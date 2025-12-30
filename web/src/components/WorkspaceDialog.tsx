import { useState, useEffect } from 'react';
import { XMarkIcon, PlusIcon, PencilIcon } from '@heroicons/react/24/outline';
import type { ObjectType, LinkType } from '../api/client';
import { schemaApi, instanceApi } from '../api/client';
import { useWorkspace } from '../WorkspaceContext';

interface WorkspaceDialogProps {
  workspaceId?: string;
  onClose: () => void;
  onSuccess: () => void;
}

export default function WorkspaceDialog({ workspaceId, onClose, onSuccess }: WorkspaceDialogProps) {
  const { workspaces, updateWorkspace, refreshWorkspaces } = useWorkspace();
  const [name, setName] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [description, setDescription] = useState('');
  const [selectedObjectTypes, setSelectedObjectTypes] = useState<string[]>([]);
  const [selectedLinkTypes, setSelectedLinkTypes] = useState<string[]>([]);
  const [objectTypes, setObjectTypes] = useState<ObjectType[]>([]);
  const [linkTypes, setLinkTypes] = useState<LinkType[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const loadData = async () => {
      setLoading(true);
      try {
        const [otData, ltData] = await Promise.all([
          schemaApi.getObjectTypes(),
          schemaApi.getLinkTypes(),
        ]);
        setObjectTypes(otData);
        setLinkTypes(ltData);

        // 如果是编辑模式，加载现有数据
        if (workspaceId) {
          const ws = workspaces.find((w) => w.id === workspaceId);
          if (ws) {
            setName(ws.name || '');
            setDisplayName(ws.display_name || '');
            setDescription(ws.description || '');
            setSelectedObjectTypes(ws.object_types || []);
            setSelectedLinkTypes(ws.link_types || []);
          }
        }
      } catch (error) {
        console.error('Failed to load data:', error);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, [workspaceId, workspaces]);

  const handleObjectTypeToggle = (typeName: string) => {
    setSelectedObjectTypes((prev) =>
      prev.includes(typeName)
        ? prev.filter((t) => t !== typeName)
        : [...prev, typeName]
    );
  };

  const handleLinkTypeToggle = (typeName: string) => {
    setSelectedLinkTypes((prev) =>
      prev.includes(typeName)
        ? prev.filter((t) => t !== typeName)
        : [...prev, typeName]
    );
  };

  const handleSave = async () => {
    if (!name.trim()) {
      alert('请输入工作空间名称');
      return;
    }

    setSaving(true);
    try {
      const data: Record<string, any> = {
        name: name.trim(),
        display_name: displayName.trim() || undefined,
        description: description.trim() || undefined,
        object_types: selectedObjectTypes,
        link_types: selectedLinkTypes,
      };

      if (workspaceId) {
        // 更新现有工作空间
        await updateWorkspace(workspaceId, data);
      } else {
        // 创建新工作空间
        await instanceApi.create('workspace', data);
        // 创建后刷新工作空间列表
        await refreshWorkspaces();
      }
      onSuccess();
      onClose();
    } catch (error: any) {
      console.error('Failed to save workspace:', error);
      alert('保存失败: ' + (error.response?.data?.message || error.message));
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
        <div className="bg-white rounded-lg p-6">
          <div className="text-center">加载中...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-4xl max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h2 className="text-xl font-bold text-gray-900">
            {workspaceId ? '编辑工作空间' : '创建工作空间'}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <XMarkIcon className="w-6 h-6" />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          <div className="space-y-6">
            {/* 基本信息 */}
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  工作空间名称 <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="例如: 默认工作空间"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  显示名称
                </label>
                <input
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="例如: 默认工作空间"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  描述
                </label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={3}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="工作空间描述..."
                />
              </div>
            </div>

            {/* Object Types 选择 */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-3">
                选择对象类型 ({selectedObjectTypes.length} 已选择)
              </label>
              <div className="border border-gray-300 rounded-lg p-4 max-h-64 overflow-y-auto">
                <div className="space-y-2">
                  {objectTypes.map((ot) => (
                    <label
                      key={ot.name}
                      className="flex items-center space-x-3 p-2 hover:bg-gray-50 rounded cursor-pointer"
                    >
                      <input
                        type="checkbox"
                        checked={selectedObjectTypes.includes(ot.name)}
                        onChange={() => handleObjectTypeToggle(ot.name)}
                        className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                      />
                      <div className="flex-1">
                        <div className="font-medium text-gray-900">
                          {ot.display_name || ot.name}
                        </div>
                        {ot.description && (
                          <div className="text-sm text-gray-500">{ot.description}</div>
                        )}
                      </div>
                    </label>
                  ))}
                </div>
              </div>
            </div>

            {/* Link Types 选择 */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-3">
                选择关系类型 ({selectedLinkTypes.length} 已选择)
              </label>
              <div className="border border-gray-300 rounded-lg p-4 max-h-64 overflow-y-auto">
                <div className="space-y-2">
                  {linkTypes.map((lt) => (
                    <label
                      key={lt.name}
                      className="flex items-center space-x-3 p-2 hover:bg-gray-50 rounded cursor-pointer"
                    >
                      <input
                        type="checkbox"
                        checked={selectedLinkTypes.includes(lt.name)}
                        onChange={() => handleLinkTypeToggle(lt.name)}
                        className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                      />
                      <div className="flex-1">
                        <div className="font-medium text-gray-900">
                          {lt.display_name || lt.name}
                        </div>
                        {lt.description && (
                          <div className="text-sm text-gray-500">{lt.description}</div>
                        )}
                      </div>
                    </label>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 p-6 border-t border-gray-200">
          <button
            onClick={onClose}
            className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
          >
            取消
          </button>
          <button
            onClick={handleSave}
            disabled={saving || !name.trim()}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>
    </div>
  );
}
