package com.abin.checkrepeatservice.common.utils;

import java.util.Map;

/**
 * 消息模板变量渲染工具
 *
 * 占位符统一用 {userName} 这种写法——这是后端既有约定，MessageServiceImpl 一直在用，
 * 模板表里的历史模板也都按这个写。前端「消息模板管理」页的提示语与之一致。
 *
 * 注意：不再兼容 ${userName} 写法——以前端迁就后端处理，前端模板管理页的输入提示语
 * 已由 ${userName} 改为 {userName}。模板里写成 ${xxx} 会原样输出，不会被替换。
 *
 * 共同实现放在这里，是为了避免「渲染逻辑散在多处、其中一处只认一种写法」这种漂移。
 */
public final class TemplateRenderUtils {

    private TemplateRenderUtils() {
    }

    /**
     * 把模板里的占位符替换为参数值
     *
     * @param template 模板文本（为 null 时原样返回 null）
     * @param params   变量表；值为 null 时替换成空串
     * @return 渲染结果；params 为空时原样返回模板
     */
    public static String render(String template, Map<String, Object> params) {
        if (template == null || params == null || params.isEmpty()) {
            return template;
        }

        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace("{" + key + "}", value);
        }
        return result;
    }
}
