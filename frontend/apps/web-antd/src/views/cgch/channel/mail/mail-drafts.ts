/**
 * 邮件回复草稿的暂存。
 *
 * 背景（线上问题）：
 * 1. 回复框原来是全局状态，切走邮件 / 回列表后重开同一封，回复被清空，也没有任何提醒；
 * 2. 「已采用的 AI 草稿」也是全局一个 id，在 A 邮件采用草稿后切到 B 手写发送，
 *    会把 A 的草稿误标成已采用。
 *
 * 所以按邮件 id 分开存，AI 草稿 id 跟着那封邮件一起存取。
 */

export interface MailReplyDraft<TFile = unknown> {
  files: TFile[];
  text: string;
  usedDraftId?: number;
}

export function createMailDraftStore<TFile>() {
  const drafts: Record<number, MailReplyDraft<TFile>> = {};

  /** 没有正文也没有附件时不要留空记录，否则恢复时会覆盖用户正在写的内容 */
  function stash(mailId: number, draft: MailReplyDraft<TFile>) {
    if (!draft.text.trim() && draft.files.length === 0) {
      delete drafts[mailId];
      return;
    }
    drafts[mailId] = {
      files: [...draft.files],
      text: draft.text,
      usedDraftId: draft.usedDraftId,
    };
  }

  /** 返回副本，调用方直接改回复框里的数组不会影响暂存内容 */
  function take(mailId: number): MailReplyDraft<TFile> {
    const d = drafts[mailId];
    if (!d) return { files: [], text: '', usedDraftId: undefined };
    return { files: [...d.files], text: d.text, usedDraftId: d.usedDraftId };
  }

  function has(mailId: number) {
    return !!drafts[mailId];
  }

  function clear(mailId: number) {
    delete drafts[mailId];
  }

  return { clear, drafts, has, stash, take };
}
