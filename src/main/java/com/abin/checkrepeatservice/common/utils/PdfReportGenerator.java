package com.abin.checkrepeatservice.common.utils;


import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF报告生成工具类：基于iText 7实现，支持中文与标红
 */
@Component
@Slf4j
public class PdfReportGenerator {

    // 中文字体路径（从配置文件获取）
    @Value("${report.pdf.font-path}")
    private String fontPath;

    // PDF页面大小（从配置文件获取）
    @Value("${report.pdf.page-size}")
    private String pageSize;

    // 页面边距（单位：pt，从配置文件获取）
    @Value("${report.pdf.margin}")
    private float margin;

    // 标红颜色（RGB：255,0,0）
    private static final Color RED_COLOR = new DeviceRgb(255, 0, 0);
    // 普通文本颜色（RGB：0,0,0）
    private static final Color BLACK_COLOR = new DeviceRgb(0, 0, 0);
    // 标题字体大小（pt）
    private static final float TITLE_FONT_SIZE = 16;
    // 副标题字体大小（pt）
    private static final float SUBTITLE_FONT_SIZE = 12;
    // 正文字体大小（pt）
    private static final float CONTENT_FONT_SIZE = 10;

    /**
     * 生成PDF报告到指定输出流（用于下载）
     * @param previewDTO 报告预览数据
     * @param outputStream 输出流（如HttpServletResponse的输出流）
     */
    public void generatePdf(ReportPreviewDTO previewDTO, OutputStream outputStream) {
        try {
            log.info("开始生成PDF内容...");

            // 参数验证 - 添加空值检查
            if (previewDTO == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "报告数据不能为空");
            }

            // 确保段落列表不为null
            if (previewDTO.getParagraphs() == null) {
                log.warn("段落列表为null，初始化为空列表");
                previewDTO.setParagraphs(new ArrayList<>());
            }

            // 确保相似来源列表不为null
            if (previewDTO.getSimilarSources() == null) {
                log.warn("相似来源列表为null，初始化为空列表");
                previewDTO.setSimilarSources(new ArrayList<>());
            }

            // 1. 加载中文字体（解决中文乱码）
            PdfFont simHeiFont = loadSimHeiFont();

            // 2. 初始化PDF文档（设置页面大小与边距）
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDoc = new PdfDocument(writer);
            // 适配页面大小（A4/A3/Letter）
            PageSize pageSizeEnum = switch (pageSize.toUpperCase()) {
                case "A3" -> PageSize.A3;
                case "LETTER" -> PageSize.LETTER;
                default -> PageSize.A4;
            };
            Document document = new Document(pdfDoc, pageSizeEnum);
            document.setMargins(margin, margin, margin, margin); // 上右下左边距

            // 3. 添加报告标题
            Paragraph title = new Paragraph("论文查重报告")
                    .setFont(simHeiFont)
                    .setFontSize(TITLE_FONT_SIZE)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBold();
            document.add(title);
            document.add(new Paragraph("\n")); // 换行

            // 4. 添加报告基础信息表格（2列：左标题右内容）
            Table baseInfoTable = new Table(UnitValue.createPercentArray(new float[]{30, 70}));
            baseInfoTable.setWidth(UnitValue.createPercentValue(100));
            ReportPreviewDTO.ReportBaseInfoDTO baseInfo = previewDTO.getBaseInfo();

            // 添加基础信息表格行
            if (baseInfo != null) {
                addTableField(baseInfoTable, simHeiFont, "报告编号", baseInfo.getReportNo());
                addTableField(baseInfoTable, simHeiFont, "任务编号", baseInfo.getTaskNo());
                addTableField(baseInfoTable, simHeiFont, "论文标题", baseInfo.getPaperTitle());
                addTableField(baseInfoTable, simHeiFont, "学生姓名", baseInfo.getStudentName());
                addTableField(baseInfoTable, simHeiFont, "指导教师", baseInfo.getTeacherName());
                addTableField(baseInfoTable, simHeiFont, "生成时间", baseInfo.getGenerateTime());
            } else {
                // 如果基础信息为空，添加提示
                addTableField(baseInfoTable, simHeiFont, "提示", "暂无基础信息");
            }
            document.add(baseInfoTable);
            document.add(new Paragraph("\n"));

            // 5. 添加重复率统计表格
            ReportPreviewDTO.ReportRateStatDTO rateStat = previewDTO.getRateStat();
            if (rateStat != null) {
                Table rateTable = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25}));
                rateTable.setWidth(UnitValue.createPercentValue(100));
                // 表头
                addTableHeader(rateTable, simHeiFont, "总重复率");
                addTableHeader(rateTable, simHeiFont, "原创率");
                addTableHeader(rateTable, simHeiFont, "重复段落数");
                addTableHeader(rateTable, simHeiFont, "总段落数");
                // 数据行
                addTableData(rateTable, simHeiFont, String.format("%.2f%%", rateStat.getRepeatRate() != null ? rateStat.getRepeatRate() : BigDecimal.ZERO));
                addTableData(rateTable, simHeiFont, String.format("%.2f%%", rateStat.getOriginalRate() != null ? rateStat.getOriginalRate() : BigDecimal.ZERO));
                addTableData(rateTable, simHeiFont, rateStat.getRepeatParagraphCount() != null ? rateStat.getRepeatParagraphCount().toString() : "0");
                addTableData(rateTable, simHeiFont, rateStat.getTotalParagraphCount() != null ? rateStat.getTotalParagraphCount().toString() : "0");
                document.add(rateTable);
            } else {
                // 如果rateStat为null，添加占位信息
                Paragraph emptyRateParagraph = new Paragraph("暂无重复率统计数据")
                        .setFont(simHeiFont)
                        .setFontSize(CONTENT_FONT_SIZE)
                        .setFontColor(ColorConstants.GRAY);
                document.add(emptyRateParagraph);
            }
            document.add(new Paragraph("\n"));

            // 6. 添加段落内容 - 修复空指针问题
            List<ReportPreviewDTO.ReportParagraphDTO> paragraphs = previewDTO.getParagraphs();
            if (paragraphs.isEmpty()) {
                // 如果没有段落数据，添加提示
                Paragraph noDataParagraph = new Paragraph("暂无段落数据")
                        .setFont(simHeiFont)
                        .setFontSize(CONTENT_FONT_SIZE)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.CENTER);
                document.add(noDataParagraph);
                document.add(new Paragraph("\n"));
            } else {
                for (ReportPreviewDTO.ReportParagraphDTO paragraph : paragraphs) {
                    // 对每个段落进行空值检查
                    if (paragraph == null) {
                        continue;
                    }

                    // 段落序号与重复率标题
                    String paraTitle = String.format("段落%d（重复率：%.2f%%）",
                            paragraph.getParagraphNo(),
                            paragraph.getSimilarity() != null ? paragraph.getSimilarity() : 0.0);
                    Paragraph paraHeader = new Paragraph(paraTitle)
                            .setFont(simHeiFont)
                            .setFontSize(CONTENT_FONT_SIZE)
                            .setItalic();
                    document.add(paraHeader);

                    // 段落内容（处理标红：解析<span style="color:red">标签）
                    Paragraph paraContent = new Paragraph()
                            .setFont(simHeiFont)
                            .setFontSize(CONTENT_FONT_SIZE);

                    String content = paragraph.getContent();
                    if (content == null || content.trim().isEmpty()) {
                        content = "【空段落】";
                    }

                    if (content.contains("<span style=\"color:red\">")) {
                        // 分割标红部分与普通部分
                        String[] parts = content.split("(<span style=\"color:red\">|</span>)");
                        for (String part : parts) {
                            if (part.isEmpty()) continue;
                            // 判断是否为标红内容（被标签包裹的部分）
                            if (content.indexOf("<span style=\"color:red\">" + part + "</span>") != -1) {
                                paraContent.add(part).setFontColor(RED_COLOR);
                            } else {
                                paraContent.add(part).setFontColor(BLACK_COLOR);
                            }
                        }
                    } else {
                        // 无标红，直接添加
                        paraContent.add(content).setFontColor(BLACK_COLOR);
                    }
                    document.add(paraContent);
                    document.add(new Paragraph("\n"));
                }
            }

            // 7. 添加相似来源列表
            List<ReportPreviewDTO.ReportSimilarSourceDTO> similarSources = previewDTO.getSimilarSources();
            if (!similarSources.isEmpty()) {
                Paragraph sourceTitle = new Paragraph("相似来源列表")
                        .setFont(simHeiFont)
                        .setFontSize(SUBTITLE_FONT_SIZE)
                        .setBold();
                document.add(sourceTitle);
                document.add(new Paragraph("\n"));

                Table sourceTable = new Table(UnitValue.createPercentArray(new float[]{10, 30, 20, 40}));
                sourceTable.setWidth(UnitValue.createPercentValue(100));
                // 表头
                addTableHeader(sourceTable, simHeiFont, "序号");
                addTableHeader(sourceTable, simHeiFont, "来源名称");
                addTableHeader(sourceTable, simHeiFont, "来源类型");
                addTableHeader(sourceTable, simHeiFont, "最大相似度");
                // 数据行
                int sourceSeq = 1;
                for (ReportPreviewDTO.ReportSimilarSourceDTO source : similarSources) {
                    if (source == null) continue;

                    addTableData(sourceTable, simHeiFont, String.valueOf(sourceSeq++));
                    addTableData(sourceTable, simHeiFont, source.getSourceName() != null ? source.getSourceName() : "");
                    addTableData(sourceTable, simHeiFont, source.getSourceType() != null ? source.getSourceType() : "");
                    addTableData(sourceTable, simHeiFont, String.format("%.2f%%", source.getMaxSimilarity() != null ? source.getMaxSimilarity() : 0.0));
                }
                document.add(sourceTable);
            } else {
                Paragraph noSourceParagraph = new Paragraph("无相似来源")
                        .setFont(simHeiFont)
                        .setFontSize(CONTENT_FONT_SIZE)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.CENTER);
                document.add(noSourceParagraph);
            }

            // 8. 关闭文档（自动刷新输出流）
            document.close();
            pdfDoc.close();
            writer.close();
            log.info("PDF内容生成完成");
        } catch (Exception e) {
            log.error("PDF报告生成失败：", e);
            throw new BusinessException(ResultCode.PARAM_ERROR,"PDF报告生成异常，请重试");
        }
    }

    /**
     * 生成PDF报告到本地文件（用于存储）
     * @param previewDTO 报告预览数据
     * @param reportPath 报告存储路径
     */
    public void generatePdfToFile(ReportPreviewDTO previewDTO, String reportPath) {
        try {
            // 创建父目录（若不存在）
            Path path = Paths.get(reportPath);
            if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            // 生成PDF到文件
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                generatePdf(previewDTO, outputStream);
            }
            log.info("PDF报告生成成功，存储路径：{}", reportPath);
        } catch (Exception e) {
            log.error("PDF报告写入文件失败（路径：{}）：", reportPath, e);
            throw new BusinessException(ResultCode.PARAM_ERROR,"报告存储异常，请联系管理员");
        }
    }

    // ------------------------------ 私有辅助方法 ------------------------------
    /**
     * 加载中文字体（simhei.ttf）
     */
    private PdfFont loadSimHeiFont() throws Exception {
        try {
            ClassPathResource fontResource = new ClassPathResource(fontPath);
            if (!fontResource.exists()) {
                log.error("中文字体文件不存在，请检查配置路径：{}", fontPath);
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "中文字体文件不存在，请检查配置路径：" + fontPath);
            }

            // 以字节流读取：打包成 fat jar 后资源在 BOOT-INF/classes 内，
            // 无法通过 getFile() 解析为文件路径
            try (var in = fontResource.getInputStream()) {
                return PdfFontFactory.createFont(in.readAllBytes(), PdfEncodings.IDENTITY_H);
            }
        } catch (IOException e) {
            log.error("加载字体文件失败，路径：{}", fontPath, e);
            // S15：内部字体路径/IO 细节不外泄给客户端，详情见服务端日志
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "报告生成失败，请稍后重试");
        }
    }

    /**
     * 向表格添加字段（标题+内容，用于基础信息表）
     */
    private void addTableField(Table table, PdfFont font, String label, String value) {
        // 标题列（右对齐，加粗）
        Paragraph labelPara = new Paragraph(label + "：")
                .setFont(font)
                .setFontSize(CONTENT_FONT_SIZE)
                .setBold()
                .setTextAlignment(TextAlignment.RIGHT);
        table.addCell(labelPara);
        // 内容列（左对齐）
        Paragraph valuePara = new Paragraph(value == null ? "" : value)
                .setFont(font)
                .setFontSize(CONTENT_FONT_SIZE)
                .setTextAlignment(TextAlignment.LEFT);
        table.addCell(valuePara);
    }

    /**
     * 向表格添加表头（居中，加粗）
     */
    private void addTableHeader(Table table, PdfFont font, String headerText) {
        Paragraph headerPara = new Paragraph(headerText)
                .setFont(font)
                .setFontSize(CONTENT_FONT_SIZE)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        table.addCell(headerPara);
    }

    /**
     * 向表格添加数据（居中）
     */
    private void addTableData(Table table, PdfFont font, String dataText) {
        Paragraph dataPara = new Paragraph(dataText == null ? "" : dataText)
                .setFont(font)
                .setFontSize(CONTENT_FONT_SIZE)
                .setTextAlignment(TextAlignment.CENTER);
        table.addCell(dataPara);
    }
}
