const app = document.getElementById('app');

async function api(path, options = {}) {
  const res = await fetch(path, options);
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

function esc(v) {
  return String(v ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}

function eventRows(list) {
  return (list || []).map(e => `
    <tr>
      <td>${esc(e.ts)}</td>
      <td>${esc(e.category)}</td>
      <td>${esc(e.action)}</td>
      <td>${esc(e.actor_name)}</td>
      <td>${esc(e.item_id)}</td>
      <td>${esc(e.detail)}</td>
      <td>
        <a href="#" data-incident="${esc(e.event_id)}">事发链</a>
        ${e.trace_id ? `<a href="#" data-trace="${esc(e.trace_id)}">追溯</a>` : ''}
      </td>
    </tr>`).join('');
}

async function renderDash() {
  const d = await api('/api/dashboard');
  app.innerHTML = `
    <div class="grid">
      <div class="stat"><div class="label">24h 事件</div><div class="value">${esc(d.events24h)}</div></div>
      <div class="stat"><div class="label">未确认告警</div><div class="value">${esc(d.alertsOpen)}</div></div>
      <div class="stat"><div class="label">最近事件</div><div class="value">${esc((d.recentEvents||[]).length)}</div></div>
      <div class="stat"><div class="label">最近告警</div><div class="value">${esc((d.recentAlerts||[]).length)}</div></div>
    </div>
    <section class="panel"><h2>最近告警</h2>
      <table><thead><tr><th>时间</th><th>级别</th><th>标题</th><th>玩家</th><th>内容</th></tr></thead>
      <tbody>${(d.recentAlerts||[]).map(a => `<tr>
        <td>${esc(a.ts)}</td><td class="sev-${esc(a.severity)}">${esc(a.severity)}</td>
        <td>${esc(a.title)}</td><td>${esc(a.actor_name)}</td><td>${esc(a.message)}</td>
      </tr>`).join('')}</tbody></table>
    </section>
    <section class="panel"><h2>最近事件</h2>
      <table><thead><tr><th>时间</th><th>类别</th><th>动作</th><th>玩家</th><th>物品</th><th>详情</th><th></th></tr></thead>
      <tbody>${eventRows(d.recentEvents)}</tbody></table>
    </section>`;
  bindLinks();
}

async function renderSearch() {
  app.innerHTML = `
    <section class="panel">
      <form class="form-row" id="searchForm">
        <input name="q" placeholder="关键词"/>
        <input name="category" placeholder="category"/>
        <input name="actor" placeholder="玩家 UUID/名称"/>
        <input name="traceId" placeholder="traceId"/>
        <button type="submit">搜索</button>
      </form>
      <table><thead><tr><th>时间</th><th>类别</th><th>动作</th><th>玩家</th><th>物品</th><th>详情</th><th></th></tr></thead>
      <tbody id="searchBody"></tbody></table>
    </section>`;
  document.getElementById('searchForm').onsubmit = async (ev) => {
    ev.preventDefault();
    const fd = new FormData(ev.target);
    const qs = new URLSearchParams([...fd.entries()].filter(([, v]) => v));
    const rows = await api('/api/search?' + qs.toString());
    document.getElementById('searchBody').innerHTML = eventRows(rows);
    bindLinks();
  };
}

async function renderAlerts() {
  const alerts = await api('/api/alerts?open=true');
  app.innerHTML = `
    <section class="panel"><h2>未确认告警</h2>
      <table><thead><tr><th>时间</th><th>级别</th><th>规则</th><th>标题</th><th>内容</th><th>玩家</th><th></th></tr></thead>
      <tbody>${(alerts||[]).map(a => `<tr>
        <td>${esc(a.ts)}</td><td class="sev-${esc(a.severity)}">${esc(a.severity)}</td>
        <td>${esc(a.rule_code)}</td><td>${esc(a.title)}</td><td>${esc(a.message)}</td><td>${esc(a.actor_name)}</td>
        <td>
          <button data-ack="${esc(a.alert_id)}">确认</button>
          ${a.related_event_id ? `<a href="#" data-incident="${esc(a.related_event_id)}">事发链</a>` : ''}
        </td>
      </tr>`).join('')}</tbody></table>
    </section>`;
  document.querySelectorAll('[data-ack]').forEach(btn => btn.onclick = async () => {
    await api('/api/alerts/' + btn.dataset.ack + '/ack', { method: 'POST' });
    renderAlerts();
  });
  bindLinks();
}

async function renderIncident(eventId) {
  const d = await api('/api/incident/' + encodeURIComponent(eventId));
  app.innerHTML = `
    <section class="panel"><h2>事发链 ${esc(eventId)}</h2>
      <pre>${esc(JSON.stringify(d.seed || d, null, 2))}</pre>
      <h2>时间线</h2>
      <table><thead><tr><th>时间</th><th>类别</th><th>动作</th><th>详情</th></tr></thead>
      <tbody>${(d.events||[]).map(e => `<tr>
        <td>${esc(e.ts)}</td><td>${esc(e.category)}</td><td>${esc(e.action)}</td><td>${esc(e.detail)}</td>
      </tr>`).join('')}</tbody></table>
      <h2>移动轨迹</h2>
      <table><thead><tr><th>时间</th><th>维度</th><th>X</th><th>Y</th><th>Z</th></tr></thead>
      <tbody>${(d.movements||[]).map(m => `<tr>
        <td>${esc(m.ts)}</td><td>${esc(m.dimension)}</td><td>${esc(m.x)}</td><td>${esc(m.y)}</td><td>${esc(m.z)}</td>
      </tr>`).join('')}</tbody></table>
    </section>`;
}

async function renderTrace(traceId) {
  const d = await api('/api/trace/' + encodeURIComponent(traceId));
  app.innerHTML = `
    <section class="panel"><h2>物品来源链 ${esc(traceId)}</h2>
      <pre>${esc(JSON.stringify(d.itemTrace || d.trace || d, null, 2))}</pre>
      <h2>链路节点</h2>
      <table><thead><tr><th>时间</th><th>动作</th><th>玩家</th><th>详情</th></tr></thead>
      <tbody>${(d.itemLinks||d.links||[]).map(l => `<tr>
        <td>${esc(l.ts)}</td><td>${esc(l.action)}</td><td>${esc(l.actorName||l.actor_name)}</td><td>${esc(l.detail)}</td>
      </tr>`).join('')}</tbody></table>
    </section>`;
}

function bindLinks() {
  document.querySelectorAll('[data-incident]').forEach(a => a.onclick = (e) => {
    e.preventDefault();
    renderIncident(a.dataset.incident);
  });
  document.querySelectorAll('[data-trace]').forEach(a => a.onclick = (e) => {
    e.preventDefault();
    renderTrace(a.dataset.trace);
  });
}

document.querySelectorAll('nav [data-view]').forEach(a => a.onclick = (e) => {
  e.preventDefault();
  const view = a.dataset.view;
  if (view === 'dash') renderDash();
  if (view === 'search') renderSearch();
  if (view === 'alerts') renderAlerts();
});

renderDash().catch(err => {
  app.textContent = '加载失败: ' + err.message + '（浏览器会弹出 Basic Auth，账号见配置 panelUsername/panelPassword）';
});
