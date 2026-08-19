package com.abin.checkrepeatservice.pojo.base;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;


/**
 * 基础文件参数（业务无关，所有文件上传通用）
 * 包含：文件流、MD5、文件名等文件本身的核心信息
 */
@Data
@ApiModel("基础文件参数（通用）")
public class FileBaseParam {
    /**
     * 上传文件流（必传）
     */
    @NotNull(message = "上传文件不能为空")
    @ApiModelProperty(value = "上传文件流", required = true)
    private MultipartFile file;

    /**
     * 文件MD5值（32位小写，必传）
     * 作用：1. 校验文件完整性 2. 防止重复上传
     */
    @NotBlank(message = "文件MD5不能为空")
    @Size(min = 32, max = 32, message = "MD5值必须为32位")
    @ApiModelProperty(value = "文件MD5值（32位小写）", required = true, example = "e10adc3949ba59abbe56e057f20f883e")
    private String fileMd5;

    /**
     * 原始文件名（可选，不传则用file.getOriginalFilename()）
     * 作用：自定义文件名（如用户上传时重命名）
     */
    @ApiModelProperty(value = "自定义原始文件名（可选）", example = "论文初稿V2.pdf")
    private String originalFilename;

    /**
     * 允许的文件类型（可选，不传则用全局配置）
     * 作用：业务层自定义允许的类型（如“论文上传”仅允许PDF，“头像上传”仅允许JPG/PNG）
     */
    @ApiModelProperty(value = "允许的文件类型（可选，如application/pdf,image/jpeg）", example = "application/pdf,application/msword")
    private String allowedTypes;

    /**
     * 单个文件最大大小（单位：MB，可选，不传则用全局配置）
     * 作用：业务层自定义大小限制（如“头像”最大2MB，“论文”最大100MB）
     */
    @ApiModelProperty(value = "单个文件最大大小（单位：MB，可选）", example = "100")
    private Integer maxSizeMb;
}
