package com.smartparking.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import java.math.BigDecimal

/**
 * Sites / Parking Locations
 */
@Entity(
    tableName = "sites",
    indices = [Index(value = ["code"], unique = true)]
)
data class SiteEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "address")
    val address: String? = null,

    @ColumnInfo(name = "currency")
    val currency: String = "NPR",

    @ColumnInfo(name = "total_capacity")
    val totalCapacity: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Zones / Floors inside a parking site
 */
@Entity(
    tableName = "zones",
    indices = [Index(value = ["site_id"])]
)
data class ZoneEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "site_id")
    val siteId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "capacity")
    val capacity: Int = 0,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Vehicle Types (Motorcycle, Car, Van, Bus, Truck)
 */
@Entity(tableName = "vehicle_types")
data class VehicleTypeEntity(
    @PrimaryKey
    val id: String, // "MOTORCYCLE", "CAR", "VAN", "BUS", "TRUCK"

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "icon_name")
    val iconName: String? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * Tariff Profiles & Pricing Rules
 */
@Entity(
    tableName = "tariffs",
    indices = [Index(value = ["site_id"])]
)
data class TariffEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "site_id")
    val siteId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "vehicle_type_id")
    val vehicleTypeId: String? = null,

    @ColumnInfo(name = "grace_period_mins")
    val gracePeriodMins: Int = 10,

    @ColumnInfo(name = "min_charge")
    val minCharge: String = "0.00", // Store as String for precision

    @ColumnInfo(name = "max_daily_charge")
    val maxDailyCharge: String? = null,

    @ColumnInfo(name = "lost_ticket_fee")
    val lostTicketFee: String = "300.00",

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Tiered Tariff Pricing Rules (hourly brackets)
 */
@Entity(
    tableName = "tariff_tiers",
    indices = [Index(value = ["tariff_id"])]
)
data class TariffTierEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "tariff_id")
    val tariffId: String,

    @ColumnInfo(name = "from_minutes")
    val fromMinutes: Int,

    @ColumnInfo(name = "to_minutes")
    val toMinutes: Int? = null, // null = infinity

    @ColumnInfo(name = "rate")
    val rate: String, // Decimal as String

    @ColumnInfo(name = "is_fixed")
    val isFixed: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * SUNMI Devices / Terminals registered to site
 */
@Entity(
    tableName = "devices",
    indices = [
        Index(value = ["site_id"]),
        Index(value = ["device_serial"], unique = true)
    ]
)
data class DeviceEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "site_id")
    val siteId: String,

    @ColumnInfo(name = "device_serial")
    val deviceSerial: String,

    @ColumnInfo(name = "model_name")
    val modelName: String? = null,

    @ColumnInfo(name = "assigned_gate")
    val assignedGate: String? = null,

    @ColumnInfo(name = "terminal_mode")
    val terminalMode: String, // "ENTRY_ONLY", "EXIT_ONLY", "BIDIRECTIONAL"

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "last_sync_at")
    val lastSyncAt: Long? = null
)

/**
 * Users & Operators
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["site_id"]),
        Index(value = ["username"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "site_id")
    val siteId: String? = null,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "full_name")
    val fullName: String,

    @ColumnInfo(name = "pin_hash")
    val pinHash: String,

    @ColumnInfo(name = "password_hash")
    val passwordHash: String? = null,

    @ColumnInfo(name = "role")
    val role: String, // "SUPER_ADMIN", "SITE_MANAGER", "OPERATOR", "AUDITOR"

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Parking Sessions (Core Transaction Table)
 */
@Entity(
    tableName = "parking_sessions",
    indices = [
        Index(value = ["ticket_number"], unique = true),
        Index(value = ["site_id"]),
        Index(value = ["vehicle_number"]),
        Index(value = ["status"]),
        Index(value = ["entry_time"]),
        Index(value = ["sync_status"])
    ]
)
data class ParkingSessionEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "ticket_number")
    val ticketNumber: String,

    @ColumnInfo(name = "site_id")
    val siteId: String,

    @ColumnInfo(name = "zone_id")
    val zoneId: String? = null,

    @ColumnInfo(name = "vehicle_number")
    val vehicleNumber: String,

    @ColumnInfo(name = "vehicle_type_id")
    val vehicleTypeId: String,

    @ColumnInfo(name = "customer_phone")
    val customerPhone: String? = null,

    @ColumnInfo(name = "entry_time")
    val entryTime: Long,

    @ColumnInfo(name = "entry_operator_id")
    val entryOperatorId: String? = null,

    @ColumnInfo(name = "entry_device_id")
    val entryDeviceId: String? = null,

    @ColumnInfo(name = "exit_time")
    val exitTime: Long? = null,

    @ColumnInfo(name = "exit_operator_id")
    val exitOperatorId: String? = null,

    @ColumnInfo(name = "exit_device_id")
    val exitDeviceId: String? = null,

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int? = null,

    @ColumnInfo(name = "tariff_id")
    val tariffId: String? = null,

    @ColumnInfo(name = "gross_amount")
    val grossAmount: String = "0.00",

    @ColumnInfo(name = "discount_amount")
    val discountAmount: String = "0.00",

    @ColumnInfo(name = "net_amount")
    val netAmount: String = "0.00",

    @ColumnInfo(name = "payment_status")
    val paymentStatus: String = "PENDING", // "PENDING", "PAID", "WAIVED"

    @ColumnInfo(name = "payment_method")
    val paymentMethod: String? = null,

    @ColumnInfo(name = "payment_reference")
    val paymentReference: String? = null,

    @ColumnInfo(name = "status")
    val status: String = "PARKED", // "PARKED", "EXITED", "LOST_TICKET", "CANCELLED"

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "PENDING", // "PENDING", "SYNCED", "CONFLICT"

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Audit Logs
 */
@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["site_id"]),
        Index(value = ["user_id"]),
        Index(value = ["created_at"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "site_id")
    val siteId: String? = null,

    @ColumnInfo(name = "user_id")
    val userId: String? = null,

    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String? = null,

    @ColumnInfo(name = "entity_id")
    val entityId: String? = null,

    @ColumnInfo(name = "details")
    val details: String? = null, // JSON serialized details

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Offline Sync Queue
 */
@Entity(
    tableName = "offline_sync_queue",
    indices = [Index(value = ["created_at"])]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "attempts")
    val attempts: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null
)

/**
 * Memberships & Subscriptions (Monthly, Annual, VIP Passes)
 */
@Entity(
    tableName = "memberships",
    indices = [
        Index(value = ["member_number"], unique = true),
        Index(value = ["vehicle_number"]),
        Index(value = ["phone"]),
        Index(value = ["is_active"]),
        Index(value = ["end_date"])
    ]
)
data class MembershipEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "member_number")
    val memberNumber: String,

    @ColumnInfo(name = "full_name")
    val fullName: String,

    @ColumnInfo(name = "phone")
    val phone: String,

    @ColumnInfo(name = "vehicle_number")
    val vehicleNumber: String,

    @ColumnInfo(name = "vehicle_type_id")
    val vehicleTypeId: String = "CAR",

    @ColumnInfo(name = "plan_type")
    val planType: String = "MONTHLY", // "MONTHLY", "QUARTERLY", "HALF_YEARLY", "ANNUAL", "VIP_PASS"

    @ColumnInfo(name = "start_date")
    val startDate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "end_date")
    val endDate: Long,

    @ColumnInfo(name = "discount_percent")
    val discountPercent: Int = 100, // 100 = 100% free parking

    @ColumnInfo(name = "fee_paid")
    val feePaid: String = "0.00",

    @ColumnInfo(name = "payment_method")
    val paymentMethod: String = "CASH",

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

