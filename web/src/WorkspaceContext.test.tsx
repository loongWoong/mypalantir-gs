import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createRoot } from 'react-dom/client';
import { act } from 'react';
import { WorkspaceProvider, useWorkspace } from './WorkspaceContext';

const mockList = vi.fn();
const mockUpdate = vi.fn();

vi.mock('./api/client', () => ({
  instanceApi: {
    list: (...args: unknown[]) => mockList(...args),
    update: (...args: unknown[]) => mockUpdate(...args),
  },
}));

function TestChild({ onValue }: { onValue: (v: ReturnType<typeof useWorkspace>) => void }) {
  const value = useWorkspace();
  if (onValue) onValue(value);
  return (
    <span data-testid="workspace-count">{value.workspaces.length}</span>
  );
}

function flushPromises() {
  return act(() => new Promise((r) => setTimeout(r, 0)));
}

describe('WorkspaceContext', () => {
  let container: HTMLDivElement;
  let root: ReturnType<typeof createRoot>;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    mockList.mockReset();
    mockUpdate.mockReset();
    localStorage.clear();
  });

  afterEach(() => {
    if (root != null) {
      act(() => {
        root.unmount();
      });
    }
    container.remove();
  });

  it('refreshWorkspaces 将 list 结果映射为 Workspace 数组', async () => {
    const items = [
      {
        id: 'w1',
        name: 'WS1',
        object_types: ['Vehicle'],
        link_types: ['owns'],
      },
    ];
    mockList.mockResolvedValueOnce({ items, total: 1 });

    let captured: ReturnType<typeof useWorkspace> | null = null;
    root = createRoot(container);
    act(() => {
      root.render(
        <WorkspaceProvider>
          <TestChild onValue={(v) => { captured = v; }} />
        </WorkspaceProvider>
      );
    });

    await flushPromises();
    await flushPromises();

    expect(mockList).toHaveBeenCalledWith('workspace', 0, 100);
    expect(captured?.workspaces).toHaveLength(1);
    expect(captured?.workspaces[0].id).toBe('w1');
    expect(captured?.workspaces[0].name).toBe('WS1');
    expect(captured?.workspaces[0].object_types).toEqual(['Vehicle']);
    expect(captured?.workspaces[0].link_types).toEqual(['owns']);
  });

  it('object_types 为对象时提取键名', async () => {
    mockList.mockResolvedValueOnce({
      items: [
        {
          id: 'w1',
          object_types: { Vehicle: true, Person: true },
          link_types: [],
        },
      ],
      total: 1,
    });

    let captured: ReturnType<typeof useWorkspace> | null = null;
    root = createRoot(container);
    act(() => {
      root.render(
        <WorkspaceProvider>
          <TestChild onValue={(v) => { captured = v; }} />
        </WorkspaceProvider>
      );
    });

    await flushPromises();
    await flushPromises();

    expect(captured?.workspaces[0].object_types).toEqual(expect.arrayContaining(['Vehicle', 'Person']));
    expect(captured?.workspaces[0].object_types).toHaveLength(2);
  });

  it('setSelectedWorkspaceId 写入 localStorage', async () => {
    mockList.mockResolvedValueOnce({ items: [{ id: 'w1', name: 'WS1' }], total: 1 });

    let captured: ReturnType<typeof useWorkspace> | null = null;
    root = createRoot(container);
    act(() => {
      root.render(
        <WorkspaceProvider>
          <TestChild onValue={(v) => { captured = v; }} />
        </WorkspaceProvider>
      );
    });

    await flushPromises();
    await flushPromises();

    act(() => {
      captured?.setSelectedWorkspaceId('w1');
    });

    expect(localStorage.getItem('selectedWorkspaceId')).toBe('w1');

    act(() => {
      captured?.setSelectedWorkspaceId(null);
    });

    expect(localStorage.getItem('selectedWorkspaceId')).toBeNull();
  });

  it('selectedWorkspace 与 selectedWorkspaceId 对应', async () => {
    mockList.mockResolvedValueOnce({
      items: [{ id: 'w1', name: 'A' }, { id: 'w2', name: 'B' }],
      total: 2,
    });

    let captured: ReturnType<typeof useWorkspace> | null = null;
    root = createRoot(container);
    act(() => {
      root.render(
        <WorkspaceProvider>
          <TestChild onValue={(v) => { captured = v; }} />
        </WorkspaceProvider>
      );
    });

    await flushPromises();
    await flushPromises();

    act(() => {
      captured?.setSelectedWorkspaceId('w2');
    });

    expect(captured?.selectedWorkspaceId).toBe('w2');
    expect(captured?.selectedWorkspace?.id).toBe('w2');
    expect(captured?.selectedWorkspace?.name).toBe('B');
  });
});
