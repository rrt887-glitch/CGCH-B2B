package cn.iocoder.yudao.module.channel.service.ai;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiKnowledgeDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiKnowledgeFileDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiKnowledgeFileMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiKnowledgeMapper;
import cn.iocoder.yudao.module.channel.service.file.ChannelFileHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_LLM_FAIL;

/**
 * 知识库资料文件：上传 PDF、Word、Excel、PPT、TXT 等，解析成文字后按段落拆成知识条目，AI 回复时检索使用。
 */
@Service
@Slf4j
public class ChannelKnowledgeFileService {

    /** 单个资料文件上限 */
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    /** 一个文件最多读取的文字 */
    private static final int MAX_CHARS = 300_000;
    /** 每个知识条目的目标长度 */
    private static final int CHUNK = 1000;
    private static final Set<String> TYPES = Set.of("pdf", "doc", "docx", "xls", "xlsx", "csv", "ppt", "pptx", "txt", "md", "html", "htm", "rtf");

    @Resource
    private ChannelAiKnowledgeFileMapper fileMapper;
    @Resource
    private ChannelAiKnowledgeMapper knowledgeMapper;
    @Resource
    private ChannelFileHelper channelFileHelper;
    @Resource
    private ChannelAiService channelAiService;

    public List<ChannelAiKnowledgeFileDO> list() {
        return fileMapper.selectAll();
    }

    /** 保存并学习一个资料文件，返回文件记录（失败时 status 为 failed，带原因） */
    public ChannelAiKnowledgeFileDO upload(String fileName, String mimeType, byte[] content) {
        String name = StrUtil.blankToDefault(FileUtil.getName(StrUtil.nullToEmpty(fileName)), "file");
        String ext = StrUtil.blankToDefault(FileUtil.extName(name), "").toLowerCase();
        if (!TYPES.contains(ext)) {
            throw exception(AI_LLM_FAIL, "暂不支持 ." + ext + " 文件，请上传 PDF、Word、Excel、PPT 或 TXT");
        }
        if (content == null || content.length == 0) {
            throw exception(AI_LLM_FAIL, "文件是空的");
        }
        if (content.length > MAX_BYTES) {
            throw exception(AI_LLM_FAIL, "文件超过 20MB，请拆分后上传");
        }
        String url = channelFileHelper.save(content, name, "knowledge", mimeType);
        ChannelAiKnowledgeFileDO f = ChannelAiKnowledgeFileDO.builder().name(name).url(url).size((long) content.length)
                .fileType(ext).status(ChannelAiKnowledgeFileDO.STATUS_LEARNING).chunks(0).build();
        fileMapper.insert(f);
        learn(f, content);
        return fileMapper.selectById(f.getId());
    }

    /** 重新学习（例如之前解析失败） */
    public ChannelAiKnowledgeFileDO relearn(Long id) {
        ChannelAiKnowledgeFileDO f = fileMapper.selectById(id);
        if (f == null) {
            throw exception(AI_LLM_FAIL, "资料文件不存在");
        }
        try {
            learn(f, channelFileHelper.load(f.getUrl()));
        } catch (Exception e) {
            fail(f, "读取文件失败：" + e.getMessage());
        }
        return fileMapper.selectById(id);
    }

    public void delete(Long id) {
        knowledgeMapper.delete(new LambdaQueryWrapperX<ChannelAiKnowledgeDO>().eq(ChannelAiKnowledgeDO::getFileId, id));
        fileMapper.deleteById(id);
    }

    private void learn(ChannelAiKnowledgeFileDO f, byte[] content) {
        String text;
        try {
            Tika tika = new Tika();
            tika.setMaxStringLength(MAX_CHARS);
            text = tika.parseToString(new ByteArrayInputStream(content));
        } catch (Throwable e) {
            log.warn("[learn][资料文件({}) 解析失败] {}", f.getId(), e.getMessage());
            fail(f, "无法读取文件内容：" + StrUtil.maxLength(e.getMessage(), 200));
            return;
        }
        List<String> chunks = split(text);
        if (chunks.isEmpty()) {
            fail(f, "没有识别到文字（可能是扫描件或图片），请上传可复制文字的文件");
            return;
        }
        knowledgeMapper.delete(new LambdaQueryWrapperX<ChannelAiKnowledgeDO>().eq(ChannelAiKnowledgeDO::getFileId, f.getId()));
        String base = StrUtil.removeSuffix(f.getName(), "." + f.getFileType());
        List<ChannelAiKnowledgeDO> rows = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ChannelAiKnowledgeDO k = ChannelAiKnowledgeDO.builder().type(ChannelAiKnowledgeDO.TYPE_FILE).fileId(f.getId())
                    .title(chunks.size() == 1 ? base : base + "（第 " + (i + 1) + " 段）").content(chunks.get(i))
                    .enabled(true).sort(100).embedding("").embeddingModel("").build();
            knowledgeMapper.insert(k);
            rows.add(k);
        }
        fileMapper.updateById(new ChannelAiKnowledgeFileDO().setId(f.getId()).setStatus(ChannelAiKnowledgeFileDO.STATUS_DONE)
                .setChunks(rows.size()).setErrorMsg(""));
        channelAiService.embedQuietly(rows);
    }

    private void fail(ChannelAiKnowledgeFileDO f, String reason) {
        fileMapper.updateById(new ChannelAiKnowledgeFileDO().setId(f.getId()).setStatus(ChannelAiKnowledgeFileDO.STATUS_FAILED)
                .setErrorMsg(StrUtil.maxLength(reason, 250)));
    }

    /** 按空行分段，再合并成约 CHUNK 字的条目；超长段落按句子切开 */
    static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (StrUtil.isBlank(text)) {
            return out;
        }
        String normalized = text.replace("\r", "").replaceAll("[ \\t\\x0B\\f]+", " ").replaceAll("\n{3,}", "\n\n").trim();
        StringBuilder cur = new StringBuilder();
        for (String para : normalized.split("\n\\s*\n")) {
            String p = para.trim();
            if (p.isEmpty()) {
                continue;
            }
            while (p.length() > CHUNK * 1.5) { // 超长段落：在句号附近切开
                int cut = lastBreak(p, CHUNK);
                flush(out, cur);
                out.add(p.substring(0, cut).trim());
                p = p.substring(cut).trim();
            }
            if (cur.length() > 0 && cur.length() + p.length() > CHUNK) {
                flush(out, cur);
            }
            if (cur.length() > 0) {
                cur.append("\n\n");
            }
            cur.append(p);
        }
        flush(out, cur);
        out.removeIf(s -> s.replaceAll("\\s", "").length() < 20); // 页码、页眉之类的碎片
        return out;
    }

    private static int lastBreak(String s, int limit) {
        for (int i = Math.min(limit, s.length() - 1); i > limit / 2; i--) {
            char c = s.charAt(i);
            if (c == '。' || c == '.' || c == '!' || c == '?' || c == '！' || c == '？' || c == '\n' || c == ';' || c == '；') {
                return i + 1;
            }
        }
        return limit;
    }

    private static void flush(List<String> out, StringBuilder cur) {
        if (cur.length() > 0) {
            out.add(cur.toString().trim());
            cur.setLength(0);
        }
    }

}
