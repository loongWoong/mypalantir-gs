import React, { useState, useEffect } from 'react';
import { metricApi } from '../../api/metric';
import type { AtomicMetric } from '../../api/metric';
import { schemaApi } from '../../api/client';
import type { ObjectType, Property } from '../../api/client';
import { useWorkspace } from '../../WorkspaceContext';

interface Props {
  onCancel: () => void;
  onSuccess: () => void;
  editMode?: boolean;
  initialData?: AtomicMetric | null;
}

const AtomicMetricBuilder: React.FC<Props> = ({ onCancel, onSuccess, editMode = false, initialData = null }) => {
  const { selectedWorkspaceId, selectedWorkspace } = useWorkspace();
  const [objectTypes, setObjectTypes] = useState<ObjectType[]>([]);
  const [objectTypeProperties, setObjectTypeProperties] = useState<Property[]>([]);
  
  const [name, setName] = useState<string>('');
  const [displayName, setDisplayName] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [businessProcess, setBusinessProcess] = useState<string>('');
  const [aggregationFunction, setAggregationFunction] = useState<string>('');
  const [aggregationField, setAggregationField] = useState<string>('');
  const [unit, setUnit] = useState<string>('');
  const [status, setStatus] = useState<string>('active');
  
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  
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
    loadObjectTypes();
    // 如果是编辑模式且有初始数据，回显数据
    if (editMode && initialData) {
      setName(initialData.name || '');
      setDisplayName((initialData as any).display_name || (initialData as any).displayName || '');
      setDescription(initialData.description || '');
      setBusinessProcess((initialData as any).business_process || (initialData as any).businessProcess || '');
      setAggregationFunction((initialData as any).aggregation_function || (initialData as any).aggregationFunction || '');
      setAggregationField((initialData as any).aggregation_field || (initialData as any).aggregationField || '');
      setUnit(initialData.unit || '');
      setStatus(initialData.status || 'active');
    }
  }, [editMode, initialData]);

  // 根据工作空间过滤对象类型
  const filteredObjectTypes = selectedWorkspace && selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0
    ? objectTypes.filter((ot) => selectedWorkspace.object_types!.includes(ot.name))
    : objectTypes;

  // 当工作空间变化时，如果当前选择的业务过程不在新工作空间中，则清空选择
  useEffect(() => {
    if (businessProcess && selectedWorkspace && selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0) {
      if (!selectedWorkspace.object_types.includes(businessProcess)) {
        setBusinessProcess('');
        setObjectTypeProperties([]);
      }
    }
  }, [selectedWorkspace, businessProcess]);

  useEffect(() => {
    if (businessProcess) {
      loadObjectTypeInfo(businessProcess);
    } else {
      setObjectTypeProperties([]);
    }
  }, [businessProcess]);


  // 自动生成显示名称
  useEffect(() => {
    if (name && !displayName) {
      setDisplayName(name);
    }
  }, [name, displayName]);

  const loadObjectTypes = async () => {
    try {
      const data = await schemaApi.getObjectTypes();
      setObjectTypes(data);
    } catch (error) {
      console.error('Failed to load object types:', error);
      alert('加载对象类型失败: ' + (error as Error).message);
    }
  };

  const loadObjectTypeInfo = async (objectTypeName: string) => {
    try {
      const [, properties] = await Promise.all([
        schemaApi.getObjectType(objectTypeName),
        schemaApi.getObjectTypeProperties(objectTypeName),
      ]);
      setObjectTypeProperties(properties);
    } catch (error) {
      console.error('Failed to load object type info:', error);
      alert('加载对象类型信息失败: ' + (error as Error).message);
    }
  };

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!name.trim()) {
      newErrors.name = '指标名称不能为空';
    }

    if (!businessProcess) {
      newErrors.businessProcess = '请选择业务过程（对象类型）';
    }

    if (!aggregationFunction) {
      newErrors.aggregationFunction = '请选择聚合函数';
    }

    // 所有聚合函数都需要聚合字段
    if (aggregationFunction && !aggregationField.trim()) {
      newErrors.aggregationField = '请选择聚合字段';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSave = async () => {
    if (!validate()) {
      return;
    }

    setLoading(true);
    try {
      // 最终验证：确保所有必填字段都有非空值
      const trimmedName = name.trim();
      if (!trimmedName || !businessProcess || !aggregationFunction || !status) {
        alert('请填写所有必填字段');
        setLoading(false);
        return;
      }

      // 构建数据对象，确保所有字段都是非空字符串
      // 只包含必填字段和有效的可选字段
      const atomicMetric: Record<string, string> = {
        name: trimmedName,
        business_process: businessProcess,
        aggregation_function: aggregationFunction,
        status: status,
      };

      // 可选字段：只在有非空值时才添加
      const trimmedDisplayName = displayName.trim();
      if (trimmedDisplayName && trimmedDisplayName.length > 0) {
        atomicMetric.display_name = trimmedDisplayName;
      }

      const trimmedDescription = description.trim();
      if (trimmedDescription && trimmedDescription.length > 0) {
        atomicMetric.description = trimmedDescription;
      }

      // 所有聚合函数都需要字段
      const trimmedField = aggregationField.trim();
      if (trimmedField && trimmedField.length > 0) {
        atomicMetric.aggregation_field = trimmedField;
      }

      const trimmedUnit = unit.trim();
      if (trimmedUnit && trimmedUnit.length > 0) {
        atomicMetric.unit = trimmedUnit;
      }

      // 确保所有值都是非空字符串（双重检查）
      for (const key in atomicMetric) {
        const value = atomicMetric[key];
        if (value === null || value === undefined || value === '') {
          delete atomicMetric[key];
        }
      }

      // 添加工作空间ID（如果已选择工作空间）
      const requestData: Record<string, any> = { ...atomicMetric };
      if (selectedWorkspaceId) {
        requestData.workspace_ids = [selectedWorkspaceId];
      }

      // 编辑模式下需要添加ID
      if (editMode && initialData) {
        requestData.id = initialData.id;
      }

      // 调试：打印发送的数据和类型
      console.log('Sending atomic metric data:', JSON.stringify(requestData, null, 2));
      console.log('Data types:', Object.entries(requestData).map(([k, v]) => `${k}: ${typeof v}`));

      if (editMode && initialData) {
        // 编辑模式：调用更新API
        await metricApi.updateAtomicMetric(initialData.id, requestData);
        alert('原子指标更新成功！');
      } else {
        // 创建模式：调用创建API
        await metricApi.createAtomicMetric(requestData);
        alert('原子指标创建成功！');
      }
      onSuccess();
    } catch (error: any) {
      console.error('Failed to save atomic metric:', error);
      console.error('Error response:', error.response?.data);
      
      // 尝试从响应中提取错误消息
      let errorMessage = editMode ? '更新原子指标失败' : '创建原子指标失败';
      if (error.response?.data?.message) {
        errorMessage += ': ' + error.response.data.message;
      } else if (error.response?.data?.data) {
        errorMessage += ': ' + error.response.data.data;
      } else if (error.message) {
        errorMessage += ': ' + error.message;
      }
      
      alert(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  // 验证原子指标
  const handleValidate = async () => {
    // 首先验证表单
    if (!validate()) {
      return;
    }

    setValidating(true);
    setValidationError(null);
    setValidationResult(null);
    try {
      // 构建验证数据
      const validateData: Record<string, string> = {
        name: name.trim(),
        business_process: businessProcess,
        aggregation_function: aggregationFunction,
        status: status,
      };

      // 添加可选字段
      if (displayName.trim()) {
        validateData.display_name = displayName.trim();
      }
      if (description.trim()) {
        validateData.description = description.trim();
      }
      if (aggregationField.trim()) {
        validateData.aggregation_field = aggregationField.trim();
      }
      if (unit.trim()) {
        validateData.unit = unit.trim();
      }

      // 调用验证 API
      const result = await metricApi.validateAtomicMetric(validateData);
      
      // 如果 columns 为 null，从 rows 中提取列名
      if (!result.columns && result.rows && result.rows.length > 0) {
        result.columns = Object.keys(result.rows[0]);
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

  // 获取适合聚合的字段
  // COUNT 和 DISTINCT_COUNT 可以使用所有字段
  // 其他函数（SUM, AVG, MIN, MAX）只能使用数值类型字段
  const getAggregatableFields = (): Property[] => {
    if (aggregationFunction === 'COUNT' || aggregationFunction === 'DISTINCT_COUNT') {
      // COUNT 和 DISTINCT_COUNT 可以使用所有字段
      return objectTypeProperties;
    } else {
      // 其他函数只使用数值类型字段
      return objectTypeProperties.filter(prop => {
        const dataType = prop.data_type?.toUpperCase() || '';
        return ['INT', 'INTEGER', 'LONG', 'BIGINT', 'DECIMAL', 'NUMERIC', 'FLOAT', 'DOUBLE', 'REAL', 'NUMBER'].some(
          type => dataType.includes(type)
        );
      });
    }
  };

  const aggregatableFields = getAggregatableFields();
  const requiresField = !!aggregationFunction;

  return (
    <div className="max-w-7xl mx-auto">
      <div className="bg-white rounded-lg shadow-lg p-6">
        <div className="flex justify-between items-center mb-4">
          <div>
            <h2 className="text-2xl font-bold">{editMode ? '编辑原子指标' : '原子指标构建器'}</h2>
            <p className="text-gray-600 text-sm mt-1">定义在特定业务过程上的最小可度量单元</p>
          </div>
          <div className="flex space-x-3">
            <button
              onClick={handleValidate}
              disabled={validating || loading}
              className="px-4 py-2 bg-green-500 text-white rounded hover:bg-green-600 disabled:bg-gray-400 transition-colors flex items-center gap-2"
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
              className="px-4 py-2 bg-gray-200 text-gray-700 rounded hover:bg-gray-300 transition-colors"
              disabled={loading || validating}
            >
              取消
            </button>
            <button
              onClick={handleSave}
              disabled={loading || validating}
              className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400 transition-colors"
            >
              {loading ? (editMode ? '保存中...' : '创建中...') : (editMode ? '保存修改' : '创建原子指标')}
            </button>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-4">
          {/* 第一列：基本信息 */}
          <div className="space-y-3">
            <div className="text-sm font-semibold text-gray-700 border-b pb-1">基本信息</div>
            
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">
                指标名称 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className={`w-full p-2 border rounded text-sm ${errors.name ? 'border-red-500' : ''}`}
                placeholder="例如: 交易金额"
              />
              {errors.name && <p className="text-red-500 text-xs mt-0.5">{errors.name}</p>}
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">
                显示名称
              </label>
              <input
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                className="w-full p-2 border rounded text-sm"
                placeholder="界面显示名称"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">
                描述
              </label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="w-full p-2 border rounded text-sm"
                rows={3}
                placeholder="描述用途和含义"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">
                单位
              </label>
              <input
                type="text"
                value={unit}
                onChange={(e) => setUnit(e.target.value)}
                className="w-full p-2 border rounded text-sm"
                placeholder="元、次、个、%"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">
                状态
              </label>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                className="w-full p-2 border rounded text-sm"
              >
                <option value="active">启用</option>
                <option value="inactive">禁用</option>
              </select>
            </div>
          </div>

          {/* 第二列：业务过程与聚合配置 */}
          <div className="space-y-3">
            <div className="text-sm font-semibold text-gray-700 border-b pb-1">业务过程</div>
            
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">
                对象类型 <span className="text-red-500">*</span>
              </label>
              <select
                value={businessProcess}
                onChange={(e) => setBusinessProcess(e.target.value)}
                className={`w-full p-2 border rounded text-sm ${errors.businessProcess ? 'border-red-500' : ''}`}
              >
                <option value="">请选择对象类型</option>
                {filteredObjectTypes.map(ot => (
                  <option key={ot.name} value={ot.name}>
                    {ot.display_name || ot.name} {ot.description ? `- ${ot.description}` : ''}
                  </option>
                ))}
              </select>
              {selectedWorkspace && filteredObjectTypes.length === 0 && (
                <p className="text-yellow-600 text-xs mt-0.5">工作空间未添加对象类型</p>
              )}
              {errors.businessProcess && (
                <p className="text-red-500 text-xs mt-0.5">{errors.businessProcess}</p>
              )}
            </div>

            <div className="text-sm font-semibold text-gray-700 border-b pb-1 mt-4">聚合配置</div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">
                聚合函数 <span className="text-red-500">*</span>
              </label>
              <select
                value={aggregationFunction}
                onChange={(e) => setAggregationFunction(e.target.value)}
                className={`w-full p-2 border rounded text-sm ${errors.aggregationFunction ? 'border-red-500' : ''}`}
              >
                <option value="">请选择聚合函数</option>
                <option value="SUM">SUM（求和）</option>
                <option value="AVG">AVG（平均值）</option>
                <option value="COUNT">COUNT（计数）</option>
                <option value="MAX">MAX（最大值）</option>
                <option value="MIN">MIN（最小值）</option>
                <option value="DISTINCT_COUNT">DISTINCT_COUNT（去重计数）</option>
              </select>
              {errors.aggregationFunction && (
                <p className="text-red-500 text-xs mt-0.5">{errors.aggregationFunction}</p>
              )}
            </div>

            {requiresField && (
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">
                  聚合字段 <span className="text-red-500">*</span>
                </label>
                {businessProcess ? (
                  <>
                    {aggregatableFields.length > 0 ? (
                      <select
                        value={aggregationField}
                        onChange={(e) => setAggregationField(e.target.value)}
                        className={`w-full p-2 border rounded text-sm ${errors.aggregationField ? 'border-red-500' : ''}`}
                      >
                        <option value="">请选择字段</option>
                        {aggregatableFields.map(prop => (
                          <option key={prop.name} value={prop.name}>
                            {prop.name} ({prop.data_type})
                          </option>
                        ))}
                      </select>
                    ) : (
                      <div className="p-2 bg-yellow-50 border border-yellow-200 rounded">
                        <p className="text-yellow-800 text-xs">
                          {aggregationFunction === 'COUNT' || aggregationFunction === 'DISTINCT_COUNT'
                            ? '无可用字段'
                            : '无数值类型字段'}
                        </p>
                      </div>
                    )}
                    {errors.aggregationField && (
                      <p className="text-red-500 text-xs mt-0.5">{errors.aggregationField}</p>
                    )}
                  </>
                ) : (
                  <div className="p-2 bg-gray-50 border border-gray-200 rounded">
                    <p className="text-gray-600 text-xs">请先选择对象类型</p>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* 第三列：预览 */}
          <div className="space-y-3">
            <div className="text-sm font-semibold text-gray-700 border-b pb-1">预览</div>
            
            {(name || businessProcess || aggregationFunction) ? (
              <div className="bg-gray-50 p-3 rounded border space-y-1.5 text-xs">
                <div>
                  <span className="font-medium text-gray-700">指标名称：</span>
                  <span className="text-gray-900">{name || '-'}</span>
                </div>
                <div>
                  <span className="font-medium text-gray-700">显示名称：</span>
                  <span className="text-gray-900">{displayName || name || '-'}</span>
                </div>
                <div>
                  <span className="font-medium text-gray-700">业务过程：</span>
                  <span className="text-gray-900">
                    {objectTypes.find(ot => ot.name === businessProcess)?.display_name || businessProcess || '-'}
                  </span>
                </div>
                <div>
                  <span className="font-medium text-gray-700">聚合函数：</span>
                  <span className="text-gray-900">{aggregationFunction || '-'}</span>
                </div>
                {aggregationField && (
                  <div>
                    <span className="font-medium text-gray-700">聚合字段：</span>
                    <span className="text-gray-900">{aggregationField}</span>
                  </div>
                )}
                {unit && (
                  <div>
                    <span className="font-medium text-gray-700">单位：</span>
                    <span className="text-gray-900">{unit}</span>
                  </div>
                )}
                {description && (
                  <div>
                    <span className="font-medium text-gray-700">描述：</span>
                    <span className="text-gray-900">{description}</span>
                  </div>
                )}
                <div>
                  <span className="font-medium text-gray-700">状态：</span>
                  <span className="text-gray-900">{status === 'active' ? '启用' : '禁用'}</span>
                </div>
              </div>
            ) : (
              <div className="bg-gray-50 p-3 rounded border">
                <p className="text-xs text-gray-500 text-center">填写信息后将显示预览</p>
              </div>
            )}
            
            <div className="bg-blue-50 p-3 rounded border border-blue-200">
              <p className="text-xs text-blue-800 font-medium mb-1">提示</p>
              <ul className="text-xs text-blue-700 space-y-1 list-disc list-inside">
                <li>指标名称建议使用英文</li>
                <li>显示名称可以使用中文</li>
                <li>COUNT可用于所有字段</li>
                <li>SUM/AVG仅限数值字段</li>
              </ul>
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

export default AtomicMetricBuilder;
