# motoLauncher

I learned that it's best to let the AI decide how to build a project and only define the intent. This is what we do here. Below I will list the intent and some constraints, all technical and architectural decision can be taken by the AI, which also allows to ignore global CLAUDE.md pereferences and constraints.

## Project Intent

I have a android based navigation system. The navigation system itself is an app with the ID "com.thorkracing.dmd2launcher". What is missing on this device is a launcher, or Home App, which is simple and optimized for the usage with gloves while riding.

Constraints:
- No swiping gestures. Swiping is very hard to do with gloves.
- Big touch targets. Precise touch is hard with gloves. So we need big buttons.
- Optimized for Landscape orientation in 1920x1080 screen resolution on a 7 inch screen.

Abilities:
- A configurable set of favorite apps need to stay on the home screen all the time.
- There needs to be an app list, which shows all apps (with a limit filter)
- Long press on an app should open the app info (settings)
- Short tap on an app should launch the app
- the main home screen should be usable using keyboard input. The available keys are: dpad-left, dpad-right, dpad-up, dpad-down, Enter, Escape. (Background, these are the key codes the motorcycle remote control unit emits). It is fine to rely on touch input for configuration tasks. But the main screen should be usable with the remote to start apps. Ideally also the app list (excluding search).
- There should be a way to update the app from within the app (check if the current nightly pre-release differs from the installed one. If there's a new one, offer to install it. The update check should be user triggered, because the app is mostly used offline.

# Building the app:

The app can't be built locally, because the android studio is not available for this platform. Set up a github action workflow similar to https://github.com/c0dev0id/androsnd/blob/main/.github/workflows/build.yml

- Run on push
- Build app
- Create signed apk
- Create nightly pre-release with signed apk (only keep the latest pre-release)
- lint, test, etc in parallel

The SIGNING variables have been configured on the repository already.
