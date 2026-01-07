import React, { useState, useEffect } from 'react';
import { metricApi } from '../../api/metric';
import { schemaApi } from '../../api/client';
import type { ObjectType, Property } from '../../api/client';
import { useWorkspace } from '../../WorkspaceContext';

interface Props {
  onCancel: () => void;
  onSuccess: () => void;
}

const AtomicMetricBuilder: React.FC<Props> = ({ onCancel, onSuccess }) => {
  const { selectedWorkspaceId, selectedWorkspace } = useWorkspace();
  const [objectTypes, setObjectTypes] = useState<ObjectType[]>([]);
  const [objectTypeProperties, setObjectTypeProperties] = useState<Property[]>([]);
  const [selectedObjectTypeInfo, setSelectedObjectTypeInfo] = useState<ObjectType | null>(null);
  
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

  useEffect(() => {
    loadObjectTypes();
  }, []);

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
        setSelectedObjectTypeInfo(null);
      }
    }
  }, [selectedWorkspace, businessProcess]);

  useEffect(() => {
    if (businessProcess) {
      loadObjectTypeInfo(businessProcess);
    } else {
      setObjectTypeProperties([]);
      setSelectedObjectTypeInfo(null);
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
      const [objectType, properties] = await Promise.all([
        schemaApi.getObjectType(objectTypeName),
        schemaApi.getObjectTypeProperties(objectTypeName),
      ]);
      setSelectedObjectTypeInfo(objectType);
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

      // 调试：打印发送的数据和类型
      console.log('Sending atomic metric data:', JSON.stringify(requestData, null, 2));
      console.log('Data types:', Object.entries(requestData).map(([k, v]) => `${k}: ${typeof v}`));

      await metricApi.createAtomicMetric(requestData);
      alert('原子指标创建成功！');
      onSuccess();
    } catch (error: any) {
      console.error('Failed to create atomic metric:', error);
      console.error('Error response:', error.response?.data);
      
      // 尝试从响应中提取错误消息
      let errorMessage = '创建原子指标失败';
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
    <div className="max-w-4xl mx-auto">
      <div className="bg-white rounded-lg shadow-lg p-6">
        <h2 className="text-2xl font-bold mb-6">原子指标构建器</h2>
        <p className="text-gray-600 mb-6">
          原子指标是最小的可度量单元，定义在特定业务过程（对象类型）上的聚合计算。
        </p>

        <div className="space-y-6">
          {/* 基本信息 */}
          <div className="border-b pb-6">
            <h3 className="text-lg font-semibold mb-4">基本信息</h3>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  指标名称 <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className={`w-full p-2 border rounded ${errors.name ? 'border-red-500' : ''}`}
                  placeholder="例如: 交易金额、播放次数"
                />
                {errors.name && <p className="text-red-500 text-sm mt-1">{errors.name}</p>}
                <p className="text-gray-500 text-xs mt-1">用于系统内部标识，建议使用英文或拼音</p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  显示名称
                </label>
                <input
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="w-full p-2 border rounded"
                  placeholder="例如: 交易金额、播放次数"
                />
                <p className="text-gray-500 text-xs mt-1">用于界面显示，如果不填则使用指标名称</p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  描述
                </label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="w-full p-2 border rounded"
                  rows={3}
                  placeholder="描述该原子指标的用途和含义"
                />
              </div>
            </div>
          </div>

          {/* 业务过程配置 */}
          <div className="border-b pb-6">
            <h3 className="text-lg font-semibold mb-4">业务过程配置</h3>
            
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                业务过程（对象类型） <span className="text-red-500">*</span>
              </label>
              <select
                value={businessProcess}
                onChange={(e) => setBusinessProcess(e.target.value)}
                className={`w-full p-2 border rounded ${errors.businessProcess ? 'border-red-500' : ''}`}
              >
                <option value="">请选择对象类型</option>
                {filteredObjectTypes.map(ot => (
                  <option key={ot.name} value={ot.name}>
                    {ot.display_name || ot.name} {ot.description ? `- ${ot.description}` : ''}
                  </option>
                ))}
              </select>
              {selectedWorkspace && filteredObjectTypes.length === 0 && (
                <p className="text-yellow-600 text-xs mt-1">
                  当前工作空间未添加任何对象类型，请先在工作空间管理中添加对象类型
                </p>
              )}
              {errors.businessProcess && (
                <p className="text-red-500 text-sm mt-1">{errors.businessProcess}</p>
              )}
              {selectedObjectTypeInfo && (
                <p className="text-gray-500 text-xs mt-1">
                  已选择: {selectedObjectTypeInfo.display_name || selectedObjectTypeInfo.name}
                  {selectedObjectTypeInfo.description && ` - ${selectedObjectTypeInfo.description}`}
                </p>
              )}
            </div>
          </div>

          {/* 聚合配置 */}
          <div className="border-b pb-6">
            <h3 className="text-lg font-semibold mb-4">聚合配置</h3>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  聚合函数 <span className="text-red-500">*</span>
                </label>
                <select
                  value={aggregationFunction}
                  onChange={(e) => setAggregationFunction(e.target.value)}
                  className={`w-full p-2 border rounded ${errors.aggregationFunction ? 'border-red-500' : ''}`}
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
                  <p className="text-red-500 text-sm mt-1">{errors.aggregationFunction}</p>
                )}
                <p className="text-gray-500 text-xs mt-1">
                  选择对哪个字段进行聚合计算
                </p>
              </div>

              {requiresField && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    聚合字段 <span className="text-red-500">*</span>
                  </label>
                  {businessProcess ? (
                    <>
                      {aggregatableFields.length > 0 ? (
                        <select
                          value={aggregationField}
                          onChange={(e) => setAggregationField(e.target.value)}
                          className={`w-full p-2 border rounded ${errors.aggregationField ? 'border-red-500' : ''}`}
                        >
                          <option value="">请选择字段</option>
                          {aggregatableFields.map(prop => (
                            <option key={prop.name} value={prop.name}>
                              {prop.name} ({prop.data_type})
                              {prop.description ? ` - ${prop.description}` : ''}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <div className="p-3 bg-yellow-50 border border-yellow-200 rounded">
                          <p className="text-yellow-800 text-sm">
                            {aggregationFunction === 'COUNT' || aggregationFunction === 'DISTINCT_COUNT'
                              ? '当前对象类型没有可用的字段。请确保对象类型已配置属性。'
                              : '当前对象类型没有可用的数值类型字段。请确保对象类型已配置数值属性。'}
                          </p>
                        </div>
                      )}
                      {errors.aggregationField && (
                        <p className="text-red-500 text-sm mt-1">{errors.aggregationField}</p>
                      )}
                      <p className="text-gray-500 text-xs mt-1">
                        {aggregationFunction === 'COUNT' || aggregationFunction === 'DISTINCT_COUNT'
                          ? 'COUNT 和 DISTINCT_COUNT 可以使用所有字段'
                          : '仅显示数值类型的字段，用于聚合计算'}
                      </p>
                    </>
                  ) : (
                    <div className="p-3 bg-gray-50 border border-gray-200 rounded">
                      <p className="text-gray-600 text-sm">请先选择业务过程（对象类型）</p>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* 其他配置 */}
          <div className="border-b pb-6">
            <h3 className="text-lg font-semibold mb-4">其他配置</h3>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  单位
                </label>
                <input
                  type="text"
                  value={unit}
                  onChange={(e) => setUnit(e.target.value)}
                  className="w-full p-2 border rounded"
                  placeholder="例如: 元、次、个、%"
                />
                <p className="text-gray-500 text-xs mt-1">指标值的单位，用于显示</p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  状态
                </label>
                <select
                  value={status}
                  onChange={(e) => setStatus(e.target.value)}
                  className="w-full p-2 border rounded"
                >
                  <option value="active">启用</option>
                  <option value="inactive">禁用</option>
                </select>
                <p className="text-gray-500 text-xs mt-1">禁用的指标不会被使用</p>
              </div>
            </div>
          </div>

          {/* 预览 */}
          {(name || businessProcess || aggregationFunction) && (
            <div className="bg-gray-50 p-4 rounded-lg">
              <h3 className="text-lg font-semibold mb-3">预览</h3>
              <div className="space-y-2 text-sm">
                <div>
                  <span className="font-medium">指标名称:</span> {name || '-'}
                </div>
                <div>
                  <span className="font-medium">显示名称:</span> {displayName || name || '-'}
                </div>
                <div>
                  <span className="font-medium">业务过程:</span>{' '}
                  {objectTypes.find(ot => ot.name === businessProcess)?.display_name || businessProcess || '-'}
                </div>
                <div>
                  <span className="font-medium">聚合函数:</span> {aggregationFunction || '-'}
                </div>
                {aggregationField && (
                  <div>
                    <span className="font-medium">聚合字段:</span> {aggregationField}
                  </div>
                )}
                {unit && (
                  <div>
                    <span className="font-medium">单位:</span> {unit}
                  </div>
                )}
                {description && (
                  <div>
                    <span className="font-medium">描述:</span> {description}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* 操作按钮 */}
        <div className="mt-6 flex justify-end space-x-4">
          <button
            onClick={onCancel}
            className="px-6 py-2 bg-gray-200 text-gray-700 rounded hover:bg-gray-300 transition-colors"
            disabled={loading}
          >
            取消
          </button>
          <button
            onClick={handleSave}
            disabled={loading}
            className="px-6 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400 transition-colors"
          >
            {loading ? '创建中...' : '创建原子指标'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default AtomicMetricBuilder;
