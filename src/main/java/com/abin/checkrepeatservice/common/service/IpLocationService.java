package com.abin.checkrepeatservice.common.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.ConfigBuilder;
import org.lionsoul.ip2region.service.Ip2Region;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;

/**
 * 登录 IP 属地解析（离线库 ip2region）。
 *
 * <p>之前这里调的是第三方接口 https://api.vvhan.com/api/ip，那个域名已经不存在了
 * （2026-10 实测阿里 DNS / Google DNS 都是 NXDOMAIN），于是每次调用都抛
 * UnknownHostException、被 catch 吞掉返回「未知地点」，登录日志里的地点就一直是未知。
 * 换成离线库后：不依赖外网、不限次数、单次查询是内存查表（微秒级），
 * 也不用再担心第三方接口挂掉会拖慢登录（旧实现没设超时，是同步调用在登录路径上的）。</p>
 *
 * <p>数据文件 {@code resources/ip2region/ip2region_v4.xdb} 随包发布（约 11MB），
 * 启动时全量读进内存。</p>
 *
 * <p>注意库里只有 IPv4：纯 IPv6 的客户端 IP 查不出结果，会回退成「未知地点」。
 * 若以后域名加了 AAAA 记录、真有 IPv6 客户端，需要再放一份 ip2region_v6.xdb
 * 并在这里补一个 {@code ConfigBuilder().asV6()} 的配置传给
 * {@code Ip2Region.create(v4Config, v6Config)}（IPv4-mapped 的 ::ffff:x.x.x.x 现在就能解析）。</p>
 */
@Slf4j
@Service
public class IpLocationService {

    /** 与 resources 目录下的相对路径一致 */
    private static final String XDB_RESOURCE = "ip2region/ip2region_v4.xdb";

    /** 内网地址在 xdb 里统一标成 Reserved */
    private static final String RESERVED = "Reserved";
    private static final String LOCAL_NETWORK = "本地网络";
    private static final String UNKNOWN = "未知地点";

    /** 加载失败时为 null，所有查询走回退值，不影响登录 */
    private Ip2Region ip2Region;

    @PostConstruct
    private void init() {
        try (InputStream in = new ClassPathResource(XDB_RESOURCE).getInputStream()) {
            Config v4Config = new ConfigBuilder()
                    .setXdbInputStream(in)
                    .setCachePolicy(Config.BufferCache)   // 全量进内存，检索不落磁盘 IO
                    .asV4();
            // v6 传 null：没有 v6 数据文件时纯 IPv6 查询返回 null，不会抛异常
            this.ip2Region = Ip2Region.create(v4Config, null);
            log.info("IP 属地库加载成功：{}（{} 个检索器，全内存缓存）", XDB_RESOURCE, v4Config.searchers);
        } catch (Exception e) {
            log.error("IP 属地库加载失败，登录地点将统一记为「{}」：{}", UNKNOWN, e.getMessage(), e);
        }
    }

    /**
     * 根据 IP 解析属地，格式：中国 → {@code 省-市}，境外 → {@code 国家-省/市}。
     *
     * <p>任何失败都返回「未知地点」而不是抛异常：登录日志记不上地点不该拦登录。</p>
     */
    public String getLocationByIp(String ip) {
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            return UNKNOWN;
        }
        // IPv6 的本地回环：v4 库认不出来，但本机自测/反代走 IPv6 时会碰到
        if ("::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return LOCAL_NETWORK;
        }
        if (ip2Region == null) {
            return UNKNOWN;
        }
        try {
            // 返回格式固定为 国家|区域|省份|城市|ISP，例：中国|江苏省|南京市|0|CN
            String region = ip2Region.search(ip.trim());
            if (!StringUtils.hasText(region) || "null".equals(region)) {
                // 纯 IPv6（当前没有 v6 数据文件）走这里
                return UNKNOWN;
            }
            return formatRegion(region.split("\\|"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return UNKNOWN;
        } catch (Exception e) {
            // 参数不是合法 IP（xdb 抛 InetAddressException）等
            log.debug("IP 属地解析失败，ip={}，原因={}", ip, e.getMessage());
            return UNKNOWN;
        }
    }

    /**
     * 把 xdb 的五段结果拼成展示用的地点。
     *
     * <p>字段顺序是 {@code 国家|省份|城市|ISP|国家代码}（实测：
     * {@code 中国|江苏省|南京市|0|CN}、{@code 223.5.5.5 → 中国|浙江省|杭州市|阿里|CN}）——
     * 注意 ISP 在第 4 段、国家代码在第 5 段，别按「国家|区域|省份|城市|ISP」去取，
     * 那样会把 ISP 当成城市拼出「杭州市-阿里」这种结果。</p>
     *
     * <p>国内取「省-市」，省和市同名时（直辖市，如北京市|北京市）只留一个；
     * 境外用「国家-城市」，没有城市就用省/州，都没有就只显示国家。</p>
     */
    private String formatRegion(String[] parts) {
        if (parts.length < 5) {
            return UNKNOWN;
        }
        String country = parts[0].trim();
        String province = parts[1].trim();
        String city = parts[2].trim();

        if (RESERVED.equals(country)) {
            // 127.0.0.0/8、192.168.x、10.x、172.16-31.x 等内网段，xdb 都标成 Reserved
            return LOCAL_NETWORK;
        }
        if (!StringUtils.hasText(country) || "0".equals(country)) {
            return UNKNOWN;
        }
        // 中国：省-市（同名只留一个，省和市都为空则只显示国家）
        if ("中国".equals(country)) {
            if (!StringUtils.hasText(province) || "0".equals(province)) {
                return country;
            }
            if (!StringUtils.hasText(city) || "0".equals(city) || city.equals(province)) {
                return province;
            }
            return province + "-" + city;
        }
        // 境外：国家-城市，没有城市就用省/州，都没有就只显示国家
        String sub = (!StringUtils.hasText(city) || "0".equals(city)) ? province : city;
        if (!StringUtils.hasText(sub) || "0".equals(sub)) {
            return country;
        }
        return country + "-" + sub;
    }
}
