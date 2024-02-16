package com.google.test.inspectors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    Logger.info("AlarmReceiver: onReceive(${intent.action})")
  }
}
