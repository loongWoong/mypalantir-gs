package com.mypalantir.reasoning.swrl;

import com.mypalantir.meta.OntologySchema;
import com.mypalantir.meta.Rule;
import com.mypalantir.reasoning.function.FunctionRegistry;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SWRL 表达式解析器。
 * 将当前加载本体（schema）中的 SWRL 字符串解析为可执行的 SWRLRule 结构。
 *
 * 支持的语法：
 * - 类型断言: Type(?var)
 * - 属性匹配: prop(?var, value)  prop(?var, ?var2)
 * - 函数调用: func(args) == value
 * - links表达式: links(?var, linkName) 作为函数参数
 * - 析取: (atom1 ∨ atom2)
 * - 不等式: (?var != "value")
 * - 前后件分隔: →
 * - 合取: ∧
 */
public class SWRLParser {

    private final Set<String> knownTypes;
    private final Set<String> knownFunctions;
    private final Set<String> knownLinks;

    public SWRLParser(OntologySchema schema, FunctionRegistry functionRegistry) {
        // 收集已知类型名
        this.knownTypes = new HashSet<>();
        if (schema.getObjectTypes() != null) {
            schema.getObjectTypes().forEach(ot -> knownTypes.add(ot.getName()));
        }

        // 收集已知函数名
        this.knownFunctions = new HashSet<>(functionRegistry.getFunctionNames());
        if (schema.getFunctions() != null) {
            schema.getFunctions().forEach(f -> knownFunctions.add(f.getName()));
        }

        // 收集已知 link 名
        this.knownLinks = new HashSet<>();
        if (schema.getLinkTypes() != null) {
            schema.getLinkTypes().forEach(lt -> knownLinks.add(lt.getName()));
        }
    }

    /**
     * 解析 schema 中所有的 SWRL 规则
     */
    public List<SWRLRule> parseAll(OntologySchema schema) {
        List<SWRLRule> rules = new ArrayList<>();
        if (schema.getRules() == null) return rules;

        for (Rule rule : schema.getRules()) {
            if (!"swrl".equals(rule.getLanguage())) continue;
            try {
                rules.add(parse(rule.getName(), rule.getDisplayName(), rule.getExpr()));
            } catch (Exception e) {
                System.err.println("Failed to parse SWRL rule '" + rule.getName() + "': " + e.getMessage());
            }
        }
        return rules;
    }

    /**
     * 解析单条 SWRL 表达式
     */
    public SWRLRule parse(String name, String displayName, String expr) {
        // 规范化：去除多余空白，统一分隔符
        String normalized = expr.replaceAll("\\s+", " ").trim();

        // 按 → 分割前件和后件
        int arrowIdx = normalized.indexOf('→');
        if (arrowIdx < 0) {
            throw new IllegalArgumentException("Missing → in SWRL expression: " + expr);
        }

        String antecedentStr = normalized.substring(0, arrowIdx).trim();
        String consequentStr = normalized.substring(arrowIdx + 1).trim();

        List<Atom> antecedents = parseAntecedents(antecedentStr);
        Atom consequent = parseConsequent(consequentStr);

        return new SWRLRule(name, displayName, antecedents, consequent);
    }

    /**
     * 解析前件：按 ∧ 分割（需要处理嵌套括号）
     */
    private List<Atom> parseAntecedents(String str) {
        List<String> parts = splitByConjunction(str);
        List<Atom> atoms = new ArrayList<>();

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            atoms.add(parseAtom(part));
        }
        return atoms;
    }

    /**
     * 按顶层 ∧ 分割，不分割括号内的 ∧
     */
    private List<String> splitByConjunction(String str) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(' || c == '（') depth++;
            else if (c == ')' || c == '）') depth--;
            else if (c == '∧' && depth == 0) {
                parts.add(current.toString());
                current = new StringBuilder();
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) parts.add(current.toString());
        return parts;
    }

    /**
     * 解析单个原子表达式
     */
    private Atom parseAtom(String str) {
        str = str.trim();

        // 不等式: (?var != "value")
        if (str.startsWith("(") && str.contains("!=")) {
            return parseInequality(str);
        }

        // 析取: (atom1 ∨ atom2)
        if (str.startsWith("(") && str.contains("∨")) {
            return parseDisjunction(str);
        }

        // 函数调用比较: func(...) == value
        if (str.contains("==")) {
            return parseFunctionCall(str);
        }

        // 普通谓词: name(?var, value) 或 Type(?var)
        return parsePredicate(str);
    }

    /**
     * 解析不等式: (?status != "正常")
     */
    private Atom parseInequality(String str) {
        // 去除外层括号
        str = stripOuterParens(str);
        String[] parts = str.split("!=");
        String varName = parts[0].trim();
        Object value = parseLiteral(parts[1].trim());
        return Atom.inequality(varName, value);
    }

    /**
     * 解析析取: (atom1 ∨ atom2)
     */
    private Atom parseDisjunction(String str) {
        str = stripOuterParens(str);
        String[] parts = str.split("∨");
        List<Atom> disjuncts = new ArrayList<>();
        for (String part : parts) {
            disjuncts.add(parseAtom(part.trim()));
        }
        return Atom.disjunction(disjuncts);
    }

    /**
     * 解析函数调用: func(args) == value
     */
    private Atom parseFunctionCall(String str) {
        // 找到 == 的位置（排除嵌套括号内的）
        int eqIdx = findTopLevelOperator(str, "==");
        if (eqIdx < 0) throw new IllegalArgumentException("Cannot find == in: " + str);

        String callPart = str.substring(0, eqIdx).trim();
        String valuePart = str.substring(eqIdx + 2).trim();

        // 解析函数名和参数
        int parenStart = callPart.indexOf('(');
        String funcName = callPart.substring(0, parenStart).trim();
        String argsStr = callPart.substring(parenStart + 1, findMatchingParen(callPart, parenStart)).trim();

        List<FunctionArg> arguments = parseFunctionArgs(argsStr);
        Object expectedValue = parseLiteral(valuePart);

        return Atom.functionCall(funcName, arguments, expectedValue);
    }

    /**
     * 解析函数参数列表（处理嵌套括号）
     */
    private List<FunctionArg> parseFunctionArgs(String argsStr) {
        List<String> argParts = splitTopLevel(argsStr, ',');
        List<FunctionArg> args = new ArrayList<>();

        for (String argStr : argParts) {
            argStr = argStr.trim();
            if (argStr.isEmpty()) continue;
            args.add(parseFunctionArg(argStr));
        }
        return args;
    }

    /**
     * 解析单个函数参数
     */
    private FunctionArg parseFunctionArg(String str) {
        str = str.trim();

        // 变量: ?p
        if (str.startsWith("?")) {
            return FunctionArg.variable(str);
        }

        // links 表达式: links(?p, linkName) 或 links(?p, linkName)[0].field
        if (str.startsWith("links(")) {
            return parseLinksArg(str);
        }

        // 字面值
        return FunctionArg.literal(parseLiteral(str));
    }

    /**
     * 解析 links 参数: links(?p, linkName) 或 links(?p, linkName)[0].field
     */
    private FunctionArg parseLinksArg(String str) {
        int parenEnd = findMatchingParen(str, str.indexOf('('));
        String inner = str.substring(str.indexOf('(') + 1, parenEnd).trim();
        String suffix = str.substring(parenEnd + 1).trim();

        String[] parts = inner.split(",");
        String variable = parts[0].trim();
        String linkName = parts[1].trim();

        if (suffix.isEmpty()) {
            return FunctionArg.linksExpr(variable, linkName);
        } else {
            return FunctionArg.linksPathExpr(variable, linkName, suffix);
        }
    }

    /**
     * 解析普通谓词: Type(?var) 或 prop(?var, value) 或 linkName(?var1, ?var2)
     */
    private Atom parsePredicate(String str) {
        int parenStart = str.indexOf('(');
        if (parenStart < 0) {
            throw new IllegalArgumentException("Not a valid predicate: " + str);
        }

        String name = str.substring(0, parenStart).trim();
        int parenEnd = findMatchingParen(str, parenStart);
        String inner = str.substring(parenStart + 1, parenEnd).trim();

        List<String> parts = splitTopLevel(inner, ',');

        // 单参数 + 已知类型名 → 类型断言
        if (parts.size() == 1 && knownTypes.contains(name)) {
            return Atom.typeAssertion(name, parts.get(0).trim());
        }

        // 双参数 + 已知 link 名 → 关系遍历
        if (parts.size() == 2 && knownLinks.contains(name)) {
            return Atom.linkTraversal(name, parts.get(0).trim(), parts.get(1).trim());
        }

        // 双参数 → 属性匹配
        if (parts.size() == 2) {
            String subject = parts.get(0).trim();
            String valueStr = parts.get(1).trim();
            if (valueStr.startsWith("?")) {
                return Atom.propertyMatchVar(name, subject, valueStr);
            }
            return Atom.propertyMatch(name, subject, parseLiteral(valueStr));
        }

        throw new IllegalArgumentException("Cannot parse predicate: " + str);
    }

    /**
     * 解析后件
     */
    private Atom parseConsequent(String str) {
        str = str.trim();
        int parenStart = str.indexOf('(');
        if (parenStart < 0) {
            throw new IllegalArgumentException("Invalid consequent: " + str);
        }

        String predicate = str.substring(0, parenStart).trim();
        int parenEnd = findMatchingParen(str, parenStart);
        String inner = str.substring(parenStart + 1, parenEnd).trim();

        List<String> parts = splitTopLevel(inner, ',');
        if (parts.size() != 2) {
            throw new IllegalArgumentException("Consequent must have 2 arguments: " + str);
        }

        String subject = parts.get(0).trim();
        String valueStr = parts.get(1).trim();

        if (valueStr.startsWith("?")) {
            return Atom.factAssertionVar(predicate, subject, valueStr);
        }
        return Atom.factAssertion(predicate, subject, parseLiteral(valueStr));
    }

    // ---- Utility methods ----

    private Object parseLiteral(String str) {
        str = str.trim();
        if ("true".equals(str)) return Boolean.TRUE;
        if ("false".equals(str)) return Boolean.FALSE;
        if (str.startsWith("\"") && str.endsWith("\"")) {
            return str.substring(1, str.length() - 1);
        }
        if (str.startsWith("\u201c") && str.endsWith("\u201d")) {
            return str.substring(1, str.length() - 1);
        }
        try { return Integer.parseInt(str); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(str); } catch (NumberFormatException ignored) {}
        return str;
    }

    private int findMatchingParen(String str, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        throw new IllegalArgumentException("Unmatched parenthesis in: " + str);
    }

    private List<String> splitTopLevel(String str, char delimiter) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        boolean inString = false;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '"' || c == '\u201c' || c == '\u201d') inString = !inString;
            if (!inString) {
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (c == delimiter && depth == 0) {
                    parts.add(current.toString());
                    current = new StringBuilder();
                    continue;
                }
            }
            current.append(c);
        }
        if (!current.isEmpty()) parts.add(current.toString());
        return parts;
    }

    private int findTopLevelOperator(String str, String operator) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < str.length() - operator.length() + 1; i++) {
            char c = str.charAt(i);
            if (c == '"' || c == '\u201c' || c == '\u201d') inString = !inString;
            if (!inString) {
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (depth == 0 && str.startsWith(operator, i)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String stripOuterParens(String str) {
        str = str.trim();
        if (str.startsWith("(") && str.endsWith(")")) {
            return str.substring(1, str.length() - 1).trim();
        }
        return str;
    }
}
