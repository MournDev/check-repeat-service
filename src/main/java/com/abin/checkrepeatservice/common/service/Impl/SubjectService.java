package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.common.mapper.MajorSubjectMapper;
import com.abin.checkrepeatservice.common.mapper.SubjectMapper;
import com.abin.checkrepeatservice.common.mapper.TeacherSubjectMapper;
import com.abin.checkrepeatservice.pojo.entity.MajorSubject;
import com.abin.checkrepeatservice.pojo.entity.Subject;
import com.abin.checkrepeatservice.pojo.entity.TeacherSubject;
import com.abin.checkrepeatservice.student.vo.DictTreeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学科领域服务 —— code 与 id 之间互转的**唯一入口**。
 *
 * 为什么要有这么一层：subject.code 是对外稳定标识（paper_info.subject_code 存的是它、
 * 前端树节点的 value 也是它），而 subject.id 是库内关联用的键（paper_info.subject_id、
 * 阈值、teacher_subject、major_subject 都用它）。两套标识一旦各写各的反查，就会出现
 * 「同一个 code 在 A 处查到了、B 处查不到」的漂移，所以全部收敛到这里。
 *
 * 刻意**不加缓存**：表只有 100 行且 code 上有唯一键，单次查询代价可忽略；而管理端
 * 改完学科要立刻在选择器/阈值候选里生效，加了缓存反而要处理失效。
 * （同一个理由写在 SimilarityThresholdResolverImpl 顶部，保持一致。）
 *
 * 照 DictService 的写法做成具体 @Service、不抽接口 —— 这个模块里没有接口/实现分离的惯例。
 */
@Service
@Slf4j
public class SubjectService {

    /** 一级门类 */
    private static final int LEVEL_ONE = 1;
    /** 二级学科 */
    private static final int LEVEL_TWO = 2;

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private MajorSubjectMapper majorSubjectMapper;

    @Autowired
    private TeacherSubjectMapper teacherSubjectMapper;

    // ------------------------------------------------------------------
    // 树
    // ------------------------------------------------------------------

    /**
     * 学科领域树。majorId 为 null 时返回整棵树（教师研究方向选择、管理端都用这个）。
     *
     * 传了 majorId 就按专业收窄候选：
     *   · major_subject 里该专业映射到的学科会显示；
     *   · 映射到的是二级学科时，它所属的门类**也会带上**（否则树不成形），
     *     但该门类下只列被映射到的那几个二级，不是全部 24 个；
     *   · 映射到的是门类本身时（如 美术学 → 艺术学），该门类连同它**全部启用中的二级**
     *     一起显示 —— 学生只知道大类时就直接选门类，知道细分的还能再往下选；
     *   · 该专业一条映射都没有（哲学/历史学/体育类）时**退回整棵树**，
     *     宁可选得宽一点，也不要让提交页的选择器空着。
     */
    public List<DictTreeVO> getTree(Long majorId) {
        List<Subject> enabled = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().eq(Subject::getIsEnabled, 1));

        Map<Long, List<Subject>> childrenByParent = enabled.stream()
                .filter(s -> s.getParentId() != null)
                .collect(Collectors.groupingBy(Subject::getParentId));

        // 门类靠 parent_id 为空来判定（结构上的事实）；level 列只用于下发，
        // 万一有脏数据两者不自洽，树形状仍以 parent_id 为准。
        List<Subject> levelOne = enabled.stream()
                .filter(s -> s.getParentId() == null)
                .sorted(bySortNo())
                .toList();

        Set<Long> mapped = mappedSubjectIds(majorId);

        List<DictTreeVO> tree = new ArrayList<>();
        for (Subject parent : levelOne) {
            boolean parentMapped = mapped == null || mapped.contains(parent.getId());

            List<DictTreeVO> childVOs = new ArrayList<>();
            for (Subject child : childrenByParent.getOrDefault(parent.getId(), List.of()).stream()
                    .sorted(bySortNo()).toList()) {
                if (mapped == null || parentMapped || mapped.contains(child.getId())) {
                    childVOs.add(toVO(child, LEVEL_TWO, null));
                }
            }

            // 门类只是作为父节点被带出来的、而它的二级全被过滤光了 → 整枝不出现在候选里。
            // （否则会显示成一个能选却毫无意义的光杆门类。）
            if (!parentMapped && childVOs.isEmpty()) {
                continue;
            }
            tree.add(toVO(parent, LEVEL_ONE, childVOs.isEmpty() ? null : childVOs));
        }
        return tree;
    }

    /**
     * 该专业映射到的学科 id；无映射（或没传专业）返回 null，语义是"不过滤"。
     */
    private Set<Long> mappedSubjectIds(Long majorId) {
        if (majorId == null) {
            return null;
        }
        List<MajorSubject> rels = majorSubjectMapper.selectList(
                new LambdaQueryWrapper<MajorSubject>().eq(MajorSubject::getMajorId, majorId));
        if (rels.isEmpty()) {
            return null;
        }
        return rels.stream().map(MajorSubject::getSubjectId).collect(Collectors.toSet());
    }

    private static Comparator<Subject> bySortNo() {
        return Comparator.comparing(Subject::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Subject::getCode, Comparator.nullsLast(String::compareTo));
    }

    private static DictTreeVO toVO(Subject s, int fallbackLevel, List<DictTreeVO> children) {
        DictTreeVO vo = new DictTreeVO();
        vo.setId(s.getId());
        vo.setLabel(s.getName());
        vo.setValue(s.getCode());
        vo.setLevel(s.getLevel() != null ? s.getLevel() : fallbackLevel);
        vo.setChildren(children);
        return vo;
    }

    // ------------------------------------------------------------------
    // code ↔ id
    // ------------------------------------------------------------------

    /**
     * 由 code 反查 id。**不限定 is_enabled** —— 学科被停用之后，引用它的历史论文
     * 仍然要能解析出 id，否则阈值解析会因为这些论文而错位。
     * 查不到返回 null（调用方自行决定是忽略还是报错）。
     */
    public Long findIdByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        Subject s = subjectMapper.selectOne(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getCode, code.trim())
                .last("LIMIT 1"));
        return s == null ? null : s.getId();
    }

    /**
     * 某学科的父学科 id。
     *
     * 返回 null 有两种情况，调用方**都不该区分对待**：传进来的是门类（parent_id 本就为 NULL），
     * 或查不到这个学科。两种都意味着"没有可继承的对象"，继续向下回退即可。
     * 阈值解析靠它实现「二级没配则继承其门类」。
     */
    public Long parentIdOf(Long subjectId) {
        if (subjectId == null) {
            return null;
        }
        Subject s = subjectMapper.selectById(subjectId);
        return s == null ? null : s.getParentId();
    }

    /**
     * 批量 code → id，保序、去重、忽略空白与查不到的 code。
     * 返回 LinkedHashMap，方便调用方按需再取 keySet/values。
     */
    public Map<String, Long> idMapOfCodes(Collection<String> codes) {
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        if (codes == null) {
            return result;
        }
        Set<String> wanted = codes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .filter(c -> !result.containsKey(c))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (wanted.isEmpty()) {
            return result;
        }
        List<Subject> found = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().in(Subject::getCode, wanted));
        Map<String, Long> byCode = found.stream()
                .collect(Collectors.toMap(Subject::getCode, Subject::getId, (a, b) -> a));
        for (String c : wanted) {
            Long id = byCode.get(c);
            if (id != null) {
                result.put(c, id);
            } else {
                log.warn("学科 code 反查不到 id，已忽略：{}", c);
            }
        }
        return result;
    }

    /** 批量 code → id（只要 id 列表，保序去重） */
    public List<Long> idsOf(Collection<String> codes) {
        return new ArrayList<>(idMapOfCodes(codes).values());
    }

    /** 批量 id → code，保序、忽略查不到的 id */
    public List<String> codesOf(Collection<Long> ids) {
        return selectOrdered(ids, Subject::getCode);
    }

    /** 批量 id → 中文名，保序、忽略查不到的 id */
    public List<String> namesOf(Collection<Long> ids) {
        return selectOrdered(ids, Subject::getName);
    }

    /**
     * 逗号分隔的 code 串 → 中文名列表（用于把 teacher_subject 拼成
     * sys_user.research_direction 那份「学科中文名 CSV 缓存」）。
     */
    public List<String> namesOfCodes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> codes = java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(c -> !c.isEmpty())
                .toList();
        return namesOf(idsOf(codes));
    }

    /** 某专业映射到的学科 id 列表（仅启用中的；论文提交页/阈值候选用） */
    public List<Long> listByMajorId(Long majorId) {
        if (majorId == null) {
            return List.of();
        }
        List<Long> ids = majorSubjectMapper.selectList(
                        new LambdaQueryWrapper<MajorSubject>().eq(MajorSubject::getMajorId, majorId))
                .stream().map(MajorSubject::getSubjectId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return subjectMapper.selectList(new LambdaQueryWrapper<Subject>()
                        .in(Subject::getId, ids)
                        .eq(Subject::getIsEnabled, 1))
                .stream().map(Subject::getId).toList();
    }

    // ------------------------------------------------------------------
    // 教师研究方向（teacher_subject 是这一项的真源）
    // ------------------------------------------------------------------

    /**
     * 覆盖式写入某教师的研究方向：先物理删除旧关联，再按 subjectIds 批量插新的。
     *
     * 教师保存设置是"整套覆盖"语义，所以这里是 replace 而不是 merge。
     * 关联表一律**物理删除**（见 TeacherSubjectMapper.deleteByUserId 的理由：
     * 复合唯一键不含 is_deleted，软删后重选同一学科会撞唯一键）。
     *
     * 收 id 而不是收 code：code→id 的反查可能有"一个都认不出"的情况（旧前端发的短码），
     * 那种情况下调用方必须能**在删除之前**决定要不要动数据，所以反查留在外面做。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceUserSubjects(Long userId, Collection<Long> subjectIds) {
        teacherSubjectMapper.deleteByUserId(userId);
        if (subjectIds == null || subjectIds.isEmpty()) {
            return;
        }
        // 显式不赋 id，走雪花（IdType.ASSIGN_ID）；create_by/update_by 由 MyMetaObjectHandler 补
        for (Long subjectId : new LinkedHashSet<>(subjectIds)) {
            TeacherSubject rel = new TeacherSubject();
            rel.setUserId(userId);
            rel.setSubjectId(subjectId);
            teacherSubjectMapper.insert(rel);
        }
    }

    /**
     * 找出 research_direction 缓存里**认不出对应学科**的旧值（迁移遗留的短码，如
     * machine_learning / database）。教师设置页拿它显示「已失效」提示。
     *
     * 为什么需要这个方法：存量里 7 个旧短码只有 2 个（ai、software_engineering）能
     * 同名映射到学科，其余 5 个在 subject 表里既不是 code 也不是 name。它们不参与
     * teacher_subject，所以回显的多选框里根本不显示 —— 教师看一眼以为"没选研究方向"，
     * 随手保存就把这些信息永久冲掉了。先把它们列出来告知，再让教师自己决定。
     *
     * 判定口径：tokens 里既不等于任何 subject.code、也不等于任何 subject.name 的那些。
     * 迁移脚本已把命中的 token 换成了中文名，所以「人工智能」这类**认得出**（按 name 命中），
     * 不会被误报成失效。
     */
    public List<String> staleCacheTokens(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> tokens = java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .distinct()
                .toList();
        if (tokens.isEmpty()) {
            return List.of();
        }
        List<Subject> hits = subjectMapper.selectList(new LambdaQueryWrapper<Subject>()
                .in(Subject::getCode, tokens)
                .or()
                .in(Subject::getName, tokens));
        Set<String> known = new HashSet<>();
        for (Subject s : hits) {
            known.add(s.getCode());
            known.add(s.getName());
        }
        return tokens.stream().filter(t -> !known.contains(t)).toList();
    }

    /** 某教师的学科 id 列表（保序、去重） */
    public List<Long> idsOfUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return teacherSubjectMapper.selectList(
                        new LambdaQueryWrapper<TeacherSubject>().eq(TeacherSubject::getUserId, userId))
                .stream().map(TeacherSubject::getSubjectId).toList();
    }

    /**
     * 某教师的学科 code 列表 —— 教师设置页多选框回显用。
     *
     * **只从 teacher_subject 取**，不回退去解析 sys_user.research_direction：
     * 那一列是缓存，且存量里还留着 5 个映射不到学科的旧短码（ai 之外的
     * machine_learning 等）。要是拿它回显，旧短码会被当成"已选学科"塞回多选框，
     * 而多选框的选项来自 subject 表 —— 于是显示出一个选不中/选不掉的幽灵选中项。
     * 旧值该走"已失效"提示，由前端单独呈现。
     */
    public List<String> codesOfUser(Long userId) {
        return codesOf(idsOfUser(userId));
    }

    // ------------------------------------------------------------------
    // 引用检查
    // ------------------------------------------------------------------

    /**
     * 该学科是否被引用（论文/阈值/教师研究方向/专业映射任一）。
     * 管理端删除学科前必须过这一关 —— 没有外键兜底，被删掉的学科会让
     * paper_info.subject_id 直接悬空。
     */
    public boolean isReferenced(Long subjectId) {
        return countReferences(subjectId) > 0;
    }

    /** 引用次数，供管理端把「有 N 处引用」原样告诉用户 */
    public int countReferences(Long subjectId) {
        if (subjectId == null) {
            return 0;
        }
        return subjectMapper.countReferences(subjectId);
    }

    /** 下级学科个数。管理端删除被拒时要把它写进文案（"下面还有 3 个二级学科"） */
    public long countChildren(Long subjectId) {
        if (subjectId == null) {
            return 0;
        }
        return subjectMapper.selectCount(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getParentId, subjectId));
    }

    /** 是否有下级学科（有子节点不许删） */
    public boolean hasChildren(Long subjectId) {
        return countChildren(subjectId) > 0;
    }

    /** 按 id 批量取某字段，保持入参顺序 */
    private List<String> selectOrdered(Collection<Long> ids, Function<Subject, String> getter) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> wanted = ids.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<Subject> found = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().in(Subject::getId, wanted));
        Map<Long, String> byId = new HashMap<>();
        for (Subject s : found) {
            byId.put(s.getId(), getter.apply(s));
        }
        List<String> result = new ArrayList<>(wanted.size());
        for (Long id : wanted) {
            String v = byId.get(id);
            if (v != null) {
                result.add(v);
            }
        }
        return result;
    }
}
