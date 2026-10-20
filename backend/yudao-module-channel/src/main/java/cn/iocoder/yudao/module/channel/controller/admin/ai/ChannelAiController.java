package cn.iocoder.yudao.module.channel.controller.admin.ai;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.channel.controller.admin.ai.vo.ChannelAiConfigVO;
import cn.iocoder.yudao.module.channel.controller.admin.ai.vo.ChannelAiDraftRespVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiForbiddenWordDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiKnowledgeDO;
import cn.iocoder.yudao.module.channel.service.ai.ChannelAiService;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - AI 数字员工")
@RestController
@RequestMapping("/channel/ai")
@Validated
public class ChannelAiController {

    @Resource
    private ChannelAiService aiService;

    // ===== 配置 =====

    @GetMapping("/config/get")
    @Operation(summary = "获取数字员工配置（不含 API Key）")
    @PreAuthorize("@ss.hasPermission('channel:ai:config')")
    public CommonResult<ChannelAiConfigVO> getConfig() {
        ChannelAiConfigDO c = aiService.getConfig();
        ChannelAiConfigVO vo = BeanUtils.toBean(c, ChannelAiConfigVO.class);
        vo.setHasApiKey(StrUtil.isNotBlank(c.getLlmApiKey()));
        vo.setLlmApiKey(null);
        return success(vo);
    }

    @PutMapping("/config/save")
    @Operation(summary = "保存数字员工配置")
    @PreAuthorize("@ss.hasPermission('channel:ai:config')")
    public CommonResult<Boolean> saveConfig(@Valid @RequestBody ChannelAiConfigVO req) {
        aiService.saveConfig(BeanUtils.toBean(req, ChannelAiConfigDO.class));
        return success(true);
    }

    @PostMapping("/config/test")
    @Operation(summary = "测试大模型连接，返回模型的回答")
    @PreAuthorize("@ss.hasPermission('channel:ai:config')")
    public CommonResult<String> testConfig(@RequestBody ChannelAiConfigVO req) {
        return success(aiService.testConfig(BeanUtils.toBean(req, ChannelAiConfigDO.class)));
    }

    // ===== 知识库 =====

    @GetMapping("/knowledge/list")
    @Operation(summary = "知识库条目")
    @Parameter(name = "type", description = "company、product、faq，不填为全部")
    @PreAuthorize("@ss.hasPermission('channel:ai:knowledge')")
    public CommonResult<List<ChannelAiKnowledgeDO>> listKnowledge(@RequestParam(value = "type", required = false) String type) {
        return success(aiService.listKnowledge(type));
    }

    @PostMapping("/knowledge/save")
    @Operation(summary = "新增或修改知识条目")
    @PreAuthorize("@ss.hasPermission('channel:ai:knowledge')")
    public CommonResult<Long> saveKnowledge(@RequestBody ChannelAiKnowledgeDO req) {
        return success(aiService.saveKnowledge(req));
    }

    @PostMapping("/knowledge/reindex")
    @Operation(summary = "为知识库建立语义索引（只处理未建或模型已更换的条目），返回处理条数")
    @PreAuthorize("@ss.hasPermission('channel:ai:knowledge')")
    public CommonResult<Integer> reindexKnowledge() {
        return success(aiService.reindexKnowledge(getLoginUserId()));
    }

    @GetMapping("/knowledge/search")
    @Operation(summary = "检索测试：看客户的这句话会命中哪些知识")
    @PreAuthorize("@ss.hasPermission('channel:ai:knowledge')")
    public CommonResult<List<Map<String, Object>>> searchKnowledge(@RequestParam("q") String q) {
        return success(aiService.searchKnowledge(getLoginUserId(), q));
    }

    @DeleteMapping("/knowledge/delete")
    @Operation(summary = "删除知识条目")
    @PreAuthorize("@ss.hasPermission('channel:ai:knowledge')")
    public CommonResult<Boolean> deleteKnowledge(@RequestParam("id") Long id) {
        aiService.deleteKnowledge(id);
        return success(true);
    }

    // ===== 违禁词 =====

    @GetMapping("/forbidden/list")
    @Operation(summary = "违禁词")
    @PreAuthorize("@ss.hasPermission('channel:ai:forbidden')")
    public CommonResult<List<ChannelAiForbiddenWordDO>> listForbidden() {
        return success(aiService.listForbidden());
    }

    @PostMapping("/forbidden/save")
    @Operation(summary = "新增或修改违禁词")
    @PreAuthorize("@ss.hasPermission('channel:ai:forbidden')")
    public CommonResult<Long> saveForbidden(@RequestBody ChannelAiForbiddenWordDO req) {
        return success(aiService.saveForbidden(req));
    }

    @DeleteMapping("/forbidden/delete")
    @Operation(summary = "删除违禁词")
    @PreAuthorize("@ss.hasPermission('channel:ai:forbidden')")
    public CommonResult<Boolean> deleteForbidden(@RequestParam("id") Long id) {
        aiService.deleteForbidden(id);
        return success(true);
    }

    // ===== 起草与草稿 =====

    @PostMapping("/draft/mail")
    @Operation(summary = "为收到的邮件起草回复")
    @Parameter(name = "messageId", required = true)
    @PreAuthorize("@ss.hasPermission('channel:ai:draft')")
    public CommonResult<ChannelAiDraftRespVO> draftMail(@RequestParam("messageId") Long messageId) {
        return success(BeanUtils.toBean(aiService.draftForMail(getLoginUserId(), messageId), ChannelAiDraftRespVO.class));
    }

    @PostMapping("/draft/whatsapp")
    @Operation(summary = "为 WhatsApp 会话起草回复")
    @Parameter(name = "conversationId", required = true)
    @PreAuthorize("@ss.hasPermission('channel:ai:draft')")
    public CommonResult<ChannelAiDraftRespVO> draftWhatsApp(@RequestParam("conversationId") Long conversationId) {
        return success(BeanUtils.toBean(aiService.draftForWhatsApp(getLoginUserId(), conversationId), ChannelAiDraftRespVO.class));
    }

    @GetMapping("/draft/pending")
    @Operation(summary = "某封邮件或某个会话最新的待处理草稿；没有返回 null")
    @PreAuthorize("@ss.hasPermission('channel:ai:draft')")
    public CommonResult<ChannelAiDraftRespVO> pending(@RequestParam("channel") String channel, @RequestParam("refId") Long refId) {
        return success(BeanUtils.toBean(aiService.getPendingDraft(channel, refId), ChannelAiDraftRespVO.class));
    }

    @GetMapping("/draft/page")
    @Operation(summary = "AI 回复记录（含已自动发出的）")
    @PreAuthorize("@ss.hasPermission('channel:ai:draft')")
    public CommonResult<PageResult<ChannelAiDraftRespVO>> page(@Valid PageParam page,
                                                               @RequestParam(value = "channel", required = false) String channel,
                                                               @RequestParam(value = "status", required = false) String status) {
        return success(BeanUtils.toBean(aiService.getDraftPage(page, channel, status), ChannelAiDraftRespVO.class));
    }

    @PostMapping("/draft/close")
    @Operation(summary = "采用（used=true）或放弃草稿")
    @PreAuthorize("@ss.hasPermission('channel:ai:draft')")
    public CommonResult<Boolean> close(@RequestParam("id") Long id, @RequestParam("used") Boolean used) {
        aiService.closeDraft(id, Boolean.TRUE.equals(used));
        return success(true);
    }

}
