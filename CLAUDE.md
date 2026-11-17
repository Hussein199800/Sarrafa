# نظام الصرافة الذكي — Smart Exchange

Arabic (RTL) single-page web app for a currency-exchange shop. Plain HTML/CSS/JS, no build step, no package manager. Same owner and same conventions as Work_1 (https://github.com/Hussein199800/Work_1) — when in doubt, do what Work_1 does.

## ⚠️ RULE #1 — NEVER LOSE THE USER'S DATA
- Data lives in IndexedDB (`smart_exchange`: stores `records`, `meta`) on each device, plus the optional Firebase copy. Never delete the database, never rename stores or record `type`s, never `localStorage.clear()`.
- Every record is additive and backward compatible: read old shapes as they are, default missing fields at read time.
- All writes go through `commit()` (lock across tabs → reload → validate → one IndexedDB transaction). Balances/profits are NOT stored; they are derived by `SXEngine.replay()` (`engine.js`).
- Keep the backup format (`format: "smart-exchange-backup"`, `version: 1`) able to read older files.
- Before pushing anything that touches loading/saving/records/sync: ask the user to download a backup first.

## Files
- `index.html` — shell: login, sidebar (`.tab-btn` with `data-sec`), mobile bar, `#main`.
- `style.css` — design system «الجواهر» copied from Work_1 (tokens at the top, light + dark).
- `script.js` — UI, storage, sync, reports. Sections separated by `/* ===== عنوان ===== */` banners.
- `engine.js` — pure accounting (Decimal.js); works in Node for `tests/engine.test.js`.
- `tests/engine.test.js`, `tests/smoke.mjs` (Playwright; includes two phones on the same account with a fake Firebase).

## Design system (from Work_1 — keep it)
- Font: **IBM Plex Sans Arabic** for everything (Google Fonts), base 16px. Icons: **Tabler** webfont (`<i class="ti ti-…">`).
- Colors «الجواهر»: emerald for actions (`--accent`, `--grad-primary`), royal plum for structure (dark `--panel` sidebar, toasts), wine/burgundy for danger and money gradient (`--grad-money`), muted gold for totals and the active nav icon. No white/blue look. Profit = `--success`, loss = `--danger`.
- Radii 10/14/20/24 (`--r-*`), soft shadow `--sh-1`. Three breakpoints only: 1280 / 1024 / 640.
- Light/dark: `[data-theme]` on `<html>` set before render, pref key `sx_theme`.
- Navigation (user decision 2026-11-05: "different from Work_1's sidebar", like the «المزيد» page of the user's app «طاقتي» — repo Hussein199800/Car): NO sidebar. Sticky blurred top bar (brand, accounting day, sync dot, theme) + fixed bottom tab bar with 5 tabs (الرئيسية، صرافة، السجل، الصندوق، المزيد). Everything else lives in the «المزيد» page (`renderMore`): gradient user card on top, then colored list cards (`listCards`, `.lcard` + `.l-ic c-*` gradient icon tiles + chevron), logout button and a footer. Sub-pages opened from «المزيد» show a back button in the top bar. A badge on «المزيد» counts things needing attention (`moreAlerts`).
- Page header = `.page-title` with `.page-icon` (emerald tile) and a double rule; KPI tiles = `.kpi` (like Work_1 `.stat-card`, colored dot + tinted gradient); important money totals = `.kpi.money` (wine gradient).
- Messages via `toast()` / `confirmBox()` / `promptBox()` / `modal()` — never `alert()` / `confirm()`.
- Numbers: Western digits; numeric inputs are `type="text" inputmode="decimal" class="num-in"`; `E.parse()` accepts Arabic-Indic digits and thousands separators.

## Domain decisions (by the user)
- Currencies in display order: الليرة السورية الجديدة (SYP, display currency) → الدولار (USD, accounting base) → الليرة التركية (TRY) → اليورو (EUR, optional, can be disabled).
- Accounting stays in USD; amounts are shown lira first, dollar second ("الليرة أولاً بالعرض فقط").
- The admin account must work on several phones at the same time: login is local per device; data is shared through **Firebase** (Google sign-in + Firestore rules with an email list), exactly like Work_1. The user wants a **new dedicated Firebase project** for this system (in progress — put its config in `FB_DEFAULT_CONFIG`). Rules: `firestore.rules`.

## «جرد الأرباح» (2026-11-08)
- Section `pcount` (`renderProfitCount`), opened from «المزيد». Per currency: opening (morning) and closing (actual) balance + a rate the user types by hand (same orientation as `costOrient`: «ل.س لكل 1$», «$ لكل 1€»). Never read from the rates page.
- Saved as records of type `profitcount` (`pcCompute` / `pcReport`). This type is NOT in the engine ledger, so it never touches balances, average cost or accounting profits. Saved with `commit(..., {validate:false})`, soft delete with audit, print/Excel via `printReport` / `exportReportXlsx`.

## Review fixes (Codex review of 99caf77, 2026-11-17)
- B03 `E.newNegatives`: rejects a change that makes the worst balance of a currency worse, or a new negative that doesn't improve the worst; fixes that reduce a deficit are allowed.
- B04 `commit(fn)`: `changes` may be a function of the latest records, computed inside the lock. Settings save = `mergeSettings(latest, base, edited)` (only fields the user changed; same field changed elsewhere ⇒ conflict error). `ui.settingsBase` keeps the page's base across soft re-renders, reset by `go()` and after save. Users via `saveUsers(mutate)`. Exchange edit keeps `baseUpdatedAt`; reopen checks the close's `updatedAt`.
- B05 `curReferenced(code)`: a currency code (and its pricing strength) is locked once any record refers to it, opening balance included.
- B06: replace target / opening / profit count reject more decimals than the currency allows (never round silently).
- B07/B08: `fbOnUser` restarts listening on every enable; `fbPushNow()` returns `{ok, error}`; «مزامنة الآن» shows success only when nothing is left pending.
- B09: `renderSoft` keeps unsaved inputs (`snapshotFields` / `restoreFields`).
- U01 meta row on two lines ≤640; U02 modal focus trap + `inert` background + focus return + `aria-labelledby`; U03 `#syncBadge` is a button with icon + short text + aria-label, tap → `showSyncInfo()`.
- NOT done yet (needs the dedicated Firebase project + Emulator): B01 conditional cloud writes (revision/transaction) and B02 cross-device overdraft policy. Until then multi-phone use can still produce a negative balance (it is flagged on the dashboard) and a stale offline push can overwrite a newer cloud copy.

## Conventions
- The user writes in Arabic; answer in Arabic.
- Ask the user ONLY with the clickable question bar (AskUserQuestion), **in Arabic**, options with the recommended one first — never a numbered text list of questions.
- Develop on the session branch, the user merges.
- Verify: `node --check script.js`, `node tests/engine.test.js`, `node tests/smoke.mjs`.
