package com.smartparking.app.data.local.dao

import androidx.room.*
import com.smartparking.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ParkingSessionEntity)

    @Update
    suspend fun updateSession(session: ParkingSessionEntity)

    @Query("SELECT * FROM parking_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): ParkingSessionEntity?

    @Query("SELECT * FROM parking_sessions WHERE ticket_number = :ticketNumber LIMIT 1")
    suspend fun getSessionByTicketNumber(ticketNumber: String): ParkingSessionEntity?

    @Query("SELECT * FROM parking_sessions WHERE vehicle_number = :vehicleNumber AND status = 'PARKED' LIMIT 1")
    suspend fun getActiveSessionByVehicleNumber(vehicleNumber: String): ParkingSessionEntity?

    @Query("SELECT * FROM parking_sessions WHERE status = 'PARKED' ORDER BY entry_time DESC")
    fun getParkedSessionsFlow(): Flow<List<ParkingSessionEntity>>

    @Query("SELECT * FROM parking_sessions ORDER BY entry_time DESC LIMIT :limit OFFSET :offset")
    fun getAllSessionsPaged(limit: Int, offset: Int): Flow<List<ParkingSessionEntity>>

    @Query("SELECT COUNT(*) FROM parking_sessions WHERE status = 'PARKED'")
    fun getCurrentlyParkedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM parking_sessions WHERE status = 'PARKED'")
    suspend fun getCurrentlyParkedCount(): Int

    @Query("SELECT COUNT(*) FROM parking_sessions WHERE entry_time >= :startOfDayTimestamp")
    fun getTodayEntriesCountFlow(startOfDayTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM parking_sessions WHERE exit_time >= :startOfDayTimestamp AND status = 'EXITED'")
    fun getTodayExitsCountFlow(startOfDayTimestamp: Long): Flow<Int>

    @Query("SELECT * FROM parking_sessions WHERE (vehicle_number LIKE '%' || :query || '%' OR ticket_number LIKE '%' || :query || '%') ORDER BY entry_time DESC LIMIT 50")
    suspend fun searchSessions(query: String): List<ParkingSessionEntity>

    @Query("SELECT * FROM parking_sessions WHERE sync_status = 'PENDING' LIMIT 50")
    suspend fun getUnsyncedSessions(): List<ParkingSessionEntity>

    @Query("UPDATE parking_sessions SET sync_status = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSessionsSynced(ids: List<String>)
}

@Dao
interface TariffDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTariff(tariff: TariffEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTariffTiers(tiers: List<TariffTierEntity>)

    @Query("SELECT * FROM tariffs WHERE site_id = :siteId AND is_active = 1")
    fun getTariffsForSiteFlow(siteId: String): Flow<List<TariffEntity>>

    @Query("SELECT * FROM tariffs WHERE site_id = :siteId AND (vehicle_type_id = :vehicleTypeId OR vehicle_type_id IS NULL) AND is_active = 1 ORDER BY is_default ASC LIMIT 1")
    suspend fun getTariffForVehicle(siteId: String, vehicleTypeId: String): TariffEntity?

    @Query("SELECT * FROM tariff_tiers WHERE tariff_id = :tariffId ORDER BY from_minutes ASC")
    suspend fun getTiersForTariff(tariffId: String): List<TariffTierEntity>
}

@Dao
interface SiteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSite(site: SiteEntity)

    @Query("SELECT * FROM sites LIMIT 1")
    suspend fun getPrimarySite(): SiteEntity?

    @Query("SELECT * FROM sites LIMIT 1")
    fun getPrimarySiteFlow(): Flow<SiteEntity?>

    @Query("SELECT * FROM zones WHERE site_id = :siteId AND is_active = 1")
    fun getZonesForSiteFlow(siteId: String): Flow<List<ZoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZones(zones: List<ZoneEntity>)
}

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE username = :username AND is_active = 1 LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE pin_hash = :pinHash AND is_active = 1 LIMIT 1")
    suspend fun getUserByPin(pinHash: String): UserEntity?
}

@Dao
interface SyncQueueDao {

    @Insert
    suspend fun enqueue(item: SyncQueueEntity)

    @Query("SELECT * FROM offline_sync_queue ORDER BY created_at ASC LIMIT :limit")
    suspend fun getPendingItems(limit: Int = 20): List<SyncQueueEntity>

    @Delete
    suspend fun deleteItem(item: SyncQueueEntity)

    @Query("DELETE FROM offline_sync_queue WHERE id IN (:ids)")
    suspend fun deleteItems(ids: List<Int>)

    @Query("UPDATE offline_sync_queue SET attempts = attempts + 1, last_error = :error WHERE id = :id")
    suspend fun incrementAttempt(id: Int, error: String)
}

@Dao
interface AuditLogDao {

    @Insert
    suspend fun insertLog(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT 100")
    fun getRecentLogsFlow(): Flow<List<AuditLogEntity>>
}
