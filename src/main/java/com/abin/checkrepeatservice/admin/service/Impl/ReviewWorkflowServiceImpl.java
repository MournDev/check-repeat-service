package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.ReviewWorkflowUpdateReq;
import com.abin.checkrepeatservice.admin.service.ReviewWorkflowService;
import com.abin.checkrepeatservice.admin.vo.ReviewWorkflowVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.mapper.ReviewWorkflowMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.ReviewWorkflow;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 审核工作流配置服务实现。
 *
 * 单行语义：review_workflow 在语义上是单行配置表，但结构上没有唯一约束，
 * 所以"取哪一行"必须读写一致 —— 统一按 id 倒序取第一条，读到就更新、读不到才插入。
 * 这样写入路径永远落在同一行上，不会越插越多，也不会出现"存了一行、读的是另一行"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewWorkflowServiceImpl implements ReviewWorkflowService {

    private final ReviewWorkflowMapper reviewWorkflowMapper;

    private final SysUserMapper sysUserMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 与前端表单的 maxlength / input-number 上限保持一致 */
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 200;
    private static final int MAX_STEP_NAME_LENGTH = 30;
    private static final int MAX_TIMEOUT_DAYS = 30;

    private static final String APPROVAL_ALL = "all";
    private static final String APPROVAL_ANY = "any";

    @Override
    public ReviewWorkflowVO getWorkflow() {
        ReviewWorkflow row = findWorkflow();

        ReviewWorkflowVO vo = new ReviewWorkflowVO();
        if (row == null) {
            // 从未配置过：给一套空配置，让页面正常渲染空白表单。
            // 这里不返回 404 —— "还没配过"是正常状态，不是错误。
            vo.setName("");
            vo.setDescription("");
            vo.setSteps(new ArrayList<>());
            vo.setEnabled(true);
            return vo;
        }

        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setDescription(row.getDescription());
        vo.setSteps(parseSteps(row.getSteps()));
        // 列允许为 NULL，语义上当"未设置"处理：默认启用，
        // 与插入时的默认值保持一致，避免页面显示成禁用又解释不清。
        vo.setEnabled(row.getEnabled() == null || row.getEnabled());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWorkflow(ReviewWorkflowUpdateReq req) {
        validate(req);

        List<ReviewWorkflowUpdateReq.Step> steps = normalizeSteps(req.getSteps());
        String stepsJson = serializeSteps(steps);
        Boolean enabled = req.getEnabled() == null || req.getEnabled();

        ReviewWorkflow existing = findWorkflow();
        if (existing == null) {
            ReviewWorkflow entity = new ReviewWorkflow();
            entity.setName(req.getName().trim());
            entity.setDescription(req.getDescription());
            entity.setSteps(stepsJson);
            entity.setEnabled(enabled);
            // DDL 默认值不足恃：BaseEntity.isDeleted 只有 @TableLogic、没有 fill 注解，
            // MyMetaObjectHandler 的 strictInsertFill 不会补这一列，落 NULL 的话
            // 之后所有查询都会拼上 is_deleted = 0，这一行就再也读不回来了。
            entity.setIsDeleted(0);
            reviewWorkflowMapper.insert(entity);
        } else {
            existing.setName(req.getName().trim());
            existing.setDescription(req.getDescription());
            existing.setSteps(stepsJson);
            existing.setEnabled(enabled);
            reviewWorkflowMapper.updateById(existing);
        }

        log.info("审核工作流配置已更新：名称={}，步骤数={}，启用={}",
                req.getName(), steps.size(), enabled);
    }

    @Override
    public List<ReviewWorkflowVO.TeacherOption> listTeacherOptions() {
        // 按 user_type 筛而不是 join sys_role：这是本项目查教师列表的既有写法
        // （AdminSchoolController / AdminDashboardServiceImpl 同款），且 user_type
        // 与 role_code 在库里是一致的，没必要为此多一次关联查询。
        // 只 select 用得到的列，顺带避开本表那些页面根本不用的宽字段。
        List<SysUser> teachers = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getId, SysUser::getRealName, SysUser::getUsername)
                        .eq(SysUser::getUserType, "TEACHER")
                        .eq(SysUser::getStatus, 1)
                        .orderByAsc(SysUser::getId));

        List<ReviewWorkflowVO.TeacherOption> options = new ArrayList<>();
        if (teachers == null) {
            return options;
        }
        for (SysUser teacher : teachers) {
            ReviewWorkflowVO.TeacherOption option = new ReviewWorkflowVO.TeacherOption();
            option.setId(teacher.getId());
            // real_name 允许为空，回落到 username，免得下拉里出现选不中的空白项
            option.setName(StringUtils.hasText(teacher.getRealName())
                    ? teacher.getRealName()
                    : teacher.getUsername());
            options.add(option);
        }
        return options;
    }

    /**
     * 定位唯一的配置行：按 id 倒序取第一条。
     * 读写两侧都走这里，保证落到同一行上（理由见类注释）。
     */
    private ReviewWorkflow findWorkflow() {
        return reviewWorkflowMapper.selectOne(
                new LambdaQueryWrapper<ReviewWorkflow>()
                        .orderByDesc(ReviewWorkflow::getId)
                        .last("LIMIT 1"));
    }

    /**
     * 反序列化 steps JSON 列。
     *
     * 解析失败时抛错而不是静默返回空数组：MySQL 能保证这列是合法 JSON，
     * 但保证不了结构是步骤数组；一旦结构对不上，静默吞掉会让用户下一次保存时
     * 把原有配置覆盖成空，属于"看起来没报错、实际丢数据"的那类故障。
     */
    private List<ReviewWorkflowVO.Step> parseSteps(String stepsJson) {
        if (!StringUtils.hasText(stepsJson)) {
            return new ArrayList<>();
        }
        try {
            List<ReviewWorkflowVO.Step> steps =
                    objectMapper.readValue(stepsJson, new TypeReference<List<ReviewWorkflowVO.Step>>() {
                    });
            return steps == null ? new ArrayList<>() : steps;
        } catch (Exception e) {
            log.error("审核工作流步骤配置无法解析，原始值={}", stepsJson, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "工作流步骤配置已损坏，无法解析", null);
        }
    }

    private String serializeSteps(List<ReviewWorkflowUpdateReq.Step> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (Exception e) {
            // 步骤里全是 String / Long / Integer，正常不可能序列化失败；
            // 真失败了说明对象被塞进了意外类型，不能把坏数据写进库。
            log.error("审核工作流步骤配置序列化失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "工作流步骤配置保存失败", null);
        }
    }

    /**
     * 把可空字段补成确定值，并做去重。
     * 去重是顺手做的清理：前端 el-select multiple 本就不会给出重复项，
     * 但库里存着重复审批人只会让"全部审批/任意审批"的语义变得难解释。
     */
    private List<ReviewWorkflowUpdateReq.Step> normalizeSteps(List<ReviewWorkflowUpdateReq.Step> steps) {
        List<ReviewWorkflowUpdateReq.Step> normalized = new ArrayList<>();
        if (steps == null) {
            return normalized;
        }
        for (ReviewWorkflowUpdateReq.Step step : steps) {
            List<String> approvers = step.getApprovers() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(new LinkedHashSet<>(step.getApprovers()));
            step.setApprovers(approvers);
            // 审批方式缺省按"全部审批"：这是更严格的一侧，
            // 宁可多要一个人点头，也不因为字段缺失把审核悄悄放宽成"任一人通过即可"。
            step.setApprovalType(StringUtils.hasText(step.getApprovalType())
                    ? step.getApprovalType()
                    : APPROVAL_ALL);
            // 超时缺省 0 = 不启用超时（与前端 input-number 的 min=0 对齐）
            if (step.getTimeout() == null) {
                step.setTimeout(0);
            }
            if (StringUtils.hasText(step.getName())) {
                step.setName(step.getName().trim());
            }
            normalized.add(step);
        }
        return normalized;
    }

    /**
     * 手工校验，不用 @Valid。
     *
     * GlobalExceptionHandler 里没有 MethodArgumentNotValidException 处理器，
     * @Valid 失败会落到 Exception 兜底变成 HTTP 500「系统繁忙」，
     * 把"名称没填"这类参数错误误导成服务故障。这里手工校验并抛 BusinessException，
     * 由 GlobalExceptionHandler 返回正确的 400 + 自定义文案。
     *
     * 注意必须用三参构造器：BusinessException 没有 (ResultCode, String) 重载，
     * 写成两参会命中 (ResultCode, Object)，把文案塞进 extraData 而 message 退回默认值。
     *
     * 不校验审批人 id 是否真实存在（与 SimilarityThresholdServiceImpl 同样的取舍）：
     * id 来自页面下拉，正常路径不会传错；真传了不存在的 id，后果只是那个审批人
     * 永远匹配不到人，不会造成错误放行 —— 不值得为此增加一次全表查询，
     * 以及"教师被删除/停用后旧配置就存不回去"的副作用。
     */
    private void validate(ReviewWorkflowUpdateReq req) {
        if (req == null) {
            throw new BusinessException(ResultCode.PARAM_EMPTY, "请求体不能为空", null);
        }

        if (!StringUtils.hasText(req.getName())) {
            throw new BusinessException(ResultCode.PARAM_EMPTY, "工作流名称不能为空", null);
        }
        if (req.getName().trim().length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "工作流名称不能超过" + MAX_NAME_LENGTH + "个字符", null);
        }

        if (req.getDescription() != null && req.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "工作流描述不能超过" + MAX_DESCRIPTION_LENGTH + "个字符", null);
        }

        List<ReviewWorkflowUpdateReq.Step> steps = req.getSteps();
        if (steps == null || steps.isEmpty()) {
            // 允许没有步骤：先建好工作流、之后再逐步配置步骤是合理用法。
            // 是否"启用却没有步骤"由使用侧决定，这里不做业务拦截。
            return;
        }

        for (int i = 0; i < steps.size(); i++) {
            // 带上序号，前端报错时能直接定位是哪一步
            String at = "第 " + (i + 1) + " 个审核步骤：";
            ReviewWorkflowUpdateReq.Step step = steps.get(i);
            if (step == null) {
                throw new BusinessException(ResultCode.PARAM_EMPTY, at + "内容不能为空", null);
            }

            if (!StringUtils.hasText(step.getName())) {
                throw new BusinessException(ResultCode.PARAM_EMPTY, at + "步骤名称不能为空", null);
            }
            if (step.getName().trim().length() > MAX_STEP_NAME_LENGTH) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        at + "步骤名称不能超过" + MAX_STEP_NAME_LENGTH + "个字符", null);
            }

            String approvalType = step.getApprovalType();
            if (StringUtils.hasText(approvalType)
                    && !APPROVAL_ALL.equals(approvalType)
                    && !APPROVAL_ANY.equals(approvalType)) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        at + "审批方式只能是 all 或 any", null);
            }

            Integer timeout = step.getTimeout();
            if (timeout != null && (timeout < 0 || timeout > MAX_TIMEOUT_DAYS)) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        at + "超时时间必须在0-" + MAX_TIMEOUT_DAYS + "天之间", null);
            }

            List<String> approvers = step.getApprovers();
            if (approvers != null) {
                for (int j = 0; j < approvers.size(); j++) {
                    if (!StringUtils.hasText(approvers.get(j))) {
                        throw new BusinessException(ResultCode.PARAM_ERROR,
                                at + "第 " + (j + 1) + " 个审批人无效", null);
                    }
                }
            }
        }
    }

}
