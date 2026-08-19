package com.abin.checkrepeatservice.admin.mapper;

import com.abin.checkrepeatservice.pojo.entity.RuleLibRelation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 规则库关系Mapper接口
 */
@Mapper
public interface RuleLibRelationMapper extends BaseMapper<RuleLibRelation> {

    /**
     * 根据规则ID查询关联的对比库
     */
    List<RuleLibRelation> selectByRuleId(@Param("ruleId") Long ruleId);

    /**
     * 根据对比库ID查询关联的规则
     */
    List<RuleLibRelation> selectByLibId(@Param("libId") Long libId);

    /**
     * 批量插入规则库关系
     */
    int insertBatch(@Param("relations") List<RuleLibRelation> relations);

    /**
     * 批量插入规则库关系（兼容旧方法名）
     */
    default int batchInsert(@Param("list") List<RuleLibRelation> relationList) {
        return insertBatch(relationList);
    }
}