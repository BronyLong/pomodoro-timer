# Pomodoro Android

Android Studio project with:
- migrated Pomodoro logic from the Python app
- foreground service for background timer
- persistent timer notification
- cycle transition notifications
- local JSON storage for tasks and stats

## Open
Open the folder in Android Studio and run `app`.

## Notes
- `compileSdk` and `targetSdk` are set to 34 to avoid AGP 8.5.2 warning-related friction.
- Theme uses `Theme.AppCompat.DayNight.NoActionBar` and includes `androidx.appcompat:appcompat`.


V3 fixes:
- Russian task text input tuned for text keyboard and multiline entry.
- Foreground service startup rewritten to avoid crashes after pressing buttons.
- Android 14 specialUse foreground service declaration added.


v5 fixes:
- tasks are stored with explicit UTF-8
- task mutations are synchronized with a Mutex
- service now saves only engine/stats and preserves tasks
- task input state uses rememberSaveable to avoid losing text during timer refreshes


v6 changes:
- deleting a completed task no longer decreases completed-task statistics
- per-day stats history is stored in statsHistory map
- added statistics calendar with month navigation and date selection
- changed app name to 'Помодоро Таймер'
- added simple clock launcher icon
