/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.ddmlib.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Slow;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AdbHelper;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AvdData;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.ClientTracker;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDeviceSharedImpl;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.InstallMetrics;
import com.android.ddmlib.InstallReceiver;
import com.android.ddmlib.Log;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.ProfileableClient;
import com.android.ddmlib.PropertyFetcher;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.RemoteSplitApkInstaller;
import com.android.ddmlib.ScreenRecorderOptions;
import com.android.ddmlib.ServiceInfo;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SplitApkInstaller;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.clientmanager.DeviceClientManager;
import com.android.ddmlib.log.LogReceiver;
import com.android.sdklib.AndroidVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.Atomics;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** A Device. It can be a physical device or an emulator. */
public final class DeviceImpl implements IDevice {
    /** Serial number of the device */
    private final String mSerialNumber;

    /** Name and path of the AVD */
    private final SettableFuture<AvdData> mAvdData = SettableFuture.create();

    /** State of the device. */
    private DeviceState mState;

    /** True if ADB is running as root */
    private boolean mIsRoot;

    /** Information about the most recent installation via this device */
    private InstallMetrics lastInstallMetrics;

    /** Device properties. */
    private final PropertyFetcher mPropFetcher = new PropertyFetcher(this);

    private final Map<String, String> mMountPoints = new HashMap<>();

    private final BatteryFetcher mBatteryFetcher = new BatteryFetcher(this);

    @GuardedBy("mClients")
    private final List<ClientImpl> mClients = new ArrayList<>();

    /** Maps pid's of clients in {@link #mClients} to their package name. */
    private final Map<Integer, String> mClientInfo = new ConcurrentHashMap<>();

    @GuardedBy("mProfileableClients")
    private final List<ProfileableClientImpl> mProfileableClients = new ArrayList<>();

    private final ClientTracker mClientTracer;

    @Nullable private final Function<IDevice, DeviceClientManager> mDeviceClientManagerProvider;

    @NonNull private final UserDataMapImpl mUserDataMap = new UserDataMapImpl();

    private final IDeviceSharedImpl iDeviceSharedImpl = new IDeviceSharedImpl(this);

    private static final String LOG_TAG = "Device";

    private static final long GET_PROP_TIMEOUT_MS = 1000;
    private static final long INITIAL_GET_PROP_TIMEOUT_MS = 5000;
    private static final int QUERY_IS_ROOT_TIMEOUT_MS = 1000;

    private static final long INSTALL_TIMEOUT_MINUTES;

    static final int WAIT_TIME = 5; // spin-wait sleep, in ms

    static {
        String installTimeout = System.getenv("ADB_INSTALL_TIMEOUT");
        long time = 4;
        if (installTimeout != null) {
            try {
                time = Long.parseLong(installTimeout);
            } catch (NumberFormatException e) {
                // use default value
            }
        }
        INSTALL_TIMEOUT_MINUTES = time;
    }

    /**
     * Socket for the connection monitoring client connection/disconnection.
     */
    private SocketChannel mSocketChannel;

    private static final long LS_TIMEOUT_SEC = 2;

    @Nullable private Set<String> mAdbFeatures;
    private Object mAdbFeaturesLock = new Object();

    @GuardedBy("this")
    @Nullable
    private DeviceClientManager mDeviceClientManager;

    @NonNull
    @Override
    public String getSerialNumber() {
        return mSerialNumber;
    }

    @Nullable
    @Override
    public String getAvdName() {
        AvdData avdData = getCurrentAvdData();
        return avdData != null ? avdData.getName() : null;
    }

    @Nullable
    @Override
    public String getAvdPath() {
        AvdData avdData = getCurrentAvdData();
        return avdData != null ? avdData.getPath() : null;
    }

    @Nullable
    private AvdData getCurrentAvdData() {
        try {
            return mAvdData.isDone() ? mAvdData.get() : null;
        } catch (ExecutionException | InterruptedException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public ListenableFuture<AvdData> getAvdData() {
        return mAvdData;
    }

    void setAvdData(@Nullable AvdData data) {
        mAvdData.set(data);
    }

    @NonNull
    @Override
    public String getName() {
        return iDeviceSharedImpl.getName();
    }

    @Override
    public DeviceState getState() {
        return mState;
    }

    /** Changes the state of the device. */
    void setState(DeviceState state) {
        mState = state;
    }

    @NonNull
    @Override
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(mPropFetcher.getProperties());
    }

    @Override
    public int getPropertyCount() {
        return mPropFetcher.getProperties().size();
    }

    @Nullable
    @Override
    public String getProperty(@NonNull String name) {
        Map<String, String> properties = mPropFetcher.getProperties();
        long timeout = properties.isEmpty() ? INITIAL_GET_PROP_TIMEOUT_MS : GET_PROP_TIMEOUT_MS;

        Future<String> future = mPropFetcher.getProperty(name);
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException
                | ExecutionException
                | java.util.concurrent.TimeoutException e) {
            // ignore
        }
        return null;
    }

    @Override
    public boolean arePropertiesSet() {
        return mPropFetcher.arePropertiesSet();
    }

    @Override
    public String getPropertyCacheOrSync(String name) {
        Future<String> future = mPropFetcher.getProperty(name);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            // ignore
        }
        return null;
    }

    @Override
    public String getPropertySync(String name) {
        Future<String> future = mPropFetcher.getProperty(name);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            // ignore
        }
        return null;
    }

    @NonNull
    @Override
    public ListenableFuture<String> getSystemProperty(@NonNull String name) {
        return mPropFetcher.getProperty(name);
    }

    @Override
    public boolean supportsFeature(@NonNull Feature feature) {
        return iDeviceSharedImpl.supportsFeature(feature, getAdbFeatures());
    }

    @NonNull
    Set<String> getAdbFeatures() {
        synchronized (mAdbFeaturesLock) {
            if (mAdbFeatures != null) {
                return mAdbFeatures;
            }

            try {
                String response = AdbHelper.getFeatures(this);
                mAdbFeatures = new HashSet<>(Arrays.asList(response.split(",")));
                response = AdbHelper.getHostFeatures();
                // We want features supported by both device and host.
                mAdbFeatures.retainAll(Arrays.asList(response.split(",")));
            } catch (TimeoutException | AdbCommandRejectedException | IOException e) {
                Log.e(LOG_TAG, new RuntimeException("Error obtaining features: ", e));
                return new HashSet<>();
            }

            return mAdbFeatures;
        }
    }

    @NonNull
    @Override
    public Map<String, ServiceInfo> services() {
        return iDeviceSharedImpl.services();
    }

    // The full list of features can be obtained from /etc/permissions/features*
    // However, the smaller set of features we are interested in can be obtained by
    // reading the build characteristics property.
    @Override
    public boolean supportsFeature(@NonNull HardwareFeature feature) {
        return iDeviceSharedImpl.supportsFeature(feature);
    }

    @NonNull
    @Override
    public AndroidVersion getVersion() {
        return iDeviceSharedImpl.getVersion();
    }

    @Nullable
    @Override
    public String getMountPoint(@NonNull String name) {
        String mount = mMountPoints.get(name);
        if (mount == null) {
            try {
                mount = queryMountPoint(name);
                mMountPoints.put(name, mount);
            } catch (TimeoutException
                    | AdbCommandRejectedException
                    | ShellCommandUnresponsiveException
                    | IOException ignored) {
            }
        }
        return mount;
    }

    @Nullable
    private String queryMountPoint(@NonNull final String name)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {

        final AtomicReference<String> ref = Atomics.newReference();
        executeShellCommand(
                "echo $" + name,
                new MultiLineReceiver() { //$NON-NLS-1$
                    @Override
                    public boolean isCancelled() {
                        return false;
                    }

                    @Override
                    public void processNewLines(@NonNull String[] lines) {
                        for (String line : lines) {
                            if (!line.isEmpty()) {
                                // this should be the only one.
                                ref.set(line);
                            }
                        }
                    }
                });
        return ref.get();
    }

    @Override
    public String toString() {
        return mSerialNumber;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isOnline()
     */
    @Override
    public boolean isOnline() {
        return mState == DeviceState.ONLINE;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isEmulator()
     */
    @Override
    public boolean isEmulator() {
        return mSerialNumber.matches(RE_EMULATOR_SN);
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isOffline()
     */
    @Override
    public boolean isOffline() {
        return mState == DeviceState.OFFLINE;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isBootLoader()
     */
    @Override
    public boolean isBootLoader() {
        return mState == DeviceState.BOOTLOADER;
    }

    @Override
    @Nullable
    public SyncService getSyncService()
            throws TimeoutException, AdbCommandRejectedException, IOException {
        SyncService syncService = new SyncService(AndroidDebugBridge.getSocketAddress(), this);
        if (syncService.openSync()) {
            return syncService;
        }

        return null;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getFileListingService()
     */
    @Override
    public FileListingService getFileListingService() {
        return new FileListingService(this);
    }

    @Override
    public RawImage getScreenshot()
            throws TimeoutException, AdbCommandRejectedException, IOException {
        return getScreenshot(0, TimeUnit.MILLISECONDS);
    }

    @Override
    public RawImage getScreenshot(long timeout, TimeUnit unit)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        return AdbHelper.getFrameBuffer(AndroidDebugBridge.getSocketAddress(), this, timeout, unit);
    }

    @Override
    public void startScreenRecorder(
            @NonNull String remoteFilePath,
            @NonNull ScreenRecorderOptions options,
            @NonNull IShellOutputReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, IOException,
                    ShellCommandUnresponsiveException {
        executeShellCommand(getScreenRecorderCommand(remoteFilePath, options), receiver, 0, null);
    }

    @VisibleForTesting
    public static String getScreenRecorderCommand(
            @NonNull String remoteFilePath, @NonNull ScreenRecorderOptions options) {
        StringBuilder sb = new StringBuilder();

        sb.append("screenrecord");
        sb.append(' ');

        if (options.width > 0 && options.height > 0) {
            sb.append("--size ");
            sb.append(options.width);
            sb.append('x');
            sb.append(options.height);
            sb.append(' ');
        }

        if (options.bitrateMbps > 0) {
            sb.append("--bit-rate ");
            sb.append(options.bitrateMbps * 1000000);
            sb.append(' ');
        }

        if (options.timeLimit > 0) {
            sb.append("--time-limit ");
            long seconds = TimeUnit.SECONDS.convert(options.timeLimit, options.timeLimitUnits);
            if (seconds > 180) {
                seconds = 180;
            }
            sb.append(seconds);
            sb.append(' ');
        }

        sb.append(remoteFilePath);

        return sb.toString();
    }

    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        executeRemoteCommand(
                command,
                receiver,
                DdmPreferences.getTimeOut(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void executeShellCommand(
            String command,
            IShellOutputReceiver receiver,
            long maxTimeToOutputResponse,
            TimeUnit maxTimeUnits,
            @Nullable InputStream is)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        executeRemoteCommand(
                AdbHelper.AdbService.EXEC,
                command,
                receiver,
                0L,
                maxTimeToOutputResponse,
                maxTimeUnits,
                is);
    }

    @Override
    public void executeBinderCommand(
            String[] parameters,
            IShellOutputReceiver receiver,
            long maxTimeToOutputResponse,
            TimeUnit maxTimeUnits,
            @Nullable InputStream is)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        if (supportsFeature(Feature.ABB_EXEC)) {
            executeRemoteCommand(
                    AdbHelper.AdbService.ABB_EXEC,
                    String.join("\u0000", parameters),
                    receiver,
                    0L,
                    maxTimeToOutputResponse,
                    maxTimeUnits,
                    is);
        } else {
            executeShellCommand(
                    "cmd " + String.join(" ", parameters),
                    receiver,
                    maxTimeToOutputResponse,
                    maxTimeUnits,
                    is);
        }
    }

    @Override
    public void executeShellCommand(
            String command, IShellOutputReceiver receiver, int maxTimeToOutputResponse)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        executeRemoteCommand(
                command,
                receiver,
                maxTimeToOutputResponse,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void executeShellCommand(
            String command,
            IShellOutputReceiver receiver,
            long maxTimeToOutputResponse,
            TimeUnit maxTimeUnits)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        executeRemoteCommand(
                command,
                receiver,
                0L,
                maxTimeToOutputResponse,
                maxTimeUnits);
    }

    @Override
    public void executeShellCommand(
            String command,
            IShellOutputReceiver receiver,
            long maxTimeout,
            long maxTimeToOutputResponse,
            TimeUnit maxTimeUnits)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        executeRemoteCommand(
                command,
                receiver,
                maxTimeout,
                maxTimeToOutputResponse,
                maxTimeUnits);
    }

    /**
     * Executes a shell command on the device and retrieve the output. The output is handed to
     * <var>rcvr</var> as it arrives.
     *
     * @param command                 the shell command to execute
     * @param rcvr                    the {@link IShellOutputReceiver} that will receives the output
     *                                of the shell command
     * @param maxTimeout              max time for the command to return. A value of 0 means no max
     *                                timeout will be applied.
     * @param maxTimeToOutputResponse max time between command output. If more time passes between
     *                                command output, the method will throw
     *                                {@link ShellCommandUnresponsiveException}. A value of 0 means
     *                                the method will wait forever for command output and never
     *                                throw.
     * @param maxTimeUnits            Units for non-zero {@code maxTimeout} and
     *                                {@code maxTimeToOutputResponse} values.
     * @throws TimeoutException                  in case of timeout on the connection when sending
     *                                           the command.
     * @throws AdbCommandRejectedException       if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send any output
     *                                           for a period longer than
     *                                           <var>maxTimeToOutputResponse</var>.
     * @throws IOException                       in case of I/O error on the connection.
     * @see DdmPreferences#getTimeOut()
     */
    @Override
    public void executeRemoteCommand(
            String command,
            IShellOutputReceiver rcvr,
            long maxTimeout,
            long maxTimeToOutputResponse,
            TimeUnit maxTimeUnits)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        executeRemoteCommand(
                AdbHelper.AdbService.SHELL,
                command,
                rcvr,
                maxTimeout,
                maxTimeToOutputResponse,
                maxTimeUnits,
                null /* inputStream */);
    }

    /**
     * Executes a shell command on the device and retrieve the output. The output is handed to
     * <var>rcvr</var> as it arrives.
     *
     * @param command                 the shell command to execute
     * @param rcvr                    the {@link IShellOutputReceiver} that will receives the output
     *                                of the shell command
     * @param maxTimeToOutputResponse max time between command output. If more time passes between
     *                                command output, the method will throw
     *                                {@link ShellCommandUnresponsiveException}. A value of 0 means
     *                                the method will wait forever for command output and never
     *                                throw.
     * @param maxTimeUnits            Units for non-zero {@code maxTimeToOutputResponse} values.
     * @throws TimeoutException                  in case of timeout on the connection when sending
     *                                           the command.
     * @throws AdbCommandRejectedException       if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send any output
     *                                           for a period longer than
     *                                           <var>maxTimeToOutputResponse</var>.
     * @throws IOException                       in case of I/O error on the connection.
     * @see DdmPreferences#getTimeOut()
     */
    @Override
    public void executeRemoteCommand(
            String command,
            IShellOutputReceiver rcvr,
            long maxTimeToOutputResponse,
            TimeUnit maxTimeUnits)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        executeRemoteCommand(
                AdbHelper.AdbService.SHELL,
                command,
                rcvr,
                maxTimeToOutputResponse,
                maxTimeUnits,
                null /* inputStream */);
    }

    /**
     * Executes a remote command on the device and retrieve the output. The output is handed to
     * <var>rcvr</var> as it arrives. The command is execute by the remote service identified by
     * the adbService parameter.
     *
     * @param adbService              the {@link AdbHelper.AdbService} to use to run the command.
     * @param command                 the shell command to execute
     * @param rcvr                    the {@link IShellOutputReceiver} that will receives the output
     *                                of the shell command
     * @param maxTimeToOutputResponse max time between command output. If more time passes between
     *                                command output, the method will throw
     *                                {@link ShellCommandUnresponsiveException}. A value of 0 means
     *                                the method will wait forever for command output and never
     *                                throw.
     * @param maxTimeUnits            Units for non-zero {@code maxTimeToOutputResponse} values.
     * @param is                      a optional {@link InputStream} to be streamed up after
     *                                invoking the command and before retrieving the response.
     * @throws TimeoutException                  in case of timeout on the connection when sending
     *                                           the command.
     * @throws AdbCommandRejectedException       if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send any output
     *                                           for a period longer than
     *                                           <var>maxTimeToOutputResponse</var>.
     * @throws IOException                       in case of I/O error on the connection.
     * @see DdmPreferences#getTimeOut()
     */
    @Override
    public void executeRemoteCommand(
            AdbHelper.AdbService adbService,
            String command,
            IShellOutputReceiver rcvr,
            long maxTimeToOutputResponse,
            TimeUnit maxTimeUnits,
            @Nullable InputStream is)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        executeRemoteCommand(
                adbService,
                command,
                rcvr,
                0L,
                maxTimeToOutputResponse,
                maxTimeUnits,
                is);
    }

    /**
     * Executes a remote command on the device and retrieve the output. The output is handed to
     * <var>rcvr</var> as it arrives. The command is execute by the remote service identified by
     * the adbService parameter.
     *
     * @param adbService              the {@link AdbHelper.AdbService} to use to run the command.
     * @param command                 the shell command to execute
     * @param rcvr                    the {@link IShellOutputReceiver} that will receives the output
     *                                of the shell command
     * @param maxTimeout              max timeout for the full command to execute. A value of 0
     *                                means no timeout.
     * @param maxTimeToOutputResponse max time between command output. If more time passes between
     *                                command output, the method will throw
     *                                {@link ShellCommandUnresponsiveException}. A value of 0 means
     *                                the method will wait forever for command output and never
     *                                throw.
     * @param maxTimeUnits            Units for non-zero {@code maxTimeout} and
     *                                {@code maxTimeToOutputResponse} values.
     * @param is                      a optional {@link InputStream} to be streamed up after
     *                                invoking the command and before retrieving the response.
     * @throws TimeoutException                  in case of timeout on the connection when sending
     *                                           the command.
     * @throws AdbCommandRejectedException       if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send any output
     *                                           for a period longer than
     *                                           <var>maxTimeToOutputResponse</var>.
     * @throws IOException                       in case of I/O error on the connection.
     * @see DdmPreferences#getTimeOut()
     */
    @Slow
    @Override
    public void executeRemoteCommand(
            AdbHelper.AdbService adbService,
            String command,
            IShellOutputReceiver rcvr,
            long maxTimeout,
            long maxTimeToOutputResponse,
            TimeUnit maxTimeUnits,
            @Nullable InputStream is)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        long maxTimeToOutputMs = 0;
        if (maxTimeToOutputResponse > 0) {
            if (maxTimeUnits == null) {
                throw new NullPointerException("Time unit must not be null for non-zero max.");
            }
            maxTimeToOutputMs = maxTimeUnits.toMillis(maxTimeToOutputResponse);
        }
        long maxTimeoutMs = 0L;
        if (maxTimeout > 0L) {
            if (maxTimeUnits == null) {
                throw new NullPointerException("Time unit must not be null for non-zero max.");
            }
            maxTimeoutMs = maxTimeUnits.toMillis(maxTimeout);
        }

        Log.v("ddms", "execute: running " + command);

        SocketChannel adbChan = null;
        try {
            long startTime = System.currentTimeMillis();
            adbChan = SocketChannel.open(AndroidDebugBridge.getSocketAddress());
            adbChan.configureBlocking(false);

            // if the device is not -1, then we first tell adb we're looking to
            // talk to a specific device
            AdbHelper.setDevice(adbChan, this);

            byte[] request = AdbHelper.formAdbRequest(adbService, command);
            AdbHelper.write(adbChan, request);

            long timeOutForResp =
                    maxTimeToOutputMs > 0 ? maxTimeToOutputMs : DdmPreferences.getTimeOut();
            AdbHelper.AdbResponse resp = AdbHelper.readAdbResponse(adbChan, false, timeOutForResp);

            if (!resp.okay) {
                Log.e("ddms", "ADB rejected shell command (" + command + "): " + resp.message);
                throw new AdbCommandRejectedException(resp.message);
            }

            byte[] data = new byte[16384];

            // stream the input file if present.
            if (is != null) {
                int read;
                while ((read = is.read(data)) != -1) {
                    ByteBuffer buf = ByteBuffer.wrap(data, 0, read);
                    int writtenTotal = 0;
                    long lastResponsive = System.currentTimeMillis();
                    while (buf.hasRemaining()) {
                        int written = adbChan.write(buf);

                        if (written == 0) {
                            // If device takes too long to respond to a write command, throw timeout
                            // exception.
                            if (maxTimeToOutputMs > 0
                                    && System.currentTimeMillis() - lastResponsive
                                            > maxTimeToOutputMs) {
                                throw new TimeoutException(
                                        String.format(
                                                "executeRemoteCommand write timed out after %sms",
                                                maxTimeToOutputMs));
                            }
                        } else {
                            lastResponsive = System.currentTimeMillis();
                        }

                        // If the overall timeout exists and is exceeded, we throw timeout
                        // exception.
                        if (maxTimeoutMs > 0
                                && System.currentTimeMillis() - startTime > maxTimeoutMs) {
                            throw new TimeoutException(
                                    String.format(
                                            "executeRemoteCommand timed out after %sms",
                                            maxTimeoutMs));
                        }

                        writtenTotal += written;
                    }
                    if (writtenTotal != read) {
                        Log.e(
                                "ddms",
                                "ADB write inconsistency, wrote "
                                        + writtenTotal
                                        + "expected "
                                        + read);
                        throw new AdbCommandRejectedException("write failed");
                    }
                }
            }

            ByteBuffer buf = ByteBuffer.wrap(data);
            buf.clear();
            long timeToResponseCount = 0;
            while (true) {
                int count;

                if (rcvr != null && rcvr.isCancelled()) {
                    Log.v("ddms", "execute: cancelled");
                    break;
                }

                count = adbChan.read(buf);
                if (count < 0) {
                    // we're at the end, we flush the output
                    rcvr.flush();
                    Log.v(
                            "ddms",
                            "execute '"
                                    + command
                                    + "' on '"
                                    + this
                                    + "' : EOF hit. Read: "
                                    + count);
                    break;
                } else if (count == 0) {
                    try {
                        int wait = WAIT_TIME * 5;
                        timeToResponseCount += wait;
                        if (maxTimeToOutputMs > 0 && timeToResponseCount > maxTimeToOutputMs) {
                            throw new ShellCommandUnresponsiveException();
                        }
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        // Throw a timeout exception in place of interrupted exception to avoid API
                        // changes.
                        throw new TimeoutException(
                                "executeRemoteCommand interrupted with immediate timeout via interruption.");
                    }
                } else {
                    // reset timeout
                    timeToResponseCount = 0;

                    // send data to receiver if present
                    if (rcvr != null) {
                        rcvr.addOutput(buf.array(), buf.arrayOffset(), buf.position());
                    }
                    buf.rewind();
                }
                // if the overall timeout exists and is exceeded, we throw timeout exception.
                if (maxTimeoutMs > 0 && System.currentTimeMillis() - startTime > maxTimeoutMs) {
                    throw new TimeoutException(
                            String.format(
                                    "executeRemoteCommand timed out after %sms", maxTimeoutMs));
                }
            }
        } finally {
            if (adbChan != null) {
                adbChan.close();
            }
            Log.v("ddms", "execute: returning");
        }
    }

    @Override
    public SocketChannel rawExec(String executable, String[] parameters)
            throws AdbCommandRejectedException, TimeoutException, IOException {
        return AdbHelper.rawExec(
                AndroidDebugBridge.getSocketAddress(), this, executable, parameters);
    }

    @Override
    public SocketChannel rawBinder(String service, String[] parameters)
            throws AdbCommandRejectedException, TimeoutException, IOException {
        final String[] command = new String[parameters.length + 1];
        command[0] = service;
        System.arraycopy(parameters, 0, command, 1, parameters.length);

        if (supportsFeature(Feature.ABB_EXEC)) {
            return AdbHelper.rawAdbService(
                    AndroidDebugBridge.getSocketAddress(),
                    this,
                    String.join("\u0000", command),
                    AdbHelper.AdbService.ABB_EXEC);
        } else {
            return AdbHelper.rawExec(AndroidDebugBridge.getSocketAddress(), this, "cmd", command);
        }
    }

    @Override
    public void runEventLogService(LogReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.runEventLogService(AndroidDebugBridge.getSocketAddress(), this, receiver);
    }

    @Override
    public void runLogService(String logname, LogReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.runLogService(AndroidDebugBridge.getSocketAddress(), this, logname, receiver);
    }

    @Override
    public void createForward(int localPort, int remotePort)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.createForward(
                AndroidDebugBridge.getSocketAddress(),
                this,
                String.format("tcp:%d", localPort), //$NON-NLS-1$
                String.format("tcp:%d", remotePort)); //$NON-NLS-1$
    }

    @Override
    public void createForward(
            int localPort, String remoteSocketName, DeviceUnixSocketNamespace namespace)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.createForward(
                AndroidDebugBridge.getSocketAddress(),
                this,
                String.format("tcp:%d", localPort), //$NON-NLS-1$
                String.format("%s:%s", namespace.getType(), remoteSocketName)); //$NON-NLS-1$
    }

    @Override
    public void removeForward(int localPort)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.removeForward(
                AndroidDebugBridge.getSocketAddress(),
                this,
                String.format("tcp:%d", localPort)); // $NON-NLS-1$
    }

    @Override
    public void createReverse(int remotePort, int localPort)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.createReverse(
                AndroidDebugBridge.getSocketAddress(),
                this,
                String.format(Locale.US, "tcp:%d", localPort), //$NON-NLS-1$
                String.format(Locale.US, "tcp:%d", remotePort)); //$NON-NLS-1$
    }

    @Override
    public void removeReverse(int remotePort)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.removeReverse(
                AndroidDebugBridge.getSocketAddress(),
                this,
                String.format(Locale.US, "tcp:%d", remotePort)); //$NON-NLS-1$
    }

    // @VisibleForTesting
    public DeviceImpl(ClientTracker clientTracer, String serialNumber, DeviceState deviceState) {
        this(clientTracer, null, serialNumber, deviceState);
    }

    // @VisibleForTesting
    public DeviceImpl(
            ClientTracker clientTracer,
            Function<IDevice, DeviceClientManager> deviceClientManagerProvider,
            String serialNumber,
            DeviceState deviceState) {
        mClientTracer = clientTracer;
        mDeviceClientManagerProvider = deviceClientManagerProvider;
        mSerialNumber = serialNumber;
        mState = deviceState;
    }

    public ClientTracker getClientTracker() {
        return mClientTracer;
    }

    @Override
    public boolean hasClients() {
        if (mDeviceClientManagerProvider != null) {
            return !getClientManager().getClients().isEmpty();
        }
        synchronized (mClients) {
            return !mClients.isEmpty();
        }
    }

    @Override
    public Client[] getClients() {
        if (mDeviceClientManagerProvider != null) {
            return getClientManager().getClients().toArray(new Client[0]);
        }
        synchronized (mClients) {
            return mClients.toArray(new Client[0]);
        }
    }

    @Override
    public Client getClient(String applicationName) {
        if (mDeviceClientManagerProvider != null) {
            Client[] clients = getClients();
            for (Client c : clients) {
                if (applicationName.equals(c.getClientData().getClientDescription())) {
                    return c;
                }
            }
            return null;
        }
        synchronized (mClients) {
            for (Client c : mClients) {
                if (applicationName.equals(c.getClientData().getClientDescription())) {
                    return c;
                }
            }
        }

        return null;
    }

    @Override
    public ProfileableClient[] getProfileableClients() {
        if (mDeviceClientManagerProvider != null) {
            return getClientManager().getProfileableClients().toArray(new ProfileableClient[0]);
        }
        return getProfileableClientImpls();
    }

    ProfileableClientImpl[] getProfileableClientImpls() {
        synchronized (mProfileableClients) {
            return mProfileableClients.toArray(new ProfileableClientImpl[0]);
        }
    }

    @Override
    @NonNull
    public DeviceClientManager getClientManager() {
        // Fast exit if feature not supported
        if (mDeviceClientManagerProvider == null) {
            // This throws an exception
            return IDevice.super.getClientManager();
        }
        synchronized (this) {
            if (mDeviceClientManager == null) {
                mDeviceClientManager = mDeviceClientManagerProvider.apply(this);
            }
            return mDeviceClientManager;
        }
    }

    @Override
    public void forceStop(String applicationName) {
        iDeviceSharedImpl.forceStop(applicationName);
    }

    @Override
    public void kill(String applicationName) {
        iDeviceSharedImpl.kill(applicationName);
    }

    void addClient(ClientImpl client) {
        synchronized (mClients) {
            mClients.add(client);
        }

        addClientInfo(client);
    }

    List<ClientImpl> getClientList() {
        synchronized (mClients) {
            return mClients;
        }
    }

    void clearClientList() {
        synchronized (mClients) {
            mClients.clear();
        }

        clearClientInfo();
    }

    /**
     * Removes a {@link ClientImpl} from the list.
     *
     * @param client the client to remove.
     * @param notify Whether or not to notify the listeners of a change.
     */
    void removeClient(ClientImpl client, boolean notify) {
        mClientTracer.trackDisconnectedClient(client);
        synchronized (mClients) {
            mClients.remove(client);
        }
        if (notify) {
            AndroidDebugBridge.deviceChanged(this, CHANGE_CLIENT_LIST);
        }

        removeClientInfo(client);
    }

    void updateProfileableClientList(@NonNull List<ProfileableClientImpl> newClientList) {
        synchronized (mProfileableClients) {
            mProfileableClients.clear();
            mProfileableClients.addAll(newClientList);
            Collections.sort(
                    mProfileableClients,
                    Comparator.comparingInt(c -> c.getProfileableClientData().getPid()));
        }
    }

    void updateProfileableClientName(int pid, @NonNull String name) {
        synchronized (mProfileableClients) {
            for (ProfileableClientImpl client : mProfileableClients) {
                if (client.getProfileableClientData().getPid() == pid) {
                    client.getProfileableClientData().setProcessName(name);
                    break;
                }
            }
        }
    }

    void clearProfileableClientList() {
        synchronized (mProfileableClients) {
            mProfileableClients.clear();
        }
    }

    /** Sets the socket channel on which a track-jdwp command for this device has been sent. */
    void setClientMonitoringSocket(@NonNull SocketChannel socketChannel) {
        mSocketChannel = socketChannel;
    }

    /**
     * Returns the channel on which responses to the track-jdwp command will be available if it has
     * been set, null otherwise. The channel is set via {@link
     * #setClientMonitoringSocket(SocketChannel)}, which is usually invoked when the device goes
     * online.
     */
    @Nullable
    SocketChannel getClientMonitoringSocket() {
        return mSocketChannel;
    }

    void update(int changeMask) {
        AndroidDebugBridge.deviceChanged(this, changeMask);
    }

    void update(@NonNull ClientImpl client, int changeMask) {
        AndroidDebugBridge.clientChanged(client, changeMask);
        updateClientInfo(client, changeMask);
    }

    void setMountingPoint(String name, String value) {
        mMountPoints.put(name, value);
    }

    private void addClientInfo(ClientImpl client) {
        ClientData cd = client.getClientData();
        setClientInfo(cd.getPid(), cd.getPackageName());
    }

    private void updateClientInfo(ClientImpl client, int changeMask) {
        if ((changeMask & ClientImpl.CHANGE_NAME) == ClientImpl.CHANGE_NAME) {
            addClientInfo(client);
        }
    }

    private void removeClientInfo(ClientImpl client) {
        int pid = client.getClientData().getPid();
        mClientInfo.remove(pid);
    }

    private void clearClientInfo() {
        mClientInfo.clear();
    }

    private void setClientInfo(int pid, String pkgName) {
        if (pkgName == null) {
            pkgName = UNKNOWN_PACKAGE;
        }

        mClientInfo.put(pid, pkgName);
    }

    @Override
    public String getClientName(int pid) {
        if (mDeviceClientManagerProvider != null) {
            String name = null;
            Client[] clients = getClients();
            for (Client c : clients) {
                if (pid == c.getClientData().getPid()) {
                    name = c.getClientData().getClientDescription();
                    break;
                }
            }
            return (name == null) ? UNKNOWN_PACKAGE : name;
        }

        return mClientInfo.getOrDefault(pid, UNKNOWN_PACKAGE);
    }

    @Override
    public void push(@NonNull String[] local, @NonNull String remote)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        Log.d(
                String.join(", ", local),
                String.format("Uploading %1$s onto device '%2$s'", remote, getSerialNumber()));
        try (SyncService sync = getSyncService()) {
            if (sync == null) {
                throw new IOException("Unable to open sync connection");
            }
            String message = String.format("Uploading file onto device '%1$s'", getSerialNumber());
            Log.d(LOG_TAG, message);
            sync.push(local, remote, SyncService.getNullProgressMonitor());
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;
        } catch (SyncException | IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;
        }
    }

    @Override
    public void pushFile(@NonNull String local, @NonNull String remote)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        String targetFileName = getFileName(local);
        Log.d(
                targetFileName,
                String.format(
                        "Uploading %1$s onto device '%2$s'", targetFileName, getSerialNumber()));

        try (SyncService sync = getSyncService()) {
            if (sync == null) {
                throw new IOException("Unable to open sync connection");
            }
            String message = String.format("Uploading file onto device '%1$s'", getSerialNumber());
            Log.d(LOG_TAG, message);
            sync.pushFile(local, remote, SyncService.getNullProgressMonitor());
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;
        } catch (SyncException | IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;
        }
    }

    @Override
    public void pullFile(String remote, String local)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        SyncService sync = null;
        try {
            String targetFileName = getFileName(remote);

            Log.d(
                    targetFileName,
                    String.format(
                            "Downloading %1$s from device '%2$s'",
                            targetFileName, getSerialNumber()));

            sync = getSyncService();
            if (sync != null) {
                String message =
                        String.format("Downloading file from device '%1$s'", getSerialNumber());
                Log.d(LOG_TAG, message);
                sync.pullFile(remote, local, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;

        } catch (SyncException | IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;
        } finally {
            if (sync != null) {
                sync.close();
            }
        }
    }

    @Override
    public void installPackage(String packageFilePath, boolean reinstall, String... extraArgs)
            throws InstallException {
        // Use default basic installReceiver
        installPackage(packageFilePath, reinstall, new InstallReceiver(), extraArgs);
    }

    @Override
    public void installPackage(
            String packageFilePath,
            boolean reinstall,
            InstallReceiver receiver,
            String... extraArgs)
            throws InstallException {
        // Use default values for some timeouts.
        installPackage(
                packageFilePath,
                reinstall,
                receiver,
                0L,
                INSTALL_TIMEOUT_MINUTES,
                TimeUnit.MINUTES,
                extraArgs);
    }

    @Override
    public void installPackage(
            String packageFilePath,
            boolean reinstall,
            InstallReceiver receiver,
            long maxTimeout,
            long maxTimeToOutputResponse,
            TimeUnit maxTimeUnits,
            String... extraArgs)
            throws InstallException {
        try {
            long uploadStartNs = System.nanoTime();
            String remoteFilePath = syncPackageToDevice(packageFilePath);
            long uploadFinishNs = System.nanoTime();
            installRemotePackage(
                    remoteFilePath,
                    reinstall,
                    receiver,
                    maxTimeout,
                    maxTimeToOutputResponse,
                    maxTimeUnits,
                    extraArgs);
            long installFinishNs = System.nanoTime();
            removeRemotePackage(remoteFilePath);
            lastInstallMetrics =
                    new InstallMetrics(
                            uploadStartNs, uploadFinishNs, uploadFinishNs, installFinishNs);
        } catch (IOException | AdbCommandRejectedException | TimeoutException | SyncException e) {
            throw new InstallException(e);
        }
    }

    @Override
    public void installPackages(
            @NonNull List<File> apks,
            boolean reinstall,
            @NonNull List<String> installOptions,
            long timeout,
            @NonNull TimeUnit timeoutUnit)
            throws InstallException {
        try {
            lastInstallMetrics =
                    SplitApkInstaller.create(this, apks, reinstall, installOptions)
                            .install(timeout, timeoutUnit);
        } catch (InstallException e) {
            throw e;
        } catch (Exception e) {
            throw new InstallException(e);
        }
    }

    @Override
    public void installPackages(
            @NonNull List<File> apks, boolean reinstall, @NonNull List<String> installOptions)
            throws InstallException {
        // Use the default single apk installer timeout.
        installPackages(apks, reinstall, installOptions, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public InstallMetrics getLastInstallMetrics() {
        return lastInstallMetrics;
    }

    @Override
    public void installRemotePackages(
            @NonNull List<String> remoteApks,
            boolean reinstall,
            @NonNull List<String> installOptions)
            throws InstallException {
        // Use the default installer timeout.
        installRemotePackages(
                remoteApks, reinstall, installOptions, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void installRemotePackages(
            @NonNull List<String> remoteApks,
            boolean reinstall,
            @NonNull List<String> installOptions,
            long timeout,
            @NonNull TimeUnit timeoutUnit)
            throws InstallException {
        try {
            RemoteSplitApkInstaller.create(this, remoteApks, reinstall, installOptions)
                    .install(timeout, timeoutUnit);
        } catch (InstallException e) {
            throw e;
        } catch (Exception e) {
            throw new InstallException(e);
        }
    }

    @Override
    public String syncPackageToDevice(String localFilePath)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        SyncService sync = null;
        try {
            String packageFileName = getFileName(localFilePath);
            String remoteFilePath = String.format("/data/local/tmp/%1$s", packageFileName);

            Log.d(
                    packageFileName,
                    String.format(
                            "Uploading %1$s onto device '%2$s'",
                            packageFileName, getSerialNumber()));

            sync = getSyncService();
            if (sync != null) {
                String message =
                        String.format("Uploading file onto device '%1$s'", getSerialNumber());
                Log.d(LOG_TAG, message);
                sync.pushFile(localFilePath, remoteFilePath, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
            return remoteFilePath;
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;

        } catch (SyncException | IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;
        } finally {
            if (sync != null) {
                sync.close();
            }
        }
    }

    /**
     * Helper method to retrieve the file name given a local file path
     *
     * @param filePath full directory path to file
     * @return {@link String} file name
     */
    private static String getFileName(String filePath) {
        return new File(filePath).getName();
    }

    @Override
    public void installRemotePackage(String remoteFilePath, boolean reinstall, String... extraArgs)
            throws InstallException {
        installRemotePackage(remoteFilePath, reinstall, new InstallReceiver(), extraArgs);
    }

    @Override
    public void installRemotePackage(
            String remoteFilePath,
            boolean reinstall,
            @NonNull InstallReceiver receiver,
            String... extraArgs)
            throws InstallException {
        installRemotePackage(
                remoteFilePath,
                reinstall,
                receiver,
                0L,
                INSTALL_TIMEOUT_MINUTES,
                TimeUnit.MINUTES,
                extraArgs);
    }

    @Override
    public void installRemotePackage(
            String remoteFilePath,
            boolean reinstall,
            @NonNull InstallReceiver receiver,
            long maxTimeout,
            long maxTimeToOutputResponse,
            TimeUnit maxTimeUnits,
            String... extraArgs)
            throws InstallException {
        try {
            StringBuilder optionString = new StringBuilder();
            if (reinstall) {
                optionString.append("-r ");
            }
            if (extraArgs != null) {
                optionString.append(Joiner.on(' ').join(extraArgs));
            }
            String cmd =
                    String.format(
                            "pm install %1$s \"%2$s\"", optionString.toString(), remoteFilePath);
            executeShellCommand(cmd, receiver, maxTimeout, maxTimeToOutputResponse, maxTimeUnits);
            String error = receiver.getErrorMessage();
            if (error != null) {
                throw new InstallException(error, receiver.getErrorCode());
            }
        } catch (TimeoutException
                | AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | IOException e) {
            throw new InstallException(e);
        }
    }

    @Override
    public void removeRemotePackage(String remoteFilePath) throws InstallException {
        try {
            executeShellCommand(
                    String.format("rm \"%1$s\"", remoteFilePath),
                    new NullOutputReceiver(),
                    INSTALL_TIMEOUT_MINUTES,
                    TimeUnit.MINUTES);
        } catch (IOException
                | TimeoutException
                | AdbCommandRejectedException
                | ShellCommandUnresponsiveException e) {
            throw new InstallException(e);
        }
    }

    @Override
    public String uninstallPackage(String packageName) throws InstallException {
        return uninstallApp(packageName, new String[] {});
    }

    @Override
    public String uninstallApp(String applicationID, String... extraArgs) throws InstallException {
        try {
            StringBuilder command = new StringBuilder("pm uninstall");

            if (extraArgs != null) {
                command.append(" ");
                Joiner.on(' ').appendTo(command, extraArgs);
            }

            command.append(" ").append(applicationID);

            InstallReceiver receiver = new InstallReceiver();
            executeShellCommand(
                    command.toString(), receiver, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            return receiver.getErrorMessage();
        } catch (TimeoutException
                | AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | IOException e) {
            throw new InstallException(e);
        }
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#reboot()
     */
    @Override
    public void reboot(String into)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.reboot(into, AndroidDebugBridge.getSocketAddress(), this);
    }

    @Override
    public boolean root()
            throws TimeoutException, AdbCommandRejectedException, IOException,
                    ShellCommandUnresponsiveException {
        if (!mIsRoot) {
            AdbHelper.root(AndroidDebugBridge.getSocketAddress(), this);
        }
        return isRoot();
    }

    @Override
    public boolean isRoot()
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        if (mIsRoot) {
            return true;
        }
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        executeShellCommand(
                "echo $USER_ID", receiver, QUERY_IS_ROOT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        String userID = receiver.getOutput().trim();
        mIsRoot = userID.equals("0");
        return mIsRoot;
    }

    @Override
    public Integer getBatteryLevel() {
        // use default of 5 minutes
        return getBatteryLevel(5 * 60 * 1000);
    }

    @Override
    public Integer getBatteryLevel(long freshnessMs) {
        Future<Integer> futureBattery = getBattery(freshnessMs, TimeUnit.MILLISECONDS);
        try {
            return futureBattery.get();
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public Future<Integer> getBattery() {
        return getBattery(5, TimeUnit.MINUTES);
    }

    @NonNull
    @Override
    public Future<Integer> getBattery(long freshnessTime, @NonNull TimeUnit timeUnit) {
        return mBatteryFetcher.getBattery(freshnessTime, timeUnit);
    }

    @NonNull
    @Override
    public List<String> getAbis() {
        return iDeviceSharedImpl.getAbis();
    }

    @Override
    public int getDensity() {
        return iDeviceSharedImpl.getDensity();
    }

    @Override
    public String getLanguage() {
        return getProperties().get(IDevice.PROP_DEVICE_LANGUAGE);
    }

    @Override
    public String getRegion() {
        return getProperty(IDevice.PROP_DEVICE_REGION);
    }

    public <T> @NonNull T computeUserDataIfAbsent(
            @NonNull Key<T> key, @NonNull Function<Key<T>, T> mappingFunction) {
        return mUserDataMap.computeUserDataIfAbsent(key, mappingFunction);
    }

    @Override
    public <T> @Nullable T getUserDataOrNull(@NonNull Key<T> key) {
        return mUserDataMap.getUserDataOrNull(key);
    }

    public <T> @Nullable T removeUserData(@NonNull Key<T> key) {
        return mUserDataMap.removeUserData(key);
    }
}
