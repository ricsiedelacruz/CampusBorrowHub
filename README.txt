CAMPUS BORROWHUB - HOW TO RUN
=============================

1. Open your existing "Campus BorrowHub" project in Android Studio
   (package name: com.example.campusborrowhub, the one you created with the
   "Empty Activity" / Jetpack Compose template).

2. In the Project panel open:
   app > src > main > java > com.example.campusborrowhub > MainActivity.kt

3. Select everything in that file (Ctrl+A), delete it, and paste the whole
   contents of the MainActivity.kt from this zip.
   (Or just copy the MainActivity.kt from this zip over the old one.)

4. Click "Sync Now" if Android Studio shows the banner (usually not needed),
   then press the green Run button.

No extra libraries, no downloads, no internet, no changes to build.gradle.
It only uses what the default Compose template already includes, plus your
existing CampusBorrowHubTheme.

HOW TO TRY THE APP
------------------
- Register with a school email (name@school.edu.ph) or a student ID, then enter
  the demo verification code shown on screen. Or tap a demo account.
- Browse tab: search + category filter, tap an item, send a borrow request.
- Profile > Demo tools: switch accounts to approve as the lender, mark handed
  over, mark returned (with condition), and leave ratings.
- Profile > "Skip ahead 1 day" to see due-date reminders and overdue notices.
- Bell icon (top right) shows notifications; Chats tab is in-app messaging.

SAVING DATA
-----------
Everything (accounts, items, requests, chats, ratings, notifications, and who is
logged in) is saved automatically to a file on the phone (borrowhub.json in the
app's private storage). It survives closing/restarting the app. To wipe it:
Profile > "Reset all saved data", or uninstall / "Clear storage" for the app.
Data is local to one phone; it is not shared between devices.
