import { useState } from 'react';
import { 
  SparklesIcon, 
  PlayIcon, 
  DocumentTextIcon,
  ClipboardDocumentIcon,
  ArrowPathIcon,
  CheckCircleIcon,
  XCircleIcon
} from '@heroicons/react/24/outline';
import { naturalLanguageQueryApi, type NaturalLanguageQueryResponse } from '../api/client';

// 示例查询
const EXAMPLE_QUERIES = [
  {
    title: '简单查询',
    query: '显示所有收费站',
    description: '查询所有收费站的基本信息'
  },
  {
    title: '过滤查询',
    query: '显示江苏省的收费站',
    description: '按省份过滤收费站'
  },
  {
    title: '聚合查询',
    query: '显示每个收费站的总收费金额',
    description: '按收费站分组统计总金额'
  },
  {
    title: '排序查询',
    query: '显示每个收费站的总收费金额，按金额降序排列',
    description: '聚合查询并排序'
  },
  {
    title: '车辆查询',
    query: '显示所有车辆信息',
    description: '查询所有车辆'
  },
  {
    title: '收费记录查询',
    query: '显示所有收费记录',
    description: '查询所有收费记录'
  }
];

export default function NaturalLanguageQuery() {
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<NaturalLanguageQueryResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showConvertedQuery, setShowConvertedQuery] = useState(false);
  const [copied, setCopied] = useState(false);

  const handleExecute = async (queryText?: string) => {
    const queryToExecute = queryText || query;
    if (!queryToExecute.trim()) {
      setError('请输入查询内容');
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);
    setShowConvertedQuery(false);

    try {
      const response = await naturalLanguageQueryApi.execute(queryToExecute);
      setResult(response);
      setShowConvertedQuery(true);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || '查询执行失败');
      console.error('Query execution error:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleExampleClick = (exampleQuery: string) => {
    setQuery(exampleQuery);
    handleExecute(exampleQuery);
  };

  const handleCopyQuery = () => {
    if (result?.convertedQuery) {
      navigator.clipboard.writeText(JSON.stringify(result.convertedQuery, null, 2));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
      handleExecute();
    }
  };

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-6 py-4 flex-shrink-0">
        <div className="flex items-center space-x-3 mb-2">
          <SparklesIcon className="w-8 h-8 text-blue-600" />
          <h1 className="text-2xl font-bold text-gray-900">自然语言查询</h1>
        </div>
        <p className="text-gray-600 text-sm">
          使用自然语言描述您的查询需求，系统会自动将其转换为结构化查询并执行。
        </p>
      </div>

      {/* Main Content - Two Column Layout */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Column - Query Input and Examples */}
        <div className="w-1/2 border-r border-gray-200 overflow-y-auto p-6 space-y-6">
          {/* Query Input */}
          <div className="bg-white rounded-lg shadow-sm p-6">
            <label htmlFor="query-input" className="block text-sm font-medium text-gray-700 mb-2">
              输入查询
            </label>
            <div className="relative">
              <textarea
                id="query-input"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={handleKeyPress}
                placeholder="例如：显示每个收费站的总收费金额，按金额降序排列"
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 resize-none"
                rows={4}
              />
              <div className="absolute bottom-2 right-2 text-xs text-gray-400">
                {query.length > 0 && `${query.length} 字符`}
              </div>
            </div>
            <div className="mt-4 flex items-center justify-between">
              <div className="text-sm text-gray-500">
                提示：按 <kbd className="px-2 py-1 bg-gray-100 rounded text-xs">Ctrl/Cmd + Enter</kbd> 执行查询
              </div>
              <button
                onClick={() => handleExecute()}
                disabled={loading || !query.trim()}
                className="flex items-center space-x-2 px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
              >
                {loading ? (
                  <>
                    <ArrowPathIcon className="w-5 h-5 animate-spin" />
                    <span>查询中...</span>
                  </>
                ) : (
                  <>
                    <PlayIcon className="w-5 h-5" />
                    <span>执行查询</span>
                  </>
                )}
              </button>
            </div>
          </div>

          {/* Example Queries */}
          <div className="bg-white rounded-lg shadow-sm p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4 flex items-center">
              <DocumentTextIcon className="w-5 h-5 mr-2 text-gray-600" />
              示例查询
            </h2>
            <div className="grid grid-cols-1 gap-3">
              {EXAMPLE_QUERIES.map((example, index) => (
                <button
                  key={index}
                  onClick={() => handleExampleClick(example.query)}
                  disabled={loading}
                  className="text-left p-3 border border-gray-200 rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <div className="font-medium text-gray-900 mb-1 text-sm">{example.title}</div>
                  <div className="text-xs text-gray-600 mb-1">{example.description}</div>
                  <div className="text-xs text-blue-600 font-mono bg-blue-50 px-2 py-1 rounded">
                    {example.query}
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Error Display */}
          {error && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-start space-x-3">
              <XCircleIcon className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
              <div className="flex-1">
                <h3 className="text-sm font-medium text-red-800">查询失败</h3>
                <p className="text-sm text-red-700 mt-1">{error}</p>
              </div>
            </div>
          )}
        </div>

        {/* Right Column - Results */}
        <div className="w-1/2 overflow-y-auto p-6 space-y-6">
          {/* Converted Query Display */}
          {result && showConvertedQuery && (
            <div className="bg-white rounded-lg shadow-sm p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-gray-900 flex items-center">
                  <DocumentTextIcon className="w-5 h-5 mr-2 text-gray-600" />
                  转换后的查询
                </h2>
                <button
                  onClick={handleCopyQuery}
                  className="flex items-center space-x-2 px-3 py-1 text-sm text-gray-600 hover:text-gray-900 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  {copied ? (
                    <>
                      <CheckCircleIcon className="w-4 h-4 text-green-600" />
                      <span>已复制</span>
                    </>
                  ) : (
                    <>
                      <ClipboardDocumentIcon className="w-4 h-4" />
                      <span>复制</span>
                    </>
                  )}
                </button>
              </div>
              <pre className="bg-gray-50 p-4 rounded-lg overflow-x-auto text-xs max-h-60">
                <code>{JSON.stringify(result.convertedQuery, null, 2)}</code>
              </pre>
            </div>
          )}

          {/* Results Display */}
          {result && result.rows ? (
            <div className="bg-white rounded-lg shadow-sm p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-gray-900">查询结果</h2>
                <div className="text-sm text-gray-500">
                  共 {result.rowCount || 0} 条记录
                </div>
              </div>
              
              {result.rows.length === 0 ? (
                <div className="text-center py-8 text-gray-500">
                  没有找到匹配的记录
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-gray-200">
                    <thead className="bg-gray-50">
                      <tr>
                        {result.columns?.map((col, index) => (
                          <th
                            key={index}
                            className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
                          >
                            {col}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="bg-white divide-y divide-gray-200">
                      {result.rows.map((row, rowIndex) => (
                        <tr key={rowIndex} className="hover:bg-gray-50">
                          {result.columns?.map((col, colIndex) => (
                            <td
                              key={colIndex}
                              className="px-4 py-2 whitespace-nowrap text-sm text-gray-900"
                            >
                              {row[col] !== null && row[col] !== undefined
                                ? String(row[col])
                                : <span className="text-gray-400">null</span>}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          ) : !result && !error && !loading ? (
            /* Empty State */
            <div className="bg-white rounded-lg shadow-sm p-12 text-center h-full flex items-center justify-center">
              <div>
                <SparklesIcon className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                <h3 className="text-lg font-medium text-gray-900 mb-2">开始查询</h3>
                <p className="text-gray-500">
                  在左侧输入框中输入您的查询，或选择一个示例查询开始
                </p>
              </div>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

