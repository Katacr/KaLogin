# KaLogin 交接文件索引

本目录记录 KaLogin 插件的模块现状、关键决策、已知坑与待办,供后续会话无缝接手。

- 项目路径:`/home/Plugins/KaLogin`
- 当前版本:`1.5.1`(`build.gradle.kts` / `src/main/resources/plugin.yml`)
- 最后更新:`2026-09-19`

## 交接文件清单

| 文件 | 主题 | 覆盖模块 | 最后更新 |
|---|---|---|---|
| `overview.md` | 项目概览 | 定位、技术栈、构建、启动流程、目录结构、模块地图 | 2026-09-19 |
| `auth.md` | 认证核心 | 双认证模式、登录/注册/自动登录流程、数据库、密码、线程与 Folia | 2026-09-19 |
| `ui-dialog.md` | UI 与对话框 | Dialog 平台抽象、Paper/Spigot 实现、Body 渲染、消息、配置升级 | 2026-09-19 |
| `features.md` | 功能模块 | 邮箱绑定/找回、欢迎条款、事件动作、反作弊、PAPI、Geyser、更新检查 | 2026-09-19 |
| `proxy.md` | 群组服务器跨服登录 | 通过 KaProxy 共享登录会话（协议、接入点、决策、待办） | 2026-09-19 |
| `lastseen.md` | 上次下线位置 | 通过共享 MySQL 记录并恢复上次下线子服与坐标（坐标后端写库、server 由代理写） | 2026-09-20 |
| `commands-api.md` | 命令与开放 API | 全部命令/权限、KaLoginAPI/KaLoginListener/事件类 | 2026-09-19 |
| `docs.md` | 文档体系与修复记录 | docs/ 与 docs-en/ 结构、本次修复的文档错误、已知缺口 | 2026-09-19 |

## 调研报告

| 文件 | 主题 | 最后更新 |
|---|---|---|
| `research/initial-survey.md` | 项目初步调研(架构与实现全景) | 2026-09-19 |

## 快速结论

- 插件提供**双认证模式**:自建 KaLogin 模式与 AuthMe 代理模式,由 `config.yml` 的 `use-AuthMe` 切换。
- 界面基于 **Minecraft 原生 Dialog**(Paper 1.21.7+ / Spigot 1.21.6+),通过反射在运行时选择平台实现。
- 数据库访问**全异步**,统一调度器兼容 Folia。
- 当前无 `TODO/FIXME` 标记,但存在若干已知隐患,详见各模块文件"当前待办"。
