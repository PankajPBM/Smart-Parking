# Contributing to Smart Parking

Thank you for your interest in contributing to Smart Parking!

This document provides guidelines and instructions for contributing to the project.

---

## 📋 Code of Conduct

### Our Standards
- Use welcoming and inclusive language
- Be respectful of differing viewpoints and experiences
- Gracefully accept constructive criticism
- Focus on what is best for the community
- Show empathy towards other community members

---

## 🛠️ Development Setup

### Prerequisites
- **JDK 17** (or later)
- **Android Studio Hedgehog** (2023.1.1) or later
- **Git** installed on your machine
- **SUNMI POS device** (optional, for hardware testing)

### Getting Started

1. **Fork and clone the repository:**
```bash
git clone https://github.com/your-org/smart-parking.git
cd smart-parking
```

2. **Open in Android Studio:**
   - File → Open → Select `SmartParkingApp` folder
   - Wait for Gradle sync to complete

3. **Verify installation:**
```bash
./gradlew test
```

---

## 🌿 Git Branching Strategy

We follow a modified **Git Flow** branching model:

### Main Branches
- **`main`** - Production releases (stable, tagged)
- **`develop`** - Integration branch for features (active development)

### Supporting Branches
- **`feature/feature-name`** - New features (branch from `develop`)
- **`bugfix/bug-name`** - Bug fixes (branch from `develop`)
- **`release/v1.x.x`** - Release preparation (branch from `develop`)
- **`hotfix/v1.x.x`** - Critical production fixes (branch from `main`)

### Branch Naming Conventions
```
feature/add-anpr-integration
feature/monthly-membership-ui
bugfix/printer-paper-out-crash
bugfix/tariff-calculation-rounding
hotfix/fix-payment-sync-issue
```

---

## 📝 Commit Message Guidelines

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

### Format
```
<type>(<scope>): <subject>

[optional body]

[optional footer(s)]
```

### Types
- **`feat`** - A new feature
- **`fix`** - A bug fix
- **`docs`** - Documentation changes only
- **`style`** - Formatting changes (no production code change)
- **`refactor`** - Code change that neither fixes a bug nor adds a feature
- **`perf`** - Performance improvement
- **`test`** - Adding or fixing tests
- **`chore`** - Build process or tooling changes

### Scopes
- `hardware` - SUNMI printer, scanner, capability layer
- `tariff` - Pricing engine and rules
- `ui` - Jetpack Compose screens and components
- `db` - Room database, entities, DAOs
- `sync` - Background sync and WorkManager
- `auth` - User management and permissions

### Examples
```
feat(hardware): add auto paper width detection for 80mm printers
fix(tariff): correct grace period calculation for edge cases
docs(readme): update setup instructions for SUNMI V2s Plus
test(tariff): add unit tests for daily cap enforcement
refactor(ui): extract reusable vehicle type card component
```

---

## 💻 Coding Standards

### Kotlin Style Guide
Follow the official [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide).

### Key Rules
1. **No Hard-Coded Strings:** Use string resources or constants
2. **Strict Null Safety:** Avoid `!!` operator; use safe calls `?.` or elvis `?:`
3. **Coroutines Best Practices:**
   - Use appropriate dispatchers (`Dispatchers.IO` for DB/network)
   - Handle cancellation gracefully
4. **Compose Guidelines:**
   - Stateless composables where possible
   - State hoisting to ViewModels
   - Remember expensive calculations
5. **Clean Architecture:**
   - Business logic in domain layer
   - Data access in data layer
   - UI only handles presentation
6. **Hardware Abstraction:**
   - Never call SUNMI hardware directly from UI
   - Always use `UniversalHardwareService` or `ParkingPrinter` interface

---

## 🧪 Testing Requirements

### Before Submitting a PR
- [ ] All unit tests pass (`./gradlew test`)
- [ ] Lint checks pass (`./gradlew lint`)
- [ ] New features include unit tests
- [ ] Code is formatted (`./gradlew ktlintFormat` if available)
- [ ] Tested on at least one SUNMI device (if hardware-related)

### Writing Tests
- Place unit tests in `app/src/test/java/`
- Place instrumented tests in `app/src/androidTest/java/`
- Use descriptive test names with backticks:
```kotlin
@Test
fun `grace period test - free within 10 minutes`() {
    // test implementation
}
```

---

## 🚀 Pull Request Process

1. **Create a feature branch:**
```bash
git checkout develop
git pull origin develop
git checkout -b feature/your-feature-name
```

2. **Make your changes:**
   - Write clean, documented code
   - Add unit tests
   - Commit with conventional commit messages

3. **Keep your branch updated:**
```bash
git fetch origin
git rebase origin/develop
```

4. **Push to your fork:**
```bash
git push origin feature/your-feature-name
```

5. **Open a Pull Request:**
   - Target `develop` branch
   - Fill out the PR template
   - Link related issues
   - Request review from maintainers

### PR Review Checklist
- Code follows project standards
- Tests are included and passing
- Documentation is updated (if needed)
- No merge conflicts
- Tested on target hardware (if applicable)

---

## 📦 Release Process (Maintainers Only)

1. Create a release branch from `develop`:
```bash
git checkout -b release/v1.1.0 develop
```

2. Update version numbers in:
   - `app/build.gradle.kts` (`versionCode`, `versionName`)
   - `CHANGELOG.md` (move unreleased to new version)

3. Merge release into `main` and tag:
```bash
git checkout main
git merge --no-ff release/v1.1.0
git tag -a v1.1.0 -m "Release v1.1.0"
git push origin main --tags
```

4. Merge release back into `develop`:
```bash
git checkout develop
git merge --no-ff release/v1.1.0
git push origin develop
```

---

## 📞 Getting Help

- **Issues:** Report bugs or request features via GitHub Issues
- **Questions:** Contact the development team at dev@smartparking.com.np
- **Documentation:** Check `README.md`, `ARCHITECTURE.md`, and `TESTING.md`

Thank you for contributing to Smart Parking! 🎉
