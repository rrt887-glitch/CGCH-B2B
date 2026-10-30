/**
 * 图标离线打包：构建时扫描源码里用到的图标名（如 lucide:bot、mdi:star），
 * 从 @iconify/json 里只取这些图标，生成虚拟模块 virtual:cgch-offline-icons，启动时注册。
 * 这样页面不再去 api.iconify.design 等国外网站临时下载图标，国内打开时图标立即显示。
 */
import type { Plugin } from 'vite';

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const VIRTUAL = 'virtual:cgch-offline-icons';
const RESOLVED = `\0${VIRTUAL}`;
const NAME = /["'`]([a-z][a-z0-9]*(?:-[a-z0-9]+)*):([a-z0-9]+(?:-[a-z0-9]+)*)["'`]/g;
const EXT = /\.(vue|ts|tsx|js|mjs)$/;

function walk(dir: string, out: string[]) {
  let names: string[];
  try {
    names = readdirSync(dir);
  } catch {
    return;
  }
  for (const n of names) {
    if (n === 'node_modules' || n === 'dist' || n.startsWith('.') || n === '__tests__') continue;
    const p = join(dir, n);
    const st = statSync(p);
    if (st.isDirectory()) walk(p, out);
    else if (EXT.test(n)) out.push(p);
  }
}

interface Collection {
  prefix: string;
  icons: Record<string, any>;
  aliases?: Record<string, { parent: string }>;
  width?: number;
  height?: number;
}

export function offlineIcons(): Plugin {
  const here = dirname(fileURLToPath(import.meta.url));
  const root = resolve(here, '..', '..', '..'); // frontend/
  // @iconify/json 是 @vben-core/design 的依赖，从那里解析
  const req = createRequire(join(root, 'packages', '@core', 'base', 'design', 'package.json'));

  /** @iconify/json 的 json 目录：先按包名解析，不行再到 node_modules 里找 */
  function findJsonDir(): string | undefined {
    const tries: (() => string)[] = [
      () => dirname(req.resolve('@iconify/json/package.json')),
      () => resolve(dirname(req.resolve('@iconify/json')), '..'),
      () => join(root, 'packages', '@core', 'base', 'design', 'node_modules', '@iconify', 'json'),
      () => join(root, 'node_modules', '@iconify', 'json'),
    ];
    for (const t of tries) {
      try {
        const dir = join(t(), 'json');
        if (statSync(join(dir, 'lucide.json')).isFile()) return dir;
      } catch {
        // 试下一个位置
      }
    }
    return undefined;
  }

  function build(): string {
    const jsonDir = findJsonDir();
    if (!jsonDir) {
      throw new Error('[offline-icons] 找不到 @iconify/json，图标无法打包进网站');
    }
    const files: string[] = [];
    walk(join(root, 'apps', 'web-antd', 'src'), files);
    walk(join(root, 'packages'), files);
    const wanted = new Map<string, Set<string>>();
    for (const f of files) {
      const text = readFileSync(f, 'utf8');
      for (const m of text.matchAll(NAME)) {
        const [, prefix, name] = m;
        if (!prefix || !name) continue;
        if (!wanted.has(prefix)) wanted.set(prefix, new Set());
        wanted.get(prefix)!.add(name);
      }
    }
    const out: Collection[] = [];
    let total = 0;
    for (const [prefix, names] of wanted) {
      const file = jsonDir ? join(jsonDir, `${prefix}.json`) : '';
      let data: Collection;
      try {
        data = JSON.parse(readFileSync(file, 'utf8'));
      } catch {
        continue; // 不是图标集（例如 http:、mailto: 之类的字符串）
      }
      const pick: Collection = { prefix, icons: {}, aliases: {}, width: data.width, height: data.height };
      for (const n of names) {
        if (data.icons[n]) {
          pick.icons[n] = data.icons[n];
        } else if (data.aliases?.[n]) {
          const parent = data.aliases[n].parent;
          pick.aliases![n] = data.aliases[n];
          if (data.icons[parent]) pick.icons[parent] = data.icons[parent];
        }
      }
      const count = Object.keys(pick.icons).length;
      if (count) {
        total += count;
        out.push(pick);
      }
    }
    if (total < 100) {
      throw new Error(`[offline-icons] 只找到 ${total} 个图标，扫描或解析有问题`);
    }
    console.log(`[offline-icons] 打包 ${total} 个图标（${out.map((c) => c.prefix).join('、')}）`);
    return `import { addCollection } from '@vben/icons';\n${out.map((c) => `addCollection(${JSON.stringify(c)});`).join('\n')}\n`;
  }

  return {
    name: 'cgch-offline-icons',
    enforce: 'pre',
    resolveId(id) {
      return id === VIRTUAL ? RESOLVED : undefined;
    },
    load(id) {
      return id === RESOLVED ? build() : undefined;
    },
  };
}
