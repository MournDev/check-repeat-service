package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.teacher.dto.StudentListDTO;
import com.abin.checkrepeatservice.teacher.service.TeacherStudentListService;
import com.abin.checkrepeatservice.user.vo.PageResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 教师端学生列表控制器：仅教师角色可访问
 */
@RestController
@RequestMapping("/api/teacher/student-list")
@PreAuthorize("hasAuthority('TEACHER')")
@Tag(name = "教师学生列表", description = "教师端指导学生列表查询与删除")
public class TeacherStudentListController {

    @Autowired
    private TeacherStudentListService teacherStudentService;

    @GetMapping("/list")
    @Operation(summary = "获取指导学生列表", description = "分页查询指定教师指导的学生")
    public Result<PageResultVO<StudentListDTO>> getStudentList(
            @Parameter(description = "教师ID", required = true)
            @RequestParam Long teacherId,
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer pageSize

    ) {
        PageResultVO<StudentListDTO> pageResult = teacherStudentService.getStudentsByTeacherId(
                teacherId, current, pageSize);
        return Result.success(pageResult);
    }
    @PostMapping("/delete")
    @Operation(summary = "删除学生")
    public Result<String> deleteStudent(
            @Parameter(description = "学生ID", required = true)
            @RequestParam Long studentId) {
        boolean result = teacherStudentService.deleteStudent(studentId);
        if (result) {
            return Result.success("删除成功");
        } else {
            return Result.error(ResultCode.SYSTEM_ERROR,"删除失败");
        }
    }

}
