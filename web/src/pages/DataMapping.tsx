import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import type { ObjectType, Property } from '../api/client';
import { schemaApi, databaseApi, mappingApi, instanceApi } from '../api/client';
import { XMarkIcon, CheckIcon } from '@heroicons/react/24/outline';

interface Column {
  name: string;
  data_type: string;
  nullable: boolean;
  is_primary_key: boolean;
}

export default function DataMapping() {
  const { objectType } = useParams<{ objectType: string }>();
  const navigate = useNavigate();
  const [objectTypeDef, setObjectTypeDef] = useState<ObjectType | null>(null);
  const [tables, setTables] = useState<any[]>([]);
  const [selectedTable, setSelectedTable] = useState<string | null>(null);
  const [columns, setColumns] = useState<Column[]>([]);
  const [mappings, setMappings] = useState<Record<string, string>>({});
  const [primaryKeyColumn, setPrimaryKeyColumn] = useState<string>('');
  const [databaseId, setDatabaseId] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (objectType) {
      loadData();
    }
  }, [objectType]);

  const loadData = async () => {
    if (!objectType) return;
    try {
      setLoading(true);
      const objectTypeData = await schemaApi.getObjectType(objectType);
      setObjectTypeDef(objectTypeData);
      
      // 获取默认数据库ID
      try {
        const dbInfo = await databaseApi.getDefaultDatabaseId();
        const defaultDbId = dbInfo.id;
        setDatabaseId(defaultDbId);
        
        // 加载表列表
        const tablesData = await databaseApi.getTables(defaultDbId);
        setTables(tablesData);
      } catch (error) {
        console.error('Failed to load database info:', error);
        alert('无法连接到数据库，请检查配置');
      }
    } catch (error) {
      console.error('Failed to load data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleTableSelect = async (tableName: string) => {
    setSelectedTable(tableName);
    try {
      const columnsData = await databaseApi.getColumns(databaseId || '', tableName);
      setColumns(columnsData.map((col: any) => ({
        name: col.name,
        data_type: col.data_type,
        nullable: col.nullable,
        is_primary_key: col.is_primary_key || false,
      })));
      
      // 自动匹配：尝试根据名称匹配列和属性
      if (objectTypeDef) {
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
      }
    } catch (error) {
      console.error('Failed to load columns:', error);
    }
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
    if (!selectedTable || !objectType || !databaseId) return;
    
    try {
      setSaving(true);
      
      // 从tables中找到对应的table实例ID
      const tableInfo = tables.find(t => t.name === selectedTable);
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
      navigate(`/instances/${objectType}?mappingId=${mappingId}`);
    } catch (error: any) {
      console.error('Failed to save mapping:', error);
      alert('保存失败: ' + (error.response?.data?.message || error.message));
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="text-center py-12">Loading...</div>;
  }

  if (!objectTypeDef) {
    return <div className="text-center py-12">Object type not found</div>;
  }

  return (
    <div className="max-w-7xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">数据映射 - {objectTypeDef.name}</h1>
          <p className="text-gray-600 mt-1">配置数据库表与对象属性的映射关系</p>
        </div>
        <button
          onClick={() => navigate(-1)}
          className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50"
        >
          <XMarkIcon className="w-5 h-5 inline mr-2" />
          取消
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 左侧：表选择 */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">选择数据表</h2>
          <div className="space-y-2 max-h-96 overflow-y-auto">
            {tables.length === 0 ? (
              <p className="text-gray-500 text-center py-8">暂无数据表</p>
            ) : (
              tables.map((table) => (
                <button
                  key={table.name}
                  onClick={() => handleTableSelect(table.name)}
                  className={`w-full text-left px-3 py-2 rounded-lg transition-colors ${
                    selectedTable === table.name
                      ? 'bg-blue-50 text-blue-700 border border-blue-200'
                      : 'hover:bg-gray-50 text-gray-700 border border-transparent'
                  }`}
                >
                  <div className="font-medium">{table.name}</div>
                  <div className="text-sm text-gray-500">{table.type}</div>
                </button>
              ))
            )}
          </div>
        </div>

        {/* 右侧：映射配置 */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">映射关系</h2>
          
          {selectedTable ? (
            <div>
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

              <div className="space-y-3 max-h-96 overflow-y-auto">
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

              <div className="mt-6 flex gap-3">
                <button
                  onClick={handleSave}
                  disabled={saving || Object.keys(mappings).length === 0}
                  className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {saving ? '保存中...' : '保存映射'}
                </button>
                <button
                  onClick={() => navigate(`/instances/${objectType}`)}
                  className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50"
                >
                  查看实例
                </button>
              </div>
            </div>
          ) : (
            <div className="text-center py-12 text-gray-500">
              <p>请先选择一个数据表</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
