/* =========================================================
   كاتب Excel صغير (xlsx) يعمل دون إنترنت ودون مكتبات خارجية.
   ينشئ ملف ZIP غير مضغوط فيه ورقة واحدة من اليمين لليسار.
   الاستخدام: XLSXLite.download("report.xlsx", "التقرير", [["عنوان",...], [1,2,...]], {headerRows:1})
   ========================================================= */
(function (root) {
    "use strict";

    const CRC_TABLE = (function () {
        const t = new Uint32Array(256);
        for (let n = 0; n < 256; n++) {
            let c = n;
            for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
            t[n] = c >>> 0;
        }
        return t;
    })();
    function crc32(bytes) {
        let c = 0xFFFFFFFF;
        for (let i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]) & 0xFF] ^ (c >>> 8);
        return (c ^ 0xFFFFFFFF) >>> 0;
    }
    function u16(v) { return [v & 0xFF, (v >>> 8) & 0xFF]; }
    function u32(v) { return [v & 0xFF, (v >>> 8) & 0xFF, (v >>> 16) & 0xFF, (v >>> 24) & 0xFF]; }

    function zip(files) {
        const enc = new TextEncoder();
        const parts = [], central = [];
        let offset = 0;
        files.forEach(function (f) {
            const name = enc.encode(f.name);
            const data = typeof f.data === "string" ? enc.encode(f.data) : f.data;
            const crc = crc32(data);
            const local = [].concat([0x50, 0x4B, 0x03, 0x04], u16(20), u16(0x0800), u16(0), u16(0), u16(0x21),
                u32(crc), u32(data.length), u32(data.length), u16(name.length), u16(0));
            parts.push(new Uint8Array(local), name, data);
            central.push(new Uint8Array([].concat([0x50, 0x4B, 0x01, 0x02], u16(20), u16(20), u16(0x0800), u16(0), u16(0), u16(0x21),
                u32(crc), u32(data.length), u32(data.length), u16(name.length), u16(0), u16(0), u16(0), u16(0), u32(0), u32(offset))), name);
            offset += local.length + name.length + data.length;
        });
        const centralSize = central.reduce(function (s, p) { return s + p.length; }, 0);
        const end = new Uint8Array([].concat([0x50, 0x4B, 0x05, 0x06], u16(0), u16(0), u16(files.length), u16(files.length),
            u32(centralSize), u32(offset), u16(0)));
        return new Blob(parts.concat(central, [end]), { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
    }

    function esc(s) {
        return String(s).replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; })
            .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, "");
    }
    function colName(i) {
        let s = "";
        i++;
        while (i > 0) { const m = (i - 1) % 26; s = String.fromCharCode(65 + m) + s; i = Math.floor((i - 1) / 26); }
        return s;
    }

    function build(sheetName, rows, opts) {
        opts = opts || {};
        const headerRows = opts.headerRows || 0;
        const widths = [];
        const xmlRows = rows.map(function (row, r) {
            const cells = row.map(function (v, c) {
                const ref = colName(c) + (r + 1);
                const style = r < headerRows ? ' s="1"' : "";
                const len = String(v === null || v === undefined ? "" : v).length;
                widths[c] = Math.max(widths[c] || 8, Math.min(60, len + 2));
                if (v === null || v === undefined || v === "") return '<c r="' + ref + '"' + style + "/>";
                if (typeof v === "number" && isFinite(v)) return '<c r="' + ref + '"' + style + "><v>" + v + "</v></c>";
                return '<c r="' + ref + '" t="inlineStr"' + style + "><is><t xml:space=\"preserve\">" + esc(v) + "</t></is></c>";
            }).join("");
            return '<row r="' + (r + 1) + '">' + cells + "</row>";
        }).join("");
        const cols = widths.length ? "<cols>" + widths.map(function (w, i) {
            return '<col min="' + (i + 1) + '" max="' + (i + 1) + '" width="' + w + '" customWidth="1"/>';
        }).join("") + "</cols>" : "";
        const sheet = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
            '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' +
            '<sheetViews><sheetView workbookViewId="0" rightToLeft="1"/></sheetViews>' + cols +
            "<sheetData>" + xmlRows + "</sheetData></worksheet>";
        const name = esc(String(sheetName || "Sheet1").replace(/[\\\/\?\*\[\]:]/g, " ").slice(0, 31));
        return zip([
            { name: "[Content_Types].xml", data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>' },
            { name: "_rels/.rels", data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>' },
            { name: "xl/workbook.xml", data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><bookViews><workbookView/></bookViews><sheets><sheet name="' + name + '" sheetId="1" r:id="rId1"/></sheets></workbook>' },
            { name: "xl/_rels/workbook.xml.rels", data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>' },
            { name: "xl/styles.xml", data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Arial"/></font><font><b/><sz val="11"/><name val="Arial"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFDCE6F0"/></patternFill></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="2"><xf fontId="0" fillId="0" borderId="0"/><xf fontId="1" fillId="2" borderId="0" applyFont="1" applyFill="1"/></cellXfs></styleSheet>' },
            { name: "xl/worksheets/sheet1.xml", data: sheet }
        ]);
    }

    function download(filename, sheetName, rows, opts) {
        const blob = build(sheetName, rows, opts);
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
    }

    root.XLSXLite = { build: build, download: download, crc32: crc32 };
})(typeof self !== "undefined" ? self : this);
