package com.abin.checkrepeatservice.common.utils;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wltea.analyzer.core.IKSegmenter;
import org.wltea.analyzer.core.Lexeme;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * IK中文分词工具类：处理文本分词与预处理
 */
@Slf4j
@Component
public class IKAnalyzerUtils {

    // 停用词集合（过滤无意义词汇，如“的”“在”“和”）
    private static final Set<String> STOP_WORDS = Set.of("的", "在", "和", "是", "我", "你", "他", "这", "那", "与", "及", "等", "了", "着", "过");

    /**
     * 中文分词（智能分词模式，适合学术文本）
     * @param text 待分词文本
     * @return 分词结果（去停用词后）
     */
    public static List<String> segment(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        try (StringReader reader = new StringReader(text.trim())) {
            // 开启智能分词模式（true=智能模式，false=最细粒度模式）
            IKSegmenter segmenter = new IKSegmenter(reader, true);
            Lexeme lexeme;
            while ((lexeme = segmenter.next()) != null) {
                String word = lexeme.getLexemeText();
                // 过滤停用词和长度≤1的无意义词
                if (!STOP_WORDS.contains(word) && word.length() > 1) {
                    result.add(word);
                }
            }
        } catch (Exception e) {
            log.error("中文分词失败：", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR,"文本预处理失败，分词异常");
        }
        return result;
    }

    /**
     * 分词结果转字符串（用于SimHash计算）
     * @param text 待分词文本
     * @return 分词后拼接的字符串（空格分隔）
     */
    public static String segmentToString(String text) {
        List<String> words = segment(text);
        return String.join(" ", words);
    }
}
