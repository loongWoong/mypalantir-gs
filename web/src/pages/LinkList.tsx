import { useEffect, useState, useRef } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import type { Link, LinkType, Instance, ObjectType } from '../api/client';
import { linkApi, schemaApi } from '../api/client';
import { 
  ArrowPathIcon, 
  ArrowTopRightOnSquareIcon, 
  ChartBarIcon, 
  TableCellsIcon, 
  ArrowDownTrayIcon, 
  Cog6ToothIcon, 
  FunnelIcon,
  LinkIcon,
  Square2StackIcon,
  CircleStackIcon,
  InformationCircleIcon
} from '@heroicons/react/24/outline';
import { ToastContainer, useToast } from '../components/Toast';
import ButtonGroup from '../components/ButtonGroup';
import PropertyStatistics, { type AnalysisDisplayOptions } from '../components/PropertyStatistics';
import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';

export default function LinkList() {
  const { linkType } = useParams<{ linkType: string }>();
  const [links, setLinks] = useState<Link[]>([]);
  const [linkTypeDef, setLinkTypeDef] = useState<LinkType | null>(null);
  const [sourceObjectType, setSourceObjectType] = useState<ObjectType | null>(null);
  const [targetObjectType, setTargetObjectType] = useState<ObjectType | null>(null);
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
    
    // Load source and target object types for mapping visualization
    const loadObjectTypes = async () => {
      if (linkTypeDef) {
        try {
          const [source, target] = await Promise.all([
            schemaApi.getObjectType(linkTypeDef.source_type),
            schemaApi.getObjectType(linkTypeDef.target_type)
          ]);
          setSourceObjectType(source);
          setTargetObjectType(target);
        } catch (error) {
          console.error("Failed to load object types", error);
        }
      }
    };
    loadObjectTypes();
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
        backgroundColor: '#f8fafc', // match bg-slate-50
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
    return (
      <div className="flex items-center justify-center h-screen bg-slate-50">
        <div className="flex flex-col items-center gap-4">
          <div className="w-8 h-8 border-4 border-blue-500/30 border-t-blue-600 rounded-full animate-spin"></div>
          <p className="text-slate-500 text-sm font-medium">Loading analysis...</p>
        </div>
      </div>
    );
  }

  if (!linkTypeDef && !loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-slate-50">
        <div className="text-center">
          <h2 className="text-xl font-bold text-slate-900">Link type not found</h2>
          <p className="text-slate-500 mt-2">The requested link type does not exist.</p>
        </div>
      </div>
    );
  }

  // Helper to cast Link to Instance for PropertyStatistics
  const linksAsInstances = allLinks as unknown as Instance[];

  return (
    <div className="flex flex-col h-screen overflow-hidden bg-slate-50">
      {/* Top Header & Link Info */}
      <div className="flex-shrink-0 sticky top-0 z-20 bg-white/80 backdrop-blur-md border-b border-slate-200 shadow-sm">
        <div className="max-w-7xl mx-auto px-6 py-4">
            {/* Header Title & Actions */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                    <div className="p-2 bg-blue-50 rounded-lg text-blue-600">
                      <LinkIcon className="w-6 h-6" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <h1 className="text-xl font-bold text-slate-900 tracking-tight">{linkTypeDef?.display_name || linkTypeDef?.name}</h1>
                        {linkTypeDef?.url && (
                            <a href={linkTypeDef.url} target="_blank" rel="noopener noreferrer" className="text-slate-400 hover:text-blue-600 transition-colors" title="查看外部处理系统">
                                <ArrowTopRightOnSquareIcon className="w-4 h-4" />
                            </a>
                        )}
                      </div>
                      <p className="text-sm text-slate-500 mt-0.5 max-w-xl truncate">{linkTypeDef?.description || 'No description available'}</p>
                    </div>
                </div>
                <div className="flex items-center gap-3">
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
                            className="flex items-center px-4 py-2 bg-slate-900 text-white rounded-lg hover:bg-slate-800 transition-all disabled:opacity-50 disabled:cursor-not-allowed shadow-sm hover:shadow active:scale-95"
                        >
                            <ArrowPathIcon className={`w-4 h-4 mr-2 ${syncing ? 'animate-spin' : ''}`} />
                            {syncing ? '同步中...' : '同步关系'}
                        </button>
                    )}
                </div>
            </div>

            {/* Visual Relation Diagram & Property Mappings */}
            {linkTypeDef && (
                <div className="mt-6 space-y-6">
                    {/* Relation Diagram */}
                    <div className="p-4 bg-slate-50/50 rounded-xl border border-slate-200/60">
                        <div className="flex items-center justify-center gap-8">
                            {/* Source */}
                            <div className="flex items-center gap-3 px-4 py-2 bg-white rounded-lg border border-slate-200 shadow-sm">
                                <div className="w-2 h-2 rounded-full bg-blue-500"></div>
                                <div className="font-mono font-semibold text-sm text-slate-700">
                                    {linkTypeDef.source_type}
                                </div>
                                <span className="text-[10px] uppercase tracking-wider text-slate-400 font-bold">Source</span>
                            </div>

                            {/* Connection Line */}
                            <div className="flex items-center gap-2 flex-1 max-w-[200px] relative">
                                <div className="h-px bg-slate-300 w-full relative">
                                    <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 px-2 bg-slate-50 text-[10px] text-slate-500 font-medium whitespace-nowrap">
                                        {linkTypeDef.cardinality} • {linkTypeDef.direction === 'undirected' ? '无向' : '有向'}
                                    </div>
                                </div>
                                {linkTypeDef.direction !== 'undirected' && (
                                    <ArrowTopRightOnSquareIcon className="w-4 h-4 text-slate-400 rotate-45" />
                                )}
                            </div>

                            {/* Target */}
                            <div className="flex items-center gap-3 px-4 py-2 bg-white rounded-lg border border-slate-200 shadow-sm">
                                <span className="text-[10px] uppercase tracking-wider text-slate-400 font-bold">Target</span>
                                <div className="font-mono font-semibold text-sm text-slate-700">
                                    {linkTypeDef.target_type}
                                </div>
                                <div className="w-2 h-2 rounded-full bg-emerald-500"></div>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-7xl mx-auto px-6 py-8">
          {viewMode === 'properties' && linkTypeDef && (
            <div className="space-y-8">
              {/* Property Mappings Detail */}
              {linkTypeDef.property_mappings && Object.keys(linkTypeDef.property_mappings).length > 0 && sourceObjectType && targetObjectType && (
                <div className="bg-amber-50 rounded-xl p-5 border border-amber-200 shadow-sm animate-in slide-in-from-top-2 duration-300">
                    <div className="text-sm font-bold text-amber-900 mb-4 flex items-center">
                        <InformationCircleIcon className="w-5 h-5 mr-2" />
                        属性映射规则（所有规则必须同时满足）
                    </div>
                    <div className="space-y-3">
                        {Object.entries(linkTypeDef.property_mappings).map(([sourcePropName, targetPropName], idx) => {
                            const sourceProp = sourceObjectType.properties.find(p => p.name === sourcePropName);
                            const targetProp = targetObjectType.properties.find(p => p.name === targetPropName);
                            
                            // Try to get display names from object type definition or use property name
                            // Note: Property interface in client.ts doesn't have display_name yet, assuming it might be added or we use description/name
                            // If Property interface is updated to include display_name, we can use it here.
                            // For now, we will check if description acts as a display name or just use name
                            
                            return (
                                <div 
                                    key={idx} 
                                    className="bg-white rounded-lg p-4 border border-amber-200 flex items-center justify-between shadow-sm hover:shadow-md transition-all"
                                >
                                    <div className="flex items-center justify-center gap-4 flex-1">
                                        {/* Source Property */}
                                        <div className="flex flex-col items-end min-w-[200px]">
                                            <div className="bg-blue-50 rounded px-3 py-2 border border-blue-100 w-full">
                                                <div className="flex items-center justify-between gap-2 mb-1">
                                                    <span className="text-xs font-semibold text-blue-900">{linkTypeDef.source_type}</span>
                                                    <span className="text-[10px] text-blue-400 font-mono bg-white px-1.5 py-0.5 rounded border border-blue-100">{sourceProp?.data_type || 'unknown'}</span>
                                                </div>
                                                <div className="flex items-baseline justify-end gap-2">
                                                    {(sourceProp?.description) && (
                                                        <span className="text-xs text-blue-600 font-medium truncate" title={sourceProp.description}>
                                                            {sourceProp.description}
                                                        </span>
                                                    )}
                                                    <span className="text-xs text-blue-800 font-mono font-bold">.{sourcePropName}</span>
                                                </div>
                                            </div>
                                        </div>

                                        <div className="text-amber-400 font-bold text-lg px-2">=</div>

                                        {/* Target Property */}
                                        <div className="flex flex-col items-start min-w-[200px]">
                                            <div className="bg-emerald-50 rounded px-3 py-2 border border-emerald-100 w-full">
                                                <div className="flex items-center justify-between gap-2 mb-1">
                                                    <span className="text-xs font-semibold text-emerald-900">{linkTypeDef.target_type}</span>
                                                    <span className="text-[10px] text-emerald-400 font-mono bg-white px-1.5 py-0.5 rounded border border-emerald-100">{targetProp?.data_type || 'unknown'}</span>
                                                </div>
                                                <div className="flex items-baseline justify-start gap-2">
                                                    <span className="text-xs text-emerald-800 font-mono font-bold">.{targetPropName}</span>
                                                    {(targetProp?.description) && (
                                                        <span className="text-xs text-emerald-600 font-medium truncate" title={targetProp.description}>
                                                            {targetProp.description}
                                                        </span>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="ml-4 text-xs text-slate-400 font-medium bg-slate-50 px-2 py-1 rounded border border-slate-100 self-center">
                                        #{idx + 1}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
              )}

              {/* Stats Cards */}
              {stats && (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                  {/* Source Stats */}
                  <div className="bg-white p-5 rounded-xl shadow-sm border border-slate-200 hover:shadow-md transition-shadow group">
                    <div className="flex justify-between items-start mb-4">
                      <div className="p-2 bg-blue-50 rounded-lg text-blue-600 group-hover:bg-blue-100 transition-colors">
                        <Square2StackIcon className="w-5 h-5" />
                      </div>
                      <span className="px-2 py-1 bg-slate-100 text-slate-500 text-[10px] font-medium rounded-full uppercase tracking-wide">Source</span>
                    </div>
                    <div className="space-y-1">
                      <div className="text-3xl font-bold text-slate-900 tracking-tight">{stats.source_count.toLocaleString()}</div>
                      <div className="text-sm text-slate-500 font-medium">源对象数量</div>
                    </div>
                    <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between">
                      <div className="text-xs text-slate-400">覆盖率</div>
                      <div className="flex items-center gap-2">
                        <div className="w-16 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                          <div className="h-full bg-blue-500 rounded-full" style={{ width: `${stats.source_coverage * 100}%` }}></div>
                        </div>
                        <span className="text-xs font-bold text-slate-700">{(stats.source_coverage * 100).toFixed(1)}%</span>
                      </div>
                    </div>
                  </div>

                  {/* Target Stats */}
                  <div className="bg-white p-5 rounded-xl shadow-sm border border-slate-200 hover:shadow-md transition-shadow group">
                    <div className="flex justify-between items-start mb-4">
                      <div className="p-2 bg-emerald-50 rounded-lg text-emerald-600 group-hover:bg-emerald-100 transition-colors">
                        <Square2StackIcon className="w-5 h-5" />
                      </div>
                      <span className="px-2 py-1 bg-slate-100 text-slate-500 text-[10px] font-medium rounded-full uppercase tracking-wide">Target</span>
                    </div>
                    <div className="space-y-1">
                      <div className="text-3xl font-bold text-slate-900 tracking-tight">{stats.target_count.toLocaleString()}</div>
                      <div className="text-sm text-slate-500 font-medium">目标对象数量</div>
                    </div>
                    <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between">
                      <div className="text-xs text-slate-400">覆盖率</div>
                      <div className="flex items-center gap-2">
                        <div className="w-16 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                          <div className="h-full bg-emerald-500 rounded-full" style={{ width: `${stats.target_coverage * 100}%` }}></div>
                        </div>
                        <span className="text-xs font-bold text-slate-700">{(stats.target_coverage * 100).toFixed(1)}%</span>
                      </div>
                    </div>
                  </div>

                  {/* Link Stats */}
                  <div className="bg-white p-5 rounded-xl shadow-sm border border-slate-200 hover:shadow-md transition-shadow group md:col-span-2 lg:col-span-2">
                    <div className="flex justify-between items-start mb-4">
                      <div className="p-2 bg-indigo-50 rounded-lg text-indigo-600 group-hover:bg-indigo-100 transition-colors">
                        <CircleStackIcon className="w-5 h-5" />
                      </div>
                      <span className="px-2 py-1 bg-indigo-50 text-indigo-600 text-[10px] font-medium rounded-full uppercase tracking-wide">Total Links</span>
                    </div>
                    <div className="flex flex-col sm:flex-row sm:items-end gap-6">
                        <div className="space-y-1">
                            <div className="text-4xl font-bold text-indigo-600 tracking-tight">{stats.link_count.toLocaleString()}</div>
                            <div className="text-sm text-slate-500 font-medium">已建立关系总数</div>
                        </div>
                        
                        {/* Additional Metrics */}
                        <div className="flex-1 grid grid-cols-2 gap-4 pb-1">
                           <div className="px-3 py-2 bg-indigo-50/50 rounded-lg border border-indigo-100">
                              <div className="text-[10px] uppercase text-indigo-400 font-semibold tracking-wider mb-0.5">平均关联</div>
                              <div className="flex items-baseline gap-1">
                                <span className="text-lg font-bold text-indigo-700">{(stats.link_count / (stats.source_count || 1)).toFixed(2)}</span>
                                <span className="text-xs text-indigo-400">/源对象</span>
                              </div>
                           </div>
                           <div className="px-3 py-2 bg-indigo-50/50 rounded-lg border border-indigo-100">
                              <div className="text-[10px] uppercase text-indigo-400 font-semibold tracking-wider mb-0.5">孤立对象</div>
                              <div className="flex items-baseline gap-1">
                                <span className="text-lg font-bold text-indigo-700">{(100 - stats.source_coverage * 100).toFixed(1)}%</span>
                                <span className="text-xs text-indigo-400">无关联</span>
                              </div>
                           </div>
                        </div>
                    </div>
                    <div className="mt-4 pt-3 border-t border-slate-100 text-xs text-slate-400 flex justify-between items-center">
                        <span>{linkTypeDef.direction === 'undirected' ? '双向关联' : '单向关联'} • {linkTypeDef.cardinality}</span>
                        <span className="font-mono text-[10px] bg-slate-100 px-1.5 py-0.5 rounded">ID: {linkTypeDef.name}</span>
                    </div>
                  </div>
                </div>
              )}

              {/* 属性统计分析 */}
              {(linkTypeDef.properties || []).length > 0 && (
                <div className="space-y-6">
                  {/* Toolbar */}
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-slate-100 rounded-lg text-slate-500">
                            <ChartBarIcon className="w-5 h-5" />
                        </div>
                        <div>
                            <h2 className="text-lg font-bold text-slate-900">属性分析</h2>
                            <p className="text-xs text-slate-500">分析链接属性分布与统计数据</p>
                        </div>
                    </div>
                    
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => setShowPropertyFilter(!showPropertyFilter)}
                        className={`flex items-center px-3 py-2 text-sm font-medium rounded-lg transition-all ${
                          showPropertyFilter 
                            ? 'bg-blue-50 text-blue-600 ring-1 ring-blue-200' 
                            : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50 border border-transparent hover:border-slate-200'
                        }`}
                      >
                        <FunnelIcon className="w-4 h-4 mr-2" />
                        筛选
                      </button>
                      <button
                        onClick={() => setShowAnalysisSettings(!showAnalysisSettings)}
                        className={`flex items-center px-3 py-2 text-sm font-medium rounded-lg transition-all ${
                          showAnalysisSettings 
                            ? 'bg-slate-100 text-slate-900 ring-1 ring-slate-200' 
                            : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50 border border-transparent hover:border-slate-200'
                        }`}
                      >
                        <Cog6ToothIcon className="w-4 h-4 mr-2" />
                        设置
                      </button>
                      <div className="h-6 w-px bg-slate-200 mx-1"></div>
                      <div className="relative">
                        <button
                          onClick={() => setShowExportMenu(!showExportMenu)}
                          className={`flex items-center px-3 py-2 text-sm font-medium rounded-lg transition-all ${
                            showExportMenu 
                              ? 'bg-slate-100 text-slate-900 ring-1 ring-slate-200' 
                              : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50 border border-transparent hover:border-slate-200'
                          }`}
                        >
                          <ArrowDownTrayIcon className="w-4 h-4 mr-2" />
                          导出
                        </button>
                        {showExportMenu && (
                          <div className="absolute right-0 mt-2 w-40 bg-white rounded-xl shadow-xl border border-slate-100 z-30 py-1 overflow-hidden animate-in fade-in zoom-in duration-200 origin-top-right">
                            <div className="px-3 py-2 text-xs font-semibold text-slate-400 uppercase tracking-wider bg-slate-50 border-b border-slate-100">
                                导出格式
                            </div>
                            <button
                              onClick={() => handleExport('png')}
                              className="w-full text-left px-4 py-2.5 text-sm text-slate-700 hover:bg-blue-50 hover:text-blue-700 flex items-center transition-colors"
                            >
                              <span className="font-mono w-10">PNG</span>
                              <span className="text-xs text-slate-400">高清晰度图片</span>
                            </button>
                            <button
                              onClick={() => handleExport('pdf')}
                              className="w-full text-left px-4 py-2.5 text-sm text-slate-700 hover:bg-blue-50 hover:text-blue-700 flex items-center transition-colors"
                            >
                              <span className="font-mono w-10">PDF</span>
                              <span className="text-xs text-slate-400">打印文档</span>
                            </button>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* 属性筛选面板 */}
                  {showPropertyFilter && (
                    <div className="p-5 bg-white rounded-xl border border-slate-200 shadow-sm animate-in slide-in-from-top-2 duration-200">
                      <div className="flex justify-between items-center mb-4">
                        <h3 className="text-sm font-semibold text-slate-900">选择显示的属性</h3>
                        <div className="flex gap-3 text-xs">
                          <button 
                            onClick={() => setVisibleProperties(new Set((linkTypeDef.properties || []).map(p => p.name)))}
                            className="text-blue-600 hover:text-blue-700 font-medium transition-colors"
                          >
                            全选
                          </button>
                          <span className="text-slate-300">|</span>
                          <button 
                            onClick={() => setVisibleProperties(new Set())}
                            className="text-slate-500 hover:text-slate-700 transition-colors"
                          >
                            清空
                          </button>
                        </div>
                      </div>
                      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
                        {(linkTypeDef.properties || []).map(prop => (
                          <label key={prop.name} className="flex items-center cursor-pointer p-2.5 rounded-lg border border-slate-200 hover:border-blue-300 hover:bg-blue-50/30 transition-all">
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
                              className="w-4 h-4 text-blue-600 rounded border-slate-300 focus:ring-blue-500 transition-colors"
                            />
                            <span className="ml-2.5 text-sm text-slate-700 truncate select-none font-medium" title={prop.name}>{prop.name}</span>
                          </label>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* 分析设置面板 */}
                  {showAnalysisSettings && (
                    <div className="p-5 bg-white rounded-xl border border-slate-200 shadow-sm animate-in slide-in-from-top-2 duration-200">
                      <h3 className="text-sm font-semibold text-slate-900 mb-4">显示选项</h3>
                      <div className="flex flex-wrap gap-6">
                        <label className="flex items-center cursor-pointer group">
                          <div className="relative flex items-center">
                            <input
                              type="checkbox"
                              checked={analysisDisplayOptions.showSummary}
                              onChange={(e) => setAnalysisDisplayOptions({
                                ...analysisDisplayOptions,
                                showSummary: e.target.checked,
                              })}
                              className="peer w-4 h-4 text-blue-600 rounded border-slate-300 focus:ring-blue-500"
                            />
                          </div>
                          <span className="ml-2.5 text-sm text-slate-700 group-hover:text-slate-900 transition-colors">统计摘要</span>
                        </label>
                        <label className="flex items-center cursor-pointer group">
                          <input
                            type="checkbox"
                            checked={analysisDisplayOptions.showNumericStats}
                            onChange={(e) => setAnalysisDisplayOptions({
                              ...analysisDisplayOptions,
                              showNumericStats: e.target.checked,
                            })}
                            className="w-4 h-4 text-blue-600 rounded border-slate-300 focus:ring-blue-500"
                          />
                          <span className="ml-2.5 text-sm text-slate-700 group-hover:text-slate-900 transition-colors">数值统计</span>
                        </label>
                        <label className="flex items-center cursor-pointer group">
                          <input
                            type="checkbox"
                            checked={analysisDisplayOptions.showChart}
                            onChange={(e) => setAnalysisDisplayOptions({
                              ...analysisDisplayOptions,
                              showChart: e.target.checked,
                            })}
                            className="w-4 h-4 text-blue-600 rounded border-slate-300 focus:ring-blue-500"
                          />
                          <span className="ml-2.5 text-sm text-slate-700 group-hover:text-slate-900 transition-colors">分布图表</span>
                        </label>
                      </div>
                    </div>
                  )}

                  {loadingStats ? (
                    <div className="text-center py-20 bg-white rounded-xl border border-slate-200 border-dashed">
                      <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500/30 border-t-blue-600"></div>
                      <p className="mt-4 text-slate-500 font-medium">正在计算属性统计...</p>
                    </div>
                  ) : allLinks.length === 0 ? (
                    <div className="text-center py-20 bg-white rounded-xl border border-slate-200 border-dashed">
                      <p className="text-slate-400">暂无数据，无法进行统计分析</p>
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
            <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-slate-200">
                  <thead className="bg-slate-50">
                    <tr>
                      <th className="px-6 py-4 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                        Source ID
                      </th>
                      <th className="px-6 py-4 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                        Target ID
                      </th>
                      {(linkTypeDef.properties || []).map((prop) => (
                        <th
                          key={prop.name}
                          className="px-6 py-4 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider"
                        >
                          {prop.name}
                        </th>
                      ))}
                      <th className="px-6 py-4 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                        Created At
                      </th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-slate-100">
                    {links.length === 0 ? (
                      <tr>
                        <td
                          colSpan={(linkTypeDef.properties || []).length + 3}
                          className="px-6 py-12 text-center text-slate-500"
                        >
                          No links found.
                        </td>
                      </tr>
                    ) : (
                      links.map((link) => (
                        <tr key={link.id} className="hover:bg-slate-50 transition-colors group">
                          <td className="px-6 py-4 whitespace-nowrap">
                            <RouterLink
                              to={`/instances/${linkTypeDef.source_type}/${link.source_id}`}
                              className="inline-flex items-center px-2.5 py-0.5 rounded border border-blue-100 bg-blue-50 text-blue-700 text-xs font-mono hover:bg-blue-100 transition-colors"
                            >
                              {link.source_id.length > 12 ? link.source_id.substring(0, 12) + '...' : link.source_id}
                            </RouterLink>
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap">
                            <RouterLink
                              to={`/instances/${linkTypeDef.target_type}/${link.target_id}`}
                              className="inline-flex items-center px-2.5 py-0.5 rounded border border-emerald-100 bg-emerald-50 text-emerald-700 text-xs font-mono hover:bg-emerald-100 transition-colors"
                            >
                              {link.target_id.length > 12 ? link.target_id.substring(0, 12) + '...' : link.target_id}
                            </RouterLink>
                          </td>
                          {(linkTypeDef.properties || []).map((prop) => (
                            <td key={prop.name} className="px-6 py-4 whitespace-nowrap text-sm text-slate-700 font-mono">
                              {link[prop.name] !== null && link[prop.name] !== undefined
                                ? String(link[prop.name])
                                : <span className="text-slate-300">-</span>}
                            </td>
                          ))}
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-500">
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
                <div className="bg-white px-6 py-4 flex items-center justify-between border-t border-slate-200">
                  <div className="text-sm text-slate-500">
                    Showing <span className="font-medium text-slate-900">{offset + 1}</span> to <span className="font-medium text-slate-900">{Math.min(offset + limit, total)}</span> of <span className="font-medium text-slate-900">{total}</span> links
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => setOffset(Math.max(0, offset - limit))}
                      disabled={offset === 0}
                      className="px-4 py-2 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-lg hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-sm"
                    >
                      Previous
                    </button>
                    <button
                      onClick={() => setOffset(offset + limit)}
                      disabled={offset + limit >= total}
                      className="px-4 py-2 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-lg hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-sm"
                    >
                      Next
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      <ToastContainer toasts={toasts} onClose={removeToast} />
    </div>
  );
}
