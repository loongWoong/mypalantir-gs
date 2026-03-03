import { describe, it, expect } from 'vitest';
import { validateStructure, validateSemantics, validateModel } from './ontologyValidator';
import type { OntologyModel } from '../models/OntologyModel';

const baseEntity = (overrides: Partial<OntologyModel['entities'][0]> = {}) => ({
  id: 'e1',
  name: 'Entity1',
  label: '实体1',
  attributes: [{ name: 'id', type: 'string', required: true, unique: true }],
  ...overrides,
});

describe('ontologyValidator', () => {
  describe('validateStructure', () => {
    it('空版本号应返回错误', () => {
      const model: OntologyModel = {
        id: 'm1',
        name: 'Test',
        version: '',
        namespace: 'test',
        entities: [],
        relations: [],
      };
      const errors = validateStructure(model);
      expect(errors.some((e) => e.message.includes('版本号'))).toBe(true);
    });

    it('空命名空间应返回错误', () => {
      const model: OntologyModel = {
        id: 'm1',
        name: 'Test',
        version: '1.0',
        namespace: '',
        entities: [],
        relations: [],
      };
      const errors = validateStructure(model);
      expect(errors.some((e) => e.message.includes('命名空间'))).toBe(true);
    });

    it('合法模型应无结构错误', () => {
      const model: OntologyModel = {
        id: 'm1',
        name: 'Test',
        version: '1.0',
        namespace: 'test',
        entities: [baseEntity()],
        relations: [],
      };
      const errors = validateStructure(model);
      expect(errors.filter((e) => e.level === 'error')).toHaveLength(0);
    });

    it('实体名称重复应返回错误', () => {
      const model: OntologyModel = {
        id: 'm1',
        name: 'Test',
        version: '1.0',
        namespace: 'test',
        entities: [
          baseEntity({ id: 'e1', name: 'A' }),
          baseEntity({ id: 'e2', name: 'A' }),
        ],
        relations: [],
      };
      const errors = validateStructure(model);
      expect(errors.some((e) => e.message.includes('实体名称重复'))).toBe(true);
    });

    it('无效数据类型应返回错误', () => {
      const model: OntologyModel = {
        id: 'm1',
        name: 'Test',
        version: '1.0',
        namespace: 'test',
        entities: [
          baseEntity({
            attributes: [{ name: 'id', type: 'invalid_type', required: true }],
          }),
        ],
        relations: [],
      };
      const errors = validateStructure(model);
      expect(errors.some((e) => e.message.includes('数据类型无效'))).toBe(true);
    });
  });

  describe('validateSemantics', () => {
    it('关系引用不存在的源实体应返回错误', () => {
      const model: OntologyModel = {
        id: 'm1',
        name: 'Test',
        version: '1.0',
        namespace: 'test',
        entities: [baseEntity({ id: 'e1', name: 'A' })],
        relations: [
          {
            id: 'r1',
            name: 'linkToB',
            source: 'e1',
            target: 'e2',
            source_type: 'A',
            target_type: 'NonExistent',
            type: '1-N',
            direction: 'directed',
          },
        ],
      };
      const errors = validateSemantics(model);
      expect(errors.some((e) => e.message.includes('目标实体类型不存在'))).toBe(true);
    });

    it('自环关系应返回错误', () => {
      const model: OntologyModel = {
        id: 'm1',
        name: 'Test',
        version: '1.0',
        namespace: 'test',
        entities: [baseEntity({ id: 'e1', name: 'A' })],
        relations: [
          {
            id: 'r1',
            name: 'self',
            source: 'e1',
            target: 'e1',
            source_type: 'A',
            target_type: 'A',
            type: '1-N',
            direction: 'directed',
          },
        ],
      };
      const errors = validateSemantics(model);
      expect(errors.some((e) => e.message.includes('不允许自环'))).toBe(true);
    });

    it('合法关系应无语义错误', () => {
      const model: OntologyModel = {
        id: 'm1',
        name: 'Test',
        version: '1.0',
        namespace: 'test',
        entities: [
          baseEntity({ id: 'e1', name: 'A' }),
          baseEntity({ id: 'e2', name: 'B' }),
        ],
        relations: [
          {
            id: 'r1',
            name: 'toB',
            source: 'e1',
            target: 'e2',
            source_type: 'A',
            target_type: 'B',
            type: '1-N',
            direction: 'directed',
          },
        ],
      };
      const errors = validateSemantics(model);
      expect(errors.filter((e) => e.level === 'error')).toHaveLength(0);
    });
  });

  describe('validateModel', () => {
    it('综合返回结构错误与语义错误', () => {
      const model: OntologyModel = {
        id: 'm1',
        name: 'Test',
        version: '',
        namespace: 'test',
        entities: [baseEntity()],
        relations: [
          {
            id: 'r1',
            name: 'x',
            source: 'e1',
            target: 'e1',
            source_type: 'Entity1',
            target_type: 'Entity1',
            type: '1-N',
            direction: 'directed',
          },
        ],
      };
      const errors = validateModel(model);
      expect(errors.some((e) => e.message.includes('版本号'))).toBe(true);
      expect(errors.some((e) => e.message.includes('不允许自环'))).toBe(true);
    });
  });
});
