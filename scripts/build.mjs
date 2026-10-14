import { existsSync } from 'node:fs';
import { python, run } from './python.mjs';
run(python, ['-m', 'compileall', '-q', 'backend']);
run(process.execPath, ['--check', 'web/dashboard.js']);
for (const file of ['web/index.html', 'web/styles.css', 'web/favicon.svg']) {
  if (!existsSync(file)) throw new Error(`Missing web asset: ${file}`);
}
console.log('BUILD PASS: Python backend compiled; dashboard JavaScript checked; web assets verified.');
