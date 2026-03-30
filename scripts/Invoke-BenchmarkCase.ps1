param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('startup_cold', 'startup_warm', 'resource_reload', 'world_join_existing')]
    [string]$CaseId,

    [Parameter(Mandatory = $true)]
    [ValidateSet('baseline', 'candidate')]
    [string]$Variant,

    [int]$RunIndex = 1,
    [string]$SessionName = (Get-Date -Format 'yyyyMMdd-HHmmss'),
    [string]$WorldId = 'unspecified',
    [string]$ArtifactsRoot = 'artifacts/benchmarks',
    [switch]$Launch
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$runRoot = Join-Path $repoRoot $ArtifactsRoot
$gameDir = Join-Path $runRoot (Join-Path $SessionName (Join-Path $Variant (Join-Path $CaseId ("run-{0:D2}" -f $RunIndex))))
$temperature = if ($CaseId -eq 'startup_cold') { 'cold' } else { 'warm' }

New-Item -ItemType Directory -Force -Path $gameDir | Out-Null

if ($CaseId -eq 'startup_cold') {
    $foundationDir = Join-Path $gameDir 'momooptimizer/foundation'
    if (Test-Path $foundationDir) {
        Remove-Item -Recurse -Force $foundationDir
    }
}

$gradleArgs = @(
    'runClient',
    "-PmomoBenchmarkGameDir=$gameDir",
    "-PmomoBenchmarkCaseId=$CaseId",
    "-PmomoBenchmarkVariant=$Variant",
    "-PmomoBenchmarkRunIndex=$RunIndex",
    "-PmomoBenchmarkTemperature=$temperature",
    "-PmomoBenchmarkWorldId=$WorldId",
    '-PmomoBenchmarkShaderEnabled=false'
)

Write-Host "GameDir : $gameDir"
Write-Host "Command : .\gradlew $($gradleArgs -join ' ')"
Write-Host "Case    : $CaseId"
Write-Host "Variant : $Variant"
Write-Host "Run     : $RunIndex"

switch ($CaseId) {
    'startup_cold' {
        Write-Host 'Action  : 起動してタイトル画面が表示されたら raw JSONL を保存する。'
    }
    'startup_warm' {
        Write-Host 'Action  : 同一 game dir を priming 済み状態にしてから再起動し、タイトル画面まで測定する。'
    }
    'resource_reload' {
        Write-Host 'Action  : タイトル画面またはワールド参加後に resource reload を 1 回実行する。'
    }
    'world_join_existing' {
        Write-Host 'Action  : 既存ベンチワールドに参加し、30 秒観測が終わるまで待機する。'
    }
}

if ($Launch) {
    Push-Location $repoRoot
    try {
        & .\gradlew @gradleArgs
    } finally {
        Pop-Location
    }
}
