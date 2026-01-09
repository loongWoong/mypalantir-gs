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
  CodeBracketIcon,

} from '@heroicons/react/24/outline';
import type { ObjectType, LinkType } from '../api/client';
import { schemaApi } from '../api/client';
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
  
  const { workspaces, selectedWorkspaceId, selectedWorkspace, setSelectedWorkspaceId, refreshWorkspaces } = useWorkspace();

  useEffect(() => {
    const loadData = async () => {
      try {
        const [objectTypesData, linkTypesData] = await Promise.all([
          schemaApi.getObjectTypes(),
          schemaApi.getLinkTypes(),
        ]);
        setObjectTypes(objectTypesData);
        setLinkTypes(linkTypesData);
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
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <div
        className={`${
          sidebarOpen ? 'w-64' : 'w-0'
        } bg-white border-r border-gray-200 transition-all duration-300 overflow-hidden flex flex-col`}
      >
        <div className="p-4 border-b border-gray-200">
          <div className="flex items-center justify-between mb-3">
            <h1 className="text-xl font-bold text-gray-900">MyPalantir</h1>
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
                className="flex-1 px-3 py-1.5 text-sm border border-gray-300 rounded-lg bg-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
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
                  className="p-1.5 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded"
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
                        className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center gap-2"
                      >
                        <PlusIcon className="w-4 h-4" />
                        创建工作空间
                      </button>
                      {selectedWorkspaceId && (
                        <button
                          onClick={() => handleEditWorkspace(selectedWorkspaceId)}
                          className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center gap-2"
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
          <nav className="p-2">
            <Link
              to="/schema"
              className={`flex items-center px-3 py-2 rounded-lg mb-2 ${
                isActive('/schema') && location.pathname === '/schema'
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-gray-700 hover:bg-gray-100'
              }`}
            >
              <Squares2X2Icon className="w-5 h-5 mr-3" />
              Schema
            </Link>

            <Link
              to="/schema-graph"
              className={`flex items-center px-3 py-2 rounded-lg mb-2 ${
                isActive('/schema-graph')
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-gray-700 hover:bg-gray-100'
              }`}
            >
              <ChartBarIcon className="w-5 h-5 mr-3" />
              Schema Graph
            </Link>

            {/* <Link
              to="/data-sources"
              className={`flex items-center px-3 py-2 rounded-lg mb-2 ${
                isActive('/data-sources')
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-gray-700 hover:bg-gray-100'
              }`}
            >
              <ServerIcon className="w-5 h-5 mr-3" />
              Data Sources
            </Link> */}

            <Link
              to="/query"
              className={`flex items-center px-3 py-2 rounded-lg mb-2 ${
                isActive('/query') && location.pathname === '/query'
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-gray-700 hover:bg-gray-100'
              }`}
            >
              <MagnifyingGlassIcon className="w-5 h-5 mr-3" />
              Query Builder
            </Link>

            <Link
              to="/metrics"
              className={`flex items-center px-3 py-2 rounded-lg mb-2 ${
                isActive('/metrics')
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-gray-700 hover:bg-gray-100'
              }`}
            >
              <ChartBarIcon className="w-5 h-5 mr-3" />
              指标管理
            </Link>

            <Link
              to="/natural-language-query"
              className={`flex items-center px-3 py-2 rounded-lg mb-2 ${
                isActive('/natural-language-query')
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-gray-700 hover:bg-gray-100'
              }`}
            >
              <SparklesIcon className="w-5 h-5 mr-3" />
              Natural Language Query
            </Link>

            <Link
              to="/sql-parse"
              className={`flex items-center px-3 py-2 rounded-lg mb-2 ${
                isActive('/sql-parse')
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-gray-700 hover:bg-gray-100'
              }`}
            >
              <CodeBracketIcon className="w-5 h-5 mr-3" />
              SQL 解析
            </Link>



            {!loading && (
              <>
                <div className="mt-4 mb-2">
                  <h2 className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    Object Types {selectedWorkspace && `(${filteredObjectTypes.length})`}
                  </h2>
                </div>
                {filteredObjectTypes.length === 0 && selectedWorkspace ? (
                  <div className="px-3 py-2 text-sm text-gray-500">
                    该工作空间未添加对象类型
                  </div>
                ) : (
                  filteredObjectTypes.map((ot) => (
                    <Link
                      key={ot.name}
                      to={`/instances/${ot.name}`}
                      className={`flex items-center px-3 py-2 rounded-lg mb-1 ${
                        isActive(`/instances/${ot.name}`)
                          ? 'bg-blue-50 text-blue-700'
                          : 'text-gray-700 hover:bg-gray-100'
                      }`}
                    >
                      <CubeIcon className="w-4 h-4 mr-3" />
                      <span className="text-sm">{ot.display_name || ot.name}</span>
                    </Link>
                  ))
                )}

                <div className="mt-4 mb-2">
                  <h2 className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    Link Types {selectedWorkspace && `(${filteredLinkTypes.length})`}
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
                      className={`flex items-center px-3 py-2 rounded-lg mb-1 ${
                        isActive(`/links/${lt.name}`)
                          ? 'bg-blue-50 text-blue-700'
                          : 'text-gray-700 hover:bg-gray-100'
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
        <header className="bg-white border-b border-gray-200 px-4 py-3 flex items-center">
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="lg:hidden text-gray-500 hover:text-gray-700 mr-3"
          >
            <Bars3Icon className="w-6 h-6" />
          </button>
          <h2 className="text-lg font-semibold text-gray-900">
            {location.pathname === '/schema' && 'Schema Browser'}
            {location.pathname.startsWith('/instances/') && 'Instances'}
            {location.pathname.startsWith('/links/') && 'Links'}
            {location.pathname.startsWith('/schema-graph') && 'Schema Graph'}
            {location.pathname.startsWith('/data-sources') && 'Data Sources'}
            {location.pathname.startsWith('/metrics') && '指标管理'}
            {location.pathname === '/query' && 'Query Builder'}
            {location.pathname === '/natural-language-query' && 'Natural Language Query'}
            {location.pathname === '/sql-parse' && 'SQL 解析'}

          </h2>
        </header>

        {/* Content area */}
        <main className={`flex-1 ${location.pathname.startsWith('/schema-graph') ? 'overflow-hidden p-0' : 'overflow-y-auto p-6'}`}>{children}</main>
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

