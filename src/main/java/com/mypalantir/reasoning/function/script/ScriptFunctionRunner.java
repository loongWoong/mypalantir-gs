package com.mypalantir.reasoning.function.script;

import com.mypalantir.reasoning.ReasoningLogger;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
        logScriptCall(scriptPath, args);
        ScriptEngine engine = getOrCreateEngine(scriptPath);
        engine.eval(content);
        Invocable inv = (Invocable) engine;
        Object result;
        try {
            result = inv.invokeFunction("run", args != null ? args.toArray() : new Object[0]);
        } catch (NoSuchMethodException e1) {
            result = inv.invokeFunction("execute", args != null ? args : List.of());
        }
        logScriptResult(scriptPath, result);
        return result;
    }

    /**
     * 脚本调用前打印入参（控制台 + logs/Reasoning.log）
     */
    private void logScriptCall(String scriptPath, List<Object> args) {
        String msg = "[ScriptFunction] call " + scriptPath + " | args=" + safeToString(args);
        System.out.println(msg);
        try (ReasoningLogger log = ReasoningLogger.open()) {
            log.line(msg);
            log.flush();
        } catch (Exception ignored) {
            // best-effort logging only
        }
    }

    private String safeToString(Object obj) {
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        return safeToString(obj, visited, 0);
    }

    private void logScriptResult(String scriptPath, Object result) {
        String msg = "[ScriptFunction] result " + scriptPath + " => " + safeToString(result);
        System.out.println(msg);
        try (ReasoningLogger log = ReasoningLogger.open()) {
            log.line(msg);
            log.flush();
        } catch (Exception ignored) {
            // best-effort logging only
        }
    }

    private String safeToString(Object obj, IdentityHashMap<Object, Boolean> visited, int depth) {
        if (obj == null) return "null";
        if (depth > 4) return "<max-depth:" + obj.getClass().getSimpleName() + ">";
        if (visited.containsKey(obj)) return "<cycle:" + obj.getClass().getSimpleName() + ">";

        if (obj instanceof String s) {
            return "\"" + truncate(s, 300).replace("\n", "\\n").replace("\r", "\\r") + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) return String.valueOf(obj);

        // JavaScript engine types (e.g., ScriptObjectMirror) often have noisy toString; keep it short.
        String simpleName = obj.getClass().getSimpleName();
        if ("ScriptObjectMirror".equals(simpleName)) {
            return "<ScriptObjectMirror:" + truncate(String.valueOf(obj), 300) + ">";
        }

        if (obj instanceof Map<?, ?> m) {
            visited.put(obj, true);
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (i++ >= 30) {
                    sb.append("...(").append(m.size()).append(" entries)");
                    break;
                }
                if (i > 1) sb.append(", ");
                sb.append(safeToString(e.getKey(), visited, depth + 1))
                  .append(": ")
                  .append(safeToString(e.getValue(), visited, depth + 1));
            }
            sb.append("}");
            visited.remove(obj);
            return sb.toString();
        }

        if (obj instanceof List<?> list) {
            visited.put(obj, true);
            List<String> parts = new ArrayList<>();
            int max = Math.min(list.size(), 50);
            for (int i = 0; i < max; i++) {
                parts.add(safeToString(list.get(i), visited, depth + 1));
            }
            if (list.size() > max) parts.add("...(" + list.size() + " items)");
            visited.remove(obj);
            return "[" + String.join(", ", parts) + "]";
        }

        String s = String.valueOf(obj);
        return truncate(s, 500);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, Math.max(0, maxLen - 3)) + "...";
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
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            ScriptEngineManager manager = new ScriptEngineManager(cl != null ? cl : getClass().getClassLoader());
            ScriptEngine engine = manager.getEngineByName(JS_ENGINE_NAME);
            if (engine == null) engine = manager.getEngineByName("nashorn");
            if (engine == null) engine = manager.getEngineByName("Nashorn");
            if (engine == null) engine = manager.getEngineByExtension("js");
            if (engine == null) {
                throw new IllegalStateException("No JavaScript script engine available (add org.openjdk.nashorn:nashorn-core dependency)");
            }
            return engine;
        });
    }
}
