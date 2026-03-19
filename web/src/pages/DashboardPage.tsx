import { useState, useRef, useEffect, useCallback } from 'react';
import { dashboardApi, naturalLanguageQueryApi, type WidgetSpec, type WidgetOperation } from '../api/client';
import WidgetRenderer, { type WidgetState } from '../components/WidgetRenderer';

interface ThinkingStep {
  step: string;
  content: string;
}

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  thinkingSteps?: ThinkingStep[];
  loading?: boolean;
}

const EXAMPLE_QUESTIONS = [
  '展示各收费站的通行数量柱状图',
  '添加一个今日通行总量的指标卡',
  '展示各入口站通行数量的饼图',
  '用表格展示最近10条通行记录',
];

const SIZE_CLASSES: Record<string, string> = {
  '1x1': 'col-span-6 row-span-1',
  '2x1': 'col-span-12 row-span-1',
  '2x2': 'col-span-12 row-span-2',
  '1x2': 'col-span-6 row-span-2',
};

// metric 卡片用较小的行高，通过 row-span 控制
const ROW_HEIGHT = 240; // 基础行高，row-span-1=240px, row-span-2=480px+gap

export default function DashboardPage() {
  const [widgets, setWidgets] = useState<WidgetState[]>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const fetchWidgetData = useCallback(async (widgetId: string, query: string) => {
    setWidgets(prev => prev.map(w =>
      w.id === widgetId ? { ...w, loading: true, error: null } : w
    ));
    try {
      const result = await naturalLanguageQueryApi.execute(query);
      setWidgets(prev => prev.map(w =>
        w.id === widgetId ? { ...w, data: result, loading: false } : w
      ));
    } catch (e: any) {
      const msg = e.response?.data?.message || e.message || '数据加载失败';
      setWidgets(prev => prev.map(w =>
        w.id === widgetId ? { ...w, error: msg, loading: false } : w
      ));
    }
  }, []);

  const applyOperations = useCallback((operations: WidgetOperation[]) => {
    setWidgets(prev => {
      let updated = [...prev];
      for (const op of operations) {
        if (op.action === 'add' && op.spec) {
          const newWidget: WidgetState = {
            id: op.widgetId,
            spec: op.spec as WidgetSpec,
            data: null,
            loading: true,
            error: null,
          };
          updated.push(newWidget);
        } else if (op.action === 'update' && op.spec) {
          updated = updated.map(w => {
            if (w.id !== op.widgetId) return w;
            const mergedSpec = { ...w.spec, ...op.spec } as WidgetSpec;
            if (op.spec.options) {
              mergedSpec.options = { ...w.spec.options, ...op.spec.options };
            }
            const queryChanged = op.spec.query && op.spec.query !== w.spec.query;
            return { ...w, spec: mergedSpec, loading: !!queryChanged };
          });
        } else if (op.action === 'remove') {
          updated = updated.filter(w => w.id !== op.widgetId);
        }
      }
      return updated;
    });

    // 触发数据加载
    for (const op of operations) {
      if (op.action === 'add' && op.spec?.query) {
        fetchWidgetData(op.widgetId, op.spec.query);
      } else if (op.action === 'update' && op.spec?.query) {
        fetchWidgetData(op.widgetId, op.spec.query);
      }
    }
  }, [fetchWidgetData]);

  const sendMessage = (text?: string) => {
    const message = text || input.trim();
    if (!message || loading) return;

    setInput('');
    setMessages(prev => [...prev,
      { role: 'user', content: message },
      { role: 'assistant', content: '', thinkingSteps: [], loading: true },
    ]);
    setLoading(true);

    const currentWidgetSpecs = widgets.map(w => ({ id: w.id, spec: w.spec }));

    const cancel = dashboardApi.chatStream(
      message,
      currentWidgetSpecs,
      (event) => {
        if (event.type === 'thinking') {
          setMessages(prev => {
            const updated = [...prev];
            const last = { ...updated[updated.length - 1] };
            last.thinkingSteps = [...(last.thinkingSteps || []), event.data as ThinkingStep];
            updated[updated.length - 1] = last;
            return updated;
          });
        } else if (event.type === 'widget_ops') {
          applyOperations(event.data as WidgetOperation[]);
        } else if (event.type === 'message') {
          setMessages(prev => {
            const updated = [...prev];
            const last = { ...updated[updated.length - 1] };
            last.content = event.data.message;
            last.loading = false;
            updated[updated.length - 1] = last;
            return updated;
          });
          setLoading(false);
        } else if (event.type === 'error') {
          setMessages(prev => {
            const updated = [...prev];
            const last = { ...updated[updated.length - 1] };
            last.content = '错误: ' + event.data.message;
            last.loading = false;
            updated[updated.length - 1] = last;
            return updated;
          });
          setLoading(false);
        }
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
      }
    );
    cancelRef.current = cancel;
  };

  return (
    <div className="h-full flex">
      {/* Chat Panel */}
      <div className="w-80 flex-shrink-0 border-r border-gray-200 bg-white flex flex-col">
        <div className="flex-1 overflow-y-auto p-3 space-y-3">
          {messages.length === 0 && (
            <div className="flex flex-col items-center justify-center h-full text-gray-400">
              <div className="text-3xl mb-3">📊</div>
              <div className="text-sm font-medium mb-1">AI Dashboard</div>
              <div className="text-xs mb-4 text-center px-2">描述你想看的数据和图表，自动生成仪表盘</div>
              <div className="flex flex-col gap-2 w-full px-2">
                {EXAMPLE_QUESTIONS.map((q, i) => (
                  <button key={i} onClick={() => sendMessage(q)}
                    className="px-3 py-2 text-xs border border-gray-300 rounded-lg hover:bg-gray-50 text-gray-700 text-left">
                    {q}
                  </button>
                ))}
              </div>
            </div>
          )}
          {messages.map((msg, idx) => (
            <div key={idx} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[90%] px-3 py-2 text-xs ${msg.role === 'user'
                ? 'bg-blue-600 text-white rounded-2xl rounded-br-sm'
                : 'bg-gray-100 text-gray-700 rounded-2xl rounded-bl-sm'
              }`}>
                {msg.role === 'assistant' && msg.thinkingSteps && msg.thinkingSteps.length > 0 && (
                  <ThinkingPanel steps={msg.thinkingSteps} streaming={msg.loading} />
                )}
                {msg.content && <div className={msg.thinkingSteps?.length ? 'mt-2 pt-2 border-t border-gray-200' : ''}>{msg.content}</div>}
                {msg.loading && !msg.thinkingSteps?.length && (
                  <div className="flex items-center gap-1">
                    <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                    <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                    <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                  </div>
                )}
              </div>
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>
        <div className="border-t border-gray-200 p-3">
          <div className="flex gap-2">
            <input type="text" value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && sendMessage()}
              placeholder="描述你想看的数据..."
              disabled={loading}
              className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50"
            />
            <button onClick={() => sendMessage()} disabled={loading || !input.trim()}
              className="px-3 py-2 bg-blue-600 text-white rounded-lg text-xs font-medium hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed">
              发送
            </button>
          </div>
        </div>
      </div>

      {/* Widget Grid */}
      <div className="flex-1 overflow-y-auto bg-gray-50 p-4">
        {widgets.length === 0 ? (
          <div className="flex items-center justify-center h-full text-gray-300">
            <div className="text-center">
              <div className="text-5xl mb-4">📈</div>
              <div className="text-lg">在左侧输入你想看的数据</div>
              <div className="text-sm mt-1">AI 会自动生成图表</div>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-12 gap-4" style={{ gridAutoRows: `${ROW_HEIGHT}px` }}>
            {widgets.map(w => (
              <div key={w.id}
                className={`${SIZE_CLASSES[w.spec.size] || 'col-span-12 row-span-1'} bg-white rounded-lg shadow-sm border border-gray-200 p-4`}>
                <WidgetRenderer widget={w} />
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

const STEP_ICONS: Record<string, string> = {
  analyze: '🔍',
  llm: '🤖',
  parse: '⚙️',
  apply: '✅',
};

function ThinkingPanel({ steps, streaming }: { steps: ThinkingStep[]; streaming?: boolean }) {
  return (
    <div className="space-y-1">
      {steps.map((s, i) => (
        <div key={i} className="flex items-start gap-1.5">
          <span className="flex-shrink-0">{STEP_ICONS[s.step] || '💭'}</span>
          <span className="text-gray-500">{s.content}</span>
        </div>
      ))}
      {streaming && (
        <div className="flex items-center gap-1 text-gray-400 ml-5">
          <div className="w-1 h-1 bg-blue-400 rounded-full animate-pulse" />
          <span>思考中...</span>
        </div>
      )}
    </div>
  );
}