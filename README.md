# Smart Parking - Professional SUNMI POS Android Application

**Complete Parking Management System for SUNMI Devices**

A production-ready Android application built specifically for SUNMI POS terminals (V2/V2s/V2s Plus/T2/T3) with automatic device detection, thermal printing, hardware scanner integration, and offline-first architecture.

---

## 🎯 Features

### Core Functionality
- ✅ **Auto-Device Detection** - Automatically detects SUNMI model and adapts paper width (58mm/80mm)
- ✅ **Fast Entry Processing** - 3-second vehicle entry with instant QR ticket generation
- ✅ **Hardware Integration** - Built-in thermal printer + laser scanner support
- ✅ **Smart Tariff Engine** - Grace periods, tiered pricing, daily caps, vehicle-type rates
- ✅ **Offline-First** - Works without internet, syncs when connected
- ✅ **Real-Time Dashboard** - Live occupancy, revenue, statistics
- ✅ **QR Code Ticketing** - Automatic generation and printing
- ✅ **Multi-Zone Support** - Ground floor, basement, VIP sections
- ✅ **Role-Based Access** - Admin, Manager, Operator roles
- ✅ **Premium UI/UX** - Material 3 design with dark/light themes

### SUNMI Hardware Support
| Model | Paper Width | Scanner | NFC | Status |
|-------|-------------|---------|-----|--------|
| V2/V2s | 58mm | ✓ | ✓ | Tested |
| V2s Plus | 80mm | ✓ | ✓ | Tested |
| T2/T3 Pro | 80mm | - | ✓ | Tested |
| P2 Series | 58mm | ✓ | ✓ | Supported |

---

## 🏗️ Architecture

### Tech Stack
```
Kotlin 1.9.22
Jetpack Compose + Material 3
Room Database (SQLite + SQLCipher encryption)
Hilt Dependency Injection
Coroutines + Flow
SUNMI SDK (Printer + Scanner)
ZXing (QR Code Generation)
WorkManager (Background Sync)
```

### Project Structure
```
SmartParkingApp/
├── app/src/main/java/com/smartparking/app/
│   ├── data/
│   │   ├── local/
│   │   │   ├── entity/        # Room entities
│   │   │   ├── dao/           # Database access objects
│   │   │   └── SmartParkingDatabase.kt
│   │   └── repository/        # Data repositories
│   ├── domain/
│   │   └── tariff/            # Business logic (TariffEngine)
│   ├── hardware/
│   │   └── sunmi/             # SUNMI device abstraction
│   │       ├── SunmiDeviceModel.kt
│   │       ├── SunmiHardwareManager.kt
│   │       └── SunmiScannerReceiver.kt
│   ├── ui/
│   │   ├── theme/             # Colors, typography, Material theme
│   │   └── screens/
│   │       ├── dashboard/     # Main dashboard
│   │       ├── entry/         # Vehicle entry flow
│   │       └── exit/          # Vehicle exit & payment
│   ├── di/                    # Dependency injection modules
│   └── SmartParkingApplication.kt
└── build.gradle.kts
```

---

## 🚀 Quick Start

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- SUNMI POS device (for hardware testing)

### Installation

1. **Clone or copy the project:**
```bash
cd "D:\Pankaj\Project\Smart Parking\SmartParkingApp"
```

2. **Open in Android Studio:**
   - File → Open → Select `SmartParkingApp` folder
   - Wait for Gradle sync to complete

3. **Build the project:**
```bash
./gradlew build
```

4. **Deploy to SUNMI device:**
   - Connect device via USB (enable USB debugging)
   - Run → Run 'app' (Shift+F10)

### Default Login Credentials
```
Admin Account:
Username: admin
PIN: 0000

Operator Account:
Username: operator1
PIN: 1234
```

---

## 📱 Core Workflows

### 1. Vehicle Entry (3-Second Flow)
```
User Opens "Vehicle Entry"
  ↓
Types License Plate (e.g., BA 12 PA 1234)
  ↓
Selects Vehicle Type (Car/Bike/Van/Bus)
  ↓
Taps "GENERATE & PRINT TICKET"
  ↓
System:
  • Generates unique ticket: SP-YYYYMMDD-XXXX
  • Creates QR code
  • Prints ticket on SUNMI thermal printer
  • Saves to local Room database
  • Queues cloud sync
```

### 2. Vehicle Exit (5-Second Flow)
```
User Opens "Vehicle Exit"
  ↓
Scans QR Ticket (hardware scanner) OR searches manually
  ↓
System calculates parking fee using tariff engine
  ↓
Displays: Duration, Fee, Vehicle details
  ↓
User selects payment method (Cash/QR/Card)
  ↓
Taps "COMPLETE EXIT & PRINT"
  ↓
System:
  • Prints official receipt
  • Updates database (vehicle exited)
  • Decrements occupancy count
  • Records payment
```

---

## 🔧 SUNMI Hardware Integration

### Thermal Printer Usage

The `SunmiHardwareManager` automatically detects device model and paper width:

```kotlin
// Inject via Hilt
@Inject lateinit var sunmiHardware: SunmiHardwareManager

// Print entry ticket
sunmiHardware.printEntryTicket(
    siteName = "Smart Parking Terminal",
    ticketNo = "SP-20260913-001245",
    vehicleNo = "BA 12 PA 1234",
    vehicleType = "CAR",
    entryTime = "09:42 AM",
    zoneName = "Ground Floor - A",
    operatorName = "Operator 1",
    rateInfo = "First hour: NPR 50"
)

// Print exit receipt
sunmiHardware.printExitReceipt(
    siteName = "Smart Parking Terminal",
    receiptNo = "RCP-20260913-001245",
    ticketNo = "SP-20260913-001245",
    vehicleNo = "BA 12 PA 1234",
    vehicleType = "CAR",
    entryTime = "09:42 AM",
    exitTime = "12:00 PM",
    duration = "2h 18m",
    parkingFee = "120.00",
    discount = "0.00",
    totalPaid = "120.00",
    paymentMethod = "CASH",
    operatorName = "Operator 1"
)
```

### Hardware Scanner Integration

Scanner broadcasts are automatically captured:

```kotlin
// Observe scan events
sunmiScannerManager.observeScans().collect { scanResult ->
    val ticketNumber = scanResult.data // "TICKET:SP-20260913-001245"
    // Process ticket lookup
}

// Manually trigger scanner
sunmiScannerManager.triggerScan()
```

---

## 💰 Tariff Configuration

### Example Tariff Setup

```kotlin
val carTariff = TariffConfig(
    id = "tariff_car_default",
    name = "Standard Car Tariff",
    vehicleTypeId = "CAR",
    gracePeriodMinutes = 10,
    minCharge = BigDecimal("50.00"),
    maxDailyCharge = BigDecimal("500.00"),
    lostTicketFee = BigDecimal("300.00"),
    tiers = listOf(
        TariffTier(0, 60, BigDecimal("50.00"), isFixed = true),      // 0-1h: NPR 50
        TariffTier(60, 120, BigDecimal("80.00"), isFixed = true),    // 1-2h: NPR 80
        TariffTier(120, 240, BigDecimal("150.00"), isFixed = true),  // 2-4h: NPR 150
        TariffTier(240, null, BigDecimal("30.00"), isFixed = false)  // 4h+: NPR 30/hr
    )
)

// Calculate fee
val result = tariffEngine.calculateFee(
    durationMinutes = 138, // 2h 18m
    tariffConfig = carTariff
)
// result.netAmount = 150.00 (2-4h bracket)
```

---

## 🗄️ Database Schema

### Key Tables

**parking_sessions** (Core transaction table)
- `id`, `ticket_number`, `vehicle_number`, `vehicle_type_id`
- `entry_time`, `exit_time`, `duration_minutes`
- `gross_amount`, `net_amount`, `payment_status`, `payment_method`
- `status` (PARKED | EXITED | LOST_TICKET)
- `sync_status` (PENDING | SYNCED)

**tariffs** + **tariff_tiers**
- Dynamic pricing configuration per site/vehicle type

**sites**, **zones**, **devices**, **users**, **audit_logs**

---

## 🔒 Security Features

- **PIN-based authentication** for operators
- **Role-based access control** (RBAC)
- **Audit logging** for all critical operations
- **SQLCipher encryption** for local database
- **Device binding** (optional license enforcement)

---

## 📊 Dashboard Statistics

Real-time metrics displayed:
- **Total Capacity** vs **Currently Parked**
- **Today's Entries** / **Exits**
- **Today's Revenue** (NPR)
- **Occupancy %** with visual progress bar
- **Recent Activity** feed

---

## 🛠️ Customization

### Change Site Configuration

Edit `SmartParkingDatabase.kt` seed data:

```kotlin
db.siteDao().insertSite(
    SiteEntity(
        id = "your_site_id",
        code = "YOUR-CODE",
        name = "Your Parking Name",
        address = "Your Address",
        totalCapacity = 300  // Adjust capacity
    )
)
```

### Modify Tariff Rates

Update default tariffs in the database seeding section.

### Customize UI Theme

Edit `app/ui/theme/Color.kt`:

```kotlin
val PrimaryBlue = Color(0xFF2563EB)  // Change brand color
```

---

## 📦 Build & Release

### Generate APK

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Generate AAB (Play Store)

```bash
./gradlew bundleRelease
```

### ProGuard Configuration

Release builds include:
- Code shrinking
- Resource shrinking
- Obfuscation

---

## 🧪 Testing on Non-SUNMI Devices

The app gracefully degrades on non-SUNMI Android devices:
- Device detection returns `UNKNOWN` model
- Printer/scanner features show "Not available" state
- All other functionality works normally

---

## 📈 Future Enhancements (V2 Roadmap)

- [ ] ANPR (Automatic Number Plate Recognition) camera integration
- [ ] Boom barrier control via GPIO/relay
- [ ] Monthly membership & subscription management
- [ ] Multi-site cloud dashboard (Next.js web admin)
- [ ] WhatsApp/SMS receipt delivery
- [ ] Fonepay/eSewa/Khalti digital payment integration
- [ ] Real-time WebSocket sync across multiple devices
- [ ] Advanced analytics & business intelligence reports

---

## 📝 License

Proprietary software. All rights reserved.

---

## 🤝 Support

For technical support, deployment, or custom feature requests:
- Email: support@smartparking.com.np
- Phone: +977-9XXXXXXXXX

---

## 🏆 Why Smart Parking?

✅ **Production-Ready** - Built with enterprise-grade architecture  
✅ **SUNMI-Native** - Leverages full hardware capabilities  
✅ **Offline-Resilient** - Never stops working  
✅ **Scalable** - From single gate to multi-location chains  
✅ **Premium UX** - Modern, fast, intuitive interface  

**Built for Nepal. Optimized for SUNMI. Ready for deployment.**
