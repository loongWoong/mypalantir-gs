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
      <div className="container mx-auto p-6">
        <div className="text-center py-8">加载中...</div>
      </div>
    );
  }

  if (error || !metric) {
    return (
      <div className="container mx-auto p-6">
        <button
          onClick={() => navigate('/metrics')}
          className="flex items-center text-blue-600 hover:text-blue-800 mb-4"
        >
          <ArrowLeftIcon className="w-5 h-5 mr-2" />
          返回指标列表
        </button>
        <div className="text-center py-8 text-red-500">
          {error || '指标不存在'}
        </div>
      </div>
    );
  }

  return (
    <div className="container mx-auto p-6">
      <button
        onClick={() => navigate('/metrics')}
        className="flex items-center text-blue-600 hover:text-blue-800 mb-4"
      >
        <ArrowLeftIcon className="w-5 h-5 mr-2" />
        返回指标列表
      </button>

      <div className="bg-white rounded-lg shadow p-6">
        <div className="mb-6">
          <h1 className="text-3xl font-bold mb-2">
            {metricType === 'atomic' 
              ? (metric as AtomicMetric).display_name || (metric as AtomicMetric).name
              : (metric as MetricDefinition).display_name || (metric as MetricDefinition).name
            }
          </h1>
          <span className={`px-3 py-1 inline-flex text-sm font-semibold rounded-full ${
            metricType === 'atomic' 
              ? 'bg-purple-100 text-purple-800'
              : metricType === 'derived'
              ? 'bg-blue-100 text-blue-800'
              : 'bg-green-100 text-green-800'
          }`}>
            {metricType === 'atomic' ? '原子指标' : metricType === 'derived' ? '派生指标' : '复合指标'}
          </span>
        </div>

        <div className="space-y-4">
          {/* 基本信息 */}
          <div>
            <h2 className="text-xl font-semibold mb-3">基本信息</h2>
            <dl className="grid grid-cols-2 gap-4">
              <div>
                <dt className="text-sm font-medium text-gray-500">指标ID</dt>
                <dd className="mt-1 text-sm text-gray-900 font-mono">{metric.id}</dd>
              </div>
              <div>
                <dt className="text-sm font-medium text-gray-500">指标名称</dt>
                <dd className="mt-1 text-sm text-gray-900">
                  {metricType === 'atomic'
                    ? (metric as AtomicMetric).name
                    : (metric as MetricDefinition).name
                  }
                </dd>
              </div>
              {((metricType === 'atomic' && ((metric as AtomicMetric).display_name || (metric as any).displayName)) ||
                (metricType !== 'atomic' && (metric as MetricDefinition).display_name)) && (
                <div>
                  <dt className="text-sm font-medium text-gray-500">显示名称</dt>
                  <dd className="mt-1 text-sm text-gray-900">
                    {metricType === 'atomic'
                      ? ((metric as AtomicMetric).display_name || (metric as any).displayName)
                      : (metric as MetricDefinition).display_name}
                  </dd>
                </div>
              )}
              <div>
                <dt className="text-sm font-medium text-gray-500">状态</dt>
                <dd className="mt-1">
                  <span className={`px-2 py-1 inline-flex text-xs font-semibold rounded-full ${
                    metric.status === 'active' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                  }`}>
                    {metric.status}
                  </span>
                </dd>
              </div>
              {(metric.description || (metric as any).description) && (
                <div className="col-span-2">
                  <dt className="text-sm font-medium text-gray-500">描述</dt>
                  <dd className="mt-1 text-sm text-gray-900">
                    {metric.description || (metric as any).description || '-'}
                  </dd>
                </div>
              )}
            </dl>
          </div>

          {/* 原子指标特有信息 */}
          {metricType === 'atomic' && (
            <div>
              <h2 className="text-xl font-semibold mb-3">原子指标配置</h2>
              <dl className="grid grid-cols-2 gap-4">
                <div>
                  <dt className="text-sm font-medium text-gray-500">业务过程</dt>
                  <dd className="mt-1 text-sm text-gray-900">
                    {(metric as AtomicMetric).business_process || (metric as any).businessProcess || '-'}
                  </dd>
                </div>
                <div>
                  <dt className="text-sm font-medium text-gray-500">统计方式</dt>
                  <dd className="mt-1 text-sm text-gray-900">
                    {(metric as AtomicMetric).aggregation_function || (metric as any).aggregationFunction || '-'}
                  </dd>
                </div>
                {((metric as AtomicMetric).aggregation_field || (metric as any).aggregationField) && (
                  <div>
                    <dt className="text-sm font-medium text-gray-500">度量字段</dt>
                    <dd className="mt-1 text-sm text-gray-900">
                      {(metric as AtomicMetric).aggregation_field || (metric as any).aggregationField}
                    </dd>
                  </div>
                )}
                {(metric as AtomicMetric).unit && (
                  <div>
                    <dt className="text-sm font-medium text-gray-500">单位</dt>
                    <dd className="mt-1 text-sm text-gray-900">{(metric as AtomicMetric).unit}</dd>
                  </div>
                )}
              </dl>
            </div>
          )}

          {/* 派生指标特有信息 */}
          {metricType === 'derived' && (
            <>
              <div>
                <h2 className="text-xl font-semibold mb-3">派生指标配置</h2>
                <dl className="grid grid-cols-2 gap-4">
                  {(metric as MetricDefinition).atomic_metric_id && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500">引用的原子指标ID</dt>
                      <dd className="mt-1 text-sm text-gray-900 font-mono">
                        {(metric as MetricDefinition).atomic_metric_id}
                      </dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).time_dimension && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500">时间维度</dt>
                      <dd className="mt-1 text-sm text-gray-900">{(metric as MetricDefinition).time_dimension}</dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).time_granularity && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500">时间粒度</dt>
                      <dd className="mt-1 text-sm text-gray-900">{(metric as MetricDefinition).time_granularity}</dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).dimensions && (metric as MetricDefinition).dimensions!.length > 0 && (
                    <div className="col-span-2">
                      <dt className="text-sm font-medium text-gray-500">维度</dt>
                      <dd className="mt-1 text-sm text-gray-900">
                        {(metric as MetricDefinition).dimensions!.join(', ')}
                      </dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).comparison_type && (metric as MetricDefinition).comparison_type!.length > 0 && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500">对比类型</dt>
                      <dd className="mt-1 text-sm text-gray-900">
                        {(metric as MetricDefinition).comparison_type!.join(', ')}
                      </dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).unit && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500">单位</dt>
                      <dd className="mt-1 text-sm text-gray-900">{(metric as MetricDefinition).unit}</dd>
                    </div>
                  )}
                </dl>
              </div>
              {(metric as MetricDefinition).business_scope && (
                <div>
                  <h2 className="text-xl font-semibold mb-3">业务范围</h2>
                  <pre className="bg-gray-50 p-4 rounded text-sm overflow-auto">
                    {JSON.stringify((metric as MetricDefinition).business_scope, null, 2)}
                  </pre>
                </div>
              )}
            </>
          )}

          {/* 复合指标特有信息 */}
          {metricType === 'composite' && (
            <>
              <div>
                <h2 className="text-xl font-semibold mb-3">复合指标配置</h2>
                <dl className="grid grid-cols-2 gap-4">
                  {(metric as MetricDefinition).derived_formula && (
                    <div className="col-span-2">
                      <dt className="text-sm font-medium text-gray-500">计算公式</dt>
                      <dd className="mt-1 text-sm text-gray-900 font-mono">
                        {(metric as MetricDefinition).derived_formula}
                      </dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).base_metric_ids && (metric as MetricDefinition).base_metric_ids!.length > 0 && (
                    <div className="col-span-2">
                      <dt className="text-sm font-medium text-gray-500">基础指标ID列表</dt>
                      <dd className="mt-1 text-sm text-gray-900">
                        <ul className="list-disc list-inside space-y-1">
                          {(metric as MetricDefinition).base_metric_ids!.map(id => (
                            <li key={id} className="font-mono">{id}</li>
                          ))}
                        </ul>
                      </dd>
                    </div>
                  )}
                  {(metric as MetricDefinition).unit && (
                    <div>
                      <dt className="text-sm font-medium text-gray-500">单位</dt>
                      <dd className="mt-1 text-sm text-gray-900">{(metric as MetricDefinition).unit}</dd>
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
