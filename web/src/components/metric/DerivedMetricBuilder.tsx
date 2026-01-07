import React, { useState, useEffect } from 'react';
import { metricApi } from '../../api/metric';
import type { AtomicMetric, MetricDefinition } from '../../api/metric';
import { schemaApi } from '../../api/client';
import type { ObjectType, LinkType, Property } from '../../api/client';
import { useWorkspace } from '../../WorkspaceContext';

interface Props {
  onCancel: () => void;
  onSuccess: () => void;
}

const DerivedMetricBuilder: React.FC<Props> = ({ onCancel, onSuccess }) => {
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

  // 根据工作空间过滤对象类型
  const filteredObjectTypes = selectedWorkspace && selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0
    ? objectTypes.filter((ot) => selectedWorkspace.object_types!.includes(ot.name))
    : objectTypes;

  useEffect(() => {
    loadData();
  }, []);

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

  useEffect(() => {
    if (selectedObjectType) {
      loadObjectTypeInfo(selectedObjectType);
      // 重置时间维度字段，因为对象类型变了
      setTimeDimension('');
      // 重置维度字段，因为对象类型变了
      setDimensions([]);
    } else {
      // 如果没有选择对象类型，清空属性信息
      setObjectTypeProperties([]);
    }
  }, [selectedObjectType]);

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
      const objectType = await schemaApi.getObjectType(objectTypeName);
      const properties = objectType.properties || [];
      setAvailableDimensions(properties.map(p => p.name));
      setObjectTypeProperties(properties);
      
      // 加载关联类型
      const outgoingLinks = await schemaApi.getOutgoingLinks(objectTypeName);
      setLinkTypes(outgoingLinks);
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
        status: 'active',
      };

      // 添加工作空间ID
      const requestData: Record<string, any> = { ...metricDefinition };
      if (selectedWorkspaceId) {
        requestData.workspace_ids = [selectedWorkspaceId];
      }

      await metricApi.createMetricDefinition(requestData);
      alert('指标创建成功！');
      onSuccess();
    } catch (error) {
      console.error('Failed to create metric:', error);
      alert('创建指标失败: ' + (error as Error).message);
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
    <div className="max-w-6xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-bold">派生指标构建器</h2>
        <button
          onClick={onCancel}
          className="px-4 py-2 bg-gray-200 text-gray-700 rounded hover:bg-gray-300"
        >
          取消
        </button>
      </div>

      <div className="space-y-6">
        {/* 栏目1: 基本信息 */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-lg font-semibold mb-4 border-b pb-2">基本信息</h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block mb-1 text-sm font-medium">指标名称</label>
              <input
                type="text"
                value={metricName}
                onChange={(e) => setMetricName(e.target.value)}
                className="w-full p-2 border rounded"
                placeholder="自动生成或手动输入"
              />
            </div>
            <div>
              <label className="block mb-1 text-sm font-medium">显示名称</label>
              <input
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                className="w-full p-2 border rounded"
                placeholder="用于界面显示"
              />
            </div>
            <div className="col-span-2">
              <label className="block mb-1 text-sm font-medium">描述</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="w-full p-2 border rounded"
                rows={2}
                placeholder="描述该派生指标的用途和含义"
              />
            </div>
          </div>
        </div>

        {/* 栏目2: 原子指标选择 */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-lg font-semibold mb-4 border-b pb-2">原子指标（度量）</h3>
          
          <div className="mb-4">
            <label className="flex items-center mr-4">
              <input
                type="radio"
                checked={!createNewAtomic}
                onChange={() => setCreateNewAtomic(false)}
                className="mr-2"
              />
              选择已有原子指标
            </label>
            <label className="flex items-center">
              <input
                type="radio"
                checked={createNewAtomic}
                onChange={() => setCreateNewAtomic(true)}
                className="mr-2"
              />
              创建新原子指标
            </label>
          </div>

          {!createNewAtomic ? (
            <div>
              <select
                value={selectedAtomicMetric?.id || ''}
                onChange={(e) => {
                  const metric = atomicMetrics.find(m => m.id === e.target.value);
                  setSelectedAtomicMetric(metric || null);
                }}
                className="w-full p-2 border rounded"
              >
                <option value="">请选择原子指标</option>
                {atomicMetrics.map(metric => (
                  <option key={metric.id} value={metric.id}>
                    {metric.display_name || metric.name} - {metric.description || '无描述'}
                  </option>
                ))}
              </select>
              {selectedAtomicMetric && (
                <div className="mt-3 p-3 bg-gray-50 rounded">
                  <p className="font-medium">已选择原子指标:</p>
                  <p className="text-sm">名称: {selectedAtomicMetric.display_name || selectedAtomicMetric.name}</p>
                  <p className="text-sm">业务过程: {selectedAtomicMetric.business_process}</p>
                  <p className="text-sm">聚合函数: {selectedAtomicMetric.aggregation_function}</p>
                  {selectedAtomicMetric.aggregation_field && (
                    <p className="text-sm">聚合字段: {selectedAtomicMetric.aggregation_field}</p>
                  )}
                </div>
              )}
            </div>
          ) : (
            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block mb-1 text-sm font-medium">业务过程（对象类型）</label>
                <select
                  value={newAtomicMetric.business_process || ''}
                  onChange={(e) => setNewAtomicMetric({ ...newAtomicMetric, business_process: e.target.value })}
                  className="w-full p-2 border rounded"
                >
                  <option value="">请选择对象类型</option>
                  {filteredObjectTypes.map(ot => (
                    <option key={ot.name} value={ot.name}>
                      {ot.display_name || ot.name}
                    </option>
                  ))}
                </select>
                {selectedWorkspace && filteredObjectTypes.length === 0 && (
                  <p className="text-yellow-600 text-xs mt-1">
                    当前工作空间未添加任何对象类型
                  </p>
                )}
              </div>
              <div>
                <label className="block mb-1 text-sm font-medium">统计方式</label>
                <select
                  value={newAtomicMetric.aggregation_function || ''}
                  onChange={(e) => setNewAtomicMetric({ ...newAtomicMetric, aggregation_function: e.target.value })}
                  className="w-full p-2 border rounded"
                >
                  <option value="">请选择聚合函数</option>
                  <option value="SUM">SUM（求和）</option>
                  <option value="AVG">AVG（平均值）</option>
                  <option value="COUNT">COUNT（计数）</option>
                  <option value="MAX">MAX（最大值）</option>
                  <option value="MIN">MIN（最小值）</option>
                  <option value="DISTINCT_COUNT">DISTINCT_COUNT（去重计数）</option>
                </select>
              </div>
              <div>
                <label className="block mb-1 text-sm font-medium">度量字段</label>
                <input
                  type="text"
                  value={newAtomicMetric.aggregation_field || ''}
                  onChange={(e) => setNewAtomicMetric({ ...newAtomicMetric, aggregation_field: e.target.value })}
                  className="w-full p-2 border rounded"
                  placeholder="例如: trans_amount"
                />
              </div>
              <div className="col-span-3">
                <label className="block mb-1 text-sm font-medium">原子指标名称</label>
                <input
                  type="text"
                  value={newAtomicMetric.name || ''}
                  onChange={(e) => setNewAtomicMetric({ ...newAtomicMetric, name: e.target.value })}
                  className="w-full p-2 border rounded"
                  placeholder="例如: 交易金额"
                />
              </div>
              <div className="col-span-3">
                <button
                  onClick={handleCreateAtomicMetric}
                  className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
                >
                  创建原子指标
                </button>
              </div>
            </div>
          )}
        </div>

        {/* 栏目3: 业务范围 */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-lg font-semibold mb-4 border-b pb-2">业务范围</h3>
          
          <div className="mb-4">
            <label className="flex items-center mr-4">
              <input
                type="radio"
                name="businessScopeType"
                checked={businessScopeType === 'single'}
                onChange={() => setBusinessScopeType('single')}
                className="mr-2"
              />
              单一对象类型
            </label>
            <label className="flex items-center">
              <input
                type="radio"
                name="businessScopeType"
                checked={businessScopeType === 'multi'}
                onChange={() => setBusinessScopeType('multi')}
                className="mr-2"
              />
              多对象类型（关联路径）
            </label>
          </div>

          {businessScopeType === 'single' ? (
            <div>
              <label className="block mb-2 text-sm font-medium">选择对象类型</label>
              <select
                value={selectedObjectType}
                onChange={(e) => setSelectedObjectType(e.target.value)}
                className="w-full p-2 border rounded"
              >
                <option value="">请选择对象类型</option>
                {filteredObjectTypes.map(ot => (
                  <option key={ot.name} value={ot.name}>
                    {ot.display_name || ot.name} - {ot.description || '无描述'}
                  </option>
                ))}
              </select>
              {selectedWorkspace && filteredObjectTypes.length === 0 && (
                <p className="text-yellow-600 text-xs mt-1">
                  当前工作空间未添加任何对象类型，请先在工作空间管理中添加对象类型
                </p>
              )}
              {selectedObjectType && (
                <p className="text-sm text-gray-500 mt-1">
                  已选择: {objectTypes.find(ot => ot.name === selectedObjectType)?.display_name || selectedObjectType}
                </p>
              )}
            </div>
          ) : (
            <div className="space-y-4">
              <div>
                <label className="block mb-2 text-sm font-medium">起始对象类型</label>
                <select
                  value={selectedObjectType}
                  onChange={(e) => setSelectedObjectType(e.target.value)}
                  className="w-full p-2 border rounded"
                >
                  <option value="">请选择起始对象类型</option>
                  {filteredObjectTypes.map(ot => (
                    <option key={ot.name} value={ot.name}>
                      {ot.display_name || ot.name} - {ot.description || '无描述'}
                    </option>
                  ))}
                </select>
                {selectedWorkspace && filteredObjectTypes.length === 0 && (
                  <p className="text-yellow-600 text-xs mt-1">
                    当前工作空间未添加任何对象类型
                  </p>
                )}
              </div>
              
              {selectedObjectType && (
                <div>
                  <label className="block mb-2 text-sm font-medium">选择关联路径</label>
                  <div className="space-y-2 max-h-40 overflow-y-auto">
                    {linkTypes.map((link, index) => (
                      <div key={link.name} className="flex items-center space-x-2">
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
                          className="h-4 w-4"
                        />
                        <label htmlFor={`link-${index}`} className="flex-1">
                          <span className="font-medium">{link.display_name || link.name}</span>
                          <span className="text-sm text-gray-500 ml-2">({link.name})</span>
                          {link.description && (
                            <p className="text-xs text-gray-500">{link.description}</p>
                          )}
                        </label>
                      </div>
                    ))}
                    {linkTypes.length === 0 && (
                      <p className="text-sm text-gray-500">当前对象类型没有可用的关联关系</p>
                    )}
                  </div>
                  
                  {selectedLinks.length > 0 && (
                    <div className="mt-3 p-3 bg-gray-50 rounded">
                      <h4 className="font-medium mb-2">已选择的关联路径</h4>
                      <ul className="list-disc pl-5 space-y-1">
                        {selectedLinks.map((link, index) => (
                          <li key={index}>
                            {linkTypes.find(l => l.name === link.name)?.display_name || link.name}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        {/* 栏目4: 时间维度 */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-lg font-semibold mb-4 border-b pb-2">时间维度</h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block mb-2 text-sm font-medium">时间维度字段</label>
              <select
                value={timeDimension}
                onChange={(e) => setTimeDimension(e.target.value)}
                className="w-full p-2 border rounded"
              >
                <option value="">请选择时间维度字段</option>
                {timeRelatedProperties.length > 0 ? (
                  timeRelatedProperties.map(prop => (
                    <option key={prop} value={prop}>
                      {prop}
                    </option>
                  ))
                ) : (
                  availableDimensions.map(prop => (
                    <option key={prop} value={prop}>
                      {prop}
                    </option>
                  ))
                )}
              </select>
              {timeRelatedProperties.length === 0 && availableDimensions.length > 0 && (
                <p className="text-sm text-yellow-600 mt-1">注意：未检测到明显的时间相关字段</p>
              )}
            </div>
            
            <div>
              <label className="block mb-2 text-sm font-medium">时间粒度</label>
              <select
                value={timeGranularity}
                onChange={(e) => setTimeGranularity(e.target.value as any)}
                className="w-full p-2 border rounded"
              >
                <option value="day">日</option>
                <option value="week">周</option>
                <option value="month">月</option>
                <option value="quarter">季度</option>
                <option value="year">年</option>
              </select>
            </div>
            
            <div className="col-span-2">
              <label className="block mb-2 text-sm font-medium">同比/环比类型</label>
              <div className="flex flex-wrap gap-2">
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
                      className="mr-2 h-4 w-4"
                    />
                    <span>
                      {type === 'YoY' && '同比（年）'}
                      {type === 'MoM' && '环比（月）'}
                      {type === 'WoW' && '环比（周）'}
                      {type === 'QoQ' && '环比（季度）'}
                    </span>
                  </label>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* 栏目5: 维度 */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-lg font-semibold mb-4 border-b pb-2">维度</h3>
          <div>
            <label className="block mb-2 text-sm font-medium">选择维度字段</label>
            <select
              multiple
              value={dimensions}
              onChange={(e) => {
                const values = Array.from(e.target.selectedOptions, option => option.value);
                setDimensions(values);
              }}
              className="w-full p-2 border rounded h-40"
            >
              {availableDimensions.map(dim => (
                <option key={dim} value={dim}>
                  {dim}
                </option>
              ))}
            </select>
            <p className="text-sm text-gray-500 mt-1">按住Ctrl/Cmd键可多选维度字段</p>
            
            {dimensions.length > 0 && (
              <div className="mt-2 p-3 bg-gray-50 rounded">
                <p className="font-medium mb-1">已选择维度:</p>
                <div className="flex flex-wrap gap-2">
                  {dimensions.map(dim => (
                    <span key={dim} className="px-2 py-1 bg-blue-100 text-blue-800 rounded text-sm">
                      {dim}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* 栏目6: 过滤条件 */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-lg font-semibold mb-4 border-b pb-2">过滤条件</h3>
          <div>
            <div className="flex justify-between items-center mb-2">
              <label className="block text-sm font-medium">过滤条件</label>
              <button
                type="button"
                onClick={addFilter}
                className="px-3 py-1 bg-blue-500 text-white text-sm rounded hover:bg-blue-600"
              >
                添加条件
              </button>
            </div>
            
            {filterConditions.length === 0 ? (
              <p className="text-sm text-gray-500">暂无过滤条件</p>
            ) : (
              <div className="space-y-3">
                {filterConditions.map((condition, index) => (
                  <div key={index} className="flex items-center space-x-2">
                    <select
                      value={condition.field}
                      onChange={(e) => updateFilter(index, 'field', e.target.value)}
                      className="flex-1 p-2 border rounded"
                    >
                      <option value="">请选择字段</option>
                      {filterFieldOptions.map(field => (
                        <option key={field} value={field}>
                          {field}
                        </option>
                      ))}
                    </select>
                    <select
                      value={condition.operator}
                      onChange={(e) => updateFilter(index, 'operator', e.target.value)}
                      className="p-2 border rounded"
                    >
                      <option value="=">等于</option>
                      <option value="!=">不等于</option>
                      <option value=">">大于</option>
                      <option value="<">小于</option>
                      <option value=">=">大于等于</option>
                      <option value="<=">小于等于</option>
                      <option value="like">包含</option>
                      <option value="in">在列表中</option>
                    </select>
                    {isTimeTypeField(condition.field) ? (
                      <input
                        type="datetime-local"
                        value={formatDateTimeForInput(condition.value)}
                        onChange={(e) => updateFilter(index, 'value', e.target.value)}
                        className="flex-1 p-2 border rounded"
                        placeholder="选择日期时间"
                      />
                    ) : (
                      <input
                        type="text"
                        value={condition.value}
                        onChange={(e) => updateFilter(index, 'value', e.target.value)}
                        className="flex-1 p-2 border rounded"
                        placeholder="值"
                      />
                    )}
                    <button
                      type="button"
                      onClick={() => removeFilter(index)}
                      className="px-2 py-1 bg-red-500 text-white rounded hover:bg-red-600"
                    >
                      删除
                    </button>
                  </div>
                ))}
              </div>
            )}
            {filterFieldOptions.length === 0 && selectedObjectType && (
              <p className="text-sm text-yellow-600 mt-2">
                提示：请先选择时间维度或维度字段，然后才能添加过滤条件
              </p>
            )}
            {!selectedObjectType && (
              <p className="text-sm text-gray-500 mt-2">
                提示：请先选择业务范围中的对象类型
              </p>
            )}
          </div>
        </div>

        {/* 保存按钮 */}
        <div className="flex justify-end space-x-4">
          <button
            onClick={onCancel}
            className="px-6 py-2 bg-gray-200 text-gray-700 rounded hover:bg-gray-300"
          >
            取消
          </button>
          <button
            onClick={handleSave}
            disabled={!selectedAtomicMetric || !selectedObjectType}
            className="px-6 py-2 bg-green-500 text-white rounded hover:bg-green-600 disabled:bg-gray-300 disabled:cursor-not-allowed"
          >
            保存指标
          </button>
        </div>
      </div>
    </div>
  );
};

export default DerivedMetricBuilder;
