package com.example.focusflight.data.repository

import com.example.focusflight.data.local.ChallengeDao
import com.example.focusflight.data.local.UserProfileDao
import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.ChallengeProgress
import com.example.focusflight.data.model.ChallengeSource
import com.example.focusflight.data.model.ChallengeStatus
import com.example.focusflight.data.model.ChallengeType
import com.example.focusflight.data.model.CuratedChallengeCatalog
import com.example.focusflight.data.model.CuratedChallengeSets
import com.example.focusflight.data.model.SetMemberKind
import kotlinx.coroutines.flow.Flow

/** Shared cap across all types and both sources (curated+custom) - challenges.md's
 *  "Active-challenge cap". */
const val MAX_ACTIVE_CHALLENGES = 3

class LocalChallengeRepository(
    private val challengeDao: ChallengeDao,
    private val userProfileDao: UserProfileDao,
    private val airportRepository: AirportRepository
) : ChallengeRepository {

    private suspend fun getUserId(): Int =
        userProfileDao.getProfile()?.id
            ?: throw IllegalStateException("No user profile found. Create a profile first.")

    override suspend fun listActiveChallenges(): List<Challenge> =
        challengeDao.getByStatus(getUserId(), ChallengeStatus.ACTIVE)

    override fun listActiveChallengesFlow(): Flow<List<Challenge>> {
        // userId is resolved synchronously, mirroring LocalFlightLogRepository.getFlightHistoryFlow
        // - a challenges list is only ever read post-onboarding, when a profile is guaranteed.
        val userId = kotlinx.coroutines.runBlocking { getUserId() }
        return challengeDao.getByStatusFlow(userId, ChallengeStatus.ACTIVE)
    }

    override suspend fun getChallenge(id: Int): Challenge? = challengeDao.getById(id)

    private suspend fun hasCapSlot(userId: Int): Boolean =
        challengeDao.countByStatus(userId, ChallengeStatus.ACTIVE) < MAX_ACTIVE_CHALLENGES

    override suspend fun startCuratedChallenge(catalogId: String): StartChallengeResult {
        val template = CuratedChallengeCatalog.find(catalogId) ?: return StartChallengeResult.UnknownTemplate
        val userId = getUserId()
        if (!hasCapSlot(userId)) return StartChallengeResult.CapReached

        val challenge = when (template.type) {
            ChallengeType.ROUTE -> Challenge(
                userId = userId,
                type = ChallengeType.ROUTE,
                source = ChallengeSource.CURATED,
                name = template.name,
                description = template.description,
                originIata = template.originIata,
                destIata = template.destIata,
                positionIata = template.originIata,
                routeProgressFraction = 0f
            )
            ChallengeType.SET_COMPLETION -> {
                val def = template.setDefinition ?: return StartChallengeResult.UnknownTemplate
                Challenge(
                    userId = userId,
                    type = ChallengeType.SET_COMPLETION,
                    source = ChallengeSource.CURATED,
                    name = template.name,
                    description = template.description,
                    setCatalogId = def.catalogId,
                    setMemberKind = def.memberKind,
                    setTotalMembers = def.members.size,
                    // Starts fresh at zero, every instance - never seeded from Story Mode's
                    // visited-set. See challenges.md#isolation.
                    visitedSetMembers = emptySet()
                )
            }
            ChallengeType.DISTANCE -> Challenge(
                userId = userId,
                type = ChallengeType.DISTANCE,
                source = ChallengeSource.CURATED,
                name = template.name,
                description = template.description,
                targetDistanceKm = template.targetDistanceKm,
                cumulativeDistanceKm = 0.0
            )
        }
        val id = challengeDao.insert(challenge)
        return StartChallengeResult.Started(challenge.copy(id = id.toInt()))
    }

    override suspend fun startCustomRouteChallenge(originIata: String, destIata: String, name: String): StartChallengeResult {
        val userId = getUserId()
        if (!hasCapSlot(userId)) return StartChallengeResult.CapReached
        val challenge = Challenge(
            userId = userId,
            type = ChallengeType.ROUTE,
            source = ChallengeSource.CUSTOM,
            name = name,
            originIata = originIata,
            destIata = destIata,
            positionIata = originIata,
            routeProgressFraction = 0f
        )
        val id = challengeDao.insert(challenge)
        return StartChallengeResult.Started(challenge.copy(id = id.toInt()))
    }

    override suspend fun startCustomDistanceChallenge(targetDistanceKm: Double, name: String): StartChallengeResult {
        val userId = getUserId()
        if (!hasCapSlot(userId)) return StartChallengeResult.CapReached
        val challenge = Challenge(
            userId = userId,
            type = ChallengeType.DISTANCE,
            source = ChallengeSource.CUSTOM,
            name = name,
            targetDistanceKm = targetDistanceKm,
            cumulativeDistanceKm = 0.0
        )
        val id = challengeDao.insert(challenge)
        return StartChallengeResult.Started(challenge.copy(id = id.toInt()))
    }

    override suspend fun abandonChallenge(id: Int) {
        // Deletes the row entirely - no ABANDONED status - so the cap slot frees immediately and
        // retrying later is a fresh instance, per challenges.md's "Abandon, not reset".
        challengeDao.deleteById(id)
    }

    override suspend fun advanceRouteChallenge(challengeId: Int, newPositionIata: String): Challenge? {
        val challenge = challengeDao.getById(challengeId) ?: return null
        if (challenge.type != ChallengeType.ROUTE || challenge.status != ChallengeStatus.ACTIVE) return challenge

        val originIata = challenge.originIata ?: return challenge
        val destIata = challenge.destIata ?: return challenge
        val origin = airportRepository.getAirportByIata(originIata) ?: return challenge
        val dest = airportRepository.getAirportByIata(destIata) ?: return challenge
        val current = airportRepository.getAirportByIata(newPositionIata) ?: return challenge

        val reachedDest = newPositionIata.equals(destIata, ignoreCase = true)
        val progress = if (reachedDest) {
            1f
        } else {
            ChallengeProgress.routeProgress(
                originLat = origin.lat, originLon = origin.lon,
                destLat = dest.lat, destLon = dest.lon,
                currentLat = current.lat, currentLon = current.lon
            )
        }

        val updated = challenge.copy(
            positionIata = newPositionIata,
            routeProgressFraction = progress,
            status = if (reachedDest) ChallengeStatus.COMPLETED else ChallengeStatus.ACTIVE,
            completedAt = if (reachedDest) System.currentTimeMillis() else null
        )
        challengeDao.update(updated)
        return updated
    }

    override suspend fun creditEligibleFlight(destIata: String, distanceKm: Double) {
        val userId = getUserId()
        val active = challengeDao.getByStatus(userId, ChallengeStatus.ACTIVE)
        if (active.isEmpty()) return
        val destAirport = airportRepository.getAirportByIata(destIata)

        for (challenge in active) {
            when (challenge.type) {
                ChallengeType.DISTANCE -> creditDistance(challenge, distanceKm)
                ChallengeType.SET_COMPLETION -> creditSetCompletion(challenge, destAirport)
                ChallengeType.ROUTE -> Unit // Route only advances via advanceRouteChallenge's own scoped session
            }
        }
    }

    private suspend fun creditDistance(challenge: Challenge, distanceKm: Double) {
        val target = challenge.targetDistanceKm ?: return
        val newCumulative = challenge.cumulativeDistanceKm + distanceKm
        val completed = newCumulative >= target
        challengeDao.update(
            challenge.copy(
                cumulativeDistanceKm = newCumulative,
                status = if (completed) ChallengeStatus.COMPLETED else ChallengeStatus.ACTIVE,
                completedAt = if (completed) System.currentTimeMillis() else null
            )
        )
    }

    private suspend fun creditSetCompletion(challenge: Challenge, destAirport: Airport?) {
        val kind = challenge.setMemberKind ?: return
        // Look up the full member list from the curated catalog (rather than trusting only the
        // stored count) so "is this actually a relevant member" is checked against the real
        // definition, not just against whatever's already been credited.
        val definition = CuratedChallengeSets.find(challenge.setCatalogId ?: return) ?: return
        val memberValue = destAirport?.let { memberValueFor(kind, it) } ?: return
        if (memberValue !in definition.members) return // lands somewhere, but not a relevant member
        if (memberValue in challenge.visitedSetMembers) return // already credited this instance

        val updatedMembers = challenge.visitedSetMembers + memberValue
        val completed = updatedMembers.size >= definition.members.size
        challengeDao.update(
            challenge.copy(
                visitedSetMembers = updatedMembers,
                status = if (completed) ChallengeStatus.COMPLETED else ChallengeStatus.ACTIVE,
                completedAt = if (completed) System.currentTimeMillis() else null
            )
        )
    }

    private fun memberValueFor(kind: SetMemberKind, airport: Airport): String = when (kind) {
        SetMemberKind.CONTINENT -> airport.continent
        SetMemberKind.COUNTRY -> airport.isoCountry
        SetMemberKind.IATA -> airport.iataCode
    }
}
