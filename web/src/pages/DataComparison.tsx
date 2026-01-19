import { useState, useEffect } from 'react';
import { databaseApi, comparisonApi, schemaApi, modelApi, mappingApi } from '../api/client';
import type { ComparisonResult, ObjectType, ModelInfo, DataSourceMapping } from '../api/client';
import { ToastContainer, useToast } from '../components/Toast';

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
    <div className="max-w-7xl mx-auto">
      <div className="flex justify-between items-center mb-6">
          <h1 className="text-2xl font-bold text-gray-900">数据交叉对比 (Data Reconciliation)</h1>
          
          {/* Mode Toggle */}
          <div className="bg-gray-100 p-1 rounded-lg flex">
              <button
                  className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
                      mode === 'table' ? 'bg-white shadow text-gray-900' : 'text-gray-500 hover:text-gray-700'
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
                  基于数据表 (By Table)
              </button>
              <button
                  className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
                      mode === 'model' ? 'bg-white shadow text-blue-600' : 'text-gray-500 hover:text-gray-700'
                  }`}
                  onClick={() => {
                      setMode('model');
                      setSourceDatabaseId('');
                      setTargetDatabaseId('');
                      setSourceTableId('');
                      setTargetTableId('');
                  }}
              >
                  基于对象模型 (By Model)
              </button>
          </div>
      </div>
      
      {/* Config Panel */}
      <div className="bg-white rounded-lg shadow p-6 mb-6">
         <div className="grid grid-cols-2 gap-8">
             {/* Source */}
             <div>
                 <h3 className="font-semibold mb-4 text-blue-700">基准 (Source)</h3>
                 
                 {mode === 'table' ? (
                     <>
                         <div className="mb-4">
                             <label className="block text-sm font-medium text-gray-700 mb-1">选择数据源 (Data Source)</label>
                             <select 
                                className="w-full border rounded px-3 py-2"
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
                         <div className="mb-4">
                             <label className="block text-sm font-medium text-gray-700 mb-1">选择数据表 (Table)</label>
                             <select 
                                className="w-full border rounded px-3 py-2"
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
                     </>
                 ) : (
                     <>
                        <div className="mb-4">
                             <label className="block text-sm font-medium text-gray-700 mb-1">选择工作空间 (Workspace)</label>
                             <select 
                                className="w-full border rounded px-3 py-2 bg-blue-50"
                                value={sourceWorkspace}
                                onChange={(e) => handleSourceWorkspaceChange(e.target.value)}
                             >
                                 <option value="">-- 请选择 --</option>
                                 {models.map(m => <option key={m.id} value={m.id}>{m.displayName || m.id}</option>)}
                             </select>
                        </div>
                        <div className="mb-4">
                             <label className="block text-sm font-medium text-gray-700 mb-1">选择对象模型 (Object Type)</label>
                             <select 
                                className="w-full border rounded px-3 py-2 bg-blue-50"
                                value={sourceModelName}
                                onChange={(e) => handleSourceModelChange(e.target.value)}
                                disabled={!sourceWorkspace}
                             >
                                 <option value="">-- 请选择 --</option>
                                 {sourceObjectTypes.map(t => <option key={t.name} value={t.name}>{t.display_name || t.name}</option>)}
                             </select>
                             {sourceTableId && (
                                 <div className="mt-1 text-xs text-gray-500">
                                     已映射到表: {tables.find(t => t.id === sourceTableId)?.displayName}
                                 </div>
                             )}
                        </div>
                     </>
                 )}
                 
                 <div className="mb-4">
                     <label className="block text-sm font-medium text-gray-700 mb-1">
                         {mode === 'model' ? '主键列 (Mapped Key)' : '主键 (Key)'}
                     </label>
                     <select 
                        className="w-full border rounded px-3 py-2"
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
                 <h3 className="font-semibold mb-4 text-green-700">对比目标 (Target)</h3>
                 
                 {mode === 'table' ? (
                     <>
                         <div className="mb-4">
                             <label className="block text-sm font-medium text-gray-700 mb-1">选择数据源 (Data Source)</label>
                             <select 
                                className="w-full border rounded px-3 py-2"
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
                         <div className="mb-4">
                             <label className="block text-sm font-medium text-gray-700 mb-1">选择数据表 (Table)</label>
                             <select 
                                className="w-full border rounded px-3 py-2"
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
                     </>
                 ) : (
                     <>
                        <div className="mb-4">
                             <label className="block text-sm font-medium text-gray-700 mb-1">选择工作空间 (Workspace)</label>
                             <select 
                                className="w-full border rounded px-3 py-2 bg-green-50"
                                value={targetWorkspace}
                                onChange={(e) => handleTargetWorkspaceChange(e.target.value)}
                             >
                                 <option value="">-- 请选择 --</option>
                                 {models.map(m => <option key={m.id} value={m.id}>{m.displayName || m.id}</option>)}
                             </select>
                        </div>
                        <div className="mb-4">
                             <label className="block text-sm font-medium text-gray-700 mb-1">选择对象模型 (Object Type)</label>
                             <select 
                                className="w-full border rounded px-3 py-2 bg-green-50"
                                value={targetModelName}
                                onChange={(e) => handleTargetModelChange(e.target.value)}
                                disabled={!targetWorkspace}
                             >
                                 <option value="">-- 请选择 --</option>
                                 {targetObjectTypes.map(t => <option key={t.name} value={t.name}>{t.display_name || t.name}</option>)}
                             </select>
                             {targetTableId && (
                                 <div className="mt-1 text-xs text-gray-500">
                                     已映射到表: {tables.find(t => t.id === targetTableId)?.displayName}
                                 </div>
                             )}
                        </div>
                     </>
                 )}

                 <div className="mb-4">
                     <label className="block text-sm font-medium text-gray-700 mb-1">
                         {mode === 'model' ? '主键列 (Mapped Key)' : '主键 (Key)'}
                     </label>
                     <select 
                        className="w-full border rounded px-3 py-2"
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
             <div className="mt-6 border-t pt-6">
                 <h3 className="font-semibold mb-4">字段映射配置 {mode === 'model' && '(基于属性名自动匹配)'}</h3>
                 <div className="grid grid-cols-3 gap-4 mb-2 font-medium text-sm text-gray-500">
                     <div>源字段 (Source)</div>
                     <div className="text-center">映射关系</div>
                     <div>目标字段 (Target)</div>
                 </div>
                 <div className="space-y-2 max-h-60 overflow-y-auto">
                     {sourceColumns.map(sc => (
                         <div key={sc.name} className="grid grid-cols-3 gap-4 items-center">
                             <div className="text-sm">{sc.name}</div>
                             <div className="flex justify-center">→</div>
                             <select 
                                className="border rounded px-2 py-1 text-sm"
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
         
         <div className="mt-6 flex justify-end">
             <button 
                className="bg-blue-600 text-white px-6 py-2 rounded hover:bg-blue-700 disabled:opacity-50"
                onClick={handleRun}
                disabled={loading}
             >
                 {loading ? '正在对比...' : '开始对比'}
             </button>
         </div>
      </div>
      
      {/* Result Panel */}
      {result && (
          <div className="space-y-6">
              {/* Summary Cards */}
              <div className="grid grid-cols-4 gap-4">
                  <div className="bg-green-50 p-4 rounded border border-green-200">
                      <div className="text-green-800 text-sm font-medium">完全匹配</div>
                      <div className="text-2xl font-bold text-green-900">{result.matchedCount}</div>
                  </div>
                  <div className="bg-red-50 p-4 rounded border border-red-200">
                      <div className="text-red-800 text-sm font-medium">值不一致</div>
                      <div className="text-2xl font-bold text-red-900">{result.mismatchedCount}</div>
                  </div>
                  <div className="bg-yellow-50 p-4 rounded border border-yellow-200">
                      <div className="text-yellow-800 text-sm font-medium">仅源表存在</div>
                      <div className="text-2xl font-bold text-yellow-900">{result.sourceOnlyCount}</div>
                  </div>
                  <div className="bg-blue-50 p-4 rounded border border-blue-200">
                      <div className="text-blue-800 text-sm font-medium">仅目标表存在</div>
                      <div className="text-2xl font-bold text-blue-900">{result.targetOnlyCount}</div>
                  </div>
              </div>
              
              {/* Diff Table */}
              <div className="bg-white rounded-lg shadow overflow-hidden">
                  <div className="px-6 py-4 border-b border-gray-200">
                      <h3 className="font-semibold">差异详情 ({result.diffs.length})</h3>
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
                                  <tr key={idx}>
                                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{diff.keyValue}</td>
                                      <td className="px-6 py-4 whitespace-nowrap text-sm">
                                          <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full 
                                            ${diff.type === 'VALUE_MISMATCH' ? 'bg-red-100 text-red-800' : 
                                              diff.type === 'MISSING_IN_TARGET' ? 'bg-yellow-100 text-yellow-800' : 
                                              'bg-blue-100 text-blue-800'}`}>
                                              {diff.type}
                                          </span>
                                      </td>
                                      <td className="px-6 py-4 text-sm text-gray-500">
                                          {diff.details ? (
                                              <ul className="list-disc pl-4 space-y-1">
                                                  {diff.details.map((d, i) => (
                                                      <li key={i}>
                                                          <span className="font-medium">{d.fieldName}:</span> 
                                                          <span className="text-red-600 line-through mx-2">{String(d.sourceValue)}</span>
                                                          <span>→</span>
                                                          <span className="text-green-600 mx-2">{String(d.targetValue)}</span>
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
                                      <td colSpan={3} className="px-6 py-4 text-center text-gray-500">
                                          ... 仅显示前 100 条差异 ...
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
