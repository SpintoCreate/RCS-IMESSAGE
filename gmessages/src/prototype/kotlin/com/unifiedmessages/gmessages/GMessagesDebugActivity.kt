package com.unifiedmessages.gmessages

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import gmbind.Bridge
import gmbind.Gmbind
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Separate launcher in debug builds only; it does not modify the main app UI. */
class GMessagesDebugActivity : ComponentActivity() {
    private val tag = "GMessagesDebug"
    private lateinit var bridge: Bridge
    private lateinit var store: GoogleSessionStore
    private lateinit var status: TextView
    private lateinit var conversations: TextView
    private lateinit var received: TextView
    private lateinit var passphrase: EditText
    private lateinit var encryptedInput: EditText
    private lateinit var chosenIndex: EditText
    private lateinit var message: EditText
    private val busy = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var restoreFailures = 0
    @Volatile private var sensitiveValues: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridge = Gmbind.newBridge()
        store = GoogleSessionStore(this)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val scroll = ScrollView(this).apply { addView(column) }
        setContentView(scroll)
        fun label(text: String): TextView = TextView(this).also {
            it.text = text
            it.textSize = 17f
            it.setPadding(0, 16, 0, 16)
            column.addView(it)
        }
        fun button(text: String, action: () -> Unit) {
            column.addView(Button(this).apply {
                this.text = text
                setOnClickListener { action() }
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        label("Personal Google Messages linked-device test. Google Messages must stay the default SMS app. Never share this build or a cookie export.")
        status = label("Disconnected")
        passphrase = EditText(this).apply {
            hint = "Nine-word import phrase shown on your PC"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            column.addView(this)
        }
        encryptedInput = EditText(this).apply {
            hint = "Encrypted transfer from PC (USB fills this automatically)"
            filters = arrayOf(android.text.InputFilter.LengthFilter(100_000))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 2
            column.addView(this)
        }
        receiveEncryptedIntent(intent)
        button("1. Import encrypted transfer and start pairing") {
            val encrypted = encryptedInput.text.toString().trim()
            val entered = passphrase.text
            val password = CharArray(entered.length) { entered[it] }
            encryptedInput.text.clear()
            passphrase.text.clear()
            runStep("pair import") {
                try {
                    val json = EncryptedTransfer.decrypt(encrypted, password)
                    val data = JSONObject(json)
                    sensitiveValues = data.keys().asSequence()
                        .mapNotNull { data.optString(it).takeIf(String::isNotBlank) }.toList()
                    clearMatchingClipboard(encrypted)
                    val emoji = bridge.beginPairing(json)
                    "Pairing request started. Tap this emoji in Google Messages: $emoji, then tap Confirm below."
                } finally {
                    password.fill('\u0000')
                }
            }
        }
        button("Confirm emoji tapped on phone") {
            runStep("pair confirmed") {
                bridge.finishPairing()
                val session = bridge.sessionJSON()
                store.write(session)
                sensitiveValues = emptyList()
                restoreFailures = 0
                "Paired. Session saved only in Android Keystore-backed encrypted storage."
            }
        }
        conversations = label("No conversations listed yet.")
        button("2. List conversations") {
            runStep("list conversations") {
                val count = bridge.listConversations().toInt()
                val lines = (0 until count).joinToString("\n") { "$it: ${bridge.conversationLabel(it.toLong())}" }
                runOnUiThread { conversations.text = if (count == 0) "No conversations found." else lines }
                "Listed $count conversations. Select the intended test conversation by its index."
            }
        }
        received = label("3. Received messages since opening: 0")
        button("3. Check receive count") { updateReceived() }
        chosenIndex = EditText(this).apply {
            hint = "Conversation index from the list (0-based)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            column.addView(this)
        }
        message = EditText(this).apply {
            hint = "One test text to your chosen conversation"
            column.addView(this)
        }
        button("4. Send one test text") {
            val index = chosenIndex.text.toString().toLongOrNull()
            val text = message.text.toString()
            if (index == null || text.isBlank()) {
                status.text = "Choose a listed conversation and write a test text."
            } else {
                sensitiveValues = listOf(text)
                runStep("send text") {
                    bridge.sendText(index, text)
                    sensitiveValues = emptyList()
                    runOnUiThread { message.text.clear() }
                    "Phone accepted the send request. Verify delivery in the recipient's app."
                }
            }
        }
        button("Sign out and erase Google Messages session") {
            runStep("erase session") {
                bridge.close()
                store.erase()
                sensitiveValues = emptyList()
                restoreFailures = 0
                "Local session erased. Unpair this device in Google Messages > Device Pairing too."
            }
        }
        if (store.exists()) restoreSession()
        handler.postDelayed(watchdog, 30_000)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveEncryptedIntent(intent)
    }

    private fun receiveEncryptedIntent(intent: Intent?) {
        val encrypted = intent?.getStringExtra("encrypted_transfer") ?: return
        intent.removeExtra("encrypted_transfer")
        encryptedInput.setText(encrypted)
        status.text = "Encrypted transfer received over USB. Enter the passphrase shown on your PC."
    }

    private fun clearMatchingClipboard(encrypted: String) {
        runOnUiThread {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.primaryClip?.getItemAt(0)?.text?.toString() == encrypted) {
                clipboard.clearPrimaryClip()
            }
        }
    }

    private fun runStep(step: String, operation: () -> String) {
        if (!busy.compareAndSet(false, true)) return
        status.text = "$step: working…"
        Log.i(tag, "$step: started")
        Thread {
            try {
                val result = operation()
                Log.i(tag, "$step: succeeded")
                runOnUiThread { status.text = result; updateReceived() }
            } catch (error: Throwable) {
                val detail = SafeDiagnostics.describe(error, sensitiveValues)
                Log.e(tag, "$step: failed: $detail")
                runOnUiThread { status.text = "$step failed: $detail" }
            } finally {
                busy.set(false)
            }
        }.start()
    }

    private fun restoreSession() {
        runStep("restore session") {
            val session = store.read() ?: error("No session is stored.")
            sensitiveValues = listOf(session)
            bridge.restore(session)
            sensitiveValues = emptyList()
            restoreFailures = 0
            "Connected to Google Messages."
        }
    }

    private fun updateReceived() {
        received.text = "3. Received messages since opening: ${bridge.receivedCount()}"
    }

    private val watchdog = object : Runnable {
        override fun run() {
            updateReceived()
            if (store.exists() && !busy.get() && bridge.status() == "disconnected") {
                if (restoreFailures < 2) {
                    restoreFailures++
                    restoreSession()
                } else {
                    status.text = "Disconnected. Reopen this screen after checking Google Messages and your connection."
                    Log.w(tag, "connection: disconnected; automatic restore paused after two failures")
                }
            }
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        passphrase.text.clear()
        encryptedInput.text.clear()
        bridge.close()
        super.onDestroy()
    }
}
