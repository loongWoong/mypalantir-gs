import axios from 'axios';

// 如果设置了环境变量则使用，否则使用相对路径（同源）
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 类型定义（匹配后端返回的小写字段名）
export interface ObjectType {
  name: string;
  display_name?: string;  // 显示名称（可选）
  description: string;
  base_type: string | null;
  properties: Property[];
  data_source?: DataSourceMapping;  // 数据源映射配置（可选）
  url?: string;  // 外部链接（可选）
}

export interface Property {
  name: string;
  display_name?: string;
  data_type: string;
  required: boolean;
  description: string;
  default_value: any;
  constraints: Record<string, any> | null;
  derived?: boolean;
  expr?: string;
}

export interface Rule {
  name: string;
  display_name?: string;
  description: string;
  language: string;
  expr: string;
}

export interface LinkType {
  name: string;
  display_name?: string;
  description: string;
  source_type: string;
  target_type: string;
  cardinality: string;
  direction: string;
  properties: Property[];
  property_mappings?: Record<string, string>;
  data_source?: DataSourceMapping;  // 数据源映射配置（可选）
  url?: string;  // 外部链接（可选）
}

export interface DataSourceConfig {
  id: string;
  type: string;
  host: string;
  port: number;
  database: string;
  username: string;
  password?: string;
  jdbc_url: string;
  properties?: Record<string, any>;
}

export interface DataSourceMapping {
  connection_id: string;
  table: string;
  id_column: string;
  source_id_column?: string;  // 用于 LinkType：源对象ID列
  target_id_column?: string;  // 用于 LinkType：目标对象ID列
  link_mode?: string;  // LinkType 映射模式："foreign_key" 或 "relation_table"，null 表示自动检测
  field_mapping: Record<string, string>;
  configured?: boolean;  // 后端可能返回的配置状态字段
}

export interface Instance {
  id: string;
  [key: string]: any;
}

export interface Link {
  id: string;
  source_id: string;
  target_id: string;
  [key: string]: any;
}

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

// Schema API
export const schemaApi = {
  getObjectTypes: async (): Promise<ObjectType[]> => {
    const response = await apiClient.get<ApiResponse<ObjectType[]>>('/schema/object-types');
    return response.data.data;
  },

  getObjectType: async (name: string): Promise<ObjectType> => {
    const response = await apiClient.get<ApiResponse<ObjectType>>(`/schema/object-types/${name}`);
    return response.data.data;
  },

  getObjectTypeProperties: async (name: string): Promise<Property[]> => {
    const response = await apiClient.get<ApiResponse<Property[]>>(`/schema/object-types/${name}/properties`);
    return response.data.data;
  },

  getLinkTypes: async (): Promise<LinkType[]> => {
    const response = await apiClient.get<ApiResponse<LinkType[]>>('/schema/link-types');
    return response.data.data;
  },

  getLinkType: async (name: string): Promise<LinkType> => {
    const response = await apiClient.get<ApiResponse<LinkType>>(`/schema/link-types/${name}`);
    return response.data.data;
  },

  getOutgoingLinks: async (objectTypeName: string): Promise<LinkType[]> => {
    const response = await apiClient.get<ApiResponse<LinkType[]>>(`/schema/object-types/${objectTypeName}/outgoing-links`);
    return response.data.data;
  },

  getIncomingLinks: async (objectTypeName: string): Promise<LinkType[]> => {
    const response = await apiClient.get<ApiResponse<LinkType[]>>(`/schema/object-types/${objectTypeName}/incoming-links`);
    return response.data.data;
  },

  getDataSources: async (): Promise<DataSourceConfig[]> => {
    const response = await apiClient.get<ApiResponse<DataSourceConfig[]>>('/schema/data-sources');
    return response.data.data;
  },

  getDataSource: async (id: string): Promise<DataSourceConfig> => {
    const response = await apiClient.get<ApiResponse<DataSourceConfig>>(`/schema/data-sources/${id}`);
    return response.data.data;
  },

  testConnection: async (id: string): Promise<{ success: boolean; message: string; metadata?: Record<string, string> }> => {
    const response = await apiClient.post<ApiResponse<{ success: boolean; message: string; metadata?: Record<string, string> }>>(
      `/schema/data-sources/${id}/test`
    );
    return response.data.data;
  },
};

// Rules API
export const rulesApi = {
  getRules: async (): Promise<Rule[]> => {
    const response = await apiClient.get<ApiResponse<Rule[]>>('/schema/rules');
    return response.data.data;
  },

  getRule: async (name: string): Promise<Rule> => {
    const response = await apiClient.get<ApiResponse<Rule>>(`/schema/rules/${name}`);
    return response.data.data;
  },

  getRulesForObjectType: async (objectTypeName: string): Promise<Rule[]> => {
    const response = await apiClient.get<ApiResponse<Rule[]>>(`/schema/object-types/${objectTypeName}/rules`);
    return response.data.data;
  },
};

// Model API
export interface ModelInfo {
  id: string;
  path: string;
  displayName: string;
}

export interface CurrentModel {
  modelId: string;
  filePath: string;
}

export const modelApi = {
  listModels: async (): Promise<ModelInfo[]> => {
    const response = await apiClient.get<ApiResponse<ModelInfo[]>>('/models');
    return response.data.data;
  },

  getObjectTypes: async (modelId: string): Promise<ObjectType[]> => {
    const response = await apiClient.get<ApiResponse<ObjectType[]>>(`/models/${modelId}/object-types`);
    return response.data.data;
  },

  getCurrentModel: async (): Promise<CurrentModel> => {
    const response = await apiClient.get<ApiResponse<CurrentModel>>('/models/current');
    return response.data.data;
  },

  switchModel: async (modelId: string): Promise<CurrentModel> => {
    const response = await apiClient.post<ApiResponse<CurrentModel>>(`/models/${modelId}/switch`);
    return response.data.data;
  },
};

export interface OntologyRulePayload {
  name: string;
  display_name?: string;
  description?: string;
  language: string;
  expr: string;
}

/** 函数入参定义（与 ontology parameters/inputs 兼容） */
export interface FunctionInputPayload {
  name: string;
  type: string;
  description?: string;
}

/** 函数出参（ontology 可能为 output: { type } 或直接 return_type） */
export interface FunctionOutputPayload {
  type?: string;
  description?: string;
}

/** 参数绑定：数据来源为 link 或衍生属性 */
export interface ParameterBindingPayload {
  parameter_name: string;
  source_type: 'link' | 'derived_attribute';
  link_name?: string;
  object_type?: string;
  attribute_name?: string;
}

/** 合并后的函数配置：兼容 ontology 的 parameters/return_type/implementation_type 与既有 inputs/output_type/implementation */
export interface FunctionPayload {
  name: string;
  display_name?: string;
  description?: string;
  /** 实现类型（兼容 implementation_type / implementation） */
  implementation?: 'builtin' | 'external' | 'script';
  implementation_type?: string;
  implementation_ref?: string;
  published?: boolean;
  script_configured?: boolean;
  script_path?: string;
  /** 入参（兼容 parameters / inputs / input，后端序列化常用 input） */
  parameters?: FunctionInputPayload[];
  inputs?: FunctionInputPayload[];
  input?: FunctionInputPayload[];
  /** 出参类型（兼容 return_type / output_type / output.type） */
  return_type?: string;
  output_type?: string;
  output?: FunctionOutputPayload;
  parameter_bindings?: ParameterBindingPayload[];
}

export interface OntologyBuilderPayload {
  version: string;
  namespace: string;
  object_types: Array<Record<string, any>>;
  link_types: Array<Record<string, any>>;
  rules?: OntologyRulePayload[];
  functions?: FunctionPayload[];
  data_sources?: Array<Record<string, any>>;
}

export interface OntologyValidationResult {
  valid: boolean;
  errors: string[];
  warnings?: string[];
  yaml: string;
}

export interface OntologySaveResult {
  success: boolean;
  filePath?: string;
  version?: string;
  message: string;
}

export interface OntologyVersion {
  version: string;
  namespace: string;
  filename: string;
  file_path: string;
  previous_version?: string;
  commit_message?: string;
  author?: string;
  timestamp: number;
  workspace_id?: string;
  workspace_name?: string;
  changes?: string[];
}

export interface VersionDiffResult {
  objectTypeDiffs: ObjectTypeDiff[];
  linkTypeDiffs: LinkTypeDiff[];
  metadataChanges: string[];
}

export interface ObjectTypeDiff {
  name: string;
  type: 'ADDED' | 'MODIFIED' | 'DELETED';
  oldValue?: any;
  newValue?: any;
  changes: string[];
  propertyDiffs: PropertyDiff[];
}

export interface PropertyDiff {
  name: string;
  type: 'ADDED' | 'MODIFIED' | 'DELETED';
  oldValue?: any;
  newValue?: any;
}

export interface LinkTypeDiff {
  name: string;
  type: 'ADDED' | 'MODIFIED' | 'DELETED';
  oldValue?: any;
  newValue?: any;
  changes: string[];
}

export const ontologyBuilderApi = {
  validate: async (payload: OntologyBuilderPayload): Promise<OntologyValidationResult> => {
    const response = await apiClient.post<ApiResponse<OntologyValidationResult>>('/ontology-builder/validate', payload);
    return response.data.data;
  },
  save: async (
    payload: OntologyBuilderPayload, 
    filename?: string, 
    workspaceId?: string, 
    commitMessage?: string
  ): Promise<OntologySaveResult> => {
    const params = new URLSearchParams();
    if (filename) params.append('filename', filename);
    if (workspaceId) params.append('workspaceId', workspaceId);
    if (commitMessage) params.append('commitMessage', commitMessage);
    const queryString = params.toString();
    const url = `/ontology-builder/save${queryString ? '?' + queryString : ''}`;
    const response = await apiClient.post<ApiResponse<OntologySaveResult>>(url, payload);
    return response.data.data;
  },
  listFiles: async (): Promise<string[]> => {
    const response = await apiClient.get<ApiResponse<string[]>>('/ontology-builder/files');
    return response.data.data;
  },
  loadFile: async (filename: string): Promise<OntologyBuilderPayload> => {
    const response = await apiClient.get<ApiResponse<OntologyBuilderPayload>>(
      `/ontology-builder/load?filename=${encodeURIComponent(filename)}`
    );
    return response.data.data;
  },
  /** 读取函数脚本内容，脚本存储在 ontology/functions/script/ 下，scriptPath 如 toll/sample_check.js */
  getScript: async (scriptPath: string): Promise<{ content: string; exists: boolean }> => {
    const response = await apiClient.get<ApiResponse<{ content: string; exists: string }>>(
      `/ontology-builder/script?scriptPath=${encodeURIComponent(scriptPath)}`
    );
    const data = response.data.data;
    return { content: data?.content ?? '', exists: data?.exists === 'true' };
  },
  /** 保存函数脚本到 ontology/functions/script/{scriptPath} */
  saveScript: async (scriptPath: string, content: string): Promise<{ success: boolean; scriptPath: string }> => {
    const response = await apiClient.post<ApiResponse<{ success: boolean; scriptPath: string }>>(
      '/ontology-builder/script',
      { scriptPath, content }
    );
    return response.data.data;
  },
  getVersionHistory: async (filename: string): Promise<OntologyVersion[]> => {
    const response = await apiClient.get<ApiResponse<OntologyVersion[]>>(
      `/ontology-builder/versions/${encodeURIComponent(filename)}/history`
    );
    return response.data.data;
  },
  getVersion: async (filename: string, version: string): Promise<OntologyBuilderPayload> => {
    const response = await apiClient.get<ApiResponse<OntologyBuilderPayload>>(
      `/ontology-builder/versions/${encodeURIComponent(filename)}/${encodeURIComponent(version)}`
    );
    return response.data.data;
  },
  compareVersions: async (filename: string, version1: string, version2: string): Promise<VersionDiffResult> => {
    const response = await apiClient.post<ApiResponse<VersionDiffResult>>(
      `/ontology-builder/versions/${encodeURIComponent(filename)}/compare`,
      { version1, version2 }
    );
    return response.data.data;
  },
  rollback: async (filename: string, version: string): Promise<OntologySaveResult> => {
    const response = await apiClient.post<ApiResponse<OntologySaveResult>>(
      `/ontology-builder/versions/${encodeURIComponent(filename)}/rollback?version=${encodeURIComponent(version)}`
    );
    return response.data.data;
  },
};

// Instance API
export const instanceApi = {
  create: async (objectType: string, data: Record<string, any>): Promise<string> => {
    const response = await apiClient.post<ApiResponse<{ id: string }>>(`/instances/${objectType}`, data);
    return response.data.data.id;
  },

  list: async (objectType: string, offset = 0, limit = 20, filters?: Record<string, any>): Promise<{ items: Instance[]; total: number }> => {
    const params = new URLSearchParams({
      offset: offset.toString(),
      limit: limit.toString(),
      ...filters,
    });
    const response = await apiClient.get<ApiResponse<{ items: Instance[]; total: number; offset: number; limit: number }>>(
      `/instances/${objectType}?${params}`
    );
    return response.data.data;
  },

  get: async (objectType: string, id: string): Promise<Instance> => {
    const response = await apiClient.get<ApiResponse<Instance>>(`/instances/${objectType}/${id}`);
    return response.data.data;
  },

  update: async (objectType: string, id: string, data: Record<string, any>): Promise<void> => {
    await apiClient.put(`/instances/${objectType}/${id}`, data);
  },

  delete: async (objectType: string, id: string): Promise<void> => {
    await apiClient.delete(`/instances/${objectType}/${id}`);
  },

  listWithMapping: async (objectType: string, mappingId: string, offset = 0, limit = 20, filters?: Record<string, any>): Promise<{ items: Instance[]; total: number }> => {
    const params = new URLSearchParams({
      offset: offset.toString(),
      limit: limit.toString(),
      mappingId: mappingId,
      ...filters,
    });
    const response = await apiClient.get<ApiResponse<{ items: Instance[]; total: number; offset: number; limit: number }>>(
      `/instances/${objectType}?${params}`
    );
    return response.data.data;
  },

  syncFromMapping: async (objectType: string, mappingId: string): Promise<void> => {
    await apiClient.post(`/instances/${objectType}/sync-from-mapping/${mappingId}`);
  },

  buildEtlModel: async (
    objectType: string,
    mappingId?: string,
    targetDatasourceId?: string,
    targetTableName?: string
  ): Promise<{ etlModel: any; createResult: any; success: boolean }> => {
    const params = new URLSearchParams();
    params.append('objectType', objectType);
    if (mappingId) params.append('mappingId', mappingId);
    if (targetDatasourceId) params.append('targetDatasourceId', targetDatasourceId);
    if (targetTableName) params.append('targetTableName', targetTableName);
    
    const response = await apiClient.post<ApiResponse<{ etlModel: any; createResult: any; success: boolean }>>(
      `/etl-model/build?${params.toString()}`
    );
    return response.data.data;
  },

  // 批量获取单个对象类型的实例
  getBatch: async (objectType: string, ids: string[]): Promise<Record<string, Instance | null>> => {
    const response = await apiClient.post<ApiResponse<Record<string, Instance | null>>>(
      `/instances/${objectType}/batch`,
      { ids }
    );
    return response.data.data;
  },

  // 批量获取多个对象类型的实例
  getBatchMultiType: async (queries: Array<{ objectType: string; ids: string[] }>): Promise<Record<string, Instance | null>> => {
    const response = await apiClient.post<ApiResponse<Record<string, Instance | null>>>(
      `/instances/batch`,
      { queries }
    );
    return response.data.data;
  },
};

// Link API
export const linkApi = {
  create: async (linkType: string, sourceId: string, targetId: string, properties?: Record<string, any>): Promise<string> => {
    const response = await apiClient.post<ApiResponse<{ id: string }>>(`/links/${linkType}`, {
      source_id: sourceId,
      target_id: targetId,
      properties: properties || {},
    });
    return response.data.data.id;
  },

  list: async (linkType: string, offset = 0, limit = 20): Promise<{ items: Link[]; total: number }> => {
    const params = new URLSearchParams({
      offset: offset.toString(),
      limit: limit.toString(),
    });
    const response = await apiClient.get<ApiResponse<{ items: Link[]; total: number; offset: number; limit: number }>>(
      `/links/${linkType}?${params}`
    );
    return response.data.data;
  },

  get: async (linkType: string, id: string): Promise<Link> => {
    const response = await apiClient.get<ApiResponse<Link>>(`/links/${linkType}/${id}`);
    return response.data.data;
  },

  update: async (linkType: string, id: string, properties: Record<string, any>): Promise<void> => {
    await apiClient.put(`/links/${linkType}/${id}`, properties);
  },

  delete: async (linkType: string, id: string): Promise<void> => {
    await apiClient.delete(`/links/${linkType}/${id}`);
  },

  getInstanceLinks: async (objectType: string, instanceId: string, linkType: string, direction: 'outgoing' | 'incoming' = 'outgoing'): Promise<Link[]> => {
    const params = new URLSearchParams({ direction });
    const response = await apiClient.get<ApiResponse<Link[]>>(`/instances/${objectType}/${instanceId}/links/${linkType}?${params}`);
    return response.data.data;
  },

  getConnectedInstances: async (objectType: string, instanceId: string, linkType: string, direction = 'outgoing'): Promise<Instance[]> => {
    const response = await apiClient.get<ApiResponse<Instance[]>>(
      `/instances/${objectType}/${instanceId}/connected/${linkType}?direction=${direction}`
    );
    return response.data.data;
  },

  sync: async (linkType: string): Promise<{ links_created: number }> => {
    const response = await apiClient.post<ApiResponse<{ links_created: number }>>(`/links/${linkType}/sync`);
    return response.data.data;
  },

  getStats: async (linkType: string): Promise<{ source_count: number; target_count: number; link_count: number; source_coverage: number; target_coverage: number }> => {
    const response = await apiClient.get<ApiResponse<{ source_count: number; target_count: number; link_count: number; source_coverage: number; target_coverage: number }>>(`/links/${linkType}/stats`);
    return response.data.data;
  },
};

// Database API
export const databaseApi = {
  getDefaultDatabaseId: async (): Promise<{ id: string }> => {
    const response = await apiClient.get<ApiResponse<{ id: string }>>('/database/default-id');
    return response.data.data;
  },

  listDatabases: async (): Promise<any[]> => {
    const response = await apiClient.get<ApiResponse<any[]>>('/database/list');
    return response.data.data;
  },

  getTables: async (databaseId?: string): Promise<any[]> => {
    const url = databaseId 
      ? `/database/tables?databaseId=${databaseId}`
      : '/database/tables';
    const response = await apiClient.get<ApiResponse<any[]>>(url);
    return response.data.data;
  },

  getColumns: async (databaseId: string, tableName: string): Promise<any[]> => {
    const url = databaseId
      ? `/database/tables/${tableName}/columns?databaseId=${databaseId}`
      : `/database/tables/${tableName}/columns`;
    const response = await apiClient.get<ApiResponse<any[]>>(url);
    return response.data.data;
  },

  getTableInfo: async (databaseId: string, tableName: string): Promise<any> => {
    const url = databaseId
      ? `/database/tables/${tableName}?databaseId=${databaseId}`
      : `/database/tables/${tableName}`;
    const response = await apiClient.get<ApiResponse<any>>(url);
    return response.data.data;
  },

  syncTables: async (databaseId: string): Promise<{ tables_created: number; columns_created: number; columns_updated: number }> => {
    const response = await apiClient.post<ApiResponse<{ tables_created: number; columns_created: number; columns_updated: number }>>(
      `/database/sync-tables?databaseId=${databaseId}`
    );
    return response.data.data;
  },
};

// Mapping API
export const mappingApi = {
  create: async (objectType: string, tableId: string, columnPropertyMappings: Record<string, string>, primaryKeyColumns?: string | string[]): Promise<string> => {
    const primaryKeyColumnsArray = Array.isArray(primaryKeyColumns) ? primaryKeyColumns : (primaryKeyColumns ? [primaryKeyColumns] : null);
    const primaryKeyColumnSingle = Array.isArray(primaryKeyColumns) ? (primaryKeyColumns.length > 0 ? primaryKeyColumns[0] : null) : primaryKeyColumns;
    console.log('[mappingApi.create] primaryKeyColumns input:', primaryKeyColumns);
    console.log('[mappingApi.create] primary_key_columns to send:', primaryKeyColumnsArray);
    console.log('[mappingApi.create] primary_key_column to send:', primaryKeyColumnSingle);
    const response = await apiClient.post<ApiResponse<{ id: string }>>('/mappings', {
      object_type: objectType,
      table_id: tableId,
      column_property_mappings: columnPropertyMappings,
      // 兼容旧格式（单个字符串）和新格式（数组）
      primary_key_columns: primaryKeyColumnsArray,
      // 保留旧字段以兼容后端
      primary_key_column: primaryKeyColumnSingle,
    });
    return response.data.data.id;
  },

  get: async (mappingId: string): Promise<any> => {
    const response = await apiClient.get<ApiResponse<any>>(`/mappings/${mappingId}`);
    return response.data.data;
  },

  getByObjectType: async (objectType: string): Promise<any[]> => {
    const response = await apiClient.get<ApiResponse<any[]>>(`/mappings/by-object-type/${objectType}`);
    return response.data.data;
  },

  getByTable: async (tableId: string): Promise<any[]> => {
    const response = await apiClient.get<ApiResponse<any[]>>(`/mappings/by-table/${tableId}`);
    return response.data.data;
  },

  update: async (mappingId: string, columnPropertyMappings: Record<string, string>, primaryKeyColumns?: string | string[]): Promise<void> => {
    const primaryKeyColumnsArray = Array.isArray(primaryKeyColumns) ? primaryKeyColumns : (primaryKeyColumns ? [primaryKeyColumns] : null);
    const primaryKeyColumnSingle = Array.isArray(primaryKeyColumns) ? (primaryKeyColumns.length > 0 ? primaryKeyColumns[0] : null) : primaryKeyColumns;
    console.log('[mappingApi.update] primaryKeyColumns input:', primaryKeyColumns);
    console.log('[mappingApi.update] primary_key_columns to send:', primaryKeyColumnsArray);
    console.log('[mappingApi.update] primary_key_column to send:', primaryKeyColumnSingle);
    await apiClient.put(`/mappings/${mappingId}`, {
      column_property_mappings: columnPropertyMappings,
      // 兼容旧格式（单个字符串）和新格式（数组）
      primary_key_columns: primaryKeyColumnsArray,
      // 保留旧字段以兼容后端
      primary_key_column: primaryKeyColumnSingle,
    });
  },

  delete: async (mappingId: string): Promise<void> => {
    await apiClient.delete(`/mappings/${mappingId}`);
  },
};

// Query API
export type FilterExpression = 
  | ['=' | '>' | '<' | '>=' | '<=' | 'LIKE', string, any]  // 简单条件
  | ['between', string, any, any]  // 范围条件
  | ['and' | 'or', ...FilterExpression[]];  // 逻辑组合

export interface Metric {
  function: 'sum' | 'avg' | 'count' | 'min' | 'max';
  field: string;  // 支持路径，如 "hasTollRecords.amount"
  alias?: string;
}

export interface QueryRequest {
  object?: string;  // 新增：替代 from
  from?: string;    // 保留：向后兼容
  select?: string[];
  filter?: FilterExpression[];  // 新增：表达式过滤
  where?: Record<string, any>;  // 保留：向后兼容
  links?: LinkQuery[];
  group_by?: string[];  // 新增：分组
  metrics?: Metric[];   // 新增：聚合指标
  orderBy?: OrderBy[];
  limit?: number;
  offset?: number;
}

export interface LinkQuery {
  name: string;
  object?: string;  // 新增：明确指定目标对象类型
  select?: string[];
  filter?: FilterExpression[];  // 新增：关联级别的过滤
  where?: Record<string, any>;  // 保留：向后兼容
  links?: LinkQuery[];
}

export interface OrderBy {
  field: string;
  direction: 'ASC' | 'DESC';
}

export interface QueryResult {
  columns: string[];
  rows: Record<string, any>[];
  rowCount: number;
}

export const queryApi = {
  execute: async (query: QueryRequest): Promise<QueryResult> => {
    const response = await apiClient.post<ApiResponse<QueryResult>>('/query', query);
    return response.data.data;
  },
};

// Natural Language Query API
export interface NaturalLanguageQueryResponse {
  query: string;
  convertedQuery: QueryRequest;
  columns?: string[];
  rows?: Record<string, any>[];
  rowCount?: number;
}

/** 自然语言查询请求：query 必填，dataSourceType 控制解析与执行时使用的数据源 */
export interface NaturalLanguageQueryRequest {
  query: string;
  /** raw=原始数据（映射表），sync=同步数据（同步表）；不传默认 sync */
  dataSourceType?: 'raw' | 'sync';
}

export const naturalLanguageQueryApi = {
  execute: async (queryOrRequest: string | NaturalLanguageQueryRequest): Promise<NaturalLanguageQueryResponse> => {
    const body = typeof queryOrRequest === 'string'
      ? { query: queryOrRequest }
      : { query: queryOrRequest.query, dataSourceType: queryOrRequest.dataSourceType };
    const response = await apiClient.post<ApiResponse<NaturalLanguageQueryResponse>>(
      '/query/natural-language',
      body
    );
    return response.data.data;
  },

  convert: async (queryOrRequest: string | NaturalLanguageQueryRequest): Promise<NaturalLanguageQueryResponse> => {
    const body = typeof queryOrRequest === 'string'
      ? { query: queryOrRequest }
      : { query: queryOrRequest.query, dataSourceType: queryOrRequest.dataSourceType };
    const response = await apiClient.post<ApiResponse<NaturalLanguageQueryResponse>>(
      '/query/natural-language/convert',
      body
    );
    return response.data.data;
  },
};

// Reasoning API
export interface TraceEntry {
  cycle: number;
  rule: string;
  fact: string;
}

export interface MatchDetail {
  condition: string;
  matched: boolean;
  actualValue?: string;
  description?: string;
}

export interface RuleEvaluation {
  rule: string;
  displayName: string;
  matched: boolean;
  fact?: string;
  factIsNew?: boolean;
  matchDetails?: MatchDetail[];
}

export interface CycleDetail {
  cycle: number;
  newFactsProduced: boolean;
  rules: RuleEvaluation[];
}

export interface InferenceResult {
  cycleCount: number;
  cycles: CycleDetail[];
  trace: TraceEntry[];
  facts: Record<string, any>;
  /** 加载关联数据摘要：link 名称 -> { count, displayName }（推理 API 返回） */
  linkedDataSummary?: Record<string, { count: number; displayName?: string }>;
}

export interface ReasoningStatus {
  parsedRules: number;
  registeredFunctions: string[];
}

/** 批量推理单条实例摘要 */
export interface BatchInstanceResult {
  instanceId: string;
  firedRules: string[];
  facts: Record<string, any>;
  cycleCount?: number;
  error?: string;
}

export const reasoningApi = {
  /** 按当前本体模型对指定对象类型与实例执行推理 */
  infer: async (objectType: string, instanceId: string): Promise<InferenceResult> => {
    const response = await apiClient.post<ApiResponse<InferenceResult>>(
      '/reasoning/infer',
      { object_type: objectType, instance_id: instanceId }
    );
    return response.data.data;
  },

  /** 兼容旧接口：仅传 passageId 时等价于 object_type=Passage */
  inferPassage: async (passageId: string): Promise<InferenceResult> => {
    const response = await apiClient.post<ApiResponse<InferenceResult>>(
      '/reasoning/infer',
      { passage_id: passageId }
    );
    return response.data.data;
  },

  batch: async (objectType: string, limit = 10): Promise<Record<string, any>[]> => {
    const response = await apiClient.post<ApiResponse<Record<string, any>[]>>(
      '/reasoning/batch',
      { object_type: objectType, limit }
    );
    return response.data.data;
  },

  /** 批量推理（全量）：异步遍历所有实例，结果写入 logs/Reasoning.log */
  batchAll: async (objectType: string): Promise<{ status: string; log: string; message: string }> => {
    const response = await apiClient.post<ApiResponse<{ status: string; log: string; message: string }>>(
      '/reasoning/batch-all',
      { object_type: objectType }
    );
    return response.data.data;
  },

  /** 批量推理（全量同步）：遍历所有实例，同步返回每条实例的规则摘要，供前端分组展示 */
  batchAllSync: async (objectType: string): Promise<BatchInstanceResult[]> => {
    const response = await apiClient.post<ApiResponse<BatchInstanceResult[]>>(
      '/reasoning/batch-all-sync',
      { object_type: objectType }
    );
    return response.data.data;
  },

  /** 当前本体中有规则的对象类型（推理根类型），随右上角模型切换 */
  rootTypes: async (): Promise<string[]> => {
    const response = await apiClient.get<ApiResponse<string[]>>('/reasoning/root-types');
    return response.data.data;
  },

  status: async (): Promise<ReasoningStatus> => {
    const response = await apiClient.get<ApiResponse<ReasoningStatus>>('/reasoning/status');
    return response.data.data;
  },

  /** CEL 表达式校验（查询验证、脚本编辑） */
  validateCel: async (expr: string): Promise<{ valid: boolean; message: string }> => {
    const response = await apiClient.post<ApiResponse<Record<string, unknown>>>(
      '/reasoning/cel/validate',
      { expr: expr ?? '' }
    );
    const data = response.data.data as { valid?: boolean; message?: string };
    return { valid: !!data?.valid, message: data?.message ?? '' };
  },

  /** CEL 表达式求值（脚本测试，传入样本上下文） */
  evaluateCel: async (
    expr: string,
    properties?: Record<string, unknown>,
    linkedData?: Record<string, unknown[]>
  ): Promise<unknown> => {
    const response = await apiClient.post<ApiResponse<unknown>>('/reasoning/cel/evaluate', {
      expr: expr ?? '',
      properties: properties ?? {},
      linked_data: linkedData ?? {},
    });
    return response.data.data;
  },

  /** 函数测试：使用给定参数调用已注册函数 */
  testFunction: async (name: string, args: unknown[]): Promise<unknown> => {
    const response = await apiClient.post<ApiResponse<unknown>>('/reasoning/functions/test', {
      name,
      args: args ?? [],
    });
    return response.data.data;
  },

  /**
   * 按实例测试函数：使用当前本体模型下指定根对象类型与实例 ID 构建上下文（实例+关联数据），按函数入参解析后调用。
   * 调用前需已通过模型切换接口切换到目标本体；数据来自该模型绑定的数据存储。
   */
  testFunctionWithInstance: async (
    name: string,
    objectType: string,
    instanceId: string
  ): Promise<unknown> => {
    const response = await apiClient.post<ApiResponse<unknown>>(
      '/reasoning/functions/test-with-instance',
      { name, object_type: objectType, instance_id: instanceId }
    );
    return response.data.data;
  },

  /**
   * CEL 表达式按实例求值（用于衍生属性等验证）。
   * 使用当前本体模型下指定根对象类型与实例 ID 构建上下文（实例 + 关联数据 + 衍生属性）并求值，用于「基于实例数据求值」校验。
   */
  evaluateCelWithInstance: async (
    expr: string,
    objectType: string,
    instanceId: string
  ): Promise<unknown> => {
    const response = await apiClient.post<ApiResponse<unknown>>(
      '/reasoning/cel/evaluate-with-instance',
      { expr: expr ?? '', object_type: objectType, instance_id: instanceId }
    );
    return response.data.data;
  },
};

// Agent API
export interface AgentStep {
  thought?: string;
  tool?: string;
  args?: Record<string, any>;
  observation?: string;
}

export interface AgentChatResponse {
  answer: string;
  steps: AgentStep[];
}

export interface AgentSSEEvent {
  type: 'step' | 'answer' | 'error';
  data: any;
}

export const agentApi = {
  chat: async (message: string): Promise<AgentChatResponse> => {
    const response = await apiClient.post<ApiResponse<AgentChatResponse>>(
      '/agent/chat',
      { message }
    );
    return response.data.data;
  },

  /** CEL 表达式按实例求值：使用当前本体模型下指定根对象类型与实例 ID 构建上下文并求值 */
  evaluateCelWithInstance: async (
    expr: string,
    objectType: string,
    instanceId: string
  ): Promise<unknown> => {
    const response = await apiClient.post<ApiResponse<unknown>>('/reasoning/cel/evaluate-with-instance', {
      expr: expr ?? '',
      object_type: objectType,
      instance_id: instanceId,
    });
    return response.data.data;
  },

  chatStream: (message: string, onEvent: (event: AgentSSEEvent) => void, onDone: () => void) => {
    const url = `${API_BASE_URL}/agent/chat/stream?message=${encodeURIComponent(message)}`;
    const eventSource = new EventSource(url);

    eventSource.addEventListener('step', (e) => {
      onEvent({ type: 'step', data: JSON.parse(e.data) });
    });

    eventSource.addEventListener('answer', (e) => {
      onEvent({ type: 'answer', data: JSON.parse(e.data) });
      eventSource.close();
      onDone();
    });

    eventSource.addEventListener('error', (e) => {
      if ((e as MessageEvent).data) {
        onEvent({ type: 'error', data: JSON.parse((e as MessageEvent).data) });
      }
      eventSource.close();
      onDone();
    });

    return () => {
      eventSource.close();
      onDone();
    };
  },
};

// Comparison API
export interface ComparisonRequest {
  sourceTableId: string;
  targetTableId: string;
  sourceKey: string;
  targetKey: string;
  columnMapping: Record<string, string>;
}

export interface ComparisonResult {
  taskId: string;
  timestamp: number;
  sourceTotal: number;
  targetTotal: number;
  matchedCount: number;
  mismatchedCount: number;
  sourceOnlyCount: number;
  targetOnlyCount: number;
  diffs: DiffRecord[];
}

export interface DiffRecord {
  keyValue: string;
  type: string;
  details: ValueDiff[];
}

export interface ValueDiff {
  fieldName: string;
  sourceValue: any;
  targetValue: any;
}

export const comparisonApi = {
  run: async (request: ComparisonRequest): Promise<ComparisonResult> => {
    const response = await apiClient.post<ApiResponse<ComparisonResult>>('/comparison/run', request);
    return response.data.data;
  },
};

export default apiClient;
