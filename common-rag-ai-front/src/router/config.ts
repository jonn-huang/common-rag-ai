import type { RouteRecordRaw } from "vue-router";

let routes: RouteRecordRaw[] = [
  {
    path: "/",
    name: "index",
    redirect: "/ragChat",
    meta: {
      isMenu: false,
      requiresAuth: true,
    },
  },
  {
    // 登录页独立成页：不渲染侧边栏、Header 与 Footer
    path: "/login",
    name: "login",
    component: () => import("@/view/login/LoginView.vue"),
    meta: {
      isMenu: false,
      layout: "blank",
      requiresAuth: false,
    },
  },
  {
    // 个人中心：账号资料、安全设置与知识库数据概览（登录后默认落地页为 AI 问答）
    path: "/profile",
    name: "profile",
    component: () => import("@/view/profile/ProfileView.vue"),
    meta: {
      isMenu: true,
      description: "个人中心",
      icon: "Avatar",
      requiresAuth: true,
    },
  },
  {
    // 一级菜单：AI 能力，只做目录分组，不渲染页面（无 component，由子路由渲染）
    path: "/ai",
    name: "ai",
    redirect: "/ragChat",
    meta: {
      isMenu: true,
      description: "AI 助手",
      icon: "MagicStick",
      requiresAuth: true,
    },
    children: [
      {
        path: "/ragChat",
        name: "ragChat",
        component: () => import("@/view/ragChat/RagChatView.vue"),
        meta: {
          isMenu: true,
          description: "AI问答",
          icon: "ChatDotRound",
          requiresAuth: true,
        },
      },
      {
        path: "/draw",
        name: "draw",
        component: () => import("@/view/draw/DrawImageView.vue"),
        meta: {
          isMenu: true,
          description: "AI绘画",
          icon: "PictureRounded",
          requiresAuth: true,
        },
      },
    ],
  },
  {
    // 一级菜单：知识库，只做目录分组，不渲染页面（无 component，由子路由渲染）
    path: "/knowledge",
    name: "knowledge",
    redirect: "/document",
    meta: {
      isMenu: true,
      description: "我的知识库",
      icon: "Collection",
      requiresAuth: true,
    },
    children: [
      {
        path: "/document",
        name: "document",
        component: () => import("@/view/know/KnowHubView.vue"),
        meta: {
          isMenu: true,
          description: "文档管理",
          icon: "Document",
          requiresAuth: true,
        },
      },
      {
        path: "/document-chuck",
        name: "documentChuck",
        component: () => import("@/view/know/DocumentChuckView.vue"),
        meta: {
          isMenu: true,
          description: "文档切片管理",
          icon: "Files",
          requiresAuth: true,
        },
      },
      {
        path: "/answer-prompt",
        name: "answerPrompt",
        component: () => import("@/view/know/AnswerPromptView.vue"),
        meta: {
          isMenu: true,
          description: "问答提示词",
          icon: "EditPen",
          requiresAuth: true,
        },
      },
    ],
  },
  {
    // 兼容旧地址：原 /know-hub 已平移为 /document
    path: "/know-hub",
    redirect: "/document",
  },
  {
    // 一级菜单：内容安全，只做目录分组，不渲染页面
    path: "/security",
    name: "security",
    redirect: "/sensitive",
    meta: {
      isMenu: true,
      description: "内容安全",
      icon: "Lock",
      requiresAuth: true,
      roles: ["admin"],
    },
    children: [
      {
        path: "/sensitive",
        name: "sensitive",
        component: () => import("@/view/sensitive/SensitiveWordView.vue"),
        meta: {
          isMenu: true,
          description: "敏感词管理",
          icon: "WarnTriangleFilled",
          requiresAuth: true,
          roles: ["admin"],
        },
      },
      {
        path: "/senCategory",
        name: "senCategory",
        component: () => import("@/view/sensitive/SenCategoryView.vue"),
        meta: {
          isMenu: true,
          description: "敏感词分类",
          icon: "Menu",
          requiresAuth: true,
          roles: ["admin"],
        },
      },
      {
        path: "/frequency",
        name: "frequency",
        component: () => import("@/view/frequency/WordFrequencyView.vue"),
        meta: {
          isMenu: true,
          description: "热点词分析",
          icon: "Histogram",
          requiresAuth: true,
          roles: ["admin"],
        },
      },
      {
        path: "/hot-question",
        name: "hotQuestion",
        component: () => import("@/view/security/HotQuestionView.vue"),
        meta: {
          isMenu: true,
          description: "热门问题配置",
          icon: "ChatLineSquare",
          requiresAuth: true,
          roles: ["admin"],
        },
      },
    ],
  },
  {
    // 一级菜单：系统管理，只做目录分组，不渲染页面
    path: "/system",
    name: "system",
    redirect: "/user",
    meta: {
      isMenu: true,
      description: "系统管理",
      icon: "Setting",
      requiresAuth: true,
      roles: ["admin"],
    },
    children: [
      {
        path: "/user",
        name: "user",
        component: () => import("@/view/user/UserView.vue"),
        meta: {
          isMenu: true,
          description: "用户管理",
          icon: "UserFilled",
          requiresAuth: true,
          roles: ["admin"],
        },
      },
      {
        path: "/logInfo",
        name: "logInfo",
        component: () => import("@/view/logInfo/LogInfoView.vue"),
        meta: {
          isMenu: true,
          description: "日志管理",
          icon: "List",
          requiresAuth: true,
          roles: ["admin"],
        },
      },
    ],
  },
];

export default routes;