import { useEffect, useState, useCallback, useRef } from 'react';
import { useParams } from 'react-router-dom';
import ForceGraph2D from 'react-force-graph-2d';
import type { Instance, Link as LinkInstance } from '../api/client';
import { instanceApi, linkApi, schemaApi } from '../api/client';
import { useWorkspace } from '../WorkspaceContext';
import { 
  ArrowPathIcon,
  CircleStackIcon,
  Cog6ToothIcon,
  ArrowRightIcon,
  ArrowLeftIcon,
  ArrowsRightLeftIcon,
  Squares2X2Icon,
  LinkIcon,
  SparklesIcon,
  TableCellsIcon,
  ArrowsPointingOutIcon,
  ArrowsPointingInIcon,
  EllipsisVerticalIcon
} from '@heroicons/react/24/outline';

interface GraphNode {
  id: string;  // 使用 type:id 作为唯一标识
  name: string;
  type: string;
  data: Instance;
  group?: number;
}

interface GraphLink {
  source: string;  // 使用 type:id 格式
  target: string;  // 使用 type:id 格式
  id: string;
  type: string;
  data: LinkInstance;
}

// 辅助函数：生成节点的唯一标识符
const getNodeKey = (type: string, id: string): string => {
  return `${type}:${id}`;
};

export default function GraphView() {
  const { objectType, instanceId } = useParams<{ objectType?: string; instanceId?: string }>();
  const { selectedWorkspace } = useWorkspace();
  const [nodes, setNodes] = useState<GraphNode[]>([]);
  const [links, setLinks] = useState<GraphLink[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
  const [graphData, setGraphData] = useState({ nodes, links });
  const [objectTypes, setObjectTypes] = useState<any[]>([]);
  const [linkTypes, setLinkTypes] = useState<any[]>([]);
  
  // 分层视图高亮状态：跟踪被点击节点和后续节点
  const [highlightedNodes, setHighlightedNodes] = useState<Set<string>>(new Set());
  const [highlightedLinks, setHighlightedLinks] = useState<Set<string>>(new Set());
  // 反向依赖高亮状态：跟踪双击节点的前驱节点
  const [reverseHighlightedNodes, setReverseHighlightedNodes] = useState<Set<string>>(new Set());
  const [reverseHighlightedLinks, setReverseHighlightedLinks] = useState<Set<string>>(new Set());
  // 用于检测双击的ref（避免双击时触发单击事件）
  const lastClickTimeRef = useRef<number>(0);
  const clickTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // 类型详情对话框状态
  const [typeDetailDialog, setTypeDetailDialog] = useState<{ type: string; layer: number; nodes: GraphNode[] } | null>(null);
  // 类型详情对话框视图模式：'card' | 'table'
  const [typeDetailViewMode, setTypeDetailViewMode] = useState<'card' | 'table'>('card');
  // 类型详情抽屉宽度（自适应）
  const [typeDetailDrawerWidth, setTypeDetailDrawerWidth] = useState<number>(884); // 默认 w-96 = 384px
  const typeDetailContentRef = useRef<HTMLDivElement>(null);
  
  // 图例面板拖动状态（使用 top 定位，更直观）
  const [legendPosition, setLegendPosition] = useState<{ x: number; y: number }>(() => {
    const saved = localStorage.getItem('graphView_legendPosition');
    if (saved) {
      try {
        const pos = JSON.parse(saved);
        // 如果是从旧版本（bottom定位）迁移，需要转换
        // 这里假设旧版本保存的是 bottom 值，需要转换为 top
        return pos;
      } catch {
        return { x: 16, y: 48 }; // 默认位置：left-4 (16px), top-12 (48px)
      }
    }
    return { x: 16, y: 48 }; // 默认位置
  });
  const [isDraggingLegend, setIsDraggingLegend] = useState(false);
  const [dragOffset, setDragOffset] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const legendRef = useRef<HTMLDivElement>(null);
  
  // 全屏状态
  const [isFullscreen, setIsFullscreen] = useState(false);
  const fullscreenContainerRef = useRef<HTMLDivElement>(null);
  const [fullscreenZoom, setFullscreenZoom] = useState(1);
  const [fullscreenPan, setFullscreenPan] = useState({ x: 0, y: 0 });
  const [isPanning, setIsPanning] = useState(false);
  const panStartRef = useRef<{ x: number; y: number } | null>(null);
  
  // 类型折叠状态（用于分层视图）
  const [collapsedTypes, setCollapsedTypes] = useState<Set<string>>(new Set());
  
  // 图例折叠状态
  const [isLegendCollapsed, setIsLegendCollapsed] = useState(false);

  // 处理图例面板拖动
  const handleLegendMouseDown = useCallback((e: React.MouseEvent) => {
    if (!legendRef.current) return;
    const rect = legendRef.current.getBoundingClientRect();
    setDragOffset({
      x: e.clientX - rect.left,
      y: e.clientY - rect.top
    });
    setIsDraggingLegend(true);
    e.preventDefault();
  }, []);

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isDraggingLegend || !legendRef.current) return;
      
      const container = legendRef.current.parentElement;
      if (!container) return;
      
      const containerRect = container.getBoundingClientRect();
      const legendRect = legendRef.current.getBoundingClientRect();
      
      // 计算新位置（相对于容器的位置，使用 top 定位）
      let newX = e.clientX - containerRect.left - dragOffset.x;
      let newY = e.clientY - containerRect.top - dragOffset.y;
      
      // 限制在容器范围内
      const maxX = containerRect.width - legendRect.width;
      const maxY = containerRect.height - legendRect.height;
      
      newX = Math.max(0, Math.min(newX, maxX));
      newY = Math.max(0, Math.min(newY, maxY));
      
      const newPosition = { x: newX, y: newY };
      setLegendPosition(newPosition);
      // 保存位置到localStorage
      localStorage.setItem('graphView_legendPosition', JSON.stringify(newPosition));
    };

    const handleMouseUp = () => {
      if (isDraggingLegend) {
        setIsDraggingLegend(false);
      }
    };

    if (isDraggingLegend) {
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = 'grabbing';
      document.body.style.userSelect = 'none';
    }

    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [isDraggingLegend, dragOffset, legendPosition]);

  // 全屏事件监听
  useEffect(() => {
    const handleFullscreenChange = () => {
      const isCurrentlyFullscreen = !!(
        document.fullscreenElement ||
        (document as any).webkitFullscreenElement ||
        (document as any).msFullscreenElement
      );
      setIsFullscreen(isCurrentlyFullscreen);
      if (!isCurrentlyFullscreen) {
        setFullscreenZoom(1);
        setFullscreenPan({ x: 0, y: 0 });
      }
    };

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    document.addEventListener('webkitfullscreenchange', handleFullscreenChange);
    document.addEventListener('msfullscreenchange', handleFullscreenChange);

    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange);
      document.removeEventListener('webkitfullscreenchange', handleFullscreenChange);
      document.removeEventListener('msfullscreenchange', handleFullscreenChange);
    };
  }, []);

  // 全屏模式下的缩放和拖动处理
  useEffect(() => {
    if (!isFullscreen) return;

    const container = fullscreenContainerRef.current;
    if (!container) return;

    // 鼠标滚轮缩放
    const handleWheel = (e: WheelEvent) => {
      const target = e.target as HTMLElement;
      if (target?.tagName === 'CANVAS' || container.contains(target)) {
        e.preventDefault();
        const delta = e.deltaY > 0 ? 0.9 : 1.1;
        setFullscreenZoom(prev => Math.max(0.1, Math.min(5, prev * delta)));
      }
    };

    // 鼠标拖动 - 使用中键或右键拖动，左键用于节点交互
    const handleMouseDown = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      // 中键或右键拖动
      if (target?.tagName === 'CANVAS' && (e.button === 1 || e.button === 2)) {
        e.preventDefault();
        setIsPanning(true);
        panStartRef.current = { x: e.clientX - fullscreenPan.x, y: e.clientY - fullscreenPan.y };
      }
    };

    const handleMouseMove = (e: MouseEvent) => {
      if (isPanning && panStartRef.current) {
        setFullscreenPan({
          x: e.clientX - panStartRef.current.x,
          y: e.clientY - panStartRef.current.y
        });
      }
    };

    const handleMouseUp = () => {
      setIsPanning(false);
      panStartRef.current = null;
    };

    // 阻止右键菜单
    const handleContextMenu = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (target?.tagName === 'CANVAS') {
        e.preventDefault();
      }
    };

    container.addEventListener('wheel', handleWheel, { passive: false });
    const canvas = container.querySelector('canvas');
    if (canvas) {
      canvas.addEventListener('mousedown', handleMouseDown);
      canvas.addEventListener('contextmenu', handleContextMenu);
    }
    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);

    return () => {
      container.removeEventListener('wheel', handleWheel);
      if (canvas) {
        canvas.removeEventListener('mousedown', handleMouseDown);
        canvas.removeEventListener('contextmenu', handleContextMenu);
      }
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isFullscreen, isPanning, fullscreenPan]);

  // 计算类型详情抽屉的自适应宽度
  useEffect(() => {
    if (!typeDetailDialog) {
      setTypeDetailDrawerWidth(884); // 默认宽度
      return;
    }

    const calculateWidth = () => {
      // 获取视口宽度
      const viewportWidth = window.innerWidth;
      const maxWidth = viewportWidth * 0.8; // 最大宽度为视口宽度的80%
      const minWidth = 884; // 最小宽度 w-96

      let contentWidth = 0;

      if (typeDetailViewMode === 'table') {
        // 表格视图：根据列数估算宽度
        const allKeys = new Set<string>();
        typeDetailDialog.nodes.forEach(node => {
          Object.keys(node.data).forEach(key => allKeys.add(key));
        });
        const otherKeys = Array.from(allKeys)
          .filter(key => !['id', 'name', 'created_at', 'updated_at'].includes(key));
        
        // 尝试从实际渲染的表格获取宽度
        const contentElement = typeDetailContentRef.current;
        if (contentElement) {
          const table = contentElement.querySelector('table');
          if (table) {
            // 临时移除宽度限制以测量自然宽度
            const originalMinWidth = table.style.minWidth;
            const originalWidth = table.style.width;
            const parent = table.parentElement;
            const originalParentOverflow = parent?.style.overflow;
            
            // 设置临时样式以测量
            table.style.minWidth = 'max-content';
            table.style.width = 'auto';
            if (parent) {
              parent.style.overflow = 'visible';
            }
            
            // 测量表格的自然宽度
            const tableWidth = table.scrollWidth;
            
            // 恢复原始样式
            table.style.minWidth = originalMinWidth;
            table.style.width = originalWidth;
            if (parent) {
              parent.style.overflow = originalParentOverflow || '';
            }
            
            if (tableWidth > 0) {
              contentWidth = tableWidth + 48; // 加上 padding (p-6 = 24px * 2)
            } else {
              // 如果测量失败，使用估算值
              const baseWidth = 4 * 100; // 固定列较窄
              const propertyWidth = otherKeys.length * 130; // 属性列稍宽
              const timeWidth = 2 * 150; // 时间列较宽
              contentWidth = baseWidth + propertyWidth + timeWidth + 48;
            }
          } else {
            // 如果表格还未渲染，根据列数估算
            const baseWidth = 4 * 100; // 固定列较窄
            const propertyWidth = otherKeys.length * 130; // 属性列稍宽
            const timeWidth = 2 * 150; // 时间列较宽
            contentWidth = baseWidth + propertyWidth + timeWidth + 48;
          }
        } else {
          // 如果内容元素还未渲染，根据列数估算
          const baseWidth = 4 * 100; // 固定列较窄
          const propertyWidth = otherKeys.length * 130; // 属性列稍宽
          const timeWidth = 2 * 150; // 时间列较宽
          contentWidth = baseWidth + propertyWidth + timeWidth + 48;
        }
      } else {
        // 卡片视图：使用默认宽度或稍大一些
        contentWidth = Math.max(minWidth, 500);
      }

      // 应用最小和最大宽度限制
      const finalWidth = Math.max(minWidth, Math.min(contentWidth, maxWidth));
      setTypeDetailDrawerWidth(finalWidth);
    };

    // 延迟计算，确保 DOM 已渲染
    const timeoutId1 = setTimeout(calculateWidth, 50);
    const timeoutId2 = setTimeout(calculateWidth, 200); // 二次延迟，确保表格完全渲染

    // 监听窗口大小变化
    window.addEventListener('resize', calculateWidth);

    return () => {
      clearTimeout(timeoutId1);
      clearTimeout(timeoutId2);
      window.removeEventListener('resize', calculateWidth);
    };
  }, [typeDetailDialog, typeDetailViewMode]);
  
  // 节点和关系上限配置（从 localStorage 读取或使用默认值）
  const [maxNodes, setMaxNodes] = useState<number>(() => {
    const saved = localStorage.getItem('graphView_maxNodes');
    return saved ? parseInt(saved, 10) : (selectedWorkspace ? 5000 : 1000);
  });
  const [maxLinksPerType, setMaxLinksPerType] = useState<number>(() => {
    const saved = localStorage.getItem('graphView_maxLinksPerType');
    return saved ? parseInt(saved, 10) : (selectedWorkspace ? 10000 : 500);
  });
  const [showSettings, setShowSettings] = useState(false);
  
  // 血缘查询模式：'forward' | 'backward' | 'full' | 'direct'
  const [lineageMode, setLineageMode] = useState<'forward' | 'backward' | 'full' | 'direct'>('direct');
  
  // 视图模式：'force' | 'hierarchical'
  const [viewMode, setViewMode] = useState<'force' | 'hierarchical'>(() => {
    const saved = localStorage.getItem('graphView_viewMode');
    return (saved === 'hierarchical' ? 'hierarchical' : 'force') as 'force' | 'hierarchical';
  });
  
  // 力导向图布局选项
  const [layoutType, setLayoutType] = useState<'force' | 'circle' | 'grid' | 'radial'>(() => {
    const saved = localStorage.getItem('graphView_layoutType');
    return (saved === 'circle' || saved === 'grid' || saved === 'radial' ? saved : 'force') as 'force' | 'circle' | 'grid' | 'radial';
  });
  
  // 力导向图参数
  const [forceParams, setForceParams] = useState(() => {
    const saved = localStorage.getItem('graphView_forceParams');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch {
        return { linkDistance: 90, chargeStrength: -300, centerStrength: 0.1, alphaDecay: 0.0228, velocityDecay: 0.4 };
      }
    }
    return { linkDistance: 90, chargeStrength: -300, centerStrength: 0.1, alphaDecay: 0.0228, velocityDecay: 0.4 };
  });
  
  // ForceGraph2D 引用
  const fgRef = useRef<any>(null);

  useEffect(() => {
    loadGraphData();
  }, [objectType, instanceId, selectedWorkspace, maxNodes, maxLinksPerType, lineageMode]);
  
  // 当工作空间变化时，更新默认上限值
  useEffect(() => {
    const isWorkspaceMode = selectedWorkspace && 
      ((selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0) ||
       (selectedWorkspace.link_types && selectedWorkspace.link_types.length > 0));
    
    // 如果用户没有自定义设置，则根据工作空间模式设置默认值
    if (!localStorage.getItem('graphView_maxNodes')) {
      setMaxNodes(isWorkspaceMode ? 5000 : 1000);
    }
    if (!localStorage.getItem('graphView_maxLinksPerType')) {
      setMaxLinksPerType(isWorkspaceMode ? 10000 : 500);
    }
  }, [selectedWorkspace]);

  const loadGraphData = async () => {
    try {
      setLoading(true);
      
      if (objectType && instanceId) {
        // 加载单个实例及其关系（根据血缘模式）
        await loadInstanceGraph(objectType, instanceId, lineageMode);
      } else {
        // 加载所有对象和关系
        await loadFullGraph();
      }
    } catch (error) {
      console.error('Failed to load graph data:', error);
    } finally {
      setLoading(false);
    }
  };

  // 递归查询正向血缘（从当前节点向后查询）
  const loadForwardLineage = async (
    objType: string,
    instId: string,
    nodeMap: Map<string, GraphNode>,
    linkList: GraphLink[],
    visitedNodes: Set<string>,
    linkTypes: any[],
    objectTypes: any[],
    maxDepth: number = 10,
    currentDepth: number = 0
  ) => {
    if (currentDepth >= maxDepth || visitedNodes.has(`${objType}:${instId}`)) {
      return;
    }
    
    visitedNodes.add(`${objType}:${instId}`);
    
    // 加载当前节点的出边（正向关系）- 只查询从当前节点指向的关系和节点
    for (const linkType of linkTypes) {
      if (linkType.source_type === objType) {
        try {
          // 明确指定 direction='outgoing'，查询从当前节点指向的关系
          const instanceLinks = await linkApi.getInstanceLinks(objType, instId, linkType.name, 'outgoing');
          for (const link of instanceLinks) {
            const targetId = link.target_id as string;
            const targetType = linkType.target_type;
            
            // 检查是否已访问过（避免重复查询）
            const nodeKey = `${targetType}:${targetId}`;
            if (visitedNodes.has(nodeKey)) {
              continue;
            }
            
            // 加载目标实例
            try {
              const targetInstance = await instanceApi.get(targetType, targetId);
              const targetNodeKey = getNodeKey(targetType, targetId);
              if (!nodeMap.has(targetNodeKey)) {
                nodeMap.set(targetNodeKey, {
                  id: targetNodeKey,  // 使用 type:id 作为唯一标识
                  name: getInstanceName(targetInstance, targetType),
                  type: targetType,
                  data: targetInstance,
                  group: getTypeGroup(targetType, objectTypes),
                });
              }
              
              // 添加关系
              const sourceNodeKey = getNodeKey(objType, instId);
              const linkId = `${link.id}_${sourceNodeKey}_${targetNodeKey}`;
              if (!linkList.some(l => l.id === linkId)) {
                linkList.push({
                  source: sourceNodeKey,  // 使用 type:id 格式
                  target: targetNodeKey,  // 使用 type:id 格式
                  id: linkId,  // 使用唯一的linkId而不是原始的link.id
                  type: linkType.name,
                  data: link,
                });
              }
              
              // 递归查询目标节点的正向血缘（继续向下追溯）
              await loadForwardLineage(
                targetType,
                targetId,
                nodeMap,
                linkList,
                visitedNodes,
                linkTypes,
                objectTypes,
                maxDepth,
                currentDepth + 1
              );
            } catch (err) {
              console.error(`Failed to load target instance ${targetId}:`, err);
            }
          }
        } catch (err) {
          console.error(`Failed to load forward links for ${linkType.name}:`, err);
        }
      }
    }
  };

  // 递归查询全链血缘（遍历与当前节点相关的全部节点和关系，包括出边和入边）
  const loadFullLineage = async (
    objType: string,
    instId: string,
    nodeMap: Map<string, GraphNode>,
    linkList: GraphLink[],
    visitedNodes: Set<string>,
    linkTypes: any[],
    objectTypes: any[],
    maxDepth: number = 10,
    currentDepth: number = 0
  ) => {
    if (currentDepth >= maxDepth || visitedNodes.has(`${objType}:${instId}`)) {
      return;
    }
    
    visitedNodes.add(`${objType}:${instId}`);
    
    // 同时查询出边和入边
    for (const linkType of linkTypes) {
      // 查询出边（正向关系）
      if (linkType.source_type === objType) {
        try {
          const instanceLinks = await linkApi.getInstanceLinks(objType, instId, linkType.name, 'outgoing');
          for (const link of instanceLinks) {
            const targetId = link.target_id as string;
            const targetType = linkType.target_type;
            
            try {
              const targetInstance = await instanceApi.get(targetType, targetId);
              const targetNodeKey = getNodeKey(targetType, targetId);
              if (!nodeMap.has(targetNodeKey)) {
                nodeMap.set(targetNodeKey, {
                  id: targetNodeKey,  // 使用 type:id 作为唯一标识
                  name: getInstanceName(targetInstance, targetType),
                  type: targetType,
                  data: targetInstance,
                  group: getTypeGroup(targetType, objectTypes),
                });
              }
              
              const sourceNodeKey = getNodeKey(objType, instId);
              const linkId = `${link.id}_${sourceNodeKey}_${targetNodeKey}`;
              if (!linkList.some(l => l.id === linkId)) {
                linkList.push({
                  source: sourceNodeKey,  // 使用 type:id 格式
                  target: targetNodeKey,  // 使用 type:id 格式
                  id: linkId,  // 使用唯一的linkId而不是原始的link.id
                  type: linkType.name,
                  data: link,
                });
              }
              
              // 递归查询目标节点的全链血缘
              await loadFullLineage(
                targetType,
                targetId,
                nodeMap,
                linkList,
                visitedNodes,
                linkTypes,
                objectTypes,
                maxDepth,
                currentDepth + 1
              );
            } catch (err) {
              console.error(`Failed to load target instance ${targetId}:`, err);
            }
          }
        } catch (err) {
          console.error(`Failed to load forward links for ${linkType.name}:`, err);
        }
      }
      
      // 查询入边（反向关系）
      if (linkType.target_type === objType) {
        try {
          const instanceLinks = await linkApi.getInstanceLinks(objType, instId, linkType.name, 'incoming');
          for (const link of instanceLinks) {
            const sourceId = link.source_id as string;
            const sourceType = linkType.source_type;
            
            const nodeKey = `${sourceType}:${sourceId}`;
            if (visitedNodes.has(nodeKey)) {
              continue;
            }
            
            try {
              const sourceInstance = await instanceApi.get(sourceType, sourceId);
              const sourceNodeKey = getNodeKey(sourceType, sourceId);
              if (!nodeMap.has(sourceNodeKey)) {
                nodeMap.set(sourceNodeKey, {
                  id: sourceNodeKey,  // 使用 type:id 作为唯一标识
                  name: getInstanceName(sourceInstance, sourceType),
                  type: sourceType,
                  data: sourceInstance,
                  group: getTypeGroup(sourceType, objectTypes),
                });
              }
              
              const targetNodeKey = getNodeKey(objType, instId);
              const linkId = `${link.id}_${sourceNodeKey}_${targetNodeKey}`;
              if (!linkList.some(l => l.id === linkId || (l.source === sourceNodeKey && l.target === targetNodeKey && l.type === linkType.name))) {
                linkList.push({
                  source: sourceNodeKey,  // 使用 type:id 格式
                  target: targetNodeKey,  // 使用 type:id 格式
                  id: linkId,  // 使用唯一的linkId而不是原始的link.id
                  type: linkType.name,
                  data: link,
                });
              }
              
              // 递归查询源节点的全链血缘
              await loadFullLineage(
                sourceType,
                sourceId,
                nodeMap,
                linkList,
                visitedNodes,
                linkTypes,
                objectTypes,
                maxDepth,
                currentDepth + 1
              );
            } catch (err) {
              console.error(`Failed to load source instance ${sourceId}:`, err);
            }
          }
        } catch (err) {
          console.error(`Failed to load backward links for ${linkType.name}:`, err);
        }
      }
    }
  };

  // 递归查询反向血缘（从当前节点向前查询）
  const loadBackwardLineage = async (
    objType: string,
    instId: string,
    nodeMap: Map<string, GraphNode>,
    linkList: GraphLink[],
    visitedNodes: Set<string>,
    linkTypes: any[],
    objectTypes: any[],
    maxDepth: number = 10,
    currentDepth: number = 0
  ) => {
    if (currentDepth >= maxDepth || visitedNodes.has(`${objType}:${instId}`)) {
      return;
    }
    
    visitedNodes.add(`${objType}:${instId}`);
    
    // 加载当前节点的入边（反向关系）- 只查询指向当前节点的关系和节点
    console.log(`[loadBackwardLineage] 开始查询节点 ${objType}:${instId} 的反向血缘，当前深度: ${currentDepth}`);
    console.log(`[loadBackwardLineage] 可用的关系类型数量: ${linkTypes.length}`);
    
    for (const linkType of linkTypes) {
      if (linkType.target_type === objType) {
        console.log(`[loadBackwardLineage] 检查关系类型: ${linkType.name} (source: ${linkType.source_type}, target: ${linkType.target_type})`);
        try {
          // 使用 getInstanceLinks API 查询入边关系，direction='incoming'
          console.log(`[loadBackwardLineage] 查询 ${objType}:${instId} 的 ${linkType.name} 入边关系...`);
          const instanceLinks = await linkApi.getInstanceLinks(objType, instId, linkType.name, 'incoming');
          console.log(`[loadBackwardLineage] 查询到 ${instanceLinks.length} 条 ${linkType.name} 关系:`, instanceLinks);
          
          for (const link of instanceLinks) {
            const sourceId = link.source_id as string;
            const sourceType = linkType.source_type;
            
            console.log(`[loadBackwardLineage] 处理关系: ${linkType.name}, source: ${sourceType}:${sourceId}, target: ${objType}:${instId}`);
            
            // 检查是否已访问过（避免重复查询）
            const nodeKey = `${sourceType}:${sourceId}`;
            if (visitedNodes.has(nodeKey)) {
              console.log(`[loadBackwardLineage] 节点 ${nodeKey} 已访问过，跳过`);
              continue;
            }
            
            // 加载源实例
            try {
              console.log(`[loadBackwardLineage] 加载源实例 ${sourceType}:${sourceId}...`);
              const sourceInstance = await instanceApi.get(sourceType, sourceId);
              console.log(`[loadBackwardLineage] 成功加载源实例 ${sourceType}:${sourceId}:`, sourceInstance);
              
              const sourceNodeKey = getNodeKey(sourceType, sourceId);
              if (!nodeMap.has(sourceNodeKey)) {
                const newNode = {
                  id: sourceNodeKey,  // 使用 type:id 作为唯一标识
                  name: getInstanceName(sourceInstance, sourceType),
                  type: sourceType,
                  data: sourceInstance,
                  group: getTypeGroup(sourceType, objectTypes),
                };
                nodeMap.set(sourceNodeKey, newNode);
                console.log(`[loadBackwardLineage] 添加新节点到图中:`, newNode);
              } else {
                const existingNode = nodeMap.get(sourceNodeKey);
                console.log(`[loadBackwardLineage] 节点 ${sourceNodeKey} 已存在于图中:`, {
                  existingNode: existingNode ? { id: existingNode.id, type: existingNode.type, name: existingNode.name } : null,
                  newSourceType: sourceType,
                  typesMatch: existingNode?.type === sourceType
                });
              }
              
              // 添加关系（使用唯一ID避免重复）
              const targetNodeKey = getNodeKey(objType, instId);
              const linkId = `${link.id}_${sourceNodeKey}_${targetNodeKey}`;
              const existingLink = linkList.find(l => l.id === linkId || (l.source === sourceNodeKey && l.target === targetNodeKey && l.type === linkType.name));
              if (!existingLink) {
                const newLink = {
                  source: sourceNodeKey,  // 使用 type:id 格式
                  target: targetNodeKey,  // 使用 type:id 格式
                  id: linkId,  // 使用唯一的linkId而不是原始的link.id
                  type: linkType.name,
                  data: link,
                };
                linkList.push(newLink);
                console.log(`[loadBackwardLineage] 添加新关系到图中:`, newLink);
              } else {
                console.log(`[loadBackwardLineage] 关系已存在，跳过:`, existingLink);
              }
              
              // 递归查询源节点的反向血缘（继续向上追溯）
              await loadBackwardLineage(
                sourceType,
                sourceId,
                nodeMap,
                linkList,
                visitedNodes,
                linkTypes,
                objectTypes,
                maxDepth,
                currentDepth + 1
              );
            } catch (err) {
              console.error(`[loadBackwardLineage] 加载源实例失败 ${sourceType}:${sourceId}:`, err);
            }
          }
        } catch (err) {
          console.error(`[loadBackwardLineage] 查询 ${linkType.name} 关系失败:`, err);
        }
      } else {
        console.log(`[loadBackwardLineage] 关系类型 ${linkType.name} 的 target_type (${linkType.target_type}) 不匹配当前节点类型 ${objType}，跳过`);
      }
    }
    console.log(`[loadBackwardLineage] 完成查询节点 ${objType}:${instId} 的反向血缘，当前节点数: ${nodeMap.size}, 当前关系数: ${linkList.length}`);
  };

  const loadInstanceGraph = async (objType: string, instId: string, mode: 'forward' | 'backward' | 'full' | 'direct' = 'direct') => {
    const [instance, allObjectTypes, allLinkTypes] = await Promise.all([
      instanceApi.get(objType, instId),
      schemaApi.getObjectTypes(),
      schemaApi.getLinkTypes(),
    ]);

    // 根据工作空间过滤对象类型和关系类型
    const objectTypes = selectedWorkspace && selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0
      ? allObjectTypes.filter((ot) => selectedWorkspace.object_types!.includes(ot.name))
      : allObjectTypes;
    
    let linkTypes: typeof allLinkTypes;
    if (selectedWorkspace && selectedWorkspace.link_types && selectedWorkspace.link_types.length > 0) {
      linkTypes = allLinkTypes.filter((lt) => {
        // 只包含源类型和目标类型都在工作空间内的关系
        const sourceInWorkspace = !selectedWorkspace.object_types || selectedWorkspace.object_types.length === 0 || 
          selectedWorkspace.object_types.includes(lt.source_type);
        const targetInWorkspace = !selectedWorkspace.object_types || selectedWorkspace.object_types.length === 0 || 
          selectedWorkspace.object_types.includes(lt.target_type);
        const included = selectedWorkspace.link_types!.includes(lt.name) && sourceInWorkspace && targetInWorkspace;
        if (lt.name === 'exit_to_path') {
          console.log(`[loadInstanceGraph] exit_to_path 过滤检查:`, {
            name: lt.name,
            source_type: lt.source_type,
            target_type: lt.target_type,
            inLinkTypes: selectedWorkspace.link_types!.includes(lt.name),
            sourceInWorkspace,
            targetInWorkspace,
            included
          });
        }
        return included;
      });
    } else {
      linkTypes = allLinkTypes;
    }
    
    console.log('[loadInstanceGraph] 工作空间过滤结果:', {
      allObjectTypesCount: allObjectTypes.length,
      filteredObjectTypesCount: objectTypes.length,
      allLinkTypesCount: allLinkTypes.length,
      filteredLinkTypesCount: linkTypes.length,
      exit_to_path_included: linkTypes.some(lt => lt.name === 'exit_to_path'),
      selectedWorkspace: selectedWorkspace ? {
        object_types: selectedWorkspace.object_types,
        link_types: selectedWorkspace.link_types
      } : null
    });
    
    // 保存 objectTypes 和 linkTypes 以便在图例中使用
    setObjectTypes(objectTypes);
    setLinkTypes(linkTypes);

    const nodeMap = new Map<string, GraphNode>();
    const linkList: GraphLink[] = [];
    const visitedNodes = new Set<string>();

    // 添加中心节点
    const centerNodeKey = getNodeKey(objType, instance.id);
    const centerNode: GraphNode = {
      id: centerNodeKey,  // 使用 type:id 作为唯一标识
      name: getInstanceName(instance, objType),
      type: objType,
      data: instance,
      group: 0,
    };
    nodeMap.set(centerNodeKey, centerNode);

    // 根据查询模式加载关系
    if (mode === 'direct') {
      // 直接关系模式：只加载直接连接的节点（原有逻辑）
      for (const linkType of linkTypes) {
        try {
          // 查找出边（从当前节点指向的关系）
          if (linkType.source_type === objType) {
            const instanceLinks = await linkApi.getInstanceLinks(objType, instId, linkType.name, 'outgoing');
            for (const link of instanceLinks) {
              const targetId = link.target_id as string;
              const targetType = linkType.target_type;
              
              // 加载目标实例
              try {
                const targetInstance = await instanceApi.get(targetType, targetId);
                const targetNodeKey = getNodeKey(targetType, targetId);
                if (!nodeMap.has(targetNodeKey)) {
                  nodeMap.set(targetNodeKey, {
                    id: targetNodeKey,  // 使用 type:id 作为唯一标识
                    name: getInstanceName(targetInstance, targetType),
                    type: targetType,
                    data: targetInstance,
                    group: getTypeGroup(targetType, objectTypes),
                  });
                }
                const linkId = `${link.id}_${centerNodeKey}_${targetNodeKey}`;
                linkList.push({
                  source: centerNodeKey,  // 使用 type:id 格式
                  target: targetNodeKey,  // 使用 type:id 格式
                  id: linkId,  // 使用唯一的linkId而不是原始的link.id
                  type: linkType.name,
                  data: link,
                });
              } catch (err) {
                console.error(`Failed to load target instance ${targetId}:`, err);
              }
            }
          }

          // 查找入边（指向当前节点的关系）
          if (linkType.target_type === objType) {
            console.log(`[loadInstanceGraph-direct] 检查入边关系类型: ${linkType.name} (source: ${linkType.source_type}, target: ${linkType.target_type})`);
            try {
              // 使用 getInstanceLinks API 查询入边关系，direction='incoming'
              console.log(`[loadInstanceGraph-direct] 查询 ${objType}:${instId} 的 ${linkType.name} 入边关系...`);
              const instanceLinks = await linkApi.getInstanceLinks(objType, instId, linkType.name, 'incoming');
              console.log(`[loadInstanceGraph-direct] 查询到 ${instanceLinks.length} 条 ${linkType.name} 关系:`, instanceLinks);
              
              for (const link of instanceLinks) {
                const sourceId = link.source_id as string;
                const sourceType = linkType.source_type;
                
                console.log(`[loadInstanceGraph-direct] 处理关系: ${linkType.name}, source: ${sourceType}:${sourceId}, target: ${objType}:${instId}`);
                
                // 加载源实例
                try {
                  console.log(`[loadInstanceGraph-direct] 加载源实例 ${sourceType}:${sourceId}...`);
                  const sourceInstance = await instanceApi.get(sourceType, sourceId);
                  console.log(`[loadInstanceGraph-direct] 成功加载源实例 ${sourceType}:${sourceId}:`, sourceInstance);
                  
                  const sourceNodeKey = getNodeKey(sourceType, sourceId);
                  if (!nodeMap.has(sourceNodeKey)) {
                    const newNode = {
                      id: sourceNodeKey,  // 使用 type:id 作为唯一标识
                      name: getInstanceName(sourceInstance, sourceType),
                      type: sourceType,
                      data: sourceInstance,
                      group: getTypeGroup(sourceType, objectTypes),
                    };
                    nodeMap.set(sourceNodeKey, newNode);
                    console.log(`[loadInstanceGraph-direct] 添加新节点到图中:`, newNode);
                  } else {
                    console.log(`[loadInstanceGraph-direct] 节点 ${sourceNodeKey} 已存在于图中`);
                  }
                  
                  const linkId = `${link.id}_${sourceNodeKey}_${centerNodeKey}`;
                  const newLink = {
                    source: sourceNodeKey,  // 使用 type:id 格式
                    target: centerNodeKey,  // 使用 type:id 格式
                    id: linkId,  // 使用唯一的linkId而不是原始的link.id
                    type: linkType.name,
                    data: link,
                  };
                  linkList.push(newLink);
                  console.log(`[loadInstanceGraph-direct] 添加新关系到图中:`, newLink);
                } catch (err) {
                  console.error(`[loadInstanceGraph-direct] 加载源实例失败 ${sourceType}:${sourceId}:`, err);
                }
              }
            } catch (err) {
              console.error(`[loadInstanceGraph-direct] 查询 ${linkType.name} 入边关系失败:`, err);
            }
          }
        } catch (err) {
          console.error(`Failed to load links for ${linkType.name}:`, err);
        }
      }
    } else if (mode === 'forward') {
      // 正向血缘：从当前节点向后递归查询
      await loadForwardLineage(
        objType,
        instId,
        nodeMap,
        linkList,
        visitedNodes,
        linkTypes,
        objectTypes
      );
    } else if (mode === 'backward') {
      // 反向血缘：从当前节点向前递归查询
      console.log('[GraphView] 开始反向血缘查询', { objType, instId, linkTypesCount: linkTypes.length, objectTypesCount: objectTypes.length });
      console.log('[GraphView] 过滤后的关系类型:', linkTypes.map(lt => ({ name: lt.name, source_type: lt.source_type, target_type: lt.target_type })));
      await loadBackwardLineage(
        objType,
        instId,
        nodeMap,
        linkList,
        visitedNodes,
        linkTypes,  // 使用工作空间过滤后的关系类型
        objectTypes
      );
      console.log('[GraphView] 反向血缘查询完成', { nodesCount: nodeMap.size, linksCount: linkList.length });
    } else if (mode === 'full') {
      // 全链血缘：遍历与当前节点相关的全部节点和关系（包括出边和入边）
      await loadFullLineage(
        objType,
        instId,
        nodeMap,
        linkList,
        visitedNodes,
        linkTypes,  // 使用工作空间过滤后的关系类型
        objectTypes
      );
    }

    const finalNodes = Array.from(nodeMap.values());
    const finalLinks = linkList;
    
    console.log('[loadInstanceGraph] 最终结果:', {
      nodesCount: finalNodes.length,
      linksCount: finalLinks.length,
      nodes: finalNodes.map(n => ({ id: n.id, type: n.type, name: n.name })),
      links: finalLinks.map(l => ({ id: l.id, type: l.type, source: l.source, target: l.target })),
      exit_to_path_links: finalLinks.filter(l => l.type === 'exit_to_path'),
      exit_transaction_nodes: finalNodes.filter(n => n.type === 'ExitTransaction')
    });
    
    setNodes(finalNodes);
    setLinks(finalLinks);
  };

  const loadFullGraph = async () => {
    // 使用用户配置的上限值
    const MAX_NODES = maxNodes;
    const MAX_LINKS_PER_TYPE = maxLinksPerType;
    
    const [allObjectTypes, allLinkTypes] = await Promise.all([
      schemaApi.getObjectTypes(),
      schemaApi.getLinkTypes(),
    ]);

    // 根据工作空间过滤对象类型和关系类型
    const objectTypes = selectedWorkspace && selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0
      ? allObjectTypes.filter((ot) => selectedWorkspace.object_types!.includes(ot.name))
      : allObjectTypes;
    
    let linkTypes: typeof allLinkTypes;
    if (selectedWorkspace && selectedWorkspace.link_types && selectedWorkspace.link_types.length > 0) {
      linkTypes = allLinkTypes.filter((lt) => {
        // 只包含源类型和目标类型都在工作空间内的关系
        const sourceInWorkspace = !selectedWorkspace.object_types || selectedWorkspace.object_types.length === 0 || 
          selectedWorkspace.object_types.includes(lt.source_type);
        const targetInWorkspace = !selectedWorkspace.object_types || selectedWorkspace.object_types.length === 0 || 
          selectedWorkspace.object_types.includes(lt.target_type);
        return selectedWorkspace.link_types!.includes(lt.name) && sourceInWorkspace && targetInWorkspace;
      });
    } else {
      linkTypes = allLinkTypes;
    }
    
    // 保存 objectTypes 和 linkTypes 以便在图例中使用
    setObjectTypes(objectTypes);
    setLinkTypes(linkTypes);

    const nodeMap = new Map<string, GraphNode>();
    const linkList: GraphLink[] = [];
    const nodeIdsToLoad = new Set<string>(); // 需要加载的节点ID集合
    const nodeDegree = new Map<string, number>(); // 节点度数（连接数）

    // 第一步：加载关系，收集节点ID
    for (const linkType of linkTypes) {
      try {
        // 根据关系类型设置基础限制
        const baseLimit = (linkType.name === 'table_has_column' || linkType.name === 'database_has_table') ? 1000 : 200;
        const limit = Math.min(MAX_LINKS_PER_TYPE, baseLimit);

        let offset = 0;
        let hasMore = true;
        
        while (hasMore) {
          const links = await linkApi.list(linkType.name, offset, limit);
          
          for (const link of links.items) {
            const sourceId = link.source_id as string;
            const targetId = link.target_id as string;
            const sourceType = linkType.source_type;
            const targetType = linkType.target_type;
            
            // 收集节点ID并计算度数（使用 type:id 作为唯一标识）
            const sourceNodeKey = getNodeKey(sourceType, sourceId);
            const targetNodeKey = getNodeKey(targetType, targetId);
            nodeIdsToLoad.add(sourceNodeKey);
            nodeIdsToLoad.add(targetNodeKey);
            nodeDegree.set(sourceNodeKey, (nodeDegree.get(sourceNodeKey) || 0) + 1);
            nodeDegree.set(targetNodeKey, (nodeDegree.get(targetNodeKey) || 0) + 1);
            
            const linkId = `${link.id}_${sourceNodeKey}_${targetNodeKey}`;
            linkList.push({
              source: sourceNodeKey,  // 使用 type:id 格式
              target: targetNodeKey,  // 使用 type:id 格式
              id: linkId,  // 使用唯一的linkId而不是原始的link.id
              type: linkType.name,
              data: link,
            });
          }
          
          // 检查是否还有更多数据
          hasMore = links.items.length === limit && (offset + limit < links.total);
          offset += limit;
          
          // 避免无限循环（设置一个合理的上限）
          if (offset > 100000) break;
        }
      } catch (err) {
        console.error(`Failed to load links for ${linkType.name}:`, err);
      }
    }

    // 第二步：如果节点数超过限制，进行采样（优先保留度数高的节点）
    // 注意：工作空间模式下，如果节点数超过限制，仍然需要采样，但会给出警告
    let nodeIdsArray = Array.from(nodeIdsToLoad);
    if (nodeIdsArray.length > MAX_NODES) {
      // 按度数排序，优先保留度数高的节点
      nodeIdsArray.sort((a, b) => {
        const degreeA = nodeDegree.get(a) || 0;
        const degreeB = nodeDegree.get(b) || 0;
        return degreeB - degreeA; // 降序
      });
      
      // 保留前 MAX_NODES 个节点
      nodeIdsArray = nodeIdsArray.slice(0, MAX_NODES);
      nodeIdsToLoad.clear();
      nodeIdsArray.forEach(id => nodeIdsToLoad.add(id));
      
      // 过滤掉包含被移除节点的链接
      const validNodeSet = new Set(nodeIdsArray);
      const filteredLinks = linkList.filter(link => 
        validNodeSet.has(link.source) && validNodeSet.has(link.target)
      );
      linkList.length = 0;
      linkList.push(...filteredLinks);
      
      const warningMsg = `节点数超过限制（${nodeIdsArray.length} > ${MAX_NODES}），已采样保留 ${MAX_NODES} 个节点（优先保留连接数高的节点）。建议：在设置中增加节点上限`;
      console.warn(warningMsg);
    }

    // 第三步：按对象类型分组收集节点ID
    const nodeIdsByType = new Map<string, Set<string>>();
    
    // 从链接中收集节点ID，并按类型分组
    // 注意：link.source 和 link.target 现在已经是 type:id 格式
    for (const link of linkList) {
      // 从 type:id 格式中提取类型和ID
      const [sourceType, sourceId] = link.source.split(':', 2);
      const [targetType, targetId] = link.target.split(':', 2);
      
      if (sourceType && sourceId) {
        if (!nodeIdsByType.has(sourceType)) {
          nodeIdsByType.set(sourceType, new Set());
        }
        nodeIdsByType.get(sourceType)!.add(sourceId);
      }
      
      if (targetType && targetId) {
        if (!nodeIdsByType.has(targetType)) {
          nodeIdsByType.set(targetType, new Set());
        }
        nodeIdsByType.get(targetType)!.add(targetId);
      }
    }

    // 第四步：批量加载节点（只加载有关系的节点，不预加载所有实例）
    // 使用批量查询接口，大幅减少 HTTP 请求数
    const queries = Array.from(nodeIdsByType.entries()).map(([typeName, nodeIds]) => ({
      objectType: typeName,
      ids: Array.from(nodeIds),
    }));

    if (queries.length > 0) {
      try {
        // 使用批量查询接口一次性获取所有节点
        const batchResults = await instanceApi.getBatchMultiType(queries);
        
        // 处理批量查询结果
        for (const [key, instance] of Object.entries(batchResults)) {
          if (instance) {
            // key 格式为 "objectType:id"
            const [typeName, id] = key.split(':', 2);
            if (typeName && id) {
              const nodeKey = getNodeKey(typeName, id);
              nodeMap.set(nodeKey, {
                id: nodeKey,  // 使用 type:id 作为唯一标识
                name: getInstanceName(instance, typeName),
                type: typeName,
                data: instance,
                group: getTypeGroup(typeName, objectTypes),
              });
            }
          }
        }
      } catch (err) {
        console.error('Failed to load instances batch:', err);
        // 如果批量查询失败，回退到逐个查询（但限制数量）
        const fallbackLimit = 100; // 回退时最多查询 100 个节点
        let fallbackCount = 0;
        for (const [typeName, nodeIds] of nodeIdsByType.entries()) {
          for (const nodeId of nodeIds) {
            if (fallbackCount >= fallbackLimit) break;
            const nodeKey = getNodeKey(typeName, nodeId);
            if (!nodeMap.has(nodeKey)) {
              try {
                const instance = await instanceApi.get(typeName, nodeId);
                nodeMap.set(nodeKey, {
                  id: nodeKey,  // 使用 type:id 作为唯一标识
                  name: getInstanceName(instance, typeName),
                  type: typeName,
                  data: instance,
                  group: getTypeGroup(typeName, objectTypes),
                });
                fallbackCount++;
              } catch (err) {
                console.error(`Failed to load instance ${nodeId} of type ${typeName}:`, err);
              }
            }
          }
          if (fallbackCount >= fallbackLimit) break;
        }
      }
    }

    // 第五步：过滤掉没有对应节点的链接
    const validLinks = linkList.filter(link => 
      nodeMap.has(link.source) && nodeMap.has(link.target)
    );

    setNodes(Array.from(nodeMap.values()));
    setLinks(validLinks);
  };

  const getInstanceName = (instance: Instance, type: string): string => {
    // 尝试获取 name 字段，如果没有则使用 id 的前8位
    if (instance.name) {
      return String(instance.name);
    }
    if (instance.name) {
      return String(instance.name);
    }
    return `${type}:${instance.id.substring(0, 8)}`;
  };

  const getTypeGroup = (type: string, objectTypes: any[]): number => {
    const index = objectTypes.findIndex(ot => ot.name === type);
    return index >= 0 ? index : 0;
  };

  // 为不同关系类型生成颜色的函数
  const getLinkTypeColor = (linkType: string): string => {
    // 预定义的颜色数组（使用更鲜艳、对比度高的颜色）
    const linkColors = [
      '#3b82f6', // 蓝色
      '#10b981', // 绿色
      '#f59e0b', // 橙色
      '#ef4444', // 红色
      '#8b5cf6', // 紫色
      '#06b6d4', // 青色
      '#ec4899', // 粉色
      '#14b8a6', // 青绿色
      '#f97316', // 橙红色
      '#6366f1', // 靛蓝色
      '#84cc16', // 黄绿色
      '#eab308', // 黄色
      '#dc2626', // 深红色
      '#7c3aed', // 深紫色
      '#0891b2', // 深青色
    ];

    // 根据关系类型名称生成哈希值
    let hash = 0;
    for (let i = 0; i < linkType.length; i++) {
      hash = linkType.charCodeAt(i) + ((hash << 5) - hash);
    }
    
    // 使用哈希值选择颜色
    const colorIndex = Math.abs(hash) % linkColors.length;
    return linkColors[colorIndex];
  };

  useEffect(() => {
    // 更新 graphData，但保留现有的节点位置信息（fx, fy）
    setGraphData(prevData => {
      // 创建一个节点 ID 到位置的映射
      const positionMap = new Map<string, { fx?: number; fy?: number }>();
      if (prevData.nodes) {
        prevData.nodes.forEach((node: any) => {
          if (node.fx !== undefined || node.fy !== undefined) {
            positionMap.set(node.id, { fx: node.fx, fy: node.fy });
          }
        });
      }
      
      // 合并新的节点数据和保留的位置信息
      const updatedNodes = nodes.map(node => {
        const position = positionMap.get(node.id);
        return {
          ...node,
          ...(position || {})
        };
      });
      
      return { nodes: updatedNodes, links };
    });
  }, [nodes, links]);

  // 圆形布局函数
  const applyCircleLayout = useCallback(() => {
    if (nodes.length === 0 || !fgRef.current) return;
    
    const centerX = 0;
    const centerY = 0;
    const radius = Math.max(200, Math.sqrt(nodes.length) * 30);
    const angleStep = (2 * Math.PI) / nodes.length;
    
    // 创建新的节点数组，添加固定位置
    const updatedNodes = nodes.map((node, index) => {
      const angle = index * angleStep;
      const x = centerX + radius * Math.cos(angle);
      const y = centerY + radius * Math.sin(angle);
      
      return {
        ...node,
        fx: x,
        fy: y
      };
    });
    
    // 更新图形数据
    setGraphData({ nodes: updatedNodes, links });
    
    // 重新启动力导向图
    if (fgRef.current) {
      setTimeout(() => {
        if (fgRef.current) {
          fgRef.current.d3Force('charge', null);
          fgRef.current.d3Force('link', null);
          fgRef.current.d3Force('center', null);
          fgRef.current.d3ReheatSimulation();
        }
      }, 0);
    }
  }, [nodes, links]);

  // 网格布局函数
  const applyGridLayout = useCallback(() => {
    if (nodes.length === 0 || !fgRef.current) return;
    
    const cols = Math.ceil(Math.sqrt(nodes.length));
    const rows = Math.ceil(nodes.length / cols);
    const spacing = 150;
    const startX = -(cols - 1) * spacing / 2;
    const startY = -(rows - 1) * spacing / 2;
    
    // 创建新的节点数组，添加固定位置
    const updatedNodes = nodes.map((node, index) => {
      const col = index % cols;
      const row = Math.floor(index / cols);
      const x = startX + col * spacing;
      const y = startY + row * spacing;
      
      return {
        ...node,
        fx: x,
        fy: y
      };
    });
    
    // 更新图形数据
    setGraphData({ nodes: updatedNodes, links });
    
    // 重新启动力导向图
    if (fgRef.current) {
      setTimeout(() => {
        if (fgRef.current) {
          fgRef.current.d3Force('charge', null);
          fgRef.current.d3Force('link', null);
          fgRef.current.d3Force('center', null);
          fgRef.current.d3ReheatSimulation();
        }
      }, 0);
    }
  }, [nodes, links]);

  // 径向布局函数（以中心节点为圆心，其他节点按层级分布）
  const applyRadialLayout = useCallback(() => {
    if (nodes.length === 0 || !fgRef.current) return;
    
    // 找到度数最高的节点作为中心节点
    const nodeDegrees = new Map<string, number>();
    links.forEach(link => {
      nodeDegrees.set(link.source, (nodeDegrees.get(link.source) || 0) + 1);
      nodeDegrees.set(link.target, (nodeDegrees.get(link.target) || 0) + 1);
    });
    
    let centerNodeId = nodes[0]?.id;
    let maxDegree = 0;
    nodes.forEach(node => {
      const degree = nodeDegrees.get(node.id) || 0;
      if (degree > maxDegree) {
        maxDegree = degree;
        centerNodeId = node.id;
      }
    });
    
    // 计算每个节点到中心节点的最短距离（层级）
    const nodeLevels = new Map<string, number>();
    const visited = new Set<string>();
    const queue: { id: string; level: number }[] = [{ id: centerNodeId!, level: 0 }];
    
    while (queue.length > 0) {
      const { id, level } = queue.shift()!;
      if (visited.has(id)) continue;
      visited.add(id);
      nodeLevels.set(id, level);
      
      links.forEach(link => {
        if (link.source === id && !visited.has(link.target)) {
          queue.push({ id: link.target, level: level + 1 });
        } else if (link.target === id && !visited.has(link.source)) {
          queue.push({ id: link.source, level: level + 1 });
        }
      });
    }
    
    // 按层级分组节点
    const nodesByLevel = new Map<number, string[]>();
    nodes.forEach(node => {
      const level = nodeLevels.get(node.id) || 0;
      if (!nodesByLevel.has(level)) {
        nodesByLevel.set(level, []);
      }
      nodesByLevel.get(level)!.push(node.id);
    });
    
    // 计算布局
    const centerX = 0;
    const centerY = 0;
    const baseRadius = 100;
    const radiusStep = 120;
    
    // 创建新的节点数组，添加固定位置
    const updatedNodes = nodes.map((node) => {
      if (node.id === centerNodeId) {
        // 中心节点
        return {
          ...node,
          fx: centerX,
          fy: centerY
        };
      } else {
        const level = nodeLevels.get(node.id) || 1;
        const levelNodes = nodesByLevel.get(level) || [];
        const indexInLevel = levelNodes.indexOf(node.id);
        const nodesInLevel = levelNodes.length;
        
        const radius = baseRadius + (level - 1) * radiusStep;
        const angle = (2 * Math.PI * indexInLevel) / nodesInLevel;
        const x = centerX + radius * Math.cos(angle);
        const y = centerY + radius * Math.sin(angle);
        
        return {
          ...node,
          fx: x,
          fy: y
        };
      }
    });
    
    // 更新图形数据
    setGraphData({ nodes: updatedNodes, links });
    
    // 重新启动力导向图
    if (fgRef.current) {
      setTimeout(() => {
        if (fgRef.current) {
          fgRef.current.d3Force('charge', null);
          fgRef.current.d3Force('link', null);
          fgRef.current.d3Force('center', null);
          fgRef.current.d3ReheatSimulation();
        }
      }, 0);
    }
  }, [nodes, links]);

  // 配置力导向图参数
  const configureForceGraph = useCallback(() => {
    if (!fgRef.current || nodes.length === 0) return;
    
    // 配置连接距离
    const linkForce = fgRef.current.d3Force('link');
    if (linkForce) {
      linkForce.distance(forceParams.linkDistance);
    }
    
    // 配置电荷力（节点之间的排斥力）
    const chargeForce = fgRef.current.d3Force('charge');
    if (chargeForce) {
      chargeForce.strength(forceParams.chargeStrength);
    }
    
    // 配置中心力（注意：d3 forceCenter 没有 strength 方法，我们通过其他方式控制）
    // 如果需要调整中心力，可以通过调整 alpha 衰减来实现
  }, [nodes, forceParams]);

  // 力导向参数预设
  const applyForcePreset = useCallback((preset: 'uniform' | 'compact' | 'spread' | 'default') => {
    let newParams: typeof forceParams;
    
    switch (preset) {
      case 'uniform':
        // 均匀分布：中等连接距离，强排斥力，使节点分布更均匀
        newParams = {
          linkDistance: 120,
          chargeStrength: -500,
          centerStrength: 0.05,
          alphaDecay: 0.0228,
          velocityDecay: 0.5
        };
        break;
      case 'compact':
        // 紧凑：短连接距离，弱排斥力，节点更紧密
        newParams = {
          linkDistance: 60,
          chargeStrength: -150,
          centerStrength: 0.15,
          alphaDecay: 0.0228,
          velocityDecay: 0.6
        };
        break;
      case 'spread':
        // 分散：长连接距离，强排斥力，节点更分散
        newParams = {
          linkDistance: 150,
          chargeStrength: -800,
          centerStrength: 0.02,
          alphaDecay: 0.0228,
          velocityDecay: 0.3
        };
        break;
      case 'default':
      default:
        // 默认参数
        newParams = {
          linkDistance: 90,
          chargeStrength: -300,
          centerStrength: 0.1,
          alphaDecay: 0.0228,
          velocityDecay: 0.4
        };
        break;
    }
    
    setForceParams(newParams);
    localStorage.setItem('graphView_forceParams', JSON.stringify(newParams));
    configureForceGraph();
    if (fgRef.current) {
      fgRef.current.d3ReheatSimulation();
    }
  }, [configureForceGraph]);

  // 释放固定位置，恢复力导向
  const releaseFixedPositions = useCallback(() => {
    if (!fgRef.current) return;
    
    // 创建新的节点数组，移除固定位置
    const updatedNodes = nodes.map((node) => ({
      ...node,
      fx: undefined,
      fy: undefined
    }));
    
    // 更新图形数据
    setGraphData({ nodes: updatedNodes, links });
    
    // 重新配置力导向参数
    setTimeout(() => {
      if (fgRef.current) {
        configureForceGraph();
        fgRef.current.d3ReheatSimulation();
      }
    }, 0);
  }, [nodes, links, configureForceGraph]);

  // 配置力导向图的参数
  useEffect(() => {
    if (fgRef.current && nodes.length > 0) {
      configureForceGraph();
    }
  }, [nodes, links, configureForceGraph]);

  // 当布局类型改变时应用布局
  useEffect(() => {
    if (viewMode !== 'force' || nodes.length === 0 || !fgRef.current) return;
    
    if (layoutType === 'circle') {
      applyCircleLayout();
    } else if (layoutType === 'grid') {
      applyGridLayout();
    } else if (layoutType === 'radial') {
      applyRadialLayout();
    } else {
      // 力导向布局：释放固定位置
      releaseFixedPositions();
    }
  }, [layoutType, viewMode, nodes.length, applyCircleLayout, applyGridLayout, applyRadialLayout, releaseFixedPositions]);

  const handleNodeClick = useCallback((node: GraphNode) => {
    setSelectedNode(node);
  }, []);

  const handleBackgroundClick = useCallback(() => {
    setSelectedNode(null);
  }, []);

  const nodeColor = (node: GraphNode) => {
    const colors = [
      '#2563eb', // 深蓝色 - 高对比度
      '#059669', // 深绿色 - 高对比度
      '#d97706', // 深橙色 - 高对比度
      '#dc2626', // 深红色 - 高对比度
      '#7c3aed', // 深紫色 - 高对比度
      '#db2777', // 深粉色 - 高对比度
      '#0891b2', // 深青色 - 高对比度
      '#65a30d', // 深黄绿色 - 高对比度
    ];
    return colors[(node.group || 0) % colors.length];
  };

  const getNodeLabel = (node: GraphNode) => {
    // node.id 现在是 type:id 格式，提取ID部分用于显示
    const nodeIdOnly = node.id.includes(':') ? node.id.split(':')[1] : node.id;
    const name = node.name || nodeIdOnly.substring(0, 8);
    const objectType = objectTypes.find(ot => ot.name === node.type);
    const typeDisplayName = objectType?.display_name || node.type;
    // 尝试获取一些关键属性
    const keyProps: string[] = [];
    if (node.data.name) keyProps.push(`Name: ${node.data.name}`);
    if (node.data.email) keyProps.push(`Email: ${node.data.email}`);
    if (node.data.age !== undefined) keyProps.push(`Age: ${node.data.age}`);
    if (node.data.founded_year) keyProps.push(`Year: ${node.data.founded_year}`);
    
    return `${name}\n[${typeDisplayName}]${keyProps.length > 0 ? '\n' + keyProps.join('\n') : ''}`;
  };

  const getLinkLabel = (link: GraphLink) => {
    const linkType = linkTypes.find(lt => lt.name === link.type);
    const typeDisplayName = linkType?.display_name || link.type;
    const labels: string[] = [typeDisplayName];
    // 添加关系的关键属性
    if (link.data.start_date) labels.push(`Start: ${link.data.start_date}`);
    if (link.data.position) labels.push(`Position: ${link.data.position}`);
    if (link.data.since) labels.push(`Since: ${link.data.since}`);
    return labels.join('\n');
  };

  // 计算分层视图的层级（优先按节点类型分组）
  const calculateHierarchicalLayers = useCallback(() => {
    if (nodes.length === 0) return new Map<string, number>();

    // 第一步：按节点类型分组
    const nodesByType = new Map<string, GraphNode[]>();
    nodes.forEach(node => {
      if (!nodesByType.has(node.type)) {
        nodesByType.set(node.type, []);
      }
      nodesByType.get(node.type)!.push(node);
    });

    // 第二步：构建节点类型之间的关系图
    // typeOutEdges: 从源类型到目标类型的边集合
    const typeOutEdges = new Map<string, Set<string>>();
    const typeInEdges = new Map<string, Set<string>>();
    
    links.forEach(link => {
      const sourceNode = nodes.find(n => n.id === link.source);
      const targetNode = nodes.find(n => n.id === link.target);
      
      if (sourceNode && targetNode && sourceNode.type !== targetNode.type) {
        // 只考虑不同类型之间的关系
        if (!typeOutEdges.has(sourceNode.type)) {
          typeOutEdges.set(sourceNode.type, new Set());
        }
        typeOutEdges.get(sourceNode.type)!.add(targetNode.type);
        
        if (!typeInEdges.has(targetNode.type)) {
          typeInEdges.set(targetNode.type, new Set());
        }
        typeInEdges.get(targetNode.type)!.add(sourceNode.type);
      }
    });

    // 第三步：计算每个节点类型的层级
    const typeLayers = new Map<string, number>();
    const typeQueue: { type: string; layer: number }[] = [];
    
    // 找到所有没有入边的类型（根类型）
    nodesByType.forEach((_typeNodes, type) => {
      const inDegree = typeInEdges.get(type)?.size || 0;
      if (inDegree === 0) {
        typeQueue.push({ type, layer: 0 });
        typeLayers.set(type, 0);
      }
    });

    // 如果没有根类型，将所有类型都放在第0层
    if (typeQueue.length === 0) {
      nodesByType.forEach((_, type) => {
        typeLayers.set(type, 0);
      });
    } else {
      // BFS遍历类型层级
      while (typeQueue.length > 0) {
        const { type, layer } = typeQueue.shift()!;
        const children = typeOutEdges.get(type) || new Set();
        
        children.forEach(childType => {
          if (!typeLayers.has(childType)) {
            const childLayer = layer + 1;
            typeLayers.set(childType, childLayer);
            typeQueue.push({ type: childType, layer: childLayer });
          } else {
            // 如果已访问，更新为更深的层级
            const currentLayer = typeLayers.get(childType)!;
            if (layer + 1 > currentLayer) {
              typeLayers.set(childType, layer + 1);
              typeQueue.push({ type: childType, layer: layer + 1 });
            }
          }
        });
      }

      // 为没有层级的类型分配层级（可能是孤立类型或循环中的类型）
      nodesByType.forEach((_, type) => {
        if (!typeLayers.has(type)) {
          // 找到该类型的所有前驱类型的最大层级
          const predecessors = typeInEdges.get(type) || new Set();
          if (predecessors.size > 0) {
            const maxPredLayer = Math.max(...Array.from(predecessors).map(p => typeLayers.get(p) || 0));
            typeLayers.set(type, maxPredLayer + 1);
          } else {
            // 没有前驱的类型放在第0层
            typeLayers.set(type, 0);
          }
        }
      });
    }

    // 第四步：为每个节点分配层级（基于其类型的层级）
    const nodeLayers = new Map<string, number>();
    nodes.forEach(node => {
      const typeLayer = typeLayers.get(node.type) || 0;
      nodeLayers.set(node.id, typeLayer);
    });

    return nodeLayers;
  }, [nodes, links]);

  // 递归查找所有后续节点（传递性）
  const findAllDescendants = useCallback((startNodeId: string, visitedNodes: Set<string> = new Set()): { nodes: Set<string>; links: Set<string> } => {
    const resultNodes = new Set<string>();
    const resultLinks = new Set<string>();
    
    // 如果已经访问过，直接返回
    if (visitedNodes.has(startNodeId)) {
      return { nodes: resultNodes, links: resultLinks };
    }
    
    visitedNodes.add(startNodeId);
    
    // 找到所有从该节点出发的连接（出边）
    const outgoingLinks = links.filter(link => link.source === startNodeId);
    
    outgoingLinks.forEach(link => {
      resultLinks.add(link.id);
      const targetId = link.target;
      
      if (!visitedNodes.has(targetId)) {
        resultNodes.add(targetId);
        
        // 递归查找目标节点的后续节点
        const descendants = findAllDescendants(targetId, visitedNodes);
        descendants.nodes.forEach(n => resultNodes.add(n));
        descendants.links.forEach(l => resultLinks.add(l));
      }
    });
    
    return { nodes: resultNodes, links: resultLinks };
  }, [links]);

  // 递归查找所有前驱节点（反向依赖，传递性）
  const findAllAncestors = useCallback((startNodeId: string, visitedNodes: Set<string> = new Set()): { nodes: Set<string>; links: Set<string> } => {
    const resultNodes = new Set<string>();
    const resultLinks = new Set<string>();
    
    // 如果已经访问过，直接返回
    if (visitedNodes.has(startNodeId)) {
      return { nodes: resultNodes, links: resultLinks };
    }
    
    visitedNodes.add(startNodeId);
    
    // 找到所有指向该节点的连接（入边）
    const incomingLinks = links.filter(link => link.target === startNodeId);
    
    incomingLinks.forEach(link => {
      resultLinks.add(link.id);
      const sourceId = link.source;
      
      if (!visitedNodes.has(sourceId)) {
        resultNodes.add(sourceId);
        
        // 递归查找源节点的前驱节点
        const ancestors = findAllAncestors(sourceId, visitedNodes);
        ancestors.nodes.forEach(n => resultNodes.add(n));
        ancestors.links.forEach(l => resultLinks.add(l));
      }
    });
    
    return { nodes: resultNodes, links: resultLinks };
  }, [links]);

  // 处理节点点击，高亮后续节点（递归传递）
  const handleNodeClickInHierarchical = useCallback((node: GraphNode) => {
    const now = Date.now();
    const timeSinceLastClick = now - lastClickTimeRef.current;
    
    // 如果距离上次点击时间很短（小于300ms），可能是双击，延迟执行单击操作
    if (timeSinceLastClick < 300) {
      // 清除之前的延迟单击
      if (clickTimeoutRef.current) {
        clearTimeout(clickTimeoutRef.current);
      }
      return;
    }
    
    lastClickTimeRef.current = now;
    
    // 延迟执行单击操作，以便检测是否是双击
    if (clickTimeoutRef.current) {
      clearTimeout(clickTimeoutRef.current);
    }
    
    clickTimeoutRef.current = setTimeout(() => {
      setSelectedNode(node);
      
      // 递归查找所有后续节点和连接
      const { nodes: descendantNodes, links: descendantLinks } = findAllDescendants(node.id);
      
      // 设置高亮状态（正向影响）
      setHighlightedNodes(descendantNodes);
      setHighlightedLinks(descendantLinks);
      
      // 清除反向依赖高亮
      setReverseHighlightedNodes(new Set());
      setReverseHighlightedLinks(new Set());
    }, 300);
  }, [findAllDescendants]);

  // 处理节点双击，高亮反向依赖节点（递归传递）
  const handleNodeDoubleClickInHierarchical = useCallback((node: GraphNode) => {
    // 取消延迟的单击操作
    if (clickTimeoutRef.current) {
      clearTimeout(clickTimeoutRef.current);
      clickTimeoutRef.current = null;
    }
    
    setSelectedNode(node);
    
    // 递归查找所有前驱节点和连接
    const { nodes: ancestorNodes, links: ancestorLinks } = findAllAncestors(node.id);
    
    // 设置反向依赖高亮状态
    setReverseHighlightedNodes(ancestorNodes);
    setReverseHighlightedLinks(ancestorLinks);
    
    // 清除正向影响高亮
    setHighlightedNodes(new Set());
    setHighlightedLinks(new Set());
  }, [findAllAncestors]);

  // 分层视图组件
  const HierarchicalView = () => {
    const layers = calculateHierarchicalLayers();
    const containerRef = useRef<HTMLDivElement>(null);
    const svgRef = useRef<SVGSVGElement>(null);

    // 按层级和类型分组节点
    const nodesByLayerAndType = new Map<number, Map<string, GraphNode[]>>();
    let maxLayer = 0;

    nodes.forEach(node => {
      const layer = layers.get(node.id) || 0;
      maxLayer = Math.max(maxLayer, layer);
      
      if (!nodesByLayerAndType.has(layer)) {
        nodesByLayerAndType.set(layer, new Map());
      }
      const layerMap = nodesByLayerAndType.get(layer)!;
      
      if (!layerMap.has(node.type)) {
        layerMap.set(node.type, []);
      }
      layerMap.get(node.type)!.push(node);
    });

    // 布局参数
    const layerWidth = 300;
    const typeBoxHeight = 200;
    const typeBoxSpacing = 20;
    const nodeItemHeight = 32;
    const nodeItemSpacing = 4;
    const padding = 40;

    // 计算每个类型框的位置
    // 每一层都从顶部开始排列，而不是累积排列
    const typeBoxPositions = new Map<string, { x: number; y: number; width: number; height: number }>();
    
    // 计算每一层的最大高度
    const layerHeights = new Map<number, number>();
    
    nodesByLayerAndType.forEach((typeMap, layer) => {
      let layerMaxHeight = 0;
      let typeY = padding; // 每一层都从顶部开始
      
      typeMap.forEach((typeNodes, type) => {
        const boxKey = `${layer}-${type}`;
        const isCollapsed = collapsedTypes.has(boxKey);
        const nodeCount = typeNodes.length;
        // 如果折叠，只显示标题栏高度（约50px），否则显示完整高度
        const boxHeight = isCollapsed ? 50 : Math.max(typeBoxHeight, nodeCount * (nodeItemHeight + nodeItemSpacing) + 60);
        typeBoxPositions.set(boxKey, {
          x: padding + layer * (layerWidth + 100),
          y: typeY,
          width: layerWidth,
          height: boxHeight
        });
        typeY += boxHeight + typeBoxSpacing;
      });
      
      // 记录这一层的总高度（包括最后一个框的底部间距）
      layerMaxHeight = typeY - typeBoxSpacing + padding; // 减去最后一个间距，加上底部padding
      layerHeights.set(layer, layerMaxHeight);
    });

    // 计算总宽度和总高度（总高度取所有层的最大高度）
    const totalWidth = padding * 2 + (maxLayer + 1) * (layerWidth + 100);
    const maxLayerHeight = Math.max(...Array.from(layerHeights.values()), padding * 2);
    const totalHeight = maxLayerHeight;

    return (
      <div 
        className="w-full h-full overflow-auto bg-gray-50" 
        ref={containerRef}
      >
        <svg
          ref={svgRef}
          width={totalWidth}
          height={totalHeight}
          className="absolute top-0 left-0 pointer-events-none"
        >
          {/* 绘制连接线 */}
          {(() => {
            // 按源类型框和目标类型框分组关系，用于归并
            const linksByConnection = new Map<string, Array<{ link: GraphLink; sourceNode: GraphNode; targetNode: GraphNode }>>();
            
            links.forEach(link => {
              const sourceNode = nodes.find(n => n.id === link.source);
              const targetNode = nodes.find(n => n.id === link.target);
              
              if (!sourceNode || !targetNode) return;
              
              const sourceLayer = layers.get(sourceNode.id) || 0;
              const targetLayer = layers.get(targetNode.id) || 0;
              const sourceBoxKey = `${sourceLayer}-${sourceNode.type}`;
              const targetBoxKey = `${targetLayer}-${targetNode.type}`;
              
              const connectionKey = `${sourceBoxKey}->${targetBoxKey}:${link.type}`;
              if (!linksByConnection.has(connectionKey)) {
                linksByConnection.set(connectionKey, []);
              }
              linksByConnection.get(connectionKey)!.push({ link, sourceNode, targetNode });
            });
            
            // 渲染归并后的关系线
            const renderedLines: any[] = [];
            
            linksByConnection.forEach((linkGroup, connectionKey) => {
              const [sourceBoxKey, rest] = connectionKey.split('->');
              const [targetBoxKey, linkType] = rest.split(':');
              
              const sourceBox = typeBoxPositions.get(sourceBoxKey);
              const targetBox = typeBoxPositions.get(targetBoxKey);
              
              if (!sourceBox || !targetBox) return;
              
              const isSourceCollapsed = collapsedTypes.has(sourceBoxKey);
              const isTargetCollapsed = collapsedTypes.has(targetBoxKey);
              
              // 如果源或目标类型被折叠，归并关系线
              if (isSourceCollapsed || isTargetCollapsed) {
                // 归并：只显示一条线，从源类型框的右侧中间到目标类型框的左侧中间
                const x1 = sourceBox.x + sourceBox.width;
                const y1 = sourceBox.y + sourceBox.height / 2;
                const x2 = targetBox.x;
                const y2 = targetBox.y + targetBox.height / 2;
                
                // 检查是否有高亮的关系
                const hasHighlighted = linkGroup.some(({ link }) => 
                  highlightedLinks.has(link.id) || reverseHighlightedLinks.has(link.id)
                );
                const linkColor = getLinkTypeColor(linkType);
                
                let strokeColor = linkColor;
                let strokeWidth = Math.min(2 + linkGroup.length * 0.5, 6); // 根据关系数量调整线宽
                if (hasHighlighted) {
                  strokeColor = linkGroup.some(({ link }) => highlightedLinks.has(link.id)) 
                    ? '#ef4444' 
                    : '#3b82f6';
                  strokeWidth = 4;
                }
                
                renderedLines.push(
                  <line
                    key={connectionKey}
                    x1={x1}
                    y1={y1}
                    x2={x2}
                    y2={y2}
                    stroke={strokeColor}
                    strokeWidth={strokeWidth}
                    markerEnd="url(#arrowhead)"
                    opacity={hasHighlighted ? 1 : 0.6}
                    className={hasHighlighted ? 'transition-all duration-200' : ''}
                  />
                );
              } else {
                // 未折叠：显示所有单独的关系线
                linkGroup.forEach(({ link, sourceNode, targetNode }) => {
                  const sourceLayer = layers.get(sourceNode.id) || 0;
                  const targetLayer = layers.get(targetNode.id) || 0;
                  const sourceTypeNodes = nodesByLayerAndType.get(sourceLayer)?.get(sourceNode.type);
                  const targetTypeNodes = nodesByLayerAndType.get(targetLayer)?.get(targetNode.type);
                  
                  if (!sourceTypeNodes || !targetTypeNodes) return;
                  
                  const sourceIndex = sourceTypeNodes.findIndex(n => n.id === sourceNode.id);
                  const targetIndex = targetTypeNodes.findIndex(n => n.id === targetNode.id);
                  
                  if (sourceIndex === -1 || targetIndex === -1) return;

                  const sourceY = sourceBox.y + 50 + sourceIndex * (nodeItemHeight + nodeItemSpacing) + nodeItemHeight / 2;
                  const targetY = targetBox.y + 50 + targetIndex * (nodeItemHeight + nodeItemSpacing) + nodeItemHeight / 2;

                  const x1 = sourceBox.x + sourceBox.width;
                  const y1 = sourceY;
                  const x2 = targetBox.x;
                  const y2 = targetY;

                  const isHighlighted = highlightedLinks.has(link.id);
                  const isReverseHighlighted = reverseHighlightedLinks.has(link.id);
                  const linkColor = getLinkTypeColor(link.type);
                  
                  let strokeColor = linkColor;
                  let strokeWidth = 2;
                  if (isHighlighted) {
                    strokeColor = '#ef4444';
                    strokeWidth = 4;
                  } else if (isReverseHighlighted) {
                    strokeColor = '#3b82f6';
                    strokeWidth = 4;
                  }

                  renderedLines.push(
                    <line
                      key={link.id}
                      x1={x1}
                      y1={y1}
                      x2={x2}
                      y2={y2}
                      stroke={strokeColor}
                      strokeWidth={strokeWidth}
                      markerEnd="url(#arrowhead)"
                      opacity={isHighlighted || isReverseHighlighted ? 1 : 0.6}
                      className={(isHighlighted || isReverseHighlighted) ? 'transition-all duration-200' : ''}
                    />
                  );
                });
              }
            });
            
            return renderedLines;
          })()}
          
          {/* 为折叠的类型框绘制入口和出口标记点 */}
          {Array.from(nodesByLayerAndType.entries()).map(([layer, typeMap]) => {
            return Array.from(typeMap.entries()).map(([type]) => {
              const boxKey = `${layer}-${type}`;
              const boxPos = typeBoxPositions.get(boxKey);
              if (!boxPos || !collapsedTypes.has(boxKey)) return null;
              
              // 检查是否有进入的关系（作为目标）
              const hasIncomingLinks = links.some(link => {
                const targetNode = nodes.find(n => n.id === link.target);
                if (!targetNode) return false;
                const targetLayer = layers.get(targetNode.id) || 0;
                return `${targetLayer}-${targetNode.type}` === boxKey;
              });
              
              // 检查是否有出去的关系（作为源）
              const hasOutgoingLinks = links.some(link => {
                const sourceNode = nodes.find(n => n.id === link.source);
                if (!sourceNode) return false;
                const sourceLayer = layers.get(sourceNode.id) || 0;
                return `${sourceLayer}-${sourceNode.type}` === boxKey;
              });
              
              const centerY = boxPos.y + boxPos.height / 2;
              
              return (
                <g key={`markers-${boxKey}`}>
                  {/* 入口点（左侧） */}
                  {hasIncomingLinks && (
                    <circle
                      cx={boxPos.x}
                      cy={centerY}
                      r={6}
                      fill="#3b82f6"
                      stroke="#1e40af"
                      strokeWidth={2}
                    />
                  )}
                  {/* 出口点（右侧） */}
                  {hasOutgoingLinks && (
                    <circle
                      cx={boxPos.x + boxPos.width}
                      cy={centerY}
                      r={6}
                      fill="#10b981"
                      stroke="#059669"
                      strokeWidth={2}
                    />
                  )}
                </g>
              );
            });
          })}
          
          <defs>
            <marker
              id="arrowhead"
              markerWidth="10"
              markerHeight="10"
              refX="9"
              refY="3"
              orient="auto"
            >
              <polygon points="0 0, 10 3, 0 6" fill="#666" />
            </marker>
          </defs>
        </svg>

        {/* 渲染类型框 */}
        <div 
          className="relative" 
          style={{ width: totalWidth, height: totalHeight }}
        >
          {Array.from(nodesByLayerAndType.entries()).map(([layer, typeMap]) => (
            <div key={layer} className="absolute">
              {Array.from(typeMap.entries()).map(([type, typeNodes]) => {
                const boxKey = `${layer}-${type}`;
                const boxPos = typeBoxPositions.get(boxKey)!;
                const objectType = objectTypes.find(ot => ot.name === type);
                const displayName = objectType?.display_name || type;

                return (
                  <div
                    key={boxKey}
                    className="absolute bg-white border-2 border-gray-300 rounded-lg shadow-lg"
                    style={{
                      left: boxPos.x,
                      top: boxPos.y,
                      width: boxPos.width,
                      height: boxPos.height,
                    }}
                  >
                    {/* 类型标题 */}
                    <div
                      className="px-4 py-2 border-b border-gray-200 bg-gradient-to-r from-blue-50 to-indigo-50 rounded-t-lg cursor-pointer hover:from-blue-100 hover:to-indigo-100 transition-colors"
                      style={{ backgroundColor: nodeColor({ group: objectTypes.findIndex(ot => ot.name === type) || 0 } as GraphNode) + '20' }}
                      onClick={(e) => {
                        e.stopPropagation();
                        setTypeDetailDialog({ type, layer, nodes: typeNodes });
                        setTypeDetailViewMode('card'); // 默认使用卡片视图
                      }}
                      title="点击查看该类型的所有数据详情"
                    >
                      <div className="flex items-center justify-between">
                        <h3 className="font-semibold text-gray-900 text-sm">{displayName}</h3>
                        <span 
                          className="text-xs text-gray-500 bg-white px-2 py-1 rounded cursor-pointer hover:bg-gray-100 transition-colors"
                          onClick={(e) => {
                            e.stopPropagation();
                            const typeKey = `${layer}-${type}`;
                            setCollapsedTypes(prev => {
                              const newSet = new Set(prev);
                              if (newSet.has(typeKey)) {
                                newSet.delete(typeKey);
                              } else {
                                newSet.add(typeKey);
                              }
                              return newSet;
                            });
                          }}
                          title="点击折叠/展开数据条目"
                        >
                          {typeNodes.length}
                        </span>
                      </div>
                    </div>

                    {/* 节点列表 */}
                    {!collapsedTypes.has(`${layer}-${type}`) && (
                      <div className="overflow-y-auto" style={{ maxHeight: boxPos.height - 50 }}>
                      {typeNodes.map((node, index) => {
                        const nodeIdOnly = node.id.includes(':') ? node.id.split(':')[1] : node.id;
                        const isSelected = selectedNode?.id === node.id;
                        const isHighlighted = highlightedNodes.has(node.id);
                        const isReverseHighlighted = reverseHighlightedNodes.has(node.id);
                        
                        // 确定节点样式（优先级：选中 > 反向依赖高亮 > 正向影响高亮）
                        let nodeClassName = 'hover:bg-blue-50';
                        let textClassName = 'text-gray-800';
                        
                        if (isSelected) {
                          nodeClassName = 'bg-blue-100 border-blue-300 border-l-4';
                          textClassName = 'text-blue-900';
                        } else if (isReverseHighlighted) {
                          nodeClassName = 'bg-indigo-100 border-indigo-300 border-l-4 shadow-md';
                          textClassName = 'text-indigo-900';
                        } else if (isHighlighted) {
                          nodeClassName = 'bg-yellow-100 border-yellow-300 border-l-4 shadow-md';
                          textClassName = 'text-yellow-900';
                        }
                        
                        return (
                          <div
                            key={node.id}
                            onClick={() => handleNodeClickInHierarchical(node)}
                            onDoubleClick={(e) => {
                              e.stopPropagation();
                              handleNodeDoubleClickInHierarchical(node);
                            }}
                            className={`px-4 py-2 border-b border-gray-100 cursor-pointer transition-all duration-200 ${nodeClassName}`}
                            style={{
                              height: nodeItemHeight,
                              marginBottom: index < typeNodes.length - 1 ? nodeItemSpacing : 0,
                            }}
                            title="单击：高亮影响分析（后续节点）| 双击：高亮反向依赖（前驱节点）"
                          >
                            <div className="flex items-center justify-between">
                              <span className={`text-sm truncate flex-1 font-medium ${textClassName}`} title={node.name}>
                                {node.name || nodeIdOnly.substring(0, 16)}
                              </span>
                              <div
                                className={`w-3 h-3 rounded-full ml-2 flex-shrink-0 transition-all ${
                                  isHighlighted ? 'ring-2 ring-yellow-500 ring-offset-1' : 
                                  isReverseHighlighted ? 'ring-2 ring-indigo-500 ring-offset-1' : ''
                                }`}
                                style={{ backgroundColor: nodeColor(node) }}
                              />
                            </div>
                          </div>
                        );
                      })}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ))}
        </div>
      </div>
    );
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-600">加载图形数据...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="h-full flex flex-col">
      {/* 工具栏 */}
      <div className="bg-gradient-to-r from-blue-50 to-indigo-50 border-b border-gray-200 px-6 py-4 flex items-center justify-between shadow-sm">
        <div>
          <h2 className="text-xl font-bold text-gray-900 flex items-center">
            <CircleStackIcon className="w-6 h-6 mr-2 text-blue-600" />
            图形视图
          </h2>
          {objectType && instanceId ? (
            <p className="text-sm text-gray-600 mt-1">
              显示 <span className="font-semibold">{objectType}</span> 实例及其关系网络
            </p>
          ) : (
            <p className="text-sm text-gray-600 mt-1">
              可视化所有对象和关系
            </p>
          )}
        </div>
        <div className="flex items-center gap-3">
          <div className="bg-white rounded-lg px-3 py-1.5 border border-gray-200">
            <span className="text-xs text-gray-500">节点: </span>
            <span className="font-semibold text-gray-900">{nodes.length}</span>
            <span className="text-xs text-gray-500 ml-3">关系: </span>
            <span className="font-semibold text-gray-900">{links.length}</span>
          </div>
          <button
            onClick={() => setShowSettings(!showSettings)}
            className="flex items-center px-4 py-2 bg-gray-600 text-white rounded-lg hover:bg-gray-700 transition-colors text-sm font-medium shadow-sm"
            title="设置"
          >
            <Cog6ToothIcon className="w-4 h-4 mr-2" />
            设置
          </button>
          <button
            onClick={loadGraphData}
            className="flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium shadow-sm"
          >
            <ArrowPathIcon className="w-4 h-4 mr-2" />
            刷新
          </button>
          <button
            onClick={() => {
              if (!isFullscreen) {
                // 进入全屏
                if (fullscreenContainerRef.current) {
                  if (fullscreenContainerRef.current.requestFullscreen) {
                    fullscreenContainerRef.current.requestFullscreen();
                  } else if ((fullscreenContainerRef.current as any).webkitRequestFullscreen) {
                    (fullscreenContainerRef.current as any).webkitRequestFullscreen();
                  } else if ((fullscreenContainerRef.current as any).msRequestFullscreen) {
                    (fullscreenContainerRef.current as any).msRequestFullscreen();
                  }
                }
                setIsFullscreen(true);
                setFullscreenZoom(1);
                setFullscreenPan({ x: 0, y: 0 });
              } else {
                // 退出全屏
                if (document.exitFullscreen) {
                  document.exitFullscreen();
                } else if ((document as any).webkitExitFullscreen) {
                  (document as any).webkitExitFullscreen();
                } else if ((document as any).msExitFullscreen) {
                  (document as any).msExitFullscreen();
                }
                setIsFullscreen(false);
              }
            }}
            className="flex items-center px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors text-sm font-medium shadow-sm"
            title={isFullscreen ? "退出全屏" : "全屏"}
          >
            {isFullscreen ? (
              <ArrowsPointingInIcon className="w-4 h-4 mr-2" />
            ) : (
              <ArrowsPointingOutIcon className="w-4 h-4 mr-2" />
            )}
            {isFullscreen ? "退出全屏" : "全屏"}
          </button>
        </div>
        
        {/* 血缘查询模式选择（仅在单个实例视图时显示） */}
        {objectType && instanceId && (
          <div className="bg-white border-b border-gray-200 px-6 py-3 shadow-sm">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-700 mr-2">血缘查询模式：</span>
              <button
                onClick={() => setLineageMode('direct')}
                className={`flex items-center px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                  lineageMode === 'direct'
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
                title="直接关系：只显示直接连接的节点"
              >
                直接关系
              </button>
              <button
                onClick={() => setLineageMode('forward')}
                className={`flex items-center px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                  lineageMode === 'forward'
                    ? 'bg-green-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
                title="正向血缘：从当前节点向后递归查询所有下游节点"
              >
                <ArrowRightIcon className="w-4 h-4 mr-1" />
                正向血缘
              </button>
              <button
                onClick={() => setLineageMode('backward')}
                className={`flex items-center px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                  lineageMode === 'backward'
                    ? 'bg-orange-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
                title="反向血缘：从当前节点向前递归查询所有上游节点"
              >
                <ArrowLeftIcon className="w-4 h-4 mr-1" />
                反向血缘
              </button>
              <button
                onClick={() => setLineageMode('full')}
                className={`flex items-center px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                  lineageMode === 'full'
                    ? 'bg-purple-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
                title="全链血缘：从当前节点前后递归查询所有相关节点"
              >
                <ArrowsRightLeftIcon className="w-4 h-4 mr-1" />
                全链血缘
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 设置面板 */}
      {showSettings && (
        <div className="bg-white border-b border-gray-200 px-6 py-4 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-semibold text-gray-900">图形视图设置</h3>
            <button
              onClick={() => setShowSettings(false)}
              className="text-gray-400 hover:text-gray-600"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              视图模式
            </label>
            <div className="flex gap-2">
              <button
                onClick={() => {
                  setViewMode('force');
                  localStorage.setItem('graphView_viewMode', 'force');
                  setHighlightedNodes(new Set());
                  setHighlightedLinks(new Set());
                  setReverseHighlightedNodes(new Set());
                  setReverseHighlightedLinks(new Set());
                }}
                className={`flex items-center px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                  viewMode === 'force'
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
                title="力导向图：使用物理模拟展示节点关系"
              >
                <LinkIcon className="w-4 h-4 mr-2" />
                力导向图
              </button>
              <button
                onClick={() => {
                  setViewMode('hierarchical');
                  localStorage.setItem('graphView_viewMode', 'hierarchical');
                  setHighlightedNodes(new Set());
                  setHighlightedLinks(new Set());
                  setReverseHighlightedNodes(new Set());
                  setReverseHighlightedLinks(new Set());
                }}
                className={`flex items-center px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                  viewMode === 'hierarchical'
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
                title="分层视图：按层级关系从左到右展示"
              >
                <Squares2X2Icon className="w-4 h-4 mr-2" />
                分层视图
              </button>
            </div>
          </div>
          
          {/* 力导向图布局选项（仅在力导向图模式下显示） */}
          {viewMode === 'force' && (
            <div className="mb-4 border-t border-gray-200 pt-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                自动排布选项
              </label>
              <div className="flex flex-wrap gap-2 mb-3">
                <button
                  onClick={() => {
                    setLayoutType('force');
                    localStorage.setItem('graphView_layoutType', 'force');
                    releaseFixedPositions();
                  }}
                  className={`flex items-center px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                    layoutType === 'force'
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                  title="力导向布局：使用物理模拟自动排布"
                >
                  <LinkIcon className="w-4 h-4 mr-2" />
                  力导向
                </button>
                <button
                  onClick={() => {
                    setLayoutType('circle');
                    localStorage.setItem('graphView_layoutType', 'circle');
                    setTimeout(() => applyCircleLayout(), 100);
                  }}
                  className={`flex items-center px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                    layoutType === 'circle'
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                  title="圆形布局：将节点均匀分布在圆周上"
                >
                  <SparklesIcon className="w-4 h-4 mr-2" />
                  圆形布局
                </button>
                <button
                  onClick={() => {
                    setLayoutType('grid');
                    localStorage.setItem('graphView_layoutType', 'grid');
                    setTimeout(() => applyGridLayout(), 100);
                  }}
                  className={`flex items-center px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                    layoutType === 'grid'
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                  title="网格布局：将节点排列成网格状"
                >
                  <TableCellsIcon className="w-4 h-4 mr-2" />
                  网格布局
                </button>
                <button
                  onClick={() => {
                    setLayoutType('radial');
                    localStorage.setItem('graphView_layoutType', 'radial');
                    setTimeout(() => applyRadialLayout(), 100);
                  }}
                  className={`flex items-center px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                    layoutType === 'radial'
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                  title="径向布局：以中心节点为圆心，其他节点按层级分布"
                >
                  <CircleStackIcon className="w-4 h-4 mr-2" />
                  径向布局
                </button>
                <button
                  onClick={() => {
                    if (layoutType === 'circle') {
                      applyCircleLayout();
                    } else if (layoutType === 'grid') {
                      applyGridLayout();
                    } else if (layoutType === 'radial') {
                      applyRadialLayout();
                    } else {
                      releaseFixedPositions();
                      configureForceGraph();
                      if (fgRef.current) {
                        fgRef.current.d3ReheatSimulation();
                      }
                    }
                  }}
                  className="flex items-center px-4 py-2 rounded-lg text-sm font-medium transition-colors bg-green-600 text-white hover:bg-green-700"
                  title="重新应用当前布局"
                >
                  <ArrowPathIcon className="w-4 h-4 mr-2" />
                  重新布局
                </button>
              </div>
              
              {/* 力导向参数预设（仅在力导向布局时显示） */}
              {layoutType === 'force' && (
                <div className="mb-3">
                  <label className="block text-xs font-medium text-gray-600 mb-2">
                    快速预设
                  </label>
                  <div className="flex flex-wrap gap-2">
                    <button
                      onClick={() => applyForcePreset('uniform')}
                      className="px-3 py-1.5 text-xs font-medium rounded-lg bg-indigo-100 text-indigo-700 hover:bg-indigo-200 transition-colors"
                      title="均匀分布：节点分布更均匀，便于观察"
                    >
                      均匀分布
                    </button>
                    <button
                      onClick={() => applyForcePreset('compact')}
                      className="px-3 py-1.5 text-xs font-medium rounded-lg bg-purple-100 text-purple-700 hover:bg-purple-200 transition-colors"
                      title="紧凑：节点更紧密排列"
                    >
                      紧凑
                    </button>
                    <button
                      onClick={() => applyForcePreset('spread')}
                      className="px-3 py-1.5 text-xs font-medium rounded-lg bg-pink-100 text-pink-700 hover:bg-pink-200 transition-colors"
                      title="分散：节点更分散，减少重叠"
                    >
                      分散
                    </button>
                    <button
                      onClick={() => applyForcePreset('default')}
                      className="px-3 py-1.5 text-xs font-medium rounded-lg bg-gray-100 text-gray-700 hover:bg-gray-200 transition-colors"
                      title="恢复默认参数"
                    >
                      默认
                    </button>
                  </div>
                </div>
              )}
              
              {/* 力导向参数调整 */}
              {layoutType === 'force' && (
                <div className="grid grid-cols-2 gap-4 mt-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      连接距离: {forceParams.linkDistance}
                    </label>
                    <input
                      type="range"
                      min="30"
                      max="200"
                      step="10"
                      value={forceParams.linkDistance}
                      onChange={(e) => {
                        const newParams = { ...forceParams, linkDistance: parseInt(e.target.value, 10) };
                        setForceParams(newParams);
                        localStorage.setItem('graphView_forceParams', JSON.stringify(newParams));
                        configureForceGraph();
                      }}
                      className="w-full"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      电荷强度: {forceParams.chargeStrength}
                    </label>
                    <input
                      type="range"
                      min="-1000"
                      max="-50"
                      step="50"
                      value={forceParams.chargeStrength}
                      onChange={(e) => {
                        const newParams = { ...forceParams, chargeStrength: parseInt(e.target.value, 10) };
                        setForceParams(newParams);
                        localStorage.setItem('graphView_forceParams', JSON.stringify(newParams));
                        configureForceGraph();
                      }}
                      className="w-full"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      中心力强度: {forceParams.centerStrength.toFixed(2)}
                    </label>
                    <input
                      type="range"
                      min="0"
                      max="1"
                      step="0.1"
                      value={forceParams.centerStrength}
                      onChange={(e) => {
                        const newParams = { ...forceParams, centerStrength: parseFloat(e.target.value) };
                        setForceParams(newParams);
                        localStorage.setItem('graphView_forceParams', JSON.stringify(newParams));
                        configureForceGraph();
                      }}
                      className="w-full"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      速度衰减: {forceParams.velocityDecay.toFixed(2)}
                    </label>
                    <input
                      type="range"
                      min="0.1"
                      max="0.9"
                      step="0.1"
                      value={forceParams.velocityDecay}
                      onChange={(e) => {
                        const newParams = { ...forceParams, velocityDecay: parseFloat(e.target.value) };
                        setForceParams(newParams);
                        localStorage.setItem('graphView_forceParams', JSON.stringify(newParams));
                      }}
                      className="w-full"
                    />
                  </div>
                </div>
              )}
            </div>
          )}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                最大节点数
              </label>
              <input
                type="number"
                min="100"
                max="50000"
                step="100"
                value={maxNodes}
                onChange={(e) => {
                  const value = parseInt(e.target.value, 10);
                  if (!isNaN(value) && value > 0) {
                    setMaxNodes(value);
                    localStorage.setItem('graphView_maxNodes', value.toString());
                  }
                }}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
              <p className="text-xs text-gray-500 mt-1">
                当节点数超过此限制时，将优先保留连接数高的节点
              </p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                每个关系类型最大关系数
              </label>
              <input
                type="number"
                min="100"
                max="100000"
                step="100"
                value={maxLinksPerType}
                onChange={(e) => {
                  const value = parseInt(e.target.value, 10);
                  if (!isNaN(value) && value > 0) {
                    setMaxLinksPerType(value);
                    localStorage.setItem('graphView_maxLinksPerType', value.toString());
                  }
                }}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
              <p className="text-xs text-gray-500 mt-1">
                每个关系类型最多加载的关系数量
              </p>
            </div>
          </div>
          <div className="mt-4 flex items-center justify-between">
            <button
              onClick={() => {
                const isWorkspaceMode = selectedWorkspace && 
                  ((selectedWorkspace.object_types && selectedWorkspace.object_types.length > 0) ||
                   (selectedWorkspace.link_types && selectedWorkspace.link_types.length > 0));
                const defaultNodes = isWorkspaceMode ? 5000 : 1000;
                const defaultLinks = isWorkspaceMode ? 10000 : 500;
                setMaxNodes(defaultNodes);
                setMaxLinksPerType(defaultLinks);
                localStorage.setItem('graphView_maxNodes', defaultNodes.toString());
                localStorage.setItem('graphView_maxLinksPerType', defaultLinks.toString());
              }}
              className="px-4 py-2 text-sm text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
            >
              恢复默认值
            </button>
            <div className="text-sm text-gray-500">
              <span>当前节点: {nodes.length} / {maxNodes}</span>
              <span className="ml-4">当前关系: {links.length}</span>
            </div>
          </div>
        </div>
      )}

      {/* 图形区域 */}
      <div 
        ref={fullscreenContainerRef}
        className={`flex-1 relative bg-white ${isFullscreen ? 'fixed inset-0 z-50' : ''}`}
        style={isFullscreen ? {
          transform: `translate(${fullscreenPan.x}px, ${fullscreenPan.y}px) scale(${fullscreenZoom})`,
          transformOrigin: 'center center',
          cursor: isPanning ? 'grabbing' : 'grab'
        } : {}}
      >
        {nodes.length === 0 ? (
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="text-center">
              <p className="text-gray-500 mb-2">没有数据可显示</p>
              <button
                onClick={loadGraphData}
                className="text-blue-600 hover:text-blue-800 text-sm"
              >
                点击加载数据
              </button>
            </div>
          </div>
        ) : viewMode === 'hierarchical' ? (
          <HierarchicalView />
        ) : (
          <ForceGraph2D
            graphData={graphData}
            nodeLabel={getNodeLabel}
            nodeColor={nodeColor}
            nodeVal={(node: any) => {
              // 根据连接数设置节点大小，确保节点足够大可见
              const linkCount = links.filter(
                l => l.source === node.id || l.target === node.id
              ).length;
              return 12 + Math.min(linkCount * 2, 25);
            }}
            nodeCanvasObject={(node: any, ctx: CanvasRenderingContext2D, globalScale: number) => {
              // node.id 现在是 type:id 格式，提取ID部分用于显示
              const nodeIdOnly = node.id.includes(':') ? node.id.split(':')[1] : node.id;
              const label = node.name || nodeIdOnly.substring(0, 8);
              const size = node.__size || 12;
              
              // 绘制节点圆形
              ctx.beginPath();
              ctx.arc(node.x || 0, node.y || 0, size, 0, 2 * Math.PI, false);
              ctx.fillStyle = nodeColor(node);
              ctx.fill();
              
              // 添加白色边框
              ctx.strokeStyle = '#ffffff';
              ctx.lineWidth = 2 / globalScale;
              ctx.stroke();
              
              // 绘制标签文字
              const fontSize = 12 / globalScale;
              if (fontSize > 4) { // 只在缩放级别合适时显示文字
                ctx.font = `bold ${fontSize}px Sans-Serif`;
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                ctx.fillStyle = '#ffffff';
                ctx.strokeStyle = '#1f2937';
                ctx.lineWidth = 3 / globalScale;
                // 添加深色描边，提高可读性
                ctx.strokeText(label, node.x || 0, node.y || 0);
                ctx.fillText(label, node.x || 0, node.y || 0);
              }
            }}
            linkLabel={getLinkLabel}
            linkDirectionalArrowLength={8}
            linkDirectionalArrowRelPos={1}
            linkWidth={() => {
              // 根据关系类型设置线条粗细
              return 2.5;
            }}
            linkColor={(link: any) => {
              // 根据关系类型设置颜色，每个关系类型使用不同颜色
              return getLinkTypeColor(link.type);
            }}
            ref={fgRef}
            linkCurvature={0.1}
            linkDirectionalParticles={3}
            linkDirectionalParticleSpeed={0.015}
            linkDirectionalParticleWidth={4}
            onNodeClick={handleNodeClick}
            onBackgroundClick={handleBackgroundClick}
            onNodeHover={(node: any) => {
              // 鼠标悬停时高亮节点
              if (node) {
                document.body.style.cursor = 'pointer';
              } else {
                document.body.style.cursor = 'default';
              }
            }}
            onLinkHover={(link: any) => {
              // 鼠标悬停时高亮关系
              document.body.style.cursor = link ? 'pointer' : 'default';
            }}
            cooldownTicks={100}
            d3AlphaDecay={forceParams.alphaDecay}
            d3VelocityDecay={forceParams.velocityDecay}
            onEngineStop={() => {
              // 图形稳定后停止
            }}
          />
        )}

        {/* 节点详情面板 - 侧边抽屉 */}
        {selectedNode && (
          <div className="absolute top-0 right-0 h-full w-96 bg-white shadow-2xl border-l border-gray-200 z-10 flex flex-col">
            {/* 头部 */}
            <div className="flex items-center justify-between p-6 border-b border-gray-200 bg-gradient-to-r from-blue-50 to-indigo-50">
              <div className="flex-1">
                <h3 className="text-xl font-bold text-gray-900 mb-2">{selectedNode.name}</h3>
                <span className="inline-block px-3 py-1 text-sm font-semibold rounded-md bg-blue-600 text-white">
                  {objectTypes.find(ot => ot.name === selectedNode.type)?.display_name || selectedNode.type}
                </span>
              </div>
              <button
                onClick={() => setSelectedNode(null)}
                className="ml-4 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-full p-2 transition-colors"
                aria-label="关闭"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* 内容区域 - 可滚动 */}
            <div className="flex-1 overflow-y-auto p-6">
              <div className="space-y-4">
                {/* ID信息 */}
                <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                  <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">实例ID</div>
                  <div className="font-mono text-sm text-gray-800 break-all bg-white p-2 rounded border">
                    {selectedNode.id.includes(':') ? selectedNode.id.split(':')[1] : selectedNode.id}
                  </div>
                </div>

                {/* 属性列表 */}
                <div>
                  <h4 className="text-sm font-semibold text-gray-700 mb-3 uppercase tracking-wide">属性信息</h4>
                  <div className="space-y-3">
                    {Object.entries(selectedNode.data)
                      .filter(([key]) => !['id', 'created_at', 'updated_at'].includes(key))
                      .map(([key, value]) => (
                        <div key={key} className="bg-white border border-gray-200 rounded-lg p-4 hover:border-blue-300 transition-colors">
                          <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">{key}</div>
                          <div className="text-base font-medium text-gray-900 break-words">
                            {typeof value === 'object' && value !== null
                              ? (
                                  <pre className="text-sm bg-gray-50 p-3 rounded border overflow-x-auto whitespace-pre-wrap">
                                    {JSON.stringify(value, null, 2)}
                                  </pre>
                                )
                              : (
                                  <span className="text-gray-900">{String(value || '-')}</span>
                                )}
                          </div>
                        </div>
                      ))}
                  </div>
                </div>

                {/* 时间信息 */}
                {selectedNode.data.created_at && (
                  <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                    <div className="grid grid-cols-2 gap-4 text-sm">
                      {selectedNode.data.created_at && (
                        <div>
                          <div className="text-xs font-semibold text-gray-500 mb-1">创建时间</div>
                          <div className="text-gray-900">{new Date(selectedNode.data.created_at).toLocaleString('zh-CN')}</div>
                        </div>
                      )}
                      {selectedNode.data.updated_at && (
                        <div>
                          <div className="text-xs font-semibold text-gray-500 mb-1">更新时间</div>
                          <div className="text-gray-900">{new Date(selectedNode.data.updated_at).toLocaleString('zh-CN')}</div>
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* 底部操作按钮 */}
            <div className="p-6 border-t border-gray-200 bg-gray-50">
              <div className="flex gap-3">
                <a
                  href={`#/instances/${selectedNode.type}/${selectedNode.id.includes(':') ? selectedNode.id.split(':')[1] : selectedNode.id}`}
                  className="flex-1 text-center px-4 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium shadow-sm"
                >
                  查看详情
                </a>
                <a
                  href={`#/graph/${selectedNode.type}/${selectedNode.id.includes(':') ? selectedNode.id.split(':')[1] : selectedNode.id}`}
                  className="flex-1 text-center px-4 py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors font-medium shadow-sm"
                >
                  关系图
                </a>
              </div>
            </div>
          </div>
        )}

        {/* 图例和统计信息 - 可拖动悬浮面板 */}
        <div
          ref={legendRef}
          className="absolute bg-white rounded-xl shadow-xl border border-gray-200 max-w-xs max-h-[calc(100vh-200px)] overflow-hidden select-none z-20"
          style={{
            left: `${legendPosition.x}px`,
            top: `${legendPosition.y}px`,
            cursor: isDraggingLegend ? 'grabbing' : 'default',
          }}
        >
          {/* 拖动手柄 - 标题栏 */}
          <div
            onMouseDown={handleLegendMouseDown}
            className="px-4 py-2 border-b border-gray-200 bg-gradient-to-r from-gray-50 to-gray-100 cursor-grab active:cursor-grabbing hover:from-gray-100 hover:to-gray-200 transition-colors"
          >
            <div className="flex items-center justify-between">
              <h4 className="text-sm font-bold text-gray-900">图例与统计</h4>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setIsLegendCollapsed(!isLegendCollapsed);
                }}
                className="flex items-center gap-1 text-gray-400 hover:text-gray-600 transition-colors cursor-pointer"
                title={isLegendCollapsed ? "展开" : "折叠"}
              >
                <EllipsisVerticalIcon className="w-4 h-4" />
              </button>
            </div>
          </div>
          
          {/* 内容区域 - 可滚动 */}
          {!isLegendCollapsed && (
            <div className="p-4 overflow-y-auto max-h-[calc(100vh-300px)]">
              <div className="space-y-3 text-xs">
                <div>
                  <div className="text-gray-500 mb-2">节点类型</div>
                  <div className="space-y-1.5">
                    {Array.from(new Set(nodes.map(n => n.type))).map((type, idx) => {
                      const typeNodes = nodes.filter(n => n.type === type);
                      const color = nodeColor({ group: idx } as GraphNode);
                      const objectType = objectTypes.find(ot => ot.name === type);
                      const displayName = objectType?.display_name || type;
                      return (
                        <div key={type} className="flex items-center justify-between">
                          <div className="flex items-center">
                            <div 
                              className="w-4 h-4 rounded-full mr-2 border-2 border-gray-300"
                              style={{ backgroundColor: color }}
                            ></div>
                            <span className="text-gray-700 font-medium">{displayName}</span>
                          </div>
                          <span className="text-gray-500">{typeNodes.length}</span>
                        </div>
                      );
                    })}
                  </div>
                </div>
                <div className="border-t border-gray-200 pt-3">
                  <div className="text-gray-500 mb-2">关系类型</div>
                  <div className="space-y-1.5">
                    {Array.from(new Set(links.map(l => l.type))).map((type) => {
                      const typeLinks = links.filter(l => l.type === type);
                      const color = getLinkTypeColor(type);
                      const linkType = linkTypes.find(lt => lt.name === type);
                      const displayName = linkType?.display_name || type;
                      return (
                        <div key={type} className="flex items-center justify-between">
                          <div className="flex items-center">
                            <div 
                              className="w-6 h-0.5 mr-2"
                              style={{ backgroundColor: color }}
                            ></div>
                            <span className="text-gray-700 font-medium">{displayName}</span>
                          </div>
                          <span className="text-gray-500">{typeLinks.length}</span>
                        </div>
                      );
                    })}
                  </div>
                </div>
                <div className="border-t border-gray-200 pt-3 space-y-1 text-gray-500">
                  <p>💡 点击节点查看详情</p>
                  <p>🖱️ 拖拽节点移动位置</p>
                  <p>🔍 鼠标悬停查看标签</p>
                  {viewMode === 'hierarchical' && (
                    <>
                      <p className="mt-2 pt-2 border-t border-gray-200">分层视图：</p>
                      <p>🖱️ 单击节点：高亮影响分析（黄色）</p>
                      <p>🖱️ 双击节点：高亮反向依赖（蓝色）</p>
                      <p>📋 点击类型标题：查看该类型所有数据详情</p>
                    </>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* 类型详情面板 - 侧边抽屉 */}
        {typeDetailDialog && (
          <div 
            className="absolute top-0 right-0 h-full bg-white shadow-2xl border-l border-gray-200 z-10 flex flex-col"
            style={{ width: `${typeDetailDrawerWidth}px` }}
          >
          {/* 头部 */}
          <div className="flex items-center justify-between p-6 border-b border-gray-200 bg-gradient-to-r from-blue-50 to-indigo-50">
            <div className="flex-1">
              <h3 className="text-xl font-bold text-gray-900 mb-2">
                {objectTypes.find(ot => ot.name === typeDetailDialog.type)?.display_name || typeDetailDialog.type}
              </h3>
              <div className="flex items-center gap-2 mb-2">
                <span className="inline-block px-3 py-1 text-sm font-semibold rounded-md bg-blue-600 text-white">
                  类型详情
                </span>
                <span className="text-sm text-gray-600">
                  层级 {typeDetailDialog.layer} · 共 {typeDetailDialog.nodes.length} 条
                </span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setTypeDetailViewMode(typeDetailViewMode === 'card' ? 'table' : 'card')}
                  className="flex items-center gap-1.5 px-3 py-1.5 bg-white border border-gray-300 rounded-md hover:bg-gray-50 transition-colors text-xs font-medium text-gray-700"
                  title={typeDetailViewMode === 'card' ? '切换到表格视图' : '切换到卡片视图'}
                >
                  <TableCellsIcon className="w-4 h-4" />
                  <span>{typeDetailViewMode === 'card' ? '列表' : '卡片'}</span>
                </button>
              </div>
            </div>
            <button
              onClick={() => setTypeDetailDialog(null)}
              className="ml-4 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-full p-2 transition-colors"
              aria-label="关闭"
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* 内容区域 - 可滚动 */}
          <div ref={typeDetailContentRef} className="flex-1 overflow-y-auto p-6">
            <div className="space-y-4">
              {typeDetailViewMode === 'card' ? (
                // 卡片视图
                typeDetailDialog.nodes.map((node, index) => {
                  const nodeIdOnly = node.id.includes(':') ? node.id.split(':')[1] : node.id;
                  const isSelected = selectedNode?.id === node.id;
                  const isHighlighted = highlightedNodes.has(node.id);
                  const isReverseHighlighted = reverseHighlightedNodes.has(node.id);

                  return (
                    <div
                      key={node.id}
                      className={`bg-white border-2 rounded-lg p-4 hover:shadow-md transition-all ${
                        isSelected
                          ? 'border-blue-500 bg-blue-50'
                          : isReverseHighlighted
                          ? 'border-indigo-500 bg-indigo-50'
                          : isHighlighted
                          ? 'border-yellow-500 bg-yellow-50'
                          : 'border-gray-200'
                      }`}
                    >
                      {/* 节点头部 */}
                      <div className="flex items-center justify-between mb-3 pb-3 border-b border-gray-200">
                        <div className="flex items-center gap-3">
                          <div
                            className="w-4 h-4 rounded-full"
                            style={{ backgroundColor: nodeColor(node) }}
                          />
                          <h4 className="font-semibold text-base text-gray-900">{node.name || nodeIdOnly.substring(0, 16)}</h4>
                          <span className="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded">
                            #{index + 1}
                          </span>
                        </div>
                      </div>

                      {/* ID信息 */}
                      <div className="bg-gray-50 rounded-lg p-4 border border-gray-200 mb-3">
                        <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">实例ID</div>
                        <div className="font-mono text-sm text-gray-800 break-all bg-white p-2 rounded border">
                          {nodeIdOnly}
                        </div>
                      </div>

                      {/* 类型信息 */}
                      <div className="bg-gray-50 rounded-lg p-4 border border-gray-200 mb-3">
                        <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">类型</div>
                        <div className="text-sm text-gray-800 bg-white p-2 rounded border">
                          {objectTypes.find(ot => ot.name === node.type)?.display_name || node.type}
                        </div>
                      </div>

                      {/* 属性列表 */}
                      <div>
                        <h4 className="text-sm font-semibold text-gray-700 mb-3 uppercase tracking-wide">属性信息</h4>
                        <div className="space-y-3">
                          {Object.entries(node.data)
                            .filter(([key]) => !['id', 'created_at', 'updated_at'].includes(key))
                            .slice(0, 6) // 只显示前6个属性
                            .map(([key, value]) => (
                              <div key={key} className="bg-white border border-gray-200 rounded-lg p-4 hover:border-blue-300 transition-colors">
                                <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">{key}</div>
                                <div className="text-base font-medium text-gray-900 break-words">
                                  {typeof value === 'object' && value !== null
                                    ? (
                                        <pre className="text-sm bg-gray-50 p-3 rounded border overflow-x-auto whitespace-pre-wrap">
                                          {JSON.stringify(value, null, 2)}
                                        </pre>
                                      )
                                    : (
                                        <span className="text-gray-900">{String(value || '-')}</span>
                                      )}
                                </div>
                              </div>
                            ))}
                        </div>
                        {Object.keys(node.data).filter(key => !['id', 'created_at', 'updated_at'].includes(key)).length > 6 && (
                          <div className="mt-2 text-xs text-gray-500 text-center">
                            还有 {Object.keys(node.data).filter(key => !['id', 'created_at', 'updated_at'].includes(key)).length - 6} 个属性...
                          </div>
                        )}
                      </div>

                      {/* 时间信息 */}
                      {(node.data.created_at || node.data.updated_at) && (
                        <div className="bg-gray-50 rounded-lg p-4 border border-gray-200 mt-3">
                          <div className="grid grid-cols-2 gap-4 text-sm">
                            {node.data.created_at && (
                              <div>
                                <div className="text-xs font-semibold text-gray-500 mb-1">创建时间</div>
                                <div className="text-gray-900">{new Date(node.data.created_at).toLocaleString('zh-CN')}</div>
                              </div>
                            )}
                            {node.data.updated_at && (
                              <div>
                                <div className="text-xs font-semibold text-gray-500 mb-1">更新时间</div>
                                <div className="text-gray-900">{new Date(node.data.updated_at).toLocaleString('zh-CN')}</div>
                              </div>
                            )}
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })
              ) : (
                // 表格视图
                (() => {
                  // 收集所有节点的所有属性键
                  const allKeys = new Set<string>();
                  typeDetailDialog.nodes.forEach(node => {
                    Object.keys(node.data).forEach(key => allKeys.add(key));
                  });
                  
                  // 定义固定列（序号、名称、ID、类型）
                  const fixedColumns = ['序号', '名称', 'ID', '类型'];
                  // 其他属性列（排除固定列和系统字段）
                  const otherKeys = Array.from(allKeys)
                    .filter(key => !['id', 'name', 'created_at', 'updated_at'].includes(key))
                    .sort();
                  
                  // 合并所有列
                  const allColumns = [...fixedColumns, ...otherKeys, '创建时间', '更新时间'];
                  
                  return (
                    <div className="overflow-x-auto">
                      <table className="min-w-full divide-y divide-gray-200 border border-gray-300">
                        <thead className="bg-gray-50 sticky top-0 z-10">
                          <tr>
                            {allColumns.map((column, colIndex) => (
                              <th
                                key={column}
                                className={`px-4 py-3 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider border-b border-gray-300 whitespace-nowrap ${
                                  colIndex < allColumns.length - 1 ? 'border-r border-gray-200' : ''
                                }`}
                              >
                                {column}
                              </th>
                            ))}
                          </tr>
                        </thead>
                        <tbody className="bg-white divide-y divide-gray-200">
                          {typeDetailDialog.nodes.map((node, index) => {
                            const nodeIdOnly = node.id.includes(':') ? node.id.split(':')[1] : node.id;
                            const isSelected = selectedNode?.id === node.id;
                            const isHighlighted = highlightedNodes.has(node.id);
                            const isReverseHighlighted = reverseHighlightedNodes.has(node.id);
                            
                            // 确定行样式
                            let rowClassName = '';
                            if (isSelected) {
                              rowClassName = 'bg-blue-50';
                            } else if (isReverseHighlighted) {
                              rowClassName = 'bg-indigo-50';
                            } else if (isHighlighted) {
                              rowClassName = 'bg-yellow-50';
                            }
                            
                            return (
                              <tr key={node.id} className={rowClassName}>
                                {/* 序号 */}
                                <td className="px-4 py-3 text-sm text-gray-900 border-r border-gray-200">
                                  {index + 1}
                                </td>
                                
                                {/* 名称 */}
                                <td className="px-4 py-3 text-sm text-gray-900 border-r border-gray-200">
                                  <div className="flex items-center gap-2">
                                    <div
                                      className="w-3 h-3 rounded-full flex-shrink-0"
                                      style={{ backgroundColor: nodeColor(node) }}
                                    />
                                    <span className="font-medium">{node.name || nodeIdOnly.substring(0, 16)}</span>
                                  </div>
                                </td>
                                
                                {/* ID */}
                                <td className="px-4 py-3 text-sm font-mono text-gray-800 border-r border-gray-200">
                                  {nodeIdOnly}
                                </td>
                                
                                {/* 类型 */}
                                <td className="px-4 py-3 text-sm text-gray-900 border-r border-gray-200">
                                  {objectTypes.find(ot => ot.name === node.type)?.display_name || node.type}
                                </td>
                                
                                {/* 其他属性列 */}
                                {otherKeys.map((key) => {
                                  const value = node.data[key];
                                  return (
                                    <td
                                      key={key}
                                      className="px-4 py-3 text-sm text-gray-900 border-r border-gray-200 max-w-xs"
                                    >
                                      <div className="truncate" title={typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value || '-')}>
                                        {typeof value === 'object' && value !== null
                                          ? JSON.stringify(value)
                                          : String(value || '-')}
                                      </div>
                                    </td>
                                  );
                                })}
                                
                                {/* 创建时间 */}
                                <td className="px-4 py-3 text-sm text-gray-600 border-r border-gray-200 whitespace-nowrap">
                                  {node.data.created_at
                                    ? new Date(node.data.created_at).toLocaleString('zh-CN')
                                    : '-'}
                                </td>
                                
                                {/* 更新时间 */}
                                <td className="px-4 py-3 text-sm text-gray-600 whitespace-nowrap">
                                  {node.data.updated_at
                                    ? new Date(node.data.updated_at).toLocaleString('zh-CN')
                                    : '-'}
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  );
                })()
              )}
            </div>
          </div>

          {/* 底部统计信息 */}
          <div className="p-6 border-t border-gray-200 bg-gray-50">
            <div className="text-sm text-gray-600">
              共显示 <span className="font-semibold text-gray-900">{typeDetailDialog.nodes.length}</span> 条数据
            </div>
          </div>
        </div>
        )}
      </div>
    </div>
  );
}

