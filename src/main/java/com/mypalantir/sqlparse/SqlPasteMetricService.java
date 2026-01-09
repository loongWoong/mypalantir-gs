package com.mypalantir.sqlparse;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.metric.AtomicMetric;
import com.mypalantir.metric.MetricDefinition;
import com.mypalantir.service.*;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQL 粘贴指标提取主服务
 * 协调 SQL 解析、映射对齐、LLM 处理、验证和存储流程
 */
@Service
public class SqlPasteMetricService {

    private static final Logger logger = LoggerFactory.getLogger(SqlPasteMetricService.class);

    private final CalciteSqlParser sqlParser;
    private final MappingResolver mappingResolver;
    private final LLMAlignment llmAlignment;
    private final MetricValidator metricValidator;
    private final AtomicMetricService atomicMetricService;
    private final MetricService metricService;
    private final DatabaseMetadataService databaseMetadataService;
    private final LLMService llmService;
    private final Loader loader;
    private final MappingService mappingService;
    
    // 新增：复杂SQL解析组件
    private final ComplexSqlStructureAnalyzer complexSqlAnalyzer;
    private final ReportMetricExtractor reportMetricExtractor;
    private final MultiDimensionAggregationHandler multiDimensionHandler;
    private final SqlLayerIntegrator sqlLayerIntegrator;
    // 新增：LEFT JOIN 报表处理器
    private final ReportJoinMetricHandler reportJoinMetricHandler;
    // 新增：RexNode 血缘解析组件
    private final RexNodeLineageExtractor rexNodeLineageExtractor;
    private final RexMetricParser rexMetricParser;

    public SqlPasteMetricService(
            CalciteSqlParser sqlParser,
            MappingResolver mappingResolver,
            LLMAlignment llmAlignment,
            MetricValidator metricValidator,
            AtomicMetricService atomicMetricService,
            MetricService metricService,
            DatabaseMetadataService databaseMetadataService,
            LLMService llmService,
            Loader loader,
            MappingService mappingService,
            ComplexSqlStructureAnalyzer complexSqlAnalyzer,
            ReportMetricExtractor reportMetricExtractor,
            MultiDimensionAggregationHandler multiDimensionHandler,
            SqlLayerIntegrator sqlLayerIntegrator,
            ReportJoinMetricHandler reportJoinMetricHandler,
            RexNodeLineageExtractor rexNodeLineageExtractor,
            RexMetricParser rexMetricParser) {
        this.sqlParser = sqlParser;
        this.mappingResolver = mappingResolver;
        this.llmAlignment = llmAlignment;
        this.metricValidator = metricValidator;
        this.atomicMetricService = atomicMetricService;
        this.metricService = metricService;
        this.databaseMetadataService = databaseMetadataService;
        this.llmService = llmService;
        this.loader = loader;
        this.mappingService = mappingService;
        this.complexSqlAnalyzer = complexSqlAnalyzer;
        this.reportMetricExtractor = reportMetricExtractor;
        this.multiDimensionHandler = multiDimensionHandler;
        this.sqlLayerIntegrator = sqlLayerIntegrator;
        this.reportJoinMetricHandler = reportJoinMetricHandler;
        this.rexNodeLineageExtractor = rexNodeLineageExtractor;
        this.rexMetricParser = rexMetricParser;
    }

    /**
     * 解析 SQL 并提取指标
     */
    public SqlPasteParseResult parseAndExtract(String sql, SqlPasteOptions options) {
        logger.info("开始解析 SQL: {}", sql.substring(0, Math.min(100, sql.length())));

        SqlPasteParseResult result = new SqlPasteParseResult();
        result.setOriginalSql(sql);

        try {
            // 步骤 1: SQL 语法解析
            logger.info("步骤 1: SQL 语法解析");
            CalciteSqlParseResult parseResult = sqlParser.parse(sql);
            result.setSqlAnalysis(parseResult);
            logger.info("解析完成，涉及 {} 个表, {} 个聚合字段", 
                parseResult.getTables().size(), 
                parseResult.getAggregations().size());

            // 步骤 2: 获取相关对象类型
            logger.info("步骤 2: 获取相关对象类型");
            List<ObjectType> relevantObjectTypes = getRelevantObjectTypes(parseResult);
            logger.info("找到 {} 个相关对象类型", relevantObjectTypes.size());

            // 步骤 3: 映射关系对齐
            logger.info("步骤 3: 映射关系对齐");
            MappingResolver.MappingAlignmentResult alignmentResult = mappingResolver.alignWithMappings(parseResult);
            result.setMappingResult(alignmentResult);
            logger.info("映射完成，已映射 {} 个字段, 未映射 {} 个字段",
                alignmentResult.getFieldMappings().size(),
                alignmentResult.getUnmappedFields().size());

            // 步骤 4: LLM 语义增强
            LLMAlignment.SemanticAlignmentResult semanticResult = null;
            if (options.isEnableLLM()) {
                logger.info("步骤 4: LLM 语义增强");
                try {
                    semanticResult = llmAlignment.enhanceSemantic(parseResult, alignmentResult, relevantObjectTypes);
                    result.setSemanticResult(semanticResult);
                    logger.info("LLM 分析完成，找到 {} 个语义指标", semanticResult.getMetrics().size());
                } catch (LLMAlignment.LLMException e) {
                    logger.warn("LLM 调用失败: {}", e.getMessage());
                    result.addSuggestion("LLM 语义分析失败，将使用基础分析结果");
                    semanticResult = createDefaultSemanticResult(parseResult);
                    result.setSemanticResult(semanticResult);
                }
            } else {
                logger.info("跳过 LLM 语义增强（已禁用）");
                semanticResult = createDefaultSemanticResult(parseResult);
                result.setSemanticResult(semanticResult);
            }

            // 步骤 5: 提取指标（增强：检测复杂SQL结构）
            logger.info("步骤 5: 提取指标");
            
            List<ExtractedMetric> extractedMetrics;
            
            // 5.1: 优先使用 RexNode 血缘解析（最精确）
            logger.info("步骤 5.1: 使用 RexNode 血缘解析");
            extractedMetrics = extractMetricsByRex(sql, parseResult, alignmentResult);
            
            if (!extractedMetrics.isEmpty()) {
                logger.info("RexNode 解析成功，提取到 {} 个指标", extractedMetrics.size());
            } else {
                // 5.2: 回退到 LEFT JOIN 报表解析
                logger.info("步骤 5.2: RexNode 解析无结果，回退到 LEFT JOIN 报表解析");
                if (reportJoinMetricHandler.isJoinReportStructure(sql)) {
                    logger.info("使用 LEFT JOIN 报表提取策略");
                    extractedMetrics = extractMetricsForJoinReport(sql, parseResult, alignmentResult);
                } else {
                    // 5.3: 继续回退到复杂报表解析
                    ComplexSqlStructureAnalyzer.SqlStructureType structureType = 
                        complexSqlAnalyzer.analyzeStructure(sql);
                    logger.info("SQL结构类型: {}", structureType);
                    
                    if (structureType == ComplexSqlStructureAnalyzer.SqlStructureType.MULTI_LAYER_REPORT) {
                        logger.info("使用多层报表SQL提取策略");
                        extractedMetrics = extractMetricsForComplexReport(sql, parseResult, alignmentResult);
                    } else {
                        logger.info("使用标准提取策略");
                        extractedMetrics = extractMetrics(parseResult, alignmentResult, semanticResult);
                    }
                }
            }
            
            result.setExtractedMetrics(extractedMetrics);
            logger.info("提取完成，找到 {} 个指标", extractedMetrics.size());

            // 步骤 6: 验证指标
            logger.info("步骤 6: 验证指标");
            List<MetricValidator.ValidationResult> validationResults = validateMetrics(extractedMetrics);
            result.setValidations(validationResults);
            
            int errorCount = (int) validationResults.stream()
                .filter(v -> v.hasErrors())
                .count();
            logger.info("验证完成，{} 个指标有错误", errorCount);

            // 步骤 7: 生成建议
            logger.info("步骤 7: 生成建议");
            List<String> suggestions = generateSuggestions(extractedMetrics, validationResults);
            result.setSuggestions(suggestions);

            logger.info("SQL 解析完成");

        } catch (Exception e) {
            logger.error("SQL 解析失败: {}", e.getMessage(), e);
            result.addError("PARSE_ERROR", "SQL 解析失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 存储提取的指标
     */
    public SaveResult saveExtractedMetrics(
            List<ExtractedMetric> metrics,
            boolean createNew,
            List<String> existingMetricIds,
            List<String> workspaceIds) {
        
        logger.info("[saveExtractedMetrics] 开始保存指标，数量: {}", metrics.size());
        
        SaveResult result = new SaveResult();
        result.setSuccess(true);

        List<String> savedIds = new ArrayList<>();
        List<SaveError> errors = new ArrayList<>();

        for (int i = 0; i < metrics.size(); i++) {
            ExtractedMetric metric = metrics.get(i);
            logger.info("[saveExtractedMetrics] 处理指标 [{}]: name={}, category={}, businessProcess={}, aggregationFunction={}, aggregationField={}, description={}",
                i, metric.getName(), metric.getCategory(), metric.getBusinessProcess(), 
                metric.getAggregationFunction(), metric.getAggregationField(), metric.getDescription());
            
            try {
                // 保存前验证必填字段
                if (metric.getCategory() == ExtractedMetric.MetricCategory.ATOMIC) {
                    if (metric.getBusinessProcess() == null || metric.getBusinessProcess().isEmpty()) {
                        logger.error("[saveExtractedMetrics] 指标 {} 缺少 businessProcess", metric.getName());
                        throw new IllegalArgumentException("原子指标缺少必填字段: businessProcess");
                    }
                    if (metric.getAggregationFunction() == null || metric.getAggregationFunction().isEmpty()) {
                        logger.error("[saveExtractedMetrics] 指标 {} 缺少 aggregationFunction", metric.getName());
                        throw new IllegalArgumentException("原子指标缺少必填字段: aggregationFunction");
                    }
                }
                
                String savedId;

                if (metric.getCategory() == ExtractedMetric.MetricCategory.ATOMIC) {
                    savedId = saveAtomicMetric(metric, createNew, existingMetricIds, workspaceIds);
                } else {
                    savedId = saveMetricDefinition(metric, createNew, existingMetricIds, workspaceIds);
                }

                savedIds.add(savedId);
                result.getSavedMetrics().add(new SavedMetricInfo(metric.getName(), savedId, "success"));

            } catch (Exception e) {
                logger.error("保存指标失败: {}", metric.getName(), e);
                errors.add(new SaveError(metric.getName(), e.getMessage()));
                result.setSuccess(false);
            }
        }

        result.setSavedIds(savedIds);
        result.setErrors(errors);

        return result;
    }

    /**
     * 仅验证指标定义
     */
    public ValidationOnlyResult validateOnly(String sql) {
        SqlPasteParseResult parseResult = parseAndExtract(sql, new SqlPasteOptions());

        ValidationOnlyResult result = new ValidationOnlyResult();
        result.setOriginalSql(sql);
        result.setExtractedMetrics(parseResult.getExtractedMetrics());
        result.setValidations(parseResult.getValidations());
        result.setSuggestions(parseResult.getSuggestions());

        boolean allValid = parseResult.getValidations().stream()
            .allMatch(v -> !v.hasErrors());
        result.setAllValid(allValid);

        return result;
    }

    /**
     * 获取相关对象类型
     */
    private List<ObjectType> getRelevantObjectTypes(CalciteSqlParseResult parseResult) {
        Set<String> objectTypeNames = new HashSet<>();
        
        try {
            List<ObjectType> allTypes = loader.listObjectTypes();
            List<ObjectType> relevantTypes = new ArrayList<>();
            
            // 只返回有映射配置的对象类型
            for (ObjectType objectType : allTypes) {
                try {
                    // 检查该对象类型是否有映射配置
                    List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectType.getName());
                    if (mappings != null && !mappings.isEmpty()) {
                        relevantTypes.add(objectType);
                    }
                } catch (Exception e) {
                    // 如果获取映射失败，跳过该对象类型
                    logger.debug("获取对象类型 '{}' 的映射失败: {}", objectType.getName(), e.getMessage());
                }
            }
            
            return relevantTypes;
        } catch (Exception e) {
            logger.warn("获取对象类型列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 创建默认的语义分析结果（当 LLM 不可用时）
     */
    private LLMAlignment.SemanticAlignmentResult createDefaultSemanticResult(CalciteSqlParseResult parseResult) {
        LLMAlignment.SemanticAlignmentResult result = new LLMAlignment.SemanticAlignmentResult();

        // 从聚合字段创建默认指标
        for (CalciteSqlParseResult.AggregationInfo agg : parseResult.getAggregations()) {
            LLMAlignment.SemanticMetric metric = new LLMAlignment.SemanticMetric();
            metric.setSqlField(agg.getExpression());
            metric.setBusinessMeaning(agg.getField());
            metric.setRecommendedName(agg.getAlias() != null ? agg.getAlias() : agg.getField());
            metric.setAggregationType(agg.getType());
            metric.setSuggestedMetricType("ATOMIC");
            metric.setConfidence(0.7);
            result.getMetrics().add(metric);
        }

        // 从 GROUP BY 字段创建默认维度
        for (String groupByField : parseResult.getGroupByFields()) {
            LLMAlignment.SemanticDimension dimension = new LLMAlignment.SemanticDimension();
            dimension.setSqlField(groupByField);
            dimension.setBusinessMeaning(groupByField);
            dimension.setTimeDimension(false);
            dimension.setEnumDimension(false);
            result.getDimensions().add(dimension);
        }

        return result;
    }

    /**
     * 提取指标（支持原子→派生→复合三层结构）
     */
    private List<ExtractedMetric> extractMetrics(
            CalciteSqlParseResult parseResult,
            MappingResolver.MappingAlignmentResult alignmentResult,
            LLMAlignment.SemanticAlignmentResult semanticResult) {
        
        logger.info("[extractMetrics] 开始提取指标");
        logger.info("[extractMetrics] semanticResult不为空: {}", semanticResult != null);
        if (semanticResult != null) {
            logger.info("[extractMetrics] semanticResult中的指标数量: {}", semanticResult.getMetrics().size());
        }
        logger.info("[extractMetrics] parseResult聚合字段数量: {}", parseResult.getAggregations().size());
        
        List<ExtractedMetric> metrics = new ArrayList<>();
        Set<String> addedMetricNames = new HashSet<>();

        // 检测是否为子查询结构（复合指标场景）
        boolean hasSubQuery = hasSubQueryStructure(parseResult);
        logger.info("[extractMetrics] 是否为子查询结构: {}", hasSubQuery);

        if (hasSubQuery) {
            logger.info("[extractMetrics] 使用路径: 从子查询结构提取分层指标");
            metrics = extractFromSubQuery(parseResult, alignmentResult, addedMetricNames);
        } else {
            logger.info("[extractMetrics] 使用路径: 从语义分析结果提取指标");
            List<LLMAlignment.SemanticMetric> semanticMetrics = semanticResult != null ? semanticResult.getMetrics() : Collections.<LLMAlignment.SemanticMetric>emptyList();
            for (LLMAlignment.SemanticMetric semanticMetric : semanticMetrics) {
                logger.info("[extractMetrics] 处理语义指标: sqlField={}, recommendedName={}", 
                    semanticMetric.getSqlField(), semanticMetric.getRecommendedName());
                ExtractedMetric metric = createExtractedMetric(semanticMetric, parseResult, alignmentResult);
                if (!addedMetricNames.contains(metric.getName())) {
                    metrics.add(metric);
                    addedMetricNames.add(metric.getName());
                    logger.info("[extractMetrics] 添加指标: name={}, category={}", metric.getName(), metric.getCategory());
                } else {
                    logger.info("[extractMetrics] 跳过重复指标: name={}", metric.getName());
                }
            }

            if (metrics.isEmpty()) {
                logger.info("[extractMetrics] 使用路径: 从SQL解析结果提取指标");
                for (CalciteSqlParseResult.AggregationInfo agg : parseResult.getAggregations()) {
                    logger.info("[extractMetrics] 处理聚合: type={}, field={}, alias={}, expression={}", 
                        agg.getType(), agg.getField(), agg.getAlias(), agg.getExpression());
                    ExtractedMetric metric = createExtractedMetricFromAggregation(agg, parseResult, alignmentResult);
                    if (!addedMetricNames.contains(metric.getName())) {
                        metrics.add(metric);
                        addedMetricNames.add(metric.getName());
                        logger.info("[extractMetrics] 添加指标: name={}, category={}", metric.getName(), metric.getCategory());
                    } else {
                        logger.info("[extractMetrics] 跳过重复指标: name={}", metric.getName());
                    }
                }
            }
        }

        logger.info("[extractMetrics] 提取完成，共 {} 个指标", metrics.size());
        return metrics;
    }
    
    /**
     * 提取复杂报表SQL的指标（新增方法）
     */
    private List<ExtractedMetric> extractMetricsForComplexReport(
            String sql,
            CalciteSqlParseResult parseResult,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        logger.info("[extractMetricsForComplexReport] 开始使用复杂报表提取策略");
        
        try {
            // 步骤1: 提取SQL层级结构
            List<ComplexSqlStructureAnalyzer.SqlLayer> layers = complexSqlAnalyzer.extractLayers(sql);
            logger.info("[extractMetricsForComplexReport] 提取到 {} 层SQL结构", layers.size());
            
            // 步骤2: 分层提取指标（原子→派生→复合）
            ReportMetricExtractor.LayeredMetrics layeredMetrics = 
                reportMetricExtractor.extractByLayer(layers);
            logger.info("[extractMetricsForComplexReport] 分层提取完成: 原子={}, 派生={}, 复合={}",
                layeredMetrics.getAtomics().size(),
                layeredMetrics.getDerived().size(),
                layeredMetrics.getComposite().size());
            
            // 步骤3: 处理多维度聚合（如payment_type维度）
            if (!layers.isEmpty()) {
                List<ExtractedMetric> dimensionalizedMetrics = 
                    multiDimensionHandler.processAllDimensionalizedMetrics(
                        layeredMetrics.getAtomics(), 
                        layers.get(0));
                
                if (!dimensionalizedMetrics.isEmpty()) {
                    logger.info("[extractMetricsForComplexReport] 生成 {} 个维度化指标", 
                        dimensionalizedMetrics.size());
                    // 替换原有的原子指标为维度化指标
                    layeredMetrics.setAtomics(dimensionalizedMetrics);
                }
            }
            
            // 步骤4: 整合层级，建立依赖关系
            SqlLayerIntegrator.IntegratedMetrics integratedMetrics = 
                sqlLayerIntegrator.integrate(layeredMetrics, layers, alignmentResult);
            logger.info("[extractMetricsForComplexReport] 整合完成，共 {} 个指标",
                integratedMetrics.getAllMetrics().size());
            
            // 步骤5: 返回所有指标
            return integratedMetrics.getAllMetrics();
            
        } catch (Exception e) {
            logger.error("[extractMetricsForComplexReport] 复杂报表提取失败: {}", e.getMessage(), e);
            logger.info("[extractMetricsForComplexReport] 降级到标准提取策略");
            
            // 失败时降级到标准提取
            LLMAlignment.SemanticAlignmentResult semanticResult = createDefaultSemanticResult(parseResult);
             return extractMetrics(parseResult, alignmentResult, semanticResult);
         }
     }

    /**
     * 使用 RexNode 血缘解析提取指标（首选策略）
     */
    private List<ExtractedMetric> extractMetricsByRex(
            String sql,
            CalciteSqlParseResult parseResult,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        logger.info("[extractMetricsByRex] 开始 RexNode 血缘解析");
        
        try {
            RexNodeLineageExtractor.RexMetricParseResult rexResult = rexMetricParser.parse(sql);
            
            if (rexResult.getError() != null) {
                logger.warn("[extractMetricsByRex] RexNode 解析失败: {}", rexResult.getError());
                return Collections.emptyList();
            }
            
            logger.info("[extractMetricsByRex] RexNode 解析成功，列数: {}", rexResult.getColumnLineages().size());
            
            List<ExtractedMetric> metrics = rexMetricParser.extractMetrics(rexResult);
            
            // 设置业务过程（从映射中获取）
            for (ExtractedMetric metric : metrics) {
                if (metric.getBusinessProcess() == null && !alignmentResult.getInvolvedObjectTypes().isEmpty()) {
                    metric.setBusinessProcess(alignmentResult.getInvolvedObjectTypes().get(0));
                }
                
                logger.info("[extractMetricsByRex] 指标: name={}, category={}, sources={}",
                    metric.getName(), metric.getCategory(), metric.getSources().size());
                
                if (metric.getSources() != null && !metric.getSources().isEmpty()) {
                    for (ExtractedMetric.ColumnSource source : metric.getSources()) {
                        logger.info("[extractMetricsByRex]   血缘: {} -> {}.{} [{}]",
                            metric.getName(), source.getSourceTable(), source.getSourceColumn(), source.getFullLineage());
                    }
                }
            }
            
            return metrics;
            
        } catch (Exception e) {
            logger.error("[extractMetricsByRex] RexNode 解析异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 提取 LEFT JOIN 报表的指标（新增方法）
     */
    private List<ExtractedMetric> extractMetricsForJoinReport(
            String sql,
            CalciteSqlParseResult parseResult,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        logger.info("[extractMetricsForJoinReport] 开始使用 LEFT JOIN 报表提取策略");
        
        try {
            // 使用 ReportJoinMetricHandler 处理
            ReportJoinMetricHandler.JoinReportMetrics joinMetrics = 
                reportJoinMetricHandler.processJoinReport(sql);
            
            if (joinMetrics.getError() != null) {
                logger.error("[extractMetricsForJoinReport] 处理失败: {}", joinMetrics.getError());
                logger.info("[extractMetricsForJoinReport] 降级到标准提取策略");
                LLMAlignment.SemanticAlignmentResult semanticResult = createDefaultSemanticResult(parseResult);
                return extractMetrics(parseResult, alignmentResult, semanticResult);
            }
            
            logger.info("[extractMetricsForJoinReport] 提取完成，共 {} 个指标", 
                joinMetrics.getAllMetrics().size());
            
            // 设置指标的业务过程（从映射中获取）
            for (ExtractedMetric metric : joinMetrics.getAllMetrics()) {
                if (metric.getBusinessProcess() == null && !alignmentResult.getInvolvedObjectTypes().isEmpty()) {
                    metric.setBusinessProcess(alignmentResult.getInvolvedObjectTypes().get(0));
                }
            }
            
            return joinMetrics.getAllMetrics();
            
        } catch (Exception e) {
            logger.error("[extractMetricsForJoinReport] LEFT JOIN 报表提取失败: {}", e.getMessage(), e);
            logger.info("[extractMetricsForJoinReport] 降级到标准提取策略");
            
            LLMAlignment.SemanticAlignmentResult semanticResult = createDefaultSemanticResult(parseResult);
            return extractMetrics(parseResult, alignmentResult, semanticResult);
        }
    }

    /**
     * 检测子查询结构（复合指标场景）
     * 子查询结构特征：内层有聚合，外层有非聚合的二次运算
     */
    private boolean hasSubQueryStructure(CalciteSqlParseResult parseResult) {
        boolean hasInnerAggregation = !parseResult.getAggregations().isEmpty();
        boolean hasOuterNonAggExpression = parseResult.getSelectFields().stream()
            .anyMatch(f -> !f.isAggregated() && f.getFieldName() != null);
        boolean hasTableSubQuery = parseResult.getTables().stream()
            .anyMatch(t -> t.getTableName() != null && t.getTableName().trim().startsWith("("));
        
        logger.info("[hasSubQueryStructure] hasInnerAggregation={}, hasOuterNonAggExpression={}, hasTableSubQuery={}", 
            hasInnerAggregation, hasOuterNonAggExpression, hasTableSubQuery);
        
        return hasInnerAggregation && (hasOuterNonAggExpression || hasTableSubQuery);
    }

    /**
     * 从子查询结构提取分层指标
     * 内层聚合 → 派生指标，外层非聚合运算 → 复合指标
     * 同时提取原子指标作为基础
     */
    private List<ExtractedMetric> extractFromSubQuery(
            CalciteSqlParseResult parseResult,
            MappingResolver.MappingAlignmentResult alignmentResult,
            Set<String> addedMetricNames) {
        
        List<ExtractedMetric> metrics = new ArrayList<>();
        Map<String, ExtractedMetric> derivedMetricsByName = new HashMap<>();
        Map<String, ExtractedMetric> atomicMetricsByName = new HashMap<>();
        
        logger.info("[extractFromSubQuery] 开始从子查询结构提取指标");
        
        // 0. 提取原子指标（内层聚合引用的字段）
        for (CalciteSqlParseResult.AggregationInfo agg : parseResult.getAggregations()) {
            if (agg.getField() != null && !agg.getField().equals("*")) {
                String fieldName = agg.getField();
                String atomicName = fieldName.toLowerCase();
                
                if (!atomicMetricsByName.containsKey(atomicName)) {
                    ExtractedMetric atomicMetric = new ExtractedMetric();
                    atomicMetric.setId(UUID.randomUUID().toString());
                    atomicMetric.setName(atomicName);
                    atomicMetric.setDisplayName(formatDisplayName(atomicName));
                    atomicMetric.setCategory(ExtractedMetric.MetricCategory.ATOMIC);
                    atomicMetric.setSourceSql(fieldName);
                    atomicMetric.setDescription("原子指标: " + fieldName);
                    atomicMetric.setConfidence(ExtractedMetric.ConfidenceLevel.HIGH);
                    
                    // 查找映射获取 businessProcess
                    for (MappingResolver.FieldMapping fieldMapping : alignmentResult.getFieldMappings()) {
                        if (fieldMapping.getSqlField() != null && 
                            (fieldMapping.getSqlField().equals(fieldName) || 
                             fieldMapping.getSqlField().contains(fieldName))) {
                            atomicMetric.setBusinessProcess(fieldMapping.getObjectType());
                            break;
                        }
                    }
                    
                    if (atomicMetric.getBusinessProcess() == null && 
                        !alignmentResult.getInvolvedObjectTypes().isEmpty()) {
                        atomicMetric.setBusinessProcess(alignmentResult.getInvolvedObjectTypes().get(0));
                    }
                    
                    atomicMetricsByName.put(atomicName, atomicMetric);
                    logger.info("[extractFromSubQuery] 添加原子指标: name={}, field={}", 
                        atomicMetric.getName(), fieldName);
                }
            }
        }
        
        // 1. 识别内层聚合 → 派生指标
        for (CalciteSqlParseResult.AggregationInfo agg : parseResult.getAggregations()) {
            ExtractedMetric metric = new ExtractedMetric();
            metric.setId(UUID.randomUUID().toString());
            String derivedName = agg.getAlias() != null ? agg.getAlias() : agg.getField() + "_derived";
            metric.setName(derivedName);
            metric.setDisplayName(formatDisplayName(derivedName));
            metric.setCategory(ExtractedMetric.MetricCategory.DERIVED);
            metric.setAggregationFunction(agg.getType());
            metric.setAggregationField(agg.getField());
            metric.setSourceSql(agg.getExpression());
            metric.setDescription("派生指标: " + agg.getType() + "(" + agg.getField() + ")");
            metric.setConfidence(ExtractedMetric.ConfidenceLevel.HIGH);
            
            // 设置原子指标引用
            if (agg.getField() != null && !agg.getField().equals("*")) {
                String atomicName = agg.getField().toLowerCase();
                if (atomicMetricsByName.containsKey(atomicName)) {
                    metric.setAtomicMetricId(atomicMetricsByName.get(atomicName).getId());
                    metric.setBusinessProcess(atomicMetricsByName.get(atomicName).getBusinessProcess());
                    logger.info("[extractFromSubQuery] 派生指标 {} 引用原子指标 {}", derivedName, atomicName);
                }
            } else {
                // COUNT(*) 的情况：从其他派生指标继承 businessProcess
                for (ExtractedMetric otherDerived : derivedMetricsByName.values()) {
                    if (otherDerived.getBusinessProcess() != null) {
                        metric.setBusinessProcess(otherDerived.getBusinessProcess());
                        metric.setAtomicMetricId(otherDerived.getAtomicMetricId());
                        logger.info("[extractFromSubQuery] 派生指标 {} (COUNT) 继承 businessProcess: {}", 
                            derivedName, metric.getBusinessProcess());
                        break;
                    }
                }
                // 兜底：使用 involvedObjectTypes
                if (metric.getBusinessProcess() == null && !alignmentResult.getInvolvedObjectTypes().isEmpty()) {
                    metric.setBusinessProcess(alignmentResult.getInvolvedObjectTypes().get(0));
                    logger.info("[extractFromSubQuery] 派生指标 {} (COUNT) 使用兜底 businessProcess: {}", 
                        derivedName, metric.getBusinessProcess());
                }
            }
            
            if (!addedMetricNames.contains(metric.getName())) {
                metrics.add(metric);
                derivedMetricsByName.put(metric.getName(), metric);
                addedMetricNames.add(metric.getName());
                logger.info("[extractFromSubQuery] 添加派生指标: name={}, aggFunc={}, aggField={}", 
                    metric.getName(), metric.getAggregationFunction(), metric.getAggregationField());
            }
        }
        
        // 将原子指标加入结果列表
        for (ExtractedMetric atomic : atomicMetricsByName.values()) {
            if (!addedMetricNames.contains(atomic.getName())) {
                metrics.add(atomic);
                addedMetricNames.add(atomic.getName());
                logger.info("[extractFromSubQuery] 添加原子指标到结果: name={}", atomic.getName());
            }
        }
        
        // 2. 识别外层非聚合表达式 → 复合指标
        // 只有引用了派生指标的表达式才是复合指标
        for (CalciteSqlParseResult.SelectField field : parseResult.getSelectFields()) {
            if (!field.isAggregated() && field.getRawExpression() != null) {
                String rawExpr = field.getRawExpression();
                String alias = field.getAlias() != null ? field.getAlias() : extractAliasFromExpression(rawExpr);
                boolean isSimpleField = (field.getFieldName() != null) && 
                    (rawExpr.equals(field.getFieldName()) || 
                     rawExpr.equals("`" + field.getFieldName() + "`"));
                
                // 检查是否引用了派生指标
                boolean referencesDerived = false;
                List<String> referencedDerivedNames = new ArrayList<>();
                for (ExtractedMetric derived : derivedMetricsByName.values()) {
                    String baseName = derived.getName().replace("_derived", "");
                    if (rawExpr.contains(baseName)) {
                        referencesDerived = true;
                        referencedDerivedNames.add(derived.getName());
                    }
                }
                
                // 只识别为复合指标的条件：
                // 1. 引用了派生指标，且是数学运算或NULLIF
                // 排除纯常量（如 '2024-07-06'）和简单CASE（无派生指标引用）
                if (!isSimpleField && referencesDerived && 
                    (rawExpr.contains("/") || rawExpr.contains("*") || 
                     rawExpr.contains("-") || rawExpr.contains("+") || 
                     rawExpr.toUpperCase().contains("NULLIF"))) {
                    
                    ExtractedMetric metric = new ExtractedMetric();
                    metric.setId(UUID.randomUUID().toString());
                    String compositeName = alias != null ? alias.replace("_composite", "") : "avg_pay_fee";
                    metric.setName(compositeName);
                    metric.setDisplayName(formatDisplayName(compositeName));
                    metric.setCategory(ExtractedMetric.MetricCategory.COMPOSITE);
                    metric.setDerivedFormula(rawExpr);
                    metric.setSourceSql(rawExpr);
                    metric.setDescription("复合指标: " + rawExpr);
                    metric.setConfidence(ExtractedMetric.ConfidenceLevel.HIGH);
                    
                    // 设置业务过程（继承自派生指标）
                    if (!derivedMetricsByName.isEmpty()) {
                        ExtractedMetric firstDerived = derivedMetricsByName.values().iterator().next();
                        if (firstDerived.getBusinessProcess() != null) {
                            metric.setBusinessProcess(firstDerived.getBusinessProcess());
                        }
                    }
                    
                    // 设置依赖的派生指标
                    List<String> baseMetricIds = new ArrayList<>();
                    for (ExtractedMetric derived : derivedMetricsByName.values()) {
                        String baseName = derived.getName().replace("_derived", "");
                        if (rawExpr.contains(baseName)) {
                            baseMetricIds.add(derived.getId());
                        }
                    }
                    metric.setBaseMetricIds(baseMetricIds);
                    
                    if (!addedMetricNames.contains(metric.getName())) {
                        metrics.add(metric);
                        addedMetricNames.add(metric.getName());
                        logger.info("[extractFromSubQuery] 添加复合指标: name={}, formula={}, 依赖派生指标={}", 
                            metric.getName(), rawExpr, referencedDerivedNames);
                    }
                } else if (!referencesDerived) {
                    logger.info("[extractFromSubQuery] 跳过非复合指标表达式: {} (无派生指标引用)", rawExpr);
                }
            }
        }
        
        logger.info("[extractFromSubQuery] 提取完成，共 {} 个指标", metrics.size());
        return metrics;
    }

    /**
     * 为派生指标设置原子指标属性（查找映射）
     */
    private void setAtomicMetricPropertiesForDerivedMetric(
            ExtractedMetric metric,
            CalciteSqlParseResult.AggregationInfo agg,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        String aggField = agg.getField();
        if (aggField != null && !aggField.isEmpty() && !"*".equals(aggField)) {
            for (MappingResolver.FieldMapping fieldMapping : alignmentResult.getFieldMappings()) {
                if (fieldMapping.getSqlField() != null && 
                    (fieldMapping.getSqlField().equals(aggField) || 
                     fieldMapping.getSqlField().contains(aggField))) {
                    metric.setBusinessProcess(fieldMapping.getObjectType());
                    metric.setAggregationField(aggField);
                    logger.info("[setAtomicMetricPropertiesForDerivedMetric] 映射成功: objectType={}", 
                        fieldMapping.getObjectType());
                    return;
                }
            }
        }
        
        if (alignmentResult.getInvolvedObjectTypes() != null && !alignmentResult.getInvolvedObjectTypes().isEmpty()) {
            metric.setBusinessProcess(alignmentResult.getInvolvedObjectTypes().get(0));
            metric.setAggregationField(aggField != null ? aggField : "*");
            logger.info("[setAtomicMetricPropertiesForDerivedMetric] 使用兜底: objectType={}", 
                metric.getBusinessProcess());
        } else {
            metric.setAggregationField(aggField != null ? aggField : "*");
        }
    }

    /**
     * 从SQL表达式中提取AS别名
     */
    private String extractAliasFromExpression(String expression) {
        if (expression == null) return null;
        Pattern pattern = Pattern.compile("\\s+AS\\s+[`\"']?([a-zA-Z_][a-zA-Z0-9_]*)[`\"']?\\s*$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从语义指标创建提取的指标
     */
    private ExtractedMetric createExtractedMetric(
            LLMAlignment.SemanticMetric semanticMetric,
            CalciteSqlParseResult parseResult,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        // 从 SQL 表达式中提取字段名
        String extractedField = extractFieldFromSqlExpression(semanticMetric.getSqlField());
        
        // 查找对应的字段映射
        String objectProperty = null;
        String objectType = null;
        for (MappingResolver.FieldMapping fieldMapping : alignmentResult.getFieldMappings()) {
            if (fieldMapping.getSqlField() != null && 
                (fieldMapping.getSqlField().equalsIgnoreCase(extractedField) ||
                 fieldMapping.getSqlField().equalsIgnoreCase(semanticMetric.getSqlField()))) {
                objectProperty = fieldMapping.getObjectProperty();
                objectType = fieldMapping.getObjectType();
                break;
            }
        }
        
        ExtractedMetric metric = new ExtractedMetric();
        metric.setId(UUID.randomUUID().toString());
        // 使用映射的属性名作为指标名，如果没有则从 SQL 提取
        String metricName = objectProperty != null ? objectProperty : extractedField;
        metric.setName(metricName);
        metric.setDisplayName(formatDisplayName(metricName));
        metric.setDescription(semanticMetric.getBusinessMeaning() != null ? 
            semanticMetric.getBusinessMeaning() : "从 SQL 提取的原子指标");
        metric.setSourceSql(semanticMetric.getSqlField());

        // 确定指标类型
        String suggestedType = semanticMetric.getSuggestedMetricType();
        if ("ATOMIC".equalsIgnoreCase(suggestedType)) {
            metric.setCategory(ExtractedMetric.MetricCategory.ATOMIC);
        } else if ("DERIVED".equalsIgnoreCase(suggestedType)) {
            metric.setCategory(ExtractedMetric.MetricCategory.DERIVED);
        } else if ("COMPOSITE".equalsIgnoreCase(suggestedType)) {
            metric.setCategory(ExtractedMetric.MetricCategory.COMPOSITE);
        } else {
            metric.setCategory(determineMetricCategory(parseResult, semanticMetric));
        }

        metric.setConfidence(convertConfidence(semanticMetric.getConfidence()));
        metric.setUnit(semanticMetric.getUnit());

        // 设置原子指标属性
        if (metric.getCategory() == ExtractedMetric.MetricCategory.ATOMIC) {
            setAtomicMetricProperties(metric, semanticMetric, alignmentResult);
        }

        // 设置派生指标属性
        if (metric.getCategory() == ExtractedMetric.MetricCategory.DERIVED) {
            setDerivedMetricProperties(metric, semanticMetric, parseResult);
        }

        return metric;
    }

    /**
     * 从聚合信息创建提取的指标
     */
    private ExtractedMetric createExtractedMetricFromAggregation(
            CalciteSqlParseResult.AggregationInfo agg,
            CalciteSqlParseResult parseResult,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        // 查找对应的字段映射
        String objectProperty = null;
        String objectType = null;
        for (MappingResolver.FieldMapping fieldMapping : alignmentResult.getFieldMappings()) {
            if (fieldMapping.getSqlField() != null && 
                (fieldMapping.getSqlField().equalsIgnoreCase(agg.getField()) ||
                 fieldMapping.getSqlField().equalsIgnoreCase(agg.getAlias()))) {
                objectProperty = fieldMapping.getObjectProperty();
                objectType = fieldMapping.getObjectType();
                break;
            }
        }
        
        ExtractedMetric metric = new ExtractedMetric();
        metric.setId(UUID.randomUUID().toString());
        // 使用映射的属性名作为指标名，如果没有则使用字段名
        metric.setName(objectProperty != null ? objectProperty : agg.getField());
        // 显示名使用属性名的驼峰格式
        metric.setDisplayName(formatDisplayName(objectProperty != null ? objectProperty : agg.getField()));
        metric.setDescription("从 SQL 提取的原子指标: " + agg.getExpression());
        metric.setSourceSql(agg.getExpression());
        metric.setCategory(ExtractedMetric.MetricCategory.ATOMIC);
        metric.setConfidence(ExtractedMetric.ConfidenceLevel.MEDIUM);

        setAtomicMetricPropertiesFromAggregation(metric, agg, alignmentResult);

        return metric;
    }
    
    /**
     * 格式化显示名称（蛇形转驼峰）
     */
    private String formatDisplayName(String name) {
        if (name == null) return "";
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        return result.toString();
    }

    /**
     * 从建议创建提取的指标
     */
    private ExtractedMetric createExtractedMetricFromSuggestion(
            LLMAlignment.MetricSuggestion suggestion,
            List<ExtractedMetric> existingMetrics) {
        
        // 查找对应的原子指标
        String atomicMetricId = suggestion.getAtomicMetricId();
        ExtractedMetric atomicMetric = findAtomicMetricByName(existingMetrics, atomicMetricId);

        if (atomicMetric == null) {
            return null;
        }

        ExtractedMetric metric = new ExtractedMetric();
        metric.setId(UUID.randomUUID().toString());
        metric.setName(suggestion.getName());
        metric.setDisplayName(suggestion.getName());
        metric.setCategory("derived".equalsIgnoreCase(suggestion.getType()) ? ExtractedMetric.MetricCategory.DERIVED : ExtractedMetric.MetricCategory.COMPOSITE);
        metric.setAtomicMetricId(atomicMetric.getId());
        metric.setTimeGranularity(suggestion.getTimeGranularity());
        metric.setDimensions(suggestion.getDimensions());
        metric.setFilterConditions(suggestion.getFilterConditions());
        metric.setConfidence(ExtractedMetric.ConfidenceLevel.MEDIUM);

        return metric;
    }

    /**
     * 使用 Apache Calcite 从 SQL 表达式中提取原始字段名
     * 例如: "COUNT(ID) AS count_star" → "ID"
     *       "SUM(amount)" → "amount"
     *       "MAX(price)" → "price"
     *       "ID" → "ID"
     *       "t.amount" → "amount"
     */
    private String extractFieldFromSqlExpression(String sqlExpression) {
        if (sqlExpression == null || sqlExpression.isEmpty()) {
            return sqlExpression;
        }

        String trimmed = sqlExpression.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        // 清理反引号，Calcite 不支持反引号作为标识符分隔符
        String cleanedExpression = trimmed.replace("`", "");
        String selectSql = "SELECT " + cleanedExpression;
        
        CalciteSqlParseResult parseResult = sqlParser.parse(selectSql);

        if (!parseResult.getSelectFields().isEmpty()) {
            CalciteSqlParseResult.SelectField firstField = parseResult.getSelectFields().get(0);
            if (firstField.getFieldName() != null) {
                logger.info("[extractFieldFromSqlExpression] 从 Calcite 解析结果提取字段: '{}' -> '{}'", 
                    sqlExpression, firstField.getFieldName());
                return firstField.getFieldName();
            }
        }

        logger.info("[extractFieldFromSqlExpression] 无法从 Calcite 解析结果提取字段,返回原值: '{}'", sqlExpression);
        return trimmed;
    }

    /**
     * 从 SqlNode 中提取字段名
     */
    private String extractFieldNameFromNode(SqlNode node) {
        if (node == null) {
            return null;
        }

        if (node instanceof SqlIdentifier) {
            SqlIdentifier identifier = (SqlIdentifier) node;
            List<String> names = identifier.names;
            if (!names.isEmpty()) {
                return names.get(names.size() - 1);
            }
        } else if (node instanceof SqlCall) {
            SqlCall call = (SqlCall) node;
            SqlKind kind = call.getKind();

            if (kind == SqlKind.AS) {
                return extractFieldNameFromNode(call.operand(0));
            }

            if (kind == SqlKind.COUNT || kind == SqlKind.SUM || kind == SqlKind.AVG ||
                kind == SqlKind.MAX || kind == SqlKind.MIN) {
                if (call.operandCount() > 0) {
                    SqlNode firstOperand = call.operand(0);
                    if (firstOperand instanceof SqlIdentifier) {
                        SqlIdentifier identifier = (SqlIdentifier) firstOperand;
                        List<String> names = identifier.names;
                        if (!names.isEmpty()) {
                            return names.get(names.size() - 1);
                        }
                    } else if (firstOperand instanceof SqlCall) {
                        return extractFieldNameFromNode(firstOperand);
                    }
                }
                return "*";
            }

            for (SqlNode operand : call.getOperandList()) {
                String fieldName = extractFieldNameFromNode(operand);
                if (fieldName != null && !fieldName.isEmpty()) {
                    return fieldName;
                }
            }
        }

        return null;
    }

    /**
     * 检查字段是否匹配（支持完整表达式和原始字段名）
     */
    private boolean isFieldMatch(String mappingSqlField, String semanticSqlField) {
        if (mappingSqlField == null || semanticSqlField == null) {
            return false;
        }

        if (mappingSqlField.equals(semanticSqlField)) {
            return true;
        }

        String extractedField = extractFieldFromSqlExpression(semanticSqlField);
        return mappingSqlField.equals(extractedField);
    }

    /**
     * 设置原子指标属性
     */
    private void setAtomicMetricProperties(
            ExtractedMetric metric,
            LLMAlignment.SemanticMetric semanticMetric,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        logger.info("\n========== 步骤5详细映射逻辑(语义路径) - 开始 ==========");
        
        metric.setAggregationFunction(convertAggregationType(semanticMetric.getAggregationType()));
        logger.info("[步骤5.1] 聚合函数: {}", semanticMetric.getAggregationType());
        logger.info("[步骤5.2] 语义指标SQL字段: {}", semanticMetric.getSqlField());

        // 调试日志：查看映射结果内容
        logger.info("[步骤5.3] 映射结果分析:");
        logger.info("  - 指标名称: {}", metric.getName());
        logger.info("  - 原始SQL字段: {}", semanticMetric.getSqlField());
        logger.info("  - fieldMappings数量: {}", 
            alignmentResult.getFieldMappings() != null ? alignmentResult.getFieldMappings().size() : 0);
        
        // 打印所有 fieldMappings 详情
        if (alignmentResult.getFieldMappings() != null && !alignmentResult.getFieldMappings().isEmpty()) {
            logger.info("  - fieldMappings详情:");
            for (int i = 0; i < alignmentResult.getFieldMappings().size(); i++) {
                MappingResolver.FieldMapping fm = alignmentResult.getFieldMappings().get(i);
                logger.info("    [{}] sqlField='{}', objectType='{}', objectProperty='{}', confidence={}", 
                    i, fm.getSqlField(), fm.getObjectType(), fm.getObjectProperty(), fm.getConfidence());
            }
        } else {
            logger.info("  - fieldMappings: 空");
        }
        
        logger.info("  - involvedObjectTypes: {}", alignmentResult.getInvolvedObjectTypes());
        logger.info("  - tableToObjectMap: {}", alignmentResult.getTableToObjectMap());
        
        // 尝试从映射中获取业务过程
        boolean foundMapping = false;

        String extractedField = extractFieldFromSqlExpression(semanticMetric.getSqlField());
        logger.info("[步骤5.4] 精确字段匹配: semanticSqlField='{}', extractedField='{}'",
            semanticMetric.getSqlField(), extractedField);

        for (MappingResolver.FieldMapping fieldMapping : alignmentResult.getFieldMappings()) {
            logger.info("  - 检查映射: mappingSqlField='{}', 匹配结果={}",
                fieldMapping.getSqlField(),
                isFieldMatch(fieldMapping.getSqlField(), semanticMetric.getSqlField()));

            if (isFieldMatch(fieldMapping.getSqlField(), semanticMetric.getSqlField())) {
                metric.setBusinessProcess(fieldMapping.getObjectType());
                // 关键修正：aggregationField应该设置为字段名，而非objectProperty
                String extractedFieldName = extractFieldFromSqlExpression(semanticMetric.getSqlField());
                metric.setAggregationField(extractedFieldName != null ? extractedFieldName : fieldMapping.getObjectProperty());
                foundMapping = true;
                logger.info("  ✓ 精确匹配成功: objectType={}, objectProperty={}, aggregationField={}",
                    fieldMapping.getObjectType(), fieldMapping.getObjectProperty(), metric.getAggregationField());
                break;
            }
        }
        if (!foundMapping) {
            logger.info("  ✗ 精确匹配失败");
        }
        
        // 兜底策略1: 使用第一个涉及的对象类型
        logger.info("[步骤5.5] 兜底策略1: 使用involvedObjectTypes");
        if (!foundMapping) {
            if (alignmentResult.getInvolvedObjectTypes() != null && !alignmentResult.getInvolvedObjectTypes().isEmpty()) {
                metric.setBusinessProcess(alignmentResult.getInvolvedObjectTypes().get(0));
                logger.warn("  ✓ 策略1触发: 指标 {} 未找到精确映射,使用第一个对象类型: {}", 
                    metric.getName(), metric.getBusinessProcess());
                foundMapping = true;
            } else {
                logger.info("  ✗ 策略1失败: involvedObjectTypes为空");
            }
        } else {
            logger.info("  - 跳过策略1: 已找到映射");
        }
        
        // 兜底策略2: 如果映射完全失败,尝试从所有对象类型中查找第一个业务对象类型（过滤系统虚拟对象）
        logger.info("[步骤5.6] 兜底策略2: 查找第一个业务对象类型");
        if (!foundMapping) {
            try {
                List<ObjectType> allTypes = loader.listObjectTypes();
                logger.info("  - 系统所有对象类型数量: {}", allTypes.size());
                
                // 过滤掉系统虚拟对象类型（workspace, database, table等）
                List<ObjectType> businessTypes = allTypes.stream()
                    .filter(t -> !isSystemVirtualObject(t.getName()))
                    .collect(Collectors.toList());
                
                logger.info("  - 过滤后的业务对象类型数量: {}", businessTypes.size());
                if (!businessTypes.isEmpty()) {
                    logger.info("  - 业务对象类型列表: {}", 
                        businessTypes.stream().map(ObjectType::getName).collect(Collectors.toList()));
                    
                    metric.setBusinessProcess(businessTypes.get(0).getName());
                    
                    // 兜底策略：使用已提取的聚合字段名
                    metric.setAggregationField(extractedField != null ? extractedField : "*");
                    
                    logger.error("  ✓ 兜底策略2触发: 指标 {} 映射完全失败,使用系统第一个业务对象类型: {}, aggregationField={}. 请检查数据映射配置!", 
                        metric.getName(), metric.getBusinessProcess(), metric.getAggregationField());
                } else {
                    logger.error("  ✗ 兜底策略2失败: 指标 {} 无法找到任何业务对象类型,请配置数据映射!", metric.getName());
                }
            } catch (Exception e) {
                logger.error("  ✗ 兜底策略2异常: 无法获取对象类型列表: {}", e.getMessage());
            }
        } else {
            logger.info("  - 跳过兜底策略2: 已找到映射");
        }
        
        logger.info("[步骤5.7] 最终结果: businessProcess={}, aggregationField={}, 映射成功={}",
            metric.getBusinessProcess(), metric.getAggregationField(), foundMapping);
        logger.info("========== 步骤5详细映射逻辑(语义路径) - 结束 ==========\n");
    }

    /**
     * 从聚合信息设置原子指标属性
     */
    private void setAtomicMetricPropertiesFromAggregation(
            ExtractedMetric metric,
            CalciteSqlParseResult.AggregationInfo agg,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        logger.info("\n========== 步骤5详细映射逻辑 - 开始 ==========");
        
        metric.setAggregationFunction(agg.getType());
        logger.info("[步骤5.1] 聚合函数: {}", agg.getType());
        
        // 处理聚合字段，COUNT(*) 可能没有具体字段
        String aggField = agg.getField();
        if (aggField != null && !aggField.isEmpty() && !"*".equals(aggField)) {
            metric.setAggregationField(aggField);
            logger.info("[步骤5.2] 聚合字段: {}", aggField);
        } else {
            // COUNT(*) 或其他无字段聚合
            metric.setAggregationField("*");
            logger.info("[步骤5.2] 聚合字段: * (COUNT全表)");
        }

        // 尝试从映射中获取业务过程
        boolean foundMapping = false;
        
        // 调试日志：查看映射结果内容
        logger.info("[步骤5.3] 映射结果分析:");
        logger.info("  - 指标名称: {}", metric.getName());
        logger.info("  - 原始SQL字段: {}", aggField);
        logger.info("  - fieldMappings数量: {}", 
            alignmentResult.getFieldMappings() != null ? alignmentResult.getFieldMappings().size() : 0);
        
        // 打印所有 fieldMappings 详情
        if (alignmentResult.getFieldMappings() != null && !alignmentResult.getFieldMappings().isEmpty()) {
            logger.info("  - fieldMappings详情:");
            for (int i = 0; i < alignmentResult.getFieldMappings().size(); i++) {
                MappingResolver.FieldMapping fm = alignmentResult.getFieldMappings().get(i);
                logger.info("    [{}] sqlField='{}', objectType='{}', objectProperty='{}', confidence={}", 
                    i, fm.getSqlField(), fm.getObjectType(), fm.getObjectProperty(), fm.getConfidence());
            }
        } else {
            logger.info("  - fieldMappings: 空");
        }
        
        logger.info("  - involvedObjectTypes: {}", alignmentResult.getInvolvedObjectTypes());
        logger.info("  - tableToObjectMap: {}", alignmentResult.getTableToObjectMap());
        
        // 策略1: 对于非 COUNT(*) 的情况，优先尝试精确匹配聚合字段
        logger.info("[步骤5.4] 策略1: 精确字段匹配");
        if (aggField != null && !aggField.isEmpty() && !"*".equals(aggField)) {
            logger.info("  - 尝试匹配字段: {}", aggField);
            for (MappingResolver.FieldMapping fieldMapping : alignmentResult.getFieldMappings()) {
                logger.info("  - 检查映射: sqlField='{}', 匹配结果={}", 
                    fieldMapping.getSqlField(), 
                    fieldMapping.getSqlField() != null && 
                    (fieldMapping.getSqlField().equals(aggField) || fieldMapping.getSqlField().contains(aggField)));
                
                if (fieldMapping.getSqlField() != null && 
                    (fieldMapping.getSqlField().equals(aggField) || 
                     fieldMapping.getSqlField().contains(aggField))) {
                    metric.setBusinessProcess(fieldMapping.getObjectType());
                    foundMapping = true;
                    logger.info("  ✓ 策略1成功: 指标 {} 通过字段匹配找到业务过程: {}", 
                        metric.getName(), metric.getBusinessProcess());
                    break;
                }
            }
            if (!foundMapping) {
                logger.info("  ✗ 策略1失败: 未找到匹配的字段映射");
            }
        } else {
            logger.info("  - 跳过策略1: COUNT(*) 无具体字段");
        }
        
        // 策略2: 对于 COUNT(*) 或字段匹配失败的情况，使用涉及的对象类型
        logger.info("[步骤5.5] 策略2: 使用涉及的对象类型");
        if (!foundMapping) {
            if (alignmentResult.getInvolvedObjectTypes() != null && !alignmentResult.getInvolvedObjectTypes().isEmpty()) {
                metric.setBusinessProcess(alignmentResult.getInvolvedObjectTypes().get(0));
                logger.info("  ✓ 策略2成功: 指标 {} 使用涉及的对象类型作为业务过程: {}", 
                    metric.getName(), metric.getBusinessProcess());
                foundMapping = true;
            } else {
                logger.info("  ✗ 策略2失败: involvedObjectTypes 为空");
            }
        } else {
            logger.info("  - 跳过策略2: 已找到映射");
        }
        
        // 策略3: 尝试从 tableToObjectMap 获取
        logger.info("[步骤5.6] 策略3: 从 tableToObjectMap 获取");
        if (!foundMapping) {
            if (alignmentResult.getTableToObjectMap() != null && !alignmentResult.getTableToObjectMap().isEmpty()) {
                String objectType = alignmentResult.getTableToObjectMap().values().iterator().next();
                metric.setBusinessProcess(objectType);
                logger.info("  ✓ 策略3成功: 指标 {} 从 tableToObjectMap 获取业务过程: {}", 
                    metric.getName(), metric.getBusinessProcess());
                foundMapping = true;
            } else {
                logger.info("  ✗ 策略3失败: tableToObjectMap 为空");
            }
        } else {
            logger.info("  - 跳过策略3: 已找到映射");
        }
        
        // 兜底策略: 如果映射完全失败,尝试从所有对象类型中查找第一个业务对象类型（过滤系统虚拟对象）
        logger.info("[步骤5.7] 兜底策略: 查找系统第一个业务对象类型");
        if (!foundMapping) {
            try {
                List<ObjectType> allTypes = loader.listObjectTypes();
                logger.info("  - 系统所有对象类型数量: {}", allTypes.size());
                
                // 过滤掉系统虚拟对象类型（workspace, database, table等）
                List<ObjectType> businessTypes = allTypes.stream()
                    .filter(t -> !isSystemVirtualObject(t.getName()))
                    .collect(Collectors.toList());
                
                logger.info("  - 过滤后的业务对象类型数量: {}", businessTypes.size());
                if (!businessTypes.isEmpty()) {
                    logger.info("  - 业务对象类型列表: {}", 
                        businessTypes.stream().map(ObjectType::getName).collect(Collectors.toList()));
                    
                    metric.setBusinessProcess(businessTypes.get(0).getName());
                    logger.error("  ✓ 兜底策略触发: 指标 {} 映射完全失败,使用系统第一个业务对象类型: {}. 请检查数据映射配置!", 
                        metric.getName(), metric.getBusinessProcess());
                } else {
                    logger.error("  ✗ 兜底策略失败: 指标 {} 无法找到任何业务对象类型,请配置数据映射!", metric.getName());
                }
            } catch (Exception e) {
                logger.error("  ✗ 兜底策略异常: 无法获取对象类型列表: {}", e.getMessage());
            }
        } else {
            logger.info("  - 跳过兜底策略: 已找到映射");
        }
        
        logger.info("[步骤5.8] 最终结果: businessProcess={}, aggregationField={}, 映射成功={}",
            metric.getBusinessProcess(), metric.getAggregationField(), foundMapping);
        logger.info("========== 步骤5详细映射逻辑 - 结束 ==========\n");
    }

    /**
     * 设置派生指标属性
     */
    private void setDerivedMetricProperties(
            ExtractedMetric metric,
            LLMAlignment.SemanticMetric semanticMetric,
            CalciteSqlParseResult parseResult) {
        
        // 从parseResult提取时间信息
        String timeDimension = null;
        String timeGranularity = null;
        
        if (!parseResult.getTimeConditions().isEmpty()) {
            CalciteSqlParseResult.TimeCondition timeCond = parseResult.getTimeConditions().get(0);
            timeDimension = timeCond.getField();
            timeGranularity = timeCond.getTimeGranularity() != null ? timeCond.getTimeGranularity() : "day";
        } else if (!parseResult.getWhereConditions().isEmpty()) {
            // 尝试从WHERE条件中识别时间字段
            for (CalciteSqlParseResult.WhereCondition cond : parseResult.getWhereConditions()) {
                if (isTimeCondition(cond)) {
                    timeDimension = cond.getField();
                    timeGranularity = "day";
                    break;
                }
            }
        }
        
        if (timeDimension != null) {
            metric.setTimeDimension(timeDimension);
            metric.setTimeGranularity(timeGranularity);
        }

        // 从 WHERE 条件提取过滤条件
        if (!parseResult.getWhereConditions().isEmpty()) {
            Map<String, Object> filterConditions = new HashMap<>();
            for (CalciteSqlParseResult.WhereCondition cond : parseResult.getWhereConditions()) {
                if (!isTimeCondition(cond)) {
                    filterConditions.put(cond.getField(), cond.getValue());
                }
            }
            if (!filterConditions.isEmpty()) {
                metric.setFilterConditions(filterConditions);
            }
        }
    }

    /**
     * 验证指标
     */
    private List<MetricValidator.ValidationResult> validateMetrics(List<ExtractedMetric> metrics) {
        return metrics.stream()
            .map(metricValidator::validate)
            .collect(Collectors.toList());
    }

    /**
     * 生成建议
     */
    private List<String> generateSuggestions(
            List<ExtractedMetric> metrics,
            List<MetricValidator.ValidationResult> validations) {
        
        List<String> suggestions = new ArrayList<>();

        // 检查是否有未映射的字段
        if (metrics.stream().anyMatch(m -> m.getConfidence() == ExtractedMetric.ConfidenceLevel.LOW)) {
            suggestions.add("部分字段未能自动映射，建议检查映射关系配置");
        }

        // 检查是否有错误
        long errorCount = validations.stream()
            .filter(v -> v.hasErrors())
            .count();
        if (errorCount > 0) {
            suggestions.add(String.format("%d 个指标存在验证错误，请修正后保存", errorCount));
        }

        // 检查指标完整性
        long incompleteCount = metrics.stream()
            .filter(m -> m.getName() == null || m.getBusinessProcess() == null)
            .count();
        if (incompleteCount > 0) {
            suggestions.add(String.format("%d 个指标信息不完整，建议补充指标名称和业务过程", incompleteCount));
        }

        if (suggestions.isEmpty()) {
            suggestions.add("所有指标验证通过，可以保存");
        }

        return suggestions;
    }

    /**
     * 保存原子指标
     */
    private String saveAtomicMetric(
            ExtractedMetric metric,
            boolean createNew,
            List<String> existingMetricIds,
            List<String> workspaceIds) throws Exception {
        
        AtomicMetric atomic = metricValidator.convertToAtomicMetric(metric);

        if (!createNew && !existingMetricIds.isEmpty()) {
            String existingId = existingMetricIds.stream()
                .filter(id -> {
                    try {
                        return metric.getName().equals(atomicMetricService.getAtomicMetric(id).getName());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .findFirst()
                .orElse(null);

            if (existingId != null) {
                atomicMetricService.updateAtomicMetric(existingId, atomic);
                return existingId;
            }
        }

        return atomicMetricService.createAtomicMetric(atomic, workspaceIds);
    }

    /**
     * 保存指标定义
     */
    private String saveMetricDefinition(
            ExtractedMetric metric,
            boolean createNew,
            List<String> existingMetricIds,
            List<String> workspaceIds) throws Exception {
        
        MetricDefinition definition = metricValidator.convertToMetricDefinition(metric);

        if (!createNew && !existingMetricIds.isEmpty()) {
            String existingId = existingMetricIds.stream()
                .filter(id -> {
                    try {
                        return metric.getName().equals(metricService.getMetricDefinition(id).getName());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .findFirst()
                .orElse(null);

            if (existingId != null) {
                metricService.updateMetricDefinition(existingId, definition);
                return existingId;
            }
        }

        return metricService.createMetricDefinition(definition, workspaceIds);
    }

    /**
     * 生成指标名称
     */
    private String generateMetricName(LLMAlignment.SemanticMetric semanticMetric) {
        String name = semanticMetric.getRecommendedName();
        if (name == null || name.isEmpty()) {
            name = semanticMetric.getBusinessMeaning();
        }
        return name != null ? name.toLowerCase().replaceAll("\\s+", "_") : "metric";
    }

    /**
     * 确定指标类型
     */
    private ExtractedMetric.MetricCategory determineMetricCategory(
            CalciteSqlParseResult parseResult,
            LLMAlignment.SemanticMetric semanticMetric) {
        
        // 检查是否有时间限定条件
        boolean hasTimeCondition = !parseResult.getTimeConditions().isEmpty();
        
        // 检查是否有业务范围限定条件（排除时间条件）
        boolean hasBusinessFilter = false;
        if (!parseResult.getWhereConditions().isEmpty()) {
            for (CalciteSqlParseResult.WhereCondition cond : parseResult.getWhereConditions()) {
                if (!isTimeCondition(cond)) {
                    hasBusinessFilter = true;
                    break;
                }
            }
        }
        
        // 检查是否有GROUP BY（除了时间维度外的维度）
        boolean hasNonTimeDimension = false;
        if (!parseResult.getGroupByFields().isEmpty()) {
            for (String groupField : parseResult.getGroupByFields()) {
                if (!isTimeDimensionField(groupField)) {
                    hasNonTimeDimension = true;
                    break;
                }
            }
        }

        // 根据设计文档规则判定：
        // 派生指标 = 原子指标 + (时间限定 或 业务范围限定 或 额外维度)
        if (hasTimeCondition || hasBusinessFilter || hasNonTimeDimension) {
            return ExtractedMetric.MetricCategory.DERIVED;
        }
        return ExtractedMetric.MetricCategory.ATOMIC;
    }
    
    /**
     * 判断字段是否为时间维度字段
     */
    private boolean isTimeDimensionField(String field) {
        if (field == null) return false;
        String lower = field.toLowerCase();
        return lower.contains("time") || lower.contains("date") || 
               lower.contains("created_at") || lower.contains("updated_at") ||
               lower.contains("year") || lower.contains("month") || 
               lower.contains("day") || lower.contains("hour");
    }

    /**
     * 转换聚合类型
     */
    private String convertAggregationType(String llmType) {
        if (llmType == null) return "SUM";
        switch (llmType.toUpperCase()) {
            case "SUM": return "SUM";
            case "AVG": return "AVG";
            case "COUNT": return "COUNT";
            case "MAX": return "MAX";
            case "MIN": return "MIN";
            default: return "SUM";
        }
    }

    /**
     * 转换置信度
     */
    private ExtractedMetric.ConfidenceLevel convertConfidence(double llmConfidence) {
        if (llmConfidence >= 0.8) return ExtractedMetric.ConfidenceLevel.HIGH;
        if (llmConfidence >= 0.5) return ExtractedMetric.ConfidenceLevel.MEDIUM;
        return ExtractedMetric.ConfidenceLevel.LOW;
    }

    /**
     * 判断是否为时间条件
     */
    private boolean isTimeCondition(CalciteSqlParseResult.WhereCondition cond) {
        String field = cond.getField().toLowerCase();
        return field.contains("time") || field.contains("date") || 
               field.contains("created_at") || field.contains("updated_at");
    }

    /**
     * 根据名称查找原子指标
     */
    private ExtractedMetric findAtomicMetricByName(List<ExtractedMetric> metrics, String name) {
        return metrics.stream()
            .filter(m -> m.getCategory() == ExtractedMetric.MetricCategory.ATOMIC)
            .filter(m -> name != null && (m.getName().equalsIgnoreCase(name) || 
                   m.getDisplayName() != null && m.getDisplayName().equalsIgnoreCase(name)))
            .findFirst()
            .orElse(null);
    }

    // ==================== 内部类定义 ====================

    public static class SqlPasteParseResult {
        private String originalSql;
        private CalciteSqlParseResult sqlAnalysis;
        private List<ExtractedMetric> extractedMetrics = new ArrayList<>();
        private List<MetricValidator.ValidationResult> validations = new ArrayList<>();
        private MappingResolver.MappingAlignmentResult mappingResult;
        private LLMAlignment.SemanticAlignmentResult semanticResult;
        private List<String> suggestions = new ArrayList<>();
        private List<String> errors = new ArrayList<>();

        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
        public CalciteSqlParseResult getSqlAnalysis() { return sqlAnalysis; }
        public void setSqlAnalysis(CalciteSqlParseResult sqlAnalysis) { this.sqlAnalysis = sqlAnalysis; }
        public List<ExtractedMetric> getExtractedMetrics() { return extractedMetrics; }
        public void setExtractedMetrics(List<ExtractedMetric> extractedMetrics) { this.extractedMetrics = extractedMetrics; }
        public List<MetricValidator.ValidationResult> getValidations() { return validations; }
        public void setValidations(List<MetricValidator.ValidationResult> validations) { this.validations = validations; }
        public MappingResolver.MappingAlignmentResult getMappingResult() { return mappingResult; }
        public void setMappingResult(MappingResolver.MappingAlignmentResult mappingResult) { this.mappingResult = mappingResult; }
        public LLMAlignment.SemanticAlignmentResult getSemanticResult() { return semanticResult; }
        public void setSemanticResult(LLMAlignment.SemanticAlignmentResult semanticResult) { this.semanticResult = semanticResult; }
        public List<String> getSuggestions() { return suggestions; }
        public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
        public void addError(String code, String message) { errors.add(code + ": " + message); }
        public void addSuggestion(String suggestion) { suggestions.add(suggestion); }
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    public static class SqlPasteOptions {
        private boolean enableLLM = true;
        private boolean suggestMetrics = true;
        private String workspaceId;

        public boolean isEnableLLM() { return enableLLM; }
        public void setEnableLLM(boolean enableLLM) { this.enableLLM = enableLLM; }
        public boolean isSuggestMetrics() { return suggestMetrics; }
        public void setSuggestMetrics(boolean suggestMetrics) { this.suggestMetrics = suggestMetrics; }
        public String getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    }

    public static class SaveResult {
        private boolean success;
        private List<String> savedIds = new ArrayList<>();
        private List<SavedMetricInfo> savedMetrics = new ArrayList<>();
        private List<SaveError> errors = new ArrayList<>();

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public List<String> getSavedIds() { return savedIds; }
        public void setSavedIds(List<String> savedIds) { this.savedIds = savedIds; }
        public List<SavedMetricInfo> getSavedMetrics() { return savedMetrics; }
        public void setSavedMetrics(List<SavedMetricInfo> savedMetrics) { this.savedMetrics = savedMetrics; }
        public List<SaveError> getErrors() { return errors; }
        public void setErrors(List<SaveError> errors) { this.errors = errors; }
    }

    public static class SavedMetricInfo {
        private String metricName;
        private String savedId;
        private String status;

        public SavedMetricInfo(String metricName, String savedId, String status) {
            this.metricName = metricName;
            this.savedId = savedId;
            this.status = status;
        }

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        public String getSavedId() { return savedId; }
        public void setSavedId(String savedId) { this.savedId = savedId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class SaveError {
        private String metricName;
        private String message;

        public SaveError(String metricName, String message) {
            this.metricName = metricName;
            this.message = message;
        }

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class ValidationOnlyResult {
        private String originalSql;
        private List<ExtractedMetric> extractedMetrics = new ArrayList<>();
        private List<MetricValidator.ValidationResult> validations = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();
        private boolean allValid;

        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
        public List<ExtractedMetric> getExtractedMetrics() { return extractedMetrics; }
        public void setExtractedMetrics(List<ExtractedMetric> extractedMetrics) { this.extractedMetrics = extractedMetrics; }
        public List<MetricValidator.ValidationResult> getValidations() { return validations; }
        public void setValidations(List<MetricValidator.ValidationResult> validations) { this.validations = validations; }
        public List<String> getSuggestions() { return suggestions; }
        public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
        public boolean isAllValid() { return allValid; }
        public void setAllValid(boolean allValid) { this.allValid = allValid; }
    }
    
    /**
     * 判断是否为系统虚拟对象类型（不需要数据映射）
     * @param objectTypeName 对象类型名称
     * @return true 表示是系统虚拟对象
     */
    private boolean isSystemVirtualObject(String objectTypeName) {
        // 系统虚拟对象类型列表（来自 schema-system.yaml）
        return "workspace".equals(objectTypeName) ||
               "database".equals(objectTypeName) ||
               "table".equals(objectTypeName) ||
               "column".equals(objectTypeName) ||
               "mapping".equals(objectTypeName) ||
               "AtomicMetric".equals(objectTypeName) ||
               "MetricDefinition".equals(objectTypeName);
    }
}
