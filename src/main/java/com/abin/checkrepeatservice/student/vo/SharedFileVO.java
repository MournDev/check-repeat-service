package com.abin.checkrepeatservice.student.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 *享文件VO
 */
@Data
@ApiModel("共享文件")
public class SharedFileVO {

    @ApiModelProperty("文件ID")
    private String id;

    @ApiModelProperty("文件名称")
    private String name;

    @ApiModelProperty("文件类型")
    private String type;

    @ApiModelProperty("文件大小")
    private Long size;

    @ApiModelProperty("上传者姓名")
    private String uploader;

    @ApiModelProperty("上传时间")
    private String uploadTime;

    @ApiModelProperty("文件描述")
    private String description;

    @ApiModelProperty("下载次数")
    private Integer downloadCount;
}