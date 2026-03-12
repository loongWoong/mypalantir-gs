import React, { useState, useEffect, useCallback } from 'react';
import {
  PlusIcon,
  TrashIcon,
  CodeBracketIcon,
  Squares2X2Icon,
  ClipboardDocumentIcon,
  ExclamationTriangleIcon,
} from '@heroicons/react/24/outline';
import type { SwrlVisualRule, SwrlCondition, SwrlConclusion } from '../../utils/ruleExprEditor';
import {
  swrlVisualToExpr,
  swrlExprToVisual,
  fallbackVisualFromExpr,
  getConclusionFromRuleExpr,
} from '../../utils/ruleExprEditor';
/** 供下拉使用的实体摘要（属性可为衍生属性） */
export interface EntityOption {
  name: string;
  display_name?: string;
  attributes: { name: string; display_name?: string; derived?: boolean }[];
}

/** 供下拉使用的关系摘要 */
export interface RelationOption {
  name: string;
  display_name?: string;
  source_type: string;
  target_type: string;
}

/** 函数摘要（仅展示用，不可编辑实现） */
export interface FunctionOption {
  name: string;
  display_name?: string;
  description?: string;
  inputs?: { name: string; type: string }[];
  parameter_bindings?: { parameter_name: string; source_type: string; link_name?: string }[];
}

export interface RuleExpressionEditorProps {
  language: string;
  expr: string;
  onChange: (expr: string) => void;
  /** 实体类型名列表（仅用于无 entities 时的主体下拉） */
  entityTypeNames: string[];
  /** 实体列表（含属性，含衍生属性），用于属性下拉与自然语言展示 */
  entities?: EntityOption[];
  /** 关系列表，用于关系下拉与自然语言展示 */
  relations?: RelationOption[];
  /** 函数列表（封装组件，仅展示不可改实现），用于规则前件中的函数条件 */
  functions?: FunctionOption[];
  /** 当前本体下全部规则（name, display_name, expr），用于析取条件中展示“某规则的结论” */
  rules?: { name: string; display_name?: string; expr: string }[];
  disabled?: boolean;
}

const DEFAULT_VISUAL_RULE: SwrlVisualRule = {
  subjectEntity: '',
  subjectVar: 'p',
  conditions: [],
  conclusion: { predicate: '', varName: 'p', value: '' },
  extraVars: [],
};

function entityDisplay(e: EntityOption | undefined): string {
  return e?.display_name || e?.name || '';
}

function attrDisplay(a: { name: string; display_name?: string; derived?: boolean }): string {
  const base = a.display_name || a.name;
  return a.derived ? `${base} (衍生)` : base;
}

function relationDisplay(r: RelationOption | undefined): string {
  return r?.display_name || r?.name || '';
}

export function RuleExpressionEditor({
  language,
  expr,
  onChange,
  entityTypeNames,
  entities = [],
  relations = [],
  functions = [],
  rules = [],
  disabled = false,
}: RuleExpressionEditorProps) {
  const isSwrl = language === 'swrl';
  const [editMode, setEditMode] = useState<'visual' | 'advanced'>(isSwrl ? 'visual' : 'advanced');
  const [visualRule, setVisualRule] = useState<SwrlVisualRule>(() => {
    if (!isSwrl || !expr.trim()) return { ...DEFAULT_VISUAL_RULE };
    const parsed = swrlExprToVisual(expr, rules);
    return parsed ?? { ...DEFAULT_VISUAL_RULE };
  });
  /** 额外变量（如 ?v）对应的实体类型，用于下拉与自然语言 */
  const [extraVarEntityTypes, setExtraVarEntityTypes] = useState<Record<string, string>>({});
  const [parseWarning, setParseWarning] = useState<string | null>(null);
  const [showSwitchConfirm, setShowSwitchConfirm] = useState(false);
  const [copiedPreview, setCopiedPreview] = useState(false);

  const entityList = entities.length > 0 ? entities : entityTypeNames.map((n) => ({ name: n, attributes: [] }));
  const getEntity = useCallback((name: string) => entityList.find((e) => e.name === name), [entityList]);

  // 仅当 expr 从外部发生变化时（如加载/切换规则），才将其解析同步到 visualRule。
  // 使用 ref 跟踪"本组件最后一次生成的 expr"，避免把自己 onChange 触发的 prop 更新再次解析回来，造成循环。
  const lastGeneratedExprRef = React.useRef<string>('');

  useEffect(() => {
    if (!isSwrl) {
      setEditMode('advanced');
      return;
    }
    // 如果这次 expr 就是本组件上次生成的，跳过解析（避免循环）
    if (expr === lastGeneratedExprRef.current) return;

    if (!expr.trim()) {
      setVisualRule({ ...DEFAULT_VISUAL_RULE });
      setExtraVarEntityTypes({});
      setParseWarning(null);
      return;
    }
    const parsed = swrlExprToVisual(expr, rules);
    if (parsed) {
      setVisualRule(parsed);
      setParseWarning(null);
    } else {
      // 解析失败时清空条件，避免显示上一条规则的内容；尝试从 expr 提取主体与结论
      const fallback = fallbackVisualFromExpr(expr);
      setVisualRule(fallback);
      setParseWarning('当前表达式无法解析为可视化形式，请使用「高级」模式编辑。');
    }
  }, [expr, language, isSwrl, rules]);

  // 唯一的 visual → expr 同步出口，避免 useEffect 监听 visualRule 造成的双重触发。
  const setVisualRuleAndFlush = useCallback((next: SwrlVisualRule) => {
    setVisualRule(next);
    const generated = swrlVisualToExpr(next);
    lastGeneratedExprRef.current = generated;
    onChange(generated);
  }, [onChange]);

  /** 变量选项：主体 + 所有额外变量，用于下拉；每个选项对应“本体变量”的展示（实体类型） */
  const variableOptions = React.useMemo(() => {
    const opts: { value: string; label: string; entityType: string }[] = [];
    const subjEnt = getEntity(visualRule.subjectEntity);
    opts.push({
      value: visualRule.subjectVar,
      label: `主体（${entityDisplay(subjEnt) || visualRule.subjectEntity || '未选'}）`,
      entityType: visualRule.subjectEntity,
    });
    visualRule.extraVars.forEach((v) => {
      const et = extraVarEntityTypes[v] ?? '';
      const ent = getEntity(et);
      opts.push({
        value: v,
        label: et ? `${entityDisplay(ent) || et}（${v}）` : `${v}（未选类型）`,
        entityType: et,
      });
    });
    return opts;
  }, [visualRule.subjectVar, visualRule.subjectEntity, visualRule.extraVars, extraVarEntityTypes, getEntity]);

  const setExtraVarType = (varName: string, entityType: string) => {
    setExtraVarEntityTypes((prev) => ({ ...prev, [varName]: entityType }));
  };

  const addCondition = () => {
    setVisualRuleAndFlush({
      ...visualRule,
      conditions: [
        ...visualRule.conditions,
        { kind: 'predicate', predicate: '', varName: visualRule.subjectVar, value: true },
      ],
    });
  };

  /** 根据函数的 parameter_bindings 生成 argExprs（links(?var, link_name) 或 ?var） */
  const buildArgExprsForFunction = useCallback(
    (fn: FunctionOption): string[] => {
      const subjectVar = visualRule.subjectVar;
      const q = (v: string) => (v.startsWith('?') ? v : `?${v}`);
      const bindings = fn.parameter_bindings ?? [];
      if (fn.inputs?.length) {
        return fn.inputs.map((inp) => {
          const b = bindings.find((x) => x.parameter_name === inp.name);
          if (b?.source_type === 'link' && b?.link_name)
            return `links(${q(subjectVar)}, ${b.link_name})`;
          return q(subjectVar);
        });
      }
      if (bindings.length) {
        return bindings.map((b) =>
          b.source_type === 'link' && b.link_name
            ? `links(${q(subjectVar)}, ${b.link_name})`
            : q(subjectVar)
        );
      }
      return [q(subjectVar)];
    },
    [visualRule.subjectVar]
  );

  const addFunctionCondition = (fn: FunctionOption) => {
    const argExprs = buildArgExprsForFunction(fn);
    setVisualRuleAndFlush({
      ...visualRule,
      conditions: [
        ...visualRule.conditions,
        { kind: 'function', funcName: fn.name, argExprs, expectedValue: true },
      ],
    });
  };

  /** 添加“满足某规则的结论”条件（规则嵌套） */
  const addRuleConclusionCondition = (rule: { name: string; display_name?: string; expr: string }) => {
    const c = getConclusionFromRuleExpr(rule.expr);
    if (!c) return;
    setVisualRuleAndFlush({
      ...visualRule,
      conditions: [
        ...visualRule.conditions,
        {
          kind: 'ruleConclusion',
          ruleName: rule.name,
          varName: visualRule.subjectVar,
          predicate: c.predicate,
          value: c.value,
        },
      ],
    });
  };

  const updateCondition = (index: number, upd: Partial<SwrlCondition> | SwrlCondition) => {
    const next = [...visualRule.conditions];
    const cur = next[index];
    if (cur.kind === 'predicate' && 'kind' in upd && upd.kind === 'relation') {
      const newVar2 = 'v';
      if (!visualRule.extraVars.includes(newVar2)) {
        setVisualRule((r) => ({
          ...r,
          extraVars: [...r.extraVars, newVar2],
        }));
      }
      next[index] = { kind: 'relation', predicate: '', var1: visualRule.subjectVar, var2: newVar2 };
    } else if (cur.kind === 'relation' && 'kind' in upd && upd.kind === 'predicate') {
      next[index] = { kind: 'predicate', predicate: '', varName: visualRule.subjectVar, value: true };
    } else if (cur.kind === 'function' && 'expectedValue' in upd) {
      next[index] = { ...cur, expectedValue: upd.expectedValue as boolean };
    } else if (cur.kind === 'ruleConclusion' && 'ruleName' in upd && upd.ruleName) {
      const r = rules.find((x) => x.name === upd.ruleName);
      const c = r ? getConclusionFromRuleExpr(r.expr) : null;
      if (c)
        next[index] = {
          kind: 'ruleConclusion',
          ruleName: r!.name,
          varName: cur.varName,
          predicate: c.predicate,
          value: c.value,
        };
      else next[index] = { ...cur, ...upd } as SwrlCondition;
    } else {
      next[index] = { ...cur, ...upd } as SwrlCondition;
    }
    setVisualRuleAndFlush({ ...visualRule, conditions: next });
  };

  const removeCondition = (index: number) => {
    const next = visualRule.conditions.filter((_, i) => i !== index);
    setVisualRuleAndFlush({ ...visualRule, conditions: next });
  };

  /** 结论（后件）的 predicate 固定为 check_status，不作为本体属性选择 */
  const CONCLUSION_PREDICATE = 'check_status';
  const setConclusion = (upd: Partial<SwrlConclusion>) => {
    setVisualRuleAndFlush({
      ...visualRule,
      conclusion: { ...visualRule.conclusion, predicate: CONCLUSION_PREDICATE, ...upd },
    });
  };

  const switchToVisual = (force = false) => {
    if (!expr.trim()) {
      setVisualRule({ ...DEFAULT_VISUAL_RULE });
      setExtraVarEntityTypes({});
      setParseWarning(null);
      setEditMode('visual');
      setShowSwitchConfirm(false);
      onChange(swrlVisualToExpr(DEFAULT_VISUAL_RULE));
      return;
    }
    const parsed = swrlExprToVisual(expr);
    if (parsed) {
      setVisualRule(parsed);
      setParseWarning(null);
      setEditMode('visual');
      setShowSwitchConfirm(false);
    } else if (!force) {
      setShowSwitchConfirm(true);
    } else {
      setParseWarning('当前表达式包含高级语法，部分内容可能无法在可视化模式中编辑。');
      setVisualRule({ ...DEFAULT_VISUAL_RULE });
      setEditMode('visual');
      setShowSwitchConfirm(false);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopiedPreview(true);
      setTimeout(() => setCopiedPreview(false), 2000);
    });
  };

  /** 某变量对应的实体类型 */
  const getVarEntityType = (varName: string): string => {
    if (varName === visualRule.subjectVar) return visualRule.subjectEntity;
    return extraVarEntityTypes[varName] ?? '';
  };

  /** 某实体类型的属性列表（用于谓词下拉） */
  const getAttributesForVar = (varName: string): { name: string; display_name?: string }[] => {
    const et = getVarEntityType(varName);
    const ent = getEntity(et);
    return ent?.attributes ?? [];
  };

  /** CEL：根据关系名取目标实体属性（links.xxx 指向的目标类型上的属性） */
  // const getAttributesForLinkType = (linkTypeName: string): { name: string; display_name?: string }[] => {
  //   const rel = relations.find((r) => r.name === linkTypeName);
  //   if (!rel) return [];
  //   const ent = getEntity(rel.target_type);
  //   return ent?.attributes ?? [];
  // };

  return (
    <div className="space-y-3">
      {isSwrl && (
        <div className="flex items-center justify-between gap-2">
          <span className="text-sm font-medium text-gray-700">编辑方式</span>
          <div className="flex rounded-md border border-gray-200 p-0.5 bg-gray-50">
            <button
              type="button"
              onClick={() => setEditMode('advanced')}
              className={`flex items-center gap-1 px-2 py-1 text-xs rounded ${
                editMode === 'advanced' ? 'bg-white shadow text-blue-700' : 'text-gray-600'
              }`}
              title="直接编辑 SWRL 源码"
            >
              <CodeBracketIcon className="w-4 h-4" />
              高级
            </button>
            <button
              type="button"
              onClick={() => switchToVisual()}
              className={`flex items-center gap-1 px-2 py-1 text-xs rounded ${
                editMode === 'visual' ? 'bg-white shadow text-blue-700' : 'text-gray-600'
              }`}
              title="通过表单填写，无需记语法"
            >
              <Squares2X2Icon className="w-4 h-4" />
              可视化
            </button>
          </div>
        </div>
      )}

      {/* 模式切换确认对话框 */}
      {showSwitchConfirm && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
          <div className="flex items-start gap-3">
            <ExclamationTriangleIcon className="w-5 h-5 text-amber-500 shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="text-sm font-medium text-amber-800">
                当前表达式包含高级语法（如析取、不等式）
              </p>
              <p className="text-sm text-amber-700 mt-1">
                切换到可视化模式将清空当前表达式，部分语法无法在可视化编辑器中表示。
              </p>
              <div className="flex gap-2 mt-3">
                <button
                  type="button"
                  onClick={() => switchToVisual(true)}
                  className="px-3 py-1.5 text-sm bg-amber-600 text-white rounded hover:bg-amber-700"
                >
                  仍然切换
                </button>
                <button
                  type="button"
                  onClick={() => setShowSwitchConfirm(false)}
                  className="px-3 py-1.5 text-sm bg-white border border-gray-300 text-gray-700 rounded hover:bg-gray-50"
                >
                  取消
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {parseWarning && !showSwitchConfirm && (
        <div className="text-amber-700 bg-amber-50 border border-amber-200 rounded px-3 py-2 text-sm">
          {parseWarning}
        </div>
      )}

      {isSwrl && editMode === 'visual' ? (
        <div className="space-y-4 border border-gray-200 rounded-lg p-4 bg-gray-50/50">
          {/* 主体：实体类型 + 变量（用主体描述） */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">主体实体类型</label>
              <select
                value={visualRule.subjectEntity}
                onChange={(e) => setVisualRuleAndFlush({ ...visualRule, subjectEntity: e.target.value })}
                disabled={disabled}
                className="w-full border rounded px-3 py-2 text-sm"
              >
                <option value="">请选择实体</option>
                {entityList.map((e) => (
                  <option key={e.name} value={e.name}>
                    {entityDisplay(e) || e.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">主体变量</label>
              <select
                value={visualRule.subjectVar}
                onChange={(e) => setVisualRuleAndFlush({ ...visualRule, subjectVar: e.target.value.trim() || 'p' })}
                disabled={disabled}
                className="w-full border rounded px-3 py-2 text-sm"
              >
                <option value={visualRule.subjectVar}>
                  {variableOptions[0]?.label ?? `主体（${entityDisplay(getEntity(visualRule.subjectEntity)) || '未选'}）`}
                </option>
              </select>
            </div>
          </div>

          {/* 条件：自然语言 + 内联下拉（衍生属性 / 函数，函数仅展示不可改实现） */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="block text-sm font-medium text-gray-700">条件（前件）</label>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={addCondition}
                  disabled={disabled}
                  className="text-xs text-blue-600 hover:text-blue-800 flex items-center gap-1"
                >
                  <PlusIcon className="w-4 h-4" />
                  添加属性/衍生属性条件
                </button>
                {functions.length > 0 && (
                  <select
                    value=""
                    onChange={(e) => {
                      const name = e.target.value;
                      if (!name) return;
                      const fn = functions.find((f) => f.name === name);
                      if (fn) addFunctionCondition(fn);
                      e.target.value = '';
                    }}
                    disabled={disabled}
                    className="text-xs border rounded px-2 py-1 text-blue-600 hover:text-blue-800"
                  >
                    <option value="">添加函数条件 ▼</option>
                    {functions.map((f) => (
                      <option key={f.name} value={f.name}>
                        {f.display_name || f.name}（{f.name}）
                      </option>
                    ))}
                  </select>
                )}
                {rules.length > 0 && (
                  <select
                    value=""
                    onChange={(e) => {
                      const name = e.target.value;
                      if (!name) return;
                      const rule = rules.find((r) => r.name === name);
                      if (rule) addRuleConclusionCondition(rule);
                      e.target.value = '';
                    }}
                    disabled={disabled}
                    className="text-xs border rounded px-2 py-1 text-amber-700 hover:text-amber-800 border-amber-300"
                  >
                    <option value="">添加规则结论条件 ▼</option>
                    {rules.map((r) => (
                      <option key={r.name} value={r.name}>
                        {r.display_name || r.name}
                      </option>
                    ))}
                  </select>
                )}
              </div>
            </div>
            <div className="space-y-3">
              {visualRule.conditions.length === 0 ? (
                <div className="text-gray-500 text-sm py-2">暂无条件，可添加「属性/衍生属性条件」「函数条件」或「规则结论条件」</div>
              ) : (
                visualRule.conditions.map((c, i) => (
                  <div key={i} className="flex items-start gap-2 p-3 bg-white border rounded-lg min-w-0 overflow-hidden">
                    <span className="text-gray-500 text-sm shrink-0 pt-1.5">
                      {i === 0 ? '当' : '且'}
                    </span>
                    {c.kind === 'function' ? (
                      <div className="flex flex-wrap items-start gap-2 flex-1 min-w-0">
                        <div className="flex-1 min-w-0 rounded bg-gray-50 border border-gray-200 px-3 py-2 overflow-hidden">
                          <div className="text-sm font-medium text-gray-800">
                            函数：{functions.find((f) => f.name === c.funcName)?.display_name || c.funcName}
                          </div>
                          {functions.find((f) => f.name === c.funcName)?.description && (
                            <div className="text-xs text-gray-500 mt-0.5">
                              {functions.find((f) => f.name === c.funcName)?.description}
                            </div>
                          )}
                          <div className="text-xs text-gray-400 mt-1 font-mono break-all whitespace-pre-wrap">
                            {c.funcName}({c.argExprs.join(', ')}) == {String(c.expectedValue)}
                          </div>
                        </div>
                        <span className="text-gray-600 text-sm">为</span>
                        <select
                          value={String(c.expectedValue)}
                          onChange={(e) => updateCondition(i, { expectedValue: e.target.value === 'true' })}
                          disabled={disabled}
                          className="border rounded px-2 py-1.5 text-sm"
                        >
                          <option value="true">是 (true)</option>
                          <option value="false">否 (false)</option>
                        </select>
                      </div>
                    ) : c.kind === 'ruleConclusion' ? (
                      <div className="flex flex-wrap items-center gap-2 flex-1 min-w-0 rounded bg-amber-50 border border-amber-200 px-3 py-2">
                        <span className="text-amber-800 text-sm shrink-0">规则</span>
                        <select
                          value={c.ruleName}
                          onChange={(e) => updateCondition(i, { ruleName: e.target.value })}
                          disabled={disabled}
                          className="border border-amber-300 rounded px-2 py-1.5 text-sm bg-white min-w-[180px]"
                        >
                          {rules.map((r) => (
                            <option key={r.name} value={r.name}>
                              {r.display_name || r.name}
                            </option>
                          ))}
                        </select>
                        <span className="text-amber-800 text-sm">的结论</span>
                        <span className="text-amber-700 font-medium">
                          （{c.predicate} = {String(c.value)}）
                        </span>
                      </div>
                    ) : c.kind === 'disjunction' ? (
                      <div className="flex flex-wrap items-start gap-2 flex-1 min-w-0 rounded bg-amber-50 border border-amber-200 px-3 py-2">
                        <span className="text-amber-800 text-sm">（</span>
                        {c.disjuncts.map((d, di) => (
                          <React.Fragment key={di}>
                            {di > 0 && <span className="text-amber-800 text-sm"> 或 </span>}
                            {d.kind === 'predicate' && (
                              <span className="text-sm font-mono text-gray-700">
                                {d.predicate}(?{d.varName}, {String(d.value)})
                              </span>
                            )}
                            {d.kind === 'ruleConclusion' && (() => {
                              const r = rules.find((x) => x.name === d.ruleName);
                              return (
                                <span className="text-sm">
                                  规则「{r?.display_name || r?.name || d.ruleName}」的结论
                                  <span className="text-amber-700 font-medium ml-1">
                                    （{d.predicate} = {String(d.value)}）
                                  </span>
                                </span>
                              );
                            })()}
                            {d.kind === 'function' && (
                              <span className="text-xs font-mono">
                                {d.funcName}({d.argExprs.join(', ')}) == {String(d.expectedValue)}
                              </span>
                            )}
                            {d.kind === 'relation' && (
                              <span className="text-sm">
                                {d.predicate}(?{d.var1}, ?{d.var2})
                              </span>
                            )}
                          </React.Fragment>
                        ))}
                        <span className="text-amber-800 text-sm">）</span>
                      </div>
                    ) : c.kind === 'predicate' ? (
                      <div className="flex flex-wrap items-center gap-1.5 flex-1">
                        <select
                          value={c.varName}
                          onChange={(e) => updateCondition(i, { varName: e.target.value })}
                          disabled={disabled}
                          className="border rounded px-2 py-1.5 text-sm min-w-[120px]"
                        >
                          {variableOptions.map((o) => (
                            <option key={o.value} value={o.value}>
                              {o.label}
                            </option>
                          ))}
                        </select>
                        <span className="text-gray-600 text-sm">的</span>
                        <select
                          value={c.predicate}
                          onChange={(e) => updateCondition(i, { predicate: e.target.value })}
                          disabled={disabled}
                          className="border rounded px-2 py-1.5 text-sm min-w-[140px]"
                        >
                          <option value="">请选择属性</option>
                          {getAttributesForVar(c.varName).map((a) => (
                            <option key={a.name} value={a.name}>
                              {attrDisplay(a)}
                            </option>
                          ))}
                        </select>
                        <span className="text-gray-600 text-sm">为</span>
                        <select
                          value={
                            typeof c.value === 'boolean'
                              ? String(c.value)
                              : ['正常', '不正常', '费用异常'].includes(String(c.value))
                              ? c.value
                              : 'other'
                          }
                          onChange={(e) => {
                            const v = e.target.value;
                            if (v === 'true' || v === 'false') updateCondition(i, { value: v === 'true' });
                            else if (v === 'other') updateCondition(i, { value: '' });
                            else updateCondition(i, { value: v });
                          }}
                          disabled={disabled}
                          className="border rounded px-2 py-1.5 text-sm"
                        >
                          <option value="true">是 (true)</option>
                          <option value="false">否 (false)</option>
                          <option value="正常">正常</option>
                          <option value="不正常">不正常</option>
                          <option value="费用异常">费用异常</option>
                          <option value="other">其他...</option>
                        </select>
                        {(typeof c.value === 'string' && !['正常', '不正常', '费用异常'].includes(c.value)) && (
                          <input
                            type="text"
                            value={c.value}
                            onChange={(e) => updateCondition(i, { value: e.target.value })}
                            disabled={disabled}
                            placeholder="值"
                            className="border rounded px-2 py-1.5 text-sm w-24"
                          />
                        )}
                      </div>
                    ) : (
                      <div className="flex flex-wrap items-center gap-1.5 flex-1">
                        <select
                          value={c.var1}
                          onChange={(e) => updateCondition(i, { var1: e.target.value })}
                          disabled={disabled}
                          className="border rounded px-2 py-1.5 text-sm min-w-[120px]"
                        >
                          {variableOptions.map((o) => (
                            <option key={o.value} value={o.value}>
                              {o.label}
                            </option>
                          ))}
                        </select>
                        <span className="text-gray-600 text-sm">通过</span>
                        <select
                          value={c.predicate}
                          onChange={(e) => {
                            const relName = e.target.value;
                            updateCondition(i, { predicate: relName });
                            const rel = relations.find((r) => r.name === relName);
                            if (rel && c.var2) setExtraVarType(c.var2, rel.target_type);
                          }}
                          disabled={disabled}
                          className="border rounded px-2 py-1.5 text-sm min-w-[140px]"
                        >
                          <option value="">请选择关系</option>
                          {relations.map((r) => (
                            <option key={r.name} value={r.name}>
                              {relationDisplay(r)}
                            </option>
                          ))}
                        </select>
                        <span className="text-gray-600 text-sm">关联到</span>
                        <select
                          value={c.var2}
                          onChange={(e) => updateCondition(i, { var2: e.target.value })}
                          disabled={disabled}
                          className="border rounded px-2 py-1.5 text-sm min-w-[120px]"
                        >
                          {variableOptions.map((o) => (
                            <option key={o.value} value={o.value}>
                              {o.label}
                            </option>
                          ))}
                        </select>
                        {/* 为 var2 绑定实体类型（可修改） */}
                        <span className="flex items-center gap-1 text-xs text-gray-500">
                          （类型：
                          <select
                            value={extraVarEntityTypes[c.var2] ?? ''}
                            onChange={(e) => setExtraVarType(c.var2, e.target.value)}
                            disabled={disabled}
                            className="border rounded px-1.5 py-1 text-xs max-w-[100px]"
                          >
                            <option value="">请选择</option>
                            {entityList.map((e) => (
                              <option key={e.name} value={e.name}>
                                {entityDisplay(e) || e.name}
                              </option>
                            ))}
                          </select>
                          ）
                        </span>
                      </div>
                    )}
                    <button
                      type="button"
                      onClick={() => removeCondition(i)}
                      disabled={disabled}
                      className="text-red-500 hover:text-red-700 shrink-0"
                    >
                      <TrashIcon className="w-4 h-4" />
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* 结论（后件）：check_status 为默认控件，不绑定本体属性选择 */}
          <div className="border-t pt-4">
            <label className="block text-sm font-medium text-gray-700 mb-2">结论（后件）</label>
            <div className="flex flex-wrap items-center gap-1.5 p-3 bg-white border rounded-lg">
              <span className="text-gray-600 text-sm">则 将</span>
              <select
                value={visualRule.conclusion.varName}
                onChange={(e) => setConclusion({ varName: e.target.value })}
                disabled={disabled}
                className="border rounded px-2 py-1.5 text-sm min-w-[120px]"
              >
                {variableOptions.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
              <span className="text-gray-600 text-sm">的</span>
              <input
                value={visualRule.conclusion.predicate}
                onChange={(e) => setConclusion({ predicate: e.target.value })}
                disabled={disabled}
                placeholder="谓词名"
                className="border rounded px-2 py-1.5 text-sm font-medium bg-gray-100 border-gray-200 min-w-[140px]"
              />
              <span className="text-gray-600 text-sm">设为</span>
              <select
                value={
                  typeof visualRule.conclusion.value === 'boolean'
                    ? String(visualRule.conclusion.value)
                    : ['正常', '不正常', '费用异常'].includes(String(visualRule.conclusion.value))
                    ? visualRule.conclusion.value
                    : 'other'
                }
                onChange={(e) => {
                  const v = e.target.value;
                  if (v === 'true' || v === 'false') setConclusion({ value: v === 'true' });
                  else if (v === 'other') setConclusion({ value: '' });
                  else setConclusion({ value: v });
                }}
                disabled={disabled}
                className="border rounded px-2 py-1.5 text-sm"
              >
                <option value="true">是 (true)</option>
                <option value="false">否 (false)</option>
                <option value="正常">正常</option>
                <option value="不正常">不正常</option>
                <option value="费用异常">费用异常</option>
                <option value="other">其他...</option>
              </select>
              {(typeof visualRule.conclusion.value === 'string' &&
                !['正常', '不正常', '费用异常'].includes(visualRule.conclusion.value)) && (
                <input
                  type="text"
                  value={visualRule.conclusion.value}
                  onChange={(e) => setConclusion({ value: e.target.value })}
                  disabled={disabled}
                  placeholder="其他值"
                  className="border rounded px-2 py-1.5 text-sm w-24"
                />
              )}
            </div>
          </div>
        </div>
      ) : (
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {isSwrl ? 'SWRL 表达式（高级）' : '表达式'}
          </label>
          <textarea
            value={expr}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            className="w-full border rounded px-3 py-2 text-sm font-mono"
            rows={6}
            placeholder={
              '例: Passage(?p) ∧ detail_count_matched(?p, true) → check_status(?p, "正常")'
            }
          />
        </div>
      )}
    </div>
  );
}
