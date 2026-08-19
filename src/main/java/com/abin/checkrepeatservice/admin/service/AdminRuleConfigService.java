package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.vo.CheckRuleOperateReq;
import com.abin.checkrepeatservice.admin.vo.CompareLibOperateReq;
import com.abin.checkrepeatservice.admin.dto.RuleLibRelationDTO;
import com.abin.checkrepeatservice.admin.vo.SystemParamReq;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.CheckRule;
import com.abin.checkrepeatservice.pojo.entity.CompareLib;
import com.abin.checkrepeatservice.pojo.entity.SystemParam;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 管理员规则配置服务接口
 */
public interface AdminRuleConfigService {
    // ========================== 查重规则管理 ==========================
    /**
     * 1. 管理员查询查重规则列表（分页）
     * @param ruleName 规则名称（可选，模糊查询）
     * @param isDefault 是否默认规则（可选：0-否，1-是）
     * @param currentPage 当前页码
     * @param pageSize 每页条数
     * @return 分页后的规则列表
     */
    Result<Page<CheckRule>> getCheckRuleList(@RequestParam(value = "ruleName", required = false) String ruleName,
                                             @RequestParam(value = "isDefault", required = false) Integer isDefault,
                                             @RequestParam("currentPage") Integer currentPage,
                                             @RequestParam("pageSize") Integer pageSize);

    /**
     * 2. 管理员新增/编辑查重规则
     * @param operateReq 规则操作参数（ruleId为空则新增，不为空则编辑）
     * @return 操作结果（新增返回规则ID，编辑返回成功标识）
     */
    Result<Map<String, Object>> saveOrUpdateCheckRule(CheckRuleOperateReq operateReq);

    /**
     * 3. 管理员删除查重规则（仅未关联任务的规则可删除）
     *
     * @param ruleId 规则ID（通过@RequestParam传参）
     * @return 删除结果
     */
    Result<String> deleteCheckRule(@RequestParam("ruleId") Long ruleId);

    /**
     * 4. 管理员查询规则关联的比对库
     * @param ruleId 规则ID（通过@RequestParam传参）
     * @return 规则与比对库关联DTO
     */
    Result<RuleLibRelationDTO> getRuleRelatedLibs(@RequestParam("ruleId") Long ruleId);

    // ========================== 比对库管理 ==========================
    /**
     * 5. 管理员查询比对库列表（分页）
     * @param libName 库名称（可选，模糊查询）
     * @param libType 库类型（可选：LOCAL-本地，REMOTE-远程）
     * @param isEnabled 是否启用（可选：0-禁用，1-启用）
     * @param currentPage 当前页码
     * @param pageSize 每页条数
     * @return 分页后的比对库列表
     */
    Result<Page<CompareLib>> getCompareLibList(@RequestParam(value = "libName", required = false) String libName,
                                                   @RequestParam(value = "libType", required = false) String libType,
                                                   @RequestParam(value = "isEnabled", required = false) Integer isEnabled,
                                                   @RequestParam("currentPage") Integer currentPage,
                                                   @RequestParam("pageSize") Integer pageSize);

    /**
     * 6. 管理员新增/编辑比对库
     * @param operateReq 比对库操作参数（libId为空则新增，不为空则编辑）
     * @return 操作结果（新增返回库ID，编辑返回成功标识）
     */
    Result<Map<String, Object>> saveOrUpdateCompareLib(CompareLibOperateReq operateReq);

    /**
     * 7. 管理员启用/禁用比对库
     *
     * @param libId     库ID（通过@RequestParam传参）
     * @param isEnabled 是否启用（0-禁用，1-启用，通过@RequestParam传参）
     * @return 操作结果
     */
    Result<String> toggleLibEnabled(@RequestParam("libId") Long libId,
                                    @RequestParam("isEnabled") Integer isEnabled);

    // ========================== 系统参数配置 ==========================
    /**
     * 8. 管理员查询当前系统参数
     * @return 系统参数实体
     */
    Result<SystemParam> getCurrentSystemParam();

    /**
     * 9. 管理员更新系统参数
     *
     * @param paramReq 系统参数配置参数
     * @return 更新结果
     */
    Result<String> updateSystemParam(SystemParamReq paramReq);
}
