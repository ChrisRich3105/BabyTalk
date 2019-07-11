package com.example.babytalk;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    private static final String LOG_TAG = "MonitorService"; // LOG-Tag
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set preferences from ressource file
        //noinspection deprecation
        addPreferencesFromResource(R.xml.preferences);

        // Now register onChangeListener for special preferences
        //noinspection deprecation
        Preference phoneNumberPref = findPreference(getString(R.string.preference_phonenumber_key));
        phoneNumberPref.setOnPreferenceChangeListener(this);
        //noinspection deprecation
        Preference pauseValuePref = findPreference(getString(R.string.preference_pause_value_key));
        pauseValuePref.setOnPreferenceChangeListener(this);
        //noinspection deprecation
        Preference accelerationValuePref = findPreference(getString(R.string.preference_motion_value_key));
        accelerationValuePref.setOnPreferenceChangeListener(this);
        //noinspection deprecation
        Preference pausePref = findPreference(getString(R.string.preference_pause_key));
        pausePref.setOnPreferenceChangeListener(this);
        //noinspection deprecation
        Preference accelerationPref = findPreference(getString(R.string.preference_motion_key));
        accelerationPref.setOnPreferenceChangeListener(this);

        // read out preference settings initially
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String savedPhoneNumber = sharedPrefs.getString(phoneNumberPref.getKey(), "");
        if (!savedPhoneNumber.isEmpty())
            onPreferenceChange(phoneNumberPref, savedPhoneNumber);
        String savedPauseValue = String.valueOf(sharedPrefs.getInt(pauseValuePref.getKey(), 30));
        if (!savedPauseValue.isEmpty())
            onPreferenceChange(pauseValuePref, savedPauseValue);
        String accelerationValue = String.valueOf(sharedPrefs.getInt(accelerationValuePref.getKey(), 50));
        if (!accelerationValue.isEmpty())
            onPreferenceChange(accelerationValuePref, accelerationValue);

        // Set enabled options based on various settings
        boolean pauseActivated = sharedPrefs.getBoolean(getString(R.string.preference_pause_key),false);
        if(!pauseActivated) {
            findPreference(getString(R.string.preference_pause_value_key)).setEnabled(false);
            findPreference(getString(R.string.preference_pause_value_key)).setShouldDisableView(true);
        }
        else {
            findPreference(getString(R.string.preference_pause_value_key)).setEnabled(true);
            findPreference(getString(R.string.preference_pause_value_key)).setShouldDisableView(false);
        }
        boolean motionActivated = sharedPrefs.getBoolean(getString(R.string.preference_motion_key),false);
        if(!motionActivated) {
            findPreference(getString(R.string.preference_motion_value_key)).setEnabled(false);
            findPreference(getString(R.string.preference_motion_value_key)).setShouldDisableView(true);
        }
        else {
            findPreference(getString(R.string.preference_motion_value_key)).setEnabled(true);
            findPreference(getString(R.string.preference_motion_value_key)).setShouldDisableView(false);
        }

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        // Update various preference summaries when their values are changed
        if(preference.getKey()==getString(R.string.preference_pause_value_key))
            preference.setSummary("Time in seconds to pause monitoring after start.\nCurrent setting: " + value.toString() + "s");
        else if(preference.getKey()==getString(R.string.preference_motion_value_key))
            preference.setSummary("Motion level to activate call. Lower values mean more sensitive triggering.\nCurrent setting: " + value.toString() + "%");
        // Change enabled settings when certain functions are disabled
        else if(preference.getKey()==getString(R.string.preference_pause_key)){
            if(!Boolean.parseBoolean(value.toString())) {
                findPreference(getString(R.string.preference_pause_value_key)).setEnabled(false);
                findPreference(getString(R.string.preference_pause_value_key)).setShouldDisableView(true);
            }
            else {
                findPreference(getString(R.string.preference_pause_value_key)).setEnabled(true);
                findPreference(getString(R.string.preference_pause_value_key)).setShouldDisableView(false);
                // Need to renew the activity
                finish();
                overridePendingTransition(0, 0);
                startActivity(getIntent());
                overridePendingTransition(0, 0);
            }
        }
        else if(preference.getKey()==getString(R.string.preference_motion_key)){
            if(!Boolean.parseBoolean(value.toString())) {
                findPreference(getString(R.string.preference_motion_value_key)).setEnabled(false);
                findPreference(getString(R.string.preference_motion_value_key)).setShouldDisableView(true);
            }
            else {
                findPreference(getString(R.string.preference_motion_value_key)).setEnabled(true);
                findPreference(getString(R.string.preference_motion_value_key)).setShouldDisableView(false);
                // Need to renew the activity
                finish();
                overridePendingTransition(0, 0);
                startActivity(getIntent());
                overridePendingTransition(0, 0);
            }
        }

        return true;
    }
}
