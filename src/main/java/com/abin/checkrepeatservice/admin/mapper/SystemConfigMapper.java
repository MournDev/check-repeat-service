package com.abin.checkrepeatservice.admin.mapper;

import com.abin.checkrepeatservice.pojo.entity.SystemConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统配置Mapper接口
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {
    
    /**
     * 根据配置键查找配置
     */
    @Select("SELECT * FROM system_config WHERE config_key = #{configKey} AND is_deleted = 0")
    SystemConfig selectByConfigKey(@Param("configKey") String configKey);
    
    /**
     * 根据配置键更新配置值
     */
    int updateByConfigKey(@Param("configKey") String configKey, 
                         @Param("configValue") String configValue);
}