import { useState, useEffect } from 'react';
import type { ObjectType, Instance } from '../api/client';
import { databaseApi, mappingApi, instanceApi } from '../api/client';
import { XMarkIcon, CheckIcon, LinkIcon } from '@heroicons/react/24/outline';

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
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [step, setStep] = useState<'database' | 'table' | 'mapping'>('database');

  useEffect(() => {
    loadDatabases();
  }, []);

  useEffect(() => {
    if (selectedDatabaseId) {
      loadTables();
    }
  }, [selectedDatabaseId]);

  useEffect(() => {
    if (selectedTable && selectedDatabaseId) {
      loadColumns();
    }
  }, [selectedTable, selectedDatabaseId]);

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

      // 自动匹配：尝试根据名称匹配列和属性
      const autoMappings: Record<string, string> = {};
      columnsData.forEach((col: any) => {
        const matchingProp = objectTypeDef.properties.find(
          (prop) => prop.name.toLowerCase() === col.name.toLowerCase()
        );
        if (matchingProp) {
          autoMappings[col.name] = matchingProp.name;
        }
      });
      setMappings(autoMappings);

      // 设置主键列
      const pkColumn = columnsData.find((col: any) => col.is_primary_key);
      if (pkColumn) {
        setPrimaryKeyColumn(pkColumn.name);
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

      // 从tables中找到对应的table实例ID
      const tableInfo = tables.find((t) => t.name === selectedTable);
      if (!tableInfo || !tableInfo.id) {
        alert('无法找到表信息，请刷新页面重试');
        return;
      }

      const tableId = tableInfo.id;
      const mappingId = await mappingApi.create(
        objectType,
        tableId,
        mappings,
        primaryKeyColumn || undefined
      );
      alert('映射关系保存成功！');
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
            <div className={`flex items-center gap-2 ${step === 'database' ? 'text-blue-600' : step !== 'database' ? 'text-green-600' : 'text-gray-400'}`}>
              <div className={`w-8 h-8 rounded-full flex items-center justify-center ${step === 'database' ? 'bg-blue-600 text-white' : step !== 'database' ? 'bg-green-600 text-white' : 'bg-gray-200 text-gray-600'}`}>
                {step !== 'database' ? <CheckIcon className="w-5 h-5" /> : '1'}
              </div>
              <span className="font-medium">选择数据源</span>
            </div>
            <div className="flex-1 h-0.5 bg-gray-200">
              <div className={`h-full transition-all ${step !== 'database' ? 'bg-green-600' : 'bg-gray-200'}`} style={{ width: step !== 'database' ? '100%' : '0%' }} />
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

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-h-64 overflow-y-auto">
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
                {saving ? '保存中...' : '保存映射'}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
