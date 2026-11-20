// 当前登录用户信息与登出逻辑，供 Header、个人中心共用
import { defineStore } from "pinia";
import { ElMessage } from "element-plus";
import router from "@/router";
import { getUserByIdApi } from "@/api/UserApi";

export interface UserInfo {
  id: number;
  name: string;
  userName: string;
  phone: string;
  sex: string;
  idNumber: string;
  /** 状态 0：禁用 1：启用 */
  status: number;
  createTime: string;
  updateTime: string;
}

const emptyUserInfo = (): UserInfo => ({
  id: 0,
  name: "",
  userName: "",
  phone: "",
  sex: "",
  idNumber: "",
  status: 1,
  createTime: "",
  updateTime: "",
});

export const useUserStore = defineStore("user", {
  state: () => ({
    userInfo: emptyUserInfo(),
    /** 是否已经成功拉取过用户信息，避免每次切页重复请求 */
    loaded: false,
    loading: false,
  }),
  getters: {
    // 头像占位文字：取用户名首字母
    avatarText: (state) => (state.userInfo.userName || "U").charAt(0).toUpperCase(),
    // 优先展示真实姓名，其次用户名
    displayName: (state) => state.userInfo.name || state.userInfo.userName || "用户",
    isAdmin: () => localStorage.getItem("userRole") === "admin",
  },
  actions: {
    /**
     * 拉取当前用户信息，force 为 true 时忽略缓存重新请求
     */
    async fetchUserInfo(force = false) {
      const userId = localStorage.getItem("userId");
      if (!userId) return null;
      if (this.loaded && !force) return this.userInfo;
      this.loading = true;
      try {
        const res: any = await getUserByIdApi(userId);
        if (res?.code === 0 && res.data) {
          this.userInfo = { ...emptyUserInfo(), ...res.data };
          this.loaded = true;
        }
      } catch (error) {
        console.warn("获取用户信息失败:", error);
      } finally {
        this.loading = false;
      }
      return this.userInfo;
    },
    /**
     * 退出登录：清理本地登录态并回到登录页
     */
    logout() {
      localStorage.removeItem("token");
      localStorage.removeItem("userRole");
      localStorage.removeItem("userId");
      this.userInfo = emptyUserInfo();
      this.loaded = false;
      router.push({ name: "login" });
      ElMessage({ message: "已成功退出登录", type: "success" });
    },
  },
});
