import { useEffect, useState } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import type { Instance, ObjectType } from '../api/client';
import { instanceApi, schemaApi, linkApi } from '../api/client';
import { ArrowLeftIcon, PencilIcon, TrashIcon, LinkIcon, CircleStackIcon } from '@heroicons/react/24/outline';
import { useNavigate } from 'react-router-dom';
import InstanceForm from '../components/InstanceForm';
import { ToastContainer, useToast } from '../components/Toast';

export default function InstanceDetail() {
  const { objectType, id } = useParams<{ objectType: string; id: string }>();
  const navigate = useNavigate();
  const [instance, setInstance] = useState<Instance | null>(null);
  const [objectTypeDef, setObjectTypeDef] = useState<ObjectType | null>(null);
  const [, setLinks] = useState<any[]>([]);
  const [connectedInstances, setConnectedInstances] = useState<Instance[]>([]);
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
      const [instanceData, objectTypeData] = await Promise.all([
        instanceApi.get(objectType, id),
        schemaApi.getObjectType(objectType),
      ]);
      setInstance(instanceData);
      setObjectTypeDef(objectTypeData);

      // Load links
      const outgoingLinks = await schemaApi.getOutgoingLinks(objectType);
      if (outgoingLinks.length > 0) {
        const linkType = outgoingLinks[0];
        try {
          const linksData = await linkApi.getInstanceLinks(objectType, id, linkType.name);
          setLinks(linksData);
          if (linksData.length > 0) {
            const connected = await linkApi.getConnectedInstances(
              objectType,
              id,
              linkType.name,
              'outgoing'
            );
            setConnectedInstances(connected);
          }
        } catch (error) {
          console.error('Failed to load links:', error);
        }
      }
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
      await instanceApi.delete(objectType, id);
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
    <div>
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

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
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
            <div>
              <dt className="text-sm font-medium text-gray-500">Created At</dt>
              <dd className="mt-1 text-sm text-gray-900">
                {instance.created_at ? new Date(instance.created_at).toLocaleString() : '-'}
              </dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-gray-500">Updated At</dt>
              <dd className="mt-1 text-sm text-gray-900">
                {instance.updated_at ? new Date(instance.updated_at).toLocaleString() : '-'}
              </dd>
            </div>
          </dl>
        </div>

        {connectedInstances.length > 0 && (
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4 flex items-center">
              <LinkIcon className="w-5 h-5 mr-2" />
              Connected Instances
            </h2>
            <div className="space-y-2">
              {connectedInstances.map((connected) => (
                <RouterLink
                  key={connected.id}
                  to={`/instances/${objectTypeDef.name}/${connected.id}`}
                  className="block p-3 border border-gray-200 rounded-lg hover:bg-gray-50"
                >
                  <div className="font-medium text-gray-900">
                    {connected.name || connected.id.substring(0, 8)}
                  </div>
                  <div className="text-xs text-gray-500 font-mono mt-1">{connected.id}</div>
                </RouterLink>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Toast 通知 */}
      <ToastContainer toasts={toasts} onClose={removeToast} />
    </div>
  );
}

