/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.nordicsemi.UART_UDP_PROXY;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.arm.mbed.UartRPC;
import com.arm.mbed.UartRPCCallbacks;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class UartService extends Service implements UartRPCCallbacks {
	public static final String TAG = "PROXY";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    protected int mConnectionState = STATE_DISCONNECTED;
    
    // DA
    private UartRPC m_uart_rpc = null;
    private MainActivity m_ui = null;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.nordicsemi.nrfUART.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.nordicsemi.nrfUART.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.nordicsemi.nrfUART.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.nordicsemi.nrfUART.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.nordicsemi.nrfUART.EXTRA_DATA";
    public final static String DEVICE_DOES_NOT_SUPPORT_UART =
            "com.nordicsemi.nrfUART.DEVICE_DOES_NOT_SUPPORT_UART";
    
    public static final UUID TX_POWER_UUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb");
    public static final UUID TX_POWER_LEVEL_UUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb");
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID FIRMWARE_REVISON_UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    public static final UUID DIS_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
   
    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            	Log.w(TAG, "mBluetoothGatt: " + mBluetoothGatt );
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        if (TX_CHAR_UUID.equals(characteristic.getUuid())) {
           // Log.d(TAG, String.format("Received TX: %d",characteristic.getValue() ));
           intent.putExtra(EXTRA_DATA, characteristic.getValue());
        } else {
        	
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
        // DA
        onDataReceived(characteristic.getStringValue(0));
    }
    
    public UartService setUartRPC(UartRPC rpc) {
    	this.m_uart_rpc = rpc;
    	this.m_uart_rpc.setCallbackHandler(this);
    	return this;
    }

    public class LocalBinder extends Binder {
        UartService getService(UartRPC rpc) {
            return UartService.this.setUartRPC(rpc);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize(MainActivity ui) {
    	// DA
    	this.m_ui = ui;
    	
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
        
        // DA
        this.disconnectSocket();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
    	this.disconnect();
        if (mBluetoothGatt == null) {
            return;
        }
        Log.w(TAG, "mBluetoothGatt closed");
        mBluetoothDeviceAddress = null;
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        
        // DA
        this.m_uart_rpc = new UartRPC(this);
        this.m_uart_rpc.setCallbackHandler(this);
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }
    
    /**
     * Enable TXNotification
     *
     * @return 
     */
    public void enableTXNotification()
    { 
    	BluetoothGattService RxService = mBluetoothGatt.getService(RX_SERVICE_UUID);
    	if (RxService == null) {
            showMessage("Rx service not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
    	BluetoothGattCharacteristic TxChar = RxService.getCharacteristic(TX_CHAR_UUID);
        if (TxChar == null) {
            showMessage("Tx charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(TxChar,true);
        
        BluetoothGattDescriptor descriptor = TxChar.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    	
    }
    
    public void writeRXCharacteristic(byte[] value)
    {
    	BluetoothGattService RxService = mBluetoothGatt.getService(RX_SERVICE_UUID);
    	showMessage("mBluetoothGatt: "+ mBluetoothGatt);
    	if (RxService == null) {
            showMessage("Rx service not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
    	BluetoothGattCharacteristic RxChar = RxService.getCharacteristic(RX_CHAR_UUID);
        if (RxChar == null) {
            showMessage("Rx charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        RxChar.setValue(value);
    	boolean status = mBluetoothGatt.writeCharacteristic(RxChar);
    	
        Log.d(TAG, "write RXchar - status=" + status);  
    }
    
    private void showMessage(String msg) {
        Log.d(TAG, msg);
    }
    
    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
    
    // DA
    @Override
	public void ackSocketOpen(boolean open_ok) {
		int opened = 0;
		if (open_ok) opened = 1;
		String socket_open_ack = "[1|" + opened + "]";
		
		Log.d(TAG, "ackSocketOpen(): ack: " + socket_open_ack + " being sent for socket() open status...");
		this.sendData(socket_open_ack);
	}
    
    // DA
    @Override
    public void disconnectSocket() {
		if (this.m_uart_rpc != null) this.m_uart_rpc.close();
	}
    
    // DA
    @Override
    public boolean splitAndSendData(final String data) {
    	boolean sent = false;
    	List<String> list = this.splitEqually(data,20);
    	if (list != null && list.size() > 0) {
    		for(int i=0;i<list.size();++i) {
    			this.sendData(list.get(i));
    		}
    		sent = true;
    	}
    	return sent;
    }
    
    // DA
    @Override
 	public void sendData(final String data) {
    	this.m_ui.logAction("UDP", "UART", data.getBytes());
    	this.writeRXCharacteristic(data.getBytes());
 	}
 	
    // DA
    private List<String> splitEqually(String text, int size) {
	    // Give the list the right capacity to start with. You could use an array
	    // instead if you wanted.
	    List<String> ret = new ArrayList<String>();

	    for (int start = 0; start < text.length(); start += size) {
	    	String tmp = text.substring(start, Math.min(text.length(), start + size));
	    	while(tmp.length() < size) tmp += " ";
	    	Log.d(TAG, "splitEqually(): adding " + tmp + "...");
	        ret.add(tmp);
	    }
	    return ret;
	}
    
    // DA
	@Override
	public void sendOverUART(byte[] data, int length) {
		String packet = this.m_uart_rpc.rpc_recv_data(data,length);
		if (packet != null) {
			Log.d(TAG, "sendOverUART(): encoded_data=[" + packet + "] length: " + packet.length() + "... splitting and sending...");
			this.splitAndSendData(packet);
		}
		else {
			Log.d(TAG, "sendOverUART(): dropping null packet. Base64 encoding failed..");
		}
	}
	
	// DA
	@Override
	public void onDataReceived(final String data) {
		if (data != null && data.length() > 0) {
			String trimmed_data = this.m_uart_rpc.trimData(data);
			
			// if we received an ACK... we will ignore it... 
			if (trimmed_data.equalsIgnoreCase("ACK") == false) {
				Log.d(TAG, "onDataReceived(): data: " + trimmed_data + " length: " + trimmed_data.length());
				if (this.m_uart_rpc.accumulate(trimmed_data)) {
					this.m_ui.logAction("UART", "UDP", this.m_uart_rpc.getAccumulation().getBytes());
					this.m_uart_rpc.dispatch();
				}
				
				// send back an ACK byte... this will reset the notifications and will allow the peer to continue...
				this.sendData("ACK");
			}
			else {
				// just received an ACK... so no need to reply with another...
				this.m_ui.logAction("UART", "UDP", trimmed_data.getBytes());
				Log.d(TAG, "onDataReceived(): recevied ACK (OK)... continuing...");
			}
		}
	}
}
