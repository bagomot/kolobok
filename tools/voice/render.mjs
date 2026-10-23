// Renders Kolobok's 49 syllable samples, and the Head's snore and sneeze, to Ogg Vorbis and writes
// assets/kolobok/sounds.json.
//
//   cd tools/voice && npm install && npm run render
//
// The voice is the songbook prototype's synthesiser, run offline: a clean harmonic tone whose
// spectrum follows the vowel's formants, with the nasal resonance of a villager. No noise, no
// filters, no compressor, nothing that could rasp. A consonant is its own harmonic spectrum that
// glides into the vowel; «р» is one soft touch; sibilants are voiced, never hissed.
//
// Each sample is one syllable sung on C4 (261.63 Hz), about 0.6 s: the vowel holds and then fades
// gently, so a long note does not stop dead and a short one is softly covered by the next. The game
// pitches the sample for each note, which shifts the timbre with it: a bass voice comes out darker,
// the little one brighter.
//
// These are draft samples; a recorded voice can replace any file under the same name.

import { createOggEncoder } from "wasm-media-encoders";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const ASSETS = join(ROOT, "src", "main", "resources", "assets", "kolobok");
const OUT = join(ASSETS, "sounds", "voice");

const SR = 44100;
const F0 = 261.63;          // C4
const LENGTH = 0.6;         // seconds
const PEAK = 0.85;

// ---- the synthesiser's tables, copied from the prototype ----
const FORM = { а: [760, 1250, 2650], о: [520, 860, 2550], у: [340, 720, 2450], э: [500, 1750, 2600], и: [300, 2200, 2950] };
const BW = [110, 150, 220];
const CONS_F = { б: [250, 800, 2200], м: [250, 1100, 2400], л: [360, 1100, 2700], р: [420, 1300, 2500], д: [280, 1700, 2700], г: [280, 1500, 1900], с: [300, 1800, 2700], ш: [300, 1600, 2300], й: [280, 2200, 3000] };
const CONS_G = { б: [1, 0.3, 0.1], м: [1, 0.06, 0.02] };
// consonant: [closure (silence), sounding before the glide, glide into the vowel], seconds
const CONS_T = { б: [0.015, 0.015, 0.04], д: [0.015, 0.015, 0.035], г: [0.018, 0.018, 0.04], м: [0, 0.05, 0.03], л: [0, 0.035, 0.045], р: [0, 0.03, 0.04], с: [0, 0.02, 0.045], ш: [0, 0.02, 0.045], й: [0, 0, 0.07] };
const VOICE = { vib: 0.012, rate: 5.5 };   // the plain voice; the others are made in game by pitch and tempo

// ---- the 49 syllables, same order and names as ru.bagomot.kolobok.entity.song.Syllable ----
const ONSETS = [["б", "b"], ["м", "m"], ["л", "l"], ["р", "r"], ["д", "d"], ["г", "g"], ["с", "s"], ["ш", "sh"]];
const VOWELS = [["а", "a"], ["о", "o"], ["у", "u"], ["э", "e"], ["и", "i"]];
const SYLLABLES = [];
for (const [c, lc] of ONSETS) for (const [v, lv] of VOWELS) SYLLABLES.push({ on: c, v, name: lc + lv });
for (const [v, lv] of VOWELS.slice(0, 4)) SYLLABLES.push({ on: "й", v, name: "y" + lv });
for (const [v, lv] of VOWELS) SYLLABLES.push({ on: "", v, name: lv });

// Harmonic amplitudes of a formant spectrum, as harmWave() computes them.
function harmonics(f0, F, G) {
  const top = Math.max(1, Math.min(48, Math.floor(4200 / f0)));
  const amps = new Float64Array(top + 1);
  for (let h = 1; h <= top; h++) {
    const f = h * f0;
    let a = 0;
    for (let i = 0; i < 3; i++) a += G[i] / Math.sqrt(1 + ((f - F[i]) / (BW[i] / 2)) ** 2);
    a += 0.3 / Math.sqrt(1 + ((f - 270) / 90) ** 2);          // nasal resonance, as with villagers
    a *= 1 - 0.3 / Math.sqrt(1 + ((f - 1050) / 200) ** 2);     // nasal dip
    a *= f > 400 ? Math.sqrt(400 / f) : 1;                       // spectral tilt
    a /= 1 + (f / 3500) ** 4;                                    // soft top
    amps[h] = a;
  }
  // WebAudio normalises a periodic wave to a peak of 1; do the same.
  let peak = 0;
  for (let i = 0; i < 2048; i++) {
    const p = (2 * Math.PI * i) / 2048;
    let s = 0;
    for (let h = 1; h < amps.length; h++) s += amps[h] * Math.sin(h * p);
    peak = Math.max(peak, Math.abs(s));
  }
  for (let h = 1; h < amps.length; h++) amps[h] /= peak;
  return amps;
}

function wave(amps, phase) {
  let s = 0;
  for (let h = 1; h < amps.length; h++) s += amps[h] * Math.sin(h * phase);
  return s;
}

// One syllable, the way sing() schedules a note, with the hold and fade of a sample.
function render({ on, v }) {
  const n = Math.round(LENGTH * SR);
  const out = new Float32Array(n);
  const vowel = harmonics(F0, FORM[v], [1, 0.6, 0.25]);
  const cons = CONS_F[on] ? harmonics(F0, CONS_F[on], CONS_G[on] || [1, 0.45, 0.18]) : null;
  const [cl, x0, xl] = CONS_T[on] || [0, 0, 0];
  const a0 = cl;
  const xs = a0 + x0;
  const off = 0.26;                                   // the vowel holds until here, then fades
  const sus = Math.max(a0 + 0.08, 0.05);
  const tauAttack = on === "с" || on === "ш" ? 0.02 : on ? 0.01 : 0.018;
  const lc = on === "м" ? 0.75 : 1;
  const fStart = F0 * 0.98;

  let g = 0;
  let phase = 0;
  const k = (tau) => 1 - Math.exp(-1 / (SR * tau));
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    // amplitude
    if (on === "р" && t < 0.045) {
      const u = t / 0.045, e = Math.min(1, u / 0.25);
      g = e * e * (3 - 2 * e) * (1 - 0.5 * Math.sin(Math.PI * u) ** 2);   // one soft touch of the tongue
    } else if (t < a0) {
      g = 0;
    } else if (t < sus) {
      g += (1 - g) * k(tauAttack);
    } else if (t < off) {
      g += (0.85 - g) * k(0.2);
    } else {
      g += (0 - g) * k(0.1);
    }
    // pitch: a small scoop up to the note, then vibrato that comes in late
    let f = t < 0.04 ? fStart * Math.pow(F0 / fStart, t / 0.04) : F0;
    const vibGain = t < 0.1 ? 0 : t < 0.35 ? (t - 0.1) / 0.25 : 1;
    f += F0 * VOICE.vib * vibGain * Math.sin(2 * Math.PI * VOICE.rate * t);
    phase += (2 * Math.PI * f) / SR;
    // consonant spectrum flowing into the vowel
    let s;
    if (cons) {
      const gv = t < xs ? 0 : t < xs + xl ? (t - xs) / xl : 1;
      const gc = t < xs ? lc : t < xs + xl ? lc * (1 - (t - xs) / xl) : 0;
      s = gv * wave(vowel, phase) + gc * wave(cons, phase);
    } else {
      s = wave(vowel, phase);
    }
    out[i] = g * s;
  }
  // No click at the end, however small the tail.
  const fade = Math.round(0.03 * SR);
  for (let i = 0; i < fade; i++) out[n - 1 - i] *= i / fade;
  let peak = 0;
  for (const x of out) peak = Math.max(peak, Math.abs(x));
  for (let i = 0; i < n; i++) out[i] *= PEAK / peak;
  return out;
}

// One sung tone for the Head's sounds: a vowel out of a consonant, as in render(), but at any pitch,
// gliding from f0 to f1, with its own length, swell and fade. Still a clean harmonic tone.
function tone({ on = "", v, f0, f1 = f0, len, gain = 1, attack = 0.03, release = 0.1 }) {
  const n = Math.round(len * SR);
  const out = new Float32Array(n);
  const mid = (f0 + f1) / 2;
  const vowel = harmonics(mid, FORM[v], [1, 0.6, 0.25]);
  const cons = CONS_F[on] ? harmonics(mid, CONS_F[on], CONS_G[on] || [1, 0.45, 0.18]) : null;
  const [, x0, xl] = CONS_T[on] || [0, 0, 0];
  let phase = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    const env = Math.min(1, t / attack) * Math.min(1, (len - t) / release);
    const f = f0 * Math.pow(f1 / f0, t / len);
    phase += (2 * Math.PI * f) / SR;
    let s;
    if (cons) {
      const gv = t < x0 ? 0 : t < x0 + xl ? (t - x0) / xl : 1;
      s = gv * wave(vowel, phase) + (1 - gv) * wave(cons, phase);
    } else {
      s = wave(vowel, phase);
    }
    out[i] = gain * Math.max(0, env) * s;
  }
  return out;
}

// Lays tones one after another, with a pause before each where asked.
function phrase(parts) {
  const chunks = [];
  for (const p of parts) {
    if (p.pause) chunks.push(new Float32Array(Math.round(p.pause * SR)));
    chunks.push(tone(p));
  }
  const total = chunks.reduce((a, c) => a + c.length, 0);
  const out = new Float32Array(total);
  let at = 0;
  for (const c of chunks) { out.set(c, at); at += c.length; }
  let peak = 0;
  for (const x of out) peak = Math.max(peak, Math.abs(x));
  for (let i = 0; i < total; i++) out[i] *= PEAK / peak;
  return out;
}

// The Head's snore: a low hum in on «о», rising, and out on «у», falling. Nothing rasps.
const SNORE = phrase([
  { v: "о", f0: F0 * 0.5, f1: F0 * 0.56, len: 0.9, gain: 0.8, attack: 0.35, release: 0.3 },
  { v: "у", f0: F0 * 0.6, f1: F0 * 0.45, len: 0.75, gain: 0.55, attack: 0.1, release: 0.4, pause: 0.15 },
]);

// The Head's sneeze, «а-а-апчхи»: three «а» climbing, a catch, then a bright short «ши».
const SNEEZE = phrase([
  { v: "а", f0: F0, f1: F0 * 1.05, len: 0.2, gain: 0.55, attack: 0.04, release: 0.06 },
  { v: "а", f0: F0 * 1.12, f1: F0 * 1.18, len: 0.2, gain: 0.65, attack: 0.04, release: 0.06, pause: 0.04 },
  { v: "а", f0: F0 * 1.26, f1: F0 * 1.4, len: 0.3, gain: 0.75, attack: 0.04, release: 0.05, pause: 0.04 },
  { on: "ш", v: "и", f0: F0 * 1.7, f1: F0 * 1.5, len: 0.24, gain: 1, attack: 0.008, release: 0.16, pause: 0.09 },
]);

async function encode(pcm) {
  const encoder = await createOggEncoder();
  encoder.configure({ channels: 1, sampleRate: SR, vbrQuality: 5 });
  const parts = [encoder.encode([pcm]).slice(), encoder.finalize().slice()];
  const total = parts.reduce((a, p) => a + p.length, 0);
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const p of parts) { bytes.set(p, offset); offset += p.length; }
  return bytes;
}

mkdirSync(OUT, { recursive: true });
const sounds = {};
for (const syl of SYLLABLES) {
  writeFileSync(join(OUT, syl.name + ".ogg"), await encode(render(syl)));
  sounds["sing." + syl.name] = { subtitle: "subtitles.kolobok.sing", sounds: ["kolobok:voice/" + syl.name] };
}
for (const syl of SYLLABLES) {
  sounds["mumble." + syl.name] = { subtitle: "subtitles.kolobok.mumble", sounds: ["kolobok:voice/" + syl.name] };
}
writeFileSync(join(OUT, "snore.ogg"), await encode(SNORE));
writeFileSync(join(OUT, "sneeze.ogg"), await encode(SNEEZE));
sounds["snore"] = { subtitle: "subtitles.kolobok.snore", sounds: ["kolobok:voice/snore"] };
sounds["sneeze"] = { subtitle: "subtitles.kolobok.sneeze", sounds: ["kolobok:voice/sneeze"] };
writeFileSync(join(ASSETS, "sounds.json"), JSON.stringify(sounds, null, 2) + "\n");
console.log(`Rendered ${SYLLABLES.length} syllables, the snore and the sneeze to ${OUT}`);
