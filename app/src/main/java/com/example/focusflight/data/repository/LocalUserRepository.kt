package com.example.focusflight.data.repository

import com.example.focusflight.data.local.UserProfileDao
import com.example.focusflight.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

class LocalUserRepository(
    private val userProfileDao: UserProfileDao
) : UserRepository {

    override fun getProfileFlow(): Flow<UserProfile?> {
        return userProfileDao.getProfileFlow()
    }

    override suspend fun getProfile(): UserProfile? {
        return userProfileDao.getProfile()
    }

    override suspend fun createProfile(username: String, homeAirportIata: String): UserProfile {
        val profile = UserProfile(
            username = username,
            userCode = UserProfile.generateUserCode(),
            homeAirportIata = homeAirportIata
        )
        val id = userProfileDao.insertProfile(profile)
        return profile.copy(id = id.toInt())
    }

    override suspend fun updateUsername(username: String) {
        val profile = userProfileDao.getProfile() ?: return
        userProfileDao.updateUsername(profile.id, username)
    }

    override suspend fun updateHomeAirport(iata: String) {
        val profile = userProfileDao.getProfile() ?: return
        userProfileDao.updateHomeAirport(profile.id, iata)
    }
}
