自然关系 1.4.1：重逢之愿缺失图标修复
原因：Minecraft 1.21.11 的 Item.Settings.getValidatedComponents 使用注册ID覆盖预设 ITEM_MODEL，默认物品实际引用 noncreatureaffinity:reunion_wish，但原包缺少对应 items/reunion_wish.json。
修复：补充该默认模型入口，指向现有未绑定图形。显式未绑定、充能、耗尽模型保持原样。已有默认物品随资源加载恢复，不需重新制作。
验证：发布构建及releaseGate通过；553项单元测试0失败0错误；实际客户端默认、显式未绑定、充能、耗尽四个入口资源存在，实际渲染截图确认均正常。
本轮仅修复资源入口和升级补丁版本，未更改复活及绑定玩法。未重新运行完整玩法回归。
替换旧版自然关系JAR后重启游戏。不要同时安装两个版本。
