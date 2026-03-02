import { useEffect, useState } from 'react';
import type { Rule } from '../api/client';
import { rulesApi } from '../api/client';
import {
  ShieldCheckIcon,
  InformationCircleIcon,
} from '@heroicons/react/24/outline';

export default function RulesView() {
  const [rules, setRules] = useState<Rule[]>([]);
  const [selectedRule, setSelectedRule] = useState<Rule | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadData = async () => {
      try {
        const rulesData = await rulesApi.getRules();
        setRules(rulesData);
        if (rulesData.length > 0) {
          setSelectedRule(rulesData[0]);
        }
      } catch (error) {
        console.error('Failed to load rules:', error);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, []);

  if (loading) {
    return <div className="text-center py-12">Loading...</div>;
  }

  const getScope = (rule: Rule): string => {
    const match = rule.expr?.match(/^(\w+)\(\?/);
    return match ? match[1] : 'Unknown';
  };

  const inferenceChain = [
    {
      label: 'Passage (通行路径)',
      color: 'bg-blue-600',
      rules: rules.filter(r => getScope(r) === 'Passage' && !r.expr?.includes('entry_involves_vehicle') && !r.expr?.includes('entry_at_station')),
    },
    {
      label: 'Vehicle (车辆)',
      color: 'bg-yellow-500',
      rules: rules.filter(r => r.expr?.includes('entry_involves_vehicle')),
    },
    {
      label: 'TollStation (收费站)',
      color: 'bg-red-500',
      rules: rules.filter(r => r.expr?.includes('entry_at_station')),
    },
  ];

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Rules List */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
        <h2 className="text-lg font-semibold text-gray-900 mb-4 flex items-center">
          <ShieldCheckIcon className="w-5 h-5 mr-2" />
          Rules ({rules.length})
        </h2>
        <div className="space-y-2">
          {rules.map((rule) => (
            <button
              key={rule.name}
              onClick={() => setSelectedRule(rule)}
              className={`w-full text-left px-3 py-2 rounded-lg transition-colors ${
                selectedRule?.name === rule.name
                  ? 'bg-blue-50 text-blue-700 border border-blue-200'
                  : 'hover:bg-gray-50 text-gray-700 border border-transparent'
              }`}
            >
              <div className="flex items-center justify-between">
                <div className="font-medium text-sm">{rule.display_name || rule.name}</div>
                <span className="text-xs px-2 py-0.5 bg-indigo-100 text-indigo-700 rounded-full">
                  {rule.language}
                </span>
              </div>
              <div className="text-xs text-gray-500 mt-1">
                Scope: {getScope(rule)}
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Rule Detail + Inference Chain */}
      <div className="lg:col-span-2 space-y-6">
        {/* Selected Rule Detail */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Rule Details</h2>
          {selectedRule ? (
            <div className="space-y-4">
              <div>
                <h3 className="text-xl font-bold text-gray-900">
                  {selectedRule.display_name || selectedRule.name}
                </h3>
                <div className="text-sm text-gray-500 font-mono mt-1">{selectedRule.name}</div>
              </div>
              {selectedRule.description && (
                <p className="text-gray-600">{selectedRule.description}</p>
              )}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-sm font-medium text-gray-500">Language</label>
                  <div className="mt-1">
                    <span className="px-2 py-1 bg-indigo-100 text-indigo-700 rounded text-sm">
                      {selectedRule.language}
                    </span>
                  </div>
                </div>
                <div>
                  <label className="text-sm font-medium text-gray-500">Scope</label>
                  <div className="mt-1 text-sm text-gray-900">{getScope(selectedRule)}</div>
                </div>
              </div>
              <div>
                <label className="text-sm font-medium text-gray-500">SWRL Expression</label>
                <div className="mt-1 p-3 bg-indigo-50 rounded-lg border border-indigo-200">
                  <code className="text-sm text-indigo-900 whitespace-pre-wrap">
                    {selectedRule.expr}
                  </code>
                </div>
              </div>
            </div>
          ) : (
            <div className="text-center py-8 text-gray-500">
              <InformationCircleIcon className="w-12 h-12 mx-auto mb-2 text-gray-400" />
              <p>Select a rule to view details</p>
            </div>
          )}
        </div>

        {/* Inference Chain */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Inference Chain</h2>
          <div className="space-y-4">
            {inferenceChain.map((level, idx) => (
              <div key={level.label}>
                <div className="flex items-center">
                  <div className={`w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-bold ${level.color}`}>
                    {idx + 1}
                  </div>
                  <div className="ml-3 font-semibold text-gray-900">{level.label}</div>
                  <span className="ml-2 text-xs text-gray-500">({level.rules.length} rules)</span>
                </div>
                <div className="ml-11 mt-2 space-y-1">
                  {level.rules.map(r => (
                    <div
                      key={r.name}
                      className={`text-sm cursor-pointer hover:text-blue-600 ${
                        selectedRule?.name === r.name ? 'text-blue-700 font-medium' : 'text-gray-600'
                      }`}
                      onClick={() => setSelectedRule(r)}
                    >
                      {r.display_name || r.name}
                    </div>
                  ))}
                </div>
                {idx < inferenceChain.length - 1 && (
                  <div className="ml-3.5 my-1 border-l-2 border-gray-300 h-4"></div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
