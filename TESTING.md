# Testing Guide for Smart Parking

## 🧪 Testing Strategy

Smart Parking uses a multi-layered testing approach to ensure reliability on all SUNMI devices.

---

## 📋 Test Categories

### 1. Unit Tests (Local JVM)
**Location:** `app/src/test/java/`

Tests business logic without Android dependencies:
- Tariff calculation engine
- Data models and utilities
- Printer abstraction interfaces

**Run:**
```bash
./gradlew test
```

### 2. Instrumented Tests (Android Device/Emulator)
**Location:** `app/src/androidTest/java/`

Tests requiring Android framework:
- Room database operations
- UI component tests
- Hardware service integration

**Run:**
```bash
./gradlew connectedAndroidTest
```

### 3. Manual SUNMI Hardware Tests
**Location:** Settings → Device & Hardware → Test Functions

Real hardware validation:
- Printer connectivity
- Scanner functionality
- NFC operations
- Camera QR scanning

---

## 🎯 Unit Test Coverage

### TariffEngineTest.kt
✅ Grace period (free parking within 10 minutes)  
✅ First hour flat rate  
✅ Second hour tier pricing  
✅ Long duration hourly increments  
✅ Daily maximum cap enforcement  
✅ Lost ticket penalty  
✅ Duration formatting  

**Run specific test:**
```bash
./gradlew test --tests "com.smartparking.app.domain.tariff.TariffEngineTest"
```

---

## 📱 Testing on SUNMI Devices

### Prerequisites
1. SUNMI POS device (V2/V2s/V2s Plus/T2/T3/etc.)
2. USB debugging enabled
3. Android Studio installed

### Step-by-Step Testing

#### 1. Install Debug Build
```bash
cd "D:\Pankaj\Project\Smart Parking\SmartParkingApp"
./gradlew installDebug
```

Or via Android Studio: Run → Run 'app' (Shift+F10)

#### 2. Test Device Detection
**Expected:** App auto-detects SUNMI model, printer, scanner

Navigate to: **Settings → Device & Hardware**

Verify:
- ✅ Device manufacturer shows "SUNMI"
- ✅ Model name detected (e.g., "V2s Plus")
- ✅ Printer status: "Available"
- ✅ Paper width: "58mm" or "80mm"

#### 3. Test Printer
Tap **"Test Printer"** button

**Expected Output:**
```
========================================
SMART PARKING
TEST PRINT SUCCESSFUL
========================================

Device: SUNMI V2s Plus
Paper Width: 80mm
Timestamp: 2026-09-13 09:42 AM

If you can read this, the printer
is working correctly.

========================================
```

#### 4. Test Entry Flow
1. Tap **"Vehicle Entry"**
2. Enter: `BA 12 PA 1234`
3. Select: **Car**
4. Zone: **Ground Floor - A**
5. Tap: **"GENERATE & PRINT TICKET"**

**Expected:**
- ✅ Thermal ticket prints with QR code
- ✅ Success toast: "✓ Ticket Printed"
- ✅ Dashboard updates: "Currently Parked" count increases

**Verify Ticket Contains:**
- Ticket number (e.g., SP-20260913-001245)
- Vehicle number
- Entry timestamp
- QR code (scannable)

#### 5. Test Scanner
1. Tap **"Vehicle Exit"**
2. Tap **"SCAN TICKET"** button
3. Point SUNMI scanner at printed QR code

**Expected:**
- ✅ Hardware scanner beeps
- ✅ Vehicle details populate automatically
- ✅ Duration calculated (e.g., "2h 18m")
- ✅ Fee displayed (e.g., "NPR 120.00")

#### 6. Test Exit & Receipt Printing
1. Select payment: **Cash**
2. Tap **"COMPLETE EXIT & PRINT"**

**Expected:**
- ✅ Receipt prints automatically
- ✅ Success toast: "✓ Exit Complete"
- ✅ Dashboard: "Currently Parked" decreases

**Verify Receipt Contains:**
- Receipt number
- Entry/Exit timestamps
- Duration breakdown
- Fee calculation
- Payment method

---

## 🔍 Testing Different SUNMI Models

### Test Matrix

| Device | Paper Width | Scanner | Expected Behavior |
|--------|-------------|---------|-------------------|
| V2 | 58mm | No | Prints 32-char width, camera fallback |
| V2s | 58mm | Yes | Hardware scanner works |
| V2s Plus | 80mm | Yes | Wider receipts, hardware scanner |
| T2 | 80mm | No | Desktop printer, camera QR |
| P2 | 58mm | Yes | Handheld with scanner |

### Cross-Device Test Checklist
- [ ] Install on multiple SUNMI models
- [ ] Verify auto-detection correct for each
- [ ] Test entry/exit on each device
- [ ] Verify paper width adaptation
- [ ] Test scanner (hardware vs camera fallback)

---

## 🐛 Debugging Common Issues

### Issue: Printer Not Detected
**Symptoms:** Settings shows "Printer: Not Available"

**Debug Steps:**
1. Check SUNMI service:
```bash
adb shell dumpsys package woyou.aidlservice.jiuiv5
```
2. Verify printer binding in logs:
```bash
adb logcat | grep "SunmiAidlPrinter"
```
3. Restart printer service:
```bash
adb shell am startservice woyou.aidlservice.jiuiv5/.IWoyouService
```

### Issue: Print Job Fails
**Check:**
1. Paper loaded correctly
2. Printer cover closed
3. No paper jam
4. Check print queue:
```sql
SELECT * FROM offline_sync_queue WHERE event_type LIKE 'PRINT_%';
```

### Issue: Scanner Not Working
**Debug:**
1. Test broadcast receiver:
```bash
adb shell am broadcast -a com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED --es data "TICKET:SP-20260913-001245"
```
2. Check scanner intent filter in AndroidManifest.xml
3. Verify ScannerReceiver is registered

---

## 📊 Test Reports

### Generate Test Coverage Report
```bash
./gradlew jacocoTestReport
```

Report location: `app/build/reports/jacoco/jacocoTestReport/html/index.html`

### Run Lint Checks
```bash
./gradlew lint
```

Report location: `app/build/reports/lint-results.html`

---

## ✅ Pre-Release Testing Checklist

Before deploying to production:

### Functionality
- [ ] Entry flow completes successfully
- [ ] Exit flow completes successfully
- [ ] Tickets print correctly on target hardware
- [ ] Receipts print correctly on target hardware
- [ ] QR codes are scannable
- [ ] Fee calculation is accurate
- [ ] Grace period enforced
- [ ] Daily cap enforced
- [ ] Lost ticket penalty works

### Hardware
- [ ] Tested on target SUNMI model(s)
- [ ] Printer auto-detected
- [ ] Paper width correct (58mm vs 80mm)
- [ ] Scanner works (or camera fallback functional)
- [ ] NFC tested (if applicable)

### Data Integrity
- [ ] Parking sessions save correctly
- [ ] No duplicate entries possible
- [ ] Exit without entry prevented
- [ ] Database migrations work
- [ ] Offline queue persists

### UI/UX
- [ ] Dashboard statistics accurate
- [ ] All buttons responsive
- [ ] Large touch targets work on device
- [ ] Dark mode functional
- [ ] No crashes during normal operation

### Edge Cases
- [ ] Behavior when printer disconnects
- [ ] Behavior when out of paper
- [ ] Behavior when database is full
- [ ] Behavior with 1000+ parked vehicles
- [ ] Multi-operator concurrent use

---

## 📞 Support & Issues

For testing issues or bugs:
1. Check logs: `adb logcat | grep SmartParking`
2. Export database: `adb pull /data/data/com.smartparking.app/databases/smart_parking.db`
3. Review offline sync queue
4. Contact development team with device model + logs

---

## 🔄 Continuous Testing

### Local Development
```bash
# Run tests on every build
./gradlew test connectedAndroidTest

# Watch mode (re-run on file changes)
./gradlew test --continuous
```

### CI/CD Integration (Future)
```yaml
# .github/workflows/test.yml
- name: Run Unit Tests
  run: ./gradlew test
  
- name: Upload Test Reports
  uses: actions/upload-artifact@v2
  with:
    name: test-reports
    path: app/build/reports/tests/
```
