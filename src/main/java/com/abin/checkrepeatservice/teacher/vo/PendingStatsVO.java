package com.abin.checkrepeatservice.teacher.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 待审核统计信息VO
 */
@Data
public class PendingStatsVO {
    
    /**
     * 待审核总数
     */
    private Integer totalPending;
    
    /**
     * 紧急待审数（包含超时和即将超时）
     */
    private Integer urgentPending;
    
    /**
     * 平均等待时间（天）
     */
    private BigDecimal avgWaitingTime;
    
    /**
     * 今日已审核数
     */
    private Integer todayReviewed;
    
    /**
     * 已超时数量
     */
    private Integer overdueCount;
    
    /**
     * 即将超时数量（24小时内到期）
     */
    private Integer urgentCount;
}