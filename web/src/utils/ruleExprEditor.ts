/**
 * 规则表达式可视化编辑：SWRL 结构定义与 expr 互转
 * 供非专业人员通过表单生成 SWRL，无需手写语法。
 */

/** SWRL 前件中的一条条件 */
export type SwrlCondition =
  | { kind: 'predicate'; predicate: string; varName: string; value: string | boolean }
  | { kind: 'relation'; predicate: string; var1: string; var2: string }
  | { kind: 'function'; funcName: string; argExprs: string[]; expectedValue: boolean };

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
    if (c.kind === 'function') {
      return `${c.funcName}(${c.argExprs.join(', ')}) == ${c.expectedValue}`;
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

/**
 * 从 SWRL expr 字符串解析为可视化结构（尽力而为，不保证所有写法都能解析）
 */
export function swrlExprToVisual(expr: string): SwrlVisualRule | null {
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

  // 解析前件: 用 ∧、^、& 或 and 分割（支持常见 SWRL 写法）
  const andPattern = /\s*[∧&^]\s*|\s+and\s+/i;
  const parts = antecedent.split(andPattern).map((s) => s.trim()).filter(Boolean);
  const conditions: SwrlCondition[] = [];
  let subjectEntity = '';
  let subjectVar = DEFAULT_SUBJECT_VAR;
  const extraVarsSet = new Set<string>();

  for (const part of parts) {
    // 实体类型: Passage(?p)
    const entityMatch = part.match(/^(\w+)\s*\(\s*\?(\w+)\s*\)$/);
    if (entityMatch) {
      const [, entity, v] = entityMatch;
      subjectEntity = entity;
      subjectVar = v;
      continue;
    }
    // 谓词: pred(?x, value)，value 支持 "字符串"、true/false、数字
    const predMatch = part.match(/^(\w+)\s*\(\s*\?(\w+)\s*,\s*(?:"([^"]*)"|(true|false)|(\d+(?:\.\d+)?))\s*\)\s*$/);
    if (predMatch) {
      const [, pred, v, strVal, boolVal, numVal] = predMatch;
      conditions.push({
        kind: 'predicate',
        predicate: pred,
        varName: v,
        value: boolVal !== undefined ? boolVal === 'true' : (strVal ?? (numVal ?? '')),
      });
      continue;
    }
    // 关系: pred(?x, ?y)
    const relMatch = part.match(/^(\w+)\s*\(\s*\?(\w+)\s*,\s*\?(\w+)\s*\)$/);
    if (relMatch) {
      const [, pred, v1, v2] = relMatch;
      conditions.push({ kind: 'relation', predicate: pred, var1: v1, var2: v2 });
      extraVarsSet.add(v1);
      extraVarsSet.add(v2);
      continue;
    }
    // 函数调用: funcName(arg1, arg2, ...) == true|false
    const funcMatch = part.match(/^(\w+)\s*\((.*)\)\s*==\s*(true|false)$/);
    if (funcMatch) {
      const [, funcName, argsStr] = funcMatch;
      const expectedValue = funcMatch[3] === 'true';
      const argExprs = splitTopLevelArgs(argsStr.trim());
      conditions.push({ kind: 'function', funcName, argExprs, expectedValue });
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
