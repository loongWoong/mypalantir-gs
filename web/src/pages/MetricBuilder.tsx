import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import DerivedMetricBuilder from '../components/metric/DerivedMetricBuilder';
import CompositeMetricBuilder from '../components/metric/CompositeMetricBuilder';
import AtomicMetricBuilder from '../components/metric/AtomicMetricBuilder';
import { metricApi } from '../api/metric';
import type { AtomicMetric, MetricDefinition } from '../api/metric';

type MetricType = 'derived' | 'atomic' | 'composite' | null;

const MetricBuilder: React.FC = () => {
  const [searchParams] = useSearchParams();
  const metricId = searchParams.get('id');
  const metricTypeParam = searchParams.get('type') as MetricType;
  
  const [selectedType, setSelectedType] = useState<MetricType>(metricTypeParam);
  const [editMode, setEditMode] = useState<boolean>(!!metricId);
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
      <div className="container mx-auto p-6">
        <div className="text-center py-8">加载中...</div>
      </div>
    );
  }

  if (selectedType === null) {
    return (
      <div className="container mx-auto p-6">
        <div className="max-w-4xl mx-auto">
          <h1 className="text-3xl font-bold mb-6">{editMode ? '编辑指标' : '指标构建器'}</h1>
          
          <div className="space-y-4">
            <div
              onClick={() => setSelectedType('derived')}
              className="p-6 border-2 border-blue-500 rounded-lg cursor-pointer hover:bg-blue-50 transition-colors"
            >
              <div className="flex items-center">
                <div className="w-4 h-4 rounded-full bg-blue-500 mr-4"></div>
                <div>
                  <h2 className="text-xl font-semibold mb-2">派生指标（推荐）</h2>
                  <p className="text-gray-600">
                    由原子指标+时间周期+维度组成，业务中最常用
                  </p>
                  <p className="text-sm text-gray-500 mt-1">
                    示例：日交易金额、月度播放VV
                  </p>
                </div>
              </div>
            </div>

            <div
              onClick={() => setSelectedType('atomic')}
              className="p-6 border-2 border-gray-300 rounded-lg cursor-pointer hover:bg-gray-50 transition-colors"
            >
              <div className="flex items-center">
                <div className="w-4 h-4 rounded-full border-2 border-gray-400 mr-4"></div>
                <div>
                  <h2 className="text-xl font-semibold mb-2">原子指标</h2>
                  <p className="text-gray-600">
                    定义最小的可度量单元，作为派生指标的基础
                  </p>
                  <p className="text-sm text-gray-500 mt-1">
                    示例：交易金额、播放次数
                  </p>
                </div>
              </div>
            </div>

            <div
              onClick={() => setSelectedType('composite')}
              className="p-6 border-2 border-gray-300 rounded-lg cursor-pointer hover:bg-gray-50 transition-colors"
            >
              <div className="flex items-center">
                <div className="w-4 h-4 rounded-full border-2 border-gray-400 mr-4"></div>
                <div>
                  <h2 className="text-xl font-semibold mb-2">复合指标</h2>
                  <p className="text-gray-600">
                    基于现有指标通过公式计算得出
                  </p>
                  <p className="text-sm text-gray-500 mt-1">
                    示例：平均交易金额、转化率
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div className="mt-6">
            <button
              onClick={() => navigate('/metrics')}
              className="px-4 py-2 bg-gray-200 text-gray-700 rounded hover:bg-gray-300"
            >
              取消
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container mx-auto p-6">
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
