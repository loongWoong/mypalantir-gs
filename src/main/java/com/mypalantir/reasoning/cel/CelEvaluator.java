package com.mypalantir.reasoning.cel;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 CEL 表达式求值器。
 * 支持当前加载本体中使用的 CEL 子集：
 * - size(collection)
 * - collection.map(v, expr).sum()
 * - 简单等值比较 a == b
 * - 字段访问 links.linkName
 * - double(x) 转换
 * - 自定义函数调用：当注入 CelFunctionAdapter 时，支持 funcName(arg1, arg2, ...) 并委托 FunctionRegistry。
 */
public class CelEvaluator {

    private static final Set<String> BUILTIN_CEL_NAMES = Set.of("size", "double");

    private final CelFunctionAdapter functionAdapter;

    /** 无注入时仅支持内置 CEL 语法，不解析自定义函数调用。 */
    public CelEvaluator() {
        this(null);
    }

    /** 注入 adapter 后，表达式中可调用 FunctionRegistry 已注册函数。 */
    public CelEvaluator(CelFunctionAdapter functionAdapter) {
        this.functionAdapter = functionAdapter;
    }

    /**
     * 对 CEL 表达式求值
     *
     * @param expr        CEL 表达式字符串
     * @param properties  实例属性 (propName → value)
     * @param linkedData  关联数据 (linkName → List<Map>)
     * @return 计算结果
     */
    public Object evaluate(String expr, Map<String, Object> properties,
                            Map<String, List<Map<String, Object>>> linkedData) {
        // 将换行符替换为空格，并合并多余空格
        expr = expr.trim().replaceAll("\\s+", " ");

        // 等值比较优先（包含 == 的表达式先拆分再求值子表达式）
        // 使用 contains 检查避免 == 被 map/sum 模式吞掉
        if (expr.contains(" == ")) {
            Matcher eqMatcher = EQUALS_PATTERN.matcher(expr);
            if (eqMatcher.matches()) {
                String left = eqMatcher.group(1).trim();
                String right = eqMatcher.group(2).trim();
                Object leftVal = evaluateSimple(left, properties, linkedData);
                Object rightVal = evaluateSimple(right, properties, linkedData);
                return Objects.equals(leftVal, rightVal);
            }
        }

        // size(links.xxx) — 精确匹配整个表达式
        Matcher sizeMatcher = SIZE_PATTERN.matcher(expr);
        if (sizeMatcher.matches()) {
            String path = sizeMatcher.group(1);
            Object collection = resolvePath(path, properties, linkedData);
            if (collection instanceof List) return ((List<?>) collection).size();
            return 0;
        }

        // links.xxx.map(v, expr).sum()
        Matcher mapSumMatcher = MAP_SUM_PATTERN.matcher(expr);
        if (mapSumMatcher.matches()) {
            String collectionPath = mapSumMatcher.group(1);
            String varName = mapSumMatcher.group(2);
            String mapExpr = mapSumMatcher.group(3);

            Object collection = resolvePath(collectionPath, properties, linkedData);
            if (!(collection instanceof List<?> list)) return 0.0;

            double sum = 0.0;
            for (Object item : list) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemMap = (Map<String, Object>) item;
                    Object val = evaluateMapExpr(mapExpr, varName, itemMap);
                    sum += toDouble(val);
                }
            }
            return sum;
        }

        // links.xxx.map(v, expr).sort()
        Matcher mapSortMatcher = MAP_SORT_PATTERN.matcher(expr);
        if (mapSortMatcher.matches()) {
            return evaluateMapSort(mapSortMatcher.group(1), mapSortMatcher.group(2),
                mapSortMatcher.group(3), properties, linkedData);
        }

        // 自定义函数调用：funcName(arg1, arg2, ...) 委托 FunctionRegistry（支持参数内括号）
        if (functionAdapter != null) {
            String[] nameAndArgs = parseFunctionCall(expr);
            if (nameAndArgs != null) {
                String name = nameAndArgs[0];
                String argsStr = nameAndArgs[1];
                if (!BUILTIN_CEL_NAMES.contains(name) && functionAdapter.hasFunction(name)) {
                    List<String> argExprs = splitArgsByComma(argsStr);
                    List<Object> evaluated = new ArrayList<>();
                    for (String argExpr : argExprs) {
                        evaluated.add(evaluateSimple(argExpr.trim(), properties, linkedData));
                    }
                    return functionAdapter.call(name, evaluated);
                }
            }
        }

        // 简单路径解析
        return evaluateSimple(expr, properties, linkedData);
    }

    /**
     * 解析函数调用形式 name(args)，返回 [name, argsStr]；若不是合法调用则返回 null。
     * 正确匹配配对括号，使参数中可含 size(links.x) 等。
     */
    private static String[] parseFunctionCall(String expr) {
        expr = expr.trim();
        int open = expr.indexOf('(');
        if (open <= 0 || !expr.endsWith(")")) return null;
        String name = expr.substring(0, open).trim();
        if (!IDENTIFIER_PATTERN.matcher(name).matches()) return null;
        int depth = 1;
        for (int i = open + 1; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(' || c == '（') depth++;
            else if (c == ')' || c == '）') {
                depth--;
                if (depth == 0) {
                    String argsStr = expr.substring(open + 1, i).trim();
                    return new String[]{name, argsStr};
                }
            }
        }
        return null;
    }

    /**
     * 按顶层逗号分割参数串，不分割括号内的逗号。
     */
    private static List<String> splitArgsByComma(String argsStr) {
        if (argsStr.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '(' || c == '（') depth++;
            else if (c == ')' || c == '）') depth--;
            else if ((c == ',' || c == '，') && depth == 0) {
                out.add(cur.toString().trim());
                cur = new StringBuilder();
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static final Pattern SIZE_PATTERN = Pattern.compile("^size\\(([^)]+)\\)$");
    private static final Pattern MAP_SUM_PATTERN = Pattern.compile("(.+)\\.map\\(([^,]+),\\s*(.+)\\)\\.sum\\(\\)");
    private static final Pattern MAP_SORT_PATTERN = Pattern.compile("(.+)\\.map\\(([^,]+),\\s*(.+)\\)\\.sort\\(\\)");
    private static final Pattern EQUALS_PATTERN = Pattern.compile("(.+?)\\s*==\\s*(.+)");
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("double\\(([^)]+)\\)");
    /** 自定义函数名：标识符 */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * 解析路径表达式
     */
    private Object resolvePath(String path, Map<String, Object> properties,
                                Map<String, List<Map<String, Object>>> linkedData) {
        path = path.trim();

        // links.linkName
        if (path.startsWith("links.")) {
            String linkName = path.substring(6);
            return linkedData != null ? linkedData.getOrDefault(linkName, List.of()) : List.of();
        }

        // 直接属性
        return properties.get(path);
    }

    /**
     * 求值 map 表达式内的子表达式
     * 如: double(d.fee)
     */
    private Object evaluateMapExpr(String expr, String varName, Map<String, Object> item) {
        expr = expr.trim();

        // double(var.field)
        Matcher doubleMatcher = DOUBLE_PATTERN.matcher(expr);
        if (doubleMatcher.matches()) {
            String inner = doubleMatcher.group(1).trim();
            Object val = resolveVarField(inner, varName, item);
            return toDouble(val);
        }

        // var.field
        return resolveVarField(expr, varName, item);
    }

    /**
     * 解析 var.field 形式
     */
    private Object resolveVarField(String expr, String varName, Map<String, Object> item) {
        if (expr.startsWith(varName + ".")) {
            String field = expr.substring(varName.length() + 1);
            return item.get(field);
        }
        return item.get(expr);
    }

    /**
     * 求值简单表达式（字面值或路径）
     */
    private Object evaluateSimple(String expr, Map<String, Object> properties,
                                   Map<String, List<Map<String, Object>>> linkedData) {
        expr = expr.trim();
        if ("true".equals(expr)) return Boolean.TRUE;
        if ("false".equals(expr)) return Boolean.FALSE;
        if (expr.startsWith("\"") && expr.endsWith("\"")) return expr.substring(1, expr.length() - 1);
        try { return Integer.parseInt(expr); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(expr); } catch (NumberFormatException ignored) {}

        // size() 嵌套
        Matcher sizeMatcher = SIZE_PATTERN.matcher(expr);
        if (sizeMatcher.matches()) {
            Object collection = resolvePath(sizeMatcher.group(1), properties, linkedData);
            if (collection instanceof List) return ((List<?>) collection).size();
            return 0;
        }

        // map(...).sum()
        Matcher mapSumMatcher = MAP_SUM_PATTERN.matcher(expr);
        if (mapSumMatcher.matches()) {
            Object collection = resolvePath(mapSumMatcher.group(1), properties, linkedData);
            if (!(collection instanceof List<?> list)) return 0.0;
            double sum = 0.0;
            for (Object item : list) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemMap = (Map<String, Object>) item;
                    sum += toDouble(evaluateMapExpr(mapSumMatcher.group(3), mapSumMatcher.group(2), itemMap));
                }
            }
            return sum;
        }

        // map(...).sort()
        Matcher mapSortMatcher = MAP_SORT_PATTERN.matcher(expr);
        if (mapSortMatcher.matches()) {
            return evaluateMapSort(mapSortMatcher.group(1), mapSortMatcher.group(2),
                mapSortMatcher.group(3), properties, linkedData);
        }

        // 嵌套的自定义函数调用
        if (functionAdapter != null) {
            String[] nameAndArgs = parseFunctionCall(expr);
            if (nameAndArgs != null) {
                String name = nameAndArgs[0];
                String argsStr = nameAndArgs[1];
                if (!BUILTIN_CEL_NAMES.contains(name) && functionAdapter.hasFunction(name)) {
                    List<String> argExprs = splitArgsByComma(argsStr);
                    List<Object> evaluated = new ArrayList<>();
                    for (String argExpr : argExprs) {
                        evaluated.add(evaluateSimple(argExpr.trim(), properties, linkedData));
                    }
                    return functionAdapter.call(name, evaluated);
                }
            }
        }

        return resolvePath(expr, properties, linkedData);
    }

    /**
     * 求值 map(...).sort() 表达式
     */
    private List<String> evaluateMapSort(String collectionPath, String varName, String mapExpr,
                                          Map<String, Object> properties,
                                          Map<String, List<Map<String, Object>>> linkedData) {
        Object collection = resolvePath(collectionPath, properties, linkedData);
        if (!(collection instanceof List<?> list)) return List.of();

        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemMap = (Map<String, Object>) item;
                Object val = evaluateMapExpr(mapExpr, varName, itemMap);
                result.add(val != null ? val.toString() : "");
            }
        }
        Collections.sort(result);
        return result;
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (NumberFormatException e) { return 0.0; }
    }
}
