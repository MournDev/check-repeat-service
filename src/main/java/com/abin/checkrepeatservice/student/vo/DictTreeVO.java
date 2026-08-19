package com.abin.checkrepeatservice.student.vo;

import lombok.Data;
import java.util.List;

/**
 * 字典树形VO（适配前端树形组件）
 */
@Data
public class DictTreeVO {
    private String label; // 显示名称（如“工学”“人工智能”）
    private String value; // 编码（如“ENG”“ENG-AI”）
    private List<DictTreeVO> children; // 子节点（二级学科）
}