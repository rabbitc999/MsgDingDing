package com.rabbitcandy.msgdingding;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("BootReceiver", "系统开机，尝试启动服务");
            // 启动无障碍服务
            Intent serviceIntent = new Intent(context, MoodReminderService.class);
            context.startForegroundService(serviceIntent);

            // 可选：显示通知提醒用户检查无障碍是否开启
            SharedPreferences prefs = context.getSharedPreferences("MoodReminderPrefs", Context.MODE_PRIVATE);
            boolean isServiceEnabled = prefs.getBoolean("accessibility_enabled", false);
            if (!isServiceEnabled) {
                // 打开主界面，提示用户开启无障碍
                Intent activityIntent = new Intent(context, MainActivity.class);
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(activityIntent);
            }
        }
    }
}