import React, { useState, useCallback } from 'react';
import type { SqlParseResult, SqlNodeTree, FieldLineage } from '../../api/sqlParser';

interface SqlParsePageProps {
  initialSql?: string;
}

const SqlParsePage: React.FC<SqlParsePageProps> = ({ initialSql = '' }) => {
  const [sql, setSql] = useState(initialSql);
  const [result, setResult] = useState<SqlParseResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set());
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

  const handleParse = async () => {
    if (!sql.trim()) return;

    setLoading(true);
    try {
      const response = await fetch('/api/v1/sql/parse', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sql })
      });
      const data = await response.json();
      setResult(data);
      if (data.success) {
        const allIds = new Set<string>();
        collectAllNodeIds(data.tree, allIds);
        setExpandedNodes(allIds);
        setSelectedNodeId(data.tree.id);
      }
    } catch (error) {
      console.error('解析失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const collectAllNodeIds = (node: SqlNodeTree, ids: Set<string>) => {
    ids.add(node.id);
    if (node.children) {
      node.children.forEach(child => collectAllNodeIds(child, ids));
    }
  };

  const toggleNode = useCallback((nodeId: string) => {
    setExpandedNodes(prev => {
      const newExpanded = new Set(prev);
      if (newExpanded.has(nodeId)) {
        newExpanded.delete(nodeId);
      } else {
        newExpanded.add(nodeId);
      }
      return newExpanded;
    });
  }, []);

  const expandAll = () => {
    if (result?.tree) {
      const allIds = new Set<string>();
      collectAllNodeIds(result.tree, allIds);
      setExpandedNodes(allIds);
    }
  };

  const collapseAll = () => {
    setExpandedNodes(new Set());
  };

  const findNodeById = useCallback((node: SqlNodeTree, id: string): SqlNodeTree | null => {
    if (node.id === id) return node;
    if (node.children) {
      for (const child of node.children) {
        const found = findNodeById(child, id);
        if (found) return found;
      }
    }
    return null;
  }, []);

  const selectedNode = result?.tree && selectedNodeId
    ? findNodeById(result.tree, selectedNodeId)
    : null;

  return (
    <div className="sql-parse-page" style={{ padding: '20px', maxWidth: '1600px', margin: '0 auto' }}>
      <h1 style={{ marginBottom: '20px', color: '#333' }}>SQL 解析与血缘分析</h1>

      <div style={{ marginBottom: '20px' }}>
        <textarea
          value={sql}
          onChange={(e) => setSql(e.target.value)}
          placeholder="请粘贴SQL查询语句..."
          style={{
            width: '100%',
            minHeight: '150px',
            padding: '12px',
            fontFamily: 'monospace',
            fontSize: '13px',
            border: '1px solid #ddd',
            borderRadius: '6px',
            resize: 'vertical'
          }}
        />
        <div style={{ marginTop: '10px', display: 'flex', gap: '10px' }}>
          <button
            onClick={handleParse}
            disabled={loading || !sql.trim()}
            style={{
              padding: '10px 24px',
              background: loading ? '#ccc' : '#2563eb',
              color: 'white',
              border: 'none',
              borderRadius: '6px',
              cursor: loading ? 'not-allowed' : 'pointer',
              fontWeight: 500
            }}
          >
            {loading ? '解析中...' : '解析SQL'}
          </button>
          <button
            onClick={() => setSql('')}
            style={{
              padding: '10px 24px',
              background: '#f3f4f6',
              color: '#374151',
              border: '1px solid #d1d5db',
              borderRadius: '6px',
              cursor: 'pointer'
            }}
          >
            清空
          </button>
        </div>
      </div>

      {result?.success && result.statistics && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
          gap: '12px',
          marginBottom: '20px'
        }}>
          <StatCard label="层级" value={result.statistics.totalLevels} color="#3b82f6" />
          <StatCard label="表" value={result.statistics.totalTables} color="#10b981" />
          <StatCard label="JOIN" value={result.statistics.totalJoins} color="#f59e0b" />
          <StatCard label="子查询" value={result.statistics.totalSubqueries} color="#ef4444" />
          <StatCard label="字段" value={result.statistics.totalFields} color="#8b5cf6" />
        </div>
      )}

      {result?.success && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
          {/* 左侧：层级结构树 */}
          <div style={{ background: '#f9fafb', borderRadius: '8px', padding: '16px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ margin: 0, fontSize: '14px', color: '#374151' }}>层级结构</h3>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button onClick={expandAll} style={smallButtonStyle}>全部展开</button>
                <button onClick={collapseAll} style={smallButtonStyle}>全部折叠</button>
              </div>
            </div>
            <div style={{ maxHeight: '500px', overflow: 'auto' }}>
              <SqlTreeView
                node={result.tree}
                expandedNodes={expandedNodes}
                onToggle={toggleNode}
                selectedNodeId={selectedNodeId}
                onSelect={setSelectedNodeId}
                level={0}
              />
            </div>
          </div>

          {/* 右侧：血缘图 */}
          <div style={{ background: '#f9fafb', borderRadius: '8px', padding: '16px' }}>
            <h3 style={{ margin: '0 0 16px 0', fontSize: '14px', color: '#374151' }}>血缘图</h3>
            <div style={{ maxHeight: '500px', overflow: 'auto' }}>
              {selectedNode ? (
                <LineageGraph node={selectedNode} lineages={result.lineage || []} />
              ) : (
                <div style={{ color: '#9ca3af', textAlign: 'center', padding: '40px' }}>
                  点击左侧节点查看血缘关系
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {result?.error && (
        <div style={{ color: '#ef4444', padding: '16px', background: '#fef2f2', borderRadius: '8px', marginTop: '20px' }}>
          {result.error}
        </div>
      )}
    </div>
  );
};

const StatCard: React.FC<{ label: string; value: number; color: string }> = ({ label, value, color }) => (
  <div style={{ background: 'white', padding: '12px', borderRadius: '8px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
    <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '2px' }}>{label}</div>
    <div style={{ fontSize: '24px', fontWeight: 600, color }}>{value}</div>
  </div>
);

const smallButtonStyle: React.CSSProperties = {
  padding: '4px 10px',
  fontSize: '12px',
  background: '#e5e7eb',
  border: 'none',
  borderRadius: '4px',
  cursor: 'pointer'
};

// 树形视图组件
const SqlTreeView: React.FC<{
  node: SqlNodeTree;
  expandedNodes: Set<string>;
  onToggle: (nodeId: string) => void;
  selectedNodeId: string | null;
  onSelect: (nodeId: string) => void;
  level: number;
}> = ({ node, expandedNodes, onToggle, selectedNodeId, onSelect, level }) => {
  const isExpanded = expandedNodes.has(node.id);
  const hasChildren = node.children && node.children.length > 0;
  const isSelected = node.id === selectedNodeId;

  return (
    <div>
      <div
        onClick={() => onSelect(node.id)}
        style={{
          display: 'flex',
          alignItems: 'center',
          padding: '6px 8px',
          background: isSelected ? '#dbeafe' : getLevelBgColor(level),
          borderRadius: '4px',
          marginBottom: '4px',
          cursor: 'pointer',
          border: isSelected ? '1px solid #3b82f6' : '1px solid transparent'
        }}
      >
        {hasChildren ? (
          <span
            onClick={(e) => { e.stopPropagation(); onToggle(node.id); }}
            style={{
              width: '16px',
              height: '16px',
              marginRight: '6px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '10px',
              cursor: 'pointer'
            }}
          >
            {isExpanded ? '▼' : '▶'}
          </span>
        ) : (
          <span style={{ width: '22px', display: 'inline-block' }} />
        )}
        <span style={{
          padding: '2px 6px',
          background: getTypeColor(node.type),
          color: 'white',
          borderRadius: '3px',
          fontSize: '11px',
          marginRight: '8px'
        }}>
          {node.type}
        </span>
        <span style={{ fontSize: '13px', color: '#374151' }}>
          {node.tables.length > 0 ? node.tables.map(t => t.alias || t.name).join(', ') : node.alias || ''}
        </span>
        {node.tables.length > 0 && (
          <span style={{ marginLeft: '8px', fontSize: '11px', color: '#9ca3af' }}>
            [{node.tables.length}表]
          </span>
        )}
      </div>
      {isExpanded && hasChildren && (
        <div style={{ marginLeft: '16px' }}>
          {node.children.map(child => (
            <SqlTreeView
              key={child.id}
              node={child}
              expandedNodes={expandedNodes}
              onToggle={onToggle}
              selectedNodeId={selectedNodeId}
              onSelect={onSelect}
              level={level + 1}
            />
          ))}
        </div>
      )}
    </div>
  );
};

const getLevelBgColor = (level: number): string => {
  const colors = ['#f0fdf4', '#ecfdf5', '#f0fdfa', '#f5f3ff', '#fefce8', '#fef2f2'];
  return colors[level % colors.length];
};

const getTypeColor = (type: string): string => {
  const colors: Record<string, string> = {
    'ROOT': '#2563eb',
    'SELECT': '#3b82f6',
    'SUBQUERY': '#8b5cf6',
    'JOIN': '#f59e0b',
    'FILTER': '#10b981',
    'AGGREGATE': '#ef4444'
  };
  return colors[type] || '#6b7280';
};

// 血缘图组件 - 节点图形式
const LineageGraph: React.FC<{ node: SqlNodeTree; lineages: FieldLineage[] }> = ({ node, lineages }) => {
  const tables = node.tables || [];
  const fields = node.fields || [];
  const children = node.children || [];

  // 收集当前节点相关的血缘
  const nodeLineages = lineages.filter(l =>
    fields.some(f => f.name === l.outputField) ||
    l.path.some(p => p.to === fields.map(f => f.name).join(','))
  );

  if (tables.length === 0 && fields.length === 0) {
    return (
      <div style={{ color: '#9ca3af', textAlign: 'center', padding: '20px' }}>
        该节点暂无表和字段信息
      </div>
    );
  }

  return (
    <div style={{ fontSize: '12px' }}>
      {/* 表节点 */}
      {tables.length > 0 && (
        <div style={{ marginBottom: '20px' }}>
          <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '8px' }}>数据源</div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            {tables.map((table, idx) => (
              <div
                key={idx}
                style={{
                  padding: '8px 12px',
                  background: '#dbeafe',
                  border: '1px solid #93c5fd',
                  borderRadius: '6px',
                  color: '#1e40af',
                  fontWeight: 500
                }}
              >
                {table.alias ? (
                  <span>
                    <span style={{ opacity: 0.7 }}>{table.name}</span>
                    <span style={{ marginLeft: '4px' }}>as</span>
                    <span style={{ fontWeight: 600, marginLeft: '4px' }}>{table.alias}</span>
                  </span>
                ) : (
                  table.name
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 字段节点 */}
      {fields.length > 0 && (
        <div style={{ marginBottom: '20px' }}>
          <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '8px' }}>输出字段</div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            {fields.map((field, idx) => (
              <div
                key={idx}
                style={{
                  padding: '4px 8px',
                  background: field.isAggregated ? '#fef3c7' : '#dcfce7',
                  border: `1px solid ${field.isAggregated ? '#fcd34d' : '#86efac'}`,
                  borderRadius: '4px',
                  fontSize: '12px',
                  color: field.isAggregated ? '#92400e' : '#166534'
                }}
              >
                {field.isAggregated && <span style={{ marginRight: '4px' }}>◉</span>}
                {field.name}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* JOIN 关系 */}
      {tables.length >= 2 && (
        <div style={{ marginBottom: '20px' }}>
          <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '8px' }}>关联关系</div>
          <div style={{ padding: '12px', background: 'white', borderRadius: '6px', border: '1px solid #e5e7eb' }}>
            <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
              {tables.map((table, idx) => (
                <React.Fragment key={idx}>
                  {idx > 0 && (
                    <span style={{ color: '#f59e0b', fontWeight: 600 }}>⟷</span>
                  )}
                  <span style={{
                    padding: '4px 8px',
                    background: '#fef3c7',
                    borderRadius: '4px',
                    fontSize: '11px'
                  }}>
                    {table.alias || table.name}
                  </span>
                </React.Fragment>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* 子查询 */}
      {children.length > 0 && (
        <div>
          <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '8px' }}>子查询 ({children.length})</div>
          {children.map((child) => (
            <div
              key={child.id}
              style={{
                padding: '8px 12px',
                background: '#f3e8ff',
                border: '1px solid #c4b5fd',
                borderRadius: '4px',
                marginBottom: '6px',
                fontSize: '12px',
                color: '#6b21a8'
              }}
            >
              <span style={{
                padding: '2px 6px',
                background: '#8b5cf6',
                color: 'white',
                borderRadius: '3px',
                fontSize: '10px',
                marginRight: '8px'
              }}>
                SUB
              </span>
              {child.tables.length > 0 ? child.tables.map(t => t.name).join(', ') : '嵌套查询'}
              {child.fields.length > 0 && (
                <span style={{ marginLeft: '8px', color: '#9ca3af' }}>
                  [{child.fields.length}字段]
                </span>
              )}
            </div>
          ))}
        </div>
      )}

      {/* 字段血缘详情 */}
      {nodeLineages.length > 0 && (
        <div style={{ marginTop: '20px' }}>
          <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '8px' }}>字段血缘</div>
          {nodeLineages.map((lineage, idx) => (
            <div
              key={idx}
              style={{
                padding: '10px',
                background: 'white',
                borderRadius: '6px',
                border: '1px solid #e5e7eb',
                marginBottom: '8px'
              }}
            >
              <div style={{ fontWeight: 500, color: '#3b82f6', marginBottom: '6px' }}>
                {lineage.outputField}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '6px', fontSize: '11px' }}>
                {lineage.path.map((step, stepIdx) => (
                  <React.Fragment key={stepIdx}>
                    {stepIdx > 0 && <span style={{ color: '#9ca3af' }}>→</span>}
                    <span style={{
                      padding: '2px 6px',
                      background: step.operation === 'SELECT' ? '#dbeafe' :
                                  step.operation.includes('SUM') ? '#fef3c7' :
                                  step.operation === 'CASE_WHEN' ? '#fce7f3' : '#e0f2fe',
                      borderRadius: '3px'
                    }}>
                      {step.from}
                    </span>
                  </React.Fragment>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default SqlParsePage;
