// 导出一批还没有 anim 的题，供逐题写讲解
// node tools/dump_batch.cjs --chapter "增长率" [--limit 30] [--skill 一般增长率]
const fs = require('fs');
const path = require('path');
const BANK = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'bank_peipei.json');
const bank = JSON.parse(fs.readFileSync(BANK, 'utf8'));

const arg = (name, def) => {
  const i = process.argv.indexOf('--' + name);
  return i >= 0 ? process.argv[i + 1] : def;
};
const chapter = arg('chapter');
const skill = arg('skill');
const limit = Number(arg('limit', 999));
const matLen = Number(arg('matlen', 900));

let list = bank.filter(q => !(q.anim || []).length);
if (chapter) list = list.filter(q => q.chapter === chapter);
if (skill) list = list.filter(q => String(q.skill).includes(skill));
list = list.slice(0, limit);

console.log(`### 共 ${list.length} 题${chapter ? ` / 章节「${chapter}」` : ''}${skill ? ` / skill 含「${skill}」` : ''}\n`);
for (const q of list) {
  console.log(`===== ${q.id} | ${q.skill} | 答案 ${q.answer}`);
  console.log(`材料: ${String(q.material).replace(/\s+/g, ' ').slice(0, matLen)}`);
  console.log(`题干: ${q.stem}`);
  console.log(`选项: ${Object.entries(q.options || {}).map(([k, v]) => k + '.' + v).join('  ')}`);
  console.log(`原解析: ${String(q.solution).replace(/\s+/g, ' ').slice(0, 500)}`);
  console.log();
}
