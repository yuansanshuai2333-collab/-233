# 关于「换基础 mod」的评估结论

## 背景

使用中提过「可以试着去寻找其他的 mod 作为基础」。因此去 mcmod.cn 调研了候选，
并回顾了此前所有故障的根因，判断是否值得更换基础。

## 调研到的候选

| 模组 | 能力 | 是否支持 1.21.11 Fabric | 对本项目是否有帮助 |
|---|---|---|---|
| [Cloth Config API](https://www.mcmod.cn/class/2346.html) | 配置界面 API | 是 | 只覆盖「配置界面」，**对 HUD 渲染与游戏逻辑零帮助** |
| [GeckoLib](https://www.mcmod.cn/class/3232.html) | 模型/动画引擎 | 是（已在用） | 是 NeedsOfNature 的硬依赖，无可更换空间 |
| [Mod Menu](https://www.mcmod.cn/class/1675.html) | 模组列表与配置入口 | 是（已在用） | 已接入 |

## 故障根因回顾（全部在自身代码，非底层 API）

| 故障 | 根因 | 归属 |
|---|---|---|
| 启动崩溃 | `KeyBinding.Category` 用同一 id 注册两次 | 自身代码 |
| 全部内容不可见 | 颜色写成 6 位十六进制，ARGB 下 alpha=0（全透明） | 自身代码 |
| 检测器状态不稳 | `Item#use` 在两端各切一次状态，两份副本互翻 | 自身代码 |
| 标签位置全错 | 手工构造视图矩阵符号错，投影后 w 为负 | 自身代码 |
| 物品紫黑格 | 缺 1.21 的 `assets/<ns>/items/` 模型定义 | 自身代码 |
| 配置界面滚动后拖不动 | 滚动时 `clearChildren()` 重建控件，重建后不响应鼠标 | 自身代码 |
| 按几次才触发 | 只取单个候选，且闸门时序错误挡住玩家自己的请求 | 自身代码 |
| HUD 数字画成方框 | 该 HUD 绘制路径取不到数字字形（换到行动栏即正常） | 自身代码 / 渲染路径选择 |
| 好友生物仍强上 | 闸门条件写成了「好感 ≥ friendThreshold」，好感 52 的生物直接穿过去 | 自身代码 |

**结论**：底层完全可用 —— 370 条动画定义加载、自主交配结算
（`mating recorded ... affinity=100`）、动画启动，均在实机日志中验证通过。
问题集中在自身代码，且已逐个定位修复。

## 决策：保持现有架构，不更换基础

理由：

1. Cloth Config **只解决配置界面**，而该界面在修复「滚动重建控件」后已可使用；
   更换需要用户额外安装前置，并重做 + 重测整个配置系统，收益与成本不匹配。
2. 最近三个真正的痛点（HUD 字形、读数不显示、闸门未拦截）**都发生在 HUD 渲染层与游戏逻辑层**，
   配置库完全够不着。
3. 换基础意味着把已经跑通的部分（动画联动、好感持久化、求欢闸门、动画选择）全部重写，
   且不解决任何已定位的问题。

## 若将来确实需要 Cloth Config

接入点很集中，改动可控：

- `build.gradle` 增加 `modCompileOnly` / `modRuntimeOnly` 的 Cloth Config 依赖（1.21.11 Fabric）
- 新增一个 `ConfigScreenProvider`，把 `AffinityConfig` 的字段映射成 Cloth 的条目
- 保留现有 `AffinityConfigScreen` 作为未安装 Cloth Config 时的降级方案
- `ModMenuIntegration` 里按是否加载了 Cloth Config 二选一
