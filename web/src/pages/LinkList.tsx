import { useEffect, useState } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import type { Link, LinkType } from '../api/client';
import { linkApi, schemaApi } from '../api/client';
import { ArrowPathIcon } from '@heroicons/react/24/outline';
import { ToastContainer, useToast } from '../components/Toast';

export default function LinkList() {
  const { linkType } = useParams<{ linkType: string }>();
  const [links, setLinks] = useState<Link[]>([]);
  const [linkTypeDef, setLinkTypeDef] = useState<LinkType | null>(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const limit = 20;
  const { toasts, showToast, removeToast } = useToast();

  useEffect(() => {
    if (linkType) {
      loadData();
    }
  }, [linkType, offset]);

  const loadData = async () => {
    if (!linkType) return;
    try {
      setLoading(true);
      const [linkTypeData, linksData] = await Promise.all([
        schemaApi.getLinkType(linkType),
        linkApi.list(linkType, offset, limit),
      ]);
      setLinkTypeDef(linkTypeData);
      setLinks(linksData.items);
      setTotal(linksData.total);
    } catch (error) {
      console.error('Failed to load data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSync = async () => {
    if (!linkType) return;
    try {
      setSyncing(true);
      const result = await linkApi.sync(linkType);
      const message = `同步完成！\n创建关系: ${result.links_created}`;
      showToast(message, 'success');
      loadData(); // 刷新列表
    } catch (error: any) {
      console.error('Failed to sync links:', error);
      const errorMessage = '同步失败: ' + (error.response?.data?.message || error.message);
      showToast(errorMessage, 'error');
    } finally {
      setSyncing(false);
    }
  };

  if (loading) {
    return <div className="text-center py-12">Loading...</div>;
  }

  if (!linkTypeDef) {
    return <div className="text-center py-12">Link type not found</div>;
  }

  return (
    <div>
      <div className="mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{linkTypeDef.display_name || linkTypeDef.name}</h1>
            {linkTypeDef.description && (
              <p className="text-gray-600 mt-1">{linkTypeDef.description}</p>
            )}
            <div className="mt-2 text-sm text-gray-500">
              <span className="font-medium">{linkTypeDef.source_type}</span>
              {' → '}
              <span className="font-medium">{linkTypeDef.target_type}</span>
              {' • '}
              <span>{linkTypeDef.cardinality}</span>
              {' • '}
              <span>{linkTypeDef.direction}</span>
            </div>
            {linkTypeDef.property_mappings && Object.keys(linkTypeDef.property_mappings).length > 0 && (
              <div className="mt-2 text-xs text-gray-400">
                属性匹配:
                {Object.entries(linkTypeDef.property_mappings).map(([sourceProp, targetProp], idx) => (
                  <span key={idx}>
                    {idx > 0 && ' AND '}
                    {linkTypeDef.source_type}.{sourceProp} = {linkTypeDef.target_type}.{targetProp}
                  </span>
                ))}
              </div>
            )}
          </div>
          {linkTypeDef.property_mappings && Object.keys(linkTypeDef.property_mappings).length > 0 && (
            <button
              onClick={handleSync}
              disabled={syncing}
              className="flex items-center px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <ArrowPathIcon className={`w-5 h-5 mr-2 ${syncing ? 'animate-spin' : ''}`} />
              {syncing ? '同步中...' : '同步关系'}
            </button>
          )}
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Source ID
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Target ID
                </th>
                {(linkTypeDef.properties || []).map((prop) => (
                  <th
                    key={prop.name}
                    className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
                  >
                    {prop.name}
                  </th>
                ))}
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Created At
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {links.length === 0 ? (
                <tr>
                  <td
                    colSpan={(linkTypeDef.properties || []).length + 3}
                    className="px-6 py-8 text-center text-gray-500"
                  >
                    No links found.
                  </td>
                </tr>
              ) : (
                links.map((link) => (
                  <tr key={link.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <RouterLink
                        to={`/instances/${linkTypeDef.source_type}/${link.source_id}`}
                        className="text-sm font-mono text-blue-600 hover:text-blue-800 hover:underline"
                      >
                        {link.source_id.substring(0, 8)}...
                      </RouterLink>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <RouterLink
                        to={`/instances/${linkTypeDef.target_type}/${link.target_id}`}
                        className="text-sm font-mono text-blue-600 hover:text-blue-800 hover:underline"
                      >
                        {link.target_id.substring(0, 8)}...
                      </RouterLink>
                    </td>
                    {(linkTypeDef.properties || []).map((prop) => (
                      <td key={prop.name} className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {link[prop.name] !== null && link[prop.name] !== undefined
                          ? String(link[prop.name])
                          : '-'}
                      </td>
                    ))}
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {link.created_at
                        ? new Date(link.created_at).toLocaleString()
                        : '-'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {total > limit && (
          <div className="bg-gray-50 px-6 py-3 flex items-center justify-between border-t border-gray-200">
            <div className="text-sm text-gray-700">
              Showing {offset + 1} to {Math.min(offset + limit, total)} of {total} links
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => setOffset(Math.max(0, offset - limit))}
                disabled={offset === 0}
                className="px-3 py-1 text-sm border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Previous
              </button>
              <button
                onClick={() => setOffset(offset + limit)}
                disabled={offset + limit >= total}
                className="px-3 py-1 text-sm border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Toast 通知 */}
      <ToastContainer toasts={toasts} onClose={removeToast} />
    </div>
  );
}

