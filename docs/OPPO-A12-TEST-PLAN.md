# Device test protocol (OPPO A12)

1. Install the GitHub Actions debug APK and launch the app without granting permissions.
2. Verify Android/board/RAM/storage/screen/battery temperature/voltage and that unsupported values are marked unavailable.
3. Enter an actual first-use date; verify date-based age; clear app data and confirm that the previous date is not recoverable.
4. Turn on screen/charging monitoring and verify the persistent notification. Use the phone, charge it, reboot, return, and inspect cumulative times. These values must represent monitored time only.
5. Grant fine location when prompted, turn on device location, and try GPS from outdoors.
6. Test microphone with noise and in a quiet room; note that dBFS is not physical decibels SPL.
7. Test speaker, vibration, touch with 2+ fingers, camera launch and sensor readings.
8. Export JSON, check Arabic text and missing values; confirm there are no network requests.
9. Turn monitoring off and confirm that the notification disappears.
10. Record any OPPO ColorOS background management or permissions restrictions.

Device and battery ageing history from before app installation cannot be reconstructed from ordinary Android APIs.
