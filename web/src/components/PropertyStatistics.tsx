import { useMemo } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';
import type { Property, Instance } from '../api/client';

interface PropertyStatisticsProps {
  property: Property;
  instances: Instance[];
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

export default function PropertyStatistics({ property, instances }: PropertyStatisticsProps) {
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
    <div className="bg-white rounded-lg border border-gray-200 p-4">
      <div className="mb-4">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-lg font-semibold text-gray-900">{property.name}</h3>
          <span className="text-xs px-2 py-1 bg-blue-100 text-blue-700 rounded">
            {property.data_type}
          </span>
        </div>
        {property.description && (
          <p className="text-sm text-gray-600 mt-1">{property.description}</p>
        )}
      </div>

      {/* 统计摘要 */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
        <div className="bg-gray-50 rounded-lg p-3">
          <div className="text-xs text-gray-500 mb-1">总数</div>
          <div className="text-lg font-semibold text-gray-900">{stats.total}</div>
        </div>
        <div className="bg-gray-50 rounded-lg p-3">
          <div className="text-xs text-gray-500 mb-1">非空值</div>
          <div className="text-lg font-semibold text-green-600">{stats.nonNull}</div>
        </div>
        <div className="bg-gray-50 rounded-lg p-3">
          <div className="text-xs text-gray-500 mb-1">空值</div>
          <div className="text-lg font-semibold text-red-600">{stats.nullCount}</div>
        </div>
        <div className="bg-gray-50 rounded-lg p-3">
          <div className="text-xs text-gray-500 mb-1">唯一值</div>
          <div className="text-lg font-semibold text-blue-600">{stats.uniqueValues}</div>
        </div>
      </div>

      {/* 数值统计信息 */}
      {stats.numericStats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
          <div className="bg-blue-50 rounded-lg p-3">
            <div className="text-xs text-gray-500 mb-1">最小值</div>
            <div className="text-lg font-semibold text-blue-900">{stats.numericStats.min.toFixed(2)}</div>
          </div>
          <div className="bg-blue-50 rounded-lg p-3">
            <div className="text-xs text-gray-500 mb-1">最大值</div>
            <div className="text-lg font-semibold text-blue-900">{stats.numericStats.max.toFixed(2)}</div>
          </div>
          <div className="bg-blue-50 rounded-lg p-3">
            <div className="text-xs text-gray-500 mb-1">平均值</div>
            <div className="text-lg font-semibold text-blue-900">{stats.numericStats.avg.toFixed(2)}</div>
          </div>
          <div className="bg-blue-50 rounded-lg p-3">
            <div className="text-xs text-gray-500 mb-1">中位数</div>
            <div className="text-lg font-semibold text-blue-900">{stats.numericStats.median.toFixed(2)}</div>
          </div>
        </div>
      )}

      {/* 图表 */}
      {stats.distribution.length > 0 && (
        <div className="mt-4">
          {showPieChart ? (
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={stats.distribution}
                  cx="50%"
                  cy="50%"
                  labelLine={false}
                  label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                  outerRadius={80}
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
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={stats.distribution}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis 
                  dataKey="name" 
                  angle={-45}
                  textAnchor="end"
                  height={100}
                  interval={0}
                />
                <YAxis />
                <Tooltip />
                <Legend />
                <Bar dataKey="value" fill="#3b82f6" />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      )}

      {stats.distribution.length === 0 && (
        <div className="text-center py-8 text-gray-500">
          暂无数据分布信息
        </div>
      )}
    </div>
  );
}
