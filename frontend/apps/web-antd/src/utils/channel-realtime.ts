import { useAccessStore } from '@vben/stores';

/**
 * 渠道消息实时推送：连接后端 /infra/ws（框架自带的 WebSocket），收到新消息时通知页面刷新。
 * - 30 秒心跳（ping / pong），连续 3 次没有回应视为断线
 * - 断线后 3 秒重连，之后逐步放慢到 30 秒一次
 * - 断线期间页面继续用定时刷新兜底，连上后定时刷新放慢
 */
export interface ChannelRealtimeEvent {
  /** whatsapp：即时消息会话（含 Facebook / Instagram / 官网客服）；mail：邮件 */
  channel: 'mail' | 'whatsapp';
  /** 会话编号（即时消息）或邮件编号 */
  refId: number;
  messageId?: number;
  /** in 客户发来、out 我方发出（含 AI）、status 发送状态变化 */
  kind: 'in' | 'out' | 'status';
}

type Listener = (e: ChannelRealtimeEvent) => void;
type StateListener = (connected: boolean) => void;

const MESSAGE_TYPE = 'channel-message';
const listeners = new Set<Listener>();
const stateListeners = new Set<StateListener>();
let ws: undefined | WebSocket;
let connected = false;
let heartbeat: ReturnType<typeof setInterval> | undefined;
let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
let missedPongs = 0;
let attempts = 0;

function setConnected(v: boolean) {
  if (connected === v) return;
  connected = v;
  stateListeners.forEach((l) => l(v));
}

function wsUrl(token: string) {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${location.host}/infra/ws?token=${encodeURIComponent(token)}`;
}

function cleanup() {
  if (heartbeat) clearInterval(heartbeat);
  heartbeat = undefined;
  setConnected(false);
}

function scheduleReconnect() {
  if (reconnectTimer || listeners.size === 0) return;
  const delay = Math.min(30_000, 3000 * 2 ** Math.min(attempts, 4));
  attempts++;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = undefined;
    connect();
  }, delay);
}

function connect() {
  const token = useAccessStore().accessToken;
  if (!token || typeof WebSocket === 'undefined') return;
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return;
  try {
    ws = new WebSocket(wsUrl(token));
  } catch {
    scheduleReconnect();
    return;
  }
  ws.addEventListener('open', () => {
    attempts = 0;
    missedPongs = 0;
    setConnected(true);
    heartbeat = setInterval(() => {
      if (missedPongs >= 3) {
        ws?.close();
        return;
      }
      missedPongs++;
      ws?.send('ping');
    }, 30_000);
  });
  ws.addEventListener('message', (ev) => {
    const text = String(ev.data);
    if (text === 'pong') {
      missedPongs = 0;
      return;
    }
    try {
      const msg = JSON.parse(text) as { content?: string; type?: string };
      if (msg.type !== MESSAGE_TYPE || !msg.content) return;
      const e = JSON.parse(msg.content) as ChannelRealtimeEvent;
      listeners.forEach((l) => l(e));
    } catch {
      // 不是我们的消息，忽略
    }
  });
  ws.addEventListener('close', () => {
    cleanup();
    ws = undefined;
    scheduleReconnect();
  });
  ws.addEventListener('error', () => ws?.close());
}

/** 订阅新消息推送；返回取消订阅的函数。最后一个订阅取消时断开连接 */
export function onChannelRealtime(listener: Listener, onState?: StateListener): () => void {
  listeners.add(listener);
  if (onState) {
    stateListeners.add(onState);
    onState(connected);
  }
  connect();
  return () => {
    listeners.delete(listener);
    if (onState) stateListeners.delete(onState);
    if (listeners.size === 0) {
      if (reconnectTimer) clearTimeout(reconnectTimer);
      reconnectTimer = undefined;
      ws?.close();
      ws = undefined;
      cleanup();
    }
  };
}

export function isChannelRealtimeConnected() {
  return connected;
}
