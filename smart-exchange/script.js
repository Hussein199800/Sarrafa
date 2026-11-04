/* =========================================================
   نظام الصرافة الذكي — الواجهة والتخزين والمزامنة
   ---------------------------------------------------------
   - الحسابات كلها في engine.js (SXEngine) بدقة عشرية (Decimal.js).
   - التخزين المحلي: IndexedDB (قاعدة smart_exchange: مخزن records ومخزن meta).
   - كل شيء «سجل» له id: الإعدادات، الأسعار، الرصيد الافتتاحي، العمليات،
     حركات الصندوق، سحوبات الأرباح، الجرد، الإقفال، سجل التدقيق.
     الأرصدة والأرباح لا تُخزَّن بل تُشتق من السجلات (انظر engine.js).
   - المزامنة (اختيارية) عبر Firebase بين أكثر من جوال بالحساب نفسه.
   ========================================================= */
"use strict";

const E = window.SXEngine;
const D = E.Decimal;
const APP_VERSION = "1.0.0";
const DB_NAME = "smart_exchange";
const DB_VERSION = 1;
const BACKUP_FORMAT = "smart-exchange-backup";
const BACKUP_VERSION = 1;

/* =========================================================
   أدوات عامة
   ========================================================= */
const $ = function (s, r) { return (r || document).querySelector(s); };
const $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
function esc(s) {
    return String(s === null || s === undefined ? "" : s).replace(/[&<>"']/g, function (c) {
        return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
}
function uid(prefix) { return (prefix || "r") + "-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 8); }
function nowISO() { return new Date().toISOString(); }
function clone(o) { return JSON.parse(JSON.stringify(o)); }
function UserError(msg) { this.message = msg; this.user = true; }
UserError.prototype = Object.create(Error.prototype);

/* SHA-256 (بلا مكتبات؛ يعمل حتى على http داخل شبكة المحل) */
function sha256(text) {
    const K = [0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2];
    const bytes = new TextEncoder().encode(text);
    const len = bytes.length;
    const words = new Uint32Array(((len + 9 + 63) >> 6) << 4);
    for (let i = 0; i < len; i++) words[i >> 2] |= bytes[i] << (24 - (i % 4) * 8);
    words[len >> 2] |= 0x80 << (24 - (len % 4) * 8);
    words[words.length - 1] = len * 8;
    const H = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
    const W = new Uint32Array(64);
    const rot = function (x, n) { return (x >>> n) | (x << (32 - n)); };
    for (let j = 0; j < words.length; j += 16) {
        for (let t = 0; t < 64; t++) {
            if (t < 16) W[t] = words[j + t];
            else {
                const s0 = rot(W[t - 15], 7) ^ rot(W[t - 15], 18) ^ (W[t - 15] >>> 3);
                const s1 = rot(W[t - 2], 17) ^ rot(W[t - 2], 19) ^ (W[t - 2] >>> 10);
                W[t] = (W[t - 16] + s0 + W[t - 7] + s1) | 0;
            }
        }
        let a = H[0], b = H[1], c = H[2], d = H[3], e = H[4], f = H[5], g = H[6], h = H[7];
        for (let t = 0; t < 64; t++) {
            const S1 = rot(e, 6) ^ rot(e, 11) ^ rot(e, 25);
            const ch = (e & f) ^ (~e & g);
            const t1 = (h + S1 + ch + K[t] + W[t]) | 0;
            const S0 = rot(a, 2) ^ rot(a, 13) ^ rot(a, 22);
            const mj = (a & b) ^ (a & c) ^ (b & c);
            const t2 = (S0 + mj) | 0;
            h = g; g = f; f = e; e = (d + t1) | 0; d = c; c = b; b = a; a = (t1 + t2) | 0;
        }
        H[0] = (H[0] + a) | 0; H[1] = (H[1] + b) | 0; H[2] = (H[2] + c) | 0; H[3] = (H[3] + d) | 0;
        H[4] = (H[4] + e) | 0; H[5] = (H[5] + f) | 0; H[6] = (H[6] + g) | 0; H[7] = (H[7] + h) | 0;
    }
    return H.map(function (x) { return (x >>> 0).toString(16).padStart(8, "0"); }).join("");
}
function passHash(userId, password) { return sha256("sx:" + userId + ":" + password); }

/* ---------- تنسيق الأرقام ---------- */
function group(s) {
    let neg = false;
    if (s.charAt(0) === "-") { neg = true; s = s.slice(1); }
    const parts = s.split(".");
    const i = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    const out = (neg ? "-" : "") + i + (parts[1] ? "." + parts[1] : "");
    return out === "-0" ? "0" : out;
}
function fmt(x, dp) {
    const d = E.dec(x);
    return group(dp === undefined ? d.toFixed() : d.toFixed(dp));
}
function fmtTrim(x, maxDp) { return group(E.dec(x).toDecimalPlaces(maxDp === undefined ? 8 : maxDp).toFixed()); }
function sym(code) { const c = E.curInfo(S, code); return c ? (c.symbol || c.code) : code; }
function curName(code) { const c = E.curInfo(S, code); return c ? c.name : code; }
function fmtCur(x, code, dp) {
    return '<span class="num">' + fmt(x, dp === undefined ? E.decimalsOf(S, code) : dp) + "</span> " + esc(sym(code));
}
function fmtUSD(x) { return fmtCur(x, S.base, 2); }
function cls(x) { const d = E.dec(x); return d.gt(0) ? "pos" : d.lt(0) ? "neg" : ""; }
function dispRate() { return ST && ST.refs[S.display]; }
/* المبالغ المحاسبية (بالدولار) تُعرض بالليرة أولاً ثم الدولار */
function money(usd, rate, opts) {
    const r = rate === undefined ? dispRate() : (rate ? E.dec(rate) : null);
    const d = E.dec(usd);
    const c = opts && opts.noColor ? "" : cls(d);
    if (!r) return '<span class="' + c + '">' + fmtUSD(d) + "</span>";
    return '<span class="' + c + '">' + fmtCur(d.times(r), S.display) + '</span> <span class="muted small">(' + fmtUSD(d) + ")</span>";
}
function moneyText(usd, rate) {
    const r = rate === undefined ? dispRate() : (rate ? E.dec(rate) : null);
    const d = E.dec(usd);
    return (r ? fmt(d.times(r), E.decimalsOf(S, S.display)) + " " + sym(S.display) + " / " : "") + fmt(d, 2) + " " + sym(S.base);
}
function numOut(x, dp) { return Number(E.dec(x).toFixed(dp === undefined ? 2 : dp)); }
function fmtDay(day) { if (!day) return ""; const p = day.split("-"); return p[2] + "/" + p[1] + "/" + p[0]; }
function fmtTime(iso) { return iso ? E.clockOf(Date.parse(iso), S.timeZone).time : ""; }
function fmtDateTime(iso) {
    if (!iso) return "";
    const c = E.clockOf(Date.parse(iso), S.timeZone);
    return fmtDay(c.date) + " " + c.time.slice(0, 5);
}
function monthName(ym) {
    const names = ["كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران", "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"];
    const p = ym.split("-");
    return names[Number(p[1]) - 1] + " " + p[0];
}

/* سعر الزوج بالنسبة للدولار بصيغة مفهومة: «ل.س لكل 1$» أو «$ لكل 1€» */
function costOrient(code) {
    const p = E.pairOf(S, S.base, code);
    return { usdIsBase: p.base === S.base, label: p.base === S.base ? sym(code) + " لكل 1 " + sym(S.base) : sym(S.base) + " لكل 1 " + sym(code) };
}
function unitCostToRate(unitCost, code) {
    const u = E.dec(unitCost);
    if (code === S.base) return E.ONE;
    if (u.lte(0)) return null;
    return costOrient(code).usdIsBase ? E.ONE.div(u) : u;
}
function rateToUnitCost(rate, code) {
    const r = E.parse(rate);
    if (code === S.base) return E.ONE;
    if (!r || r.lte(0)) return null;
    return costOrient(code).usdIsBase ? E.ONE.div(r) : r;
}
function fmtCostRate(unitCost, code) {
    if (code === S.base) return "1";
    const r = unitCostToRate(unitCost, code);
    return r ? fmtTrim(r, 4) + " " + costOrient(code).label : "—";
}

/* =========================================================
   IndexedDB
   ========================================================= */
const DB = {
    db: null,
    open: function () {
        return new Promise(function (resolve, reject) {
            if (!window.indexedDB) { reject(new Error("المتصفح لا يدعم IndexedDB")); return; }
            const req = indexedDB.open(DB_NAME, DB_VERSION);
            req.onupgradeneeded = function () {
                const db = req.result;
                if (!db.objectStoreNames.contains("records")) db.createObjectStore("records", { keyPath: "id" });
                if (!db.objectStoreNames.contains("meta")) db.createObjectStore("meta", { keyPath: "key" });
            };
            req.onsuccess = function () {
                DB.db = req.result;
                DB.db.onversionchange = function () { DB.db.close(); location.reload(); };
                resolve(DB.db);
            };
            req.onerror = function () { reject(req.error); };
            req.onblocked = function () { reject(new Error("قاعدة البيانات مقفلة من تبويب آخر")); };
        });
    },
    all: function (store) {
        return new Promise(function (resolve, reject) {
            const tx = DB.db.transaction(store, "readonly");
            const req = tx.objectStore(store).getAll();
            req.onsuccess = function () { resolve(req.result || []); };
            req.onerror = function () { reject(req.error); };
        });
    },
    get: function (store, key) {
        return new Promise(function (resolve, reject) {
            const tx = DB.db.transaction(store, "readonly");
            const req = tx.objectStore(store).get(key);
            req.onsuccess = function () { resolve(req.result || null); };
            req.onerror = function () { reject(req.error); };
        });
    },
    /* كتابة ذرية: كل السجلات (والميتا) في معاملة واحدة؛ clear = استبدال كامل (الاستعادة) */
    write: function (records, metas, clear) {
        return new Promise(function (resolve, reject) {
            const tx = DB.db.transaction(["records", "meta"], "readwrite");
            const rs = tx.objectStore("records"), ms = tx.objectStore("meta");
            if (clear) rs.clear();
            (records || []).forEach(function (r) { rs.put(r); });
            (metas || []).forEach(function (m) { ms.put(m); });
            tx.oncomplete = function () { resolve(); };
            tx.onerror = function () { reject(tx.error); };
            tx.onabort = function () { reject(tx.error || new Error("أُلغيت عملية الحفظ")); };
        });
    }
};

/* =========================================================
   الحالة العامة
   ========================================================= */
let records = new Map();   /* id → سجل */
let S = null;              /* الإعدادات الفعلية (مع الأسعار) */
let ST = null;             /* نتيجة إعادة تشغيل الدفتر */
let me = null;             /* المستخدم الحالي */
let syncMeta = { key: "sync", enabled: false, workspace: "exchange", lastPull: 0, dirty: {}, lastSyncAt: 0, config: "" };
const ui = { section: "dashboard", ex: null, logFilter: {}, cashTab: "add", reportType: "daily", reportFrom: "", reportTo: "", registerFilter: {}, pnlPeriod: "today", moveFilter: "", moveLimit: 200 };
const channel = typeof BroadcastChannel !== "undefined" ? new BroadcastChannel("smart-exchange") : null;

function lsGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
function lsSet(k, v) { try { if (v === null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch (e) { /* وضع خاص */ } }

function deviceCode() {
    let c = lsGet("sx_device");
    if (!c || !/^[A-Z]{1,3}$/.test(c)) {
        const L = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        c = L[Math.floor(Math.random() * L.length)] + L[Math.floor(Math.random() * L.length)];
        lsSet("sx_device", c);
    }
    return c;
}

function defaultSettings() {
    return {
        id: "settings", type: "settings", updatedAt: "", /* فارغ = لم يُعدَّل بعد؛ لا يكتب فوق إعدادات جهاز آخر عند المزامنة */
        base: "USD", display: "SYP",
        shopName: "محل الصرافة",
        currencies: [
            { code: "SYP", name: "الليرة السورية الجديدة", symbol: "ل.س", decimals: 0, strength: 1, enabled: true },
            { code: "USD", name: "الدولار الأمريكي", symbol: "$", decimals: 2, strength: 3, enabled: true },
            { code: "TRY", name: "الليرة التركية", symbol: "₺", decimals: 2, strength: 2, enabled: true },
            { code: "EUR", name: "اليورو", symbol: "€", decimals: 2, strength: 4, enabled: true }
        ],
        refOverride: {},
        costMethod: "avg", rounding: "half-up", valuation: "reference",
        closeMode: "manual", dayStartHour: 0, timeZone: "Asia/Damascus",
        users: [{ id: "u-admin", username: "admin", name: "المدير", role: "manager", hash: passHash("u-admin", "1234"), mustChange: true }]
    };
}
function settingsRec() { const r = records.get("settings"); return r && !r.deleted ? r : defaultSettings(); }
function effectiveSettings(map) {
    const src = (map || records).get("settings");
    const s = clone(src && !src.deleted ? src : defaultSettings());
    s.rates = {};
    (map || records).forEach(function (r) {
        if (r.type === "rate" && !r.deleted) s.rates[r.pair] = { buy: r.buy, sell: r.sell, updatedAt: r.updatedAt, by: r.by };
    });
    return s;
}
function recompute() {
    S = effectiveSettings();
    ST = E.replay(Array.from(records.values()), S);
}
function hasOpening() { const o = records.get("opening"); return !!(o && !o.deleted); }
function ledgerOps() {
    return Array.from(records.values()).filter(function (r) { return !r.deleted && (r.type === "exchange" || r.type === "cash" || r.type === "withdrawal" || r.type === "count"); });
}
function byType(type, includeDeleted) {
    return Array.from(records.values()).filter(function (r) { return r.type === type && (includeDeleted || !r.deleted); });
}

/* =========================================================
   الحفظ: قفل بين التبويبات ← قراءة أحدث نسخة ← تحقق ← معاملة واحدة
   ========================================================= */
function withLock(fn) {
    if (navigator.locks && navigator.locks.request) return navigator.locks.request("smart-exchange-write", fn);
    return fn();
}
async function reloadRecords() {
    const list = await DB.all("records");
    records = new Map(list.map(function (r) { return [r.id, r]; }));
}
function stamp(r, isNew) {
    const t = nowISO();
    r.updatedAt = t;
    r.updatedBy = me ? me.username : "system";
    if (isNew || !r.createdAt) { r.createdAt = r.createdAt || t; r.createdBy = r.createdBy || (me ? me.username : "system"); r.device = r.device || deviceCode(); }
    return r;
}
function negMessage(list) {
    const n = list[0];
    return "لا يمكن الحفظ: سيصبح رصيد " + curName(n.cur) + " سالباً (" + fmt(n.qty, E.decimalsOf(S, n.cur)) + ")" +
        (n.no ? " عند العملية " + n.no : "") + (list.length > 1 ? " وفي " + (list.length - 1) + " موضع آخر" : "") + ".";
}
/* changes: سجلات كاملة. opts.check(nextMap, nextState, nextSettings) يعيد رسالة خطأ أو لا شيء */
function commit(changes, opts) {
    opts = opts || {};
    return withLock(async function () {
        await reloadRecords();
        const savedSync = await DB.get("meta", "sync");
        if (savedSync) syncMeta = savedSync;
        const prevSettings = effectiveSettings();
        const prevState = E.replay(Array.from(records.values()), prevSettings);
        const next = new Map(records);
        changes.forEach(function (r) { stamp(r, !records.has(r.id)); next.set(r.id, r); });
        const nextSettings = effectiveSettings(next);
        const nextState = E.replay(Array.from(next.values()), nextSettings);
        if (opts.check) {
            const msg = opts.check(next, nextState, nextSettings, records);
            if (msg) throw new UserError(msg);
        }
        if (opts.validate !== false) {
            const neg = E.newNegatives(prevState, nextState);
            if (neg.length) throw new UserError(negMessage(neg));
        }
        changes.forEach(function (r) { if (syncMeta.enabled) syncMeta.dirty[r.id] = true; });
        await DB.write(changes, syncMeta.enabled ? [syncMeta] : []);
        records = next;
        recompute();
        if (channel) channel.postMessage({ t: "changed" });
        fbSchedulePush();
        return nextState;
    });
}
function audit(action, record, before, reason) {
    return {
        id: uid("a"), type: "audit", action: action, refId: record.id, refType: record.type, refNo: record.no || "",
        before: before ? JSON.stringify(before) : "", after: JSON.stringify(record), reason: reason || "", at: nowISO()
    };
}
function failMsg(e) {
    if (e && e.user) return e.message;
    console.error(e);
    return "حدث خطأ غير متوقع ولم تُحفظ التغييرات: " + (e && e.message ? e.message : e);
}

/* =========================================================
   الرسائل والنوافذ
   ========================================================= */
function toast(html, kind, ms) {
    const el = document.createElement("div");
    el.className = "toast " + (kind || "");
    const icon = { ok: "circle-check", bad: "alert-circle", warn: "alert-triangle" }[kind] || "info-circle";
    el.innerHTML = '<i class="ti ti-' + icon + ' toast-icon" aria-hidden="true"></i><div>' + html + "</div>";
    el.addEventListener("click", function () { el.style.display = "none"; });
    $("#toasts").appendChild(el);
    setTimeout(function () { el.remove(); }, ms || 4500);
}
function modal(html, opts) {
    opts = opts || {};
    return new Promise(function (resolve) {
        const back = document.createElement("div");
        back.className = "modal-back";
        back.innerHTML = '<div class="modal ' + (opts.wide ? "wide" : "") + '" role="dialog" aria-modal="true">' + html + "</div>";
        $("#modalRoot").appendChild(back);
        const box = back.firstChild;
        function done(v) { back.remove(); document.removeEventListener("keydown", onKey); resolve(v); }
        function onKey(e) { if (e.key === "Escape" && !opts.noEscape) done(null); }
        document.addEventListener("keydown", onKey);
        back.addEventListener("click", function (e) { if (e.target === back && !opts.noEscape) done(null); });
        box.addEventListener("click", function (e) {
            const b = e.target.closest("[data-modal]");
            if (!b) return;
            const v = b.getAttribute("data-modal");
            if (v === "cancel") { done(null); return; }
            if (opts.collect) {
                const res = opts.collect(box, v);
                if (res === false) return;
                done(res);
            } else done(v);
        });
        if (opts.onOpen) opts.onOpen(box);
        const f = box.querySelector("[autofocus], input, select, textarea, button.primary");
        if (f) setTimeout(function () { f.focus(); }, 30);
    });
}
function confirmBox(title, body, okText, danger) {
    return modal("<h3>" + esc(title) + "</h3><div>" + body + "</div><div class=\"actions\">" +
        '<button class="btn ' + (danger ? "danger" : "primary") + '" data-modal="ok">' + esc(okText || "تأكيد") + '</button>' +
        '<button class="btn" data-modal="cancel">إلغاء</button></div>').then(function (v) { return v === "ok"; });
}
function promptBox(title, body, label, opts) {
    opts = opts || {};
    return modal("<h3>" + esc(title) + "</h3>" + (body ? "<div>" + body + "</div>" : "") +
        '<label class="field" style="margin-top:12px"><span>' + esc(label) + '</span><textarea id="pbVal" autofocus>' + esc(opts.value || "") + '</textarea></label>' +
        '<p class="error-text" id="pbErr"></p><div class="actions"><button class="btn ' + (opts.danger ? "danger" : "primary") + '" data-modal="ok">' + esc(opts.okText || "تأكيد") + '</button><button class="btn" data-modal="cancel">إلغاء</button></div>', {
        collect: function (box) {
            const v = box.querySelector("#pbVal").value.trim();
            if (opts.required !== false && !v) { box.querySelector("#pbErr").textContent = "هذا الحقل مطلوب."; return false; }
            return v;
        }
    });
}

/* =========================================================
   المستخدمون والجلسة
   ========================================================= */
const ROLES = { manager: "مدير", cashier: "صرّاف", viewer: "مشاهد" };
const CASHIER_CAN = { exchange: 1, countSave: 1 };
function can(action) {
    if (!me) return false;
    if (me.role === "manager") return true;
    if (me.role === "cashier") return !!CASHIER_CAN[action];
    return false;
}
function users() { return settingsRec().users || []; }
function findSessionUser() {
    let s = null;
    try { s = JSON.parse(lsGet("sx_session") || "null"); } catch (e) { s = null; }
    if (!s) return null;
    const u = users().find(function (x) { return x.id === s.id && x.hash === s.hash; });
    return u || null;
}
function showLogin() {
    $("#boot").classList.add("hidden");
    $("#app").classList.add("hidden");
    $("#loginScreen").classList.remove("hidden");
    $("#loginError").textContent = "";
    setTimeout(function () { $("#loginUser").focus(); }, 50);
}
function login(username, password) {
    const u = users().find(function (x) { return x.username.toLowerCase() === username.trim().toLowerCase(); });
    if (!u || u.hash !== passHash(u.id, password)) return false;
    me = u;
    /* جلسة لكل جهاز: الدخول بالحساب نفسه من أكثر من جوال مسموح في الوقت نفسه */
    lsSet("sx_session", JSON.stringify({ id: u.id, hash: u.hash, at: nowISO() }));
    return true;
}
function logout() {
    lsSet("sx_session", null);
    me = null;
    showLogin();
}

/* =========================================================
   اليوم المحاسبي والإقفال
   ========================================================= */
function clockDay(ms) { return E.dayOf(ms === undefined ? Date.now() : ms, S.timeZone, S.dayStartHour); }
function closeOf(day) { const r = records.get("close-" + day); return r && !r.deleted ? r : null; }
function isClosed(day) { const c = closeOf(day); return !!(c && c.status === "closed"); }
function openDay() {
    let d = clockDay(), guard = 0;
    while (isClosed(d) && guard++ < 1000) d = E.addDays(d, 1);
    return d;
}
function activityDays() { return Object.keys(ST.days).sort(); }
function unclosedDays(upTo) {
    return activityDays().filter(function (d) { return d <= upTo && !isClosed(d); });
}
function staleCloses() {
    return byType("close").filter(function (c) {
        return c.status === "closed" && E.snapshotDiffers(c.snapshot, E.closeSnapshot(ST, c.day, S));
    }).map(function (c) { return c.day; }).sort();
}
function buildClose(day, auto, note) {
    const existing = closeOf(day);
    const history = existing ? (existing.history || []).slice() : [];
    if (existing && existing.snapshot) {
        history.push({
            snapshot: existing.snapshot, closedAt: existing.closedAt, closedBy: existing.closedBy,
            reopenedAt: existing.reopenedAt || "", reopenedBy: existing.reopenedBy || "", reopenReason: existing.reopenReason || "", note: note || ""
        });
    }
    return {
        id: "close-" + day, type: "close", day: day, status: "closed", snapshot: E.closeSnapshot(ST, day, S),
        closedAt: nowISO(), closedBy: me ? me.username : "system", auto: !!auto, history: history,
        createdAt: existing ? existing.createdAt : undefined
    };
}
async function closeUpTo(day, auto) {
    const list = unclosedDays(day);
    if (list.indexOf(day) < 0 && !isClosed(day)) list.push(day);
    if (!list.length) throw new UserError("اليوم " + fmtDay(day) + " مقفل مسبقاً.");
    const changes = list.map(function (d) { return buildClose(d, auto); });
    await commit(changes, {
        validate: false,
        check: function (next) {
            for (let i = 0; i < list.length; i++) {
                const c = next.get("close-" + list[i]);
                const prev = records.get("close-" + list[i]);
                if (prev && prev.status === "closed" && !prev.deleted) return "اليوم " + fmtDay(list[i]) + " مقفل مسبقاً (ربما من جهاز أو تبويب آخر).";
                if (!c) return "تعذر الإقفال";
            }
            return null;
        }
    });
    return list.length;
}
async function autoCloseTick() {
    if (!S || !me || S.closeMode !== "auto") return;
    const today = clockDay();
    /* الإقفال التلقائي لا يعيد إقفال يوم أُعيد فتحه يدوياً */
    const targets = activityDays().filter(function (d) { return d < today && !closeOf(d); });
    if (!targets.length) return;
    try {
        const changes = targets.map(function (d) { return buildClose(d, true); });
        await commit(changes, {
            validate: false,
            check: function () {
                for (let i = 0; i < targets.length; i++) if (closeOf(targets[i])) return "skip";
                return null;
            }
        });
        toast("أُقفل تلقائياً: " + targets.map(fmtDay).join("، "), "info");
        renderSoft();
    } catch (e) { if (!(e && e.message === "skip")) console.warn("auto close", e); }
}

/* =========================================================
   التنقل والإطار
   ========================================================= */
const SECTIONS = {
    dashboard: renderDashboard, exchange: renderExchange, rates: renderRates, cashbox: renderCashbox,
    log: renderLog, pnl: renderPnl, close: renderClose, register: renderRegister, withdraw: renderWithdraw,
    count: renderCount, reports: renderReports, settings: renderSettings
};
function go(sec) {
    if (!SECTIONS[sec]) sec = "dashboard";
    ui.section = sec;
    lsSet("sx_section", sec);
    $$("#sidebar [data-sec]").forEach(function (b) { b.classList.toggle("active", b.dataset.sec === sec); });
    document.body.classList.remove("nav-open");
    const nb = $('#sidebar [data-sec="' + sec + '"] b');
    if (nb) $("#mobileSectionName").textContent = nb.textContent;
    render();
    window.scrollTo(0, 0);
}
function render() {
    if (!me) return;
    renderTop();
    const main = $("#main");
    if (!hasOpening() && ui.section !== "settings") { renderSetup(main); return; }
    try { SECTIONS[ui.section](main); }
    catch (e) { console.error(e); main.innerHTML = '<div class="alert bad">تعذر عرض هذا القسم: ' + esc(e.message) + "</div>"; }
}
/* تحديث بعد تغيير من تبويب/جهاز آخر دون مسح نموذج قيد الإدخال */
function renderSoft() {
    if (!me) return;
    const u = users().find(function (x) { return x.id === me.id; });
    if (!u) { toast("حُذف حسابك أو تغيّر من جهاز آخر؛ سجّل الدخول من جديد.", "warn"); logout(); return; }
    if (u.hash !== me.hash) { toast("تغيّرت كلمة المرور من جهاز آخر؛ سجّل الدخول من جديد.", "warn"); logout(); return; }
    me = u;
    renderTop();
    if (ui.section === "exchange" && hasOpening()) { exUpdatePreview(); renderExSide(); return; }
    const active = document.activeElement;
    if (active && $("#main").contains(active) && /INPUT|TEXTAREA|SELECT/.test(active.tagName)) return;
    render();
}
function renderTop() {
    $("#dayBadge span").textContent = "اليوم المحاسبي: " + fmtDay(openDay());
    $("#userBadge").textContent = me.name || me.username;
    $("#roleBadge").textContent = ROLES[me.role] || me.role;
    renderSyncBadge();
}
const SECTION_ICONS = {
    dashboard: "layout-dashboard", exchange: "arrows-exchange", rates: "chart-candle", cashbox: "cash-register", log: "list-details",
    pnl: "trending-up", close: "lock", register: "notebook", withdraw: "moneybag", count: "clipboard-check", reports: "report-analytics", settings: "settings"
};
function pageHead(title, sub, actions) {
    return '<div class="page-title"><span class="page-icon"><i class="ti ti-' + (SECTION_ICONS[ui.section] || "coins") + '"></i></span>' +
        '<div class="page-text"><h2>' + esc(title) + "</h2>" + (sub ? "<p>" + sub + "</p>" : "") + "</div>" +
        (actions ? '<div class="row">' + actions + "</div>" : "") + "</div>";
}
function curOptions(selected, onlyEnabled) {
    return (onlyEnabled === false ? S.currencies : E.enabledCurrencies(S)).map(function (c) {
        return '<option value="' + esc(c.code) + '"' + (c.code === selected ? " selected" : "") + ">" + esc(c.name) + " (" + esc(c.code) + ")</option>";
    }).join("");
}
function globalAlerts() {
    const out = [];
    if (ST.negatives.length) {
        const n = ST.negatives[ST.negatives.length - 1];
        out.push('<div class="alert bad"><b>تنبيه:</b> يوجد رصيد سالب (' + esc(curName(n.cur)) + ") — غالباً بسبب عمليات متزامنة من جهازين. راجع سجل العمليات أو أضف رصيداً.</div>");
    }
    const stale = staleCloses();
    if (stale.length) out.push('<div class="alert warn"><b>نتائج غير معتمدة:</b> تغيّرت نتائج أيام مقفلة بعد إقفالها (' + stale.map(fmtDay).join("، ") + '). أعد اعتمادها من صفحة «إقفال اليوم».</div>');
    const reopened = byType("close").filter(function (c) { return c.status === "reopened"; });
    if (reopened.length) out.push('<div class="alert warn">أيام معاد فتحها بانتظار إعادة الإقفال: ' + reopened.map(function (c) { return fmtDay(c.day); }).join("، ") + "</div>");
    const missing = E.enabledCurrencies(S).filter(function (c) { return !ST.refs[c.code]; });
    if (missing.length) out.push('<div class="alert warn">لا يوجد سعر تقييم مرجعي لـ: ' + missing.map(function (c) { return esc(c.name); }).join("، ") + ' — أدخل أسعار زوجها مع الدولار في «أسعار الصرف».</div>');
    const past = unclosedDays(E.addDays(clockDay(), -1)).filter(function (d) { return !closeOf(d); });
    if (past.length && S.closeMode === "manual") out.push('<div class="alert info">أيام سابقة غير مقفلة: ' + past.map(fmtDay).join("، ") + "</div>");
    return out.join("");
}

/* =========================================================
   الإعداد الأول: العملات ← الأسعار ← الصندوق الافتتاحي
   ========================================================= */
let setupDraft = null;
function renderSetup(main) {
    if (!can("settings")) {
        main.innerHTML = pageHead("بانتظار إعداد الصندوق", "") + '<div class="alert info">لم يُعدّ الصندوق الافتتاحي بعد. يجب أن يدخل المدير لإعداده (أو تفعيل المزامنة لجلب البيانات من جهاز آخر).</div>';
        return;
    }
    if (!setupDraft) {
        const s = clone(settingsRec());
        setupDraft = { step: 1, currencies: s.currencies, rates: clone(S.rates || {}), opening: {} };
        s.currencies.forEach(function (c) { setupDraft.opening[c.code] = { qty: "", rate: c.code === "USD" ? "1" : "" }; });
    }
    const d = setupDraft;
    const steps = ["العملات", "أسعار الشراء والبيع", "الصندوق الافتتاحي"];
    let html = '<div class="setup">' + pageHead("إعداد الصندوق الافتتاحي", "خطوة " + d.step + " من 3 — يمكن تعديل هذه البيانات لاحقاً قبل أول عملية فقط.") +
        '<div class="steps">' + steps.map(function (t, i) { return '<span class="' + (i + 1 === d.step ? "on" : "") + '">' + (i + 1) + ". " + t + "</span>"; }).join("") + "</div>";

    if (d.step === 1) {
        html += '<div class="card"><h3>العملات</h3><p class="muted small">العملة الرسمية في العرض: الليرة السورية الجديدة، ثم الدولار، ثم الليرة التركية، ثم اليورو (اختياري). الدولار هو عملة القياس المحاسبي للأرباح ولا يمكن تعطيله.</p>' +
            '<div class="table-wrap"><table><thead><tr><th>مفعّلة</th><th>الاسم</th><th>الرمز (Code)</th><th>العلامة</th><th>المنازل العشرية</th><th>القوة في التسعير</th></tr></thead><tbody>' +
            d.currencies.map(function (c, i) {
                const fixed = c.code === "USD" || c.code === "SYP";
                return "<tr><td><input type=\"checkbox\" data-c=\"enabled\" data-i=\"" + i + "\"" + (c.enabled ? " checked" : "") + (fixed ? " disabled" : "") + ' style="width:22px;min-height:22px"></td>' +
                    '<td><input data-c="name" data-i="' + i + '" value="' + esc(c.name) + '"></td>' +
                    '<td><input data-c="code" data-i="' + i + '" value="' + esc(c.code) + '"' + (fixed ? " readonly" : "") + ' maxlength="5" style="width:90px"></td>' +
                    '<td><input data-c="symbol" data-i="' + i + '" value="' + esc(c.symbol) + '" style="width:80px"></td>' +
                    '<td><input data-c="decimals" data-i="' + i + '" value="' + c.decimals + '" class="num-in" style="width:70px"></td>' +
                    '<td><input data-c="strength" data-i="' + i + '" value="' + c.strength + '" class="num-in" style="width:70px"></td></tr>';
            }).join("") + "</tbody></table></div>" +
            '<p class="muted small">«القوة في التسعير» تحدد اتجاه السعر: العملة الأقوى تكون أساس الزوج (مثلاً 1 دولار = 112 ليرة، و1 يورو = 1.16 دولار).</p></div>' +
            '<div class="row"><button class="btn primary big" data-act="s-next">التالي</button><span class="spacer"></span>' +
            '<button class="btn" data-act="s-sync">لدي بيانات على جهاز آخر (المزامنة)</button><button class="btn" data-act="s-restore">استعادة نسخة احتياطية</button></div>';
    } else if (d.step === 2) {
        const tmp = Object.assign({}, S, { currencies: d.currencies });
        html += '<div class="card"><h3>أسعار الشراء والبيع</h3><p class="muted small">سعر الشراء: حين يستلم الصراف العملة الأساس من الزبون. سعر البيع: حين يسلّمها. أسعار أزواج الدولار مطلوبة لأنها تحدد سعر التقييم المرجعي.</p>' +
            ratesTable(E.allPairs(tmp), d.rates, tmp, true) + "</div>" +
            '<div class="row"><button class="btn" data-act="s-back">السابق</button><button class="btn primary big" data-act="s-next">التالي</button></div>';
    } else {
        const tmp = Object.assign({}, S, { currencies: d.currencies, rates: d.rates });
        const refs = E.refRates(tmp);
        html += '<div class="card"><h3>الرصيد الافتتاحي وتكلفته</h3><p class="muted small">أدخل الرصيد الموجود فعلاً لكل عملة وتكلفته (بالسعر الذي اشتريته به). يمكن إدخال صفر. تكلفة الدولار = 1 دائماً.</p>' +
            '<div class="table-wrap"><table><thead><tr><th>العملة</th><th>الرصيد الافتتاحي</th><th>متوسط التكلفة</th><th>تكلفة الوحدة بالدولار</th><th>القيمة الإجمالية بالدولار</th></tr></thead><tbody>' +
            E.enabledCurrencies(tmp).map(function (c) {
                const o = d.opening[c.code] || (d.opening[c.code] = { qty: "", rate: "" });
                const orient = E.pairOf(tmp, "USD", c.code).base === "USD" ? c.symbol + " لكل 1 $" : "$ لكل 1 " + c.symbol;
                const suggest = refs[c.code] ? (E.pairOf(tmp, "USD", c.code).base === "USD" ? refs[c.code] : E.ONE.div(refs[c.code])) : null;
                return "<tr><td><b>" + esc(c.name) + "</b> <span class=\"muted\">" + esc(c.code) + "</span></td>" +
                    '<td><input class="num-in" inputmode="decimal" data-o="qty" data-cur="' + c.code + '" value="' + esc(o.qty) + '" placeholder="0"></td>' +
                    "<td>" + (c.code === "USD" ? "1" : '<input class="num-in" inputmode="decimal" data-o="rate" data-cur="' + c.code + '" value="' + esc(o.rate) + '" placeholder="' + (suggest ? fmtTrim(suggest, 4) : "") + '"><span class="hint muted small">' + esc(orient) + "</span>") + "</td>" +
                    '<td class="num" data-unit="' + c.code + '"></td><td class="num" data-total="' + c.code + '"></td></tr>';
            }).join("") + '<tr class="total"><td colspan="4">إجمالي قيمة الصندوق الافتتاحي</td><td class="num" id="openTotal"></td></tr></tbody></table></div>' +
            '<p class="error-text" id="setupErr"></p></div>' +
            '<div class="row"><button class="btn" data-act="s-back">السابق</button><button class="btn success big" data-act="s-save">حفظ وبدء العمل</button></div>';
    }
    html += "</div>";
    main.innerHTML = html;

    $$("[data-c]", main).forEach(function (el) {
        el.addEventListener("input", function () {
            const c = d.currencies[Number(el.dataset.i)], k = el.dataset.c;
            if (k === "enabled") c.enabled = el.checked;
            else if (k === "decimals" || k === "strength") c[k] = parseInt(el.value, 10) || 0;
            else if (k === "code") c.code = el.value.trim().toUpperCase();
            else c[k] = el.value;
        });
        el.addEventListener("change", function () { el.dispatchEvent(new Event("input")); });
    });
    bindRateInputs(main, d.rates);
    function calcOpening() {
        const tmp = Object.assign({}, S, { currencies: d.currencies, rates: d.rates });
        let total = E.ZERO;
        E.enabledCurrencies(tmp).forEach(function (c) {
            const o = d.opening[c.code];
            const q = E.parse(o.qty) || E.ZERO;
            let unit = null;
            if (c.code === "USD") unit = E.ONE;
            else {
                const r = E.parse(o.rate || ($('[data-o="rate"][data-cur="' + c.code + '"]', main) || {}).placeholder);
                if (r && r.gt(0)) unit = E.pairOf(tmp, "USD", c.code).base === "USD" ? E.ONE.div(r) : r;
            }
            const uEl = $('[data-unit="' + c.code + '"]', main), tEl = $('[data-total="' + c.code + '"]', main);
            if (uEl) uEl.textContent = unit ? fmtTrim(unit, 8) : "—";
            if (tEl) tEl.textContent = unit ? fmt(q.times(unit), 2) + " $" : "—";
            if (unit) total = total.plus(q.times(unit));
        });
        const t = $("#openTotal", main);
        if (t) t.textContent = fmt(total, 2) + " $";
    }
    $$("[data-o]", main).forEach(function (el) {
        el.addEventListener("input", function () { d.opening[el.dataset.cur][el.dataset.o] = el.value; calcOpening(); });
    });
    if (d.step === 3) calcOpening();

    main.onclick = async function (e) {
        const b = e.target.closest("[data-act]");
        if (!b) return;
        const act = b.dataset.act;
        if (act === "s-sync") { go("settings"); setTimeout(function () { const el = $("#syncCard"); if (el) el.scrollIntoView(); }, 50); return; }
        if (act === "s-restore") { pickRestoreFile(); return; }
        if (act === "s-back") { d.step--; render(); return; }
        if (act === "s-next") {
            if (d.step === 1) {
                const codes = {};
                for (const c of d.currencies) {
                    if (!/^[A-Z]{2,5}$/.test(c.code)) { toast("رمز العملة يجب أن يكون من 2 إلى 5 أحرف إنجليزية: " + esc(c.code || "فارغ"), "bad"); return; }
                    if (codes[c.code]) { toast("رمز العملة مكرر: " + esc(c.code), "bad"); return; }
                    codes[c.code] = 1;
                    if (!c.name.trim()) { toast("اسم العملة مطلوب", "bad"); return; }
                    if (c.decimals < 0 || c.decimals > 8) { toast("المنازل العشرية بين 0 و8", "bad"); return; }
                }
            }
            if (d.step === 2) {
                const tmp = Object.assign({}, S, { currencies: d.currencies, rates: d.rates });
                const err = validateRates(tmp, d.rates, true);
                if (err) { toast(err, "bad"); return; }
            }
            d.step++; render(); return;
        }
        if (act === "s-save") {
            const tmp = Object.assign({}, S, { currencies: d.currencies, rates: d.rates });
            const balances = {};
            for (const c of E.enabledCurrencies(tmp)) {
                const o = d.opening[c.code];
                const q = o.qty === "" ? E.ZERO : E.parse(o.qty);
                if (q === null) { $("#setupErr").textContent = "رصيد غير صالح لـ " + c.name; return; }
                if (q.lt(0)) { $("#setupErr").textContent = "لا يجوز إدخال رصيد سالب (" + c.name + ")"; return; }
                let unit = E.ONE;
                if (c.code !== "USD") {
                    const ph = ($('[data-o="rate"][data-cur="' + c.code + '"]', main) || {}).placeholder;
                    const r = E.parse(o.rate || ph);
                    if (q.gt(0) && (!r || r.lte(0))) { $("#setupErr").textContent = "أدخل تكلفة صحيحة لرصيد " + c.name; return; }
                    unit = r && r.gt(0) ? (E.pairOf(tmp, "USD", c.code).base === "USD" ? E.ONE.div(r) : r) : E.ZERO;
                }
                balances[c.code] = { qty: q.toFixed(), unitCost: unit.toFixed() };
            }
            const ok = await confirmBox("حفظ الإعداد الافتتاحي", "سيُحفظ الصندوق الافتتاحي والأسعار. يمكن تعديلها لاحقاً من الإعدادات قبل أول عملية فقط.", "حفظ");
            if (!ok) return;
            try {
                const set = clone(settingsRec());
                set.currencies = d.currencies;
                const changes = [set].concat(rateRecords(d.rates, tmp));
                changes.push({ id: "opening", type: "opening", day: "", at: nowISO(), balances: balances });
                await commit(changes);
                setupDraft = null;
                toast("تم إعداد الصندوق. يمكنك البدء بالعمليات.", "ok");
                go("dashboard");
            } catch (err) { $("#setupErr").textContent = failMsg(err); }
        }
    };
}

/* =========================================================
   لوحة التحكم
   ========================================================= */
function sumDays(filterFn) {
    const t = E.emptyDay("");
    Object.keys(ST.days).forEach(function (k) {
        if (!filterFn(k)) return;
        const a = ST.days[k];
        t.count += a.count; t.profit = t.profit.plus(a.profit); t.loss = t.loss.plus(a.loss); t.net = t.net.plus(a.net);
        t.valuation = t.valuation.plus(a.valuation); t.inventory = t.inventory.plus(a.inventory);
        t.withdrawals = t.withdrawals.plus(a.withdrawals); t.capitalIn = t.capitalIn.plus(a.capitalIn); t.capitalOut = t.capitalOut.plus(a.capitalOut);
        t.sypEquivalent = t.sypEquivalent.plus(a.sypEquivalent); t.withdrawalCount += a.withdrawalCount; t.cashCount += a.cashCount;
        Object.keys(a.pairs).forEach(function (p) { t.pairs[p] = (t.pairs[p] || 0) + a.pairs[p]; });
        Object.keys(a.curCount).forEach(function (p) { t.curCount[p] = (t.curCount[p] || 0) + a.curCount[p]; });
    });
    return t;
}
function curCard(c) {
    const st = ST.curs[c.code] || { qty: E.ZERO, book: E.ZERO };
    const avg = st.qty.gt(0) ? st.book.div(st.qty) : null;
    return '<div class="kpi cur-card' + (st.qty.lt(0) ? " negative" : "") + '"><span class="code">' + esc(c.code) + '</span><div class="label">' + esc(c.name) + "</div>" +
        '<div class="value ' + (st.qty.lt(0) ? "neg" : "") + '">' + fmtCur(st.qty, c.code) + "</div>" +
        '<div class="sub">التكلفة: ' + (avg ? esc(fmtCostRate(avg, c.code)) : "—") + "</div>" +
        '<div class="sub">القيمة الدفترية: ' + fmtUSD(st.book) + "</div>" +
        (st.lastAt ? '<div class="sub">آخر حركة: ' + esc(fmtDateTime(st.lastAt)) + "</div>" : "") + "</div>";
}
function barChart(items, opts) {
    opts = opts || {};
    const W = 640, H = opts.height || 200, pad = 26, n = Math.max(items.length, 1);
    const series = opts.series || [{ key: "value", color: null }];
    let max = 0, min = 0;
    items.forEach(function (it) { series.forEach(function (s) { max = Math.max(max, it[s.key] || 0); min = Math.min(min, it[s.key] || 0); }); });
    if (max === min) max = min + 1;
    const span = max - min, plotH = H - pad * 2;
    const y = function (v) { return pad + (max - v) / span * plotH; };
    const bw = (W - pad * 2) / n;
    let svg = '<svg class="chart" viewBox="0 0 ' + W + " " + H + '" role="img" aria-label="' + esc(opts.title || "رسم بياني") + '">';
    svg += '<line class="axis" x1="' + pad + '" x2="' + (W - pad) + '" y1="' + y(0) + '" y2="' + y(0) + '"/>';
    items.forEach(function (it, i) {
        const x0 = W - pad - (i + 1) * bw; /* من اليمين لليسار */
        const inner = bw * 0.7 / series.length;
        series.forEach(function (s, j) {
            const v = it[s.key] || 0;
            const color = s.color || (v >= 0 ? "var(--success)" : "var(--danger)");
            const top = Math.min(y(v), y(0)), h = Math.max(Math.abs(y(v) - y(0)), v ? 1 : 0);
            svg += '<rect x="' + (x0 + bw * 0.15 + j * inner).toFixed(1) + '" y="' + top.toFixed(1) + '" width="' + inner.toFixed(1) + '" height="' + h.toFixed(1) + '" rx="2" fill="' + color + '"><title>' + esc(it.label + ": " + (opts.fmt ? opts.fmt(v) : v)) + "</title></rect>";
        });
        if (n <= 16 || i % Math.ceil(n / 12) === 0) svg += '<text x="' + (x0 + bw / 2).toFixed(1) + '" y="' + (H - 6) + '" text-anchor="middle">' + esc(it.short || it.label) + "</text>";
    });
    svg += '<text x="' + (W - pad) + '" y="14" text-anchor="end">' + esc(opts.fmt ? opts.fmt(max) : max) + "</text>";
    if (min < 0) svg += '<text x="' + (W - pad) + '" y="' + (H - pad + 12) + '" text-anchor="end">' + esc(opts.fmt ? opts.fmt(min) : min) + "</text>";
    return svg + "</svg>";
}
function renderDashboard(main) {
    const today = openDay();
    const a = ST.days[today] || E.emptyDay(today);
    const all = sumDays(function () { return true; });
    const exOps = byType("exchange").sort(E.sortLedger);
    const last = exOps[exOps.length - 1];
    let topPair = "—", topN = 0;
    Object.keys(all.pairs).forEach(function (p) { if (all.pairs[p] > topN) { topN = all.pairs[p]; topPair = p; } });
    const unreal = E.unrealized(ST, S);
    const withdrawn = all.withdrawals;
    const available = all.net.plus(all.valuation).plus(all.inventory).minus(withdrawn);
    const lastUpdate = Array.from(records.values()).reduce(function (m, r) { return r.updatedAt > m ? r.updatedAt : m; }, "");

    /* بيانات الرسوم */
    const days30 = [];
    for (let i = 0; i < 30; i++) {
        const d = E.addDays(today, -i), x = ST.days[d];
        days30.push({ label: fmtDay(d), short: d.slice(8), value: x ? numOut(x.net) : 0, profit: x ? numOut(x.profit) : 0, loss: x ? numOut(x.loss) : 0 });
    }
    const months = [];
    for (let i = 0; i < 12; i++) {
        const dt = new Date(today.slice(0, 7) + "-01T00:00:00Z");
        dt.setUTCMonth(dt.getUTCMonth() - i);
        const ym = dt.toISOString().slice(0, 7);
        const m = sumDays(function (k) { return k.slice(0, 7) === ym; });
        months.push({ label: monthName(ym), short: ym.slice(5), value: numOut(m.net) });
    }
    const curCounts = E.enabledCurrencies(S).map(function (c) { return { label: c.name, short: c.code, value: all.curCount[c.code] || 0 }; });
    const fmtUsd = function (v) { return fmt(v, 2) + " $"; };

    main.innerHTML = pageHead("لوحة التحكم", "اليوم المحاسبي المفتوح: " + fmtDay(today) + " — آخر تحديث: " + esc(fmtDateTime(lastUpdate)),
        can("exchange") ? '<button class="btn primary big" data-go="exchange">⇄ عملية صرافة جديدة</button>' : "") +
        globalAlerts() +
        '<h3>أرصدة الصندوق</h3><div class="grid g4" style="margin-bottom:16px">' + E.enabledCurrencies(S).map(curCard).join("") + "</div>" +
        '<h3>أرباح اليوم</h3><div class="grid g4" style="margin-bottom:16px">' +
        '<div class="kpi profit"><div class="label">أرباح اليوم</div><div class="value pos">' + money(a.profit, undefined, { noColor: true }) + "</div></div>" +
        '<div class="kpi loss"><div class="label">خسائر اليوم</div><div class="value neg">' + money(a.loss, undefined, { noColor: true }) + "</div></div>" +
        '<div class="kpi money"><div class="label">صافي اليوم بالدولار</div><div class="value ' + cls(a.net) + '">' + fmtUSD(a.net) + "</div></div>" +
        '<div class="kpi money"><div class="label">صافي اليوم بالليرة السورية</div><div class="value ' + cls(a.net) + '">' + (dispRate() ? fmtCur(a.net.times(dispRate()), S.display) : "—") + "</div></div></div>" +
        '<h3>إحصائيات العمليات</h3><div class="grid g4" style="margin-bottom:16px">' +
        '<div class="kpi info"><div class="label">عدد عمليات اليوم</div><div class="value">' + a.count + "</div></div>" +
        '<div class="kpi info"><div class="label">إجمالي عدد العمليات</div><div class="value">' + all.count + "</div></div>" +
        '<div class="kpi info"><div class="label">أكثر زوج تداولاً</div><div class="value ltr">' + esc(topPair) + '</div><div class="sub">' + (topN ? topN + " عملية" : "") + "</div></div>" +
        '<div class="kpi info"><div class="label">آخر عملية</div><div class="value" style="font-size:16px">' + (last ? '<span class="ltr">' + esc(last.no) + "</span><br>استلام " + fmtCur(last.inAmt, last.inCur) + "<br>تسليم " + fmtCur(last.outAmt, last.outCur) : "—") + '</div><div class="sub">' + (last ? esc(fmtDateTime(last.at)) : "") + "</div></div></div>" +
        '<div class="grid g3" style="margin-bottom:16px">' +
        '<div class="kpi"><div class="label">صافي الأرباح التراكمي (الصرافة)</div><div class="value">' + money(all.net) + "</div></div>" +
        '<div class="kpi"><div class="label">الأرباح المتاحة للسحب (بعد التسويات والسحوبات)</div><div class="value">' + money(available) + '</div><div class="sub">المسحوب حتى الآن: ' + fmtUSD(withdrawn) + "</div></div>" +
        '<div class="kpi"><div class="label">أرباح غير محققة (إعادة تقييم بالسعر المرجعي)</div><div class="value">' + money(unreal.total) + '</div><div class="sub">ليست جزءاً من أرباح الصرافة</div></div></div>' +
        '<div class="grid g2">' +
        '<div class="card"><h3>الأرباح اليومية (آخر 30 يوماً)</h3>' + barChart(days30, { fmt: fmtUsd, title: "الأرباح اليومية" }) + "</div>" +
        '<div class="card"><h3>الأرباح الشهرية (آخر 12 شهراً)</h3>' + barChart(months, { fmt: fmtUsd, title: "الأرباح الشهرية" }) + "</div>" +
        '<div class="card"><h3>مقارنة الأرباح والخسائر (آخر 14 يوماً)</h3>' + barChart(days30.slice(0, 14), { fmt: fmtUsd, title: "الأرباح والخسائر", series: [{ key: "profit", color: "var(--success)" }, { key: "loss", color: "var(--danger)" }] }) +
        '<div class="legend"><span><i style="background:var(--success)"></i>أرباح</span><span><i style="background:var(--danger)"></i>خسائر</span></div></div>' +
        '<div class="card"><h3>عدد العمليات حسب العملات</h3>' + barChart(curCounts, { title: "العمليات حسب العملات", series: [{ key: "value", color: "var(--plum-300)" }] }) + "</div></div>";
    main.onclick = function (e) { const b = e.target.closest("[data-go]"); if (b) go(b.dataset.go); };
}

/* =========================================================
   أسعار الشراء والبيع
   ========================================================= */
function rateId(key) { return "rate-" + key.replace("/", "-"); }
function pairText(p, s) {
    const b = E.curInfo(s || S, p.base), q = E.curInfo(s || S, p.quote);
    return "1 " + (b ? b.name : p.base) + " = ؟ " + (q ? q.name : p.quote);
}
function ratesTable(pairs, rates, s, editable) {
    if (!pairs.length) return '<p class="empty">لا توجد أزواج (فعّل عملتين على الأقل).</p>';
    return '<div class="table-wrap"><table><thead><tr><th>الزوج</th><th>اتجاه التسعير</th><th>سعر الشراء</th><th>سعر البيع</th><th>الفرق</th><th>آخر تعديل</th></tr></thead><tbody>' +
        pairs.map(function (p) {
            const r = rates[p.key] || {};
            const b = E.parse(r.buy), sl = E.parse(r.sell);
            return "<tr><td class=\"ltr\"><b>" + esc(p.key) + "</b></td><td class=\"small muted\">" + esc(pairText(p, s)) + "</td>" +
                '<td><input class="num-in" inputmode="decimal" data-rate="' + esc(p.key) + '" data-side="buy" value="' + esc(r.buy || "") + '"' + (editable ? "" : " readonly") + ' style="min-width:110px"></td>' +
                '<td><input class="num-in" inputmode="decimal" data-rate="' + esc(p.key) + '" data-side="sell" value="' + esc(r.sell || "") + '"' + (editable ? "" : " readonly") + ' style="min-width:110px"></td>' +
                '<td class="num" data-spread="' + esc(p.key) + '">' + (b && sl ? fmtTrim(sl.minus(b), 6) : "—") + "</td>" +
                '<td class="small muted">' + (r.updatedAt ? esc(fmtDateTime(r.updatedAt)) + (r.by ? " — " + esc(r.by) : "") : "—") + "</td></tr>";
        }).join("") + "</tbody></table></div>";
}
function bindRateInputs(root, rates) {
    $$("[data-rate]", root).forEach(function (el) {
        el.addEventListener("input", function () {
            const k = el.dataset.rate;
            rates[k] = Object.assign({}, rates[k] || {}, { [el.dataset.side]: el.value.trim() });
            const b = E.parse(rates[k].buy), s = E.parse(rates[k].sell);
            const sp = $('[data-spread="' + k + '"]', root);
            if (sp) sp.textContent = b && s ? fmtTrim(s.minus(b), 6) : "—";
            el.classList.toggle("invalid", el.value.trim() !== "" && (!E.parse(el.value) || E.parse(el.value).lte(0)));
        });
    });
}
function validateRates(s, rates, requireUsd) {
    const pairs = E.allPairs(s);
    for (const p of pairs) {
        const r = rates[p.key] || {};
        const any = (r.buy || "") !== "" || (r.sell || "") !== "";
        const usdPair = p.base === s.base || p.quote === s.base;
        if (!any && !(requireUsd && usdPair)) continue;
        const b = E.parse(r.buy), sl = E.parse(r.sell);
        if (!b || b.lte(0) || !sl || sl.lte(0)) return "أدخل سعري شراء وبيع موجبين للزوج " + p.key + (usdPair ? " (مطلوب للتقييم المرجعي)" : "");
    }
    return null;
}
function rateRecords(rates, s) {
    const out = [];
    E.allPairs(s).forEach(function (p) {
        const r = rates[p.key];
        if (!r || !E.parse(r.buy) || !E.parse(r.sell)) return;
        const old = records.get(rateId(p.key));
        if (old && !old.deleted && E.dec(old.buy).eq(E.dec(r.buy)) && E.dec(old.sell).eq(E.dec(r.sell))) return;
        out.push({ id: rateId(p.key), type: "rate", pair: p.key, buy: E.dec(r.buy).toFixed(), sell: E.dec(r.sell).toFixed(), createdAt: old ? old.createdAt : undefined });
    });
    return out;
}
function renderRates(main) {
    const draft = clone(S.rates);
    const editable = can("rates");
    const refs = ST.refs;
    main.innerHTML = pageHead("أسعار الشراء والبيع", "تُستدعى تلقائياً عند اختيار العملات، ويمكن تعديل السعر داخل العملية دون تغيير السعر الافتراضي. العمليات المحفوظة تحتفظ بسعرها الفعلي.") +
        '<div class="card">' + ratesTable(E.allPairs(S), draft, S, editable) +
        '<p class="muted small">الاتجاه: «1 أساس = س مقابل». إذا استلم الصراف العملة الأساس فهو يشتريها بسعر الشراء، وإذا سلّمها فهو يبيعها بسعر البيع.</p>' +
        (editable ? '<div class="row"><button class="btn primary big" data-act="save-rates">حفظ الأسعار</button><span class="muted small" id="ratesMsg"></span></div>' : '<div class="alert info">عرض فقط — تعديل الأسعار للمدير.</div>') + "</div>" +
        '<div class="card"><h3>أسعار التقييم المرجعية</h3><p class="muted small">تُستخدم لتقييم العملات بالدولار وحساب الأرباح وتحويلها إلى الليرة. الافتراضي: منتصف سعري الشراء والبيع مع الدولار (يمكن تجاوزه من الإعدادات). يُحفظ السعر المرجعي مع كل عملية فلا تتغير نتائجها لاحقاً.</p>' +
        '<div class="table-wrap"><table><thead><tr><th>العملة</th><th>وحدات لكل 1 دولار</th><th>المصدر</th></tr></thead><tbody>' +
        E.enabledCurrencies(S).map(function (c) {
            const r = refs[c.code];
            const src = c.code === S.base ? "عملة القياس" : (E.parse((S.refOverride || {})[c.code]) ? "محدد يدوياً" : "منتصف الشراء والبيع");
            return "<tr><td>" + esc(c.name) + '</td><td class="num">' + (r ? fmtTrim(r, 6) : '<span class="neg">غير متوفر</span>') + "</td><td>" + src + "</td></tr>";
        }).join("") + "</tbody></table></div></div>";
    bindRateInputs(main, draft);
    main.onclick = async function (e) {
        const b = e.target.closest("[data-act]");
        if (!b || b.dataset.act !== "save-rates") return;
        const err = validateRates(S, draft, true);
        if (err) { toast(err, "bad"); return; }
        const warn = E.allPairs(S).filter(function (p) { const r = draft[p.key]; return r && E.parse(r.buy) && E.parse(r.sell) && E.parse(r.buy).gt(E.parse(r.sell)); });
        if (warn.length && !(await confirmBox("تنبيه", "سعر الشراء أعلى من سعر البيع في: " + warn.map(function (p) { return esc(p.key); }).join("، ") + ". هل تريد المتابعة؟", "متابعة"))) return;
        const changes = rateRecords(draft, S);
        if (!changes.length) { toast("لا تغييرات للحفظ", "info"); return; }
        b.disabled = true;
        try { await commit(changes, { validate: false }); toast("تم حفظ الأسعار (" + changes.length + " زوج)", "ok"); render(); }
        catch (er) { toast(failMsg(er), "bad"); b.disabled = false; }
    };
}

/* =========================================================
   عملية صرافة جديدة
   ========================================================= */
function nextNo(prefix, type) {
    const dev = deviceCode(), re = new RegExp("^" + (prefix || "") + dev + "-(\\d+)$");
    let max = 0;
    records.forEach(function (r) {
        if (r.type !== type) return;
        const m = re.exec(r.no || "");
        if (m) max = Math.max(max, Number(m[1]));
    });
    return (prefix || "") + dev + "-" + (max + 1);
}
function newExForm(keep) {
    const en = E.enabledCurrencies(S).map(function (c) { return c.code; });
    const inCur = keep && en.indexOf(keep.inCur) >= 0 ? keep.inCur : (en.indexOf("USD") >= 0 ? "USD" : en[0]);
    let outCur = keep && en.indexOf(keep.outCur) >= 0 ? keep.outCur : (en.indexOf(S.display) >= 0 ? S.display : en[1]);
    if (outCur === inCur) outCur = en.find(function (c) { return c !== inCur; });
    const f = { id: uid("x"), editId: null, inCur: inCur, outCur: outCur, inAmt: "", rate: "", outAmt: "", basis: "rate", costMethod: S.costMethod || "avg", manualRate: "", note: "", saving: false };
    const r = E.defaultRate(S, inCur, outCur);
    f.rate = r ? r.toFixed() : "";
    return f;
}
function exCalc(f) {
    /* حساب العملية الحالية مقابل الأرصدة الحالية (للمعاينة) */
    const inA = E.parse(f.inAmt), outA = E.parse(f.outAmt), rate = E.parse(f.rate);
    const res = { ok: false, inA: inA, outA: outA, rate: rate };
    const c = ST.curs[f.outCur] || { qty: E.ZERO, book: E.ZERO };
    let available = c.qty;
    if (f.editId) {
        const orig = records.get(f.editId);
        if (orig && orig.outCur === f.outCur) available = available.plus(E.dec(orig.outAmt));
        if (orig && orig.inCur === f.outCur) available = available.minus(E.dec(orig.inAmt));
    }
    res.available = available;
    const refs = ST.refs;
    const unit = f.outCur === S.base ? E.ONE : (c.qty.gt(0) ? c.book.div(c.qty) : (c.lastAvg || (refs[f.outCur] ? E.ONE.div(refs[f.outCur]) : E.ZERO)));
    res.unit = unit;
    if (!inA || !outA || inA.lte(0) || outA.lte(0)) return res;
    const bookCost = outA.times(unit);
    const manualUnit = f.costMethod === "manual" && f.outCur !== S.base ? rateToUnitCost(f.manualRate, f.outCur) : null;
    const effCost = manualUnit ? outA.times(manualUnit) : bookCost;
    let valueIn;
    if (f.inCur === S.base) valueIn = inA;
    else if (S.valuation === "cost") valueIn = effCost;
    else valueIn = refs[f.inCur] ? inA.div(refs[f.inCur]) : effCost;
    const refIn = f.inCur === S.base ? E.ONE : refs[f.inCur], refOut = f.outCur === S.base ? E.ONE : refs[f.outCur];
    res.ok = true;
    res.bookCost = bookCost; res.effCost = effCost; res.valueIn = valueIn; res.manualUnit = manualUnit;
    res.profit = valueIn.minus(effCost);
    res.valuation = effCost.minus(bookCost);
    res.margin = refIn && refOut ? inA.div(refIn).minus(outA.div(refOut)) : null;
    res.effRate = E.rateFromAmounts(S, f.inCur, f.outCur, inA, outA);
    res.overdraw = outA.gt(available);
    return res;
}
function renderExchange(main) {
    if (!ui.ex) ui.ex = newExForm();
    const f = ui.ex;
    const editing = !!f.editId;
    const orig = editing ? records.get(f.editId) : null;
    const p = E.pairOf(S, f.inCur, f.outCur);
    const no = editing ? orig.no : nextNo("", "exchange");
    const day = editing ? orig.day : openDay();
    main.innerHTML = pageHead(editing ? "تعديل العملية " + esc(orig.no) : "عملية صرافة جديدة", "آخر حقل تعدّله (السعر أو المبلغ الناتج) هو أساس الحساب.") +
        (editing ? '<div class="alert warn">أنت تعدّل عملية محفوظة. ستُحفظ نسختها السابقة في سجل التدقيق ويعاد احتساب الأرصدة والأرباح اللاحقة. <button class="btn sm" data-act="cancel-edit">إلغاء التعديل</button></div>' : "") +
        (!can("exchange") ? '<div class="alert info">حسابك للعرض فقط.</div>' : "") +
        '<div class="ex-layout"><div class="card"><div class="grid g3 meta3" style="margin-bottom:12px">' +
        '<div class="field"><span>رقم العملية</span><input value="' + esc(no) + '" readonly class="num-in"></div>' +
        '<div class="field"><span>التاريخ</span><input value="' + esc(fmtDay(day)) + '" readonly class="num-in"></div>' +
        '<div class="field"><span>الوقت</span><input id="exClock" value="' + esc(editing ? fmtTime(orig.at) : fmtTime(nowISO())) + '" readonly class="num-in"></div></div>' +
        '<div class="ex-cur">' +
        '<label class="field"><span>العملة التي يستلمها الصراف</span><select id="exInCur">' + curOptions(f.inCur) + "</select></label>" +
        '<button class="swap-btn" data-act="swap" title="تبديل الاتجاه" aria-label="تبديل الاتجاه">⇄</button>' +
        '<label class="field"><span>العملة التي يسلّمها الصراف</span><select id="exOutCur">' + curOptions(f.outCur) + "</select></label></div>" +
        '<div class="grid g3" style="margin-top:12px">' +
        '<label class="field"><span>المبلغ المستلم (' + esc(sym(f.inCur)) + ')</span><input id="exInAmt" class="num-in big-in" inputmode="decimal" autocomplete="off" value="' + esc(f.inAmt) + '" placeholder="0"></label>' +
        '<label class="field"><span>سعر الصرف</span><input id="exRate" class="num-in big-in" inputmode="decimal" autocomplete="off" value="' + esc(f.rate) + '"><span class="hint ltr">1 ' + esc(p.base) + " = ? " + esc(p.quote) + "</span></label>" +
        '<label class="field"><span>المبلغ الناتج (' + esc(sym(f.outCur)) + ')</span><input id="exOutAmt" class="num-in big-in" inputmode="decimal" autocomplete="off" value="' + esc(f.outAmt) + '" placeholder="0"><span class="hint">يمكن تعديله يدوياً</span></label></div>' +
        '<div class="rate-line" id="exRateLine"></div><div id="exQuick" class="quick-profit"></div>' +
        '<div class="grid g2" style="margin-top:12px">' +
        '<div class="field"><span>طريقة حساب التكلفة</span><div class="seg" id="exCost"><button data-cm="avg" class="' + (f.costMethod !== "manual" ? "on" : "") + '">المتوسط المرجح</button><button data-cm="manual" class="' + (f.costMethod === "manual" ? "on" : "") + '"' + (f.outCur === S.base ? " disabled" : "") + '>تكلفة يدوية</button></div></div>' +
        '<label class="field' + (f.costMethod === "manual" && f.outCur !== S.base ? "" : " hidden") + '" id="exManualWrap"><span>سعر التكلفة اليدوي (' + esc(f.outCur !== S.base ? costOrient(f.outCur).label : "") + ')</span><input id="exManual" class="num-in" inputmode="decimal" value="' + esc(f.manualRate) + '"><span class="hint">يُوثَّق الفرق عن المتوسط كتسوية تقييم منفصلة.</span></label></div>' +
        '<label class="field" style="margin-top:12px"><span>ملاحظات (اختياري)</span><input id="exNote" value="' + esc(f.note) + '" maxlength="300"></label>' +
        '<p class="error-text" id="exErr"></p>' +
        '<div class="row"><button class="btn success big" data-act="save"' + (can("exchange") ? "" : " disabled") + ">" + (editing ? "حفظ التعديل" : "حفظ العملية") + '</button>' +
        '<button class="btn big" data-act="reset">تصفير العملية</button></div></div>' +
        '<div class="card preview" id="exSide"></div></div>';

    const inAmt = $("#exInAmt"), rateEl = $("#exRate"), outAmt = $("#exOutAmt");
    function recalcOut() {
        const o = E.computeOut(S, f.inCur, f.outCur, f.inAmt, f.rate);
        f.outAmt = o ? E.roundAmount(S, f.outCur, o).toFixed() : "";
        outAmt.value = f.outAmt;
    }
    function recalcRate() {
        const r = E.rateFromAmounts(S, f.inCur, f.outCur, f.inAmt, f.outAmt);
        f.rate = r ? r.toDecimalPlaces(8).toFixed() : f.rate;
        rateEl.value = f.rate;
    }
    inAmt.addEventListener("input", function () {
        f.inAmt = inAmt.value;
        if (f.basis === "out" && E.parse(f.outAmt)) recalcRate(); else recalcOut();
        exUpdatePreview();
    });
    rateEl.addEventListener("input", function () { f.rate = rateEl.value; f.basis = "rate"; recalcOut(); exUpdatePreview(); });
    outAmt.addEventListener("input", function () { f.outAmt = outAmt.value; f.basis = "out"; recalcRate(); exUpdatePreview(); });
    function changeCur(which, val) {
        const prevIn = f.inCur, prevOut = f.outCur;
        f[which] = val;
        if (f.inCur === f.outCur) { if (which === "inCur") f.outCur = prevIn; else f.inCur = prevOut; }
        const r = E.defaultRate(S, f.inCur, f.outCur);
        f.rate = r ? r.toFixed() : "";
        f.basis = "rate";
        if (f.outCur === S.base) f.costMethod = "avg";
        const o = E.computeOut(S, f.inCur, f.outCur, f.inAmt, f.rate);
        f.outAmt = o ? E.roundAmount(S, f.outCur, o).toFixed() : "";
        renderExchange(main);
        const el = $("#exInAmt"); if (el) el.focus();
    }
    $("#exInCur").addEventListener("change", function (e) { changeCur("inCur", e.target.value); });
    $("#exOutCur").addEventListener("change", function (e) { changeCur("outCur", e.target.value); });
    $("#exManual").addEventListener("input", function (e) { f.manualRate = e.target.value; exUpdatePreview(); });
    $("#exNote").addEventListener("input", function (e) { f.note = e.target.value; });
    [inAmt, rateEl, outAmt].forEach(function (el) {
        el.addEventListener("keydown", function (e) { if (e.key === "Enter") { e.preventDefault(); exSave(); } });
    });
    main.onclick = function (e) {
        const cm = e.target.closest("[data-cm]");
        if (cm && !cm.disabled) { f.costMethod = cm.dataset.cm; renderExchange(main); return; }
        const b = e.target.closest("[data-act]");
        if (!b) return;
        if (b.dataset.act === "swap") {
            const t = f.inCur; f.inCur = f.outCur; f.outCur = t;
            const r = E.defaultRate(S, f.inCur, f.outCur);
            f.rate = r ? r.toFixed() : ""; f.basis = "rate";
            if (f.outCur === S.base) f.costMethod = "avg";
            const o = E.computeOut(S, f.inCur, f.outCur, f.inAmt, f.rate);
            f.outAmt = o ? E.roundAmount(S, f.outCur, o).toFixed() : "";
            renderExchange(main);
        } else if (b.dataset.act === "reset") {
            /* لا يمس الأرصدة ولا العمليات السابقة */
            ui.ex = newExForm();
            renderExchange(main);
            $("#exInAmt").focus();
        } else if (b.dataset.act === "cancel-edit") {
            ui.ex = newExForm(); renderExchange(main);
        } else if (b.dataset.act === "save") exSave();
    };
    exUpdatePreview();
    renderExSide();
}
function exUpdatePreview() {
    const f = ui.ex;
    if (!f || !$("#exRateLine")) return;
    const r = exCalc(f);
    const def = E.defaultRate(S, f.inCur, f.outCur);
    let line = "";
    if (def) line += "السعر الافتراضي: <b class=\"num\">" + fmtTrim(def) + "</b>";
    else line += '<span class="warn-c">لا يوجد سعر افتراضي لهذا الزوج — أدخل السعر يدوياً أو من «أسعار الصرف».</span>';
    if (r.effRate) line += " — السعر الفعلي: <b class=\"num\">" + fmtTrim(r.effRate, 6) + "</b>";
    $("#exRateLine").innerHTML = line;
    const q = $("#exQuick");
    if (q) {
        q.className = "quick-profit " + (r.ok ? cls(r.profit) : "");
        q.innerHTML = r.ok ? "الربح المتوقع: " + money(r.profit) + (r.overdraw ? ' — <span class="neg">الرصيد غير كافٍ</span>' : "") : "";
    }
    ["#exInAmt", "#exRate", "#exOutAmt"].forEach(function (id) {
        const el = $(id), v = el.value.trim();
        el.classList.toggle("invalid", v !== "" && (!E.parse(v) || E.parse(v).lte(0)));
    });
    renderExSide(r);
}
function renderExSide(r) {
    const box = $("#exSide");
    if (!box || !ui.ex) return;
    const f = ui.ex;
    r = r || exCalc(f);
    const prof = r.ok ? r.profit : null;
    box.innerHTML = "<h3>المعاينة الفورية</h3>" +
        '<div class="profit-box ' + (prof ? cls(prof) : "") + '"><div class="muted small">الربح أو الخسارة المتوقعة</div><div class="big">' + (prof ? money(prof) : "—") + "</div></div>" +
        "<dl>" +
        "<dt>رصيد " + esc(curName(f.outCur)) + " المتاح</dt><dd class=\"" + (r.overdraw ? "neg" : "") + '">' + fmt(r.available, E.decimalsOf(S, f.outCur)) + " " + esc(sym(f.outCur)) + "</dd>" +
        "<dt>رصيد " + esc(curName(f.inCur)) + " الحالي</dt><dd>" + fmt((ST.curs[f.inCur] || { qty: E.ZERO }).qty, E.decimalsOf(S, f.inCur)) + " " + esc(sym(f.inCur)) + "</dd>" +
        "<dt>متوسط تكلفة " + esc(curName(f.outCur)) + "</dt><dd>" + esc(fmtCostRate(r.unit, f.outCur)) + "</dd>" +
        (r.ok ? "<dt>سعر التكلفة المستخدم</dt><dd>" + esc(r.manualUnit ? fmtCostRate(r.manualUnit, f.outCur) + " (يدوي)" : fmtCostRate(r.unit, f.outCur)) + "</dd>" +
            "<dt>التكلفة الدفترية للمسلَّم</dt><dd>" + fmt(r.bookCost, 2) + " $</dd>" +
            "<dt>قيمة المستلم بالدولار</dt><dd>" + fmt(r.valueIn, 2) + " $</dd>" +
            (r.manualUnit ? "<dt>تسوية تقييم (منفصلة)</dt><dd class=\"" + cls(r.valuation) + '">' + fmt(r.valuation, 2) + " $</dd>" : "") +
            (r.margin ? "<dt>هامش السعر مقابل المرجع</dt><dd>" + fmt(r.margin, 2) + " $</dd>" : "") : "") +
        "</dl>" +
        (r.overdraw ? '<div class="alert bad" style="margin-top:10px">المبلغ الناتج أكبر من الرصيد المتاح.</div>' : "") +
        (r.ok && r.margin && !r.margin.minus(r.profit).abs().lt("0.01") ? '<p class="muted small">الفرق بين هامش السعر والربح المحاسبي سببه أن تكلفة العملة المسلَّمة (متوسط شرائها) تختلف عن سعرها المرجعي الحالي.</p>' : "") +
        '<p class="muted small">الأرباح بالدولار (عملة القياس) ومقابلها بالليرة بالسعر المرجعي ' + (dispRate() ? fmtTrim(dispRate(), 4) : "—") + ".</p>";
}
async function exSave() {
    const f = ui.ex;
    const err = $("#exErr");
    if (!f || f.saving) return;
    if (!can("exchange")) return;
    err.textContent = "";
    const inA = E.parse(f.inAmt), rate = E.parse(f.rate);
    const outA = E.parse(f.outAmt);
    if (f.inCur === f.outCur) { err.textContent = "لا يجوز اختيار العملة نفسها في طرفي العملية."; return; }
    if (!inA || inA.lte(0)) { err.textContent = "أدخل مبلغاً مستلماً أكبر من صفر."; $("#exInAmt").focus(); return; }
    if (!rate || rate.lte(0)) { err.textContent = "سعر الصرف يجب أن يكون أكبر من صفر."; $("#exRate").focus(); return; }
    if (!outA || outA.lte(0)) { err.textContent = "المبلغ الناتج يجب أن يكون أكبر من صفر."; $("#exOutAmt").focus(); return; }
    const roundedOut = E.roundAmount(S, f.outCur, outA), roundedIn = E.roundAmount(S, f.inCur, inA);
    if (!roundedOut.eq(outA) || !roundedIn.eq(inA)) { err.textContent = "عدد المنازل العشرية أكبر من المسموح للعملة."; return; }
    if (f.costMethod === "manual" && f.outCur !== S.base && !rateToUnitCost(f.manualRate, f.outCur)) { err.textContent = "أدخل سعر تكلفة يدوياً صحيحاً."; return; }
    if (S.valuation !== "cost" && f.inCur !== S.base && !ST.refs[f.inCur]) { err.textContent = "لا يوجد سعر تقييم مرجعي لـ " + curName(f.inCur) + ". أدخل أسعار زوجها مع الدولار أولاً."; return; }
    const editing = !!f.editId;
    const orig = editing ? records.get(f.editId) : null;
    if (editing && (!orig || orig.deleted)) { err.textContent = "العملية الأصلية غير موجودة أو محذوفة."; return; }
    if (editing && isClosed(orig.day)) { err.textContent = "يوم هذه العملية مقفل. أعد فتح اليوم أولاً."; return; }
    if (!editing && records.has(f.id)) { err.textContent = "حُفظت هذه العملية مسبقاً."; return; }
    const r = exCalc(f);
    const manualUnit = f.costMethod === "manual" && f.outCur !== S.base ? rateToUnitCost(f.manualRate, f.outCur) : null;
    const now = nowISO();
    const rec = Object.assign(editing ? clone(orig) : {}, {
        id: editing ? orig.id : f.id, type: "exchange",
        no: editing ? orig.no : nextNo("", "exchange"),
        day: editing ? orig.day : openDay(), at: editing ? orig.at : now,
        inCur: f.inCur, outCur: f.outCur, inAmt: inA.toFixed(), outAmt: outA.toFixed(),
        rate: rate.toFixed(), pair: E.pairOf(S, f.inCur, f.outCur).key,
        defaultRate: (E.defaultRate(S, f.inCur, f.outCur) || "").toString(), basis: f.basis,
        costMethod: manualUnit ? "manual" : "avg", manualUnitCost: manualUnit ? manualUnit.toFixed() : "", manualRate: manualUnit ? f.manualRate : "",
        refRates: editing && orig.refRates ? orig.refRates : E.refRatesToStrings(ST.refs),
        note: f.note.trim()
    });
    if (r.overdraw && !editing) { err.textContent = "الرصيد غير كافٍ: المتاح " + fmt(r.available, E.decimalsOf(S, f.outCur)) + " " + sym(f.outCur) + "."; return; }
    const changes = [rec];
    if (editing) changes.push(audit("edit", rec, orig, ""));
    f.saving = true;
    const btn = $('[data-act="save"]');
    if (btn) btn.disabled = true;
    try {
        const nextState = await commit(changes, {
            check: function (next, st, s, prev) {
                if (!editing && prev.has(rec.id)) return "حُفظت هذه العملية مسبقاً.";
                if (!editing && isClosedIn(prev, rec.day)) return "أُقفل هذا اليوم للتو من جهاز أو تبويب آخر. أعد المحاولة.";
                return null;
            }
        });
        const res = nextState.results[rec.id];
        toast("تم حفظ العملية <b>" + esc(rec.no) + "</b> — " + (res ? money(res.profit) : ""), "ok");
        if (editing) {
            const stale = staleCloses();
            if (stale.length) toast("تأثرت أيام مقفلة لاحقة وتحتاج إعادة اعتماد: " + stale.map(fmtDay).join("، "), "warn", 8000);
            ui.ex = newExForm();
            go("log");
            return;
        }
        ui.ex = newExForm(f);
        renderExchange($("#main"));
        $("#exInAmt").focus();
    } catch (e2) {
        f.saving = false;
        if (btn) btn.disabled = false;
        err.textContent = failMsg(e2);
    }
}
function isClosedIn(map, day) { const c = map.get("close-" + day); return !!(c && !c.deleted && c.status === "closed"); }

/* =========================================================
   إدارة الصندوق
   ========================================================= */
const CASH_KINDS = { add: "إضافة رصيد", withdraw: "سحب رصيد", zero: "تصفير رصيد", replace: "استبدال الرصيد" };
const MOVE_NAMES = {
    "opening": "رصيد افتتاحي", "exchange-in": "صرافة: استلام", "exchange-out": "صرافة: تسليم",
    "cash-add": "إضافة رصيد", "cash-withdraw": "سحب رصيد", "cash-zero": "تصفير رصيد", "cash-replace": "استبدال رصيد",
    "profit-withdrawal": "سحب أرباح", "count-settlement": "تسوية جرد"
};
function renderCashbox(main) {
    const tab = ui.cashTab;
    const allowed = can("cash");
    const moves = ST.moves.filter(function (m) { return !ui.moveFilter || m.cur === ui.moveFilter; }).slice().reverse();
    const shown = moves.slice(0, ui.moveLimit);
    const unitHint = function (code) { return code === S.base ? "" : costOrient(code).label; };
    const firstCur = E.enabledCurrencies(S)[0].code;
    main.innerHTML = pageHead("إدارة الصندوق", "إضافات رأس المال والسحوبات والتسويات لا تُحتسب أرباحاً أو خسائر صرافة.") +
        globalAlerts() +
        '<div class="grid g4" style="margin-bottom:16px">' + E.enabledCurrencies(S).map(curCard).join("") + "</div>" +
        '<div class="card"><div class="seg" style="margin-bottom:14px">' + Object.keys(CASH_KINDS).map(function (k) {
            return '<button data-tab="' + k + '" class="' + (tab === k ? "on" : "") + '">' + CASH_KINDS[k] + "</button>";
        }).join("") + "</div>" +
        (allowed ? "" : '<div class="alert info">حركات الصندوق للمدير فقط.</div>') +
        '<div class="grid g3">' +
        '<label class="field"><span>العملة</span><select id="cbCur">' + curOptions(ui.cbCur || firstCur) + "</select></label>" +
        (tab === "add" || tab === "withdraw" ? '<label class="field"><span>المبلغ</span><input id="cbAmt" class="num-in" inputmode="decimal"></label>' : "") +
        (tab === "replace" ? '<label class="field"><span>الرصيد الجديد</span><input id="cbTarget" class="num-in" inputmode="decimal"></label>' : "") +
        (tab === "add" || tab === "replace" ? '<label class="field"><span>سعر التكلفة <span id="cbUnitHint"></span></span><input id="cbCost" class="num-in" inputmode="decimal" placeholder="فارغ = متوسط التكلفة الحالي"></label>' : "") +
        "</div>" +
        '<label class="field" style="margin-top:12px"><span>السبب</span><input id="cbReason" maxlength="200" placeholder="' + (tab === "add" ? "مثال: إضافة رأس مال" : "مثال: مصروف / سحب شخصي") + '"></label>' +
        '<div id="cbInfo" class="muted small" style="margin-top:8px"></div><p class="error-text" id="cbErr"></p>' +
        '<button class="btn ' + (tab === "zero" ? "danger" : "primary") + ' big" data-act="cash-save"' + (allowed ? "" : " disabled") + ">" + CASH_KINDS[tab] + "</button></div>" +
        '<div class="card"><div class="row" style="margin-bottom:10px"><h3 style="margin:0">سجل حركات الصندوق</h3><span class="spacer"></span>' +
        '<select id="mvCur" style="width:auto"><option value="">كل العملات</option>' + curOptions(ui.moveFilter, false) + "</select></div>" +
        '<div class="table-wrap"><table><thead><tr><th>التاريخ</th><th>الوقت</th><th>العملة</th><th>الرصيد السابق</th><th>التغيير</th><th>الرصيد الجديد</th><th>نوع الحركة</th><th>السبب</th><th>المرجع</th><th></th></tr></thead><tbody>' +
        (shown.length ? shown.map(function (m) {
            const rec = records.get(m.id);
            const canDel = allowed && rec && (rec.type === "cash") && !isClosed(rec.day);
            return "<tr><td>" + fmtDay(m.day) + '</td><td class="num">' + esc(fmtTime(m.at)) + "</td><td>" + esc(m.cur) + '</td><td class="num">' + fmt(m.before, E.decimalsOf(S, m.cur)) +
                '</td><td class="num ' + cls(m.delta) + '">' + (m.delta.gt(0) ? "+" : "") + fmt(m.delta, E.decimalsOf(S, m.cur)) + '</td><td class="num">' + fmt(m.after, E.decimalsOf(S, m.cur)) +
                "</td><td>" + esc(MOVE_NAMES[m.kind] || m.kind) + '</td><td class="wrap">' + esc(m.reason) + '</td><td class="ltr">' + esc(m.no || "") + "</td><td>" +
                (canDel ? '<button class="btn sm" data-del="' + esc(m.id) + '">حذف</button>' : "") + "</td></tr>";
        }).join("") : '<tr><td colspan="10" class="empty">لا توجد حركات</td></tr>') + "</tbody></table></div>" +
        (moves.length > shown.length ? '<div class="row" style="margin-top:10px"><button class="btn" data-act="more">عرض المزيد (' + (moves.length - shown.length) + ")</button></div>" : "") + "</div>";

    function info() {
        const code = $("#cbCur").value;
        ui.cbCur = code;
        const c = ST.curs[code] || { qty: E.ZERO, book: E.ZERO };
        const avg = c.qty.gt(0) ? c.book.div(c.qty) : null;
        const h = $("#cbUnitHint"); if (h) h.textContent = unitHint(code) ? "(" + unitHint(code) + ")" : "";
        const cost = $("#cbCost"); if (cost) cost.disabled = code === S.base;
        $("#cbInfo").innerHTML = "الرصيد الحالي: <b>" + fmtCur(c.qty, code) + "</b> — متوسط التكلفة: " + esc(avg ? fmtCostRate(avg, code) : "—");
    }
    $("#cbCur").addEventListener("change", info);
    info();
    $("#mvCur").addEventListener("change", function (e) { ui.moveFilter = e.target.value; render(); });
    main.onclick = async function (e) {
        const t = e.target.closest("[data-tab]");
        if (t) { ui.cashTab = t.dataset.tab; render(); return; }
        const del = e.target.closest("[data-del]");
        if (del) { deleteRecordFlow(del.dataset.del); return; }
        const b = e.target.closest("[data-act]");
        if (!b) return;
        if (b.dataset.act === "more") { ui.moveLimit += 300; render(); return; }
        if (b.dataset.act !== "cash-save") return;
        const err = $("#cbErr"); err.textContent = "";
        const code = $("#cbCur").value, reason = $("#cbReason").value.trim();
        const c = ST.curs[code] || { qty: E.ZERO };
        const rec = { id: uid("c"), type: "cash", kind: tab, cur: code, day: openDay(), at: nowISO(), reason: reason, no: nextNo("C", "cash") };
        let amount = null;
        if (tab === "add" || tab === "withdraw") {
            amount = E.parse($("#cbAmt").value);
            if (!amount || amount.lte(0)) { err.textContent = "أدخل مبلغاً أكبر من صفر."; return; }
            if (!E.roundAmount(S, code, amount).eq(amount)) { err.textContent = "عدد المنازل العشرية أكبر من المسموح."; return; }
            if (tab === "withdraw" && amount.gt(c.qty)) { err.textContent = "المبلغ أكبر من الرصيد المتاح."; return; }
            rec.amount = amount.toFixed();
        }
        if (tab === "replace") {
            const tg = E.parse($("#cbTarget").value);
            if (!tg || tg.lt(0)) { err.textContent = "أدخل رصيداً جديداً صحيحاً (صفر أو أكثر)."; return; }
            rec.target = tg.toFixed();
            rec.prevBalance = c.qty.toFixed();
        }
        if (tab === "zero") rec.prevBalance = c.qty.toFixed();
        const costEl = $("#cbCost");
        if (costEl && costEl.value.trim() && code !== S.base) {
            const u = rateToUnitCost(costEl.value, code);
            if (!u) { err.textContent = "سعر التكلفة غير صالح."; return; }
            rec.unitCost = u.toFixed(); rec.costRate = costEl.value.trim();
        }
        const needsCost = code !== S.base && (tab === "add" || (tab === "replace" && E.dec(rec.target).gt(c.qty))) && !rec.unitCost && !(c.qty.gt(0) || ST.refs[code]);
        if (needsCost) { err.textContent = "أدخل سعر التكلفة (لا يوجد متوسط تكلفة حالي)."; return; }
        if (!reason) { err.textContent = "اكتب سبب الحركة."; return; }
        const desc = tab === "zero" ? "تصفير رصيد " + curName(code) + " (" + fmt(c.qty, E.decimalsOf(S, code)) + ")" :
            tab === "replace" ? "استبدال رصيد " + curName(code) + " من " + fmt(c.qty, E.decimalsOf(S, code)) + " إلى " + fmt(rec.target, E.decimalsOf(S, code)) + " (الفرق " + fmt(E.dec(rec.target).minus(c.qty), E.decimalsOf(S, code)) + ")" :
                CASH_KINDS[tab] + ": " + fmt(amount, E.decimalsOf(S, code)) + " " + sym(code);
        if (!(await confirmBox(CASH_KINDS[tab], esc(desc) + "<br><span class=\"muted small\">لن تُحتسب هذه الحركة ربحاً أو خسارة.</span>", "تأكيد", tab === "zero"))) return;
        try { await commit([rec]); toast("تم: " + esc(desc), "ok"); render(); }
        catch (er) { err.textContent = failMsg(er); }
    };
}

/* حذف منطقي لأي سجل دفتري (يبقى أثره في سجل التدقيق) */
async function deleteRecordFlow(id) {
    const rec = records.get(id);
    if (!rec || rec.deleted) return;
    if (!can("editOp")) { toast("الحذف للمدير فقط", "bad"); return; }
    if (isClosed(rec.day)) { toast("يوم هذه الحركة مقفل. أعد فتح اليوم أولاً.", "bad"); return; }
    const reason = await promptBox("حذف " + (rec.no || ""), "سيُحذف السجل حذفاً منطقياً (يبقى في سجل التدقيق) ويعاد احتساب الأرصدة والأرباح.", "سبب الحذف", { danger: true, okText: "حذف" });
    if (!reason) return;
    const before = clone(rec);
    const upd = Object.assign(clone(rec), { deleted: true, deletedAt: nowISO(), deletedBy: me.username, deleteReason: reason });
    try {
        await commit([upd, audit("delete", upd, before, reason)]);
        toast("تم الحذف وإعادة الاحتساب", "ok");
        const stale = staleCloses();
        if (stale.length) toast("تأثرت أيام مقفلة لاحقة: " + stale.map(fmtDay).join("، "), "warn", 8000);
        render();
    } catch (e) { toast(failMsg(e), "bad", 8000); }
}

/* =========================================================
   سجل العمليات
   ========================================================= */
function filteredOps() {
    const f = ui.logFilter;
    let list = byType("exchange", !!f.deleted).sort(E.sortLedger).reverse();
    if (f.no) list = list.filter(function (r) { return (r.no || "").toLowerCase().indexOf(f.no.toLowerCase()) >= 0; });
    if (f.from) list = list.filter(function (r) { return r.day >= f.from; });
    if (f.to) list = list.filter(function (r) { return r.day <= f.to; });
    if (f.cur) list = list.filter(function (r) { return r.inCur === f.cur || r.outCur === f.cur; });
    if (f.pair) list = list.filter(function (r) { return E.pairOf(S, r.inCur, r.outCur).key === f.pair; });
    if (f.result === "profit") list = list.filter(function (r) { const x = ST.results[r.id]; return x && x.profit.gt(0); });
    if (f.result === "loss") list = list.filter(function (r) { const x = ST.results[r.id]; return x && x.profit.lt(0); });
    return list;
}
function renderLog(main) {
    const f = ui.logFilter;
    const list = filteredOps();
    const limit = f.limit || 300;
    let total = E.ZERO;
    list.forEach(function (r) { const x = ST.results[r.id]; if (x && !r.deleted) total = total.plus(x.profit); });
    main.innerHTML = pageHead("سجل العمليات", list.length + " عملية — صافي النتيجة: " + money(total)) +
        '<details class="card" id="lfBox"' + (window.innerWidth > 860 || Object.keys(f).some(function (k) { return f[k] && k !== "limit"; }) ? " open" : "") + '><summary>البحث والتصفية</summary><div class="grid g4" style="margin-top:10px">' +
        '<label class="field"><span>رقم العملية</span><input id="lfNo" value="' + esc(f.no || "") + '"></label>' +
        '<label class="field"><span>من تاريخ</span><input id="lfFrom" type="date" value="' + esc(f.from || "") + '"></label>' +
        '<label class="field"><span>إلى تاريخ</span><input id="lfTo" type="date" value="' + esc(f.to || "") + '"></label>' +
        '<label class="field"><span>العملة</span><select id="lfCur"><option value="">الكل</option>' + curOptions(f.cur, false) + "</select></label>" +
        '<label class="field"><span>زوج العملات</span><select id="lfPair"><option value="">الكل</option>' + E.allPairs(S).map(function (p) { return '<option' + (f.pair === p.key ? " selected" : "") + ">" + esc(p.key) + "</option>"; }).join("") + "</select></label>" +
        '<label class="field"><span>النتيجة</span><select id="lfRes"><option value="">الكل</option><option value="profit"' + (f.result === "profit" ? " selected" : "") + '>الرابحة</option><option value="loss"' + (f.result === "loss" ? " selected" : "") + ">الخاسرة</option></select></label>" +
        '<label class="check" style="align-self:end;min-height:44px"><input type="checkbox" id="lfDel"' + (f.deleted ? " checked" : "") + "> إظهار المحذوفة</label>" +
        '<div class="row" style="align-self:end"><button class="btn" data-act="clear">مسح التصفية</button><button class="btn" data-act="xlsx">Excel</button><button class="btn" data-act="print">طباعة</button></div></div></details>' +
        '<div class="table-wrap"><table><thead><tr><th>رقم</th><th>التاريخ</th><th>الوقت</th><th>المستلمة</th><th>المسلّمة</th><th>المبلغ المستلم</th><th>المبلغ المسلّم</th><th>سعر الصرف</th><th>سعر التكلفة</th><th>الربح/الخسارة $</th><th>بالليرة</th><th>ملاحظات</th><th></th></tr></thead><tbody>' +
        (list.length ? list.slice(0, limit).map(function (r) {
            const x = ST.results[r.id];
            const locked = isClosed(r.day);
            const unitOut = x ? (x.manualUnitCost || x.unitCostOut) : null;
            return '<tr class="' + (r.deleted ? "deleted" : "") + '"><td class="ltr"><b>' + esc(r.no) + "</b></td><td>" + fmtDay(r.day) + '</td><td class="num">' + esc(fmtTime(r.at)) + "</td><td>" + esc(r.inCur) + "</td><td>" + esc(r.outCur) +
                '</td><td class="num">' + fmt(r.inAmt, E.decimalsOf(S, r.inCur)) + '</td><td class="num">' + fmt(r.outAmt, E.decimalsOf(S, r.outCur)) + '</td><td class="num">' + fmtTrim(r.rate, 6) +
                '</td><td class="small">' + (unitOut ? esc(fmtCostRate(unitOut, r.outCur)) + (r.costMethod === "manual" ? " (يدوي)" : "") : "—") +
                '</td><td class="num ' + (x ? cls(x.profit) : "") + '">' + (x ? fmt(x.profit, 2) : "—") + '</td><td class="num ' + (x ? cls(x.profit) : "") + '">' + (x && x.sypRate ? fmt(x.profit.times(x.sypRate), E.decimalsOf(S, S.display)) : "—") +
                '</td><td class="wrap small">' + esc(r.note || "") + (r.deleted ? '<br><span class="neg">محذوفة: ' + esc(r.deleteReason || "") + "</span>" : "") + "</td><td>" +
                (r.deleted ? "" : (locked ? '<span class="badge">مقفل</span>' : (can("editOp") ? '<button class="btn sm" data-edit="' + esc(r.id) + '">تعديل</button><button class="btn sm" data-del="' + esc(r.id) + '">حذف</button>' : ""))) +
                '<button class="btn sm ghost" data-hist="' + esc(r.id) + '" title="سجل التدقيق">⋯</button></td></tr>';
        }).join("") : '<tr><td colspan="13" class="empty">لا توجد عمليات مطابقة</td></tr>') + "</tbody></table></div>" +
        (list.length > limit ? '<div class="row" style="margin-top:10px"><button class="btn" data-act="more">عرض المزيد (' + (list.length - limit) + ")</button></div>" : "");

    const bindF = function (id, key, ev) {
        $(id).addEventListener(ev || "change", function (e) { f[key] = e.target.type === "checkbox" ? e.target.checked : e.target.value; f.limit = 300; renderLog(main); });
    };
    bindF("#lfFrom", "from"); bindF("#lfTo", "to"); bindF("#lfCur", "cur"); bindF("#lfPair", "pair"); bindF("#lfRes", "result"); bindF("#lfDel", "deleted");
    $("#lfNo").addEventListener("change", function (e) { f.no = e.target.value.trim(); renderLog(main); });
    main.onclick = function (e) {
        const ed = e.target.closest("[data-edit]");
        if (ed) { startEdit(ed.dataset.edit); return; }
        const del = e.target.closest("[data-del]");
        if (del) { deleteRecordFlow(del.dataset.del); return; }
        const h = e.target.closest("[data-hist]");
        if (h) { showAuditFor(h.dataset.hist); return; }
        const b = e.target.closest("[data-act]");
        if (!b) return;
        if (b.dataset.act === "clear") { ui.logFilter = {}; renderLog(main); }
        if (b.dataset.act === "more") { f.limit = limit + 300; renderLog(main); }
        if (b.dataset.act === "xlsx" || b.dataset.act === "print") {
            const rep = reportOps(list.filter(function (r) { return !r.deleted; }), "سجل العمليات");
            if (b.dataset.act === "xlsx") exportReportXlsx(rep); else printReport(rep);
        }
    };
}
function startEdit(id) {
    const r = records.get(id);
    if (!r || r.deleted) return;
    if (isClosed(r.day)) { toast("يوم هذه العملية مقفل. أعد فتح اليوم أولاً.", "bad"); return; }
    ui.ex = {
        id: r.id, editId: r.id, inCur: r.inCur, outCur: r.outCur, inAmt: r.inAmt, rate: r.rate, outAmt: r.outAmt, basis: "rate",
        costMethod: r.costMethod || "avg", manualRate: r.manualRate || "", note: r.note || "", saving: false
    };
    go("exchange");
}
function showAuditFor(id) {
    const list = byType("audit").filter(function (a) { return a.refId === id; }).sort(function (a, b) { return a.at < b.at ? -1 : 1; });
    const r = records.get(id);
    const names = { edit: "تعديل", delete: "حذف", reopen: "إعادة فتح", settle: "تسوية" };
    const row = function (json) {
        try {
            const o = JSON.parse(json);
            if (o.type !== "exchange") return esc(json.slice(0, 200));
            return esc(o.inAmt + " " + o.inCur + " ← " + o.outAmt + " " + o.outCur + " @ " + o.rate + (o.note ? " — " + o.note : ""));
        } catch (e) { return ""; }
    };
    modal("<h3>سجل التدقيق " + esc(r ? r.no || "" : "") + "</h3>" +
        (r ? '<p class="small muted">أُنشئت: ' + esc(fmtDateTime(r.createdAt)) + " بواسطة " + esc(r.createdBy || "") + (r.device ? " (جهاز " + esc(r.device) + ")" : "") + "</p>" : "") +
        (list.length ? '<div class="table-wrap"><table><thead><tr><th>الوقت</th><th>الإجراء</th><th>بواسطة</th><th>قبل</th><th>السبب</th></tr></thead><tbody>' +
            list.map(function (a) { return "<tr><td>" + esc(fmtDateTime(a.at)) + "</td><td>" + esc(names[a.action] || a.action) + "</td><td>" + esc(a.createdBy || "") + '</td><td class="wrap small ltr">' + row(a.before) + '</td><td class="wrap">' + esc(a.reason) + "</td></tr>"; }).join("") +
            "</tbody></table></div>" : '<p class="empty">لا توجد تعديلات على هذه العملية.</p>') +
        '<div class="actions"><button class="btn" data-modal="cancel">إغلاق</button></div>', { wide: true });
}

/* =========================================================
   الأرباح والخسائر
   ========================================================= */
function periodRange(period) {
    const today = openDay();
    if (period === "today") return { from: today, to: today, label: "اليوم " + fmtDay(today) };
    if (period === "week") return { from: E.addDays(today, -6), to: today, label: "آخر 7 أيام" };
    if (period === "month") return { from: today.slice(0, 7) + "-01", to: today.slice(0, 7) + "-31", label: monthName(today.slice(0, 7)) };
    if (period === "year") return { from: today.slice(0, 4) + "-01-01", to: today.slice(0, 4) + "-12-31", label: "سنة " + today.slice(0, 4) };
    return { from: "0000-00-00", to: "9999-12-31", label: "الأرباح التراكمية (كل الفترات)" };
}
function renderPnl(main) {
    const per = ui.pnlPeriod;
    const rg = periodRange(per);
    const t = sumDays(function (k) { return k >= rg.from && k <= rg.to; });
    const ops = byType("exchange").filter(function (r) { return r.day >= rg.from && r.day <= rg.to; });
    const byPair = {};
    let margin = E.ZERO;
    ops.forEach(function (r) {
        const x = ST.results[r.id]; if (!x) return;
        const k = E.pairOf(S, r.inCur, r.outCur).key;
        const b = byPair[k] || (byPair[k] = { n: 0, profit: E.ZERO, loss: E.ZERO, net: E.ZERO });
        b.n++; b.net = b.net.plus(x.profit);
        if (x.profit.gt(0)) b.profit = b.profit.plus(x.profit); else b.loss = b.loss.plus(x.profit.neg());
        if (x.margin) margin = margin.plus(x.margin);
    });
    const overall = t.net.plus(t.valuation).plus(t.inventory);
    const unreal = E.unrealized(ST, S);
    const periods = { today: "اليوم", week: "الأسبوع", month: "الشهر", year: "السنة", all: "تراكمي" };
    main.innerHTML = pageHead("الأرباح والخسائر", "جميع الأرباح محسوبة بالدولار (عملة القياس) ويظهر مقابلها بالليرة السورية الجديدة.") +
        '<div class="seg" style="margin-bottom:14px">' + Object.keys(periods).map(function (k) { return '<button data-per="' + k + '" class="' + (per === k ? "on" : "") + '">' + periods[k] + "</button>"; }).join("") + "</div>" +
        "<h3>" + esc(rg.label) + "</h3>" +
        '<div class="grid g4" style="margin-bottom:16px">' +
        '<div class="kpi profit"><div class="label">أرباح العمليات الموجبة</div><div class="value pos">' + money(t.profit, undefined, { noColor: true }) + "</div></div>" +
        '<div class="kpi loss"><div class="label">خسائر العمليات السالبة</div><div class="value neg">' + money(t.loss, undefined, { noColor: true }) + "</div></div>" +
        '<div class="kpi ' + (t.net.lt(0) ? "loss" : "profit") + '"><div class="label">صافي ربح الصرافة</div><div class="value">' + money(t.net) + '</div><div class="sub">' + t.count + " عملية</div></div>" +
        '<div class="kpi info"><div class="label">هامش السعر مقابل المرجع</div><div class="value">' + money(margin) + '</div><div class="sub">للمقارنة مع الربح المحاسبي</div></div></div>' +
        '<div class="card"><h3>النتيجة المفصلة (منفصلة عن مؤشر أرباح الصرافة)</h3><div class="table-wrap"><table><tbody>' +
        "<tr><td>ربح عمليات الصرافة (المحقق)</td><td class=\"num\">" + money(t.net) + "</td></tr>" +
        "<tr><td>تسويات التقييم (فرق التكلفة اليدوية)</td><td class=\"num\">" + money(t.valuation) + "</td></tr>" +
        "<tr><td>فروقات الجرد المعتمدة</td><td class=\"num\">" + money(t.inventory) + "</td></tr>" +
        '<tr class="total"><td>النتيجة الإجمالية بعد التسويات</td><td class="num">' + money(overall) + "</td></tr>" +
        "<tr><td>سحوبات الأرباح (ليست خسارة)</td><td class=\"num\">" + fmtUSD(t.withdrawals) + "</td></tr>" +
        "<tr><td>إضافات رأس المال / سحوبات الصندوق (ليست ربحاً)</td><td class=\"num\">" + fmtUSD(t.capitalIn) + " / " + fmtUSD(t.capitalOut) + "</td></tr>" +
        "<tr><td>أرباح غير محققة حالياً (إعادة تقييم الأرصدة)</td><td class=\"num\">" + money(unreal.total) + "</td></tr>" +
        "</tbody></table></div>" +
        '<p class="muted small">سياسة الاعتراف بالأرباح الحالية: ' + (S.valuation === "cost" ? "«عند العودة إلى الدولار» — تبديل عملتين غير الدولار لا يحقق ربحاً حتى تُستبدل العملة بدولار." : "«التقييم المرجعي» — العملة المستلمة تُقيَّم بالسعر المرجعي وقت العملية، والربح = قيمتها − التكلفة الدفترية للعملة المسلّمة.") + " يختلف هامش السعر عن الربح المحاسبي حين تختلف تكلفة شراء العملة المسلّمة عن سعرها المرجعي الحالي.</p></div>" +
        '<div class="card"><h3>الأرباح حسب زوج العملات</h3><div class="table-wrap"><table><thead><tr><th>الزوج</th><th>العمليات</th><th>الأرباح</th><th>الخسائر</th><th>الصافي</th></tr></thead><tbody>' +
        (Object.keys(byPair).length ? Object.keys(byPair).sort().map(function (k) {
            const b = byPair[k];
            return '<tr><td class="ltr">' + esc(k) + "</td><td>" + b.n + '</td><td class="num pos">' + fmt(b.profit, 2) + '</td><td class="num neg">' + fmt(b.loss, 2) + '</td><td class="num">' + money(b.net) + "</td></tr>";
        }).join("") : '<tr><td colspan="5" class="empty">لا توجد عمليات في هذه الفترة</td></tr>') + "</tbody></table></div></div>" +
        '<div class="card"><h3>الأرباح غير المحققة حسب العملة</h3><div class="table-wrap"><table><thead><tr><th>العملة</th><th>الرصيد</th><th>القيمة الدفترية $</th><th>القيمة بالسعر المرجعي $</th><th>الفرق</th></tr></thead><tbody>' +
        E.enabledCurrencies(S).map(function (c) {
            const st = ST.curs[c.code], u = unreal.byCur[c.code];
            const ref = c.code === S.base ? E.ONE : ST.refs[c.code];
            return "<tr><td>" + esc(c.name) + '</td><td class="num">' + fmtCur(st.qty, c.code) + '</td><td class="num">' + fmt(st.book, 2) + '</td><td class="num">' + (ref ? fmt(st.qty.div(ref), 2) : "—") + '</td><td class="num ' + (u ? cls(u) : "") + '">' + (u ? fmt(u, 2) : "—") + "</td></tr>";
        }).join("") + "</tbody></table></div></div>";
    main.onclick = function (e) { const b = e.target.closest("[data-per]"); if (b) { ui.pnlPeriod = b.dataset.per; renderPnl(main); } };
}

/* =========================================================
   إقفال اليوم وترحيل الأرباح
   ========================================================= */
function snapshotTable(snap) {
    const rate = E.parse(snap.displayRate);
    return '<div class="kv">' +
        "<div>عدد العمليات</div><div>" + snap.count + "</div>" +
        "<div>إجمالي الأرباح</div><div>" + money(snap.profit, rate, { noColor: true }) + "</div>" +
        "<div>إجمالي الخسائر</div><div>" + money(snap.loss, rate, { noColor: true }) + "</div>" +
        "<div>صافي الربح</div><div>" + money(snap.net, rate) + "</div>" +
        "<div>تسويات التقييم</div><div>" + money(snap.valuation, rate) + "</div>" +
        "<div>فروقات الجرد</div><div>" + money(snap.inventory, rate) + "</div>" +
        "<div>سحوبات الأرباح</div><div>" + fmtUSD(snap.withdrawals) + "</div>" +
        "<div>سعر تحويل الدولار وقت الإقفال</div><div class=\"num\">" + (rate ? fmtTrim(rate, 4) + " " + esc(sym(S.display)) : "—") + "</div></div>" +
        '<div class="table-wrap" style="margin-top:10px"><table><thead><tr><th>العملة</th><th>الرصيد</th><th>متوسط التكلفة</th><th>القيمة الدفترية $</th></tr></thead><tbody>' +
        Object.keys(snap.balances || {}).filter(function (k) { return E.curInfo(S, k); }).map(function (k) {
            const b = snap.balances[k];
            return "<tr><td>" + esc(curName(k)) + '</td><td class="num">' + fmtCur(b.qty, k) + "</td><td>" + (b.avg ? esc(fmtCostRate(b.avg, k)) : "—") + '</td><td class="num">' + fmt(b.book, 2) + "</td></tr>";
        }).join("") + "</tbody></table></div>";
}
function renderClose(main) {
    const today = openDay();
    const pending = unclosedDays(today);
    const stale = staleCloses();
    const reopened = byType("close").filter(function (c) { return c.status === "reopened"; }).sort(function (a, b) { return a.day < b.day ? -1 : 1; });
    const snap = E.closeSnapshot(ST, today, S);
    const allowed = can("close");
    main.innerHTML = pageHead("إقفال اليوم وترحيل الأرباح", "الإقفال لا يصفّر أرصدة الصندوق: تنتقل الأرصدة والتكاليف إلى اليوم التالي كما هي.") +
        globalAlerts() +
        '<div class="grid g2"><div class="card"><h3>اليوم المحاسبي الحالي: ' + fmtDay(today) + "</h3>" + snapshotTable(snap) +
        '<div class="row" style="margin-top:14px"><button class="btn primary big" data-act="close-today"' + (allowed ? "" : " disabled") + ">إقفال اليوم وترحيل الأرباح</button></div>" +
        (pending.filter(function (d) { return d < today; }).length ? '<p class="muted small">سيتم إقفال الأيام السابقة غير المقفلة أولاً بالترتيب: ' + pending.filter(function (d) { return d < today; }).map(fmtDay).join("، ") + "</p>" : "") +
        "</div>" +
        '<div class="card"><h3>إعدادات الإقفال</h3><div class="kv"><div>الوضع</div><div>' + (S.closeMode === "auto" ? "تلقائي" : "يدوي") + "</div><div>بداية اليوم المحاسبي</div><div>الساعة " + String(S.dayStartHour).padStart(2, "0") + ":00</div><div>المنطقة الزمنية</div><div class=\"ltr\">" + esc(S.timeZone) + "</div></div>" +
        '<p class="muted small">الإقفال التلقائي يعمل والمتصفح مفتوح عند حلول الوقت، وعند فتح النظام من جديد يُقفل الأيام الماضية غير المقفلة. لا يُعد مضموناً والمتصفح مغلق.</p>' +
        '<button class="btn" data-go="settings">تغيير الإعدادات</button></div></div>' +
        (stale.length ? '<div class="card"><h3>أيام تحتاج إعادة اعتماد</h3><p class="muted small">تغيّرت نتائج هذه الأيام بعد إقفالها بسبب تعديلات في أيام سابقة. تُعتمد بالترتيب الزمني.</p><div class="row">' + stale.map(function (d) { return '<span class="badge warn">' + fmtDay(d) + "</span>"; }).join("") +
            '<button class="btn primary" data-act="reapprove"' + (allowed ? "" : " disabled") + ">إعادة اعتماد بالترتيب</button></div></div>" : "") +
        (reopened.length ? '<div class="card"><h3>أيام معاد فتحها</h3><div class="table-wrap"><table><thead><tr><th>اليوم</th><th>سبب إعادة الفتح</th><th>بواسطة</th><th>النتيجة السابقة</th><th></th></tr></thead><tbody>' +
            reopened.map(function (c) {
                return "<tr><td>" + fmtDay(c.day) + '</td><td class="wrap">' + esc(c.reopenReason || "") + "</td><td>" + esc(c.reopenedBy || "") + "</td><td>" + money(c.snapshot.net, E.parse(c.snapshot.displayRate)) + '</td><td><button class="btn sm primary" data-reclose="' + c.day + '"' + (allowed ? "" : " disabled") + ">إعادة الإقفال</button></td></tr>";
            }).join("") + "</tbody></table></div></div>" : "") +
        '<div class="card"><h3>إعادة فتح يوم مقفل</h3><div class="row"><select id="reDay" style="width:auto">' +
        byType("close").filter(function (c) { return c.status === "closed"; }).sort(function (a, b) { return a.day < b.day ? 1 : -1; }).map(function (c) { return '<option value="' + c.day + '">' + fmtDay(c.day) + "</option>"; }).join("") +
        '</select><button class="btn" data-act="reopen"' + (allowed ? "" : " disabled") + ">إعادة فتح اليوم</button></div>" +
        '<p class="muted small">بعد إعادة الفتح يُسمح بتعديل عمليات اليوم، وتبقى نتيجة الإقفال السابقة محفوظة، ولا تُعرض النتائج المعدلة كمعتمدة حتى إعادة الإقفال.</p></div>';

    main.onclick = async function (e) {
        const g = e.target.closest("[data-go]");
        if (g) { go(g.dataset.go); return; }
        const rc = e.target.closest("[data-reclose]");
        if (rc) {
            const d = rc.dataset.reclose;
            const earlier = unclosedDays(d).filter(function (x) { return x < d; });
            if (earlier.length) { toast("أقفل الأيام السابقة أولاً: " + earlier.map(fmtDay).join("، "), "bad"); return; }
            if (!(await confirmBox("إعادة إقفال " + fmtDay(d), snapshotTable(E.closeSnapshot(ST, d, S)), "إقفال"))) return;
            try { await commit([buildClose(d, false, "إعادة إقفال بعد التعديل")], { validate: false }); toast("أُعيد إقفال " + fmtDay(d), "ok"); render(); }
            catch (er) { toast(failMsg(er), "bad"); }
            return;
        }
        const b = e.target.closest("[data-act]");
        if (!b) return;
        if (b.dataset.act === "close-today") {
            const list = unclosedDays(today); if (list.indexOf(today) < 0) list.push(today);
            if (!(await confirmBox("إقفال اليوم " + fmtDay(today), (list.length > 1 ? "<p>سيتم إقفال: " + list.map(fmtDay).join("، ") + "</p>" : "") + snapshotTable(snap) + '<p class="muted small">بعد الإقفال تُسجل العمليات الجديدة في اليوم التالي، ولا يمكن تعديل عمليات هذا اليوم إلا بإعادة فتحه.</p>', "إقفال وترحيل"))) return;
            b.disabled = true;
            try { const n = await closeUpTo(today, false); toast("تم إقفال " + n + " يوم وترحيل الأرباح. اليوم المحاسبي المفتوح الآن: " + fmtDay(openDay()), "ok", 7000); render(); }
            catch (er) { toast(failMsg(er), "bad"); b.disabled = false; }
        }
        if (b.dataset.act === "reapprove") {
            const list = staleCloses();
            if (!(await confirmBox("إعادة اعتماد", "ستُعاد اعتماد نتائج: " + list.map(fmtDay).join("، ") + " بالترتيب الزمني، مع الاحتفاظ بالنتائج السابقة في سجل الإقفال.", "اعتماد"))) return;
            try { await commit(list.map(function (d) { return buildClose(d, false, "إعادة احتساب بعد تعديل يوم سابق"); }), { validate: false }); toast("تم إعادة الاعتماد", "ok"); render(); }
            catch (er) { toast(failMsg(er), "bad"); }
        }
        if (b.dataset.act === "reopen") {
            const d = $("#reDay").value;
            if (!d) { toast("لا توجد أيام مقفلة", "info"); return; }
            const reason = await promptBox("إعادة فتح اليوم " + fmtDay(d), "ستبقى نتيجة الإقفال الحالية محفوظة للرجوع إليها.", "سبب إعادة الفتح", { okText: "إعادة الفتح" });
            if (!reason) return;
            const c = clone(closeOf(d));
            const before = clone(c);
            Object.assign(c, { status: "reopened", reopenedAt: nowISO(), reopenedBy: me.username, reopenReason: reason });
            try { await commit([c, audit("reopen", c, before, reason)], { validate: false }); toast("أُعيد فتح " + fmtDay(d), "ok"); render(); }
            catch (er) { toast(failMsg(er), "bad"); }
        }
    };
}

/* =========================================================
   سجل الأرباح المرحّلة (الأيام المقفلة)
   ========================================================= */
function closedRows(filter) {
    filter = filter || {};
    return byType("close").filter(function (c) {
        return (!filter.from || c.day >= filter.from) && (!filter.to || c.day <= filter.to);
    }).sort(function (a, b) { return a.day < b.day ? -1 : 1; });
}
function renderRegister(main) {
    const f = ui.registerFilter;
    const rows = closedRows(f);
    const stale = staleCloses();
    let cum = E.ZERO, cumD = E.ZERO;
    const months = {}, years = {};
    const body = rows.map(function (c) {
        const s = c.snapshot, rate = E.parse(s.displayRate), net = E.dec(s.net);
        cum = cum.plus(net); cumD = cumD.plus(rate ? net.times(rate) : E.ZERO);
        const m = c.day.slice(0, 7), y = c.day.slice(0, 4);
        [[months, m], [years, y]].forEach(function (pair) {
            const o = pair[0][pair[1]] || (pair[0][pair[1]] = { count: 0, profit: E.ZERO, loss: E.ZERO, net: E.ZERO, disp: E.ZERO });
            o.count += Number(s.count); o.profit = o.profit.plus(s.profit); o.loss = o.loss.plus(s.loss); o.net = o.net.plus(net); o.disp = o.disp.plus(rate ? net.times(rate) : E.ZERO);
        });
        const status = c.status === "reopened" ? '<span class="badge warn">معاد فتحه</span>' : (stale.indexOf(c.day) >= 0 ? '<span class="badge warn">يحتاج إعادة اعتماد</span>' : '<span class="badge ok">معتمد</span>');
        return "<tr><td>" + fmtDay(c.day) + "</td><td>" + s.count + '</td><td class="num pos">' + fmt(s.profit, 2) + '</td><td class="num neg">' + fmt(s.loss, 2) + '</td><td class="num ' + cls(net) + '">' + fmt(net, 2) +
            '</td><td class="num ' + cls(net) + '">' + (rate ? fmt(net.times(rate), E.decimalsOf(S, S.display)) : "—") + '</td><td class="num">' + fmt(cum, 2) + "</td><td>" + status + (c.auto ? ' <span class="badge">تلقائي</span>' : "") +
            '</td><td><button class="btn sm" data-detail="' + c.day + '">تفاصيل</button><button class="btn sm" data-print="' + c.day + '">طباعة</button></td></tr>';
    }).join("");
    const aggTable = function (title, obj, labelFn) {
        const keys = Object.keys(obj).sort();
        return '<div class="card"><h3>' + title + '</h3><div class="table-wrap"><table><thead><tr><th>الفترة</th><th>العمليات</th><th>الأرباح $</th><th>الخسائر $</th><th>الصافي $</th><th>الصافي ' + esc(sym(S.display)) + "</th></tr></thead><tbody>" +
            (keys.length ? keys.map(function (k) { const o = obj[k]; return "<tr><td>" + esc(labelFn(k)) + "</td><td>" + o.count + '</td><td class="num pos">' + fmt(o.profit, 2) + '</td><td class="num neg">' + fmt(o.loss, 2) + '</td><td class="num ' + cls(o.net) + '">' + fmt(o.net, 2) + '</td><td class="num ' + cls(o.net) + '">' + fmt(o.disp, E.decimalsOf(S, S.display)) + "</td></tr>"; }).join("") : '<tr><td colspan="6" class="empty">لا يوجد</td></tr>') +
            "</tbody></table></div></div>";
    };
    main.innerHTML = pageHead("سجل الأرباح اليومية", "نتائج الأيام المقفلة محفوظة بصورة مستقلة كما اعتُمدت وقت الإقفال.") +
        '<div class="card"><div class="row"><label class="field"><span>من</span><input type="date" id="rgFrom" value="' + esc(f.from || "") + '"></label><label class="field"><span>إلى</span><input type="date" id="rgTo" value="' + esc(f.to || "") + '"></label>' +
        '<span class="spacer"></span><button class="btn" data-act="xlsx">تصدير Excel</button><button class="btn" data-act="print">طباعة</button></div></div>' +
        '<div class="kpi" style="margin-bottom:16px"><div class="label">الأرباح التراكمية المرحّلة</div><div class="value">' + money(cum, null) + ' <span class="muted small">≈ ' + fmtCur(cumD, S.display) + " (بأسعار أيام الإقفال)</span></div></div>" +
        '<div class="table-wrap" style="margin-bottom:16px"><table><thead><tr><th>التاريخ</th><th>العمليات</th><th>الأرباح $</th><th>الخسائر $</th><th>الصافي $</th><th>الصافي ' + esc(sym(S.display)) + "</th><th>التراكمي $</th><th>الحالة</th><th></th></tr></thead><tbody>" +
        (body || '<tr><td colspan="9" class="empty">لا توجد أيام مقفلة بعد</td></tr>') + "</tbody></table></div>" +
        '<div class="grid g2">' + aggTable("الإجمالي الشهري", months, monthName) + aggTable("الإجمالي السنوي", years, function (y) { return "سنة " + y; }) + "</div>";
    $("#rgFrom").addEventListener("change", function (e) { f.from = e.target.value; renderRegister(main); });
    $("#rgTo").addEventListener("change", function (e) { f.to = e.target.value; renderRegister(main); });
    main.onclick = function (e) {
        const d = e.target.closest("[data-detail]");
        if (d) { showCloseDetail(d.dataset.detail); return; }
        const p = e.target.closest("[data-print]");
        if (p) { printReport(reportDayClose(p.dataset.print)); return; }
        const b = e.target.closest("[data-act]");
        if (!b) return;
        const rep = buildReport("carried", f.from || "", f.to || "");
        if (b.dataset.act === "xlsx") exportReportXlsx(rep); else printReport(rep);
    };
}
function showCloseDetail(day) {
    const c = closeOf(day);
    if (!c) return;
    const ops = byType("exchange").filter(function (r) { return r.day === day; }).sort(E.sortLedger);
    modal("<h3>تقرير إقفال " + fmtDay(day) + "</h3>" +
        '<p class="small muted">أُقفل: ' + esc(fmtDateTime(c.closedAt)) + " بواسطة " + esc(c.closedBy || "") + (c.auto ? " (تلقائي)" : "") + (c.status === "reopened" ? ' — <span class="warn-c">معاد فتحه: ' + esc(c.reopenReason || "") + "</span>" : "") + "</p>" +
        snapshotTable(c.snapshot) +
        (c.history && c.history.length ? "<details style=\"margin-top:12px\"><summary>نتائج إقفال سابقة (" + c.history.length + ")</summary>" + c.history.map(function (h) {
            return '<div class="card" style="margin-top:8px"><div class="small muted">أُقفل ' + esc(fmtDateTime(h.closedAt)) + (h.reopenReason ? " — أعيد فتحه: " + esc(h.reopenReason) : "") + (h.note ? " — " + esc(h.note) : "") + "</div>" + money(h.snapshot.net, E.parse(h.snapshot.displayRate)) + " — " + h.snapshot.count + " عملية</div>";
        }).join("") + "</details>" : "") +
        "<h4 style=\"margin-top:14px\">عمليات اليوم (" + ops.length + ")</h4>" +
        '<div class="table-wrap"><table><thead><tr><th>رقم</th><th>الوقت</th><th>استلام</th><th>تسليم</th><th>السعر</th><th>الربح $</th></tr></thead><tbody>' +
        ops.map(function (r) { const x = ST.results[r.id]; return '<tr><td class="ltr">' + esc(r.no) + '</td><td class="num">' + esc(fmtTime(r.at)) + "</td><td>" + fmtCur(r.inAmt, r.inCur) + "</td><td>" + fmtCur(r.outAmt, r.outCur) + '</td><td class="num">' + fmtTrim(r.rate, 6) + '</td><td class="num ' + (x ? cls(x.profit) : "") + '">' + (x ? fmt(x.profit, 2) : "") + "</td></tr>"; }).join("") +
        "</tbody></table></div>" +
        '<div class="actions"><button class="btn primary" data-modal="print">طباعة</button><button class="btn" data-modal="xlsx">Excel</button><button class="btn" data-modal="cancel">إغلاق</button></div>', { wide: true })
        .then(function (v) {
            if (v === "print") printReport(reportDayClose(day));
            if (v === "xlsx") exportReportXlsx(reportDayClose(day));
        });
}

/* =========================================================
   سحب الأرباح
   ========================================================= */
function renderWithdraw(main) {
    const allowed = can("withdraw");
    const all = sumDays(function () { return true; });
    const realized = all.net.plus(all.valuation).plus(all.inventory);
    const available = realized.minus(all.withdrawals);
    const list = byType("withdrawal", true).sort(E.sortLedger).reverse();
    const firstCur = ui.wdCur || S.display;
    main.innerHTML = pageHead("سحب الأرباح", "السحب يخفض رصيد الصندوق ولا يُعد خسارة تشغيلية ولا يغيّر أرباح الأيام السابقة.") +
        '<div class="grid g3" style="margin-bottom:16px">' +
        '<div class="kpi profit"><div class="label">الأرباح المحققة (بعد التسويات)</div><div class="value">' + money(realized) + "</div></div>" +
        '<div class="kpi info"><div class="label">إجمالي المسحوب</div><div class="value">' + money(all.withdrawals, undefined, { noColor: true }) + "</div></div>" +
        '<div class="kpi ' + (available.lt(0) ? "loss" : "profit") + '"><div class="label">الأرباح المتبقية القابلة للسحب</div><div class="value">' + money(available) + "</div></div></div>" +
        '<div class="alert info">الرصيد النقدي في الصندوق ليس كله أرباحاً: يتضمن رأس المال. اسحب في حدود الأرباح المتبقية.</div>' +
        '<div class="card"><h3>سحب جديد</h3><div class="grid g3">' +
        '<label class="field"><span>التاريخ</span><input value="' + fmtDay(openDay()) + '" readonly></label>' +
        '<label class="field"><span>العملة</span><select id="wdCur">' + curOptions(firstCur) + "</select></label>" +
        '<label class="field"><span>المبلغ</span><input id="wdAmt" class="num-in" inputmode="decimal"></label>' +
        '<label class="field"><span>سعر التحويل إلى الدولار <span id="wdHint" class="muted"></span></span><input id="wdRate" class="num-in" inputmode="decimal"></label>' +
        '<label class="field"><span>المقابل بالدولار</span><input id="wdUsd" readonly class="num-in"></label>' +
        '<label class="field"><span>ملاحظات</span><input id="wdNote" maxlength="200"></label></div>' +
        '<p class="error-text" id="wdErr"></p><button class="btn primary big" data-act="wd-save"' + (allowed ? "" : " disabled") + ">تسجيل السحب</button></div>" +
        '<div class="card"><h3>سجل سحوبات الأرباح</h3><div class="table-wrap"><table><thead><tr><th>رقم</th><th>التاريخ</th><th>العملة</th><th>المبلغ</th><th>السعر</th><th>بالدولار</th><th>ملاحظات</th><th>بواسطة</th><th></th></tr></thead><tbody>' +
        (list.length ? list.map(function (r) {
            return '<tr class="' + (r.deleted ? "deleted" : "") + '"><td class="ltr">' + esc(r.no || "") + "</td><td>" + fmtDay(r.day) + "</td><td>" + esc(r.cur) + '</td><td class="num">' + fmt(r.amount, E.decimalsOf(S, r.cur)) + '</td><td class="num">' + fmtTrim(r.rate || 1, 6) + '</td><td class="num">' + fmt(r.usdValue, 2) +
                '</td><td class="wrap">' + esc(r.note || "") + "</td><td>" + esc(r.createdBy || "") + "</td><td>" + (!r.deleted && allowed && !isClosed(r.day) ? '<button class="btn sm" data-del="' + esc(r.id) + '">حذف</button>' : "") + "</td></tr>";
        }).join("") : '<tr><td colspan="9" class="empty">لا توجد سحوبات</td></tr>') + "</tbody></table></div></div>";

    function setRate() {
        const code = $("#wdCur").value;
        ui.wdCur = code;
        const ref = ST.refs[code];
        const p = E.pairOf(S, S.base, code);
        $("#wdHint").textContent = code === S.base ? "" : "(" + (p.base === S.base ? sym(code) + " لكل 1$" : "$ لكل 1 " + sym(code)) + ")";
        $("#wdRate").value = code === S.base ? "1" : (ref ? (p.base === S.base ? ref : E.ONE.div(ref)).toDecimalPlaces(6).toFixed() : "");
        $("#wdRate").readOnly = code === S.base;
        calc();
    }
    function usdOf() {
        const code = $("#wdCur").value;
        const a = E.parse($("#wdAmt").value), r = E.parse($("#wdRate").value);
        if (!a || !r || r.lte(0)) return null;
        if (code === S.base) return a;
        return E.pairOf(S, S.base, code).base === S.base ? a.div(r) : a.times(r);
    }
    function calc() { const u = usdOf(); $("#wdUsd").value = u ? fmt(u, 2) : ""; }
    $("#wdCur").addEventListener("change", setRate);
    $("#wdAmt").addEventListener("input", calc);
    $("#wdRate").addEventListener("input", calc);
    setRate();
    main.onclick = async function (e) {
        const del = e.target.closest("[data-del]");
        if (del) { deleteRecordFlow(del.dataset.del); return; }
        const b = e.target.closest("[data-act]");
        if (!b || b.dataset.act !== "wd-save") return;
        const err = $("#wdErr"); err.textContent = "";
        const code = $("#wdCur").value, a = E.parse($("#wdAmt").value), usd = usdOf();
        if (!a || a.lte(0)) { err.textContent = "أدخل مبلغاً أكبر من صفر."; return; }
        if (!E.roundAmount(S, code, a).eq(a)) { err.textContent = "عدد المنازل العشرية أكبر من المسموح."; return; }
        if (!usd) { err.textContent = "أدخل سعر تحويل صحيحاً."; return; }
        if (a.gt(ST.curs[code].qty)) { err.textContent = "المبلغ أكبر من رصيد " + curName(code) + "."; return; }
        let msg = "سحب " + fmtCur(a, code) + " (≈ " + fmtUSD(usd) + ") من الأرباح.";
        if (usd.gt(available)) msg += '<div class="alert warn" style="margin-top:8px">المبلغ أكبر من الأرباح المتبقية (' + fmtUSD(available) + "). الفرق سيُسحب من رأس المال.</div>";
        if (!(await confirmBox("تأكيد سحب الأرباح", msg, "تسجيل السحب"))) return;
        const r = $("#wdRate").value;
        const rec = {
            id: uid("w"), type: "withdrawal", no: nextNo("W", "withdrawal"), day: openDay(), at: nowISO(), cur: code, amount: a.toFixed(),
            rate: code === S.base ? "1" : (E.pairOf(S, S.base, code).base === S.base ? E.dec(r).toFixed() : E.ONE.div(E.dec(r)).toFixed()),
            usdValue: usd.toFixed(), note: $("#wdNote").value.trim()
        };
        try { await commit([rec]); toast("تم تسجيل سحب الأرباح", "ok"); render(); }
        catch (er) { err.textContent = failMsg(er); }
    };
}

/* =========================================================
   جرد ومطابقة الصندوق (اختياري)
   ========================================================= */
function renderCount(main) {
    if (!ui.countDraft) ui.countDraft = {};
    const dr = ui.countDraft;
    const today = openDay();
    const a = ST.days[today] || E.emptyDay(today);
    const list = byType("count").sort(E.sortLedger).reverse();
    main.innerHTML = pageHead("جرد ومطابقة الصندوق", "اختياري — فتح هذه الصفحة لا يعدّل الأرصدة. اترك خانة «الفعلي» فارغة لاستثناء عملة (جرد عملة واحدة ممكن).") +
        '<div class="card"><div class="table-wrap"><table><thead><tr><th>العملة</th><th>الرصيد المسجل</th><th>الرصيد الفعلي</th><th>الفرق</th><th>الحالة</th><th>الفرق بالدولار (مرجعي)</th></tr></thead><tbody>' +
        E.enabledCurrencies(S).map(function (c) {
            return "<tr><td><b>" + esc(c.name) + '</b></td><td class="num">' + fmtCur(ST.curs[c.code].qty, c.code) + '</td><td><input class="num-in" inputmode="decimal" data-actual="' + c.code + '" value="' + esc(dr[c.code] || "") + '" style="min-width:140px"></td>' +
                '<td class="num" data-diff="' + c.code + '"></td><td data-st="' + c.code + '"></td><td class="num" data-usd="' + c.code + '"></td></tr>';
        }).join("") + "</tbody></table></div>" +
        '<div id="countSum" style="margin-top:12px"></div>' +
        '<label class="field" style="margin-top:10px"><span>سبب التسوية / ملاحظات</span><input id="countReason" maxlength="300"></label>' +
        '<p class="error-text" id="countErr"></p>' +
        '<div class="row"><button class="btn primary big" data-act="count-approve"' + (can("countSave") ? "" : " disabled") + '>اعتماد الجرد</button><button class="btn" data-act="count-clear">مسح الإدخال</button></div></div>' +
        '<div class="card"><h3>نتيجة اليوم بعد الجرد</h3><div class="kv"><div>ربح عمليات الصرافة</div><div>' + money(a.net) + "</div><div>فروقات الجرد المعتمدة</div><div>" + money(a.inventory) + "</div><div><b>النتيجة الإجمالية بعد تسوية الجرد</b></div><div><b>" + money(a.net.plus(a.inventory)) + "</b></div></div>" +
        '<p class="muted small">فروقات الجرد منفصلة عن مؤشر أرباح الصرافة.</p></div>' +
        '<div class="card"><h3>سجل الجرد</h3><div class="table-wrap"><table><thead><tr><th>رقم الجرد</th><th>التاريخ</th><th>الوقت</th><th>العملات</th><th>المسجل ← الفعلي (الفرق)</th><th>الحالة</th><th>تمت التسوية؟</th><th>السبب</th><th></th></tr></thead><tbody>' +
        (list.length ? list.map(function (k) {
            const linked = k.settledBy ? records.get(k.settledBy) : null;
            const st = k.settled ? '<span class="badge ok">مسوّى</span>' : (linked ? '<span class="badge info">سُوّي لاحقاً (' + esc(linked.no) + ")</span>" : '<span class="badge">محفوظ دون تسوية</span>');
            const anyDiff = (k.lines || []).some(function (l) { return !E.dec(l.diff).isZero(); });
            return '<tr><td class="ltr">' + esc(k.no) + "</td><td>" + fmtDay(k.day) + '</td><td class="num">' + esc(fmtTime(k.at)) + "</td><td>" + (k.lines || []).map(function (l) { return esc(l.cur); }).join("، ") + '</td><td class="wrap small">' +
                (k.lines || []).map(function (l) { return esc(l.cur) + ": " + fmt(l.recorded, E.decimalsOf(S, l.cur)) + " ← " + fmt(l.actual, E.decimalsOf(S, l.cur)) + ' (<span class="' + cls(l.diff) + '">' + fmt(l.diff, E.decimalsOf(S, l.cur)) + "</span>)"; }).join("<br>") +
                "</td><td>" + (anyDiff ? '<span class="badge warn">فيه فروقات</span>' : '<span class="badge ok">مطابق</span>') + "</td><td>" + st + '</td><td class="wrap">' + esc(k.reason || "") + "</td><td>" +
                (!k.settled && !k.settledBy && anyDiff && can("settle") ? '<button class="btn sm" data-settle="' + esc(k.id) + '">تسوية الآن</button>' : "") + "</td></tr>";
        }).join("") : '<tr><td colspan="9" class="empty">لا توجد عمليات جرد</td></tr>') + "</tbody></table></div></div>";

    function lines() {
        const out = [];
        E.enabledCurrencies(S).forEach(function (c) {
            const v = (dr[c.code] || "").trim();
            if (v === "") return;
            const act = E.parse(v);
            out.push({ cur: c.code, recorded: ST.curs[c.code].qty, actual: act, valid: act !== null && act.gte(0) && E.roundAmount(S, c.code, act).eq(act) });
        });
        return out;
    }
    function update() {
        let totalUsd = E.ZERO, missingRef = false;
        const groups = { match: [], short: [], over: [] };
        E.enabledCurrencies(S).forEach(function (c) {
            const l = lines().find(function (x) { return x.cur === c.code; });
            const dEl = $('[data-diff="' + c.code + '"]', main), sEl = $('[data-st="' + c.code + '"]', main), uEl = $('[data-usd="' + c.code + '"]', main);
            if (!l) { dEl.textContent = ""; sEl.innerHTML = '<span class="muted small">غير مشمولة</span>'; uEl.textContent = ""; return; }
            if (!l.valid) { dEl.textContent = "—"; sEl.innerHTML = '<span class="neg">قيمة غير صالحة</span>'; uEl.textContent = ""; return; }
            const diff = l.actual.minus(l.recorded);
            const ref = c.code === S.base ? E.ONE : ST.refs[c.code];
            dEl.innerHTML = '<span class="' + cls(diff) + '">' + (diff.gt(0) ? "+" : "") + fmt(diff, E.decimalsOf(S, c.code)) + "</span>";
            sEl.innerHTML = diff.isZero() ? '<span class="badge ok">مطابق</span>' : diff.lt(0) ? '<span class="badge bad">نقص</span>' : '<span class="badge info">زيادة</span>';
            (diff.isZero() ? groups.match : diff.lt(0) ? groups.short : groups.over).push(c.name);
            if (ref) { const u = diff.div(ref); totalUsd = totalUsd.plus(u); uEl.innerHTML = '<span class="' + cls(u) + '">' + fmt(u, 2) + "</span>"; }
            else { missingRef = true; uEl.textContent = "—"; }
        });
        $("#countSum").innerHTML = '<div class="kv"><div>العملات المتطابقة</div><div>' + (groups.match.join("، ") || "—") + "</div><div>عملات فيها نقص</div><div class=\"neg\">" + (groups.short.join("، ") || "—") +
            "</div><div>عملات فيها زيادة</div><div class=\"info-c\">" + (groups.over.join("، ") || "—") + "</div><div>إجمالي فروقات الجرد</div><div>" + money(totalUsd) + (missingRef ? ' <span class="warn-c small">(بعض العملات بلا سعر مرجعي)</span>' : "") + "</div></div>";
    }
    $$("[data-actual]", main).forEach(function (el) { el.addEventListener("input", function () { dr[el.dataset.actual] = el.value; update(); }); });
    update();

    main.onclick = async function (e) {
        const st = e.target.closest("[data-settle]");
        if (st) { settleLater(st.dataset.settle); return; }
        const b = e.target.closest("[data-act]");
        if (!b) return;
        if (b.dataset.act === "count-clear") { ui.countDraft = {}; render(); return; }
        if (b.dataset.act !== "count-approve") return;
        const err = $("#countErr"); err.textContent = "";
        const ls = lines();
        if (!ls.length) { err.textContent = "أدخل الرصيد الفعلي لعملة واحدة على الأقل."; return; }
        if (ls.some(function (l) { return !l.valid; })) { err.textContent = "يوجد رصيد فعلي غير صالح."; return; }
        const anyDiff = ls.some(function (l) { return !l.actual.eq(l.recorded); });
        const reason = $("#countReason").value.trim();
        const choice = await modal("<h3>اعتماد الجرد</h3><p>اختر طريقة الاعتماد:</p>" +
            '<div class="stack"><button class="btn big" data-modal="save" style="width:100%">حفظ الجرد دون تعديل الأرصدة</button>' +
            (anyDiff && can("settle") ? '<button class="btn primary big" data-modal="settle" style="width:100%">تسوية الأرصدة لتطابق المبالغ الفعلية</button>' : "") + "</div>" +
            (anyDiff && !can("settle") ? '<p class="muted small">التسوية للمدير فقط.</p>' : "") +
            '<div class="actions"><button class="btn" data-modal="cancel">إلغاء</button></div>');
        if (!choice) return;
        if (choice === "settle" && !reason) { err.textContent = "اكتب سبب التسوية أولاً."; return; }
        const rec = {
            id: uid("k"), type: "count", no: nextNo("K", "count"), day: openDay(), at: nowISO(), settled: choice === "settle", reason: reason,
            lines: ls.map(function (l) { return { cur: l.cur, recorded: l.recorded.toFixed(), actual: l.actual.toFixed(), diff: l.actual.minus(l.recorded).toFixed() }; })
        };
        if (rec.settled) rec.settledAt = rec.at;
        try {
            await commit([rec], {
                check: function (next, st2) {
                    /* الرصيد المسجل تغيّر منذ فتح الصفحة (عملية من جهاز آخر)؟ */
                    const prevState = E.replay(Array.from(records.values()), S);
                    for (const l of rec.lines) {
                        const prev = prevState.curs[l.cur].qty;
                        if (!prev.eq(E.dec(l.recorded))) return "تغيّر الرصيد المسجل لـ " + curName(l.cur) + " أثناء الجرد. راجع الأرقام وأعد الاعتماد.";
                    }
                    return null;
                }
            });
            ui.countDraft = {};
            toast(rec.settled ? "تم اعتماد الجرد وتسوية الأرصدة" : "تم حفظ الجرد دون تعديل الأرصدة", "ok");
            render();
        } catch (er) { err.textContent = failMsg(er); }
    };
}
async function settleLater(id) {
    const k = records.get(id);
    if (!k || k.settled || k.settledBy) { toast("سُوّي هذا الجرد مسبقاً", "bad"); return; }
    const reason = await promptBox("تسوية الجرد " + k.no, "ستُطبَّق الفروقات المسجلة في الجرد على الأرصدة الحالية كتسوية جديدة بتاريخ اليوم.", "سبب التسوية", { okText: "تسوية" });
    if (!reason) return;
    const rec = { id: uid("k"), type: "count", no: nextNo("K", "count"), day: openDay(), at: nowISO(), settled: true, settledAt: nowISO(), reason: reason, fromCount: k.id, lines: clone(k.lines) };
    const upd = Object.assign(clone(k), { settledBy: rec.id });
    try {
        await commit([rec, upd, audit("settle", upd, k, reason)], {
            check: function (next, st, s, prev) { const p = prev.get(id); return p && (p.settled || p.settledBy) ? "سُوّي هذا الجرد مسبقاً." : null; }
        });
        toast("تمت التسوية", "ok"); render();
    } catch (e) { toast(failMsg(e), "bad"); }
}

/* =========================================================
   التقارير
   ========================================================= */
const REPORTS = {
    daily: "تقرير العمليات اليومية", monthly: "تقرير العمليات الشهرية", pnl: "تقرير الأرباح والخسائر", carried: "تقرير الأرباح المرحّلة",
    balances: "تقرير أرصدة الصندوق", moves: "تقرير حركات الصندوق", withdrawals: "تقرير سحب الأرباح", counts: "تقرير الجرد والمطابقة",
    countDiffs: "تقرير فروقات الجرد", pairs: "تقرير الأرباح حسب زوج العملات", annual: "تقرير سنوي شامل"
};
/* خلية التقرير: {t: نص للعرض، n: رقم لـ Excel، c: صنف لون} */
function cN(x, dp, color) { const d = E.dec(x); return { t: fmt(d, dp), n: numOut(d, dp), c: color ? cls(d) : "" }; }
function reportOps(list, title) {
    const rows = [];
    let p = E.ZERO, l = E.ZERO, disp = E.ZERO;
    list.forEach(function (r) {
        const x = ST.results[r.id];
        const pr = x ? x.profit : E.ZERO;
        if (pr.gt(0)) p = p.plus(pr); else l = l.plus(pr.neg());
        if (x && x.sypRate) disp = disp.plus(pr.times(x.sypRate));
        rows.push([r.no, fmtDay(r.day), fmtTime(r.at), r.inCur, r.outCur, cN(r.inAmt, E.decimalsOf(S, r.inCur)), cN(r.outAmt, E.decimalsOf(S, r.outCur)), cN(r.rate, 6),
            x ? fmtCostRate(x.manualUnitCost || x.unitCostOut, r.outCur) : "", cN(pr, 2, true), x && x.sypRate ? cN(pr.times(x.sypRate), E.decimalsOf(S, S.display), true) : "", r.note || ""]);
    });
    return {
        title: title, head: ["رقم", "التاريخ", "الوقت", "المستلمة", "المسلّمة", "المبلغ المستلم", "المبلغ المسلّم", "سعر الصرف", "سعر التكلفة", "الربح/الخسارة $", "بالليرة", "ملاحظات"],
        rows: rows, totals: ["الإجمالي (" + rows.length + ")", "", "", "", "", "", "", "", "", cN(p.minus(l), 2, true), cN(disp, E.decimalsOf(S, S.display), true), ""],
        summary: [["الأرباح", moneyText(p)], ["الخسائر", moneyText(l)], ["الصافي", moneyText(p.minus(l))]]
    };
}
function reportDayClose(day) {
    const c = closeOf(day);
    const rep = reportOps(byType("exchange").filter(function (r) { return r.day === day; }).sort(E.sortLedger), "تقرير إقفال يوم " + fmtDay(day));
    if (c) {
        const s = c.snapshot, rate = E.parse(s.displayRate);
        rep.summary = [["عدد العمليات", String(s.count)], ["إجمالي الأرباح", moneyText(s.profit, rate)], ["إجمالي الخسائر", moneyText(s.loss, rate)], ["صافي الربح", moneyText(s.net, rate)],
            ["فروقات الجرد", moneyText(s.inventory, rate)], ["سعر الدولار وقت الإقفال", rate ? fmtTrim(rate, 4) : "—"], ["الحالة", c.status === "closed" ? "مقفل" : "معاد فتحه"]]
            .concat(Object.keys(s.balances).filter(function (k) { return E.curInfo(S, k); }).map(function (k) { return ["رصيد " + curName(k), fmt(s.balances[k].qty, E.decimalsOf(S, k)) + " " + sym(k)]; }));
    }
    return rep;
}
function inRange(day, from, to) { return (!from || day >= from) && (!to || day <= to); }
function buildReport(type, from, to) {
    const rep = buildReportInner(type, from, to);
    rep.key = rep.key || type;
    return rep;
}
function buildReportInner(type, from, to) {
    const title = REPORTS[type] + (from || to ? " (" + (from ? fmtDay(from) : "البداية") + " — " + (to ? fmtDay(to) : "اليوم") + ")" : "");
    const dp = E.decimalsOf(S, S.display);
    if (type === "daily") return reportOps(byType("exchange").filter(function (r) { return inRange(r.day, from, to); }).sort(E.sortLedger), title);
    if (type === "monthly" || type === "annual") {
        const groups = {};
        Object.keys(ST.days).forEach(function (d) {
            if (type === "monthly" && !inRange(d, from, to)) return;
            if (type === "annual" && from && d.slice(0, 4) !== from.slice(0, 4)) return;
            const k = d.slice(0, 7), a = ST.days[d];
            const g = groups[k] || (groups[k] = E.emptyDay(k));
            g.count += a.count; g.profit = g.profit.plus(a.profit); g.loss = g.loss.plus(a.loss); g.net = g.net.plus(a.net);
            g.valuation = g.valuation.plus(a.valuation); g.inventory = g.inventory.plus(a.inventory); g.withdrawals = g.withdrawals.plus(a.withdrawals);
            g.capitalIn = g.capitalIn.plus(a.capitalIn); g.capitalOut = g.capitalOut.plus(a.capitalOut); g.sypEquivalent = g.sypEquivalent.plus(a.sypEquivalent);
        });
        const keys = Object.keys(groups).sort();
        const T = E.emptyDay("");
        const rows = keys.map(function (k) {
            const g = groups[k];
            ["profit", "loss", "net", "valuation", "inventory", "withdrawals", "capitalIn", "capitalOut", "sypEquivalent"].forEach(function (f) { T[f] = T[f].plus(g[f]); });
            T.count += g.count;
            return type === "monthly" ? [monthName(k), g.count, cN(g.profit, 2), cN(g.loss, 2), cN(g.net, 2, true), cN(g.sypEquivalent, dp, true)]
                : [monthName(k), g.count, cN(g.profit, 2), cN(g.loss, 2), cN(g.net, 2, true), cN(g.valuation, 2, true), cN(g.inventory, 2, true), cN(g.withdrawals, 2), cN(g.capitalIn, 2), cN(g.capitalOut, 2), cN(g.sypEquivalent, dp, true)];
        });
        if (type === "monthly") return { title: title, head: ["الشهر", "العمليات", "الأرباح $", "الخسائر $", "الصافي $", "الصافي " + sym(S.display)], rows: rows, totals: ["الإجمالي", T.count, cN(T.profit, 2), cN(T.loss, 2), cN(T.net, 2, true), cN(T.sypEquivalent, dp, true)] };
        return {
            title: REPORTS.annual + " " + (from ? from.slice(0, 4) : "(كل السنوات)"),
            head: ["الشهر", "العمليات", "الأرباح $", "الخسائر $", "صافي الصرافة $", "تسويات التقييم $", "فروقات الجرد $", "سحوبات الأرباح $", "إضافات رأس المال $", "سحوبات الصندوق $", "الصافي " + sym(S.display)],
            rows: rows, totals: ["الإجمالي", T.count, cN(T.profit, 2), cN(T.loss, 2), cN(T.net, 2, true), cN(T.valuation, 2, true), cN(T.inventory, 2, true), cN(T.withdrawals, 2), cN(T.capitalIn, 2), cN(T.capitalOut, 2), cN(T.sypEquivalent, dp, true)],
            summary: [["النتيجة الإجمالية بعد التسويات", moneyText(T.net.plus(T.valuation).plus(T.inventory))]].concat(E.enabledCurrencies(S).map(function (c) { return ["رصيد " + c.name + " الحالي", fmt(ST.curs[c.code].qty, c.decimals) + " " + sym(c.code)]; }))
        };
    }
    if (type === "pnl") {
        const T = E.emptyDay("");
        const rows = Object.keys(ST.days).filter(function (d) { return inRange(d, from, to); }).sort().map(function (d) {
            const a = ST.days[d];
            ["profit", "loss", "net", "valuation", "inventory", "sypEquivalent"].forEach(function (f) { T[f] = T[f].plus(a[f]); });
            T.count += a.count;
            return [fmtDay(d), a.count, cN(a.profit, 2), cN(a.loss, 2), cN(a.net, 2, true), cN(a.valuation, 2, true), cN(a.inventory, 2, true), cN(a.net.plus(a.valuation).plus(a.inventory), 2, true), cN(a.sypEquivalent, dp, true)];
        });
        return { title: title, head: ["التاريخ", "العمليات", "الأرباح $", "الخسائر $", "صافي الصرافة $", "تسويات التقييم $", "فروقات الجرد $", "النتيجة الإجمالية $", "صافي الصرافة " + sym(S.display)], rows: rows,
            totals: ["الإجمالي", T.count, cN(T.profit, 2), cN(T.loss, 2), cN(T.net, 2, true), cN(T.valuation, 2, true), cN(T.inventory, 2, true), cN(T.net.plus(T.valuation).plus(T.inventory), 2, true), cN(T.sypEquivalent, dp, true)] };
    }
    if (type === "carried") {
        let cnt = 0, p = E.ZERO, l = E.ZERO, n = E.ZERO, dd = E.ZERO;
        const rows = closedRows({ from: from, to: to }).map(function (c) {
            const s = c.snapshot, rate = E.parse(s.displayRate);
            cnt += Number(s.count); p = p.plus(s.profit); l = l.plus(s.loss); n = n.plus(s.net);
            const nd = rate ? E.dec(s.net).times(rate) : E.ZERO; dd = dd.plus(nd);
            return [fmtDay(c.day), Number(s.count), cN(s.profit, 2), cN(s.loss, 2), cN(s.net, 2, true), cN(nd, dp, true), rate ? cN(rate, 4) : "", c.status === "closed" ? "مقفل" : "معاد فتحه"];
        });
        return { title: title, head: ["التاريخ", "العمليات", "الأرباح $", "الخسائر $", "الصافي $", "الصافي " + sym(S.display), "سعر الإقفال", "الحالة"], rows: rows, totals: ["الإجمالي", cnt, cN(p, 2), cN(l, 2), cN(n, 2, true), cN(dd, dp, true), "", ""] };
    }
    if (type === "balances") {
        const at = to || openDay();
        const bal = E.balancesAt(ST, at) || {};
        let tb = E.ZERO, tr = E.ZERO;
        const rows = E.enabledCurrencies(S).map(function (c) {
            const b = to ? (bal[c.code] || { qty: E.ZERO, book: E.ZERO }) : ST.curs[c.code];
            const ref = c.code === S.base ? E.ONE : ST.refs[c.code];
            tb = tb.plus(b.book); if (ref) tr = tr.plus(b.qty.div(ref));
            return [c.name, cN(b.qty, c.decimals), b.qty.gt(0) ? fmtCostRate(b.book.div(b.qty), c.code) : "—", cN(b.book, 2), ref ? cN(b.qty.div(ref), 2) : "", ref ? cN(b.qty.div(ref).minus(b.book), 2, true) : ""];
        });
        return { title: REPORTS.balances + (to ? " في نهاية " + fmtDay(to) : " (الحالية)"), head: ["العملة", "الرصيد", "متوسط التكلفة", "القيمة الدفترية $", "القيمة بالسعر المرجعي الحالي $", "فرق إعادة التقييم $"], rows: rows, totals: ["الإجمالي", "", "", cN(tb, 2), cN(tr, 2), cN(tr.minus(tb), 2, true)] };
    }
    if (type === "moves") {
        const rows = ST.moves.filter(function (m) { return m.kind !== "exchange-in" && m.kind !== "exchange-out" && inRange(m.day || "", from, to); }).map(function (m) {
            return [fmtDay(m.day), fmtTime(m.at), m.cur, cN(m.before, E.decimalsOf(S, m.cur)), cN(m.delta, E.decimalsOf(S, m.cur), true), cN(m.after, E.decimalsOf(S, m.cur)), MOVE_NAMES[m.kind] || m.kind, m.reason, m.no || "", cN(m.bookDelta, 2)];
        });
        return { title: title, head: ["التاريخ", "الوقت", "العملة", "الرصيد السابق", "التغيير", "الرصيد الجديد", "نوع الحركة", "السبب", "المرجع", "الأثر الدفتري $"], rows: rows, note: "لا يشمل حركات الصرافة (انظر تقرير العمليات)." };
    }
    if (type === "withdrawals") {
        let t = E.ZERO;
        const rows = byType("withdrawal").filter(function (r) { return inRange(r.day, from, to); }).sort(E.sortLedger).map(function (r) {
            t = t.plus(r.usdValue);
            return [r.no || "", fmtDay(r.day), r.cur, cN(r.amount, E.decimalsOf(S, r.cur)), cN(r.rate || 1, 6), cN(r.usdValue, 2), r.note || "", r.createdBy || ""];
        });
        return { title: title, head: ["رقم", "التاريخ", "العملة", "المبلغ", "السعر (وحدات/$)", "بالدولار", "ملاحظات", "بواسطة"], rows: rows, totals: ["الإجمالي", "", "", "", "", cN(t, 2), "", ""] };
    }
    if (type === "counts" || type === "countDiffs") {
        const rows = [];
        let t = E.ZERO;
        byType("count").filter(function (r) { return inRange(r.day, from, to); }).sort(E.sortLedger).forEach(function (k) {
            const res = ST.results[k.id];
            (k.lines || []).forEach(function (l) {
                if (type === "countDiffs" && E.dec(l.diff).isZero()) return;
                const li = res && res.lines[l.cur];
                if (k.settled && li) t = t.plus(li.bookDelta);
                rows.push([k.no, fmtDay(k.day), fmtTime(k.at), l.cur, cN(l.recorded, E.decimalsOf(S, l.cur)), cN(l.actual, E.decimalsOf(S, l.cur)), cN(l.diff, E.decimalsOf(S, l.cur), true),
                    E.dec(l.diff).isZero() ? "مطابق" : E.dec(l.diff).lt(0) ? "نقص" : "زيادة", k.settled ? "نعم" : (k.settledBy ? "لاحقاً" : "لا"), li ? cN(k.settled ? li.bookDelta : li.atRef, 2, true) : "", k.reason || ""]);
            });
        });
        return { title: title, head: ["رقم الجرد", "التاريخ", "الوقت", "العملة", "المسجل", "الفعلي", "الفرق", "الحالة", "تمت التسوية؟", "الأثر بالدولار", "السبب"], rows: rows, totals: ["إجمالي أثر التسويات المعتمدة", "", "", "", "", "", "", "", "", cN(t, 2, true), ""] };
    }
    if (type === "pairs") {
        const g = {};
        byType("exchange").filter(function (r) { return inRange(r.day, from, to); }).forEach(function (r) {
            const x = ST.results[r.id]; if (!x) return;
            const k = E.pairOf(S, r.inCur, r.outCur).key;
            const o = g[k] || (g[k] = { n: 0, p: E.ZERO, l: E.ZERO, net: E.ZERO, d: E.ZERO });
            o.n++; o.net = o.net.plus(x.profit); if (x.profit.gt(0)) o.p = o.p.plus(x.profit); else o.l = o.l.plus(x.profit.neg());
            if (x.sypRate) o.d = o.d.plus(x.profit.times(x.sypRate));
        });
        const T = { n: 0, p: E.ZERO, l: E.ZERO, net: E.ZERO, d: E.ZERO };
        const rows = Object.keys(g).sort().map(function (k) {
            const o = g[k]; T.n += o.n; T.p = T.p.plus(o.p); T.l = T.l.plus(o.l); T.net = T.net.plus(o.net); T.d = T.d.plus(o.d);
            return [k, o.n, cN(o.p, 2), cN(o.l, 2), cN(o.net, 2, true), cN(o.d, dp, true)];
        });
        return { title: title, head: ["الزوج", "العمليات", "الأرباح $", "الخسائر $", "الصافي $", "الصافي " + sym(S.display)], rows: rows, totals: ["الإجمالي", T.n, cN(T.p, 2), cN(T.l, 2), cN(T.net, 2, true), cN(T.d, dp, true)] };
    }
    return { title: "", head: [], rows: [] };
}
function cellText(c) { return c && typeof c === "object" ? c.t : (c === null || c === undefined ? "" : String(c)); }
function cellHtml(c) { return c && typeof c === "object" ? '<span class="num ' + (c.c || "") + '">' + esc(c.t) + "</span>" : esc(c); }
function reportTableHtml(rep) {
    return '<div class="table-wrap"><table><thead><tr>' + rep.head.map(function (h) { return "<th>" + esc(h) + "</th>"; }).join("") + "</tr></thead><tbody>" +
        (rep.rows.length ? rep.rows.map(function (r) { return "<tr>" + r.map(function (c) { return "<td>" + cellHtml(c) + "</td>"; }).join("") + "</tr>"; }).join("") : '<tr><td colspan="' + rep.head.length + '" class="empty">لا توجد بيانات في هذه الفترة</td></tr>') +
        (rep.totals && rep.rows.length ? '<tr class="total">' + rep.totals.map(function (c) { return "<td>" + cellHtml(c) + "</td>"; }).join("") + "</tr>" : "") + "</tbody></table></div>";
}
function summaryHtml(rep) {
    return rep.summary ? '<div class="kv sum" style="margin-top:10px">' + rep.summary.map(function (s) { return "<div>" + esc(s[0]) + "</div><div>" + esc(s[1]) + "</div>"; }).join("") + "</div>" : "";
}
function exportReportXlsx(rep) {
    const rows = [[rep.title], [settingsRec().shopName + " — " + fmtDateTime(nowISO())], [], rep.head];
    rep.rows.forEach(function (r) { rows.push(r.map(function (c) { return c && typeof c === "object" ? c.n : c; })); });
    if (rep.totals && rep.rows.length) rows.push(rep.totals.map(function (c) { return c && typeof c === "object" ? c.n : c; }));
    if (rep.summary) { rows.push([]); rep.summary.forEach(function (s) { rows.push([s[0], s[1]]); }); }
    XLSXLite.download("smart-exchange-" + (rep.key || "report") + "-" + openDay() + ".xlsx", "تقرير", rows, { headerRows: 0 });
}
function printReport(rep) {
    const shop = settingsRec().shopName || "";
    $("#printArea").innerHTML = "<h1>" + esc(rep.title) + '</h1><div class="meta">' + esc(shop) + " — طُبع في " + esc(fmtDateTime(nowISO())) + " — بواسطة " + esc(me ? me.username : "") + "</div>" +
        reportTableHtml(rep) + summaryHtml(rep) + (rep.note ? '<p class="meta">' + esc(rep.note) + "</p>" : "");
    setTimeout(function () { window.print(); }, 50);
}
function renderReports(main) {
    const t = ui.reportType;
    const rep = buildReport(t, ui.reportFrom, ui.reportTo);
    main.innerHTML = pageHead("التقارير", "الأرقام مأخوذة مباشرة من قاعدة البيانات. للتصدير إلى PDF استخدم «طباعة» ثم «حفظ كـ PDF».") +
        '<div class="card"><div class="grid g4">' +
        '<label class="field"><span>نوع التقرير</span><select id="rpType">' + Object.keys(REPORTS).map(function (k) { return '<option value="' + k + '"' + (k === t ? " selected" : "") + ">" + REPORTS[k] + "</option>"; }).join("") + "</select></label>" +
        '<label class="field"><span>' + (t === "annual" ? "السنة (أي تاريخ فيها)" : t === "balances" ? "—" : "من تاريخ") + '</span><input type="date" id="rpFrom" value="' + esc(ui.reportFrom) + '"' + (t === "balances" ? " disabled" : "") + "></label>" +
        '<label class="field"><span>' + (t === "balances" ? "الأرصدة في نهاية يوم (فارغ = الحالية)" : "إلى تاريخ") + '</span><input type="date" id="rpTo" value="' + esc(ui.reportTo) + '"' + (t === "annual" ? " disabled" : "") + "></label>" +
        '<div class="row" style="align-self:end"><button class="btn" data-q="today">اليوم</button><button class="btn" data-q="month">الشهر</button><button class="btn" data-q="year">السنة</button><button class="btn" data-q="all">الكل</button></div></div>' +
        '<div class="row" style="margin-top:12px"><button class="btn primary" data-act="print">طباعة / PDF</button><button class="btn success" data-act="xlsx">تصدير Excel</button></div></div>' +
        '<div class="card"><h3>' + esc(rep.title) + "</h3>" + reportTableHtml(rep) + summaryHtml(rep) + (rep.note ? '<p class="muted small">' + esc(rep.note) + "</p>" : "") + "</div>";
    $("#rpType").addEventListener("change", function (e) { ui.reportType = e.target.value; renderReports(main); });
    $("#rpFrom").addEventListener("change", function (e) { ui.reportFrom = e.target.value; renderReports(main); });
    $("#rpTo").addEventListener("change", function (e) { ui.reportTo = e.target.value; renderReports(main); });
    main.onclick = function (e) {
        const q = e.target.closest("[data-q]");
        if (q) {
            const rg = periodRange(q.dataset.q === "all" ? "all" : q.dataset.q);
            ui.reportFrom = q.dataset.q === "all" ? "" : rg.from;
            ui.reportTo = q.dataset.q === "all" ? "" : (q.dataset.q === "today" ? rg.to : "");
            renderReports(main); return;
        }
        const b = e.target.closest("[data-act]");
        if (!b) return;
        if (b.dataset.act === "print") printReport(rep);
        if (b.dataset.act === "xlsx") exportReportXlsx(rep);
    };
}

/* =========================================================
   الإعدادات
   ========================================================= */
const TIMEZONES = ["Asia/Damascus", "Europe/Istanbul", "Asia/Beirut", "Asia/Amman", "Asia/Baghdad", "Asia/Riyadh", "Asia/Dubai", "Europe/Berlin", "UTC"];
function renderSettings(main) {
    const set = clone(settingsRec());
    const opsExist = ledgerOps().length > 0;
    const isMgr = can("settings");
    const opening = records.get("opening");
    const lastBackup = lsGet("sx_last_backup");
    main.innerHTML = pageHead("الإعدادات", "الإصدار " + APP_VERSION + " — رمز هذا الجهاز: " + esc(deviceCode())) +
        (!isMgr ? '<div class="alert info">الإعدادات للمدير. يمكنك تغيير كلمة مرورك والوضع الليلي فقط.</div>' : "") +

        /* العملات */
        (isMgr ? '<div class="card"><h3>إعدادات العملات</h3><p class="muted small">الدولار عملة القياس المحاسبي للأرباح، والليرة السورية الجديدة عملة العرض الأولى. ' + (opsExist ? "لا يمكن تغيير رموز العملات بعد بدء العمليات." : "") + "</p>" +
            '<div class="table-wrap"><table><thead><tr><th>مفعّلة</th><th>الاسم</th><th>الرمز</th><th>العلامة</th><th>المنازل العشرية</th><th>القوة في التسعير</th></tr></thead><tbody>' +
            set.currencies.map(function (c, i) {
                const fixed = c.code === "USD" || c.code === "SYP";
                const used = opsExist && (ST.curs[c.code] && (!ST.curs[c.code].qty.isZero() || ST.moves.some(function (m) { return m.cur === c.code; })));
                return '<tr><td><input type="checkbox" data-sc="enabled" data-i="' + i + '"' + (c.enabled ? " checked" : "") + (fixed ? " disabled" : "") + ' style="width:22px;min-height:22px"></td>' +
                    '<td><input data-sc="name" data-i="' + i + '" value="' + esc(c.name) + '"></td>' +
                    '<td><input data-sc="code" data-i="' + i + '" value="' + esc(c.code) + '"' + (fixed || opsExist ? " readonly" : "") + ' style="width:90px"></td>' +
                    '<td><input data-sc="symbol" data-i="' + i + '" value="' + esc(c.symbol) + '" style="width:80px"></td>' +
                    '<td><input data-sc="decimals" data-i="' + i + '" value="' + c.decimals + '" class="num-in" style="width:70px"></td>' +
                    '<td><input data-sc="strength" data-i="' + i + '" value="' + c.strength + '" class="num-in" style="width:70px"' + (opsExist ? " readonly" : "") + "></td></tr>" +
                    (used && !c.enabled ? "" : "");
            }).join("") + "</tbody></table></div>" +
            '<p class="muted small">تعطيل عملة (مثل اليورو) يخفيها من الواجهة والأزواج؛ لا يمكن تعطيل عملة لها رصيد.</p></div>' : "") +

        /* الحساب والأسعار المرجعية */
        (isMgr ? '<div class="card"><h3>إعدادات الحساب والتقييم</h3><div class="grid g3">' +
            '<label class="field"><span>طريقة حساب التكلفة الافتراضية</span><select id="stCost"><option value="avg"' + (set.costMethod !== "manual" ? " selected" : "") + '>المتوسط المرجح التلقائي</option><option value="manual"' + (set.costMethod === "manual" ? " selected" : "") + ">التكلفة اليدوية</option></select></label>" +
            '<label class="field"><span>سياسة التقريب (للمبالغ النهائية فقط)</span><select id="stRound">' + [["half-up", "تقريب نصف للأعلى"], ["half-even", "تقريب مصرفي"], ["down", "قطع (للأسفل)"], ["up", "للأعلى"]].map(function (o) { return '<option value="' + o[0] + '"' + (set.rounding === o[0] ? " selected" : "") + ">" + o[1] + "</option>"; }).join("") + "</select></label>" +
            '<label class="field"><span>سياسة الاعتراف بالأرباح</span><select id="stVal"><option value="reference"' + (set.valuation !== "cost" ? " selected" : "") + '>التقييم المرجعي (ربح كل عملية فوراً)</option><option value="cost"' + (set.valuation === "cost" ? " selected" : "") + ">عند العودة إلى الدولار فقط</option></select><span class=\"hint\">" + (opsExist ? "تغييرها يعيد احتساب كل الأرباح السابقة؛ الأيام المقفلة ستحتاج إعادة اعتماد." : "") + "</span></label>" +
            '<label class="field"><span>اسم المحل (للطباعة)</span><input id="stShop" value="' + esc(set.shopName || "") + '"></label></div>' +
            '<h4 style="margin-top:14px">الأسعار المرجعية المستخدمة في التقييم (اختياري)</h4><p class="muted small">فارغ = منتصف سعري الشراء والبيع مع الدولار. القيمة: عدد الوحدات لكل 1 دولار.</p><div class="grid g4">' +
            set.currencies.filter(function (c) { return c.code !== "USD" && c.enabled; }).map(function (c) {
                return '<label class="field"><span>' + esc(c.name) + '</span><input class="num-in" inputmode="decimal" data-ref="' + c.code + '" value="' + esc((set.refOverride || {})[c.code] || "") + '" placeholder="' + (ST.refs[c.code] ? fmtTrim(ST.refs[c.code], 6) : "") + '"></label>';
            }).join("") + "</div>" +
            '<p class="muted small">أسعار الشراء والبيع وسعر الدولار مقابل الليرة: من صفحة <a href="#" data-go="rates">أسعار الصرف</a>.</p></div>' : "") +

        /* الإقفال */
        (isMgr ? '<div class="card"><h3>إعدادات الإقفال</h3><div class="grid g3">' +
            '<label class="field"><span>طريقة الإقفال</span><select id="stClose"><option value="manual"' + (set.closeMode !== "auto" ? " selected" : "") + '>إقفال يدوي</option><option value="auto"' + (set.closeMode === "auto" ? " selected" : "") + ">إقفال تلقائي</option></select></label>" +
            '<label class="field"><span>ساعة نهاية/بداية اليوم المحاسبي</span><select id="stHour">' + Array.from({ length: 24 }, function (_, h) { return '<option value="' + h + '"' + (Number(set.dayStartHour) === h ? " selected" : "") + ">" + String(h).padStart(2, "0") + ":00" + (h === 0 ? " (منتصف الليل)" : "") + "</option>"; }).join("") + "</select></label>" +
            '<label class="field"><span>المنطقة الزمنية</span><select id="stTz">' + TIMEZONES.map(function (z) { return "<option" + (set.timeZone === z ? " selected" : "") + ">" + z + "</option>"; }).join("") + "</select></label></div>" +
            '<p class="muted small">تغيير الساعة أو المنطقة لا يغيّر يوم العمليات المحفوظة (يُحفظ يوم كل عملية معها).</p></div>' : "") +

        (isMgr ? '<div class="row" style="margin-bottom:16px"><button class="btn primary big" data-act="save-settings">حفظ الإعدادات</button></div>' : "") +

        /* المستخدمون */
        '<div class="card"><h3>المستخدمون</h3><p class="muted small">يمكن الدخول بالحساب نفسه (مثل admin) من أكثر من جوال في الوقت نفسه. الصلاحيات: المدير (كل شيء)، الصرّاف (العمليات وحفظ الجرد)، المشاهد (عرض فقط).</p>' +
        '<div class="table-wrap"><table><thead><tr><th>اسم المستخدم</th><th>الاسم</th><th>الصلاحية</th><th></th></tr></thead><tbody>' +
        set.users.map(function (u) {
            const self = me && u.id === me.id;
            return "<tr><td class=\"ltr\"><b>" + esc(u.username) + "</b>" + (self ? ' <span class="badge info">أنت</span>' : "") + (u.mustChange ? ' <span class="badge warn">كلمة مرور افتراضية</span>' : "") + "</td><td>" + esc(u.name || "") + "</td><td>" + esc(ROLES[u.role] || u.role) + "</td><td>" +
                (self || isMgr ? '<button class="btn sm" data-pw="' + esc(u.id) + '">كلمة المرور</button>' : "") +
                (isMgr ? '<button class="btn sm" data-ue="' + esc(u.id) + '">تعديل</button>' + (self ? "" : '<button class="btn sm" data-ud="' + esc(u.id) + '">حذف</button>') : "") + "</td></tr>";
        }).join("") + "</tbody></table></div>" +
        (isMgr ? '<div class="row" style="margin-top:10px"><button class="btn" data-act="add-user">إضافة مستخدم</button></div>' : "") + "</div>" +

        /* المزامنة */
        '<div class="card" id="syncCard"><h3>المزامنة بين الأجهزة (أكثر من جوال)</h3><div id="syncPanel"></div></div>' +

        /* النظام والنسخ */
        '<div class="card"><h3>النظام والنسخ الاحتياطي</h3>' +
        '<div class="alert warn"><b>مهم:</b> IndexedDB ليست بديلاً عن النسخ الاحتياطي: حذف بيانات المتصفح أو تلف الجهاز قد يؤدي إلى فقدان البيانات. نزّل نسخة احتياطية بانتظام' + (lastBackup ? " (آخر نسخة من هذا الجهاز: " + esc(fmtDateTime(lastBackup)) + ")" : " (لم تُنزَّل نسخة من هذا الجهاز بعد)") + ".</div>" +
        '<div class="row"><button class="btn" data-act="theme">الوضع الليلي/النهاري</button>' +
        (can("backup") ? '<button class="btn primary" data-act="backup">تنزيل نسخة احتياطية (JSON)</button><button class="btn" data-act="restore">استعادة نسخة احتياطية</button><button class="btn" data-act="export-xlsx">تصدير كل العمليات Excel</button>' : "") +
        "</div>" +
        '<label class="field" style="margin-top:12px;max-width:260px"><span>رمز هذا الجهاز (يظهر في أرقام العمليات)</span><input id="stDev" value="' + esc(deviceCode()) + '" maxlength="3" class="ltr"></label>' +
        "</div>" +

        /* الصندوق الافتتاحي */
        (isMgr && opening ? '<div class="card"><h3>الصندوق الافتتاحي</h3>' + (opsExist ? '<p class="muted small">بدأت العمليات: التعديل يتم عبر حركات «إدارة الصندوق» الموثقة (إضافة/سحب/استبدال) بدلاً من تغيير البيانات الافتتاحية.</p>' :
            '<p class="muted small">لم تبدأ العمليات بعد — يمكن إعادة الإعداد الافتتاحي.</p><button class="btn" data-act="redo-setup">إعادة إعداد الصندوق الافتتاحي</button>') +
            '<div class="table-wrap" style="margin-top:10px"><table><thead><tr><th>العملة</th><th>الرصيد الافتتاحي</th><th>التكلفة</th></tr></thead><tbody>' +
            Object.keys(opening.balances || {}).map(function (k) { const b = opening.balances[k]; return "<tr><td>" + esc(curName(k)) + '</td><td class="num">' + fmtCur(b.qty, k) + "</td><td>" + esc(fmtCostRate(b.unitCost, k)) + "</td></tr>"; }).join("") + "</tbody></table></div></div>" : "") +

        (isMgr ? '<div class="card" style="border-color:var(--danger)"><h3 class="neg">منطقة الخطر</h3><p class="muted small">حذف كل البيانات من هذا الجهاز (لا يحذف النسخة على Firebase). نزّل نسخة احتياطية أولاً.</p><button class="btn danger" data-act="wipe">حذف كل البيانات من هذا الجهاز</button></div>' : "");

    renderSyncPanel();
    $$("[data-sc]", main).forEach(function (el) {
        const h = function () {
            const c = set.currencies[Number(el.dataset.i)], k = el.dataset.sc;
            if (k === "enabled") c.enabled = el.checked;
            else if (k === "decimals" || k === "strength") c[k] = parseInt(el.value, 10) || 0;
            else if (k === "code") c.code = el.value.trim().toUpperCase();
            else c[k] = el.value;
        };
        el.addEventListener("input", h); el.addEventListener("change", h);
    });
    const devEl = $("#stDev");
    devEl.addEventListener("change", function () {
        const v = devEl.value.trim().toUpperCase();
        if (!/^[A-Z]{1,3}$/.test(v)) { toast("رمز الجهاز: من حرف إلى 3 أحرف إنجليزية", "bad"); devEl.value = deviceCode(); return; }
        lsSet("sx_device", v); toast("تم تغيير رمز الجهاز إلى " + v, "ok");
    });

    main.onclick = async function (e) {
        const g = e.target.closest("[data-go]");
        if (g) { e.preventDefault(); go(g.dataset.go); return; }
        const pw = e.target.closest("[data-pw]");
        if (pw) { changePassword(pw.dataset.pw); return; }
        const ue = e.target.closest("[data-ue]");
        if (ue) { editUser(ue.dataset.ue); return; }
        const ud = e.target.closest("[data-ud]");
        if (ud) { deleteUser(ud.dataset.ud); return; }
        const sb = e.target.closest("[data-sync]");
        if (sb) { syncAction(sb.dataset.sync); return; }
        const b = e.target.closest("[data-act]");
        if (!b) return;
        const act = b.dataset.act;
        if (act === "theme") toggleTheme();
        if (act === "backup") downloadBackup();
        if (act === "restore") pickRestoreFile();
        if (act === "export-xlsx") exportReportXlsx(reportOps(byType("exchange").sort(E.sortLedger), "كل العمليات"));
        if (act === "redo-setup") {
            if (opsExist) return;
            if (!(await confirmBox("إعادة الإعداد", "سيُحذف الإعداد الافتتاحي الحالي لتعيد إدخاله.", "متابعة"))) return;
            const o = Object.assign(clone(opening), { deleted: true, deletedAt: nowISO(), deleteReason: "إعادة الإعداد الافتتاحي" });
            try { await commit([o, audit("delete", o, opening, "إعادة الإعداد الافتتاحي")], { validate: false }); setupDraft = null; go("dashboard"); } catch (er) { toast(failMsg(er), "bad"); }
        }
        if (act === "wipe") wipeAll();
        if (act === "add-user") editUser(null);
        if (act === "save-settings") {
            const codes = {};
            for (const c of set.currencies) {
                if (!/^[A-Z]{2,5}$/.test(c.code) || codes[c.code]) { toast("رمز عملة غير صالح أو مكرر: " + esc(c.code), "bad"); return; }
                codes[c.code] = 1;
                if (!c.name.trim()) { toast("اسم العملة مطلوب", "bad"); return; }
                if (c.decimals < 0 || c.decimals > 8) { toast("المنازل العشرية بين 0 و8", "bad"); return; }
                const st = ST.curs[c.code];
                if (!c.enabled && st && !st.qty.isZero()) { toast("لا يمكن تعطيل " + esc(c.name) + " وفيها رصيد. صفّر رصيدها أولاً من إدارة الصندوق.", "bad"); return; }
            }
            set.costMethod = $("#stCost").value; set.rounding = $("#stRound").value; set.valuation = $("#stVal").value; set.shopName = $("#stShop").value.trim();
            set.closeMode = $("#stClose").value; set.dayStartHour = Number($("#stHour").value); set.timeZone = $("#stTz").value;
            set.refOverride = {};
            for (const el of $$("[data-ref]", main)) {
                const v = el.value.trim();
                if (!v) continue;
                const r = E.parse(v);
                if (!r || r.lte(0)) { toast("سعر مرجعي غير صالح لـ " + el.dataset.ref, "bad"); return; }
                set.refOverride[el.dataset.ref] = r.toFixed();
            }
            const old = settingsRec();
            if (opsExist && old.valuation !== set.valuation && !(await confirmBox("تغيير سياسة الاعتراف بالأرباح", "سيعاد احتساب أرباح كل العمليات السابقة وفق السياسة الجديدة، وستحتاج الأيام المقفلة إلى إعادة اعتماد. متابعة؟", "متابعة"))) return;
            try { await commit([set, audit("edit", set, old, "تعديل الإعدادات")], { validate: false }); toast("تم حفظ الإعدادات", "ok"); render(); }
            catch (er) { toast(failMsg(er), "bad"); }
        }
    };
}
async function saveUsers(list, reason) {
    const set = clone(settingsRec());
    set.users = list;
    await commit([set], { validate: false });
}
function changePassword(id) {
    const u = users().find(function (x) { return x.id === id; });
    if (!u) return;
    const self = me && me.id === id;
    return modal("<h3>تغيير كلمة مرور " + esc(u.username) + "</h3>" +
        (self && u.mustChange ? '<div class="alert warn">أنت تستخدم كلمة المرور الافتراضية. غيّرها الآن لحماية بياناتك.</div>' : "") +
        (self ? '<label class="field"><span>كلمة المرور الحالية</span><input type="password" id="pwOld" autocomplete="current-password"></label>' : "") +
        '<label class="field" style="margin-top:10px"><span>كلمة المرور الجديدة (4 أحرف على الأقل)</span><input type="password" id="pwNew" autocomplete="new-password"></label>' +
        '<label class="field" style="margin-top:10px"><span>تأكيد كلمة المرور</span><input type="password" id="pwNew2" autocomplete="new-password"></label>' +
        '<p class="error-text" id="pwErr"></p><div class="actions"><button class="btn primary" data-modal="ok">حفظ</button><button class="btn" data-modal="cancel">' + (self && u.mustChange ? "لاحقاً" : "إلغاء") + "</button></div>", {
        collect: function (box) {
            const err = box.querySelector("#pwErr");
            if (self && passHash(u.id, box.querySelector("#pwOld").value) !== u.hash) { err.textContent = "كلمة المرور الحالية غير صحيحة."; return false; }
            const a = box.querySelector("#pwNew").value, b = box.querySelector("#pwNew2").value;
            if (a.length < 4) { err.textContent = "كلمة المرور قصيرة."; return false; }
            if (a !== b) { err.textContent = "كلمتا المرور غير متطابقتين."; return false; }
            return a;
        }
    }).then(async function (pw) {
        if (!pw) return;
        const list = clone(users());
        const t = list.find(function (x) { return x.id === id; });
        t.hash = passHash(id, pw); t.mustChange = false;
        try {
            await saveUsers(list, "تغيير كلمة المرور");
            if (self) { me = users().find(function (x) { return x.id === id; }); lsSet("sx_session", JSON.stringify({ id: me.id, hash: me.hash, at: nowISO() })); }
            toast("تم تغيير كلمة المرور. تُطبَّق على كل الأجهزة بعد المزامنة.", "ok");
            render();
        } catch (e) { toast(failMsg(e), "bad"); }
    });
}
function editUser(id) {
    const u = id ? users().find(function (x) { return x.id === id; }) : null;
    modal("<h3>" + (u ? "تعديل المستخدم" : "مستخدم جديد") + "</h3>" +
        '<label class="field"><span>اسم المستخدم (إنجليزي)</span><input id="ueUser" class="ltr" value="' + esc(u ? u.username : "") + '"></label>' +
        '<label class="field" style="margin-top:10px"><span>الاسم</span><input id="ueName" value="' + esc(u ? u.name || "" : "") + '"></label>' +
        '<label class="field" style="margin-top:10px"><span>الصلاحية</span><select id="ueRole"' + (u && me && u.id === me.id ? " disabled" : "") + ">" + Object.keys(ROLES).map(function (r) { return '<option value="' + r + '"' + (u && u.role === r ? " selected" : "") + ">" + ROLES[r] + "</option>"; }).join("") + "</select></label>" +
        (u ? "" : '<label class="field" style="margin-top:10px"><span>كلمة المرور</span><input id="uePass" type="password" autocomplete="new-password"></label>') +
        '<p class="error-text" id="ueErr"></p><div class="actions"><button class="btn primary" data-modal="ok">حفظ</button><button class="btn" data-modal="cancel">إلغاء</button></div>', {
        collect: function (box) {
            const err = box.querySelector("#ueErr");
            const username = box.querySelector("#ueUser").value.trim();
            if (!/^[A-Za-z0-9_.-]{3,30}$/.test(username)) { err.textContent = "اسم المستخدم 3-30 حرفاً إنجليزياً أو رقماً."; return false; }
            if (users().some(function (x) { return x.username.toLowerCase() === username.toLowerCase() && (!u || x.id !== u.id); })) { err.textContent = "اسم المستخدم موجود."; return false; }
            const pass = u ? null : box.querySelector("#uePass").value;
            if (!u && pass.length < 4) { err.textContent = "كلمة المرور 4 أحرف على الأقل."; return false; }
            return { username: username, name: box.querySelector("#ueName").value.trim(), role: box.querySelector("#ueRole").value, pass: pass };
        }
    }).then(async function (v) {
        if (!v) return;
        const list = clone(users());
        if (u) { const t = list.find(function (x) { return x.id === u.id; }); t.username = v.username; t.name = v.name; if (!(me && u.id === me.id)) t.role = v.role; }
        else { const nid = uid("u"); list.push({ id: nid, username: v.username, name: v.name, role: v.role, hash: passHash(nid, v.pass), mustChange: false }); }
        try { await saveUsers(list); if (me) me = users().find(function (x) { return x.id === me.id; }) || me; toast("تم الحفظ", "ok"); render(); } catch (e) { toast(failMsg(e), "bad"); }
    });
}
async function deleteUser(id) {
    const u = users().find(function (x) { return x.id === id; });
    if (!u || (me && me.id === id)) return;
    if (u.role === "manager" && users().filter(function (x) { return x.role === "manager"; }).length < 2) { toast("يجب أن يبقى مدير واحد على الأقل", "bad"); return; }
    if (!(await confirmBox("حذف المستخدم", "حذف " + esc(u.username) + "؟ عملياته السابقة تبقى كما هي.", "حذف", true))) return;
    try { await saveUsers(users().filter(function (x) { return x.id !== id; })); toast("تم الحذف", "ok"); render(); } catch (e) { toast(failMsg(e), "bad"); }
}
async function wipeAll() {
    const v = await promptBox("حذف كل البيانات", '<p class="neg">لا يمكن التراجع. اكتب كلمة <b>حذف</b> للتأكيد.</p>', "التأكيد", { danger: true, okText: "حذف نهائي" });
    if (v !== "حذف") { if (v) toast("لم يُحذف شيء", "info"); return; }
    try {
        await DB.write([], [{ key: "sync", enabled: false, workspace: syncMeta.workspace, lastPull: 0, dirty: {}, lastSyncAt: 0, config: syncMeta.config || "" }], true);
        location.reload();
    } catch (e) { toast(failMsg(e), "bad"); }
}
function toggleTheme() {
    const t = document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", t);
    lsSet("sx_theme", t);
}

/* =========================================================
   النسخ الاحتياطي والاستعادة
   ========================================================= */
function backupPayload() {
    const list = Array.from(records.values()).sort(function (a, b) { return a.id < b.id ? -1 : 1; });
    const body = JSON.stringify(list);
    return { format: BACKUP_FORMAT, version: BACKUP_VERSION, dbVersion: DB_VERSION, appVersion: APP_VERSION, exportedAt: nowISO(), device: deviceCode(), count: list.length, checksum: sha256(body), records: list };
}
function downloadBackup() {
    const data = JSON.stringify(backupPayload(), null, 1);
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob([data], { type: "application/json" }));
    a.download = "smart-exchange-backup-" + openDay() + "-" + Date.now().toString(36) + ".json";
    document.body.appendChild(a); a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
    lsSet("sx_last_backup", nowISO());
    toast("تم تنزيل النسخة الاحتياطية (" + records.size + " سجل). احفظها في مكان آمن خارج الجهاز.", "ok");
}
function validateBackup(obj) {
    if (!obj || obj.format !== BACKUP_FORMAT) return "الملف ليس نسخة احتياطية لنظام الصرافة الذكي.";
    if (typeof obj.version !== "number" || obj.version > BACKUP_VERSION) return "إصدار النسخة (" + obj.version + ") أحدث من هذا النظام. حدّث النظام أولاً.";
    if ((obj.dbVersion || 1) > DB_VERSION) return "إصدار قاعدة البيانات في النسخة أحدث من هذا النظام.";
    if (!Array.isArray(obj.records)) return "الملف تالف: لا توجد سجلات.";
    const ids = {};
    for (const r of obj.records) {
        if (!r || typeof r !== "object" || typeof r.id !== "string" || !r.id || typeof r.type !== "string") return "الملف تالف: سجل غير صالح.";
        if (ids[r.id]) return "الملف تالف: سجل مكرر " + r.id;
        ids[r.id] = 1;
    }
    const sorted = obj.records.slice().sort(function (a, b) { return a.id < b.id ? -1 : 1; });
    if (obj.checksum && obj.checksum !== sha256(JSON.stringify(sorted))) return "بصمة الملف غير مطابقة: الملف معدَّل أو تالف.";
    const set = obj.records.find(function (r) { return r.id === "settings"; });
    if (set && (!Array.isArray(set.currencies) || !Array.isArray(set.users) || !set.users.length)) return "الملف تالف: الإعدادات غير صالحة.";
    try {
        const s = effectiveSettings(new Map(obj.records.map(function (r) { return [r.id, r]; })));
        E.replay(obj.records, s);
    } catch (e) { return "تعذر إعادة بناء الأرصدة من النسخة: " + e.message; }
    return null;
}
function pickRestoreFile() {
    if (!can("backup") && hasOpening()) { toast("الاستعادة للمدير فقط", "bad"); return; }
    const inp = document.createElement("input");
    inp.type = "file"; inp.accept = ".json,application/json";
    inp.onchange = function () { if (inp.files[0]) restoreFromFile(inp.files[0]); };
    inp.click();
}
async function restoreFromFile(file) {
    let obj;
    try { obj = JSON.parse(await file.text()); }
    catch (e) { toast("تعذر قراءة الملف: ليس JSON صالحاً.", "bad", 7000); return; }
    const err = validateBackup(obj);
    if (err) { toast(err, "bad", 8000); return; }
    const counts = {};
    obj.records.forEach(function (r) { if (!r.deleted) counts[r.type] = (counts[r.type] || 0) + 1; });
    const days = obj.records.filter(function (r) { return r.type === "exchange" && !r.deleted; }).map(function (r) { return r.day; }).sort();
    const names = { exchange: "عمليات صرافة", cash: "حركات صندوق", withdrawal: "سحوبات أرباح", count: "عمليات جرد", close: "أيام مقفلة", rate: "أسعار أزواج", audit: "سجلات تدقيق", opening: "رصيد افتتاحي", settings: "إعدادات" };
    const summary = '<div class="kv">' + "<div>تاريخ النسخة</div><div>" + esc(fmtDateTime(obj.exportedAt)) + "</div><div>عدد السجلات</div><div>" + obj.records.length + "</div>" +
        Object.keys(counts).map(function (k) { return "<div>" + esc(names[k] || k) + "</div><div>" + counts[k] + "</div>"; }).join("") +
        (days.length ? "<div>الفترة</div><div>" + fmtDay(days[0]) + " — " + fmtDay(days[days.length - 1]) + "</div>" : "") + "</div>" +
        '<div class="alert warn" style="margin-top:10px">ستُستبدل كل البيانات الحالية على هذا الجهاز (' + records.size + " سجل) بمحتوى النسخة. يُنصح بتنزيل نسخة من البيانات الحالية أولاً." +
        (syncMeta.enabled ? " المزامنة مفعّلة: ستُرفع السجلات المستعادة وتُدمج مع النسخة المشتركة (الأحدث من كل سجل يبقى)." : "") + "</div>";
    const choice = await modal("<h3>استعادة نسخة احتياطية</h3>" + summary + '<div class="actions"><button class="btn danger" data-modal="ok">استعادة</button><button class="btn" data-modal="backup">تنزيل نسخة من الحالي أولاً</button><button class="btn" data-modal="cancel">إلغاء</button></div>');
    if (choice === "backup") { downloadBackup(); return restoreFromFile(file); }
    if (choice !== "ok") return;
    try {
        await withLock(async function () {
            /* معاملة واحدة: مسح + كتابة؛ أي فشل يلغي كل شيء ويبقي البيانات القديمة */
            if (syncMeta.enabled) obj.records.forEach(function (r) { syncMeta.dirty[r.id] = true; });
            await DB.write(obj.records, [syncMeta], true);
            await reloadRecords();
        });
        recompute();
        /* التحقق من سلامة البيانات بعد الاستعادة */
        const back = await DB.all("records");
        if (back.length !== obj.records.length) throw new Error("عدد السجلات بعد الاستعادة غير مطابق");
        if (channel) channel.postMessage({ t: "changed" });
        fbSchedulePush();
        const u = findSessionUser();
        toast("تمت الاستعادة بنجاح (" + back.length + " سجل)" + (ST.negatives.length ? " — تنبيه: توجد أرصدة سالبة في البيانات المستعادة." : ""), "ok", 7000);
        if (!u) { logout(); return; }
        me = u; setupDraft = null; go("dashboard");
    } catch (e) { toast("فشلت الاستعادة ولم تتغير البيانات: " + failMsg(e), "bad", 9000); }
}

/* =========================================================
   المزامنة عبر Firebase (مثل نظام Work_1)
   ---------------------------------------------------------
   - تسجيل الدخول للنظام محلي (admin …) ويعمل على أي عدد من الأجهزة معاً.
   - لمشاركة البيانات بين الجوالات: دخول Google مرة واحدة على كل جهاز،
     وقواعد Firestore تسمح فقط للبريد المسموح (انظر README.md).
   - كل سجل وثيقة: workspaces/{ws}/records/{id} = {id, json, updatedAt, deleted, syncedAt}
   - الحسم: الأحدث updatedAt يفوز. البيانات المحلية تعمل دون إنترنت وتُرفع عند عودة الاتصال.
   ========================================================= */
const FB_DEFAULT_CONFIG = {
    apiKey: "AIzaSyD4EqYkOr359yOQ_VDbFadUD_Q6-jT88BM",
    authDomain: "work-1-c2965.firebaseapp.com",
    projectId: "work-1-c2965",
    storageBucket: "work-1-c2965.firebasestorage.app",
    messagingSenderId: "859204415987",
    appId: "1:859204415987:web:2bfccb5c1d56a3eccc6286"
};
const FB_SDK = "https://www.gstatic.com/firebasejs/10.12.2/";
const fb = { backend: null, loading: null, user: null, unsub: null, offUser: null, status: "off", error: "", pushTimer: null, pushing: false, pushAgain: false };

function fbConfig() {
    if (syncMeta.config) { try { return JSON.parse(syncMeta.config); } catch (e) { /* الافتراضي */ } }
    return FB_DEFAULT_CONFIG;
}
function fbWs() { return syncMeta.workspace || "exchange"; }
function fbDocId(id) { return String(id).replace(/\//g, "_"); }
function fbErrText(e) {
    const code = (e && (e.code || e.message)) || String(e);
    if (/permission-denied|insufficient permissions/i.test(code)) return "حساب Google هذا غير مسموح في قواعد Firestore. أضف بريده إلى القواعد (README.md).";
    if (/unauthorized-domain/.test(code)) return "نطاق الموقع غير مضاف إلى Authorized domains في إعدادات Firebase Authentication.";
    if (/network|unavailable|offline/i.test(code)) return "لا يوجد اتصال بالإنترنت — سيُرفع العمل عند عودة الاتصال.";
    if (/popup-closed|cancelled-popup/.test(code)) return "أُغلقت نافذة الدخول.";
    return "خطأ في المزامنة: " + code;
}
function fbLoadBackend() {
    if (fb.backend) return Promise.resolve(fb.backend);
    if (window.__SX_FB_TEST) { fb.backend = window.__SX_FB_TEST; return Promise.resolve(fb.backend); }
    if (fb.loading) return fb.loading;
    fb.loading = Promise.all([
        import(FB_SDK + "firebase-app.js"), import(FB_SDK + "firebase-auth.js"), import(FB_SDK + "firebase-firestore.js")
    ]).then(function (mods) {
        const appMod = mods[0], authMod = mods[1], fs = mods[2];
        const app = appMod.initializeApp(fbConfig(), "smart-exchange");
        const auth = authMod.getAuth(app);
        const db = fs.getFirestore(app);
        const col = function (ws) { return fs.collection(db, "workspaces", ws, "records"); };
        fb.backend = {
            onUser: function (cb) {
                authMod.getRedirectResult(auth).catch(function () {});
                return authMod.onAuthStateChanged(auth, function (u) { cb(u ? { uid: u.uid, email: u.email } : null); });
            },
            signIn: function () {
                const provider = new authMod.GoogleAuthProvider();
                provider.setCustomParameters({ prompt: "select_account" });
                return authMod.signInWithPopup(auth, provider).catch(function (error) {
                    if (error && /popup-blocked|operation-not-supported|popup-closed-by-browser/.test(error.code || "")) return authMod.signInWithRedirect(auth, provider);
                    throw error;
                });
            },
            signOut: function () { return authMod.signOut(auth); },
            listen: function (ws, since, onDocs, onError) {
                const q = since ? fs.query(col(ws), fs.where("syncedAt", ">", fs.Timestamp.fromMillis(since))) : col(ws);
                return fs.onSnapshot(q, function (snap) {
                    const docs = [];
                    snap.docChanges().forEach(function (ch) {
                        if (ch.type === "removed") return;
                        const v = ch.doc.data();
                        if (!v.syncedAt) return; /* كتابة محلية لم تؤكَّد بعد */
                        docs.push({ id: v.id, json: v.json || "", updatedAt: v.updatedAt || "", syncedAt: v.syncedAt.toMillis() });
                    });
                    if (docs.length) onDocs(docs);
                }, onError);
            },
            push: function (ws, list) {
                const chunks = [];
                for (let i = 0; i < list.length; i += 300) chunks.push(list.slice(i, i + 300));
                return chunks.reduce(function (p, chunk) {
                    return p.then(function () {
                        const batch = fs.writeBatch(db);
                        chunk.forEach(function (r) {
                            batch.set(fs.doc(db, "workspaces", ws, "records", fbDocId(r.id)), { id: r.id, json: r.json, updatedAt: r.updatedAt, deleted: !!r.deleted, syncedAt: fs.serverTimestamp() });
                        });
                        return batch.commit();
                    });
                }, Promise.resolve());
            }
        };
        return fb.backend;
    });
    return fb.loading;
}
async function saveSyncMeta() { await DB.write([], [syncMeta]); }
async function fbStart() {
    if (!syncMeta.enabled) { fb.status = "off"; renderSyncUI(); return; }
    fb.status = "connecting"; renderSyncUI();
    try {
        const b = await fbLoadBackend();
        if (fb.offUser) return;
        fb.offUser = b.onUser(function (user) {
            fb.user = user;
            if (user) { fb.status = "online"; fb.error = ""; fbListen(); fbPushNow(); }
            else { fb.status = "signedout"; fbStopListen(); }
            renderSyncUI();
        });
    } catch (e) {
        fb.loading = null;
        fb.status = "error"; fb.error = "تعذر تحميل Firebase — تحقق من الاتصال بالإنترنت.";
        console.error(e);
        renderSyncUI();
    }
}
function fbStopListen() { if (fb.unsub) { try { fb.unsub(); } catch (e) { /* */ } fb.unsub = null; } }
function fbListen() {
    fbStopListen();
    fb.unsub = fb.backend.listen(fbWs(), syncMeta.lastPull || 0, fbApplyRemote, function (err) {
        fb.status = "error"; fb.error = fbErrText(err); renderSyncUI();
    });
}
async function fbApplyRemote(docs) {
    let applied = 0;
    try {
        await withLock(async function () {
            await reloadRecords();
            const saved = await DB.get("meta", "sync");
            if (saved) syncMeta = saved;
            const changes = [];
            let maxSynced = syncMeta.lastPull || 0;
            docs.forEach(function (d) {
                maxSynced = Math.max(maxSynced, d.syncedAt || 0);
                let rec;
                try { rec = JSON.parse(d.json); } catch (e) { return; }
                if (!rec || typeof rec !== "object" || rec.id !== d.id || typeof rec.type !== "string") return;
                const local = records.get(d.id);
                if (local && String(local.updatedAt || "") >= String(rec.updatedAt || "")) return;
                changes.push(rec);
                delete syncMeta.dirty[d.id];
            });
            syncMeta.lastPull = maxSynced;
            syncMeta.lastSyncAt = Date.now();
            await DB.write(changes, [syncMeta]);
            changes.forEach(function (r) { records.set(r.id, r); });
            applied = changes.length;
        });
    } catch (e) { fb.error = failMsg(e); renderSyncUI(); return; }
    if (applied) {
        recompute();
        if (channel) channel.postMessage({ t: "changed" });
        toast("المزامنة: وصل " + applied + " تغيير من جهاز آخر", "info", 3500);
        renderSoft();
    }
    renderSyncUI();
}
function fbSchedulePush() {
    if (!syncMeta.enabled) return;
    clearTimeout(fb.pushTimer);
    fb.pushTimer = setTimeout(fbPushNow, 1200);
    renderSyncUI();
}
async function fbPushNow() {
    if (!syncMeta.enabled || !fb.user || !fb.backend) return;
    if (fb.pushing) { fb.pushAgain = true; return; }
    const ids = Object.keys(syncMeta.dirty || {});
    if (!ids.length) return;
    fb.pushing = true; renderSyncUI();
    try {
        const list = ids.map(function (id) { return records.get(id); }).filter(Boolean).map(function (r) {
            return { id: r.id, json: JSON.stringify(r), updatedAt: r.updatedAt || "", deleted: !!r.deleted };
        });
        await fb.backend.push(fbWs(), list);
        const saved = await DB.get("meta", "sync");
        if (saved) syncMeta = saved;
        list.forEach(function (x) { const cur = records.get(x.id); if (!cur || (cur.updatedAt || "") === x.updatedAt) delete syncMeta.dirty[x.id]; });
        syncMeta.lastSyncAt = Date.now();
        await saveSyncMeta();
        fb.error = "";
    } catch (e) { fb.error = fbErrText(e); console.warn("push", e); }
    finally {
        fb.pushing = false; renderSyncUI();
        if (fb.pushAgain) { fb.pushAgain = false; fbSchedulePush(); }
    }
}
async function syncAction(act) {
    if (act === "enable") {
        if (!can("settings") && hasOpening()) { toast("تفعيل المزامنة للمدير", "bad"); return; }
        const ws = ($("#fbWs") && $("#fbWs").value.trim()) || fbWs();
        if (!/^[A-Za-z0-9_-]{2,40}$/.test(ws)) { toast("اسم المساحة المشتركة: أحرف إنجليزية وأرقام فقط", "bad"); return; }
        syncMeta.enabled = true; syncMeta.workspace = ws; syncMeta.lastPull = 0; syncMeta.dirty = {};
        /* أول مزامنة: رفع كل السجلات المحلية (الإعدادات الافتراضية غير المعدلة لا تُرفع) */
        records.forEach(function (r) { if (r.updatedAt) syncMeta.dirty[r.id] = true; });
        await saveSyncMeta();
        await fbStart();
        if (fb.backend && !fb.user) syncAction("signin");
        render();
    }
    if (act === "signin") {
        try { await fbStart(); const b = await fbLoadBackend(); await b.signIn(); }
        catch (e) { toast(fbErrText(e), "bad", 8000); }
    }
    if (act === "signout") {
        if (!(await confirmBox("الخروج من حساب Google", "تبقى بياناتك على هذا الجهاز وعلى Firebase. يتوقف هذا الجهاز عن المزامنة حتى تدخل مجدداً.", "خروج"))) return;
        try { await fb.backend.signOut(); } catch (e) { /* */ }
    }
    if (act === "now") {
        if (!fb.user) { toast("سجّل الدخول بحساب Google أولاً", "info"); return; }
        fbListen(); await fbPushNow(); toast("تمت المزامنة", "ok");
    }
    if (act === "disable") {
        if (!(await confirmBox("إيقاف المزامنة على هذا الجهاز", "تبقى البيانات على هذا الجهاز ولا يُحذف شيء من Firebase.", "إيقاف"))) return;
        fbStopListen(); syncMeta.enabled = false; await saveSyncMeta(); fb.status = "off"; render();
    }
    if (act === "config") {
        const v = await promptBox("إعدادات Firebase مخصصة", '<p class="small muted">الصق كائن firebaseConfig لمشروعك (JSON). اتركه فارغاً للعودة إلى المشروع الافتراضي. يلزم إعادة تحميل الصفحة.</p>', "firebaseConfig", { value: syncMeta.config || "", required: false });
        if (v === null) return;
        if (v) { try { const o = JSON.parse(v); if (!o.apiKey || !o.projectId) throw new Error(); } catch (e) { toast("JSON غير صالح أو ينقصه apiKey/projectId", "bad"); return; } }
        syncMeta.config = v; await saveSyncMeta(); location.reload();
    }
}
function renderSyncBadge() {
    const el = $("#syncBadge");
    if (!el) return;
    const pending = Object.keys(syncMeta.dirty || {}).length;
    let cls2 = "badge", txt = "دون مزامنة (هذا الجهاز فقط)";
    if (syncMeta.enabled) {
        if (fb.status === "online" && !fb.error) { cls2 = pending ? "badge warn" : "badge ok"; txt = pending ? "بانتظار الرفع (" + pending + ")" : "متزامن ✓"; }
        else if (fb.status === "signedout") { cls2 = "badge warn"; txt = "المزامنة: سجّل دخول Google"; }
        else if (fb.status === "connecting") { cls2 = "badge info"; txt = "جارٍ الاتصال…"; }
        else { cls2 = "badge bad"; txt = navigator.onLine === false ? "دون اتصال — يُحفظ محلياً" : "خطأ في المزامنة"; }
    }
    el.className = cls2.replace("badge", "side-pill");
    $("span", el).textContent = txt;
    el.title = fb.error || "";
}
function renderSyncPanel() {
    const box = $("#syncPanel");
    if (!box) return;
    const pending = Object.keys(syncMeta.dirty || {}).length;
    const isMgr = can("settings") || !hasOpening();
    if (!syncMeta.enabled) {
        box.innerHTML = '<p>لاستخدام النظام من أكثر من جوال على البيانات نفسها: فعّل المزامنة وسجّل الدخول بحساب Google مسموح (مثل نظام Work_1). تسجيل الدخول للنظام (admin) يبقى كما هو ويعمل على كل الأجهزة في الوقت نفسه.</p>' +
            '<label class="field" style="max-width:320px"><span>اسم المساحة المشتركة (نفسه على كل الأجهزة)</span><input id="fbWs" class="ltr" value="' + esc(fbWs()) + '"></label>' +
            '<div class="row" style="margin-top:10px"><button class="btn primary" data-sync="enable"' + (isMgr ? "" : " disabled") + '>تفعيل المزامنة والدخول بحساب Google</button><button class="btn" data-sync="config"' + (isMgr ? "" : " disabled") + ">مشروع Firebase مخصص</button></div>" +
            '<p class="muted small">المشروع الافتراضي: ' + esc(fbConfig().projectId) + " — المساحة: " + esc(fbWs()) + ". يجب أن يكون بريد Google مضافاً إلى قواعد Firestore.</p>";
    } else {
        box.innerHTML = '<div class="kv"><div>الحالة</div><div>' + esc({ online: "متصل", signedout: "بانتظار دخول Google", connecting: "جارٍ الاتصال", error: "خطأ", off: "متوقف" }[fb.status] || fb.status) + "</div>" +
            "<div>حساب Google</div><div class=\"ltr\">" + esc(fb.user ? fb.user.email || fb.user.uid : "—") + "</div>" +
            "<div>المشروع / المساحة</div><div class=\"ltr\">" + esc(fbConfig().projectId + " / " + fbWs()) + "</div>" +
            "<div>بانتظار الرفع</div><div>" + pending + " سجل</div>" +
            "<div>آخر مزامنة</div><div>" + (syncMeta.lastSyncAt ? esc(fmtDateTime(new Date(syncMeta.lastSyncAt).toISOString())) : "—") + "</div></div>" +
            (fb.error ? '<div class="alert bad" style="margin-top:10px">' + esc(fb.error) + "</div>" : "") +
            '<div class="row" style="margin-top:10px">' + (fb.user ? '<button class="btn primary" data-sync="now">مزامنة الآن</button><button class="btn" data-sync="signout">خروج من Google</button>' : '<button class="btn primary" data-sync="signin">الدخول بحساب Google</button>') +
            '<button class="btn" data-sync="disable"' + (isMgr ? "" : " disabled") + ">إيقاف المزامنة على هذا الجهاز</button></div>" +
            '<p class="muted small">عند العمل من جوالين في الوقت نفسه تصل التغييرات خلال ثوانٍ. دون إنترنت يُحفظ العمل محلياً ويُرفع عند عودة الاتصال. إن تعارض تعديل السجل نفسه من جهازين يبقى الأحدث.</p>';
    }
}
function renderSyncUI() { renderSyncBadge(); renderSyncPanel(); }

/* =========================================================
   التشغيل
   ========================================================= */
function enterApp() {
    $("#boot").classList.add("hidden");
    $("#loginScreen").classList.add("hidden");
    $("#app").classList.remove("hidden");
    go(lsGet("sx_section") || "dashboard");
    let asked = false;
    try { asked = sessionStorage.getItem("sx_pw_asked") === me.id; sessionStorage.setItem("sx_pw_asked", me.id); } catch (e) { /* */ }
    if (me.mustChange && !asked) setTimeout(function () { changePassword(me.id); }, 400);
    autoCloseTick();
}
/* الشعار الموحّد: مربع حبر وسهما التبديل وعملة ذهبية «تمّ» */
function sealSVG(extraClass) {
    return '<svg class="seal brand-mark' + (extraClass ? " " + extraClass : "") + '" viewBox="0 0 64 64" aria-hidden="true">' +
        '<rect class="bm-tile" x="2" y="2" width="60" height="60" rx="17"/>' +
        '<g class="bm-arrows" fill="none" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">' +
        '<path d="M15 25 H43"/><path d="M36 18 L43 25 L36 32"/>' +
        '<path d="M43 38 H21"/><path d="M28 31 L21 38 L28 45"/></g>' +
        '<circle class="bm-badge" cx="47" cy="46.5" r="10.5"/>' +
        '<path class="bm-check" d="M42.3 46.6 L45.6 49.9 L51.8 43.4" fill="none" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>' +
        "</svg>";
}
function anchorSeals() {
    $$("svg.seal[data-seal]").forEach(function (old) {
        const extra = Array.from(old.classList).filter(function (c) { return c !== "seal"; }).join(" ");
        const holder = document.createElement("div");
        holder.innerHTML = sealSVG(extra);
        old.replaceWith(holder.firstChild);
    });
}
anchorSeals();

async function init() {
    if (!E || !window.Decimal) { $("#bootMsg").textContent = "تعذر تحميل ملفات النظام (engine.js / decimal.js)."; return; }
    try {
        await DB.open();
        await reloadRecords();
        const sm = await DB.get("meta", "sync");
        if (sm) syncMeta = Object.assign(syncMeta, sm);
        if (!syncMeta.dirty) syncMeta.dirty = {};
    } catch (e) {
        $("#bootMsg").innerHTML = '<span class="neg">تعذر فتح قاعدة البيانات المحلية: ' + esc(e.message || e) + "</span><br>لم يُحذف أي شيء. جرّب متصفحاً آخر أو أغلق التبويبات الأخرى.";
        return;
    }
    try { recompute(); }
    catch (e) { console.error(e); $("#bootMsg").textContent = "خطأ في قراءة البيانات: " + e.message; return; }
    if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(function () {});

    $("#loginForm").addEventListener("submit", function (e) {
        e.preventDefault();
        if (login($("#loginUser").value, $("#loginPass").value)) { $("#loginPass").value = ""; enterApp(); }
        else $("#loginError").textContent = "اسم المستخدم أو كلمة المرور غير صحيحة.";
    });
    $("#logoutBtn").addEventListener("click", logout);
    $("#themeBtn").addEventListener("click", toggleTheme);
    $("#themeBtnMobile").addEventListener("click", toggleTheme);
    $("#menuBtn").addEventListener("click", function () { document.body.classList.add("nav-open"); });
    $("#menuClose").addEventListener("click", function () { document.body.classList.remove("nav-open"); });
    $("#navShade").addEventListener("click", function () { document.body.classList.remove("nav-open"); });
    $$("#sidebar [data-sec]").forEach(function (b) { b.addEventListener("click", function () { go(b.dataset.sec); }); });

    if (channel) channel.onmessage = async function (ev) {
        if (!ev.data || ev.data.t !== "changed") return;
        try {
            await reloadRecords();
            const sm = await DB.get("meta", "sync");
            if (sm) syncMeta = sm;
            recompute();
            renderSoft();
        } catch (e) { console.warn(e); }
    };
    window.addEventListener("online", function () { fbPushNow(); renderSyncUI(); });
    window.addEventListener("offline", renderSyncUI);
    document.addEventListener("visibilitychange", function () { if (!document.hidden) { autoCloseTick(); fbPushNow(); } });

    setInterval(function () {
        const c = $("#exClock");
        if (c && ui.ex && !ui.ex.editId) c.value = fmtTime(nowISO());
    }, 1000);
    let lastDay = clockDay();
    setInterval(function () {
        if (!me) return;
        const d = clockDay();
        if (d !== lastDay) { lastDay = d; renderTop(); }
        autoCloseTick();
    }, 30000);

    me = findSessionUser();
    if (me) enterApp(); else showLogin();
    fbStart();

    if ("serviceWorker" in navigator && (location.protocol === "https:" || location.hostname === "localhost" || location.hostname === "127.0.0.1")) {
        navigator.serviceWorker.register("sw.js").catch(function () {});
    }
}
init();
