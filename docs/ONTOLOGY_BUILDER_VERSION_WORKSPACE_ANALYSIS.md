# 本体构建工具 - 版本管理与工作空间配置分析

## 1. 概述

本文档分析本体构建工具（Ontology Builder）中本体模型版本管理和工作空间配置的实现现状，并提出新增功能及优化建议。

## 2. 当前实现分析

### 2.1 本体模型版本管理

#### 2.1.1 数据结构

**前端模型（OntologyModel）**：
```typescript
interface OntologyModel {
  id: string;
  name: string;
  version: string;        // 版本号（字符串，如 "1.0.0"）
  namespace: string;
  entities: Entity[];
  relations: Relation[];
}
```

**后端模型（OntologySchema）**：
```java
public class OntologySchema {
    private String version;      // 版本号
    private String namespace;    // 命名空间
    private List<ObjectType> objectTypes;
    private List<LinkType> linkTypes;
}
```

#### 2.1.2 版本存储

- **存储位置**：YAML 文件顶层字段
- **存储格式**：字符串（如 `version: "1.0.0"`）
- **文件路径**：`./ontology/{filename}.yaml`

#### 2.1.3 当前限制

1. **无版本历史**：每次保存覆盖原文件，无法追溯历史版本
2. **无版本比较**：无法对比不同版本的差异
3. **无版本升级机制**：版本号需要手动输入，无自动递增
4. **无版本依赖管理**：无法管理模型间的依赖关系
5. **无版本回滚**：无法恢复到历史版本

### 2.2 工作空间配置

#### 2.2.1 数据结构

**系统定义（schema-system.yaml）**：
```yaml
object_types:
  - name: workspace
    display_name: 工作空间
    properties:
      - name: name              # 工作空间唯一标识
      - name: display_name      # 显示名称
      - name: description       # 描述
      - name: object_types      # 对象类型列表（JSON数组）
      - name: link_types        # 关系类型列表（JSON数组）
```

**前端实现**：
- `WorkspaceContext.tsx`：工作空间上下文管理
- `WorkspaceDialog.tsx`：工作空间创建/编辑对话框
- 工作空间数据存储在 Neo4j 或关系数据库中

#### 2.2.2 工作空间功能

1. **分组管理**：将对象类型和关系类型分组到不同工作空间
2. **界面过滤**：根据选中的工作空间过滤显示内容
3. **系统工作空间**：内置系统工作空间，包含系统对象和关系

#### 2.2.3 当前限制

1. **与本体构建器未集成**：本体构建器生成的文件未关联工作空间
2. **无工作空间模板**：无法基于工作空间创建模型模板
3. **无工作空间权限**：无权限控制机制
4. **无工作空间导入导出**：无法批量导入导出工作空间配置

## 3. 核心问题分析

### 3.1 版本管理问题

#### 问题 1：文件覆盖导致版本丢失

**现状**：
```java
// OntologyBuilderService.java:202
public String saveToOntologyFolder(OntologySchema schema, String filename) throws IOException {
    // ...
    if (Files.exists(filePath)) {
        throw new IOException("文件已存在: " + filename + "，请修改文件名后重试");
    }
    // 直接覆盖，无版本历史
    Files.writeString(filePath, yamlContent);
}
```

**影响**：
- 无法追溯模型变更历史
- 误操作无法恢复
- 无法进行版本对比

#### 问题 2：版本号管理不规范

**现状**：
- 版本号由用户手动输入（前端输入框）
- 无版本号格式校验
- 无语义化版本规范（Semantic Versioning）

**影响**：
- 版本号可能重复或不规范
- 无法自动判断版本兼容性

### 3.2 工作空间集成问题

#### 问题 1：本体文件与工作空间未关联

**现状**：
- 本体构建器保存的文件独立于工作空间
- 工作空间管理的是运行时对象类型，而非文件
- 两者之间无明确关联关系

**影响**：
- 无法在工作空间内管理本体文件
- 无法基于工作空间创建模型
- 工作空间切换时无法自动加载对应模型

#### 问题 2：工作空间配置未持久化到文件

**现状**：
- 工作空间配置存储在数据库中
- 本体文件存储在文件系统中
- 两者分离，无法统一管理

## 4. 新增功能建议

### 4.1 版本管理功能

#### 4.1.1 版本历史管理

**功能描述**：
- 保存模型时自动创建版本快照
- 支持查看版本历史列表
- 支持版本对比和回滚

**实现方案**：

**后端实现**：
```java
// 新增版本存储结构
public class OntologyVersion {
    private String version;
    private String namespace;
    private String filename;
    private String filePath;        // 版本文件路径
    private String previousVersion; // 上一版本号
    private String commitMessage;   // 提交说明
    private String author;           // 作者
    private long timestamp;          // 时间戳
    private OntologySchema schema;   // 模型内容
}

// 修改保存逻辑
public String saveToOntologyFolder(OntologySchema schema, String filename, String commitMessage) {
    // 1. 检查是否存在当前版本
    OntologyVersion currentVersion = loadCurrentVersion(filename);
    
    // 2. 生成新版本号（自动递增或用户指定）
    String newVersion = generateNextVersion(currentVersion, schema.getVersion());
    
    // 3. 保存版本快照到版本目录
    String versionPath = saveVersionSnapshot(filename, newVersion, schema, commitMessage);
    
    // 4. 更新当前版本链接
    updateCurrentVersionLink(filename, newVersion);
    
    return versionPath;
}

// 版本目录结构
// ontology/
//   ├── models/
//   │   ├── model1.yaml          # 当前版本（符号链接）
//   │   └── model1/
//   │       ├── v1.0.0.yaml      # 版本快照
//   │       ├── v1.0.1.yaml
//   │       └── v1.1.0.yaml
```

**前端实现**：
```typescript
// 新增版本历史组件
interface VersionHistory {
  version: string;
  timestamp: number;
  author: string;
  commitMessage: string;
  changes: string[];  // 变更摘要
}

// 版本管理 API
export const versionApi = {
  getHistory: async (filename: string): Promise<VersionHistory[]> => {
    // 获取版本历史列表
  },
  getVersion: async (filename: string, version: string): Promise<OntologySchema> => {
    // 获取指定版本
  },
  compareVersions: async (filename: string, v1: string, v2: string): Promise<DiffResult> => {
    // 版本对比
  },
  rollback: async (filename: string, version: string): Promise<void> => {
    // 回滚到指定版本
  },
};
```

#### 4.1.2 语义化版本管理

**功能描述**：
- 支持语义化版本规范（MAJOR.MINOR.PATCH）
- 自动版本号递增
- 版本兼容性检查

**实现方案**：
```java
public class VersionManager {
    // 解析版本号
    public Version parseVersion(String versionStr) {
        // 解析 "1.2.3" 格式
    }
    
    // 生成下一版本
    public String generateNextVersion(Version current, VersionType type) {
        // type: MAJOR, MINOR, PATCH
        // 自动递增对应部分
    }
    
    // 检查兼容性
    public boolean isCompatible(Version v1, Version v2) {
        // 主版本号相同则兼容
    }
}
```

**前端 UI**：
```typescript
// 版本号输入组件
<VersionInput
  currentVersion={model.version}
  onChange={(version) => setModel({...model, version})}
  onAutoIncrement={(type) => {
    // 自动递增：major, minor, patch
    const nextVersion = incrementVersion(model.version, type);
    setModel({...model, version: nextVersion});
  }}
/>
```

#### 4.1.3 版本对比功能

**功能描述**：
- 可视化对比两个版本的差异
- 显示实体、关系、属性的增删改

**实现方案**：
```java
public class VersionComparator {
    public DiffResult compare(OntologySchema v1, OntologySchema v2) {
        DiffResult result = new DiffResult();
        
        // 对比对象类型
        result.objectTypeDiffs = compareObjectTypes(
            v1.getObjectTypes(), 
            v2.getObjectTypes()
        );
        
        // 对比关系类型
        result.linkTypeDiffs = compareLinkTypes(
            v1.getLinkTypes(), 
            v2.getLinkTypes()
        );
        
        return result;
    }
}
```

**前端 UI**：
```typescript
// 版本对比组件
<VersionCompare
  version1={v1}
  version2={v2}
  diffResult={diffResult}
  onSelectChange={(change) => {
    // 选择要应用的变更
  }}
/>
```

### 4.2 工作空间集成功能

#### 4.2.1 工作空间与模型关联

**功能描述**：
- 保存模型时关联到工作空间
- 工作空间切换时自动加载对应模型
- 在工作空间内管理多个模型版本

**实现方案**：

**修改 OntologySchema**：
```java
public class OntologySchema {
    private String version;
    private String namespace;
    private String workspaceId;      // 新增：关联工作空间ID
    private String workspaceName;   // 新增：工作空间名称
    // ...
}
```

**修改保存逻辑**：
```java
public String saveToOntologyFolder(
    OntologySchema schema, 
    String filename, 
    String workspaceId  // 新增参数
) {
    // 1. 验证工作空间是否存在
    validateWorkspace(workspaceId);
    
    // 2. 保存模型文件
    String filePath = saveModelFile(schema, filename);
    
    // 3. 更新工作空间配置（添加模型引用）
    updateWorkspaceModels(workspaceId, filename, schema.getVersion());
    
    return filePath;
}
```

**前端实现**：
```typescript
// 保存时选择工作空间
const importToSystem = async () => {
  // 1. 选择工作空间（如果未选择）
  const workspaceId = selectedWorkspaceId || await selectWorkspace();
  
  // 2. 保存模型并关联工作空间
  const result = await ontologyBuilderApi.save(
    toApiFormat(model), 
    filename,
    workspaceId  // 新增参数
  );
};
```

#### 4.2.2 工作空间模板功能

**功能描述**：
- 基于工作空间创建模型模板
- 从模板快速创建新模型
- 模板包含预定义的对象类型和关系类型

**实现方案**：
```java
// 工作空间模板服务
public class WorkspaceTemplateService {
    // 从工作空间创建模板
    public ModelTemplate createTemplateFromWorkspace(String workspaceId) {
        Workspace workspace = loadWorkspace(workspaceId);
        OntologySchema schema = loadModelsForWorkspace(workspaceId);
        
        return ModelTemplate.builder()
            .workspaceId(workspaceId)
            .objectTypes(workspace.getObjectTypes())
            .linkTypes(workspace.getLinkTypes())
            .schema(schema)
            .build();
    }
    
    // 从模板创建模型
    public OntologySchema createModelFromTemplate(String templateId, String namespace) {
        ModelTemplate template = loadTemplate(templateId);
        // 基于模板创建新模型
    }
}
```

#### 4.2.3 工作空间导入导出

**功能描述**：
- 导出工作空间配置（包括模型文件）
- 导入工作空间配置
- 支持批量操作

**实现方案**：
```java
// 工作空间导出
public WorkspaceExport exportWorkspace(String workspaceId) {
    Workspace workspace = loadWorkspace(workspaceId);
    List<OntologySchema> models = loadModelsForWorkspace(workspaceId);
    
    return WorkspaceExport.builder()
        .workspace(workspace)
        .models(models)
        .metadata(collectMetadata(workspaceId))
        .build();
}

// 工作空间导入
public String importWorkspace(WorkspaceExport export) {
    // 1. 创建工作空间
    String workspaceId = createWorkspace(export.getWorkspace());
    
    // 2. 导入模型文件
    for (OntologySchema model : export.getModels()) {
        saveModelFile(model, generateFilename(model));
        associateWithWorkspace(workspaceId, model);
    }
    
    return workspaceId;
}
```

### 4.3 模型管理功能增强

#### 4.3.1 模型依赖管理

**功能描述**：
- 定义模型间的依赖关系
- 检查依赖完整性
- 自动加载依赖模型

**实现方案**：
```java
public class OntologySchema {
    private String version;
    private String namespace;
    private List<ModelDependency> dependencies;  // 新增
    
    public static class ModelDependency {
        private String namespace;
        private String versionRange;  // 如 ">=1.0.0,<2.0.0"
        private boolean required;
    }
}
```

#### 4.3.2 模型校验增强

**功能描述**：
- 校验模型依赖
- 校验命名空间冲突
- 校验版本兼容性

**实现方案**：
```java
private List<String> validateDependencies(OntologySchema schema) {
    List<String> errors = new ArrayList<>();
    
    for (ModelDependency dep : schema.getDependencies()) {
        // 检查依赖模型是否存在
        if (!modelExists(dep.getNamespace())) {
            errors.add("依赖模型不存在: " + dep.getNamespace());
            continue;
        }
        
        // 检查版本兼容性
        String currentVersion = getModelVersion(dep.getNamespace());
        if (!versionMatches(currentVersion, dep.getVersionRange())) {
            errors.add("依赖版本不兼容: " + dep.getNamespace() + 
                      " (需要: " + dep.getVersionRange() + 
                      ", 当前: " + currentVersion + ")");
        }
    }
    
    return errors;
}
```

## 5. 优化建议

### 5.1 性能优化

#### 5.1.1 版本文件存储优化

**问题**：每个版本保存完整文件，占用空间大

**优化方案**：
- 使用增量存储：只保存版本间的差异
- 压缩历史版本：对旧版本进行压缩
- 定期清理：自动清理过旧版本

```java
// 增量存储实现
public class IncrementalVersionStorage {
    public void saveVersion(String filename, String version, OntologySchema schema) {
        OntologySchema previous = loadPreviousVersion(filename);
        DiffResult diff = compare(previous, schema);
        
        // 只保存差异
        saveDiff(filename, version, diff);
    }
}
```

#### 5.1.2 模型加载优化

**问题**：加载大模型时性能差

**优化方案**：
- 延迟加载：按需加载实体和关系
- 缓存机制：缓存常用模型
- 索引优化：为模型文件建立索引

### 5.2 用户体验优化

#### 5.2.1 版本管理 UI

**建议**：
- 版本历史时间线视图
- 版本对比可视化
- 一键回滚功能
- 版本标签和分支支持

#### 5.2.2 工作空间集成 UI

**建议**：
- 在工作空间选择器中显示关联的模型
- 模型保存时自动关联当前工作空间
- 工作空间切换时提示加载模型

### 5.3 数据一致性优化

#### 5.3.1 命名空间管理

**问题**：命名空间可能冲突

**优化方案**：
- 命名空间唯一性检查
- 命名空间与工作空间关联
- 自动生成命名空间建议

```java
public String generateNamespace(String workspaceId, String modelName) {
    Workspace workspace = loadWorkspace(workspaceId);
    String baseNamespace = workspace.getNamespace() + "." + modelName;
    
    // 检查唯一性并调整
    int suffix = 1;
    String namespace = baseNamespace;
    while (namespaceExists(namespace)) {
        namespace = baseNamespace + "." + suffix++;
    }
    
    return namespace;
}
```

#### 5.3.2 文件命名规范

**问题**：文件名可能不规范

**优化方案**：
- 自动生成文件名（基于命名空间）
- 文件名格式校验
- 文件名冲突检测和自动重命名

## 6. 实施优先级

### 高优先级（P0）

1. **版本历史管理**：基础功能，必须实现
2. **工作空间关联**：核心集成功能
3. **版本对比**：提升用户体验

### 中优先级（P1）

1. **语义化版本管理**：规范化版本号
2. **工作空间模板**：提升效率
3. **模型依赖管理**：支持复杂场景

### 低优先级（P2）

1. **工作空间导入导出**：批量操作
2. **性能优化**：大规模场景优化
3. **UI 增强**：用户体验提升

## 7. 技术实现细节

### 7.1 版本存储目录结构

```
ontology/
├── models/                    # 模型文件目录
│   ├── model1.yaml           # 当前版本（符号链接或最新版本）
│   └── model1/               # 版本历史目录
│       ├── metadata.json     # 版本元数据
│       ├── v1.0.0.yaml
│       ├── v1.0.1.yaml
│       └── v1.1.0.yaml
├── workspaces/               # 工作空间配置
│   └── workspace1.json
└── templates/                # 模板目录
    └── template1.json
```

### 7.2 数据库表设计（如需要）

```sql
-- 模型版本表
CREATE TABLE ontology_versions (
    id VARCHAR(64) PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    version VARCHAR(32) NOT NULL,
    namespace VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(64),
    file_path VARCHAR(512),
    previous_version VARCHAR(32),
    commit_message TEXT,
    author VARCHAR(128),
    created_at BIGINT,
    UNIQUE KEY uk_file_version (filename, version)
);

-- 工作空间模型关联表
CREATE TABLE workspace_models (
    workspace_id VARCHAR(64),
    filename VARCHAR(255),
    version VARCHAR(32),
    is_active BOOLEAN DEFAULT TRUE,
    PRIMARY KEY (workspace_id, filename)
);
```

### 7.3 API 接口设计

```java
// 版本管理 API
@RestController
@RequestMapping("/api/v1/ontology-builder/versions")
public class VersionController {
    @GetMapping("/{filename}/history")
    List<VersionInfo> getHistory(@PathVariable String filename);
    
    @GetMapping("/{filename}/{version}")
    OntologySchema getVersion(@PathVariable String filename, @PathVariable String version);
    
    @PostMapping("/{filename}/compare")
    DiffResult compareVersions(@PathVariable String filename, @RequestBody CompareRequest request);
    
    @PostMapping("/{filename}/rollback")
    void rollback(@PathVariable String filename, @RequestParam String version);
}

// 工作空间集成 API
@PostMapping("/save")
ResponseEntity<?> save(
    @RequestBody OntologySchema schema,
    @RequestParam String filename,
    @RequestParam(required = false) String workspaceId
);
```

## 8. 总结

本体构建工具在版本管理和工作空间集成方面还有很大改进空间。通过实施上述功能，可以：

1. **提升模型管理能力**：版本历史、对比、回滚等功能
2. **增强系统集成**：工作空间与模型关联，统一管理
3. **改善用户体验**：自动化、可视化、易用性提升
4. **支持复杂场景**：依赖管理、模板、批量操作

建议按照优先级逐步实施，先完成核心功能，再逐步完善高级特性。

