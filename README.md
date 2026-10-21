# Agnes 搜索 · 创作

> 一个**会自己联网查资料**的 AI 搜索 Agent，外加**图像生成 / 编辑**和**视频生成**（Android / Jetpack Compose）。
> 默认接入 [Agnes AI](https://www.agnes-ai.com/zh-Hans/docs/overview)（OpenAI 兼容接口），端点、Key、模型全部可自配。

## 搜索：不是套壳对话，是真去查

```
提问 → 自己决定搜什么 → 抓搜索结果 → 打开原文读正文 → 判断够不够 → 再搜 → 带引用编号作答
```

| 能力 | 实现 |
| - | - |
| 联网检索 | 内置多引擎抓取：**百度** / Bing 中国 / Bing / DuckDuckGo / DDG Lite / SearXNG（自建实例可选）。**不需要任何第三方搜索 Key** |
| 读原文 | Jsoup 抓页面 + 正文提取（剥导航/广告/评论，取正文密度最大的容器），自动识别 GBK / UTF-8 |
| 工具调用 | OpenAI 兼容的 `tools` / `tool_call`，模型自己决定调 `web_search` 还是 `open_url` |
| 流式输出 | SSE 增量解析 + 自研轻量 Markdown 渲染（标题/列表/表格/代码块/粗体行内码） |
| 引用 | 回答里的 `[1] [2]` 与来源卡片编号一致，点卡片直接跳浏览器 |

## 创作：图像 & 视频

| 能力 | 接口 | 说明 |
| - | - | - |
| 文生图 | `POST /v1/images/generations` | 尺寸档位 `1K/2K/3K/4K` + 画幅比例，2.0 模型自动换算精确尺寸 |
| 图生图 / 多图合成 | 同上 | 相册选图或贴 URL，最多 4 张，自动压成 Data URI 直传 |
| 视频生成 | `POST /v1/videos` + `GET /agnesapi` | 文生视频 / **首尾帧控制** / 图参考，时长 4-12s，720P-2K，异步任务自动轮询进度 |
| 结果处理 | — | 大图查看弹窗、保存到相册（MediaStore，落到 `Pictures/AgnesSearch`）、分享、视频系统播放器打开；生成物同时留一份 App 内缓存，URL 过期也能看 |

## 功能一览

- **搜索页**：大搜索框 + 推荐问法；实时状态（正在检索 / 正在阅读 / 整理中）与步骤轨迹；可随时停止；结果带来源卡片
- **创作页**：图像 / 视频两个面板，参数全可视化（pills 选择），异步视频任务带进度条、刷新、取消、保存
- **历史**：搜索记录 + 图像 + 视频三类，本地保存，支持关键词过滤 / 收藏 / 删除 / 回看
- **我的**：用量统计（搜索次数、生成图像、生成视频、Token、花费估算）、接口设置、清空数据、关于
- **接口设置**：Base URL、API Key、文本/图像/视频模型、搜索源、SearXNG 地址、检索轮次、结果数、网页阅读数、温度、最大输出、Thinking、自定义系统提示、代理、主题、回答语言、图像与视频默认参数、自动存相册
- **一键测试连接**：调 `/models` 校验端点与 Key
- 深浅色主题 / 渐变玻璃拟态 UI / 悬浮胶囊底栏（4 tab）

## 默认配置

| 项 | 值 |
| - | - |
| Base URL | `https://apihub.agnes-ai.com/v1` |
| 文本模型 | `agnes-2.5-flash`（另可选 `agnes-3.0-flash` / `agnes-2.5-pro` / `agnes-2.5-pro-beta`） |
| 图像模型 | `agnes-image-2.5-flash`（另有 `2.1` / `2.0`） |
| 视频模型 | `agnes-video-2.5`（另有 `agnes-video-2.5-flash`） |
| 认证 | `Authorization: Bearer <API Key>` |

换任何 OpenAI 兼容服务（自建 vLLM、OneAPI、OpenRouter…）只需改 Base URL + Key + 模型名。

## 构建

仓库自带 GitHub Actions 复用工作流，推 `main` 即出 APK；打 `v*` tag 自动发 Release。

- 工作流：`.github/workflows/build.yml`，实际逻辑复用 `Code-xy/video_hex/.github/workflows/android-build.yml@main`
- 产物：`ci-logs` 分支的 `.ci/` 目录（构建日志 + APK + meta），不用 GitHub API 也能拿包
- 签名：仓库内置 `signing/dev-release.jks`，固定签名保证覆盖安装

```bash
./gradlew assembleRelease     # 需要 Android SDK + JDK 17
```

## 目录

```
app/src/main/java/com/seagull/agnesearch/
├── core/      Models.kt（配置/会话/生成记录/用量） Store.kt（SharedPreferences + StateFlow）
├── net/       Http.kt（OkHttp + 可选代理） AgnesClient.kt（SSE 流式 + 工具调用）
│              MediaClient.kt（图像生成 / 视频任务与轮询）
├── search/    SearchEngine.kt（百度和 Bing/DDG/SearXNG 抓取与自动降级） PageReader.kt（正文提取）
├── agent/     AgentRunner.kt（检索—阅读—作答 的工具调用循环）
├── util/      ImagePicker.kt（相册图 → Data URI） MediaSaver.kt（存相册 / 落本地）
└── ui/        theme/ components/（玻璃卡片·自绘控件·Markdown·自研图片加载）
               search/ create/ history/
```

## 数据与隐私

API Key、搜索历史、生成记录、用量统计**只存在本机**，不上传任何服务器；请求由本机直连你配置的端点。
