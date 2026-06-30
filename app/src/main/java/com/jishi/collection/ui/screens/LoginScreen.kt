package com.jishi.collection.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jishi.collection.BuildConfig
import com.jishi.collection.ui.components.MessageBar
import com.jishi.collection.ui.theme.JiShiColors

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    syncFromJson: (String) -> Unit,
    onLoginReady: () -> Unit,
) {
    val context = LocalContext.current
    var loginDetected by remember { mutableStateOf(false) }
    var loginHandled by remember { mutableStateOf(false) }
    var cookieHint by remember { mutableStateOf("等待小红书页面登录") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun handleLoginReady(message: String) {
        if (loginHandled) return
        loginHandled = true
        loginDetected = true
        cookieHint = message
        onLoginReady()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JiShiColors.Paper),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "小红书登录",
                color = JiShiColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "为了安全，手机号和验证码只在下方小红书官方 WebView 中输入。集示只检测本机 WebView Cookie，不保存登录凭据。",
                color = JiShiColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(12.dp))
            MessageBar(if (loginDetected) "登录态已就绪，正在关闭登录页。" else cookieHint)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    loginDetected = false
                    loginHandled = false
                    cookieHint = "正在清除 WebView 登录态..."
                    clearRednoteWebLoginState(webView) {
                        cookieHint = "登录态已清除，请重新登录"
                    }
                },
                shape = RoundedCornerShape(999.dp),
            ) {
                Text("清除登录态")
            }
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = {
                WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            cookieHint = "正在确认登录状态..."
                            view.evaluateJavascript(LOGIN_CHECK_SCRIPT, null)
                            view.postDelayed({ view.evaluateJavascript(LOGIN_CHECK_SCRIPT, null) }, 1200)
                            view.postDelayed({ view.evaluateJavascript(LOGIN_CHECK_SCRIPT, null) }, 3000)
                            view.postDelayed({ view.evaluateJavascript(LOGIN_CHECK_SCRIPT, null) }, 6000)
                        }
                    }
                    webChromeClient = WebChromeClient()
                    addJavascriptInterface(
                        RednoteBridge(
                            syncFromJson = syncFromJson,
                            postLoginStatus = { loggedIn, message ->
                                mainHandler.post {
                                    if (loggedIn) {
                                        handleLoginReady(message)
                                    } else {
                                        loginDetected = false
                                        cookieHint = message
                                    }
                                }
                            },
                            postStatus = { _, _ -> },
                        ),
                        "JiShiBridge",
                    )
                    loadUrl(REDNOTE_LOGIN_URL)
                }
            },
        )
    }
}

private fun clearRednoteWebLoginState(webView: WebView?, onDone: () -> Unit) {
    webView?.evaluateJavascript(
        """
            try {
              localStorage.clear();
              sessionStorage.clear();
              if (window.indexedDB && indexedDB.databases) {
                indexedDB.databases().then(function(databases) {
                  databases.forEach(function(database) {
                    if (database && database.name) indexedDB.deleteDatabase(database.name);
                  });
                }).catch(function() {});
              }
            } catch (_) {}
        """.trimIndent(),
        null,
    )
    CookieManager.getInstance().removeAllCookies {
        CookieManager.getInstance().removeSessionCookies {
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            webView?.clearCache(true)
            webView?.clearHistory()
            webView?.clearFormData()
            webView?.loadUrl(REDNOTE_LOGIN_URL)
            onDone()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HiddenRednoteSyncWebView(
    syncFromJson: (String) -> Unit,
    postStatus: (String, Boolean) -> Unit,
    completeSync: (String) -> Unit,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var started by remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier
            .size(1.dp)
            .alpha(0f),
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (started) return
                        started = true
                        if (!hasLikelyRednoteAuthCookie()) {
                            postStatus("未检测到小红书登录态，请先进入登录页完成登录", true)
                            return
                        }
                        postStatus("正在通过隐藏 WebView 获取收藏...", false)
                        view.evaluateJavascript(COLLECT_SYNC_SCRIPT, null)
                    }
                }
                webChromeClient = WebChromeClient()
                addJavascriptInterface(
                    RednoteBridge(
                        syncFromJson = syncFromJson,
                        completeSync = completeSync,
                        postLoginStatus = { _, _ -> },
                        postStatus = { message, done ->
                            mainHandler.post { postStatus(message, done) }
                        },
                    ),
                    "JiShiBridge",
                )
                loadUrl(REDNOTE_WEB_URL)
            }
        },
    )
}

private fun hasLikelyRednoteAuthCookie(): Boolean {
    val cookieManager = CookieManager.getInstance()
    val hosts = listOf(
        "https://www.xiaohongshu.com",
        "https://edith.xiaohongshu.com",
        "https://www.rednote.com",
    )
    return hosts
        .asSequence()
        .mapNotNull { cookieManager.getCookie(it) }
        .flatMap { it.split(";").asSequence() }
        .map { it.trim() }
        .any { cookie ->
            AUTH_COOKIE_NAMES.any { name ->
                cookie.startsWith("$name=") && cookie.length > "$name=".length
            }
        }
}

private val AUTH_COOKIE_NAMES = listOf("web_session")

private class RednoteBridge(
    private val syncFromJson: (String) -> Unit,
    private val completeSync: (String) -> Unit = {},
    private val postLoginStatus: (Boolean, String) -> Unit,
    private val postStatus: (String, Boolean) -> Unit,
) {
    @JavascriptInterface
    fun postFavoritesJson(json: String) {
        if (json.length <= 2_000_000) {
            syncFromJson(json)
        }
    }

    @JavascriptInterface
    fun postSyncStatus(message: String, done: Boolean) {
        postStatus(message, done)
    }

    @JavascriptInterface
    fun postSyncComplete(json: String) {
        if (json.length <= 2_000_000) {
            completeSync(json)
        }
    }

    @JavascriptInterface
    fun postLoginStatus(loggedIn: Boolean, message: String) {
        postLoginStatus(loggedIn, message)
    }
}

private const val REDNOTE_WEB_URL = "https://www.xiaohongshu.com"
private const val REDNOTE_LOGIN_URL = "https://www.xiaohongshu.com/notification"
private val LOGIN_CHECK_SCRIPT = """
    (async () => {
      const bridge = window.JiShiBridge;
      const report = (loggedIn, message, detail = {}) => {
        try { bridge && bridge.postLoginStatus(Boolean(loggedIn), String(message)); } catch (_) {}
        return JSON.stringify({ loggedIn: Boolean(loggedIn), message, detail });
      };
      const pageText = () => (document.body && document.body.innerText || '').replace(/\s+/g, ' ');
      const profileSignals = () => {
        const text = pageText();
        const path = location.pathname || '';
        const isProfileUrl = /\/user(\/profile\/[^/?#]+)?/.test(path);
        const hasProfileText = text.includes('小红书号') ||
          (text.includes('关注') && text.includes('粉丝') && (text.includes('获赞与收藏') || text.includes('收藏')));
        const isLoginPage = path.includes('/login') || text.includes('输入手机号') || text.includes('验证码登录');
        return { isProfileUrl, hasProfileText, isLoginPage, path };
      };
      const fetchWithTimeout = async (url, options = {}, timeoutMs = 4500) => {
        if (!window.AbortController) {
          return Promise.race([
            fetch(url, options),
            new Promise((_, reject) => setTimeout(() => reject(new Error('登录态接口超时')), timeoutMs))
          ]);
        }
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), timeoutMs);
        try {
          return await fetch(url, { ...options, signal: controller.signal });
        } finally {
          clearTimeout(timer);
        }
      };
      try {
        const response = await fetchWithTimeout('/api/sns/web/v2/user/me', { credentials: 'include' });
        const json = await response.json();
        const data = json && json.data || {};
        const user = data.user || data.user_info || data;
        const userId = data.user_id || user.user_id || user.id || data.id;
        const guest = data.guest || data.is_guest || user.guest;
        if (userId && guest !== true) {
          return report(true, '已确认小红书登录态', { source: 'user/me', userId });
        }
        const signals = profileSignals();
        if (signals.isProfileUrl && signals.hasProfileText && !signals.isLoginPage) {
          return report(true, '已根据个人主页确认登录态', { source: 'profile-page', signals });
        }
        return report(false, '请继续在小红书页面完成登录', { source: 'user/me-empty', json, signals });
      } catch (error) {
        const signals = profileSignals();
        if (signals.isProfileUrl && signals.hasProfileText && !signals.isLoginPage) {
          return report(true, '已根据个人主页确认登录态', { source: 'profile-page-after-error', signals });
        }
        return report(false, '请继续在小红书页面完成登录', { source: 'error', error: String(error && error.message || error), signals });
      }
    })();
""".trimIndent()

private val COLLECT_SYNC_SCRIPT = """
    (async () => {
      const bridge = window.JiShiBridge;
      const notify = (message, done = false) => {
        try { bridge && bridge.postSyncStatus(String(message), Boolean(done)); } catch (_) {}
      };
      const md5 = (string) => {
        const add32 = (a, b) => (a + b) & 0xffffffff;
        const cmn = (q, a, b, x, s, t) => add32((add32(add32(a, q), add32(x, t)) << s) | (add32(add32(a, q), add32(x, t)) >>> (32 - s)), b);
        const ff = (a, b, c, d, x, s, t) => cmn((b & c) | ((~b) & d), a, b, x, s, t);
        const gg = (a, b, c, d, x, s, t) => cmn((b & d) | (c & (~d)), a, b, x, s, t);
        const hh = (a, b, c, d, x, s, t) => cmn(b ^ c ^ d, a, b, x, s, t);
        const ii = (a, b, c, d, x, s, t) => cmn(c ^ (b | (~d)), a, b, x, s, t);
        const md5cycle = (x, k) => {
          let a = x[0], b = x[1], c = x[2], d = x[3];
          a = ff(a, b, c, d, k[0], 7, -680876936); d = ff(d, a, b, c, k[1], 12, -389564586);
          c = ff(c, d, a, b, k[2], 17, 606105819); b = ff(b, c, d, a, k[3], 22, -1044525330);
          a = ff(a, b, c, d, k[4], 7, -176418897); d = ff(d, a, b, c, k[5], 12, 1200080426);
          c = ff(c, d, a, b, k[6], 17, -1473231341); b = ff(b, c, d, a, k[7], 22, -45705983);
          a = ff(a, b, c, d, k[8], 7, 1770035416); d = ff(d, a, b, c, k[9], 12, -1958414417);
          c = ff(c, d, a, b, k[10], 17, -42063); b = ff(b, c, d, a, k[11], 22, -1990404162);
          a = ff(a, b, c, d, k[12], 7, 1804603682); d = ff(d, a, b, c, k[13], 12, -40341101);
          c = ff(c, d, a, b, k[14], 17, -1502002290); b = ff(b, c, d, a, k[15], 22, 1236535329);
          a = gg(a, b, c, d, k[1], 5, -165796510); d = gg(d, a, b, c, k[6], 9, -1069501632);
          c = gg(c, d, a, b, k[11], 14, 643717713); b = gg(b, c, d, a, k[0], 20, -373897302);
          a = gg(a, b, c, d, k[5], 5, -701558691); d = gg(d, a, b, c, k[10], 9, 38016083);
          c = gg(c, d, a, b, k[15], 14, -660478335); b = gg(b, c, d, a, k[4], 20, -405537848);
          a = gg(a, b, c, d, k[9], 5, 568446438); d = gg(d, a, b, c, k[14], 9, -1019803690);
          c = gg(c, d, a, b, k[3], 14, -187363961); b = gg(b, c, d, a, k[8], 20, 1163531501);
          a = gg(a, b, c, d, k[13], 5, -1444681467); d = gg(d, a, b, c, k[2], 9, -51403784);
          c = gg(c, d, a, b, k[7], 14, 1735328473); b = gg(b, c, d, a, k[12], 20, -1926607734);
          a = hh(a, b, c, d, k[5], 4, -378558); d = hh(d, a, b, c, k[8], 11, -2022574463);
          c = hh(c, d, a, b, k[11], 16, 1839030562); b = hh(b, c, d, a, k[14], 23, -35309556);
          a = hh(a, b, c, d, k[1], 4, -1530992060); d = hh(d, a, b, c, k[4], 11, 1272893353);
          c = hh(c, d, a, b, k[7], 16, -155497632); b = hh(b, c, d, a, k[10], 23, -1094730640);
          a = hh(a, b, c, d, k[13], 4, 681279174); d = hh(d, a, b, c, k[0], 11, -358537222);
          c = hh(c, d, a, b, k[3], 16, -722521979); b = hh(b, c, d, a, k[6], 23, 76029189);
          a = hh(a, b, c, d, k[9], 4, -640364487); d = hh(d, a, b, c, k[12], 11, -421815835);
          c = hh(c, d, a, b, k[15], 16, 530742520); b = hh(b, c, d, a, k[2], 23, -995338651);
          a = ii(a, b, c, d, k[0], 6, -198630844); d = ii(d, a, b, c, k[7], 10, 1126891415);
          c = ii(c, d, a, b, k[14], 15, -1416354905); b = ii(b, c, d, a, k[5], 21, -57434055);
          a = ii(a, b, c, d, k[12], 6, 1700485571); d = ii(d, a, b, c, k[3], 10, -1894986606);
          c = ii(c, d, a, b, k[10], 15, -1051523); b = ii(b, c, d, a, k[1], 21, -2054922799);
          a = ii(a, b, c, d, k[8], 6, 1873313359); d = ii(d, a, b, c, k[15], 10, -30611744);
          c = ii(c, d, a, b, k[6], 15, -1560198380); b = ii(b, c, d, a, k[13], 21, 1309151649);
          a = ii(a, b, c, d, k[4], 6, -145523070); d = ii(d, a, b, c, k[11], 10, -1120210379);
          c = ii(c, d, a, b, k[2], 15, 718787259); b = ii(b, c, d, a, k[9], 21, -343485551);
          x[0] = add32(a, x[0]); x[1] = add32(b, x[1]); x[2] = add32(c, x[2]); x[3] = add32(d, x[3]);
        };
        const md5blk = (s) => {
          const blocks = [];
          for (let i = 0; i < 64; i += 4) blocks[i >> 2] = s.charCodeAt(i) + (s.charCodeAt(i + 1) << 8) + (s.charCodeAt(i + 2) << 16) + (s.charCodeAt(i + 3) << 24);
          return blocks;
        };
        const md51 = (s) => {
          const n = s.length;
          const state = [1732584193, -271733879, -1732584194, 271733878];
          let i;
          for (i = 64; i <= n; i += 64) md5cycle(state, md5blk(s.substring(i - 64, i)));
          s = s.substring(i - 64);
          const tail = Array(16).fill(0);
          for (i = 0; i < s.length; i++) tail[i >> 2] |= s.charCodeAt(i) << ((i % 4) << 3);
          tail[i >> 2] |= 0x80 << ((i % 4) << 3);
          if (i > 55) { md5cycle(state, tail); tail.fill(0); }
          tail[14] = n * 8;
          md5cycle(state, tail);
          return state;
        };
        const hex = '0123456789abcdef';
        const rhex = (n) => {
          let s = '';
          for (let j = 0; j < 4; j++) s += hex[(n >> (j * 8 + 4)) & 0x0f] + hex[(n >> (j * 8)) & 0x0f];
          return s;
        };
        const state = md51(String(string));
        return rhex(state[0]) + rhex(state[1]) + rhex(state[2]) + rhex(state[3]);
      };
      const encodeUtf8 = (value) => {
        const encoded = encodeURIComponent(value);
        const bytes = [];
        for (let i = 0; i < encoded.length; i++) {
          const ch = encoded.charAt(i);
          if (ch === '%') {
            bytes.push(parseInt(encoded.charAt(i + 1) + encoded.charAt(i + 2), 16));
            i += 2;
          } else {
            bytes.push(ch.charCodeAt(0));
          }
        }
        return bytes;
      };
      const b64Encode = (bytes) => {
        const alphabet = 'ZmserbBoHQtNP+wOcza/LpngG8yJq42KWYj0DSfdikx3VT16IlUAFM97hECvuRX5';
        const triplet = (n) => alphabet[(n >> 18) & 63] + alphabet[(n >> 12) & 63] + alphabet[(n >> 6) & 63] + alphabet[n & 63];
        const chunks = [];
        const remainder = bytes.length % 3;
        const length = bytes.length - remainder;
        for (let i = 0; i < length; i += 3) chunks.push(triplet((bytes[i] << 16) + (bytes[i + 1] << 8) + bytes[i + 2]));
        if (remainder === 1) chunks.push(alphabet[bytes[length] >> 2] + alphabet[(bytes[length] << 4) & 63] + '==');
        if (remainder === 2) chunks.push(alphabet[((bytes[length] << 8) + bytes[length + 1]) >> 10] + alphabet[(((bytes[length] << 8) + bytes[length + 1]) >> 4) & 63] + alphabet[(((bytes[length] << 8) + bytes[length + 1]) << 2) & 63] + '=');
        return chunks.join('');
      };
      const crc32 = (value) => {
        const table = [];
        for (let i = 0; i < 256; i++) {
          let c = i;
          for (let j = 0; j < 8; j++) c = c & 1 ? (c >>> 1) ^ 0xedb88320 : c >>> 1;
          table[i] = c;
        }
        let crc = -1;
        for (let i = 0; i < value.length; i++) crc = table[(crc ^ value.charCodeAt(i)) & 255] ^ (crc >>> 8);
        return (-1 ^ crc ^ 0xedb88320) >>> 0;
      };
      const createSignature = (apiPath) => {
        if (typeof window.mnsv2 !== 'function') throw new Error('小红书签名环境未加载完成：window.mnsv2 not available');
        const timestamp = Date.now();
        const x3 = window.mnsv2(apiPath, md5(apiPath), md5(apiPath));
        const xs = 'XYS_' + b64Encode(encodeUtf8(JSON.stringify({
          x0: '4.3.3',
          x1: 'xhs-pc-web',
          x2: (navigator && navigator.platform) || 'Android',
          x3,
          x4: ''
        })));
        const a1Match = document.cookie.match(/a1=([^;]+)/);
        const a1 = a1Match ? a1Match[1] : '';
        const fingerprint = localStorage.getItem('b1') || '';
        const xsCommon = b64Encode(encodeUtf8(JSON.stringify({
          s0: 3,
          s1: '',
          x0: localStorage.getItem('b1b1') || '1',
          x1: '4.3.3',
          x2: (navigator && navigator.platform) || 'Android',
          x3: 'xhs-pc-web',
          x4: '6.2.1',
          x5: a1,
          x6: '',
          x7: '',
          x8: fingerprint,
          x9: crc32('' + fingerprint),
          x10: 0,
          x11: 'normal',
          x12: (localStorage.getItem('dsllt') || '') + ';' + (window._dsl || '')
        })));
        let traceId = '';
        const traceChars = 'abcdef0123456789';
        for (let i = 0; i < 16; i++) traceId += traceChars.charAt(Math.floor(Math.random() * traceChars.length));
        return { 'x-s': xs, 'x-t': String(timestamp), 'x-s-common': xsCommon, 'x-b3-traceid': traceId };
      };
      const signedGet = async (apiPath) => {
        const response = await fetch('https://edith.xiaohongshu.com' + apiPath, {
          method: 'GET',
          credentials: 'include',
          referrer: 'https://www.xiaohongshu.com/',
          headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            ...createSignature(apiPath)
          }
        });
        const text = await response.text();
        let json = {};
        try { json = JSON.parse(text); } catch (_) {}
        if (!response.ok) throw new Error('HTTP ' + response.status + ': ' + text.slice(0, 120));
        if (json && json.code && json.code !== 0) {
          throw new Error('接口返回 code=' + json.code + ' ' + (json.msg || json.message || ''));
        }
        return json;
      };
      const getUserId = async () => {
        const json = await signedGet('/api/sns/web/v2/user/me');
        const data = json && json.data || {};
        const userId = data.user_id || data.user?.user_id || data.user?.id || data.id;
        if (userId) return userId;
        throw new Error('未获取到 user_id：' + JSON.stringify(json).slice(0, 160));
      };
      const extractNotes = (data) => {
        if (Array.isArray(data.notes)) return data.notes;
        if (Array.isArray(data.items)) return data.items;
        if (Array.isArray(data.list)) return data.list;
        if (Array.isArray(data.note_list)) return data.note_list;
        if (Array.isArray(data.collects)) return data.collects;
        return [];
      };
      let currentUserId = '';
      try {
        notify('正在获取当前用户...');
        const userId = await getUserId();
        currentUserId = userId;
        let cursor = '';
        let page = 0;
        let total = 0;
        const syncedIds = [];
        let hasMore = true;
        const maxPages = 200;
        while (hasMore && page < maxPages) {
          page += 1;
          const params = new URLSearchParams({
            num: '10',
            user_id: userId,
            image_formats: 'jpg,webp,avif',
            xsec_token: '',
            xsec_source: ''
          });
          if (cursor) params.set('cursor', cursor);
          const path = '/api/sns/web/v2/note/collect/page?' + params.toString();
          notify('正在同步收藏第 ' + page + ' 页...');
          const json = await signedGet(path);
          const data = json.data || {};
          const notes = extractNotes(data);
          total += notes.length;
          cursor = data.cursor || '';
          hasMore = Boolean(data.has_more);
          notes.forEach((item) => {
            const card = item && item.note_card || {};
            const id = item && (item.id || item.note_id || item.rednoteId) || card.note_id || '';
            if (id) syncedIds.push(String(id));
          });
          bridge.postFavoritesJson(JSON.stringify({
            notes,
            sync: { accountUserId: userId, cursor, hasMore, page }
          }));
          if (!notes.length && !hasMore) break;
        }
        bridge.postSyncComplete(JSON.stringify({
          success: true,
          userId,
          ids: Array.from(new Set(syncedIds)),
          total,
          message: '真实收藏同步完成'
        }));
        notify('真实收藏同步完成，共读取 ' + total + ' 条。返回首页查看分类。', true);
        return JSON.stringify({ ok: true, total });
      } catch (error) {
        const message = '同步失败：' + String(error && error.message || error);
        bridge.postSyncComplete(JSON.stringify({
          success: false,
          userId: currentUserId,
          ids: [],
          message
        }));
        notify(message, true);
        return JSON.stringify({ ok: false, error: String(error && error.message || error) });
      }
    })();
""".trimIndent()
