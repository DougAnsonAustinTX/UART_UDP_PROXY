package com.arm.mbed;

import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;

public class MyLocation extends TimerTask implements LocationListener {
	public static final String TAG = "PROXY";
	private boolean haveLocation = false;
	private String location = "";
	private boolean enabled = false;
	private Location myLocation = null;
	private LocationManager lm = null;
	private Timer timer1 = null;
	private static int TIMER_STARTUP_DELAY_MS = 10000;			// delay the timer initially for 10 seconds
	private static int DEFAULT_TIMER_INTERVAL_MS = 20000;		// query for location every 30 seconds
	
	// constructor
	public MyLocation(Context context) {
		this.myLocation = null;
		this.location = "0.0:0.0:-1.0:-1.0";
		this.initLocationListener(context);
		if (this.enabled) this.initTimer();
	}
	
	private void initLocationListener(Context context) {
		if (this.lm == null) this.lm = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
		if (this.lm != null) this.checkEnabled();
	}
	
	private void initTimer() {
		if (this.enabled && this.timer1 == null) {
			this.timer1 = new Timer();
			this.timer1.schedule(this,MyLocation.TIMER_STARTUP_DELAY_MS,MyLocation.DEFAULT_TIMER_INTERVAL_MS);
		}
	}
	
	private boolean checkEnabled() {
		this.enabled = false;
		if (this.lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			this.lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
			this.enabled = true;
		}
		if (this.lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			this.lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
			this.enabled = true;
		}
		return this.enabled;
	}
	
	public void updateLocation() {
		this.parseLocation();
	}
	
	private void parseLocation() {
		if (this.enabled && this.haveLocation) {
			this.location = "" + String.format("%.5f",this.myLocation.getLatitude()) + ":" +  
								 String.format("%.5f",this.myLocation.getLongitude()) + ":" +
								 String.format("%.1f",this.myLocation.getAltitude()) + ":" +
								 String.format("%.1f",this.myLocation.getSpeed());
		}
		
		// DEBUG
		Log.d(TAG,"Location: " + this.location);
	}
	
	private void updateLocation(Location location) {
		if (location != null)  {
			this.myLocation = location;
			this.haveLocation = true;
		}
	}
	
	public String getLocation() {
		return this.location;
	}

	@Override
	public void onLocationChanged(Location location) {
		// TODO Auto-generated method stub
		if (this.lm != null) this.lm.removeUpdates(this);
		this.updateLocation(location);
		this.parseLocation();
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void run() {
		Location gps_loc = null;
		Location net_loc = null;
		Location last_loc = null;
		
		this.lm.removeUpdates(this);
		if (this.lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			gps_loc = this.lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		}
		if (this.lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			net_loc = this.lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		}
		
		last_loc = net_loc;
		
		if (gps_loc != null && net_loc != null) {
			if (gps_loc.getTime() > net_loc.getTime()) {
				last_loc = gps_loc;
			}
		}
		
		// update our location
		this.updateLocation(last_loc);
		this.parseLocation();
	}
}
