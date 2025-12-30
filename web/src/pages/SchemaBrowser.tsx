import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ObjectType, LinkType } from '../api/client';
import { schemaApi } from '../api/client';
import { useWorkspace } from '../WorkspaceContext';
import { 
  CubeIcon, 
  LinkIcon,
  InformationCircleIcon,
  ArrowPathIcon
} from '@heroicons/react/24/outline';

export default function SchemaBrowser() {
  const navigate = useNavigate();
  const { selectedWorkspace } = useWorkspace();
  const [objectTypes, setObjectTypes] = useState<ObjectType[]>([]);
  const [linkTypes, setLinkTypes] = useState<LinkType[]>([]);
  const [selectedObjectType, setSelectedObjectType] = useState<ObjectType | null>(null);
  const [selectedLinkType, setSelectedLinkType] = useState<LinkType | null>(null);
  const [loading, setLoading] = useState(true);

  // 根据工作空间过滤 Object Types 和 Link Types
  const workspaceFilteredObjectTypes = selectedWorkspace && selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0
    ? objectTypes.filter((ot) => selectedWorkspace.object_types!.includes(ot.name))
    : objectTypes;

  const workspaceFilteredLinkTypes = selectedWorkspace && selectedWorkspace.link_types && selectedWorkspace.link_types.length > 0
    ? linkTypes.filter((lt) => selectedWorkspace.link_types!.includes(lt.name))
    : linkTypes;

  // 计算过滤后的Link Types（当选择Object Type时）
  const filteredLinkTypes = selectedObjectType
    ? workspaceFilteredLinkTypes.filter(
        (lt) =>
          lt.source_type === selectedObjectType.name ||
          lt.target_type === selectedObjectType.name
      )
    : workspaceFilteredLinkTypes;

  // 计算过滤后的Object Types（当选择Link Type时）
  const filteredObjectTypes = selectedLinkType
    ? workspaceFilteredObjectTypes.filter(
        (ot) =>
          ot.name === selectedLinkType.source_type ||
          ot.name === selectedLinkType.target_type
      )
    : workspaceFilteredObjectTypes;

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
        <h2 className="text-lg font-semibold text-gray-900 mb-4 flex items-center justify-between">
          <div className="flex items-center">
            <CubeIcon className="w-5 h-5 mr-2" />
            Object Types ({filteredObjectTypes.length}{selectedLinkType ? ` / ${workspaceFilteredObjectTypes.length}` : ''})
          </div>
          {selectedLinkType && (
            <button
              onClick={() => setSelectedLinkType(null)}
              className="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded hover:bg-gray-200"
            >
              清除过滤
            </button>
          )}
        </h2>
        <div className="space-y-2">
          {filteredObjectTypes.length === 0 ? (
            <div className="text-center py-4 text-gray-500 text-sm">
              {selectedLinkType
                ? `没有与 "${selectedLinkType.name}" 相关的对象类型`
                : '暂无对象类型'}
            </div>
          ) : (
            filteredObjectTypes.map((ot) => (
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
                <div className="font-medium">{ot.display_name || ot.name}</div>
                {ot.description && (
                  <div className="text-sm text-gray-500 mt-1">{ot.description}</div>
                )}
              </button>
            ))
          )}
        </div>
      </div>

      {/* Object Type / Link Type Details */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">
          {selectedLinkType ? 'Link Type Details' : 'Object Type Details'}
        </h2>
        {selectedLinkType ? (
          <div>
            <div className="mb-4">
              <h3 className="text-xl font-bold text-gray-900">{selectedLinkType.display_name || selectedLinkType.name}</h3>
              {selectedLinkType.description && (
                <p className="text-gray-600 mt-1">{selectedLinkType.description}</p>
              )}
            </div>

            {/* 图形化展示属性映射关系 */}
            {selectedLinkType.property_mappings && Object.keys(selectedLinkType.property_mappings).length > 0 && (
              <div className="mb-6">
                <h4 className="font-semibold text-gray-900 mb-4">属性映射关系图</h4>
                <div className="relative bg-gradient-to-br from-blue-50 via-yellow-50 to-purple-50 rounded-lg p-6 border-2 border-gray-300">
                  {/* 源对象类型 */}
                  <div className="bg-blue-100 rounded-lg p-4 mb-4 border-2 border-blue-400 shadow-md">
                    <div className="flex items-center justify-between mb-2">
                      <div>
                        <div className="font-bold text-blue-900 text-lg">{selectedLinkType.source_type}</div>
                        <div className="text-xs text-blue-700 mt-1">源对象类型</div>
                      </div>
                      <CubeIcon className="w-6 h-6 text-blue-600" />
                    </div>
                    {(() => {
                      const sourceType = objectTypes.find(ot => ot.name === selectedLinkType.source_type);
                      return sourceType ? (
                        <div className="space-y-2 mt-3">
                          {Object.keys(selectedLinkType.property_mappings).map((sourceProp) => {
                            const prop = sourceType.properties.find(p => p.name === sourceProp);
                            return prop ? (
                              <div
                                key={sourceProp}
                                className="bg-white rounded-lg p-2.5 border-4 border-blue-500 shadow-sm hover:shadow-md transition-shadow"
                              >
                                <div className="flex items-center justify-between">
                                  <div className="flex items-center gap-2">
                                    <span className="font-semibold text-blue-900">
                                      {prop.name}
                                    </span>
                                    <span className="text-xs px-1.5 py-0.5 bg-yellow-400 text-yellow-900 rounded font-bold">
                                      映射
                                    </span>
                                  </div>
                                  <span className="text-xs px-2 py-1 bg-blue-300 text-blue-800 rounded font-medium">
                                    {prop.data_type}
                                  </span>
                                </div>
                                {prop.description && (
                                  <p className="text-xs text-gray-600 mt-1">{prop.description}</p>
                                )}
                              </div>
                            ) : null;
                          })}
                        </div>
                      ) : null;
                    })()}
                  </div>

                  {/* 映射关系连线和规则 */}
                  <div className="relative my-6">
                    {/* 连接线 */}
                    <div className="absolute inset-0 flex items-center">
                      <div className="flex-1 border-t-2 border-dashed border-gray-500"></div>
                      <div className="mx-4"></div>
                      <div className="flex-1 border-t-2 border-dashed border-gray-500"></div>
                    </div>
                    
                    {/* 中心内容 */}
                    <div className="relative flex flex-col items-center">
                      <div className="bg-white rounded-full p-3 border-4 border-purple-400 shadow-lg">
                        <LinkIcon className="w-10 h-10 text-purple-600" />
                      </div>
                      <div className="mt-3 bg-white rounded-lg px-4 py-2 border-2 border-purple-300 shadow-md">
                        <div className="text-sm font-bold text-purple-900 text-center">
                          {selectedLinkType.display_name || selectedLinkType.name}
                        </div>
                        <div className="text-xs text-gray-600 text-center mt-1">
                          {selectedLinkType.cardinality} • {selectedLinkType.direction}
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* 属性映射规则详情 */}
                  <div className="bg-yellow-100 rounded-lg p-4 mb-4 border-2 border-yellow-400 shadow-md">
                    <div className="text-sm font-bold text-yellow-900 mb-3 flex items-center">
                      <InformationCircleIcon className="w-5 h-5 mr-2" />
                      属性映射规则（所有规则必须同时满足）
                    </div>
                    <div className="space-y-2">
                      {Object.entries(selectedLinkType.property_mappings).map(([sourceProp, targetProp], idx) => (
                        <div 
                          key={idx} 
                          className="bg-white rounded-lg p-3 border-2 border-yellow-500 flex items-center justify-between shadow-sm"
                        >
                          <div className="flex items-center gap-3 flex-1">
                            <div className="bg-blue-200 rounded px-2 py-1">
                              <span className="text-xs font-semibold text-blue-900">{selectedLinkType.source_type}</span>
                              <span className="text-xs text-blue-700">.{sourceProp}</span>
                            </div>
                            <div className="text-yellow-700 font-bold text-lg">=</div>
                            <div className="bg-purple-200 rounded px-2 py-1">
                              <span className="text-xs font-semibold text-purple-900">{selectedLinkType.target_type}</span>
                              <span className="text-xs text-purple-700">.{targetProp}</span>
                            </div>
                          </div>
                          <div className="ml-4 text-xs text-gray-500 font-medium">
                            #{idx + 1}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* 目标对象类型 */}
                  <div className="bg-purple-100 rounded-lg p-4 border-2 border-purple-400 shadow-md">
                    <div className="flex items-center justify-between mb-2">
                      <div>
                        <div className="font-bold text-purple-900 text-lg">{selectedLinkType.target_type}</div>
                        <div className="text-xs text-purple-700 mt-1">目标对象类型</div>
                      </div>
                      <CubeIcon className="w-6 h-6 text-purple-600" />
                    </div>
                    {(() => {
                      const targetType = objectTypes.find(ot => ot.name === selectedLinkType.target_type);
                      return targetType ? (
                        <div className="space-y-2 mt-3">
                          {Object.values(selectedLinkType.property_mappings).map((targetProp) => {
                            const prop = targetType.properties.find(p => p.name === targetProp);
                            return prop ? (
                              <div
                                key={targetProp}
                                className="bg-white rounded-lg p-2.5 border-4 border-purple-500 shadow-sm hover:shadow-md transition-shadow"
                              >
                                <div className="flex items-center justify-between">
                                  <div className="flex items-center gap-2">
                                    <span className="font-semibold text-purple-900">
                                      {prop.name}
                                    </span>
                                    <span className="text-xs px-1.5 py-0.5 bg-yellow-400 text-yellow-900 rounded font-bold">
                                      映射
                                    </span>
                                  </div>
                                  <span className="text-xs px-2 py-1 bg-purple-300 text-purple-800 rounded font-medium">
                                    {prop.data_type}
                                  </span>
                                </div>
                                {prop.description && (
                                  <p className="text-xs text-gray-600 mt-1">{prop.description}</p>
                                )}
                              </div>
                            ) : null;
                          })}
                        </div>
                      ) : null;
                    })()}
                  </div>
                </div>
              </div>
            )}

            {/* 基本信息 */}
            <div className="mb-4">
              <h4 className="font-semibold text-gray-900 mb-2">基本信息</h4>
              <div className="space-y-2">
                <div className="p-3 bg-gray-50 rounded-lg border border-gray-200">
                  <div className="flex items-center justify-between">
                    <span className="text-gray-600">源对象类型:</span>
                    <span className="font-medium text-gray-900">{selectedLinkType.source_type}</span>
                  </div>
                </div>
                <div className="p-3 bg-gray-50 rounded-lg border border-gray-200">
                  <div className="flex items-center justify-between">
                    <span className="text-gray-600">目标对象类型:</span>
                    <span className="font-medium text-gray-900">{selectedLinkType.target_type}</span>
                  </div>
                </div>
                <div className="p-3 bg-gray-50 rounded-lg border border-gray-200">
                  <div className="flex items-center justify-between">
                    <span className="text-gray-600">基数:</span>
                    <span className="font-medium text-gray-900">{selectedLinkType.cardinality}</span>
                  </div>
                </div>
                <div className="p-3 bg-gray-50 rounded-lg border border-gray-200">
                  <div className="flex items-center justify-between">
                    <span className="text-gray-600">方向:</span>
                    <span className="font-medium text-gray-900">{selectedLinkType.direction}</span>
                  </div>
                </div>
              </div>
            </div>

            {/* 关系属性 */}
            {selectedLinkType.properties && selectedLinkType.properties.length > 0 && (
              <div className="mb-4">
                <h4 className="font-semibold text-gray-900 mb-2">关系属性</h4>
                <div className="space-y-2">
                  {selectedLinkType.properties.map((prop) => (
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
                    </div>
                  ))}
                </div>
              </div>
            )}

            {(!selectedLinkType.property_mappings || Object.keys(selectedLinkType.property_mappings).length === 0) && (
              <div className="text-center py-8 text-yellow-600 bg-yellow-50 rounded-lg border border-yellow-200">
                <InformationCircleIcon className="w-8 h-8 mx-auto mb-2" />
                <p className="text-sm">该关系类型未定义属性映射规则</p>
                <p className="text-xs text-yellow-500 mt-1">无法自动同步关系</p>
              </div>
            )}
          </div>
        ) : selectedObjectType ? (
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
            <p>Select an object type or link type to view details</p>
          </div>
        )}
      </div>

      {/* Link Types */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
        <h2 className="text-lg font-semibold text-gray-900 mb-4 flex items-center justify-between">
          <div className="flex items-center">
            <LinkIcon className="w-5 h-5 mr-2" />
            Link Types ({filteredLinkTypes.length}{selectedObjectType ? ` / ${workspaceFilteredLinkTypes.length}` : ''})
          </div>
          {selectedObjectType && (
            <button
              onClick={() => setSelectedObjectType(null)}
              className="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded hover:bg-gray-200"
            >
              清除过滤
            </button>
          )}
        </h2>
        <div className="space-y-2">
          {filteredLinkTypes.length === 0 ? (
            <div className="text-center py-4 text-gray-500 text-sm">
              {selectedObjectType
                ? `没有与 "${selectedObjectType.name}" 相关的关系类型`
                : '暂无关系类型'}
            </div>
          ) : (
            filteredLinkTypes.map((lt) => (
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
              <div className="font-medium">{lt.display_name || lt.name}</div>
              <div className="text-sm text-gray-500 mt-1">
                {lt.source_type} → {lt.target_type}
              </div>
              {lt.description && (
                <div className="text-xs text-gray-400 mt-1">{lt.description}</div>
              )}
            </button>
            ))
          )}
        </div>

        {selectedLinkType && (
          <div className="mt-4 p-3 bg-gray-50 rounded-lg border border-gray-200">
            <h4 className="font-semibold text-gray-900 mb-2">{selectedLinkType.display_name || selectedLinkType.name}</h4>
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

