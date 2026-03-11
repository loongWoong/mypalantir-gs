import { useState, useEffect, useRef, useCallback } from 'react';
import type { CelVisualExpr, CelOperand, CelExprMode } from '../../utils/celExprEditor';
import {
  celVisualToExpr,
  celExprToVisual,
  DEFAULT_CEL_VISUAL,
} from '../../utils/celExprEditor';

export interface EntityOption {
  name: string;
  display_name?: string;
  attributes: { name: string; display_name?: string }[];
}

export interface RelationOption {
  name: string;
  display_name?: string;
  source_type: string;
  target_type: string;
}

export interface CelExprBuilderProps {
  expr: string;
  onChange: (expr: string) => void;
  relations: RelationOption[];
  entities: EntityOption[];
  disabled?: boolean;
}

function attrDisplay(a: { name: string; display_name?: string }): string {
  return a.display_name || a.name;
}

function relationDisplay(r: RelationOption): string {
  return r.display_name || r.name;
}

export function CelExprBuilder({
  expr,
  onChange,
  relations,
  entities,
  disabled = false,
}: CelExprBuilderProps) {
  const [visual, setVisual] = useState<CelVisualExpr>(() => {
    if (!expr.trim()) return { ...DEFAULT_CEL_VISUAL };
    const parsed = celExprToVisual(expr);
    return parsed ?? { ...DEFAULT_CEL_VISUAL };
  });

  // 跟踪本组件最后一次生成的 expr，避免把自己 onChange 触发的 prop 更新再次解析回来造成循环
  const lastGeneratedExprRef = useRef<string>('');

  useEffect(() => {
    // 如果这次 expr 是本组件自己生成的，跳过解析（避免循环）
    if (expr === lastGeneratedExprRef.current) return;

    if (!expr.trim()) {
      setVisual({ ...DEFAULT_CEL_VISUAL });
      return;
    }
    const parsed = celExprToVisual(expr);
    if (parsed) setVisual(parsed);
  }, [expr]);

  // 统一的 visual → expr 同步出口，直接调用 onChange，不再使用 useEffect 监听 visual
  const flushVisual = useCallback((next: CelVisualExpr) => {
    const generated = celVisualToExpr(next);
    lastGeneratedExprRef.current = generated || '';
    onChange(generated || '');
  }, [onChange]);

  const getEntity = (name: string) => entities.find((e) => e.name === name);
  const getAttributesForLinkType = (linkTypeName: string) => {
    const rel = relations.find((r) => r.name === linkTypeName);
    if (!rel) return [];
    const ent = getEntity(rel.target_type);
    return ent?.attributes ?? [];
  };

  const setLeft = (upd: Partial<CelVisualExpr['left']>) =>
    setVisual((v) => {
      const next = { ...v, left: { ...v.left, ...upd } };
      flushVisual(next);
      return next;
    });
  const setRight = (upd: Partial<CelVisualExpr['right']>) =>
    setVisual((v) => {
      const next = { ...v, right: { ...v.right, ...upd } };
      flushVisual(next);
      return next;
    });
  const setMode = (mode: CelExprMode) =>
    setVisual((v) => {
      const next = { ...v, mode };
      flushVisual(next);
      return next;
    });

  return (
    <div className="space-y-3 min-w-0 overflow-hidden">
      <div className="flex items-center gap-2">
        <span className="text-xs text-gray-500">表达式类型：</span>
        <select
          value={visual.mode}
          onChange={(e) => setMode(e.target.value as CelExprMode)}
          disabled={disabled}
          className="border rounded px-2 py-1 text-sm"
        >
          <option value="single">单值（如关系数量、聚合合计）</option>
          <option value="compare">比较（左侧 运算符 右侧）</option>
        </select>
      </div>
      <p className="text-xs text-gray-500">
        {visual.mode === 'single'
          ? '选择取值方式与关系，生成单个 CEL 表达式（如 size(links.关系名)）'
          : '选择左右两侧的取值方式与关系，生成 CEL 比较表达式'}
      </p>
      <div className="flex flex-wrap items-start gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-gray-600 text-sm">{visual.mode === 'single' ? '取值' : '左侧'}</span>
          <select
            value={visual.left.kind}
            onChange={(e) => setLeft({ kind: e.target.value as CelOperand['kind'] })}
            disabled={disabled}
            className="border rounded px-2 py-1.5 text-sm"
          >
            <option value="size">关系数量</option>
            <option value="sum">聚合合计</option>
            <option value="sort">集合排序</option>
          </select>
          <select
            value={visual.left.linkType}
            onChange={(e) => setLeft({ linkType: e.target.value })}
            disabled={disabled}
            className="border rounded px-2 py-1.5 text-sm min-w-[130px]"
          >
            <option value="">请选择关系</option>
            {relations.map((r) => (
              <option key={r.name} value={r.name}>{relationDisplay(r)}</option>
            ))}
          </select>
          {(visual.left.kind === 'sum' || visual.left.kind === 'sort') && (
            <>
              <input
                type="text"
                value={visual.left.alias ?? 'd'}
                onChange={(e) => setLeft({ alias: e.target.value || 'd' })}
                disabled={disabled}
                placeholder="别名"
                className="border rounded px-2 py-1.5 text-sm w-14 font-mono"
              />
              <select
                value={visual.left.property ?? ''}
                onChange={(e) => setLeft({ property: e.target.value })}
                disabled={disabled}
                className="border rounded px-2 py-1.5 text-sm min-w-[100px]"
              >
                <option value="">属性</option>
                {getAttributesForLinkType(visual.left.linkType).map((a) => (
                  <option key={a.name} value={a.name}>{attrDisplay(a)}</option>
                ))}
              </select>
              {visual.left.kind === 'sum' && (
                <label className="flex items-center gap-1 text-xs">
                  <input
                    type="checkbox"
                    checked={visual.left.useDouble ?? false}
                    onChange={(e) => setLeft({ useDouble: e.target.checked })}
                    disabled={disabled}
                  />
                  double()
                </label>
              )}
            </>
          )}
        </div>
        {visual.mode === 'compare' && (
          <>
            <select
              value={visual.operator}
              onChange={(e) => setVisual((v) => {
                const next = { ...v, operator: e.target.value as CelVisualExpr['operator'] };
                flushVisual(next);
                return next;
              })}
              disabled={disabled}
              className="border rounded px-2 py-1.5 text-sm"
            >
              <option value="==">==</option>
              <option value="!=">!=</option>
              <option value="<">&lt;</option>
              <option value=">">&gt;</option>
              <option value="<=">≤</option>
              <option value=">=">≥</option>
            </select>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-gray-600 text-sm">右侧</span>
          <select
            value={visual.right.kind}
            onChange={(e) => setRight({ kind: e.target.value as CelOperand['kind'] })}
            disabled={disabled}
            className="border rounded px-2 py-1.5 text-sm"
          >
            <option value="size">关系数量</option>
            <option value="sum">聚合合计</option>
            <option value="sort">集合排序</option>
          </select>
          <select
            value={visual.right.linkType}
            onChange={(e) => setRight({ linkType: e.target.value })}
            disabled={disabled}
            className="border rounded px-2 py-1.5 text-sm min-w-[130px]"
          >
            <option value="">请选择关系</option>
            {relations.map((r) => (
              <option key={r.name} value={r.name}>{relationDisplay(r)}</option>
            ))}
          </select>
          {(visual.right.kind === 'sum' || visual.right.kind === 'sort') && (
            <>
              <input
                type="text"
                value={visual.right.alias ?? 's'}
                onChange={(e) => setRight({ alias: e.target.value || 's' })}
                disabled={disabled}
                placeholder="别名"
                className="border rounded px-2 py-1.5 text-sm w-14 font-mono"
              />
              <select
                value={visual.right.property ?? ''}
                onChange={(e) => setRight({ property: e.target.value })}
                disabled={disabled}
                className="border rounded px-2 py-1.5 text-sm min-w-[100px]"
              >
                <option value="">属性</option>
                {getAttributesForLinkType(visual.right.linkType).map((a) => (
                  <option key={a.name} value={a.name}>{attrDisplay(a)}</option>
                ))}
              </select>
              {visual.right.kind === 'sum' && (
                <label className="flex items-center gap-1 text-xs">
                  <input
                    type="checkbox"
                    checked={visual.right.useDouble ?? false}
                    onChange={(e) => setRight({ useDouble: e.target.checked })}
                    disabled={disabled}
                  />
                  double()
                </label>
              )}
            </>
          )}
            </div>
          </>
        )}
      </div>
      {celVisualToExpr(visual) && (
        <div className="p-2 bg-gray-50 border rounded text-xs font-mono text-gray-700 break-all whitespace-pre-wrap overflow-x-auto max-h-32 overflow-y-auto">
          {celVisualToExpr(visual)}
        </div>
      )}
    </div>
  );
}
