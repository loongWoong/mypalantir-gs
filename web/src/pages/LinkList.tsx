import { useEffect, useState, useRef } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import type { Link, LinkType, Instance } from '../api/client';
import { linkApi, schemaApi } from '../api/client';
import { ArrowPathIcon, ArrowTopRightOnSquareIcon, ChartBarIcon, TableCellsIcon, ArrowDownTrayIcon, Cog6ToothIcon, FunnelIcon } from '@heroicons/react/24/outline';
import { ToastContainer, useToast } from '../components/Toast';
import ButtonGroup from '../components/ButtonGroup';
import PropertyStatistics, { type AnalysisDisplayOptions } from '../components/PropertyStatistics';
import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';

export default function LinkList() {
  const { linkType } = useParams<{ linkType: string }>();
  const [links, setLinks] = useState<Link[]>([]);
  const [linkTypeDef, setLinkTypeDef] = useState<LinkType | null>(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const limit = 20;
  const { toasts, showToast, removeToast } = useToast();

  // View Mode
  const [viewMode, setViewMode] = useState<'properties' | 'list'>('properties');
  
  // Stats
  const [stats, setStats] = useState<{ source_count: number; target_count: number; link_count: number; source_coverage: number; target_coverage: number } | null>(null);
  const [allLinks, setAllLinks] = useState<Link[]>([]);
  const [loadingStats, setLoadingStats] = useState(false);
  const [showAnalysisSettings, setShowAnalysisSettings] = useState(false);
  const [analysisDisplayOptions, setAnalysisDisplayOptions] = useState<AnalysisDisplayOptions>({
    showSummary: true,
    showNumericStats: true,
    showChart: true,
  });
  const [showPropertyFilter, setShowPropertyFilter] = useState(false);
  const [visibleProperties, setVisibleProperties] = useState<Set<string>>(new Set());
  const [showExportMenu, setShowExportMenu] = useState(false);
  const analysisRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (linkType) {
      if (viewMode === 'list') {
        loadData();
      } else {
        loadStats();
      }
    }
  }, [linkType, offset, viewMode]);

  // Initialize visible properties
  useEffect(() => {
    if (linkTypeDef?.properties) {
      setVisibleProperties(new Set(linkTypeDef.properties.map(p => p.name)));
    }
  }, [linkTypeDef]);

  const loadData = async () => {
    if (!linkType) return;
    try {
      setLoading(true);
      const [linkTypeData, linksData] = await Promise.all([
        schemaApi.getLinkType(linkType),
        linkApi.list(linkType, offset, limit),
      ]);
      setLinkTypeDef(linkTypeData);
      setLinks(linksData.items);
      setTotal(linksData.total);
    } catch (error) {
      console.error('Failed to load data:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadStats = async () => {
    if (!linkType) return;
    try {
      setLoading(true);
      setLoadingStats(true);
      
      const linkTypeData = await schemaApi.getLinkType(linkType);
      setLinkTypeDef(linkTypeData);

      const [statsData, allLinksData] = await Promise.all([
        linkApi.getStats(linkType),
        linkApi.list(linkType, 0, 5000) // Load up to 5000 links for property analysis
      ]);
      
      setStats(statsData);
      setAllLinks(allLinksData.items);
      setLoadingStats(false);
    } catch (error) {
      console.error('Failed to load stats:', error);
      showToast('加载统计数据失败', 'error');
    } finally {
      setLoading(false);
      setLoadingStats(false);
    }
  };

  const handleSync = async () => {
    if (!linkType) return;
    try {
      setSyncing(true);
      const result = await linkApi.sync(linkType);
      const message = `同步完成！\n创建关系: ${result.links_created}`;
      showToast(message, 'success');
      if (viewMode === 'list') {
        loadData();
      } else {
        loadStats();
      }
    } catch (error: any) {
      console.error('Failed to sync links:', error);
      const errorMessage = '同步失败: ' + (error.response?.data?.message || error.message);
      showToast(errorMessage, 'error');
    } finally {
      setSyncing(false);
    }
  };

  const handleExport = async (type: 'png' | 'pdf') => {
    if (!analysisRef.current) return;
    
    try {
      showToast('正在生成导出文件...', 'info');
      await new Promise(resolve => setTimeout(resolve, 100));
      
      const canvas = await html2canvas(analysisRef.current, {
        useCORS: true,
        logging: false,
        backgroundColor: '#ffffff',
        scale: 2
      });
      
      const fileName = `${linkTypeDef?.name || 'analysis'}_properties.${type}`;
      
      if (type === 'png') {
        const link = document.createElement('a');
        link.download = fileName;
        link.href = canvas.toDataURL('image/png');
        link.click();
      } else {
        const imgData = canvas.toDataURL('image/png');
        const imgWidth = canvas.width;
        const imgHeight = canvas.height;
        
        const pdf = new jsPDF({
          orientation: imgWidth > imgHeight ? 'landscape' : 'portrait',
          unit: 'px',
          format: [imgWidth, imgHeight]
        });
        
        pdf.addImage(imgData, 'PNG', 0, 0, imgWidth, imgHeight);
        pdf.save(fileName);
      }
      
      setShowExportMenu(false);
      showToast('导出成功', 'success');
    } catch (error) {
      console.error('Export failed:', error);
      showToast('导出失败', 'error');
    }
  };

  if (loading && !linkTypeDef) {
    return <div className="text-center py-12">Loading...</div>;
  }

  if (!linkTypeDef && !loading) {
    return <div className="text-center py-12">Link type not found</div>;
  }

  // Helper to cast Link to Instance for PropertyStatistics
  const linksAsInstances = allLinks as unknown as Instance[];

  return (
    <div className="flex flex-col h-screen overflow-hidden">
      {/* Top Header & Link Info */}
      <div className="flex-shrink-0 sticky top-0 z-10 bg-white border-b border-gray-200 overflow-y-auto max-h-[60vh]">
        <div className="px-6 py-6 space-y-6">
            {/* Header Title & Actions */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <h1 className="text-2xl font-bold text-gray-900">{linkTypeDef?.display_name || linkTypeDef?.name}</h1>
                    {linkTypeDef?.url && (
                        <a href={linkTypeDef.url} target="_blank" rel="noopener noreferrer" className="text-gray-400 hover:text-blue-600 transition-colors" title="查看外部处理系统">
                            <ArrowTopRightOnSquareIcon className="w-5 h-5" />
                        </a>
                    )}
                </div>
                <div className="flex gap-3">
                    <ButtonGroup
                        value={viewMode}
                        onChange={(val) => setViewMode(val as 'properties' | 'list')}
                        options={[
                            { value: 'properties', label: '属性分析', icon: ChartBarIcon, title: '属性分析视图' },
                            { value: 'list', label: '数据列表', icon: TableCellsIcon, title: '数据列表视图' },
                        ]}
                    />
                    {linkTypeDef?.property_mappings && Object.keys(linkTypeDef.property_mappings).length > 0 && (
                        <button
                            onClick={handleSync}
                            disabled={syncing}
                            className="flex items-center px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            <ArrowPathIcon className={`w-5 h-5 mr-2 ${syncing ? 'animate-spin' : ''}`} />
                            {syncing ? '同步中...' : '同步关系'}
                        </button>
                    )}
                </div>
            </div>

            {/* Description */}
            {linkTypeDef?.description && (
                <p className="text-gray-600">{linkTypeDef.description}</p>
            )}

            {/* Visual Relation Diagram */}
            {linkTypeDef && (
                <div className="bg-gray-50 rounded-xl border border-gray-200 p-8">
                    <div className="flex items-center justify-center gap-12">
                        {/* Source */}
                        <div className="flex flex-col items-center gap-3">
                             <div className="px-3 py-1 bg-blue-100 text-blue-700 rounded-full text-xs font-semibold uppercase tracking-wide">Source</div>
                             <div className="bg-white px-8 py-4 rounded-lg shadow-sm border border-blue-200 font-mono font-bold text-lg text-gray-900 min-w-[160px] text-center">
                                {linkTypeDef.source_type}
                             </div>
                        </div>

                        {/* Connection */}
                        <div className="flex flex-col items-center gap-2 flex-1 max-w-[240px]">
                            <div className="text-xs text-gray-500 font-medium px-2 py-0.5 bg-gray-200 rounded text-center">{linkTypeDef.cardinality}</div>
                            <div className="relative w-full h-px bg-gray-300">
                                <div className={`absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-2 h-2 rounded-full ${linkTypeDef.direction === 'undirected' ? 'bg-gray-400' : 'bg-transparent'}`}></div>
                                {linkTypeDef.direction !== 'undirected' && (
                                    <div className="absolute right-0 top-1/2 -translate-y-1/2 text-gray-300 -mr-1.5">▶</div>
                                )}
                            </div>
                            <div className="text-xs text-gray-400">{linkTypeDef.direction === 'undirected' ? '无向关系' : '有向关系'}</div>
                        </div>

                        {/* Target */}
                        <div className="flex flex-col items-center gap-3">
                             <div className="px-3 py-1 bg-green-100 text-green-700 rounded-full text-xs font-semibold uppercase tracking-wide">Target</div>
                             <div className="bg-white px-8 py-4 rounded-lg shadow-sm border border-green-200 font-mono font-bold text-lg text-gray-900 min-w-[160px] text-center">
                                {linkTypeDef.target_type}
                             </div>
                        </div>
                    </div>

                    {/* Property Mappings */}
                    {linkTypeDef.property_mappings && Object.keys(linkTypeDef.property_mappings).length > 0 && (
                        <div className="mt-8 pt-8 border-t border-gray-200/60">
                            <div className="text-center text-xs font-medium text-gray-500 mb-4 uppercase tracking-wider">属性映射关系</div>
                            <div className="space-y-3 max-w-lg mx-auto">
                                {Object.entries(linkTypeDef.property_mappings).map(([sourceProp, targetProp], idx) => (
                                    <div key={idx} className="flex items-center justify-center gap-6 text-sm group hover:bg-white hover:shadow-sm rounded-lg p-2 transition-all">
                                        <div className="flex-1 text-right font-mono text-gray-600 group-hover:text-blue-600 transition-colors font-medium">{sourceProp}</div>
                                        <div className="text-gray-300 text-xs">●</div>
                                        <div className="flex-1 text-left font-mono text-gray-600 group-hover:text-green-600 transition-colors font-medium">{targetProp}</div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-6 pb-6 pt-6">
        {viewMode === 'properties' && linkTypeDef && (
          <div className="space-y-6">
            {/* Stats Cards */}
            {stats && (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                <div className="bg-white p-5 rounded-xl shadow-sm border border-gray-200 hover:shadow-md transition-shadow">
                  <div className="text-sm font-medium text-gray-500 mb-2">源对象数量</div>
                  <div className="text-3xl font-bold text-gray-900">{stats.source_count.toLocaleString()}</div>
                  <div className="text-xs text-gray-400 mt-2 flex items-center gap-1">
                    <span className="w-2 h-2 rounded-full bg-blue-500"></span>
                    {linkTypeDef.source_type}
                  </div>
                </div>
                <div className="bg-white p-5 rounded-xl shadow-sm border border-gray-200 hover:shadow-md transition-shadow">
                  <div className="text-sm font-medium text-gray-500 mb-2">目标对象数量</div>
                  <div className="text-3xl font-bold text-gray-900">{stats.target_count.toLocaleString()}</div>
                  <div className="text-xs text-gray-400 mt-2 flex items-center gap-1">
                    <span className="w-2 h-2 rounded-full bg-green-500"></span>
                    {linkTypeDef.target_type}
                  </div>
                </div>
                <div className="bg-white p-5 rounded-xl shadow-sm border border-gray-200 hover:shadow-md transition-shadow">
                  <div className="text-sm font-medium text-gray-500 mb-2">已建立关系</div>
                  <div className="text-3xl font-bold text-blue-600">{stats.link_count.toLocaleString()}</div>
                  <div className="text-xs text-gray-400 mt-2">成功建立连接</div>
                </div>
                <div className="bg-white p-5 rounded-xl shadow-sm border border-gray-200 hover:shadow-md transition-shadow">
                  <div className="text-sm font-medium text-gray-500 mb-2">覆盖率</div>
                  <div className="flex justify-between items-end mt-1">
                    <div>
                      <div className="text-2xl font-bold text-gray-900">{(stats.source_coverage * 100).toFixed(1)}%</div>
                      <div className="text-xs text-gray-400 mt-1">源对象</div>
                    </div>
                    <div className="text-right">
                      <div className="text-2xl font-bold text-gray-900">{(stats.target_coverage * 100).toFixed(1)}%</div>
                      <div className="text-xs text-gray-400 mt-1">目标对象</div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* 属性统计分析 */}
            {(linkTypeDef.properties || []).length > 0 && (
              <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
                <div className="flex items-center justify-between mb-6">
                  <h2 className="text-xl font-bold text-gray-900">属性分析</h2>
                  <div className="flex gap-2">
                    <button
                      onClick={() => setShowPropertyFilter(!showPropertyFilter)}
                      className={`flex items-center px-3 py-2 text-sm rounded-lg transition-colors ${
                        showPropertyFilter 
                          ? 'bg-blue-100 text-blue-700' 
                          : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
                      }`}
                    >
                      <FunnelIcon className="w-5 h-5 mr-2" />
                      筛选属性
                    </button>
                    <button
                      onClick={() => setShowAnalysisSettings(!showAnalysisSettings)}
                      className={`flex items-center px-3 py-2 text-sm rounded-lg transition-colors ${
                        showAnalysisSettings 
                          ? 'bg-gray-200 text-gray-900' 
                          : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
                      }`}
                    >
                      <Cog6ToothIcon className="w-5 h-5 mr-2" />
                      设置
                    </button>
                    <div className="relative">
                      <button
                        onClick={() => setShowExportMenu(!showExportMenu)}
                        className={`flex items-center px-3 py-2 text-sm rounded-lg transition-colors ${
                          showExportMenu 
                            ? 'bg-gray-200 text-gray-900' 
                            : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
                        }`}
                      >
                        <ArrowDownTrayIcon className="w-5 h-5 mr-2" />
                        导出
                      </button>
                      {showExportMenu && (
                        <div className="absolute right-0 mt-2 w-32 bg-white rounded-lg shadow-lg border border-gray-100 z-10 py-1">
                          <button
                            onClick={() => handleExport('png')}
                            className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center"
                          >
                            <span className="w-8">PNG</span>
                            <span className="text-xs text-gray-400">图片</span>
                          </button>
                          <button
                            onClick={() => handleExport('pdf')}
                            className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center"
                          >
                            <span className="w-8">PDF</span>
                            <span className="text-xs text-gray-400">文档</span>
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* 属性筛选面板 */}
                {showPropertyFilter && (
                  <div className="mb-6 p-4 bg-gray-50 rounded-lg border border-gray-200">
                    <div className="flex justify-between items-center mb-3">
                      <h3 className="text-sm font-semibold text-gray-900">选择显示的属性</h3>
                      <div className="flex gap-2 text-xs">
                        <button 
                          onClick={() => setVisibleProperties(new Set((linkTypeDef.properties || []).map(p => p.name)))}
                          className="text-blue-600 hover:text-blue-800 font-medium"
                        >
                          全选
                        </button>
                        <span className="text-gray-300">|</span>
                        <button 
                          onClick={() => setVisibleProperties(new Set())}
                          className="text-gray-500 hover:text-gray-700"
                        >
                          清空
                        </button>
                      </div>
                    </div>
                    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-2">
                      {(linkTypeDef.properties || []).map(prop => (
                        <label key={prop.name} className="flex items-center cursor-pointer hover:bg-white p-2 rounded border border-transparent hover:border-gray-200 transition-colors">
                          <input
                            type="checkbox"
                            checked={visibleProperties.has(prop.name)}
                            onChange={(e) => {
                              const newSet = new Set(visibleProperties);
                              if (e.target.checked) {
                                newSet.add(prop.name);
                              } else {
                                newSet.delete(prop.name);
                              }
                              setVisibleProperties(newSet);
                            }}
                            className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500 border-gray-300"
                          />
                          <span className="ml-2 text-sm text-gray-700 truncate select-none" title={prop.name}>{prop.name}</span>
                        </label>
                      ))}
                    </div>
                  </div>
                )}

                {/* 分析设置面板 */}
                {showAnalysisSettings && (
                  <div className="mb-6 p-4 bg-gray-50 rounded-lg border border-gray-200">
                    <h3 className="text-sm font-semibold text-gray-900 mb-3">显示选项</h3>
                    <div className="flex flex-wrap gap-4">
                      <label className="flex items-center cursor-pointer">
                        <input
                          type="checkbox"
                          checked={analysisDisplayOptions.showSummary}
                          onChange={(e) => setAnalysisDisplayOptions({
                            ...analysisDisplayOptions,
                            showSummary: e.target.checked,
                          })}
                          className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                        />
                        <span className="ml-2 text-sm text-gray-700">统计摘要</span>
                      </label>
                      <label className="flex items-center cursor-pointer">
                        <input
                          type="checkbox"
                          checked={analysisDisplayOptions.showNumericStats}
                          onChange={(e) => setAnalysisDisplayOptions({
                            ...analysisDisplayOptions,
                            showNumericStats: e.target.checked,
                          })}
                          className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                        />
                        <span className="ml-2 text-sm text-gray-700">数值统计</span>
                      </label>
                      <label className="flex items-center cursor-pointer">
                        <input
                          type="checkbox"
                          checked={analysisDisplayOptions.showChart}
                          onChange={(e) => setAnalysisDisplayOptions({
                            ...analysisDisplayOptions,
                            showChart: e.target.checked,
                          })}
                          className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                        />
                        <span className="ml-2 text-sm text-gray-700">分布图表</span>
                      </label>
                    </div>
                  </div>
                )}

                {loadingStats ? (
                  <div className="text-center py-12">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                    <p className="mt-4 text-gray-600">正在加载统计数据...</p>
                  </div>
                ) : allLinks.length === 0 ? (
                  <div className="text-center py-12 text-gray-500">
                    <p>暂无数据，无法进行统计分析</p>
                  </div>
                ) : (
                  <div className="space-y-6" ref={analysisRef}>
                    {(linkTypeDef.properties || [])
                      .filter(prop => visibleProperties.has(prop.name))
                      .map((prop) => (
                      <PropertyStatistics
                        key={prop.name}
                        property={prop}
                        instances={linksAsInstances}
                        displayOptions={analysisDisplayOptions}
                      />
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {viewMode === 'list' && linkTypeDef && (
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-4 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      Source ID
                    </th>
                    <th className="px-6 py-4 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      Target ID
                    </th>
                    {(linkTypeDef.properties || []).map((prop) => (
                      <th
                        key={prop.name}
                        className="px-6 py-4 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider"
                      >
                        {prop.name}
                      </th>
                    ))}
                    <th className="px-6 py-4 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      Created At
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {links.length === 0 ? (
                    <tr>
                      <td
                        colSpan={(linkTypeDef.properties || []).length + 3}
                        className="px-6 py-12 text-center text-gray-500"
                      >
                        No links found.
                      </td>
                    </tr>
                  ) : (
                    links.map((link) => (
                      <tr key={link.id} className="hover:bg-gray-50 transition-colors">
                        <td className="px-6 py-4 whitespace-nowrap">
                          <RouterLink
                            to={`/instances/${linkTypeDef.source_type}/${link.source_id}`}
                            className="text-sm font-mono text-blue-600 hover:text-blue-800 hover:underline"
                          >
                            {link.source_id.substring(0, 8)}...
                          </RouterLink>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <RouterLink
                            to={`/instances/${linkTypeDef.target_type}/${link.target_id}`}
                            className="text-sm font-mono text-blue-600 hover:text-blue-800 hover:underline"
                          >
                            {link.target_id.substring(0, 8)}...
                          </RouterLink>
                        </td>
                        {(linkTypeDef.properties || []).map((prop) => (
                          <td key={prop.name} className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                            {link[prop.name] !== null && link[prop.name] !== undefined
                              ? String(link[prop.name])
                              : '-'}
                          </td>
                        ))}
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                          {link.created_at
                            ? new Date(link.created_at).toLocaleString()
                            : '-'}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            {total > limit && (
              <div className="bg-gray-50 px-6 py-4 flex items-center justify-between border-t border-gray-200">
                <div className="text-sm text-gray-700">
                  Showing {offset + 1} to {Math.min(offset + limit, total)} of {total} links
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => setOffset(Math.max(0, offset - limit))}
                    disabled={offset === 0}
                    className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  >
                    Previous
                  </button>
                  <button
                    onClick={() => setOffset(offset + limit)}
                    disabled={offset + limit >= total}
                    className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      <ToastContainer toasts={toasts} onClose={removeToast} />
    </div>
  );
}