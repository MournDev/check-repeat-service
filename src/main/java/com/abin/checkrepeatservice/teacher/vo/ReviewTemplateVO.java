package com.abin.checkrepeatservice.teacher.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审核意见模板返回 VO。
 *
 * <p>字段是照 ReviewTemplates.vue 实际读的那几个逐个定的：
 * id / title / category / content / scenarios / isPublic / usageCount / lastUsed / updateTime。</p>
 */
@Data
public class ReviewTemplateVO {

    /** 模板ID（雪花ID，19 位，按字符串下发否则 JS 会丢末位） */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /** 模板标题 */
    private String title;

    /** 模板分类 */
    private String category;

    /** 模板内容 */
    private String content;

    /** 使用场景标签。库里是 JSON 文本，这里转成数组给前端的 el-checkbox-group */
    private List<String> scenarios;

    /** 是否公开 */
    private Boolean isPublic;

    /** 使用次数，库里为 NULL 时给 0 —— 前端会就地做 usageCount += 1，null 会算成 NaN */
    private Integer usageCount;

    /** 最后使用时间，从未使用过为 null */
    private LocalDateTime lastUsed;

    /** 最后更新时间，列表页展示用 */
    private LocalDateTime updateTime;

}
