import { useState, useEffect } from 'react';
import type { ObjectType, LinkType, QueryRequest, QueryResult, FilterExpression, Metric } from '../api/client';
import { schemaApi, queryApi } from '../api/client';
import { metricApi } from '../api/metric';
import type { MetricDefinition, MetricQuery, MetricResult, AtomicMetric } from '../api/metric';
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
  const [queryMode, setQueryMode] = useState<'simple' | 'aggregate' | 'metric'>('simple');
  const [metricDefinitions, setMetricDefinitions] = useState<MetricDefinition[]>([]);
  const [atomicMetrics, setAtomicMetrics] = useState<AtomicMetric[]>([]);
  const [selectedMetricId, setSelectedMetricId] = useState<string>('');
  const [selectedMetricType, setSelectedMetricType] = useState<'atomic' | 'derived' | 'composite' | null>(null);
  const [metricTimeRange, setMetricTimeRange] = useState<{ start: string; end: string }>({ start: '', end: '' });
  const [metricDimensions, setMetricDimensions] = useState<Record<string, any>>({});
  const [metricResult, setMetricResult] = useState<MetricResult | null>(null);
  // 复合指标中的派生指标及其查询条件
  const [derivedMetricsInComposite, setDerivedMetricsInComposite] = useState<MetricDefinition[]>([]);
  const [derivedMetricConditions, setDerivedMetricConditions] = useState<Record<string, {
    time_range?: { start: string; end: string };
    dimensions?: Record<string, any>;
  }>>({});
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
    loadMetricDefinitions();
  }, []);

  const normalizeMetricDefinition = (def: any): MetricDefinition => ({
    ...def,
    // 兼容后端 camelCase 字段
    metric_type: def.metric_type || def.metricType,
    display_name: def.display_name || def.displayName,
    time_dimension: def.time_dimension || def.timeDimension,
    time_granularity: def.time_granularity || def.timeGranularity,
    atomic_metric_id: def.atomic_metric_id || def.atomicMetricId,
    filter_conditions: def.filter_conditions || def.filterConditions,
    comparison_type: def.comparison_type || def.comparisonType,
    base_metric_ids: def.base_metric_ids || def.baseMetricIds,
    derived_formula: def.derived_formula || def.derivedFormula,
  });

  const loadMetricDefinitions = async () => {
    try {
      const [definitions, atomic] = await Promise.all([
        metricApi.listMetricDefinitions(),
        metricApi.listAtomicMetrics(),
      ]);
      console.log('Loaded metric definitions:', definitions);
      console.log('Loaded atomic metrics:', atomic);
      const normalized = definitions.map(normalizeMetricDefinition);
      setMetricDefinitions(normalized);
      setAtomicMetrics(atomic);
    } catch (error) {
      console.error('Failed to load metrics:', error);
      setError('加载指标列表失败: ' + (error as Error).message);
    }
  };

  // 根据工作空间过滤对象类型
  const filteredObjectTypes = selectedWorkspace && selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0
    ? objectTypes.filter((ot) => selectedWorkspace.object_types!.includes(ot.name))
    : objectTypes;

  useEffect(() => {
    if (query.from || query.object) {
      const objectName = query.object || query.from;
      const ot = filteredObjectTypes.find(o => o.name === objectName);
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

  // 当选择复合指标时，加载其包含的派生指标
  useEffect(() => {
    const loadDerivedMetricsInComposite = async () => {
      if (!selectedMetricId) {
        setDerivedMetricsInComposite([]);
        setDerivedMetricConditions({});
        return;
      }

      const selectedMetric = metricDefinitions.find(m => m.id === selectedMetricId);
      if (!selectedMetric || selectedMetric.metric_type !== 'composite') {
        setDerivedMetricsInComposite([]);
        setDerivedMetricConditions({});
        return;
      }

      // 获取复合指标的基础指标ID列表
      const baseMetricIds = selectedMetric.base_metric_ids || [];
      if (baseMetricIds.length === 0) {
        setDerivedMetricsInComposite([]);
        setDerivedMetricConditions({});
        return;
      }

      try {
        // 加载所有基础指标的详细信息
        const baseMetricsDetails = await Promise.all(
          baseMetricIds.map(async (id) => {
            try {
              // 先尝试从原子指标中查找
              const atomicMetric = atomicMetrics.find(m => m.id === id);
              if (atomicMetric) {
                return { type: 'atomic' as const, metric: atomicMetric };
              }
              // 再从指标定义中查找
              const metricDef = metricDefinitions.find(m => m.id === id);
              if (metricDef) {
                return { type: 'definition' as const, metric: metricDef };
              }
              // 如果本地没有，尝试从服务器获取
              const definition = await metricApi.getMetricDefinition(id);
              return { type: 'definition' as const, metric: normalizeMetricDefinition(definition) };
            } catch (err) {
              console.error(`Failed to load base metric ${id}:`, err);
              return null;
            }
          })
        );

        // 筛选出派生指标
        const derivedMetrics = baseMetricsDetails
          .filter(detail => detail && detail.type === 'definition' && 
                           (detail.metric as MetricDefinition).metric_type === 'derived')
          .map(detail => detail!.metric as MetricDefinition);

        setDerivedMetricsInComposite(derivedMetrics);

        // 初始化派生指标的查询条件
        const initialConditions: Record<string, {
          time_range?: { start: string; end: string };
          dimensions?: Record<string, any>;
        }> = {};
        
        derivedMetrics.forEach(dm => {
          initialConditions[dm.id] = {
            time_range: { start: '', end: '' },
            dimensions: {}
          };
        });
        
        setDerivedMetricConditions(initialConditions);
      } catch (error) {
        console.error('Failed to load base metrics:', error);
        setDerivedMetricsInComposite([]);
        setDerivedMetricConditions({});
      }
    };

    loadDerivedMetricsInComposite();
  }, [selectedMetricId, metricDefinitions, atomicMetrics]);

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
    
    // 根对象的属性
    selectedObjectType?.properties.forEach(prop => {
      fields.push({ path: prop.name, label: prop.name, type: prop.data_type });
    });
    
    // 关联对象的属性
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
    // 指标查询
    if (queryMode === 'metric') {
      if (!selectedMetricId) {
        setError('请选择指标');
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const metricQuery: MetricQuery = {
          metric_id: selectedMetricId,
        };

        // 添加时间范围
        if (metricTimeRange.start && metricTimeRange.end) {
          metricQuery.time_range = {
            start: metricTimeRange.start,
            end: metricTimeRange.end,
          };
        }

        // 添加维度
        if (Object.keys(metricDimensions).length > 0) {
          const filteredDimensions: Record<string, any> = {};
          Object.entries(metricDimensions).forEach(([key, value]) => {
            if (value && value.trim() !== '') {
              filteredDimensions[key] = value;
            }
          });
          if (Object.keys(filteredDimensions).length > 0) {
            metricQuery.dimensions = filteredDimensions;
          }
        }

        // 添加复合指标中的派生指标查询条件
        const selectedMetric = metricDefinitions.find(m => m.id === selectedMetricId);
        if (selectedMetric && selectedMetric.metric_type === 'composite' && derivedMetricsInComposite.length > 0) {
          const derivedConditions: Record<string, {
            time_range?: { start: string; end: string };
            dimensions?: Record<string, any>;
          }> = {};

          derivedMetricsInComposite.forEach(dm => {
            const conditions = derivedMetricConditions[dm.id];
            if (conditions) {
              const metricCondition: {
                time_range?: { start: string; end: string };
                dimensions?: Record<string, any>;
              } = {};

              // 添加时间范围
              if (conditions.time_range && conditions.time_range.start && conditions.time_range.end) {
                metricCondition.time_range = {
                  start: conditions.time_range.start,
                  end: conditions.time_range.end
                };
              }

              // 添加维度
              if (conditions.dimensions && Object.keys(conditions.dimensions).length > 0) {
                const filteredDims: Record<string, any> = {};
                Object.entries(conditions.dimensions).forEach(([key, value]) => {
                  if (value && typeof value === 'string' && value.trim() !== '') {
                    filteredDims[key] = value;
                  }
                });
                if (Object.keys(filteredDims).length > 0) {
                  metricCondition.dimensions = filteredDims;
                }
              }

              // 只有当有有效条件时才添加
              if (metricCondition.time_range || metricCondition.dimensions) {
                derivedConditions[dm.id] = metricCondition;
              }
            }
          });

          if (Object.keys(derivedConditions).length > 0) {
            metricQuery.derived_metric_conditions = derivedConditions;
          }
        }

        const result = await metricApi.calculateMetric(metricQuery);
        setMetricResult(result);
        setResults(null); // 清空普通查询结果
      } catch (error: any) {
        console.error('Failed to calculate metric:', error);
        setError('指标查询失败: ' + (error.response?.data?.message || error.message || '未知错误'));
        setMetricResult(null);
      } finally {
        setLoading(false);
      }
      return;
    }

    // 普通查询和聚合查询
    const objectName = query.object || query.from;
    if (!objectName) {
      setError('请选择对象类型');
      return;
    }
    
    if (queryMode === 'simple') {
      if (!query.select || query.select.length === 0) {
        setError('请至少选择一个属性');
        return;
      }
    } else {
      // 聚合查询需要 group_by 或 metrics
      if ((!query.group_by || query.group_by.length === 0) && 
          (!query.metrics || query.metrics.length === 0)) {
        setError('聚合查询需要至少一个分组属性或聚合指标');
        return;
      }
    }

    setLoading(true);
    setError(null);
    setMetricResult(null); // 清空指标查询结果
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
        // 将 Metric 对象转换为数组格式：["sum", "field", "alias"]
        queryPayload.metrics = query.metrics?.map((metric: Metric) => {
          const arr: any[] = [metric.function, metric.field];
          if (metric.alias) {
            arr.push(metric.alias);
          }
          return arr;
        }) || [];
      } else {
        // 普通查询
        queryPayload.select = query.select || [];
      }

      // 过滤条件
      if (filterExpressions.length > 0) {
        queryPayload.filter = filterExpressions;
      } else if (Object.keys(query.where || {}).length > 0) {
        // 向后兼容旧的 where 格式
        queryPayload.where = query.where;
      }

      // 排序和分页
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

  // 过滤表达式相关函数
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
      setError('当前对象类型没有可用的关联类型');
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
    let queryPayload: any;
    
    if (queryMode === 'metric') {
      // 指标查询 JSON
      const selectedMetric = atomicMetrics.find(m => m.id === selectedMetricId) || 
                            metricDefinitions.find(m => m.id === selectedMetricId);
      
      queryPayload = {
        metric_id: selectedMetricId,
        metric_info: selectedMetric ? {
          id: selectedMetric.id,
          name: selectedMetric.name,
          display_name: 'display_name' in selectedMetric ? selectedMetric.display_name : undefined,
          type: selectedMetricType || ('metric_type' in selectedMetric ? selectedMetric.metric_type : undefined),
          ...(selectedMetric && 'business_process' in selectedMetric ? {
            business_process: selectedMetric.business_process,
            aggregation_function: selectedMetric.aggregation_function,
            aggregation_field: selectedMetric.aggregation_field,
            unit: selectedMetric.unit
          } : {}),
          ...(selectedMetric && 'business_scope' in selectedMetric ? {
            business_scope: selectedMetric.business_scope,
            time_dimension: selectedMetric.time_dimension,
            time_granularity: selectedMetric.time_granularity,
            dimensions: selectedMetric.dimensions
          } : {})
        } : undefined,
        time_range: (metricTimeRange.start && metricTimeRange.end) ? {
          start: metricTimeRange.start,
          end: metricTimeRange.end
        } : undefined,
        dimensions: Object.keys(metricDimensions).length > 0 ? 
          Object.fromEntries(
            Object.entries(metricDimensions).filter(([_, v]) => v && v.trim() !== '')
          ) : undefined
      };
    } else {
      // 普通查询和聚合查询 JSON
      queryPayload = {
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
      // 将 Metric 对象转换为数组格式用于 JSON 预览
      if (query.metrics && query.metrics.length > 0) {
        queryPayload.metrics = query.metrics.map((metric: Metric) => {
          const arr: any[] = [metric.function, metric.field];
          if (metric.alias) {
            arr.push(metric.alias);
          }
          return arr;
        });
      }
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
      // 如果是 directed（有向），只有当 objectTypeName 是 source_type 时才能作为关联查询
      if (lt.direction === 'directed') {
        return lt.source_type === objectTypeName;
      }
      // 如果是 undirected（无向），objectTypeName 可以是 source_type 或 target_type
      return lt.source_type === objectTypeName || lt.target_type === objectTypeName;
    });
  };

  const getLinkTargetType = (linkTypeName: string) => {
    const linkType = linkTypes.find(lt => lt.name === linkTypeName);
    const objectName = query.object || query.from;
    if (!linkType || !objectName) return null;
    
    // 如果是 directed（有向），只能从 source 到 target
    if (linkType.direction === 'directed') {
      if (linkType.source_type === objectName) {
        return objectTypes.find(ot => ot.name === linkType.target_type);
      }
      // directed link 不能反向查询
      return null;
    }
    
    // 如果是 undirected（无向），可以从 source 到 target，也可以从 target 到 source
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
          <h2 className="text-xl font-bold text-gray-900">查询构建器</h2>
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
              <span className="text-sm">普通查询</span>
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
            <label className="flex items-center cursor-pointer">
              <input
                type="radio"
                value="metric"
                checked={queryMode === 'metric'}
                onChange={() => {
                  setQueryMode('metric');
                  setMetricResult(null);
                  // 切换到指标查询模式时，重新加载指标列表
                  loadMetricDefinitions();
                }}
                className="mr-2"
              />
              <span className="text-sm">指标查询</span>
            </label>
          </div>
        </div>

        {/* 对象类型选择（非指标查询模式） */}
        {queryMode !== 'metric' && (
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
        )}

        {/* 指标查询配置 */}
        {queryMode === 'metric' && (
          <>
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                选择指标 <span className="text-red-500">*</span>
              </label>
              <select
                value={selectedMetricId}
                onChange={(e) => {
                  const value = e.target.value;
                  setSelectedMetricId(value);
                  setMetricResult(null);
                  // 清空维度
                  setMetricDimensions({});
                  // 判断指标类型
                  if (atomicMetrics.find(m => m.id === value)) {
                    setSelectedMetricType('atomic');
                  } else if (metricDefinitions.find(m => m.id === value)) {
                    const metric = metricDefinitions.find(m => m.id === value);
                    setSelectedMetricType(metric?.metric_type || null);
                  } else {
                    setSelectedMetricType(null);
                  }
                }}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">选择指标...</option>
                {/* 原子指标 */}
                {atomicMetrics.length > 0 && (
                  <optgroup label="原子指标">
                    {atomicMetrics.map(metric => (
                      <option key={metric.id} value={metric.id}>
                        {metric.display_name || metric.name}
                      </option>
                    ))}
                  </optgroup>
                )}
                {/* 派生指标 */}
                {metricDefinitions.filter(m => m.metric_type === 'derived').length > 0 && (
                  <optgroup label="派生指标">
                    {metricDefinitions.filter(m => m.metric_type === 'derived').map(metric => (
                      <option key={metric.id} value={metric.id}>
                        {metric.display_name || metric.name}
                      </option>
                    ))}
                  </optgroup>
                )}
                {/* 复合指标 */}
                {metricDefinitions.filter(m => m.metric_type === 'composite').length > 0 && (
                  <optgroup label="复合指标">
                    {metricDefinitions.filter(m => m.metric_type === 'composite').map(metric => (
                      <option key={metric.id} value={metric.id}>
                        {metric.display_name || metric.name}
                      </option>
                    ))}
                  </optgroup>
                )}
                {atomicMetrics.length === 0 && metricDefinitions.length === 0 && (
                  <option value="" disabled>暂无可用指标</option>
                )}
              </select>
              {atomicMetrics.length === 0 && metricDefinitions.length === 0 && (
                <p className="text-sm text-gray-500 mt-1">
                  没有可用的指标，请先在指标管理页面创建指标
                </p>
              )}
            </div>

            {selectedMetricId && (() => {
              // 先查找原子指标
              let selectedMetric: AtomicMetric | MetricDefinition | undefined = atomicMetrics.find(m => m.id === selectedMetricId);
              // 如果没找到，再查找指标定义
              if (!selectedMetric) {
                selectedMetric = metricDefinitions.find(m => m.id === selectedMetricId);
              }
              
              // 判断是否为复合指标
              const isCompositeMetric = selectedMetric && 'metric_type' in selectedMetric && selectedMetric.metric_type === 'composite';
              
              return selectedMetric ? (
                <>
                  {/* 复合指标说明 */}
                  {isCompositeMetric && (
                    <div className="mb-6 bg-blue-50 border border-blue-200 rounded-lg p-4">
                      <div className="flex items-start">
                        <svg className="w-5 h-5 text-blue-600 mr-2 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                          <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                        </svg>
                        <div className="flex-1">
                          <h4 className="text-sm font-medium text-blue-900 mb-1">复合指标查询说明</h4>
                          <p className="text-xs text-blue-800">
                            复合指标基于多个基础指标计算。下方设置的<strong>时间范围</strong>和<strong>维度筛选</strong>条件将传递给所有基础指标（包括派生指标）。
                            如果基础指标为派生指标，它将使用这些参数结合自身的<strong>过滤条件</strong>来计算。
                          </p>
                        </div>
                      </div>
                    </div>
                  )}
                  
                  {/* 复合指标中的派生指标查询条件输入 */}
                  {isCompositeMetric && derivedMetricsInComposite.length > 0 && (
                    <div className="mb-6 bg-purple-50 border border-purple-200 rounded-lg p-4">
                      <h4 className="text-sm font-semibold text-purple-900 mb-4 flex items-center">
                        <svg className="w-5 h-5 mr-2" fill="currentColor" viewBox="0 0 20 20">
                          <path fillRule="evenodd" d="M11.3 1.046A1 1 0 0112 2v5h4a1 1 0 01.82 1.573l-7 10A1 1 0 018 18v-5H4a1 1 0 01-.82-1.573l7-10a1 1 0 011.12-.38z" clipRule="evenodd" />
                        </svg>
                        派生指标查询条件
                        <span className="ml-2 text-xs font-normal text-purple-700">
                          (检测到 {derivedMetricsInComposite.length} 个派生指标)
                        </span>
                      </h4>
                      
                      <p className="text-xs text-purple-700 mb-4">
                        ℹ️ 请为每个派生指标设置其所需的查询条件（时间范围和维度筛选）。每个派生指标还将自动应用其固有的过滤条件。
                      </p>
                      
                      <div className="space-y-6">
                        {derivedMetricsInComposite.map((derivedMetric, index) => (
                          <div key={derivedMetric.id} className="bg-white border border-purple-200 rounded-lg p-4">
                            {/* 派生指标基本信息 */}
                            <div className="mb-4 pb-3 border-b border-purple-100">
                              <div className="flex items-center justify-between">
                                <div className="flex items-center">
                                  <span className="bg-purple-600 text-white text-xs font-bold rounded-full w-6 h-6 flex items-center justify-center mr-2">
                                    {index + 1}
                                  </span>
                                  <div>
                                    <h5 className="text-sm font-semibold text-gray-900">
                                      {derivedMetric.display_name || derivedMetric.name}
                                    </h5>
                                    {derivedMetric.description && (
                                      <p className="text-xs text-gray-500 mt-0.5">{derivedMetric.description}</p>
                                    )}
                                  </div>
                                </div>
                                <span className="text-xs bg-purple-100 text-purple-800 px-2 py-1 rounded">
                                  派生指标
                                </span>
                              </div>
                              
                              {/* 显示固有过滤条件 */}
                              {derivedMetric.filter_conditions && Object.keys(derivedMetric.filter_conditions).length > 0 && (
                                <div className="mt-3 bg-gray-50 rounded p-2">
                                  <p className="text-xs font-medium text-gray-700 mb-1">固有过滤条件：</p>
                                  <div className="flex flex-wrap gap-2">
                                    {Object.entries(derivedMetric.filter_conditions).map(([field, value]) => (
                                      <span key={field} className="text-xs bg-gray-200 text-gray-700 px-2 py-0.5 rounded">
                                        {field} = {String(value)}
                                      </span>
                                    ))}
                                  </div>
                                </div>
                              )}
                            </div>
                            
                            {/* 时间范围输入 */}
                            {derivedMetric.time_dimension && (
                              <div className="mb-4">
                                <label className="block text-xs font-medium text-gray-700 mb-2">
                                  时间范围 <span className="text-gray-500">(字段: {derivedMetric.time_dimension})</span>
                                </label>
                                <div className="grid grid-cols-2 gap-3">
                                  <div>
                                    <label className="block text-xs text-gray-600 mb-1">开始时间</label>
                                    <input
                                      type="date"
                                      value={derivedMetricConditions[derivedMetric.id]?.time_range?.start || ''}
                                      onChange={(e) => {
                                        setDerivedMetricConditions({
                                          ...derivedMetricConditions,
                                          [derivedMetric.id]: {
                                            ...derivedMetricConditions[derivedMetric.id],
                                            time_range: {
                                              ...derivedMetricConditions[derivedMetric.id]?.time_range,
                                              start: e.target.value,
                                              end: derivedMetricConditions[derivedMetric.id]?.time_range?.end || ''
                                            }
                                          }
                                        });
                                      }}
                                      className="w-full border border-gray-300 rounded px-2 py-1.5 text-xs"
                                    />
                                  </div>
                                  <div>
                                    <label className="block text-xs text-gray-600 mb-1">结束时间</label>
                                    <input
                                      type="date"
                                      value={derivedMetricConditions[derivedMetric.id]?.time_range?.end || ''}
                                      onChange={(e) => {
                                        setDerivedMetricConditions({
                                          ...derivedMetricConditions,
                                          [derivedMetric.id]: {
                                            ...derivedMetricConditions[derivedMetric.id],
                                            time_range: {
                                              ...derivedMetricConditions[derivedMetric.id]?.time_range,
                                              start: derivedMetricConditions[derivedMetric.id]?.time_range?.start || '',
                                              end: e.target.value
                                            }
                                          }
                                        });
                                      }}
                                      className="w-full border border-gray-300 rounded px-2 py-1.5 text-xs"
                                    />
                                  </div>
                                </div>
                              </div>
                            )}
                            
                            {/* 维度筛选输入 */}
                            {derivedMetric.dimensions && derivedMetric.dimensions.length > 0 && (
                              <div>
                                <label className="block text-xs font-medium text-gray-700 mb-2">
                                  维度筛选
                                </label>
                                <div className="space-y-2">
                                  {derivedMetric.dimensions.map(dim => (
                                    <div key={dim} className="flex items-center gap-2">
                                      <label className="text-xs text-gray-700 w-28">{dim}:</label>
                                      <input
                                        type="text"
                                        value={derivedMetricConditions[derivedMetric.id]?.dimensions?.[dim] || ''}
                                        onChange={(e) => {
                                          setDerivedMetricConditions({
                                            ...derivedMetricConditions,
                                            [derivedMetric.id]: {
                                              ...derivedMetricConditions[derivedMetric.id],
                                              dimensions: {
                                                ...derivedMetricConditions[derivedMetric.id]?.dimensions,
                                                [dim]: e.target.value
                                              }
                                            }
                                          });
                                        }}
                                        placeholder="输入维度值..."
                                        className="flex-1 border border-gray-300 rounded px-2 py-1 text-xs"
                                      />
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                  
                  {/* 时间范围（仅派生和复合指标） */}
                  {'time_dimension' in selectedMetric && selectedMetric.time_dimension && (
                    <div className="mb-6">
                      <label className="block text-sm font-medium text-gray-700 mb-2">
                        时间范围 {isCompositeMetric && <span className="text-xs text-gray-500">(用于所有基础指标)</span>}
                      </label>
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs text-gray-600 mb-1">开始时间</label>
                          <input
                            type="date"
                            value={metricTimeRange.start}
                            onChange={(e) => setMetricTimeRange({ ...metricTimeRange, start: e.target.value })}
                            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                          />
                        </div>
                        <div>
                          <label className="block text-xs text-gray-600 mb-1">结束时间</label>
                          <input
                            type="date"
                            value={metricTimeRange.end}
                            onChange={(e) => setMetricTimeRange({ ...metricTimeRange, end: e.target.value })}
                            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                          />
                        </div>
                      </div>
                      {isCompositeMetric && (
                        <p className="text-xs text-blue-600 mt-2">
                          ℹ️ 该时间范围将应用于所有基础指标的计算
                        </p>
                      )}
                    </div>
                  )}

                  {/* 维度选择（仅派生和复合指标） */}
                  {'dimensions' in selectedMetric && selectedMetric.dimensions && selectedMetric.dimensions.length > 0 && (
                    <div className="mb-6">
                      <label className="block text-sm font-medium text-gray-700 mb-2">
                        维度筛选 {isCompositeMetric && <span className="text-xs text-gray-500">(用于所有基础指标)</span>}
                      </label>
                      <div className="space-y-2 border border-gray-300 rounded-lg p-3 bg-white">
                        {selectedMetric.dimensions.map(dim => (
                          <div key={dim} className="flex items-center gap-2">
                            <label className="text-sm text-gray-700 w-24">{dim}:</label>
                            <input
                              type="text"
                              value={metricDimensions[dim] || ''}
                              onChange={(e) => setMetricDimensions({ ...metricDimensions, [dim]: e.target.value })}
                              placeholder="输入维度值..."
                              className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                            />
                          </div>
                        ))}
                      </div>
                      {isCompositeMetric && (
                        <p className="text-xs text-blue-600 mt-2">
                          ℹ️ 这些维度筛选条件将应用于所有基础指标的计算
                        </p>
                      )}
                    </div>
                  )}
                </>
              ) : null;
            })()}
          </>
        )}

        {/* 属性选择（仅普通查询模式） */}
        {queryMode === 'simple' && selectedObjectType && (
          <div className="mb-6">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              选择属性 <span className="text-red-500">*</span>
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

        {/* 关联查询（非指标查询模式） */}
        {queryMode !== 'metric' && (
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
                      <label className="text-xs text-gray-600">选择属性:</label>
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
        )}

        {/* 查询条件（非指标查询模式） */}
        {queryMode !== 'metric' && (
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
                    <option value="">选择属性...</option>
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
                    placeholder="值"
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
                      <option value="">选择属性...</option>
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
                          placeholder="起始值"
                          className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                        />
                        <span className="text-sm text-gray-500">和</span>
                        <input
                          type="text"
                          value={value2 || ''}
                          onChange={(e) => updateFilterExpression(index, [operator as any, fieldPath, value1 || '', e.target.value] as FilterExpression)}
                          placeholder="结束值"
                          className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                        />
                      </>
                    ) : (
                      <input
                        type="text"
                        value={value1 || ''}
                        onChange={(e) => updateFilterExpression(index, [operator as any, fieldPath, e.target.value] as FilterExpression)}
                        placeholder="值"
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
        )}

        {/* 分组功能（仅聚合查询模式） */}
        {queryMode === 'aggregate' && (
          <div className="mb-6">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              分组属性
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

        {/* 聚合指标（仅聚合查询模式） */}
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
                    <option value="">选择属性...</option>
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

        {/* 排序和分页（非指标查询模式） */}
        {queryMode !== 'metric' && (
          <>
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
                      <option value="">选择属性...</option>
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
                <label className="block text-sm font-medium text-gray-700 mb-1">偏移量</label>
                <input
                  type="number"
                  value={query.offset || 0}
                  onChange={(e) => setQuery({ ...query, offset: parseInt(e.target.value) || 0 })}
                  className="w-full border border-gray-300 rounded px-2 py-1 text-sm"
                  min="0"
                />
              </div>
            </div>
          </>
        )}

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
              value={(() => {
                if (queryMode === 'metric') {
                  const selectedMetric = atomicMetrics.find(m => m.id === selectedMetricId) || 
                                        metricDefinitions.find(m => m.id === selectedMetricId);
                  
                  // 构建实际查询结构（OntologyQuery）
                  let ontologyQuery: any = {};
                  
                  if (selectedMetric && 'metric_type' in selectedMetric && selectedMetric.metric_type === 'derived') {
                    // 派生指标：显示构建后的 OntologyQuery 结构
                    const derivedMetric = selectedMetric as MetricDefinition;
                    
                    // FROM
                    if (derivedMetric.business_scope) {
                      if (derivedMetric.business_scope.type === 'single') {
                        ontologyQuery.from = derivedMetric.business_scope.base_object_type;
                      } else if (derivedMetric.business_scope.type === 'multi') {
                        ontologyQuery.from = derivedMetric.business_scope.from;
                        if (derivedMetric.business_scope.links) {
                          ontologyQuery.links = derivedMetric.business_scope.links;
                        }
                      }
                    }
                    
                    // METRICS（从原子指标获取）
                    const atomicMetric = atomicMetrics.find(m => m.id === derivedMetric.atomic_metric_id);
                    if (atomicMetric) {
                      const func = atomicMetric.aggregation_function?.toLowerCase() || '';
                      const field = atomicMetric.aggregation_field || '*';
                      if (func === 'count' || func === 'distinct_count') {
                        ontologyQuery.metrics = [['count', '*', null]];
                      } else if (field && field !== '*') {
                        ontologyQuery.metrics = [[func, field, null]];
                      }
                    }
                    
                    // FILTER（过滤条件 + 查询维度 + 时间范围）
                    const filterExpressions: any[] = [];
                    
                    // 派生指标的过滤条件
                    if (derivedMetric.filter_conditions) {
                      Object.entries(derivedMetric.filter_conditions).forEach(([field, value]) => {
                        if (value && !(typeof value === 'string' && value.trim() === '')) {
                          filterExpressions.push(['=', field, value]);
                        }
                      });
                    }
                    
                    // 查询维度
                    Object.entries(metricDimensions).forEach(([field, value]) => {
                      if (value && value.trim() !== '') {
                        filterExpressions.push(['=', field, value]);
                      }
                    });
                    
                    // 时间范围
                    if (metricTimeRange.start && metricTimeRange.end && derivedMetric.time_dimension) {
                      filterExpressions.push(['>=', derivedMetric.time_dimension, metricTimeRange.start]);
                      filterExpressions.push(['<=', derivedMetric.time_dimension, metricTimeRange.end]);
                    }
                    
                    if (filterExpressions.length > 0) {
                      ontologyQuery.filter = filterExpressions;
                    }
                    
                    // GROUP BY
                    const groupBy: string[] = [];
                    if (derivedMetric.time_dimension) {
                      groupBy.push(derivedMetric.time_dimension);
                    }
                    if (derivedMetric.dimensions) {
                      groupBy.push(...derivedMetric.dimensions);
                    }
                    if (groupBy.length > 0) {
                      ontologyQuery.group_by = groupBy;
                    }
                    
                    // ORDER BY
                    if (derivedMetric.time_dimension) {
                      ontologyQuery.orderBy = [{ field: derivedMetric.time_dimension, direction: 'ASC' }];
                    }
                  } else {
                    // 原子指标或未找到：显示指标查询 JSON
                    ontologyQuery = {
                      metric_id: selectedMetricId,
                      metric_info: selectedMetric ? {
                        id: selectedMetric.id,
                        name: selectedMetric.name,
                        display_name: 'display_name' in selectedMetric ? selectedMetric.display_name : undefined,
                        type: selectedMetricType || ('metric_type' in selectedMetric ? selectedMetric.metric_type : undefined),
                        ...(selectedMetric && 'business_process' in selectedMetric ? {
                          business_process: selectedMetric.business_process,
                          aggregation_function: selectedMetric.aggregation_function,
                          aggregation_field: selectedMetric.aggregation_field,
                          unit: selectedMetric.unit
                        } : {}),
                        ...(selectedMetric && 'business_scope' in selectedMetric ? {
                          business_scope: selectedMetric.business_scope,
                          time_dimension: selectedMetric.time_dimension,
                          time_granularity: selectedMetric.time_granularity,
                          dimensions: selectedMetric.dimensions
                        } : {})
                      } : undefined,
                      time_range: (metricTimeRange.start && metricTimeRange.end) ? {
                        start: metricTimeRange.start,
                        end: metricTimeRange.end
                      } : undefined,
                      dimensions: Object.keys(metricDimensions).length > 0 ? 
                        Object.fromEntries(
                          Object.entries(metricDimensions).filter(([_, v]) => v && v.trim() !== '')
                        ) : undefined
                    };
                  }
                  
                  return JSON.stringify(ontologyQuery, null, 2);
                } else {
                  return JSON.stringify({
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
                  }, null, 2);
                }
              })()}
              onChange={(e) => {
                if (queryMode === 'metric') {
                  // 指标查询模式下不允许编辑 JSON
                  return;
                }
                try {
                  const parsed = JSON.parse(e.target.value);
                  // 如果 metrics 是数组格式，转换为对象格式
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
              readOnly={queryMode === 'metric'}
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
          disabled={loading || 
            (queryMode === 'metric' && !selectedMetricId) ||
            (queryMode !== 'metric' && !query.object && !query.from) ||
            (queryMode === 'simple' && (!query.select || query.select.length === 0)) ||
            (queryMode === 'aggregate' && (!query.group_by || query.group_by.length === 0) && (!query.metrics || query.metrics.length === 0))}
          className="w-full flex items-center justify-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
        >
          {loading ? (
            <>
              <ArrowPathIcon className="w-5 h-5 animate-spin mr-2" />
              执行中...
            </>
          ) : (
            <>
              <PlayIcon className="w-5 h-5 mr-2" />
              执行查询
            </>
          )}
        </button>
      </div>

      {/* 右侧：查询结果 */}
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

        {metricResult ? (
          <div>
            <div className="mb-4 p-3 bg-blue-50 border border-blue-200 rounded-lg">
              <div className="text-sm">
                <div className="font-semibold text-blue-900 mb-1">指标: {metricResult.metricName}</div>
                {metricResult.timeGranularity && (
                  <div className="text-blue-700">时间粒度: {metricResult.timeGranularity}</div>
                )}
                <div className="text-blue-700">计算时间: {new Date(metricResult.calculatedAt).toLocaleString()}</div>
              </div>
            </div>
            
            {metricResult.sql && (
              <div className="mb-4">
                <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
                  <div className="flex items-center justify-between mb-2">
                    <h4 className="text-sm font-semibold text-gray-700">执行的 SQL</h4>
                    <button
                      onClick={() => {
                        navigator.clipboard.writeText(metricResult.sql || '');
                        alert('SQL 已复制到剪贴板');
                      }}
                      className="text-xs text-blue-600 hover:text-blue-800"
                    >
                      复制
                    </button>
                  </div>
                  <pre className="text-xs text-gray-800 bg-white p-3 rounded border border-gray-300 overflow-x-auto font-mono">
                    {metricResult.sql}
                  </pre>
                </div>
              </div>
            )}
            
            {viewMode === 'table' ? (
              <div className="overflow-x-auto">
                {metricResult.results && metricResult.results.length > 0 ? (
                  <table className="min-w-full divide-y divide-gray-200 bg-white rounded-lg shadow">
                    <thead className="bg-gray-50">
                      <tr>
                        {metricResult.timeGranularity && (
                          <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                            时间
                          </th>
                        )}
                        {metricResult.results[0]?.dimensionValues && Object.keys(metricResult.results[0].dimensionValues).map(dim => (
                          <th
                            key={dim}
                            className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
                          >
                            {dim}
                          </th>
                        ))}
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                          指标值
                        </th>
                        {metricResult.results[0]?.unit && (
                          <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                            单位
                          </th>
                        )}
                        {metricResult.results[0]?.comparisons && Object.keys(metricResult.results[0].comparisons).length > 0 && (
                          <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                            对比
                          </th>
                        )}
                      </tr>
                    </thead>
                    <tbody className="bg-white divide-y divide-gray-200">
                      {metricResult.results.map((point, idx) => (
                      <tr key={idx} className="hover:bg-gray-50">
                        {metricResult.timeGranularity && (
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                            {point.timeValue || '-'}
                          </td>
                        )}
                        {point.dimensionValues && Object.entries(point.dimensionValues).map(([key, value]) => (
                          <td key={key} className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                            {String(value)}
                          </td>
                        ))}
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                          {point.metricValue}
                        </td>
                        {point.unit && (
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                            {point.unit}
                          </td>
                        )}
                        {point.comparisons && Object.keys(point.comparisons).length > 0 && (
                          <td className="px-6 py-4 text-sm text-gray-600">
                            {Object.entries(point.comparisons).map(([key, comp]) => (
                              <div key={key} className="text-xs">
                                {key}: {comp.display} ({comp.description})
                              </div>
                            ))}
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
                ) : (
                  <div className="p-8 text-center text-gray-500 bg-white rounded-lg shadow">
                    <p>查询结果为空，没有数据返回。</p>
                  </div>
                )}
              </div>
            ) : (
              <pre className="p-4 overflow-auto bg-gray-50 text-sm rounded border border-gray-200">
                {JSON.stringify(metricResult, null, 2)}
              </pre>
            )}
          </div>
        ) : results ? (
          <div>
            <div className="mb-4 text-sm text-gray-600">
              共 {results.rowCount} 条结果
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
