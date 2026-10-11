# Natural Relationships 1.0.44

## 友情之证食用功能

- 现有 friendship_token 物品新增食用能力，物品 ID、纹理、堆叠上限 64 和创造栏位置保持不变。
- 每个恢复 5 点饥饿值（2.5 格），额外饱和度为 0。恢复值不超过原版 20 点上限。
- 使用原版食物的食用时长、声音及消耗机制；生存模式每次消耗一个，吃满后不能继续食用。
- 此次未增加新物品，按 VERSIONING.md 使用测试修订版本 1.0.44。

## 后续扩展位置

src/main/java/com/nonid/affinity/item/FriendshipTokenItem.java 集中定义 NUTRITION、SATURATION_MODIFIER、FOOD 和 CONSUMABLE。
后续可在该类调整食用属性、使用 ConsumableComponent 的消费效果或加入物品专属行为，无需更改物品 ID 或重写注册流程。当前不添加未定义的状态效果。

## 验证

编译与打包成功；4 项真实服务器食用检查通过，涵盖可食用判定、饥饿恢复、堆叠消耗、上限及吃满禁用。此次未启动图形客户端检查食用动画。
发行包资源完整，不含测试探针。最终服务器回归：36 项通过，0 失败，服务器正常退出。日志：.codex-temp/token-food-1-0-44/console.log。