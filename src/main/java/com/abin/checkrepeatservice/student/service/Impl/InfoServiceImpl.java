package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.mapper.SysRoleMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.dto.UpdatePasswordReq;
import com.abin.checkrepeatservice.pojo.entity.SysLoginLog;
import com.abin.checkrepeatservice.pojo.entity.SysRole;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.dto.UpdateEmailReq;
import com.abin.checkrepeatservice.student.service.InfoService;
import com.abin.checkrepeatservice.student.vo.LoginHistoryVO;
import com.abin.checkrepeatservice.student.vo.LoginLogQueryReq;
import com.abin.checkrepeatservice.user.dto.UpdateUserInfoReq;
import com.abin.checkrepeatservice.user.mapper.SysLoginLogMapper;
import com.abin.checkrepeatservice.user.vo.LoginVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InfoServiceImpl implements InfoService {
    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private MinioClient minioClient;

    @Value("${minio.bucket.avatar:avatar}")
    private String avatarBucket;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    /**
     * 浏览器直连用的 MinIO 地址，只用来拼存库的 URL（头像的 &lt;img src&gt;）。
     *
     * <p>必须和 {@link #minioEndpoint} 分开：后者是后端 SDK 的连接地址，容器部署下
     * 通常是 compose 里的网络别名（如 {@code http://minio:9000}），只有同网络容器能解析 ——
     * 拿它拼出来的 URL 存进 sys_user.avatar，浏览器打不开，头像永远裂图。
     * 没配 MINIO_PUBLIC_ENDPOINT 时回退成 endpoint，行为和以前一致。</p>
     */
    @Value("${minio.public-endpoint:${minio.endpoint}}")
    private String minioPublicEndpoint;

    @Value("${spring.mail.username}")
    private String fromEmail;


    @Resource
    private SysLoginLogMapper sysLoginLogMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private JavaMailSender mailSender;

    @Override
    public Result<LoginVO> updateUserInfo(UpdateUserInfoReq updateReq) {
        try {
            // 1. 获取当前登录用户（从 Security 上下文获取）
            SysUser currentUser = currentLoginUser();
            if (currentUser == null) {
                // 分不出是"没登录"还是"账号已不存在"，对调用方而言都是要重新登录
                return Result.error(ResultCode.NOT_LOGIN, "用户未登录");
            }

            // 2. 更新用户信息（仅允许更新部分字段）
            currentUser.setRealName(updateReq.getRealName());
            currentUser.setEmail(updateReq.getEmail());
            currentUser.setPhone(updateReq.getPhone());
            currentUser.setMajor(updateReq.getMajor());
            currentUser.setCollegeName(updateReq.getCollegeName());
            currentUser.setGrade(updateReq.getGrade());
            currentUser.setClassName(updateReq.getClassName());
            currentUser.setIntroduce(updateReq.getIntroduce());

            sysUserMapper.updateById(currentUser);

            // 3. 查询角色信息用于构建 LoginVO
            SysRole sysRole = sysRoleMapper.selectById(currentUser.getRoleId());
            if (sysRole == null || sysRole.getIsDeleted() == 1) {
                log.warn("用户角色无效，roleId={}", currentUser.getRoleId());
                return Result.error(ResultCode.PARAM_ERROR, "用户角色无效");
            }

            log.info("用户信息更新成功：用户名={}", currentUser.getUsername());
            return Result.success("用户信息更新成功", toLoginVO(currentUser, sysRole.getRoleCode()));
        } catch (Exception e) {
            log.error("用户信息更新失败：{}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR,"用户信息更新失败：" + e.getMessage());
        }
    }

    @Override
    public Result<LoginVO> getCurrentUserInfo() {
        try {
            SysUser currentUser = currentLoginUser();
            if (currentUser == null) {
                return Result.error(ResultCode.NOT_LOGIN, "用户未登录");
            }

            SysRole sysRole = sysRoleMapper.selectById(currentUser.getRoleId());
            if (sysRole == null || sysRole.getIsDeleted() == 1) {
                log.warn("用户角色无效，roleId={}", currentUser.getRoleId());
                return Result.error(ResultCode.PARAM_ERROR, "用户角色无效");
            }

            return Result.success(toLoginVO(currentUser, sysRole.getRoleCode()));
        } catch (Exception e) {
            log.error("查询当前用户信息失败：{}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询用户信息失败：" + e.getMessage());
        }
    }

    /**
     * 取当前登录用户（Security 上下文 → 用户名 → sys_user）。
     * 未登录或用户已被删除时返回 null。
     */
    private SysUser currentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, authentication.getName())
                        .eq(SysUser::getIsDeleted, 0)
        );
    }

    /**
     * 把用户实体拼成前端用的 LoginVO。
     *
     * <p>token / expireTime 不填：它们只属于登录响应，这里共用同一个结构是为了让前端
     * 「登录 / 查询 / 更新」三条路径拿到同样一组字段、合并逻辑可以共用。</p>
     *
     * <p>emailVerified 和 avatar 必须带上：前端拿到响应后是
     * {@code { ...userStore.userInfo, ...res.data }} 这样整体覆盖合并的，
     * 少一个字段就会把 store 里已有值覆盖成 null（表现为保存一次资料，
     * 邮箱验证状态变回「未验证」）。</p>
     */
    private LoginVO toLoginVO(SysUser user, String roleCode) {
        LoginVO loginVO = new LoginVO();
        loginVO.setUserId(user.getId());
        loginVO.setRoleCode(roleCode);
        loginVO.setUsername(user.getUsername());
        loginVO.setRealName(user.getRealName());
        loginVO.setMajor(user.getMajor());
        loginVO.setCollegeName(user.getCollegeName());
        loginVO.setGrade(user.getGrade());
        loginVO.setClassName(user.getClassName());
        loginVO.setPhone(user.getPhone());
        loginVO.setEmail(user.getEmail());
        loginVO.setEmailVerified(user.getEmailVerified());
        loginVO.setIntroduce(user.getIntroduce());
        loginVO.setAvatar(user.getAvatar());
        loginVO.setLastLoginTime(user.getLastLoginTime());
        return loginVO;
    }

    @Override
    public Result<String> uploadAvatar(MultipartFile file) {
        try {
            // 1. 参数校验
            if (file == null || file.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "请选择要上传的头像文件");
            }

            // 2. 获取当前登录用户信息
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
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

            // 4. 文件类型和大小校验
            String contentType = file.getContentType();
            if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
                return Result.error(ResultCode.PARAM_ERROR, "只支持 JPG 和 PNG 格式的图片");
            }

            // 限制文件大小（例如5MB）
            if (file.getSize() > 5 * 1024 * 1024) {
                return Result.error(ResultCode.PARAM_ERROR, "头像文件大小不能超过5MB");
            }

            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename != null ?
                    originalFilename.substring(originalFilename.lastIndexOf(".")) : "";

            // 生成唯一文件名
            String uniqueFileName = "avatar/" + currentUser.getId() + "/" +
                    UUID.randomUUID().toString().replace("-", "") + fileExtension;

            // 5. 上传文件到 MinIO
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(avatarBucket)
                        .object(uniqueFileName)
                        .stream(inputStream, inputStream.available(), -1)
                        .contentType(contentType)
                        .build());
            }

            // 6. 构建可访问的头像URL（给浏览器用的那个地址，不是 SDK 的连接地址）
            String avatarUrl = minioPublicEndpoint + "/" + avatarBucket + "/" + uniqueFileName;

            // 7. 更新用户头像路径
            currentUser.setAvatar(avatarUrl);
            sysUserMapper.updateById(currentUser);

            log.info("用户头像上传成功： 头像路径={}", avatarUrl);
            return Result.success("头像上传成功", avatarUrl);
        } catch (ErrorResponseException e) {
            // MinIO 侧最常见的这个失败：桶没建过（新环境刚部署、或桶名配错）。
            // 别把 "The specified bucket does not exist" 这句英文直接甩给用户，
            // 日志里把桶名带上，管理员一眼知道该去建哪个桶。
            String minioCode = (e.errorResponse() == null) ? "" : e.errorResponse().code();
            if ("NoSuchBucket".equals(minioCode)) {
                log.error("头像上传失败：存储桶 {} 不存在，需先在 MinIO 上创建该桶并开放匿名读，"
                        + "否则头像存不进去；endpoint={}", avatarBucket, minioEndpoint);
                return Result.error(ResultCode.SYSTEM_ERROR, "头像存储未初始化（存储桶不存在），请联系管理员");
            }
            log.error("头像上传失败: 错误信息={}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "头像上传失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("头像上传失败: 错误信息={}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "头像上传失败：" + e.getMessage());
        }
    }

    @Override
    public Result<Page<LoginHistoryVO>> getLoginHistory(LoginLogQueryReq queryReq) {
        try {
            // 构建查询条件
            LambdaQueryWrapper<SysLoginLog> queryWrapper = Wrappers.<SysLoginLog>lambdaQuery()
                    .like(StringUtils.hasText(queryReq.getUsername()), SysLoginLog::getUsername, queryReq.getUsername())
                    .like(StringUtils.hasText(queryReq.getLoginIp()), SysLoginLog::getLoginIp, queryReq.getLoginIp())
                    .eq(queryReq.getLoginStatus() != null, SysLoginLog::getLoginResult, queryReq.getLoginStatus())
                    .orderByDesc(SysLoginLog::getLoginTime);

            // 分页查询
            Page<SysLoginLog> page = new Page<>(queryReq.getPageNo(), queryReq.getPageSize());
            Page<SysLoginLog> resultPage = sysLoginLogMapper.selectPage(page, queryWrapper);

            // 转换为VO
            Page<LoginHistoryVO> voPage = new Page<>(queryReq.getPageNo(), queryReq.getPageSize(), resultPage.getTotal());
            List<LoginHistoryVO> voList = resultPage.getRecords().stream().map(sysLoginLog -> {
                LoginHistoryVO vo = new LoginHistoryVO();
                BeanUtils.copyProperties(sysLoginLog, vo);
                return vo;
            }).collect(Collectors.toList());

            voPage.setRecords(voList);

            log.info("查询登录历史成功，查询条件：{}", queryReq);
            return Result.success("查询成功", voPage);
        } catch (Exception e) {
            log.error("查询登录历史失败：{}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> updatePassword(UpdatePasswordReq updatePasswordReq) {
        try {
            // 1. 获取当前登录用户
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
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

            // 2. 校验原密码是否正确
            if (!passwordEncoder.matches(updatePasswordReq.getOldPassword(), currentUser.getPassword())) {
                return Result.error(ResultCode.PARAM_ERROR, "原密码错误");
            }

            // 4. 校验新密码不能与原密码相同
            if (updatePasswordReq.getNewPassword().equals(updatePasswordReq.getOldPassword())) {
                return Result.error(ResultCode.PARAM_ERROR, "新密码不能与原密码相同");
            }

            // 5. 更新密码（加密存储）
            currentUser.setPassword(passwordEncoder.encode(updatePasswordReq.getNewPassword()));
            sysUserMapper.updateById(currentUser);

            log.info("用户密码修改成功：用户名={}", currentUsername);
            return Result.success("密码修改成功");
        } catch (Exception e) {
            log.error("用户密码修改失败：{}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "密码修改失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> sendEmailCode(String email) {
        // 生成6位随机验证码
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));

        // 将验证码存储到Redis中，设置5分钟过期时间
        redisTemplate.opsForValue().set("email_code:" + email, code, 300, TimeUnit.SECONDS);

        // 发送邮件
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("验证码");
            message.setText("您的验证码是：" + code + "，5分钟内有效。");
            mailSender.send(message);
            return Result.success("验证码发送成功");
        } catch (Exception e) {
            log.error("发送验证码失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "验证码发送失败");
        }
    }


    @Override
    public Result<String> verifyEmail(String token) {
        // 从Redis中获取token对应的信息
        String email = redisTemplate.opsForValue().get("email_verify_token:" + token);

        if (StringUtils.isEmpty(email)) {
            return Result.error(ResultCode.PARAM_ERROR, "验证链接已失效");
        }

        // 更新用户邮箱状态为已验证
        SysUser user = sysUserMapper.selectByEmail(email);
        if (user != null) {
            user.setEmailVerified(1);
            sysUserMapper.updateById(user);

            // 删除已使用的token
            redisTemplate.delete("email_verify_token:" + token);

            return Result.success("邮箱验证成功");
        }

        return Result.error(ResultCode.PARAM_ERROR, "用户不存在");
    }


    @Override
    public Result<String> sendVerifyEmail(String email) {
        // 生成唯一验证token
        String token = UUID.randomUUID().toString().replace("-", "");

        // 将token和邮箱存储到Redis中，设置24小时过期时间
        redisTemplate.opsForValue().set("email_verify_token:" + token, email, 24, TimeUnit.HOURS);

        // 构建验证链接
        String verifyUrl = "http://localhost:3000/verify-email?token=" + token;
        // 发送验证邮件
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("邮箱验证");
            message.setText("请点击以下链接完成邮箱验证：\n" + verifyUrl + "\n链接24小时内有效。");
            mailSender.send(message);
            return Result.success("验证邮件发送成功");
        } catch (Exception e) {
            log.error("发送验证邮件失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "验证邮件发送失败");
        }
    }


    @Override
    public Result<String> updateEmail(UpdateEmailReq updateReq) {
        // 获取用户ID和新邮箱
        Long userId = updateReq.getUserId();
        String newEmail = updateReq.getNewEmail();
        String verificationCode = updateReq.getVerificationCode();

        // 检查验证码是否正确
        String storedCode = redisTemplate.opsForValue().get("email_code:" + newEmail);
        if (!verificationCode.equals(storedCode)) {
            return Result.error(ResultCode.PARAM_ERROR, "验证码错误");
        }

        // 查找用户
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.PARAM_ERROR, "用户不存在");
        }

        // 检查邮箱是否已被其他用户使用
        SysUser existingUser = sysUserMapper.selectByEmail(newEmail);
        if (existingUser != null && !existingUser.getId().equals(userId)) {
            return Result.error(ResultCode.PARAM_ERROR, "该邮箱已被其他用户使用");
        }

        // 更新用户邮箱
        user.setEmail(newEmail);
        user.setEmailVerified(0); // 新邮箱需要重新验证
        sysUserMapper.updateById(user);

        // 删除已使用的验证码
        redisTemplate.delete("email_code:" + newEmail);

        return Result.success("邮箱更新成功，请重新验证新邮箱");
    }

}
