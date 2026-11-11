# مساعدي — Android native prototype

This is an Android Studio Java/Gradle project, **not an APK**.

## Build
1. Open the project directory in Android Studio (JDK 17, Android SDK 35).
2. Sync Gradle and install any requested Android Gradle Plugin dependencies.
3. Build > Build APK(s), then install `app/build/outputs/apk/debug/app-debug.apk` on an Android phone.

## Implemented
- Native Arabic UI and speech recognition via Android speech intent (availability depends on installed recognition service).
- Relative-time reminders: `فكرني بعد 5 دقايق ...`, `بعد 2 ساعة ...`, `بعد 1 يوم ...`.
- Manual date/time picker for other reminders.
- Persistent reminder list in SharedPreferences; alarms via AlarmManager; rescheduling on reboot.
- Notification channel and Android 13+ notification permission.
- TTS reply (voice selection through system TTS settings; male/female availability varies).
- Wake phrase preference is saved but NOT actively listened for.

## Limitations
- No always-listening wake word/background microphone service.
- No AI conversational backend; limited command parsing only.
- Android 12+ exact alarm access may require granting Alarms & reminders in system settings; without it the app uses inexact alarms.
- Notification permission must be granted for visible alerts.
- Voice recognition requires a speech service and may require Internet.
- No reminder edit/delete UI yet.
- Not tested on physical Android hardware; no SDK/build toolchain in this environment.
