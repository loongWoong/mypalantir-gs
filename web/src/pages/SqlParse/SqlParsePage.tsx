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
  const [selectedNodeId, setSelectedNodeId] = useState<string>('');

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
        setExpandedNodes(new Set());
        setSelectedNodeId(data.tree?.id || '');
      }
    } catch (error) {
      console.error('解析失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const toggleNode = useCallback((nodeId: string) => {
    setExpandedNodes(prev => {
      const newSet = new Set(prev);
      if (newSet.has(nodeId)) {
        newSet.delete(nodeId);
      } else {
        newSet.add(nodeId);
      }
      return newSet;
    });
  }, []);

  const expandAll = useCallback(() => {
    if (!result?.tree) return;
    const allNodeIds = new Set<string>();
    collectAllNodeIds(result.tree, allNodeIds);
    setExpandedNodes(allNodeIds);
  }, [result]);

  const collapseAll = useCallback(() => {
    setExpandedNodes(new Set());
  }, []);

  const collectAllNodeIds = (node: SqlNodeTree, nodeIds: Set<string>) => {
    nodeIds.add(node.id);
    if (node.children) {
      node.children.forEach(child => collectAllNodeIds(child, nodeIds));
    }
  };

  const flattenSelectLevels = useCallback((node: SqlNodeTree, level: number): SelectLevel[] => {
    const levels: SelectLevel[] = [];

    const levelData: SelectLevel = {
      id: node.id,
      level,
      node,
      type: level === 0 ? 'ROOT' : (node.type === 'SUBQUERY_PARENT' ? node.alias : (node.tables.length === 0 && node.fields.length === 0 ? 'SUBQUERY' : 'SELECT')),
      tables: (node as any).tables || [],
      fields: (node as any).fields || [],
      expressions: (node as any).expressions || [],
      children: (node as any).children || [],
      groupBy: (node as any).groupBy || [],
      whereCondition: (node as any).whereCondition || null,
      orderBy: (node as any).orderBy || []
    };
    levels.push(levelData);

    if (node.children) {
      node.children.forEach(child => {
        levels.push(...flattenSelectLevels(child, level + 1));
      });
    }

    return levels;
  }, []);

  const getSelectLevels = (): SelectLevel[] => {
    if (!result?.tree) return [];
    return flattenSelectLevels(result.tree, 0);
  };

  interface SelectLevel {
    id: string;
    level: number;
    node: SqlNodeTree;
    type: string;
    tables: { name: string; alias?: string; joinType?: string }[];
    fields: { name: string; isAggregated: boolean; expression?: string; table?: string }[];
    expressions: { type: string; function?: string; expression: string }[];
    children: SqlNodeTree[];
    groupBy: string[];
    whereCondition: string | null;
    orderBy: string[];
  }

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
          gridTemplateColumns: 'repeat(auto-fit, minmax(100px, 1fr))',
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
        <div style={{ display: 'grid', gridTemplateColumns: '400px 1fr', gap: '20px' }}>
          <div style={{ background: '#f9fafb', borderRadius: '8px', padding: '16px', height: '600px', overflow: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ margin: 0, fontSize: '14px', color: '#374151' }}>层级结构</h3>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button onClick={expandAll} style={smallButtonStyle}>全部展开</button>
                <button onClick={collapseAll} style={smallButtonStyle}>折叠</button>
              </div>
            </div>
            {getSelectLevels().map((levelData) => (
              <LevelCard
                key={levelData.id}
                levelData={levelData}
                isExpanded={expandedNodes.has(levelData.id)}
                isSelected={selectedNodeId === levelData.id}
                onToggle={() => toggleNode(levelData.id)}
                onSelect={() => setSelectedNodeId(levelData.id)}
              />
            ))}
          </div>

          <div style={{ background: '#f9fafb', borderRadius: '8px', padding: '16px', height: '600px', overflow: 'auto' }}>
            <h3 style={{ margin: '0 0 16px 0', fontSize: '14px', color: '#374151' }}>血缘详情 - {selectedNodeId ? getSelectLevels().find(l => l.id === selectedNodeId)?.level : '未选择'}</h3>
            <LineagePanel level={getSelectLevels().find(l => l.id === selectedNodeId)} lineages={result?.lineage || []} />
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

const LevelCard: React.FC<{
  levelData: SelectLevel;
  isExpanded: boolean;
  isSelected: boolean;
  onToggle: () => void;
  onSelect: () => void;
}> = ({ levelData, isExpanded, isSelected, onToggle, onSelect }) => {
  const hasChildren = levelData.children.length > 0;
  const displayChildren = levelData.children;

  return (
    <div style={{ marginBottom: '8px' }}>
      <div
        onClick={onSelect}
        style={{
          display: 'flex',
          alignItems: 'center',
          padding: '10px 12px',
          background: isSelected ? '#dbeafe' : getLevelBgColor(levelData.level),
          borderRadius: '6px',
          cursor: 'pointer',
          border: isSelected ? '1px solid #3b82f6' : '1px solid #e5e7eb'
        }}
      >
        {hasChildren ? (
          <span onClick={(e) => { e.stopPropagation(); onToggle(); }} style={{ marginRight: '8px', fontSize: '12px', cursor: 'pointer' }}>
            {isExpanded ? '▼' : '▶'}
          </span>
        ) : (
          <span style={{ width: '20px', display: 'inline-block' }} />
        )}
        <span style={{
          padding: '3px 10px',
          background: getTypeColor(levelData.type),
          color: 'white',
          borderRadius: '4px',
          fontSize: '12px',
          marginRight: '12px',
          fontWeight: 500
        }}>
          L{levelData.level}
        </span>
        <span style={{ fontSize: '13px', fontWeight: 500, color: '#374151' }}>
          {levelData.node.type === 'SUBQUERY_PARENT' ? levelData.node.alias : levelData.type}
        </span>
        <span style={{ marginLeft: 'auto', fontSize: '11px', color: '#9ca3af' }}>
          {levelData.tables.length}表 / {levelData.fields.length}字段
        </span>
      </div>

      {isExpanded && hasChildren && (
        <div style={{
          marginTop: '6px',
          marginLeft: '28px',
          padding: '12px',
          background: 'white',
          borderRadius: '6px',
          border: '1px solid #e5e7eb'
        }}>
          {levelData.tables.length > 0 && (
            <div style={{ marginBottom: '10px' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '6px', fontWeight: 500 }}>数据源表</div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                {levelData.tables.map((table, idx) => (
                  <span key={idx} style={{
                    padding: '4px 10px',
                    background: '#eff6ff',
                    border: '1px solid #bfdbfe',
                    borderRadius: '4px',
                    fontSize: '12px',
                    color: '#1e40af'
                  }}>
                    {table.alias ? `${table.name} → ${table.alias}` : table.name}
                  </span>
                ))}
              </div>
            </div>
          )}

          {levelData.fields.length > 0 && (
            <div style={{ marginBottom: '10px' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '6px', fontWeight: 500 }}>输出字段</div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                {levelData.fields.map((field, idx) => (
                  <span key={idx} style={{
                    padding: '3px 8px',
                    background: field.isAggregated ? '#fef3c7' : '#f0fdf4',
                    border: `1px solid ${field.isAggregated ? '#fcd34d' : '#bbf7d0'}`,
                    borderRadius: '4px',
                    fontSize: '11px',
                    color: field.isAggregated ? '#92400e' : '#166534'
                  }}>
                    {field.isAggregated && <span style={{ marginRight: '2px' }}>◉</span>}
                    {field.name}
                  </span>
                ))}
              </div>
            </div>
          )}

          {levelData.groupBy.length > 0 && (
            <div style={{ marginBottom: '8px' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px', fontWeight: 500 }}>GROUP BY</div>
              <div style={{ fontSize: '11px', color: '#374151' }}>
                {levelData.groupBy.slice(0, 3).join(', ')}
                {levelData.groupBy.length > 3 && ` ... (+${levelData.groupBy.length - 3})`}
              </div>
            </div>
          )}

          {levelData.whereCondition && (
            <div style={{ marginBottom: '8px' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px', fontWeight: 500 }}>WHERE</div>
              <div style={{ fontSize: '11px', color: '#374151', wordBreak: 'break-all' }}>
                {levelData.whereCondition.length > 100 ? levelData.whereCondition.substring(0, 100) + '...' : levelData.whereCondition}
              </div>
            </div>
          )}

          <div style={{ marginTop: '12px', paddingTop: '10px', borderTop: '1px solid #e5e7eb' }}>
            <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '8px', fontWeight: 500 }}>
              子查询 ({displayChildren.length})
            </div>
            {displayChildren.map((child, idx) => (
              <div key={idx} style={{
                padding: '8px 10px',
                background: '#f9fafb',
                borderRadius: '4px',
                marginBottom: '6px',
                fontSize: '11px'
              }}>
                <span style={{
                  padding: '2px 6px',
                  background: getTypeColor('SUBQUERY'),
                  color: 'white',
                  borderRadius: '3px',
                  marginRight: '8px'
                }}>
                  L{levelData.level + 1}
                </span>
                <span style={{ color: '#374151' }}>
                  {child.alias || 'SUBQUERY'}
                </span>
                <span style={{ marginLeft: 'auto', color: '#9ca3af' }}>
                  {child.tables?.length || 0}表 / {child.fields?.length || 0}字段
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

const getLevelBgColor = (level: number): string => {
  const colors = ['#f0fdf4', '#ecfdf5', '#f0fdfa', '#f5f3ff', '#fefce8', '#fef2f2', '#fdf4ff', '#fff7ed'];
  return colors[level % colors.length];
};

const getTypeColor = (type: string): string => {
  const colors: Record<string, string> = { 'ROOT': '#2563eb', 'SELECT': '#3b82f6', 'SUBQUERY': '#8b5cf6' };
  return colors[type] || '#6b7280';
};

const LineagePanel: React.FC<{ level?: SelectLevel; lineages: FieldLineage[] }> = ({ level, lineages }) => {
  if (!level) {
    return <div style={{ color: '#9ca3af', textAlign: 'center', padding: '40px' }}>请选择左侧的层级查看详情</div>;
  }

  const levelLineages = lineages.filter(l => level.fields.some(f => f.name === l.outputField));

  return (
    <div style={{ fontSize: '12px' }}>
      <div style={{ padding: '12px', background: getLevelBgColor(level.level), borderRadius: '6px', marginBottom: '16px', border: '1px solid #e5e7eb' }}>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: '8px' }}>
          <span style={{ padding: '2px 8px', background: getTypeColor(level.type), color: 'white', borderRadius: '4px', fontSize: '11px', marginRight: '8px' }}>
            Level {level.level}
          </span>
          <span style={{ fontWeight: 600, color: '#374151' }}>{level.node.type === 'SUBQUERY_PARENT' ? level.node.alias : level.type}</span>
        </div>
        {level.tables.length > 0 && (
          <div style={{ fontSize: '11px', color: '#6b7280' }}>
            数据源: {level.tables.map(t => t.alias || t.name).join(' → ')}
          </div>
        )}
      </div>

      <div>
        <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '10px', fontWeight: 500 }}>
          字段血缘 ({levelLineages.length}/{level.fields.length})
        </div>
        {levelLineages.length === 0 ? (
          <div style={{ color: '#9ca3af', fontSize: '12px', padding: '20px', textAlign: 'center' }}>
            该层级暂无字段血缘数据
          </div>
        ) : (
          levelLineages.map((lineage, idx) => (
            <div key={idx} style={{ padding: '10px', background: 'white', borderRadius: '6px', border: '1px solid #e5e7eb', marginBottom: '8px' }}>
              <div style={{ display: 'flex', alignItems: 'center', marginBottom: '6px' }}>
                <span style={{ padding: '2px 8px', background: '#3b82f6', color: 'white', borderRadius: '10px', fontSize: '11px', fontWeight: 500 }}>
                  {lineage.outputField}
                </span>
                {lineage.function && <span style={{ marginLeft: '8px', fontSize: '11px', color: '#6b7280' }}>[{lineage.function}]</span>}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '4px', fontSize: '11px' }}>
                {lineage.path.map((step, stepIdx) => (
                  <React.Fragment key={stepIdx}>
                    {stepIdx > 0 && <span style={{ color: '#9ca3af' }}>→</span>}
                    <span style={{ padding: '2px 6px', background: step.operation === 'SELECT' ? '#dbeafe' : step.operation.includes('SUM') || step.operation.includes('AVG') ? '#fef3c7' : step.operation === 'CASE_WHEN' ? '#fce7f3' : '#e0f2fe', borderRadius: '3px', fontSize: '10px' }}>
                      {step.from}
                    </span>
                  </React.Fragment>
                ))}
              </div>
            </div>
          ))
        )}
      </div>

      {level.expressions.length > 0 && (
        <div style={{ marginTop: '16px' }}>
          <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '10px', fontWeight: 500 }}>表达式</div>
          {level.expressions.map((expr, idx) => (
            <div key={idx} style={{ padding: '8px 10px', background: 'white', borderRadius: '4px', border: '1px solid #e5e7eb', marginBottom: '6px', fontSize: '11px' }}>
              <span style={{ padding: '2px 6px', background: expr.type === 'AGGREGATE' ? '#fef3c7' : '#fce7f3', borderRadius: '3px', fontSize: '10px', marginRight: '8px' }}>
                {expr.function || expr.type}
              </span>
              <span style={{ color: '#6b7280' }}>{expr.expression.length > 60 ? expr.expression.substring(0, 60) + '...' : expr.expression}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

interface SelectLevel {
  id: string;
  level: number;
  node: SqlNodeTree;
  type: string;
  tables: { name: string; alias?: string; joinType?: string }[];
  fields: { name: string; isAggregated: boolean; expression?: string; table?: string }[];
  expressions: { type: string; function?: string; expression: string }[];
  children: SqlNodeTree[];
  groupBy: string[];
  whereCondition: string | null;
  orderBy: string[];
}

export default SqlParsePage;
