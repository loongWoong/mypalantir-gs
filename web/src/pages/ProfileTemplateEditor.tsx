import React, { useState, useEffect } from 'react';
import { Editor, Frame, Element, useNode, useEditor } from '@craftjs/core';
import { MetricCardWidget, ChartWidget } from '../components/profile-editor/widgets';
import { profileTemplateApi } from '../api/profile-template';
import { schemaApi } from '../api/client';
import type { ProfileTemplate } from '../types/profile';
import type { ObjectType } from '../api/client';

// 容器组件（画布）
const Container = ({ children }: any) => {
  const {
    connectors: { connect, drag },
  } = useNode();

  return (
    <div
      ref={(ref) => {
        if (ref) {
          connect(drag(ref));
        }
      }}
      className="min-h-full p-6 bg-gray-50 rounded-lg"
    >
      <div className="space-y-4">{children}</div>
    </div>
  );
};

// 组件注册表
const componentMap = {
  MetricCardWidget,
  ChartWidget,
  Container,
};

// 模板加载器组件（需要在 Editor 内部使用）
const TemplateLoader = ({ templateId, onLoadComplete }: { templateId: string; onLoadComplete: () => void }) => {
  const { actions } = useEditor();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!templateId) {
      // 清空画布
      actions.clearEvents();
      actions.deserialize({
        ROOT: {
          type: { resolvedName: 'Container' },
          isCanvas: true,
          props: {},
          displayName: 'Container',
          custom: {},
          nodes: [],
          parent: null,
        },
      });
      return;
    }

    const loadTemplate = async () => {
      setLoading(true);
      try {
        console.log('TemplateLoader: Loading template with id:', templateId);
        const template = await profileTemplateApi.get(templateId);
        console.log('TemplateLoader: Received template:', template);
        if (template && template.craftState) {
          // 解析并加载模板状态
          const craftState = typeof template.craftState === 'string' 
            ? JSON.parse(template.craftState) 
            : template.craftState;
          console.log('TemplateLoader: Deserializing craftState:', craftState);
          actions.deserialize(craftState);
          console.log('TemplateLoader: Template loaded successfully');
        } else {
          console.warn('TemplateLoader: Template or craftState is missing');
        }
        onLoadComplete();
      } catch (error) {
        console.error('TemplateLoader: Failed to load template:', error);
        alert('加载模板失败: ' + ((error as Error).message || '未知错误'));
        onLoadComplete();
      } finally {
        setLoading(false);
      }
    };

    loadTemplate();
  }, [templateId, actions, onLoadComplete]);

  return null;
};

const ProfileTemplateEditor: React.FC = () => {
  const [entityTypes, setEntityTypes] = useState<ObjectType[]>([]);
  const [entityType, setEntityType] = useState<string>('');
  const [templates, setTemplates] = useState<ProfileTemplate[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>('');
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [loadingTemplate, setLoadingTemplate] = useState(false);
  const [loadingEntityTypes, setLoadingEntityTypes] = useState(false);

  // 加载对象类型列表
  useEffect(() => {
    loadEntityTypes();
  }, []);

  // 当实体类型变化时，加载对应的模板
  useEffect(() => {
    if (entityType) {
      loadTemplates();
    }
  }, [entityType]);

  const loadEntityTypes = async () => {
    setLoadingEntityTypes(true);
    try {
      const objectTypes = await schemaApi.getObjectTypes();
      setEntityTypes(objectTypes);
      // 默认选择第一个对象类型
      if (objectTypes.length > 0 && !entityType) {
        setEntityType(objectTypes[0].name);
      }
    } catch (error) {
      console.error('Failed to load entity types:', error);
      // 如果加载失败，使用默认值
      setEntityTypes([]);
    } finally {
      setLoadingEntityTypes(false);
    }
  };

  const loadTemplates = async () => {
    if (!entityType) {
      console.log('loadTemplates: entityType is empty, skipping');
      setTemplates([]);
      return;
    }
    
    console.log('loadTemplates: Loading templates for entityType:', entityType);
    setLoadingTemplates(true);
    try {
      const templateList = await profileTemplateApi.list(entityType);
      console.log('loadTemplates: Received template list:', templateList);
      // 确保 templateList 是数组，如果为 null 或 undefined 则使用空数组
      const safeTemplateList = Array.isArray(templateList) ? templateList : [];
      console.log('loadTemplates: Safe template list:', safeTemplateList);
      setTemplates(safeTemplateList);
      // 如果当前选中的模板不在新列表中，清空选择
      if (selectedTemplateId && !safeTemplateList.find(t => t.id === selectedTemplateId)) {
        setSelectedTemplateId('');
      }
    } catch (error) {
      console.error('Failed to load templates:', error);
      // 显示错误提示
      alert('加载模板列表失败: ' + ((error as Error).message || '未知错误'));
      // 出错时设置为空数组
      setTemplates([]);
    } finally {
      setLoadingTemplates(false);
    }
  };

  const handleTemplateSelect = (templateId: string) => {
    setLoadingTemplate(true);
    setSelectedTemplateId(templateId);
  };

  const handleLoadComplete = () => {
    setLoadingTemplate(false);
  };

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* 顶部工具栏 */}
      <div className="bg-white border-b px-6 py-4 flex items-center justify-between shadow-sm">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">画像编辑器</h1>
          <p className="text-sm text-gray-500 mt-1">拖拽组件构建画像模板</p>
        </div>
        <div className="flex items-center space-x-4">
          <select
            value={entityType}
            onChange={(e) => setEntityType(e.target.value)}
            className="px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 min-w-[150px]"
            disabled={loadingEntityTypes}
          >
            <option value="">选择画像类型</option>
            {entityTypes.map((type) => (
              <option key={type.name} value={type.name}>
                {type.display_name || type.name}
              </option>
            ))}
          </select>
          <select
            value={selectedTemplateId}
            onChange={(e) => handleTemplateSelect(e.target.value)}
            className="px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 min-w-[200px]"
            disabled={loadingTemplates || loadingTemplate}
          >
            <option value="">选择模板（可选）</option>
            {loadingTemplates ? (
              <option disabled>加载中...</option>
            ) : (
              Array.isArray(templates) && templates.length > 0 ? (
                templates.map((template) => (
                  <option key={template.id} value={template.id}>
                    {template.displayName || template.name}
                  </option>
                ))
              ) : (
                <option disabled>暂无模板</option>
              )
            )}
          </select>
          {loadingTemplate && (
            <span className="text-sm text-gray-500">加载中...</span>
          )}
        </div>
      </div>

      <Editor resolver={componentMap}>
        <TemplateLoader templateId={selectedTemplateId} onLoadComplete={handleLoadComplete} />
        <div className="flex flex-1 overflow-hidden">
          {/* 左侧：组件工具箱 */}
          <Toolbox />

          {/* 中间：画布 */}
          <div className="flex-1 p-6 overflow-auto">
            <Frame>
              <Element is={Container} canvas>
                <div className="text-center text-gray-400 py-12">
                  从左侧拖拽组件到此处开始构建画像，或从右上角选择已有模板
                </div>
              </Element>
            </Frame>
          </div>

          {/* 右侧：属性编辑面板 */}
          <SettingsPanel />
        </div>

        {/* 底部操作栏 */}
        <EditorActions 
          entityType={entityType} 
          onTemplatesChange={loadTemplates}
          onTemplateSaved={(templateId) => {
            setSelectedTemplateId(templateId);
          }}
        />
      </Editor>
    </div>
  );
};

// 工具箱组件
const Toolbox = () => {
  const { actions, query } = useEditor();

  const addMetricCard = () => {
    const element = React.createElement(MetricCardWidget, {
      title: '新指标卡片',
    });
    const nodeTree = query.parseReactElement(element).toNodeTree();
    actions.addNodeTree(nodeTree, 'ROOT');
  };

  const addChart = () => {
    const element = React.createElement(ChartWidget, {
      title: '新图表',
      chartType: 'bar',
    });
    const nodeTree = query.parseReactElement(element).toNodeTree();
    actions.addNodeTree(nodeTree, 'ROOT');
  };

  return (
    <div className="w-64 bg-white border-r p-4 overflow-auto">
      <h2 className="text-lg font-semibold mb-4 text-gray-800">组件库</h2>
      <div className="space-y-3">
        <button
          onClick={addMetricCard}
          className="w-full p-4 bg-white rounded-lg border-2 border-gray-200 hover:border-blue-500 hover:shadow-md transition-all text-left"
        >
          <div className="flex items-start space-x-3">
            <span className="text-3xl">📊</span>
            <div className="flex-1">
              <div className="text-sm font-semibold text-gray-800">指标卡片</div>
              <div className="text-xs text-gray-500 mt-1">展示单个指标数值</div>
            </div>
          </div>
        </button>
        
        <button
          onClick={addChart}
          className="w-full p-4 bg-white rounded-lg border-2 border-gray-200 hover:border-blue-500 hover:shadow-md transition-all text-left"
        >
          <div className="flex items-start space-x-3">
            <span className="text-3xl">📈</span>
            <div className="flex-1">
              <div className="text-sm font-semibold text-gray-800">图表</div>
              <div className="text-xs text-gray-500 mt-1">展示数据趋势</div>
            </div>
          </div>
        </button>
      </div>

      <div className="mt-6 p-3 bg-blue-50 rounded-lg text-sm text-blue-800">
        <p className="font-medium mb-1">💡 提示</p>
        <p>点击组件添加到画布，点击组件在右侧编辑属性</p>
      </div>
    </div>
  );
};


// 属性编辑面板
const SettingsPanel = () => {
  const { selected } = useEditor((state) => {
    const currentNodeId = Array.from(state.events.selected).pop();
    return {
      selected: currentNodeId,
    };
  });

  return (
    <div className="w-80 bg-white border-l p-6 overflow-auto">
      <h2 className="text-lg font-semibold mb-4 text-gray-800">组件属性</h2>
      {selected ? (
        <SelectedNodeSettings />
      ) : (
        <div className="text-gray-500 text-sm text-center py-12">
          <p className="mb-2">👆</p>
          <p>选择一个组件</p>
          <p>以编辑其属性</p>
        </div>
      )}
    </div>
  );
};

const SelectedNodeSettings = () => {
  const { selected } = useEditor((state) => ({
    selected: Array.from(state.events.selected).pop(),
  }));

  if (!selected) return null;

  return (
    <div>
      {/* 使用 useEditor hook 获取 query */}
      <NodeSettings nodeId={selected} />
    </div>
  );
};

const NodeSettings = ({ nodeId }: { nodeId: string }) => {
  const { query, actions } = useEditor();
  const node = query.node(nodeId).get();
  const SettingsComponent = node.related?.settings;
  
  const handleDelete = () => {
    if (confirm('确定要删除这个组件吗？')) {
      actions.delete(nodeId);
    }
  };
  
  return (
    <div>
      <div className="mb-4 pb-4 border-b">
        <div className="flex items-center justify-between">
          <div>
            <div className="text-sm font-medium text-gray-600">组件类型</div>
            <div className="text-base font-semibold text-gray-800 mt-1">
              {node.data.displayName || node.data.name}
            </div>
          </div>
          <button
            onClick={handleDelete}
            className="px-3 py-1.5 text-sm text-red-600 bg-red-50 rounded-lg hover:bg-red-100 transition-colors"
            title="删除组件"
          >
            删除
          </button>
        </div>
      </div>
      
      {SettingsComponent ? (
        <SettingsComponent />
      ) : (
        <div className="text-gray-500 text-sm">该组件无可配置属性</div>
      )}
    </div>
  );
};

// 编辑器操作栏
const EditorActions = ({ 
  entityType, 
  onTemplatesChange,
  onTemplateSaved
}: { 
  entityType: string; 
  onTemplatesChange: () => void;
  onTemplateSaved?: (templateId: string) => void;
}) => {
  const { query, actions } = useEditor();
  const [saving, setSaving] = useState(false);
  const [showSaveDialog, setShowSaveDialog] = useState(false);
  const [templateName, setTemplateName] = useState('');
  const [templateDescription, setTemplateDescription] = useState('');

  const handleSaveClick = () => {
    setTemplateName('');
    setTemplateDescription('');
    setShowSaveDialog(true);
  };

  const handleSave = async () => {
    if (!templateName.trim()) {
      alert('请输入模板名称');
      return;
    }

    if (!entityType) {
      alert('请先选择画像类型');
      return;
    }

    setSaving(true);
    try {
      const serializedState = query.serialize();
      
      // 调用后端 API 保存模板
      // craftState 需要是字符串格式（JSON 字符串）
      const result = await profileTemplateApi.create({
        name: templateName.trim().replace(/\s+/g, '_'),
        displayName: templateName.trim(),
        description: templateDescription.trim() || undefined,
        entityType,
        craftState: typeof serializedState === 'string' ? serializedState : JSON.stringify(serializedState),
      });

      alert('模板保存成功！');
      setShowSaveDialog(false);
      setTemplateName('');
      setTemplateDescription('');
      // 刷新模板列表
      await onTemplatesChange();
      // 自动选择新保存的模板
      if (result && result.id && onTemplateSaved) {
        onTemplateSaved(result.id);
      }
    } catch (error: any) {
      console.error('Save error:', error);
      // 显示详细的错误信息
      const errorMessage = error?.response?.data?.message || error?.message || '保存失败，请稍后重试';
      alert('保存失败: ' + errorMessage);
    } finally {
      setSaving(false);
    }
  };

  const handleClear = () => {
    if (confirm('确定要清空画布吗？此操作不可恢复。')) {
      actions.clearEvents();
      // 重置画布
      window.location.reload();
    }
  };

  return (
    <>
      <div className="bg-white border-t px-6 py-3 flex items-center justify-between shadow-sm">
        <div className="text-sm text-gray-600">
          {/* 可以显示状态信息 */}
        </div>
        <div className="flex space-x-3">
          <button
            onClick={handleClear}
            className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
          >
            清空
          </button>
          <button
            onClick={handleSaveClick}
            className="px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors"
          >
            保存模板
          </button>
        </div>
      </div>

      {/* 保存对话框 */}
      {showSaveDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-96 shadow-xl">
            <h3 className="text-lg font-semibold mb-4">保存模板</h3>
            <div className="mb-4">
              <label className="block text-sm font-medium mb-2">模板名称 <span className="text-red-500">*</span></label>
              <input
                type="text"
                value={templateName}
                onChange={(e) => setTemplateName(e.target.value)}
                placeholder="请输入模板名称"
                className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                autoFocus
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && templateName.trim()) {
                    handleSave();
                  } else if (e.key === 'Escape') {
                    setShowSaveDialog(false);
                  }
                }}
              />
            </div>
            <div className="mb-4">
              <label className="block text-sm font-medium mb-2">描述（可选）</label>
              <textarea
                value={templateDescription}
                onChange={(e) => setTemplateDescription(e.target.value)}
                placeholder="请输入模板描述"
                rows={3}
                className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
              />
            </div>
            <div className="flex justify-end space-x-3">
              <button
                onClick={() => {
                  setShowSaveDialog(false);
                  setTemplateName('');
                  setTemplateDescription('');
                }}
                className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
              >
                取消
              </button>
              <button
                onClick={handleSave}
                disabled={saving || !templateName.trim()}
                className="px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {saving ? '保存中...' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default ProfileTemplateEditor;
