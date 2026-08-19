package com.abin.checkrepeatservice.common.utils;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Apache Tika文本提取工具类：统一处理doc/docx/pdf等格式文件的文本提取
 */
public class TikaTextExtractor {
    // 日志记录
    private static final Logger log = LoggerFactory.getLogger(TikaTextExtractor.class);

    // 初始化Tika实例（自动识别文件类型）
    private static final Tika tika = new Tika();

    // 最大文本提取长度（默认10MB，避免超大文件导致内存溢出）
    private static final int MAX_EXTRACT_LENGTH = 10 * 1024 * 1024; // 10MB

    /**
     * 从文件路径提取文本（核心方法）
     * @param filePath 文件绝对路径（如：/data/paper/2025/11/09/xxx.pdf）
     * @return 提取的纯文本内容
     * @throws IOException    文件读取异常
     * @throws TikaException  Tika解析异常
     * @throws SAXException   XML解析异常
     */
    public static String extractTextFromFile(String filePath) throws IOException, TikaException, SAXException {
        // 添加空值检查
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "论文文件路径不能为空");
        }
        // 1. 校验文件合法性
        File file = new File(filePath);
        if (!file.exists()) {
            log.error("文件不存在：{}", filePath);
            throw new BusinessException(ResultCode.PARAM_ERROR,"文件不存在：" + filePath);
        }
        if (!file.isFile()) {
            log.error("路径不是有效文件：{}", filePath);
            throw new BusinessException(ResultCode.PARAM_ERROR,"路径不是有效文件：" + filePath);
        }
        // 校验文件大小（避免超大文件）
        if (file.length() > MAX_EXTRACT_LENGTH) {
            log.error("文件过大（超过{}MB）：{}", MAX_EXTRACT_LENGTH / 1024 / 1024, filePath);
            throw new BusinessException(ResultCode.PARAM_ERROR,"文件过大，仅支持" + MAX_EXTRACT_LENGTH / 1024 / 1024 + "MB以内的文件");
        }

        // 2. 读取文件流并提取文本（try-with-resources自动关闭流）
        try (InputStream inputStream = new FileInputStream(file)) {
            // 方式1：简易提取（适合大部分场景，自动识别格式）
            String text = tika.parseToString(inputStream);

            // 方式2：复杂提取（自定义元数据，适合需要文件属性的场景，可选）
            // String text = extractTextWithMetadata(inputStream, file.getName());

            // 文本清洗（去除多余空格、换行符）
            return cleanText(text);
        } catch (IOException e) {
            log.error("文件读取失败（路径：{}）：", filePath, e);
            throw e; // 抛出异常由上层处理
        } catch (TikaException e) {
            log.error("Tika解析文件失败（路径：{}）：", filePath, e);
            throw e;
        }
    }

    /**
     * 带元数据的文本提取（扩展方法，如需获取文件作者、创建时间等属性时使用）
     * @param inputStream 文件输入流
     * @param fileName    文件名（用于辅助识别格式）
     * @return 提取的纯文本内容
     */
    private static String extractTextWithMetadata(InputStream inputStream, String fileName) throws IOException, TikaException, SAXException {
        // 1. 初始化元数据（可获取文件作者、创建时间等信息）
        Metadata metadata = new Metadata();
        metadata.set("resourceName", fileName); // 设置文件名，辅助格式识别

        // 2. 初始化内容处理器（限制文本长度，避免内存溢出）
        ContentHandler contentHandler = new BodyContentHandler(MAX_EXTRACT_LENGTH);

        // 3. 初始化自动识别解析器（支持多种格式）
        Parser parser = new AutoDetectParser();
        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, parser); // 绑定解析器

        // 4. 解析文件并提取文本
        parser.parse(inputStream, contentHandler, metadata, parseContext);

        // 5. （可选）打印元数据（如需要文件属性可开启）
        log.debug("文件元数据 - 文件名：{}，作者：{}，创建时间：{}",
                metadata.get("resourceName"),
                metadata.get("AUTHOR"),
                metadata.get("Creation-Date"));

        // 6. 清洗并返回文本
        return cleanText(contentHandler.toString());
    }

    /**
     * 文本清洗：去除多余空格、换行符、特殊字符，统一格式
     * @param text 原始提取文本
     * @return 清洗后的文本
     */
    private static String cleanText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        // 1. 去除多余换行符（将\r\n、\r统一转为\n，再合并多个\n为一个）
        String cleaned = text.replaceAll("\\r\\n|\\r", "\n")
                .replaceAll("\\n+", "\n")
                // 2. 去除多余空格（合并多个空格为一个）
                .replaceAll("\\s+", " ")
                // 3. 去除首尾空格/换行符
                .trim();
        // 4. 处理中文特殊空格（全角空格转半角）
        return cleaned.replaceAll("　", " ");
    }

    /**
     * 快速判断文件是否为支持的文本提取格式（预处理校验）
     * @param filePath 文件路径
     * @return true=支持，false=不支持
     */
    public static boolean isSupportedFormat(String filePath) {
        if (filePath == null || filePath.lastIndexOf(".") == -1) {
            return false;
        }
        // 支持的文件后缀（全小写）
        String[] supportedSuffixes = {"doc", "docx", "pdf", "txt", "ppt", "pptx", "xls", "xlsx"};
        String suffix = filePath.substring(filePath.lastIndexOf(".") + 1).toLowerCase();
        for (String s : supportedSuffixes) {
            if (s.equals(suffix)) {
                return true;
            }
        }
        log.warn("不支持的文件格式（路径：{}），仅支持{}", filePath, String.join(",", supportedSuffixes));
        return false;
    }
}
