import { describe, it, expect, vi, beforeEach } from 'vitest';

const { mockGet, mockPost } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPost: vi.fn(),
}));

vi.mock('./client', () => ({
  default: {
    get: mockGet,
    post: mockPost,
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('metricApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('listAtomicMetrics 返回原子指标列表', async () => {
    const list = [
      {
        id: 'm1',
        name: 'total_fee',
        displayName: '总费用',
        businessProcess: 'toll',
        aggregationFunction: 'sum',
        aggregationField: 'amount',
        status: 'active',
      },
    ];
    mockGet.mockResolvedValueOnce({ data: { code: 200, data: list, message: 'success', timestamp: '' } });

    const { metricApi } = await import('./metric');
    const result = await metricApi.listAtomicMetrics();

    expect(result).toEqual(list);
    expect(mockGet).toHaveBeenCalledWith('/metrics/atomic-metrics');
  });

  it('getAtomicMetric 请求指定 id', async () => {
    const metric = {
      id: 'm1',
      name: 'total_fee',
      businessProcess: 'toll',
      aggregationFunction: 'sum',
      status: 'active',
    };
    mockGet.mockResolvedValueOnce({ data: { code: 200, data: metric, message: 'success', timestamp: '' } });

    const { metricApi } = await import('./metric');
    const result = await metricApi.getAtomicMetric('m1');

    expect(result).toEqual(metric);
    expect(mockGet).toHaveBeenCalledWith('/metrics/atomic-metrics/m1');
  });

  it('calculateMetric 发送 query 并返回 MetricResult', async () => {
    const query = { metric_id: 'm1', time_range: { start: '2024-01-01', end: '2024-01-31' } };
    const resultData = {
      metricId: 'm1',
      metricName: 'total_fee',
      results: [{ timeValue: '2024-01', metricValue: 1000 }],
      calculatedAt: '2024-01-01T00:00:00Z',
    };
    mockPost.mockResolvedValueOnce({ data: { code: 200, data: resultData, message: 'success', timestamp: '' } });

    const { metricApi } = await import('./metric');
    const result = await metricApi.calculateMetric(query);

    expect(result).toEqual(resultData);
    expect(mockPost).toHaveBeenCalledWith('/metrics/calculate', query);
  });

  it('createAtomicMetric 发送 POST 并返回 id', async () => {
    mockPost.mockResolvedValueOnce({ data: { code: 200, data: { id: 'new-id' }, message: 'success', timestamp: '' } });

    const { metricApi } = await import('./metric');
    const result = await metricApi.createAtomicMetric({
      name: 'new_metric',
      businessProcess: 'bp',
      aggregationFunction: 'sum',
      aggregationField: 'amount',
      status: 'active',
    });

    expect(result).toEqual({ id: 'new-id' });
    expect(mockPost).toHaveBeenCalledWith('/metrics/atomic-metrics', expect.any(Object));
  });
});
