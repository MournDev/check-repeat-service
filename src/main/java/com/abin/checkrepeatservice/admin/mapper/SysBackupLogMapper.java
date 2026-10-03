package com.abin.checkrepeatservice.admin.mapper;

import com.abin.checkrepeatservice.pojo.entity.SysBackupLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 备份日志Mapper接口
 *
 * <p>查询/分页/软删除全部走 MyBatis-Plus 内置方法，无需 XML。</p>
 */
@Mapper
public interface SysBackupLogMapper extends BaseMapper<SysBackupLog> {
}
