package com.abin.checkrepeatservice.teacher.vo;

import lombok.Data;
import java.util.List;

/**
 * 今日审核统计VO
 */
@Data
public class TodayReviewedVO {
    
    /**
     * 今日审核数量
     */
    private Integer count;
    
    /**
     * 详细信息列表
     */
    private List<HourlyCountVO> details;
    
    /**
     * 小时统计VO
     */
    @Data
    public static class HourlyCountVO {
        /**
         * 小时
         */
        private Integer hour;
        
        /**
         * 数量
         */
        private Integer count;
    }
}