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
  const [editMode, setEditMode] = useState<'visual' | 'advanced'>(() => {
    if (!expr.trim()) return 'visual';
    return celExprToVisual(expr) ? 'visual' : 'advanced';
  });
  const [visual, setVisual] = useState<CelVisualExpr>(() => {
    if (!expr.trim()) return { ...DEFAULT_CEL_VISUAL };
    const parsed = celExprToVisual(expr);
    return parsed ?? { ...DEFAULT_CEL_VISUAL };
  });

  const lastGeneratedExprRef = useRef<string>('');

  useEffect(() => {
    if (expr === lastGeneratedExprRef.current) return;

    if (!expr.trim()) {
      setVisual({ ...DEFAULT_CEL_VISUAL });
      return;
    }
    const parsed = celExprToVisual(expr);
    if (parsed) {
      setVisual(parsed);
    } else if (editMode === 'visual') {
      setEditMode('advanced');
    }
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

  const ensureFuncArgs = (side: 'left' | 'right') => {
    const v = side === 'left' ? visual.left : visual.right;
    const existing = (v as any).funcArgs as CelOperand['funcArgs'] | undefined;
    if (existing && existing.length > 0) return existing;
    // 默认仅创建一个参数位，避免界面上无意义地出现「参数2」
    return [{ kind: 'link', linkType: '' } as any];
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
        <span className="text-xs text-gray-500">编辑模式：</span>
        <select
          value={editMode}
          onChange={(e) => {
            const m = e.target.value as 'visual' | 'advanced';
            setEditMode(m);
            if (m === 'visual') {
              const parsed = celExprToVisual(expr);
              if (parsed) setVisual(parsed);
            }
          }}
          disabled={disabled}
          className="border rounded px-2 py-1 text-sm"
        >
          <option value="visual">可视化</option>
          <option value="advanced">高级（源码）</option>
        </select>
      </div>
      {editMode === 'advanced' ? (
        <div className="space-y-2">
          <textarea
            value={expr}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono min-h-[80px] focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            placeholder="输入 CEL 表达式，如 size(links.xxx) == path_detail_count"
            spellCheck={false}
          />
          {expr.trim() && (
            <div className="p-2 bg-gray-50 border rounded text-xs font-mono text-gray-700 break-all whitespace-pre-wrap overflow-x-auto max-h-32 overflow-y-auto">
              {expr}
            </div>
          )}
        </div>
      ) : (
      <>
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
            <option value="variable">变量/属性引用</option>
            <option value="function_call">函数调用</option>
          </select>
          {visual.left.kind === 'variable' ? (
            <input
              type="text"
              value={visual.left.variableName ?? ''}
              onChange={(e) => setLeft({ variableName: e.target.value })}
              disabled={disabled}
              placeholder="属性名或衍生属性名"
              className="border rounded px-2 py-1.5 text-sm min-w-[150px] font-mono"
            />
          ) : visual.left.kind === 'function_call' ? (
            <>
              {(() => {
                const funcArgs = ensureFuncArgs('left');
                const arg1 = funcArgs[0];
                const arg2 = funcArgs[1];
                return (
                  <>
                    <input
                      type="text"
                      value={visual.left.funcName ?? ''}
                      onChange={(e) =>
                        setLeft({
                          funcName: e.target.value,
                          funcArgs,
                        })
                      }
                      disabled={disabled}
                      placeholder="函数名，如 detect_late_upload"
                      className="border rounded px-2 py-1.5 text-sm min-w-[180px] font-mono"
                    />
                    <span className="text-xs text-gray-500 ml-1">参数1：</span>
                    <select
                      value={arg1.kind}
                      onChange={(e) =>
                        setLeft({
                          funcArgs: [
                            {
                              ...arg1,
                              kind: e.target.value as any,
                            },
                            arg2,
                          ],
                        })
                      }
                      disabled={disabled}
                      className="border rounded px-2 py-1.5 text-sm"
                    >
                      <option value="link">关系</option>
                      <option value="variable">变量/属性</option>
                    </select>
                    {arg1.kind === 'link' ? (
                      <select
                        value={arg1.linkType ?? ''}
                        onChange={(e) =>
                          setLeft({
                            funcArgs: [
                              { ...arg1, linkType: e.target.value },
                              arg2,
                            ],
                          })
                        }
                        disabled={disabled}
                        className="border rounded px-2 py-1.5 text-sm min-w-[130px]"
                      >
                        <option value="">请选择关系</option>
                        {relations.map((r) => (
                          <option key={r.name} value={r.name}>
                            {relationDisplay(r)}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <input
                        type="text"
                        value={arg1.variableName ?? ''}
                        onChange={(e) =>
                          setLeft({
                            funcArgs: [
                              { ...arg1, variableName: e.target.value },
                              arg2,
                            ],
                          })
                        }
                        disabled={disabled}
                        placeholder="变量/属性名，如 split_time"
                        className="border rounded px-2 py-1.5 text-sm min-w-[150px] font-mono"
                      />
                    )}
                    {/* 参数2：真正需要第二个参数时才显示，避免所有函数都固定有「参数2」 */}
                    {arg2 ? (
                      <>
                        <span className="text-xs text-gray-500 ml-2">参数2（可选）：</span>
                        <select
                          value={arg2.kind}
                          onChange={(e) =>
                            setLeft({
                              funcArgs: [
                                arg1,
                                {
                                  ...arg2,
                                  kind: e.target.value as any,
                                },
                              ],
                            })
                          }
                          disabled={disabled}
                          className="border rounded px-2 py-1.5 text-sm"
                        >
                          <option value="link">关系</option>
                          <option value="variable">变量/属性</option>
                        </select>
                        {arg2.kind === 'link' ? (
                          <select
                            value={arg2.linkType ?? ''}
                            onChange={(e) =>
                              setLeft({
                                funcArgs: [
                                  arg1,
                                  { ...arg2, linkType: e.target.value },
                                ],
                              })
                            }
                            disabled={disabled}
                            className="border rounded px-2 py-1.5 text-sm min-w-[130px]"
                          >
                            <option value="">请选择关系</option>
                            {relations.map((r) => (
                              <option key={r.name} value={r.name}>
                                {relationDisplay(r)}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <input
                            type="text"
                            value={arg2.variableName ?? ''}
                            onChange={(e) =>
                              setLeft({
                                funcArgs: [
                                  arg1,
                                  { ...arg2, variableName: e.target.value },
                                ],
                              })
                            }
                            disabled={disabled}
                            placeholder="变量/属性名，如 split_time"
                            className="border rounded px-2 py-1.5 text-sm min-w-[150px] font-mono"
                          />
                        )}
                        <button
                          type="button"
                          onClick={() =>
                            setLeft({
                              funcArgs: [arg1],
                            })
                          }
                          disabled={disabled}
                          className="ml-2 text-xs text-gray-500 hover:text-gray-700"
                        >
                          移除参数2
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        onClick={() =>
                          setLeft({
                            funcArgs: [
                              arg1,
                              { kind: 'variable', variableName: '' } as any,
                            ],
                          })
                        }
                        disabled={disabled}
                        className="ml-2 text-xs text-blue-600 hover:text-blue-800"
                      >
                        + 添加参数2
                      </button>
                    )}
                  </>
                );
              })()}
            </>
          ) : (
          <>
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
                <option value="variable">变量/属性引用</option>
                <option value="function_call">函数调用</option>
              </select>
              {visual.right.kind === 'variable' ? (
                <input
                  type="text"
                  value={visual.right.variableName ?? ''}
                  onChange={(e) => setRight({ variableName: e.target.value })}
                  disabled={disabled}
                  placeholder="属性名或衍生属性名"
                  className="border rounded px-2 py-1.5 text-sm min-w-[150px] font-mono"
                />
              ) : visual.right.kind === 'function_call' ? (
                <>
                  {(() => {
                    const funcArgs = ensureFuncArgs('right');
                    const arg1 = funcArgs[0];
                    const arg2 = funcArgs[1];
                    return (
                      <>
                        <input
                          type="text"
                          value={visual.right.funcName ?? ''}
                          onChange={(e) =>
                            setRight({
                              funcName: e.target.value,
                              funcArgs,
                            })
                          }
                          disabled={disabled}
                          placeholder="函数名，如 detect_late_upload"
                          className="border rounded px-2 py-1.5 text-sm min-w-[180px] font-mono"
                        />
                        <span className="text-xs text-gray-500 ml-1">参数1：</span>
                        <select
                          value={arg1.kind}
                          onChange={(e) =>
                            setRight({
                              funcArgs: [
                                {
                                  ...arg1,
                                  kind: e.target.value as any,
                                },
                                arg2,
                              ],
                            })
                          }
                          disabled={disabled}
                          className="border rounded px-2 py-1.5 text-sm"
                        >
                          <option value="link">关系</option>
                          <option value="variable">变量/属性</option>
                        </select>
                        {arg1.kind === 'link' ? (
                          <select
                            value={arg1.linkType ?? ''}
                            onChange={(e) =>
                              setRight({
                                funcArgs: [
                                  { ...arg1, linkType: e.target.value },
                                  arg2,
                                ],
                              })
                            }
                            disabled={disabled}
                            className="border rounded px-2 py-1.5 text-sm min-w-[130px]"
                          >
                            <option value="">请选择关系</option>
                            {relations.map((r) => (
                              <option key={r.name} value={r.name}>
                                {relationDisplay(r)}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <input
                            type="text"
                            value={arg1.variableName ?? ''}
                            onChange={(e) =>
                              setRight({
                                funcArgs: [
                                  { ...arg1, variableName: e.target.value },
                                  arg2,
                                ],
                              })
                            }
                            disabled={disabled}
                            placeholder="变量/属性名，如 split_time"
                            className="border rounded px-2 py-1.5 text-sm min-w-[150px] font-mono"
                          />
                        )}
                        {arg2 ? (
                          <>
                            <span className="text-xs text-gray-500 ml-2">参数2（可选）：</span>
                            <select
                              value={arg2.kind}
                              onChange={(e) =>
                                setRight({
                                  funcArgs: [
                                    arg1,
                                    {
                                      ...arg2,
                                      kind: e.target.value as any,
                                    },
                                  ],
                                })
                              }
                              disabled={disabled}
                              className="border rounded px-2 py-1.5 text-sm"
                            >
                              <option value="link">关系</option>
                              <option value="variable">变量/属性</option>
                            </select>
                            {arg2.kind === 'link' ? (
                              <select
                                value={arg2.linkType ?? ''}
                                onChange={(e) =>
                                  setRight({
                                    funcArgs: [
                                      arg1,
                                      { ...arg2, linkType: e.target.value },
                                    ],
                                  })
                                }
                                disabled={disabled}
                                className="border rounded px-2 py-1.5 text-sm min-w-[130px]"
                              >
                                <option value="">请选择关系</option>
                                {relations.map((r) => (
                                  <option key={r.name} value={r.name}>
                                    {relationDisplay(r)}
                                  </option>
                                ))}
                              </select>
                            ) : (
                              <input
                                type="text"
                                value={arg2.variableName ?? ''}
                                onChange={(e) =>
                                  setRight({
                                    funcArgs: [
                                      arg1,
                                      { ...arg2, variableName: e.target.value },
                                    ],
                                  })
                                }
                                disabled={disabled}
                                placeholder="变量/属性名，如 split_time"
                                className="border rounded px-2 py-1.5 text-sm min-w-[150px] font-mono"
                              />
                            )}
                            <button
                              type="button"
                              onClick={() =>
                                setRight({
                                  funcArgs: [arg1],
                                })
                              }
                              disabled={disabled}
                              className="ml-2 text-xs text-gray-500 hover:text-gray-700"
                            >
                              移除参数2
                            </button>
                          </>
                        ) : (
                          <button
                            type="button"
                            onClick={() =>
                              setRight({
                                funcArgs: [
                                  arg1,
                                  { kind: 'variable', variableName: '' } as any,
                                ],
                              })
                            }
                            disabled={disabled}
                            className="ml-2 text-xs text-blue-600 hover:text-blue-800"
                          >
                            + 添加参数2
                          </button>
                        )}
                      </>
                    );
                  })()}
                </>
              ) : (
          <>
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
      </>
      )}
    </div>
  );
}
