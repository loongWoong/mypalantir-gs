## 项目第三方组件总览

本文件梳理本项目当前用到的主要第三方组件，按**存储 / 后端 / 前端 / Python 脚本**分类，便于后续维护与审计。

---

## 一、存储相关组件

- **关系型数据库**
  - **H2（内嵌数据库）**：`com.h2database:h2`（runtime）
  - **MySQL JDBC 驱动**：`com.mysql:mysql-connector-j:8.0.33`

- **图数据库**
  - **Neo4j Java Driver**：`org.neo4j.driver:neo4j-java-driver:5.15.0`

- **Python 访问数据库**
  - **PyMySQL**：`PyMySQL` / `PyMySQL>=1.1.0` / `PyMySQL==1.1.0`

---

## 二、Java 后端组件（Spring Boot）

来源：`pom.xml`

- **Web 与基础框架**
  - `org.springframework.boot:spring-boot-starter-web`
  - `org.springframework.boot:spring-boot-starter-jdbc`

- **配置与数据格式**
  - `org.yaml:snakeyaml`
  - `com.fasterxml.jackson.core:jackson-databind`
  - `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`
  - `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`

- **表达式与脚本执行**
  - `dev.cel:cel:0.9.1`（Google Common Expression Language）
  - `org.openjdk.nashorn:nashorn-core:15.4`（JavaScript 引擎）

- **标识、加密与安全**
  - `com.fasterxml.uuid:java-uuid-generator:4.3.0`
  - `org.bouncycastle:bcprov-jdk18on:1.78.1`

- **环境配置**
  - `io.github.cdimascio:dotenv-java:3.0.0`

- **查询/计算引擎**
  - `org.apache.calcite:calcite-core:1.37.0`

- **开发辅助**
  - `org.projectlombok:lombok`（optional）

- **测试与报告**
  - `org.springframework.boot:spring-boot-starter-test`
  - `io.qameta.allure:allure-junit5:2.29.0`
  - Maven 插件：`maven-surefire-plugin`，`maven-surefire-report-plugin`

---

## 三、前端组件（React + Vite）

来源：`web/package.json`

### 1）运行时依赖（dependencies）

- **UI / React 生态**
  - `react`，`react-dom`
  - `react-router-dom`
  - `@heroicons/react`
  - `react-markdown`，`remark-gfm`
  - `recharts`
  - `reactflow`
  - `react-force-graph-2d`

- **编辑器 / 表达式**
  - `@monaco-editor/react`
  - `monaco-editor`
  - `estree-util-is-identifier-name`

- **网络与工具**
  - `axios`
  - `extend`
  - `html2canvas`
  - `jspdf`

- **样式与构建流水线**
  - `tailwindcss`
  - `@tailwindcss/typography`
  - `postcss`
  - `autoprefixer`

- **文本处理小工具**
  - `space-separated-tokens`
  - `trough`

### 2）开发与测试依赖（devDependencies）

- **构建与开发**
  - `vite`
  - `@vitejs/plugin-react`
  - `typescript`

- **Lint 与代码质量**
  - `eslint`
  - `@eslint/js`
  - `typescript-eslint`
  - `eslint-plugin-react-hooks`
  - `eslint-plugin-react-refresh`
  - `globals`

- **测试与覆盖率**
  - `vitest`
  - `@vitest/ui`
  - `@vitest/coverage-v8`
  - `jsdom`

- **端到端测试与报告**
  - `@playwright/test`
  - `allure-vitest`
  - `allure-playwright`

- **类型定义**
  - `@types/node`
  - `@types/react`
  - `@types/react-dom`

---

## 四、Python 工具 / 脚本依赖

来源：`local/requirements.txt` 与 `scripts/requirements.txt`

- `PyMySQL` / `PyMySQL>=1.1.0` / `PyMySQL==1.1.0`
- `PyYAML` / `PyYAML>=6.0` / `PyYAML==6.0.1`

主要用于：

- 从 Python 脚本访问 MySQL 等数据库；
- 加载与解析 YAML（如本体文件、配置）。

