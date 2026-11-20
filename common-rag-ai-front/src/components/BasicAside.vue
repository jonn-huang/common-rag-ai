<template>
  <div id="basic-aside" :class="{ 'is-collapse': isCollapse }">
    <!-- 固定头部：品牌信息与折叠按钮，不随菜单滚动 -->
    <div class="aside-head">
      <div v-if="!isCollapse" class="aside-brand">
        <h1>Common-RAG-AI</h1>
        <el-text class="aside-slogan" size="small">知识库AI问答系统</el-text>
      </div>
      <el-icon class="collapse-toggle" :size="18" @click="toggleCollapse">
        <Expand v-if="isCollapse" />
        <Fold v-else />
      </el-icon>
    </div>
    <el-divider v-if="!isCollapse" class="aside-divider" />

    <!-- 菜单滚动区：菜单过多时只在区域内滚动，头部保持固定 -->
    <div class="aside-menu-scroll">
      <el-menu
        ref="menuRef"
        :default-active="defaultPath"
        :default-openeds="defaultOpeneds"
        class="aside-menu"
        :collapse="isCollapse"
        background-color="#1A478A"
        text-color="#FFFFFF"
        active-text-color="#91CC75"
      >
        <template v-for="item in menuRouterList" :key="item.path">
          <!-- 有子菜单：渲染为可折叠目录 -->
          <el-sub-menu
            v-if="childMenus(item).length > 0"
            :index="menuIndex(item)"
          >
            <template #title>
              <el-icon>
                <component :is="item.meta?.icon"></component>
              </el-icon>
              <span>{{ item.meta?.description }}</span>
            </template>
            <el-menu-item
              v-for="child in childMenus(item)"
              :key="child.path"
              :index="child.path"
              @click="handleSelect(child)"
            >
              <el-icon>
                <component :is="child.meta?.icon"></component>
              </el-icon>
              <template #title>{{ child.meta?.description }}</template>
            </el-menu-item>
          </el-sub-menu>

          <!-- 无子菜单：普通菜单项 -->
          <el-menu-item
            v-else
            :index="item.path"
            @click="handleSelect(item)"
          >
            <el-icon>
              <component :is="item.meta?.icon"></component>
            </el-icon>
            <template #title>{{ item.meta?.description }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </div>
  </div>
</template>

<script setup lang="ts">
import routes from "@/router/config.ts";
import router from "@/router";

const emit = defineEmits(["changeAside"]);
const isCollapse = ref(false);
const menuRef = ref();
const defaultPath = ref(
  router.currentRoute.value.fullPath === "/" ? "/profile" : router.currentRoute.value.fullPath
);

// 使用计算属性过滤不是菜单项的路由选项
const menuRouterList = computed(() => {
  return routes.filter((item) => {
    return item.meta?.isMenu;
  });
});

// 取出可见的子菜单（父级只做目录，不作为可点击页面）
const childMenus = (item: any) =>
  (item.children ?? []).filter((child: any) => child.meta?.isMenu);

// 目录节点统一用 name 作为 index，避免与子项 path 混淆
const menuIndex = (item: any) => String(item.name ?? item.path);

// 找出当前路由所属目录的 index
const resolveParentIndex = (path: string) =>
  menuRouterList.value
    .filter((item) =>
      childMenus(item).some((child: any) => child.path === path)
    )
    .map((item) => menuIndex(item));

// 默认展开当前路由所在目录，保证高亮项可见
const defaultOpeneds = ref<string[]>(resolveParentIndex(defaultPath.value));

const openCurrentParent = () => {
  const [index] = resolveParentIndex(defaultPath.value);
  if (!index) return;
  // el-menu 仅在初始化时读取 default-openeds，路由切换后需主动展开
  nextTick(() => menuRef.value?.open(index));
};

router.afterEach((to) => {
  defaultPath.value = to.path;
  openCurrentParent();
});

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value;
  emit("changeAside", isCollapse.value);
  // 折叠时 el-menu 会收起全部目录，重新展开后恢复当前目录
  if (!isCollapse.value) {
    openCurrentParent();
  }
};

onMounted(openCurrentParent);

const handleSelect = (e: any) => {
  const target = e.path || e.fullPath;
  if (!target) return;
  router.push({
    path: target,
  });
};
</script>

<style scoped lang="less">
#basic-aside {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: linear-gradient(180deg, #1a478a 0%, #143a70 100%);
}
// 菜单需保持与品牌区一致的底色，否则折叠态的浮层子菜单会变成透明
:deep(.el-menu) {
  z-index: 10; // 配合 ChatView
}
.aside-head {
  flex: none;
  height: 80px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  box-sizing: border-box;
  color: #bfcbd9;
  overflow: hidden;
  transition: height 0.25s ease;
}
.aside-brand {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  h1 {
    font-size: 16px;
    font-weight: 600;
    line-height: 1.2;
    color: #bfcbd9;
    white-space: nowrap;
  }
}
.aside-slogan {
  font-size: 12px;
  color: #bfcbd9;
  white-space: nowrap;
}
.collapse-toggle {
  flex: none;
  cursor: pointer;
  color: #bfcbd9;
  transition: color 0.2s ease;
  &:hover {
    color: #91cc75;
  }
}
.aside-divider {
  flex: none;
  margin: 0 0 6px;
  border-color: rgba(255, 255, 255, 0.16);
}
// 菜单滚动区：细滚动条，深色面板上半透明白
.aside-menu-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.28) transparent;

  &::-webkit-scrollbar {
    width: 6px;
    height: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.24);
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb:hover {
    background: rgba(255, 255, 255, 0.42);
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
}
.aside-menu {
  border: none;
  &:not(.el-menu--collapse) {
    width: 100%;
  }
}
// 折叠态：头部收窄，只保留展开按钮
#basic-aside.is-collapse {
  .aside-head {
    height: 56px;
    padding: 0;
    justify-content: center;
  }
}
</style>