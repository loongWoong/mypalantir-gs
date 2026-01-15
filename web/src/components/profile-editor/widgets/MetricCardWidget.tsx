import React, { useEffect, useState } from 'react';
import { useNode, type UserComponent } from '@craftjs/core';
import { metricApi } from '../../../api/metric';
import type { MetricCardWidgetProps } from '../../../types/profile';

export const MetricCardWidget: UserComponent<MetricCardWidgetProps> = (props) => {
  const {
    connectors: { connect, drag },
    selected,
  } = useNode((state) => ({
    selected: state.events.selected,
  }));

  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 预览模式：实时获取数据
  useEffect(() => {
    if (props.metricId) {
      fetchMetricData();
    }
  }, [props.metricId]);

  const fetchMetricData = async () => {
    setLoading(true);
    setError(null);
    try {
      // 使用示例时间范围，实际使用时需要从上下文获取
      const result = await metricApi.calculateMetric({
        metric_id: props.metricId!,
        time_range: { start: '2024-01-01', end: '2024-12-31' },
        dimensions: {},
      });
      setData(result);
    } catch (err) {
      console.error('Failed to fetch metric data:', err);
      setError('加载失败');
    } finally {
      setLoading(false);
    }
  };

  const formatValue = (value: number) => {
    if (value === null || value === undefined) return '-';
    
    switch (props.format) {
      case 'currency':
        return `¥${value.toFixed(props.precision || 2)}`;
      case 'percentage':
        return `${(value * 100).toFixed(props.precision || 1)}%`;
      default:
        return value.toLocaleString(undefined, {
          minimumFractionDigits: props.precision || 0,
          maximumFractionDigits: props.precision || 0,
        });
    }
  };

  const getValue = () => {
    if (!data?.results || data.results.length === 0) return null;
    
    const firstResult = data.results[0];
    // 支持两种格式：metricValue 或者直接取第一个数值字段
    if (firstResult.metricValue !== undefined) {
      return firstResult.metricValue;
    }
    
    // 尝试从原始结果中提取数值
    const numericFields = Object.keys(firstResult).filter(
      key => typeof firstResult[key] === 'number'
    );
    return numericFields.length > 0 ? firstResult[numericFields[0]] : null;
  };

  return (
    <div
      ref={(ref) => {
        if (ref) {
          connect(drag(ref));
        }
      }}
      className={`
        bg-white rounded-lg shadow p-6 border-2 transition-colors cursor-move
        ${selected ? 'border-blue-500' : 'border-transparent'}
        hover:border-blue-300
      `}
    >
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm text-gray-600">{props.title || '指标卡片'}</span>
        {props.icon && <span className="text-2xl">{props.icon}</span>}
      </div>
      
      <div className="flex items-baseline">
        {loading ? (
          <div className="text-gray-400">加载中...</div>
        ) : error ? (
          <div className="text-red-500 text-sm">{error}</div>
        ) : (
          <>
            <span className="text-3xl font-bold text-gray-900">
              {getValue() !== null ? formatValue(getValue()!) : '-'}
            </span>
            {props.unit && (
              <span className="ml-2 text-gray-600">{props.unit}</span>
            )}
          </>
        )}
      </div>
    </div>
  );
};

// 属性编辑器
const MetricCardSettings = () => {
  const {
    actions: { setProp },
    metricId,
    title,
    format,
    unit,
    icon,
    precision,
  } = useNode((node) => ({
    metricId: node.data.props.metricId,
    title: node.data.props.title,
    format: node.data.props.format,
    unit: node.data.props.unit,
    icon: node.data.props.icon,
    precision: node.data.props.precision,
  }));

  const [metrics, setMetrics] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadMetrics();
  }, []);

  const loadMetrics = async () => {
    setLoading(true);
    try {
      const [atomicMetrics, derivedMetrics] = await Promise.all([
        metricApi.listAtomicMetrics(),
        metricApi.listMetricDefinitions(),
      ]);
      setMetrics([...atomicMetrics, ...derivedMetrics]);
    } catch (error) {
      console.error('Failed to load metrics:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-4">
      <div>
        <label className="block text-sm font-medium mb-1">标题</label>
        <input
          type="text"
          value={title || ''}
          onChange={(e) => setProp((props: MetricCardWidgetProps) => (props.title = e.target.value))}
          className="w-full p-2 border rounded text-sm"
        />
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">选择指标</label>
        <select
          value={metricId || ''}
          onChange={(e) => setProp((props: MetricCardWidgetProps) => (props.metricId = e.target.value))}
          className="w-full p-2 border rounded text-sm"
          disabled={loading}
        >
          <option value="">请选择指标</option>
          {metrics.map((m) => (
            <option key={m.id} value={m.id}>
              {m.displayName || m.display_name || m.name}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">格式</label>
        <select
          value={format || 'number'}
          onChange={(e) => setProp((props: MetricCardWidgetProps) => (props.format = e.target.value as any))}
          className="w-full p-2 border rounded text-sm"
        >
          <option value="number">数字</option>
          <option value="currency">货币</option>
          <option value="percentage">百分比</option>
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">单位</label>
        <input
          type="text"
          value={unit || ''}
          onChange={(e) => setProp((props: MetricCardWidgetProps) => (props.unit = e.target.value))}
          className="w-full p-2 border rounded text-sm"
          placeholder="如：元、次、个"
        />
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">图标 (Emoji)</label>
        <input
          type="text"
          value={icon || ''}
          onChange={(e) => setProp((props: MetricCardWidgetProps) => (props.icon = e.target.value))}
          className="w-full p-2 border rounded text-sm"
          placeholder="如：📊 💰 🚗"
        />
      </div>

      {(format === 'currency' || format === 'percentage') && (
        <div>
          <label className="block text-sm font-medium mb-1">精度（小数位）</label>
          <input
            type="number"
            min="0"
            max="4"
            value={precision || 0}
            onChange={(e) =>
              setProp((props: MetricCardWidgetProps) => (props.precision = parseInt(e.target.value)))
            }
            className="w-full p-2 border rounded text-sm"
          />
        </div>
      )}
    </div>
  );
};

// Craft.js 配置
MetricCardWidget.craft = {
  displayName: '指标卡片',
  props: {
    title: '指标标题',
    format: 'number',
    precision: 0,
  },
  related: {
    settings: MetricCardSettings,
  },
};
