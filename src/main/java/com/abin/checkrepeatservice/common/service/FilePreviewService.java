package com.abin.checkrepeatservice.common.service;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.util.Map;

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
     * 取文件预览地址（登录态入口）。
     * 返回 {mode, url}：direct = 带签名令牌的文件流地址；kkfileview = kkFileView 预览页地址。
     * 前端应把 url 交给 iframe 直接加载，不要拉成 blob。
     */
    Map<String, String> getPreviewInfo(Long fileId);

    /**
     * 智能预览报告
     */
    ResponseEntity<?> smartPreviewReport(String paperId);

    /**
     * 按报告ID流式返回报告文件（仅 kkFileView 服务端回调使用）
     */
    ResponseEntity<Resource> serveReport(Long reportId, String fileName);
}
