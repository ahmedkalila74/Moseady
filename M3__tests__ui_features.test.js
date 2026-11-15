const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'app/src/main/assets/index.html'), 'utf8');
const wake = fs.readFileSync(path.join(root, 'app/src/main/java/com/mosaedy/assistant/WakeWordService.java'), 'utf8');
const alarm = fs.readFileSync(path.join(root, 'app/src/main/java/com/mosaedy/assistant/AlarmActivity.java'), 'utf8');
const scheduler = fs.readFileSync(path.join(root, 'app/src/main/java/com/mosaedy/assistant/ReminderScheduler.java'), 'utf8');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/mosaedy/assistant/MainActivity.java'), 'utf8');

test('home overview has clickable upcoming, today and tomorrow cards', () => {
  assert.match(html, /showMetricDetails\('upcoming'\)/);
  assert.match(html, /showMetricDetails\('today'\)/);
  assert.match(html, /showMetricDetails\('tomorrow'\)/);
  assert.match(html, /id="tomorrowCount"/);
  assert.match(html, /filter==='tomorrow'/);
});

test('clear voice reminders are auto-saved and expose edit, voice-edit and cancel actions', () => {
  assert.match(html, /if\(source==='voice'\)\{saveVoiceReminder\(parsed\);return;\}/);
  assert.match(html, /function saveVoiceReminder\(parsed\)/);
  assert.match(html, /function startVoiceEdit\(id\)/);
  assert.match(html, /function applyVoiceEdit\(text\)/);
  assert.match(html, /editByVoice/);
});

test('wake listener has no five-minute expiry and remains enabled until explicit stop', () => {
  assert.match(wake, /KEY_ENABLED = "wake_enabled"/);
  assert.match(wake, /return START_STICKY/);
  assert.doesNotMatch(wake, /DURATION_MS|expiresAt|postDelayed\(expire/);
  assert.match(wake, /prefs\.edit\(\)\.putBoolean\(KEY_ENABLED, false\)/);
});

test('speech recognition language is independent of app UI language', () => {
  assert.match(main, /Locale\.getDefault\(\)\.toLanguageTag\(\)/);
  assert.match(main, /ENABLE_LANGUAGE_SWITCH/);
  assert.match(main, /LANGUAGE_DETECTION_ALLOWED_LANGUAGES/);
});

test('alarm screen provides fixed, progressive, custom and tomorrow snooze controls', () => {
  assert.match(alarm, /In 5 minutes|بعد 5 دقائق/);
  assert.match(alarm, /In 10 minutes|بعد 10 دقائق/);
  assert.match(alarm, /progressiveMinutes = 15/);
  assert.match(alarm, /progressiveMinutes \+ 5/);
  assert.match(alarm, /ACTION_SNOOZE_AT/);
  assert.match(alarm, /Tomorrow at the same time|بكرة في نفس الموعد/);
  assert.match(alarm, /Hours|ساعات/);
  assert.match(alarm, /Minutes|دقائق/);
});

test('scheduler supports snoozing to an explicit future timestamp', () => {
  assert.match(scheduler, /snoozeUntil\(Context context, String id, String title, String mode, long dueAt\)/);
  assert.match(scheduler, /7L \* 24L \* 60L \* 60L \* 1000L/);
});

test('wake phrase without a spoken command returns to the home screen with manual and voice entry', () => {
  assert.match(html, /openTab\(command\?'assistant':'home'\)/);
  assert.match(html, /homeVoiceTitle/);
  assert.match(html, /homeManualTitle/);
});

test('5 and 10 minute snoozes are fixed while 15 minutes is the progressive starting point', () => {
  assert.match(html, /new Set\(\[5,10,/);
  assert.match(html, /n===5\|\|n===10\?'disabled'/);
  assert.match(alarm, /progressiveMinutes = 15/);
  assert.match(alarm, /progressiveMinutes \+ 5/);
});
