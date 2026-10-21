# Agnes 搜索

> 一个**会自己联网查资料**的 AI 搜索 Agent（Android / Jetpack Compose）。
> 默认接入 [Agnes AI](https://www.agnes-ai.com/zh-Hans/docs/overview)（OpenAI 兼容接口），端点、Key、模型全部可自配。

AGNES**不是**那种"在对话框里编答案"的套壳。它的执行链路是：

```
用户提问 → 自己决定搜什么 → 抓取搜索结果 → 打开原文读正文 → 判断信息够不够 → 再搜/收尾 → 带引用编号作答
```

## 它到底做了什么

| 能力 | 实现 |
| - | - |
| 联网检索 | App 内置多引擎抓取：Bing 中国 / Bing / DuckDuckGo / DuckDuckGo Lite / SearXNG（自建实例可选）。**不需要任何第三方搜索 API Key** |
| 读原文 | Jsoup 抓页面 + 正文抽取（剥导航/广告/评论，取正文密度最大的容器），自动识别 GBK/UTF-8 |
| 工具调用 | 通过 OpenAI 兼容的 `tools` / `tool_call` 协议，模型自己决定调 `web_search` 还是 `open_url` |
| 流式输出 | SSE 增量解析，边生成边显示；答案用自研轻量 Markdown 渲染（标题/列表/表格/代码块/粗体行内码） |
| 引用 | 回答里的 `[1] [2]` 和下方来源卡片编号一致，点卡片直接跳浏览器 |

## 功能

- **搜索页**：大搜索框 + 推荐问法；运行中显示实时状态（正在检索 / 正在阅读 / 整理中）与步骤轨迹；可随时停止
- **历史**：本地保存，支持关键词过滤 / 只看收藏 / 收藏 / 删除；点历史直接回看完整答案与来源
- **我的**：使用统计（搜索次数、调用轮次、输入/输出 Token、花费估算）、接口设置、清空数据、关于
- **接口设置**：Base URL、API Key、模型（预置 Agnes 全部文本模型 + 自定义）、搜索源、SearXNG 地址、每轮结果数、自动阅读网页数、检索轮次上限、温度、最大输出、Thinking 模式、自定义系统提示、代理、主题、回答语言
- **一键测试连接**：调 `/models` 校验端点与 Key
- 深浅色主题 / 渐变玻璃拟态 UI / 悬浮胶囊底栏

## 默认配置

| 项 | 值 |
| - | - |
| Base URL | `https://apihub.agnes-ai.com/v1` |
| 模型 | `agnes-2.5-flash`（可选 `agnes-3.0-flash` / `agnes-2.5-pro` / `agnes-2.5-pro-beta`） |
| 认证 | `Authorization: Bearer <API Key>` |

换任何 OpenAI 兼容服务（自建 vLLM、OneAPI、OpenRouter…）只需改 Base URL + Key + 模型名。

## 构建

仓库自带 GitHub Actions 复用工作流，推 `main` 即出 APK；打 `v*` tag 自动发 Release。

- 工作流：`.github/workflows/build.yml`，实际逻辑复用 `Code-xy/video_hex/.github/workflows/android-build.yml@main`
- 产物：`.ci/` 目录在 `ci-logs` 分支（构建日志 + APK + meta），不用 GitHub API 也能拿包
- 签名：仓库内置 `signing/dev-release.jks`，固定签名保证覆盖安装

本地构建（需要 Android SDK + JDK 17）：

```bash
./gradlew assembleRelease
```

## 目录

```
app/src/main/java/com/seagull/agnesearch/
├── core/      Models.kt（配置/会话/用量） Store.kt（SharedPreferences + StateFlow）
├── net/       Http.kt（OkHttp + 可选代理） AgnesClient.kt（SSE 流式 + 工具调用 + 连通性测试）
├── search/    SearchEngine.kt（多引擎抓取与降级） PageReader.kt（正文抽取）
├── agent/     AgentRunner.kt（检索—阅读—作答 的工具调用循环）
└── ui/        theme/ components/（玻璃卡片·自绘控件·Markdown 渲染） search/ history/
```

## 数据与隐私

API Key、历史记录、用量统计**只存在本机** SharedPreferences，不上传任何服务器；请求由本机直连你配置的端点。
