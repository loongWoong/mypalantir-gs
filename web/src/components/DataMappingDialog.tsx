import { useState, useEffect, useRef } from 'react';
import type { ObjectType, Instance } from '../api/client';
import { databaseApi, mappingApi, instanceApi } from '../api/client';
import { XMarkIcon, CheckIcon, LinkIcon } from '@heroicons/react/24/outline';
import { ToastContainer, useToast } from './Toast';

interface Column {
  name: string;
  data_type: string;
  nullable: boolean;
  is_primary_key: boolean;
}

interface DataMappingDialogProps {
  objectType: string;
  objectTypeDef: ObjectType;
  onClose: () => void;
  onSuccess: () => void;
}

export default function DataMappingDialog({
  objectType,
  objectTypeDef,
  onClose,
  onSuccess,
}: DataMappingDialogProps) {
  const [databases, setDatabases] = useState<Instance[]>([]);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string>('');
  const [tables, setTables] = useState<any[]>([]);
  const [selectedTable, setSelectedTable] = useState<string | null>(null);
  const [columns, setColumns] = useState<Column[]>([]);
  const [mappings, setMappings] = useState<Record<string, string>>({});
  const [primaryKeyColumn, setPrimaryKeyColumn] = useState<string>('');
  const [existingMappingId, setExistingMappingId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [step, setStep] = useState<'database' | 'table' | 'mapping'>('database');
  const [isLoadingExistingMapping, setIsLoadingExistingMapping] = useState(false);
  const hasLoadedExistingMapping = useRef(false);
  const { toasts, showToast, removeToast } = useToast();

  useEffect(() => {
    loadDatabases();
  }, []);

  // 加载已有的映射关系（在数据库列表加载完成后，只执行一次）
  useEffect(() => {
    if (databases.length > 0 && !hasLoadedExistingMapping.current) {
      loadExistingMapping();
    }
  }, [databases.length]);

  // 加载已有的映射关系
  const loadExistingMapping = async () => {
    // 如果数据库列表还没加载完成，或已经加载过，直接返回
    if (databases.length === 0 || hasLoadedExistingMapping.current) {
      return;
    }
    
    try {
      hasLoadedExistingMapping.current = true;
      setIsLoadingExistingMapping(true);
      const existingMappings = await mappingApi.getByObjectType(objectType);
      if (existingMappings && existingMappings.length > 0) {
        // 使用第一个映射关系
        const mapping = existingMappings[0];
        const tableId = mapping.table_id;
        
        if (tableId) {
          // 获取表信息
          const table = await instanceApi.get('table', tableId);
          const tableName = table.name;
          const databaseId = table.database_id;
          
          if (databaseId && tableName) {
            // 检查数据库是否在列表中
            const dbExists = databases.some(db => db.id === databaseId);
            if (dbExists) {
              // 保存已有的映射 ID，用于后续更新
              if (mapping.id) {
                setExistingMappingId(mapping.id);
              }
              
              // 先设置数据库（这会触发 loadTables，但会被 isLoadingExistingMapping 阻止）
              setSelectedDatabaseId(databaseId);
              
              // 等待一下，确保状态更新
              await new Promise(resolve => setTimeout(resolve, 50));
              
              // 加载表列表
              const tablesData = await databaseApi.getTables(databaseId);
              setTables(tablesData);
              
              // 等待一下，确保表列表已设置
              await new Promise(resolve => setTimeout(resolve, 50));
              
              // 设置表（这会触发 loadColumns，但会被 isLoadingExistingMapping 阻止）
              setSelectedTable(tableName);
              
              // 等待一下，确保状态更新
              await new Promise(resolve => setTimeout(resolve, 50));
              
              // 加载列信息
              const columnsData = await databaseApi.getColumns(databaseId, tableName);
              setColumns(columnsData.map((col: any) => ({
                name: col.name,
                data_type: col.data_type,
                nullable: col.nullable,
                is_primary_key: col.is_primary_key || false,
              })));
              
              // 回显字段映射关系（column_property_mappings 是 {列名: 属性名}）
              if (mapping.column_property_mappings) {
                setMappings(mapping.column_property_mappings);
              }
              
              // 回显主键列
              if (mapping.primary_key_column) {
                setPrimaryKeyColumn(mapping.primary_key_column);
              }
              
              // 最后跳转到映射配置步骤
              setStep('mapping');
            }
          }
        }
      }
    } catch (error) {
      console.error('Failed to load existing mapping:', error);
      hasLoadedExistingMapping.current = false; // 失败时重置，允许重试
      // 如果加载失败，不影响正常流程
    } finally {
      setIsLoadingExistingMapping(false);
    }
  };

  // 当只有一个数据源时，自动选择（只有在没有加载已有映射时）
  useEffect(() => {
    if (databases.length === 1 && !selectedDatabaseId && !isLoadingExistingMapping) {
      setSelectedDatabaseId(databases[0].id);
    }
  }, [databases, selectedDatabaseId, isLoadingExistingMapping]);

  // 加载表列表（只有在不是加载已有映射时）
  useEffect(() => {
    if (selectedDatabaseId && !isLoadingExistingMapping) {
      loadTables();
    }
  }, [selectedDatabaseId, isLoadingExistingMapping]);

  // 加载列信息（只有在不是加载已有映射时）
  useEffect(() => {
    if (selectedTable && selectedDatabaseId && !isLoadingExistingMapping) {
      loadColumns();
    }
  }, [selectedTable, selectedDatabaseId, isLoadingExistingMapping]);

  const loadDatabases = async () => {
    try {
      setLoading(true);
      const dbList = await instanceApi.list('database', 0, 100);
      setDatabases(dbList.items);
    } catch (error) {
      console.error('Failed to load databases:', error);
      alert('无法加载数据源列表');
    } finally {
      setLoading(false);
    }
  };

  const loadTables = async () => {
    if (!selectedDatabaseId) return;
    try {
      setLoading(true);
      const tablesData = await databaseApi.getTables(selectedDatabaseId);
      setTables(tablesData);
    } catch (error) {
      console.error('Failed to load tables:', error);
      alert('无法加载表列表');
    } finally {
      setLoading(false);
    }
  };

  // 工具函数：将驼峰命名转换为下划线命名
  const camelToSnake = (str: string): string => {
    return str.replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`).replace(/^_/, '');
  };

  // 工具函数：将下划线命名转换为驼峰命名
  const snakeToCamel = (str: string): string => {
    return str.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase());
  };

  // 工具函数：标准化名称用于匹配（统一转为小写）
  const normalizeName = (name: string): string => {
    return name.toLowerCase();
  };

  // 工具函数：判断两个名称是否匹配（支持完全匹配、驼峰/下划线互转）
  const isNameMatch = (name1: string, name2: string): boolean => {
    const n1 = normalizeName(name1);
    const n2 = normalizeName(name2);
    
    // 完全匹配（忽略大小写）
    if (n1 === n2) {
      return true;
    }
    
    // 驼峰转下划线后匹配
    const snake1 = normalizeName(camelToSnake(name1));
    const snake2 = normalizeName(camelToSnake(name2));
    if (snake1 === n2 || n1 === snake2 || snake1 === snake2) {
      return true;
    }
    
    // 下划线转驼峰后匹配
    const camel1 = normalizeName(snakeToCamel(name1));
    const camel2 = normalizeName(snakeToCamel(name2));
    if (camel1 === n2 || n1 === camel2 || camel1 === camel2) {
      return true;
    }
    
    return false;
  };

  const loadColumns = async () => {
    if (!selectedTable || !selectedDatabaseId) return;
    try {
      setLoading(true);
      const columnsData = await databaseApi.getColumns(selectedDatabaseId, selectedTable);
      setColumns(columnsData.map((col: any) => ({
        name: col.name,
        data_type: col.data_type,
        nullable: col.nullable,
        is_primary_key: col.is_primary_key || false,
      })));

      // 只有在没有已有映射时才进行自动匹配
      const hasExistingMappings = Object.keys(mappings).length > 0;
      if (!hasExistingMappings) {
        // 自动匹配：尝试根据名称匹配列和属性（支持完全匹配、驼峰/下划线互转）
        const autoMappings: Record<string, string> = {};
        columnsData.forEach((col: any) => {
          const matchingProp = objectTypeDef.properties.find(
            (prop) => isNameMatch(prop.name, col.name)
          );
          if (matchingProp) {
            autoMappings[col.name] = matchingProp.name;
          }
        });
        setMappings(autoMappings);

        // 设置主键列（只有在没有已有主键列时才设置）
        if (!primaryKeyColumn) {
          const pkColumn = columnsData.find((col: any) => col.is_primary_key);
          if (pkColumn) {
            setPrimaryKeyColumn(pkColumn.name);
          }
        }
      }
    } catch (error) {
      console.error('Failed to load columns:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDatabaseSelect = () => {
    if (selectedDatabaseId) {
      setStep('table');
    }
  };

  const handleTableSelect = (tableName: string) => {
    setSelectedTable(tableName);
    setStep('mapping');
  };

  const handleMappingChange = (columnName: string, propertyName: string) => {
    const newMappings = { ...mappings };
    if (propertyName) {
      newMappings[columnName] = propertyName;
    } else {
      delete newMappings[columnName];
    }
    setMappings(newMappings);
  };

  const handleSave = async () => {
    if (!selectedTable || !selectedDatabaseId) return;

    try {
      setSaving(true);

      // 如果已有映射 ID，则更新；否则创建新的
      if (existingMappingId) {
        // 更新已有映射
        await mappingApi.update(
          existingMappingId,
          mappings,
          primaryKeyColumn || undefined
        );
        showToast('映射关系更新成功！', 'success');
      } else {
        // 创建新映射
        // 从tables中找到对应的table实例ID
        const tableInfo = tables.find((t) => t.name === selectedTable);
        if (!tableInfo || !tableInfo.id) {
          alert('无法找到表信息，请刷新页面重试');
          return;
        }

        const tableId = tableInfo.id;
        await mappingApi.create(
          objectType,
          tableId,
          mappings,
          primaryKeyColumn || undefined
        );
        showToast('映射关系保存成功！', 'success');
      }
      
      onSuccess();
      onClose();
    } catch (error: any) {
      console.error('Failed to save mapping:', error);
      alert('保存失败: ' + (error.response?.data?.message || error.message));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-6xl mx-4 max-h-[90vh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <div>
            <h2 className="text-2xl font-bold text-gray-900">数据映射 - {objectTypeDef.name}</h2>
            <p className="text-gray-600 mt-1">配置数据库表与对象属性的映射关系</p>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <XMarkIcon className="w-6 h-6" />
          </button>
        </div>

        {/* Steps */}
        <div className="px-6 py-4 bg-gray-50 border-b border-gray-200">
          <div className="flex items-center gap-4">
            <div className={`flex items-center gap-2 ${step === 'database' ? 'text-blue-600' : (step === 'table' || step === 'mapping') ? 'text-green-600' : 'text-gray-400'}`}>
              <div className={`w-8 h-8 rounded-full flex items-center justify-center ${step === 'database' ? 'bg-blue-600 text-white' : (step === 'table' || step === 'mapping') ? 'bg-green-600 text-white' : 'bg-gray-200 text-gray-600'}`}>
                {(step === 'table' || step === 'mapping') ? <CheckIcon className="w-5 h-5" /> : '1'}
              </div>
              <span className="font-medium">选择数据源</span>
            </div>
            <div className="flex-1 h-0.5 bg-gray-200">
              <div className={`h-full transition-all ${(step === 'table' || step === 'mapping') ? 'bg-green-600' : 'bg-gray-200'}`} style={{ width: (step === 'table' || step === 'mapping') ? '100%' : '0%' }} />
            </div>
            <div className={`flex items-center gap-2 ${step === 'table' ? 'text-blue-600' : step === 'mapping' ? 'text-green-600' : 'text-gray-400'}`}>
              <div className={`w-8 h-8 rounded-full flex items-center justify-center ${step === 'table' ? 'bg-blue-600 text-white' : step === 'mapping' ? 'bg-green-600 text-white' : 'bg-gray-200 text-gray-600'}`}>
                {step === 'mapping' ? <CheckIcon className="w-5 h-5" /> : '2'}
              </div>
              <span className="font-medium">选择表</span>
            </div>
            <div className="flex-1 h-0.5 bg-gray-200">
              <div className={`h-full transition-all ${step === 'mapping' ? 'bg-green-600' : 'bg-gray-200'}`} style={{ width: step === 'mapping' ? '100%' : '0%' }} />
            </div>
            <div className={`flex items-center gap-2 ${step === 'mapping' ? 'text-blue-600' : 'text-gray-400'}`}>
              <div className={`w-8 h-8 rounded-full flex items-center justify-center ${step === 'mapping' ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-600'}`}>
                3
              </div>
              <span className="font-medium">配置映射</span>
            </div>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          {step === 'database' && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                选择数据源
              </label>
              <select
                value={selectedDatabaseId}
                onChange={(e) => setSelectedDatabaseId(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              >
                <option value="">-- 请选择数据源 --</option>
                {databases.map((db) => (
                  <option key={db.id} value={db.id}>
                    {db.name || db.id} {db.host && `(${db.host}:${db.port})`}
                  </option>
                ))}
              </select>
            </div>
          )}

          {step === 'table' && (
            <div>
              <h3 className="text-lg font-semibold text-gray-900 mb-4">选择数据表</h3>
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 max-h-96 overflow-y-auto">
                {tables.length === 0 ? (
                  <p className="text-gray-500 text-center py-8 col-span-full">暂无数据表</p>
                ) : (
                  tables.map((table) => (
                    <button
                      key={table.name}
                      onClick={() => handleTableSelect(table.name)}
                      className={`text-left px-4 py-3 rounded-lg transition-colors border ${
                        selectedTable === table.name
                          ? 'bg-blue-50 text-blue-700 border-blue-200'
                          : 'hover:bg-gray-50 text-gray-700 border-gray-200'
                      }`}
                    >
                      <div className="font-medium">{table.name}</div>
                      <div className="text-sm text-gray-500">{table.type}</div>
                    </button>
                  ))
                )}
              </div>
            </div>
          )}

          {step === 'mapping' && selectedTable && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* 左侧：表结构 ER图 */}
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
                  <LinkIcon className="w-5 h-5" />
                  数据表结构
                </h3>
                <div className="bg-blue-50 rounded-lg p-4 mb-4 border-2 border-blue-200">
                  <div className="font-bold text-blue-900 text-lg mb-2">{selectedTable}</div>
                  <div className="text-sm text-blue-700">数据表</div>
                </div>
                <div className="space-y-2 max-h-96 overflow-y-auto">
                  {columns.map((column) => (
                    <div
                      key={column.name}
                      className={`p-3 rounded-lg border-2 ${
                        mappings[column.name]
                          ? 'bg-green-50 border-green-300'
                          : 'bg-gray-50 border-gray-200'
                      }`}
                    >
                      <div className="flex items-center justify-between mb-1">
                        <span className="font-medium text-gray-900">{column.name}</span>
                        <div className="flex gap-1">
                          <span className="text-xs px-2 py-1 bg-gray-200 text-gray-700 rounded">
                            {column.data_type}
                          </span>
                          {column.is_primary_key && (
                            <span className="text-xs px-2 py-1 bg-yellow-100 text-yellow-700 rounded">
                            主键
                          </span>
                          )}
                        </div>
                      </div>
                      {mappings[column.name] && (
                        <div className="text-xs text-green-600 mt-1">
                          → 映射到: {mappings[column.name]}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              {/* 右侧：对象属性 ER图 */}
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
                  <LinkIcon className="w-5 h-5" />
                  对象属性结构
                </h3>
                <div className="bg-purple-50 rounded-lg p-4 mb-4 border-2 border-purple-200">
                  <div className="font-bold text-purple-900 text-lg mb-2">{objectTypeDef.name}</div>
                  <div className="text-sm text-purple-700">对象类型</div>
                </div>
                <div className="space-y-2 max-h-96 overflow-y-auto">
                  {objectTypeDef.properties.map((prop) => {
                    const mappedColumn = Object.keys(mappings).find(
                      (col) => mappings[col] === prop.name
                    );
                    return (
                      <div
                        key={prop.name}
                        className={`p-3 rounded-lg border-2 ${
                          mappedColumn
                            ? 'bg-green-50 border-green-300'
                            : 'bg-gray-50 border-gray-200'
                        }`}
                      >
                        <div className="flex items-center justify-between mb-1">
                          <span className="font-medium text-gray-900">{prop.name}</span>
                          <span className="text-xs px-2 py-1 bg-gray-200 text-gray-700 rounded">
                            {prop.data_type}
                          </span>
                        </div>
                        {mappedColumn && (
                          <div className="text-xs text-green-600 mt-1">
                            ← 映射自: {mappedColumn}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* 映射配置区域 */}
              <div className="lg:col-span-2 bg-white rounded-lg border border-gray-200 p-4">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">字段映射配置</h3>
                <div className="mb-4">
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    主键列（用于生成实例ID）
                  </label>
                  <select
                    value={primaryKeyColumn}
                    onChange={(e) => setPrimaryKeyColumn(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  >
                    <option value="">无</option>
                    {columns.map((col) => (
                      <option key={col.name} value={col.name}>
                        {col.name} {col.is_primary_key && '(主键)'}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-h-[600px] overflow-y-auto">
                  {columns.map((column) => (
                    <div
                      key={column.name}
                      className="p-3 bg-gray-50 rounded-lg border border-gray-200"
                    >
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-gray-900">{column.name}</span>
                          <span className="text-xs px-2 py-1 bg-gray-200 text-gray-700 rounded">
                            {column.data_type}
                          </span>
                          {column.is_primary_key && (
                            <span className="text-xs px-2 py-1 bg-yellow-100 text-yellow-700 rounded">
                              主键
                            </span>
                          )}
                        </div>
                      </div>
                      <select
                        value={mappings[column.name] || ''}
                        onChange={(e) => handleMappingChange(column.name, e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      >
                        <option value="">-- 未映射 --</option>
                        {objectTypeDef.properties.map((prop) => (
                          <option key={prop.name} value={prop.name}>
                            {prop.name} ({prop.data_type})
                          </option>
                        ))}
                      </select>
                      {mappings[column.name] && (
                        <div className="mt-2 flex items-center text-sm text-green-600">
                          <CheckIcon className="w-4 h-4 mr-1" />
                          已映射到 {mappings[column.name]}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between p-6 border-t border-gray-200 bg-gray-50">
          <div className="flex gap-3">
            {step !== 'database' && (
              <button
                onClick={() => {
                  if (step === 'mapping') {
                    setStep('table');
                  } else if (step === 'table') {
                    setStep('database');
                  }
                }}
                className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                上一步
              </button>
            )}
            <button
              onClick={onClose}
              className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50"
            >
              取消
            </button>
          </div>
          <div className="flex gap-3">
            {step === 'database' && (
              <button
                onClick={handleDatabaseSelect}
                disabled={!selectedDatabaseId || loading}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                下一步
              </button>
            )}
            {step === 'mapping' && (
              <button
                onClick={handleSave}
                disabled={saving || Object.keys(mappings).length === 0}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {saving 
                  ? (existingMappingId ? '更新中...' : '保存中...') 
                  : (existingMappingId ? '更新映射' : '保存映射')}
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Toast 通知 */}
      <ToastContainer toasts={toasts} onClose={removeToast} />
    </div>
  );
}
