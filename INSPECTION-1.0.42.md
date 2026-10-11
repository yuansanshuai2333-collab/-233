# Natural Relationships 1.0.42 检查报告 / Inspection report

检查日期：2026-09-22。

## 结果 / Results

- 显示名称 / Display name: Natural Relationships
- 发行版本 / Release version: 1.0.42
- JAR 元数据版本 / Metadata version: 1.0.42+1.21.11
- 内部 ID / Internal ID: noncreatureaffinity（保持不变 / unchanged）
- 构建成功 / Build successful.
- 本次全套现有自动测试 / Existing automated suite this run: 249 tests, 33 classes, 0 failures, 0 errors, 0 skipped.
- 与 rc.42 对比，117 个代码类字节一致；改动为显示名称、语言资源和版本元数据。117 compiled classes are byte-identical to rc.42; branding, localization resources and version metadata changed.
- 正式 JAR 不含开发测试探针 / No development probe entries in the release JAR.
- rc.42 此前的独立服务器检查：32 通过、0 失败；本次未重复运行，不能表述为 1.0.42 新启动会话。Previous rc.42 isolated server checks: 32 passed, 0 failed; not rerun as a new 1.0.42 session.
- 已检查本地 NeedsOfNature、GeckoLib、Trinkets JAR 的依赖声明，区分了基础必需项与可选集成。Local dependency metadata was inspected; required base components and optional integrations are distinguished in both guides.

## 注意事项 / Findings and limitations

- 本次未发现阻止编译或自动测试通过的问题。This inspection found no build or automated-test failures.
- Gradle 提示构建脚本使用了未来 Gradle 10 不兼容的弃用功能；当前 Gradle 9.2.0 构建成功，不是游戏运行报错。Gradle reported deprecated features relevant to a future Gradle 10 upgrade; the current Gradle 9.2.0 build succeeds.
- 名称升级不是全生物、全整合包或长期稳定性认证。The version change is not certification of every creature, modpack, or long-running session.
- 仍需完整客户端重进存档、骑乘切换、复杂地形、跨维度和多伙伴长时实测。Client reload, riding, complex terrain, dimension transitions and extended multi-companion sessions still need practical validation.
- 未修改玩家实例或存档；只生成工程中的发行包。The player's instance and saves were not modified.

## 文件 / Files

- build/libs/naturalrelationship-1.0.42+1.21.11.jar
- FEATURES-zh-CN.md
- FEATURES-en-US.md

SHA256: 52DBF23841E4AB99CD1BD8C5948B953CF087BAE98E179744BD9B61A3F5B281B8