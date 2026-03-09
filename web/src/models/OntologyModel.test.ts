import { describe, it, expect } from 'vitest';
import { toApiFormat, fromApiFormat } from './OntologyModel';
import type { OntologyModel } from './OntologyModel';

describe('OntologyModel', () => {
  describe('toApiFormat', () => {
    it('将 version 和 namespace 映射到顶层', () => {
      const model: OntologyModel = {
        id: 'm1',
        name: 'Test',
        version: '1.0',
        namespace: 'test.ns',
        entities: [],
        relations: [],
      };
      const api = toApiFormat(model);
      expect(api.version).toBe('1.0');
      expect(api.namespace).toBe('test.ns');
      expect(api.object_types).toEqual([]);
      expect(api.link_types).toBeDefined();
    });

    it('将 entities 转为 object_types，attributes 转为 properties', () => {
      const model: OntologyModel = {
        id: 'm1',
        name: 'Test',
        version: '1.0',
        namespace: 'ns',
        entities: [
          {
            id: 'e1',
            name: 'Vehicle',
            label: '车辆',
            attributes: [
              { name: 'id', type: 'string', required: true },
              { name: 'plate', type: 'string', required: false },
            ],
          },
        ],
        relations: [],
      };
      const api = toApiFormat(model);
      expect(api.object_types).toHaveLength(1);
      expect(api.object_types[0].name).toBe('Vehicle');
      expect(api.object_types[0].properties).toHaveLength(2);
      expect(api.object_types[0].properties[0].name).toBe('id');
      expect(api.object_types[0].properties[0].data_type).toBe('string');
      expect(api.object_types[0].properties[0].required).toBe(true);
    });

    it('将 relation type 映射为 cardinality', () => {
      const model: OntologyModel = {
        id: 'm1',
        name: 'Test',
        version: '1.0',
        namespace: 'ns',
        entities: [
          { id: 'e1', name: 'A', label: 'A', attributes: [] },
          { id: 'e2', name: 'B', label: 'B', attributes: [] },
        ],
        relations: [
          {
            id: 'r1',
            name: 'link',
            source: 'e1',
            target: 'e2',
            source_type: 'A',
            target_type: 'B',
            type: '1-N',
            direction: 'directed',
          },
        ],
      };
      const api = toApiFormat(model);
      expect(api.link_types).toHaveLength(1);
      expect(api.link_types[0].cardinality).toBe('one-to-many');
    });
  });

  describe('fromApiFormat', () => {
    it('将 object_types 转为 entities', () => {
      const apiData = {
        version: '1.0',
        namespace: 'ns',
        object_types: [
          {
            name: 'Vehicle',
            display_name: '车辆',
            properties: [
              { name: 'id', data_type: 'string', required: true },
            ],
          },
        ],
        link_types: [],
      };
      const model = fromApiFormat(apiData);
      expect(model.entities).toHaveLength(1);
      expect(model.entities[0].name).toBe('Vehicle');
      expect(model.entities[0].label).toBe('车辆');
      expect(model.entities[0].attributes[0].name).toBe('id');
      expect(model.entities[0].attributes[0].type).toBe('string');
    });

    it('将 cardinality 转为 relation type', () => {
      const apiData = {
        version: '1.0',
        namespace: 'ns',
        object_types: [
          { name: 'A', properties: [] },
          { name: 'B', properties: [] },
        ],
        link_types: [
          {
            name: 'owns',
            source_type: 'A',
            target_type: 'B',
            cardinality: 'many-to-one',
            direction: 'directed',
          },
        ],
      };
      const model = fromApiFormat(apiData);
      expect(model.relations).toHaveLength(1);
      expect(model.relations[0].type).toBe('N-1');
    });

    it('空 object_types 时 entities 为空数组', () => {
      const model = fromApiFormat({ object_types: [], link_types: [] });
      expect(model.entities).toEqual([]);
      expect(model.relations).toEqual([]);
    });
  });
});
