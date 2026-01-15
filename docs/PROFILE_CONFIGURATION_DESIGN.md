# 画像配置化功能完整设计（基于 Craft.js + React-Grid-Layout）

> 本文档是画像功能的完整设计方案，采用 Craft.js + React-Grid-Layout 构建可视化编辑器

**技术栈**：
- **前端编辑器**: Craft.js (MIT) - 提供拖拽、组件管理、配置面板
- **布局引擎**: React-Grid-Layout (MIT) - 提供响应式网格布局
- **图表库**: Recharts (MIT) - 提供数据可视化
- **后端框架**: Spring Boot + 现有指标系统

---

## 一、核心设计理念

### 1.1 配置化目标

**问题**：画像结构硬编码，无法灵活调整维度和展示形式

**解决方案**：基于 **Craft.js** 构建可视化编辑器 + **React-Grid-Layout** 实现响应式布局

```
┌─────────────────────────────────────────────┐
│  Craft.js Editor (拖拽 + 组件管理)           │
│    ↓                                        │
│  React-Grid-Layout (网格布局 + 响应式)       │
│    ↓                                        │
│  Profile Widgets (指标卡片/图表/表格)         │
│    ↓                                        │
│  JSON Config (序列化配置，保存到后端)         │
└─────────────────────────────────────────────┘
```

**核心能力**：
- ✅ **Craft.js 拖拽编辑**：从组件库拖拽到画布
- ✅ **网格自动布局**：自动对齐，响应式调整
- ✅ **实时预览**：编辑即所见
- ✅ **配置面板**：右侧属性编辑器
- ✅ **序列化存储**：导出为 JSON 保存到数据库

---

### 1.2 技术架构图

```
┌──────────────────────────────────────────────────────────────────┐
│                    前端层 (React + TypeScript)                    │
├──────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Craft.js Editor (编辑器核心)                            │   │
│  │  - useEditor()      编辑器状态管理                        │   │
│  │  - useNode()        组件节点管理                          │   │
│  │  - Frame            画布容器                             │   │
│  │  - Element          可拖拽元素                           │   │
│  └─────────────────────────────────────────────────────────┘   │
│                          ↓                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  React-Grid-Layout (布局引擎)                            │   │
│  │  - GridLayout       响应式网格布局                        │   │
│  │  - onLayoutChange   布局变化回调                         │   │
│  │  - isDraggable      支持拖拽调整                         │   │
│  │  - isResizable      支持尺寸调整                         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                          ↓                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Profile Widgets (画像组件)                              │   │
│  │  - MetricCardWidget    指标卡片 (数字展示)               │   │
│  │  - ChartWidget         图表 (Recharts)                   │   │
│  │  - TableWidget         表格 (数据列表)                   │   │
│  │  - TextWidget          文本/标题                         │   │
│  └─────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
                          ↓ HTTP API
┌──────────────────────────────────────────────────────────────────┐
│                    后端层 (Spring Boot)                           │
├──────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  ProfileTemplateService (模板管理服务)                   │   │
│  │  - save()        保存 Craft.js 序列化的 JSON             │   │
│  │  - load()        加载模板配置                            │   │
│  │  - validate()    验证配置合法性                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                          ↓                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  ProfileRenderService (画像渲染服务)                     │   │
│  │  - render()      根据模板渲染画像                        │   │
│  │  - fetchData()   执行指标查询和数据聚合                  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                          ↓                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  MetricService + QueryService (已有)                     │   │
│  └─────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

---

## 二、数据模型设计

### 2.1 画像模板数据结构

#### **数据库表设计**

```sql
-- 画像模板表
CREATE TABLE PROFILE_TEMPLATES (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    description TEXT,
    entity_type VARCHAR(50) NOT NULL,     -- Gantry/Vehicle/TollStation
    template_config TEXT NOT NULL,        -- JSON 配置（核心）
    is_system BOOLEAN DEFAULT FALSE,      -- 是否系统模板
    is_public BOOLEAN DEFAULT FALSE,      -- 是否公开
    creator_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(entity_type, name)
);

CREATE INDEX idx_template_entity ON PROFILE_TEMPLATES(entity_type);
CREATE INDEX idx_template_creator ON PROFILE_TEMPLATES(creator_id);

-- 画像模板分享表（可选）
CREATE TABLE PROFILE_TEMPLATE_SHARES (
    id VARCHAR(36) PRIMARY KEY,
    template_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    permission VARCHAR(20) DEFAULT 'read',  -- read/write
    shared_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (template_id) REFERENCES PROFILE_TEMPLATES(id)
);
```

---

#### **模板配置 JSON 结构**

```typescript
// ProfileTemplateConfig 完整定义
interface ProfileTemplateConfig {
  version: string;  // 配置版本号，用于兼容性
  
  // 布局配置
  layout: {
    type: 'grid' | 'flex';  // 布局类型
    columns: number;         // 列数
    gap: number;             // 间距
  };
  
  // 画像组件列表（核心）
  widgets: ProfileWidget[];
  
  // 全局样式
  theme?: {
    primaryColor?: string;
    fontSize?: number;
  };
}

// 画像组件（卡片）
interface ProfileWidget {
  id: string;                    // 组件唯一ID
  type: WidgetType;              // 组件类型
  title: string;                 // 标题
  position: {                    // 位置配置
    row: number;
    col: number;
    width: number;               // 宽度（占几列）
    height: number;              // 高度（px）
  };
  
  // 数据源配置（核心）
  dataSource: DataSourceConfig;
  
  // 可视化配置
  visualization?: VisualizationConfig;
}

// 组件类型
type WidgetType = 
  | 'metric_card'        // 指标卡片（数字展示）
  | 'chart'              // 图表
  | 'table'              // 表格
  | 'text'               // 文本
  | 'divider';           // 分隔符

// 数据源配置
interface DataSourceConfig {
  type: 'metric' | 'query';  // 数据来源类型
  
  // 指标类型数据源
  metricId?: string;         // 指标ID（原子/派生/复合）
  metricQuery?: {            // 指标查询参数
    dimensions?: Record<string, string>;
    timeRange?: {
      start: string;         // 支持占位符：${startDate}
      end: string;           // 支持占位符：${endDate}
    };
  };
  
  // 自定义查询类型数据源
  ontologyQuery?: {          // OntologyQuery 配置
    object: string;
    filter?: any[];
    groupBy?: string[];
    metrics?: any[];
    orderBy?: any[];
    limit?: number;
  };
  
  // 数据转换（可选）
  transform?: {
    type: 'aggregate' | 'filter' | 'sort';
    config: any;
  };
}

// 可视化配置
interface VisualizationConfig {
  // 图表类型（type=chart 时）
  chartType?: 'bar' | 'line' | 'pie' | 'area' | 'scatter';
  
  // 图表配置
  chartConfig?: {
    xAxis?: string;          // X轴字段
    yAxis?: string;          // Y轴字段
    series?: string;         // 系列字段
    color?: string[];        // 颜色配置
    showLegend?: boolean;
    showDataLabel?: boolean;
  };
  
  // 指标卡片配置（type=metric_card 时）
  metricCardConfig?: {
    format?: 'number' | 'currency' | 'percentage';
    precision?: number;
    unit?: string;
    icon?: string;
    trendIndicator?: boolean;  // 是否显示趋势
  };
  
  // 表格配置（type=table 时）
  tableConfig?: {
    columns?: TableColumn[];
    pagination?: boolean;
    pageSize?: number;
  };
}

interface TableColumn {
  field: string;
  title: string;
  width?: number;
  format?: string;
  align?: 'left' | 'center' | 'right';
}
```

---

### 2.2 配置示例

#### **门架画像模板示例**

```json
{
  "version": "1.0",
  "layout": {
    "type": "grid",
    "columns": 4,
    "gap": 16
  },
  "widgets": [
    {
      "id": "widget_1",
      "type": "metric_card",
      "title": "总交易量",
      "position": { "row": 0, "col": 0, "width": 1, "height": 120 },
      "dataSource": {
        "type": "metric",
        "metricId": "gantry_transaction_count",
        "metricQuery": {
          "dimensions": {
            "gantry_id": "${entityId}"
          },
          "timeRange": {
            "start": "${startDate}",
            "end": "${endDate}"
          }
        }
      },
      "visualization": {
        "metricCardConfig": {
          "format": "number",
          "unit": "笔",
          "icon": "📊"
        }
      }
    },
    {
      "id": "widget_2",
      "type": "metric_card",
      "title": "总收入",
      "position": { "row": 0, "col": 1, "width": 1, "height": 120 },
      "dataSource": {
        "type": "metric",
        "metricId": "gantry_total_revenue",
        "metricQuery": {
          "dimensions": {
            "gantry_id": "${entityId}"
          },
          "timeRange": {
            "start": "${startDate}",
            "end": "${endDate}"
          }
        }
      },
      "visualization": {
        "metricCardConfig": {
          "format": "currency",
          "precision": 2,
          "unit": "元",
          "icon": "💰"
        }
      }
    },
    {
      "id": "widget_3",
      "type": "chart",
      "title": "24小时交易分布",
      "position": { "row": 1, "col": 0, "width": 2, "height": 300 },
      "dataSource": {
        "type": "query",
        "ontologyQuery": {
          "object": "GantryTransaction",
          "filter": [
            ["=", "gantry_id", "${entityId}"],
            [">=", "trans_time", "${startDate}"],
            ["<=", "trans_time", "${endDate}"]
          ],
          "select": ["trans_time"],
          "metrics": [["count", "*", "count"]],
          "orderBy": [{"field": "trans_time", "direction": "ASC"}]
        },
        "transform": {
          "type": "aggregate",
          "config": {
            "groupBy": "hour",
            "extractFrom": "trans_time"
          }
        }
      },
      "visualization": {
        "chartType": "bar",
        "chartConfig": {
          "xAxis": "hour",
          "yAxis": "count",
          "color": ["#3b82f6"],
          "showLegend": false,
          "showDataLabel": true
        }
      }
    },
    {
      "id": "widget_4",
      "type": "chart",
      "title": "车型分布",
      "position": { "row": 1, "col": 2, "width": 2, "height": 300 },
      "dataSource": {
        "type": "query",
        "ontologyQuery": {
          "object": "GantryTransaction",
          "filter": [
            ["=", "gantry_id", "${entityId}"],
            [">=", "trans_time", "${startDate}"],
            ["<=", "trans_time", "${endDate}"]
          ],
          "groupBy": ["snapshot_vehicle_type"],
          "metrics": [
            ["count", "*", "count"],
            ["sum", "fee", "total_fee"]
          ]
        }
      },
      "visualization": {
        "chartType": "pie",
        "chartConfig": {
          "series": "snapshot_vehicle_type",
          "value": "count",
          "showLegend": true
        }
      }
    }
  ]
}
```

---

## 三、后端服务设计

### 3.1 模板管理服务

```java
// src/main/java/com/mypalantir/service/ProfileTemplateService.java
package com.mypalantir.service;

@Service
public class ProfileTemplateService {
    
    private final ProfileTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 创建画像模板
     */
    public ProfileTemplate createTemplate(CreateTemplateRequest request) {
        // 1. 验证配置
        validateTemplateConfig(request.getTemplateConfig());
        
        // 2. 保存模板
        ProfileTemplate template = new ProfileTemplate();
        template.setId(UUID.randomUUID().toString());
        template.setName(request.getName());
        template.setDisplayName(request.getDisplayName());
        template.setEntityType(request.getEntityType());
        template.setTemplateConfig(objectMapper.writeValueAsString(request.getTemplateConfig()));
        template.setCreatorId(getCurrentUserId());
        
        return templateRepository.save(template);
    }
    
    /**
     * 更新画像模板
     */
    public ProfileTemplate updateTemplate(String templateId, UpdateTemplateRequest request) {
        ProfileTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new NotFoundException("Template not found"));
        
        // 验证配置
        if (request.getTemplateConfig() != null) {
            validateTemplateConfig(request.getTemplateConfig());
            template.setTemplateConfig(objectMapper.writeValueAsString(request.getTemplateConfig()));
        }
        
        if (request.getName() != null) {
            template.setName(request.getName());
        }
        if (request.getDisplayName() != null) {
            template.setDisplayName(request.getDisplayName());
        }
        
        template.setUpdatedAt(LocalDateTime.now());
        return templateRepository.save(template);
    }
    
    /**
     * 获取模板列表
     */
    public List<ProfileTemplate> listTemplates(String entityType, Boolean isPublic) {
        if (isPublic != null && isPublic) {
            return templateRepository.findByEntityTypeAndIsPublic(entityType, true);
        }
        
        // 返回系统模板 + 用户自己的模板
        String userId = getCurrentUserId();
        return templateRepository.findByEntityTypeAndCreatorOrPublic(entityType, userId);
    }
    
    /**
     * 获取模板详情
     */
    public ProfileTemplateDetail getTemplateDetail(String templateId) {
        ProfileTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new NotFoundException("Template not found"));
        
        ProfileTemplateConfig config = objectMapper.readValue(
            template.getTemplateConfig(),
            ProfileTemplateConfig.class
        );
        
        ProfileTemplateDetail detail = new ProfileTemplateDetail();
        detail.setId(template.getId());
        detail.setName(template.getName());
        detail.setDisplayName(template.getDisplayName());
        detail.setEntityType(template.getEntityType());
        detail.setConfig(config);
        
        return detail;
    }
    
    /**
     * 验证模板配置
     */
    private void validateTemplateConfig(ProfileTemplateConfig config) {
        // 1. 验证版本
        if (config.getVersion() == null || !config.getVersion().equals("1.0")) {
            throw new ValidationException("Unsupported config version");
        }
        
        // 2. 验证布局
        if (config.getLayout() == null || config.getLayout().getColumns() < 1) {
            throw new ValidationException("Invalid layout config");
        }
        
        // 3. 验证组件
        if (config.getWidgets() == null || config.getWidgets().isEmpty()) {
            throw new ValidationException("At least one widget is required");
        }
        
        for (ProfileWidget widget : config.getWidgets()) {
            validateWidget(widget);
        }
    }
    
    /**
     * 验证单个组件
     */
    private void validateWidget(ProfileWidget widget) {
        // 验证数据源
        DataSourceConfig dataSource = widget.getDataSource();
        if (dataSource == null) {
            throw new ValidationException("Widget must have a data source");
        }
        
        if ("metric".equals(dataSource.getType())) {
            // 验证指标ID是否存在
            if (dataSource.getMetricId() == null) {
                throw new ValidationException("Metric ID is required for metric data source");
            }
            // TODO: 验证指标是否存在
        } else if ("query".equals(dataSource.getType())) {
            // 验证OntologyQuery
            if (dataSource.getOntologyQuery() == null) {
                throw new ValidationException("OntologyQuery is required for query data source");
            }
            // TODO: 验证查询是否合法
        }
    }
    
    /**
     * 删除模板
     */
    public void deleteTemplate(String templateId) {
        ProfileTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new NotFoundException("Template not found"));
        
        // 只能删除自己创建的模板
        if (!template.getCreatorId().equals(getCurrentUserId())) {
            throw new ForbiddenException("Cannot delete others' templates");
        }
        
        templateRepository.delete(template);
    }
    
    private String getCurrentUserId() {
        // TODO: 从安全上下文获取当前用户ID
        return "system";
    }
}
```

---

### 3.2 画像渲染服务

```java
// src/main/java/com/mypalantir/service/ProfileRenderService.java
package com.mypalantir.service;

@Service
public class ProfileRenderService {
    
    private final ProfileTemplateService templateService;
    private final MetricCalculator metricCalculator;
    private final QueryService queryService;
    
    /**
     * 根据模板渲染画像
     * 
     * @param templateId 模板ID
     * @param entityId 实体ID（如 gantryId）
     * @param params 参数（如 startDate, endDate）
     * @return 渲染结果
     */
    public ProfileRenderResult renderProfile(
        String templateId,
        String entityId,
        Map<String, String> params
    ) throws Exception {
        // 1. 获取模板
        ProfileTemplateDetail template = templateService.getTemplateDetail(templateId);
        ProfileTemplateConfig config = template.getConfig();
        
        // 2. 准备参数上下文
        Map<String, String> context = new HashMap<>();
        context.put("entityId", entityId);
        context.putAll(params);  // startDate, endDate 等
        
        // 3. 渲染所有组件
        List<WidgetRenderResult> widgetResults = new ArrayList<>();
        for (ProfileWidget widget : config.getWidgets()) {
            try {
                WidgetRenderResult result = renderWidget(widget, context);
                widgetResults.add(result);
            } catch (Exception e) {
                log.error("Failed to render widget: {}", widget.getId(), e);
                // 记录错误，继续渲染其他组件
                WidgetRenderResult errorResult = new WidgetRenderResult();
                errorResult.setWidgetId(widget.getId());
                errorResult.setError(e.getMessage());
                widgetResults.add(errorResult);
            }
        }
        
        // 4. 组装结果
        ProfileRenderResult result = new ProfileRenderResult();
        result.setTemplateId(templateId);
        result.setEntityId(entityId);
        result.setLayout(config.getLayout());
        result.setWidgets(widgetResults);
        
        return result;
    }
    
    /**
     * 渲染单个组件
     */
    private WidgetRenderResult renderWidget(
        ProfileWidget widget,
        Map<String, String> context
    ) throws Exception {
        WidgetRenderResult result = new WidgetRenderResult();
        result.setWidgetId(widget.getId());
        result.setType(widget.getType());
        result.setTitle(widget.getTitle());
        result.setPosition(widget.getPosition());
        
        // 根据数据源类型获取数据
        DataSourceConfig dataSource = widget.getDataSource();
        Object data = fetchData(dataSource, context);
        
        // 应用数据转换（如果有）
        if (dataSource.getTransform() != null) {
            data = transformData(data, dataSource.getTransform());
        }
        
        result.setData(data);
        result.setVisualization(widget.getVisualization());
        
        return result;
    }
    
    /**
     * 获取数据
     */
    private Object fetchData(DataSourceConfig dataSource, Map<String, String> context) throws Exception {
        if ("metric".equals(dataSource.getType())) {
            return fetchMetricData(dataSource, context);
        } else if ("query".equals(dataSource.getType())) {
            return fetchQueryData(dataSource, context);
        }
        throw new IllegalArgumentException("Unknown data source type: " + dataSource.getType());
    }
    
    /**
     * 获取指标数据
     */
    private Object fetchMetricData(DataSourceConfig dataSource, Map<String, String> context) throws Exception {
        String metricId = dataSource.getMetricId();
        MetricQueryConfig metricQuery = dataSource.getMetricQuery();
        
        // 构建 MetricQuery
        MetricQuery query = new MetricQuery();
        query.setMetricId(metricId);
        
        // 替换占位符
        if (metricQuery != null) {
            if (metricQuery.getDimensions() != null) {
                Map<String, Object> dimensions = new HashMap<>();
                for (Map.Entry<String, String> entry : metricQuery.getDimensions().entrySet()) {
                    String value = replacePlaceholders(entry.getValue(), context);
                    dimensions.put(entry.getKey(), value);
                }
                query.setDimensions(dimensions);
            }
            
            if (metricQuery.getTimeRange() != null) {
                String start = replacePlaceholders(metricQuery.getTimeRange().getStart(), context);
                String end = replacePlaceholders(metricQuery.getTimeRange().getEnd(), context);
                query.setTimeRange(new MetricQuery.TimeRange(start, end));
            }
        }
        
        // 计算指标
        // 先尝试作为派生/复合指标
        try {
            MetricDefinition metricDef = metricService.getMetricDefinition(metricId);
            return metricCalculator.calculateMetric(metricDef, query);
        } catch (Exception e) {
            // 尝试作为原子指标
            AtomicMetric atomicMetric = atomicMetricService.getAtomicMetric(metricId);
            return metricCalculator.calculateAtomicMetric(atomicMetric, query);
        }
    }
    
    /**
     * 获取查询数据
     */
    private Object fetchQueryData(DataSourceConfig dataSource, Map<String, String> context) throws Exception {
        Map<String, Object> ontologyQuery = dataSource.getOntologyQuery();
        
        // 替换占位符
        Map<String, Object> processedQuery = replacePlaceholdersInQuery(ontologyQuery, context);
        
        // 执行查询
        return queryService.executeQuery(processedQuery);
    }
    
    /**
     * 替换占位符 ${xxx}
     */
    private String replacePlaceholders(String template, Map<String, String> context) {
        if (template == null) return null;
        
        String result = template;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue());
        }
        return result;
    }
    
    /**
     * 在查询中替换占位符
     */
    private Map<String, Object> replacePlaceholdersInQuery(
        Map<String, Object> query,
        Map<String, String> context
    ) {
        // 深度递归替换所有字符串值中的占位符
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                result.put(entry.getKey(), replacePlaceholders((String) value, context));
            } else if (value instanceof List) {
                result.put(entry.getKey(), replaceInList((List<?>) value, context));
            } else if (value instanceof Map) {
                result.put(entry.getKey(), replacePlaceholdersInQuery((Map<String, Object>) value, context));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }
    
    private List<?> replaceInList(List<?> list, Map<String, String> context) {
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String) {
                result.add(replacePlaceholders((String) item, context));
            } else if (item instanceof List) {
                result.add(replaceInList((List<?>) item, context));
            } else if (item instanceof Map) {
                result.add(replacePlaceholdersInQuery((Map<String, Object>) item, context));
            } else {
                result.add(item);
            }
        }
        return result;
    }
    
    /**
     * 数据转换
     */
    private Object transformData(Object data, TransformConfig transform) {
        // 根据转换类型处理数据
        String type = transform.getType();
        
        if ("aggregate".equals(type)) {
            // 聚合转换（如按小时聚合）
            return aggregateTransform(data, transform.getConfig());
        } else if ("filter".equals(type)) {
            // 过滤转换
            return filterTransform(data, transform.getConfig());
        } else if ("sort".equals(type)) {
            // 排序转换
            return sortTransform(data, transform.getConfig());
        }
        
        return data;
    }
    
    private Object aggregateTransform(Object data, Map<String, Object> config) {
        // 实现聚合逻辑（如提取小时并分组）
        // TODO: 实现
        return data;
    }
    
    private Object filterTransform(Object data, Map<String, Object> config) {
        // 实现过滤逻辑
        return data;
    }
    
    private Object sortTransform(Object data, Map<String, Object> config) {
        // 实现排序逻辑
        return data;
    }
}
```

---

### 3.3 API 接口

```java
// src/main/java/com/mypalantir/controller/ProfileTemplateController.java
package com.mypalantir.controller;

@RestController
@RequestMapping("/api/v1/profile-templates")
public class ProfileTemplateController {
    
    private final ProfileTemplateService templateService;
    private final ProfileRenderService renderService;
    
    /**
     * 创建模板
     */
    @PostMapping
    public ApiResponse<ProfileTemplate> createTemplate(@RequestBody CreateTemplateRequest request) {
        ProfileTemplate template = templateService.createTemplate(request);
        return ApiResponse.success(template);
    }
    
    /**
     * 更新模板
     */
    @PutMapping("/{templateId}")
    public ApiResponse<ProfileTemplate> updateTemplate(
        @PathVariable String templateId,
        @RequestBody UpdateTemplateRequest request
    ) {
        ProfileTemplate template = templateService.updateTemplate(templateId, request);
        return ApiResponse.success(template);
    }
    
    /**
     * 获取模板列表
     */
    @GetMapping
    public ApiResponse<List<ProfileTemplate>> listTemplates(
        @RequestParam String entityType,
        @RequestParam(required = false) Boolean isPublic
    ) {
        List<ProfileTemplate> templates = templateService.listTemplates(entityType, isPublic);
        return ApiResponse.success(templates);
    }
    
    /**
     * 获取模板详情
     */
    @GetMapping("/{templateId}")
    public ApiResponse<ProfileTemplateDetail> getTemplate(@PathVariable String templateId) {
        ProfileTemplateDetail detail = templateService.getTemplateDetail(templateId);
        return ApiResponse.success(detail);
    }
    
    /**
     * 删除模板
     */
    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> deleteTemplate(@PathVariable String templateId) {
        templateService.deleteTemplate(templateId);
        return ApiResponse.success(null);
    }
    
    /**
     * 渲染画像（根据模板）
     */
    @PostMapping("/{templateId}/render")
    public ApiResponse<ProfileRenderResult> renderProfile(
        @PathVariable String templateId,
        @RequestBody RenderProfileRequest request
    ) throws Exception {
        ProfileRenderResult result = renderService.renderProfile(
            templateId,
            request.getEntityId(),
            request.getParams()
        );
        return ApiResponse.success(result);
    }
    
    /**
     * 验证模板配置
     */
    @PostMapping("/validate")
    public ApiResponse<ValidationResult> validateTemplate(@RequestBody ProfileTemplateConfig config) {
        try {
            templateService.validateTemplateConfig(config);
            return ApiResponse.success(new ValidationResult(true, "Valid"));
        } catch (ValidationException e) {
            return ApiResponse.success(new ValidationResult(false, e.getMessage()));
        }
    }
}
```

---

## 四、前端实现设计

### 4.1 画像配置器页面

```typescript
// web/src/pages/ProfileTemplateBuilder.tsx
import React, { useState, useEffect } from 'react';
import { DndProvider, useDrag, useDrop } from 'react-dnd';
import { HTML5Backend } from 'react-dnd-html5-backend';

const ProfileTemplateBuilder: React.FC = () => {
  const [template, setTemplate] = useState<ProfileTemplateConfig>({
    version: '1.0',
    layout: { type: 'grid', columns: 4, gap: 16 },
    widgets: []
  });
  
  const [selectedWidget, setSelectedWidget] = useState<ProfileWidget | null>(null);
  const [availableMetrics, setAvailableMetrics] = useState<any[]>([]);

  // 加载可用指标列表
  useEffect(() => {
    loadMetrics();
  }, []);

  // 添加组件
  const addWidget = (type: WidgetType) => {
    const newWidget: ProfileWidget = {
      id: `widget_${Date.now()}`,
      type,
      title: `新组件`,
      position: {
        row: Math.floor(template.widgets.length / 4),
        col: template.widgets.length % 4,
        width: 1,
        height: type === 'metric_card' ? 120 : 300
      },
      dataSource: {
        type: 'metric',
        metricId: ''
      }
    };
    
    setTemplate({
      ...template,
      widgets: [...template.widgets, newWidget]
    });
    setSelectedWidget(newWidget);
  };

  // 更新组件
  const updateWidget = (widgetId: string, updates: Partial<ProfileWidget>) => {
    setTemplate({
      ...template,
      widgets: template.widgets.map(w => 
        w.id === widgetId ? { ...w, ...updates } : w
      )
    });
  };

  // 删除组件
  const deleteWidget = (widgetId: string) => {
    setTemplate({
      ...template,
      widgets: template.widgets.filter(w => w.id !== widgetId)
    });
    if (selectedWidget?.id === widgetId) {
      setSelectedWidget(null);
    }
  };

  // 保存模板
  const saveTemplate = async () => {
    try {
      await profileTemplateApi.createTemplate({
        name: 'my_gantry_profile',
        displayName: '门架画像',
        entityType: 'Gantry',
        templateConfig: template
      });
      alert('模板保存成功！');
    } catch (error) {
      alert('保存失败: ' + error.message);
    }
  };

  return (
    <DndProvider backend={HTML5Backend}>
      <div className="flex h-screen">
        {/* 左侧：组件库 */}
        <div className="w-64 bg-gray-50 border-r p-4">
          <h2 className="text-lg font-semibold mb-4">组件库</h2>
          <div className="space-y-2">
            <ComponentPalette
              type="metric_card"
              label="指标卡片"
              icon="📊"
              onAdd={() => addWidget('metric_card')}
            />
            <ComponentPalette
              type="chart"
              label="图表"
              icon="📈"
              onAdd={() => addWidget('chart')}
            />
            <ComponentPalette
              type="table"
              label="表格"
              icon="📋"
              onAdd={() => addWidget('table')}
            />
          </div>
        </div>

        {/* 中间：画布 */}
        <div className="flex-1 p-6 overflow-auto bg-gray-100">
          <div className="mb-4 flex justify-between items-center">
            <h1 className="text-2xl font-bold">画像配置器</h1>
            <div className="space-x-2">
              <button
                onClick={saveTemplate}
                className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
              >
                保存模板
              </button>
            </div>
          </div>

          {/* 画布网格 */}
          <div className="bg-white rounded-lg shadow p-4">
            <div
              className="grid gap-4"
              style={{
                gridTemplateColumns: `repeat(${template.layout.columns}, 1fr)`
              }}
            >
              {template.widgets.map(widget => (
                <WidgetCard
                  key={widget.id}
                  widget={widget}
                  isSelected={selectedWidget?.id === widget.id}
                  onSelect={() => setSelectedWidget(widget)}
                  onDelete={() => deleteWidget(widget.id)}
                  style={{
                    gridColumn: `span ${widget.position.width}`,
                    height: widget.position.height
                  }}
                />
              ))}
            </div>
          </div>
        </div>

        {/* 右侧：属性编辑器 */}
        <div className="w-80 bg-gray-50 border-l p-4 overflow-auto">
          {selectedWidget ? (
            <WidgetEditor
              widget={selectedWidget}
              availableMetrics={availableMetrics}
              onChange={(updates) => updateWidget(selectedWidget.id, updates)}
            />
          ) : (
            <div className="text-center text-gray-500 mt-10">
              选择一个组件以编辑属性
            </div>
          )}
        </div>
      </div>
    </DndProvider>
  );
};

// 组件面板项
const ComponentPalette: React.FC<{
  type: WidgetType;
  label: string;
  icon: string;
  onAdd: () => void;
}> = ({ type, label, icon, onAdd }) => (
  <div
    onClick={onAdd}
    className="p-3 bg-white rounded border border-gray-200 hover:border-blue-500 cursor-pointer transition-colors"
  >
    <div className="flex items-center space-x-2">
      <span className="text-2xl">{icon}</span>
      <span className="text-sm font-medium">{label}</span>
    </div>
  </div>
);

// 组件卡片
const WidgetCard: React.FC<{
  widget: ProfileWidget;
  isSelected: boolean;
  onSelect: () => void;
  onDelete: () => void;
  style: React.CSSProperties;
}> = ({ widget, isSelected, onSelect, onDelete, style }) => (
  <div
    onClick={onSelect}
    style={style}
    className={`
      relative p-4 bg-white rounded border-2 cursor-pointer
      ${isSelected ? 'border-blue-500' : 'border-gray-200'}
      hover:border-blue-400 transition-colors
    `}
  >
    <div className="flex justify-between items-start mb-2">
      <h3 className="font-semibold text-sm">{widget.title}</h3>
      <button
        onClick={(e) => {
          e.stopPropagation();
          onDelete();
        }}
        className="text-red-500 hover:text-red-700"
      >
        ×
      </button>
    </div>
    <div className="text-xs text-gray-500">
      类型: {widget.type}
    </div>
    {widget.dataSource.metricId && (
      <div className="text-xs text-gray-500 mt-1">
        指标: {widget.dataSource.metricId}
      </div>
    )}
  </div>
);

// 属性编辑器
const WidgetEditor: React.FC<{
  widget: ProfileWidget;
  availableMetrics: any[];
  onChange: (updates: Partial<ProfileWidget>) => void;
}> = ({ widget, availableMetrics, onChange }) => {
  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold mb-4">组件属性</h2>

      {/* 基础属性 */}
      <div>
        <label className="block text-sm font-medium mb-1">标题</label>
        <input
          type="text"
          value={widget.title}
          onChange={(e) => onChange({ title: e.target.value })}
          className="w-full p-2 border rounded text-sm"
        />
      </div>

      {/* 数据源配置 */}
      <div>
        <label className="block text-sm font-medium mb-1">数据源类型</label>
        <select
          value={widget.dataSource.type}
          onChange={(e) => onChange({
            dataSource: {
              ...widget.dataSource,
              type: e.target.value as 'metric' | 'query'
            }
          })}
          className="w-full p-2 border rounded text-sm"
        >
          <option value="metric">指标</option>
          <option value="query">自定义查询</option>
        </select>
      </div>

      {/* 指标选择 */}
      {widget.dataSource.type === 'metric' && (
        <div>
          <label className="block text-sm font-medium mb-1">选择指标</label>
          <select
            value={widget.dataSource.metricId || ''}
            onChange={(e) => onChange({
              dataSource: {
                ...widget.dataSource,
                metricId: e.target.value
              }
            })}
            className="w-full p-2 border rounded text-sm"
          >
            <option value="">请选择指标</option>
            {availableMetrics.map(metric => (
              <option key={metric.id} value={metric.id}>
                {metric.display_name || metric.name}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* 可视化配置 */}
      {widget.type === 'chart' && (
        <div>
          <label className="block text-sm font-medium mb-1">图表类型</label>
          <select
            value={widget.visualization?.chartType || 'bar'}
            onChange={(e) => onChange({
              visualization: {
                ...widget.visualization,
                chartType: e.target.value as any
              }
            })}
            className="w-full p-2 border rounded text-sm"
          >
            <option value="bar">柱状图</option>
            <option value="line">折线图</option>
            <option value="pie">饼图</option>
            <option value="area">面积图</option>
          </select>
        </div>
      )}

      {/* 布局配置 */}
      <div>
        <label className="block text-sm font-medium mb-1">宽度（占列数）</label>
        <input
          type="number"
          min="1"
          max="4"
          value={widget.position.width}
          onChange={(e) => onChange({
            position: {
              ...widget.position,
              width: parseInt(e.target.value)
            }
          })}
          className="w-full p-2 border rounded text-sm"
        />
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">高度（px）</label>
        <input
          type="number"
          min="100"
          step="10"
          value={widget.position.height}
          onChange={(e) => onChange({
            position: {
              ...widget.position,
              height: parseInt(e.target.value)
            }
          })}
          className="w-full p-2 border rounded text-sm"
        />
      </div>
    </div>
  );
};

export default ProfileTemplateBuilder;
```

---

### 4.2 画像查看器页面

```typescript
// web/src/pages/ProfileViewer.tsx
import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';

const ProfileViewer: React.FC = () => {
  const { templateId } = useParams<{ templateId: string }>();
  const [entityId, setEntityId] = useState('');
  const [startDate, setStartDate] = useState('2024-01-01');
  const [endDate, setEndDate] = useState('2024-12-31');
  const [renderResult, setRenderResult] = useState<ProfileRenderResult | null>(null);
  const [loading, setLoading] = useState(false);

  // 渲染画像
  const renderProfile = async () => {
    if (!entityId) {
      alert('请选择实体');
      return;
    }

    setLoading(true);
    try {
      const result = await profileTemplateApi.renderProfile(templateId, {
        entityId,
        params: { startDate, endDate }
      });
      setRenderResult(result);
    } catch (error) {
      alert('渲染失败: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container mx-auto p-6">
      {/* 查询条件 */}
      <div className="bg-white rounded-lg shadow p-6 mb-6">
        <div className="grid grid-cols-4 gap-4">
          <div>
            <label className="block text-sm font-medium mb-2">实体ID</label>
            <input
              type="text"
              value={entityId}
              onChange={(e) => setEntityId(e.target.value)}
              className="w-full p-2 border rounded"
              placeholder="输入门架ID"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-2">开始日期</label>
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="w-full p-2 border rounded"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-2">结束日期</label>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="w-full p-2 border rounded"
            />
          </div>
          <div className="flex items-end">
            <button
              onClick={renderProfile}
              disabled={loading}
              className="w-full px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
            >
              {loading ? '加载中...' : '查看画像'}
            </button>
          </div>
        </div>
      </div>

      {/* 画像展示 */}
      {renderResult && (
        <div
          className="grid gap-4"
          style={{
            gridTemplateColumns: `repeat(${renderResult.layout.columns}, 1fr)`
          }}
        >
          {renderResult.widgets.map(widget => (
            <WidgetRenderer
              key={widget.widgetId}
              widget={widget}
            />
          ))}
        </div>
      )}
    </div>
  );
};

// 组件渲染器
const WidgetRenderer: React.FC<{ widget: WidgetRenderResult }> = ({ widget }) => {
  if (widget.error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded p-4">
        <p className="text-red-800">加载失败: {widget.error}</p>
      </div>
    );
  }

  return (
    <div
      className="bg-white rounded-lg shadow p-6"
      style={{
        gridColumn: `span ${widget.position.width}`,
        height: widget.position.height
      }}
    >
      <h3 className="text-lg font-semibold mb-4">{widget.title}</h3>
      
      {widget.type === 'metric_card' && (
        <MetricCardRenderer data={widget.data} config={widget.visualization} />
      )}
      
      {widget.type === 'chart' && (
        <ChartRenderer data={widget.data} config={widget.visualization} />
      )}
      
      {widget.type === 'table' && (
        <TableRenderer data={widget.data} config={widget.visualization} />
      )}
    </div>
  );
};

export default ProfileViewer;
```

---

## 五、总结

### 配置化优势

1. **灵活性**：用户可自定义画像结构，无需修改代码
2. **可复用**：模板可分享和复制，降低配置成本
3. **可扩展**：新增组件类型只需扩展配置规范
4. **可维护**：配置与代码分离，便于调整

### 实施路径

**Phase 1**：模板管理基础能力（1周）
- 模板CRUD API
- 简单的模板编辑器

**Phase 2**：可视化配置器（2周）
- 拖拽式编辑器
- 实时预览

**Phase 3**：高级功能（1-2周）
- 模板市场
- 模板导入导出
- 权限管理

---

**文档版本**: v1.1  
**更新日期**: 2026-01-15  
**作者**: Qoder
