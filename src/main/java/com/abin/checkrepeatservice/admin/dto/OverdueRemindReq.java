package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 超期论文一键催办请求（Wave 3）。
 */
@Data
public class OverdueRemindReq {

    /** 论文ID列表（一次最多 100 篇） */
    private List<Long> paperIds;

    /** 催办渠道：IN_APP（默认）/ EMAIL / BOTH */
    private String channel;

    /** 自定义催办文案（≤200 字），为空使用默认模板 */
    private String message;
}
