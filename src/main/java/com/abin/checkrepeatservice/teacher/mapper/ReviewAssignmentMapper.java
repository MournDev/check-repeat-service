package com.abin.checkrepeatservice.teacher.mapper;

import com.abin.checkrepeatservice.pojo.entity.ReviewAssignment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评阅任务 Mapper（论文全生命周期 P2）。
 * 归属 teacher 模块：教师评阅工作台为主使用方，管理端分配跨模块引用。
 */
@Mapper
public interface ReviewAssignmentMapper extends BaseMapper<ReviewAssignment> {
}
