# GTNH-Dumper
 Dump data for GTNH wiki

 Special thanks : https://github.com/GTNewHorizons/ExampleMod1.7.10

## 维度与星系参数

`gtnhdimension.json` 合并原有 GTNEI 条目和 Forge 当前注册表中尚未覆盖的所有维度（包括 Random Things）。
外层统一使用 `key` 标识记录：旧条目沿用原缩写，新条目使用 `dim:<ID>`，冲突时添加 `forge:` 前缀。
追加条目按维度 ID 排序。尚未在当前会话注册的动态维度不在导出范围内。

GTNEI 专有数据统一放在可选的 `gtneiInfo` 对象中：`abbreviatedName`、`internalName`、`fullName`、
`originalName`（英文名）、`localizedName`（当前语言名）、`tier`（保留数值类型）和 `stoneTypes`（矿石宿主岩石类型列表）。
这些字段不再在外层重复；没有 GTNEI 资料的维度省略整个 `gtneiInfo`，不生成假缩写、等级或岩石数据。

仍支持 List/Map 两种格式，但结构已更新：List 格式为 `{"dimensions": [...]}`（旧容器名为 `abbreviatedName`），
Map 格式仍以原有键值索引记录。外层 `key` 取代原来的 `abbreviatedName`；读取旧字段的程序需改用 `gtneiInfo.<字段>`。
外层通用信息包括：

- `dimensionId`、`providerTypeId`、`providerClass`：实际注册的维度 ID、Provider 类型 ID 和实现类。
  同一 Provider 类型可以供多个维度使用；这些 ID 使用本次整合包及存档的注册值。
- `dimensionName`、`saveFolder`：Provider 返回的名称和存档子目录。主世界默认目录为空，JSON 省略该字段。
- `movementFactor`：坐标换算系数，默认传送规则为目标 X/Z = 原 X/Z × 原维度系数 ÷ 目标维度系数。
- `canRespawnHere`、`isSurfaceWorld`、`isHellWorld`、`hasNoSky`：重生、地表及环境标志。
  重生标志不等于完整的床使用规则；`hasNoSky` 指天空光照标志，不代表没有天空画面。
- `height`、`actualHeight`、`averageGroundLevel`、`horizon`、`cloudHeight`：Provider 报告的高度参数，
  不是地形扫描结果，也不把 `actualHeight` 视为建筑高度上限。
- `shouldLoadSpawn`：Forge Provider 的出生区域加载设置，不表示全部区块常驻。

维度从 Forge 当前注册表匹配，优先使用 Provider 名称及天体内部标识，必要时使用英文名称。
原有条目的 `dimensionLookup` 为 `matched`、`unmatched` 或 `ambiguous`；后两者附带 `candidateDimensionIds`，
不猜测 `dimensionId`。新追加的 Forge 条目标记为 `registered`，直接使用注册 ID，同名维度分别保留。
Provider 创建失败也保留注册记录，以 `providerState: "unavailable"` 及 `readErrors` 标明原因。
NEI 中的虚拟地点不一定有独立的实际维度。

导出不会创建、热加载世界，也不会把其他世界绑定到待导出的 Provider。
`providerState: "loaded"` 表示使用已绑定世界的 Provider；`"unbound"` 表示新建的未绑定实例。
未绑定实例仅尝试读取名称、存档目录、坐标比例、重生、地表、`height` 及星系接口参数。
环境字段和其余高度字段需要正确的世界初始化，未加载时省略，避免输出下界等维度的错误默认值。
模组的其他 getter 也可能依赖世界；读取失败只省略该字段，并在对应对象的 `readErrors` 记录字段及异常类型。
零值、负值和 `false` 原样保留。

星系维度额外包含 `galacticSpaceInfo`，普通维度省略此对象：

- `celestialBody`：内部名、翻译键、当前语言名、类型、可达性、火箭等级要求、空间站许可、
  大气成分、图标路径、相对大小、轨道距离／周期／相位、母天体和恒星系信息。
  `tierRequired` 与 `gtneiInfo.tier` 分开保存；轨道参数是游戏相对值，大气列表不表示浓度。
- `gravity`、`fallDamageModifier`、`thermalLevelModifier`、`windLevel`、`hasBreathableAtmosphere`、
  `soundVolReductionAmount`、`meteorFrequency`：环境参数。重力和热环境等是原始游戏参数，非 SI 单位或摄氏温度。
- `fuelUsageMultiplier`、`netherPortalsOperational`、`exitHeight`：航行及传送参数。
- `solarSize`、`solarEnergyMultiplier`、`dayLengthTicks`、`canRainOrSnow`、`hasSunset`：日照和天气参数。
- 支持对应扩展接口时，补充 `atmosphericPressure`、`solarRadiation`、`lightningStormFrequency`。
  不支持的属性省略；`providerState` 同样适用于此对象，未绑定实例读取的值应结合实际导出核对。

本项仍不支持按 Mod 筛选。运行 `gradlew checkDimensionExport` 可检查维度匹配、未初始化字段、异常隔离和星系结构。

## 板材切割机数据

板材切割机从真实的 `cutterRecipes` 配方池读取数据，按物品输入、输出和原始
`sourceMod` 分组。物品比较沿用 NEI 的规则，包含数量和 NBT；不同来源分别导出。
例如锝杆切割分别保留 BartWorks 的 4 EU/t 配方和 GT++ 的 120 EU/t 配方。

组内沿用 NEI 的合并规则，`inputFluids` 的首个槽位保存切削液候选列表，
每个候选包含实际用量、概率及 NBT。候选顺序与游戏一致，供 Wiki 流体槽轮播。
`fluidDurations` 数组按候选顺序保存每项耗时（tick），同种流体的不同用量分别保留。
界面固定显示斜杠分隔的时间列表。
旧记录继续使用自身的流体和耗时。

## 魔导手册（Thaumonomicon）导出

NEI 选项 → 工具 → 数据导出 → GTNH Dumper 里的 **TC4 Thaumonomicon** 一次导出整本手册，
写入 `tc4thaumonomicon.json`，并把用到的贴图复制到 `tc4thaumonomicon-assets/<域名>/<路径>`。
自动导出（`researchOnly` 或全量）也会生成它。它取代了原来的 `tc4research.json`，包含其全部字段（同名同值），
原研究导出已移除。

顶层分三段，都是按注册顺序排列的映射：

- `categories`（键为分类 key）：`Name`、`Order`、`Icon`、`Background`（贴图 id）、
  `MinColumn/MinRow/MaxColumn/MaxRow`（浏览器滚动范围）、`ResearchCount`。
- `aspects`（键为要素 tag，如 `ignis`）：`Name`、`Description`、`Color`、`IsPrimal`、
  `Components`（合成它的两个 tag，基础要素没有）、`Image`、`Blend`。
- `researches`（键为研究 key）：
  - 位置与外观：`Category`（本地化分类名，同旧文件）、`CategoryKey`（分类 key）、`Column`、`Row`（手册网格坐标）、`Icon`（物品）或 `IconResource`（贴图）、
    `IsSpecial/IsRound/IsSecondary/IsStub/IsVirtual/IsConcealed/IsHidden/IsLost/IsAutoUnlock`。
  - 依赖：`Parents`、`ParentsHidden`、`Siblings`。
  - 研究内容：`Aspects`（所需要素及数量）、`Complexity`、`Warp`、`Description`。
  - 触发：`ItemTriggers/EntityTriggers/AspectTriggers` 为本地化名称（与旧文件一致），
    `ItemTriggerItems/EntityTriggerIds/AspectTriggerTags` 为对应的物品、实体 ID、要素 tag；
    `KillerTriggers` 为 Automagy 的击杀线索。
  - `Pages`：手册页面，每页 `{"type": ...}`。
    `text`/`text_concealed`/`image`/`aspects` 为文字、条件文字、插图、要素列表；
    `normal_crafting`/`arcane_crafting`/`crucible_crafting`/`infusion_crafting`/`infusion_enchantment`
    的 `recipes` 数组为同页轮播的各个配方；`compound_crafting` 为结构页，`blocks` 给出每块的 x/y/z；
    `smelting` 为熔炼。物品槽沿用配方导出的格式（物品、候选数组、`#矿辞`，空槽为 null）。
    无法识别的页面或配方输出 `unsupported` 并记录类名。
  - `sourceMod`：研究的注册来源 Mod。

页面文字保持 Thaumcraft 原样：`<BR>` 换行，`<LINE>` 分隔线，
`<IMG>域名:路径:u:v:宽:高:缩放</IMG>` 插图（引用的贴图已一并复制），`§` 为格式码。文字使用游戏当前语言。
启用 Mod 筛选时只导出该 Mod 的研究和它们所在的分类，不导出要素。

## 按 Mod 导出

在游戏内打开 **NEI 选项 → 工具 → 数据导出 → GTNH Dumper**，在 **Mod 筛选 / Mod filter**
输入已加载 Mod 的 ID（如 `gregtech`）或完整显示名称，然后点击对应导出按钮。
不区分大小写，忽略首尾空格；清空输入即可恢复全量导出。设置使用 NEI 的全局/世界配置并自动保存。
未知或重名的 Mod 会提示错误；重名时请使用 ID。

筛选结果保存到 `dumps/filtered/<modid>/`，全量导出仍使用 `dumps/`。各个 Mod 的结果互不混合。

- 物品、图标和矿辞成员：普通物品按注册所属 Mod 筛选，GT 机器按 metadata 对应的
  MetaTileEntity 实现类所属 Mod 筛选，保留所有 metadata/NBT 变体。
  物品和图标还保留命中配方引用的跨 Mod NBT 变体，例如附属装配线使用的 GregTech 研究闪存；
  这些记录保持真实注册归属。普通的跨 Mod 原料仍由基础包物品库提供。
- 流体：按 Forge 记录的流体注册 Mod 筛选，包括没有对应方块的流体。
- GT 材料：按导出数据中的材料来源字段筛选；GT 结构沿用控制器物品的上述归属规则。
  因此附属 Mod 共用 `gregtech:gt.blockmachines` 时，仍可归属到提供具体 MetaTileEntity
  实现类的 Mod；无法可靠解析实现类归属时回退到物品注册 Mod。
- 配方：**原始配方来源属于所选 Mod，或至少一个物品产物属于所选 Mod**，满足任一条件即导出。
  产物归属沿用物品导出的规则（包括 GT 附属机器的 metadata 归属），支持概率产物和候选产物。
  仅原料、催化剂或流体产物属于该 Mod 不会触发物品产物匹配。
  原始来源目前使用 `GTRecipe.owners` 的第一条记录。
  来源表示最初注册该配方的 Mod。
  GTNL 延迟到 tick 注册的配方可能被 FML 误记为 GregTech；开启 `NEIRecipeOwnerStackTrace=true`
  后，导出器会对首次调用栈中明确经过 GTNL builder 和延迟加载器的配方恢复来源。
  后续修改的调用栈以及直接复制配方的调用栈不会用于覆盖原始来源。
  匹配后保留完整配方，包括其他 Mod 的原料和产物；同时命中两条规则不会重复导出。
  有来源记录时保留真实 `sourceMod`，不会因产物匹配而改写来源。
  筛选先检查原始产物，只有命中的配方才转换输入、提取说明和流体槽并序列化；
  NEI 处理器仍需加载并遍历原始配方列表。物品归属判断在本次处理器导出中按物品和 metadata 缓存。
  配方列表为空的分类不写文件、不列入导出索引；同一路径的旧文件会删除，全量导出也遵循此规则。
- 神秘时代研究：在研究成功注册时记录 FML 生命周期事件的 Mod ID，导出为 `sourceMod`。
  同一分类可以包含多个 Mod 的研究；修改已注册研究或重复注册保留首次来源。
  生命周期上下文缺失时，全量导出保留该研究并省略 `sourceMod`，Mod 筛选跳过该项。

GT 配方需要在加载配方前开启 `config/GregTech/Client.cfg` 的 `NEIRecipeOwner=true`，修改后重启游戏。
运行时打开该选项无法补回此前丢失的来源记录。缺少来源的 GT 配方、普通 NEI 配方以及 EOH/EEC
等缺少来源记录的自定义配方仍可按物品产物匹配；来源匹配的限制会在 `recipes_filter_report.json`
和游戏提示中说明。清空筛选仍可全量导出这些配方。

TiC 材料及维度等没有可靠逐项 Mod 归属的数据，在开启筛选时会提示并停止该项导出。

研究来源捕获使用 UniMixins 的延迟加载入口，仅在安装 Thaumcraft 时启用；运行 Dumper 需安装 UniMixins。

## 配方专用物品变体

从 `GTRecipe` 转换的配方会导出布尔字段 `nbtSensitive`，原样保留 `isNBTSensitive` 的 `true` 或 `false`。
EOH 等自定义配方及其他无法确定该标志的配方省略此字段，不默认填 `false`。
该字段表示 GT 的配方级 NBT 匹配标志；`false` 不排除特殊物品自身的 NBT 检查，输入和产物的 NBT 仍保留。

客户端首次导出物品信息或图标时，会遍历已支持的配方处理器，收集输入、产物、矿辞压缩前的备选物品、催化物及其他展示物品，补充创造物品栏中的记录。每个物品保留 NBT，数量归一为 1，沿用现有 JSON key、short key 和图片命名规则。

完成的扫描结果供本次客户端会话后续导出复用。扫描失败会记入日志，并在下次物品或图标导出时重试；成功扫描后修改配方，需要重启客户端以更新结果。

扫描会遍历全部配方处理器，再按物品所属 Mod 筛选结果，其中 GT 机器采用前述实现类归属规则。配方导出本身按前述来源或物品产物规则筛选。

命中配方中的非空 NBT 身份按注册键、metadata 和结构化 NBT 精确匹配，并补入筛选后的物品与图标。
切换 Mod 筛选会重建配方依赖范围，避免沿用另一个附属的变体；未命中配方的跨 Mod 变体不会因此加入。
运行 `gradlew checkRecipeNbtDependencies checkRecipeItemVariants` 可验证精确 NBT 匹配与变体扫描。

日志包含扫描汇总和失败项，便于检查当前整合包中各处理器提供的物品记录。

## 一次性自动进档导出

首次启动会在游戏目录生成 `config/gtnhdumper-autorun.json`，默认 `enabled: false`。
需要导出时将 `enabled` 改为 `true`，然后正常启动客户端。也可以提前创建该文件：

```json
{
  "enabled": true,
  "outputMode": "latest",
  "noImages": true,
  "modFilter": "",
  "keepWorld": false,
  "exitGame": true
}
```

上例适合反复更新配方数据。仅写 `{"enabled": true}` 时使用时间戳目录，同样默认跳过图标导出。
自动流程由模组在客户端内执行。
启动时先将 `enabled` 改为 `false`，防止崩溃后重复运行；下次运行需手动重新开启。
到主菜单后自动创建本次专用的 `Dumper-<runId>` 创造模式超平坦存档，等待 NEI 与神秘研究同步，
逐项导出到所选输出目录。`request.json` 留存本次参数，`status.json` 记录进度、耗时、筛选范围和跳过的任务。
每次仍生成独立的运行 ID 和存档名，固定输出目录只复用导出文件。

### 输出目录与图片

| 参数 | 默认值 | 行为 |
| --- | --- | --- |
| `outputMode` | `"timestamp"` | 每次写入 `dumper-runs/<runId>/dumps/`；改为 `"latest"` 后重复写入 `dumper-runs/latest/dumps/` |
| `noImages` | `true` | 跳过物品图标任务；改为 `false` 时导出图标及动画 |
| `modFilter` | `""` | 全量导出；填入已加载 Mod 的 ID 或完整名称后按前述来源规则筛选 |
| `tasks` | 无（全量） | 只运行列出的任务，如 `["gtnhdimension","tc4thaumonomicon"]`；见下方“只导出部分数据” |

`latest` 在世界和 NEI 准备完成后更新本次范围的数据。旧配方目录会先清除，
因此附属更新时删除的配方也会从结果中移除。全量与各 Mod 的结果分别保存；
例如 `modFilter: "gregtech"` 写入 `dumper-runs/latest/filtered/gregtech/dumps/`，
对应的请求与状态也保存在这个 Mod 的目录中。
时间戳模式的筛选数据写入 `dumper-runs/<runId>/dumps/filtered/<modid>/`，请求与状态保存在 `<runId>/` 下。

`noImages: true` 保留该目录中的 `icons/`、`itemicon.json` 与图片重定向文件。
首次只导出数据时得到配方、物品等 JSON；图片及动画映射可以沿用已有图标导出，
需要补充新物品或更新纹理时设置 `noImages: false`。
图标导出仍使用最大 PNG 压缩。`imagesOnly` 明确请求捕获素材，优先于 `noImages`。

#### 图标缓存、像素哈希与渲染稳定性

- **并行编码**：渲染仍在客户端线程，PNG 编码交给后台线程，默认 CPU 核数减 2（最多 16）。
  待编码像素按字节限额排队，默认 512 MB。
- **内容缓存**：每张图按像素计算 SHA-1，编码结果存进 `.minecraft/dumper-cache/png/<前两位>/<哈希>.png`，
  输出目录里的文件是指向缓存的硬链接（不支持时复制）。同一实例再次导出时，像素没变的图片不再编码，
  `icon-performance.json` 的 `pngCacheHits` 记录命中数。缓存可以随时删除，下次导出重新生成。
  不要原地修改输出目录里的 PNG，否则会改坏缓存；需要修改时先复制。
- **像素哈希清单**：`icon-hashes.json` 的 `images` 把每个 PNG 文件名映射到像素哈希
  （`sha1("宽:高:" + 按行的 ARGB 大端字节)`）。哈希只看像素，与 PNG 编码无关；工具链用它比较两次导出。
- **动画帧条上限**：帧条长度是所有动画帧数的最小公倍数，可能远超内存。原始帧条超过 256 MB 的动画
  只导出静态图，记在 `icon-performance.json` 的 `oversizedAnimations`。海晶石系列（帧条可达 1.65 GB）固定导出静态图。
- **固定渲染时钟**：渲染图标期间，`Minecraft.getSystemTime()`（附魔光效、随时间变色）、世界时间与渲染插值
  （刷怪笼生物、旋转模型）、玩家 tick 都固定为同一值，结束后恢复，`frozenRenderClock` 记录所用的值。
  图集里所有动画贴图也重置到第一帧（`resetAnimatedSprites` 记录数量）：流体单元、水晶瓶、外观板等画的是流体贴图，
  不是物品自己的图标，否则会停在导出开始时图集恰好显示的那一帧（Hodgepodge 只推进最近画过的贴图），每次导出都可能不同。
  模组自己的动画计数器也固定（GT `mAnimationTick`、植物魔法 `ticksInGame`、神秘工匠 `elapsedTicks`，`frozenCounters` 记录数量）；
  直接读系统时间的物品渲染器由 `mixins/clock/` 改读冻结时钟（林业蝴蝶与蜜蜂、龙研地球、Gadomancy 节点与使魔、
  巫术小玩意原初手套、GT 故障特效），只在导出图标期间生效；渲染时新建的生物（生物灵魂、刷怪笼、奖杯）随机数和 UUID 按类名固定。
  直接读系统时间或随机数的渲染器仍会每次不同，由工具链比较两次导出的 `icon-hashes.json` 记为不稳定图标。

这些设置可以用 JVM 参数调整：

| JVM 参数 | 默认值 | 作用 |
| --- | --- | --- |
| `-Dgtnhdumper.pngWorkers=N` | CPU 核数 − 2，最多 16 | PNG 编码线程数；`0` 表示在渲染线程上同步编码 |
| `-Dgtnhdumper.pngQueueMB=N` | `512` | 待编码像素的内存上限 |
| `-Dgtnhdumper.pngCache=false` | `true` | 关闭内容缓存，直接写 PNG |
| `-Dgtnhdumper.maxAnimationMB=N` | `256` | 单个动画帧条的原始大小上限 |

整合包实例建议最大内存设为 8 GB 或以上。

### 只导出部分数据

反复调试某一份数据时，用 `tasks` 只跑需要的任务，例如只看维度和魔导手册：

```json
{"enabled": true, "tasks": ["gtnhdimension", "tc4thaumonomicon"], "outputMode": "latest", "exitGame": true}
```

可用的任务名：`itemicon`、`advitempanel`、`recipes`、`oreDictionary`、`fluid`、`gtmaterial`、`gtnhdimension`、
`ticmaterial`、`tc4thaumonomicon`、`betterquesting`（任务书源文件）。写错名字会在开始前报错并列出可用名字。

- 图标只在列出 `itemicon` 时导出，此时不看 `noImages`。
- 配方物品扫描（约 2～3 分钟）只在列出 `itemicon` 或 `advitempanel` 时运行，其他任务不需要它。
- `latest` 模式下只清除所选任务的旧输出，同目录里其他数据保留，便于和工具链配合反复迭代。
- 不能与 `researchOnly`、`smoke`、`imagesOnly` 同时使用（`tasks` 已覆盖它们的用途）。
- `status.json` 的 `tasks` 记录本次选择。启动游戏和准备世界仍需约 2 分钟。

### 只更新一个附属

```json
{
  "enabled": true,
  "outputMode": "latest",
  "modFilter": "TwistSpaceTechnology",
  "noImages": true,
  "exitGame": true
}
```

筛选复用手动导出的归属规则，保留命中配方中的全部跨 Mod 原料与产物。
物品与流体 JSON 只包含该 Mod 的条目，处理附属数据时与基础包的物品库组合使用。
维度等缺少逐项归属的数据会列入 `status.json` 的 `skipped`，其余任务继续完成。
配方来源缺失的分类另见 `recipes_filter_report.json`；GT 配方的来源采集需要提前开启 `NEIRecipeOwner`。
`modFilter` 只控制本次自动会话，手动 NEI 筛选设置保持原值。

`smoke` 使用的 TiC 材料缺少逐项归属，运行该模式时使用空 `modFilter`。配置组合错误会在创建存档、清理旧数据前报告。

### 存档与结束流程

导出后等待集成服务器保存、停止并刷新存档缓存。成功时按 `keepWorld` 保留或删除本次创建的存档；
导出或停止阶段报错时保留存档。`exitGame: true` 随后退出客户端，`false` 返回主菜单。
结束时恢复并保存运行前的难度和失焦暂停设置，准备或导出失败时也执行恢复。
清理失败会报告 `FAILED`，保留剩余内容。`EXPORTED` 表示导出任务及存档退出流程完成；随后可用数据处理工具检查物品关联和图片完整性。

可选参数：`runId`（1–101 位字母、数字、下划线或连字符，首字符必须是字母或数字，默认自动生成）、
`seed`（默认 `290002`）、`smoke: true`（仅导出 TiC 材料以检查生命周期）。
时间戳模式的 `runId` 必须唯一，`latest` 为固定输出目录的保留名称。

设置 `noImages: false` 后可以使用以下图标复用参数。
`imageMetadata` 接受先前图标 JSON 映射文件路径，导入名称、帧数及尺寸等映射并跳过图标渲染。
使用此参数时，请将对应 PNG 配齐到输出目录并核对图片。
`imageResume` 接受包含 `directory` 和 `images` 的 JSON 清单：`images` 将 PNG 文件名映射到 `[宽, 高]`，
`directory` 指向图片目录。导出器按清单决定可复用项，将图片硬链接或拷贝到本次输出，缺失清单项继续渲染。
复用前应检查 PNG 内容，并确认映射或清单与当前整合包及导出设置匹配。
状态中的 `imagesReused` 记录是否启用了上述任一复用参数。

`managedByRunner: true` 是可选外部调度接口：模组仍负责创建世界、导出和安全退出存档，
将世界保留给外部调度器验证、清理。调度器负责启动客户端、验证 PNG 和核对导出数据。
外部调度器等待进程退出时须保持 `exitGame: true`。

配方物品的附加提示使用 `tooltip` 字段，通过 `RecipeItem.withTooltip(...)` 追加。
普通配方物品序列化为以 `<br>` 连接的字符串；专用记录和流体槽也可保留提示列表。
它与物品数据库的 `tooltips` 字段分开。神秘时代配方的 `research` 保持列表形式。

### 只更新研究数据

设置 `researchOnly: true` 只运行魔导手册导出，支持同时指定 `modFilter`。
它使用已注册的研究表，跳过物品扫描、图片导出与玩家研究解锁；仍沿用自动建档、退出和存档清理流程。
此模式与 `imagesOnly`、`smoke` 互斥，要求安装 Thaumcraft。
`outputMode: "latest"` 只清理并重新写入 `tc4thaumonomicon.json` 及其贴图，保留其他导出数据。

```json
{"enabled":true,"researchOnly":true,"noImages":true,"exitGame":true}
```

### 定向图片与时钟动画

`imagesOnly: true` 仅运行配方物品扫描和图标导出，不导出其他数据库。
可配合 `imageKeys` 指定完整 `shortKey` 数组；未找到的 key 会报告失败。
例如 `{"enabled":true,"imagesOnly":true,"imageKeys":["minecraft:diamond"]}`。
`imageDiagnostics: true` 在 `icon-capture-report.json` 记录渲染器、非透明像素数、NBT 和 FBO 前后的 GL 状态，供定向图片诊断使用。

GT 两种超立方体由客户端 RenderTick 同时按 50 ms 目标间隔采样 120 帧，动态帧为 64×64，静态图另以 256×256 渲染。
实际采样时间记录在诊断文件中；低帧率会影响间隔。动画仍使用现有 `frameCount`、`size` 和 `_dynamic.png` 横向帧条格式，PNG 使用最大压缩。
手动 NEI 导出及自动会话均等待采样和 PNG 写入完成后才发布映射或完成提示。
这两项时钟动画重新采样，不从 `imageResume` 复用旧帧条；`imageMetadata` 仍只导入已有映射并跳过采样。

特殊物品的选择规则和参数集中在 `ItemIconRenderProfile`，按注册名与 damage 选择，同一物品的 NBT 变体使用相同配置。
定时采样与关闭面剔除分别配置；超立方体在绘制期间关闭剔除，以保留 Angelica 将宽线展开成的三角形，随后恢复原状态。
普通物品保留现有静态图和纹理动画导出流程。当前所有定时采样物品共用一组采样参数。

## 任务书源文件

全量自动导出时，如果加载了 BetterQuesting，会把任务书源文件复制到 `dumps/betterquesting/`，
让工具链从同一份导出读取任务数据：

- `DefaultQuests/`：`config/betterquesting/DefaultQuests/` 原样复制（任务、任务线、`QuestLinesOrder.txt`）。
- `lang/`：整合包翻译放在 `config/txloader/forceload/betterquesting/lang/` 的各语言文件。

每次导出先清空该目录再复制；按 Mod 导出时跳过。

## 重复导出的一致性

同一整合包连续两次全量导出，数据文件应逐字节一致，工具链才能只上传真正变化的页面。
游戏里不少数据按对象哈希、注册时机或系统时间排列，导出器在不改变数据含义的前提下固定这些顺序：

- 配方顺序：GT 配方按 NEI 的排序规则（电压、耗时等）输出，规则相同的按配方 JSON 文本排序；
  熔炉、合成台、林业机器、火箭工作台等来源本身是 HashMap 的处理器按配方 JSON 文本排序
  （清单见 `StableOrder.UNORDERED_RECIPE_SOURCES`）。其他处理器保留 NEI 给出的顺序。
- 槽位候选：同一槽位的候选物品按物品的稳定次序（所属模组加载顺序、注册名、damage、NBT）排序。不用数字 ID：
  按 HashMap 注册物品的模组（如 ironchestminecarts、Thaumic Tinkerer）每次分到的 ID 不同。GT 输入保留配方自身登记的物品在首位，
  其余矿辞等价物排在后面。流体候选不重排（板材切割机的 `fluidDurations` 与它一一对应）。
- 产物表：林业离心机、蜜蜂产物按概率从高到低、同概率按 JSON 文本；鸿蒙之眼产物按数量从大到小、同量按物品次序。
- 矿辞（`oreDictionary.json`）按矿辞名排序，成员按物品次序；GT++ 材料按名称、匠魂材料按 ID 输出；
  神秘时代要素表按要素注册顺序（基础要素在前）；“包含要素的物品”按要素注册顺序分组。
- NEI 处理器的轮播计数从 0 开始（原为系统时间），微型方块材料、燃料演示配方等示例固定；
  燃料页的演示熔炼配方取原料在该次序中最靠前的一条。
- MobsInfo 生成生物表时，新建实体的随机数按类名播种（与图标导出相同），随机的首领名字和史莱姆体型（血量）固定。
- AE2 外观板列表只在第一次调用时按当时已注册的方块生成；有些启动里这次调用发生在最后几个模组注册方块之前，
  会少约 280 个外观板（物品列表与外观板配方都受影响）。导出器在加载完成时清空并重建这份列表。
- 物品列表（`advitempanel.json` 等）按同样的物品次序排列，配方扫描补充的 NBT 变体排在所属物品旁边。
- 配方目录名使用 FML 的模组 ID：NEI 的处理器信息有时把林业登记成 `forestry`，有时是 `Forestry`。

仍会变化的内容来自模组在启动时随机或按懒加载状态生成的数据，导出器如实记录：

- Gadomancy：精华压缩器的要素数量，粘性罐、伪装盔甲研究页的示例配方。
- Thaumic Tinkerer 灵宝研究的隐藏前置：取的是设置页面那一刻已注册的研究。
- 神秘时代为没有登记要素的物品按配方推算要素，结果依赖推算顺序（约百个物品的要素值，以及“包含要素的物品”分页）。
- GT 离心机里由林业产物表生成的蜂巢配方，产物槽位顺序；林业离心机 `gt.comb:81` 的神秘碎片种类。
- 鸿蒙之眼个别产物数量的末位（TecTech 启动时累加浮点数）。
- MobsInfo 里 EnderZoo 凋灵女巫的生成群系（暮色森林群系时有时无）。
- BuildCraft 集成台的示例输出（门的扩展）。
- 物品说明：GT++ 部分零件的“材料类型”，腐化木炭的燃烧时间，Extra Utilities QED 的名字，林业电子管说明里各段的顺序。

## 已移除的导出

以下导出没有被数据处理工具链使用，已经移除：GT 矿脉、GT 小矿、GT 地下流体（维基改由
`gtneioreplugin` 的 NEI 配方重建）、GT 结构、任务与任务线（任务由 BetterQuesting 的任务文件处理）、
旧的神秘时代研究表 `tc4research.json`（由魔导手册导出取代），以及自动导出的 `layoutsOnly` NEI 布局模式。
自动导出的 `latest` 全量刷新会删除这些旧文件，避免误用过期数据。
