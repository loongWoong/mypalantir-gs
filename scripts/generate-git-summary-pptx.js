/**
 * 根据 Git Commit 记录生成改动点汇总 PPT
 * 使用 pptxgenjs
 */

const pptxgen = require("pptxgenjs");

const pres = new pptxgen();
pres.layout = "LAYOUT_16x9";
pres.author = "MyPalantir Team";
pres.title = "近期改动汇总";

// 配色：Ocean Gradient - 科技感
const PRIMARY = "065A82";
const SECONDARY = "1C7293";
const ACCENT = "02C39A";
const DARK = "21295C";
const LIGHT = "F8FAFC";

// 1. 封面
const slide1 = pres.addSlide();
slide1.background = { color: DARK };
slide1.addText("近期改动汇总", {
  x: 0.5,
  y: 2,
  w: 9,
  h: 1.2,
  fontSize: 44,
  fontFace: "Arial",
  color: "FFFFFF",
  bold: true,
  align: "center",
  valign: "middle"
});
slide1.addText("MyPalantir 项目 · 2026-03 更新", {
  x: 0.5,
  y: 3.3,
  w: 9,
  h: 0.5,
  fontSize: 18,
  fontFace: "Arial",
  color: "A5B4FC",
  align: "center"
});

// 2. 目录
const slide2 = pres.addSlide();
slide2.background = { color: LIGHT };
slide2.addShape(pres.shapes.RECTANGLE, {
  x: 0.5,
  y: 0.3,
  w: 0.08,
  h: 0.7,
  fill: { color: PRIMARY }
});
slide2.addText("目录", {
  x: 0.7,
  y: 0.35,
  w: 8,
  h: 0.6,
  fontSize: 32,
  fontFace: "Arial",
  color: DARK,
  bold: true,
  margin: 0
});
slide2.addText(
  [
    { text: "认证与路由增强", options: { bullet: true, breakLine: true } },
    { text: "推理引擎与 Agent 能力", options: { bullet: true, breakLine: true } },
    { text: "本体与知识图谱", options: { bullet: true, breakLine: true } },
    { text: "OBU 诊断与三层模型", options: { bullet: true, breakLine: true } },
    { text: "自然语言查询与 Agent UI", options: { bullet: true, breakLine: true } },
    { text: "收费系统集成", options: { bullet: true, breakLine: true } },
    { text: "测试与文档", options: { bullet: true } }
  ],
  { x: 0.5, y: 1.1, w: 9, h: 3.5, fontSize: 18, color: "334155", fontFace: "Arial" }
);

// 3. 认证与路由
const slide3 = pres.addSlide();
slide3.background = { color: "FFFFFF" };
slide3.addShape(pres.shapes.RECTANGLE, {
  x: 0.5,
  y: 0.3,
  w: 0.08,
  h: 0.7,
  fill: { color: PRIMARY }
});
slide3.addText("1. 认证与路由增强", {
  x: 0.7,
  y: 0.35,
  w: 8,
  h: 0.6,
  fontSize: 28,
  fontFace: "Arial",
  color: DARK,
  bold: true,
  margin: 0
});
slide3.addText(
  [
    { text: "实现认证与路由增强 (feat: implement authentication and routing enhancements)", options: { bullet: true, breakLine: true } },
    { text: "提升前后端鉴权与路由安全", options: { bullet: true } }
  ],
  { x: 0.5, y: 1.2, w: 9, h: 2, fontSize: 16, color: "475569", fontFace: "Arial" }
);
slide3.addText("相关提交", {
  x: 0.5,
  y: 3.5,
  w: 9,
  h: 0.4,
  fontSize: 14,
  fontFace: "Arial",
  color: SECONDARY,
  bold: true
});
slide3.addText("6f5f9d2 · 2026-03-17", {
  x: 0.5,
  y: 3.9,
  w: 9,
  h: 0.4,
  fontSize: 12,
  fontFace: "Consolas",
  color: "64748B"
});

// 4. 推理引擎与 Agent
const slide4 = pres.addSlide();
slide4.background = { color: "FFFFFF" };
slide4.addShape(pres.shapes.RECTANGLE, {
  x: 0.5,
  y: 0.3,
  w: 0.08,
  h: 0.7,
  fill: { color: PRIMARY }
});
slide4.addText("2. 推理引擎与 Agent 能力", {
  x: 0.7,
  y: 0.35,
  w: 8,
  h: 0.6,
  fontSize: 28,
  fontFace: "Arial",
  color: DARK,
  bold: true,
  margin: 0
});
slide4.addText(
  [
    { text: "集成 Nashorn JavaScript 引擎，支持本体脚本函数", options: { bullet: true, breakLine: true } },
    { text: "重构 AgentTools，通过构造函数注入 ObjectMapper，提升可测试性", options: { bullet: true, breakLine: true } },
    { text: "新增批量推理 API（同步/异步）", options: { bullet: true, breakLine: true } },
    { text: "ReasoningService 返回推理结果及关联数据摘要", options: { bullet: true, breakLine: true } },
    { text: "增强 FunctionDef / ReasoningService 参数处理与表达式求值", options: { bullet: true, breakLine: true } },
    { text: "基于实例的评估与函数测试", options: { bullet: true, breakLine: true } },
    { text: "合并 CEL 和函数实现，移除过时收费脚本函数", options: { bullet: true } }
  ],
  { x: 0.5, y: 1.15, w: 9, h: 4, fontSize: 15, color: "475569", fontFace: "Arial" }
);

// 5. 本体与知识图谱
const slide5 = pres.addSlide();
slide5.background = { color: "FFFFFF" };
slide5.addShape(pres.shapes.RECTANGLE, {
  x: 0.5,
  y: 0.3,
  w: 0.08,
  h: 0.7,
  fill: { color: PRIMARY }
});
slide5.addText("3. 本体与知识图谱", {
  x: 0.7,
  y: 0.35,
  w: 8,
  h: 0.6,
  fontSize: 28,
  fontFace: "Arial",
  color: DARK,
  bold: true,
  margin: 0
});
slide5.addText(
  [
    { text: "增强本体 schema，支持衍生属性与推理能力", options: { bullet: true, breakLine: true } },
    { text: "更新 ontology 与 SQL 脚本，优化数据处理", options: { bullet: true, breakLine: true } },
    { text: "Demo passage 推理 SQL 与应用环境加载", options: { bullet: true, breakLine: true } },
    { text: "移除废弃 ontology 文件，更新 schema controller", options: { bullet: true, breakLine: true } },
    { text: "Dome 服务集成，配置校验增强", options: { bullet: true } }
  ],
  { x: 0.5, y: 1.15, w: 9, h: 3.5, fontSize: 15, color: "475569", fontFace: "Arial" }
);

// 6. OBU 诊断
const slide6 = pres.addSlide();
slide6.background = { color: "FFFFFF" };
slide6.addShape(pres.shapes.RECTANGLE, {
  x: 0.5,
  y: 0.3,
  w: 0.08,
  h: 0.7,
  fill: { color: PRIMARY }
});
slide6.addText("4. OBU 拆分异常诊断", {
  x: 0.7,
  y: 0.35,
  w: 8,
  h: 0.6,
  fontSize: 28,
  fontFace: "Arial",
  color: DARK,
  bold: true,
  margin: 0
});
slide6.addText(
  [
    { text: "OBU 拆分异常诊断三层模型：衍生属性 / 函数 / 规则", options: { bullet: true, breakLine: true } },
    { text: "推理机理文档与诊断推理页面", options: { bullet: true, breakLine: true } },
    { text: "前向链推理引擎、SWRL 规则解析、内置函数", options: { bullet: true, breakLine: true } },
    { text: "移除过时的 OBU 拆分诊断文档", options: { bullet: true } }
  ],
  { x: 0.5, y: 1.15, w: 9, h: 3, fontSize: 15, color: "475569", fontFace: "Arial" }
);

// 7. 自然语言与 Agent UI
const slide7 = pres.addSlide();
slide7.background = { color: "FFFFFF" };
slide7.addShape(pres.shapes.RECTANGLE, {
  x: 0.5,
  y: 0.3,
  w: 0.08,
  h: 0.7,
  fill: { color: PRIMARY }
});
slide7.addText("5. 自然语言查询与 Agent UI", {
  x: 0.7,
  y: 0.35,
  w: 8,
  h: 0.6,
  fontSize: 28,
  fontFace: "Arial",
  color: DARK,
  bold: true,
  margin: 0
});
slide7.addText(
  [
    { text: "集成自然语言查询工具，优化 Agent 意图识别与知识召回", options: { bullet: true, breakLine: true } },
    { text: "添加 ReAct 诊断 Agent（SSE 流式）、Qwen3.5 集成", options: { bullet: true, breakLine: true } },
    { text: "dataSourceType 支持自然语言查询", options: { bullet: true, breakLine: true } },
    { text: "UI 组件本地化与表达式处理优化", options: { bullet: true, breakLine: true } },
    { text: "更新 Agent 页面标题和示例问题", options: { bullet: true } }
  ],
  { x: 0.5, y: 1.15, w: 9, h: 3.5, fontSize: 15, color: "475569", fontFace: "Arial" }
);

// 8. 收费系统
const slide8 = pres.addSlide();
slide8.background = { color: "FFFFFF" };
slide8.addShape(pres.shapes.RECTANGLE, {
  x: 0.5,
  y: 0.3,
  w: 0.08,
  h: 0.7,
  fill: { color: PRIMARY }
});
slide8.addText("6. 收费系统集成", {
  x: 0.7,
  y: 0.35,
  w: 8,
  h: 0.6,
  fontSize: 28,
  fontFace: "Arial",
  color: DARK,
  bold: true,
  margin: 0
});
slide8.addText(
  [
    { text: "合并 cls/feature/toll-system 分支", options: { bullet: true, breakLine: true } },
    { text: "收费系统相关功能集成到主开发流程", options: { bullet: true } }
  ],
  { x: 0.5, y: 1.2, w: 9, h: 2, fontSize: 16, color: "475569", fontFace: "Arial" }
);

// 9. 测试与文档
const slide9 = pres.addSlide();
slide9.background = { color: "FFFFFF" };
slide9.addShape(pres.shapes.RECTANGLE, {
  x: 0.5,
  y: 0.3,
  w: 0.08,
  h: 0.7,
  fill: { color: PRIMARY }
});
slide9.addText("7. 测试与文档", {
  x: 0.7,
  y: 0.35,
  w: 8,
  h: 0.6,
  fontSize: 28,
  fontFace: "Arial",
  color: DARK,
  bold: true,
  margin: 0
});
slide9.addText(
  [
    { text: "集成 Allure 测试报告", options: { bullet: true, breakLine: true } },
    { text: "清理过时文档", options: { bullet: true } }
  ],
  { x: 0.5, y: 1.2, w: 9, h: 2, fontSize: 16, color: "475569", fontFace: "Arial" }
);

// 10. 近期提交时间线
const slide10 = pres.addSlide();
slide10.background = { color: LIGHT };
slide10.addShape(pres.shapes.RECTANGLE, {
  x: 0.5,
  y: 0.3,
  w: 0.08,
  h: 0.7,
  fill: { color: PRIMARY }
});
slide10.addText("近期提交概览", {
  x: 0.7,
  y: 0.35,
  w: 8,
  h: 0.6,
  fontSize: 28,
  fontFace: "Arial",
  color: DARK,
  bold: true,
  margin: 0
});
slide10.addTable(
  [
    [
      { text: "日期", options: { fill: { color: PRIMARY }, color: "FFFFFF", bold: true } },
      { text: "提交", options: { fill: { color: PRIMARY }, color: "FFFFFF", bold: true } },
      { text: "说明", options: { fill: { color: PRIMARY }, color: "FFFFFF", bold: true } }
    ],
    ["03-17", "6083048", "修改"],
    ["03-17", "6f5f9d2", "认证与路由增强"],
    ["03-17", "cc15dd5", "移除 OBU 诊断文档"],
    ["03-16", "c46d768", "Nashorn 引擎与推理能力增强"],
    ["03-12", "7e42e6a", "AgentService/AgentTools 诊断消息"],
    ["03-12", "c8965b7", "本体与推理服务更新"],
    ["03-11", "e8b8eb9", "合并 CEL 和函数实现"],
    ["03-10", "026940e", "自然语言查询与 Agent 意图"],
    ["03-09", "2f3cb3d", "前向链推理引擎、SWRL、OBU 诊断"],
    ["03-06", "5d5ff74", "OBU 三层模型与推理机理"],
    ["03-05", "133d920", "Allure 测试报告"],
    ["03-04", "6f6bab1", "dataSourceType 支持 NL 查询"]
  ],
  {
    x: 0.5,
    y: 1.1,
    w: 9,
    colW: [0.8, 1.2, 7],
    fontSize: 12,
    fontFace: "Arial",
    color: "334155",
    border: { pt: 0.5, color: "E2E8F0" },
    align: "left",
    valign: "middle"
  }
);

// 11. 总结
const slide11 = pres.addSlide();
slide11.background = { color: DARK };
slide11.addText("总结", {
  x: 0.5,
  y: 1.5,
  w: 9,
  h: 1,
  fontSize: 36,
  fontFace: "Arial",
  color: "FFFFFF",
  bold: true,
  align: "center"
});
slide11.addText(
  "近期工作聚焦于推理引擎增强、Agent 能力提升、本体扩展、OBU 诊断模型、自然语言查询及收费系统集成。",
  {
    x: 0.5,
    y: 2.6,
    w: 9,
    h: 1.2,
    fontSize: 18,
    fontFace: "Arial",
    color: "E2E8F0",
    align: "center"
  }
);

pres.writeFile({ fileName: "近期改动汇总.pptx" }).then(() => {
  console.log("PPT 已生成: 近期改动汇总.pptx");
});
