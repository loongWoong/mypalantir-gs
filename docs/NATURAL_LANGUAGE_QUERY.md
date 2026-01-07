# 自然语言查询功能

## 概述

自然语言查询功能允许用户使用自然语言描述查询需求，系统会自动将其转换为结构化的 Ontology 查询 DSL 并执行。

## 配置

### 方式 1: 使用 .env 文件（推荐）

在项目根目录创建 `.env` 文件（已自动添加到 .gitignore）：

```bash
# DeepSeek API Configuration
LLM_API_KEY=your-api-key-here
LLM_API_URL=https://api.deepseek.com/v1/chat/completions
LLM_MODEL=deepseek-chat
```

### 方式 2: 在 application.properties 中配置

```properties
# LLM Configuration (for Natural Language Query)
llm.api.key=your-api-key-here
llm.api.url=https://api.deepseek.com/v1/chat/completions
llm.model=deepseek-chat
llm.temperature=0.1
llm.max.retries=3
llm.timeout=30000
```

**配置优先级**：`.env` 文件 > `application.properties` > 默认值

### 配置说明

- `LLM_API_KEY` / `llm.api.key`: LLM API 密钥（必需）
- `LLM_API_URL` / `llm.api.url`: LLM API 端点（默认：DeepSeek）
- `LLM_MODEL` / `llm.model`: 使用的模型（默认：deepseek-chat）
- `llm.temperature`: 温度参数，控制输出的随机性（默认：0.1，较低值更稳定）
- `llm.max.retries`: 最大重试次数（默认：3）
- `llm.timeout`: 超时时间（毫秒，默认：30000）

### 支持的 LLM 提供商

#### DeepSeek（默认配置）
```bash
# .env
LLM_API_KEY=sk-xxx
LLM_API_URL=https://api.deepseek.com/v1/chat/completions
LLM_MODEL=deepseek-chat
```

#### OpenAI
```bash
# .env
LLM_API_KEY=sk-xxx
LLM_API_URL=https://api.openai.com/v1/chat/completions
LLM_MODEL=gpt-4
```

#### 其他 OpenAI 兼容的 API
```bash
# .env
LLM_API_KEY=your-key
LLM_API_URL=https://your-api-endpoint.com/v1/chat/completions
LLM_MODEL=your-model
```

## API 端点

### 1. 执行自然语言查询

**POST** `/api/v1/query/natural-language`

将自然语言查询转换为 OntologyQuery 并执行。

**请求体：**
```json
{
  "query": "显示每个收费站的总收费金额，按金额降序排列"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "query": "显示每个收费站的总收费金额，按金额降序排列",
    "convertedQuery": {
      "object": "收费站",
      "links": [{"name": "拥有收费记录"}],
      "group_by": ["名称"],
      "metrics": [["sum", "拥有收费记录.金额", "总金额"]],
      "orderBy": [{"field": "总金额", "direction": "DESC"}]
    },
    "columns": ["名称", "总金额"],
    "rows": [
      ["收费站A", 15000.0],
      ["收费站B", 12000.0]
    ],
    "rowCount": 2
  }
}
```

### 2. 仅转换查询（不执行）

**POST** `/api/v1/query/natural-language/convert`

仅将自然语言查询转换为 OntologyQuery，不执行查询。用于调试和验证。

**请求体：**
```json
{
  "query": "显示所有收费站"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "query": "显示所有收费站",
    "convertedQuery": {
      "object": "收费站",
      "select": ["名称", "省份"]
    }
  }
}
```

## 使用示例

### 示例 1: 简单查询
```bash
curl -X POST http://localhost:8080/api/v1/query/natural-language \
  -H "Content-Type: application/json" \
  -d '{"query": "显示所有收费站"}'
```

### 示例 2: 聚合查询
```bash
curl -X POST http://localhost:8080/api/v1/query/natural-language \
  -H "Content-Type: application/json" \
  -d '{"query": "显示每个收费站的总收费金额"}'
```

### 示例 3: 带过滤条件的查询
```bash
curl -X POST http://localhost:8080/api/v1/query/natural-language \
  -H "Content-Type: application/json" \
  -d '{"query": "显示江苏省的收费站"}'
```

### 示例 4: 时间范围查询
```bash
curl -X POST http://localhost:8080/api/v1/query/natural-language \
  -H "Content-Type: application/json" \
  -d '{"query": "显示2024年1月的收费记录"}'
```

### 示例 5: 多关联查询
```bash
curl -X POST http://localhost:8080/api/v1/query/natural-language \
  -H "Content-Type: application/json" \
  -d '{"query": "显示每个车辆的总收费金额"}'
```

## 工作原理

1. **Ontology Schema 摘要生成**：系统从 Ontology Schema 生成结构化的 JSON 摘要，包含所有对象类型、属性、链接类型等信息。

2. **Prompt 构建**：系统构建包含以下内容的 Prompt：
   - Ontology Schema 摘要
   - 查询 DSL 格式说明
   - 字段路径规则
   - Few-shot 示例

3. **LLM 调用**：调用配置的 LLM API，将自然语言查询转换为 JSON 格式的查询 DSL。

4. **查询验证**：验证转换后的查询是否符合 Ontology Schema。

5. **查询执行**：将验证通过的查询转换为 OntologyQuery 并执行。

## 优化特性

### 1. 缓存机制
- Ontology Schema 摘要缓存 5 分钟，避免频繁生成
- 当 Schema 更新时，缓存会自动失效

### 2. 重试机制
- 支持自动重试（默认 3 次）
- 使用指数退避策略

### 3. 错误处理
- 详细的错误信息
- JSON 响应清理（移除 markdown 代码块标记）
- 查询验证确保转换结果的正确性

### 4. Few-shot 学习
- Prompt 中包含多个示例，帮助 LLM 更好地理解查询格式

## 注意事项

1. **API 密钥**：确保正确配置 LLM API 密钥
2. **成本控制**：LLM API 调用会产生费用，建议设置合理的超时和重试次数
3. **准确性**：自然语言查询的准确性取决于：
   - LLM 模型的能力
   - Ontology Schema 的完整性和描述性
   - 查询的复杂程度

## 故障排查

### 错误：LLM API key not configured
- 检查 `application.properties` 中的 `llm.api.key` 配置

### 错误：LLM API call failed
- 检查网络连接
- 验证 API 密钥是否有效
- 检查 API 端点是否正确

### 错误：自然语言查询转换失败
- 检查查询文本是否清晰
- 查看日志中的 LLM 响应
- 尝试使用 `/convert` 端点查看转换结果

