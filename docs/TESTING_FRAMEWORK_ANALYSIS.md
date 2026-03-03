# MyPalantir 测试框架功能实现逻辑分析

本文档从实现逻辑角度分析当前项目的测试框架，并使用 PlantUML 描述架构与流程。PlantUML 图块可直接复制到 [PlantUML 在线服务器](https://www.plantuml.com/plantuml/uml) 或本地 PlantUML 工具中渲染。

---

## 总结：测试框架如何满足需求

本项目的测试框架将**单元测试（UT）**、**接口测试（API）**、**UI/E2E 测试**与**统一报告**整合在一起，满足“一处入口、分层执行、报告集中”的需求。

| 需求维度 | 实现方式 | 说明 |
|----------|----------|------|
| **UT（单元测试）** | 后端 JUnit 5（MockMvc/Mockito 隔离依赖）+ 前端 Vitest（jsdom 环境） | 后端不启 Spring 上下文，仅测 Controller/Service/工具类逻辑；前端测组件、API 封装、工具函数等。 |
| **接口测试** | 后端 Controller 层 MockMvc | 通过 `MockMvc.perform(get/post(...))` 模拟 HTTP 请求，断言 status、jsonPath、响应体，等价于 API 级验证。 |
| **UI / E2E** | Playwright 在 `web/e2e/` 下执行 | 覆盖页面加载、路由、主导航、关键页可访问性等，可选启动本地 dev server。 |
| **报告整合** | 统一目录 `test-reports/` + 汇总页 `index.html` | 后端 Surefire HTML → `test-reports/java/`；前端 Vitest 报告与覆盖率 → `test-reports/frontend/`；E2E Playwright 报告 → `test-reports/e2e/`；汇总页提供各层报告链接，便于本地或 CI 一键查看。 |

**入口统一**：在项目根目录执行 `npm run test:all` 或 `.\scripts\run-all-tests.ps1`（及对应 shell 脚本），按顺序执行“后端测试 → 前端 UT+覆盖率 → E2E”，任一层失败即终止；全部通过后，所有报告已写入 `test-reports/`，可直接打开 `test-reports/index.html` 跳转各层报告。从而在**测试类型**（UT、接口、UI）和**产出**（报告）两方面都实现了整合。

---

## 1. 测试框架总体架构

项目采用**三层测试**：后端单元/API 测试（JUnit 5 + Maven Surefire）、前端单元测试（Vitest）、E2E/UI 测试（Playwright）。报告统一输出到 `test-reports/`，由根目录脚本或 `npm run test:all` 顺序驱动。

```plantuml
@startuml
!theme plain
skinparam backgroundColor #FEFEFE
skinparam componentStyle rectangle

package "测试入口" {
  [npm run test:all] as npm
  [run-all-tests.ps1 / .sh] as script
}

package "后端测试" {
  [Maven Surefire] as surefire
  [JUnit 5] as junit
  [MockMvc / Mockito] as mock
  [target/surefire-reports] as surefireOut
  [test-reports/java] as javaReport
}

package "前端测试" {
  [Vitest] as vitest
  [Playwright] as playwright
  [test-reports/frontend] as feReport
  [test-reports/e2e] as e2eReport
}

package "报告汇总" {
  [test-reports/index.html] as index
}

npm --> script
script --> surefire
surefire --> junit
junit --> mock
surefire --> surefireOut
surefire --> javaReport
script --> vitest
vitest --> feReport
script --> playwright
playwright --> e2eReport
javaReport --> index
feReport --> index
e2eReport --> index

@enduml
```

---

## 2. 一键测试执行流程（活动图）

执行 `npm run test:all` 或 `.\scripts\run-all-tests.ps1` 时，各层测试按固定顺序执行，任一层失败则终止并返回非零退出码。

```plantuml
@startuml
|#LightBlue| 脚本/入口 |
start
:执行 npm run test:all 或 run-all-tests;
:解析工作目录为项目根;

|#LightGreen| 后端 |
:mvn test -q;
if (Maven 退出码 = 0?) then (否)
  :输出 "后端测试失败";
  stop
endif
:Surefire 运行 *Test.java / *IT.java;
:生成 target/surefire-reports (XML/文本);
:report-only 生成 test-reports/java/surefire-report.html;

|#LightYellow| 前端 UT |
:cd web;
:npm run test:coverage;
if (Vitest 退出码 = 0?) then (否)
  :输出 "前端单元测试失败";
  stop
endif
:Vitest 执行 *.{test,spec}.{ts,tsx};
:输出 junit.xml + coverage 到 test-reports/frontend;
:node scripts/generate-simple-report.cjs;
:生成 test-reports/frontend/report.html;

|#Lavender| E2E |
:npm run test:e2e;
if (Playwright 退出码 = 0?) then (否)
  stop
endif
:Playwright 运行 e2e/*.spec.ts;
:输出 HTML + junit 到 test-reports/e2e;

|#LightBlue| 结束 |
:回到项目根;
:输出 "全部测试通过" 与报告目录;
stop
@enduml
```

---

## 3. 后端测试发现与运行逻辑（组件图）

Maven Surefire 根据 `pom.xml` 中的 include/exclude 模式发现测试类，运行阶段绑定在 `test`，报告由 `maven-surefire-report-plugin` 在同阶段通过 `report-only` 从 Surefire 的 XML 生成。

```plantuml
@startuml
skinparam backgroundColor #FEFEFE

package "pom.xml 配置" {
  component "maven-surefire-plugin" as surefirePlugin {
    [includes: *Test.java, *Tests.java, *IT.java, *ITCase.java]
    [excludes: QueryDebugTest.java]
  }
  component "maven-surefire-report-plugin" as reportPlugin {
    [phase: test]
    [goal: report-only]
    [outputDirectory: test.reports.dir]
    [outputName: surefire-report]
  }
}

package "运行期" {
  component "Surefire" as surefire {
    [扫描 src/test/java]
    [执行 JUnit 5 引擎]
    [写 target/surefire-reports/*.xml]
  }
  component "JUnit 5" as junit {
    [@Test / @BeforeEach]
    [Assertions / MockMvc]
  }
}

package "输出" {
  component "target/surefire-reports" as tgt
  component "test-reports/java" as out
}

surefirePlugin --> surefire
surefire --> junit
surefire --> tgt
reportPlugin ..> tgt : 读取 XML
reportPlugin --> out : 写入 HTML

@enduml
```

---

## 4. 后端单元测试与被测对象关系（类图）

后端测试**不启动完整 Spring 上下文**，采用 **Standalone MockMvc + Mockito**：Controller 测试手动实例化 Controller，将依赖的 Service 用 `mock()` 注入；Service 测试则直接 new Service 并 mock 其依赖（如 Loader）。这样避免依赖数据库、Neo4j 等外部资源。

```plantuml
@startuml
skinparam backgroundColor #FEFEFE

package "被测类 (src/main)" {
  class HealthController {
    + getHealth()
  }
  class SchemaController {
    - schemaService : SchemaService
    - testService : DataSourceTestService
    + listObjectTypes()
    + getObjectType(name)
    + testConnection(req)
  }
  class SchemaService {
    - loader : Loader
    + listObjectTypes()
    + getObjectType(name)
  }
  class Loader {
    + load()
    + getObjectType(name)
    + listObjectTypes()
  }
  class ApiResponse<T> {
    + success(data)
    + error(code, message)
  }
  SchemaController --> SchemaService : 依赖
  SchemaService --> Loader : 依赖
}

package "测试类 (src/test)" {
  class HealthControllerTest {
    - mockMvc : MockMvc
    + setUp()
    + health_returnsOkAndStatus()
  }
  class SchemaControllerTest {
    - mockMvc : MockMvc
    - schemaService : SchemaService
    - testService : DataSourceTestService
    + setUp()
    + listObjectTypes_returnsOkAndList()
    + getObjectType_whenNotFound_returns404()
  }
  class SchemaServiceTest {
    - loader : Loader
    - schemaService : SchemaService
    + listObjectTypes_delegatesToLoader()
    + getObjectType_notFound_throws()
  }
  class LoaderTest {
    - schemaPath : String
    + getObjectType_afterLoad_returnsType()
    + listObjectTypes_afterLoad_returnsAll()
  }
  class ApiResponseTest {
    + success_returns200AndData()
    + error_returnsCodeAndMessage()
  }
}

HealthControllerTest ..> HealthController : 测试
SchemaControllerTest ..> SchemaController : 测试
SchemaControllerTest ..> SchemaService : mock
SchemaServiceTest ..> SchemaService : 测试
SchemaServiceTest ..> Loader : mock
LoaderTest ..> Loader : 测试
ApiResponseTest ..> ApiResponse : 测试

note right of SchemaControllerTest
  MockMvcBuilders.standaloneSetup(
    new SchemaController(schemaService, testService)
  ).build()
end note

@enduml
```

---

## 5. Controller 测试请求-响应序列（序列图）

以 Schema 相关接口为例：测试构造 GET 请求，MockMvc 将请求交给被测 Controller，Controller 调用被 mock 的 SchemaService，返回预置数据，测试再对响应做 status/jsonPath 断言。

```plantuml
@startuml
participant "SchemaControllerTest" as Test
participant "MockMvc" as Mvc
participant "SchemaController" as Ctrl
participant "SchemaService\n(mock)" as Svc

Test -> Test : setUp()\nmock(SchemaService)\nstandaloneSetup(controller)
Test -> Mvc : perform(get("/api/v1/schema/object-types"))
Mvc -> Ctrl : listObjectTypes()
Ctrl -> Svc : listObjectTypes()
Svc --> Ctrl : List<ObjectType> (stub)
Ctrl --> Mvc : ResponseEntity<ApiResponse>
Mvc --> Test : ResultActions
Test -> Test : andExpect(status().isOk())\nandExpect(jsonPath("$.data[0].name").value("Vehicle"))
Test -> Test : verify(schemaService).listObjectTypes()
@enduml
```

---

## 6. 前端测试与报告生成流程（活动图）

前端单元测试由 Vitest 执行，配置中指定了 junit、html 报告及 v8 覆盖率输出路径；测试结束后通过 `generate-simple-report.cjs` 解析 `junit.xml` 生成可 file:// 打开的 `report.html`。

```plantuml
@startuml
| 调用方 |
:cd web;
:npm run test:coverage;

| Vitest |
:vitest run --coverage;
:匹配 src/**/*.{test,spec}.{ts,tsx};
:jsdom 环境执行用例;
:收集覆盖率 (v8);

| 输出 |
:写 test-reports/frontend/junit.xml;
:写 test-reports/frontend/index.html (Vitest UI);
:写 test-reports/frontend/coverage/** (HTML/JSON);

| 后处理 |
:node scripts/generate-simple-report.cjs;
if (junit.xml 存在?) then (否)
  :跳过，exit 0;
  stop
endif
:解析 junit.xml (tests, failures, errors, time, testcase);
:生成 test-reports/frontend/report.html (单一 HTML);

stop
@enduml
```

---

## 7. E2E 测试与 Playwright 配置关系（部署图/组件图）

E2E 测试由 Playwright 在 `web/e2e/` 下执行，配置中指定报告输出到 `test-reports/e2e`，可选启动本地 dev server（非 CI 时）。

```plantuml
@startuml
skinparam backgroundColor #FEFEFE

package "playwright.config.ts" {
  component [testDir: ./e2e] as cfgDir
  component [reporters: html, junit] as reporters
  component [outputFolder: ../test-reports/e2e/playwright-report] as outHtml
  component [outputFile: ../test-reports/e2e/junit.xml] as outJunit
  component [webServer: npm run dev (非 CI)] as webServer
  component [projects: chromium] as proj
}

package "e2e 用例" {
  component [smoke.spec.ts] as smoke
  component [pages.spec.ts] as pages
  component [navigation.spec.ts] as nav
}

package "输出" {
  component [test-reports/e2e/playwright-report] as rep
  component [test-reports/e2e/junit.xml] as xml
}

reporters --> outHtml
reporters --> outJunit
cfgDir --> smoke
cfgDir --> pages
cfgDir --> nav
smoke --> rep
pages --> rep
nav --> rep
smoke --> xml
webServer --> smoke : 可选提供 baseURL

@enduml
```

---

## 8. 报告汇总与目录结构（静态结构）

所有报告最终汇聚到 `test-reports/`，由 `test-reports/index.html` 提供统一入口链接到各层 HTML 报告。

```plantuml
@startuml
skinparam backgroundColor #FEFEFE

together {
  rectangle "test-reports/" as root {
    rectangle "index.html\n(汇总入口)" as index
    rectangle "java/" as java {
      rectangle "surefire-report.html" as sf
      rectangle "*.xml, *.txt" as sfExtra
    }
    rectangle "frontend/" as fe {
      rectangle "report.html\n(可 file:// 打开)" as feReport
      rectangle "junit.xml" as feJunit
      rectangle "index.html\n(Vitest UI)" as feUi
      rectangle "coverage/" as cov {
        rectangle "index.html" as covIndex
      }
    }
    rectangle "e2e/" as e2e {
      rectangle "playwright-report/\nindex.html" as pw
      rectangle "junit.xml" as pwJunit
    }
  }
}

index -down-> sf : 链接
index -down-> feReport : 链接
index -down-> covIndex : 链接
index -down-> pw : 链接

@enduml
```

---

## 9. 测试分层与技术栈对照表

| 层级       | 工具/框架              | 发现规则                          | 报告输出位置 |
|------------|------------------------|-----------------------------------|--------------|
| 后端       | JUnit 5, Surefire, MockMvc, Mockito | `**/*Test.java`, `**/*IT.java` 等 | `test-reports/java/` |
| 前端 UT    | Vitest, jsdom, v8 覆盖率 | `src/**/*.{test,spec}.{ts,tsx}`   | `test-reports/frontend/` |
| E2E/UI     | Playwright             | `web/e2e/*.spec.ts`               | `test-reports/e2e/` |

---

## 10. 小结

- **后端**：通过 Maven Surefire 聚合 JUnit 5，测试采用 Standalone MockMvc + Mockito，不启 Spring 上下文，报告由 Surefire Report 插件从 XML 生成到 `test-reports/java`。
- **前端**：Vitest 负责单元测试与覆盖率，报告与 junit.xml 写入 `test-reports/frontend`，再由 `generate-simple-report.cjs` 生成可离线打开的 `report.html`。
- **E2E**：Playwright 在 `web/e2e` 执行，报告与 JUnit XML 写入 `test-reports/e2e`。
- **统一入口**：`npm run test:all` 或 `scripts/run-all-tests.ps1/.sh` 按顺序执行上述三层，`test-reports/index.html` 提供报告汇总链接。

以上 PlantUML 图块均按 PlantUML 语法编写，可在支持 PlantUML 的编辑器或在线服务中直接渲染。
