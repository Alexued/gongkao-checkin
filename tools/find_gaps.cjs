// 找材料里被抠掉数字的题：node tools/find_gaps.cjs [--list]
// 原始 PPT/网页里有些数值是图片，提取时丢了，留下「占……的。」「同比增长。」这种断句。
// 这种题材料自身不自洽，讲解写不出来（硬写就是编数字），得单独挑出来。
const fs = require('fs');
const path = require('path');
const BANK = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'bank_peipei.json');
const bank = JSON.parse(fs.readFileSync(BANK, 'utf8'));

// 「的」「增长」「为」「达」后面直接跟句读＝该处本应有个数
const PATS = [
  /(?:占|比重为|比重达)[^，。；、]{0,8}的(?=[。，；、）])/,
  /同比(?:增长|下降|增加|减少)(?=[。，；、）])/,
  /(?:增长|下降|增速为|增幅为)(?=[。，；、）])/,
  /(?:为|达到?)(?=[。，；、）])/,
];

// 解析里的公式/算式原本是图片，丢了之后留下「根据公式：，则」「代入公式，倍」这种。
// 这类题**能**写讲解，但我得自己把算式重算一遍（不能照抄解析）。
const SOL_PATS = [
  /根据公式[：:]\s*[，。]/,
  /代入公式[，,]\s*[倍％%]/,
  /则所求\s*[为＝=]?\s*[，。]/,
  /可得所求\s*[，。]/,
  /[，,]\s*即\s*[，。]/,
];

const hits = [];
for (const q of bank) {
  const m = String(q.material || '');
  const which = PATS.filter(p => p.test(m));
  if (which.length) hits.push({ q, n: which.length });
}
const solGaps = bank.filter(q => SOL_PATS.some(p => p.test(String(q.solution || ''))));

// 解析里明说要看图（柱形图/折线图/表格），但材料是纯文字——这种题**做不了**，
// 抽取时的「解析里说定位统计表/图」过滤漏掉了「柱形图」「折线图」这些说法。
const CHART_PATS = [/定位柱形?图/, /定位折线图/, /定位条形图/, /根据(?:所给)?柱状图/, /观察表格/, /两个柱状图/, /定位图表/];
const chartOnly = bank.filter(q =>
  CHART_PATS.some(p => p.test(String(q.solution || ''))) && !q.table
);

console.log(`题库 ${bank.length} 题`);
console.log(`  材料里有缺数的：${hits.length} 题 (${(hits.length / bank.length * 100).toFixed(1)}%)`);
console.log(`    —— 注意：只说明材料某处缺数，不等于本题做不了；`);
console.log(`       缺的那个数可能跟本题无关（实测 4 道平均数题全被标记但全可解）`);
console.log(`  解析里公式/结果缺失的：${solGaps.length} 题 (${(solGaps.length / bank.length * 100).toFixed(1)}%)`);
console.log(`    —— 这类能写讲解，但算式必须自己重算，不能照抄解析`);
console.log(`  解析要求看图、但材料是纯文字的：${chartOnly.length} 题 (${(chartOnly.length / bank.length * 100).toFixed(1)}%)`);
console.log(`    —— 这类**做不了**，数据在丢掉的图里。抽取时的图表过滤漏了「柱形图/折线图」这些说法`);
if (process.argv.includes('--chart')) {
  console.log('\n看图题 id：');
  for (const q of chartOnly) console.log(`  ${q.id}  ${q.chapter}  ${q.skill}`);
}
const byCh = {};
for (const h of hits) byCh[h.q.chapter] = (byCh[h.q.chapter] || 0) + 1;
console.log('\n按章节：');
for (const [k, v] of Object.entries(byCh).sort((a, b) => b[1] - a[1])) {
  const tot = bank.filter(q => q.chapter === k).length;
  console.log(`  ${String(v).padStart(4)}/${String(tot).padEnd(4)}  ${k}`);
}
if (process.argv.includes('--list')) {
  console.log('\nid 列表：');
  for (const h of hits) console.log(`  ${h.q.id}  ${h.q.chapter}  ${h.q.skill}`);
}
