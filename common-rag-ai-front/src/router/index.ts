import { createRouter, createWebHistory } from "vue-router";
import routes from "./config";
import {ElMessage} from "element-plus";

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean;
    roles?: string[];
    /** blank：登录页等独立页面，不渲染主框架（侧边栏/Header/Footer） */
    layout?: 'blank' | 'default';
    /** 菜单与 Header 展示用的中文名称 */
    description?: string;
    /** 菜单图标名，取 Element Plus 图标组件名 */
    icon?: string;
    /** 是否渲染到侧边栏菜单 */
    isMenu?: boolean;
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 全局前置守卫
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token'); // 获取 token
  const userRole = localStorage.getItem('userRole'); // 获取用户角色

  // 已登录用户无需再进入登录页，直接回到 AI 问答首页
  if (to.name === 'login' && token) {
    next({ name: 'ragChat' });
    return;
  }

  if (to.meta.requiresAuth) {
    if (!token) {
      // 如果目标路由需要认证且没有 token，则显示提示信息并重定向到登录页
      ElMessage({ message: '请先登录', type: 'warning' });
      next({ name: 'login' });
    } else if (to.meta.roles && Array.isArray(to.meta.roles) && !to.meta.roles.includes(userRole ?? '')) {
      // 如果目标路由需要特定角色且用户角色不符合要求，则显示提示信息并重定向到首页
      ElMessage({ message: '您没有权限访问该页面', type: 'error' });
      next({ name: 'index' });
    } else {
      // 否则继续导航
      next();
    }
  } else {
    // 如果目标路由不需要认证，则继续导航
    next();
  }
});

export default router;
