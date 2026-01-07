import React, { useState, useEffect } from 'react';
import { metricApi } from '../../api/metric';
import type { MetricDefinition } from '../../api/metric';
import { useWorkspace } from '../../WorkspaceContext';

interface Props {
  onCancel: () => void;
  onSuccess: () => void;
}

// 统一的基础指标类型（可以是原子指标或派生/复合指标）
interface BaseMetric {
  id: string;
  name: string;
  display_name?: string;
  type: 'atomic' | 'derived' | 'composite';
}

const CompositeMetricBuilder: React.FC<Props> = ({ onCancel, onSuccess }) => {
  const { selectedWorkspaceId } = useWorkspace();
  const [baseMetrics, setBaseMetrics] = useState<BaseMetric[]>([]);
  const [formula, setFormula] = useState<string>('');
  const [name, setName] = useState<string>('');
  const [displayName, setDisplayName] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [unit, setUnit] = useState<string>('');
  const [searchTerm, setSearchTerm] = useState<string>('');

  useEffect(() => {
    loadBaseMetrics();
  }, []);

  const loadBaseMetrics = async () => {
    try {
      // 同时加载原子指标、派生指标和复合指标
      const [atomicMetrics, derivedMetrics, compositeMetrics] = await Promise.all([
        metricApi.listAtomicMetrics(),
        metricApi.listMetricDefinitions('derived'),
        metricApi.listMetricDefinitions('composite'),
      ]);

      // 合并所有指标，统一格式
      const allMetrics: BaseMetric[] = [
        ...atomicMetrics.map(m => ({
          id: m.id,
          name: m.display_name || m.name,
          type: 'atomic' as const,
        })),
        ...derivedMetrics.map(m => ({
          id: m.id,
          name: m.display_name || m.name,
          type: 'derived' as const,
        })),
        ...compositeMetrics.map(m => ({
          id: m.id,
          name: m.display_name || m.name,
          type: 'composite' as const,
        })),
      ];

      setBaseMetrics(allMetrics);
    } catch (error) {
      console.error('Failed to load base metrics:', error);
    }
  };

  // 从公式中提取已使用的指标名称，并转换为ID
  const getUsedMetricIds = (): string[] => {
    const matches = formula.match(/\{([^}]+)\}/g);
    if (!matches) return [];
    const metricNames = matches.map(m => m.slice(1, -1));
    // 将名称转换为ID
    return metricNames
      .map(name => {
        const metric = baseMetrics.find(m => m.name === name);
        return metric?.id;
      })
      .filter((id): id is string => !!id);
  };

  // 从公式中提取已使用的指标名称（用于显示）
  const getUsedMetricNames = (): string[] => {
    const matches = formula.match(/\{([^}]+)\}/g);
    if (!matches) return [];
    return matches.map(m => m.slice(1, -1));
  };

  // 添加指标到公式（使用名称）
  const addMetricToFormula = (metricId: string) => {
    const metric = baseMetrics.find(m => m.id === metricId);
    if (metric) {
      const metricPlaceholder = `{${metric.name}}`;
      setFormula(prev => prev + metricPlaceholder);
    }
  };

  // 将公式中的名称转换为ID（用于保存）
  const convertFormulaNamesToIds = (formulaWithNames: string): string => {
    let convertedFormula = formulaWithNames;
    baseMetrics.forEach(metric => {
      // 替换 {指标名称} 为 {指标ID}
      const namePattern = new RegExp(`\\{${metric.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\}`, 'g');
      convertedFormula = convertedFormula.replace(namePattern, `{${metric.id}}`);
    });
    return convertedFormula;
  };

  // 添加运算符到公式
  const addOperatorToFormula = (operator: string) => {
    setFormula(prev => prev + operator);
  };

  // 删除公式最后一个字符
  const deleteLastChar = () => {
    setFormula(prev => prev.slice(0, -1));
  };

  // 清空公式
  const clearFormula = () => {
    setFormula('');
  };

  // 过滤指标
  const filteredMetrics = baseMetrics.filter(metric =>
    metric.name.toLowerCase().includes(searchTerm.toLowerCase())
  );

  // 获取已使用的指标ID和名称
  const usedMetricIds = getUsedMetricIds();
  const usedMetricNames = getUsedMetricNames();

  const handleSave = async () => {
    try {
      // 将公式中的名称转换为ID
      const formulaWithIds = convertFormulaNamesToIds(formula);
      
      const metricDefinition: Partial<MetricDefinition> = {
        name,
        display_name: displayName,
        description,
        metric_type: 'composite',
        base_metric_ids: usedMetricIds,
        derived_formula: formulaWithIds,
        unit,
        status: 'active',
      };

      // 添加工作空间ID
      const requestData: Record<string, any> = { ...metricDefinition };
      if (selectedWorkspaceId) {
        requestData.workspace_ids = [selectedWorkspaceId];
      }

      await metricApi.createMetricDefinition(requestData);
      alert('复合指标创建成功！');
      onSuccess();
    } catch (error) {
      console.error('Failed to create composite metric:', error);
      alert('创建复合指标失败: ' + (error as Error).message);
    }
  };

  return (
    <div className="max-w-6xl mx-auto">
      <h2 className="text-2xl font-bold mb-6">复合指标构建器</h2>

      <div className="bg-white p-6 rounded-lg shadow space-y-6">
        <div>
          <label className="block mb-1">指标名称</label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full p-2 border rounded"
            placeholder="例如: 日均交易金额"
          />
        </div>

        <div>
          <label className="block mb-1">显示名称</label>
          <input
            type="text"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="w-full p-2 border rounded"
          />
        </div>

        <div>
          <label className="block mb-1">描述</label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full p-2 border rounded"
            rows={3}
          />
        </div>

        {/* 公式构建器 */}
        <div className="border-2 border-gray-200 rounded-lg p-4 bg-gray-50">
          <label className="block mb-3 font-semibold">计算公式构建</label>
          
          {/* 公式显示区域 */}
          <div className="mb-4">
            <input
              type="text"
              value={formula}
              onChange={(e) => setFormula(e.target.value)}
              className="w-full p-3 border-2 border-blue-300 rounded font-mono text-lg bg-white"
              placeholder="公式将显示在这里... 也可以手动输入"
            />
            {formula && (
              <div className="mt-2 text-sm text-gray-600">
                <span className="font-semibold">已使用指标:</span>
                {usedMetricNames.length > 0 ? (
                  <span className="ml-2">
                    {usedMetricNames.map((name, idx) => (
                      <span key={`${name}-${idx}`} className="inline-block bg-blue-100 text-blue-800 px-2 py-1 rounded mr-2">
                        {name}
                      </span>
                    ))}
                  </span>
                ) : (
                  <span className="ml-2 text-gray-400">暂无</span>
                )}
              </div>
            )}
          </div>

          {/* 运算符按钮 */}
          <div className="mb-4">
            <label className="block mb-2 text-sm font-semibold text-gray-700">运算符</label>
            <div className="flex flex-wrap gap-2">
              {['+', '-', '*', '/', '%', '(', ')'].map(op => (
                <button
                  key={op}
                  onClick={() => addOperatorToFormula(op)}
                  className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors font-mono text-lg"
                >
                  {op}
                </button>
              ))}
            </div>
          </div>

          {/* 基础指标选择 */}
          <div className="mb-4">
            <label className="block mb-2 text-sm font-semibold text-gray-700">选择基础指标</label>
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="搜索指标..."
              className="w-full p-2 border rounded mb-3"
            />
            <div className="max-h-64 overflow-y-auto border rounded bg-white">
              {/* 原子指标 */}
              {filteredMetrics.filter(m => m.type === 'atomic').length > 0 && (
                <div className="p-2">
                  <div className="text-xs font-semibold text-gray-500 mb-1 px-2">原子指标</div>
                  <div className="flex flex-wrap gap-2">
                    {filteredMetrics.filter(m => m.type === 'atomic').map(metric => (
                      <button
                        key={metric.id}
                        onClick={() => addMetricToFormula(metric.id)}
                        className="px-3 py-1.5 bg-green-100 text-green-800 rounded hover:bg-green-200 transition-colors text-sm"
                      >
                        {metric.name}
                      </button>
                    ))}
                  </div>
                </div>
              )}
              {/* 派生指标 */}
              {filteredMetrics.filter(m => m.type === 'derived').length > 0 && (
                <div className="p-2 border-t">
                  <div className="text-xs font-semibold text-gray-500 mb-1 px-2">派生指标</div>
                  <div className="flex flex-wrap gap-2">
                    {filteredMetrics.filter(m => m.type === 'derived').map(metric => (
                      <button
                        key={metric.id}
                        onClick={() => addMetricToFormula(metric.id)}
                        className="px-3 py-1.5 bg-blue-100 text-blue-800 rounded hover:bg-blue-200 transition-colors text-sm"
                      >
                        {metric.name}
                      </button>
                    ))}
                  </div>
                </div>
              )}
              {/* 复合指标 */}
              {filteredMetrics.filter(m => m.type === 'composite').length > 0 && (
                <div className="p-2 border-t">
                  <div className="text-xs font-semibold text-gray-500 mb-1 px-2">复合指标</div>
                  <div className="flex flex-wrap gap-2">
                    {filteredMetrics.filter(m => m.type === 'composite').map(metric => (
                      <button
                        key={metric.id}
                        onClick={() => addMetricToFormula(metric.id)}
                        className="px-3 py-1.5 bg-purple-100 text-purple-800 rounded hover:bg-purple-200 transition-colors text-sm"
                      >
                        {metric.name}
                      </button>
                    ))}
                  </div>
                </div>
              )}
              {filteredMetrics.length === 0 && (
                <div className="p-4 text-center text-gray-400">未找到匹配的指标</div>
              )}
            </div>
          </div>

          {/* 公式操作按钮 */}
          <div className="flex gap-2">
            <button
              onClick={deleteLastChar}
              disabled={!formula}
              className="px-4 py-2 bg-yellow-500 text-white rounded hover:bg-yellow-600 disabled:bg-gray-300 disabled:cursor-not-allowed"
            >
              删除最后一个字符
            </button>
            <button
              onClick={clearFormula}
              disabled={!formula}
              className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600 disabled:bg-gray-300 disabled:cursor-not-allowed"
            >
              清空公式
            </button>
          </div>

          <p className="text-xs text-gray-500 mt-3">
            提示: 点击指标按钮或运算符按钮来构建公式。公式中显示指标名称，保存时会自动转换为ID。
          </p>
        </div>

        <div>
          <label className="block mb-1">单位</label>
          <input
            type="text"
            value={unit}
            onChange={(e) => setUnit(e.target.value)}
            className="w-full p-2 border rounded"
            placeholder="例如: 元/笔"
          />
        </div>

        <div className="flex justify-end space-x-4">
          <button
            onClick={onCancel}
            className="px-4 py-2 bg-gray-200 text-gray-700 rounded hover:bg-gray-300"
          >
            取消
          </button>
          <button
            onClick={handleSave}
            disabled={!name || usedMetricIds.length === 0 || !formula}
            className="px-4 py-2 bg-green-500 text-white rounded hover:bg-green-600 disabled:bg-gray-300"
          >
            保存指标
          </button>
        </div>
      </div>
    </div>
  );
};

export default CompositeMetricBuilder;
