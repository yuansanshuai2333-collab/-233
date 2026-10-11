/**
 * Minimal, dependency-free ZIP reader shared by the release tooling.
 *
 * <p>It exists because two tools need the same thing -- "list a jar's entries, and read one of them" -- and a
 * second hand-written parser would be a second place for the release gate's fail-closed guarantees to rot.
 * Every structural problem throws {@link ZipReadError}; nothing is returned as a partial answer. A caller that
 * wants "incomplete directory" to be a FAILURE (the release gate) simply does not catch the error as anything
 * else, and a caller that wants to compare two archives reports it as a failure too.
 */

import { inflateRawSync } from 'node:zlib';
import { basename } from 'node:path';

const EOCD_SIGNATURE = 0x06054b50;
const CENTRAL_SIGNATURE = 0x02014b50;
const LOCAL_SIGNATURE = 0x04034b50;
const ZIP64_MARKER = 0xffffffff;

export class ZipReadError extends Error {}

/** The end-of-central-directory record, located by scanning back for one whose comment length reaches EOF. */
function findEndOfCentralDirectory(buffer) {
  const earliest = Math.max(0, buffer.length - (0xffff + 22));
  for (let offset = buffer.length - 22; offset >= earliest; offset--) {
    if (buffer.readUInt32LE(offset) !== EOCD_SIGNATURE) {
      continue;
    }
    const commentLength = buffer.readUInt16LE(offset + 20);
    if (offset + 22 + commentLength === buffer.length) {
      return offset;
    }
  }
  return -1;
}

/**
 * Every entry in the archive, in central-directory order.
 *
 * @param {Buffer} buffer the whole archive
 * @returns {Array<{name: string, method: number, crc32: number, compressedSize: number,
 *   uncompressedSize: number, localHeaderOffset: number}>}
 * @throws {ZipReadError} when the directory cannot be walked in full
 */
export function readArchive(buffer) {
  const eocd = findEndOfCentralDirectory(buffer);
  if (eocd < 0) {
    throw new ZipReadError('no consistent end-of-central-directory record: not a readable ZIP archive');
  }
  const diskNumber = buffer.readUInt16LE(eocd + 4);
  const centralDisk = buffer.readUInt16LE(eocd + 6);
  const entryCount = buffer.readUInt16LE(eocd + 10);
  const centralSize = buffer.readUInt32LE(eocd + 12);
  const centralOffset = buffer.readUInt32LE(eocd + 16);
  if (diskNumber !== 0 || centralDisk !== 0) {
    throw new ZipReadError('the archive spans multiple disks');
  }
  if (entryCount === 0xffff || centralOffset === ZIP64_MARKER || centralSize === ZIP64_MARKER) {
    throw new ZipReadError('the archive uses ZIP64 fields');
  }
  if (entryCount === 0) {
    throw new ZipReadError('the archive declares zero entries');
  }
  if (centralOffset + centralSize > buffer.length) {
    throw new ZipReadError(`the central directory (${centralOffset}+${centralSize}) runs past the end of the `
      + `file (${buffer.length} bytes): the archive is truncated`);
  }

  const entries = [];
  let offset = centralOffset;
  for (let i = 0; i < entryCount; i++) {
    if (offset + 46 > buffer.length) {
      throw new ZipReadError(`central directory entry ${i + 1} of ${entryCount} starts past the end of the file`);
    }
    if (buffer.readUInt32LE(offset) !== CENTRAL_SIGNATURE) {
      throw new ZipReadError(`central directory entry ${i + 1} of ${entryCount} has no entry signature at `
        + `${offset}: the directory is corrupt or the declared entry count is wrong`);
    }
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    if (offset + 46 + nameLength + extraLength + commentLength > buffer.length) {
      throw new ZipReadError(`central directory entry ${i + 1} of ${entryCount} runs past the end of the file`);
    }
    const name = buffer.toString('utf8', offset + 46, offset + 46 + nameLength);
    if (name.length === 0) {
      throw new ZipReadError(`central directory entry ${i + 1} of ${entryCount} has an empty name`);
    }
    entries.push({
      name,
      method: buffer.readUInt16LE(offset + 10),
      crc32: buffer.readUInt32LE(offset + 16),
      compressedSize: buffer.readUInt32LE(offset + 20),
      uncompressedSize: buffer.readUInt32LE(offset + 24),
      localHeaderOffset: buffer.readUInt32LE(offset + 42),
    });
    offset += 46 + nameLength + extraLength + commentLength;
  }
  if (entries.length !== entryCount) {
    throw new ZipReadError(`walked ${entries.length} of ${entryCount} declared central directory entries`);
  }
  if (offset !== centralOffset + centralSize) {
    throw new ZipReadError(`the central directory walk ended at ${offset}, not at its declared end `
      + `${centralOffset + centralSize}: the directory is inconsistent`);
  }
  return entries;
}

/**
 * The decompressed bytes of one entry.
 *
 * @throws {ZipReadError} when the local header, the data or the compression method cannot be used
 */
export function readEntry(buffer, entry) {
  const offset = entry.localHeaderOffset;
  if (offset + 30 > buffer.length || buffer.readUInt32LE(offset) !== LOCAL_SIGNATURE) {
    throw new ZipReadError(`entry ${entry.name}: no local file header at ${offset}`);
  }
  const nameLength = buffer.readUInt16LE(offset + 26);
  const extraLength = buffer.readUInt16LE(offset + 28);
  const start = offset + 30 + nameLength + extraLength;
  const end = start + entry.compressedSize;
  if (end > buffer.length) {
    throw new ZipReadError(`entry ${entry.name}: compressed data runs past the end of the file`);
  }
  const raw = buffer.subarray(start, end);
  if (entry.method === 0) {
    return raw;
  }
  if (entry.method !== 8) {
    throw new ZipReadError(`entry ${entry.name}: unsupported compression method ${entry.method}`);
  }
  try {
    return inflateRawSync(raw);
  } catch (error) {
    throw new ZipReadError(`entry ${entry.name}: cannot decompress: ${error.message}`);
  }
}

/** `<name>-<version>.jar` -> `<version>`, or null when the name does not have that shape. */
export function versionFromFileName(filePath) {
  const base = basename(filePath).replace(/\.jar$/, '');
  const dash = base.indexOf('-');
  if (dash < 0 || dash === base.length - 1) {
    return null;
  }
  return base.slice(dash + 1);
}

/** The `version` string declared by the archive's `fabric.mod.json`, or null when it has none. */
export function manifestVersion(buffer, entries) {
  const entry = entries.find((candidate) => candidate.name === 'fabric.mod.json');
  if (entry === undefined) {
    return null;
  }
  let manifest;
  try {
    manifest = JSON.parse(readEntry(buffer, entry).toString('utf8'));
  } catch (error) {
    if (error instanceof ZipReadError) {
      throw error;
    }
    throw new ZipReadError(`fabric.mod.json is not readable JSON: ${error.message}`);
  }
  return typeof manifest.version === 'string' ? manifest.version : null;
}
