# Windows panel-server helper: keep SSH reverse tunnel alive to the public VPS.
# Requires OpenSSH client. Edit $Vps and $User, then run in a dedicated window or Task Scheduler.

$ErrorActionPreference = 'Stop'
$Vps = 'PUBLIC_VPS_IP'
$User = 'seminecraft-tunnel'
$LocalPanel = '127.0.0.1:8088'
$RemoteBind = '127.0.0.1:8088'

while ($true) {
    Write-Host "$(Get-Date -Format o) connecting $User@$Vps ..."
    & ssh -N `
        -o ServerAliveInterval=30 `
        -o ServerAliveCountMax=3 `
        -o ExitOnForwardFailure=yes `
        -R "${RemoteBind}:${LocalPanel}" `
        "${User}@${Vps}"
    Write-Host "$(Get-Date -Format o) ssh exited ($LASTEXITCODE); retry in 5s"
    Start-Sleep -Seconds 5
}
