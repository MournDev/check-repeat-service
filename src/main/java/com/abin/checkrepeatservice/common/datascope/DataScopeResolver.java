package com.abin.checkrepeatservice.common.datascope;

import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysRoleMapper;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysRole;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 数据范围统一裁决组件（治理项）。
 *
 * <p>定位：替代各 Service 里散写的「学生=本人、教师=本人指导、管理员=全部」判断，
 * 为后续多租户/学院教务等自定义角色预留单一扩展点。所有条件拼装统一走
 * MyBatis-Plus apply 参数绑定，禁止字符串拼接（S11 规范）。</p>
 *
 * <p>范围来源：</p>
 * <ul>
 *   <li>学生 → SELF；教师 → TEACHER（与学生/教师既有权限链路一致）；</li>
 *   <li>超管 → ALL（强制，不受角色配置影响）；</li>
 *   <li>其他后台角色 → sys_role.data_scope（空/非法回落 ALL）。</li>
 * </ul>
 */
@Component
public class DataScopeResolver {

    @Resource
    private SysRoleMapper sysRoleMapper;

    /**
     * 当前登录人的数据范围
     */
    public String resolveScope() {
        return resolveScope(UserBusinessInfoUtils.getCurrentSysUser());
    }

    /**
     * 把数据范围条件附加到论文查询（无范围限制时不做任何修改）
     */
    public void applyPaperScope(LambdaQueryWrapper<PaperInfo> wrapper) {
        SysUser user = UserBusinessInfoUtils.getCurrentSysUser();
        String scope = resolveScope(user);
        switch (scope) {
            case DataScope.COLLEGE -> {
                if (user.getCollegeId() == null) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.apply("college_id = {0}", user.getCollegeId());
                }
            }
            case DataScope.MAJOR -> {
                if (user.getMajorId() == null) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.apply("major_id = {0}", user.getMajorId());
                }
            }
            case DataScope.TEACHER -> wrapper.apply("teacher_id = {0}", user.getId());
            case DataScope.SELF -> wrapper.apply("student_id = {0}", user.getId());
            default -> {
                // ALL：不附加条件
            }
        }
    }

    /**
     * 单篇论文是否在当前登录人的数据范围内（详情/下载/审核/删除等按 ID 直达的入口使用）
     */
    public boolean canAccessPaper(PaperInfo paper) {
        if (paper == null) {
            return false;
        }
        SysUser user = UserBusinessInfoUtils.getCurrentSysUser();
        String scope = resolveScope(user);
        return switch (scope) {
            case DataScope.COLLEGE -> user.getCollegeId() != null
                    && Objects.equals(paper.getCollegeId(), user.getCollegeId());
            case DataScope.MAJOR -> user.getMajorId() != null
                    && Objects.equals(paper.getMajorId(), user.getMajorId());
            case DataScope.TEACHER -> Objects.equals(paper.getTeacherId(), user.getId());
            case DataScope.SELF -> Objects.equals(paper.getStudentId(), user.getId());
            default -> true;
        };
    }

    private String resolveScope(SysUser user) {
        if (UserBusinessInfoUtils.isStudent()) {
            return DataScope.SELF;
        }
        if (UserBusinessInfoUtils.isTeacher()) {
            return DataScope.TEACHER;
        }
        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        if (role == null) {
            return DataScope.ALL;
        }
        // 超管始终全量，避免误配 data_scope 把超管锁死
        if ("SUPER_ADMIN".equalsIgnoreCase(role.getRoleCode())) {
            return DataScope.ALL;
        }
        return DataScope.normalize(role.getDataScope());
    }
}
