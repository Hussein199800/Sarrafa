/* اختبارات الحسابات المالية الأساسية — بيانات تجريبية فقط.
   التشغيل: node tests/engine.test.js */
"use strict";
const E = require("../engine.js");
const D = E.Decimal;

let failed = 0, passed = 0;
function check(name, ok, detail) {
    if (ok) { passed++; console.log("PASS " + name); }
    else { failed++; console.log("FAIL " + name + (detail !== undefined ? " — " + detail : "")); }
}
function near(a, b, eps) { return new D(a).minus(new D(b)).abs().lte(eps || "0.0000001"); }

function settings(extra) {
    return Object.assign({
        base: "USD", display: "SYP", valuation: "reference", rounding: "half-up",
        currencies: [
            { code: "SYP", name: "الليرة السورية الجديدة", decimals: 0, strength: 1, enabled: true },
            { code: "USD", name: "الدولار", decimals: 2, strength: 3, enabled: true },
            { code: "TRY", name: "الليرة التركية", decimals: 2, strength: 2, enabled: true },
            { code: "EUR", name: "اليورو", decimals: 2, strength: 4, enabled: true }
        ],
        rates: {
            "USD/SYP": { buy: "110", sell: "112" },
            "USD/TRY": { buy: "38", sell: "39" },
            "EUR/USD": { buy: "1.14", sell: "1.16" },
            "TRY/SYP": { buy: "2.8", sell: "2.9" },
            "EUR/SYP": { buy: "125", sell: "130" },
            "EUR/TRY": { buy: "44", sell: "45" }
        },
        refOverride: {}
    }, extra || {});
}
let seq = 0;
function ex(inCur, outCur, inAmt, outAmt, extra) {
    seq++;
    return Object.assign({
        id: "x" + seq, type: "exchange", no: "T-" + seq, day: "2026-11-01",
        at: "2026-11-01T10:" + String(seq).padStart(2, "0") + ":00Z", createdAt: "c" + String(seq).padStart(4, "0"),
        inCur: inCur, outCur: outCur, inAmt: String(inAmt), outAmt: String(outAmt), costMethod: "avg"
    }, extra || {});
}
function opening(bal) {
    return { id: "opening", type: "opening", balances: bal, at: "2026-10-31T00:00:00Z", day: "2026-10-31" };
}
const S = settings();
const open = opening({
    SYP: { qty: "1000000", unitCost: new D(1).div(111).toFixed() },
    USD: { qty: "5000", unitCost: "1" },
    TRY: { qty: "20000", unitCost: "0.025" },
    EUR: { qty: "2000", unitCost: "1.15" }
});

/* --- الأسعار والأزواج --- */
check("اتجاه الزوج USD/SYP", E.pairOf(S, "SYP", "USD").key === "USD/SYP");
check("اتجاه الزوج EUR/USD", E.pairOf(S, "USD", "EUR").key === "EUR/USD");
check("عدد الأزواج لأربع عملات = 6", E.allPairs(S).length === 6);
check("السعر الافتراضي عند استلام الدولار = سعر الشراء", E.defaultRate(S, "USD", "SYP").eq(110));
check("السعر الافتراضي عند تسليم الدولار = سعر البيع", E.defaultRate(S, "SYP", "USD").eq(112));
check("المبلغ الناتج 100$ × 110", E.computeOut(S, "USD", "SYP", "100", "110").eq(11000));
check("المبلغ الناتج 11200 ل.س ÷ 112", E.computeOut(S, "SYP", "USD", "11200", "112").eq(100));
check("السعر الفعلي من مبلغ معدّل 13950/100", E.rateFromAmounts(S, "USD", "SYP", "100", "13950").eq("139.5"));
const refs = E.refRates(S);
check("سعر التقييم المرجعي لليرة = منتصف 110 و112", refs.SYP.eq(111));
check("سعر التقييم المرجعي لليورو = 1 ÷ 1.15", near(refs.EUR, new D(1).div("1.15")));
check("قراءة أرقام عربية وفواصل", E.parse("١٢٬٥٠٠٫٥").eq("12500.5") && E.parse("1,000").eq(1000) && E.parse("abc") === null);
check("دقة عشرية 0.1 + 0.2 = 0.3", E.dec("0.1").plus("0.2").eq("0.3"));
check("تقريب الليرة لعدد صحيح", E.roundAmount(S, "SYP", "13950.5").eq(13951));

/* --- عمليات رابحة وخاسرة --- */
{
    const buy = ex("USD", "SYP", 100, 11000);              /* شراء دولار بـ110 */
    const sell = ex("SYP", "USD", 11200, 100);             /* بيع دولار بـ112 */
    const loss = ex("SYP", "USD", 10900, 100);             /* بيع دولار بـ109 (خاسر) */
    const flat = ex("SYP", "USD", 11100, 100);             /* بيع بسعر المرجع = بلا ربح */
    const st = E.replay([open, buy, sell, loss, flat], S);
    check("ربح شراء دولار مقابل ليرة", near(st.results[buy.id].profit, new D(100).minus(new D(11000).div(111))));
    check("ربح بيع دولار مقابل ليرة", near(st.results[sell.id].profit, new D(11200).div(111).minus(100)));
    check("عملية خاسرة سالبة", st.results[loss.id].profit.lt(0));
    check("عملية دون ربح أو خسارة", near(st.results[flat.id].profit, 0));
    check("رصيد الدولار بعد العمليات", st.curs.USD.qty.eq(5000 + 100 - 300));
    check("رصيد الليرة بعد العمليات", st.curs.SYP.qty.eq(1000000 - 11000 + 11200 + 10900 + 11100));
    const day = st.days["2026-11-01"];
    check("عدد عمليات اليوم = 4", day.count === 4);
    check("صافي اليوم = الأرباح − الخسائر", near(day.net, day.profit.minus(day.loss)));
    check("المقابل بالليرة = الصافي × 111", near(day.sypEquivalent, day.net.times(111), "0.00001"));
    /* توازن: تغير إجمالي القيمة الدفترية = مجموع الأرباح (لا رأس مال ولا تسويات هنا) */
    const bookTotal = Object.keys(st.curs).reduce(function (s, k) { return s.plus(st.curs[k].book); }, E.ZERO);
    const openBook = new D(1000000).div(111).plus(5000).plus(new D(20000).times("0.025")).plus(new D(2000).times("1.15"));
    check("المعادلة متوازنة: الدفتري = الافتتاحي + صافي الأرباح", near(bookTotal, openBook.plus(day.net)));
}

/* --- المتوسط المرجح (مثال الوثيقة بعملة غير الدولار) --- */
{
    const S2 = settings({ valuation: "cost" });
    const st0 = [opening({ USD: { qty: "10000", unitCost: "1" } })];
    /* شراء 1000 يورو بـ1.10 ثم 500 بـ1.12 ثم 300 بـ1.15 (الصراف يستلم يورو ويسلّم دولار) */
    const a = ex("EUR", "USD", 1000, 1100), b = ex("EUR", "USD", 500, 560), c = ex("EUR", "USD", 300, 345);
    const st = E.replay(st0.concat([a, b, c]), S2);
    const expected = new D(1000 * 1.10 + 500 * 1.12 + 300 * 1.15).div(1800);
    check("متوسط التكلفة المرجح = (1000×1.10 + 500×1.12 + 300×1.15) ÷ 1800", near(st.curs.EUR.book.div(st.curs.EUR.qty), expected));
    /* بيع جزء من الرصيد: 600 يورو بـ1.20 */
    const s = ex("USD", "EUR", 720, 600);
    const st2 = E.replay(st0.concat([a, b, c, s]), S2);
    check("بيع جزء من الرصيد: الربح = 720 − 600 × المتوسط", near(st2.results[s.id].profit, new D(720).minus(expected.times(600))));
    check("المتوسط لا يتغير بعد البيع", near(st2.curs.EUR.book.div(st2.curs.EUR.qty), expected));
    check("بيع كامل الرصيد يصفّر القيمة الدفترية", (function () {
        const all = ex("USD", "EUR", 1300, 1200);
        const st3 = E.replay(st0.concat([a, b, c, s, all]), S2);
        return st3.curs.EUR.qty.isZero() && st3.curs.EUR.book.isZero();
    })());
}

/* --- تبديل عملتين غير الدولار: لا ازدواج للربح --- */
{
    const S2 = settings({ valuation: "cost" });
    const t = ex("TRY", "SYP", 1000, 2800);
    const st = E.replay([open, t], S2);
    check("سياسة التكلفة: تبديل ليرة تركية بسورية ربحه صفر حتى العودة للدولار", near(st.results[t.id].profit, 0));
    const stR = E.replay([open, t], S);
    const expected = new D(1000).div("38.5").minus(new D(2800).div(111));
    check("سياسة المرجع: ربح التبديل = فرق القيمتين بالسعر المرجعي (مرة واحدة)", near(stR.results[t.id].profit, expected));
    const bookAll = function (x) { return Object.keys(x.curs).reduce(function (s, k) { return s.plus(x.curs[k].book); }, E.ZERO); };
    const before = bookAll(E.replay([open], S));
    check("التوازن بعد تبديل غير الدولار", near(bookAll(stR).minus(before), stR.results[t.id].profit));
}

/* --- التكلفة اليدوية: لا أرباح وهمية --- */
{
    const m = ex("SYP", "USD", 11200, 100);
    const t = ex("USD", "TRY", 100, 3800, { costMethod: "manual", manualUnitCost: "0.03" }); /* تكلفة يدوية أعلى من 0.025 */
    const st = E.replay([open, m, t], S);
    const r = st.results[t.id];
    check("التكلفة اليدوية تُستخدم في ربح الصرافة", near(r.profit, new D(100).minus(new D(3800).times("0.03"))));
    check("فرق التكلفة اليدوية يظهر كتسوية تقييم منفصلة", near(r.valuation, new D(3800).times("0.03").minus(new D(3800).times("0.025"))));
    check("ربح الصرافة + تسوية التقييم = الأثر الدفتري الفعلي", near(r.profit.plus(r.valuation), r.valueIn.minus(r.bookCost)));
}

/* --- الصندوق: إضافة وسحب وتصفير واستبدال لا تُحتسب أرباحاً --- */
{
    const base = { type: "cash", day: "2026-11-01" };
    const add = Object.assign({ id: "c1", kind: "add", cur: "TRY", amount: "1000", unitCost: "0.026", at: "2026-11-01T09:00:00Z" }, base);
    const wd = Object.assign({ id: "c2", kind: "withdraw", cur: "USD", amount: "500", at: "2026-11-01T09:01:00Z" }, base);
    const rep = Object.assign({ id: "c3", kind: "replace", cur: "USD", target: "4000", at: "2026-11-01T09:02:00Z" }, base);
    const zero = Object.assign({ id: "c4", kind: "zero", cur: "EUR", at: "2026-11-01T09:03:00Z" }, base);
    const st = E.replay([open, add, wd, rep, zero], S);
    check("إضافة رصيد", st.curs.TRY.qty.eq(21000));
    check("سحب رصيد", st.results.c2.after.eq(4500));
    check("استبدال الرصيد يسجل الفرق", st.results.c3.delta.eq(-500) && st.curs.USD.qty.eq(4000));
    check("تصفير رصيد", st.curs.EUR.qty.isZero() && st.curs.EUR.book.isZero());
    const d = st.days["2026-11-01"];
    check("حركات الصندوق ليست أرباحاً", d.net.isZero() && d.count === 0);
    check("سجل حركات الصندوق: الرصيد السابق والتغيير والجديد", st.moves.some(function (m) { return m.id === "c3" && m.before.eq(4500) && m.delta.eq(-500) && m.after.eq(4000); }));
}

/* --- تجاوز الرصيد --- */
{
    const big = ex("SYP", "USD", 600000, 5001);
    const before = E.replay([open], S), after = E.replay([open, big], S);
    check("محاولة صرف مبلغ أكبر من الرصيد تُكتشف", E.newNegatives(before, after).length === 1);
}

/* --- تعديل عملية قديمة يعيد احتساب ما بعدها --- */
{
    const S2 = settings({ valuation: "cost" });
    const o = opening({ USD: { qty: "1000", unitCost: "1" } });
    const a = ex("EUR", "USD", 100, 110), b = ex("USD", "EUR", 60, 50);
    const st1 = E.replay([o, a, b], S2);
    const a2 = Object.assign({}, a, { outAmt: "120" });
    const st2 = E.replay([o, a2, b], S2);
    check("تعديل تكلفة الشراء يغيّر ربح البيع اللاحق", near(st1.results[b.id].profit, 5) && near(st2.results[b.id].profit, 0));
    const st3 = E.replay([o, Object.assign({}, a, { deleted: true }), b], S2);
    check("حذف عملية منطقياً يستبعدها ويكشف الرصيد السالب الناتج", st3.negatives.length === 1);
}

/* --- سحب الأرباح --- */
{
    const s1 = ex("SYP", "USD", 11200, 100);
    const w = { id: "w1", type: "withdrawal", day: "2026-11-01", at: "2026-11-01T23:00:00Z", cur: "SYP", amount: "5550", rate: "111", usdValue: "50" };
    const st = E.replay([open, s1, w], S);
    const d = st.days["2026-11-01"];
    check("سحب الأرباح لا يُحتسب خسارة", near(d.net, st.results[s1.id].profit) && d.loss.isZero());
    check("سحب الأرباح يخفض الرصيد ويُسجل بالدولار", st.curs.SYP.qty.eq(1000000 + 11200 - 5550) && d.withdrawals.eq(50));
}

/* --- الجرد --- */
{
    const lines = [
        { cur: "USD", recorded: "5000", actual: "4980", diff: "-20" },
        { cur: "SYP", recorded: "1000000", actual: "1000000", diff: "0" }
    ];
    const c1 = { id: "k1", type: "count", day: "2026-11-01", at: "2026-11-01T12:00:00Z", lines: lines, settled: false };
    const st = E.replay([open, c1], S);
    check("حفظ الجرد دون تسوية لا يغيّر الأرصدة", st.curs.USD.qty.eq(5000));
    const c2 = Object.assign({}, c1, { settled: true, reason: "نقص" });
    const s1 = ex("SYP", "USD", 11200, 100, { at: "2026-11-01T11:00:00Z" });
    const st2 = E.replay([open, s1, Object.assign({}, c2, { lines: [{ cur: "USD", recorded: "4900", actual: "4880", diff: "-20" }] })], S);
    const d = st2.days["2026-11-01"];
    check("تسوية الجرد تعدل الرصيد", st2.curs.USD.qty.eq(4880));
    check("فرق الجرد منفصل عن ربح الصرافة", near(d.inventory, -20) && near(d.net, st2.results[s1.id].profit));
    const surplus = { id: "k3", type: "count", day: "2026-11-01", at: "2026-11-01T13:00:00Z", settled: true, lines: [{ cur: "SYP", diff: "50000" }] };
    const st3 = E.replay([open, surplus], S);
    check("جرد فيه زيادة يضيفها بمتوسط التكلفة", st3.curs.SYP.qty.eq(1050000) && near(st3.days["2026-11-01"].inventory, new D(50000).div(111)));
}

/* --- الإقفال --- */
{
    const a = ex("SYP", "USD", 11200, 100, { day: "2026-11-02", at: "2026-11-02T10:00:00Z" });
    const st = E.replay([open, a], S);
    const snap = E.closeSnapshot(st, "2026-11-02", S);
    check("ملخص الإقفال يحفظ العدد والأرصدة وسعر الليرة", snap.count === 1 && snap.balances.USD.qty === "4900" && snap.displayRate === "111");
    check("اليوم التالي يحتفظ بالأرصدة كما هي", E.balancesAt(st, "2026-11-05").USD.qty.eq(4900));
    const a2 = Object.assign({}, a, { inAmt: "11300" });
    const snap2 = E.closeSnapshot(E.replay([open, a2], S), "2026-11-02", S);
    check("كشف تغيّر نتيجة يوم مقفل بعد تعديل", E.snapshotDiffers(snap, snap2) && !E.snapshotDiffers(snap, snap));
}

/* --- اليوم المحاسبي والمنطقة الزمنية --- */
{
    const t = Date.parse("2026-11-01T22:30:00Z"); /* دمشق UTC+3 ← 01:30 يوم 2 */
    check("اليوم المحاسبي بتوقيت دمشق", E.dayOf(t, "Asia/Damascus", 0) === "2026-11-02");
    check("بداية اليوم الساعة 2 صباحاً تُبقي العملية في اليوم السابق", E.dayOf(t, "Asia/Damascus", 2) === "2026-11-01");
    check("اليوم التالي", E.addDays("2026-12-31", 1) === "2027-01-01");
}

console.log("\n" + passed + " ناجح، " + failed + " فاشل");
process.exit(failed ? 1 : 0);
