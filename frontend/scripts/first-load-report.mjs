// 统计首次打开网站（启动阶段，包括登录页）要下载的文件数和压缩后大小，在 CI 里输出，方便对比优化效果。
// 用法：node scripts/first-load-report.mjs apps/web-antd/dist
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { brotliCompressSync } from 'node:zlib';

const dist = path.resolve(process.argv[2] || 'apps/web-antd/dist');
const html = readFileSync(path.join(dist, 'index.html'), 'utf8');

const sizeCache = new Map();
function brSize(rel) {
  if (!sizeCache.has(rel)) {
    const file = path.join(dist, rel);
    const size = existsSync(`${file}.br`)
      ? statSync(`${file}.br`).size
      : brotliCompressSync(readFileSync(file)).length;
    sizeCache.set(rel, size);
  }
  return sizeCache.get(rel);
}

const boot = new Set();
const queue = [];
function add(rel) {
  rel = rel.replace(/^\//, '');
  if (!boot.has(rel) && existsSync(path.join(dist, rel))) {
    boot.add(rel);
    if (rel.endsWith('.js')) queue.push(rel);
  }
}
for (const m of html.matchAll(/(?:src|href)="([^"]+\.(?:js|css))"/g)) add(m[1]);

// 静态 import 递归；入口里的动态 import（启动代码）按 __vite__mapDeps 记录的依赖全部计入
const entries = [...boot].filter((f) => f.startsWith('jse/'));
while (queue.length > 0) {
  const rel = queue.shift();
  const code = readFileSync(path.join(dist, rel), 'utf8');
  const dir = path.posix.dirname(rel);
  for (const m of code.matchAll(/(?:\bfrom|\bimport)\s*["'](\.{1,2}\/[^"']+)["']/g)) {
    add(path.posix.join(dir, m[1]));
  }
  if (entries.includes(rel) || path.posix.basename(rel).startsWith('bootstrap-')) {
    const files = code.match(/m\.f=\[([^\]]*)\]/);
    const list = files ? [...files[1].matchAll(/"([^"]+)"/g)].map((x) => x[1]) : [];
    for (const call of code.matchAll(/__vite__mapDeps\(\[([\d,]*)\]/g)) {
      for (const i of call[1].split(',').filter(Boolean)) add(list[Number(i)]);
    }
  }
}

const all = readdirSync(path.join(dist, 'js')).filter((f) => f.endsWith('.js'));
const rows = [...boot].map((f) => [f, brSize(f)]).sort((a, b) => b[1] - a[1]);
const total = rows.reduce((a, r) => a + r[1], 0);
const kb = (n) => `${(n / 1024).toFixed(0)}KB`;
console.log(
  `首次打开：${boot.size} 个文件，压缩后 ${kb(total)}；js 目录共 ${all.length} 个文件`,
);
console.log(`最大的文件：${rows.slice(0, 15).map(([f, s]) => `${f.split('/').pop()} ${kb(s)}`).join('，')}`);
