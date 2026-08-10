$ErrorActionPreference = 'SilentlyContinue'
$root = "C:\DDI\luxray\backend"
$logsDir = "$root\logs"
New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force

$services = @(
    @{ name='discovery'; jar='discovery-service-1.0.0-SNAPSHOT.jar'; subdir='discovery-service\target' },
    @{ name='auth';      jar='auth-service-1.0.0-SNAPSHOT.jar';      subdir='auth-service\target' },
    @{ name='cursos';    jar='cursos-service-1.0.0-SNAPSHOT.jar';    subdir='cursos-service\target' },
    @{ name='analytics'; jar='analytics-service-1.0.0-SNAPSHOT.jar'; subdir='analytics-service\target' },
    @{ name='gateway';   jar='api-gateway-1.0.0-SNAPSHOT.jar';       subdir='api-gateway\target' }
)

foreach ($s in $services) {
    $name = $s.name
    $jar = $s.jar
    $subdir = $s.subdir
    $args = @(
        "-jar",
        "$root\$subdir\$jar"
    ) -join ' '
    $proc = Start-Process -FilePath "java" -ArgumentList @("-jar","$root\$subdir\$jar") `
        -RedirectStandardOutput "$logsDir\${name}.out" `
        -RedirectStandardError "$logsDir\${name}.err" `
        -WorkingDirectory $root `
        -WindowStyle Hidden -PassThru
    Write-Host "[$name] PID=$($proc.Id) launched"
}
