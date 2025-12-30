import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ObjectType, LinkType } from '../api/client';
import { schemaApi } from '../api/client';
import { 
  CubeIcon, 
  LinkIcon,
  InformationCircleIcon,
  ArrowPathIcon
} from '@heroicons/react/24/outline';

export default function SchemaBrowser() {
  const navigate = useNavigate();
  const [objectTypes, setObjectTypes] = useState<ObjectType[]>([]);
  const [linkTypes, setLinkTypes] = useState<LinkType[]>([]);
  const [selectedObjectType, setSelectedObjectType] = useState<ObjectType | null>(null);
  const [selectedLinkType, setSelectedLinkType] = useState<LinkType | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadData = async () => {
      try {
        const [objectTypesData, linkTypesData] = await Promise.all([
          schemaApi.getObjectTypes(),
          schemaApi.getLinkTypes(),
        ]);
        setObjectTypes(objectTypesData);
        setLinkTypes(linkTypesData);
        if (objectTypesData.length > 0) {
          setSelectedObjectType(objectTypesData[0]);
        }
      } catch (error) {
        console.error('Failed to load schema:', error);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, []);

  if (loading) {
    return <div className="text-center py-12">Loading...</div>;
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Object Types List */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
        <h2 className="text-lg font-semibold text-gray-900 mb-4 flex items-center">
          <CubeIcon className="w-5 h-5 mr-2" />
          Object Types ({objectTypes.length})
        </h2>
        <div className="space-y-2">
          {objectTypes.map((ot) => (
            <button
              key={ot.name}
              onClick={() => {
                setSelectedObjectType(ot);
                setSelectedLinkType(null);
              }}
              className={`w-full text-left px-3 py-2 rounded-lg transition-colors ${
                selectedObjectType?.name === ot.name
                  ? 'bg-blue-50 text-blue-700 border border-blue-200'
                  : 'hover:bg-gray-50 text-gray-700 border border-transparent'
              }`}
            >
              <div className="font-medium">{ot.name}</div>
              {ot.description && (
                <div className="text-sm text-gray-500 mt-1">{ot.description}</div>
              )}
            </button>
          ))}
        </div>
      </div>

      {/* Object Type Details */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Object Type Details</h2>
        {selectedObjectType ? (
          <div>
            <div className="mb-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-xl font-bold text-gray-900">{selectedObjectType.name}</h3>
                  {selectedObjectType.description && (
                    <p className="text-gray-600 mt-1">{selectedObjectType.description}</p>
                  )}
                </div>
                <button
                  onClick={() => navigate(`/data-mapping/${selectedObjectType.name}`)}
                  className="flex items-center px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
                  title="数据映射"
                >
                  <ArrowPathIcon className="w-5 h-5 mr-2" />
                  数据映射
                </button>
              </div>
            </div>

            <div className="mb-4">
              <h4 className="font-semibold text-gray-900 mb-2">Properties</h4>
              <div className="space-y-2">
                {selectedObjectType.properties.map((prop) => (
                  <div
                    key={prop.name}
                    className="p-3 bg-gray-50 rounded-lg border border-gray-200"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-gray-900">{prop.name}</span>
                      <span className="text-xs px-2 py-1 bg-blue-100 text-blue-700 rounded">
                        {prop.data_type}
                      </span>
                    </div>
                    {prop.description && (
                      <p className="text-sm text-gray-600 mt-1">{prop.description}</p>
                    )}
                    <div className="flex items-center gap-2 mt-2">
                      {prop.required && (
                        <span className="text-xs px-2 py-1 bg-red-100 text-red-700 rounded">
                          Required
                        </span>
                      )}
                      {prop.default_value !== null && prop.default_value !== undefined && (
                        <span className="text-xs text-gray-500">
                          Default: {JSON.stringify(prop.default_value)}
                        </span>
                      )}
                    </div>
                    {prop.constraints && Object.keys(prop.constraints).length > 0 && (
                      <div className="mt-2 text-xs text-gray-500">
                        Constraints: {JSON.stringify(prop.constraints)}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        ) : (
          <div className="text-center py-8 text-gray-500">
            <InformationCircleIcon className="w-12 h-12 mx-auto mb-2 text-gray-400" />
            <p>Select an object type to view details</p>
          </div>
        )}
      </div>

      {/* Link Types */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
        <h2 className="text-lg font-semibold text-gray-900 mb-4 flex items-center">
          <LinkIcon className="w-5 h-5 mr-2" />
          Link Types ({linkTypes.length})
        </h2>
        <div className="space-y-2">
          {linkTypes.map((lt) => (
            <button
              key={lt.name}
              onClick={() => {
                setSelectedLinkType(lt);
                setSelectedObjectType(null);
              }}
              className={`w-full text-left px-3 py-2 rounded-lg transition-colors ${
                selectedLinkType?.name === lt.name
                  ? 'bg-blue-50 text-blue-700 border border-blue-200'
                  : 'hover:bg-gray-50 text-gray-700 border border-transparent'
              }`}
            >
              <div className="font-medium">{lt.name}</div>
              <div className="text-sm text-gray-500 mt-1">
                {lt.source_type} → {lt.target_type}
              </div>
              {lt.description && (
                <div className="text-xs text-gray-400 mt-1">{lt.description}</div>
              )}
            </button>
          ))}
        </div>

        {selectedLinkType && (
          <div className="mt-4 p-3 bg-gray-50 rounded-lg border border-gray-200">
            <h4 className="font-semibold text-gray-900 mb-2">{selectedLinkType.name}</h4>
            <div className="text-sm space-y-1">
              <div>
                <span className="text-gray-600">Source:</span>{' '}
                <span className="font-medium">{selectedLinkType.source_type}</span>
              </div>
              <div>
                <span className="text-gray-600">Target:</span>{' '}
                <span className="font-medium">{selectedLinkType.target_type}</span>
              </div>
              <div>
                <span className="text-gray-600">Cardinality:</span>{' '}
                <span className="font-medium">{selectedLinkType.cardinality}</span>
              </div>
              <div>
                <span className="text-gray-600">Direction:</span>{' '}
                <span className="font-medium">{selectedLinkType.direction}</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

