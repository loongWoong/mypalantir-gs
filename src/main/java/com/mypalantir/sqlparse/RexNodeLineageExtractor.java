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

        // Handle different root node types
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
            // For unhandled types, try to extract from inputs recursively
            for (RelNode input : relNode.getInputs()) {
                extractAllLineages(input, result);
            }
            return;
        }

        // Always recursively process inputs to handle nested structures
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

            // 解析聚合调用中的表达式，特别是 CASE WHEN 情况
            parseAggregateCallExpression(aggCall, aggregate, columnLineage, mq);

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

    /**
     * 解析聚合调用中的表达式，特别是处理 SUM(CASE WHEN ...) 这样的情况
     */
    private void parseAggregateCallExpression(AggregateCall aggCall, Aggregate aggregate,
                                             ColumnLineage columnLineage, RelMetadataQuery mq) {
        List<Integer> argList = aggCall.getArgList();
        if (argList.isEmpty()) {
            return;
        }

        // 获取聚合函数的参数表达式
        RelNode input = aggregate.getInput(0);
        if (input instanceof Project) {
            Project project = (Project) input;
            List<? extends RexNode> projects = project.getProjects();

            for (Integer argIndex : argList) {
                if (argIndex >= 0 && argIndex < projects.size()) {
                    RexNode rexNode = projects.get(argIndex);

                    // 如果参数是 CASE 表达式，解析其中的条件和字段
                    if (rexNode instanceof RexCall) {
                        RexCall call = (RexCall) rexNode;
                        if (call.getOperator() instanceof org.apache.calcite.sql.fun.SqlCaseOperator) {
                            parseCaseInAggregate(call, columnLineage, project, mq);
                        }
                    }
                }
            }
        }
    }

    /**
     * 解析聚合中的 CASE 表达式，如 SUM(CASE WHEN PAYTYPE = 6 THEN AMOUNT END)
     */
    private void parseCaseInAggregate(RexCall caseCall, ColumnLineage columnLineage,
                                     Project project, RelMetadataQuery mq) {
        List<RexNode> operands = caseCall.getOperands();
        if (operands.size() < 3) return;

        Map<String, Object> filterConditions = new HashMap<>();
        List<Map<String, Object>> caseBranches = new ArrayList<>();

        // 解析 CASE WHEN 条件和结果
        for (int i = 0; i < operands.size() - 1; i += 2) {
            if (i + 1 < operands.size()) {
                RexNode condition = operands.get(i);
                RexNode result = operands.get(i + 1);

                Map<String, Object> branch = new HashMap<>();
                branch.put("condition", condition.toString());
                branch.put("result", result.toString());
                caseBranches.add(branch);

                // 提取 PAYTYPE 条件
                if (condition.toString().contains("PAYTYPE")) {
                    String conditionStr = condition.toString();
                    if (conditionStr.contains("=")) {
                        String[] parts = conditionStr.split("=");
                        if (parts.length == 2) {
                            String value = parts[1].trim();
                            try {
                                int payTypeValue = Integer.parseInt(value);
                                filterConditions.put("paytype_" + payTypeValue, true);
                                filterConditions.put("paytype_condition", "PAYTYPE = " + payTypeValue);
                            } catch (NumberFormatException e) {
                                filterConditions.put("paytype_condition", conditionStr);
                            }
                        }
                    }
                }

                // 从结果中提取聚合字段
                if (result instanceof RexInputRef) {
                    int resultIndex = ((RexInputRef) result).getIndex();
                    Set<RelColumnOrigin> origins = mq.getColumnOrigins(project.getInput(), resultIndex);
                    if (origins != null && !origins.isEmpty()) {
                        RelColumnOrigin origin = origins.iterator().next();
                        String fieldName = origin.getOriginTable().getRowType()
                            .getFieldList().get(origin.getOriginColumnOrdinal()).getName();
                        columnLineage.setAggregationField(fieldName);
                        logger.info("[parseCaseInAggregate] 从 CASE 结果提取聚合字段: {}", fieldName);
                    }
                }
            }
        }

        // 处理 ELSE 子句
        if (operands.size() % 2 == 1) {
            RexNode elseResult = operands.get(operands.size() - 1);
            filterConditions.put("_else_result", elseResult.toString());
        }

        filterConditions.put("case_branches", caseBranches);
        filterConditions.put("_case_type", "conditional_aggregation");

        columnLineage.setFilterCondition(filterConditions);
        columnLineage.setTransformType("AGGREGATION_WITH_CASE");
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

        // Extract join conditions as dimensions
        RexNode condition = join.getCondition();
        if (condition != null) {
            Map<String, Object> joinConditions = extractJoinConditions(condition);
            result.getJoinConditions().putAll(joinConditions);
            logger.info("[RexNodeLineageExtractor] Join 条件: {}", joinConditions);
        }

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

    /**
     * Extract join conditions for dimensional analysis
     */
    private Map<String, Object> extractJoinConditions(RexNode condition) {
        Map<String, Object> conditions = new HashMap<>();

        if (condition instanceof RexCall) {
            RexCall call = (RexCall) condition;
            String opName = call.getOperator().getName().toUpperCase();

            if ("AND".equals(opName) || "OR".equals(opName)) {
                // Multiple conditions
                List<Map<String, Object>> subConditions = new ArrayList<>();
                for (RexNode operand : call.getOperands()) {
                    if (operand instanceof RexCall) {
                        Map<String, Object> subCond = extractJoinConditions(operand);
                        if (!subCond.isEmpty()) {
                            subConditions.add(subCond);
                        }
                    }
                }
                conditions.put("operator", opName);
                conditions.put("conditions", subConditions);
            } else if ("=".equals(opName) && call.getOperands().size() == 2) {
                // Equality join condition
                RexNode left = call.getOperands().get(0);
                RexNode right = call.getOperands().get(1);

                conditions.put("type", "equality");
                conditions.put("left", left.toString());
                conditions.put("right", right.toString());
                conditions.put("operator", "=");

                // Extract field names for dimensional analysis
                String leftField = extractFieldNameFromRexNode(left);
                String rightField = extractFieldNameFromRexNode(right);
                if (leftField != null && rightField != null) {
                    conditions.put("left_field", leftField);
                    conditions.put("right_field", rightField);
                }
            }
        }

        return conditions;
    }

    private String extractFieldNameFromRexNode(RexNode node) {
        if (node instanceof RexInputRef) {
            // This would need more context to resolve to actual field names
            return "field_" + ((RexInputRef) node).getIndex();
        } else if (node instanceof RexCall) {
            RexCall call = (RexCall) node;
            if (call.getOperands().size() == 1 && call.getOperands().get(0) instanceof RexInputRef) {
                return "field_" + ((RexInputRef) call.getOperands().get(0)).getIndex();
            }
        }
        return node.toString();
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
        // First check if it's in our alias map
        if (tableAliasMap.containsKey(tableName)) {
            RelNode aliasedNode = tableAliasMap.get(tableName);
            String realTable = findRealTableName(aliasedNode);
            if (realTable != null) {
                logger.debug("[expandTableAlias] 别名展开: {} -> {} (from alias map)", tableName, realTable);
                return realTable;
            }
        }

        // Check if it's already a real table
        if (isRealTable(tableName)) {
            return tableName;
        }

        // Try to resolve through the RelNode tree
        String resolved = findTableInContext(tableName, context, mq);
        if (resolved != null && !resolved.equals(tableName)) {
            logger.debug("[expandTableAlias] 上下文解析: {} -> {}", tableName, resolved);
            return resolved;
        }

        // For subquery aliases like 'A', 'B', try to resolve through parent context
        if (context != null && Character.isLetter(tableName.charAt(0))) {
            String contextualResult = resolveAliasThroughRelTree(tableName, context);
            if (contextualResult != null) {
                logger.debug("[expandTableAlias] 关系树解析: {} -> {}", tableName, contextualResult);
                return contextualResult;
            }
        }

        logger.debug("[expandTableAlias] 无法解析别名: {}", tableName);
        return tableName;
    }

    /**
     * Resolve alias by traversing the RelNode tree to find subqueries
     */
    private String resolveAliasThroughRelTree(String alias, RelNode root) {
        // Look for Project nodes that might represent subqueries with aliases
        return findAliasInRelTree(alias, root, new HashSet<>());
    }

    private String findAliasInRelTree(String targetAlias, RelNode node, Set<RelNode> visited) {
        if (visited.contains(node)) {
            return null;
        }
        visited.add(node);

        // Check if this node has the alias we're looking for
        if (node instanceof Project) {
            Project project = (Project) node;
            // The alias might be defined at a higher level in the tree
            // For now, we'll look at the inputs
        }

        // Check inputs recursively
        for (RelNode input : node.getInputs()) {
            if (input instanceof Project || input instanceof Aggregate) {
                // This might be a subquery that defines our alias
                String result = findAliasInRelTree(targetAlias, input, visited);
                if (result != null) {
                    return result;
                }
            } else if (input instanceof TableScan) {
                TableScan scan = (TableScan) input;
                String tableName = scan.getTable().getQualifiedName().toString();
                // If we find a table scan, it might be what the alias refers to
                // This is a heuristic - in complex queries, we need more context
                logger.debug("[findAliasInRelTree] Found table scan: {}", tableName);
            }
        }

        return null;
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
                    parseIfnullExpression(call, columnLineage);
                 } else if (call.getOperator() instanceof org.apache.calcite.sql.fun.SqlCaseOperator) {
                     columnLineage.setTransformType("CASE");
                     parseCaseExpression(call, columnLineage);
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

     /**
      * Parse IFNULL expression to extract null handling information
      */
     private void parseIfnullExpression(RexCall call, ColumnLineage columnLineage) {
         if (call.getOperands().size() >= 2) {
             Map<String, Object> filterConditions = new HashMap<>();
             filterConditions.put("_is_null_filled", true);
             filterConditions.put("_null_default_value", call.getOperands().get(1).toString());
             filterConditions.put("_inner_expression", call.getOperands().get(0).toString());
             columnLineage.setFilterCondition(filterConditions);
         }
     }

     /**
      * Parse CASE WHEN expression to extract filter conditions
      */
     private void parseCaseExpression(RexCall call, ColumnLineage columnLineage) {
         List<RexNode> operands = call.getOperands();
         if (operands.size() < 3) return; // CASE must have at least WHEN, THEN, ELSE

         Map<String, Object> filterConditions = new HashMap<>();
         List<Map<String, Object>> caseBranches = new ArrayList<>();

         // CASE WHEN condition1 THEN result1 WHEN condition2 THEN result2 ... ELSE default END
         // Operands: [condition1, result1, condition2, result2, ..., default]

         for (int i = 0; i < operands.size() - 1; i += 2) {
             if (i + 1 < operands.size()) {
                 RexNode condition = operands.get(i);
                 RexNode result = operands.get(i + 1);

                 Map<String, Object> branch = new HashMap<>();
                 branch.put("condition", condition.toString());
                 branch.put("result", result.toString());
                 caseBranches.add(branch);

                 // For the specific case of PAYTYPE = value, extract as filter condition
                 if (condition.toString().contains("PAYTYPE")) {
                     String conditionStr = condition.toString();
                     if (conditionStr.contains("=")) {
                         String[] parts = conditionStr.split("=");
                         if (parts.length == 2) {
                             String value = parts[1].trim();
                             try {
                                 int payTypeValue = Integer.parseInt(value);
                                 filterConditions.put("paytype_" + payTypeValue, true);
                                 filterConditions.put("paytype_condition", "PAYTYPE = " + payTypeValue);
                             } catch (NumberFormatException e) {
                                 filterConditions.put("paytype_condition", conditionStr);
                             }
                         }
                     }
                 }
             }
         }

         // Handle ELSE clause if present
         if (operands.size() % 2 == 1) {
             RexNode elseResult = operands.get(operands.size() - 1);
             filterConditions.put("_else_result", elseResult.toString());
         }

         filterConditions.put("case_branches", caseBranches);
         filterConditions.put("_case_type", "conditional_branch");

         columnLineage.setFilterCondition(filterConditions);
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
        private Map<String, Object> joinConditions = new HashMap<>();

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
        public Map<String, Object> getJoinConditions() { return joinConditions; }
        public void setJoinConditions(Map<String, Object> joinConditions) { this.joinConditions = joinConditions; }
    }

    public static class ColumnLineage {
        private String outputName;
        private int outputIndex;
        private String rexNodeType;
        private RexNode rexNode;
        private String transformType;
        private String aggregationFunction;
        private String aggregationField;
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
        public String getAggregationField() { return aggregationField; }
        public void setAggregationField(String aggregationField) { this.aggregationField = aggregationField; }
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
