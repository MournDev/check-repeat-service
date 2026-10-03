package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.common.mapper.CategorySimilarityThresholdMapper;
import com.abin.checkrepeatservice.common.mapper.SimilarityThresholdMapper;
import com.abin.checkrepeatservice.common.service.SimilarityThresholdResolver;
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
        return resolve(collegeId, majorId);
    }

    @Override
    public BigDecimal resolve(Long collegeId, Long majorId) {
        // 只知道专业时补出所属学院，好让专业没配阈值时能回退到学院级
        if (collegeId == null && majorId != null) {
            Major major = majorMapper.selectById(majorId);
            if (major != null) {
                collegeId = major.getCollegeId();
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
        LambdaQueryWrapper<CategorySimilarityThreshold> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategorySimilarityThreshold::getCategoryType, categoryType);
        if (collegeId != null) {
            wrapper.eq(CategorySimilarityThreshold::getCollegeId, collegeId);
        }
        if (majorId != null) {
            wrapper.eq(CategorySimilarityThreshold::getMajorId, majorId);
        }
        wrapper.orderByDesc(CategorySimilarityThreshold::getId).last("LIMIT 1");

        CategorySimilarityThreshold row = categorySimilarityThresholdMapper.selectOne(wrapper);
        return (row == null || row.getThreshold() == null)
                ? null
                : BigDecimal.valueOf(row.getThreshold());
    }

}
