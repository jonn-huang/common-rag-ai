<template>
  <aside class="session-panel">
    <div class="session-panel__head">
      <el-button class="create-btn" type="primary" @click="emit('create')">
        <el-icon><Plus /></el-icon>
        <span>新建对话</span>
      </el-button>
      <el-input
        v-model="keyword"
        size="small"
        clearable
        placeholder="搜索历史对话"
        :prefix-icon="Search"
      />
    </div>

    <div class="session-panel__list">
      <template v-if="filteredGroups.length">
        <div v-for="group in filteredGroups" :key="group.label" class="session-group">
          <div class="session-group__label">{{ group.label }}</div>
          <div
            v-for="session in group.items"
            :key="session.id"
            class="session-item"
            :class="{ 'is-active': session.id === activeId }"
            @click="handleSelect(session.id)"
          >
            <el-icon class="session-item__icon"><ChatLineRound /></el-icon>

            <el-input
              v-if="editingId === session.id"
              v-model="editingTitle"
              class="session-item__rename"
              size="small"
              maxlength="30"
              @click.stop
              @keyup.enter="commitRename(session.id)"
              @keyup.esc="cancelRename"
              @blur="commitRename(session.id)"
            />
            <template v-else>
              <span class="session-item__title" :title="session.title">{{ session.title }}</span>
              <span class="session-item__time">{{ formatSessionTime(session.updatedAt) }}</span>
              <span class="session-item__actions" @click.stop>
                <el-tooltip content="重命名" placement="top">
                  <el-icon class="action-icon" @click="startRename(session)"><EditPen /></el-icon>
                </el-tooltip>
                <el-popconfirm
                  title="删除该对话？"
                  confirm-button-text="删除"
                  cancel-button-text="取消"
                  width="180"
                  @confirm="emit('delete', session.id)"
                >
                  <template #reference>
                    <el-icon class="action-icon action-icon--danger"><Delete /></el-icon>
                  </template>
                </el-popconfirm>
              </span>
            </template>
          </div>
        </div>
      </template>
      <div v-else class="session-panel__empty">
        <el-icon><ChatDotRound /></el-icon>
        <p>{{ keyword ? "没有匹配的对话" : "暂无历史对话" }}</p>
      </div>
    </div>

    <div class="session-panel__foot">
      <el-icon><CircleCheck /></el-icon>
      <span>企业知识库已就绪</span>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import {
  ChatDotRound,
  ChatLineRound,
  CircleCheck,
  Delete,
  EditPen,
  Plus,
  Search,
} from "@element-plus/icons-vue";
import type { ChatSession, SessionGroup } from "@/composables/useChatSessions";

const props = defineProps<{
  groups: SessionGroup[];
  activeId: string;
}>();

const emit = defineEmits<{
  (e: "create"): void;
  (e: "select", id: string): void;
  (e: "rename", id: string, title: string): void;
  (e: "delete", id: string): void;
}>();

const keyword = ref("");
const editingId = ref("");
const editingTitle = ref("");

const filteredGroups = computed(() => {
  const word = keyword.value.trim().toLowerCase();
  if (!word) return props.groups;
  return props.groups
    .map((group) => ({
      label: group.label,
      items: group.items.filter((item) => item.title.toLowerCase().includes(word)),
    }))
    .filter((group) => group.items.length > 0);
});

const formatSessionTime = (timestamp: number) => {
  const date = new Date(timestamp);
  const diff = Date.now() - timestamp;
  if (diff < 60 * 1000) return "刚刚";
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)} 分钟前`;
  const isToday = new Date().toDateString() === date.toDateString();
  if (isToday) {
    return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
  }
  return `${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
};

const handleSelect = (id: string) => {
  if (editingId.value === id) return;
  emit("select", id);
};

const startRename = (session: ChatSession) => {
  editingId.value = session.id;
  editingTitle.value = session.title;
  nextTick(() => {
    const input = document.querySelector<HTMLInputElement>(".session-item__rename input");
    input?.focus();
    input?.select();
  });
};

const commitRename = (id: string) => {
  if (editingId.value !== id) return;
  emit("rename", id, editingTitle.value);
  editingId.value = "";
};

const cancelRename = () => {
  editingId.value = "";
};
</script>

<style scoped lang="less">
.session-panel {
  display: flex;
  flex-direction: column;
  width: 260px;
  flex: 0 0 260px;
  height: 100%;
  padding: 16px 12px 12px;
  box-sizing: border-box;
  background: #ffffff;
  border: 1px solid #e8eef7;
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(26, 71, 138, 0.06);
}

.session-panel__head {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.create-btn {
  width: 100%;
  height: 38px;
  border: none;
  border-radius: 10px;
  font-weight: 500;
  background: linear-gradient(120deg, #1a478a 0%, #2f7cf6 100%);
  box-shadow: 0 6px 14px rgba(47, 124, 246, 0.22);

  &:hover,
  &:focus {
    background: linear-gradient(120deg, #1a478a 0%, #2f7cf6 100%);
    opacity: 0.92;
  }
}

.session-panel__list {
  flex: 1;
  min-height: 0;
  margin: 12px -4px 0;
  padding: 0 4px 4px;
  overflow-y: auto;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: #d7e2f2;
    border-radius: 3px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }
}

.session-group__label {
  padding: 10px 8px 6px;
  font-size: 12px;
  color: #93a1b5;
}

.session-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 10px;
  margin-bottom: 2px;
  border-radius: 9px;
  font-size: 13px;
  color: #48576c;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;

  &::before {
    content: "";
    position: absolute;
    left: 0;
    top: 8px;
    bottom: 8px;
    width: 3px;
    border-radius: 2px;
    background: transparent;
    transition: background 0.2s ease;
  }

  &:hover {
    background: #f4f7fc;

    .session-item__time {
      opacity: 0;
    }

    .session-item__actions {
      opacity: 1;
    }
  }

  &.is-active {
    background: #eef4ff;
    color: #1a478a;
    font-weight: 500;

    &::before {
      background: #2f7cf6;
    }
  }
}

.session-item__icon {
  flex: 0 0 auto;
  font-size: 14px;
  color: #8fa3bf;
}

.session-item__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item__time {
  flex: 0 0 auto;
  font-size: 11px;
  color: #a6b3c6;
  transition: opacity 0.2s ease;
}

.session-item__actions {
  position: absolute;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.action-icon {
  font-size: 14px;
  color: #7d8ea6;

  &:hover {
    color: #2f7cf6;
  }
}

.action-icon--danger:hover {
  color: #e45656;
}

.session-item__rename {
  flex: 1;
  min-width: 0;
}

.session-panel__empty {
  padding-top: 40px;
  text-align: center;
  color: #a6b3c6;
  font-size: 12px;

  .el-icon {
    font-size: 24px;
    margin-bottom: 8px;
  }
}

.session-panel__foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 10px 8px 2px;
  border-top: 1px solid #eef2f9;
  font-size: 12px;
  color: #8fa3bf;
}
</style>