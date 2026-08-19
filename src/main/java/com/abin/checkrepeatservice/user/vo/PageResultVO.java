package com.abin.checkrepeatservice.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果VO（原有逻辑通用：所有分页接口统一返回格式）
 * @param <T> 数据列表泛型（支持不同类型的分页数据）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResultVO<T> {

    /**
     * 当前页码（从1开始）
     */
    private Integer pageNum;

    /**
     * 每页条数
     */
    private Integer pageSize;

    /**
     * 总数据条数
     */
    private Integer totalCount;

    /**
     * 总页数
     */
    private Integer totalPage;

    /**
     * 当前页数据列表
     */
    private List<T> list;

    /**
     * 快速创建空分页结果（无数据时用）
     * @param pageNum 当前页码
     * @param pageSize 每页条数
     * @param <T> 泛型
     * @return 空分页结果
     */
    public static <T> PageResultVO<T> empty(Integer pageNum, Integer pageSize) {
        return new PageResultVO<>(pageNum, pageSize, 0, 0, null);
    }
}
