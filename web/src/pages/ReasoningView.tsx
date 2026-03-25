import { useState, useEffect, useMemo } from 'react';
import { reasoningApi, instanceApi } from '../api/client';
import type { InferenceResult, ReasoningStatus, Instance, CycleDetail, BatchInstanceResult } from '../api/client';

// 根据触发规则列表生成分组签名（排序后 join，保证顺序无关）
function ruleSignature(firedRules: string[]): string {
  return [...firedRules].sort().join(' | ') || '(无规则触发)';
}

// 判断是否为"正常通行"签名（只有 normal 类规则）
function isNormalSignature(sig: string): boolean {
  if (sig === '(无规则触发)') return false;
  return sig.split(' | ').every(r => r.toLowerCase().includes('normal'));
}

export default function ReasoningView() {
  const [status, setStatus] = useState<ReasoningStatus | null>(null);
  const [rootTypes, setRootTypes] = useState<string[]>([]);
  const [selectedObjectType, setSelectedObjectType] = useState('');
  const [instances, setInstances] = useState<Instance[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState('');
  const [instanceInput, setInstanceInput] = useState('');
  const [inferenceResult, setInferenceResult] = useState<InferenceResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [inferError, setInferError] = useState('');

  // 批量推理状态（暂不使用，setter 仅在 handleBatchAll 中调用）
  // const [batchLoading, setBatchLoading] = useState(false);
  const [batchResults, _setBatchResults] = useState<BatchInstanceResult[] | null>(null);
  const [batchError, _setBatchError] = useState('');
  // 展开/折叠的分组 key
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

  // 按当前本体模型加载：推理根类型 + 状态
  useEffect(() => {
    const load = async () => {
      try {
        const [statusData, types] = await Promise.all([
          reasoningApi.status(),
          reasoningApi.rootTypes(),
        ]);
        setStatus(statusData);
        setRootTypes(types);
        if (types.length > 0 && !selectedObjectType) setSelectedObjectType(types[0]);
      } catch (err: any) {
        console.error('Failed to load reasoning data:', err);
      }
    };
    load();
  }, []);

  // 切换对象类型时加载该类型实例列表
  useEffect(() => {
    if (!selectedObjectType) {
      setInstances([]);
      return;
    }
    instanceApi.list(selectedObjectType, 0, 200)
      .then((res) => setInstances(res.items))
      .catch(() => setInstances([]));
  }, [selectedObjectType]);

  // 批量推理（暂不使用）
  // const handleBatchAll = async () => {
  //   if (!selectedObjectType) return;
  //   setBatchLoading(true);
  //   setBatchResults(null);
  //   setBatchError('');
  //   setExpandedGroups(new Set());
  //   try {
  //     const results = await reasoningApi.batchAllSync(selectedObjectType);
  //     setBatchResults(results);
  //     if (results.length > 0) {
  //       const firstSig = ruleSignature(results[0].firedRules);
  //       setExpandedGroups(new Set([firstSig]));
  //     }
  //   } catch (err: any) {
  //     setBatchError(err.response?.data?.message || err.message || 'Batch inference failed');
  //   } finally {
  //     setBatchLoading(false);
  //   }
  // };

  const handleInfer = async () => {
    const objectType = selectedObjectType;
    const id = instanceInput || selectedInstanceId;
    if (!objectType || !id) return;

    setLoading(true);
    setInferError('');
    setInferenceResult(null);

    try {
      const result = await reasoningApi.infer(objectType, id);
      setInferenceResult(result);
    } catch (err: any) {
      setInferError(err.response?.data?.message || err.message || 'Inference failed');
    } finally {
      setLoading(false);
    }
  };

  // 根据规则名确定颜色
  const getRuleColor = (ruleName: string) => {
    if (ruleName.includes('scope')) return 'bg-gray-500';
    if (ruleName.includes('split_status') || ruleName.includes('split_normal') || ruleName.includes('split_route') || ruleName.includes('split_fee') || ruleName.includes('split_actual')) return 'bg-blue-500';
    if (ruleName.includes('cause') || ruleName.includes('reason')) return 'bg-amber-500';
    if (ruleName.includes('vehicle') || ruleName.includes('station')) return 'bg-red-500';
    if (ruleName.includes('integrity') || ruleName.includes('count') || ruleName.includes('interval') || ruleName.includes('fee_mismatch')) return 'bg-indigo-500';
    if (ruleName.toLowerCase().includes('normal')) return 'bg-green-500';
    return 'bg-purple-500';
  };

  // 批量推理结果按规则签名分组
  const batchGroups = useMemo(() => {
    if (!batchResults) return [];
    const map = new Map<string, { sig: string; items: BatchInstanceResult[] }>();
    for (const item of batchResults) {
      const sig = ruleSignature(item.firedRules);
      if (!map.has(sig)) map.set(sig, { sig, items: [] });
      map.get(sig)!.items.push(item);
    }
    // 排序：正常通行在最后，异常按数量降序
    return Array.from(map.values()).sort((a, b) => {
      const aNormal = isNormalSignature(a.sig);
      const bNormal = isNormalSignature(b.sig);
      if (aNormal && !bNormal) return 1;
      if (!aNormal && bNormal) return -1;
      return b.items.length - a.items.length;
    });
  }, [batchResults]);

  const toggleGroup = (sig: string) => {
    setExpandedGroups(prev => {
      const next = new Set(prev);
      if (next.has(sig)) next.delete(sig);
      else next.add(sig);
      return next;
    });
  };

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Reasoning Engine</h1>

      {/* 引擎状态 */}
      {status && (
        <div className="mb-6 bg-white rounded-lg border border-gray-200 p-4">
          <div className="flex items-center gap-6">
            <div className="flex items-center gap-2">
              <div className="w-2 h-2 rounded-full bg-green-500"></div>
              <span className="text-sm text-gray-600">Engine Active</span>
            </div>
            <div className="text-sm text-gray-600">
              <span className="font-medium">{status.parsedRules}</span> rules parsed
            </div>
            <div className="text-sm text-gray-600">
              <span className="font-medium">{status.registeredFunctions?.length || 0}</span> functions registered
            </div>
            <span className="text-sm text-gray-600">测试：</span>
            <span className="text-sm text-gray-600">G151137001001020100702026030106322711</span>
            <span className="text-sm text-gray-600">G001537001004020109102026030107180163</span>
          </div>
        </div>
      )}

      {/* 输入区域：按当前本体模型选择对象类型与实例 */}
      <div className="mb-6 bg-white rounded-lg border border-gray-200 p-4">
        <h2 className="text-sm font-semibold text-gray-700 mb-3">选择推理对象（随右上角本体模型变化）</h2>
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-[140px]">
            <label className="block text-xs text-gray-500 mb-1">对象类型</label>
            <select
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              value={selectedObjectType}
              onChange={(e) => { setSelectedObjectType(e.target.value); setSelectedInstanceId(''); setInstanceInput(''); }}
            >
              <option value="">-- 选择类型 --</option>
              {rootTypes.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>
          <div className="flex-1 min-w-[160px]">
            <label className="block text-xs text-gray-500 mb-1">实例</label>
            <select
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              value={selectedInstanceId}
              onChange={(e) => { setSelectedInstanceId(e.target.value); setInstanceInput(''); }}
            >
              <option value="">-- 选择实例 --</option>
              {instances.map((i) => (
                <option key={i.id} value={i.id}>{i.pass_id ?? i.path_id ?? i.id}</option>
              ))}
            </select>
          </div>
          <div className="text-sm text-gray-400 self-center">或</div>
          <div className="flex-1 min-w-[160px]">
            <label className="block text-xs text-gray-500 mb-1">直接输入 ID</label>
            <input
              type="text"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="输入实例 ID"
              value={instanceInput}
              onChange={(e) => { setInstanceInput(e.target.value); setSelectedInstanceId(''); }}
            />
          </div>
          <button
            onClick={handleInfer}
            disabled={loading || !selectedObjectType || (!selectedInstanceId && !instanceInput)}
            className="px-6 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed"
          >
            {loading ? '推理中...' : '执行推理'}
          </button>
          {/* 批量推理（暂不使用）
          <button
            onClick={handleBatchAll}
            disabled={batchLoading || !selectedObjectType}
            title="遍历所有实例执行推理，按规则分组展示结果"
            className="px-6 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:bg-gray-300 disabled:cursor-not-allowed"
          >
            {batchLoading ? '批量推理中...' : '批量推理'}
          </button>
          */}
        </div>
        {batchError && (
          <div className="mt-3 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
            批量推理失败：{batchError}
          </div>
        )}
      </div>

      {/* 单实例推理错误提示 */}
      {inferError && (
        <div className="mb-6 bg-red-50 border border-red-200 rounded-lg p-4 text-sm text-red-700">
          {inferError}
        </div>
      )}

      {/* 单实例推理结果 */}
      {inferenceResult && (
        <div className="mb-6 bg-white rounded-lg border border-gray-200 p-4 space-y-4">
          {/* 加载关联数据 */}
          {inferenceResult.linkedDataSummary && Object.keys(inferenceResult.linkedDataSummary).length > 0 && (
            <div className="border border-gray-100 rounded-lg overflow-hidden">
              <div className="px-3 py-2 bg-slate-50 border-b border-gray-100 text-sm font-medium text-gray-700">
                加载关联数据 (linkedData)
              </div>
              <div className="p-3 space-y-1 text-xs font-mono max-h-40 overflow-y-auto">
                {Object.entries(inferenceResult.linkedDataSummary).map(([key, entry]) => {
                  const count = typeof entry === 'number' ? entry : (entry?.count ?? 0);
                  const displayName = typeof entry === 'object' && entry?.displayName ? entry.displayName : key;
                  return (
                    <div key={key} className="flex items-center gap-2">
                      <span className="text-gray-700">{displayName}</span>
                      <span className="text-gray-400">→</span>
                      <span className="text-gray-800 font-medium">{count} records</span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          <div>
          <h2 className="text-sm font-semibold text-gray-700 mb-4">
            Inference Trace
            <span className="ml-2 text-xs font-normal text-gray-400">
              {inferenceResult.cycleCount} cycles, {inferenceResult.trace.length} rules fired
            </span>
          </h2>
          <div className="space-y-4">
            {inferenceResult.cycles && inferenceResult.cycles.length > 0 ? (
              inferenceResult.cycles.filter((c: CycleDetail) => {
                const rules = c.rules ?? (c as any).rules ?? [];
                return rules.some((r: any) => r.matched && (r.factIsNew ?? r.fact_is_new));
              }).map((cycle: CycleDetail) => {
                const rules = cycle.rules ?? (cycle as any).rules ?? [];
                const matched = rules.filter((r: any) => r.matched && (r.factIsNew ?? r.fact_is_new));
                return (
                  <div key={cycle.cycle} className="border border-gray-200 rounded-lg overflow-hidden">
                    {/* Cycle 标题栏 */}
                    <div className="flex items-center gap-2 px-3 py-2 bg-gray-50 border-b border-gray-200">
                      <div className="w-6 h-6 rounded-full bg-blue-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                        {cycle.cycle}
                      </div>
                      <span className="text-xs font-semibold text-gray-600">
                        Cycle {cycle.cycle}
                      </span>
                      <span className="text-xs text-gray-400">
                        {matched.length} rule{matched.length > 1 ? 's' : ''} fired
                      </span>
                    </div>
                    {/* 匹配的规则 */}
                    <div className="divide-y divide-gray-100">
                      {matched.map((entry, idx) => {
                        const ruleName = entry.rule ?? (entry as any).rule;
                        const displayName = entry.displayName ?? (entry as any).display_name;
                        const fact = entry.fact ?? (entry as any).fact;
                        const matchDetails = entry.matchDetails ?? (entry as any).match_details ?? [];
                        return (
                        <div key={idx} className="px-3 py-2 bg-green-50/50">
                          <div className="flex items-start gap-3">
                            <div className={`w-2 h-2 rounded-full mt-1.5 flex-shrink-0 ${getRuleColor(ruleName || '')}`} />
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2">
                                <span className="text-sm font-medium text-gray-900">{displayName || ruleName || '—'}</span>
                              </div>
                              {fact && (
                                <div className="text-xs text-green-700 font-mono mt-0.5">{fact}</div>
                              )}
                              {/* 匹配原因 */}
                              {matchDetails.length > 0 && (
                                <div className="mt-1.5 ml-1 space-y-0.5">
                                  {matchDetails.map((detail: any, didx: number) => (
                                    <div key={didx} className="flex items-start gap-2 text-[11px]">
                                      <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 mt-1 ${detail.matched ? 'bg-green-400' : 'bg-red-400'}`} />
                                      <div className="min-w-0">
                                        <span className="text-gray-700">{detail.description || detail.condition}</span>
                                        <span className="font-mono text-gray-400 ml-1.5">({detail.condition}{detail.actualValue || detail.actual_value ? ` = ${detail.actualValue ?? detail.actual_value}` : ''})</span>
                                      </div>
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>
                          </div>
                        </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })
            ) : (
              <div className="text-sm text-gray-400 text-center py-4">No rules were triggered</div>
            )}
          </div>
          </div>
        </div>
      )}

      {/* 批量推理分组结果 */}
      {batchResults && (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-gray-700">
              批量推理结果
              <span className="ml-2 text-xs font-normal text-gray-400">
                共 {batchResults.length} 条 · {batchGroups.length} 个规则组
              </span>
            </h2>
            <div className="flex gap-2">
              <button
                onClick={() => setExpandedGroups(new Set(batchGroups.map(g => g.sig)))}
                className="text-xs text-indigo-600 hover:text-indigo-800"
              >
                全部展开
              </button>
              <span className="text-xs text-gray-300">|</span>
              <button
                onClick={() => setExpandedGroups(new Set())}
                className="text-xs text-gray-500 hover:text-gray-700"
              >
                全部折叠
              </button>
            </div>
          </div>

          <div className="space-y-2">
            {batchGroups.map((group) => {
              const isNormal = isNormalSignature(group.sig);
              const isNoRule = group.sig === '(无规则触发)';
              const isExpanded = expandedGroups.has(group.sig);
              const hasError = group.items.some(i => i.error);

              // 分组标题颜色
              const headerBg = isNormal
                ? 'bg-green-50 border-green-200'
                : isNoRule
                  ? 'bg-gray-50 border-gray-200'
                  : hasError
                    ? 'bg-red-50 border-red-200'
                    : 'bg-amber-50 border-amber-200';
              const countBadge = isNormal
                ? 'bg-green-100 text-green-700'
                : isNoRule
                  ? 'bg-gray-100 text-gray-600'
                  : hasError
                    ? 'bg-red-100 text-red-700'
                    : 'bg-amber-100 text-amber-700';

              return (
                <div key={group.sig} className={`border rounded-lg overflow-hidden ${isNormal ? 'border-green-200' : isNoRule ? 'border-gray-200' : hasError ? 'border-red-200' : 'border-amber-200'}`}>
                  {/* 分组标题 */}
                  <button
                    className={`w-full flex items-center gap-3 px-4 py-3 text-left ${headerBg} hover:brightness-95 transition-all`}
                    onClick={() => toggleGroup(group.sig)}
                  >
                    {/* 展开/折叠箭头 */}
                    <span className={`text-gray-400 text-xs transition-transform ${isExpanded ? 'rotate-90' : ''}`}>▶</span>

                    {/* 规则标签列表 */}
                    <div className="flex flex-wrap gap-1.5 flex-1 min-w-0">
                      {group.sig === '(无规则触发)' ? (
                        <span className="text-xs text-gray-500 italic">无规则触发</span>
                      ) : (
                        group.sig.split(' | ').map((rule) => (
                          <span
                            key={rule}
                            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium text-white ${getRuleColor(rule)}`}
                          >
                            {rule}
                          </span>
                        ))
                      )}
                    </div>

                    {/* 实例数量 badge */}
                    <span className={`flex-shrink-0 px-2.5 py-0.5 rounded-full text-xs font-semibold ${countBadge}`}>
                      {group.items.length} 条
                    </span>
                  </button>

                  {/* 展开后的实例列表 */}
                  {isExpanded && (
                    <div className="divide-y divide-gray-100 max-h-80 overflow-y-auto">
                      {group.items.map((item) => (
                        <div key={item.instanceId} className="flex items-start gap-3 px-4 py-2.5 hover:bg-gray-50">
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2">
                              {/* 可点击跳转到单实例推理 */}
                              <button
                                className="text-xs font-mono text-blue-600 hover:text-blue-800 hover:underline truncate max-w-[360px]"
                                onClick={() => {
                                  setInstanceInput(item.instanceId);
                                  setSelectedInstanceId('');
                                  // 滚动到顶部
                                  window.scrollTo({ top: 0, behavior: 'smooth' });
                                }}
                                title="点击填入 ID 并执行单实例推理"
                              >
                                {item.instanceId}
                              </button>
                              {item.error && (
                                <span className="text-xs text-red-500 ml-1">推理失败</span>
                              )}
                            </div>
                            {/* facts 摘要 */}
                            {item.facts && Object.keys(item.facts).length > 0 && (
                              <div className="mt-0.5 flex flex-wrap gap-2">
                                {Object.entries(item.facts).map(([k, v]) => (
                                  <span key={k} className="text-[11px] font-mono text-gray-500">
                                    {k}=<span className="text-gray-800">{String(v)}</span>
                                  </span>
                                ))}
                              </div>
                            )}
                            {item.error && (
                              <div className="text-[11px] text-red-500 mt-0.5">{item.error}</div>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
