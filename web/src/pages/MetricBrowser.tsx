import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { metricApi } from '../api/metric';
import type { MetricDefinition, AtomicMetric } from '../api/metric';
import { 
  PlusIcon, 
  ChartBarIcon, 
  TrashIcon, 
  PencilIcon, 
  EyeIcon,
  FunnelIcon
} from '@heroicons/react/24/outline';

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
    <div className="max-w-7xl mx-auto space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-text flex items-center gap-2">
            <ChartBarIcon className="w-8 h-8 text-primary" />
            指标浏览器
        </h1>
        <button
          onClick={() => navigate('/metrics/builder')}
          className="flex items-center px-4 py-2 bg-primary text-white rounded-lg hover:bg-blue-600 shadow-sm transition-all duration-200 font-medium"
        >
          <PlusIcon className="w-5 h-5 mr-2" />
          创建指标
        </button>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-1 inline-flex">
        {[
          { id: 'all', label: '全部' },
          { id: 'atomic', label: '原子指标' },
          { id: 'derived', label: '派生指标' },
          { id: 'composite', label: '复合指标' }
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setFilter(tab.id as any)}
            className={`px-4 py-2 rounded-md text-sm font-medium transition-all duration-200 ${
              filter === tab.id 
                ? 'bg-primary text-white shadow-sm' 
                : 'text-gray-500 hover:text-text hover:bg-gray-50'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="text-center py-12">
            <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary mx-auto"></div>
            <p className="mt-4 text-gray-500">加载指标数据中...</p>
        </div>
      ) : (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
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
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  操作
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {/* 显示原子指标 */}
              {(filter === 'all' || filter === 'atomic') && atomicMetrics.map(atomic => (
                <tr key={atomic.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm font-medium text-text">
                      {atomic.display_name || atomic.name}
                    </div>
                    {atomic.display_name && (
                        <div className="text-xs text-gray-400 mt-0.5 font-mono">{atomic.name}</div>
                    )}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className="px-2.5 py-0.5 inline-flex text-xs font-medium rounded-full bg-purple-50 text-purple-700 border border-purple-200">
                      原子指标
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <div className="text-sm text-gray-500 max-w-xs truncate" title={atomic.description || ''}>
                        {atomic.description || '-'}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2.5 py-0.5 inline-flex text-xs font-medium rounded-full border ${
                      atomic.status === 'active' 
                        ? 'bg-green-50 text-green-700 border-green-200' 
                        : 'bg-gray-100 text-gray-600 border-gray-200'
                    }`}>
                      {atomic.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                    <div className="flex justify-end items-center space-x-3">
                        <button
                          onClick={() => navigate(`/metrics/${atomic.id}`)}
                          className="text-gray-400 hover:text-primary transition-colors"
                          title="查看详情"
                        >
                          <EyeIcon className="w-5 h-5" />
                        </button>
                        <button
                          onClick={() => navigate(`/metrics/builder?id=${atomic.id}&type=atomic`)}
                          className="text-gray-400 hover:text-blue-600 transition-colors"
                          title="编辑"
                        >
                          <PencilIcon className="w-5 h-5" />
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
                          className="text-gray-400 hover:text-red-600 transition-colors"
                          title="删除"
                        >
                          <TrashIcon className="w-5 h-5" />
                        </button>
                    </div>
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
                <tr key={metric.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm font-medium text-text">
                      {metric.display_name || metric.name}
                    </div>
                    {metric.display_name && (
                        <div className="text-xs text-gray-400 mt-0.5 font-mono">{metric.name}</div>
                    )}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2.5 py-0.5 inline-flex text-xs font-medium rounded-full border ${
                        metric.metric_type === 'derived' 
                            ? 'bg-blue-50 text-blue-700 border-blue-200' 
                            : 'bg-orange-50 text-orange-700 border-orange-200'
                    }`}>
                      {metric.metric_type === 'derived' ? '派生指标' : '复合指标'}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <div className="text-sm text-gray-500 max-w-xs truncate" title={metric.description || ''}>
                        {metric.description || '-'}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2.5 py-0.5 inline-flex text-xs font-medium rounded-full border ${
                      metric.status === 'active' 
                        ? 'bg-green-50 text-green-700 border-green-200' 
                        : 'bg-gray-100 text-gray-600 border-gray-200'
                    }`}>
                      {metric.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                    <div className="flex justify-end items-center space-x-3">
                        <button
                          onClick={() => navigate(`/metrics/${metric.id}`)}
                          className="text-gray-400 hover:text-primary transition-colors"
                          title="查看详情"
                        >
                          <EyeIcon className="w-5 h-5" />
                        </button>
                        <button
                          onClick={() => navigate(`/metrics/builder?id=${metric.id}&type=${metric.metric_type}`)}
                          className="text-gray-400 hover:text-blue-600 transition-colors"
                          title="编辑"
                        >
                          <PencilIcon className="w-5 h-5" />
                        </button>
                        <button
                          onClick={() => handleDelete(metric.id)}
                          className="text-gray-400 hover:text-red-600 transition-colors"
                          title="删除"
                        >
                          <TrashIcon className="w-5 h-5" />
                        </button>
                    </div>
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
        <div className="text-center py-16 bg-white rounded-lg border border-dashed border-gray-300">
          <FunnelIcon className="w-12 h-12 text-gray-300 mx-auto mb-4" />
          <p className="text-gray-500 font-medium">暂无指标数据</p>
          <button
             onClick={() => navigate('/metrics/builder')}
             className="mt-4 px-4 py-2 bg-primary text-white rounded-lg hover:bg-blue-600 shadow-sm transition-all duration-200 text-sm font-medium"
          >
             创建第一个指标
          </button>
        </div>
      )}
    </div>
  );
};

export default MetricBrowser;
