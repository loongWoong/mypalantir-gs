package com.mypalantir.sqlparse;

import com.mypalantir.meta.Loader;
import com.mypalantir.metric.AtomicMetric;
import com.mypalantir.metric.MetricDefinition;
import com.mypalantir.service.AtomicMetricService;
import com.mypalantir.service.DataValidator;
import com.mypalantir.service.MetricService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 指标验证器
 * 验证提取的指标定义是否符合业务规则
 */
@Component
public class MetricValidator {

    private static final Set<String> VALID_AGGREGATION_FUNCTIONS = new HashSet<>(
        Arrays.asList("SUM", "AVG", "COUNT", "MAX", "MIN", "DISTINCT_COUNT", "COUNT_DISTINCT")
    );

    private static final Set<String> VALID_TIME_GRANULARITIES = new HashSet<>(
        Arrays.asList("minute", "hour", "day", "week", "month", "quarter", "year")
    );

    private static final Set<String> VALID_METRIC_TYPES = new HashSet<>(
        Arrays.asList("ATOMIC", "DERIVED", "COMPOSITE", "atomic", "derived", "composite")
    );

    private static final Set<String> VALID_COMPARISON_TYPES = new HashSet<>(
        Arrays.asList("YoY", "MoM", "WoW", "QoQ", "YoY", "MoM", "WoW", "QoQ")
    );

    private static final Set<String> VALID_STATUS = new HashSet<>(
        Arrays.asList("active", "inactive", "deprecated")
    );

    private final Loader loader;
    private final AtomicMetricService atomicMetricService;
    private final MetricService metricService;
    private final DataValidator dataValidator;

    public MetricValidator(
            Loader loader,
            AtomicMetricService atomicMetricService,
            MetricService metricService,
            DataValidator dataValidator) {
        this.loader = loader;
        this.atomicMetricService = atomicMetricService;
        this.metricService = metricService;
        this.dataValidator = dataValidator;
    }

    /**
     * 验证提取的指标定义
     */
    public ValidationResult validate(ExtractedMetric metric) {
        ValidationResult result = new ValidationResult();
        result.setValid(true);

        String category = metric.getCategory() != null ? metric.getCategory().name() : null;

        if ("ATOMIC".equalsIgnoreCase(category)) {
            validateAtomicMetric(metric, result);
        } else if ("DERIVED".equalsIgnoreCase(category)) {
            validateDerivedMetric(metric, result);
        } else if ("COMPOSITE".equalsIgnoreCase(category)) {
            validateCompositeMetric(metric, result);
        } else {
            result.addError("UNKNOWN_METRIC_TYPE", "未知的指标类型: " + category);
        }

        return result;
    }

    /**
     * 批量验证指标
     */
    public List<ValidationResult> validateMetrics(List<ExtractedMetric> metrics) {
        return metrics.stream()
            .map(this::validate)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 验证原子指标
     */
    private void validateAtomicMetric(ExtractedMetric metric, ValidationResult result) {
        // 验证业务过程对象类型是否存在
        if (metric.getBusinessProcess() == null || metric.getBusinessProcess().isEmpty()) {
            result.addError("MISSING_BUSINESS_PROCESS", "原子指标必须指定业务过程（对象类型）");
        } else {
            try {
                loader.getObjectType(metric.getBusinessProcess());
            } catch (Loader.NotFoundException e) {
                result.addError("BUSINESS_PROCESS_NOT_FOUND", "业务过程对象类型不存在: " + metric.getBusinessProcess());
            }
        }

        // 验证聚合函数
        if (metric.getAggregationFunction() == null || metric.getAggregationFunction().isEmpty()) {
            result.addError("MISSING_AGGREGATION_FUNCTION", "原子指标必须指定聚合函数");
        } else if (!isValidAggregationFunction(metric.getAggregationFunction())) {
            result.addError("INVALID_AGGREGATION_FUNCTION", "不支持的聚合函数: " + metric.getAggregationFunction() + 
                "，可选值: " + String.join(", ", VALID_AGGREGATION_FUNCTIONS));
        }

        // 验证指标名称
        if (metric.getName() == null || metric.getName().isEmpty()) {
            result.addWarning("MISSING_NAME", "建议提供指标名称");
        } else if (!isValidName(metric.getName())) {
            result.addError("INVALID_NAME", "指标名称格式不正确，只能包含字母、数字、下划线");
        }

        // 验证指标状态
        if (metric.getStatus() != null && !metric.getStatus().isEmpty()) {
            if (!VALID_STATUS.contains(metric.getStatus().toLowerCase())) {
                result.addError("INVALID_STATUS", "无效的指标状态: " + metric.getStatus() + 
                    "，可选值: active, inactive, deprecated");
            }
        }
    }

    /**
     * 验证派生指标
     */
    private void validateDerivedMetric(ExtractedMetric metric, ValidationResult result) {
        // 验证原子指标引用
        if (metric.getAtomicMetricId() == null || metric.getAtomicMetricId().isEmpty()) {
            result.addError("MISSING_ATOMIC_METRIC", "派生指标必须引用原子指标");
        } else {
            try {
                atomicMetricService.getAtomicMetric(metric.getAtomicMetricId());
            } catch (Exception e) {
                result.addError("ATOMIC_METRIC_NOT_FOUND", "引用的原子指标不存在: " + metric.getAtomicMetricId());
            }
        }

        // 验证时间粒度
        if (metric.getTimeGranularity() != null && !metric.getTimeGranularity().isEmpty()) {
            if (!isValidTimeGranularity(metric.getTimeGranularity())) {
                result.addError("INVALID_TIME_GRANULARITY", "无效的时间粒度: " + metric.getTimeGranularity() + 
                    "，可选值: minute, hour, day, week, month, quarter, year");
            }
        }

        // 验证时间维度
        if (metric.getTimeDimension() != null && !metric.getTimeDimension().isEmpty()) {
            if (!isValidFieldName(metric.getTimeDimension())) {
                result.addWarning("INVALID_TIME_DIMENSION", "时间维度字段名格式可能不正确: " + metric.getTimeDimension());
            }
        }

        // 验证维度列表
        if (metric.getDimensions() != null) {
            for (int i = 0; i < metric.getDimensions().size(); i++) {
                String dimension = metric.getDimensions().get(i);
                if (!isValidFieldName(dimension)) {
                    result.addWarning("INVALID_DIMENSION", "维度字段名格式可能不正确: " + dimension);
                }
            }
        }

        // 验证过滤条件
        if (metric.getFilterConditions() != null) {
            validateFilterConditions(metric.getFilterConditions(), result);
        }

        // 验证对比类型
        if (metric.getComparisonType() != null) {
            for (String comparison : metric.getComparisonType()) {
                if (!isValidComparisonType(comparison)) {
                    result.addWarning("INVALID_COMPARISON_TYPE", "可能无效的对比类型: " + comparison);
                }
            }
        }

        // 验证指标名称
        if (metric.getName() == null || metric.getName().isEmpty()) {
            result.addWarning("MISSING_NAME", "建议提供指标名称");
        }
    }

    /**
     * 验证复合指标
     */
    private void validateCompositeMetric(ExtractedMetric metric, ValidationResult result) {
        // 验证公式
        if (metric.getDerivedFormula() == null || metric.getDerivedFormula().isEmpty()) {
            result.addError("MISSING_FORMULA", "复合指标必须提供计算公式");
        } else if (!isValidFormula(metric.getDerivedFormula())) {
            result.addError("INVALID_FORMULA", "公式语法错误: " + metric.getDerivedFormula());
        }

        // 验证基础指标引用
        if (metric.getBaseMetricIds() == null || metric.getBaseMetricIds().isEmpty()) {
            result.addError("MISSING_BASE_METRICS", "复合指标必须引用基础指标");
        } else {
            // 验证所有引用的指标都存在
            for (String metricId : metric.getBaseMetricIds()) {
                try {
                    metricService.getMetricDefinition(metricId);
                } catch (Exception e) {
                    result.addError("BASE_METRIC_NOT_FOUND", "引用的基础指标不存在: " + metricId);
                }
            }
        }

        // 验证指标名称
        if (metric.getName() == null || metric.getName().isEmpty()) {
            result.addWarning("MISSING_NAME", "建议提供指标名称");
        }
    }

    /**
     * 验证过滤条件
     */
    private void validateFilterConditions(Map<String, Object> filterConditions, ValidationResult result) {
        for (Map.Entry<String, Object> entry : filterConditions.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();

            if (!isValidFieldName(field)) {
                result.addWarning("INVALID_FILTER_FIELD", "过滤字段名格式可能不正确: " + field);
            }

            if (value == null) {
                result.addWarning("NULL_FILTER_VALUE", "过滤条件值为空: " + field);
            }
        }
    }

    /**
     * 验证聚合函数有效性
     */
    public boolean isValidAggregationFunction(String function) {
        if (function == null) return false;
        return VALID_AGGREGATION_FUNCTIONS.contains(function.toUpperCase());
    }

    /**
     * 验证时间粒度有效性
     */
    public boolean isValidTimeGranularity(String granularity) {
        if (granularity == null) return false;
        return VALID_TIME_GRANULARITIES.contains(granularity.toLowerCase());
    }

    /**
     * 验证对比类型有效性
     */
    public boolean isValidComparisonType(String comparisonType) {
        if (comparisonType == null) return false;
        return VALID_COMPARISON_TYPES.stream()
            .anyMatch(t -> t.equalsIgnoreCase(comparisonType));
    }

    /**
     * 验证名称格式
     */
    private boolean isValidName(String name) {
        if (name == null || name.isEmpty()) return false;
        Pattern pattern = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
        return pattern.matcher(name).matches();
    }

    /**
     * 验证字段名格式
     */
    private boolean isValidFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) return false;
        Pattern pattern = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");
        return pattern.matcher(fieldName).matches();
    }

    /**
     * 验证公式语法
     */
    private boolean isValidFormula(String formula) {
        if (formula == null || formula.isEmpty()) return false;

        // 检查括号匹配
        int count = 0;
        for (char c : formula.toCharArray()) {
            if (c == '(') count++;
            else if (c == ')') count--;
            if (count < 0) return false;
        }
        if (count != 0) return false;

        // 检查基本的运算符
        String validChars = "[+\\-*/%()^{}0-9.a-zA-Z_\\s\\[\\]]+";
        return formula.matches(validChars);
    }

    /**
     * 将 ExtractedMetric 转换为 AtomicMetric
     */
    public AtomicMetric convertToAtomicMetric(ExtractedMetric extracted) {
        AtomicMetric atomic = new AtomicMetric();
        atomic.setId(extracted.getId());
        atomic.setName(extracted.getName());
        atomic.setDisplayName(extracted.getDisplayName());
        atomic.setDescription(extracted.getDescription());
        atomic.setBusinessProcess(extracted.getBusinessProcess());
        atomic.setAggregationFunction(extracted.getAggregationFunction());
        atomic.setAggregationField(extracted.getAggregationField());
        atomic.setUnit(extracted.getUnit());
        atomic.setStatus(extracted.getStatus() != null ? extracted.getStatus() : "active");
        return atomic;
    }

    /**
     * 将 ExtractedMetric 转换为 MetricDefinition
     */
    public MetricDefinition convertToMetricDefinition(ExtractedMetric extracted) {
        MetricDefinition definition = new MetricDefinition();
        definition.setId(extracted.getId());
        definition.setName(extracted.getName());
        definition.setDisplayName(extracted.getDisplayName());
        definition.setDescription(extracted.getDescription());

        String category = extracted.getCategory() != null ? extracted.getCategory().name().toLowerCase() : "derived";
        definition.setMetricType(category);

        if ("derived".equals(category)) {
            definition.setAtomicMetricId(extracted.getAtomicMetricId());
            definition.setBusinessScope(extracted.getBusinessScope());
            definition.setTimeDimension(extracted.getTimeDimension());
            definition.setTimeGranularity(extracted.getTimeGranularity());
            definition.setDimensions(extracted.getDimensions());
            definition.setFilterConditions(extracted.getFilterConditions());
            definition.setComparisonType(extracted.getComparisonType());
        } else if ("composite".equals(category)) {
            definition.setDerivedFormula(extracted.getDerivedFormula());
            definition.setBaseMetricIds(extracted.getBaseMetricIds());
        }

        definition.setUnit(extracted.getUnit());
        definition.setStatus(extracted.getStatus() != null ? extracted.getStatus() : "active");

        return definition;
    }

    // ==================== 内部类定义 ====================

    public static class ValidationResult {
        private boolean valid = true;
        private List<ValidationError> errors = new ArrayList<>();
        private List<ValidationWarning> warnings = new ArrayList<>();
        private List<ValidationInfo> infos = new ArrayList<>();

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }

        public void addError(String code, String message) {
            valid = false;
            errors.add(new ValidationError(code, message));
        }

        public void addWarning(String code, String message) {
            warnings.add(new ValidationWarning(code, message));
        }

        public void addInfo(String code, String message) {
            infos.add(new ValidationInfo(code, message));
        }

        public List<ValidationError> getErrors() { return errors; }
        public List<ValidationWarning> getWarnings() { return warnings; }
        public List<ValidationInfo> getInfos() { return infos; }

        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }

    public static class ValidationError {
        private final String code;
        private final String message;

        public ValidationError(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }
    }

    public static class ValidationWarning {
        private final String code;
        private final String message;

        public ValidationWarning(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }
    }

    public static class ValidationInfo {
        private final String code;
        private final String message;

        public ValidationInfo(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
}
