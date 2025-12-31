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
  ArrowsRightLeftIcon
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
  const { selectedWorkspace } = useWorkspace();
  const [nodes, setNodes] = useState<GraphNode[]>([]);
  const [links, setLinks] = useState<GraphLink[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
  const [graphData, setGraphData] = useState({ nodes, links });
  
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
    
    // 加载当前节点的出边（正向关系）
    for (const linkType of linkTypes) {
      if (linkType.source_type === objType) {
        try {
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
              
              // 添加关系
              const linkId = `${link.id}_${instId}_${targetId}`;
              if (!linkList.some(l => l.id === linkId)) {
                linkList.push({
                  source: instId,
                  target: targetId,
                  id: link.id,
                  type: linkType.name,
                  data: link,
                });
              }
              
              // 递归查询目标节点的正向血缘
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
    
    // 加载当前节点的入边（反向关系）
    for (const linkType of linkTypes) {
      if (linkType.target_type === objType) {
        try {
          // 查询所有指向当前节点的关系
          // 注意：这里需要查询所有链接然后过滤，因为后端可能没有直接查询入边的API
          let offset = 0;
          const limit = 1000;
          let hasMore = true;
          
          while (hasMore) {
            const allLinks = await linkApi.list(linkType.name, offset, limit);
            const instanceLinks = allLinks.items.filter(
              (link: any) => link.target_id === instId
            );
            
            for (const link of instanceLinks) {
              const sourceId = link.source_id as string;
              const sourceType = linkType.source_type;
              
              // 检查是否已访问过（避免重复查询）
              const nodeKey = `${sourceType}:${sourceId}`;
              if (visitedNodes.has(nodeKey)) {
                continue;
              }
              
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
                
                // 添加关系（使用唯一ID避免重复）
                const linkId = `${link.id}_${sourceId}_${instId}`;
                if (!linkList.some(l => l.id === linkId || (l.source === sourceId && l.target === instId && l.type === linkType.name))) {
                  linkList.push({
                    source: sourceId,
                    target: instId,
                    id: link.id,
                    type: linkType.name,
                    data: link,
                  });
                }
                
                // 递归查询源节点的反向血缘
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
                console.error(`Failed to load source instance ${sourceId}:`, err);
              }
            }
            
            // 检查是否还有更多数据
            hasMore = allLinks.items.length === limit && (offset + limit < allLinks.total);
            offset += limit;
            
            // 如果已经找到指向当前节点的链接，可以提前退出
            if (instanceLinks.length > 0 && offset >= allLinks.total) {
              break;
            }
            
            // 避免无限循环
            if (offset > 10000) break;
          }
        } catch (err) {
          console.error(`Failed to load backward links for ${linkType.name}:`, err);
        }
      }
    }
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
        return selectedWorkspace.link_types!.includes(lt.name) && sourceInWorkspace && targetInWorkspace;
      });
    } else {
      linkTypes = allLinkTypes;
    }

    const nodeMap = new Map<string, GraphNode>();
    const linkList: GraphLink[] = [];
    const visitedNodes = new Set<string>();

    // 添加中心节点
    const centerNode: GraphNode = {
      id: instance.id,
      name: getInstanceName(instance, objType),
      type: objType,
      data: instance,
      group: 0,
    };
    nodeMap.set(instance.id, centerNode);

    // 根据查询模式加载关系
    if (mode === 'direct') {
      // 直接关系模式：只加载直接连接的节点（原有逻辑）
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

          // 查找入边
          if (linkType.target_type === objType) {
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
      await loadBackwardLineage(
        objType,
        instId,
        nodeMap,
        linkList,
        visitedNodes,
        linkTypes,
        objectTypes
      );
    } else if (mode === 'full') {
      // 全链血缘：正向 + 反向
      await loadForwardLineage(
        objType,
        instId,
        nodeMap,
        linkList,
        new Set<string>(), // 正向使用独立的 visited set
        linkTypes,
        objectTypes
      );
      await loadBackwardLineage(
        objType,
        instId,
        nodeMap,
        linkList,
        new Set<string>(), // 反向使用独立的 visited set
        linkTypes,
        objectTypes
      );
    }

    setNodes(Array.from(nodeMap.values()));
    setLinks(linkList);
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
            
            // 收集节点ID并计算度数
            nodeIdsToLoad.add(sourceId);
            nodeIdsToLoad.add(targetId);
            nodeDegree.set(sourceId, (nodeDegree.get(sourceId) || 0) + 1);
            nodeDegree.set(targetId, (nodeDegree.get(targetId) || 0) + 1);
            
            linkList.push({
              source: sourceId,
              target: targetId,
              id: link.id,
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
    for (const link of linkList) {
      // 根据链接类型推断source和target的类型
      const linkTypeDef = linkTypes.find(lt => lt.name === link.type);
      if (linkTypeDef) {
        if (!nodeIdsByType.has(linkTypeDef.source_type)) {
          nodeIdsByType.set(linkTypeDef.source_type, new Set());
        }
        if (!nodeIdsByType.has(linkTypeDef.target_type)) {
          nodeIdsByType.set(linkTypeDef.target_type, new Set());
        }
        nodeIdsByType.get(linkTypeDef.source_type)!.add(link.source);
        nodeIdsByType.get(linkTypeDef.target_type)!.add(link.target);
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
              nodeMap.set(id, {
                id: id,
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
            if (!nodeMap.has(nodeId)) {
              try {
                const instance = await instanceApi.get(typeName, nodeId);
                nodeMap.set(nodeId, {
                  id: nodeId,
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
    setGraphData({ nodes, links });
  }, [nodes, links]);

  // 配置力导向图的参数，增加连线默认长度
  useEffect(() => {
    if (fgRef.current && nodes.length > 0) {
      // 使用 d3Force 配置 linkDistance
      const linkForce = fgRef.current.d3Force('link');
      if (linkForce) {
        linkForce.distance(90); // 增加连线默认长度，使图形更清晰
      }
    }
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
        <div className="absolute bottom-12 left-4 bg-white rounded-xl shadow-xl border border-gray-200 p-4 max-w-xs max-h-[calc(100vh-200px)] overflow-y-auto">
          <h4 className="text-sm font-bold text-gray-900 mb-3 sticky top-0 bg-white pb-2 border-b border-gray-200">图例与统计</h4>
          <div className="space-y-3 text-xs mt-2">
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
                  const color = getLinkTypeColor(type);
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

