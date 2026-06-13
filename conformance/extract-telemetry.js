// Extract per-contestant build telemetry (output tokens, tool calls, duration)
// from Claude Code session transcript(s) and map each to (model, framework, trial).
//
// Usage:  node extract-telemetry.js <session1.jsonl> [<session2.jsonl> ...]
// Output: CSV on stdout: model,framework,trial,dispatches,output_tokens,tool_uses,duration_ms
//
// It joins tool_use (the Agent dispatch: carries input.model + input.description)
// to its tool_result (carries the "<usage>subagent_tokens: N ...</usage>" block)
// via tool_use_id. Only contestant-build dispatches are counted; graders, reviewers,
// and infra agents are excluded. Trials built by more than one dispatch (e.g. a
// tiko-mcp build that was later finished by a second agent) are summed.
const fs = require('fs');
const files = process.argv.slice(2);
const uses = {};   // id -> {desc, model}
const usage = {};  // id -> {tokens, tool_uses, duration}

for (const f of files) {
  let data;
  try { data = fs.readFileSync(f, 'utf8'); } catch { continue; }
  for (const line of data.split('\n')) {
    if (!line.trim()) continue;
    let o; try { o = JSON.parse(line); } catch { continue; }
    const content = o && o.message && o.message.content;
    if (!Array.isArray(content)) continue;
    for (const c of content) {
      if (c.type === 'tool_use') {
        uses[c.id] = { desc: (c.input && c.input.description) || '', model: (c.input && c.input.model) || '' };
      } else if (c.type === 'tool_result') {
        const txt = Array.isArray(c.content) ? c.content.map(x => x.text || '').join('') : (typeof c.content === 'string' ? c.content : '');
        const m = txt.match(/subagent_tokens:\s*(\d+)/);
        if (m) {
          const tu = txt.match(/tool_uses:\s*(\d+)/);
          const du = txt.match(/duration_ms:\s*(\d+)/);
          usage[c.tool_use_id] = { tokens: +m[1], tool_uses: tu ? +tu[1] : 0, duration: du ? +du[1] : 0 };
        }
      }
    }
  }
}

function classify(desc, model) {
  const d = desc;
  if (!/contestant|reference|^Finish tiko-mcp/i.test(d)) return null;        // contestant builds only
  if (/Grade|review|jbang|oracle|harness|scaffold|smoke/i.test(d)) return null; // exclude infra
  let M = model === 'fable' ? 'claude-fable-5' : model === 'opus' ? 'claude-opus-4-8' : model === 'sonnet' ? 'claude-sonnet-4-6' : '';
  if (!M) M = /F5|f5-/.test(d) ? 'claude-fable-5' : /Opus|o8-/.test(d) ? 'claude-opus-4-8' : 'claude-sonnet-4-6';
  const fw = /tiko-mcp|Tiko-MCP|Tiko \(MCP\)|Finish tiko-mcp/i.test(d) ? 'tiko-mcp'
    : /spring3|Spring3|3\.3\.5/i.test(d) ? 'spring3'
    : /tiko/i.test(d) ? 'tiko'
    : /spring/i.test(d) ? 'spring' : '?';
  const mt = d.match(/(?:f5-|o8-|trial-)?(\d{2})\b/);
  const trial = mt ? mt[1] : '01';   // spring3 has one trial per model
  return { model: M, fw, trial };
}

const agg = {};
for (const id in usage) {
  const u = uses[id];
  if (!u) continue;
  const k = classify(u.desc, u.model);
  if (!k) continue;
  const key = `${k.model}|${k.fw}|${k.trial}`;
  if (!agg[key]) agg[key] = { tokens: 0, tool_uses: 0, duration: 0, n: 0 };
  agg[key].tokens += usage[id].tokens;
  agg[key].tool_uses += usage[id].tool_uses;
  agg[key].duration += usage[id].duration;
  agg[key].n += 1;
}

console.log('model,framework,trial,dispatches,output_tokens,tool_uses,duration_ms');
for (const key of Object.keys(agg).sort()) {
  const a = agg[key];
  const [m, f, t] = key.split('|');
  console.log([m, f, t, a.n, a.tokens, a.tool_uses, a.duration].join(','));
}
