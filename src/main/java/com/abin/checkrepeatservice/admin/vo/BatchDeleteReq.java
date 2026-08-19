package com.abin.checkrepeatservice.admin.vo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/**
 * 批量删除请求VO
 */
@Data
public class BatchDeleteReq {
    @NotEmpty(message = "用户ID列表不能为空")
    private List<Long> userIds;
}