import { useState } from 'react';
import type { Attribute } from '../../models/OntologyModel';
import { TrashIcon, PlusIcon, DocumentTextIcon } from '@heroicons/react/24/outline';
import { CelExprBuilder } from './CelExprBuilder';
import type { EntityOption, RelationOption } from './CelExprBuilder';

interface PropertyEditorProps {
  attributes: Attribute[];
  onChange: (attributes: Attribute[]) => void;
  /** 当前实体名，用于 CEL 衍生属性（仅显示从该实体出发的关系） */
  currentEntityName?: string;
  /** 关系列表，用于 CEL 表达式中的 links.xxx */
  relations?: RelationOption[];
  /** 实体列表（含属性），用于 CEL 中关系目标实体的属性下拉 */
  entities?: EntityOption[];
}

const DATA_TYPES = [
  'string',
  'int',
  'long',
  'float',
  'double',
  'bigdecimal',
  'bool',
  'boolean',
  'date',
  'datetime',
  'json',
  'array',
];

export function PropertyEditor({
  attributes,
  onChange,
  currentEntityName = '',
  relations = [],
  entities = [],
}: PropertyEditorProps) {
  const relationsFromEntity =
    currentEntityName && relations.length > 0
      ? relations.filter((r) => r.source_type === currentEntityName)
      : relations;
  const derivedAttrs = attributes.filter((a) => a.derived === true);
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
              <div className="col-span-2">
                <input
                  type="text"
                  placeholder="属性名"
                  value={attr.name}
                  onChange={(e) => updateAttribute(index, { name: e.target.value })}
                  className="w-full text-xs border rounded px-2 py-1"
                  onFocus={() => setEditingIndex(index)}
                />
              </div>
              <div className="col-span-2">
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
              <div className="col-span-2 flex items-center gap-1">
                <input
                  type="checkbox"
                  checked={attr.derived || false}
                  onChange={(e) => updateAttribute(index, { derived: e.target.checked, ...(e.target.checked && !attr.expr ? { expr: '' } : {}) })}
                  className="w-4 h-4"
                  title="CEL 衍生属性，表达式在下方配置"
                />
                <label className="text-xs cursor-pointer" onClick={() => updateAttribute(index, { derived: !attr.derived })}>衍生</label>
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
                {attr.derived && (
                  <p className="text-xs text-blue-600">CEL 表达式请在下方「CEL 衍生属性配置」中填写</p>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {/* CEL 衍生属性配置：在属性列表下方，仅当存在衍生属性且提供了关系/实体时显示 */}
      {derivedAttrs.length > 0 && relationsFromEntity.length > 0 && entities.length > 0 && (
        <div className="border-t border-gray-200 pt-4 mt-4">
          <h4 className="text-sm font-medium text-gray-700 flex items-center gap-1 mb-2">
            <DocumentTextIcon className="w-4 h-4" />
            CEL 衍生属性配置
          </h4>
          <p className="text-xs text-gray-500 mb-3">为勾选「衍生」的属性配置 CEL 表达式，用于由关系与聚合计算得出属性值</p>
          <div className="space-y-4">
            {attributes.map((attr, index) =>
              attr.derived ? (
                <div key={index} className="border border-gray-200 rounded-lg p-3 bg-gray-50/50">
                  <div className="text-sm font-medium text-gray-800 mb-2">
                    {attr.display_name || attr.name || '未命名'}
                    {attr.name && <span className="text-gray-500 font-normal ml-1">({attr.name})</span>}
                  </div>
                  <CelExprBuilder
                    expr={attr.expr ?? ''}
                    onChange={(expr) => updateAttribute(index, { expr })}
                    relations={relationsFromEntity}
                    entities={entities}
                  />
                </div>
              ) : null
            )}
          </div>
        </div>
      )}
    </div>
  );
}

