package top.galqq.ui;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.SwitchPreference;
import android.util.Log;
import top.galqq.R;
import top.galqq.config.ConfigManager;

public class GalSettingsActivity extends PreferenceActivity {
    
    private static final String TAG = "GalQQ.SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.i(TAG, "onCreate called");
        
        try {
            setTitle("GalQQ 设置");
            addPreferencesFromResource(R.xml.preferences_gal);
            
            // Initialize MMKV
            ConfigManager.init(this);
            
            // Bind preferences to MMKV
            bindPreferences();
            
            Log.i(TAG, "Preferences loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load preferences: " + e.getMessage(), e);
        }
    }

    private void bindPreferences() {
        // Module Enable Switch
        SwitchPreference enableSwitch = (SwitchPreference) findPreference(ConfigManager.KEY_ENABLED);
        if (enableSwitch != null) {
            enableSwitch.setChecked(ConfigManager.isModuleEnabled());
            enableSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setModuleEnabled((Boolean) newValue);
                return true;
            });
        }

        // AI Enable Switch
        SwitchPreference aiEnableSwitch = (SwitchPreference) findPreference(ConfigManager.KEY_AI_ENABLED);
        if (aiEnableSwitch != null) {
            aiEnableSwitch.setChecked(ConfigManager.isAiEnabled());
            aiEnableSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setAiEnabled((Boolean) newValue);
                return true;
            });
        }

        // System Prompt
        EditTextPreference sysPromptPref = (EditTextPreference) findPreference(ConfigManager.KEY_SYS_PROMPT);
        if (sysPromptPref != null) {
            sysPromptPref.setText(ConfigManager.getSysPrompt());
            sysPromptPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setSysPrompt((String) newValue);
                sysPromptPref.setText((String) newValue);
                return true;
            });
        }

        // API URL
        EditTextPreference apiUrlPref = (EditTextPreference) findPreference(ConfigManager.KEY_API_URL);
        if (apiUrlPref != null) {
            apiUrlPref.setText(ConfigManager.getApiUrl());
            apiUrlPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setApiUrl((String) newValue);
                apiUrlPref.setText((String) newValue);
                return true;
            });
        }

        // API Key
        EditTextPreference apiKeyPref = (EditTextPreference) findPreference(ConfigManager.KEY_API_KEY);
        if (apiKeyPref != null) {
            apiKeyPref.setText(ConfigManager.getApiKey());
            apiKeyPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setApiKey((String) newValue);
                apiKeyPref.setText((String) newValue);
                return true;
            });
        }

        // Dictionary Path
        EditTextPreference dictPathPref = (EditTextPreference) findPreference(ConfigManager.KEY_DICT_PATH);
        if (dictPathPref != null) {
            dictPathPref.setText(ConfigManager.getDictPath());
            dictPathPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setDictPath((String) newValue);
                dictPathPref.setText((String) newValue);
                return true;
            });
        }
    }
}
