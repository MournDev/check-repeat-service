package com.abin.checkrepeatservice.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 比对库操作请求DTO（新增/编辑）
 */
@Data
public class CompareLibOperateReq {
    /**
     * 比对库ID（编辑时必传，新增时不传）
     */
    private Long libId; // 前端传参：libId=1546278765432123471（编辑时）

    /**
     * 比对库名称（必传）
     */
    @NotBlank(message = "比对库名称不能为空")
    @Size(max = 100, message = "比对库名称长度不能超过100字符")
    private String libName; // 前端传参：libName=校内本科论文库

    /**
     * 比对库编码（必传，唯一）
     */
    @NotBlank(message = "比对库编码不能为空")
    @Size(max = 50, message = "比对库编码长度不能超过50字符")
    private String libCode; // 前端传参：libCode=CAMPUS_UNDERGRADUATE

    /**
     * 比对库类型（必传：LOCAL-本地库，REMOTE-远程库）
     */
    @NotBlank(message = "比对库类型不能为空")
    private String libType; // 前端传参：libType=LOCAL

    /**
     * 库地址（本地路径或远程URL，必传）
     */
    @NotBlank(message = "库地址不能为空")
    @Size(max = 500, message = "库地址长度不能超过500字符")
    private String libUrl; // 前端传参：libUrl=/data/compare-lib/campus-undergraduate/

    /**
     * 是否启用（0-禁用，1-启用，必传）
     */
    @NotNull(message = "请指定是否启用比对库")
    private Integer isEnabled; // 前端传参：isEnabled=1

    /**
     * 比对库描述（可选）
     */
    @Size(max = 500, message = "比对库描述长度不能超过500字符")
    private String description; // 前端传参：description=校内存储的本科毕业论文库
}
