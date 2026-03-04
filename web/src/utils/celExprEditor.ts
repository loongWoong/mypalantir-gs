/**
 * CEL 表达式可视化编辑：结构定义与 expr 互转
 * 支持：关系数量比较、聚合合计比较、集合排序比较
 */

export type CelOperandKind = 'size' | 'sum' | 'sort';

export interface CelOperand {
  kind: CelOperandKind;
  linkType: string;
  /** map 别名，如 d、s */
  alias?: string;
  /** 属性名（sum/sort 时必填） */
  property?: string;
  /** sum 时是否用 double() 包裹（数值型） */
  useDouble?: boolean;
}

export type CelExprMode = 'single' | 'compare';

export interface CelVisualExpr {
  /** 单值（如 size(links.xxx)）或 比较（左 op 右） */
  mode: CelExprMode;
  expressionName: string;
  left: CelOperand;
  operator: '==' | '!=' | '<' | '>' | '<=' | '>=';
  right: CelOperand;
}

const DEFAULT_ALIAS_LEFT = 'd';
const DEFAULT_ALIAS_RIGHT = 's';

function celOperandToExpr(op: CelOperand, defaultAlias: string): string {
  const alias = op.alias || defaultAlias;
  if (op.kind === 'size') {
    return `size(links.${op.linkType})`;
  }
  if (op.kind === 'sum') {
    const propExpr = op.useDouble ? `double(${alias}.${op.property})` : `${alias}.${op.property}`;
    return `links.${op.linkType}.map(${alias}, ${propExpr}).sum()`;
  }
  if (op.kind === 'sort') {
    const propExpr = op.property ? `${alias}.${op.property}` : alias;
    return `links.${op.linkType}.map(${alias}, ${propExpr}).sort()`;
  }
  return '';
}

/**
 * 从可视化结构生成 CEL expr 字符串
 */
export function celVisualToExpr(v: CelVisualExpr): string {
  const leftStr = celOperandToExpr(v.left, DEFAULT_ALIAS_LEFT);
  if (v.mode === 'single') return leftStr || '';
  const rightStr = celOperandToExpr(v.right, DEFAULT_ALIAS_RIGHT);
  if (!leftStr || !rightStr) return '';
  return `${leftStr} ${v.operator} ${rightStr}`.trim();
}

function parseOperand(s: string): CelOperand | null {
  const t = s.replace(/\s+/g, ' ').trim();
  const sizeMatch = t.match(/^size\s*\(\s*links\.(\w+)\s*\)$/);
  if (sizeMatch) return { kind: 'size', linkType: sizeMatch[1] };

  const sumMatch = t.match(/^links\.(\w+)\s*\.map\s*\(\s*(\w+)\s*,\s*(?:double\s*\(\s*\w+\.(\w+)\s*\)|(\w+)\.(\w+))\s*\)\.sum\(\)$/);
  if (sumMatch) {
    const prop = sumMatch[3] || sumMatch[5];
    return { kind: 'sum', linkType: sumMatch[1], alias: sumMatch[2], property: prop, useDouble: t.includes('double(') };
  }

  const sortMatch = t.match(/^links\.(\w+)\s*\.map\s*\(\s*(\w+)\s*,\s*\w+\.(\w+)\s*\)\.sort\(\)$/);
  if (sortMatch) return { kind: 'sort', linkType: sortMatch[1], alias: sortMatch[2], property: sortMatch[3] };

  return null;
}

/**
 * 从 CEL expr 解析为可视化结构（支持单值或比较）
 */
export function celExprToVisual(expr: string): CelVisualExpr | null {
  const t = expr.replace(/\s+/g, ' ').trim();
  if (!t) {
    return {
      mode: 'single',
      expressionName: '',
      left: { kind: 'size', linkType: '' },
      operator: '==',
      right: { kind: 'size', linkType: '' },
    };
  }

  const ops: Array<CelVisualExpr['operator']> = ['==', '!=', '<=', '>=', '<', '>'];
  let opIndex = -1;
  let opStr = '==';
  for (const o of ops) {
    const i = t.indexOf(o);
    if (i >= 0 && (opIndex < 0 || i < opIndex)) {
      opIndex = i;
      opStr = o;
    }
  }

  if (opIndex < 0) {
    const single = parseOperand(t);
    if (single) {
      return {
        mode: 'single',
        expressionName: '',
        left: single,
        operator: '==',
        right: { kind: 'size', linkType: '' },
      };
    }
    return null;
  }

  const leftStr = t.slice(0, opIndex).trim();
  const rightStr = t.slice(opIndex + opStr.length).trim();
  const left = parseOperand(leftStr);
  const right = parseOperand(rightStr);
  if (!left || !right) return null;

  return {
    mode: 'compare',
    expressionName: '',
    left,
    operator: opStr,
    right,
  };
}

export const DEFAULT_CEL_VISUAL: CelVisualExpr = {
  mode: 'single',
  expressionName: '',
  left: { kind: 'size', linkType: '' },
  operator: '==',
  right: { kind: 'size', linkType: '' },
};
