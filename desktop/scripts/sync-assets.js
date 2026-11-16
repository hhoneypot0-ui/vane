// 把 Android 端的资源同步进 desktop/assets，打包时只需带这一个目录。
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..');
const src = path.join(root, 'app', 'src', 'main', 'assets');
const dst = path.join(__dirname, '..', 'assets');

fs.mkdirSync(dst, { recursive: true });
for (const f of ['home.html', 'player.html', 'hosts.txt']) {
  const from = path.join(src, f);
  if (fs.existsSync(from)) {
    fs.copyFileSync(from, path.join(dst, f));
    const kb = Math.round(fs.statSync(from).size / 1024);
    console.log(`[vane] 同步 ${f}（${kb} KB）`);
  }
}
