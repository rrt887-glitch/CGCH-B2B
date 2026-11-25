/**
 * 唤醒任务的状态展示规则。
 *
 * 单独抽出来是因为这里出过两次错（「待确认 0」、把已确认的算进待确认），
 * 抽成纯函数后可以直接用单测锁住。
 *
 * 背景：后端 task.status=ready 只表示「AI 写完了」。之后用户逐条确认、跳过都不会
 * 再改任务状态，所以列表上的标签必须按各状态的条数推导，才能和任务详情里的数字一致。
 */

export type TaskStatusCode = 'done' | 'generating' | 'ready' | 'sending';

export interface TaskCounts {
  approved?: number;
  draft?: number;
  failed?: number;
  generating?: number;
  sent?: number;
  skipped?: number;
}

export interface TaskLike {
  counts?: TaskCounts;
  status: string;
}

export const TASK_STATUS: Record<string, { color: string; label: string }> = {
  generating: { color: 'processing', label: 'AI 撰写中' },
  ready: { color: 'orange', label: '待确认' },
  sending: { color: 'processing', label: '发送中' },
  done: { color: 'green', label: '已完成' },
};

export const ITEM_STATUS: Record<string, { color: string; label: string }> = {
  generating: { color: 'processing', label: '撰写中' },
  draft: { color: 'orange', label: '待确认' },
  approved: { color: 'blue', label: '已确认' },
  sent: { color: 'green', label: '已发送' },
  failed: { color: 'red', label: '失败' },
  skipped: { color: 'default', label: '已跳过' },
};

/** 待确认 = 只有草稿。已确认待发送是另一件事，混进来会让列表和详情对不上。 */
export const draftCount = (t: TaskLike) => t.counts?.draft ?? 0;
/** 已确认、等待发送 */
export const approvedCount = (t: TaskLike) => t.counts?.approved ?? 0;

/**
 * 列表卡片的标签：
 * - 有草稿 → 待确认
 * - 草稿都确认完、还没发 → 待发送
 * - 一条都发不出去 → 说清原因（生成失败 / 无可发送内容 / 没有候选人），不要显示「待确认」
 */
export function taskTag(t: TaskLike): { color: string; label: string } {
  if (t.status !== 'ready') {
    return TASK_STATUS[t.status] ?? { color: 'default', label: t.status };
  }
  if (draftCount(t) > 0) return { color: 'orange', label: '待确认' };
  if (approvedCount(t) > 0) return { color: 'blue', label: '待发送' };
  if ((t.counts?.failed ?? 0) > 0) return { color: 'red', label: '生成失败' };
  if ((t.counts?.skipped ?? 0) > 0) return { color: 'default', label: '无可发送内容' };
  return { color: 'default', label: '没有候选人' };
}
