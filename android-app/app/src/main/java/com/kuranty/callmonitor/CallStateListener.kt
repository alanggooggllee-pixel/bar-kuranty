package com.kuranty.callmonitor

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Monitors phone call state using TelephonyCallback (Android 12+).
 * Triggers the overlay reminder when a call starts.
 */
@RequiresApi(Build.VERSION_CODES.S)
class CallStateListener(private val context: Context) {

    companion object {
        private const val TAG = "CallStateListener"
    }

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var isRegistered = false

    private val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    Log.i(TAG, "Call active — showing overlay reminder")
                    showOverlay()
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    Log.i(TAG, "Call ended — dismissing overlay")
                    dismissOverlay()
                }
                TelephonyManager.CALL_STATE_RINGING -> {
                    Log.i(TAG, "Incoming call ringing")
                }
            }
        }
    }

    fun register() {
        if (isRegistered) return
        try {
            telephonyManager.registerTelephonyCallback(
                context.mainExecutor,
                callback
            )
            isRegistered = true
            Log.i(TAG, "TelephonyCallback registered")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing READ_PHONE_STATE permission", e)
        }
    }

    fun unregister() {
        if (!isRegistered) return
        telephonyManager.unregisterTelephonyCallback(callback)
        isRegistered = false
        Log.i(TAG, "TelephonyCallback unregistered")
    }

    private fun showOverlay() {
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        }
        context.startForegroundService(intent)
    }

    private fun dismissOverlay() {
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_DISMISS
        }
        context.startService(intent)
    }
}
