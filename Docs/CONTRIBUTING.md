# Contribution Documentation

__Any help is greatly appreciated.__

### Table of Contents

- [For translator](#for-translator)
- [For coder/developer](#for-coderdeveloper)
- [Build from source](#build-from-source)
- [New API presets](#new-api-presets)

## For translator:

Languages are translated using AI, it's improving, but still cannot fully replicate the precision of human translation. Feel free to propose new translations, or to update any existing one. You can translate it then share it via a [pull request](https://github.com/KerballOne/SpamBlocker-Extended/pulls)/[issue](https://github.com/KerballOne/SpamBlocker-Extended/issues/new).

#### Files to translate:

-  All [`strings_.xml`](../app/src/main/res/values/strings_1.xml) files in the [`values`](../app/src/main/res/values) folder
    - Make sure to insert a backslash `\` before any apostrophe `'` or quotes `"`
-  For F-Droid store listings, the `short_description.txt` / `full_description.txt` files under [`metadata/<locale>/`](../metadata)

#### Note
* If you are unsure of the locale prefix for a specific language, you can find it [here](https://countrycode.org/).

#### Explanation
* AI can't handle large files well, that's why `strings.xml` is split into multiple smaller files.


## For coder/developer:
Please try to keep new code consistent with the existing style, with a couple of notes:

- Please write comments only where the *why* isn't obvious from the code itself.
- Please open an issue to discuss first if you want to contribute a new feature, so we can agree on the approach before you put in the work.


## Build from source

This is a short guide for building and running this application with Android Studio:

1. Download Android Studio from [here](https://developer.android.com/studio).
2. Clone this repository to your local workspace: `git clone https://github.com/KerballOne/SpamBlocker-Extended.git`.
3. Prepare a device emulator from the menu: Tools → Device Manager, and follow the step-by-step guide.
4. Run the app. If step 3 was completed, you should be able to just press the "play" button (green triangle) at the top.
5. If everything goes well, you'll see the app installed on your emulator and be able to run it.

## New API presets

If you know a public database or API service that could be added to the presets, please open an issue.
- PRs are welcome for public services.
- For proprietary services, we'll reach out to ask for permission to integrate. It will only be added with their explicit permission.
  - If you've already contacted a provider and received a positive reply, feel free to open a PR with a preset template. We appreciate it and will be happy to merge it.
    - When contacting a provider, please make clear that: this app does not share numbers identified by their API with anyone else. It only shares numbers that were blocked by local rules, since no provider would want their own data leaked to competitors.
