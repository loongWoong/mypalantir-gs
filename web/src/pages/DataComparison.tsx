import { useState, useEffect } from 'react';
import { databaseApi, comparisonApi } from '../api/client';
import type { ComparisonResult } from '../api/client';
import { ToastContainer, useToast } from '../components/Toast';

export default function DataComparison() {
  // State for config
  const [tables, setTables] = useState<any[]>([]);
  const [sourceTableId, setSourceTableId] = useState('');
  const [targetTableId, setTargetTableId] = useState('');
  const [sourceColumns, setSourceColumns] = useState<any[]>([]);
  const [targetColumns, setTargetColumns] = useState<any[]>([]);
  
  const [sourceKey, setSourceKey] = useState('');
  const [targetKey, setTargetKey] = useState('');
  
  const [columnMapping, setColumnMapping] = useState<Record<string, string>>({});
  
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ComparisonResult | null>(null);
  
  const { toasts, showToast, removeToast } = useToast();

  useEffect(() => {
    loadTables();
  }, []);

  const loadTables = async () => {
    try {
      const dbInfo = await databaseApi.getDefaultDatabaseId();
      const tablesData = await databaseApi.getTables(dbInfo.id);
      setTables(tablesData);
    } catch (error) {
      console.error('Failed to load tables:', error);
      showToast('加载表列表失败', 'error');
    }
  };

  const handleSourceTableChange = async (tableId: string) => {
    setSourceTableId(tableId);
    setSourceKey('');
    setColumnMapping({});
    if (tableId) {
       const table = tables.find(t => t.id === tableId);
       if (table) {
           const cols = await databaseApi.getColumns(table.database_id, table.name);
           setSourceColumns(cols);
           // auto select pk
           const pk = cols.find((c: any) => c.is_primary_key);
           if (pk) setSourceKey(pk.name);
       }
    } else {
        setSourceColumns([]);
    }
  };

  const handleTargetTableChange = async (tableId: string) => {
    setTargetTableId(tableId);
    setTargetKey('');
    if (tableId) {
       const table = tables.find(t => t.id === tableId);
       if (table) {
           const cols = await databaseApi.getColumns(table.database_id, table.name);
           setTargetColumns(cols);
           const pk = cols.find((c: any) => c.is_primary_key);
           if (pk) setTargetKey(pk.name);
       }
    } else {
        setTargetColumns([]);
    }
  };
  
  // Auto mapping
  useEffect(() => {
      if (sourceColumns.length > 0 && targetColumns.length > 0) {
          const mapping: Record<string, string> = {};
          sourceColumns.forEach(sc => {
              const tc = targetColumns.find(t => t.name.toLowerCase() === sc.name.toLowerCase());
              if (tc) {
                  mapping[sc.name] = tc.name;
              }
          });
          setColumnMapping(mapping);
      }
  }, [sourceColumns, targetColumns]);

  const handleRun = async () => {
      if (!sourceTableId || !targetTableId || !sourceKey || !targetKey) {
          showToast('请完整填写配置', 'error');
          return;
      }
      
      setLoading(true);
      try {
          const res = await comparisonApi.run({
              sourceTableId,
              targetTableId,
              sourceKey,
              targetKey,
              columnMapping
          });
          setResult(res);
          showToast('对比完成', 'success');
      } catch (error: any) {
          console.error(error);
          showToast('对比失败: ' + error.message, 'error');
      } finally {
          setLoading(false);
      }
  };

  return (
    <div className="max-w-7xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">数据交叉对比 (Data Reconciliation)</h1>
      
      {/* Config Panel */}
      <div className="bg-white rounded-lg shadow p-6 mb-6">
         <div className="grid grid-cols-2 gap-8">
             {/* Source */}
             <div>
                 <h3 className="font-semibold mb-4 text-blue-700">基准表 (Source)</h3>
                 <div className="mb-4">
                     <label className="block text-sm font-medium text-gray-700 mb-1">选择数据表</label>
                     <select 
                        className="w-full border rounded px-3 py-2"
                        value={sourceTableId}
                        onChange={(e) => handleSourceTableChange(e.target.value)}
                     >
                         <option value="">-- 请选择 --</option>
                         {tables.map(t => <option key={t.id} value={t.id}>{t.name} ({t.type})</option>)}
                     </select>
                 </div>
                 <div className="mb-4">
                     <label className="block text-sm font-medium text-gray-700 mb-1">主键 (Key)</label>
                     <select 
                        className="w-full border rounded px-3 py-2"
                        value={sourceKey}
                        onChange={(e) => setSourceKey(e.target.value)}
                     >
                         <option value="">-- 请选择 --</option>
                         {sourceColumns.map(c => <option key={c.name} value={c.name}>{c.name}</option>)}
                     </select>
                 </div>
             </div>
             
             {/* Target */}
             <div>
                 <h3 className="font-semibold mb-4 text-green-700">对比表 (Target)</h3>
                 <div className="mb-4">
                     <label className="block text-sm font-medium text-gray-700 mb-1">选择数据表</label>
                     <select 
                        className="w-full border rounded px-3 py-2"
                        value={targetTableId}
                        onChange={(e) => handleTargetTableChange(e.target.value)}
                     >
                         <option value="">-- 请选择 --</option>
                         {tables.map(t => <option key={t.id} value={t.id}>{t.name} ({t.type})</option>)}
                     </select>
                 </div>
                 <div className="mb-4">
                     <label className="block text-sm font-medium text-gray-700 mb-1">主键 (Key)</label>
                     <select 
                        className="w-full border rounded px-3 py-2"
                        value={targetKey}
                        onChange={(e) => setTargetKey(e.target.value)}
                     >
                         <option value="">-- 请选择 --</option>
                         {targetColumns.map(c => <option key={c.name} value={c.name}>{c.name}</option>)}
                     </select>
                 </div>
             </div>
         </div>
         
         {/* Mapping */}
         {sourceTableId && targetTableId && (
             <div className="mt-6 border-t pt-6">
                 <h3 className="font-semibold mb-4">字段映射配置</h3>
                 <div className="grid grid-cols-3 gap-4 mb-2 font-medium text-sm text-gray-500">
                     <div>源字段 (Source)</div>
                     <div className="text-center">映射关系</div>
                     <div>目标字段 (Target)</div>
                 </div>
                 <div className="space-y-2 max-h-60 overflow-y-auto">
                     {sourceColumns.map(sc => (
                         <div key={sc.name} className="grid grid-cols-3 gap-4 items-center">
                             <div className="text-sm">{sc.name}</div>
                             <div className="flex justify-center">→</div>
                             <select 
                                className="border rounded px-2 py-1 text-sm"
                                value={columnMapping[sc.name] || ''}
                                onChange={(e) => {
                                    const val = e.target.value;
                                    setColumnMapping(prev => {
                                        const next = {...prev};
                                        if (val) next[sc.name] = val;
                                        else delete next[sc.name];
                                        return next;
                                    });
                                }}
                             >
                                 <option value="">(不对比)</option>
                                 {targetColumns.map(tc => (
                                     <option key={tc.name} value={tc.name}>{tc.name}</option>
                                 ))}
                             </select>
                         </div>
                     ))}
                 </div>
             </div>
         )}
         
         <div className="mt-6 flex justify-end">
             <button 
                className="bg-blue-600 text-white px-6 py-2 rounded hover:bg-blue-700 disabled:opacity-50"
                onClick={handleRun}
                disabled={loading}
             >
                 {loading ? '正在对比...' : '开始对比'}
             </button>
         </div>
      </div>
      
      {/* Result Panel */}
      {result && (
          <div className="space-y-6">
              {/* Summary Cards */}
              <div className="grid grid-cols-4 gap-4">
                  <div className="bg-green-50 p-4 rounded border border-green-200">
                      <div className="text-green-800 text-sm font-medium">完全匹配</div>
                      <div className="text-2xl font-bold text-green-900">{result.matchedCount}</div>
                  </div>
                  <div className="bg-red-50 p-4 rounded border border-red-200">
                      <div className="text-red-800 text-sm font-medium">值不一致</div>
                      <div className="text-2xl font-bold text-red-900">{result.mismatchedCount}</div>
                  </div>
                  <div className="bg-yellow-50 p-4 rounded border border-yellow-200">
                      <div className="text-yellow-800 text-sm font-medium">仅源表存在</div>
                      <div className="text-2xl font-bold text-yellow-900">{result.sourceOnlyCount}</div>
                  </div>
                  <div className="bg-blue-50 p-4 rounded border border-blue-200">
                      <div className="text-blue-800 text-sm font-medium">仅目标表存在</div>
                      <div className="text-2xl font-bold text-blue-900">{result.targetOnlyCount}</div>
                  </div>
              </div>
              
              {/* Diff Table */}
              <div className="bg-white rounded-lg shadow overflow-hidden">
                  <div className="px-6 py-4 border-b border-gray-200">
                      <h3 className="font-semibold">差异详情 ({result.diffs.length})</h3>
                  </div>
                  <div className="overflow-x-auto">
                      <table className="min-w-full divide-y divide-gray-200">
                          <thead className="bg-gray-50">
                              <tr>
                                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Key Value</th>
                                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">类型</th>
                                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">详情</th>
                              </tr>
                          </thead>
                          <tbody className="bg-white divide-y divide-gray-200">
                              {result.diffs.slice(0, 100).map((diff, idx) => (
                                  <tr key={idx}>
                                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{diff.keyValue}</td>
                                      <td className="px-6 py-4 whitespace-nowrap text-sm">
                                          <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full 
                                            ${diff.type === 'VALUE_MISMATCH' ? 'bg-red-100 text-red-800' : 
                                              diff.type === 'MISSING_IN_TARGET' ? 'bg-yellow-100 text-yellow-800' : 
                                              'bg-blue-100 text-blue-800'}`}>
                                              {diff.type}
                                          </span>
                                      </td>
                                      <td className="px-6 py-4 text-sm text-gray-500">
                                          {diff.details ? (
                                              <ul className="list-disc pl-4 space-y-1">
                                                  {diff.details.map((d, i) => (
                                                      <li key={i}>
                                                          <span className="font-medium">{d.fieldName}:</span> 
                                                          <span className="text-red-600 line-through mx-2">{String(d.sourceValue)}</span>
                                                          <span>→</span>
                                                          <span className="text-green-600 mx-2">{String(d.targetValue)}</span>
                                                      </li>
                                                  ))}
                                              </ul>
                                          ) : (
                                              <span className="text-gray-400">-</span>
                                          )}
                                      </td>
                                  </tr>
                              ))}
                              {result.diffs.length > 100 && (
                                  <tr>
                                      <td colSpan={3} className="px-6 py-4 text-center text-gray-500">
                                          ... 仅显示前 100 条差异 ...
                                      </td>
                                  </tr>
                              )}
                          </tbody>
                      </table>
                  </div>
              </div>
          </div>
      )}
      
      <ToastContainer toasts={toasts} onClose={removeToast} />
    </div>
  );
}
