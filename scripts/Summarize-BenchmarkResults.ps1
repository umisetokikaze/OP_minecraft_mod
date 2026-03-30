param(
    [string]$ArtifactsRoot = 'artifacts/benchmarks',
    [string]$OutputRoot = 'artifacts/benchmarks/summary'
)

$ErrorActionPreference = 'Stop'

function Get-Median {
    param([double[]]$Values)
    if (-not $Values -or $Values.Count -eq 0) {
        throw 'Values must not be empty.'
    }
    $sorted = $Values | Sort-Object
    $middle = [int]($sorted.Count / 2)
    if (($sorted.Count % 2) -eq 1) {
        return [double]$sorted[$middle]
    }
    return ([double]$sorted[$middle - 1] + [double]$sorted[$middle]) / 2.0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$inputRoot = Join-Path $repoRoot $ArtifactsRoot
$summaryRoot = Join-Path $repoRoot $OutputRoot
New-Item -ItemType Directory -Force -Path $summaryRoot | Out-Null

$eventFiles = Get-ChildItem -Path $inputRoot -Recurse -Filter 'benchmark-events.jsonl' -File -ErrorAction SilentlyContinue
if (-not $eventFiles) {
    throw "No benchmark-events.jsonl found under $inputRoot"
}

$starts = @{}
$finishes = @()
$invalidated = @()

foreach ($file in $eventFiles) {
    foreach ($line in Get-Content $file.FullName) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $event = $line | ConvertFrom-Json
        switch ($event.eventType) {
            'benchmark_run_start' { $starts[$event.sessionId] = $event }
            'benchmark_run_finish' { $finishes += $event }
            'benchmark_run_invalidated' { $invalidated += $event }
        }
    }
}

$reportRows = @()
foreach ($caseId in @('startup_cold', 'startup_warm', 'resource_reload', 'world_join_existing')) {
    foreach ($metricName in @('startupMillis', 'resourceReloadMillis', 'worldJoinTtfcfMillis', 'worldJoin30sStallCount', 'worldJoin30sMaxFrameMillis')) {
        $baselineValues = @()
        $candidateValues = @()

        foreach ($finish in $finishes) {
            $start = $starts[$finish.sessionId]
            if (-not $start) {
                continue
            }
            if ($start.payload.caseId -ne $caseId) {
                continue
            }
            $value = $finish.payload.$metricName
            if ($null -eq $value) {
                continue
            }
            if ($start.payload.variant -eq 'baseline') {
                $baselineValues += [double]$value
            } elseif ($start.payload.variant -eq 'candidate') {
                $candidateValues += [double]$value
            }
        }

        if ($baselineValues.Count -eq 0 -and $candidateValues.Count -eq 0) {
            continue
        }

        $baselineMedian = if ($baselineValues.Count -gt 0) { Get-Median $baselineValues } else { $null }
        $candidateMedian = if ($candidateValues.Count -gt 0) { Get-Median $candidateValues } else { $null }
        $deltaPercent = $null
        if ($null -ne $baselineMedian -and $null -ne $candidateMedian -and $baselineMedian -ne 0) {
            $deltaPercent = (($candidateMedian - $baselineMedian) / $baselineMedian) * 100.0
        }

        $invalidatedCount = @($invalidated | Where-Object {
            $_.payload.caseId -eq $caseId
        }).Count

        $reportRows += [pscustomobject]@{
            caseId = $caseId
            metric = $metricName
            baselineMedian = $baselineMedian
            candidateMedian = $candidateMedian
            deltaPercent = $deltaPercent
            baselineSamples = $baselineValues.Count
            candidateSamples = $candidateValues.Count
            invalidatedCount = $invalidatedCount
        }
    }
}

$jsonPath = Join-Path $summaryRoot 'benchmark-summary.json'
$mdPath = Join-Path $summaryRoot 'benchmark-summary.md'

$reportRows | ConvertTo-Json -Depth 4 | Set-Content -Path $jsonPath -Encoding UTF8

$markdown = @(
    '| caseId | metric | baselineMedian | candidateMedian | deltaPercent | baselineSamples | candidateSamples | invalidatedCount |',
    '|---|---:|---:|---:|---:|---:|---:|---:|'
)
foreach ($row in $reportRows) {
    $markdown += "| $($row.caseId) | $($row.metric) | $($row.baselineMedian) | $($row.candidateMedian) | $($row.deltaPercent) | $($row.baselineSamples) | $($row.candidateSamples) | $($row.invalidatedCount) |"
}
$markdown | Set-Content -Path $mdPath -Encoding UTF8

Write-Host "Wrote $jsonPath"
Write-Host "Wrote $mdPath"
