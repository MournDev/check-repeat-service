package com.abin.checkrepeatservice.common.aspect;

import com.abin.checkrepeatservice.admin.service.SysOperationLogService;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.utils.HttpIpUtils;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.SysOperationLog;
import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.admin.mapper.SysOperationLogMapper;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * 操作日志切面
 * 自动记录带有@OperationLog注解的方法执行情况
 */
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    @Resource
    private JwtUtils jwtUtils;
    
    @Resource
    private SysOperationLogMapper sysOperationLogMapper;

    /**
     * S15：日志落库真异步所用线程池（AsyncConfig 通用池 core3/max10/CallerRunsPolicy 兜底）。
     * 注意：必须注入具体 ThreadPoolTaskExecutor 类型（见 AsyncConfig 注释踩坑记录）
     */
    @Resource(name = "asyncExecutor")
    private ThreadPoolTaskExecutor asyncExecutor;

    /** 脱敏字段（键名不区分大小写）：命中后值替换为 ***，防止密码/令牌明文入库 */
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(\"(?:password|oldPassword|newPassword|confirmPassword|pwd|token|accessToken"
                    + "|refreshToken|secret|secretKey|authorization|authCode)\"\\s*:\\s*)"
                    + "(?:\"[^\"]*\"|[^,}\\]]+)",
            Pattern.CASE_INSENSITIVE);
    private static final String MASK_REPLACEMENT = "$1\"***\"";

    /** 参数串长度上限：防止大请求体（如含长文本的 DTO）撑爆日志列 */
    private static final int MAX_PARAM_LENGTH = 2000;

    @Around("@annotation(operationLog)")
    public Object recordOperationLog(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        String errorMsg = null;
        
        try {
            // 执行目标方法
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            errorMsg = e.getMessage();
            throw e;
        } finally {
            // 异步记录日志（避免影响主业务性能）
            try {
                recordLog(joinPoint, operationLog, result, errorMsg, System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                log.error("记录操作日志失败: {}", e.getMessage(), e);
            }
        }
    }

    private void recordLog(ProceedingJoinPoint joinPoint, OperationLog operationLog,
                          Object result, String errorMsg, long executeTime) {
        try {
            SysOperationLog logEntity = new SysOperationLog();

            // 获取当前用户信息（token 只解析一次，避免用户名/ID/角色各解析一遍）
            String token = getBearerToken();
            String username = extractUsername(token);
            Long userId = extractUserId(token);

            logEntity.setUserId(userId);
            logEntity.setUserName(username);
            // 用户类型取 token 里的 roleCode，与前端 STUDENT/TEACHER/ADMIN 字典一致；
            // 不写则走库默认值 '未知用户'，操作日志页「用户类型」列会显示为空
            logEntity.setUserType(extractRoleCode(token));
            logEntity.setOperationType(operationLog.type());
            logEntity.setDescription(buildDescription(operationLog, joinPoint));

            // 记录参数（S15：脱敏 + 截断 + 文件类型只记元信息）
            if (operationLog.recordParams()) {
                String params = getMethodParams(joinPoint);
                logEntity.setRequestParams(params);
            }

            // 记录IP地址（S15：统一走 HttpIpUtils 信任链口径，防 XFF 伪造）
            logEntity.setIpAddress(getClientIpAddress());

            // 记录操作时间
            logEntity.setOperationTime(LocalDateTime.now());

            // 记录执行耗时（毫秒）——统计分析的「平均执行耗时」依赖此列
            logEntity.setExecutionTime(executeTime);

            // 记录操作结果状态——不显式写入会走库默认值 1，「失败操作」将恒为 0
            logEntity.setStatus(errorMsg == null ? 1 : 0);

            // S15：落库改为真异步后线程内无 SecurityContext/RequestContext，
            // MyMetaObjectHandler 兜底会填匿名管理员 ID——审计字段在移交前显式写入
            // （MP strictInsertFill 不覆盖非空字段，异步线程不会再回退填充）
            logEntity.setCreateTime(logEntity.getOperationTime());
            logEntity.setUpdateTime(logEntity.getOperationTime());
            if (userId != null && userId > 0) {
                logEntity.setCreateBy(userId);
                logEntity.setUpdateBy(userId);
            }

            // S15：真异步落库——此前注释写"异步"实为同步 insert，每次 @OperationLog
            // 请求都被 DB 写入阻塞。实体构建必须在请求线程（依赖 RequestContextHolder
            // 与 token），仅把 insert 挪进线程池；insert 失败只影响日志不影响业务
            CompletableFuture.runAsync(() -> {
                try {
                    sysOperationLogMapper.insert(logEntity);
                    log.info("操作日志记录成功: 用户={}, 操作={}, 耗时={}ms",
                            username, operationLog.type(), executeTime);
                } catch (Exception e) {
                    log.error("保存操作日志到数据库失败: {}", e.getMessage(), e);
                }
            }, asyncExecutor);

        } catch (Exception e) {
            log.error("构建操作日志失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从当前请求头取出 Bearer token（无则返回 null）
     */
    private String getBearerToken() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String header = request.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    return header.substring(7);
                }
            }
        } catch (Exception e) {
            log.warn("获取请求token失败: {}", e.getMessage());
        }
        return null;
    }

    private String extractUsername(String token) {
        if (token == null) {
            return "anonymous";
        }
        try {
            String username = jwtUtils.extractUsername(token);
            return username != null ? username : "anonymous";
        } catch (Exception e) {
            log.warn("从token解析用户名失败: {}", e.getMessage());
            return "anonymous";
        }
    }

    private Long extractUserId(String token) {
        if (token == null) {
            return 0L;
        }
        try {
            Long userId = jwtUtils.extractUserId(token);
            return userId != null ? userId : 0L;
        } catch (Exception e) {
            log.warn("从token解析用户ID失败: {}", e.getMessage());
            return 0L;
        }
    }

    private String extractRoleCode(String token) {
        if (token == null) {
            return null;
        }
        try {
            return jwtUtils.extractRoleCode(token);
        } catch (Exception e) {
            log.warn("从token解析角色编码失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * S15：委托 HttpIpUtils 统一取 IP——原实现盲信 XFF 首跳（客户端可伪造），
     * 现走「内网直连才信任代理头 + 右向左取合法 IP + 格式校验」信任链
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return HttpIpUtils.getRealIp(attributes.getRequest());
            }
        } catch (Exception e) {
            log.warn("获取客户端IP失败: {}", e.getMessage());
        }
        return "unknown";
    }

    private String getMethodParams(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();

            if (args.length == 0) {
                return "{}";
            }

            // 过滤掉HttpServletRequest、HttpServletResponse等不适合序列化的参数
            StringBuilder params = new StringBuilder("{");
            String[] paramNames = signature.getParameterNames();

            for (int i = 0; i < args.length; i++) {
                if (args[i] != null &&
                    !(args[i] instanceof HttpServletRequest) &&
                    !(args[i] instanceof HttpServletResponse)) {

                    if (params.length() > 1) params.append(",");

                    String paramName = paramNames != null && i < paramNames.length ? paramNames[i] : "param" + i;
                    params.append("\"").append(paramName).append("\":");

                    try {
                        // S15：上传文件只记元信息——直接序列化 MultipartFile 会把
                        // getBytes() 整个文件 base64 进操作日志
                        if (args[i] instanceof MultipartFile file) {
                            Map<String, Object> fileMeta = new LinkedHashMap<>();
                            fileMeta.put("fileName", file.getOriginalFilename());
                            fileMeta.put("size", file.getSize());
                            params.append(JSON.toJSONString(fileMeta));
                        } else {
                            params.append(JSON.toJSONString(args[i]));
                        }
                    } catch (Exception e) {
                        params.append("\"").append(args[i].toString()).append("\"");
                    }
                }
            }
            params.append("}");

            // S15：脱敏密码/令牌类字段（含 toString 兜底路径，Lombok @Data 的 toString 会带明文），
            // 再截断防大请求体撑爆日志列
            String masked = SENSITIVE_FIELD_PATTERN.matcher(params.toString()).replaceAll(MASK_REPLACEMENT);
            if (masked.length() > MAX_PARAM_LENGTH) {
                masked = masked.substring(0, MAX_PARAM_LENGTH) + "...(truncated)";
            }
            return masked;
        } catch (Exception e) {
            log.warn("获取方法参数失败: {}", e.getMessage());
            return "{}";
        }
    }

    private String buildDescription(OperationLog operationLog, ProceedingJoinPoint joinPoint) {
        if (!operationLog.description().isEmpty()) {
            return operationLog.description();
        }
        
        // 自动生成描述
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getName();
        String className = signature.getDeclaringType().getSimpleName();
        
        return String.format("%s.%s()", className, methodName);
    }
}