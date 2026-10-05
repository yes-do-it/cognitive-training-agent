const DEMO_USER_ID = 1;
const $ = (id) => document.getElementById(id);
const state = {
  tasks: [],
  selectedTask: null,
  selectedContent: null,
  executionId: null,
  assistantController: null,
  exercise: null
};

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
  if (!response.ok) throw new Error(payload?.info || payload?.message || ("HTTP " + response.status));
  return unwrap(payload);
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[char]));
}

function formatStatus(status) {
  const normalized = String(status || "-").toUpperCase();
  return '<span class="tag ' + normalized.toLowerCase() + '">' + normalized + '</span>';
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

function initializeDate() {
  $("trainingDate").value = businessToday();
}

function setConnection(status, message) {
  $("connectionDot").className = "dot " + (status === "UP" ? "" : status === "ERROR" ? "error" : "pending");
  $("connectionText").textContent = message;
}

async function loadHealth() {
  try {
    const health = await request("/api/health/overview");
    setConnection(health.status === "UP" ? "UP" : "ERROR", health.status === "UP" ? "训练服务正常" : "训练服务异常");
  } catch (_) {
    setConnection("ERROR", "无法连接训练服务");
  }
}

async function loadSummary() {
  const date = $("trainingDate").value;
  const from = new Date(date + "T00:00:00");
  from.setDate(from.getDate() - 29);
  try {
    const summary = await request("/api/training/executions/summary?userId=" + DEMO_USER_ID + "&from=" + from.toISOString().slice(0, 10) + "&to=" + encodeURIComponent(date));
    $("successRate").textContent = Math.round(Number(summary?.successRate || 0) * 100) + "%";
  } catch (_) {
    $("successRate").textContent = "-";
  }
}

function formatTrendTime(value) {
  if (!value) return "完成时间未知";
  const date = new Date(String(value).replace(" ", "T"));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
}

async function loadTrend() {
  const date = $("trainingDate").value;
  const from = new Date(date + "T00:00:00");
  from.setDate(from.getDate() - 29);
  try {
    const executions = await request("/api/training/executions?userId=" + DEMO_USER_ID + "&from=" + from.toISOString().slice(0, 10) + "&to=" + encodeURIComponent(date));
    const records = Array.isArray(executions) ? executions : [];
    const scored = records.filter((item) => item.score != null).map((item) => Number(item.score));
    const durations = records.filter((item) => item.durationSeconds != null).map((item) => Number(item.durationSeconds));
    const successCount = records.filter((item) => String(item.status).toUpperCase() === "SUCCESS").length;
    $("trendTotal").textContent = records.length;
    $("trendAverageScore").textContent = scored.length ? Math.round(scored.reduce((sum, value) => sum + value, 0) / scored.length) : "-";
    $("trendAverageDuration").textContent = durations.length ? Math.round(durations.reduce((sum, value) => sum + value, 0) / durations.length) + " 秒" : "-";
    $("trendSuccessCount").textContent = successCount;
    const list = $("trendList");
    if (!records.length) {
      list.innerHTML = '<div class="empty">近 30 天还没有完成训练，完成一次后这里会显示你的表现。</div>';
      return;
    }
    const contentIds = [...new Set(records.map((item) => item.contentId).filter(Boolean))];
    const contentPairs = await Promise.all(contentIds.map(async (id) => [id, await request("/api/training/contents/" + id).catch(() => null)]));
    const titles = new Map(contentPairs.filter((pair) => pair[1]).map((pair) => [pair[0], pair[1].title]));
    list.innerHTML = records.slice(0, 8).map((item) => {
      const title = titles.get(item.contentId) || ("训练内容 #" + (item.contentId || "-"));
      const status = String(item.status || "-").toUpperCase();
      const score = item.score == null ? "未评分" : item.score + " 分";
      const duration = item.durationSeconds == null ? "耗时未知" : item.durationSeconds + " 秒";
      return '<div class="trend-item"><div><strong>' + escapeHtml(title) + '</strong><small>' + formatTrendTime(item.finishedAt) + ' · 执行 #' + item.id + '</small></div><div class="trend-result"><strong>' + escapeHtml(score) + '</strong><small>' + escapeHtml(duration) + ' · ' + escapeHtml(status) + '</small></div></div>';
    }).join("");
  } catch (error) {
    $("trendTotal").textContent = "-";
    $("trendAverageScore").textContent = "-";
    $("trendAverageDuration").textContent = "-";
    $("trendSuccessCount").textContent = "-";
    $("trendList").innerHTML = '<div class="empty">训练表现加载失败：' + escapeHtml(error.message) + '</div>';
  }
}
async function loadTasks() {
  const date = $("trainingDate").value;
  $("dateHint").textContent = "演示用户 1 · " + date;
  $("taskList").innerHTML = '<div class="empty">正在加载今日训练...</div>';
  try {
    const tasks = await request("/api/training/tasks?userId=" + DEMO_USER_ID + "&trainingDate=" + encodeURIComponent(date));
    state.tasks = tasks || [];
    const contents = await Promise.all(state.tasks.map((task) => request("/api/training/contents/" + task.contentId).catch(() => null)));
    state.tasks = state.tasks.map((task, index) => ({ ...task, content: contents[index] }));
    renderTasks();
    const completed = state.tasks.filter((task) => task.status === "COMPLETED").length;
    $("taskCount").textContent = state.tasks.length;
    $("completedCount").textContent = completed;
    $("progressText").textContent = state.tasks.length ? Math.round(completed / state.tasks.length * 100) + "% 今日完成" : "暂无训练安排";
  } catch (error) {
    $("taskList").innerHTML = '<div class="empty">训练任务加载失败：' + escapeHtml(error.message) + '</div>';
    $("taskCount").textContent = "-";
    $("completedCount").textContent = "-";
  }
}

function renderTasks() {
  const box = $("taskList");
  box.replaceChildren();
  if (!state.tasks.length) {
    box.innerHTML = '<div class="empty">今天暂时没有训练安排</div>';
    return;
  }
  state.tasks.forEach((task) => {
    const content = task.content || {};
    const action = task.status === "COMPLETED"
      ? '<button class="ghost" data-view-task="' + task.id + '">查看内容</button>'
      : task.status === "PENDING"
        ? '<button class="primary" data-view-task="' + task.id + '">开始训练</button>'
        : task.status === "IN_PROGRESS"
          ? '<button class="primary" data-view-task="' + task.id + '">继续训练</button>'
          : '<button class="ghost" data-view-task="' + task.id + '">查看</button>';
    const row = document.createElement("div");
    row.className = "task-card";
    row.innerHTML = '<div class="task-info"><strong>' +
      escapeHtml(content.title || ("训练内容 #" + task.contentId)) +
      '</strong><small>' + escapeHtml(content.contentType || "认知训练") +
      ' · ' + formatStatus(task.status) + '</small></div><div class="task-action">' + action + '</div>';
    box.appendChild(row);
  });
  box.querySelectorAll("[data-view-task]").forEach((button) => {
    button.addEventListener("click", () => viewTask(Number(button.dataset.viewTask)));
  });
}

function isMemoryContent(content) {
  const value = [
    content?.contentType,
    content?.title,
    content?.contentBody
  ].join(" ").toUpperCase();
  return value.includes("MEMORY") || value.includes("记忆") || value.includes("数字广度") || value.includes("工作记忆");
}

function isReactionContent(content) {
  const value = [
    content?.contentType,
    content?.title,
    content?.contentBody
  ].join(" ").toUpperCase();
  return value.includes("ATTENTION") || value.includes("REACTION") || value.includes("注意力") || value.includes("反应速度") || value.includes("反应训练");
}

function resetExercise() {
  if (state.exercise?.timer) window.clearTimeout(state.exercise.timer);
  state.exercise = null;
  if ($("exerciseArea")) $("exerciseArea").hidden = true;
  if ($("sequenceDisplay")) $("sequenceDisplay").textContent = "准备开始";
  if ($("sequenceHint")) $("sequenceHint").textContent = "数字出现后请按顺序记忆。";
  if ($("exerciseFeedback")) $("exerciseFeedback").textContent = "";
  if ($("answerInput")) {
    $("answerInput").value = "";
    $("answerInput").hidden = false;
    $("answerInput").disabled = false;
  }
  if ($("submitAnswerButton")) {
    $("submitAnswerButton").hidden = false;
    $("submitAnswerButton").disabled = false;
  }
  if ($("reactionButton")) {
    $("reactionButton").hidden = true;
    $("reactionButton").disabled = false;
    $("reactionButton").textContent = "等待信号...";
  }
}

function memoryLength(difficulty) {
  const level = Math.max(1, Math.min(5, Number(difficulty) || 2));
  return Math.min(9, 3 + level);
}

function createSequence(length) {
  let sequence = "";
  while (sequence.length < length) {
    const digit = String(Math.floor(Math.random() * 10));
    if (!sequence || sequence[sequence.length - 1] !== digit) sequence += digit;
  }
  return sequence;
}

function beginMemoryExercise() {
  resetExercise();
  state.exercise = {
    kind: "memory",
    round: 0,
    rounds: 3,
    correct: 0,
    sequence: "",
    showing: false,
    startedAt: Date.now(),
    timer: null
  };
  $("exerciseArea").hidden = false;
  $("finishArea").hidden = true;
  $("finishScore").readOnly = true;
  $("finishDuration").readOnly = true;
  renderMemoryRound();
}

function beginReactionExercise() {
  resetExercise();
  state.exercise = {
    kind: "reaction",
    round: 0,
    rounds: 5,
    reactionTimes: [],
    signalAt: null,
    startedAt: Date.now(),
    timer: null
  };
  $("exerciseArea").hidden = false;
  $("finishArea").hidden = true;
  $("finishScore").readOnly = true;
  $("finishDuration").readOnly = true;
  $("answerInput").hidden = true;
  $("submitAnswerButton").hidden = true;
  $("reactionButton").hidden = false;
  renderReactionRound();
}

function renderMemoryRound() {
  const exercise = state.exercise;
  if (!exercise) return;
  if (exercise.round >= exercise.rounds) {
    completeMemoryExercise();
    return;
  }

  const length = memoryLength(state.selectedContent?.difficulty) + exercise.round;
  exercise.sequence = createSequence(length);
  exercise.showing = true;
  $("exerciseProgress").textContent = "第 " + (exercise.round + 1) + " / " + exercise.rounds + " 轮";
  $("exerciseTimerHint").textContent = "请集中注意力";
  $("sequenceDisplay").textContent = exercise.sequence;
  $("sequenceHint").textContent = "请记住数字顺序，数字即将隐藏。";
  $("answerInput").value = "";
  $("answerInput").disabled = true;
  $("submitAnswerButton").disabled = true;
  $("exerciseFeedback").textContent = "";

  const visibleMs = Math.max(2200, exercise.sequence.length * 520);
  exercise.timer = window.setTimeout(() => {
    if (!state.exercise) return;
    exercise.showing = false;
    $("sequenceDisplay").textContent = "● ● ● ●";
    $("sequenceHint").textContent = "数字已隐藏，请按刚才的顺序输入。";
    $("exerciseTimerHint").textContent = "请输入答案";
    $("answerInput").disabled = false;
    $("submitAnswerButton").disabled = false;
    $("answerInput").focus();
  }, visibleMs);
}

function renderReactionRound() {
  const exercise = state.exercise;
  if (!exercise || exercise.kind !== "reaction") return;
  if (exercise.round >= exercise.rounds) {
    completeReactionExercise();
    return;
  }
  exercise.signalAt = null;
  $("exerciseProgress").textContent = "第 " + (exercise.round + 1) + " / " + exercise.rounds + " 轮";
  $("exerciseTimerHint").textContent = "等待信号";
  $("sequenceDisplay").textContent = "准备";
  $("sequenceHint").textContent = "看到绿色信号后，立即点击按钮。不要提前点击。";
  $("reactionButton").disabled = true;
  $("reactionButton").textContent = "等待信号...";
  $("exerciseFeedback").textContent = "";
  $("sequenceDisplay").classList.remove("reaction-go");
  const waitMs = 900 + Math.floor(Math.random() * 1800);
  exercise.timer = window.setTimeout(() => {
    if (!state.exercise || state.exercise.kind !== "reaction") return;
    exercise.signalAt = performance.now();
    $("sequenceDisplay").textContent = "GO";
    $("sequenceDisplay").classList.add("reaction-go");
    $("sequenceHint").textContent = "现在点击！";
    $("exerciseTimerHint").textContent = "立即响应";
    $("reactionButton").disabled = false;
    $("reactionButton").textContent = "立即点击";
    exercise.timer = null;
  }, waitMs);
}

function submitReactionAnswer() {
  const exercise = state.exercise;
  if (!exercise || exercise.kind !== "reaction" || exercise.signalAt == null) return;
  const reactionTime = Math.max(1, Math.round(performance.now() - exercise.signalAt));
  exercise.reactionTimes.push(reactionTime);
  exercise.round += 1;
  exercise.signalAt = null;
  $("reactionButton").disabled = true;
  $("reactionButton").textContent = reactionTime + " ms";
  $("sequenceDisplay").classList.remove("reaction-go");
  $("exerciseFeedback").textContent = "本轮反应时间：" + reactionTime + " 毫秒。";
  exercise.timer = window.setTimeout(renderReactionRound, 900);
}

function completeReactionExercise() {
  const exercise = state.exercise;
  if (!exercise || exercise.kind !== "reaction") return;
  const total = exercise.reactionTimes.reduce((sum, value) => sum + value, 0);
  const average = Math.round(total / exercise.reactionTimes.length);
  const score = Math.max(0, Math.min(100, Math.round(100 - Math.max(0, average - 250) / 7)));
  const duration = Math.max(1, Math.round((Date.now() - exercise.startedAt) / 1000));
  $("exerciseProgress").textContent = "训练完成";
  $("exerciseTimerHint").textContent = "得分 " + score;
  $("sequenceDisplay").classList.remove("reaction-go");
  $("sequenceDisplay").textContent = average + " ms";
  $("sequenceHint").textContent = "5 轮平均反应时间。分数越高，表示反应越快。";
  $("exerciseFeedback").textContent = "请确认结果后提交，系统会记录本次训练。";
  $("reactionButton").disabled = true;
  $("reactionButton").textContent = "训练完成";
  $("finishScore").value = score;
  $("finishDuration").value = duration;
  $("finishSuccess").value = score >= 60 ? "true" : "false";
  $("finishResultData").value = "注意力反应速度训练：共 " + exercise.rounds + " 轮，平均反应时间 " + average + " 毫秒。";
  $("finishArea").hidden = false;
  $("finishArea").scrollIntoView({ behavior: "smooth", block: "center" });
}
function submitMemoryAnswer() {
  const exercise = state.exercise;
  if (!exercise || exercise.showing) return;
  const answer = $("answerInput").value.replace(/\s/g, "");
  if (!/^\d+$/.test(answer)) {
    $("exerciseFeedback").textContent = "请输入数字答案。";
    return;
  }
  const correct = answer === exercise.sequence;
  if (correct) exercise.correct += 1;
  $("exerciseFeedback").textContent = correct
    ? "回答正确，准备下一轮。"
    : "本轮未匹配，正确答案是 " + exercise.sequence + "。";
  $("answerInput").disabled = true;
  $("submitAnswerButton").disabled = true;
  exercise.round += 1;
  exercise.timer = window.setTimeout(renderMemoryRound, 900);
}

function completeMemoryExercise() {
  const exercise = state.exercise;
  if (!exercise) return;
  const score = Math.round(exercise.correct / exercise.rounds * 100);
  const duration = Math.max(1, Math.round((Date.now() - exercise.startedAt) / 1000));
  $("exerciseProgress").textContent = "训练完成";
  $("exerciseTimerHint").textContent = "得分 " + score;
  $("sequenceDisplay").textContent = score >= 60 ? "完成得不错" : "可以再试一次";
  $("sequenceHint").textContent = "你完成了 " + exercise.rounds + " 轮，正确 " + exercise.correct + " 轮。";
  $("exerciseFeedback").textContent = "请确认结果后提交，系统会记录本次训练。";
  $("finishScore").value = score;
  $("finishDuration").value = duration;
  $("finishSuccess").value = score >= 60 ? "true" : "false";
  $("finishResultData").value = "工作记忆数字广度训练：共 " + exercise.rounds + " 轮，答对 " + exercise.correct + " 轮。";
  $("finishArea").hidden = false;
  $("finishArea").scrollIntoView({ behavior: "smooth", block: "center" });
}

async function viewTask(taskId) {
  const task = state.tasks.find((item) => Number(item.id) === taskId);
  if (!task) return;
  resetExercise();
  state.selectedTask = task;
  state.selectedContent = task.content || await request("/api/training/contents/" + task.contentId);
  const content = state.selectedContent || {};
  $("trainingPanel").hidden = false;
  $("trainingTitle").textContent = content.title || "训练内容";
  $("trainingType").textContent = content.contentType || "认知训练";
  $("trainingDifficulty").textContent = "难度 " + (content.difficulty ?? "-");
  $("trainingBody").textContent = content.contentBody || "请按照训练指导完成本次训练。";
  if (isMemoryContent(content)) {
    $("trainingBody").textContent += "\n\n本次将进行 3 轮数字记忆训练。数字会逐轮增加，请在隐藏后按原顺序输入。";
  } else if (isReactionContent(content)) {
    $("trainingBody").textContent += "\n\n本次将进行 5 轮反应速度训练。请等待绿色信号出现后立即点击，不要提前点击。";
  }
  $("trainingStatus").innerHTML = formatStatus(task.status);
  $("startButton").textContent = isMemoryContent(content) ? "开始数字记忆训练 →" : isReactionContent(content) ? "开始反应速度训练 →" : "开始本次训练 →";
  $("startArea").hidden = task.status !== "PENDING";
  $("finishArea").hidden = true;
  $("trainingPanel").scrollIntoView({ behavior: "smooth", block: "start" });
}

async function startTraining() {
  if (!state.selectedTask) return;
  try {
    const execution = await request("/api/training/executions/tasks/" + state.selectedTask.id + "/start", {
      method: "POST",
      body: JSON.stringify({
        userId: DEMO_USER_ID,
        idempotencyKey: "user-" + state.selectedTask.id + "-" + Date.now()
      })
    });
    state.executionId = execution.id;
    $("trainingStatus").innerHTML = formatStatus("IN_PROGRESS");
    $("startArea").hidden = true;
    $("finishResult").textContent = "";
    if (isMemoryContent(state.selectedContent)) {
      beginMemoryExercise();
    } else if (isReactionContent(state.selectedContent)) {
      beginReactionExercise();
    } else {
      $("finishScore").readOnly = false;
      $("finishDuration").readOnly = false;
      $("finishArea").hidden = false;
      $("finishResult").textContent = "训练已开始，请完成训练后提交结果。";
    }
  } catch (error) {
    $("finishArea").hidden = false;
    $("finishResult").textContent = "开始训练失败：" + error.message;
  }
}

async function finishTraining() {
  if (!state.executionId) {
    $("finishResult").textContent = "请先点击开始本次训练";
    return;
  }
  const success = $("finishSuccess").value === "true";
  const score = $("finishScore").value === "" ? null : Number($("finishScore").value);
  const duration = $("finishDuration").value === "" ? null : Number($("finishDuration").value);
  if (score !== null && (score < 0 || score > 100)) {
    $("finishResult").textContent = "得分必须在 0 到 100 之间";
    return;
  }
  try {
    await request("/api/training/executions/" + state.executionId + "/finish", {
      method: "POST",
      body: JSON.stringify({
        success,
        score,
        durationSeconds: duration,
        resultData: $("finishResultData").value.trim(),
        errorMessage: success ? null : $("finishResultData").value.trim()
      })
    });
    $("trainingStatus").innerHTML = formatStatus(success ? "COMPLETED" : "PENDING");
    $("finishResult").textContent = success ? "本次训练已完成，结果已保存。" : "本次训练已记录为未完成，可以稍后重试。";
    $("finishArea").hidden = true;
    $("finishScore").readOnly = false;
    $("finishDuration").readOnly = false;
    resetExercise();
    state.executionId = null;
    await loadTasks();
    await loadSummary();
    await loadTrend();
  } catch (error) {
    $("finishResult").textContent = "提交训练结果失败：" + error.message;
  }
}

function processAssistantEvent(rawEvent) {
  const lines = rawEvent.replace(/\r/g, "").split("\n");
  let eventName = "message";
  const data = [];
  lines.forEach((line) => {
    if (line.startsWith("event:")) eventName = line.slice(6).trim();
    if (line.startsWith("data:")) data.push(line.slice(5).replace(/^ /, ""));
  });
  const value = data.join("\n");
  if (eventName === "token" || eventName === "message") $("assistantOutput").textContent += value;
  if (eventName === "error") $("assistantOutput").textContent += "\n\n处理失败：" + value;
}

async function askAssistant() {
  const message = $("assistantMessage").value.trim();
  if (!message) return;
  if (state.assistantController) state.assistantController.abort();
  state.assistantController = new AbortController();
  $("assistantOutput").textContent = "";
  $("assistantButton").disabled = true;
  try {
    const query = new URLSearchParams({
      userId: String(DEMO_USER_ID),
      trainingDate: $("trainingDate").value,
      message
    });
    const response = await fetch("/api/agent/flow/stream?" + query.toString(), {
      headers: { Accept: "text/event-stream", "Cache-Control": "no-cache" },
      signal: state.assistantController.signal
    });
    if (!response.ok || !response.body) throw new Error("HTTP " + response.status);
    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";
    while (true) {
      const result = await reader.read();
      if (result.done) break;
      buffer += decoder.decode(result.value, { stream: true });
      const events = buffer.replace(/\r\n/g, "\n").split("\n\n");
      buffer = events.pop() || "";
      events.forEach(processAssistantEvent);
    }
    if (buffer.trim()) processAssistantEvent(buffer);
  } catch (error) {
    if (error.name !== "AbortError") $("assistantOutput").textContent = "训练助手暂时无法回答：" + error.message;
  } finally {
    $("assistantButton").disabled = false;
    state.assistantController = null;
  }
}

$("refreshTrendButton").addEventListener("click", loadTrend);
$("refreshButton").addEventListener("click", () => { loadTasks(); loadSummary(); loadTrend(); loadHealth(); });
$("refreshTasksButton").addEventListener("click", () => { loadTasks(); loadSummary(); loadTrend(); });
$("trainingDate").addEventListener("change", () => { loadTasks(); loadSummary(); loadTrend(); });
$("startButton").addEventListener("click", startTraining);
$("submitAnswerButton").addEventListener("click", submitMemoryAnswer); $("reactionButton").addEventListener("click", submitReactionAnswer);
$("answerInput").addEventListener("keydown", (event) => { if (event.key === "Enter") submitMemoryAnswer(); });
$("finishButton").addEventListener("click", finishTraining);
$("assistantButton").addEventListener("click", askAssistant);
document.querySelectorAll("[data-prompt]").forEach((button) => button.addEventListener("click", () => {
  $("assistantMessage").value = button.dataset.prompt;
  $("assistantMessage").focus();
}));
initializeDate();
loadHealth();
loadTasks();
loadSummary();
loadTrend();