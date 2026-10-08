package com.abin.checkrepeatservice.common.mapper;

import com.abin.checkrepeatservice.pojo.entity.TeacherSubject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 教师研究方向 Mapper 接口
 */
@Mapper
public interface TeacherSubjectMapper extends BaseMapper<TeacherSubject> {

    /**
     * 清空某教师的全部研究方向（物理删除）。
     *
     * 教师保存设置时是"整套覆盖"语义：先 deleteByUserId、再批量插新的。
     * 之所以坚持物理删：uk_teacher_subject(user_id, subject_id) 不含 is_deleted，
     * 软删后重选同一个学科会撞唯一键；而 @TableLogic 会把 delete(id) 偷偷改成 UPDATE，
     * 所以这里显式写 @Delete。
     */
    @Delete("DELETE FROM teacher_subject WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 删除某学科下的全部教师关联（管理端删除学科前的一次性清理/引用检查用）
     */
    @Delete("DELETE FROM teacher_subject WHERE subject_id = #{subjectId}")
    int deleteBySubjectId(@Param("subjectId") Long subjectId);

}
