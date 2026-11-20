package cn.iocoder.yudao.module.channel.service.file;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 渠道附件、媒体文件的存取：统一走 infra 模块的文件存储（默认数据库存储，可在「基础设施 → 文件配置」切换）。
 */
@Component
public class ChannelFileHelper {

    /** 单个附件 / 媒体上限 */
    public static final int MAX_BYTES = 25 * 1024 * 1024;

    /** 数据库、本地、FTP/SFTP 存储返回的地址形如 {domain}/admin-api/infra/file/{configId}/get/{path} */
    private static final Pattern LOCAL_URL = Pattern.compile("/admin-api/infra/file/(\\d+)/get/(.+)$");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    @Resource
    private FileApi fileApi;
    @Resource
    private FileService fileService;

    /** 保存文件，返回访问地址 */
    public String save(byte[] content, String fileName, String directory, String mimeType) {
        return fileApi.createFile(content, randomize(fileName), directory, mimeType);
    }

    /**
     * 文件名加随机串。
     *
     * 渠道附件是「公网 URL 直接访问」的（访客 H5、WhatsApp 图片都要能看），
     * 如果文件名可猜（例如 image-8744.jpg），别人就能顺着编号遍历下载别人的聊天图片。
     * 这里统一加 16 位随机串，URL 不可猜；原有调用方传的文件名/扩展名保留，方便下载时识别。
     */
    static String randomize(String fileName) {
        String safe = StrUtil.blankToDefault(fileName, "file");
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String ext = dot > 0 ? safe.substring(dot) : "";
        return StrUtil.maxLength(base, 80) + "-" + cn.hutool.core.util.RandomUtil.randomString(16) + StrUtil.maxLength(ext, 10);
    }

    /** 读取之前保存的文件内容 */
    public byte[] load(String url) throws Exception {
        Matcher m = LOCAL_URL.matcher(StrUtil.subBefore(url, "?", false));
        if (m.find()) {
            String path = URLDecoder.decode(m.group(2), StandardCharsets.UTF_8);
            return fileService.getFileContent(Long.valueOf(m.group(1)), path);
        }
        // 对象存储等公网地址，直接下载
        HttpResponse<byte[]> resp = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 300) {
            throw new IllegalStateException("读取文件失败：HTTP " + resp.statusCode());
        }
        return resp.body();
    }

}
