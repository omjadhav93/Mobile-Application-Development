package com.example.codeboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        Button enableKeyboardBtn = findViewById(R.id.enableKeyboardBtn);
        Button selectKeyboardBtn = findViewById(R.id.selectKeyboardBtn);

        enableKeyboardBtn.setOnClickListener(v -> openKeyboardSettings());
        selectKeyboardBtn.setOnClickListener(v -> showKeyboardPicker());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void openKeyboardSettings() {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        startActivity(intent);
    }

    private void showKeyboardPicker() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateStatus() {
        if (isKeyboardEnabled()) {
            statusText.setText("Keyboard enabled ✅");
        } else {
            statusText.setText("Keyboard not enabled ❌");
        }
    }

    private boolean isKeyboardEnabled() {
        String enabledKeyboards =
                Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure.ENABLED_INPUT_METHODS
                );

        return enabledKeyboards != null &&
                enabledKeyboards.contains(getPackageName());
    }
}