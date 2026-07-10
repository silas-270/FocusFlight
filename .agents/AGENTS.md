# FocusFlight — Workspace Agent Rules

## 1. Strict Tech Stack & Language
- **Language**: Kotlin ONLY. No Java.
- **UI Framework**: Jetpack Compose ONLY. Do NOT write XML layouts or mix views.
- **Single Activity**: Run everything through [CesiumActivity.kt](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/app/src/main/java/com/example/focusflight/CesiumActivity.kt) using Compose Navigation.

## 2. Architecture (MVVM)
- **Model**: Database queries via repositories in `data/`. No raw DB logic in ViewModels.
- **ViewModel**: Manage state in `ui/viewmodel/` using Kotlin `StateFlow`. ViewModels must have zero references to Android UI/Context classes.
- **View**: Stateless Compose screens in `ui/screens/` that consume state and emit events.

## 3. Styling & Spacing
- **Theme**: All styling must consume tokens from `com.example.focusflight.ui.theme.FocusFlightTheme` in [Theme.kt](file:///c:/Users/kamme/AndroidStudioProjects/FocusFlight/app/src/main/java/com/example/focusflight/ui/theme/Theme.kt).
- **No Hardcoding**: Never hardcode colors, typography, or dimensions (`dp`/`sp`). Use theme colors, Material typography, and spacing constants (e.g., `Spacing.Medium`).

## 4. Current Phase constraints
- **Native Engine Bypass**: The native CesiumRS engine is temporarily mocked/disabled. Do not restore native C/Rust loader code until UI development is fully completed.
- **Git Commits**: Commit changes locally using `git` as soon as a phase or distinct class/screen is verified.
