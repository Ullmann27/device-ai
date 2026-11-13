package at.heinz.deviceai;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** Self-contained test APK provider. */
public final class ContinuumNativePdfProbeProvider extends ContentProvider {
    public static final String NAME = "continuum-native-probe.pdf";
    private final AtomicInteger queries = new AtomicInteger();
    private final AtomicInteger opens = new AtomicInteger();

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"counts".equals(method)) throw new IllegalArgumentException("UNKNOWN_PROBE_METHOD");
        Bundle result = new Bundle();
        result.putInt("queries", queries.get());
        result.putInt("opens", opens.get());
        return result;
    }

    private static boolean knownUri(Uri uri) {
        return "/document/4711".equals(uri.getPath()) &&
            uri.getQuery() == null && uri.getFragment() == null;
    }

    private static void requireUri(Uri uri) {
        if (!knownUri(uri)) throw new IllegalArgumentException("UNKNOWN_SYNTHETIC_FIXTURE");
    }

    @Override public String getType(Uri uri) {
        requireUri(uri);
        return "application/pdf";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        requireUri(uri);
        queries.incrementAndGet();
        String[] columns = projection != null ? projection :
            new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = NAME;
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = (long) bytes().length;
        }
        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(row);
        return cursor;
    }

    @Override public synchronized ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!knownUri(uri)) throw new FileNotFoundException("UNKNOWN_SYNTHETIC_FIXTURE");
        if (!"r".equals(mode)) throw new FileNotFoundException("READ_ONLY_SYNTHETIC_FIXTURE");
        opens.incrementAndGet();
        if (getContext() == null) throw new IllegalStateException("NO_PROVIDER_CONTEXT");
        File fixture = new File(getContext().getCacheDir(), NAME);
        if (!fixture.exists()) {
            try (FileOutputStream output = new FileOutputStream(fixture)) {
                output.write(bytes());
            } catch (IOException error) {
                FileNotFoundException failure = new FileNotFoundException("FIXTURE_WRITE_FAILED");
                failure.initCause(error);
                throw failure;
            }
        }
        return ParcelFileDescriptor.open(fixture, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    public static byte[] bytes() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String stream = "BT /F1 18 Tf 72 720 Td (CONTINUUM PDF TEST 4711) Tj ET\n";
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length " + stream.length() + " >>\nstream\n" + stream + "endstream"
        };
        write(output, "%PDF-1.4\n");
        int[] offsets = new int[objects.length];
        for (int i = 0; i < objects.length; i++) {
            offsets[i] = output.size();
            write(output, (i + 1) + " 0 obj\n" + objects[i] + "\nendobj\n");
        }
        int xref = output.size();
        write(output, "xref\n0 " + (objects.length + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) write(output, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        write(output, "trailer\n<< /Size " + (objects.length + 1) +
            " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return output.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        output.write(bytes, 0, bytes.length);
    }
}
