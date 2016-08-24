/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2016 halogenOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server;

import android.app.ActivityManagerNative;
import android.app.ActivityThread;
import android.app.IAlarmManager;
import android.app.INotificationManager;
import android.app.usage.UsageStatsManagerInternal;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources.Theme;
import android.os.Build;
import android.os.Environment;
import android.os.FactoryTest;
import android.os.IPowerManager;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.storage.IMountService;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Slog;
import android.view.WindowManager;
import android.webkit.WebViewFactory;

import com.android.internal.R;
import com.android.internal.os.BinderInternal;
import com.android.internal.os.RegionalizationEnvironment;
import com.android.internal.os.SamplingProfilerIntegration;
import com.android.server.accessibility.AccessibilityManagerService;
import com.android.server.accounts.AccountManagerService;
import com.android.server.am.ActivityManagerService;
import com.android.server.audio.AudioService;
import com.android.server.camera.CameraService;
import com.android.server.clipboard.ClipboardService;
import com.android.server.content.ContentService;
import com.android.server.devicepolicy.DevicePolicyManagerService;
import com.android.server.display.DisplayManagerService;
import com.android.server.dreams.DreamManagerService;
import com.android.server.fingerprint.FingerprintService;
import com.android.server.hdmi.HdmiControlService;
import com.android.server.gesture.GestureService;
import com.android.server.input.InputManagerService;
import com.android.server.job.JobSchedulerService;
import com.android.server.lights.LightsService;
import com.android.server.media.MediaRouterService;
import com.android.server.media.MediaSessionService;
import com.android.server.media.projection.MediaProjectionManagerService;
import com.android.server.net.NetworkPolicyManagerService;
import com.android.server.net.NetworkStatsService;
import com.android.server.notification.NotificationManagerService;
import com.android.server.os.RegionalizationService;
import com.android.server.os.SchedulingPolicyService;
import com.android.server.pm.BackgroundDexOptService;
import com.android.server.pm.Installer;
import com.android.server.pm.LauncherAppsService;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.UserManagerService;
import com.android.server.power.PowerManagerService;
import com.android.server.power.ShutdownThread;
import com.android.server.restrictions.RestrictionsManagerService;
import com.android.server.search.SearchManagerService;
import com.android.server.statusbar.StatusBarManagerService;
import com.android.server.storage.DeviceStorageMonitorService;
import com.android.server.telecom.TelecomLoaderService;
import com.android.server.trust.TrustManagerService;
import com.android.server.tv.TvInputManagerService;
import com.android.server.twilight.TwilightService;
import com.android.server.usage.UsageStatsService;
import com.android.server.usb.UsbService;
import com.android.server.wallpaper.WallpaperManagerService;
import com.android.server.webkit.WebViewUpdateService;
import com.android.server.wm.WindowManagerService;

import dalvik.system.VMRuntime;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public final class SystemServer {
    private static final String TAG = "SystemServer";

    private static final String
            ENCRYPTING_STATE = "trigger_restart_min_framework",
            ENCRYPTED_STATE  = "1";

    private static final long SNAPSHOT_INTERVAL = 60 * 60 * 1000; // 1hr

    // The earliest supported time.  We pick one day into 1970, to
    // give any timezone code room without going into negative time.
    private static final long EARLIEST_SUPPORTED_TIME = 86400 * 1000;

    /*
     * Implementation class names. TODO: Move them to a codegen class or load
     * them from the build system somehow.
     */
    private static final String
            // Services
            BACKUP_MANAGER_SERVICE_CLASS =              // Backup Manager
                "com.android.server.backup.BackupManagerService$Lifecycle",
            APPWIDGET_SERVICE_CLASS =                   // App Widget Service
                "com.android.server.appwidget.AppWidgetService",
            VOICE_RECOGNITION_MANAGER_SERVICE_CLASS =   // Voice Recognition Manager
                "com.android.server.voiceinteraction.VoiceInteractionManagerService",
            PRINT_MANAGER_SERVICE_CLASS =               // Print Manager
                "com.android.server.print.PrintManagerService",
            USB_SERVICE_CLASS =                         // USB Service
                "com.android.server.usb.UsbService$Lifecycle",
            MIDI_SERVICE_CLASS =                        // MIDI Service
                "com.android.server.midi.MidiService$Lifecycle",
            WIFI_SERVICE_CLASS =                        // WiFi Service
                "com.android.server.wifi.WifiService",
            WIFI_P2P_SERVICE_CLASS =                    // WiFi P2P Service
                "com.android.server.wifi.p2p.WifiP2pService",
            ETHERNET_SERVICE_CLASS =                    // Ethernet Service
                "com.android.server.ethernet.EthernetService",
            JOB_SCHEDULER_SERVICE_CLASS =               // Job Scheduler Service
                "com.android.server.job.JobSchedulerService",
            MOUNT_SERVICE_CLASS =                       // Mount Service
                "com.android.server.MountService$Lifecycle",
            WIFI_SCANNING_SERVICE_CLASS =               // WiFi Scanning Service
                "com.android.server.wifi.WifiScanningService",
            RTT_SERVICE_CLASS =                         // GPS RTT Clock Service
                "com.android.server.wifi.RttService",

            // Peristent data block property
            PERSISTENT_DATA_BLOCK_PROP = "ro.frp.pst"
            ;

    private final int mFactoryTestMode;
    private Timer mProfilerSnapshotTimer;

    private Context mSystemContext;
    private SystemServiceManager mSystemServiceManager;

    // TODO: remove all of these references by improving dependency resolution and boot phases
    private PowerManagerService mPowerManagerService;
    private ActivityManagerService mActivityManagerService;
    private DisplayManagerService mDisplayManagerService;
    private PackageManagerService mPackageManagerService;
    private PackageManager mPackageManager;

    private ContentResolver mContentResolver;

    private boolean mOnlyCore;
    private boolean mFirstBoot;

    /**
     * Start the sensor service.
     */
    private static native void startSensorService();

    /**
     * The main entry point from zygote.
     */
    public static void main(String[] args) {
        new SystemServer().run();
    }
    
    private static void slogi(String msg) {
        Slog.i(TAG, msg);
    }
    
    private static void slogw(String msg) {
        Slog.w(TAG, msg);
    }

    public SystemServer() {
        // Check for factory test mode.
        mFactoryTestMode = FactoryTest.getMode();
    }

    private void run() {
        // If a device's clock is before 1970 (before 0), a lot of
        // APIs crash dealing with negative numbers, notably
        // java.io.File#setLastModified, so instead we fake it and
        // hope that time from cell towers or NTP fixes it shortly.
        if (System.currentTimeMillis() < EARLIEST_SUPPORTED_TIME) {
            slogw("System clock is before 1970; setting to 1970.");
            SystemClock.setCurrentTimeMillis(EARLIEST_SUPPORTED_TIME);
        }
        
        try {
            // Pre-boot device-specific initialization
            if (!SystemProperties.get("ro.device.model_varies").isEmpty()) {
                Class modelInitClass = Class.forName("org.halogenos.hw.init.DeviceInit");
                Method method = modelInitClass.getMethod("initDevice", null);
                method.invoke(null, null);
            }
        } catch(Throwable e) {
            // Don't panic, this is optional
            e.printStackTrace();
            slogw("Could not find or call device-specific initialization." +
                " Skipping. (This is not fatal)");
        }

        // If the system has "persist.sys.language" and friends set, replace them with
        // "persist.sys.locale". Note that the default locale at this point is calculated
        // using the "-Duser.locale" command line flag. That flag is usually populated by
        // AndroidRuntime using the same set of system properties, but only the system_server
        // and system apps are allowed to set them.
        //
        // NOTE: Most changes made here will need an equivalent change to
        // core/jni/AndroidRuntime.cpp
        if (!SystemProperties.get("persist.sys.language").isEmpty()) {
            final String languageTag = Locale.getDefault().toLanguageTag();

            SystemProperties.set("persist.sys.locale", languageTag);
            SystemProperties.set("persist.sys.language", "");
            SystemProperties.set("persist.sys.country", "");
            SystemProperties.set("persist.sys.localevar", "");
        }

        // Here we go!
        slogi("Entered the Android system server!");
        slogi("Welcome to halogenOS! The system is booting. ROCK ON!");
        EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_SYSTEM_RUN, SystemClock.uptimeMillis());

        // In case the runtime switched since last boot (such as when
        // the old runtime was removed in an OTA), set the system
        // property so that it is in sync. We can't do this in
        // libnativehelper's JniInvocation::Init code where we already
        // had to fallback to a different runtime because it is
        // running as root and we need to be the system user to set
        // the property. http://b/11463182
        SystemProperties.set("persist.sys.dalvik.vm.lib.2", VMRuntime.getRuntime().vmLibrary());

        // Enable the sampling profiler.
        if (SamplingProfilerIntegration.isEnabled()) {
            SamplingProfilerIntegration.start();
            mProfilerSnapshotTimer = new Timer();
            mProfilerSnapshotTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    SamplingProfilerIntegration.writeSnapshot("system_server", null);
                }
            }, SNAPSHOT_INTERVAL, SNAPSHOT_INTERVAL);
        }

        // Mmmmmm... more memory!
        VMRuntime.getRuntime().clearGrowthLimit();

        // The system server has to run all of the time, so it needs to be
        // as efficient as possible with its memory usage.
        VMRuntime.getRuntime().setTargetHeapUtilization(0.8f);

        // Some devices rely on runtime fingerprint generation, so make sure
        // we've defined it before booting further.
        Build.ensureFingerprintProperty();

        // Within the system server, it is an error to access Environment paths without
        // explicitly specifying a user.
        Environment.setUserRequired(true);

        // Ensure binder calls into the system always run at foreground priority.
        BinderInternal.disableBackgroundScheduling(true);

        // Prepare the main looper thread (this thread).
        android.os.Process.setThreadPriority(
                android.os.Process.THREAD_PRIORITY_FOREGROUND);
        android.os.Process.setCanSelfBackground(false);
        Looper.prepareMainLooper();

        // Initialize native services.
        System.loadLibrary("android_servers");

        // Check whether we failed to shut down last time we tried.
        // This call may not return.
        performPendingShutdown();

        // Initialize the system context.
        createSystemContext();

        // Create the system service manager.
        mSystemServiceManager = new SystemServiceManager(mSystemContext);
        LocalServices.addService(SystemServiceManager.class, mSystemServiceManager);

        // Start services.
        try {
            startBootstrapServices();
            startCoreServices();
            startOtherServices();
        } catch (Throwable ex) {
            Slog.e("System", "******************************************"       );
            Slog.e("System", "************ Failure starting system services", ex);
            throw ex;
        }

        // For debug builds, log event loop stalls to dropbox for analysis.
        if (StrictMode.conditionallyEnableDebugLogging())
            slogi("Enabled StrictMode for system server main thread.");

        // Loop forever.
        Looper.loop();
        throw new RuntimeException("Main thread loop unexpectedly exited");
    }

    private void reportWtf(String msg, Throwable e) {
        slogw   (     "***********************************************");
        Slog.wtf(TAG, "BOOT FAILURE " + msg, e);
    }

    private void performPendingShutdown() {
        final String shutdownAction = SystemProperties.get(
                ShutdownThread.SHUTDOWN_ACTION_PROPERTY, "");
        if (shutdownAction != null && shutdownAction.length() > 0) {
            boolean reboot = (shutdownAction.charAt(0) == '1');

            final String reason;
            if (shutdownAction.length() > 1)
                reason = shutdownAction.substring(1, shutdownAction.length());
            else
                reason = null;

            ShutdownThread.rebootOrShutdown(null, reboot, reason);
        }
    }

    private void createSystemContext() {
        ActivityThread activityThread = ActivityThread.systemMain();
        mSystemContext = activityThread.getSystemContext();
        mSystemContext.setTheme(android.R.style.Theme_DeviceDefault_Light_DarkActionBar);
    }

    /**
     * Starts the small tangle of critical services that are needed to get
     * the system off the ground.  These services have complex mutual dependencies
     * which is why we initialize them all in one place here.  Unless your service
     * is also entwined in these dependencies, it should be initialized in one of
     * the other functions.
     */
    private void startBootstrapServices() {
        // Wait for installd to finish starting up so that it has a chance to
        // create critical directories such as /data/user with the appropriate
        // permissions.  We need this to complete before we initialize other services.
        Installer installer = mSystemServiceManager.startService(Installer.class);

        // Activity manager runs the show.
        mActivityManagerService = mSystemServiceManager.startService(
                ActivityManagerService.Lifecycle.class
        ).getService();
        mActivityManagerService.setSystemServiceManager(mSystemServiceManager);
        mActivityManagerService.setInstaller(installer);

        // Power manager needs to be started early because other services need it.
        // Native daemons may be watching for it to be registered so it must be ready
        // to handle incoming binder calls immediately (including being able to verify
        // the permissions for those calls).
        mPowerManagerService = mSystemServiceManager.startService(PowerManagerService.class);

        // Now that the power manager has been started, let the activity manager
        // initialize power management features.
        mActivityManagerService.initPowerManagement();

        // Manages LEDs and display backlight so we need it to bring up the display.
        mSystemServiceManager.startService(LightsService.class);

        // Display manager is needed to provide display metrics before package manager
        // starts up.
        mDisplayManagerService = mSystemServiceManager.startService(DisplayManagerService.class);

        // We need the default display before we can initialize the package manager.
        mSystemServiceManager.startBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        // Only run "core" apps if we're encrypting the device.
        String cryptState = SystemProperties.get("vold.decrypt");
        if (ENCRYPTING_STATE.equals(cryptState)) {
            slogw("Detected encryption in progress - only parsing core apps");
            mOnlyCore = true;
        } else if (ENCRYPTED_STATE.equals(cryptState)) {
            slogw("Device encrypted - only parsing core apps");
            mOnlyCore = true;
        }

        if (RegionalizationEnvironment.isSupported()) {
            slogi("Regionalization Service");
            RegionalizationService regionalizationService = new RegionalizationService();
            ServiceManager.addService("regionalization", regionalizationService);
        }

        // Start the package manager.
        slogi("Package Manager");
        mPackageManagerService = PackageManagerService.main(mSystemContext, installer,
                mFactoryTestMode != FactoryTest.FACTORY_TEST_OFF, mOnlyCore);
        mFirstBoot = mPackageManagerService.isFirstBoot();
        mPackageManager = mSystemContext.getPackageManager();

        slogi("User Service");
        ServiceManager.addService(Context.USER_SERVICE, UserManagerService.getInstance());

        // Initialize attribute cache used to cache resources from packages.
        AttributeCache.init(mSystemContext);

        // Set up the Application instance for the system process and get started.
        mActivityManagerService.setSystemProcess();

        // The sensor service needs access to package manager service, app ops
        // service, and permissions service, therefore we start it after them.
        startSensorService();
    }

    /**
     * Starts some essential services that are not tangled up in the bootstrap process.
     */
    private void startCoreServices() {
        // Tracks the battery level.  Requires LightService.
        mSystemServiceManager.startService(BatteryService.class);

        // Tracks application usage stats.
        mSystemServiceManager.startService(UsageStatsService.class);
        mActivityManagerService.setUsageStatsManager(
                LocalServices.getService(UsageStatsManagerInternal.class)
        );
        // Update after UsageStatsService is available, needed before performBootDexOpt.
        mPackageManagerService.getUsageStatsIfNoPackageUsageInfo();

        // Tracks whether the updatable WebView is in a ready state and watches for update installs.
        mSystemServiceManager.startService(WebViewUpdateService.class);
    }

    /**
     * Starts a miscellaneous grab bag of stuff that has yet to be refactored
     * and organized.
     */
    private void startOtherServices() {
        final Context context = mSystemContext;
        
        // Declare all services
        AccountManagerService accountManager                = null;
        ContentService contentService                       = null;
        VibratorService vibrator                            = null;
        IAlarmManager alarm                                 = null;
        IMountService mountService                          = null;
        NetworkManagementService networkManagement          = null;
        NetworkStatsService networkStats                    = null;
        NetworkPolicyManagerService networkPolicy           = null;
        ConnectivityService connectivity                    = null;
        NetworkScoreService networkScore                    = null;
        NsdService serviceDiscovery                         = null;
        WindowManagerService wm                             = null;
        UsbService usb                                      = null;
        SerialService serial                                = null;
        NetworkTimeUpdateService networkTimeUpdater         = null;
        CommonTimeManagementService commonTimeMgmtService   = null;
        InputManagerService inputManager                    = null;
        TelephonyRegistry telephonyRegistry                 = null;
        ConsumerIrService consumerIr                        = null;
        AudioService audioService                           = null;
        MmsServiceBroker mmsService                         = null;
        EntropyMixer entropyMixer                           = null;
        CameraService cameraService                         = null;
        StatusBarManagerService statusBar                   = null;
        INotificationManager notification                   = null;
        InputMethodManagerService imm                       = null;
        WallpaperManagerService wallpaper                   = null;
        LocationManagerService location                     = null;
        CountryDetectorService countryDetector              = null;
        TextServicesManagerService tsms                     = null;
        LockSettingsService lockSettings                    = null;
        AssetAtlasService atlas                             = null;
        MediaRouterService mediaRouter                      = null;
        GestureService gestureService                       = null;
        
        // Set necessary boolean variables
        boolean disableStorage = 
            SystemProperties.getBoolean("config.disable_storage", false);
        boolean disableBluetooth = 
            SystemProperties.getBoolean("config.disable_bluetooth", false);
        boolean disableLocation = 
            SystemProperties.getBoolean("config.disable_location", false);
        boolean disableSystemUI = 
            SystemProperties.getBoolean("config.disable_systemui", false);
        boolean disableNonCoreServices =
            SystemProperties.getBoolean("config.disable_noncore", false);
        boolean disableNetwork =
            SystemProperties.getBoolean("config.disable_network", false);
        boolean disableNetworkTime =
            SystemProperties.getBoolean("config.disable_networktime", false);
        boolean isEmulator =
            SystemProperties.get("ro.kernel.qemu").equals("1") ||
                SystemProperties.get("ro.hw.bt.nosupport").equals("1");
        boolean disableAtlas =
            SystemProperties.getBoolean("config.disable_atlas", true);
        
        // Try to start all services
        try {
            slogi("Reading configuration...");
            SystemConfig.getInstance();
            
            mContentResolver = context.getContentResolver();
        
            slogi("Scheduling Policy");
            ServiceManager.addService("scheduling_policy", new SchedulingPolicyService());
            mSystemServiceManager.startService(TelecomLoaderService.class);
        
            slogi("Telephony Registry");
            telephonyRegistry = new TelephonyRegistry(context);
            ServiceManager.addService("telephony.registry", telephonyRegistry);
        
            slogi("Entropy Mixer");
            entropyMixer = new EntropyMixer(context);
        
            slogi("Camera Service");
            mSystemServiceManager.startService(CameraService.class);
        
            // The AccountManager must come before the ContentService
            try {
                // TODO: seems like this should be disable-able, but req'd by ContentService
                slogi("Account Manager");
                accountManager = new AccountManagerService(context);
                ServiceManager.addService(Context.ACCOUNT_SERVICE, accountManager);
            } catch (Throwable e) {
                Slog.e(TAG, "Failure starting Account Manager", e);
            }
        
            slogi("Content Manager");
            contentService = ContentService.main(context,
                    mFactoryTestMode == FactoryTest.FACTORY_TEST_LOW_LEVEL);
        
            slogi("System Content Providers");
            mActivityManagerService.installSystemProviders();
        
            slogi("Vibrator Service");
            vibrator = new VibratorService(context);
            ServiceManager.addService("vibrator", vibrator);
        
            slogi("Consumer IR Service");
            consumerIr = new ConsumerIrService(context);
            ServiceManager.addService(Context.CONSUMER_IR_SERVICE, consumerIr);
        
            mSystemServiceManager.startService(AlarmManagerService.class);
            alarm = IAlarmManager.Stub.asInterface(
                    ServiceManager.getService(Context.ALARM_SERVICE)
            );
        
            slogi("Init Watchdog");
            final Watchdog watchdog = Watchdog.getInstance();
            watchdog.init(context, mActivityManagerService);
        
            slogi("Input Manager");
            inputManager = new InputManagerService(context);
        
            slogi("Window Manager");
            wm = WindowManagerService.main(context, inputManager,
                    mFactoryTestMode != FactoryTest.FACTORY_TEST_LOW_LEVEL,
                    true, mOnlyCore);
            ServiceManager.addService(Context.WINDOW_SERVICE, wm);
            ServiceManager.addService(Context.INPUT_SERVICE, inputManager);
        
            mActivityManagerService.setWindowManager(wm);
        
            inputManager.setWindowManagerCallbacks(wm.getInputMonitor());
            inputManager.start();
        
            // TODO: Use service dependencies instead.
            mDisplayManagerService.windowManagerAndInputReady();
        
            // Skip Bluetooth if we have an emulator kernel
            if (isEmulator) slogi("No Bluetooh Service (emulator)");
            
            else if (mFactoryTestMode == FactoryTest.FACTORY_TEST_LOW_LEVEL)
                slogi("No Bluetooth Service (factory test)");
            else if (!context.getPackageManager().hasSystemFeature
                       (PackageManager.FEATURE_BLUETOOTH))
                slogi("No Bluetooth Service (Bluetooth Hardware Not Present)");
            else if (disableBluetooth)
                slogi("Bluetooth Service disabled by config");
            else {
                slogi("Bluetooth Service");
                mSystemServiceManager.startService(BluetoothService.class);
            }
        } catch (RuntimeException e) {
            Slog.e("System", "******************************************");
            Slog.e("System", "************ Failure starting core services ", e);
        }
        
        // Bring up services needed for UI.
        if (mFactoryTestMode != FactoryTest.FACTORY_TEST_LOW_LEVEL) {
            try {
                slogi("Input Method Service");
                imm = new InputMethodManagerService(context, wm);
                ServiceManager.addService(Context.INPUT_METHOD_SERVICE, imm);
            } catch (Throwable e) {
                reportWtf("starting Input Manager Service", e);
            }
        
            try {
                slogi("Accessibility Manager");
                ServiceManager.addService(Context.ACCESSIBILITY_SERVICE,
                        new AccessibilityManagerService(context));
            } catch (Throwable e) {
                reportWtf("starting Accessibility Manager", e);
            }
        }
        
        try {
            wm.displayReady();
        } catch (Throwable e) {
            reportWtf("making display ready", e);
        }
        
        if (mFactoryTestMode != FactoryTest.FACTORY_TEST_LOW_LEVEL) {
            if (!disableStorage &&
                !"0".equals(SystemProperties.get("system_init.startmountservice"))) {
                try {
                    /*
                     * NotificationManagerService is dependant on MountService,
                     * (for media / usb notifications) so we must start MountService first.
                     */
                    mSystemServiceManager.startService(MOUNT_SERVICE_CLASS);
                    mountService = IMountService.Stub.asInterface(
                            ServiceManager.getService("mount"));
                } catch (Throwable e) {
                    reportWtf("starting Mount Service", e);
                }
            }
        }
        
        // We start this here so that we update our configuration to set watch or television
        // as appropriate.
        mSystemServiceManager.startService(UiModeManagerService.class);
        
        try {
            mPackageManagerService.performBootDexOpt();
        } catch (Throwable e) {
            reportWtf("performing boot dexopt", e);
        }
        
        try {
            ActivityManagerNative.getDefault().showBootMessage(
                    context.getResources().getText(
                            com.android.internal.R.string.android_upgrading_starting_apps
                    ),
                    false
            );
        } catch (RemoteException e) {
            slogw("Failed to show boot message");
        }
        
        if (mFactoryTestMode != FactoryTest.FACTORY_TEST_LOW_LEVEL) {
            if (!disableNonCoreServices) {
                try {
                    slogi( "LockSettingsService");
                    lockSettings = new LockSettingsService(context);
                    ServiceManager.addService("lock_settings", lockSettings);
                } catch (Throwable e) {
                    reportWtf("starting LockSettingsService service", e);
                }
        
                if (!SystemProperties.get(PERSISTENT_DATA_BLOCK_PROP).equals(""))
                    mSystemServiceManager.startService(PersistentDataBlockService.class);
        
                mSystemServiceManager.startService(DeviceIdleController.class);
        
                // Always start the Device Policy Manager, so that the API is compatible with
                // API8.
                mSystemServiceManager.startService(DevicePolicyManagerService.Lifecycle.class);
            }
        
            if (!disableSystemUI) {
                try {
                    slogi("Status Bar");
                    statusBar = new StatusBarManagerService(context, wm);
                    ServiceManager.addService(Context.STATUS_BAR_SERVICE, statusBar);
                } catch (Throwable e) {
                    reportWtf("starting StatusBarManagerService", e);
                }
            }
        
            if (!disableNonCoreServices) {
                try {
                    slogi("Clipboard Service");
                    ServiceManager.addService(Context.CLIPBOARD_SERVICE,
                            new ClipboardService(context));
                } catch (Throwable e) {
                    reportWtf("starting Clipboard Service", e);
                }
            }
        
            if (!disableNetwork) {
                try {
                    slogi("NetworkManagement Service");
                    networkManagement = NetworkManagementService.create(context);
                    ServiceManager.addService(
                        Context.NETWORKMANAGEMENT_SERVICE, networkManagement);
                } catch (Throwable e) {
                    reportWtf("starting NetworkManagement Service", e);
                }
            }
        
            if (!disableNonCoreServices) {
                try {
                    slogi("Text Service Manager Service");
                    tsms = new TextServicesManagerService(context);
                    ServiceManager.addService(Context.TEXT_SERVICES_MANAGER_SERVICE, tsms);
                } catch (Throwable e) {
                    reportWtf("starting Text Service Manager Service", e);
                }
            }
        
            if (!disableNetwork) {
                try {
                    slogi("Network Score Service");
                    networkScore = new NetworkScoreService(context);
                    ServiceManager.addService(Context.NETWORK_SCORE_SERVICE, networkScore);
                } catch (Throwable e) {
                    reportWtf("starting Network Score Service", e);
                }
        
                try {
                    slogi("NetworkStats Service");
                    networkStats = new NetworkStatsService(context, networkManagement, alarm);
                    ServiceManager.addService(Context.NETWORK_STATS_SERVICE, networkStats);
                } catch (Throwable e) {
                    reportWtf("starting NetworkStats Service", e);
                }
        
                try {
                    slogi("NetworkPolicy Service");
                    networkPolicy = new NetworkPolicyManagerService(
                            context, mActivityManagerService,
                            (IPowerManager)ServiceManager.getService(Context.POWER_SERVICE),
                            networkStats, networkManagement);
                    ServiceManager.addService(Context.NETWORK_POLICY_SERVICE, networkPolicy);
                } catch (Throwable e) {
                    reportWtf("starting NetworkPolicy Service", e);
                }
        
                mSystemServiceManager.startService(WIFI_P2P_SERVICE_CLASS);
                mSystemServiceManager.startService(WIFI_SERVICE_CLASS);
                mSystemServiceManager.startService(WIFI_SCANNING_SERVICE_CLASS);
        
                mSystemServiceManager.startService(RTT_SERVICE_CLASS);
        
                if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_ETHERNET) ||
                    mPackageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST))
                    mSystemServiceManager.startService(ETHERNET_SERVICE_CLASS);
        
                try {
                    slogi("Connectivity Service");
                    connectivity = new ConnectivityService(
                            context, networkManagement, networkStats, networkPolicy);
                    ServiceManager.addService(Context.CONNECTIVITY_SERVICE, connectivity);
                    networkStats.bindConnectivityManager(connectivity);
                    networkPolicy.bindConnectivityManager(connectivity);
                } catch (Throwable e) {
                    reportWtf("starting Connectivity Service", e);
                }
        
                try {
                    slogi("Network Service Discovery Service");
                    serviceDiscovery = NsdService.create(context);
                    ServiceManager.addService(
                            Context.NSD_SERVICE, serviceDiscovery);
                } catch (Throwable e) {
                    reportWtf("starting Service Discovery Service", e);
                }
            }
        
            if (!disableNonCoreServices) {
                try {
                    slogi("UpdateLock Service");
                    ServiceManager.addService(Context.UPDATE_LOCK_SERVICE,
                            new UpdateLockService(context));
                } catch (Throwable e) {
                    reportWtf("starting UpdateLockService", e);
                }
            }
        
            /*
             * MountService has a few dependencies: Notification Manager and
             * AppWidget Provider. Make sure MountService is completely started
             * first before continuing.
             */
            if (mountService != null && !mOnlyCore) {
                try {
                    mountService.waitForAsecScan();
                } catch (RemoteException ignored) {
                    slogw("Failure waiting for Android Secure scan");
                }
            }
        
            try {
                if (accountManager != null)
                    accountManager.systemReady();
            } catch (Throwable e) {
                reportWtf("making Account Manager Service ready", e);
            }
        
            try {
                if (contentService != null)
                    contentService.systemReady();
            } catch (Throwable e) {
                reportWtf("making Content Service ready", e);
            }
        
            mSystemServiceManager.startService(NotificationManagerService.class);
            notification = INotificationManager.Stub.asInterface(
                    ServiceManager.getService(Context.NOTIFICATION_SERVICE));
        
            networkPolicy.bindNotificationManager(notification);
        
            mSystemServiceManager.startService(DeviceStorageMonitorService.class);
        
            if (!disableLocation) {
                try {
                    slogi("Location Manager");
                    location = new LocationManagerService(context);
                    ServiceManager.addService(Context.LOCATION_SERVICE, location);
                } catch (Throwable e) {
                    reportWtf("starting Location Manager", e);
                }
        
                try {
                    slogi("Country Detector");
                    countryDetector = new CountryDetectorService(context);
                    ServiceManager.addService(Context.COUNTRY_DETECTOR, countryDetector);
                } catch (Throwable e) {
                    reportWtf("starting Country Detector", e);
                }
            }
        
            if (!disableNonCoreServices) {
                try {
                    slogi("Search Service");
                    ServiceManager.addService(Context.SEARCH_SERVICE,
                            new SearchManagerService(context));
                } catch (Throwable e) {
                    reportWtf("starting Search Service", e);
                }
            }
        
            try {
                slogi("DropBox Service");
                ServiceManager.addService(Context.DROPBOX_SERVICE,
                        new DropBoxManagerService(context, new File("/data/system/dropbox")));
            } catch (Throwable e) {
                reportWtf("starting DropBoxManagerService", e);
            }
        
            if (!disableNonCoreServices && context.getResources().getBoolean(
                        R.bool.config_enableWallpaperService)) {
                try {
                    slogi("Wallpaper Service");
                    wallpaper = new WallpaperManagerService(context);
                    ServiceManager.addService(Context.WALLPAPER_SERVICE, wallpaper);
                } catch (Throwable e) {
                    reportWtf("starting Wallpaper Service", e);
                }
            }
        
            try {
                slogi("Audio Service");
                audioService = new AudioService(context);
                ServiceManager.addService(Context.AUDIO_SERVICE, audioService);
            } catch (Throwable e) {
                reportWtf("starting Audio Service", e);
            }
        
            if (!disableNonCoreServices)
                mSystemServiceManager.startService(DockObserver.class);
        
            try {
                slogi("Wired Accessory Manager");
                // Listen for wired headset changes
                inputManager.setWiredAccessoryCallbacks(
                        new WiredAccessoryManager(context, inputManager));
            } catch (Throwable e) {
                reportWtf("starting WiredAccessoryManager", e);
            }
        
            if (!disableNonCoreServices) {
                if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
                    try {
                        // Start MIDI Manager service
                        slogi("MIDI Manager");
                        mSystemServiceManager.startService(MIDI_SERVICE_CLASS);
                    } catch(Throwable e) {
                        slogw("Failure starting MIDI Manager");
                    }
                }
        
                if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
                        || mPackageManager.hasSystemFeature(
                                PackageManager.FEATURE_USB_ACCESSORY)) {
                    try {
                        // Manage USB host and device support
                        slogi("USB host/device service");
                        mSystemServiceManager.startService(USB_SERVICE_CLASS);
                    } catch(Throwable e) {
                        slogw("Failure starting USB host/device service");
                    }
                }
        
                try {
                    slogi("Serial Service");
                    // Serial port support
                    serial = new SerialService(context);
                    ServiceManager.addService(Context.SERIAL_SERVICE, serial);
                } catch (Throwable e) {
                    Slog.e(TAG, "Failure starting SerialService", e);
                }
            }
        
            mSystemServiceManager.startService(TwilightService.class);
        
            mSystemServiceManager.startService(JobSchedulerService.class);
        
            if (!disableNonCoreServices) {
                if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_BACKUP))
                    mSystemServiceManager.startService(BACKUP_MANAGER_SERVICE_CLASS);
        
                if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS))
                    mSystemServiceManager.startService(APPWIDGET_SERVICE_CLASS);
        
                if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_VOICE_RECOGNIZERS))
                    mSystemServiceManager.startService(VOICE_RECOGNITION_MANAGER_SERVICE_CLASS);
        
                if (GestureLauncherService.isGestureLauncherEnabled(context.getResources())) {
                    slogi("Gesture Launcher Service");
                    mSystemServiceManager.startService(GestureLauncherService.class);
                }
            }
        
            try {
                slogi("DiskStats Service");
                ServiceManager.addService("diskstats", new DiskStatsService(context));
            } catch (Throwable e) {
                reportWtf("starting DiskStats Service", e);
            }
        
            try {
                // need to add this service even if SamplingProfilerIntegration.isEnabled()
                // is false, because it is this service that detects system property change and
                // turns on SamplingProfilerIntegration. Plus, when sampling profiler doesn't work,
                // there is little overhead for running this service.
                slogi("SamplingProfiler Service");
                ServiceManager.addService("samplingprofiler",
                            new SamplingProfilerService(context));
            } catch (Throwable e) {
                reportWtf("starting SamplingProfiler Service", e);
            }
        
            if (!disableNetwork && !disableNetworkTime) {
                try {
                    slogi("NetworkTimeUpdateService");
                    networkTimeUpdater = new NetworkTimeUpdateService(context);
                } catch (Throwable e) {
                    reportWtf("starting NetworkTimeUpdate service", e);
                }
            }
        
            try {
                slogi("CommonTimeManagementService");
                commonTimeMgmtService = new CommonTimeManagementService(context);
                ServiceManager.addService("commontime_management", commonTimeMgmtService);
            } catch (Throwable e) {
                reportWtf("starting CommonTimeManagementService service", e);
            }
        
            if (!disableNetwork) {
                try {
                    slogi("CertBlacklister");
                    CertBlacklister blacklister = new CertBlacklister(context);
                } catch (Throwable e) {
                    reportWtf("starting CertBlacklister", e);
                }
            }
        
            if (!disableNonCoreServices) {
                // Dreams (interactive idle-time views, a/k/a screen savers, and doze mode)
                mSystemServiceManager.startService(DreamManagerService.class);
            }
        
            if (!disableNonCoreServices && !disableAtlas) {
                try {
                    slogi("Assets Atlas Service");
                    atlas = new AssetAtlasService(context);
                    ServiceManager.addService(AssetAtlasService.ASSET_ATLAS_SERVICE, atlas);
                } catch (Throwable e) {
                    reportWtf("starting AssetAtlasService", e);
                }
            }
        
            if (!disableNonCoreServices)
                ServiceManager.addService(GraphicsStatsService.GRAPHICS_STATS_SERVICE,
                        new GraphicsStatsService(context));
        
            if (context.getResources().getBoolean(
                    com.android.internal.R.bool.config_enableGestureService)) {
                try {
                    slogi("Gesture Sensor Service");
                    gestureService = new GestureService(context, inputManager);
                    ServiceManager.addService("gesture", gestureService);
                } catch (Throwable e) {
                    Slog.e(TAG, "Failure starting Gesture Sensor Service", e);
                }
            }
        
            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_PRINTING))
                mSystemServiceManager.startService(PRINT_MANAGER_SERVICE_CLASS);
        
            mSystemServiceManager.startService(RestrictionsManagerService.class);
        
            mSystemServiceManager.startService(MediaSessionService.class);
        
            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_HDMI_CEC))
                mSystemServiceManager.startService(HdmiControlService.class);
        
            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV))
                mSystemServiceManager.startService(TvInputManagerService.class);
        
            if (!disableNonCoreServices) {
                try {
                    slogi("Media Router Service");
                    mediaRouter = new MediaRouterService(context);
                    ServiceManager.addService(Context.MEDIA_ROUTER_SERVICE, mediaRouter);
                } catch (Throwable e) {
                    reportWtf("starting MediaRouterService", e);
                }
        
                mSystemServiceManager.startService(TrustManagerService.class);
        
                mSystemServiceManager.startService(FingerprintService.class);
        
                try {
                    slogi("BackgroundDexOptService");
                    BackgroundDexOptService.schedule(context, 0);
                } catch (Throwable e) {
                    reportWtf("starting BackgroundDexOptService", e);
                }
        
            }
        
            mSystemServiceManager.startService(LauncherAppsService.class);
        }
        
        if (!disableNonCoreServices)
            mSystemServiceManager.startService(MediaProjectionManagerService.class);
        
        // Before things start rolling, be sure we have decided whether
        // we are in safe mode.
        final boolean safeMode = wm.detectSafeMode() ||
                    SystemProperties.get("debug.sw.safemode", "0").equals("1") ||
                    SystemProperties.get("persist.sw.safemode", "0").equals("1");
        if (safeMode) {
            mActivityManagerService.enterSafeMode();
            SystemProperties.set("persist.sw.safemode", "0");
            // Disable the JIT for the system_server process
            VMRuntime.getRuntime().disableJitCompilation();
        } else {
            // Enable the JIT for the system_server process
            VMRuntime.getRuntime().startJitCompilation();
        }
        
        // MMS service broker
        mmsService = mSystemServiceManager.startService(MmsServiceBroker.class);
        
        // It is now time to start up the app processes...
        
        try {
            vibrator.systemReady();
        } catch (Throwable e) {
            reportWtf("making Vibrator Service ready", e);
        }
        
        if (lockSettings != null) {
            try {
                lockSettings.systemReady();
            } catch (Throwable e) {
                reportWtf("making Lock Settings Service ready", e);
            }
        }
        
        // Needed by DevicePolicyManager for initialization
        mSystemServiceManager.startBootPhase(SystemService.PHASE_LOCK_SETTINGS_READY);
        
        mSystemServiceManager.startBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        
        try {
            wm.systemReady();
        } catch (Throwable e) {
            reportWtf("making Window Manager Service ready", e);
        }
        
        if (safeMode)
            mActivityManagerService.showSafeModeOverlay();
        
        // Update the configuration for this context by hand, because we're going
        // to start using it before the config change done in wm.systemReady() will
        // propagate to it.
        Configuration config = wm.computeNewConfiguration();
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager w = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        w.getDefaultDisplay().getMetrics(metrics);
        context.getResources().updateConfiguration(config, metrics);
        
        try {
            // TODO: use boot phase
            mPowerManagerService.systemReady(mActivityManagerService.getAppOpsService());
        } catch (Throwable e) {
            reportWtf("making Power Manager Service ready", e);
        }
        
        try {
            mPackageManagerService.systemReady();
        } catch (Throwable e) {
            reportWtf("making Package Manager Service ready", e);
        }
        
        try {
            // TODO: use boot phase and communicate these flags some other way
            mDisplayManagerService.systemReady(safeMode, mOnlyCore);
        } catch (Throwable e) {
            reportWtf("making Display Manager Service ready", e);
        }
        
        if (gestureService != null) {
            try {
                gestureService.systemReady();
            } catch (Throwable e) {
                reportWtf("making Gesture Sensor Service ready", e);
            }
        }
        
        // These are needed to propagate to the runnable below.
        final NetworkManagementService networkManagementF = networkManagement;
        final NetworkStatsService networkStatsF = networkStats;
        final NetworkPolicyManagerService networkPolicyF = networkPolicy;
        final ConnectivityService connectivityF = connectivity;
        final NetworkScoreService networkScoreF = networkScore;
        final WallpaperManagerService wallpaperF = wallpaper;
        final InputMethodManagerService immF = imm;
        final LocationManagerService locationF = location;
        final CountryDetectorService countryDetectorF = countryDetector;
        final NetworkTimeUpdateService networkTimeUpdaterF = networkTimeUpdater;
        final CommonTimeManagementService commonTimeMgmtServiceF = commonTimeMgmtService;
        final TextServicesManagerService textServiceManagerServiceF = tsms;
        final StatusBarManagerService statusBarF = statusBar;
        final AssetAtlasService atlasF = atlas;
        final InputManagerService inputManagerF = inputManager;
        final TelephonyRegistry telephonyRegistryF = telephonyRegistry;
        final MediaRouterService mediaRouterF = mediaRouter;
        final AudioService audioServiceF = audioService;
        final MmsServiceBroker mmsServiceF = mmsService;
        
        // We now tell the activity manager it is okay to run third party
        // code.  It will call back into us once it has gotten to the state
        // where third party code can really run (but before it has actually
        // started launching the initial applications), for us to complete our
        // initialization.
        mActivityManagerService.systemReady(new Runnable() {
            @Override
            public void run() {
                slogi("Making services ready");
                mSystemServiceManager.startBootPhase(
                        SystemService.PHASE_ACTIVITY_MANAGER_READY);
        
                try {
                    mActivityManagerService.startObservingNativeCrashes();
                } catch (Throwable e) {
                    reportWtf("observing native crashes", e);
                }
        
                slogi("WebViewFactory preparation");
                WebViewFactory.prepareWebViewInSystemServer();
        
                try {
                    startSystemUi(context);
                } catch (Throwable e) {
                    reportWtf("starting System UI", e);
                }
                
                try {
                    if (networkScoreF != null) networkScoreF.systemReady();
                } catch (Throwable e) {
                    reportWtf("making Network Score Service ready", e);
                }
                
                try {
                    if (networkManagementF != null) networkManagementF.systemReady();
                } catch (Throwable e) {
                    reportWtf("making Network Managment Service ready", e);
                }
                
                try {
                    if (networkStatsF != null) networkStatsF.systemReady();
                } catch (Throwable e) {
                    reportWtf("making Network Stats Service ready", e);
                }
                
                try {
                    if (networkPolicyF != null) networkPolicyF.systemReady();
                } catch (Throwable e) {
                    reportWtf("making Network Policy Service ready", e);
                }
                
                try {
                    if (connectivityF != null) connectivityF.systemReady();
                } catch (Throwable e) {
                    reportWtf("making Connectivity Service ready", e);
                }
                
                try {
                    if (audioServiceF != null) audioServiceF.systemReady();
                } catch (Throwable e) {
                    reportWtf("Notifying AudioService running", e);
                }
                
                Watchdog.getInstance().start();
        
                // It is now okay to let the various system services start their
                // third party code...
                mSystemServiceManager.startBootPhase(
                        SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
        
                try {
                    if (wallpaperF != null) wallpaperF.systemRunning();
                } catch (Throwable e) {
                    reportWtf("Notifying WallpaperService running", e);
                }
                
                try {
                    if (immF != null) immF.systemRunning(statusBarF);
                } catch (Throwable e) {
                    reportWtf("Notifying InputMethodService running", e);
                }
                
                try {
                    if (locationF != null) locationF.systemRunning();
                } catch (Throwable e) {
                    reportWtf("Notifying Location Service running", e);
                }
                
                try {
                    if (countryDetectorF != null) countryDetectorF.systemRunning();
                } catch (Throwable e) {
                    reportWtf("Notifying CountryDetectorService running", e);
                }
                
                try {
                    if (networkTimeUpdaterF != null) networkTimeUpdaterF.systemRunning();
                } catch (Throwable e) {
                    reportWtf("Notifying NetworkTimeService running", e);
                }
                
                try {
                    if (commonTimeMgmtServiceF != null)
                        commonTimeMgmtServiceF.systemRunning();
                } catch (Throwable e) {
                    reportWtf("Notifying CommonTimeManagementService running", e);
                }
                
                try {
                    if (textServiceManagerServiceF != null)
                        textServiceManagerServiceF.systemRunning();
                } catch (Throwable e) {
                    reportWtf("Notifying TextServicesManagerService running", e);
                }
                
                try {
                    if (atlasF != null) atlasF.systemRunning();
                } catch (Throwable e) {
                    reportWtf("Notifying AssetAtlasService running", e);
                }
                
                try {
                    // TODO(BT) Pass parameter to input manager
                    if (inputManagerF != null) inputManagerF.systemRunning();
                } catch (Throwable e) {
                    reportWtf("Notifying InputManagerService running", e);
                }
                
                try {
                    if (telephonyRegistryF != null) telephonyRegistryF.systemRunning();
                } catch (Throwable e) {
                    reportWtf("Notifying TelephonyRegistry running", e);
                }
                
                try {
                    if (mediaRouterF != null) mediaRouterF.systemRunning();
                } catch (Throwable e) {
                    reportWtf("Notifying MediaRouterService running", e);
                }
        
                try {
                    if (mmsServiceF != null) mmsServiceF.systemRunning();
                } catch (Throwable e) {
                    reportWtf("Notifying MmsService running", e);
                }
            }
        });
    }

    protected static final void startSystemUi(Context context) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.android.systemui",
                        "com.android.systemui.SystemUIService"));
            context.startServiceAsUser(intent, UserHandle.OWNER);
        } catch(Exception ex) {
            slogw("Could not start SystemUI service. Is it installed? " +
                        "Is it having crashes?");
        }
    }

}