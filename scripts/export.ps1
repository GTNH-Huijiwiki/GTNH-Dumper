<#
.SYNOPSIS
  用 Prism 里的 GTNH 实例运行 GTNH-Dumper 的自动导出。

.DESCRIPTION
  替换实例 mods 里的 Dumper jar，写好自动导出配置，启动游戏，等它导出完退出，再汇报结果。
  输出固定在 <游戏目录>\dumper-runs\latest\dumps，工具链的 DUMPS_DIR 指向这里。
    不带任务名：全量导出。先删除 dumper-runs\latest，再重新导出。
    带任务名：  只重新导出这些任务（latest 模式），其余数据原样保留。
  全量导出先用 en_US 启动一次导出英文名，再用 GAME_LANGUAGE（默认 zh_CN）做主导出；语言由脚本写进 options.txt。
  PNG 内容缓存默认在仓库的 dumper-cache（PNG_CACHE_DIR 可改），不在实例里。
  配置见 .env.example。

.EXAMPLE
  .\scripts\export.ps1
  全量导出（含物品图标与实体图片）。

.EXAMPLE
  .\scripts\export.ps1 entities entityimages
  只导出实体数据与实体图片。

.EXAMPLE
  .\scripts\export.ps1 entityimages -Entities 'Thaumcraft.TaintSpore,Thaumcraft.TaintSwarm'
  只重新渲染这两个实体的图片，其余图片与 entity-images.json 的其它条目保持不变。

.EXAMPLE
  .\scripts\export.ps1 -Build -Items 'gregtech:gt.blockmachines:1000,EnderIO:item*Conduit*'
  调试：只渲染选中物品的图标（短键，* 通配）。写到独立的 dumper-runs\debug-<时间>，latest 不受影响。

.EXAMPLE
  .\scripts\export.ps1 -Handlers 'GTNEIDefaultHandler,*Forestry*'
  调试：只导出选中的 NEI 配方处理器（匹配处理器 ID、overlay ID、类名或标题，* 通配），同样写到 debug 目录。

.EXAMPLE
  .\scripts\export.ps1 englishnames
  只用 en_US 启动导出英文名（写到 dumper-runs\englishnames.json）。

.EXAMPLE
  .\scripts\export.ps1 -List
  列出可用的任务名。

.EXAMPLE
  .\scripts\export.ps1 -Build recipes
  先构建 jar，再只导出配方。

.EXAMPLE
  .\scripts\export.ps1 -Clean
  删除 latest 以外的旧导出和 Dumper-* 存档，不启动游戏。
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)] [string[]] $Tasks,
    [switch] $Build,     # 先 gradlew build
    [switch] $NoImages,  # 全量导出时跳过物品图标
    [switch] $Clean,     # 只清理旧导出，不导出
    [switch] $List,      # 只列出任务名
    [switch] $DryRun,    # 只显示会做什么
    [switch] $SkipEnglishNames, # 全量导出时不先用 en_US 跑一次英文名（少启动一次游戏，但 CropsNH 等机器的英文名会是中文）
    [string] $Jar,       # 指定 jar，覆盖 DUMPER_JAR 与 build\libs
    [string] $Entities,  # 只重新导出这些实体的图片（逗号分隔注册名），要与任务 entityimages 一起用；其余图片与记录原样保留
    [string] $Items,     # 调试：只导出这些物品的图标（逗号分隔短键，* 通配）
    [string] $Handlers,  # 调试：只导出这些 NEI 配方处理器（逗号分隔，* 通配）
    [switch] $Diagnostics # 在 icon-capture-report.json 记录渲染器与 GL 状态，配合 -Items 用
)

$ErrorActionPreference = 'Stop'
$Repo = Split-Path -Parent $PSScriptRoot

# ── 配置：.env 与系统环境变量（环境变量优先） ──────────────────────────
$envFile = @{}
$envPath = Join-Path $Repo '.env'
if (Test-Path $envPath) {
    foreach ($line in Get-Content $envPath -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$' -and $line -notmatch '^\s*#') {
            $envFile[$Matches[1]] = $Matches[2].Trim('"')
        }
    }
}
function Get-Setting([string]$Name, [string]$Default = '') {
    $fromEnv = [Environment]::GetEnvironmentVariable($Name)
    if ($fromEnv) { return $fromEnv }
    if ($envFile.ContainsKey($Name) -and $envFile[$Name]) { return $envFile[$Name] }
    return $Default
}

# 任务名直接读 ExportOptions.java，不另存一份。
$optionsSource = Join-Path $Repo 'src\main\java\com\iouter\gtnhdumper\automation\ExportOptions.java'
$taskNames = @()
if (Test-Path $optionsSource) {
    $text = Get-Content $optionsSource -Raw -Encoding UTF8
    if ($text -match '(?s)TASK_NAMES\s*=.*?asList\((.*?)\)\)') {
        $taskNames = [regex]::Matches($Matches[1], '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
    }
}
if ($List) {
    if ($taskNames) { $taskNames -join "`n" } else { Write-Warning "读不到 $optionsSource" }
    return
}

# ── 路径 ──────────────────────────────────────────────────────────────
$instance = Get-Setting 'PRISM_INSTANCE'
$account = Get-Setting 'PRISM_ACCOUNT' 'Dumper'
$prismExe = Get-Setting 'PRISM_EXE' (Join-Path $env:LOCALAPPDATA 'Programs\PrismLauncher\prismlauncher.exe')
$gameDir = Get-Setting 'GAME_DIR'
if (-not $gameDir) {
    if (-not $instance) { throw '请在 .env 里设置 PRISM_INSTANCE（见 .env.example）。' }
    $dataDir = Get-Setting 'PRISM_DATA_DIR' (Join-Path $env:APPDATA 'PrismLauncher')
    $root = Join-Path $dataDir "instances\$instance"
    $gameDir = @('.minecraft', 'minecraft') | ForEach-Object { Join-Path $root $_ } | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $gameDir) { throw "找不到实例的游戏目录：$root\.minecraft" }
}
$gameDir = (Resolve-Path $gameDir).Path
$runs = Join-Path $gameDir 'dumper-runs'
$latest = Join-Path $runs 'latest'
$dumps = Join-Path $latest 'dumps'
$config = Join-Path $gameDir 'config\gtnhdumper-autorun.json'
# PNG 内容缓存默认放在本仓库的 dumper-cache（已 gitignore），不随实例走，换整合包版本也能复用。
$cacheDir = Get-Setting 'PNG_CACHE_DIR' (Join-Path $Repo 'dumper-cache')
if (-not [IO.Path]::IsPathRooted($cacheDir)) { $cacheDir = Join-Path $Repo $cacheDir }
$cacheDir = [IO.Path]::GetFullPath($cacheDir)
$mods = Join-Path $gameDir 'mods'

function Test-Inside([string]$Path, [string]$Parent) {
    $full = [IO.Path]::GetFullPath($Path).TrimEnd('\')
    $base = [IO.Path]::GetFullPath($Parent).TrimEnd('\')
    return $full.StartsWith($base + '\', [StringComparison]::OrdinalIgnoreCase)
}
function Remove-Safely([string]$Path, [string]$Parent) {
    if (-not (Test-Inside $Path $Parent)) { throw "拒绝删除 $Parent 之外的路径：$Path" }
    if ($DryRun) { Write-Host "  [dry-run] 删除 $Path"; return }
    Remove-Item -LiteralPath $Path -Recurse -Force
}
function Get-GameProcess {
    $key = if ($instance) { $instance } else { Split-Path (Split-Path $gameDir) -Leaf }
    Get-CimInstance Win32_Process -Filter "name='java.exe' or name='javaw.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.Contains($key) } | Select-Object -First 1
}

# ── 只清理 ────────────────────────────────────────────────────────────
if ($Clean) {
    if (Get-GameProcess) { throw '游戏正在运行，先关掉再清理。' }
    $victims = @()
    if (Test-Path $runs) { $victims += Get-ChildItem $runs -Directory | Where-Object { $_.Name -ne 'latest' } }
    $saves = Join-Path $gameDir 'saves'
    if (Test-Path $saves) { $victims += Get-ChildItem $saves -Directory -Filter 'Dumper-*' }
    foreach ($v in $victims) { if (-not $DryRun) { Write-Host "删除 $($v.FullName)" }; Remove-Safely $v.FullName $gameDir }
    Write-Host "已清理 $($victims.Count) 个目录；保留 $latest 与 PNG 缓存 $cacheDir。"
    return
}

# ── 任务与参数检查 ────────────────────────────────────────────────────
$Tasks = @($Tasks | Where-Object { $_ } | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
foreach ($task in $Tasks) {
    if ($taskNames -and ($taskNames -notcontains $task)) {
        throw "没有这个任务：$task。可用：$($taskNames -join ', ')"
    }
}
# 调试导出：只做选中的物品图标或配方处理器，写到独立目录，latest 保持原样。
$itemKeys = @($Items -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$handlerNames = @($Handlers -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$selection = $itemKeys.Count -gt 0 -or $handlerNames.Count -gt 0
if ($Diagnostics -and $itemKeys.Count -eq 0) { throw '-Diagnostics 要和 -Items 一起用。' }
if ($selection) {
    $implied = @()
    if ($itemKeys.Count -gt 0) { $implied += 'itemicon' }
    if ($handlerNames.Count -gt 0) { $implied += 'recipes' }
    $extra = @($Tasks | Where-Object { $implied -notcontains $_ })
    if ($extra.Count -gt 0) { throw "-Items / -Handlers 不能和这些任务一起用：$($extra -join ', ')" }
    $Tasks = $implied
    $debugRunId = 'debug-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    $dumps = Join-Path (Join-Path $runs $debugRunId) 'dumps'
}
$full = $Tasks.Count -eq 0
if (-not (Test-Path $prismExe)) { throw "找不到 Prism：$prismExe（设置 PRISM_EXE）。" }
if (-not $DryRun -and (Get-GameProcess)) { throw '游戏正在运行（别的导出或你自己开着），先关掉再导出。' }

# ── jar ───────────────────────────────────────────────────────────────
if ($Build) {
    Write-Host '构建 Dumper …'
    if ($DryRun) { Write-Host '  [dry-run] gradlew build -x test' }
    else {
        Push-Location $Repo
        try { & (Join-Path $Repo 'gradlew.bat') build -x test; if ($LASTEXITCODE -ne 0) { throw '构建失败。' } }
        finally { Pop-Location }
    }
}
if (-not $Jar) { $Jar = Get-Setting 'DUMPER_JAR' }
if (-not $Jar) {
    # 只要正式 jar：不要 -dev、-sources、-preshadow、-predowngrade。
    $Jar = Get-ChildItem (Join-Path $Repo 'build\libs') -Filter 'gtnhdumper-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '-(dev|sources|preshadow|predowngrade)' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $Jar -or -not (Test-Path $Jar)) { throw '找不到 Dumper jar：先 gradlew build，或加 -Build，或设置 DUMPER_JAR。' }
$Jar = (Resolve-Path $Jar).Path

# ── 替换实例里的 Dumper ───────────────────────────────────────────────
$target = Join-Path $mods 'gtnhdumper.jar'
$newHash = (Get-FileHash $Jar -Algorithm SHA256).Hash
$old = @(Get-ChildItem $mods -Filter 'gtnhdumper*.jar' -ErrorAction SilentlyContinue)
Write-Host "Dumper jar：$Jar"
if ($old.Count -eq 1 -and $old[0].FullName -eq $target -and (Get-FileHash $target -Algorithm SHA256).Hash -eq $newHash) {
    Write-Host '  实例里已是这个 jar，不用替换。'
} else {
    $backup = Join-Path $gameDir 'dumper-backup'
    foreach ($o in $old) {
        Write-Host "  替换 $($o.Name)（旧 jar 备份到 dumper-backup，只留最近一个）"
        if (-not $DryRun) {
            New-Item -ItemType Directory -Force $backup | Out-Null
            Get-ChildItem $backup -File | Remove-Item -Force
            Move-Item -LiteralPath $o.FullName -Destination $backup -Force
        }
    }
    if ($DryRun) { Write-Host "  [dry-run] 复制到 $target" } else { Copy-Item -LiteralPath $Jar -Destination $target -Force }
}

# ── 导出 ──────────────────────────────────────────────────────────────
# 主导出用 GAME_LANGUAGE（默认 zh_CN，维基要中文名）；英文名用 en_US 单独启动一次，写成 englishnames.json，
# 主导出读它来填 originalName（有些模组把名字在加载时按启动语言定死，事后切语言改不了）。
$gameLanguage = Get-Setting 'GAME_LANGUAGE' 'zh_CN'
$englishFile = Join-Path $runs 'englishnames.json'
$englishOnly = $Tasks.Count -eq 1 -and $Tasks[0] -eq 'englishnames'
if ($Tasks -contains 'englishnames' -and -not $englishOnly) { throw 'englishnames 必须单独运行：它要用 en_US 启动，不能和别的任务一起。' }
$runEnglish = $englishOnly -or ($full -and -not $SkipEnglishNames)
$optionsFile = Join-Path $gameDir 'options.txt'

function Set-GameLanguage([string]$Lang) {
    $lines = @()
    if (Test-Path $optionsFile) { $lines = @(Get-Content $optionsFile -Encoding UTF8) }
    $found = $false
    $lines = @($lines | ForEach-Object { if ($_ -match '^lang:') { $found = $true; "lang:$Lang" } else { $_ } })
    if (-not $found) { $lines = @("lang:$Lang") + $lines }
    [IO.File]::WriteAllText($optionsFile, (($lines -join "`n") + "`n"), (New-Object Text.UTF8Encoding $false))
}

# 启动一次游戏并等它导出完。成功返回状态对象，失败抛错。
function Invoke-GameRun([System.Collections.IDictionary]$Request, [string]$Language, [string]$Label) {
    $json = $Request | ConvertTo-Json -Compress
    Write-Host "[$Label] 语言 $Language，配置：$json"
    if ($DryRun) { Write-Host '  [dry-run] 写入配置、启动 Prism 并等待游戏退出'; return $null }
    if (-not $instance) { throw '启动需要 PRISM_INSTANCE。' }
    New-Item -ItemType Directory -Force (Split-Path $config) | Out-Null
    Set-GameLanguage $Language
    # 无 BOM 的 UTF-8：配方处理器可以按中文标题选择。
    [IO.File]::WriteAllText($config, $json, (New-Object Text.UTF8Encoding $false))
    $startedMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $runDir = if ($Request['outputMode'] -eq 'latest') { $latest } else { Join-Path $runs $Request['runId'] }
    $statusFile = Join-Path $runDir 'status.json'
    try {
        Start-Process $prismExe -ArgumentList @('--launch', $instance, '--offline', $account) -WindowStyle Hidden
        $game = $null
        for ($i = 0; $i -lt 100 -and -not $game; $i++) { Start-Sleep -Seconds 3; $game = Get-GameProcess }
        if (-not $game) { throw '游戏没有启动，检查 PRISM_EXE、PRISM_INSTANCE 与 PRISM_ACCOUNT。' }
        Write-Host "游戏已启动（pid $($game.ProcessId)），导出中，约需几分钟到半小时……"
        $deadline = (Get-Date).AddMinutes([int](Get-Setting 'EXPORT_TIMEOUT_MIN' '120'))
        $lastStage = ''
        while (Get-Process -Id $game.ProcessId -ErrorAction SilentlyContinue) {
            if ((Get-Date) -gt $deadline) { Stop-Process -Id $game.ProcessId -Force; throw '导出超时，已关闭游戏。' }
            Start-Sleep -Seconds 10
            if (Test-Path $statusFile) {
                try {
                    $s = Get-Content $statusFile -Raw -Encoding UTF8 | ConvertFrom-Json
                    if ($s.updatedMillis -ge $startedMs -and "$($s.stage) $($s.message)" -ne $lastStage) {
                        $lastStage = "$($s.stage) $($s.message)"
                        Write-Host "  $(Get-Date -Format HH:mm:ss) $lastStage"
                    }
                } catch { }
            }
        }
    } finally {
        # 不管成败，语言设置还原成主导出的语言，免得下次手动启动是英文。
        Set-GameLanguage $gameLanguage
    }
    if (-not (Test-Path $statusFile)) { throw "游戏已退出但没有状态文件，导出没有开始：$statusFile（看游戏 logs\latest.log）。" }
    $s = Get-Content $statusFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($s.updatedMillis -lt $startedMs) { throw '状态文件不是这次写的，导出没有开始（看游戏 logs\latest.log）。' }
    if ($s.language -ne $Language) { throw "导出实际用的语言是 $($s.language)，不是 $Language；检查实例的 options.txt。" }
    Write-Host ''
    Write-Host "[$Label] 状态：$($s.stage)，完成 $(@($s.completed).Count) 项，失败 $(@($s.failed).Count) 项"
    foreach ($f in @($s.failed)) { Write-Host "  失败：$f" }
    foreach ($p in $s.taskMillis.PSObject.Properties) { Write-Host ('  {0,-18} {1,6:N0} 秒' -f $p.Name, ($p.Value / 1000)) }
    if ($s.stage -ne 'EXPORTED') { throw "[$Label] 没有导出完成（$($s.stage)）。" }
    return $s
}

Write-Host "输出：$dumps"
if ($selection) { Write-Host '调试导出：latest 目录不受影响。' }
Write-Host "PNG 缓存：$cacheDir"

# ── 第一步：en_US 导出英文名 ───────────────────────────────────────────
if ($runEnglish) {
    $null = Invoke-GameRun ([ordered]@{ enabled = $true; outputMode = 'latest'; exitGame = $true; tasks = @('englishnames') }) 'en_US' '英文名'
    $produced = Join-Path $dumps 'englishnames.json'
    if ($DryRun) { Write-Host "  [dry-run] 把 $produced 复制到 $englishFile" }
    else {
        if (-not (Test-Path $produced)) { throw "没有生成英文名文件：$produced" }
        Copy-Item -LiteralPath $produced -Destination $englishFile -Force
        Write-Host "英文名文件：$englishFile"
    }
}
if ($englishOnly) { return }

# ── 第二步：主导出 ─────────────────────────────────────────────────────
$request = [ordered]@{ enabled = $true; outputMode = 'latest'; exitGame = $true; pngCacheDir = $cacheDir }
if ($full) { $request['noImages'] = [bool]$NoImages } else { $request['tasks'] = $Tasks }
if ($selection) {
    $request['outputMode'] = 'timestamp'
    $request['runId'] = $debugRunId
    if ($itemKeys.Count -gt 0) { $request['imageKeys'] = $itemKeys }
    if ($handlerNames.Count -gt 0) { $request['recipeHandlers'] = $handlerNames }
    if ($Diagnostics) { $request['imageDiagnostics'] = $true }
}
$entityIds = @($Entities -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($entityIds.Count -gt 0) {
    if ($Tasks.Count -ne 1 -or $Tasks[0] -ne 'entityimages') { throw '-Entities 必须配合且只配合任务 entityimages' }
    $request['entities'] = $entityIds
}
# 用到物品或流体名称的导出才带英文名文件；它是上一次 en_US 导出的结果，换整合包版本后要重新生成。
$usesNames = $full -or ($Tasks -contains 'advitempanel') -or ($Tasks -contains 'fluid')
if ($usesNames) {
    if (Test-Path $englishFile) {
        $request['englishNames'] = $englishFile
        Write-Host "英文名文件：$englishFile（生成于 $((Get-Item $englishFile).LastWriteTime)）"
    } elseif ($DryRun -and $runEnglish) {
        $request['englishNames'] = $englishFile
    } else {
        Write-Warning '没有英文名文件：加载时按启动语言定死的名字（如 CropsNH 机器）会是中文。先运行 .\scripts\export.ps1 englishnames。'
    }
}
Write-Host "导出：$(if ($full) { '全量' + $(if ($NoImages) { '（不含物品图标）' } else { '' }) } else { $Tasks -join ', ' })"
if ($full -and (Test-Path $latest)) {
    if ($DryRun) { Write-Host '  [dry-run] 全量导出：先删除固定目录 latest' }
    else { Write-Host '全量导出：先删除固定目录 latest' }
    Remove-Safely $latest $runs
}
$s = Invoke-GameRun $request $gameLanguage '主导出'
if ($s) { Write-Host "$(if ($selection) { '调试输出（不要给工具链用）' } else { '数据目录（工具链的 DUMPS_DIR）' })：$dumps" }
