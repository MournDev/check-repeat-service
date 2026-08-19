package com.abin.checkrepeatservice.common.service;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

public interface FilePreviewService {

    /**
     * 直接文件预览
     */
    ResponseEntity<Resource> directPreview(Long fileId);

    /**
     * KKFileView代理预览（通过URL）
     */
    ResponseEntity<byte[]> onlinePreviewByUrl(String url);

    /**
     * 智能预览（自动选择预览方式）
     */
    ResponseEntity<?> smartPreview(Long fileId);

    /**
     * 智能预览报告
     */
    ResponseEntity<?> smartPreviewReport(String paperId);
}
