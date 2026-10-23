import { requestClient } from '#/api/request';

/** 批量翻译：默认译成中文；传 sample 时译成与 sample 相同的语言（输入框双语模式） */
export const translateTexts = (texts: string[], opts: { sample?: string; target?: string } = {}) =>
  requestClient.post<string[]>('/channel/translate', { texts, ...opts });

/** 翻译设置：个人偏好，保存在本机浏览器 */
export interface TranslatePrefs {
  chat: boolean;
  input: boolean;
}

const KEY = 'cgch-translate-prefs';

export function loadTranslatePrefs(): TranslatePrefs {
  try {
    const v = localStorage.getItem(KEY);
    if (v) return { chat: false, input: false, ...JSON.parse(v) };
  } catch {
    // 浏览器禁止存储时使用默认值
  }
  return { chat: false, input: false };
}

export function saveTranslatePrefs(p: TranslatePrefs) {
  try {
    localStorage.setItem(KEY, JSON.stringify(p));
  } catch {
    // 忽略
  }
}
