package com.abin.checkrepeatservice.teacher.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 教师视角下的学生联系信息。
 *
 * <p>只放聊天页「查看学生信息」真正会用到的三个字段（ChatCenter.vue 读的是
 * {@code data.name / data.email / data.phone}）。不要图省事直接返回 SysUser ——
 * 那会把 password、roleId 之类的东西一起送出去。</p>
 */
@Data
@ApiModel("学生联系信息")
public class StudentInfoVO {

    @ApiModelProperty("姓名")
    private String name;

    @ApiModelProperty("邮箱")
    private String email;

    @ApiModelProperty("电话")
    private String phone;

}
