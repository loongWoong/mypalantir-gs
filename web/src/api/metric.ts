import apiClient from './client';

// API响应类型
interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

// SQL粘贴指标提取选项
export interface SqlPasteOptions {
  enableLLM?: boolean;
  suggestMetrics?: boolean;
  workspaceId?: string;
}

// 提取的指标
export interface ExtractedMetric {
  suggestedId?: string;
  name: string;
  displayName?: string;
  description?: string;
  category: 'ATOMIC' | 'DERIVED' | 'COMPOSITE';
  sourceSql?: string;
  confidence?: 'HIGH' | 'MEDIUM' | 'LOW';
  notes?: string[];
  unit?: string;
  definition: {
    businessProcess?: string;
    business_process?: string;
    aggregationFunction?: string;
    aggregation_function?: string;
    aggregationField?: string;
    aggregation_field?: string;
    atomicMetricId?: string;
    atomic_metric_id?: string;
    timeDimension?: string;
    time_dimension?: string;
    timeGranularity?: string;
    time_granularity?: string;
    dimensions?: string[];
    filterConditions?: Record<string, any>;
    filter_conditions?: Record<string, any>;
    comparisonType?: string[];
    comparison_type?: string[];
    derivedFormula?: string;
    derived_formula?: string;
    baseMetricIds?: string[];
    base_metric_ids?: string[];
    status?: string;
  };
}

// 验证结果
export interface MetricValidation {
  valid: boolean;
  errorCount: number;
  warningCount: number;
  errors: ValidationMessage[];
  warnings: ValidationMessage[];
  infos: ValidationMessage[];
}

export interface ValidationMessage {
  code: string;
  message: string;
}

// 字段映射
export interface FieldMapping {
  sqlField: string;
  sqlTable: string;
  objectProperty: string;
  objectType: string;
  columnName: string;
  confidence: 'HIGH' | 'MEDIUM' | 'LOW';
}

// 未映射字段
export interface UnmappedField {
  sqlExpression: string;
  fieldType: string;
  aggregated: boolean;
  suggestedObjectType?: string;
  confidence: 'HIGH' | 'MEDIUM' | 'LOW';
}

// 映射对齐结果
export interface MappingAlignment {
  mappingId?: string;
  fieldMappings: FieldMapping[];
  unmappedFields: UnmappedField[];
  involvedObjectTypes: string[];
  tableToObjectMap: Record<string, string>;
}

// 语义指标
export interface SemanticMetric {
  sqlField: string;
  businessMeaning: string;
  recommendedName: string;
  aggregationType: string;
  suggestedMetricType: string;
  unit?: string;
  confidence: number;
}

// 语义维度
export interface SemanticDimension {
  sqlField: string;
  businessMeaning: string;
  timeDimension: boolean;
  enumDimension: boolean;
  enumValues?: string[];
}

// 时间分析
export interface TimeAnalysis {
  timeField?: string;
  timeGranularity?: string;
  timeRange?: {
    start: string;
    end: string;
  };
}

// 过滤分析
export interface FilterAnalysis {
  field: string;
  operator: string;
  value: string;
  businessScope: boolean;
}

// 语义对齐结果
export interface SemanticAlignment {
  metrics: SemanticMetric[];
  dimensions: SemanticDimension[];
  timeAnalysis?: TimeAnalysis;
  filterAnalysis: FilterAnalysis[];
}

// SQL解析结果
export interface SqlPasteParseResult {
  originalSql: string;
  extractedMetrics: ExtractedMetric[];
  validations: MetricValidation[];
  mappingResult?: MappingAlignment;
  semanticResult?: SemanticAlignment;
  suggestions: string[];
  errors?: string[];
}

// 保存结果
export interface SaveResult {
  success: boolean;
  savedIds: string[];
  results: {
    metricName: string;
    savedId: string;
    status: string;
  }[];
  errors: {
    metricName: string;
    message: string;
  }[];
}

// 验证结果（仅验证）
export interface ValidationOnlyResult {
  originalSql: string;
  extractedMetrics: ExtractedMetric[];
  validations: MetricValidation[];
  suggestions: string[];
  allValid: boolean;
}

// 原子指标接口
export interface AtomicMetric {
  id: string;
  name: string;
  displayName?: string;  // 后端返回驼峰命名
  display_name?: string; // 兼容下划线命名
  description?: string;
  businessProcess: string;  // 后端返回驼峰命名
  business_process?: string; // 兼容下划线命名
  aggregationFunction: string;  // 后端返回驼峰命名
  aggregation_function?: string; // 兼容下划线命名
  aggregationField?: string;  // 后端返回驼峰命名
  aggregation_field?: string; // 兼容下划线命名
  unit?: string;
  status: string;
}

// 指标定义接口
export interface MetricDefinition {
  id: string;
  name: string;
  display_name?: string;
  description?: string;
  metric_type: 'derived' | 'composite';
  atomic_metric_id?: string;
  business_scope?: {
    type: 'single' | 'multi';
    base_object_type?: string;
    from?: string;
    links?: Array<{
      name: string;
      select?: string[];
      where?: Record<string, any>;
    }>;
    where?: Record<string, any>;
  };
  time_dimension?: string;
  time_granularity?: 'day' | 'week' | 'month' | 'quarter' | 'year';
  dimensions?: string[];
  filter_conditions?: Record<string, any>;
  comparison_type?: ('YoY' | 'MoM' | 'WoW' | 'QoQ')[];
  derived_formula?: string;
  base_metric_ids?: string[];
  unit?: string;
  status: string;
};

// 指标查询请求
export type MetricQuery = {
  metric_id: string;
  time_range?: {
    start: string;
    end: string;
  };
  dimensions?: Record<string, any>;
  cache?: boolean;
  // 复合指标中的派生指标查询条件
  derived_metric_conditions?: Record<string, {
    time_range?: {
      start: string;
      end: string;
    };
    dimensions?: Record<string, any>;
  }>;
};

// 指标结果
export type MetricResult = {
  metricId: string;
  metricName: string;
  timeGranularity?: string;
  // 支持两种格式：
  // 1. 原始 SQL 结果（推荐）：Record<string, any>[] - 直接返回查询结果的行数据
  // 2. 结构化格式（向后兼容）：MetricDataPoint[] - 用于复合指标等需要特殊处理的场景
  results: (Record<string, any> | MetricDataPoint)[];
  // 列名列表（当返回原始 SQL 结果时提供）
  columns?: string[];
  calculatedAt: string;
  sql?: string;
};

export type MetricDataPoint = {
  timeValue?: string;
  dimensionValues?: Record<string, any>;
  metricValue: number;
  unit?: string;
  comparisons?: Record<string, ComparisonValue>;
};

export type ComparisonValue = {
  value: number;
  display: string;
  description: string;
};

// 指标API
export const metricApi = {
  // 原子指标管理
  createAtomicMetric: async (metric: Partial<AtomicMetric>): Promise<{ id: string }> => {
    const response = await apiClient.post<ApiResponse<{ id: string }>>('/metrics/atomic-metrics', metric);
    return response.data.data;
  },

  listAtomicMetrics: async (): Promise<AtomicMetric[]> => {
    const response = await apiClient.get<ApiResponse<AtomicMetric[]>>('/metrics/atomic-metrics');
    return response.data.data;
  },

  getAtomicMetric: async (id: string): Promise<AtomicMetric> => {
    const response = await apiClient.get<ApiResponse<AtomicMetric>>(`/metrics/atomic-metrics/${id}`);
    return response.data.data;
  },

  updateAtomicMetric: async (id: string, metric: Partial<AtomicMetric>): Promise<void> => {
    await apiClient.put(`/metrics/atomic-metrics/${id}`, metric);
  },

  deleteAtomicMetric: async (id: string): Promise<void> => {
    await apiClient.delete(`/metrics/atomic-metrics/${id}`);
  },

  getAtomicMetricsByBusinessProcess: async (businessProcess: string): Promise<AtomicMetric[]> => {
    const response = await apiClient.get<ApiResponse<AtomicMetric[]>>(
      `/metrics/atomic-metrics/by-business-process/${businessProcess}`
    );
    return response.data.data;
  },

  // 验证原子指标
  validateAtomicMetric: async (metric: Partial<AtomicMetric>): Promise<{
    sql: string;
    columns: string[];
    rows: Record<string, any>[];
    rowCount: number;
  }> => {
    const response = await apiClient.post<ApiResponse<{
      sql: string;
      columns: string[];
      rows: Record<string, any>[];
      rowCount: number;
    }>>('/metrics/atomic-metrics/validate', metric);
    return response.data.data;
  },

  // 验证指标定义（派生指标/复合指标）
  validateMetricDefinition: async (definition: Partial<MetricDefinition>): Promise<{
    sql: string;
    columns: string[];
    rows: Record<string, any>[];
    rowCount: number;
  }> => {
    const response = await apiClient.post<ApiResponse<{
      sql: string;
      columns: string[];
      rows: Record<string, any>[];
      rowCount: number;
    }>>('/metrics/definitions/validate', definition);
    return response.data.data;
  },

  // 指标定义管理
  createMetricDefinition: async (definition: Partial<MetricDefinition>): Promise<{ id: string }> => {
    const response = await apiClient.post<ApiResponse<{ id: string }>>('/metrics/definitions', definition);
    return response.data.data;
  },

  listMetricDefinitions: async (metricType?: string): Promise<MetricDefinition[]> => {
    const params = metricType ? { metric_type: metricType } : {};
    const response = await apiClient.get<ApiResponse<MetricDefinition[]>>('/metrics/definitions', { params });
    return response.data.data;
  },

  getMetricDefinition: async (id: string): Promise<MetricDefinition> => {
    const response = await apiClient.get<ApiResponse<MetricDefinition>>(`/metrics/definitions/${id}`);
    return response.data.data;
  },

  updateMetricDefinition: async (id: string, definition: Partial<MetricDefinition>): Promise<void> => {
    await apiClient.put(`/metrics/definitions/${id}`, definition);
  },

  deleteMetricDefinition: async (id: string): Promise<void> => {
    await apiClient.delete(`/metrics/definitions/${id}`);
  },

  // 指标计算
  calculateMetric: async (query: MetricQuery): Promise<MetricResult> => {
    const response = await apiClient.post<ApiResponse<MetricResult>>('/metrics/calculate', query);
    return response.data.data;
  },

  batchQueryMetrics: async (queries: MetricQuery[]): Promise<MetricResult[]> => {
    const response = await apiClient.post<ApiResponse<MetricResult[]>>('/metrics/query/batch', { queries });
    return response.data.data;
  },

  // ==================== SQL 粘贴指标提取 API ====================

  // 解析 SQL 并提取指标
  parseAndExtractSql: async (sql: string, options?: SqlPasteOptions): Promise<SqlPasteParseResult> => {
    const response = await apiClient.post<ApiResponse<SqlPasteParseResult>>('/sql-paste/parse', {
      sql,
      options: options || { enableLLM: true, suggestMetrics: true },
    });
    return response.data.data;
  },

  // 保存提取的指标
  saveExtractedMetrics: async (
    metrics: ExtractedMetric[],
    createNew: boolean,
    existingMetricIds?: string[],
    workspaceIds?: string[]
  ): Promise<SaveResult> => {
    const response = await apiClient.post<ApiResponse<SaveResult>>('/sql-paste/save', {
      metrics,
      createNew,
      existingMetricIds: existingMetricIds || [],
      workspaceIds: workspaceIds || [],
    });
    return response.data.data;
  },

  // 仅验证指标定义（支持选择性验证）
  validateExtractedMetrics: async (sql: string, metricIndices?: number[]): Promise<ValidationOnlyResult> => {
    const response = await apiClient.post<ApiResponse<ValidationOnlyResult>>('/sql-paste/validate', {
      sql,
      metricIndices: metricIndices || undefined,
    });
    return response.data.data;
  },
};

