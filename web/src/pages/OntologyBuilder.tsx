import React, { useCallback, useState, useMemo } from 'react';
import ReactFlow, {
  type Node,
  type Edge,
  Background,
  Controls,
  MiniMap,
  type Connection,
  useNodesState,
  useEdgesState,
  MarkerType,
  Panel,
} from 'reactflow';
import 'reactflow/dist/style.css';
import {
  ArrowDownTrayIcon,
  CheckCircleIcon,
  ExclamationTriangleIcon,
  PlusIcon,
  TrashIcon,
  WrenchScrewdriverIcon,
  DocumentArrowDownIcon,
  DocumentArrowUpIcon,
  FolderArrowDownIcon,
} from '@heroicons/react/24/outline';
import { ontologyBuilderApi } from '../api/client';
import { type OntologyModel, type Entity, type Relation, createDefaultEntity, createDefaultRelation, toApiFormat } from '../models/OntologyModel';
import { validateModel, type ValidationError } from '../utils/ontologyValidator';
import { EntityNode } from '../components/ontology/EntityNode';
import { PropertyEditor } from '../components/ontology/PropertyEditor';

const nodeTypes = {
  entity: EntityNode,
};

export default function OntologyBuilder() {
  const [model, setModel] = useState<OntologyModel>({
    id: `model-${Date.now()}`,
    name: 'Untitled Model',
    version: '1.0.0',
    namespace: 'ontology.builder',
    entities: [],
    relations: [],
  });

  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<Edge | null>(null);
  const [yamlOutput, setYamlOutput] = useState('');
  const [validationErrors, setValidationErrors] = useState<ValidationError[]>([]);
  const [isValid, setIsValid] = useState<boolean | null>(null);
  const [filename, setFilename] = useState('');

  // 当模型名称改变时，自动更新文件名（如果文件名为空）
  React.useEffect(() => {
    if (!filename && model.name && model.name.trim() && model.name !== 'Untitled Model') {
      // 清理模型名称作为默认文件名
      const cleanName = model.name.trim().replace(/[^a-zA-Z0-9_-]/g, '-');
      if (cleanName) {
        setFilename(cleanName);
      }
    }
  }, [model.name]);

  // 同步模型到React Flow节点和边
  React.useEffect(() => {
    const flowNodes: Node[] = model.entities.map((entity) => ({
      id: entity.id,
      type: 'entity',
      position: entity.position || { x: Math.random() * 500, y: Math.random() * 500 },
      data: {
        entity,
        onEdit: (e: Entity) => {
          setModel((prev) => ({
            ...prev,
            entities: prev.entities.map((ent) => (ent.id === e.id ? e : ent)),
          }));
        },
        onDelete: (id: string) => {
          setModel((prev) => ({
            ...prev,
            entities: prev.entities.filter((e) => e.id !== id),
            relations: prev.relations.filter((r) => r.source !== id && r.target !== id),
          }));
        },
      },
    }));

    const flowEdges: Edge[] = model.relations.map((relation) => ({
      id: relation.id,
      source: relation.source,
      target: relation.target,
      label: relation.name || relation.display_name || '',
      type: 'smoothstep',
      animated: false,
      markerEnd: {
        type: MarkerType.ArrowClosed,
      },
      data: { relation },
    }));

    setNodes(flowNodes);
    setEdges(flowEdges);
  }, [model, setNodes, setEdges]);

  // 处理节点位置变化
  const onNodesChangeWithPosition = useCallback(
    (changes: any) => {
      onNodesChange(changes);
      
      // 当节点位置改变时，同步到模型
      if (Array.isArray(changes)) {
        changes.forEach((change: any) => {
          if (change.type === 'position' && change.position) {
            setModel((prev) => ({
              ...prev,
              entities: prev.entities.map((entity) => {
                if (entity.id === change.id) {
                  return { ...entity, position: change.position };
                }
                return entity;
              }),
            }));
          }
        });
      }
    },
    [onNodesChange]
  );

  // 处理连接创建（创建关系）
  const onConnect = useCallback(
    (params: Connection) => {
      if (!params.source || !params.target) return;

      const sourceEntity = model.entities.find((e) => e.id === params.source);
      const targetEntity = model.entities.find((e) => e.id === params.target);

      if (!sourceEntity || !targetEntity) return;

      // 检查是否已存在相同的关系
      const existingRelation = model.relations.find(
        (r) => r.source === params.source && r.target === params.target
      );

      if (existingRelation) {
        alert('关系已存在');
        return;
      }

      const newRelation = createDefaultRelation(
        params.source!,
        params.target!,
        sourceEntity.name,
        targetEntity.name
      );

      setModel((prev) => ({
        ...prev,
        relations: [...prev.relations, newRelation],
      }));
    },
    [model.entities, model.relations]
  );

  // 添加实体
  const addEntity = () => {
    const newEntity = createDefaultEntity();
    const position = { x: Math.random() * 400 + 100, y: Math.random() * 400 + 100 };
    newEntity.position = position;

    setModel((prev) => ({
      ...prev,
      entities: [...prev.entities, newEntity],
    }));

    // 选中新创建的实体
    setTimeout(() => {
      const node = nodes.find((n) => n.id === newEntity.id) || {
        id: newEntity.id,
        type: 'entity',
        position,
        data: { entity: newEntity },
      };
      setSelectedNode(node as Node);
    }, 100);
  };

  // 删除选中的节点
  const deleteSelectedNode = () => {
    if (selectedNode) {
      setModel((prev) => ({
        ...prev,
        entities: prev.entities.filter((e) => e.id !== selectedNode.id),
        relations: prev.relations.filter((r) => r.source !== selectedNode.id && r.target !== selectedNode.id),
      }));
      setSelectedNode(null);
    }
  };

  // 删除选中的边
  const deleteSelectedEdge = () => {
    if (selectedEdge) {
      setModel((prev) => ({
        ...prev,
        relations: prev.relations.filter((r) => r.id !== selectedEdge.id),
      }));
      setSelectedEdge(null);
    }
  };

  // 更新实体
  const updateEntity = (entityId: string, updates: Partial<Entity>) => {
    setModel((prev) => ({
      ...prev,
      entities: prev.entities.map((e) => (e.id === entityId ? { ...e, ...updates } : e)),
    }));
  };

  // 更新关系
  const updateRelation = (relationId: string, updates: Partial<Relation>) => {
    setModel((prev) => ({
      ...prev,
      relations: prev.relations.map((r) => (r.id === relationId ? { ...r, ...updates } : r)),
    }));
  };

  // 校验并生成YML
  const validateAndGenerate = async () => {
    // 前端校验
    const frontendErrors = validateModel(model);
    setValidationErrors(frontendErrors);
    setIsValid(frontendErrors.filter((e) => e.level === 'error').length === 0);

    if (frontendErrors.filter((e) => e.level === 'error').length > 0) {
      setYamlOutput('');
      return;
    }

    // 后端校验和生成YML
    try {
      const apiPayload = toApiFormat(model);
      const result = await ontologyBuilderApi.validate(apiPayload);
      setIsValid(result.valid);
      setYamlOutput(result.yaml);
      if (result.errors && result.errors.length > 0) {
        const backendErrors: ValidationError[] = result.errors.map((msg) => ({
          level: 'error',
          message: msg,
        }));
        setValidationErrors([...frontendErrors, ...backendErrors]);
      }
    } catch (error: any) {
      setIsValid(false);
      setValidationErrors([
        ...frontendErrors,
        { level: 'error', message: `后端校验失败: ${error.message}` },
      ]);
    }
  };

  // 下载YML
  const downloadYaml = () => {
    if (!yamlOutput) return;
    const blob = new Blob([yamlOutput], { type: 'application/yaml;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `ontology-model-${model.name}-${model.version}.yaml`;
    link.click();
    window.URL.revokeObjectURL(url);
  };

  // 导入系统内（保存到ontology文件夹）
  const importToSystem = async () => {
    if (!yamlOutput) {
      alert('请先生成并校验YML');
      return;
    }

    // 检查文件名
    const trimmedFilename = filename.trim();
    if (!trimmedFilename) {
      alert('请输入文件名');
      return;
    }

    // 验证文件名格式
    if (!/^[a-zA-Z0-9_-]+$/.test(trimmedFilename)) {
      alert('文件名只能包含字母、数字、下划线和连字符');
      return;
    }

    if (isValid === false) {
      const confirm = window.confirm('模型校验未通过，是否仍要保存？');
      if (!confirm) return;
    }

    try {
      const apiPayload = toApiFormat(model);
      const result = await ontologyBuilderApi.save(apiPayload, trimmedFilename);
      
      if (result.success) {
        alert(`✅ 文件已成功保存到系统：${result.filePath || 'ontology文件夹'}`);
      } else {
        // 检查是否是文件重复错误
        if (result.message && result.message.includes('已存在')) {
          alert(`❌ 文件名重复：${trimmedFilename}.yaml 已存在，请修改文件名后重试`);
        } else {
          alert(`❌ 保存失败：${result.message}`);
        }
      }
    } catch (error: any) {
      // 检查是否是文件重复错误
      const errorMessage = error.response?.data?.message || error.response?.data?.data?.message || error.message || '未知错误';
      if (errorMessage.includes('已存在') || errorMessage.includes('exists')) {
        alert(`❌ 文件名重复：${trimmedFilename}.yaml 已存在，请修改文件名后重试`);
      } else {
        alert(`❌ 保存失败：${errorMessage}`);
      }
    }
  };

  // 保存模型到本地存储
  const saveModel = () => {
    const dataStr = JSON.stringify(model, null, 2);
    localStorage.setItem(`ontology-model-${model.id}`, dataStr);
    alert('模型已保存到本地存储');
  };

  // 从本地存储加载模型
  const loadModel = () => {
    const keys = Object.keys(localStorage).filter((key) => key.startsWith('ontology-model-'));
    if (keys.length === 0) {
      alert('没有找到保存的模型');
      return;
    }
    const key = keys[keys.length - 1]; // 加载最新的
    const dataStr = localStorage.getItem(key);
    if (dataStr) {
      try {
        const loadedModel = JSON.parse(dataStr) as OntologyModel;
        setModel(loadedModel);
        alert('模型已加载');
      } catch (error) {
        alert('加载模型失败');
      }
    }
  };

  // 获取当前选中的实体
  const selectedEntity = useMemo(() => {
    if (!selectedNode) return null;
    return model.entities.find((e) => e.id === selectedNode.id) || null;
  }, [selectedNode, model.entities]);

  // 获取当前选中的关系
  const selectedRelation = useMemo(() => {
    if (!selectedEdge) return null;
    return model.relations.find((r) => r.id === selectedEdge.id) || null;
  }, [selectedEdge, model.relations]);

  return (
    <div className="h-full flex flex-col bg-gray-50">
      {/* 顶部工具栏 */}
      <div className="px-6 py-4 bg-white border-b border-gray-200 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <WrenchScrewdriverIcon className="w-6 h-6 text-blue-600" />
          <div>
            <h1 className="text-xl font-semibold text-gray-900">本体构建工具</h1>
            <p className="text-sm text-gray-500">可视化创建节点和关系，自动生成并校验 YML</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <input
            type="text"
            value={model.version}
            onChange={(e) => setModel((prev) => ({ ...prev, version: e.target.value }))}
            placeholder="版本"
            className="px-3 py-1.5 border rounded text-sm w-24"
          />
          <input
            type="text"
            value={model.namespace}
            onChange={(e) => setModel((prev) => ({ ...prev, namespace: e.target.value }))}
            placeholder="命名空间"
            className="px-3 py-1.5 border rounded text-sm w-40"
          />
          <button
            onClick={saveModel}
            className="px-3 py-1.5 rounded-md bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 flex items-center gap-1"
          >
            <DocumentArrowDownIcon className="w-4 h-4" />
            保存
          </button>
          <button
            onClick={loadModel}
            className="px-3 py-1.5 rounded-md bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 flex items-center gap-1"
          >
            <DocumentArrowUpIcon className="w-4 h-4" />
            加载
          </button>
          <button
            onClick={validateAndGenerate}
            className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700"
          >
            生成并校验
          </button>
        </div>
      </div>

      <div className="flex-1 grid grid-cols-12 gap-4 p-4 min-h-0">
        {/* 左侧：画布 */}
        <div className="col-span-8 bg-white border border-gray-200 rounded-lg overflow-hidden">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChangeWithPosition}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={(_, node) => {
              setSelectedNode(node);
              setSelectedEdge(null);
            }}
            onEdgeClick={(_, edge) => {
              setSelectedEdge(edge);
              setSelectedNode(null);
            }}
            onPaneClick={() => {
              setSelectedNode(null);
              setSelectedEdge(null);
            }}
            nodeTypes={nodeTypes}
            fitView
          >
            <Background />
            <Controls />
            <MiniMap />
            <Panel position="top-left">
              <button
                onClick={addEntity}
                className="px-3 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 flex items-center gap-1"
              >
                <PlusIcon className="w-4 h-4" />
                添加实体
              </button>
            </Panel>
          </ReactFlow>
        </div>

        {/* 右侧：属性编辑面板 */}
        <div className="col-span-4 bg-white border border-gray-200 rounded-lg p-4 overflow-y-auto">
          {selectedEntity ? (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="font-semibold text-gray-900">编辑实体</h3>
                <button
                  onClick={deleteSelectedNode}
                  className="text-red-500 hover:text-red-700"
                >
                  <TrashIcon className="w-5 h-5" />
                </button>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">名称 *</label>
                <input
                  type="text"
                  value={selectedEntity.name}
                  onChange={(e) => updateEntity(selectedEntity.id, { name: e.target.value })}
                  className="w-full border rounded px-3 py-2 text-sm"
                  placeholder="实体名称"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">显示名称</label>
                <input
                  type="text"
                  value={selectedEntity.display_name || ''}
                  onChange={(e) =>
                    updateEntity(selectedEntity.id, {
                      display_name: e.target.value,
                      label: e.target.value || selectedEntity.name,
                    })
                  }
                  className="w-full border rounded px-3 py-2 text-sm"
                  placeholder="显示名称"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">描述</label>
                <textarea
                  value={selectedEntity.description || ''}
                  onChange={(e) => updateEntity(selectedEntity.id, { description: e.target.value })}
                  className="w-full border rounded px-3 py-2 text-sm"
                  rows={3}
                  placeholder="实体描述"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">基础类型</label>
                <select
                  value={selectedEntity.base_type || ''}
                  onChange={(e) => updateEntity(selectedEntity.id, { base_type: e.target.value || undefined })}
                  className="w-full border rounded px-3 py-2 text-sm"
                >
                  <option value="">无</option>
                  {model.entities
                    .filter((e) => e.id !== selectedEntity.id)
                    .map((e) => (
                      <option key={e.id} value={e.name}>
                        {e.name}
                      </option>
                    ))}
                </select>
              </div>

              <div>
                <PropertyEditor
                  attributes={selectedEntity.attributes}
                  onChange={(attributes) => updateEntity(selectedEntity.id, { attributes })}
                />
              </div>
            </div>
          ) : selectedRelation ? (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="font-semibold text-gray-900">编辑关系</h3>
                <button
                  onClick={deleteSelectedEdge}
                  className="text-red-500 hover:text-red-700"
                >
                  <TrashIcon className="w-5 h-5" />
                </button>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">名称 *</label>
                <input
                  type="text"
                  value={selectedRelation.name}
                  onChange={(e) => updateRelation(selectedRelation.id, { name: e.target.value })}
                  className="w-full border rounded px-3 py-2 text-sm"
                  placeholder="关系名称"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">显示名称</label>
                <input
                  type="text"
                  value={selectedRelation.display_name || ''}
                  onChange={(e) => updateRelation(selectedRelation.id, { display_name: e.target.value })}
                  className="w-full border rounded px-3 py-2 text-sm"
                  placeholder="显示名称"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">描述</label>
                <textarea
                  value={selectedRelation.description || ''}
                  onChange={(e) => updateRelation(selectedRelation.id, { description: e.target.value })}
                  className="w-full border rounded px-3 py-2 text-sm"
                  rows={3}
                  placeholder="关系描述"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">关系类型</label>
                <select
                  value={selectedRelation.type}
                  onChange={(e) => updateRelation(selectedRelation.id, { type: e.target.value as Relation['type'] })}
                  className="w-full border rounded px-3 py-2 text-sm"
                >
                  <option value="1-1">一对一 (1-1)</option>
                  <option value="1-N">一对多 (1-N)</option>
                  <option value="N-1">多对一 (N-1)</option>
                  <option value="N-N">多对多 (N-N)</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">方向</label>
                <select
                  value={selectedRelation.direction}
                  onChange={(e) =>
                    updateRelation(selectedRelation.id, { direction: e.target.value as Relation['direction'] })
                  }
                  className="w-full border rounded px-3 py-2 text-sm"
                >
                  <option value="directed">有向</option>
                  <option value="undirected">无向</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">源实体</label>
                <input
                  type="text"
                  value={selectedRelation.source_type}
                  className="w-full border rounded px-3 py-2 text-sm bg-gray-100"
                  disabled
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">目标实体</label>
                <input
                  type="text"
                  value={selectedRelation.target_type}
                  className="w-full border rounded px-3 py-2 text-sm bg-gray-100"
                  disabled
                />
              </div>

              {selectedRelation.properties && (
                <div>
                  <PropertyEditor
                    attributes={selectedRelation.properties}
                    onChange={(properties) => updateRelation(selectedRelation.id, { properties })}
                  />
                </div>
              )}
            </div>
          ) : (
            <div className="text-center text-gray-500 py-8">
              <p>选择一个实体或关系进行编辑</p>
              <p className="text-sm mt-2">或点击"添加实体"创建新实体</p>
            </div>
          )}
        </div>
      </div>

      {/* 底部：YML输出和校验结果 */}
      {(yamlOutput || validationErrors.length > 0) && (
        <div className="border-t border-gray-200 bg-white p-4 max-h-64 overflow-y-auto">
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2">
              <h3 className="font-semibold text-gray-900">校验结果</h3>
              {isValid === true && (
                <span className="text-sm text-green-600 flex items-center gap-1">
                  <CheckCircleIcon className="w-4 h-4" />
                  校验通过
                </span>
              )}
              {isValid === false && (
                <span className="text-sm text-red-600 flex items-center gap-1">
                  <ExclamationTriangleIcon className="w-4 h-4" />
                  校验未通过
                </span>
              )}
            </div>
            {yamlOutput && (
              <div className="flex items-center gap-2">
                <button
                  onClick={downloadYaml}
                  className="px-3 py-1.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 flex items-center gap-1"
                >
                  <ArrowDownTrayIcon className="w-4 h-4" />
                  下载 YML
                </button>
                <div className="flex items-center gap-2 border-l border-gray-300 pl-2">
                  <input
                    type="text"
                    value={filename}
                    onChange={(e) => setFilename(e.target.value)}
                    placeholder="输入文件名（不含扩展名）"
                    className="px-3 py-1.5 border rounded text-sm w-48 focus:outline-none focus:ring-2 focus:ring-green-500"
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        importToSystem();
                      }
                    }}
                  />
                  <span className="text-sm text-gray-500">.yaml</span>
                  <button
                    onClick={importToSystem}
                    className="px-3 py-1.5 rounded-md bg-green-600 text-white text-sm hover:bg-green-700 flex items-center gap-1"
                    title="保存到系统ontology文件夹"
                  >
                    <FolderArrowDownIcon className="w-4 h-4" />
                    导入系统内
                  </button>
                </div>
              </div>
            )}
          </div>

          {validationErrors.length > 0 && (
            <div className="mb-4">
              <div className="space-y-1">
                {validationErrors.map((error, index) => (
                  <div
                    key={index}
                    className={`text-sm ${
                      error.level === 'error' ? 'text-red-600' : 'text-yellow-600'
                    }`}
                  >
                    {error.level === 'error' ? '❌' : '⚠️'} {error.message}
                  </div>
                ))}
              </div>
            </div>
          )}

          {yamlOutput && (
            <div>
              <h4 className="text-sm font-medium text-gray-700 mb-2">生成的 YML</h4>
              <textarea
                className="w-full h-32 border rounded p-2 text-xs font-mono"
                value={yamlOutput}
                readOnly
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
