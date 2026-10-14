import { existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
export const python = process.env.PYTHON ||
  (existsSync('.venv/Scripts/python.exe') ? '.venv/Scripts/python.exe' :
    existsSync('.venv/bin/python') ? '.venv/bin/python' : 'python');
export function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status || 1);
}
if (process.argv[2]) run(python, process.argv.slice(2));
