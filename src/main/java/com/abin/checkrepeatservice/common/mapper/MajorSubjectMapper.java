package com.abin.checkrepeatservice.common.mapper;

import com.abin.checkrepeatservice.pojo.entity.MajorSubject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 专业↔学科领域映射 Mapper 接口
 */
@Mapper
public interface MajorSubjectMapper extends BaseMapper<MajorSubject> {

    /**
     * 删除某专业的全部学科映射（物理删除，重建整套映射时先清后插）。
     *
     * 为什么不用 mapper.delete(wrapper)：实体上的 @TableLogic 会把 delete 改写成
     * UPDATE ... SET is_deleted = 1。而 uk_major_subject(major_id, subject_id)
     * 不含 is_deleted —— 软删后重新加回同一对，插入会撞唯一键，表现为"保存不了"。
     */
    @Delete("DELETE FROM major_subject WHERE major_id = #{majorId}")
    int deleteByMajorId(@Param("majorId") Long majorId);

    /**
     * 删除某学科下的全部专业映射（管理端删除学科前的一次性清理/引用检查用）
     */
    @Delete("DELETE FROM major_subject WHERE subject_id = #{subjectId}")
    int deleteBySubjectId(@Param("subjectId") Long subjectId);

}
