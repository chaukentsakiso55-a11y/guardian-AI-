package com.cyberpulse.guardianai

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val prefs by lazy { getSharedPreferences("guardian_state", Context.MODE_PRIVATE) }
    private val googleRequest = 9001
    private var pageReady = false
    private var pinUnlockedThisLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(1, 4, 8)
        window.navigationBarColor = Color.rgb(1, 4, 8)

        webView = WebView(this)
        webView.setBackgroundColor(Color.rgb(1, 4, 8))
        webView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = false
        webView.settings.setSupportZoom(false)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.addJavascriptInterface(GuardianBridge(), "GuardianNative")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                syncAuthUi()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return if (uri.scheme == "file" || uri.scheme == "data" || uri.scheme == "about") {
                    false
                } else {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    true
                }
            }
        }
        setContentView(webView)
        webView.loadUrl("file:///android_asset/guardian_ui.html")
    }

    override fun onResume() {
        super.onResume()
        if (pinUnlockedThisLaunch) startProtection()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != googleRequest) return
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            val token = account.idToken
            if (token.isNullOrBlank()) {
                authError("Google sign-in did not return an ID token.")
                return
            }
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) afterAuthentication("Google")
                    else authError(task.exception?.localizedMessage ?: "Google sign-in failed.")
                }
        } catch (e: Exception) {
            authError(e.localizedMessage ?: "Google sign-in was cancelled.")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun syncAuthUi() {
        if (!pageReady) return
        val guest = prefs.getBoolean("guest_session", false)
        val user = auth.currentUser
        when {
            user != null -> {
                js("window.guardianAuthSuccess && window.guardianAuthSuccess('Firebase');")
                ensurePin()
            }
            guest -> {
                js("window.guardianAuthSuccess && window.guardianAuthSuccess('Guest');")
                ensurePin()
            }
            else -> js("window.guardianRequireAuth && window.guardianRequireAuth();")
        }
    }

    private fun afterAuthentication(mode: String) {
        prefs.edit().putBoolean("guest_session", mode == "Guest").putString("access_mode", mode).apply()
        js("window.guardianAuthSuccess && window.guardianAuthSuccess(${quoteJs(mode)});")
        ensurePin()
    }

    private fun ensurePin() {
        if (pinUnlockedThisLaunch) return
        if (PinStore.hasPin(this)) showVerifyPinDialog(onVerified = {
            pinUnlockedThisLaunch = true
            startProtection()
            promptAccessibilityIfNeeded()
        }) else showCreatePinDialog()
    }

    private fun showCreatePinDialog() {
        val pin = pinField("6-digit PIN")
        val confirm = pinField("Confirm PIN")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
            addView(pin)
            addView(confirm)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Create GuardianAI protection PIN")
            .setMessage("This PIN protects critical GuardianAI controls.")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("Secure GuardianAI", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val a = pin.text.toString()
                val b = confirm.text.toString()
                when {
                    !a.matches(Regex("\\d{6}")) -> pin.error = "Enter exactly 6 digits"
                    a != b -> confirm.error = "PINs do not match"
                    else -> {
                        PinStore.save(this, a)
                        pinUnlockedThisLaunch = true
                        dialog.dismiss()
                        startProtection()
                        promptAccessibilityIfNeeded()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showVerifyPinDialog(onVerified: () -> Unit, onCancel: (() -> Unit)? = null) {
        val pin = pinField("Protection PIN")
        val dialog = AlertDialog.Builder(this)
            .setTitle("GuardianAI protection PIN")
            .setView(pin)
            .setCancelable(onCancel != null)
            .setPositiveButton("Unlock", null)
            .apply { if (onCancel != null) setNegativeButton("Cancel") { _, _ -> onCancel() } }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (PinStore.verify(this, pin.text.toString())) {
                    dialog.dismiss()
                    onVerified()
                } else pin.error = "Incorrect PIN"
            }
        }
        dialog.show()
    }

    private fun pinField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        setSingleLine(true)
        maxEms = 6
    }

    private fun startProtection() {
        ContextCompat.startForegroundService(this, Intent(this, ProtectionForegroundService::class.java))
        prefs.edit().putBoolean("protection_enabled", true).apply()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 401)
        }
    }

    private fun promptAccessibilityIfNeeded() {
        if (PermissionChecks.accessibility(this)) {
            js("window.guardianStatus && window.guardianStatus('Protection core active.');")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Enable external-app protection")
            .setMessage("GuardianAI needs Android Accessibility permission to detect and block matching access-seeking searches in supported external apps. Android requires you to approve this yourself.")
            .setPositiveButton("Open Accessibility") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun authError(message: String) {
        js("window.guardianAuthError && window.guardianAuthError(${quoteJs(message)});")
        if (!pageReady) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun js(script: String) {
        if (!pageReady) return
        runOnUiThread { webView.evaluateJavascript(script, null) }
    }

    private fun quoteJs(value: String): String = "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'"

    inner class GuardianBridge {
        @JavascriptInterface
        fun emailAuth(email: String, password: String, create: Boolean) {
            runOnUiThread {
                if (email.isBlank() || password.length < 6) {
                    authError("Enter a valid email and a password of at least 6 characters.")
                    return@runOnUiThread
                }
                val task = if (create) auth.createUserWithEmailAndPassword(email.trim(), password)
                else auth.signInWithEmailAndPassword(email.trim(), password)
                task.addOnCompleteListener(this@MainActivity) { result ->
                    if (result.isSuccessful) afterAuthentication("Email")
                    else authError(result.exception?.localizedMessage ?: "Email authentication failed.")
                }
            }
        }

        @JavascriptInterface
        fun guestAuth() = runOnUiThread { afterAuthentication("Guest") }

        @JavascriptInterface
        fun anonymousAuth() = runOnUiThread {
            auth.signInAnonymously().addOnCompleteListener(this@MainActivity) { result ->
                if (result.isSuccessful) afterAuthentication("Anonymous")
                else authError(result.exception?.localizedMessage ?: "Anonymous sign-in failed.")
            }
        }

        @JavascriptInterface
        fun googleAuth() = runOnUiThread {
            val clientId = getString(R.string.google_web_client_id)
            if (clientId.isBlank() || clientId.startsWith("YOUR_")) {
                authError("Google authentication is not fully configured in Firebase yet.")
                return@runOnUiThread
            }
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(clientId)
                .build()
            startActivityForResult(GoogleSignIn.getClient(this@MainActivity, options).signInIntent, googleRequest)
        }

        @JavascriptInterface
        fun askSafetyAI(question: String): String = SafetyKnowledge.answer(this@MainActivity, question)

        @JavascriptInterface
        fun disableProtection() = runOnUiThread {
            showVerifyPinDialog(onVerified = {
                stopService(Intent(this@MainActivity, ProtectionForegroundService::class.java))
                prefs.edit().putBoolean("protection_enabled", false).apply()
                js("window.guardianStatus && window.guardianStatus('Background protection stopped. Android Accessibility access remains controlled in system settings.');")
            })
        }

        @JavascriptInterface
        fun openAccessibility() = runOnUiThread { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        @JavascriptInterface
        fun openUsageAccess() = runOnUiThread { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

        @JavascriptInterface
        fun openNotificationAccess() = runOnUiThread { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }
}
