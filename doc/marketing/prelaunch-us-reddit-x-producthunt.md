# LumaVault — U.S. pre-launch plan (Reddit, X, Product Hunt)

**Audience:** United States (first), then expand messaging as you add regions.  
**Platform rollout:** **Google Play (Android) first** → **Apple App Store (iOS) later** on the same product story. Say **“Android first”** publicly until you have a firm iOS ship date; avoid guessing dates.

**Assumptions:** No beta waitlist required; landing page optional but recommended (`/landing/` — update it when iOS is near to add an App Store badge / “Coming soon on iOS”).  

**Language:** All copy below is **English** (ready to paste).

**Replace placeholders:**  
`[PLAY LINK]` · `[APP STORE LINK]` (when live) · `[LANDING URL]` · `[X_PROFILE_URL]` · `[IOS_PUBLIC_NOTE]` (e.g. `iOS is in development—join updates on X` or `App Store expected Q4 2026` **only if true**)

---

## Platform story (use everywhere)

One sentence you can repeat:

> **LumaVault** is an offline-first encrypted photo/video vault. We’re shipping **Google Play (U.S.) first**; **iOS on the App Store is planned next**—same privacy posture, platform-native implementation.

Do **not** imply feature parity or ship dates for iOS until engineering and App Review reality match what you write.

---

## How to use this doc

| Channel          | What “weekly” means |
|------------------|---------------------|
| **Reddit**       | ~1 substantive post or thread per week (quality over quantity). Early weeks skew **Android**; add **iOS-leaning** subs only when you have TestFlight or a real App Store link. |
| **X**            | ~5 posts/week. After Play launch, keep **one recurring line** about iOS roadmap so Android users don’t feel bait-and-switched. |
| **Product Hunt** | **Not** a weekly feed like Reddit/X. “Pre-warm” = **prep + subscribers + launch-day assets**. First PH launch usually aligns with **your biggest public milestone** (often **Play** or **first cross-platform** drop—pick one story and stick to it). |

---

## Product Hunt: should you pre-warm?

**Yes, but differently.** Product Hunt rewards a **clear launch story**. For a **Play-first** product:

- **Option A (common):** Launch PH on **Google Play (U.S.) go-live day** (or the week of). Story = “Android ships first; iOS next.”  
- **Option B:** Wait until **iOS + Android** are both live if you want a single “everywhere” narrative—**only** if you won’t leave PH silent for months and you accept slower early traction on PH.

This doc assumes **Option A** unless you revise.

PH pre-launch work:

1. **Maker profile** (photo, bio, X + landing).  
2. **Assets**: 60s demo / GIF, 5 gallery shots (**Android UI** for v1).  
3. **Ship** (optional): “Android first, iOS next” + notify.  
4. **Launch day (PT)** + credible hunter + **no vote brigading**.  
5. **First comment** states **Play link today**, **App Store later** (no fake dates).

---

## 8-week calendar (high level)

| Week | Reddit | X | Product Hunt (prep) |
|------|--------|---|------------------------|
| 1 | Privacy workflow (Android-first build) | Trust + threat model + “Android first, iOS next” once | PH maker profile |
| 2 | Local vs cloud trust | Philosophy + Play Data safety / App Privacy (later) | Tagline + 3 bullets (mention roadmap once) |
| 3 | Android dev foot-guns | Encryption + UX | Gallery list (Android screenshots) |
| 4 | Cleanup without cloud | On-device AI | Demo draft (Android) |
| 5 | Vault UX friction | Poll + screenshots | Finalize demo / GIF |
| 6 | Backup threat model | Backup narrative | First comment + FAQ (**billing**, **what uploads**) |
| 7 | “Why install?” proof | Countdown **Play** | Schedule PH; hunter; Ship optional |
| 8 | **Live:** Play link + **iOS planned** | Launch thread | **PH launch day** — paste kit |

---

# Reddit — weekly posts (copy/paste)

**Rules:** Read each subreddit’s sidebar before posting. If self-promotion is restricted, shorten product mentions to “an app I’m building” and add details in comments only.

---

## Week 1 — r/privacy or r/PrivacyGuides (discussion)

**Title:**  
What’s your non-negotiable for a “sensitive photos” workflow on Android?

**Body:**  
I’m trying to design around a simple rule: **the gallery never becomes a cloud sync problem by accident.**

For people in the US who keep IDs, finance screenshots, medical photos, or anything you wouldn’t want in a generic backup pipeline—what do you actually do today?

- Separate folder + manual discipline?  
- A second gallery app?  
- Turning off backup entirely (and accepting the tradeoff)?  

I’m building **LumaVault**, an **offline-first** vault idea: encrypt on-device, biometric lock, and **no routine uploads of your media** as part of the core design. **Ship order:** **Google Play (Android) first**, then **iOS on the App Store** afterward (same product goals; dates TBD until announced). More context: `[LANDING_URL]` (if live).

What’s the **one** requirement you wouldn’t compromise on?

---

## Week 2 — r/androidapps

**Title:**  
Do you trust “local-only” apps more than “privacy-focused” cloud apps?

**Body:**  
Honest question for US Android users: when an app says “privacy,” do you default to assuming **some** data leaves the device unless proven otherwise?

I’m working on **LumaVault** (encrypted local vault + on-device helpers like cleanup/redaction concepts). Still pre-launch on **Google Play**—**iOS App Store later**. Details: `[LANDING_URL]`

If you had to pick one statement you want to be true, which is closer to you?

1) **Local encryption + explicit exports only**  
2) **Convenience sync**, but strong policies + audits  

Curious what you optimize for in real life (not in theory).

---

## Week 3 — r/androiddev

**Title:**  
Offline encrypted media vault on Android: what’s the biggest foot-gun?

**Body:**  
Building an **offline-first** media vault (AES-GCM style file encryption, Keystore-backed keys, Room metadata). Trying to avoid the classic traps:

- accidental cloud backup of plaintext paths/thumbnails  
- confusing “deleted from gallery” vs “still on disk”  
- restore/backup UX that nudges users toward unsafe shortcuts  

For devs who’ve shipped similar apps: **what mistake created the most support tickets or bad reviews?**

Product: **LumaVault** (consumer-facing). **Android ships first; iOS next.** Context: `[LANDING_URL]`

---

## Week 4 — r/DataHoarder or r/privacy

**Title:**  
How do you handle duplicate / blurry / junk photos without sending your library to the cloud?

**Body:**  
My camera roll is half “maybe useful” and half “why did I take 12 burst shots of a receipt.”  

I want **LumaVault** to help with **on-device** cleanup/classification ideas (nothing that requires uploading your library for “smart” features). **v1 is targeting Google Play (U.S.) first**; iOS comes after. More: `[LANDING_URL]`

What’s your current workflow in the US—**manual**, **local tools**, or **you just gave up**?

If you tried a “smart cleanup” app before, what made you uninstall it?

---

## Week 5 — r/androidapps

**Title:**  
Vault apps: do you want “maximum security UX” or “it should feel like a normal gallery”?

**Body:**  
Pre-launch feedback request (US audience): for a locked vault, which UX do you prefer?

- **A)** Extra friction everywhere (confirmations, strict modes)  
- **B)** Friction only on destructive actions, otherwise feels like Photos  

I’m building **LumaVault** and trying not to ship “security theater,” but also not ship “oops I exported the wrong thing.” **Android first, iOS later.** Context: `[LANDING_URL]`

Reply with **A/B** and **one** feature you’d insist on for v1.

---

## Week 6 — r/privacy

**Title:**  
Encrypted backup of a phone vault: what’s the threat model you actually use?

**Body:**  
When people say “backup,” it can mean anything from “copy to my laptop” to “sync folder that I forgot was syncing.”  

For **LumaVault**, I’m thinking: **encrypted backup packages** where recovery is intentional—not a silent cloud mirror of thumbnails. **Launching on Google Play (U.S.) first; iOS App Store afterward.** Details: `[LANDING_URL]`

What do **you** want to be true?

- backup should be **air-gapped friendly**  
- backup should be **dead-simple** even if less flexible  

---

## Week 7 — r/androidapps

**Title:**  
Launching on Google Play (US) in ~1 week—what makes you hit “Install” vs “Not now” for a new indie app?

**Body:**  
I’m shipping **LumaVault** soon on **Google Play (U.S.)** first: **offline encrypted vault** for photos/videos, biometric unlock, and features aimed at **local** cleanup/redaction workflows (see Play listing when live). **iOS / App Store is on the roadmap after Android.**

What proof do you look for on a cold install?

- permissions list  
- clear “what leaves the device” statement  
- screenshots that show the actual UI  
- open-source builds (asking what moves you—not claiming this)

Site: `[LANDING_URL]` — Play: `[PLAY LINK]` (if already public)

---

## Week 8 — r/androidapps (post-Play launch)

**Title:**  
LumaVault is live on Google Play (US) — offline encrypted vault for photos/videos (iOS next)

**Body:**  
**LumaVault** is now on **Google Play (U.S.)**: `[PLAY LINK]`

**Roadmap:** **iOS on the App Store is planned next**—`[IOS_PUBLIC_NOTE]` (keep this accurate; if you have no date, say “in development” only).

What it is:  
- **Encrypted on your device** (not a cloud photo service)  
- **Biometric / lock** workflows  
- Tools oriented around **local** organization + privacy workflows (see listing for specifics)

What it is not:  
- not trying to replace your entire camera roll sync stack overnight  

I’m an indie build—**blunt reviews help**. If something feels confusing in the first 60 seconds, tell me what screen you were on.

More context: `[LANDING_URL]`

---

# X (Twitter) — weekly posts (copy/paste)

**Cadence:** 5 posts/week.

**Footer templates (pick one per post):**

- **Pre-Play:** `More: [LANDING_URL] · Google Play (U.S.) first — iOS App Store later. Follow for updates.`  
- **Post-Play, pre-iOS:** `Android: [PLAY LINK] · iOS: [IOS_PUBLIC_NOTE] · [LANDING_URL]`  
- **Post both stores:** `Android: [PLAY LINK] · iOS: [APP STORE LINK] · [LANDING_URL]`

---

## Week 1

1. US Android folks: what’s the *one* photo category you’d never want caught in a generic cloud backup pipeline—IDs, finance, medical, “other”? I’m building **LumaVault**: offline-first, encrypted on-device. **Shipping Google Play (U.S.) first; iOS App Store next on the roadmap.** [LANDING_URL]

2. “Privacy policy” isn’t the same as “your pixels never leave the phone.” I care about the second one for **LumaVault**. What do *you* verify before trusting a gallery/vault app? [LANDING_URL]

3. Building in public: week 1 is threat modeling for normal humans—“my backup settings did *what*?” **Android build lands first; iOS follows.** [LANDING_URL]

4. Sensitive photos in the same camera roll as memes: workflow problem or tooling problem? I’m betting tooling—a dedicated encrypted vault. Play first, iOS after. [LANDING_URL]

5. Poll: For a vault app, what matters more? (a) Maximum local control (b) Convenience (more trust assumptions)

---

## Week 2

1. “Local-only” should be verifiable: permissions, network calls, defaults. That’s the bar for **LumaVault**. **Play (U.S.) first, App Store later.** [LANDING_URL]

2. If ML helps, it should be **on-device** and optional—not “upload your library for magic.” **LumaVault** [LANDING_URL]

3. Do you read **Data safety** (Android) / **App Privacy** (iOS) *before* install, or only after something feels sketchy? We’re Android-first, but I’m designing with both storefront disclosures in mind.

4. First-run copy should explain **what never uploads** in plain English. Still iterating. **LumaVault** [LANDING_URL]

5. Uninstalled a “privacy” app before—what was the moment?

---

## Week 3

1. Android: Keystore + per-file encryption is table stakes. The hard part is **UX**—restore, exports, “what did I just delete?” **LumaVault** ships **Play** first. [LANDING_URL]

2. Sensitive workflows shouldn’t need 17 toggles.

3. The scariest vault bugs are boring: thumbnails, providers, backup surprises. Building with that in mind—**iOS will get the same paranoia pass** when we port.

4. The Play listing will answer **“What leaves my device?”** early. **LumaVault** [LANDING_URL]

5. Reply with your #1 vault app pet peeve → my “do not ship” list.

---

## Week 4

1. Camera rolls fail because humans take 9 blurry photos and 1 good one. **On-device** cleanup ideas in **LumaVault**—**Google Play (U.S.) first**. [LANDING_URL]

2. “Smart cleanup” that requires uploading your whole library is a non-starter. Local-first or nothing.

3. Receipt screenshots deserve their own encrypted universe.

4. Junk photo strategy: manual / ignore / third-party?

5. **Second brain** for sensitive media—not a second cloud. **Android now, iOS next.** [LANDING_URL]

---

## Week 5

1. Should a vault feel like a **bank app** or a **gallery** with strict export gates? Debating defaults—**Play v1 first**. [LANDING_URL]

2. Poll: Export/share—(a) always multi-step confirm (b) fast but labeled “leaves the vault”?

3. Screenshot test: “What’s confusing in 5 seconds?” **LumaVault**

4. Pre-launch feedback = best prioritization. Reply or catch the Reddit thread. [LANDING_URL]

5. Ship **only three** v1 vault features—what are they? (I’ll compare to the roadmap, **Android first**.)

---

## Week 6

1. Backup is a **threat model** debate. **LumaVault**: encrypted packages, not silent mirrors. [LANDING_URL]

2. Google Photos can coexist. LumaVault is a **safe island**—first on **Android (U.S.)**, then **iOS**.

3. Restore UX earns trust—or loses it.

4. New phone: bigger fear—**losing data** or **leaking data**?

5. North star: **default local, explicit outbound.** [LANDING_URL]

---

## Week 7

1. ~1 week to **Google Play (U.S.)**. Permissions, first-run copy, honest screenshots. **iOS App Store after.** [LANDING_URL]

2. What makes you bounce in the first 30 seconds on a cold indie install?

3. One line: **Encrypted local vault for photos/videos—Android first in the U.S., iOS next.** [LANDING_URL]

4. Follow `[X_PROFILE_URL]` for the **Play** link drop (and later **App Store**).

5. Feature requests welcome—**Android v1** scope is tight, but I read everything.

---

## Week 8 (Play live + iOS roadmap)

1. **LumaVault** is live on **Google Play (U.S.)**: [PLAY LINK] — Offline-first encrypted vault + local privacy workflows. **iOS / App Store:** [IOS_PUBLIC_NOTE] [LANDING_URL]

2. Shipped on Play ≠ done. Feedback firehose starts now. Thank you for blunt reviews.

3. Check **Data safety** + permissions first. Lock/unlock—what felt wrong?

4. Roadmap: friction-driven improvements on Android **while iOS is in progress** (no feature bingo).

5. If you gave feedback: you’re on the **founding friction list** for v1.1 **and** the iOS port. Thank you.

---

# iOS App Store — later-phase prep (do not over-promise early)

Use this block when you are **~4–8 weeks from iOS submission** or when TestFlight exists. Until then, keep public copy to **“iOS in development / planned after Android.”**

## Landing page checklist (iOS)

- [ ] Add **Download on the App Store** badge (or “Coming soon on iOS”) + `[APP STORE LINK]` when approved  
- [ ] Add **App Privacy** summary link (Apple) alongside **Google Play Data safety**

## X — iOS proximity (examples, paste when true)

1. **LumaVault** iOS update: TestFlight is open / closed / next wave: [link or “DM for invite policy”]. Android: [PLAY LINK]

2. Porting notes: iOS has different photo picker + backup defaults—**we’re designing for the same “no accidental cloud vault” rule.** [LANDING_URL]

3. App Store review reality: ship dates move. **Android is live:** [PLAY_LINK] · **iOS:** [IOS_PUBLIC_NOTE]

## Reddit — iOS-leaning (only when credible)

**Title (example):**  
`[WIP] Encrypted offline photo vault coming to iOS after Android—what’s your #1 “vault app” dealbreaker?`

**Body (template):**  
**LumaVault** is live on **Google Play (U.S.)** today: `[PLAY_LINK]`. I’m working toward **iOS on the App Store** next with the same offline-first / local encryption posture (implementation details differ by platform).

What would make you **not** trust an iOS vault on day one?

---

# Product Hunt — weekly prep checklist (weeks 1–7)

Use as a **tick list**; not all items apply if you skip Ship.

### Week 1
- [ ] PH maker profile: photo, short bio, X + `[LANDING_URL]` (mention **Android first, iOS next** once in bio if character limit allows)

### Week 2
- [ ] **3 value bullets** (see Launch kit) + **one roadmap line** (iOS after Android—no date unless firm)
- [ ] **5 screenshots** (**Android** UI for this launch)

### Week 3
- [ ] **First comment** draft (Play link + iOS planned)
- [ ] PH topics: Android, Privacy, Photography, Indie Apps (pick what fits)

### Week 4
- [ ] **60s demo** on **Android** (import → lock → one local tool)
- [ ] GIF under current PH limits

### Week 5
- [ ] Thumbnail (verify current PH pixel spec in dashboard)
- [ ] Optional **Ship**: “Android (U.S.) first · iOS next”

### Week 6
- [ ] FAQ: billing (RevenueCat / Play / **future App Store**), what uploads, what doesn’t
- [ ] Calendar holds (Pacific) for launch day

### Week 7
- [ ] Schedule PH launch; confirm hunter
- [ ] Pre-write replies: “When iOS?” → paste **accurate** `[IOS_PUBLIC_NOTE]`

---

# Product Hunt — Launch kit (paste on launch day)

**Recommended story for this roadmap:** PH coincides with **Google Play (U.S.)** availability; **iOS is explicitly “next.”**

## Tagline options (pick one)

- `Encrypted photo & video vault—offline-first. Android now, iOS next.`  
- `Keep sensitive media off the accidental cloud path. (Play first)`  
- `Local encryption + biometric lock. Google Play (U.S.) first.`  

## Launch title

`LumaVault`

## Subtitle / short description

`Offline-first encrypted vault for photos & videos. Google Play (U.S.) first; iOS App Store next.`

## First comment (maker comment)

Hey Product Hunt — I’m the maker of **LumaVault**.

**What it is:** an **offline-first** vault that encrypts photos/videos **on your device**, with biometric lock and **local** workflows for cleanup + privacy tools (exact v1 scope is in the **Google Play** listing).

**Ship order:** **Google Play (United States) first.** **iOS on the App Store is planned next**—`[IOS_PUBLIC_NOTE]`.

**What it’s not:** a cloud photo service. I’m optimizing for sensitive screenshots not living in the same “sync everything” mental model as memes.

**Feedback I want:** first-run clarity on Android—permissions + “what leaves the device” (purchase flows are separate from vaulted media). When iOS ships, I’ll want the same bluntness on onboarding + iCloud/backup foot-guns.

**Links:**  
- Google Play: [PLAY LINK]  
- More context: [LANDING_URL]  
- X: [X_PROFILE_URL]

If you try the Android build: what confused you in the **first 2 minutes**?

— [Your name]

## Gallery captions (optional)

1. `Vault home after unlock (Android)`  
2. `Import / isolate sensitive items`  
3. `Local privacy workflow (see Play listing)`  
4. `Encrypted backup — intentional recovery`  
5. `Plain-English privacy posture`

## PH “Ship” page — short copy (optional)

**Headline:** `LumaVault — encrypted vault (offline-first)`  
**Body:** `Photos/videos encrypted on-device. Biometric lock. Local cleanup & privacy tools. Google Play (U.S.) first; iOS App Store next. Notify me.`  
**Button:** `Notify me`

---

## Product ethics (all channels)

- Do not claim “military-grade,” “100% secure,” or “zero risk.”  
- **Android:** point to **Google Play Data safety**.  
- **iOS (when live):** point to **App Store privacy labels** + your privacy policy URL.  
- **Cross-platform:** do not imply identical features or ship dates until true.  
- Avoid vote manipulation / brigading on PH or Reddit.

---

## File location

- This document: `doc/marketing/prelaunch-us-reddit-x-producthunt.md`  
- Static landing page (update for dual badges when iOS is ready): `landing/index.html` + `landing/styles.css`
