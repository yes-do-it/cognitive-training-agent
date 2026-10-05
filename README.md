# Cognitive Training Agent Platform

认知训练智能辅助编排系统。项目以 `cog-train-plat` 的训练领域为主，以 `ai-agent-station-study` 中验证过的 Spring AI、MCP、SSE 和 Agent 编排能力为辅，采用 DDD 模块化单体逐步建设。

## 当前边界

- 训练领域负责用户、内容、计划、每日任务、执行记录、状态流转、幂等和失败重试。
- Agent 位于应用层，通过训练领域提供的只读工具查询事实。
- Agent 不直接修改训练表；涉及计划调整时只生成待确认方案。
- Flow Agent 和 Auto Agent 通过 SSE 输出。
- MCP Server 默认关闭；云端采用独立 MCP 侧车容器，主应用通过内网 MCP SSE Client 连接。

## 已完成

- 迁移训练调度领域模型和 DDD 分层结构。
- 统一数据库为 `cognitive_training`，配置改为环境变量。
- 增加 MySQL、PostgreSQL/pgvector 和应用的 Docker Compose 配置。
- 增加 Spring AI 1.0.0、MCP Client 和 MCP SDK 依赖。
- 增加训练上下文、执行历史和统计查询工具。
- 增加 Flow Agent、Auto Agent 和 SSE 接口。
- 增加 MCP Server，只读暴露：`query_training_context`、`query_training_history`、`query_training_stats`、`query_training_contents`。
- Agent 默认使用本地工具；配置 MCP Client 后自动切换为 MCP 工具回调。
- 增加训练知识库 RAG：使用 `TokenTextSplitter` 切分训练内容，通过 Embedding 写入独立 pgvector 表 `cognitive_training_vector_store`，Agent 生成建议前自动检索相关内容。
- 增加 PDF、DOCX、TXT、MD 文档导入：使用 Tika 解析后创建训练内容，可选自动提交异步向量索引任务。
- 增加训练内容版本控制：更新接口支持 `expectedVersion` 乐观锁，版本递增且拒绝过期写入；更新后自动触发向量重建。
- RAG 使用独立 PostgreSQL 数据源，MySQL 仍作为训练领域主数据源；通过 `COGNITIVE_RAG_ENABLED` 控制开关。
- 增加 Agent 建议方案确认接口：仅在用户确认后，以事务方式创建训练计划并绑定训练内容。
- 云端 profile 已支持服务器 `<SERVER_HOST>`，默认数据库名为 `cognitive_training`。
- 云端已部署主应用和 MCP 侧车：主应用暴露 8091，MCP 仅通过 Docker 内网 `http://mcp:8091/mcp` 提供服务。
- 已验收 Flow Agent、Auto Agent、训练查询接口、知识库索引/检索，以及 MCP Client 到 Server 的 SSE 会话建立。
- `mvn -DskipTests package` 已通过。

- 增加内容资产治理：导入记录持久化、创建/更新/删除版本快照、逻辑删除和删除后向量清理。

## 确认落库接口

客户端确认 Agent 输出的训练方案后调用 `POST /api/training/plans/confirm`，请求包含用户、日期、执行时间和训练内容 ID。应用服务会校验用户、日期范围、内容存在性和重复内容，并在同一事务中完成计划创建与内容绑定；事务提交后才刷新动态调度。撤销使用 `POST /api/training/plans/{planId}/revoke`，审计查询使用 `GET /api/training/plans/{planId}/audits?userId={userId}`。

Agent 本身不会调用该写接口，仍然只负责生成待确认建议。

## 训练知识库接口

- `POST /api/knowledge/training-contents/{contentId}/index`：将指定训练内容切分、向量化并写入知识库。
- `GET /api/knowledge/search?query=数字工作记忆`：按语义检索训练内容，返回命中文本、相似度和训练内容元数据。
- `POST /api/knowledge/training-contents/index`：按 `contentIds` 批量幂等索引训练内容，重复 ID 会自动去重。
- `DELETE /api/knowledge/training-contents/{contentId}/index`：删除指定训练内容的全部向量块。
- `POST /api/knowledge/training-contents/index/async`：提交异步批量索引任务，立即返回 `taskId`。
- `GET /api/knowledge/index-tasks/{taskId}`：查询异步索引任务状态和每个内容的切块数量。

## 文档导入和内容版本

- `POST /api/training/contents/import`：使用 `multipart/form-data` 上传 PDF、DOCX、TXT 或 MD 文件；字段包括 `file`、可选 `title`、`contentType`、`difficulty`、`status` 和 `index`。
- 导入成功后创建训练内容，初始 `version=1`；`index=true` 且已启用 RAG 时会自动提交异步索引任务。
- `PUT /api/training/contents/{contentId}`：可传 `expectedVersion`。版本匹配时更新并递增版本；版本过期时返回业务码 `0002`，避免覆盖并发更新；更新成功后自动提交当前内容的向量重建任务。
- 异步索引任务状态已持久化到 MySQL 表 `training_knowledge_index_task`，支持应用启动恢复、最多 3 次失败重试和任务结果查询。

- `GET /api/training/contents/{contentId}/versions`：查询内容的全部版本快照，包含来源类型和导入文件名。
- `GET /api/training/imports/{importId}`：查询文档导入记录、解析状态和索引任务 ID。
- `DELETE /api/training/contents/{contentId}`：逻辑删除内容，保留训练计划引用和版本历史，并删除对应知识库向量。

- `POST /api/training/contents/import/batch`：批量上传多个 PDF、DOCX、TXT 或 MD 文件，共享一个异步索引任务，每个文件保留独立导入记录。
- `GET /api/training/contents/{contentId}/versions/diff?fromVersion=1&toVersion=2`：返回两个版本的变更字段及前后快照。
- GET /api/training/executions/{executionId}：查询单次训练执行记录及最终状态。
- GET /api/training/executions/summary?userId=1&from=2026-09-01&to=2026-10-01：汇总周期内执行次数、成功率、平均分和耗时。
- GET /api/monitoring/overview?staleMinutes=30&limit=50：只读汇总训练任务、执行记录和知识库索引任务状态，并返回失败/超时告警。
- GET /api/health/overview：检查 MySQL、pgvector、MCP Client 和 Agent 的运行状态，返回 UP、DEGRADED 或 DOWN。
- GET /api/monitoring/prometheus?staleMinutes=30&limit=50：以 Prometheus text format 输出任务状态、健康状态、告警数量和 MCP 工具数量。

云端已验证训练内容 1 可以成功索引并命中“工作记忆数字广度验收题”。单个和异步批量索引均采用先删除后重建策略，重复索引不会产生重复向量；批量接口会对内容 ID 去重。

## 主要目录

```text
cognitive-training-agent-platform
├─ cog-train-plat-api              # HTTP DTO 和响应模型
├─ cog-train-plat-domain           # 用户、内容、计划、任务、执行记录领域模型
├─ cog-train-plat-infrastructure   # MyBatis DAO、Mapper 和仓储实现
├─ cog-train-plat-trigger          # 训练 HTTP 接口和动态调度器
├─ cog-train-plat-app              # Spring Boot 入口、Agent、MCP Server
├─ docs/dev-ops                    # MySQL、审计和 pgvector 初始化及运维资料
├─ docker-compose.yml              # 本地依赖与应用
├─ docker-compose.cloud.yml        # 云端 app + MCP 侧车
└─ .env.example
```

## 本地复现

```bash
copy .env.example .env
mvn -DskipTests package
docker compose up -d mysql vector_db
docker compose up -d app
```

本地需要启用知识库时，将 `.env` 中的 `COGNITIVE_RAG_ENABLED` 设为 `true`；pgvector 建表脚本位于 `docs/dev-ops/pgvector/003_cognitive_training_vector_store.sql`。

## 云服务器部署

项目配置中发现的服务器地址是 `<SERVER_HOST>`，不是自定义域名；`apis.itedus.cn` 是 AI 接口地址，不能当作服务器域名使用。

云服务器当前部署状态：主应用 `8091` 已运行；MySQL `13306`、pgvector `15432` 使用服务器已有服务；MCP 不映射公网端口，仅在 Compose 内网监听。

具备 SSH 用户和密钥后，在服务器执行：

```bash
cd /opt/cognitive-training-agent-platform
cp .env.cloud.example .env
# 修改 .env 中的 DB_PASSWORD、SPRING_AI_OPENAI_API_KEY 等配置
# 首次部署需执行 docs/dev-ops/mysql/sql/002_training_plan_audit.sql、docs/dev-ops/mysql/sql/003_training_knowledge_index_task.sql 和 docs/dev-ops/mysql/sql/004_training_content_audit.sql
# 启用 RAG 时设置 COGNITIVE_RAG_ENABLED=true，并配置 PGVECTOR_URL、PGVECTOR_USERNAME、PGVECTOR_PASSWORD
# pgvector 初始化脚本：docs/dev-ops/pgvector/003_cognitive_training_vector_store.sql
docker compose -f docker-compose.cloud.yml up -d --build mcp
docker compose -f docker-compose.cloud.yml up -d app
```

如果使用云服务器上已有 MySQL/pgvector，而不是 Compose 新建容器，使用 `SPRING_PROFILES_ACTIVE=cloud`，并设置 `DB_HOST=<SERVER_HOST>`、`DB_PORT=13306`、`DB_NAME=cognitive_training` 等变量。

## 当前仍建议继续完善的方向

1. 增加内容版本差异、导入记录和索引任务的前端可视化操作。
2. 增加定时训练总结、Agent 任务状态持久化和更细粒度的失败重试策略。
3. 增加正式环境鉴权、权限隔离、限流，以及基于测试容器的 MCP/Agent 集成测试。















## Prometheus 与 Grafana 监控

- `docker-compose.monitoring.yml` 启动 Prometheus，抓取 `/api/monitoring/prometheus`；Prometheus 只绑定服务器本机 `127.0.0.1:9090`。
- `monitoring/prometheus/alert_rules.yml` 包含应用不可用、组件异常、超时任务和索引失败告警。
- Grafana 使用 `docker-compose.grafana.yml` 独立启动，启动前必须通过 `GRAFANA_ADMIN_PASSWORD` 显式设置管理员密码，服务同样只绑定 `127.0.0.1:3000`。
- 远程查看 Prometheus 可使用 SSH 隧道：`ssh -L 9090:127.0.0.1:9090 root@<SERVER_HOST>`；Grafana 使用 `ssh -L 3000:127.0.0.1:3000 root@<SERVER_HOST>`。

## 云端端到端验收

- scripts/verify-cloud.ps1 默认只读检查健康状态、训练任务、运行监控和 Prometheus 指标。
- 使用 .\scripts\verify-cloud.ps1 -IncludeAgent 可显式执行一次 Flow Agent SSE 验收；该步骤会调用模型。
- 详细步骤见 docs/dev-ops/cloud-e2e-verification.md。

## 训练管理前端

- cog-train-plat-web 提供训练任务、运行健康、监控摘要和 Flow Agent SSE 的最小演示页面。
- 前端通过 docker-compose.web.yml 启动，默认只绑定云服务器本机 127.0.0.1:8088。
- 使用说明见 docs/dev-ops/web-console.md。

