# 崩溃修复记录：KeyBinding.Category 重复注册

## 现象

安装 v0.1.0 后**客户端一启动就崩溃**，连主菜单都进不去。

崩溃报告关键行：

```
Description: Initializing game

java.lang.RuntimeException: Could not execute entrypoint stage 'client' due to errors,
  provided by 'noncreatureaffinity' at 'com.nonid.affinity.client.NonCreatureAffinityClient'!
Caused by: java.lang.IllegalArgumentException: Category 'noncreatureaffinity:main' is already registered.
	at net.minecraft.class_304$class_11900.method_74698(class_304.java:43)
	at com.nonid.affinity.client.NonCreatureAffinityClient.onInitializeClient(NonCreatureAffinityClient.java:43)
```

## 原因

`KeyBinding.Category` 是**全局唯一注册**的：同一个 `Identifier` 只能 `create` 一次，
第二次调用会抛 `IllegalArgumentException`。

我加「打开配置界面」按键时，是这样写的：

```java
// 求欢键
new KeyBinding("key.noncreatureaffinity.request_mating", InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_F,
        KeyBinding.Category.create(Identifier.of(MOD_ID, "main")));   // 第一次：OK

// 配置键
new KeyBinding("key.noncreatureaffinity.open_config", InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_K,
        KeyBinding.Category.create(Identifier.of(MOD_ID, "main")));   // 第二次：抛异常 → 崩溃
```

两次都调用了 `create(...)`，于是启动阶段直接抛异常。这类错误属于**注册期致命错误**，
没有降级空间，所以表现为完全无法启动。

## 修复

把分类提成**只初始化一次的静态常量**，两个按键共用：

```java
/** Key binding category. Created exactly once: the category registry rejects duplicates. */
private static final KeyBinding.Category CATEGORY =
        KeyBinding.Category.create(Identifier.of(NonCreatureAffinity.MOD_ID, "main"));
```

## 验证方式（这次是真的跑起来了）

用 Loom 的开发客户端实际启动，并借助玩家提供的存档直接进入世界：

```
[NoN Affinity] registered item noncreatureaffinity:affinity_detector
[NoN Affinity] NeedsOfNature detected; animation integration enabled.
[NoN Affinity] initialized (NeedsOfNature present: true)
[NoN Animation] Loaded 370 animation definition(s) from datapacks
Time elapsed: 2379 ms
```

要点：

- **不再出现** `Category ... already registered`
- 客户端完整启动（纹理图集、声音引擎均正常），并成功进入世界
- 内容包 370 条动画定义全部加载

字节码层面也做了核对：`javap -c` 显示 `Category.create` 只出现在 `static {}` 初始化块里，全类仅一次调用。

## 同时修掉的另一个问题：测试包 pack.mcmeta 格式

客户端日志中出现：

```
Couldn't load needsofnature/client/9999_0001_affinity_testpack pack metadata:
  Pack declares support for version newer than 64, but is missing mandatory fields min_format and max_format
```

1.21.9+ 起，`pack_format` 声明为 64 以上时必须同时提供 `min_format` / `max_format`。
我原先只写了 `pack_format: 75`。参照官方默认包改正为：

```json
"pack": {
  "pack_format": 64,
  "supported_formats": [64, 81],
  "min_format": 64,
  "max_format": 81,
  "description": "..."
}
```

（注：这个测试包只是我用于自测的，**不在你游戏目录里**，所以它与你遇到的崩溃无关。）

## 教训

凡是「注册表 + 唯一 id」的 API，都要确认调用次数。同类需要留意的还有
`PayloadTypeRegistry.register`、`AttachmentRegistry.create`、`Registry.register` 等，
本项目这些都在启动期单次执行，本次已一并复核。
