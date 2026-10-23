package com.abin.checkrepeatservice.common.event;

/**
 * 安全配置（security_config）变更事件。
 *
 * <p>后台保存安全配置或手动刷新配置时发布，缓存了安全策略的服务监听后立即失效，
 * 不必等各自的短 TTL 自然过期。</p>
 *
 * @param configKey 发生变更的配置键（当前固定为 security_config）
 */
public record SecurityConfigChangedEvent(String configKey) {
}
