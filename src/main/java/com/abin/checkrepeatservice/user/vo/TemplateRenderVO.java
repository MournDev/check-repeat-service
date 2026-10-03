package com.abin.checkrepeatservice.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板渲染预览结果
 *
 * 前端「消息模板管理」页的预览弹窗读的是 data.title / data.content。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateRenderVO {

    /**
     * 渲染后的标题
     */
    private String title;

    /**
     * 渲染后的内容
     */
    private String content;
}
