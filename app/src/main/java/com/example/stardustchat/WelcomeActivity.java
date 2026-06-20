package com.example.stardustchat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;

public class WelcomeActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private EditText welcomeMessageInput;
    private Button welcomeSendButton;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        initViews();
        setupToolbar();
        setupNavigationDrawer();
        setupInputComponents();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);
        welcomeMessageInput = findViewById(R.id.welcomeMessageInput);
        welcomeSendButton = findViewById(R.id.welcomeSendButton);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle(R.string.app_name);
        }
    }

    private void setupNavigationDrawer() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_current_chat);
    }

    private void setupInputComponents() {
        welcomeSendButton.setOnClickListener(v -> {
            String message = welcomeMessageInput.getText().toString().trim();
            if (TextUtils.isEmpty(message)) {
                Toast.makeText(this, R.string.toast_enter_message, Toast.LENGTH_SHORT).show();
                return;
            }
            startChatWithMessage(message);
        });

        welcomeMessageInput.setOnEditorActionListener((v, actionId, event) -> {
            String message = welcomeMessageInput.getText().toString().trim();
            if (TextUtils.isEmpty(message)) {
                return false;
            }
            startChatWithMessage(message);
            return true;
        });
    }

    private void startChatWithMessage(String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("first_message", message);
        startActivity(intent);
        welcomeMessageInput.setText("");
    }

    private void openMainActivity(String period) {
        Intent intent = new Intent(this, MainActivity.class);
        if (!TextUtils.isEmpty(period)) {
            intent.putExtra("load_history", period);
        }
        startActivity(intent);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_current_chat) {
            openMainActivity(null);
        } else if (id == R.id.nav_today) {
            openMainActivity("today");
        } else if (id == R.id.nav_yesterday) {
            openMainActivity("yesterday");
        } else if (id == R.id.nav_this_week) {
            openMainActivity("week");
        } else if (id == R.id.nav_older) {
            openMainActivity("older");
        } else if (id == R.id.nav_settings) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("open_settings", true);
            startActivity(intent);
        } else if (id == R.id.nav_theme) {
            Toast.makeText(this, R.string.toast_theme_not_ready, Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_about) {
            showAbout();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.welcome_about_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
