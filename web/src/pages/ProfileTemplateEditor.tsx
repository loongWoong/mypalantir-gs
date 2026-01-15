import React, { useState } from 'react';
import { Editor, Frame, Element, useNode, useEditor } from '@craftjs/core';
import { MetricCardWidget, ChartWidget } from '../components/profile-editor/widgets';
import { profileTemplateApi } from '../api/profile-template';

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

const ProfileTemplateEditor: React.FC = () => {
  const [templateName, setTemplateName] = useState('');
  const [entityType, setEntityType] = useState('Gantry');

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* 顶部工具栏 */}
      <div className="bg-white border-b px-6 py-4 flex items-center justify-between shadow-sm">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">画像编辑器</h1>
          <p className="text-sm text-gray-500 mt-1">拖拽组件构建画像模板</p>
        </div>
        <div className="flex items-center space-x-4">
          <input
            type="text"
            value={templateName}
            onChange={(e) => setTemplateName(e.target.value)}
            placeholder="输入模板名称"
            className="px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <select
            value={entityType}
            onChange={(e) => setEntityType(e.target.value)}
            className="px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="Gantry">门架画像</option>
            <option value="Vehicle">车辆画像</option>
            <option value="TollStation">收费站画像</option>
          </select>
        </div>
      </div>

      <Editor resolver={componentMap}>
        <div className="flex flex-1 overflow-hidden">
          {/* 左侧：组件工具箱 */}
          <Toolbox />

          {/* 中间：画布 */}
          <div className="flex-1 p-6 overflow-auto">
            <Frame>
              <Element is={Container} canvas>
                <div className="text-center text-gray-400 py-12">
                  从左侧拖拽组件到此处开始构建画像
                </div>
              </Element>
            </Frame>
          </div>

          {/* 右侧：属性编辑面板 */}
          <SettingsPanel />
        </div>

        {/* 底部操作栏 */}
        <EditorActions templateName={templateName} entityType={entityType} />
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
  const { query } = useEditor();
  const node = query.node(nodeId).get();
  const SettingsComponent = node.related?.settings;
  
  return (
    <div>
      <div className="mb-4 pb-4 border-b">
        <div className="text-sm font-medium text-gray-600">组件类型</div>
        <div className="text-base font-semibold text-gray-800 mt-1">
          {node.data.displayName || node.data.name}
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
const EditorActions = ({ templateName, entityType }: any) => {
  const { query, actions } = useEditor();
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    if (!templateName.trim()) {
      alert('请输入模板名称');
      return;
    }

    setSaving(true);
    try {
      const serializedState = query.serialize();
      
      // 调用后端 API 保存模板
      await profileTemplateApi.create({
        name: templateName.trim().replace(/\s+/g, '_'),
        displayName: templateName.trim(),
        entityType,
        craftState: serializedState,
      });

      alert('模板保存成功！');
    } catch (error) {
      console.error('Save error:', error);
      alert('保存失败: ' + (error as Error).message);
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
          onClick={handleSave}
          disabled={saving}
          className="px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {saving ? '保存中...' : '保存模板'}
        </button>
      </div>
    </div>
  );
};

export default ProfileTemplateEditor;
