# AGENTS.md — oshwhub_Android

立创开源硬件平台（https://oshwhub.com/）的 Android 客户端：直接逆向站点 Web API，
用 Kotlin + Jetpack Compose(Material 3) 重绘界面，**不是** WebView 套壳。

## 构建 / 测试命令（Windows）

```powershell
$env:JAVA_HOME="C:\Users\lenovo\AppData\Local\Programs\Android Studio 2\jbr"
.\gradlew.bat :app:assembleDebug          # 产物 app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat :app:testDebugUnitTest      # 联网接口测试（ApiSmokeTest / ApiCoverageTest）
```

单测直接打线上接口，需要联网；失败信息在
`app/build/test-results/testDebugUnitTest/TEST-*.xml`（`system-out` 里有打印的统计）。

## 版本号 / 发布（CI）

仓库里**没有需要手改的版本号常量**：`app/build.gradle.kts` 在配置期从 git 现场推导。
`versionName` 跟 tag 走，`versionCode` 用日期式（实测输出，拿去对表）：

| git 状态 | versionName | versionCode |
| --- | --- | --- |
| HEAD 正好在 tag `v1.4.0` 上 | `1.4.0` | HEAD 提交日期，如 `20261021` |
| tag `v1.4.0` 之后有 1 个提交 | `1.4.1-dev.1+gf074ede` | 同上 |
| HEAD 在预发布 tag `v2.0.0-rc.1` 上 | `2.0.0-rc.1` | 同上 |
| 一个 tag 都没有（当前状态） | `0.0.0-dev.N+g<sha7>` | 同上 |

- **versionCode = `yyyyMMdd`**，取 `git log -1 --format=%cd --date=short`（HEAD 提交自带时区）：
  跨机器、跨重跑都稳定，不受构建那一刻的时钟影响。同一天发两次必须覆盖，否则同名同码。
- 覆盖口径（本地 / CI 通用）：`-Poshwhub.versionName=1.4.0 -Poshwhub.versionCode=2026102202`。
  不是 git 工作区（源码包导出）→ 降级成 `0.0.0-dev.N` + code `1`，不会配置失败。
- **发版就一条**：`git tag v1.1.0 && git push --tags`（tag 必须 `v` + 数字开头）。
- `.github/workflows/android.yml`：checkout 必须 `fetch-depth: 0`——`git describe --tags`
  和 changelog 的 tag 区间都要完整历史，浅克隆会退化成 `0.0.0-dev.N`。
  只构建 release，用 cache 里的 debug keystore 重签，产物
  `<applicationId>-<versionName>.apk` + 同名 `.sha256`，tag 推送时挂 Release。
- Release 正文 = GitHub 自动 notes（`generate_release_notes: true`，分组规则在
  `.github/release.yml`，**只统计合并的 PR**）→ 之后再用 `gh api PATCH` 追加
  `.github/scripts/changelog.py` 按 conventional commit 分组的提交清单。
  本项目直推 master，自动 notes 里往往只有 Full Changelog 链接，所以那一段才是正文。
- 于是提交信息尽量守 `type(scope): subject`（feat / fix / docs / refactor / perf / ci /
  build / test / chore）；`type!:` 或正文 `BREAKING CHANGE` 会进"破坏性变更"分组。
  本地可跑：`python3 .github/scripts/changelog.py --range v1.0.0..HEAD`。
- 产物路径：release 变体的 `output-metadata.json` 与 `app-release-unsigned.apk` 在
  `app/build/outputs/apk/release/`（CI 只信这份 metadata 里的 applicationId/versionName/
  versionCode）。`app/release/` 下那份是历史遗留的已签名包，已 `.gitignore`，别拿它当版本依据。

## 模块结构

| 路径 | 作用 |
| --- | --- |
| `data/net/ApiClient.kt` | OkHttp + 持久化 Cookie + `{success,code,result}` 信封解析、统一 UA/Referer |
| `data/OshApi.kt` | 与站点 web 一一对应的接口层（全部端点集中在这里） |
| `data/AuthRepository.kt` | passport.jlc.com 的 CAS 登录（SM2 加密密码 / 短信验证码 / `loginByCasTicket`） |
| `data/net/WebCookieBridge.kt` | WebView cookie → CookieStore（风控 cookie 同步，绕开 HttpOnly 读不到会话 cookie） |
| `ui/auth/WebLoginScreen.kt` | 网页登录：WebView 跑真实登录页，拦 `AC-` 票据交回 OkHttp 换会话 |
| `data/crypto/Sm2.kt` | 纯 Kotlin SM2（C1C3C2）加密，用于登录参数 |
| `data/model/Models.kt` | `@Serializable` DTO + 容错序列化器 |
| `data/AppContainer.kt` | 手写依赖容器（api / settings / session / history） |
| `ui/AppRoot.kt` | 导航壳：`Routes` + NavHost + 底部栏/侧边 Rail 自适应 |
| `ui/<feature>/*.kt` | 每个页面一个 ViewModel + Screen（StateFlow + LaunchedEffect） |
| `ui/components/*.kt` | 通用卡片、空态/错误态、Markdown/HTML 渲染 |
| `_tmp/` | 逆向期抓包与工具（`tools/capture.mjs` CDP 抓 API、`tools/drive.mjs` 导航页面） |

## 已验证的接口事实（别照直觉改）

- `/api/common/` 下的资源路径是**复数**：`/api/common/projects/{uuid}/comments|status|like|collect|addViewCount`。
  `OshApi.apiType()` 负责把上层用的单数映射成复数。
- 详情类接口是 **GET**：`/api/articles/{uuid}`、`/api/posts/{uuid}`。
  用 POST 会返回 `401 {success:false,msg:""}`。
- 搜索是 **POST** `/api/search/data`，body `{type,keywords,category,sort,page,pageSize}`；
  `type` 取 `project|user|article|post`，返回的 `lists` 里元素结构随 type 变化
  （因此 `SearchPage.lists` 用 `JsonElement`）。
- 列表接口的 tab 参数不能乱传：`/api/articles?tab=recommend` 匿名访问返回 `total=0`，
  必须**省略 tab**（或 `filter=all` 取全量）。
- 文章分类（技术/高校/产品…）没有服务端过滤参数：`catalog=` 被忽略，`type` 只区分
  content_type（1 长文 / 3 资讯）。`ArticleListViewModel` 按返回项里的 `catalog`
  字段在客户端筛，逐页扫描后端列表凑满一屏。
- `/api/articles` 列表页在 web 上是 **SSR** 的（抓包看不到 XHR），只能靠 JSON 接口自拼。
- 合集地址用 `path`（如 `officeRecommend`），接口只认 **uuid**：
  `GET /api/collections/{path}` 会返回 `code 130000`。用 `OshApi.resolveCollectionKey()`
  翻 `/api/collections` 列表把 path 映射成 uuid。
- 团队账号（如 `li-chuang-kai-fa-ban`）的 `/api/users/{uuid}/projects` 恒为 0，团队工程走 `/api/teams/`。
- 后端字段类型不固定，例如 `users/{uuid}/info` 的 `info` 可能是 `{}`、可能缺失字段、
  也可能是**空数组 `[]`**；`ProfileInfo.companySize` 可能是 `0` 也可能是 `""`。
  → `Models.kt` 里用 `LenientStringSerializer` / `LenientProfileInfoSerializer` 兜底，
  否则整条 `UserInfo` 会变成 `null`。
- `/api/notifications` 的消息项只有 `user, content, type, status, uuid, updated_at, created_at`：
  **没有 `title`，也没有布尔 `read`**，已读状态是字符串 `status = "unread"/"read"`
  （所以 DTO 要加 `status`，用 `Notification.unread` 判断，别读 `read`）。
  `type` 只区分 `allNotifications`（通知）与 `message`（私信），传 `like/follow/interaction`
  等一律按"全部通知"返回，`system` 只是消息本身的类型而不是筛选值。
- 站点 header 铃铛下拉固定传 `read=false`（只要未读）；**消息列表必须省略 `read`**
  才拿得到历史消息，否则读过的全部消失。另有 `GET /api/notifications/unread`
  （pageSize 1000）返回未读列表。
- `POST /api/notifications/read` body `{"uuids":[...]}`，成功后服务端 `status` 变 `read`。

## 登录 / 风控（重要）

- 登录走 `passport.jlc.com` 的 CAS：`/api/cas/login/get-init-session` →
  `/with-password`(SM2) 或 `/with-sms/send-code|check-code` → 回跳带 `code=AC-<32hex>` →
  `POST https://oshwhub.com/api/login/login-by-code` (form `code=...`) → `GET /api/users` 验证。
- 短信/登录接口有火山 WAF 风控，纯 OkHttp 会收到
  `检测到当前环境有风险，需要安全验证！`。**别试着伪造风控 cookie**，正解是
  `ui/auth/WebLoginScreen.kt`：WebView 里跑真实登录页（滑块/短信都在页面里过），
  `WebViewClient` 从 URL 抓 `AC-[0-9a-f]{32}` 并 `shouldOverrideUrlLoading` 返回 true
  拦下跳转，改由 `AuthRepository.loginByCasTicket(code)` 用 OkHttp 换会话。
- 会话 cookie 是 HttpOnly 的，`CookieManager.getCookie()` 读不到，
  所以**不能**用"WebView 登录完同步 cookie"当主路径；`WebCookieBridge` 只同步
  `HWWAFSESID` 一类非 HttpOnly 的风控 cookie。
- CAS 登录页对 UA 敏感：`WebLoginScreen` 和 `ApiClient` 都用同一份桌面 Chrome UA。

## 品牌资源

- 图标：`drawable/ic_launcher_background.xml`（海军蓝→蓝→青渐变）+ `ic_launcher_foreground.xml`
  （芯片环 + 8 圆头引脚，收敛在 66dp 安全区）+ `ic_launcher_monochrome.xml`（主题图标）。
  旧的 `mipmap-*/ic_launcher.webp` 已删除（minSdk 31 只用 anydpi 自适应图标）。
- 启动画面：`drawable/splash_logo.xml`（240dp 画布，内容在中圈 112dp），
  在 `values/themes.xml` 与 `values-night/themes.xml` 里挂
  `windowSplashScreenBackground / AnimatedIcon / IconBackgroundColor`。

## 代码约定 / 坑

- Kotlin 块注释**可嵌套**：注释里写 `/api/common/*` 会开启一个未闭合注释，编译报
  “Unclosed comment”。写接口通配时用 `/api/common/` 或反引号包裹。
- 图片 URL 多为协议相对（`//image.lceda.cn/...`），统一走 `Fmt.url()` 规范化。
- 主题走 `ui/theme/OshTheme`（支持动态取色、纯黑、跟随系统），窗口宽度类随
  `calculateWindowSizeClass` 传进 `LocalWindowWidth`。
- 新增页面：`OshApi` 加接口 → `data/model` 加 DTO → `ui/<feature>` 写
  `XxxViewModel(StateFlow)+XxxScreen` → 在 `AppRoot.kt` 注册 `Routes` 与 `composable`。
- **别在进页面时自动 `markNotificationsRead`**：读接口只给未读时，一进页面标记已读
  ＝消息在列表里永久消失。`NotificationsScreen` 改成只由"全部已读"按钮触发。
- 抓包/验证新接口优先用 `_tmp/tools`：先 `launch-edge.ps1` 起 CDP，再
  `node _tmp/tools/capture.mjs`（后台）+ `node _tmp/tools/drive.mjs <url>` 导航。
