/**
 * WhatsApp 嵌入式注册：加载 Facebook JS SDK，弹出 Meta 官方开通窗口，
 * 客户选择或新建企业账户、输入号码并在手机上扫码（共存模式）或验证号码后，
 * 拿到 code 与 waba_id（以及 phone_number_id）交给后端完成接入。
 */
import type { WaSignupConfig } from '#/api/channel/oauth';

declare global {
  interface Window {
    FB?: any;
    fbAsyncInit?: () => void;
  }
}

let sdkPromise: Promise<void> | undefined;

function loadSdk(cfg: WaSignupConfig): Promise<void> {
  if (window.FB) return Promise.resolve();
  if (!sdkPromise) {
    sdkPromise = new Promise((resolve, reject) => {
      window.fbAsyncInit = () => {
        window.FB.init({ appId: cfg.appId, autoLogAppEvents: true, xfbml: false, version: cfg.apiVersion });
        resolve();
      };
      const s = document.createElement('script');
      s.src = cfg.sdkUrl || 'https://connect.facebook.net/en_US/sdk.js';
      s.async = true;
      s.defer = true;
      s.crossOrigin = 'anonymous';
      s.onerror = () => {
        sdkPromise = undefined;
        reject(new Error('无法加载 Facebook SDK，请检查网络（需要能访问 facebook.com）'));
      };
      document.body.append(s);
    });
  }
  return sdkPromise;
}

export interface WaSignupSession {
  code: string;
  mode: WaSignupMode;
  phoneNumberId?: string;
  wabaId: string;
}

/**
 * coexist：共存模式，客户输入正在用 WhatsApp Business App 的号码，用手机 App 扫描窗口里的二维码确认，
 *          手机照常使用，聊天记录同步过来；new：开通一个新号码（不在手机 App 上使用的号码）
 */
export type WaSignupMode = 'coexist' | 'new';

/** 打开开通窗口；用户取消或没有完成时返回 undefined */
export async function runWaSignup(cfg: WaSignupConfig, mode: WaSignupMode = 'coexist'): Promise<undefined | WaSignupSession> {
  await loadSdk(cfg);
  let info: undefined | { phone_number_id?: string; waba_id?: string };
  let cancelled = false;
  const onMessage = (e: MessageEvent) => {
    if (!/(^|\.)facebook\.com$/.test(new URL(e.origin).hostname)) return;
    try {
      const d = typeof e.data === 'string' ? JSON.parse(e.data) : e.data;
      if (d?.type !== 'WA_EMBEDDED_SIGNUP') return;
      if (String(d.event).startsWith('FINISH')) info = d.data;
      else if (d.event === 'CANCEL') cancelled = true;
    } catch {
      // 其他来自 facebook.com 的消息，忽略
    }
  };
  window.addEventListener('message', onMessage);
  try {
    const code = await new Promise<string | undefined>((resolve) => {
      window.FB.login(
        (resp: any) => resolve(resp?.authResponse?.code),
        {
          config_id: cfg.configId,
          response_type: 'code',
          override_default_response_type: true,
          extras: {
            setup: {},
            featureType: mode === 'coexist' ? 'whatsapp_business_app_onboarding' : '',
            sessionInfoVersion: '3',
          },
        },
      );
    });
    // 结束消息偶尔晚于登录回调到达，稍等片刻
    for (let i = 0; i < 20 && code && !info && !cancelled; i++) {
      await new Promise((r) => setTimeout(r, 150));
    }
    if (!code || !info?.waba_id) return undefined;
    if (mode === 'new' && !info.phone_number_id) return undefined;
    return { code, mode, phoneNumberId: info.phone_number_id, wabaId: info.waba_id };
  } finally {
    window.removeEventListener('message', onMessage);
  }
}
