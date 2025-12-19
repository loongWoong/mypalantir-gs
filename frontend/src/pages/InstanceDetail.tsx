import { useEffect, useState } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import type { Instance, ObjectType } from '../api/client';
import { instanceApi, schemaApi, linkApi } from '../api/client';
import { ArrowLeftIcon, PencilIcon, TrashIcon, LinkIcon, CircleStackIcon } from '@heroicons/react/24/outline';
import { useNavigate } from 'react-router-dom';
import InstanceForm from '../components/InstanceForm';

export default function InstanceDetail() {
  const { objectType, id } = useParams<{ objectType: string; id: string }>();
  const navigate = useNavigate();
  const [instance, setInstance] = useState<Instance | null>(null);
  const [objectTypeDef, setObjectTypeDef] = useState<ObjectType | null>(null);
  const [, setLinks] = useState<any[]>([]);
  const [connectedInstances, setConnectedInstances] = useState<Instance[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    if (objectType && id) {
      loadData();
    }
  }, [objectType, id]);

  const loadData = async () => {
    if (!objectType || !id) return;
    try {
      setLoading(true);
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
          const linksData = await linkApi.getInstanceLinks(objectType, id, linkType.Name);
          setLinks(linksData);
          if (linksData.length > 0) {
            const connected = await linkApi.getConnectedInstances(
              objectType,
              id,
              linkType.Name,
              'outgoing'
            );
            setConnectedInstances(connected);
          }
        } catch (error) {
          console.error('Failed to load links:', error);
        }
      }
    } catch (error) {
      console.error('Failed to load data:', error);
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
    return <div className="text-center py-12">Loading...</div>;
  }

  if (!instance || !objectTypeDef) {
    return <div className="text-center py-12">Instance not found</div>;
  }

  return (
    <div>
      <div className="mb-6">
        <RouterLink
          to={`/instances/${objectType}`}
          className="inline-flex items-center text-blue-600 hover:text-blue-800 mb-4"
        >
          <ArrowLeftIcon className="w-4 h-4 mr-2" />
          Back to {objectTypeDef.Name} List
        </RouterLink>
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{objectTypeDef.Name}</h1>
            <p className="text-sm text-gray-500 font-mono mt-1">ID: {instance.id}</p>
          </div>
          <div className="flex gap-2">
            <RouterLink
              to={`/graph/${objectType}/${id}`}
              className="flex items-center px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700"
            >
              <CircleStackIcon className="w-4 h-4 mr-2" />
              View Graph
            </RouterLink>
            <button
              onClick={() => setShowForm(true)}
              className="flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              <PencilIcon className="w-4 h-4 mr-2" />
              Edit
            </button>
            <button
              onClick={handleDelete}
              className="flex items-center px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700"
            >
              <TrashIcon className="w-4 h-4 mr-2" />
              Delete
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
            {objectTypeDef.Properties.map((prop) => (
              <div key={prop.Name}>
                <dt className="text-sm font-medium text-gray-500">{prop.Name}</dt>
                <dd className="mt-1 text-sm text-gray-900">
                  {instance[prop.Name] !== null && instance[prop.Name] !== undefined
                    ? typeof instance[prop.Name] === 'object'
                      ? (
                          <pre className="bg-gray-50 p-2 rounded text-xs overflow-x-auto">
                            {JSON.stringify(instance[prop.Name], null, 2)}
                          </pre>
                        )
                      : String(instance[prop.Name])
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
                  to={`/instances/${objectTypeDef.Name}/${connected.id}`}
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
    </div>
  );
}

