import { describe, it, expect, vi, beforeEach } from 'vitest';

const { mockGet, mockPost } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPost: vi.fn(),
}));

vi.mock('axios', () => ({
  default: {
    create: () => ({
      get: mockGet,
      post: mockPost,
      put: vi.fn(),
      delete: vi.fn(),
    }),
  },
}));

describe('schemaApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('getObjectTypes 返回 data 中的列表', async () => {
    const list = [{ name: 'Vehicle', display_name: '车辆', description: '', base_type: null, properties: [] }];
    mockGet.mockResolvedValueOnce({ data: { code: 200, data: list, message: 'success', timestamp: '' } });

    const { schemaApi } = await import('./client');
    const result = await schemaApi.getObjectTypes();

    expect(result).toEqual(list);
    expect(mockGet).toHaveBeenCalledWith('/schema/object-types');
  });

  it('getObjectType 请求指定 name', async () => {
    const ot = { name: 'Vehicle', display_name: '车辆', description: '', base_type: null, properties: [] };
    mockGet.mockResolvedValueOnce({ data: { code: 200, data: ot, message: 'success', timestamp: '' } });

    const { schemaApi } = await import('./client');
    const result = await schemaApi.getObjectType('Vehicle');

    expect(result).toEqual(ot);
    expect(mockGet).toHaveBeenCalledWith('/schema/object-types/Vehicle');
  });

  it('getLinkTypes 返回 link 类型列表', async () => {
    const links = [{ name: 'owns', source_type: 'Vehicle', target_type: 'Person', description: '', cardinality: 'many-to-one', direction: 'forward', properties: [] }];
    mockGet.mockResolvedValueOnce({ data: { code: 200, data: links, message: 'success', timestamp: '' } });

    const { schemaApi } = await import('./client');
    const result = await schemaApi.getLinkTypes();

    expect(result).toEqual(links);
  });
});

describe('modelApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('listModels 返回模型列表', async () => {
    const list = [{ id: 'm1', path: '/path/to.yaml', displayName: 'Test Model' }];
    mockGet.mockResolvedValueOnce({ data: { code: 200, data: list, message: 'success', timestamp: '' } });

    const { modelApi } = await import('./client');
    const result = await modelApi.listModels();

    expect(result).toEqual(list);
    expect(mockGet).toHaveBeenCalledWith('/models');
  });

  it('getCurrentModel 返回当前模型', async () => {
    const current = { modelId: 'm1', filePath: '/path/to.yaml' };
    mockGet.mockResolvedValueOnce({ data: { code: 200, data: current, message: 'success', timestamp: '' } });

    const { modelApi } = await import('./client');
    const result = await modelApi.getCurrentModel();

    expect(result).toEqual(current);
    expect(mockGet).toHaveBeenCalledWith('/models/current');
  });

  it('switchModel 发送 POST 并返回当前模型', async () => {
    const current = { modelId: 'm2', filePath: '/path/to2.yaml' };
    mockPost.mockResolvedValueOnce({ data: { code: 200, data: current, message: 'success', timestamp: '' } });

    const { modelApi } = await import('./client');
    const result = await modelApi.switchModel('m2');

    expect(result).toEqual(current);
    expect(mockPost).toHaveBeenCalledWith('/models/m2/switch');
  });
});

describe('instanceApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('list 返回 items 与 total', async () => {
    const data = { items: [{ id: 'i1', name: 'Instance1' }], total: 1, offset: 0, limit: 20 };
    mockGet.mockResolvedValueOnce({ data: { code: 200, data, message: 'success', timestamp: '' } });

    const { instanceApi } = await import('./client');
    const result = await instanceApi.list('workspace', 0, 100);

    expect(result.items).toHaveLength(1);
    expect(result.items[0].id).toBe('i1');
    expect(result.total).toBe(1);
    expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('/instances/workspace?'));
    expect(mockGet).toHaveBeenCalledWith(expect.stringMatching(/offset=0&limit=100/));
  });

  it('create 发送 POST 并返回 id', async () => {
    mockPost.mockResolvedValueOnce({ data: { code: 200, data: { id: 'new-id' }, message: 'success', timestamp: '' } });

    const { instanceApi } = await import('./client');
    const result = await instanceApi.create('workspace', { name: 'New WS' });

    expect(result).toBe('new-id');
    expect(mockPost).toHaveBeenCalledWith('/instances/workspace', { name: 'New WS' });
  });

  it('get 请求指定对象类型与 id', async () => {
    const inst = { id: 'w1', name: 'Workspace1' };
    mockGet.mockResolvedValueOnce({ data: { code: 200, data: inst, message: 'success', timestamp: '' } });

    const { instanceApi } = await import('./client');
    const result = await instanceApi.get('workspace', 'w1');

    expect(result).toEqual(inst);
    expect(mockGet).toHaveBeenCalledWith('/instances/workspace/w1');
  });
});

describe('linkApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('create 发送 source_id 与 target_id', async () => {
    mockPost.mockResolvedValueOnce({ data: { code: 200, data: { id: 'link-1' }, message: 'success', timestamp: '' } });

    const { linkApi } = await import('./client');
    const result = await linkApi.create('owns', 'src-1', 'tgt-1');

    expect(result).toBe('link-1');
    expect(mockPost).toHaveBeenCalledWith('/links/owns', {
      source_id: 'src-1',
      target_id: 'tgt-1',
      properties: {},
    });
  });

  it('list 请求 offset 与 limit', async () => {
    const data = { items: [], total: 0, offset: 0, limit: 20 };
    mockGet.mockResolvedValueOnce({ data: { code: 200, data, message: 'success', timestamp: '' } });

    const { linkApi } = await import('./client');
    await linkApi.list('owns', 10, 5);

    expect(mockGet).toHaveBeenCalledWith(expect.stringMatching(/offset=10&limit=5/));
  });
});

describe('queryApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('execute 发送 query 并返回 data', async () => {
    const query = { from: 'Vehicle', select: ['id'], limit: 10 };
    const resultData = { columns: ['id'], rows: [{ id: '1' }], rowCount: 1 };
    mockPost.mockResolvedValueOnce({ data: { code: 200, data: resultData, message: 'success', timestamp: '' } });

    const { queryApi } = await import('./client');
    const result = await queryApi.execute(query);

    expect(result).toEqual(resultData);
    expect(mockPost).toHaveBeenCalledWith('/query', query);
  });
});

describe('ApiResponse 结构', () => {
  it('接口约定为 code, message, data, timestamp', () => {
    const response = { code: 200, message: 'success', data: [], timestamp: '2024-01-01T00:00:00Z' };
    expect(response).toHaveProperty('code');
    expect(response).toHaveProperty('message');
    expect(response).toHaveProperty('data');
    expect(response).toHaveProperty('timestamp');
  });
});
