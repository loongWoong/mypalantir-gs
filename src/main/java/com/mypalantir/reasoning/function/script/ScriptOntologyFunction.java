package com.mypalantir.reasoning.function.script;

import com.mypalantir.reasoning.function.OntologyFunction;

import javax.script.ScriptException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 基于脚本实现的 OntologyFunction，委托 ScriptFunctionRunner 执行。
 * 脚本从 ontology 目录下 functions/script/ 加载（ontologyBaseDir 为当前本体文件所在目录）。
 */
public class ScriptOntologyFunction implements OntologyFunction {

    private final String name;
    private final String scriptPath;
    private final Path ontologyBaseDir;
    private final ScriptFunctionRunner runner;

    public ScriptOntologyFunction(String name, String scriptPath, Path ontologyBaseDir, ScriptFunctionRunner runner) {
        this.name = name;
        this.scriptPath = scriptPath != null && !scriptPath.isBlank() ? scriptPath : null;
        this.ontologyBaseDir = ontologyBaseDir;
        this.runner = runner != null ? runner : new ScriptFunctionRunner();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object execute(List<Object> args) {
        if (scriptPath == null) {
            throw new IllegalStateException("Script path not set for function: " + name);
        }
        try {
            return runner.execute(scriptPath, args, ontologyBaseDir);
        } catch (ScriptException e) {
            throw new RuntimeException("Script error in " + name + " (" + scriptPath + "): " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load script " + scriptPath + ": " + e.getMessage(), e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Script must define execute(args): " + scriptPath, e);
        }
    }
}
