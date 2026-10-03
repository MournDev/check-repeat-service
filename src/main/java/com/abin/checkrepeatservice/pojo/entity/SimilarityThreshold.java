package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 全局相似度阈值设置实体类
 * 对应数据库表：similarity_threshold
 *
 * 本表是相似度阈值的【唯一真值源】。判定逻辑见
 * common/service/SimilarityThresholdResolver。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("similarity_threshold")
public class SimilarityThreshold extends BaseEntity {

    /**
     * 全局相似度阈值（百分比整数，0-100）。
     * 未配置到学院/专业的论文按此值判定合格。
     */
    @TableField("global_threshold")
    private Integer globalThreshold;

}
