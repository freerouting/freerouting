# Freerouting Donation and Support Channels Plan

**Document Status:** Approved for Implementation  
**Date:** August 2026  
**Target Branch:** `feat/donation-and-support-channels`  
**Corporate Entity:** Estonian OÜ (EU VAT compliant, SEPA, Stripe EU)

---

## 1. Executive Summary & Demographic Baseline

Based on July 2026 telemetry and historical GitHub Sponsors data:
* **Monthly Active Reach:** **452,754 routing sessions/month** across >100 countries.
* **Geographic Distribution:**
  * **North America & Anglosphere (~68%):** US (58.6%), UK (2.7%), NZ (1.8%), AU (1.6%), CA (0.7%). High affinity for Apple Pay, Google Pay, credit cards, and GitHub Sponsors.
  * **China & Sinosphere (~8.5%):** CN (7.6%), TW, HK, SG (>38,000 monthly sessions). High friction with Western payment cards; requires **Alipay & WeChat Pay** via Stripe.
  * **Europe (~8.0%):** DE (2.3%), FR (1.0%), ES, IT, PL, NL. Prefer SEPA Direct Debit, iDEAL, Giropay, and cards.
  * **East Asia & Others (~15.5%):** JP (2.0%), KR (1.3%), IN (2.0%), BR (0.7%), RU (1.5%).
* **Historical Sponsor Conversion Patterns:**
  * **One-time donations:** Accounted for **~81% of all individual sponsors**. Median donation is **$20**, with prominent spikes at **$10, $20, and $100**.
  * **Recurring sponsorships:** 7 patrons ($5/mo typical for makers, $10/mo for pros, $60/yr, and $100–$300/mo corporate tier).

---

## 2. Donation Tier Architecture

### A. One-Time Donation Tiers (Default User Path)

| Amount | Tier Name | Icon | Intent & Copy |
| :---: | :--- | :---: | :--- |
| **$10** | **Coffee Backer** | ☕ | *"Buy a coffee to fuel the next late-night routing session."* |
| **$25** | **Board Enthusiast** *(Recommended)* | 🔌 | *"Support a smooth routing experience on your latest hardware build."* |
| **$50** | **Power Router** | ⚡ | *"Power active bugfixes, performance tuning, and new algorithmic features."* |
| **$100** | **Open Source Champion** | 🚀 | *"Fund substantial AI development credits and infrastructure for the community."* |
| **$250+** | **Benefactor** | 💎 | *"Accelerate high-impact R&D, major releases, and long-term sustainability."* |
| **Custom** | **Custom Amount** | ✍️ | *Donor-defined amount ($5 minimum).* |

### B. Recurring Membership Tiers (Monthly & Annual)

| Monthly | Annual (Save ~15%) | Tier Name | Target Audience & Perks |
| :---: | :---: | :--- | :--- |
| **$5 / mo** | **$50 / yr** | 🌱 **Supporter** | • Individual makers / hobbyists<br>• Supporter badge on GitHub & listing on website Supporters Wall. |
| **$15 / mo** | **$150 / yr** | 🛠️ **Hardware Pro** | • Freelancers / active hardware engineers<br>• Priority issue triage & early-access preview builds. |
| **$50 / mo** | **$500 / yr** | 🛡️ **Project Patron** | • Small hardware studios / power teams<br>• Named mention in release notes + priority feature consideration. |
| **$200 / mo** | **$2,000 / yr** | 🏢 **Corporate Sponsor** | • EDA / PCB tool providers & manufacturing companies<br>• Prominent logo on README, website header, and About dialog.<br>• Official EU VAT-compliant B2B reverse charge invoices. |

---

## 3. Payment Gateway & Legal Architecture

* **Primary Processor:** **Stripe EU** (via Estonian OÜ).
  * **Supported Methods:** Apple Pay, Google Pay, Credit/Debit Cards, SEPA Direct Debit, iDEAL, Giropay, **Alipay**, and **WeChat Pay**.
  * **Settlement:** Converted automatically to EUR and deposited directly to the Estonian company IBAN (LHV / Wise Business).
* **Secondary Channel:** **GitHub Sponsors** (0% fee for individuals, high trust for GitHub users).
* **Tertiary / Fallback:** **PayPal** (for users without card/wallet access).
* **B2B / Direct Wire Transfers:** Direct SEPA / SWIFT bank transfers for corporate sponsors requiring formal vendor onboarding and reverse-charge VAT invoicing.

---

## 4. Execution Phases & Task Lists

```
┌─────────────────────────────────────────────────────────────┐
│ Phase 1: Stripe & Payment Gateway Setup                     │
├─────────────────────────────────────────────────────────────┤
│ Phase 2: Dedicated Support Hub Webpage (freerouting.app)    │
├─────────────────────────────────────────────────────────────┤
│ Phase 3: Desktop GUI & CLI Integration                      │
├─────────────────────────────────────────────────────────────┤
│ Phase 4: Localization, Telemetry & Verification Gates       │
└─────────────────────────────────────────────────────────────┘
```

---

### Phase 1: Stripe & Payment Gateway Setup

**Objective:** Configure payment links, wallets, and Chinese payment methods under the Estonian OÜ Stripe account.

- [ ] **1.1 Activate Global & Regional Payment Methods in Stripe Dashboard**
  - [ ] Enable **Apple Pay** and **Google Pay** (domain verification on `freerouting.app`).
  - [ ] Enable **Alipay** and **WeChat Pay** under Stripe payment method settings.
  - [ ] Enable **SEPA Direct Debit**, **iDEAL**, and **Bancontact** for European donors.
  - [ ] Enable **China UnionPay** card support.
- [ ] **1.2 Generate Stripe Payment Links**
  - [ ] Create one-time payment links: `$10`, `$25`, `$50`, `$100`, and dynamic custom amount.
  - [ ] Create recurring payment links: `$5/mo` (`$50/yr`), `$15/mo` (`$150/yr`), `$50/mo` (`$500/yr`), and `$200/mo` (`$2,000/yr`).
  - [ ] Configure success and cancel redirect URLs back to `https://freerouting.app/donate/thank-you`.
- [ ] **1.3 Configure Payouts and Tax/Invoicing**
  - [ ] Link Estonian OÜ IBAN (LHV / Wise Business) for automatic EUR payouts.
  - [ ] Enable automated email receipts and Stripe Tax for EU VAT compliance.

**Acceptance Criteria for Phase 1:**
* Stripe payment links generate valid checkout sessions supporting Cards, Apple Pay, Google Pay, Alipay, WeChat Pay, and SEPA.

---

### Phase 2: Dedicated Support Hub Webpage (`freerouting.app/donate`)

**Objective:** Deploy a fast, responsive, and trustworthy donation landing page that converts both Western and Chinese traffic.

- [ ] **2.1 Landing Page Architecture & Design**
  - [ ] Create `/donate` (or `/sponsor`) page with clear value proposition and impact metrics (450k+ monthly sessions, active algorithmic development, AI API compute costs).
  - [ ] Implement responsive UI with two top-level tabs: **One-Time Support** (default) and **Monthly / Annual Membership**.
  - [ ] Embed preset tier buttons (`$10`, `$25`, `$50`, `$100`, Custom) with instant checkout triggers.
- [ ] **2.2 Multi-Gateway Integration**
  - [ ] Stripe Elements / Payment Links integration for 1-click Apple Pay, Google Pay, and Cards.
  - [ ] Prominent WeChat Pay & Alipay badges with automatic QR display for Chinese visitors.
  - [ ] Dedicated buttons for **GitHub Sponsors** and **PayPal**.
  - [ ] B2B Corporate Sponsorship callout: *"Need a corporate VAT invoice or direct bank transfer? Contact us."*
- [ ] **2.3 Post-Donation Thank You Page**
  - [ ] Create `/donate/thank-you` with social share buttons, Discord / GitHub community links, and backer acknowledgement.

**Acceptance Criteria for Phase 2:**
* `freerouting.app/donate` renders cleanly on mobile and desktop, processes test and live payments across all supported payment rails, and routes users to the thank-you screen.

---

### Phase 3: Desktop GUI & CLI Integration

**Objective:** Direct in-app users to the unified support page at high-satisfaction touchpoints.

- [ ] **3.1 Unified Donation URL Configuration**
  - [ ] Define canonical donation URL constant (e.g. `https://freerouting.app/donate`) across all application entry points.
- [ ] **3.2 GUI Help & Menu Enhancements**
  - [ ] Update `WindowAbout.java` to feature a prominent "💖 Sponsor / Donate" button and updated project reach statistics.
  - [ ] Add a "Sponsor Freerouting..." item under `BoardMenuHelp.java`.
  - [ ] Update `WindowUserSettings.java` profile dialog sponsor section with the new unified URL and tier overview.
- [ ] **3.3 Post-Routing Success State Hook (High Conversion)**
  - [ ] Implement an unobtrusive post-routing completion banner/notification when a route completes with **100% completion and 0 DRC violations**.
  - [ ] Include a gentle link: *"🎉 Board successfully routed! Support Freerouting development: [Donate]"*.
- [ ] **3.4 CLI Mode Console Banner**
  - [ ] Refine `Freerouting.java` console banner shown after successful batch routing runs.
  - [ ] Respect `--quiet` and `--silent` flags so scripting/CI pipelines are never polluted.

**Acceptance Criteria for Phase 3:**
* Clicking sponsor links in GUI opens the canonical `/donate` page in default browser.
* CLI outputs clean, non-intrusive support link only on interactive/normal successful runs.
* Unit tests for GUI menus and CLI output formatting remain 100% green.

---

### Phase 4: Localization, Telemetry & Quality Gates

**Objective:** Localize all new UI text across supported languages and verify code quality.

- [ ] **4.1 Internationalization (i18n)**
  - [ ] Update resource bundles (`WindowUserSettings_*.properties`, `WindowAbout_*.properties`, `BoardMenuHelp_*.properties`) for all 20+ supported languages (en, zh_CN, de, ja, fr, es, it, pt_BR, ko, ru, etc.).
  - [ ] Run `python scripts/i18n/extract-context.py --check` to ensure full translation coverage.
- [ ] **4.2 Privacy-Respecting Conversion Telemetry**
  - [ ] Track anonymous click events (e.g. `FRAnalytics.buttonClicked("sponsor_button", source)`) to measure conversion from GUI About, Profile, Post-Route toast, and CLI.
- [ ] **4.3 Verification & Quality Gates**
  - [ ] Run `./gradlew spotlessCheck checkstyleMain checkstyleTest` (or `gradlew.bat` on Windows).
  - [ ] Run `./gradlew test` to ensure all unit and accessibility tests pass.
  - [ ] Verify no regressions against ArchUnit architecture boundary checks (`ModuleBoundariesArchTest`).

**Acceptance Criteria for Phase 4:**
* Spotless, Checkstyle, and all automated tests pass without errors or warnings.
* All translations load cleanly without missing key fallbacks.

---

## 5. Summary Timeline & Milestones

| Milestone | Deliverables | Verification |
| :--- | :--- | :--- |
| **M1: Stripe & Gateway Ready** | Payment links created; Apple Pay, WeChat Pay, Alipay, SEPA active. | Test transactions verified in Stripe Sandbox/Live. |
| **M2: Support Webpage Live** | `freerouting.app/donate` deployed with multi-tier widget and B2B callout. | End-to-end checkout verified across mobile & desktop. |
| **M3: App Touchpoints Updated** | GUI menus, About window, Settings dialog, and CLI banner wired to `/donate`. | GUI manual smoke tests + MenuActionA11yTest green. |
| **M4: Full i18n & Release** | All 20+ locale files updated; full test suite and quality gates green. | `./gradlew check` passes on `feat/donation-and-support-channels`. |
