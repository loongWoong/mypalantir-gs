package com.mypalantir.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    // 使用默认的 Jackson 配置
    // 字段上的 @JsonProperty 注解会控制 JSON 序列化时的字段名
    // getter 上的 @JsonIgnore 会防止 Jackson 使用 getter 方法名
}

