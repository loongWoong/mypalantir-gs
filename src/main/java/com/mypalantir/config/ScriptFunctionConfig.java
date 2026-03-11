package com.mypalantir.config;

import com.mypalantir.reasoning.function.script.ScriptFunctionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 脚本函数执行器配置。
 * 脚本优先从当前本体所在目录下的 functions/script/ 加载，其次从类路径 resources/functions/script/ 加载。
 */
@Configuration
public class ScriptFunctionConfig {

    @Bean
    public ScriptFunctionRunner scriptFunctionRunner() {
        return new ScriptFunctionRunner();
    }
}
