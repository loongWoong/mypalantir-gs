/**
 * 规则表达式可视化编辑：SWRL 结构定义与 expr 互转
 * 供非专业人员通过表单生成 SWRL，无需手写语法。
 */

/** SWRL 前件中的一条条件 */
export type SwrlCondition =
  | { kind: 'predicate'; predicate: string; varName: string; value: string | boolean }
  | { kind: 'relation'; predicate: string; var1: string; var2: string };

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

/**
 * 从可视化结构生成 SWRL expr 字符串
 */
export function swrlVisualToExpr(rule: SwrlVisualRule): string {
  const q = (v: string) => (v.startsWith('?') ? v : `?${v}`);
  const fmtVal = (val: string | boolean) =>
    typeof val === 'boolean' ? String(val) : `"${String(val).replace(/"/g, '\\"')}"`;

  const head = `${rule.subjectEntity}(${q(rule.subjectVar)})`;
  const condStrs = rule.conditions.map((c) => {
    if (c.kind === 'predicate') {
      return `${c.predicate}(${q(c.varName)}, ${fmtVal(c.value)})`;
    }
    return `${c.predicate}(${q(c.var1)}, ${q(c.var2)})`;
  });
  const antecedent = [head, ...condStrs].join('\n        ∧ ');
  const concl = rule.conclusion;
  const consequent = `${concl.predicate}(${q(concl.varName)}, ${fmtVal(concl.value)})`;
  return `${antecedent}\n        → ${consequent}`.trim();
}

/**
 * 从 SWRL expr 字符串解析为可视化结构（尽力而为，不保证所有写法都能解析）
 */
export function swrlExprToVisual(expr: string): SwrlVisualRule | null {
  const trimmed = expr.replace(/\s+/g, ' ').trim();
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

  // 解析后件: pred(?x, value)
  const conclMatch = consequent.match(/^(\w+)\s*\(\s*\?(\w+)\s*,\s*(?:"([^"]*)"|(true|false))\s*\)$/);
  if (!conclMatch) return null;
  const [, conclPred, conclVar, strVal, boolVal] = conclMatch;
  const conclusion: SwrlConclusion = {
    predicate: conclPred,
    varName: conclVar,
    value: boolVal !== undefined ? boolVal === 'true' : (strVal ?? ''),
  };

  // 解析前件: 用 ∧ 或 and 分割（支持 Unicode ∧ 与 ASCII）
  const andPattern = /\s*[∧&]\s*|\s+and\s+/i;
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
    // 谓词: pred(?x, value)
    const predMatch = part.match(/^(\w+)\s*\(\s*\?(\w+)\s*,\s*(?:"([^"]*)"|(true|false))\s*\)$/);
    if (predMatch) {
      const [, pred, v, strVal, boolVal] = predMatch;
      conditions.push({
        kind: 'predicate',
        predicate: pred,
        varName: v,
        value: boolVal !== undefined ? boolVal === 'true' : (strVal ?? ''),
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
