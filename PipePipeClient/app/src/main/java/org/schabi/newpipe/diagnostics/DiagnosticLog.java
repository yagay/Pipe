package org.schabi.newpipe.diagnostics;

import android.content.Context;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.schabi.newpipe.BuildConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Local bounded diagnostics: no logcat permission and no external telemetry.
 * Shared across UI, extractors, playback, networking and uncaught exceptions.
 */
public final class DiagnosticLog {
    private static final Object LOCK = new Object();
    private static final int MAX_BYTES = 768 * 1024;
    private static final int MAX_STACK = 7000;
    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s\\\"'<>]+");
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(authorization|cookie|token|secret|password|apikey|api_key|session|signature)"
                    + "\\s*[:=]\\s*[^\\s,;}]+");
    private static volatile Context application;
    private static volatile Thread.UncaughtExceptionHandler originalHandler;
    private static final ThreadLocal<Boolean> HANDLING_CRASH =
            ThreadLocal.withInitial(() -> false);

    private DiagnosticLog() { }

    public static void install(final Context context) {
        application = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler current =
                Thread.getDefaultUncaughtExceptionHandler();
        if (current != null && current != originalHandler) {
            originalHandler = current;
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                if (!HANDLING_CRASH.get()) {
                    HANDLING_CRASH.set(true);
                    try {
                        error("CRASH", "UNCAUGHT", throwable,
                                "thread=" + thread.getName());
                    } catch (final Throwable ignored) { }
                }
                if (originalHandler != null) {
                    originalHandler.uncaughtException(thread, throwable);
                }
            });
        }
        event("APP", "START", "process initialized");
    }

    public static void event(final String category, final String code,
                             final String details) {
        write("INFO", category, code, details, null);
    }

    public static void error(final String category, final String code,
                             final Throwable throwable, final String details) {
        write("ERROR", category, code, details, throwable);
    }

    private static void write(final String severity, final String category,
                              final String code, final String details,
                              final Throwable throwable) {
        final Context ctx = application;
        if (ctx == null) return;
        try {
            final StringBuilder entry = new StringBuilder(256);
            entry.append(timestamp()).append(' ').append(severity).append(' ')
                    .append(clean(category)).append('/').append(clean(code))
                    .append(' ').append(clean(details));
            if (throwable != null) {
                entry.append(" exception=").append(throwable.getClass().getName());
                final StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                entry.append(" stack=").append(clean(
                        sw.toString().substring(0, Math.min(MAX_STACK, sw.toString().length()))));
            }
            entry.append('\n');
            synchronized (LOCK) {
                final File dir = dir(ctx);
                final File active = new File(dir, "events.log");
                if (active.exists() && active.length() + entry.length() > MAX_BYTES) {
                    final File previous = new File(dir, "previous.log");
                    if (previous.exists()) previous.delete();
                    active.renameTo(previous);
                }
                try (FileOutputStream stream = new FileOutputStream(active, true)) {
                    stream.write(entry.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (final Throwable failed) {
            Log.w("PipeDiagnostics", "Unable to write diagnostic record",
                    failed);
        }
    }

    private static File dir(final Context ctx) {
        final File directory = new File(ctx.getFilesDir(), "diagnostics");
        if (!directory.exists()) directory.mkdirs();
        return directory;
    }

    private static String timestamp() {
        final SimpleDateFormat format =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static String clean(final String input) {
        if (input == null) return "";
        String result = input.replace('\r', ' ').replace('\n', ' ');
        result = URL.matcher(result).replaceAll("[URL_REDACTED]");
        result = SENSITIVE.matcher(result).replaceAll("$1=[REDACTED]");
        return result.substring(0, Math.min(result.length(), MAX_STACK));
    }

    public static File exportZip(final Context context) throws Exception {
        final Context ctx = context.getApplicationContext();
        final File exportDir = new File(ctx.getCacheDir(), "diagnostic_exports");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw new java.io.IOException("Unable to create export directory");
        }
        final File zip = new File(exportDir, "Pipe-diagnostic-" +
                System.currentTimeMillis() + ".zip");
        synchronized (LOCK) {
            try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
                final String meta = "package=" + BuildConfig.APPLICATION_ID
                        + "\nversion=" + BuildConfig.VERSION_NAME
                        + "\nversionCode=" + BuildConfig.VERSION_CODE
                        + "\nandroid=" + Build.VERSION.RELEASE
                        + "\nsdk=" + Build.VERSION.SDK_INT
                        + "\ndevice=" + clean(Build.MANUFACTURER + " " + Build.MODEL)
                        + "\ngenerated=" + timestamp()
                        + "\nprivacy=URLs and common credentials redacted; review before sharing\n";
                output.putNextEntry(new ZipEntry("device.txt"));
                output.write(meta.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
                for (final String name : new String[]{"events.log", "previous.log"}) {
                    final File source = new File(dir(ctx), name);
                    if (!source.isFile()) continue;
                    output.putNextEntry(new ZipEntry(name));
                    try (FileInputStream input = new FileInputStream(source)) {
                        final byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                        }
                    }
                    output.closeEntry();
                }
            }
        }
        return zip;
    }

    /**
     * Write straight into the public Downloads collection. Android 10+ uses
     * MediaStore (no storage permission or document/share chooser required).
     * Android 8-9 uses the legacy public directory with WRITE_EXTERNAL_STORAGE.
     *
     * @return the saved file name shown to the user after completion
     */
    public static String saveToDownloads(final Context context) throws Exception {
        final Context ctx = context.getApplicationContext();
        event("DIAGNOSTICS", "EXPORT", "saving diagnostic ZIP to Downloads");
        final File zip = exportZip(ctx);
        final String name = zip.getName();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(ctx, zip, name);
            } else {
                if (ContextCompat.checkSelfPermission(ctx,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException(
                            "Storage permission is required on Android 8-9");
                }
                final File downloads = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (!downloads.exists() && !downloads.mkdirs()) {
                    throw new IOException("Cannot create public Downloads directory");
                }
                final File target = new File(downloads, name);
                try (FileInputStream input = new FileInputStream(zip);
                     FileOutputStream output = new FileOutputStream(target)) {
                    copy(input, output);
                }
            }
            event("DIAGNOSTICS", "SAVED_TO_DOWNLOADS", "file=" + name);
            return name;
        } finally {
            if (!zip.delete()) {
                zip.deleteOnExit();
            }
        }
    }

    private static void saveWithMediaStore(final Context context, final File zip,
                                            final String fileName) throws IOException {
        final ContentResolver resolver = context.getContentResolver();
        final ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        final Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Cannot create Downloads entry");
        boolean complete = false;
        try {
            try (FileInputStream input = new FileInputStream(zip);
                 OutputStream output = resolver.openOutputStream(uri)) {
                if (output == null) throw new IOException("Cannot open Downloads output stream");
                copy(input, output);
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) < 1) {
                throw new IOException("Could not finalize Downloads entry");
            }
            complete = true;
        } finally {
            if (!complete) resolver.delete(uri, null, null);
        }
    }

    private static void copy(final FileInputStream input, final OutputStream output)
            throws IOException {
        final byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
    }

    public static void clear(final Context context) {
        synchronized (LOCK) {
            final File directory = dir(context.getApplicationContext());
            new File(directory, "events.log").delete();
            new File(directory, "previous.log").delete();
        }
        event("DIAGNOSTICS", "CLEARED", "user cleared local diagnostic logs");
    }
}
