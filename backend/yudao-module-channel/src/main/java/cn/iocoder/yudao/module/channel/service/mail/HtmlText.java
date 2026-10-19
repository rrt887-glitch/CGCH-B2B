package cn.iocoder.yudao.module.channel.service.mail;

import cn.hutool.core.util.StrUtil;

/**
 * HTML 正文转纯文本（用于摘要和纯文本备份），只做基础处理。
 */
public final class HtmlText {

    private HtmlText() {
    }

    public static String toText(String html) {
        if (StrUtil.isBlank(html)) {
            return "";
        }
        String s = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|li|h[1-6])>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        return s.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n\\s*\\n+", "\n\n").trim();
    }

    /** 列表摘要：取正文前若干字符，去掉引用的历史邮件 */
    public static String snippet(String text, String html, int max) {
        String body = StrUtil.isNotBlank(text) ? text : toText(html);
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith(">")) {
                continue;
            }
            if (t.matches("(?i)^(on .+ wrote:|-{2,}\\s*original message\\s*-{2,}|在 .+ 写道[:：]?)$")) {
                break;
            }
            if (!t.isEmpty()) {
                sb.append(t).append(' ');
            }
            if (sb.length() >= max) {
                break;
            }
        }
        return StrUtil.maxLength(sb.toString().trim(), max - 3);
    }

}
