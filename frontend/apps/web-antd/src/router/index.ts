import {
  createRouter,
  createWebHashHistory,
  createWebHistory,
} from 'vue-router';

import { resetStaticRoutes } from '@vben/utils';

import { Modal } from 'ant-design-vue';

import { isChunkLoadError } from '#/utils/chunk-error';
import { isServiceUnavailable, showServiceUnavailable } from '#/utils/service-fallback';

import { createRouterGuard } from './guard';
import { routes } from './routes';
import { setupBaiduTongJi } from './tongji';

/**
 *  @zh_CN 创建vue-router实例
 */
const router = createRouter({
  history:
    import.meta.env.VITE_ROUTER_HISTORY === 'hash'
      ? createWebHashHistory(import.meta.env.VITE_BASE)
      : createWebHistory(import.meta.env.VITE_BASE),
  // 应该添加到路由的初始路由列表。
  routes,
  scrollBehavior: (to, _from, savedPosition) => {
    if (savedPosition) {
      return savedPosition;
    }
    return to.hash ? { behavior: 'smooth', el: to.hash } : { left: 0, top: 0 };
  },
  // 是否应该禁止尾部斜杠。
  // strict: true,
});

const resetRoutes = () => resetStaticRoutes(router, routes);

// 创建路由守卫
createRouterGuard(router);

/**
 * 分包（chunk）加载失败兜底。
 *
 * 线上出现过：进「线索列表」后页面只剩菜单、十几秒没有任何提示，手动刷新才恢复
 * （多半是版本刚更新、旧的 chunk 已经不存在，或者网络抖了一下）。
 * 这里的策略：先自动重载一次，重载后仍失败才弹明确提示 + 重试入口，避免无限刷新。
 */
const CHUNK_RETRY_KEY = 'cgch-chunk-retry';

function showChunkLoadFailed() {
  Modal.error({
    title: '页面加载失败',
    content:
      '页面文件没有加载下来（常见于网络抖动，或系统刚更新过版本）。请刷新重试；如果反复出现，请联系管理员。',
    okText: '刷新重试',
    onOk: () => {
      sessionStorage.removeItem(CHUNK_RETRY_KEY);
      window.location.reload();
    },
  });
}

function handleChunkLoadError() {
  // 第一次：静默重载一次（大多数情况这一下就好了，用户无感）
  if (!sessionStorage.getItem(CHUNK_RETRY_KEY)) {
    sessionStorage.setItem(CHUNK_RETRY_KEY, '1');
    window.location.reload();
    return;
  }
  showChunkLoadFailed();
}

// 导航成功就把标记清掉，下次再遇到才会重新自动重试一次
router.afterEach(() => sessionStorage.removeItem(CHUNK_RETRY_KEY));

router.onError((error) => {
  if (isChunkLoadError(error)) {
    handleChunkLoadError();
    return;
  }
  // 导航期间后端不可用（登录信息 / 菜单拉不到）时不能白屏
  if (isServiceUnavailable(error)) {
    showServiceUnavailable();
    return;
  }
  console.error('[router]', error);
});

// 页面内部异步组件加载失败不会走 router.onError，这里再兜一层
window.addEventListener('unhandledrejection', (event) => {
  if (!isChunkLoadError(event.reason)) return;
  event.preventDefault();
  handleChunkLoadError();
});
// 设置百度统计
setupBaiduTongJi(router);

export { resetRoutes, router };
