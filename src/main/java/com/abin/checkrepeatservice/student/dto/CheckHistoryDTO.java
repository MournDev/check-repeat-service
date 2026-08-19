package com.abin.checkrepeatservice.student.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 查重历史记录DTO
 */
@Data
@ApiModel(description = "查重历史记录DTO")
public class CheckHistoryDTO {
    
    @ApiModelProperty(value = "版本号")
    private Integer version;
    
    @ApiModelProperty(value = "报告ID")
    private String reportId;
    
    @ApiModelProperty(value = "查重时间")
    private LocalDateTime checkTime;
    
    @ApiModelProperty(value = "相似度")
    private BigDecimal similarity;
    
    @ApiModelProperty(value = "评级")
    private String rating;
    
    @ApiModelProperty(value = "是否为当前版本")
    private Boolean isCurrent;
    
    @ApiModelProperty(value = "修改说明")
    private String changes;
    
    @ApiModelProperty(value = "相比上一版本的改进")
    private BigDecimal improvementFromPrevious;
    
    @ApiModelProperty(value = "各章节变化情况")
    private Map<String, SectionChangeDTO> sectionChanges;
    
    /**
     * 章节变化DTO
     */
    @Data
    public static class SectionChangeDTO {
        @ApiModelProperty(value = "前一个版本的相似度")
        private BigDecimal from;
        
        @ApiModelProperty(value = "当前版本的相似度")
        private BigDecimal to;
        
        @ApiModelProperty(value = "变化值")
        private BigDecimal change;
    }
}