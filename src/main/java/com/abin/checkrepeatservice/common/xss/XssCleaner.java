package com.abin.checkrepeatservice.common.xss;

import jakarta.annotation.Resource;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * XSS 输入清洗器（安全修复 S9）——全局过滤器与（未来）Service 层共用的单一决策点。
 *
 * <p>口径与项目既有 Service 层手动清洗保持一致（Jsoup）：</p>
 * <ul>
 *   <li><b>纯文本字段（默认）</b>：{@link Safelist#none()}，剥离所有标签并对
 *       {@code < > &} 等做 HTML 实体转义。指导记录、评语、意见等 8+ 处存量
 *       字段已是该口径，前端按「已清洗文本」渲染；</li>
 *   <li><b>富文本字段</b>：relaxed 白名单 + 少量补充标签，仅保留安全排版标签，
 *       与 {@code ReviewAttachUtils.cleanReviewOpinion} 同配置，避免同一字段
 *       在不同入口被洗成两种结果；</li>
 *   <li><b>凭据/令牌字段</b>：原样返回。HTML 转义会改变密码/令牌真实字节，
 *       属于过滤器最常见的误伤事故；这些字段也不存在被渲染为 HTML 的场景。</li>
 * </ul>
 *
 * <p>注意：这是「输入侧」纵深防御，不替代前端输出编码。存量数据未做回溯清洗，
 * Vue 文本插值默认转义仍是第一道防线；本过滤器保证此后新写入内容不带可执行脚本。</p>
 */
@Component
public class XssCleaner {

    /**
     * 富文本白名单：与 ReviewAttachUtils.cleanReviewOpinion 保持同款，
     * static 保证全局只有一份 Safelist（Jsoup Safelist 非线程安全文档未承诺，
     * 这里构建后不再修改，只做只读 clean）。
     */
    private static final Safelist RICH_SAFELIST = Safelist.relaxed()
            .addTags("p", "br", "strong", "em", "u", "span", "ul", "li", "ol")
            .addAttributes("span", "style")
            .addProtocols("a", "href", "http", "https");

    @Resource
    private XssProperties properties;

    /**
     * 按字段名选择策略清洗单个字符串值。
     *
     * @param value     原始值（null 原样返回）
     * @param fieldName 所属字段名（JSON 键名 / 表单参数名），null 时按纯文本处理
     * @return 清洗后的值
     */
    public String clean(String value, String fieldName) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String name = fieldName == null ? "" : fieldName;

        // 1. 凭据/令牌：绝不动
        if (matches(name, properties.getSkipFieldNames())) {
            return value;
        }
        // 2. 富文本：保留安全标签
        if (matches(name, properties.getRichTextFieldNames())) {
            return Jsoup.clean(value, RICH_SAFELIST);
        }
        // 3. 默认：纯文本，剥离所有标签 + 实体转义
        return Jsoup.clean(value, Safelist.none());
    }

    private boolean matches(String fieldName, java.util.List<String> configuredNames) {
        if (configuredNames == null || configuredNames.isEmpty()) {
            return false;
        }
        for (String configured : configuredNames) {
            if (configured.equalsIgnoreCase(fieldName)) {
                return true;
            }
        }
        return false;
    }
}
