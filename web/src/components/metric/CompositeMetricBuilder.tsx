import React, { useState, useEffect } from 'react';
import { metricApi } from '../../api/metric';
import type { MetricDefinition } from '../../api/metric';
import { useWorkspace } from '../../WorkspaceContext';

interface Props {
  onCancel: () => void;
  onSuccess: () => void;
  editMode?: boolean;
  initialData?: MetricDefinition | null;
}

// 统一的基础指标类型（可以是原子指标或派生/复合指标）
interface BaseMetric {
  id: string;
  name: string;
  display_name?: string;
  type: 'atomic' | 'derived' | 'composite';
}

const CompositeMetricBuilder: React.FC<Props> = ({ onCancel, onSuccess, editMode = false, initialData = null }) => {
  const { selectedWorkspaceId } = useWorkspace();
  const [baseMetrics, setBaseMetrics] = useState<BaseMetric[]>([]);
  const [formula, setFormula] = useState<string>('');
  const [name, setName] = useState<string>('');
  const [displayName, setDisplayName] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [unit, setUnit] = useState<string>('');
  const [searchTerm, setSearchTerm] = useState<string>('');
  
  // 添加一个标志位，记录是否已经完成初始化回显
  const [isInitialized, setIsInitialized] = useState<boolean>(false);
  
  // 验证相关状态
  const [validating, setValidating] = useState(false);
  const [validationResult, setValidationResult] = useState<{
    sql: string;
    columns: string[];
    rows: Record<string, any>[];
    rowCount: number;
  } | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [showValidationResult, setShowValidationResult] = useState(false);

  useEffect(() => {
    loadBaseMetrics();
  }, []);

  // 单独的useEffect处理编辑模式的数据回显
  useEffect(() => {
    // 如果是编辑模式且有初始数据，且还未初始化，且baseMetrics已加载
    if (editMode && initialData && !isInitialized && baseMetrics.length > 0) {
      console.log('===== 开始回显复合指标数据 =====');
      console.log('initialData:', initialData);
      console.log('baseMetrics count:', baseMetrics.length);
      
      // 回显基本信息
      setName(initialData.name || '');
      setDisplayName(initialData.display_name || '');
      setDescription(initialData.description || '');
      setUnit(initialData.unit || '');
      
      // 回显公式：将ID转换为名称显示
      if (initialData.derived_formula) {
        console.log('原始公式 (ID):', initialData.derived_formula);
        const formulaWithNames = convertFormulaIdsToNames(initialData.derived_formula);
        console.log('转换后公式 (名称):', formulaWithNames);
        setFormula(formulaWithNames);
      }
      
      // 标记为已初始化
      setIsInitialized(true);
      console.log('===== 复合指标数据回显完成 =====');
    }
  }, [editMode, initialData, isInitialized, baseMetrics]);

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

  // 将公式中ID转换为名称（用于回显）
  const convertFormulaIdsToNames = (formulaWithIds: string): string => {
    let convertedFormula = formulaWithIds;
    baseMetrics.forEach(metric => {
      // 替换 {ID} 为 {指标名称}
      const idPattern = new RegExp(`\\{${metric.id.replace(/[.*+?^${}()|[\\]\\]/g, '\\$&')}\\}`, 'g');
      convertedFormula = convertedFormula.replace(idPattern, `{${metric.name}}`);
    });
    return convertedFormula;
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
        unit: unit || undefined,
        status: 'active',
      };

      // 添加工作空间ID
      const requestData: Record<string, any> = { ...metricDefinition };
      if (selectedWorkspaceId) {
        requestData.workspace_ids = [selectedWorkspaceId];
      }

      // 编辑模式下需要添加ID
      if (editMode && initialData) {
        requestData.id = initialData.id;
      }

      if (editMode && initialData) {
        // 编辑模式：调用更新API
        await metricApi.updateMetricDefinition(initialData.id, requestData);
        alert('复合指标更新成功！');
      } else {
        // 创建模式：调用创建API
        await metricApi.createMetricDefinition(requestData);
        alert('复合指标创建成功！');
      }
      onSuccess();
    } catch (error) {
      console.error('Failed to save composite metric:', error);
      alert((editMode ? '更新' : '创建') + '复合指标失败: ' + (error as Error).message);
    }
  };

  // 验证复合指标
  const handleValidate = async () => {
    // 验证必填字段
    if (!formula || formula.trim().length === 0) {
      alert('请输入公式');
      return;
    }
    if (usedMetricIds.length === 0) {
      alert('公式中至少包含一个指标');
      return;
    }

    setValidating(true);
    setValidationError(null);
    setValidationResult(null);
    try {
      // 将公式中的名称转换为ID
      const formulaWithIds = convertFormulaNamesToIds(formula);
      
      const metricDefinition: Partial<MetricDefinition> = {
        name: name || '_validate_',
        display_name: displayName,
        description,
        metric_type: 'composite',
        base_metric_ids: usedMetricIds,
        derived_formula: formulaWithIds,
        unit: unit || undefined,
        status: 'active',
      };

      // 调用验证 API
      const result = await metricApi.validateMetricDefinition(metricDefinition);
      console.log('复合指标验证结果:', result);
      
      // 如果 columns 为 null，从 rows 中提取列名
      if (!result.columns && result.rows && result.rows.length > 0) {
        result.columns = Object.keys(result.rows[0]);
        console.log('从 rows 提取的 columns:', result.columns);
      }
      
      setValidationResult(result);
      setShowValidationResult(true);
    } catch (error: any) {
      console.error('Validation failed:', error);
      let errorMessage = '验证失败';
      if (error.response?.data?.message) {
        errorMessage += ': ' + error.response.data.message;
      } else if (error.message) {
        errorMessage += ': ' + error.message;
      }
      setValidationError(errorMessage);
      setShowValidationResult(true);
    } finally {
      setValidating(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto">
      <div className="bg-white rounded-lg shadow-lg p-6">
        <div className="flex justify-between items-center mb-4">
          <div>
            <h2 className="text-2xl font-bold">{editMode ? '编辑复合指标' : '复合指标构建器'}</h2>
            <p className="text-gray-600 text-sm mt-1">通过组合多个基础指标进行数学运算创建复合指标</p>
          </div>
          <div className="flex space-x-3">
            <button
              onClick={handleValidate}
              disabled={validating || !formula || usedMetricIds.length === 0}
              className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400 transition-colors flex items-center gap-2"
            >
              {validating ? (
                <>
                  <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  验证中...
                </>
              ) : (
                '验证指标'
              )}
            </button>
            <button
              onClick={onCancel}
              className="px-4 py-2 bg-gray-200 text-gray-700 rounded hover:bg-gray-300"
              disabled={validating}
            >
              取消
            </button>
            <button
              onClick={handleSave}
              disabled={validating || !name || usedMetricIds.length === 0 || !formula}
              className="px-4 py-2 bg-green-500 text-white rounded hover:bg-green-600 disabled:bg-gray-300"
            >
              {editMode ? '保存修改' : '保存指标'}
            </button>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-4">
          {/* 第一列：基本信息 */}
          <div className="space-y-3">
            <div className="text-sm font-semibold text-gray-700 border-b pb-1">基本信息</div>
            
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">指标名称 <span className="text-red-500">*</span></label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full p-2 border rounded text-sm"
                placeholder="例如: 日均交易金额"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">显示名称</label>
              <input
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                className="w-full p-2 border rounded text-sm"
                placeholder="界面显示名称"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">描述</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="w-full p-2 border rounded text-sm"
                rows={3}
                placeholder="描述指标用途"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">单位</label>
              <input
                type="text"
                value={unit}
                onChange={(e) => setUnit(e.target.value)}
                className="w-full p-2 border rounded text-sm"
                placeholder="例如: 元/笔"
              />
            </div>
          </div>

          {/* 第二列：公式构建器 */}
          <div className="space-y-3 col-span-2">
            <div className="text-sm font-semibold text-gray-700 border-b pb-1">计算公式构建</div>
            
            {/* 公式显示 */}
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">公式 <span className="text-red-500">*</span></label>
              <input
                type="text"
                value={formula}
                onChange={(e) => setFormula(e.target.value)}
                className="w-full p-2 border-2 border-blue-300 rounded font-mono text-sm bg-white"
                placeholder="公式将显示在这里... 也可以手动输入"
              />
              {formula && usedMetricNames.length > 0 && (
                <div className="mt-1 flex flex-wrap gap-1">
                  {usedMetricNames.map((name, idx) => (
                    <span key={`${name}-${idx}`} className="inline-block bg-blue-100 text-blue-800 px-2 py-0.5 rounded text-xs">
                      {name}
                    </span>
                  ))}
                </div>
              )}
            </div>

            {/* 运算符按钮 */}
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">运算符</label>
              <div className="flex gap-1">
                {['+', '-', '*', '/', '%', '(', ')'].map(op => (
                  <button
                    key={op}
                    onClick={() => addOperatorToFormula(op)}
                    className="px-3 py-1.5 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors font-mono text-sm"
                  >
                    {op}
                  </button>
                ))}
                <button
                  onClick={deleteLastChar}
                  disabled={!formula}
                  className="px-3 py-1.5 bg-yellow-500 text-white rounded hover:bg-yellow-600 disabled:bg-gray-300 text-xs"
                >
                  删除
                </button>
                <button
                  onClick={clearFormula}
                  disabled={!formula}
                  className="px-3 py-1.5 bg-red-500 text-white rounded hover:bg-red-600 disabled:bg-gray-300 text-xs"
                >
                  清空
                </button>
              </div>
            </div>

            {/* 基础指标选择 */}
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">选择基础指标</label>
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="搜索指标..."
                className="w-full p-2 border rounded text-sm mb-2"
              />
              <div className="max-h-56 overflow-y-auto border rounded bg-white">
                {/* 原子指标 */}
                {filteredMetrics.filter(m => m.type === 'atomic').length > 0 && (
                  <div className="p-2">
                    <div className="text-xs font-semibold text-gray-500 mb-1">原子指标</div>
                    <div className="flex flex-wrap gap-1">
                      {filteredMetrics.filter(m => m.type === 'atomic').map(metric => (
                        <button
                          key={metric.id}
                          onClick={() => addMetricToFormula(metric.id)}
                          className="px-2 py-1 bg-green-100 text-green-800 rounded hover:bg-green-200 text-xs"
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
                    <div className="text-xs font-semibold text-gray-500 mb-1">派生指标</div>
                    <div className="flex flex-wrap gap-1">
                      {filteredMetrics.filter(m => m.type === 'derived').map(metric => (
                        <button
                          key={metric.id}
                          onClick={() => addMetricToFormula(metric.id)}
                          className="px-2 py-1 bg-blue-100 text-blue-800 rounded hover:bg-blue-200 text-xs"
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
                    <div className="text-xs font-semibold text-gray-500 mb-1">复合指标</div>
                    <div className="flex flex-wrap gap-1">
                      {filteredMetrics.filter(m => m.type === 'composite').map(metric => (
                        <button
                          key={metric.id}
                          onClick={() => addMetricToFormula(metric.id)}
                          className="px-2 py-1 bg-purple-100 text-purple-800 rounded hover:bg-purple-200 text-xs"
                        >
                          {metric.name}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
                {filteredMetrics.length === 0 && (
                  <div className="p-3 text-center text-gray-400 text-xs">未找到匹配的指标</div>
                )}
              </div>
            </div>

            <div className="bg-blue-50 p-2 rounded border border-blue-200">
              <p className="text-xs text-blue-800">
                提示: 点击指标按钮或运算符按钮构建公式。公式中显示指标名称，保存时自动转换为ID。
              </p>
            </div>
          </div>
        </div>

        {/* 验证结果展示区域 */}
        {showValidationResult && (
          <div className="mt-6 bg-white rounded-lg shadow-lg p-6 border border-gray-200">
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-lg font-semibold text-gray-900">验证结果</h3>
              <button
                onClick={() => setShowValidationResult(false)}
                className="text-gray-500 hover:text-gray-700"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {validationError ? (
              <div className="bg-red-50 border border-red-200 rounded p-4">
                <div className="flex items-start">
                  <svg className="w-5 h-5 text-red-500 mt-0.5 mr-2" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                  </svg>
                  <div>
                    <p className="text-sm font-medium text-red-800">验证失败</p>
                    <p className="text-sm text-red-700 mt-1">{validationError}</p>
                  </div>
                </div>
              </div>
            ) : validationResult ? (
              <div className="space-y-4">
                {/* SQL 展示 */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <label className="text-sm font-medium text-gray-700">执行的 SQL</label>
                    <button
                      onClick={() => {
                        navigator.clipboard.writeText(validationResult.sql);
                        alert('SQL 已复制到剪贴板');
                      }}
                      className="text-xs px-2 py-1 bg-gray-100 text-gray-700 rounded hover:bg-gray-200"
                    >
                      复制 SQL
                    </button>
                  </div>
                  <pre className="text-xs text-gray-800 bg-gray-50 p-3 rounded border border-gray-300 overflow-x-auto font-mono">
                    {validationResult.sql}
                  </pre>
                </div>

                {/* 查询结果展示 */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <label className="text-sm font-medium text-gray-700">
                      查询结果 ({validationResult.rowCount} 条记录)
                    </label>
                  </div>
                  
                  {validationResult.rows && validationResult.rows.length > 0 ? (
                    <div className="overflow-x-auto border border-gray-300 rounded max-h-96 overflow-y-auto">
                      <table className="min-w-full divide-y divide-gray-200">
                        <thead className="bg-gray-50">
                          <tr>
                            {(validationResult.columns || []).map((col, idx) => (
                              <th
                                key={idx}
                                className="px-4 py-2 text-left text-xs font-medium text-gray-700 uppercase tracking-wider"
                              >
                                {col}
                              </th>
                            ))}
                          </tr>
                        </thead>
                        <tbody className="bg-white divide-y divide-gray-200">
                          {validationResult.rows.map((row, rowIdx) => (
                            <tr key={rowIdx} className="hover:bg-gray-50">
                              {(validationResult.columns || []).map((col, colIdx) => (
                                <td key={colIdx} className="px-4 py-2 text-sm text-gray-900">
                                  {row[col] !== undefined && row[col] !== null
                                    ? typeof row[col] === 'object'
                                      ? JSON.stringify(row[col])
                                      : String(row[col])
                                    : '-'}
                                </td>
                              ))}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <div className="bg-gray-50 border border-gray-200 rounded p-4 text-center">
                      <p className="text-sm text-gray-600">无查询结果</p>
                    </div>
                  )}
                </div>
              </div>
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
};

export default CompositeMetricBuilder;
