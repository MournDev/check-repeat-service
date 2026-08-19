package com.abin.checkrepeatservice.student.mapper;

import com.abin.checkrepeatservice.pojo.entity.CheckRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 查重规则Mapper接口
 */
@Mapper
public interface CheckRuleMapper extends BaseMapper<CheckRule> {
}