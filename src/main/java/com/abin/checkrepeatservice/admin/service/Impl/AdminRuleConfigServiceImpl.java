package com.abin.checkrepeatservice.admin.service.Impl;

import com.alibaba.fastjson2.JSON;
import com.abin.checkrepeatservice.admin.vo.CheckRuleOperateReq;
import com.abin.checkrepeatservice.admin.vo.CompareLibOperateReq;
import com.abin.checkrepeatservice.admin.dto.RuleLibRelationDTO;
import com.abin.checkrepeatservice.admin.vo.SystemParamReq;
import com.abin.checkrepeatservice.admin.mapper.CompareLibMapper;
import com.abin.checkrepeatservice.admin.mapper.RuleLibRelationMapper;
import com.abin.checkrepeatservice.admin.mapper.SystemParamMapper;
import com.abin.checkrepeatservice.admin.service.AdminRuleConfigService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.SimilarityThresholdResolver;
import com.abin.checkrepeatservice.pojo.entity.*;
import com.abin.checkrepeatservice.student.mapper.CheckRuleMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员规则配置服务实现类
 */
@Slf4j
@Service
public class AdminRuleConfigServiceImpl extends ServiceImpl<CheckRuleMapper, CheckRule> implements AdminRuleConfigService {

    @Resource
    private CompareLibMapper compareLibMapper;

    @Resource
    private RuleLibRelationMapper ruleLibRelationMapper;

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private SystemParamMapper systemParamMapper;

    // 合格线真值源（专业 > 学院 > 全局 > yml 兜底）。
    // 原先这里注入的是 admin.check-rule.default-threshold 常量，写进 check_rule.pass_threshold
    // 后既不参与判定、编辑分支也不更新，留着会让"通过阈值"看着像能配置的合格线。
    @Resource
    private SimilarityThresholdResolver similarityThresholdResolver;

    // 默认配置（从配置文件获取）
    @Value("${admin.check-rule.default-interval}")
    private Integer defaultInterval;
    @Value("${admin.check-rule.default-max-count}")
    private Integer defaultMaxCount;
    @Value("${admin.compare-lib.default-enabled}")
    private Integer defaultLibEnabled;
    @Value("${admin.system-param.max-paper-size}")
    private Long defaultMaxPaperSize;
    @Value("${admin.system-param.max-concurrent-check}")
    private Integer defaultMaxConcurrent;
    @Value("${admin.jwt-expiration}")
    private Long defaultJwtExpiration;

    // ========================== 查重规则管理 ==========================
    @Override
    public Result<Page<CheckRule>> getCheckRuleList(@RequestParam(value = "ruleName", required = false) String ruleName,
                                                        @RequestParam(value = "isDefault", required = false) Integer isDefault,
                                                        @RequestParam("currentPage") Integer currentPage,
                                                        @RequestParam("pageSize") Integer pageSize) {
        // 构建分页查询条件（仅查询未删除的规则）
        Page<CheckRule> rulePage = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<CheckRule> ruleWrapper = new LambdaQueryWrapper<>();
        ruleWrapper.eq(CheckRule::getIsDeleted, 0);

        // 模糊查询条件
        if (StringUtils.hasText(ruleName)) {
            ruleWrapper.like(CheckRule::getRuleName, ruleName);
        }
        // 是否默认规则过滤
        if (isDefault != null && Arrays.asList(0, 1).contains(isDefault)) {
            ruleWrapper.eq(CheckRule::getIsDefault, isDefault);
        }

        // 按创建时间倒序。create_time 只到秒，同一秒内创建的多条规则谁在前由数据库定，
        // 翻页时会出现"某条规则在第 1 页和第 2 页各出现一次"这类错乱，故再用 id 兜底排序
        ruleWrapper.orderByDesc(CheckRule::getCreateTime)
                .orderByDesc(CheckRule::getId);

        // selectPage 会把记录与总数回填进传入的 page 对象，直接返回它即可
        Page<CheckRule> pageInfo = baseMapper.selectPage(rulePage, ruleWrapper);

        return Result.success("查重规则列表查询成功", pageInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> saveOrUpdateCheckRule(CheckRuleOperateReq operateReq) {
        Long ruleId = operateReq.getRuleId();
        String ruleCode = operateReq.getRuleCode();
        Integer isDefault = operateReq.getIsDefault();
        List<Long> libIds = operateReq.getLibIds();

        // 1. 基础校验
        // 1.1 校验规则编码唯一性（新增或编辑时编码变更需校验）
        LambdaQueryWrapper<CheckRule> codeWrapper = new LambdaQueryWrapper<>();
        codeWrapper.eq(CheckRule::getRuleCode, ruleCode)
                .eq(CheckRule::getIsDeleted, 0);
        if (ruleId != null) {
            codeWrapper.ne(CheckRule::getId, ruleId); // 编辑时排除自身
        }
        if (baseMapper.selectCount(codeWrapper) > 0) {
            return Result.error(ResultCode.PARAM_ERROR, "规则编码已存在，请修改后重试");
        }

        // 1.2 校验默认规则（仅允许一条默认规则）
        if (isDefault == 1) {
            LambdaQueryWrapper<CheckRule> defaultWrapper = new LambdaQueryWrapper<>();
            defaultWrapper.eq(CheckRule::getIsDefault, 1)
                    .eq(CheckRule::getIsDeleted, 0);
            if (ruleId != null) {
                defaultWrapper.ne(CheckRule::getId, ruleId);
            }
            if (baseMapper.selectCount(defaultWrapper) > 0) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "已存在默认规则，仅允许一条默认规则");
            }
        }

        // 1.3 校验关联的比对库是否存在且启用
        List<CompareLib> libList = compareLibMapper.selectBatchIds(libIds);
        if (libList.size() != libIds.size()) {
            // 找出不存在的库ID
            Set<Long> existLibIds = libList.stream().map(CompareLib::getId).collect(Collectors.toSet());
            List<Long> notExistIds = libIds.stream().filter(id -> !existLibIds.contains(id)).collect(Collectors.toList());
            return Result.error(ResultCode.PARAM_ERROR,
                    String.format("关联的比对库不存在：%s", String.join(",", notExistIds.stream().map(String::valueOf).collect(Collectors.toList()))));
        }
        // 校验关联的库是否启用（仅启用的库可关联）
        List<Long> disabledLibIds = libList.stream()
                .filter(lib -> lib.getIsEnabled() == 0)
                .map(CompareLib::getId)
                .collect(Collectors.toList());
        if (!disabledLibIds.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR,
                    String.format("关联的比对库已禁用：%s，请启用后再关联", String.join(",", disabledLibIds.stream().map(String::valueOf).collect(Collectors.toList()))));
        }

        // 2. 构建规则实体
        CheckRule checkRule = new CheckRule();
        if (ruleId != null) {
            // 编辑：查询原规则
            checkRule = baseMapper.selectById(ruleId);
            if (checkRule == null || checkRule.getIsDeleted() == 1) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "待编辑的规则不存在或已删除");
            }
        } else {
            // 新增：设置默认值。
            // pass_threshold 不参与合格判定，这里只镜像当前真正生效的全局阈值，
            // 让旧接口的返回值不至于和「相似度阈值设置」页显示的数字打架。
            checkRule.setPassThreshold(BigDecimal.valueOf(similarityThresholdResolver.getEffectiveGlobalThreshold()));
            checkRule.setCheckInterval(operateReq.getCheckInterval() != null ? operateReq.getCheckInterval() : defaultInterval);
            checkRule.setMaxCheckCount(operateReq.getMaxCheckCount() != null ? operateReq.getMaxCheckCount() : defaultMaxCount);
            checkRule.setIsDefault(operateReq.getIsDefault() != null ? operateReq.getIsDefault() : 0);
        }
        // 公共字段赋值
        checkRule.setRuleName(operateReq.getRuleName());
        checkRule.setRuleCode(ruleCode);
        checkRule.setDescription(operateReq.getDescription());
        // compare_lib 是 NOT NULL 的历史列，存库名 JSON（如 ["校内论文库","知网期刊库"]）。
        // 真正的关系在 rule_lib_relation（下面第 4 步写），全代码库没有任何地方读这个列，
        // 但列本身没默认值：新增时不给它赋值，MyBatis-Plus 会按 NOT_NULL 策略把整列从
        // INSERT 里省掉，MySQL 严格模式直接报 1364 "Field 'compare_lib' doesn't have
        // a default value" —— 管理端因此一条规则都建不出来。
        checkRule.setCompareLib(JSON.toJSONString(
                libList.stream().map(CompareLib::getLibName).collect(Collectors.toList())));
        // 填充审计字段
        UserBusinessInfoUtils.setAuditField(checkRule, ruleId == null);

        // 3. 保存规则（新增/编辑）
        boolean saveSuccess = ruleId == null ? baseMapper.insert(checkRule) > 0 : baseMapper.updateById(checkRule) > 0;
        if (!saveSuccess) {
            return Result.error(ResultCode.SYSTEM_ERROR, ruleId == null ? "规则新增失败" : "规则编辑失败");
        }
        Long finalRuleId = ruleId == null ? checkRule.getId() : ruleId;

        // 4. 维护规则与比对库的关联关系（先删后加，确保关联关系最新）
        // 4.1 删除原有关联
        LambdaQueryWrapper<RuleLibRelation> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(RuleLibRelation::getRuleId, finalRuleId);
        ruleLibRelationMapper.delete(relationWrapper);

        // 4.2 新增关联
        List<RuleLibRelation> relationList = libIds.stream().map(libId -> {
            RuleLibRelation relation = new RuleLibRelation();
            relation.setRuleId(finalRuleId);
            relation.setLibId(libId);
            UserBusinessInfoUtils.setAuditField(relation, true);
            return relation;
        }).collect(Collectors.toList());
        if (!relationList.isEmpty()) {
            ruleLibRelationMapper.batchInsert(relationList); // 需自定义批量插入方法
        }

        // 5. 构建返回结果
        Map<String, Object> resultMap = new HashMap<>(2);
        if (ruleId == null) {
            resultMap.put("ruleId", finalRuleId);
            resultMap.put("message", "规则新增成功");
        } else {
            resultMap.put("success", true);
            resultMap.put("message", "规则编辑成功");
        }

        return Result.success(ruleId == null ? "规则新增成功" : "规则编辑成功", resultMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> deleteCheckRule(@RequestParam("ruleId") Long ruleId) {
        // 1. 校验规则存在性
        CheckRule checkRule = baseMapper.selectById(ruleId);
        if (checkRule == null || checkRule.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "待删除的规则不存在或已删除");
        }

        // 2. 校验规则是否已关联查重任务（已关联则不允许删除）
//        LambdaQueryWrapper<CheckTask> taskWrapper = new LambdaQueryWrapper<>();
//        taskWrapper.eq(CheckTask::getCheckRuleId, ruleId)
//                .eq(CheckTask::getIsDeleted, 0);
//        if (checkTaskMapper.selectCount(taskWrapper) > 0) {
//            return Result.error(ResultCode.PERMISSION_NOT_STATUS, "该规则已关联查重任务，无法删除");
//        }

        // 3. 软删除规则
        checkRule.setIsDeleted(1);
        UserBusinessInfoUtils.setAuditField(checkRule, false);
        if (baseMapper.updateById(checkRule) <= 0) {
            return Result.error(ResultCode.SYSTEM_ERROR, "规则删除失败");
        }

        // 4. 删除关联的比对库关系
        LambdaQueryWrapper<RuleLibRelation> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(RuleLibRelation::getRuleId, ruleId);
        ruleLibRelationMapper.delete(relationWrapper);

        log.info("管理员删除查重规则成功：规则ID={}，规则名称={}", ruleId, checkRule.getRuleName());
        return Result.success("规则删除成功");
    }

    @Override
    public Result<RuleLibRelationDTO> getRuleRelatedLibs(@RequestParam("ruleId") Long ruleId) {
        // 1. 校验规则存在性
        CheckRule checkRule = baseMapper.selectById(ruleId);
        if (checkRule == null || checkRule.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "规则不存在或已删除");
        }

        // 2. 查询关联的比对库ID
        LambdaQueryWrapper<RuleLibRelation> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(RuleLibRelation::getRuleId, ruleId);
        List<RuleLibRelation> relationList = ruleLibRelationMapper.selectList(relationWrapper);
        if (CollectionUtils.isEmpty(relationList)) {
            return Result.success("规则未关联任何比对库", new RuleLibRelationDTO());
        }

        // 3. 批量查询比对库信息
        List<Long> libIds = relationList.stream().map(RuleLibRelation::getLibId).collect(Collectors.toList());
        List<CompareLib> libList = compareLibMapper.selectBatchIds(libIds);

        // 4. 转换为DTO
        RuleLibRelationDTO dto = new RuleLibRelationDTO();
        // 4.1 规则基础信息
        RuleLibRelationDTO.CheckRuleBaseDTO ruleBaseDTO = new RuleLibRelationDTO.CheckRuleBaseDTO();
        ruleBaseDTO.setRuleId(checkRule.getId());
        ruleBaseDTO.setRuleName(checkRule.getRuleName());
        ruleBaseDTO.setRuleCode(checkRule.getRuleCode());
        ruleBaseDTO.setPassThreshold(checkRule.getPassThreshold());
        ruleBaseDTO.setIsDefault(checkRule.getIsDefault());
        ruleBaseDTO.setDescription(checkRule.getDescription());
        dto.setRuleBase(ruleBaseDTO);

        // 4.2 关联的比对库信息
        List<RuleLibRelationDTO.CompareLibBaseDTO> libDTOList = libList.stream().map(lib -> {
            RuleLibRelationDTO.CompareLibBaseDTO libDTO = new RuleLibRelationDTO.CompareLibBaseDTO();
            libDTO.setLibId(lib.getId());
            libDTO.setLibName(lib.getLibName());
            libDTO.setLibCode(lib.getLibCode());
            libDTO.setLibType(lib.getLibType());
            libDTO.setIsEnabled(lib.getIsEnabled());
            return libDTO;
        }).collect(Collectors.toList());
        dto.setRelatedLibs(libDTOList);

        return Result.success("规则关联比对库查询成功", dto);
    }

    // ========================== 比对库管理 ==========================
    @Override
    public Result<Page<CompareLib>> getCompareLibList(@RequestParam(value = "libName", required = false) String libName,
                                                          @RequestParam(value = "libType", required = false) String libType,
                                                          @RequestParam(value = "isEnabled", required = false) Integer isEnabled,
                                                          @RequestParam("currentPage") Integer currentPage,
                                                          @RequestParam("pageSize") Integer pageSize) {
        // 构建分页查询条件（仅查询未删除的库）
        Page<CompareLib> libPage = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<CompareLib> libWrapper = new LambdaQueryWrapper<>();
        libWrapper.eq(CompareLib::getIsDeleted, 0);

        // 过滤条件
        if (StringUtils.hasText(libName)) {
            libWrapper.like(CompareLib::getLibName, libName);
        }
        if (StringUtils.hasText(libType) && Arrays.asList("LOCAL", "REMOTE").contains(libType)) {
            libWrapper.eq(CompareLib::getLibType, libType);
        }
        if (isEnabled != null && Arrays.asList(0, 1).contains(isEnabled)) {
            libWrapper.eq(CompareLib::getIsEnabled, isEnabled);
        }

        // 排序理由同 getCheckRuleList：create_time 精度到秒，需 id 兜底保证翻页稳定
        libWrapper.orderByDesc(CompareLib::getCreateTime)
                .orderByDesc(CompareLib::getId);

        // selectPage 会把记录与总数回填进传入的 page 对象，直接返回它即可
        Page<CompareLib> pageInfo = compareLibMapper.selectPage(libPage, libWrapper);

        return Result.success("比对库列表查询成功", pageInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> saveOrUpdateCompareLib(CompareLibOperateReq operateReq) {
        Long libId = operateReq.getLibId();
        String libCode = operateReq.getLibCode();
        String libType = operateReq.getLibType();
        String libUrl = operateReq.getLibUrl();

        // 1. 基础校验
        // 1.1 校验库编码唯一性
        LambdaQueryWrapper<CompareLib> codeWrapper = new LambdaQueryWrapper<>();
        codeWrapper.eq(CompareLib::getLibCode, libCode)
                .eq(CompareLib::getIsDeleted, 0);
        if (libId != null) {
            codeWrapper.ne(CompareLib::getId, libId);
        }
        if (compareLibMapper.selectCount(codeWrapper) > 0) {
            return Result.error(ResultCode.PARAM_ERROR, "比对库编码已存在，请修改后重试");
        }

        // 1.2 校验库类型与地址合法性
        if (!Arrays.asList("LOCAL", "REMOTE").contains(libType)) {
            return Result.error(ResultCode.PARAM_ERROR, "比对库类型无效（仅支持LOCAL-本地库、REMOTE-远程库）");
        }
        // 本地库校验路径存在性
        if ("LOCAL".equals(libType)) {
            if (!Files.exists(Paths.get(libUrl))) {
                return Result.error(ResultCode.PARAM_ERROR, "本地比对库路径不存在：" + libUrl);
            }
            if (!Files.isDirectory(Paths.get(libUrl))) {
                return Result.error(ResultCode.PARAM_ERROR, "本地比对库路径不是有效目录：" + libUrl);
            }
        }
        // 远程库校验URL格式（简化校验）
        if ("REMOTE".equals(libType) && !(libUrl.startsWith("http://") || libUrl.startsWith("https://"))) {
            return Result.error(ResultCode.PARAM_ERROR, "远程比对库URL格式无效（需以http://或https://开头）");
        }

        // 2. 构建比对库实体
        CompareLib compareLib = new CompareLib();
        if (libId != null) {
            // 编辑：查询原库
            compareLib = compareLibMapper.selectById(libId);
            if (compareLib == null || compareLib.getIsDeleted() == 1) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "待编辑的比对库不存在或已删除");
            }
        } else {
            // 新增：设置默认值
            compareLib.setIsEnabled(operateReq.getIsEnabled() != null ? operateReq.getIsEnabled() : defaultLibEnabled);
        }
        // 公共字段赋值
        compareLib.setLibName(operateReq.getLibName());
        compareLib.setLibCode(libCode);
        compareLib.setLibType(libType);
        compareLib.setLibUrl(libUrl);
        compareLib.setDescription(operateReq.getDescription());
        // 填充审计字段
        UserBusinessInfoUtils.setAuditField(compareLib, libId == null);

        // 3. 保存比对库（新增/编辑）
        boolean saveSuccess = libId == null
                ? compareLibMapper.insert(compareLib) > 0
                : compareLibMapper.updateById(compareLib) > 0;
        if (!saveSuccess) {
            return Result.error(ResultCode.SYSTEM_ERROR, libId == null ? "比对库新增失败" : "比对库编辑失败");
        }

        // 4. 构建返回结果
        Map<String, Object> resultMap = new HashMap<>(2);
        if (libId == null) {
            resultMap.put("libId", String.valueOf(compareLib.getId()));
            resultMap.put("message", "比对库新增成功");
        } else {
            resultMap.put("success", true);
            resultMap.put("message", "比对库编辑成功");
        }

        return Result.success(libId == null ? "比对库新增成功" : "比对库编辑成功", resultMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> toggleLibEnabled(@RequestParam("libId") Long libId,
                                           @RequestParam("isEnabled") Integer isEnabled) {
        // 1. 校验参数与库存在性
        if (!Arrays.asList(0, 1).contains(isEnabled)) {
            return Result.error(ResultCode.PARAM_ERROR, "是否启用参数无效（仅支持0-禁用、1-启用）");
        }
        CompareLib compareLib = compareLibMapper.selectById(libId);
        if (compareLib == null || compareLib.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "比对库不存在或已删除");
        }
        // 若状态无变更，直接返回成功
        if (compareLib.getIsEnabled().equals(isEnabled)) {
            return Result.success("比对库状态无需变更（当前已" + (isEnabled == 1 ? "启用" : "禁用") + "）");
        }

        // 2. 禁用校验：若库已关联规则，提示风险
        if (isEnabled == 0) {
            LambdaQueryWrapper<RuleLibRelation> relationWrapper = new LambdaQueryWrapper<>();
            relationWrapper.eq(RuleLibRelation::getLibId, libId);
            if (ruleLibRelationMapper.selectCount(relationWrapper) > 0) {
                return Result.error(ResultCode.BUSINESS_NO_SAFE, "该比对库已关联查重规则，禁用后将影响关联规则的查重结果，是否继续？（需前端二次确认）");
            }
        }

        // 3. 更新启用状态
        compareLib.setIsEnabled(isEnabled);
        UserBusinessInfoUtils.setAuditField(compareLib, false);
        if (compareLibMapper.updateById(compareLib) <= 0) {
            return Result.error(ResultCode.SYSTEM_ERROR, "比对库" + (isEnabled == 1 ? "启用" : "禁用") + "失败");
        }

        log.info("管理员{}比对库成功：库ID={}，库名称={}",
                isEnabled == 1 ? "启用" : "禁用", libId, compareLib.getLibName());
        return Result.success("比对库" + (isEnabled == 1 ? "启用" : "禁用") + "成功");
    }

    // ========================== 系统参数配置 ==========================
    @Override
    public Result<SystemParam> getCurrentSystemParam() {
        // 查询系统参数（仅一条记录）
        SystemParam systemParam = systemParamMapper.selectOne(
                new LambdaQueryWrapper<SystemParam>()
                        .eq(SystemParam::getIsDeleted, 0)
                        .last("LIMIT 1")
        );
        if (systemParam == null) {
            // 若不存在，返回默认配置（并初始化到数据库）
            systemParam = initDefaultSystemParam();
        }
        return Result.success("系统参数查询成功", systemParam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateSystemParam(SystemParamReq paramReq) {
        // 1. 查询当前系统参数（仅一条记录）
        SystemParam systemParam = systemParamMapper.selectOne(
                new LambdaQueryWrapper<SystemParam>()
                        .eq(SystemParam::getIsDeleted, 0)
                        .last("LIMIT 1")
        );
        if (systemParam == null) {
            // 不存在则新增
            systemParam = new SystemParam();
            systemParam.setMaxPaperSize(paramReq.getMaxPaperSize());
            systemParam.setMaxConcurrentCheck(paramReq.getMaxConcurrentCheck());
            systemParam.setJwtExpiration(paramReq.getJwtExpiration());
            systemParam.setIsDeleted(0);
            UserBusinessInfoUtils.setAuditField(systemParam, true);
            systemParamMapper.insert(systemParam);
        } else {
            // 存在则更新
            systemParam.setMaxPaperSize(paramReq.getMaxPaperSize());
            systemParam.setMaxConcurrentCheck(paramReq.getMaxConcurrentCheck());
            systemParam.setJwtExpiration(paramReq.getJwtExpiration());
            UserBusinessInfoUtils.setAuditField(systemParam, false);
            systemParamMapper.updateById(systemParam);
        }

        log.info("管理员更新系统参数成功：最大论文大小={}MB，最大并发查重数={}，JWT有效期={}小时",
                paramReq.getMaxPaperSize() / 1024 / 1024,
                paramReq.getMaxConcurrentCheck(),
                paramReq.getJwtExpiration() / 1000 / 3600);
        return Result.success("系统参数更新成功");
    }

    // ------------------------------ 私有辅助方法 ------------------------------
    /**
     * 初始化默认系统参数（首次查询无数据时）
     */
    private SystemParam initDefaultSystemParam() {
        SystemParam defaultParam = new SystemParam();
        defaultParam.setMaxPaperSize(defaultMaxPaperSize);
        defaultParam.setMaxConcurrentCheck(defaultMaxConcurrent);
        defaultParam.setJwtExpiration(defaultJwtExpiration);
        defaultParam.setIsDeleted(0);
        UserBusinessInfoUtils.setAuditField(defaultParam, true);
        systemParamMapper.insert(defaultParam);
        log.info("初始化系统默认参数成功：最大论文大小={}MB，最大并发查重数={}，JWT有效期={}小时",
                defaultMaxPaperSize / 1024 / 1024,
                defaultMaxConcurrent,
                defaultJwtExpiration / 1000 / 3600);
        return defaultParam;
    }
}
