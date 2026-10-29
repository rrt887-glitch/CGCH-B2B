import type { BasicUserInfo } from '@vben-core/typings';

import { acceptHMRUpdate, defineStore } from 'pinia';

interface AccessState {
  /**
   * 用户信息
   */
  userInfo: BasicUserInfo | null;
  /**
   * 用户角色
   */
  userRoles: string[];
}

/**
 * @zh_CN 用户信息相关
 */
export const useUserStore = defineStore('core-user', {
  actions: {
    setUserInfo(userInfo: BasicUserInfo | null) {
      this.userInfo = userInfo;
      // 退出登录（清空用户信息）时一并清空角色；用户信息自带角色时同步角色，否则保留单独设置的角色
      if (!userInfo) {
        this.userRoles = [];
        return;
      }
      const roles = (userInfo as { roles?: unknown }).roles;
      if (Array.isArray(roles)) {
        this.userRoles = roles
          .map((r) => (typeof r === 'string' ? r : (r as { value?: string })?.value))
          .filter((r): r is string => !!r);
      }
    },
    setUserRoles(roles: string[]) {
      this.userRoles = roles;
    },
  },
  state: (): AccessState => ({
    userInfo: null,
    userRoles: [],
  }),
});

// 解决热更新问题
const hot = import.meta.hot;
if (hot) {
  hot.accept(acceptHMRUpdate(useUserStore, hot));
}
