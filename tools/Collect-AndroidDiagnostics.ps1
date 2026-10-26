param(
    [string]$Serial,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$Output = (Join-Path $PSScriptRoot '..\device-results')
)
$ErrorActionPreference = 'Stop'
$target = @()
if ($Serial) { $target = @('-s', $Serial) }
$destination = [IO.Path]::GetFullPath($Output)
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$names = & $Adb @target shell run-as com.echorelle.android ls files
if ($LASTEXITCODE -ne 0) { throw 'Could not read app diagnostics. Select one authorized device and install the debug APK.' }
$files = @($names | Where-Object { $_ -match '^app-events-[0-9]+\.jsonl$' })
foreach ($name in $files) {
    $lines = & $Adb @target exec-out run-as com.echorelle.android cat "files/$name"
    if ($LASTEXITCODE -ne 0) { throw "Failed to collect $name" }
    $path = Join-Path $destination $name
    [IO.File]::WriteAllLines($path, $lines, [Text.UTF8Encoding]::new($false))
    $events = @($lines | ForEach-Object { try { $_ | ConvertFrom-Json } catch { } })
    if ($events.Count -eq 0) { continue }
    $first = $events[0]
    $last = $events[-1]
    $checkpoints = @($events | Where-Object event -eq 'checkpoint')
    $summary = [ordered]@{
        source = $name
        recordedSeconds = [math]::Round(($last.elapsed_ns - $first.elapsed_ns) / 1e9, 3)
        firstBatteryPercent = $first.details.battery_percent
        lastBatteryPercent = $last.details.battery_percent
        cleanServiceEnd = ($last.event -eq 'session_end')
        lastEvent = $last.event
        checkpoints = $checkpoints.Count
        lastCheckpoint = if ($checkpoints.Count) { $checkpoints[-1].details } else { $null }
        audibleOutputVerified = $false
        interpretation = 'Frame consumption and environment observations only. Abrupt endings have no inferred cause. Battery change is not an energy benchmark; compare unplugged idle and continuous playback baselines.'
    }
    $summary | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $destination ($name -replace '\.jsonl$', '-summary.json'))
}
& $Adb @target shell dumpsys activity services com.echorelle.android | Set-Content (Join-Path $destination 'service-state.txt')
& $Adb @target shell dumpsys media_session | Set-Content (Join-Path $destination 'media-state.txt')
Write-Output "Collected $($files.Count) local app journals and automatic summaries in $destination."
