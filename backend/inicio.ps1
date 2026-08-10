$ErrorActionPreference = 'SilentlyContinue'
$logsDir = "C:\DDI\luxray\backend\logs"
New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force

$services = @(
    @{ name='discovery'; jar='discovery-service\target\discovery-service-1.0.0-SNAPSHOT.jar' },
    @{ name='auth';      jar='auth-service\target\auth-service-1.0.0-SNAPSHOT.jar' },
    @{ name='cursos';    jar='cursos-service\target\cursos-service-1.0.0-SNAPSHOT.jar' },
    @{ name='analytics'; jar='analytics-service\target\analytics-service-1.0.0-SNAPSHOT.jar' },
    @{ name='gateway';   jar='api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar' }
)

foreach ($s in $services) {
    $name = $s.name
    $jar = $s.jar
    $out = "$logsDir\${name}.out"
    $err = "$logsDir\${name}.err"
    $proc = Start-Process -FilePath "java" -ArgumentList "-jar", $jar `
        -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Hidden -PassThru
    Write-Host "[$name] PID=$($proc.Id) launching..."
}

Write-Host "Waiting 30s for services to start..."
Start-Sleep -Seconds 30

# Comprobaciones de puerto
$checkPorts = @(
    @{n='discovery (8761)'; p=8761},
    @{n='auth-service (8081)'; p=8081},
    @{n='cursos-service (8082)'; p=8082},
    @{n='analytics-service (8083)'; p=8083},
    @{n='gateway (8080)'; p=8080}
)
foreach ($c in $checkPorts) {
    $open = Test-NetConnection -ComputerName localhost -Port $c.p -InformationLevel Quiet
    Write-Host ("  {0,-25} -> {1}" -f $c.n, $(if($open){'OK'}else{'DOWN'}))
}
