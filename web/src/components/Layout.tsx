import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { 
  Squares2X2Icon, 
  CubeIcon, 
  LinkIcon,
  Bars3Icon,
  XMarkIcon,
  ChartBarIcon,
  MagnifyingGlassIcon,
  PlusIcon,
  PencilIcon,
  ChevronDownIcon,
  SparklesIcon,
  BuildingOfficeIcon,
  ArrowPathIcon,
  MapPinIcon,
  UserIcon,
  TruckIcon,
  CreditCardIcon,
  ArrowDownTrayIcon,
  ArrowUpTrayIcon,
  MapIcon,
  ListBulletIcon,
  CalculatorIcon,
  ClipboardDocumentCheckIcon
} from '@heroicons/react/24/outline';
import type { ObjectType, LinkType, ModelInfo, CurrentModel } from '../api/client';
import { schemaApi, modelApi } from '../api/client';
import { useEffect } from 'react';
import { useWorkspace } from '../WorkspaceContext';
import WorkspaceDialog from './WorkspaceDialog';

interface LayoutProps {
  children: React.ReactNode;
}

export default function Layout({ children }: LayoutProps) {
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [objectTypes, setObjectTypes] = useState<ObjectType[]>([]);
  const [linkTypes, setLinkTypes] = useState<LinkType[]>([]);
  const [loading, setLoading] = useState(true);
  const [workspaceDialogOpen, setWorkspaceDialogOpen] = useState(false);
  const [editingWorkspaceId, setEditingWorkspaceId] = useState<string | undefined>(undefined);
  const [workspaceDropdownOpen, setWorkspaceDropdownOpen] = useState(false);
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [currentModel, setCurrentModel] = useState<CurrentModel | null>(null);
  
  const { workspaces, selectedWorkspaceId, selectedWorkspace, setSelectedWorkspaceId, refreshWorkspaces } = useWorkspace();

  useEffect(() => {
    const loadData = async () => {
      try {
        const [objectTypesData, linkTypesData, modelsData, currentModelData] = await Promise.all([
          schemaApi.getObjectTypes(),
          schemaApi.getLinkTypes(),
          modelApi.listModels(),
          modelApi.getCurrentModel(),
        ]);
        setObjectTypes(objectTypesData);
        setLinkTypes(linkTypesData);
        setModels(modelsData);
        setCurrentModel(currentModelData);
      } catch (error) {
        console.error('Failed to load schema:', error);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, []);

  const isActive = (path: string) => {
    return location.pathname.startsWith(path);
  };

  // 根据工作空间过滤 Object Types 和 Link Types
  // 如果选择了工作空间，只显示工作空间内添加的对象和关系；否则显示全部
  const filteredObjectTypes = selectedWorkspace
    ? objectTypes.filter((ot) => 
        selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0
          ? selectedWorkspace.object_types.includes(ot.name)
          : false
      )
    : objectTypes;

  const filteredLinkTypes = selectedWorkspace
    ? linkTypes.filter((lt) => 
        selectedWorkspace.link_types && selectedWorkspace.link_types.length > 0
          ? selectedWorkspace.link_types.includes(lt.name)
          : false
      )
    : linkTypes;

  // 为每个 object type 选择合适的图标
  const getObjectTypeIcon = (objectTypeName: string) => {
    const iconMap: Record<string, React.ComponentType<{ className?: string }>> = {
      // 实体类型
      'TollStation': BuildingOfficeIcon,      // 收费站 - 建筑物图标
      'TollLane': ArrowPathIcon,              // 收费车道 - 路径/循环图标
      'Gantry': MapPinIcon,                   // ETC门架 - 地图标记图标
      'Operator': UserIcon,                    // 操作员 - 用户图标
      'Vehicle': TruckIcon,                    // 车辆 - 卡车图标
      'Media': CreditCardIcon,                 // 通行介质 - 卡片图标
      // 交易类型
      'EntryTransaction': ArrowDownTrayIcon,  // 入口交易 - 下载/进入图标
      'ExitTransaction': ArrowUpTrayIcon,     // 出口交易 - 上传/离开图标
      'GantryTransaction': MapPinIcon,         // 门架交易 - 地图标记图标
      // 路径类型
      'Passage': MapIcon,                      // 通行路径 - 地图图标
      'PathDetail': ListBulletIcon,           // 路径明细 - 列表图标
      'SplitDetail': CalculatorIcon,           // 拆分明细 - 计算器图标
    };
    
    return iconMap[objectTypeName] || CubeIcon; // 默认使用 CubeIcon
  };

  const handleCreateWorkspace = () => {
    setEditingWorkspaceId(undefined);
    setWorkspaceDialogOpen(true);
  };

  const handleEditWorkspace = (id: string) => {
    setEditingWorkspaceId(id);
    setWorkspaceDialogOpen(true);
    setWorkspaceDropdownOpen(false);
  };

  const handleWorkspaceDialogSuccess = () => {
    refreshWorkspaces();
  };

  return (
    <div className="flex h-screen bg-background text-text font-sans">
      {/* Sidebar */}
      <div
        className={`${
          sidebarOpen ? 'w-64' : 'w-0'
        } bg-white border-r border-gray-200 transition-all duration-300 overflow-hidden flex flex-col`}
      >
        <div className="p-4 border-b border-gray-200">
          <div className="flex items-center justify-between mb-3">
            <h1 className="text-xl font-bold text-text">MyPalantir</h1>
            <button
              onClick={() => setSidebarOpen(false)}
              className="lg:hidden text-gray-500 hover:text-gray-700"
            >
              <XMarkIcon className="w-5 h-5" />
            </button>
          </div>
          
          {/* 工作空间选择器 */}
          <div className="relative">
            <div className="flex items-center gap-2">
              <select
                value={selectedWorkspaceId || ''}
                onChange={(e) => setSelectedWorkspaceId(e.target.value || null)}
                className="flex-1 px-3 py-1.5 text-sm border border-gray-300 rounded-lg bg-white focus:ring-2 focus:ring-primary focus:border-transparent outline-none"
              >
                <option value="">全部对象</option>
                {workspaces.map((ws) => (
                  <option key={ws.id} value={ws.id}>
                    {ws.display_name || ws.name || ws.id.substring(0, 8)}
                  </option>
                ))}
              </select>
              <div className="relative">
                <button
                  onClick={() => setWorkspaceDropdownOpen(!workspaceDropdownOpen)}
                  className="p-1.5 text-gray-500 hover:text-text hover:bg-gray-100 rounded cursor-pointer"
                  title="管理工作空间"
                >
                  <ChevronDownIcon className="w-4 h-4" />
                </button>
                {workspaceDropdownOpen && (
                  <>
                    <div
                      className="fixed inset-0 z-10"
                      onClick={() => setWorkspaceDropdownOpen(false)}
                    />
                    <div className="absolute right-0 mt-1 w-48 bg-white rounded-lg shadow-lg border border-gray-200 z-20">
                      <button
                        onClick={handleCreateWorkspace}
                        className="w-full text-left px-4 py-2 text-sm text-text hover:bg-gray-50 flex items-center gap-2 cursor-pointer"
                      >
                        <PlusIcon className="w-4 h-4" />
                        创建工作空间
                      </button>
                      {selectedWorkspaceId && (
                        <button
                          onClick={() => handleEditWorkspace(selectedWorkspaceId)}
                          className="w-full text-left px-4 py-2 text-sm text-text hover:bg-gray-50 flex items-center gap-2 cursor-pointer"
                        >
                          <PencilIcon className="w-4 h-4" />
                          编辑当前工作空间
                        </button>
                      )}
                    </div>
                  </>
                )}
              </div>
            </div>
            {selectedWorkspace && (
              <p className="text-xs text-gray-500 mt-1">
                {selectedWorkspace.object_types?.length || 0} 个对象类型, {selectedWorkspace.link_types?.length || 0} 个关系类型
              </p>
            )}
          </div>
        </div>

        <div className="flex-1 overflow-y-auto">
          <nav className="p-2 space-y-1">
            <Link
              to="/schema"
              className={`flex items-center px-3 py-2 rounded-lg transition-colors duration-200 ${
                isActive('/schema') && location.pathname === '/schema'
                  ? 'bg-blue-50 text-primary font-medium'
                  : 'text-text hover:bg-gray-100'
              }`}
            >
              <Squares2X2Icon className="w-5 h-5 mr-3" />
              本体模型
            </Link>

            <Link
              to="/schema-graph"
              className={`flex items-center px-3 py-2 rounded-lg transition-colors duration-200 ${
                isActive('/schema-graph')
                  ? 'bg-blue-50 text-primary font-medium'
                  : 'text-text hover:bg-gray-100'
              }`}
            >
              <ChartBarIcon className="w-5 h-5 mr-3" />
              本体关系图
            </Link>

            {/* <Link
              to="/data-sources"
              className={`flex items-center px-3 py-2 rounded-lg transition-colors duration-200 ${
                isActive('/data-sources')
                  ? 'bg-blue-50 text-primary font-medium'
                  : 'text-text hover:bg-gray-100'
              }`}
            >
              <ServerIcon className="w-5 h-5 mr-3" />
              Data Sources
            </Link> */}

            <Link
              to="/query"
              className={`flex items-center px-3 py-2 rounded-lg transition-colors duration-200 ${
                isActive('/query') && location.pathname === '/query'
                  ? 'bg-blue-50 text-primary font-medium'
                  : 'text-text hover:bg-gray-100'
              }`}
            >
              <MagnifyingGlassIcon className="w-5 h-5 mr-3" />
              查询构建器
            </Link>

            <Link
              to="/metrics"
              className={`flex items-center px-3 py-2 rounded-lg transition-colors duration-200 ${
                isActive('/metrics')
                  ? 'bg-blue-50 text-primary font-medium'
                  : 'text-text hover:bg-gray-100'
              }`}
            >
              <ChartBarIcon className="w-5 h-5 mr-3" />
              指标管理
            </Link>

            <Link
              to="/natural-language-query"
              className={`flex items-center px-3 py-2 rounded-lg transition-colors duration-200 ${
                isActive('/natural-language-query')
                  ? 'bg-blue-50 text-primary font-medium'
                  : 'text-text hover:bg-gray-100'
              }`}
            >
              <SparklesIcon className="w-5 h-5 mr-3" />
              自然语言查询
            </Link>

            <Link
              to="/data-comparison"
              className={`flex items-center px-3 py-2 rounded-lg transition-colors duration-200 ${
                isActive('/data-comparison')
                  ? 'bg-blue-50 text-primary font-medium'
                  : 'text-text hover:bg-gray-100'
              }`}
            >
              <ClipboardDocumentCheckIcon className="w-5 h-5 mr-3" />
              数据对账
            </Link>



            {!loading && (
              <>
                <div className="mt-6 mb-2">
                  <h2 className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    本体类型 {selectedWorkspace && `(${filteredObjectTypes.length})`}
                  </h2>
                </div>
                {filteredObjectTypes.length === 0 && selectedWorkspace ? (
                  <div className="px-3 py-2 text-sm text-gray-500">
                    该工作空间未添加对象类型
                  </div>
                ) : (
                  filteredObjectTypes.map((ot) => {
                    const IconComponent = getObjectTypeIcon(ot.name);
                    return (
                      <Link
                        key={ot.name}
                        to={`/instances/${ot.name}`}
                        className={`flex items-center px-3 py-2 rounded-lg mb-1 transition-colors duration-200 ${
                          isActive(`/instances/${ot.name}`)
                            ? 'bg-blue-50 text-primary font-medium'
                            : 'text-text hover:bg-gray-100'
                        }`}
                      >
                        <IconComponent className="w-4 h-4 mr-3" />
                        <span className="text-sm">{ot.display_name || ot.name}</span>
                      </Link>
                    );
                  })
                )}

                <div className="mt-6 mb-2">
                  <h2 className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    关系类型 {selectedWorkspace && `(${filteredLinkTypes.length})`}
                  </h2>
                </div>
                {filteredLinkTypes.length === 0 && selectedWorkspace ? (
                  <div className="px-3 py-2 text-sm text-gray-500">
                    该工作空间未添加关系类型
                  </div>
                ) : (
                  filteredLinkTypes.map((lt) => (
                    <Link
                      key={lt.name}
                      to={`/links/${lt.name}`}
                      className={`flex items-center px-3 py-2 rounded-lg mb-1 transition-colors duration-200 ${
                        isActive(`/links/${lt.name}`)
                          ? 'bg-blue-50 text-primary font-medium'
                          : 'text-text hover:bg-gray-100'
                      }`}
                    >
                      <LinkIcon className="w-4 h-4 mr-3" />
                      <span className="text-sm">{lt.display_name || lt.name}</span>
                    </Link>
                  ))
                )}
              </>
            )}
          </nav>
        </div>
      </div>

      {/* Main content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top bar */}
        <header className="bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between shadow-sm z-10">
          <div className="flex items-center">
            <button
              onClick={() => setSidebarOpen(!sidebarOpen)}
              className="lg:hidden text-gray-500 hover:text-text mr-4 cursor-pointer"
            >
              <Bars3Icon className="w-6 h-6" />
            </button>
            <h2 className="text-xl font-semibold text-text tracking-tight">
              {location.pathname === '/schema' && 'Schema Browser'}
              {location.pathname.startsWith('/instances/') && 'Instances'}
              {location.pathname.startsWith('/links/') && 'Links'}
              {location.pathname.startsWith('/schema-graph') && 'Schema Graph'}
              {location.pathname.startsWith('/data-sources') && 'Data Sources'}
              {location.pathname.startsWith('/metrics') && 'Metric Manage'}
              {location.pathname === '/query' && 'Query Builder'}
              {location.pathname === '/natural-language-query' && 'Natural Language Query'}
              {location.pathname === '/data-comparison' && 'Data Comparison'}
            </h2>
          </div>
          
          {/* 模型选择器 */}
          {currentModel && models.length > 0 ? (
            <div className="flex items-center space-x-3">
              <label className="text-sm font-medium text-gray-600 whitespace-nowrap">当前模型:</label>
              <div className="relative">
                <select
                  value={currentModel.modelId}
                  onChange={(e) => {
                    const selectedModel = models.find(m => m.id === e.target.value);
                    if (selectedModel && selectedModel.id !== currentModel.modelId) {
                      if (confirm(`切换模型需要重启应用。\n\n当前模型: ${currentModel.modelId}\n选择模型: ${selectedModel.displayName}\n\n请在 application.properties 中设置:\nontology.model=${selectedModel.id}\n\n然后重启应用。`)) {
                        // 可以打开配置文件或显示说明
                        console.log(`请设置 ontology.model=${selectedModel.id} 并重启应用`);
                      }
                    }
                  }}
                  className="px-3 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary bg-white min-w-[140px]"
                >
                  {models.map((model) => (
                    <option key={model.id} value={model.id}>
                      {model.displayName}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          ) : (
            <div className="text-xs text-gray-400 animate-pulse">加载模型中...</div>
          )}
        </header>

        {/* Content area */}
        <main className={`flex-1 ${location.pathname.startsWith('/schema-graph') ? 'overflow-hidden p-0' : 'overflow-y-auto p-8'}`}>{children}</main>
      </div>

      {/* 工作空间对话框 */}
      {workspaceDialogOpen && (
        <WorkspaceDialog
          workspaceId={editingWorkspaceId}
          onClose={() => {
            setWorkspaceDialogOpen(false);
            setEditingWorkspaceId(undefined);
          }}
          onSuccess={handleWorkspaceDialogSuccess}
        />
      )}
    </div>
  );
}

