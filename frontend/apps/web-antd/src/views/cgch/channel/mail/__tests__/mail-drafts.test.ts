import { describe, expect, it } from 'vitest';

import { createMailDraftStore } from '../mail-drafts';

/**
 * 这两个语义对应线上两个问题：
 * - 切换邮件后重开，回复不能丢（暂存/恢复）
 * - 在 A 采用 AI 草稿后切到 B 手写发送，不能把 A 的草稿标成已采用（按邮件隔离）
 */
describe('createMailDraftStore', () => {
  const file = (id: number) => ({ id, fileName: `f${id}` });

  it('暂存后能按邮件恢复正文、附件和已采用的草稿 id', () => {
    const store = createMailDraftStore<{ id: number; fileName: string }>();
    store.stash(1, { files: [file(10)], text: '你好', usedDraftId: 99 });

    expect(store.take(1)).toEqual({
      files: [{ id: 10, fileName: 'f10' }],
      text: '你好',
      usedDraftId: 99,
    });
  });

  it('没有正文和附件时不留空记录，避免覆盖正在编辑的内容', () => {
    const store = createMailDraftStore<{ id: number }>();
    store.stash(1, { files: [], text: '   ', usedDraftId: 5 });

    expect(store.has(1)).toBe(false);
    expect(store.take(1)).toEqual({ files: [], text: '', usedDraftId: undefined });
  });

  it('不同邮件的草稿互不影响，AI 草稿 id 不会串到别的邮件', () => {
    const store = createMailDraftStore<{ id: number }>();
    store.stash(1, { files: [], text: 'A 的回复', usedDraftId: 99 });
    store.stash(2, { files: [], text: 'B 的回复' });

    expect(store.take(1).usedDraftId).toBe(99);
    // B 没有采用过草稿，发送 B 时不能去关闭 A 的草稿
    expect(store.take(2).usedDraftId).toBeUndefined();
  });

  it('恢复出来的是副本，改回复框不会改到暂存内容', () => {
    const store = createMailDraftStore<{ id: number }>();
    store.stash(1, { files: [file(10)], text: '原文' });

    const restored = store.take(1);
    restored.text = '被改掉了';
    restored.files.push(file(11));

    expect(store.take(1)).toEqual({
      files: [{ id: 10, fileName: 'f10' }],
      text: '原文',
      usedDraftId: undefined,
    });
  });

  it('发送成功后清掉这封邮件的暂存', () => {
    const store = createMailDraftStore<{ id: number }>();
    store.stash(1, { files: [], text: '待发送' });
    store.clear(1);

    expect(store.has(1)).toBe(false);
  });
});
