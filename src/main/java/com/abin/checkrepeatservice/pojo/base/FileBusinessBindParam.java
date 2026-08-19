package com.abin.checkrepeatservice.pojo.base;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 业务绑定参数（通用文件上传专用）
 * 作用：将上传的通用文件与具体业务场景关联，实现“文件服务”与“业务模块”解耦
 */
@Data
@ApiModel(value = "FileBusinessBindParam", description = "业务绑定参数（关联文件与具体业务）")
public class FileBusinessBindParam {

    /**
     * 业务类型（必传，枚举化取值）
     * 示例：
     * - PAPER_ATTACH：论文附件
     * - USER_AVATAR：用户头像
     * - REVIEW_ATTACH：审核附件
     * - CHECK_REPORT：查重报告
     */
    @ApiModelProperty(value = "业务类型（枚举值：PAPER_ATTACH/USER_AVATAR等）",
            required = true, example = "PAPER_ATTACH")
    private String businessType;

    /**
     * 业务ID（必传，关联具体业务数据的主键）
     * 示例：
     * - 业务类型=PAPER_ATTACH时，业务ID=论文ID（paper_submit.id）
     * - 业务类型=USER_AVATAR时，业务ID=用户ID（sys_user.id）
     */
    @ApiModelProperty(value = "业务ID（关联具体业务数据的主键）",
            required = true, example = "123456")
    private Long businessId;

    /**
     * 业务扩展信息（可选，JSON格式字符串）
     * 作用：存储业务特有信息，避免通用文件表冗余字段
     * 示例：
     * - 论文附件：{"attachmentUse":"论文初稿","paperType":"GRADUATION"}
     * - 审核附件：{"reviewType":"修改意见","reviewId":"789"}
     */
    @ApiModelProperty(value = "业务扩展信息（JSON格式字符串，可选）",
            example = "{\"attachmentUse\":\"论文初稿\"}")
    private String businessExtJson;
}
