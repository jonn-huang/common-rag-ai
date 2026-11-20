<template>
  <div id="basic-layout">
    <el-container class="app-shell">
      <el-aside :style="asideStyle">
        <BasicAside @change-aside="changeAside" />
      </el-aside>

      <el-container class="app-body">
        <!-- 内容区顶部：页面标题、面包屑与用户区 -->
        <el-header class="app-header" :height="HEADER_HEIGHT">
          <div class="header-left">
            <h2 class="header-title">{{ pageTitle }}</h2>
            <el-breadcrumb v-if="breadcrumb.length" separator="/" class="header-breadcrumb">
              <el-breadcrumb-item v-for="(item, index) in breadcrumb" :key="index">
                {{ item }}
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>

          <div class="header-right">
            <el-tooltip content="个人中心" placement="bottom">
              <el-button class="header-icon-button" text circle @click="goProfile">
                <el-icon :size="18"><Avatar /></el-icon>
              </el-button>
            </el-tooltip>

            <el-dropdown trigger="click" @command="handleCommand">
              <div class="header-user">
                <span class="header-avatar">
                  <el-icon :size="16"><Avatar /></el-icon>
                </span>
                <span class="header-name">{{ userStore.displayName }}</span>
                <el-icon class="header-arrow" :size="12"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile" :icon="User">个人中心</el-dropdown-item>
                  <el-dropdown-item command="logout" divided :icon="SwitchButton">
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>

        <el-main class="app-main">
          <RouterView v-slot="{ Component }">
            <transition name="fade-slide" mode="out-in">
              <!-- 部分页面为多根节点，包一层容器保证过渡动画可用 -->
              <div :key="route.path" class="route-view">
                <component :is="Component" />
              </div>
            </transition>
          </RouterView>
        </el-main>

        <el-footer class="app-footer" :height="FOOTER_HEIGHT">
          <span>© {{ year }} Common-RAG-AI · 个人知识库 AI 问答系统</span>
          <span class="footer-tags">
            <em>Spring AI</em>
            <em>Milvus</em>
            <em>Vue 3</em>
          </span>
        </el-footer>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessageBox } from "element-plus";
import { ArrowDown, Avatar, SwitchButton, User } from "@element-plus/icons-vue";
import BasicAside from "@/components/BasicAside.vue";
import { useUserStore } from "@/store/user";

// 折叠宽度与 el-menu 的 --el-menu-collapse-width (64px) 保持一致
const ASIDE_WIDTH = "210px";
const ASIDE_COLLAPSE_WIDTH = "64px";
const HEADER_HEIGHT = "60px";
const FOOTER_HEIGHT = "40px";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const year = new Date().getFullYear();

const asideStyle = ref({
  width: ASIDE_WIDTH,
  transition: "width 0.25s ease",
  overflow: "hidden",
});

const changeAside = (isCollapse: boolean) => {
  asideStyle.value.width = isCollapse ? ASIDE_COLLAPSE_WIDTH : ASIDE_WIDTH;
};

// 页面标题取路由 meta.description，未配置时回退到系统名
const pageTitle = computed(() => route.meta.description || "Common-RAG-AI");

// 面包屑：只保留配置了 description 的层级，单层页面不重复展示
const breadcrumb = computed(() => {
  const labels = route.matched
    .map((record) => record.meta?.description)
    .filter((label): label is string => Boolean(label));
  return labels.length > 1 ? labels : [];
});

const goProfile = () => {
  if (route.name !== "profile") {
    router.push({ name: "profile" });
  }
};

const handleCommand = async (command: string) => {
  if (command === "profile") {
    goProfile();
    return;
  }
  if (command === "logout") {
    try {
      await ElMessageBox.confirm("退出后需要重新登录，确定退出吗？", "退出登录", {
        type: "warning",
        confirmButtonText: "退出",
        cancelButtonText: "取消",
      });
      userStore.logout();
    } catch {
      // 取消退出，无需处理
    }
  }
};

onMounted(() => {
  userStore.fetchUserInfo();
});
</script>

<style scoped lang="less">
#basic-layout {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.app-shell {
  height: 100%;
}

.app-body {
  min-width: 0;
  height: 100%;
  background: #f4f6fb;
}

.app-header {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 22px;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid #e8edf5;
  box-shadow: 0 6px 16px -16px rgba(22, 54, 110, 0.45);
}

.header-left {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 14px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #16325c;
  white-space: nowrap;
}

.header-breadcrumb {
  font-size: 12px;

  :deep(.el-breadcrumb__inner) {
    color: #9aa6b9;
    font-weight: 400;
  }

  :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
    color: #5b6a83;
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.header-icon-button {
  color: #6b7a93;

  &:hover {
    color: #1a478a;
    background: #eef3fb;
  }
}

.header-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.2s ease;

  &:hover {
    background: #eef3fb;
  }
}

.header-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #1a478a, #2d6bc4);
}

.header-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #2c3a52;
}

.header-arrow {
  color: #9aa6b9;
}

.app-main {
  flex: 1;
  min-height: 0;
  padding: 20px;
  overflow-y: auto;
  background: #f4f6fb;
}

.route-view {
  min-height: 100%;
}

.app-footer {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 22px;
  font-size: 12px;
  color: #9aa6b9;
  background: #fff;
  border-top: 1px solid #e8edf5;

  .footer-tags {
    display: flex;
    gap: 6px;

    em {
      padding: 2px 8px;
      border-radius: 999px;
      font-style: normal;
      color: #6b7a93;
      background: #f4f6fb;
      border: 1px solid #e8edf5;
    }
  }
}

/* 路由切换过渡：仅做透明度与轻微位移，避免影响内部滚动容器 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 960px) {
  .header-breadcrumb {
    display: none;
  }
}
</style>
