# Git Version Control Setup Guide

## 📦 Initialize Git Repository

### Step 1: Open Terminal/Command Prompt

Navigate to your project directory:
```bash
cd "D:\Pankaj\Project\Smart Parking\SmartParkingApp"
```

### Step 2: Initialize Git
```bash
git init
```

**Output:**
```
Initialized empty Git repository in D:/Pankaj/Project/Smart Parking/SmartParkingApp/.git/
```

### Step 3: Configure Git (First Time)
```bash
git config user.name "Your Name"
git config user.email "your.email@example.com"
```

Or set globally for all projects:
```bash
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

---

## 🌿 Branch Setup

### Create Main and Develop Branches
```bash
# Ensure you're on main
git branch -M main

# Create develop branch
git checkout -b develop
```

---

## 📝 Initial Commit

### Stage All Files
```bash
git add .
```

### Create Initial Commit
```bash
git commit -m "feat: initial Smart Parking v1.0.0 release

- Universal SUNMI POS device support with auto-detection
- Official AIDL printer service integration (IWoyouService)
- Dynamic capability detection (printer, scanner, NFC, camera)
- Smart tariff engine with grace periods and tiered pricing
- Offline-first architecture with Room database
- Real-time dashboard with live statistics
- Fast 3-second entry, 5-second exit flows
- Automatic thermal ticket and receipt printing
- Material 3 UI with dark/light themes
- Complete testing infrastructure
- Comprehensive documentation"
```

---

## 🔗 Connect to Remote Repository (GitHub/GitLab)

### Option A: GitHub

1. **Create repository on GitHub** (via web interface)
   - Repository name: `smart-parking-sunmi`
   - Description: "Professional Parking Management System for SUNMI POS Devices"
   - Private or Public (recommend Private for commercial)

2. **Add remote and push:**
```bash
git remote add origin https://github.com/your-username/smart-parking-sunmi.git
git branch -M main
git push -u origin main
git push origin develop
```

### Option B: GitLab

1. **Create project on GitLab**

2. **Add remote and push:**
```bash
git remote add origin https://gitlab.com/your-username/smart-parking-sunmi.git
git branch -M main
git push -u origin main
git push origin develop
```

### Option C: Local Git Only (No Remote)
Skip remote setup. Your repository will work locally for version control.

---

## 🏷️ Tag Initial Release

```bash
git tag -a v1.0.0 -m "Smart Parking v1.0.0 - Initial Production Release

Features:
- Universal SUNMI device support
- Automatic printing integration
- Offline-first architecture
- Smart tariff calculation
- Real-time dashboard
- Complete documentation

Tested on: V2, V2s, V2s Plus, T2, T3"

git push origin v1.0.0
```

---

## 🔄 Daily Development Workflow

### Starting a New Feature
```bash
# Make sure develop is up to date
git checkout develop
git pull origin develop

# Create feature branch
git checkout -b feature/monthly-membership

# Work on your feature...
# (make changes, test)

# Stage and commit
git add .
git commit -m "feat(membership): add monthly membership management UI"

# Push to remote
git push origin feature/monthly-membership

# Create Pull Request on GitHub/GitLab
# After PR approval, merge to develop
```

### Fixing a Bug
```bash
git checkout develop
git checkout -b bugfix/printer-timeout

# Fix the bug, test

git add .
git commit -m "fix(hardware): increase printer connection timeout to 10s"
git push origin bugfix/printer-timeout
```

### Hotfix for Production
```bash
git checkout main
git checkout -b hotfix/v1.0.1-payment-crash

# Fix critical bug

git add .
git commit -m "fix(payment): prevent crash when payment method is null"

# Merge to main
git checkout main
git merge --no-ff hotfix/v1.0.1-payment-crash
git tag -a v1.0.1 -m "Hotfix: Payment crash fix"

# Merge back to develop
git checkout develop
git merge --no-ff hotfix/v1.0.1-payment-crash

# Push everything
git push origin main develop --tags
```

---

## 📊 Useful Git Commands

### Check Status
```bash
git status
```

### View Commit History
```bash
git log --oneline --graph --all
```

### View Changes
```bash
git diff                    # Unstaged changes
git diff --staged           # Staged changes
git diff develop            # Compare with develop branch
```

### Undo Changes
```bash
git restore file.kt         # Discard changes in working directory
git restore --staged file.kt # Unstage file
git reset HEAD~1            # Undo last commit (keep changes)
git reset --hard HEAD~1     # Undo last commit (discard changes)
```

### Stash Work in Progress
```bash
git stash                   # Save current work
git stash list              # View stashed changes
git stash pop               # Apply and remove latest stash
```

### View Branches
```bash
git branch                  # Local branches
git branch -r               # Remote branches
git branch -a               # All branches
```

### Delete Branch
```bash
git branch -d feature/old-feature    # Delete local branch
git push origin --delete feature/old-feature  # Delete remote branch
```

---

## 🔐 .gitignore Verification

### Check What Git Ignores
```bash
git status --ignored
```

### Test if File is Ignored
```bash
git check-ignore -v app/build/outputs/apk/release/app-release.apk
```

---

## 📋 Pre-Commit Checklist

Before every commit, verify:
- [ ] Code compiles: `./gradlew build`
- [ ] Tests pass: `./gradlew test`
- [ ] Lint passes: `./gradlew lint`
- [ ] No debug code or `TODO` comments committed
- [ ] No secrets or API keys committed
- [ ] Commit message follows conventional commits format

---

## 🚀 Release Workflow

### Preparing a Release

1. **Create release branch:**
```bash
git checkout develop
git checkout -b release/v1.1.0
```

2. **Update version numbers:**
   - `app/build.gradle.kts`: Update `versionCode` and `versionName`
   - `CHANGELOG.md`: Move unreleased items to v1.1.0 section

3. **Commit version bump:**
```bash
git add app/build.gradle.kts CHANGELOG.md
git commit -m "chore: bump version to v1.1.0"
```

4. **Merge to main:**
```bash
git checkout main
git merge --no-ff release/v1.1.0
git tag -a v1.1.0 -m "Release v1.1.0"
```

5. **Merge back to develop:**
```bash
git checkout develop
git merge --no-ff release/v1.1.0
```

6. **Push everything:**
```bash
git push origin main develop --tags
```

---

## 🔍 Troubleshooting

### Merge Conflicts
```bash
# During merge, if conflicts occur
git status                  # See conflicted files

# Edit files to resolve conflicts
# Remove conflict markers (<<<<, ====, >>>>)

git add resolved-file.kt
git commit -m "merge: resolve conflicts in XYZ"
```

### Accidentally Committed to Wrong Branch
```bash
# If committed to main instead of feature branch
git reset HEAD~1            # Undo commit (keep changes)
git checkout -b feature/my-feature
git add .
git commit -m "feat: your feature"
```

### Discard All Local Changes
```bash
git reset --hard origin/develop
```

---

## 📦 Backup Strategy

### Create Backup Tag
```bash
git tag backup-2026-09-13
git push origin backup-2026-09-13
```

### Export Project Bundle
```bash
git bundle create smart-parking-backup.bundle --all
```

### Restore from Bundle
```bash
git clone smart-parking-backup.bundle restored-project
```

---

## 🤝 Team Collaboration

### Pull Latest Changes
```bash
git checkout develop
git pull origin develop
```

### Review Someone's Branch
```bash
git fetch origin
git checkout feature/their-feature
```

### Sync Fork with Upstream
```bash
git remote add upstream https://github.com/original-org/smart-parking.git
git fetch upstream
git checkout develop
git merge upstream/develop
git push origin develop
```

---

## 📈 Repository Statistics

### View Contributor Stats
```bash
git shortlog -sn
```

### View File History
```bash
git log --follow --patch -- app/src/main/java/com/smartparking/app/MainActivity.kt
```

### Find Commit by Message
```bash
git log --grep="printer"
```

---

## ✅ Next Steps After Git Setup

1. ✅ Repository initialized
2. ✅ `.gitignore` configured
3. ✅ Initial commit created
4. ✅ Branches set up (main, develop)
5. ✅ Tagged v1.0.0
6. ⬜ Push to remote (GitHub/GitLab)
7. ⬜ Set up CI/CD pipelines (optional)
8. ⬜ Configure branch protection rules
9. ⬜ Add team collaborators

---

## 📞 Support

For Git-related questions:
- Official Git documentation: https://git-scm.com/doc
- GitHub guides: https://guides.github.com/
- GitLab tutorials: https://docs.gitlab.com/ee/tutorials/

**Happy coding! 🚀**
