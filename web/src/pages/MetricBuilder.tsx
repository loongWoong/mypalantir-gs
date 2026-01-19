import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import DerivedMetricBuilder from '../components/metric/DerivedMetricBuilder';
import CompositeMetricBuilder from '../components/metric/CompositeMetricBuilder';
import AtomicMetricBuilder from '../components/metric/AtomicMetricBuilder';
import { metricApi } from '../api/metric';
import type { AtomicMetric, MetricDefinition } from '../api/metric';
import { ChartBarIcon } from '@heroicons/react/24/outline';

type MetricType = 'derived' | 'atomic' | 'composite' | null;

const MetricBuilder: React.FC = () => {
  const [searchParams] = useSearchParams();
  const metricId = searchParams.get('id');
  const metricTypeParam = searchParams.get('type') as MetricType;
  
  const [selectedType, setSelectedType] = useState<MetricType>(metricTypeParam);
  const editMode = !!metricId;
  const [atomicMetricData, setAtomicMetricData] = useState<AtomicMetric | null>(null);
  const [metricDefinitionData, setMetricDefinitionData] = useState<MetricDefinition | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const navigate = useNavigate();

  // 当进入编辑模式时，加载指标数据
  useEffect(() => {
    if (metricId && metricTypeParam) {
      loadMetricData(metricId, metricTypeParam);
    }
  }, [metricId, metricTypeParam]);

  const loadMetricData = async (id: string, type: MetricType) => {
    setLoading(true);
    try {
      if (type === 'atomic') {
        const data = await metricApi.getAtomicMetric(id);
        // 字段标准化：统一使用下划线命名
        const normalizedData = {
          ...data,
          display_name: (data as any).display_name || (data as any).displayName,
          business_process: (data as any).business_process || (data as any).businessProcess,
          aggregation_function: (data as any).aggregation_function || (data as any).aggregationFunction,
          aggregation_field: (data as any).aggregation_field || (data as any).aggregationField,
        };
        setAtomicMetricData(normalizedData);
      } else if (type === 'derived' || type === 'composite') {
        const data = await metricApi.getMetricDefinition(id);
        // 字段标准化：统一使用下划线命名
        const normalizedData = {
          ...data,
          metric_type: (data as any).metric_type || (data as any).metricType,
          display_name: (data as any).display_name || (data as any).displayName,
          atomic_metric_id: (data as any).atomic_metric_id || (data as any).atomicMetricId,
          business_scope: (data as any).business_scope || (data as any).businessScope,
          time_dimension: (data as any).time_dimension || (data as any).timeDimension,
          time_granularity: (data as any).time_granularity || (data as any).timeGranularity,
          filter_conditions: (data as any).filter_conditions || (data as any).filterConditions,
          comparison_type: (data as any).comparison_type || (data as any).comparisonType,
          derived_formula: (data as any).derived_formula || (data as any).derivedFormula,
          base_metric_ids: (data as any).base_metric_ids || (data as any).baseMetricIds,
        };
        console.log('加载的指标数据 (标准化后):', normalizedData);
        setMetricDefinitionData(normalizedData as MetricDefinition);
      }
    } catch (error) {
      console.error('Failed to load metric data:', error);
      alert('加载指标数据失败: ' + (error as Error).message);
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

  if (selectedType === null) {
    return (
      <div className="max-w-7xl mx-auto space-y-8">
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-10">
            <h1 className="text-3xl font-bold text-text mb-4 flex items-center justify-center gap-3">
                <ChartBarIcon className="w-10 h-10 text-primary" />
                {editMode ? '编辑指标' : '创建新指标'}
            </h1>
            <p className="text-gray-500 max-w-2xl mx-auto">
                请选择您要创建的指标类型。指标是业务数据的量化度量，您可以从原子指标开始，或者基于现有指标构建更复杂的派生和复合指标。
            </p>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div
              onClick={() => setSelectedType('derived')}
              className="group p-6 bg-white border border-gray-200 rounded-xl cursor-pointer hover:border-primary hover:shadow-md transition-all duration-200 relative overflow-hidden"
            >
              <div className="absolute top-0 right-0 bg-primary text-white text-xs font-bold px-3 py-1 rounded-bl-lg">推荐</div>
              <div className="flex flex-col h-full">
                <div className="w-12 h-12 rounded-lg bg-blue-50 flex items-center justify-center mb-4 group-hover:bg-blue-100 transition-colors">
                    <div className="w-6 h-6 rounded-full bg-blue-500"></div>
                </div>
                <h2 className="text-lg font-bold text-text mb-2">派生指标</h2>
                <p className="text-gray-600 text-sm mb-4 flex-grow">
                  由原子指标 + 时间周期 + 维度组成，是业务分析中最常用的指标类型。
                </p>
                <div className="bg-gray-50 p-3 rounded-lg border border-gray-100">
                    <p className="text-xs text-gray-500 font-medium mb-1">示例：</p>
                    <div className="flex flex-wrap gap-2">
                        <span className="text-xs bg-white border border-gray-200 px-2 py-1 rounded text-gray-600">日交易金额</span>
                        <span className="text-xs bg-white border border-gray-200 px-2 py-1 rounded text-gray-600">月度活跃用户</span>
                    </div>
                </div>
              </div>
            </div>

            <div
              onClick={() => setSelectedType('atomic')}
              className="group p-6 bg-white border border-gray-200 rounded-xl cursor-pointer hover:border-purple-500 hover:shadow-md transition-all duration-200"
            >
              <div className="flex flex-col h-full">
                <div className="w-12 h-12 rounded-lg bg-purple-50 flex items-center justify-center mb-4 group-hover:bg-purple-100 transition-colors">
                    <div className="w-6 h-6 rounded-full border-4 border-purple-400"></div>
                </div>
                <h2 className="text-lg font-bold text-text mb-2">原子指标</h2>
                <p className="text-gray-600 text-sm mb-4 flex-grow">
                  定义最小的可度量单元，不包含任何维度限定，作为派生指标的基础。
                </p>
                <div className="bg-gray-50 p-3 rounded-lg border border-gray-100">
                    <p className="text-xs text-gray-500 font-medium mb-1">示例：</p>
                    <div className="flex flex-wrap gap-2">
                        <span className="text-xs bg-white border border-gray-200 px-2 py-1 rounded text-gray-600">交易金额</span>
                        <span className="text-xs bg-white border border-gray-200 px-2 py-1 rounded text-gray-600">支付次数</span>
                    </div>
                </div>
              </div>
            </div>

            <div
              onClick={() => setSelectedType('composite')}
              className="group p-6 bg-white border border-gray-200 rounded-xl cursor-pointer hover:border-orange-500 hover:shadow-md transition-all duration-200"
            >
              <div className="flex flex-col h-full">
                <div className="w-12 h-12 rounded-lg bg-orange-50 flex items-center justify-center mb-4 group-hover:bg-orange-100 transition-colors">
                    <div className="w-6 h-6 rounded-sm bg-orange-500 transform rotate-45"></div>
                </div>
                <h2 className="text-lg font-bold text-text mb-2">复合指标</h2>
                <p className="text-gray-600 text-sm mb-4 flex-grow">
                  基于现有指标通过数学公式计算得出，用于表达比率、比例等复杂业务含义。
                </p>
                <div className="bg-gray-50 p-3 rounded-lg border border-gray-100">
                    <p className="text-xs text-gray-500 font-medium mb-1">示例：</p>
                    <div className="flex flex-wrap gap-2">
                        <span className="text-xs bg-white border border-gray-200 px-2 py-1 rounded text-gray-600">客单价</span>
                        <span className="text-xs bg-white border border-gray-200 px-2 py-1 rounded text-gray-600">转化率</span>
                    </div>
                </div>
              </div>
            </div>
          </div>

          <div className="mt-12 flex justify-center">
            <button
              onClick={() => navigate('/metrics')}
              className="px-6 py-2.5 bg-white border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors shadow-sm font-medium"
            >
              返回列表
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto">
      {selectedType === 'derived' && (
        <DerivedMetricBuilder
          onCancel={() => navigate('/metrics')}
          onSuccess={() => navigate('/metrics')}
          editMode={editMode}
          initialData={metricDefinitionData}
        />
      )}
      {selectedType === 'composite' && (
        <CompositeMetricBuilder
          onCancel={() => navigate('/metrics')}
          onSuccess={() => navigate('/metrics')}
          editMode={editMode}
          initialData={metricDefinitionData}
        />
      )}
      {selectedType === 'atomic' && (
        <AtomicMetricBuilder
          onCancel={() => navigate('/metrics')}
          onSuccess={() => navigate('/metrics')}
          editMode={editMode}
          initialData={atomicMetricData}
        />
      )}
    </div>
  );
};

export default MetricBuilder;
