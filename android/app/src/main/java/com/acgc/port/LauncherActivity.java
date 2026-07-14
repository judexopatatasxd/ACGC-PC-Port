package com.acgc.port;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Locale;

public final class LauncherActivity extends Activity {
    private static final int PICK_ROM = 1001;
    private static final String[] ROM_EXTENSIONS = {".iso", ".gcm", ".ciso"};

    private TextView status;
    private Button playButton;
    private Button selectButton;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        refreshState();
        if (state == null && findRom() != null) {
            playButton.postDelayed(this::startGame, 180);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) refreshState();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(38), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(16, 23, 22));

        TextView title = new TextView(this);
        title.setText("Animal Crossing\nPort Android");
        title.setTextColor(Color.WHITE);
        title.setTextSize(31);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView explanation = new TextView(this);
        explanation.setText("Este APK no incluye el juego. Selecciona tu propia imagen original de Animal Crossing (USA Rev 0) en formato ISO, GCM o CISO.");
        explanation.setTextColor(Color.rgb(195, 211, 205));
        explanation.setTextSize(16);
        explanation.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        explanationParams.setMargins(0, dp(22), 0, dp(22));
        root.addView(explanation, explanationParams);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(16), dp(15), dp(16), dp(15));
        status.setBackground(rounded(Color.rgb(31, 45, 42), 14));
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        progressParams.setMargins(0, dp(18), 0, dp(10));
        root.addView(progress, progressParams);

        selectButton = new Button(this);
        selectButton.setText("Seleccionar ROM original");
        selectButton.setTextSize(16);
        selectButton.setTextColor(Color.rgb(13, 28, 22));
        selectButton.setAllCaps(false);
        selectButton.setBackground(rounded(Color.rgb(114, 214, 161), 16));
        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openPicker(); }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        buttonParams.setMargins(0, dp(18), 0, dp(10));
        root.addView(selectButton, buttonParams);

        playButton = new Button(this);
        playButton.setText("Iniciar juego");
        playButton.setTextSize(16);
        playButton.setTextColor(Color.WHITE);
        playButton.setAllCaps(false);
        playButton.setBackground(rounded(Color.rgb(52, 94, 78), 16));
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startGame(); }
        });
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        root.addView(playButton, playParams);

        TextView note = new TextView(this);
        note.setText("La ROM queda recordada y el juego iniciará directamente la próxima vez. Usa el botón ⚙ dentro del juego para ocultar, mover o restaurar los controles.");
        note.setTextColor(Color.rgb(143, 164, 157));
        note.setTextSize(13);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.setMargins(0, dp(18), 0, 0);
        root.addView(note, noteParams);

        setContentView(root);
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/octet-stream", "application/x-iso9660-image", "*/*"
        });
        startActivityForResult(intent, PICK_ROM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_ROM || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        final Uri uri = data.getData();
        final String displayName = queryName(uri);
        if (!isSupportedName(displayName)) {
            Toast.makeText(this, "Selecciona un archivo .iso, .gcm o .ciso", Toast.LENGTH_LONG).show();
            return;
        }

        setBusy(true, "Copiando la ROM al almacenamiento privado…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    installRom(uri, displayName);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            setBusy(false, "ROM lista: " + displayName);
                            refreshState();
                        }
                    });
                } catch (final Exception error) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            setBusy(false, "No se pudo copiar la ROM.");
                            Toast.makeText(LauncherActivity.this,
                                    error.getMessage() == null ? error.toString() : error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                            refreshState();
                        }
                    });
                }
            }
        }, "rom-copy").start();
    }

    private String queryName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        String tail = uri.getLastPathSegment();
        return tail == null ? "game.iso" : tail;
    }

    private boolean isSupportedName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : ROM_EXTENSIONS) {
            if (lower.endsWith(extension)) return true;
        }
        return false;
    }

    private String safeName(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() > 100 ? cleaned.substring(cleaned.length() - 100) : cleaned;
    }

    private File romDirectory() {
        return new File(getFilesDir(), "rom");
    }

    private File findRom() {
        File[] files = romDirectory().listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && isSupportedName(file.getName())) return file;
        }
        return null;
    }

    private void installRom(Uri uri, String displayName) throws IOException {
        File romDir = romDirectory();
        if (!romDir.exists() && !romDir.mkdirs()) throw new IOException("No se pudo crear la carpeta de ROM.");

        File partial = new File(romDir, "incoming.part");
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IOException("Android no pudo abrir el archivo seleccionado.");
        try {
            copy(input, new FileOutputStream(partial));
        } finally {
            input.close();
        }

        if (partial.length() < 0x440) {
            partial.delete();
            throw new IOException("El archivo es demasiado pequeño para ser una imagen de GameCube.");
        }
        validateDisc(partial, displayName);

        File[] oldFiles = romDir.listFiles();
        if (oldFiles != null) {
            for (File old : oldFiles) {
                if (!old.equals(partial) && old.isFile() && isSupportedName(old.getName())) old.delete();
            }
        }

        File target = new File(romDir, safeName(displayName));
        if (target.exists() && !target.delete()) throw new IOException("No se pudo reemplazar la ROM anterior.");
        if (!partial.renameTo(target)) {
            copy(new FileInputStream(partial), new FileOutputStream(target));
            partial.delete();
        }

        installShader("default.vert");
        installShader("default.frag");
    }

    private void validateDisc(File file, String displayName) throws IOException {
        boolean ciso = displayName.toLowerCase(Locale.ROOT).endsWith(".ciso");
        long base = ciso ? 0x8000L : 0L;
        RandomAccessFile random = new RandomAccessFile(file, "r");
        try {
            if (ciso) {
                byte[] cisoMagic = new byte[4];
                random.readFully(cisoMagic);
                if (cisoMagic[0] != 'C' || cisoMagic[1] != 'I' ||
                        cisoMagic[2] != 'S' || cisoMagic[3] != 'O') {
                    throw new IOException("El archivo .ciso no tiene una cabecera CISO válida.");
                }
            }

            byte[] id = new byte[8];
            random.seek(base);
            random.readFully(id);
            String gameId = new String(id, 0, 6, "US-ASCII");
            if (!"GAFE01".equals(gameId) || id[6] != 0 || id[7] != 0) {
                throw new IOException("ROM incompatible: se necesita Animal Crossing USA Rev 0 (GAFE01_00).");
            }

            byte[] gcMagic = new byte[4];
            random.seek(base + 0x1cL);
            random.readFully(gcMagic);
            if ((gcMagic[0] & 0xff) != 0xc2 || (gcMagic[1] & 0xff) != 0x33 ||
                    (gcMagic[2] & 0xff) != 0x9f || (gcMagic[3] & 0xff) != 0x3d) {
                throw new IOException("La imagen seleccionada no parece ser un disco válido de GameCube.");
            }
        } finally {
            random.close();
        }
    }

    private void installShader(String name) throws IOException {
        File dir = new File(getFilesDir(), "shaders");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("No se pudo crear la carpeta de shaders.");
        InputStream input = getAssets().open(name);
        try {
            copy(input, new FileOutputStream(new File(dir, name)));
        } finally {
            input.close();
        }
    }

    private void copy(InputStream input, OutputStream output) throws IOException {
        try {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
            output.flush();
        } finally {
            output.close();
        }
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        selectButton.setEnabled(!busy);
        playButton.setEnabled(!busy && findRom() != null);
        status.setText(message);
    }

    private void refreshState() {
        File rom = findRom();
        if (rom == null) {
            status.setText("No hay una ROM seleccionada.");
            playButton.setEnabled(false);
            selectButton.setText("Seleccionar ROM original");
        } else {
            status.setText("ROM lista: " + rom.getName() + "\n" + (rom.length() / (1024 * 1024)) + " MB");
            playButton.setEnabled(true);
            selectButton.setText("Cambiar ROM");
        }
    }

    private void startGame() {
        if (findRom() == null) {
            openPicker();
            return;
        }
        try {
            installShader("default.vert");
            installShader("default.frag");
        } catch (IOException error) {
            Toast.makeText(this, "No se pudieron preparar los shaders: " + error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, GameActivity.class));
    }
}
