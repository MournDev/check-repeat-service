package com.abin.checkrepeatservice.teacher.mapper;

import com.abin.checkrepeatservice.pojo.entity.GuidanceRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 指导记录 Mapper：指导记录由教师发起，归属 teacher 模块；
 * 学生端只读/确认，跨模块引用本 Mapper（与 ReviewRecordMapper 的归属先例一致）。
 */
@Mapper
public interface GuidanceRecordMapper extends BaseMapper<GuidanceRecord> {
}
