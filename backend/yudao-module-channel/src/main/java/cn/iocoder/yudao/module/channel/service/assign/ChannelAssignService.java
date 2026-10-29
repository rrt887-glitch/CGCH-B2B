package cn.iocoder.yudao.module.channel.service.assign;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 按渠道账号分配新客户：每个号码 / 主页可以设置接待成员，新会话按顺序轮流分给他们（跳过已停用的成员）。
 * 分给号码所属业务员自己时不设负责人。没有设置接待成员时，新会话都由号码所属业务员处理。
 */
@Service
@Slf4j
public class ChannelAssignService {

    @Resource
    private ChannelWaAccountMapper channelWaAccountMapper;
    @Resource
    private AdminUserApi adminUserApi;

    public static List<Long> parseIds(String s) {
        List<Long> out = new ArrayList<>();
        for (String p : StrUtil.split(StrUtil.nullToEmpty(s), ',')) {
            String t = p.trim();
            if (t.matches("\\d{1,18}")) {
                Long id = Long.valueOf(t);
                if (!out.contains(id)) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    /** 新会话的负责人：轮到号码所属业务员或没有可用的接待成员时返回 null */
    public Long nextOwner(ChannelWaAccountDO a) {
        List<Long> ids = a == null ? List.of() : parseIds(a.getAssignUserIds());
        if (ids.isEmpty()) {
            return null;
        }
        try {
            channelWaAccountMapper.update(null, new LambdaUpdateWrapper<ChannelWaAccountDO>()
                    .eq(ChannelWaAccountDO::getId, a.getId()).setSql("assign_cursor = IFNULL(assign_cursor, 0) + 1"));
            ChannelWaAccountDO fresh = channelWaAccountMapper.selectById(a.getId());
            int cursor = fresh == null || fresh.getAssignCursor() == null ? 1 : fresh.getAssignCursor();
            for (int i = 0; i < ids.size(); i++) {
                Long uid = ids.get(Math.floorMod(cursor - 1 + i, ids.size()));
                AdminUserRespDTO u = adminUserApi.getUser(uid);
                if (u != null && CommonStatusEnum.isEnable(u.getStatus())) {
                    return Objects.equals(uid, a.getUserId()) ? null : uid;
                }
            }
        } catch (Exception e) {
            log.warn("[nextOwner][号码 {} 分配失败，由号码所属业务员处理] {}", a.getId(), e.getMessage());
        }
        return null;
    }

    /** 设置接待成员（按顺序），空列表表示不分配 */
    public String normalize(List<Long> userIds) {
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long id : userIds) {
                if (id == null) {
                    continue;
                }
                AdminUserRespDTO u = adminUserApi.getUser(id);
                if (u != null && CommonStatusEnum.isEnable(u.getStatus())) {
                    set.add(id);
                }
            }
        }
        return set.isEmpty() ? "" : StrUtil.join(",", set);
    }

}
