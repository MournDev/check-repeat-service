package com.abin.checkrepeatservice.admin.mapper;

import com.abin.checkrepeatservice.pojo.entity.AutoAssignmentHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自动分配历史记录Mapper接口
 */
@Mapper
public interface AutoAssignmentHistoryMapper extends BaseMapper<AutoAssignmentHistory> {
}