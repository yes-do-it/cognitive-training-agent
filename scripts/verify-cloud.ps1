param(
    [string]$BaseUrl = "http://localhost:8091",
    [long]$UserId = 1,
    [string]$TrainingDate = "2026-09-29",
    [string]$GrafanaUrl = "http://localhost:3000",
    [switch]$IncludeAgent
)

$ErrorActionPreference = "Stop"
$script:FailedChecks = 0

function Invoke-GetJson {
    param([string]$Uri)
    return Invoke-RestMethod -Method Get -Uri $Uri -TimeoutSec 30
}

function Test-Check {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    try {
        $result = & $Action
        Write-Host "[OK]   $Name" -ForegroundColor Green
        return $result
    } catch {
        $script:FailedChecks++
        Write-Host "[FAIL] $Name - $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

Write-Host "Cognitive Training cloud verification" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl"
Write-Host "Training date: $TrainingDate"
Write-Host ""

$health = Test-Check "application health" {
    $response = Invoke-GetJson "$BaseUrl/api/health/overview"
    if ($response.code -and $response.code -ne "0000") {
        throw "business code is $($response.code)"
    }
    if ($response.data.overallStatus -and $response.data.overallStatus -notin @("UP", "DEGRADED")) {
        throw "overall status is $($response.data.overallStatus)"
    }
    $response
}

$tasks = Test-Check "training task query" {
    $response = Invoke-GetJson "$BaseUrl/api/training/tasks?userId=$UserId&trainingDate=$TrainingDate"
    if ($response.code -and $response.code -ne "0000") {
        throw "business code is $($response.code)"
    }
    if ($null -eq $response.data) {
        throw "response data is null"
    }
    $response
}

if ($tasks -and $tasks.data) {
    Write-Host "       tasks: $($tasks.data.Count)" -ForegroundColor DarkGray
    $tasks.data | ForEach-Object {
        Write-Host "       task=$($_.id) content=$($_.contentId) status=$($_.status)" -ForegroundColor DarkGray
    }
}

$monitoring = Test-Check "runtime monitoring overview" {
    $response = Invoke-GetJson "$BaseUrl/api/monitoring/overview?staleMinutes=30&limit=50"
    if ($response.code -and $response.code -ne "0000") {
        throw "business code is $($response.code)"
    }
    $response
}

$metrics = Test-Check "Prometheus metrics endpoint" {
    $response = Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$BaseUrl/api/monitoring/prometheus?staleMinutes=30&limit=50" -TimeoutSec 30
    if ($response.StatusCode -ne 200) {
        throw "HTTP status is $($response.StatusCode)"
    }
    foreach ($metric in @("cognitive_training_health", "cognitive_training_tasks", "cognitive_training_mcp_tools")) {
        if ($response.Content -notmatch [regex]::Escape($metric)) {
            throw "metric $metric is missing"
        }
    }
    $response
}

if ($IncludeAgent) {
    Test-Check "Flow Agent SSE" {
        $message = [uri]::EscapeDataString("请根据我的训练历史安排今天的训练")
        $response = Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$BaseUrl/api/agent/flow/stream?userId=$UserId&message=$message" -TimeoutSec 180
        if ($response.StatusCode -ne 200) {
            throw "HTTP status is $($response.StatusCode)"
        }
        if ($response.Content -notmatch "event:\s*done" -and $response.Content -notmatch "data:\s*\[DONE\]") {
            throw "SSE stream did not finish with event: done"
        }
        $response
    } | Out-Null
} else {
    Write-Host "[SKIP] Flow Agent SSE (use -IncludeAgent to invoke the model)" -ForegroundColor Yellow
}

try {
    $grafana = Invoke-RestMethod -Method Get -Uri "$GrafanaUrl/api/health" -TimeoutSec 5
    if ($grafana.database -ne "ok") {
        throw "Grafana database status is $($grafana.database)"
    }
    Write-Host "[OK]   Grafana health ($GrafanaUrl)" -ForegroundColor Green
} catch {
    Write-Host "[SKIP] Grafana health ($GrafanaUrl) - keep the SSH tunnel open to check it" -ForegroundColor Yellow
}

Write-Host ""
if ($script:FailedChecks -gt 0) {
    Write-Host "Verification failed: $script:FailedChecks check(s) failed." -ForegroundColor Red
    exit 1
}

Write-Host "Verification passed." -ForegroundColor Green


