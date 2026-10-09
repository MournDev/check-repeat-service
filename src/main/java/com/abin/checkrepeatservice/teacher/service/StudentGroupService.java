package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.StudentGroupReq;
import com.abin.checkrepeatservice.teacher.vo.GroupStudentVO;
import com.abin.checkrepeatservice.teacher.vo.StudentGroupVO;

import java.util.List;

/**
 * 学生分组服务（教师端「学生分组管理」页面）
 */
public interface StudentGroupService {

    /**
     * 查询当前教师的全部分组（含组内学生）
     */
    Result<List<StudentGroupVO>> listGroups();

    /**
     * 创建分组
     */
    Result<StudentGroupVO> createGroup(StudentGroupReq req);

    /**
     * 更新分组（名称/描述）
     */
    Result<StudentGroupVO> updateGroup(Long groupId, StudentGroupReq req);

    /**
     * 删除分组（连同成员关系一并清除）
     */
    Result<String> deleteGroup(Long groupId);

    /**
     * 批量添加学生到分组（已在组内的自动跳过）
     */
    Result<String> addStudents(Long groupId, List<Long> studentIds);

    /**
     * 从分组移除单个学生
     */
    Result<String> removeStudent(Long groupId, Long studentId);

    /**
     * 查询当前教师名下尚未加入该分组的学生
     */
    Result<List<GroupStudentVO>> listStudentsNotInGroup(Long groupId);
}
