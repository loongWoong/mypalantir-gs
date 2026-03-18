import { useEffect, useState } from 'react';
import AgentChat from './AgentChat';
import { agentConversationApi, type ConversationSummary } from '../api/client';

export default function AgentChatPage() {
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);

  useEffect(() => {
    agentConversationApi.list().then((list) => {
      setConversations(list);
      if (list.length > 0) {
        setActiveId(list[0].id);
      } else {
        // 没有历史会话时自动创建一个
        agentConversationApi.create().then((conv) => {
          setConversations([conv]);
          setActiveId(conv.id);
        });
      }
    });
  }, []);

  const handleNew = async () => {
    const conv = await agentConversationApi.create();
    setConversations((prev) => [conv, ...prev]);
    setActiveId(conv.id);
  };

  return (
    <div className="h-full flex">
      {/* 左侧会话列表 */}
      <div className="w-64 border-r border-gray-200 flex flex-col">
        <div className="p-3 flex items-center justify-between border-b border-gray-100">
          <span className="text-sm font-medium text-gray-700">对话历史</span>
          <button
            onClick={handleNew}
            className="text-xs px-2 py-1 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            新建
          </button>
        </div>
        <div className="flex-1 overflow-y-auto">
          {conversations.map((c) => (
            <button
              key={c.id}
              onClick={() => setActiveId(c.id)}
              className={`w-full text-left px-3 py-2 text-sm border-b border-gray-50 hover:bg-gray-50 ${
                c.id === activeId ? 'bg-blue-50 text-blue-700' : 'text-gray-700'
              }`}
            >
              <div className="truncate font-medium">{c.title}</div>
              {c.lastPreview && (
                <div className="truncate text-xs text-gray-400 mt-0.5">{c.lastPreview}</div>
              )}
            </button>
          ))}
        </div>
      </div>

      {/* 右侧当前会话 */}
      <div className="flex-1">
        {activeId && <AgentChat conversationId={activeId} key={activeId} />}
        {!activeId && (
          <div className="h-full flex items-center justify-center text-gray-400 text-sm">
            请选择左侧对话或新建对话
          </div>
        )}
      </div>
    </div>
  );
}

