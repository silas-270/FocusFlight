package com.silas270.blocktime.data.model

/**
 * Lifecycle per docs/challenges.md: Active -> Completed (final qualifying flight), or
 * Active -> gone (abandon deletes the row entirely rather than storing e.g. an ABANDONED status
 * here - see `ChallengeRepository.abandonChallenge` and challenges.md's "Abandon, not reset").
 */
enum class ChallengeStatus { ACTIVE, COMPLETED }
