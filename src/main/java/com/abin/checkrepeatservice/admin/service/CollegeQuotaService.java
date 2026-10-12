package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.CheckQuotaSaveReq;
import com.abin.checkrepeatservice.admin.vo.CheckCollegeQuotaVO;
import com.abin.checkrepeatservice.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 学院月度查重额度服务（论文全生命周期 P5）。
 *
 * 月度隔离说明：额度按 (college_id, quota_month) 一行一条记录（唯一键），
 * quota_month 格式 yyyy-MM，跨月自然产生新记录，因此无需任何定时清零任务。
 */
public interface CollegeQuotaService {

    /**
     * 校验并扣减学院当月查重额度（在每个查重任务创建入口、任务真正落库前调用）。
     *
     * 规则：
     *  - collegeId 为 null（论文未归属学院）：不限额，直接放行；
     *  - 当月 (college_id, 当前月) 无额度记录：不限额，直接放行；
     *  - 有记录且 used_quota >= total_quota：抛 BusinessException(BUSINESS_QUOTA_EXCEEDED)；
     *  - 否则原子扣减 used_quota + 1。
     */
    void checkAndConsume(Long collegeId);

    /**
     * 管理端额度列表（quotaMonth 可选；pageSize=-1 时不分页返回全量），补学院名称
     */
    Result<Page<CheckCollegeQuotaVO>> listQuotas(String quotaMonth, Integer currentPage, Integer pageSize);

    /**
     * 管理端保存额度：按 (college_id, quota_month) upsert（存在改 total，不存在插入 used=0）
     */
    Result<Void> saveQuota(CheckQuotaSaveReq req);
}
