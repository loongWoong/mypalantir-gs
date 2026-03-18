import { useState, useRef, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { agentApi, type AgentStep } from '../api/client';

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

export default function AgentChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [conversationId] = useState(() => crypto.randomUUID());
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

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
            // Replace or add step by index
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
      conversationId
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
                      <div className="text-sm prose prose-sm prose-gray max-w-none">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
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

function StepsPanel({ steps, streaming }: { steps: AgentStep[]; streaming?: boolean }) {
  // Auto-expand when streaming, allow manual toggle
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
