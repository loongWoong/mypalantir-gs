import ReactECharts from 'echarts-for-react';
import type { WidgetSpec, NaturalLanguageQueryResponse } from '../api/client';

interface ChartProps {
  spec: WidgetSpec;
  data: NaturalLanguageQueryResponse;
}

function getColumnData(data: NaturalLanguageQueryResponse, fieldName: string): any[] {
  if (!data.rows || !data.columns) return [];
  const idx = data.columns.indexOf(fieldName);
  if (idx === -1) {
    // 尝试模糊匹配
    const fuzzyIdx = data.columns.findIndex(c => c.includes(fieldName) || fieldName.includes(c));
    if (fuzzyIdx === -1) return data.rows.map((_, i) => i);
    return data.rows.map(row => Object.values(row)[fuzzyIdx]);
  }
  return data.rows.map(row => Object.values(row)[idx]);
}

function getRowValues(data: NaturalLanguageQueryResponse): { columns: string[]; rows: any[][] } {
  if (!data.rows || !data.columns) return { columns: [], rows: [] };
  return {
    columns: data.columns,
    rows: data.rows.map(row => data.columns!.map(col => (row as any)[col] ?? Object.values(row)[data.columns!.indexOf(col)])),
  };
}

export function LineChartWidget({ spec, data }: ChartProps) {
  const xField = spec.options?.xField || data.columns?.[0] || '';
  const yField = spec.options?.yField || data.columns?.[1] || '';
  const seriesField = spec.options?.seriesField;

  if (seriesField && data.rows) {
    const groups = new Map<string, { x: any[]; y: any[] }>();
    const xIdx = data.columns?.indexOf(xField) ?? 0;
    const yIdx = data.columns?.indexOf(yField) ?? 1;
    const sIdx = data.columns?.indexOf(seriesField) ?? 2;
    for (const row of data.rows) {
      const vals = Object.values(row);
      const key = String(vals[sIdx]);
      if (!groups.has(key)) groups.set(key, { x: [], y: [] });
      groups.get(key)!.x.push(vals[xIdx]);
      groups.get(key)!.y.push(vals[yIdx]);
    }
    const xData = [...new Set(data.rows.map(r => Object.values(r)[xIdx]))];
    const series = [...groups.entries()].map(([name, g]) => ({
      name,
      type: 'line' as const,
      data: xData.map(x => {
        const i = g.x.indexOf(x);
        return i >= 0 ? g.y[i] : null;
      }),
    }));
    return <ReactECharts option={{
      tooltip: { trigger: 'axis' },
      legend: { data: [...groups.keys()] },
      xAxis: { type: 'category', data: xData },
      yAxis: { type: 'value' },
      series,
    }} style={{ height: '100%', width: '100%' }} />;
  }

  return <ReactECharts option={{
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: getColumnData(data, xField) },
    yAxis: { type: 'value' },
    series: [{ type: 'line', data: getColumnData(data, yField), name: yField }],
  }} style={{ height: '100%', width: '100%' }} />;
}

export function BarChartWidget({ spec, data }: ChartProps) {
  const xField = spec.options?.xField || data.columns?.[0] || '';
  const yField = spec.options?.yField || data.columns?.[1] || '';
  const seriesField = spec.options?.seriesField;

  if (seriesField && data.rows) {
    const xIdx = data.columns?.indexOf(xField) ?? 0;
    const yIdx = data.columns?.indexOf(yField) ?? 1;
    const sIdx = data.columns?.indexOf(seriesField) ?? 2;
    const groups = new Map<string, Map<string, number>>();
    for (const row of data.rows) {
      const vals = Object.values(row);
      const key = String(vals[sIdx]);
      if (!groups.has(key)) groups.set(key, new Map());
      groups.get(key)!.set(String(vals[xIdx]), Number(vals[yIdx]));
    }
    const xData = [...new Set(data.rows.map(r => String(Object.values(r)[xIdx])))];
    const series = [...groups.entries()].map(([name, m]) => ({
      name,
      type: 'bar' as const,
      data: xData.map(x => m.get(x) ?? 0),
    }));
    return <ReactECharts option={{
      tooltip: { trigger: 'axis' },
      legend: { data: [...groups.keys()] },
      xAxis: { type: 'category', data: xData },
      yAxis: { type: 'value' },
      series,
    }} style={{ height: '100%', width: '100%' }} />;
  }

  return <ReactECharts option={{
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: getColumnData(data, xField) },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: getColumnData(data, yField), name: yField }],
  }} style={{ height: '100%', width: '100%' }} />;
}

export function PieChartWidget({ spec, data }: ChartProps) {
  const nameField = spec.options?.nameField || data.columns?.[0] || '';
  const valueField = spec.options?.valueField || data.columns?.[1] || '';
  const names = getColumnData(data, nameField);
  const values = getColumnData(data, valueField);
  const pieData = names.map((n, i) => ({ name: String(n), value: Number(values[i]) }));

  return <ReactECharts option={{
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{ type: 'pie', radius: '60%', data: pieData }],
  }} style={{ height: '100%', width: '100%' }} />;
}

export function MetricWidget({ spec, data }: ChartProps) {
  const valueField = spec.options?.valueField || data.columns?.[1] || data.columns?.[0] || '';
  const labelField = spec.options?.labelField || spec.title;
  const values = getColumnData(data, valueField);
  const value = values.length > 0 ? values[0] : '-';

  return (
    <div className="flex flex-col items-center justify-center h-full">
      <div className="text-3xl font-bold text-blue-600">
        {typeof value === 'number' ? value.toLocaleString() : value}
      </div>
      <div className="text-sm text-gray-500 mt-2">{labelField}</div>
    </div>
  );
}

export function TableWidget({ data }: { data: NaturalLanguageQueryResponse }) {
  const { columns, rows } = getRowValues(data);
  if (!columns.length) return <div className="text-gray-400 text-sm">无数据</div>;

  return (
    <div className="overflow-auto h-full">
      <table className="min-w-full text-sm">
        <thead className="bg-gray-50 sticky top-0">
          <tr>
            {columns.map(col => (
              <th key={col} className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">{col}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200">
          {rows.map((row, i) => (
            <tr key={i} className="hover:bg-gray-50">
              {row.map((cell, j) => (
                <td key={j} className="px-3 py-2 text-gray-700 whitespace-nowrap">{String(cell ?? '')}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}