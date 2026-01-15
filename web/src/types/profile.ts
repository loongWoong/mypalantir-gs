// 画像功能类型定义

// Craft.js 组件基础属性
export interface BaseWidgetProps {
  metricId?: string;
  title?: string;
  dataSourceType?: 'metric' | 'query';
  customQuery?: any;
}

// 指标卡片组件属性
export interface MetricCardWidgetProps extends BaseWidgetProps {
  format?: 'number' | 'currency' | 'percentage';
  unit?: string;
  icon?: string;
  precision?: number;
}

// 图表组件属性
export interface ChartWidgetProps extends BaseWidgetProps {
  chartType?: 'bar' | 'line' | 'pie' | 'area';
  xAxis?: string;
  yAxis?: string;
  colors?: string[];
  showLegend?: boolean;
}

// 表格组件属性
export interface TableWidgetProps extends BaseWidgetProps {
  columns?: TableColumn[];
  pageSize?: number;
}

export interface TableColumn {
  field: string;
  title: string;
  width?: number;
  align?: 'left' | 'center' | 'right';
}

// 文本组件属性
export interface TextWidgetProps {
  text?: string;
  fontSize?: number;
  fontWeight?: 'normal' | 'bold';
  color?: string;
  align?: 'left' | 'center' | 'right';
}

// 模板配置（Craft.js 序列化格式）
export interface ProfileTemplate {
  id: string;
  name: string;
  displayName: string;
  description?: string;
  entityType: string;  // Gantry/Vehicle/TollStation
  craftState: string;  // Craft.js 序列化的 JSON
  gridLayout?: GridLayout[];  // React-Grid-Layout 配置（可选）
  isPublic?: boolean;
  creatorId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface GridLayout {
  i: string;  // widget id
  x: number;
  y: number;
  w: number;  // width (占几列)
  h: number;  // height (网格单位)
}

// 创建模板请求
export interface CreateTemplateRequest {
  name: string;
  displayName: string;
  description?: string;
  entityType: string;
  craftState: string;
  gridLayout?: string;  // JSON 字符串
}

// 更新模板请求
export interface UpdateTemplateRequest {
  name?: string;
  displayName?: string;
  description?: string;
  craftState?: string;
  gridLayout?: string;
}

// 画像渲染上下文
export interface ProfileContext {
  entityId?: string;
  startDate?: string;
  endDate?: string;
  [key: string]: any;
}
