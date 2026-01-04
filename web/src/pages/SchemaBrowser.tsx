import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ObjectType, LinkType, DataSourceConfig } from '../api/client';
import { schemaApi } from '../api/client';
import { useWorkspace } from '../WorkspaceContext';
import { 
  CubeIcon, 
  LinkIcon,
  InformationCircleIcon,
  ServerIcon,
  CheckCircleIcon,
  XCircleIcon,
  ArrowPathIcon
} from '@heroicons/react/24/outline';

export default function SchemaBrowser() {
  const navigate = useNavigate();
  const { selectedWorkspace } = useWorkspace();
  const [objectTypes, setObjectTypes] = useState<ObjectType[]>([]);
  const [linkTypes, setLinkTypes] = useState<LinkType[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceConfig[]>([]);
  const [selectedObjectType, setSelectedObjectType] = useState<ObjectType | null>(null);
  const [selectedLinkType, setSelectedLinkType] = useState<LinkType | null>(null);
  const [activeTab, setActiveTab] = useState<'properties' | 'datasource'>('properties');
  const [linkTypeActiveTab, setLinkTypeActiveTab] = useState<'properties' | 'datasource'>('properties');
  const [loading, setLoading] = useState(true);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string; metadata?: Record<string, string> } | null>(null);

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
        const [objectTypesData, linkTypesData, dataSourcesData] = await Promise.all([
          schemaApi.getObjectTypes(),
          schemaApi.getLinkTypes(),
          schemaApi.getDataSources(),
        ]);
        setObjectTypes(objectTypesData);
        setLinkTypes(linkTypesData);
        setDataSources(dataSourcesData);
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

  // 当选择的对象类型改变时，重置 Tab 和测试结果
  useEffect(() => {
    setActiveTab('properties');
    setTestResult(null);
  }, [selectedObjectType?.name]);

  // 当选择的 Link Type 改变时，重置 Tab 和测试结果
  useEffect(() => {
    setLinkTypeActiveTab('properties');
    setTestResult(null);
  }, [selectedLinkType?.name]);

  // 获取数据源配置详情
  const getDataSourceConfig = (connectionId: string): DataSourceConfig | undefined => {
    return dataSources.find(ds => ds.id === connectionId);
  };

  // 测试数据源连接
  const handleTestConnection = async (connectionId: string) => {
    try {
      setTesting(true);
      setTestResult(null);
      const result = await schemaApi.testConnection(connectionId);
      setTestResult(result);
    } catch (error: any) {
      setTestResult({
        success: false,
        message: error.response?.data?.message || error.message || 'Test connection failed'
      });
    } finally {
      setTesting(false);
    }
  };

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
                <div className="flex items-center justify-between">
              <div className="font-medium">{ot.display_name || ot.name}</div>
                  {ot.data_source && (
                  <ServerIcon 
                    className="w-4 h-4 text-green-600" 
                    title="Has data source configured"
                  />
                )}
              </div>
              {ot.description && (
                  <div className="text-sm text-gray-500 mt-1">{ot.description}</div>
                )}
              </button>
            ))
          )}
        </div>
      </div>

      {/* Object Type / Link Type / Link Type Details */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">
          {selectedLinkType ? 'Link Type Details' : selectedObjectType ? 'Object Type Details' : 'Details'}
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
              <h3 className="text-xl font-bold text-gray-900">{selectedObjectType.name}</h3>
              {selectedObjectType.description && (
                <p className="text-gray-600 mt-1">{selectedObjectType.description}</p>
              )}
            </div>

            {/* Tab 切换 */}
            <div className="border-b border-gray-200 mb-4">
              <nav className="flex space-x-4">
                <button
                  onClick={() => setActiveTab('properties')}
                  className={`py-2 px-1 border-b-2 font-medium text-sm ${
                    activeTab === 'properties'
                      ? 'border-blue-500 text-blue-600'
                      : 'border-transparent text-gray-500 hover:text-gray-700'
                  }`}
                >
                  Properties
                </button>
                <button
                  onClick={() => setActiveTab('datasource')}
                  className={`py-2 px-1 border-b-2 font-medium text-sm flex items-center ${
                    activeTab === 'datasource'
                      ? 'border-blue-500 text-blue-600'
                      : 'border-transparent text-gray-500 hover:text-gray-700'
                  }`}
                >
                  <ServerIcon className="w-4 h-4 mr-1" />
                  Data Source
                </button>
              </nav>
            </div>

            {/* Tab 内容 */}
            {activeTab === 'properties' ? (
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
            ) : (
              <div className="mb-4">
                {selectedObjectType.data_source ? (
                  <div className="space-y-4">
                    {/* 数据源配置信息 */}
                    <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
                      <div className="flex items-center justify-between mb-3">
                        <h4 className="font-semibold text-gray-900 flex items-center">
                          <ServerIcon className="w-5 h-5 mr-2 text-blue-600" />
                          Data Source Configuration
                        </h4>
                        {(() => {
                          const dsConfig = getDataSourceConfig(selectedObjectType.data_source!.connection_id);
                          return dsConfig ? (
                            <button
                              onClick={() => handleTestConnection(selectedObjectType.data_source!.connection_id)}
                              disabled={testing}
                              className={`flex items-center px-3 py-1 text-sm rounded-lg font-medium transition-colors ${
                                testing
                                  ? 'bg-gray-300 text-gray-500 cursor-not-allowed'
                                  : 'bg-green-600 text-white hover:bg-green-700'
                              }`}
                            >
                              {testing ? (
                                <>
                                  <ArrowPathIcon className="w-4 h-4 mr-1 animate-spin" />
                                  Testing...
                                </>
                              ) : (
                                <>
                                  <CheckCircleIcon className="w-4 h-4 mr-1" />
                                  Test Connection
                                </>
                              )}
                            </button>
                          ) : null;
                        })()}
                      </div>
                      
                      <div className="space-y-2 text-sm">
                        <div>
                          <span className="text-gray-600">Connection ID:</span>{' '}
                          <span className="font-medium">{selectedObjectType.data_source.connection_id}</span>
                        </div>
                        <div>
                          <span className="text-gray-600">Table:</span>{' '}
                          <span className="font-medium">{selectedObjectType.data_source.table}</span>
                        </div>
                        <div>
                          <span className="text-gray-600">ID Column:</span>{' '}
                          <span className="font-medium">{selectedObjectType.data_source.id_column}</span>
                        </div>
                      </div>

                      {/* 数据源详细信息 */}
                      {(() => {
                        const dsConfig = getDataSourceConfig(selectedObjectType.data_source.connection_id);
                        if (dsConfig) {
                          return (
                            <div className="mt-4 pt-4 border-t border-blue-200">
                              <div className="text-xs font-semibold text-gray-700 mb-2">Connection Details:</div>
                              <div className="space-y-1 text-xs">
                                <div>
                                  <span className="text-gray-600">Type:</span>{' '}
                                  <span className="font-medium">{dsConfig.type}</span>
                                </div>
                                <div>
                                  <span className="text-gray-600">Host:</span>{' '}
                                  <span className="font-medium">{dsConfig.host}</span>
                                </div>
                                {dsConfig.port > 0 && (
                                  <div>
                                    <span className="text-gray-600">Port:</span>{' '}
                                    <span className="font-medium">{dsConfig.port}</span>
                                  </div>
                                )}
                                <div>
                                  <span className="text-gray-600">Database:</span>{' '}
                                  <span className="font-medium">{dsConfig.database}</span>
                                </div>
                              </div>
                            </div>
                          );
                        }
                        return null;
                      })()}
                    </div>

                    {/* 字段映射 */}
                    <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                      <h4 className="font-semibold text-gray-900 mb-3">Field Mapping</h4>
                      <div className="space-y-2">
                        {Object.entries(selectedObjectType.data_source.field_mapping || {}).map(([propertyName, columnName]) => (
                          <div key={propertyName} className="flex items-center justify-between p-2 bg-white rounded border border-gray-200">
                            <span className="text-sm font-medium text-gray-900">{propertyName}</span>
                            <span className="text-sm text-gray-500">→</span>
                            <code className="text-xs px-2 py-1 bg-gray-100 text-gray-700 rounded">{columnName}</code>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* 测试结果 */}
                    {testResult && (
                      <div className={`p-4 rounded-lg border ${
                        testResult.success
                          ? 'bg-green-50 border-green-200'
                          : 'bg-red-50 border-red-200'
                      }`}>
                        <div className="flex items-start">
                          {testResult.success ? (
                            <CheckCircleIcon className="w-5 h-5 text-green-600 mr-2 mt-0.5" />
                          ) : (
                            <XCircleIcon className="w-5 h-5 text-red-600 mr-2 mt-0.5" />
                          )}
                          <div className="flex-1">
                            <div className={`font-medium ${
                              testResult.success ? 'text-green-800' : 'text-red-800'
                            }`}>
                              {testResult.success ? 'Connection Successful' : 'Connection Failed'}
                            </div>
                            <div className={`text-sm mt-1 ${
                              testResult.success ? 'text-green-700' : 'text-red-700'
                            }`}>
                              {testResult.message}
                            </div>
                            {testResult.success && testResult.metadata && Object.keys(testResult.metadata).length > 0 && (
                              <div className="mt-3 pt-3 border-t border-green-200">
                                <div className="text-xs font-semibold text-green-800 mb-2">Database Information:</div>
                                <div className="space-y-1">
                                  {Object.entries(testResult.metadata).map(([key, value]) => (
                                    <div key={key} className="text-xs text-green-700">
                                      <span className="font-medium">{key}:</span> {value}
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                    <div className="flex items-center justify-between">
                      <div>
                        <h4 className="font-semibold text-gray-900 mb-1 flex items-center">
                          <InformationCircleIcon className="w-5 h-5 mr-2 text-gray-400" />
                          No Data Source Configured
                        </h4>
                        <p className="text-sm text-gray-500">This object type uses file system storage</p>
                      </div>
                    </div>
                    <div className="mt-3 text-xs text-gray-500">
                      <p>To configure a data source, edit the schema.yaml file and add a data_source mapping for this object type.</p>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        ) : selectedLinkType ? (
          <div>
            <div className="mb-4">
              <h3 className="text-xl font-bold text-gray-900">{selectedLinkType.name}</h3>
              {selectedLinkType.description && (
                <p className="text-gray-600 mt-1">{selectedLinkType.description}</p>
              )}
            </div>

            {/* Tab 切换 */}
            <div className="border-b border-gray-200 mb-4">
              <nav className="flex space-x-4">
                <button
                  onClick={() => setLinkTypeActiveTab('properties')}
                  className={`py-2 px-1 border-b-2 font-medium text-sm ${
                    linkTypeActiveTab === 'properties'
                      ? 'border-blue-500 text-blue-600'
                      : 'border-transparent text-gray-500 hover:text-gray-700'
                  }`}
                >
                  Properties
                </button>
                <button
                  onClick={() => setLinkTypeActiveTab('datasource')}
                  className={`py-2 px-1 border-b-2 font-medium text-sm flex items-center ${
                    linkTypeActiveTab === 'datasource'
                      ? 'border-blue-500 text-blue-600'
                      : 'border-transparent text-gray-500 hover:text-gray-700'
                  }`}
                >
                  <ServerIcon className="w-4 h-4 mr-1" />
                  Data Source
                </button>
              </nav>
            </div>

            {/* Tab 内容 */}
            {linkTypeActiveTab === 'properties' ? (
              <div className="mb-4">
                <div className="text-sm space-y-2 mb-4">
                  <div className="p-2 bg-gray-50 rounded border border-gray-200">
                    <span className="text-gray-600">Source:</span>{' '}
                    <span className="font-medium">{selectedLinkType.source_type}</span>
                  </div>
                  <div className="p-2 bg-gray-50 rounded border border-gray-200">
                    <span className="text-gray-600">Target:</span>{' '}
                    <span className="font-medium">{selectedLinkType.target_type}</span>
                  </div>
                  <div className="p-2 bg-gray-50 rounded border border-gray-200">
                    <span className="text-gray-600">Cardinality:</span>{' '}
                    <span className="font-medium">{selectedLinkType.cardinality}</span>
                  </div>
                  <div className="p-2 bg-gray-50 rounded border border-gray-200">
                    <span className="text-gray-600">Direction:</span>{' '}
                    <span className="font-medium">{selectedLinkType.direction}</span>
                  </div>
                </div>

                <h4 className="font-semibold text-gray-900 mb-2">Link Properties</h4>
                {selectedLinkType.properties && selectedLinkType.properties.length > 0 ? (
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
                        <div className="flex items-center gap-2 mt-2">
                          {prop.required && (
                            <span className="text-xs px-2 py-1 bg-red-100 text-red-700 rounded">
                              Required
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-sm text-gray-500">No properties defined</div>
                )}
              </div>
            ) : (
              <div className="mb-4">
                {selectedLinkType.data_source ? (
                  <div className="space-y-4">
                    {/* 数据源配置信息 */}
                    <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
                      <div className="flex items-center justify-between mb-3">
                        <h4 className="font-semibold text-gray-900 flex items-center">
                          <ServerIcon className="w-5 h-5 mr-2 text-blue-600" />
                          Data Source Configuration
                        </h4>
                        {(() => {
                          const dsConfig = getDataSourceConfig(selectedLinkType.data_source!.connection_id);
                          return dsConfig ? (
                            <button
                              onClick={() => handleTestConnection(selectedLinkType.data_source!.connection_id)}
                              disabled={testing}
                              className={`flex items-center px-3 py-1 text-sm rounded-lg font-medium transition-colors ${
                                testing
                                  ? 'bg-gray-300 text-gray-500 cursor-not-allowed'
                                  : 'bg-green-600 text-white hover:bg-green-700'
                              }`}
                            >
                              {testing ? (
                                <>
                                  <ArrowPathIcon className="w-4 h-4 mr-1 animate-spin" />
                                  Testing...
                                </>
                              ) : (
                                <>
                                  <CheckCircleIcon className="w-4 h-4 mr-1" />
                                  Test Connection
                                </>
                              )}
                            </button>
                          ) : null;
                        })()}
                      </div>
                      
                      <div className="space-y-2 text-sm">
                        <div>
                          <span className="text-gray-600">Connection ID:</span>{' '}
                          <span className="font-medium">{selectedLinkType.data_source.connection_id}</span>
                        </div>
                        <div>
                          <span className="text-gray-600">Table:</span>{' '}
                          <span className="font-medium">{selectedLinkType.data_source.table}</span>
                        </div>
                        <div>
                          <span className="text-gray-600">ID Column:</span>{' '}
                          <span className="font-medium">{selectedLinkType.data_source.id_column}</span>
                        </div>
                        {selectedLinkType.data_source.source_id_column && (
                          <div>
                            <span className="text-gray-600">Source ID Column:</span>{' '}
                            <span className="font-medium">{selectedLinkType.data_source.source_id_column}</span>
                            <span className="text-xs text-gray-500 ml-2">({selectedLinkType.source_type})</span>
                          </div>
                        )}
                        {selectedLinkType.data_source.target_id_column && (
                          <div>
                            <span className="text-gray-600">Target ID Column:</span>{' '}
                            <span className="font-medium">{selectedLinkType.data_source.target_id_column}</span>
                            <span className="text-xs text-gray-500 ml-2">({selectedLinkType.target_type})</span>
                          </div>
                        )}
                      </div>

                      {/* 数据源详细信息 */}
                      {(() => {
                        const dsConfig = getDataSourceConfig(selectedLinkType.data_source.connection_id);
                        if (dsConfig) {
                          return (
                            <div className="mt-4 pt-4 border-t border-blue-200">
                              <div className="text-xs font-semibold text-gray-700 mb-2">Connection Details:</div>
                              <div className="space-y-1 text-xs">
                                <div>
                                  <span className="text-gray-600">Type:</span>{' '}
                                  <span className="font-medium">{dsConfig.type}</span>
                                </div>
                                <div>
                                  <span className="text-gray-600">Host:</span>{' '}
                                  <span className="font-medium">{dsConfig.host}</span>
                                </div>
                                {dsConfig.port > 0 && (
                                  <div>
                                    <span className="text-gray-600">Port:</span>{' '}
                                    <span className="font-medium">{dsConfig.port}</span>
                                  </div>
                                )}
                                <div>
                                  <span className="text-gray-600">Database:</span>{' '}
                                  <span className="font-medium">{dsConfig.database}</span>
                                </div>
                              </div>
                            </div>
                          );
                        }
                        return null;
                      })()}
                    </div>

                    {/* 字段映射 */}
                    <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                      <h4 className="font-semibold text-gray-900 mb-3">Field Mapping</h4>
                      <div className="space-y-2">
                        {Object.entries(selectedLinkType.data_source.field_mapping || {}).map(([propertyName, columnName]) => (
                          <div key={propertyName} className="flex items-center justify-between p-2 bg-white rounded border border-gray-200">
                            <span className="text-sm font-medium text-gray-900">{propertyName}</span>
                            <span className="text-sm text-gray-500">→</span>
                            <code className="text-xs px-2 py-1 bg-gray-100 text-gray-700 rounded">{columnName}</code>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* 测试结果 */}
                    {testResult && (
                      <div className={`p-4 rounded-lg border ${
                        testResult.success
                          ? 'bg-green-50 border-green-200'
                          : 'bg-red-50 border-red-200'
                      }`}>
                        <div className="flex items-start">
                          {testResult.success ? (
                            <CheckCircleIcon className="w-5 h-5 text-green-600 mr-2 mt-0.5" />
                          ) : (
                            <XCircleIcon className="w-5 h-5 text-red-600 mr-2 mt-0.5" />
                          )}
                          <div className="flex-1">
                            <div className={`font-medium ${
                              testResult.success ? 'text-green-800' : 'text-red-800'
                            }`}>
                              {testResult.success ? 'Connection Successful' : 'Connection Failed'}
                            </div>
                            <div className={`text-sm mt-1 ${
                              testResult.success ? 'text-green-700' : 'text-red-700'
                            }`}>
                              {testResult.message}
                            </div>
                            {testResult.success && testResult.metadata && Object.keys(testResult.metadata).length > 0 && (
                              <div className="mt-3 pt-3 border-t border-green-200">
                                <div className="text-xs font-semibold text-green-800 mb-2">Database Information:</div>
                                <div className="space-y-1">
                                  {Object.entries(testResult.metadata).map(([key, value]) => (
                                    <div key={key} className="text-xs text-green-700">
                                      <span className="font-medium">{key}:</span> {value}
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                    <div className="flex items-center justify-between">
                      <div>
                        <h4 className="font-semibold text-gray-900 mb-1 flex items-center">
                          <InformationCircleIcon className="w-5 h-5 mr-2 text-gray-400" />
                          No Data Source Configured
                        </h4>
                        <p className="text-sm text-gray-500">This link type uses file system storage</p>
                      </div>
                    </div>
                    <div className="mt-3 text-xs text-gray-500">
                      <p>To configure a data source, edit the schema.yaml file and add a data_source mapping for this link type.</p>
                    </div>
                  </div>
                )}
              </div>
            )}
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
              <div className="flex items-center justify-between">
                <div className="font-medium">{lt.display_name || lt.name}</div>
                {lt.data_source && (
                  <ServerIcon 
                    className="w-4 h-4 text-green-600" 
                    title="Has data source configured"
                  />
                )}
              </div>
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

