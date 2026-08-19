package com.abin.checkrepeatservice.teacher.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 教师个人信息DTO
 */
@Data
@ApiModel(description = "教师个人信息")
public class TeacherProfileDTO {

    @ApiModelProperty(value = "真实姓名", example = "张教授")
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名长度不能超过50个字符")
    private String realName;

    @ApiModelProperty(value = "工号", example = "T001")
    @NotBlank(message = "工号不能为空")
    @Size(max = 20, message = "工号长度不能超过20个字符")
    private String username;

    @ApiModelProperty(value = "手机号", example = "13800138000")
    @Size(max = 11, message = "手机号格式不正确")
    private String phone;

    @ApiModelProperty(value = "邮箱", example = "zhang@university.edu.cn")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    @ApiModelProperty(value = "所属学院", example = "计算机学院")
    @Size(max = 100, message = "学院名称长度不能超过100个字符")
    private String college;

    @ApiModelProperty(value = "职称", example = "教授")
    @Size(max = 50, message = "职称长度不能超过50个字符")
    private String title;

    @ApiModelProperty(value = "研究方向", example = "人工智能、机器学习")
    @Size(max = 200, message = "研究方向长度不能超过200个字符")
    private String researchDirection;

    @ApiModelProperty(value = "个人简介")
    @Size(max = 1000, message = "个人简介长度不能超过1000个字符")
    private String introduction;

    @ApiModelProperty(value = "最大指导学生数", example = "10")
    private Integer maxStudents;

    @ApiModelProperty(value = "当前指导学生数", example = "8")
    private Integer currentStudents;
}