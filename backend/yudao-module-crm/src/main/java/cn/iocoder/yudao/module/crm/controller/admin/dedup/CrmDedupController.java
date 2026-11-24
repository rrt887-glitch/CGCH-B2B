package cn.iocoder.yudao.module.crm.controller.admin.dedup;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.crm.controller.admin.dedup.vo.CrmDedupRespVO;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.mysql.clue.CrmClueMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 查重：按名称、公司、邮箱、电话、官网、WhatsApp 在客户与线索中查找可能重复的记录。
 * 不受负责人数据权限限制，便于发现其他同事或公海里已有的客户。
 */
@Tag(name = "管理后台 - CRM 查重")
@RestController
@RequestMapping("/crm/dedup")
@Validated
public class CrmDedupController {

    private static final int LIMIT = 50;

    @Resource
    private CrmCustomerMapper customerMapper;
    @Resource
    private CrmClueMapper clueMapper;

    @GetMapping("/search")
    @Operation(summary = "查重搜索")
    @Parameter(name = "keyword", description = "关键字，至少 2 个字符", required = true, example = "acme")
    @Parameter(name = "scope", description = "范围：all 全部，customer 客户，clue 线索", example = "all")
    @Parameter(name = "field", description = "字段：all、name、email、mobile、website", example = "mobile")
    @PreAuthorize("@ss.hasPermission('crm:dedup:query')")
    public CommonResult<List<CrmDedupRespVO>> search(@RequestParam("keyword") String keyword,
                                                     @RequestParam(value = "scope", defaultValue = "all") String scope,
                                                     @RequestParam(value = "field", defaultValue = "all") String field) {
        String kw = StrUtil.trim(keyword);
        List<CrmDedupRespVO> result = new ArrayList<>();
        if (StrUtil.length(kw) < 2) {
            return success(result);
        }
        if (!"clue".equals(scope)) {
            List<CrmCustomerDO> customers = customerMapper.selectList(matchQuery(field, kw,
                    CrmCustomerDO::getName, CrmCustomerDO::getCompanyName, CrmCustomerDO::getEmail,
                    CrmCustomerDO::getMobile, CrmCustomerDO::getTelephone, CrmCustomerDO::getWebsite, CrmCustomerDO::getWhatsapp)
                    .orderByDesc(CrmCustomerDO::getId).last("LIMIT " + LIMIT));
            for (CrmCustomerDO c : customers) {
                result.add(toResp("customer", c.getId(), c.getName(), c.getCompanyName(), c.getEmail(), c.getMobile(),
                        c.getCountryCode(), c.getOwnerUserId(), kw, fields(c.getName(), c.getCompanyName(), c.getEmail(),
                                c.getMobile(), c.getTelephone(), c.getWebsite(), c.getWhatsapp())));
            }
        }
        if (!"customer".equals(scope)) {
            List<CrmClueDO> clues = clueMapper.selectList(matchQuery(field, kw,
                    CrmClueDO::getName, CrmClueDO::getCompanyName, CrmClueDO::getEmail,
                    CrmClueDO::getMobile, CrmClueDO::getTelephone, CrmClueDO::getWebsite, CrmClueDO::getWhatsapp)
                    .orderByDesc(CrmClueDO::getId).last("LIMIT " + LIMIT));
            for (CrmClueDO c : clues) {
                result.add(toResp("clue", c.getId(), c.getName(), c.getCompanyName(), c.getEmail(), c.getMobile(),
                        c.getCountryCode(), c.getOwnerUserId(), kw, fields(c.getName(), c.getCompanyName(), c.getEmail(),
                                c.getMobile(), c.getTelephone(), c.getWebsite(), c.getWhatsapp())));
            }
        }
        return success(result);
    }

    private static <T> LambdaQueryWrapperX<T> matchQuery(String field, String keyword,
            SFunction<T, ?> name, SFunction<T, ?> companyName, SFunction<T, ?> email,
            SFunction<T, ?> mobile, SFunction<T, ?> telephone, SFunction<T, ?> website, SFunction<T, ?> whatsapp) {
        List<SFunction<T, ?>> columns = switch (field) {
            case "name" -> List.of(name, companyName);
            case "email" -> List.of(email);
            case "mobile" -> List.of(mobile, telephone, whatsapp);
            case "website" -> List.of(website);
            default -> List.of(name, companyName, email, mobile, telephone, website, whatsapp);
        };
        LambdaQueryWrapperX<T> query = new LambdaQueryWrapperX<>();
        query.and(w -> {
            w.like(columns.get(0), keyword);
            for (int i = 1; i < columns.size(); i++) {
                w.or().like(columns.get(i), keyword);
            }
        });
        return query;
    }

    private static Map<String, String> fields(String name, String companyName, String email, String mobile,
                                              String telephone, String website, String whatsapp) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("companyName", companyName);
        map.put("email", email);
        map.put("mobile", mobile);
        map.put("telephone", telephone);
        map.put("website", website);
        map.put("whatsapp", whatsapp);
        return map;
    }

    private static CrmDedupRespVO toResp(String type, Long id, String name, String companyName, String email,
                                         String mobile, String countryCode, Long ownerUserId, String kw,
                                         Map<String, String> values) {
        List<String> matched = new ArrayList<>();
        values.forEach((field, value) -> {
            if (StrUtil.containsIgnoreCase(value, kw)) {
                matched.add(field);
            }
        });
        CrmDedupRespVO vo = new CrmDedupRespVO();
        vo.setType(type);
        vo.setId(id);
        vo.setName(name);
        vo.setCompanyName(companyName);
        vo.setEmail(email);
        vo.setMobile(mobile);
        vo.setCountryCode(countryCode);
        vo.setOwnerUserId(ownerUserId);
        vo.setMatchedFields(matched);
        return vo;
    }

}
