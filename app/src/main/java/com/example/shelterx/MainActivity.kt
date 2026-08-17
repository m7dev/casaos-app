package com.example.shelterx

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.text.InputType
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            this,
            "shelterx_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private val defaultAddress = "192.168.0.100"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWebView()
        openSaved()
    }

    private fun setupWebView() {
        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = false
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            web.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean = false
            override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) showErrorOverlay()
            }
            override fun onPageFinished(v: WebView, url: String?) {
                super.onPageFinished(v, url)
                tryAutoLogin()
            }
        }
        setupLayout()
    }

    private fun setupLayout() {
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        val webParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        root.addView(web, webParams)

        val density = resources.displayMetrics.density
        val buttonSize = (40 * density).toInt()

        val settingsButton = Button(this).apply {
            text = "⚙"
            textSize = 18f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.TRANSPARENT)
            alpha = 0.9f
        }
        val buttonParams = FrameLayout.LayoutParams(buttonSize, buttonSize).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = 0
            topMargin = 0
        }
        settingsButton.setOnClickListener { showSettings() }
        root.addView(settingsButton, buttonParams)

        setContentView(root)

        settingsButton.postDelayed({
            settingsButton.animate().alpha(0f).setDuration(400).withEndAction {
                settingsButton.visibility = View.GONE
            }.start()
        }, 3000)

        web.setOnLongClickListener {
            settingsButton.visibility = View.VISIBLE
            settingsButton.alpha = 0.9f
            settingsButton.postDelayed({
                settingsButton.animate().alpha(0f).setDuration(400).withEndAction {
                    settingsButton.visibility = View.GONE
                }.start()
            }, 3000)
            true
        }
    }

    private fun openSaved() {
        val address = prefs.getString("address", defaultAddress) ?: defaultAddress
        val url = normalize(address) ?: "http://$defaultAddress"
        web.loadUrl(url)
    }

    private fun normalize(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return try {
            val u = Uri.parse(s)
            if (u.host.isNullOrBlank()) null else s
        } catch (_: Exception) { null }
    }

    private fun tryAutoLogin() {
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        if (username.isEmpty() && password.isEmpty()) return

        val userJs = JSONObject.quote(username)
        val passJs = JSONObject.quote(password)
        val js = """
            (function() {
                var attempts = 0;
                function setValue(el, value) {
                    el.value = value;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                function findSubmit(scope) {
                    var btn = scope.querySelector('button[type=submit], input[type=submit]');
                    if (btn) return btn;
                    var buttons = scope.querySelectorAll('button');
                    for (var i = 0; i < buttons.length; i++) {
                        if (/login|sign.?in|увійти|вхід/i.test(buttons[i].textContent || '')) return buttons[i];
                    }
                    return null;
                }
                function tryFill() {
                    attempts++;
                    var scope = document.querySelector('.login-panel') || document;
                    var pass = scope.querySelector('input[type=password]');
                    if (!pass) {
                        if (attempts < 20) setTimeout(tryFill, 300);
                        return;
                    }
                    if (pass.value) return;
                    var user = scope.querySelector('input[type=text], input[type=email]');
                    if (user) setValue(user, $userJs);
                    setValue(pass, $passJs);
                    var form = pass.form;
                    if (form) {
                        if (typeof form.requestSubmit === 'function') form.requestSubmit();
                        else form.submit();
                        return;
                    }
                    var btn = findSubmit(scope);
                    if (btn) btn.click();
                }
                tryFill();
            })();
        """.trimIndent()
        web.evaluateJavascript(js, null)
    }

    private fun showErrorOverlay() {
        runOnUiThread {
            Toast.makeText(this, "Не вдалося підключитися до сервера", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }

        val input = EditText(this).apply {
            hint = "192.168.0.100:80"
            setSingleLine(true)
            setText(prefs.getString("address", defaultAddress))
            selectAll()
        }
        box.addView(input)

        val userInput = EditText(this).apply {
            hint = "Логін (необов'язково)"
            setSingleLine(true)
            setText(prefs.getString("username", ""))
        }
        box.addView(userInput)

        val passInput = EditText(this).apply {
            hint = "Пароль (необов'язково)"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("password", ""))
        }
        box.addView(passInput)

        val info = TextView(this).apply {
            text = "\nПриклади:\n192.168.0.100\n192.168.0.100:80\n192.168.0.100:8080\n\nМожна також вказати http:// або https://.\n\nЯкщо вказано логін і пароль, вони автоматично підставляються у форму входу на сторінці (зберігаються зашифровано)."
            textSize = 14f
        }
        box.addView(info)

        AlertDialog.Builder(this)
            .setTitle("Налаштування ShelterX")
            .setView(box)
            .setNegativeButton("Скасувати", null)
            .setNeutralButton("Оновити", null)
            .setPositiveButton("Зберегти", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value = input.text.toString().trim()
                        val url = normalize(value)
                        if (url == null) {
                            input.error = "Неправильна адреса"
                            return@setOnClickListener
                        }
                        prefs.edit()
                            .putString("address", value)
                            .putString("username", userInput.text.toString())
                            .putString("password", passInput.text.toString())
                            .apply()
                        web.loadUrl(url)
                        dialog.dismiss()
                    }
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        web.reload()
                        dialog.dismiss()
                    }
                    input.requestFocus()
                }
                dialog.show()
            }
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack()
        else super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add("Налаштування").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add("Оновити").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.title.toString()) {
            "Налаштування" -> showSettings()
            "Оновити" -> web.reload()
        }
        return true
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_UP &&
            event.keyCode == android.view.KeyEvent.KEYCODE_MENU) {
            showSettings()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
