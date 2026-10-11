/**
 * Per-entry comparison of two delivered jars.
 *
 * <p><b>Why it exists.</b> A revision that claims "no gameplay code changed" is a claim about every class in the
 * jar, and reading a changelog is not evidence for it. When 1.1.1 was superseded, the older jar was no longer in
 * `build/libs` and the claim could not be checked class by class any more. This tool makes the comparison a command
 * instead of an assertion.
 *
 * <p><b>What "identical" means here.</b> Entries are compared by the SHA-256 of their <em>decompressed</em>
 * contents, so a match is byte identity of the actual class file. An earlier version compared the CRC-32 recorded
 * in the central directory, which is a 32-bit checksum: a CRC match does not strictly prove the bytes are equal
 * (and a malicious or unlucky archive can collide). The wording said "byte-identical", which the check did not
 * support -- both the check and the wording are now SHA-256.
 *
 * <p>Usage:
 *
 * <pre>node tools/jar-diff.mjs &lt;old.jar&gt; &lt;new.jar&gt; [--expect-identical-classes]</pre>
 *
 * <p>Removed, added and changed entries are listed, and the `.class` subset is summarised separately -- that
 * summary is the answer to "is the gameplay code unchanged?". Without a flag the exit code is 1 when the two
 * archives differ at all. With `--expect-identical-classes` the claim itself is the assertion: every class must
 * have identical content, and the expected non-class difference (the version string in `fabric.mod.json`) is
 * reported without failing.
 */

import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { basename } from 'node:path';
import { readArchive, readEntry } from './lib/zip-read.mjs';

const args = process.argv.slice(2);
const expectIdenticalClasses = args.includes('--expect-identical-classes');
const paths = args.filter((argument) => !argument.startsWith('--'));
if (paths.length !== 2) {
  console.error('usage: node tools/jar-diff.mjs <old.jar> <new.jar> [--expect-identical-classes]');
  process.exit(1);
}

const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');

function load(path) {
  const buffer = readFileSync(path);
  const entries = new Map();
  for (const entry of readArchive(buffer)) {
    entries.set(entry.name, {
      hash: sha256(readEntry(buffer, entry)),
      size: entry.uncompressedSize,
    });
  }
  return entries;
}

let before;
let after;
try {
  before = load(paths[0]);
  after = load(paths[1]);
} catch (error) {
  console.error(`jar diff: cannot read an archive: ${error.message}`);
  process.exit(1);
}

const removed = [];
const added = [];
const changed = [];
for (const [name, entry] of before) {
  const other = after.get(name);
  if (other === undefined) {
    removed.push(name);
  } else if (other.hash !== entry.hash) {
    changed.push({ name, from: entry.hash, to: other.hash });
  }
}
for (const name of after.keys()) {
  if (!before.has(name)) {
    added.push(name);
  }
}

const isClass = (name) => name.endsWith('.class');
const count = (list, predicate) => list.filter(predicate).length;

console.log(`jar diff: ${basename(paths[0])} -> ${basename(paths[1])}`);
console.log('  comparison: SHA-256 of decompressed entry content (byte identity, not CRC-32)');
console.log(`  entries: ${before.size} -> ${after.size}`);
console.log(`  classes: ${count([...before.keys()], isClass)} -> ${count([...after.keys()], isClass)}`);
console.log(`  removed: ${removed.length} (${count(removed, isClass)} class)`);
for (const name of removed) console.log(`    - ${name}`);
console.log(`  added  : ${added.length} (${count(added, isClass)} class)`);
for (const name of added) console.log(`    + ${name}`);
console.log(`  changed: ${changed.length} (${count(changed, (entry) => isClass(entry.name))} class)`);
for (const entry of changed) console.log(`    ~ ${entry.name}  ${entry.from.slice(0, 12)} -> ${entry.to.slice(0, 12)}`);

const classChanges = count(removed, isClass) + count(added, isClass)
  + count(changed, (entry) => isClass(entry.name));
const differs = removed.length + added.length + changed.length > 0;
if (classChanges === 0) {
  console.log(differs
    ? '  every class has identical SHA-256 content; only non-class entries differ'
    : '  the two archives are identical entry for entry');
} else {
  console.log(`  ${classChanges} class file(s) differ`);
}

if (expectIdenticalClasses) {
  if (classChanges > 0) {
    console.error(`  FAIL: --expect-identical-classes was given, but ${classChanges} class file(s) differ`);
    process.exit(1);
  }
  // Non-class differences (the version string in fabric.mod.json) are expected in a rebuild, so they are
  // reported above and do not fail this mode.
  console.log('  OK: every class has identical content, which is what --expect-identical-classes asserts');
  process.exit(0);
}
process.exit(differs ? 1 : 0);
