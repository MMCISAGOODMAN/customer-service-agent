const API_STREAM = '/api/v1/chat/stream';
const SESSION_KEY = 'customer_service_session_id';

const messageList = document.getElementById('messageList');
const chatForm = document.getElementById('chatForm');
const messageInput = document.getElementById('messageInput');
const sendBtn = document.getElementById('sendBtn');
const sessionIdDisplay = document.getElementById('sessionIdDisplay');
const modeBadge = document.getElementById('modeBadge');
const statusText = document.getElementById('statusText');
const newSessionBtn = document.getElementById('newSessionBtn');

let sessionId = sessionStorage.getItem(SESSION_KEY);
let isSending = false;
let abortController = null;

function init() {
  updateSessionDisplay();
  bindEvents();
  autoResizeTextarea();
}

function bindEvents() {
  chatForm.addEventListener('submit', onSubmit);
  messageInput.addEventListener('keydown', onInputKeydown);
  messageInput.addEventListener('input', autoResizeTextarea);
  newSessionBtn.addEventListener('click', startNewSession);

  document.querySelectorAll('.quick-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const msg = btn.dataset.msg;
      if (msg && !isSending) {
        messageInput.value = msg;
        chatForm.requestSubmit();
      }
    });
  });
}

function onInputKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    chatForm.requestSubmit();
  }
}

function autoResizeTextarea() {
  messageInput.style.height = 'auto';
  messageInput.style.height = Math.min(messageInput.scrollHeight, 120) + 'px';
}

async function onSubmit(e) {
  e.preventDefault();
  const text = messageInput.value.trim();
  if (!text || isSending) return;

  appendUserMessage(text);
  messageInput.value = '';
  autoResizeTextarea();

  const streamEl = createStreamingMessage();
  setSending(true);

  abortController = new AbortController();

  try {
    const response = await fetch(API_STREAM, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ message: text, sessionId: sessionId || undefined }),
      signal: abortController.signal,
    });

    if (!response.ok) {
      const errBody = await response.json().catch(() => ({}));
      throw new Error(errBody.message || `请求失败 (${response.status})`);
    }

    await consumeSSE(response, streamEl);
  } catch (err) {
    if (err.name === 'AbortError') return;
    finalizeStream(streamEl, `抱歉，服务暂时不可用：${err.message}`, null, true);
    updateModeBadge({ mode: 'error', fallback: true });
    statusText.textContent = '连接异常，请稍后重试';
  } finally {
    setSending(false);
    abortController = null;
    messageInput.focus();
  }
}

async function consumeSSE(response, streamEl) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let accumulated = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split('\n\n');
    buffer = blocks.pop() || '';

    for (const block of blocks) {
      const event = parseSSEBlock(block);
      if (!event) continue;

      if (event.name === 'token') {
        const data = safeJson(event.data);
        if (data?.text) {
          accumulated += data.text;
          updateStreamContent(streamEl, accumulated);
        }
      } else if (event.name === 'status') {
        const data = safeJson(event.data);
        if (data?.message) {
          updateStreamStatus(streamEl, data.message, data.phase);
        }
      } else if (event.name === 'react') {
        const data = safeJson(event.data);
        if (data) {
          appendReactStep(streamEl, data);
        }
      } else if (event.name === 'done') {
        const data = safeJson(event.data);
        if (data?.sessionId) {
          sessionId = data.sessionId;
          sessionStorage.setItem(SESSION_KEY, sessionId);
          updateSessionDisplay();
        }
        updateModeBadge(data);
        finalizeStream(streamEl, accumulated || data?.reply || '', buildMeta(data));
      }
    }
  }
}

function parseSSEBlock(block) {
  const lines = block.split('\n');
  let name = 'message';
  let data = '';
  for (const line of lines) {
    if (line.startsWith('event:')) name = line.slice(6).trim();
    else if (line.startsWith('data:')) data += line.slice(5).trim();
  }
  return data ? { name, data } : null;
}

function safeJson(str) {
  try { return JSON.parse(str); } catch { return null; }
}

function createStreamingMessage() {
  const el = document.createElement('div');
  el.className = 'message message-bot';
  el.innerHTML = `
    <div class="avatar bot">
      <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
      </svg>
    </div>
    <div class="bubble streaming">
      <div class="react-panel">
        <div class="react-panel-title">ReAct 过程</div>
        <div class="react-steps"></div>
      </div>
      <div class="stream-status">
        <span class="status-spinner"></span>
        <span class="status-text">小智正在思考…</span>
      </div>
      <div class="stream-content"><span class="stream-cursor"></span></div>
    </div>
  `;
  messageList.appendChild(el);
  scrollToBottom();
  return el;
}

function updateStreamStatus(el, message, phase) {
  const statusEl = el.querySelector('.stream-status');
  if (!statusEl) return;
  statusEl.querySelector('.status-text').textContent = message;
  if (phase === 'fallback') statusEl.classList.add('fallback');
}

function updateStreamContent(el, text) {
  const contentEl = el.querySelector('.stream-content');
  if (!contentEl) return;
  contentEl.innerHTML = formatReply(text) + '<span class="stream-cursor"></span>';
  scrollToBottom();
}

function appendReactStep(el, data) {
  const stepsEl = el.querySelector('.react-steps');
  const panelEl = el.querySelector('.react-panel');
  if (!stepsEl) return;
  if (panelEl) panelEl.classList.add('has-steps');

  const phase = data.phase || 'unknown';
  const step = data.step != null ? data.step : '';
  let label = '';
  let detail = '';

  if (phase === 'thought') {
    label = `Thought #${step}`;
    detail = data.plannedActions
      ? `${data.message || ''} → ${data.plannedActions}`
      : (data.message || '');
  } else if (phase === 'action') {
    label = `Action #${step}`;
    detail = `${data.tool}(${data.args || ''})`;
  } else if (phase === 'observation') {
    label = `Observation #${step}`;
    detail = data.failed ? `❌ ${data.result || '失败'}` : (data.result || '');
  } else {
    label = String(phase);
    detail = data.message || data.tool || '';
  }

  const item = document.createElement('div');
  item.className = `react-step react-step-${phase}${data.failed ? ' react-step-failed' : ''}`;
  item.innerHTML = `
    <span class="react-step-label">${escapeHtml(label)}</span>
    <span class="react-step-detail">${escapeHtml(detail)}</span>
  `;
  stepsEl.appendChild(item);
  scrollToBottom();
}

function finalizeStream(el, text, meta, isError = false) {
  const bubble = el.querySelector('.bubble');
  if (!bubble) return;

  bubble.classList.remove('streaming');
  if (isError) bubble.classList.add('error-bubble');

  const statusEl = bubble.querySelector('.stream-status');
  if (statusEl) statusEl.remove();

  const contentEl = bubble.querySelector('.stream-content');
  if (contentEl) {
    contentEl.innerHTML = formatReply(text);
  }

  if (meta) {
    const metaEl = document.createElement('div');
    metaEl.className = 'bubble-meta';
    metaEl.innerHTML = meta;
    bubble.appendChild(metaEl);
  }

  scrollToBottom();
}

function buildMeta(data) {
  if (!data) return null;
  const tags = [];
  if (data.mode) tags.push(`<span class="meta-tag">模式 ${escapeHtml(data.mode)}</span>`);
  const llmRounds = data.llmRounds ?? data.reactRounds;
  if (llmRounds != null) {
    tags.push(`<span class="meta-tag">LLM 推理 ${llmRounds} 轮</span>`);
  }
  const toolCount = data.toolInvocations ?? data.toolCalls?.length;
  if (toolCount != null && toolCount > 0) {
    tags.push(`<span class="meta-tag">工具 ${toolCount} 次</span>`);
  }
  if (data.fallback) tags.push(`<span class="meta-tag">已降级</span>`);
  if (data.toolCalls?.length) {
    tags.push(`<span class="meta-tag">${escapeHtml(data.toolCalls.join(' · '))}</span>`);
  }
  return tags.length ? tags.join('') : null;
}

function appendUserMessage(text) {
  const el = document.createElement('div');
  el.className = 'message message-user';
  el.innerHTML = `
    <div class="avatar user">我</div>
    <div class="bubble"><p>${escapeHtml(text)}</p></div>
  `;
  messageList.appendChild(el);
  scrollToBottom();
}

function formatReply(text) {
  if (!text) return '<p>（无回复内容）</p>';
  return text
    .split('\n')
    .map((line) => {
      const trimmed = line.trim();
      if (!trimmed) return '';
      let html = escapeHtml(trimmed);
      html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
      if (trimmed.startsWith('- ')) return `<p>• ${html.slice(2)}</p>`;
      return `<p>${html}</p>`;
    })
    .join('');
}

function updateSessionDisplay() {
  sessionIdDisplay.textContent = sessionId ? sessionId.slice(0, 8) + '…' : '新会话';
  sessionIdDisplay.title = sessionId || '';
}

function updateModeBadge(data) {
  modeBadge.className = 'mode-badge';
  if (data?.mode === 'error') {
    modeBadge.classList.add('mode-error');
    modeBadge.textContent = 'Error';
    return;
  }
  if (data?.fallback) {
    modeBadge.classList.add('mode-fallback');
    modeBadge.textContent = '降级模式';
    statusText.textContent = 'AI 不可用，已切换规则引擎';
  } else {
    modeBadge.classList.add('mode-react');
    modeBadge.textContent = 'ReAct';
    statusText.textContent = '流式响应已开启 · 支持订单查询与常见问题';
  }
}

function startNewSession() {
  if (abortController) abortController.abort();
  sessionId = null;
  sessionStorage.removeItem(SESSION_KEY);
  updateSessionDisplay();
  messageList.innerHTML = `
    <div class="message message-bot welcome-msg">
      <div class="avatar bot">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
        </svg>
      </div>
      <div class="bubble">
        <p>已开启新会话。有什么可以帮您？</p>
      </div>
    </div>
  `;
  modeBadge.className = 'mode-badge mode-react';
  modeBadge.textContent = 'ReAct';
  statusText.textContent = '流式响应已开启 · 支持订单查询与常见问题';
}

function setSending(sending) {
  isSending = sending;
  sendBtn.disabled = sending;
  messageInput.disabled = sending;
  sendBtn.classList.toggle('sending', sending);
}

function scrollToBottom() {
  messageList.scrollTop = messageList.scrollHeight;
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

init();
