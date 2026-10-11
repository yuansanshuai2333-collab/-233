// Translation key audit.
//
// Cross-checks every key referenced in code against the language files. Run it before a release:
// a missing key shows its raw name on screen, which is how the panel_mode bug was noticed.
//
// Vanilla keys (gui.done, gui.cancel, ...) are supplied by Minecraft and are skipped, as are keys
// that are built by concatenating a field name onto a prefix.
import fs from 'node:fs';

const SRC = 'I:/DSH/work/noncreatureaffinity/src/main/java';
const LANG = 'I:/DSH/work/noncreatureaffinity/src/main/resources/assets/noncreatureaffinity/lang';

/** Keys Minecraft itself provides, so they must not be required from this mod's files. */
const VANILLA_KEYS = new Set([
  'gui.done', 'gui.cancel', 'gui.back', 'gui.toTitle', 'gui.yes', 'gui.no', 'gui.ok',
]);

const files = [];
(function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = `${dir}/${e.name}`;
    if (e.isDirectory()) walk(p);
    else if (e.name.endsWith('.java')) files.push(p);
  }
})(SRC);

const used = new Map();
for (const file of files) {
  const text = fs.readFileSync(file, 'utf8');
  const short = file.split(/[\\/]/).pop();
  const lineOf = index => text.slice(0, index).split('\n').length;

  // Prefix constants such as: private static final String KEY = "message.noncreatureaffinity.";
  const prefixes = [];
  const constRe = /private static final String \w*KEY\w*\s*=\s*"([^"]+)"/g;
  let m;
  while ((m = constRe.exec(text)) !== null) {
    prefixes.push(m[1]);
  }

  const litRe = /translatable\(\s*"([^"]+)"/g;
  while ((m = litRe.exec(text)) !== null) {
    const key = m[1];
    // A trailing dot means this is a prefix, not a whole key.
    if (!key.includes('.') || key.endsWith('.')) continue;
    if (!used.has(key)) used.set(key, []);
    used.get(key).push(`${short}:${lineOf(m.index)}`);
  }

  for (const prefix of prefixes) {
    const re = /translatable\(\s*\w+\s*\+\s*"([^"]+)"/g;
    while ((m = re.exec(text)) !== null) {
      const key = prefix + m[1];
      if (key.endsWith('.')) continue; // A dynamic prefix is not itself a translation key.
      if (!used.has(key)) used.set(key, []);
      used.get(key).push(`${short}:${lineOf(m.index)}`);
    }
  }
}

// The failure suffix is selected from the enum at runtime; audit every actual suffix.
const feedbackSource = fs.readFileSync(`${SRC}/com/nonid/affinity/service/InteractionStartFeedback.java`, 'utf8');
const reasons = feedbackSource.match(/enum\s+Reason\s*\{([^}]+)\}/);
if (!reasons) throw new Error('Interaction failure reasons could not be read');
for (const reason of reasons[1].split(',').map(value => value.trim()).filter(Boolean)) {
  used.set(`message.noncreatureaffinity.interaction_failure.${reason.toLowerCase()}`,
    ['InteractionStartFeedback.Reason']);
}

const zh = JSON.parse(fs.readFileSync(`${LANG}/zh_cn.json`, 'utf8'));
const en = JSON.parse(fs.readFileSync(`${LANG}/en_us.json`, 'utf8'));

// ---------------------------------------------------------------------------
// Config-screen options.
//
// The config screen builds its keys by CONCATENATING a field name onto a prefix:
//     Text.translatable("config.noncreatureaffinity.option." + name)
// The literal scan above cannot see those, so previously this audit could pass while an option shown
// to players had no translation at all. The list of shown options is therefore read from the screen's
// own VISIBLE array instead of being duplicated here, so an option added there is audited automatically.
// ---------------------------------------------------------------------------
const OPTION_PREFIX = 'config.noncreatureaffinity.option.';
const SCREEN = `${SRC}/com/nonid/affinity/client/AffinityConfigScreen.java`;
let optionKeysChecked = 0;
const optionProblems = [];
try {
  const screenText = fs.readFileSync(SCREEN, 'utf8');
  const listMatch = screenText.match(/VISIBLE\s*=\s*Arrays\.asList\(([\s\S]*?)\);/);
  if (!listMatch) throw new Error('VISIBLE not found in the config screen');
  const names = [...listMatch[1].matchAll(/"([A-Za-z0-9_]+)"/g)].map(m => m[1]);
  if (names.length === 0) throw new Error('VISIBLE parsed as empty');
  for (const name of names) {
    for (const suffix of ['', '.desc']) {
      const key = OPTION_PREFIX + name + suffix;
      optionKeysChecked++;
      const inZh = Object.prototype.hasOwnProperty.call(zh, key);
      const inEn = Object.prototype.hasOwnProperty.call(en, key);
      if (!inZh || !inEn) {
        optionProblems.push({ key, inZh, inEn });
      }
    }
  }
  console.log(`配置界面选项: ${names.length} 项 / ${optionKeysChecked} 键   缺失: ${optionProblems.length}`);
  for (const p of optionProblems) {
    console.log(`  MISSING ${p.key}  zh=${p.inZh} en=${p.inEn}`);
  }
} catch (error) {
  // Never silently skip: a skipped audit is how a missing option translation reaches a player.
  optionProblems.push({ key: `(audit failed: ${error.message})`, inZh: false, inEn: false });
  console.log(`配置界面选项审计失败: ${error.message}`);
}

const isPlayerFacing = key => /^(message|config|item|screen|key|tier|gui)\./.test(key);

// Keys built by concatenating a literal prefix and suffix, e.g.
//     return "tier.noncreatureaffinity." + word;
// The literal-only scan above cannot see these, which is how the tier words stayed missing.
const TIER_WORDS = ['new', 'stranger', 'familiar', 'friendly', 'intimate', 'full'];
for (const prefix of ['tier.noncreatureaffinity.']) {
  for (const word of TIER_WORDS) {
    const key = prefix + word;
    if (!used.has(key)) used.set(key, ['(built dynamically)']);
  }
}

const problems = [];
let checked = 0;
for (const [key, where] of [...used.entries()].sort()) {
  if (!isPlayerFacing(key) || VANILLA_KEYS.has(key)) continue;
  checked++;
  const inZh = Object.prototype.hasOwnProperty.call(zh, key);
  const inEn = Object.prototype.hasOwnProperty.call(en, key);
  if (!inZh || !inEn) {
    problems.push({ key, where: where[0], inZh, inEn });
  }
}

console.log(`玩家可见键: ${checked}   缺失: ${problems.length}`);
for (const p of problems) {
  console.log(`  MISSING ${p.key}  (${p.where})  zh=${p.inZh} en=${p.inEn}`);
}

let mismatched = 0;
for (const key of Object.keys(zh)) {
  if (!Object.prototype.hasOwnProperty.call(en, key)) continue;
  const z = (String(zh[key]).match(/%s/g) || []).length;
  const e = (String(en[key]).match(/%s/g) || []).length;
  if (z !== e) {
    mismatched++;
    console.log(`  PLACEHOLDER ${key}: zh ${z} vs en ${e}`);
  }
}
console.log(`占位符不一致: ${mismatched}`);

if (problems.length > 0 || mismatched > 0 || optionProblems.length > 0) {
  process.exit(1);
}
console.log('语言文件校验通过');
