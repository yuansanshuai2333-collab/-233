# 开发交接记录（交易 UI / 同步载荷 → dev.3）

**用途**：上下文恢复后直接从"未完成路径"继续，不重启方案讨论。规格见 `规格-1.2.0-关系等级与权限.md`（已定稿）。

## 一、当前状态（实测，非推测）

| 项 | 状态 |
|---|---|
| 已安装 | `1.2.0-dev.2`（两处实例哈希一致 `afc260aa97af…`；含 Shift 并发修复，已由 GPT 查字节码确认） |
| dev.3 | **未交付**。已写出但未打包 |
| 已写出并编译通过 | `service/RelationshipTiers.java`（四档 + 迁移算法，含 `Math.toIntExact((long) min + floorDiv(...))`）、`service/FreeTradeStatus.java`（服务端判定→显示状态的唯一映射） |
| 测试 | `RelationshipTiersTest` 8 项通过；`FreeTradeStatus` **无测试** |
| 未写 | 网络载荷、注册、客户端渲染、服务端发送点、窗口清理、旧数据拦截 |
| 冻结交付 | `1.1.4`（`c22f112b7c2e…`），任何新玩法只在 dev 包 |

## 二、目标：一条可运行完整路径（本批只做这条）

```
打开村民交易窗口 → 服务端发送当前报价 → 客户端显示（免费 / 剩余 N/3 / 原因）
→ 成交后更新 → 关闭窗口清理
```

然后补：配方切换、资格变化（好感掉档、额度耗尽、缺货）、旧窗口/旧序号数据拦截。

### 实现顺序与落点（每步都可编译验证）

1. **载荷**：新增 `net/FreeTradeStatusPayload`（记录 `FreeTradeStatus` 的字段）＋ 在 `net/AffinityNetworking` 的注册处加 S2C 注册。
   - 参照同目录既有 `AffinityDataPayload` 的 `PayloadTypeRegistry` / `PacketCodec` 写法照抄形态，不要自创。
   - **窗口身份**：`syncId` + 村民 UUID + **服务端该窗口实例令牌**（自增 long）。**不能只靠 `syncId`**（原版会复用）。
2. **服务端发送点**（`service/VillagerFreeTrades` 内新增 `sendStatus(...)`）：
   - 打开窗口时、切换配方时（`MerchantScreenHandler.setRecipeIndex` / `switchTo`）、每次成交后、额度刷新后、
     好感或配置变化后、库存/价格变化后。
   - 报价生成处已有 `FreeTradeQuote`，直接映射 `FreeTradeStatus.of(windowId, offerIndex, quote, remaining, total, currentCustomer)`。
3. **客户端状态**：`client/FreeTradeStatusStore`（按窗口键存当前状态；**先绑定窗口实例令牌再接收报价**，第一份任意报价不当身份）。
4. **客户端渲染**：新增客户端混入（`MerchantScreen`）在交易界面画一行：
   - 免费：`挚友礼遇：免费（两种付款材料均免除）· 剩余 N/3 次`
   - 失效但有原因：正常价 + 原因（`NOT_CLOSE_FRIEND` / `NO_ALLOWANCE_LEFT` / `PRICE_NOT_FREE`）
   - **非当前客户**：`交易不可用`（**清免费态，不显示价格**）
   - 等待新报价：`报价更新中`（撤下旧免费提示，不显示"按正常价"）
   - 批量停止：按实际原因（额度用尽 / 好感失效 / 缺货），不一律归因"次数用完"
   - 混入必须进**客户端** mixin 配置（现工程只有公共 `noncreatureaffinity.mixins.json`，需确认是否已有 client 配置；没有则新建并在 `fabric.mod.json` 注册）。
5. **清理**：`onClosed` / 切换窗口时清该窗口状态；序号不匹配、村民不匹配、实例令牌不匹配 ⇒ 丢弃。

### 必测（规则层可测的先测，交互层留给实机）

- `FreeTradeStatusTest`：免费态；`NO_ALLOWANCE_LEFT`；`NOT_CLOSE_FRIEND`；`PRICE_NOT_FREE`；
  **非当前客户 ⇒ `showAsUnavailable()` 为真且 `free == false`**；等待态；`paidFirstCount` 与报价一致。
- 旧数据拦截：序号更小 / 窗口不同 / 村民不同 ⇒ 不覆盖当前显示。
- 交互层（实机，用户执行）：窗口重开、快速切配方、额度耗尽、资格失效、关闭后迟到载荷。

## 三、必须保留的回归案例（关闭标准）

**服务端已记录免费成交，界面仍显示付款价格。** 关闭条件：窗口同时正确显示
①免费状态 ②两种付款材料均免除 ③剩余次数，且与**实际扣料结果**一致（背包+付款槽合计核对）。

## 四、纪律与约束（沿用，不重复讨论）

- 证据四层分界：规则层 / 写法层（源码断言，**不算交互通过**）/ 启动层（`runServer` 到 `Done`，**不证明交易路径正确**）/ 交互层。
- 每批交付：改动文件清单、测试原始输出、可区分版本的开发包 + sha256；**不把新增辅助类当一批交付**。
- 受阻时报告**具体失败调用 / 编译错误 / 缺失接口**，不写"需要先读文件"。
- `-Pwip=false` 已不可用（1.2.0 触及 shipped 文件与 mixin 配置）；1.1.x 只能从无 1.2.0 文件的树切。
- 开发环境锚点自检假报（`class_3218` vs yarn 名）不是回归，锚点验证须在生产实例。
- 待办（本批不做）：伙伴权限接入、四种绑定入口、降级撤销任务、配置迁移执行。

## 四之二、网络 API 实际形态（已查证，下次直接照写，不必再探索）

**载荷记录**（照抄 `net/RequestStatePayload.java` / `AffinityDataPayload.java` 的形态）：

```java
public record XxxPayload(UUID uuid, int a, boolean b) implements CustomPayload {
    public static final CustomPayload.Id<XxxPayload> ID =
            new CustomPayload.Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "xxx"));
    public static final PacketCodec<RegistryByteBuf, XxxPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.UUID, XxxPayload::uuid,
            PacketCodecs.VAR_INT, XxxPayload::a,
            PacketCodecs.BOOLEAN, XxxPayload::b,
            XxxPayload::new);
    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
```
可用编解码器：`PacketCodecs.VAR_INT` / `VAR_LONG` / `BOOLEAN` / `string(n)` / `UUID`；无字段载荷用
`PacketCodec.unit(new XxxPayload())`（见 `BreakFreePayload`）。

**服务端注册**（`net/AffinityNetworking.java` 第 38–45 行是注册区，第 50–63 行是接收区）：

```java
PayloadTypeRegistry.playS2C().register(XxxPayload.ID, XxxPayload.CODEC);   // 服务端→客户端
ServerPlayNetworking.canSend(player, XxxPayload.ID);                      // 发送前的能力检查（项目惯例）
ServerPlayNetworking.send(player, payload);
ServerPlayNetworking.registerGlobalReceiver(YyyPayload.ID, (payload, context) -> { ... });  // C2S
```
- **C2S（绑定请求）**走 `playC2S().register` + `registerGlobalReceiver`；**S2C（状态）**走 `playS2C().register` + `send`。
- 服务端类不得引用客户端专用类：客户端接收写在 `client/` 下（现有 `AffinityClientNetworking` 即为该侧入口）。
- 发送前一律先 `canSend`（项目现有 4 处都是这个写法）。

## 五、下次开工第一条命令

```powershell
# 1) 确认起点
Select-String -Path gradle.properties -Pattern '^mod_version'      # 期望 1.2.0-dev.2
# 2) 确认已写类仍在
Test-Path src\main\java\com\nonid\affinity\service\FreeTradeStatus.java, src\main\java\com\nonid\affinity\service\RelationshipTiers.java
# 3) 编译基线
& 'I:\DSH\work\tools\gradle-9.2.0\bin\gradle.bat' --offline --console=plain --no-daemon compileJava
```
