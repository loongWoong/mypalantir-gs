import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { metricApi, type SqlPasteParseResult, type ExtractedMetric, type MetricValidation, type SemanticAlignment } from '../api/metric';
import { useWorkspace } from '../WorkspaceContext';

const SqlPastePage: React.FC = () => {
  const navigate = useNavigate();
  const { selectedWorkspaceId } = useWorkspace();
  const [sql, setSql] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [result, setResult] = useState<SqlPasteParseResult | null>(null);
  const [selectedMetrics, setSelectedMetrics] = useState<Set<number>>(new Set());
  const [enableLLM, setEnableLLM] = useState<boolean>(false);
  const [saveLoading, setSaveLoading] = useState<boolean>(false);
  const [saveResult, setSaveResult] = useState<any>(null);
  const [validateLoading, setValidateLoading] = useState<boolean>(false);
  const [validateResults, setValidateResults] = useState<Map<number, any>>(new Map());
  const [showValidateResults, setShowValidateResults] = useState<boolean>(false);

  const handleParse = async () => {
    if (!sql.trim()) {
      alert('请输入 SQL 语句');
      return;
    }

    setLoading(true);
    setSaveResult(null);
    try {
      const parseResult = await metricApi.parseAndExtractSql(sql, { enableLLM, workspaceId: selectedWorkspaceId || undefined });
      setResult(parseResult);
      
      // 默认选中所有指标
      const allIndices = new Set<number>();
      parseResult.extractedMetrics.forEach((_, index) => allIndices.add(index));
      setSelectedMetrics(allIndices);
    } catch (error) {
      console.error('SQL 解析失败:', error);
      alert('SQL 解析失败: ' + (error as Error).message);
    } finally {
      setLoading(false);
    }
  }

  const handleClear = () => {
    setSql('');
    setResult(null);
    setSelectedMetrics(new Set());
    setSaveResult(null);
    setValidateResults(new Map());
    setShowValidateResults(false);
  }

  const toggleMetric = (index: number) => {
    const newSelected = new Set(selectedMetrics);
    if (newSelected.has(index)) {
      newSelected.delete(index);
    } else {
      newSelected.add(index);
    }
    setSelectedMetrics(newSelected);
  }

  const toggleAll = () => {
    if (!result) return;
    if (selectedMetrics.size === result.extractedMetrics.length) {
      setSelectedMetrics(new Set());
    } else {
      const allIndices = new Set<number>();
      result.extractedMetrics.forEach((_, index) => allIndices.add(index));
      setSelectedMetrics(allIndices);
    }
  }

  const handleValidate = async () => {
    if (!result || selectedMetrics.size === 0) {
      alert('请选择要验证的指标');
      return;
    }

    setValidateLoading(true);
    setShowValidateResults(true);
    const newValidateResults = new Map<number, any>();

    try {
      // 遍历选中的指标进行验证
      for (const index of Array.from(selectedMetrics)) {
        const metric = result.extractedMetrics[index];
        
        try {
          let validateResult;
          
          // 获取字段
          const businessProcess = metric.definition.businessProcess || metric.definition.business_process;
          const aggregationFunction = metric.definition.aggregationFunction || metric.definition.aggregation_function;
          const aggregationField = metric.definition.aggregationField || metric.definition.aggregation_field;
          
          // 根据指标类型进行验证
          if (metric.category === 'ATOMIC') {
            // 原子指标：只需验证业务过程，不需要聚合函数
            if (!businessProcess) {
              throw new Error('缺少业务过程对象类型');
            }
            // 原子指标不需要 aggregationFunction（直接从物理字段取值）
            
            validateResult = await metricApi.validateAtomicMetric({
              id: metric.id || metric.suggestedId,
              name: metric.name,
              display_name: metric.displayName,
              description: metric.description,
              business_process: businessProcess,
              aggregation_function: aggregationFunction || undefined,
              aggregation_field: aggregationField || '*',
              unit: metric.unit,
              status: 'active'
            });
          } else if (metric.category === 'DERIVED') {
            // 派生指标：需要聚合函数
            if (!businessProcess) {
              throw new Error('缺少业务过程对象类型');
            }
            if (!aggregationFunction) {
              throw new Error('缺少聚合函数');
            }
            
            validateResult = await metricApi.validateMetricDefinition({
              id: metric.id || metric.suggestedId,
              name: metric.name,
              display_name: metric.displayName,
              description: metric.description,
              metric_type: 'derived',
              atomic_metric_id: metric.definition.atomicMetricId || metric.definition.atomic_metric_id,
              time_dimension: metric.definition.timeDimension || metric.definition.time_dimension,
              time_granularity: (metric.definition.timeGranularity || metric.definition.time_granularity) as 'day' | 'week' | 'month' | 'quarter' | 'year' | undefined,
              dimensions: metric.definition.dimensions,
              filter_conditions: metric.definition.filterConditions || metric.definition.filter_conditions,
              derived_formula: undefined,
              base_metric_ids: undefined,
              unit: metric.unit,
              status: 'active'
            });
          } else {
            // 复合指标：不需要聚合函数，需要公式和基础指标
            const derivedFormula = metric.definition.derivedFormula || metric.definition.derived_formula;
            const baseMetricIds = metric.definition.baseMetricIds || metric.definition.base_metric_ids;
            
            validateResult = await metricApi.validateMetricDefinition({
              id: metric.id || metric.suggestedId,
              name: metric.name,
              display_name: metric.displayName,
              description: metric.description,
              metric_type: 'composite',
              atomic_metric_id: undefined,
              time_dimension: undefined,
              time_granularity: undefined,
              dimensions: undefined,
              filter_conditions: undefined,
              derived_formula: derivedFormula,
              base_metric_ids: baseMetricIds,
              unit: metric.unit,
              status: 'active'
            });
          }
          
          newValidateResults.set(index, {
            success: true,
            ...validateResult
          });
        } catch (error: any) {
          newValidateResults.set(index, {
            success: false,
            error: error.message || '验证失败'
          });
        }
      }
      
      setValidateResults(newValidateResults);
    } catch (error) {
      console.error('验证过程失败:', error);
      alert('验证过程失败: ' + (error as Error).message);
    } finally {
      setValidateLoading(false);
    }
  };

  const handleSave = async () => {
    if (!result || selectedMetrics.size === 0) {
      alert('请选择要保存的指标');
      return;
    }

    // 检查是否所有选中指标都已验证通过
    const unvalidatedMetrics: number[] = [];
    const failedMetrics: number[] = [];
    
    for (const index of Array.from(selectedMetrics)) {
      const validateResult = validateResults.get(index);
      if (!validateResult) {
        unvalidatedMetrics.push(index);
      } else if (!validateResult.success) {
        failedMetrics.push(index);
      }
    }

    if (unvalidatedMetrics.length > 0) {
      alert(`有 ${unvalidatedMetrics.length} 个指标未验证，请先验证指标后再保存`);
      return;
    }

    if (failedMetrics.length > 0) {
      alert(`有 ${failedMetrics.length} 个指标验证失败，请修正后再保存`);
      return;
    }

    const metricsToSave: ExtractedMetric[] = result.extractedMetrics
      .filter((_, index) => selectedMetrics.has(index))
      .map(metric => ({
        ...metric,
        id: metric.suggestedId || undefined,
      }));

    setSaveLoading(true);
    try {
      const saveResult = await metricApi.saveExtractedMetrics(metricsToSave, true, [], selectedWorkspaceId ? [selectedWorkspaceId] : []);
      setSaveResult(saveResult);
      
      if (saveResult.success) {
        alert('指标保存成功！');
      } else {
        alert('部分指标保存失败，请查看详细信息');
      }
    } catch (error) {
      console.error('保存失败:', error);
      alert('保存失败: ' + (error as Error).message);
    } finally {
      setSaveLoading(false);
    }
  }

  const getCategoryLabel = (category: string) => {
    switch (category) {
      case 'ATOMIC': return { text: '原子指标', color: 'bg-blue-100 text-blue-800' };
      case 'DERIVED': return { text: '派生指标', color: 'bg-green-100 text-green-800' };
      case 'COMPOSITE': return { text: '复合指标', color: 'bg-purple-100 text-purple-800' };
      default: return { text: category, color: 'bg-gray-100 text-gray-800' };
    }
  }

  const getConfidenceLabel = (confidence?: string) => {
    switch (confidence) {
      case 'HIGH': return { text: '高', color: 'text-green-600' };
      case 'MEDIUM': return { text: '中', color: 'text-yellow-600' };
      case 'LOW': return { text: '低', color: 'text-red-600' };
      default: return { text: '-', color: 'text-gray-600' };
    }
  }

  const getValidationStatus = (validation: MetricValidation) => {
    if (!validation.valid) return { text: '有错误', color: 'text-red-600' };
    if (validation.warningCount > 0) return { text: '有警告', color: 'text-yellow-600' };
    return { text: '通过', color: 'text-green-600' };
  }

  return (
    <div className="container mx-auto p-6">
      <div className="max-w-6xl mx-auto">
        {/* 标题 */}
        <div className="mb-6">
          <h1 className="text-3xl font-bold mb-2">SQL 粘贴指标提取</h1>
          <p className="text-gray-600">
            粘贴已有的 SQL 查询语句，系统将自动解析并提取其中的指标定义
          </p>
        </div>

        {/* SQL 输入区域 */}
        <div className="bg-white rounded-lg shadow-md p-6 mb-6">
          <div className="flex items-center justify-between mb-4">
            <label className="block text-lg font-medium text-gray-700">
              SQL 查询语句
            </label>
            <div className="flex items-center space-x-4">
              <label className="flex items-center">
                <input
                  type="checkbox"
                  checked={enableLLM}
                  onChange={(e) => setEnableLLM(e.target.checked)}
                  className="mr-2 h-4 w-4 text-blue-600 rounded"
                />
                <span className="text-sm text-gray-600">启用 LLM 语义分析</span>
              </label>
            </div>
          </div>
          
          <textarea
            value={sql}
            onChange={(e) => setSql(e.target.value)}
            placeholder="SELECT SUM(amount) as total_amount, status FROM orders WHERE created_at >= '2024-01-01' GROUP BY status"
            className="w-full h-40 p-4 border border-gray-300 rounded-lg font-mono text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          />
          
          <div className="mt-4 flex justify-end space-x-3">
            <button
              onClick={handleClear}
              className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition-colors"
            >
              清除
            </button>
            <button
              onClick={handleParse}
              disabled={loading || !sql.trim()}
              className={`px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors ${
                loading || !sql.trim() ? 'opacity-50 cursor-not-allowed' : ''
              }`}
            >
              {loading ? '解析中...' : '解析 SQL'}
            </button>
          </div>
        </div>

        {/* 解析结果 */}
        {result && (
          <div className="space-y-6">
            {/* 解析摘要 */}
            <div className="bg-white rounded-lg shadow-md p-6">
              <h2 className="text-xl font-semibold mb-4">解析结果摘要</h2>
              
              <div className="grid grid-cols-4 gap-4 mb-4">
                <div className="bg-blue-50 rounded-lg p-4 text-center">
                  <div className="text-2xl font-bold text-blue-600">
                    {result.extractedMetrics.length}
                  </div>
                  <div className="text-sm text-gray-600">提取的指标</div>
                </div>
                <div className="bg-green-50 rounded-lg p-4 text-center">
                  <div className="text-2xl font-bold text-green-600">
                    {result.extractedMetrics.filter(m => m.category === 'ATOMIC').length}
                  </div>
                  <div className="text-sm text-gray-600">原子指标</div>
                </div>
                <div className="bg-yellow-50 rounded-lg p-4 text-center">
                  <div className="text-2xl font-bold text-yellow-600">
                    {result.extractedMetrics.filter(m => m.category === 'DERIVED').length}
                  </div>
                  <div className="text-sm text-gray-600">派生指标</div>
                </div>
                <div className="bg-purple-50 rounded-lg p-4 text-center">
                  <div className="text-2xl font-bold text-purple-600">
                    {result.mappingResult?.fieldMappings.length || 0}
                  </div>
                  <div className="text-sm text-gray-600">已映射字段</div>
                </div>
              </div>

              {/* 建议 */}
              {result.suggestions.length > 0 && (
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                  <h3 className="font-medium text-blue-800 mb-2">建议</h3>
                  <ul className="list-disc list-inside text-sm text-blue-700 space-y-1">
                    {result.suggestions.map((suggestion, index) => (
                      <li key={index}>{suggestion}</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* 错误 */}
              {result.errors && result.errors.length > 0 && (
                <div className="bg-red-50 border border-red-200 rounded-lg p-4 mt-4">
                  <h3 className="font-medium text-red-800 mb-2">错误</h3>
                  <ul className="list-disc list-inside text-sm text-red-700 space-y-1">
                    {result.errors.map((error, index) => (
                      <li key={index}>{error}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            {/* 指标列表 */}
            {result.extractedMetrics.length > 0 && (
              <div className="bg-white rounded-lg shadow-md p-6">
                <div className="flex items-center justify-between mb-4">
                  <h2 className="text-xl font-semibold">提取的指标</h2>
                  <button
                    onClick={toggleAll}
                    className="text-sm text-blue-600 hover:text-blue-800"
                  >
                    {selectedMetrics.size === result.extractedMetrics.length ? '取消全选' : '全选'}
                  </button>
                </div>

                <div className="space-y-4">
                  {result.extractedMetrics.map((metric, index) => {
                    const validation = result.validations[index];
                    const categoryInfo = getCategoryLabel(metric.category);
                    const confidenceInfo = getConfidenceLabel(metric.confidence);
                    const validationStatus = validation ? getValidationStatus(validation) : null;
                    const isSelected = selectedMetrics.has(index);

                    return (
                      <div
                        key={index}
                        className={`border rounded-lg p-4 transition-all cursor-pointer ${
                          isSelected
                            ? 'border-blue-500 bg-blue-50'
                            : 'border-gray-200 hover:border-gray-300'
                        }`}
                        onClick={() => toggleMetric(index)}
                      >
                        <div className="flex items-start space-x-4">
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={(e) => {
                              e.stopPropagation();
                              toggleMetric(index);
                            }}
                            className="mt-1 h-4 w-4 text-blue-600 rounded"
                          />
                          
                          <div className="flex-1">
                            <div className="flex items-center space-x-3 mb-2">
                              <span className={`px-2 py-1 rounded text-xs font-medium ${categoryInfo.color}`}>
                                {categoryInfo.text}
                              </span>
                              <span className="font-medium text-lg">{metric.displayName || metric.name}</span>
                              {metric.sourceSql && (
                                <code className="text-sm text-gray-500 bg-gray-100 px-2 py-0.5 rounded">
                                  {metric.sourceSql}
                                </code>
                              )}
                            </div>

                            {metric.description && (
                              <p className="text-sm text-gray-600 mb-2">{metric.description}</p>
                            )}

                            {/* 指标定义详情 */}
                            <div className="grid grid-cols-2 gap-2 text-sm mt-3">
                              {metric.definition.businessProcess && (
                                <div>
                                  <span className="text-gray-500">业务过程：</span>
                                  <span className="text-gray-800">{metric.definition.businessProcess}</span>
                                </div>
                              )}
                              {metric.definition.aggregationFunction && (
                                <div>
                                  <span className="text-gray-500">聚合函数：</span>
                                  <span className="text-gray-800">{metric.definition.aggregationFunction}</span>
                                </div>
                              )}
                              {metric.definition.timeGranularity && (
                                <div>
                                  <span className="text-gray-500">时间粒度：</span>
                                  <span className="text-gray-800">{metric.definition.timeGranularity}</span>
                                </div>
                              )}
                              {metric.unit && (
                                <div>
                                  <span className="text-gray-500">单位：</span>
                                  <span className="text-gray-800">{metric.unit}</span>
                                </div>
                              )}
                              <div>
                                <span className="text-gray-500">置信度：</span>
                                <span className={`font-medium ${confidenceInfo.color}`}>
                                  {confidenceInfo.text}
                                </span>
                              </div>
                              {validationStatus && (
                                <div>
                                  <span className="text-gray-500">验证：</span>
                                  <span className={`font-medium ${validationStatus.color}`}>
                                    {validationStatus.text}
                                  </span>
                                </div>
                              )}
                            </div>

                            {/* 验证消息 */}
                            {validation && validation.warnings.length > 0 && (
                              <div className="mt-2">
                                {validation.warnings.map((warning, i) => (
                                  <p key={i} className="text-sm text-yellow-600">
                                    ⚠️ {warning.message}
                                  </p>
                                ))}
                              </div>
                            )}
                            
                            {/* 指标验证结果 */}
                            {validateResults.has(index) && (
                              <div className="mt-3 p-3 bg-gray-50 rounded-lg">
                                {validateResults.get(index)?.success ? (
                                  <div className="space-y-2">
                                    <div className="flex items-center text-green-600">
                                      <svg className="w-5 h-5 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                                      </svg>
                                      <span className="font-medium">验证通过</span>
                                    </div>
                                    {validateResults.get(index)?.sql && (
                                      <div>
                                        <p className="text-xs text-gray-500 mb-1">生成的 SQL:</p>
                                        <code className="text-xs bg-white p-2 rounded block overflow-x-auto">
                                          {validateResults.get(index)?.sql}
                                        </code>
                                      </div>
                                    )}
                                    {validateResults.get(index)?.rowCount !== undefined && (
                                      <p className="text-xs text-gray-600">
                                        查询结果: {validateResults.get(index)?.rowCount} 条记录
                                      </p>
                                    )}
                                  </div>
                                ) : (
                                  <div className="flex items-start text-red-600">
                                    <svg className="w-5 h-5 mr-2 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                                    </svg>
                                    <div>
                                      <p className="font-medium">验证失败</p>
                                      <p className="text-sm mt-1">{validateResults.get(index)?.error}</p>
                                    </div>
                                  </div>
                                )}
                              </div>
                            )}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* 语义分析结果 */}
            {result.semanticResult && (
              <div className="bg-white rounded-lg shadow-md p-6">
                <h2 className="text-xl font-semibold mb-4">语义分析结果</h2>

                {/* 识别的指标 */}
                {result.semanticResult.metrics.length > 0 && (
                  <div className="mb-6">
                    <h3 className="font-medium text-gray-700 mb-3">识别的指标</h3>
                    <div className="space-y-2">
                      {result.semanticResult.metrics.map((metric, index) => (
                        <div key={index} className="bg-gray-50 rounded-lg p-3">
                          <div className="flex items-center space-x-2 mb-1">
                            <code className="text-sm bg-gray-200 px-2 py-0.5 rounded">
                              {metric.sqlField}
                            </code>
                            <span className="text-gray-400">→</span>
                            <span className="font-medium">{metric.recommendedName}</span>
                            <span className="text-xs text-gray-500">({metric.businessMeaning})</span>
                          </div>
                          <div className="text-xs text-gray-500">
                            聚合: {metric.aggregationType} | 推荐类型: {metric.suggestedMetricType} | 
                            置信度: {(metric.confidence * 100).toFixed(0)}%
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 识别的维度 */}
                {result.semanticResult.dimensions.length > 0 && (
                  <div className="mb-6">
                    <h3 className="font-medium text-gray-700 mb-3">识别的维度</h3>
                    <div className="grid grid-cols-2 gap-3">
                      {result.semanticResult.dimensions.map((dim, index) => (
                        <div key={index} className="bg-gray-50 rounded-lg p-3">
                          <div className="flex items-center space-x-2 mb-1">
                            <code className="text-sm bg-gray-200 px-2 py-0.5 rounded">
                              {dim.sqlField}
                            </code>
                            <span className="text-gray-400">→</span>
                            <span className="font-medium">{dim.businessMeaning}</span>
                          </div>
                          <div className="text-xs text-gray-500">
                            {dim.timeDimension ? '时间维度' : ''}
                            {dim.enumDimension ? `枚举维度: ${dim.enumValues?.join(', ')}` : ''}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 时间分析 */}
                {result.semanticResult.timeAnalysis && (
                  <div className="mb-6">
                    <h3 className="font-medium text-gray-700 mb-3">时间分析</h3>
                    <div className="bg-gray-50 rounded-lg p-3">
                      <div className="text-sm">
                        {result.semanticResult.timeAnalysis.timeField && (
                          <div>时间字段: {result.semanticResult.timeAnalysis.timeField}</div>
                        )}
                        {result.semanticResult.timeAnalysis.timeGranularity && (
                          <div>时间粒度: {result.semanticResult.timeAnalysis.timeGranularity}</div>
                        )}
                        {result.semanticResult.timeAnalysis.timeRange && (
                          <div>
                            时间范围: {result.semanticResult.timeAnalysis.timeRange.start} ~ 
                            {result.semanticResult.timeAnalysis.timeRange.end}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                )}

                {/* 过滤分析 */}
                {result.semanticResult.filterAnalysis.length > 0 && (
                  <div>
                    <h3 className="font-medium text-gray-700 mb-3">过滤条件分析</h3>
                    <div className="space-y-2">
                      {result.semanticResult.filterAnalysis.map((filter, index) => (
                        <div key={index} className="bg-gray-50 rounded-lg p-3">
                          <code className="text-sm">{filter.field} {filter.operator} {filter.value}</code>
                          {filter.businessScope && (
                            <span className="ml-2 text-xs bg-green-100 text-green-800 px-2 py-0.5 rounded">
                              业务范围限定
                            </span>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* 字段映射结果 */}
            {result.mappingResult && (
              <div className="bg-white rounded-lg shadow-md p-6">
                <h2 className="text-xl font-semibold mb-4">字段映射结果</h2>

                {/* 已映射字段 */}
                {result.mappingResult.fieldMappings.length > 0 && (
                  <div className="mb-6">
                    <h3 className="font-medium text-green-700 mb-3">
                      已映射 ({result.mappingResult.fieldMappings.length})
                    </h3>
                    <div className="space-y-2">
                      {result.mappingResult.fieldMappings.map((mapping, index) => (
                        <div key={index} className="flex items-center space-x-3 bg-green-50 rounded-lg p-3">
                          <code className="text-sm bg-gray-200 px-2 py-0.5 rounded">
                            {mapping.sqlField}
                          </code>
                          <span className="text-gray-400">→</span>
                          <span className="font-medium">{mapping.objectType}.{mapping.objectProperty}</span>
                          <span className={`text-xs px-2 py-0.5 rounded ${
                            mapping.confidence === 'HIGH' ? 'bg-green-200 text-green-800' :
                            mapping.confidence === 'MEDIUM' ? 'bg-yellow-200 text-yellow-800' :
                            'bg-red-200 text-red-800'
                          }`}>
                            {mapping.confidence}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 未映射字段 */}
                {result.mappingResult.unmappedFields.length > 0 && (
                  <div>
                    <h3 className="font-medium text-yellow-700 mb-3">
                      未映射 ({result.mappingResult.unmappedFields.length})
                    </h3>
                    <div className="space-y-2">
                      {result.mappingResult.unmappedFields.map((field, index) => (
                        <div key={index} className="flex items-center space-x-3 bg-yellow-50 rounded-lg p-3">
                          <code className="text-sm bg-gray-200 px-2 py-0.5 rounded">
                            {field.sqlExpression}
                          </code>
                          <span className="text-xs text-gray-500">({field.fieldType})</span>
                          {field.suggestedObjectType && (
                            <span className="text-xs text-gray-500">
                              可能属于: {field.suggestedObjectType}
                            </span>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 涉及的对象类型 */}
                {result.mappingResult.involvedObjectTypes.length > 0 && (
                  <div className="mt-4">
                    <h3 className="font-medium text-gray-700 mb-2">涉及的对象类型</h3>
                    <div className="flex flex-wrap gap-2">
                      {result.mappingResult.involvedObjectTypes.map((type, index) => (
                        <span
                          key={index}
                          className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm"
                        >
                          {type}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* 保存按钮 */}
            <div className="bg-white rounded-lg shadow-md p-6">
              <div className="flex items-center justify-between">
                <div className="text-gray-600">
                  已选择 <span className="font-medium text-blue-600">{selectedMetrics.size}</span> 个指标
                </div>
                <div className="flex space-x-3">
                  <button
                    onClick={handleValidate}
                    disabled={validateLoading || selectedMetrics.size === 0}
                    className={`px-4 py-2 bg-yellow-600 text-white rounded-lg hover:bg-yellow-700 transition-colors ${
                      validateLoading || selectedMetrics.size === 0 ? 'opacity-50 cursor-not-allowed' : ''
                    }`}
                  >
                    {validateLoading ? '验证中...' : '验证指标'}
                  </button>
                  <button
                    onClick={() => navigate('/metrics')}
                    className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition-colors"
                  >
                    取消
                  </button>
                  <button
                    onClick={handleSave}
                    disabled={saveLoading || selectedMetrics.size === 0}
                    className={`px-6 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors ${
                      saveLoading || selectedMetrics.size === 0 ? 'opacity-50 cursor-not-allowed' : ''
                    }`}
                  >
                    {saveLoading ? '保存中...' : '保存选中的指标'}
                  </button>
                </div>
              </div>
            </div>

            {/* 保存结果 */}
            {saveResult && (
              <div className={`rounded-lg p-6 ${saveResult.success ? 'bg-green-50 border border-green-200' : 'bg-red-50 border border-red-200'}`}>
                <h3 className={`font-semibold mb-4 ${saveResult.success ? 'text-green-800' : 'text-red-800'}`}>
                  保存结果
                </h3>
                
                {saveResult.results.length > 0 && (
                  <div className="mb-4">
                    <h4 className="text-sm font-medium text-gray-700 mb-2">成功保存的指标</h4>
                    <div className="space-y-1">
                      {saveResult.results.map((result: any, index: number) => (
                        <div key={index} className="flex items-center space-x-2 text-sm">
                          <span className="text-green-600">✓</span>
                          <span>{result.metricName}</span>
                          <code className="text-xs text-gray-500">{result.savedId}</code>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {saveResult.errors.length > 0 && (
                  <div>
                    <h4 className="text-sm font-medium text-red-700 mb-2">保存失败的指标</h4>
                    <div className="space-y-1">
                      {saveResult.errors.map((error: any, index: number) => (
                        <div key={index} className="flex items-start space-x-2 text-sm">
                          <span className="text-red-600">✗</span>
                          <div>
                            <span className="font-medium">{error.metricName}</span>
                            <p className="text-red-600 text-xs">{error.message}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default SqlPastePage;
