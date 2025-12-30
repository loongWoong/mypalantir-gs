import { useEffect, useState } from 'react';
import { useParams, Link, useSearchParams } from 'react-router-dom';
import type { Instance, ObjectType } from '../api/client';
import { instanceApi, schemaApi, databaseApi, mappingApi } from '../api/client';
import { PlusIcon, PencilIcon, TrashIcon, ArrowPathIcon, CloudArrowDownIcon, XMarkIcon, LinkIcon, ArrowDownTrayIcon } from '@heroicons/react/24/outline';
import InstanceForm from '../components/InstanceForm';
import DataMappingDialog from '../components/DataMappingDialog';

export default function InstanceList() {
  const { objectType } = useParams<{ objectType: string }>();
  const [searchParams] = useSearchParams();
  const mappingId = searchParams.get('mappingId');
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
  const limit = 20;

  // 判断是否为系统对象类型（不需要关联按钮）
  const isSystemObjectType = (type: string | undefined) => {
    return type === 'database' || type === 'table' || type === 'column' || type === 'mapping';
  };

  useEffect(() => {
    if (objectType) {
      loadData();
    }
  }, [objectType, offset, mappingId]);

  const loadData = async () => {
    if (!objectType) return;
    try {
      setLoading(true);
      const objectTypeData = await schemaApi.getObjectType(objectType);
      setObjectTypeDef(objectTypeData);
      
      let instancesData;
      if (mappingId) {
        // 使用mapping查询数据
        instancesData = await instanceApi.listWithMapping(objectType, mappingId, offset, limit);
        setFromMapping(true);
      } else {
        // 常规查询
        instancesData = await instanceApi.list(objectType, offset, limit);
        setFromMapping(false);
      }
      
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

  const handleSyncClick = async () => {
    if (objectType !== 'database') return;
    
    try {
      // 加载所有数据库实例供选择
      const dbList = await instanceApi.list('database', 0, 100);
      setDatabases(dbList.items);
      setShowSyncDialog(true);
    } catch (error) {
      console.error('Failed to load databases:', error);
      alert('无法加载数据源列表');
    }
  };

  const handleSync = async () => {
    if (!selectedDatabaseId) {
      alert('请选择数据源');
      return;
    }

    try {
      setSyncing(true);
      const result = await databaseApi.syncTables(selectedDatabaseId);
      alert(`同步完成！\n创建表: ${result.tables_created}\n创建列: ${result.columns_created}\n更新列: ${result.columns_updated}`);
      setShowSyncDialog(false);
      setSelectedDatabaseId('');
      loadData(); // 刷新列表
    } catch (error: any) {
      console.error('Failed to sync tables:', error);
      alert('同步失败: ' + (error.response?.data?.message || error.message));
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
      alert('数据抽取完成！已从数据库同步数据到实例中。');
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

  if (loading) {
    return <div className="text-center py-12">Loading...</div>;
  }

  if (!objectTypeDef) {
    return <div className="text-center py-12">Object type not found</div>;
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-gray-900">{objectTypeDef.name}</h1>
            {fromMapping && (
              <span className="px-3 py-1 bg-green-100 text-green-700 rounded-lg text-sm font-medium">
                数据源映射
              </span>
            )}
          </div>
          {objectTypeDef.description && (
            <p className="text-gray-600 mt-1">{objectTypeDef.description}</p>
          )}
          {fromMapping && (
            <p className="text-sm text-gray-500 mt-1">
              数据来自数据库映射，实时查询
            </p>
          )}
        </div>
        <div className="flex gap-2">
          {objectType === 'database' && (
            <button
              onClick={handleSyncClick}
              className="flex items-center px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
            >
              <CloudArrowDownIcon className="w-5 h-5 mr-2" />
              同步表信息
            </button>
          )}
          {!isSystemObjectType(objectType) && (
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
          {fromMapping && (
            <button
              onClick={() => {
                const newParams = new URLSearchParams(searchParams);
                newParams.delete('mappingId');
                window.location.href = `/instances/${objectType}`;
              }}
              className="flex items-center px-4 py-2 bg-gray-600 text-white rounded-lg hover:bg-gray-700 transition-colors"
            >
              <ArrowPathIcon className="w-5 h-5 mr-2" />
              查看本地实例
            </button>
          )}
          <button
            onClick={handleCreate}
            className="flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <PlusIcon className="w-5 h-5 mr-2" />
            Create Instance
          </button>
        </div>
      </div>

      {showForm && (
        <InstanceForm
          objectType={objectTypeDef}
          instance={editingInstance}
          onClose={handleFormClose}
        />
      )}

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  ID
                </th>
                {objectTypeDef.properties.map((prop) => (
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
                    colSpan={objectTypeDef.properties.length + 2}
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
                        to={`/instances/${objectType}/${instance.id}`}
                        className="text-blue-600 hover:text-blue-800 text-sm font-mono"
                      >
                        {instance.id.substring(0, 8)}...
                      </Link>
                    </td>
                    {objectTypeDef.properties.map((prop) => (
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

        {total > limit && (
          <div className="bg-gray-50 px-6 py-3 flex items-center justify-between border-t border-gray-200">
            <div className="text-sm text-gray-700">
              Showing {offset + 1} to {Math.min(offset + limit, total)} of {total} instances
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => setOffset(Math.max(0, offset - limit))}
                disabled={offset === 0}
                className="px-3 py-1 text-sm border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Previous
              </button>
              <button
                onClick={() => setOffset(offset + limit)}
                disabled={offset + limit >= total}
                className="px-3 py-1 text-sm border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

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
    </div>
  );
}

