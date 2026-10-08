package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.Impl.SubjectService;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.teacher.dto.UpdateTeacherInfoReq;
import com.abin.checkrepeatservice.teacher.service.TeacherInfoService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TeacherInfoServiceImpl implements TeacherInfoService{

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private SubjectService subjectService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateInfo(UpdateTeacherInfoReq updateReq){
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null|| !authentication.isAuthenticated()){
                return Result.error(ResultCode.NOT_LOGIN, "用户未登录");
            }
            String currentUsername = authentication.getName();
            SysUser currentUser = sysUserMapper.selectOne(
                    Wrappers.<SysUser>lambdaQuery()
                            .eq(SysUser::getUsername, currentUsername)
                            .eq(SysUser::getIsDeleted, 0)
            );

            if (currentUser == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "当前用户不存在");
            }
            currentUser.setRealName(updateReq.getRealName());
            currentUser.setProfessionalTitle(updateReq.getTitle());
            currentUser.setCollegeId(updateReq.getCollegeId());
            // 研究方向：teacher_subject 是**真源**，sys_user.research_direction 降级成
            // 一份「学科中文名 CSV 缓存」（3 个展示点和 2 处关键词匹配直接读它，
            // 保留这一列那几处就一行都不用改，显示还从 'ai' 变成了「人工智能」）。
            // username 是工号，这里不能用它去反查（identity 用 username，userId 才是主键）
            applyResearchFields(currentUser, updateReq.getResearchFields());
            currentUser.setIntroduce(updateReq.getIntroduce());
            currentUser.setPhone(updateReq.getPhone());
            currentUser.setEmail(updateReq.getEmail());
            currentUser.setOffice(updateReq.getOffice());
            currentUser.setOfficeHours(updateReq.getOfficeHours());
            currentUser.setMaxReviewCount(updateReq.getMaxReviewCount());
            currentUser.setReviewDeadline(updateReq.getReviewDeadline());

            sysUserMapper.updateById(currentUser);

            log.info("用户信息更新成功：用户名={}", currentUsername);
            return Result.success("用户信息更新成功");
        } catch (Exception e) {
            log.error("用户信息更新失败：{}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "用户信息更新失败：" + e.getMessage());
        }
    }

    /**
     * 写入教师研究方向。researchFields 的语义已从「7 个硬编码短码」改为
     * **学科领域 code 列表**（如 ["ENG-AI","ENG-SE"]），与论文、阈值共用同一套取值域。
     *
     * 三种入参各有明确语义，别合并处理：
     *   · null = 本次请求不涉及该字段（老客户端不带这个 key）→ 原样保留，什么都不动；
     *   · 非空但**一个都匹配不到学科** = 疑似旧前端发的 7 个旧短码 → 不修改、只告警，
     *     避免一次普通保存把研究方向抹掉（见下面的注释）；
     *   · [] = 明确清空 → 删掉全部关联行，并把缓存列**显式置 NULL**。
     * 这里顺带修掉的既有 bug：原来是 `String.join(",", getResearchFields())`，列表为 null
     * 时直接 NPE，一次「只改手机号」的保存会整个失败。
     */
    private void applyResearchFields(SysUser user, List<String> researchFields) {
        if (researchFields == null) {
            return;
        }
        boolean hasToken = researchFields.stream().anyMatch(f -> f != null && !f.isBlank());
        Map<String, Long> resolved = subjectService.idMapOfCodes(researchFields);
        if (resolved.isEmpty() && hasToken) {
            // 传了值却一个都认不出：最可能是**旧前端** —— 它发的还是 ai / machine_learning
            // 那 7 个短码，在新表里既不是 code 也不是 name。此时若继续走"清空"分支，
            // 一次普通的资料保存就会把教师的研究方向连同关联行一起抹掉。按 DEPLOY.md
            // 第八节（新后端要能跟旧前端并存）这里什么都不动，只告警。
            // 真正的"清空"意图由空列表 [] 表达，不会被这里拦住。
            log.warn("研究方向入参一个都匹配不到学科（疑似旧前端的短码），本次不修改 - 用户ID: {}, 入参: {}",
                    user.getId(), researchFields);
            return;
        }
        subjectService.replaceUserSubjects(user.getId(), resolved.values());
        if (resolved.isEmpty()) {
            // 清空场景：先删关联行（replaceUserSubjects 已做），再把缓存列置 NULL ——
            // 不能指望 setResearchDirection(null) + updateById，NOT_NULL 策略会跳过它，
            // 于是旧的中文名会一直挂在缓存列上；更不能写空串（会让所有教师都命中
            // 研究兴趣匹配，理由见 SysUserMapper.clearResearchDirection 的注释）。
            user.setResearchDirection(null);
            sysUserMapper.clearResearchDirection(user.getId());
            return;
        }
        user.setResearchDirection(String.join(",", subjectService.namesOf(resolved.values())));
    }

    @Override
    public Result<UpdateTeacherInfoReq> getInfoByUserId(Long userId) {
        try {
            // 根据用户ID查询教师信息
            SysUser user = sysUserMapper.selectOne(
                    Wrappers.<SysUser>lambdaQuery()
                            .eq(SysUser::getId, userId)
                            .eq(SysUser::getIsDeleted, 0)
            );

            if (user == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "用户不存在");
            }

            // 将SysUser实体转换为UpdateTeacherInfoReq DTO
            UpdateTeacherInfoReq info = new UpdateTeacherInfoReq();
            info.setRealName(user.getRealName());
            info.setUsername(user.getUsername());
            info.setTitle(user.getProfessionalTitle());
            info.setCollegeId(user.getCollegeId());

            // 研究方向回显：**从 teacher_subject 取 code**，而不是解析 research_direction。
            // 那一列是缓存，且存量里还留着 5 个映射不到学科的旧短码（machine_learning 等）；
            // 拿它回显会把旧短码当成"已选学科"塞回多选框，而多选框的选项来自 subject 表，
            // 结果是一个选不中也去不掉的幽灵选中项。旧值该由前端按"已失效"单独提示。
            info.setResearchFields(subjectService.codesOfUser(userId));
            // 再把「认不出学科」的旧值一并下发，让前端提示"已失效"：
            // 这些值不在 teacher_subject 里、多选框显示不出来，教师一保存就会永久丢掉。
            info.setLegacyResearchFields(subjectService.staleCacheTokens(user.getResearchDirection()));

            info.setIntroduce(user.getIntroduce());
            info.setPhone(user.getPhone());
            info.setEmail(user.getEmail());
            info.setOffice(user.getOffice());
            info.setOfficeHours(user.getOfficeHours());
            info.setMaxReviewCount(user.getMaxReviewCount());
            info.setReviewDeadline(user.getReviewDeadline());

            log.info("获取用户信息成功：用户ID={}", userId);
            return Result.success("获取用户信息成功", info);
        } catch (Exception e) {
            log.error("获取用户信息失败：用户ID={}, 错误信息={}", userId, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取用户信息失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> changePasswordByUserId(Long userId, String newPassword) {
        if (userId == null || newPassword == null){
            return Result.error(ResultCode.PARAM_ERROR, "用户ID或密码不能为空");
        }
        
        // 添加密码长度验证
        if (newPassword.length() < 6 || newPassword.length() > 50) {
            return Result.error(ResultCode.PARAM_ERROR, "密码长度必须在6-50位之间");
        }
        
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        String encodePassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodePassword);
        sysUserMapper.updateById(user);
        log.info("用户密码修改成功：用户ID={}", userId);
        return Result.success("密码修改成功");
    }

}
