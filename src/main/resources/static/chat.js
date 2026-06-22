// Chat interactions — called from inline onclick handlers in chat fragments

function sessionId() {
  return document.getElementById('session-id')?.value;
}

function topic() {
  return document.getElementById('topic')?.value;
}

async function sendMsg() {
  const input = document.getElementById('msg-input');
  const message = input?.value?.trim();
  if (!message || !sessionId()) return;
  input.value = '';
  input.style.height = '42px';

  // Optimistically show user message while waiting
  const messages = document.getElementById('messages');
  const placeholder = document.createElement('div');
  placeholder.className = 'msg user';
  placeholder.innerHTML = `<div class="msg-label">You</div><div class="msg-bubble">${escHtml(message)}</div>`;
  messages?.appendChild(placeholder);
  messages?.scrollTo(0, messages.scrollHeight);

  const params = new URLSearchParams({ sessionId: sessionId(), message });
  const res = await fetch('/chat/message?' + params, { method: 'POST' });
  const html = await res.text();
  if (messages && placeholder) {
    placeholder.remove();
    messages.insertAdjacentHTML('beforeend', html);
    messages.scrollTo(0, messages.scrollHeight);
  }
}

async function extractFindings() {
  if (!sessionId()) return;
  const panel = document.getElementById('findings-panel');
  if (panel) panel.innerHTML = '<div class="findings-header">Extracting…</div><div class="findings-scroll"><div class="findings-empty"><div class="spinner"></div></div></div>';

  const res = await fetch('/chat/extract?sessionId=' + sessionId(), { method: 'POST' });
  const html = await res.text();
  if (panel) panel.innerHTML = html;
}

async function saveFindings() {
  const cards = document.querySelectorAll('.finding-card:not(.rejected)');
  const types = [], titles = [], bodies = [];
  cards.forEach(c => {
    types.push(c.dataset.type);
    titles.push(c.dataset.title);
    bodies.push(c.dataset.body);
  });

  const params = new URLSearchParams({ sessionId: sessionId(), topic: topic() });
  types.forEach(t => params.append('types', t));
  titles.forEach(t => params.append('titles', t));
  bodies.forEach(b => params.append('bodies', b));

  const panel = document.getElementById('findings-panel');
  if (panel) panel.innerHTML = '<div class="findings-header">Saving…</div><div class="findings-scroll"><div class="findings-empty"><div class="spinner"></div></div></div>';

  const res = await fetch('/chat/save?' + params, { method: 'POST' });
  const html = await res.text();
  if (panel) panel.innerHTML = html;
}

function toggleCard(index, keep) {
  const card = document.getElementById('card-' + index);
  if (!card) return;
  if (keep) {
    card.classList.remove('rejected');
    card.querySelector('.keep').classList.add('active');
    card.querySelector('.reject').classList.remove('active');
  } else {
    card.classList.add('rejected');
    card.querySelector('.reject').classList.add('active');
    card.querySelector('.keep').classList.remove('active');
  }
}

function escHtml(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

// Auto-grow textarea
document.addEventListener('input', e => {
  if (e.target.id === 'msg-input') {
    e.target.style.height = '42px';
    e.target.style.height = Math.min(e.target.scrollHeight, 120) + 'px';
  }
});
