# 画像功能实现指南（基于 Craft.js + React-Grid-Layout）

> 完整的前后端实现方案，采用开源可视化编辑器构建灵活的画像配置系统

**技术选型**：
- **Craft.js** (MIT): 拖拽编辑器核心
- **React-Grid-Layout** (MIT): 响应式网格布局
- **Recharts** (MIT): 数据可视化
- **Spring Boot**: 后端服务

---

## 一、前端实现方案

### 1.1 安装依赖

```bash
cd web
npm install @craftjs/core @craftjs/utils
npm install react-grid-layout
npm install recharts
npm install react-grid-layout/css/styles.css
npm install react-resizable/css/styles.css
```

### 1.2 目录结构

```
web/src/
├── pages/
│   ├── ProfileTemplateEditor.tsx      # 画像编辑器页面
│   ├── ProfileViewer.tsx              # 画像查看页面
│   └── ProfileTemplateList.tsx        # 模板列表页面
├── components/
│   └── profile-editor/
│       ├── Editor.tsx                 # Craft.js 编辑器容器
│       ├── Toolbox.tsx                # 左侧组件工具箱
│       ├── Canvas.tsx                 # 中间画布区域
│       ├── SettingsPanel.tsx          # 右侧属性编辑面板
│       └── widgets/
│           ├── MetricCardWidget.tsx   # 指标卡片组件
│           ├── ChartWidget.tsx        # 图表组件
│           ├── TableWidget.tsx        # 表格组件
│           ├── TextWidget.tsx         # 文本组件
│           └── index.ts               # 组件注册
├── api/
│   └── profile-template.ts            # 模板 API 接口
└── types/
    └── profile.ts                     # 类型定义
```

---

## 二、核心实现步骤

### Step 1: 类型定义

```typescript
// web/src/types/profile.ts

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

// 模板配置（Craft.js 序列化格式）
export interface ProfileTemplate {
  id: string;
  name: string;
  displayName: string;
  entityType: string;  // Gantry/Vehicle
  craftState: string;  // Craft.js 序列化的 JSON
  gridLayout?: GridLayout[];  // React-Grid-Layout 配置
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
```

---

### Step 2: 画像组件开发

#### **2.1 指标卡片组件**

```typescript
// web/src/components/profile-editor/widgets/MetricCardWidget.tsx
import React, { useEffect, useState } from 'react';
import { useNode, UserComponent } from '@craftjs/core';
import { metricApi } from '../../../api/metric';

export interface MetricCardWidgetProps {
  metricId?: string;
  title?: string;
  format?: 'number' | 'currency' | 'percentage';
  unit?: string;
  icon?: string;
  precision?: number;
}

export const MetricCardWidget: UserComponent<MetricCardWidgetProps> = (props) => {
  const {
    connectors: { connect, drag },
    selected,
    actions: { setProp },
  } = useNode((state) => ({
    selected: state.events.selected,
  }));

  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  // 预览模式：实时获取数据
  useEffect(() => {
    if (props.metricId) {
      fetchMetricData();
    }
  }, [props.metricId]);

  const fetchMetricData = async () => {
    setLoading(true);
    try {
      // TODO: 获取上下文参数（entityId, startDate, endDate）
      const result = await metricApi.calculate(props.metricId!, {
        dimensions: {},
        timeRange: { start: '2024-01-01', end: '2024-12-31' }
      });
      setData(result);
    } catch (error) {
      console.error('Failed to fetch metric data:', error);
    } finally {
      setLoading(false);
    }
  };

  const formatValue = (value: number) => {
    if (!value) return '-';
    
    switch (props.format) {
      case 'currency':
        return `¥${value.toFixed(props.precision || 2)}`;
      case 'percentage':
        return `${(value * 100).toFixed(props.precision || 1)}%`;
      default:
        return value.toLocaleString();
    }
  };

  return (
    <div
      ref={(ref) => connect(drag(ref!))}
      className={`
        bg-white rounded-lg shadow p-6 border-2 transition-colors
        ${selected ? 'border-blue-500' : 'border-transparent'}
        hover:border-blue-300
      `}
    >
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm text-gray-600">{props.title || '指标卡片'}</span>
        {props.icon && <span className="text-2xl">{props.icon}</span>}
      </div>
      
      <div className="flex items-baseline">
        {loading ? (
          <div className="text-gray-400">加载中...</div>
        ) : (
          <>
            <span className="text-3xl font-bold text-gray-900">
              {data?.results?.[0]?.value 
                ? formatValue(data.results[0].value)
                : '-'}
            </span>
            {props.unit && (
              <span className="ml-2 text-gray-600">{props.unit}</span>
            )}
          </>
        )}
      </div>
    </div>
  );
};

// 配置面板
MetricCardWidget.craft = {
  displayName: '指标卡片',
  props: {
    title: '指标标题',
    format: 'number',
    precision: 0,
  },
  related: {
    settings: MetricCardSettings,
  },
};

// 属性编辑器
const MetricCardSettings = () => {
  const {
    metricId,
    title,
    format,
    unit,
    icon,
    precision,
    actions: { setProp },
  } = useNode((node) => ({
    metricId: node.data.props.metricId,
    title: node.data.props.title,
    format: node.data.props.format,
    unit: node.data.props.unit,
    icon: node.data.props.icon,
    precision: node.data.props.precision,
  }));

  const [metrics, setMetrics] = useState<any[]>([]);

  useEffect(() => {
    loadMetrics();
  }, []);

  const loadMetrics = async () => {
    try {
      const atomicMetrics = await metricApi.listAtomicMetrics();
      const derivedMetrics = await metricApi.listMetricDefinitions();
      setMetrics([...atomicMetrics, ...derivedMetrics]);
    } catch (error) {
      console.error('Failed to load metrics:', error);
    }
  };

  return (
    <div className="space-y-4">
      <div>
        <label className="block text-sm font-medium mb-1">标题</label>
        <input
          type="text"
          value={title || ''}
          onChange={(e) => setProp((props: any) => (props.title = e.target.value))}
          className="w-full p-2 border rounded text-sm"
        />
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">选择指标</label>
        <select
          value={metricId || ''}
          onChange={(e) => setProp((props: any) => (props.metricId = e.target.value))}
          className="w-full p-2 border rounded text-sm"
        >
          <option value="">请选择指标</option>
          {metrics.map((m) => (
            <option key={m.id} value={m.id}>
              {m.display_name || m.name}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">格式</label>
        <select
          value={format || 'number'}
          onChange={(e) => setProp((props: any) => (props.format = e.target.value))}
          className="w-full p-2 border rounded text-sm"
        >
          <option value="number">数字</option>
          <option value="currency">货币</option>
          <option value="percentage">百分比</option>
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">单位</label>
        <input
          type="text"
          value={unit || ''}
          onChange={(e) => setProp((props: any) => (props.unit = e.target.value))}
          className="w-full p-2 border rounded text-sm"
          placeholder="如：元、次、个"
        />
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">图标 (Emoji)</label>
        <input
          type="text"
          value={icon || ''}
          onChange={(e) => setProp((props: any) => (props.icon = e.target.value))}
          className="w-full p-2 border rounded text-sm"
          placeholder="如：📊 💰 🚗"
        />
      </div>

      {(format === 'currency' || format === 'percentage') && (
        <div>
          <label className="block text-sm font-medium mb-1">精度（小数位）</label>
          <input
            type="number"
            min="0"
            max="4"
            value={precision || 0}
            onChange={(e) =>
              setProp((props: any) => (props.precision = parseInt(e.target.value)))
            }
            className="w-full p-2 border rounded text-sm"
          />
        </div>
      )}
    </div>
  );
};
```

---

#### **2.2 图表组件**

```typescript
// web/src/components/profile-editor/widgets/ChartWidget.tsx
import React, { useEffect, useState } from 'react';
import { useNode, UserComponent } from '@craftjs/core';
import { BarChart, Bar, LineChart, Line, PieChart, Pie, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts';
import { queryApi } from '../../../api/client';

export interface ChartWidgetProps {
  title?: string;
  chartType?: 'bar' | 'line' | 'pie';
  queryConfig?: any;  // OntologyQuery 配置
  xAxis?: string;
  yAxis?: string;
  colors?: string[];
  showLegend?: boolean;
}

export const ChartWidget: UserComponent<ChartWidgetProps> = (props) => {
  const {
    connectors: { connect, drag },
    selected,
  } = useNode((state) => ({
    selected: state.events.selected,
  }));

  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (props.queryConfig) {
      fetchChartData();
    }
  }, [props.queryConfig]);

  const fetchChartData = async () => {
    setLoading(true);
    try {
      const result = await queryApi.executeQuery(props.queryConfig);
      setData(result.rows || []);
    } catch (error) {
      console.error('Failed to fetch chart data:', error);
    } finally {
      setLoading(false);
    }
  };

  const renderChart = () => {
    if (loading) return <div className="text-gray-400">加载中...</div>;
    if (!data || data.length === 0) return <div className="text-gray-400">暂无数据</div>;

    const commonProps = {
      width: 400,
      height: 300,
      data,
    };

    switch (props.chartType) {
      case 'bar':
        return (
          <BarChart {...commonProps}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey={props.xAxis || 'name'} />
            <YAxis />
            <Tooltip />
            {props.showLegend && <Legend />}
            <Bar dataKey={props.yAxis || 'value'} fill={props.colors?.[0] || '#3b82f6'} />
          </BarChart>
        );
      case 'line':
        return (
          <LineChart {...commonProps}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey={props.xAxis || 'name'} />
            <YAxis />
            <Tooltip />
            {props.showLegend && <Legend />}
            <Line type="monotone" dataKey={props.yAxis || 'value'} stroke={props.colors?.[0] || '#3b82f6'} />
          </LineChart>
        );
      case 'pie':
        return (
          <PieChart {...commonProps}>
            <Pie dataKey={props.yAxis || 'value'} data={data} fill={props.colors?.[0] || '#3b82f6'} label />
            <Tooltip />
            {props.showLegend && <Legend />}
          </PieChart>
        );
      default:
        return null;
    }
  };

  return (
    <div
      ref={(ref) => connect(drag(ref!))}
      className={`
        bg-white rounded-lg shadow p-6 border-2 transition-colors
        ${selected ? 'border-blue-500' : 'border-transparent'}
        hover:border-blue-300
      `}
    >
      <h3 className="text-lg font-semibold mb-4">{props.title || '图表'}</h3>
      <div className="flex justify-center">
        {renderChart()}
      </div>
    </div>
  );
};

ChartWidget.craft = {
  displayName: '图表',
  props: {
    title: '图表标题',
    chartType: 'bar',
    showLegend: true,
    colors: ['#3b82f6'],
  },
  related: {
    settings: ChartWidgetSettings,
  },
};

const ChartWidgetSettings = () => {
  const {
    title,
    chartType,
    showLegend,
    actions: { setProp },
  } = useNode((node) => ({
    title: node.data.props.title,
    chartType: node.data.props.chartType,
    showLegend: node.data.props.showLegend,
  }));

  return (
    <div className="space-y-4">
      <div>
        <label className="block text-sm font-medium mb-1">标题</label>
        <input
          type="text"
          value={title || ''}
          onChange={(e) => setProp((props: any) => (props.title = e.target.value))}
          className="w-full p-2 border rounded text-sm"
        />
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">图表类型</label>
        <select
          value={chartType || 'bar'}
          onChange={(e) => setProp((props: any) => (props.chartType = e.target.value))}
          className="w-full p-2 border rounded text-sm"
        >
          <option value="bar">柱状图</option>
          <option value="line">折线图</option>
          <option value="pie">饼图</option>
        </select>
      </div>

      <div>
        <label className="flex items-center space-x-2">
          <input
            type="checkbox"
            checked={showLegend || false}
            onChange={(e) => setProp((props: any) => (props.showLegend = e.target.checked))}
          />
          <span className="text-sm">显示图例</span>
        </label>
      </div>
    </div>
  );
};
```

---

### Step 3: 编辑器主页面

```typescript
// web/src/pages/ProfileTemplateEditor.tsx
import React, { useState } from 'react';
import { Editor, Frame, Element } from '@craftjs/core';
import GridLayout from 'react-grid-layout';
import { MetricCardWidget } from '../components/profile-editor/widgets/MetricCardWidget';
import { ChartWidget } from '../components/profile-editor/widgets/ChartWidget';
import { profileTemplateApi } from '../api/profile-template';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';

// 组件注册表
const componentMap = {
  MetricCardWidget,
  ChartWidget,
  // TableWidget,
  // TextWidget,
};

const ProfileTemplateEditor: React.FC = () => {
  const [templateName, setTemplateName] = useState('');
  const [entityType, setEntityType] = useState('Gantry');
  const [enabled, setEnabled] = useState(true);

  const handleSave = async (query: any, actions: any) => {
    try {
      const serializedState = query.serialize();
      
      await profileTemplateApi.create({
        name: templateName || 'untitled',
        displayName: templateName || '未命名画像',
        entityType,
        craftState: serializedState,
      });

      alert('模板保存成功！');
    } catch (error) {
      alert('保存失败: ' + (error as Error).message);
    }
  };

  return (
    <div className="h-screen flex flex-col">
      {/* 顶部工具栏 */}
      <div className="bg-white border-b px-6 py-4 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">画像编辑器</h1>
        </div>
        <div className="flex items-center space-x-4">
          <input
            type="text"
            value={templateName}
            onChange={(e) => setTemplateName(e.target.value)}
            placeholder="模板名称"
            className="px-3 py-2 border rounded"
          />
          <select
            value={entityType}
            onChange={(e) => setEntityType(e.target.value)}
            className="px-3 py-2 border rounded"
          >
            <option value="Gantry">门架画像</option>
            <option value="Vehicle">车辆画像</option>
          </select>
          <Editor
            resolver={componentMap}
            enabled={enabled}
            onRender={({ render }) => (
              <button
                onClick={() => handleSave(render, null)}
                className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
              >
                保存模板
              </button>
            )}
          >
            {({ query }) => (
              <button
                onClick={() => handleSave(query, null)}
                className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
              >
                保存模板
              </button>
            )}
          </Editor>
        </div>
      </div>

      <Editor resolver={componentMap} enabled={enabled}>
        <div className="flex flex-1 overflow-hidden">
          {/* 左侧：组件工具箱 */}
          <Toolbox />

          {/* 中间：画布 */}
          <div className="flex-1 p-6 overflow-auto bg-gray-100">
            <Frame>
              <Element is={Container} canvas>
                {/* 画布内容 */}
              </Element>
            </Frame>
          </div>

          {/* 右侧：属性编辑面板 */}
          <SettingsPanel />
        </div>
      </Editor>
    </div>
  );
};

// 工具箱组件
const Toolbox = () => {
  const { connectors } = useEditor();

  return (
    <div className="w-64 bg-gray-50 border-r p-4">
      <h2 className="text-lg font-semibold mb-4">组件库</h2>
      <div className="space-y-2">
        <ToolboxItem
          icon="📊"
          label="指标卡片"
          onDragStart={(e) => connectors.create(e.nativeEvent, <MetricCardWidget />)}
        />
        <ToolboxItem
          icon="📈"
          label="图表"
          onDragStart={(e) => connectors.create(e.nativeEvent, <ChartWidget />)}
        />
      </div>
    </div>
  );
};

const ToolboxItem = ({ icon, label, onDragStart }: any) => (
  <div
    draggable
    onDragStart={onDragStart}
    className="p-3 bg-white rounded border border-gray-200 hover:border-blue-500 cursor-move transition-colors"
  >
    <div className="flex items-center space-x-2">
      <span className="text-2xl">{icon}</span>
      <span className="text-sm font-medium">{label}</span>
    </div>
  </div>
);

// 容器组件（支持拖放）
const Container = ({ children }: any) => {
  const {
    connectors: { connect, drag },
  } = useNode();

  return (
    <div
      ref={(ref) => connect(drag(ref!))}
      className="min-h-screen p-4"
      style={{ background: '#f5f5f5' }}
    >
      {children}
    </div>
  );
};

// 属性编辑面板
const SettingsPanel = () => {
  const { selected } = useEditor((state) => {
    const currentNodeId = Array.from(state.events.selected).pop();
    return {
      selected: currentNodeId,
    };
  });

  return (
    <div className="w-80 bg-gray-50 border-l p-4 overflow-auto">
      <h2 className="text-lg font-semibold mb-4">属性</h2>
      {selected ? (
        <Editor>
          {({ query }) => {
            const node = query.node(selected).get();
            const SettingsComponent = node.related?.settings;
            return SettingsComponent ? <SettingsComponent /> : <div className="text-gray-500">该组件无可配置属性</div>;
          }}
        </Editor>
      ) : (
        <div className="text-gray-500">选择一个组件以编辑属性</div>
      )}
    </div>
  );
};

export default ProfileTemplateEditor;
```

---

## 三、后端实现方案

### 3.1 数据库表设计

```sql
-- 画像模板表
CREATE TABLE PROFILE_TEMPLATES (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    description TEXT,
    entity_type VARCHAR(50) NOT NULL,     -- Gantry/Vehicle
    craft_state TEXT NOT NULL,            -- Craft.js 序列化的 JSON
    grid_layout TEXT,                     -- React-Grid-Layout 配置 (可选)
    is_public BOOLEAN DEFAULT FALSE,
    creator_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(entity_type, name)
);

CREATE INDEX idx_template_entity ON PROFILE_TEMPLATES(entity_type);
CREATE INDEX idx_template_creator ON PROFILE_TEMPLATES(creator_id);
```

---

### 3.2 Java 实体类

```java
// src/main/java/com/mypalantir/entity/ProfileTemplate.java
package com.mypalantir.entity;

@Entity
@Table(name = "PROFILE_TEMPLATES")
@Data
public class ProfileTemplate {
    @Id
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    private String displayName;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private String entityType;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String craftState;  // Craft.js 序列化的 JSON
    
    @Column(columnDefinition = "TEXT")
    private String gridLayout;  // React-Grid-Layout 配置
    
    private Boolean isPublic;
    
    private String creatorId;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
```

---

### 3.3 Repository

```java
// src/main/java/com/mypalantir/repository/ProfileTemplateRepository.java
package com.mypalantir.repository;

public interface ProfileTemplateRepository extends JpaRepository<ProfileTemplate, String> {
    
    List<ProfileTemplate> findByEntityType(String entityType);
    
    List<ProfileTemplate> findByEntityTypeAndIsPublic(String entityType, Boolean isPublic);
    
    Optional<ProfileTemplate> findByEntityTypeAndName(String entityType, String name);
}
```

---

### 3.4 Service 实现

```java
// src/main/java/com/mypalantir/service/ProfileTemplateService.java
package com.mypalantir.service;

@Service
public class ProfileTemplateService {
    
    private final ProfileTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 创建模板
     */
    public ProfileTemplate createTemplate(CreateTemplateRequest request) {
        // 验证模板名称唯一性
        if (templateRepository.findByEntityTypeAndName(
            request.getEntityType(), 
            request.getName()
        ).isPresent()) {
            throw new DuplicateException("Template name already exists");
        }
        
        ProfileTemplate template = new ProfileTemplate();
        template.setId(UUID.randomUUID().toString());
        template.setName(request.getName());
        template.setDisplayName(request.getDisplayName());
        template.setEntityType(request.getEntityType());
        template.setCraftState(request.getCraftState());
        template.setGridLayout(request.getGridLayout());
        template.setIsPublic(false);
        template.setCreatorId(getCurrentUserId());
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        
        return templateRepository.save(template);
    }
    
    /**
     * 更新模板
     */
    public ProfileTemplate updateTemplate(String templateId, UpdateTemplateRequest request) {
        ProfileTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new NotFoundException("Template not found"));
        
        if (request.getName() != null) {
            template.setName(request.getName());
        }
        if (request.getDisplayName() != null) {
            template.setDisplayName(request.getDisplayName());
        }
        if (request.getCraftState() != null) {
            template.setCraftState(request.getCraftState());
        }
        if (request.getGridLayout() != null) {
            template.setGridLayout(request.getGridLayout());
        }
        
        template.setUpdatedAt(LocalDateTime.now());
        return templateRepository.save(template);
    }
    
    /**
     * 获取模板列表
     */
    public List<ProfileTemplate> listTemplates(String entityType) {
        return templateRepository.findByEntityType(entityType);
    }
    
    /**
     * 获取模板详情
     */
    public ProfileTemplate getTemplate(String templateId) {
        return templateRepository.findById(templateId)
            .orElseThrow(() -> new NotFoundException("Template not found"));
    }
    
    /**
     * 删除模板
     */
    public void deleteTemplate(String templateId) {
        ProfileTemplate template = getTemplate(templateId);
        
        // 权限检查
        if (!template.getCreatorId().equals(getCurrentUserId())) {
            throw new ForbiddenException("Cannot delete others' templates");
        }
        
        templateRepository.delete(template);
    }
    
    private String getCurrentUserId() {
        // TODO: 从安全上下文获取
        return "system";
    }
}
```

---

### 3.5 Controller

```java
// src/main/java/com/mypalantir/controller/ProfileTemplateController.java
package com.mypalantir.controller;

@RestController
@RequestMapping("/api/v1/profile-templates")
public class ProfileTemplateController {
    
    private final ProfileTemplateService templateService;
    
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
        @RequestParam String entityType
    ) {
        List<ProfileTemplate> templates = templateService.listTemplates(entityType);
        return ApiResponse.success(templates);
    }
    
    /**
     * 获取模板详情
     */
    @GetMapping("/{templateId}")
    public ApiResponse<ProfileTemplate> getTemplate(@PathVariable String templateId) {
        ProfileTemplate template = templateService.getTemplate(templateId);
        return ApiResponse.success(template);
    }
    
    /**
     * 删除模板
     */
    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> deleteTemplate(@PathVariable String templateId) {
        templateService.deleteTemplate(templateId);
        return ApiResponse.success(null);
    }
}
```

---

### 3.6 DTO

```java
// src/main/java/com/mypalantir/dto/CreateTemplateRequest.java
@Data
public class CreateTemplateRequest {
    private String name;
    private String displayName;
    private String entityType;
    private String craftState;  // Craft.js 序列化的 JSON 字符串
    private String gridLayout;  // 可选
}

@Data
public class UpdateTemplateRequest {
    private String name;
    private String displayName;
    private String craftState;
    private String gridLayout;
}
```

---

## 四、画像渲染实现

### 4.1 前端渲染器

```typescript
// web/src/pages/ProfileViewer.tsx
import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Editor, Frame } from '@craftjs/core';
import { profileTemplateApi } from '../api/profile-template';
import { MetricCardWidget, ChartWidget } from '../components/profile-editor/widgets';

const componentMap = {
  MetricCardWidget,
  ChartWidget,
};

const ProfileViewer: React.FC = () => {
  const { templateId } = useParams<{ templateId: string }>();
  const [entityId, setEntityId] = useState('');
  const [startDate, setStartDate] = useState('2024-01-01');
  const [endDate, setEndDate] = useState('2024-12-31');
  const [craftState, setCraftState] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const loadTemplate = async () => {
    setLoading(true);
    try {
      const template = await profileTemplateApi.get(templateId!);
      setCraftState(template.craftState);
    } catch (error) {
      alert('加载模板失败: ' + (error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (templateId) {
      loadTemplate();
    }
  }, [templateId]);

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
              onClick={loadTemplate}
              disabled={loading}
              className="w-full px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
            >
              刷新
            </button>
          </div>
        </div>
      </div>

      {/* 画像展示 */}
      {craftState && (
        <Editor resolver={componentMap} enabled={false}>
          <Frame data={craftState}>
            <div className="p-4">
              {/* 画像内容将自动从 craftState 恢复 */}
            </div>
          </Frame>
        </Editor>
      )}
    </div>
  );
};

export default ProfileViewer;
```

---

## 五、实施步骤

### Phase 1: 基础集成（2-3天）

1. **安装依赖**
```bash
npm install @craftjs/core react-grid-layout recharts
```

2. **创建基础组件**
   - MetricCardWidget
   - 简单的编辑器页面

3. **测试拖拽功能**

---

### Phase 2: 组件完善（3-4天）

1. **完成所有 Widget**
   - ChartWidget（柱状图、折线图、饼图）
   - TableWidget
   - TextWidget

2. **配置面板开发**
   - 指标选择器
   - 样式配置
   - 数据源配置

---

### Phase 3: 后端集成（2-3天）

1. **实现后端服务**
   - ProfileTemplateService
   - ProfileTemplateController
   - 数据库表创建

2. **API 对接**
   - 保存模板
   - 加载模板
   - 渲染画像

---

### Phase 4: 优化与测试（2-3天）

1. **性能优化**
   - 数据缓存
   - 懒加载

2. **用户体验**
   - 拖拽平滑度
   - 响应式布局

3. **测试**
   - 功能测试
   - 兼容性测试

---

## 六、关键技术点

### 6.1 Craft.js 序列化

```typescript
// 序列化（保存）
const { query } = useEditor();
const json = query.serialize();
await profileTemplateApi.create({ craftState: json });

// 反序列化（加载）
<Frame data={craftState}>
  {/* 内容自动恢复 */}
</Frame>
```

### 6.2 占位符替换

```typescript
// 在组件中使用上下文参数
const { entityId, startDate, endDate } = useContext(ProfileContext);

useEffect(() => {
  fetchData({
    dimensions: { gantry_id: entityId },
    timeRange: { start: startDate, end: endDate }
  });
}, [entityId, startDate, endDate]);
```

### 6.3 网格布局保存

```typescript
// 监听布局变化
<GridLayout onLayoutChange={(layout) => {
  // 保存布局配置
  setGridLayout(layout);
}}>
```

---

## 七、总结

### 优势

1. **开箱即用**：Craft.js 提供完整的拖拽编辑能力
2. **灵活扩展**：轻松添加新的组件类型
3. **开源免费**：MIT 许可证，无版权风险
4. **社区活跃**：文档完善，问题容易解决

### 注意事项

1. **性能**: 大量组件时注意优化
2. **兼容性**: 测试不同浏览器
3. **数据安全**: 验证用户输入

---

**文档版本**: v1.0  
**创建日期**: 2026-01-15  
**作者**: Qoder
