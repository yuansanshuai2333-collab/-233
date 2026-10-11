# 测试用内容包（dev test pack）

这个目录是**开发自测用的最小 NeedsOfNature 内容包**，用途只有一个：
在没有官方内容包的情况下，让「按键 → 匹配 → 启动动画 → 好感结算」这条链路能够跑起来。

> ⚠️ 里面的模型与动画都是**占位资源**（一个不可见的根骨骼 + 无关键帧动画），
> 所以游戏里不会看到正常的交配动作。它的作用是把**逻辑链路**验证通，不是给你日常游玩用的。
> 日常游玩请使用 NeedsOfNature 的官方内容包。

## 内容

```
testpack/
├─ pack.mcmeta                                  包元数据（含 animationframework 段）
├─ data/noncreatureaffinity/afw_animdefs/affinity_test/
│  ├─ wolf_player.json                          玩家 + 狼
│  └─ pig_player.json                           玩家 + 猪
└─ assets/noncreatureaffinity/geckolib/
   ├─ models/testpack/entity/entity/{wolf,pig,player}.geo.json    占位模型
   └─ animations/afw/affinity_test/*.animation.json               占位动画
```

两个定义都是「**玩家(passive) + 生物(active, injector V)**」的形状，
正是本 mod 的交配按键所要求的结构。

## 关键路径规则（踩过坑，记下来）

| 内容 | 必须放在 | 原因 |
|---|---|---|
| 动画定义 `.json` | **`data/<ns>/afw_animdefs/`** | NEEDSOFNATURE 用服务端数据资源管理器（`SERVER_DATA`）读取 |
| geckolib 模型/动画/贴图 | **`assets/<ns>/geckolib/...`** | 客户端资源侧 |
| `pack.mcmeta` | 包根（两侧共用同一份） | 数据包栈里需要它才能读到 `animationframework` 段 |

**只放 `assets/` 会导致动画定义永远不加载**（这是最容易犯的错）。

## 怎么用

把整个 `testpack` 目录（或它的 zip）复制到游戏目录下的 `needsofnature/`：

```
<游戏目录>/needsofnature/0001_affinity_testpack/
```

- 文件名前缀 `NNNN_` 用于控制多个内容包的加载优先级（数字越小越优先）。
- 开发环境对应目录：`I:\DSH\work\noncreatureaffinity\run\needsofnature\`（已投放好）。

## 如何确认它生效

启动后看日志，应出现：

```
[NoN Animation] Loaded 2 animation definition(s) from datapacks
[NoN Affinity] 2 animation definition(s) loaded: noncreatureaffinity:affinity_test/pig_player, noncreatureaffinity:affinity_test/wolf_player
```

第二行是本 mod 的启动自检。如果只出现 `Loaded 0 animation definition(s)`，
说明包没放对位置（必须是 `data/` 而不是只有 `assets/`）。

游戏内还可以用：

```
/nonaffinity diagnose
```

它会报告：NON 是否加载、定义条数、是否存在「玩家+生物」形状的定义、附近可交互目标及其好感/性欲。

## 换成你自己的动画

1. 改 `pack.mcmeta` 里的 `animationframework.id`（**必须全局唯一**，否则模型会互相抢）；
2. 在 `data/<你的ns>/afw_animdefs/` 下写你的定义（字段白名单见
   `I:\DSH\work\needsofnature\docs\10-animation-framework.md`）；
3. 在 `assets/<你的ns>/geckolib/` 下放真实模型与动画。
