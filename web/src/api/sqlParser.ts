export interface SqlParseResult {
  success: boolean;
  originalSql: string;
  tree: SqlNodeTree;
  lineage: FieldLineage[];
  statistics: ParseStatistics;
  error?: string;
}

export interface SqlNodeTree {
  id: string;
  type: string;
  level: number;
  sql: string;
  alias?: string;
  description?: string;
  tables: TableReference[];
  fields: FieldInfo[];
  expressions: ExpressionInfo[];
  children: SqlNodeTree[];
  joinCondition?: string;
  whereCondition?: string;
  groupBy?: string[];
  orderBy?: string[];
}

export interface FieldLineage {
  outputField: string;
  outputTable: string;
  expression: string;
  function?: string;
  sourceFields: SourceField[];
  path: LineageStep[];
  transformations: string[];
}

export interface SourceField {
  table: string;
  field: string;
  alias?: string;
}

export interface LineageStep {
  from: string;
  to: string;
  operation: string;
}

export interface TableReference {
  name: string;
  alias?: string;
  schema?: string;
  subquery?: string;
  joinType?: string;
  joinedWith?: string;
}

export interface FieldInfo {
  name: string;
  alias?: string;
  table?: string;
  dataType?: string;
  isAggregated: boolean;
  expression?: string;
}

export interface ExpressionInfo {
  type: string;
  expression: string;
  function?: string;
  condition?: string;
}

export interface ParseStatistics {
  totalLevels: number;
  totalTables: number;
  totalJoins: number;
  totalSubqueries: number;
  totalFields: number;
}
