package com.abin.checkrepeatservice.user.service.Impl;


import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.IpLocationService;
import com.abin.checkrepeatservice.common.utils.HttpIpUtils;
import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.common.utils.UserAgentUtils;
import com.abin.checkrepeatservice.mapper.SysRoleMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.dto.ForgotPasswordReq;
import com.abin.checkrepeatservice.pojo.dto.LoginReq;
import com.abin.checkrepeatservice.pojo.dto.RefreshTokenReq;
import com.abin.checkrepeatservice.pojo.dto.RegisterReq;
import com.abin.checkrepeatservice.pojo.dto.SendPasswordResetCodeReq;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    private IpLocationService ipLocationService;

    @Resource
    private BusinessMetricsService businessMetricsService;

    @Resource
    private EmailService emailService;

    /*
     * 这里刻意用自动配置的 StringRedisTemplate，而不是 InfoServiceImpl 那个
     * RedisTemplate<String,String>。后者其实是 Spring Boot 自动配置的
     * RedisTemplate<Object,Object>（JDK 序列化），两个坑：
     *   1. 自建 RedisTemplate bean 会顶掉自动配置，已有的 email_code: /
     *      email_verify_token:（24h TTL，可能正被用户使用）会全部读不出来；
     *   2. JDK 序列化下 opsForValue().increment() 写入的是裸 ASCII 整数，
     *      再用 get() 读会抛 SerializationException —— 错误计数要能加也能读。
     * StringRedisTemplate 同样是零配置，且键值在 redis-cli 里可读。
     */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 找回密码验证码的 Redis 键前缀与有效期
    private static final String KEY_CODE_PREFIX = "pwd_reset_code:";
    private static final String KEY_COOLDOWN_PREFIX = "pwd_reset_code_cooldown:";
    private static final String KEY_ATTEMPTS_PREFIX = "pwd_reset_code_attempts:";
    private static final long CODE_TTL_SECONDS = 300;
    private static final long COOLDOWN_SECONDS = 60;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    // 用户名不存在与邮箱不匹配共用一句，避免暴露账号是否存在
    private static final String IDENTITY_MISMATCH_MSG = "用户名或邮箱不匹配";

    // 验证码用 SecureRandom：Math.random() 既无前导零（100000-999999）又存在偏斜
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
        String loginLocation = ipLocationService.getLocationByIp(loginIp); // 属地（离线库 ip2region）
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

    /**
     * 发送密码重置邮箱验证码（找回密码第一步）
     */
    @Override
    public Result<String> sendPasswordResetCode(SendPasswordResetCodeReq req) {
        String username = req.getUsername();

        try {
            // 1. 先占重发冷却位。放在身份校验之前，是为了让「发送过于频繁」这条提示
            //    对存在与不存在的账号一视同仁，不至于变成账号探测的信号。
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                    KEY_COOLDOWN_PREFIX + username, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(acquired)) {
                return Result.error(ResultCode.BUSINESS_NO_COUNT, "发送过于频繁，请稍后再试");
            }

            // 2. 校验身份（用户名不存在与邮箱不匹配回同一句）
            SysUser sysUser = sysUserMapper.selectOne(
                    Wrappers.<SysUser>lambdaQuery()
                            .eq(SysUser::getUsername, username)
                            .eq(SysUser::getIsDeleted, 0)
            );
            if (sysUser == null || !req.getEmail().equals(sysUser.getEmail())) {
                log.warn("密码重置验证码身份校验未通过：用户名={}", username);
                return Result.error(ResultCode.PARAM_ERROR, IDENTITY_MISMATCH_MSG);
            }

            // 3. 生成 6 位验证码并写入 Redis（5 分钟有效）
            String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
            stringRedisTemplate.opsForValue().set(
                    KEY_CODE_PREFIX + username, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);

            // 4. 发信。发不出去就把验证码和冷却位一起撤掉，让用户能立刻重试
            boolean sent = emailService.sendPasswordResetCodeEmail(
                    sysUser.getEmail(), sysUser.getUsername(), code,
                    (int) (CODE_TTL_SECONDS / 60), sysUser.getId());
            if (!sent) {
                stringRedisTemplate.delete(KEY_CODE_PREFIX + username);
                stringRedisTemplate.delete(KEY_COOLDOWN_PREFIX + username);
                return Result.error(ResultCode.SYSTEM_ERROR, "验证码发送失败，请稍后重试");
            }

            // 日志里不要出现验证码本身
            log.info("密码重置验证码已发送：用户名={}", username);
            return Result.success("验证码发送成功");
        } catch (Exception e) {
            // 这一步让找回密码依赖上了 Redis：Redis 不可用时必须降级成一句提示，
            // 而不是让连接异常冒到全局处理器，变成一句没头没尾的「系统繁忙」。
            log.error("发送密码重置验证码失败：用户名={}", username, e);
            try {
                stringRedisTemplate.delete(KEY_COOLDOWN_PREFIX + username);
            } catch (Exception ignored) {
                // 清理失败无所谓，冷却位自己会过期
            }
            return Result.error(ResultCode.SYSTEM_ERROR, "验证码发送失败，请稍后重试");
        }
    }

    // AuthServiceImpl.java
    @Override
    public Result<String> forgotPassword(ForgotPasswordReq forgotPasswordReq) {
        String username = forgotPasswordReq.getUsername();

        // 1. 根据用户名查询用户信息
        SysUser sysUser = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getIsDeleted, 0)
        );

        // 2. 校验身份（用户名不存在与邮箱不匹配回同一句，与发码接口保持一致）
        if (sysUser == null || !forgotPasswordReq.getEmail().equals(sysUser.getEmail())) {
            log.warn("密码重置身份校验未通过：用户名={}", username);
            return Result.error(ResultCode.PARAM_ERROR, IDENTITY_MISMATCH_MSG);
        }

        String codeKey = KEY_CODE_PREFIX + username;
        try {
            // 3. 校验邮箱验证码：改密码之前必须过这一关。
            //    （此前 ForgotPasswordReq 没有 verificationCode 字段，前端传的验证码被
            //     Jackson 静默丢弃，等于填什么都能改密码，只剩「邮箱对得上」一层校验。）
            String storedCode = stringRedisTemplate.opsForValue().get(codeKey);
            if (storedCode == null || !MessageDigest.isEqual(
                    storedCode.getBytes(StandardCharsets.UTF_8),
                    forgotPasswordReq.getVerificationCode().getBytes(StandardCharsets.UTF_8))) {
                return verifyCodeFailed(username, codeKey, storedCode != null);
            }

            // 4. 更新密码（加密存储）
            sysUser.setPassword(passwordEncoder.encode(forgotPasswordReq.getNewPassword()));
            sysUserMapper.updateById(sysUser);

            // 5. 用完即弃：删掉验证码和错误计数（冷却位留给它自然过期）
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(KEY_ATTEMPTS_PREFIX + username);

            log.info("用户密码重置成功：用户名={}", username);
            return Result.success("密码重置成功");
        } catch (Exception e) {
            log.error("用户密码重置失败：用户名={}", username, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "密码重置失败");
        }
    }

    /**
     * 验证码校验失败的分支：累计错误次数，超过上限就把验证码作废，逼用户重新获取
     *
     * @param codeExists 验证码是否还在 Redis 里（不在时不要建计数键）
     */
    private Result<String> verifyCodeFailed(String username, String codeKey, boolean codeExists) {
        // 码都不存在就别写计数键了：否则匿名调用方拿任意用户名来刷，
        // 就能造出一堆永远不用的计数键。
        if (!codeExists) {
            return Result.error(ResultCode.PARAM_VALUE_INVALID, "验证码错误或已过期");
        }

        String attemptsKey = KEY_ATTEMPTS_PREFIX + username;
        Long attempts = stringRedisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            // increment 不会继承验证码的 TTL，首次计数时自己补一个，与验证码同寿
            stringRedisTemplate.expire(attemptsKey, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        }
        if (attempts != null && attempts >= MAX_VERIFY_ATTEMPTS) {
            stringRedisTemplate.delete(codeKey);
            log.warn("密码重置验证码错误次数超限，已作废验证码：用户名={}", username);
            return Result.error(ResultCode.PARAM_VALUE_INVALID, "验证码错误次数过多，请重新获取验证码");
        }
        return Result.error(ResultCode.PARAM_VALUE_INVALID, "验证码错误或已过期");
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
