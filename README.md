# cardboard_box

Minecraft **1.20.1 / Forge 47.x** 纸箱模组核心实现：把**任何方块**装进箱子带走，
放回时保留方块状态与方块实体数据。

## 功能

| 操作 | 结果 |
| --- | --- |
| 空纸箱 **右键** 任意方块 | 方块变成纸箱，方块状态 +（若有）方块实体 NBT 存入（消耗 1 个纸箱） |
| 装了东西的纸箱 **右键** 地面 | 还原方块并写回 NBT，返还 1 个空纸箱 |
| 纸箱方块 **左键敲击** | 立即还原内容，返还 1 个空纸箱 |
| 纸箱方块 **潜行 + 右键** | 收成物品，内容随 NBT 走 |
| 物品栏 tooltip | 显示里面装的方块 |

行为细节：

- **任何方块都能装**：没有方块实体的方块（泥土、石头、楼梯……）只保存方块状态；
  有方块实体的（箱子、熔炉、机器……）额外保存其完整 NBT。
- **还原时优先沿用原状态**：朝向、连接、含水等属性保持一致；目标位置无法存活时
  （例如悬空的火把）才按点击面重新计算朝向。
- **装 / 放 / 取都会触发方块更新**：拾取用 `Block.UPDATE_ALL`（邻居 + 客户端），
  放置后再显式 `updateNeighborsAt`，周围的红石、栅栏连接、上方方块的支撑判定会立刻响应。
- 带内容的纸箱不可堆叠（`getMaxStackSize = 1`），空箱可叠 64。

## 构建

需要 **JDK 17**（ForgeGradle 不支持 21/25）。

```bat
set JAVA_HOME=C:\Program Files\Java\jdk-17
gradlew.bat build
```

产物：`build/libs/cardboardbox-1.20.1-0.1.2.jar`

其他任务：`gradlew.bat runClient`、`gradlew.bat runServer`。

> 报 `Unsupported class file major version` 就是 Gradle 用了高版本 JDK；
> 也可解开 `gradle.properties` 里的 `org.gradle.java.home`。

## 结构

```
src/main/java/com/tinycarton/cardboardbox/
├── CardboardBox.java        注册方块 / 物品 / 方块实体，创造栏，配置装载
├── box/BoxBlock.java          左键还原、潜行右键收物品、物品化（toItemStack）
├── box/BoxBlockEntity.java    只存 storedTile + tileData
├── box/BoxBlockItem.java      核心：装方块 / 放方块
├── handler/Handler.java       单方块钩子：place / postPlace / loadData / save
├── handler/HandlerManager.java 黑名单 + handler 注册表 + canPickUp 判定
├── handler/CanPickUpResult.java 判定结果枚举
├── mods/ModHandler.java       按 mod 分组的适配层，自动生成 BE 黑名单配置
└── mods/VanillaHandler.java   原版黑名单 + 箱子朝向修正
src/main/resources/
├── META-INF/mods.toml
├── assets/cardboardbox/     模型 / 方块状态 / 贴图 / 5 种语言
└── data/                      配方 + 进度
```

## 扩展

给某个 mod 的方块做适配（软依赖，只在目标 mod 加载时生效）：

```java
public class CreateHandler extends ModHandler {
    private ForgeConfigSpec.BooleanValue allowShafts;

    @Override
    public void build(ForgeConfigSpec.Builder b) {
        allowShafts = b.define("Create.allowShafts", true);
    }

    @Override
    public void load(ForgeConfigSpec config) {
        if (!allowShafts.get()) {
            HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.SHAFT);
        }
        HandlerManager.INSTANCE.registerHandler(SomeModBlocks.MACHINE.get(), new Handler() {
            @Override
            public void postPlaceBlock(Player player, Level level, BlockPos pos, InteractionHand hand,
                                       Direction dir, float hx, float hy, float hz, BlockState state, CompoundTag data) {
                // 重新绑定多方块、补回 owner、刷新 capability……
            }
        });
    }
}
```

在 `CardboardBox` 构造器里登记：`ModHandler.modSupportHandlerMap.put("create", CreateHandler.class);`

配置 `config/cardboardbox-common.toml` 会自动列出所有已注册的方块实体类型（含其他 mod），
名字带 `cable` / `wire` / `pipe` / `tube` / `conduit` / `channel` 的默认禁用，其余默认允许；
`BlackListSettings.BlackListMobSpawners` 控制刷怪笼。方块层黑名单见 `VanillaHandler`。

## 本分支：NeoForge 实现

`neoforge-1.20.1` 分支使用 NeoGradle + NeoForge **47.1.106**（Minecraft 1.20.1）：

| 关注点 | 本分支的实现 |
| --- | --- |
| 构建 | `net.neoforged.gradle.userdev` 7.0.192（Gradle 8.6 / JDK 17） |
| 依赖 | `net.neoforged:forge:1.20.1-47.1.106`（NeoForge 1.20.1 仍按 forge 坐标发布） |
| 包名 | NeoForge 1.20.1 仍是 `net.minecraftforge.*`，源码与 Forge 分支保持同一套 import |
| 运行配置 | `runs { client / server }`，服务端带 `--nogui` |
| 元数据 | `META-INF/mods.toml`，依赖 `forge [47,)`、`minecraft [1.20,1.21)`、`loaderVersion [1,)` |
| 保护类 mod 兼容 | `BlockEvent.BreakEvent` + `ForgeEventFactory.onBlockPlace`（与 Forge 同名同签名） |

与 `forge-1.20.1` 的差异集中在构建系统（NeoGradle vs ForgeGradle）；核心代码
（`box/`、`handler/`、`mods/`）两边保持一致，改动时注意同步。

## 移植到其他版本 / 加载器

核心逻辑只依赖 `Block` / `BlockEntity` / `BlockItem` 的生命周期，需要改的是注册层与事件层：

- **注册**：`DeferredRegister` → 目标加载器的注册 API；`@Mod` / `FMLCommonSetupEvent` → 对应入口。
- **配置**：`ForgeConfigSpec` → 目标加载器的配置系统（`ModHandler` / `VanillaHandler` 是唯一耦合点）。
- **保持 NBT 键不变**以兼容存档：物品 `storedItem` / `tileData`，方块 `storedTile` / `tileData`。
- **纸箱方块必须登记为该 `BlockEntityType` 的有效方块**：区块重载时会做 `isValid` 校验，
  漏登记会导致纸箱的方块实体被丢弃、内容消失。
- 方块状态以 `Block.getId` / `Block.stateById` 存 int，依赖注册表 id 顺序；若目标环境不保证顺序，
  改用 `NbtUtils.writeBlockState`（存 `Name` + `Properties`）。
- 双层方块（床、门、高花等）目前只搬运被点击的那一半，另一半会按原版规则掉落/消失。

## 许可

MIT，版权与许可声明见 [LICENSE](LICENSE)。
