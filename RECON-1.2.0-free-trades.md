# 1.2.0 挚友限次零价交易：阶段 A 勘察 + 阶段 B 状态机

> 用途：交付**阶段 A（只读勘察）**与**阶段 B（额度状态机与测试）**的成果，供评审确认后再进入 C–F。
> 基线：1.1.1（普通折扣的“快照优先”边界**原样保留**，本文件不改变它）。
> 状态：**功能尚未完整实现**。当前仓库里落地的只有阶段 B 的纯逻辑类与其单元测试；交易接入、UI、事件接线、
> 存档字段迁移**都还没有写**。本文件不代表功能可用。

---

## 一、阶段 A 勘察结论（全部来自本地 1.21.11 映射 JAR 反汇编或本项目源码）

### 1. 挚友的实际判定（本项目侧）

- 关系数据：`CreatureAffinityData`（挂在实体上的 Fabric Attachment
  `noncreatureaffinity:affinity`），每个玩家一条 `PlayerAffinity`。
- 判定入口：`AffinityService.isFriend(creature, player, config)`；`PlayerAffinity.isBefriended()` 是**缓存标记**，
  由 `AffinityService.checkFriendThreshold(...)` 在好感达到 `friendThreshold` 时置位。
- **风险（方案第 3 条要求核对）**：`befriended` 缓存与当前好感**可能不一致**（好感可以被下调，而该标记在
  关系存续期间不会被清除）。因此零价资格**不能只看 `isBefriended()`**，应以“当前好感 ≥ friendThreshold”
  为准，并让 UI 显示的口径与之一致。这一点必须在阶段 C 落地时统一到一个判定函数里。

### 2. 存档入口

- 关系数据：`AffinityAttachments.AFFINITY` → `NbtCompoundCodec.of(toNbt, fromNbt)`，
  **`.copyOnDeath()`**。
- 这两个事实直接决定方案第 3 条的“村民→僵尸村民→村民转换”问题：
  - 转换会新建实体；**附着数据是否随同转移取决于 Fabric Attachment 的拷贝行为**，
    本地反汇编**尚未确认**转换路径是否触发拷贝（见“未决项”）。
  - 保守做法：把**每对关系的额度**存在 `PlayerAffinity` 里（随关系一起走），
    而不是单独存一份“村民身份”记录；这样只要关系跟着个体走，额度就跟着走。

### 3. 交易输出调用链（已确认到方法级）

> **⚠️ 本节第一版把 `onTakeItem` 的控制流写错了，已按复核意见更正。**
> 当时的说法是“调用方忽略 `depleteBuyItems` 的返回值”——**错误**。真实字节码是：
> `if (!depleteBuyItems(in1,in2) && !depleteBuyItems(in2,in1)) skip;`
> 也就是说 `merchant.trade(...)`、统计与两个输入槽的回写都在**成功分支**里；
> 两次都失败时这些步骤**全部跳过**（只剩末尾的商人经验更新，它在条件之外）。
> 因此**绝不能**把“`onTakeItem` 执行过”或“`depleteBuyItems` 调用过”当成成交成功。

`TradeOutputSlot.onTakeItem(player, stack)` 的实际控制流（字节码偏移 0–119）：

```
onCrafted(stack);
TradeOffer offer = merchantInventory.getTradeOffer();
if (offer == null) return;                                  // ← 没有报价就直接返回
ItemStack in1 = merchantInventory.getStack(0);
ItemStack in2 = merchantInventory.getStack(1);
if (offer.depleteBuyItems(in1, in2) || offer.depleteBuyItems(in2, in1)) {
    merchant.trade(offer);                                  // uses++ / 库存 / 配方推进
    player.incrementStat(Stats.TRADED_WITH_VILLAGER);
    merchantInventory.setStack(0, in1);                     // 回写被扣减后的堆叠
    merchantInventory.setStack(1, in2);
}
merchant.setExperienceFromServer(merchant.getExperience() + offer.getMerchantExperience());  // 条件之外
```

**由此得到的三条工程结论：**

1. 成交成功的判定点是**那个 if 分支**，不是“方法被调用过”。真实风险是**输出物品的提取与成功提交可能不同步**：
   `takeStack`（提取）与 `onTakeItem`（提交）的**先后必须查清**，包括 `quickMove` 批量循环里的时序
   与第三份之后输出槽何时刷新，再决定在哪里做最终校验。
   **更正（两处）**：第一版写“末端拒绝时 `merchant.trade` 已经执行”不总成立——当两次 `depleteBuyItems`
   都失败时那个调用会被跳过；另有一处写“取件发生在 `onTakeItem` 之后”，那是**没有依据的统一断言**，已删除。
2. 免费交易若要让 `depleteBuyItems` 返回 true 又不扣料，必须**改这两个方法的语义**（在免费条件下放行且不 `decrement`），
   或者把整段替换为“由本模组决定是否扣料”的等价实现。
3. 该分支里**没有**“检查输入材料是否足够”的独立判断——材料是否足够只体现在 `depleteBuyItems` 里，
   这正是它必须被改造的原因。

相关的已确认签名：

| 类 | 方法 | 说明 |
|---|---|---|
| `TradeOffer` | `matchesBuyItems(ItemStack, ItemStack)` | `TradedItem.matches(stack) && stack.getCount() >= getFirstBuyItemCount(...)`；**空槽必然 false** |
| `TradeOffer` | `depleteBuyItems(ItemStack, ItemStack)` | 先 `matchesBuyItems`，再对**槽内真实堆叠** `decrement(显示数量)`；返回值决定成交是否继续 |
| `TradeOffer` | `getUses()` / `use()` / `isDisabled()` | `uses >= maxUses` 即缺货；`use()` 只 `uses++` |
| `TradeOffer` | `getDisplayedFirstBuyItem()` / `getOriginalFirstBuyItem()` / `getSpecialPrice()` / `increaseSpecialPrice(int)` / `getDemandBonus()` | 价格入口；`getDemandBonus()` 返回的是**原始需求值**，不是价格加成（见第四节） |
| `MerchantInventory` | `updateOffers()` | 空槽提前返回（见下） |
| `MerchantScreenHandler` | `switchTo(int)` / `onContentChanged(Inventory)` / `canInsertIntoSlot(...)` / `quickMove(...)` | 界面侧；阶段 C/D 仍需补全 `quickMove` 与输出刷新的完整路径 |

**价格算术（已核对，与方案的第四节一致）**：
`原始数量 = base + max(0, floor(base * 需求 * priceMultiplier)) + specialPrice`，
原版随后钳制到 `1..物品最大堆叠`。`specialPrice` 里已含声望/治愈与现有自然关系优惠，
**不要再扣一次治愈或英雄**；需求是独立项且**必须带上 priceMultiplier**（见第二阶段的状态机）。

### 4. ⚠️ 关键发现：空输入交易在原版流程里根本走不到成交

`MerchantInventory.updateOffers()` 的开头（字节码偏移 0–85）：

```
tradeOffer = null;
if (inventory.get(0).isEmpty()) { in1 = inventory.get(1); in2 = EMPTY; }
else                            { in1 = inventory.get(0); in2 = inventory.get(1); }
if (in1.isEmpty()) {
    setStack(2, EMPTY);        // ← 两个输入槽都空时：清空输出槽并 return
    merchantRewardedExperience = 0;
    return;                    // ← 到不了下面的“按输入匹配配方并产生输出”
}
```

也就是说：**“槽空也能出货”不是把最低数量改成 0 就能得到的**，必须先让这条路径不提前返回并把输出槽填上。
这同时解释了方案第 45 行的警告（“必须保证两个输入槽均空时可领取符合条件商品”）。
阶段 D 需要注入的点因此至少是：`MerchantInventory.updateOffers()`（空槽分支）＋
`TradeOffer.matchesBuyItems`/`depleteBuyItems`（放行与不扣料）＋ 成交后的次数消费。

### 5. 阶段 A 剩余只读项（本轮已确认/已更正）

**⚠️ 更正（高优先级，我上一轮写错了）：附件在生物转换时【会】自动转移。**

上一轮我根据原版 `EntityConversionType.copyData(...)` 里没有 Fabric 字段，就断言"附件不自动迁移"——
**这个推断是错的**。Fabric 的 `fabric-data-attachment-api-v1` 自己注册了转换事件：

```java
// AttachmentEntrypoint#onInitialize
ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, keepEquipment) ->
        AttachmentTargetImpl.transfer(previous, converted, true));

// AttachmentTargetImpl#transfer
if (!isDeath || type.copyOnDeath()) {
    target.setAttached(type, entry.getValue());     // ← 同一个数据对象，不是深拷贝
}
```

`isDeath = true`，所以**带 `copyOnDeath()` 的附件会被转移**；本模组的关系附件正是
`.copyOnDeath()`，且它同时覆盖玩家重生与**生物转换**（`AttachmentRegistry.Builder#copyOnDeath` 的文档即如此说明）。
原版 `copyData` 里没有 Fabric 字段，只说明**原版自己**不搬这些数据，不能推出运行时不搬。

由此产生的**真实待办**（不是"要手工拷贝"，而是"要核对时序"）：

| 仍待确认 | 为什么重要 |
|---|---|
| Fabric `MOB_CONVERSION` 事件的确切注入点，及它与 `convertTo` 里 **finalizer 的先后顺序** | 若 finalizer 在 transfer 之后运行，**在 finalizer 里另建一份数据会被随后的 transfer 覆盖**；若在之前运行，transfer 又会覆盖 finalizer 写的东西 |
| transfer 是**同一对象**而非深拷贝 | 旧实体与新实体短暂共用同一个 `CreatureAffinityData`；任何在此刻对旧实体缓冲区的写入都要明确顺序，不能默认"两边各自独立" |
| 感染（村民→僵尸村民）与治愈（僵尸村民→村民）**两个方向**都触发该事件 | 方案要求两个方向都必须保持"同一村民" |
| 多玩家关系 + 额度 + 两个事件标记的**转换往返与存档重载**验证 | 这是"同一村民"结论的验收证据；在此之前不得声称已通过 |

**结论：不存在"必须手工迁移"的缺口，但也不存在"已验证"的结论。** 实现前先按上表核对时序与两个方向，
不要默认需要手工拷贝，也不要默认现状已经正确。

**✅ 已确认（本轮）：村民转换会新建实体**：`EntityType.VILLAGER.create(...)` → `MobEntity.convertTo(...)`
→ `setUpNewEntity` → finalizer → `spawnEntity` → 视类型丢弃旧实体；新实体是**新 UUID**。
附件之所以仍能跟上，是因为上面那条 Fabric 事件，而不是因为原版会拷贝。

**✅ 已确认（本轮，含更正）：英雄"新获得"的回调，但入口不止一处。**

- `LivingEntity.setStatusEffect(instance, source)` 的分支是
  `previous == null ? onStatusEffectApplied : onStatusEffectUpgraded`；
- **`LivingEntity.addStatusEffect(instance, source)` 也会直接调用 `onStatusEffectApplied`**
  （字节码偏移 52）——所以上一轮"`setStatusEffect` 是唯一分派点"**不成立**；
- 可用的过滤点因此是**回调本身**（`onStatusEffectApplied`），而不是某一个调用者：
  以"效果类型是 HAS_HERO_OF_THE_VILLAGE"+"实体是服务端玩家"过滤，并**只注入一处**，
  否则父子两处会各记一次，代次双计数；
- `ServerPlayerEntity` 本身就是服务端类，它覆写这些回调并转发网络层，**不是客户端代码**，
  不能把它当成"要排除的客户端包"。

**⚠️ 更正：共享 `onTakeItem` 不等于"第四份必然停止"。**

上一轮我由"批量与点击共用末端方法"推出可以在成功分支统一计次——**方向对，但不足以证明停止条件**：
`quickMove` 移动输出槽与普通 `takeStack` 的**时序**仍需查清（谁先取件、谁后提交、第三份之后输出槽何时刷新），
而且 Shift 循环里是否使用了**旧报价**或**已被移动的堆叠**也必须验证。
另外上一轮我写过"取件发生在 `onTakeItem` 之后"——**这是一处没有依据的统一断言，已删除**；
正确的说法是：**提取与提交的先后必须查清**，在提取之前仍要服务端重新验证。

| 当时待确认 | 为什么重要 | 现状 |
|---|---|---|
| Fabric `MOB_CONVERSION` 的注入点与 finalizer 的**先后顺序**（两个转换方向） | 决定"是否需要在转换处做任何事"，以及会不会覆盖/被覆盖 | **已确认**：见 6.1（finalizer 在前、事件在后、spawnEntity 之前；两方向一致） |
| 治愈者 uuid 的**读取入口** | 方案要求取自成功转换时的实际治愈者 | **已确认**：见 6.2（`ConversionPlayer`，明文 NBT；推荐两个观察点） |
| `MerchantScreenHandler.quickMove` / 普通 `takeStack` 的**时序**，及第三份后输出槽刷新 | 决定"第四份必然停止"能否成立、Shift 循环是否用旧报价 | **已确认**：见 6.3（点击提取在前、提交在后；Shift 同样提交，且**先入背包后提交**；循环条件已写明） |
| `MerchantScreenHandler.switchTo` / `onContentChanged` 的完整方法体 | 决定空槽免费时输出槽何时刷新、由谁写 | **已确认**：见 6.3(c)(d)（选择交易会 `setRecipeIndex` + `switchTo` 自动填充；`onContentChanged` 调 `updateOffers`） |
| `VillagerEntity.trade(TradeOffer)` 的实现（uses 与经验写在哪一份 offer 上） | 决定"免费是否消耗 uses"要在哪一层处理 | **已确认**：见 6.5（uses/经验/升级都写在这一份 offer 上） |

> 上表五行现均已落实为第 6 节的方法级结论，**不再是待办**；第 6 节同时给出了它们对阶段 C/D 的实现含义。

**没有确认的，就不写进实现。** 另外：**英雄效果的"延长/升级不刷新"目前只是 epoch 不变这一契约的测试**，
**不代表真实效果回调已经被验证**——回调尚未接线，两层要分开看。即使回调接线完成，
也还需要覆盖**加载/重生/效果复制/持续效果更新**才能说"每次 Applied 就是一个真实新获取周期"。

本轮把上一轮的**两条错误结论撤回并更正**（附件会随转换转移；英雄回调入口不止一处），
并把"共享末端方法即可停止第四份"降级为**待查时序**——该时序已在下一节查清。

### 6. 阶段 A 剩余只读项：**本轮全部查清**（方法级证据，来源见下）

> 本轮改用**反编译**（`I:\DSH\work\tools\vineflower.jar`，输入本机 1.21.11 yarn 映射 JAR）替代逐条读字节码，
> 因此下面引用的都是可读源码行，而不是"偏移 45/75"这类需要二次解释的推断。
> 反编译产物：`.codex-temp\mc-src\`（保留，供阶段 C/D 继续查）。

#### 6.1 转换时序：两个方向一致，事件发生在 finalizer 之后、spawnEntity 之前

`MobEntity.convertTo(...)`（源码）：

```java
if (this.isRemoved()) return null;
T mob = entityType.create(this.getEntityWorld(), reason);       // (1) 新建空实体
if (mob == null) return null;
context.type().setUpNewEntity(this, mob, context);              // (2) 位置/效果/组件
finalizer.finalizeConversion(mob);                              // (3) 原版搬运村民数据
if (this.getEntityWorld() instanceof ServerWorld sw) sw.spawnEntity(mob);   // (4) ← MOB_CONVERSION 挂在这次调用上
if (context.type().shouldDiscardOldEntity()) this.discard();    // (5) 丢弃旧实体
return mob;
```

Fabric 的注入点（`fabric-entity-events-v1` → `MobMixin`，mojmap 名）：

```java
@ModifyArg(method = "convertTo(...)",
           at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
private Entity afterEntityConverted(Entity converted, @Local(argsOnly = true) ConversionParams ctx) {
    ServerLivingEntityEvents.MOB_CONVERSION.invoker().onConversion((Mob) this, (Mob) converted, ctx);
    return converted;
}
```

`addFreshEntity`(mojmap) 就是 `ServerWorld.spawnEntity`(yarn)；`AttachmentEntrypoint` 在该事件里执行
`AttachmentTargetImpl.transfer(previous, converted, true)`，`copyOnDeath` 附件因此被**按引用**写给新实体。

**顺序结论（感染与治愈完全相同，因为两者都走 `MobEntity.convertTo`）：**

| # | 发生的事 | 治愈（僵尸村民→村民） | 感染（村民→僵尸村民） |
|---|---|---|---|
| 1 | 新建实体 | `EntityType.VILLAGER.create` | `EntityType.ZOMBIE_VILLAGER.create` |
| 2 | `setUpNewEntity` | `create(this,false,false)` → SINGLE | `create(villager,true,true)` → SINGLE |
| 3 | **finalizer（原版搬数据）** | `finishConversion` 的 lambda：装备、`setVillagerData`、`readGossipData`、`setOffers(offerData.copy())`、`setExperience`、`initialize`、`reinitializeBrain`、**`handleInteraction(ZOMBIE_VILLAGER_CURED, converter, villager)`**、反胃、世界事件 1027 | `infectVillager` 的 lambda：`initialize(...ZombieData(false,true))`、`setVillagerData`、`setGossip(villager.getGossip().copy())`、`setOfferData(villager.getOffers().copy())`、`setExperience`、世界事件 1026 |
| 4 | **MOB_CONVERSION → 关系附件转移（同一对象）** | 有 | 有 |
| 5 | `spawnEntity` | 有 | 有 |
| 6 | `discard()` 旧实体 | 有（SINGLE） | 有（SINGLE） |

**实现含义（这是本节真正要产出的东西）：**

1. **不要在 finalizer 里自建/搬运关系数据**——第 4 步的 transfer 会覆盖它，或与它共用同一个可变对象。
2. **原版治愈优惠发生在第 3 步**（`handleInteraction` → `VillagerEntity` 的互动响应 → 声望），
   早于我们拿到附件；它与本模组的折扣叠加的方式是**原版在 `prepareOffersFor` 里对 `specialPrice` 增量写入**，
   与本模组"HEAD 还原 / TAIL 施加"的记账互不冲突（既有 1.1.x 结论不变）。
3. 事件在第 5 步**之前**：处理函数里**不能**假设新实体已在世界中被登记（`world.getEntity(uuid)` 可能查不到），
   也不能假设它已经 tick 过。
4. 第 4~6 步之间旧实体仍持有同一个数据对象：任何"按实体身份"的缓存都必须意识到这一点（按 UUID 的键不受影响）。

#### 6.2 治愈者 UUID：明文 NBT，键名 `ConversionPlayer`；**入口设计已按复核意见修正**

`ZombieVillagerEntity`（源码）：

- 字段 `private UUID converter`（无 getter，`setConverting(UUID,int)` 也是 private）；
- 写：`view.putNullable("ConversionPlayer", Uuids.INT_STREAM_CODEC, this.converter)`
  ——**4 个 int 的明文 UUID，不是加密**；
- `ConversionTime` 只在 `isConverting()` 为真时写 `conversionTimer`，否则写 `-1`；
- 读：`ConversionTime`（默认 −1）≠ −1 时才读 `ConversionPlayer` 并 `setConverting(uuid, timer)`；
- 玩家喂金苹果时 `setConverting(player.getUuid(), random.nextInt(2401) + 3600)`。

**⚠️ 修正（复核指出）：不得把 `handleInteraction(ZOMBIE_VILLAGER_CURED, …)` 当作唯一的额度恢复入口。**
它有两个真实缺陷：

1. **它会静默不触发**：`finishConversion` 的 finalizer 里是
   `PlayerEntity p = world.getPlayerByUuid(this.converter); if (p instanceof ServerPlayerEntity) { … }`。
   治愈者**已离线**、或**不在该世界**（换了维度、去了别的存档/末地）时，`getPlayerByUuid` 取不到，
   整段（连成就带互动）都不会执行——而治愈**确实发生了**，这一次真实治愈事件会被丢掉。
2. **它发生在附件转移之前**（见 6.1 的顺序表：finalizer 是第 3 步，`MOB_CONVERSION` 是第 4 步）。
   在它里面去改"新村民"的关系数据，会被随后第 4 步的 transfer **覆盖**（或与之共用同一个可变对象）。

**修正后的入口设计（阶段 C/D 按此实现）：**

| 要素 | 决定 | 理由 |
|---|---|---|
| 读治愈者的时机与来源 | 在**转换完成路径**上，从**旧实体（僵尸村民）**读 `converter`；为此加一个 `@Accessor("converter")`（或读 NBT `ConversionPlayer`） | 新村民身上没有这个信息；旧实体此刻还没 `discard`，字段也未被清空 |
| 记录方式 | 落成一条**治愈事实**：`(村民UUID, 治愈者UUID, 维度/时间戳)`，先记事实、不要求玩家在线 | 玩家离线不该让"这次治愈"消失；额度只是**延后同步** |
| 应用时机 | **必须安排在附件转移之后**：把事实放进一个待处理集合，在该实体**确认已进入世界之后**（例如下一次报价/交互、或实体加载后的一个服务端 tick）再应用到关系上 | 不能依赖"同一个 Fabric 事件里监听器的先后"——`MOB_CONVERSION` 的监听器顺序没有契约保证；在同一次事件里改数据仍可能被 transfer 覆盖 |
| 玩家离线/换维度 | 事实照留；等到该玩家**下次可用**（上线、回到该维度、或下次与该村民交易报价）时再推进 `cureEpoch` | 与阶段 B 的"延迟应用"契约一致：epoch 只为真实事件增长，不因读取时机丢事件 |
| 幂等 | 每条事实带唯一键（村民 UUID + 治愈者 UUID + 该次转换的实例标记），重复投递只应用一次 | 转换事件与延迟应用可能被触发多次（重载、重复 tick） |
| 备选入口 | `ServerWorld.handleInteraction(ZOMBIE_VILLAGER_CURED, …)` **只作为"额外证据"**（在线时能更早拿到），不作为唯一入口 | 它不可靠，见上面的缺陷 1 |

**必须补的测试（阶段 D 验收项，纯逻辑可测的部分先测，游戏内部分单列）：**

1. **治疗开始后玩家离线**：治愈完成时 `getPlayerByUuid` 取不到 → 治愈事实仍然产生并保留；
   玩家上线（或下次打开该村民交易界面）后 `cureEpoch` 推进、额度按规则置 3。
2. **治疗开始后玩家换维度**：同上，事实不能因为"当前世界查不到玩家"而丢失或写错到别人身上。
3. **顺序断言**：应用关系变化的那一步**必须晚于**附件转移；用一个可注入的"转移完成"钩子断言顺序，
   而不是依赖真实监听器顺序。
4. **幂等**：同一事实投递两次只推进一次 epoch。
5. **转换往返**：村民→僵尸村民→村民之后，多玩家关系、两个 epoch 标记、剩余额度都还在（游戏内）。

#### 6.3 提取与提交：Shift 与点击**共用末端** `TradeOutputSlot.onTakeItem`，但提取在前

（a）**普通点击（PICKUP）**：`ScreenHandler.internalOnSlotClick` → `slot.tryTakeStackRange(...)`
（`TradeOutputSlot.takeStack` 先累加 `amount`）→ `optional.ifPresent(stack -> { setCursorStack(stack); slot.onTakeItem(player, stack); })`
→ **提取在前、提交在后**，提交拿到的是**非空**的取出堆叠。

（b）**Shift 批量（QUICK_MOVE）**：

```java
// ScreenHandler.internalOnSlotClick
ItemStack itemStack = this.quickMove(player, slotIndex);
while (!itemStack.isEmpty() && ItemStack.areItemsEqual(slot.getStack(), itemStack)) {
    itemStack = this.quickMove(player, slotIndex);       // ← 一次 Shift 会连续成交多份
}

// MerchantScreenHandler.quickMove
if (slot == 2) {
    if (!this.insertItem(itemStack2, 3, 39, true)) return ItemStack.EMPTY;   // 输出先被塞进背包
    slot2.onQuickTransfer(itemStack2, itemStack);                            // 只有统计：Slot.onQuickTransfer → onCrafted
    this.playYesSound();
}
...
if (itemStack2.getCount() == itemStack.getCount()) return ItemStack.EMPTY;
slot2.onTakeItem(player, itemStack2);        // ← 所有分支共同末端：**提交在这里**
```

→ 结论：**"Shift 不提交、只有点击提交"是错的**；两者都到 `onTakeItem`。但必须注意：
**提交时物品已经先被移进背包**，所以提交处只能决定"扣不扣料/扣不扣额度"，无法"先拒绝再不给"；
`onQuickTransfer` 本身只做统计（`onCrafted`），不参与成交。

（c）**选择交易会触发自动填充**：`ServerPlayNetworkHandler.onSelectMerchantTrade(packet)`：

```java
handler.setRecipeIndex(index);   // → merchantInventory.setOfferIndex → updateOffers
handler.switchTo(index);         // → 若两个输入槽都空，从背包把材料搬进输入槽（autofill）
```

→ 免费交易若不希望"玩家不必付钱却被自动塞进材料"，需在免费资格成立时跳过这段 autofill；
若保留 autofill，则材料会留在槽里、关闭界面时由 `onClosed` 退还，但界面上看起来像要付钱。
**两种做法的取舍属于阶段 D 的设计决定，本勘察不改代码。**

（d）**输出槽刷新时机**：`MerchantInventory.setStack(0/1, …)` 与 `removeStack(0/1, …)` 会触发 `updateOffers()`
（`needsOfferUpdate` 只对 0/1 为真）；`MerchantScreenHandler.onContentChanged` 也调用 `updateOffers()`；
`setStack(2, …)` **不会**递归。空输入时 `updateOffers()` 提前返回并清空输出。

#### 6.4 零价必须绕过的三个原版判定（源码级）

1. `TradeOffer.getFirstBuyItemCount(...)` =
   `clamp(base + max(0, floor(base * demandBonus * priceMultiplier)) + specialPrice, 1, maxCount)`
   —— **下限是 1**：把价格"算成 0"在原版数学里表达不出来。
2. `TradeOffer.matchesBuyItems(stack, buyItem)`：第一步就是 `firstBuyItem.matches(stack)`，
   空槽为假 → **两槽皆空永远不匹配**；`getValidOffer` 与 `depleteBuyItems` 都建立在它之上。
3. `MerchantInventory.updateOffers()`：`if (first.isEmpty()) { setStack(2, EMPTY); merchantRewardedExperience = 0; return; }`
   —— 空槽时输出被清空。

因此零价实现必然是"**另一条路**"：让空槽也能填输出（`updateOffers` 空槽分支）＋ 成交时不走 `depleteBuyItems`。

#### 6.5 `merchant.trade(offer)` 到底改了什么（**落点要分开说**）

```java
// MerchantEntity.trade（VillagerEntity 继承）
offer.use();                                        // ← 这一份 TradeOffer 上：uses++（isDisabled() = uses >= maxUses）
this.ambientSoundChance = -this.getMinAmbientSoundDelay();
this.afterUsing(offer);                             // VillagerEntity.afterUsing ↓
if (this.customer instanceof ServerPlayerEntity sp) Criteria.VILLAGER_TRADE.trigger(sp, this, offer.getSellItem());

// VillagerEntity.afterUsing
int i = 3 + this.random.nextInt(4);
this.experience += offer.getMerchantExperience();   // ← 村民实体上的字段
this.lastCustomer = this.getCustomer();
if (this.canLevelUp()) { this.levelUpTimer = 40; this.levelingUp = true; i += 5; }   // ← 村民实体上的计时/状态
if (offer.shouldRewardPlayerExperience()) world.spawnEntity(new ExperienceOrbEntity(...));  // ← 世界中的一个新实体
```

**落点必须分开表述（复核指出上一版写成一锅"都写在 offer 上"）：**

| 变化 | 落点 |
|---|---|
| `uses`（交易次数，`isDisabled()` 的依据） | **这一份 `TradeOffer`** |
| 村民经验 `experience` | **村民实体** |
| 升级计时 `levelUpTimer` / `levelingUp` | **村民实体** |
| 玩家经验球 | **世界里新生成的 `ExperienceOrbEntity`** |
| 成就判定 `Criteria.VILLAGER_TRADE` | 以 `customer`（村民实体的客户字段）为输入触发 |

→ 免费交易若照常调用 `merchant.trade(offer)`，上述全部照旧发生（与付费交易一致）；
"免费但不消耗 uses"只是**其中一项**语义的改动，不能顺带把经验/升级/经验球一起改掉或一起保留，
必须逐项决定并写进文档。

#### 6.6 对第三节、第四节的影响

- 第一节末尾那张"仍待确认"表的**五行全部已确认**（6.1~6.5），因此不再是待办；
- 第四节第 5 条从"要核对时序"改为"已核对的结论 + 仍需游戏内验证的项"；
- 第三节中"注入点与 finalizer 先后顺序"从待查项**移除**；保留"**游戏内转换往返 + 存档重载**"作为验收项
  （它需要真实客户端/服务端，本轮不做）；
- 1.1.3 起发布构建用 `-Pwip=false`：**开发构建 289 项**（含 20 项 `FreeTradeQuota` 测试），
  **发布构建 269 项**。阶段 B 的 20 项仍只覆盖纯逻辑。

---

## 二、阶段 B 已交付：额度状态机（纯逻辑）

新增 `com.nonid.affinity.service.FreeTradeQuota`，不含世界、实体或物品依赖，规则全部落在纯函数里，
因此“哪个事件刷新、哪个事件绝不能刷新”可以被逐条断言。

### 已落地的规则

| 规则 | 实现 |
|---|---|
| 每对“玩家×村民”独立，共享 3 次，不按商品分 | `State` 是**每对关系**的状态；商品不参与 |
| 两次不同事件可以各自重置为 3，但**永不叠加** | 刷新是 `SET_FULL`（置 3），不是 `+3` |
| 同一事件重复通知不再恢复 | 两个 epoch 一起比较，都不新则不动 |
| 英雄事件**延迟应用**（不在获得时遍历世界） | 由“下次报价”调用 `applyObservedEvents(state, heroEpoch, cureEpoch)` |
| 英雄延长/升级不刷新 | epoch 只在**新获得**时增长，持续时间/等级变化不改变它 |
| 未消费的事件之后可用 | 无“过期”逻辑；成交时再检查挚友与价格 |
| 只剩 1 次时来新事件 → 3（不是 4） | 同上 `SET_FULL` |
| 失去挚友或价格不达标 → 暂停，不扣不重置 | 资格是**每次报价重新计算**的，`State` 不动 |
| 只有**真正成功交付**才扣次数 | `spend(...)` 只由成交路径调用；另有 `recordPaidCompletion` 只记累计 |
| 低于 0 不可能 | `spend` 在 0 处停；`sanitized()` 把读到的值裁剪到 `0..3` |
| 旧存档不凭空得 3 次 | `forMigratedRelation(...)` 的 `remaining == 0`，且把当前 epoch 标为已处理；**第一次真实事件**才发第一轮 |
| 累计数不随刷新清零 | `totalFreeTrades` / `totalCompletions` 在刷新中保留 |

### 事件同步契约（按复核意见重做）

**第一版把两个标记分别推进，这是错的**：若英雄 1 已发生但尚未同步，治愈 1 先给出 3 次，消费两次后才同步英雄 1，
又会回到 3——等于把一次旧恢复积存下来。现在只有**一个**入口：

```java
FreeTradeQuota.applyObservedEvents(state, currentHeroEpoch, currentCureEpoch)
```

规则：比较两个已处理标记，**任一**变新就**只置 3 一次**并**同时推进两个标记**；都不新则什么都不做。
调用时机是**每次报价**以及**成交验证之前**。这样：

- 治愈即时处理时**带上当时玩家的英雄代次**，不会把已发生的英雄事件留成“以后才发现”的第二次奖励；
- 事后真正新发生的事件（代次更高）仍可恢复；
- 重复/过期通知（含延长或升级英雄，它不改变代次）不生效。

### 初始化契约（按复核意见拆分）

| 工厂方法 | 用途 | 语义 |
|---|---|---|
| `State.forNewRelation()` | **新关系首次见面** | 两个标记留 0；因此**已经为真**的事件（例如玩家已是英雄）仍会被认作新事件并授额 |
| `State.forMigratedRelation(hero, cure)` | **旧存档迁移** | 把安装时已存在的代次标为已处理，**不伪造事件**；安装后发生的事件仍然有效 |

第一版只有一个 `initial(...)`，把“传当前代次”当成已处理，于是**新村民会吞掉第一次真实事件**——
这正是复核指出的“调用合同不清”。现在两个名字各自说明历史假设。

### 价格公式（按复核意见修正）

```java
effectiveFirstPayment(baseCount, rawDemand, priceMultiplier, specialPrice)
    = baseCount + max(0, floor(baseCount * rawDemand * priceMultiplier)) + specialPrice
```

第一版把 `demandBonus` 当成“已经算好的加成”直接相加，**用错了语义**：`TradeOffer.getDemandBonus()` 是
**原始需求值**，必须乘上 `priceMultiplier` 再取整。例：base 20、raw demand 2、multiplier 0.05 → 加成 2；
multiplier 0.2 → 加成 8。同一个 raw demand 会因系数不同而不同，所以系数属于公式。
返回的名字改为 `demandCost(...)`（“要付的**物品数量**”），float + floor 的形态与原版一致。

### 另外统一的一件事：**唯一的有效价公式**

现行接口只有一个（旧的三参数版本已删除，本文档不再保留其描述）：

```java
FreeTradeQuota.effectiveFirstPayment(baseCount, rawDemand, priceMultiplier, specialPrice)
FreeTradeQuota.demandCost(baseCount, rawDemand, priceMultiplier)          // 需要单独展示/记录时
```

即“原版算术（含 float + floor 的需求项）、但不加最低 1 的钳制”。资格判据是**这个值 ≤ 0**
（而不是“折扣配置=100%”）：配置 50% 但把便宜商品打到 0 → 合格；配置 100% 但因需求加价仍为正 → 不合格、也不扣次数。

### 测试

`FreeTradeQuotaTest` **20 项**，覆盖：

- 刷新事件：英雄 epoch 首次/重复/过期回调；延长与升级不刷新；治愈首次/重复；两事件不叠加；
  只剩 1 次时新事件变 3；
- **同步契约（本轮新增）**：两事件都先为真、消费后再同步 → 不退款不补发；
  **治愈即时应用并带上英雄代次后，延迟到达的旧英雄事件不再授额**；两个代次同时变新只授一次；
  消费之后**真正新发生**的事件仍可恢复；
- **初始化契约（本轮新增）**：新关系 `forNewRelation()` 不授额但第一次真实事件仍授；
  迁移 `forMigratedRelation(...)` 不重放安装时已存在的效果，安装后的事件与治愈仍有效；
- 资格：四种拒绝原因；需求导致价格未达 0 时不扣次数；失去挚友只暂停；
- 消费：到 0 为止、累计继续、刷新不抹累计且推进 `quotaRevision`；
- 存档：被篡改值裁剪（99→3、负数→0）；
- 价格：与原版 float+floor 算术逐例对照，含“负需求被忽略”与
  **同一 raw demand 在不同 priceMultiplier 下结果不同**（0.05→2、0.2→8）。

`gradle build`：**289 项测试通过、0 失败、0 跳过**（1.1.1 的 269 项 + 新增 20 项）。
其中"固定基线的实际持久化"仍未实现（见第三节），因此这 20 项只覆盖纯逻辑。

---

## 三、未做 / 未验证（必须如实对待）

**阶段 B 的纯逻辑可以继续使用，但下面这些一律不能标为已完成：**

- **固定基线的实际持久化未实现**：`forMigratedRelation(...)` 的基线目前只存在于测试里，
  **没有任何地方在功能首次启用时把它写进存档**。在落地之前，“迁移基线固定一次”只是契约，不是事实。
- **交易接入未实现**：空槽出货、双材料免除、逐份扣次数、库存与 uses、服务端重新校验，全部未写；
  **空输入成交未验证**（原版 `MerchantInventory.updateOffers()` 的空槽提前返回仍是当前实际行为）。
- **UI 未实现**：“挚友礼遇：免费，剩余 2/3 次”以及耗尽后恢复普通报价。
- **事件接线未实现**：英雄"新获得"回调（注入点已确认，见第一阶段）与治愈"成功转换"事实都**没有接**。
  治愈侧必须按 6.2 修正后的设计做：**从旧实体读 `converter`** → 落成治愈事实（玩家离线也保留）→
  **在附件转移之后**再应用到关系上，**不得**以 `handleInteraction(ZOMBIE_VILLAGER_CURED, …)` 为唯一入口。
  该设计的 5 项测试（离线、换维度、顺序、幂等、转换往返）**一项都没写**。
- **转换往返未验证（时序本身已查清）**：见第一节 6.1——`MOB_CONVERSION` 在本机 yarn 映射下由
  `fabric-entity-events-v1` 的 `MobMixin` 以 `@ModifyArg` 挂在 `MobEntity.convertTo` 内
  `ServerWorld.spawnEntity` 那次调用上，因此顺序是
  `setUpNewEntity → finalizer（原版搬村民数据）→ MOB_CONVERSION（附件按引用转移）→ spawnEntity → discard`，
  感染与治愈两个方向都走同一条 `MobEntity.convertTo`，所以结论一致，**不需要**手工迁移。
  仍然未做的，是**游戏内**的转换往返与存档重载验证（"多玩家关系 + 额度 + 两个标记"是否完好）。
- **存档字段未落地**：`PlayerAffinity` 里还没有 `remainingFreeTrades` / `lastAppliedHeroEpoch` /
  `lastAppliedCureEpoch` / `quotaRevision` 等字段，也没有旧存档迁移。
- **未做游戏内验证**，也未安装 1.2.0（版本号**尚未**升到 1.2.0）。
- 依授权：阶段 F 若要在测试实例上验证存档迁移，我会**先把该实例的 saves 备份**再动。

---

## 四、提交层契约（进入阶段 C/D 前必须遵守，来自本轮复核）

1. **资格信号 `tradeAvailable` 的含义**：它表达“配方存在、库存未耗尽、目标有效、界面状态可用”，
   **不得**复用原版“当前输入材料是否足够”的布尔值——免费交易恰恰是材料不足的情形，
   复用会让每一笔免费交易在开始前就被拒。免费分支**绕过两种材料要求**。
2. **`spend(...)` 在剩余为 0 时静默返回 ≠ 提交成功**。后续提交层必须返回明确的成功/失败，
   **只有确认成功后才消费一次并派发一次成就事件**；失败（背包满、断线、重复请求、旧报价）不得消费。
3. **`quotaRevision` 的用途**：它是“旧报价是否过期”的依据之一。消费等状态变化**必须推进**它，
   否则第四次交易可能因为“修订号没变”而继续走免费路径。不能只靠检查刷新版本来阻止第四次。
4. **成功判定点**：只能用 `TradeOutputSlot.onTakeItem` 里那个成功分支（见第一阶段第 3 节），
   **不得**以“方法执行过 / deplete 调用过”代替。注意真实风险是**输出提取（`takeStack`）与成功提交
   （`onTakeItem`）可能不同步**——两者必须在同一次服务端处理里串起来，且不得在末端拒绝后回滚已取走的物品。
5. **转换迁移不是"要自己搬数据"，而是"它自己会搬、但顺序决定你不能乱插队"**：已查清（第一节 6.1）——
   Fabric 的 `MOB_CONVERSION` 挂在 `MobEntity.convertTo` 里的 `spawnEntity` 调用上，**在 finalizer 之后**，
   附件按引用转移。因此：**不要**在 finalizer 里另建数据（会被 transfer 覆盖或共用同一对象）；
   事件里不要假设新实体已进入世界；也不要依赖"按实体身份"的缓存。
   仍需在阶段 D 用**游戏内**转换往返 + 存档重载验证"多玩家关系 + 两个事件标记 + 额度"。
6. **治愈事实的入口必须与"玩家在线"解耦（按复核意见）**：治愈者 UUID 取**旧实体**的 `converter`；
   事实先记下（玩家离线/换维度也要保留），**在附件转移之后**再应用到关系上；
   不得依赖同一个 Fabric 事件内的监听器顺序，也不得以 `handleInteraction(ZOMBIE_VILLAGER_CURED, …)`
   为唯一入口（它在玩家查不到时整段不执行）。完整设计见 6.2。

---

## 五、对文档待确认项的处理（按复核意见定案）

1. **挚友判定口径**：零价资格使用**当前好感 ≥ friendThreshold**，符合“失去挚友即暂停”的既定规则。
   现有 `AffinityService.isFriend(...)` 含**缓存 OR 条件**，**不可直接复用**；
   本轮**只统一零价资格及其 UI 口径**，不擅自改动其他既有同伴规则。
2. **额度存关系侧**：`PlayerAffinity` 可行，且 Fabric 的 `MOB_CONVERSION` 会把 `copyOnDeath` 附件转给新实体，
   所以设计上不需要手工搬迁。但**该转移的时序与两个转换方向尚未验证**（见第四节第 5 条），
   因此现在仍**不得声称“同一村民”的额度已确认随个体转移**。

---

## 六、阶段 C/D 接入：本轮已写进源码的部分，及其验证边界

### 6.1 新增的文件（全部登记在 `tools/wip-sources.txt`）

| 文件 | 作用 |
|---|---|
| `service/FreeTradeAccounts` | 关系字段 ↔ `FreeTradeQuota.State` 的桥；迁移、记录治愈事实、**应用待同步事件后再给交易层用** |
| `service/CureFactRules` | 治愈事实的去重规则（纯逻辑）：同一转换id只记一次；无转换id / 无治愈者分别拒绝 |
| `service/FreeTradeQuote` | **唯一的报价计算**：界面与服务端成交用同一份 `effectiveFirst` / `vanillaFirstCount` / 拒绝原因 / 额度 / 修订号 |
| `service/VillagerFreeTrades` | 服务端协调层：挚友判定（当前好感，不用带缓存的 `isFriend`）、报价、免费成交扣次、治愈事实记录、事件注册 |
| `data/FreeTradeHeroEpoch` | 玩家侧英雄代次（附件的 `copyOnDeath`，只在新获得时 +1） |
| `mixin/ZombieVillagerCureAccessor` | 读**旧实体**的 `converter`（私有字段，无 getter） |
| `mixin/FreeTradeMerchantInventoryMixin` | 空槽/材料不足时把合格商品的售出物放进输出槽 |
| `mixin/FreeTradeOutputSlotMixin` | 免费分支：保留原版全部副作用（`merchant.trade`、统计、经验同步），**只跳过扣料**，并扣一次额度 |
| `mixin/FreeTradeHeroEffectMixin` | `onStatusEffectApplied` 且效果为村庄英雄时推进代次（延长/升级走另一个回调，不推进） |
| `data/PlayerAffinity` | 新增持久化字段：`remainingFreeTrades`、两个 `lastApplied*Epoch`、**`observedCureEpoch`（待同步事实）**、**`lastCureSourceId`（去重标记）**、`quotaRevision`、累计数、`quotaInitialized` |

### 6.2 复核两点硬化要求的落实方式

1. **待处理事实持久化**：事实 = 关系里的 `observedCureEpoch`，去重标记 = `lastCureSourceId`，
   两者都写入 NBT（`quota_initialized` 与它们同组写入，避免"只写一半被当成未初始化而重新迁移"）。
   玩家离线时**照样记录**：记录路径只用到治愈者 UUID，不做任何玩家查找。
2. **不靠转换后的村民 UUID 定位**：事实与标记都存在**关系**里，而关系在生物的 `copyOnDeath` 附件中，
   由 Fabric 的转换转移按引用带到新个体；去重 id 用的是**转换方实体**的 UUID（一次转换一个），
   不是村民的 UUID。因此连续感染/治愈都归属同一关系。

### 6.3 已验证 / 未验证（本轮如实分界）

**已验证（可复现）**

- `gradle test`（开发构建，含开发中源码）：**303 项，0 失败 0 错误**（比 1.1.4 的 269 项发布构建多 34 项，
  其中本轮新增 14 项：`FreeTradeAccountsTest` 7 项 + `FreeTradeQuoteTest` 7 项）。
  覆盖：治愈事实跨重载不丢不重、同一转换重复观察只记一次、二次转换仍归属同一关系、
  多条旧事件只发一次 3 次、迁移基线固定、旧存档不再补发、**第 3 → 第 4 次边界**
  （免费 3 次后报价转为正常价且拒绝原因是"额度用尽"，而非"不能交易"）、
  非挚友即使价格为 0 也不免费、需求加价导致价格 >0 时不扣次数。
- **混入在真实服务端加载成功**：`gradle runServer` 启动到 `Done (0.280s)!`，
  四个新混入全部应用（配置是 `required: true` + `defaultRequire: 1`，签名不对会直接崩启动）。

**未验证（不得声称）**

- **没有任何游戏内交易实测**：空槽出货、双材料免除、Shift 批量上限、第三次后停止、
  背包满/光标持物/旧报价/重复请求等边界，全部未在游戏里跑过。
- 事件级未接：UI（"挚友礼遇：免费，剩余 2/3 次"）、三类事件与结构化日志、成就预留（阶段 E）。
- 迁移与转换往返的**存档实测**未做（阶段 D 验收项，需备份测试存档后执行）。

### 6.4 一个观察：锚点自检在**开发环境**会假报失败

`gradle runServer`（开发环境）日志里出现：

```
[NoN Affinity] could not find advanceStage(Lnet/minecraft/class_3218;Ljava/util/UUID;)Z in
com.nonid.internal.animation.server.ServerAnimationController; ... the next-stage control will NOT grant affinity.
```

这不是本轮引入的，也不是 1.1.x 的回归，而是**环境差异**：该自检用常量字符串比对**中介名**描述符
（`class_3218`），而开发环境里 Fabric 会把模组运行期重映射到 yarn 名（`ServerWorld`），于是比对失败；
交付出去的 JAR 在生产环境两侧都是中介名，所以能匹配（1.0.49 的实机日志即是生产环境的通过记录）。
**结论**：开发服务端**不能**用来验证该锚点；要验证锚点必须在生产实例上装交付 JAR 跑。
若将来要让两侧都能自检，应让比对同时接受中介名与 yarn 名，而不是只认一个。

### 6.5 复核指出的三处缺陷：修复方式与现在的验证状态

| # | 缺陷 | 修复 | 现在能证明什么 |
|---|---|---|---|
| 1（高） | **旧免费报价失效后可能拿到商品却不扣额度**：检查只在 `onTakeItem`，而普通点击先把商品放进鼠标、Shift 先把商品放进背包，都发生在该方法之前；此时若资格已失效，代码退回原版扣料流程，空材料槽扣料失败，但商品已经给出去且收不回来 | **把判定挪到"商品移动之前"的两个入口**：① 新增 `FreeTradeSlotGuardMixin`（目标 `Slot.canTakeItems`，仅对 `TradeOutputSlot` 生效）——原版在普通点击的 `tryTakeStackRange` 之前、Shift 的 `quickMove` 之前都会先问它；拒绝时**先刷新输出**再返回 false，商品原地不动。② 新增 `FreeTradeMerchantScreenHandlerMixin`（目标 `MerchantScreenHandler.quickMove` 的 HEAD）——Shift 循环里 `canTakeItems` 只在循环开始前检查一次，逐次迭代必须由这里拦住，返回 `ItemStack.EMPTY` 让调用方的 while 结束。判定规则独立成纯函数 `FreeTradeGuard.verdict(付不出钱, 现在是否免费)`，三种结果各有测试 | 规则层：`FreeTradeGuardTest` 3 项（失效+付不出钱→拒绝；仍免费→放行；槽里有材料→完全不干预）。混入层：`runServer` 启动到 `Done`，两个新混入与新的 `MerchantInventory` 访问器全部应用（`required:true` + `defaultRequire:1`，签名不对会崩启动）。**仍未做**：游戏内实际点击/Shift 复现该场景 |
| 2（高） | **旧存档首次报价会吞掉升级后获得的英雄奖励**：未初始化关系把"首次报价时的英雄代次"当迁移基线 | 迁移基线改为**固定的 0**（`State.forNewRelation()`），删除 `forMigratedRelation(baseline...)`。理由是它本来就成立：英雄代次计数器随功能安装从 0 开始，**只有本模组观察到的"新获得"才会 +1**，所以 >0 一定是安装后的事件；而安装时已经在生效的效果从未推进计数器，仍是 0，因此**不会伪造**奖励。基线也因此与"何时第一次报价"、"村民当时是否加载"完全无关 | `FreeTradeAccountsTest` 新增 **T4**（升级→获得英雄→首次报价→拿到 3 次；且安装时已在生效的效果不会伪造）；`FreeTradeQuotaTest` 两条迁移用例改写为同一口径，并保留"未加载村民不吞事件"的断言 |
| 3（中） | **普通交易累计次数在成交成功前增加**：`recordPaidCompletion` 在原版扣料结果未知时就执行 | 移到 `onTakeItem` 的 **TAIL**，并用**该 offer 的 uses 是否增加**判定是否真的成交（`MerchantEntity.trade` 的第一件事就是 `offer.use()`）；HEAD 记下成交前的 uses，免费分支设标记并在 TAIL 直接返回，避免免费成交被算成普通成交。若 HEAD 发现"报价已不免费、而两个付款槽都是空的"（守卫本应拦住的提取），记一条 warn 而**不计入成交** | 规则层：`FreeTradeAccountsTest` 覆盖计数语义；混入层由启动验证确认注入点存在。**仍未做**：游戏内验证"失败提取不计入统计" |

**服务端启动成功只能算启动检查**，本轮没有改变这个定位；上表最后一列的"仍未做"就是它的边界。

**复核建议的交互验证清单（仍未执行，需实机）**：免费商品显示后好感降低再点击/Shift（不交货、不扣次、不计成交）；
升级旧存档→先获得英雄→首次报价（发 3 次）；三次连续 Shift（恰好 3 次免费，之后按正常价）；
背包满/只能容纳部分/鼠标持物（不丢、不重复交货、扣次与成交一致）；免费报价后库存或所选配方变化（旧输出不能继续成交）。

### 6.6 第二轮复核的三处遗漏：修复方式

| # | 遗漏 | 修复 | 回归覆盖 |
|---|---|---|---|
| 1（高） | **槽里有东西不等于付得起**：守卫以"两个付款槽皆空"为拒绝条件。放错物品、数量不足、缺第二材料时槽并不空，守卫放行 → 商品先被取走 → 原版扣料失败 → 无偿取得 | 放行条件改为**「当前确实能免费成交」或「当前材料确实满足正常成交」**。付款匹配用原版自己的 **`TradeOffer.matchesBuyItems`（不消耗材料）**，并且**两个槽位顺序都试**，与完成路径试 `depleteBuyItems` 的方式一致。判定签名变为 `FreeTradeGuard.verdict(available, quoteFree, canPayNow)`：不可用→拒绝；免费→免费放行；付得起→按正常价放行；否则→拒绝并刷新 | `FreeTradeGuardTest` 扩到 4 项，含"槽满但付不起→拒绝"；`FreeTradeWiringTest` 断言**两个顺序都试**且**绝不出现 `.depleteBuyItems(`**、断言没有任何调用点把字面量 `true` 当可用性 |
| 2（高） | **守卫没有真正验证库存**：三个调用点都写 `quote(..., true)`，等于宣称可用 | 新增 `VillagerFreeTrades.tradeAvailable(villager, player, offer)`：生物未移除且存活、**当前客户就是该玩家**、`!offer.isDisabled()`（缺货）、且 `getOffers().contains(offer)`（按身份判断配方是否还在列表里——补货会重建列表）。**四处调用点（两个守卫、输出槽提交、生成输出）全部传入这个结果**，不再有字面量 `true` | 同上（wiring 测试逐个文件检查）；可用性语义本身由原版状态决定，实机验证仍在待办 |
| 3（中） | **普通成交统计漏掉"材料恰好用完"**：HEAD 存旧报价、TAIL 重新 `getTradeOffer()`；成功扣料后付款槽更新会触发 `updateOffers()`，材料恰好耗尽时该字段变 null，TAIL 直接返回，真实成交没有计数；若换了报价还会比较到另一个对象 | HEAD **连 TradeOffer 对象一起保存**（`noncreatureaffinity$offerOfTake`），TAIL 比较**同一个对象**的 uses；免费分支仍用标记跳过 | `FreeTradeWiringTest` 断言 HEAD 保存对象、TAIL 使用保存的对象 |

**这四类回归的当前状态**：错误材料、材料不足/缺第二材料 → 逻辑层由 `verdict` 矩阵覆盖，判定输入取自原版
`matchesBuyItems`（本模组不自己实现匹配）；旧输出变缺货 → 由 `tradeAvailable` 覆盖，调用点由 wiring 测试钉住；
普通交易恰好耗尽材料仍计数 → 由"保存同一对象"覆盖并由 wiring 测试钉住。**它们的游戏内表现仍未实测。**
