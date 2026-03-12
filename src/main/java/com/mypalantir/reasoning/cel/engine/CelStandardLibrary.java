package com.mypalantir.reasoning.cel.engine;

import dev.cel.compiler.CelCompilerBuilder;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeFactory;
import org.springframework.stereotype.Component;

/**
 * CEL 标准库：提供标准宏与运行时的 Builder。
 */
@Component
public class CelStandardLibrary {

    public CelCompilerBuilder newCompilerBuilder() {
        return CelCompilerFactory.standardCelCompilerBuilder()
                .setStandardMacros(CelStandardMacro.STANDARD_MACROS);
    }

    public CelRuntimeBuilder newRuntimeBuilder() {
        return CelRuntimeFactory.standardCelRuntimeBuilder();
    }
}
