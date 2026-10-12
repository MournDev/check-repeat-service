package com.abin.checkrepeatservice.report.service;

import com.abin.checkrepeatservice.report.dto.ReportVerifyVO;

/**
 * 报告验真服务（论文全生命周期 P5）
 */
public interface ReportVerifyService {

    /**
     * 按报告编号验真：重算内容哈希与库存哈希比对
     *
     * @param reportNo 报告编号（如 REPORT1759xxxxxxx）
     * @return 验真结果（编号不存在/历史报告无验真码/篡改 均返回 valid=false 并给出说明）
     */
    ReportVerifyVO verify(String reportNo);
}
