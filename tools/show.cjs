// 看某道真题的全貌：node tools/show.cjs <id 或 序号> [--full]
// 用来核对材料里有没有解题需要的数字（解析里的公式常常是缺的）
const fs = require('fs');
const path = require('path');
const P = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'bank_peipei.json');
const j = JSON.parse(fs.readFileSync(P, 'utf8'));
const key = process.argv[2];
const full = process.argv.includes('--full');
const q = /^\d+$/.test(key) ? j[Number(key)] : j.find(x => x.id === key);
if (!q) { console.error('找不到:', key); process.exit(1); }
const cut = (s, n) => (full ? String(s) : String(s).slice(0, n));
console.log(`id      ${q.id}`);
console.log(`chapter ${q.chapter}   skill ${q.skill}`);
console.log(`source  ${q.source}`);
console.log(`\n--- 材料 ---\n${cut(q.material, 1200)}`);
console.log(`\n--- 题干 ---\n${q.stem}`);
console.log(`\n--- 选项 ---`);
for (const [k, v] of Object.entries(q.options || {})) console.log(`  ${k}. ${v}`);
console.log(`\n答案 ${q.answer}`);
console.log(`\n--- 原解析 ---\n${cut(q.solution, 1500)}`);
console.log(`\nanim 步数 ${(q.anim || []).length}`);
