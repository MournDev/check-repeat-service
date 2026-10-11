package com.abin.checkrepeatservice.teacher.mapper;

import com.abin.checkrepeatservice.pojo.entity.ReviewWorkflowProgress;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 多级初审投票 Mapper（论文全生命周期 P2）。
 */
@Mapper
public interface ReviewWorkflowProgressMapper extends BaseMapper<ReviewWorkflowProgress> {
}
