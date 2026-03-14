package com.routerassistant;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class WebViewActivity extends AppCompatActivity {

    public static final String EXTRA_ADDRESS = "extra_address";
    private static final String TAG = "RouterMgr";

    private WebView webView;
    private LinearProgressIndicator progressBar;
    private MaterialToolbar toolbar;
    private PreferenceHelper prefHelper;
    private String address;
    private String username = "";
    private String password = "";
    private boolean desktopMode = false;
    private boolean loginCompleted = false;
    private String originalUA = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> debugLogs = new ArrayList<>();

    private static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        address = getIntent().getStringExtra(EXTRA_ADDRESS);
        if (address == null) {
            finish();
            return;
        }

        prefHelper = new PreferenceHelper(this);
        loadSavedCredentials();
        autoSaveRouterEntry();
        initViews();
        setupWebView();

        if (password.isEmpty()) {
            showFirstTimeCredentialsDialog();
        } else {
            dbg("=== start address=" + address + " user=[" + username + "] pass=[***] ===");
            loadRouter();
        }
    }

    private void dbg(String msg) {
        Log.d(TAG, msg);
        debugLogs.add(msg);
        if (debugLogs.size() > 500) debugLogs.remove(0);
    }

    private void loadSavedCredentials() {
        RouterInfo saved = prefHelper.getRouter(address);
        if (saved != null) {
            username = saved.getUsername();
            password = saved.getPassword();
        }
    }

    private void autoSaveRouterEntry() {
        RouterInfo existing = prefHelper.getRouter(address);
        if (existing == null) {
            prefHelper.saveRouter(new RouterInfo(address, username, password));
        } else {
            existing.setLastUsed(System.currentTimeMillis());
            prefHelper.saveRouter(existing);
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        progressBar = findViewById(R.id.progress_bar);
        webView = findViewById(R.id.webview);

        toolbar.setTitle(R.string.title_router_admin);
        toolbar.setSubtitle(address);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        View fab = findViewById(R.id.fab_autofill);
        if (fab != null) fab.setVisibility(View.GONE);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setTextZoom(100);

        originalUA = settings.getUserAgentString();

        webView.setWebViewClient(new RouterWebViewClient());
        webView.setWebChromeClient(new RouterWebChromeClient());
    }

    private void loadRouter() {
        String url = address.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        dbg("loadUrl: " + url);
        webView.loadUrl(url);
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            webView.reload();
            return true;
        } else if (id == R.id.action_desktop_mode) {
            desktopMode = !desktopMode;
            item.setChecked(desktopMode);
            toggleDesktopMode();
            return true;
        } else if (id == R.id.action_auto_fill) {
            loginCompleted = false;
            injectAutoFill(true);
            return true;
        } else if (id == R.id.action_save_credentials) {
            showSaveCredentialsDialog();
            return true;
        } else if (id == R.id.action_clear_cache) {
            webView.clearCache(true);
            webView.clearHistory();
            Toast.makeText(this, R.string.msg_cache_cleared, Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_disconnect) {
            finish();
            return true;
        } else if (id == R.id.action_debug_log) {
            showDebugLog();
            return true;
        }
        return false;
    }

    private void toggleDesktopMode() {
        WebSettings settings = webView.getSettings();
        if (desktopMode) {
            settings.setUserAgentString(DESKTOP_USER_AGENT);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            webView.setInitialScale(1);
        } else {
            settings.setUserAgentString(originalUA);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            webView.setInitialScale(0);
        }
        webView.reload();
    }

    private void injectDesktopViewport() {
        if (!desktopMode) return;
        String js = "(function() {" +
                "var meta = document.querySelector('meta[name=viewport]');" +
                "if (meta) { meta.setAttribute('content', 'width=1024'); }" +
                "else {" +
                "  meta = document.createElement('meta');" +
                "  meta.name = 'viewport';" +
                "  meta.content = 'width=1024';" +
                "  document.head.appendChild(meta);" +
                "}" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private String buildAutoFillJs() {
        String escapedUser = escapeJs(username);
        String escapedPass = escapeJs(password);

        return "(function() {" +
            "var L=function(m){console.log('[RM] '+m);};" +
            "if(window.__rmCleanup) window.__rmCleanup();" +
            "var U='" + escapedUser + "',P='" + escapedPass + "',MK='__rm';" +
            "L('autofill start, user=['+U+'] pass='+(P?'***':'(empty)'));" +

            "function hasPasswordField(doc){" +
            "  try{" +
            "    var found=false;" +
            "    doc.querySelectorAll('input').forEach(function(inp){" +
            "      var t=(inp.type||'').toLowerCase();" +
            "      var n=((inp.name||'')+'|'+(inp.id||'')+'|'+(inp.placeholder||'')).toLowerCase();" +
            "      if(t==='password'||n.indexOf('pwd')>=0||n.indexOf('pass')>=0||n.indexOf('mima')>=0) found=true;" +
            "    });" +
            "    if(found) return true;" +
            "    var fs=doc.querySelectorAll('iframe,frame');" +
            "    for(var i=0;i<fs.length;i++){" +
            "      try{" +
            "        var fd=fs[i].contentDocument||(fs[i].contentWindow&&fs[i].contentWindow.document);" +
            "        if(fd&&hasPasswordField(fd)) return true;" +
            "      }catch(e){}" +
            "    }" +
            "  }catch(e){}" +
            "  return false;" +
            "}" +

            "if(!hasPasswordField(document)){" +
            "  L('no password field found, not a login page, skipping');console.log('[RM_LOGIN_DONE]');return;" +
            "}" +

            "var ns;" +
            "try{ns=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;}catch(e){L('ERR get native setter: '+e);}" +
            "function setVal(el,val){" +
            "  try{el.focus();}catch(e){}" +
            "  if(ns){try{ns.call(el,val);}catch(e){L('ERR ns.call: '+e);el.value=val;}}" +
            "  else{el.value=val;}" +
            "  ['input','change','blur','keydown','keyup'].forEach(function(ev){" +
            "    el.dispatchEvent(new Event(ev,{bubbles:true}));" +
            "  });" +
            "}" +

            "function classify(inp){" +
            "  var t=(inp.type||'').toLowerCase();" +
            "  if(t==='hidden'||t==='submit'||t==='button'||t==='checkbox'||t==='radio'||t==='file'||t==='image') return 0;" +
            "  var n=(inp.name||'')+'|'+(inp.id||'')+'|'+(inp.placeholder||'')+'|'+(inp.getAttribute('data-type')||'')+'|'+(inp.className||'');" +
            "  n=n.toLowerCase();" +
            "  if(t==='password'||n.indexOf('pwd')>=0||n.indexOf('pass')>=0||n.indexOf('mima')>=0) return 1;" +
            "  if(t==='text'||t==='email'||t==='tel'||t===''||" +
            "     n.indexOf('user')>=0||n.indexOf('name')>=0||n.indexOf('account')>=0||" +
            "     n.indexOf('login')>=0||n.indexOf('admin')>=0||n.indexOf('zhanghu')>=0) return 2;" +
            "  return -1;" +
            "}" +

            "function descInp(inp){" +
            "  return '<input type=\"'+inp.type+'\" name=\"'+inp.name+'\" id=\"'+inp.id+'\" class=\"'+(inp.className||'').substring(0,60)+'\" placeholder=\"'+inp.placeholder+'\">';" +
            "}" +

            "function fillInput(inp){" +
            "  if(!inp||!inp.tagName||inp.tagName!=='INPUT'||inp.getAttribute(MK)) return false;" +
            "  var c=classify(inp);" +
            "  L('scan: '+descInp(inp)+' => classify='+c);" +
            "  if(c===1){setVal(inp,P);inp.setAttribute(MK,'1');L('FILLED password');return true;}" +
            "  if(c===2&&U){setVal(inp,U);inp.setAttribute(MK,'1');L('FILLED username');return true;}" +
            "  if(c===-1){L('SKIP unknown type='+inp.type);}" +
            "  return false;" +
            "}" +

            "function scanDoc(doc,label){" +
            "  try{" +
            "    var inputs=doc.querySelectorAll('input');" +
            "    L('scanDoc('+label+'): found '+inputs.length+' inputs');" +
            "    inputs.forEach(function(inp){fillInput(inp);});" +
            "    var fs=doc.querySelectorAll('iframe,frame');" +
            "    if(fs.length>0) L('found '+fs.length+' iframes');" +
            "    fs.forEach(function(f,idx){" +
            "      try{" +
            "        var fd=f.contentDocument||(f.contentWindow&&f.contentWindow.document);" +
            "        if(fd){" +
            "          var fi=fd.querySelectorAll('input');" +
            "          L('iframe['+idx+']: accessible, '+fi.length+' inputs');" +
            "          fi.forEach(function(inp){fillInput(inp);});" +
            "        }else{L('iframe['+idx+']: contentDocument is null');}" +
            "      }catch(e){L('iframe['+idx+']: cross-origin or error: '+e);}" +
            "    });" +
            "  }catch(e){L('scanDoc error: '+e);}" +
            "}" +

            "function observe(doc){" +
            "  try{" +
            "    var tgt=doc.body||doc.documentElement;" +
            "    if(!tgt){L('observe: no body/docEl');return null;}" +
            "    var obs=new MutationObserver(function(muts){" +
            "      muts.forEach(function(m){" +
            "        if(m.type==='attributes'&&m.target.tagName==='INPUT'){" +
            "          if(m.attributeName!=='type') return;" +
            "          L('mutation: type changed on '+descInp(m.target));" +
            "          m.target.removeAttribute(MK);fillInput(m.target);return;" +
            "        }" +
            "        m.addedNodes.forEach(function(nd){" +
            "          if(nd.nodeType!==1) return;" +
            "          if(nd.tagName==='INPUT'){L('mutation: INPUT added');fillInput(nd);}" +
            "          else if(nd.querySelectorAll){" +
            "            var ni=nd.querySelectorAll('input');" +
            "            if(ni.length>0){L('mutation: node added with '+ni.length+' inputs');}" +
            "            ni.forEach(function(i){fillInput(i);});" +
            "          }" +
            "          if(nd.tagName==='IFRAME'||nd.tagName==='FRAME'){" +
            "            L('mutation: iframe added');" +
            "            nd.addEventListener('load',function(){scanDoc(doc,'iframe-load');});" +
            "          }" +
            "        });" +
            "      });" +
            "    });" +
            "    obs.observe(tgt,{childList:true,subtree:true,attributes:true,attributeFilter:['type']});" +
            "    L('MutationObserver installed');" +
            "    return obs;" +
            "  }catch(e){L('observe error: '+e);return null;}" +
            "}" +

            "function watchFrames(doc){" +
            "  try{" +
            "    doc.querySelectorAll('iframe,frame').forEach(function(f){" +
            "      f.addEventListener('load',function(){" +
            "        try{" +
            "          var fd=f.contentDocument||(f.contentWindow&&f.contentWindow.document);" +
            "          if(fd){L('iframe onload: re-scan');scanDoc(fd,'iframe-onload');observe(fd);}" +
            "        }catch(e){L('iframe onload error: '+e);}" +
            "      });" +
            "    });" +
            "  }catch(e){}" +
            "}" +

            "L('=== page HTML tag dump ===');" +
            "try{" +
            "  var all=document.querySelectorAll('input,textarea,select,[contenteditable]');" +
            "  L('total interactive elements: '+all.length);" +
            "  all.forEach(function(el,i){" +
            "    L('  ['+i+'] <'+el.tagName.toLowerCase()+' type=\"'+(el.type||'')+'\" name=\"'+(el.name||'')+'\" id=\"'+(el.id||'')+'\" class=\"'+(el.className||'').substring(0,50)+'\" placeholder=\"'+(el.placeholder||'')+'\">');" +
            "  });" +
            "}catch(e){L('dump error: '+e);}" +

            "scanDoc(document,'initial');" +
            "var mainObs=observe(document);" +
            "watchFrames(document);" +

            "var pc=0,pid=setInterval(function(){" +
            "  pc++;" +
            "  if(!hasPasswordField(document)){L('password field gone, login done');if(window.__rmCleanup)window.__rmCleanup();console.log('[RM_LOGIN_DONE]');return;}" +
            "  scanDoc(document,'poll#'+pc);" +
            "  if(pc>=40){clearInterval(pid);L('polling stopped after 20s');}" +
            "},500);" +

            "window.__rmCleanup=function(){" +
            "  if(mainObs)mainObs.disconnect();" +
            "  clearInterval(pid);" +
            "};" +
            "setTimeout(function(){if(window.__rmCleanup)window.__rmCleanup();L('autofill engine stopped (30s timeout)');},30000);" +
            "})();";
    }

    private void injectAutoFill(boolean showToast) {
        if (username.isEmpty() && password.isEmpty()) {
            if (showToast) {
                Toast.makeText(this, R.string.msg_no_credentials, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        dbg("injectAutoFill called, showToast=" + showToast);
        webView.evaluateJavascript(buildAutoFillJs(), result -> {
            if (showToast) {
                Toast.makeText(this, R.string.msg_auto_fill_done, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private void clearWebViewSessionData() {
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
        dbg("cleared cookies, cache & form data");
    }

    private void showFirstTimeCredentialsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_credentials, null);
        TextInputEditText etUser = dialogView.findViewById(R.id.et_dialog_username);
        TextInputEditText etPass = dialogView.findViewById(R.id.et_dialog_password);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_first_time_title)
                .setMessage(R.string.dialog_first_time_message)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save_and_connect, (dialog, which) -> {
                    String newUser = etUser.getText() != null ? etUser.getText().toString().trim() : "";
                    String newPass = etPass.getText() != null ? etPass.getText().toString() : "";
                    username = newUser;
                    password = newPass;
                    prefHelper.saveRouter(new RouterInfo(address, username, password));
                    dbg("=== start address=" + address + " user=[" + username + "] pass=[" + (password.isEmpty() ? "" : "***") + "] ===");
                    loadRouter();
                })
                .setNegativeButton(R.string.btn_skip, (dialog, which) -> {
                    clearWebViewSessionData();
                    dbg("=== start address=" + address + " user=[] pass=[] (skipped) ===");
                    loadRouter();
                })
                .setCancelable(false)
                .show();
    }

    private void showSaveCredentialsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_credentials, null);
        TextInputEditText etUser = dialogView.findViewById(R.id.et_dialog_username);
        TextInputEditText etPass = dialogView.findViewById(R.id.et_dialog_password);

        etUser.setText(username);
        etPass.setText(password);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_save_credentials)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String newUser = etUser.getText() != null ? etUser.getText().toString().trim() : "";
                    String newPass = etPass.getText() != null ? etPass.getText().toString() : "";
                    username = newUser;
                    password = newPass;
                    RouterInfo router = new RouterInfo(address, username, password);
                    prefHelper.saveRouter(router);
                    Toast.makeText(this, R.string.msg_credentials_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    @SuppressLint("SetTextI18n")
    private void showDebugLog() {
        StringBuilder sb = new StringBuilder();
        for (String line : debugLogs) {
            sb.append(line).append("\n");
        }
        String logText = sb.toString();

        ScrollView scrollView = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(logText.isEmpty() ? getString(R.string.msg_no_logs) : logText);
        tv.setTextSize(11);
        tv.setPadding(32, 16, 32, 16);
        tv.setTextIsSelectable(true);
        scrollView.addView(tv);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_debug_log)
                .setView(scrollView)
                .setPositiveButton(R.string.btn_copy_all, (dialog, which) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("debug_log", logText));
                        Toast.makeText(this, R.string.msg_copied_to_clipboard, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(R.string.btn_clear, (dialog, which) -> debugLogs.clear())
                .setNegativeButton(R.string.btn_close, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private void tryAutoFillWithRetry() {
        if (username.isEmpty() && password.isEmpty()) return;
        if (loginCompleted) {
            dbg("loginCompleted=true, skipping autofill");
            return;
        }
        handler.postDelayed(() -> injectAutoFill(false), 150);
    }

    private class RouterWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return false;
            }
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            dbg("pageStarted: " + url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            toolbar.setSubtitle(view.getTitle() != null ? view.getTitle() : address);
            dbg("pageFinished: " + url);
            injectDesktopViewport();
            tryAutoFillWithRetry();
        }

        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                              String host, String realm) {
            dbg("httpAuthRequest: host=" + host + " realm=" + realm);
            if (!username.isEmpty() && !password.isEmpty()) {
                handler.proceed(username, password);
            } else {
                super.onReceivedHttpAuthRequest(view, handler, host, realm);
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            dbg("sslError: " + error.toString());
            new MaterialAlertDialogBuilder(WebViewActivity.this)
                    .setTitle(R.string.dialog_ssl_title)
                    .setMessage(R.string.dialog_ssl_message)
                    .setPositiveButton(R.string.btn_continue, (dialog, which) -> handler.proceed())
                    .setNegativeButton(R.string.btn_cancel, (dialog, which) -> handler.cancel())
                    .setCancelable(false)
                    .show();
        }
    }

    private class RouterWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            progressBar.setProgress(newProgress);
            if (newProgress >= 100) {
                progressBar.setVisibility(View.GONE);
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            if (title != null && !title.isEmpty()) {
                toolbar.setSubtitle(title);
            }
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage cm) {
            String msg = cm.message();
            if (msg != null) {
                if (msg.startsWith("[RM]")) {
                    dbg("JS " + msg);
                } else if (msg.equals("[RM_LOGIN_DONE]")) {
                    loginCompleted = true;
                    dbg("login detected as completed, autofill disabled for subsequent pages");
                }
            }
            return super.onConsoleMessage(cm);
        }
    }
}
