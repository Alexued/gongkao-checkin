// 估算「材料缺数导致做不了」的规模：node tools/count_blocked.cjs
// 判据不是「材料里有没有缺口」，而是「缺口多到把整段的增速全吃掉」——
// 像证券公司那组，八处「同比增长。」后面全是空，基期类题一道都做不了。
const fs = require('fs');
const path = require('path');
const BANK = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'bank_peipei.json');
const bank = JSON.parse(fs.readFileSync(BANK, 'utf8'));

// 材料里「同比增长/下降」后面直接跟标点的次数
const holes = m => (String(m).match(/同比(?:增长|下降|增加|减少|提高|降低)(?=[。，；、）])/g) || []).length;
// 材料里还剩几个可用的百分数
const pcts = m => (String(m).match(/\d+(?:\.\d+)?%/g) || []).length;
// 解析要求看图
const chart = s => /定位柱形?图|定位折线图|定位条形图|根据(?:所给)?柱状图|观察表格|两个柱状图|定位图表|定位表\d/.test(String(s));

let noRate = 0, chartOnly = 0, ok = 0;
const rows = [];
for (const q of bank) {
  const h = holes(q.material), p = pcts(q.material);
  const c = chart(q.solution);
  let tag;
  if (c) { tag = '要看图/表'; chartOnly++; }
  else if (h >= 3 && p === 0) { tag = '增速全被抠空'; noRate++; }
  else { tag = 'ok'; ok++; }
  rows.push({ id: q.id, chapter: q.chapter, tag, h, p });
}
console.log(`题库 ${bank.length} 题`);
console.log(`  要看图/表（解析明确要求定位图或表，材料是纯文字）：${chartOnly}`);
console.log(`  材料增速全被抠空（缺口≥3 处且一个百分数都不剩）：${noRate}`);
console.log(`  其余（大概率可写）：${ok}  = ${(ok / bank.length * 100).toFixed(1)}%`);
console.log(`\n注：这只是粗筛。实际能不能写要逐题看——本题需要的那个数在不在，`);
console.log(`    跟材料别处缺不缺没关系。已写的 ${bank.filter(q => (q.anim || []).length).length} 题里，`);
console.log(`    有几道就是「材料有缺口但本题用不到」。`);

if (process.argv.includes('--list')) {
  const bad = rows.filter(r => r.tag !== 'ok');
  console.log('\n可疑题：');
  for (const r of bad) console.log(`  ${r.id}  ${r.tag}  缺口${r.h} 余百分数${r.p}  ${r.chapter}`);
}
