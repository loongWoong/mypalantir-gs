import { useEffect, useState } from 'react';
import { useParams, Link as RouterLink, useNavigate } from 'react-router-dom';
import type { Instance, ObjectType, QueryRequest, Rule } from '../api/client';
import { schemaApi, queryApi, rulesApi, instanceApi } from '../api/client';
import { ArrowLeftIcon, PencilIcon, TrashIcon, ShieldCheckIcon } from '@heroicons/react/24/outline';
import InstanceForm from '../components/InstanceForm';

export default function InstanceDetail() {
  const { objectType, id } = useParams<{ objectType: string; id: string }>();
  const navigate = useNavigate();
  const [instance, setInstance] = useState<Instance | null>(null);
  const [objectTypeDef, setObjectTypeDef] = useState<ObjectType | null>(null);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [rules, setRules] = useState<Rule[]>([]);

  useEffect(() => {
    if (objectType && id) {
      loadData();
    }
  }, [objectType, id]);

  const loadData = async () => {
    if (!objectType || !id) return;
    try {
      setLoading(true);
      
      // 获取对象类型定义
      const objectTypeData = await schemaApi.getObjectType(objectType);
      setObjectTypeDef(objectTypeData);

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
      
      if (instanceResult.rows.length === 0) {
        setLoading(false);
        return;
      }

      const instanceData: Instance = {
        id: instanceResult.rows[0].id || id,
        ...instanceResult.rows[0],
      };
      setInstance(instanceData);

      // 加载关联规则
      const rulesData = await rulesApi.getRulesForObjectType(objectType);
      setRules(rulesData);

    } catch (error) {
      console.error('Failed to load data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!objectType || !id) return;
    if (!confirm('确定要删除此实例吗？')) return;
    try {
      await instanceApi.delete(objectType, id);
      navigate(`/instances/${objectType}`);
    } catch (error) {
      console.error('Failed to delete instance:', error);
      alert('删除失败');
    }
  };

  if (loading) {
    return <div className="text-center py-12">Loading...</div>;
  }

  if (!instance || !objectTypeDef) {
    return <div className="text-center py-12">Instance not found</div>;
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

      {/* 物理属性 */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Properties</h2>
        <dl className="space-y-4">
          {objectTypeDef.properties.filter(p => !p.derived).map((prop) => (
            <div key={prop.name}>
              <dt className="text-sm font-medium text-gray-500">
                {prop.display_name || prop.name}
              </dt>
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

      {/* 衍生属性 */}
      {objectTypeDef.properties.some(p => p.derived) && (
        <div className="bg-white rounded-lg shadow-sm border border-purple-200 p-6 mb-6">
          <h2 className="text-lg font-semibold text-purple-900 mb-4 flex items-center">
            <span className="text-xs px-2 py-1 bg-purple-100 text-purple-700 rounded-full mr-2">Derived</span>
            Derived Properties
          </h2>
          <dl className="space-y-4">
            {objectTypeDef.properties.filter(p => p.derived).map((prop) => (
              <div key={prop.name}>
                <dt className="text-sm font-medium text-gray-500 flex items-center justify-between">
                  <span>{prop.display_name || prop.name}</span>
                  <span className="text-xs px-2 py-0.5 bg-purple-100 text-purple-700 rounded-full">
                    {prop.data_type}
                  </span>
                </dt>
                <dd className="mt-1 text-sm text-gray-900">
                  {instance[prop.name] !== null && instance[prop.name] !== undefined
                    ? String(instance[prop.name])
                    : <span className="text-gray-400 italic">Not computed</span>}
                </dd>
                {prop.expr && (
                  <div className="mt-1 p-2 bg-purple-50 rounded text-xs text-purple-800">
                    <code>{prop.expr}</code>
                  </div>
                )}
              </div>
            ))}
          </dl>
        </div>
      )}

      {/* 规则推导 */}
      {rules.length > 0 && (
        <div className="bg-white rounded-lg shadow-sm border border-indigo-200 p-6">
          <h2 className="text-lg font-semibold text-indigo-900 mb-4 flex items-center">
            <ShieldCheckIcon className="w-5 h-5 mr-2" />
            Rule Inference
          </h2>
          <div className="space-y-3">
            {rules.map((rule) => (
              <div key={rule.name} className="p-3 bg-indigo-50 rounded-lg border border-indigo-200">
                <div className="flex items-center justify-between">
                  <span className="font-medium text-gray-900">{rule.display_name || rule.name}</span>
                  <span className="text-xs px-2 py-1 bg-indigo-100 text-indigo-700 rounded-full">
                    {rule.language}
                  </span>
                </div>
                <p className="text-sm text-gray-600 mt-1">{rule.description}</p>
                <div className="mt-2 flex items-center text-sm">
                  <span className="text-gray-500">Result:</span>
                  <span className="ml-2 text-gray-400 italic">Pending</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
