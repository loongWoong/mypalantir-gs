import { useEffect, useState } from 'react';
import type { ObjectType, LinkType } from '../api/client';
import { schemaApi } from '../api/client';
import { 
  CubeIcon, 
  LinkIcon,
  InformationCircleIcon 
} from '@heroicons/react/24/outline';

export default function SchemaBrowser() {
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
              key={ot.Name}
              onClick={() => {
                setSelectedObjectType(ot);
                setSelectedLinkType(null);
              }}
              className={`w-full text-left px-3 py-2 rounded-lg transition-colors ${
                selectedObjectType?.Name === ot.Name
                  ? 'bg-blue-50 text-blue-700 border border-blue-200'
                  : 'hover:bg-gray-50 text-gray-700 border border-transparent'
              }`}
            >
              <div className="font-medium">{ot.Name}</div>
              {ot.Description && (
                <div className="text-sm text-gray-500 mt-1">{ot.Description}</div>
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
              <h3 className="text-xl font-bold text-gray-900">{selectedObjectType.Name}</h3>
              {selectedObjectType.Description && (
                <p className="text-gray-600 mt-1">{selectedObjectType.Description}</p>
              )}
            </div>

            <div className="mb-4">
              <h4 className="font-semibold text-gray-900 mb-2">Properties</h4>
              <div className="space-y-2">
                {selectedObjectType.Properties.map((prop) => (
                  <div
                    key={prop.Name}
                    className="p-3 bg-gray-50 rounded-lg border border-gray-200"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-gray-900">{prop.Name}</span>
                      <span className="text-xs px-2 py-1 bg-blue-100 text-blue-700 rounded">
                        {prop.DataType}
                      </span>
                    </div>
                    {prop.Description && (
                      <p className="text-sm text-gray-600 mt-1">{prop.Description}</p>
                    )}
                    <div className="flex items-center gap-2 mt-2">
                      {prop.Required && (
                        <span className="text-xs px-2 py-1 bg-red-100 text-red-700 rounded">
                          Required
                        </span>
                      )}
                      {prop.DefaultValue !== null && prop.DefaultValue !== undefined && (
                        <span className="text-xs text-gray-500">
                          Default: {JSON.stringify(prop.DefaultValue)}
                        </span>
                      )}
                    </div>
                    {prop.Constraints && Object.keys(prop.Constraints).length > 0 && (
                      <div className="mt-2 text-xs text-gray-500">
                        Constraints: {JSON.stringify(prop.Constraints)}
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
              key={lt.Name}
              onClick={() => {
                setSelectedLinkType(lt);
                setSelectedObjectType(null);
              }}
              className={`w-full text-left px-3 py-2 rounded-lg transition-colors ${
                selectedLinkType?.Name === lt.Name
                  ? 'bg-blue-50 text-blue-700 border border-blue-200'
                  : 'hover:bg-gray-50 text-gray-700 border border-transparent'
              }`}
            >
              <div className="font-medium">{lt.Name}</div>
              <div className="text-sm text-gray-500 mt-1">
                {lt.SourceType} → {lt.TargetType}
              </div>
              {lt.Description && (
                <div className="text-xs text-gray-400 mt-1">{lt.Description}</div>
              )}
            </button>
          ))}
        </div>

        {selectedLinkType && (
          <div className="mt-4 p-3 bg-gray-50 rounded-lg border border-gray-200">
            <h4 className="font-semibold text-gray-900 mb-2">{selectedLinkType.Name}</h4>
            <div className="text-sm space-y-1">
              <div>
                <span className="text-gray-600">Source:</span>{' '}
                <span className="font-medium">{selectedLinkType.SourceType}</span>
              </div>
              <div>
                <span className="text-gray-600">Target:</span>{' '}
                <span className="font-medium">{selectedLinkType.TargetType}</span>
              </div>
              <div>
                <span className="text-gray-600">Cardinality:</span>{' '}
                <span className="font-medium">{selectedLinkType.Cardinality}</span>
              </div>
              <div>
                <span className="text-gray-600">Direction:</span>{' '}
                <span className="font-medium">{selectedLinkType.Direction}</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

