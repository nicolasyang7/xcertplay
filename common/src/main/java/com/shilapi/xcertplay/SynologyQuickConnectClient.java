package com.shilapi.xcertplay;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;


/** Synology DSM File Station QuickConnect/WebAPI file uploader. */
public class SynologyQuickConnectClient {
    private static final String TAG = "SynoQCClient";
    private static final int TIMEOUT_MS = 15000;

    public static class UploadResult {
        public boolean success;
        public String message;
        public String resolvedUrl;
        public UploadResult(boolean success, String message) { this(success, message, null); }
        public UploadResult(boolean success, String message, String resolvedUrl) {
            this.success = success; this.message = message; this.resolvedUrl = resolvedUrl;
        }
    }

    public UploadResult upload(String qcId, String username, String password, String otpCode,
                               String destFolder, File file, String overrideUrl) {
        if (file == null || !file.isFile()) return new UploadResult(false, "本地文件不存在");
        if (TextUtils.isEmpty(qcId) && TextUtils.isEmpty(overrideUrl)) return new UploadResult(false, "未配置 QuickConnect ID/备用地址");
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) return new UploadResult(false, "群晖账号或密码未设置");
        if (TextUtils.isEmpty(destFolder)) destFolder = "/docker/navitool-dashboard/data/logs";
        if (!destFolder.startsWith("/")) destFolder = "/" + destFolder;
        try {
            validateOverrideUrl(overrideUrl);
            String base = TextUtils.isEmpty(overrideUrl) ? resolveQuickConnect(qcId.trim()) : overrideUrl.trim();
            if (TextUtils.isEmpty(base)) {
                return new UploadResult(false, "QuickConnect 未找到可用的群晖 WebAPI 地址；已拒绝使用门户网页地址");
            }
            validateOverrideUrl(base);
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            Map<String, String> cookies = new HashMap<>();
            String sid = login(base, username, password, otpCode, cookies);
            if (TextUtils.isEmpty(sid)) return new UploadResult(false, "群晖登录失败，请检查账号/密码/验证码", base);
            boolean ok = uploadToFileStation(base, sid, cookies, destFolder, file);
            return new UploadResult(ok, ok ? "上传成功: " + file.getName() : "上传失败，请检查目录与写入权限", base);
        } catch (Exception e) {
            return new UploadResult(false, "上传异常: " + e.getClass().getSimpleName());
        }
    }

    private String resolveQuickConnect(String qcId) {
        // Try the China QuickConnect scheduler first for vehicles on mainland networks;
        // retain the global scheduler as fallback because some IDs are not served by .cn.
        for (String endpoint : new String[]{"https://global.quickconnect.cn/Serv.php", "https://global.quickconnect.to/Serv.php"}) {
            HttpURLConnection c = null;
            try {
                JSONObject req = new JSONObject(); req.put("version", 1); req.put("command", "get_server_info");
                req.put("serverID", qcId); req.put("id", "dsm_portal_https");
                c = openConnection(endpoint, "POST"); c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true); try (OutputStream out = c.getOutputStream()) { out.write(req.toString().getBytes(StandardCharsets.UTF_8)); }
                int responseCode = c.getResponseCode();
                if (responseCode == 200) {
                    JSONObject root = new JSONObject(readStream(c.getInputStream()));
                    if (root.optInt("errno", -1) != 0) {
                        continue;
                    }
                    JSONObject server = root.optJSONObject("server");
                    JSONObject service = root.optJSONObject("service");
                    JSONObject serviceMap = server == null ? null : server.optJSONObject("service");
                    JSONArray services = server == null ? null : server.optJSONArray("service");
                    LinkedHashSet<String> candidates = new LinkedHashSet<>();

                    // Current QuickConnect get_server_info response exposes the working DSM relay here.
                    if (service != null) {
                        String relayHost = service.optString("relay_dn", "");
                        int relayPort = service.optInt("relay_port", 0);
                        if (!TextUtils.isEmpty(relayHost) && relayPort > 0) {
                            candidates.add("https://" + relayHost + ":" + relayPort);
                        }
                    }
                    JSONObject smartdns = root.optJSONObject("smartdns");
                    if (smartdns != null) {
                        String directHost = smartdns.optString("external", "");
                        if (!TextUtils.isEmpty(directHost)) candidates.add(normalizeBase(directHost));
                    }
                    if (serviceMap != null) {
                        String candidate = serviceMap.optString("external", serviceMap.optString("dsm_portal_https", ""));
                        if (!TextUtils.isEmpty(candidate)) candidates.add(normalizeBase(candidate));
                    }
                    if (services != null) for (int i = 0; i < services.length(); i++) {
                        JSONObject item = services.optJSONObject(i);
                        if (item != null) {
                            String cand = item.optString("外部地址", item.optString("external", ""));
                            if (!TextUtils.isEmpty(cand)) candidates.add(normalizeBase(cand));
                        }
                    }
                    JSONObject env = root.optJSONObject("env");
                    if (env != null) {
                        String controlHost = env.optString("control_host", "");
                        if (!TextUtils.isEmpty(controlHost)) candidates.add(normalizeBase(qcId + "." + controlHost));
                    }
                    // Portal domains are last-resort candidates and are never accepted without API validation.
                    candidates.add("https://" + qcId + ".quickconnect.to");
                    candidates.add("https://" + qcId + ".quickconnect.cn");
                    for (String candidate : candidates) {
                        boolean relay = candidate.matches("https://[^/]+:[0-9]+");
                        int attempts = relay ? 2 : 1;
                        boolean reachable = false;
                        for (int attempt = 1; attempt <= attempts; attempt++) {
                            if (pingSynoApi(candidate)) {
                                reachable = true;
                                break;
                            }
                        }
                        if (reachable) {
                            return candidate;
                        }
                    }
                }
            } catch (Exception ignored) { }
            finally { if (c != null) c.disconnect(); }
        }
        return null;
    }

    private String normalizeBase(String value) {
        if (value.startsWith("http")) return value;
        return "https://" + value;
    }

    private void validateOverrideUrl(String overrideUrl) throws Exception {
        if (TextUtils.isEmpty(overrideUrl)) return;
        URI uri = new URI(overrideUrl.trim());
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IOException("备用地址必须使用 HTTP 或 HTTPS");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
            throw new IOException("备用地址格式无效");
        }
        if ("http".equalsIgnoreCase(scheme)) {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            boolean localOnly = addresses.length > 0;
            for (InetAddress address : addresses) {
                localOnly &= address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress();
            }
            if (!localOnly) {
                throw new IOException("HTTP 备用地址仅允许可信局域网；远程地址请用 HTTPS");
            }
        }
    }

    private boolean pingSynoApi(String baseUrl) {
        HttpURLConnection c = null;
        try {
            String url = baseUrl + "/webapi/query.cgi?api=SYNO.API.Info&version=1&method=query&query=SYNO.API.Auth";
            c = openConnection(url, "GET");
            // Mainland QuickConnect relays can take longer to complete TLS negotiation.
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(false);
            int code = c.getResponseCode();
            String contentType = c.getContentType();
            String body = readStream(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code == 200 && contentType != null && contentType.toLowerCase().contains("json")
                    && !TextUtils.isEmpty(body)) {
                return new JSONObject(body).optBoolean("success", false);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
        return false;
    }

    private String login(String base, String user, String pass, String otp, Map<String, String> cookies) throws Exception {
        String params = "api=SYNO.API.Auth&version=6&method=login&account=" + enc(user) + "&passwd=" + enc(pass)
                + "&session=FileStation&format=sid";
        if (!TextUtils.isEmpty(otp)) params += "&otp_code=" + enc(otp);
        JSONObject root = requestJson(base + "/webapi/auth.cgi", "POST", cookies,
                params.getBytes(StandardCharsets.UTF_8));
        JSONObject data = root.optJSONObject("data");
        return root.optBoolean("success") && data != null ? data.optString("sid", null) : null;
    }

    private boolean uploadToFileStation(String base, String sid, Map<String, String> cookies, String folder, File file) throws Exception {
        String boundary = "----NaviTool" + System.currentTimeMillis();
        // Keep the DSM session token in the query string; File Station ignores
        // _sid when it is included as one of the multipart form fields.
        String url = base + "/webapi/entry.cgi?_sid=" + enc(sid);
        HttpURLConnection c = openConnection(url, "POST");
        c.setDoOutput(true); c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        applyCookies(c, cookies);
        try (DataOutputStream out = new DataOutputStream(c.getOutputStream()); FileInputStream in = new FileInputStream(file)) {
            // File Station Upload expects every parameter in its multipart body;
            // the file content must be the final part (Synology API guide, v2).
            writeMultipartField(out, boundary, "api", "SYNO.FileStation.Upload");
            writeMultipartField(out, boundary, "version", "2");
            writeMultipartField(out, boundary, "method", "upload");
            writeMultipartField(out, boundary, "path", folder);
            writeMultipartField(out, boundary, "create_parents", "true");
            writeMultipartField(out, boundary, "overwrite", "true");
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + file.getName()
                    + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        try {
            int code = c.getResponseCode();
            JSONObject resp = readJsonResponse(c, "文件上传");
            boolean success = code >= 200 && code < 300 && resp.optBoolean("success");
            return success;
        } finally {
            c.disconnect();
        }
    }

    private void writeMultipartField(DataOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private JSONObject requestJson(String url, String method, Map<String, String> cookies, byte[] body) throws Exception {
        HttpURLConnection c = openConnection(url, method);
        c.setInstanceFollowRedirects(false);
        applyCookies(c, cookies);
        try {
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                try (OutputStream out = c.getOutputStream()) { out.write(body); }
            }
            String cookie = c.getHeaderField("Set-Cookie");
            if (cookie != null) cookies.put("cookie", cookie.split(";", 2)[0]);
            return readJsonResponse(c, "群晖 API");
        } finally {
            c.disconnect();
        }
    }

    private JSONObject readJsonResponse(HttpURLConnection c, String operation) throws Exception {
        int code = c.getResponseCode();
        String contentType = c.getContentType();
        String location = c.getHeaderField("Location");
        String body = readStream(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 300 && code < 400) {
            throw new java.io.IOException(operation + " 返回重定向 HTTP " + code + " (" + location + ")");
        }
        if (TextUtils.isEmpty(body) || !body.trim().startsWith("{")) {
            throw new java.io.IOException(operation + " 返回非 JSON (HTTP " + code + ", " + contentType + ")");
        }
        return new JSONObject(body);
    }

    private HttpURLConnection openConnection(String url, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(); c.setRequestMethod(method);
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "XcertPlay/1.0");
        return c;
    }
    private void applyCookies(HttpURLConnection c, Map<String, String> cookies) {
        String cookie = cookies.get("cookie"); if (cookie != null) c.setRequestProperty("Cookie", cookie);
    }
    private String readStream(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder b = new StringBuilder(); String line; while ((line = reader.readLine()) != null) b.append(line); return b.toString();
        }
    }
    private String enc(String value) throws Exception { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); }
}
