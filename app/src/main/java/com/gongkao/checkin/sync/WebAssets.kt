package com.gongkao.checkin.sync

/** 电脑端网页（单文件，无外部依赖）。 */
object WebAssets {

    fun page(): String = HEAD + BODY + SCRIPT

    private const val HEAD = """<!DOCTYPE html>
<html lang="zh-CN"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>考公打卡 · 电脑端</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
:root{
  --bg:#F4F6FB; --card:#fff; --ink:#0E1526; --sub:#5C6780; --dim:#8C97AF;
  --line:#E4E8F1; --accent:#6C8CFF; --teal:#35D0BA; --amber:#FFB24D; --rose:#FF6B8B;
  --shadow:0 4px 24px rgba(20,32,66,.07);
  --spring:cubic-bezier(.32,.72,0,1);
}
body{background:var(--bg);color:var(--ink);font:14px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;padding:24px;min-height:100vh}
.wrap{max-width:1180px;margin:0 auto;display:grid;gap:18px}
.hero{background:linear-gradient(135deg,#6C8CFF,#7C7BFF 45%,#A67BFF);border-radius:24px;padding:26px 30px;color:#fff;display:flex;align-items:center;gap:30px;box-shadow:0 10px 34px rgba(108,140,255,.28)}
.hero h1{font-size:22px;font-weight:600;letter-spacing:.5px}
.hero .date{opacity:.86;font-size:13px;margin-top:2px}
.hero .grow{flex:1}
.chips{display:flex;gap:10px;margin-top:14px;flex-wrap:wrap}
.chip{background:rgba(255,255,255,.18);border:1px solid rgba(255,255,255,.24);padding:5px 12px;border-radius:11px;font-size:12px;backdrop-filter:blur(6px)}
.ringbox{position:relative;width:118px;height:118px;flex:0 0 auto}
.ringbox svg{transform:rotate(-90deg)}
.ringbox .val{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center}
.ringbox .val b{font-size:26px;font-weight:600;line-height:1}
.ringbox .val span{font-size:11px;opacity:.85;margin-top:3px}
.grid{display:grid;grid-template-columns:1.35fr 1fr;gap:18px;align-items:start}
@media(max-width:980px){.grid{grid-template-columns:1fr}}
.card{background:var(--card);border-radius:20px;padding:20px 22px;box-shadow:var(--shadow)}
.card h2{font-size:15px;font-weight:600;margin-bottom:4px;display:flex;align-items:center;gap:8px}
.card h2 em{font-style:normal;color:var(--dim);font-size:12px;font-weight:400}
.card .hint{color:var(--dim);font-size:12px;margin-bottom:14px}
.task{display:flex;align-items:center;gap:13px;padding:13px 14px;border-radius:15px;background:#FAFBFE;border:1px solid var(--line);margin-bottom:9px;transition:transform .42s var(--spring),background .3s,opacity .3s,box-shadow .3s}
.task:hover{transform:translateY(-2px);box-shadow:0 6px 18px rgba(20,32,66,.08)}
.task.done{opacity:.55;background:#F2F5FA}
.task.done .t{text-decoration:line-through;color:var(--dim)}
.task.carry{background:#FFF9F0;border-color:#FBE3C2}
.box{width:26px;height:26px;border-radius:50%;border:2px solid #C3CBDD;flex:0 0 auto;cursor:pointer;display:flex;align-items:center;justify-content:center;transition:transform .5s var(--spring),background .3s,border-color .3s;font-size:12px;color:#fff;user-select:none}
.box:hover{transform:scale(1.12)}
.box:active{transform:scale(.9)}
.box.on{background:var(--accent);border-color:var(--accent)}
.box.multi{font-size:10px;color:var(--sub);font-weight:600}
.box.multi.on{color:#fff}
.task .m{flex:1;min-width:0}
.task .t{font-size:14px;font-weight:500;word-break:break-all}
.task .s{font-size:11.5px;color:var(--dim);margin-top:2px;display:flex;gap:8px;flex-wrap:wrap}
.badge{background:#FFEFD8;color:#C8791A;border-radius:7px;padding:1px 7px;font-size:11px;font-weight:600}
.badge.blue{background:#E8EEFF;color:#4A63D8}
.mini{display:flex;align-items:flex-end;gap:4px;height:64px;margin-top:6px}
.mini i{flex:1;background:linear-gradient(180deg,var(--accent),rgba(108,140,255,.25));border-radius:4px 4px 2px 2px;min-height:3px;transition:height .6s var(--spring);position:relative}
.mini i:hover::after{content:attr(data-t);position:absolute;bottom:100%;left:50%;transform:translateX(-50%);background:var(--ink);color:#fff;font-size:10px;padding:2px 6px;border-radius:5px;white-space:nowrap;font-style:normal}
table{width:100%;border-collapse:collapse;font-size:12.5px}
th{text-align:left;color:var(--dim);font-weight:500;padding:7px 8px;border-bottom:1px solid var(--line);font-size:11.5px}
td{padding:8px;border-bottom:1px solid #F1F4F9}
tr:last-child td{border-bottom:none}
.mono{font-variant-numeric:tabular-nums;font-family:ui-monospace,SFMono-Regular,Menlo,monospace}
input,select,button{font:inherit;color:inherit}
input,select{background:#F5F7FB;border:1px solid var(--line);border-radius:11px;padding:9px 12px;width:100%;outline:none;transition:border-color .25s,background .25s}
input:focus,select:focus{border-color:var(--accent);background:#fff}
.row{display:flex;gap:9px;margin-bottom:9px}
.row>*{flex:1}
.row.tight{gap:7px}
button{background:var(--accent);color:#fff;border:none;border-radius:11px;padding:10px 18px;cursor:pointer;font-weight:500;transition:transform .38s var(--spring),filter .25s,box-shadow .25s}
button:hover{filter:brightness(1.06);box-shadow:0 5px 16px rgba(108,140,255,.32)}
button:active{transform:scale(.95)}
button.ghost{background:#EEF1F8;color:var(--sub)}
button.tiny{padding:5px 11px;font-size:12px;border-radius:9px}
button.danger{background:#FFECF0;color:var(--rose)}
.empty{color:var(--dim);font-size:12.5px;text-align:center;padding:22px 0}
.gate{position:fixed;inset:0;background:rgba(14,21,38,.55);backdrop-filter:blur(7px);display:none;align-items:center;justify-content:center;z-index:50}
.gate.show{display:flex}
.gate .box2{background:#fff;border-radius:22px;padding:28px 30px;width:320px;text-align:center;box-shadow:0 24px 60px rgba(0,0,0,.3);animation:pop .5s var(--spring)}
@keyframes pop{from{transform:scale(.9) translateY(14px);opacity:0}to{transform:none;opacity:1}}
.gate h3{font-size:16px;margin-bottom:6px}
.gate p{color:var(--dim);font-size:12px;margin-bottom:16px}
.dot{width:7px;height:7px;border-radius:50%;background:var(--teal);display:inline-block;animation:pulse 2.4s infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.35}}
.done-banner{background:linear-gradient(135deg,#35D0BA,#2FB39F);color:#fff;border-radius:16px;padding:15px 18px;font-size:13.5px;font-weight:500;margin-bottom:13px;animation:pop .55s var(--spring)}
.taskrow{display:flex;align-items:center;gap:10px;padding:9px 11px;border-radius:12px;background:#FAFBFE;border:1px solid var(--line);margin-bottom:7px;font-size:13px}
.taskrow .g{flex:1}
.taskrow .d{color:var(--dim);font-size:11.5px}
.tabs{display:flex;gap:6px;margin-bottom:12px}
.tabs button{background:#EEF1F8;color:var(--sub);padding:6px 13px;font-size:12.5px;border-radius:9px}
.tabs button.on{background:var(--accent);color:#fff}
</style></head>
"""
    private const val BODY = """<body>
<div class="wrap">
  <div class="hero">
    <div class="ringbox">
      <svg width="118" height="118">
        <circle cx="59" cy="59" r="50" fill="none" stroke="rgba(255,255,255,.24)" stroke-width="9"/>
        <circle id="ring" cx="59" cy="59" r="50" fill="none" stroke="#fff" stroke-width="9"
                stroke-linecap="round" stroke-dasharray="314.16" stroke-dashoffset="314.16"
                style="transition:stroke-dashoffset .9s cubic-bezier(.32,.72,0,1)"/>
      </svg>
      <div class="val"><b id="pct">0%</b><span id="frac">0/0</span></div>
    </div>
    <div class="grow">
      <h1 id="hello">今日计划</h1>
      <div class="date" id="dateText">--</div>
      <div class="chips">
        <div class="chip" id="cLeft">倒计时 --</div>
        <div class="chip" id="cStreak">连续全勤 --</div>
        <div class="chip"><span class="dot"></span> <span id="cSync">已连接</span></div>
      </div>
    </div>
  </div>

  <div class="grid">
    <div class="card">
      <h2>今日任务 <em id="todayCount"></em></h2>
      <div class="hint">点圆圈打卡，手机端会立刻同步。橙色是昨天没做完累加过来的。</div>
      <div id="banner"></div>
      <div id="items"><div class="empty">加载中…</div></div>
    </div>

    <div style="display:grid;gap:18px">
      <div class="card">
        <h2>近 21 天完成度</h2>
        <div class="mini" id="mini"></div>
      </div>
      <div class="card">
        <h2>任务管理</h2>
        <div class="hint">新增后立即出现在今天的清单里。</div>
        <div class="row"><input id="nt" placeholder="任务名称，例如「资料分析 20 题」"></div>
        <div class="row tight">
          <select id="nr">
            <option value="DAILY">每天都要</option>
            <option value="UNTIL">截止到某天</option>
          </select>
          <input id="nu" type="date" style="display:none">
          <input id="ng" type="number" min="1" value="1" title="每天几次" style="max-width:80px">
        </div>
        <div class="row"><button onclick="addTask()">添加任务</button></div>
        <div id="tasks"></div>
      </div>
      <div class="card">
        <h2>总结束日</h2>
        <div class="hint">到这天为止不再生成新任务，首页显示倒计时。</div>
        <div class="row tight">
          <input id="ed" type="date">
          <button class="ghost" style="flex:0 0 auto" onclick="setEnd()">保存</button>
        </div>
      </div>
    </div>
  </div>

  <div class="card">
    <h2>记录</h2>
    <div class="tabs">
      <button class="on" onclick="tab(0,this)">做题计时</button>
      <button onclick="tab(1,this)">百化分</button>
      <button onclick="tab(2,this)">公式背诵</button>
    </div>
    <div id="rec"></div>
  </div>
</div>

<div class="gate" id="gate">
  <div class="box2">
    <h3>输入配对 PIN</h3>
    <p>PIN 在手机端「统计 → 电脑端同步」里查看</p>
    <input id="pin" maxlength="4" inputmode="numeric" placeholder="4 位数字"
           style="text-align:center;font-size:22px;letter-spacing:8px;font-variant-numeric:tabular-nums">
    <div style="height:14px"></div>
    <button style="width:100%" onclick="savePin()">连接</button>
  </div>
</div>
"""

    private const val SCRIPT = """<script>
var PIN = localStorage.getItem('ck_pin') || '';
var rev = -1, data = null, tabIdx = 0, busy = false;

function api(path, body){
  var opt = {headers:{'X-Pin':PIN}};
  if(body){ opt.method='POST'; opt.body=JSON.stringify(Object.assign({pin:PIN}, body)); }
  return fetch(path, opt).then(function(r){
    if(r.status===401){ gate(true); throw new Error('pin'); }
    return r.json();
  });
}
function gate(show){ document.getElementById('gate').classList.toggle('show', show); }
function savePin(){
  PIN = document.getElementById('pin').value.trim();
  localStorage.setItem('ck_pin', PIN);
  gate(false); rev = -1; poll();
}
function esc(s){ return String(s==null?'':s).replace(/[&<>"]/g, function(c){
  return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]; }); }

function poll(){
  api('/api/state').then(function(d){
    document.getElementById('cSync').textContent = '已连接';
    if(d.revision === rev) return;
    rev = d.revision; data = d; render(d);
  }).catch(function(e){
    if(e.message!=='pin') document.getElementById('cSync').textContent = '连接中断';
  });
}

function render(d){
  document.getElementById('hello').textContent = d.nickname + '，今日计划';
  document.getElementById('dateText').textContent = d.dateText;
  document.getElementById('pct').textContent = Math.round(d.ratio*100) + '%';
  document.getElementById('frac').textContent = d.finished + '/' + d.total;
  var C = 2*Math.PI*50;
  document.getElementById('ring').setAttribute('stroke-dashoffset', C*(1-d.ratio));
  document.getElementById('cLeft').textContent = d.daysLeft==null ? '未设结束日'
      : (d.daysLeft>=0 ? '距结束还有 ' + d.daysLeft + ' 天' : '已超过结束日');
  document.getElementById('cStreak').textContent = '连续全勤 ' + d.streak + ' 天';
  document.getElementById('todayCount').textContent = d.finished + ' / ' + d.total + ' 已完成';
  if(d.endDate) document.getElementById('ed').value = d.endDate;

  document.getElementById('banner').innerHTML = (d.allDone && d.total>0)
    ? '<div class="done-banner">今日全部完成，继续保持这个节奏。</div>' : '';

  var h = '';
  d.items.forEach(function(it){
    var cls = 'task' + (it.done?' done':'') + (it.carry&&!it.done?' carry':'');
    var multi = it.target>1;
    var inner = it.done ? '✓' : (multi ? (it.progress+'/'+it.target) : '');
    var sub = [];
    if(it.carry) sub.push('<span class="badge">补 · 欠 '+it.debtDays+' 天</span>');
    if(multi) sub.push('已完成 '+it.progress+' / '+it.target+(it.unit||''));
    if(it.doneAt) sub.push('打卡 '+it.doneAt);
    h += '<div class="'+cls+'">'
      + '<div class="box'+(it.done?' on':'')+(multi?' multi':'')+'" onclick="bump(\''+it.key+'\',1)" '
      + 'oncontextmenu="event.preventDefault();bump(\''+it.key+'\',-1)">'+inner+'</div>'
      + '<div class="m"><div class="t">'+esc(it.title)+'</div>'
      + (sub.length?'<div class="s">'+sub.join('')+'</div>':'')+'</div>'
      + (it.done?'<button class="tiny ghost" onclick="bump(\''+it.key+'\',-1)">撤销</button>':'')
      + '</div>';
  });
  document.getElementById('items').innerHTML = h || '<div class="empty">今天没有任务，先在右边加一个。</div>';

  var m = '';
  d.recent.forEach(function(r){
    var pct = Math.round(r.ratio*100);
    m += '<i style="height:'+Math.max(4, r.ratio*64)+'px" data-t="'+r.date.slice(5)+' '+pct+'%"></i>';
  });
  document.getElementById('mini').innerHTML = m;

  var t = '';
  d.tasks.forEach(function(x){
    t += '<div class="taskrow"><div class="g">'+esc(x.title)
      + '<div class="d">'+x.deadline+(x.target>1?' · 每天 '+x.target+' 次':'')+'</div></div>'
      + '<button class="tiny danger" onclick="delTask(\''+x.id+'\')">删除</button></div>';
  });
  document.getElementById('tasks').innerHTML = t || '<div class="empty">还没有任务</div>';
  renderRec();
}

function renderRec(){
  if(!data) return;
  var h = '';
  if(tabIdx===0){
    h = '<table><tr><th>日期</th><th>名称</th><th>开始</th><th>总用时</th><th>打点</th><th>平均每点</th></tr>';
    data.timer.forEach(function(x){
      h += '<tr><td>'+x.date+'</td><td>'+esc(x.label||'做题计时')+'</td><td class="mono">'+x.startAt
        + '</td><td class="mono">'+x.duration+'</td><td>'+x.laps+'</td><td class="mono">'+x.avg+'</td></tr>';
    });
    h += '</table>';
    if(!data.timer.length) h = '<div class="empty">还没有计时记录</div>';
  } else if(tabIdx===1){
    h = '<table><tr><th>日期</th><th>模式</th><th>题量</th><th>正确</th><th>正确率</th><th>平均每题</th></tr>';
    data.percent.forEach(function(x){
      h += '<tr><td>'+x.date+'</td><td>'+x.mode+'</td><td>'+x.total+'</td><td>'+x.correct
        + '</td><td>'+x.accuracy+'%</td><td>'+x.avg+'</td></tr>';
    });
    h += '</table>';
    if(!data.percent.length) h = '<div class="empty">还没有百化分记录</div>';
  } else {
    h = '<table><tr><th>日期</th><th>模式</th><th>分类</th><th>条数</th><th>记住</th><th>掌握率</th></tr>';
    data.formula.forEach(function(x){
      h += '<tr><td>'+x.date+'</td><td>'+x.mode+'</td><td>'+esc(x.category)+'</td><td>'+x.total
        + '</td><td>'+x.known+'</td><td>'+x.accuracy+'%</td></tr>';
    });
    h += '</table>';
    if(!data.formula.length) h = '<div class="empty">还没有公式背诵记录</div>';
  }
  document.getElementById('rec').innerHTML = h;
}
function tab(i, el){
  tabIdx = i;
  document.querySelectorAll('.tabs button').forEach(function(b){ b.classList.remove('on'); });
  el.classList.add('on');
  renderRec();
}

function bump(key, delta){
  if(busy) return; busy = true;
  api('/api/action', {action:'bump', key:key, delta:delta})
    .then(function(){ rev = -1; poll(); }).finally(function(){ busy = false; });
}
function addTask(){
  var title = document.getElementById('nt').value.trim();
  if(!title) return;
  var repeat = document.getElementById('nr').value;
  var until = document.getElementById('nu').value;
  if(repeat==='UNTIL' && !until){ alert('请选择截止日期'); return; }
  api('/api/action', {action:'addTask', title:title, repeat:repeat,
      untilDate:until||null, target:parseInt(document.getElementById('ng').value)||1})
    .then(function(){ document.getElementById('nt').value=''; rev=-1; poll(); });
}
function delTask(id){
  if(!confirm('删除这个任务？已完成的历史记录会保留。')) return;
  api('/api/action', {action:'deleteTask', id:id}).then(function(){ rev=-1; poll(); });
}
function setEnd(){
  api('/api/action', {action:'setEndDate', date:document.getElementById('ed').value||null})
    .then(function(){ rev=-1; poll(); });
}

document.getElementById('nr').addEventListener('change', function(e){
  document.getElementById('nu').style.display = e.target.value==='UNTIL' ? '' : 'none';
});
document.getElementById('pin').addEventListener('keydown', function(e){
  if(e.key==='Enter') savePin();
});
if(!PIN) gate(true);
poll();
setInterval(poll, 1500);
</script></body></html>
"""
}
