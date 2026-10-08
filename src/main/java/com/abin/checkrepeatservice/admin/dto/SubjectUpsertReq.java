package com.abin.checkrepeatservice.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学科领域新增/修改请求。
 *
 * 新增与修改共用一个 DTO，但两者对 code / parentId 的态度相反：
 *   · 新增时 code 必填、parentId 决定层级；
 *   · 修改时这两个字段都**不接受改变**，服务层会与库里的值比对，不等就拒绝
 *     （而不是静默忽略——静默忽略会让管理员以为改成功了）。
 *
 * level 刻意不在这个 DTO 里：它由 parentId 推导（见 AdminSubjectServiceImpl），
 * 让前端传层级等于把「parentId 指门类、level 却写 2」这种自相矛盾的行放进库里。
 */
@Data
public class SubjectUpsertReq {

    /**
     * 学科编码（唯一，如 ENG、ENG-CS），创建后不可修改。
     *
     * 限定大写字母/数字/连字符：paper_info.subject_code 存的就是它，
     * 而 AcademicIntegrityServiceImpl 拿这一列去 contains 匹配资源分类，
     * 容忍小写会引入「ENG-CS 与 eng-cs 是两个学科」这类只在匹配时才暴露的坑。
     */
    @NotBlank(message = "学科编码不能为空")
    @Size(max = 50, message = "学科编码长度不能超过50")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "学科编码只能包含大写字母、数字和连字符（如 ENG-CS）")
    private String code;

    /**
     * 学科名称（如 工学、人工智能）。可改——它只是显示名，
     * paper_info.subject_code 存的是 code，改名不影响任何既有引用。
     */
    @NotBlank(message = "学科名称不能为空")
    @Size(max = 128, message = "学科名称长度不能超过128")
    private String name;

    /**
     * 上级门类ID。null / 不传 = 创建一级门类；有值则创建该门类下的二级学科。
     * 只接受一级门类作为上级（不支持三级），修改时不允许变更。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long parentId;

    /**
     * 同层排序，不传则排到同级末尾（服务层取同级 max+1）。
     */
    private Integer sortNo;

    /**
     * 是否启用：0-停用，1-启用。不传按启用处理。
     */
    private Integer isEnabled;

}
