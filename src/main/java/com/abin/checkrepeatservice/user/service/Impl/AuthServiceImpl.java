package com.abin.checkrepeatservice.user.service.Impl;


import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.HttpIpUtils;
import com.abin.checkrepeatservice.common.utils.IpLocationUtils;
import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.common.utils.UserAgentUtils;
import com.abin.checkrepeatservice.mapper.SysRoleMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.dto.ForgotPasswordReq;
import com.abin.checkrepeatservice.pojo.dto.LoginReq;
import com.abin.checkrepeatservice.pojo.dto.RefreshTokenReq;
import com.abin.checkrepeatservice.pojo.dto.RegisterReq;
import com.abin.checkrepeatservice.pojo.entity.SysRole;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.monitor.service.BusinessMetricsService;
import com.abin.checkrepeatservice.pojo.entity.SysLoginLog;
import com.abin.checkrepeatservice.user.mapper.SysLoginLogMapper;
import com.abin.checkrepeatservice.user.service.AuthService;
import com.abin.checkrepeatservice.user.vo.LoginVO;
import com.abin.checkrepeatservice.user.vo.RefreshTokenVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务实现类（核心业务逻辑：注册、登录、令牌刷新）
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    // 依赖注入（数据访问层、认证工具、加密工具）
    @Resource
    private AuthenticationManager authenticationManager;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private HttpServletRequest request;

    @Resource
    private SysLoginLogMapper sysLoginLogMapper;

    @Resource
    private BusinessMetricsService businessMetricsService;

    /**
     * 用户注册（事务保证：确保用户插入成功，避免脏数据）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> register(RegisterReq registerReq) {
        // 1. 校验用户名唯一性（避免重复注册）
        try {
            SysUser existingUser = sysUserMapper.selectOne(
                    Wrappers.<SysUser>lambdaQuery()
                            .eq(SysUser::getUsername, registerReq.getUsername())
                            .eq(SysUser::getIsDeleted, 0)
            );
            if (existingUser != null) {
                return Result.error(ResultCode.PARAM_ERROR, "用户名已存在");
            }
        } catch (Exception e) {
            log.error("注册-校验用户名唯一性异常", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "系统内部错误，注册失败");
        }

        // 2. 校验角色有效性（避免传入无效角色ID）
        SysRole sysRole = sysRoleMapper.selectById(registerReq.getRoleId());
        if (sysRole == null || sysRole.getIsDeleted() == 1) {
            return Result.error(ResultCode.PARAM_ERROR, "选择的角色无效，无法注册");
        }

        // 3. 构建新用户实体（密码加密存储）
        SysUser newUser = new SysUser();
        newUser.setUsername(registerReq.getUsername());
        newUser.setPassword(passwordEncoder.encode(registerReq.getPassword())); // 密码加密
        newUser.setRealName(registerReq.getRealName());
        newUser.setRoleId(registerReq.getRoleId());
        newUser.setEmail(registerReq.getEmail());
        newUser.setPhone(registerReq.getPhone());
        newUser.setLastLoginTime(null); // 首次注册无登录时间
        newUser.setIsDeleted(0); // 默认为未删除

        // 4. 插入数据库（审计字段由MyBatis-Plus自动填充：createTime、createUserId等）
        sysUserMapper.insert(newUser);
        log.info("用户注册成功：用户名={}，角色={}", registerReq.getUsername(), sysRole.getRoleCode());
        businessMetricsService.record(BusinessMetricsService.Event.USER_REGISTER,
                BusinessMetricsService.Outcome.SUCCESS);
        return Result.success("注册成功");
    }

    /**
     * 用户登录（Spring Security认证 + JWT生成）
     */
    @Override
    public Result<LoginVO> login(LoginReq loginReq) {
        String loginIp = HttpIpUtils.getRealIp(request);           // IP地址
        String loginDevice = UserAgentUtils.parseDevice(request);   // 设备信息
        String loginLocation = IpLocationUtils.getLocationByIp(loginIp); // 地理位置
        // 1. 执行Spring Security认证（用户名+密码校验）
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginReq.getUsername(),
                            loginReq.getPassword()
                    )
            );
        } catch (Exception e) {
            log.warn("用户登录失败：用户名={}，原因=用户名或密码错误", loginReq.getUsername());
            recordLoginLog(null, loginReq.getUsername(), loginIp, loginLocation,
                    loginDevice, 0, "用户名或密码错误");
            businessMetricsService.record(BusinessMetricsService.Event.USER_LOGIN,
                    BusinessMetricsService.Outcome.FAILURE);
            return Result.error(ResultCode.PARAM_ERROR, "用户名或密码错误");
        }

        // 2. 认证通过：设置上下文（后续权限校验会用到）
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. 查询用户完整信息（含基础字段、审计字段）
        SysUser sysUser = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, loginReq.getUsername())
                        .eq(SysUser::getIsDeleted, 0)
        );
        // 兜底：理论上认证通过后用户必存在，避免极端情况（如认证后用户被删除）
        if (sysUser == null) {
            log.error("用户登录异常：用户名={}，认证通过但用户不存在", loginReq.getUsername());
            businessMetricsService.record(BusinessMetricsService.Event.USER_LOGIN,
                    BusinessMetricsService.Outcome.FAILURE);
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "登录用户不存在，请联系管理员");
        }

        // 4. 查询用户角色信息（获取角色编码，用于前端权限控制）
        SysRole sysRole = sysRoleMapper.selectById(sysUser.getRoleId());
        if (sysRole == null || sysRole.getIsDeleted() == 1) {
            log.error("用户登录异常：用户名={}，角色ID={}无效", loginReq.getUsername(), sysUser.getRoleId());
            businessMetricsService.record(BusinessMetricsService.Event.USER_LOGIN,
                    BusinessMetricsService.Outcome.FAILURE);
            return Result.error(ResultCode.PARAM_ERROR, "用户未分配有效角色，无法登录");
        }
        recordLoginLog(sysUser.getId(), loginReq.getUsername(), loginIp, loginLocation,
                loginDevice, 1, null);
        // 5. 更新用户最后登录时间（审计字段，记录登录轨迹）
        sysUser.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(sysUser);

        // 6. 更新登录日志中的用户ID
        try {
            SysLoginLog loginLog = sysLoginLogMapper.selectOne(
                    Wrappers.<SysLoginLog>lambdaQuery()
                            .eq(SysLoginLog::getUsername, loginReq.getUsername())
                            .last("LIMIT 1")
            );
            if (loginLog != null && loginLog.getUserId() == null) {
                loginLog.setUserId(sysUser.getId());
                sysLoginLogMapper.updateById(loginLog);
            }
        } catch (Exception e) {
            log.warn("更新登录日志用户ID失败：用户名={}", loginReq.getUsername(), e);
        }

        // 6. 生成JWT令牌（携带用户ID、用户名、角色编码）
        String jwtToken = jwtUtils.generateToken(
                sysUser.getId(),
                sysUser.getUsername(),
                sysRole.getRoleCode()
        );

        // 7. 封装登录响应VO（仅返回非敏感信息）
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(jwtToken);
        loginVO.setUserId(sysUser.getId());
        loginVO.setRoleCode(sysRole.getRoleCode());
        loginVO.setUsername(sysUser.getUsername());
        loginVO.setRealName(sysUser.getRealName());
        loginVO.setMajor(sysUser.getMajor());
        loginVO.setGrade(sysUser.getGrade());
        loginVO.setClassName(sysUser.getClassName());
        loginVO.setPhone(sysUser.getPhone());
        loginVO.setEmail(sysUser.getEmail());
        loginVO.setEmailVerified(sysUser.getEmailVerified());
        loginVO.setIntroduce(sysUser.getIntroduce());
        loginVO.setExpireTime(jwtUtils.getExpirationDate(jwtToken).getTime()); // 令牌过期时间
        loginVO.setAvatar(sysUser.getAvatar());
        loginVO.setLastLoginTime(sysUser.getLastLoginTime()); // 最后登录时间


        log.info("用户登录成功：用户名={}，角色={}，令牌过期时间={}",
                loginReq.getUsername(), sysRole.getRoleCode(), loginVO.getExpireTime());
        businessMetricsService.record(BusinessMetricsService.Event.USER_LOGIN,
                BusinessMetricsService.Outcome.SUCCESS);
        return Result.success("登录成功", loginVO);
    }

    /**
     * 令牌刷新（避免用户频繁登录，提升体验）
     */
    @Override
    public Result<RefreshTokenVO> refreshToken(RefreshTokenReq refreshTokenReq) {
        String oldToken = refreshTokenReq.getOldToken();

        // 1. 校验旧令牌有效性（格式+是否过期）
        if (!jwtUtils.validateTokenFormat(oldToken)) {
            log.warn("令牌刷新失败：旧令牌格式错误，token={}", oldToken);
            return Result.error(ResultCode.PARAM_ERROR, "令牌格式错误，请重新登录");
        }
        if (jwtUtils.isTokenExpired(oldToken)) {
            log.warn("令牌刷新失败：旧令牌已过期，token={}", oldToken);
            return Result.error(ResultCode.PARAM_VALUE_INVALID, "旧令牌已过期，请重新登录");
        }

        // 2. 从旧令牌提取用户核心信息（无需查库，减少IO开销）
        Long userId = jwtUtils.extractUserId(oldToken);
        String username = jwtUtils.extractUsername(oldToken);
        String roleCode = jwtUtils.extractRoleCode(oldToken);
        // 兜底：校验令牌中的用户信息是否完整
        if (userId == null || username == null || roleCode == null) {
            log.error("令牌刷新失败：旧令牌信息不完整，token={}", oldToken);
            return Result.error(ResultCode.PARAM_ERROR, "令牌信息损坏，请重新登录");
        }

        // 3. 生成新令牌（过期时间重置，保持用户信息一致）
        String newToken = jwtUtils.generateToken(userId, username, roleCode);
        Long newExpireTime = jwtUtils.getExpirationDate(newToken).getTime();

        // 4. 封装刷新响应VO
        RefreshTokenVO refreshTokenVO = new RefreshTokenVO();
        refreshTokenVO.setNewToken(newToken);
        refreshTokenVO.setExpireTime(newExpireTime);

        log.info("令牌刷新成功：用户名={}，旧令牌过期时间={}，新令牌过期时间={}",
                username, jwtUtils.getExpirationDate(oldToken), newExpireTime);
        return Result.success("令牌刷新成功", refreshTokenVO);
    }

    /**
     * 用户退出登录（清除上下文，前端需同步清除令牌）
     */
    @Override
    public Result<String> logout() {
        SecurityContextHolder.clearContext();
        log.info("用户退出登录成功：清除Security上下文");
        return Result.success("退出登录成功");
    }

    // AuthServiceImpl.java
    @Override
    public Result<String> forgotPassword(ForgotPasswordReq forgotPasswordReq) {
        // 1. 根据用户名查询用户信息
        SysUser sysUser = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, forgotPasswordReq.getUsername())
                        .eq(SysUser::getIsDeleted, 0)
        );

        // 2. 校验用户是否存在
        if (sysUser == null) {
            return Result.error(ResultCode.PARAM_ERROR, "用户不存在");
        }

        // 3. 校验邮箱是否匹配
        if (!forgotPasswordReq.getEmail().equals(sysUser.getEmail())) {
            return Result.error(ResultCode.PARAM_ERROR, "邮箱验证失败");
        }

        // 4. 更新密码（加密存储）
        try {
            sysUser.setPassword(passwordEncoder.encode(forgotPasswordReq.getNewPassword()));
            sysUserMapper.updateById(sysUser);
            log.info("用户密码重置成功：用户名={}", forgotPasswordReq.getUsername());
            return Result.success("密码重置成功");
        } catch (Exception e) {
            log.error("用户密码重置失败：用户名={}", forgotPasswordReq.getUsername(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "密码重置失败");
        }
    }
    /**
     * 记录登录日志
     */
    private void recordLoginLog(Long userId, String username, String loginIp,
                                String loginLocation, String loginDevice,
                                Integer loginStatus, String errorMsg) {
        try {
            SysLoginLog loginLog = new SysLoginLog();
            loginLog.setUserId(userId);
            loginLog.setUsername(username);
            loginLog.setLoginIp(loginIp);
            loginLog.setLoginLocation(loginLocation);
            loginLog.setLoginDevice(loginDevice);
            loginLog.setLoginTime(LocalDateTime.now());
            loginLog.setLoginResult(loginStatus);
            loginLog.setFailReason(errorMsg);
            sysLoginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.error("记录登录日志失败：用户名={}", username, e);
        }
    }


    // UserService.java
//    public Map<String, Object> getUserInfoByRole(Long userId, String userType) {
//        SysUser sysUser = sysUserMapper.selectById(userId);
//
//        // 根据用户类型返回不同的字段集合
//        return switch (userType) {
//            case "STUDENT" -> buildStudentResponse(sysUser);
//            case "TEACHER" -> buildTeacherResponse(sysUser);
//            case "ADMIN" -> buildAdminResponse(sysUser);
//            default -> Map.of();
//        };
//    }

//    private Map<String, Object> buildStudentResponse(SysUser sysUser) {
//        Map<String, Object> response = new HashMap<>();
//        response.put("userId", sysUser.getId());
//        response.put("roleCode", sysUser.getRoleId());
//        response.put("username", sysUser.getUsername());
//        response.put("realName", sysUser.getRealName());
//        response.put("major", sysUser.getMajor());
//        response.put("userType", sysUser.getUserType());
//        response.put("email", sysUser.getEmail());
//        response.put("phone", sysUser.getPhone());
//        return response;
//    }
//
//    private Map<String, Object> buildTeacherResponse(SysUser sysUser) {
//        Map<String, Object> response = new HashMap<>();
//        response.put("id", sysUser.getId());
//        response.put("username", sysUser.getUsername());
//        response.put("realName", sysUser.getRealName());
//        response.put("collegeId", sysUser.getCollegeId());
//        response.put("userType", sysUser.getUserType());
//        response.put("email", sysUser.getEmail());
//        response.put("phone", sysUser.getPhone());
//        // 教师不需要的字段：studentId, grade, classInfo 等
//        return response;
//    }
//
//    private Map<String, Object> buildAdminResponse(SysUser sysUser) {
//        Map<String, Object> response = new HashMap<>();
//        response.put("id", sysUser.getId());
//        response.put("username", sysUser.getUsername());
//        response.put("realName", sysUser.getRealName());
//        response.put("userType", sysUser.getUserType());
//        response.put("email", sysUser.getEmail());
//        response.put("phone", sysUser.getPhone());
//        response.put("roleId", sysUser.getRoleId());
//        // 管理员可以看到所有字段
//        response.put("department", sysUser.getDepartment());
//        response.put("collegeId", sysUser.getCollegeId());
//        // 但不需要学生和教师的特定字段
//        return response;
//    }
}
