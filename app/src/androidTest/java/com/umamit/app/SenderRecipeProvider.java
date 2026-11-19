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
 * Another app's provider for ReceiveRecipeDeviceTest (PRF-08, D-133 Q4): it lives in the test APK and runs in the
 * test package's own process, under its own uid, so the app reads its files as it reads a real sender's. Java, not
 * Kotlin: the test APK leaves out what the app already carries (the Kotlin runtime among it), and that process loads
 * the test APK alone.
 *
 * <p>{@link #call} with {@link #METHOD_SEND} stores {@link #EXTRA_BYTES} under the file name {@code arg} and grants
 * the caller read access to that one URI ({@link #EXTRA_URI}), as a sender grants the app it shares with. The
 * provider's read permission is held by no app, so the grant is the only way in. Synthetic recipes only (D-016).
 */
public final class SenderRecipeProvider extends ContentProvider {
    static final String METHOD_SEND = "send";
    static final String EXTRA_BYTES = "bytes";
    static final String EXTRA_URI = "uri";
    private static final String TYPE = "application/vnd.umamit.recipe+json";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!METHOD_SEND.equals(method) || arg == null || extras == null) {
            throw new IllegalArgumentException("unknown call: " + method);
        }
        File file = fileOf(arg);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(extras.getByteArray(EXTRA_BYTES));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(getContext().getPackageName() + ".sent-recipes")
                .appendPath(file.getName())
                .build();
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

    /** A file of the provider's own folder; any other name is refused. */
    private File fileOf(String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.startsWith(".")) {
            throw new IllegalArgumentException("bad name: " + name);
        }
        File dir = new File(getContext().getCacheDir(), "sent");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("no folder");
        }
        return new File(dir, name);
    }
}
