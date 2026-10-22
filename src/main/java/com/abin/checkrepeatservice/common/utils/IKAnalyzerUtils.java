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
import java.util.Locale;
import java.util.Set;

/**
 * IK中文分词工具类：处理文本分词与预处理
 */
@Slf4j
@Component
public class IKAnalyzerUtils {

    /**
     * 分词结果版本头（写入 MinIO 的分词文件带上此标记）。
     *
     * <p>英文归一（小写/连字符拆分/停用词）上线后，旧分词文件与当前词表不一致，
     * 引擎只认带当前版本头的结果，旧文件自动回落原文实时分词，
     * 避免「新词表的待查文本 vs 旧词表的库内文本」混算导致相似度失真。</p>
     */
    public static final String SEGMENT_VERSION_HEADER = "ik-v2\n";

    // 停用词集合（过滤无意义词汇，如“的”“在”“和”）
    private static final Set<String> STOP_WORDS = Set.of("的", "在", "和", "是", "我", "你", "他", "这", "那", "与", "及", "等", "了", "着", "过");

    /**
     * 英文停用词（学术文本高频、无区分度）。
     *
     * <p>IK 对连续字母已统一输出小写，这里按小写匹配；不清除英文虚词会让
     * 两篇无关英文论文因 the/of/is 等高频词共享维度而虚高相似度。</p>
     */
    private static final Set<String> EN_STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "than", "so", "such", "as",
            "of", "to", "in", "on", "at", "by", "for", "with", "from", "into", "over", "under",
            "about", "between", "through", "during", "before", "after", "above", "below",
            "is", "are", "was", "were", "be", "been", "being", "am", "do", "does", "did", "done",
            "have", "has", "had", "having", "will", "would", "shall", "should", "can", "could",
            "may", "might", "must", "not", "no", "nor", "only", "also", "very",
            "this", "that", "these", "those", "it", "its", "we", "our", "you", "your",
            "they", "their", "he", "she", "him", "her", "me", "my", "us", "them",
            "there", "here", "which", "who", "whom", "whose", "what", "when", "where", "why", "how",
            "all", "any", "both", "each", "few", "more", "most", "other", "some");

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
                // 英文归一后再过滤：小写、连字符复合词拆分（machine-learning → machine + learning）
                for (String word : normalizeLexeme(lexeme.getLexemeText())) {
                    // 过滤停用词（中/英）和长度≤1的无意义词
                    if (word.length() > 1 && !STOP_WORDS.contains(word) && !EN_STOP_WORDS.contains(word)) {
                        result.add(word);
                    }
                }
            }
        } catch (Exception e) {
            log.error("中文分词失败：", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR,"文本预处理失败，分词异常");
        }
        return result;
    }

    /**
     * 词元归一（中文原样返回，英文统一小写并拆分连字符复合词）。
     *
     * <p>IK 本身已把连续字母转小写，这里再显式小写是幂等兜底；
     * 连字符拆分是为了让「machine-learning」与「machine learning」命中同一批词，
     * 否则两种写法在余弦/SimHash 里是完全不同的词元。</p>
     */
    private static List<String> normalizeLexeme(String word) {
        if (word == null || word.isEmpty()) {
            return List.of();
        }
        // 含非 ASCII 字符（中文/中英混合）不做英文归一，保持原分词结果
        if (!isAscii(word)) {
            return List.of(word);
        }
        // IK 会把句末标点粘在英文词后（processing. / dog.），先削掉首尾非字母数字，
        // 否则同一单词带不带标点会被当成两个词
        String normalized = trimAsciiPunctuation(word.toLowerCase(Locale.ROOT));
        if (normalized.isEmpty()) {
            return List.of();
        }
        if (normalized.indexOf('-') < 0) {
            return List.of(normalized);
        }
        List<String> parts = new ArrayList<>(2);
        for (String part : normalized.split("-+")) {
            String trimmed = trimAsciiPunctuation(part);
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    private static String trimAsciiPunctuation(String word) {
        int start = 0;
        int end = word.length();
        while (start < end && !Character.isLetterOrDigit(word.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetterOrDigit(word.charAt(end - 1))) {
            end--;
        }
        return word.substring(start, end);
    }

    private static boolean isAscii(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (word.charAt(i) > 127) {
                return false;
            }
        }
        return true;
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
