# MyPalantir 自动化测试框架

本仓库将 **单元测试（UT）**、**接口测试（API）**、**UI/E2E 测试** 与 **报告** 统一整合，便于在 Cursor 或 CI 中一键运行与查看。

## 目录结构

```
test-reports/                 # 统一报告输出目录
├── index.html                # 报告汇总入口（本文件在仓库中保留）
├── java/                     # 后端 JUnit 报告（Maven Surefire）
├── frontend/                 # 前端 Vitest 报告（junit.xml + index.html）
└── e2e/                      # Playwright E2E 报告
web/
├── e2e/                      # E2E 用例目录
├── vitest.config.ts          # 前端单元测试配置
├── playwright.config.ts      # E2E 配置
└── src/**/*.test.ts(x)       # 前端单元测试
src/test/java/                # 后端 JUnit 测试（*Test.java / *IT.java）
```

## 首次准备

- 后端：需已安装 JDK 17、Maven。
- 前端：在 `web/` 下执行 `npm install`；E2E 首次需安装浏览器：`cd web && npx playwright install chromium`。

## 如何运行

### 一键运行全部测试（推荐）

在项目根目录执行：

```bash
# 使用 npm（需先在根目录 npm install，可选）
npm run test:all

# 或使用脚本
# Windows PowerShell
.\scripts\run-all-tests.ps1

# Linux / macOS
./scripts/run-all-tests.sh
```

将依次执行：**后端测试（含 HTML 报告）→ 前端单元测试与覆盖率 → E2E/UI 测试**，报告写入 `test-reports/`。

### 分步运行

| 层级       | 命令（在对应目录） | 说明           |
|------------|--------------------|----------------|
| 后端       | `mvn test`         | JUnit 5 + Surefire，自动生成 HTML（test-reports/java/surefire-report.html） |
| 前端 UT    | `cd web && npm run test` | Vitest（仅测试）        |
| 前端 UT+覆盖率 | `cd web && npm run test:coverage` | Vitest + v8，生成 test-reports/frontend/ 与 coverage/ |
| 前端 E2E   | `cd web && npm run test:e2e` | Playwright |

## 报告查看

- **汇总页**：打开 `test-reports/index.html`，可跳转到各层 HTML 及覆盖率。
- **后端**：`test-reports/java/surefire-report.html` 为 Surefire HTML；同目录下有 XML/文本及静态资源。
- **前端**：`test-reports/frontend/report.html` 为单一 HTML 测试报告，**可直接用 file:// 打开**，无 CORS 问题；`frontend/coverage/index.html` 为覆盖率（需先运行 `npm run test:coverage`）；`junit.xml` 可供 CI 解析。原 `index.html` 为 Vitest 交互式报告，需通过本地服务（如 `npx serve test-reports/frontend`）打开。
- **E2E**：`test-reports/e2e/playwright-report/index.html` 为 Playwright HTML 报告。

## 技术栈

- **后端**：JUnit 5、Spring Boot Test（MockMvc、TestRestTemplate）、Maven Surefire。
- **前端 UT**：Vitest、jsdom、@vitest/ui、覆盖率 v8。
- **E2E/UI**：Playwright，可选启动本地 dev server。

## 后端测试用例清单

以下为 `src/test/java` 下被 Surefire 运行的 JUnit 5 测试类（`QueryDebugTest.java` 为带 `main` 的脚本，已在 pom 中排除）：

| 包 | 测试类 | 说明 |
|----|--------|------|
| **controller** | ApiResponseTest | ApiResponse 工厂方法、ErrorItem、getter/setter |
| | HealthControllerTest | GET /health 返回 200 与 status |
| | SchemaControllerTest | Schema API：object-types、link-types、data-sources、testConnection |
| | QueryControllerTest | POST /query 执行与 400 非法参数 |
| | LinkControllerTest | 创建/获取/列表/统计/同步/删除 link |
| | InstanceControllerTest | 实例 CRUD、列表、404 |
| | MappingControllerTest | 映射 CRUD、按对象类型查询 |
| | DatabaseControllerTest | 默认库 ID、库列表 |
| | MetricControllerTest | 原子指标 CRUD、404 |
| | NaturalLanguageQueryControllerTest | 自然语言执行与仅转换、空查询 400 |
| | OntologyModelControllerTest | 模型列表、对象类型、当前模型、切换模型 |
| | ComparisonControllerTest | 数据对比执行、异常 500 |
| | OntologyBuilderControllerTest | 校验、文件列表、加载文件 |
| | InstanceLinkControllerTest | 实例关联 link、关联实例列表 |
| | ETLLinkControllerTest | ETL link 列表、批量创建、空 body 400 |
| | EtlModelControllerTest | ETL 模型构建、非法参数 400 |
| **meta** | ParserTest | Parser 解析 YAML、文件不存在、属性与 data_source |
| | DataSourceMappingTest | 数据源与 ObjectType 映射解析 |
| | DataSourceConfigTest | buildJdbcUrl：mysql/postgres/h2/未设置类型抛异常 |
| | ValidatorTest | Schema 语法/语义/约束校验、各类非法情况 |
| | LoaderTest | 使用 schema-mini.yaml 加载、getObjectType/listObjectTypes、getLinkType/listLinkTypes、getDataSourceById、getOutgoingLinks/getIncomingLinks、未加载时行为 |
| **repository** | PathManagerTest | 实例/ link 路径、命名空间默认值、ASCII/非 ASCII 名称规范化 |
| **service** | VersionComparatorTest | 版本对比：无变更、元数据/ObjectType/LinkType 增删改 |
| | VersionManagerTest | 版本解析、下一版本生成（MAJOR/MINOR/PATCH）、兼容性、Version.toString |
| | DataValidatorTest | 实例与 link 数据校验、必填/类型/默认值 |
| | SchemaServiceTest | 对 Loader 的委托：object-types、link-types、data-sources 等 |
| **query** | QueryParserTest | parseMap/parseJson/parseYaml、from/object、links、limit/offset |
| | FieldPathResolverTest | 简单字段解析、空路径/属性不存在/link 不在查询中 |
| | OntologyQueryTest | getFrom、select/limit/offset、orderBy、linkQuery、where/filter |
| **metric** | AtomicMetricTest | 从 Map 构造、toMap、可选字段 |
| | MetricResultTest | MetricResult getter/setter、ComparisonValue、MetricDataPoint |

## 扩展说明

- **后端**：在 `src/test/java` 下新增 `*Test.java` 或 `*IT.java` 即可被 Surefire 运行；需完整 Spring 上下文的接口测试可配合 `application-test.properties` 或 Testcontainers 使用。
- **前端**：在 `src` 下新增 `*.test.ts` / `*.spec.tsx`；E2E 用例放在 `web/e2e/`。当前前端 UT 覆盖：`api/client.test.ts`（schemaApi、modelApi、instanceApi、linkApi、queryApi）、`api/metric.test.ts`、`utils/ontologyValidator.test.ts`、`models/OntologyModel.test.ts`（toApiFormat/fromApiFormat）、`WorkspaceContext.test.tsx`（refreshWorkspaces 映射、object_types/link_types 对象转数组、setSelectedWorkspaceId/localStorage、selectedWorkspace 与 selectedWorkspaceId 对应）。
- **CI**：根目录 `npm run test:all` 或脚本可直接用于 CI；报告目录为 `test-reports/`，可按需发布 HTML 或解析 JUnit XML。

## 前端 E2E/UI 测试用例清单

以下为 `web/e2e/` 下 Playwright 运行的 E2E/UI 用例（`cd web && npm run test:e2e`）：

| 文件 | 说明 |
|------|------|
| **smoke.spec.ts** | 烟雾测试：首页加载与重定向、#root 挂载；主导航：Schema/Links/Query 页可访问、从首页导航到 Schema |
| **pages.spec.ts** | 主要页面可访问：/schema、/schema-graph、/data-sources、/query、/metrics、/metrics/builder、/natural-language-query、/data-comparison、/ontology-builder；带参数路由：/instances/workspace、/links/owns |
| **navigation.spec.ts** | 侧栏导航 UI：点击「查询构建器」「指标管理」「自然语言查询」「数据对账」「本体构建工具」「本体关系图」「本体模型」跳转正确；页面标题：Schema Browser、Query Builder、Metric Manage |
