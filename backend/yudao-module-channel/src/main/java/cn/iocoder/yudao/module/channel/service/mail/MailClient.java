package cn.iocoder.yudao.module.channel.service.mail;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * IMAP 收信、SMTP 发信的底层封装，不涉及数据库。
 */
@Slf4j
public final class MailClient {

    private static final int TIMEOUT_MS = 20_000;
    /** 首次同步只拉最近这么多封，避免一次导入整个邮箱 */
    public static final int FIRST_SYNC_LIMIT = 100;
    /** 每轮同步最多处理的邮件数 */
    public static final int BATCH_LIMIT = 200;

    private MailClient() {
    }

    /** 解析后的一封邮件 */
    @Data
    @Builder
    public static class ParsedMail {
        private long uid;
        private String messageId;
        private String inReplyTo;
        private String references;
        private String fromAddress;
        private String fromName;
        private List<String> to;
        private List<String> cc;
        private String subject;
        private String textBody;
        private String htmlBody;
        private LocalDateTime sentTime;
        private List<Attachment> attachments;
    }

    /** 附件（收到的或待发送的） */
    @Data
    @Builder
    public static class Attachment {
        private String fileName;
        private String contentType;
        private byte[] content;
        /** 内嵌图片的 Content-ID，不含尖括号 */
        private String contentId;
        private boolean inlined;
    }

    /** 单个附件大小上限，超过的跳过 */
    public static final int MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024;

    /** 一轮收信结果 */
    @Data
    @Builder
    public static class FetchResult {
        private long uidValidity;
        private long maxUid;
        private List<ParsedMail> mails;
    }

    // ==================== 连接 ====================

    private static Session imapSession(ChannelMailAccountDO a) {
        String p = Boolean.TRUE.equals(a.getImapSsl()) ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail.store.protocol", p);
        props.put("mail." + p + ".host", a.getImapHost());
        props.put("mail." + p + ".port", String.valueOf(a.getImapPort()));
        props.put("mail." + p + ".connectiontimeout", String.valueOf(TIMEOUT_MS));
        props.put("mail." + p + ".timeout", String.valueOf(TIMEOUT_MS));
        props.put("mail." + p + ".partialfetch", "false");
        if (Boolean.TRUE.equals(a.getImapSsl())) {
            props.put("mail.imaps.ssl.checkserveridentity", "true");
        }
        return Session.getInstance(props);
    }

    private static Session smtpSession(ChannelMailAccountDO a) {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", a.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(a.getSmtpPort()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MS));
        props.put("mail.smtp.timeout", String.valueOf(TIMEOUT_MS));
        props.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MS));
        if (Boolean.TRUE.equals(a.getSmtpSsl())) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true"); // 服务器支持时自动升级加密
        }
        return Session.getInstance(props);
    }

    private static Store openStore(ChannelMailAccountDO a) throws MessagingException {
        Session session = imapSession(a);
        Store store = session.getStore(Boolean.TRUE.equals(a.getImapSsl()) ? "imaps" : "imap");
        store.connect(a.getImapHost(), a.getImapPort(), loginName(a), a.getPassword());
        return store;
    }

    private static String loginName(ChannelMailAccountDO a) {
        return StrUtil.blankToDefault(a.getUsername(), a.getEmail());
    }

    /** 测试 IMAP 与 SMTP 是否都能登录，失败时抛出带原因的异常 */
    public static void testConnection(ChannelMailAccountDO a) throws MessagingException {
        try (Store store = openStore(a)) {
            store.getFolder("INBOX").exists();
        }
        Session session = smtpSession(a);
        try (Transport t = session.getTransport("smtp")) {
            t.connect(a.getSmtpHost(), a.getSmtpPort(), loginName(a), a.getPassword());
        }
    }

    // ==================== 收信 ====================

    /**
     * 拉取收件箱中 UID 大于 lastUid 的邮件。
     * uidValidity 与上次不同（或首次）时，只取最近 {@link #FIRST_SYNC_LIMIT} 封。
     */
    public static FetchResult fetchInbox(ChannelMailAccountDO a) throws MessagingException {
        try (Store store = openStore(a)) {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            try {
                UIDFolder uf = (UIDFolder) folder;
                long validity = uf.getUIDValidity();
                long lastUid = a.getLastSyncUid() == null ? 0 : a.getLastSyncUid();
                boolean fresh = lastUid == 0 || a.getUidValidity() == null || a.getUidValidity() != validity;
                Message[] messages;
                if (fresh) {
                    int count = folder.getMessageCount();
                    messages = count == 0 ? new Message[0]
                            : folder.getMessages(Math.max(1, count - FIRST_SYNC_LIMIT + 1), count);
                    lastUid = 0;
                } else {
                    messages = uf.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
                }
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                fp.add(UIDFolder.FetchProfileItem.UID);
                folder.fetch(messages, fp);

                List<ParsedMail> result = new ArrayList<>();
                long maxUid = lastUid;
                for (Message m : messages) {
                    long uid = uf.getUID(m);
                    if (uid <= lastUid) {
                        continue; // "n:*" 在没有新邮件时会返回最后一封，跳过
                    }
                    if (result.size() >= BATCH_LIMIT) {
                        break;
                    }
                    try {
                        result.add(parse((MimeMessage) m, uid));
                    } catch (Exception e) {
                        log.warn("[fetchInbox][{}] 解析 UID {} 失败", a.getEmail(), uid, e);
                    }
                    maxUid = Math.max(maxUid, uid);
                }
                return FetchResult.builder().uidValidity(validity).maxUid(maxUid).mails(result).build();
            } finally {
                folder.close(false);
            }
        }
    }

    private static ParsedMail parse(MimeMessage m, long uid) throws MessagingException, IOException {
        InternetAddress from = firstAddress(m.getFrom());
        Body body = new Body();
        collectBody(m, body);
        Date date = m.getSentDate() != null ? m.getSentDate() : m.getReceivedDate();
        return ParsedMail.builder()
                .uid(uid)
                .messageId(m.getMessageID())
                .inReplyTo(header(m, "In-Reply-To"))
                .references(header(m, "References"))
                .fromAddress(from == null ? null : from.getAddress().toLowerCase(Locale.ROOT))
                .fromName(from == null ? null : from.getPersonal())
                .to(addresses(m.getRecipients(Message.RecipientType.TO)))
                .cc(addresses(m.getRecipients(Message.RecipientType.CC)))
                .subject(m.getSubject())
                .textBody(body.text)
                .htmlBody(body.html)
                .attachments(body.attachments)
                .sentTime(date == null ? LocalDateTime.now()
                        : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()))
                .build();
    }

    private static class Body {
        String text;
        String html;
        List<Attachment> attachments = new ArrayList<>();
    }

    /** 递归取第一段纯文本与 HTML 正文，并收集附件与内嵌图片 */
    private static void collectBody(Part part, Body body) throws MessagingException, IOException {
        String fileName = decodeFileName(part.getFileName());
        boolean attachment = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || (StrUtil.isNotBlank(fileName) && !part.isMimeType("multipart/*"));
        String cid = part instanceof MimeBodyPart mbp ? StrUtil.strip(mbp.getContentID(), "<", ">") : null;
        boolean inlineImage = !attachment && part.isMimeType("image/*") && StrUtil.isNotBlank(cid);
        if (attachment || inlineImage) {
            byte[] bytes;
            try (java.io.InputStream in = part.getInputStream()) {
                bytes = in.readNBytes(MAX_ATTACHMENT_BYTES + 1);
            }
            if (bytes.length > MAX_ATTACHMENT_BYTES) {
                log.warn("[collectBody] 附件 {} 超过 {} 字节，已跳过", fileName, MAX_ATTACHMENT_BYTES);
                return;
            }
            body.attachments.add(Attachment.builder()
                    .fileName(StrUtil.blankToDefault(fileName, inlineImage ? cid : "attachment"))
                    .contentType(StrUtil.subBefore(part.getContentType(), ";", false).trim().toLowerCase(Locale.ROOT))
                    .content(bytes).contentId(cid)
                    .inlined(inlineImage || Part.INLINE.equalsIgnoreCase(part.getDisposition()) && StrUtil.isNotBlank(cid))
                    .build());
            return;
        }
        if (part.isMimeType("text/plain") && body.text == null) {
            body.text = asString(part.getContent());
        } else if (part.isMimeType("text/html") && body.html == null) {
            body.html = asString(part.getContent());
        } else if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                collectBody(mp.getBodyPart(i), body);
            }
        } else if (part.isMimeType("message/rfc822")) {
            collectBody((Part) part.getContent(), body);
        }
    }

    private static String decodeFileName(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        try {
            return MimeUtility.decodeText(name);
        } catch (UnsupportedEncodingException e) {
            return name;
        }
    }

    private static String asString(Object content) throws IOException {
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof java.io.InputStream in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return content == null ? null : content.toString();
    }

    private static String header(MimeMessage m, String name) throws MessagingException {
        String[] v = m.getHeader(name);
        return v == null || v.length == 0 ? null : v[0].trim();
    }

    private static InternetAddress firstAddress(Address[] list) {
        if (list == null) {
            return null;
        }
        for (Address a : list) {
            if (a instanceof InternetAddress ia) {
                return ia;
            }
        }
        return null;
    }

    private static List<String> addresses(Address[] list) {
        List<String> result = new ArrayList<>();
        if (list != null) {
            for (Address a : list) {
                if (a instanceof InternetAddress ia) {
                    result.add(ia.getAddress().toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    // ==================== 发信 ====================

    /**
     * 发送邮件，返回发出邮件的 Message-ID。
     *
     * @param inReplyTo 回复的原邮件 Message-ID，可为空
     * @param references 原邮件的 References，可为空
     */
    public static String send(ChannelMailAccountDO a, List<String> to, List<String> cc, String subject,
                              String text, String html, String inReplyTo, String references)
            throws MessagingException, UnsupportedEncodingException {
        return send(a, to, cc, subject, text, html, inReplyTo, references, List.of());
    }

    /** 发送邮件（可带附件），返回发出邮件的 Message-ID */
    public static String send(ChannelMailAccountDO a, List<String> to, List<String> cc, String subject,
                              String text, String html, String inReplyTo, String references,
                              List<Attachment> attachments)
            throws MessagingException, UnsupportedEncodingException {
        Session session = smtpSession(a);
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(a.getEmail(), StrUtil.blankToDefault(a.getDisplayName(), null), "UTF-8"));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(String.join(",", to)));
        if (cc != null && !cc.isEmpty()) {
            msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(String.join(",", cc)));
        }
        msg.setSubject(StrUtil.nullToEmpty(subject), "UTF-8");
        msg.setSentDate(new Date());
        if (StrUtil.isNotBlank(inReplyTo)) {
            msg.setHeader("In-Reply-To", inReplyTo);
            msg.setHeader("References", StrUtil.isBlank(references) ? inReplyTo : references + " " + inReplyTo);
        }
        // 正文：有 HTML 时 text + html 二选一展示（alternative）
        MimeBodyPart bodyPart = new MimeBodyPart();
        if (StrUtil.isNotBlank(html)) {
            MimeMultipart alt = new MimeMultipart("alternative");
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(StrUtil.blankToDefault(text, HtmlText.toText(html)), "UTF-8");
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(html, "text/html; charset=UTF-8");
            alt.addBodyPart(textPart);
            alt.addBodyPart(htmlPart);
            bodyPart.setContent(alt);
        } else {
            bodyPart.setText(StrUtil.nullToEmpty(text), "UTF-8");
        }
        if (attachments == null || attachments.isEmpty()) {
            if (StrUtil.isNotBlank(html)) {
                msg.setContent((Multipart) bodyPart.getContent());
            } else {
                msg.setText(StrUtil.nullToEmpty(text), "UTF-8");
            }
        } else {
            MimeMultipart mixed = new MimeMultipart("mixed");
            mixed.addBodyPart(bodyPart);
            for (Attachment att : attachments) {
                MimeBodyPart ap = new MimeBodyPart();
                ap.setDataHandler(new jakarta.activation.DataHandler(new jakarta.mail.util.ByteArrayDataSource(
                        att.getContent(), StrUtil.blankToDefault(att.getContentType(), "application/octet-stream"))));
                ap.setFileName(MimeUtility.encodeText(att.getFileName(), "UTF-8", null));
                ap.setDisposition(Part.ATTACHMENT);
                mixed.addBodyPart(ap);
            }
            msg.setContent(mixed);
        }
        msg.saveChanges();
        try (Transport t = session.getTransport("smtp")) {
            t.connect(a.getSmtpHost(), a.getSmtpPort(), loginName(a), a.getPassword());
            t.sendMessage(msg, msg.getAllRecipients());
        }
        return msg.getMessageID();
    }

}
