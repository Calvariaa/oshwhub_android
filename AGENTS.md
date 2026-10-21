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

## 模块结构

| 路径 | 作用 |
| --- | --- |
| `data/net/ApiClient.kt` | OkHttp + 持久化 Cookie + `{success,code,result}` 信封解析、统一 UA/Referer |
| `data/OshApi.kt` | 与站点 web 一一对应的接口层（全部端点集中在这里） |
| `data/AuthRepository.kt` | passport.jlc.com 的 CAS 登录（SM2 加密密码 / 短信验证码） |
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

## 代码约定 / 坑

- Kotlin 块注释**可嵌套**：注释里写 `/api/common/*` 会开启一个未闭合注释，编译报
  “Unclosed comment”。写接口通配时用 `/api/common/` 或反引号包裹。
- 图片 URL 多为协议相对（`//image.lceda.cn/...`），统一走 `Fmt.url()` 规范化。
- 主题走 `ui/theme/OshTheme`（支持动态取色、纯黑、跟随系统），窗口宽度类随
  `calculateWindowSizeClass` 传进 `LocalWindowWidth`。
- 新增页面：`OshApi` 加接口 → `data/model` 加 DTO → `ui/<feature>` 写
  `XxxViewModel(StateFlow)+XxxScreen` → 在 `AppRoot.kt` 注册 `Routes` 与 `composable`。
- 抓包/验证新接口优先用 `_tmp/tools`：先 `launch-edge.ps1` 起 CDP，再
  `node _tmp/tools/capture.mjs`（后台）+ `node _tmp/tools/drive.mjs <url>` 导航。
