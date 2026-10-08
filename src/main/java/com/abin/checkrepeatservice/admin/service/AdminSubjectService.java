package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.SubjectUpsertReq;
import com.abin.checkrepeatservice.admin.vo.AdminSubjectVO;

import java.util.List;

/**
 * 管理端学科领域服务。
 *
 * 所有写方法在参数不合法时抛 BusinessException（对应 HTTP 400 + 自定义文案），
 * 这些"规则性拒绝"是这层的主要职责：学科表没有外键兜底，错行落库后
 * 只能在业务代码里到处兜。
 */
public interface AdminSubjectService {

    /**
     * 学科领域树（门类 → 其二级学科），附带引用次数与是否有子节点。
     */
    List<AdminSubjectVO> listTree();

    /**
     * 新增学科领域，返回新 id。
     * parentId 为 null 建一级门类，否则建该门类下的二级学科。
     */
    Long create(SubjectUpsertReq req);

    /**
     * 修改学科领域：只允许改 name / sortNo / isEnabled。
     * code 与 parentId 传了就必须与库中一致，不一致直接拒绝。
     */
    void update(Long id, SubjectUpsertReq req);

    /**
     * 启用/停用。停用后不再出现在选择器与阈值候选中，已引用的数据不受影响。
     */
    void setEnabled(Long id, boolean enabled);

    /**
     * 物理删除。要求没有下级学科、且无处引用；否则拒绝并说明原因。
     */
    void delete(Long id);

}
