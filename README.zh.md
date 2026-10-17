# 3X-UI Manager

通过 REST API 管理 [3x-ui](https://github.com/MHSanaei/3x-ui) 面板的原生移动客户端——随时随地查看仪表盘、入站、客户端（支持二维码分享）、节点和 Xray 配置。**多实例：连接多个面板，并在同一个应用内随时切换。**

> ✅ **兼容至 3x-ui v3.10.0 的面板。** 应用使用面板稳定的 REST API，因此面板发布新版本**并不**需要新版应用——面板升级后无需等待应用更新（少数例外会在应用的发行说明中注明）。面板 **3.10.0** 就是例外之一：创建 Shadowsocks 入站和 MASQUE 支持需要应用 **v0.15.1** 或更高版本。

本分支（`main`）为 **Android** 应用（Kotlin + Jetpack Compose），直接在仓库根目录构建。

[![Get it on F-Droid](https://fdroid.gitlab.io/artwork/badge/get-it-on.png)](https://f-droid.org/packages/net.yukh.xui)

![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg) ![Platform: Android 7+](https://img.shields.io/badge/Android-7%2B-3DDC84?logo=android&logoColor=white) ![Made with Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)

🇬🇧 [English version](README.md) · 🇷🇺 [Версия на русском](README.ru.md) · 📖 [用户手册](docs/3X-UI-MANAGER.en.md) · 📝 [更新日志](CHANGELOG.md)

---

## 下载

- **[F-Droid](https://f-droid.org/packages/net.yukh.xui)** — 推荐；可复现构建，通过 F-Droid 客户端自动更新。
- **[GitHub Releases](https://github.com/yukh975/3X-UI-Manager/releases/latest)** — 签名的 APK（`standard` 变体，支持应用内更新）+ 可复现的 `fdroid.apk`。
- **[Obtainium](https://github.com/ImranR98/Obtainium)** — 直接从 GitHub Releases 安装并自动更新：通过仓库 URL `https://github.com/yukh975/3X-UI-Manager` 添加应用（按 `3x-ui-manager` 过滤 APK 以选择 `standard` 变体）。
- **iOS** — 用于侧载（AltStore / Sideloadly）的未签名 `.ipa`，随每个版本附上。

需要面板运行 **3x-ui v3.3.0 或更高版本**（API 令牌认证）；已测试至 **v3.10.0**（参见顶部的兼容性说明）。

---

## 文档

完整的用户手册，基于本应用源码编写——安装、使用 API 令牌连接、每个界面、告警、隐私和故障排除。

| 手册 | 语言 | 格式 |
| --- | --- | --- |
| **[3X-UI-MANAGER.en.md](docs/3X-UI-MANAGER.en.md)** · [PDF](docs/pdf/3X-UI-MANAGER.en.pdf) | 🇬🇧 English | Markdown + PDF |
| **[3X-UI-MANAGER.ru.md](docs/3X-UI-MANAGER.ru.md)** · [PDF](docs/pdf/3X-UI-MANAGER.ru.pdf) | 🇷🇺 Русский | Markdown + PDF |
| **[3X-UI-MANAGER.zh.md](docs/3X-UI-MANAGER.zh.md)** · [PDF](docs/pdf/3X-UI-MANAGER.zh.pdf) | 🇨🇳 中文（简体） | Markdown + PDF |

---

## 仓库结构

| 分支 | 内容 |
| --- | --- |
| **`main`** | **Android** 应用（本分支）。Gradle 项目位于仓库根目录（`app/`、`build.gradle.kts` 等）。 |
| **`apple`** | **Apple / iOS** 应用——基于 Kotlin Multiplatform + Compose-MP。 |

3X-UI 面板的**用户手册**（俄文为准 + 英文）位于独立仓库 **3X-UI-Manual**——与管理应用无关。

上游项目位于 [MHSanaei/3x-ui](https://github.com/MHSanaei/3x-ui)；另有一份私有的**纯镜像**作为只读参考，用于对比面板升级带来的变化。本仓库仅为管理应用——不包含面板的任何 Go 源码。

---

## 功能

### 连接
- **仅 API 令牌**（Bearer）——粘贴面板中的令牌。需要面板 **v3.3.0 或更高版本**。
- **自签名 TLS** 开关，用于使用自签名证书的面板。
- **订阅基础 URL** 字段（参见[订阅](#订阅)）。
- 令牌加密存储（`EncryptedSharedPreferences`，AES-256，密钥存于 Android Keystore）。
- **多面板（配置文件）。** 保存多个面板，并通过顶栏的 **⇄** 按钮切换——切换活动面板、添加另一个或移除一个。

### 仪表盘
- 实时服务器状态，每 3 秒轮询：**Xray** 运行/停止状态 + 版本，一键**重启 Xray**（需确认）。
- **CPU**、**内存**、**磁盘**（已用/总量）；**在线**客户端数、**网络 ↑/↓** 每秒、**TCP/UDP** 连接数、**平均负载**、**运行时间**、公网 IP。
- 点击任意指标卡片（CPU、内存、磁盘、负载、网络、连接）→ 显示**历史图表**，带间隔下拉框（实时至 5 小时）。
- **本月流量**——主面板自身入站的当月代理（VPN）流量（含周期起始日期）。
- 点击**在线** → 当前已连接的客户端列表，显示各自所属的入站（每 3 秒刷新）。
- 底部有 **3x-ui 面板版本**卡片；如有可用更新，**更新**按钮（需确认）会触发面板自更新。
- **Geo 数据库**——逐个重新下载面板内置的 Geo 规则文件（`geoip.dat`、`geosite.dat` 及 RU/IR 变体）；每次更新后面板会重启 Xray（先确认）。

### 入站
- 列表含每行的**启用/禁用**开关、流量用量 vs 配额、客户端数量。
- 使用结构化编辑器**创建 / 编辑 / 删除**（常见场景无需原始 JSON）：
  - 基本信息：备注、端口、监听 IP、协议、启用状态。
  - 限制：流量限制（GB）、流量重置计划、到期（日期选择器）。
  - **传输**：网络（tcp/ws/grpc/httpupgrade/xhttp/kcp）及相关字段（ws/httpupgrade 的 path+host、grpc 服务名）。
  - **安全**：none / TLS（SNI）/ Reality（dest、服务器名称、Short ID、指纹、公钥/私钥）。
  - **嗅探**：启用 + destOverride 复选框。
  - **高级（JSON）**：协议 `settings`（decryption、fallbacks 等）。客户端列表**不**在此显示——客户端在“客户端”标签页管理，保存入站时绝不会被改动。

### 客户端
- 列表含在线状态圆点、流量、到期时间、最近在线。
- **按邮箱搜索**——输入名称/地址的一部分即可过滤列表。
- **创建 / 编辑 / 删除**（邮箱/名称可编辑，支持重命名）：入站归属（多选；保存时绑定/解绑）、流量限制、IP 限制、重置周期、到期、Telegram ID、分组、备注。
- **分享面板**（点击客户端）：**订阅**分区（二维码 + 链接）和可展开的**连接**分区，每个服务器链接都有自己的二维码 + 复制/分享。

### 节点
- 管理远程面板（多面板）：在线状态、CPU/内存/延迟、入站/客户端数量、每个节点的**本月流量**。
- **添加 / 编辑 / 删除**：名称、地址、端口、协议、基础路径、API 令牌、TLS 验证模式、允许私有地址。

### Xray 配置（⋮ 菜单）
针对面板 Xray 配置的结构化编辑器——每次保存都会回写**整个**配置，保留同级和未知键。字段集与面板自身的表单一致。（需要面板 **v3.3.0+**。）
- **出站**——列表（添加 / 编辑 / 删除 / 排序）+ 各协议表单（vless、vmess、trojan、shadowsocks、socks、http、freedom、blackhole、wireguard），含传输 + TLS/REALITY；**从 `vless://` 链接导入**。
- **路由**——规则（来源 / 目标 / 入站 → 出站或负载均衡，可排序）+ 路由策略 + 路由测试器。
- **负载均衡**——负载均衡器（策略 / 选择器 / 回退）+ 其驱动的观测器探测设置（独立保存按钮）。
- **DNS**——启用、DNS 级选项、服务器（裸字符串或完整对象）、FakeDNS 池。
- **常规 / 日志**——日志级别、路由策略、出站测试 URL、流量统计开关。
- **Xray 配置（原始）**——完整配置的 JSON 形式，用于覆盖表单未包含的内容（高级 xHTTP、hysteria、reverse 等）。

### 备份 / 恢复（⋮ 菜单）
通过系统文件选择器**备份**面板的整个数据库（设置、入站、客户端**以及** Xray 配置）到文件，并可从中**恢复**面板。与数据库引擎无关——面板将 SQLite 保存为 `x-ui.db`，将 PostgreSQL 保存为 `x-ui.dump`，两者均可导回。恢复前需确认，并会重启 Xray（连接短暂中断）。API 令牌同样可用。

### 面板管理（⋮ 菜单）
- **管理员账户**——更改面板登录用户名和密码（需输入当前凭据确认）。
- **API 令牌**——列出、创建（明文仅显示一次供复制）、启用/禁用、删除。
- **重启面板**——重启面板服务（需确认；应用会在几秒后重新连接）。

### 其他
- **应用内更新**：应用在启动时以及通过**设置**（⋮ 菜单）中的**检查更新**按钮检查自身的 [GitLab 发行版](#ci--releases)。有新版本时会显示发行说明，一键下载 APK 并交给系统安装器——原地更新（发行版使用同一密钥签名）。无需账户或令牌；发行版公开可用。
- **应用锁**：**设置**（⋮ 菜单）中可选的 4–8 位数字密码（+ 生物识别解锁）。仅保护**已登录的面板界面**——连接状态下退到后台，或启动时恢复已保存的会话，才会重新锁定。在**连接**页面（未登录）或刚手动登录后不会要求输入。
- **语言**：英语（默认）、俄语或中文，可在**设置**（⋮ 菜单）中切换——无需重启。
- 每次**保存**和**删除**都有确认对话框，避免误操作。

---

## 认证

应用**仅**使用 **API 令牌**（Bearer）认证，面向面板 **v3.4.x 及更高版本**（已测试至 **v3.10.0**）。没有登录名/密码模式。

> **面板 v3.3.x 用户：** 当前应用版本针对 **v3.4.x** 优化。使用 **v3.3.x** 面板的用户请使用最后一个兼容版本——**v0.3.23**。

在面板 **v3.3.0** 上，所有管理 API 都移到了 `/panel/api/*` 下，Bearer 令牌以面板首位管理员的身份通过认证。因此一个令牌即可覆盖**应用的每一项功能**——仪表盘、入站、客户端、节点、**Xray 配置编辑器**、**设置**、**订阅链接**和**备份 / 恢复**。3x-ui 令牌拥有**完全管理员权限**（没有只读或限定范围的令牌），请像保护密码一样保护它。

在面板的 **Settings → Security → API Token** 中创建令牌。

**为什么使用令牌（而不是登录）：** 令牌**不会按定时过期**，因此不会在会话中途掉线，也从不重复要求 2FA——不同于登录会话，后者在 `sessionMaxAge`（默认 **360 分钟 / 6 小时**）后失效。v3.3.0 之前的面板仅向已登录会话开放设置和 Xray 配置，因此以前支持登录；在 v3.3.0+ 上令牌即可访问全部功能，所以应用现在仅支持令牌。

唯一的例外：如果令牌之后在面板中被**禁用或重新创建**，其请求会以 `401` 失败。此时应用会**把你带回连接页面**，并显示明确的“你的 API 令牌已失效”提示，且预填原有信息——输入有效令牌重新连接即可。实际上很少发生，因为令牌不会超时。

---

## 订阅

**订阅链接**是一个 URL，向客户端应用提供某个用户的*全部*配置并保持更新。在 3x-ui 中它并不被存储——而是这样拼出来的：

```
<subscription server base>/<client subId>
```

`subId` 对每个客户端都是唯一的（应用已掌握），因此每个客户端都有自己的链接。唯一共享的部分是**订阅服务器基础地址**，它是面板设置。

- **自动（面板 v3.3.0+）：** 应用会通过令牌自动从面板读取基础地址——因此每个客户端的订阅二维码和链接都无需配置即可显示。
- **手动覆盖：** 当自动读取不适用时，在连接页面的**订阅基础 URL** 字段中**一次性**设置基础地址——例如订阅前有**反向代理**、其公开 URI 与面板存储的不同。可填写：
  - 你的**反向代理 URI**（例如 `https://sub.example.com/`），或
  - 面板中显示的**订阅 URL**。

  随后应用按 `base + subId` 拼接每个客户端的链接。

**在面板中的位置：** *Settings → Subscription*——启用状态、端口、路径和域名。完整的基础地址通常形如 `https://your-host:2096/sub/`（端口/路径取决于你的配置）。如果前面有反向代理，请改用其公开 URI。

---

## 贡献者

- [@venti1112](https://github.com/venti1112) — 应用、README 及用户手册的简体中文翻译（[#9](https://github.com/yukh975/3X-UI-Manager/pull/9)、[#10](https://github.com/yukh975/3X-UI-Manager/pull/10)）

---

© 2026 Yuriy Khachaturian ([yukh.net](https://yukh.net))
