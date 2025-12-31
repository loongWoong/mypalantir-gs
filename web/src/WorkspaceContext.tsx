import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import type { Instance } from './api/client';
import { instanceApi } from './api/client';

export interface Workspace {
  id: string;
  name?: string;
  display_name?: string;
  description?: string;
  object_types?: string[];
  link_types?: string[];
  raw: Instance;
}

interface WorkspaceContextValue {
  workspaces: Workspace[];
  selectedWorkspaceId: string | null;
  selectedWorkspace: Workspace | null;
  setSelectedWorkspaceId: (id: string | null) => void;
  refreshWorkspaces: () => Promise<void>;
  updateWorkspace: (id: string, patch: Partial<Workspace>) => Promise<void>;
}

const WorkspaceContext = createContext<WorkspaceContextValue | undefined>(undefined);

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [selectedWorkspaceId, setSelectedWorkspaceIdState] = useState<string | null>(null);

  const selectedWorkspace =
    (selectedWorkspaceId && workspaces.find((w) => w.id === selectedWorkspaceId)) || null;

  const loadFromStorage = () => {
    try {
      const stored = localStorage.getItem('selectedWorkspaceId');
      if (stored) {
        setSelectedWorkspaceIdState(stored);
      }
    } catch {
      // ignore
    }
  };

  const saveToStorage = (id: string | null) => {
    try {
      if (id) {
        localStorage.setItem('selectedWorkspaceId', id);
      } else {
        localStorage.removeItem('selectedWorkspaceId');
      }
    } catch {
      // ignore
    }
  };

  const setSelectedWorkspaceId = (id: string | null) => {
    setSelectedWorkspaceIdState(id);
    saveToStorage(id);
  };

  const refreshWorkspaces = async () => {
    try {
      const res = await instanceApi.list('workspace', 0, 100);
      const items = res.items || [];
      const mapped: Workspace[] = items.map((inst) => {
        // 处理 object_types：可能是数组或对象
        let objectTypes: string[] = [];
        if (inst.object_types) {
          if (Array.isArray(inst.object_types)) {
            objectTypes = inst.object_types as string[];
          } else if (typeof inst.object_types === 'object') {
            // 如果是对象，提取键名作为数组
            objectTypes = Object.keys(inst.object_types);
          }
        }

        // 处理 link_types：可能是数组或对象
        let linkTypes: string[] = [];
        if (inst.link_types) {
          if (Array.isArray(inst.link_types)) {
            linkTypes = inst.link_types as string[];
          } else if (typeof inst.link_types === 'object') {
            // 如果是对象，提取键名作为数组
            linkTypes = Object.keys(inst.link_types);
          }
        }

        return {
          id: inst.id,
          name: inst.name as string | undefined,
          display_name: (inst as any).display_name as string | undefined,
          description: inst.description as string | undefined,
          object_types: objectTypes,
          link_types: linkTypes,
          raw: inst,
        };
      });
      setWorkspaces(mapped);

      // 如果当前选择的工作空间不存在了，则重置
      if (selectedWorkspaceId && !mapped.find((w) => w.id === selectedWorkspaceId)) {
        setSelectedWorkspaceId(null);
      }

      // 如果没有选择且存在工作空间，默认选择第一个
      if (!selectedWorkspaceId && mapped.length > 0) {
        setSelectedWorkspaceId(mapped[0].id);
      }
    } catch (e) {
      // 加载失败时，不影响主功能
      console.error('Failed to load workspaces:', e);
    }
  };

  const updateWorkspace = async (id: string, patch: Partial<Workspace>) => {
    const ws = workspaces.find((w) => w.id === id);
    if (!ws) return;
    const data: Record<string, any> = {};
    if (patch.name !== undefined) data.name = patch.name;
    if (patch.display_name !== undefined) (data as any).display_name = patch.display_name;
    if (patch.description !== undefined) data.description = patch.description;
    if (patch.object_types !== undefined) data.object_types = patch.object_types;
    if (patch.link_types !== undefined) data.link_types = patch.link_types;

    try {
      await instanceApi.update('workspace', id, data);
      await refreshWorkspaces();
    } catch (e) {
      console.error('Failed to update workspace:', e);
    }
  };

  useEffect(() => {
    loadFromStorage();
    refreshWorkspaces();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const value: WorkspaceContextValue = {
    workspaces,
    selectedWorkspaceId,
    selectedWorkspace,
    setSelectedWorkspaceId,
    refreshWorkspaces,
    updateWorkspace,
  };

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

export function useWorkspace() {
  const ctx = useContext(WorkspaceContext);
  if (!ctx) {
    throw new Error('useWorkspace must be used within WorkspaceProvider');
  }
  return ctx;
}

