package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.common.mapper.CategorySimilarityThresholdMapper;
import com.abin.checkrepeatservice.common.mapper.SimilarityThresholdMapper;
import com.abin.checkrepeatservice.common.service.SimilarityThresholdResolver;
import com.abin.checkrepeatservice.common.service.Impl.SubjectService;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.CategorySimilarityThreshold;
import com.abin.checkrepeatservice.pojo.entity.Major;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SimilarityThreshold;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.MajorMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 相似度阈值解析实现：专业 &gt; 学院 &gt; 全局 &gt; yml 兜底。
 *
 * 不做缓存。SystemConfigServiceImpl.refreshConfigCache() 本身是空壳，
 * 项目也没有引入 Caffeine/Redis 缓存设施；而每次查重本来就要跑 Tika 解析与查重引擎
 * （秒级），这里多的两次带索引/主键的查询可以忽略。不缓存还有个好处：
 * 管理端 PUT 之后立即生效，不存在缓存失效窗口。
 */
@Service
@RequiredArgsConstructor
public class SimilarityThresholdResolverImpl implements SimilarityThresholdResolver {

    private final SimilarityThresholdMapper similarityThresholdMapper;

    private final CategorySimilarityThresholdMapper categorySimilarityThresholdMapper;

    private final MajorMapper majorMapper;

    private final SysUserMapper sysUserMapper;

    /**
     * 学科两级查法与 subject_code 兜底反查都走它 —— code→id 的规则只允许有一份实现，
     * 散开写就会出现"同一篇论文在这里解析出了学科、在别处解析不出"的漂移。
     */
    private final SubjectService subjectService;

    /**
     * 兜底阈值。与改造前各判定点注入的是同一个配置键，
     * 因此两张新表为空时判定结果与改造前完全一致。
     */
    @Value("${admin.check-rule.default-threshold:20.00}")
    private BigDecimal fallbackThreshold;

    @Override
    public BigDecimal resolveForPaper(PaperInfo paperInfo) {
        if (paperInfo == null) {
            return globalThreshold();
        }
        Long collegeId = paperInfo.getCollegeId();
        Long majorId = paperInfo.getMajorId();

        // 论文上没带学院/专业时，回查学生资料兜底
        if (collegeId == null && majorId == null && paperInfo.getStudentId() != null) {
            SysUser student = sysUserMapper.selectById(paperInfo.getStudentId());
            if (student != null) {
                collegeId = student.getCollegeId();
                majorId = student.getMajorId();
            }
        }
        return resolve(subjectIdOf(paperInfo), collegeId, majorId);
    }

    @Override
    public BigDecimal resolve(Long collegeId, Long majorId) {
        // 两参版本保持原样（学生端「当前查重规则」卡片还在用它），
        // 只把它转成三参版本、subjectId 传 null —— 于是行为逐字节不变。
        return resolve(null, collegeId, majorId);
    }

    @Override
    public BigDecimal resolve(Long subjectId, Long collegeId, Long majorId) {
        // 只知道专业时补出所属学院，好让专业没配阈值时能回退到学院级
        if (collegeId == null && majorId != null) {
            Major major = majorMapper.selectById(majorId);
            if (major != null) {
                collegeId = major.getCollegeId();
            }
        }

        // 学科维度排在最前：它比"专业"更贴近论文本身（同一专业下不同学科方向的
        // 重复率标准可以不同）。两级查法即"精确没配就继承其门类"。
        if (subjectId != null) {
            BigDecimal threshold = categoryThreshold(
                    CategorySimilarityThreshold.TYPE_SUBJECT, null, null, subjectId);
            if (threshold != null) {
                return threshold;
            }
            // 二级学科没配 → 继承它所属门类上配的那条
            Long parentId = subjectService.parentIdOf(subjectId);
            if (parentId != null) {
                threshold = categoryThreshold(
                        CategorySimilarityThreshold.TYPE_SUBJECT, null, null, parentId);
                if (threshold != null) {
                    return threshold;
                }
            }
        }

        if (majorId != null) {
            BigDecimal threshold = categoryThreshold(
                    CategorySimilarityThreshold.TYPE_MAJOR, null, majorId);
            if (threshold != null) {
                return threshold;
            }
        }

        if (collegeId != null) {
            BigDecimal threshold = categoryThreshold(
                    CategorySimilarityThreshold.TYPE_COLLEGE, collegeId, null);
            if (threshold != null) {
                return threshold;
            }
        }

        return globalThreshold();
    }

    /**
     * 论文的学科ID：优先用 subject_id；存量论文可能只有 subject_code（1.10.5 的回填
     * 只覆盖了 code 能对上学科的，脏 code 会留空），所以这里用 code 兜底反查一次。
     * 不兜底的话，那些论文永远拿不到学科维度的阈值，且不会报任何错。
     */
    private Long subjectIdOf(PaperInfo paperInfo) {
        if (paperInfo.getSubjectId() != null) {
            return paperInfo.getSubjectId();
        }
        if (paperInfo.getSubjectCode() == null || paperInfo.getSubjectCode().isBlank()) {
            return null;
        }
        return subjectService.findIdByCode(paperInfo.getSubjectCode());
    }

    @Override
    public Integer getEffectiveGlobalThreshold() {
        return globalThreshold().intValue();
    }

    /**
     * 取当前生效的全局阈值：表里有配置就用表里的，否则用 yml 兜底值。
     *
     * similarity_threshold 在语义上是"单行表"，但表结构上并没有唯一约束，
     * 所以这里必须把"取哪一行"定死：按 id 倒序取第一条。
     * 用 id 而不是 update_time 排序，是因为雪花 id 恒有值且单调递增，
     * 而 update_time 在有人手工 INSERT 时可能为 NULL（MySQL 的 DESC 会把 NULL 排在最后，
     * 那行配置就永远选不中了）。写入路径永远更新它读到的这一行，id 因此保持稳定。
     */
    private BigDecimal globalThreshold() {
        SimilarityThreshold row = similarityThresholdMapper.selectOne(
                new LambdaQueryWrapper<SimilarityThreshold>()
                        .orderByDesc(SimilarityThreshold::getId)
                        .last("LIMIT 1"));
        if (row != null && row.getGlobalThreshold() != null) {
            return BigDecimal.valueOf(row.getGlobalThreshold());
        }
        return fallbackThreshold;
    }

    /**
     * 查某一级（学院或专业）配置的阈值，没有配置时返回 null 以继续向上回退。
     */
    private BigDecimal categoryThreshold(String categoryType, Long collegeId, Long majorId) {
        return categoryThreshold(categoryType, collegeId, majorId, null);
    }

    /**
     * 查某一级（学院/专业/学科）配置的阈值，没配则返回 null 以继续向上回退。
     */
    private BigDecimal categoryThreshold(String categoryType, Long collegeId, Long majorId, Long subjectId) {
        LambdaQueryWrapper<CategorySimilarityThreshold> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategorySimilarityThreshold::getCategoryType, categoryType);
        if (collegeId != null) {
            wrapper.eq(CategorySimilarityThreshold::getCollegeId, collegeId);
        }
        if (majorId != null) {
            wrapper.eq(CategorySimilarityThreshold::getMajorId, majorId);
        }
        if (subjectId != null) {
            wrapper.eq(CategorySimilarityThreshold::getSubjectId, subjectId);
        }
        wrapper.orderByDesc(CategorySimilarityThreshold::getId).last("LIMIT 1");

        CategorySimilarityThreshold row = categorySimilarityThresholdMapper.selectOne(wrapper);
        return (row == null || row.getThreshold() == null)
                ? null
                : BigDecimal.valueOf(row.getThreshold());
    }

}
