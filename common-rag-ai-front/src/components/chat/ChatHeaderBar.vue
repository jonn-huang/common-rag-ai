<template>
  <header class="chat-header">
    <div class="chat-header__left">
      <el-button
        v-if="showSessionToggle"
        class="icon-btn"
        text
        circle
        @click="emit('toggle-sessions')"
      >
        <el-icon><Menu /></el-icon>
      </el-button>
      <img class="chat-header__logo" :src="robotUrl" alt="智能助手" />
      <div class="chat-header__text">
        <div class="chat-header__title">
          <span>智能知识库客服</span>
          <el-tag size="small" effect="light" round class="brand-tag">企业版</el-tag>
        </div>
        <div class="chat-header__sub">{{ sessionTitle || "基于企业知识库的智能问答" }}</div>
      </div>
    </div>

    <div class="chat-header__right">
      <span class="online">
        <i class="online__dot"></i>
        在线
      </span>
      <el-tooltip content="全局检索配置，可在「文档管理 · 知识库检索配置」中调整" placement="bottom-end">
        <el-tag class="config-tag" size="small" effect="plain" round>{{ configSummary }}</el-tag>
      </el-tooltip>
      <el-tooltip content="清空当前对话" placement="bottom">
        <el-button class="icon-btn" text circle :disabled="!hasMessages" @click="emit('clear')">
          <el-icon><Delete /></el-icon>
        </el-button>
      </el-tooltip>
    </div>
  </header>
</template>

<script setup lang="ts">
import { Delete, Menu } from "@element-plus/icons-vue";
import robotUrl from "@/assets/robot.svg";

defineProps<{
  sessionTitle?: string;
  configSummary: string;
  hasMessages: boolean;
  showSessionToggle?: boolean;
}>();

const emit = defineEmits<{
  (e: "clear"): void;
  (e: "toggle-sessions"): void;
}>();
</script>

<style scoped lang="less">
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 62px;
  flex: 0 0 62px;
  padding: 0 18px;
  background: #ffffff;
  border: 1px solid #e8eef7;
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(26, 71, 138, 0.05);
}

.chat-header__left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.chat-header__logo {
  width: 34px;
  height: 34px;
  padding: 6px;
  border-radius: 10px;
  background: linear-gradient(140deg, #eaf2ff 0%, #dbe8ff 100%);
  box-sizing: border-box;
}

.chat-header__text {
  min-width: 0;
}

.chat-header__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #16233a;
}

.brand-tag {
  border-color: #cfe0ff;
  background: #eef4ff;
  color: #2f7cf6;
}

.chat-header__sub {
  margin-top: 2px;
  font-size: 12px;
  color: #8b9bb2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-header__right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
}

.online {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #4f9f6a;
}

.online__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #6cc48a;
  box-shadow: 0 0 0 3px rgba(108, 196, 138, 0.18);
}

.config-tag {
  border-color: #dbe7f9;
  background: #f6f9ff;
  color: #5c7597;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.icon-btn {
  color: #7d8ea6;

  &:hover {
    color: #2f7cf6;
    background: #f2f7ff;
  }
}
</style>