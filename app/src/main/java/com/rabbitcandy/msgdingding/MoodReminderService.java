package com.rabbitcandy.msgdingding;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.app.NotificationCompat;

import java.util.HashSet;
import java.util.Set;

public class MoodReminderService extends AccessibilityService {

    private static final String TAG = "MoodReminder";
    private static final String PREFS_NAME = "MoodReminderPrefs";
    private static final String PACKAGE_LIST_KEY = "package_list";
    private static final String REMINDER_INTERVAL_KEY = "reminder_interval";
    private static final String CHANNEL_ID = "mood_reminder_channel";
    private static final int DEFAULT_REMINDER_INTERVAL = 60000; // 默认60秒
    private static final int NOTIFICATION_ID_FOREGROUND = 1001;
    private NotificationManager notificationManager;
    private final Set<String> monitoredPackages = new HashSet<>();
    private long reminderInterval = DEFAULT_REMINDER_INTERVAL;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "无障碍服务已连接");

        // 1. 必须先获取 NotificationManager 实例
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // 2. 创建通知渠道（Android 8.0+ 必须）
        createNotificationChannel();

        // 3. 加载用户配置的包名和提醒间隔
        loadSettings();

        // 4. 启动前台服务（此时通知渠道已就绪，不会崩溃）
        startForegroundService();

        // 5. 保存无障碍服务已启用的标志（供开机自启判断）
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean("accessibility_enabled", true).apply();
    }

    // 保存最后提醒时间
    private void saveLastReminderTime(long time) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong("last_reminder_time", time)
                .apply();
    }

    // 读取最后提醒时间
    private long getLastReminderTime() {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong("last_reminder_time", 0);
    }

    /**
     * 启动前台服务（Android 8.0+ 必须调用 startForeground 以防止服务被系统杀死）
     */
    private void startForegroundService() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)  // 建议换成自己的图标
                .setContentTitle("定时情绪管家运行中")
                .setContentText("正在后台提醒需要及时回复消息")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);  // 常驻通知，用户无法手动清除

        startForeground(NOTIFICATION_ID_FOREGROUND, builder.build());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence packageName = event.getPackageName();
        if (packageName == null) return;

        String pkg = packageName.toString();
        boolean isMonitoredApp = monitoredPackages.contains(pkg);

        // 当窗口状态改变时（即切换到某个应用界面），检查是否需要提醒
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            long currentTime = System.currentTimeMillis();
            long lastReminderTime = getLastReminderTime(); // 从本地读取

            if (isMonitoredApp && (currentTime - lastReminderTime) >= reminderInterval) {
                showNotification("定时提醒", "请注意及时回复消息");
                saveLastReminderTime(currentTime); // 保存到本地
                loadSettings();  // 重新加载配置，以便用户在设置修改后能动态生效
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "无障碍服务被系统中断");
    }

    /**
     * 加载用户保存的配置：监听包名列表、提醒间隔
     */
    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> savedPackages = prefs.getStringSet(PACKAGE_LIST_KEY, null);

        // 清空并添加默认包名（QQ、微信、YouTube）
        monitoredPackages.clear();
        monitoredPackages.add("com.tencent.mobileqq");
        monitoredPackages.add("com.tencent.mm");
        monitoredPackages.add("com.google.android.youtube");

        if (savedPackages != null) {
            monitoredPackages.addAll(savedPackages);
        }

        reminderInterval = prefs.getLong(REMINDER_INTERVAL_KEY, DEFAULT_REMINDER_INTERVAL);
        Log.d(TAG, "配置加载完成，监听包数：" + monitoredPackages.size() + "，间隔：" + reminderInterval + "ms");
    }

    /**
     * 发送通知提醒
     */
    private void showNotification(String title, String content) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } else {
            Log.e(TAG, "notificationManager 为 null，无法发送通知");
        }
    }

    /**
     * 创建通知渠道（Android 8.0+ 必须）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "定时提醒";
            String description = "用于定时提醒需要及时回复消息";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "通知渠道创建成功");
            } else {
                Log.e(TAG, "notificationManager 为 null，无法创建通知渠道");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "服务已销毁");

        // 保存无障碍服务已关闭的标志
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean("accessibility_enabled", false).apply();
    }
}