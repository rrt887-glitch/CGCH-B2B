package cn.iocoder.yudao.module.channel.service.file;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.channel.dal.mysql.file.ChannelFileSecurityMapper;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 历史附件安全迁移。
 *
 * 背景：附件是「公网 URL 直接访问」的（访客 H5、聊天图片都要能看），而早期上传的文件名可猜
 * （例如 image-8744.jpg），知道规律就能遍历下载别人的图片。新上传的文件已经带 16 位随机串，
 * 这个服务负责把**历史文件**也改成随机名，并把数据库里的引用一起替换，最后删掉旧文件。
 *
 * 由管理员手动触发（POST /channel/files/security-migrate），支持 dryRun 先看数量。
 */
@Service
@Slf4j
public class ChannelFileSecurityService {

    /** 存放文件地址的表和列（白名单，避免拼接用户输入） */
    private static final String[][] URL_COLUMNS = {
            {"channel_wa_message", "file_url"},
            {"channel_wa_conversation", "avatar_url"},
            {"channel_mail_attachment", "file_url"},
            {"channel_cloud_file", "url"},
            {"channel_ai_knowledge_file", "url"},
    };

    /** 已经是随机名的就不再处理：xxx-16位随机串.ext */
    private static final Pattern SAFE_NAME = Pattern.compile("-([A-Za-z0-9]{16})(\\.[A-Za-z0-9]{1,10})?$");

    /** 文件地址形如 {domain}/admin-api/infra/file/{configId}/get/{path} */
    private static final Pattern FILE_URL = Pattern.compile("/admin-api/infra/file/(\\d+)/get/(.+)$");

    @Resource
    private ChannelFileSecurityMapper fileSecurityMapper;
    @Resource
    private ChannelFileHelper fileHelper;
    @Resource
    private FileService fileService;

    /** 迁移结果 */
    public record Result(int scanned, int migrated, int alreadySafe, int failed, List<String> errors) {
    }

    /** 扫描所有引用过附件的列，把可猜的文件名改成随机名 */
    public Result migrate(boolean dryRun) {
        Set<String> urls = new LinkedHashSet<>();
        for (String[] col : URL_COLUMNS) {
            List<String> list = fileSecurityMapper.selectFileUrls(col[0], col[1]);
            if (list != null) {
                urls.addAll(list);
            }
        }
        int migrated = 0;
        int safe = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (String url : urls) {
            if (StrUtil.isBlank(url)) {
                continue;
            }
            Matcher m = FILE_URL.matcher(StrUtil.subBefore(url, "?", false));
            if (!m.find()) {
                continue;
            }
            String path = m.group(2);
            String fileName = StrUtil.subAfter(path, "/", true);
            if (SAFE_NAME.matcher(fileName).find()) {
                safe++;
                continue;
            }
            if (dryRun) {
                migrated++;
                continue;
            }
            try {
                Long configId = Long.valueOf(m.group(1));
                FileDO old = fileService.getFileByConfigIdAndPath(configId, path);
                if (old == null) {
                    continue;
                }
                byte[] content = fileService.getFileContent(configId, path);
                if (content == null || content.length == 0) {
                    failed++;
                    errors.add(url + "：读取内容为空");
                    continue;
                }
                String directory = StrUtil.subBefore(path, "/", true);
                String newUrl = fileHelper.save(content, fileName, directory, old.getType());
                for (String[] col : URL_COLUMNS) {
                    fileSecurityMapper.replaceUrl(col[0], col[1], url, newUrl);
                }
                try {
                    fileService.deleteFile(old.getId());
                } catch (Exception e) {
                    log.warn("[migrate][旧文件 {} 删除失败，可忽略：{}]", old.getId(), e.getMessage());
                }
                migrated++;
            } catch (Exception e) {
                failed++;
                if (errors.size() < 20) {
                    errors.add(url + "：" + e.getMessage());
                }
            }
        }
        log.info("[migrate][附件安全迁移 {}] 扫描 {} 个地址，迁移 {}，已安全 {}，失败 {}",
                dryRun ? "(试运行)" : "", urls.size(), migrated, safe, failed);
        return new Result(urls.size(), migrated, safe, failed, errors);
    }

    /** 迁移统计（给接口返回用） */
    public Map<String, Object> stats(Result r) {
        return Map.of("scanned", r.scanned(), "migrated", r.migrated(),
                "alreadySafe", r.alreadySafe(), "failed", r.failed(), "errors", r.errors());
    }
}
