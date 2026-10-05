# 训练管理前端

`cog-train-plat-web` 是当前项目的最小可演示前端，使用 Nginx 提供静态页面并反向代理 `/api` 到主应用。当前包含：

- 今日训练任务查询
- MySQL、pgvector、MCP Client 和 Agent 健康状态
- 训练任务、执行记录和索引任务监控摘要
- Flow Agent SSE 流式编排建议

## 云端启动

前端只绑定云服务器本机 8088 端口，不直接暴露未鉴权的训练数据。云端执行：

```bash
docker compose -f docker-compose.web.yml up -d --build web
```

本地建立隧道：

```powershell
ssh -N -L 8088:127.0.0.1:8088 root@<SERVER_HOST>
```

浏览器打开：

```text
http://localhost:8088
```

页面通过同源 `/api` 访问主应用，因此浏览器不需要直接访问 8091，也不需要额外配置跨域。
