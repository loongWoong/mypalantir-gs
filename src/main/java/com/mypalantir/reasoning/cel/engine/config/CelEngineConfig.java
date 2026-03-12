package com.mypalantir.reasoning.cel.engine.config;

import com.mypalantir.reasoning.cel.engine.CelFunctionRegistry;
import com.mypalantir.reasoning.cel.engine.CelRuntimeFactory;
import com.mypalantir.reasoning.cel.engine.CelStandardLibrary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CelEngineConfig {

    @Bean
    public CelRuntimeFactory celRuntimeFactory(CelStandardLibrary standardLibrary, CelFunctionRegistry registry) {
        return new CelRuntimeFactory(standardLibrary, registry);
    }
}
