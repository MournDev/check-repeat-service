package com.abin.checkrepeatservice.common.utils;

import com.alibaba.fastjson.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * IP属地查询工具类（基于第三方接口，免费版有调用限制，生产环境建议替换为付费接口）
 */
public class IpLocationUtils {
    // 免费IP属地查询接口（示例：纯真IP接口，返回省/市）
    private static final String IP_LOCATION_URL = "https://api.vvhan.com/api/ip?ip=%s";
    private static final RestTemplate restTemplate = new RestTemplate();

    /**
     * 根据IP查询属地（格式：省-市，如 广东省-深圳市）
     */
    public static String getLocationByIp(String ip) {
        // 本地IP直接返回"本地网络"
        if ("127.0.0.1".equals(ip) || ip.startsWith("192.168.") || ip.startsWith("10.")) {
            return "本地网络";
        }
        try {
            String url = String.format(IP_LOCATION_URL, ip);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JSONObject resultJson = JSONObject.parseObject(response.getBody());

            // 解析响应结果（不同接口格式不同，需根据实际接口调整）
            if (resultJson.getInteger("success") == 1) {
                JSONObject data = resultJson.getJSONObject("data");
                String province = data.getString("province");
                String city = data.getString("city");
                return String.format("%s-%s", province, city);
            }
        } catch (Exception e) {
            // 接口调用失败时返回"未知地点"，不影响登录流程
            return "未知地点";
        }
        return "未知地点";
    }
}
