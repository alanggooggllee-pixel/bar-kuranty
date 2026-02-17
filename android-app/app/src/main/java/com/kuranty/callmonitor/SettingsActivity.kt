package com.kuranty.callmonitor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Настройки"

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<EditTextPreference>("recording_path")?.let { pref ->
                pref.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                if (pref.text.isNullOrBlank()) {
                    pref.text = RecordingWatcher.DEFAULT_RECORDING_PATH
                }
            }

            findPreference<EditTextPreference>("drive_folder_id")?.let { pref ->
                pref.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            }

            findPreference<EditTextPreference>("reminder_text")?.let { pref ->
                pref.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            }
        }
    }
}
