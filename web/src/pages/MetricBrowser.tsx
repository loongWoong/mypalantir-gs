import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { metricApi } from '../api/metric';
import type { MetricDefinition, AtomicMetric } from '../api/metric';
import { PlusIcon, PencilIcon, TrashIcon } from '@heroicons/react/24/outline';

const MetricBrowser: React.FC = () => {
  const [metrics, setMetrics] = useState<MetricDefinition[]>([]);
  const [atomicMetrics, setAtomicMetrics] = useState<AtomicMetric[]>([]);
  const [loading, setLoading] = useState(false);
  const [filter, setFilter] = useState<'all' | 'atomic' | 'derived' | 'composite'>('all');
  const navigate = useNavigate();

  useEffect(() => {
    loadMetrics();
  }, [filter]);

  const loadMetrics = async () => {
    setLoading(true);
    try {
      const [metricsData, atomicData] = await Promise.all([
        filter === 'all' || filter === 'atomic'
          ? metricApi.listMetricDefinitions()
          : metricApi.listMetricDefinitions(filter),
        metricApi.listAtomicMetrics(),
      ]);
      
      // 转换字段名：后端可能返回 camelCase，需要转换为 snake_case
      const normalizedMetrics = metricsData.map((m: any) => ({
        ...m,
        metric_type: m.metric_type || m.metricType,
        display_name: m.display_name || m.displayName,
        time_dimension: m.time_dimension || m.timeDimension,
        time_granularity: m.time_granularity || m.timeGranularity,
        atomic_metric_id: m.atomic_metric_id || m.atomicMetricId,
        business_scope: m.business_scope || m.businessScope,
        filter_conditions: m.filter_conditions || m.filterConditions,
        comparison_type: m.comparison_type || m.comparisonType,
        derived_formula: m.derived_formula || m.derivedFormula,
        base_metric_ids: m.base_metric_ids || m.baseMetricIds,
      }));
      
      setMetrics(normalizedMetrics);
      setAtomicMetrics(atomicData);
    } catch (error) {
      console.error('Failed to load metrics:', error);
      alert('加载指标失败: ' + (error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('确定要删除这个指标吗？')) {
      return;
    }
    try {
      await metricApi.deleteMetricDefinition(id);
      loadMetrics();
    } catch (error) {
      console.error('Failed to delete metric:', error);
      alert('删除指标失败: ' + (error as Error).message);
    }
  };

  return (
    <div className="container mx-auto p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-3xl font-bold">指标浏览器</h1>
        <button
          onClick={() => navigate('/metrics/builder')}
          className="flex items-center px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          <PlusIcon className="w-5 h-5 mr-2" />
          创建指标
        </button>
      </div>

      <div className="mb-4 flex space-x-2">
        <button
          onClick={() => setFilter('all')}
          className={`px-4 py-2 rounded ${filter === 'all' ? 'bg-blue-500 text-white' : 'bg-gray-200'}`}
        >
          全部
        </button>
        <button
          onClick={() => setFilter('atomic')}
          className={`px-4 py-2 rounded ${filter === 'atomic' ? 'bg-blue-500 text-white' : 'bg-gray-200'}`}
        >
          原子指标
        </button>
        <button
          onClick={() => setFilter('derived')}
          className={`px-4 py-2 rounded ${filter === 'derived' ? 'bg-blue-500 text-white' : 'bg-gray-200'}`}
        >
          派生指标
        </button>
        <button
          onClick={() => setFilter('composite')}
          className={`px-4 py-2 rounded ${filter === 'composite' ? 'bg-blue-500 text-white' : 'bg-gray-200'}`}
        >
          复合指标
        </button>
      </div>

      {loading ? (
        <div className="text-center py-8">加载中...</div>
      ) : (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  指标名称
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  类型
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  描述
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  状态
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  操作
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {/* 显示原子指标 */}
              {(filter === 'all' || filter === 'atomic') && atomicMetrics.map(atomic => (
                <tr key={atomic.id}>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm font-medium text-gray-900">
                      {atomic.display_name || atomic.name}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-purple-100 text-purple-800">
                      原子指标
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <div className="text-sm text-gray-500">{atomic.description || '-'}</div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                      atomic.status === 'active' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                    }`}>
                      {atomic.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                    <button
                      onClick={() => navigate(`/metrics/${atomic.id}`)}
                      className="text-indigo-600 hover:text-indigo-900 mr-4"
                    >
                      查看
                    </button>
                    <button
                      onClick={async () => {
                        if (!confirm('确定要删除这个原子指标吗？')) return;
                        try {
                          await metricApi.deleteAtomicMetric(atomic.id);
                          loadMetrics();
                        } catch (error) {
                          console.error('Failed to delete atomic metric:', error);
                          alert('删除原子指标失败: ' + (error as Error).message);
                        }
                      }}
                      className="text-red-600 hover:text-red-900"
                    >
                      删除
                    </button>
                  </td>
                </tr>
              ))}
              {/* 显示派生指标和复合指标 */}
              {(filter === 'all' || filter === 'derived' || filter === 'composite') && metrics
                .filter(metric => {
                  if (filter === 'all') return true;
                  if (filter === 'derived') return metric.metric_type === 'derived';
                  if (filter === 'composite') return metric.metric_type === 'composite';
                  return false;
                })
                .map(metric => (
                <tr key={metric.id}>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm font-medium text-gray-900">
                      {metric.display_name || metric.name}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-blue-100 text-blue-800">
                      {metric.metric_type === 'derived' ? '派生指标' : '复合指标'}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <div className="text-sm text-gray-500">{metric.description || '-'}</div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                      metric.status === 'active' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                    }`}>
                      {metric.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                    <button
                      onClick={() => navigate(`/metrics/${metric.id}`)}
                      className="text-indigo-600 hover:text-indigo-900 mr-4"
                    >
                      查看
                    </button>
                    <button
                      onClick={() => handleDelete(metric.id)}
                      className="text-red-600 hover:text-red-900"
                    >
                      删除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {!loading && (
        (filter === 'atomic' && atomicMetrics.length === 0) ||
        (filter === 'derived' && metrics.filter(m => m.metric_type === 'derived').length === 0) ||
        (filter === 'composite' && metrics.filter(m => m.metric_type === 'composite').length === 0) ||
        (filter === 'all' && atomicMetrics.length === 0 && metrics.length === 0)
      ) && (
        <div className="text-center py-8 text-gray-500">
          暂无指标，点击"创建指标"开始创建
        </div>
      )}
    </div>
  );
};

export default MetricBrowser;
