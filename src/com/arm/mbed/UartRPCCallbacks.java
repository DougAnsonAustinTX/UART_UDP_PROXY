package com.arm.mbed;

public interface UartRPCCallbacks {
		public void sendOverUART(byte data[],int length);
		public void ackSocketOpen(boolean open_ok);
		public void onDataReceived(final String data);
		public boolean splitAndSendData(final String data);
		public void sendData(final String data);
		public void disconnectSocket();
}
