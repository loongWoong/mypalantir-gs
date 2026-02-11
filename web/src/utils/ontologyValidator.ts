/**
 * 本体模型校验工具
 * 包含结构校验和语义校验
 */

import type { OntologyModel, Entity, Attribute } from '../models/OntologyModel';

export interface ValidationError {
  level: 'error' | 'warning';
  message: string;
  entityId?: string;
  relationId?: string;
  attributeName?: string;
}

/**
 * 结构校验：检查必填字段、字段类型、唯一ID等
 */
export function validateStructure(model: OntologyModel): ValidationError[] {
  const errors: ValidationError[] = [];

  // 校验模型基本信息
  if (!model.version || model.version.trim() === '') {
    errors.push({ level: 'error', message: '版本号不能为空' });
  }

  if (!model.namespace || model.namespace.trim() === '') {
    errors.push({ level: 'error', message: '命名空间不能为空' });
  }

  // 校验实体名称唯一性
  const entityNames = new Set<string>();
  const entityIds = new Set<string>();

  model.entities.forEach((entity, index) => {
    if (!entity.id || entity.id.trim() === '') {
      errors.push({ level: 'error', message: `实体[${index}]缺少ID` });
    } else if (entityIds.has(entity.id)) {
      errors.push({ level: 'error', message: `实体ID重复: ${entity.id}`, entityId: entity.id });
    } else {
      entityIds.add(entity.id);
    }

    if (!entity.name || entity.name.trim() === '') {
      errors.push({ level: 'error', message: `实体[${index}]名称不能为空`, entityId: entity.id });
    } else if (!isValidName(entity.name)) {
      errors.push({ level: 'error', message: `实体[${index}]名称格式无效: ${entity.name}`, entityId: entity.id });
    } else if (entityNames.has(entity.name)) {
      errors.push({ level: 'error', message: `实体名称重复: ${entity.name}`, entityId: entity.id });
    } else {
      entityNames.add(entity.name);
    }

    // 校验实体属性
    const propertyNames = new Set<string>();
    entity.attributes.forEach((attr, attrIndex) => {
      if (!attr.name || attr.name.trim() === '') {
        errors.push({ level: 'error', message: `实体${entity.name}的属性[${attrIndex}]名称不能为空`, entityId: entity.id });
      } else if (!isValidName(attr.name)) {
        errors.push({ level: 'error', message: `实体${entity.name}的属性${attr.name}名称格式无效`, entityId: entity.id, attributeName: attr.name });
      } else if (propertyNames.has(attr.name)) {
        errors.push({ level: 'error', message: `实体${entity.name}的属性名称重复: ${attr.name}`, entityId: entity.id, attributeName: attr.name });
      } else {
        propertyNames.add(attr.name);
      }

      if (!attr.type || !isValidDataType(attr.type)) {
        errors.push({ level: 'error', message: `实体${entity.name}的属性${attr.name}数据类型无效: ${attr.type}`, entityId: entity.id, attributeName: attr.name });
      }
    });

    // 检查是否有主键
    const hasPrimaryKey = entity.attributes.some((attr) => attr.required && attr.unique);
    if (!hasPrimaryKey) {
      errors.push({ level: 'warning', message: `实体${entity.name}缺少主键（required且unique的属性）`, entityId: entity.id });
    }
  });

  // 校验关系
  const relationIds = new Set<string>();
  const relationKeys = new Set<string>();

  model.relations.forEach((relation, index) => {
    if (!relation.id || relation.id.trim() === '') {
      errors.push({ level: 'error', message: `关系[${index}]缺少ID` });
    } else if (relationIds.has(relation.id)) {
      errors.push({ level: 'error', message: `关系ID重复: ${relation.id}`, relationId: relation.id });
    } else {
      relationIds.add(relation.id);
    }

    if (!relation.name || relation.name.trim() === '') {
      errors.push({ level: 'error', message: `关系[${index}]名称不能为空`, relationId: relation.id });
    } else if (!isValidName(relation.name)) {
      errors.push({ level: 'error', message: `关系[${index}]名称格式无效: ${relation.name}`, relationId: relation.id });
    }

    // 检查关系唯一性（同一对实体不能有多个同名关系）
    const relationKey = `${relation.source_type}::${relation.target_type}::${relation.name}`;
    if (relationKeys.has(relationKey)) {
      errors.push({ level: 'error', message: `关系重复: ${relation.name} (${relation.source_type} → ${relation.target_type})`, relationId: relation.id });
    } else {
      relationKeys.add(relationKey);
    }

    if (!relation.source || !relation.target) {
      errors.push({ level: 'error', message: `关系${relation.name}的源或目标实体ID不能为空`, relationId: relation.id });
    }

    if (!relation.source_type || !relation.target_type) {
      errors.push({ level: 'error', message: `关系${relation.name}的源或目标实体类型不能为空`, relationId: relation.id });
    }
  });

  return errors;
}

/**
 * 语义校验：检查业务规则
 */
export function validateSemantics(model: OntologyModel): ValidationError[] {
  const errors: ValidationError[] = [];

  // 构建实体名称映射
  const entityNameMap = new Map<string, Entity>();
  model.entities.forEach((entity) => {
    entityNameMap.set(entity.name, entity);
  });

  // 校验关系引用的实体是否存在
  model.relations.forEach((relation) => {
    if (!entityNameMap.has(relation.source_type)) {
      errors.push({ level: 'error', message: `关系${relation.name}引用的源实体类型不存在: ${relation.source_type}`, relationId: relation.id });
    }

    if (!entityNameMap.has(relation.target_type)) {
      errors.push({ level: 'error', message: `关系${relation.name}引用的目标实体类型不存在: ${relation.target_type}`, relationId: relation.id });
    }

    // 不允许自环
    if (relation.source_type === relation.target_type) {
      errors.push({ level: 'error', message: `关系${relation.name}不允许自环（源和目标不能相同）`, relationId: relation.id });
    }

    // 检查源和目标实体ID是否有效
    const sourceEntity = model.entities.find((e) => e.id === relation.source);
    const targetEntity = model.entities.find((e) => e.id === relation.target);

    if (!sourceEntity) {
      errors.push({ level: 'error', message: `关系${relation.name}的源实体ID无效: ${relation.source}`, relationId: relation.id });
    } else if (sourceEntity.name !== relation.source_type) {
      errors.push({ level: 'error', message: `关系${relation.name}的源实体ID与类型不匹配`, relationId: relation.id });
    }

    if (!targetEntity) {
      errors.push({ level: 'error', message: `关系${relation.name}的目标实体ID无效: ${relation.target}`, relationId: relation.id });
    } else if (targetEntity.name !== relation.target_type) {
      errors.push({ level: 'error', message: `关系${relation.name}的目标实体ID与类型不匹配`, relationId: relation.id });
    }
  });

  // 校验基础类型引用
  model.entities.forEach((entity) => {
    if (entity.base_type) {
      if (!entityNameMap.has(entity.base_type)) {
        errors.push({ level: 'error', message: `实体${entity.name}的基础类型不存在: ${entity.base_type}`, entityId: entity.id });
      } else if (entity.base_type === entity.name) {
        errors.push({ level: 'error', message: `实体${entity.name}不能将自身作为基础类型`, entityId: entity.id });
      }
    }
  });

  // 检查孤立实体（没有关系的实体）
  const connectedEntityNames = new Set<string>();
  model.relations.forEach((relation) => {
    connectedEntityNames.add(relation.source_type);
    connectedEntityNames.add(relation.target_type);
  });

  model.entities.forEach((entity) => {
    if (!connectedEntityNames.has(entity.name) && model.entities.length > 1) {
      errors.push({ level: 'warning', message: `实体${entity.name}是孤立实体（没有与其他实体的关系）`, entityId: entity.id });
    }
  });

  return errors;
}

/**
 * 综合校验
 */
export function validateModel(model: OntologyModel): ValidationError[] {
  const structureErrors = validateStructure(model);
  const semanticErrors = validateSemantics(model);
  return [...structureErrors, ...semanticErrors];
}

/**
 * 检查名称格式是否有效（字母开头，可包含字母、数字、下划线）
 */
function isValidName(name: string): boolean {
  if (!name || name.trim() === '') {
    return false;
  }
  return /^[\p{L}][\p{L}\p{N}_]*$/u.test(name);
}

/**
 * 检查数据类型是否有效
 */
function isValidDataType(dataType: string): boolean {
  if (!dataType) {
    return false;
  }

  const validTypes = ['string', 'int', 'long', 'float', 'double', 'bigdecimal', 'bool', 'date', 'datetime', 'json', 'array', 'integer'];

  // 支持数组类型 array<type>
  if (dataType.startsWith('array<') && dataType.endsWith('>')) {
    const innerType = dataType.substring(6, dataType.length - 1);
    return isValidDataType(innerType);
  }

  // 支持 integer 作为 int 的别名
  if (dataType === 'integer') {
    return true;
  }

  return validTypes.includes(dataType);
}

