# 立创开源 · oshwhub 桌面端（Android / Compose M3）

把 [oshwhub.com](https://oshwhub.com/) 的**桌面网页端**搬到 Android 平板／横屏上：
不是 WebView 套壳，而是逆向站点 Web API 后用 Kotlin 原生重绘，界面全部 Material Design 3。

- 语言/UI：Kotlin 2.x + Jetpack Compose（Material 3，动态取色 / 纯黑 / 跟随系统）
- 网络：OkHttp + kotlinx.serialization（Cookie 持久化，重启仍登录）
- 适配：手机竖屏 → 平板/横屏 用 `calculateWindowSizeClass` 自动切换 底部导航 ↔ 侧边 NavigationRail
- 最低版本：Android 12（minSdk 31）；编译 SDK 37，Gradle 9.3.1

---

## 1. 功能覆盖（对齐电脑端 web）

| 模块 | 已实现 |
| --- | --- |
| 首页 | 顶部轮播 Banner、快捷入口（开源广场/技术文章/平台公告/工程合集）、精彩活动、官方推荐、开源项目流 |
| 广场 Explore | 按标签/分类浏览工程流，分页加载 |
| 搜索 | 工程 / 用户 / 文章 / 帖子 四类结果、热词、结果计数，输入即搜 |
| 工程详情 | 工程信息、图片轮播、BOM、附件、评论区（点赞/回复/发表）、点赞 / 收藏 / 关注、浏览量计数、相关推荐 |
| 文章 | 技术文章 / 资讯列表、分类（技术·高校·产品）客户端筛选、Markdown 渲染、评论 |
| 帖子 | 论坛帖列表与详情、评论 |
| 合集 | 官方合集列表、合集内工程流（`path` → `uuid` 自动解析） |
| 用户 | 个人主页、工程/文章/帖子/收藏/关注/粉丝、关注与取关、资料卡 |
| 我的 | 登录态、我的工程、消息未读数、浏览历史（本地）、设置入口 |
| 消息 | 通知列表、未读计数、已读上报 |
| 活动 | 赛事征集列表与详情 |
| 登录 | 账号密码（SM2 加密）/ 手机验证码 / **浏览器（WebView）登录**三通道，见下文 |
| 其它 | 深色模式、纯黑主题、动态取色、图片自适应、Markdown/HTML 富文本、错误态与空态重试 |

## 2. 快速开始

```powershell
# JDK：用 Android Studio 自带的 JBR 即可
$env:JAVA_HOME="C:\Users\lenovo\AppData\Local\Programs\Android Studio 2\jbr"

.\gradlew.bat :app:assembleDebug          # 产物 app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat :app:testDebugUnitTest      # 联网接口测试（会真的打线上接口）
```

单元测试是"契约测试"：`ApiSmokeTest` 依次请求首页/搜索/详情/用户等关键接口，
`ApiCoverageTest` 打印全量端点成功率。失败细节在
`app/build/test-results/testDebugUnitTest/TEST-*.xml` 的 `system-out`。

## 3. 架构

```
app/src/main/java/io/github/Calvariaa/oshwhub/
├── data/
│   ├── net/ApiClient.kt      OkHttp + 持久化 Cookie + {success,code,result} 信封解析 + 统一 UA/Referer
│   ├── net/CookieStore.kt    纯文本落盘 Cookie，重启保留登录态
│   ├── net/WebCookieBridge.kt WebView → OkHttp 的 cookie 搬运（风控 cookie）
│   ├── OshApi.kt             与站点 web 一一对应的接口层（所有端点集中于此）
│   ├── AuthRepository.kt     passport.jlc.com 的 CAS 登录（SM2 / 短信 / 票据兑换）
│   ├── crypto/Sm2.kt         纯 Kotlin SM2（C1C3C2）加密
│   ├── model/Models.kt       @Serializable DTO + 容错序列化器
│   ├── SettingsStore.kt      DataStore 偏好（主题、纯黑、动态取色…）
│   └── AppContainer.kt       手写依赖容器 + SessionManager（登录态/未读/cookie 事件）
└── ui/
    ├── AppRoot.kt            Routes + NavHost + 底部栏/侧边 Rail 自适应
    ├── <feature>/XxxScreen.kt 每个页面 = ViewModel(StateFlow) + Screen
    ├── components/           卡片、空态/错误态、Markdown/HTML 渲染、评论区
    └── theme/                Material 3 主题、动态取色、纯黑
```

数据流：`Screen → ViewModel(viewModelScope) → OshApi → ApiClient → OkHttp(CookieStore)`；
登录态由 `SessionManager.user: StateFlow<UserInfo?>` 广播，各页面 `collectAsState` 响应。

## 4. 登录：为什么需要两条路

站点登录是立创统一登录中心（`passport.jlc.com`）的 CAS 流程：

```
GET  /login                      → 页面内联 SM2 公钥（sm2PublicKey）
POST /api/cas/login/get-init-session
POST /api/cas/login/with-password   （username/password 均 SM2 加密）
POST /api/cas/login/with-sms/send-code / check-code
      → 回跳 https://oshwhub.com/?code=AC-xxxx
POST https://oshwhub.com/api/login/login-by-code  (form code=AC-xxxx)
GET  /api/users                     → 验证登录态
```

**坑：** 登录/短信接口由火山引擎 WAF 保护，纯 HTTP 客户端（含本 App 的 OkHttp）会被判定
“检测到当前环境有风险，需要安全验证！”。风控依赖浏览器 JS 挑战生成的 cookie（`HWWAFSESID` 等），
所以：

1. **原生通道**（`AuthRepository`）：SM2 加密 → CAS → 拿 `AC-` 票据 → OkHttp 自己换会话。
   环境干净时可用，触发风控时会收到上面那句提示。
2. **浏览器通道**（`ui/auth/WebLoginScreen.kt`）：在 App 内的真实 Chromium（WebView）里跑完整登录页，
   手机验证码、滑块验证都在页面里完成；`WebViewClient` 盯住 URL 里的 `AC-` 票据，
   **一出现就拦下该跳转**，改由 OkHttp 调 `login-by-code` 换会话。

为什么不在 WebView 里登录完直接偷 cookie？因为会话 cookie 是 `HttpOnly` 的，
`android.webkit.CookieManager.getCookie()` 读不到（[参考](https://stackoverflow.com/questions/52653128/get-httponly-cookies-from-android-webview)）。
票据方案让会话 cookie 直接由 OkHttp 收到、写进我们自己的 `CookieStore`，绕开这个限制；
WebView 那边只把风控 cookie 通过 `WebCookieBridge` 同步过来，让后续原生接口也带着同一套指纹。

## 5. 逆向期发现（别照直觉改）

- `/api/common/` 下资源路径是**复数**：`/api/common/projects/{uuid}/comments|status|like|collect|addViewCount`。
- 详情类接口是 **GET**：`/api/articles/{uuid}`、`/api/posts/{uuid}`；用 POST 会返回 `401 {success:false,msg:""}`。
- 搜索是 **POST** `/api/search/data`，body `{type,keywords,category,sort,page,pageSize}`；
  `type ∈ {project,user,article,post}`，返回 `lists` 元素结构随 type 变化。
- `/api/articles?tab=recommend` 匿名访问返回 `total=0`，必须省略 `tab`（或 `filter=all`）。
- 文章分类没有服务端过滤参数（`catalog=` 被忽略），只能客户端按 `catalog` 字段筛。
- 合集地址用 `path`（如 `officeRecommend`），接口只认 `uuid`；`GET /api/collections/{path}` 返回 `code 130000`，
  需先翻 `/api/collections` 列表做映射（`OshApi.resolveCollectionKey()`）。
- 团队账号的 `/api/users/{uuid}/projects` 恒为 0，团队工程走 `/api/teams/`。
- 后端字段类型不稳定：`info` 可能是 `{}` / 缺字段 / **空数组 `[]`**，`companySize` 可能是 `0` 或 `""`
  → `Models.kt` 用 `LenientStringSerializer` / `LenientProfileInfoSerializer` 兜底。
- Kotlin 块注释可嵌套：注释里写 `/api/common/` + `*` 会开出未闭合注释导致编译失败。
- 图片 URL 多为协议相对（`//image.lceda.cn/...`），统一走 `Fmt.url()`。

抓包/复现工具在 `_tmp/tools/`（不纳入版本库）：`launch-edge.ps1` 起带 CDP 的 Edge，
`capture.mjs` 记录 XHR，`drive.mjs` 导航页面。

## 6. 已知限制

- 手机号登录/短信接口在部分网络环境必须先过 WAF 安全验证 → 用「用浏览器登录」入口。
- 发布工程、编辑器、PCB 预览等重交互功能不在范围内（网页端依赖 EDA 画布）。
- 单元测试直接打线上接口，站点改版会红，属于预期。

## 7. 说明

本项目为个人学习用途的第三方客户端，与立创开源硬件平台无隶属关系；
所有内容与数据版权归原平台及原作者所有。
