param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [ValidateSet('force-stop','reboot')][string]$Mode = 'force-stop',
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference='Stop'
$emulated = & $Adb -s $Serial shell getprop ro.kernel.qemu
if ($LASTEXITCODE -ne 0 -or $emulated -ne '1') { throw 'Select a running Android emulator; this test does not reboot physical phones.' }
# Intended for an emulator containing the debug app and its matching instrumentation APK.
# Each phase backs up/restores definitions internally; neither phase clears application data.
function Invoke-Phase([string]$Phase) {
    $result = & $Adb -s $Serial shell am instrument -w -e class com.echorelle.android.ProcessRecoveryTest -e processPhase $Phase com.echorelle.android.test/androidx.test.runner.AndroidJUnitRunner
    $text = $result -join "`n"
    if ($LASTEXITCODE -ne 0 -or $text -notmatch 'OK \(' -or $text -match 'FAILURES!!!|INSTRUMENTATION_FAILED') { throw "Process test $Phase failed:`n$text" }
    Write-Output "$Phase phase passed."
}
Invoke-Phase 'seed'
try {
    if($Mode -eq 'force-stop') {
        & $Adb -s $Serial shell am force-stop com.echorelle.android
        if($LASTEXITCODE -ne 0) { throw 'Force-stop failed.' }
    } else {
        & $Adb -s $Serial reboot
        $deadline = (Get-Date).AddMinutes(2)
        do {
            Start-Sleep -Seconds 2
            $boot = & $Adb -s $Serial shell getprop sys.boot_completed 2>$null
            if((Get-Date) -gt $deadline) { throw 'Emulator boot timed out; run the verify phase to restore the library when available.' }
        } while ($boot -ne '1')
    }
    & $Adb -s $Serial shell input keyevent KEYCODE_WAKEUP
    & $Adb -s $Serial shell wm dismiss-keyguard
} finally {
    Invoke-Phase 'verify'
}
Write-Output "$Mode recovery passed: saved definitions/visual survived, playback stayed stopped, original library restored."
