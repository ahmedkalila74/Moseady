const test = require('node:test');
const assert = require('node:assert/strict');
const Time = require('../app/src/main/assets/mosaedy_time.js');

test('12 AM maps to wheel 12 and back to hour zero', () => {
  assert.equal(Time.toWheelHour(0, '12'), 0);
  assert.equal(Time.displayHour(0, '12'), 12);
  assert.equal(Time.fromWheelHour(0, 'AM', '12'), 0);
});

test('12 PM maps to wheel 12 and back to hour twelve', () => {
  assert.equal(Time.toWheelHour(12, '12'), 0);
  assert.equal(Time.displayHour(0, '12'), 12);
  assert.equal(Time.fromWheelHour(0, 'PM', '12'), 12);
});

test('9 AM and 9 PM remain distinct in 12-hour mode', () => {
  assert.equal(Time.toWheelHour(9, '12'), 9);
  assert.equal(Time.fromWheelHour(9, 'AM', '12'), 9);
  assert.equal(Time.fromWheelHour(9, 'PM', '12'), 21);
});

test('24-hour mode preserves all hours', () => {
  for (let hour = 0; hour < 24; hour++) {
    assert.equal(Time.toWheelHour(hour, '24'), hour);
    assert.equal(Time.fromWheelHour(hour, 'AM', '24'), hour);
  }
});
