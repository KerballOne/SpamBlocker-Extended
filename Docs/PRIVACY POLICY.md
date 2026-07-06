### Table of Contents

- [Introduction](##Introduction)
- [Compliance with data regulations](##compliance-with-data-regulations)
- [Third party cloud service dependencies](##third-party-cloud-service-dependencies)
- [Data possibly processed by third party services](####data-possibly-processed-by-third-party-services)
- [Android permissions requested by the application](##android-permissions-requested-by-the-application)
- [License](##license)

## Introduction
This privacy policy covers the use of the 'SpamBlocker Extended' (https://github.com/KerballOne/SpamBlocker-Extended) Android application.

It may not be applicable to other software produced or released by KerballOne (https://github.com/KerballOne)

## Compliance with data regulations

SpamBlocker Extended is [GDPR](https://commission.europa.eu/law/law-topic/data-protection_en?), [HIPAA](https://www.hhs.gov/hipaa/index.html) and [CCPA](https://oag.ca.gov/privacy/ccpa/regs) privacy regulations compliant.

SpamBlocker Extended when running does not use, collect, store or share any statistics, personal information or analytics from its users, their devices, or their use of these, other than Android operating system built-in mechanisms that are present for all mobile applications.

SpamBlocker Extended does not contain any advertising SDK, nor any tracker of the user, their device, or their use of these.

Cookies are not used, stored, or shared, at any point.

As indirect identification data, SpamBlocker Extended only stores external API keys, only upon user action, and stored only on the user's device.

All external interactions require user action (pressing a button at least) unless explicitly configured by the user to automatically do so, which is always disabled by default.

## Third party cloud service dependencies

Note that SpamBlocker Extended:

* Relies on The "Do Not Call" (DNC) Database (https://www.ftc.gov/policy-notices/open-government/data-sets/do-not-call-data) to retrieve information usable (by American users) to perform blocking call numbers reported to the Federal Trade Commission. Only if the user accepts it explicitly. Used directly on the user's device, for this purpose only. This service may store user information(s) and data(s) allowing identification. Please refer to the [FTC's privacy policy](https://www.ftc.gov/privacy) for detailed information on how they handle user data.

* Allows online database(s) downloading, upon user activation and configuration, relying on any external database service the user sets up. Database(s) downloaded are stored and used locally on the user's device. Optionally, this service(s) may store user information(s) and data(s) allowing identification. Please refer to the service's own privacy policy for detailed information on how they handle user data.

* Allows online caller phone number verification, validation, or reporting, upon user configuration and activation, relying on external cloud service(s) the user chooses. User credentials (API key) for all such service(s) are stored locally on the user's device and are only used for authentication with the official endpoints the user configured. This service(s) may store user information(s) and data(s) allowing identification. Please refer to the service's own privacy policy for detailed information on how they handle user data.

#### Data possibly processed by third party services

__No personal data is sent to or otherwise shared with anyone. Data collected by third party services is by the operation of the device running SpamBlocker Extended and without support or participation from 'SpamBlocker Extended'.__

The only known possible data leaks _(to the third-party servers)_ are the following:
1. User's credentials _(API key)_.
2. User's device IP address.
3. Phone number verified and/or validated.
4. Country codes _(either auto-detected or set manually)_.
5. Date and time stamp, time difference to GMT.
6. Access status/HTTP status code.
7. Browser, operating system, interface, language, version of the browser software, user agent, and all information possibly available on the HTTP header.

Third party services do not necessarily collect all of this data _(always refer to the service's own privacy policy)_.

## Android permissions requested by the application
Note that the SpamBlocker Extended application __optionally__ requires the following Android platform permissions:

* "INTERNET" Android permission in order to be able to perform status retrieval, parsing or checking, downloading or updating a database, process instant query or number reporting. Only at the explicit request of the user or automatically if configured to do so.

* "MANAGE_EXTERNAL_STORAGE" _(Android 11+)_ or "READ/WRITE_EXTERNAL_STORAGE" _(Android 10)_ Android permission in order to be able to perform file access from an automated workflow. Only at the explicit request of the user or automatically if configured to do so.

* "ANSWER_PHONE_CALLS" Android permission in order to be able to perform Reject, Answer, and Hang-up actions on calls.

* "POST_NOTIFICATIONS" Android permission in order to be able to show notifications.

* "READ_CONTACTS" Android permission in order to be able to match contacts.

* "RECEIVE_SMS" / "RECEIVE_MMS" Android permission in order to be able to receive new messages for screening.

* "READ_CALL_LOG" and "READ_SMS" Android permission in order to be able to check if a call or number is repeated.

* "PACKAGE_USAGE_STATS" Android permission in order to be able to use the Recent Apps feature, only for checking whether an app has been used recently.

* "READ_PHONE_STATE" Android permission in order to be able to use the Answer+Hang-up block mode (monitor ringing state).

* "NOTIFICATION_ACCESS" Android permission in order to be able to use the Push Alert and Notification Screening features (reading the title/body of notifications from apps the user has explicitly selected).

* "VIBRATE", "WAKE_LOCK", and "CAMERA" Android permissions in order to be able to use the Alerts feature (vibration, screen-wake, and flashlight signals for allowed screened notifications).

* "SYSTEM_ALERT_WINDOW" Android permission in order to be able to show the Caller ID floating window.

## License
[MIT License](https://mit-license.org/)

Copyright (c) 2024 aj3423
Copyright (c) 2026 KerballOne
