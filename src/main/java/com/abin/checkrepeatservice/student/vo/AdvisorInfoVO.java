package com.abin.checkrepeatservice.student.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 *师信息VO
 */
@Data
@ApiModel("导师信息")
public class AdvisorInfoVO {

    @ApiModelProperty("导师ID")
    private String id;

    @ApiModelProperty("导师姓名")
    private String name;

    @ApiModelProperty("导师职称")
    private String title;

    @ApiModelProperty("研究领域")
    private String researchField;

    @ApiModelProperty("邮箱")
    private String email;

    @ApiModelProperty("电话")
    private String phone;

    @ApiModelProperty("办公室")
    private String office;

    @ApiModelProperty("办公时间")
    private String officeHours;

    @ApiModelProperty("头像")
    private String avatar;

    @ApiModelProperty("个人简介")
    private String bio;

    @ApiModelProperty("指导学生数")
    private Integer studentCount;

    @ApiModelProperty("所属学院")
    private String college;
}