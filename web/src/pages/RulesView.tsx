import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import type { OntologyBuilderPayload, OntologyRulePayload } from '../api/client';
import { ontologyBuilderApi, modelApi } from '../api/client';
import { RuleExpressionEditor } from '../components/ontology/RuleExpressionEditor';
import {
  ShieldCheckIcon,
  InformationCircleIcon,
  PlusIcon,
  TrashIcon,
  ArrowDownTrayIcon,
  DocumentArrowUpIcon,
  FolderOpenIcon,
  MagnifyingGlassIcon,
  ChevronDownIcon,
  ChevronRightIcon,
} from '@heroicons/react/24/outline';

const DEFAULT_RULE: OntologyRulePayload = {
  name: '',
  display_name: '',
  description: '',
  language: 'swrl',
  expr: '',
};

function getScope(expr: string | undefined): string {
  if (!expr) return 'Unknown';
  const match = expr.match(/^(\w+)\(\?/);
  return match ? match[1] : 'Unknown';
}

/** 规则表达式：优先 expr，兼容后端返回的 swrl 字段 */
function getRuleExpr(rule: OntologyRulePayload & { swrl?: string }): string {
  return rule.expr ?? rule.swrl ?? '';
}

export default function RulesView() {
  const [fileList, setFileList] = useState<string[]>([]);
  const [selectedFilename, setSelectedFilename] = useState<string>('');
  const [schema, setSchema] = useState<OntologyBuilderPayload | null>(null);
  const [selectedRuleIndex, setSelectedRuleIndex] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingSchema, setLoadingSchema] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());

  const rules = schema?.rules ?? [];
  const selectedRule = selectedRuleIndex !== null && rules[selectedRuleIndex] ? rules[selectedRuleIndex] : null;

  const toggleGroupCollapse = (groupName: string) => {
    setCollapsedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(groupName)) {
        next.delete(groupName);
      } else {
        next.add(groupName);
      }
      return next;
    });
  };

  /** 从路径中取文件名（兼容 / 与 \） */
  const getBasename = (filePath: string) => {
    const normalized = filePath.replace(/\\/g, '/');
    const last = normalized.lastIndexOf('/');
    return last >= 0 ? normalized.slice(last + 1) : filePath;
  };

  const loadFileList = useCallback(async () => {
    try {
      const [files, currentModel] = await Promise.all([
        ontologyBuilderApi.listFiles(),
        modelApi.getCurrentModel().catch(() => null),
      ]);
      setFileList(files);
      if (files.length > 0) {
        const pathToUse = currentModel?.filePath;
        const match = pathToUse
          ? files.find((f) => f === getBasename(pathToUse) || pathToUse.endsWith(f) || pathToUse === f)
          : undefined;
        setSelectedFilename((prev) => (prev ? prev : match ?? files[0]));
      }
    } catch (e) {
      console.error('Failed to list ontology files:', e);
      setError('无法获取本体文件列表');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadFileList();
  }, [loadFileList]);

  useEffect(() => {
    if (!selectedFilename) {
      setSchema(null);
      setSelectedRuleIndex(null);
      return;
    }
    let cancelled = false;
    setLoadingSchema(true);
    setError(null);
    ontologyBuilderApi
      .loadFile(selectedFilename)
      .then((data) => {
        if (!cancelled) {
          setSchema(data);
          setSelectedRuleIndex(null);
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e?.response?.data?.message || e?.message || '加载文件失败');
          setSchema(null);
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingSchema(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedFilename]);

  const addRule = () => {
    if (!schema) return;
    const newRule: OntologyRulePayload = { ...DEFAULT_RULE, name: `rule_${Date.now()}` };
    setSchema({
      ...schema,
      rules: [...(schema.rules ?? []), newRule],
    });
    setSelectedRuleIndex((schema.rules ?? []).length);
  };

  const updateRule = (index: number, updates: Partial<OntologyRulePayload>) => {
    if (!schema?.rules) return;
    const next = [...schema.rules];
    if (index < 0 || index >= next.length) return;
    next[index] = { ...next[index], ...updates };
    setSchema({ ...schema, rules: next });
  };

  const deleteRule = (index: number) => {
    if (!schema?.rules) return;
    const next = schema.rules.filter((_, i) => i !== index);
    setSchema({ ...schema, rules: next });
    if (selectedRuleIndex === index) setSelectedRuleIndex(null);
    else if (selectedRuleIndex !== null && selectedRuleIndex > index) setSelectedRuleIndex(selectedRuleIndex - 1);
  };

  const saveSchema = async () => {
    if (!schema || !selectedFilename) {
      setError('请先选择并加载一个本体文件');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await ontologyBuilderApi.save(schema, selectedFilename);
    } catch (e: any) {
      setError(e?.response?.data?.message || e?.message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const objectTypes = schema?.object_types ?? [];
  const linkTypes = schema?.link_types ?? [];
  const entitiesForEditor = objectTypes.map((ot: Record<string, any>) => ({
    name: ot.name ?? '',
    display_name: ot.display_name,
    attributes: (ot.properties ?? []).map((p: Record<string, any>) => ({
      name: p.name ?? '',
      display_name: p.display_name,
      derived: p.derived === true,
    })),
  }));
  const functionsForEditor = (schema?.functions ?? []).map((f: Record<string, any>) => ({
    name: f.name ?? '',
    display_name: f.display_name,
    description: f.description,
    inputs: f.inputs ?? [],
    parameter_bindings: f.parameter_bindings ?? [],
  }));
  const relationsForEditor = linkTypes.map((lt: Record<string, any>) => ({
    name: lt.name ?? '',
    display_name: lt.display_name,
    source_type: lt.source_type ?? '',
    target_type: lt.target_type ?? '',
  }));
  const entityTypeNames = objectTypes.map((ot: Record<string, any>) => ot.name ?? '');

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-gray-500">加载文件列表...</div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* 文件选择与保存 */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 flex flex-wrap items-center gap-4">
        <div className="flex items-center gap-2">
          <FolderOpenIcon className="w-5 h-5 text-gray-500" />
          <label className="text-sm font-medium text-gray-700">本体文件</label>
          <select
            value={selectedFilename}
            onChange={(e) => setSelectedFilename(e.target.value)}
            className="border rounded-md px-3 py-2 text-sm min-w-[200px]"
          >
            <option value="">请选择</option>
            {fileList.map((f) => (
              <option key={f} value={f}>
                {f}
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          onClick={() => selectedFilename && loadFileList().then(() => ontologyBuilderApi.loadFile(selectedFilename).then(setSchema))}
          disabled={!selectedFilename || loadingSchema}
          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 disabled:opacity-50"
        >
          <ArrowDownTrayIcon className="w-4 h-4" />
          重新加载
        </button>
        <button
          type="button"
          onClick={saveSchema}
          disabled={!schema || saving}
          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:opacity-50"
        >
          <DocumentArrowUpIcon className="w-4 h-4" />
          {saving ? '保存中...' : '保存到本体文件'}
        </button>
        {error && <p className="text-sm text-red-600">{error}</p>}
      </div>

      {loadingSchema && (
        <div className="text-center py-8 text-gray-500">正在加载 Schema...</div>
      )}

      {!loadingSchema && !schema && selectedFilename && (
        <div className="bg-white rounded-lg border border-gray-200 p-8 text-center text-gray-500">
          未能加载该文件或文件为空，请检查后重试。
        </div>
      )}

      {!loadingSchema && schema && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* 规则列表 */}
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <h2 className="text-lg font-semibold text-gray-900 mb-3 flex items-center justify-between">
              <span className="flex items-center">
                <ShieldCheckIcon className="w-5 h-5 mr-2" />
                Rules ({rules.length})
              </span>
              <button
                type="button"
                onClick={addRule}
                className="px-2 py-1.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 flex items-center gap-1"
              >
                <PlusIcon className="w-4 h-4" />
                添加
              </button>
            </h2>
            {/* 搜索框 */}
            <div className="relative mb-3">
              <MagnifyingGlassIcon className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="搜索规则..."
                className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <div className="space-y-1 max-h-[calc(100vh-380px)] overflow-y-auto">
              {rules.length === 0 ? (
                <p className="text-sm text-gray-500 py-4">暂无规则，点击「添加」创建</p>
              ) : (
                (() => {
                  const colors = ['bg-blue-500', 'bg-yellow-500', 'bg-red-500', 'bg-green-500', 'bg-purple-500', 'bg-indigo-500', 'bg-pink-500', 'bg-teal-500'];
                  const filteredRules = rules.filter((r) => {
                    if (!searchQuery.trim()) return true;
                    const q = searchQuery.toLowerCase();
                    return (
                      (r.name ?? '').toLowerCase().includes(q) ||
                      (r.display_name ?? '').toLowerCase().includes(q) ||
                      (r.description ?? '').toLowerCase().includes(q)
                    );
                  });
                  const rulesByScope = objectTypes
                    .map((ot: Record<string, any>, idx: number) => ({
                      name: ot.name ?? '',
                      displayName: ot.display_name ?? ot.name ?? '',
                      color: colors[idx % colors.length],
                      rules: filteredRules.filter((r) => getScope(getRuleExpr(r)) === ot.name),
                    }))
                    .filter((group) => group.rules.length > 0);
                  const unknownRules = filteredRules.filter((r) => {
                    const scope = getScope(getRuleExpr(r));
                    return scope === 'Unknown' || !objectTypes.some((ot: Record<string, any>) => ot.name === scope);
                  });
                  if (unknownRules.length > 0) {
                    rulesByScope.push({
                      name: 'Unknown',
                      displayName: '未分类',
                      color: 'bg-gray-400',
                      rules: unknownRules,
                    });
                  }
                  if (rulesByScope.length === 0 && searchQuery.trim()) {
                    return <p className="text-sm text-gray-500 py-4 text-center">未找到匹配的规则</p>;
                  }
                  return rulesByScope.map((group) => (
                    <div key={group.name} className="border border-gray-100 rounded-lg overflow-hidden">
                      <button
                        type="button"
                        onClick={() => toggleGroupCollapse(group.name)}
                        className="w-full flex items-center gap-2 px-3 py-2 bg-gray-50 hover:bg-gray-100 transition-colors text-left"
                      >
                        {collapsedGroups.has(group.name) ? (
                          <ChevronRightIcon className="w-4 h-4 text-gray-500 shrink-0" />
                        ) : (
                          <ChevronDownIcon className="w-4 h-4 text-gray-500 shrink-0" />
                        )}
                        <span className={`w-2 h-2 rounded-full shrink-0 ${group.color}`} />
                        <span className="text-sm font-medium text-gray-700 truncate">{group.displayName}</span>
                        <span className="text-xs text-gray-400 ml-auto shrink-0">({group.rules.length})</span>
                      </button>
                      {!collapsedGroups.has(group.name) && (
                        <div className="divide-y divide-gray-50">
                          {group.rules.map((rule, i) => {
                            const ruleIndex = rules.indexOf(rule);
                            return (
                              <button
                                key={`${rule.name}-${i}`}
                                onClick={() => setSelectedRuleIndex(ruleIndex)}
                                className={`w-full text-left px-3 py-2 transition-colors ${
                                  selectedRuleIndex === ruleIndex
                                    ? 'bg-blue-50 text-blue-700'
                                    : 'hover:bg-gray-50 text-gray-700'
                                }`}
                              >
                                <div className="flex items-center justify-between gap-2">
                                  <div className="font-medium text-sm truncate">{rule.display_name || rule.name || '(未命名)'}</div>
                                  <span className="text-xs px-1.5 py-0.5 bg-indigo-100 text-indigo-700 rounded shrink-0">
                                    {rule.language}
                                  </span>
                                </div>
                              </button>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  ));
                })()
              )}
            </div>
          </div>

          {/* 规则详情与编辑 */}
          <div className="lg:col-span-2 space-y-6">
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">
                {selectedRule ? '编辑规则' : '规则详情'}
              </h2>
              {selectedRule ? (
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-xl font-bold text-gray-900">{selectedRule.display_name || selectedRule.name}</h3>
                    <button
                      type="button"
                      onClick={() => selectedRuleIndex !== null && deleteRule(selectedRuleIndex)}
                      className="text-red-500 hover:text-red-700"
                    >
                      <TrashIcon className="w-5 h-5" />
                    </button>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">名称 *</label>
                    <input
                      type="text"
                      value={selectedRule.name}
                      onChange={(e) => selectedRuleIndex !== null && updateRule(selectedRuleIndex, { name: e.target.value })}
                      className="w-full border rounded px-3 py-2 text-sm"
                      placeholder="规则唯一标识"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">显示名称</label>
                    <input
                      type="text"
                      value={selectedRule.display_name ?? ''}
                      onChange={(e) => selectedRuleIndex !== null && updateRule(selectedRuleIndex, { display_name: e.target.value })}
                      className="w-full border rounded px-3 py-2 text-sm"
                      placeholder="如：通行路径完整性正常"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">描述</label>
                    <textarea
                      value={selectedRule.description ?? ''}
                      onChange={(e) => selectedRuleIndex !== null && updateRule(selectedRuleIndex, { description: e.target.value })}
                      className="w-full border rounded px-3 py-2 text-sm"
                      rows={2}
                      placeholder="规则说明"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">语言</label>
                    <select
                      value={selectedRule.language}
                      onChange={(e) => selectedRuleIndex !== null && updateRule(selectedRuleIndex, { language: e.target.value })}
                      className="w-full border rounded px-3 py-2 text-sm"
                    >
                      <option value="swrl">SWRL</option>
                      <option value="cel">CEL</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">表达式 (expr) *</label>
                    <RuleExpressionEditor
                      language="swrl"
                      expr={getRuleExpr(selectedRule)}
                      onChange={(expr) => selectedRuleIndex !== null && updateRule(selectedRuleIndex, { expr })}
                      entityTypeNames={entityTypeNames}
                      entities={entitiesForEditor}
                      relations={relationsForEditor}
                      functions={functionsForEditor}
                      rules={rules.map((r) => ({ name: r.name, display_name: r.display_name, expr: getRuleExpr(r) }))}
                    />
                  </div>
                </div>
              ) : (
                <div className="text-center py-8 text-gray-500">
                  <InformationCircleIcon className="w-12 h-12 mx-auto mb-2 text-gray-400" />
                  <p>从左侧选择一条规则进行查看或编辑</p>
                  <p className="text-sm mt-1">规则与对象类型、关系同存于当前选中的本体文件中，保存后将写回整份 Schema</p>
                  <p className="text-sm mt-2">
                    规则可由<strong>衍生属性</strong>或<strong>函数</strong>组成：在「可视化」编辑下可添加「属性/衍生属性条件」或「函数条件」；
                    函数为封装组件，仅展示不可修改实现，请到 <Link to="/functions" className="text-blue-600 hover:underline">Functions</Link> 页管理定义。
                  </p>
                </div>
              )}
            </div>

            {/* 推理链展示（只读） - 动态根据本体模型中的 object_types 和规则的 getScope() 归类 */}
            {rules.length > 0 && (
              <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
                <h2 className="text-lg font-semibold text-gray-900 mb-4">推理链</h2>
                <div className="space-y-4">
                  {(() => {
                    const colors = ['bg-blue-600', 'bg-yellow-500', 'bg-red-500', 'bg-green-600', 'bg-purple-600', 'bg-indigo-600', 'bg-pink-500', 'bg-teal-500'];
                    const rulesByScope = objectTypes
                      .map((ot: Record<string, any>, idx: number) => ({
                        name: ot.name ?? '',
                        label: ot.display_name ? `${ot.name} (${ot.display_name})` : ot.name,
                        color: colors[idx % colors.length],
                        rules: rules.filter((r) => getScope(getRuleExpr(r)) === ot.name),
                      }))
                      .filter((group) => group.rules.length > 0);
                    return rulesByScope.map((level, idx) => (
                      <div key={level.name}>
                        <div className="flex items-center">
                          <div className={`w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-bold ${level.color}`}>
                            {idx + 1}
                          </div>
                          <div className="ml-3 font-semibold text-gray-900">{level.label}</div>
                          <span className="ml-2 text-xs text-gray-500">({level.rules.length} rules)</span>
                        </div>
                        <div className="ml-11 mt-2 space-y-1">
                          {level.rules.map((r, i) => (
                            <div
                              key={`${r.name}-${i}`}
                              className={`text-sm cursor-pointer hover:text-blue-600 ${selectedRule?.name === r.name ? 'text-blue-700 font-medium' : 'text-gray-600'}`}
                              onClick={() => setSelectedRuleIndex(rules.indexOf(r))}
                            >
                              {r.display_name || r.name}
                            </div>
                          ))}
                        </div>
                        {idx < rulesByScope.length - 1 && <div className="ml-3.5 my-1 border-l-2 border-gray-300 h-4" />}
                      </div>
                    ));
                  })()}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
