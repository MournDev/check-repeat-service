package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.teacher.dto.StudentListDTO;
import com.abin.checkrepeatservice.user.vo.PageResultVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 教师管理学生列表接口
 */
public interface TeacherStudentListService extends IService<SysUser> {
    /**
     * 获取教师下所有学生列表
     * @param teacherId 教师ID
     * @param current 当前页码
     * @param pageSize 每页数量
     * @return 学生列表
     */
    PageResultVO<StudentListDTO> getStudentsByTeacherId(Long teacherId, Integer current, Integer pageSize);

    /**
     * 删除学生
     * @param studentId 学生ID
     * @return 删除结果
     */
    boolean deleteStudent(Long studentId);
}
