package com.abin.checkrepeatservice.admin.mapper;

import com.abin.checkrepeatservice.pojo.entity.DefenseBatch;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 答辩批次 Mapper（论文全生命周期 P3）。
 * 归属 admin 模块：批次管理主使用方；教师端工作台与学生端我的答辩跨模块引用。
 */
@Mapper
public interface DefenseBatchMapper extends BaseMapper<DefenseBatch> {
}
