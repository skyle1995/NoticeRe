# NoticeRe

利用 Root 权限，把安卓设备上的 **短信、未接来电、应用通知** 实时转发到 **Bark** 和/或 **Webhook** 的小工具。

- 短信、未接来电：通过 Root 直接读取系统私有 SQLite 数据库（`mmssms.db` / `calllog.db`），**无需**授予 `READ_SMS` / `READ_CALL_LOG` 等敏感权限，也无需设为默认短信应用。
- 通知：通过系统唯一的正规渠道 `NotificationListenerService` 捕获，需用户在「通知使用权」中授权。
- 支持按 **号码 / 关键词** 白名单过滤。
- 转发目标：**Bark**（可设级别/提示音）与 **Webhook**（通用 JSON / 钉钉 / 飞书 / Server酱，支持加签与自定义 Header）。
- 短信/来电自动带上**接收的 SIM 手机号**，区分卡 1 卡 2。
- 标题/正文支持**自定义模板**（`{type}` `{number}` `{content}` `{time}` `{sim}` 等占位符）。
- 内置**运行日志**，可查看每条消息的捕获与发送结果。
- 发送失败自动**落盘重发**（至少一次语义），进程存活由前台服务保证、支持开机自启。

## 界面

应用分 4 个 Tab：

1. **运行状态**：Root / 通知使用权 / 前台服务状态 + 运行日志。
2. **转发设置**：短信、未接来电、通知的开关与过滤规则。
3. **推送设置**：Bark 与 Webhook 的推送目标配置。
4. **模板设置**：自定义短信/未接来电/通知的消息模板（可重置为默认）。

## 环境要求

- 已 Root 的设备（Magisk / KernelSU / APatch 等，能授予应用 root 权限即可）
- Android 8.0（API 26）及以上
- 构建：Android Studio（JBR 21 / JDK 17+）

## 构建

用 Android Studio 打开本项目直接运行，或在命令行：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug      # 调试版
./gradlew :app:assembleRelease    # 发布版（ifunx 签名）
```

产物：
- 调试版：`app/build/outputs/apk/debug/app-debug.apk`
- 发布版：`app/build/outputs/apk/release/app-release.apk`

> 调试版内置了调试签名（`app/debug.keystore`，口令 `android`），无需依赖 `~/.android/debug.keystore`。

### 发布签名（release）

发布版用 `ifunx` 密钥签名，凭据**外置**、不写进 build 脚本：

- 优先读项目根目录 `signing.properties`（本地文件，已加入 `.gitignore`，不入 git），内容：
  ```properties
  KS_PATH=/Volumes/Device/MT2/keys/ifunx.jks
  KS_STORE_PASSWORD=...
  KS_KEY_PASSWORD=...
  ```
- 缺失时回退环境变量 `KS_PATH` / `KS_STORE_PASSWORD` / `KS_KEY_PASSWORD`。
- 凭据全缺时仅打印警告，release 产出**未签名** APK（构建不失败）。

换密钥后首次安装需先卸载（release 签名与 debug 签名不同）：

```bash
adb uninstall com.noticere.app
adb install app/build/outputs/apk/release/app-release.apk
```

## 使用步骤

1. 安装并打开应用，在「运行状态」点击 **检测 Root**，授予 Root 权限。
2. 配置转发目标：
   - **Bark**：填服务器（默认 `https://api.day.app`）、设备 Key，可选推送级别与提示音。
   - **Webhook**：选平台（通用 JSON / 钉钉 / 飞书 / Server酱），填地址；钉钉/飞书可填加签密钥，通用平台可填自定义 Header。
3. 按需配置过滤：短信（号码/关键词白名单）、未接来电（号码白名单）、通知（包名/关键词白名单），留空 = 全部转发。
4. 打开需要的转发开关（短信 / 未接来电 / 通知），点击 **保存并应用**。
5. 通知转发需点击 **授权通知使用权**，在系统设置中勾选 NoticeRe。
6. 建议在系统设置里允许自启动、关闭本应用的电池优化，以保证后台稳定。

## Webhook 报文格式

按所选平台不同：

- **通用 JSON**：`POST` 到配置 URL，`Content-Type: application/json`，可附加自定义 Header：
  ```json
  {
    "type": "sms | missed_call | notification | test",
    "source": "号码 或 应用名",
    "title": "标题",
    "body": "正文",
    "package": "包名（通知才有，其余为 null）",
    "sim": "接收的 SIM 手机号（通知为 null）",
    "timestamp": 1730000000000
  }
  ```
- **钉钉**：`{"msgtype":"text","text":{"content":"标题\n正文"}}`，密钥非空时自动按 `timestamp\nsecret` 加签并追加到 URL。
- **飞书**：`{"msg_type":"text","content":{"text":"标题\n正文"}}`，密钥非空时在 body 内附带 `timestamp`/`sign`。
- **Server酱**：`{"title":"标题","desp":"正文"}`。

## 工作原理

| 内容 | 捕获方式 | 说明 |
| --- | --- | --- |
| 短信 | Root 读 `mmssms.db` | 监听 `content://sms` 变化 + 15s 轮询兜底；root 拷贝 DB 快照后只读查询增量 |
| 未接来电 | Root 读 `calllog.db` | 监听 `content://call_log/calls` 变化 + 轮询兜底；过滤 `type = 3/5/6`（未接/拒接/拦截） |
| 通知 | `NotificationListenerService` | 系统回调，支持标题/正文/聊天多行文本，可按包名/关键词过滤 |

> Root 使用 libsu 的 `FLAG_MOUNT_MASTER`（`su --mount-master`），在全局挂载命名空间中读取 `/data/data` 下其它应用（短信/通话）的数据库。

首次运行会先建立“基线”（记录当前最大 id），避免把历史消息全部转发出去。

发送采用「先落盘、再发送」：消息写入 `pending_messages.json` 后立即触发发送，失败的条目在启动时与每 15s 定时循环中持续重发（至少一次语义，网络抖动下可能偶发重复）。
