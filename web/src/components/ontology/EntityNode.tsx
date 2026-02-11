import { Handle, Position, type NodeProps } from 'reactflow';
import type { Entity } from '../../models/OntologyModel';

interface EntityNodeData {
  entity: Entity;
  onEdit?: (entity: Entity) => void;
  onDelete?: (entityId: string) => void;
}

export function EntityNode({ data, selected }: NodeProps<EntityNodeData>) {
  const { entity } = data;
  const attributeCount = entity.attributes.length;

  return (
    <div
      className={`bg-white border-2 rounded-lg shadow-lg min-w-[200px] ${
        selected ? 'border-blue-500' : 'border-gray-300'
      }`}
    >
      <Handle type="target" position={Position.Top} className="w-3 h-3 bg-blue-500" />
      
      <div className="px-4 py-2 bg-blue-50 border-b border-gray-200">
        <div className="font-semibold text-sm text-gray-900">{entity.label || entity.name}</div>
        {entity.description && (
          <div className="text-xs text-gray-500 mt-1 truncate">{entity.description}</div>
        )}
      </div>
      
      <div className="px-4 py-2">
        <div className="text-xs text-gray-600">
          {attributeCount} 个属性
        </div>
        {entity.base_type && (
          <div className="text-xs text-gray-500 mt-1">
            继承自: {entity.base_type}
          </div>
        )}
      </div>

      <Handle type="source" position={Position.Bottom} className="w-3 h-3 bg-blue-500" />
    </div>
  );
}

