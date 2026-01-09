package com.mypalantir.sqlparse;

import org.apache.calcite.config.Lex;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.plan.hep.HepPlanner;
import org.apache.calcite.plan.hep.HepProgramBuilder;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.core.*;
import org.apache.calcite.rel.metadata.*;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.rex.*;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.calcite.util.Source;
import org.apache.calcite.schema.Schemas;
import java.io.Reader;
import java.io.StringReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * RexNode 血缘提取器 (Calcite 1.37.0 兼容版)
 * 职责：基于 Calcite RexNode 递归遍历表达式树，追踪每个输出列的完整血缘链路
 */
@Component
public class RexNodeLineageExtractor {

    private static final Logger logger = LoggerFactory.getLogger(RexNodeLineageExtractor.class);

    private final Map<String, RelNode> tableAliasMap = new HashMap<>();

    public RexMetricParseResult parse(String sql) {
        logger.info("[RexNodeLineageExtractor] 开始解析 SQL");
        logger.info("[RexNodeLineageExtractor] SQL长度: {}", sql.length());

        RexMetricParseResult result = new RexMetricParseResult();
        result.setOriginalSql(sql);
        tableAliasMap.clear();

        try {
            SqlParser.Config config = SqlParser.config()
                .withLex(Lex.MYSQL)
                .withIdentifierMaxLength(128);
            SqlParser parser = SqlParser.create(sql, config);
            SqlNode sqlNode = parser.parseStmt();

            if (!(sqlNode instanceof SqlSelect)) {
                logger.warn("[RexNodeLineageExtractor] 仅支持 SELECT 语句");
                result.setError("仅支持 SELECT 语句");
                return result;
            }

            SqlSelect sqlSelect = (SqlSelect) sqlNode;
            result.setSqlSelect(sqlSelect);

            RelNode relNode = convertToRelNode(sqlSelect, sql);
            if (relNode == null) {
                result.setError("转换为 RelNode 失败");
                return result;
            }

            result.setRelNode(relNode);

            buildTableAliasMap(relNode, sql);
            extractAllLineages(relNode, result);

            logger.info("[RexNodeLineageExtractor] 解析完成，共 {} 个输出列", result.getColumnLineages().size());

        } catch (Exception e) {
            logger.error("[RexNodeLineageExtractor] 解析失败: {}", e.getMessage(), e);
            result.setError("解析失败: " + e.getMessage());
        }

        return result;
    }

    private void buildTableAliasMap(RelNode root, String sql) {
        logger.info("[RexNodeLineageExtractor] 构建表别名映射");

        Deque<RelNode> queue = new ArrayDeque<>();
        queue.add(root);
        int aliasCounter = 1;

        while (!queue.isEmpty()) {
            RelNode node = queue.pollFirst();

            if (node instanceof Project) {
                Project project = (Project) node;
                RelNode input = project.getInput();
                if (input instanceof Project || input instanceof Aggregate || input instanceof Filter) {
                    String alias = "subquery_" + aliasCounter++;
                    tableAliasMap.put(alias, input);
                    logger.info("[RexNodeLineageExtractor] 添加别名映射: {} -> {}", alias, input.getClass().getSimpleName());
                    queue.add(input);
                }
            } else if (node instanceof Aggregate) {
                Aggregate aggregate = (Aggregate) node;
                for (RelNode input : aggregate.getInputs()) {
                    if (input instanceof Project || input instanceof Filter) {
                        String alias = "subquery_" + aliasCounter++;
                        tableAliasMap.put(alias, input);
                        logger.info("[RexNodeLineageExtractor] 添加别名映射: {} -> {}", alias, input.getClass().getSimpleName());
                        queue.add(input);
                    }
                }
            } else if (node instanceof Filter) {
                Filter filter = (Filter) node;
                queue.add(filter.getInput());
            } else if (node instanceof Join) {
                Join join = (Join) node;
                queue.add(join.getLeft());
                queue.add(join.getRight());
            } else if (node instanceof Union) {
                Union union = (Union) node;
                for (RelNode input : union.getInputs()) {
                    queue.add(input);
                }
            } else if (node instanceof TableScan) {
                TableScan scan = (TableScan) node;
                String tableName = scan.getTable().getQualifiedName().toString();
                if (!tableAliasMap.containsValue(scan)) {
                    tableAliasMap.put(tableName, scan);
                    logger.info("[RexNodeLineageExtractor] 真实表: {}", tableName);
                }
            }
        }

        logger.info("[RexNodeLineageExtractor] 别名映射表大小: {}", tableAliasMap.size());
    }

    private void extractAllLineages(RelNode relNode, RexMetricParseResult result) {
        logger.info("[RexNodeLineageExtractor] 开始递归提取血缘, 节点类型: {}", relNode.getClass().getSimpleName());

        if (relNode instanceof Project) {
            extractProjectLineage((Project) relNode, result);
        } else if (relNode instanceof Aggregate) {
            extractAggregateLineage((Aggregate) relNode, result);
        } else if (relNode instanceof Join) {
            extractJoinLineage((Join) relNode, result);
        } else if (relNode instanceof Union) {
            extractUnionLineage((Union) relNode, result);
        } else if (relNode instanceof Correlate) {
            extractCorrelateLineage((Correlate) relNode, result);
        } else if (relNode instanceof Filter) {
            extractFilterLineage((Filter) relNode, result);
        } else {
            logger.warn("[RexNodeLineageExtractor] 未处理的节点类型: {}", relNode.getClass().getSimpleName());
        }

        for (RelNode input : relNode.getInputs()) {
            extractAllLineages(input, result);
        }
    }

    private void extractProjectLineage(Project project, RexMetricParseResult result) {
        logger.info("[RexNodeLineageExtractor] 提取 Project 血缘, 输出列数: {}", project.getRowType().getFieldCount());

        List<? extends RexNode> projectList = project.getProjects();
        RelMetadataQuery mq = project.getCluster().getMetadataQuery();
        RelNode input = project.getInput();

        for (int i = 0; i < projectList.size(); i++) {
            RexNode rexNode = projectList.get(i);
            String outputName = project.getRowType().getFieldList().get(i).getName();

            ColumnLineage columnLineage = extractRexNodeLineage(rexNode, input, mq);
            columnLineage.setOutputName(outputName);
            columnLineage.setOutputIndex(i);

            result.getColumnLineages().add(columnLineage);
            logger.info("[RexNodeLineageExtractor] Project 列 {} -> {} 个源", outputName, columnLineage.getSources().size());
        }
    }

    private void extractAggregateLineage(Aggregate aggregate, RexMetricParseResult result) {
        logger.info("[RexNodeLineageExtractor] 提取 Aggregate 血缘, GROUP KEY 数量: {}", aggregate.getGroupSet().size());

        RelMetadataQuery mq = aggregate.getCluster().getMetadataQuery();
        int groupCount = aggregate.getGroupSet().size();

        for (int i = 0; i < groupCount; i++) {
            int groupIndex = aggregate.getGroupSet().asList().get(i);
            String outputName = aggregate.getRowType().getFieldList().get(i).getName();

            ColumnLineage columnLineage = new ColumnLineage();
            columnLineage.setOutputName(outputName);
            columnLineage.setOutputIndex(i);
            columnLineage.setRexNodeType("GROUP_KEY");
            columnLineage.setTransformType("GROUP_KEY");

            Set<RelColumnOrigin> origins = mq.getColumnOrigins(aggregate, i);
            if (origins != null) {
                for (RelColumnOrigin origin : origins) {
                    ColumnSource source = resolveColumnOrigin(origin, aggregate, mq);
                    columnLineage.getSources().add(source);
                }
            }

            result.getColumnLineages().add(columnLineage);
            logger.info("[RexNodeLineageExtractor] GROUP_KEY {} -> {} 个源", outputName, columnLineage.getSources().size());
        }

        for (int i = 0; i < aggregate.getAggCallList().size(); i++) {
            AggregateCall aggCall = aggregate.getAggCallList().get(i);
            String outputName = aggregate.getRowType().getFieldList().get(groupCount + i).getName();

            ColumnLineage columnLineage = new ColumnLineage();
            columnLineage.setOutputName(outputName);
            columnLineage.setOutputIndex(groupCount + i);
            columnLineage.setAggregationFunction(aggCall.getAggregation().toString());
            columnLineage.setRexNodeType("AGGREGATE_CALL");
            columnLineage.setDistinct(aggCall.isDistinct());

            List<RelColumnOrigin> origins = traceAggCallOrigins(aggCall, aggregate, mq);
            for (RelColumnOrigin origin : origins) {
                ColumnSource source = resolveColumnOrigin(origin, aggregate, mq);
                columnLineage.getSources().add(source);
            }

            if (aggCall.filterArg >= 0) {
                Map<String, Object> filterCondition = new HashMap<>();
                filterCondition.put("filterArgIndex", aggCall.filterArg);
                columnLineage.setFilterCondition(filterCondition);
            }

            result.getColumnLineages().add(columnLineage);
            logger.info("[RexNodeLineageExtractor] Aggregate 列 {} ({}) -> {} 个源",
                outputName, aggCall.getAggregation(), columnLineage.getSources().size());
        }
    }

    private List<RelColumnOrigin> traceAggCallOrigins(AggregateCall aggCall, Aggregate aggregate, RelMetadataQuery mq) {
        List<RelColumnOrigin> allOrigins = new ArrayList<>();

        List<Integer> argList = aggCall.getArgList();
        if (argList.isEmpty() && aggCall.filterArg >= 0) {
            argList = Collections.singletonList(aggCall.filterArg);
        }

        for (Integer argIndex : argList) {
            if (argIndex >= 0) {
                Set<RelColumnOrigin> origins = mq.getColumnOrigins(aggregate, argIndex);
                if (origins != null && !origins.isEmpty()) {
                    allOrigins.addAll(origins);
                } else {
                    List<RelColumnOrigin> recursiveOrigins = traceThroughInputs(aggregate.getInput(0), argIndex, mq);
                    allOrigins.addAll(recursiveOrigins);
                }
            }
        }

        return allOrigins;
    }

     private List<RelColumnOrigin> traceThroughInputs(RelNode node, int columnIndex, RelMetadataQuery mq) {
         List<RelColumnOrigin> origins = new ArrayList<>();

         if (node instanceof Project) {
             Project project = (Project) node;
             List<? extends RexNode> projects = project.getProjects();
             if (columnIndex < projects.size()) {
                 RexNode rexNode = projects.get(columnIndex);
                 if (rexNode instanceof RexInputRef) {
                     int inputIndex = ((RexInputRef) rexNode).getIndex();
                     Set<RelColumnOrigin> originSet = mq.getColumnOrigins(project.getInput(), inputIndex);
                     if (originSet != null) {
                         origins.addAll(originSet);
                     }
                 } else if (rexNode instanceof RexCall) {
                     RexCall call = (RexCall) rexNode;
                     for (RexNode operand : call.getOperands()) {
                         if (operand instanceof RexInputRef) {
                             int inputIndex = ((RexInputRef) operand).getIndex();
                     Set<RelColumnOrigin> originSet = mq.getColumnOrigins(project.getInput(), inputIndex);
                             if (originSet != null) {
                                 origins.addAll(originSet);
                             }
                         }
                     }
                 }
             }
         } else if (node instanceof Aggregate) {
             Aggregate aggregate = (Aggregate) node;
             if (columnIndex < aggregate.getGroupSet().size()) {
                 Set<RelColumnOrigin> originSet = mq.getColumnOrigins(aggregate, columnIndex);
                 if (originSet != null) {
                     origins.addAll(originSet);
                 }
             }
         } else if (node instanceof Filter) {
             Filter filter = (Filter) node;
             Set<RelColumnOrigin> originSet = mq.getColumnOrigins(filter.getInput(), columnIndex);
             if (originSet != null) {
                 origins.addAll(originSet);
             }
         }

         if (origins.isEmpty() && !node.getInputs().isEmpty()) {
             origins.addAll(traceThroughInputs(node.getInput(0), columnIndex, mq));
         }

         return origins;
     }

    private void extractJoinLineage(Join join, RexMetricParseResult result) {
        logger.info("[RexNodeLineageExtractor] 提取 Join 血缘, 类型: {}, 左: {}, 右: {}",
            join.getJoinType(), join.getLeft().getClass().getSimpleName(), join.getRight().getClass().getSimpleName());

        RelMetadataQuery mq = join.getCluster().getMetadataQuery();
        int leftFieldCount = join.getLeft().getRowType().getFieldCount();
        int totalFieldCount = join.getRowType().getFieldCount();

        for (int i = 0; i < totalFieldCount; i++) {
            String outputName = join.getRowType().getFieldList().get(i).getName();
            ColumnLineage columnLineage = new ColumnLineage();
            columnLineage.setOutputName(outputName);
            columnLineage.setOutputIndex(i);
            columnLineage.setRexNodeType("JOIN");

            RelNode sourceNode;
            int sourceIndex;
            if (i < leftFieldCount) {
                sourceNode = join.getLeft();
                sourceIndex = i;
            } else {
                sourceNode = join.getRight();
                sourceIndex = i - leftFieldCount;
            }

            Set<RelColumnOrigin> origins = mq.getColumnOrigins(sourceNode, sourceIndex);
            if (origins != null) {
                for (RelColumnOrigin origin : origins) {
                    ColumnSource source = resolveColumnOrigin(origin, sourceNode, mq);
                    columnLineage.getSources().add(source);
                }
            }

            result.getColumnLineages().add(columnLineage);
        }
    }

    private void extractUnionLineage(Union union, RexMetricParseResult result) {
        logger.info("[RexNodeLineageExtractor] 提取 Union 血缘, 输入数: {}", union.getInputs().size());

        RelMetadataQuery mq = union.getCluster().getMetadataQuery();
        int fieldCount = union.getRowType().getFieldCount();

        for (int i = 0; i < fieldCount; i++) {
            String outputName = union.getRowType().getFieldList().get(i).getName();
            ColumnLineage columnLineage = new ColumnLineage();
            columnLineage.setOutputName(outputName);
            columnLineage.setOutputIndex(i);
            columnLineage.setRexNodeType("UNION");
            columnLineage.setTransformType("UNION_ALL");

            int inputIndex = 0;
            for (RelNode input : union.getInputs()) {
                Set<RelColumnOrigin> origins = mq.getColumnOrigins(input, i);
                if (origins != null) {
                    for (RelColumnOrigin origin : origins) {
                        ColumnSource source = resolveColumnOrigin(origin, input, mq);
                        source.addTransformation("UNION input#" + inputIndex);
                        columnLineage.getSources().add(source);
                    }
                }
                inputIndex++;
            }

            result.getColumnLineages().add(columnLineage);
        }
    }

    private void extractCorrelateLineage(Correlate correlate, RexMetricParseResult result) {
        logger.info("[RexNodeLineageExtractor] 提取 Correlate 血缘");

        RelMetadataQuery mq = correlate.getCluster().getMetadataQuery();
        int leftFieldCount = correlate.getLeft().getRowType().getFieldCount();
        int totalFieldCount = correlate.getRowType().getFieldCount();

        for (int i = 0; i < totalFieldCount; i++) {
            String outputName = correlate.getRowType().getFieldList().get(i).getName();
            ColumnLineage columnLineage = new ColumnLineage();
            columnLineage.setOutputName(outputName);
            columnLineage.setOutputIndex(i);
            columnLineage.setRexNodeType("CORRELATE");

            RelNode sourceNode = (i < leftFieldCount) ? correlate.getLeft() : correlate.getRight();
            int sourceIndex = (i < leftFieldCount) ? i : i - leftFieldCount;

            Set<RelColumnOrigin> origins = mq.getColumnOrigins(sourceNode, sourceIndex);
            if (origins != null) {
                for (RelColumnOrigin origin : origins) {
                    ColumnSource source = resolveColumnOrigin(origin, sourceNode, mq);
                    columnLineage.getSources().add(source);
                }
            }

            result.getColumnLineages().add(columnLineage);
        }
    }

    private void extractFilterLineage(Filter filter, RexMetricParseResult result) {
        logger.info("[RexNodeLineageExtractor] 提取 Filter 血缘");

        RelMetadataQuery mq = filter.getCluster().getMetadataQuery();
        RelNode input = filter.getInput();

        for (int i = 0; i < input.getRowType().getFieldCount(); i++) {
            Set<RelColumnOrigin> origins = mq.getColumnOrigins(input, i);
            if (origins != null && !origins.isEmpty()) {
                ColumnLineage columnLineage = new ColumnLineage();
                columnLineage.setOutputName(input.getRowType().getFieldList().get(i).getName());
                columnLineage.setOutputIndex(i);
                columnLineage.setRexNodeType("FILTER");

                for (RelColumnOrigin origin : origins) {
                    ColumnSource source = resolveColumnOrigin(origin, input, mq);
                    columnLineage.getSources().add(source);
                }

                result.getColumnLineages().add(columnLineage);
            }
        }
    }

    private ColumnSource resolveColumnOrigin(RelColumnOrigin origin, RelNode context, RelMetadataQuery mq) {
        ColumnSource source = new ColumnSource();

        String originTableName = origin.getOriginTable().getQualifiedName().toString();
        int ordinal = origin.getOriginColumnOrdinal();
        String originColumnName = origin.getOriginTable().getRowType().getFieldList().get(ordinal).getName();

        logger.debug("[RexNodeLineageExtractor] 原始来源: {}.{}", originTableName, originColumnName);

        String resolvedTableName = expandTableAlias(originTableName, context, mq);
        if (resolvedTableName != null && !resolvedTableName.equals(originTableName)) {
            logger.debug("[RexNodeLineageExtractor] 别名展开: {} -> {}", originTableName, resolvedTableName);
            originTableName = resolvedTableName;
        }

        source.setSourceTable(originTableName);
        source.setSourceColumn(originColumnName);
        source.setColumnOrdinal(ordinal);

        return source;
    }

    private String expandTableAlias(String tableName, RelNode context, RelMetadataQuery mq) {
        if (tableAliasMap.containsKey(tableName)) {
            RelNode aliasedNode = tableAliasMap.get(tableName);
            return findRealTableName(aliasedNode);
        }

        if (isRealTable(tableName)) {
            return tableName;
        }

        return findTableInContext(tableName, context, mq);
    }

    private String findTableInContext(String tableName, RelNode context, RelMetadataQuery mq) {
        if (context == null) {
            return tableName;
        }

        if (context instanceof TableScan) {
            TableScan scan = (TableScan) context;
            String scanTableName = scan.getTable().getQualifiedName().toString();
            if (tableName.equalsIgnoreCase(scanTableName)) {
                return scanTableName;
            }
        }

        for (RelNode input : context.getInputs()) {
            String result = findTableInContext(tableName, input, mq);
            if (result != null && !result.equals(tableName)) {
                return result;
            }
        }

        return tableName;
    }

    private String findRealTableName(RelNode node) {
        if (node instanceof TableScan) {
            return ((TableScan) node).getTable().getQualifiedName().toString();
        }

        for (RelNode input : node.getInputs()) {
            String result = findRealTableName(input);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private boolean isRealTable(String tableName) {
        if (tableName.toLowerCase().startsWith("subquery_")) {
            return false;
        }
        return tableName.matches("^[A-Z][A-Z0-9_]*$");
    }

    private ColumnLineage extractRexNodeLineage(RexNode rexNode, RelNode input, RelMetadataQuery mq) {
        ColumnLineage columnLineage = new ColumnLineage();
        columnLineage.setRexNode(rexNode);
        columnLineage.setRexNodeType(rexNode.getClass().getSimpleName());

        RexSourceCollector visitor = new RexSourceCollector(input, mq, this);
        List<ColumnSource> sources = visitor.collectSources(rexNode);

        for (ColumnSource source : sources) {
            if (source.getTransformations() == null) {
                source.setTransformations(new ArrayList<>());
            }
            source.getTransformations().add(0, rexNode.toString());
        }

        columnLineage.setSources(sources);

        RexShuttle shuttle = new RexShuttle() {
            @Override
            public RexNode visitCall(RexCall call) {
                String opName = call.getOperator().getName().toUpperCase();
                if ("IFNULL".equals(opName)) {
                    columnLineage.setTransformType("IFNULL");
                 } else if (call.getOperator() instanceof org.apache.calcite.sql.fun.SqlCaseOperator) {
                     columnLineage.setTransformType("CASE");
                 } else if (call.getOperator() instanceof org.apache.calcite.sql.fun.SqlSumAggFunction ||
                            call.getOperator() instanceof org.apache.calcite.sql.fun.SqlCountAggFunction ||
                            call.getOperator() instanceof org.apache.calcite.sql.fun.SqlAvgAggFunction) {
                     columnLineage.setTransformType("AGGREGATION");
                 } else if ("COALESCE".equals(opName)) {
                     columnLineage.setTransformType("COALESCE");
                 }
                 return super.visitCall(call);
             }
         };
         shuttle.apply(rexNode);

         return columnLineage;
    }

    private RelNode convertToRelNode(SqlSelect sqlSelect, String sql) {
        try {
            CalciteSchema schema = CalciteSchema.createRootSchema(true);

            SqlToRelConverter.Config config = SqlToRelConverter.config()
                .withTrimUnusedFields(false);

            SqlToRelConverter converter = new SqlToRelConverter(
                null, // ViewExpander
                null, // SqlValidator
                null, // CatalogReader
                null, // RelOptCluster
                null, // SqlRexConvertletTable
                config
            );

            RelRoot root = converter.convertQuery(sqlSelect, false, true);
            RelNode relNode = root.rel;
            HepProgramBuilder hepBuilder = new HepProgramBuilder();
            hepBuilder.addRuleInstance(CoreRules.PROJECT_REMOVE);
            HepPlanner hepPlanner = new HepPlanner(hepBuilder.build());
            hepPlanner.setRoot(relNode);
            relNode = hepPlanner.findBestExp();

            logger.info("[RexNodeLineageExtractor] RelNode类型: {}", relNode.getClass().getSimpleName());
            return relNode;

        } catch (Exception e) {
            logger.error("[RexNodeLineageExtractor] RelNode转换失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public String generateMetricSql(ColumnLineage lineage, String tableName) {
        if (lineage.getSources().isEmpty()) {
            return null;
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT\n    ");

        if (lineage.getAggregationFunction() != null) {
            sql.append(lineage.getAggregationFunction()).append("(");
            ColumnSource primarySource = lineage.getSources().get(0);
            sql.append(primarySource.getSourceTable()).append(".").append(primarySource.getSourceColumn());
            sql.append(") AS ").append(lineage.getOutputName());
        } else {
            sql.append(lineage.getRexNode() != null ? lineage.getRexNode().toString() : lineage.getOutputName())
               .append(" AS ").append(lineage.getOutputName());
        }

        sql.append("\nFROM ").append(tableName);

        if (lineage.getFilterCondition() != null) {
            sql.append("\nWHERE 1=1");
        }

        return sql.toString();
    }

    public static class RexMetricParseResult {
        private String originalSql;
        private SqlSelect sqlSelect;
        private RelNode relNode;
        private List<ColumnLineage> columnLineages = new ArrayList<>();
        private String error;

        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
        public SqlSelect getSqlSelect() { return sqlSelect; }
        public void setSqlSelect(SqlSelect sqlSelect) { this.sqlSelect = sqlSelect; }
        public RelNode getRelNode() { return relNode; }
        public void setRelNode(RelNode relNode) { this.relNode = relNode; }
        public List<ColumnLineage> getColumnLineages() { return columnLineages; }
        public void setColumnLineages(List<ColumnLineage> columnLineages) { this.columnLineages = columnLineages; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    public static class ColumnLineage {
        private String outputName;
        private int outputIndex;
        private String rexNodeType;
        private RexNode rexNode;
        private String transformType;
        private String aggregationFunction;
        private boolean distinct;
        private Map<String, Object> filterCondition;
        private List<ColumnSource> sources = new ArrayList<>();

        public String getOutputName() { return outputName; }
        public void setOutputName(String outputName) { this.outputName = outputName; }
        public int getOutputIndex() { return outputIndex; }
        public void setOutputIndex(int outputIndex) { this.outputIndex = outputIndex; }
        public String getRexNodeType() { return rexNodeType; }
        public void setRexNodeType(String rexNodeType) { this.rexNodeType = rexNodeType; }
        public RexNode getRexNode() { return rexNode; }
        public void setRexNode(RexNode rexNode) { this.rexNode = rexNode; }
        public String getTransformType() { return transformType; }
        public void setTransformType(String transformType) { this.transformType = transformType; }
        public String getAggregationFunction() { return aggregationFunction; }
        public void setAggregationFunction(String aggregationFunction) { this.aggregationFunction = aggregationFunction; }
        public boolean isDistinct() { return distinct; }
        public void setDistinct(boolean distinct) { this.distinct = distinct; }
        public Map<String, Object> getFilterCondition() { return filterCondition; }
        public void setFilterCondition(Map<String, Object> filterCondition) { this.filterCondition = filterCondition; }
        public List<ColumnSource> getSources() { return sources; }
        public void setSources(List<ColumnSource> sources) { this.sources = sources; }
    }

    public static class ColumnSource {
        private String sourceTable;
        private String sourceColumn;
        private int columnOrdinal;
        private List<String> transformations = new ArrayList<>();

        public String getSourceTable() { return sourceTable; }
        public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
        public String getSourceColumn() { return sourceColumn; }
        public void setSourceColumn(String sourceColumn) { this.sourceColumn = sourceColumn; }
        public int getColumnOrdinal() { return columnOrdinal; }
        public void setColumnOrdinal(int columnOrdinal) { this.columnOrdinal = columnOrdinal; }
        public List<String> getTransformations() { return transformations; }
        public void setTransformations(List<String> transformations) { this.transformations = transformations; }

        public void addTransformation(String transform) {
            if (this.transformations == null) {
                this.transformations = new ArrayList<>();
            }
            this.transformations.add(transform);
        }

        public String getFullLineage() {
            if (transformations == null || transformations.isEmpty()) {
                return (sourceTable != null ? sourceTable : "?") + "." + (sourceColumn != null ? sourceColumn : "?");
            }
            StringBuilder sb = new StringBuilder();
            sb.append(sourceTable).append(".").append(sourceColumn);
            for (int i = transformations.size() - 1; i >= 0; i--) {
                sb.append(" -> ").append(transformations.get(i));
            }
            return sb.toString();
        }
    }

     private static class RexSourceCollector extends RexShuttle {
         private final RelNode input;
         private final RelMetadataQuery mq;
         private final RexNodeLineageExtractor extractor;
         private final List<ColumnSource> sources = new ArrayList<>();

         RexSourceCollector(RelNode input, RelMetadataQuery mq, RexNodeLineageExtractor extractor) {
             this.input = input;
             this.mq = mq;
             this.extractor = extractor;
         }

         @Override
         public RexNode visitInputRef(RexInputRef inputRef) {
             int index = inputRef.getIndex();

             Set<RelColumnOrigin> origins = mq.getColumnOrigins(input, index);
             if (origins != null) {
                 for (RelColumnOrigin origin : origins) {
                     ColumnSource source = extractor.resolveColumnOrigin(origin, input, mq);
                     sources.add(source);
                 }
             }

             return inputRef;
         }

         @Override
         public RexNode visitCall(RexCall call) {
             for (RexNode operand : call.getOperands()) {
                 operand.accept(this);
             }
             return call;
         }

         List<ColumnSource> collectSources(RexNode rexNode) {
             sources.clear();
             rexNode.accept(this);
             return new ArrayList<>(sources);
         }
     }
}
