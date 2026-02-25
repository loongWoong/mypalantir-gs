import { useState } from 'react';
import type { Attribute } from '../../models/OntologyModel';
import { TrashIcon, PlusIcon } from '@heroicons/react/24/outline';

interface PropertyEditorProps {
  attributes: Attribute[];
  onChange: (attributes: Attribute[]) => void;
}

const DATA_TYPES = [
  'string',
  'int',
  'long',
  'float',
  'double',
  'bigdecimal',
  'bool',
  'date',
  'datetime',
  'json',
  'array',
];

export function PropertyEditor({ attributes, onChange }: PropertyEditorProps) {
  const [editingIndex, setEditingIndex] = useState<number | null>(null);

  const addAttribute = () => {
    const newAttr: Attribute = {
      name: '',
      type: 'string',
      required: false,
      description: '',
    };
    onChange([...attributes, newAttr]);
    setEditingIndex(attributes.length);
  };

  const updateAttribute = (index: number, updates: Partial<Attribute>) => {
    const updated = attributes.map((attr, i) => (i === index ? { ...attr, ...updates } : attr));
    onChange(updated);
  };

  const removeAttribute = (index: number) => {
    onChange(attributes.filter((_, i) => i !== index));
    if (editingIndex === index) {
      setEditingIndex(null);
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <div>
          <h4 className="text-sm font-medium text-gray-700">属性列表</h4>
          <p className="text-xs text-gray-500 mt-0.5">提示：同时勾选"必填"和"唯一"可设置为主键</p>
        </div>
        <button
          onClick={addAttribute}
          className="text-blue-600 hover:text-blue-700 text-sm flex items-center gap-1"
        >
          <PlusIcon className="w-4 h-4" />
          添加属性
        </button>
      </div>

      <div className="space-y-2 max-h-96 overflow-y-auto">
        {attributes.map((attr, index) => (
          <div
            key={index}
            className={`border rounded p-2 ${
              editingIndex === index ? 'border-blue-500 bg-blue-50' : 'border-gray-200'
            }`}
          >
            <div className="grid grid-cols-12 gap-2 items-center">
              <div className="col-span-3">
                <input
                  type="text"
                  placeholder="属性名"
                  value={attr.name}
                  onChange={(e) => updateAttribute(index, { name: e.target.value })}
                  className="w-full text-xs border rounded px-2 py-1"
                  onFocus={() => setEditingIndex(index)}
                />
              </div>
              <div className="col-span-3">
                <select
                  value={attr.type}
                  onChange={(e) => updateAttribute(index, { type: e.target.value })}
                  className="w-full text-xs border rounded px-2 py-1"
                  onFocus={() => setEditingIndex(index)}
                >
                  {DATA_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
              </div>
              <div className="col-span-2 flex items-center gap-1">
                <input
                  type="checkbox"
                  checked={attr.required || false}
                  onChange={(e) => updateAttribute(index, { required: e.target.checked })}
                  className="w-4 h-4"
                  title="必填字段"
                />
                <label className="text-xs text cursor-pointer" onClick={() => updateAttribute(index, { required: !attr.required })}>必填</label>
              </div>
              <div className="col-span-2 flex items-center gap-1">
                <input
                  type="checkbox"
                  checked={attr.unique || false}
                  onChange={(e) => updateAttribute(index, { unique: e.target.checked })}
                  className="w-4 h-4"
                  title="唯一字段（与必填组合可设置为主键）"
                />
                <label className="text-xs text cursor-pointer" onClick={() => updateAttribute(index, { unique: !attr.unique })}>唯一</label>
              </div>
              <div className="col-span-1 flex items-center gap-1">
                {attr.required && attr.unique && (
                  <span className="text-xs text-green-600 font-medium" title="主键：必填且唯一">🔑</span>
                )}
              </div>
              <div className="col-span-1">
                <button
                  onClick={() => removeAttribute(index)}
                  className="text-red-500 hover:text-red-700"
                  title="删除属性"
                >
                  <TrashIcon className="w-4 h-4" />
                </button>
              </div>
            </div>
            {editingIndex === index && (
              <div className="mt-2 space-y-1">
                <input
                  type="text"
                  placeholder="显示名称（可选）"
                  value={attr.display_name || ''}
                  onChange={(e) => updateAttribute(index, { display_name: e.target.value })}
                  className="w-full text-xs border rounded px-2 py-1"
                />
                <input
                  type="text"
                  placeholder="描述（可选）"
                  value={attr.description || ''}
                  onChange={(e) => updateAttribute(index, { description: e.target.value })}
                  className="w-full text-xs border rounded px-2 py-1"
                />
                <input
                  type="text"
                  placeholder="默认值（可选）"
                  value={attr.default_value || ''}
                  onChange={(e) => updateAttribute(index, { default_value: e.target.value })}
                  className="w-full text-xs border rounded px-2 py-1"
                />
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

