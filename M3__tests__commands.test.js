const test = require('node:test');
const assert = require('node:assert/strict');
const Commands = require('../app/src/main/assets/mosaedy_commands.js');

const now = new Date(2026, 10, 14, 13, 43, 0, 0).getTime();
const reminder = { id: 'test-1', title: 'اتصل بمحمد', dueAt: now + 60_000, mode: 'alarm', repeat: 'none' };

test('classifies reminder, list, edit and cancel intents', () => {
  assert.equal(Commands.classify('فكرني بعد 30 ثانية'), 'new');
  assert.equal(Commands.classify('عندي إيه بكرة؟'), 'list');
  assert.equal(Commands.classify('أجلها كمان 10 دقايق'), 'edit');
  assert.equal(Commands.classify('الغيه'), 'cancel');
});

test('postpone in Arabic moves a future reminder relative to its existing time', () => {
  const result = Commands.edit('أجلها كمان 10 دقايق', reminder, now);
  assert.equal(result.ok, true, result.message);
  assert.equal(result.updates.dueAt, reminder.dueAt + 600_000);
});

test('postpone in English moves a due reminder relative to now', () => {
  const dueReminder = { ...reminder, dueAt: now - 120_000 };
  const result = Commands.edit('postpone by 10 minutes', dueReminder, now);
  assert.equal(result.ok, true, result.message);
  assert.equal(result.updates.dueAt, now + 600_000);
});
