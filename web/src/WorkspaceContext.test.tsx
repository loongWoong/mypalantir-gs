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
    expect(captured).not.toBeNull();
    
    const ctx = captured as unknown as ReturnType<typeof useWorkspace>;

    expect(ctx.workspaces).toHaveLength(1);
    expect(ctx.workspaces[0].id).toBe('w1');
    expect(ctx.workspaces[0].name).toBe('WS1');
    expect(ctx.workspaces[0].object_types).toEqual(['Vehicle']);
    expect(ctx.workspaces[0].link_types).toEqual(['owns']);
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

    expect(captured).not.toBeNull();
    const ctx = captured as unknown as ReturnType<typeof useWorkspace>;

    expect(ctx.workspaces[0].object_types).toEqual(expect.arrayContaining(['Vehicle', 'Person']));
    expect(ctx.workspaces[0].object_types).toHaveLength(2);
  });

  it('updateWorkspace 调用 update 并刷新', async () => {
    const items = [{ id: 'w1', name: 'WS1' }];
    mockList.mockResolvedValue({ items, total: 1 });
    mockUpdate.mockResolvedValue({});

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

    expect(captured).not.toBeNull();
    const ctx = captured as unknown as ReturnType<typeof useWorkspace>;

    await act(async () => {
      await ctx.updateWorkspace('w1', { name: 'WS2' });
    });

    expect(mockUpdate).toHaveBeenCalledWith('workspace', 'w1', { name: 'WS2' });
    expect(mockList).toHaveBeenCalledTimes(2); // init + refresh
  });

  it('selectedWorkspace 根据 ID 筛选', async () => {
    mockList.mockResolvedValue({
      items: [
        { id: 'w1', name: 'A' },
        { id: 'w2', name: 'B' },
      ],
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

    expect(captured).not.toBeNull();
    const ctx = captured as unknown as ReturnType<typeof useWorkspace>;

    act(() => {
      ctx.setSelectedWorkspaceId('w2');
    });

    // 重新捕获
    // 注意：TestChild 会在重新渲染时调用 onValue
    // 但这里我们是在同一个渲染周期或者后续更新中
    // 简单起见，我们假设 TestChild 每次 render 都会更新 captured
    // 实际需要 flushPromises 或者 waitFor
    
    // 这里因为 TestChild 直接在 render 中调用 onValue，所以当 context 变化导致 re-render 时，captured 会更新
    // 但 context update 是异步的 state update
    await flushPromises();
    
    // Refresh captured value reference as it might have been updated
    const ctxUpdated = captured as unknown as ReturnType<typeof useWorkspace>;

    expect(ctxUpdated.selectedWorkspaceId).toBe('w2');
    expect(ctxUpdated.selectedWorkspace?.id).toBe('w2');
    expect(ctxUpdated.selectedWorkspace?.name).toBe('B');
  });
});
