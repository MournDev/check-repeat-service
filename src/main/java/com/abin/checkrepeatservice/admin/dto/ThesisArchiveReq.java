package com.abin.checkrepeatservice.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 论文归档请求（P4）
 */
@Data
public class ThesisArchiveReq {

    /**
     * 归档备注（可选）
     */
    @Size(max = 500, message = "归档备注不能超过500字符")
    private String remark;
}
