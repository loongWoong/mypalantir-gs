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
      const rawResults = result.results || [];
      
      // 如果是结构化格式（MetricDataPoint），转换为扁平格式
      const flattenedData = rawResults.map((item: any) => {
        // 检查是否是结构化格式
        if (item.metricValue !== undefined) {
          const flatItem: any = {};
          if (item.timeValue !== undefined) flatItem.timeValue = item.timeValue;
          if (item.metricValue !== undefined) flatItem.metricValue = item.metricValue;
          if (item.unit !== undefined) flatItem.unit = item.unit;
          // 将维度值扁平化
          if (item.dimensionValues) {
            Object.keys(item.dimensionValues).forEach(dim => {
              flatItem[dim] = item.dimensionValues[dim];
            });
          }
          return flatItem;
        }
        // 原始 SQL 结果格式，直接返回
        return item;
      });
      
      setData(flattenedData);
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
  const [availableFields, setAvailableFields] = useState<string[]>([]);
  const [loadingFields, setLoadingFields] = useState(false);

  useEffect(() => {
    loadMetrics();
  }, []);

  useEffect(() => {
    if (metricId) {
      loadMetricFields();
    } else {
      setAvailableFields([]);
    }
  }, [metricId]);

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

  const loadMetricFields = async () => {
    if (!metricId) return;
    
    setLoadingFields(true);
    try {
      // 获取指标结果以提取可用字段
      const result = await metricApi.calculateMetric({
        metric_id: metricId,
        time_range: { start: '2024-01-01', end: '2024-12-31' },
        dimensions: {},
      });
      
      // 提取字段列表
      const fields: string[] = [];
      
      if (result.columns && result.columns.length > 0) {
        // 如果有 columns 字段，直接使用（原始 SQL 结果格式）
        fields.push(...result.columns);
      } else if (result.results && result.results.length > 0) {
        // 否则从第一个结果对象中提取键
        const firstResult = result.results[0];
        if (typeof firstResult === 'object' && firstResult !== null) {
          // 处理结构化格式（MetricDataPoint）
          if ('metricValue' in firstResult) {
            // 结构化格式：使用实际的字段名
            if ('timeValue' in firstResult) fields.push('timeValue');
            if ('dimensionValues' in firstResult && firstResult.dimensionValues) {
              // 对于维度字段，使用实际的维度名称作为字段名
              Object.keys(firstResult.dimensionValues).forEach(dim => {
                fields.push(dim);
              });
            }
            fields.push('metricValue');
            if ('unit' in firstResult) fields.push('unit');
          } else {
            // 原始 SQL 结果格式：直接使用对象的键
            fields.push(...Object.keys(firstResult));
          }
        }
      }
      
      setAvailableFields(fields);
      
      // 如果当前 x/y 轴字段不在可用字段列表中，自动选择第一个合适的字段
      if (fields.length > 0) {
        setProp((props: ChartWidgetProps) => {
          // 自动设置 x 轴：优先选择时间字段，否则选择第一个维度字段，最后选择第一个字段
          if (!props.xAxis || !fields.includes(props.xAxis)) {
            const timeField = fields.find(f => f === 'timeValue');
            if (timeField) {
              props.xAxis = timeField;
            } else {
              // 选择第一个非指标值、非单位的字段作为 x 轴
              const xField = fields.find(f => f !== 'metricValue' && f !== 'unit');
              props.xAxis = xField || fields[0];
            }
          }
          
          // 自动设置 y 轴：优先选择指标值字段
          if (!props.yAxis || !fields.includes(props.yAxis)) {
            const valueField = fields.find(f => f === 'metricValue');
            if (valueField) {
              props.yAxis = valueField;
            } else {
              // 如果没有 metricValue，选择第一个数值字段（排除时间字段）
              const numericField = fields.find(f => f !== 'timeValue' && f !== 'unit');
              props.yAxis = numericField || fields[fields.length - 1];
            }
          }
        });
      }
    } catch (error) {
      console.error('Failed to load metric fields:', error);
      setAvailableFields([]);
    } finally {
      setLoadingFields(false);
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
          onChange={(e) => {
            const selectedMetricId = e.target.value;
            setProp((props: ChartWidgetProps) => {
              props.metricId = selectedMetricId;
              
              // 如果选择了指标，自动填充标题为指标的名称（默认值）
              if (selectedMetricId) {
                const selectedMetric = metrics.find((m) => m.id === selectedMetricId);
                if (selectedMetric) {
                  const metricName = selectedMetric.displayName || selectedMetric.display_name || selectedMetric.name;
                  if (metricName) {
                    props.title = metricName;
                  }
                }
              }
            });
          }}
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
        {loadingFields ? (
          <div className="w-full p-2 border rounded text-sm text-gray-400">加载字段中...</div>
        ) : availableFields.length > 0 ? (
          <select
            value={xAxis || ''}
            onChange={(e) => setProp((props: ChartWidgetProps) => (props.xAxis = e.target.value))}
            className="w-full p-2 border rounded text-sm"
          >
            <option value="">请选择X轴字段</option>
            {availableFields.map((field) => (
              <option key={field} value={field}>
                {field}
              </option>
            ))}
          </select>
        ) : metricId ? (
          <div className="w-full p-2 border rounded text-sm text-gray-400">暂无可用字段，请先选择指标</div>
        ) : (
          <div className="w-full p-2 border rounded text-sm text-gray-400">请先选择指标</div>
        )}
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">Y轴字段</label>
        {loadingFields ? (
          <div className="w-full p-2 border rounded text-sm text-gray-400">加载字段中...</div>
        ) : availableFields.length > 0 ? (
          <select
            value={yAxis || ''}
            onChange={(e) => setProp((props: ChartWidgetProps) => (props.yAxis = e.target.value))}
            className="w-full p-2 border rounded text-sm"
          >
            <option value="">请选择Y轴字段</option>
            {availableFields.map((field) => (
              <option key={field} value={field}>
                {field}
              </option>
            ))}
          </select>
        ) : metricId ? (
          <div className="w-full p-2 border rounded text-sm text-gray-400">暂无可用字段，请先选择指标</div>
        ) : (
          <div className="w-full p-2 border rounded text-sm text-gray-400">请先选择指标</div>
        )}
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
