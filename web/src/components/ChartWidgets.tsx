import ReactECharts from 'echarts-for-react';
import type { WidgetSpec, NaturalLanguageQueryResponse } from '../api/client';

interface ChartProps {
  spec: WidgetSpec;
  data: NaturalLanguageQueryResponse;
}

function getColumnData(data: NaturalLanguageQueryResponse, fieldName: string): any[] {
  if (!data.rows || !data.columns) return [];
  // 精确匹配：直接用列名作为 key 访问行对象，避免 Object.values 顺序不稳定
  if (data.columns.includes(fieldName)) {
    return data.rows.map(row => (row as any)[fieldName]);
  }
  // 模糊匹配：找到匹配的列名后同样用 key 访问
  const fuzzyCol = data.columns.find(c => c.includes(fieldName) || fieldName.includes(c));
  if (fuzzyCol) {
    return data.rows.map(row => (row as any)[fuzzyCol]);
  }
  // 兜底：返回行序号
  return data.rows.map((_, i) => i);
}

function getRowValues(data: NaturalLanguageQueryResponse): { columns: string[]; rows: any[][] } {
  if (!data.rows || !data.columns) return { columns: [], rows: [] };
  return {
    columns: data.columns,
    rows: data.rows.map(row => data.columns!.map(col => (row as any)[col] ?? Object.values(row)[data.columns!.indexOf(col)])),
  };
}

export function LineChartWidget({ spec, data }: ChartProps) {
  const resolveField = (hint: string | undefined, fallbackIdx: number) => {
    if (!hint) return data.columns?.[fallbackIdx] || '';
    if (data.columns?.includes(hint)) return hint;
    const fuzzy = data.columns?.find(c => c.includes(hint) || hint.includes(c));
    return fuzzy || data.columns?.[fallbackIdx] || hint;
  };
  const xField = resolveField(spec.options?.xField, 0);
  const yField = resolveField(spec.options?.yField, 1);
  const seriesField = spec.options?.seriesField;

  if (seriesField && data.rows) {
    const groups = new Map<string, { x: any[]; y: any[] }>();
    for (const row of data.rows) {
      const r = row as any;
      const key = String(r[seriesField]);
      if (!groups.has(key)) groups.set(key, { x: [], y: [] });
      groups.get(key)!.x.push(r[xField]);
      groups.get(key)!.y.push(r[yField]);
    }
    const xData = [...new Set(data.rows.map(r => (r as any)[xField]))];
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
  // 若 spec 中指定的字段名不在列中，回退到按位置取列（x→第0列，y→第1列）
  const resolveField = (hint: string | undefined, fallbackIdx: number) => {
    if (!hint) return data.columns?.[fallbackIdx] || '';
    if (data.columns?.includes(hint)) return hint;
    const fuzzy = data.columns?.find(c => c.includes(hint) || hint.includes(c));
    return fuzzy || data.columns?.[fallbackIdx] || hint;
  };
  const xField = resolveField(spec.options?.xField, 0);
  const yField = resolveField(spec.options?.yField, 1);
  const seriesField = spec.options?.seriesField;

  if (seriesField && data.rows) {
    const groups = new Map<string, Map<string, number>>();
    for (const row of data.rows) {
      const r = row as any;
      const key = String(r[seriesField]);
      if (!groups.has(key)) groups.set(key, new Map());
      groups.get(key)!.set(String(r[xField]), Number(r[yField]));
    }
    const xData = [...new Set(data.rows.map(r => String((r as any)[xField])))];
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
  const resolveField = (hint: string | undefined, fallbackIdx: number) => {
    if (!hint) return data.columns?.[fallbackIdx] || '';
    if (data.columns?.includes(hint)) return hint;
    const fuzzy = data.columns?.find(c => c.includes(hint) || hint.includes(c));
    return fuzzy || data.columns?.[fallbackIdx] || hint;
  };
  const nameField = resolveField(spec.options?.nameField, 0);
  const valueField = resolveField(spec.options?.valueField, 1);
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