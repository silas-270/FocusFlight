package com.example.focusflight.data.model

/**
 * Curated (we authored it, see [CuratedChallengeCatalog]) vs. custom (the player defined it) -
 * see docs/design/challenges.md's "Where it comes from" per type. Set-completion is curated
 * only (needs real authoring to mean anything); Route and Distance allow both.
 */
enum class ChallengeSource { CURATED, CUSTOM }
