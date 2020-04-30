
/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.ddmlib.ClientData.DebuggerStatus.ATTACHED;
import static com.android.ddmlib.ClientData.DebuggerStatus.DEFAULT;
import static com.android.ddmlib.ClientData.DebuggerStatus.WAITING;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.fakeadbserver.DeviceState;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class ClientLifecycleIntegrationTest {
  public @Rule FakeAdbTestRule myFakeAdb = new FakeAdbTestRule();

  @Test
  public void clientCreation() throws Exception {
    assertThat(AndroidDebugBridge.getBridge().getDevices()).isEmpty();
    // Connect a test device.
    DeviceState state = myFakeAdb.connectAndWaitForDevice();
    assertThat(AndroidDebugBridge.getBridge().getDevices()).hasLength(1);
    IDevice device = AndroidDebugBridge.getBridge().getDevices()[0];
    assertThat(device.getClients()).isEmpty();
    // Bring up a basic client
    myFakeAdb.launchAndWaitForProcess(state, true);
    assertThat(device.getClients()).hasLength(1);
  }

  @Test
  public void createAndKillClient() throws Exception {
    assertThat(AndroidDebugBridge.getBridge().getDevices()).isEmpty();
    // Connect a test device.
    DeviceState state = myFakeAdb.connectAndWaitForDevice();
    myFakeAdb.launchAndWaitForProcess(state, true);
    IDevice device = AndroidDebugBridge.getBridge().getDevices()[0];
    CountDownLatch latch = new CountDownLatch(1);
    AndroidDebugBridge.addDeviceChangeListener(new AndroidDebugBridge.IDeviceChangeListener() {
      @Override
      public void deviceConnected(@NonNull IDevice device) { }

      @Override
      public void deviceDisconnected(@NonNull IDevice device) {}

      @Override
      public void deviceChanged(@NonNull IDevice device, int changeMask) {
        latch.countDown();
      }
    });
    device.getClients()[0].kill();
    // Block until we get a deviceChanged notification.
    assertThat(latch.await(FakeAdbTestRule.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(device.getClients()).isEmpty();
  }

  @Test
  public void clientDefaultState() throws Exception {
    assertThat(AndroidDebugBridge.getBridge().getDevices()).isEmpty();
    DeviceState state = myFakeAdb.connectAndWaitForDevice();
    IDevice device = AndroidDebugBridge.getBridge().getDevices()[0];
    myFakeAdb.launchAndWaitForProcess(state, true);
    ClientImpl client = (ClientImpl)device.getClient(FakeAdbTestRule.CLIENT_PACKAGE_NAME);
    assertThat(client.isDebuggerAttached()).isFalse();
    assertThat(client.isValid()).isTrue();
    assertThat(client.getDebuggerListenPort()).isNotEqualTo(0);
    assertThat(client.getDebugger().getConnectionState()).isEqualTo(Debugger.ConnectionState.ST_NOT_CONNECTED);
    assertThat(client.getClientData().getDebuggerConnectionStatus()).isAnyOf(DEFAULT, WAITING);
  }

  @Test
  @Ignore("Flaky b/155419945")
  public void attachDebugger() throws Exception {
    assertThat(AndroidDebugBridge.getBridge().getDevices()).isEmpty();
    DeviceState state = myFakeAdb.connectAndWaitForDevice();
    IDevice device = AndroidDebugBridge.getBridge().getDevices()[0];
    myFakeAdb.launchAndWaitForProcess(state, true);
    ClientImpl client = (ClientImpl)device.getClient(FakeAdbTestRule.CLIENT_PACKAGE_NAME);
    CountDownLatch hasDebuggerStateChangedEvent = new CountDownLatch(1);
    AndroidDebugBridge.addClientChangeListener(new AndroidDebugBridge.IClientChangeListener() {
      @Override
      public void clientChanged(@NonNull Client client, int changeMask) {
        if((changeMask & Client.CHANGE_DEBUGGER_STATUS) != 0) {
          hasDebuggerStateChangedEvent.countDown();
        }
      }
    });
    assertThat(client.getDebugger().getListenPort()).isEqualTo(client.getDebuggerListenPort());
    SocketChannel debugger = SocketChannel.open(new InetSocketAddress("localhost", client.getDebuggerListenPort()));
    FakeAdbTestRule.issueHandshake(debugger);
    // Because of threading we may get back the handshake before the state of the debugger is updated.
    // as such we need to wait for our latch to be triggered to not have a flaky test.
    assertThat(hasDebuggerStateChangedEvent.await(FakeAdbTestRule.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(client.getClientData().getDebuggerConnectionStatus()).isEqualTo(ATTACHED);
    assertThat(client.getDebugger().getConnectionState()).isEqualTo(Debugger.ConnectionState.ST_READY);
  }
}