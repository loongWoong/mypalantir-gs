import { useState, useEffect } from 'react';
import type { ObjectType, LinkType, QueryRequest, QueryResult, FilterExpression, Metric } from '../api/client';
import { schemaApi, queryApi } from '../api/client';
import { useWorkspace } from '../WorkspaceContext';
import { 
  PlayIcon, 
  DocumentDuplicateIcon, 
  ArrowPathIcon,
  MagnifyingGlassIcon,
  PlusIcon,
  TrashIcon
} from '@heroicons/react/24/outline';

interface FieldInfo {
  path: string;
  label: string;
  type: string;
}

export default function QueryBuilder() {
  const { selectedWorkspace } = useWorkspace();
  const [objectTypes, setObjectTypes] = useState<ObjectType[]>([]);
  const [linkTypes, setLinkTypes] = useState<LinkType[]>([]);
  const [selectedObjectType, setSelectedObjectType] = useState<ObjectType | null>(null);
  const [queryMode, setQueryMode] = useState<'simple' | 'aggregate'>('simple');
  const [query, setQuery] = useState<QueryRequest>({
    from: '',
    select: [],
    where: {},
    links: [],
    orderBy: [],
    limit: 20,
    offset: 0
  });
  const [filterExpressions, setFilterExpressions] = useState<FilterExpression[]>([]);
  const [results, setResults] = useState<QueryResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [showJson, setShowJson] = useState(false);
  const [viewMode, setViewMode] = useState<'table' | 'json'>('table');
  const [whereConditions, setWhereConditions] = useState<Array<{field: string; operator: string; value: string}>>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadSchema();
  }, []);

  // 根据工作空间过滤对象类型
  const filteredObjectTypes = selectedWorkspace && selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0
    ? objectTypes.filter((ot) => selectedWorkspace.object_types!.includes(ot.name))
    : objectTypes;

  useEffect(() => {
if (query.from || query.object) {      const objectName = query.object || query.from;      const ot = filteredObjectTypes.find(o => o.name === objectName);
      setSelectedObjectType(ot || null);
    } else {
      setSelectedObjectType(null);
    }
  }, [query.from, query.object, filteredObjectTypes]);

  useEffect(() => {
    // 更新 query.where 基于 whereConditions（仅用于简单模式）
    if (queryMode === 'simple') {
      const where: Record<string, any> = {};
      whereConditions.forEach(cond => {
        if (cond.field && cond.value) {
          where[cond.field] = cond.value;
        }
      });
      setQuery(prev => ({ ...prev, where }));
    }
  }, [whereConditions, queryMode]);

  const loadSchema = async () => {
    try {
      const [objectTypesData, linkTypesData] = await Promise.all([
        schemaApi.getObjectTypes(),
        schemaApi.getLinkTypes(),
      ]);
      setObjectTypes(objectTypesData);
      setLinkTypes(linkTypesData);
    } catch (error) {
      console.error('Failed to load schema:', error);
    }
  };

  // 获取可用字段（包括根对象和关联对象的属性）
  const getAvailableFields = (): FieldInfo[] => {
    const fields: FieldInfo[] = [];
    
    // 根对象的属�?
    selectedObjectType?.properties.forEach(prop => {
      fields.push({ path: prop.name, label: prop.name, type: prop.data_type });
    });
    
    // 关联对象的属�?
    query.links?.forEach(link => {
      const targetType = getLinkTargetType(link.name);
      targetType?.properties.forEach(prop => {
        fields.push({ 
          path: `${link.name}.${prop.name}`, 
          label: `${link.name}.${prop.name}`, 
          type: prop.data_type 
        });
      });
    });
    
    return fields;
  };

  const executeQuery = async () => {
    const objectName = query.object || query.from;
    if (!objectName) {
      setError('请选择对象类型');
      return;
    }
    
    if (queryMode === 'simple') {
      if (!query.select || query.select.length === 0) {
        setError('请至少选择一个属�?);
        return;
      }
    } else {
      // 聚合查询需�?group_by �?metrics
      if ((!query.group_by || query.group_by.length === 0) && 
          (!query.metrics || query.metrics.length === 0)) {
        setError('聚合查询需要至少一个分组属性或聚合指标');
        return;
      }
    }

    setLoading(true);
    setError(null);
    try {
      // 构建查询对象
      const queryPayload: any = {
        object: objectName,
        links: query.links?.map(link => {
          const targetType = getLinkTargetType(link.name);
          return {
            name: link.name,
            object: targetType?.name,
            select: link.select
          };
        }),
      };

      if (queryMode === 'aggregate') {
        // 聚合查询
        queryPayload.group_by = query.group_by || [];
        // �?Metric 对象转换为数组格式：["sum", "field", "alias"]
        queryPayload.metrics = query.metrics?.map((metric: Metric) => {
          const arr: any[] = [metric.function, metric.field];
          if (metric.alias) {
            arr.push(metric.alias);
          }
          return arr;
        }) || [];
      } else {
        // 普通查�?
        queryPayload.select = query.select || [];
      }

      // 过滤条件
      if (filterExpressions.length > 0) {
        queryPayload.filter = filterExpressions;
      } else if (Object.keys(query.where || {}).length > 0) {
        // 向后兼容旧的 where 格式
        queryPayload.where = query.where;
      }

      // 排序和分�?
      if (query.orderBy && query.orderBy.length > 0) {
        queryPayload.orderBy = query.orderBy;
      }
      queryPayload.limit = query.limit;
      queryPayload.offset = query.offset;

      const result = await queryApi.execute(queryPayload);
      setResults(result);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || '查询执行失败');
      setResults(null);
    } finally {
      setLoading(false);
    }
  };

  const addWhereCondition = () => {
    setWhereConditions([...whereConditions, { field: '', operator: '=', value: '' }]);
  };

  const removeWhereCondition = (index: number) => {
    setWhereConditions(whereConditions.filter((_, i) => i !== index));
  };

  const updateWhereCondition = (index: number, field: string, operator: string, value: string) => {
    const updated = [...whereConditions];
    updated[index] = { field, operator, value };
    setWhereConditions(updated);
  };

  // 过滤表达式相关函�?
  const addFilterExpression = () => {
    setFilterExpressions([...filterExpressions, ['=', '', ''] as FilterExpression]);
  };

  const removeFilterExpression = (index: number) => {
    setFilterExpressions(filterExpressions.filter((_, i) => i !== index));
  };

  const updateFilterExpression = (index: number, expr: FilterExpression) => {
    const updated = [...filterExpressions];
    updated[index] = expr;
    setFilterExpressions(updated);
  };

  const addLink = () => {
    const objectName = query.object || query.from || '';
    const availableLinks = getAvailableLinks(objectName);
    if (availableLinks.length === 0) {
      setError('当前对象类型没有可用的关联类�?);
      return;
    }
    setQuery(prev => ({
      ...prev,
      links: [...(prev.links || []), {
        name: availableLinks[0].name,
        select: [],
      }]
    }));
  };

  const removeLink = (index: number) => {
    setQuery(prev => ({
      ...prev,
      links: (prev.links || []).filter((_, i) => i !== index)
    }));
  };

  const updateLinkSelect = (linkIndex: number, propertyName: string, checked: boolean) => {
    setQuery(prev => {
      const links = [...(prev.links || [])];
      const link = { ...links[linkIndex] };
      if (checked) {
        link.select = [...(link.select || []), propertyName];
      } else {
        link.select = (link.select || []).filter(s => s !== propertyName);
      }
      links[linkIndex] = link;
      return { ...prev, links };
    });
  };

  const addOrderBy = () => {
    setQuery(prev => ({
      ...prev,
      orderBy: [...(prev.orderBy || []), { field: '', direction: 'ASC' }]
    }));
  };

  const removeOrderBy = (index: number) => {
    setQuery(prev => ({
      ...prev,
      orderBy: (prev.orderBy || []).filter((_, i) => i !== index)
    }));
  };

  const updateOrderBy = (index: number, field: string, direction: 'ASC' | 'DESC') => {
    setQuery(prev => {
      const orderBy = [...(prev.orderBy || [])];
      orderBy[index] = { field, direction };
      return { ...prev, orderBy };
    });
  };

  // 聚合指标相关函数
  const addMetric = () => {
    setQuery(prev => ({
      ...prev,
      metrics: [...(prev.metrics || []), { function: 'sum', field: '', alias: '' }]
    }));
  };

  const removeMetric = (index: number) => {
    setQuery(prev => ({
      ...prev,
      metrics: (prev.metrics || []).filter((_, i) => i !== index)
    }));
  };

  const updateMetric = (index: number, func: string, field: string, alias: string) => {
    setQuery(prev => {
      const metrics = [...(prev.metrics || [])];
      metrics[index] = { 
        function: func as 'sum' | 'avg' | 'count' | 'min' | 'max', 
        field, 
        alias: alias || undefined 
      };
      return { ...prev, metrics };
    });
  };

  const copyQueryJson = () => {
    const queryPayload: any = {
      object: query.object || query.from,
      links: query.links,
      filter: filterExpressions.length > 0 ? filterExpressions : undefined,
      where: Object.keys(query.where || {}).length > 0 ? query.where : undefined,
      group_by: query.group_by,
      select: query.select,
      orderBy: query.orderBy,
      limit: query.limit,
      offset: query.offset
    };
    // �?Metric 对象转换为数组格式用�?JSON 预览
    if (query.metrics && query.metrics.length > 0) {
      queryPayload.metrics = query.metrics.map((metric: Metric) => {
        const arr: any[] = [metric.function, metric.field];
        if (metric.alias) {
          arr.push(metric.alias);
        }
        return arr;
      });
    }
    navigator.clipboard.writeText(JSON.stringify(queryPayload, null, 2));
  };

  const exportResults = () => {
    if (!results) return;
    const json = JSON.stringify(results, null, 2);
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `query-results-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const getAvailableLinks = (objectTypeName: string) => {
    return linkTypes.filter(lt => {
      // 如果�?directed（有向），只有当 objectTypeName �?source_type 时才能作为关联查�?
      if (lt.direction === 'directed') {
        return lt.source_type === objectTypeName;
      }
      // 如果�?undirected（无向），objectTypeName 可以�?source_type �?target_type
      return lt.source_type === objectTypeName || lt.target_type === objectTypeName;
    });
  };

  const getLinkTargetType = (linkTypeName: string) => {
    const linkType = linkTypes.find(lt => lt.name === linkTypeName);
    const objectName = query.object || query.from;
    if (!linkType || !objectName) return null;
    
    // 如果�?directed（有向），只能从 source �?target
    if (linkType.direction === 'directed') {
      if (linkType.source_type === objectName) {
        return objectTypes.find(ot => ot.name === linkType.target_type);
      }
      // directed link 不能反向查询
      return null;
    }
    
    // 如果�?undirected（无向），可以从 source �?target，也可以�?target �?source
    if (linkType.source_type === objectName) {
      return objectTypes.find(ot => ot.name === linkType.target_type);
    }
    if (linkType.target_type === objectName) {
      return objectTypes.find(ot => ot.name === linkType.source_type);
    }
    
    return null;
  };

  const isNumericType = (type: string): boolean => {
    return ['number', 'integer', 'float', 'double', 'decimal', 'bigint'].includes(type.toLowerCase());
  };

  return (
    <div className="flex h-full bg-gray-50">
      {/* 左侧：查询构建器 */}
      <div className="w-1/2 border-r border-gray-200 overflow-y-auto p-6">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold text-gray-900">查询构建�?/h2>
        </div>

        {/* 查询模式切换 */}
        <div className="mb-6">
          <label className="block text-sm font-medium text-gray-700 mb-2">
            查询模式
          </label>
          <div className="flex gap-4">
            <label className="flex items-center cursor-pointer">
              <input
                type="radio"
                value="simple"
                checked={queryMode === 'simple'}
                onChange={() => {
                  setQueryMode('simple');
                  setQuery(prev => ({ ...prev, group_by: undefined, metrics: undefined }));
                }}
                className="mr-2"
              />
              <span className="text-sm">普通查�?/span>
            </label>
            <label className="flex items-center cursor-pointer">
              <input
                type="radio"
                value="aggregate"
                checked={queryMode === 'aggregate'}
                onChange={() => {
                  setQueryMode('aggregate');
                  setQuery(prev => ({ ...prev, select: [] }));
                }}
                className="mr-2"
              />
              <span className="text-sm">聚合查询</span>
            </label>
          </div>
        </div>

        {/* 对象类型选择 */}
        <div className="mb-6">
          <label className="block text-sm font-medium text-gray-700 mb-2">
            对象类型 <span className="text-red-500">*</span>
          </label>
          <select
            value={query.object || query.from}
            onChange={(e) => {
              setQuery({ ...query, object: e.target.value, from: e.target.value, select: [] });
              setWhereConditions([]);
              setFilterExpressions([]);
            }}
            className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">选择对象类型...</option>
            {filteredObjectTypes.map(ot => (
              <option key={ot.name} value={ot.name}>{ot.display_name || ot.name}</option>
            ))}
          </select>
        </div>

        {/* 属性选择（仅普通查询模式） */}
        {queryMode === 'simple' && selectedObjectType && (
          <div className="mb-6">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              选择属�?<span className="text-red-500">*</span>
            </label>
            <div className="space-y-2 border border-gray-300 rounded-lg p-3 max-h-48 overflow-y-auto bg-white">
              {selectedObjectType.properties.map(prop => (
                <label key={prop.name} className="flex items-center cursor-pointer hover:bg-gray-50 p-1 rounded">
                  <input
                    type="checkbox"
                    checked={query.select?.includes(prop.name) || false}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setQuery({ ...query, select: [...(query.select || []), prop.name] });
                      } else {
                        setQuery({ ...query, select: (query.select || []).filter(s => s !== prop.name) });
                      }
                    }}
                    className="mr-2"
                  />
                  <span className="text-sm text-gray-900">{prop.name}</span>
                  <span className="text-xs text-gray-500 ml-2">({prop.data_type})</span>
                </label>
              ))}
            </div>
          </div>
        )}

        {/* 关联查询 */}
        <div className="mb-6">
          <div className="flex items-center justify-between mb-2">
            <label className="block text-sm font-medium text-gray-700">关联查询</label>
            <button
              onClick={addLink}
              disabled={!query.object && !query.from}
              className="text-sm text-blue-600 hover:text-blue-800 flex items-center disabled:text-gray-400"
            >
              <PlusIcon className="w-4 h-4 mr-1" />
              添加关联
            </button>
          </div>
          <div className="space-y-3">
            {query.links?.map((link, linkIndex) => {
              const targetType = getLinkTargetType(link.name);
              return (
                <div key={linkIndex} className="border border-gray-300 rounded-lg p-3 bg-gray-50">
                  <div className="flex items-center justify-between mb-2">
                    <select
                      value={link.name}
                      onChange={(e) => {
                        setQuery(prev => {
                          const links = [...(prev.links || [])];
                          links[linkIndex] = { ...links[linkIndex], name: e.target.value, select: [] };
                          return { ...prev, links };
                        });
                      }}
                      className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm mr-2"
                    >
                      <option value="">选择关联类型...</option>
                      {getAvailableLinks(query.object || query.from || '').map(lt => (
                        <option key={lt.name} value={lt.name}>{lt.name}</option>
                      ))}
                    </select>
                    <button
                      onClick={() => removeLink(linkIndex)}
                      className="text-red-600 hover:text-red-800"
                    >
                      <TrashIcon className="w-4 h-4" />
                    </button>
                  </div>
                  {targetType && queryMode === 'simple' && (
                    <div className="mt-2 space-y-1">
                      <label className="text-xs text-gray-600">选择属�?</label>
                      <div className="space-y-1 max-h-32 overflow-y-auto">
                        {targetType.properties.map(prop => (
                          <label key={prop.name} className="flex items-center cursor-pointer hover:bg-white p-1 rounded">
                            <input
                              type="checkbox"
                              checked={link.select?.includes(prop.name) || false}
                              onChange={(e) => updateLinkSelect(linkIndex, prop.name, e.target.checked)}
                              className="mr-2"
                            />
                            <span className="text-xs text-gray-900">{prop.name}</span>
                          </label>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* 查询条件 */}
        <div className="mb-6">
          <div className="flex items-center justify-between mb-2">
            <label className="block text-sm font-medium text-gray-700">查询条件</label>
            {queryMode === 'simple' ? (
              <button
                onClick={addWhereCondition}
                className="text-sm text-blue-600 hover:text-blue-800 flex items-center"
              >
                <PlusIcon className="w-4 h-4 mr-1" />
                添加条件
              </button>
            ) : (
              <button
                onClick={addFilterExpression}
                className="text-sm text-blue-600 hover:text-blue-800 flex items-center"
              >
                <PlusIcon className="w-4 h-4 mr-1" />
                添加条件
              </button>
            )}
          </div>
          
          {queryMode === 'simple' ? (
            // 简单模式的过滤条件
            <div className="space-y-2">
              {whereConditions.map((cond, index) => (
                <div key={index} className="flex gap-2 items-center">
                  <select
                    value={cond.field}
                    onChange={(e) => updateWhereCondition(index, e.target.value, cond.operator, cond.value)}
                    className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                  >
                    <option value="">选择属�?..</option>
                    {selectedObjectType?.properties.map(prop => (
                      <option key={prop.name} value={prop.name}>{prop.name}</option>
                    ))}
                  </select>
                  <select
                    value={cond.operator}
                    onChange={(e) => updateWhereCondition(index, cond.field, e.target.value, cond.value)}
                    className="border border-gray-300 rounded px-2 py-1 text-sm"
                  >
                    <option value="=">=</option>
                    <option value=">">&gt;</option>
                    <option value="<">&lt;</option>
                    <option value=">=">&gt;=</option>
                    <option value="<=">&lt;=</option>
                    <option value="LIKE">LIKE</option>
                  </select>
                  <input
                    type="text"
                    value={cond.value}
                    onChange={(e) => updateWhereCondition(index, cond.field, cond.operator, e.target.value)}
                    placeholder="�?
                    className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                  />
                  <button
                    onClick={() => removeWhereCondition(index)}
                    className="text-red-600 hover:text-red-800"
                  >
                    <TrashIcon className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          ) : (
            // 聚合模式的过滤表达式
            <div className="space-y-2">
              {filterExpressions.map((expr, index) => {
                const operator = expr[0];
                const fieldPath = expr[1] as string;
                const value1 = expr[2];
                const value2 = expr.length > 3 ? expr[3] : undefined;
                
                return (
                  <div key={index} className="flex gap-2 items-center border border-gray-300 rounded p-2 bg-white">
                    <select
                      value={operator}
                      onChange={(e) => {
                        const newOp = e.target.value;
                        if (newOp === 'between') {
                          updateFilterExpression(index, [newOp as any, fieldPath, value1 || '', value2 || ''] as FilterExpression);
                        } else {
                          updateFilterExpression(index, [newOp as any, fieldPath, value1 || ''] as FilterExpression);
                        }
                      }}
                      className="border border-gray-300 rounded px-2 py-1 text-sm"
                    >
                      <option value="=">=</option>
                      <option value=">">&gt;</option>
                      <option value="<">&lt;</option>
                      <option value=">=">&gt;=</option>
                      <option value="<=">&lt;=</option>
                      <option value="between">BETWEEN</option>
                      <option value="LIKE">LIKE</option>
                    </select>
                    <select
                      value={fieldPath}
                      onChange={(e) => {
                        if (operator === 'between') {
                          updateFilterExpression(index, [operator as any, e.target.value, value1 || '', value2 || ''] as FilterExpression);
                        } else {
                          updateFilterExpression(index, [operator as any, e.target.value, value1 || ''] as FilterExpression);
                        }
                      }}
                      className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                    >
                      <option value="">选择属�?..</option>
                      {getAvailableFields().map(field => (
                        <option key={field.path} value={field.path}>{field.label}</option>
                      ))}
                    </select>
                    {operator === 'between' ? (
                      <>
                        <input
                          type="text"
                          value={value1 || ''}
                          onChange={(e) => updateFilterExpression(index, [operator as any, fieldPath, e.target.value, value2 || ''] as FilterExpression)}
                          placeholder="起始�?
                          className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                        />
                        <span className="text-sm text-gray-500">�?/span>
                        <input
                          type="text"
                          value={value2 || ''}
                          onChange={(e) => updateFilterExpression(index, [operator as any, fieldPath, value1 || '', e.target.value] as FilterExpression)}
                          placeholder="结束�?
                          className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                        />
                      </>
                    ) : (
                      <input
                        type="text"
                        value={value1 || ''}
                        onChange={(e) => updateFilterExpression(index, [operator as any, fieldPath, e.target.value] as FilterExpression)}
                        placeholder="�?
                        className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                      />
                    )}
                    <button
                      onClick={() => removeFilterExpression(index)}
                      className="text-red-600 hover:text-red-800"
                    >
                      <TrashIcon className="w-4 h-4" />
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* 分组功能（仅聚合查询模式�?*/}
        {queryMode === 'aggregate' && (
          <div className="mb-6">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              分组属�?
            </label>
            <div className="space-y-2 border border-gray-300 rounded-lg p-3 max-h-48 overflow-y-auto bg-white">
              {getAvailableFields().map(field => (
                <label key={field.path} className="flex items-center cursor-pointer hover:bg-gray-50 p-1 rounded">
                  <input
                    type="checkbox"
                    checked={query.group_by?.includes(field.path) || false}
                    onChange={(e) => {
                      const groupBy = query.group_by || [];
                      if (e.target.checked) {
                        setQuery({ ...query, group_by: [...groupBy, field.path] });
                      } else {
                        setQuery({ ...query, group_by: groupBy.filter(g => g !== field.path) });
                      }
                    }}
                    className="mr-2"
                  />
                  <span className="text-sm text-gray-900">{field.label}</span>
                  <span className="text-xs text-gray-500 ml-2">({field.type})</span>
                </label>
              ))}
            </div>
          </div>
        )}

        {/* 聚合指标（仅聚合查询模式�?*/}
        {queryMode === 'aggregate' && (
          <div className="mb-6">
            <div className="flex items-center justify-between mb-2">
              <label className="block text-sm font-medium text-gray-700">聚合指标</label>
              <button
                onClick={addMetric}
                className="text-sm text-blue-600 hover:text-blue-800 flex items-center"
              >
                <PlusIcon className="w-4 h-4 mr-1" />
                添加指标
              </button>
            </div>
            <div className="space-y-2">
              {query.metrics?.map((metric, index) => (
                <div key={index} className="flex gap-2 items-center border border-gray-300 rounded p-2 bg-white">
                  <select
                    value={metric.function}
                    onChange={(e) => updateMetric(index, e.target.value, metric.field, metric.alias || '')}
                    className="border border-gray-300 rounded px-2 py-1 text-sm"
                  >
                    <option value="sum">SUM</option>
                    <option value="avg">AVG</option>
                    <option value="count">COUNT</option>
                    <option value="min">MIN</option>
                    <option value="max">MAX</option>
                  </select>
                  <select
                    value={metric.field}
                    onChange={(e) => updateMetric(index, metric.function, e.target.value, metric.alias || '')}
                    className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                  >
                    <option value="">选择属�?..</option>
                    {getAvailableFields()
                      .filter(f => metric.function === 'count' || isNumericType(f.type))
                      .map(field => (
                        <option key={field.path} value={field.path}>{field.label}</option>
                      ))}
                  </select>
                  <input
                    type="text"
                    placeholder="别名（可选）"
                    value={metric.alias || ''}
                    onChange={(e) => updateMetric(index, metric.function, metric.field, e.target.value)}
                    className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                  />
                  <button
                    onClick={() => removeMetric(index)}
                    className="text-red-600 hover:text-red-800"
                  >
                    <TrashIcon className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 排序和分�?*/}
        <div className="mb-6">
          <label className="block text-sm font-medium text-gray-700 mb-2">排序</label>
          <div className="space-y-2">
            {query.orderBy?.map((order, index) => (
              <div key={index} className="flex gap-2 items-center">
                <select
                  value={order.field}
                  onChange={(e) => updateOrderBy(index, e.target.value, order.direction)}
                  className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                >
                  <option value="">选择属�?..</option>
                  {getAvailableFields().map(field => (
                    <option key={field.path} value={field.path}>{field.label}</option>
                  ))}
                </select>
                <select
                  value={order.direction}
                  onChange={(e) => updateOrderBy(index, order.field, e.target.value as 'ASC' | 'DESC')}
                  className="border border-gray-300 rounded px-2 py-1 text-sm"
                >
                  <option value="ASC">ASC</option>
                  <option value="DESC">DESC</option>
                </select>
                <button
                  onClick={() => removeOrderBy(index)}
                  className="text-red-600 hover:text-red-800"
                >
                  <TrashIcon className="w-4 h-4" />
                </button>
              </div>
            ))}
            <button
              onClick={addOrderBy}
              className="text-sm text-blue-600 hover:text-blue-800 flex items-center"
            >
              <PlusIcon className="w-4 h-4 mr-1" />
              添加排序
            </button>
          </div>
        </div>

        <div className="mb-6 grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">每页数量</label>
            <input
              type="number"
              value={query.limit || 20}
              onChange={(e) => setQuery({ ...query, limit: parseInt(e.target.value) || 20 })}
              className="w-full border border-gray-300 rounded px-2 py-1 text-sm"
              min="1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">偏移�?/label>
            <input
              type="number"
              value={query.offset || 0}
              onChange={(e) => setQuery({ ...query, offset: parseInt(e.target.value) || 0 })}
              className="w-full border border-gray-300 rounded px-2 py-1 text-sm"
              min="0"
            />
          </div>
        </div>

        {/* JSON 预览 */}
        <div className="mb-6">
          <div className="flex items-center justify-between mb-2">
            <label className="block text-sm font-medium text-gray-700">查询 JSON</label>
            <div className="flex gap-2">
              <button
                onClick={copyQueryJson}
                className="text-sm text-blue-600 hover:text-blue-800 flex items-center"
                title="复制 JSON"
              >
                <DocumentDuplicateIcon className="w-4 h-4 mr-1" />
              </button>
              <button
                onClick={() => setShowJson(!showJson)}
                className="text-sm text-blue-600 hover:text-blue-800"
              >
                {showJson ? '隐藏' : '显示'} JSON
              </button>
            </div>
          </div>
          {showJson && (
            <textarea
              className="w-full font-mono text-xs p-2 border border-gray-300 rounded"
              rows={10}
              value={JSON.stringify({
                object: query.object || query.from,
                links: query.links,
                filter: filterExpressions.length > 0 ? filterExpressions : undefined,
                where: Object.keys(query.where || {}).length > 0 ? query.where : undefined,
                group_by: query.group_by,
                metrics: query.metrics?.map((metric: Metric) => {
                  const arr: any[] = [metric.function, metric.field];
                  if (metric.alias) {
                    arr.push(metric.alias);
                  }
                  return arr;
                }),
                select: query.select,
                orderBy: query.orderBy,
                limit: query.limit,
                offset: query.offset
              }, null, 2)}
              onChange={(e) => {
                try {
                  const parsed = JSON.parse(e.target.value);
                  // 如果 metrics 是数组格式，转换为对象格�?
                  if (parsed.metrics && Array.isArray(parsed.metrics)) {
                    parsed.metrics = parsed.metrics.map((m: any) => {
                      if (Array.isArray(m)) {
                        return {
                          function: m[0],
                          field: m[1],
                          alias: m.length > 2 ? m[2] : undefined
                        };
                      }
                      return m;
                    });
                  }
                  setQuery(parsed);
                  if (parsed.filter) {
                    setFilterExpressions(parsed.filter);
                  }
                } catch (err) {
                  // 忽略无效 JSON
                }
              }}
            />
          )}
        </div>

        {/* 错误提示 */}
        {error && (
          <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded text-sm text-red-700">
            {error}
          </div>
        )}

        {/* 执行按钮 */}
        <button
          onClick={executeQuery}
          disabled={loading || !query.object && !query.from || 
            (queryMode === 'simple' && (!query.select || query.select.length === 0)) ||
            (queryMode === 'aggregate' && (!query.group_by || query.group_by.length === 0) && (!query.metrics || query.metrics.length === 0))}
          className="w-full flex items-center justify-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
        >
          {loading ? (
            <>
              <ArrowPathIcon className="w-5 h-5 animate-spin mr-2" />
              执行�?..
            </>
          ) : (
            <>
              <PlayIcon className="w-5 h-5 mr-2" />
              执行查询
            </>
          )}
        </button>
      </div>

      {/* 右侧：查询结�?*/}
      <div className="w-1/2 overflow-y-auto p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold text-gray-900">查询结果</h2>
          {results && (
            <div className="flex items-center gap-2">
              <button
                onClick={exportResults}
                className="text-sm text-blue-600 hover:text-blue-800 flex items-center"
              >
                <DocumentDuplicateIcon className="w-4 h-4 mr-1" />
                导出
              </button>
              <button
                onClick={() => setViewMode(viewMode === 'table' ? 'json' : 'table')}
                className="text-sm text-blue-600 hover:text-blue-800"
              >
                {viewMode === 'table' ? 'JSON 视图' : '表格视图'}
              </button>
            </div>
          )}
        </div>

        {results ? (
          <div>
            <div className="mb-4 text-sm text-gray-600">
              �?{results.rowCount} 条结�?
            </div>
            {viewMode === 'table' ? (
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200 bg-white rounded-lg shadow">
                  <thead className="bg-gray-50">
                    <tr>
                      {results.columns.map(col => (
                        <th
                          key={col}
                          className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
                        >
                          {col.startsWith('$f') ? `聚合结果 ${col}` : col}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {results.rows.map((row, idx) => (
                      <tr key={idx} className="hover:bg-gray-50">
                        {results.columns.map(col => (
                          <td
                            key={col}
                            className="px-6 py-4 whitespace-nowrap text-sm text-gray-900"
                          >
                            {row[col] !== null && row[col] !== undefined ? String(row[col]) : 'null'}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <pre className="p-4 overflow-auto bg-gray-50 text-sm rounded border border-gray-200">
                {JSON.stringify(results, null, 2)}
              </pre>
            )}
          </div>
        ) : (
          <div className="text-center text-gray-500 py-12">
            <MagnifyingGlassIcon className="w-12 h-12 mx-auto mb-4 text-gray-400" />
            <p>点击"执行查询"查看结果</p>
          </div>
        )}
      </div>
    </div>
  );
}
