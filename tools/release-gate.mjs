/**
 * Release gate: the last check before a built jar is delivered.
 *
 * <p><b>Why this is a script and not a test.</b> An unfinished feature sitting in the source tree is compiled into
 * every development build, which is correct -- that is what makes its unit tests run. The rule "unfinished code
 * must not ship" belongs at the moment a release is cut, so it is a standalone check run against the jar that is
 * about to be delivered. The build side of the same rule lives in {@code tools/wip-sources.txt}, which both this
 * script and {@code build.gradle} read, so a hand-maintained list in two places cannot drift.
 *
 * <p><b>Fail-closed.</b> The gate passes only when every assertion below was actually evaluated and held. Anything
 * that prevents an assertion from being evaluated -- an unreadable jar, a truncated or inconsistent ZIP directory,
 * a missing {@code fabric.mod.json}, an unreadable or absent work-in-progress list -- is a FAILURE, never a skipped
 * check. Printing "MISSING" for a required entry and then reporting PASS is exactly the failure mode this replaces.
 *
 * <p>Usage, from the project root:
 *
 * <pre>node tools/release-gate.mjs build/libs/naturalrelationship-1.1.3+1.21.11.jar</pre>
 *
 * <p>Exit code 0 means every assertion held. Exit code 1 means at least one failed; the reasons are printed and the
 * jar must be rebuilt (or the delivery corrected) before it is handed over.
 */

import { readFileSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { manifestVersion, readArchive, versionFromFileName } from './lib/zip-read.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const WIP_LIST = join(HERE, 'wip-sources.txt');
const REQUIRED_ENTRIES = ['fabric.mod.json'];

/** Filename classifiers that are build by-products, never deliveries. */
const REJECTED_CLASSIFIERS = ['-sources.jar', '-dev.jar', '-sources-dev.jar'];

const jarPath = process.argv[2];
if (!jarPath) {
  console.error('usage: node tools/release-gate.mjs <jar>');
  process.exit(1);
}

/** Collected instead of exiting early, so one run reports every reason it failed. */
const failures = [];
const notes = [];
const fail = (message) => failures.push(message);
const note = (message) => notes.push(message);

// ---------------------------------------------------------------------------------------------
// 1. The work-in-progress list. An unreadable list means the central assertion cannot be made.
// ---------------------------------------------------------------------------------------------
function loadWorkInProgress() {
  let text;
  try {
    text = readFileSync(WIP_LIST, 'utf8');
  } catch (error) {
    fail(`cannot read the work-in-progress list ${WIP_LIST}: ${error.message}`);
    return null;
  }
  const paths = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith('#'));
  for (const path of paths) {
    if (!path.endsWith('.java') || path.startsWith('/') || path.includes('..')) {
      fail(`work-in-progress list entry is not a plain src/main/java path: ${path}`);
    }
  }
  if (paths.length === 0) {
    note('the work-in-progress list is empty: there is no unfinished code to guard in this release');
  }
  return paths.map((path) => path.slice(0, -'.java'.length));
}

// ---------------------------------------------------------------------------------------------
// 2. Read and walk the archive. Any structural problem is a failure, not a shorter entry list.
// ---------------------------------------------------------------------------------------------
const workInProgress = loadWorkInProgress();

let entries = null;
let manifestValue = null;
try {
  const buffer = readFileSync(jarPath);
  entries = readArchive(buffer);
  manifestValue = entries.some((entry) => entry.name === 'fabric.mod.json')
    ? manifestVersion(buffer, entries)
    : null;
} catch (error) {
  const reason = error instanceof Error ? error.message : String(error);
  fail(`cannot verify ${jarPath}: ${reason}`);
}

const fileName = basename(jarPath);
for (const classifier of REJECTED_CLASSIFIERS) {
  if (fileName.endsWith(classifier)) {
    fail(`${fileName} is a build by-product (${classifier}); deliver the remapped jar instead`);
  }
}
const fileVersion = versionFromFileName(jarPath);
if (fileVersion === null) {
  fail(`cannot derive a version from the file name ${fileName} (expected <name>-<version>.jar)`);
}

if (entries !== null) {
  for (const required of REQUIRED_ENTRIES) {
    if (!entries.some((entry) => entry.name === required)) {
      fail(`the jar has no ${required}`);
    }
  }
  if (manifestValue === null && entries.some((entry) => entry.name === 'fabric.mod.json')) {
    fail('fabric.mod.json declares no string "version"');
  }
  if (workInProgress !== null) {
    const offenders = entries
      .map((entry) => entry.name)
      .filter((name) => workInProgress.some((prefix) => name.startsWith(prefix)));
    if (offenders.length > 0) {
      const header = `the jar contains ${offenders.length} work-in-progress class(es):`;
      fail(header);
      // Keep the indented lines next to the header even when other failures were recorded too.
      const at = failures.indexOf(header);
      failures.splice(at + 1, 0, ...offenders.map((name) => `    - ${name}`));
    }
  }
}

if (manifestValue !== null && fileVersion !== null && manifestValue !== fileVersion) {
  fail(`version mismatch: the file name says ${fileVersion}, fabric.mod.json says ${manifestValue}`
    + ' (the jar was not rebuilt after the version bump, or was renamed by hand)');
}

// ---------------------------------------------------------------------------------------------
// Report.
// ---------------------------------------------------------------------------------------------
console.log(`release gate: ${jarPath}`);
console.log(`  file version    : ${fileVersion ?? '(underivable)'}`);
console.log(`  manifest version: ${manifestValue ?? '(unreadable)'}`);
console.log(`  entries         : ${entries === null ? '(unreadable)' : entries.length}`);
console.log(`  fabric.mod.json : ${entries !== null && entries.some((e) => e.name === 'fabric.mod.json')
  ? 'present' : 'MISSING'}`);
console.log(`  guarded prefixes: ${workInProgress === null ? '(unreadable)' : workInProgress.length}`);
for (const text of notes) {
  console.log(`  NOTE: ${text}`);
}
if (failures.length > 0) {
  console.error(`  FAIL (${failures.length} problem(s)):`);
  for (const text of failures) {
    console.error(`  - ${text}`);
  }
  console.error('  Fix the jar or the delivery before handing it over; do not treat this as a warning.');
  process.exit(1);
}
console.log('  PASS');
