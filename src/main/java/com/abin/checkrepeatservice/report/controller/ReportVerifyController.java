package com.abin.checkrepeatservice.report.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.report.dto.ReportVerifyVO;
import com.abin.checkrepeatservice.report.service.ReportVerifyService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 报告验真公开接口（论文全生命周期 P5）
 *
 * 路径已在 SecurityConfig permitAll 放行（对齐行业报告验真惯例：凭报告编号匿名验真）。
 * 安全边界：
 * - 只回元数据（标题打码/三项指标/时间），绝不回报告文件与论文正文；
 * - 按 IP 限流（60 次/小时，Redis 计数），防报告编号遍历枚举；
 * - Redis 不可用时放行（验真是只读低敏操作，可用性优先）。
 */
@RestController
@RequestMapping("/api/report/verify")
@Slf4j
public class ReportVerifyController {

    /**
     * 单 IP 每小时最大验真次数
     */
    private static final int RATE_LIMIT_PER_HOUR = 60;

    private static final String RATE_LIMIT_KEY_PREFIX = "p5:report-verify:";

    @Resource
    private ReportVerifyService reportVerifyService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 按报告编号验真（公开，无需登录）
     */
    @GetMapping("/{reportNo}")
    public Result<ReportVerifyVO> verify(@PathVariable("reportNo") String reportNo,
                                         HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        checkRateLimit(clientIp);
        return Result.success(reportVerifyService.verify(reportNo));
    }

    /**
     * IP 限流：固定窗口 1 小时 60 次，首次计数时设置过期
     */
    private void checkRateLimit(String clientIp) {
        try {
            String key = RATE_LIMIT_KEY_PREFIX + clientIp;
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, Duration.ofHours(1));
            }
            if (count != null && count > RATE_LIMIT_PER_HOUR) {
                log.warn("报告验真接口触发限流 - IP: {}, 次数: {}", clientIp, count);
                throw new com.abin.checkrepeatservice.common.Exception.BusinessException(
                        com.abin.checkrepeatservice.common.enums.ResultCode.SYSTEM_ERROR,
                        "验真请求过于频繁，请 1 小时后再试");
            }
        } catch (com.abin.checkrepeatservice.common.Exception.BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Redis 异常降级：放行（验真为只读低敏操作）
            log.warn("报告验真限流计数失败，降级放行 - IP: {}: {}", clientIp, e.getMessage());
        }
    }

    /**
     * 解析客户端 IP：优先取代理链首跳（X-Forwarded-For），兜底 remoteAddr
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
