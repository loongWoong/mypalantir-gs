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
    <div className="bg-white rounded-xl border border-gray-200 p-5 shadow-sm hover:shadow-md transition-all duration-200">
      {/* 属性标题行 */}
      <div className="flex items-center mb-4 border-b border-gray-100 pb-3 gap-3">
        <h3 className="text-lg font-bold text-gray-900">{property.name}</h3>
        <span className={`px-2.5 py-1 text-xs font-medium rounded-full flex-shrink-0 ${
          isNumeric ? 'bg-indigo-100 text-indigo-700' : 'bg-blue-100 text-blue-700'
        }`}>
          {property.data_type}
        </span>
        {property.description && (
          <span className="text-sm text-gray-500 truncate" title={property.description}>
            {property.description}
          </span>
        )}
      </div>

      <div className="flex flex-col lg:flex-row gap-4 items-stretch">
        {/* 统计摘要 */}
        {displayOptions.showSummary && (
          <div className="lg:w-48 flex-shrink-0 bg-gray-50 rounded-lg p-3 border border-gray-100 flex flex-col justify-center">
            <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">统计摘要</h4>
            <div className="space-y-1.5">
              <div className="flex justify-between items-baseline">
                <span className="text-xs text-gray-500">总数</span>
                <span className="text-sm font-bold text-gray-900">{stats.total.toLocaleString()}</span>
              </div>
              <div className="flex justify-between items-baseline">
                <span className="text-xs text-gray-500">有效</span>
                <span className="text-sm font-bold text-green-600">{stats.nonNull.toLocaleString()}</span>
              </div>
              <div className="flex justify-between items-baseline">
                <span className="text-xs text-gray-500">空值</span>
                <span className="text-sm font-bold text-red-500">{stats.nullCount.toLocaleString()}</span>
              </div>
              <div className="flex justify-between items-baseline">
                <span className="text-xs text-gray-500">唯一</span>
                <span className="text-sm font-bold text-blue-600">{stats.uniqueValues.toLocaleString()}</span>
              </div>
            </div>
          </div>
        )}

        {/* 数值统计信息 */}
        {displayOptions.showNumericStats && stats.numericStats && (
          <div className="lg:w-48 flex-shrink-0 bg-indigo-50 rounded-lg p-3 border border-indigo-100 flex flex-col justify-center">
            <h4 className="text-[10px] font-bold text-indigo-400 uppercase tracking-wider mb-2">数值统计</h4>
            <div className="space-y-1.5">
              <div className="flex justify-between items-baseline">
                <span className="text-xs text-indigo-600/70">Min</span>
                <span className="text-sm font-mono font-bold text-indigo-700">{stats.numericStats.min.toLocaleString()}</span>
              </div>
              <div className="flex justify-between items-baseline">
                <span className="text-xs text-indigo-600/70">Max</span>
                <span className="text-sm font-mono font-bold text-indigo-700">{stats.numericStats.max.toLocaleString()}</span>
              </div>
              <div className="flex justify-between items-baseline">
                <span className="text-xs text-indigo-600/70">Avg</span>
                <span className="text-sm font-mono font-bold text-indigo-700">{stats.numericStats.avg.toLocaleString(undefined, { maximumFractionDigits: 1 })}</span>
              </div>
              <div className="flex justify-between items-baseline">
                <span className="text-xs text-indigo-600/70">Med</span>
                <span className="text-sm font-mono font-bold text-indigo-700">{stats.numericStats.median.toLocaleString(undefined, { maximumFractionDigits: 1 })}</span>
              </div>
            </div>
          </div>
        )}

        {/* 右侧图表区域 */}
        {displayOptions.showChart && (
          <div className="flex-1 min-w-0 min-h-[160px] h-[160px]">
            {stats.distribution.length > 0 ? (
              <div className="bg-white rounded-lg h-full flex flex-col">
                <div className="flex-1 w-full h-full">
                  {showPieChart ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie
                          data={stats.distribution}
                          cx="50%"
                          cy="50%"
                          innerRadius={40}
                          outerRadius={70}
                          paddingAngle={2}
                          dataKey="value"
                        >
                          {stats.distribution.map((entry, index) => (
                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} stroke="fff" strokeWidth={2} />
                          ))}
                        </Pie>
                        <Tooltip 
                          contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)' }}
                        />
                        <Legend 
                          layout="vertical" 
                          verticalAlign="middle" 
                          align="right"
                          wrapperStyle={{ paddingLeft: '10px', fontSize: '11px' }}
                        />
                      </PieChart>
                    </ResponsiveContainer>
                  ) : (
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={stats.distribution} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
                        <XAxis 
                          dataKey="name" 
                          axisLine={false}
                          tickLine={false}
                          tick={{ fontSize: 10, fill: '#6B7280' }}
                          dy={5}
                          interval={0}
                          angle={stats.distribution.length > 10 ? -45 : 0}
                          textAnchor={stats.distribution.length > 10 ? 'end' : 'middle'}
                          height={30}
                        />
                        <YAxis 
                          axisLine={false}
                          tickLine={false}
                          tick={{ fontSize: 10, fill: '#6B7280' }}
                        />
                        <Tooltip 
                          cursor={{ fill: '#F3F4F6' }}
                          contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)' }}
                        />
                        <Bar 
                          dataKey="value" 
                          fill="#3B82F6" 
                          radius={[4, 4, 0, 0]}
                          maxBarSize={40}
                        />
                      </BarChart>
                    </ResponsiveContainer>
                  )}
                </div>
              </div>
            ) : (
              <div className="h-full flex flex-col items-center justify-center text-gray-400 bg-gray-50 rounded-lg border border-dashed border-gray-200">
                <svg className="w-8 h-8 mb-2 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                </svg>
                <p className="text-xs">暂无数据</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
