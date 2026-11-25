import { describe, expect, it } from 'vitest';

import { approvedCount, draftCount, taskTag } from '../task-status';

/**
 * 这里出过两次线上问题（「待确认 0」、已确认的草稿被算进待确认），所以用单测锁住：
 * 列表标签和卡片数字必须能由 counts 唯一推导出来。
 */
describe('taskTag', () => {
  const counts = (c: Record<string, number>) => ({
    approved: 0,
    draft: 0,
    failed: 0,
    generating: 0,
    sent: 0,
    skipped: 0,
    ...c,
  });

  it('有草稿时显示待确认', () => {
    expect(taskTag({ status: 'ready', counts: counts({ draft: 3, approved: 2 }) })).toEqual({
      color: 'orange',
      label: '待确认',
    });
  });

  it('草稿都确认完、还没发送时显示待发送，而不是待确认', () => {
    expect(taskTag({ status: 'ready', counts: counts({ draft: 0, approved: 2 }) })).toEqual({
      color: 'blue',
      label: '待发送',
    });
  });

  it('全部被跳过时说明无可发送内容', () => {
    expect(taskTag({ status: 'ready', counts: counts({ skipped: 5 }) })).toEqual({
      color: 'default',
      label: '无可发送内容',
    });
  });

  it('生成失败时优先报失败', () => {
    expect(taskTag({ status: 'ready', counts: counts({ failed: 2, skipped: 1 }) })).toEqual({
      color: 'red',
      label: '生成失败',
    });
  });

  it('生成中 / 发送中 / 已完成沿用后端状态', () => {
    expect(taskTag({ status: 'generating', counts: counts({}) }).label).toBe('AI 撰写中');
    expect(taskTag({ status: 'sending', counts: counts({}) }).label).toBe('发送中');
    expect(taskTag({ status: 'done', counts: counts({}) }).label).toBe('已完成');
  });

  it('待确认只算草稿，已确认单独统计', () => {
    const t = { status: 'ready', counts: counts({ draft: 4, approved: 6 }) };
    expect(draftCount(t)).toBe(4);
    expect(approvedCount(t)).toBe(6);
  });

  it('counts 缺失时不抛错', () => {
    expect(taskTag({ status: 'ready' })).toEqual({ color: 'default', label: '没有候选人' });
    expect(draftCount({ status: 'ready' })).toBe(0);
  });
});
