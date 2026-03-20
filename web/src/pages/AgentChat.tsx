import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { agentApi, agentConversationApi, type AgentStep, type ChatHistoryMessage } from '../api/client';

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  steps?: AgentStep[];
  loading?: boolean;
}

const EXAMPLE_QUESTIONS = [
  '诊断 G000237004001020107102026030106075950 的拆分异常原因',
  '诊断 G181337002004020109102026030106071129 的拆分异常原因',
  '查询 G181337002004020109102026030106071129 的门架交易明细',
  '查询G181337002004020109102026030106071129所有通行记录',
  '统计每个入口站的通行数量',
];

interface AgentChatProps {
  conversationId: string;
}

// ──────────────────────────────────────────────
// 诊断卡片解析与渲染
// ──────────────────────────────────────────────

interface DiagnosisBlock {
  summary: string;
  sections: { title: string; content: string }[];
  queryHint: string;
}

/**
 * 前端格式规范化：对标记内部文本做节标题统一，作为后端规范化的最后一道防线。
 * 处理后端未覆盖到的边缘情况（如 LLM 在流式输出中产生的细微变体）。
 */
function normalizeInner(raw: string): string {
  let text = raw;
  // ### 一、xxx / **一、xxx** → ## 一、xxx
  text = text.replace(/^#{1,6}\s+((一|二|三|四)、[^\n]+)$/gm, '## $1');
  text = text.replace(/^\*{1,2}((一|二|三|四)、[^*\n]+)\*{1,2}\s*$/gm, '## $1');
  // 裸中文序号标题（行首无任何前缀，且该行不含 | 避免误匹配表格）
  text = text.replace(/^((一|二|三|四)、[^|\n]+)$/gm, (_, p1) =>
    p1.startsWith('## ') ? p1 : `## ${p1}`
  );
  // 一句话总结：去除 **...** 包裹 和行首 >
  const firstSection = text.indexOf('\n## ');
  if (firstSection !== -1) {
    const summaryPart = text.slice(0, firstSection)
      .replace(/\*{1,2}([^*]+)\*{1,2}/g, '$1')
      .replace(/^>\s*/gm, '');
    text = summaryPart + text.slice(firstSection);
  }
  return text;
}

/** 从 Answer 文本中提取 <!--DIAGNOSIS_START--> ... <!--DIAGNOSIS_END--> 块 */
function parseDiagnosis(text: string): DiagnosisBlock | null {
  // 容忍标记大小写、空格变体（后端已规范化，这里作为兜底）
  const normalized = text
    .replace(/<!--\s*DIAGNOSIS_START\s*-->/gi, '<!--DIAGNOSIS_START-->')
    .replace(/<!--\s*DIAGNOSIS_END\s*-->/gi,   '<!--DIAGNOSIS_END-->')
    .replace(/<!--\s*QUERY_HINT\s*-->/gi,       '<!--QUERY_HINT-->');

  const match = normalized.match(/<!--DIAGNOSIS_START-->([\s\S]*?)<!--DIAGNOSIS_END-->/);
  if (!match) return null;

  const inner = normalizeInner(match[1].trim());

  // 提取 <!--QUERY_HINT--> 之后的内容作为跳转提示
  const hintMatch = inner.match(/<!--QUERY_HINT-->\s*([\s\S]*?)$/);
  const queryHint = hintMatch ? hintMatch[1].trim() : '';
  const bodyWithoutHint = inner.replace(/<!--QUERY_HINT-->[\s\S]*$/, '').trim();

  // 第一行（## 之前）是一句话总结
  let rest = bodyWithoutHint;
  let summary = '';
  const firstHeading = bodyWithoutHint.indexOf('\n## ');
  if (firstHeading !== -1) {
    summary = bodyWithoutHint.slice(0, firstHeading).trim().replace(/\n/g, ' ');
    rest = bodyWithoutHint.slice(firstHeading).trim();
  } else {
    // 没有找到 ## 标题，整体作为 summary
    summary = bodyWithoutHint.trim();
    rest = '';
  }

  // 按 ## 标题分割各节
  const sectionRegex = /^## (.+)$/gm;
  const sections: { title: string; content: string }[] = [];
  let lastIndex = 0;
  let lastTitle = '';
  let m: RegExpExecArray | null;
  while ((m = sectionRegex.exec(rest)) !== null) {
    if (lastTitle) {
      sections.push({ title: lastTitle, content: rest.slice(lastIndex, m.index).trim() });
    }
    lastTitle = m[1].trim();
    lastIndex = m.index + m[0].length;
  }
  if (lastTitle) {
    sections.push({ title: lastTitle, content: rest.slice(lastIndex).trim() });
  }

  return { summary, sections, queryHint };
}

function DiagnosisCard({ block }: { block: DiagnosisBlock }) {
  const navigate = useNavigate();

  // 判断是否为异常（summary 含"异常"）
  const isAbnormal = block.summary.includes('异常');

  return (
    <div className="mt-1 rounded-xl border border-gray-200 overflow-hidden text-sm">
      {/* 一句话总结 */}
      <div className={`px-4 py-3 flex items-start gap-2 ${isAbnormal ? 'bg-red-50 border-b border-red-100' : 'bg-green-50 border-b border-green-100'}`}>
        <span className="mt-0.5 text-base">{isAbnormal ? '⚠️' : '✅'}</span>
        <p className={`font-medium leading-snug ${isAbnormal ? 'text-red-700' : 'text-green-700'}`}>
          {block.summary || '诊断完成'}
        </p>
      </div>

      {/* 各节内容 */}
      <div className="divide-y divide-gray-100">
        {block.sections.map((sec, i) => (
          <SectionBlock key={i} title={sec.title} content={sec.content} />
        ))}
      </div>

      {/* 跳转问数按钮 */}
      {block.queryHint && (
        <div className="px-4 py-3 bg-blue-50 border-t border-blue-100 flex items-center justify-between gap-3">
          <p className="text-blue-700 text-xs leading-snug flex-1">{block.queryHint}</p>
          <button
            onClick={() => navigate('/natural-language-query')}
            className="shrink-0 px-3 py-1.5 bg-blue-600 text-white text-xs rounded-lg hover:bg-blue-700 transition-colors font-medium"
          >
            去问数 →
          </button>
        </div>
      )}
    </div>
  );
}

function SectionBlock({ title, content }: { title: string; content: string }) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div>
      <button
        onClick={() => setCollapsed(c => !c)}
        className="w-full px-4 py-2 flex items-center justify-between bg-gray-50 hover:bg-gray-100 transition-colors text-left"
      >
        <span className="font-semibold text-gray-700 text-xs">{title}</span>
        <span className="text-gray-400 text-xs">{collapsed ? '▶' : '▼'}</span>
      </button>
      {!collapsed && (
        <div className="px-4 py-3 prose prose-sm prose-gray max-w-none text-xs">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
        </div>
      )}
    </div>
  );
}

// ──────────────────────────────────────────────
// 查询表格解析与渲染
// ──────────────────────────────────────────────

interface QueryTableBlock {
  title: string;
  columns: string[];
  rows: string[][];
  rawMarkdown: string; // 原始 Markdown，解析失败时降级使用
}

/** 解析 Markdown 表格为列/行结构 */
function parseMarkdownTable(md: string): { columns: string[]; rows: string[][] } | null {
  const lines = md.trim().split('\n').map(l => l.trim()).filter(Boolean);
  // 至少需要表头行 + 分隔行
  if (lines.length < 2) return null;
  const headerLine = lines[0];
  const sepLine = lines[1];
  if (!headerLine.startsWith('|') || !sepLine.match(/^\|[-| :]+\|/)) return null;

  const parseRow = (line: string) =>
    line.replace(/^\||\|$/g, '').split('|').map(c => c.trim());

  const columns = parseRow(headerLine);
  const rows = lines.slice(2).filter(l => l.startsWith('|')).map(parseRow);
  return { columns, rows };
}

/** 从 Answer 文本中提取 <!--QUERY_TABLE_START--> ... <!--QUERY_TABLE_END--> 块 */
function parseQueryTable(text: string): QueryTableBlock | null {
  const normalized = text
    .replace(/<!--\s*QUERY_TABLE_START\s*-->/gi, '<!--QUERY_TABLE_START-->')
    .replace(/<!--\s*QUERY_TABLE_END\s*-->/gi,   '<!--QUERY_TABLE_END-->');

  const match = normalized.match(/<!--QUERY_TABLE_START-->([\s\S]*?)<!--QUERY_TABLE_END-->/);
  if (!match) return null;

  const inner = match[1].trim();
  // 第一行（表格之前）是标题说明
  const tableStart = inner.indexOf('\n|');
  let title = '';
  let tableMarkdown = inner;
  if (tableStart !== -1) {
    title = inner.slice(0, tableStart).trim().replace(/^[>*#\s]+/, '');
    tableMarkdown = inner.slice(tableStart).trim();
  }

  const parsed = parseMarkdownTable(tableMarkdown);
  if (!parsed) {
    return { title, columns: [], rows: [], rawMarkdown: inner };
  }
  return { title, columns: parsed.columns, rows: parsed.rows, rawMarkdown: inner };
}

function QueryTableCard({ block }: { block: QueryTableBlock }) {
  const hasParsed = block.columns.length > 0;

  return (
    <div className="mt-1 rounded-xl border border-gray-200 overflow-hidden text-sm">
      {/* 标题栏 */}
      <div className="px-4 py-2.5 bg-gray-50 border-b border-gray-200 flex items-center gap-2">
        <span className="text-gray-500 text-sm">📋</span>
        <span className="font-medium text-gray-700 text-xs">
          {block.title || '查询结果'}
        </span>
        {hasParsed && (
          <span className="ml-auto text-[11px] text-gray-400">
            共 {block.rows.length} 条
          </span>
        )}
      </div>

      {/* 表格 */}
      {hasParsed ? (
        <div className="overflow-x-auto">
          <table className="w-full text-xs border-collapse">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                {block.columns.map((col, i) => (
                  <th
                    key={i}
                    className="px-3 py-2 text-left font-semibold text-gray-600 whitespace-nowrap border-r border-gray-100 last:border-r-0"
                  >
                    {col}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.length === 0 ? (
                <tr>
                  <td colSpan={block.columns.length} className="px-3 py-4 text-center text-gray-400">
                    暂无数据
                  </td>
                </tr>
              ) : (
                block.rows.map((row, ri) => (
                  <tr
                    key={ri}
                    className={`border-b border-gray-100 last:border-b-0 ${ri % 2 === 0 ? 'bg-white' : 'bg-gray-50/50'}`}
                  >
                    {block.columns.map((_, ci) => (
                      <td
                        key={ci}
                        className="px-3 py-2 text-gray-700 border-r border-gray-100 last:border-r-0 break-all"
                      >
                        {row[ci] ?? ''}
                      </td>
                    ))}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      ) : (
        // 解析失败降级为 Markdown
        <div className="px-4 py-3 prose prose-sm prose-gray max-w-none text-xs overflow-x-auto">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{block.rawMarkdown}</ReactMarkdown>
        </div>
      )}
    </div>
  );
}

/** 渲染 assistant 消息：查询表格 → 诊断卡片 → 普通 Markdown */
function AssistantContent({ content }: { content: string }) {
  // 优先检测查询表格块
  const queryTable = parseQueryTable(content);
  if (queryTable) {
    const before = content.slice(0, content.toLowerCase().indexOf('<!--query_table_start-->')).trim();
    return (
      <div>
        {before && (
          <div className="prose prose-sm prose-gray max-w-none mb-2">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{before}</ReactMarkdown>
          </div>
        )}
        <QueryTableCard block={queryTable} />
      </div>
    );
  }

  const diagnosis = parseDiagnosis(content);
  if (diagnosis) {
    const before = content.slice(0, content.indexOf('<!--DIAGNOSIS_START-->')).trim();
    return (
      <div>
        {before && (
          <div className="prose prose-sm prose-gray max-w-none mb-2">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{before}</ReactMarkdown>
          </div>
        )}
        <DiagnosisCard block={diagnosis} />
      </div>
    );
  }
  return (
    <div className="prose prose-sm prose-gray max-w-none">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
    </div>
  );
}

// ──────────────────────────────────────────────
// 主组件
// ──────────────────────────────────────────────

export default function AgentChat({ conversationId }: AgentChatProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    let cancelled = false;
    agentConversationApi.messages(conversationId).then((history: ChatHistoryMessage[]) => {
      if (cancelled) return;
      const mapped: ChatMessage[] = history.map((m) => ({
        role: m.role,
        content: m.content,
      }));
      setMessages(mapped);
    });
    return () => { cancelled = true; };
  }, [conversationId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    return () => { cancelRef.current?.(); };
  }, []);

  const sendMessage = (text?: string) => {
    const message = text || input.trim();
    if (!message || loading) return;

    setInput('');
    const userMsg: ChatMessage = { role: 'user', content: message };
    const assistantMsg: ChatMessage = { role: 'assistant', content: '', steps: [], loading: true };
    setMessages(prev => [...prev, userMsg, assistantMsg]);
    setLoading(true);

    const cancel = agentApi.chatStream(
      message,
      (event) => {
        setMessages(prev => {
          const updated = [...prev];
          const last = { ...updated[updated.length - 1] };

          if (event.type === 'step') {
            const stepData = event.data;
            const stepIndex: number = stepData.step;
            const step: AgentStep = {
              thought: stepData.thought,
              tool: stepData.tool,
              args: stepData.args,
              observation: stepData.observation,
            };
            const steps = [...(last.steps || [])];
            while (steps.length < stepIndex) steps.push({});
            steps[stepIndex - 1] = step;
            last.steps = steps;
          } else if (event.type === 'answer') {
            last.content = event.data.answer;
            last.loading = false;
          } else if (event.type === 'error') {
            last.content = '错误: ' + event.data.message;
            last.loading = false;
          }

          updated[updated.length - 1] = last;
          return updated;
        });
      },
      () => {
        setLoading(false);
        setMessages(prev => {
          const updated = [...prev];
          const last = { ...updated[updated.length - 1] };
          last.loading = false;
          updated[updated.length - 1] = last;
          return updated;
        });
      },
      conversationId,
    );

    cancelRef.current = cancel;
  };

  return (
    <div className="h-full w-full flex flex-col">
      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-gray-400">
            <div className="text-4xl mb-4">🤖</div>
            <div className="text-lg font-medium mb-2">智能助手</div>
            <div className="text-sm mb-6">基于本体知识的 ReAct 推理 Agent，支持数据查询与异常诊断</div>
            <div className="flex flex-wrap gap-2 justify-center">
              {EXAMPLE_QUESTIONS.map((q, i) => (
                <button
                  key={i}
                  onClick={() => sendMessage(q)}
                  className="px-3 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 text-gray-700"
                >
                  {q}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((msg, idx) => (
          <div key={idx} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div className={`max-w-[85%] ${msg.role === 'user'
              ? 'bg-blue-600 text-white rounded-2xl rounded-br-sm px-4 py-2'
              : 'bg-white border border-gray-200 rounded-2xl rounded-bl-sm px-4 py-3'
            }`}>
              {msg.loading && !msg.steps?.length ? (
                <div className="flex items-center gap-2 text-gray-500">
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                  <span className="text-sm ml-1">推理中...</span>
                </div>
              ) : (
                <>
                  {msg.content && (
                    msg.role === 'user' ? (
                      <div className="text-sm whitespace-pre-wrap">{msg.content}</div>
                    ) : (
                      <div className="text-sm">
                        <AssistantContent content={msg.content} />
                      </div>
                    )
                  )}
                  {msg.steps && msg.steps.length > 0 && (
                    <StepsPanel steps={msg.steps} streaming={msg.loading} />
                  )}
                  {msg.loading && msg.steps && msg.steps.length > 0 && (
                    <div className="mt-2 flex items-center gap-1 text-xs text-gray-400">
                      <div className="w-1.5 h-1.5 bg-blue-400 rounded-full animate-pulse" />
                      推理中...
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div className="border-t border-gray-200 bg-white p-4">
        <div className="flex gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && sendMessage()}
            placeholder="输入问题..."
            disabled={loading}
            className="flex-1 px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50"
          />
          <button
            onClick={() => sendMessage()}
            disabled={loading || !input.trim()}
            className="px-5 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed"
          >
            发送
          </button>
        </div>
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────
// 推理步骤面板（不变）
// ──────────────────────────────────────────────

function StepsPanel({ steps, streaming }: { steps: AgentStep[]; streaming?: boolean }) {
  const [manualToggle, setManualToggle] = useState<boolean | null>(null);
  const expanded = manualToggle !== null ? manualToggle : !!streaming;

  return (
    <div className="mt-3 border-t border-gray-100 pt-2">
      <button
        onClick={() => setManualToggle(expanded ? false : true)}
        className="text-xs text-gray-500 hover:text-gray-700 flex items-center gap-1"
      >
        <span className={`transition-transform ${expanded ? 'rotate-90' : ''}`}>▶</span>
        推理过程 ({steps.length} 步)
      </button>
      {expanded && (
        <div className="mt-2 space-y-2">
          {steps.map((step, idx) => (
            <div key={idx} className="text-xs border-l-2 border-gray-200 pl-3 py-1">
              {step.thought && (
                <div className="text-gray-600 mb-1">
                  <span className="font-medium text-gray-500">Thought: </span>
                  {step.thought}
                </div>
              )}
              {step.tool && (
                <div className="mb-1">
                  <span className="font-medium text-blue-600">Action: </span>
                  <code className="bg-blue-50 px-1 rounded">{step.tool}</code>
                  {step.args && (
                    <span className="text-gray-400 ml-1">
                      {JSON.stringify(step.args)}
                    </span>
                  )}
                </div>
              )}
              {step.observation ? (
                <div className="text-gray-500">
                  <span className="font-medium text-green-600">Observation: </span>
                  <span className="font-mono text-[11px] break-all">
                    {step.observation.length > 200
                      ? step.observation.substring(0, 200) + '...'
                      : step.observation}
                  </span>
                </div>
              ) : step.tool && (
                <div className="text-gray-400 flex items-center gap-1">
                  <div className="w-1.5 h-1.5 bg-yellow-400 rounded-full animate-pulse" />
                  <span className="text-[11px]">执行中...</span>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
