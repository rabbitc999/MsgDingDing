package com.rabbitcandy.msgdingding;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private EditText etPackageList;
    private EditText etReminderInterval;
    private Button btnStartService;

    // 在 MainActivity 类中声明
    private final ActivityResultLauncher<Intent> batteryOptimizationLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // 用户从设置页面返回后，可以再次检查是否已关闭电池优化
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
                        Toast.makeText(this, "已关闭电池优化，服务将稳定运行", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "未关闭电池优化，可能影响后台运行", Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

    // Android13通知权限动态申请回调
    private final ActivityResultLauncher<String> notifyPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if(isGranted){
                    Toast.makeText(this,"通知权限已授权，可以正常弹窗提醒",Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(this,"通知权限被拒绝，无法弹出情绪提醒",Toast.LENGTH_LONG).show();
                    gotoAppNotifySetting();
                }
            }
    );

    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                batteryOptimizationLauncher.launch(intent);
            } else {
                Toast.makeText(this, "已处于电池优化白名单中", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==========新增：通知权限全套检测逻辑==========
    /**
     * 检查通知权限：Android13动态权限 + 系统通知总开关双重校验
     */
    private void checkNotificationPermission(){
        boolean sysNotifyEnable = NotificationManagerCompat.from(this).areNotificationsEnabled();
        boolean runtimeNotifyOk = true;

        // Android13+ 校验POST_NOTIFICATIONS运行时权限
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            runtimeNotifyOk = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            if(!runtimeNotifyOk){
                // 申请通知权限
                notifyPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }

        // 系统通知总开关关闭，跳转设置
        if(!sysNotifyEnable){
            Toast.makeText(this,"应用通知已被系统关闭，请开启通知权限",Toast.LENGTH_LONG).show();
            gotoAppNotifySetting();
        }else{
            Toast.makeText(this,"通知权限正常",Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 跳转到本应用专属通知设置页面（全Android版本兼容）
     */
    private void gotoAppNotifySetting(){
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra("app_package", getPackageName());
            intent.putExtra("app_uid", getApplicationInfo().uid);
        } else {
            // 低版本跳转应用详情
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
        }
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        loadSettings();
        setupClickListeners();
        requestIgnoreBatteryOptimizations();
        checkNotificationPermission();
    }

    private void initViews() {
        etPackageList = findViewById(R.id.et_package_list);
        etReminderInterval = findViewById(R.id.et_reminder_interval);
        btnStartService = findViewById(R.id.btn_start_service);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("MoodReminderPrefs", Context.MODE_PRIVATE);
        Set<String> packages = prefs.getStringSet("package_list", null);
        if (packages != null) {
            StringBuilder sb = new StringBuilder();
            for (String pkg : packages) {
                sb.append(pkg).append(",");
            }
            if (sb.length() > 0) {
                sb.deleteCharAt(sb.length() - 1); // 移除最后一个逗号
            }
            etPackageList.setText(sb.toString());
        }

        long interval = prefs.getLong("reminder_interval", 60000);
        etReminderInterval.setText(String.valueOf(interval / 1000)); // 转换为秒显示
    }

    private void setupClickListeners() {
        btnStartService.setOnClickListener(v -> {
            saveSettings();
            startAccessibilityService();
        });
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences("MoodReminderPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // 保存包名列表
        String packageText = etPackageList.getText().toString();
        Set<String> packages = new HashSet<>();
        if (!packageText.isEmpty()) {
            String[] packageArray = packageText.split(",");
            for (String pkg : packageArray) {
                pkg = pkg.trim();
                if (!pkg.isEmpty()) {
                    packages.add(pkg);
                }
            }
        }
        editor.putStringSet("package_list", packages);

        // 保存提醒间隔（转换为毫秒）
        try {
            int intervalSeconds = Integer.parseInt(etReminderInterval.getText().toString());
            long intervalMs = intervalSeconds * 1000L;
            editor.putLong("reminder_interval", intervalMs);
        } catch (NumberFormatException e) {
            editor.putLong("reminder_interval", 60000); // 默认60秒
        }

        editor.apply();
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
    }

    private void startAccessibilityService() {
        // 检查无障碍服务是否已启用
        if (!isAccessibilityServiceEnabled()) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "请在设置中启用无障碍服务", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "无障碍服务已在运行", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + MoodReminderService.class.getCanonicalName();
        String enabledServices = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(service);
    }
}