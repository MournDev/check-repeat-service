package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.ReviewTemplate;
import com.abin.checkrepeatservice.teacher.dto.ReviewTemplateSaveReq;
import com.abin.checkrepeatservice.teacher.mapper.ReviewTemplateMapper;
import com.abin.checkrepeatservice.teacher.service.ReviewTemplateService;
import com.abin.checkrepeatservice.teacher.vo.ReviewTemplateVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审核意见模板服务实现。
 *
 * <p>可见性见接口注释。越权一律抛三参 BusinessException —— 本项目的
 * BusinessException 没有 (ResultCode, String) 构造器，两参写法会命中
 * (ResultCode, Object) 把文案塞进 extraData，message 退回默认值。</p>
 *
 * <p>scenarios 的 JSON 处理方式与 ReviewWorkflowServiceImpl 一致（自建 ObjectMapper、
 * 转换集中在私有方法里），仅在一处刻意不同，见 parseScenarios 的注释。</p>
 */
@Service
@Slf4j
public class ReviewTemplateServiceImpl implements ReviewTemplateService {

    @Autowired
    private ReviewTemplateMapper reviewTemplateMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<ReviewTemplateVO> list() {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        List<ReviewTemplate> rows = reviewTemplateMapper.selectList(
                new LambdaQueryWrapper<ReviewTemplate>()
                        // 「自己的 或 公开的」必须整体括起来：不括的话 or 会把后面的条件
                        // 从 and 链里拆出去，等于没有权限过滤。isDeleted 由 @TableLogic 自动加。
                        .and(w -> w.eq(ReviewTemplate::getCreateBy, currentUserId)
                                .or()
                                .eq(ReviewTemplate::getIsPublic, true))
                        // last_used 为 NULL 的是从未用过的新模板，MySQL 里 NULL 在 DESC 下排在最后，
                        // 再按 id 倒序兜底，保证顺序稳定
                        .orderByDesc(ReviewTemplate::getLastUsed)
                        .orderByDesc(ReviewTemplate::getId));
        return rows.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewTemplateVO create(ReviewTemplateSaveReq req) {
        ReviewTemplate entity = new ReviewTemplate();
        applyRequest(entity, req);
        // 从未使用过：次数给 0、时间留 null。
        // 次数不能留在 null —— 前端 applyTemplate 会做 usageCount += 1，null 会算成 NaN。
        entity.setUsageCount(0);
        reviewTemplateMapper.insert(entity);
        // insert 后审计字段已由 MyMetaObjectHandler 回填到 entity 上，可直接返回
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewTemplateVO update(Long id, ReviewTemplateSaveReq req) {
        ReviewTemplate existing = requireOwned(id);
        applyRequest(existing, req);
        reviewTemplateMapper.updateById(existing);
        return toVO(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireOwned(id);
        // 逻辑删除：@TableLogic 会改写成 UPDATE is_deleted = 1
        reviewTemplateMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewTemplateVO use(Long id) {
        ReviewTemplate row = requireVisible(id);
        // 老数据可能是 NULL，先兜成 0 再加，避免 NPE
        row.setUsageCount((row.getUsageCount() == null ? 0 : row.getUsageCount()) + 1);
        row.setLastUsed(LocalDateTime.now());
        reviewTemplateMapper.updateById(row);
        return toVO(row);
    }

    /**
     * 取一条当前教师可见的模板（自己的或公开的），不可见按"不存在"处理。
     *
     * <p>不可见时给 404 而不是 403：这个页面上本来就看不到别人的私有模板，
     * 回 403 等于确认"这个 id 确实存在"，没有必要泄漏这一点。</p>
     */
    private ReviewTemplate requireVisible(Long id) {
        ReviewTemplate row = reviewTemplateMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "模板不存在或已删除", null);
        }
        boolean owned = UserBusinessInfoUtils.getCurrentUserId().equals(row.getCreateBy());
        if (!owned && !Boolean.TRUE.equals(row.getIsPublic())) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "模板不存在或已删除", null);
        }
        return row;
    }

    /**
     * 取一条本人创建的模板。别人的模板即使公开也不可改、不可删 ——
     * 公开的语义是"可查看可用"，不是"可编辑"。这里给 403 而不是 404：
     * 公开模板在这个页面上本来就看得到，装作不存在反而让人以为页面坏了。
     */
    private ReviewTemplate requireOwned(Long id) {
        ReviewTemplate row = requireVisible(id);
        if (!UserBusinessInfoUtils.getCurrentUserId().equals(row.getCreateBy())) {
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS,
                    "只能修改或删除自己创建的模板", null);
        }
        return row;
    }

    /** 把请求里的可写字段搬到实体上（新增与更新共用） */
    private void applyRequest(ReviewTemplate entity, ReviewTemplateSaveReq req) {
        entity.setTitle(req.getTitle().trim());
        entity.setCategory(trimToNull(req.getCategory()));
        entity.setContent(req.getContent());
        entity.setScenarios(serializeScenarios(normalizeScenarios(req.getScenarios())));
        entity.setIsPublic(Boolean.TRUE.equals(req.getIsPublic()));
    }

    private ReviewTemplateVO toVO(ReviewTemplate entity) {
        ReviewTemplateVO vo = new ReviewTemplateVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setCategory(entity.getCategory());
        vo.setContent(entity.getContent());
        vo.setScenarios(parseScenarios(entity.getScenarios()));
        vo.setIsPublic(Boolean.TRUE.equals(entity.getIsPublic()));
        vo.setUsageCount(entity.getUsageCount() == null ? 0 : entity.getUsageCount());
        vo.setLastUsed(entity.getLastUsed());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 反序列化 scenarios JSON 列。
     *
     * <p>与 ReviewWorkflowServiceImpl.parseSteps 刻意不同：那里解析失败是抛错的
     * （单行配置，静默吞掉会让下次保存把整份配置覆盖成空）。这里返回空列表，
     * 因为这是**列表**页 —— 一条脏数据抛异常会让整页都显示不出模板来，
     * 代价远大于"这一条的标签显示为空"。标签是三个复选框、在编辑弹窗里肉眼可见，
     * 不存在"没注意就被清掉"的隐蔽性。</p>
     */
    private List<String> parseScenarios(String scenariosJson) {
        if (!StringUtils.hasText(scenariosJson)) {
            return new ArrayList<>();
        }
        try {
            List<String> scenarios = objectMapper.readValue(
                    scenariosJson, new TypeReference<List<String>>() {
                    });
            return scenarios == null ? new ArrayList<>() : scenarios;
        } catch (Exception e) {
            log.error("审核模板使用场景无法解析，原始值={}", scenariosJson, e);
            return new ArrayList<>();
        }
    }

    private String serializeScenarios(List<String> scenarios) {
        try {
            return objectMapper.writeValueAsString(scenarios);
        } catch (Exception e) {
            // 元素全是 String，正常不可能失败；真失败说明对象被塞进了意外类型
            log.error("审核模板使用场景序列化失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "模板使用场景保存失败", null);
        }
    }

    /** 去空、去重。前端复选框本不会给重复项，但库里存重复值只会让展示变得难解释 */
    private List<String> normalizeScenarios(List<String> scenarios) {
        if (scenarios == null) {
            return new ArrayList<>();
        }
        return scenarios.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

}
