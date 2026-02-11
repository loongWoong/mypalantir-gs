import { useState, useEffect } from 'react';
import { databaseApi, comparisonApi, modelApi, mappingApi } from '../api/client';
import type { ComparisonResult, ObjectType, ModelInfo, DataSourceMapping } from '../api/client';
import { ToastContainer, useToast } from '../components/Toast';
import { 
  ArrowPathIcon, 
  CheckCircleIcon, 
  TableCellsIcon, 
  CubeIcon,
  ArrowRightIcon
} from '@heroicons/react/24/outline';

export default function DataComparison() {
  // Config Mode
  const [mode, setMode] = useState<'table' | 'model'>('table');

  // State for config
  const [databases, setDatabases] = useState<any[]>([]);
  const [tables, setTables] = useState<any[]>([]);
  const [models, setModels] = useState<ModelInfo[]>([]);
  
  // Model Selection
  const [sourceWorkspace, setSourceWorkspace] = useState('');
  const [targetWorkspace, setTargetWorkspace] = useState('');
  const [sourceObjectTypes, setSourceObjectTypes] = useState<ObjectType[]>([]);
  const [targetObjectTypes, setTargetObjectTypes] = useState<ObjectType[]>([]);
  const [sourceModelName, setSourceModelName] = useState('');
  const [targetModelName, setTargetModelName] = useState('');

  // Underlying Table Config (Used for execution)
  const [sourceDatabaseId, setSourceDatabaseId] = useState('');
  const [targetDatabaseId, setTargetDatabaseId] = useState('');
  const [sourceTableId, setSourceTableId] = useState('');
  const [targetTableId, setTargetTableId] = useState('');
  const [sourceColumns, setSourceColumns] = useState<any[]>([]);
  const [targetColumns, setTargetColumns] = useState<any[]>([]);
  
  const [sourceKey, setSourceKey] = useState('');
  const [targetKey, setTargetKey] = useState('');
  
  const [columnMapping, setColumnMapping] = useState<Record<string, string>>({});
  
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ComparisonResult | null>(null);
  
  const { toasts, showToast, removeToast } = useToast();

  useEffect(() => {
    loadTables();
    loadModels();
  }, []);

  const loadTables = async () => {
    try {
      const databases = await databaseApi.listDatabases();
      if (databases.length === 0) {
        try {
            const dbInfo = await databaseApi.getDefaultDatabaseId();
            databases.push({ id: dbInfo.id, name: 'default' });
        } catch (e) {
            console.error('Failed to get default database', e);
        }
      }
      setDatabases(databases);

      let allTables: any[] = [];
      await Promise.all(databases.map(async (db) => {
          try {
              const tables = await databaseApi.getTables(db.id);
              const tablesWithDb = tables.map((t: any) => ({
                  ...t,
                  displayName: `[${db.name || db.database_name || 'DB'}] ${t.name}`,
                  originalName: t.name,
                  dbName: db.name || db.database_name,
                  database_id: db.id
              }));
              allTables = [...allTables, ...tablesWithDb];
          } catch (e) {
              console.error(`Failed to load tables for db ${db.id}`, e);
          }
      }));
      allTables.sort((a, b) => a.displayName.localeCompare(b.displayName));
      setTables(allTables);
    } catch (error) {
      console.error('Failed to load tables:', error);
      showToast('加载表列表失败', 'error');
    }
  };

  const loadModels = async () => {
      try {
          const list = await modelApi.listModels();
          setModels(list);
          // Optional: Load default workspace's object types if needed
      } catch (error) {
          console.error('Failed to load models', error);
      }
  };

  const handleSourceWorkspaceChange = async (wsId: string) => {
      setSourceWorkspace(wsId);
      setSourceModelName('');
      setSourceTableId('');
      if (wsId) {
          try {
              const types = await modelApi.getObjectTypes(wsId);
              setSourceObjectTypes(types);
          } catch (e) {
              console.error(e);
              showToast('加载模型对象类型失败', 'error');
              setSourceObjectTypes([]);
          }
      } else {
          setSourceObjectTypes([]);
      }
  };

  const handleTargetWorkspaceChange = async (wsId: string) => {
      setTargetWorkspace(wsId);
      setTargetModelName('');
      setTargetTableId('');
      if (wsId) {
          try {
              const types = await modelApi.getObjectTypes(wsId);
              setTargetObjectTypes(types);
          } catch (e) {
              console.error(e);
              showToast('加载模型对象类型失败', 'error');
              setTargetObjectTypes([]);
          }
      } else {
          setTargetObjectTypes([]);
      }
  };

  // --- Table Logic ---

  const handleSourceTableChange = async (tableId: string) => {
    setSourceTableId(tableId);
    if (mode === 'table') {
        setSourceKey('');
        setColumnMapping({});
    }
    
    if (tableId) {
       const table = tables.find(t => t.id === tableId);
       if (table) {
           const cols = await databaseApi.getColumns(table.database_id, table.name);
           setSourceColumns(cols);
           if (mode === 'table') {
               const pk = cols.find((c: any) => c.is_primary_key);
               if (pk) setSourceKey(pk.name);
           }
           return cols;
       }
    } else {
        setSourceColumns([]);
    }
    return [];
  };

  const handleTargetTableChange = async (tableId: string) => {
    setTargetTableId(tableId);
    if (mode === 'table') {
        setTargetKey('');
    }

    if (tableId) {
       const table = tables.find(t => t.id === tableId);
       if (table) {
           const cols = await databaseApi.getColumns(table.database_id, table.name);
           setTargetColumns(cols);
           if (mode === 'table') {
               const pk = cols.find((c: any) => c.is_primary_key);
               if (pk) setTargetKey(pk.name);
           }
           return cols;
       }
    } else {
        setTargetColumns([]);
    }
    return [];
  };

  // --- Model Logic ---

  // Helper to resolve data source from mapping API if not present in model
  const resolveModelDataSource = async (model: ObjectType): Promise<ObjectType> => {
      if (model.data_source && model.data_source.connection_id) {
          return model;
      }

      try {
          const mappings = await mappingApi.getByObjectType(model.name);
          if (mappings && mappings.length > 0) {
              const mapping = mappings[0];
              const table = tables.find(t => t.id === mapping.table_id);
              
              if (table) {
                  // Invert mapping: Column -> Property  ==>  Property -> Column
                  const field_mapping: Record<string, string> = {};
                  if (mapping.column_property_mappings) {
                      Object.entries(mapping.column_property_mappings).forEach(([col, prop]) => {
                           if (typeof prop === 'string') {
                               field_mapping[prop] = col;
                           }
                      });
                  }
                  
                  const newDataSource: DataSourceMapping = {
                      connection_id: table.database_id,
                      table: table.originalName,
                      id_column: mapping.primary_key_column,
                      field_mapping: field_mapping
                  };
                  
                  return { ...model, data_source: newDataSource };
              }
          }
      } catch (e) {
          console.error("Failed to resolve mapping", e);
      }
      return model;
  };

  const handleSourceModelChange = async (modelName: string) => {
      setSourceModelName(modelName);
      if (!modelName) {
          handleSourceTableChange('');
          return;
      }
      
      let model = sourceObjectTypes.find(m => m.name === modelName);
      if (!model) return;

      // Try to resolve data source if missing
      if (!model.data_source || !model.data_source.connection_id) {
          const resolvedModel = await resolveModelDataSource(model);
          if (resolvedModel.data_source) {
              model = resolvedModel;
              // Update state to reflect resolved data source (important for auto-mapping)
              setSourceObjectTypes(prev => prev.map(m => m.name === modelName ? resolvedModel : m));
          }
      }

      if (!model.data_source || !model.data_source.connection_id) {
          showToast('该模型未配置数据源映射', 'error');
          return;
      }
      
      const table = tables.find(t => 
          t.database_id === model!.data_source!.connection_id && 
          t.originalName === model!.data_source!.table
      );
      
      if (table) {
          await handleSourceTableChange(table.id);
          if (model.data_source.id_column) {
              setSourceKey(model.data_source.id_column);
          }
      } else {
          showToast(`未找到映射的数据表: ${model.data_source.table}`, 'error');
      }
  };

  const handleTargetModelChange = async (modelName: string) => {
      setTargetModelName(modelName);
      if (!modelName) {
          handleTargetTableChange('');
          return;
      }

      let model = targetObjectTypes.find(m => m.name === modelName);
      if (!model) return;

      // Try to resolve data source if missing
      if (!model.data_source || !model.data_source.connection_id) {
          const resolvedModel = await resolveModelDataSource(model);
          if (resolvedModel.data_source) {
              model = resolvedModel;
              // Update state
              setTargetObjectTypes(prev => prev.map(m => m.name === modelName ? resolvedModel : m));
          }
      }

      if (!model.data_source || !model.data_source.connection_id) {
          showToast('该模型未配置数据源映射', 'error');
          return;
      }
      
      const table = tables.find(t => 
          t.database_id === model!.data_source!.connection_id && 
          t.originalName === model!.data_source!.table
      );
      
      if (table) {
          await handleTargetTableChange(table.id);
          if (model.data_source.id_column) {
              setTargetKey(model.data_source.id_column);
          }
      } else {
          showToast(`未找到映射的数据表: ${model.data_source.table}`, 'error');
      }
  };

  // Auto Mapping for Model Mode
  useEffect(() => {
      if (mode === 'model' && sourceModelName && targetModelName && sourceColumns.length > 0 && targetColumns.length > 0) {
          const sourceModel = sourceObjectTypes.find(m => m.name === sourceModelName);
          const targetModel = targetObjectTypes.find(m => m.name === targetModelName);
          
          if (sourceModel?.data_source?.field_mapping && targetModel?.data_source?.field_mapping) {
              const mapping: Record<string, string> = {};
              sourceModel.properties.forEach(sp => {
                  const tp = targetModel.properties.find(p => p.name === sp.name);
                  if (tp) {
                      const sourceCol = sourceModel.data_source!.field_mapping[sp.name];
                      const targetCol = targetModel.data_source!.field_mapping[tp.name];
                      if (sourceCol && targetCol) {
                          mapping[sourceCol] = targetCol;
                      }
                  }
              });
              if (Object.keys(mapping).length > 0) {
                  setColumnMapping(mapping);
              }
          }
      }
  }, [mode, sourceModelName, targetModelName, sourceColumns, targetColumns, sourceObjectTypes, targetObjectTypes]);
  
  // Auto mapping for Table Mode
  useEffect(() => {
      if (mode === 'table' && sourceColumns.length > 0 && targetColumns.length > 0) {
          const mapping: Record<string, string> = {};
          sourceColumns.forEach(sc => {
              const tc = targetColumns.find(t => t.name.toLowerCase() === sc.name.toLowerCase());
              if (tc) {
                  mapping[sc.name] = tc.name;
              }
          });
          setColumnMapping(mapping);
      }
  }, [mode, sourceColumns, targetColumns]);

  const handleRun = async () => {
      if (!sourceTableId || !targetTableId || !sourceKey || !targetKey) {
          showToast('请完整填写配置', 'error');
          return;
      }
      
      setLoading(true);
      try {
          const res = await comparisonApi.run({
              sourceTableId,
              targetTableId,
              sourceKey,
              targetKey,
              columnMapping
          });
          setResult(res);
          showToast('对比完成', 'success');
      } catch (error: any) {
          console.error(error);
          showToast('对比失败: ' + error.message, 'error');
      } finally {
          setLoading(false);
      }
  };

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      <div className="flex justify-between items-center">
          <h1 className="text-2xl font-bold text-text">数据对账</h1>
          
          {/* Mode Toggle */}
          <div className="bg-white p-1 rounded-lg border border-gray-200 flex shadow-sm">
              <button
                  className={`px-4 py-2 rounded-md text-sm font-medium transition-all duration-200 flex items-center gap-2 ${
                      mode === 'table' 
                        ? 'bg-primary text-white shadow-sm' 
                        : 'text-gray-500 hover:text-text hover:bg-gray-50'
                  }`}
                  onClick={() => {
                      setMode('table');
                      setSourceWorkspace('');
                      setTargetWorkspace('');
                      setSourceModelName('');
                      setTargetModelName('');
                      setSourceDatabaseId('');
                      setTargetDatabaseId('');
                      setSourceTableId('');
                      setTargetTableId('');
                  }}
              >
                  <TableCellsIcon className="w-4 h-4" />
                  基于数据表 (By Table)
              </button>
              <button
                  className={`px-4 py-2 rounded-md text-sm font-medium transition-all duration-200 flex items-center gap-2 ${
                      mode === 'model' 
                        ? 'bg-primary text-white shadow-sm' 
                        : 'text-gray-500 hover:text-text hover:bg-gray-50'
                  }`}
                  onClick={() => {
                      setMode('model');
                      setSourceDatabaseId('');
                      setTargetDatabaseId('');
                      setSourceTableId('');
                      setTargetTableId('');
                  }}
              >
                  <CubeIcon className="w-4 h-4" />
                  基于对象模型 (By Model)
              </button>
          </div>
      </div>
      
      {/* Config Panel */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
         <div className="grid grid-cols-2 gap-8 relative">
             {/* Divider */}
             <div className="absolute top-0 bottom-0 left-1/2 w-px bg-gray-200 transform -translate-x-1/2 hidden md:block"></div>

             {/* Source */}
             <div>
                 <h3 className="font-semibold mb-6 text-primary flex items-center gap-2 text-lg">
                    <span className="w-2 h-6 bg-primary rounded-full"></span>
                    基准 (Source)
                 </h3>
                 
                 {mode === 'table' ? (
                     <div className="space-y-5">
                         <div>
                             <label className="block text-sm font-medium text-text mb-2">选择数据源 (Data Source)</label>
                             <select 
                                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 focus:ring-2 focus:ring-primary focus:border-transparent outline-none transition-shadow bg-white"
                                value={sourceDatabaseId}
                                onChange={(e) => {
                                    setSourceDatabaseId(e.target.value);
                                    handleSourceTableChange('');
                                }}
                             >
                                 <option value="">-- 请选择 --</option>
                                 {databases.map(db => <option key={db.id} value={db.id}>{db.name || db.database_name || 'Default'}</option>)}
                             </select>
                         </div>
                         <div>
                             <label className="block text-sm font-medium text-text mb-2">选择数据表 (Table)</label>
                             <select 
                                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 focus:ring-2 focus:ring-primary focus:border-transparent outline-none transition-shadow bg-white disabled:bg-gray-50 disabled:text-gray-400"
                                value={sourceTableId}
                                onChange={(e) => handleSourceTableChange(e.target.value)}
                                disabled={!sourceDatabaseId}
                             >
                                 <option value="">-- 请选择 --</option>
                                 {tables
                                    .filter(t => t.database_id === sourceDatabaseId)
                                    .map(t => <option key={t.id} value={t.id}>{t.originalName || t.name}</option>)
                                 }
                             </select>
                         </div>
                     </div>
                 ) : (
                     <div className="space-y-5">
                        <div>
                             <label className="block text-sm font-medium text-text mb-2">选择工作空间 (Workspace)</label>
                             <select 
                                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 focus:ring-2 focus:ring-primary focus:border-transparent outline-none transition-shadow bg-blue-50/50"
                                value={sourceWorkspace}
                                onChange={(e) => handleSourceWorkspaceChange(e.target.value)}
                             >
                                 <option value="">-- 请选择 --</option>
                                 {models.map(m => <option key={m.id} value={m.id}>{m.displayName || m.id}</option>)}
                             </select>
                        </div>
                        <div>
                             <label className="block text-sm font-medium text-text mb-2">选择对象模型 (Object Type)</label>
                             <select 
                                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 focus:ring-2 focus:ring-primary focus:border-transparent outline-none transition-shadow bg-blue-50/50 disabled:bg-gray-50 disabled:text-gray-400"
                                value={sourceModelName}
                                onChange={(e) => handleSourceModelChange(e.target.value)}
                                disabled={!sourceWorkspace}
                             >
                                 <option value="">-- 请选择 --</option>
                                 {sourceObjectTypes.map(t => <option key={t.name} value={t.name}>{t.display_name || t.name}</option>)}
                             </select>
                             {sourceTableId && (
                                 <div className="mt-2 text-xs text-gray-500 flex items-center gap-1">
                                     <CheckCircleIcon className="w-3 h-3 text-green-500" />
                                     已映射到表: <span className="font-mono">{tables.find(t => t.id === sourceTableId)?.displayName}</span>
                                 </div>
                             )}
                        </div>
                     </div>
                 )}
                 
                 <div className="mt-5">
                     <label className="block text-sm font-medium text-text mb-2">
                         {mode === 'model' ? '主键列 (Mapped Key)' : '主键 (Key)'}
                     </label>
                     <select 
                        className="w-full border border-gray-300 rounded-lg px-3 py-2.5 focus:ring-2 focus:ring-primary focus:border-transparent outline-none transition-shadow bg-white disabled:bg-gray-50 disabled:text-gray-400"
                        value={sourceKey}
                        onChange={(e) => setSourceKey(e.target.value)}
                        disabled={mode === 'model' && !sourceKey} 
                     >
                         <option value="">-- 请选择 --</option>
                         {sourceColumns.map(c => <option key={c.name} value={c.name}>{c.name}</option>)}
                     </select>
                 </div>
             </div>
             
             {/* Target */}
             <div>
                 <h3 className="font-semibold mb-6 text-cta flex items-center gap-2 text-lg">
                    <span className="w-2 h-6 bg-cta rounded-full"></span>
                    对比目标 (Target)
                 </h3>
                 
                 {mode === 'table' ? (
                     <div className="space-y-5">
                         <div>
                             <label className="block text-sm font-medium text-text mb-2">选择数据源 (Data Source)</label>
                             <select 
                                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 focus:ring-2 focus:ring-cta focus:border-transparent outline-none transition-shadow bg-white"
                                value={targetDatabaseId}
                                onChange={(e) => {
                                    setTargetDatabaseId(e.target.value);
                                    handleTargetTableChange('');
                                }}
                             >
                                 <option value="">-- 请选择 --</option>
                                 {databases.map(db => <option key={db.id} value={db.id}>{db.name || db.database_name || 'Default'}</option>)}
                             </select>
                         </div>
                         <div>
                             <label className="block text-sm font-medium text-text mb-2">选择数据表 (Table)</label>
                             <select 
                                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 focus:ring-2 focus:ring-cta focus:border-transparent outline-none transition-shadow bg-white disabled:bg-gray-50 disabled:text-gray-400"
                                value={targetTableId}
                                onChange={(e) => handleTargetTableChange(e.target.value)}
                                disabled={!targetDatabaseId}
                             >
                                 <option value="">-- 请选择 --</option>
                                 {tables
                                    .filter(t => t.database_id === targetDatabaseId)
                                    .map(t => <option key={t.id} value={t.id}>{t.originalName || t.name}</option>)
                                 }
                             </select>
                         </div>
                     </div>
                 ) : (
                     <div className="space-y-5">
                        <div>
                             <label className="block text-sm font-medium text-text mb-2">选择工作空间 (Workspace)</label>
                             <select 
                                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 focus:ring-2 focus:ring-cta focus:border-transparent outline-none transition-shadow bg-orange-50/50"
                                value={targetWorkspace}
                                onChange={(e) => handleTargetWorkspaceChange(e.target.value)}
                             >
                                 <option value="">-- 请选择 --</option>
                                 {models.map(m => <option key={m.id} value={m.id}>{m.displayName || m.id}</option>)}
                             </select>
                        </div>
                        <div>
                             <label className="block text-sm font-medium text-text mb-2">选择对象模型 (Object Type)</label>
                             <select 
                                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 focus:ring-2 focus:ring-cta focus:border-transparent outline-none transition-shadow bg-orange-50/50 disabled:bg-gray-50 disabled:text-gray-400"
                                value={targetModelName}
                                onChange={(e) => handleTargetModelChange(e.target.value)}
                                disabled={!targetWorkspace}
                             >
                                 <option value="">-- 请选择 --</option>
                                 {targetObjectTypes.map(t => <option key={t.name} value={t.name}>{t.display_name || t.name}</option>)}
                             </select>
                             {targetTableId && (
                                 <div className="mt-2 text-xs text-gray-500 flex items-center gap-1">
                                     <CheckCircleIcon className="w-3 h-3 text-green-500" />
                                     已映射到表: <span className="font-mono">{tables.find(t => t.id === targetTableId)?.displayName}</span>
                                 </div>
                             )}
                        </div>
                     </div>
                 )}

                 <div className="mt-5">
                     <label className="block text-sm font-medium text-text mb-2">
                         {mode === 'model' ? '主键列 (Mapped Key)' : '主键 (Key)'}
                     </label>
                     <select 
                        className="w-full border border-gray-300 rounded-lg px-3 py-2.5 focus:ring-2 focus:ring-cta focus:border-transparent outline-none transition-shadow bg-white disabled:bg-gray-50 disabled:text-gray-400"
                        value={targetKey}
                        onChange={(e) => setTargetKey(e.target.value)}
                     >
                         <option value="">-- 请选择 --</option>
                         {targetColumns.map(c => <option key={c.name} value={c.name}>{c.name}</option>)}
                     </select>
                 </div>
             </div>
         </div>
         
         {/* Mapping */}
         {sourceTableId && targetTableId && (
             <div className="mt-8 border-t border-gray-200 pt-8">
                 <h3 className="font-semibold mb-6 text-text flex items-center gap-2">
                    <ArrowPathIcon className="w-5 h-5 text-gray-400" />
                    字段映射配置 {mode === 'model' && <span className="text-xs font-normal text-gray-500 ml-2 bg-gray-100 px-2 py-0.5 rounded-full">基于属性名自动匹配</span>}
                 </h3>
                 <div className="bg-gray-50 rounded-t-lg border border-gray-200 border-b-0 px-4 py-3 grid grid-cols-3 gap-4 font-medium text-sm text-gray-500">
                     <div>源字段 (Source)</div>
                     <div className="text-center">映射关系</div>
                     <div>目标字段 (Target)</div>
                 </div>
                 <div className="border border-gray-200 rounded-b-lg divide-y divide-gray-100 bg-white max-h-[400px] overflow-y-auto">
                     {sourceColumns.map(sc => (
                         <div key={sc.name} className="grid grid-cols-3 gap-4 items-center px-4 py-3 hover:bg-gray-50 transition-colors">
                             <div className="text-sm font-mono text-text">{sc.name}</div>
                             <div className="flex justify-center text-gray-400">
                                <ArrowRightIcon className="w-4 h-4" />
                             </div>
                             <select 
                                className="border border-gray-300 rounded px-2 py-1.5 text-sm font-mono focus:ring-1 focus:ring-primary focus:border-primary outline-none"
                                value={columnMapping[sc.name] || ''}
                                onChange={(e) => {
                                    const val = e.target.value;
                                    setColumnMapping(prev => {
                                        const next = {...prev};
                                        if (val) next[sc.name] = val;
                                        else delete next[sc.name];
                                        return next;
                                    });
                                }}
                             >
                                 <option value="">(不对比)</option>
                                 {targetColumns.map(tc => (
                                     <option key={tc.name} value={tc.name}>{tc.name}</option>
                                 ))}
                             </select>
                         </div>
                     ))}
                 </div>
             </div>
         )}
         
         <div className="mt-8 flex justify-end">
             <button 
                className="bg-primary text-white px-8 py-2.5 rounded-lg hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed shadow-sm transition-all duration-200 font-medium flex items-center gap-2"
                onClick={handleRun}
                disabled={loading}
             >
                 {loading ? (
                    <>
                        <ArrowPathIcon className="w-5 h-5 animate-spin" />
                        正在对比...
                    </>
                 ) : (
                    <>
                        <CheckCircleIcon className="w-5 h-5" />
                        开始对比
                    </>
                 )}
             </button>
         </div>
      </div>
      
      {/* Result Panel */}
      {result && (
          <div className="space-y-6 animate-slide-in-right">
              {/* Summary Cards */}
              <div className="grid grid-cols-4 gap-6">
                  <div className="bg-white p-6 rounded-lg border border-gray-200 shadow-sm">
                      <div className="flex items-center gap-2 mb-2">
                        <div className="w-2 h-2 rounded-full bg-green-500"></div>
                        <div className="text-gray-500 text-sm font-medium">完全匹配</div>
                      </div>
                      <div className="text-3xl font-bold text-gray-900">{result.matchedCount}</div>
                  </div>
                  <div className="bg-white p-6 rounded-lg border border-gray-200 shadow-sm">
                      <div className="flex items-center gap-2 mb-2">
                        <div className="w-2 h-2 rounded-full bg-red-500"></div>
                        <div className="text-gray-500 text-sm font-medium">值不一致</div>
                      </div>
                      <div className="text-3xl font-bold text-gray-900">{result.mismatchedCount}</div>
                  </div>
                  <div className="bg-white p-6 rounded-lg border border-gray-200 shadow-sm">
                      <div className="flex items-center gap-2 mb-2">
                        <div className="w-2 h-2 rounded-full bg-yellow-500"></div>
                        <div className="text-gray-500 text-sm font-medium">仅源表存在</div>
                      </div>
                      <div className="text-3xl font-bold text-gray-900">{result.sourceOnlyCount}</div>
                  </div>
                  <div className="bg-white p-6 rounded-lg border border-gray-200 shadow-sm">
                      <div className="flex items-center gap-2 mb-2">
                        <div className="w-2 h-2 rounded-full bg-blue-500"></div>
                        <div className="text-gray-500 text-sm font-medium">仅目标表存在</div>
                      </div>
                      <div className="text-3xl font-bold text-gray-900">{result.targetOnlyCount}</div>
                  </div>
              </div>
              
              {/* Diff Table */}
              <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                  <div className="px-6 py-4 border-b border-gray-200 bg-gray-50 flex justify-between items-center">
                      <h3 className="font-semibold text-text">差异详情</h3>
                      <span className="bg-gray-200 text-gray-600 text-xs px-2 py-1 rounded-full">{result.diffs.length} 条记录</span>
                  </div>
                  <div className="overflow-x-auto">
                      <table className="min-w-full divide-y divide-gray-200">
                          <thead className="bg-gray-50">
                              <tr>
                                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Key Value</th>
                                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">类型</th>
                                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">详情</th>
                              </tr>
                          </thead>
                          <tbody className="bg-white divide-y divide-gray-200">
                              {result.diffs.slice(0, 100).map((diff, idx) => (
                                  <tr key={idx} className="hover:bg-gray-50 transition-colors">
                                      <td className="px-6 py-4 whitespace-nowrap text-sm font-mono font-medium text-primary">{diff.keyValue}</td>
                                      <td className="px-6 py-4 whitespace-nowrap text-sm">
                                          <span className={`px-2.5 py-0.5 inline-flex text-xs font-medium rounded-full border 
                                            ${diff.type === 'VALUE_MISMATCH' ? 'bg-red-50 text-red-700 border-red-200' : 
                                              diff.type === 'MISSING_IN_TARGET' ? 'bg-yellow-50 text-yellow-700 border-yellow-200' : 
                                              'bg-blue-50 text-blue-700 border-blue-200'}`}>
                                              {diff.type}
                                          </span>
                                      </td>
                                      <td className="px-6 py-4 text-sm text-gray-500">
                                          {diff.details ? (
                                              <ul className="space-y-1.5">
                                                  {diff.details.map((d, i) => (
                                                      <li key={i} className="flex items-center gap-2">
                                                          <span className="font-medium text-gray-700 font-mono text-xs bg-gray-100 px-1.5 py-0.5 rounded">{d.fieldName}</span>
                                                          <span className="text-gray-400 text-xs">:</span>
                                                          <span className="text-red-600 line-through bg-red-50 px-1 rounded">{String(d.sourceValue)}</span>
                                                          <ArrowRightIcon className="w-3 h-3 text-gray-400" />
                                                          <span className="text-green-600 bg-green-50 px-1 rounded">{String(d.targetValue)}</span>
                                                      </li>
                                                  ))}
                                              </ul>
                                          ) : (
                                              <span className="text-gray-400">-</span>
                                          )}
                                      </td>
                                  </tr>
                              ))}
                              {result.diffs.length > 100 && (
                                  <tr>
                                      <td colSpan={3} className="px-6 py-4 text-center text-sm text-gray-500 bg-gray-50">
                                          ... 仅显示前 100 条差异，完整报告请下载 ...
                                      </td>
                                  </tr>
                              )}
                          </tbody>
                      </table>
                  </div>
              </div>
          </div>
      )}
      
      <ToastContainer toasts={toasts} onClose={removeToast} />
    </div>
  );
}
