// 校验 anim 数据：node tools/check_anim.cjs [patch.json ...]
// 不带参数＝校验 bank_peipei.json 里已有的；带参数＝校验补丁文件（合并前先查一遍）
//
// 查这些（都是真出过问题的点）：
//   1. 每题恰好一步 pick=true —— 界面靠它确认答案
//   2. kill 里不能有正确答案 —— killOption 会挡掉，但那说明讲解写错了
//   3. kill 只能是 A/B/C/D，且不重复
//   4. facts 里的串必须在正文 b 里出现 —— 否则高亮落空
//   5. t/b 非空，b 不能太短（<15 字基本是占位）
//   6. 补丁里的 id 必须在题库里存在
const fs = require('fs');
const path = require('path');
const BANK = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'bank_peipei.json');
const bank = JSON.parse(fs.readFileSync(BANK, 'utf8'));
const byId = new Map(bank.map(q => [q.id, q]));

const errs = [];
const warns = [];

function checkOne(id, anim) {
  const q = byId.get(id);
  if (!q) { errs.push(`${id}: 题库里没有这个 id`); return; }
  if (!Array.isArray(anim) || anim.length === 0) { errs.push(`${id}: anim 空`); return; }
  const picks = anim.filter(s => s.pick === true).length;
  if (picks !== 1) errs.push(`${id}: pick=true 有 ${picks} 步，必须恰好 1`);
  const seen = new Set();
  anim.forEach((s, i) => {
    const at = `${id} 第${i + 1}步`;
    if (!s.t || !String(s.t).trim()) errs.push(`${at}: t 空`);
    if (!s.b || !String(s.b).trim()) errs.push(`${at}: b 空`);
    else if (String(s.b).trim().length < 15) warns.push(`${at}: b 只有 ${String(s.b).trim().length} 字，像占位`);
    for (const k of s.kill || []) {
      if (!['A', 'B', 'C', 'D'].includes(k)) errs.push(`${at}: kill 里有非法项 ${k}`);
      if (k === q.answer) errs.push(`${at}: kill 划掉了正确答案 ${k}`);
      if (seen.has(k)) warns.push(`${at}: ${k} 被重复 kill`);
      seen.add(k);
    }
    for (const f of s.facts || []) {
      if (!String(s.b).includes(String(f))) errs.push(`${at}: facts "${f}" 在正文里找不到`);
    }
  });
  if (seen.size >= 4) errs.push(`${id}: 四个选项全被 kill 了`);
}

const files = process.argv.slice(2);
let n = 0;
if (files.length) {
  for (const f of files) {
    const patch = JSON.parse(fs.readFileSync(f, 'utf8'));
    for (const [id, anim] of Object.entries(patch)) { checkOne(id, anim); n++; }
  }
} else {
  for (const q of bank) if ((q.anim || []).length) { checkOne(q.id, q.anim); n++; }
}

console.log(`校验 ${n} 题`);
if (warns.length) { console.log(`\n提醒 ${warns.length} 条：`); warns.forEach(w => console.log('  ! ' + w)); }
if (errs.length) { console.log(`\n错误 ${errs.length} 条：`); errs.forEach(e => console.log('  x ' + e)); process.exit(1); }
console.log('全部通过');
