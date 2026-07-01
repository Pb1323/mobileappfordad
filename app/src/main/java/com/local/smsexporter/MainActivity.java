package com.local.smsexporter;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_READ_SMS = 1001;

    private TextView statusText;
    private Button permissionButton;
    private Button exportButton;
    private Button shareButton;
    private ProgressBar progressBar;
    private ArrayList<Uri> lastExportUris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refreshPermissionState();
    }

    private void buildUi() {
        int padding = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(0xFFF8FAFC);

        TextView title = new TextView(this);
        title.setText("SMS Exporter");
        title.setTextSize(28);
        title.setTextColor(0xFF0F172A);
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Export every SMS on this phone into local files in Downloads. No server, no account, no store required.");
        intro.setTextSize(16);
        intro.setTextColor(0xFF334155);
        intro.setPadding(0, 0, 0, dp(18));
        root.addView(intro);

        statusText = new TextView(this);
        statusText.setTextSize(15);
        statusText.setTextColor(0xFF1E293B);
        statusText.setPadding(0, 0, 0, dp(16));
        root.addView(statusText);

        permissionButton = primaryButton("Grant SMS Permission");
        permissionButton.setOnClickListener(v -> requestSmsPermission());
        root.addView(permissionButton);

        exportButton = primaryButton("Export SMS");
        exportButton.setOnClickListener(v -> exportSms());
        root.addView(exportButton);

        shareButton = secondaryButton("Share Last Export");
        shareButton.setEnabled(false);
        shareButton.setOnClickListener(v -> shareLastExport());
        root.addView(shareButton);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);
        setContentView(scrollView);
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundColor(0xFF1D4ED8);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        button.setLayoutParams(params);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = primaryButton(text);
        button.setTextColor(0xFF0F172A);
        button.setBackgroundColor(0xFFE2E8F0);
        return button;
    }

    private void refreshPermissionState() {
        boolean hasPermission = hasSmsPermission();
        permissionButton.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
        exportButton.setEnabled(hasPermission);
        statusText.setText(hasPermission
                ? "Ready. Tap Export SMS to create transcript files in Downloads/SMS Exporter."
                : "SMS permission is required before this app can read messages.");
    }

    private boolean hasSmsPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_SMS}, REQUEST_READ_SMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_SMS) {
            refreshPermissionState();
        }
    }

    private void exportSms() {
        if (!hasSmsPermission()) {
            requestSmsPermission();
            return;
        }

        setBusy(true, "Reading SMS messages...");

        new Thread(() -> {
            try {
                List<SmsMessage> messages = readSmsMessages();
                String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());

                ArrayList<Uri> uris = new ArrayList<>();
                uris.add(writeDownloadFile("sms-export-" + stamp + ".txt", "text/plain", buildText(messages)));
                uris.add(writeDownloadFile("sms-export-" + stamp + ".csv", "text/csv", buildCsv(messages)));
                uris.add(writeDownloadFile("sms-export-" + stamp + ".html", "text/html", buildHtml(messages)));

                runOnUiThread(() -> {
                    lastExportUris = uris;
                    shareButton.setEnabled(true);
                    setBusy(false, "Export complete. " + messages.size() + " SMS messages saved to Downloads/SMS Exporter.");
                    Toast.makeText(this, "SMS export complete", Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> setBusy(false, "Export failed: " + error.getMessage()));
            }
        }).start();
    }

    private List<SmsMessage> readSmsMessages() {
        ArrayList<SmsMessage> messages = new ArrayList<>();
        String[] projection = new String[]{
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.BODY,
                Telephony.Sms.THREAD_ID
        };

        try (Cursor cursor = getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                Telephony.Sms.DATE + " ASC"
        )) {
            if (cursor == null) {
                return messages;
            }

            int idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID);
            int addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);
            int dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE);
            int typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE);
            int bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY);
            int threadIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID);

            while (cursor.moveToNext()) {
                SmsMessage message = new SmsMessage();
                message.id = cursor.getLong(idIndex);
                message.address = safe(cursor.getString(addressIndex));
                message.dateMillis = cursor.getLong(dateIndex);
                message.type = cursor.getInt(typeIndex);
                message.body = safe(cursor.getString(bodyIndex));
                message.threadId = cursor.getLong(threadIndex);
                messages.add(message);
            }
        }

        return messages;
    }

    private Uri writeDownloadFile(String fileName, String mimeType, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        ContentResolver resolver = getContentResolver();
                    ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SMS Exporter");
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IllegalStateException("Could not create download file");
            }

            try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                if (outputStream == null) {
                    throw new IllegalStateException("Could not open download file");
                }
                outputStream.write(bytes);
            }

            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return uri;
    }

    private String buildText(List<SmsMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("SMS Export\n");
        builder.append("Generated: ").append(formatDate(System.currentTimeMillis())).append("\n");
        builder.append("Messages: ").append(messages.size()).append("\n\n");

        for (SmsMessage message : messages) {
            builder.append("[").append(formatDate(message.dateMillis)).append("] ");
            builder.append(message.direction()).append(" ");
            builder.append(message.address.isEmpty() ? "Unknown" : message.address).append("\n");
            builder.append(message.body).append("\n");
            builder.append("Thread: ").append(message.threadId).append(" | SMS ID: ").append(message.id).append("\n\n");
        }

        return builder.toString();
    }

    private String buildCsv(List<SmsMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("id,thread_id,direction,address,timestamp,message\n");
        for (SmsMessage message : messages) {
            builder.append(csv(message.id)).append(",");
            builder.append(csv(message.threadId)).append(",");
            builder.append(csv(message.direction())).append(",");
            builder.append(csv(message.address)).append(",");
            builder.append(csv(formatDate(message.dateMillis))).append(",");
            builder.append(csv(message.body)).append("\n");
        }
        return builder.toString();
    }

    private String buildHtml(List<SmsMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><head><meta charset=\"utf-8\">");
        builder.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        builder.append("<title>SMS Export</title>");
        builder.append("<style>");
        builder.append("body{font-family:Arial,sans-serif;margin:24px;background:#f8fafc;color:#0f172a}");
        builder.append("article{background:white;border:1px solid #e2e8f0;border-radius:8px;padding:14px;margin:12px 0}");
        builder.append(".meta{color:#475569;font-size:13px;margin-bottom:8px}.body{white-space:pre-wrap;font-size:15px}");
        builder.append("</style></head><body>");
        builder.append("<h1>SMS Export</h1>");
        builder.append("<p>Generated ").append(escapeHtml(formatDate(System.currentTimeMillis()))).append(". ");
        builder.append(messages.size()).append(" messages.</p>");

        for (SmsMessage message : messages) {
            builder.append("<article><div class=\"meta\">");
            builder.append(escapeHtml(formatDate(message.dateMillis))).append(" | ");
            builder.append(escapeHtml(message.direction())).append(" | ");
            builder.append(escapeHtml(message.address.isEmpty() ? "Unknown" : message.address));
            builder.append(" | Thread ").append(message.threadId);
            builder.append("</div><div class=\"body\">");
            builder.append(escapeHtml(message.body));
            builder.append("</div></article>");
        }

        builder.append("</body></html>");
        return builder.toString();
    }

    private void shareLastExport() {
        if (lastExportUris.isEmpty()) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("text/*");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, lastExportUris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share SMS export"));
    }

    private void setBusy(boolean busy, String status) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        permissionButton.setEnabled(!busy);
        exportButton.setEnabled(!busy && hasSmsPermission());
        shareButton.setEnabled(!busy && !lastExportUris.isEmpty());
        statusText.setText(status);
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(millis));
    }

    private String csv(Object value) {
        String text = String.valueOf(value == null ? "" : value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String escapeHtml(String value) {
        return safe(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static class SmsMessage {
        long id;
        long threadId;
        String address;
        long dateMillis;
        int type;
        String body;

        String direction() {
            if (type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                return "Sent";
            }
            if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                return "Received";
            }
            if (type == Telephony.Sms.MESSAGE_TYPE_DRAFT) {
                return "Draft";
            }
            if (type == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
                return "Outbox";
            }
            if (type == Telephony.Sms.MESSAGE_TYPE_FAILED) {
                return "Failed";
            }
            if (type == Telephony.Sms.MESSAGE_TYPE_QUEUED) {
                return "Queued";
            }
            return "Other";
        }
    }
}

