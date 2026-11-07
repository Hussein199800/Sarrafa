/* =========================================================
   محرك المحاسبة — نظام الصرافة الذكي
   ---------------------------------------------------------
   منطق نقي بلا واجهة ولا تخزين: يعمل في المتصفح وفي Node (للاختبارات).

   المبدأ: كل الأرصدة والتكاليف والأرباح «مشتقة» من إعادة تشغيل دفتر
   الحركات بالترتيب الزمني (opening ← exchange / cash / withdrawal / count).
   لذلك تعديل عملية قديمة أو حذفها يعيد احتساب كل ما بعدها تلقائياً،
   ولا يمكن أن تنشأ قيمة مالية أو تختفي دون حركة مسجلة.

   عملة القياس المحاسبي: الدولار (settings.base = "USD").
   لكل عملة: الكمية qty والقيمة الدفترية book (بالدولار) ← متوسط التكلفة = book / qty.
   ========================================================= */
(function (root, factory) {
    if (typeof module === "object" && module.exports) module.exports = factory(require("./vendor/decimal.js"));
    else root.SXEngine = factory(root.Decimal);
})(typeof self !== "undefined" ? self : this, function (DecimalLib) {
    "use strict";

    const D = DecimalLib.clone({ precision: 40, rounding: DecimalLib.ROUND_HALF_UP, toExpNeg: -30, toExpPos: 40 });
    const ZERO = new D(0);
    const ONE = new D(1);

    const ROUNDING = {
        "half-up": DecimalLib.ROUND_HALF_UP,
        "half-even": DecimalLib.ROUND_HALF_EVEN,
        "down": DecimalLib.ROUND_DOWN,
        "up": DecimalLib.ROUND_UP
    };

    /* ---------- الأرقام ---------- */

    /* تحويل نص المستخدم إلى رقم دقيق؛ null إن كان فارغاً أو غير صالح */
    function parse(v) {
        if (v instanceof D) return v;
        if (v === null || v === undefined) return null;
        let s = String(v).trim();
        if (!s) return null;
        /* الأرقام العربية والفارسية والفواصل */
        s = s.replace(/[٠-٩]/g, function (d) { return String(d.charCodeAt(0) - 0x0660); })
            .replace(/[۰-۹]/g, function (d) { return String(d.charCodeAt(0) - 0x06F0); })
            .replace(/٫/g, ".").replace(/[٬,\s]/g, "");
        if (!/^[-+]?(\d+\.?\d*|\.\d+)(e[-+]?\d+)?$/i.test(s)) return null;
        try { return new D(s); } catch (e) { return null; }
    }
    function dec(v) { const p = parse(v); return p === null ? ZERO : p; }
    function str(d) { return d === null || d === undefined ? "" : (d instanceof D ? d : dec(d)).toFixed(); }

    function roundMode(settings) { return ROUNDING[(settings && settings.rounding) || "half-up"] || DecimalLib.ROUND_HALF_UP; }
    function decimalsOf(settings, code) {
        const c = curInfo(settings, code);
        return c && Number.isInteger(c.decimals) ? c.decimals : 2;
    }
    function roundAmount(settings, code, x) {
        return dec(x).toDecimalPlaces(decimalsOf(settings, code), roundMode(settings));
    }

    /* ---------- العملات والأزواج ---------- */

    function curInfo(settings, code) {
        return (settings.currencies || []).find(function (c) { return c.code === code; }) || null;
    }
    function enabledCurrencies(settings) {
        return (settings.currencies || []).filter(function (c) { return c.enabled; });
    }
    /* الزوج دائماً «العملة الأقوى / الأضعف»: 1 أساس = r مقابل */
    function pairOf(settings, a, b) {
        const A = curInfo(settings, a) || { strength: 0 }, B = curInfo(settings, b) || { strength: 0 };
        const aBase = A.strength > B.strength || (A.strength === B.strength && a < b);
        const base = aBase ? a : b, quote = aBase ? b : a;
        return { base: base, quote: quote, key: base + "/" + quote };
    }
    function allPairs(settings) {
        const list = enabledCurrencies(settings), out = [];
        for (let i = 0; i < list.length; i++) for (let j = i + 1; j < list.length; j++) {
            out.push(pairOf(settings, list[i].code, list[j].code));
        }
        return out;
    }
    /* السعر الافتراضي للعملية: إن استلم الصراف الأساس فهو «يشتري» ← سعر الشراء، وإلا «يبيع» ← سعر البيع */
    function defaultRate(settings, inCur, outCur) {
        const p = pairOf(settings, inCur, outCur);
        const r = (settings.rates || {})[p.key];
        if (!r) return null;
        return parse(inCur === p.base ? r.buy : r.sell);
    }
    function computeOut(settings, inCur, outCur, inAmt, rate) {
        const p = pairOf(settings, inCur, outCur);
        const a = parse(inAmt), r = parse(rate);
        if (!a || !r || r.lte(0)) return null;
        return inCur === p.base ? a.times(r) : a.div(r);
    }
    function rateFromAmounts(settings, inCur, outCur, inAmt, outAmt) {
        const p = pairOf(settings, inCur, outCur);
        const a = parse(inAmt), b = parse(outAmt);
        if (!a || !b || a.lte(0) || b.lte(0)) return null;
        return inCur === p.base ? b.div(a) : a.div(b);
    }

    /* أسعار التقييم المرجعية: كم وحدة من العملة تساوي دولاراً واحداً.
       الافتراضي: منتصف سعري الشراء والبيع لزوج العملة مع الدولار؛ ويمكن تجاوزه من الإعدادات. */
    function refRates(settings) {
        const out = {}, base = settings.base || "USD";
        (settings.currencies || []).forEach(function (c) {
            if (c.code === base) { out[c.code] = ONE; return; }
            const o = parse((settings.refOverride || {})[c.code]);
            if (o && o.gt(0)) { out[c.code] = o; return; }
            const p = pairOf(settings, base, c.code);
            const r = (settings.rates || {})[p.key];
            const buy = r && parse(r.buy), sell = r && parse(r.sell);
            if (!buy || !sell || buy.lte(0) || sell.lte(0)) { out[c.code] = null; return; }
            const mid = buy.plus(sell).div(2);
            out[c.code] = p.base === base ? mid : ONE.div(mid);
        });
        return out;
    }
    function refRatesToStrings(refs) {
        const o = {};
        Object.keys(refs).forEach(function (k) { if (refs[k]) o[k] = refs[k].toFixed(); });
        return o;
    }

    /* ---------- اليوم المحاسبي ---------- */

    const dtfCache = {};
    function partsIn(ms, tz) {
        const key = tz || "UTC";
        if (!dtfCache[key]) {
            dtfCache[key] = new Intl.DateTimeFormat("en-CA", {
                timeZone: key, year: "numeric", month: "2-digit", day: "2-digit",
                hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23"
            });
        }
        const o = {};
        dtfCache[key].formatToParts(new Date(ms)).forEach(function (p) { o[p.type] = p.value; });
        return o;
    }
    /* اليوم المحاسبي يبدأ عند الساعة startHour بتوقيت المنطقة tz */
    function dayOf(ms, tz, startHour) {
        const p = partsIn(ms - (Number(startHour) || 0) * 3600000, tz);
        return p.year + "-" + p.month + "-" + p.day;
    }
    function clockOf(ms, tz) {
        const p = partsIn(ms, tz);
        return { date: p.year + "-" + p.month + "-" + p.day, time: p.hour + ":" + p.minute + ":" + p.second };
    }
    function addDays(day, n) {
        const d = new Date(day + "T00:00:00Z");
        d.setUTCDate(d.getUTCDate() + n);
        return d.toISOString().slice(0, 10);
    }

    /* ---------- إعادة تشغيل الدفتر ---------- */

    const LEDGER_TYPES = { opening: 1, exchange: 1, cash: 1, withdrawal: 1, count: 1 };

    function sortLedger(a, b) {
        const ao = a.type === "opening" ? 0 : 1, bo = b.type === "opening" ? 0 : 1;
        if (ao !== bo) return ao - bo;
        if ((a.day || "") !== (b.day || "")) return (a.day || "") < (b.day || "") ? -1 : 1;
        if ((a.at || "") !== (b.at || "")) return (a.at || "") < (b.at || "") ? -1 : 1;
        if ((a.createdAt || "") !== (b.createdAt || "")) return (a.createdAt || "") < (b.createdAt || "") ? -1 : 1;
        return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
    }

    function emptyDay(day) {
        return {
            day: day, count: 0, profit: ZERO, loss: ZERO, net: ZERO, valuation: ZERO,
            inventory: ZERO, withdrawals: ZERO, withdrawalCount: 0, capitalIn: ZERO, capitalOut: ZERO,
            cashCount: 0, pairs: {}, curCount: {}, sypEquivalent: ZERO
        };
    }

    function opRefs(record, fallback) {
        const out = {};
        Object.keys(fallback).forEach(function (k) { out[k] = fallback[k]; });
        const r = record.refRates || {};
        Object.keys(r).forEach(function (k) { const v = parse(r[k]); if (v && v.gt(0)) out[k] = v; });
        return out;
    }

    function replay(records, settings) {
        const base = settings.base || "USD", display = settings.display || "SYP";
        const currentRefs = refRates(settings);
        const curs = {};
        function ensure(code) {
            if (!curs[code]) curs[code] = { qty: ZERO, book: ZERO, lastAvg: null, lastAt: null, lastMove: null };
            return curs[code];
        }
        (settings.currencies || []).forEach(function (c) { ensure(c.code); });

        const results = {}, days = {}, dayEnd = {}, moves = [], negatives = [], warnings = [];
        let currentDay = null;

        function unitOf(code, refs) {
            if (code === base) return ONE;
            const c = ensure(code);
            if (c.qty.gt(0)) return c.book.div(c.qty);
            if (c.lastAvg) return c.lastAvg;
            const r = refs[code];
            return r && r.gt(0) ? ONE.div(r) : ZERO;
        }
        function snapshot() {
            const s = {};
            Object.keys(curs).forEach(function (k) { s[k] = { qty: curs[k].qty, book: curs[k].book }; });
            return s;
        }
        function agg(day) { return days[day] || (days[day] = emptyDay(day)); }
        function move(r, code, before, delta, kind, reason, bookDelta) {
            const c = ensure(code);
            c.lastAt = r.at; c.lastMove = kind;
            moves.push({
                id: r.id, type: r.type, kind: kind, day: r.day, at: r.at, cur: code,
                before: before, delta: delta, after: c.qty, reason: reason || "", bookDelta: bookDelta, no: r.no || ""
            });
        }
        /* إخراج كمية بتكلفتها الدفترية (المتوسط المرجح) */
        function removeQty(r, code, q, refs) {
            const c = ensure(code);
            let cost;
            if (code === base) cost = q;
            else if (c.qty.gt(0) && q.eq(c.qty)) cost = c.book;
            else cost = q.times(unitOf(code, refs));
            if (c.qty.gt(0)) c.lastAvg = c.book.div(c.qty);
            c.qty = c.qty.minus(q);
            c.book = c.book.minus(cost);
            if (c.qty.isZero()) c.book = ZERO;
            if (c.qty.lt(0)) negatives.push({ id: r.id, no: r.no || "", day: r.day, cur: code, qty: c.qty });
            return cost;
        }
        function addQty(code, q, valueUSD) {
            const c = ensure(code);
            c.qty = c.qty.plus(q);
            c.book = c.book.plus(valueUSD);
            if (code === base) c.book = c.qty;
            if (c.qty.gt(0)) c.lastAvg = c.book.div(c.qty);
            if (c.qty.isZero()) c.book = ZERO;
        }

        const ledger = records.filter(function (r) { return r && !r.deleted && LEDGER_TYPES[r.type]; }).sort(sortLedger);

        ledger.forEach(function (r) {
            if (r.type !== "opening" && r.day !== currentDay) {
                if (currentDay) dayEnd[currentDay] = snapshot();
                currentDay = r.day;
            }
            const refs = opRefs(r, currentRefs);

            if (r.type === "opening") {
                Object.keys(r.balances || {}).forEach(function (code) {
                    const b = r.balances[code];
                    const q = dec(b.qty);
                    if (q.isZero()) return;
                    const unit = code === base ? ONE : dec(b.unitCost);
                    const before = ensure(code).qty;
                    addQty(code, q, q.times(unit));
                    move(r, code, before, q, "opening", "الرصيد الافتتاحي", q.times(unit));
                });
                return;
            }

            const a = agg(r.day);

            if (r.type === "exchange") {
                const inA = dec(r.inAmt), outA = dec(r.outAmt);
                const unitOut = unitOf(r.outCur, refs);
                const outBefore = ensure(r.outCur).qty, inBefore = ensure(r.inCur).qty;
                const bookCost = removeQty(r, r.outCur, outA, refs);
                const manual = r.costMethod === "manual" ? parse(r.manualUnitCost) : null;
                const effCost = manual && manual.gte(0) ? (r.outCur === base ? outA : outA.times(manual)) : bookCost;

                let valueIn, refMissing = false;
                if (r.inCur === base) valueIn = inA;
                else if ((settings.valuation || "reference") === "cost") valueIn = effCost;
                else if (refs[r.inCur] && refs[r.inCur].gt(0)) valueIn = inA.div(refs[r.inCur]);
                else { valueIn = effCost; refMissing = true; }
                if (refMissing) warnings.push({ id: r.id, no: r.no, message: "لا يوجد سعر تقييم مرجعي للعملة " + r.inCur + "؛ احتُسب الربح صفراً" });

                addQty(r.inCur, inA, valueIn);
                move(r, r.outCur, outBefore, outA.neg(), "exchange-out", "صرافة: تسليم", bookCost.neg());
                move(r, r.inCur, inBefore, inA, "exchange-in", "صرافة: استلام", valueIn);

                const profit = valueIn.minus(effCost);
                const valuation = effCost.minus(bookCost);
                const refIn = r.inCur === base ? ONE : refs[r.inCur];
                const refOut = r.outCur === base ? ONE : refs[r.outCur];
                const margin = refIn && refOut ? inA.div(refIn).minus(outA.div(refOut)) : null;
                const sypRate = refs[display] || null;

                results[r.id] = {
                    profit: profit, valuation: valuation, bookCost: bookCost, effCost: effCost, valueIn: valueIn,
                    unitCostOut: unitOut, manualUnitCost: manual, margin: margin, sypRate: sypRate,
                    profitDisplay: sypRate ? profit.times(sypRate) : null,
                    outAfter: ensure(r.outCur).qty, inAfter: ensure(r.inCur).qty
                };

                a.count++;
                if (profit.gt(0)) a.profit = a.profit.plus(profit);
                else if (profit.lt(0)) a.loss = a.loss.plus(profit.neg());
                a.net = a.net.plus(profit);
                a.valuation = a.valuation.plus(valuation);
                if (sypRate) a.sypEquivalent = a.sypEquivalent.plus(profit.times(sypRate));
                const pk = pairOf(settings, r.inCur, r.outCur).key;
                a.pairs[pk] = (a.pairs[pk] || 0) + 1;
                a.curCount[r.inCur] = (a.curCount[r.inCur] || 0) + 1;
                a.curCount[r.outCur] = (a.curCount[r.outCur] || 0) + 1;
                return;
            }

            if (r.type === "cash") {
                const code = r.cur, c = ensure(code), before = c.qty;
                let delta;
                if (r.kind === "zero") delta = c.qty.neg();
                else if (r.kind === "replace") delta = dec(r.target).minus(c.qty);
                else if (r.kind === "withdraw") delta = dec(r.amount).neg();
                else delta = dec(r.amount);
                let bookDelta = ZERO;
                if (delta.gt(0)) {
                    const given = parse(r.unitCost);
                    const unit = code === base ? ONE : (given && given.gt(0) ? given : unitOf(code, refs));
                    bookDelta = delta.times(unit);
                    addQty(code, delta, bookDelta);
                    a.capitalIn = a.capitalIn.plus(bookDelta);
                } else if (delta.lt(0)) {
                    bookDelta = removeQty(r, code, delta.neg(), refs).neg();
                    a.capitalOut = a.capitalOut.plus(bookDelta.neg());
                }
                a.cashCount++;
                move(r, code, before, delta, "cash-" + (r.kind || "add"), r.reason, bookDelta);
                results[r.id] = { delta: delta, bookDelta: bookDelta, before: before, after: c.qty };
                return;
            }

            if (r.type === "withdrawal") {
                const code = r.cur, c = ensure(code), before = c.qty;
                const q = dec(r.amount);
                const bookCost = removeQty(r, code, q, refs);
                const usd = parse(r.usdValue) || (code === base ? q : (parse(r.rate) && parse(r.rate).gt(0) ? q.div(parse(r.rate)) : bookCost));
                a.withdrawals = a.withdrawals.plus(usd);
                a.withdrawalCount++;
                move(r, code, before, q.neg(), "profit-withdrawal", r.note || "سحب أرباح", bookCost.neg());
                results[r.id] = { usd: usd, bookCost: bookCost, before: before, after: c.qty };
                return;
            }

            if (r.type === "count") {
                const res = { lines: {}, bookEffect: ZERO, refEffect: ZERO };
                (r.lines || []).forEach(function (l) {
                    const diff = dec(l.diff);
                    const ref = l.cur === base ? ONE : refs[l.cur];
                    const atRef = ref && ref.gt(0) ? diff.div(ref) : ZERO;
                    let bookDelta = ZERO;
                    if (r.settled && !diff.isZero()) {
                        const before = ensure(l.cur).qty;
                        if (diff.gt(0)) {
                            bookDelta = diff.times(unitOf(l.cur, refs));
                            addQty(l.cur, diff, bookDelta);
                        } else {
                            bookDelta = removeQty(r, l.cur, diff.neg(), refs).neg();
                        }
                        move(r, l.cur, before, diff, "count-settlement", r.reason || "تسوية جرد", bookDelta);
                    }
                    res.lines[l.cur] = { diff: diff, bookDelta: bookDelta, atRef: atRef };
                    res.bookEffect = res.bookEffect.plus(bookDelta);
                    res.refEffect = res.refEffect.plus(atRef);
                });
                if (r.settled) a.inventory = a.inventory.plus(res.bookEffect);
                results[r.id] = res;
            }
        });
        if (currentDay) dayEnd[currentDay] = snapshot();

        const openingSnap = {};
        return {
            curs: curs, results: results, days: days, dayEnd: dayEnd, moves: moves,
            negatives: negatives, warnings: warnings, refs: currentRefs, openingSnap: openingSnap
        };
    }

    /* أرصدة نهاية يوم معين (أو آخر يوم قبله فيه حركة) */
    function balancesAt(state, day) {
        if (state.dayEnd[day]) return state.dayEnd[day];
        const keys = Object.keys(state.dayEnd).filter(function (k) { return k <= day; }).sort();
        return keys.length ? state.dayEnd[keys[keys.length - 1]] : null;
    }

    /* ملخص إقفال يوم: يُحفظ كما هو في سجل الإقفال */
    function closeSnapshot(state, day, settings) {
        const a = state.days[day] || emptyDay(day);
        const display = settings.display || "SYP";
        const rate = state.refs[display];
        const bal = balancesAt(state, day) || {};
        const balances = {};
        Object.keys(bal).forEach(function (k) {
            const q = bal[k].qty, b = bal[k].book;
            balances[k] = { qty: q.toFixed(), book: b.toFixed(), avg: q.gt(0) ? b.div(q).toFixed() : "" };
        });
        return {
            day: day, count: a.count,
            profit: a.profit.toFixed(), loss: a.loss.toFixed(), net: a.net.toFixed(),
            valuation: a.valuation.toFixed(), inventory: a.inventory.toFixed(),
            withdrawals: a.withdrawals.toFixed(),
            displayRate: rate ? rate.toFixed() : "",
            netDisplay: rate ? a.net.times(rate).toFixed() : "",
            balances: balances
        };
    }

    /* هل تغيّرت نتائج يوم مقفل بعد الإقفال (بسبب تعديل يوم سابق مثلاً)؟ */
    function snapshotDiffers(saved, fresh) {
        if (!saved || !fresh) return true;
        const fix = function (v) { return dec(v).toDecimalPlaces(6).toFixed(); };
        if (Number(saved.count) !== Number(fresh.count)) return true;
        const fields = ["profit", "loss", "net", "valuation", "inventory", "withdrawals"];
        for (let i = 0; i < fields.length; i++) if (fix(saved[fields[i]]) !== fix(fresh[fields[i]])) return true;
        const keys = Object.keys(Object.assign({}, saved.balances || {}, fresh.balances || {}));
        for (let i = 0; i < keys.length; i++) {
            const s = (saved.balances || {})[keys[i]] || {}, f = (fresh.balances || {})[keys[i]] || {};
            if (fix(s.qty || 0) !== fix(f.qty || 0)) return true;
        }
        return false;
    }

    /* سالب جديد ظهر بسبب تغيير مقترح؟ (يُرفض الحفظ) */
    function newNegatives(before, after) {
        const seen = {};
        before.negatives.forEach(function (n) { seen[n.id + "|" + n.cur] = true; });
        return after.negatives.filter(function (n) { return !seen[n.id + "|" + n.cur]; });
    }

    /* الأرباح غير المحققة: فرق القيمة بالسعر المرجعي الحالي عن القيمة الدفترية */
    function unrealized(state, settings) {
        const out = { total: ZERO, byCur: {} };
        Object.keys(state.curs).forEach(function (k) {
            const c = state.curs[k];
            const ref = k === (settings.base || "USD") ? ONE : state.refs[k];
            if (!ref || !ref.gt(0)) { out.byCur[k] = null; return; }
            const v = c.qty.div(ref).minus(c.book);
            out.byCur[k] = v;
            out.total = out.total.plus(v);
        });
        return out;
    }

    return {
        Decimal: D, ZERO: ZERO, ONE: ONE,
        parse: parse, dec: dec, str: str, roundAmount: roundAmount, decimalsOf: decimalsOf, roundMode: roundMode,
        curInfo: curInfo, enabledCurrencies: enabledCurrencies, pairOf: pairOf, allPairs: allPairs,
        defaultRate: defaultRate, computeOut: computeOut, rateFromAmounts: rateFromAmounts,
        refRates: refRates, refRatesToStrings: refRatesToStrings,
        dayOf: dayOf, clockOf: clockOf, addDays: addDays,
        replay: replay, sortLedger: sortLedger, balancesAt: balancesAt, closeSnapshot: closeSnapshot,
        snapshotDiffers: snapshotDiffers, newNegatives: newNegatives, unrealized: unrealized, emptyDay: emptyDay
    };
});
