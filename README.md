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

## 世界生成战利品

NEI Custom Diagram 的“世界生成战利品”图表导出为配方，文件为 `recipes/neicustomdiagram/worldgenloot_null.json`，
与其他配方并列。每个箱子战利品表是一条配方，没有输入，`outputItems` 是表中的全部物品。

- 数据与图表一致：Forge `ChestGenHooks` 注册表（TooMuchLoot 改写的就是它），加上图表自带的
  Roguelike Dungeons、暮色森林补充表（对应模组已加载时才有）。按英文键排序，条目顺序与注册顺序一致。
- 输出物品的 `chance` 是**单次抽取**命中该条目的概率，单位与其他配方相同（10000 为 100%），
  即 `权重 ÷ 总权重 × 10000`。`amount` 是堆叠数量上限，数量有范围时写在物品的 `tooltip`（“数量：1～3”）。
- 配方字段：`structure`（英文键，Roguelike/暮色森林表没有键，沿用图表生成的标题）、
  `structureName`（图表有本地化名时才有，缺失时省略，不拿键名冒充）、`lootSource`（`forge`/`roguelike`/`twilightforest`）、
  `rollsMin`/`rollsMax`（每个箱子的抽取次数，两端都可取到，仅 Forge 表；Forge 的 `getCount` 不含上限，已换算）、`totalWeight`（总权重）。
- 每个箱子抽取 `rollsMin`～`rollsMax` 次，每次按权重抽一个条目；同一物品可以出现在多个条目中，各自保留。
- 物品经 `RecipeItem` 构造，因此和其他配方一样会被物品变体扫描收集。
- 读取的是注册表里的原始条目，不用 `ChestGenHooks.getItems(Random)`：后者对附魔书等条目每次返回新的随机物品，
  导出会每次不同，也对不上物品库。游戏每次生成都会换新物品的条目（`getChestGenBase` 返回新对象）导出为不带随机属性的
  模板物品，`tooltip` 注明“每次生成时随机”。
- 战利品表以游戏当前状态为准：TooMuchLoot 在服务器启动时才应用自定义表，导出时机决定看到的是原版表还是自定义表。
- 按 Mod 筛选时，战利品表只要有一个物品属于所选 Mod 就导出。

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

加载了 BetterQuesting 时，扫描还会遍历任务书：任务图标、物品类任务的要求物品、物品奖励与可选奖励。
任务奖励常带专门的 NBT（改名的工具、任务用纸），配方里看不到，靠这一步进入物品与图标导出。

日志包含扫描汇总和失败项，便于检查当前整合包中各处理器提供的物品记录。

## 用脚本在 Prism 实例里导出（Windows）

`scripts/export.ps1` 把 Dumper 放进 Prism 里的 GTNH 实例，启动游戏、等导出完成并汇报结果，不用手动改配置。

1. 复制 `.env.example` 为 `.env`，至少设置 `PRISM_INSTANCE`（Prism 的 `instances` 目录下的实例文件夹名）。其余项有默认值，见文件里的注释。
2. 构建 jar：`.\gradlew build`，或给脚本加 `-Build`。
3. 运行：

```powershell
.\scripts\export.ps1                         # 全量导出：先删除 dumper-runs\latest，再重新导出
.\scripts\export.ps1 entities entityimages   # 只导出这两个任务，其余数据保留
.\scripts\export.ps1 entityimages -Entities 'Thaumcraft.TaintSpore,Thaumcraft.TaintSwarm'   # 只重画这两个实体的图片，其余图片和记录保留
.\scripts\export.ps1 englishnames          # 只用 en_US 启动导出英文名，写到 dumper-runs\englishnames.json
.\scripts\export.ps1 -List                   # 列出任务名
.\scripts\export.ps1 -Separate -NoImages     # 写到独立的 separate-<时间> 目录，latest 不动；连做两次比对数据是否稳定
.\scripts\export.ps1 -Clean                  # 删除 latest 以外的旧导出和 Dumper-* 存档
```

其他开关：`-Build`、`-NoImages`（全量时跳过物品图标）、`-SkipEnglishNames`（全量时不先跑英文名）、`-DryRun`（只显示会做什么）、`-Jar <路径>`。

**调试导出（只做选中的条目）**：改渲染或某个配方适配器时，不用跑完整导出。

```powershell
.\scripts\export.ps1 -Build -Items 'gregtech:gt.blockmachines:1000,EnderIO:item*Conduit*'   # 只画这些物品的图标
.\scripts\export.ps1 -Build -Handlers 'NEIHandlerCarpenter,*ForestryHandler*'              # 只导出这些配方处理器
.\scripts\export.ps1 -Items 'gregtech:gt.metaitem.01:32600' -Diagnostics                   # 附带渲染器与 GL 状态记录
```

- `-Items` 匹配物品短键；`-Handlers` 匹配处理器 ID、overlay ID、完整类名、简单类名或标题。逗号分隔，忽略大小写，`*` 匹配任意文字，
  每一项都要整名匹配。没有匹配到任何东西的项会让导出失败，避免拼错时得到一份看似正常的空结果。
- 结果写到独立的 `dumper-runs\debug-<时间>\dumps`，`latest` 保持原样；产物只含选中条目，不要给工具链用。`-Clean` 一并删除。
- `-Items` 仍要先做配方物品扫描（约 2～3 分钟，保证带 NBT 的变体能选到）；`-Handlers` 单独使用时不需要。
- 对应自动导出配置的 `imageKeys`、`recipeHandlers`，两者要求 `outputMode: "timestamp"`。
- 选中少量物品时渲染顺序与完整导出不同，依赖渲染先后顺序的问题只能在完整导出（或 `-Items '*'`）里复现。

**语言与英文名**：全量导出先用 `en_US` 启动一次，只导出英文名（`englishnames` 任务，几分钟），再用 `GAME_LANGUAGE`
（`.env`，默认 `zh_CN`）做主导出。脚本在每次启动前把实例 `options.txt` 的 `lang` 写成对应语言，结束后还原；
实际语言与预期不符时报错。原因：有些模组在加载时按启动语言把名字写死（如 CropsNH 机器把翻译后的名字传给 GT），
`originalName` 只靠事后切换语言取不到英文名。主导出通过自动导出配置的 `englishNames`（英文名文件的绝对路径）读取它，
物品与流体的 `originalName` 优先用这份。NBT 里嵌了本地化文字的物品两次启动的短键不同，对不上的保持主导出里的名字；
`status.json` 的 `englishNames` 记录文件、条目数与命中数。
同一时间只能有一个游戏在跑，游戏已在运行时脚本会直接报错。

数据固定写入 `<游戏目录>\dumper-runs\latest\dumps`，工具链的 `DUMPS_DIR` 指向这里即可。全量导出会先删掉整个 `latest`，
指定任务时只替换这些任务的输出（`latest` 模式，见下文）。图片走 `dumper-cache\png` 的内容缓存，重复导出只重新写变了的图，
所以每次全量导出的磁盘占用基本只有一份；`-Clean` 清掉历史导出，不动缓存。

**替换 Dumper 的做法**（想手动做时同样适用）：关闭游戏，删掉实例 `mods` 里旧的 `gtnhdumper*.jar`，把
`build\libs\gtnhdumper-<版本>.jar` 复制进去。只用正式 jar，不要 `-dev`、`-sources`、`-preshadow`、`-predowngrade`。
脚本把它复制成 `mods\gtnhdumper.jar`，旧 jar 备份到游戏目录的 `dumper-backup`（只留最近一个），jar 没变时不复制。
不指定固定 `runId` 而用 `latest` 模式，是因为用过的 `runId` 目录会被拒绝，游戏会停在主菜单。

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

#### 图标格式

物品图标默认导出为 WebP，由导出器直接编码，不做事后转换；同样的像素编出同样的字节。自动导出配置 `legacyPng: true`
（`scripts/export.ps1 -LegacyPng`）按旧格式输出 PNG。实体图片、配方与提示框贴图不受影响。

- 画面是小贴图整数倍放大的像素图，按原尺寸（边长 16、32、64 或 128）无损保存，由前端按像素风放大。
- 只有立体方块的静态渲染允许有损（质量 80），且体积不超过无损的 80% 时才采用；其余一律无损。
- 动画是单个动画 WebP（每帧 50 ms，相同的相邻帧合并），不再是横向帧条。
- `itemicon.json` 的 `iconPixelated`、`animationPixelated` 记录该图是否应按像素风缩放。
- 用到星空着色器的图标按游戏界面的视角距离渲染，动画帧边长 256。
- GT 凝聚态流体带有自己的覆盖层动画；GT 与 NEI 的流体展示物品都会逐帧推进它。

#### 图标缓存、像素哈希与渲染稳定性

- **并行编码**：渲染仍在客户端线程，图片编码交给后台线程，默认 CPU 核数减 2（最多 16）。
  待编码像素按字节限额排队，默认 512 MB。
- **内容缓存**：每张图按像素计算 SHA-1，编码结果存进缓存目录的 `<前两位>/<哈希>.png` 或
  `<哈希>.<编码规则>.webp`（默认 `.minecraft/dumper-cache/png`），
  输出目录里的文件是指向缓存的硬链接（不支持时复制）。同一实例再次导出时，像素没变的图片不再编码，
  `icon-performance.json` 的 `pngCacheHits` 记录命中数。缓存可以随时删除，下次导出重新生成。
  不要原地修改输出目录里的 PNG，否则会改坏缓存；需要修改时先复制。
  缓存目录可显式指定：自动导出配置的 `pngCacheDir`（绝对路径）优先，其次 JVM 参数 `-Dgtnhdumper.pngCacheDir=路径`。
  放在游戏目录之外，换整合包版本或重装实例后仍可沿用。`scripts/export.ps1` 默认把它设为仓库下 gitignore 的 `dumper-cache/`（`PNG_CACHE_DIR` 可改）。
  输出目录与缓存不在同一磁盘时无法硬链接，自动退回复制。
- **像素哈希清单**：`icon-hashes.json` 的 `images` 把每个 PNG 文件名映射到像素哈希
  （`sha1("宽:高:" + 按行的 ARGB 大端字节)`，动画按拼起来的帧条计算）。哈希只看像素，与编码无关；工具链用它比较两次导出。
- **动画帧条上限**：帧条长度是所有动画帧数的最小公倍数，可能远超内存。原始帧条超过 256 MB 的动画
  只导出静态图，记在 `icon-performance.json` 的 `oversizedAnimations`。海晶石系列（帧条可达 1.65 GB）固定导出静态图。
- **固定渲染时钟**：渲染图标期间，`Minecraft.getSystemTime()`（附魔光效、随时间变色）、世界时间与渲染插值
  （刷怪笼生物、旋转模型）、玩家 tick 都固定为同一值，结束后恢复，`frozenRenderClock` 记录所用的值。
  图集里所有动画贴图也重置到第一帧（`resetAnimatedSprites` 记录数量）：流体单元、水晶瓶、外观板等画的是流体贴图，
  不是物品自己的图标，否则会停在导出开始时图集恰好显示的那一帧（Hodgepodge 只推进最近画过的贴图），每次导出都可能不同。
  模组自己的动画计数器也固定（GT `mAnimationTick` 与凝聚态覆盖层帧号、植物魔法 `ticksInGame`、神秘工匠
  `elapsedTicks`、CodeChickenCore 的渲染时间，`frozenCounters` 记录数量）。
- **类清单改写**：其余来源由 coremod 转换器 `DeterminismTransformer` 处理：类加载时改写清单里各类的个别指令，
  只在导出期间返回固定值。覆盖直接读 `System.currentTimeMillis`、`System.nanoTime`、LWJGL `Sys.getTime` 的类
  （旋转的火箭、按时间换色的魔豆与精华、Universium 着色器、陨石指南针、林业与魔法蜜蜂等），渲染器自己保存的帧号
  与时刻（阶段伪装板、无尽箱子、注魔爪、使魔），EnderIO 方尖碑读的服务端 tick，用对象身份哈希调色的电脑部件，
  以及读取时现掷的随机值。加类只需在清单里添一行；`redirectedClockClasses` 记录实际改写到的类和指令数，
  某个类改写到 0 处时日志告警（模组换了写法）。读 `Minecraft.getSystemTime` 的类已被覆盖。
  暮色森林蚊群的模型在游戏启动时随机摆放，它的随机数始终按固定种子生成（不限导出期间）。
- **共享随机数**：每次渲染前重置客户端世界和 `Math.random()` 背后的随机数发生器（`mathRandom` 记录是否成功），
  物品里新建的生物朝向、掉落物浮动相位等不再随之前渲染了多少东西而变。渲染时新建的实体仍按类名固定自带随机数与 UUID。
- **光晕脉冲与故障特效**：GT、Avaritia 与永恒奇点的光晕脉冲、GT 故障特效的偏移按动画帧号取固定值
  （第 0 帧为均值，`fixedRandomPulses` 记录次数）：帧间仍然闪动，两次导出一致。
- **贴图风格并列**：GT++ 按组分选合金的贴图风格，原来并列时随哈希顺序而定，同一材料每次启动外观可能不同；
  现在固定取组分中靠前的一种。它在材料注册时生效，装着 Dumper 就生效。
- 仍依赖时间或随机数的渲染会在两次导出间不同，由工具链比较两次导出的 `icon-hashes.json` 记为不稳定图标。

这些设置可以用 JVM 参数调整：

| JVM 参数 | 默认值 | 作用 |
| --- | --- | --- |
| `-Dgtnhdumper.pngWorkers=N` | CPU 核数 − 2，最多 16 | PNG 编码线程数；`0` 表示在渲染线程上同步编码 |
| `-Dgtnhdumper.pngQueueMB=N` | `512` | 待编码像素的内存上限 |
| `-Dgtnhdumper.pngCache=false` | `true` | 关闭内容缓存，直接写 PNG |
| `-Dgtnhdumper.pngCacheDir=路径` | `.minecraft/dumper-cache/png` | 缓存目录；自动导出配置的 `pngCacheDir` 优先 |
| `-Dgtnhdumper.maxAnimationMB=N` | `256` | 单个动画帧条的原始大小上限 |

整合包实例建议最大内存设为 8 GB 或以上。

### 只导出部分数据

反复调试某一份数据时，用 `tasks` 只跑需要的任务，例如只看维度和魔导手册：

```json
{"enabled": true, "tasks": ["gtnhdimension", "tc4thaumonomicon"], "outputMode": "latest", "exitGame": true}
```

可用的任务名：`itemicon`、`advitempanel`、`recipes`、`oreDictionary`、`fluid`、`gtmaterial`、`gtnhdimension`、
`ticmaterial`、`tc4thaumonomicon`、`betterquesting`（任务书源文件）、`biomes`、`achievements`、`entities`、`entityimages`。写错名字会在开始前报错并列出可用名字。

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
可配合 `imageKeys` 指定 `shortKey` 数组（支持 `*` 通配）；未匹配到的项会报告失败。要求 `outputMode: "timestamp"`。
例如 `{"enabled":true,"imagesOnly":true,"imageKeys":["minecraft:diamond"]}`。
`imageDiagnostics: true` 在 `icon-capture-report.json` 记录渲染器、非透明像素数、NBT 和 FBO 前后的 GL 状态，供定向图片诊断使用。

动图的来源有三种：

- **贴图图集动画**：按 `.mcmeta` 的帧序与时长逐 tick 上传。带 `interpolate: true` 的贴图在游戏里由 Angelica 的
  `InterpolatedIcon` 在相邻两帧间渐变（1.7.10 原版没有这个功能），导出按同样的算法生成中间帧；是否渐变取决于运行中的贴图类。
- **GT 动画计数器**：超时空金属的零件与部件绕轴旋转，盖亚之魂、棱彩硅岩按色相循环。它们没有动画贴图，
  导出时按 GT 实际选用的渲染器识别，逐帧推进 `mAnimationTick`（帧长 64×64，一帧约 1 tick）。静态图是未旋转的第 0 帧。
- **时钟采样**：GT 超立方体的线框按 `Minecraft.getSystemTime` 旋转，导出时让冻结时钟每帧前进 50 ms。

静态图一律用 256×256 直接渲染第一帧。动画帧在能够无损放大回静态图时保持贴图分辨率（平面像素图即如此）；
自定义渲染器用小贴图画立方体、旋转或多层图形时，按贴图分辨率渲染会得到锯齿缩略图，这时提高到 32、64 或 128。
提高过分辨率的动画记在 `icon-performance.json` 的 `detailAnimations`。

GT 两种超立方体取 120 帧，动态帧为 64×64，静态图为时钟起点的 256×256。所有动画都在一次同步渲染里完成，
沿用 `frameCount`、`size` 字段和 `_dynamic` 文件名（WebP 为动画文件，legacy PNG 为横向帧条）；这两项不从 `imageResume` 复用旧动画。

Amazing Trophies 的方块结构奖杯默认用 VBO 绘制，模型在首次渲染时构建并缓存；在导出图标时构建，几何体每次运行都会带上不同的偏移，结构偏离底座。
导出图标前改用该模组的普通渲染器（与 `-Damazingtrophies.disableVBO=true` 相同），逐帧绘制方块。
GT 按机器编号缓存库存模型，而奖杯用跳过遮挡面的方块渲染器画结构里的机器；导出图标期间停用这个缓存
（`uncachedGtModelRenders` 记录次数），否则先在奖杯里出现过的机器图标会缺面。

特殊物品的选择规则和参数集中在 `ItemIconRenderProfile`，按注册名与 damage 选择，同一物品的 NBT 变体使用相同配置。
定时采样与关闭面剔除分别配置；超立方体在绘制期间关闭剔除，以保留 Angelica 将宽线展开成的三角形，随后恢复原状态。
普通物品保留现有静态图和纹理动画导出流程。当前所有定时采样物品共用一组采样参数。

## 生物群系与生态之书

全量自动导出（及 `tasks` 中的 `biomes`）写出 `biomes.json`：

- `biomes`：每个已注册群系一条，含 ID、英文名、类名、温度、降水、`highHumidity`、`enableSnow`、`enableRain`、`lightning`、
  `rootHeight`、`heightVariation`、颜色、地表与填充方块、BiomeDictionary 标签、林业气候（`forestryTemperature`/`forestryHumidity`）、
  IC2 作物加成（`ic2CropBonus`，有 IC2 作物 API 时）、按生物类别的刷怪表（实体注册名 `entity`、权重、群组大小；实体名称到 `entities.json` 中按注册名查），以及在生态之书扩展版中的页码 `bookPages`。
- 装了巫术时另有两本书，取自游戏里构造的书本界面（不显示），与翻开看到的一致，当前语言与英文各一份：
  - `bookOfBiomes`（`witchery:ingredient:106`，生态之书）：季节变迁仪式指南，每类群系一页，含核心物品和群系列表。
  - `bookOfBiomesExtended`（`witchery:bookbiomes2`，扩展版）：分节（BiomeDictionary 类型）与每页的群系、降雨、温度、降雪、打雷四行。

按 Mod 导出时跳过（群系属于整个整合包）。

## 维度生物群系

全量自动导出（及 `tasks` 中的 `dimensionbiomes`）写出 `dimensionbiomes.json`：`{"method": {...}, "dimensions": [...]}`。
每个已注册维度一条记录，用新建的维度提供器绑定一个只带种子的内存探测世界（不生成区块、不写存档），取其 `WorldChunkManager`，
不受当前已加载世界影响。导出用的世界是超平坦的，读不到真实群系，所以探测世界的世界类型取整合包的默认生成器：
读 `config/defaultworldgenerator.cfg` 的 `World Generator`（GTNH 为 `RWG2_CONTINENTS`），读不到或类型不存在时用原版默认类型；
采用的类型与来源写在 `method.worldType`、`method.worldTypeSource`。玩家创建世界时改选其他生成器（RWG、超平坦），主世界的群系集合会不同。

- `dimensionId`、`providerClass`、`chunkManagerClass`。
- `chunkManagerSource`：管理器来源。`overworld` 是地表提供器；`provider` 是维度自己建的管理器（星系、下界、暮色森林等），结果可信；
  `world-type` 是维度没有自己的管理器，沿用了原版管理器或世界类型给所有维度的那个类，这类维度的真实区块生成器可能根本不用它，`biomes` 不可靠，使用时应跳过。
- `biomes`：该维度能生成的群系 ID，升序。取法（写在顶层 `method` 里）：对固定的几个种子，各用
  `getBiomesForGeneration`（四分之一分辨率，每块 1024 格）与 `loadBlockGeneratorData`（每 128 格一块 16×16）
  扫描原点周围半径 `radius` 格的正方形，取并集。固定生物群系的维度（下界、多数星球）是精确的；
  主世界这类分层或噪声生成的维度，范围以外极稀有的群系、窄的河流等可能缺失（RWG2 的各类河流、冰海、热带岛等没有采到）。
- `spawnBiomes`：`getBiomesToSpawnIn()`，只是出生点会选用的群系，不是全部群系，对固定群系的管理器还是共用的默认列表，别当作维度的群系用。
- `samples`：读取的格数；`errors`：取不到时的原因，此时 `biomes` 可能为空，不做猜测。

按 Mod 导出时跳过（维度属于整个整合包）。

## 成就与奖杯

全量自动导出（及 `tasks` 中的 `achievements`）写出 `achievements.json`：

- `pages`：成就界面翻页的顺序，原版页 `Minecraft` 在前，其后是 Forge 成就页名。
- `achievements`：`AchievementList` 中的每个成就，含统计 ID `id`、当前语言与英文的名称和描述、所在页 `page`、
  坐标 `x`/`y`、前置 `parent`、`special`，以及图标 `icon`（与物品导出相同的 key，带 NBT 的物品用短 key）。
  Amazing Trophies 在 `config/amazingtrophies/achievements` 中定义的成就另有 `source: "amazingtrophies"`
  和原样复制的条件 `condition`（模组解析后不保留这份定义，只能读配置）。
- `trophies`：Amazing Trophies 注册的奖杯，含 `id`、中英文名、奖杯方块物品的 key `item`，
  以及 `config/amazingtrophies/trophies` 中的条件 `condition` 与模型 `model`。

按 Mod 导出时跳过（成就属于整个整合包）。

## 实体

全量自动导出（及 `tasks` 中的 `entities`）写出 `entities.json`：`{"entities": [...]}`，包含 `EntityList` 中注册的
全部实体（含投射物等非生物），按注册名排序。每条记录：

- `id`：EntityList 注册名，与刷怪表、刷怪笼、任务击杀要求使用的名字相同，是其他数据引用实体的键。
- `name`/`nameEnglish`：语言文件中 `entity.<id>.name` 的当前语言与英文译名，没有该键时不写。
- `class`、`mod`/`modName`/`modEntityId`（Forge 注册）、`trackingRange`/`updateFrequency`、原版全局 ID `globalId`、
  刷怪蛋颜色 `spawnEgg`。
- `categories`：按类层次判断的类别（`boss`、`hostile`、`animal`、`water`、`ambient`、`tameable`、`npc`、`flying`、
  `ranged`、`ageable`、`nonLiving` 等）；`creatureTypes`：可归入的刷怪类别（`monster`、`creature` 等）。
- 生物另有：`width`/`height`、`fireImmune`、基础属性 `health`、`attackDamage`、`movementSpeed`、`followRange`、
  `knockbackResistance`，`armor`、`experience`、`undead`、`creatureAttribute`。实体只构造、不加入世界。
- `mobsInfo`（加载 MobsInfo 时）：`health`、精英怪 `infernal`（`always`/`allowed`/`never`）、能否装入灵魂瓶 `soulVial`、
  和平难度可否生成 `peaceful`、生成群系 ID `spawnBiomes` 与其他来源 `spawnOther`（结构、事件），能否掉落怪物灵魂 `soulDrop`（真时才写）。
  掉落本身不在这里：Mob Info 配方（`recipes/mobsinfo`）逐掉落带有同样的字段，见下文“Mob Info 配方”。
- `poweredSpawner`（加载 Ender IO 时，仅生物）：电动刷怪笼黑名单 `blacklisted` 与耗能倍率 `costMultiplier`。

### Mob Info 配方

`recipes/mobsinfo/MobHandler_mobhandler.json` 是 Mob Info 的 NEI 页面，每个实体一条。除屏幕上的文字外，每个掉落还带结构化字段，
可直接计算而不必解析提示文字：

- `chance`：几率，万分比（10000 为必掉，省略即必掉）；`variableChance` 为真时几率由修饰器决定，`chance` 只是占位。
- `lootable`（受抢夺影响）、`playerOnly`（仅玩家击杀，严格限制）、`variableChance`，为真时才写。
- `chanceModifiers`：修饰器列表，每项 `type`（类名，如 `NormalChance`、`BaseChance`、`DropsOnlyInDimension`、
  `DropsOnlyWithEnchant`、`DropsOnlyUsing`、`OrUsing`、`OrBiome`、`EachLevelOfGives`、`PoweredCreeper`、
  `DropsOnlyWithWeaknessIII`）加构造参数（维度号 `dimension`、附魔 `enchantmentData`、武器 `weapon`、`chance`、
  `newChance`、`biome` 等；物品用短键，附魔写 `id` 与 `name`，群系写 ID）。
- `additionalInfo`：该掉落的附加说明。

这些字段取自 MobsInfo 自己的掉落（`MobRecipe.mOutputs`），与 NEI 页面按类型、几率、物品配对；配不上的掉落只有 `chance`。
`spawnList` 是该实体的生成地点，`spawnTotal` 是 MobsInfo 已知的地点总数：NEI 页面据此写“在所有地方均会生成”（相等）、“在 N 处生成”（N 小于总数的一半）或“除了 M 处均会生成”（M = 总数 − N）。
`eecEut`、`eecDuration`（tick）只在工业屠宰场（KubaTech 的 Extreme Entity Crusher）能处理该实体时写，取自它的配方表；
没有这两项的实体不能被工业屠宰场处理。每个掉落另有 `eecOrder`：它在工业屠宰场配方掉落列表里的位置（0 起）；没有该字段的掉落不在工业屠宰场的列表里。
该列表的顺序与 MobsInfo 自己的掉落顺序、NEI 页面的分组顺序都不同，按 `eecOrder` 排序才能还原工业屠宰场看到的顺序。
工业屠宰场自己的配方不再单独导出，见“已移除的导出”。

MobsInfo 另外登记、注册名不在 EntityList 中的变体（如凋灵骷髅 `witherSkeleton`）也导出为单独记录：
`id` 为 `<注册名>#<NBT 字段>=<值>,…`（如 `Skeleton#SkeletonType=1`，与任务击杀要求的写法相同），
NBT 取与新建同注册名实体不同的标量字段；另有 `variantOf`、`variantNbt`、`mobsInfoKey`，基础记录带 `variants` 列表。
按 Mod 导出时跳过。

### 实体图片

`entityimages` 任务（全量导出且 `noImages: false`，或在 `tasks` 中列出）为每个生物写出两张图到 `entity-images/`：

- `Entity <注册名>.webp`：全身照，1024×1024，斜前方 3/4 俯视、物品栏光照，按 2048 渲染后缩小。
- `EntityHead <注册名>.webp`：像素头像，正面平视、无光照，只画模型的头部部件（每模型单位 4 像素，纹素对齐像素）。
  认不出头部时画整个生物（`headSource: "body"`）。注册名中的 `#` 等文件名不能用的字符换成空格或下划线。

两种图都按物品图标的 WebP 规则编码：像素图按原生尺寸无损存储；全身照是带光照的渲染图，有损明显更小时用有损，头像永远无损。
`legacyPng`（`export.ps1 -LegacyPng`）时改回 `.png`。文件名以 `entity-images.json` 里记的为准。

姿势固定（身体、头、俯仰朝正前方，无摆动、受伤与死亡动画，时钟冻结）；实体在按类名固定随机种子的范围内新建，
构造期间 `entityInit` 前后与每次渲染前都重设实体与世界的随机数，Special Mobs 的随机体型等每次相同。
头像去掉手持物、身上的盔甲与渲染器另外画的东西（法杖、弓、箭袋），头盔的盔甲模型（如神秘时代邪教徒）算作头部。
借用玩家皮肤的生物（暮色森林巨人）画成 Steve。多实体拼成的 Boss（暮色森林娜迦、九头蛇）把身体节或头与脖子按游戏逻辑摆好一起画。
个别生物的视角与部件在 `EntityImages` 的特判表中指定。

`entity-images.json`：`images`（每个实体的 `full`、`head`、`headSource`、`headView` 等）、`failures`、`skipped`
（如盔甲架）、`pixelHashes`（每个文件的像素哈希，同 `icon-hashes.json`）与 `pngCacheHits`。
实体图与物品图标共用内容缓存（`.minecraft/dumper-cache/png/`），像素不变的图片只建硬链接。
少数生物的渲染器自身取随机（蚊群、愤怒末影人的抖动、粒子），两次导出仍可能不同，由工具链的不稳定名单处理。
全身照的取景对之前任务留下的渲染状态敏感（排在物品图标后面画，约三十多个实体会差几个像素），所以 `export.ps1`
在含实体图片的导出末尾另开一次游戏单独画实体图；手动或自己写配置导出时，请让 `entityimages` 单独运行。

## 蜜蜂

任务 `bees` 把游戏里读得到的蜜蜂数据写到 `bees/` 目录。物品只记短 key，气候只记 id（中文名见 `biomes.json` 的 `forestryClimates`）。

| 文件 | 内容 |
|---|---|
| `species.json` | 染色体顺序、分类树、每个蜂种的名称与说明、气候、颜色、各项标记、欣喜条件、产物与特产、基因组模板 |
| `mutations.json` | 当前与全部养蜂模式的倍率、研究催化物、杂交（亲本、子代、基础概率、条件） |
| `alleles.json` | 等位基因的名称、所属染色体与数值；采蜜对象另带 `flowerType` |
| `frames.json` | 每个框架的修正值、耐久与提示。产量修正在游戏里是相加的，其余各项相乘（养蜂模式同样如此） |
| `flowers.json` | 每类采蜜对象认的方块；靠代码判断的规则只记类名 |
| `hives.json` | 野生蜂巢掉落；Forestry 注册的蜂巢另带生成概率与适宜群系 |

游戏没有译文、原样返回语言键的名称和说明不写出。蜂种和等位基因的英文名来自 `englishnames` 任务。人读代码写的说明（比如特殊效果的实际作用）不是游戏数据，不导出。

所有蜂后是同一个物品（公主蜂、雄蜂、幼虫也是），蜂种只在 NBT 的基因组里。物品表（`advitempanel.json`）和配方里的蜂类物品
另带 `beeSpecies`（蜂种 uid）与 `beeType`（`QUEEN` / `PRINCESS` / `DRONE` / `LARVAE`）；没有基因组的蜂物品不带。

## 任务书源文件

全量自动导出时，如果加载了 BetterQuesting，会把任务书源文件复制到 `dumps/betterquesting/`，
让工具链从同一份导出读取任务数据：

- `DefaultQuests/`：`config/betterquesting/DefaultQuests/` 原样复制（任务、任务线、`QuestLinesOrder.txt`）。
- `lang/`：整合包翻译放在 `config/txloader/forceload/betterquesting/lang/` 的各语言文件。
- `itemKeys.json`：任务中每个物品堆栈在物品导出里的 key，`{任务ID: {icon, tasks: {序号: [key…]}, rewards: {序号: [key…]}}}`。
  任务 ID 与工具链相同（UUID 两半大端拼接后的 URL-safe Base64，无填充），序号是任务、奖励的 `index`，
  列表与 `requiredItems`、`rewards`/`choices` 一一对应。key 取物品导出中物品、metadata 与 NBT 内容都相同的那一项
  （NBT 的字符串形式随复制改变顺序，不能直接重算）；通配堆栈和没有导出的堆栈为 `null`。
  另有 `taskMatches: {序号: [[key…]…]}`：每个任务要求的堆栈实际接受的全部已导出物品，用 BetterQuesting 的
  `ItemComparison.StackMatch` 按任务的 `ignoreNBT` / `partialMatch` 判断，工具链据此给物品写任务关联。

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

数据任务运行期间同样冻结时钟、按任务名重置共享随机数，并把玩家视为站在地面上（有的物品说明在空中时不同）。
模组本身随机或按哈希顺序产生的内容，导出时固定为某一种结果（游戏里原本也没有确定的值）：

- BartWorks 材料的神秘时代要素按要素名顺序登记。神秘时代每个物品只保留 6 个要素，数量并列时去掉哪一个取决于
  这个顺序，原来每次启动不同，并连带影响按配方推算出的要素和“包含要素的物品”。
- 合金的贴图风格（见上文，物品说明里的“材料类型”随之固定）；离心机蜂巢配方按概率排列产物；NEI 同优先级的
  可用机器按物品排序；成就页原版在前，其余按名称。
- GTNHLib 的随机风味文字（多个机器说明）按翻译键固定种子；烤箱手套的轮换名称、腐化木炭燃烧时间、QED 名称、
  精华压缩器要素、按时间轮换的研究页示例取固定值。AE2 容量卡支持列表、林业电子管分节按文本排序。
- Thaumic Tinkerer 灵宝研究的隐藏前置按同一条件从最终注册表补齐；BuildCraft 集成台的门示例按标识排序。
- 暮色森林黑森林、雪林与沼泽群系的怪物生成表每次调用随机返回两张表中的一张，启动时其他模组追加的怪物随机落在
  其中一张里；`biomes.json` 写两张表的合并结果，按实体类名排序。
- 生物导出时按注册名播种（特殊史莱姆的体型等）。小行星带每次随机的轨道相位不再导出。

- 星空动图：宇宙素、Avaritia 星空等用 GTNHLib 星空着色器画的图标没有动画贴图，导出时按“渲染中是否用到该着色器”识别，
  冻结时钟逐帧前进（120 帧，带贴图动画的取其整数倍，末尾 30 帧淡化接回开头）。游戏里底色 20 秒变一轮，循环里只有其中一段。
- Gadomancy 两条注魔配方的随机要素消耗固定取中间值，配方另带每种要素的上下限 `inputAspectRanges`，
  魔导手册页带 `aspect_ranges`。
- Thaumic Exploration 的两个陶罐图标按模组本意收窄侧面（原版渲染四角有缝）。

已知两次导出仍可能不同、导出器没有处理的内容（工具链按黑名单沿用原值）：

- 神秘时代推算要素：约几十个物品（最近一次是 AE2 石英类部件的 `Vitreus` 等）及对应的“包含要素的物品”分页。
- AE2 部分升级卡支持列表里不带数量的行；神秘工匠一组注魔配方的顺序。
- 上述暮色森林群系：启动时 Mob Info 与 Hardcore Ender Expansion 读到哪张表仍是随机的，部分生物的生成群系、
  刷怪笼配方里的群系列表、黑森林末影人用哪个实体类（影响合并后的顺序）会不同。
- 图标：CropsNH 软锰叶每次启动随机取一种叶形，生物灵魂里个别生物有轻微明暗差异，女巫的门偶尔左右镜像，
  个别奖杯与流体展示图标。
- 个别物品说明：滚动变色的名字、随机的玩笑名称、偶尔回落成英文的一行。

`tooltips` 里的 `§z数字§Z` 是 Chromatic Tooltips 组件的流水号，两次导出可能整体偏移，工具链切分时会删掉。

## 已移除的导出

以下导出没有被数据处理工具链使用，已经移除：GT 矿脉、GT 小矿、GT 地下流体（维基改由
`gtneioreplugin` 的 NEI 配方重建）、GT 结构、任务与任务线（任务由 BetterQuesting 的任务文件处理）、
旧的神秘时代研究表 `tc4research.json`（由魔导手册导出取代），以及自动导出的 `layoutsOnly` NEI 布局模式。
自动导出的 `latest` 全量刷新会删除这些旧文件，避免误用过期数据。

另外移除了 Extreme Entity Crusher 配方（`recipes/kubatech/ExtremeEntityCrusher.json`，标识 `kubatech.eec`）。游戏里没有对应的
NEI 页面，那是用 KubaTech 的配方表加规则拼出来的投影，与 Mob Info 是同一份掉落数据。需要的数值（掉落几率、修饰器、
耗电与耗时）现在都在 Mob Info 配方里。
