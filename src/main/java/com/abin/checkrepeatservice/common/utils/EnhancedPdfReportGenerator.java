package com.abin.checkrepeatservice.common.utils;

import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 增强版PDF报告生成器
 * 提供更丰富的报告内容和更好的视觉效果
 */
@Component
@Slf4j
public class EnhancedPdfReportGenerator {
    
    @Value("${report.pdf.font-path:fonts/SimHei.ttf}")
    private String fontPath;
    
    @Value("${report.pdf.page-size:A4}")
    private String pageSize;
    
    @Value("${report.pdf.margin:50}")
    private float margin;
    
    // 颜色定义
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(25, 118, 210);  // 主色调蓝色
    private static final DeviceRgb SUCCESS_COLOR = new DeviceRgb(46, 125, 50);   // 成功绿色
    private static final DeviceRgb WARNING_COLOR = new DeviceRgb(239, 108, 0);   // 警告橙色
    private static final DeviceRgb DANGER_COLOR = new DeviceRgb(211, 47, 47);    // 危险红色
    private static final DeviceRgb RED_COLOR = new DeviceRgb(255, 0, 0);         // 标红
    private static final DeviceRgb BLACK_COLOR = new DeviceRgb(0, 0, 0);         // 黑色
    
    // 字体大小
    private static final float TITLE_SIZE = 20;
    private static final float HEADING_SIZE = 16;
    private static final float SUBHEADING_SIZE = 14;
    private static final float CONTENT_SIZE = 10;
    
    /**
     * 生成增强版查重报告
     */
    public void generateEnhancedReport(ReportPreviewDTO previewDTO, OutputStream outputStream) {
        try {
            log.info("开始生成增强版PDF报告...");
            
            // 加载字体
            PdfFont font = loadChineseFont();
            
            // 初始化PDF文档
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDoc = new PdfDocument(writer);
            PageSize pageSizeEnum = getPageEnum();
            Document document = new Document(pdfDoc, pageSizeEnum);
            document.setMargins(margin, margin, margin, margin);
            
            // 生成报告各部分内容
            generateCoverPage(document, font, previewDTO);           // 封面
            document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            generateSummarySection(document, font, previewDTO);      // 摘要
            generateDetailedAnalysis(document, font, previewDTO);    // 详细分析
            generateSimilaritySources(document, font, previewDTO);   // 相似来源
            generateRecommendations(document, font, previewDTO);     // 改进建议
            
            // 关闭文档
            document.close();
            pdfDoc.close();
            writer.close();
            
            log.info("增强版PDF报告生成完成");
            
        } catch (Exception e) {
            log.error("增强版PDF报告生成失败", e);
            throw new RuntimeException("报告生成失败", e);
        }
    }
    
    /**
     * 生成报告封面
     */
    private void generateCoverPage(Document document, PdfFont font, ReportPreviewDTO previewDTO) {
        ReportPreviewDTO.ReportBaseInfoDTO baseInfo = previewDTO.getBaseInfo();
        
        // 标题
        Paragraph title = new Paragraph("论文查重检测报告")
                .setFont(font)
                .setFontSize(TITLE_SIZE)
                .setFontColor(PRIMARY_COLOR)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);
        
        // 副标题
        Paragraph subtitle = new Paragraph("Plagiarism Detection Report")
                .setFont(font)
                .setFontSize(SUBHEADING_SIZE)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(subtitle);
        
        document.add(new Paragraph("\n\n"));
        
        // 基本信息表格
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{30, 70}));
        infoTable.setWidth(UnitValue.createPercentValue(100));
        
        if (baseInfo != null) {
            addInfoRow(infoTable, font, "报告编号", baseInfo.getReportNo());
            addInfoRow(infoTable, font, "检测时间", 
                      LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss")));
            addInfoRow(infoTable, font, "论文标题", baseInfo.getPaperTitle());
            addInfoRow(infoTable, font, "作者", baseInfo.getAuthor());
            addInfoRow(infoTable, font, "指导教师", baseInfo.getTeacherName());
        }
        
        document.add(infoTable);
        
        // 重复率展示（大号字体）
        if (baseInfo != null && baseInfo.getSimilarityRate() != null) {
            document.add(new Paragraph("\n\n"));
            
            String rateText = String.format("%.2f%%", baseInfo.getSimilarityRate());
            DeviceRgb rateColor = getRateColor(baseInfo.getSimilarityRate());
            
            Paragraph rateDisplay = new Paragraph("整体重复率: " + rateText)
                    .setFont(font)
                    .setFontSize(24)
                    .setFontColor(rateColor)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(rateDisplay);
        }
    }
    
    /**
     * 生成摘要部分
     */
    private void generateSummarySection(Document document, PdfFont font, ReportPreviewDTO previewDTO) {
        ReportPreviewDTO.ReportBaseInfoDTO baseInfo = previewDTO.getBaseInfo();
        
        // 章节标题
        addSectionHeading(document, font, "检测摘要");
        
        // 统计数据表格
        Table statTable = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25}));
        statTable.setWidth(UnitValue.createPercentValue(100));
        
        // 表头
        addTableHeader(statTable, font, "总重复率");
        addTableHeader(statTable, font, "原创率");
        addTableHeader(statTable, font, "重复段落");
        addTableHeader(statTable, font, "总段落");
        
        // 数据行
        if (baseInfo != null) {
            BigDecimal similarity = baseInfo.getSimilarityRate() != null ? 
                                  baseInfo.getSimilarityRate() : BigDecimal.ZERO;
            BigDecimal originality = BigDecimal.valueOf(100).subtract(similarity);
            
            addTableData(statTable, font, String.format("%.2f%%", similarity), getRateColor(similarity));
            addTableData(statTable, font, String.format("%.2f%%", originality), 
                        originality.compareTo(BigDecimal.valueOf(80)) >= 0 ? SUCCESS_COLOR : WARNING_COLOR);
            addTableData(statTable, font, String.valueOf(previewDTO.getParagraphs() != null ? 
                       previewDTO.getParagraphs().size() : 0));
            addTableData(statTable, font, String.valueOf(previewDTO.getParagraphs() != null ? 
                       previewDTO.getParagraphs().size() : 0));
        }
        
        document.add(statTable);
        
        // 风险等级评估
        if (baseInfo != null && baseInfo.getSimilarityRate() != null) {
            document.add(new Paragraph("\n"));
            String riskLevel = getRiskLevel(baseInfo.getSimilarityRate());
            DeviceRgb riskColor = getRiskColor(baseInfo.getSimilarityRate());
            
            Paragraph riskText = new Paragraph("风险等级: " + riskLevel)
                    .setFont(font)
                    .setFontSize(CONTENT_SIZE)
                    .setFontColor(riskColor)
                    .setBold();
            document.add(riskText);
        }
    }
    
    /**
     * 生成详细分析
     */
    private void generateDetailedAnalysis(Document document, PdfFont font, ReportPreviewDTO previewDTO) {
        addSectionHeading(document, font, "详细分析");
        
        List<ReportPreviewDTO.ReportParagraphDTO> paragraphs = previewDTO.getParagraphs();
        if (paragraphs == null || paragraphs.isEmpty()) {
            Paragraph noData = new Paragraph("暂无段落分析数据")
                    .setFont(font)
                    .setFontSize(CONTENT_SIZE)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(noData);
            return;
        }
        
        // 按相似度排序显示前10个段落
        paragraphs.stream()
                .sorted((p1, p2) -> {
                    Double s1 = p1.getSimilarity() != null ? p1.getSimilarity() : 0.0;
                    Double s2 = p2.getSimilarity() != null ? p2.getSimilarity() : 0.0;
                    return s2.compareTo(s1); // 降序排列
                })
                .limit(10)
                .forEach(paragraph -> {
                    addParagraphAnalysis(document, font, paragraph);
                    document.add(new Paragraph("\n"));
                });
    }
    
    /**
     * 生成相似来源列表
     */
    private void generateSimilaritySources(Document document, PdfFont font, ReportPreviewDTO previewDTO) {
        addSectionHeading(document, font, "相似来源");
        
        List<ReportPreviewDTO.ReportSimilarSourceDTO> sources = previewDTO.getSimilarSources();
        if (sources == null || sources.isEmpty()) {
            Paragraph noSources = new Paragraph("未发现显著相似来源")
                    .setFont(font)
                    .setFontSize(CONTENT_SIZE)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(noSources);
            return;
        }
        
        Table sourceTable = new Table(UnitValue.createPercentArray(new float[]{8, 35, 20, 20, 17}));
        sourceTable.setWidth(UnitValue.createPercentValue(100));
        
        // 表头
        addTableHeader(sourceTable, font, "序号");
        addTableHeader(sourceTable, font, "来源标题");
        addTableHeader(sourceTable, font, "来源类型");
        addTableHeader(sourceTable, font, "最大相似度");
        addTableHeader(sourceTable, font, "匹配段落");
        
        // 数据行
        for (int i = 0; i < sources.size(); i++) {
            ReportPreviewDTO.ReportSimilarSourceDTO source = sources.get(i);
            addTableData(sourceTable, font, String.valueOf(i + 1));
            addTableData(sourceTable, font, source.getSourceName() != null ? source.getSourceName() : "");
            addTableData(sourceTable, font, source.getSourceType() != null ? source.getSourceType() : "");
            
            if (source.getMaxSimilarity() != null) {
                String similarityText = String.format("%.2f%%", source.getMaxSimilarity());
                DeviceRgb color = getRateColor(BigDecimal.valueOf(source.getMaxSimilarity()));
                addTableData(sourceTable, font, similarityText, color);
            } else {
                addTableData(sourceTable, font, "0.00%");
            }
            
            addTableData(sourceTable, font, source.getMatchedParagraphs() != null ?
                        source.getMatchedParagraphs() : "0");
        }
        
        document.add(sourceTable);
    }
    
    /**
     * 生成改进建议
     */
    private void generateRecommendations(Document document, PdfFont font, ReportPreviewDTO previewDTO) {
        addSectionHeading(document, font, "改进建议");
        
        ReportPreviewDTO.ReportBaseInfoDTO baseInfo = previewDTO.getBaseInfo();
        BigDecimal similarity = baseInfo != null && baseInfo.getSimilarityRate() != null ? 
                               baseInfo.getSimilarityRate() : BigDecimal.ZERO;
        
        String[] recommendations = getRecommendations(similarity);
        
        for (String recommendation : recommendations) {
            Paragraph recPara = new Paragraph("• " + recommendation)
                    .setFont(font)
                    .setFontSize(CONTENT_SIZE)
                    .setMarginBottom(5);
            document.add(recPara);
        }
    }
    
    // ------------------------------ 辅助方法 ------------------------------
    
    private PdfFont loadChineseFont() throws Exception {
        ClassPathResource fontResource = new ClassPathResource(fontPath);
        if (!fontResource.exists()) {
            throw new RuntimeException("字体文件不存在: " + fontPath);
        }
        return PdfFontFactory.createFont(fontResource.getFile().getAbsolutePath(), PdfEncodings.IDENTITY_H);
    }
    
    private PageSize getPageEnum() {
        return switch (pageSize.toUpperCase()) {
            case "A3" -> PageSize.A3;
            case "LETTER" -> PageSize.LETTER;
            default -> PageSize.A4;
        };
    }
    
    private void addSectionHeading(Document document, PdfFont font, String title) {
        document.add(new Paragraph("\n"));
        Paragraph heading = new Paragraph(title)
                .setFont(font)
                .setFontSize(HEADING_SIZE)
                .setFontColor(PRIMARY_COLOR)
                .setBold()
                .setMarginBottom(10);
        document.add(heading);
    }
    
    private void addInfoRow(Table table, PdfFont font, String label, String value) {
        Paragraph labelPara = new Paragraph(label + ":")
                .setFont(font)
                .setFontSize(CONTENT_SIZE)
                .setBold()
                .setTextAlignment(TextAlignment.RIGHT);
        table.addCell(labelPara);
        
        Paragraph valuePara = new Paragraph(value != null ? value : "")
                .setFont(font)
                .setFontSize(CONTENT_SIZE)
                .setTextAlignment(TextAlignment.LEFT);
        table.addCell(valuePara);
    }
    
    private void addTableHeader(Table table, PdfFont font, String text) {
        Paragraph header = new Paragraph(text)
                .setFont(font)
                .setFontSize(CONTENT_SIZE)
                .setFontColor(ColorConstants.WHITE)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        header.setBackgroundColor(PRIMARY_COLOR);
        table.addHeaderCell(header);
    }
    
    private void addTableData(Table table, PdfFont font, String text) {
        addTableData(table, font, text, BLACK_COLOR);
    }
    
    private void addTableData(Table table, PdfFont font, String text, DeviceRgb color) {
        Paragraph data = new Paragraph(text)
                .setFont(font)
                .setFontSize(CONTENT_SIZE)
                .setFontColor(color)
                .setTextAlignment(TextAlignment.CENTER);
        table.addCell(data);
    }
    
    private void addParagraphAnalysis(Document document, PdfFont font, ReportPreviewDTO.ReportParagraphDTO paragraph) {
        // 段落标题
        String title = String.format("段落 %d (相似度: %.2f%%)", 
                                   paragraph.getParagraphNo(),
                                   paragraph.getSimilarity() != null ? paragraph.getSimilarity() : 0.0);
        DeviceRgb titleColor = getRateColor(BigDecimal.valueOf(
                paragraph.getSimilarity() != null ? paragraph.getSimilarity() : 0.0));
        
        Paragraph titlePara = new Paragraph(title)
                .setFont(font)
                .setFontSize(SUBHEADING_SIZE)
                .setFontColor(titleColor)
                .setBold()
                .setMarginBottom(5);
        document.add(titlePara);
        
        // 段落内容
        String content = paragraph.getContent() != null ? paragraph.getContent() : "[空段落]";
        Paragraph contentPara = new Paragraph(content)
                .setFont(font)
                .setFontSize(CONTENT_SIZE)
                .setFontColor(BLACK_COLOR);
        document.add(contentPara);
    }
    
    private DeviceRgb getRateColor(BigDecimal rate) {
        double value = rate.doubleValue();
        if (value <= 10) return SUCCESS_COLOR;
        if (value <= 20) return WARNING_COLOR;
        return DANGER_COLOR;
    }
    
    private DeviceRgb getRiskColor(BigDecimal rate) {
        return getRateColor(rate);
    }
    
    private String getRiskLevel(BigDecimal rate) {
        double value = rate.doubleValue();
        if (value <= 10) return "低风险";
        if (value <= 20) return "中等风险";
        if (value <= 30) return "高风险";
        return "极高风险";
    }
    
    private String[] getRecommendations(BigDecimal similarity) {
        double rate = similarity.doubleValue();
        if (rate <= 10) {
            return new String[]{
                "论文原创性良好，继续保持",
                "建议仔细检查引用格式是否规范",
                "可以考虑增加更多个人观点和分析"
            };
        } else if (rate <= 20) {
            return new String[]{
                "存在一定程度的重复内容，需要修改",
                "重点检查高相似度段落的表述方式",
                "加强文献综述部分的原创性表达",
                "确保所有引用都有正确的标注"
            };
        } else {
            return new String[]{
                "重复率较高，建议大幅修改",
                "重新组织高相似度段落的内容结构",
                "增加原创性分析和观点阐述",
                "严格按照学术规范处理引用内容",
                "建议寻求导师指导进行针对性修改"
            };
        }
    }
}