package com.acgc.port;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.libsdl.app.SDLActivity;

public final class GameActivity extends SDLActivity {
    private VirtualControllerView controller;
    private Button settingsButton;

    @Override
    protected void onCreate(Bundle state) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureWindow();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(state);
        configureWindow();

        controller = new VirtualControllerView(this);
        addContentView(controller, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        controller.setControlsVisible(controller.areControlsVisible());

        addSettingsButton();
        immersive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        immersive();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override
    public void onBackPressed() {
        if (controller != null && controller.isEditMode()) {
            finishEditingControls();
            return;
        }
        super.onBackPressed();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
    }

    private void addSettingsButton() {
        settingsButton = new Button(this);
        settingsButton.setText("⚙");
        settingsButton.setTextSize(18);
        settingsButton.setTextColor(Color.WHITE);
        settingsButton.setAllCaps(false);
        settingsButton.setMinWidth(0);
        settingsButton.setMinHeight(0);
        settingsButton.setPadding(0, 0, 0, 0);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(145, 17, 27, 25));
        background.setStroke(dp(1), Color.argb(180, 255, 255, 255));
        background.setShape(GradientDrawable.OVAL);
        settingsButton.setBackground(background);
        settingsButton.setOnClickListener(view -> {
            if (controller.isEditMode()) finishEditingControls();
            else showControllerMenu();
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(44), dp(44), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = dp(6);
        addContentView(settingsButton, params);
    }

    private void showControllerMenu() {
        String visibilityAction = controller.areControlsVisible()
                ? "Ocultar controles táctiles"
                : "Mostrar controles táctiles";
        CharSequence[] actions = {
                visibilityAction,
                "Mover botones",
                "Restablecer posiciones",
                "Volver al selector de ROM"
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Controles táctiles")
                .setItems(actions, (ignored, which) -> {
                    if (which == 0) {
                        boolean visible = !controller.areControlsVisible();
                        controller.setControlsVisible(visible);
                        Toast.makeText(this,
                                visible ? "Controles visibles" : "Controles ocultos",
                                Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        controller.setControlsVisible(true);
                        controller.setEditMode(true);
                        settingsButton.setText("✓");
                        Toast.makeText(this,
                                "Arrastra cada botón y pulsa ✓ para terminar.",
                                Toast.LENGTH_LONG).show();
                    } else if (which == 2) {
                        controller.resetLayout();
                        Toast.makeText(this, "Posiciones restauradas", Toast.LENGTH_SHORT).show();
                    } else if (which == 3) {
                        finish();
                    }
                })
                .create();
        dialog.setOnDismissListener(ignored -> immersiveSoon());
        dialog.show();
    }

    private void finishEditingControls() {
        controller.setEditMode(false);
        settingsButton.setText("⚙");
        Toast.makeText(this, "Posiciones guardadas", Toast.LENGTH_SHORT).show();
        immersiveSoon();
    }

    private void immersiveSoon() {
        getWindow().getDecorView().postDelayed(this::immersive, 120);
    }

    private void immersive() {
        configureWindow();
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
            getWindow().getInsetsController().hide(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            getWindow().getInsetsController().setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
