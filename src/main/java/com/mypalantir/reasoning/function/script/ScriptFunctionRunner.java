package com.mypalantir.reasoning.function.script;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从存储路径加载并执行脚本函数（JavaScript）。
 * 脚本约定：定义 execute(args) 函数，args 为参数列表，返回结果。
 * 优先从 ontology 目录下 functions/script/ 加载，其次从类路径 resources/functions/script/ 加载。
 */
public class ScriptFunctionRunner {

    private static final String SCRIPT_SUBDIR = "functions/script";
    private static final String SCRIPT_CLASSPATH_PREFIX = "functions/script/";
    private static final String JS_ENGINE_NAME = "javascript";

    private final ConcurrentHashMap<String, ScriptEngine> engineCache = new ConcurrentHashMap<>();

    public ScriptFunctionRunner() {
    }

    /**
     * 执行脚本：先加载脚本内容，再调用其中的 execute(args)。
     *
     * @param scriptPath      相对路径，如 toll/sample_check.js
     * @param args            参数列表（会传入脚本）
     * @param ontologyBaseDir 当前本体所在目录（如 ontology/toll.yaml 则传 ontology 的 Path），可为 null
     * @return 脚本 execute(args) 的返回值
     */
    public Object execute(String scriptPath, List<Object> args, Path ontologyBaseDir) throws ScriptException, IOException, NoSuchMethodException {
        String content = loadScriptContent(scriptPath, ontologyBaseDir);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Script not found or empty: " + scriptPath);
        }
        ScriptEngine engine = getOrCreateEngine(scriptPath);
        engine.eval(content);
        Invocable inv = (Invocable) engine;
        Object result = inv.invokeFunction("execute", args != null ? args : List.of());
        return result;
    }

    /**
     * 加载脚本内容：优先 ontology 目录下 functions/script/，其次类路径。
     */
    private String loadScriptContent(String scriptPath, Path ontologyBaseDir) throws IOException {
        if (scriptPath == null || scriptPath.isBlank()) return null;
        String normalized = scriptPath.replace('\\', '/').trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);

        // 1) 优先从 ontology 目录下 functions/script/ 加载
        if (ontologyBaseDir != null && Files.isDirectory(ontologyBaseDir)) {
            Path file = ontologyBaseDir.resolve(SCRIPT_SUBDIR).resolve(normalized);
            if (Files.isRegularFile(file)) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
        }

        // 2) 回退到类路径 resources/functions/script/...
        String classpathResource = SCRIPT_CLASSPATH_PREFIX + normalized;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        return null;
    }

    private ScriptEngine getOrCreateEngine(String scriptPath) {
        return engineCache.computeIfAbsent(scriptPath, k -> {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName(JS_ENGINE_NAME);
            if (engine == null) {
                engine = manager.getEngineByExtension("js");
            }
            if (engine == null) {
                throw new IllegalStateException("No JavaScript script engine available (add nashorn or graaljs dependency)");
            }
            return engine;
        });
    }
}
