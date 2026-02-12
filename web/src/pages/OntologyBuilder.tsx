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
  FolderOpenIcon,
  XMarkIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  ClipboardDocumentIcon,
  InformationCircleIcon,
  ArrowsRightLeftIcon,
  ArrowUturnLeftIcon,
} from '@heroicons/react/24/outline';
import { ontologyBuilderApi, type OntologyVersion } from '../api/client';
import { type OntologyModel, type Entity, type Relation, createDefaultEntity, createDefaultRelation, toApiFormat, fromApiFormat } from '../models/OntologyModel';
import { validateModel, type ValidationError } from '../utils/ontologyValidator';
import { useWorkspace } from '../WorkspaceContext';
import { EntityNode } from '../components/ontology/EntityNode';
import { PropertyEditor } from '../components/ontology/PropertyEditor';
import { PropertyMappingsEditor } from '../components/ontology/PropertyMappingsEditor';

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
  const [baseFilename, setBaseFilename] = useState(''); // 基础文件名（不含版本号）
  const [showFileDialog, setShowFileDialog] = useState(false);
  const [fileList, setFileList] = useState<string[]>([]);
  const [loadingFiles, setLoadingFiles] = useState(false);
  const [versionHistory, setVersionHistory] = useState<OntologyVersion[]>([]);
  const [commitMessage, setCommitMessage] = useState('');
  const [autoVersion, setAutoVersion] = useState<string>('1.0.0');
  const [autoNamespace, setAutoNamespace] = useState<string>('ontology.builder');
  const [versionIncrementType, setVersionIncrementType] = useState<'PATCH' | 'MINOR' | 'MAJOR'>('PATCH');
  const [nextVersion, setNextVersion] = useState<string>('1.0.0');
  const [activeTab, setActiveTab] = useState<'validation' | 'yaml' | 'save' | 'history'>('validation');
  const [historyFilename, setHistoryFilename] = useState<string>('');
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [yamlExpanded, setYamlExpanded] = useState(false);
  const [errorsExpanded, setErrorsExpanded] = useState(true);
  const [warningsExpanded, setWarningsExpanded] = useState(true);
  const [showCompareDialog, setShowCompareDialog] = useState(false);
  const [compareVersion1, setCompareVersion1] = useState<string>('');
  const [compareVersion2, setCompareVersion2] = useState<string>('');
  const [compareResult, setCompareResult] = useState<any>(null);
  const [loadingCompare, setLoadingCompare] = useState(false);
  const [showRollbackDialog, setShowRollbackDialog] = useState(false);
  const [rollbackVersion, setRollbackVersion] = useState<string>('');
  const [loadingRollback, setLoadingRollback] = useState(false);
  const { selectedWorkspaceId, selectedWorkspace } = useWorkspace();

  // 解析版本号
  const parseVersion = useCallback((versionStr: string): { major: number; minor: number; patch: number } => {
    const parts = versionStr.split('.');
    return {
      major: parseInt(parts[0] || '1', 10),
      minor: parseInt(parts[1] || '0', 10),
      patch: parseInt(parts[2] || '0', 10),
    };
  }, []);

  // 生成下一版本号
  const generateNextVersion = useCallback((currentVersion: string, type: 'PATCH' | 'MINOR' | 'MAJOR'): string => {
    const version = parseVersion(currentVersion);
    switch (type) {
      case 'MAJOR':
        return `${version.major + 1}.0.0`;
      case 'MINOR':
        return `${version.major}.${version.minor + 1}.0`;
      case 'PATCH':
        return `${version.major}.${version.minor}.${version.patch + 1}`;
      default:
        return `${version.major}.${version.minor}.${version.patch + 1}`;
    }
  }, [parseVersion]);

  // 当版本号或递增类型改变时，更新下一版本号
  React.useEffect(() => {
    const next = generateNextVersion(autoVersion, versionIncrementType);
    setNextVersion(next);
  }, [autoVersion, versionIncrementType, generateNextVersion]);

  // 自动生成命名空间（基于工作空间或文件名）
  React.useEffect(() => {
    let generatedNamespace = 'ontology.builder';
    
    if (selectedWorkspace) {
      // 基于工作空间生成命名空间
      const workspaceName = selectedWorkspace.name || selectedWorkspace.id;
      // 清理工作空间名称，转换为命名空间格式
      const cleanName = workspaceName
        .replace(/[^a-zA-Z0-9._-]/g, '.')
        .toLowerCase()
        .replace(/\.+/g, '.')
        .replace(/^\.|\.$/g, '');
      
      if (cleanName) {
        generatedNamespace = `ontology.${cleanName}`;
      }
    } else if (baseFilename.trim()) {
      // 基于基础文件名生成命名空间
      const cleanName = baseFilename.trim()
        .replace(/[^a-zA-Z0-9._-]/g, '.')
        .toLowerCase()
        .replace(/\.+/g, '.')
        .replace(/^\.|\.$/g, '');
      
      if (cleanName) {
        generatedNamespace = `ontology.${cleanName}`;
      }
    }
    
    setAutoNamespace(generatedNamespace);
    // 更新模型中的命名空间
    setModel((prev) => ({ ...prev, namespace: generatedNamespace }));
  }, [selectedWorkspace, baseFilename]);

  // 自动获取版本号（从历史版本或默认）
  React.useEffect(() => {
    if (!baseFilename.trim()) {
      // 没有基础文件名，使用默认
      setAutoVersion('1.0.0');
      setModel((prev) => ({ ...prev, version: '' }));
      return;
    }

    let cancelled = false;
    
    const fetchVersion = async () => {
      try {
        // 提取基础文件名（移除版本号和扩展名）
        const baseFile = baseFilename.trim().replace(/\.(yaml|yml)$/i, '').replace(/-\d+\.\d+\.\d+$/, '');
        if (!baseFile) {
          if (!cancelled) {
            setAutoVersion('1.0.0');
            setModel((prev) => ({ ...prev, version: '' }));
          }
          return;
        }
        // 使用基础文件名查询版本历史
        const history = await ontologyBuilderApi.getVersionHistory(baseFile);
        if (!cancelled) {
          if (history.length > 0) {
            // 获取最新版本，显示当前版本，保存时后端会自动递增
            const latestVersion = history[0].version;
            setAutoVersion(latestVersion);
            setModel((prev) => ({ ...prev, version: '' })); // 清空版本号，让后端自动递增
          } else {
            // 没有历史版本，使用默认
            setAutoVersion('1.0.0');
            setModel((prev) => ({ ...prev, version: '' }));
          }
        }
      } catch (error) {
        if (!cancelled) {
          console.warn('获取版本历史失败，使用默认版本:', error);
          // 获取失败，使用默认
          setAutoVersion('1.0.0');
          setModel((prev) => ({ ...prev, version: '' }));
        }
      }
    };
    
    fetchVersion();
    
    return () => {
      cancelled = true;
    };
  }, [baseFilename]);

  // 当模型名称改变时，自动更新基础文件名（如果文件名为空）
  React.useEffect(() => {
    if (!baseFilename && model.name && model.name.trim() && model.name !== 'Untitled Model') {
      // 清理模型名称作为默认文件名
      const cleanName = model.name.trim().replace(/[^a-zA-Z0-9_-]/g, '-');
      if (cleanName) {
        setBaseFilename(cleanName);
      }
    }
  }, [model.name, baseFilename]);

  // 计算完整文件名（基础文件名 + 版本号）
  // 使用 nextVersion 而不是 autoVersion，因为保存时会使用根据递增类型计算的下一个版本号
  const fullFilename = React.useMemo(() => {
    if (!baseFilename.trim()) return '';
    // 如果基础文件名已经包含版本号格式，先移除
    const cleanBase = baseFilename.trim().replace(/-\d+\.\d+\.\d+$/, '');
    return `${cleanBase}-${nextVersion}`;
  }, [baseFilename, nextVersion]);

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
    // 确保模型使用最新的版本号和命名空间
    const modelToValidate = {
      ...model,
      version: autoVersion, // 使用自动生成的版本号
      namespace: autoNamespace, // 使用自动生成的命名空间
    };
    
    // 前端校验
    const frontendErrors = validateModel(modelToValidate);
    setValidationErrors(frontendErrors);
    setIsValid(frontendErrors.filter((e) => e.level === 'error').length === 0);

    if (frontendErrors.filter((e) => e.level === 'error').length > 0) {
      setYamlOutput('');
      return;
    }

    // 后端校验和生成YML
    try {
      const apiPayload = toApiFormat(modelToValidate);
      const result = await ontologyBuilderApi.validate(apiPayload);
      setIsValid(result.valid);
      setYamlOutput(result.yaml);
      
      const backendValidationErrors: ValidationError[] = [];
      
      // 添加错误
      if (result.errors && result.errors.length > 0) {
        const backendErrors: ValidationError[] = result.errors.map((msg) => ({
          level: 'error',
          message: msg,
        }));
        backendValidationErrors.push(...backendErrors);
      }
      
      // 添加警告
      if (result.warnings && result.warnings.length > 0) {
        const backendWarnings: ValidationError[] = result.warnings.map((msg) => ({
          level: 'warning',
          message: msg,
        }));
        backendValidationErrors.push(...backendWarnings);
      }
      
      if (backendValidationErrors.length > 0) {
        setValidationErrors([...frontendErrors, ...backendValidationErrors]);
      }
      
      // 根据校验结果自动切换标签页
      if (result.valid) {
        // 校验通过，切换到保存配置标签页
        setActiveTab('save');
      } else {
        // 校验未通过，保持在校验结果标签页
        setActiveTab('validation');
      }
    } catch (error: any) {
      setIsValid(false);
      setValidationErrors([
        ...frontendErrors,
        { level: 'error', message: `后端校验失败: ${error.message}` },
      ]);
      setActiveTab('validation');
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

    // 检查基础文件名
    const trimmedBaseFilename = baseFilename.trim();
    if (!trimmedBaseFilename) {
      alert('请输入文件名');
      return;
    }

    // 验证基础文件名格式
    if (!/^[a-zA-Z0-9_-]+$/.test(trimmedBaseFilename)) {
      alert('文件名只能包含字母、数字、下划线和连字符');
      return;
    }

    // 使用完整文件名（基础文件名 + 版本号）
    const trimmedFilename = fullFilename;

    if (isValid === false) {
      const confirm = window.confirm('模型校验未通过，是否仍要保存？');
      if (!confirm) return;
    }

    try {
      // 根据选择的递增类型生成新版本号
      const newVersion = generateNextVersion(autoVersion, versionIncrementType);
      
      // 确保使用自动生成的命名空间和版本号
      // 注意：版本号不能为空，否则后端校验会失败
      const modelToSave = {
        ...model,
        namespace: autoNamespace,
        version: newVersion, // 使用根据递增类型生成的新版本号
      };
      
      const apiPayload = toApiFormat(modelToSave);
      const result = await ontologyBuilderApi.save(
        apiPayload, 
        trimmedFilename, 
        selectedWorkspaceId || undefined,
        commitMessage || undefined
      );
      
      if (result.success) {
        const savedVersion = result.version || newVersion;
        alert(`✅ 文件已成功保存到系统：${result.filePath || 'ontology文件夹'}\n版本：${savedVersion}\n命名空间：${autoNamespace}`);
        setCommitMessage(''); // 清空提交说明
        // 更新自动版本号为保存后的版本号
        if (result.version) {
          setAutoVersion(result.version);
        } else {
          setAutoVersion(newVersion);
        }
        // 重置递增类型为 PATCH（下次保存默认使用 PATCH）
        setVersionIncrementType('PATCH');
        // 刷新版本历史（使用基础文件名）
        if (baseFilename.trim()) {
          loadVersionHistory(baseFilename.trim());
        }
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

  // 从文件名中提取基础文件名（移除版本号和扩展名）
  const extractBaseFilename = (filename: string): string => {
    if (!filename.trim()) return '';
    // 移除扩展名
    let base = filename.trim().replace(/\.(yaml|yml)$/i, '');
    // 移除版本号（格式：-数字.数字.数字）
    base = base.replace(/-\d+\.\d+\.\d+$/, '');
    return base;
  };

  // 规范化文件名用于版本历史查询（只移除扩展名，保留版本号）
  const normalizeFilenameForHistory = (filename: string): string => {
    if (!filename.trim()) return '';
    // 只移除扩展名，保留版本号（因为版本目录是基于完整文件名创建的）
    return filename.trim().replace(/\.(yaml|yml)$/i, '');
  };

  // 加载版本历史
  const loadVersionHistory = async (file: string) => {
    if (!file.trim()) {
      setVersionHistory([]);
      return;
    }
    
    try {
      setLoadingHistory(true);
      // 规范化文件名：只移除扩展名，保留版本号（因为版本目录是基于完整文件名创建的）
      const normalizedFile = normalizeFilenameForHistory(file);
      console.log('查询版本历史，输入文件名:', file, '规范化后:', normalizedFile);
      const history = await ontologyBuilderApi.getVersionHistory(normalizedFile);
      console.log('版本历史查询结果:', history);
      setVersionHistory(history);
      if (history.length === 0) {
        console.warn('未找到版本历史，文件名:', normalizedFile);
      }
    } catch (error: any) {
      console.error('加载版本历史失败:', error);
      setVersionHistory([]);
      const errorMessage = error?.response?.data?.message || error?.response?.data?.data?.message || error.message || '未知错误';
      alert('加载版本历史失败: ' + errorMessage);
    } finally {
      setLoadingHistory(false);
    }
  };

  // 当切换到版本历史标签页时，自动加载当前文件的版本历史
  React.useEffect(() => {
    if (activeTab === 'history') {
      // 优先使用 historyFilename，如果没有则使用基础文件名+版本号构建完整文件名
      const fileToLoad = historyFilename.trim() || (baseFilename.trim() ? `${baseFilename.trim()}-${autoVersion}` : '');
      if (fileToLoad) {
        loadVersionHistory(fileToLoad);
      }
    }
  }, [activeTab, historyFilename, baseFilename, autoVersion]);

  // 当基础文件名改变时，如果正在查看版本历史，自动加载
  // 注意：这里应该使用完整文件名（基础文件名+版本号），而不是基础文件名
  React.useEffect(() => {
    if (activeTab === 'history' && baseFilename.trim() && !historyFilename.trim()) {
      // 使用基础文件名+当前版本号构建完整文件名
      const fullName = `${baseFilename.trim()}-${autoVersion}`;
      setHistoryFilename(fullName);
      loadVersionHistory(fullName);
    }
  }, [baseFilename, activeTab, autoVersion]);

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

  // 打开文件选择对话框
  const openFileDialog = async () => {
    setShowFileDialog(true);
    setLoadingFiles(true);
    try {
      const files = await ontologyBuilderApi.listFiles();
      setFileList(files);
    } catch (error: any) {
      alert('获取文件列表失败: ' + (error.message || '未知错误'));
      setFileList([]);
    } finally {
      setLoadingFiles(false);
    }
  };

  // 从ontology文件夹加载文件
  const loadFileFromOntology = async (file: string) => {
    try {
      setLoadingFiles(true);
      const apiData = await ontologyBuilderApi.loadFile(file);
      const loadedModel = fromApiFormat(apiData);
      
      // 为实体添加位置信息（如果不存在）
      const entitiesWithPosition = loadedModel.entities.map((entity, index) => ({
        ...entity,
        position: entity.position || {
          x: (index % 4) * 200 + 100,
          y: Math.floor(index / 4) * 200 + 100,
        },
      }));
      
      setModel({
        ...loadedModel,
        entities: entitiesWithPosition,
      });
      
      // 设置文件名为加载的文件名（去掉扩展名和版本号）
      const nameWithoutExt = file.replace(/\.(yaml|yml)$/i, '');
      // 移除版本号部分（格式：filename-1.0.0）
      const baseName = nameWithoutExt.replace(/-\d+\.\d+\.\d+$/, '');
      setBaseFilename(baseName);
      
      // 更新命名空间和版本号（从加载的模型）
      const loadedVersion = loadedModel.version || '1.0.0';
      const loadedNamespace = loadedModel.namespace || 'ontology.builder';
      
      setAutoNamespace(loadedNamespace);
      setAutoVersion(loadedVersion);
      
      // 确保模型中的版本号和命名空间也被正确设置
      setModel({
        ...loadedModel,
        entities: entitiesWithPosition,
        version: loadedVersion, // 确保版本号不为空
        namespace: loadedNamespace, // 确保命名空间不为空
      });
      
      // 始终设置完整文件名（去掉扩展名）到 historyFilename，以便版本历史查询使用
      // 如果文件名包含版本号，使用完整文件名；否则使用基础文件名+版本号
      const fullNameForHistory = normalizeFilenameForHistory(file);
      // 如果文件名本身不包含版本号，尝试用加载的版本号构建完整文件名
      const finalHistoryFilename = fullNameForHistory.includes('-') && /-\d+\.\d+\.\d+$/.test(fullNameForHistory)
        ? fullNameForHistory
        : `${baseName}-${loadedVersion}`;
      setHistoryFilename(finalHistoryFilename);
      
      // 如果当前在版本历史标签页，自动加载版本历史
      if (activeTab === 'history') {
        loadVersionHistory(finalHistoryFilename);
      }
      
      // 清空之前的校验结果
      setYamlOutput('');
      setValidationErrors([]);
      setIsValid(null);
      
      // 自动关闭文件选择对话框
      setShowFileDialog(false);
    } catch (error: any) {
      alert('加载文件失败: ' + (error.message || '未知错误'));
    } finally {
      setLoadingFiles(false);
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
          <div className="px-3 py-1.5 bg-gray-50 border border-gray-200 rounded text-sm text-gray-700 flex items-center gap-1">
            <span className="text-gray-500">版本:</span>
            <span className="font-medium">{autoVersion}</span>
            <span className="text-xs text-gray-400">(自动)</span>
          </div>
          <div className="px-3 py-1.5 bg-gray-50 border border-gray-200 rounded text-sm text-gray-700 flex items-center gap-1">
            <span className="text-gray-500">命名空间:</span>
            <span className="font-medium">{autoNamespace}</span>
            <span className="text-xs text-gray-400">(自动)</span>
          </div>
          {selectedWorkspace && (
            <div className="px-3 py-1.5 bg-blue-50 border border-blue-200 rounded text-sm text-blue-700 flex items-center gap-1">
              <span className="font-medium">工作空间:</span>
              <span>{selectedWorkspace.display_name || selectedWorkspace.name || selectedWorkspace.id.substring(0, 8)}</span>
            </div>
          )}
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
            加载本地
          </button>
          <button
            onClick={openFileDialog}
            className="px-3 py-1.5 rounded-md bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 flex items-center gap-1"
          >
            <FolderOpenIcon className="w-4 h-4" />
            加载文件
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

              {/* 属性映射编辑器 */}
              <div>
                <PropertyMappingsEditor
                  sourceEntity={model.entities.find((e) => e.name === selectedRelation.source_type) || null}
                  targetEntity={model.entities.find((e) => e.name === selectedRelation.target_type) || null}
                  propertyMappings={selectedRelation.property_mappings}
                  onChange={(propertyMappings) => updateRelation(selectedRelation.id, { property_mappings: propertyMappings })}
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

      {/* 底部：校验结果和操作面板 */}
      {(yamlOutput || validationErrors.length > 0 || isValid !== null) && (
        <div className="border-t border-gray-200 bg-white">
          {/* 标签页导航 */}
          <div className="border-b border-gray-200">
            <div className="flex items-center justify-between px-4">
              <div className="flex items-center gap-1">
                <button
                  onClick={() => setActiveTab('validation')}
                  className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
                    activeTab === 'validation'
                      ? 'border-blue-600 text-blue-600'
                      : 'border-transparent text-gray-500 hover:text-gray-700'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    {isValid === true ? (
                      <CheckCircleIcon className="w-5 h-5 text-green-600" />
                    ) : isValid === false ? (
                      <ExclamationTriangleIcon className="w-5 h-5 text-red-600" />
                    ) : (
                      <InformationCircleIcon className="w-5 h-5 text-gray-400" />
                    )}
                    <span>校验结果</span>
                    {validationErrors.length > 0 && (
                      <span className={`px-2 py-0.5 rounded-full text-xs ${
                        validationErrors.filter(e => e.level === 'error').length > 0
                          ? 'bg-red-100 text-red-700'
                          : 'bg-yellow-100 text-yellow-700'
                      }`}>
                        {validationErrors.filter(e => e.level === 'error').length} 错误 / {validationErrors.filter(e => e.level === 'warning').length} 警告
                      </span>
                    )}
                  </div>
                </button>
                {yamlOutput && (
                  <button
                    onClick={() => setActiveTab('yaml')}
                    className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
                      activeTab === 'yaml'
                        ? 'border-blue-600 text-blue-600'
                        : 'border-transparent text-gray-500 hover:text-gray-700'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <ClipboardDocumentIcon className="w-5 h-5" />
                      <span>YAML 预览</span>
                    </div>
                  </button>
                )}
                {yamlOutput && (
                  <button
                    onClick={() => setActiveTab('save')}
                    className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
                      activeTab === 'save'
                        ? 'border-blue-600 text-blue-600'
                        : 'border-transparent text-gray-500 hover:text-gray-700'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <FolderArrowDownIcon className="w-5 h-5" />
                      <span>保存配置</span>
                    </div>
                  </button>
                )}
                <button
                  onClick={() => setActiveTab('history')}
                  className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
                    activeTab === 'history'
                      ? 'border-blue-600 text-blue-600'
                      : 'border-transparent text-gray-500 hover:text-gray-700'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <DocumentArrowUpIcon className="w-5 h-5" />
                    <span>版本历史</span>
                    {versionHistory.length > 0 && (
                      <span className="px-2 py-0.5 bg-blue-100 text-blue-700 rounded-full text-xs">
                        {versionHistory.length}
                      </span>
                    )}
                  </div>
                </button>
              </div>
              <div className="flex items-center gap-2">
                {yamlOutput && activeTab === 'yaml' && (
                  <button
                    onClick={downloadYaml}
                    className="px-3 py-1.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 flex items-center gap-1"
                  >
                    <ArrowDownTrayIcon className="w-4 h-4" />
                    下载 YML
                  </button>
                )}
                <button
                  onClick={() => {
                    setYamlOutput('');
                    setValidationErrors([]);
                    setIsValid(null);
                  }}
                  className="px-3 py-1.5 rounded-md bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 flex items-center gap-1"
                  title="隐藏校验结果面板"
                >
                  <XMarkIcon className="w-4 h-4" />
                  隐藏
                </button>
              </div>
            </div>
          </div>

          {/* 标签页内容 */}
          <div className="p-4 max-h-96 overflow-y-auto">
            {/* 校验结果标签页 */}
            {activeTab === 'validation' && (
              <div className="space-y-4">
                {/* 校验状态摘要 */}
                <div className={`p-4 rounded-lg border-2 ${
                  isValid === true
                    ? 'bg-green-50 border-green-200'
                    : isValid === false
                    ? 'bg-red-50 border-red-200'
                    : 'bg-gray-50 border-gray-200'
                }`}>
                  <div className="flex items-center gap-3">
                    {isValid === true ? (
                      <>
                        <CheckCircleIcon className="w-6 h-6 text-green-600" />
                        <div>
                          <h4 className="font-semibold text-green-900">校验通过</h4>
                          <p className="text-sm text-green-700 mt-1">模型结构完整，可以保存</p>
                        </div>
                      </>
                    ) : isValid === false ? (
                      <>
                        <ExclamationTriangleIcon className="w-6 h-6 text-red-600" />
                        <div>
                          <h4 className="font-semibold text-red-900">校验未通过</h4>
                          <p className="text-sm text-red-700 mt-1">
                            发现 {validationErrors.filter(e => e.level === 'error').length} 个错误，请修复后重试
                          </p>
                        </div>
                      </>
                    ) : (
                      <>
                        <InformationCircleIcon className="w-6 h-6 text-gray-400" />
                        <div>
                          <h4 className="font-semibold text-gray-900">等待校验</h4>
                          <p className="text-sm text-gray-600 mt-1">点击"生成并校验"按钮开始校验</p>
                        </div>
                      </>
                    )}
                  </div>
                </div>

                {/* 错误列表 */}
                {validationErrors.filter(e => e.level === 'error').length > 0 && (
                  <div className="border border-red-200 rounded-lg overflow-hidden">
                    <button
                      onClick={() => setErrorsExpanded(!errorsExpanded)}
                      className="w-full px-4 py-3 bg-red-50 border-b border-red-200 flex items-center justify-between hover:bg-red-100 transition-colors"
                    >
                      <div className="flex items-center gap-2">
                        <ExclamationTriangleIcon className="w-5 h-5 text-red-600" />
                        <span className="font-semibold text-red-900">
                          错误 ({validationErrors.filter(e => e.level === 'error').length})
                        </span>
                      </div>
                      {errorsExpanded ? (
                        <ChevronUpIcon className="w-5 h-5 text-red-600" />
                      ) : (
                        <ChevronDownIcon className="w-5 h-5 text-red-600" />
                      )}
                    </button>
                    {errorsExpanded && (
                      <div className="divide-y divide-red-100">
                        {validationErrors
                          .filter(e => e.level === 'error')
                          .map((error, index) => (
                            <div key={index} className="px-4 py-3 bg-white hover:bg-red-50 transition-colors">
                              <div className="flex items-start gap-2">
                                <span className="text-red-600 mt-0.5">●</span>
                                <span className="text-sm text-red-900">{error.message}</span>
                              </div>
                            </div>
                          ))}
                      </div>
                    )}
                  </div>
                )}

                {/* 警告列表 */}
                {validationErrors.filter(e => e.level === 'warning').length > 0 && (
                  <div className="border border-yellow-200 rounded-lg overflow-hidden">
                    <button
                      onClick={() => setWarningsExpanded(!warningsExpanded)}
                      className="w-full px-4 py-3 bg-yellow-50 border-b border-yellow-200 flex items-center justify-between hover:bg-yellow-100 transition-colors"
                    >
                      <div className="flex items-center gap-2">
                        <ExclamationTriangleIcon className="w-5 h-5 text-yellow-600" />
                        <span className="font-semibold text-yellow-900">
                          警告 ({validationErrors.filter(e => e.level === 'warning').length})
                        </span>
                      </div>
                      {warningsExpanded ? (
                        <ChevronUpIcon className="w-5 h-5 text-yellow-600" />
                      ) : (
                        <ChevronDownIcon className="w-5 h-5 text-yellow-600" />
                      )}
                    </button>
                    {warningsExpanded && (
                      <div className="divide-y divide-yellow-100">
                        {validationErrors
                          .filter(e => e.level === 'warning')
                          .map((error, index) => (
                            <div key={index} className="px-4 py-3 bg-white hover:bg-yellow-50 transition-colors">
                              <div className="flex items-start gap-2">
                                <span className="text-yellow-600 mt-0.5">●</span>
                                <span className="text-sm text-yellow-900">{error.message}</span>
                              </div>
                            </div>
                          ))}
                      </div>
                    )}
                  </div>
                )}

                {/* 无错误无警告提示 */}
                {validationErrors.length === 0 && isValid === true && (
                  <div className="text-center py-8 text-gray-500">
                    <CheckCircleIcon className="w-12 h-12 text-green-500 mx-auto mb-2" />
                    <p className="text-sm">没有发现任何问题</p>
                  </div>
                )}
              </div>
            )}

            {/* YAML 预览标签页 */}
            {activeTab === 'yaml' && yamlOutput && (
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <ClipboardDocumentIcon className="w-5 h-5 text-gray-400" />
                    <span className="text-sm font-medium text-gray-700">生成的 YAML 内容</span>
                  </div>
                  <button
                    onClick={() => setYamlExpanded(!yamlExpanded)}
                    className="text-sm text-gray-500 hover:text-gray-700 flex items-center gap-1"
                  >
                    {yamlExpanded ? (
                      <>
                        <ChevronUpIcon className="w-4 h-4" />
                        收起
                      </>
                    ) : (
                      <>
                        <ChevronDownIcon className="w-4 h-4" />
                        展开
                      </>
                    )}
                  </button>
                </div>
                <div className={`border rounded-lg bg-gray-50 overflow-hidden transition-all ${
                  yamlExpanded ? 'max-h-none' : 'max-h-64'
                }`}>
                  <textarea
                    className="w-full p-3 text-xs font-mono bg-transparent border-0 resize-none focus:outline-none"
                    value={yamlOutput}
                    readOnly
                    rows={yamlExpanded ? yamlOutput.split('\n').length : 10}
                    style={{ height: yamlExpanded ? 'auto' : '256px' }}
                  />
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => {
                      navigator.clipboard.writeText(yamlOutput);
                      alert('YAML 内容已复制到剪贴板');
                    }}
                    className="px-3 py-1.5 rounded-md bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 flex items-center gap-1"
                  >
                    <ClipboardDocumentIcon className="w-4 h-4" />
                    复制
                  </button>
                  <button
                    onClick={downloadYaml}
                    className="px-3 py-1.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 flex items-center gap-1"
                  >
                    <ArrowDownTrayIcon className="w-4 h-4" />
                    下载文件
                  </button>
                </div>
              </div>
            )}

            {/* 版本历史标签页 */}
            {activeTab === 'history' && (
              <div className="space-y-4">
                {/* 文件选择 */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    选择模型文件
                  </label>
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      value={historyFilename}
                      onChange={(e) => setHistoryFilename(e.target.value)}
                      onBlur={() => {
                        if (historyFilename.trim()) {
                          loadVersionHistory(historyFilename.trim());
                        }
                      }}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && historyFilename.trim()) {
                          loadVersionHistory(historyFilename.trim());
                        }
                      }}
                      placeholder="输入文件名（可包含版本号，如：carbak-1.0.0）"
                      className="flex-1 px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <button
                      onClick={() => {
                        if (historyFilename.trim()) {
                          loadVersionHistory(historyFilename.trim());
                        }
                      }}
                      disabled={!historyFilename.trim() || loadingHistory}
                      className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed flex items-center gap-1"
                    >
                      {loadingHistory ? '加载中...' : '查询'}
                    </button>
                    <button
                      onClick={openFileDialog}
                      className="px-4 py-2 rounded-md bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 flex items-center gap-1"
                    >
                      <FolderOpenIcon className="w-4 h-4" />
                      浏览文件
                    </button>
                  </div>
                  <p className="text-xs text-gray-500 mt-1">输入文件名或点击"浏览文件"从列表中选择</p>
                </div>

                {loadingHistory ? (
                  <div className="text-center py-8 text-gray-500">加载中...</div>
                ) : versionHistory.length === 0 ? (
                  <div className="text-center py-8 text-gray-500">
                    <DocumentArrowUpIcon className="w-12 h-12 text-gray-300 mx-auto mb-2" />
                    <p>暂无版本历史</p>
                    <p className="text-sm mt-1">请选择或输入文件名查询版本历史</p>
                  </div>
                ) : (
                  <div className="space-y-6">
                    {/* 版本时间线图 */}
                    <div className="bg-white border border-gray-200 rounded-lg p-4">
                      <h4 className="text-sm font-semibold text-gray-900 mb-4">版本时间线</h4>
                      <div className="relative">
                        {/* 时间线 */}
                        <div className="absolute left-4 top-0 bottom-0 w-0.5 bg-blue-200"></div>
                        <div className="space-y-4">
                          {versionHistory.map((version, index) => {
                            const isLatest = index === 0;
                            const date = new Date(version.timestamp);
                            const dateStr = date.toLocaleDateString('zh-CN');
                            const timeStr = date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
                            
                            return (
                              <div key={index} className="relative flex items-start gap-4">
                                {/* 时间线节点 */}
                                <div className={`relative z-10 flex-shrink-0 w-8 h-8 rounded-full border-2 flex items-center justify-center ${
                                  isLatest
                                    ? 'bg-blue-600 border-blue-600'
                                    : 'bg-white border-blue-300'
                                }`}>
                                  {isLatest && (
                                    <CheckCircleIcon className="w-5 h-5 text-white" />
                                  )}
                                </div>
                                
                                {/* 版本信息 */}
                                <div className="flex-1 pb-4">
                                  <div className="flex items-center gap-2 mb-1">
                                    <span className={`font-semibold ${
                                      isLatest ? 'text-blue-600' : 'text-gray-900'
                                    }`}>
                                      v{version.version}
                                    </span>
                                    {isLatest && (
                                      <span className="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs rounded-full">
                                        最新
                                      </span>
                                    )}
                                    <span className="text-xs text-gray-500">
                                      {dateStr} {timeStr}
                                    </span>
                                  </div>
                                  
                                  {version.commit_message && (
                                    <p className="text-sm text-gray-700 mb-2">{version.commit_message}</p>
                                  )}
                                  
                                  {version.workspace_name && (
                                    <div className="text-xs text-gray-500 mb-2">
                                      工作空间: {version.workspace_name}
                                    </div>
                                  )}
                                  
                                  {version.changes && version.changes.length > 0 && (
                                    <div className="mt-2">
                                      <div className="text-xs font-medium text-gray-600 mb-1">变更内容：</div>
                                      <ul className="text-xs text-gray-600 space-y-1">
                                        {version.changes.map((change, i) => (
                                          <li key={i} className="flex items-start gap-1">
                                            <span className="text-blue-500 mt-0.5">•</span>
                                            <span>{change}</span>
                                          </li>
                                        ))}
                                      </ul>
                                    </div>
                                  )}
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    </div>

                    {/* 版本列表（详细视图） */}
                    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
                      <div className="px-4 py-3 bg-gray-50 border-b border-gray-200">
                        <h4 className="text-sm font-semibold text-gray-900">版本列表</h4>
                      </div>
                      <div className="divide-y divide-gray-200">
                        {versionHistory.map((version, index) => {
                          const isLatest = index === 0;
                          const date = new Date(version.timestamp);
                          const dateStr = date.toLocaleDateString('zh-CN', { 
                            year: 'numeric', 
                            month: 'long', 
                            day: 'numeric' 
                          });
                          const timeStr = date.toLocaleTimeString('zh-CN', { 
                            hour: '2-digit', 
                            minute: '2-digit',
                            second: '2-digit'
                          });
                          
                          return (
                            <div
                              key={index}
                              className={`p-4 hover:bg-gray-50 transition-colors ${
                                isLatest ? 'bg-blue-50/30' : ''
                              }`}
                            >
                              <div className="flex items-start justify-between">
                                <div className="flex-1">
                                  <div className="flex items-center gap-2 mb-2">
                                    <span className={`font-semibold text-lg ${
                                      isLatest ? 'text-blue-600' : 'text-gray-900'
                                    }`}>
                                      v{version.version}
                                    </span>
                                    {isLatest && (
                                      <span className="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs rounded-full font-medium">
                                        最新版本
                                      </span>
                                    )}
                                    {version.previous_version && (
                                      <span className="text-xs text-gray-500">
                                        基于 v{version.previous_version}
                                      </span>
                                    )}
                                  </div>
                                  
                                  {version.commit_message && (
                                    <p className="text-sm text-gray-700 mb-2">{version.commit_message}</p>
                                  )}
                                  
                                  <div className="flex items-center gap-4 text-xs text-gray-500 mb-2">
                                    <span>时间: {dateStr} {timeStr}</span>
                                    {version.workspace_name && (
                                      <span>工作空间: {version.workspace_name}</span>
                                    )}
                                    {version.author && (
                                      <span>作者: {version.author}</span>
                                    )}
                                  </div>
                                  
                                  {version.changes && version.changes.length > 0 && (
                                    <div className="mt-2">
                                      <div className="text-xs font-medium text-gray-600 mb-1">变更内容：</div>
                                      <ul className="text-xs text-gray-600 space-y-1">
                                        {version.changes.map((change, i) => (
                                          <li key={i} className="flex items-start gap-1">
                                            <span className="text-blue-500 mt-0.5">•</span>
                                            <span>{change}</span>
                                          </li>
                                        ))}
                                      </ul>
                                    </div>
                                  )}
                                </div>
                                
                                <div className="flex items-center gap-2 ml-4">
                                  <button
                                    onClick={() => {
                                      setCompareVersion1(version.version);
                                      setCompareVersion2('');
                                      setCompareResult(null);
                                      setShowCompareDialog(true);
                                    }}
                                    className="px-3 py-1.5 rounded-md bg-blue-100 text-blue-700 text-sm hover:bg-blue-200 flex items-center gap-1"
                                    title="对比此版本与其他版本"
                                  >
                                    <ArrowsRightLeftIcon className="w-4 h-4" />
                                    对比
                                  </button>
                                  {!isLatest && (
                                    <button
                                      onClick={() => {
                                        setRollbackVersion(version.version);
                                        setShowRollbackDialog(true);
                                      }}
                                      className="px-3 py-1.5 rounded-md bg-orange-100 text-orange-700 text-sm hover:bg-orange-200 flex items-center gap-1"
                                      title="回滚到此版本"
                                    >
                                      <ArrowUturnLeftIcon className="w-4 h-4" />
                                      回滚
                                    </button>
                                  )}
                                  <button
                                    onClick={async () => {
                                      try {
                                        const versionData = await ontologyBuilderApi.getVersion(
                                          historyFilename.trim() || baseFilename.trim(),
                                          version.version
                                        );
                                        const loadedModel = fromApiFormat(versionData);
                                        setModel({
                                          ...loadedModel,
                                          version: version.version,
                                          namespace: version.namespace,
                                        });
                                        setAutoVersion(version.version);
                                        setAutoNamespace(version.namespace);
                                        setActiveTab('validation');
                                        alert(`已加载版本 ${version.version}`);
                                      } catch (error: any) {
                                        alert('加载版本失败: ' + (error.message || '未知错误'));
                                      }
                                    }}
                                    className="px-3 py-1.5 rounded-md bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 flex items-center gap-1"
                                    title="加载此版本到编辑器"
                                  >
                                    <DocumentArrowUpIcon className="w-4 h-4" />
                                    加载
                                  </button>
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* 保存配置标签页 */}
            {activeTab === 'save' && yamlOutput && (
              <div className="space-y-4">
                {/* 文件信息 */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    文件名 <span className="text-red-500">*</span>
                  </label>
                  <div className="space-y-2">
                    <div className="flex items-center gap-2">
                      <input
                        type="text"
                        value={baseFilename}
                        onChange={(e) => setBaseFilename(e.target.value)}
                        placeholder="输入文件名（不含版本号和扩展名）"
                        className="flex-1 px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            importToSystem();
                          }
                        }}
                      />
                      <span className="text-sm text-gray-400">-</span>
                      <span className="px-3 py-2 bg-gray-100 border border-gray-300 rounded-md text-sm text-gray-700 font-mono">
                        {autoVersion}
                      </span>
                      <span className="text-sm text-gray-500">.yaml</span>
                    </div>
                    {baseFilename.trim() && (
                      <div className="px-3 py-2 bg-blue-50 border border-blue-200 rounded-md">
                        <div className="text-xs text-blue-600 mb-1">完整文件名预览（将保存为）：</div>
                        <div className="text-sm font-mono text-blue-900">{fullFilename}.yaml</div>
                        <div className="text-xs text-blue-500 mt-1">基于当前版本 {autoVersion} 和递增类型 {versionIncrementType} 计算</div>
                      </div>
                    )}
                  </div>
                  <p className="text-xs text-gray-500 mt-1">文件名只能包含字母、数字、下划线和连字符，版本号会自动拼接</p>
                </div>

                {/* 版本和命名空间信息 */}
                <div className="grid grid-cols-2 gap-4">
                  <div className="bg-gray-50 px-4 py-3 rounded-lg border border-gray-200">
                    <div className="flex items-center justify-between mb-2">
                      <div className="text-gray-500 text-xs font-medium">当前版本号</div>
                      <div className="text-xs text-gray-400">下一版本: <span className="font-mono font-semibold text-blue-600">{nextVersion}</span></div>
                    </div>
                    <div className="font-semibold text-gray-900 text-lg font-mono mb-3">{autoVersion}</div>
                    <div className="space-y-2">
                      <div className="text-xs text-gray-600 mb-2">版本递增类型：</div>
                      <div className="flex gap-2">
                        <button
                          onClick={() => setVersionIncrementType('PATCH')}
                          className={`flex-1 px-2 py-1.5 text-xs rounded border transition-colors ${
                            versionIncrementType === 'PATCH'
                              ? 'bg-blue-100 border-blue-300 text-blue-700 font-medium'
                              : 'bg-white border-gray-300 text-gray-600 hover:bg-gray-50'
                          }`}
                          title="修订号：向下兼容的问题修正（如：1.0.0 → 1.0.1）"
                        >
                          PATCH
                        </button>
                        <button
                          onClick={() => setVersionIncrementType('MINOR')}
                          className={`flex-1 px-2 py-1.5 text-xs rounded border transition-colors ${
                            versionIncrementType === 'MINOR'
                              ? 'bg-blue-100 border-blue-300 text-blue-700 font-medium'
                              : 'bg-white border-gray-300 text-gray-600 hover:bg-gray-50'
                          }`}
                          title="次版本号：向下兼容的功能性新增（如：1.0.0 → 1.1.0）"
                        >
                          MINOR
                        </button>
                        <button
                          onClick={() => setVersionIncrementType('MAJOR')}
                          className={`flex-1 px-2 py-1.5 text-xs rounded border transition-colors ${
                            versionIncrementType === 'MAJOR'
                              ? 'bg-red-100 border-red-300 text-red-700 font-medium'
                              : 'bg-white border-gray-300 text-gray-600 hover:bg-gray-50'
                          }`}
                          title="主版本号：不兼容的API修改（如：1.0.0 → 2.0.0）"
                        >
                          MAJOR
                        </button>
                      </div>
                      <div className="text-xs text-gray-500 mt-2">
                        {versionIncrementType === 'PATCH' && '修复小问题，向下兼容'}
                        {versionIncrementType === 'MINOR' && '新增功能，向下兼容'}
                        {versionIncrementType === 'MAJOR' && '重大变更，可能不兼容'}
                      </div>
                    </div>
                  </div>
                  <div className="bg-gray-50 px-4 py-3 rounded-lg border border-gray-200">
                    <div className="text-gray-500 text-xs mb-1 font-medium">命名空间</div>
                    <div className="font-semibold text-gray-900 text-lg break-all">{autoNamespace}</div>
                    <div className="text-xs text-gray-400 mt-1">基于工作空间自动生成</div>
                  </div>
                </div>

                {/* 工作空间信息 */}
                {selectedWorkspace ? (
                  <div className="bg-blue-50 px-4 py-3 rounded-lg border border-blue-200">
                    <div className="text-blue-700 text-xs mb-1 font-medium">归属工作空间</div>
                    <div className="text-blue-900 font-semibold">
                      {selectedWorkspace.display_name || selectedWorkspace.name || selectedWorkspace.id}
                    </div>
                    {selectedWorkspace.description && (
                      <div className="text-blue-600 text-sm mt-1">{selectedWorkspace.description}</div>
                    )}
                  </div>
                ) : (
                  <div className="bg-yellow-50 px-4 py-3 rounded-lg border border-yellow-200">
                    <div className="flex items-start gap-2">
                      <ExclamationTriangleIcon className="w-5 h-5 text-yellow-600 mt-0.5" />
                      <div>
                        <div className="text-yellow-800 font-medium text-sm">未选择工作空间</div>
                        <div className="text-yellow-700 text-xs mt-1">
                          模型将保存但不关联工作空间。请在左侧导航栏选择工作空间。
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* 提交说明 */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    提交说明 <span className="text-gray-400">(可选)</span>
                  </label>
                  <input
                    type="text"
                    value={commitMessage}
                    onChange={(e) => setCommitMessage(e.target.value)}
                    placeholder="例如：添加用户实体和关系"
                    className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
                  />
                  <p className="text-xs text-gray-500 mt-1">描述本次更改的内容，便于版本管理</p>
                </div>

                {/* 操作按钮 */}
                <div className="flex items-center gap-3 pt-2 border-t border-gray-200">
                  <button
                    onClick={importToSystem}
                    disabled={!baseFilename.trim() || isValid === false}
                    className={`flex-1 px-4 py-2.5 rounded-md text-sm font-medium flex items-center justify-center gap-2 ${
                      !baseFilename.trim() || isValid === false
                        ? 'bg-gray-100 text-gray-400 cursor-not-allowed'
                        : 'bg-green-600 text-white hover:bg-green-700'
                    }`}
                  >
                    <FolderArrowDownIcon className="w-5 h-5" />
                    保存到系统
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 文件选择对话框 */}
      {showFileDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md mx-4">
            <div className="flex items-center justify-between p-4 border-b border-gray-200">
              <h3 className="text-lg font-semibold text-gray-900">选择要加载的文件</h3>
              <button
                onClick={() => setShowFileDialog(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>
            <div className="p-4 max-h-96 overflow-y-auto">
              {loadingFiles ? (
                <div className="text-center py-8 text-gray-500">加载中...</div>
              ) : fileList.length === 0 ? (
                <div className="text-center py-8 text-gray-500">
                  <p>ontology 文件夹中没有找到 YAML 文件</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {fileList.map((file) => {
                    // 移除扩展名，保留版本号（用于版本历史查询）
                    const fullNameForHistory = normalizeFilenameForHistory(file);
                    return (
                      <button
                        key={file}
                        onClick={async () => {
                          // 先关闭对话框，避免显示空白状态
                          setShowFileDialog(false);
                          // 加载文件
                          await loadFileFromOntology(file);
                          // 如果当前在版本历史标签页，自动加载该文件的版本历史
                          if (activeTab === 'history') {
                            // 使用完整文件名（去掉扩展名）填充，因为版本目录是基于完整文件名创建的
                            setHistoryFilename(fullNameForHistory);
                            loadVersionHistory(fullNameForHistory);
                          }
                        }}
                        className="w-full text-left px-4 py-3 rounded-md border border-gray-200 hover:bg-blue-50 hover:border-blue-300 transition-colors"
                      >
                        <div className="flex items-center gap-2">
                          <DocumentArrowUpIcon className="w-5 h-5 text-gray-400" />
                          <span className="text-sm font-medium text-gray-900">{file}</span>
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
            <div className="p-4 border-t border-gray-200 flex justify-end">
              <button
                onClick={() => setShowFileDialog(false)}
                className="px-4 py-2 rounded-md bg-gray-100 text-gray-700 text-sm hover:bg-gray-200"
              >
                取消
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 版本对比对话框 */}
      {showCompareDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-4xl mx-4 max-h-[90vh] flex flex-col">
            <div className="flex items-center justify-between p-4 border-b border-gray-200">
              <h3 className="text-lg font-semibold text-gray-900">版本对比</h3>
              <button
                onClick={() => {
                  setShowCompareDialog(false);
                  setCompareVersion1('');
                  setCompareVersion2('');
                  setCompareResult(null);
                }}
                className="text-gray-400 hover:text-gray-600"
              >
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>
            <div className="p-4 flex-1 overflow-y-auto">
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      版本 1
                    </label>
                    <select
                      value={compareVersion1}
                      onChange={(e) => setCompareVersion1(e.target.value)}
                      className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="">选择版本...</option>
                      {versionHistory.map((v) => (
                        <option key={v.version} value={v.version}>
                          v{v.version}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      版本 2
                    </label>
                    <select
                      value={compareVersion2}
                      onChange={(e) => setCompareVersion2(e.target.value)}
                      className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="">选择版本...</option>
                      {versionHistory.map((v) => (
                        <option key={v.version} value={v.version}>
                          v{v.version}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="flex justify-end">
                  <button
                    onClick={async () => {
                      if (!compareVersion1 || !compareVersion2) {
                        alert('请选择两个版本进行对比');
                        return;
                      }
                      if (compareVersion1 === compareVersion2) {
                        alert('请选择不同的版本进行对比');
                        return;
                      }
                      try {
                        setLoadingCompare(true);
                        const filename = historyFilename.trim() || baseFilename.trim();
                        const result = await ontologyBuilderApi.compareVersions(
                          filename,
                          compareVersion1,
                          compareVersion2
                        );
                        setCompareResult(result);
                      } catch (error: any) {
                        alert('版本对比失败: ' + (error.message || '未知错误'));
                      } finally {
                        setLoadingCompare(false);
                      }
                    }}
                    disabled={!compareVersion1 || !compareVersion2 || loadingCompare}
                    className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed flex items-center gap-1"
                  >
                    {loadingCompare ? '对比中...' : '开始对比'}
                  </button>
                </div>
                {compareResult && (
                  <div className="mt-4 space-y-4">
                    {compareResult.metadataChanges && compareResult.metadataChanges.length > 0 && (
                      <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
                        <h4 className="text-sm font-semibold text-yellow-900 mb-2">元数据变更</h4>
                        <ul className="text-sm text-yellow-800 space-y-1">
                          {compareResult.metadataChanges.map((change: string, i: number) => (
                            <li key={i}>• {change}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                    {compareResult.objectTypeDiffs && compareResult.objectTypeDiffs.length > 0 && (
                      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                        <h4 className="text-sm font-semibold text-blue-900 mb-2">
                          对象类型变更 ({compareResult.objectTypeDiffs.length} 项)
                        </h4>
                        <div className="space-y-2">
                          {compareResult.objectTypeDiffs.map((diff: any, i: number) => (
                            <div key={i} className="bg-white rounded p-2">
                              <div className="flex items-center gap-2 mb-1">
                                <span className={`text-xs px-2 py-0.5 rounded ${
                                  diff.type === 'ADDED' ? 'bg-green-100 text-green-700' :
                                  diff.type === 'DELETED' ? 'bg-red-100 text-red-700' :
                                  'bg-yellow-100 text-yellow-700'
                                }`}>
                                  {diff.type === 'ADDED' ? '新增' : diff.type === 'DELETED' ? '删除' : '修改'}
                                </span>
                                <span className="font-medium text-sm">{diff.name}</span>
                              </div>
                              {diff.changes && diff.changes.length > 0 && (
                                <ul className="text-xs text-gray-600 ml-4 space-y-1">
                                  {diff.changes.map((change: string, j: number) => (
                                    <li key={j}>• {change}</li>
                                  ))}
                                </ul>
                              )}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                    {compareResult.linkTypeDiffs && compareResult.linkTypeDiffs.length > 0 && (
                      <div className="bg-purple-50 border border-purple-200 rounded-lg p-4">
                        <h4 className="text-sm font-semibold text-purple-900 mb-2">
                          关系类型变更 ({compareResult.linkTypeDiffs.length} 项)
                        </h4>
                        <div className="space-y-2">
                          {compareResult.linkTypeDiffs.map((diff: any, i: number) => (
                            <div key={i} className="bg-white rounded p-2">
                              <div className="flex items-center gap-2 mb-1">
                                <span className={`text-xs px-2 py-0.5 rounded ${
                                  diff.type === 'ADDED' ? 'bg-green-100 text-green-700' :
                                  diff.type === 'DELETED' ? 'bg-red-100 text-red-700' :
                                  'bg-yellow-100 text-yellow-700'
                                }`}>
                                  {diff.type === 'ADDED' ? '新增' : diff.type === 'DELETED' ? '删除' : '修改'}
                                </span>
                                <span className="font-medium text-sm">{diff.name}</span>
                              </div>
                              {diff.changes && diff.changes.length > 0 && (
                                <ul className="text-xs text-gray-600 ml-4 space-y-1">
                                  {diff.changes.map((change: string, j: number) => (
                                    <li key={j}>• {change}</li>
                                  ))}
                                </ul>
                              )}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                    {(!compareResult.objectTypeDiffs || compareResult.objectTypeDiffs.length === 0) &&
                     (!compareResult.linkTypeDiffs || compareResult.linkTypeDiffs.length === 0) &&
                     (!compareResult.metadataChanges || compareResult.metadataChanges.length === 0) && (
                      <div className="text-center py-8 text-gray-500">
                        <p>两个版本之间没有差异</p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 版本回滚确认对话框 */}
      {showRollbackDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md mx-4">
            <div className="flex items-center justify-between p-4 border-b border-gray-200">
              <h3 className="text-lg font-semibold text-gray-900">确认回滚</h3>
              <button
                onClick={() => {
                  setShowRollbackDialog(false);
                  setRollbackVersion('');
                }}
                className="text-gray-400 hover:text-gray-600"
              >
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>
            <div className="p-4">
              <div className="mb-4">
                <p className="text-sm text-gray-700 mb-2">
                  确定要回滚到版本 <span className="font-semibold text-orange-600">v{rollbackVersion}</span> 吗？
                </p>
                <p className="text-xs text-gray-500">
                  回滚操作将创建一个新版本，当前版本的历史记录将保留。
                </p>
              </div>
            </div>
            <div className="p-4 border-t border-gray-200 flex justify-end gap-2">
              <button
                onClick={() => {
                  setShowRollbackDialog(false);
                  setRollbackVersion('');
                }}
                className="px-4 py-2 rounded-md bg-gray-100 text-gray-700 text-sm hover:bg-gray-200"
              >
                取消
              </button>
              <button
                onClick={async () => {
                  try {
                    setLoadingRollback(true);
                    const filename = historyFilename.trim() || baseFilename.trim();
                    const result = await ontologyBuilderApi.rollback(filename, rollbackVersion);
                    if (result.success) {
                      alert(`✅ 已成功回滚到版本 ${rollbackVersion}`);
                      setShowRollbackDialog(false);
                      setRollbackVersion('');
                      // 刷新版本历史
                      if (filename) {
                        loadVersionHistory(filename);
                      }
                    } else {
                      alert('回滚失败: ' + (result.message || '未知错误'));
                    }
                  } catch (error: any) {
                    alert('回滚失败: ' + (error.message || '未知错误'));
                  } finally {
                    setLoadingRollback(false);
                  }
                }}
                disabled={loadingRollback}
                className="px-4 py-2 rounded-md bg-orange-600 text-white text-sm hover:bg-orange-700 disabled:bg-gray-300 disabled:cursor-not-allowed"
              >
                {loadingRollback ? '回滚中...' : '确认回滚'}
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
