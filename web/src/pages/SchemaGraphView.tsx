import { useEffect, useState, useCallback, useRef } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import type { ObjectType, LinkType } from '../api/client';
import { schemaApi } from '../api/client';
import { useWorkspace } from '../WorkspaceContext';
import { 
  ArrowPathIcon,
  CubeIcon
} from '@heroicons/react/24/outline';

interface SchemaNode {
  id: string;
  name: string;
  description: string;
  type: 'object_type';
  data: ObjectType;
  group?: number;
}

interface SchemaLink {
  source: SchemaNode | string; // 可以是节点对象引用或字符串 ID
  target: SchemaNode | string; // 可以是节点对象引用或字符串 ID
  id: string;
  name: string;
  description: string;
  cardinality: string;
  direction: string;
  data: LinkType;
}

// 节点颜色映射
const nodeColors = [
  '#3b82f6', // blue
  '#10b981', // green
  '#f59e0b', // orange
  '#ef4444', // red
  '#8b5cf6', // purple
  '#ec4899', // pink
  '#06b6d4', // cyan
  '#84cc16', // lime
];

// 根据对象类型名称生成颜色
const getNodeColor = (name: string): string => {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return nodeColors[Math.abs(hash) % nodeColors.length];
};

export default function SchemaGraphView() {
  const { selectedWorkspace } = useWorkspace();
  const [nodes, setNodes] = useState<SchemaNode[]>([]);
  const [links, setLinks] = useState<SchemaLink[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedNode, setSelectedNode] = useState<SchemaNode | null>(null);
  const [selectedLink, setSelectedLink] = useState<SchemaLink | null>(null);
  const [graphData, setGraphData] = useState({ nodes, links });
  const fgRef = useRef<any>(null);

  useEffect(() => {
    loadSchemaGraph();
  }, [selectedWorkspace]);

  const loadSchemaGraph = async () => {
    try {
      setLoading(true);
      
      // 加载所有对象类型和关系类型
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

      // 创建节点（对象类型）
      const schemaNodes: SchemaNode[] = objectTypes.map((ot, index) => ({
        id: ot.name,
        name: ot.display_name || ot.name,
        description: ot.description || '',
        type: 'object_type',
        data: ot,
        group: index % nodeColors.length,
      }));

      // 创建边（关系类型）
      // 注意：source 和 target 必须是节点对象的引用，而不是字符串 ID
      const schemaLinks: SchemaLink[] = linkTypes
        .map((lt) => {
          // 查找源和目标节点对象
          const sourceNode = schemaNodes.find(n => n.id === lt.source_type);
          const targetNode = schemaNodes.find(n => n.id === lt.target_type);
          
          if (!sourceNode || !targetNode) {
            console.warn(`Link ${lt.name} references non-existent node: ${lt.source_type} -> ${lt.target_type}`);
            return null;
          }

          return {
            source: sourceNode, // 使用节点对象引用，而不是字符串 ID
            target: targetNode, // 使用节点对象引用，而不是字符串 ID
            id: `${lt.name}_${lt.source_type}_${lt.target_type}`,
            name: lt.display_name || lt.name,
            description: lt.description || '',
            cardinality: lt.cardinality,
            direction: lt.direction,
            data: lt,
          } as SchemaLink;
        })
        .filter((link): link is SchemaLink => link !== null);

      setNodes(schemaNodes);
      setLinks(schemaLinks);
      setGraphData({ nodes: schemaNodes, links: schemaLinks });
    } catch (error) {
      console.error('Failed to load schema graph:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleNodeClick = useCallback((node: SchemaNode) => {
    setSelectedNode(node);
    setSelectedLink(null);
  }, []);

  const handleLinkClick = useCallback((link: SchemaLink) => {
    setSelectedLink(link);
    setSelectedNode(null);
  }, []);

  const handleBackgroundClick = useCallback(() => {
    setSelectedNode(null);
    setSelectedLink(null);
  }, []);

  // 处理节点拖动
  const handleNodeDrag = useCallback((node: any) => {
    // 更新节点的位置，使关系线能够跟随
    node.fx = node.x;
    node.fy = node.y;
    
    // 直接更新 graphData 中的节点对象，确保关系线能够实时跟随
    // ForceGraph2D 会自动重新渲染，linkCanvasObject 会使用最新的节点位置
    setGraphData(prevData => {
      const updatedNodes = prevData.nodes.map((n: any) => {
        if (n.id === node.id) {
          // 更新被拖动的节点
          n.x = node.x;
          n.y = node.y;
          n.fx = node.x;
          n.fy = node.y;
          return n;
        }
        return n;
      });
      
      // 更新 links 中的节点引用，确保关系线能够实时跟随
      // 如果 link 的 source/target 是节点对象，需要更新引用
      const updatedLinks = prevData.links.map((link: any) => {
        // 如果 source 是节点对象且是被拖动的节点，更新引用
        if (typeof link.source === 'object' && link.source.id === node.id) {
          const updatedNode = updatedNodes.find((n: any) => n.id === node.id);
          if (updatedNode) {
            link.source = updatedNode;
          }
        }
        // 如果 target 是节点对象且是被拖动的节点，更新引用
        if (typeof link.target === 'object' && link.target.id === node.id) {
          const updatedNode = updatedNodes.find((n: any) => n.id === node.id);
          if (updatedNode) {
            link.target = updatedNode;
          }
        }
        return link;
      });
      
      // 返回新的 graphData，确保 ForceGraph2D 重新渲染
      return {
        nodes: updatedNodes,
        links: updatedLinks
      };
    });
  }, []);

  // 处理节点拖动结束
  const handleNodeDragEnd = useCallback((node: any) => {
    // 固定节点位置，使关系线保持拉长的状态
    node.fx = node.x;
    node.fy = node.y;
    
    // 更新状态
    setNodes(prevNodes => 
      prevNodes.map(n => 
        n.id === node.id 
          ? { ...n, x: node.x, y: node.y }
          : n
      )
    );
    
    // 更新 graphData
    setGraphData(prevData => ({
      nodes: prevData.nodes.map((n: any) => 
        n.id === node.id 
          ? { ...n, x: node.x, y: node.y, fx: node.x, fy: node.y }
          : n
      ),
      links: prevData.links
    }));
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center">
          <ArrowPathIcon className="h-8 w-8 animate-spin mx-auto text-gray-400 mb-2" />
          <p className="text-gray-500">加载Schema图...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="h-full flex flex-col">
      {/* 标题栏 */}
      <div className="bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <CubeIcon className="h-6 w-6 text-blue-600" />
          <div>
            <h1 className="text-xl font-semibold text-gray-900">Ontology Schema 图</h1>
            <p className="text-sm text-gray-500">展示对象类型和关系类型的定义</p>
          </div>
        </div>
        <div className="flex items-center gap-4">
          <button
            onClick={loadSchemaGraph}
            className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <ArrowPathIcon className="h-4 w-4 inline mr-2" />
            刷新
          </button>
          <div className="text-sm text-gray-600">
            <span className="font-medium">{nodes.length}</span> 个对象类型 · 
            <span className="font-medium"> {links.length}</span> 个关系类型
          </div>
        </div>
      </div>

      {/* 图视图 */}
      <div className="flex-1 relative bg-white">
        <ForceGraph2D
          ref={fgRef}
          graphData={graphData}
          nodeLabel={(node: any) => {
            const n = node as SchemaNode;
            return `${n.name}\n${n.description || ''}`;
          }}
          nodeColor={(node: any) => {
            const n = node as SchemaNode;
            return getNodeColor(n.data.name); // 使用原始 name 计算颜色，保持颜色一致性
          }}
          nodeVal={(node: any) => {
            // 根据连接数调整节点大小
            const linkCount = links.filter(l => l.source === node.id || l.target === node.id).length;
            return 8 + Math.min(linkCount * 2, 20);
          }}
          linkLabel={(link: any) => {
            const l = link as SchemaLink;
            return `${l.name}\n${l.description || ''}\n${l.cardinality} (${l.direction})`;
          }}
          linkDirectionalArrowLength={4}
          linkDirectionalArrowRelPos={1}
          linkColor={() => '#94a3b8'}
          linkWidth={1}
          onNodeDrag={handleNodeDrag}
          onNodeDragEnd={handleNodeDragEnd}
          onNodeClick={handleNodeClick}
          onLinkClick={handleLinkClick}
          onBackgroundClick={handleBackgroundClick}
          // 降低物理引擎的强度，使拖动后的位置更容易保持
          d3AlphaDecay={0.0228}
          d3VelocityDecay={0.4}
          nodeCanvasObject={(node: any, ctx: CanvasRenderingContext2D, globalScale: number) => {
            const n = node as SchemaNode;
            const label = n.data.display_name || n.data.name;
            const fontSize = 12 / globalScale;
            ctx.font = `bold ${fontSize}px Sans-Serif`;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';

            // 绘制节点圆圈
            const r = node.__size || 10;
            ctx.beginPath();
            ctx.arc(node.x || 0, node.y || 0, r, 0, 2 * Math.PI, false);
            ctx.fillStyle = getNodeColor(n.data.name); // 使用原始 name 计算颜色，保持颜色一致性
            ctx.fill();
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 2 / globalScale;
            ctx.stroke();

            // 绘制标签（仅在缩放足够大时）
            if (globalScale > 0.5) {
              ctx.fillStyle = '#ffffff';
              ctx.strokeStyle = '#1f2937';
              ctx.lineWidth = 2 / globalScale;
              ctx.strokeText(label, node.x || 0, node.y || 0);
              ctx.fillText(label, node.x || 0, node.y || 0);
            }
          }}
          linkCanvasObject={(link: any, ctx: CanvasRenderingContext2D, globalScale: number) => {
            const l = link as SchemaLink;
            const source = link.source;
            const target = link.target;
            
            // 获取节点位置和大小
            const sourceX = source.x || 0;
            const sourceY = source.y || 0;
            const targetX = target.x || 0;
            const targetY = target.y || 0;
            const sourceRadius = source.__size || 10;
            const targetRadius = target.__size || 10;
            
            // 计算方向向量
            const dx = targetX - sourceX;
            const dy = targetY - sourceY;
            const linkLength = Math.sqrt(dx * dx + dy * dy);
            
            if (linkLength === 0) return; // 避免除零错误
            
            // 单位向量
            const unitX = dx / linkLength;
            const unitY = dy / linkLength;
            
            // 计算连接到节点边缘的起点和终点
            const startX = sourceX + unitX * sourceRadius;
            const startY = sourceY + unitY * sourceRadius;
            const endX = targetX - unitX * targetRadius;
            const endY = targetY - unitY * targetRadius;
            
            // 绘制关系线（减小线宽）
            ctx.beginPath();
            ctx.moveTo(startX, startY);
            ctx.lineTo(endX, endY);
            ctx.strokeStyle = '#94a3b8';
            ctx.lineWidth = 1;
            ctx.stroke();
            
            // 绘制箭头（在目标节点边缘）
            const arrowLength = 4;
            const arrowAngle = Math.atan2(dy, dx);
            
            ctx.beginPath();
            ctx.moveTo(endX, endY);
            ctx.lineTo(
              endX - arrowLength * Math.cos(arrowAngle - Math.PI / 6),
              endY - arrowLength * Math.sin(arrowAngle - Math.PI / 6)
            );
            ctx.lineTo(
              endX - arrowLength * Math.cos(arrowAngle + Math.PI / 6),
              endY - arrowLength * Math.sin(arrowAngle + Math.PI / 6)
            );
            ctx.closePath();
            ctx.fillStyle = '#94a3b8';
            ctx.fill();
            
            // 绘制关系标签（降低显示阈值，使标签更容易显示）
            if (globalScale > 0.6) {
              const midX = (startX + endX) / 2;
              const midY = (startY + endY) / 2;
              
              // 使用 display_name，如果没有则使用 name
              const label = l.data.display_name || l.name;
              
              // 增大字号，从10改为16
              const fontSize = 16 / globalScale;
              ctx.font = `bold ${fontSize}px Sans-Serif`;
              ctx.textAlign = 'center';
              ctx.textBaseline = 'middle';
              
              // 使用更易读的颜色：深色文字配白色描边
              ctx.fillStyle = '#1e293b'; // 深灰色文字
              ctx.strokeStyle = '#ffffff'; // 白色描边
              ctx.lineWidth = 4 / globalScale; // 稍粗的描边
              ctx.strokeText(label, midX, midY);
              ctx.fillText(label, midX, midY);
            }
          }}
          cooldownTicks={100}
          onEngineStop={() => {
            // 图布局完成后可以执行的操作
          }}
        />
      </div>

      {/* 详情面板 - 侧边抽屉 */}
      {(selectedNode || selectedLink) && (
        <div className="absolute top-0 right-0 h-full w-96 bg-white shadow-2xl border-l border-gray-200 z-10 flex flex-col">
          {/* 头部 */}
          <div className="flex items-center justify-between p-6 border-b border-gray-200 bg-gradient-to-r from-blue-50 to-indigo-50">
            <div className="flex-1">
              {selectedNode && (
                <>
                  <h3 className="text-xl font-bold text-gray-900 mb-2">{selectedNode.data.display_name || selectedNode.data.name}</h3>
                  <span className="inline-block px-3 py-1 text-sm font-semibold rounded-md bg-blue-600 text-white">
                    对象类型
                  </span>
                </>
              )}
              {selectedLink && (
                <>
                  <h3 className="text-xl font-bold text-gray-900 mb-2">{selectedLink.name}</h3>
                  <span className="inline-block px-3 py-1 text-sm font-semibold rounded-md bg-green-600 text-white">
                    关系类型
                  </span>
                </>
              )}
            </div>
            <button
              onClick={() => {
                setSelectedNode(null);
                setSelectedLink(null);
              }}
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
            {selectedNode && (
              <div className="space-y-6">
                {/* 描述 */}
                {selectedNode.description && (
                  <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                    <div className="text-xs font-semibold text-blue-700 uppercase tracking-wide mb-2">描述</div>
                    <p className="text-sm text-gray-800 leading-relaxed">{selectedNode.description}</p>
                  </div>
                )}

                {/* 属性列表 */}
                <div>
                  <h4 className="text-sm font-semibold text-gray-700 mb-4 uppercase tracking-wide">
                    属性定义 ({selectedNode.data.properties.length})
                  </h4>
                  <div className="space-y-3">
                    {selectedNode.data.properties.map((prop, idx) => (
                      <div key={idx} className="bg-white border border-gray-200 rounded-lg p-4 hover:border-blue-300 transition-colors">
                        <div className="flex items-start justify-between mb-2">
                          <div className="font-semibold text-base text-gray-900">{prop.name}</div>
                          {prop.required && (
                            <span className="px-2 py-0.5 text-xs font-semibold rounded bg-red-100 text-red-700">
                              必填
                            </span>
                          )}
                        </div>
                        <div className="text-sm text-gray-600 mb-2">
                          <span className="font-medium">类型:</span> {prop.data_type}
                        </div>
                        {prop.description && (
                          <div className="text-sm text-gray-500 mt-2 pt-2 border-t border-gray-100">
                            {prop.description}
                          </div>
                        )}
                        {prop.constraints && Object.keys(prop.constraints).length > 0 && (
                          <div className="mt-2 pt-2 border-t border-gray-100">
                            <div className="text-xs font-semibold text-gray-500 mb-1">约束条件:</div>
                            <div className="text-xs text-gray-600">
                              {Object.entries(prop.constraints).map(([key, value]) => (
                                <div key={key} className="inline-block mr-2 mb-1 px-2 py-0.5 bg-gray-100 rounded">
                                  {key}: {String(value)}
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {selectedLink && (
              <div className="space-y-6">
                {/* 描述 */}
                {selectedLink.description && (
                  <div className="bg-green-50 border border-green-200 rounded-lg p-4">
                    <div className="text-xs font-semibold text-green-700 uppercase tracking-wide mb-2">描述</div>
                    <p className="text-sm text-gray-800 leading-relaxed">{selectedLink.description}</p>
                  </div>
                )}

                {/* 关系信息 */}
                <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
                  <h4 className="text-sm font-semibold text-gray-700 mb-3 uppercase tracking-wide">关系信息</h4>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <div className="text-xs font-semibold text-gray-500 mb-1">源类型</div>
                      <div className="text-base font-medium text-gray-900">{selectedLink.data.source_type}</div>
                    </div>
                    <div>
                      <div className="text-xs font-semibold text-gray-500 mb-1">目标类型</div>
                      <div className="text-base font-medium text-gray-900">{selectedLink.data.target_type}</div>
                    </div>
                    <div>
                      <div className="text-xs font-semibold text-gray-500 mb-1">基数</div>
                      <div className="text-base font-medium text-gray-900">{selectedLink.cardinality}</div>
                    </div>
                    <div>
                      <div className="text-xs font-semibold text-gray-500 mb-1">方向</div>
                      <div className="text-base font-medium text-gray-900">{selectedLink.direction}</div>
                    </div>
                  </div>
                </div>

                {/* 关系属性 */}
                {selectedLink.data.properties && selectedLink.data.properties.length > 0 && (
                  <div>
                    <h4 className="text-sm font-semibold text-gray-700 mb-4 uppercase tracking-wide">
                      关系属性 ({selectedLink.data.properties.length})
                    </h4>
                    <div className="space-y-3">
                      {selectedLink.data.properties.map((prop, idx) => (
                        <div key={idx} className="bg-white border border-gray-200 rounded-lg p-4 hover:border-green-300 transition-colors">
                          <div className="flex items-start justify-between mb-2">
                            <div className="font-semibold text-base text-gray-900">{prop.name}</div>
                            {prop.required && (
                              <span className="px-2 py-0.5 text-xs font-semibold rounded bg-red-100 text-red-700">
                                必填
                              </span>
                            )}
                          </div>
                          <div className="text-sm text-gray-600">
                            <span className="font-medium">类型:</span> {prop.data_type}
                          </div>
                          {prop.description && (
                            <div className="text-sm text-gray-500 mt-2 pt-2 border-t border-gray-100">
                              {prop.description}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* 图例 */}
      <div className="absolute top-20 right-4 bg-white border border-gray-200 rounded-lg shadow-lg p-4 text-xs">
        <div className="font-semibold text-gray-700 mb-2">图例</div>
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <div className="w-4 h-4 rounded-full bg-blue-500"></div>
            <span>对象类型</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-8 h-0.5 bg-slate-400"></div>
            <span>关系类型</span>
          </div>
          <div className="text-gray-500 mt-2">
            点击节点查看详情
          </div>
          <div className="text-gray-500">
            点击边查看关系详情
          </div>
        </div>
      </div>
    </div>
  );
}

