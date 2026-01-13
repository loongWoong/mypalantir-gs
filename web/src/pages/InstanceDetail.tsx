import { useEffect, useState } from 'react';
import { useParams, Link as RouterLink, useNavigate } from 'react-router-dom';
import type { Instance, ObjectType, QueryRequest } from '../api/client';
import { instanceApi, schemaApi, queryApi, linkApi } from '../api/client';
import { ArrowLeftIcon, PencilIcon, TrashIcon, LinkIcon, CircleStackIcon } from '@heroicons/react/24/outline';
import InstanceForm from '../components/InstanceForm';
import { ToastContainer, useToast } from '../components/Toast';

export default function InstanceDetail() {
  const { objectType, id } = useParams<{ objectType: string; id: string }>();
  const navigate = useNavigate();
  const [instance, setInstance] = useState<Instance | null>(null);
  const [objectTypeDef, setObjectTypeDef] = useState<ObjectType | null>(null);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { toasts, showToast, removeToast } = useToast();

  useEffect(() => {
    if (objectType && id) {
      loadData();
    }
  }, [objectType, id]);

  const loadData = async () => {
    if (!objectType || !id) return;
    try {
      setLoading(true);
      setError(null);
      
      // 获取对象类型定义
      const objectTypeData = await schemaApi.getObjectType(objectType);
      setObjectTypeDef(objectTypeData);

      // 优先使用 OntologyQuery 查询，如果失败则回退到直接 API 调用
      try {
        // 使用 ontology query 查询单个实例
        // 获取 ID 字段名（从 data_source.id_column 或使用第一个属性）
        const idField = objectTypeData.data_source?.id_column || 
                       (objectTypeData.properties && objectTypeData.properties.length > 0 
                         ? objectTypeData.properties[0].name 
                         : 'id');
        
        const selectFields: string[] = ['id']; // 查询结果中会有 id 字段
        if (objectTypeData.properties) {
          objectTypeData.properties.forEach(prop => {
            selectFields.push(prop.name);
          });
        }

        const instanceQuery: QueryRequest = {
          object: objectType,
          select: selectFields,
          filter: [['=', idField, id]], // 使用实际的 ID 字段名
          limit: 1,
        };

        const instanceResult = await queryApi.execute(instanceQuery);
        
        if (instanceResult.rows.length > 0) {
          const instanceData: Instance = {
            id: instanceResult.rows[0].id || id,
            ...instanceResult.rows[0],
          };
          setInstance(instanceData);
          return;
        }
      } catch (queryError) {
        console.warn('OntologyQuery failed, falling back to direct API:', queryError);
      }

      // 回退到直接 API 调用
      const instanceData = await instanceApi.get(objectType, id);
      setInstance(instanceData);

    } catch (error: any) {
      console.error('Failed to load data:', error);
      const errorMessage = error.response?.status === 404 
        ? `实例不存在或已被删除 (ID: ${id})`
        : error.response?.data?.message || error.message || '加载实例详情失败';
      setError(errorMessage);
      showToast(errorMessage, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!objectType || !id) return;
    if (!confirm('Are you sure you want to delete this instance?')) return;
    try {
      // TODO: 实现删除逻辑
      navigate(`/instances/${objectType}`);
    } catch (error) {
      console.error('Failed to delete instance:', error);
      alert('Failed to delete instance');
    }
  };

  if (loading) {
    return (
      <div>
        <div className="text-center py-12">Loading...</div>
        <ToastContainer toasts={toasts} onClose={removeToast} />
      </div>
    );
  }

  if (error || !instance || !objectTypeDef) {
    return (
      <div>
        <div className="text-center py-12">
          <div className="text-red-600 font-semibold mb-2">
            {error || 'Instance not found'}
          </div>
          <p className="text-gray-600 mb-4">
            {error 
              ? '无法加载实例详情，请检查实例ID是否正确或实例是否已被删除。'
              : '实例不存在或无法加载。'}
          </p>
          <RouterLink
            to={`/instances/${objectType || ''}`}
            className="inline-flex items-center text-blue-600 hover:text-blue-800"
          >
            <ArrowLeftIcon className="w-4 h-4 mr-2" />
            返回列表
          </RouterLink>
        </div>
        <ToastContainer toasts={toasts} onClose={removeToast} />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-6 py-8">
      {/* 头部 */}
      <div className="mb-6">
        <RouterLink
          to={`/instances/${objectType}`}
          className="inline-flex items-center text-blue-600 hover:text-blue-800 mb-4"
        >
          <ArrowLeftIcon className="w-4 h-4 mr-2" />
          Back to {objectTypeDef.name} List
        </RouterLink>
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{objectTypeDef.name}</h1>
            <p className="text-sm text-gray-500 font-mono mt-1">ID: {instance.id}</p>
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => navigate(`/graph/${objectType}/${id}`)}
              className="flex items-center px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700"
            >
              <CircleStackIcon className="w-4 h-4 mr-2" />
              查看图形
            </button>
            <button
              onClick={() => setShowForm(true)}
              className="flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              <PencilIcon className="w-4 h-4 mr-2" />
              编辑实例
            </button>
            <button
              onClick={handleDelete}
              className="flex items-center px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700"
            >
              <TrashIcon className="w-4 h-4 mr-2" />
              删除实例
            </button>
          </div>
        </div>
      </div>

      {showForm && objectTypeDef && (
        <InstanceForm
          objectType={objectTypeDef}
          instance={instance}
          onClose={() => {
            setShowForm(false);
            loadData();
          }}
        />
      )}

      {/* 属性面板 */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Properties</h2>
        <dl className="space-y-4">
          {objectTypeDef.properties.map((prop) => (
            <div key={prop.name}>
              <dt className="text-sm font-medium text-gray-500">{prop.name}</dt>
              <dd className="mt-1 text-sm text-gray-900">
                {instance[prop.name] !== null && instance[prop.name] !== undefined
                  ? typeof instance[prop.name] === 'object'
                    ? (
                        <pre className="bg-gray-50 p-2 rounded text-xs overflow-x-auto">
                          {JSON.stringify(instance[prop.name], null, 2)}
                        </pre>
                      )
                    : String(instance[prop.name])
                  : '-'}
              </dd>
            </div>
          ))}
        </dl>
      </div>

      {/* Toast 通知 */}
      <ToastContainer toasts={toasts} onClose={removeToast} />
    </div>
  );
}
