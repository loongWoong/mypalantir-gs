/**
 * 统一的中间元模型（Graph Meta Model）
 * 这是前端内部使用的统一数据结构，不直接操作YML
 */

export interface OntologyModel {
  id: string;
  name: string;
  version: string;
  namespace: string;
  entities: Entity[];
  relations: Relation[];
  metadata?: Record<string, any>;
}

export interface Entity {
  id: string;
  name: string;
  display_name?: string;
  label: string;
  description?: string;
  base_type?: string;
  attributes: Attribute[];
  position?: { x: number; y: number }; // 画布位置
  metadata?: Record<string, any>;
  data_source?: any;
  url?: string;
}

export interface Attribute {
  name: string;
  display_name?: string;
  type: string;
  required: boolean;
  unique?: boolean;
  description?: string;
  default_value?: any;
  constraints?: Record<string, any>;
}

export interface Relation {
  id: string;
  name: string;
  display_name?: string;
  description?: string;
  source: string; // Entity ID
  target: string; // Entity ID
  source_type: string; // Entity name
  target_type: string; // Entity name
  type: '1-1' | '1-N' | 'N-1' | 'N-N';
  direction: 'directed' | 'undirected';
  properties?: Attribute[];
  property_mappings?: Record<string, string>;
  metadata?: Record<string, any>;
  data_source?: any;
  url?: string;
}

/**
 * 将前端模型转换为后端API格式
 */
export function toApiFormat(model: OntologyModel): any {
  return {
    version: model.version,
    namespace: model.namespace,
    object_types: model.entities.map((entity) => ({
      name: entity.name,
      display_name: entity.display_name,
      description: entity.description,
      base_type: entity.base_type,
      properties: entity.attributes.map((attr) => {
        const constraints = { ...attr.constraints };
        // 将unique字段放入constraints中，因为后端从constraints中读取unique
        if (attr.unique !== undefined) {
          constraints.unique = attr.unique;
        }
        const property: any = {
          name: attr.name,
          data_type: attr.type,
          required: attr.required,
          description: attr.description,
          default_value: attr.default_value,
          constraints: Object.keys(constraints).length > 0 ? constraints : null,
        };
        // 只有当display_name存在时才添加
        if (attr.display_name !== undefined && attr.display_name !== null && attr.display_name !== '') {
          property.display_name = attr.display_name;
        }
        return property;
      }),
      data_source: entity.data_source,
      url: entity.url,
    })),
    link_types: model.relations.map((relation) => {
      const linkType: any = {
        name: relation.name,
        display_name: relation.display_name,
        description: relation.description,
        source_type: relation.source_type,
        target_type: relation.target_type,
        cardinality: mapRelationTypeToCardinality(relation.type),
        direction: relation.direction,
        properties: relation.properties?.map((prop) => {
          const property: any = {
            name: prop.name,
            data_type: prop.type,
            required: prop.required,
            description: prop.description,
            default_value: prop.default_value,
            constraints: prop.constraints,
          };
          // 只有当display_name存在时才添加
          if (prop.display_name !== undefined && prop.display_name !== null && prop.display_name !== '') {
            property.display_name = prop.display_name;
          }
          return property;
        }),
        data_source: relation.data_source,
        url: relation.url,
      };
      // 只有当property_mappings存在时才添加
      if (relation.property_mappings !== undefined && relation.property_mappings !== null) {
        // 如果property_mappings是空对象，设置为null；否则保留
        if (Object.keys(relation.property_mappings).length > 0) {
          linkType.property_mappings = relation.property_mappings;
        } else {
          linkType.property_mappings = null;
        }
      }
      return linkType;
    }),
    data_sources: [],
  };
}

/**
 * 将后端API格式转换为前端模型
 */
export function fromApiFormat(apiData: any): OntologyModel {
  const entities: Entity[] = (apiData.object_types || []).map((ot: any, index: number) => ({
    id: `entity-${index}`,
    name: ot.name,
    display_name: ot.display_name,
    label: ot.display_name || ot.name,
    description: ot.description,
    base_type: ot.base_type,
    attributes: (ot.properties || []).map((prop: any) => {
      // 从constraints中提取unique字段，如果存在的话
      let unique = prop.unique;
      if (prop.constraints && prop.constraints.unique !== undefined) {
        unique = prop.constraints.unique;
      }
      const constraints = { ...prop.constraints };
      // 从constraints中移除unique，因为我们在顶层处理它
      if (constraints && constraints.unique !== undefined) {
        delete constraints.unique;
      }
      return {
        name: prop.name,
        display_name: prop.display_name,
        type: prop.data_type,
        required: prop.required || false,
        unique: unique,
        description: prop.description,
        default_value: prop.default_value,
        constraints: Object.keys(constraints || {}).length > 0 ? constraints : undefined,
      };
    }),
    data_source: ot.data_source,
    url: ot.url,
  }));

  const relations: Relation[] = (apiData.link_types || []).map((lt: any, index: number) => ({
    id: `relation-${index}`,
    name: lt.name,
    display_name: lt.display_name,
    description: lt.description,
    source: entities.find((e) => e.name === lt.source_type)?.id || '',
    target: entities.find((e) => e.name === lt.target_type)?.id || '',
    source_type: lt.source_type,
    target_type: lt.target_type,
    type: mapCardinalityToRelationType(lt.cardinality),
    direction: lt.direction || 'directed',
    properties: (lt.properties || []).map((prop: any) => ({
      name: prop.name,
      display_name: prop.display_name,
      type: prop.data_type,
      required: prop.required || false,
      description: prop.description,
      default_value: prop.default_value,
      constraints: prop.constraints,
    })),
    property_mappings: lt.property_mappings || undefined,
    data_source: lt.data_source,
    url: lt.url,
  }));

  return {
    id: `model-${Date.now()}`,
    name: apiData.name || 'Untitled Model',
    version: apiData.version || '1.0.0',
    namespace: apiData.namespace || 'ontology.builder',
    entities,
    relations,
    metadata: apiData.metadata,
  };
}

function mapRelationTypeToCardinality(type: Relation['type']): string {
  const mapping: Record<string, string> = {
    '1-1': 'one-to-one',
    '1-N': 'one-to-many',
    'N-1': 'many-to-one',
    'N-N': 'many-to-many',
  };
  return mapping[type] || 'one-to-many';
}

function mapCardinalityToRelationType(cardinality: string): Relation['type'] {
  const mapping: Record<string, Relation['type']> = {
    'one-to-one': '1-1',
    'one-to-many': '1-N',
    'many-to-one': 'N-1',
    'many-to-many': 'N-N',
  };
  return mapping[cardinality] || '1-N';
}

/**
 * 创建默认的实体
 */
export function createDefaultEntity(): Entity {
  return {
    id: `entity-${Date.now()}`,
    name: '',
    label: '',
    description: '',
    attributes: [
      {
        name: 'id',
        type: 'string',
        required: true,
        unique: true,
        description: '主键',
      },
    ],
    position: { x: 0, y: 0 },
  };
}

/**
 * 创建默认的关系
 */
export function createDefaultRelation(sourceId: string, targetId: string, sourceType: string, targetType: string): Relation {
  return {
    id: `relation-${Date.now()}`,
    name: '',
    description: '',
    source: sourceId,
    target: targetId,
    source_type: sourceType,
    target_type: targetType,
    type: '1-N',
    direction: 'directed',
    properties: [],
  };
}

