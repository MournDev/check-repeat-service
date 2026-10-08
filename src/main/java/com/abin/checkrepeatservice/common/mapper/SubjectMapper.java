package com.abin.checkrepeatservice.common.mapper;

import com.abin.checkrepeatservice.pojo.dto.SubjectRefCountDTO;
import com.abin.checkrepeatservice.pojo.entity.Subject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 学科领域数据访问层
 *
 * 只有 100 行左右、且 code 上有唯一键，按 code 反查/按 ids 批量取名这类操作在
 * SubjectService 里用 LambdaQueryWrapper 拼即可，所以这里只有一个自定义方法
 * （countReferences），保持和 CollegeMapper / MajorMapper 一样的极简风格。
 */
@Mapper
public interface SubjectMapper extends BaseMapper<Subject> {

    /**
     * 统计某学科被引用的次数（管理端删除学科前的拦截依据）。
     *
     * 四个引用点：论文、阈值配置、教师研究方向、专业映射。学科没有外键约束，
     * 这些引用全靠代码维护，所以删除前必须显式查一遍。
     *
     * 一次查询用标量子查询拼出来，避免注入四个 mapper 再各查一次。
     */
    @Select("""
            SELECT (SELECT COUNT(*) FROM paper_info p
                     WHERE p.subject_id = #{subjectId} AND p.is_deleted = 0)
                 + (SELECT COUNT(*) FROM category_similarity_threshold t
                     WHERE t.subject_id = #{subjectId} AND t.is_deleted = 0)
                 + (SELECT COUNT(*) FROM teacher_subject ts
                     WHERE ts.subject_id = #{subjectId} AND ts.is_deleted = 0)
                 + (SELECT COUNT(*) FROM major_subject ms
                     WHERE ms.subject_id = #{subjectId} AND ms.is_deleted = 0)
            """)
    int countReferences(@Param("subjectId") Long subjectId);

    /**
     * 全表引用次数，一次查完给管理端列表用。
     *
     * 为什么不让列表逐行调 countReferences：学科有 100 行，那个方法每条要跑四个子查询，
     * 逐行调就是 400 次子查询；这里一条 SQL 出全部，行数=学科数。
     * 与 countReferences 保持完全相同的口径，改一处必须改另一处。
     */
    @Select("""
            SELECT s.id AS subject_id,
                   ((SELECT COUNT(*) FROM paper_info p
                      WHERE p.subject_id = s.id AND p.is_deleted = 0)
                  + (SELECT COUNT(*) FROM category_similarity_threshold t
                      WHERE t.subject_id = s.id AND t.is_deleted = 0)
                  + (SELECT COUNT(*) FROM teacher_subject ts
                      WHERE ts.subject_id = s.id AND ts.is_deleted = 0)
                  + (SELECT COUNT(*) FROM major_subject ms
                      WHERE ms.subject_id = s.id AND ms.is_deleted = 0)) AS ref_count
            FROM subject s
            WHERE s.is_deleted = 0
            """)
    List<SubjectRefCountDTO> countReferencesAll();

    /**
     * 物理删除。
     *
     * 必须自定义而不是用 deleteById —— BaseEntity.isDeleted 上有 @TableLogic，
     * deleteById 会变成「把 is_deleted 置 1」的软删，而 uk_subject_code 不含 is_deleted：
     * 软删掉 ENG-AI 之后再也建不回同一个 code，错误还是唯一键冲突这种难懂的样子。
     * 学科表的约定是「停用走 is_enabled，删除是真删」（见 pojo/entity/Subject.java）。
     */
    @Delete("DELETE FROM subject WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

}
