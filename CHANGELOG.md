# Changelog

All notable changes to the Smart Parking project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-09-13

### 🎉 Initial Release

First production release of Smart Parking for SUNMI POS devices.

### Added

#### Core Features
- **Fast Vehicle Entry** - 3-second entry flow with automatic thermal ticket printing
- **Fast Vehicle Exit** - 5-second exit flow with fee calculation and receipt printing
- **Smart Tariff Engine** - Configurable grace periods, tiered rates, daily caps, vehicle-type pricing
- **Universal SUNMI Support** - Auto-detection for V2/V2s/V2s Plus/T2/T3/P2/D2/S2 series
- **Offline-First Architecture** - Full functionality without internet connection
- **Real-Time Dashboard** - Live statistics (occupancy, revenue, entries, exits)
- **QR Code Ticketing** - Automatic generation and thermal printing with QR codes
- **Multi-Zone Management** - Support for multiple parking zones (ground, basement, VIP)
- **Role-Based Access Control** - Admin, Manager, Operator roles with PIN authentication

#### Hardware Integration
- **Official SUNMI AIDL Printer Service** - IWoyouService.aidl integration
- **Dynamic Capability Detection** - Runtime probing of printer, scanner, NFC, camera
- **Auto Paper Width Adaptation** - Automatic 58mm vs 80mm receipt formatting
- **Hardware Scanner Support** - Integrated laser/camera barcode scanning
- **Print Queue System** - Failed print jobs automatically queued for retry
- **Printer Status Monitoring** - Real-time status (busy, out of paper, overheated)

#### Database & Sync
- **Room Database** - SQLite with encrypted storage (SQLCipher)
- **Offline Sync Queue** - Background synchronization with retry mechanism
- **Audit Logging** - Complete activity trail for compliance
- **Transaction Safety** - Print failures never corrupt parking sessions

#### UI/UX
- **Material 3 Design** - Modern, premium interface
- **Dark/Light Themes** - System-adaptive theming
- **Large Touch Targets** - Optimized for POS handheld operation
- **Premium Color System** - High-contrast, sunlight-readable palette
- **Animated Transitions** - Smooth, professional interactions

### Technical Stack
- Kotlin 1.9.22
- Jetpack Compose + Material 3
- Room Database 2.6.1
- Hilt Dependency Injection 2.50
- Coroutines + Flow
- SUNMI AIDL Services
- ZXing QR Code Generation

### Testing
- Unit tests for tariff calculation engine
- Hardware mock tests for printer layer
- Comprehensive testing documentation

### Documentation
- Complete README with setup instructions
- Architecture documentation
- Testing guide
- API reference for hardware abstraction

---

## [Unreleased]

### Planned for v1.1.0
- [ ] Monthly membership management
- [ ] Customer profiles and history
- [ ] Advanced reports (PDF/Excel export)
- [ ] Multi-language support (Nepali, English)
- [ ] SMS receipt delivery
- [ ] Web admin dashboard sync

### Planned for v1.2.0
- [ ] Digital payment integration (Fonepay, eSewa, Khalti)
- [ ] ANPR camera integration
- [ ] Boom barrier control
- [ ] Real-time WebSocket sync
- [ ] Multi-site management

---

## Version History

### Version Numbering
- **Major.Minor.Patch** (e.g., 1.0.0)
- **Major**: Breaking changes or major new features
- **Minor**: New features, backward compatible
- **Patch**: Bug fixes, backward compatible

### Release Dates
- **v1.0.0**: September 13, 2026 - Initial production release

---

## Migration Guide

### From Development to v1.0.0
No migration needed - first release.

### Database Migrations
- Initial schema v1 with seeded default data
- Default admin account: username `admin`, PIN `0000`
- Default operator account: username `operator1`, PIN `1234`

---

## Contributors

Built by the Smart Parking development team.

For support: support@smartparking.com.np

---

## License

Proprietary software. All rights reserved.
