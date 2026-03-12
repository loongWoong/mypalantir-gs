/**
 * 规则表达式可视化编辑：SWRL 结构定义与 expr 互转
 * 供非专业人员通过表单生成 SWRL，无需手写语法。
 */

/** 规则结论引用（前件中“满足某条规则的结论”的嵌套条件） */
export interface RuleConclusionRef {
  kind: 'ruleConclusion';
  ruleName: string;
  varName: string;
  predicate: string;
  value: string | boolean;
}

/** SWRL 前件中的一条条件 */
export type SwrlCondition =
  | { kind: 'predicate'; predicate: string; varName: string; value: string | boolean }
  | RuleConclusionRef
  | { kind: 'relation'; predicate: string; var1: string; var2: string }
  | { kind: 'function'; funcName: string; argExprs: string[]; expectedValue: boolean }
  | { kind: 'disjunction'; disjuncts: SwrlCondition[] };

/** SWRL 后件（结论） */
export interface SwrlConclusion {
  predicate: string;
  varName: string;
  value: string | boolean;
}

/** SWRL 可视化结构 */
export interface SwrlVisualRule {
  subjectEntity: string; // 实体类型名，如 Passage
  subjectVar: string;    // 主变量名（不含 ?），如 p
  conditions: SwrlCondition[];
  conclusion: SwrlConclusion;
  /** 条件/结论中出现的其他变量（如 ?v），用于关系条件 */
  extraVars: string[];    // 如 ['v']
}

const DEFAULT_SUBJECT_VAR = 'p';

/** 按顶层逗号分割参数串，不拆分括号内的逗号 */
function splitTopLevelArgs(argsStr: string): string[] {
  if (!argsStr.trim()) return [];
  const result: string[] = [];
  let depth = 0;
  let start = 0;
  for (let i = 0; i < argsStr.length; i++) {
    const c = argsStr[i];
    if (c === '(' || c === '[') depth++;
    else if (c === ')' || c === ']') depth--;
    else if (c === ',' && depth === 0) {
      result.push(argsStr.slice(start, i).trim());
      start = i + 1;
    }
  }
  result.push(argsStr.slice(start).trim());
  return result.filter(Boolean);
}

/**
 * 从可视化结构生成 SWRL expr 字符串
 */
export function swrlVisualToExpr(rule: SwrlVisualRule): string {
  const q = (v: string) => (v.startsWith('?') ? v : `?${v}`);
  const fmtVal = (val: string | boolean) => {
    if (typeof val === 'boolean') return String(val);
    const s = String(val);
    if (/^\d+(?:\.\d+)?$/.test(s)) return s;
    return `"${s.replace(/"/g, '\\"')}"`;
  };

  const head = `${rule.subjectEntity}(${q(rule.subjectVar)})`;
  const condStrs = rule.conditions.map((c) => {
    if (c.kind === 'predicate') {
      return `${c.predicate}(${q(c.varName)}, ${fmtVal(c.value)})`;
    }
    if (c.kind === 'ruleConclusion') {
      return `${c.predicate}(${q(c.varName)}, ${fmtVal(c.value)})`;
    }
    if (c.kind === 'function') {
      return `${c.funcName}(${c.argExprs.join(', ')}) == ${c.expectedValue}`;
    }
    if (c.kind === 'disjunction') {
      const parts = c.disjuncts.map((d) => {
        if (d.kind === 'predicate') return `${d.predicate}(${q(d.varName)}, ${fmtVal(d.value)})`;
        if (d.kind === 'ruleConclusion') return `${d.predicate}(${q(d.varName)}, ${fmtVal(d.value)})`;
        if (d.kind === 'function') return `${d.funcName}(${d.argExprs.join(', ')}) == ${d.expectedValue}`;
        return `${(d as { predicate: string; var1: string; var2: string }).predicate}(${q((d as { var1: string }).var1)}, ${q((d as { var2: string }).var2)})`;
      });
      return `(${parts.join(' ∨ ')})`;
    }
    return `${c.predicate}(${q(c.var1)}, ${q(c.var2)})`;
  });
  const antecedent = [head, ...condStrs].join('\n        ∧ ');
  const concl = rule.conclusion;
  const consequent = `${concl.predicate}(${q(concl.varName)}, ${fmtVal(concl.value)})`;
  return `${antecedent}\n        → ${consequent}`.trim();
}

/**
 * 规范化 SWRL 字符串：统一箭头与“且”的写法，便于解析
 */
function normalizeSwrl(expr: string): string {
  return expr
    .replace(/\s+/g, ' ')
    .replace(/\s*->\s*/g, ' → ')
    .replace(/\s*=>\s*/g, ' → ')
    .replace(/\^/g, ' ∧ ')
    .replace(/\s*&\s*/g, ' ∧ ')
    .trim();
}

/** 按顶层 ∨ 分割，不分割括号内的 ∨ */
function splitByTopLevelOr(str: string): string[] {
  const result: string[] = [];
  let depth = 0;
  let start = 0;
  for (let i = 0; i < str.length; i++) {
    const c = str[i];
    if (c === '(' || c === '（') depth++;
    else if (c === ')' || c === '）') depth--;
    else if (depth === 0 && str.slice(i).match(/^\s*∨\s*/)) {
      const orLen = str.slice(i).match(/^\s*∨\s*/)![0].length;
      result.push(str.slice(start, i).trim());
      start = i + orLen;
      i += orLen - 1;
    }
  }
  if (start < str.length) result.push(str.slice(start).trim());
  return result.filter(Boolean);
}

/** 用于解析时匹配“某规则的结论”的规则摘要 */
export interface RuleSummaryForParse {
  name: string;
  display_name?: string;
  expr: string;
}

/** 解析单条条件（谓词/规则结论引用/关系/函数），不解析实体类型；传入 rules 时谓词可解析为 ruleConclusion */
function parseOneCondition(
  part: string,
  options?: { subjectVar?: string; rules?: RuleSummaryForParse[] }
): SwrlCondition | null {
  const predMatch = part.match(/^(\w+)\s*\(\s*\?(\w+)\s*,\s*(?:"([^"]*)"|(true|false)|(\d+(?:\.\d+)?))\s*\)\s*$/);
  if (predMatch) {
    const [, pred, v, strVal, boolVal, numVal] = predMatch;
    const value = boolVal !== undefined ? boolVal === 'true' : (strVal ?? (numVal ?? ''));
    const valueNorm = typeof value === 'boolean' ? value : String(value);
    if (options?.rules?.length) {
      for (const r of options.rules) {
        const c = getConclusionFromRuleExpr(r.expr);
        if (!c) continue;
        const cVal = typeof c.value === 'boolean' ? c.value : String(c.value);
        if (c.predicate === pred && cVal === valueNorm) {
          return {
            kind: 'ruleConclusion',
            ruleName: r.name,
            varName: v,
            predicate: pred,
            value,
          };
        }
      }
    }
    return { kind: 'predicate', predicate: pred, varName: v, value };
  }
  const relMatch = part.match(/^(\w+)\s*\(\s*\?(\w+)\s*,\s*\?(\w+)\s*\)$/);
  if (relMatch) {
    const [, pred, v1, v2] = relMatch;
    return { kind: 'relation', predicate: pred, var1: v1, var2: v2 };
  }
  const funcMatch = part.match(/^(\w+)\s*\((.*)\)\s*==\s*(true|false)$/);
  if (funcMatch) {
    const [, funcName, argsStr] = funcMatch;
    const expectedValue = funcMatch[3] === 'true';
    const argExprs = splitTopLevelArgs(argsStr.trim());
    return { kind: 'function', funcName, argExprs, expectedValue };
  }
  return null;
}

/**
 * 从 SWRL expr 字符串解析为可视化结构（尽力而为，不保证所有写法都能解析）。
 * 传入 rules 时，前件中与某规则结论一致的谓词会解析为 ruleConclusion，便于嵌套规则展示与编辑。
 */
export function swrlExprToVisual(
  expr: string,
  rules?: RuleSummaryForParse[]
): SwrlVisualRule | null {
  const trimmed = normalizeSwrl(expr);
  if (!trimmed) {
    return {
      subjectEntity: '',
      subjectVar: DEFAULT_SUBJECT_VAR,
      conditions: [],
      conclusion: { predicate: '', varName: DEFAULT_SUBJECT_VAR, value: '' },
      extraVars: [],
    };
  }

  const arrowIndex = trimmed.indexOf('→') >= 0 ? trimmed.indexOf('→') : trimmed.indexOf('->');
  if (arrowIndex === -1) return null;
  const arrowLen = trimmed[arrowIndex] === '→' ? 1 : 2;
  const antecedent = trimmed.slice(0, arrowIndex).trim();
  const consequent = trimmed.slice(arrowIndex + arrowLen).trim();

  // 解析后件: pred(?x, value)，value 支持 "字符串"、true/false、数字（允许逗号后有多余空格）
  const conclMatch = consequent.match(/^(\w+)\s*\(\s*\?(\w+)\s*,\s*(?:"([^"]*)"|(true|false)|(\d+(?:\.\d+)?))\s*\)\s*$/);
  if (!conclMatch) return null;
  const [, conclPred, conclVar, strVal, boolVal, numVal] = conclMatch;
  const conclusion: SwrlConclusion = {
    predicate: conclPred,
    varName: conclVar,
    value: boolVal !== undefined ? boolVal === 'true' : (strVal ?? (numVal ?? '')),
  };

  // 解析前件: 用 ∧ 分割（析取子句 (A ∨ B) 作为整体保留）
  const andPattern = /\s*[∧&^]\s*|\s+and\s+/i;
  const parts = antecedent.split(andPattern).map((s) => s.trim()).filter(Boolean);
  const conditions: SwrlCondition[] = [];
  let subjectEntity = '';
  let subjectVar = DEFAULT_SUBJECT_VAR;
  const extraVarsSet = new Set<string>();
  const parseOpts = { subjectVar: '', rules };

  for (const part of parts) {
    const trimmedPart = part.trim();
    // 实体类型: Passage(?p)
    const entityMatch = trimmedPart.match(/^(\w+)\s*\(\s*\?(\w+)\s*\)$/);
    if (entityMatch) {
      const [, entity, v] = entityMatch;
      subjectEntity = entity;
      subjectVar = v;
      parseOpts.subjectVar = v;
      continue;
    }
    // 析取: (atom1 ∨ atom2) — 外层括号且含 ∨，按 ∨ 分割后解析每个子句（支持规则结论嵌套）
    if (trimmedPart.startsWith('(') && trimmedPart.endsWith(')') && trimmedPart.includes('∨')) {
      const inner = trimmedPart.slice(1, -1).trim();
      const disjunctParts = splitByTopLevelOr(inner);
      const disjuncts: SwrlCondition[] = [];
      for (const dp of disjunctParts) {
        const cond = parseOneCondition(dp.trim(), { ...parseOpts, subjectVar });
        if (!cond) return null;
        disjuncts.push(cond);
        if (cond.kind === 'relation') {
          extraVarsSet.add(cond.var1);
          extraVarsSet.add(cond.var2);
        }
      }
      conditions.push({ kind: 'disjunction', disjuncts });
      continue;
    }
    const cond = parseOneCondition(trimmedPart, { ...parseOpts, subjectVar });
    if (cond) {
      conditions.push(cond);
      if (cond.kind === 'relation') {
        extraVarsSet.add(cond.var1);
        extraVarsSet.add(cond.var2);
      }
      continue;
    }
    return null;
  }

  extraVarsSet.delete(subjectVar);
  const extraVars = Array.from(extraVarsSet);

  return {
    subjectEntity,
    subjectVar,
    conditions,
    conclusion,
    extraVars,
  };
}

/** 判断当前 expr 是否可解析为 SWRL 可视化结构 */
export function canParseAsSwrl(expr: string): boolean {
  if (!expr || !expr.trim()) return true;
  return swrlExprToVisual(expr) !== null;
}

/**
 * 从规则表达式字符串中解析后件（结论），得到谓词与值，用于匹配“某规则的结论”
 */
export function getConclusionFromRuleExpr(expr: string): { predicate: string; value: string | boolean } | null {
  const trimmed = expr.replace(/\s+/g, ' ').trim();
  const arrowIdx = trimmed.indexOf('→');
  if (arrowIdx < 0) return null;
  const consequent = trimmed.slice(arrowIdx + 1).trim();
  const m = consequent.match(/^(\w+)\s*\(\s*\?\w+\s*,\s*(?:"([^"]*)"|(true|false)|(\d+(?:\.\d+)?))\s*\)/);
  if (!m) return null;
  const [, predicate, strVal, boolVal, numVal] = m;
  const value = boolVal !== undefined ? boolVal === 'true' : (strVal ?? numVal ?? '');
  return { predicate, value };
}

/**
 * 解析失败时使用的回退：从 expr 中尽量提取主体与结论，条件置空，避免显示其他规则内容
 */
export function fallbackVisualFromExpr(expr: string): SwrlVisualRule {
  const trimmed = expr.replace(/\s+/g, ' ').trim();
  let subjectEntity = '';
  let subjectVar = DEFAULT_SUBJECT_VAR;
  let conclusion: SwrlConclusion = { predicate: '', varName: DEFAULT_SUBJECT_VAR, value: '' };
  const arrowIdx = trimmed.indexOf('→');
  if (arrowIdx >= 0) {
    const ant = trimmed.slice(0, arrowIdx).trim();
    const cons = trimmed.slice(arrowIdx + 1).trim();
    const entityMatch = ant.match(/(\w+)\s*\(\s*\?(\w+)\s*\)/);
    if (entityMatch) {
      subjectEntity = entityMatch[1];
      subjectVar = entityMatch[2];
    }
    const conclMatch = cons.match(/^(\w+)\s*\(\s*\?(\w+)\s*,\s*(?:"([^"]*)"|(true|false)|(\d+(?:\.\d+)?))\s*\)/);
    if (conclMatch) {
      const strVal = conclMatch[3];
      const boolVal = conclMatch[4];
      const numVal = conclMatch[5];
      conclusion = {
        predicate: conclMatch[1],
        varName: conclMatch[2],
        value: boolVal !== undefined ? boolVal === 'true' : (strVal ?? numVal ?? ''),
      };
    }
  }
  return {
    subjectEntity,
    subjectVar,
    conditions: [],
    conclusion,
    extraVars: [],
  };
}
