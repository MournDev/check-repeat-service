package com.abin.checkrepeatservice.common.utils;


import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.DeviceType;
import eu.bitwalker.useragentutils.UserAgent;
import eu.bitwalker.useragentutils.OperatingSystem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 设备解析工具类：基于User-Agent识别设备信息
 */
public class UserAgentUtils {
    /**
     * 解析登录设备信息（格式：浏览器 - 操作系统 - 设备类型）
     */
    public static String parseDevice(HttpServletRequest request) {
        String userAgentStr = request.getHeader("User-Agent");
        if (StringUtils.isEmpty(userAgentStr)) {
            return "未知设备";
        }
        UserAgent userAgent = UserAgent.parseUserAgentString(userAgentStr);
        Browser browser = userAgent.getBrowser(); // 浏览器（如 Chrome、Edge）
        OperatingSystem os = userAgent.getOperatingSystem(); // 操作系统（如 Windows 10、iOS）
        DeviceType deviceType = userAgent.getOperatingSystem().getDeviceType(); // 设备类型（PC、MOBILE）

        return String.format("%s - %s - %s", browser.getName(), os.getName(), deviceType.getName());
    }
}
