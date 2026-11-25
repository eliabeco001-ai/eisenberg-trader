// Copia a interface única do projeto (pwa/index.html) para desktop/app/index.html.
const fs = require('fs');
const path = require('path');
const src = path.join(__dirname, '..', '..', 'pwa', 'index.html');
const dstDir = path.join(__dirname, '..', 'app');
if (!fs.existsSync(src)) {
  console.error('ERRO: não encontrei ' + src + ' (pasta pwa/ deve existir na raiz do repositório).');
  process.exit(1);
}
fs.mkdirSync(dstDir, { recursive: true });
fs.copyFileSync(src, path.join(dstDir, 'index.html'));
console.log('Interface copiada para desktop/app/index.html');
