# 云端端到端验收

本项目的 `scripts/verify-cloud.ps1` 用于验证云端主应用、训练查询、运行监控和 Prometheus 指标。脚本默认只读，不创建、修改或删除训练数据。

## 只读验收

在 Windows PowerShell 中执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\verify-cloud.ps1
```

默认使用已经部署的云端地址和现有验收日期。更换用户或日期：

```powershell
.\scripts\verify-cloud.ps1 -UserId 1 -TrainingDate 2026-09-29
```

## Agent SSE 验收

Agent 会调用模型并产生流式输出，可能消耗模型额度，因此需要显式开启：

```powershell
.\scripts\verify-cloud.ps1 -IncludeAgent
```

## Grafana 验收

先保持 SSH 隧道窗口运行：

```powershell
ssh -N -L 3000:127.0.0.1:3000 root@<SERVER_HOST>
```

再在另一个 PowerShell 中执行：

```powershell
.\scripts\verify-cloud.ps1 -GrafanaUrl http://localhost:3000
```

Grafana 仪表盘地址：

```text
http://localhost:3000/d/cognitive-training-runtime/cognitive-training-runtime
```

## 验收顺序

1. `/api/health/overview`：确认 MySQL、pgvector、MCP Client 和 Agent 状态。
2. `/api/training/tasks`：确认训练领域查询链路。
3. `/api/monitoring/overview`：确认任务、执行记录和索引任务汇总。
4. `/api/monitoring/prometheus`：确认 Prometheus 指标格式和关键指标。
5. `-IncludeAgent`：确认 Flow Agent 的 SSE 输出。
6. Grafana：确认 Prometheus 数据源和运行仪表盘。

计划确认、内容导入和任务状态变更属于写操作，建议在只读验收通过后，通过前端或 Postman 单独验证，并保留返回的计划 ID、导入 ID 和执行 ID。
