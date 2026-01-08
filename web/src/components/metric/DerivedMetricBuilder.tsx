import React, { useState, useEffect } from 'react';
import { metricApi } from '../../api/metric';
import type { AtomicMetric, MetricDefinition } from '../../api/metric';
import { schemaApi } from '../../api/client';
import type { ObjectType, LinkType, Property } from '../../api/client';
import { useWorkspace } from '../../WorkspaceContext';

interface Props {
  onCancel: () => void;
  onSuccess: () => void;
  editMode?: boolean;
  initialData?: MetricDefinition | null;
}

const DerivedMetricBuilder: React.FC<Props> = ({ onCancel, onSuccess, editMode = false, initialData = null }) => {
  const { selectedWorkspaceId, selectedWorkspace } = useWorkspace();
  const [objectTypes, setObjectTypes] = useState<ObjectType[]>([]);
  const [atomicMetrics, setAtomicMetrics] = useState<AtomicMetric[]>([]);
  const [selectedAtomicMetric, setSelectedAtomicMetric] = useState<AtomicMetric | null>(null);
  const [createNewAtomic, setCreateNewAtomic] = useState(false);
  const [newAtomicMetric, setNewAtomicMetric] = useState<Partial<AtomicMetric>>({});
  
  const [businessScopeType, setBusinessScopeType] = useState<'single' | 'multi'>('single');
  const [selectedObjectType, setSelectedObjectType] = useState<string>('');
  const [linkTypes, setLinkTypes] = useState<LinkType[]>([]);
  const [selectedLinks, setSelectedLinks] = useState<Array<{ name: string; select: string[] }>>([]);
  
  const [timeDimension, setTimeDimension] = useState<string>('');
  const [timeGranularity, setTimeGranularity] = useState<'day' | 'week' | 'month' | 'quarter' | 'year'>('day');
  const [comparisonTypes, setComparisonTypes] = useState<Array<'YoY' | 'MoM' | 'WoW' | 'QoQ'>>([]);
  
  const [dimensions, setDimensions] = useState<string[]>([]);
  const [availableDimensions, setAvailableDimensions] = useState<string[]>([]);
  const [objectTypeProperties, setObjectTypeProperties] = useState<Property[]>([]);
  
  const [filterConditions, setFilterConditions] = useState<Array<{ field: string; operator: string; value: string }>>([]);
  
  const [metricName, setMetricName] = useState<string>('');
  const [displayName, setDisplayName] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [unit, setUnit] = useState<string>('');
  
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

  // 根据工作空间过滤对象类型
  const filteredObjectTypes = selectedWorkspace && selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0
    ? objectTypes.filter((ot) => selectedWorkspace.object_types!.includes(ot.name))
    : objectTypes;

  useEffect(() => {
    loadData();
  }, []);

  // 单独的useEffect处理编辑模式的数据回显
  useEffect(() => {
    // 如果是编辑模式且有初始数据，且还未初始化
    if (editMode && initialData && !isInitialized && atomicMetrics.length > 0 && objectTypes.length > 0) {
      console.log('===== 开始回显数据 =====');
      console.log('initialData:', initialData);
      
      setMetricName(initialData.name || '');
      setDisplayName(initialData.display_name || '');
      setDescription(initialData.description || '');
      setUnit(initialData.unit || '');
      
      // 回显时间粒度和比较类型
      if (initialData.time_granularity) setTimeGranularity(initialData.time_granularity);
      if (initialData.comparison_type) setComparisonTypes(initialData.comparison_type);
      
      // 回显业务范围
      if (initialData.business_scope) {
        const scope = initialData.business_scope;
        setBusinessScopeType(scope.type || 'single');
        
        if (scope.type === 'single' && scope.base_object_type) {
          setSelectedObjectType(scope.base_object_type);
        } else if (scope.type === 'multi' && scope.from) {
          setSelectedObjectType(scope.from);
          if (scope.links) {
            const links = scope.links.map(link => ({
              name: link.name,
              select: link.select || []
            }));
            setSelectedLinks(links);
          }
        }
      }
      
      // 回显原子指标
      if (initialData.atomic_metric_id) {
        const atomic = atomicMetrics.find(m => m.id === initialData.atomic_metric_id);
        if (atomic) {
          console.log('找到原子指标:', atomic);
          setSelectedAtomicMetric(atomic);
        }
      }
      
      // 标记为已初始化，防止重复执行
      setIsInitialized(true);
      
      // 延迟回显依赖于objectType的字段（时间维度、维度、过滤条件）
      setTimeout(() => {
        console.log('===== 延迟回显依赖字段 =====');
        
        // 回显时间维度
        if (initialData.time_dimension) {
          console.log('回显时间维度:', initialData.time_dimension);
          setTimeDimension(initialData.time_dimension);
        }
        
        // 回显维度
        if (initialData.dimensions) {
          console.log('回显维度:', initialData.dimensions);
          setDimensions(initialData.dimensions);
        }
        
        // 回显过滤条件
        if (initialData.filter_conditions) {
          const conditions = Object.entries(initialData.filter_conditions).map(([field, value]) => ({
            field,
            operator: '=',
            value: String(value)
          }));
          console.log('回显过滤条件:', conditions);
          setFilterConditions(conditions);
        }
      }, 500); // 等待loadObjectTypeInfo完成
    }
  }, [editMode, initialData, isInitialized, atomicMetrics, objectTypes]);

  // 当工作空间变化时，如果当前选择的对象类型不在新工作空间中，则清空选择
  useEffect(() => {
    if (selectedObjectType && selectedWorkspace && selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0) {
      if (!selectedWorkspace.object_types.includes(selectedObjectType)) {
        setSelectedObjectType('');
        setAvailableDimensions([]);
        setObjectTypeProperties([]);
        setTimeDimension('');
        setDimensions([]);
      }
    }
  }, [selectedWorkspace, selectedObjectType]);

  // 当选择原子指标且业务范围为单一对象时，自动同步对象类型
  useEffect(() => {
    console.log('===== useEffect 触发 =====');
    console.log('businessScopeType:', businessScopeType);
    console.log('selectedAtomicMetric:', selectedAtomicMetric);
    
    if (businessScopeType === 'single') {
      if (selectedAtomicMetric) {
        // 兼容两种命名方式：business_process (下划线) 和 businessProcess (驼峰)
        const businessProcess = (selectedAtomicMetric as any).business_process || (selectedAtomicMetric as any).businessProcess;
        console.log('businessProcess (兼容两种命名):', businessProcess);
        
        if (businessProcess) {
          console.log('自动同步对象类型:', businessProcess, '当前对象类型:', selectedObjectType);
          // 直接设置对象类型，会触发下面的 useEffect 来加载信息
          setSelectedObjectType(businessProcess);
        } else {
          console.log('未找到 businessProcess 字段');
        }
      }
    }
  }, [selectedAtomicMetric, businessScopeType]);

  useEffect(() => {
    console.log('selectedObjectType 变化:', selectedObjectType);
    if (selectedObjectType) {
      console.log('开始加载对象类型信息...');
      loadObjectTypeInfo(selectedObjectType);
      // 只在非编辑模式或已初始化后才重置这些字段
      if (!editMode || isInitialized) {
        // 重置时间维度字段，因为对象类型变了
        setTimeDimension('');
        // 重置维度字段，因为对象类型变了
        setDimensions([]);
      }
    } else {
      // 如果没有选择对象类型，清空属性信息
      console.log('清空对象类型信息');
      setObjectTypeProperties([]);
      setAvailableDimensions([]);
    }
  }, [selectedObjectType, editMode, isInitialized]);

  const loadData = async () => {
    try {
      const [objectTypesData, atomicMetricsData] = await Promise.all([
        schemaApi.getObjectTypes(),
        metricApi.listAtomicMetrics(),
      ]);
      setObjectTypes(objectTypesData);
      setAtomicMetrics(atomicMetricsData);
    } catch (error) {
      console.error('Failed to load data:', error);
    }
  };

  const loadObjectTypeInfo = async (objectTypeName: string) => {
    try {
      console.log('=== 开始加载对象类型信息 ===');
      console.log('对象类型名称:', objectTypeName);
      const objectType = await schemaApi.getObjectType(objectTypeName);
      const properties = objectType.properties || [];
      console.log('加载到 ' + properties.length + ' 个属性:', properties.map(p => p.name));
      setAvailableDimensions(properties.map(p => p.name));
      setObjectTypeProperties(properties);
      console.log('已更新 availableDimensions 和 objectTypeProperties');
      
      // 加载关联类型
      const outgoingLinks = await schemaApi.getOutgoingLinks(objectTypeName);
      setLinkTypes(outgoingLinks);
      console.log('=== 对象类型信息加载完成 ===');
    } catch (error) {
      console.error('Failed to load object type info:', error);
    }
  };

  const generateMetricName = () => {
    if (!selectedAtomicMetric) return '';
    
    const timePrefix = {
      day: '日',
      week: '周',
      month: '月',
      quarter: '季度',
      year: '年',
    }[timeGranularity] || '';
    
    return timePrefix + selectedAtomicMetric.name;
  };

  useEffect(() => {
    if (selectedAtomicMetric && timeGranularity) {
      const name = generateMetricName();
      setMetricName(name);
      setDisplayName(name);
    }
  }, [selectedAtomicMetric, timeGranularity]);

  const handleCreateAtomicMetric = async () => {
    try {
      // 添加工作空间ID
      const requestData: Record<string, any> = {
        ...newAtomicMetric,
        status: 'active',
      };
      if (selectedWorkspaceId) {
        requestData.workspace_ids = [selectedWorkspaceId];
      }
      const result = await metricApi.createAtomicMetric(requestData);
      const created = await metricApi.getAtomicMetric(result.id);
      setAtomicMetrics([...atomicMetrics, created]);
      setSelectedAtomicMetric(created);
      setCreateNewAtomic(false);
    } catch (error) {
      console.error('Failed to create atomic metric:', error);
      alert('创建原子指标失败: ' + (error as Error).message);
    }
  };

  const handleSave = async () => {
    try {
      const businessScope: any = {
        type: businessScopeType,
      };
      
      if (businessScopeType === 'single') {
        businessScope.base_object_type = selectedObjectType;
      } else {
        businessScope.from = selectedObjectType;
        if (selectedLinks.length > 0) {
          businessScope.links = selectedLinks;
        }
      }

      const metricDefinition: Partial<MetricDefinition> = {
        name: metricName,
        display_name: displayName,
        description,
        metric_type: 'derived',
        atomic_metric_id: selectedAtomicMetric?.id,
        business_scope: businessScope,
        time_dimension: timeDimension,
        time_granularity: timeGranularity,
        dimensions: dimensions.length > 0 ? dimensions : undefined,
        filter_conditions: filterConditions.length > 0 
          ? Object.fromEntries(filterConditions.map(f => [f.field, f.value]))
          : undefined,
        comparison_type: comparisonTypes.length > 0 ? comparisonTypes : undefined,
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
        alert('派生指标更新成功！');
      } else {
        // 创建模式：调用创建API
        await metricApi.createMetricDefinition(requestData);
        alert('派生指标创建成功！');
      }
      onSuccess();
    } catch (error) {
      console.error('Failed to save metric:', error);
      alert((editMode ? '更新' : '创建') + '派生指标失败: ' + (error as Error).message);
    }
  };

  // 验证派生指标
  const handleValidate = async () => {
    // 验证必填字段
    if (!selectedAtomicMetric) {
      alert('请选择原子指标');
      return;
    }
    if (!selectedObjectType) {
      alert('请选择对象类型');
      return;
    }
    if (!timeDimension) {
      alert('请选择时间维度');
      return;
    }

    setValidating(true);
    setValidationError(null);
    setValidationResult(null);
    try {
      // 构建验证数据
      const businessScope: any = {
        type: businessScopeType,
      };
      
      if (businessScopeType === 'single') {
        businessScope.base_object_type = selectedObjectType;
      } else {
        businessScope.from = selectedObjectType;
        if (selectedLinks.length > 0) {
          businessScope.links = selectedLinks;
        }
      }

      const metricDefinition: Partial<MetricDefinition> = {
        name: metricName || '_validate_',
        display_name: displayName,
        description,
        metric_type: 'derived',
        atomic_metric_id: selectedAtomicMetric.id,
        business_scope: businessScope,
        time_dimension: timeDimension,
        time_granularity: timeGranularity,
        dimensions: dimensions.length > 0 ? dimensions : undefined,
        filter_conditions: filterConditions.length > 0 
          ? Object.fromEntries(filterConditions.map(f => [f.field, f.value]))
          : undefined,
        comparison_type: comparisonTypes.length > 0 ? comparisonTypes : undefined,
        unit: unit || undefined,
        status: 'active',
      };

      // 调用验证 API
      const result = await metricApi.validateMetricDefinition(metricDefinition);
      
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

  const addFilter = () => {
    setFilterConditions([...filterConditions, { field: '', operator: '=', value: '' }]);
  };

  const updateFilter = (index: number, field: string, value: any) => {
    const newConditions = [...filterConditions];
    newConditions[index] = { ...newConditions[index], [field]: value };
    setFilterConditions(newConditions);
  };

  const removeFilter = (index: number) => {
    setFilterConditions(filterConditions.filter((_, i) => i !== index));
  };

  // 获取时间相关属性
  const timeRelatedProperties = availableDimensions.filter(prop => {
    const lowerProp = prop.toLowerCase();
    return lowerProp.includes('time') || 
           lowerProp.includes('date') || 
           lowerProp.includes('timestamp') ||
           lowerProp.includes('create') ||
           lowerProp.includes('update') ||
           lowerProp.includes('modified');
  });

  // 获取过滤条件可用的字段列表（时间维度 + 维度字段）
  const getFilterFieldOptions = () => {
    const fields: string[] = [];
    
    // 添加时间维度字段（如果已选择）
    if (timeDimension) {
      fields.push(timeDimension);
    }
    
    // 添加已选择的维度字段
    dimensions.forEach(dim => {
      if (!fields.includes(dim)) {
        fields.push(dim);
      }
    });
    
    // 如果还没有字段，则提供所有可用维度作为选项
    if (fields.length === 0 && availableDimensions.length > 0) {
      return availableDimensions;
    }
    
    return fields;
  };

  const filterFieldOptions = getFilterFieldOptions();

  // 判断字段是否是时间类型
  const isTimeTypeField = (fieldName: string): boolean => {
    const property = objectTypeProperties.find(p => p.name === fieldName);
    if (!property) return false;
    
    const dataType = property.data_type?.toLowerCase() || '';
    return dataType.includes('date') || 
           dataType.includes('time') || 
           dataType.includes('timestamp') ||
           dataType === 'datetime';
  };

  // 格式化日期时间值用于输入框
  const formatDateTimeForInput = (value: string): string => {
    if (!value) return '';
    // 如果已经是正确的格式（YYYY-MM-DDTHH:mm），直接返回
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) {
      return value;
    }
    // 如果是日期格式（YYYY-MM-DD），转换为 datetime-local 格式
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
      return value + 'T00:00';
    }
    // 尝试解析其他格式
    try {
      const date = new Date(value);
      if (!isNaN(date.getTime())) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
      }
    } catch (e) {
      // 解析失败，返回原值
    }
    return value;
  };

  return (
    <div className="max-w-7xl mx-auto">
      <div className="bg-white rounded-lg shadow-lg p-6">
        <div className="flex justify-between items-center mb-4">
          <div>
            <h2 className="text-2xl font-bold">{editMode ? '编辑派生指标' : '派生指标构建器'}</h2>
            <p className="text-gray-600 text-sm mt-1">基于原子指标添加时间、维度等约束条件</p>
          </div>
          <div className="flex space-x-3">
            <button
              onClick={handleValidate}
              disabled={validating || !selectedAtomicMetric || !selectedObjectType}
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
              disabled={validating || !selectedAtomicMetric || !selectedObjectType}
              className="px-4 py-2 bg-green-500 text-white rounded hover:bg-green-600 disabled:bg-gray-300 disabled:cursor-not-allowed"
            >
              {editMode ? '保存修改' : '保存指标'}
            </button>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-4">
          {/* 第一列：基本信息 + 原子指标 */}
          <div className="space-y-3">
            <div className="text-sm font-semibold text-gray-700 border-b pb-1">基本信息</div>
            
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">指标名称</label>
              <input
                type="text"
                value={metricName}
                onChange={(e) => setMetricName(e.target.value)}
                className="w-full p-2 border rounded text-sm"
                placeholder="自动生成或手动输入"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">显示名称</label>
              <input
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                className="w-full p-2 border rounded text-sm"
                placeholder="界面显示"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">描述</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="w-full p-2 border rounded text-sm"
                rows={2}
                placeholder="指标用途和含义"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">单位</label>
              <input
                type="text"
                value={unit}
                onChange={(e) => setUnit(e.target.value)}
                className="w-full p-2 border rounded text-sm"
                placeholder="例如: 元、次、个、%"
              />
            </div>

            <div className="text-sm font-semibold text-gray-700 border-b pb-1 pt-2">原子指标（度量）</div>
            
            <div className="space-y-2">
              <div className="flex items-center space-x-2 text-xs">
                <label className="flex items-center">
                  <input
                    type="radio"
                    checked={!createNewAtomic}
                    onChange={() => setCreateNewAtomic(false)}
                    className="mr-1"
                  />
                  选择已有
                </label>
                <label className="flex items-center">
                  <input
                    type="radio"
                    checked={createNewAtomic}
                    onChange={() => setCreateNewAtomic(true)}
                    className="mr-1"
                  />
                  创建新的
                </label>
              </div>

              {!createNewAtomic ? (
                <div>
                  <select
                    value={selectedAtomicMetric?.id || ''}
                    onChange={(e) => {
                      const metric = atomicMetrics.find(m => m.id === e.target.value);
                      console.log('===== 选择原子指标 =====');
                      console.log('选中的原子指标:', metric);
                      console.log('business_process:', metric?.business_process);
                      setSelectedAtomicMetric(metric || null);
                    }}
                    className="w-full p-2 border rounded text-xs"
                  >
                    <option value="">请选择原子指标</option>
                    {atomicMetrics.map(metric => (
                      <option key={metric.id} value={metric.id}>
                        {metric.display_name || metric.name}
                      </option>
                    ))}
                  </select>
                  {selectedAtomicMetric && (
                    <div className="mt-1 p-2 bg-gray-50 rounded text-xs">
                      <p><span className="font-medium">名称:</span> {(selectedAtomicMetric as any).display_name || (selectedAtomicMetric as any).displayName || selectedAtomicMetric.name}</p>
                      <p><span className="font-medium">业务过程:</span> {(selectedAtomicMetric as any).business_process || (selectedAtomicMetric as any).businessProcess}</p>
                      <p><span className="font-medium">聚合函数:</span> {(selectedAtomicMetric as any).aggregation_function || (selectedAtomicMetric as any).aggregationFunction}</p>
                    </div>
                  )}
                </div>
              ) : (
                <div className="space-y-2">
                  <select
                    value={newAtomicMetric.business_process || ''}
                    onChange={(e) => setNewAtomicMetric({ ...newAtomicMetric, business_process: e.target.value })}
                    className="w-full p-2 border rounded text-xs"
                  >
                    <option value="">对象类型</option>
                    {filteredObjectTypes.map(ot => (
                      <option key={ot.name} value={ot.name}>
                        {ot.display_name || ot.name}
                      </option>
                    ))}
                  </select>
                  <select
                    value={newAtomicMetric.aggregation_function || ''}
                    onChange={(e) => setNewAtomicMetric({ ...newAtomicMetric, aggregation_function: e.target.value })}
                    className="w-full p-2 border rounded text-xs"
                  >
                    <option value="">聚合函数</option>
                    <option value="SUM">SUM</option>
                    <option value="AVG">AVG</option>
                    <option value="COUNT">COUNT</option>
                    <option value="MAX">MAX</option>
                    <option value="MIN">MIN</option>
                    <option value="DISTINCT_COUNT">DISTINCT_COUNT</option>
                  </select>
                  <input
                    type="text"
                    value={newAtomicMetric.aggregation_field || ''}
                    onChange={(e) => setNewAtomicMetric({ ...newAtomicMetric, aggregation_field: e.target.value })}
                    className="w-full p-2 border rounded text-xs"
                    placeholder="度量字段"
                  />
                  <input
                    type="text"
                    value={newAtomicMetric.name || ''}
                    onChange={(e) => setNewAtomicMetric({ ...newAtomicMetric, name: e.target.value })}
                    className="w-full p-2 border rounded text-xs"
                    placeholder="原子指标名称"
                  />
                  <button
                    onClick={handleCreateAtomicMetric}
                    className="w-full px-2 py-1.5 bg-blue-500 text-white rounded hover:bg-blue-600 text-xs"
                  >
                    创建原子指标
                  </button>
                </div>
              )}
            </div>
          </div>

          {/* 第二列：业务范围 + 时间维度 */}
          <div className="space-y-3">
            <div className="text-sm font-semibold text-gray-700 border-b pb-1">业务范围</div>
            
            <div className="flex items-center space-x-2 text-xs">
              <label className="flex items-center">
                <input
                  type="radio"
                  name="businessScopeType"
                  checked={businessScopeType === 'single'}
                  onChange={() => setBusinessScopeType('single')}
                  className="mr-1"
                />
                单一对象
              </label>
              <label className="flex items-center">
                <input
                  type="radio"
                  name="businessScopeType"
                  checked={businessScopeType === 'multi'}
                  onChange={() => setBusinessScopeType('multi')}
                  className="mr-1"
                />
                多对象（关联）
              </label>
            </div>

            {businessScopeType === 'single' ? (
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">
                  对象类型 <span className="text-red-500">*</span>
                  {selectedAtomicMetric && (
                    <span className="ml-1 text-blue-600 font-normal">(已从原子指标同步)</span>
                  )}
                </label>
                <select
                  value={selectedObjectType}
                  onChange={(e) => setSelectedObjectType(e.target.value)}
                  className="w-full p-2 border rounded text-xs"
                  disabled={selectedAtomicMetric !== null}
                  key={`object-type-${selectedObjectType}-${selectedAtomicMetric?.id || 'none'}`}
                >
                  <option value="">请选择对象类型</option>
                  {filteredObjectTypes.map(ot => (
                    <option key={ot.name} value={ot.name}>
                      {ot.display_name || ot.name}
                    </option>
                  ))}
                </select>
                {selectedWorkspace && filteredObjectTypes.length === 0 && (
                  <p className="text-yellow-600 text-xs mt-0.5">工作空间未添加对象类型</p>
                )}
                {selectedAtomicMetric && selectedObjectType && (
                  <p className="text-blue-600 text-xs mt-0.5">
                    ✓ 已自动从原子指标"{(selectedAtomicMetric as any).display_name || (selectedAtomicMetric as any).displayName || selectedAtomicMetric.name}"同步业务过程
                  </p>
                )}
              </div>
            ) : (
              <div className="space-y-2">
                <label className="block text-xs font-medium text-gray-700 mb-1">起始对象类型</label>
                <select
                  value={selectedObjectType}
                  onChange={(e) => setSelectedObjectType(e.target.value)}
                  className="w-full p-2 border rounded text-xs"
                >
                  <option value="">请选择起始对象类型</option>
                  {filteredObjectTypes.map(ot => (
                    <option key={ot.name} value={ot.name}>
                      {ot.display_name || ot.name}
                    </option>
                  ))}
                </select>
                
                {selectedObjectType && linkTypes.length > 0 && (
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">选择关联路径</label>
                    <div className="max-h-32 overflow-y-auto border rounded bg-white p-2 space-y-1">
                      {linkTypes.map((link, index) => (
                        <label key={link.name} className="flex items-center text-xs">
                          <input
                            type="checkbox"
                            id={`link-${index}`}
                            checked={selectedLinks.some(l => l.name === link.name)}
                            onChange={(e) => {
                              if (e.target.checked) {
                                setSelectedLinks([...selectedLinks, { name: link.name, select: [] }]);
                              } else {
                                setSelectedLinks(selectedLinks.filter(l => l.name !== link.name));
                              }
                            }}
                            className="mr-1"
                          />
                          {link.display_name || link.name}
                        </label>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            <div className="text-sm font-semibold text-gray-700 border-b pb-1 pt-2">时间维度</div>
            
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">时间字段</label>
              <select
                value={timeDimension}
                onChange={(e) => setTimeDimension(e.target.value)}
                className="w-full p-2 border rounded text-xs"
              >
                <option value="">请选择时间字段</option>
                {(timeRelatedProperties.length > 0 ? timeRelatedProperties : availableDimensions).map(prop => (
                  <option key={prop} value={prop}>
                    {prop}
                  </option>
                ))}
              </select>
            </div>
            
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">时间粒度</label>
              <select
                value={timeGranularity}
                onChange={(e) => setTimeGranularity(e.target.value as any)}
                className="w-full p-2 border rounded text-xs"
              >
                <option value="day">日</option>
                <option value="week">周</option>
                <option value="month">月</option>
                <option value="quarter">季度</option>
                <option value="year">年</option>
              </select>
            </div>
            
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">同比/环比</label>
              <div className="grid grid-cols-2 gap-1 text-xs">
                {(['YoY', 'MoM', 'WoW', 'QoQ'] as const).map(type => (
                  <label key={type} className="flex items-center">
                    <input
                      type="checkbox"
                      checked={comparisonTypes.includes(type)}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setComparisonTypes([...comparisonTypes, type]);
                        } else {
                          setComparisonTypes(comparisonTypes.filter(t => t !== type));
                        }
                      }}
                      className="mr-1"
                    />
                    {type === 'YoY' && '同比(年)'}
                    {type === 'MoM' && '环比(月)'}
                    {type === 'WoW' && '环比(周)'}
                    {type === 'QoQ' && '环比(季)'}
                  </label>
                ))}
              </div>
            </div>
          </div>

          {/* 第三列：维度 + 过滤条件 */}
          <div className="space-y-3">
            <div className="text-sm font-semibold text-gray-700 border-b pb-1">维度</div>
            
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">
                选择维度字段
                {availableDimensions.length > 0 && (
                  <span className="ml-1 text-gray-500">(共 {availableDimensions.length} 个可用)</span>
                )}
              </label>
              <select
                multiple
                value={dimensions}
                onChange={(e) => {
                  const values = Array.from(e.target.selectedOptions, option => option.value);
                  setDimensions(values);
                }}
                className="w-full p-2 border rounded text-xs h-28"
              >
                {availableDimensions.length === 0 ? (
                  <option disabled>请先选择对象类型</option>
                ) : (
                  availableDimensions.map(dim => (
                    <option key={dim} value={dim}>
                      {dim}
                    </option>
                  ))
                )}
              </select>
              <p className="text-xs text-gray-500 mt-0.5">Ctrl/Cmd+点击多选</p>
              
              {dimensions.length > 0 && (
                <div className="mt-1 flex flex-wrap gap-1">
                  {dimensions.map(dim => (
                    <span key={dim} className="px-1.5 py-0.5 bg-blue-100 text-blue-800 rounded text-xs">
                      {dim}
                    </span>
                  ))}
                </div>
              )}
            </div>

            <div className="text-sm font-semibold text-gray-700 border-b pb-1 pt-2">过滤条件</div>
            
            <div>
              <div className="flex justify-between items-center mb-1">
                <label className="block text-xs font-medium text-gray-700">过滤条件</label>
                <button
                  type="button"
                  onClick={addFilter}
                  className="px-2 py-0.5 bg-blue-500 text-white text-xs rounded hover:bg-blue-600"
                >
                  +
                </button>
              </div>
              
              {filterConditions.length === 0 ? (
                <p className="text-xs text-gray-500">暂无过滤条件</p>
              ) : (
                <div className="space-y-2 max-h-64 overflow-y-auto">
                  {filterConditions.map((condition, index) => (
                    <div key={index} className="flex items-center space-x-1">
                      <select
                        value={condition.field}
                        onChange={(e) => updateFilter(index, 'field', e.target.value)}
                        className="flex-1 p-1 border rounded text-xs"
                      >
                        <option value="">字段</option>
                        {filterFieldOptions.map(field => (
                          <option key={field} value={field}>
                            {field}
                          </option>
                        ))}
                      </select>
                      <select
                        value={condition.operator}
                        onChange={(e) => updateFilter(index, 'operator', e.target.value)}
                        className="p-1 border rounded text-xs"
                      >
                        <option value="=">=</option>
                        <option value="!=">≠</option>
                        <option value=">">&gt;</option>
                        <option value="<">&lt;</option>
                        <option value=">=">≥</option>
                        <option value="<=">≤</option>
                      </select>
                      {isTimeTypeField(condition.field) ? (
                        <input
                          type="datetime-local"
                          value={formatDateTimeForInput(condition.value)}
                          onChange={(e) => updateFilter(index, 'value', e.target.value)}
                          className="flex-1 p-1 border rounded text-xs"
                        />
                      ) : (
                        <input
                          type="text"
                          value={condition.value}
                          onChange={(e) => updateFilter(index, 'value', e.target.value)}
                          className="flex-1 p-1 border rounded text-xs"
                          placeholder="值"
                        />
                      )}
                      <button
                        type="button"
                        onClick={() => removeFilter(index)}
                        className="px-1.5 py-0.5 bg-red-500 text-white rounded hover:bg-red-600 text-xs"
                      >
                        ×
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="bg-blue-50 p-2 rounded border border-blue-200">
              <p className="text-xs text-blue-800 font-medium mb-1">提示</p>
              <ul className="text-xs text-blue-700 space-y-0.5 list-disc list-inside">
                <li>必须选择原子指标</li>
                <li>必须选择对象类型</li>
                <li>时间粒度影响指标名称</li>
                <li>维度用于分组统计</li>
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

export default DerivedMetricBuilder;
