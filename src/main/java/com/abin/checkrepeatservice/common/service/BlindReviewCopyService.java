package com.abin.checkrepeatservice.common.service;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.mapper.FileInfoMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 盲审专用副本生成服务。
 *
 * <p>背景：元数据脱敏只能挡住接口字段，论文 PDF/Word 正文里的姓名、导师姓名、
 * 致谢、邮箱/电话仍会直接暴露作者。严格盲审需要在“文档级”去标识化。</p>
 *
 * <p>做法：用 Tika 抽取正文 → 命中身份词与联系方式统一打码、移除致谢章节 →
 * 重新生成一份<b>纯文本 PDF</b>（不携带原文件的作者等元数据）。副本按
 * {@code paperId/fileId} 缓存，论文重新提交（fileId 变化）后自动重新生成。</p>
 *
 * <p>已知边界：重新排版后不保留原文图表/页眉样式；姓名识别基于已知身份词
 * （学生/导师的姓名、用户名、论文 author 字段），无法识别正文里自述的化名。</p>
 */
@Service
@Slf4j
public class BlindReviewCopyService {

    private static final Tika TIKA = new Tika();

    /** 同一文件的并发生成锁（单机有效；多实例部署时最坏情况是重复生成一次） */
    private static final ConcurrentHashMap<String, Object> GENERATION_LOCKS = new ConcurrentHashMap<>();

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private static final String MASK = "***";

    /** 副本存储根目录 */
    @Value("${review.blind.base-path:/data/review-blind/}")
    private String basePath;

    /** 正文最大字符数（防止超大文档把堆打满） */
    @Value("${review.blind.max-chars:5000000}")
    private int maxChars;

    /** 是否移除“致谢/致谢辞/Acknowledgements”章节 */
    @Value("${review.blind.remove-acknowledgement:true}")
    private boolean removeAcknowledgement;

    /** 中文字体（复用报告模块的字体配置） */
    @Value("${report.pdf.font-path:fonts/simhei.ttf}")
    private String fontPath;

    @Resource
    private FileInfoMapper fileInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private FileService fileService;

    /**
     * 获取（或生成）该论文当前版本的盲审副本。
     *
     * @return 已匿名化的 PDF 绝对路径
     */
    public Path getOrCreate(PaperInfo paper) {
        if (paper == null || paper.getId() == null || paper.getFileId() == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文或原始文件不存在", null);
        }
        FileInfo fileInfo = fileInfoMapper.selectById(paper.getFileId());
        if (fileInfo == null || !StringUtils.hasText(fileInfo.getStoragePath())) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文原始文件不存在", null);
        }

        Path target = buildTargetPath(paper, fileInfo);
        if (isUsable(target)) {
            return target;
        }

        String lockKey = target.toString();
        Object lock = GENERATION_LOCKS.computeIfAbsent(lockKey, k -> new Object());
        synchronized (lock) {
            try {
                if (isUsable(target)) {
                    return target;
                }
                Path dir = target.getParent();
                Files.createDirectories(dir);

                List<String> tokens = collectIdentityTokens(paper);
                String rawText = extractText(fileInfo);
                String body = redact(stripAcknowledgement(rawText), tokens);
                String title = redact(paper.getPaperTitle(), tokens);

                Path tempFile = Files.createTempFile(dir, "blind-", ".pdf");
                try {
                    writePdf(tempFile, title, body);
                    moveAtomically(tempFile, target);
                } catch (Exception e) {
                    Files.deleteIfExists(tempFile);
                    throw e;
                }
                log.info("盲审副本生成成功 - paperId: {}, fileId: {}, path: {}",
                        paper.getId(), fileInfo.getId(), target);
                return target;
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("生成盲审副本失败 - paperId: {}, fileId: {}",
                        paper.getId(), fileInfo.getId(), e);
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "盲审副本生成失败，请稍后重试", null);
            } finally {
                GENERATION_LOCKS.remove(lockKey, lock);
            }
        }
    }

    private Path buildTargetPath(PaperInfo paper, FileInfo fileInfo) {
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        return base.resolve(String.valueOf(paper.getId()))
                .resolve(fileInfo.getId() + ".pdf");
    }

    private boolean isUsable(Path path) {
        try {
            return Files.isRegularFile(path) && Files.size(path) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 抽取正文并做长度上限保护。
     */
    private String extractText(FileInfo fileInfo) {
        File source = fileService.resolveStoredFile(fileInfo.getStoragePath());
        if (source == null || !source.isFile()) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND,
                    "论文原始文件不存在，无法生成盲审副本", null);
        }
        try {
            String text = TIKA.parseToString(source);
            if (!StringUtils.hasText(text)) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND,
                        "论文正文为空，无法生成盲审副本", null);
            }
            if (text.length() > maxChars) {
                log.warn("盲审副本正文超长已截断 - fileId: {}, 原长度: {}, 上限: {}",
                        fileInfo.getId(), text.length(), maxChars);
                text = text.substring(0, maxChars) + "\n（正文过长，已截断）";
            }
            return text;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("盲审副本正文解析失败 - fileId: {}", fileInfo.getId(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR,
                    "论文解析失败，无法生成盲审副本", null);
        }
    }

    /**
     * 收集需要打码的身份词：论文 author、学生/导师姓名与用户名。
     * 长词优先，避免“张三丰”被“张三”先替换后剩下“丰”。
     */
    private List<String> collectIdentityTokens(PaperInfo paper) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        addToken(tokens, paper.getAuthor());
        addToken(tokens, paper.getStudentName());
        addToken(tokens, paper.getStudentUsername());
        addToken(tokens, paper.getTeacherName());
        addToken(tokens, paper.getTeacherRealName());

        if (paper.getStudentId() != null) {
            SysUser student = sysUserMapper.selectById(paper.getStudentId());
            if (student != null) {
                addToken(tokens, student.getRealName());
                addToken(tokens, student.getUsername());
            }
        }
        if (paper.getTeacherId() != null) {
            SysUser teacher = sysUserMapper.selectById(paper.getTeacherId());
            if (teacher != null) {
                addToken(tokens, teacher.getRealName());
                addToken(tokens, teacher.getUsername());
            }
        }

        List<String> sorted = new ArrayList<>();
        for (String token : tokens) {
            if (token != null && token.trim().length() >= 2) {
                sorted.add(token.trim());
            }
        }
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        return sorted;
    }

    private void addToken(LinkedHashSet<String> tokens, String value) {
        if (StringUtils.hasText(value)) {
            tokens.add(value.trim());
        }
    }

    /**
     * 身份词打码 + 邮箱/手机号打码。
     */
    private String redact(String text, List<String> tokens) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String result = text;
        for (String token : tokens) {
            result = result.replace(token, MASK);
        }
        result = EMAIL_PATTERN.matcher(result).replaceAll("[邮箱已隐去]");
        result = PHONE_PATTERN.matcher(result).replaceAll("[电话已隐去]");
        return result;
    }

    /**
     * 移除致谢章节：从最后一个“致谢/谢辞/Acknowledgements”标题行起，
     * 到下一个“参考文献/附录/References/Appendix”标题行为止；没有后续标题则删到结尾。
     */
    private String stripAcknowledgement(String text) {
        if (!removeAcknowledgement || !StringUtils.hasText(text)) {
            return text;
        }
        String[] lines = text.split("\\R");
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().matches("^(?i)(致\\s*谢|谢\\s*辞|acknowledge?ments?)$")) {
                start = i;
            }
        }
        if (start < 0) {
            return text;
        }
        int end = lines.length;
        for (int i = start + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.matches("^(?i)(参考文献|附\\s*录|references?|appendix.*)$")) {
                end = i;
                break;
            }
        }

        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < start; i++) {
            result.append(lines[i]).append('\n');
        }
        for (int i = end; i < lines.length; i++) {
            result.append(lines[i]).append('\n');
        }
        return result.toString();
    }

    /**
     * 生成纯文本 PDF。显式设置文档信息，避免沿用原文件的作者元数据。
     */
    private void writePdf(Path target, String title, String body) throws IOException {
        PdfFont font = loadFont();
        PdfWriter writer = new PdfWriter(target.toString());
        PdfDocument pdfDocument = new PdfDocument(writer);
        try {
            pdfDocument.getDocumentInfo()
                    .setTitle("盲审论文")
                    .setAuthor("盲审系统")
                    .setCreator("check-repeat-service");

            Document document = new Document(pdfDocument, PageSize.A4);
            document.setMargins(56, 56, 56, 56);
            document.setFont(font).setFontSize(10.5f);

            document.add(new Paragraph("盲审专用副本（已匿名化）")
                    .setBold().setFontSize(13).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph(StringUtils.hasText(title) ? title : "盲审论文")
                    .setBold().setFontSize(16).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph(" "));

            for (String line : body.split("\\R")) {
                String paragraphText = line.trim();
                if (paragraphText.isEmpty()) {
                    continue;
                }
                document.add(new Paragraph(paragraphText)
                        .setFirstLineIndent(21f)
                        .setMultipliedLeading(1.5f));
            }

            document.add(new Paragraph("—— 本文档由系统自动生成，已隐去作者、导师、致谢及联系方式 ——")
                    .setFontSize(9).setTextAlignment(TextAlignment.CENTER));
            document.close();
        } finally {
            pdfDocument.close();
            writer.close();
        }
    }

    private PdfFont loadFont() throws IOException {
        ClassPathResource fontResource = new ClassPathResource(fontPath);
        if (!fontResource.exists()) {
            throw new IOException("中文字体文件不存在: " + fontPath);
        }
        try (InputStream in = fontResource.getInputStream()) {
            return PdfFontFactory.createFont(in.readAllBytes(), PdfEncodings.IDENTITY_H);
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
