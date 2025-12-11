package io.github.trojan_gfw.igniter;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import io.github.trojan_gfw.igniter.common.constants.Constants;
import io.github.trojan_gfw.igniter.common.os.Task;
import io.github.trojan_gfw.igniter.common.os.Threads;
import io.github.trojan_gfw.igniter.common.utils.AnimationUtils;
import io.github.trojan_gfw.igniter.common.utils.DisplayUtils;
import io.github.trojan_gfw.igniter.common.utils.PreferenceUtils;
import io.github.trojan_gfw.igniter.common.utils.SnackbarUtils;
import io.github.trojan_gfw.igniter.connection.TrojanConnection;
import io.github.trojan_gfw.igniter.exempt.activity.ExemptAppActivity;
import io.github.trojan_gfw.igniter.proxy.aidl.ITrojanService;
import io.github.trojan_gfw.igniter.servers.activity.ServerListActivity;
import io.github.trojan_gfw.igniter.servers.data.ServerListDataManager;
import io.github.trojan_gfw.igniter.servers.data.ServerListDataSource;
import io.github.trojan_gfw.igniter.settings.activity.SettingsActivity;
import io.github.trojan_gfw.igniter.tile.ProxyHelper;


public class MainActivity extends io.github.trojan_gfw.igniter.common.app.BaseAppCompatActivity implements TrojanConnection.Callback {
    private static final String TAG = "MainActivity";
    private static final long INVALID_PORT = -1L;
    private static final String CONNECTION_TEST_URL = "https://www.google.com";

    private String shareLink;
    private ViewGroup rootViewGroup;
    private EditText remoteServerRemarkText;
    private EditText remoteAddrText;
    private EditText remoteServerSNIText;
    private EditText remotePortText;
    private EditText passwordText;
    private Switch ipv6Switch;
    private Switch verifySwitch;
    private Switch clashSwitch;
    private Switch allowLanSwitch;
    private Switch autoModeSwitch;
    private Button startStopButton, copyPortBtn;
    
    // 自动模式相关UI
    private ViewGroup currentServerLayout;
    private android.widget.TextView currentServerNameText;
    private android.widget.TextView connectionStatusText;
    private android.widget.TextView serverDelayText;
    
    // 自动模式管理器
    private AutoModeManager autoModeManager;
    private EditText trojanURLText;
    private @ProxyService.ProxyState
    int proxyState = ProxyService.STATE_NONE;
    private long currentProxyPort;
    private final TrojanConnection connection = new TrojanConnection(false);
    private final Object lock = new Object();
    private volatile ITrojanService trojanService;
    private ServerListDataSource serverListDataManager;
    private AlertDialog linkDialog;
    private ActivityResultLauncher<Intent> goToServerListActivityResultLauncher;
    private ActivityResultLauncher<Intent> exemptAppSettingsActivityResultLauncher;
    private ActivityResultLauncher<Intent> startProxyActivityResultLauncher;

    private TextViewListener remoteServerRemarkTextListener = new TextViewListener() {
        @Override
        protected void onTextChanged(String before, String old, String aNew, String after) {
            // update TextView
            startUpdates(); // to prevent infinite loop.
            if (remoteServerRemarkText.hasFocus()) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                ins.setRemoteServerRemark(remoteServerRemarkText.getText().toString());
            }
            endUpdates();
        }
    };

    private TextViewListener remoteAddrTextListener = new TextViewListener() {
        @Override
        protected void onTextChanged(String before, String old, String aNew, String after) {
            // update TextView
            startUpdates(); // to prevent infinite loop.
            if (remoteAddrText.hasFocus()) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                String remoteAddrRawStr = remoteAddrText.getText().toString();
                ins.setRemoteAddr(remoteAddrRawStr.trim());
            }
            endUpdates();
        }
    };

    private TextViewListener remoteServerSNITextListener = new TextViewListener() {
        @Override
        protected void onTextChanged(String before, String old, String aNew, String after) {
            // update TextView
            startUpdates(); // to prevent infinite loop.
            if (remoteServerSNIText.hasFocus()) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                String remoteServerSNIRawStr = remoteServerSNIText.getText().toString();
                ins.setSNI(remoteServerSNIRawStr.trim());
            }
            endUpdates();
        }
    };

    private TextViewListener remotePortTextListener = new TextViewListener() {
        @Override
        protected void onTextChanged(String before, String old, String aNew, String after) {
            // update TextView
            startUpdates(); // to prevent infinite loop.
            if (remotePortText.hasFocus()) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                String portStr = remotePortText.getText().toString();
                try {
                    int port = Integer.parseInt(portStr);
                    ins.setRemotePort(port);
                } catch (NumberFormatException e) {
                    // Ignore when we get invalid number
                    e.printStackTrace();
                }
            }
            endUpdates();
        }
    };
    private TextViewListener passwordTextListener = new TextViewListener() {
        @Override
        protected void onTextChanged(String before, String old, String aNew, String after) {
            // update TextView
            startUpdates(); // to prevent infinite loop.
            if (passwordText.hasFocus()) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                ins.setPassword(passwordText.getText().toString());
            }
            endUpdates();
        }
    };

    private void copyRawResourceToDir(int resId, String destPathName, boolean override) {
        File file = new File(destPathName);
        if (override || !file.exists()) {
            try {
                try (InputStream is = getResources().openRawResource(resId);
                     FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateViews(int state) {
        proxyState = state;
        boolean inputEnabled;
        switch (state) {
            case ProxyService.STARTING: {
                inputEnabled = false;
                startStopButton.setText(R.string.button_service__starting);
                startStopButton.setEnabled(false);
                break;
            }
            case ProxyService.STARTED: {
                inputEnabled = false;
                startStopButton.setText(R.string.button_service__stop);
                startStopButton.setEnabled(true);
                break;
            }
            case ProxyService.STOPPING: {
                inputEnabled = false;
                startStopButton.setText(R.string.button_service__stopping);
                startStopButton.setEnabled(false);
                break;
            }
            default: {
                inputEnabled = true;
                startStopButton.setText(R.string.button_service__start);
                startStopButton.setEnabled(true);
                break;
            }
        }
        remoteServerRemarkText.setEnabled(inputEnabled);
        remoteAddrText.setEnabled(inputEnabled);
        remoteServerSNIText.setEnabled(inputEnabled);
        remotePortText.setEnabled(inputEnabled);
        ipv6Switch.setEnabled(inputEnabled);
        passwordText.setEnabled(inputEnabled);
        verifySwitch.setEnabled(inputEnabled);
        clashSwitch.setEnabled(inputEnabled);
        allowLanSwitch.setEnabled(inputEnabled);
    }

    private void applyConfigInstance(TrojanConfig config) {
        TrojanConfig ins = Globals.getTrojanConfigInstance();
        if (config != null) {
            String remoteServerRemark = config.getRemoteServerRemark();
            String remoteAddress = config.getRemoteAddr();
            String remoteServerSNI = config.getSNI();
            int remotePort = config.getRemotePort();
            String password = config.getPassword();
            boolean verifyCert = config.getVerifyCert();
            boolean enableIpv6 = config.getEnableIpv6();

            ins.setRemoteServerRemark(remoteServerRemark);
            ins.setSNI(remoteServerSNI);
            ins.setRemoteAddr(remoteAddress);
            ins.setRemotePort(remotePort);
            ins.setPassword(password);
            ins.setVerifyCert(verifyCert);
            ins.setEnableIpv6(enableIpv6);

            remoteServerRemarkText.setText(remoteServerRemark);
            remoteServerSNIText.setText(remoteServerSNI);
            passwordText.setText(password);
            remotePortText.setText(String.valueOf(remotePort));
            remoteAddrText.setText(remoteAddress);
            remoteAddrText.setSelection(remoteAddrText.length());
            verifySwitch.setChecked(verifyCert);
            ipv6Switch.setChecked(enableIpv6);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check native library loading
        if (!JNIHelper.isLibraryLoaded()) {
            LogHelper.e("MainActivity", "Native library failed to load");
            Toast.makeText(this, "Native library failed to load. Please reinstall the app.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // Test native library
        String testResult = JNIHelper.testNativeLibrary();
        LogHelper.i("MainActivity", "Native library test: " + testResult);
        
        // Request notification permission for Android 13+
        requestNotificationPermission();
        
        final int screenWidth = DisplayUtils.getScreenWidth();
        if (screenWidth >= 1080) {
            setContentView(R.layout.activity_main);
        } else {
            setContentView(R.layout.activity_main_720);
        }

        goToServerListActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        Intent data = result.getData();
                        if (result.getResultCode() == RESULT_OK && data != null) {
                            shareLink = "";
                            final TrojanConfig selectedConfig = data.getParcelableExtra(ServerListActivity.KEY_TROJAN_CONFIG);
                            if (selectedConfig != null) {
                                LogHelper.e("gotoServer: ", selectedConfig.toString());

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        TrojanConfig ins = Globals.getTrojanConfigInstance();
                                        ins.setRemoteServerRemark(selectedConfig.getRemoteServerRemark());
                                        ins.setRemoteAddr(selectedConfig.getRemoteAddr());
                                        ins.setSNI(selectedConfig.getSNI());
                                        ins.setRemotePort(selectedConfig.getRemotePort());
                                        ins.setPassword(selectedConfig.getPassword());
                                        ins.setEnableIpv6(selectedConfig.getEnableIpv6());
                                        ins.setVerifyCert(selectedConfig.getVerifyCert());
                                        TrojanHelper.WriteTrojanConfig(Globals.getTrojanConfigInstance(), Globals.getTrojanConfigPath());
                                        applyConfigInstance(ins);
                                        
                                        // 如果自动模式启用，更新当前服务器
                                        if (autoModeManager != null && autoModeManager.isAutoModeEnabled()) {
                                            autoModeManager.setCurrentServer(selectedConfig.getIdentifier());
                                        }
                                    }
                                });
                                shareLink = TrojanURLHelper.GenerateTrojanURL(selectedConfig);
                            }
                        }
                    }
                });

        exemptAppSettingsActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK) {
                            if (ProxyService.STARTED == proxyState) {
                                SnackbarUtils.showTextLong(rootViewGroup, R.string.main_restart_proxy_service_tip);
                            }
                        }
                    }
                });

        startProxyActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK) {
                            LogHelper.i("MainActivity", "VPN permission granted, starting service");
                            ProxyHelper.startProxyService(getApplicationContext());
                        } else {
                            LogHelper.w("MainActivity", "VPN permission denied");
                            Toast.makeText(MainActivity.this, "VPN permission is required to start the service", Toast.LENGTH_LONG).show();
                        }
                    }
                });

        rootViewGroup = findViewById(R.id.rootScrollView);
        remoteServerRemarkText = findViewById(R.id.remoteServerRemarkText);
        remoteAddrText = findViewById(R.id.remoteAddrText);
        remoteServerSNIText = findViewById(R.id.remoteServerSNIText);
        remotePortText = findViewById(R.id.remotePortText);
        passwordText = findViewById(R.id.passwordText);
        ipv6Switch = findViewById(R.id.ipv6Switch);
        verifySwitch = findViewById(R.id.verifySwitch);
        clashSwitch = findViewById(R.id.clashSwitch);
        allowLanSwitch = findViewById(R.id.allowLanSwitch);
        autoModeSwitch = findViewById(R.id.autoModeSwitch);
        startStopButton = findViewById(R.id.startStopButton);
        copyPortBtn = findViewById(R.id.copyPortBtn);
        
        // 自动模式UI
        currentServerLayout = findViewById(R.id.currentServerLayout);
        currentServerNameText = findViewById(R.id.currentServerNameText);
        connectionStatusText = findViewById(R.id.connectionStatusText);
        serverDelayText = findViewById(R.id.serverDelayText);



        copyRawResourceToDir(R.raw.cacert, Globals.getCaCertPath(), true);
        copyRawResourceToDir(R.raw.country, Globals.getCountryMmdbPath(), true);
        copyRawResourceToDir(R.raw.clash_config, Globals.getClashConfigPath(), false);

        remoteServerRemarkText.addTextChangedListener(remoteServerRemarkTextListener);

        remoteAddrText.addTextChangedListener(remoteAddrTextListener);

        remoteServerSNIText.addTextChangedListener(remoteServerSNITextListener);

        remotePortText.addTextChangedListener(remotePortTextListener);

        passwordText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    passwordText.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
                } else {
                    // place cursor on the end
                    passwordText.setInputType(EditorInfo.TYPE_CLASS_TEXT);
                    passwordText.setSelection(passwordText.getText().length());
                }
            }
        });

        boolean enableClash = PreferenceUtils.getBooleanPreference(getContentResolver(),
                Uri.parse(Constants.PREFERENCE_URI), Constants.PREFERENCE_KEY_ENABLE_CLASH, true);
        clashSwitch.setChecked(enableClash);
        clashSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Generally speaking, it's better to insert content into ContentProvider in background
                // thread, but that may cause data inconsistency when user starts proxy right after
                // switching.
                PreferenceUtils.putBooleanPreference(getContentResolver(),
                        Uri.parse(Constants.PREFERENCE_URI), Constants.PREFERENCE_KEY_ENABLE_CLASH,
                        isChecked);
            }
        });

        boolean allowLan = PreferenceUtils.getBooleanPreference(getContentResolver(),
                Uri.parse(Constants.PREFERENCE_URI), Constants.PREFERENCE_KEY_ALLOW_LAN, false);
        allowLanSwitch.setChecked(allowLan);
        allowLanSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Generally speaking, it's better to insert content into ContentProvider in background
                // thread, but that may cause data inconsistency when user starts proxy right after
                // switching.
                PreferenceUtils.putBooleanPreference(getContentResolver(),
                        Uri.parse(Constants.PREFERENCE_URI), Constants.PREFERENCE_KEY_ALLOW_LAN,
                        isChecked);
            }
        });

        passwordText.addTextChangedListener(passwordTextListener);

        ipv6Switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                ins.setEnableIpv6(isChecked);
            }
        });

        verifySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                ins.setVerifyCert(isChecked);
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Trojan URL");

        trojanURLText = new EditText(this);

        trojanURLText.setInputType(InputType.TYPE_CLASS_TEXT);
        trojanURLText.setSingleLine(false);
        trojanURLText.setSelectAllOnFocus(true);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = getResources().getDimensionPixelSize(R.dimen.dialog_margin);
        params.rightMargin = getResources().getDimensionPixelSize(R.dimen.dialog_margin);
        trojanURLText.setLayoutParams(params);
        container.addView(trojanURLText);
        builder.setView(container);

        builder.setPositiveButton(R.string.common_update, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TrojanURLParseResult parseResult = TrojanURLHelper.ParseTrojanURL(trojanURLText.getText().toString());
                if (parseResult != null) {
                    Globals.setTrojanConfigInstance(TrojanURLHelper.CombineTrojanURLParseResultToTrojanConfig(parseResult, Globals.getTrojanConfigInstance()));
                    applyConfigInstance(Globals.getTrojanConfigInstance());
                }
                dialog.cancel();
            }
        });
        builder.setNegativeButton(R.string.common_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        linkDialog = builder.create();

        TextViewListener trojanConfigChangedTextViewListener = new TextViewListener() {
            @Override
            protected void onTextChanged(String before, String old, String aNew, String after) {
                startUpdates();
                String str = TrojanURLHelper.GenerateTrojanURL(Globals.getTrojanConfigInstance());
                if (str != null) {
                    shareLink = str;
                }
                endUpdates();
            }
        };

        remoteAddrText.addTextChangedListener(trojanConfigChangedTextViewListener);
        remoteServerSNIText.addTextChangedListener(trojanConfigChangedTextViewListener);
        remotePortText.addTextChangedListener(trojanConfigChangedTextViewListener);
        passwordText.addTextChangedListener(trojanConfigChangedTextViewListener);

        startStopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!Globals.getTrojanConfigInstance().isValidRunningConfig()) {
                    Toast.makeText(MainActivity.this,
                            R.string.invalid_configuration,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (proxyState == ProxyService.STATE_NONE || proxyState == ProxyService.STOPPED) {
                    try {
                        TrojanHelper.WriteTrojanConfig(
                                Globals.getTrojanConfigInstance(),
                                Globals.getTrojanConfigPath()
                        );
                        TrojanHelper.ShowConfig(Globals.getTrojanConfigPath());
                        
                        // Check VPN permission
                        Intent i = VpnService.prepare(getApplicationContext());
                        if (i != null) {
                            LogHelper.i("MainActivity", "Requesting VPN permission");
                            startProxyActivityResultLauncher.launch(i);
                        } else {
                            LogHelper.i("MainActivity", "VPN permission already granted, starting service");
                            ProxyHelper.startProxyService(getApplicationContext());
                        }
                    } catch (Exception e) {
                        LogHelper.e("MainActivity", "Failed to start VPN: " + e.getMessage());
                        Toast.makeText(MainActivity.this, "Failed to start VPN: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else if (proxyState == ProxyService.STARTED) {
                    // stop ProxyService
                    ProxyHelper.stopProxyService(getApplicationContext());
                }
            }
        });
        serverListDataManager = new ServerListDataManager(Globals.getTrojanConfigListPath(), false, "", 0L);
        connection.connect(this, this);
        Threads.instance().runOnWorkThread(new Task() {
            @Override
            public void onRun() {
                PreferenceUtils.putBooleanPreference(getContentResolver(),
                        Uri.parse(Constants.PREFERENCE_URI),
                        Constants.PREFERENCE_KEY_FIRST_START, false);
            }
        });
        View horseIv = findViewById(R.id.imageView);
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                swayTheHorse();
                return true;
            }
        });
        horseIv.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        copyPortBtn.setOnClickListener(v-> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            String portStr = String.valueOf(currentProxyPort);
            ClipData data = ClipData.newPlainText("port", portStr);
            cm.setPrimaryClip(data);
            SnackbarUtils.showTextShort(rootViewGroup,
                    getString(R.string.main_proxy_port_copied_to_clipboard, portStr));
        });
        
        // 初始化自动模式管理器
        initAutoMode();
    }

    private void swayTheHorse() {
        View v = findViewById(R.id.imageView);
        v.clearAnimation();
        AnimationUtils.sway(v, 60f, 500L, 4f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkTrojanURLFromClipboard();
        checkServiceStatus();
        
        // 刷新自动模式显示
        refreshAutoModeDisplay();
    }
    
    /**
     * 刷新自动模式显示
     */
    private void refreshAutoModeDisplay() {
        if (autoModeManager != null && autoModeManager.isAutoModeEnabled()) {
            String currentServerId = autoModeManager.getCurrentServerId();
            if (currentServerId != null) {
                LogHelper.i(TAG, "Refreshing auto mode display for: " + currentServerId);
                updateCurrentServerDisplay(currentServerId);
                // 强制刷新连接状态
                forceRefreshAutoModeConnectionStatus();
            }
        }
    }
    
    /**
     * 更新自动模式的连接状态显示
     */
    private void updateAutoModeConnectionStatus(int state) {
        if (autoModeManager != null && autoModeManager.isAutoModeEnabled() && 
            currentServerLayout.getVisibility() == View.VISIBLE) {
            
            LogHelper.i(TAG, "Updating auto mode connection status: " + state);
            
            switch (state) {
                case ProxyService.STARTING:
                    connectionStatusText.setText(R.string.connecting);
                    connectionStatusText.setTextColor(getColor(android.R.color.holo_orange_dark));
                    break;
                case ProxyService.STARTED:
                    connectionStatusText.setText(R.string.connected);
                    connectionStatusText.setTextColor(getColor(android.R.color.holo_green_dark));
                    break;
                case ProxyService.STOPPING:
                    connectionStatusText.setText(R.string.disconnecting);
                    connectionStatusText.setTextColor(getColor(android.R.color.holo_orange_dark));
                    break;
                case ProxyService.STOPPED:
                case ProxyService.STATE_NONE:
                default:
                    connectionStatusText.setText(R.string.disconnected);
                    connectionStatusText.setTextColor(getColor(android.R.color.holo_red_dark));
                    break;
            }
        }
    }
    
    /**
     * 强制刷新自动模式连接状态显示
     */
    private void forceRefreshAutoModeConnectionStatus() {
        if (autoModeManager != null && autoModeManager.isAutoModeEnabled()) {
            // 获取当前实际的服务状态
            ITrojanService service;
            synchronized (lock) {
                service = trojanService;
            }
            
            if (service != null) {
                try {
                    int actualState = service.getState();
                    LogHelper.i(TAG, "Force refreshing auto mode status - actual state: " + actualState + ", cached state: " + proxyState);
                    updateAutoModeConnectionStatus(actualState);
                    // 同步更新缓存的状态
                    proxyState = actualState;
                } catch (RemoteException e) {
                    LogHelper.e(TAG, "Failed to get service state: " + e.getMessage());
                    updateAutoModeConnectionStatus(proxyState);
                }
            } else {
                LogHelper.w(TAG, "Service not connected, using cached state: " + proxyState);
                updateAutoModeConnectionStatus(proxyState);
            }
        }
    }

    private void checkTrojanURLFromClipboard() {
        Threads.instance().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (!clipboardManager.hasPrimaryClip()) {
                    return;
                }
                ClipData clipData = clipboardManager.getPrimaryClip();
                // check clipboard
                if (clipData == null || clipData.getItemCount() == 0) {
                    return;
                }
                final CharSequence clipboardText = clipData.getItemAt(0).coerceToText(MainActivity.this);
                // check scheme
                TrojanURLParseResult parseResult = TrojanURLHelper.ParseTrojanURL(clipboardText.toString());
                if (parseResult == null) {
                    return;
                }

                // show once if trojan url
                if (clipboardManager.hasPrimaryClip()) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(R.string.clipboard_import_tip)
                        .setPositiveButton(R.string.common_confirm, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                TrojanConfig newConfig = TrojanURLHelper.CombineTrojanURLParseResultToTrojanConfig(parseResult, Globals.getTrojanConfigInstance());
                                Globals.setTrojanConfigInstance(newConfig);
                                applyConfigInstance(newConfig);
                            }
                        })
                        .setNegativeButton(R.string.common_cancel, null)
                        .create()
                        .show();
            }
        });
    }

    @UiThread
    private void updatePortInfo(long port) {
        currentProxyPort = port;
        if (port >= 0L && port <= 65535) {
            copyPortBtn.setText(getString(R.string.notification_listen_port, String.valueOf(port)));
            copyPortBtn.setVisibility(View.VISIBLE);
        } else {
            copyPortBtn.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onServiceConnected(final ITrojanService service) {
        LogHelper.i(TAG, "onServiceConnected");
        synchronized (lock) {
            trojanService = service;
        }
        Threads.instance().runOnWorkThread(new Task() {
            @Override
            public void onRun() {
                try {
                    final int state = service.getState();
                    final long port = service.getProxyPort();
                    runOnUiThread(() -> {
                        updateViews(state);
                        updateAutoModeConnectionStatus(state);
                        // 服务连接后，强制刷新自动模式状态
                        if (autoModeManager != null && autoModeManager.isAutoModeEnabled()) {
                            forceRefreshAutoModeConnectionStatus();
                        }
                        if (ProxyService.STARTED == state || ProxyService.STARTING == state) {
                            updatePortInfo(port);
                        } else {
                            updatePortInfo(INVALID_PORT);
                        }
                    });
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onServiceDisconnected() {
        LogHelper.i(TAG, "onServiceDisconnected");
        synchronized (lock) {
            trojanService = null;
        }
        runOnUiThread(() -> {
            updatePortInfo(INVALID_PORT);
            updateAutoModeConnectionStatus(ProxyService.STOPPED);
        });
    }

    @Override
    public void onStateChanged(int state, String msg) {
        LogHelper.i(TAG, "onStateChanged# state: " + state + " msg: " + msg);
        updateViews(state);
        
        // 更新自动模式显示的连接状态
        updateAutoModeConnectionStatus(state);
        
        try {
            JSONObject msgJson = new JSONObject(msg);
            long port = msgJson.optLong(ProxyService.STATE_MSG_KEY_PORT, INVALID_PORT);
            updatePortInfo(port);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTestResult(final String testUrl, final boolean connected, final long delay, @NonNull final String error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showTestConnectionResult(testUrl, connected, delay, error);
                // Save test result to current config
                saveTestResultToCurrentConfig(connected, delay, error);
            }
        });
    }

    private void showTestConnectionResult(String testUrl, boolean connected, long delay, @NonNull String error) {
        if (connected) {
            Toast.makeText(getApplicationContext(), getString(R.string.connected_to__in__ms,
                    testUrl, String.valueOf(delay)), Toast.LENGTH_LONG).show();
        } else {
            LogHelper.e(TAG, "TestError: " + error);
            Toast.makeText(getApplicationContext(),
                    getString(R.string.failed_to_connect_to__,
                            testUrl, error),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void saveTestResultToCurrentConfig(boolean connected, long delay, String error) {
        try {
            TrojanConfig currentConfig = Globals.getTrojanConfigInstance();
            if (currentConfig != null) {
                String serverIdentifier = currentConfig.getIdentifier();
                
                // 使用TestResultManager保存测试结果到独立文件
                TestResultManager.getInstance().saveTestResult(serverIdentifier, connected, delay, error);
                
                LogHelper.i(TAG, "Test result saved for " + serverIdentifier + ": connected=" + connected + ", delay=" + delay + "ms");
            }
        } catch (Exception e) {
            LogHelper.e(TAG, "Failed to save test result: " + e.getMessage());
        }
    }

    @Override
    public void onBinderDied() {
        LogHelper.i(TAG, "onBinderDied");
        connection.disconnect(this);
        // connect the new binder
        // todo is it necessary to re-connect?
        connection.connect(this, this);
    }

    /**
     * Test connection by invoking {@link ITrojanService#testConnection(String)}. Since {@link ITrojanService}
     * is from remote process, a {@link RemoteException} might be thrown. Test result will be delivered
     * to {@link #onTestResult(String, boolean, long, String)} by {@link TrojanConnection}.
     */
    private void testConnection() {
        ITrojanService service;
        synchronized (lock) {
            service = trojanService;
        }
        if (service == null) {
            showTestConnectionResult(CONNECTION_TEST_URL, false, 0L, getString(R.string.trojan_service_not_available));
        } else {
            try {
                service.testConnection(CONNECTION_TEST_URL);
            } catch (RemoteException e) {
                showTestConnectionResult(CONNECTION_TEST_URL, false, 0L, getString(R.string.trojan_service_error));
                e.printStackTrace();
            }
        }
    }

    /**
     * Show develop info in Logcat by invoking {@link ITrojanService#showDevelopInfoInLogcat}. Since {@link ITrojanService}
     * is from remote process, a {@link RemoteException} might be thrown.
     */
    private void showDevelopInfoInLogcat() {
        ITrojanService service;
        synchronized (lock) {
            service = trojanService;
        }
        if (service != null) {
            try {
                service.showDevelopInfoInLogcat();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void clearEditTextFocus() {
        remoteServerRemarkText.clearFocus();
        remoteAddrText.clearFocus();
        remoteServerSNIText.clearFocus();
        remotePortText.clearFocus();
        passwordText.clearFocus();
    }

    private void showSaveConfigResult(final boolean success) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        success ? R.string.main_save_success : R.string.main_save_failed,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Bind menu items to their relative actions
        switch (item.getItemId()) {
            case R.id.action_test_connection:
                testConnection();
                return true;
            case R.id.action_show_develop_info_logcat:
                // log of this process
                LogHelper.showDevelopInfoInLogcat();
                // log of other processes
                showDevelopInfoInLogcat();
                return true;
            case R.id.action_save_profile:
                if (!Globals.getTrojanConfigInstance().isValidRunningConfig()) {
                    Toast.makeText(MainActivity.this, R.string.invalid_configuration, Toast.LENGTH_SHORT).show();
                    return true;
                }
                Threads.instance().runOnWorkThread(new Task() {
                    @Override
                    public void onRun() {
                        TrojanConfig config = Globals.getTrojanConfigInstance();
                        TrojanHelper.WriteTrojanConfig(config, Globals.getTrojanConfigPath());
                        serverListDataManager.saveServerConfig(config);
                        showSaveConfigResult(true);
                    }
                });
                return true;
            case R.id.action_view_server_list:
                gotoServerList();
                return true;
            case R.id.action_about:
                clearEditTextFocus();
                startActivity(AboutActivity.create(MainActivity.this));
                return true;
            case R.id.action_share_link:
                trojanURLText.setText(shareLink);
                linkDialog.show();
                trojanURLText.selectAll();
                return true;
            case R.id.action_exempt_app:
                exemptAppSettingsActivityResultLauncher.launch(ExemptAppActivity.create(this));
                return true;
            case R.id.action_settings:
                startActivity(SettingsActivity.create(this));
                return true;
            default:
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    private void gotoServerList() {
        clearEditTextFocus();

        boolean proxyOn = false;
        String proxyHost = null;
        long proxyPort = 0L;
        ITrojanService service;
        synchronized (lock) {
            service = trojanService;
        }
        if (service != null) {
            try {
                proxyOn = service.getState() == ProxyService.STARTED;
                proxyHost = service.getProxyHost();
                proxyPort = service.getProxyPort();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        goToServerListActivityResultLauncher.launch(ServerListActivity.create(MainActivity.this,
                proxyOn, proxyHost, proxyPort));
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        TrojanConfig cachedConfig = TrojanHelper.readTrojanConfig(Globals.getTrojanConfigPath());
        if (cachedConfig != null) {
            applyConfigInstance(cachedConfig);
            
            // 显示当前配置的测试结果（如果有的话）
            String testResult = cachedConfig.getFormattedTestResult();
            LogHelper.i(TAG, "Current config test result: " + testResult);
        }
        
        // 初始化TestResultManager并显示统计信息
        TestResultManager manager = TestResultManager.getInstance();
        LogHelper.i(TAG, "Test results loaded: " + manager.getTestResultCount() + 
                   " total, " + manager.getValidTestResultCount() + " valid");
        
        // 调试：显示所有测试结果
        debugPrintAllTestResults();
    }



    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, 
                                         @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                LogHelper.i("MainActivity", "Notification permission granted");
            } else {
                LogHelper.w("MainActivity", "Notification permission denied");
                // Show a message to user about the importance of notification permission
                SnackbarUtils.showTextLong(rootViewGroup, "Notification permission is required for VPN service status");
            }
        }
    }

    private void checkServiceStatus() {
        LogHelper.i("MainActivity", "Current proxy state: " + proxyState);
        LogHelper.i("MainActivity", "Current proxy port: " + currentProxyPort);
        
        // Check if service is actually running
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        boolean serviceRunning = false;
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (ProxyService.class.getName().equals(service.service.getClassName())) {
                serviceRunning = true;
                break;
            }
        }
        LogHelper.i("MainActivity", "ProxyService actually running: " + serviceRunning);
    }



    /**
     * 初始化自动模式
     */
    private void initAutoMode() {
        autoModeManager = AutoModeManager.getInstance(this);
        
        // 设置自动模式开关状态
        autoModeSwitch.setChecked(autoModeManager.isAutoModeEnabled());
        updateAutoModeUI(autoModeManager.isAutoModeEnabled());
        
        // 自动模式开关监听器
        autoModeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                autoModeManager.setAutoModeEnabled(isChecked);
                updateAutoModeUI(isChecked);
                
                if (isChecked) {
                    // 启用自动模式时，如果当前有服务器配置，设置为当前服务器
                    TrojanConfig currentConfig = Globals.getTrojanConfigInstance();
                    if (currentConfig != null && currentConfig.isValidRunningConfig()) {
                        autoModeManager.setCurrentServer(currentConfig.getIdentifier());
                    }
                    // 强制刷新连接状态显示
                    forceRefreshAutoModeConnectionStatus();
                }
            }
        });
        
        // 设置自动模式监听器
        autoModeManager.setListener(new AutoModeManager.AutoModeListener() {
            @Override
            public void onAutoModeStateChanged(boolean enabled) {
                runOnUiThread(() -> {
                    autoModeSwitch.setChecked(enabled);
                    updateAutoModeUI(enabled);
                });
            }
            
            @Override
            public void onServerChanged(String serverId) {
                runOnUiThread(() -> updateCurrentServerDisplay(serverId));
            }
            
            @Override
            public void onServerSwitched(String fromServerId, String toServerId) {
                runOnUiThread(() -> {
                    updateCurrentServerDisplay(toServerId);
                    // 自动切换服务器配置
                    switchToServer(toServerId);
                    // 显示切换提示
                    Toast.makeText(MainActivity.this, 
                        getString(R.string.server_switched, getServerDisplayName(toServerId)), 
                        Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onConnectionSuccess(String serverId) {
                runOnUiThread(() -> {
                    // 连接成功时强制刷新状态和延迟
                    forceRefreshAutoModeConnectionStatus();
                    updateServerDelay(serverId);
                    LogHelper.i(TAG, "Auto mode connection success for: " + serverId);
                });
            }
            
            @Override
            public void onRetryConnection(String serverId, int retryCount) {
                runOnUiThread(() -> {
                    connectionStatusText.setText(getString(R.string.retrying_connection, retryCount, 3));
                    connectionStatusText.setTextColor(getColor(android.R.color.holo_orange_dark));
                });
            }
            
            @Override
            public void onNoAlternativeServer() {
                runOnUiThread(() -> {
                    connectionStatusText.setText(R.string.no_available_server);
                    connectionStatusText.setTextColor(getColor(android.R.color.holo_red_dark));
                });
            }
            
            @Override
            public void onServerTesting(String serverId) {
                runOnUiThread(() -> {
                    serverDelayText.setText(R.string.testing);
                    serverDelayText.setTextColor(getColor(android.R.color.holo_orange_dark));
                });
            }
            
            @Override
            public void onServerTestCompleted(String serverId, boolean success, long delay) {
                runOnUiThread(() -> updateServerDelay(serverId));
            }
        });
        
        // 如果自动模式已启用，更新当前服务器显示
        if (autoModeManager.isAutoModeEnabled()) {
            String currentServerId = autoModeManager.getCurrentServerId();
            if (currentServerId != null) {
                LogHelper.i(TAG, "Auto mode enabled, updating display for current server: " + currentServerId);
                updateCurrentServerDisplay(currentServerId);
                
                // 强制刷新延迟显示
                TestResult testResult = TestResultManager.getInstance().getTestResult(currentServerId);
                if (testResult != null) {
                    LogHelper.i(TAG, "Found existing test result for " + currentServerId + 
                               ": " + testResult.getFormattedResult());
                } else {
                    LogHelper.i(TAG, "No existing test result for " + currentServerId);
                }
            }
        }
    }
    
    /**
     * 更新自动模式UI显示
     */
    private void updateAutoModeUI(boolean enabled) {
        currentServerLayout.setVisibility(enabled ? View.VISIBLE : View.GONE);
        
        // 根据自动模式状态调整手动配置区域的可见性
        remoteServerRemarkText.setEnabled(!enabled);
        remoteAddrText.setEnabled(!enabled);
        remoteServerSNIText.setEnabled(!enabled);
        remotePortText.setEnabled(!enabled);
        passwordText.setEnabled(!enabled);
        ipv6Switch.setEnabled(!enabled);
        verifySwitch.setEnabled(!enabled);
        
        if (enabled) {
            // 自动模式下，隐藏手动配置区域
            remoteServerRemarkText.setAlpha(0.5f);
            remoteAddrText.setAlpha(0.5f);
            remoteServerSNIText.setAlpha(0.5f);
            remotePortText.setAlpha(0.5f);
            passwordText.setAlpha(0.5f);
            ipv6Switch.setAlpha(0.5f);
            verifySwitch.setAlpha(0.5f);
        } else {
            // 手动模式下，恢复手动配置区域
            remoteServerRemarkText.setAlpha(1.0f);
            remoteAddrText.setAlpha(1.0f);
            remoteServerSNIText.setAlpha(1.0f);
            remotePortText.setAlpha(1.0f);
            passwordText.setAlpha(1.0f);
            ipv6Switch.setAlpha(1.0f);
            verifySwitch.setAlpha(1.0f);
        }
    }
    
    /**
     * 更新当前服务器显示
     */
    private void updateCurrentServerDisplay(String serverId) {
        if (serverId == null) {
            currentServerNameText.setText(R.string.no_server_selected);
            connectionStatusText.setText(R.string.disconnected);
            connectionStatusText.setTextColor(getColor(android.R.color.darker_gray));
            serverDelayText.setText("");
            return;
        }
        
        String displayName = getServerDisplayName(serverId);
        currentServerNameText.setText(displayName);
        
        // 强制刷新连接状态 - 获取实际的服务状态
        forceRefreshAutoModeConnectionStatus();
        
        // 更新延迟显示 - 直接从TestResultManager获取
        updateServerDelay(serverId);
        
        LogHelper.d(TAG, "Updated server display for: " + serverId + 
                   ", display name: " + displayName + ", proxy state: " + proxyState);
    }
    
    /**
     * 更新服务器延迟显示
     */
    private void updateServerDelay(String serverId) {
        TestResult testResult = TestResultManager.getInstance().getTestResult(serverId);
        
        LogHelper.d(TAG, "Updating delay for server: " + serverId);
        if (testResult != null) {
            LogHelper.d(TAG, "Test result found - connected: " + testResult.isConnected() + 
                       ", delay: " + testResult.getDelay() + "ms, valid: " + testResult.isValid());
        } else {
            LogHelper.d(TAG, "No test result found for server: " + serverId);
        }
        
        if (testResult != null) {
            if (testResult.isConnected()) {
                // 显示延迟和测试时间
                String delayWithTime = testResult.getFormattedResultWithTime(this);
                serverDelayText.setText(delayWithTime);
                serverDelayText.setTextColor(getDelayColor(testResult.getDelay()));
                LogHelper.d(TAG, "Displayed delay with time: " + delayWithTime);
            } else {
                // 显示连接失败和测试时间
                String failedWithTime = testResult.getFormattedResultWithTime(this);
                serverDelayText.setText(failedWithTime);
                serverDelayText.setTextColor(getColor(android.R.color.holo_red_dark));
                LogHelper.d(TAG, "Displayed: connection failed with time");
            }
        } else {
            serverDelayText.setText(R.string.not_tested);
            serverDelayText.setTextColor(getColor(android.R.color.darker_gray));
            LogHelper.d(TAG, "Displayed: not tested");
        }
    }
    
    /**
     * 根据延迟获取颜色
     */
    private int getDelayColor(long delay) {
        if (delay < 100) {
            return getColor(android.R.color.holo_green_dark);
        } else if (delay < 300) {
            return getColor(android.R.color.holo_orange_dark);
        } else {
            return getColor(android.R.color.holo_red_dark);
        }
    }
    
    /**
     * 获取服务器显示名称
     */
    private String getServerDisplayName(String serverId) {
        try {
            ServerListDataManager dataManager = new ServerListDataManager(
                Globals.getTrojanConfigListPath(), false, "", 0L);
            List<TrojanConfig> servers = dataManager.loadServerConfigList();
            
            for (TrojanConfig server : servers) {
                if (server.getIdentifier().equals(serverId)) {
                    String remark = server.getRemoteServerRemark();
                    return !android.text.TextUtils.isEmpty(remark) ? remark : serverId;
                }
            }
        } catch (Exception e) {
            LogHelper.e(TAG, "Error getting server display name: " + e.getMessage());
        }
        return serverId;
    }
    
    /**
     * 切换到指定服务器
     */
    private void switchToServer(String serverId) {
        try {
            ServerListDataManager dataManager = new ServerListDataManager(
                Globals.getTrojanConfigListPath(), false, "", 0L);
            List<TrojanConfig> servers = dataManager.loadServerConfigList();
            
            for (TrojanConfig server : servers) {
                if (server.getIdentifier().equals(serverId)) {
                    // 更新全局配置
                    Globals.setTrojanConfigInstance(server);
                    TrojanHelper.WriteTrojanConfig(server, Globals.getTrojanConfigPath());
                    
                    // 更新UI显示
                    applyConfigInstance(server);
                    
                    // 如果当前已连接，重新连接
                    if (proxyState == ProxyService.STARTED) {
                        // 先断开当前连接
                        ProxyHelper.stopProxyService(getApplicationContext());
                        
                        // 延迟重新连接
                        new android.os.Handler().postDelayed(() -> {
                            try {
                                Intent i = VpnService.prepare(getApplicationContext());
                                if (i != null) {
                                    startProxyActivityResultLauncher.launch(i);
                                } else {
                                    ProxyHelper.startProxyService(getApplicationContext());
                                }
                            } catch (Exception e) {
                                LogHelper.e(TAG, "Failed to restart VPN: " + e.getMessage());
                            }
                        }, 1000);
                    }
                    
                    LogHelper.i(TAG, "Switched to server: " + serverId);
                    break;
                }
            }
        } catch (Exception e) {
            LogHelper.e(TAG, "Error switching to server: " + e.getMessage());
        }
    }

    /**
     * 调试：打印所有测试结果
     */
    private void debugPrintAllTestResults() {
        try {
            ServerListDataManager dataManager = new ServerListDataManager(
                Globals.getTrojanConfigListPath(), false, "", 0L);
            List<TrojanConfig> servers = dataManager.loadServerConfigList();
            
            LogHelper.i(TAG, "=== Debug: All Test Results ===");
            for (TrojanConfig server : servers) {
                String serverId = server.getIdentifier();
                TestResult result = TestResultManager.getInstance().getTestResult(serverId);
                if (result != null) {
                    LogHelper.i(TAG, "Server: " + serverId + " -> " + result.getFormattedResult() + 
                               " (valid: " + result.isValid() + ")");
                } else {
                    LogHelper.i(TAG, "Server: " + serverId + " -> No test result");
                }
            }
            LogHelper.i(TAG, "=== End Debug ===");
        } catch (Exception e) {
            LogHelper.e(TAG, "Error printing test results: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connection.disconnect(this);
        
        // 清理自动模式管理器
        if (autoModeManager != null) {
            autoModeManager.destroy();
        }
    }
}
