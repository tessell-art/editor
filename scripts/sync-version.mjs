#!/usr/bin/env node
// Propagates `package.json#version` to the other monorepo manifests:
//   - build.sbt        (Scala/sbt)
//   - desktop/src-tauri/tauri.conf.json
//   - desktop/src-tauri/Cargo.toml
//
// Hooked into the `npm version` lifecycle (see package.json#scripts.version), so
//   npm version 0.4.0 --no-git-tag-version
// bumps everything in one shot. Also runnable standalone:
//   npm run version:sync   # after manually editing package.json#version
//
// The Rust lockfile (`Cargo.lock`) is refreshed by `cargo update -p
// tessella-editor-desktop --offline` if cargo is on PATH; otherwise the next
// `cargo build` will reconcile it.

import { readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const pkg = JSON.parse(readFileSync(resolve(root, 'package.json'), 'utf-8'));
const newVersion = pkg.version;

if (!/^\d+\.\d+\.\d+(-[\w.]+)?$/.test(newVersion)) {
    console.error(`sync-version: refusing to propagate non-SemVer string '${newVersion}'`);
    process.exit(1);
}

// Android versionCode: major*10000 + minor*100 + patch (monotonic). Must stay
// in lockstep with the literal in android/app/build.gradle.
const [maj, min, pat] = newVersion.split('-')[0].split('.').map(Number);
const androidVersionCode = maj * 10000 + min * 100 + pat;

// Each target uses an anchored regex so we only touch the manifest's own version,
// not nested dependency `version = "..."` lines (relevant for Cargo.toml).
// `replace` defaults to substituting the quoted SemVer; targets that need a
// different value (e.g. Android's integer versionCode) provide their own.
const targets = [
    {
        path: 'build.sbt',
        pattern: /^(\s*version\s*:=\s*)"[^"]+"/m
    },
    {
        path: 'desktop/src-tauri/tauri.conf.json',
        pattern: /^(\s*"version"\s*:\s*)"[^"]+"/m
    },
    {
        path: 'desktop/src-tauri/Cargo.toml',
        pattern: /^(version\s*=\s*)"[^"]+"/m
    },
    {
        path: 'android/app/build.gradle',
        pattern: /^(\s*versionName\s+)"[^"]+"/m
    },
    {
        path: 'android/app/build.gradle',
        pattern: /^(\s*versionCode\s+)\d+/m,
        replace: (_, prefix) => `${prefix}${androidVersionCode}`,
        display: androidVersionCode
    }
];

for (const { path, pattern, replace, display } of targets) {
    const file = resolve(root, path);
    const before = readFileSync(file, 'utf-8');
    const after = before.replace(pattern, replace ?? ((_, prefix) => `${prefix}"${newVersion}"`));
    if (before === after) {
        console.error(`sync-version: no version line matched in ${path} (regex needs updating)`);
        process.exit(1);
    }
    writeFileSync(file, after);
    console.log(`sync-version: ${path} -> ${display ?? newVersion}`);
}

// Refresh Cargo.lock so the desktop crate's locked version matches Cargo.toml.
// Offline + targeted to avoid a network hit and unrelated dep bumps.
const cargo = spawnSync(
    'cargo',
    ['update', '-p', 'tessella-editor-desktop', '--offline'],
    { cwd: resolve(root, 'desktop/src-tauri'), stdio: 'inherit' }
);
if (cargo.status === 0) {
    console.log('sync-version: Cargo.lock refreshed');
} else if (cargo.error?.code === 'ENOENT') {
    console.warn('sync-version: cargo not on PATH; Cargo.lock will reconcile on next `cargo build`');
} else {
    console.warn('sync-version: `cargo update` failed; refresh Cargo.lock manually before tagging');
}
