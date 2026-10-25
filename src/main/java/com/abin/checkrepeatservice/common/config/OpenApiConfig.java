package com.abin.checkrepeatservice.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置。
 *
 * <p>访问地址（项目 context-path 为 /check）：</p>
 * <ul>
 *   <li>Swagger UI：/check/swagger-ui/index.html</li>
 *   <li>OpenAPI JSON：/check/v3/api-docs</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    /** 安全方案名称：必须与全局 SecurityRequirement 中引用的名称完全一致 */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI checkRepeatOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("论文查重管理系统 API")
                        .version("1.0.0")
                        .description("论文查重管理系统后端接口文档，供前后端联调与项目交付使用。"
                                + "除登录、注册等匿名接口外，其余接口均需先调用登录接口获取 JWT，"
                                + "再点击右上角 Authorize 填入该 token。"))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .description("填入登录接口返回的 token（无需手动添加 Bearer 前缀）")))
                // 全局应用：所有接口默认展示锁图标，并自动携带 Authorization: Bearer <token>
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
