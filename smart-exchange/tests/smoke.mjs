/* اختبار آلي للواجهة في متصفح حقيقي — بيانات تجريبية فقط.
   التشغيل: node tests/smoke.mjs   (يحتاج Playwright) */
import { createRequire } from "module";
import fs from "fs";
import { createServer } from "./serve.mjs";

const require = createRequire(import.meta.url);
function loadPlaywright() {
    for (const id of ["playwright", "/opt/node22/lib/node_modules/playwright"]) {
        try { return require(id); } catch (e) { /* التالي */ }
    }
    console.error("Playwright غير مثبت: npm install --no-save playwright");
    process.exit(2);
}
const { chromium } = loadPlaywright();

const server = createServer();
await new Promise(function (r) { server.listen(0, "127.0.0.1", r); });
const BASE = "http://127.0.0.1:" + server.address().port + "/index.html";

let failed = 0;
function check(name, ok, detail) {
    console.log((ok ? "PASS " : "FAIL ") + name + (ok || detail === undefined ? "" : " — " + detail));
    if (!ok) failed++;
}

/* مخزن Firebase وهمي مشترك بين «جوالين» */
const store = {};
let clock = 1000;
async function attachFakeFirebase(context, email) {
    await context.exposeBinding("__sxPush", function (src, ws, list) {
        store[ws] = store[ws] || {};
        list.forEach(function (r) { clock += 1; store[ws][r.id] = Object.assign({}, r, { syncedAt: clock }); });
        return true;
    });
    await context.exposeBinding("__sxPull", function (src, ws, since) {
        return Object.values(store[ws] || {}).filter(function (d) { return d.syncedAt > since; });
    });
    await context.addInitScript(function (mail) {
        window.__SX_FB_TEST = {
            onUser: function (cb) { setTimeout(function () { cb({ uid: "u1", email: mail }); }, 10); return function () {}; },
            signIn: function () { return Promise.resolve(); },
            signOut: function () { return Promise.resolve(); },
            listen: function (ws, since, onDocs) {
                let last = since || 0, stop = false;
                async function tick() {
                    if (stop) return;
                    const docs = await window.__sxPull(ws, last);
                    if (docs.length) { last = Math.max.apply(null, docs.map(function (d) { return d.syncedAt; })); onDocs(docs); }
                    setTimeout(tick, 200);
                }
                tick();
                return function () { stop = true; };
            },
            push: function (ws, list) { return window.__sxPush(ws, list); }
        };
    }, email);
}

const browser = await chromium.launch();
const errors = [];
async function newPhone(name) {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 }, acceptDownloads: true });
    await attachFakeFirebase(context, "owner@example.com");
    const page = await context.newPage();
    page.on("pageerror", function (e) { errors.push(name + ": " + e.message); });
    page.on("console", function (m) { if (m.type() === "error") errors.push(name + " console: " + m.text()); });
    page.on("dialog", function (d) { d.dismiss(); });
    await page.goto(BASE);
    return { context: context, page: page };
}
async function login(page) {
    await page.fill("#loginUser", "admin");
    await page.fill("#loginPass", "1234");
    await page.click("#loginForm button[type=submit]");
    /* نافذة تغيير كلمة المرور الافتراضية ← لاحقاً */
    await page.waitForSelector(".modal", { timeout: 3000 });
    await page.click('.modal [data-modal="cancel"]');
}
async function modalOk(page) {
    await page.waitForSelector('.modal [data-modal="ok"]');
    await page.click('.modal [data-modal="ok"]');
}
async function nav(page, sec) {
    const menu = await page.$("#menuBtn");
    if (menu && await menu.isVisible()) await menu.click();
    await page.click('#sidebar [data-sec="' + sec + '"]');
}
async function state(page) {
    return page.evaluate(function () {
        const o = {};
        Object.keys(ST.curs).forEach(function (k) { o[k] = ST.curs[k].qty.toFixed(); });
        return { bal: o, day: openDay(), ops: byType("exchange").length, net: sumDays(function () { return true; }).net.toFixed(6), neg: ST.negatives.length };
    });
}

/* ---------- الجوال الأول: الإعداد ---------- */
const A = await newPhone("A");
const pa = A.page;
await pa.waitForSelector("#loginScreen:not(.hidden)");
check("تظهر شاشة الدخول", true);
await pa.fill("#loginUser", "admin");
await pa.fill("#loginPass", "0000");
await pa.click("#loginForm button[type=submit]");
check("كلمة مرور خاطئة تُرفض", (await pa.textContent("#loginError")).length > 0);
await login(pa);
await pa.waitForSelector(".setup");
check("يظهر معالج الإعداد الافتتاحي عند أول تشغيل", true);
const order = await pa.$$eval("[data-c=code]", function (els) { return els.map(function (e) { return e.value; }); });
check("ترتيب العملات: ليرة سورية جديدة، دولار، تركي، يورو", order.join(",") === "SYP,USD,TRY,EUR", order.join(","));
await pa.click('[data-act="s-next"]');
const rates = { "USD/SYP": ["110", "112"], "USD/TRY": ["38", "39"], "EUR/USD": ["1.14", "1.16"], "TRY/SYP": ["2.8", "2.9"], "EUR/SYP": ["125", "130"], "EUR/TRY": ["44", "45"] };
for (const k of Object.keys(rates)) {
    await pa.fill('[data-rate="' + k + '"][data-side="buy"]', rates[k][0]);
    await pa.fill('[data-rate="' + k + '"][data-side="sell"]', rates[k][1]);
}
await pa.click('[data-act="s-next"]');
await pa.fill('[data-o="qty"][data-cur="SYP"]', "1,000,000");
await pa.fill('[data-o="rate"][data-cur="SYP"]', "111");
await pa.fill('[data-o="qty"][data-cur="USD"]', "5000");
await pa.fill('[data-o="qty"][data-cur="TRY"]', "20000");
await pa.fill('[data-o="rate"][data-cur="TRY"]', "40");
await pa.fill('[data-o="qty"][data-cur="EUR"]', "2000");
await pa.fill('[data-o="rate"][data-cur="EUR"]', "1.15");
const total = await pa.textContent("#openTotal");
check("إجمالي الصندوق الافتتاحي يُحسب", total.indexOf("16,809.01") >= 0, total);
await pa.click('[data-act="s-save"]');
await modalOk(pa);
await pa.waitForSelector(".cur-card");
let st = await state(pa);
check("حُفظت الأرصدة الافتتاحية", st.bal.SYP === "1000000" && st.bal.USD === "5000" && st.bal.EUR === "2000", JSON.stringify(st.bal));

/* ---------- عملية صرافة ---------- */
await nav(pa, "exchange");
await pa.waitForSelector("#exInAmt");
check("السعر الافتراضي عند استلام الدولار = سعر الشراء", (await pa.inputValue("#exRate")) === "110");
await pa.fill("#exInAmt", "100");
check("المبلغ الناتج تلقائياً 11000", (await pa.inputValue("#exOutAmt")) === "11000");
await pa.fill("#exOutAmt", "10950");
check("تعديل المبلغ الناتج يحدّث السعر الفعلي إلى 109.5", (await pa.inputValue("#exRate")) === "109.5");
await pa.fill("#exInAmt", "200");
check("بعد تعديل الناتج يبقى هو الأساس (السعر يتغير)", (await pa.inputValue("#exOutAmt")) === "10950");
await pa.fill("#exRate", "110");
check("تعديل السعر يعيد حساب الناتج", (await pa.inputValue("#exOutAmt")) === "22000");
await pa.click('[data-act="reset"]');
check("تصفير العملية يمسح المبالغ", (await pa.inputValue("#exInAmt")) === "" && (await pa.inputValue("#exOutAmt")) === "");
st = await state(pa);
check("التصفير لا يمس الأرصدة", st.bal.USD === "5000" && st.ops === 0);

await pa.fill("#exInAmt", "100");
await pa.click('[data-act="save"]');
await pa.waitForFunction(function () { return byType("exchange").length === 1; });
st = await state(pa);
check("شراء دولار مقابل ليرة: تحديث الرصيدين", st.bal.USD === "5100" && st.bal.SYP === "989000", JSON.stringify(st.bal));
check("ربح العملية محسوب", Number(st.net) > 0.9 && Number(st.net) < 0.91, st.net);

/* بيع دولار مقابل ليرة */
await pa.click(".swap-btn");
check("زر التبديل يعكس الاتجاه ويستدعي سعر البيع", (await pa.inputValue("#exRate")) === "112");
await pa.fill("#exInAmt", "11200");
check("الناتج 100 دولار", (await pa.inputValue("#exOutAmt")) === "100");
await pa.press("#exInAmt", "Enter");
await pa.waitForFunction(function () { return byType("exchange").length === 2; });

/* تجاوز الرصيد */
await pa.fill("#exInAmt", "1000000");
await pa.click('[data-act="save"]');
await pa.waitForTimeout(200);
check("منع عملية تتجاوز الرصيد", (await pa.textContent("#exErr")).indexOf("الرصيد غير كافٍ") >= 0, await pa.textContent("#exErr"));
await pa.click('[data-act="reset"]');

/* تبديل عملتين غير الدولار + تكلفة يدوية */
await pa.selectOption("#exInCur", "TRY");
await pa.selectOption("#exOutCur", "SYP");
await pa.fill("#exInAmt", "1000");
await pa.click('[data-cm="manual"]');
await pa.fill("#exManual", "110");
await pa.click('[data-act="save"]');
await pa.waitForFunction(function () { return byType("exchange").length === 3; });
const manualRes = await pa.evaluate(function () { const r = byType("exchange").find(function (x) { return x.inCur === "TRY"; }); return { m: r.costMethod, v: ST.results[r.id].valuation.toFixed(6) }; });
check("التكلفة اليدوية تُحفظ وتُفصل تسوية التقييم", manualRes.m === "manual" && manualRes.v !== "0", JSON.stringify(manualRes));

/* ---------- الصندوق ---------- */
const netBeforeCash = (await state(pa)).net;
await nav(pa, "cashbox");
await pa.selectOption("#cbCur", "USD");
await pa.fill("#cbAmt", "1000");
await pa.fill("#cbReason", "رأس مال");
await pa.click('[data-act="cash-save"]');
await modalOk(pa);
await pa.waitForFunction(function () { return ST.curs.USD.qty.eq(6000); });
st = await state(pa);
check("إضافة رصيد لا تغيّر الأرباح", st.net === netBeforeCash, st.net + " vs " + netBeforeCash);

/* ---------- سجل العمليات: تعديل وحذف ---------- */
await nav(pa, "log");
await pa.waitForSelector("[data-edit]");
check("سجل العمليات يعرض 3 عمليات", (await pa.$$("[data-edit]")).length === 3);
const firstId = await pa.evaluate(function () { return byType("exchange").sort(E.sortLedger)[0].id; });
await pa.click('[data-edit="' + firstId + '"]');
await pa.waitForSelector("#exInAmt");
await pa.fill("#exInAmt", "50");
await pa.click('[data-act="save"]');
await pa.waitForFunction(function (id) { return records.get(id).inAmt === "50"; }, firstId);
const audits = await pa.evaluate(function (id) { return byType("audit").filter(function (a) { return a.refId === id; }).length; }, firstId);
check("تعديل عملية يحفظ نسختها السابقة في سجل التدقيق", audits === 1);

/* ---------- الإقفال ---------- */
const dayBefore = (await state(pa)).day;
await nav(pa, "close");
await pa.click('[data-act="close-today"]');
await modalOk(pa);
await pa.waitForFunction(function (d) { return isClosed(d); }, dayBefore);
st = await state(pa);
check("بعد الإقفال يُفتح اليوم التالي", st.day > dayBefore, st.day);
check("الإقفال لا يصفّر الأرصدة", st.bal.USD !== "0");
const dup = await pa.evaluate(async function (d) { try { await closeUpTo(d, false); return "closed-again"; } catch (e) { return e.message; } }, dayBefore);
check("منع الإقفال المكرر", dup !== "closed-again" && (await pa.evaluate(function () { return byType("close").length; })) === 1, dup);
const editLocked = await pa.evaluate(function (id) { return isClosed(records.get(id).day); }, firstId);
check("عمليات اليوم المقفل مقفلة للتعديل", editLocked);

/* ---------- الجرد ---------- */
await nav(pa, "count");
const usdRecorded = Number((await state(pa)).bal.USD);
await pa.fill('[data-actual="USD"]', String(usdRecorded - 20));
await pa.fill("#countReason", "نقص 20");
await pa.click('[data-act="count-approve"]');
await pa.click('.modal [data-modal="settle"]');
await pa.waitForFunction(function () { return byType("count").length === 1; });
st = await state(pa);
const inv = await pa.evaluate(function () { return ST.days[openDay()].inventory.toFixed(); });
check("تسوية الجرد تعدل الرصيد وتُفصل عن الربح", st.bal.USD === String(usdRecorded - 20) && inv === "-20", st.bal.USD + " / " + inv);

/* ---------- سحب الأرباح ---------- */
await nav(pa, "withdraw");
await pa.selectOption("#wdCur", "USD");
await pa.fill("#wdAmt", "1");
await pa.click('[data-act="wd-save"]');
await modalOk(pa);
await pa.waitForFunction(function () { return byType("withdrawal").length === 1; });
check("سحب الأرباح يخفض الرصيد", (await state(pa)).bal.USD === String(usdRecorded - 21));

/* ---------- التقارير ---------- */
await nav(pa, "reports");
for (const t of ["daily", "monthly", "pnl", "carried", "balances", "moves", "withdrawals", "counts", "countDiffs", "pairs", "annual"]) {
    await pa.selectOption("#rpType", t);
    await pa.waitForTimeout(30);
}
check("كل التقارير تُعرض دون أخطاء", errors.length === 0, errors.join(" | "));
const [dl] = await Promise.all([pa.waitForEvent("download", { timeout: 5000 }).catch(function (e) { console.log("download errors:", errors); throw e; }), pa.click('[data-act="xlsx"]')]);
const xlsxPath = await dl.path();
const head = fs.readFileSync(xlsxPath).subarray(0, 2).toString();
check("تصدير Excel ينتج ملف xlsx", head === "PK" && dl.suggestedFilename().endsWith(".xlsx"), head + " " + dl.suggestedFilename() + " " + (await dl.failure()));

/* ---------- لوحة التحكم ---------- */
await nav(pa, "dashboard");
check("لوحة التحكم تعرض الرسوم البيانية", (await pa.$$("svg.chart")).length === 4);

/* ---------- الحفظ بعد إعادة التحميل ---------- */
const before = await state(pa);
await pa.reload();
await pa.waitForSelector(".cur-card");
const after = await state(pa);
check("البيانات محفوظة بعد إعادة فتح المتصفح", JSON.stringify(before) === JSON.stringify(after));

/* ---------- النسخ الاحتياطي ---------- */
await nav(pa, "settings");
const [bk] = await Promise.all([pa.waitForEvent("download"), pa.click('[data-act="backup"]')]);
const backupFile = await bk.path();
const backup = JSON.parse(fs.readFileSync(backupFile, "utf8"));
check("النسخة الاحتياطية تحتوي كل السجلات وبصمة", backup.format === "smart-exchange-backup" && backup.records.length > 10 && backup.checksum.length === 64);
const badFile = backupFile + ".bad.json";
const bad = JSON.parse(JSON.stringify(backup)); bad.records[0].id = "tampered";
fs.writeFileSync(badFile, JSON.stringify(bad));
const badMsg = await pa.evaluate(async function (txt) { return validateBackup(JSON.parse(txt)); }, fs.readFileSync(badFile, "utf8"));
check("استعادة ملف غير صالح تُرفض", !!badMsg, badMsg);

/* ---------- جوالان بالحساب نفسه في الوقت نفسه ---------- */
await pa.click('[data-sync="enable"]');
await pa.waitForFunction(function () { return fb.status === "online"; });
await pa.waitForFunction(function () { return Object.keys(syncMeta.dirty).length === 0; }, null, { timeout: 8000 });
check("الجوال الأول رفع بياناته", Object.keys(store.exchange || {}).length >= records_count());
function records_count() { return backup.records.length; }

const B = await newPhone("B");
const pb = B.page;
await login(pb);
await pb.waitForSelector(".setup");
await pb.click('[data-act="s-sync"]');
await pb.click('[data-sync="enable"]');
await pb.waitForFunction(function () { return hasOpening() && byType("exchange").length === 3; }, null, { timeout: 8000 });
const sb = await state(pb), sa = await state(pa);
check("الجوال الثاني (admin نفسه) استلم كل البيانات", JSON.stringify(sa.bal) === JSON.stringify(sb.bal), JSON.stringify(sb.bal));

await nav(pb, "exchange");
await pb.waitForSelector("#exInAmt");
await pb.fill("#exInAmt", "10");
await pb.click('[data-act="save"]');
await pa.waitForFunction(function () { return byType("exchange").length === 4; }, null, { timeout: 8000 });
check("عملية من الجوال الثاني تظهر في الأول خلال ثوانٍ", true);
const nos = await pa.evaluate(function () { return byType("exchange").map(function (r) { return r.no; }); });
check("أرقام العمليات لا تتكرر بين الجهازين", new Set(nos).size === nos.length, nos.join(","));
check("الجوال الأول ما زال مسجلاً للدخول", await pa.evaluate(function () { return !!me; }));

/* ---------- تعدد التبويبات ---------- */
const tab2 = await A.context.newPage();
tab2.on("pageerror", function (e) { errors.push("tab2: " + e.message); });
await tab2.goto(BASE);
await tab2.waitForSelector("#app:not(.hidden)");
await nav(pa, "cashbox");
await pa.selectOption("#cbCur", "TRY");
await pa.fill("#cbAmt", "5");
await pa.fill("#cbReason", "اختبار تبويب");
await pa.click('[data-act="cash-save"]');
await modalOk(pa);
await tab2.waitForFunction(function () { return byType("cash").length === 2; }, null, { timeout: 5000 });
check("تبويب آخر يتحدث تلقائياً", true);

/* ---------- الوضع الليلي وشاشة الكمبيوتر ---------- */
await pa.click("#themeBtn");
check("الوضع الليلي", (await pa.getAttribute("html", "data-theme")) === "dark");
await pa.setViewportSize({ width: 1366, height: 900 });
await nav(pa, "dashboard");
const overflow = await pa.evaluate(function () { return document.documentElement.scrollWidth > window.innerWidth + 1; });
check("لا تمرير أفقي على الكمبيوتر", !overflow);
await pa.setViewportSize({ width: 390, height: 844 });
const wide = [];
for (const sec of ["dashboard", "exchange", "rates", "cashbox", "log", "pnl", "close", "register", "withdraw", "count", "reports", "settings"]) {
    await nav(pa, sec);
    await pa.waitForTimeout(60);
    if (await pa.evaluate(function () { return document.documentElement.scrollWidth > window.innerWidth + 1; })) wide.push(sec);
}
check("لا تمرير أفقي على الجوال في كل الأقسام", !wide.length, wide.join(","));
if (process.env.SHOTS) {
    for (const sec of ["dashboard", "exchange", "log", "close", "register", "count", "settings"]) {
        await nav(pa, sec);
        await pa.waitForTimeout(150);
        await pa.screenshot({ path: process.env.SHOTS + "/m-" + sec + ".png", fullPage: true });
    }
    await pa.setViewportSize({ width: 1366, height: 900 });
    for (const sec of ["dashboard", "exchange", "reports"]) {
        await nav(pa, sec);
        await pa.waitForTimeout(150);
        await pa.screenshot({ path: process.env.SHOTS + "/d-" + sec + ".png", fullPage: true });
    }
}

check("لا أخطاء JavaScript أثناء الاختبار", errors.length === 0, errors.join(" | "));
await browser.close();
server.close();
console.log(failed ? "\nفشل " + failed + " اختبار" : "\nكل الاختبارات ناجحة");
process.exit(failed ? 1 : 0);
