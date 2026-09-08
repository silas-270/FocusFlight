package com.example.focusflight.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════════════════════
// THEME-SWITCHABLE TOKENS
// ══════════════════════════════════════════════════════════════════════════════
// These are the tokens that differ between the dark "Emirates Luxury" palette and the light
// "Sky" palette. They stay top-level `val`s with these exact names - by delegating each one to
// `ActivePalette.current`, every existing call site (composables, Canvas draw lambdas, anything
// that just reads e.g. `Amber`) keeps working unchanged: Compose's snapshot system tracks the
// read wherever it happens and redraws/recomposes when `ActivePalette.current` changes, with no
// `@Composable` annotation required on these getters.

data class FocusFlightColors(
    val midnight: Color,   // background
    val deepNavy: Color,   // surface / card
    val slate: Color,      // secondary container
    val amber: Color,      // primary accent
    val softAmber: Color,  // translucent accent
    val offWhite: Color,   // onBackground / onSurface text
    val haze: Color,       // secondary text
    val dim: Color,        // tertiary text
    val green: Color,      // emerald accent
    val border: Color,     // outline
    val crimsonRed: Color,     // destructive actions
    val softCrimson: Color,    // translucent destructive container
    // The flat 2D world map (TravelMapCard / TravelMapDetailModal) - kept in step with the rest
    // of the theme rather than shared, since a dark espresso ocean read as broken on the light
    // "Sky" background it used to sit on.
    val mapOcean: Color,
    val mapUnvisitedLand: Color,
    val mapUnvisitedStroke: Color,
    val mapVisitedStroke: Color,
    val mapGraticule: Color,
)

val DarkPalette = FocusFlightColors(
    midnight    = Color(0xFF140D09),   // Deep dark espresso / black-leather base
    deepNavy    = Color(0xFF2B1C14),   // Rich saddle leather & walnut brown card surface
    slate       = Color(0xFF442E22),   // Warm chestnut / secondary container
    amber       = Color(0xFFD4AF37),   // Emirates signature champagne gold accent
    softAmber   = Color(0x40D4AF37),   // Translucent gold accent
    offWhite    = Color(0xFFF8F5EE),   // Warm cream / champagne white text
    haze        = Color(0xFFA89886),   // Warm sand / desert taupe secondary text
    dim         = Color(0xFF6B584B),   // Muted mocha / tertiary text
    green       = Color(0xFF10B981),   // Emerald green accent
    border      = Color(0xFF3D2A1F),   // Warm leather edge border
    crimsonRed  = Color(0xFFE54D4D),   // Luxury crimson red for destructive / dangerous actions
    softCrimson = Color(0x2EE54D4D),   // Translucent crimson red container
    mapOcean            = Color(0xFF19110B),   // Deep espresso ocean
    mapUnvisitedLand    = Color(0xFF332219),   // Warm saddle leather land
    mapUnvisitedStroke  = Color(0xFF5A4335),   // Muted leather country outline
    mapVisitedStroke    = Color(0xFFF8F5EE),   // Cream / white visited outline
    mapGraticule        = Color(0xFF5A4335),   // Subtle map route/grid lines
)

// "Sky" - clouds and open sky instead of the cockpit's leather and brass. Deliberately a
// saturated, colorful blue rather than a washed-out near-white - the point is a light theme that
// still reads as blue sky and still holds contrast against the white "cloud" cards, not a pale
// absence of color. The accent is a deliberately *deeper, more saturated* gold than the dark
// palette's champagne amber - that lighter gold sits too close to the sky blue in lightness and
// goes muddy/washed out on it (near-complementary hue, but almost no lightness contrast). A
// darker, richer marigold gold reads as "sun in the sky" instead of "barely visible."
val LightSkyPalette = FocusFlightColors(
    midnight    = Color(0xFF6EC2EF),   // Saturated sky blue background
    deepNavy    = Color(0xFFFFFFFF),   // White cloud card surface
    slate       = Color(0xFFBFE6FA),   // Lighter sky blue secondary container
    amber       = Color(0xFFC9820C),   // Deep marigold gold - holds contrast against the sky blue
    softAmber   = Color(0x40C9820C),
    offWhite    = Color(0xFF0E2A44),   // Deep sky-navy text - strong contrast on blue and white alike
    haze        = Color(0xFF2C5478),   // Muted steel-blue secondary text
    dim         = Color(0xFF4C7699),   // Softer tertiary text, still legible
    green       = Color(0xFF0E9668),   // Slightly deepened emerald - holds contrast on the brighter blue
    border      = Color(0xFF3E93C9),   // Vivid sky-blue outline, visible on both bg and card
    crimsonRed  = Color(0xFFD53F3F),   // Slightly deepened - still reads as destructive on light backgrounds
    softCrimson = Color(0x2ED53F3F),
    // The map's ocean/land/stroke tones are drawn from the same blue family as the rest of the
    // theme (bg and secondary-container blues) rather than a bespoke shade, so the map reads as
    // part of the app instead of a mismatched, oddly-darker patch sitting on top of it.
    mapOcean            = Color(0xFF6EC2EF),   // Same blue as the page background
    mapUnvisitedLand    = Color(0xFFEAF5FC),   // Near-white sky-tinted land, clearly lighter than ocean
    mapUnvisitedStroke  = Color(0xFF3E93C9),   // Same vivid blue as the app's border/outline token
    mapVisitedStroke    = Color(0xFF0E2A44),   // Deep navy outline - contrast against gold visited land
    mapGraticule        = Color(0xFFBFE6FA),   // Same secondary-container blue as the rest of the app
)

/** Holds the palette currently in effect. [FocusFlightTheme] writes to [current] whenever the
 *  resolved theme mode changes; every token below reads through it. */
object ActivePalette {
    var current: FocusFlightColors by mutableStateOf(DarkPalette)
}

val Midnight: Color get() = ActivePalette.current.midnight
val DeepNavy: Color get() = ActivePalette.current.deepNavy
val Slate: Color get() = ActivePalette.current.slate
val Amber: Color get() = ActivePalette.current.amber
val SoftAmber: Color get() = ActivePalette.current.softAmber
val OffWhite: Color get() = ActivePalette.current.offWhite
val Haze: Color get() = ActivePalette.current.haze
val Dim: Color get() = ActivePalette.current.dim
val Green: Color get() = ActivePalette.current.green
val Border: Color get() = ActivePalette.current.border
val CrimsonRed: Color get() = ActivePalette.current.crimsonRed
val SoftCrimson: Color get() = ActivePalette.current.softCrimson
val MapOcean: Color get() = ActivePalette.current.mapOcean
val MapUnvisitedLand: Color get() = ActivePalette.current.mapUnvisitedLand
val MapUnvisitedStroke: Color get() = ActivePalette.current.mapUnvisitedStroke
val MapVisitedStroke: Color get() = ActivePalette.current.mapVisitedStroke
val MapGraticule: Color get() = ActivePalette.current.mapGraticule

// ══════════════════════════════════════════════════════════════════════════════
// SHARED TOKENS (same in both themes)
// ══════════════════════════════════════════════════════════════════════════════
// Deliberately not palette-switched: these are keyed to their own metaphor (medals, parchment,
// gold map markings) rather than the background theme, and already read fine against both a dark
// cockpit and a light sky.

// ── Achievement Medals ───────────────────────────────────────────────────────
val Gold       = Color(0xFFFFDF73)
val GoldDeep   = Color(0xFF8B6508)
val GoldIcon   = Color(0xFFFFF7DC)

val Silver     = Color(0xFFE8E6E1)
val SilverDeep = Color(0xFF5A534C)
val SilverIcon = Color(0xFFFFFFFF)

val Bronze     = Color(0xFFD48148)
val BronzeDeep = Color(0xFF6E3618)
val BronzeIcon = Color(0xFFFFE8DC)

/** Not a metal, and deliberately so - the unranked band (see `AchievementTier.RUBY`). Warm enough
 *  to sit beside the champagne golds rather than fight them, and clearly distinct from
 *  [CrimsonRed], which this palette reserves for destructive actions. */
val Ruby       = Color(0xFFE8607A)
val RubyDeep   = Color(0xFF6E1226)
val RubyIcon   = Color(0xFFFFDDE4)

// ── World Map (theme-shared markings) ───────────────────────────────────────
val MapVisitedLand              = Color(0xFFD4AF37)   // Champagne gold visited land
val MapCompletedContinentStroke = Color(0xFF10B981)   // Emerald green complete continent
val MapRouteArc                 = Color(0xFFD4AF37)   // Gold active flight route

// ── Logbook & Parchment ──────────────────────────────────────────────────────
val LogbookParchment            = Color(0xFFF5E6C0)   // Aged cream paper
val LogbookParchmentDark        = Color(0xFFEDD89A)   // Yellowed paper patch
val LogbookInkDark              = Color(0xFF1A1208)   // Near-black ink
val LogbookInkMid               = Color(0xFF6B5033)   // Warm sepia mid-tone
val LogbookInkFaint             = Color(0xFFB09870)   // Faded sepia labels
val LogbookMarginRed            = Color(0xFFCC1C1C)   // Red margin stamp line
val LogbookGrainDark            = Color(0xFF8B6914)   // Warm paper noise
val LogbookGrainLight           = Color(0xFFFFFFE0)   // Highlight paper noise

// ── Pilot Ranks & Challenges ─────────────────────────────────────────────────
val RankCommander               = Color(0xFFD4AF37)   // Gold
val RankCaptain                 = Color(0xFF10B981)   // Emerald Green
val RankFirstOfficer            = Color(0xFFA89886)   // Sand / Taupe
val ChallengeGold               = Color(0xFFFFD700)
