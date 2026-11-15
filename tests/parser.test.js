const test = require('node:test');
const assert = require('node:assert/strict');
const Parser = require('../app/src/main/assets/mosaedy_parser.js');

const now = new Date(2026, 10, 14, 13, 43, 0, 0).getTime();
const relativeCases = [
  ['فكرني كمان دقيقة أجيب السندوتش', 60_000, 'أجيب السندوتش'],
  ['فكرني بعد 30 ثانية أراجع الملف', 30_000, 'أراجع الملف'],
  ['فكرني كمان 10 دقايق أشرب مياه', 600_000, 'أشرب مياه'],
  ['فكرني بعد ساعة ونص أراجع الملف', 5_400_000, 'أراجع الملف'],
  ['فكرني بعد ساعة ونصف أراجع الملف', 5_400_000, 'أراجع الملف'],
  ['فكرني بعد دقيقتين أراجع الملف', 120_000, 'أراجع الملف'],
  ['فكرني بعد ثانيتين أراجع الملف', 2_000, 'أراجع الملف'],
  ['فكرني بعد يومين أزور ماما', 172_800_000, 'أزور ماما'],
  ['فكرني بعد 2 يومين أزور ماما', 345_600_000, 'أزور ماما'],
  ['بعد عشر دقايق أجيب السندوتش', 600_000, 'أجيب السندوتش'],
  ['فكرني بعد كمان 10 دقايق أجيب السندوتش', 600_000, 'أجيب السندوتش'],
  ['Remind me in 10 minutes to call Mom', 600_000, 'call Mom'],
  ['Remind me in 1.5 hours to call Mom', 5_400_000, 'call Mom'],
  ['Remind me after half an hour to check', 1_800_000, 'check'],
  ['Remind me in eleven minutes to call', 660_000, 'call'],
];

for (const [phrase, delta, title] of relativeCases) {
  test(`relative reminder: ${phrase}`, () => {
    const result = Parser.parse(phrase, now, { defaultMode: 'alarm' });
    assert.equal(result.ok, true, result.message);
    assert.equal(result.title, title);
    assert.ok(Math.abs((result.dueAt - now) - delta) < 1_000);
    assert.equal(result.mode, 'alarm');
    assert.equal(result.notes, phrase);
  });
}

test('absolute English date and time is parsed without guessing the task', () => {
  const result = Parser.parse('Remind me tomorrow at 8 AM to call Ahmed', now, { defaultMode: 'alarm' });
  assert.equal(result.ok, true, result.message);
  assert.equal(result.title, 'call Ahmed');
  assert.ok(result.dueAt > now);
});

test('missing time asks for clarification rather than inventing a time', () => {
  const result = Parser.parse('فكرني أجيب السندوتش', now, { defaultMode: 'alarm' });
  assert.equal(result.ok, false);
  assert.equal(result.missing, 'time');
  assert.equal(result.title, 'أجيب السندوتش');
});

test('missing task asks what the reminder is for', () => {
  const result = Parser.parse('فكرني بعد 10 دقايق', now, { defaultMode: 'alarm' });
  assert.equal(result.ok, false);
  assert.equal(result.missing, 'title');
  assert.ok(result.dueAt > now);
});
