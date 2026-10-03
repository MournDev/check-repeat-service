package com.abin.checkrepeatservice.admin.vo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 查重规则操作请求DTO（新增/编辑）
 */
@Data
public class CheckRuleOperateReq {
    /**
     * 规则ID（编辑时必传，新增时不传）
     */
    private Long ruleId; // 前端传参：ruleId=1546278765432123461（编辑时）

    /**
     * 规则名称（必传）
     */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 100, message = "规则名称长度不能超过100字符")
    private String ruleName; // 前端传参：ruleName=本科毕业论文规则

    /**
     * 规则编码（必传，唯一）
     */
    @NotBlank(message = "规则编码不能为空")
    @Size(max = 50, message = "规则编码长度不能超过50字符")
    private String ruleCode; // 前端传参：ruleCode=UNDERGRADUATE

    /**
     * 合格重复率阈值（0-100）
     *
     * @deprecated 前端已不再提交该字段（查重规则页的"通过阈值"表单项已移除），
     * 合格线请到「相似度阈值设置」页配置。这里**刻意去掉了 @NotNull / @DecimalMin / @DecimalMax**：
     * 字段缺失时不再拦截请求，否则 GlobalExceptionHandler 没有
     * MethodArgumentNotValidException 处理器，会把缺字段打成 HTTP 500 而不是 400。
     */
    @Deprecated
    private BigDecimal passThreshold; // 已废弃：仅兼容可能存在的旧调用方

    /**
     * 关联比对库ID列表（必传，至少1个）
     */
    @NotNull(message = "请选择关联的比对库")
    @Size(min = 1, message = "至少关联1个比对库")
    private List<Long> libIds; // 前端传参：libIds=1546278765432123471,1546278765432123472

    /**
     * 二次查重间隔（秒，必传，≥0）
     */
    @NotNull(message = "二次查重间隔不能为空")
    @DecimalMin(value = "0", message = "二次查重间隔不能小于0秒")
    private Integer checkInterval; // 前端传参：checkInterval=86400

    /**
     * 最大查重次数（必传，≥1）
     */
    @NotNull(message = "最大查重次数不能为空")
    @DecimalMin(value = "1", message = "最大查重次数不能小于1次")
    private Integer maxCheckCount; // 前端传参：maxCheckCount=5

    /**
     * 是否默认规则（0-否，1-是，必传）
     */
    @NotNull(message = "请指定是否为默认规则")
    private Integer isDefault; // 前端传参：isDefault=1

    /**
     * 规则描述（可选）
     */
    @Size(max = 500, message = "规则描述长度不能超过500字符")
    private String description; // 前端传参：description=本科毕业论文专用规则
}
