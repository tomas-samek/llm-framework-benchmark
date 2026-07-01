// Count per-build "compile attempts" from subagent transcripts.
//
// An attempt = one invocation of a Maven build goal (package/compile/install/
// verify) by the contestant. We report:
//   compile_attempts      = invocations up to AND INCLUDING the first BUILD SUCCESS
//   total_build_runs      = all build invocations in the transcript
//   reached_build_success = whether any build invocation reported BUILD SUCCESS
//
// "Attempt" is a deliberately coarse, cadence-dependent proxy (an agent that
// compiles after every edit logs more than one that batches edits). Report the
// MEDIAN across trials and always pair it with tokens; it is meaningful as a
// relative compile-vs-run signal, not an absolute.
//
// Usage:  node extract-attempts.js <subagents-dir> [<subagents-dir> ...]
// Output: CSV  model,framework,trial,compile_attempts,total_build_runs,reached_build_success
const fs = require('fs');
const path = require('path');

const BUILD_RE = /\bmvn(?:\.cmd|w)?\b[^\n]*\b(?:package|install|verify|(?<!test-)compile)\b/i;
const RUNGOAL_RE = /\bexec:java\b/i;          // a run, not a compile — excluded
const META_RE = /archetype:generate|dependency:tree|help:|-version\b/i;

function classify(promptText) {
  const m = promptText.match(/runs[\\/]stage-1[\\/]([A-Za-z0-9-]+)[\\/]trial-([A-Za-z0-9-]+)/);
  if (!m) return null;
  const framework = m[1];
  const trialId = m[2];                        // e.g. s-03, o-05, 04
  let model = 'claude-opus-4-8';
  if (/^s5-/.test(trialId)) model = 'claude-sonnet-5';
  else if (/^s-/.test(trialId)) model = 'claude-sonnet-4-6';
  else if (/^o-/.test(trialId)) model = 'claude-opus-4-8';
  else if (/^f5-/.test(trialId)) model = 'claude-fable-5';
  const trial = trialId.replace(/^[A-Za-z0-9]+-/, '');   // strip s-/o-/f5- prefix
  return { framework, model, trial };
}

function processFile(file) {
  let data;
  try { data = fs.readFileSync(file, 'utf8'); } catch { return null; }
  const lines = data.split('\n').filter(l => l.trim());

  // First user/text content carries the dispatch prompt (with the trial path).
  let prompt = '';
  const buildCmds = {};   // tool_use_id -> command (build invocations only)
  const order = [];       // ordered list of build tool_use_ids as they were issued
  const success = {};     // tool_use_id -> bool (BUILD SUCCESS seen in its result)

  for (const line of lines) {
    let o; try { o = JSON.parse(line); } catch { continue; }
    const msg = o && o.message;
    const content = msg && msg.content;
    if (!Array.isArray(content)) {
      if (!prompt && typeof content === 'string') prompt += content;
      continue;
    }
    for (const c of content) {
      if (c.type === 'text' && !prompt) prompt += c.text || '';
      if (c.type === 'tool_use') {
        const cmd = (c.input && (c.input.command || '')) || '';
        if (!prompt && cmd === '' && c.input && c.input.prompt) prompt += c.input.prompt;
        if (BUILD_RE.test(cmd) && !RUNGOAL_RE.test(cmd) && !META_RE.test(cmd)) {
          buildCmds[c.id] = cmd; order.push(c.id);
        }
      } else if (c.type === 'tool_result') {
        const id = c.tool_use_id;
        if (!(id in buildCmds)) continue;
        const txt = Array.isArray(c.content) ? c.content.map(x => x.text || '').join('')
                  : (typeof c.content === 'string' ? c.content : '');
        success[id] = /BUILD SUCCESS/.test(txt);
      }
    }
  }
  // Fallback: the whole file text usually contains the prompt path even if the
  // first-message heuristic missed it.
  if (!/runs[\\/]stage-1/.test(prompt)) prompt = data;

  const k = classify(prompt);
  if (!k) return null;
  if (!order.length) return { ...k, compile_attempts: 0, total_build_runs: 0, reached_build_success: false };

  let firstWin = -1;
  for (let i = 0; i < order.length; i++) { if (success[order[i]]) { firstWin = i; break; } }
  const reached = firstWin >= 0;
  const compile_attempts = reached ? firstWin + 1 : order.length;
  return { ...k, compile_attempts, total_build_runs: order.length, reached_build_success: reached };
}

const dirs = process.argv.slice(2);
const rows = [];
for (const d of dirs) {
  let files; try { files = fs.readdirSync(d); } catch { continue; }
  for (const f of files) {
    if (!f.endsWith('.jsonl')) continue;
    const r = processFile(path.join(d, f));
    if (r) rows.push(r);
  }
}
rows.sort((a, b) => (a.framework + a.model + a.trial).localeCompare(b.framework + b.model + b.trial));
console.log('model,framework,trial,compile_attempts,total_build_runs,reached_build_success');
for (const r of rows) {
  console.log([r.model, r.framework, r.trial, r.compile_attempts, r.total_build_runs, r.reached_build_success].join(','));
}
