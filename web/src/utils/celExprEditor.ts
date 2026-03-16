/**
 * CEL 表达式可视化编辑：结构定义与 expr 互转
 * 支持：关系数量比较、聚合合计比较、集合排序比较、函数调用（函数名 + 关系 / 变量 / 混合参数）
 */

export type CelOperandKind = 'size' | 'sum' | 'sort' | 'variable' | 'function_call';

export type CelFuncArgKind = 'link' | 'variable';

export interface CelFuncArg {
  kind: CelFuncArgKind;
  /** kind=link 时的关系名，如 has_gantry_transaction */
  linkType?: string;
  /** kind=variable 时的变量/属性名，如 split_time */
  variableName?: string;
}

export interface CelOperand {
  kind: CelOperandKind;
  linkType: string;
  /** map 别名，如 d、s */
  alias?: string;
  /** 属性名（sum/sort 时必填） */
  property?: string;
  /** sum 时是否用 double() 包裹（数值型） */
  useDouble?: boolean;
  /** variable 类型时的变量/属性名引用（如衍生属性名） */
  variableName?: string;
  /** function_call 类型时的函数名 */
  funcName?: string;
  /**
   * function_call 参数列表：
   * - 支持多个参数
   * - 每个参数可为关系（links.xxx）或变量/属性名
   */
  funcArgs?: CelFuncArg[];
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
  if (op.kind === 'variable') {
    return op.variableName ?? '';
  }
  if (op.kind === 'function_call') {
    const funcName = (op.funcName ?? '').trim();
    if (!funcName) return '';
    // 优先使用 funcArgs（多参数），兼容旧的单参数字段
    const args: CelFuncArg[] = (() => {
      if (op.funcArgs && op.funcArgs.length > 0) {
        return op.funcArgs;
      }
      // 兼容旧结构：只有 funcArgKind / funcArgLinkType / funcArgVariableName
      const argKind: CelFuncArgKind = (op as any).funcArgKind ?? 'link';
      if (argKind === 'link') {
        const lt = ((op as any).funcArgLinkType ?? '').trim();
        if (!lt) return [];
        return [{ kind: 'link', linkType: lt }];
      }
      const v = ((op as any).funcArgVariableName ?? '').trim();
      if (!v) return [];
      return [{ kind: 'variable', variableName: v }];
    })();
    if (args.length === 0) return '';
    const argExprs = args
      .map((a) => {
        if (a.kind === 'link') {
          const lt = (a.linkType ?? '').trim();
          if (!lt) return '';
          return `links.${lt}`;
        }
        const v = (a.variableName ?? '').trim();
        return v || '';
      })
      .filter((s) => s);
    if (argExprs.length === 0) return '';
    return `${funcName}(${argExprs.join(', ')})`;
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

  // 函数调用：支持多参数，如 detect_late_upload(links.has_gantry_transaction, split_time)
  const funcCallMatch = t.match(/^([a-zA-Z_]\w*)\s*\(\s*(.+)\s*\)$/);
  if (funcCallMatch) {
    const funcName = funcCallMatch[1];
    const argsRaw = funcCallMatch[2];
    const argStrs = argsRaw.split(',').map((x) => x.trim()).filter((x) => x.length > 0);
    const funcArgs: CelFuncArg[] = [];
    for (const arg of argStrs) {
      const linkMatch = arg.match(/^links\.(\w+)$/);
      if (linkMatch) {
        funcArgs.push({ kind: 'link', linkType: linkMatch[1] });
        continue;
      }
      const varMatch = arg.match(/^[a-zA-Z_]\w*$/);
      if (varMatch) {
        funcArgs.push({ kind: 'variable', variableName: varMatch[0] });
        continue;
      }
      // 对于暂不识别的复杂参数，先按变量名兜底展示原文
      funcArgs.push({ kind: 'variable', variableName: arg });
    }
    if (funcArgs.length === 0) return null;
    return {
      kind: 'function_call',
      linkType: '',
      funcName,
      funcArgs,
    };
  }

  const varMatch = t.match(/^([a-zA-Z_]\w*)$/);
  if (varMatch) return { kind: 'variable', linkType: '', variableName: varMatch[1] };

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
  let opStr: CelVisualExpr['operator'] = '==';
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
