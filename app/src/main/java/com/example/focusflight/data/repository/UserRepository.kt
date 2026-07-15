package com.example.focusflight.data.repository

import com.example.focusflight.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getProfileFlow(): Flow<UserProfile?>
    suspend fun getProfile(): UserProfile?
    suspend fun createProfile(username: String, homeAirportIata: String): UserProfile
    suspend fun updateUsername(username: String)
    suspend fun updateHomeAirport(iata: String)
}
