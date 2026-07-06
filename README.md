# SpamBlocker Extended
Android Call/SMS/Notification blocker. (Android 10+)

SpamBlocker Extended blocks spam calls, SMS, and now notifications too. Notification Screening lets you apply the same blocking rules to RCS, Signal, WhatsApp, email, and any other messaging app that posts a notification, not just your default SMS app, so spam gets filtered no matter which app it actually arrives through.

Started as a fork of [aj3423/SpamBlocker](https://github.com/aj3423/SpamBlocker), with Notification Screening, per-app/per-rule Alerts, and a reworked Settings UI added on top. Published under its own applicationId (`dev.kerballone.spamblocker`) so it installs independently, side by side with the original if you want.

<p>
  <a href="https://github.com/KerballOne/SpamBlocker-Extended/releases/latest">
    <img src="https://github.com/user-attachments/assets/75d2f736-ba69-4173-b972-6f69a1804e85" alt="Get it on Github" height="60" />
  </a>
</p>

Table of Contents
=================
   * [What's different from SpamBlocker](#whats-different-from-spamblocker)
   * [How it works](#how-it-works)
   * [Features](#features)
   * [Limitations](#limitations)
   * [FAQ](#faq)
   * [Permissions](#permissions)
   * [Privacy](#privacy)
   * [Support](#support)
   * [Language Support](#language-support)
   * [Contributing](#contributing)
   * [Donate](#donate-)

# What makes this different

- **Notification Screening**: screens notifications from chosen apps, including RCS, Signal, WhatsApp, email, and any other messaging app, through the same Number/Text Rules used for real calls/SMS, and dismisses the notification if the rule says "Block."
- **Alerts**: a lightweight sound/vibration/flashlight/screen-wake chime, distinct from a full notification, for when Notification Screening *allows* a message. This avoids a duplicate notification when the app's own notification is already visible. Configurable per-app (Actions > Alerts) or per-rule (overrides the app default).
- **Settings redesign**: collapsible card sections (Basic Rules, Number Rules, Text Rules, Advanced Rules, Actions, Integrations, Miscellaneous), a 4th bottom-nav tab for notification-screening-sourced history, right-aligned status icons/counters on section headers, and a toggle-chip "Apply to" selector (Calls/SMS/MMS/Title/Body) instead of a checkbox dropdown.

See the [Core Features](https://github.com/KerballOne/SpamBlocker-Extended/wiki/Core-Features) wiki page for the regex rule engine, Query API workflows, Push Alert, SMS Alert, SMS Bomb, Spam Database, and more.

# How it works
It works without replacing your call/SMS app.
- For call: <br>
  It's a Caller ID app.

- For SMS: <br>
  <b>Standalone Mode</b>:

  The app takes over SMS notifications, you need to disable notifications from the SMS app to avoid duplicates.
  - Pros
    - Works with any SMS app.
    - Advanced notification management (customizable sound/icon/color/LED)
    - Built-in "Quick Copy" support (for copying OTP codes)
  - Cons
    - Feels disconnected, as messages and notifications are handled by two different apps.
    - The app doesn't handle RCS and MMS multimedia content.
    - Requires SMS permission.

  <b>Notification Screening</b>:

  For SMS/messaging apps that aren't your default SMS app, screens their *notifications* directly (needs Notification Access, no SMS permission).
  - Pros
    - Works with any app that posts notifications, not just SMS apps.
    - No SMS permission needed.
  - Cons
    - Only sees what's in the notification's title/body, not the full message.
    - The original app's own notification still briefly appears before this app can dismiss it.

# Features:

| Filter                        | It checks                                                                                                                                                                                      |
|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Contacts                      | From a contact?                                                                                                                                                                                |
| Contact Group                 | From a contact group?                                                                                                                                                                          |
| Contact Prefix                | Matches prefix of an existing contact? Use case: save one clinic number `xxxx22` to allow its full range `xxxx11`-`xxxx99`                                                                     |
| STIR/SHAKEN                   | Fails STIR/SHAKEN attestation?                                                                                                                                                                 |
| Repeated Calls                | Multiple calls from the same number in a short while?                                                                                                                                          |
| Dialed Number                 | Have you dialed the number?                                                                                                                                                                    |
| Answered Number               | Allow previously answered numbers                                                                                                                                                              |
| Emergency                     | Allow calls for a while after dialing an emergency number                                                                                                                                      |
| Push Alert                    | Allow calls after receiving notifications from other apps, e.g.: "Your order has been taken by driver ...", the driver may then contact you.                                                   |
| SMS Alert                     | Allow calls after receiving SMS messages like: "[From ...] We are calling to inform ..., please feel free to answer."                                                                          |
| SMS Bomb                      | Block continuous OTP message floods                                                                                                                                                            |
| Recent Apps                   | Allow calls if some apps have been used recently.<br>Use case:<br>&emsp; You ordered Pizza online and soon they call you to refund.                                                            |
| Meeting Mode                  | Decline calls during online video meetings.                                                                                                                                                    |
| Off Time                      | A time period that always allows calls, usually no spams at night.                                                                                                                             |
| Spam Database                 | If it exists in the spam database. Any public downloadable spam databases can be integrated, such as the [DNC](https://www.ftc.gov/policy-notices/open-government/data-sets/do-not-call-data). |
| Schedule & Calendar           | Auto adjust rules based on time schedule and calendar events                                                                                                                                   |
| Geolocation & Carrier         | Block numbers based on geolocation or carrier name                                                                                                                                             |
| CNAP                          | The caller's display name                                                                                                                                                                      |
| Instant Query                 | Check the incoming number online in real time, querying multiple API endpoints simultaneously, such as the [PhoneBlock](https://phoneblock.net/).                                              |
| Report Spam                   | Automatically or manually report the number to build our crowd-sourced databases, protecting others and yourself.                                                                              |
| Notification Screening        | Screens notifications from chosen apps against Number/Text Rules with "Apply to: Title/Body" enabled.                                                                                          |
| Alerts                        | A lightweight sound/vibration/flashlight/screen-wake chime for allowed screened notifications, per-app or per-rule.                                                                             |
| Regex<br>(regular expression) | Check the [Wiki](https://github.com/KerballOne/SpamBlocker-Extended/wiki/Regex-Workflow-Templates) for examples.                                                                                |

# Limitations
- Auto clear SMS: no plan
- Local AI support: no plan
- RCS support: no plan
- Android 9- support: no plan
- Notification Screening only sees notification title/body text, not the full message content, and can't prevent the original app's own notification from briefly appearing before it's dismissed.

# FAQ
 - [Security warning from Google Play when installing this app](https://github.com/aj3423/SpamBlocker/issues/108)
 - [How the "Priority" works](https://github.com/aj3423/SpamBlocker/issues/166)
 - [It stops working after being killed](https://github.com/aj3423/SpamBlocker/issues/100)


# Permissions

| Permission (all optional)            | Why                                                                    |
|--------------------------------------|------------------------------------------------------------------------|
| INTERNET                             | For database downloading / instant query / number reporting            |
| ANSWER_PHONE_CALLS                   | Reject, answer and hang-up calls                                       |
| POST_NOTIFICATIONS                   | Show notifications                                                     |
| READ_CONTACTS                        | Match contacts                                                         |
| RECEIVE_SMS / RECEIVE_MMS            | For SMS notification screening                                         |
| SEND_SMS                             | For auto replying to blocked contacts                                  |
| READ_CALL_LOG / READ_SMS             | For allowing repeated calls                                            |
| PACKAGE_USAGE_STATS                  | For feature: Recent Apps (check whether an app has been used recently) |
| READ_PHONE_STATE                     | For BlockMode: Answer+Hang-up (monitor ringing state)                  |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | For it to keep working after being swiped and killed                   |
| NOTIFICATION_ACCESS                  | For features: Push Alert and Notification Screening                   |
| WRITE_SETTINGS                       | For customizing call ringtone                                          |
| READ_LOG                             | For reporting bugs with logcat messages                                |
| SYSTEM_ALERT_WINDOW                  | For the caller ID floating window                                      |
| VIBRATE / WAKE_LOCK / CAMERA         | For Alerts (vibration, screen-wake, flashlight)                        |

# Privacy
 - For offline features

   No data collection.

 - For online features:

   The API endpoints will see your:

     - IP address
     - TLS and TCP fingerprints (which would reveal your Android version)
     - The reported number(including the country code)

   Nothing else.

   You can also disable the internet access, or download the offline apk from the release page.

Full [Privacy Policy](https://github.com/KerballOne/SpamBlocker-Extended/blob/master/Docs/PRIVACY%20POLICY.md).

# Support
 - Please open an [issue](https://github.com/KerballOne/SpamBlocker-Extended/issues) on this repo.
 - Many common questions are already answered in the [original SpamBlocker's issue list](https://github.com/aj3423/SpamBlocker/issues).


# Language support

Languages are translated using AI, PRs for corrections are welcome.

# Contributing
 - [Contributing Guidelines](https://github.com/KerballOne/SpamBlocker-Extended/blob/master/Docs/CONTRIBUTING.md)

# Donate 🤑

This project does not accept donations. If you'd like to support the developer of the original SpamBlocker project this was built on, see https://aj3423.github.io/donate
