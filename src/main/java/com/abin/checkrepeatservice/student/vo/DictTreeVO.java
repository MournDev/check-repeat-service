package com.abin.checkrepeatservice.student.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.List;

/**
 * 字典树形VO（适配前端树形组件）
 *
 * 原先只服务学科领域树（后端把 sys_dict_data 的字符串约定拼成两级）。现在数据源换成了
 * subject 表，label/value/children 三个字段的形状**刻意保持不变** —— 这是回滚安全的
 * 关键：deploy.sh 健康检查失败会回滚应用镜像而 DDL 不回滚，旧代码（只读字典）必须
 * 能继续给新前端供树；反过来新代码的供树形状也就不能变。
 *
 * id / level 是新增的可选字段，旧代码不会填（前端必须容忍为 null）：
 *   · id    —— 管理端阈值页要提交 subjectId，只能从树里拿；
 *   · level —— 提交页要把一级门类标注成「（大类）」，靠 value 里有没有 '-' 判断也行，
 *              但那是字符串约定的老路子，显式给 level 更稳。
 */
@Data
public class DictTreeVO {
    private String label; // 显示名称（如“工学”“人工智能”）
    private String value; // 编码（如“ENG”“ENG-AI”）
    private List<DictTreeVO> children; // 子节点（二级学科）

    /**
     * 学科ID（subject.id）。13 位，不过 2^53，无需 @JsonFormat 兜底；
     * 与 parentId 同理标成 STRING，避免前端拿到混合形状。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 层级：1-一级门类，2-二级学科。旧代码路径下为 null
     */
    private Integer level;
}
