import { defineConfig } from '@vben/vite-config';

import { offlineIcons } from './build/offline-icons';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      plugins: [offlineIcons()],
      server: {
        allowedHosts: true,
        proxy: {
          '/admin-api': {
            changeOrigin: true,
            cookieDomainRewrite: '',
            rewrite: (path) => path.replace(/^\/admin-api/, ''),
            // 支持通过 VITE_PROXY_TARGET 指向线上/测试环境便于本地 UI 预览
            target:
              process.env.VITE_PROXY_TARGET || 'http://localhost:48080/admin-api',
            ws: true,
          },
        },
      },
    },
  };
});
