package com.arm.mbed;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;


@SuppressWarnings("unused")
public class PreferenceManager {
	private SharedPreferences mSharedPrefs = null;
	private SharedPreferences.Editor mEditor = null;
	private boolean default_boolean = false;
		
	// Constructor
	public PreferenceManager(Context ctx, String appName) {
		this.mSharedPrefs = ctx.getSharedPreferences(appName,Context.MODE_PRIVATE);
	}
	    		
	// set an float preference
	public void setPreference(String name, double value) {
		this.setPreference(name, "" + value);
	}
		
	// set an int preference
	public void setPreference(String name, int value) {
		this.setPreference(name, "" + value);
	}
	
	// set a boolean preference
	public void setPreference(String name, boolean value) {
		this.setPreference(name, "" + this.convert(value));
	}
		
	// get a float preference
	public double getFloatPreference(String name, double def) {
		return (double)this.getFloatPreference(name, "" + def);
	}
		
	// get a preference
	public int getIntPreference(String name, int def) {
		return (int)this.getIntPreference(name, "" + def);
	}
	
	// method to convert an int to a boolean
	private boolean convert(int val) {
		boolean bval = false;
		if (val > 0)
			bval = true;
		return bval;
	}
	
	// method to convert a boolean to an int
	private int convert(boolean bval) {
		int ival = 0;
		if (bval == true)
			ival = 1;
		return ival;
	}
	// get a boolean preference
	public boolean getBooleanPreference(String name) {
		return this.convert((int)this.getIntPreference(name, "" + this.convert(this.default_boolean)));
	}
		
	// get a boolean preference
	public boolean getBooleanPreference(String name, boolean def) {
		return this.convert((int)this.getIntPreference(name, "" + this.convert(def)));
	}
		
	// get a preference
	public int getIntPreference(String name) {
		return (int)this.getIntPreference(name, "-1");
	}
	
	// get a preference
	public double getFloatPreference(String name) {
		return (double)this.getFloatPreference(name, "-1.0");
	}
	
	// get a preference
	public String getPreference(String name) {
		return (String)this.getPreference(name,"");
	}
	
	// get a prefrence (double)
	public double getFloatPreference(String name, String def) {
		double value = -1;
		String s_pref = (String)this.getPreference(name,def);
		try {
			if (s_pref != null && s_pref.length() > 0 && Double.valueOf(s_pref) >= 0)
				value = Double.valueOf(s_pref);
			else
				value = Double.valueOf(def);
		}
		catch(Exception ex) {
			try {
				value = Double.valueOf(def);
			}
			catch(Exception ex2) {
				value = -999;
			}
		}
		
		// return the double value
		return value;
	}
	
	// get a preference (int)
	public int getIntPreference(String name, String def) {
		int value = -1;
		String s_pref = (String)this.getPreference(name,def);
		try {
			if (s_pref != null && s_pref.length() > 0 && Integer.valueOf(s_pref) >= 0)
				value = Integer.valueOf(s_pref);
			else
				value = Integer.valueOf(def);
		}
		catch(Exception ex) {
			try {
				value = Integer.valueOf(def);
			}
			catch(Exception ex2) {
				value = -999;
			}
		}
		
		// return the integer value
		return value;
	}
	
	// set a preference
	public void setPreference(String name, String value) {		
		// initialize the preference editor (SLOW)
		if (this.mSharedPrefs != null && this.mEditor == null)
			this.mEditor = this.mSharedPrefs.edit();
		
		// Set the preference if we can
		if (this.mEditor != null) {
			this.mEditor.putString(name, value);
			this.mEditor.commit();
		}
	}
	
	// set a preference
	public void deletePreference(String name) {
		// initialize the preference editor (SLOW)
		if (this.mSharedPrefs != null && this.mEditor == null) {
			this.mEditor = this.mSharedPrefs.edit();
		}
		
		// Set the preference if we can
		if (this.mEditor != null) {
			this.mEditor.remove(name);
			this.mEditor.commit();
		}
	}

	// get a preference
	public String getPreference(String name, String def) {
		String val = null;
		
		// Get the preference
		if (this.mSharedPrefs != null)
			val = this.mSharedPrefs.getString(name, def);
				
		// Use default if no preference exists
		if (val == null || val.length() == 0) val = def;
				
		// return the preference
		return val;
	}
}
