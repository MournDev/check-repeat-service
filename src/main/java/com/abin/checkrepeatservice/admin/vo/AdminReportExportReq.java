package com.abin.checkrepeatservice.admin.vo;

import lombok.Data;

import java.util.List;

/**
 * 管理员批量导出报告请求
 * 按 ids 个数分支：1 个返回单份 PDF，多个返回含多份 PDF 的 ZIP
 */
@Data
public class AdminReportExportReq {

    /**
     * 报告ID列表（关联check_report.id）
     */
    private List<Long> ids;
}
