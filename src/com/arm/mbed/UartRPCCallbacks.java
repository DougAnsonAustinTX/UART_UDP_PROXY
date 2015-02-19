package com.arm.mbed;

public interface UartRPCCallbacks {
		public void sendOverUART(byte data[],int length);
		public void ackSocketOpen(boolean open_ok);
		void onDataReceived(final String data);
		void sendData(final String data);
		public void disconnectSocket();
}
