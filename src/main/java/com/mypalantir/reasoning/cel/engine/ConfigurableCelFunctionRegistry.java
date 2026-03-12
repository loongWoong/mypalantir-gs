package com.mypalantir.reasoning.cel.engine;

import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可配置的 CEL 函数注册表实现。
 * 支持同一函数名多个重载（不同参数个数）。
 */
@Component
public class ConfigurableCelFunctionRegistry implements CelFunctionRegistry {

    /** name -> list of overloads (each overload has its own overloadId) */
    private final Map<String, List<CelFunction>> functionsByName = new ConcurrentHashMap<>();

    @Override
    public void register(String name, CelFunction function) {
        functionsByName.computeIfAbsent(name, k -> new ArrayList<>()).clear();
        functionsByName.get(name).add(function);
    }

    @Override
    public void registerOverload(String name, CelFunction overload) {
        functionsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(overload);
    }

    @Override
    public Collection<CelFunctionDecl> getFunctionDeclarations() {
        List<CelFunctionDecl> decls = new ArrayList<>();
        for (Map.Entry<String, List<CelFunction>> e : functionsByName.entrySet()) {
            List<CelFunction> overloads = e.getValue();
            if (overloads.isEmpty()) continue;
            List<CelOverloadDecl> overloadDecls = new ArrayList<>();
            for (CelFunction fn : overloads) {
                CelFunctionMetadata m = fn.getMetadata();
                overloadDecls.add(CelOverloadDecl.newMemberOverload(
                        m.getOverloadId(),
                        m.getDoc() != null ? m.getDoc() : "",
                        m.getResultType(),
                        m.getParameterTypes().toArray(new dev.cel.common.types.CelType[0])));
            }
            CelFunctionMetadata first = overloads.get(0).getMetadata();
            decls.add(CelFunctionDecl.newFunctionDeclaration(
                    first.getCelName(),
                    overloadDecls.toArray(new CelOverloadDecl[0])));
        }
        return decls;
    }

    @Override
    public void addFunctionBindings(CelRuntimeBuilder runtimeBuilder) {
        for (List<CelFunction> overloads : functionsByName.values()) {
            for (CelFunction fn : overloads) {
                String overloadId = fn.getMetadata().getOverloadId();
                runtimeBuilder.addFunctionBindings(
                        CelRuntime.CelFunctionBinding.from(
                                overloadId,
                                Collections.singletonList(Object.class),
                                args -> fn.evaluate(args, CelEvalContext.getCurrent())));
            }
        }
    }
}
