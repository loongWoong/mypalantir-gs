import { useMemo } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';
import type { Property, Instance } from '../api/client';

export interface AnalysisDisplayOptions {
  showSummary: boolean;      // 统计摘要
  showNumericStats: boolean;  // 数值统计
  showChart: boolean;         // 分布图表
}

interface PropertyStatisticsProps {
  property: Property;
  instances: Instance[];
  displayOptions: AnalysisDisplayOptions;
}

interface Statistics {
  total: number;
  nonNull: number;
  nullCount: number;
  uniqueValues: number;
  distribution: Array<{ name: string; value: number }>;
  numericStats?: {
    min: number;
    max: number;
    avg: number;
    median: number;
  };
}

const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16'];

export default function PropertyStatistics({ property, instances, displayOptions }: PropertyStatisticsProps) {
  const stats = useMemo<Statistics>(() => {
    const values = instances
      .map(inst => inst[property.name])
      .filter(val => val !== null && val !== undefined);

    const total = instances.length;
    const nonNull = values.length;
    const nullCount = total - nonNull;

    // 计算唯一值数量
    const uniqueValues = new Set(values.map(v => String(v))).size;

    // 计算分布（对于分类数据）
    const valueCounts = new Map<string, number>();
    values.forEach(val => {
      const key = String(val);
      valueCounts.set(key, (valueCounts.get(key) || 0) + 1);
    });

    // 转换为分布数组，按数量排序，最多显示20个
    const distribution = Array.from(valueCounts.entries())
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 20);

    // 如果是数值类型，计算统计信息
    let numericStats: Statistics['numericStats'] | undefined;
    const numericValues = values
      .map(v => {
        const num = typeof v === 'number' ? v : parseFloat(String(v));
        return isNaN(num) ? null : num;
      })
      .filter((v): v is number => v !== null);

    if (numericValues.length > 0) {
      const sorted = [...numericValues].sort((a, b) => a - b);
      const min = sorted[0];
      const max = sorted[sorted.length - 1];
      const avg = numericValues.reduce((sum, v) => sum + v, 0) / numericValues.length;
      const median = sorted.length % 2 === 0
        ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2
        : sorted[Math.floor(sorted.length / 2)];

      numericStats = { min, max, avg, median };
    }

    return {
      total,
      nonNull,
      nullCount,
      uniqueValues,
      distribution,
      numericStats,
    };
  }, [property.name, instances]);

  // 判断是否为数值类型
  const isNumeric = property.data_type === 'number' || 
                    property.data_type === 'integer' || 
                    property.data_type === 'float' || 
                    property.data_type === 'double' ||
                    stats.numericStats !== undefined;

  // 判断是否适合显示饼图（分类数据，唯一值数量较少）
  const showPieChart = !isNumeric && stats.uniqueValues <= 10 && stats.distribution.length > 0;

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-3">
      {/* 属性标题行 - 紧凑布局 */}
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <h3 className="text-base font-semibold text-gray-900">{property.name}</h3>
          <span className="text-xs px-2 py-0.5 bg-blue-100 text-blue-700 rounded">
            {property.data_type}
          </span>
        </div>
        {property.description && (
          <span className="text-xs text-gray-500 truncate max-w-md">{property.description}</span>
        )}
      </div>

      {/* 统计信息 - 单行紧凑布局 */}
      <div className="flex flex-wrap items-center gap-3 mb-2">
        {/* 统计摘要 */}
        {displayOptions.showSummary && (
          <>
            <div className="flex items-center gap-1.5 text-sm">
              <span className="text-gray-500">总数:</span>
              <span className="font-semibold text-gray-900">{stats.total}</span>
            </div>
            <div className="flex items-center gap-1.5 text-sm">
              <span className="text-gray-500">非空:</span>
              <span className="font-semibold text-green-600">{stats.nonNull}</span>
            </div>
            <div className="flex items-center gap-1.5 text-sm">
              <span className="text-gray-500">空值:</span>
              <span className="font-semibold text-red-600">{stats.nullCount}</span>
            </div>
            <div className="flex items-center gap-1.5 text-sm">
              <span className="text-gray-500">唯一值:</span>
              <span className="font-semibold text-blue-600">{stats.uniqueValues}</span>
            </div>
          </>
        )}

        {/* 数值统计信息 - 紧凑显示 */}
        {displayOptions.showNumericStats && stats.numericStats && (
          <>
            <div className="flex items-center gap-1.5 text-sm">
              <span className="text-gray-500">最小值:</span>
              <span className="font-semibold text-blue-700">{stats.numericStats.min.toFixed(2)}</span>
            </div>
            <div className="flex items-center gap-1.5 text-sm">
              <span className="text-gray-500">最大值:</span>
              <span className="font-semibold text-blue-700">{stats.numericStats.max.toFixed(2)}</span>
            </div>
            <div className="flex items-center gap-1.5 text-sm">
              <span className="text-gray-500">平均值:</span>
              <span className="font-semibold text-blue-700">{stats.numericStats.avg.toFixed(2)}</span>
            </div>
            <div className="flex items-center gap-1.5 text-sm">
              <span className="text-gray-500">中位数:</span>
              <span className="font-semibold text-blue-700">{stats.numericStats.median.toFixed(2)}</span>
            </div>
          </>
        )}
      </div>

      {/* 图表 - 紧凑高度 */}
      {displayOptions.showChart && stats.distribution.length > 0 && (
        <div className="mt-2">
          {showPieChart ? (
            <ResponsiveContainer width="100%" height={200}>
              <PieChart>
                <Pie
                  data={stats.distribution}
                  cx="50%"
                  cy="50%"
                  labelLine={false}
                  label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                  outerRadius={60}
                  fill="#8884d8"
                  dataKey="value"
                >
                  {stats.distribution.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={stats.distribution}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis 
                  dataKey="name" 
                  angle={-45}
                  textAnchor="end"
                  height={80}
                  interval={0}
                  tick={{ fontSize: 10 }}
                />
                <YAxis tick={{ fontSize: 10 }} />
                <Tooltip />
                <Bar dataKey="value" fill="#3b82f6" />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      )}

      {displayOptions.showChart && stats.distribution.length === 0 && (
        <div className="text-center py-4 text-gray-400 text-sm">
          暂无数据分布信息
        </div>
      )}
    </div>
  );
}
