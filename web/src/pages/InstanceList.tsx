import { useEffect, useState, useRef } from 'react';
import { useParams, Link, useSearchParams, useNavigate } from 'react-router-dom';
import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';
import type { Instance, ObjectType, QueryRequest, FilterExpression, OrderBy } from '../api/client';
import { instanceApi, schemaApi, queryApi, databaseApi, mappingApi } from '../api/client';
import { useWorkspace } from '../WorkspaceContext';
import { PlusIcon, PencilIcon, TrashIcon, CloudArrowDownIcon, XMarkIcon, LinkIcon, ArrowDownTrayIcon, FunnelIcon, MagnifyingGlassIcon, CircleStackIcon, ServerIcon, ChartBarIcon, TableCellsIcon, Cog6ToothIcon, ArrowTopRightOnSquareIcon } from '@heroicons/react/24/outline';
import InstanceForm from '../components/InstanceForm';
import ButtonGroup from '../components/ButtonGroup';
import DataMappingDialog from '../components/DataMappingDialog';
import PropertyStatistics, { type AnalysisDisplayOptions } from '../components/PropertyStatistics';
import { ToastContainer, useToast } from '../components/Toast';

export default function InstanceList() {
  const { objectType } = useParams<{ objectType: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const mappingId = searchParams.get('mappingId');
  const { selectedWorkspace } = useWorkspace();
  const [instances, setInstances] = useState<Instance[]>([]);
  const [objectTypeDef, setObjectTypeDef] = useState<ObjectType | null>(null);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingInstance, setEditingInstance] = useState<Instance | null>(null);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const [fromMapping, setFromMapping] = useState(false);
  const [showSyncDialog, setShowSyncDialog] = useState(false);
  const [databases, setDatabases] = useState<Instance[]>([]);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string>('');
  const [syncing, setSyncing] = useState(false);
  const [showMappingDialog, setShowMappingDialog] = useState(false);
  const [showSyncExtractDialog, setShowSyncExtractDialog] = useState(false);
  const [mappings, setMappings] = useState<any[]>([]);
  const [selectedMappingId, setSelectedMappingId] = useState<string>('');
  const [extracting, setExtracting] = useState(false);
  const [showFilters, setShowFilters] = useState(false);
  const [filters, setFilters] = useState<Array<{ property: string; value: string }>>([]);
  const [availableMappings, setAvailableMappings] = useState<any[]>([]);
  const [queryMode, setQueryMode] = useState<'mapping' | 'storage'>('storage'); // 'mapping' 表示映射数据查询，'storage' 表示实例存储查询
  const [viewMode, setViewMode] = useState<'properties' | 'list'>('properties'); // 'properties' 表示属性分析视图，'list' 表示数据列表视图
  const [allInstances, setAllInstances] = useState<Instance[]>([]); // 用于统计分析的所有实例数据
  const [loadingStats, setLoadingStats] = useState(false);
  const [showAnalysisSettings, setShowAnalysisSettings] = useState(false);
  const [analysisDisplayOptions, setAnalysisDisplayOptions] = useState<AnalysisDisplayOptions>({
    showSummary: true,
    showNumericStats: true,
    showChart: true,
  });
  const [showPropertyFilter, setShowPropertyFilter] = useState(false);
  const [visibleProperties, setVisibleProperties] = useState<Set<string>>(new Set());
  const [showExportMenu, setShowExportMenu] = useState(false);
  const analysisRef = useRef<HTMLDivElement>(null);
  
  // 查询配置相关状态（用于UI编辑，不立即触发查询）
  const [queryFilterExpressions, setQueryFilterExpressions] = useState<FilterExpression[]>([]);
  const [queryOrderBy, setQueryOrderBy] = useState<OrderBy[]>([]);
  const [queryLimit, setQueryLimit] = useState(20);
  const [queryOffset, setQueryOffset] = useState(0);
  const [querySelectedFields, setQuerySelectedFields] = useState<Set<string>>(new Set());
  const [showQueryConfig, setShowQueryConfig] = useState(false);
  
  // 实际应用的查询配置（用于查询）
  const [appliedFilterExpressions, setAppliedFilterExpressions] = useState<FilterExpression[]>([]);
  const [appliedOrderBy, setAppliedOrderBy] = useState<OrderBy[]>([]);
  const [appliedLimit, setAppliedLimit] = useState(20);
  const [appliedOffset, setAppliedOffset] = useState(0);
  const [appliedSelectedFields, setAppliedSelectedFields] = useState<Set<string>>(new Set());

  const limit = 20;
  const { toasts, showToast, removeToast } = useToast();

  // Initialize visible properties when object type definition is loaded
  useEffect(() => {
    if (objectTypeDef?.properties) {
      setVisibleProperties(new Set(objectTypeDef.properties.map(p => p.name)));
    }
  }, [objectTypeDef]);

  // 判断是否为系统对象类型（不需要关联按钮）
  const isSystemObjectType = (type: string | undefined) => {
    return type === 'database' || type === 'table' || type === 'column' || type === 'mapping';
  };

  // 判断是否为 system 工作空间
  const isSystemWorkspace = () => {
    if (!selectedWorkspace) return false;
    const wsName = selectedWorkspace.name?.toLowerCase();
    const wsDisplayName = selectedWorkspace.display_name?.toLowerCase();
    return wsName === 'system' || wsDisplayName === 'system' || selectedWorkspace.id === 'system';
  };

  // 查询条件管理函数
  const addFilterExpression = () => {
    setQueryFilterExpressions([...queryFilterExpressions, ['=', '', ''] as FilterExpression]);
  };

  const removeFilterExpression = (index: number) => {
    setQueryFilterExpressions(queryFilterExpressions.filter((_, i) => i !== index));
  };

  const updateFilterExpression = (index: number, expr: FilterExpression) => {
    const updated = [...queryFilterExpressions];
    updated[index] = expr;
    setQueryFilterExpressions(updated);
  };

  // 排序管理函数
  const addOrderBy = () => {
    setQueryOrderBy([...queryOrderBy, { field: '', direction: 'ASC' }]);
  };

  const removeOrderBy = (index: number) => {
    setQueryOrderBy(queryOrderBy.filter((_, i) => i !== index));
  };

  const updateOrderBy = (index: number, field: string, direction: 'ASC' | 'DESC') => {
    const orderBy = [...queryOrderBy];
    orderBy[index] = { field, direction };
    setQueryOrderBy(orderBy);
  };

  // 加载可用的映射配置
  useEffect(() => {
    if (objectType && !isSystemObjectType(objectType)) {
      loadAvailableMappings();
    }
  }, [objectType]);


  // 当对象类型改变时，重置查询配置
  useEffect(() => {
    if (objectType) {
      setQueryFilterExpressions([]);
      setQueryOrderBy([]);
      setQueryLimit(20);
      setQueryOffset(0);
      setQuerySelectedFields(new Set());
      setAppliedFilterExpressions([]);
      setAppliedOrderBy([]);
      setAppliedLimit(20);
      setAppliedOffset(0);
      setAppliedSelectedFields(new Set());
      setOffset(0);
    }
  }, [objectType]);

  // 当对象类型定义加载时，初始化字段选择（默认全选）
  useEffect(() => {
    if (objectTypeDef?.properties) {
      const allFields = new Set(objectTypeDef.properties.map(p => p.name));
      if (querySelectedFields.size === 0) {
        setQuerySelectedFields(new Set(allFields));
      }
      if (appliedSelectedFields.size === 0) {
        setAppliedSelectedFields(new Set(allFields));
      }
    }
  }, [objectTypeDef]);

  useEffect(() => {
    if (objectType) {
      // 如果当前是属性分析视图，加载所有数据用于统计分析
      if (viewMode === 'properties') {
        loadAllDataForStats();
      } else {
        // 列表视图时加载分页数据
        loadData();
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [objectType, offset, mappingId, queryMode, viewMode, appliedFilterExpressions, appliedOrderBy, appliedLimit, appliedOffset, appliedSelectedFields]);

  // 当筛选条件改变时，重置offset并重新加载数据（仅在列表视图）
  useEffect(() => {
    if (objectType && viewMode === 'list') {
      setOffset(0);
      setQueryOffset(0);
      setAppliedOffset(0);
      // 延迟加载，避免在filters变化时立即触发
      const timer = setTimeout(() => {
        loadData();
      }, 300);
      return () => clearTimeout(timer);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters, viewMode]);

  // 加载可用的映射配置
  const loadAvailableMappings = async () => {
    if (!objectType || isSystemObjectType(objectType)) return;
    try {
      const mappingsList = await mappingApi.getByObjectType(objectType);
      setAvailableMappings(mappingsList);
      // 如果URL中有mappingId，设置为映射查询模式
      if (mappingId && mappingsList.some(m => m.id === mappingId)) {
        setQueryMode('mapping');
      } else {
        // 如果没有mappingId，设置为存储查询模式
        setQueryMode('storage');
      }
    } catch (error) {
      console.error('Failed to load mappings:', error);
      setAvailableMappings([]);
      // 出错时默认使用存储查询模式
      setQueryMode('storage');
    }
  };

  // 加载所有数据用于统计分析
  const loadAllDataForStats = async () => {
    if (!objectType) return;
    try {
      setLoading(true);
      setLoadingStats(true);
      
      // 首先获取对象类型定义
      const objectTypeData = await schemaApi.getObjectType(objectType);
      setObjectTypeDef(objectTypeData); // 设置对象类型定义，这样页面才能渲染
      
      // 根据查询模式决定使用哪种查询方式
      if (queryMode === 'mapping' && availableMappings.length > 0) {
        // 映射数据查询：加载所有数据（使用较大的 limit）
        const targetMappingId = mappingId || availableMappings[0]?.id;
        if (targetMappingId) {
          try {
            const instancesData = await instanceApi.listWithMapping(objectType, targetMappingId, 0, 5000);
            setAllInstances(instancesData.items);
            setFromMapping(true);
            setLoading(false);
            setLoadingStats(false);
            return;
          } catch (error) {
            console.warn('Failed to load mapping data for stats, trying smaller limit:', error);
            // 尝试更小的 limit
            try {
              const instancesData = await instanceApi.listWithMapping(objectType, targetMappingId, 0, 1000);
              setAllInstances(instancesData.items);
              setFromMapping(true);
              setLoading(false);
              setLoadingStats(false);
              return;
            } catch (error2) {
              console.error('Failed to load mapping data with smaller limit:', error2);
              // 如果映射查询失败，继续尝试实例存储查询
            }
          }
        }
      }
      
      // 实例存储查询：优先使用直接 API 调用（更稳定）
      setFromMapping(false);
      try {
        // 先尝试使用较大的 limit
        const instancesData = await instanceApi.list(objectType, 0, 5000);
        setAllInstances(instancesData.items);
        setLoading(false);
        setLoadingStats(false);
      } catch (apiError) {
        console.warn('Direct API call failed, trying smaller limit:', apiError);
        try {
          // 尝试更小的 limit
          const instancesData = await instanceApi.list(objectType, 0, 1000);
          setAllInstances(instancesData.items);
          setLoading(false);
          setLoadingStats(false);
        } catch (apiError2) {
          console.warn('Direct API call with smaller limit failed, trying OntologyQuery:', apiError2);
          // 最后尝试使用 OntologyQuery
          try {
            const selectFields: string[] = ['id'];
            if (objectTypeData.properties) {
              objectTypeData.properties.forEach(prop => {
                selectFields.push(prop.name);
              });
            }
            
            const queryRequest: QueryRequest = {
              object: objectType,
              select: selectFields,
              limit: 1000, // 使用较小的 limit
              offset: 0,
            };
            
            const queryResult = await queryApi.execute(queryRequest);
            const instances: Instance[] = queryResult.rows.map((row: Record<string, any>) => ({
              id: row.id || '',
              ...row,
            }));
            
            setAllInstances(instances);
            setLoading(false);
            setLoadingStats(false);
          } catch (queryError) {
            console.error('All methods failed to load data for statistics:', queryError);
            setAllInstances([]);
            setLoading(false);
            setLoadingStats(false);
            showToast('加载统计数据失败，请稍后重试', 'error');
          }
        }
      }
    } catch (error) {
      console.error('Failed to load all data for statistics:', error);
      setAllInstances([]);
      setLoading(false);
      setLoadingStats(false);
      showToast('加载统计数据失败', 'error');
    }
  };

  const loadData = async () => {
    if (!objectType) return;
    try {
      setLoading(true);
      
      // 首先获取对象类型定义
      const objectTypeData = await schemaApi.getObjectType(objectType);
      setObjectTypeDef(objectTypeData);
      
      // 根据查询模式决定使用哪种查询方式
      // 映射数据查询：使用 mappingId 从数据库实时查询
      if (queryMode === 'mapping' && availableMappings.length > 0) {
        // 优先使用URL中的mappingId，否则使用第一个可用的mapping
        const targetMappingId = mappingId || availableMappings[0]?.id;
        if (targetMappingId) {
          // 使用 mappingId 查询数据库（通过 MappedDataService）
          const instancesData = await instanceApi.listWithMapping(objectType, targetMappingId, offset, limit);
          setInstances(instancesData.items);
          setTotal(instancesData.total);
          setFromMapping(true);
          return;
        }
      }
      
      // 实例存储查询：使用 instance 从本地存储查询（OntologyQuery 或直接 API）
      setFromMapping(false);
      
      // 构建筛选条件
      const filterParams: Record<string, any> = {};
      filters.forEach(filter => {
        if (filter.property && filter.value) {
          filterParams[filter.property] = filter.value;
        }
      });
      
      // 优先使用 OntologyQuery，如果失败则回退到直接 API 调用
      try {
        // 构建 ontology query：查询当前 object type 的所有实例
        const selectFields: string[] = ['id']; // 首先添加 id 字段（查询系统会自动映射 id_column 为 id）
        
        // 添加选中的属性字段（如果未选择任何字段，则选择所有字段）
        if (objectTypeData.properties) {
          if (appliedSelectedFields.size > 0) {
            // 只选择已选中的字段
            objectTypeData.properties.forEach(prop => {
              if (appliedSelectedFields.has(prop.name)) {
                selectFields.push(prop.name);
              }
            });
          } else {
            // 如果没有选择任何字段，默认选择所有字段
            objectTypeData.properties.forEach(prop => {
              selectFields.push(prop.name);
            });
          }
        }
        
        // 构建过滤条件（转换为 OntologyQuery 格式）
        const queryFilters: FilterExpression[] = [];
        Object.entries(filterParams).forEach(([key, value]) => {
          queryFilters.push(['=' as const, key, value]);
        });
        
        // 合并查询配置中的过滤条件（使用应用的配置）
        const allFilters: FilterExpression[] = [...queryFilters, ...appliedFilterExpressions.filter(expr => {
          // 只保留有效的过滤表达式（有字段和值）
          const field = expr[1] as string;
          const value = expr[2];
          return field && value !== undefined && value !== null && value !== '';
        })];
        
        const queryRequest: QueryRequest = {
          object: objectType,
          select: selectFields,
          limit: appliedLimit || limit,
          offset: appliedOffset !== undefined ? appliedOffset : offset,
          ...(allFilters.length > 0 && { filter: allFilters }),
          ...(appliedOrderBy.length > 0 && { orderBy: appliedOrderBy }),
        };
        
        // 执行 ontology query
        const queryResult = await queryApi.execute(queryRequest);
        
        // 将查询结果转换为实例列表格式
        const instances: Instance[] = queryResult.rows.map((row: Record<string, any>) => {
          const instance: Instance = {
            id: row.id || '',
            ...row,
          };
          return instance;
        });
        
        setInstances(instances);
        setTotal(queryResult.rowCount || instances.length);
        return;
      } catch (queryError) {
        console.warn('OntologyQuery failed, falling back to direct API:', queryError);
      }
      
      // 回退到直接 API 调用（支持筛选）
      // 注意：直接 API 调用不支持 links 和 orderBy，所以只在 OntologyQuery 失败时使用
      const instancesData = await instanceApi.list(
        objectType, 
        appliedOffset !== undefined ? appliedOffset : offset, 
        appliedLimit || limit, 
        Object.keys(filterParams).length > 0 ? filterParams : undefined
      );
      setInstances(instancesData.items);
      setTotal(instancesData.total);
    } catch (error) {
      console.error('Failed to load data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingInstance(null);
    setShowForm(true);
  };

  const handleEdit = (instance: Instance) => {
    setEditingInstance(instance);
    setShowForm(true);
  };

  const handleDelete = async (id: string) => {
    if (!objectType) return;
    if (!confirm('Are you sure you want to delete this instance?')) return;
    try {
      await instanceApi.delete(objectType, id);
      loadData();
    } catch (error) {
      console.error('Failed to delete instance:', error);
      alert('Failed to delete instance');
    }
  };

  const handleFormClose = () => {
    setShowForm(false);
    setEditingInstance(null);
    loadData();
  };

  const handleAddFilter = () => {
    setFilters([...filters, { property: '', value: '' }]);
  };

  const handleRemoveFilter = (index: number) => {
    setFilters(filters.filter((_, i) => i !== index));
  };

  const handleFilterChange = (index: number, field: 'property' | 'value', value: string) => {
    const newFilters = [...filters];
    newFilters[index] = { ...newFilters[index], [field]: value };
    setFilters(newFilters);
  };

  const handleClearFilters = () => {
    setFilters([]);
  };

  const handleSyncClick = async () => {
    if (objectType !== 'database') return;
    
    try {
      // 加载所有数据库实例供选择
      const dbList = await instanceApi.list('database', 0, 100);
      setDatabases(dbList.items);
      // 如果只有一个数据源，自动选择
      if (dbList.items.length === 1) {
        setSelectedDatabaseId(dbList.items[0].id);
      }
      setShowSyncDialog(true);
    } catch (error) {
      console.error('Failed to load databases:', error);
      alert('无法加载数据源列表');
    }
  };

  const handleSync = async () => {
    if (!selectedDatabaseId) {
      showToast('请选择数据源', 'error');
      return;
    }

    try {
      setSyncing(true);
      const result = await databaseApi.syncTables(selectedDatabaseId);
      const message = `同步完成！\n创建表: ${result.tables_created}\n创建列: ${result.columns_created}\n更新列: ${result.columns_updated}`;
      showToast(message, 'success');
      setShowSyncDialog(false);
      setSelectedDatabaseId('');
      loadData(); // 刷新列表
    } catch (error: any) {
      console.error('Failed to sync tables:', error);
      const errorMessage = '同步失败: ' + (error.response?.data?.message || error.message);
      showToast(errorMessage, 'error');
    } finally {
      setSyncing(false);
    }
  };

  const handleSyncExtractClick = async () => {
    if (!objectType || isSystemObjectType(objectType)) return;

    try {
      // 加载该对象类型的所有映射
      const mappingsList = await mappingApi.getByObjectType(objectType);
      
      if (mappingsList.length === 0) {
        alert('该对象类型尚未配置数据映射，请先配置数据映射');
        return;
      }

      // 为每个映射加载表信息
      const mappingsWithTableInfo = await Promise.all(
        mappingsList.map(async (mapping) => {
          try {
            if (mapping.table_id) {
              const table = await instanceApi.get('table', mapping.table_id);
              return { ...mapping, table_name: table.name || mapping.table_id };
            }
            return mapping;
          } catch (error) {
            console.error(`Failed to load table info for ${mapping.table_id}:`, error);
            return { ...mapping, table_name: mapping.table_id || '未知表' };
          }
        })
      );

      setMappings(mappingsWithTableInfo);

      // 如果只有一个映射，直接使用
      if (mappingsWithTableInfo.length === 1) {
        setSelectedMappingId(mappingsWithTableInfo[0].id);
        handleExtract(mappingsWithTableInfo[0].id);
      } else {
        // 多个映射，显示选择对话框
        setShowSyncExtractDialog(true);
      }
    } catch (error: any) {
      console.error('Failed to load mappings:', error);
      alert('加载映射配置失败: ' + (error.response?.data?.message || error.message));
    }
  };

  const handleExtract = async (mappingId?: string) => {
    const targetMappingId = mappingId || selectedMappingId;
    if (!targetMappingId || !objectType) {
      alert('请选择映射配置');
      return;
    }

    try {
      setExtracting(true);
      await instanceApi.syncFromMapping(objectType, targetMappingId);
      showToast('数据抽取完成！已从数据库同步数据到实例中。', 'success');
      setShowSyncExtractDialog(false);
      setSelectedMappingId('');
      loadData(); // 刷新列表
    } catch (error: any) {
      console.error('Failed to extract data:', error);
      alert('数据抽取失败: ' + (error.response?.data?.message || error.message));
    } finally {
      setExtracting(false);
    }
  };

  const handleExport = async (type: 'png' | 'pdf') => {
    if (!analysisRef.current) return;
    
    try {
      showToast('正在生成导出文件...', 'info');
      // 等待一点时间确保UI渲染完成
      await new Promise(resolve => setTimeout(resolve, 100));
      
      const canvas = await html2canvas(analysisRef.current, {
        useCORS: true,
        logging: false,
        backgroundColor: '#ffffff',
        scale: 2 // 提高清晰度
      });
      
      const fileName = `${objectTypeDef?.name || 'analysis'}_properties.${type}`;
      
      if (type === 'png') {
        const link = document.createElement('a');
        link.download = fileName;
        link.href = canvas.toDataURL('image/png');
        link.click();
      } else {
        const imgData = canvas.toDataURL('image/png');
        const imgWidth = canvas.width;
        const imgHeight = canvas.height;
        
        // 创建与图片尺寸匹配的PDF
        const pdf = new jsPDF({
          orientation: imgWidth > imgHeight ? 'landscape' : 'portrait',
          unit: 'px',
          format: [imgWidth, imgHeight]
        });
        
        pdf.addImage(imgData, 'PNG', 0, 0, imgWidth, imgHeight);
        pdf.save(fileName);
      }
      
      setShowExportMenu(false);
      showToast('导出成功', 'success');
    } catch (error) {
      console.error('Export failed:', error);
      showToast('导出失败', 'error');
    }
  };

  // 在属性分析视图下，即使 loading 为 true，只要 objectTypeDef 已加载，就显示页面
  // 在列表视图下，需要等待 loading 完成
  if (viewMode === 'list' && loading) {
    return <div className="text-center py-12">Loading...</div>;
  }

  if (!objectTypeDef) {
    return <div className="text-center py-12">Loading...</div>;
  }

  return (
    <div className="flex flex-col h-screen overflow-hidden">
      <div className="flex-shrink-0 sticky top-0 z-10 bg-white border-b border-gray-200 pb-6 pt-4 px-4">
        <div className="flex items-center justify-between mb-6">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold text-gray-900">{objectTypeDef.name}</h1>
              {objectTypeDef.url && (
                <a
                  href={objectTypeDef.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-gray-500 hover:text-blue-600 transition-colors"
                  title="查看外部画像"
                >
                  <ArrowTopRightOnSquareIcon className="w-6 h-6" />
                </a>
              )}
            {queryMode === 'mapping' && fromMapping && (
              <span className="px-3 py-1 bg-blue-100 text-blue-700 rounded-lg text-sm font-medium">
                实例存储查询
              </span>
            )}
            {queryMode === 'storage' && !fromMapping && (
              <span className="px-3 py-1 bg-gray-100 text-gray-700 rounded-lg text-sm font-medium">
                映射数据查询
              </span>
            )}
            </div>
            {objectTypeDef.description && (
              <p className="text-gray-600 mt-1">{objectTypeDef.description}</p>
            )}
            {queryMode === 'mapping' && fromMapping && (
              <p className="text-sm text-gray-500 mt-1">
                数据来自本地实例存储
              </p>
            )}
            {queryMode === 'storage' && !fromMapping && (
              <p className="text-sm text-gray-500 mt-1">
               数据来自数据库映射，实时查询 
              </p>
            )}
          </div>
          <div className="flex gap-2">
          {/* 视图切换按钮 */}
          <ButtonGroup
            value={viewMode}
            onChange={(val) => setViewMode(val as 'properties' | 'list')}
            options={[
              { value: 'properties', label: '属性分析', icon: ChartBarIcon, title: '属性分析视图' },
              { value: 'list', label: '数据列表', icon: TableCellsIcon, title: '数据列表视图' },
            ]}
          />
          {/* 查询模式切换按钮组 */}
          {!isSystemObjectType(objectType) && availableMappings.length > 0 && (
            <ButtonGroup
              value={queryMode}
              onChange={(val) => {
                const mode = val as 'mapping' | 'storage';
                if (mode === 'storage') {
                  // 切换到实例存储查询模式
                  setQueryMode('storage');
                  setOffset(0);
                  const newParams = new URLSearchParams(searchParams);
                  newParams.delete('mappingId');
                  navigate(`/instances/${objectType}${newParams.toString() ? '?' + newParams.toString() : ''}`, { replace: true });
                } else {
                  // 切换到映射数据查询模式
                  setQueryMode('mapping');
                  setOffset(0);
                  const newParams = new URLSearchParams(searchParams);
                  const targetMappingId = mappingId || availableMappings[0]?.id;
                  if (targetMappingId) {
                    newParams.set('mappingId', targetMappingId);
                  }
                  navigate(`/instances/${objectType}${newParams.toString() ? '?' + newParams.toString() : ''}`, { replace: true });
                }
              }}
              options={[
                { value: 'storage', label: '映射数据', icon: CircleStackIcon, title: '映射数据查询：使用 mappingId 从数据库实时查询' },
                { value: 'mapping', label: '实例存储', icon: ServerIcon, title: '实例存储查询：使用 instance 从本地存储查询' },
              ]}
              activeClassName="bg-indigo-600 text-white"
            />
          )}
          {objectType === 'database' && (
            <button
              onClick={handleSyncClick}
              className="flex items-center px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
            >
              <CloudArrowDownIcon className="w-5 h-5 mr-2" />
              同步表信息
            </button>
          )}
          {!isSystemObjectType(objectType) && !isSystemWorkspace() && (
            <>
              <button
                onClick={() => setShowMappingDialog(true)}
                className="flex items-center px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors"
              >
                <LinkIcon className="w-5 h-5 mr-2" />
                关联数据源
              </button>
              <button
                onClick={handleSyncExtractClick}
                className="flex items-center px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700 transition-colors"
              >
                <ArrowDownTrayIcon className="w-5 h-5 mr-2" />
                同步抽取
              </button>
            </>
          )}
          {viewMode === 'list' && !fromMapping && (
            <button
              onClick={() => setShowFilters(!showFilters)}
              className={`flex items-center px-4 py-2 rounded-lg transition-colors ${
                showFilters || filters.length > 0
                  ? 'bg-blue-600 text-white hover:bg-blue-700'
                  : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
              }`}
            >
              <FunnelIcon className="w-5 h-5 mr-2" />
              筛选
              {filters.length > 0 && (
                <span className="ml-2 px-2 py-0.5 bg-white text-blue-600 rounded-full text-xs font-medium">
                  {filters.filter(f => f.property && f.value).length}
                </span>
              )}
            </button>
          )}
          <button
            onClick={handleCreate}
            className="flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <PlusIcon className="w-5 h-5 mr-2" />
            创建实例
          </button>
        </div>
      </div>
      </div>

      {/* 可滚动内容区域 */}
      <div className="flex-1 overflow-y-auto px-4 pb-4">
      {/* 筛选面板 - 仅在列表视图显示 */}
      {viewMode === 'list' && showFilters && !fromMapping && objectTypeDef && (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-4">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-semibold text-gray-900 flex items-center">
              <MagnifyingGlassIcon className="w-5 h-5 mr-2" />
              多条件检索
            </h3>
            <div className="flex gap-2">
              {filters.length > 0 && (
                <button
                  onClick={handleClearFilters}
                  className="px-3 py-1 text-sm text-gray-600 hover:text-gray-800"
                >
                  清空
                </button>
              )}
              <button
                onClick={() => setShowFilters(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>
          </div>
          
          <div className="space-y-3">
            {filters.map((filter, index) => (
              <div key={index} className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                <select
                  value={filter.property}
                  onChange={(e) => handleFilterChange(index, 'property', e.target.value)}
                  className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                >
                  <option value="">-- 选择字段 --</option>
                  {objectTypeDef.properties.map((prop) => (
                    <option key={prop.name} value={prop.name}>
                      {prop.name} ({prop.data_type})
                    </option>
                  ))}
                </select>
                <input
                  type="text"
                  value={filter.value}
                  onChange={(e) => handleFilterChange(index, 'value', e.target.value)}
                  placeholder="输入筛选值..."
                  className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                />
                <button
                  onClick={() => handleRemoveFilter(index)}
                  className="px-3 py-2 text-red-600 hover:text-red-800 hover:bg-red-50 rounded-lg transition-colors"
                  title="删除筛选条件"
                >
                  <XMarkIcon className="w-5 h-5" />
                </button>
              </div>
            ))}
            
            <button
              onClick={handleAddFilter}
              className="w-full px-4 py-2 border-2 border-dashed border-gray-300 text-gray-600 rounded-lg hover:border-blue-500 hover:text-blue-600 transition-colors"
            >
              + 添加筛选条件
            </button>
          </div>
          
          {filters.length === 0 && (
            <div className="text-center py-4 text-gray-500 text-sm">
              点击"添加筛选条件"开始筛选
            </div>
          )}
        </div>
      )}

      {showForm && (
        <InstanceForm
          objectType={objectTypeDef}
          instance={editingInstance}
          onClose={handleFormClose}
        />
      )}

      {/* 属性分析视图 */}
      {viewMode === 'properties' && objectTypeDef && (
        <div className="space-y-4">
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-semibold text-gray-900">Properties 分析</h2>
              <div className="flex gap-2">
                <button
                  onClick={() => setShowPropertyFilter(!showPropertyFilter)}
                  className={`flex items-center px-3 py-2 text-sm rounded-lg transition-colors ${
                    showPropertyFilter 
                      ? 'bg-blue-100 text-blue-700' 
                      : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
                  }`}
                  title="筛选属性"
                >
                  <FunnelIcon className="w-5 h-5 mr-2" />
                  筛选属性
                </button>
                <button
                  onClick={() => setShowAnalysisSettings(!showAnalysisSettings)}
                  className={`flex items-center px-3 py-2 text-sm rounded-lg transition-colors ${
                    showAnalysisSettings 
                      ? 'bg-gray-200 text-gray-900' 
                      : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
                  }`}
                  title="分析设置"
                >
                  <Cog6ToothIcon className="w-5 h-5 mr-2" />
                  设置
                </button>
                <div className="relative">
                  <button
                    onClick={() => setShowExportMenu(!showExportMenu)}
                    className={`flex items-center px-3 py-2 text-sm rounded-lg transition-colors ${
                      showExportMenu 
                        ? 'bg-gray-200 text-gray-900' 
                        : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
                    }`}
                    title="导出分析"
                  >
                    <ArrowDownTrayIcon className="w-5 h-5 mr-2" />
                    导出
                  </button>
                  {showExportMenu && (
                    <div className="absolute right-0 mt-2 w-32 bg-white rounded-lg shadow-lg border border-gray-100 z-10 py-1">
                      <button
                        onClick={() => handleExport('png')}
                        className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center"
                      >
                        <span className="w-8">PNG</span>
                        <span className="text-xs text-gray-400">图片</span>
                      </button>
                      <button
                        onClick={() => handleExport('pdf')}
                        className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center"
                      >
                        <span className="w-8">PDF</span>
                        <span className="text-xs text-gray-400">文档</span>
                      </button>
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* 属性筛选面板 */}
            {showPropertyFilter && (
              <div className="mb-4 p-4 bg-gray-50 rounded-lg border border-gray-200">
                <div className="flex justify-between items-center mb-3">
                  <h3 className="text-sm font-semibold text-gray-900">选择显示的属性</h3>
                  <div className="flex gap-2 text-xs">
                    <button 
                      onClick={() => setVisibleProperties(new Set(objectTypeDef.properties.map(p => p.name)))}
                      className="text-blue-600 hover:text-blue-800 font-medium"
                    >
                      全选
                    </button>
                    <span className="text-gray-300">|</span>
                    <button 
                      onClick={() => setVisibleProperties(new Set())}
                      className="text-gray-500 hover:text-gray-700"
                    >
                      清空
                    </button>
                  </div>
                </div>
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-2">
                  {objectTypeDef.properties.map(prop => (
                    <label key={prop.name} className="flex items-center cursor-pointer hover:bg-white p-1.5 rounded border border-transparent hover:border-gray-200 transition-colors">
                      <input
                        type="checkbox"
                        checked={visibleProperties.has(prop.name)}
                        onChange={(e) => {
                          const newSet = new Set(visibleProperties);
                          if (e.target.checked) {
                            newSet.add(prop.name);
                          } else {
                            newSet.delete(prop.name);
                          }
                          setVisibleProperties(newSet);
                        }}
                        className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500 border-gray-300"
                      />
                      <span className="ml-2 text-sm text-gray-700 truncate select-none" title={prop.name}>{prop.name}</span>
                    </label>
                  ))}
                </div>
              </div>
            )}

            {/* 分析设置面板 */}
            {showAnalysisSettings && (
              <div className="mb-4 p-4 bg-gray-50 rounded-lg border border-gray-200">
                <h3 className="text-sm font-semibold text-gray-900 mb-3">显示选项</h3>
                <div className="flex flex-wrap gap-4">
                  <label className="flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      checked={analysisDisplayOptions.showSummary}
                      onChange={(e) => setAnalysisDisplayOptions({
                        ...analysisDisplayOptions,
                        showSummary: e.target.checked,
                      })}
                      className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                    />
                    <span className="ml-2 text-sm text-gray-700">统计摘要</span>
                  </label>
                  <label className="flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      checked={analysisDisplayOptions.showNumericStats}
                      onChange={(e) => setAnalysisDisplayOptions({
                        ...analysisDisplayOptions,
                        showNumericStats: e.target.checked,
                      })}
                      className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                    />
                    <span className="ml-2 text-sm text-gray-700">数值统计</span>
                  </label>
                  <label className="flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      checked={analysisDisplayOptions.showChart}
                      onChange={(e) => setAnalysisDisplayOptions({
                        ...analysisDisplayOptions,
                        showChart: e.target.checked,
                      })}
                      className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                    />
                    <span className="ml-2 text-sm text-gray-700">分布图表</span>
                  </label>
                </div>
              </div>
            )}

            {loadingStats ? (
              <div className="text-center py-12">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                <p className="mt-4 text-gray-600">正在加载统计数据...</p>
              </div>
            ) : allInstances.length === 0 ? (
              <div className="text-center py-12 text-gray-500">
                <p>暂无数据，无法进行统计分析</p>
                <p className="text-sm text-gray-400 mt-2">请先创建实例或同步数据</p>
              </div>
            ) : (
              <div className="space-y-3" ref={analysisRef}>
                {objectTypeDef.properties
                  .filter(prop => visibleProperties.has(prop.name))
                  .map((prop) => (
                  <PropertyStatistics
                    key={prop.name}
                    property={prop}
                    instances={allInstances}
                    displayOptions={analysisDisplayOptions}
                  />
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* 数据列表视图 */}
      {viewMode === 'list' && (
      <>
      {/* 查询配置面板 */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-4">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-gray-900 flex items-center">
            <Cog6ToothIcon className="w-5 h-5 mr-2" />
            查询配置
          </h3>
          <button
            onClick={() => setShowQueryConfig(!showQueryConfig)}
            className="text-sm text-gray-600 hover:text-gray-900"
          >
            {showQueryConfig ? '收起' : '展开'}
          </button>
        </div>

        {showQueryConfig && (
          <div className="space-y-4">
            {/* 显示字段选择 */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-sm font-medium text-gray-700">显示字段</label>
                <div className="flex gap-2 text-xs">
                  <button
                    onClick={() => {
                      if (objectTypeDef?.properties) {
                        setQuerySelectedFields(new Set(objectTypeDef.properties.map(p => p.name)));
                      }
                    }}
                    className="text-blue-600 hover:text-blue-800 font-medium"
                  >
                    全选
                  </button>
                  <span className="text-gray-300">|</span>
                  <button
                    onClick={() => setQuerySelectedFields(new Set())}
                    className="text-gray-500 hover:text-gray-700"
                  >
                    清空
                  </button>
                </div>
              </div>
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-2 border border-gray-300 rounded-lg p-3 bg-gray-50 max-h-48 overflow-y-auto">
                {objectTypeDef?.properties.map(prop => (
                  <label key={prop.name} className="flex items-center cursor-pointer hover:bg-white p-1.5 rounded border border-transparent hover:border-gray-200 transition-colors">
                    <input
                      type="checkbox"
                      checked={querySelectedFields.has(prop.name)}
                      onChange={(e) => {
                        const newSet = new Set(querySelectedFields);
                        if (e.target.checked) {
                          newSet.add(prop.name);
                        } else {
                          newSet.delete(prop.name);
                        }
                        setQuerySelectedFields(newSet);
                      }}
                      className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500 border-gray-300"
                    />
                    <span className="ml-2 text-sm text-gray-700 truncate select-none" title={prop.name}>{prop.name}</span>
                  </label>
                ))}
              </div>
            </div>

            {/* 查询条件 */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-sm font-medium text-gray-700">查询条件</label>
                <button
                  onClick={addFilterExpression}
                  disabled={!objectTypeDef}
                  className="text-sm text-blue-600 hover:text-blue-800 flex items-center disabled:text-gray-400 disabled:cursor-not-allowed"
                >
                  <PlusIcon className="w-4 h-4 mr-1" />
                  添加查询条件
                </button>
              </div>
              <div className="space-y-2">
                {queryFilterExpressions.map((expr, index) => {
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
                        {objectTypeDef?.properties.map(prop => (
                          <option key={prop.name} value={prop.name}>{prop.name}</option>
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
                {queryFilterExpressions.length === 0 && (
                  <div className="text-center py-4 text-gray-500 text-sm">
                    点击"添加查询条件"开始配置查询条件
                  </div>
                )}
              </div>
            </div>

            {/* 排序 */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-sm font-medium text-gray-700">排序</label>
                <button
                  onClick={addOrderBy}
                  className="text-sm text-blue-600 hover:text-blue-800 flex items-center"
                >
                  <PlusIcon className="w-4 h-4 mr-1" />
                  添加排序
                </button>
              </div>
              <div className="space-y-2">
                {queryOrderBy.map((order, index) => (
                  <div key={index} className="flex gap-2 items-center">
                    <select
                      value={order.field}
                      onChange={(e) => updateOrderBy(index, e.target.value, order.direction)}
                      className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                    >
                      <option value="">选择属性...</option>
                      {objectTypeDef?.properties.map(prop => (
                        <option key={prop.name} value={prop.name}>{prop.name}</option>
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
                {queryOrderBy.length === 0 && (
                  <div className="text-center py-2 text-gray-500 text-sm">
                    点击"添加排序"开始配置排序
                  </div>
                )}
              </div>
            </div>

            {/* 分页设置 */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">每页数量</label>
                <input
                  type="number"
                  value={queryLimit}
                  onChange={(e) => setQueryLimit(parseInt(e.target.value) || 20)}
                  className="w-full border border-gray-300 rounded px-2 py-1 text-sm"
                  min="1"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">偏移量</label>
                <input
                  type="number"
                  value={queryOffset}
                  onChange={(e) => setQueryOffset(parseInt(e.target.value) || 0)}
                  className="w-full border border-gray-300 rounded px-2 py-1 text-sm"
                  min="0"
                />
              </div>
            </div>

            {/* 应用查询按钮 */}
            <div className="flex justify-end">
              <button
                onClick={() => {
                  // 将配置状态应用到实际查询状态
                  setAppliedFilterExpressions([...queryFilterExpressions]);
                  setAppliedOrderBy([...queryOrderBy]);
                  setAppliedLimit(queryLimit);
                  setAppliedOffset(queryOffset);
                  setAppliedSelectedFields(new Set(querySelectedFields));
                  setOffset(queryOffset);
                  // loadData 会在 useEffect 中自动触发，因为 applied* 状态改变了
                }}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
              >
                应用查询
              </button>
            </div>
          </div>
        )}
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  ID
                </th>
                {objectTypeDef.properties
                  .filter(prop => appliedSelectedFields.size === 0 || appliedSelectedFields.has(prop.name))
                  .map((prop) => (
                    <th
                      key={prop.name}
                      className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
                    >
                      {prop.name}
                    </th>
                  ))}
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {instances.length === 0 ? (
                <tr>
                  <td
                    colSpan={(appliedSelectedFields.size === 0 
                      ? objectTypeDef.properties.length 
                      : objectTypeDef.properties.filter(p => appliedSelectedFields.has(p.name)).length) + 2}
                    className="px-6 py-8 text-center text-gray-500"
                  >
                    No instances found. Create one to get started.
                  </td>
                </tr>
              ) : (
                instances.map((instance) => (
                  <tr key={instance.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <Link
                        to={`/instances/${objectType}/${instance.id || ''}`}
                        className="text-blue-600 hover:text-blue-800 text-sm font-mono"
                      >
                        {instance.id ? `${instance.id.substring(0, 8)}...` : '-'}
                      </Link>
                    </td>
                    {objectTypeDef.properties
                      .filter(prop => appliedSelectedFields.size === 0 || appliedSelectedFields.has(prop.name))
                      .map((prop) => (
                        <td key={prop.name} className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {instance[prop.name] !== null && instance[prop.name] !== undefined
                            ? typeof instance[prop.name] === 'object'
                              ? JSON.stringify(instance[prop.name])
                              : String(instance[prop.name])
                            : '-'}
                        </td>
                      ))}
                    <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                      {!fromMapping && (
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => handleEdit(instance)}
                            className="text-blue-600 hover:text-blue-800"
                            title="Edit"
                          >
                            <PencilIcon className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleDelete(instance.id)}
                            className="text-red-600 hover:text-red-800"
                            title="Delete"
                          >
                            <TrashIcon className="w-4 h-4" />
                          </button>
                        </div>
                      )}
                      {fromMapping && (
                        <span className="text-xs text-gray-400">只读</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {total > (appliedLimit || limit) && (
          <div className="bg-gray-50 px-6 py-3 flex items-center justify-between border-t border-gray-200">
            <div className="text-sm text-gray-700">
              Showing {(appliedOffset !== undefined ? appliedOffset : offset) + 1} to {Math.min((appliedOffset !== undefined ? appliedOffset : offset) + (appliedLimit || limit), total)} of {total} instances
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => {
                  const newOffset = Math.max(0, (appliedOffset !== undefined ? appliedOffset : offset) - (appliedLimit || limit));
                  setAppliedOffset(newOffset);
                  setOffset(newOffset);
                }}
                disabled={(appliedOffset !== undefined ? appliedOffset : offset) === 0}
                className="px-3 py-1 text-sm border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Previous
              </button>
              <button
                onClick={() => {
                  const newOffset = (appliedOffset !== undefined ? appliedOffset : offset) + (appliedLimit || limit);
                  setAppliedOffset(newOffset);
                  setOffset(newOffset);
                }}
                disabled={(appliedOffset !== undefined ? appliedOffset : offset) + (appliedLimit || limit) >= total}
                className="px-3 py-1 text-sm border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
      </>
      )}

      {/* 同步对话框 */}
      {showSyncDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl p-6 max-w-md w-full mx-4">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-bold text-gray-900">从数据源同步表信息</h2>
              <button
                onClick={() => {
                  setShowSyncDialog(false);
                  setSelectedDatabaseId('');
                }}
                className="text-gray-400 hover:text-gray-600"
              >
                <XMarkIcon className="w-6 h-6" />
              </button>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                选择数据源
              </label>
              <select
                value={selectedDatabaseId}
                onChange={(e) => setSelectedDatabaseId(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-green-500"
              >
                <option value="">-- 请选择数据源 --</option>
                {databases.map((db) => (
                  <option key={db.id} value={db.id}>
                    {db.name || db.id} {db.host && `(${db.host}:${db.port})`}
                  </option>
                ))}
              </select>
            </div>

            <div className="text-sm text-gray-600 mb-4">
              <p>同步操作将：</p>
              <ul className="list-disc list-inside mt-2 space-y-1">
                <li>从选中的数据源获取所有表信息</li>
                <li>创建或更新表实例（table对象）</li>
                <li>为每个表创建或更新列实例（column对象）</li>
              </ul>
              <p className="mt-2 text-blue-600">
                同步完成后，可以在 <Link to="/instances/table" className="underline">表列表</Link> 和 <Link to="/instances/column" className="underline">列列表</Link> 中查看同步的数据。
              </p>
            </div>

            <div className="flex gap-3">
              <button
                onClick={handleSync}
                disabled={!selectedDatabaseId || syncing}
                className="flex-1 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {syncing ? '同步中...' : '开始同步'}
              </button>
              <button
                onClick={() => {
                  setShowSyncDialog(false);
                  setSelectedDatabaseId('');
                }}
                className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                取消
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 数据映射对话框 */}
      {showMappingDialog && objectTypeDef && objectType && (
        <DataMappingDialog
          objectType={objectType}
          objectTypeDef={objectTypeDef}
          onClose={() => setShowMappingDialog(false)}
          onSuccess={() => {
            setShowMappingDialog(false);
            loadAvailableMappings(); // 重新加载映射列表
            loadData();
          }}
        />
      )}

      {/* 同步抽取对话框 */}
      {showSyncExtractDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl p-6 max-w-md w-full mx-4">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-bold text-gray-900">同步抽取数据</h2>
              <button
                onClick={() => {
                  setShowSyncExtractDialog(false);
                  setSelectedMappingId('');
                }}
                className="text-gray-400 hover:text-gray-600"
              >
                <XMarkIcon className="w-6 h-6" />
              </button>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                选择映射配置
              </label>
              <select
                value={selectedMappingId}
                onChange={(e) => setSelectedMappingId(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500"
              >
                <option value="">-- 请选择映射配置 --</option>
                {mappings.map((mapping) => {
                  const tableName = mapping.table_name || mapping.table_id || '未知表';
                  const mappingCount = mapping.column_property_mappings 
                    ? Object.keys(mapping.column_property_mappings).length 
                    : 0;
                  const primaryKey = mapping.primary_key_column ? ` (主键: ${mapping.primary_key_column})` : '';
                  return (
                    <option key={mapping.id} value={mapping.id}>
                      {tableName} - {mappingCount} 个字段映射{primaryKey}
                    </option>
                  );
                })}
              </select>
            </div>

            <div className="text-sm text-gray-600 mb-4">
              <p>同步抽取操作将：</p>
              <ul className="list-disc list-inside mt-2 space-y-1">
                <li>根据选中的映射配置从数据库查询数据</li>
                <li>将查询到的数据转换为对象实例</li>
                <li>创建或更新本地实例数据</li>
                <li>如果配置了主键列，将使用主键值作为实例ID</li>
              </ul>
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => handleExtract()}
                disabled={!selectedMappingId || extracting}
                className="flex-1 px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {extracting ? '抽取中...' : '开始抽取'}
              </button>
              <button
                onClick={() => {
                  setShowSyncExtractDialog(false);
                  setSelectedMappingId('');
                }}
                className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                取消
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Toast 通知 */}
      <ToastContainer toasts={toasts} onClose={removeToast} />
      </div>
    </div>
  );
}

