package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

import java.util.List;

/**
 * 版本对比请求DTO
 */
@Data
public class VersionCompareRequest {
    private Long paperId;
    private List<Long> versionIds; // 对比的版本ID列表
}