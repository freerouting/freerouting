# Managing Donors, Sponsors, and Community Patrons

Freerouting is an independent open-source PCB autorouter maintained for the global electronics engineering community. This document outlines how community donations and sponsorships are curated, and provides step-by-step instructions for the maintainer (Andras) to update the donor list.

---

## 1. Moderation & Curation Principles

To ensure that only appropriate, respectful, and verified names appear in Freerouting, donor additions are **not automated directly from payment webhooks**. Instead, the maintainer manually reviews each donation:

1. **Vetting for Appropriateness**: Ensure names, handles, or corporate entities do not contain profanity, spam, offensive pseudonyms, or malicious URLs.
2. **Privacy & Anonymity**: If a contributor requests anonymity, respect their privacy and do not publish their name.
3. **Optional Links**: For qualifying corporate or sustaining sponsors, an optional external homepage link (`url`) may be included.

---

## 2. Dynamic Single-Source-of-Truth Architecture

Freerouting uses a single source of truth for both the project website and all desktop client installations:

- **Source File**: [`website/donors.json`](../website/donors.json)
- **Website Rendering**: [`website/donate.html`](../website/donate.html) dynamically loads and displays the donor list from `donors.json`.
- **Desktop Application Rendering**: In the Freerouting Swing desktop application, clicking **Help &rarr; Sponsor Freerouting** opens `WindowSponsor`. The dialog uses `DonorService` to asynchronously fetch `donors.json` from the repository over HTTPS.
- **Offline Fallback**: If the user is offline or the network request times out (3-second limit), `WindowSponsor` immediately falls back to the bundled resource copy at `src/main/resources/app/freerouting/gui/windows/board/donors.json`.

> [!IMPORTANT]
> **Zero Client Update Required:**
> Because the desktop client fetches the latest list dynamically over HTTPS, users do **not** need to update their Freerouting version to see newly added donors. Whenever a new commit to `website/donors.json` lands on `master`, all active desktop installations will automatically display the new donors the next time the window is opened.

---

## 3. How to Add a New Donor (Maintainer Workflow)

When a new contribution is received via Stripe, GitHub Sponsors, Alipay, or other supported channels:

### Step 1: Open `website/donors.json`

Locate the target sponsorship tier inside [`website/donors.json`](../website/donors.json):

```json
{
  "updated": "2026-10-26",
  "tiers": [
    {
      "name": "Gold & Silver Sponsors",
      "donors": [
        {
          "name": "Foxy Laboratories",
          "url": "https://www.foxylabs.eu/"
        }
      ]
    },
    {
      "name": "Community Backers & Patrons",
      "donors": [
        "Jane Doe",
        {
          "name": "Acme Hardware Labs",
          "url": "https://example.com"
        }
      ]
    }
  ]
}
```

### Step 2: Add the Donor Entry

You can add donors either as:
- **An object with an optional link**:
  ```json
  {
    "name": "Company Name or Sponsor",
    "url": "https://example.com"
  }
  ```
- **A simple plain-text string**:
  ```json
  "Contributor Name"
  ```

Update the `"updated"` date field at the top of the file to today's date (`YYYY-MM-DD`).

### Step 3: Synchronize the Bundled Fallback Copy

Copy the updated `website/donors.json` to the internal resource directory so subsequent offline builds ship with the latest list:

```bash
cp website/donors.json src/main/resources/app/freerouting/gui/windows/board/donors.json
```
*(On Windows PowerShell: `Copy-Item website/donors.json src/main/resources/app/freerouting/gui/windows/board/donors.json`)*

### Step 4: Commit and Push to Master

```bash
git add website/donors.json src/main/resources/app/freerouting/gui/windows/board/donors.json
git commit -m "chore(donors): add [Donor Name] to community sponsors"
git push origin master
```

### Step 5: Verification

- Open [`https://www.freerouting.app/donate.html`](https://www.freerouting.app/donate.html) to verify the new donor badge appears under the honor roll.
- Launch Freerouting desktop and click **Help &rarr; Sponsor Freerouting** to confirm the updated donor is visible.
