package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.SubjectUpsertReq;
import com.abin.checkrepeatservice.admin.service.AdminSubjectService;
import com.abin.checkrepeatservice.admin.vo.AdminSubjectVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.mapper.SubjectMapper;
import com.abin.checkrepeatservice.common.service.Impl.SubjectService;
import com.abin.checkrepeatservice.pojo.dto.SubjectRefCountDTO;
import com.abin.checkrepeatservice.pojo.entity.Subject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端学科领域服务实现。
 *
 * 这个类的实质是一组**规则**：学科表没有外键、也没有软删兜底，
 * 一旦放进自相矛盾的行（二级的 parent_id 指向另一个二级、code 大小写不一、
 * 删掉一个还被论文引用的学科），后续只在业务代码里四处漏。
 * 所以每条写路径都先校验、再落库，拒绝理由都写进 BusinessException 的文案。
 *
 * 两条贯穿全文的硬规则：
 *   1. code 创建后不可改 —— paper_info.subject_code 存的就是它，
 *      改了会一次性断掉所有既有引用（需要级联，本期不做）。
 *   2. 删除只能物理删，且必须先过"无子节点 + 无引用"两道关。
 *      软删在这里是错的：uk_subject_code 不含 is_deleted，软删后同 code 再也建不回来。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSubjectServiceImpl implements AdminSubjectService {

    /** 一级门类 */
    private static final int LEVEL_ONE = 1;
    /** 二级学科 */
    private static final int LEVEL_TWO = 2;

    private final SubjectMapper subjectMapper;

    /** 引用检查与子节点计数收敛在 SubjectService，不在这里另写一套 SQL */
    private final SubjectService subjectService;

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    @Override
    public List<AdminSubjectVO> listTree() {
        List<Subject> all = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>()
                        .orderByAsc(Subject::getSortNo)
                        .orderByAsc(Subject::getCode));

        Map<Long, Integer> refCounts = new HashMap<>();
        for (SubjectRefCountDTO row : subjectMapper.countReferencesAll()) {
            refCounts.put(row.getSubjectId(), row.getRefCount() == null ? 0 : row.getRefCount());
        }

        Map<Long, AdminSubjectVO> byId = new LinkedHashMap<>();
        List<AdminSubjectVO> roots = new ArrayList<>();
        List<Subject> levelTwo = new ArrayList<>();

        for (Subject s : all) {
            AdminSubjectVO vo = toVO(s, refCounts);
            byId.put(s.getId(), vo);
            if (s.getParentId() == null) {
                roots.add(vo);
            } else {
                levelTwo.add(s);
            }
        }

        for (Subject child : levelTwo) {
            AdminSubjectVO parent = byId.get(child.getParentId());
            if (parent == null) {
                // 库里的孤儿行（parent_id 指向不存在的学科，或指向一个已被软删的行）。
                // 不静默丢掉——丢掉的话管理页上根本看不见它，也就永远修不掉；
                // 挂到根上让管理员看得见、点得进去删。段 5.7 的 orphan 断言期望它是 0。
                log.warn("学科领域存在孤儿行，已在管理端挂到根节点展示 - id: {}, code: {}, parentId: {}",
                        child.getId(), child.getCode(), child.getParentId());
                roots.add(byId.get(child.getId()));
                continue;
            }
            if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>());
            }
            parent.getChildren().add(byId.get(child.getId()));
        }

        // 有子节点才不能删，而"有没有子节点"在这棵树里本来就已经算出来了，
        // 不必再对每个门类跑一次 COUNT。二级学科不可能有第三级，恒为 false。
        for (AdminSubjectVO root : roots) {
            root.setHasChildren(root.getChildren() != null && !root.getChildren().isEmpty());
        }
        return roots;
    }

    private AdminSubjectVO toVO(Subject s, Map<Long, Integer> refCounts) {
        AdminSubjectVO vo = new AdminSubjectVO();
        vo.setId(s.getId());
        vo.setParentId(s.getParentId());
        vo.setLevel(s.getLevel());
        vo.setCode(s.getCode());
        vo.setName(s.getName());
        vo.setSortNo(s.getSortNo());
        vo.setIsEnabled(s.getIsEnabled());
        vo.setReferenceCount(refCounts.getOrDefault(s.getId(), 0));
        vo.setHasChildren(false);
        return vo;
    }

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SubjectUpsertReq req) {
        String code = req.getCode().trim();
        String name = req.getName().trim();
        Long parentId = req.getParentId();

        if (subjectMapper.selectCount(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getCode, code)) > 0) {
            // 唯一键 uk_subject_code 是并发下的最终兜底，这里先查一次是为了给出
            // "学科编码已存在"这句人话，而不是把 SQLIntegrityConstraintViolation 冒到前端
            throw new BusinessException(ResultCode.PARAM_ERROR, "学科编码已存在：" + code, null);
        }

        Integer level = LEVEL_ONE;
        if (parentId != null) {
            Subject parent = subjectMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "所选上级门类不存在", null);
            }
            if (parent.getParentId() != null) {
                // 只允许两级。放过去的话，这门课的表结构和阈值继承（只往上找一层）
                // 都会当场失效，而且是静默失效
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "「" + parent.getName() + "」本身就是二级学科，不能作为上级（学科领域只支持两级）", null);
            }
            level = LEVEL_TWO;
        }

        Integer isEnabled = req.getIsEnabled();
        if (isEnabled != null && isEnabled != 0 && isEnabled != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "启用状态只能是0或1", null);
        }

        Subject entity = new Subject();
        entity.setCode(code);
        entity.setName(name);
        entity.setParentId(parentId);
        entity.setLevel(level);
        entity.setSortNo(req.getSortNo() != null ? req.getSortNo() : nextSortNo(parentId, level));
        entity.setIsEnabled(isEnabled != null ? isEnabled : 1);
        // 显式置 0：BaseEntity.isDeleted 没有 fill 注解，MyMetaObjectHandler 的
        // strictInsertFill 不会补它；落 NULL 的话后续查询恒拼 `is_deleted = 0` 就再也读不回这行
        entity.setIsDeleted(0);

        subjectMapper.insert(entity);
        log.info("新增学科领域成功 - id: {}, code: {}, name: {}, level: {}, parentId: {}",
                entity.getId(), code, name, level, parentId);
        return entity.getId();
    }

    /** 同级末尾的下一个排序号（没传 sortNo 时用，免得新建的门类插到已有列表中间） */
    private int nextSortNo(Long parentId, Integer level) {
        LambdaQueryWrapper<Subject> wrapper = new LambdaQueryWrapper<>();
        if (level == LEVEL_ONE) {
            wrapper.isNull(Subject::getParentId);
        } else {
            wrapper.eq(Subject::getParentId, parentId);
        }
        wrapper.orderByDesc(Subject::getSortNo).last("LIMIT 1");
        Subject last = subjectMapper.selectOne(wrapper);
        return (last == null || last.getSortNo() == null) ? 1 : last.getSortNo() + 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SubjectUpsertReq req) {
        Subject existing = requireSubject(id);

        String code = req.getCode().trim();
        if (!code.equals(existing.getCode())) {
            // 拒绝而不是静默忽略：静默忽略会让管理员以为改成功了，
            // 而 paper_info.subject_code 上还留着旧编码
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "学科编码创建后不可修改（当前为 " + existing.getCode() + "）", null);
        }

        // parentId 传 null 视为"不修改"：两级之间没有合法的"移动"，
        // 一级不可能有上级、二级也不允许在门类之间搬（搬了会连带影响已有阈值与论文归属）。
        // 前端编辑弹窗把门类字段做成只读回显，正常路径不会走到这里。
        Long parentId = req.getParentId();
        if (parentId != null && !parentId.equals(existing.getParentId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "学科所属门类不支持修改（如需调整，请删除后重新创建）", null);
        }

        Integer isEnabled = req.getIsEnabled();
        if (isEnabled != null && isEnabled != 0 && isEnabled != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "启用状态只能是0或1", null);
        }

        existing.setName(req.getName().trim());
        if (req.getSortNo() != null) {
            existing.setSortNo(req.getSortNo());
        }
        if (isEnabled != null) {
            existing.setIsEnabled(isEnabled);
        }

        subjectMapper.updateById(existing);
        log.info("修改学科领域成功 - id: {}, code: {}, name: {}, isEnabled: {}",
                id, existing.getCode(), existing.getName(), existing.getIsEnabled());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(Long id, boolean enabled) {
        Subject existing = requireSubject(id);
        existing.setIsEnabled(enabled ? 1 : 0);
        subjectMapper.updateById(existing);
        log.info("{}学科领域 - id: {}, code: {}", enabled ? "启用" : "停用", id, existing.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Subject existing = requireSubject(id);

        long children = subjectService.countChildren(id);
        if (children > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "「" + existing.getName() + "」下还有 " + children + " 个二级学科，请先删除它们", null);
        }

        int references = subjectService.countReferences(id);
        if (references > 0) {
            // 没有外键，删掉就是让 paper_info.subject_id / 阈值配置 / 教师研究方向 /
            // major_subject 悬空，而且不会有任何报错。宁可拒绝。
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "「" + existing.getName() + "」已被 " + references
                            + " 处引用（论文 / 阈值配置 / 教师研究方向 / 专业映射），不能删除；"
                            + "如不再使用，请改为「停用」", null);
        }

        subjectMapper.physicalDeleteById(id);
        log.info("删除学科领域成功 - id: {}, code: {}", id, existing.getCode());
    }

    private Subject requireSubject(Long id) {
        Subject existing = id == null ? null : subjectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "学科领域不存在或已删除", null);
        }
        return existing;
    }

}
