package com.abin.checkrepeatservice.common.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 句级切分与引文识别工具（论文全生命周期 P5）
 *
 * 职责：
 * 1. 句级切分：按中英文句末标点与换行切句，保留原文偏移，丢弃过短句；
 * 2. 参考文献区块定位：匹配最后一次出现的「参考文献 / 参 考 文 献 / References」标题行，
 *    该行至文末整体剔除、不计重（行业惯例：参考文献本身相似属正常）；
 * 3. 引用句识别：句尾带 [n] / [n,m] 引注样式标记为引用句。
 *
 * 降级策略（刻意保守）：只有文档确实存在参考文献区块时才启用引用句标记，
 * 没有区块的文档（如未按模板写的正文）中出现的 [n] 一律按普通文本计重，
 * 避免「图[1]」「表[2]」类排版误标导致漏判。
 */
public final class SentenceSplitterUtils {

    private SentenceSplitterUtils() {
    }

    /**
     * 句末分隔符：中英文句号/感叹/问号/分号 + 换行
     */
    private static final Pattern SENTENCE_DELIMITER = Pattern.compile("[。！？!?；;\\n]+");

    /**
     * 参考文献标题行：行首（允许空白）恰好是标题词本身，避免命中正文中的「本文参考文献如下：」
     * (?im) 忽略大小写 + 多行模式；「参 考 文 献」兼容字间空格的排版
     */
    private static final Pattern REFERENCES_HEADING = Pattern.compile(
            "(?im)^[ \\t]*(参考文献|参\\s*考\\s*文\\s*献|references)[ \\t]*$");

    /**
     * 句尾引注：[1]、[1,2]、[1-3] 等数字引注收尾（仅识别半角方括号）
     */
    private static final Pattern TAIL_CITATION = Pattern.compile("\\[\\d+([,-]\\d+)*]\\s*$");

    /**
     * 短于该长度（去空白字符数）的句不参与比对与计重（标题、页码残句等）
     */
    public static final int MIN_SENTENCE_CHAR_COUNT = 6;

    /**
     * 句跨度：句文本 + 在入参文本中的起止偏移
     */
    @Data
    @AllArgsConstructor
    public static class SentenceSpan {
        private String text;
        private int startOffset;
        private int endOffset;
    }

    /**
     * 定位参考文献区块起点（最后一次出现的标题行行首偏移）
     *
     * @return 区块起点偏移；-1 表示无参考文献区块
     */
    public static int indexOfReferencesBlock(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        Matcher matcher = REFERENCES_HEADING.matcher(text);
        int last = -1;
        while (matcher.find()) {
            last = matcher.start();
        }
        return last;
    }

    /**
     * 句级切分：按句末标点/换行切分，保留每句在原文中的偏移；
     * 丢弃去空白字符数 < {@link #MIN_SENTENCE_CHAR_COUNT} 的句。
     *
     * @param text 正文文本（调用方应已剔除参考文献区块）
     */
    public static List<SentenceSpan> split(String text) {
        List<SentenceSpan> spans = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return spans;
        }
        Matcher matcher = SENTENCE_DELIMITER.matcher(text);
        int start = 0;
        while (matcher.find()) {
            addIfValid(spans, text, start, matcher.start());
            start = matcher.end();
        }
        // 最后一个分隔符之后的尾句
        addIfValid(spans, text, start, text.length());
        return spans;
    }

    /**
     * 是否引用句：句尾带 [n] / [n,m] / [n-m] 样式引注
     */
    public static boolean endsWithCitation(String sentence) {
        if (sentence == null) {
            return false;
        }
        return TAIL_CITATION.matcher(sentence.trim()).find();
    }

    /**
     * 去空白字符数（中英文字符均按 1 计），作为句子字数权重
     */
    public static int charCount(String sentence) {
        if (sentence == null) {
            return 0;
        }
        return sentence.replaceAll("\\s", "").length();
    }

    private static void addIfValid(List<SentenceSpan> spans, String text, int start, int end) {
        if (end <= start) {
            return;
        }
        String sentence = text.substring(start, end);
        if (charCount(sentence) < MIN_SENTENCE_CHAR_COUNT) {
            return;
        }
        spans.add(new SentenceSpan(sentence, start, end));
    }
}
