package com.example.features.messages

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsTransactionReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val sender = messages[0].originatingAddress ?: "Bank SMS"
            val fullBody = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
            val timestamp = messages[0].timestampMillis

            receiverScope.launch {
                MessageIngestionManager.processIncomingMessage(
                    context = context,
                    rawText = fullBody,
                    source = "SMS: $sender",
                    timestamp = timestamp
                )
            }
        }
    }
}
