import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import DerivedMetricBuilder from '../components/metric/DerivedMetricBuilder';
import CompositeMetricBuilder from '../components/metric/CompositeMetricBuilder';
import AtomicMetricBuilder from '../components/metric/AtomicMetricBuilder';

type MetricType = 'derived' | 'atomic' | 'composite' | null;

const MetricBuilder: React.FC = () => {
  const [selectedType, setSelectedType] = useState<MetricType>(null);
  const navigate = useNavigate();

  if (selectedType === null) {
    return (
      <div className="container mx-auto p-6">
        <div className="max-w-4xl mx-auto">
          <h1 className="text-3xl font-bold mb-6">指标构建器</h1>
          
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
          onCancel={() => setSelectedType(null)}
          onSuccess={() => navigate('/metrics')}
        />
      )}
      {selectedType === 'composite' && (
        <CompositeMetricBuilder
          onCancel={() => setSelectedType(null)}
          onSuccess={() => navigate('/metrics')}
        />
      )}
      {selectedType === 'atomic' && (
        <AtomicMetricBuilder
          onCancel={() => setSelectedType(null)}
          onSuccess={() => navigate('/metrics')}
        />
      )}
    </div>
  );
};

export default MetricBuilder;
