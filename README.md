# cardboard_box

Minecraft **1.20.1 / Fabric** 纸箱模组核心实现：把**任何方块**装进箱子带走，
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

- **任何方块都能装**：没有方块实体的方块只保存方块状态；有方块实体的额外保存其完整 NBT。
- **还原时优先沿用原状态**：朝向、连接、含水等属性保持一致；目标位置无法存活时才按点击面重算。
- **装 / 放 / 取都会触发方块更新**：拾取用 `Block.UPDATE_ALL`，放置后显式 `updateNeighborsAt`。
- 带内容的纸箱与空箱都按默认 64 堆叠上限处理；不同 NBT 的纸箱不会互相合并
  （1.20.1 的 `Item#getMaxStackSize()` 是 final，要严格限制需要 mixin）。

## 构建

需要 **JDK 17**。

```bat
set JAVA_HOME=C:\Program Files\Java\jdk-17
gradlew.bat build
```

产物：`build/libs/cardboardbox-1.20.1-0.1.2.jar`

其他任务：`gradlew.bat runClient`、`gradlew.bat runServer`。

本分支的构建链：

| 项 | 版本 |
| --- | --- |
| Fabric Loom | 1.7.4（Gradle 8.10.2） |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.92.12+1.20.1 |
| 映射 | Mojang official（`loom.officialMojangMappings()`） |

> `maven.fabricmc.net` 在国内常需代理；可在用户级 `~/.gradle/gradle.properties` 里配置
> `systemProp.https.proxyHost` / `systemProp.https.proxyPort`，并把这些域名加入 `nonProxyHosts` 走直连：
> `libraries.minecraft.net`、`piston-data.mojang.com`、`piston-meta.mojang.com`。

## 结构

```
src/main/java/com/tinycarton/cardboardbox/
├── CardboardBox.java           ModInitializer：注册方块 / 物品 / 方块实体，创造栏，配置装载
├── box/BoxBlock.java           左键还原、潜行右键收物品、物品化（toItemStack）
├── box/BoxBlockEntity.java     只存 storedTile + tileData
├── box/BoxBlockItem.java       核心：装方块 / 放方块
├── handler/Handler.java        单方块钩子：place / postPlace / loadData / save
├── handler/HandlerManager.java 黑名单 + handler 注册表 + canPickUp 判定
├── handler/CanPickUpResult.java 判定结果枚举
├── mods/Config.java            JSON 配置（config/cardboardbox.json）
├── mods/ModHandler.java        按 mod 分组的适配层
└── mods/VanillaHandler.java    原版黑名单 + 箱子朝向修正
src/main/resources/
├── fabric.mod.json
├── assets/cardboardbox/        模型 / 方块状态 / 贴图 / 5 种语言
└── data/cardboardbox/          配方 + 进度
```

## 扩展

给某个 mod 的方块做适配（软依赖，只在目标 mod 加载时生效）：

```java
public class CreateHandler extends ModHandler {
    @Override
    public void load() {
        HandlerManager.INSTANCE.banBlockEntity(BlockEntityType.SHAFT);
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

在 `CardboardBox#onInitialize` 里登记：`ModHandler.modSupportHandlerMap.put("create", CreateHandler.class);`

配置 `config/cardboardbox.json`：

```json
{
  "blackListMobSpawners": true,
  "blockEntityBanList": {
    "minecraft:chest": false,
    "minecraft:hopper": false,
    "minecraft:beacon": true
  }
}
```

首次启动会把所有已注册的方块实体类型写进去；名字带 `cable` / `wire` / `pipe` / `tube` / `conduit` / `channel`
的默认 `true`。

## 移植到其他版本 / 加载器

核心逻辑只依赖 `Block` / `BlockEntity` / `BlockItem` 的生命周期，需要改的是注册层、事件层与配置层：

- **注册**：Fabric 直接 `Registry.register(BuiltInRegistries.X, id, value)`；方块实体用
  `FabricBlockEntityTypeBuilder.create(...).build()`。
- **配置**：本分支用 Gson 读写 `config/cardboardbox.json`（`mods/Config`），换加载器时替换这一个类即可。
- **创造栏**：`ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)`。
- **保持 NBT 键不变**以兼容存档：物品 `storedItem` / `tileData`，方块 `storedTile` / `tileData`。
- **纸箱方块必须登记为该 `BlockEntityType` 的有效方块**：区块重载时会做校验，漏登记会导致内容丢失。
- 方块状态以 `Block.getId` / `Block.stateById` 存 int，依赖注册表 id 顺序；若目标环境不保证顺序，
  改用 `NbtUtils.writeBlockState`（存 `Name` + `Properties`）。
- 双层方块（床、门、高花等）目前只搬运被点击的那一半，另一半会按原版规则掉落/消失。

## 许可

MIT，版权与许可声明见 [LICENSE](LICENSE)。
