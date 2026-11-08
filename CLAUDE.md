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

## Conventions
- The user writes in Arabic; answer in Arabic.
- Ask the user ONLY with the clickable question bar (AskUserQuestion), **in Arabic**, options with the recommended one first — never a numbered text list of questions.
- Develop on the session branch, the user merges.
- Verify: `node --check script.js`, `node tests/engine.test.js`, `node tests/smoke.mjs`.
