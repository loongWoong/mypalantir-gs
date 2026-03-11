import { useState, useCallback } from 'react';
import { reasoningApi } from '../api/client';
import { CheckCircleIcon, ExclamationCircleIcon, PlayIcon, DocumentTextIcon } from '@heroicons/react/24/outline';

const DEFAULT_EXPR = 'size(links.items) == 0';
const DEFAULT_CONTEXT = `{
  "properties": {},
  "linked_data": {}
}`;

export default function CelPlayground() {
  const [expr, setExpr] = useState(DEFAULT_EXPR);
  const [contextJson, setContextJson] = useState(DEFAULT_CONTEXT);
  const [validateStatus, setValidateStatus] = useState<{ valid: boolean; message: string } | null>(null);
  const [evaluateResult, setEvaluateResult] = useState<{ ok: boolean; value?: unknown; error?: string } | null>(null);
  const [validating, setValidating] = useState(false);
  const [evaluating, setEvaluating] = useState(false);

  const handleValidate = useCallback(async () => {
    setValidating(true);
    setValidateStatus(null);
    try {
      const result = await reasoningApi.validateCel(expr.trim());
      setValidateStatus({ valid: result.valid, message: result.message });
    } catch (e) {
      setValidateStatus({
        valid: false,
        message: e instanceof Error ? e.message : '校验请求失败',
      });
    } finally {
      setValidating(false);
    }
  }, [expr]);

  const handleEvaluate = useCallback(async () => {
    setEvaluating(true);
    setEvaluateResult(null);
    try {
      let properties: Record<string, unknown> = {};
      let linkedData: Record<string, unknown[]> = {};
      try {
        const parsed = JSON.parse(contextJson) as Record<string, unknown>;
        if (parsed.properties && typeof parsed.properties === 'object' && !Array.isArray(parsed.properties)) {
          properties = parsed.properties as Record<string, unknown>;
        }
        if (parsed.linked_data && typeof parsed.linked_data === 'object' && !Array.isArray(parsed.linked_data)) {
          const ld = parsed.linked_data as Record<string, unknown>;
          for (const [k, v] of Object.entries(ld)) {
            if (Array.isArray(v)) linkedData[k] = v;
          }
        }
      } catch {
        setEvaluateResult({ ok: false, error: '上下文 JSON 格式错误' });
        setEvaluating(false);
        return;
      }
      const value = await reasoningApi.evaluateCel(expr.trim(), properties, linkedData);
      setEvaluateResult({ ok: true, value });
    } catch (e) {
      setEvaluateResult({
        ok: false,
        error: e instanceof Error ? e.message : String(e),
      });
    } finally {
      setEvaluating(false);
    }
  }, [expr, contextJson]);

  return (
    <div className="p-6 max-w-4xl mx-auto space-y-6">
      <div className="flex items-center gap-2 text-gray-900">
        <DocumentTextIcon className="w-8 h-8 text-blue-600" />
        <h1 className="text-2xl font-semibold">CEL 表达式编辑与测试</h1>
      </div>
      <p className="text-sm text-gray-600">
        编辑 CEL 表达式并可选填写样本上下文（properties、linked_data），进行校验与求值测试。支持
        size(links.xxx)、map().sum()、比较运算及已注册函数调用。
      </p>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">CEL 表达式</label>
        <textarea
          value={expr}
          onChange={(e) => setExpr(e.target.value)}
          className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono h-24"
          placeholder="例如: size(links.items) == 1"
          spellCheck={false}
        />
        <div className="mt-2 flex items-center gap-2">
          <button
            type="button"
            onClick={handleValidate}
            disabled={validating || !expr.trim()}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md bg-gray-100 text-gray-800 text-sm hover:bg-gray-200 disabled:opacity-50"
          >
            {validating ? '验证中...' : '验证'}
          </button>
          <button
            type="button"
            onClick={handleEvaluate}
            disabled={evaluating || !expr.trim()}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:opacity-50"
          >
            <PlayIcon className="w-4 h-4" />
            {evaluating ? '求值中...' : '求值'}
          </button>
        </div>
        {validateStatus && (
          <div
            className={`mt-2 flex items-center gap-1.5 text-sm ${
              validateStatus.valid ? 'text-green-700' : 'text-red-700'
            }`}
          >
            {validateStatus.valid ? (
              <CheckCircleIcon className="w-5 h-5 shrink-0" />
            ) : (
              <ExclamationCircleIcon className="w-5 h-5 shrink-0" />
            )}
            <span>{validateStatus.message}</span>
          </div>
        )}
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">样本上下文（可选）JSON</label>
        <p className="text-xs text-gray-500 mb-1">
          properties: 当前实例属性；linked_data: 关系名 → 关联对象列表
        </p>
        <textarea
          value={contextJson}
          onChange={(e) => setContextJson(e.target.value)}
          className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono h-48"
          spellCheck={false}
        />
      </div>

      {evaluateResult && (
        <div
          className={`p-4 rounded-lg ${
            evaluateResult.ok ? 'bg-green-50 border border-green-200' : 'bg-red-50 border border-red-200'
          }`}
        >
          <div className="text-sm font-medium text-gray-700 mb-1">求值结果</div>
          {evaluateResult.ok ? (
            <pre className="text-sm font-mono whitespace-pre-wrap break-all text-green-800">
              {JSON.stringify(evaluateResult.value, null, 2)}
            </pre>
          ) : (
            <p className="text-sm text-red-800">{evaluateResult.error}</p>
          )}
        </div>
      )}
    </div>
  );
}
