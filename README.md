# Agnes 客户端

> 接入 [Agnes AI](https://www.agnes-ai.com/zh-Hans/docs/overview)（OpenAI 兼容接口）的 Android 客户端：**多会话聊天 + 图像生成/编辑 + 视频生成**。
> 端点、API Key、模型全部可自配，换任何 OpenAI 兼容服务都能用。

Jetpack Compose · Material3 · 纯自绘组件（无第三方 UI 库）· 深浅色主题。

## 三大功能

### 1. 对话（多会话聊天）

- **流式输出**：SSE 增量解析，边生成边显示；也可关掉流式等整段回复
- **多会话**：左上角 `+` 新建，点标题弹出会话列表切换/删除；历史页可搜索、收藏
- **多模态输入**：最多 4 张图（相册选图自动压成 Data URI，走 `image_url` 内容块）
- **Markdown 渲染**：标题 / 列表 / 表格 / 代码块 / 行内码 / 粗体，自研轻量渲染器
- **上下文控制**：每次带多少条历史可调；温度、最大输出、Thinking 模式、自定义系统提示
- 每条回复可复制、可重发；token 与耗时直接标在气泡下

### 2. 图像生成 / 编辑

| 接口 | 能力 |
| - | - |
| `POST /v1/images/generations` | 文生图、图生图、多图合成（最多 4 张参考图） |

- 尺寸档位 `1K / 2K / 3K / 4K` + 画幅 `1:1 / 4:3 / 3:4 / 16:9 / 9:16 / 3:2 / 2:3 / 21:9`
- `2.0` 模型自动把档位换算成该模型支持的精确尺寸
- 参考图支持相册选图或直接贴 URL；选择器结构对不上也能降级解析

### 3. 视频生成

| 接口 | 能力 |
| - | - |
| `POST /v1/videos` + `GET /agnesapi?video_id=…` | 文生视频 / **首尾帧控制** / 图参考，异步任务轮询 |

- 时长 `4-12s`，分辨率 `720P / 1080P / 1K / 2K`，画幅 6 种
- 首尾帧支持相册选图（Data URI）或 URL；参考图提示词里用 `<Picture N>` 指代
- 提交后进任务列表，实时进度百分比，可刷新 / 取消；完成后系统播放器打开或存相册

## 结果与数据

- 图像/视频：大图弹窗看原图、保存到相册（`Pictures|Movies/AgnesSearch`）、分享链接；同时落一份 App 私有缓存，URL 过期也能看
- 历史页：**对话 / 图像 / 视频** 三类，关键词过滤、收藏、删除、回看
- 我的页：对话消息数、模型调用、生成图像/视频、输入输出 Token、花费估算
- **全部数据只存本机** SharedPreferences，不上传任何服务器

## 默认配置

| 项 | 值 |
| - | - |
| Base URL | `https://apihub.agnes-ai.com/v1` |
| 文本模型 | `agnes-3.0-flash` / `agnes-2.5-flash` / `agnes-2.5-pro` / `agnes-2.5-pro-beta` |
| 图像模型 | `agnes-image-2.5-flash` / `2.1` / `2.0` |
| 视频模型 | `agnes-video-2.5` / `agnes-video-2.5-flash` |
| 认证 | `Authorization: Bearer <API Key>` |

支持自建代理设置（例如 Clash 的 `http://127.0.0.1:7890`），所有请求统一走它。

## 构建

```bash
./gradlew assembleRelease    # 需要 Android SDK + JDK 17
```

仓库自带 GitHub Actions：推 `main` 即出 APK，构建日志和产物强推回 `ci-logs` 分支的 `.ci/` 目录；
打 `v*` tag 自动发 Release。复用工作流：`Code-xy/video_hex/.github/workflows/android-build.yml@main`。
签名用仓库内置 `signing/dev-release.jks`，固定签名保证覆盖安装。

## 目录

```
app/src/main/java/com/seagull/agnesearch/
├── core/      Models.kt（配置 / 会话 / 消息 / 生成记录 / 用量）  Store.kt（SharedPreferences + StateFlow）
├── net/       Http.kt（OkHttp + 可选代理）  AgnesClient.kt（流式 & 非流式对话、连通性测试）
│              MediaClient.kt（图像生成、视频任务创建与轮询）
├── util/      ImagePicker.kt（相册图 → Data URI）  MediaSaver.kt（存相册 / 落本地）
└── ui/        theme/ components/（玻璃卡片·自绘控件·Markdown·自研图片加载）
               chat/ create/ history/
```
