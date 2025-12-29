import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import ForceGraph2D from 'react-force-graph-2d';
import type { Instance, Link as LinkInstance } from '../api/client';
import { instanceApi, linkApi, schemaApi } from '../api/client';
import { 
  ArrowPathIcon,
  CircleStackIcon
} from '@heroicons/react/24/outline';

interface GraphNode {
  id: string;
  name: string;
  type: string;
  data: Instance;
  group?: number;
}

interface GraphLink {
  source: string;
  target: string;
  id: string;
  type: string;
  data: LinkInstance;
}

export default function GraphView() {
  const { objectType, instanceId } = useParams<{ objectType?: string; instanceId?: string }>();
  const [nodes, setNodes] = useState<GraphNode[]>([]);
  const [links, setLinks] = useState<GraphLink[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
  const [graphData, setGraphData] = useState({ nodes, links });

  useEffect(() => {
    loadGraphData();
  }, [objectType, instanceId]);

  const loadGraphData = async () => {
    try {
      setLoading(true);
      
      if (objectType && instanceId) {
        // 加载单个实例及其关系
        await loadInstanceGraph(objectType, instanceId);
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

  const loadInstanceGraph = async (objType: string, instId: string) => {
    const [instance, objectTypes, linkTypes] = await Promise.all([
      instanceApi.get(objType, instId),
      schemaApi.getObjectTypes(),
      schemaApi.getLinkTypes(),
    ]);

    const nodeMap = new Map<string, GraphNode>();
    const linkList: GraphLink[] = [];

    // 添加中心节点
    const centerNode: GraphNode = {
      id: instance.id,
      name: getInstanceName(instance, objType),
      type: objType,
      data: instance,
      group: 0,
    };
    nodeMap.set(instance.id, centerNode);

    // 加载所有关系
    for (const linkType of linkTypes) {
      try {
        // 查找出边
        if (linkType.source_type === objType) {
          const instanceLinks = await linkApi.getInstanceLinks(objType, instId, linkType.name);
          for (const link of instanceLinks) {
            const targetId = link.target_id as string;
            const targetType = linkType.target_type;
            
            // 加载目标实例
            try {
              const targetInstance = await instanceApi.get(targetType, targetId);
              if (!nodeMap.has(targetId)) {
                nodeMap.set(targetId, {
                  id: targetId,
                  name: getInstanceName(targetInstance, targetType),
                  type: targetType,
                  data: targetInstance,
                  group: getTypeGroup(targetType, objectTypes),
                });
              }
              linkList.push({
                source: instance.id,
                target: targetId,
                id: link.id,
                type: linkType.name,
                data: link,
              });
            } catch (err) {
              console.error(`Failed to load target instance ${targetId}:`, err);
            }
          }
        }

        // 查找入边（需要从目标类型查询）
        if (linkType.target_type === objType) {
          // 对于入边，需要通过目标实例查询
          try {
            const allLinks = await linkApi.list(linkType.name, 0, 1000);
            const instanceLinks = allLinks.items.filter(
              (link: any) => link.target_id === instId
            );
          for (const link of instanceLinks) {
            const sourceId = link.source_id as string;
            const sourceType = linkType.source_type;
            
            try {
              const sourceInstance = await instanceApi.get(sourceType, sourceId);
              if (!nodeMap.has(sourceId)) {
                nodeMap.set(sourceId, {
                  id: sourceId,
                  name: getInstanceName(sourceInstance, sourceType),
                  type: sourceType,
                  data: sourceInstance,
                  group: getTypeGroup(sourceType, objectTypes),
                });
              }
              linkList.push({
                source: sourceId,
                target: instance.id,
                id: link.id,
                type: linkType.name,
                data: link,
              });
            } catch (err) {
              console.error(`Failed to load source instance ${sourceId}:`, err);
            }
          }
          } catch (err) {
            console.error(`Failed to load incoming links for ${linkType.name}:`, err);
          }
        }
      } catch (err) {
        console.error(`Failed to load links for ${linkType.name}:`, err);
      }
    }

    setNodes(Array.from(nodeMap.values()));
    setLinks(linkList);
  };

  const loadFullGraph = async () => {
    const [objectTypes, linkTypes] = await Promise.all([
      schemaApi.getObjectTypes(),
      schemaApi.getLinkTypes(),
    ]);

    const nodeMap = new Map<string, GraphNode>();
    const linkList: GraphLink[] = [];

    // 加载所有对象类型的实例（限制数量以避免性能问题）
    for (const objType of objectTypes) {
      try {
        const instances = await instanceApi.list(objType.name, 0, 50); // 限制每个类型最多50个
        for (const instance of instances.items) {
          nodeMap.set(instance.id, {
            id: instance.id,
            name: getInstanceName(instance, objType.name),
            type: objType.name,
            data: instance,
            group: getTypeGroup(objType.name, objectTypes),
          });
        }
      } catch (err) {
        console.error(`Failed to load instances for ${objType.name}:`, err);
      }
    }

    // 加载所有关系
    for (const linkType of linkTypes) {
      try {
        const links = await linkApi.list(linkType.name, 0, 200); // 限制关系数量
        for (const link of links.items) {
          const sourceId = link.source_id as string;
          const targetId = link.target_id as string;
          
          // 只添加已加载节点的关系
          if (nodeMap.has(sourceId) && nodeMap.has(targetId)) {
            linkList.push({
              source: sourceId,
              target: targetId,
              id: link.id,
              type: linkType.name,
              data: link,
            });
          }
        }
      } catch (err) {
        console.error(`Failed to load links for ${linkType.name}:`, err);
      }
    }

    setNodes(Array.from(nodeMap.values()));
    setLinks(linkList);
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

  useEffect(() => {
    setGraphData({ nodes, links });
  }, [nodes, links]);

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
    const name = node.name || node.id.substring(0, 8);
    const type = node.type;
    // 尝试获取一些关键属性
    const keyProps: string[] = [];
    if (node.data.name) keyProps.push(`Name: ${node.data.name}`);
    if (node.data.email) keyProps.push(`Email: ${node.data.email}`);
    if (node.data.age !== undefined) keyProps.push(`Age: ${node.data.age}`);
    if (node.data.founded_year) keyProps.push(`Year: ${node.data.founded_year}`);
    
    return `${name}\n[${type}]${keyProps.length > 0 ? '\n' + keyProps.join('\n') : ''}`;
  };

  const getLinkLabel = (link: GraphLink) => {
    const labels: string[] = [link.type];
    // 添加关系的关键属性
    if (link.data.start_date) labels.push(`Start: ${link.data.start_date}`);
    if (link.data.position) labels.push(`Position: ${link.data.position}`);
    if (link.data.since) labels.push(`Since: ${link.data.since}`);
    return labels.join('\n');
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
            onClick={loadGraphData}
            className="flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium shadow-sm"
          >
            <ArrowPathIcon className="w-4 h-4 mr-2" />
            刷新
          </button>
        </div>
      </div>

      {/* 图形区域 */}
      <div className="flex-1 relative bg-white">
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
              const label = node.name || node.id.substring(0, 8);
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
              // 根据关系类型设置颜色，使用更深的颜色提高对比度
              const colors: Record<string, string> = {
                'worksAt': '#2563eb', // 深蓝色
                'knows': '#059669', // 深绿色
                'default': '#64748b', // 深灰色
              };
              return colors[link.type] || colors.default;
            }}
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
                  {selectedNode.type}
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
                    {selectedNode.id}
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
                  href={`#/instances/${selectedNode.type}/${selectedNode.id}`}
                  className="flex-1 text-center px-4 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium shadow-sm"
                >
                  查看详情
                </a>
                <a
                  href={`#/graph/${selectedNode.type}/${selectedNode.id}`}
                  className="flex-1 text-center px-4 py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors font-medium shadow-sm"
                >
                  关系图
                </a>
              </div>
            </div>
          </div>
        )}

        {/* 图例和统计信息 */}
        <div className="absolute bottom-4 left-4 bg-white rounded-xl shadow-xl border border-gray-200 p-4 max-w-xs">
          <h4 className="text-sm font-bold text-gray-900 mb-3">图例与统计</h4>
          <div className="space-y-3 text-xs">
            <div>
              <div className="text-gray-500 mb-2">节点类型</div>
              <div className="space-y-1.5">
                {Array.from(new Set(nodes.map(n => n.type))).map((type, idx) => {
                  const typeNodes = nodes.filter(n => n.type === type);
                  const color = nodeColor({ group: idx } as GraphNode);
                  return (
                    <div key={type} className="flex items-center justify-between">
                      <div className="flex items-center">
                        <div 
                          className="w-4 h-4 rounded-full mr-2 border-2 border-gray-300"
                          style={{ backgroundColor: color }}
                        ></div>
                        <span className="text-gray-700 font-medium">{type}</span>
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
                  const colors: Record<string, string> = {
                    'worksAt': '#3b82f6',
                    'knows': '#10b981',
                    'default': '#94a3b8',
                  };
                  const color = colors[type] || colors.default;
                  return (
                    <div key={type} className="flex items-center justify-between">
                      <div className="flex items-center">
                        <div 
                          className="w-6 h-0.5 mr-2"
                          style={{ backgroundColor: color }}
                        ></div>
                        <span className="text-gray-700 font-medium">{type}</span>
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
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

