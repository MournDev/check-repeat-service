package com.abin.checkrepeatservice.common.mapper;

import com.abin.checkrepeatservice.pojo.entity.SimilarityThreshold;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全局相似度阈值 Mapper 接口
 *
 * 放在 common 而非 admin 包：SimilarityThresholdResolver 位于 common，
 * 由 student 层的查重判定直接调用，Mapper 跟着它走可避免 common 反向依赖 admin。
 */
@Mapper
public interface SimilarityThresholdMapper extends BaseMapper<SimilarityThreshold> {
}
