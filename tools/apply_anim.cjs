// 把补丁里的 anim 合并进 bank_peipei.json：node tools/apply_anim.cjs patch1.json [patch2.json ...]
// 补丁格式：{ "<题目id>": [ {t,b,kill,pick,facts}, ... ], ... }
//
// 先跑 check_anim.cjs 校验，不通过就别合。这里只做最后一道防线：
// 已经有 anim 的题默认不覆盖（要覆盖加 --force），避免手滑把之前写好的冲掉。
const fs = require('fs');
const path = require('path');
const BANK = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'bank_peipei.json');

const force = process.argv.includes('--force');
const files = process.argv.slice(2).filter(a => !a.startsWith('--'));
if (!files.length) { console.error('用法: node tools/apply_anim.cjs patch.json [--force]'); process.exit(1); }

const bank = JSON.parse(fs.readFileSync(BANK, 'utf8'));
const byId = new Map(bank.map(q => [q.id, q]));

let added = 0, skipped = 0, missing = 0;
for (const f of files) {
  const patch = JSON.parse(fs.readFileSync(f, 'utf8'));
  for (const [id, anim] of Object.entries(patch)) {
    const q = byId.get(id);
    if (!q) { console.log(`  ? ${id} 题库里没有，跳过`); missing++; continue; }
    if ((q.anim || []).length && !force) { skipped++; continue; }
    q.anim = anim;
    added++;
  }
}

// 保持原文件的紧凑格式（2.3MB 已经在 APK 里，别因为格式化把体积翻倍）
fs.writeFileSync(BANK, JSON.stringify(bank), 'utf8');
const total = bank.length;
const withAnim = bank.filter(q => (q.anim || []).length).length;
console.log(`写入 ${added} 题，跳过已有 ${skipped} 题，id 不存在 ${missing} 题`);
console.log(`覆盖率：${withAnim}/${total} = ${(withAnim / total * 100).toFixed(1)}%`);
console.log(`文件大小：${(fs.statSync(BANK).size / 1024 / 1024).toFixed(2)} MB`);
