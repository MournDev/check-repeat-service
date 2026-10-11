package com.abin.checkrepeatservice.admin.mapper;

import com.abin.checkrepeatservice.pojo.entity.DefenseRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 答辩记录 Mapper（论文全生命周期 P3）。
 * 归属 admin 模块：学生入批与名单导出主使用方；教师端评分录入、学生端我的答辩跨模块引用。
 */
@Mapper
public interface DefenseRecordMapper extends BaseMapper<DefenseRecord> {
}
