import type { WidgetSpec, NaturalLanguageQueryResponse } from '../api/client';
import { LineChartWidget, BarChartWidget, PieChartWidget, MetricWidget, TableWidget } from './ChartWidgets';

export interface WidgetState {
  id: string;
  spec: WidgetSpec;
  data: NaturalLanguageQueryResponse | null;
  loading: boolean;
  error: string | null;
}

export default function WidgetRenderer({ widget }: { widget: WidgetState }) {
  return (
    <div className="h-full flex flex-col">
      <div className="text-sm font-medium text-gray-700 mb-2 truncate">{widget.spec.title}</div>
      <div className="flex-1 min-h-0">
        {widget.loading ? (
          <div className="flex items-center justify-center h-full">
            <div className="flex items-center gap-2 text-gray-400">
              <div className="w-2 h-2 bg-blue-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
              <div className="w-2 h-2 bg-blue-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
              <div className="w-2 h-2 bg-blue-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
              <span className="text-sm ml-1">加载数据...</span>
            </div>
          </div>
        ) : widget.error ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-sm text-red-500 text-center px-4">{widget.error}</div>
          </div>
        ) : !widget.data ? (
          <div className="flex items-center justify-center h-full text-gray-400 text-sm">无数据</div>
        ) : (
          <ChartSwitch spec={widget.spec} data={widget.data} />
        )}
      </div>
    </div>
  );
}

function ChartSwitch({ spec, data }: { spec: WidgetSpec; data: NaturalLanguageQueryResponse }) {
  switch (spec.type) {
    case 'line': return <LineChartWidget spec={spec} data={data} />;
    case 'bar': return <BarChartWidget spec={spec} data={data} />;
    case 'pie': return <PieChartWidget spec={spec} data={data} />;
    case 'metric': return <MetricWidget spec={spec} data={data} />;
    case 'table': return <TableWidget data={data} />;
    default: return <div className="text-gray-400 text-sm">不支持的图表类型: {spec.type}</div>;
  }
}
