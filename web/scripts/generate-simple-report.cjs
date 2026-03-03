/**
 * 根据 test-reports/frontend/junit.xml 生成单一 HTML 报告，可直接用 file:// 打开，无 CORS 问题。
 * 运行：在项目根目录执行 node web/scripts/generate-simple-report.cjs
 */
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..', '..');
const junitPath = path.join(root, 'test-reports', 'frontend', 'junit.xml');
const outPath = path.join(root, 'test-reports', 'frontend', 'report.html');

if (!fs.existsSync(junitPath)) {
  console.warn('junit.xml 不存在，跳过生成 report.html:', junitPath);
  process.exit(0);
}

const xml = fs.readFileSync(junitPath, 'utf8');
const testsMatch = xml.match(/tests="(\d+)"\s+failures="(\d+)"\s+errors="(\d+)"\s+time="([^"]+)"/);
const tests = testsMatch ? parseInt(testsMatch[1], 10) : 0;
const failures = testsMatch ? parseInt(testsMatch[2], 10) : 0;
const errors = testsMatch ? parseInt(testsMatch[3], 10) : 0;
const time = testsMatch ? testsMatch[4] : '0';

const cases = [];
const caseBlockRegex = /<testcase[^>]*classname="([^"]*)"[^>]*name="([^"]*)"[^>]*time="([^"]*)"[^>]*>[\s\S]*?<\/testcase>/g;
let m;
while ((m = caseBlockRegex.exec(xml)) !== null) {
  const block = m[0];
  const failed = /<(failure|error)/.test(block);
  cases.push({ classname: m[1], name: m[2], time: m[3], failed });
}

const totalFailures = failures + errors;
const passed = tests - totalFailures;

const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Vitest 测试报告</title>
  <style>
    * { box-sizing: border-box; }
    body { font-family: system-ui, sans-serif; margin: 0; padding: 1.5rem; background: #0f172a; color: #e2e8f0; line-height: 1.5; }
    h1 { font-size: 1.5rem; margin: 0 0 1rem; }
    .summary { display: flex; gap: 1rem; flex-wrap: wrap; margin-bottom: 1.5rem; }
    .stat { padding: 0.5rem 1rem; border-radius: 8px; }
    .stat.total { background: #1e293b; }
    .stat.passed { background: #14532d; color: #86efac; }
    .stat.failed { background: #7f1d1d; color: #fca5a5; }
    table { width: 100%; border-collapse: collapse; background: #1e293b; border-radius: 8px; overflow: hidden; }
    th, td { padding: 0.5rem 0.75rem; text-align: left; border-bottom: 1px solid #334155; }
    th { background: #334155; font-weight: 600; }
    tr:last-child td { border-bottom: none; }
    .pass { color: #86efac; }
    .fail { color: #fca5a5; }
    .time { color: #94a3b8; font-size: 0.875rem; }
  </style>
</head>
<body>
  <h1>Vitest 测试报告</h1>
  <div class="summary">
    <span class="stat total">总用例: ${tests}</span>
    <span class="stat passed">通过: ${passed}</span>
    <span class="stat failed">失败: ${totalFailures}</span>
    <span class="stat total">耗时: ${time}s</span>
  </div>
  <table>
    <thead><tr><th>用例</th><th>状态</th><th>耗时</th></tr></thead>
    <tbody>
${cases.map(c => `      <tr><td>${escapeHtml(c.name)}</td><td class="${c.failed ? 'fail' : 'pass'}">${c.failed ? '失败' : '通过'}</td><td class="time">${c.time}s</td></tr>`).join('\n')}
    </tbody>
  </table>
</body>
</html>
`;

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, html, 'utf8');
console.log('已生成可 file:// 打开的单一报告:', outPath);
