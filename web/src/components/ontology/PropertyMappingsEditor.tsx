import { useState } from 'react';
import { PlusIcon, TrashIcon } from '@heroicons/react/24/outline';
import type { Entity } from '../../models/OntologyModel';

interface PropertyMappingsEditorProps {
  sourceEntity: Entity | null;
  targetEntity: Entity | null;
  propertyMappings: Record<string, string> | undefined;
  onChange: (propertyMappings: Record<string, string> | undefined) => void;
}

export function PropertyMappingsEditor({
  sourceEntity,
  targetEntity,
  propertyMappings,
  onChange,
}: PropertyMappingsEditorProps) {
  const [newSourceAttr, setNewSourceAttr] = useState('');
  const [newTargetAttr, setNewTargetAttr] = useState('');

  const mappings = propertyMappings || {};
  const mappingsEntries = Object.entries(mappings);

  const sourceAttributes = sourceEntity?.attributes || [];
  const targetAttributes = targetEntity?.attributes || [];

  const handleAddMapping = () => {
    if (!newSourceAttr || !newTargetAttr) {
      alert('请选择源属性和目标属性');
      return;
    }

    const updated = {
      ...mappings,
      [newSourceAttr]: newTargetAttr,
    };
    onChange(updated);
    setNewSourceAttr('');
    setNewTargetAttr('');
  };

  const handleRemoveMapping = (sourceAttr: string) => {
    const updated = { ...mappings };
    delete updated[sourceAttr];
    const hasMappings = Object.keys(updated).length > 0;
    onChange(hasMappings ? updated : undefined);
  };

  const handleClearAll = () => {
    onChange(undefined);
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div>
          <h4 className="text-sm font-medium text-gray-700">属性映射</h4>
          <p className="text-xs text-gray-500 mt-0.5">建立源实体属性到目标实体属性的映射关系</p>
        </div>
        {mappingsEntries.length > 0 && (
          <button
            onClick={handleClearAll}
            className="text-xs text-red-600 hover:text-red-700"
            title="清空所有映射"
          >
            清空
          </button>
        )}
      </div>

      {/* 当前映射列表 */}
      {mappingsEntries.length > 0 && (
        <div className="space-y-2 border rounded p-2 bg-gray-50">
          {mappingsEntries.map(([sourceAttr, targetAttr]) => (
            <div
              key={sourceAttr}
              className="flex items-center gap-2 p-2 bg-white border rounded text-sm"
            >
              <div className="flex-1 flex items-center gap-2">
                <span className="font-medium text-blue-600">{sourceEntity?.name || '源'}</span>
                <span className="text-gray-500">.</span>
                <span className="text-gray-700">{sourceAttr}</span>
                <span className="text-gray-400 mx-1">→</span>
                <span className="font-medium text-green-600">{targetEntity?.name || '目标'}</span>
                <span className="text-gray-500">.</span>
                <span className="text-gray-700">{targetAttr}</span>
              </div>
              <button
                onClick={() => handleRemoveMapping(sourceAttr)}
                className="text-red-500 hover:text-red-700"
                title="删除映射"
              >
                <TrashIcon className="w-4 h-4" />
              </button>
            </div>
          ))}
        </div>
      )}

      {/* 添加新映射 */}
      <div className="border rounded p-3 bg-gray-50">
        <div className="space-y-2">
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">
                源属性 ({sourceEntity?.name || '源实体'})
              </label>
              <select
                value={newSourceAttr}
                onChange={(e) => setNewSourceAttr(e.target.value)}
                className="w-full text-xs border rounded px-2 py-1.5"
              >
                <option value="">选择源属性</option>
                {sourceAttributes.map((attr) => (
                  <option key={attr.name} value={attr.name}>
                    {attr.display_name || attr.name}
                    {attr.required && ' *'}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">
                目标属性 ({targetEntity?.name || '目标实体'})
              </label>
              <select
                value={newTargetAttr}
                onChange={(e) => setNewTargetAttr(e.target.value)}
                className="w-full text-xs border rounded px-2 py-1.5"
              >
                <option value="">选择目标属性</option>
                {targetAttributes.map((attr) => (
                  <option key={attr.name} value={attr.name}>
                    {attr.display_name || attr.name}
                    {attr.required && ' *'}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <button
            onClick={handleAddMapping}
            disabled={!newSourceAttr || !newTargetAttr}
            className="w-full px-3 py-1.5 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed flex items-center justify-center gap-1"
          >
            <PlusIcon className="w-3 h-3" />
            添加映射
          </button>
        </div>
      </div>

      {mappingsEntries.length === 0 && (
        <div className="text-center text-xs text-gray-400 py-2">
          暂无属性映射，请添加映射关系
        </div>
      )}
    </div>
  );
}


