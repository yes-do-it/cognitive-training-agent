const $ = (id) => document.getElementById(id);
const state = { agentMode: "flow", agentController: null, activeView: "dashboard", tasks: [], contents: [], plans: [], monitor: null, proposalApplied: false, pendingProposal: null, editingPlanId: null, adjustmentTaskId: null, agentConversation: [], agentAssistantBubble: null, agentAssistantStarted: false };

function unwrap(response) {
  return response && Object.prototype.hasOwnProperty.call(response, "data") ? response.data : response;
}

async function request(path, options = {}) {
  const headers = { Accept: "application/json", ...(options.headers || {}) };
  if (options.body && !headers["Content-Type"]) headers["Content-Type"] = "application/json";
  const response = await fetch(path, { ...options, headers });
  const text = await response.text();
  let payload = null;
  try { payload = text ? JSON.parse(text) : null; } catch (_) { payload = text; }
  if (!response.ok) throw new Error(payload?.info || payload?.message || `HTTP ${response.status}`);
  return unwrap(payload);
}

function setConnection(text, type) {
  const element = $("connectionStatus");
  element.textContent = text;
  element.className = `status-pill ${type}`;
}

function showToast(message, type = "normal") {
  const toast = $("toast");
  toast.textContent = message;
  toast.className = `toast show ${type}`;
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => { toast.className = "toast"; }, 3200);
}

function statusTag(status) {
  const normalized = String(status || "-").toUpperCase();
  return `<span class="tag ${normalized.toLowerCase()}">${normalized}</span>`;
}

function formatCounts(counts) {
  const entries = Object.entries(counts || {});
  return entries.length ? entries.map(([key, value]) => `${key}: ${value}`).join(" · ") : "暂无记录";
}

function formatDate(value) {
  return value || "-";
}

function businessToday() {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.filter((part) => part.type !== "literal").map((part) => [part.type, part.value]));
  return values.year + "-" + values.month + "-" + values.day;
}

function renderHealth(health) {
  const lists = [$("componentList"), $("componentListMonitor")].filter(Boolean);
  const status = health?.status || "UNKNOWN";
  [$("healthStatus"), $("healthStatusMonitor")].filter(Boolean).forEach((element) => { element.innerHTML = statusTag(status); });
  const components = health?.components || {};
  lists.forEach((list) => {
    list.replaceChildren();
    Object.entries(components).forEach(([name, item]) => {
      const row = document.createElement("div");
      const label = { mysql: "MySQL 主数据库", pgvector: "pgvector 知识库", mcpClient: "MCP 工具服务", agent: "Agent 编排引擎" }[name] || name;
      row.innerHTML = `<span>${label}</span><span>${statusTag(item?.status)}<small>${item?.message || ""}</small></span>`;
      list.appendChild(row);
    });
  });
  $("mcpToolCount").textContent = components.mcpClient?.toolCount ?? "0";
}

function taskAction(task) {
  const status = String(task.status || "").toUpperCase();
  const isDemoAdmin = Number($("userId").value || 1) === 1;
  if (status === "PENDING") {
    const adjust = isDemoAdmin
      ? ' <button class="table-action" data-adjust-task="' + task.id + '">调整内容</button>'
      : "";
    return '<button class="table-action" data-start-task="' + task.id + '">开始训练</button>' + adjust;
  }
  if (status === "RUNNING") return '<span class="muted">执行中</span>';
  return '<span class="muted">已记录</span>';
}

function renderTasks(tasks) {
  state.tasks = tasks || [];
  const table = $("taskTable");
  const empty = $("taskEmpty");
  const body = table.querySelector("tbody");
  body.replaceChildren();
  if (!state.tasks.length) { table.hidden = true; empty.hidden = false; return; }
  empty.hidden = true;
  table.hidden = false;
  state.tasks.forEach((task) => {
    const row = document.createElement("tr");
    row.innerHTML = `<td><strong>内容 #${task.contentId ?? "-"}</strong><small>任务 #${task.id ?? "-"}</small></td><td>计划 #${task.planId ?? "-"}</td><td>${formatDate(task.trainingDate)}</td><td>${statusTag(task.status)}</td><td>${taskAction(task)}</td>`;
    body.appendChild(row);
  });
  body.querySelectorAll("[data-start-task]").forEach((button) => button.addEventListener("click", () => startTask(button.dataset.startTask)));
  body.querySelectorAll("[data-adjust-task]").forEach((button) => button.addEventListener("click", () => openTaskAdjustment(button.dataset.adjustTask)));
}

function renderMonitoring(monitor) {
  state.monitor = monitor || {};
  const tasks = monitor?.trainingTaskStatusCounts || {};
  const executions = monitor?.executionStatusCounts || {};
  const indexes = monitor?.indexTaskStatusCounts || {};
  const alerts = monitor?.alerts || [];
  $("alertCount").textContent = alerts.length;
  const summary = `<dt>训练任务</dt><dd>${formatCounts(tasks)}</dd><dt>执行记录</dt><dd>${formatCounts(executions)}</dd><dt>知识索引</dt><dd>${formatCounts(indexes)}</dd>`;
  $("monitoringSummary").innerHTML = summary;
  $("monitoringDetail").innerHTML = `${summary}<dt>告警</dt><dd>${alerts.length ? `${alerts.length} 条待处理` : "暂无告警"}</dd>`;
  const alertList = $("alertList");
  alertList.replaceChildren();
  if (!alerts.length) { alertList.innerHTML = `<div class="empty">暂无告警，运行状态良好。</div>`; return; }
  alerts.forEach((alert) => { const item = document.createElement("div"); item.className = "alert-item"; item.innerHTML = `<span class="alert-dot"></span><div><strong>${alert.type || alert.category || "运行告警"}</strong><p>${alert.message || alert.detail || JSON.stringify(alert)}</p></div>`; alertList.appendChild(item); });
}

function renderExecutionSummary(summary) {
  if (!summary) return;
  $("executionTotal").textContent = summary.totalExecutions ?? 0;
  $("executionSuccess").textContent = summary.successCount ?? 0;
  $("executionScore").textContent = summary.averageScore ?? "-";
  $("executionRate").textContent = summary.successRate == null ? "-" : `${Math.round(Number(summary.successRate) * 100)}%`;
}

async function loadDashboard() {
  const userId = encodeURIComponent($("userId").value || "1");
  const dateValue = $("trainingDate").value;
  const date = encodeURIComponent(dateValue);
  setConnection("正在同步", "pending");
  try {
    const from = new Date(`${dateValue}T00:00:00`);
    from.setDate(from.getDate() - 30);
    const fromDate = from.toISOString().slice(0, 10);
    const [health, tasks, monitoring, execution] = await Promise.all([
      request("/api/health/overview"),
      request(`/api/training/tasks?userId=${userId}&trainingDate=${date}`),
      request("/api/monitoring/overview?staleMinutes=30&limit=50"),
      request(`/api/training/executions/summary?userId=${userId}&from=${fromDate}&to=${date}`)
    ]);
    renderHealth(health);
    renderTasks(tasks);
    renderMonitoring(monitoring);
    renderExecutionSummary(execution);
    $("taskCount").textContent = tasks?.length || 0;
    $("completedCount").textContent = tasks?.filter((task) => task.status === "COMPLETED").length || 0;
    $("progressHint").textContent = tasks?.length ? `${Math.round((tasks.filter((task) => task.status === "COMPLETED").length / tasks.length) * 100)}% 今日完成` : "暂无今日任务";
    $("taskHint").textContent = `${dateValue} · 用户 ${$("userId").value || "1"}`;
    $("agentContextUser").textContent = `#${$("userId").value || "1"}`;
    $("agentContextDate").textContent = dateValue;
    setConnection(health?.status === "UP" ? "云端已连接" : "服务需关注", health?.status === "UP" ? "up" : "degraded");
  } catch (error) {
    setConnection("服务异常", "down");
    [$("healthStatus"), $("healthStatusMonitor")].filter(Boolean).forEach((element) => { element.innerHTML = statusTag("DOWN"); });
    showToast(`数据加载失败：${error.message}`, "error");
  }
}

async function startTask(taskId) {
  try {
    const result = await request("/api/training/executions/tasks/" + taskId + "/start", { method: "POST", body: JSON.stringify({ userId: Number($("userId").value || 1), idempotencyKey: "web-" + taskId + "-" + Date.now() }) });
    showToast("训练已开始，执行记录 #" + (result?.id || "已创建"), "success");
    if (result?.id) $("finishExecutionId").value = result.id;
    switchView("executions");
    await loadDashboard();
  } catch (error) { showToast("启动失败：" + error.message, "error"); }
}

function closeTaskAdjustment() {
  state.adjustmentTaskId = null;
  $("taskAdjustmentPanel").hidden = true;
  $("adjustmentResult").textContent = "";
  $("adjustReason").value = "";
}

async function openTaskAdjustment(taskId) {
  const task = state.tasks.find((item) => Number(item.id) === Number(taskId));
  if (!task) return;
  if (String(task.status || "").toUpperCase() !== "PENDING") {
    showToast("只有待执行任务可以调整", "error");
    return;
  }
  const panel = $("taskAdjustmentPanel");
  panel.hidden = false;
  $("taskAdjustmentHint").textContent = "任务 #" + task.id + " · " + task.trainingDate + " · 当前内容 #" + task.contentId;
  $("adjustmentResult").textContent = "正在加载可用训练内容...";
  try {
    if (!state.contents.length) state.contents = await request("/api/training/contents");
    const enabledContents = state.contents.filter((content) => String(content.status || "").toUpperCase() === "ENABLED");
    const select = $("adjustContentId");
    select.replaceChildren();
    enabledContents.forEach((content) => {
      const option = document.createElement("option");
      option.value = content.id;
      option.textContent = (content.title || "训练内容") + "（#" + content.id + " · " + (content.contentType || "认知训练") + "）";
      select.appendChild(option);
    });
    if (!enabledContents.length) throw new Error("暂无启用的训练内容");
    const current = enabledContents.find((content) => Number(content.id) === Number(task.contentId));
    select.value = String(current?.id || enabledContents[0].id);
    $("adjustReason").value = "";
    $("adjustmentResult").textContent = "";
    state.adjustmentTaskId = Number(task.id);
    panel.scrollIntoView({ behavior: "smooth", block: "nearest" });
  } catch (error) {
    $("adjustmentResult").textContent = "训练内容加载失败：" + error.message;
  }
}

async function saveTaskAdjustment() {
  const taskId = state.adjustmentTaskId;
  const contentId = Number($("adjustContentId").value);
  const reason = $("adjustReason").value.trim();
  if (!taskId) {
    showToast("请先选择一个待执行任务", "error");
    return;
  }
  if (!Number.isInteger(contentId) || contentId <= 0) {
    $("adjustmentResult").textContent = "请选择有效的训练内容";
    return;
  }
  if (!reason) {
    $("adjustmentResult").textContent = "请填写调整原因，便于保留管理记录";
    $("adjustReason").focus();
    return;
  }
  const button = $("saveAdjustmentButton");
  button.disabled = true;
  $("adjustmentResult").textContent = "正在保存调整...";
  try {
    await request("/api/admin/training/tasks/" + taskId + "/content", {
      method: "PUT",
      body: JSON.stringify({ contentId, operatorId: "demo-admin", reason })
    });
    showToast("当天任务 #" + taskId + " 已调整", "success");
    closeTaskAdjustment();
    await loadDashboard();
  } catch (error) {
    $("adjustmentResult").textContent = "调整失败：" + error.message;
    showToast("调整失败：" + error.message, "error");
  } finally {
    button.disabled = false;
  }
}

function switchView(view) {
  state.activeView = view;
  document.querySelectorAll("[data-view-panel]").forEach((panel) => panel.classList.toggle("active", panel.dataset.viewPanel === view));
  document.querySelectorAll(".nav-item").forEach((button) => button.classList.toggle("active", button.dataset.view === view));
  const titles = { dashboard: "训练驾驶舱", agent: "Agent 编排", plans: "训练计划", contents: "训练内容库", executions: "执行记录", monitoring: "运行监控" };
  $("pageTitle").textContent = titles[view] || "训练驾驶舱";
  if (view === "monitoring") loadDashboard();
  if (view === "executions") loadDashboard();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function appendTimeline(text, type = "stage") {
  const timeline = $("agentTimeline");
  if (timeline.querySelector(".timeline-placeholder")) timeline.replaceChildren();
  const item = document.createElement("div");
  item.className = `timeline-item ${type}`;
  item.innerHTML = `<span class="timeline-icon">${type === "error" ? "!" : type === "token" ? "✦" : "✓"}</span><span>${text}</span>`;
  timeline.appendChild(item);
  timeline.scrollTop = timeline.scrollHeight;
}

function appendChatMessage(role, text) {
  const log = $("agentChatLog");
  if (!log) return null;
  log.querySelector(".chat-placeholder")?.remove();
  const item = document.createElement("div");
  item.className = `chat-message ${role}`;
  const label = document.createElement("span");
  label.className = "chat-label";
  label.textContent = role === "user" ? "你" : role === "system" ? "系统" : "Agent";
  const body = document.createElement("div");
  body.className = "chat-body";
  body.textContent = text || "";
  item.append(label, body);
  log.appendChild(item);
  log.scrollTop = log.scrollHeight;
  return body;
}

function renderAgentFinalAnswer(raw) {
  const body = state.agentAssistantBubble;
  if (!body) return;
  const proposal = extractProposal(raw);
  const naturalAnswer = raw.replace(/```(?:json)?\\s*[\s\S]*?```/gi, "").trim();
  let display = naturalAnswer || "Agent 已完成分析。";
  if (proposal) {
    const contents = proposal.contentIds?.length ? proposal.contentIds.join("、") : "待选择";
    display += `\n\n已生成待确认方案：${proposal.name || "认知训练方案"}。\n周期：${proposal.startDate || "-"} 至 ${proposal.endDate || "-"}；时间：${proposal.scheduleTime || "-"}；内容：${contents}。\n请检查右侧字段后输入“确认”创建计划，也可以点击右侧“确认并创建计划”；输入“取消”可放弃。`;
  }
  body.textContent = display;
}

function visibleAgentText(raw) {
  let visible = raw || "";
  const fenceStart = visible.search(new RegExp(String.fromCharCode(96, 96, 96) + "(?:json)?\\s*", "i"));
  if (fenceStart >= 0) visible = visible.slice(0, fenceStart);
  const jsonStart = visible.search(/\{\s*["']?(?:name|startDate|endDate|contentIds|reason)\b/i);
  if (jsonStart >= 0) visible = visible.slice(0, jsonStart);
  return visible.trim();
}
function parseProposalCandidate(candidate) {
  try {
    const parsed = JSON.parse(candidate);
    if (!parsed || typeof parsed !== "object") return null;
    const contentIds = Array.isArray(parsed.contentIds)
      ? parsed.contentIds.map((value) => Number(value)).filter((value) => Number.isInteger(value) && value > 0)
      : [];
    if (!parsed.name && !contentIds.length && !parsed.startDate) return null;
    return { ...parsed, contentIds };
  } catch (_) {
    return null;
  }
}

function extractProposal(text) {
  const fenced = [...text.matchAll(/```(?:json)?\\s*([\s\S]*?)```/gi)].map((match) => match[1].trim());
  for (const candidate of fenced) {
    const proposal = parseProposalCandidate(candidate);
    if (proposal) return proposal;
  }
  for (let start = text.indexOf("{"); start >= 0; start = text.indexOf("{", start + 1)) {
    let depth = 0;
    let quoted = false;
    let escaped = false;
    for (let index = start; index < text.length; index += 1) {
      const char = text[index];
      if (quoted) {
        if (escaped) escaped = false;
        else if (char === "\\") escaped = true;
        else if (char === '"') quoted = false;
        continue;
      }
      if (char === '"') { quoted = true; continue; }
      if (char === "{") depth += 1;
      if (char === "}") {
        depth -= 1;
        if (depth === 0) {
          const proposal = parseProposalCandidate(text.slice(start, index + 1));
          if (proposal) return proposal;
          break;
        }
      }
    }
  }
  return null;
}

function applyProposal(proposal) {
  if (!proposal || typeof proposal !== "object" || state.proposalApplied) return;
  let filled = 0;
  if (typeof proposal.name === "string" && proposal.name.trim()) { $("planName").value = proposal.name.trim(); filled += 1; }
  if (/^\d{4}-\d{2}-\d{2}$/.test(proposal.startDate || "")) { $("planStartDate").value = proposal.startDate; filled += 1; }
  if (/^\d{4}-\d{2}-\d{2}$/.test(proposal.endDate || "")) { $("planEndDate").value = proposal.endDate; filled += 1; }
  if (/^\d{2}:\d{2}/.test(proposal.scheduleTime || "")) { $("planScheduleTime").value = proposal.scheduleTime.slice(0, 5); filled += 1; }
  if (proposal.contentIds?.length) { $("planContentIds").value = proposal.contentIds.join(","); filled += 1; }
  if (!filled) return;
  state.proposalApplied = true;
  state.pendingProposal = { ...proposal };
  const reason = typeof proposal.reason === "string" && proposal.reason.trim() ? ` ${proposal.reason.trim()}` : "";
  $("confirmResult").textContent = `Agent 已识别结构化方案并填入确认区。可直接输入“确认”创建计划，也可以先修改右侧字段；输入“取消”放弃方案。${reason}`;
  appendTimeline("已识别结构化方案，已自动填入人工确认区", "done");
}

function processSseEvent(rawEvent) {
  const lines = rawEvent.replace(/\r/g, "").split("\n");
  let eventName = "message";
  const data = [];
  lines.forEach((line) => {
    if (line.startsWith("event:")) eventName = line.slice(6).trim();
    if (line.startsWith("data:")) data.push(line.slice(5).replace(/^ /, ""));
  });
  const value = data.join("\n");
  if (!value && eventName !== "done") return;
  if (eventName === "stage") { appendTimeline(value, "stage"); $("agentRunStatus").textContent = value; return; }
  if (eventName === "evidence") { renderAgentEvidence(value); appendTimeline("已读取最近 30 天训练趋势", "stage"); return; }
  if (eventName === "token" || eventName === "message") {
    $("agentOutput").textContent += value;
    $("agentOutput").scrollTop = $("agentOutput").scrollHeight;
    if (state.agentAssistantBubble) {
      if (!state.agentAssistantStarted) state.agentAssistantStarted = true;
      const visible = visibleAgentText($("agentOutput").textContent);
      state.agentAssistantBubble.textContent = visible || "正在生成分析结果……";
      $("agentChatLog").scrollTop = $("agentChatLog").scrollHeight;
    }
    applyProposal(extractProposal($("agentOutput").textContent));
    return;
  }
  if (eventName === "error") { appendTimeline(value, "error"); throw new Error(value); }
  if (eventName === "done" || value === "[DONE]") { appendTimeline("方案生成完成，等待人工确认", "done"); $("agentRunStatus").textContent = "待确认"; }
}
function renderAgentEvidence(value) {
  const box = $("agentEvidence");
  if (!box) return;
  try {
    const stats = JSON.parse(value);
    const successRate = Number(stats.successRate || 0) * 100;
    const performance = (stats.contentPerformance || []).map((item) =>
      `内容 #${item.contentId}：${Number(item.averageScore || 0).toFixed(0)} 分 / 成功率 ${(Number(item.successRate || 0) * 100).toFixed(0)}%`
    ).join("；");
    box.innerHTML = `<p class="eyebrow">TRAINING TREND</p><strong>本次分析依据</strong><div class="evidence-metrics"><span>完成 ${stats.totalExecutions || 0} 次</span><span>成功率 ${successRate.toFixed(0)}%</span><span>平均分 ${Number(stats.averageScore || 0).toFixed(0)}</span><span>平均耗时 ${Number(stats.averageDurationSeconds || 0).toFixed(0)} 秒</span></div><p class="muted">${performance || "暂无按内容拆分的执行记录"}</p>`;
  } catch (error) {
    box.querySelector(".muted")?.replaceChildren(document.createTextNode("趋势数据暂时无法展示，但不影响 Agent 继续分析。"));
  }
}

function isConfirmMessage(message) {
  return /^(好的?[，,\s]*)?(确认|同意|可以|按这个执行|按方案执行|创建计划|保存方案)(一下|这份方案|方案)?[。！!，,\s]*$/i.test(message.trim());
}

function isCancelMessage(message) {
  return /^(好的?[，,\s]*)?(取消|不要了|重新规划|重来)(这份方案|方案)?[。！!，,\s]*$/i.test(message.trim());
}

function clearPendingProposal() {
  state.pendingProposal = null;
  state.proposalApplied = false;
}

async function runAgent() {
  const userMessage = $("agentMessage").value.trim();
  if (!userMessage) { showToast("请先输入训练问题", "error"); return; }

  if (isConfirmMessage(userMessage) || isCancelMessage(userMessage)) {
    $("agentMessage").value = "";
    appendChatMessage("user", userMessage);
    if (isCancelMessage(userMessage)) {
      clearPendingProposal();
      $("confirmResult").textContent = "已取消待确认方案。你可以重新描述训练目标。";
      $("agentRunStatus").textContent = "待运行";
      appendChatMessage("assistant", "好的，已取消这份待确认训练方案。你可以重新描述训练目标，我会重新分析。");
      return;
    }
    if (!state.pendingProposal) {
      appendChatMessage("assistant", "当前没有待确认的训练方案。请先描述训练目标，让 Agent 生成一份方案。");
      $("agentRunStatus").textContent = "待运行";
      return;
    }
    state.agentAssistantBubble = appendChatMessage("assistant", "正在确认训练方案并创建计划……");
    $("agentRunStatus").textContent = "确认中";
    await confirmPlan({ fromChat: true });
    return;
  }

  const context = state.agentConversation.slice(-4).map((turn, index) =>
    "第 " + (index + 1) + " 轮用户：" + turn.user + "\n第 " + (index + 1) + " 轮 Agent：" + turn.assistant.slice(0, 2400)
  ).join("\n\n");
  const message = context
    ? "这是同一场编排对话的历史上下文：\n" + context + "\n\n本轮用户追问：" + userMessage
    : userMessage;
  const endpoint = state.agentMode === "auto" ? "auto" : "flow";
  const button = $("agentButton");
  const cancel = $("agentCancelButton");
  state.agentController = new AbortController();
  state.proposalApplied = false;
  state.agentAssistantStarted = false;
  state.agentAssistantBubble = null;
  button.disabled = true; cancel.disabled = false;
  $("agentMessage").value = "";
  $("agentOutput").textContent = "";
  appendChatMessage("user", userMessage);
  state.agentAssistantBubble = appendChatMessage("assistant", "正在读取训练数据并生成建议……");
  $("agentTimeline").innerHTML = "";
  $("agentRunStatus").textContent = "连接 Agent...";
  appendTimeline("本轮问题：" + userMessage, "stage");
  appendTimeline("已连接 " + (state.agentMode === "auto" ? "Auto Agent" : "Flow Agent"), "stage");
  try {

    const response = await fetch("/api/agent/" + endpoint + "/stream", { method: "POST", headers: { Accept: "text/event-stream", "Content-Type": "application/json", "Cache-Control": "no-cache" }, body: JSON.stringify({ userId: Number($("userId").value || 1), trainingDate: $("trainingDate").value || null, message }), signal: state.agentController.signal });
    if (!response.ok || !response.body) throw new Error("HTTP " + response.status);
    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const events = buffer.replace(/\r\n/g, "\n").split("\n\n");
      buffer = events.pop() || "";
      events.forEach(processSseEvent);
      await new Promise((resolve) => requestAnimationFrame(resolve));
    }
    buffer += decoder.decode();
    if (buffer.trim()) processSseEvent(buffer);
    if ($("agentRunStatus").textContent !== "待确认") { appendTimeline("连接已结束，等待人工确认", "done"); $("agentRunStatus").textContent = "待确认"; }
    const assistantMessage = $("agentOutput").textContent.trim();
    if (assistantMessage && $("agentRunStatus").textContent === "待确认") {
      renderAgentFinalAnswer(assistantMessage);
      state.agentConversation.push({ user: userMessage, assistant: assistantMessage });
      state.agentConversation = state.agentConversation.slice(-4);
    }
  } catch (error) {
    $("agentMessage").value = userMessage;
    if (state.agentAssistantBubble) state.agentAssistantBubble.textContent = "本次调用失败：" + error.message;
    if (error.name === "AbortError") { appendTimeline("已停止本次编排，输入内容已保留", "error"); $("agentRunStatus").textContent = "已停止"; }
    else { appendTimeline("本次调用失败：" + error.message, "error"); $("agentRunStatus").textContent = "执行失败"; showToast("Agent 调用失败：" + error.message, "error"); }
  } finally {
    button.disabled = false; cancel.disabled = true; state.agentController = null;
  }
}

async function confirmPlan(options = {}) {
  const fromChat = options.fromChat === true;
  const replyRole = fromChat ? "assistant" : "system";
  const ids = $("planContentIds").value.split(",").map((value) => Number(value.trim())).filter((value) => Number.isInteger(value) && value > 0);
  if (!ids.length) {
    const message = "确认失败：至少需要一个训练内容 ID，请先补充方案内容。";
    $("confirmResult").textContent = message;
    if (fromChat && state.agentAssistantBubble) state.agentAssistantBubble.textContent = message;
    else appendChatMessage(replyRole, message);
    showToast("至少填写一个训练内容 ID", "error");
    return false;
  }
  const payload = { userId: Number($("userId").value || 1), name: $("planName").value.trim(), startDate: $("planStartDate").value, endDate: $("planEndDate").value, scheduleTime: $("planScheduleTime").value ? $("planScheduleTime").value + ":00" : "", contents: ids.map((contentId, index) => ({ contentId, sortOrder: index })) };
  if (!payload.name || !payload.startDate || !payload.endDate || !payload.scheduleTime) {
    const message = "确认失败：方案名称、起止日期和执行时间不能为空。";
    $("confirmResult").textContent = message;
    if (fromChat && state.agentAssistantBubble) state.agentAssistantBubble.textContent = message;
    else appendChatMessage(replyRole, message);
    showToast("方案字段不完整", "error");
    return false;
  }
  try {
    const result = await request("/api/training/plans/confirm", { method: "POST", body: JSON.stringify(payload) });
    const planId = result?.plan?.id || "";
    clearPendingProposal();
    $("confirmResult").textContent = "已确认计划 #" + planId + "，调度器已刷新。";
    const successMessage = "已确认训练方案，计划 #" + planId + " 创建成功，调度器已刷新。你可以继续询问下一步训练安排。";
    if (fromChat && state.agentAssistantBubble) state.agentAssistantBubble.textContent = successMessage;
    else appendChatMessage(replyRole, successMessage);
    appendTimeline("训练计划 #" + planId + " 创建成功", "done");
    $("agentRunStatus").textContent = "已确认";
    showToast("训练方案已落库并刷新调度", "success");
    await loadDashboard();
    await loadPlans();
    return true;
  } catch (error) {
    const message = "训练计划创建失败：" + error.message;
    $("confirmResult").textContent = "提交失败：" + error.message;
    if (fromChat && state.agentAssistantBubble) state.agentAssistantBubble.textContent = message;
    else appendChatMessage(replyRole, message);
    showToast("方案提交失败：" + error.message, "error");
    return false;
  }
}
async function createPlan() {
  const ids = $("planContentsStandalone").value.split(",")
    .map((value) => Number(value.trim()))
    .filter((value) => Number.isInteger(value) && value > 0);
  if (!ids.length) { showToast("请填写训练内容 ID", "error"); return; }
  const editingPlanId = state.editingPlanId;
  const payload = {
    userId: Number($("planUserId").value || 1),
    name: $("planNameStandalone").value.trim(),
    startDate: $("planStartStandalone").value,
    endDate: $("planEndStandalone").value,
    scheduleTime: $("planTimeStandalone").value + ":00",
    enabled: $("planEnabled").value === "true",
    contents: ids.map((contentId, index) => ({ contentId, sortOrder: index }))
  };
  if (!payload.name || !payload.startDate || !payload.endDate || !payload.scheduleTime) {
    showToast("计划名称、日期和执行时间不能为空", "error");
    return;
  }
  const path = editingPlanId
    ? "/api/training/plans/" + editingPlanId
    : "/api/training/plans/confirm";
  const method = editingPlanId ? "PUT" : "POST";
  if (editingPlanId) payload.reason = "管理端编辑训练计划";
  try {
    const result = await request(path, { method, body: JSON.stringify(payload) });
    const planId = result?.plan?.id || result?.id || editingPlanId || "";
    $("planResult").textContent = editingPlanId
      ? "计划 #" + planId + " 已更新，调度器已刷新。"
      : "计划 #" + planId + " 创建成功，调度器已刷新。";
    showToast(editingPlanId ? "训练计划已更新" : "计划创建成功", "success");
    resetPlanForm();
    await loadDashboard();
    await loadPlans();
  } catch (error) {
    $("planResult").textContent = "保存失败：" + error.message;
    showToast("计划保存失败：" + error.message, "error");
  }
}

function resetContentForm() {
  $("editingContentId").value = "";
  $("editingContentVersion").value = "";
  $("contentStatus").value = "ENABLED";
  $("contentFormTitle").textContent = "新增训练内容";
  $("createContentButton").textContent = "保存训练内容";
  $("cancelContentEditButton").hidden = true;
}

function beginContentEdit(content) {
  $("editingContentId").value = content.id || "";
  $("editingContentVersion").value = content.version || "";
  $("contentTitle").value = content.title || "";
  $("contentType").value = content.contentType || "";
  $("contentDifficulty").value = content.difficulty || 1;
  $("contentBody").value = content.contentBody || "";
  $("contentStatus").value = content.status || "ENABLED";
  $("contentFormTitle").textContent = "编辑训练内容 #" + content.id;
  $("createContentButton").textContent = "保存修改";
  $("cancelContentEditButton").hidden = false;
  document.querySelector('[data-view-panel="contents"]').scrollIntoView({ behavior: "smooth", block: "start" });
}

async function saveContent() {
  const contentId = Number($("editingContentId").value) || null;
  const payload = {
    title: $("contentTitle").value.trim(),
    contentType: $("contentType").value.trim(),
    difficulty: Number($("contentDifficulty").value),
    contentBody: $("contentBody").value.trim(),
    status: $("contentStatus").value
  };
  if (!payload.title || !payload.contentBody) {
    showToast("标题和训练说明不能为空", "error");
    return;
  }
  const path = contentId ? "/api/training/contents/" + contentId : "/api/training/contents";
  const method = contentId ? "PUT" : "POST";
  if (contentId) payload.expectedVersion = Number($("editingContentVersion").value) || null;
  try {
    const result = await request(path, { method, body: JSON.stringify(payload) });
    const id = result?.id || contentId || "";
    $("contentResult").textContent = "内容 #" + id + " 已保存，修改会自动触发知识索引重建。";
    $("indexContentId").value = id || $("indexContentId").value;
    showToast(contentId ? "训练内容已更新" : "训练内容已保存", "success");
    resetContentForm();
    await loadContents();
  } catch (error) {
    $("contentResult").textContent = "保存失败：" + error.message;
    showToast("内容保存失败：" + error.message, "error");
  }
}

async function deleteContent(contentId) {
  if (!window.confirm("确认停用训练内容 #" + contentId + " 吗？历史计划和版本记录会保留。")) return;
  try {
    await request("/api/training/contents/" + contentId, { method: "DELETE" });
    showToast("训练内容已逻辑删除", "success");
    if (Number($("editingContentId").value) === Number(contentId)) resetContentForm();
    await loadContents();
  } catch (error) {
    showToast("内容停用失败：" + error.message, "error");
  }
}

async function showContentVersions(contentId) {
  try {
    const versions = await request("/api/training/contents/" + contentId + "/versions");
    if (!versions?.length) {
      window.alert("内容 #" + contentId + " 暂无快照；首次编辑、导入或停用后会产生版本记录");
      return;
    }
    const ordered = [...versions].sort((left, right) => Number(left.version || 0) - Number(right.version || 0));
    let message = ordered.map((item) =>
      "v" + (item.version ?? "-") + " · " + (item.status || "-") + " · " +
      (item.sourceType || "MANUAL") + " · " + (item.createdAt || "-")
    ).join("\n");
    if (ordered.length >= 2) {
      const fromVersion = ordered[0].version;
      const toVersion = ordered[ordered.length - 1].version;
      const diff = await request(
        "/api/training/contents/" + contentId + "/versions/diff?fromVersion=" +
        encodeURIComponent(fromVersion) + "&toVersion=" + encodeURIComponent(toVersion)
      );
      message += "\n\n最近版本差异（v" + fromVersion + " → v" + toVersion + "）：\n" +
        ((diff?.changedFields || []).join("、") || "无字段变化");
    }
    window.alert("内容 #" + contentId + " 版本历史\n\n" + message);
  } catch (error) {
    showToast("版本查询失败：" + error.message, "error");
  }
}

async function searchKnowledge() {
  const query = $("knowledgeQuery").value.trim();
  if (!query) return;
  const box = $("knowledgeResults"); box.innerHTML = `<div class="empty">正在检索...</div>`;
  try { const results = await request(`/api/knowledge/search?query=${encodeURIComponent(query)}`); box.replaceChildren(); if (!results?.length) { box.innerHTML = `<div class="empty">未检索到相关知识</div>`; return; } results.slice(0, 6).forEach((item) => { const row = document.createElement("div"); row.className = "knowledge-item"; row.innerHTML = `<strong>相关度 ${item.score == null ? "-" : Number(item.score).toFixed(3)}</strong><p>${item.text || ""}</p>`; box.appendChild(row); }); }
  catch (error) { box.innerHTML = `<div class="empty">检索失败：${error.message}</div>`; }
}

function renderIndexTask(task) {
  const box = $("indexTaskResult");
  if (!box || !task) return;
  const status = String(task.status || "UNKNOWN").toUpperCase();
  const progress = (task.indexedCount ?? 0) + "/" + (task.requestedCount ?? 0);
  const detail = task.error ? " · " + task.error : "";
  box.textContent = "索引任务 " + (task.taskId || "-") + "：" + status + "（" + progress + "）" + detail;
}

async function watchIndexTask(taskId) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    await new Promise((resolve) => window.setTimeout(resolve, 1500));
    try {
      const task = await request("/api/knowledge/index-tasks/" + encodeURIComponent(taskId));
      renderIndexTask(task);
      const status = String(task.status || "").toUpperCase();
      if (status === "SUCCEEDED" || status === "FAILED") {
        showToast(status === "SUCCEEDED" ? "知识索引任务已完成" : "知识索引任务失败", status === "SUCCEEDED" ? "success" : "error");
        return;
      }
    } catch (error) {
      $("indexTaskResult").textContent = "索引任务查询失败：" + error.message;
      return;
    }
  }
  $("indexTaskResult").textContent += " · 仍在后台执行，可稍后重新查询";
}

async function indexContent() {
  const id = Number($("indexContentId").value);
  if (!id) {
    showToast("请填写训练内容 ID", "error");
    return;
  }
  try {
    const task = await request("/api/knowledge/training-contents/index/async", {
      method: "POST",
      body: JSON.stringify({ contentIds: [id] })
    });
    renderIndexTask(task);
    showToast("索引任务已提交", "success");
    if (task?.taskId) watchIndexTask(task.taskId);
  } catch (error) {
    $("indexTaskResult").textContent = "索引任务提交失败：" + error.message;
    showToast("索引任务提交失败：" + error.message, "error");
  }
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[char]));
}

function renderPlans(plans) {
  state.plans = plans || [];
  const table = $("planTable");
  const empty = $("planEmpty");
  const body = table.querySelector("tbody");
  body.replaceChildren();
  if (!state.plans.length) {
    table.hidden = true;
    empty.hidden = false;
    empty.textContent = "当前用户暂无训练计划";
    return;
  }
  empty.hidden = true;
  table.hidden = false;
  state.plans.forEach((plan) => {
    const row = document.createElement("tr");
    const enabled = plan.enabled === true;
    const actions = '<button class="table-action" data-edit-plan="' + plan.id + '">编辑</button> ' +
      (enabled ? '<button class="table-action" data-revoke-plan="' + plan.id + '">撤销</button>' : '<span class="muted">已撤销</span>');
    row.innerHTML = '<td><strong>' + escapeHtml(plan.name) + '</strong><small>计划 #' + plan.id + '</small></td>' +
      '<td>' + escapeHtml(plan.startDate) + ' 至 ' + escapeHtml(plan.endDate) + '</td>' +
      '<td>' + escapeHtml(plan.scheduleTime) + '</td>' +
      '<td>' + statusTag(enabled ? "ENABLED" : "DISABLED") + '</td><td>' + actions + '</td>';
    body.appendChild(row);
  });
  body.querySelectorAll("[data-edit-plan]").forEach((button) =>
    button.addEventListener("click", () => beginPlanEdit(Number(button.dataset.editPlan))));
  body.querySelectorAll("[data-revoke-plan]").forEach((button) =>
    button.addEventListener("click", () => revokePlan(button.dataset.revokePlan)));
}

async function beginPlanEdit(planId) {
  const plan = state.plans.find((item) => Number(item.id) === Number(planId));
  if (!plan) return;
  try {
    const contentIds = await request("/api/training/plans/" + planId + "/contents?userId=" +
      encodeURIComponent($("userId").value || "1"));
    state.editingPlanId = Number(planId);
    $("planUserId").value = plan.userId || $("userId").value || "1";
    $("planNameStandalone").value = plan.name || "";
    $("planStartStandalone").value = plan.startDate || "";
    $("planEndStandalone").value = plan.endDate || "";
    $("planTimeStandalone").value = String(plan.scheduleTime || "09:00").slice(0, 5);
    $("planContentsStandalone").value = (contentIds || []).join(",");
    $("planFormTitle").textContent = "编辑训练计划 #" + planId;
    $("createPlanButton").textContent = "保存计划修改";
    $("cancelPlanEditButton").hidden = false;
    $("planResult").textContent = "已加载计划内容，修改后保存即可刷新调度。";
    document.querySelector('[data-view-panel="plans"]').scrollIntoView({ behavior: "smooth", block: "start" });
  } catch (error) {
    showToast("计划内容加载失败：" + error.message, "error");
  }
}

function resetPlanForm() {
  state.editingPlanId = null;
  $("planFormTitle").textContent = "创建训练计划";
  $("createPlanButton").textContent = "创建并刷新调度任务";
  $("cancelPlanEditButton").hidden = true;
  $("planEnabled").value = "true";
  $("planResult").textContent = "";
}

async function loadPlans() {
  try { renderPlans(await request(`/api/training/plans?userId=${encodeURIComponent($("userId").value || "1")}`)); }
  catch (error) { $("planEmpty").hidden = false; $("planEmpty").textContent = `计划加载失败：${error.message}`; }
}

async function revokePlan(planId) {
  if (!window.confirm(`确认撤销训练计划 #${planId} 吗？已有历史执行记录不会删除。`)) return;
  try { await request(`/api/training/plans/${planId}/revoke`, { method: "POST", body: JSON.stringify({ userId: Number($("userId").value || 1), reason: "用户在平台操作台撤销" }) }); showToast("计划已撤销，调度器已刷新", "success"); await loadPlans(); await loadDashboard(); }
  catch (error) { showToast(`撤销失败：${error.message}`, "error"); }
}

function renderContents(contents) {
  state.contents = contents || [];
  const table = $("contentTable");
  const empty = $("contentEmpty");
  const body = table.querySelector("tbody");
  body.replaceChildren();
  if (!state.contents.length) {
    table.hidden = true;
    empty.hidden = false;
    empty.textContent = "暂无训练内容";
    return;
  }
  empty.hidden = true;
  table.hidden = false;
  state.contents.forEach((content) => {
    const row = document.createElement("tr");
    row.innerHTML =
      "<td><strong>" + escapeHtml(content.title) + "</strong><small>内容 #" + content.id + "</small></td>" +
      "<td>" + escapeHtml(content.contentType || "DOCUMENT") + "</td>" +
      "<td>" + (content.difficulty ?? "-") + "</td>" +
      "<td>v" + (content.version ?? "-") + "</td>" +
      "<td>" + statusTag(content.status) + "</td>" +
      "<td><button class=\"table-action\" data-edit-content=\"" + content.id + "\">编辑</button> " +
      "<button class=\"table-action\" data-content-versions=\"" + content.id + "\">版本</button> " +
      (String(content.status).toUpperCase() === "ENABLED"
        ? "<button class=\"table-action\" data-delete-content=\"" + content.id + "\">停用</button>"
        : "<span class=\"muted\">已停用</span>") + "</td>";
    body.appendChild(row);
  });
  body.querySelectorAll("[data-edit-content]").forEach((button) => {
    button.addEventListener("click", () => {
      const content = state.contents.find((item) => String(item.id) === button.dataset.editContent);
      if (content) beginContentEdit(content);
    });
  });
  body.querySelectorAll("[data-delete-content]").forEach((button) => {
    button.addEventListener("click", () => deleteContent(Number(button.dataset.deleteContent)));
  });
  body.querySelectorAll("[data-content-versions]").forEach((button) => {
    button.addEventListener("click", () => showContentVersions(Number(button.dataset.contentVersions)));
  });
}

async function loadContents() {
  try { renderContents(await request("/api/training/contents")); }
  catch (error) { $("contentEmpty").hidden = false; $("contentEmpty").textContent = `内容加载失败：${error.message}`; }
}

function renderExecutions(executions) {
  const table = $("executionTable");
  const empty = $("executionEmpty");
  const body = table.querySelector("tbody");
  body.replaceChildren();
  if (!executions?.length) { table.hidden = true; empty.hidden = false; empty.textContent = "当前周期暂无已完成执行记录"; return; }
  empty.hidden = true; table.hidden = false;
  executions.forEach((execution) => {
    const row = document.createElement("tr");
    row.innerHTML = `<td><strong>#${execution.id}</strong><small>第 ${execution.attemptNo ?? 1} 次</small></td><td>任务 #${execution.taskId ?? "-"} / 内容 #${execution.contentId ?? "-"}</td><td>${statusTag(execution.status)}</td><td>${execution.score ?? "-"}</td><td>${execution.durationSeconds == null ? "-" : `${execution.durationSeconds}s`}</td>`;
    body.appendChild(row);
  });
}

async function loadExecutions() {
  const dateValue = $("trainingDate").value;
  const from = new Date(`${dateValue}T00:00:00`); from.setDate(from.getDate() - 30);
  try { renderExecutions(await request(`/api/training/executions?userId=${encodeURIComponent($("userId").value || "1")}&from=${from.toISOString().slice(0, 10)}&to=${encodeURIComponent(dateValue)}`)); }
  catch (error) { $("executionEmpty").hidden = false; $("executionEmpty").textContent = `执行记录加载失败：${error.message}`; }
}

async function finishExecution() {
  const executionId = Number($("finishExecutionId").value);
  if (!executionId) { showToast("请先开始一条训练任务，或填写执行记录 ID", "error"); return; }
  const success = $("finishSuccess").value === "true";
  const payload = { success, score: $("finishScore").value ? Number($("finishScore").value) : null, durationSeconds: $("finishDuration").value ? Number($("finishDuration").value) : null, resultData: $("finishResultData").value.trim(), errorMessage: success ? null : $("finishResultData").value.trim() };
  try { const result = await request(`/api/training/executions/${executionId}/finish`, { method: "POST", body: JSON.stringify(payload) }); $("finishResult").textContent = `执行记录 #${result?.id || executionId} 已完成，任务状态已更新。`; showToast("训练结果已保存", "success"); await loadDashboard(); await loadExecutions(); }
  catch (error) { $("finishResult").textContent = `提交失败：${error.message}`; showToast(`提交结果失败：${error.message}`, "error"); }
}
async function importContent() {
  const file = $("contentFile").files[0];
  if (!file) { showToast("请先选择一个文档文件", "error"); return; }
  const form = new FormData(); form.append("file", file); form.append("difficulty", $("contentDifficulty").value || "2"); form.append("index", "true");
  try {
    const response = await fetch("/api/training/contents/import", { method: "POST", headers: { Accept: "application/json" }, body: form });
    const payload = await response.json(); if (!response.ok) throw new Error(payload?.info || `HTTP ${response.status}`);
    const result = unwrap(payload); $("contentResult").textContent = `文档已解析，内容 #${result?.contentId || ""} 已保存。`; $("indexContentId").value = result?.contentId || $("indexContentId").value; showToast("文档导入成功", "success"); await loadContents();
  } catch (error) { showToast(`文档导入失败：${error.message}`, "error"); }
}
$("refreshButton").addEventListener("click", loadDashboard);
$("healthButton").addEventListener("click", loadDashboard);
$("agentButton").addEventListener("click", runAgent);
$("agentCancelButton").addEventListener("click", () => state.agentController?.abort());
$("confirmPlanButton").addEventListener("click", confirmPlan);
$("createPlanButton").addEventListener("click", createPlan); $("cancelPlanEditButton").addEventListener("click", resetPlanForm);
$("createContentButton").addEventListener("click", saveContent);
$("cancelContentEditButton").addEventListener("click", resetContentForm);
$("knowledgeSearchButton").addEventListener("click", searchKnowledge);
$("indexContentButton").addEventListener("click", indexContent);
$("importContentButton").addEventListener("click", importContent);
$("refreshPlansButton").addEventListener("click", loadPlans);
$("refreshContentsButton").addEventListener("click", loadContents);
$("refreshExecutionsButton").addEventListener("click", loadExecutions);
$("finishExecutionButton").addEventListener("click", finishExecution); $("saveAdjustmentButton").addEventListener("click", saveTaskAdjustment); $("cancelAdjustmentButton").addEventListener("click", closeTaskAdjustment);
$("agentMessage").addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    runAgent();
  }
});
if ($("trainingDate").value === "2026-09-29" || !$("trainingDate").value) $("trainingDate").value = businessToday();
document.querySelectorAll(".nav-item").forEach((button) => button.addEventListener("click", () => switchView(button.dataset.view)));
document.querySelectorAll("[data-go]").forEach((button) => button.addEventListener("click", () => switchView(button.dataset.go)));
document.querySelectorAll("[data-prompt]").forEach((button) => button.addEventListener("click", () => { $("agentMessage").value = button.dataset.prompt; switchView("agent"); $("agentMessage").focus(); }));
document.querySelectorAll("[data-agent-mode]").forEach((button) => button.addEventListener("click", () => { state.agentMode = button.dataset.agentMode; document.querySelectorAll("[data-agent-mode]").forEach((item) => item.classList.toggle("active", item === button)); $("agentSessionTitle").textContent = `${state.agentMode === "auto" ? "Auto Agent" : "Flow Agent"} 对话`; }));
loadDashboard();
resetPlanForm();
resetContentForm();
