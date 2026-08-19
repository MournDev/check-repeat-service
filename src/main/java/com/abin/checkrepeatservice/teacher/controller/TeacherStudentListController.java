package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.teacher.dto.StudentListDTO;
import com.abin.checkrepeatservice.teacher.service.TeacherStudentListService;
import com.abin.checkrepeatservice.user.vo.PageResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 教师端学生列表控制器：仅教师角色可访问
 */
@RestController
@RequestMapping("/api/teacher/student-list")
@PreAuthorize("hasAuthority('TEACHER')")
public class TeacherStudentListController {

    @Autowired
    private TeacherStudentListService teacherStudentService;

    @GetMapping("/list")
    public Result<PageResultVO<StudentListDTO>> getStudentList(
            @RequestParam Long teacherId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer pageSize

    ) {
        PageResultVO<StudentListDTO> pageResult = teacherStudentService.getStudentsByTeacherId(
                teacherId, current, pageSize);
        return Result.success(pageResult);
    }
    @PostMapping("/delete")
    public Result<String> deleteStudent(@RequestParam Long studentId) {
        boolean result = teacherStudentService.deleteStudent(studentId);
        if (result) {
            return Result.success("删除成功");
        } else {
            return Result.error(ResultCode.SYSTEM_ERROR,"删除失败");
        }
    }

}
