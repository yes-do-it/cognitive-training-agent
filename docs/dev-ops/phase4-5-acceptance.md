# 第四、五阶段：自动化测试与云端验收

## 1. 自动化测试

核心领域层和调度层已接入 Maven 测试：

~~~bash
mvn -pl cog-train-plat-trigger -am test
~~~

本轮结果：

- TrainingPlanDomainServiceTest：6 个测试通过
- TrainingExecutionDomainServiceTest：9 个测试通过
- TrainingTaskSchedulerTest：4 个测试通过
- 合计：19 个测试，0 失败，0 错误

覆盖规则：

- 每日训练任务按“用户 + 计划 + 日期”幂等生成
- 并发生成时使用数据库条件插入保护
- 计划日期范围和用户启用状态校验
- 待执行任务才能调整训练内容
- 任务状态按预期状态条件更新
- 执行启动幂等键重复请求
- 并发抢占失败后的幂等恢复
- 幂等键跨任务复用拦截
- 重复提交相同训练结果幂等返回
- 重复提交不同结果拒绝
- 得分范围和历史查询日期范围校验
- 调度计划注册、计划变更重排、计划删除取消
- 应用重启后当天计划的补偿生成

完整打包命令：

~~~bash
mvn -DskipTests package
~~~

已通过。应用模块原有 ApiTest 在其 POM 中显式配置为 skipTests=true，它依赖完整 Spring Boot 外部环境；本轮没有擅自开启，避免本机数据库配置影响构建结果。

## 2. 云端验收

服务器：<SERVER_HOST>

验收时间：2026-10-05

验收命令：

~~~powershell
.\scripts\verify-cloud.ps1 -BaseUrl "http://<SERVER_HOST>:8091" -TrainingDate "2026-10-05"
~~~

结果：

- 应用健康检查：通过
- MySQL：UP
- pgvector：UP
- MCP Client：UP，工具数量 4
- Agent Bean：ENABLED
- 训练任务查询：HTTP 200
- 运行监控概览：HTTP 200
- Prometheus 指标：HTTP 200
- 前端 http://<SERVER_HOST>:8088/：HTTP 200
- MCP 容器：healthy
- 调度器启动恢复：成功恢复 3 个启用计划
- 管理端状态保护：已完成任务不允许按当天待执行任务规则调整

本轮已获得授权并实际调用当前配置的外部模型完成 SSE 验收：

- Flow Agent：HTTP 200，Content-Type 为 text/event-stream，返回 263 个事件，包含连续 event:token 增量输出，最后返回 event:done 和 data:[DONE]。
- Auto Agent：HTTP 200，Content-Type 为 text/event-stream，返回 303 个事件，包含连续 event:token 增量输出，最后返回 event:done 和 data:[DONE]。
- 两次调用均只读取训练上下文，没有修改训练计划或任务。
- 当前云端模型配置为 gpt-4.1-mini，接口入口为 https://apis.itedus.cn。

## 3. 复现步骤

~~~bash
mvn -pl cog-train-plat-trigger -am test
mvn -DskipTests package
~~~

云端重新部署应用镜像时，只需上传：

~~~text
cog-train-plat-app/target/cog-train-plat-app.jar
~~~

然后在服务器执行：

~~~bash
cd /opt/cognitive-training-agent-platform
docker compose -f docker-compose.cloud.yml up -d --build mcp app
~~~

数据库、pgvector、前端和监控容器不需要随每次应用代码变更重建。