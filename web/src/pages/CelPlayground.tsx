import { useState, useCallback, useEffect } from 'react';
import type { OntologyBuilderPayload } from '../api/client';
import { ontologyBuilderApi, modelApi, reasoningApi, instanceApi } from '../api/client';
import type { ModelInfo, ObjectType, Instance } from '../api/client';
import { CelExprBuilder } from '../components/ontology/CelExprBuilder';
import type { EntityOption, RelationOption } from '../components/ontology/CelExprBuilder';
import {
  DocumentTextIcon,
  FolderOpenIcon,
  ArrowDownTrayIcon,
  DocumentArrowUpIcon,
  InformationCircleIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  MagnifyingGlassIcon,
  CheckCircleIcon,
  ExclamationCircleIcon,
  PlayIcon,
} from '@heroicons/react/24/outline';

interface DerivedAttrRef {
  objectTypeName: string;
  propertyName: string;
}

export default function CelPlayground() {
  const [fileList, setFileList] = useState<string[]>([]);
  const [selectedFilename, setSelectedFilename] = useState<string>('');
  const [schema, setSchema] = useState<OntologyBuilderPayload | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingSchema, setLoadingSchema] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());

  const [selectedAttr, setSelectedAttr] = useState<DerivedAttrRef | null>(null);
  const [validateStatus, setValidateStatus] = useState<{ expr: string; valid: boolean; message: string } | null>(null);
  const [validating, setValidating] = useState(false);

  const [sampleContext, setSampleContext] = useState<string>(
    `{\n  "properties": {},\n  "linked_data": {}\n}`
  );
  const [evaluateResult, setEvaluateResult] = useState<{ ok: boolean; value?: unknown; error?: string } | null>(null);
  const [evaluating, setEvaluating] = useState(false);

  // 按实例求值：本体模型、根对象类型、实例
  const [testModelList, setTestModelList] = useState<ModelInfo[]>([]);
  const [testModelId, setTestModelId] = useState<string>('');
  const [testObjectTypes, setTestObjectTypes] = useState<ObjectType[]>([]);
  const [testObjectType, setTestObjectType] = useState<string>('');
  const [testInstanceOptions, setTestInstanceOptions] = useState<Array<{ id: string; label: string; raw: Instance }>>([]);
  const [testInstanceId, setTestInstanceId] = useState<string>('');
  const [loadingTestData, setLoadingTestData] = useState(false);

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
      const [files, currentModel, models] = await Promise.all([
        ontologyBuilderApi.listFiles(),
        modelApi.getCurrentModel().catch(() => null),
        modelApi.listModels().catch(() => []),
      ]);
      setFileList(files);
      setTestModelList(models);
      if (files.length > 0) {
        const pathToUse = currentModel?.filePath;
        const getBasename = (filePath: string) => {
          const normalized = filePath.replace(/\\/g, '/');
          const last = normalized.lastIndexOf('/');
          return last >= 0 ? normalized.slice(last + 1) : filePath;
        };
        const match = pathToUse
          ? files.find((f) => f === getBasename(pathToUse) || pathToUse.endsWith(f) || pathToUse === f)
          : undefined;
        setSelectedFilename((prev) => (prev ? prev : match ?? files[0]));
        if (currentModel?.modelId) {
          setTestModelId(currentModel.modelId);
        } else if (models.length > 0) {
          setTestModelId(models[0].id);
        }
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
      setSelectedAttr(null);
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
          setSelectedAttr(null);
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

  const entitiesForEditor: EntityOption[] = objectTypes.map((ot: any) => ({
    name: ot.name ?? '',
    display_name: ot.display_name,
    attributes: (ot.properties ?? []).map((p: any) => ({
      name: p.name ?? '',
      display_name: p.display_name,
    })),
  }));

  const relationsForEditor: RelationOption[] = linkTypes.map((lt: any) => ({
    name: lt.name ?? '',
    display_name: lt.display_name,
    source_type: lt.source_type ?? '',
    target_type: lt.target_type ?? '',
  }));

  const derivedGroups = objectTypes
    .map((ot: any) => {
      const derivedProps = (ot.properties ?? []).filter((p: any) => p.derived === true);
      return {
        objectTypeName: ot.name as string,
        displayName: (ot.display_name ?? ot.name ?? '') as string,
        derivedProps,
      };
    })
    .filter((g) => g.derivedProps.length > 0);

  const filteredGroups = derivedGroups
    .map((g) => {
      const props = g.derivedProps.filter((p: any) => {
        if (!searchQuery.trim()) return true;
        const q = searchQuery.toLowerCase();
        return (
          (p.name ?? '').toLowerCase().includes(q) ||
          (p.display_name ?? '').toLowerCase().includes(q) ||
          (p.description ?? '').toLowerCase().includes(q)
        );
      });
      return { ...g, derivedProps: props };
    })
    .filter((g) => g.derivedProps.length > 0);

  const findProperty = (ref: DerivedAttrRef | null): { ot: any; prop: any } | null => {
    if (!ref || !schema) return null;
    const ot = objectTypes.find((t: any) => t.name === ref.objectTypeName);
    if (!ot || !ot.properties) return null;
    const prop = ot.properties.find((p: any) => p.name === ref.propertyName);
    if (!prop) return null;
    return { ot, prop };
  };

  const updatePropertyExpr = (ref: DerivedAttrRef, expr: string) => {
    if (!schema) return;
    const next = { ...schema };
    const ots = [...(next.object_types ?? [])];
    const otIndex = ots.findIndex((t: any) => t.name === ref.objectTypeName);
    if (otIndex === -1) return;
    const ot = { ...ots[otIndex] };
    const props = [...(ot.properties ?? [])];
    const propIndex = props.findIndex((p: any) => p.name === ref.propertyName);
    if (propIndex === -1) return;
    const prop = { ...props[propIndex], expr };
    props[propIndex] = prop;
    ot.properties = props;
    ots[otIndex] = ot;
    next.object_types = ots as any;
    setSchema(next);
  };

  const handleValidateExpr = useCallback(
    async (expr: string) => {
      setValidating(true);
      setValidateStatus(null);
      try {
        const result = await reasoningApi.validateCel(expr ?? '');
        setValidateStatus({ expr: expr ?? '', valid: result.valid, message: result.message ?? '' });
      } catch (e) {
        setValidateStatus({
          expr: expr ?? '',
          valid: false,
          message: e instanceof Error ? e.message : '校验请求失败',
        });
      } finally {
        setValidating(false);
      }
    },
    []
  );

  const handleEvaluateSample = useCallback(
    async (expr: string) => {
      setEvaluating(true);
      setEvaluateResult(null);
      try {
        let properties: Record<string, unknown> = {};
        let linkedData: Record<string, unknown[]> = {};
        try {
          const parsed = JSON.parse(sampleContext) as Record<string, unknown>;
          if (parsed.properties && typeof parsed.properties === 'object' && !Array.isArray(parsed.properties)) {
            properties = parsed.properties as Record<string, unknown>;
          }
          if (parsed.linked_data && typeof parsed.linked_data === 'object' && !Array.isArray(parsed.linked_data)) {
            const ld = parsed.linked_data as Record<string, unknown>;
            for (const [k, v] of Object.entries(ld)) {
              if (Array.isArray(v)) linkedData[k] = v;
            }
          }
        } catch {
          setEvaluateResult({ ok: false, error: '样本上下文 JSON 格式错误' });
          setEvaluating(false);
          return;
        }
        const value = await reasoningApi.evaluateCel(expr.trim(), properties, linkedData);
        setEvaluateResult({ ok: true, value });
      } catch (e) {
        setEvaluateResult({
          ok: false,
          error: e instanceof Error ? e.message : String(e),
        });
      } finally {
        setEvaluating(false);
      }
    },
    [sampleContext]
  );

  const selected = findProperty(selectedAttr);

  // 切换测试模型时刷新对象类型和实例列表
  useEffect(() => {
    if (!testModelId) {
      setTestObjectTypes([]);
      setTestObjectType('');
      setTestInstanceOptions([]);
      setTestInstanceId('');
      return;
    }
    let cancelled = false;
    modelApi
      .getObjectTypes(testModelId)
      .then((ots) => {
        if (!cancelled) {
          setTestObjectTypes(ots);
          setTestObjectType('');
          setTestInstanceOptions([]);
          setTestInstanceId('');
        }
      })
      .catch(() => {
        if (!cancelled) setTestObjectTypes([]);
      });
    return () => {
      cancelled = true;
    };
  }, [testModelId]);

  // 切换根对象类型时加载实例列表（当前模型下的数据）
  useEffect(() => {
    if (!testModelId || !testObjectType) {
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
          const options = items.map((item) => {
            const id = (item.id ?? (item as any).pass_id ?? Object.values(item)[0]) as string;
            const label = typeof id === 'string' ? id : JSON.stringify(id);
            return { id: String(id), label, raw: item };
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
  }, [testModelId, testObjectType]);

  const handleEvaluateWithInstance = useCallback(async () => {
    if (!selected || !selectedAttr || !testModelId || !testObjectType || !testInstanceId) return;
    setEvaluating(true);
    setEvaluateResult(null);
    try {
      // 确保后端当前模型与选择一致
      await modelApi.switchModel(testModelId);
      const value = await reasoningApi.evaluateCelWithInstance(
        selected.prop.expr ?? '',
        testObjectType,
        testInstanceId
      );
      setEvaluateResult({ ok: true, value });
    } catch (e) {
      setEvaluateResult({
        ok: false,
        error: e instanceof Error ? e.message : String(e),
      });
    } finally {
      setEvaluating(false);
    }
  }, [selected, selectedAttr, testModelId, testObjectType, testInstanceId]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-gray-500">加载本体文件列表...</div>
      </div>
    );
  }

  return (
    <div className="space-y-4 p-6">
      <div className="flex items-center gap-2 text-gray-900">
        <DocumentTextIcon className="w-7 h-7 text-blue-600" />
        <h1 className="text-xl font-semibold">CEL 衍生属性统一管理</h1>
        <span className="text-xs text-gray-500 flex items-center gap-1">
          <InformationCircleIcon className="w-4 h-4" />
          按本体对象类型分组查看和编辑衍生属性的 CEL 表达式
        </span>
      </div>

      {/* 文件选择与保存，与 RulesView/OntologyBuilder 一致 */}
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
            ontologyBuilderApi
              .loadFile(selectedFilename)
              .then(setSchema)
              .catch(() => {})
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
          {/* 左侧：按对象类型分组的衍生属性列表 */}
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <h2 className="text-lg font-semibold text-gray-900 mb-3 flex items-center justify-between">
              <span className="flex items-center">
                <span className="w-2 h-2 rounded-full bg-blue-500 mr-2" />
                衍生属性
              </span>
            </h2>
            <div className="relative mb-3">
              <MagnifyingGlassIcon className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="搜索属性名称、显示名称或描述..."
                className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <div className="space-y-1 max-h-[calc(100vh-380px)] overflow-y-auto">
              {filteredGroups.length === 0 ? (
                <p className="text-sm text-gray-500 py-4 text-center">当前本体中暂无衍生属性，或无匹配结果。</p>
              ) : (
                filteredGroups.map((group, idx) => (
                  <div key={group.objectTypeName} className="border border-gray-100 rounded-lg overflow-hidden">
                    <button
                      type="button"
                      onClick={() => toggleGroupCollapse(group.objectTypeName)}
                      className="w-full flex items-center gap-2 px-3 py-2 bg-gray-50 hover:bg-gray-100 transition-colors text-left"
                    >
                      {collapsedGroups.has(group.objectTypeName) ? (
                        <ChevronRightIcon className="w-4 h-4 text-gray-500 shrink-0" />
                      ) : (
                        <ChevronDownIcon className="w-4 h-4 text-gray-500 shrink-0" />
                      )}
                      <span
                        className={`w-2 h-2 rounded-full shrink-0 ${
                          ['bg-blue-500', 'bg-yellow-500', 'bg-red-500', 'bg-green-500', 'bg-purple-500', 'bg-indigo-500'][
                            idx % 6
                          ]
                        }`}
                      />
                      <span className="text-sm font-medium text-gray-700 truncate">{group.displayName}</span>
                      <span className="text-xs text-gray-400 ml-auto shrink-0">({group.derivedProps.length})</span>
                    </button>
                    {!collapsedGroups.has(group.objectTypeName) && (
                      <div className="divide-y divide-gray-50">
                        {group.derivedProps.map((p: any) => {
                          const isSelected =
                            selectedAttr?.objectTypeName === group.objectTypeName &&
                            selectedAttr?.propertyName === p.name;
                          return (
                            <button
                              key={p.name}
                              type="button"
                              onClick={() =>
                                setSelectedAttr({ objectTypeName: group.objectTypeName, propertyName: p.name })
                              }
                              className={`w-full text-left px-3 py-2 transition-colors ${
                                isSelected ? 'bg-blue-50 text-blue-700' : 'hover:bg-gray-50 text-gray-700'
                              }`}
                            >
                              <div className="font-medium text-sm truncate">
                                {p.display_name || p.name || '(未命名)'}
                              </div>
                              <div className="text-xs text-gray-500 mt-0.5 truncate">
                                {group.objectTypeName} · {p.type}
                              </div>
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>

          {/* 右侧：选中衍生属性的 CEL 表达式编辑与测试 */}
          <div className="lg:col-span-2 space-y-4">
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">
                {selected ? '编辑衍生属性 CEL 表达式' : '选择左侧的衍生属性进行编辑'}
              </h2>
              {selected ? (
                <div className="space-y-4">
                  <div className="flex items-center justify-between flex-wrap gap-2">
                    <div>
                      <div className="text-base font-semibold text-gray-900">
                        {selected.prop.display_name || selected.prop.name || '未命名'}
                        <span className="text-gray-500 font-normal ml-1">
                          ({selected.prop.name}) · {selected.prop.type}
                        </span>
                      </div>
                      <p className="text-xs text-gray-500 mt-1">
                        所属对象类型：<span className="font-mono">{selected.ot.name}</span>
                      </p>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <label className="block text-sm font-medium text-gray-700">CEL 表达式</label>
                      <button
                        type="button"
                        onClick={() => handleValidateExpr(selected.prop.expr ?? '')}
                        disabled={validating || !(selected.prop.expr ?? '').trim()}
                        className="text-xs px-2 py-1 rounded border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-50"
                      >
                        {validating ? '验证中...' : '验证表达式'}
                      </button>
                    </div>
                    <CelExprBuilder
                      expr={selected.prop.expr ?? ''}
                      onChange={(expr) =>
                        selectedAttr && updatePropertyExpr(selectedAttr, expr)
                      }
                      relations={relationsForEditor.filter(
                        (r) => r.source_type === selected.ot.name || !r.source_type
                      )}
                      entities={entitiesForEditor}
                    />
                    {validateStatus && validateStatus.expr === (selected.prop.expr ?? '') && (
                      <div
                        className={`mt-2 flex items-center gap-1.5 text-xs ${
                          validateStatus.valid ? 'text-green-700' : 'text-red-700'
                        }`}
                      >
                        {validateStatus.valid ? (
                          <CheckCircleIcon className="w-4 h-4 shrink-0" />
                        ) : (
                          <ExclamationCircleIcon className="w-4 h-4 shrink-0" />
                        )}
                        <span>{validateStatus.message}</span>
                      </div>
                    )}
                  </div>

                  <div className="border-t border-dashed border-gray-200 pt-4 mt-2 space-y-2">
                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                      <div className="space-y-2">
                        <div className="flex items-center justify-between">
                          <label className="block text-sm font-medium text-gray-700">样本上下文（可选）JSON</label>
                          <button
                            type="button"
                            onClick={() =>
                              handleEvaluateSample(selected.prop.expr ?? '')
                            }
                            disabled={evaluating || !(selected.prop.expr ?? '').trim()}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-blue-600 text-white text-xs hover:bg-blue-700 disabled:opacity-50"
                          >
                            <PlayIcon className="w-3.5 h-3.5" />
                            {evaluating ? '求值中...' : '使用样本求值'}
                          </button>
                        </div>
                        <p className="text-xs text-gray-500">
                          properties: 当前实例属性；linked_data: 关系名 → 关联对象列表。此处仅用于本页测试，不会保存到本体。
                        </p>
                        <textarea
                          value={sampleContext}
                          onChange={(e) => setSampleContext(e.target.value)}
                          className="w-full border border-gray-300 rounded-lg px-3 py-2 text-xs font-mono h-32"
                          spellCheck={false}
                        />
                      </div>

                      <div className="space-y-2">
                        <div className="flex items-center justify-between">
                          <label className="block text-sm font-medium text-gray-700">基于实例数据求值</label>
                        </div>
                        <p className="text-xs text-gray-500">
                          选择本体模型、根对象类型与实例，系统将按该模型的数据源构建上下文（实例 + 关联数据 + 衍生属性）进行求值。
                        </p>
                        <div className="space-y-2">
                          <select
                            value={testModelId}
                            onChange={(e) => setTestModelId(e.target.value)}
                            className="w-full border rounded px-2 py-1.5 text-xs"
                          >
                            <option value="">选择本体模型</option>
                            {testModelList.map((m) => (
                              <option key={m.id} value={m.id}>
                                {m.displayName || m.path || m.id}
                              </option>
                            ))}
                          </select>
                          <select
                            value={testObjectType}
                            onChange={(e) => setTestObjectType(e.target.value)}
                            className="w-full border rounded px-2 py-1.5 text-xs"
                          >
                            <option value="">选择根对象类型</option>
                            {testObjectTypes.map((ot) => (
                              <option key={ot.name} value={ot.name}>
                                {ot.display_name || ot.name}
                              </option>
                            ))}
                          </select>
                          <select
                            value={testInstanceId}
                            onChange={(e) => setTestInstanceId(e.target.value)}
                            disabled={loadingTestData || !testObjectType}
                            className="w-full border rounded px-2 py-1.5 text-xs"
                          >
                            <option value="">选择实例</option>
                            {testInstanceOptions.map((opt) => (
                              <option key={opt.id} value={opt.id}>
                                {opt.label}
                              </option>
                            ))}
                          </select>
                          {loadingTestData && (
                            <span className="text-[11px] text-gray-400">实例加载中...</span>
                          )}
                          <button
                            type="button"
                            onClick={handleEvaluateWithInstance}
                            disabled={
                              evaluating ||
                              !(selected.prop.expr ?? '').trim() ||
                              !testModelId ||
                              !testObjectType ||
                              !testInstanceId
                            }
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-indigo-600 text-white text-xs hover:bg-indigo-700 disabled:opacity-50"
                          >
                            <PlayIcon className="w-3.5 h-3.5" />
                            {evaluating ? '求值中...' : '用实例数据求值'}
                          </button>
                        </div>
                      </div>
                    </div>
                    {evaluateResult && (
                      <div
                        className={`mt-2 p-3 rounded text-xs font-mono break-all ${
                          evaluateResult.ok ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'
                        }`}
                      >
                        {evaluateResult.ok ? (
                          <pre className="whitespace-pre-wrap">
                            {JSON.stringify(evaluateResult.value, null, 2)}
                          </pre>
                        ) : (
                          <span>{evaluateResult.error}</span>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              ) : (
                <div className="text-center py-10 text-gray-500">
                  <p className="mb-2">从左侧选择一个已勾选「衍生」的属性，查看和编辑其 CEL 表达式。</p>
                  <p className="text-xs max-w-md mx-auto">
                    提示：在本体构建器中，可为属性勾选「衍生」并配置数据类型、描述等基础信息；本页面专注于统一管理各对象类型上的 CEL
                    衍生表达式。
                  </p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
