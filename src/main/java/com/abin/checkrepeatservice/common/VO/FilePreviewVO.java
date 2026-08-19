package com.abin.checkrepeatservice.common.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilePreviewVO {
    private byte[] content;
    private String contentType;
    private String fileName;
    private HttpHeaders headers;
}
