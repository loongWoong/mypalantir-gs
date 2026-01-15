import React, { useEffect, useState } from 'react';
import { useNode, type UserComponent } from '@craftjs/core';
import { BarChart, Bar, LineChart, Line, PieChart, Pie, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { metricApi } from '../../../api/metric';
import type { ChartWidgetProps } from '../../../types/profile';

export const ChartWidget: UserComponent<ChartWidgetProps> = (props) => {
  const {
    connectors: { connect, drag },
    selected,
  } = useNode((state) => ({
    selected: state.events.selected,
  }));

  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (props.metricId) {
      fetchChartData();
    }
  }, [props.metricId]);

  const fetchChartData = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await metricApi.calculateMetric({
        metric_id: props.metricId!,
        time_range: { start: '2024-01-01', end: '2024-12-31' },
        dimensions: {},
      });
      
      // 转换数据格式以适应图表
      setData(result.results || []);
    } catch (err) {
      console.error('Failed to fetch chart data:', err);
      setError('加载失败');
    } finally {
      setLoading(false);
    }
  };

  const renderChart = () => {
    if (loading) return <div className="text-gray-400 text-center py-12">加载中...</div>;
    if (error) return <div className="text-red-500 text-center py-12">{error}</div>;
    if (!data || data.length === 0) return <div className="text-gray-400 text-center py-12">暂无数据</div>;

    const chartProps = {
      data,
      margin: { top: 5, right: 30, left: 20, bottom: 5 },
    };

    switch (props.chartType) {
      case 'bar':
        return (
          <ResponsiveContainer width="100%" height={300}>
            <BarChart {...chartProps}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey={props.xAxis || 'name'} />
              <YAxis />
              <Tooltip />
              {props.showLegend && <Legend />}
              <Bar dataKey={props.yAxis || 'value'} fill={props.colors?.[0] || '#3b82f6'} />
            </BarChart>
          </ResponsiveContainer>
        );
      case 'line':
        return (
          <ResponsiveContainer width="100%" height={300}>
            <LineChart {...chartProps}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey={props.xAxis || 'name'} />
              <YAxis />
              <Tooltip />
              {props.showLegend && <Legend />}
              <Line type="monotone" dataKey={props.yAxis || 'value'} stroke={props.colors?.[0] || '#3b82f6'} />
            </LineChart>
          </ResponsiveContainer>
        );
      case 'pie':
        return (
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie dataKey={props.yAxis || 'value'} data={data} fill={props.colors?.[0] || '#3b82f6'} label />
              <Tooltip />
              {props.showLegend && <Legend />}
            </PieChart>
          </ResponsiveContainer>
        );
      default:
        return <div className="text-gray-400 text-center py-12">请选择图表类型</div>;
    }
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
      <h3 className="text-lg font-semibold mb-4">{props.title || '图表'}</h3>
      {renderChart()}
    </div>
  );
};

// 属性编辑器
const ChartWidgetSettings = () => {
  const {
    actions: { setProp },
    metricId,
    title,
    chartType,
    xAxis,
    yAxis,
    showLegend,
  } = useNode((node) => ({
    metricId: node.data.props.metricId,
    title: node.data.props.title,
    chartType: node.data.props.chartType,
    xAxis: node.data.props.xAxis,
    yAxis: node.data.props.yAxis,
    showLegend: node.data.props.showLegend,
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
          onChange={(e) => setProp((props: ChartWidgetProps) => (props.title = e.target.value))}
          className="w-full p-2 border rounded text-sm"
        />
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">选择指标</label>
        <select
          value={metricId || ''}
          onChange={(e) => setProp((props: ChartWidgetProps) => (props.metricId = e.target.value))}
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
        <label className="block text-sm font-medium mb-1">图表类型</label>
        <select
          value={chartType || 'bar'}
          onChange={(e) => setProp((props: ChartWidgetProps) => (props.chartType = e.target.value as any))}
          className="w-full p-2 border rounded text-sm"
        >
          <option value="bar">柱状图</option>
          <option value="line">折线图</option>
          <option value="pie">饼图</option>
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">X轴字段</label>
        <input
          type="text"
          value={xAxis || ''}
          onChange={(e) => setProp((props: ChartWidgetProps) => (props.xAxis = e.target.value))}
          className="w-full p-2 border rounded text-sm"
          placeholder="默认: name"
        />
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">Y轴字段</label>
        <input
          type="text"
          value={yAxis || ''}
          onChange={(e) => setProp((props: ChartWidgetProps) => (props.yAxis = e.target.value))}
          className="w-full p-2 border rounded text-sm"
          placeholder="默认: value"
        />
      </div>

      <div>
        <label className="flex items-center space-x-2">
          <input
            type="checkbox"
            checked={showLegend !== false}
            onChange={(e) => setProp((props: ChartWidgetProps) => (props.showLegend = e.target.checked))}
          />
          <span className="text-sm">显示图例</span>
        </label>
      </div>
    </div>
  );
};

// Craft.js 配置
ChartWidget.craft = {
  displayName: '图表',
  props: {
    title: '图表标题',
    chartType: 'bar',
    showLegend: true,
    colors: ['#3b82f6'],
  },
  related: {
    settings: ChartWidgetSettings,
  },
};
