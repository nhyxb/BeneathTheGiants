# Minecraft NeoForge 1.21.1 Mod 开发环境

这是基于 NeoForge 官方 **1.21.1 ModDevGradle MDK** 初始化的 Java 模组工程，包含可直接导入 IDE 的示例 mod、客户端/服务端运行配置和 Gradle Wrapper。

## 工具链

- Minecraft：1.21.1
- NeoForge：21.1.252
- Java toolchain：21（Minecraft 1.21.1 的目标 Java 版本）
- ModDevGradle：2.0.148
- Gradle Wrapper：9.2.1；无需单独安装 Gradle
- `settings.gradle` 已配置 Foojay resolver。若找不到本地 JDK 21，Gradle 会尝试下载并缓存对应工具链。首次构建需要能访问 Gradle、NeoForge 和 Foojay 仓库。

当前宿主机默认 Java 为 25。Gradle Wrapper 使用这个已安装的 JDK 启动构建，再通过 Java toolchain 使用 JDK 21 编译和运行 Minecraft。

## 快速开始

在此目录运行：

```bash
./gradlew build
DRI_PRIME=pci-0000_03_00_0 ./gradlew --console=plain runClient
```

当前开发机使用 `DRI_PRIME=pci-0000_03_00_0` 选择 AMD RX 6700 XT（PCI 地址 `0000:03:00.0`）运行客户端。该地址是本机配置；在其它机器上应按实际显卡 PCI 地址调整，不要写入通用 `gradle.properties`。

其它常用任务：

```bash
./gradlew runServer
./gradlew runData
./gradlew tasks
```

`runServer` 首次启动会因为 Minecraft EULA 尚未接受而退出。阅读运行目录中的 `eula.txt`，确认同意后将 `eula=false` 改成 `eula=true` 再启动。

## IDE

在 IntelliJ IDEA 或 Eclipse 中打开此目录并导入 Gradle 工程。首次同步会下载 Gradle、JDK 21（若本机没有）以及 Minecraft/NeoForge 开发依赖，可能需要一些时间和磁盘空间。

## 游戏功能与使用

以下功能已实现。最终构建和 33 项 GameTest 已通过（包括加入世界时的尺寸/AABB 缓存回归），客户端在指定独显上启动并进入本地世界；FOV、步行动画的真人视觉手感、道具真人输入、网络重登及多人客户端/服务端视觉一致性仍未人工验收。

- 玩家进入世界后会永久保持 1/28.8 尺寸倍率，站立高度约为 1/16 格；重登、死亡重生和切换维度后仍保持。玩家微型尺寸和眼高继续生效。
- `examplemod:scale_wand` 可在创意栏获取，也可在允许作弊或具有相应权限时运行 `/give @s examplemod:scale_wand`。
- 手持道具右键非玩家生物，可在正常和微型状态间切换。成功切换后使用者进入约 10 tick 冷却；冷却在该玩家的双手与其它道具堆叠间共享。道具不消耗、没有耐久损耗。
- 道具不能对玩家使用。右键非生物也不生效。目标恢复后的碰撞箱若没有足够空间，道具会拒绝恢复并提示，目标保持微型；无效或失败操作不会改变目标状态。
- 生存模式配方为紫水晶碎片、铜锭、木棍各 1 个的无序配方，合成 1 个 `scale_wand`。
- 微型实体朝墙移动时可贴墙攀爬，潜行时会停在墙上。普通大小的生物不会因此获得攀墙能力。
- wand 缩小的非玩家生物为该生物普通尺寸的 0.5 倍，保留不同种类间的原始大小差异；其碰撞箱、眼高和相应台阶高度按该尺寸调整。高度至少为受踩目标 4 倍才符合踩踏尺寸条件，因此普通体型与同种半尺寸生物之间的 2:1 高度比不足以踩踏。
- 玩家和带本 mod 微型状态的生物移动速度为普通值的 0.25；LivingEntity 步行动画只对本 mod 标记的微型实体独立处理，按局部模型尺寸归一化位移后沿用 vanilla 平滑、动画上限 1，静止时为 0，不靠提升实际移动速度加快动画，也不修改第一人称 camera bob。
- 第一人称和 F5 前/后的第三人称视野不因本 mod 自有移动速度 modifier 而收窄。校正只抵消本 mod 自己导致的 FOV 差异；原版疾跑、飞行、弓等物品、真实药水效果和用户 FOV 设置仍可改变视野。微型第三人称相机距离继续生效。
- 旧存档迁移只调整本 mod 自有旧 modifier：玩家 speed 从 `-0.65` 更新为 `-0.75`；旧版 wand 微型生物更新为普通尺寸的 0.5 倍、movement ×0.25 和半尺寸对应的 step，并幂等补齐缺失的 jump/max-health modifier。迁移保留当前绝对生命值，不再按最大生命值变化换算比例，也不删除其它模组 modifier；修复缺失 max-health modifier 后若生命值高于新上限，只裁剪至合法值。
- 友好生物受到伤害后会反击攻击者；不会主动进攻，也不会反击主人或同队成员，创造/旁观模式玩家不会成为攻击目标。体型至少大四倍的生物在行走或落下时，若脚底水平覆盖并真实踩中玩家或被本 mod 道具缩小的生物，会造成伤害；正常大小的自然幼年动物不属于踩踏目标。

玩家与联机服务器都需要安装相同版本的本 mod。Pehkui 只是尺寸与属性分离、同步及持久化设计的参考，不需要安装，也不是本 mod 的运行时依赖。

核心平衡参数如下；最终构建与 GameTest 已验证自动行为，真人视觉和联机体验仍待实际验收。

| 项目 | 参数 |
|---|---:|
| 玩家尺寸倍率 / 站立高度 | 1/28.8 / 约 1/16 格 |
| wand 生物尺寸 | 各自普通尺寸的 0.5 倍 |
| 最大生命值 | ×0.5（玩家基准 20 点时为 10 点） |
| 攻击输入伤害 | ×0.25（含武器/投射物，在目标护甲、抗性等通常减伤前） |
| 水平移动 / 跳跃 | ×0.25 / ×0.5 |
| 挖掘速度 | ×0.2（玩家） |
| 输出 / 受到击退 | ×0.25 / ×2 |
| 方块 / 实体交互距离 | 最低 1.5 格（玩家） |
| 台阶高度 | 按各实体尺寸比例缩放，最低 1/16 格 |
| 步行动画 | 局部位移按模型尺寸归一化；vanilla 平滑、最高 1，静止为 0 |
| 贴墙上升 | 0.08 格/tick；潜行时停墙 |
| 行走 / 下落踩踏 | 2 点 / `2 + min(下落距离, 8)` 点 |
| 踩踏冷却 | 每个受踩目标独立 10 tick |

攻击倍率作用于包含武器/投射物伤害的入站攻击输入，在目标护甲、抗性等减伤前生效；因此目标最终掉血不保证固定为原攻击的四分之一。踩踏表中数值是基础伤害，目标护甲和抗性等通常减伤继续生效，但踩踏不应用难度伤害缩放；踩踏可在普通受击无敌帧内造成伤害，同时保留此前的受击计时。

### 功能验证状态

2026-10-05 最终验证：`./gradlew --console=plain build` 退出码 0，`BUILD SUCCESSFUL`（650 ms），且 `compileJava` 与 `jar` 实际执行；产物为 `build/libs/examplemod-1.0.0.jar`（81,577 bytes）。`./gradlew --console=plain runGameTestServer` 退出码 0，完成 33 项 GameTest（856 ms），输出 `All 33 required tests passed`。新迁移用例检查旧 NBT 首次加入和第二次加入；缺失 modifier 的实体在 `addFreshEntity` 返回后立即达到 scale 0.5 / AABB 高度 0.7，不依赖手动刷新或等待 tick。GameTestServer 结果不等于 JUnit 通过，Gradle `test` 仍为 `NO-SOURCE`。

最终客户端 smoke 于 2026-10-05 使用 `DRI_PRIME=pci-0000_03_00_0 ./gradlew --console=plain runClient` 启动：日志确认 RX 6700 XT / OpenGL 4.6、Example Mod 1.0.0、1,291 条配方、Dev 登录并进入本地 integrated world；`LivingEntityMixin`、`client.AbstractClientPlayerMixin` 和 `client.EnderDragonRendererMixin` 均已应用，启动与进入世界期间没有阻断 ERROR/FATAL/MixinApply/InvalidInjection。客户端仍保持运行；该任务没有以 exit 0 结束，也不代表人工 FOV、F5 镜头、步频或联网测试已通过。产物 JAR 为 81,577 bytes，并包含 FOV Mixin 类及所需资源。

历史记录：更早的 24 项 GameTest 与不带 `DRI_PRIME` 的客户端启动来自之前版本；旧客户端由操作者 Ctrl+C 结束（退出码 130），清理阶段的 OpenAL `Stop: Invalid name parameter` 不影响当时的启动 smoke。该历史启动没有使用上方独显环境变量。

当前客户端 FOV（含疾跑、飞行、弓、真实药水及用户设置）、第一/第三人称镜头和步行动画仍需真人操作实测；FOV 不能由服务器 GameTest 代替。真人右键操作、视觉/碰撞直观一致性、真实网络重登及多人双端视觉同步也尚未人工验收；客户端启动 smoke 不代表这些体验通过。

## 开始编写

示例 mod 的 ID 是 `examplemod`。修改 mod 时，确保 `gradle.properties` 中的 `mod_id` 与 Java 主类 `@Mod(...)` 使用的 ID 一致。常用元信息（ID、名称、版本、Maven group）在 `gradle.properties` 中；Java 源码位于 `src/main/java`，资源位于 `src/main/resources`。
