package com.acgc.port;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Manages only the user-owned NES ROMs and HD textures supported by the PC port. */
public final class ContentManagerActivity extends Activity {
    private static final int PICK_NES_ROMS = 2001;
    private static final int PICK_TEXTURE_ZIP = 2002;

    // The runtime's NES buffer is 0xC0010 bytes, including the 16-byte iNES header.
    private static final long MAX_NES_ROM_SIZE = 0xC0010L;
    private static final long MAX_TEXTURE_FILE_SIZE = 256L * 1024L * 1024L;
    private static final long MAX_TEXTURE_PACK_SIZE = 8L * 1024L * 1024L * 1024L;
    private static final int MAX_TEXTURE_FILES = 100000;

    private LinearLayout root;
    private LinearLayout nesList;
    private TextView nesStatus;
    private TextView textureStatus;
    private TextView operationStatus;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        refreshState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nesList != null) refreshState();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 23, 22));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("ROMs de NES y texturas", 27, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView intro = text(
                "Añade únicamente archivos de tus propias copias. El APK no incluye ROMs ni texturas.",
                14, Color.rgb(190, 207, 201));
        intro.setGravity(Gravity.CENTER);
        addWithMargins(intro, 0, 12, 0, 18);

        addSection("ROMs de NES");
        nesStatus = statusText();
        root.addView(nesStatus, matchWrap());

        Button addNes = primaryButton("Añadir ROMs .nes");
        addNes.setOnClickListener(view -> openNesPicker());
        addButton(addNes, 12);

        nesList = new LinearLayout(this);
        nesList.setOrientation(LinearLayout.VERTICAL);
        addWithMargins(nesList, 0, 8, 0, 4);

        addSection("Paquete de texturas HD");
        textureStatus = statusText();
        root.addView(textureStatus, matchWrap());

        TextView help = text(
                "Selecciona un ZIP con texturas Dolphin en formato .dds. El paquete nuevo reemplaza al anterior.",
                13, Color.rgb(143, 164, 157));
        addWithMargins(help, 0, 8, 0, 4);

        Button installTextures = secondaryButton("Instalar texturas (.zip)");
        installTextures.setOnClickListener(view -> openTexturePicker());
        addButton(installTextures, 8);

        Button deleteTextures = dangerButton("Eliminar texturas");
        deleteTextures.setOnClickListener(view -> confirmDeleteTextures());
        addButton(deleteTextures, 8);

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(18), 0, dp(6));
        root.addView(progress, progressParams);

        operationStatus = text("", 14, Color.WHITE);
        operationStatus.setGravity(Gravity.CENTER);
        root.addView(operationStatus, matchWrap());

        Button back = secondaryButton("Volver");
        back.setOnClickListener(view -> finish());
        addButton(back, 18);

        setContentView(scroll);
    }

    private void openNesPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/octet-stream", "application/x-nes-rom", "*/*"});
        startActivityForResult(intent, PICK_NES_ROMS);
    }

    private void openTexturePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/x-zip-compressed", "*/*"});
        startActivityForResult(intent, PICK_TEXTURE_ZIP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_NES_ROMS) {
            List<Uri> uris = selectedUris(data);
            if (!uris.isEmpty()) importNesRoms(uris);
            return;
        }

        if (requestCode == PICK_TEXTURE_ZIP && data.getData() != null) {
            Uri uri = data.getData();
            if (!queryName(uri).toLowerCase(Locale.ROOT).endsWith(".zip")) {
                Toast.makeText(this, "Selecciona un archivo .zip", Toast.LENGTH_LONG).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Instalar texturas")
                    .setMessage("El paquete de texturas anterior será reemplazado.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Instalar", (dialog, which) ->
                            runOperation("Instalando texturas…", () -> installTexturePack(uri),
                                    "Texturas instaladas. Se cargarán al reiniciar el juego."))
                    .show();
        }
    }

    private List<Uri> selectedUris(Intent data) {
        List<Uri> uris = new ArrayList<Uri>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) uris.add(uri);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        return uris;
    }

    private void importNesRoms(List<Uri> uris) {
        setBusy(true, "Añadiendo ROMs de NES…");
        new Thread(() -> {
            int imported = 0;
            String firstError = null;
            for (Uri uri : uris) {
                try {
                    importNesRom(uri, queryName(uri));
                    imported++;
                } catch (Exception error) {
                    if (firstError == null) firstError = errorMessage(error);
                }
            }

            final int importedCount = imported;
            final String failure = firstError;
            runOnUiThread(() -> {
                setBusy(false, failure == null ? "Operación completada."
                        : "Algunos archivos no se añadieron.");
                refreshState();
                if (importedCount > 0) {
                    Toast.makeText(this,
                            importedCount == 1 ? "ROM de NES añadida"
                                    : importedCount + " ROMs de NES añadidas",
                            Toast.LENGTH_LONG).show();
                }
                if (failure != null) Toast.makeText(this, failure, Toast.LENGTH_LONG).show();
            });
        }, "nes-rom-import").start();
    }

    private void importNesRom(Uri uri, String displayName) throws IOException {
        if (displayName == null || !displayName.toLowerCase(Locale.ROOT).endsWith(".nes")) {
            throw new IOException("Selecciona archivos con extensión .nes.");
        }

        File directory = nesDirectory();
        ensureDirectory(directory, "No se pudo crear la carpeta de ROMs de NES.");
        File partial = new File(directory, "incoming.part");
        try {
            if (partial.exists() && !partial.delete()) {
                throw new IOException("No se pudo limpiar una importación anterior.");
            }
            copyUri(uri, partial, MAX_NES_ROM_SIZE);
            validateNesRom(partial);
            moveFile(partial, uniqueNesTarget(safeNesName(displayName)));
        } catch (IOException error) {
            partial.delete();
            throw error;
        }
    }

    private void validateNesRom(File file) throws IOException {
        if (file.length() < 16) throw new IOException("La ROM de NES es demasiado pequeña.");
        if (file.length() > MAX_NES_ROM_SIZE) {
            throw new IOException("La ROM supera el máximo compatible de 768 KB.");
        }
        try (RandomAccessFile random = new RandomAccessFile(file, "r")) {
            byte[] magic = new byte[4];
            random.readFully(magic);
            if (magic[0] != 'N' || magic[1] != 'E' || magic[2] != 'S'
                    || (magic[3] & 0xff) != 0x1a) {
                throw new IOException("El archivo no tiene una cabecera iNES válida.");
            }
        }
    }

    private File uniqueNesTarget(String requestedName) {
        File requested = new File(nesDirectory(), requestedName);
        if (!requested.exists()) return requested;

        String base = requestedName.substring(0, requestedName.length() - 4);
        for (int i = 2; i < 10000; i++) {
            File candidate = new File(nesDirectory(), base + "_" + i + ".nes");
            if (!candidate.exists()) return candidate;
        }
        return new File(nesDirectory(), System.currentTimeMillis() + ".nes");
    }

    private void confirmDeleteNes(File rom) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar ROM de NES")
                .setMessage("¿Eliminar " + rom.getName() + "?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) ->
                        runOperation("Eliminando ROM…", () -> {
                            if (rom.exists() && !rom.delete()) {
                                throw new IOException("No se pudo eliminar " + rom.getName() + ".");
                            }
                        }, "ROM de NES eliminada."))
                .show();
    }

    private void installTexturePack(Uri uri) throws IOException {
        File staging = new File(getFilesDir(), "texture_pack.incoming");
        File target = textureDirectory();
        deleteRecursive(staging);
        if (!staging.mkdirs()) throw new IOException("No se pudo preparar la carpeta de texturas.");

        int count = 0;
        long total = 0L;
        InputStream raw = getContentResolver().openInputStream(uri);
        if (raw == null) throw new IOException("Android no pudo abrir el ZIP.");
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }

                String entryName = entry.getName().replace('\\', '/');
                if (!entryName.toLowerCase(Locale.ROOT).endsWith(".dds")) {
                    zip.closeEntry();
                    continue;
                }
                entryName = entryName.substring(0, entryName.length() - 4) + ".dds";
                if (entryName.length() > 240) {
                    throw new IOException("El ZIP contiene una ruta demasiado larga.");
                }

                File output = new File(staging, entryName);
                String rootPath = staging.getCanonicalPath() + File.separator;
                if (!output.getCanonicalPath().startsWith(rootPath)) {
                    throw new IOException("El ZIP contiene una ruta no permitida.");
                }
                File parent = output.getParentFile();
                if (parent == null || (!parent.exists() && !parent.mkdirs())) {
                    throw new IOException("No se pudo crear una carpeta del paquete.");
                }

                long written;
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                    written = copy(zip, out, MAX_TEXTURE_FILE_SIZE);
                }
                total += written;
                if (total > MAX_TEXTURE_PACK_SIZE) {
                    throw new IOException("El paquete supera el límite de seguridad de 8 GB.");
                }
                count++;
                if (count > MAX_TEXTURE_FILES) {
                    throw new IOException("El paquete contiene demasiados archivos.");
                }
                zip.closeEntry();
            }
        } catch (IOException error) {
            deleteRecursiveQuietly(staging);
            throw error;
        }

        if (count == 0) {
            deleteRecursive(staging);
            throw new IOException("El ZIP no contiene texturas .dds compatibles.");
        }

        File backup = new File(getFilesDir(), "texture_pack.previous");
        deleteRecursive(backup);
        if (target.exists() && !target.renameTo(backup)) {
            deleteRecursive(staging);
            throw new IOException("No se pudo respaldar el paquete anterior.");
        }
        if (!staging.renameTo(target)) {
            if (backup.exists() && !backup.renameTo(target)) {
                throw new IOException("No se pudo instalar el paquete ni restaurar el anterior.");
            }
            throw new IOException("No se pudo activar el nuevo paquete de texturas.");
        }
        deleteRecursiveQuietly(backup);
        File cache = new File(target, "texture_cache.bin");
        if (cache.exists()) cache.delete();
    }

    private void confirmDeleteTextures() {
        if (countFiles(textureDirectory(), ".dds") == 0) {
            Toast.makeText(this, "No hay texturas instaladas.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Eliminar texturas")
                .setMessage("Se eliminarán todas las texturas importadas y su caché.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) ->
                        runOperation("Eliminando texturas…",
                                () -> deleteRecursive(textureDirectory()),
                                "Texturas eliminadas."))
                .show();
    }

    private interface FileOperation {
        void run() throws Exception;
    }

    private void runOperation(String message, FileOperation operation, String successMessage) {
        setBusy(true, message);
        new Thread(() -> {
            Exception failure = null;
            try {
                operation.run();
            } catch (Exception error) {
                failure = error;
            }
            final Exception result = failure;
            runOnUiThread(() -> {
                setBusy(false, result == null ? "Operación completada." : "La operación falló.");
                refreshState();
                if (result == null) {
                    Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, errorMessage(result), Toast.LENGTH_LONG).show();
                }
            });
        }, "content-file-operation").start();
    }

    private void refreshState() {
        File[] roms = listNesRoms();
        nesStatus.setText(roms.length == 0
                ? "No hay ROMs de NES añadidas."
                : roms.length + (roms.length == 1
                        ? " ROM de NES añadida." : " ROMs de NES añadidas."));

        nesList.removeAllViews();
        for (File rom : roms) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(8), dp(8));
            row.setBackground(rounded(Color.rgb(31, 45, 42), 12));

            TextView name = text(rom.getName(), 14, Color.WHITE);
            row.addView(name, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button delete = dangerButton("Eliminar");
            delete.setOnClickListener(view -> confirmDeleteNes(rom));
            row.addView(delete, new LinearLayout.LayoutParams(dp(104), dp(46)));

            LinearLayout.LayoutParams params = matchWrap();
            params.setMargins(0, 0, 0, dp(8));
            nesList.addView(row, params);
        }

        int textureCount = countFiles(textureDirectory(), ".dds");
        long textureBytes = directorySize(textureDirectory());
        textureStatus.setText(textureCount == 0
                ? "No hay texturas HD instaladas."
                : textureCount + " texturas .dds instaladas (" + formatBytes(textureBytes) + ").");
    }

    private File[] listNesRoms() {
        File[] files = nesDirectory().listFiles(file ->
                file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".nes"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    private void setBusy(boolean busy, String message) {
        setEnabledRecursive(root, !busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        operationStatus.setText(message);
    }

    private void setEnabledRecursive(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setEnabledRecursive(group.getChildAt(i), enabled);
            }
        }
    }

    private File nesDirectory() {
        return new File(getFilesDir(), "nes_roms");
    }

    private File textureDirectory() {
        return new File(getFilesDir(), "texture_pack");
    }

    private void ensureDirectory(File directory, String message) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) throw new IOException(message);
    }

    private void copyUri(Uri uri, File target, long limit) throws IOException {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IOException("Android no pudo abrir el archivo seleccionado.");
        try (InputStream in = input; OutputStream out = new FileOutputStream(target)) {
            copy(in, out, limit);
        }
    }

    private long copy(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[1024 * 1024];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            total += read;
            if (limit > 0L && total > limit) {
                throw new IOException(limit == MAX_NES_ROM_SIZE
                        ? "La ROM supera el máximo compatible de 768 KB."
                        : "Un archivo del paquete supera 256 MB.");
            }
            output.write(buffer, 0, read);
        }
        output.flush();
        return total;
    }

    private void moveFile(File source, File target) throws IOException {
        if (source.renameTo(target)) return;
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(target)) {
            copy(input, output, -1L);
        }
        if (!source.delete()) throw new IOException("No se pudo limpiar el archivo temporal.");
    }

    private void deleteRecursive(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        if (!file.delete()) throw new IOException("No se pudo eliminar " + file.getName() + ".");
    }

    private void deleteRecursiveQuietly(File file) {
        try {
            deleteRecursive(file);
        } catch (IOException ignored) {
        }
    }

    private int countFiles(File directory, String extension) {
        if (!directory.isDirectory()) return 0;
        int count = 0;
        File[] files = directory.listFiles();
        if (files == null) return 0;
        for (File file : files) {
            if (file.isDirectory()) count += countFiles(file, extension);
            else if (file.getName().toLowerCase(Locale.ROOT).endsWith(extension)) count++;
        }
        return count;
    }

    private long directorySize(File file) {
        if (!file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += directorySize(child);
        }
        return total;
    }

    private String safeNesName(String name) {
        String stem = name.substring(0, name.length() - 4)
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (stem.isEmpty()) stem = "juego";
        if (stem.length() > 80) stem = stem.substring(0, 80);
        return stem + ".nes";
    }

    private String queryName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        String tail = uri.getLastPathSegment();
        return tail == null ? "archivo" : tail;
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String errorMessage(Exception error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    private TextView statusText() {
        TextView view = text("", 14, Color.rgb(195, 211, 205));
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        view.setBackground(rounded(Color.rgb(31, 45, 42), 12));
        return view;
    }

    private void addSection(String title) {
        TextView heading = text(title, 20, Color.WHITE);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        addWithMargins(heading, 0, 18, 0, 10);
    }

    private Button primaryButton(String label) {
        return button(label, Color.rgb(114, 214, 161), Color.rgb(13, 28, 22));
    }

    private Button secondaryButton(String label) {
        return button(label, Color.rgb(42, 67, 60), Color.WHITE);
    }

    private Button dangerButton(String label) {
        return button(label, Color.rgb(112, 53, 53), Color.WHITE);
    }

    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setBackground(rounded(background, 14));
        return button;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void addButton(Button button, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        params.setMargins(0, dp(topMarginDp), 0, 0);
        root.addView(button, params);
    }

    private void addWithMargins(View view, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        root.addView(view, params);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
