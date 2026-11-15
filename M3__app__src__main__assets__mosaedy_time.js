/* Mosaedy 3.4 time-wheel conversion helpers. All values use local device time. */
(function (root) {
  'use strict';
  function toWheelHour(hour24, format) {
    const h = Math.max(0, Math.min(23, Math.trunc(Number(hour24) || 0)));
    return format === '12' ? h % 12 : h;
  }
  function fromWheelHour(wheelHour, ampm, format) {
    const max = format === '12' ? 11 : 23;
    const h = Math.max(0, Math.min(max, Math.trunc(Number(wheelHour) || 0)));
    return format === '12' ? (h % 12) + (ampm === 'PM' ? 12 : 0) : h;
  }
  function displayHour(wheelHour, format) {
    const h = Math.trunc(Number(wheelHour) || 0);
    return format === '12' && h === 0 ? 12 : h;
  }
  const api = { toWheelHour, fromWheelHour, displayHour };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.MosaedyTime = api;
})(typeof window !== 'undefined' ? window : globalThis);
