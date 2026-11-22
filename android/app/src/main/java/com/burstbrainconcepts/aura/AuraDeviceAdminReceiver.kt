package com.burstbrainconcepts.aura

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Receives only the user-approved Android Device Admin lifecycle for lock-screen capability. */
class AuraDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "AURA screen-lock protection enabled.", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "AURA screen-lock protection disabled.", Toast.LENGTH_SHORT).show()
    }
}
