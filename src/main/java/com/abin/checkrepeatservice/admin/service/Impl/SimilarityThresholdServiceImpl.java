package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.SimilarityThresholdUpdateReq;
import com.abin.checkrepeatservice.admin.service.SimilarityThresholdService;
import com.abin.checkrepeatservice.admin.vo.SimilarityThresholdVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.mapper.CategorySimilarityThresholdMapper;
import com.abin.checkrepeatservice.common.mapper.SimilarityThresholdMapper;
import com.abin.checkrepeatservice.common.mapper.CollegeMapper;
import com.abin.checkrepeatservice.common.mapper.SubjectMapper;
import com.abin.checkrepeatservice.common.service.SimilarityThresholdResolver;
import com.abin.checkrepeatservice.pojo.entity.CategorySimilarityThreshold;
import com.abin.checkrepeatservice.pojo.entity.College;
import com.abin.checkrepeatservice.pojo.entity.Major;
import com.abin.checkrepeatservice.pojo.entity.SimilarityThreshold;
import com.abin.checkrepeatservice.pojo.entity.Subject;
import com.abin.checkrepeatservice.student.mapper.MajorMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 相似度阈值设置服务实现。
 *
 * 写入策略：全局阈值在 similarity_threshold 上 upsert 单行；
 * 学院/专业阈值整表覆盖（物理清空后重新插入）。
 * 选整表覆盖而不是按 id 差量，是因为前端新增项的 id 是 Date.now() 造的临时值，
 * 没有业务含义；而 PUT 天然提交的就是完整集合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarityThresholdServiceImpl implements SimilarityThresholdService {

    private final SimilarityThresholdMapper similarityThresholdMapper;

    private final CategorySimilarityThresholdMapper categorySimilarityThresholdMapper;

    private final CollegeMapper collegeMapper;

    private final MajorMapper majorMapper;

    private final SimilarityThresholdResolver similarityThresholdResolver;

    private final SubjectMapper subjectMapper;

    @Override
    public SimilarityThresholdVO getThresholds() {
        SimilarityThresholdVO vo = new SimilarityThresholdVO();
        vo.setGlobalThreshold(similarityThresholdResolver.getEffectiveGlobalThreshold());
        vo.setCategoryThresholds(loadCategoryItems());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateThresholds(SimilarityThresholdUpdateReq req) {
        validate(req);

        saveGlobalThreshold(req.getGlobalThreshold());

        List<SimilarityThresholdUpdateReq.CategoryItem> items = req.getCategoryThresholds();
        categorySimilarityThresholdMapper.deleteAll();
        if (items != null) {
            for (SimilarityThresholdUpdateReq.CategoryItem item : items) {
                CategorySimilarityThreshold entity = new CategorySimilarityThreshold();
                entity.setCategoryType(item.getCategoryType());
                entity.setCollegeId(item.getCollegeId());
                entity.setMajorId(item.getMajorId());
                entity.setSubjectId(item.getSubjectId());
                entity.setThreshold(item.getThreshold());
                // DDL 默认值不足恃（存量库的 NULL 行见 db/存量修复.sql 段 2）：
                // BaseEntity.isDeleted 没有 fill 注解，MyMetaObjectHandler 的 strictInsertFill 不会补，
                // 落 NULL 的话后续查询恒拼 `is_deleted = 0` 就再也读不回这一行。这里显式兜底。
                entity.setIsDeleted(0);
                categorySimilarityThresholdMapper.insert(entity);
            }
        }

        log.info("相似度阈值设置已更新：全局={}，学院/专业配置 {} 条",
                req.getGlobalThreshold(), items == null ? 0 : items.size());
    }

    /**
     * upsert 全局阈值这一行。
     *
     * similarity_threshold 在语义上是单行表但结构上没有唯一约束，
     * 所以"取哪一行"要和 SimilarityThresholdResolver 里的读法一致：按 id 倒序取第一条，
     * 读到就更新、读不到才插入。这样写入路径永远落在同一行上，不会越插越多。
     */
    private void saveGlobalThreshold(Integer globalThreshold) {
        SimilarityThreshold existing = similarityThresholdMapper.selectOne(
                new LambdaQueryWrapper<SimilarityThreshold>()
                        .orderByDesc(SimilarityThreshold::getId)
                        .last("LIMIT 1"));
        if (existing == null) {
            SimilarityThreshold entity = new SimilarityThreshold();
            entity.setGlobalThreshold(globalThreshold);
            entity.setIsDeleted(0);
            similarityThresholdMapper.insert(entity);
        } else {
            existing.setGlobalThreshold(globalThreshold);
            similarityThresholdMapper.updateById(existing);
        }
    }

    /**
     * 读取学院/专业/学科阈值，并把外键反查成展示名。
     * 名称在这里推导而不是落库，避免学院/学科改名后配置表里留着旧名字。
     */
    private List<SimilarityThresholdVO.CategoryItem> loadCategoryItems() {
        List<CategorySimilarityThreshold> rows = categorySimilarityThresholdMapper.selectList(null);
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, String> collegeNames = new HashMap<>();
        for (College college : collegeMapper.selectList(null)) {
            collegeNames.put(college.getId(), college.getCollegeName());
        }
        Map<Long, String> majorNames = new HashMap<>();
        for (Major major : majorMapper.selectList(null)) {
            majorNames.put(major.getId(), major.getMajorName());
        }

        // 学科名一次取全（而不是每行查一次）：门类要额外标「大类」，
        // 因为同一个 category_type=subject 下既有门类又有二级，只显示名字分不清是哪一级
        // —— 而两者的生效范围差别很大（配在门类上的会继承给它的全部二级学科）。
        Map<Long, Subject> subjects = new HashMap<>();
        for (Subject subject : subjectMapper.selectList(null)) {
            subjects.put(subject.getId(), subject);
        }

        List<SimilarityThresholdVO.CategoryItem> items = new ArrayList<>(rows.size());
        for (CategorySimilarityThreshold row : rows) {
            SimilarityThresholdVO.CategoryItem item = new SimilarityThresholdVO.CategoryItem();
            item.setId(row.getId());
            item.setCategoryType(row.getCategoryType());
            item.setCollegeId(row.getCollegeId());
            item.setMajorId(row.getMajorId());
            item.setSubjectId(row.getSubjectId());
            item.setThreshold(row.getThreshold());
            item.setCategoryName(resolveCategoryName(row, collegeNames, majorNames, subjects));
            items.add(item);
        }
        return items;
    }

    /** 按 categoryType 三路反查展示名 */
    private String resolveCategoryName(CategorySimilarityThreshold row,
                                       Map<Long, String> collegeNames,
                                       Map<Long, String> majorNames,
                                       Map<Long, Subject> subjects) {
        String type = row.getCategoryType();
        if (CategorySimilarityThreshold.TYPE_SUBJECT.equals(type)) {
            Subject s = subjects.get(row.getSubjectId());
            if (s == null) {
                // 学科被删了而配置行还在：把裸 id 显出来，别留空白让人以为页面坏了
                return "学科#" + row.getSubjectId() + "（已不存在）";
            }
            return s.getParentId() == null ? s.getName() + "（大类）" : s.getName();
        }
        if (CategorySimilarityThreshold.TYPE_MAJOR.equals(type)) {
            return majorNames.get(row.getMajorId());
        }
        return collegeNames.get(row.getCollegeId());
    }

    /**
     * 手工校验，不用 @Valid。
     *
     * 不是为了 HTTP 状态码（GlobalExceptionHandler 已有 MethodArgumentNotValidException
     * 处理器，@Valid 失败同样返回 400），而是为了**文案能定位到具体哪一条**：
     * 这是批量提交，字段级 @Valid 只能给出"阈值不能为空"这类不含下标的提示，
     * 用户对着十几行配置无从下手。这里手工校验并抛 BusinessException，
     * 文案统一带「第 N 条配置：」前缀。
     *
     * 注意必须用三参构造器：BusinessException 没有 (ResultCode, String) 重载，
     * 写成两参会命中 (ResultCode, Object)，把文案塞进 extraData 而 message 退回默认值。
     */
    private void validate(SimilarityThresholdUpdateReq req) {
        if (req == null) {
            throw new BusinessException(ResultCode.PARAM_EMPTY, "请求体不能为空", null);
        }

        Integer global = req.getGlobalThreshold();
        if (global == null) {
            throw new BusinessException(ResultCode.PARAM_EMPTY, "全局相似度阈值不能为空", null);
        }
        if (global < 0 || global > 100) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "全局相似度阈值必须在0-100之间", null);
        }

        List<SimilarityThresholdUpdateReq.CategoryItem> items = req.getCategoryThresholds();
        if (items == null || items.isEmpty()) {
            return;
        }

        Set<String> seenKeys = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            // 带上序号，前端报错时能直接定位是哪一条
            String at = "第 " + (i + 1) + " 条配置：";
            SimilarityThresholdUpdateReq.CategoryItem item = items.get(i);
            if (item == null) {
                throw new BusinessException(ResultCode.PARAM_EMPTY, at + "内容不能为空", null);
            }

            String type = item.getCategoryType();
            if (!CategorySimilarityThreshold.TYPE_COLLEGE.equals(type)
                    && !CategorySimilarityThreshold.TYPE_MAJOR.equals(type)
                    && !CategorySimilarityThreshold.TYPE_SUBJECT.equals(type)) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        at + "分类类型必须是 college、major 或 subject", null);
            }

            if (item.getThreshold() == null) {
                throw new BusinessException(ResultCode.PARAM_EMPTY, at + "阈值不能为空", null);
            }
            if (item.getThreshold() < 0 || item.getThreshold() > 100) {
                throw new BusinessException(ResultCode.PARAM_ERROR, at + "阈值必须在0-100之间", null);
            }

            if (CategorySimilarityThreshold.TYPE_COLLEGE.equals(type)) {
                if (item.getCollegeId() == null) {
                    throw new BusinessException(ResultCode.PARAM_EMPTY, at + "学院阈值必须指定学院", null);
                }
            } else if (CategorySimilarityThreshold.TYPE_SUBJECT.equals(type)) {
                // 学科维度必须带 subjectId：这张表没有唯一键，缺了它就没法定位到"哪一行"，
                // 会插出一批 subject_id 全为 NULL 的配置，解析时永远匹配不到、纯垃圾行
                if (item.getSubjectId() == null) {
                    throw new BusinessException(ResultCode.PARAM_EMPTY, at + "学科阈值必须指定学科领域", null);
                }
            } else if (item.getMajorId() == null) {
                throw new BusinessException(ResultCode.PARAM_EMPTY, at + "专业阈值必须指定专业", null);
            }

            // 自然键去重：同一学院/专业/学科只允许一条，否则库里出现重复行、解析时谁生效要看排序。
            // 这张表**没有唯一键**，去重完全靠这里，所以新增维度必须同步拼进 key ——
            // 漏拼的后果是"同一门类下的两个二级学科只能存下一条"。
            String naturalKey = type + ":" + item.getCollegeId() + ":" + item.getMajorId() + ":" + item.getSubjectId();
            if (!seenKeys.add(naturalKey)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, at + "存在重复的学院/专业阈值", null);
            }
        }
        // 说明：这里不校验 collegeId / majorId 是否真实存在。
        // 页面的 id 来自学院/专业下拉，正常路径不会传错；而真传了不存在的 id，
        // 后果只是这条配置永远匹配不到学生、解析时继续向上回退，
        // 不会造成错误判定 —— 不值得为它增加查询和"学院已停用就存不了配置"的副作用。
    }

}
