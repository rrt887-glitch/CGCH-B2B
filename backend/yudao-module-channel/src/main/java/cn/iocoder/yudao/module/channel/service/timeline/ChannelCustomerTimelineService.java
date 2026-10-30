package cn.iocoder.yudao.module.channel.service.timeline;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaMessageMapper;
import cn.iocoder.yudao.module.channel.service.mail.HtmlText;
import cn.iocoder.yudao.module.crm.service.customer.CrmCustomerService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 客户 360 · 沟通记录：把这个客户在 WhatsApp、Facebook、Instagram、官网客服和邮件里的往来按时间合在一起，
 * 在客户详情里查看。能看到客户（CRM 数据权限 READ）的人就能看到这些记录，不要求是会话的处理人。
 */
@Service
public class ChannelCustomerTimelineService {

    private static final int MAX_LIMIT = 100;

    @Resource
    private CrmCustomerService crmCustomerService;
    @Resource
    private ChannelWaConversationMapper channelWaConversationMapper;
    @Resource
    private ChannelWaMessageMapper channelWaMessageMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;

    public record Item(String channel, String platform, Integer direction, LocalDateTime time, String text, String subject,
                       String type, String fileName, Boolean ai, Long conversationId, Long mailId, String contact) {
    }

    public record Page(List<Item> list, Map<String, Long> counts, boolean more) {
    }

    /**
     * @param before 只取这个时间之前的（翻页用），为空从最新开始
     */
    public Page list(Long customerId, LocalDateTime before, Integer limit) {
        crmCustomerService.validateCustomerReadable(customerId); // 校验 CRM 数据权限
        int n = limit == null ? 50 : Math.max(1, Math.min(MAX_LIMIT, limit));
        List<ChannelWaConversationDO> convs = channelWaConversationMapper.selectList(
                new LambdaQueryWrapperX<ChannelWaConversationDO>().eq(ChannelWaConversationDO::getCustomerId, customerId));
        Map<Long, ChannelWaConversationDO> convById = new HashMap<>();
        convs.forEach(c -> convById.put(c.getId(), c));
        List<Item> items = new ArrayList<>();
        long imCount = 0;
        if (!convById.isEmpty()) {
            imCount = channelWaMessageMapper.selectCount(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                    .in(ChannelWaMessageDO::getConversationId, convById.keySet()));
            for (ChannelWaMessageDO m : channelWaMessageMapper.selectList(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                    .in(ChannelWaMessageDO::getConversationId, convById.keySet())
                    .ltIfPresent(ChannelWaMessageDO::getMessageTime, before)
                    .orderByDesc(ChannelWaMessageDO::getMessageTime).orderByDesc(ChannelWaMessageDO::getId)
                    .last("LIMIT " + (n + 1)))) {
                ChannelWaConversationDO c = convById.get(m.getConversationId());
                items.add(new Item("im", StrUtil.blankToDefault(c.getPlatform(), ChannelWaAccountDO.PLATFORM_WHATSAPP), m.getDirection(),
                        m.getMessageTime(), StrUtil.maxLength(m.getTextBody(), 1000), null, m.getType(), m.getFileName(),
                        m.getAiGenerated(), c.getId(), null, StrUtil.blankToDefault(c.getRemarkName(), c.contactLabel())));
            }
        }
        LambdaQueryWrapperX<ChannelMailMessageDO> mq = new LambdaQueryWrapperX<>();
        mq.eq(ChannelMailMessageDO::getCustomerId, customerId);
        mq.ne(ChannelMailMessageDO::getFolder, "DRAFT");
        long mailCount = channelMailMessageMapper.selectCount(mq);
        LambdaQueryWrapperX<ChannelMailMessageDO> ml = new LambdaQueryWrapperX<>();
        ml.eq(ChannelMailMessageDO::getCustomerId, customerId);
        ml.ne(ChannelMailMessageDO::getFolder, "DRAFT");
        ml.ltIfPresent(ChannelMailMessageDO::getSentTime, before);
        ml.orderByDesc(ChannelMailMessageDO::getSentTime).orderByDesc(ChannelMailMessageDO::getId).last("LIMIT " + (n + 1));
        for (ChannelMailMessageDO m : channelMailMessageMapper.selectList(ml)) {
            String body = HtmlText.snippet(m.getTextBody(), m.getHtmlBody(), 1000);
            items.add(new Item("mail", "mail", m.getDirection(), m.getSentTime(), body, m.getSubject(), "email", null,
                    m.getAiGenerated(), null, m.getId(), StrUtil.blankToDefault(m.getFromName(), m.getFromAddress())));
        }
        items.sort(Comparator.comparing(Item::time, Comparator.nullsLast(Comparator.reverseOrder())));
        boolean more = items.size() > n;
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("im", imCount);
        counts.put("mail", mailCount);
        counts.put("conversations", (long) convs.size());
        return new Page(more ? items.subList(0, n) : items, counts, more);
    }

}
