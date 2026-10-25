package com.abin.checkrepeatservice.common.config;

import com.abin.checkrepeatservice.common.component.MinioProp;
import io.minio.MinioClient;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MinioConfiguration {

    @Autowired
    private MinioProp minioProp;

    /**
     * 外部依赖超时（H10）：MinIO SDK 默认 OkHttp 无业务级超时约束，
     * 存储抖动时会把全库逐篇读取的查重线程长时间挂住。
     */
    @Value("${minio.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${minio.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Value("${minio.write-timeout-ms:10000}")
    private int writeTimeoutMs;

    @Value("${minio.max-idle-connections:32}")
    private int maxIdleConnections;

    @Bean
    public MinioClient minioClient() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
                // 单次调用总超时：避免连接/读取分别达标但整体无限拖长
                .callTimeout(connectTimeoutMs + readTimeoutMs, TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(maxIdleConnections, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build();

        return new MinioClient.Builder()
                .endpoint(minioProp.getEndpoint())
                .credentials(minioProp.getAccessKey(), minioProp.getSecretKey())
                .region("us-east-1")
                .httpClient(httpClient)
                .build();
    }
}
