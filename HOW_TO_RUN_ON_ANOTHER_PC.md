# 💻 How to Transfer & Run on Another PC (Zero Errors)

This project has been configured to be **100% self-contained and portable**. You can copy it to any computer and run it in Android Studio with zero errors.

---

## 📦 Step 1: How to Copy the Project

1. Copy the entire **`SmartParkingApp`** folder:
   ```
   D:\Pankaj\Project\Smart Parking\SmartParkingApp
   ```
2. Paste it anywhere on the new PC (e.g. `C:\Projects\SmartParkingApp` or `D:\SmartParkingApp`).

*(Note: You do **not** need to copy the hidden `.gradle` or `build` folders if you want to zip it, but copying the whole folder as-is works perfectly).*

---

## 🛠️ Step 2: Open on the New PC

1. Open **Android Studio** on the new PC.
2. Click **File → Open** (or click **Open** on the Welcome screen).
3. Browse and select the **`SmartParkingApp`** folder.
4. Click **OK**.

---

## ⚡ Step 3: What Android Studio Does Automatically

* Android Studio will read `settings.gradle` and `build.gradle`.
* All dependencies (Compose, Room, SUNMI AIDL services, ZXing) will be downloaded from Google / Maven Central automatically.
* The **`app`** run configuration and the green **Run (▶)** button will be enabled immediately.

---

## 📱 Step 4: Connect SUNMI Device & Run

1. On the SUNMI POS device:
   * Go to **Settings → Developer Options → Enable USB Debugging**.
2. Connect the SUNMI device to the PC via USB.
3. In Android Studio, select your SUNMI device from the device dropdown.
4. Click the green **Run (▶)** button (or press `Shift + F10`).

---

## ⚙️ Minimum Requirements on New PC

* **Android Studio**: Android Studio Giraffe, Hedgehog, Iguana, Jellyfish, Koala, or Ladybug.
* **JDK**: JDK 17 (comes built-in with Android Studio).
* **Internet**: Active internet connection on the first sync so Gradle can download library packages.
