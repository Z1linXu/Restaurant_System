#!/usr/bin/env node

import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import {
  cp,
  mkdir,
  readFile,
  readdir,
  rm,
  stat,
  writeFile,
} from 'node:fs/promises'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const padRoot = resolve(scriptDirectory, '..')
const repositoryRoot = resolve(padRoot, '..')
const frontendDist = resolve(repositoryRoot, 'frontend/dist')
const androidAssets = resolve(padRoot, 'android/app/src/main/assets/web')
const offlineDatabaseSource = resolve(repositoryRoot, 'frontend/src/offline/offlineDatabase.ts')
const manifestPath = resolve(androidAssets, 'asset-manifest.json')
const buildInfoPath = resolve(androidAssets, 'build-info.json')
const metadataFiles = new Set(['asset-manifest.json', 'build-info.json'])

function fail(message) {
  throw new Error(`[bundled-assets] ${message}`)
}

function sha256(content) {
  return createHash('sha256').update(content).digest('hex')
}

async function exists(path) {
  try {
    await stat(path)
    return true
  } catch {
    return false
  }
}

async function listFiles(root, directory = root) {
  const entries = await readdir(directory, { withFileTypes: true })
  const files = []
  for (const entry of entries) {
    const absolutePath = resolve(directory, entry.name)
    if (entry.isDirectory()) {
      files.push(...await listFiles(root, absolutePath))
    } else if (entry.isFile()) {
      files.push(relative(root, absolutePath).replaceAll('\\', '/'))
    }
  }
  return files.sort()
}

async function readOfflineDatabaseVersion() {
  const source = await readFile(offlineDatabaseSource, 'utf8')
  const match = source.match(/OFFLINE_DATABASE_VERSION\s*=\s*(\d+)/)
  if (!match) {
    fail('unable to read OFFLINE_DATABASE_VERSION')
  }
  return Number(match[1])
}

function localAssetPath(rawPath) {
  if (/^[a-z]+:/i.test(rawPath) || rawPath.startsWith('//')) {
    return null
  }
  return rawPath.split(/[?#]/, 1)[0].replace(/^\.\//, '').replace(/^\//, '')
}

async function readEntrypoints(root) {
  const indexPath = resolve(root, 'index.html')
  if (!await exists(indexPath)) {
    fail('index.html is missing')
  }
  const indexHtml = await readFile(indexPath, 'utf8')
  const scripts = [...indexHtml.matchAll(/<script\b[^>]*\bsrc=["']([^"']+)["'][^>]*>/gi)]
    .map((match) => localAssetPath(match[1]))
    .filter((path) => path?.endsWith('.js'))
  const styles = [...indexHtml.matchAll(/<link\b[^>]*\bhref=["']([^"']+)["'][^>]*>/gi)]
    .map((match) => localAssetPath(match[1]))
    .filter((path) => path?.endsWith('.css'))

  if (scripts.length === 0) {
    fail('index.html does not reference a local JavaScript entrypoint')
  }
  if (styles.length === 0) {
    fail('index.html does not reference a local CSS entrypoint')
  }
  for (const entrypoint of [...scripts, ...styles]) {
    if (!entrypoint || !await exists(resolve(root, entrypoint))) {
      fail(`index.html references missing entrypoint: ${entrypoint}`)
    }
  }
  return { scripts, styles }
}

function defaultBuildVersion() {
  let revision = 'unknown'
  try {
    revision = execFileSync('git', ['-C', repositoryRoot, 'rev-parse', '--short', 'HEAD'], { encoding: 'utf8' }).trim()
  } catch {
    // A source archive may not include Git metadata.
  }
  return `bundled-${revision}-${new Date().toISOString().replace(/[-:.]/g, '').replace('Z', 'Z')}`
}

async function createManifest(buildVersion) {
  const entrypoints = await readEntrypoints(androidAssets)
  const offlineDatabaseSchemaVersion = await readOfflineDatabaseVersion()
  const files = (await listFiles(androidAssets)).filter((path) => !metadataFiles.has(path))
  const assets = []
  for (const path of files) {
    const content = await readFile(resolve(androidAssets, path))
    assets.push({ path, bytes: content.byteLength, sha256: sha256(content) })
  }
  const manifest = {
    manifestVersion: 1,
    buildVersion,
    generatedAt: new Date().toISOString(),
    offlineDatabaseSchemaVersion,
    entrypoints,
    assets,
  }
  const manifestContent = `${JSON.stringify(manifest, null, 2)}\n`
  await writeFile(manifestPath, manifestContent, 'utf8')
  const buildInfo = {
    buildVersion,
    generatedAt: manifest.generatedAt,
    offlineDatabaseSchemaVersion,
    assetManifestSha256: sha256(manifestContent),
  }
  await writeFile(buildInfoPath, `${JSON.stringify(buildInfo, null, 2)}\n`, 'utf8')
}

async function verifyAssets() {
  if (!await exists(manifestPath) || !await exists(buildInfoPath)) {
    fail('asset-manifest.json or build-info.json is missing; run copy-frontend-dist.sh')
  }
  const manifestContent = await readFile(manifestPath, 'utf8')
  const manifest = JSON.parse(manifestContent)
  const buildInfo = JSON.parse(await readFile(buildInfoPath, 'utf8'))
  const offlineDatabaseSchemaVersion = await readOfflineDatabaseVersion()

  if (manifest.manifestVersion !== 1) {
    fail(`unsupported manifest version: ${manifest.manifestVersion}`)
  }
  if (!manifest.buildVersion || manifest.buildVersion !== buildInfo.buildVersion) {
    fail('build version differs between manifest and build info')
  }
  if (manifest.offlineDatabaseSchemaVersion !== offlineDatabaseSchemaVersion
    || buildInfo.offlineDatabaseSchemaVersion !== offlineDatabaseSchemaVersion) {
    fail(`offline schema mismatch; frontend=${offlineDatabaseSchemaVersion}`)
  }
  if (sha256(manifestContent) !== buildInfo.assetManifestSha256) {
    fail('asset manifest hash does not match build-info.json')
  }

  await readEntrypoints(androidAssets)
  const actualFiles = (await listFiles(androidAssets)).filter((path) => !metadataFiles.has(path))
  const expectedFiles = manifest.assets.map((asset) => asset.path).sort()
  if (JSON.stringify(actualFiles) !== JSON.stringify(expectedFiles)) {
    fail('bundled assets contain missing or untracked files')
  }

  for (const asset of manifest.assets) {
    const content = await readFile(resolve(androidAssets, asset.path))
    if (content.byteLength !== asset.bytes || sha256(content) !== asset.sha256) {
      fail(`asset hash mismatch: ${asset.path}`)
    }
  }

  console.log(`[bundled-assets] verified ${manifest.assets.length} files for ${manifest.buildVersion}`)
}

async function syncAssets() {
  if (!await exists(frontendDist)) {
    fail('frontend/dist not found; run the frontend production build first')
  }
  await readEntrypoints(frontendDist)
  await rm(androidAssets, { recursive: true, force: true })
  await mkdir(androidAssets, { recursive: true })
  await cp(frontendDist, androidAssets, { recursive: true })
  const buildVersion = process.env.BUNDLED_BUILD_VERSION
    || process.env.VITE_APP_BUILD_VERSION
    || defaultBuildVersion()
  await createManifest(buildVersion)
  await verifyAssets()
  console.log(`[bundled-assets] synchronized ${frontendDist} -> ${androidAssets}`)
}

const command = process.argv[2]
if (command === 'sync') {
  await syncAssets()
} else if (command === 'verify') {
  await verifyAssets()
} else {
  fail('usage: sync-bundled-assets.mjs <sync|verify>')
}
