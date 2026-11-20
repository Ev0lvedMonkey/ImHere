# Security policy

## Reporting a vulnerability

Please do not open a public issue for security problems. Report them privately
through GitHub Security Advisories: open the **Security** tab of this repository
and choose **Report a vulnerability**.

Include the app version, Android version, and steps to reproduce. Reports about
the SMS pipeline, the home-screen widget, location handling, or local data
storage are especially welcome.

## Scope

ImHere is a local-only Android app. It has no backend, no account, and no
`INTERNET` permission. Only the launcher activity and the widget provider are
exported; SMS status receivers, the SOS service, and debug tooling are not.
