# Implementation Plan - Rename Project to Alien Scream

This plan outlines the steps to rename the project from "Alien Screamer" to "Alien Scream", including display names, project configuration, and package structure.

## Proposed Changes

### Project Configuration
#### [MODIFY] [settings.gradle.kts](file:///C:/Users/Ableton/IdeaProjects/Alien%20Screamer/settings.gradle.kts)
- Change `rootProject.name` to `"Alien Scream"`.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/Ableton/IdeaProjects/Alien%20Screamer/app/build.gradle.kts)
- Change `namespace` to `"com.alienscream.app"`.
- Change `applicationId` to `"com.alienscream.app"`.

### Resources
#### [MODIFY] [strings.xml](file:///C:/Users/Ableton/IdeaProjects/Alien%20Screamer/app/src/main/res/values/strings.xml)
- Change `app_name` to `"Alien Scream"`.

#### [MODIFY] [themes.xml](file:///C:/Users/Ableton/IdeaProjects/Alien%20Screamer/app/src/main/res/values/themes.xml)
- Rename `Theme.AlienScreamer` to `Theme.AlienScream`.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Ableton/IdeaProjects/Alien%20Screamer/app/src/main/AndroidManifest.xml)
- Update theme reference to `@style/Theme.AlienScream`.

### Code Changes
#### [MODIFY] [MainActivity.kt](file:///C:/Users/Ableton/IdeaProjects/Alien%20Screamer/app/src/main/java/com/alienscreamer/app/MainActivity.kt)
- Update package declaration to `com.alienscream.app`.
- Update imports to use `com.alienscream.app`.
- Rename `AlienScreamerApp` composable to `AlienScreamApp`.
- Update header text from `"Alien Screamer"` to `"Alien Scream"`.

#### [MODIFY] [SynthEngine.kt](file:///C:/Users/Ableton/IdeaProjects/Alien%20Screamer/app/src/main/java/com/alienscreamer/app/engine/SynthEngine.kt)
- Update package declaration to `com.alienscream.app.engine`.

#### [MODIFY] [SynthComponents.kt](file:///C:/Users/Ableton/IdeaProjects/Alien%20Screamer/app/src/main/java/com/alienscreamer/app/ui/SynthComponents.kt)
- Update package declaration and imports.

#### [MODIFY] [VisualizerSection.kt](file:///C:/Users/Ableton/IdeaProjects/Alien%20Screamer/app/src/main/java/com/alienscreamer/app/ui/VisualizerSection.kt)
- Update package declaration and imports.

#### [MODIFY] [Main.kt](file:///C:/Users/Ableton/IdeaProjects/Alien%20Screamer/src/Main.kt)
- Update `JFrame` title to `"Alien Scream"`.

### Structural Changes
- Move files from `app/src/main/java/com/alienscreamer/app/` to `app/src/main/java/com/alienscream/app/`.

## Verification Plan

### Automated Tests
- Run `gradlew assembleDebug` to ensure the project builds with the new package name and configuration.

### Manual Verification
- Verify the app name appears correctly in the launcher and UI header.
- Verify the theme is correctly applied.
