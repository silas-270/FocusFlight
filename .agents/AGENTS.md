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

## Layout
- NEVER stack components linearly top-to-bottom without a visual anchor or background layer
- ALWAYS think in layers: background (Canvas/SVG/gradient), mid (content), foreground (controls)
- Use Box with fillMaxSize to layer decorative SVG/Canvas behind content
- Prefer scaffold-style full-screen layouts over scrollable lists of components
- Dead whitespace below content = layout failure; fill it with a decorative background layer

## Icons & Images  
- NEVER use emoji as icons
- ALWAYS use Material Symbols via androidx.compose.material:material-icons-extended
- For network images: ALWAYS use Coil3 AsyncImage, NEVER load images manually
- For placeholder/decorative visuals: use Canvas drawing or vector drawables, NOT emojis
- Icon sizes: use standard Dp values (24.dp default, 48.dp for prominent actions)

## Navigation & Transitions
- NEVER use fadeIn/fadeOut as primary nav transition (looks sluggish)
- ALWAYS use slideIntoContainer/slideOutOfContainer for screen transitions
- Set duration to 300ms max (tween(300)), NEVER 1000ms+
- Forward: SlideDirection.Start enter + SlideDirection.Start exit
- Back: SlideDirection.End popEnter + SlideDirection.End popExit  
- Enable predictive back: android:enableOnBackInvokedCallback="true" in AndroidManifest
- Use Navigation Compose ≥ 2.8.0 for automatic predictive back gesture support
