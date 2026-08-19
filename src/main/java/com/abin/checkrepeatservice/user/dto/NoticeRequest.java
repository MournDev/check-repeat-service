package com.abin.checkrepeatservice.user.dto;

import com.abin.checkrepeatservice.common.enums.NoticeType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NoticeRequest {
    private String toEmail;
    private NoticeType noticeType;
    private String customSubject; // 可选自定义主题
    private String customContent; // 可选自定义内容
    private Map<String, Object> templateParams; // 模板参数
}
