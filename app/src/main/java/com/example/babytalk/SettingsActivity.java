package com.example.babytalk;

import android.app.ActionBar;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.widget.Toast;

public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection deprecation
        addPreferencesFromResource(R.xml.preferences);

        //noinspection deprecation
        Preference phoneNumberPref = findPreference(getString(R.string.preference_phonenumber_key));
        phoneNumberPref.setOnPreferenceChangeListener(this);
        //noinspection deprecation
        Preference pauseValuePref = findPreference(getString(R.string.preference_pause_value_key));
        pauseValuePref.setOnPreferenceChangeListener(this);


        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String savedPhoneNumber = sharedPrefs.getString(phoneNumberPref.getKey(), "");
        onPreferenceChange(phoneNumberPref, savedPhoneNumber);
        String savedPauseValue = sharedPrefs.getString(pauseValuePref.getKey(), "");
        onPreferenceChange(pauseValuePref, savedPauseValue);

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        if(preference.getKey()==getString(R.string.preference_phonenumber_key))
            preference.setSummary("Phone number to be called when monitoring triggers.\nCurrent setting: "+value.toString());
        else if(preference.getKey()==getString(R.string.preference_pause_value_key)) {
            if (Integer.parseInt(value.toString()) >= 100) {
                Toast.makeText(this, "Pause time must be below 100s", Toast.LENGTH_SHORT).show();
                return false;
            }
            preference.setSummary("Time in seconds to pause monitoring after start.\nCurrent setting: " + value.toString() + "s");
        }
        else
            preference.setSummary(value.toString());

        return true;
    }
}
