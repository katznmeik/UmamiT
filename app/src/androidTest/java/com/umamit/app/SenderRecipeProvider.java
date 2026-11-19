package com.umamit.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Another app's provider for ReceiveRecipeDeviceTest (PRF-08, D-133 Q4): it lives in the test APK and runs under the
 * test package's uid, so the app reads its files as it reads a real sender's. Java, not Kotlin: the test APK leaves
 * out what the app already carries (the Kotlin runtime among it), and its processes load the test APK alone.
 *
 * <p>It runs in a process of its own ({@code :sender}), not in the test package's main process: that one hosts
 * androidx.test's {@code EmptyActivity} and {@code BootstrapActivity} (the app's manifest has neither), which
 * ActivityScenario starts on every stop and close and finishes by broadcast on the next launch. The app's provider
 * calls and copies started, bound and kept that process for the sender, and with it the API 36 emulator showed none
 * of TransitionJankTest's ActivityScenario launches on screen (CI run 44610036789).
 *
 * <p>{@link #call} with {@link #METHOD_SEND} stores {@link #EXTRA_BYTES} under the file name {@code arg} and grants
 * the caller read access to that one URI ({@link #EXTRA_URI}), as a sender grants the app it shares with; the
 * test's intents carry {@code FLAG_GRANT_READ_URI_PERMISSION} as a real sender's do. {@link #call} with
 * {@link #METHOD_FORGET} revokes every grant of its files and deletes them, so no test leaves a grant or a file behind.
 * Synthetic recipes only (D-016). None of its methods blocks: each one is a small file operation or a constant.
 *
 * <p>The provider is exported with no read or write permission. The instrumentation code runs in the app's process
 * and uid, and a provider-level permission is checked when the provider is acquired ({@code getContentProvider}),
 * before {@code call()} runs and so before any grant exists: with a permission nobody holds, the test's own
 * {@code call("send")} was refused. Without one, acquiring the provider and reading its URIs needs nothing on any API
 * level (35 and 36 alike), so the test is certain to run. It therefore no longer proves that the app reads a
 * sender's file only through the grant; that is checked on the phone (09 §4).
 */
public final class SenderRecipeProvider extends ContentProvider {
    static final String METHOD_SEND = "send";
    static final String METHOD_FORGET = "forget";
    static final String EXTRA_BYTES = "bytes";
    static final String EXTRA_URI = "uri";
    private static final String TYPE = "application/vnd.umamit.recipe+json";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_FORGET.equals(method)) {
            forget();
            return Bundle.EMPTY;
        }
        if (!METHOD_SEND.equals(method) || arg == null || extras == null) {
            throw new IllegalArgumentException("unknown call: " + method);
        }
        File file = fileOf(arg);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(extras.getByteArray(EXTRA_BYTES));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Uri uri = uriOf(file.getName());
        getContext().grantUriPermission(getCallingPackage(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Bundle result = new Bundle();
        result.putParcelable(EXTRA_URI, uri);
        return result;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("read only");
        }
        return ParcelFileDescriptor.open(fileOf(uri.getLastPathSegment()), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return TYPE;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    /** Revokes the grants of every sent file and deletes it. */
    private void forget() {
        File[] files = folder().listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            getContext().revokeUriPermission(uriOf(file.getName()), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (!file.delete() && file.exists()) {
                throw new IllegalStateException("not deleted: " + file.getName());
            }
        }
    }

    private Uri uriOf(String name) {
        return new Uri.Builder()
                .scheme("content")
                .authority(getContext().getPackageName() + ".sent-recipes")
                .appendPath(name)
                .build();
    }

    private File folder() {
        File dir = new File(getContext().getCacheDir(), "sent");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("no folder");
        }
        return dir;
    }

    /** A file of the provider's own folder; any other name is refused. */
    private File fileOf(String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.startsWith(".")) {
            throw new IllegalArgumentException("bad name: " + name);
        }
        return new File(folder(), name);
    }
}
