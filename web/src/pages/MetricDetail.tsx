import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { metricApi } from '../api/metric';
import type { MetricDefinition, AtomicMetric } from '../api/metric';
import { ArrowLeftIcon } from '@heroicons/react/24/outline';

const MetricDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [metric, setMetric] = useState<MetricDefinition | AtomicMetric | null>(null);
  const [metricType, setMetricType] = useState<'atomic' | 'derived' | 'composite' | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (id) {
      loadMetric();
    }
  }, [id]);

  const loadMetric = async () => {
    if (!id) return;
    
    setLoading(true);
    setError(null);
    try {
      // 先尝试作为原子指标加载
      try {
        const atomic = await metricApi.getAtomicMetric(id);
        // 转换字段名：后端可能返回 camelCase，需要转换为 snake_case
        const normalizedAtomic = {
          ...atomic,
          display_name: (atomic as any).display_name || (atomic as any).displayName,
          business_process: (atomic as any).business_process || (atomic as any).businessProcess,
          aggregation_function: (atomic as any).aggregation_function || (atomic as any).aggregationFunction,
          aggregation_field: (atomic as any).aggregation_field || (atomic as any).aggregationField,
        };
        setMetric(normalizedAtomic as AtomicMetric);
        setMetricType('atomic');
      } catch (e) {
        // 如果不是原子指标，尝试作为指标定义加载（派生指标或复合指标）
        try {
          const definition = await metricApi.getMetricDefinition(id);
          // 转换字段名
          const normalized = {
            ...definition,
            metric_type: (definition as any).metric_type || (definition as any).metricType,
            display_name: definition.display_name || (definition as any).displayName,
            time_dimension: definition.time_dimension || (definition as any).timeDimension,
            time_granularity: definition.time_granularity || (definition as any).timeGranularity,
            atomic_metric_id: definition.atomic_metric_id || (definition as any).atomicMetricId,
            business_scope: definition.business_scope || (definition as any).businessScope,
            filter_conditions: definition.filter_conditions || (definition as any).filterConditions,
            comparison_type: definition.comparison_type || (definition as any).comparisonType,
            derived_formula: definition.derived_formula || (definition as any).derivedFormula,
            base_metric_ids: definition.base_metric_ids || (definition as any).baseMetricIds,
          };
          setMetric(normalized as MetricDefinition);
          setMetricType(normalized.metric_type as 'derived' | 'composite');
        } catch (e2) {
          setError('指标不存在');
        }
      }
    } catch (err) {
      console.error('Failed to load metric:', err);
      setError('加载指标失败: ' + (err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto p-6">
        <div className="text-center py-12">
            <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary mx-auto"></div>
            <p className="mt-4 text-gray-500">加载中...</p>
        </div>
      </div>
    );
  }

  if (error || !metric) {
    return (
      <div className="max-w-7xl mx-auto p-6">
        <button
          onClick={() => navigate('/metrics')}
          className="flex items-center text-gray-500 hover:text-text mb-6 transition-colors"
        >
          <ArrowLeftIcon className="w-4 h-4 mr-2" />
          返回指标列表
        </button>
        <div className="text-center py-16 bg-white rounded-lg border border-dashed border-gray-300">
          <p className="text-red-500 font-medium">{error || '指标不存在'}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto p-6">
      <button
        onClick={() => navigate('/metrics')}
        className="flex items-center text-gray-500 hover:text-text mb-6 transition-colors"
      >
        <ArrowLeftIcon className="w-4 h-4 mr-2" />
        返回指标列表
      </button>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-8">
        <div className="mb-8 border-b border-gray-100 pb-6">
          <div className="flex items-center gap-3 mb-2">
            <h1 className="text-3xl font-bold text-text">
                {metricType === 'atomic' 
                ? (metric as AtomicMetric).display_name || (metric as AtomicMetric).name
                : (metric as MetricDefinition).display_name || (metric as MetricDefinition).name
                }
            </h1>
            <span className={`px-3 py-1 inline-flex text-sm font-semibold rounded-full border ${
                metricType === 'atomic' 
                ? 'bg-purple-50 text-purple-700 border-purple-200'
                : metricType === 'derived'
                ? 'bg-blue-50 text-blue-700 border-blue-200'
                : 'bg-orange-50 text-orange-700 border-orange-200'
            }`}>
                {metricType === 'atomic' ? '原子指标' : metricType === 'derived' ? '派生指标' : '复合指标'}
            </span>
          </div>
          <p className="text-gray-500 max-w-3xl">
            {metric.description || (metric as any).description || '暂无描述'}
          </p>
        </div>

        <div className="space-y-8">
          {/* 基本信息 */}
          <div>
            <h2 className="text-lg font-semibold mb-4 text-text flex items-center">
                <span className="w-1 h-5 bg-primary rounded-full mr-2"></span>
                基本信息
            </h2>
            <dl className="grid grid-cols-1 md:grid-cols-2 gap-6 bg-gray-50 p-6 rounded-lg border border-gray-100">
              <div>
                <dt className="text-sm font-medium text-gray-500 mb-1">指标ID</dt>
                <dd className="text-sm text-text font-mono bg-white px-2 py-1 rounded border border-gray-200 inline-block">{metric.id}</dd>
              </div>
              <div>
                <dt className="text-sm font-medium text-gray-500 mb-1">指标名称 (Name)</dt>
                <dd className="text-sm text-text font-mono">
                  {metricType === 'atomic'
                    ? (metric as AtomicMetric).name
                    : (metric as MetricDefinition).name
                  }
                </dd>
              </div>
              {((metricType === 'atomic' && ((metric as AtomicMetric).display_name || (metric as any).displayName)) ||
                (metricType !== 'atomic' && (metric as MetricDefinition).display_name)) && (
                <div>
                  <dt className="text-sm font-medium text-gray-500 mb-1">显示名称 (Display Name)</dt>
                  <dd className="text-sm text-text font-medium">
                    {metricType === 'atomic'
                      ? ((metric as AtomicMetric).display_name || (metric as any).displayName)
                      : (metric as MetricDefinition).display_name}
                  </dd>
                </div>
              )}
              <div>
                <dt className="text-sm font-medium text-gray-500 mb-1">状态</dt>
                <dd>
                  <span className={`px-2.5 py-0.5 inline-flex text-xs font-medium rounded-full border ${
                    metric.status === 'active' 
                        ? 'bg-green-50 text-green-700 border-green-200' 
                        : 'bg-gray-100 text-gray-600 border-gray-200'
                  }`}>
                    {metric.status}
                  </span>
                </dd>
              </div>
            </dl>
          </div>

          {/* 原子指标特有信息 */}
          {metricType === 'atomic' && (
            <div>
              <h2 className="text-lg font-semibold mb-4 text-text flex items-center">
                  <span className="w-1 h-5 bg-purple-500 rounded-full mr-2"></span>
                  原子指标配置
              </h2>
              <dl className="grid grid-cols-1 md:grid-cols-2 gap-6 bg-gray-50 p-6 rounded-lg border border-gray-100">
                <div>
                  <dt className="text-sm font-medium text-gray-500 mb-1">业务过程</dt>
                  <dd className="text-sm text-text">
                    {(metric as AtomicMetric).business_process || (metric as any).businessProcess || '-'}
                  </dd>
                </div>
                <div>
                  <dt className="text-sm font-medium text-gray-500 mb-1">统计方式</dt>
                  <dd className="text-sm text-text">
                    {(metric as AtomicMetric).aggregation_function || (metric as any).aggregationFunction || '-'}
                  </dd>
                </div>
                {((metric as AtomicMetric).aggregation_field || (metric as any).aggregationField) && (
                  <div>
                    <dt className="text-sm font-medium text-gray-500 mb-1">度量字段</dt>
                    <dd className="text-sm text-text font-mono bg-white px-2 py-1 rounded border border-gray-200 inline-block">
                      {(metric as AtomicMetric).aggregation_field || (metric as any).aggregationField}
                    </dd>
                  </div>
                )}
                {(metric as AtomicMetric).unit && (
                  <div>
                    <dt className="text-sm font-medium text-gray-500 mb-1">单位</dt>
                    <dd className="text-sm text-text">{(metric as AtomicMetric).unit}</dd>
                  </div>
                )}
              </dl>
            </div>
          )}

          {/* 派生指标特有信息 */}
          {metricType === 'derived' && (
            <>
              <div>
                <h2 className="text-lg font-semibold mb-4 text-text flex items-center">
                    <span className="w-1 h-5 bg-blue-500 rounded-full mr-2"></span>
                    派生指标配置
                </h2>
                <dl className="grid grid-cols-1 md:grid-cols-2 gap-6 bg-gray-50 p-6 rounded-lg border border-gray-100">
                  {(metric as MetricDefinition).atomic_metric_id && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500 mb-1">引用的原子指标ID</dt>
                      <dd className="text-sm text-text font-mono bg-white px-2 py-1 rounded border border-gray-200 inline-block text-blue-600 cursor-pointer hover:underline" onClick={() => navigate(`/metrics/${(metric as MetricDefinition).atomic_metric_id}`)}>
                        {(metric as MetricDefinition).atomic_metric_id}
                      </dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).time_dimension && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500 mb-1">时间维度</dt>
                      <dd className="text-sm text-text">{(metric as MetricDefinition).time_dimension}</dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).time_granularity && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500 mb-1">时间粒度</dt>
                      <dd className="text-sm text-text">{(metric as MetricDefinition).time_granularity}</dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).dimensions && (metric as MetricDefinition).dimensions!.length > 0 && (
                    <div className="col-span-2">
                      <dt className="text-sm font-medium text-gray-500 mb-1">维度</dt>
                      <dd className="flex flex-wrap gap-2 mt-1">
                        {(metric as MetricDefinition).dimensions!.map(d => (
                            <span key={d} className="px-2 py-1 bg-white border border-gray-200 rounded text-xs font-mono text-gray-600">
                                {d}
                            </span>
                        ))}
                      </dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).comparison_type && (metric as MetricDefinition).comparison_type!.length > 0 && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500 mb-1">对比类型</dt>
                      <dd className="flex flex-wrap gap-2 mt-1">
                        {(metric as MetricDefinition).comparison_type!.map(t => (
                            <span key={t} className="px-2 py-1 bg-blue-50 text-blue-700 rounded text-xs font-medium">
                                {t}
                            </span>
                        ))}
                      </dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).unit && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500 mb-1">单位</dt>
                      <dd className="text-sm text-text">{(metric as MetricDefinition).unit}</dd>
                    </div>
                  )}
                </dl>
              </div>
              {(metric as MetricDefinition).business_scope && (
                <div>
                  <h2 className="text-lg font-semibold mb-4 text-text flex items-center">
                      <span className="w-1 h-5 bg-gray-400 rounded-full mr-2"></span>
                      业务范围
                  </h2>
                  <div className="bg-gray-50 p-4 rounded-lg border border-gray-100 overflow-auto">
                    <pre className="text-sm font-mono text-gray-700">
                        {JSON.stringify((metric as MetricDefinition).business_scope, null, 2)}
                    </pre>
                  </div>
                </div>
              )}
            </>
          )}

          {/* 复合指标特有信息 */}
          {metricType === 'composite' && (
            <>
              <div>
                <h2 className="text-lg font-semibold mb-4 text-text flex items-center">
                    <span className="w-1 h-5 bg-orange-500 rounded-full mr-2"></span>
                    复合指标配置
                </h2>
                <dl className="grid grid-cols-1 gap-6 bg-gray-50 p-6 rounded-lg border border-gray-100">
                  {(metric as MetricDefinition).derived_formula && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500 mb-2">计算公式</dt>
                      <dd className="p-4 bg-white border border-gray-200 rounded-lg font-mono text-sm text-gray-800 shadow-sm">
                        {(metric as MetricDefinition).derived_formula}
                      </dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).base_metric_ids && (metric as MetricDefinition).base_metric_ids!.length > 0 && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500 mb-2">基础指标列表</dt>
                      <dd className="grid grid-cols-1 md:grid-cols-2 gap-3">
                          {(metric as MetricDefinition).base_metric_ids!.map(id => (
                            <div 
                                key={id} 
                                className="flex items-center p-3 bg-white border border-gray-200 rounded-lg cursor-pointer hover:border-primary hover:shadow-sm transition-all"
                                onClick={() => navigate(`/metrics/${id}`)}
                            >
                                <span className="w-2 h-2 rounded-full bg-gray-300 mr-3"></span>
                                <span className="font-mono text-sm text-blue-600">{id}</span>
                            </div>
                          ))}
                      </dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).unit && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500 mb-1">单位</dt>
                      <dd className="text-sm text-text">{(metric as MetricDefinition).unit}</dd>
                    </div>
                  )}
                </dl>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default MetricDetail;
