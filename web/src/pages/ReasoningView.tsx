import { useState, useEffect } from 'react';
import { reasoningApi, instanceApi } from '../api/client';
import type { InferenceResult, ReasoningStatus, Instance, CycleDetail, MatchDetail } from '../api/client';

export default function ReasoningView() {
  const [status, setStatus] = useState<ReasoningStatus | null>(null);
  const [rootTypes, setRootTypes] = useState<string[]>([]);
  const [selectedObjectType, setSelectedObjectType] = useState('');
  const [instances, setInstances] = useState<Instance[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState('');
  const [instanceInput, setInstanceInput] = useState('');
  const [inferenceResult, setInferenceResult] = useState<InferenceResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

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

  const handleInfer = async () => {
    const objectType = selectedObjectType;
    const id = instanceInput || selectedInstanceId;
    if (!objectType || !id) return;

    setLoading(true);
    setError('');
    setInferenceResult(null);

    try {
      const result = await reasoningApi.infer(objectType, id);
      setInferenceResult(result);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Inference failed');
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
    return 'bg-purple-500';
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
        </div>
      </div>

      {/* 错误提示 */}
      {error && (
        <div className="mb-6 bg-red-50 border border-red-200 rounded-lg p-4 text-sm text-red-700">
          {error}
        </div>
      )}

      {/* 推理结果 */}
      {inferenceResult && (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <h2 className="text-sm font-semibold text-gray-700 mb-4">
            Inference Trace
            <span className="ml-2 text-xs font-normal text-gray-400">
              {inferenceResult.cycleCount} cycles, {inferenceResult.trace.length} rules fired
            </span>
          </h2>
          <div className="space-y-4">
            {inferenceResult.cycles && inferenceResult.cycles.length > 0 ? (
              inferenceResult.cycles.filter((c: CycleDetail) => c.newFactsProduced).map((cycle: CycleDetail) => {
                const matched = cycle.rules.filter(r => r.matched && r.factIsNew);
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
                      {matched.map((entry, idx) => (
                        <div key={idx} className="px-3 py-2 bg-green-50/50">
                          <div className="flex items-start gap-3">
                            <div className={`w-2 h-2 rounded-full mt-1.5 flex-shrink-0 ${getRuleColor(entry.rule)}`} />
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2">
                                <span className="text-sm font-medium text-gray-900">{entry.displayName || entry.rule}</span>
                              </div>
                              {entry.fact && (
                                <div className="text-xs text-green-700 font-mono mt-0.5">{entry.fact}</div>
                              )}
                              {/* 匹配原因 */}
                              {entry.matchDetails && entry.matchDetails.length > 0 && (
                                <div className="mt-1.5 ml-1 space-y-0.5">
                                  {entry.matchDetails.map((detail: MatchDetail, didx: number) => (
                                    <div key={didx} className="flex items-start gap-2 text-[11px]">
                                      <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 mt-1 ${detail.matched ? 'bg-green-400' : 'bg-red-400'}`} />
                                      <div className="min-w-0">
                                        <span className="text-gray-700">{detail.description || detail.condition}</span>
                                        <span className="font-mono text-gray-400 ml-1.5">({detail.condition}{detail.actualValue ? ` = ${detail.actualValue}` : ''})</span>
                                      </div>
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                );
              })
            ) : (
              <div className="text-sm text-gray-400 text-center py-4">No rules were triggered</div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
