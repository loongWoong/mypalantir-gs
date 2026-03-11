import { useEffect, useState, useCallback } from 'react';
import type {
  OntologyBuilderPayload,
  FunctionPayload,
  FunctionInputPayload,
  ParameterBindingPayload,
} from '../api/client';
import { ontologyBuilderApi, reasoningApi, modelApi, instanceApi } from '../api/client';
import type { ModelInfo, ObjectType } from '../api/client';
import {
  CpuChipIcon,
  PlusIcon,
  TrashIcon,
  DocumentArrowUpIcon,
  FolderOpenIcon,
  ArrowDownTrayIcon,
  LinkIcon,
  CubeIcon,
  InformationCircleIcon,
  PlayIcon,
  XMarkIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  MagnifyingGlassIcon,
} from '@heroicons/react/24/outline';

const DEFAULT_FUNCTION: FunctionPayload = {
  name: '',
  display_name: '',
  description: '',
  implementation: 'builtin',
  implementation_type: 'builtin',
  parameters: [],
  inputs: [],
  return_type: 'boolean',
  output_type: 'boolean',
  parameter_bindings: [],
};

/** 兼容 ontology（parameters/return_type）与后端（input/output.type）及既有（inputs/output_type） */
function getParams(fn: FunctionPayload): FunctionInputPayload[] {
  const list = fn.parameters ?? fn.inputs ?? (fn as FunctionPayload & { input?: FunctionInputPayload[] }).input ?? [];
  return Array.isArray(list) ? list : [];
}
function getReturnType(fn: FunctionPayload): string {
  return (
    fn.return_type ??
    fn.output_type ??
    (fn.output && typeof fn.output === 'object' && 'type' in fn.output ? (fn.output as { type?: string }).type : undefined) ??
    'boolean'
  );
}
function getImplementation(fn: FunctionPayload): string {
  return fn.implementation_type ?? fn.implementation ?? 'builtin';
}

/** 从规则 SWRL 表达式中解析“作用域”（第一个谓词的主语类型），与 RulesView 一致 */
function getScope(expr: string | undefined): string {
  if (!expr) return 'Unknown';
  const match = expr.match(/^(\w+)\(\?/);
  return match ? match[1] : 'Unknown';
}

/** 从规则表达式中提取出现的函数名（与 schema 中函数名匹配） */
function getFunctionNamesInRule(expr: string | undefined, functionNames: string[]): string[] {
  if (!expr || functionNames.length === 0) return [];
  const found: string[] = [];
  for (const name of functionNames) {
    if (expr.includes(name + '(') || expr.includes(name + ' (')) found.push(name);
  }
  return found;
}

export default function FunctionsView() {
  const [fileList, setFileList] = useState<string[]>([]);
  const [selectedFilename, setSelectedFilename] = useState<string>('');
  const [schema, setSchema] = useState<OntologyBuilderPayload | null>(null);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingSchema, setLoadingSchema] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [testModalOpen, setTestModalOpen] = useState(false);
  const [testArgsJson, setTestArgsJson] = useState('[]');
  const [testResult, setTestResult] = useState<{ ok: boolean; value?: unknown; error?: string } | null>(null);
  const [testRunning, setTestRunning] = useState(false);
  // 按实例测试（规则上下文）：本体模型、根对象类型、实例
  const [testModelList, setTestModelList] = useState<ModelInfo[]>([]);
  const [testModelId, setTestModelId] = useState<string>('');
  const [testObjectTypes, setTestObjectTypes] = useState<ObjectType[]>([]);
  const [testObjectType, setTestObjectType] = useState<string>('');
  const [testInstanceOptions, setTestInstanceOptions] = useState<Array<{ id: string; label: string }>>([]);
  const [testInstanceId, setTestInstanceId] = useState<string>('');
  const [loadingTestData, setLoadingTestData] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());

  const functions = schema?.functions ?? [];
  const rules = schema?.rules ?? [];
  const selectedFn = selectedIndex !== null && functions[selectedIndex] ? functions[selectedIndex] : null;
  const objectTypes = schema?.object_types ?? [];
  const linkTypes = schema?.link_types ?? [];

  const toggleGroupCollapse = (groupName: string) => {
    setCollapsedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(groupName)) next.delete(groupName);
      else next.add(groupName);
      return next;
    });
  };

  const loadFileList = useCallback(async () => {
    try {
      const [files, currentModel] = await Promise.all([
        ontologyBuilderApi.listFiles(),
        modelApi.getCurrentModel().catch(() => null),
      ]);
      setFileList(files);
      if (files.length > 0) {
        const getBasename = (filePath: string) => {
          const normalized = filePath.replace(/\\/g, '/');
          const last = normalized.lastIndexOf('/');
          return last >= 0 ? normalized.slice(last + 1) : filePath;
        };
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
  }, []);

  useEffect(() => {
    if (!selectedFilename) {
      setSchema(null);
      setSelectedIndex(null);
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
          setSelectedIndex(null);
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

  const addFunction = () => {
    if (!schema) return;
    const newFn: FunctionPayload = { ...DEFAULT_FUNCTION, name: `fn_${Date.now()}` };
    setSchema({
      ...schema,
      functions: [...(schema.functions ?? []), newFn],
    });
    setSelectedIndex((schema.functions ?? []).length);
  };

  const updateFunction = (index: number, updates: Partial<FunctionPayload>) => {
    if (!schema?.functions) return;
    const next = [...schema.functions];
    if (index < 0 || index >= next.length) return;
    next[index] = { ...next[index], ...updates };
    setSchema({ ...schema, functions: next });
  };

  const deleteFunction = (index: number) => {
    if (!schema?.functions) return;
    const next = schema.functions.filter((_, i) => i !== index);
    setSchema({ ...schema, functions: next });
    if (selectedIndex === index) setSelectedIndex(null);
    else if (selectedIndex !== null && selectedIndex > index) setSelectedIndex(selectedIndex - 1);
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

  const addInput = (fnIndex: number) => {
    const fn = schema?.functions?.[fnIndex];
    if (!fn) return;
    const params = getParams(fn);
    const newParam: FunctionInputPayload = { name: `arg_${params.length + 1}`, type: 'list<?>' };
    const next = [...params, newParam];
    updateFunction(fnIndex, { parameters: next, inputs: next, input: next });
  };

  const updateInput = (fnIndex: number, inputIndex: number, updates: Partial<FunctionInputPayload>) => {
    const fn = schema?.functions?.[fnIndex];
    const params = fn ? getParams(fn) : [];
    if (inputIndex < 0 || inputIndex >= params.length) return;
    const next = params.map((inp, i) => (i === inputIndex ? { ...inp, ...updates } : inp));
    updateFunction(fnIndex, { parameters: next, inputs: next, input: next });
  };

  const removeInput = (fnIndex: number, inputIndex: number) => {
    const fn = schema?.functions?.[fnIndex];
    const params = fn ? getParams(fn) : [];
    const nameToRemove = params[inputIndex]?.name;
    if (nameToRemove == null) return;
    const next = params.filter((_, i) => i !== inputIndex);
    const bindings = (fn?.parameter_bindings ?? []).filter((b) => b.parameter_name !== nameToRemove);
    updateFunction(fnIndex, { parameters: next, inputs: next, input: next, parameter_bindings: bindings });
  };

  const setBinding = (fnIndex: number, parameterName: string, binding: ParameterBindingPayload | null) => {
    const fn = schema?.functions?.[fnIndex];
    if (!fn) return;
    let bindings = [...(fn.parameter_bindings ?? [])];
    bindings = bindings.filter((b) => b.parameter_name !== parameterName);
    if (binding) bindings.push(binding);
    updateFunction(fnIndex, { parameter_bindings: bindings });
  };

  const getBinding = (fn: FunctionPayload, parameterName: string): ParameterBindingPayload | undefined =>
    fn.parameter_bindings?.find((b) => b.parameter_name === parameterName);

  const getDerivedProperties = (objectType: Record<string, any> | undefined) =>
    objectType ? (objectType.properties ?? []).filter((p: Record<string, any>) => p.derived === true) : [];

  const runFunctionTest = useCallback(async () => {
    if (!selectedFn?.name) return;
    setTestRunning(true);
    setTestResult(null);
    try {
      const args = JSON.parse(testArgsJson) as unknown[];
      const value = await reasoningApi.testFunction(selectedFn.name, args);
      setTestResult({ ok: true, value });
    } catch (e) {
      setTestResult({
        ok: false,
        error: e instanceof Error ? e.message : String(e),
      });
    } finally {
      setTestRunning(false);
    }
  }, [selectedFn?.name, testArgsJson]);

  const openTestModal = useCallback(async () => {
    setTestArgsJson('[]');
    setTestResult(null);
    setTestModalOpen(true);
    setTestObjectType('');
    setTestInstanceId('');
    setTestInstanceOptions([]);
    try {
      const [models, current] = await Promise.all([
        modelApi.listModels(),
        modelApi.getCurrentModel().catch(() => null),
      ]);
      setTestModelList(models);
      if (current?.modelId) setTestModelId(current.modelId);
      else if (models.length > 0) setTestModelId(models[0].id);
      else setTestModelId('');
    } catch (e) {
      console.error('Load test models failed:', e);
    }
  }, []);

  // 切换本体模型时加载该模型下的对象类型
  useEffect(() => {
    if (!testModalOpen || !testModelId) {
      setTestObjectTypes([]);
      setTestObjectType('');
      setTestInstanceId('');
      setTestInstanceOptions([]);
      return;
    }
    let cancelled = false;
    modelApi
      .getObjectTypes(testModelId)
      .then((ots) => {
        if (!cancelled) {
          setTestObjectTypes(ots);
          setTestObjectType('');
          setTestInstanceId('');
          setTestInstanceOptions([]);
        }
      })
      .catch(() => {
        if (!cancelled) setTestObjectTypes([]);
      });
    return () => {
      cancelled = true;
    };
  }, [testModalOpen, testModelId]);

  // 切换根对象类型时加载实例列表（当前模型下的数据）
  useEffect(() => {
    if (!testModalOpen || !testObjectType || !testModelId) {
      setTestInstanceOptions([]);
      setTestInstanceId('');
      return;
    }
    let cancelled = false;
    setLoadingTestData(true);
    instanceApi
      .list(testObjectType, 0, 50)
      .then(({ items }) => {
        if (!cancelled) {
          const options = items.map((item: Record<string, unknown>) => {
            const id = (item.id ?? item.pass_id ?? Object.values(item)[0]) as string;
            const label = typeof id === 'string' ? id : JSON.stringify(id);
            return { id: String(id), label };
          });
          setTestInstanceOptions(options);
          setTestInstanceId(options[0]?.id ?? '');
        }
      })
      .catch(() => {
        if (!cancelled) setTestInstanceOptions([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingTestData(false);
      });
    return () => {
      cancelled = true;
    };
  }, [testModalOpen, testModelId, testObjectType]);

  const runTestWithInstance = useCallback(async () => {
    if (!selectedFn?.name || !testObjectType || !testInstanceId) return;
    setTestRunning(true);
    setTestResult(null);
    try {
      await modelApi.switchModel(testModelId);
      const value = await reasoningApi.testFunctionWithInstance(
        selectedFn.name,
        testObjectType,
        testInstanceId
      );
      setTestResult({ ok: true, value });
    } catch (e) {
      setTestResult({
        ok: false,
        error: e instanceof Error ? e.message : String(e),
      });
    } finally {
      setTestRunning(false);
    }
  }, [selectedFn?.name, testModelId, testObjectType, testInstanceId]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-gray-500">加载文件列表...</div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
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
          onClick={() =>
            selectedFilename &&
            ontologyBuilderApi.loadFile(selectedFilename).then(setSchema).catch(() => {})
          }
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

      {loadingSchema && <div className="text-center py-8 text-gray-500">正在加载 Schema...</div>}

      {!loadingSchema && !schema && selectedFilename && (
        <div className="bg-white rounded-lg border border-gray-200 p-8 text-center text-gray-500">
          未能加载该文件或文件为空。
        </div>
      )}

      {!loadingSchema && schema && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <h2 className="text-lg font-semibold text-gray-900 mb-3 flex items-center justify-between">
              <span className="flex items-center">
                <CpuChipIcon className="w-5 h-5 mr-2" />
                函数 ({functions.length})
              </span>
              <button
                type="button"
                onClick={addFunction}
                className="px-2 py-1.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 flex items-center gap-1"
              >
                <PlusIcon className="w-4 h-4" />
                添加
              </button>
            </h2>
            <div className="relative mb-3">
              <MagnifyingGlassIcon className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="搜索函数..."
                className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <div className="space-y-1 max-h-[calc(100vh-380px)] overflow-y-auto">
              {functions.length === 0 ? (
                <p className="text-sm text-gray-500 py-4">暂无函数，点击「添加」创建。函数供规则前件调用，可与 link/衍生属性绑定参数。</p>
              ) : (
                (() => {
                  const colors = ['bg-blue-500', 'bg-yellow-500', 'bg-red-500', 'bg-green-500', 'bg-purple-500', 'bg-indigo-500', 'bg-pink-500', 'bg-teal-500'];
                  const functionNames = functions.map((f) => f.name);
                  const filteredFunctions = functions.filter((fn) => {
                    if (!searchQuery.trim()) return true;
                    const q = searchQuery.toLowerCase();
                    return (
                      (fn.name ?? '').toLowerCase().includes(q) ||
                      (fn.display_name ?? '').toLowerCase().includes(q) ||
                      (fn.description ?? '').toLowerCase().includes(q)
                    );
                  });
                  const functionsByScope: Array<{ name: string; displayName: string; color: string; functions: FunctionPayload[] }> = objectTypes
                    .map((ot: Record<string, any>, idx: number) => {
                      const scope = ot.name ?? '';
                      const scopeRules = rules.filter((r: Record<string, any>) => getScope(r.expr ?? r.swrl) === scope);
                      const namesInScope = new Set<string>();
                      scopeRules.forEach((r: Record<string, any>) => {
                        getFunctionNamesInRule(r.expr ?? r.swrl, functionNames).forEach((name) => namesInScope.add(name));
                      });
                      const groupFns = filteredFunctions.filter((fn) => namesInScope.has(fn.name));
                      return {
                        name: scope,
                        displayName: (ot.display_name ?? ot.name ?? scope) as string,
                        color: colors[idx % colors.length],
                        functions: groupFns,
                      };
                    })
                    .filter((g) => g.functions.length > 0);
                  const usedNames = new Set(functionsByScope.flatMap((g) => g.functions.map((f) => f.name)));
                  const ungrouped = filteredFunctions.filter((fn) => !usedNames.has(fn.name));
                  if (ungrouped.length > 0) {
                    functionsByScope.push({
                      name: 'Unknown',
                      displayName: '未分类',
                      color: 'bg-gray-400',
                      functions: ungrouped,
                    });
                  }
                  if (functionsByScope.length === 0 && searchQuery.trim()) {
                    return <p className="text-sm text-gray-500 py-4 text-center">未找到匹配的函数</p>;
                  }
                  return functionsByScope.map((group) => (
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
                        <span className="text-xs text-gray-400 ml-auto shrink-0">({group.functions.length})</span>
                      </button>
                      {!collapsedGroups.has(group.name) && (
                        <div className="divide-y divide-gray-50">
                          {group.functions.map((fn) => {
                            const idx = functions.indexOf(fn);
                            return (
                              <button
                                key={fn.name}
                                type="button"
                                onClick={() => setSelectedIndex(idx)}
                                className={`w-full text-left px-3 py-2 transition-colors ${
                                  selectedIndex === idx ? 'bg-blue-50 text-blue-700' : 'hover:bg-gray-50 text-gray-700'
                                }`}
                              >
                                <div className="font-medium text-sm truncate">{fn.display_name || fn.name || '(未命名)'}</div>
                                <div className="text-xs text-gray-500 mt-0.5">
                                  {getImplementation(fn)} · {getParams(fn).length} 个入参
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

          <div className="lg:col-span-2">
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">
                {selectedFn ? '编辑函数' : '函数与衍生属性、规则的关系'}
              </h2>
              {selectedFn ? (
                <div className="space-y-4">
                  <div className="flex items-center justify-between flex-wrap gap-2">
                    <h3 className="text-xl font-bold text-gray-900">{selectedFn.display_name || selectedFn.name}</h3>
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={openTestModal}
                        className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md bg-amber-100 text-amber-800 text-sm hover:bg-amber-200"
                      >
                        <PlayIcon className="w-4 h-4" />
                        测试函数
                      </button>
                      <button
                        type="button"
                        onClick={() => selectedIndex !== null && deleteFunction(selectedIndex)}
                        className="text-red-500 hover:text-red-700"
                      >
                        <TrashIcon className="w-5 h-5" />
                      </button>
                    </div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">名称 *</label>
                    <input
                      type="text"
                      value={selectedFn.name}
                      onChange={(e) => selectedIndex !== null && updateFunction(selectedIndex, { name: e.target.value })}
                      className="w-full border rounded px-3 py-2 text-sm"
                      placeholder="如 check_route_consistency"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">显示名称</label>
                    <input
                      type="text"
                      value={selectedFn.display_name ?? ''}
                      onChange={(e) =>
                        selectedIndex !== null && updateFunction(selectedIndex, { display_name: e.target.value })
                      }
                      className="w-full border rounded px-3 py-2 text-sm"
                      placeholder="如：拆分路径与门架路径是否一致"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">描述</label>
                    <textarea
                      value={selectedFn.description ?? ''}
                      onChange={(e) =>
                        selectedIndex !== null && updateFunction(selectedIndex, { description: e.target.value })
                      }
                      className="w-full border rounded px-3 py-2 text-sm"
                      rows={2}
                      placeholder="规则前件中调用此函数时的语义说明"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">实现类型</label>
                    <select
                      value={getImplementation(selectedFn)}
                      onChange={(e) => {
                        if (selectedIndex === null) return;
                        const v = e.target.value;
                        updateFunction(selectedIndex, {
                          implementation: v as 'builtin' | 'external' | 'script',
                          implementation_type: v,
                        });
                      }}
                      className="w-full border rounded px-3 py-2 text-sm"
                    >
                      <option value="builtin">builtin（引擎内置）</option>
                      <option value="script">script（脚本，从 functions/script/ 加载）</option>
                      <option value="external">external（外部服务）</option>
                    </select>
                  </div>
                  {getImplementation(selectedFn) === 'script' && (
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">脚本路径 *</label>
                      <input
                        type="text"
                        value={selectedFn.script_path ?? ''}
                        onChange={(e) =>
                          selectedIndex !== null && updateFunction(selectedIndex, { script_path: e.target.value })
                        }
                        className="w-full border rounded px-3 py-2 text-sm font-mono"
                        placeholder="如 toll/sample_check.js"
                      />
                      <p className="text-xs text-gray-500 mt-1">相对于当前本体所在目录下的 functions/script/（如 ontology/functions/script/）</p>
                    </div>
                  )}
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">出参（返回类型）</label>
                    <input
                      type="text"
                      value={getReturnType(selectedFn)}
                      onChange={(e) => {
                        if (selectedIndex === null) return;
                        const v = e.target.value;
                        updateFunction(selectedIndex, {
                          return_type: v,
                          output_type: v,
                          output: { type: v },
                        });
                      }}
                      className="w-full border rounded px-3 py-2 text-sm"
                      placeholder="boolean | number | string | date | list&lt;T&gt;"
                    />
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <label className="block text-sm font-medium text-gray-700">入参（参数名与类型，与定义文件一致）</label>
                      <button
                        type="button"
                        onClick={() => selectedIndex !== null && addInput(selectedIndex)}
                        className="text-xs text-blue-600 hover:text-blue-800"
                      >
                        + 添加参数
                      </button>
                    </div>
                    <div className="space-y-3">
                      {getParams(selectedFn).map((inp, i) => (
                        <div
                          key={i}
                          className="border border-gray-200 rounded-lg p-3 bg-gray-50/50 space-y-2"
                        >
                          <div className="flex gap-2 items-center">
                            <input
                              type="text"
                              value={inp.name}
                              onChange={(e) =>
                                selectedIndex !== null && updateInput(selectedIndex, i, { name: e.target.value })
                              }
                              className="flex-1 border rounded px-2 py-1.5 text-sm"
                              placeholder="参数名"
                            />
                            <select
                              value={inp.type}
                              onChange={(e) =>
                                selectedIndex !== null && updateInput(selectedIndex, i, { type: e.target.value })
                              }
                              className="flex-1 border rounded px-2 py-1.5 text-sm bg-white"
                            >
                              <option value="">选择类型</option>
                              <optgroup label="基础类型">
                                <option value="string">string</option>
                                <option value="int">int</option>
                                <option value="long">long</option>
                                <option value="float">float</option>
                                <option value="double">double</option>
                                <option value="number">number</option>
                                <option value="boolean">boolean</option>
                                <option value="date">date</option>
                                <option value="datetime">datetime</option>
                                <option value="json">json</option>
                              </optgroup>
                              <optgroup label="本体对象类型">
                                {objectTypes.map((ot: Record<string, any>) => (
                                  <option key={ot.name} value={ot.name}>
                                    {ot.display_name || ot.name}
                                  </option>
                                ))}
                              </optgroup>
                              <optgroup label="本体对象列表类型">
                                {objectTypes.map((ot: Record<string, any>) => (
                                  <option key={`list<${ot.name}>`} value={`list<${ot.name}>`}>
                                    list&lt;{ot.display_name || ot.name}&gt;
                                  </option>
                                ))}
                              </optgroup>
                            </select>
                            <button
                              type="button"
                              onClick={() => selectedIndex !== null && removeInput(selectedIndex, i)}
                              className="text-red-500 hover:text-red-700"
                            >
                              <TrashIcon className="w-4 h-4" />
                            </button>
                          </div>
                          <div className="text-xs text-gray-600 flex items-center gap-2">
                            <span>数据来源（可选，供规则引擎解析）：</span>
                          </div>
                          <div className="flex flex-wrap gap-2 items-center">
                            <select
                              value={getBinding(selectedFn, inp.name)?.source_type ?? ''}
                              onChange={(e) => {
                                const v = e.target.value;
                                if (!v || selectedIndex === null) return;
                                if (v === 'link')
                                  setBinding(selectedIndex, inp.name, {
                                    parameter_name: inp.name,
                                    source_type: 'link',
                                    link_name: linkTypes[0]?.name ?? '',
                                  });
                                else if (v === 'derived_attribute')
                                  setBinding(selectedIndex, inp.name, {
                                    parameter_name: inp.name,
                                    source_type: 'derived_attribute',
                                    object_type: objectTypes[0]?.name ?? '',
                                    attribute_name: '',
                                  });
                                else setBinding(selectedIndex, inp.name, null);
                              }}
                              className="border rounded px-2 py-1 text-sm"
                            >
                              <option value="">不绑定</option>
                              <option value="link">绑定到关系 (link)</option>
                              <option value="derived_attribute">绑定到衍生属性</option>
                            </select>
                            {getBinding(selectedFn, inp.name)?.source_type === 'link' && (
                              <select
                                value={getBinding(selectedFn, inp.name)?.link_name ?? ''}
                                onChange={(e) =>
                                  selectedIndex !== null &&
                                  setBinding(selectedIndex, inp.name, {
                                    ...getBinding(selectedFn, inp.name)!,
                                    link_name: e.target.value,
                                  })
                                }
                                className="border rounded px-2 py-1 text-sm"
                              >
                                {linkTypes.map((lt: Record<string, any>) => (
                                  <option key={lt.name} value={lt.name ?? ''}>
                                    {lt.display_name || lt.name} ({lt.name})
                                  </option>
                                ))}
                              </select>
                            )}
                            {getBinding(selectedFn, inp.name)?.source_type === 'derived_attribute' && (
                              <>
                                <select
                                  value={getBinding(selectedFn, inp.name)?.object_type ?? ''}
                                  onChange={(e) =>
                                    selectedIndex !== null &&
                                    setBinding(selectedIndex, inp.name, {
                                      ...getBinding(selectedFn, inp.name)!,
                                      object_type: e.target.value,
                                      attribute_name: '',
                                    })
                                  }
                                  className="border rounded px-2 py-1 text-sm"
                                >
                                  {objectTypes.map((ot: Record<string, any>) => (
                                    <option key={ot.name} value={ot.name ?? ''}>
                                      {ot.display_name || ot.name}
                                    </option>
                                  ))}
                                </select>
                                <select
                                  value={getBinding(selectedFn, inp.name)?.attribute_name ?? ''}
                                  onChange={(e) =>
                                    selectedIndex !== null &&
                                    setBinding(selectedIndex, inp.name, {
                                      ...getBinding(selectedFn, inp.name)!,
                                      attribute_name: e.target.value,
                                    })
                                  }
                                  className="border rounded px-2 py-1 text-sm"
                                >
                                  <option value="">-- 选择衍生属性 --</option>
                                  {getDerivedProperties(
                                    objectTypes.find(
                                      (ot: Record<string, any>) =>
                                        ot.name === getBinding(selectedFn, inp.name)?.object_type
                                    )
                                  ).map((p: Record<string, any>) => (
                                    <option key={p.name} value={p.name}>
                                      {p.display_name || p.name} ({p.name})
                                    </option>
                                  ))}
                                </select>
                              </>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              ) : (
                <div className="text-center py-8 text-gray-500">
                  <InformationCircleIcon className="w-12 h-12 mx-auto mb-2 text-gray-400" />
                  <p>从左侧选择函数进行编辑，或点击「添加」创建。</p>
                  <p className="text-sm mt-2 text-left max-w-xl mx-auto">
                    <strong>与衍生属性、规则的关系：</strong> 函数在规则前件中被调用（如
                    <code className="bg-gray-100 px-1 rounded">check_route_consistency(links(?p, ...), ...)</code>
                    ）。此处可为每个入参配置默认数据来源：<LinkIcon className="w-4 h-4 inline mx-0.5" />
                    <strong>关系 (link)</strong> 或 <CubeIcon className="w-4 h-4 inline mx-0.5" />
                    <strong>衍生属性</strong>，规则编辑时可据此自动带出参数，与本体、衍生属性保持一致。
                  </p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 函数测试弹窗 */}
      {testModalOpen && selectedFn && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40" onClick={() => setTestModalOpen(false)}>
          <div
            className="bg-white rounded-lg shadow-xl max-w-lg w-full p-5 space-y-4 max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">测试函数：{selectedFn.name}</h3>
              <button type="button" onClick={() => setTestModalOpen(false)} className="text-gray-500 hover:text-gray-700">
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>

            {/* 按实例运行（规则上下文）：选择本体模型与根实例，用该模型绑定的数据存储查询后调用函数 */}
            <div className="rounded-lg border border-gray-200 bg-gray-50 p-3 space-y-3">
              <p className="text-sm font-medium text-gray-700">按实例运行（规则上下文）</p>
              <p className="text-xs text-gray-500">
                选择关联规则绑定的本体模型与根对象实例，系统将按该模型的数据存储查询实例及关联数据，并作为函数入参执行。
              </p>
              <div className="grid grid-cols-1 gap-2">
                <div>
                  <label className="block text-xs text-gray-500 mb-0.5">本体模型</label>
                  <select
                    value={testModelId}
                    onChange={(e) => setTestModelId(e.target.value)}
                    className="w-full border rounded px-2 py-1.5 text-sm"
                  >
                    <option value="">请选择</option>
                    {testModelList.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.displayName || m.path || m.id}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-0.5">根对象类型</label>
                  <select
                    value={testObjectType}
                    onChange={(e) => setTestObjectType(e.target.value)}
                    className="w-full border rounded px-2 py-1.5 text-sm"
                  >
                    <option value="">请选择</option>
                    {testObjectTypes.map((ot) => (
                      <option key={ot.name} value={ot.name}>
                        {ot.display_name || ot.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-0.5">实例</label>
                  <select
                    value={testInstanceId}
                    onChange={(e) => setTestInstanceId(e.target.value)}
                    disabled={loadingTestData || !testObjectType}
                    className="w-full border rounded px-2 py-1.5 text-sm"
                  >
                    <option value="">请选择</option>
                    {testInstanceOptions.map((opt) => (
                      <option key={opt.id} value={opt.id}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                  {loadingTestData && <span className="text-xs text-gray-400">加载中…</span>}
                </div>
              </div>
              <button
                type="button"
                onClick={runTestWithInstance}
                disabled={testRunning || !testModelId || !testObjectType || !testInstanceId}
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md bg-indigo-600 text-white text-sm hover:bg-indigo-700 disabled:opacity-50"
              >
                <PlayIcon className="w-4 h-4" />
                {testRunning ? '执行中...' : '用该实例数据运行'}
              </button>
            </div>

            <p className="text-xs text-gray-500">
              或手动填写入参（JSON 数组，顺序与函数定义一致）。例如实例对象 <code className="bg-gray-100 px-1 rounded">&#123;&#125;</code>、关联列表 <code className="bg-gray-100 px-1 rounded">[&#123;&#125;]</code>。
            </p>
            <textarea
              value={testArgsJson}
              onChange={(e) => setTestArgsJson(e.target.value)}
              className="w-full border rounded px-3 py-2 text-sm font-mono h-32"
              placeholder='[{"pass_id": "xxx"}, [{"fee": 10}]]'
              spellCheck={false}
            />
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={runFunctionTest}
                disabled={testRunning}
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:opacity-50"
              >
                <PlayIcon className="w-4 h-4" />
                {testRunning ? '执行中...' : '执行'}
              </button>
              <button
                type="button"
                onClick={() => setTestModalOpen(false)}
                className="px-3 py-2 rounded-md border border-gray-300 text-sm hover:bg-gray-50"
              >
                关闭
              </button>
            </div>
            {testResult && (
              <div
                className={`p-3 rounded text-sm font-mono break-all ${
                  testResult.ok ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'
                }`}
              >
                {testResult.ok ? (
                  <pre className="whitespace-pre-wrap">{JSON.stringify(testResult.value, null, 2)}</pre>
                ) : (
                  <span>{testResult.error}</span>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
