<template>
  <!-- 全局配置 Element Plus 中文语言包，统一分页等内置文案 -->
  <el-config-provider :locale="zhCn">
    <!-- 登录页等独立页面不套主框架，直接渲染路由组件 -->
    <RouterView v-if="isBlankLayout" />
    <BasicLayout v-else />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import BasicLayout from "./layout/BasicLayout.vue";

const route = useRoute();
const isBlankLayout = computed(() => route.meta.layout === "blank");
</script>

<style scoped lang="less"></style>
