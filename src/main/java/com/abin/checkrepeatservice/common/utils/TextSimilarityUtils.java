package com.abin.checkrepeatservice.common.utils;


import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.CosineSimilarity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * 文本相似度计算工具类：SimHash+余弦相似度组合算法
 */
@Component
@Slf4j
public class TextSimilarityUtils {

    // SimHash哈希位数（从配置文件获取）
    @Value("${check.simhash.hash-bits}")
    private int hashBits;

    // 海明距离阈值（从配置文件获取）
    @Getter
    @Value("${check.simhash.hamming-threshold}")
    private int hammingThreshold;

    // 余弦相似度计算器
    private final CosineSimilarity cosineSimilarity = new CosineSimilarity();

    /**
     * 1. 计算文本的SimHash值（用于快速去重）
     * @param text 待计算文本（需先分词）
     * @return SimHash值（BigInteger）
     */
    public BigInteger calculateSimHash(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "计算SimHash的文本不能为空");
        }

        // 初始化SimHash向量（hashBits位，默认0）
        int[] simHashVector = new int[hashBits];
        // 按空格分割分词结果（IK分词后已用空格拼接）
        String[] words = text.split(" ");

        for (String word : words) {
            // 计算每个词的MD5哈希值（128位）
            BigInteger wordHash = getMd5Hash(word);
            // 构建词的哈希向量（128位→hashBits位，高位截断）
            for (int i = 0; i < hashBits; i++) {
                // 取第i位（从高位到低位）
                BigInteger bitMask = BigInteger.ONE.shiftLeft(hashBits - 1 - i);
                if (wordHash.and(bitMask).compareTo(BigInteger.ZERO) != 0) {
                    simHashVector[i] += 1; // 该位为1，向量+1
                } else {
                    simHashVector[i] -= 1; // 该位为0，向量-1
                }
            }
        }

        // 生成最终SimHash值（向量>0取1，≤0取0）
        BigInteger simHash = BigInteger.ZERO;
        for (int i = 0; i < hashBits; i++) {
            if (simHashVector[i] > 0) {
                simHash = simHash.or(BigInteger.ONE.shiftLeft(hashBits - 1 - i));
            }
        }
        return simHash;
    }

    /**
     * 2. 计算两个SimHash的海明距离（判断是否相似）
     * @param simHash1 文本1的SimHash
     * @param simHash2 文本2的SimHash
     * @return 海明距离（值越小越相似）
     */
    public int calculateHammingDistance(BigInteger simHash1, BigInteger simHash2) {
        if (simHash1 == null || simHash2 == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "SimHash值不能为空");
        }
        // 异或运算（相同位0，不同位1），统计1的个数即海明距离
        BigInteger xor = simHash1.xor(simHash2);
        return xor.bitCount();
    }

    /**
     * 3. 计算两个文本的余弦相似度（精细比对，0→完全不相似，1→完全相同）
     * @param text1 文本1（已分词，空格分隔）
     * @param text2 文本2（已分词，空格分隔）
     * @return 余弦相似度（保留4位小数）
     */
    public double calculateCosineSimilarity(String text1, String text2) {
        if ((text1 == null || text1.trim().isEmpty()) || (text2 == null || text2.trim().isEmpty())) {
            return 0.0;
        }

        // 构建词频Map（统计两个文本的所有词）
        Map<CharSequence, Integer> text1Freq = new HashMap<>();
        Map<CharSequence, Integer> text2Freq = new HashMap<>();
        Map<CharSequence, Boolean> allWords = new HashMap<>();

        // 统计文本1词频
        for (String word : text1.split(" ")) {
            text1Freq.put(word, text1Freq.getOrDefault(word, 0) + 1);
            allWords.put(word, true);
        }
        // 统计文本2词频
        for (String word : text2.split(" ")) {
            text2Freq.put(word, text2Freq.getOrDefault(word, 0) + 1);
            allWords.put(word, true);
        }

        // 计算余弦相似度（Apache Commons Text工具类）
        double similarity = cosineSimilarity.cosineSimilarity(text1Freq, text2Freq);
        // 保留4位小数，避免精度问题
        return Math.round(similarity * 10000) / 10000.0;
    }

    /**
     * 4. 综合判断文本相似度（SimHash快速筛选+余弦相似度精细计算）
     * @param text1 原始文本（论文内容）
     * @param text2 比对文本（库中文本）
     * @return 综合相似度（百分比，保留2位小数）
     */
    public double calculateComprehensiveSimilarity(String text1, String text2) {
        // 步骤1：分词预处理
        String segmentedText1 = IKAnalyzerUtils.segmentToString(text1);
        String segmentedText2 = IKAnalyzerUtils.segmentToString(text2);

        // 步骤2：SimHash快速筛选（海明距离>阈值，直接返回低相似度）
        BigInteger simHash1 = calculateSimHash(segmentedText1);
        BigInteger simHash2 = calculateSimHash(segmentedText2);
        int hammingDistance = calculateHammingDistance(simHash1, simHash2);
        if (hammingDistance > hammingThreshold) {
            return 0.0; // 海明距离过大，判定为不相似
        }

        // 步骤3：余弦相似度精细计算（返回百分比）
        double cosineSimilarity = calculateCosineSimilarity(segmentedText1, segmentedText2);
        return Math.round(cosineSimilarity * 10000) / 100.0; // 转换为百分比（如15.32%）
    }

    // ------------------------------ 私有辅助方法 ------------------------------
    /**
     * 计算字符串的MD5哈希值（128位）
     */
    private BigInteger getMd5Hash(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] mdBytes = md.digest(str.getBytes(StandardCharsets.UTF_8));
            return new BigInteger(1, mdBytes); // 转换为正整数
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5算法初始化失败：", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文本哈希计算异常");
        }
    }

}