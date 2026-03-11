import { useEffect, useState, useCallback } from 'react';
import type {
  OntologyBuilderPayload,
  FunctionPayload,
  FunctionInputPayload,
  ParameterBindingPayload,
} from '../api/client';
import { ontologyBuilderApi, reasoningApi } from '../api/client';
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

/** 兼容 ontology（parameters/return_type/implementation_type）与既有（inputs/output_type/implementation） */
function getParams(fn: FunctionPayload): FunctionInputPayload[] {
  return fn.parameters ?? fn.inputs ?? [];
}
function getReturnType(fn: FunctionPayload): string {
  return fn.return_type ?? fn.output_type ?? (fn.output && typeof fn.output === 'object' && 'type' in fn.output ? (fn.output as { type?: string }).type : undefined) ?? 'boolean';
}
function getImplementation(fn: FunctionPayload): string {
  return fn.implementation_type ?? fn.implementation ?? 'builtin';
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

  const functions = schema?.functions ?? [];
  const selectedFn = selectedIndex !== null && functions[selectedIndex] ? functions[selectedIndex] : null;
  const objectTypes = schema?.object_types ?? [];
  const linkTypes = schema?.link_types ?? [];

  const loadFileList = useCallback(async () => {
    try {
      const files = await ontologyBuilderApi.listFiles();
      setFileList(files);
      if (files.length > 0 && !selectedFilename) setSelectedFilename(files[0]);
    } catch (e) {
      console.error('Failed to list ontology files:', e);
      setError('无法获取本体文件列表');
    } finally {
      setLoading(false);
    }
  }, [selectedFilename]);

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
    updateFunction(fnIndex, { parameters: next, inputs: next });
  };

  const updateInput = (fnIndex: number, inputIndex: number, updates: Partial<FunctionInputPayload>) => {
    const fn = schema?.functions?.[fnIndex];
    const params = fn ? getParams(fn) : [];
    if (inputIndex < 0 || inputIndex >= params.length) return;
    const next = params.map((inp, i) => (i === inputIndex ? { ...inp, ...updates } : inp));
    updateFunction(fnIndex, { parameters: next, inputs: next });
  };

  const removeInput = (fnIndex: number, inputIndex: number) => {
    const fn = schema?.functions?.[fnIndex];
    const params = fn ? getParams(fn) : [];
    const nameToRemove = params[inputIndex]?.name;
    if (nameToRemove == null) return;
    const next = params.filter((_, i) => i !== inputIndex);
    const bindings = (fn?.parameter_bindings ?? []).filter((b) => b.parameter_name !== nameToRemove);
    updateFunction(fnIndex, { parameters: next, inputs: next, parameter_bindings: bindings });
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

  const openTestModal = useCallback(() => {
    setTestArgsJson('[]');
    setTestResult(null);
    setTestModalOpen(true);
  }, []);

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
            <h2 className="text-lg font-semibold text-gray-900 mb-4 flex items-center justify-between">
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
            <div className="space-y-2">
              {functions.length === 0 ? (
                <p className="text-sm text-gray-500 py-4">暂无函数，点击「添加」创建。函数供规则前件调用，可与 link/衍生属性绑定参数。</p>
              ) : (
                functions.map((fn, idx) => (
                  <button
                    key={`${fn.name}-${idx}`}
                    onClick={() => setSelectedIndex(idx)}
                    className={`w-full text-left px-3 py-2 rounded-lg transition-colors border ${
                      selectedIndex === idx
                        ? 'bg-blue-50 text-blue-700 border-blue-200'
                        : 'hover:bg-gray-50 border-transparent'
                    }`}
                  >
                    <div className="font-medium text-sm truncate">{fn.display_name || fn.name || '(未命名)'}</div>
                    <div className="text-xs text-gray-500 mt-0.5">
                      {getImplementation(fn)} · {getParams(fn).length} 个入参
                    </div>
                  </button>
                ))
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
                    <label className="block text-sm font-medium text-gray-700 mb-1">返回类型</label>
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
                      placeholder="boolean | number | string"
                    />
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <label className="block text-sm font-medium text-gray-700">入参</label>
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
                            <input
                              type="text"
                              value={inp.type}
                              onChange={(e) =>
                                selectedIndex !== null && updateInput(selectedIndex, i, { type: e.target.value })
                              }
                              className="flex-1 border rounded px-2 py-1.5 text-sm"
                              placeholder="类型 如 list&lt;SplitDetail&gt;"
                            />
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
            className="bg-white rounded-lg shadow-xl max-w-lg w-full p-5 space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">测试函数：{selectedFn.name}</h3>
              <button type="button" onClick={() => setTestModalOpen(false)} className="text-gray-500 hover:text-gray-700">
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>
            <p className="text-xs text-gray-500">
              入参为 JSON 数组，顺序与函数定义一致。例如实例对象 <code className="bg-gray-100 px-1 rounded">&#123;&#125;</code>、关联列表 <code className="bg-gray-100 px-1 rounded">[&#123;&#125;]</code>。
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
