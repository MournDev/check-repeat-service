package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 操作日志查询DTO
 */
@Data
public class OperationLogQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标题 */
    private String title;

    /** 业务类型 */
    private String businessType;

    /** 操作状态 */
    private Integer status;

    /** 操作人员 */
    private String operName;

    /** 开始时间 */
    private String beginTime;

    /** 结束时间 */
    private String endTime;

    /** 当前页码 */
    private Integer pageNum = 1;

    /** 每页显示记录数 */
    private Integer pageSize = 10;
}

