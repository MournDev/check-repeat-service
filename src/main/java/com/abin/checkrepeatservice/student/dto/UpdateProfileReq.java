package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateProfileReq {
    
    /**
     * 手机号码
     * 格式：11位数字，以1开头，第二位为3-9
     */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    
    /**
     * 邮箱地址
     */
    @Email(message = "邮箱格式不正确")
    private String email;
    
    /**
     * 头像URL
     */
    private String avatar;
    
    /**
     * 研究兴趣
     * 长度限制：最多200字符
     */
    @Pattern(regexp = "^.{0,200}$", message = "研究兴趣长度不能超过200字符")
    private String researchInterest;
    
    /**
     * 个人简介
     * 长度限制：最多500字符
     */
    @Pattern(regexp = "^.{0,500}$", message = "个人简介长度不能超过500字符")
    private String introduce;
    
    /**
     * 年级
     */
    private String grade;
    
    /**
     * 专业
     */
    private String major;
    
    /**
     * 班级
     */
    private String className;
}