Set-Location "c:\Users\bruno\Desktop\Projeto Perfect World\pw-bot-mats"
New-Item -ItemType Directory -Force -Path .\logs | Out-Null
$statusFile = ".\logs\admin-status.txt"
$cp = (Get-Content .\cp.txt -Raw).Trim()
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
("time=" + (Get-Date -Format "yyyy-MM-dd HH:mm:ss") + " admin=" + $isAdmin) | Set-Content $statusFile -Encoding UTF8
if (-not $isAdmin) { exit 1 }
java -cp "target/classes;$cp" com.bot.Main
