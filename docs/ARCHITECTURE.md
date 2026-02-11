# MyPalantir 架构文档

本文档使用 PlantUML 语法描述了 MyPalantir 平台的架构设计，包括当前平台架构和未来完整架构。

## 架构图说明

### 1. 当前平台架构图

展示了 MyPalantir 平台的核心架构，包括：

- **应用层**：Web UI、REST API、Query DSL
- **智能服务层**：指标计算引擎、LLM 服务
- **Ontology 层**：Schema 定义、查询引擎、执行路由等
- **数据源层**：JDBC 数据库、文件系统、Neo4j 图数据库

### 2. 未来完整架构图

展示了 MyPalantir 平台与外部系统集成的完整架构，包括：

#### 核心平台扩展
- **用户交互层**：Web UI、移动端 App、API Gateway
- **应用服务层**：REST API、GraphQL API、WebSocket Service
- **智能服务层**：指标引擎、LLM 服务、AI Agent、推荐引擎
- **数据治理层**：数据质量、数据血缘、数据目录
- **存储层**：关系数据库、图数据库、对象存储、时序数据库

#### 外部系统集成

**ETL 系统**：
- Dome Scheduler（ETL 调度系统）
- Dome Datasource（数据源管理）
- SeaTunnel（数据同步引擎）
- Airflow（工作流调度）

**可视化工具**：
- Tableau（BI 可视化）
- Grafana（监控可视化）
- Superset（开源 BI）
- 自定义仪表板

**机器学习平台**：
- MLflow（模型管理）
- Kubeflow（ML 工作流）
- Feature Store（特征存储）
- Model Serving（模型服务）

**大模型平台**：
- OpenAI API
- DeepSeek API
- 本地 LLM（Ollama/LocalAI）
- Embedding Service（向量化服务）
- Vector Database（向量数据库：Pinecone/Weaviate）

**外部数据源**：
- 业务数据库（MySQL/PostgreSQL）
- 数据仓库（Snowflake/BigQuery）
- 数据湖（Hadoop/S3）
- 实时数据流（Kafka/Pulsar）

## 如何查看架构图

### 方法一：使用 PlantUML 工具

1. **在线查看**：
   - 访问 [PlantUML Online Server](http://www.plantuml.com/plantuml/uml/)
   - 复制 `docs/architecture.puml` 文件内容
   - 粘贴到在线编辑器中查看

2. **VS Code 插件**：
   - 安装 "PlantUML" 插件
   - 打开 `docs/architecture.puml` 文件
   - 使用 `Alt+D` 预览图表

3. **本地工具**：
   ```bash
   # 安装 PlantUML
   npm install -g node-plantuml
   
   # 生成图片
   puml generate docs/architecture.puml -o docs/images/
   ```

### 方法二：使用 Mermaid（备选）

如果 PlantUML 不可用，也可以使用 Mermaid 语法。我们提供了 Mermaid 版本的架构图（待实现）。

## 架构设计原则

1. **分层架构**：清晰的层次划分，每层职责明确
2. **解耦设计**：通过 Ontology 层实现业务概念与物理存储的解耦
3. **可扩展性**：支持多种数据源和外部系统集成
4. **智能化**：集成 AI/ML 能力，提供智能化的数据服务
5. **联邦查询**：支持跨数据源的统一查询，无需数据迁移

## 关键组件说明

### Query Engine（查询引擎）
- 将 OntologyQuery DSL 转换为物理 SQL
- 基于 Apache Calcite 进行查询优化
- 支持单数据源和跨数据源联邦查询

### ExecutionRouter（执行路由）
- 自动分析查询涉及的数据源
- 智能选择最优执行路径
- 单数据源走 SQL 路径，跨数据源走联邦执行

### FederatedCalciteRunner（联邦查询执行器）
- 基于 Calcite 的联邦查询能力
- 支持跨数据源的 JOIN、聚合等操作
- 自动下推过滤和投影，减少数据传输

### AI Agent（智能代理服务）
- 自动数据发现
- 智能查询建议
- 异常检测
- 自动化报告生成

## 未来扩展方向

1. **实时数据处理**：集成流式数据处理能力（Kafka、Pulsar）
2. **向量数据库**：支持语义搜索和 RAG（检索增强生成）
3. **模型服务**：集成 ML 模型推理服务
4. **多租户支持**：支持多租户隔离和权限管理
5. **云原生部署**：支持 Kubernetes 部署和自动扩缩容

## 相关文档

- [系统架构](./README.md#系统架构) - README 中的架构说明
- [查询引擎架构](./README.md#查询引擎架构) - 查询引擎详细说明
- [技术架构](./README.md#技术架构) - 技术栈和核心模块


