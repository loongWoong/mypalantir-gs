import { useMemo, useState } from 'react';
import { BarChart, Bar, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, LineChart, Line, XAxis, YAxis, CartesianGrid } from 'recharts';
import type { Property, Instance } from '../api/client';
import { ChartBarIcon, ChartPieIcon, ListBulletIcon } from '@heroicons/react/24/outline';

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
  distribution: Array<{ name: string; value: number; percent: number }>;
  histogram?: Array<{ name: string; min: number; max: number; value: number }>;
  numericStats?: {
    min: number;
    max: number;
    avg: number;
    median: number;
  };
  isNumeric: boolean;
}

const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16'];

export default function PropertyStatistics({ property, instances, displayOptions }: PropertyStatisticsProps) {
  const [chartType, setChartType] = useState<'bar' | 'pie' | 'list' | 'line'>('bar');

  const stats = useMemo<Statistics>(() => {
    const values = instances
      .map(inst => inst[property.name])
      .filter(val => val !== null && val !== undefined);

    const total = instances.length;
    const nonNull = values.length;
    const nullCount = total - nonNull;

    // Check if numeric based on data type or content
    const isNumericType = ['number', 'integer', 'float', 'double', 'decimal'].includes(property.data_type?.toLowerCase() || '');
    // Also try to detect from values if type is unknown or string but content is numeric
    const isContentNumeric = values.length > 0 && values.every(v => !isNaN(Number(v)) && v !== '');
    const isNumeric = isNumericType || isContentNumeric;

    // Calculate unique values
    const uniqueValues = new Set(values.map(v => String(v))).size;

    // 1. Calculate Frequency Distribution (Top Values)
    const valueCounts = new Map<string, number>();
    values.forEach(val => {
      const key = String(val);
      valueCounts.set(key, (valueCounts.get(key) || 0) + 1);
    });

    const distribution = Array.from(valueCounts.entries())
      .map(([name, value]) => ({ 
        name, 
        value,
        percent: nonNull > 0 ? (value / nonNull) * 100 : 0
      }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 10); // Keep top 10 for list view

    // 2. Calculate Numeric Stats & Histogram
    let numericStats: Statistics['numericStats'] | undefined;
    let histogram: Statistics['histogram'] | undefined;

    if (isNumeric && values.length > 0) {
      const numericValues = values.map(v => Number(v)).sort((a, b) => a - b);
      
      if (numericValues.length > 0) {
        const min = numericValues[0];
        const max = numericValues[numericValues.length - 1];
        const avg = numericValues.reduce((sum, v) => sum + v, 0) / numericValues.length;
        const median = numericValues.length % 2 === 0
          ? (numericValues[numericValues.length / 2 - 1] + numericValues[numericValues.length / 2]) / 2
          : numericValues[Math.floor(numericValues.length / 2)];

        numericStats = { min, max, avg, median };

        // Generate Histogram if we have enough unique values, otherwise use distribution
        if (uniqueValues > 10) {
          const binCount = 20;
          const range = max - min;
          const binWidth = range === 0 ? 1 : range / binCount;
          
          const bins = new Array(binCount).fill(0).map((_, i) => ({
            min: min + i * binWidth,
            max: min + (i + 1) * binWidth,
            value: 0,
            name: `${(min + i * binWidth).toFixed(1)}-${(min + (i + 1) * binWidth).toFixed(1)}`
          }));

          numericValues.forEach(v => {
            let binIndex = Math.floor((v - min) / binWidth);
            if (binIndex >= binCount) binIndex = binCount - 1;
            bins[binIndex].value++;
          });

          histogram = bins;
        }
      }
    }

    return {
      total,
      nonNull,
      nullCount,
      uniqueValues,
      distribution,
      histogram,
      numericStats,
      isNumeric
    };
  }, [property.name, property.data_type, instances]);

  const hasValidData = stats.nonNull > 0;
  const showContent = hasValidData && (
    displayOptions.showSummary || 
    (displayOptions.showNumericStats && !!stats.numericStats) || 
    displayOptions.showChart
  );

  const renderChart = () => {
    if (stats.isNumeric && stats.histogram) {
      // Numeric charts
      if (chartType === 'line') {
         return (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={stats.histogram} margin={{ top: 5, right: 5, left: -20, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
              <XAxis dataKey="name" hide />
              <YAxis tick={{ fontSize: 10 }} />
              <Tooltip 
                cursor={{ stroke: '#6366f1' }}
                content={({ active, payload }) => {
                  if (active && payload && payload.length) {
                    const data = payload[0].payload;
                    return (
                      <div className="bg-gray-900 text-white text-xs rounded py-1 px-2 shadow-xl z-50">
                        <div className="font-mono mb-1">{data.min.toFixed(2)} - {data.max.toFixed(2)}</div>
                        <div>数量: <span className="font-bold">{data.value}</span></div>
                      </div>
                    );
                  }
                  return null;
                }}
              />
              <Line type="monotone" dataKey="value" stroke="#6366f1" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
         );
      }
      // Default bar chart for numeric
      return (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={stats.histogram} barCategoryGap={1} margin={{ top: 5, right: 0, left: 0, bottom: 0 }}>
            <Tooltip 
              cursor={{ fill: '#F3F4F6' }}
              content={({ active, payload }) => {
                if (active && payload && payload.length) {
                  const data = payload[0].payload;
                  return (
                    <div className="bg-gray-900 text-white text-xs rounded py-1 px-2 shadow-xl z-50">
                      <div className="font-mono mb-1">{data.min.toFixed(2)} - {data.max.toFixed(2)}</div>
                      <div>数量: <span className="font-bold">{data.value}</span></div>
                    </div>
                  );
                }
                return null;
              }}
            />
            <Bar dataKey="value" fill="#6366f1" radius={[2, 2, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      );
    } else {
      // Categorical charts
      if (chartType === 'pie') {
        return (
          <ResponsiveContainer width="100%" height="100%">
            <PieChart margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
              <Pie
                data={stats.distribution}
                cx="50%"
                cy="50%"
                innerRadius={25}
                outerRadius={45}
                paddingAngle={2}
                dataKey="value"
              >
                {stats.distribution.map((_, index) => (
                  <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} stroke="fff" strokeWidth={1} />
                ))}
              </Pie>
              <Tooltip 
                contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)', fontSize: '11px' }}
                itemStyle={{ padding: 0 }}
              />
            </PieChart>
          </ResponsiveContainer>
        );
      }
      
      // Default bar chart for categorical (HF Style but simplified)
       return (
        <div className="flex flex-col gap-1.5 h-full overflow-hidden pt-1">
            {stats.distribution.slice(0, 5).map((item, idx) => (
              <div key={idx} className="group flex-shrink-0">
                <div className="flex justify-between text-[10px] mb-0.5 leading-tight">
                  <div className="flex items-center gap-1.5 overflow-hidden">
                    <span className="text-gray-400 font-mono w-3">{idx + 1}</span>
                    <span className="font-medium text-gray-700 truncate max-w-[100px] xl:max-w-[140px]" title={item.name}>
                      {item.name === '' ? '(空值)' : item.name}
                    </span>
                  </div>
                  <div className="flex items-center gap-1.5 flex-shrink-0">
                    <span className="text-gray-400">{item.value.toLocaleString()}</span>
                    <span className="font-bold text-gray-600 w-8 text-right">{item.percent.toFixed(0)}%</span>
                  </div>
                </div>
                <div className="h-1 bg-gray-100 rounded-full overflow-hidden w-full">
                  <div 
                    className={`h-full rounded-full ${
                      idx === 0 ? 'bg-blue-500' : 'bg-blue-400/70'
                    }`}
                    style={{ width: `${item.percent}%` }}
                  />
                </div>
              </div>
            ))}
            {stats.uniqueValues > 5 && (
              <div className="text-[9px] text-gray-400 text-center font-medium flex-shrink-0">
                + {stats.uniqueValues - 5} 其他值
              </div>
            )}
          </div>
       );
    }
  };

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-sm hover:shadow-md transition-all duration-200 group">
      {/* Property Header */}
      <div className={`flex items-center gap-3 ${showContent ? 'mb-4 border-b border-gray-100 pb-3' : ''}`}>
        <h3 className="text-base font-bold text-gray-900 truncate max-w-[200px]" title={property.name}>{property.name}</h3>
        <span className={`px-2 py-0.5 text-[10px] font-medium rounded-full flex-shrink-0 uppercase ${
          stats.isNumeric ? 'bg-indigo-50 text-indigo-700 border border-indigo-100' : 'bg-blue-50 text-blue-700 border border-blue-100'
        }`}>
          {property.data_type}
        </span>
        {property.description && (
          <span className="text-xs text-gray-400 truncate flex-1" title={property.description}>
            {property.description}
          </span>
        )}
      </div>

      {showContent && (
        <div className="flex flex-row gap-4 h-[140px] overflow-hidden">
          {/* 统计摘要 */}
          {displayOptions.showSummary && (
            <div className="w-[160px] flex-shrink-0 bg-gray-50 rounded-lg p-3 border border-gray-100 flex flex-col justify-center">
              <div className="space-y-1.5">
                <div className="flex justify-between items-center">
                  <span className="text-[10px] uppercase tracking-wider text-gray-400 font-semibold">总数</span>
                  <span className="text-xs font-bold text-gray-700">{stats.total.toLocaleString()}</span>
                </div>
                <div className="w-full h-px bg-gray-200/50"></div>
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <div className="text-[10px] text-gray-400 mb-0.5">有效</div>
                    <div className="text-xs font-bold text-green-600">{stats.nonNull.toLocaleString()}</div>
                  </div>
                  <div>
                    <div className="text-[10px] text-gray-400 mb-0.5">空值</div>
                    <div className="text-xs font-bold text-red-500">{stats.nullCount.toLocaleString()}</div>
                  </div>
                </div>
                <div className="flex justify-between items-center pt-1">
                  <span className="text-[10px] text-gray-400">唯一值</span>
                  <span className="text-xs font-bold text-blue-600">{stats.uniqueValues.toLocaleString()}</span>
                </div>
              </div>
            </div>
          )}

          {/* 数值统计信息 */}
          {displayOptions.showNumericStats && stats.numericStats && (
            <div className="flex-shrink-0 min-w-[140px] max-w-[30%] bg-indigo-50/50 rounded-lg p-3 border border-indigo-100 flex flex-col justify-center transition-all duration-300">
              <div className="space-y-1 w-full">
                <div className="flex justify-between items-baseline gap-2">
                  <span className="text-[10px] text-indigo-400 font-medium uppercase whitespace-nowrap">平均值</span>
                  <span className="text-xs font-mono font-bold text-indigo-700 truncate" title={stats.numericStats.avg.toLocaleString(undefined, { maximumFractionDigits: 2 })}>
                    {stats.numericStats.avg.toLocaleString(undefined, { maximumFractionDigits: 1 })}
                  </span>
                </div>
                <div className="flex justify-between items-baseline gap-2">
                  <span className="text-[10px] text-indigo-400 font-medium uppercase whitespace-nowrap">中位数</span>
                  <span className="text-xs font-mono font-bold text-indigo-700 truncate" title={stats.numericStats.median.toLocaleString(undefined, { maximumFractionDigits: 2 })}>
                    {stats.numericStats.median.toLocaleString(undefined, { maximumFractionDigits: 1 })}
                  </span>
                </div>
                <div className="flex justify-between items-baseline pt-1 border-t border-indigo-100/50 mt-1 gap-2">
                  <span className="text-[10px] text-indigo-400 font-medium whitespace-nowrap">最小值</span>
                  <span className="text-xs font-mono text-indigo-600 truncate" title={stats.numericStats.min.toLocaleString()}>
                    {stats.numericStats.min.toLocaleString()}
                  </span>
                </div>
                <div className="flex justify-between items-baseline gap-2">
                  <span className="text-[10px] text-indigo-400 font-medium whitespace-nowrap">最大值</span>
                  <span className="text-xs font-mono text-indigo-600 truncate" title={stats.numericStats.max.toLocaleString()}>
                    {stats.numericStats.max.toLocaleString()}
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* 分布图表 */}
          {displayOptions.showChart && (
            <div className="flex-1 min-w-0 h-full overflow-hidden flex flex-col relative">
              {stats.isNumeric && stats.histogram ? (
                // Numeric Histogram
                <>
                  <div className="flex justify-between items-end mb-1 px-1 flex-shrink-0">
                    <span className="text-[10px] text-gray-400 font-mono">{stats.numericStats?.min.toLocaleString()}</span>
                    <div className="flex items-center gap-2">
                      <span className="text-[10px] text-gray-400 font-mono">数值分布</span>
                      {/* Chart Type Switcher for Numeric */}
                        <div className="flex bg-gray-100 rounded p-0.5 opacity-0 group-hover:opacity-100 transition-opacity absolute right-0 top-0">
                        <button 
                          onClick={() => setChartType('bar')}
                          className={`p-1 rounded ${chartType === 'bar' ? 'bg-white shadow text-indigo-600' : 'text-gray-400 hover:text-gray-600'}`}
                          title="柱状图"
                        >
                          <ChartBarIcon className="w-3 h-3" />
                        </button>
                        <button 
                          onClick={() => setChartType('line')}
                          className={`p-1 rounded ${chartType === 'line' ? 'bg-white shadow text-indigo-600' : 'text-gray-400 hover:text-gray-600'}`}
                          title="折线图"
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="w-3 h-3">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
                          </svg>
                        </button>
                      </div>
                    </div>
                    <span className="text-[10px] text-gray-400 font-mono">{stats.numericStats?.max.toLocaleString()}</span>
                  </div>
                  <div className="flex-1 w-full min-h-0">
                    {renderChart()}
                  </div>
                </>
              ) : (
                // Categorical Charts
                <div className="h-full flex flex-col justify-center overflow-hidden relative">
                    {stats.distribution.length > 0 ? (
                    <>
                        {/* Chart Type Switcher for Categorical */}
                        <div className="absolute top-0 right-0 z-10 flex bg-gray-100 rounded p-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                        <button 
                          onClick={() => setChartType('bar')}
                          className={`p-1 rounded ${chartType === 'bar' ? 'bg-white shadow text-blue-600' : 'text-gray-400 hover:text-gray-600'}`}
                          title="列表视图"
                        >
                          <ListBulletIcon className="w-3 h-3" />
                        </button>
                        <button 
                          onClick={() => setChartType('pie')}
                          className={`p-1 rounded ${chartType === 'pie' ? 'bg-white shadow text-blue-600' : 'text-gray-400 hover:text-gray-600'}`}
                          title="饼图"
                        >
                          <ChartPieIcon className="w-3 h-3" />
                        </button>
                      </div>
                      {renderChart()}
                    </>
                    ) : (
                    <div className="h-full flex flex-col items-center justify-center text-gray-400 bg-gray-50/50 rounded-lg border border-dashed border-gray-200">
                      <span className="text-xs">暂无数据</span>
                    </div>
                    )}
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
