package com.arm.mbed;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.util.Base64;
import android.util.Log;

public class UartRPC {
	public static final String      TAG = "UARTUDPPROXY";
	private static final String		_DELIMITER = "\\|";
	private static final String		_HEAD = "[";
	private static final String		_TAIL = "]";
	
	// RPC functions supported:
	private static final int 		SOCKET_OPEN_FN  = 0x01;
	private static final int 	    SOCKET_CLOSE_FN = 0x02;
	private static final int 	    SEND_DATA_FN    = 0x04;
	private static final int 	    RECV_DATA_FN    = 0x08;
	
	// UDP Socket Config
	private static final int	    SOCKET_TIMEOUT_MS = 10000;			// 10 seconds
	private static final int		SOCKET_BUFFER_SIZE = 4096;			// socket buffer size
	
	private InetAddress 			m_address = null;
	private int    					m_port = 0;
	
	private UartRPCCallbacks        m_handler = null;
	private DatagramSocket 			m_socket = null;
	private Runnable 				m_listener = null;
	private Thread				    m_listener_thread = null;
	private boolean					m_do_run_listener = false;
	
	private String					m_args = "";
	private String					m_accumulator = "";
	private String					m_response = "";
	private int						m_fn_id = 0;
	
	private boolean					m_send_status = false;
	private DatagramPacket			m_send_packet = null;
				
	public UartRPC(UartRPCCallbacks handler) {
		this.reset();
		this.m_handler = handler;
	}
	
	private void stopListener() {
		if (this.m_listener_thread  != null) {
			try {
				this.m_do_run_listener = false;
				this.m_listener_thread.join((SOCKET_TIMEOUT_MS+1000));
				this.m_listener_thread = null;
				this.m_listener = null;
			}
			catch (Exception ex) {
				Log.d(TAG, "stopListener(): exception caught during listener thread stop(): " + ex.getMessage());
			}
		}
	}
	
	private void reset() {
		this.m_args = "";
		this.m_accumulator = "";
		this.m_response = "";
		this.m_fn_id = 0;
	}
	
	public boolean accumulate(String data) {
		boolean do_dispatch = false;
		
		if (data != null && data.length() > 0) {
			// accumulate...
			this.m_accumulator += data;
			
			// see if we have everything...
			if (this.m_accumulator.contains(_HEAD) && this.m_accumulator.contains(_TAIL)) {
				// ready to dispatch
				Log.d(TAG, "accumulate(): packet ready for dispatch...");
				do_dispatch = true;
			}
			else {
				// continue accumulating
				Log.d(TAG, "accumulate(): continue accumulating...");
			}
		}
		else if (data != null) {
			Log.d(TAG, "accumulate(): data length is 0... ignoring...");
		}
		else {
			Log.d(TAG, "accumulate(): data is NULL... ignoring...");
		}
		
		return do_dispatch;
	}
	
	public String getAccumulation() { return this.m_accumulator; }; 
	public boolean dispatch() { return this.dispatch(this.parse(this.m_accumulator)); }
	
	private String[] parse(String data) {
		// remove HEAD and TAIL
		String tmp1 = data.replace(_HEAD,"");
		String tmp2 = tmp1.replace(_TAIL,"");
		
		// split by delimiter now...
		return tmp2.split(_DELIMITER);
	}
	
	private boolean dispatch(String rpc_call[]) {
		boolean success = false;
		
		// slot 0 is the RPC command fn id, slot 1 is the RPC args...
		try {
			this.m_fn_id = Integer.parseInt(rpc_call[0]);
			this.m_args = rpc_call[1];
			Log.d(TAG,"dispatch(): fn_id=" + this.m_fn_id + " args: [" + this.m_args + "]");
			
			// dispatch to appropriate function for processing
			switch (this.m_fn_id) {
				case SOCKET_OPEN_FN: 
					success = this.rpc_socket_open(this.m_args);
					this.m_handler.ackSocketOpen(success);
					break;
				case SOCKET_CLOSE_FN:
					success = this.rpc_socket_close(this.m_args);
					break;
				case SEND_DATA_FN:
					success = this.rpc_send_data(this.m_args);
					break;
				default:
					Log.d(TAG,"dispatch(): IMPROPER fn_id=" + this.m_fn_id + " args: [" + this.m_args + "]... ignoring...");
					break;
			}
		}
		catch (Exception ex) {
			Log.d(TAG,"dispatch(): Exception in dispatch(): " + ex.getMessage());
			ex.printStackTrace();
		}
		
		// reset if successful...
		this.reset();
		
		// return our status
		return success;
	}
	
	// RPC: open_socket()
	private boolean rpc_socket_open(String data) {
		try {
			String args[] = data.split(" ");
			
			// parse args
			this.m_address = InetAddress.getByName(args[0].trim());
			this.m_port = Integer.parseInt(args[1]);
			
			// open the socket... 
			Log.d(TAG, "rpc_open_socket(): opening UDP Socket: " + args[0].trim() + "@" + this.m_port);
			this.m_socket = new DatagramSocket(this.m_port);
			this.m_socket.setSoTimeout(SOCKET_TIMEOUT_MS);
			this.m_socket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
			this.m_socket.setSendBufferSize(SOCKET_BUFFER_SIZE);
			this.m_socket.setBroadcast(false);
			this.m_socket.setReuseAddress(true);
			Log.d(TAG, "rpc_open_socket(): creating the listeners...");
			this.createListener();
			
			return true;
		}
		catch(Exception ex) {
			Log.d(TAG, "rpc_open_socket(): openSocket() failed: " + ex.getMessage() + "... closing...");
			this.rpc_socket_close(null);
		}
		
		return false;
	}

	public void close() { this.rpc_socket_close(null); }
	
	// RPC: close_socket()
	private boolean rpc_socket_close(String args) {
		Log.d(TAG, "close(): stopping listener...");
		this.stopListener();
		Log.d(TAG, "close(): closing socket...");
		this.closeSocket();
		Log.d(TAG, "close(): resetting to default...");
		this.reset();
		Log.d(TAG, "close(): completed.");
		return true;
	}
		
    private void closeSocket() {
    	if (this.m_socket != null) {
    		this.m_socket.close();
    		this.m_socket.disconnect();
    	}
    	this.m_socket = null;
    }
	
	private void createListener() {
		this.m_do_run_listener = true;
		if (this.m_listener == null) { 
			this.m_listener = new Runnable() {
				@Override
				public synchronized void run() {
					byte[] receiveData = new byte[SOCKET_BUFFER_SIZE];
					while (m_do_run_listener) {
						DatagramPacket p = new DatagramPacket(receiveData,receiveData.length);
						Log.d(TAG, "listener(): waiting on receive()...");
						try {
							m_socket.receive(p);
							Log.d(TAG, "listener(): received data... processing...");
							byte[] data = p.getData();
							int data_length = p.getLength();
							if (data != null && data.length > 0) {
								Log.d(TAG, "listener(): data length: " + data_length + " (data.length=" + data_length + ") ... sending over UART...");
								m_handler.sendOverUART(data,data_length);
								Log.d(TAG, "listener(): send over UART completed");
							}
						}
						catch (java.net.SocketTimeoutException ex) {
							Log.d(TAG, "listener(): timed out... retrying receive...");
			        	}
						catch (Exception ex) {
							Log.d(TAG, "listener(): exception during receive(): " + ex.getMessage());
							ex.printStackTrace();
						}
					}
					Log.d(TAG, "listener(): exiting listener loop...");
				}
			};
			
			try {
				this.m_listener_thread = new Thread(this.m_listener);
				this.m_listener_thread.start();
			}
			catch (Exception ex) {
				Log.d(TAG, "listener(): exception during thread start(): " + ex.getMessage()); 
			}
		}
	}
	
	// RPC: recv data
	public String rpc_recv_data(byte data[],int length) {
		// encode the data
		String encoded_data = this.encode(data,length);
		if (encoded_data != null) {
			// create the header and frame
			String frame = "" + RECV_DATA_FN + "|" + encoded_data;
			return _HEAD + frame.trim() + _TAIL;
		}
		return null;
	}
	
	// RPC: send data
	public boolean rpc_send_data(String data) {
		this.m_send_status = false;
		
		// decode out of Base64...
		byte[] raw_bytes = this.decode(data);
		
		// create a UDP datagram...
		this.m_send_packet = new DatagramPacket(raw_bytes,raw_bytes.length,this.m_address,this.m_port);
		
		// dispatch a thread off the main UI thread if everything is OK...
		if (this.m_socket != null && raw_bytes != null && raw_bytes.length > 0) {			
			// spawn a thread to handle this to get off the UI thread
			Thread thread = new Thread(new Runnable(){
			    @Override
			    public synchronized void run() {
		        	try {		    			
						Log.d(TAG,"send() sending...");
						m_socket.send(m_send_packet);
						m_send_status = true;
						Log.d(TAG, "send() successful.");
		        	}
					catch (Exception ex) {
						Log.d(TAG, "send() failed: " + ex.getMessage());
						//ex.printStackTrace();
					}
			    }
			});
			thread.start();
		}
		else if (this.m_socket != null) {
			Log.d(TAG, "send() failed: as data was null or had zero length");
		}
		else {
			Log.d(TAG, "send() failed: as socket was null");
		}
		return this.m_send_status;
	}
	
	public byte[] decode(String data) {
		try {
			byte[] b64_data = data.getBytes();
			return Base64.decode(b64_data, Base64.DEFAULT);
		}
		catch (Exception ex) {
			Log.d(TAG,"decode() caught exception while trying to decode: [" + data + "]. length: " + data.length() + " Message: " + ex.getMessage());
			ex.printStackTrace();
			
			Log.d(TAG,"decode() (EXCEPTION): just returning input data: [" + data + "]...");
			byte[] raw_data = data.getBytes();
			return raw_data;
		}
	}
	
	public String encode(byte data[],int length) {
		byte[] encoded = Base64.encode(data, 0, length, Base64.DEFAULT);
		try {
			return new String(encoded,"UTF-8");
		}
		catch (Exception ex) { 
			Log.d(TAG,"encode() caught exception while trying to encode " + length + " bytes. Exception: " + ex.getMessage());
			ex.printStackTrace();
		}
		return null;
	}
	
	public String trimData(String data) {
		return data.replace("[\n\n]","").replace("[\n]","").trim();
	}
}
