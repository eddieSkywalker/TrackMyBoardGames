import { copyFile, mkdir } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendDirectory = fileURLToPath(new URL('../', import.meta.url))
const licenseFiles = [
  ['echarts', 'LICENSE'],
  ['echarts', 'NOTICE'],
  ['zrender', 'LICENSE'],
  ['tslib', 'LICENSE.txt'],
  ['tslib', 'CopyrightNotice.txt'],
]

for (const [packageName, fileName] of licenseFiles) {
  const source = join(frontendDirectory, 'node_modules', packageName, fileName)
  const destination = join(frontendDirectory, 'dist', 'licenses', packageName, fileName)
  await mkdir(dirname(destination), { recursive: true })
  await copyFile(source, destination)
}

console.log('Copied ECharts and runtime dependency license notices into dist/licenses.')
