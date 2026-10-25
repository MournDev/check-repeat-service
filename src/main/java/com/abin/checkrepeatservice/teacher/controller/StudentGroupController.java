package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.teacher.dto.StudentGroupReq;
import com.abin.checkrepeatservice.teacher.service.StudentGroupService;
import com.abin.checkrepeatservice.teacher.vo.GroupStudentVO;
import com.abin.checkrepeatservice.teacher.vo.StudentGroupVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生分组管理（教师端，仅负责请求接收与响应）
 */
@Slf4j
@RestController
@RequestMapping("/api/teacher/student-groups")
@Tag(name = "学生分组管理", description = "教师端学生分组管理")
public class StudentGroupController {

    @Resource
    private StudentGroupService studentGroupService;

    /**
     * 查询当前教师的全部分组（含组内学生）
     */
    @GetMapping
    @Operation(summary = "获取分组列表", description = "查询当前教师的全部分组（含组内学生）")
    public Result<List<StudentGroupVO>> listGroups() {
        return studentGroupService.listGroups();
    }

    /**
     * 创建分组
     */
    @PostMapping
    @Operation(summary = "创建分组")
    @OperationLog(type = "student_group_create", description = "创建学生分组")
    public Result<StudentGroupVO> createGroup(
            @Parameter(description = "分组创建请求体", required = true)
            @Valid @RequestBody StudentGroupReq req) {
        return studentGroupService.createGroup(req);
    }

    /**
     * 更新分组（名称/描述）
     */
    @PutMapping("/{groupId}")
    @Operation(summary = "更新分组", description = "更新分组名称与描述")
    @OperationLog(type = "student_group_update", description = "更新学生分组")
    public Result<StudentGroupVO> updateGroup(
            @Parameter(description = "分组ID", required = true)
            @PathVariable Long groupId,
            @Parameter(description = "分组更新请求体", required = true)
            @Valid @RequestBody StudentGroupReq req) {
        return studentGroupService.updateGroup(groupId, req);
    }

    /**
     * 删除分组
     */
    @DeleteMapping("/{groupId}")
    @Operation(summary = "删除分组")
    @OperationLog(type = "student_group_delete", description = "删除学生分组")
    public Result<String> deleteGroup(
            @Parameter(description = "分组ID", required = true)
            @PathVariable Long groupId) {
        return studentGroupService.deleteGroup(groupId);
    }

    /**
     * 批量添加学生到分组
     */
    @PostMapping("/{groupId}/students")
    @Operation(summary = "批量添加学生到分组")
    @OperationLog(type = "student_group_add_students", description = "添加学生到分组")
    public Result<String> addStudents(
            @Parameter(description = "分组ID", required = true)
            @PathVariable Long groupId,
            @Parameter(description = "学生ID列表", required = true)
            @RequestBody List<Long> studentIds) {
        return studentGroupService.addStudents(groupId, studentIds);
    }

    /**
     * 从分组移除单个学生
     */
    @DeleteMapping("/{groupId}/students/{studentId}")
    @Operation(summary = "从分组移除学生")
    @OperationLog(type = "student_group_remove_student", description = "从分组移除学生")
    public Result<String> removeStudent(
            @Parameter(description = "分组ID", required = true)
            @PathVariable Long groupId,
            @Parameter(description = "学生ID", required = true)
            @PathVariable Long studentId) {
        return studentGroupService.removeStudent(groupId, studentId);
    }

    /**
     * 查询当前教师名下尚未加入该分组的学生
     */
    @GetMapping("/{groupId}/students/not-in-group")
    @Operation(summary = "获取未加入分组的学生", description = "查询当前教师名下尚未加入该分组的学生")
    public Result<List<GroupStudentVO>> listStudentsNotInGroup(
            @Parameter(description = "分组ID", required = true)
            @PathVariable Long groupId) {
        return studentGroupService.listStudentsNotInGroup(groupId);
    }
}
