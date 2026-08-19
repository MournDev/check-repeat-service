package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

import java.util.Date;

// 待办事项DTO
@Data
public class TodoItemDTO {
    private Long id;
    private String title;
    private String description;
    private Boolean completed;
    private String priority;        // high, normal, low
    private Date dueDate;
    private Date createTime;
}
