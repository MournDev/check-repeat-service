package com.abin.checkrepeatservice.common.aspect;

import com.abin.checkrepeatservice.admin.service.SysOperationLogService;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.SysOperationLog;
import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.pojo.entity.SysOperationLog;
import com.abin.checkrepeatservice.admin.mapper.SysOperationLogMapper;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

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
            
            // 获取当前用户信息
            String username = getCurrentUsername();
            Long userId = getCurrentUserId();
            
            logEntity.setUserId(userId);
            logEntity.setUserName(username);
            logEntity.setOperationType(operationLog.type());
            logEntity.setDescription(buildDescription(operationLog, joinPoint));
            
            // 记录参数
            if (operationLog.recordParams()) {
                String params = getMethodParams(joinPoint);
                logEntity.setRequestParams(params);
            }
            
            // 记录IP地址
            logEntity.setIpAddress(getClientIpAddress());
            
            // 记录执行时间
            logEntity.setOperationTime(LocalDateTime.now());
            
            // 保存到数据库
            sysOperationLogMapper.insert(logEntity);
            
            log.info("操作日志记录成功: 用户={}, 操作={}, 耗时={}ms", 
                    username, operationLog.type(), executeTime);
                    
        } catch (Exception e) {
            log.error("保存操作日志到数据库失败: {}", e.getMessage(), e);
        }
    }

    private String getCurrentUsername() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String token = request.getHeader("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                    return jwtUtils.extractUsername(token);
                }
            }
        } catch (Exception e) {
            log.warn("获取当前用户名失败: {}", e.getMessage());
        }
        return "anonymous";
    }

    private Long getCurrentUserId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String token = request.getHeader("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                    return jwtUtils.extractUserId(token);
                }
            }
        } catch (Exception e) {
            log.warn("获取当前用户ID失败: {}", e.getMessage());
        }
        return 0L;
    }

    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                // 处理多个IP的情况
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
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
                        params.append(JSON.toJSONString(args[i]));
                    } catch (Exception e) {
                        params.append("\"").append(args[i].toString()).append("\"");
                    }
                }
            }
            params.append("}");
            
            return params.toString();
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