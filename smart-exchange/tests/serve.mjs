/* خادم محلي بسيط للتشغيل والاختبار: node tests/serve.mjs [المنفذ] */
import http from "http";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const TYPES = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8", ".json": "application/json", ".svg": "image/svg+xml", ".webmanifest": "application/manifest+json" };
export function createServer() {
    return http.createServer(function (req, res) {
        let rel = decodeURIComponent(req.url.split("?")[0]).replace(/^\/+/, "") || "index.html";
        const file = path.join(ROOT, rel);
        if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); res.end(); return; }
        res.writeHead(200, { "Content-Type": TYPES[path.extname(file)] || "application/octet-stream", "Cache-Control": "no-cache" });
        fs.createReadStream(file).pipe(res);
    });
}
if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
    const port = Number(process.argv[2]) || 8080;
    createServer().listen(port, "0.0.0.0", function () {
        console.log("نظام الصرافة الذكي يعمل على: http://localhost:" + port);
        console.log("من جوال على الشبكة نفسها: http://<عنوان-IP-هذا-الكمبيوتر>:" + port);
    });
}
