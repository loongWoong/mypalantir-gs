import { useMemo, useState } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import {
  ArrowDownTrayIcon,
  CheckCircleIcon,
  ExclamationTriangleIcon,
  PlusIcon,
  TrashIcon,
  WrenchScrewdriverIcon,
} from '@heroicons/react/24/outline';
import { ontologyBuilderApi } from '../api/client';

interface BuilderProperty {
  name: string;
  data_type: string;
  required: boolean;
  description: string;
  default_value: string;
}

interface BuilderObjectType {
  name: string;
  display_name: string;
  description: string;
  properties: BuilderProperty[];
}

interface BuilderLinkType {
  name: string;
  description: string;
  source_type: string;
  target_type: string;
  cardinality: 'one-to-one' | 'one-to-many' | 'many-to-one' | 'many-to-many';
  direction: 'directed' | 'undirected';
  properties: BuilderProperty[];
}

const defaultProperty = (): BuilderProperty => ({
  name: 'id',
  data_type: 'string',
  required: true,
  description: '主键',
  default_value: '',
});

const defaultObject = (): BuilderObjectType => ({
  name: '',
  display_name: '',
  description: '',
  properties: [defaultProperty()],
});

const defaultLink = (): BuilderLinkType => ({
  name: '',
  description: '',
  source_type: '',
  target_type: '',
  cardinality: 'one-to-many',
  direction: 'directed',
  properties: [],
});

export default function OntologyBuilder() {
  const [version, setVersion] = useState('1.0.0');
  const [namespace, setNamespace] = useState('ontology.builder');
  const [objectTypes, setObjectTypes] = useState<BuilderObjectType[]>([]);
  const [linkTypes, setLinkTypes] = useState<BuilderLinkType[]>([]);
  const [draftObject, setDraftObject] = useState<BuilderObjectType>(defaultObject());
  const [draftLink, setDraftLink] = useState<BuilderLinkType>(defaultLink());
  const [yamlOutput, setYamlOutput] = useState('');
  const [errors, setErrors] = useState<string[]>([]);
  const [valid, setValid] = useState<boolean | null>(null);

  const graphData = useMemo(() => {
    const nodes = objectTypes.map((objectType) => ({
      id: objectType.name,
      label: objectType.display_name || objectType.name,
    }));

    const links = linkTypes
      .filter((link) => link.source_type && link.target_type)
      .map((link, index) => ({
        id: `${link.name}-${index}`,
        source: link.source_type,
        target: link.target_type,
        label: link.name,
      }));

    return { nodes, links };
  }, [objectTypes, linkTypes]);

  const addObjectType = () => {
    if (!draftObject.name.trim()) return;
    setObjectTypes((prev) => [...prev, draftObject]);
    setDraftObject(defaultObject());
  };

  const addLinkType = () => {
    if (!draftLink.name.trim() || !draftLink.source_type || !draftLink.target_type) return;
    setLinkTypes((prev) => [...prev, draftLink]);
    setDraftLink(defaultLink());
  };

  const removeObjectType = (name: string) => {
    setObjectTypes((prev) => prev.filter((item) => item.name !== name));
    setLinkTypes((prev) => prev.filter((link) => link.source_type !== name && link.target_type !== name));
  };

  const removeLinkType = (name: string) => {
    setLinkTypes((prev) => prev.filter((item) => item.name !== name));
  };

  const validateAndGenerateYaml = async () => {
    const payload = {
      version,
      namespace,
      object_types: objectTypes,
      link_types: linkTypes,
      data_sources: [],
    };

    const result = await ontologyBuilderApi.validate(payload);
    setValid(result.valid);
    setErrors(result.errors);
    setYamlOutput(result.yaml);
  };

  const downloadYaml = () => {
    const blob = new Blob([yamlOutput], { type: 'application/yaml;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'ontology-model.yaml';
    link.click();
    window.URL.revokeObjectURL(url);
  };

  return (
    <div className="h-full flex flex-col bg-gray-50">
      <div className="px-6 py-4 bg-white border-b border-gray-200 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <WrenchScrewdriverIcon className="w-6 h-6 text-blue-600" />
          <div>
            <h1 className="text-xl font-semibold text-gray-900">本体构建工具</h1>
            <p className="text-sm text-gray-500">可视化创建节点和关系，自动生成并校验 YML</p>
          </div>
        </div>
        <button
          onClick={validateAndGenerateYaml}
          className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700"
        >
          生成并校验
        </button>
      </div>

      <div className="grid grid-cols-12 gap-4 p-4 flex-1 min-h-0">
        <div className="col-span-4 bg-white border border-gray-200 rounded-lg p-4 overflow-auto">
          <h2 className="font-medium mb-3">模型基础信息</h2>
          <div className="space-y-2 mb-4">
            <input className="w-full border rounded px-3 py-2 text-sm" value={version} onChange={(e) => setVersion(e.target.value)} placeholder="version" />
            <input className="w-full border rounded px-3 py-2 text-sm" value={namespace} onChange={(e) => setNamespace(e.target.value)} placeholder="namespace" />
          </div>

          <h3 className="font-medium mb-2">新增实体节点</h3>
          <div className="space-y-2">
            <input className="w-full border rounded px-3 py-2 text-sm" placeholder="name" value={draftObject.name} onChange={(e) => setDraftObject({ ...draftObject, name: e.target.value })} />
            <input className="w-full border rounded px-3 py-2 text-sm" placeholder="display_name" value={draftObject.display_name} onChange={(e) => setDraftObject({ ...draftObject, display_name: e.target.value })} />
            <input className="w-full border rounded px-3 py-2 text-sm" placeholder="description" value={draftObject.description} onChange={(e) => setDraftObject({ ...draftObject, description: e.target.value })} />
            <button onClick={addObjectType} className="w-full border border-blue-600 text-blue-600 rounded py-2 text-sm hover:bg-blue-50">
              <PlusIcon className="w-4 h-4 inline mr-1" />添加实体
            </button>
          </div>

          <h3 className="font-medium mt-5 mb-2">新增关系边</h3>
          <div className="space-y-2">
            <input className="w-full border rounded px-3 py-2 text-sm" placeholder="name" value={draftLink.name} onChange={(e) => setDraftLink({ ...draftLink, name: e.target.value })} />
            <input className="w-full border rounded px-3 py-2 text-sm" placeholder="description" value={draftLink.description} onChange={(e) => setDraftLink({ ...draftLink, description: e.target.value })} />
            <select className="w-full border rounded px-3 py-2 text-sm" value={draftLink.source_type} onChange={(e) => setDraftLink({ ...draftLink, source_type: e.target.value })}>
              <option value="">source_type</option>
              {objectTypes.map((obj) => (
                <option key={obj.name} value={obj.name}>{obj.name}</option>
              ))}
            </select>
            <select className="w-full border rounded px-3 py-2 text-sm" value={draftLink.target_type} onChange={(e) => setDraftLink({ ...draftLink, target_type: e.target.value })}>
              <option value="">target_type</option>
              {objectTypes.map((obj) => (
                <option key={obj.name} value={obj.name}>{obj.name}</option>
              ))}
            </select>
            <button onClick={addLinkType} className="w-full border border-indigo-600 text-indigo-600 rounded py-2 text-sm hover:bg-indigo-50">
              <PlusIcon className="w-4 h-4 inline mr-1" />添加关系
            </button>
          </div>

          <h3 className="font-medium mt-5">已建模节点</h3>
          <ul className="mt-2 space-y-2">
            {objectTypes.map((obj) => (
              <li key={obj.name} className="border rounded px-2 py-1 text-sm flex items-center justify-between">
                <span>{obj.name}</span>
                <button onClick={() => removeObjectType(obj.name)} className="text-red-500"><TrashIcon className="w-4 h-4" /></button>
              </li>
            ))}
          </ul>

          <h3 className="font-medium mt-5">已建模关系</h3>
          <ul className="mt-2 space-y-2">
            {linkTypes.map((link) => (
              <li key={link.name} className="border rounded px-2 py-1 text-sm flex items-center justify-between">
                <span>{link.name}: {link.source_type} → {link.target_type}</span>
                <button onClick={() => removeLinkType(link.name)} className="text-red-500"><TrashIcon className="w-4 h-4" /></button>
              </li>
            ))}
          </ul>
        </div>

        <div className="col-span-5 bg-white border border-gray-200 rounded-lg p-2 min-h-0">
          <div className="h-full">
            <ForceGraph2D
              graphData={graphData}
              nodeLabel={(node: any) => node.label}
              linkLabel={(link: any) => link.label}
              nodeCanvasObject={(node: any, ctx, globalScale) => {
                const label = node.label || node.id;
                const fontSize = 12 / globalScale;
                ctx.font = `${fontSize}px Sans-Serif`;
                ctx.fillStyle = '#2563eb';
                ctx.beginPath();
                ctx.arc(node.x, node.y, 8, 0, 2 * Math.PI, false);
                ctx.fill();
                ctx.fillStyle = '#111827';
                ctx.fillText(label, node.x + 10, node.y + 4);
              }}
              linkDirectionalArrowLength={6}
              linkDirectionalArrowRelPos={1}
              linkColor={() => '#94a3b8'}
            />
          </div>
        </div>

        <div className="col-span-3 bg-white border border-gray-200 rounded-lg p-4 overflow-auto">
          <h2 className="font-medium">YML 生成结果</h2>
          {valid === true && (
            <p className="mt-2 text-sm text-green-600 flex items-center gap-1">
              <CheckCircleIcon className="w-4 h-4" /> 校验通过
            </p>
          )}
          {valid === false && (
            <p className="mt-2 text-sm text-red-600 flex items-center gap-1">
              <ExclamationTriangleIcon className="w-4 h-4" /> 校验未通过
            </p>
          )}

          {errors.length > 0 && (
            <ul className="mt-2 text-xs text-red-600 list-disc pl-4 space-y-1">
              {errors.map((error) => (
                <li key={error}>{error}</li>
              ))}
            </ul>
          )}

          <textarea
            className="mt-3 w-full h-80 border rounded p-2 text-xs font-mono"
            value={yamlOutput}
            onChange={(e) => setYamlOutput(e.target.value)}
            placeholder="校验后自动生成 YML"
          />

          <button
            disabled={!yamlOutput}
            onClick={downloadYaml}
            className="mt-2 w-full py-2 rounded bg-gray-800 text-white text-sm disabled:opacity-50"
          >
            <ArrowDownTrayIcon className="w-4 h-4 inline mr-1" />下载 YML
          </button>
        </div>
      </div>
    </div>
  );
}
