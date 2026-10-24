package com.aiwolfie.flashdock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_BOOT = 101;
    private static final int PICK_RECOVERY = 102;
    private static final int PICK_ROM = 103;
    private static final int PICK_SYSTEM = 104;
    private static final int PICK_OEM = 105;
    private static final int SAVE_PACKAGE = 201;

    private static final String BOOT = "boot";
    private static final String RECOVERY = "recovery";
    private static final String ROM = "rom";
    private static final String SYSTEM_IMAGE = "system_image";
    private static final String OEM_PACKAGE = "oem_package";

    private static final int BG = 0xFF0A1018;
    private static final int CARD = 0xFF121C28;
    private static final int CARD_ALT = 0xFF182433;
    private static final int TEXT = 0xFFEAF2F8;
    private static final int MUTED = 0xFF9AABBC;
    private static final int ACCENT = 0xFF75E0C2;
    private static final int WARN = 0xFFFFC979;
    private static final int RED = 0xFFFF8B8B;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private TextView rootStatus;
    private TextView selectionInfo;
    private TextView outputLog;
    private Button continueButton;

    private String pendingKind;
    private String pendingName;
    private String pendingSha256;
    private long pendingSize;
    private File pendingFile;
    private Uri currentSourceUri;
    private String currentSourceName;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF0A1018);
        getWindow().setNavigationBarColor(0xFF0A1018);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUi();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(32));
        scroll.addView(content);
        setContentView(scroll);

        TextView eyebrow = text("DEVICE MAINTENANCE  /  ANDROID", 11, ACCENT, true);
        eyebrow.setLetterSpacing(0.1f);
        content.addView(eyebrow);
        TextView title = text("FlashDock", 34, TEXT, true);
        title.setPadding(0, dp(5), 0, 0);
        content.addView(title);
        TextView subtitle = text("Image inspection, backups, and controlled flashing.", 14, MUTED, false);
        subtitle.setPadding(0, dp(2), 0, dp(18));
        content.addView(subtitle);

        LinearLayout deviceCard = card();
        deviceCard.addView(text("DEVICE", 11, ACCENT, true));
        deviceCard.addView(text(Build.MANUFACTURER + " " + Build.MODEL + "  ·  Android " + Build.VERSION.RELEASE, 15, TEXT, true));
        deviceCard.addView(text("Codename: " + Build.DEVICE + "  ·  SDK " + Build.VERSION.SDK_INT, 12, MUTED, false));
        rootStatus = text("Root access: not checked", 13, WARN, true);
        rootStatus.setPadding(0, dp(8), 0, dp(4));
        deviceCard.addView(rootStatus);
        Button checkRoot = button("CHECK ROOT ACCESS", true);
        checkRoot.setOnClickListener(v -> checkRootAccess());
        deviceCard.addView(checkRoot);
        content.addView(deviceCard);

        addSectionTitle("DEVICE CONTROLS  /  NO-ROOT OPTIONS");
        addToolCard("Restart Android", "Opens the system power dialog through an optional accessibility service. You confirm Restart yourself; FlashDock cannot silently reboot an ordinary unrooted phone.", "OPEN RESTART MENU", v -> openPowerMenu("restart"));
        addToolCard("Shut down Android", "Uses the same Android power dialog. Enable FlashDock Power Menu in Accessibility settings first; then select Power off in the system dialog.", "OPEN SHUTDOWN MENU", v -> openPowerMenu("shutdown"));
        addToolCard("Recovery mode", "With root, FlashDock can request a recovery reboot. Without root, use OPPO's hardware-key method shown in the guide.", "ENTER / SHOW RECOVERY GUIDE", v -> rebootMode("recovery"));

        addSectionTitle("01  /  RAW IMAGES");
        addToolCard("Kernel / boot image", "Flash a compatible Android boot image to the boot partition. A full backup is created before writing.", "SELECT boot.img", v -> chooseFile(PICK_BOOT, "image/*"));
        addToolCard("Recovery image", "Only works when this device has a separate recovery partition. Recovery-as-boot layouts are not guessed.", "SELECT recovery.img", v -> chooseFile(PICK_RECOVERY, "image/*"));

        addSectionTitle("02  /  OS & FIRMWARE PACKAGES");
        addToolCard("ROM / OTA ZIP", "Copies the package to a location you choose. Installation must be completed by a compatible recovery or the OEM updater.", "SELECT ROM ZIP", v -> chooseFile(PICK_ROM, "application/zip"));
        addToolCard("Other .img / full OS image", "Inspects and stages the file only. Direct flashing of system, vendor, vbmeta, sparse images, and unknown partition layouts is disabled. Unrooted self-flashing requires a compatible external fastboot host and a device that actually supports Fastboot.", "SELECT .IMG FILE", v -> chooseFile(PICK_SYSTEM, "*/*"));
        addToolCard("Stock OEM firmware (.ofp / .ozip / ZIP)", "Stages the package and calculates SHA-256. A recovery-installable ZIP can be installed only by a compatible recovery. OPPO .ofp files need a matching OEM service tool; this app does not decrypt or flash them.", "SELECT STOCK FIRMWARE", v -> chooseFile(PICK_OEM, "*/*"));

        addSectionTitle("03  /  SELECTED FILE");
        LinearLayout selectedCard = card();
        selectionInfo = text("No file selected. Choose an image or package above.", 13, MUTED, false);
        selectedCard.addView(selectionInfo);
        continueButton = button("CONTINUE", true);
        continueButton.setEnabled(false);
        continueButton.setAlpha(0.45f);
        continueButton.setOnClickListener(v -> continueWithSelection());
        LinearLayout.LayoutParams continueLp = new LinearLayout.LayoutParams(-1, dp(50));
        continueLp.topMargin = dp(12);
        selectedCard.addView(continueButton, continueLp);
        content.addView(selectedCard);

        addSectionTitle("OPERATION LOG");
        LinearLayout logCard = card();
        outputLog = text("No operation has run.", 12, MUTED, false);
        outputLog.setTypeface(Typeface.MONOSPACE);
        logCard.addView(outputLog);
        content.addView(logCard);

        TextView footer = text("FlashDock does not unlock bootloaders, bypass verified boot, or guess undocumented partitions. Keep a separate copy of critical data.", 11, MUTED, false);
        footer.setPadding(dp(2), dp(16), dp(2), 0);
        content.addView(footer);
    }

    private void addSectionTitle(String title) {
        TextView label = text(title, 11, ACCENT, true);
        label.setPadding(dp(2), dp(20), dp(2), dp(9));
        content.addView(label);
    }

    private void addToolCard(String heading, String description, String action, View.OnClickListener listener) {
        LinearLayout box = card();
        TextView h = text(heading, 17, TEXT, true);
        h.setPadding(0, 0, 0, dp(5));
        box.addView(h);
        TextView d = text(description, 13, MUTED, false);
        d.setLineSpacing(dp(2), 1.0f);
        box.addView(d);
        Button b = button(action, false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(46));
        p.topMargin = dp(12);
        box.addView(b, p);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = dp(10);
        content.addView(box, cardLp);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(14), dp(15), dp(15));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0xFF243445);
        box.setBackground(bg);
        return box;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? BG : TEXT);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(primary ? ACCENT : CARD_ALT);
        shape.setCornerRadius(dp(12));
        if (!primary) shape.setStroke(dp(1), 0xFF33495D);
        b.setBackground(shape);
        b.setPadding(dp(12), 0, dp(12), 0);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void chooseFile(int request, String mime) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        if (Build.VERSION.SDK_INT >= 19) intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, request);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == SAVE_PACKAGE) {
            savePendingPackageToUri(uri);
            return;
        }
        String kind;
        if (requestCode == PICK_BOOT) kind = BOOT;
        else if (requestCode == PICK_RECOVERY) kind = RECOVERY;
        else if (requestCode == PICK_ROM) kind = ROM;
        else if (requestCode == PICK_SYSTEM) kind = SYSTEM_IMAGE;
        else if (requestCode == PICK_OEM) kind = OEM_PACKAGE;
        else return;
        importSelection(uri, kind);
    }

    private void importSelection(Uri uri, String kind) {
        currentSourceUri = uri;
        currentSourceName = queryDisplayName(uri);
        continueButton.setEnabled(false);
        continueButton.setAlpha(0.45f);
        selectionInfo.setText("Reading file and calculating SHA-256…");
        outputLog.setText("Inspecting selected file…");
        executor.execute(() -> {
            try {
                File out = new File(getCacheDir(), "selected_" + kind + "_" + System.currentTimeMillis());
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long size = 0;
                byte[] buffer = new byte[64 * 1024];
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    if (in == null) throw new IllegalStateException("Cannot open the selected file.");
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        size += read;
                    }
                    fos.getFD().sync();
                }
                if (size == 0) throw new IllegalStateException("Selected file is empty.");
                String sha = toHex(digest.digest());
                if (BOOT.equals(kind) || RECOVERY.equals(kind)) {
                    byte[] header = new byte[8];
                    try (InputStream in = new java.io.FileInputStream(out)) {
                        if (in.read(header) != 8) throw new IllegalStateException("Image is too small to be a boot image.");
                    }
                    String magic = new String(header, java.nio.charset.StandardCharsets.US_ASCII);
                    if (!"ANDROID!".equals(magic)) {
                        out.delete();
                        throw new IllegalStateException("Rejected image header: expected ANDROID! boot image. This build deliberately does not flash vendor_boot, vbmeta, sparse system images, or unknown formats.");
                    }
                }
                final File finalFile = out;
                final long finalSize = size;
                final String finalSha = sha;
                runOnUiThread(() -> {
                    if (pendingFile != null && !pendingFile.equals(finalFile)) pendingFile.delete();
                    pendingFile = finalFile;
                    pendingKind = kind;
                    pendingName = currentSourceName == null ? finalFile.getName() : currentSourceName;
                    pendingSize = finalSize;
                    pendingSha256 = finalSha;
                    String detail = "File: " + pendingName + "\nType: " + kindLabel(kind) + "\nSize: " + humanSize(finalSize) + "\nSHA-256: " + finalSha;
                    if (BOOT.equals(kind) || RECOVERY.equals(kind)) detail += "\nHeader: ANDROID! (boot image)\nTarget will be checked again before any write.";
                    if (SYSTEM_IMAGE.equals(kind)) detail += "\nDirect system-partition writes are disabled.";
                    if (OEM_PACKAGE.equals(kind)) detail += "\nOEM package flashing is not implemented; package will only be copied.";
                    selectionInfo.setText(detail);
                    continueButton.setEnabled(true);
                    continueButton.setAlpha(1f);
                    continueButton.setText((BOOT.equals(kind) || RECOVERY.equals(kind)) ? "REVIEW & FLASH" : "PREPARE PACKAGE");
                    outputLog.setText("Inspection complete. SHA-256 computed locally; no file uploaded.\n" + detail);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    selectionInfo.setText("File rejected or could not be read.\n" + e.getMessage());
                    outputLog.setText("INSPECTION FAILED\n" + e.getMessage());
                    continueButton.setEnabled(false);
                    continueButton.setAlpha(0.45f);
                });
            }
        });
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0) return cursor.getString(col);
            }
        } catch (Exception ignored) { }
        return "selected_file";
    }

    private void continueWithSelection() {
        if (pendingFile == null || pendingKind == null) return;
        if (BOOT.equals(pendingKind) || RECOVERY.equals(pendingKind)) showFlashConfirmation();
        else startSaveDocument();
    }

    private void showFlashConfirmation() {
        String phrase = BOOT.equals(pendingKind) ? "FLASH BOOT" : "FLASH RECOVERY";
        String target = BOOT.equals(pendingKind) ? "boot" : "recovery";
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView warning = text("This writes a raw block partition. The app will first attempt a full backup to /sdcard/FlashDock/backups and verify the written bytes. A wrong image can prevent Android from booting. The target partition must exist and fit the image.", 13, WARN, false);
        box.addView(warning);
        TextView summary = text("\nTarget: " + target + "\nImage: " + pendingName + "\nSize: " + humanSize(pendingSize) + "\nSHA-256: " + pendingSha256 + "\n\nType " + phrase + " to continue.", 12, TEXT, false);
        summary.setTypeface(Typeface.MONOSPACE);
        box.addView(summary);
        EditText confirm = new EditText(this);
        confirm.setSingleLine(true);
        confirm.setHint(phrase);
        confirm.setTextColor(TEXT);
        confirm.setHintTextColor(MUTED);
        confirm.setPadding(dp(8), dp(8), dp(8), dp(8));
        box.addView(confirm);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Destructive operation")
                .setView(box)
                .setNegativeButton("CANCEL", (d, w) -> { })
                .setPositiveButton("FLASH", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!phrase.equals(confirm.getText().toString().trim())) {
                confirm.setError("Enter " + phrase + " exactly");
                return;
            }
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(confirm.getWindowToken(), 0);
            dialog.dismiss();
            executeRawFlash(target, pendingFile);
        }));
        dialog.show();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void executeRawFlash(String target, File image) {
        continueButton.setEnabled(false);
        continueButton.setAlpha(0.45f);
        outputLog.setText("Requesting root. Checking the target, backup space, image size, and partition before writing…");
        executor.execute(() -> {
            try {
                File script = new File(getFilesDir(), "flash_raw_image.sh");
                try (InputStream in = getAssets().open("flash_raw_image.sh"); FileOutputStream out = new FileOutputStream(script)) {
                    byte[] b = new byte[8192]; int n;
                    while ((n = in.read(b)) != -1) out.write(b, 0, n);
                    out.getFD().sync();
                }
                String command = "sh " + shellQuote(script.getAbsolutePath()) + " " + shellQuote(target) + " " + shellQuote(image.getAbsolutePath());
                CommandResult result = rootCommand(command);
                runOnUiThread(() -> {
                    outputLog.setText("Exit code: " + result.exitCode + "\n" + result.output);
                    if (result.exitCode == 0 && result.output.contains("SUCCESS:")) {
                        rootStatus.setText("Root access: confirmed for this operation");
                        rootStatus.setTextColor(ACCENT);
                        showInfo("Flash verified", "The image bytes matched the target. Read the backup path in the operation log. Reboot only when ready.");
                    } else {
                        rootStatus.setText("Root access / flash operation failed");
                        rootStatus.setTextColor(RED);
                        showInfo("Flash not confirmed", "Review the operation log. If a write began, do not assume the device is safe to reboot. Preserve the backup path shown in the log.\n\nNo-root note: UBL alone does not grant Android partition-write access. An ordinary app cannot write the running phone’s raw partitions without root. A separate computer can use Fastboot only if this exact device exposes a compatible Fastboot interface.");
                    }
                    continueButton.setEnabled(pendingFile != null);
                    continueButton.setAlpha(pendingFile != null ? 1f : 0.45f);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    outputLog.setText("FLASH FAILED\n" + e);
                    continueButton.setEnabled(pendingFile != null);
                    continueButton.setAlpha(pendingFile != null ? 1f : 0.45f);
                    showInfo("Flash failed", e.toString());
                });
            }
        });
    }

    private void startSaveDocument() {
        String suggested = safeFilename(pendingName == null ? "firmware_package.bin" : pendingName);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, suggested);
        startActivityForResult(intent, SAVE_PACKAGE);
    }

    private void savePendingPackageToUri(Uri destination) {
        if (pendingFile == null) return;
        File source = pendingFile;
        String kind = pendingKind;
        continueButton.setEnabled(false);
        continueButton.setAlpha(0.45f);
        outputLog.setText("Copying verified package to the location you selected…");
        executor.execute(() -> {
            try {
                try (InputStream in = new java.io.FileInputStream(source); OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                    if (out == null) throw new IllegalStateException("Could not open destination.");
                    byte[] buffer = new byte[64 * 1024]; int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    out.flush();
                }
                runOnUiThread(() -> {
                    outputLog.setText("Package copied to the selected document location.\nName: " + pendingName + "\nSize: " + humanSize(pendingSize) + "\nSHA-256: " + pendingSha256 + "\n\nNo system partition was written.");
                    continueButton.setEnabled(pendingFile != null);
                    continueButton.setAlpha(1f);
                    if (ROM.equals(kind)) {
                        new AlertDialog.Builder(this)
                                .setTitle("ROM package staged")
                                .setMessage("The ZIP is copied. Install it only if it is built for this exact device and your recovery supports the package's signature and installation format.\n\nReboot to recovery now?")
                                .setNegativeButton("NOT NOW", (d, w) -> { })
                                .setPositiveButton("REBOOT RECOVERY", (d, w) -> rebootMode("recovery"))
                                .show();
                    } else if (SYSTEM_IMAGE.equals(kind)) {
                        new AlertDialog.Builder(this)
                                .setTitle("System image staged")
                                .setMessage("The image has not been flashed. Most system images need a matching bootloader/fastbootd workflow and correct partition mapping. Copy it to the computer/fastboot host before rebooting the phone to bootloader mode.\n\nReboot to bootloader now?")
                                .setNegativeButton("NOT NOW", (d, w) -> { })
                                .setPositiveButton("REBOOT BOOTLOADER", (d, w) -> rebootMode("bootloader"))
                                .show();
                    } else {
                        String ext = pendingName == null ? "" : pendingName.toLowerCase(Locale.ROOT);
                        if (ext.endsWith(".ofp") || ext.endsWith(".ozip")) {
                            showInfo("OEM package staged — NOT FLASHED", "This file was copied only. OPPO .ofp/.ozip packages are OEM-specific and are not generic Android images or recovery ZIPs. Use a matching supported OPPO service/update tool for this exact model and firmware; some service workflows require authorization. No partition was changed.");
                        } else {
                            showInfo("Firmware package staged — NOT FLASHED", "This file was copied only. A .zip is installable only if it is actually a recovery-compatible update package for this exact model and the installed recovery supports it. Stock full-firmware ZIPs are not all recovery-installable. No partition was changed.");
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    outputLog.setText("COPY FAILED\n" + e);
                    continueButton.setEnabled(pendingFile != null);
                    continueButton.setAlpha(pendingFile != null ? 1f : 0.45f);
                    showInfo("Copy failed", e.toString());
                });
            }
        });
    }

    private void checkRootAccess() {
        rootStatus.setText("Root access: checking… approve the superuser prompt if shown");
        rootStatus.setTextColor(WARN);
        executor.execute(() -> {
            CommandResult result = rootCommand("id");
            boolean ok = result.exitCode == 0 && result.output.contains("uid=0");
            runOnUiThread(() -> {
                rootStatus.setText(ok ? "Root access: confirmed" : "Root access: unavailable or denied");
                rootStatus.setTextColor(ok ? ACCENT : RED);
                outputLog.setText("Root check exit code: " + result.exitCode + "\n" + result.output);
            });
        });
    }

    private void openPowerMenu(String desiredAction) {
        if (PowerMenuAccessibilityService.openPowerDialog()) {
            String action = "restart".equals(desiredAction) ? "Choose Restart" : "Choose Power off";
            outputLog.setText("Android system power dialog opened. " + action + " to complete the action. FlashDock does not select it for you.");
            Toast.makeText(this, action + " in the system power dialog", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Enable no-root power menu")
                .setMessage("A normal Android app cannot directly power off or reboot the phone without privileged access. FlashDock can open the Android power dialog after you explicitly enable its small accessibility service. The service only requests the system power dialog; it does not read screen content or capture input.\n\nTap Settings, enable ‘FlashDock Power Menu’, return here, then tap this button again. You can also use the phone's physical power button instead.")
                .setNegativeButton("CANCEL", (d, w) -> { })
                .setPositiveButton("OPEN ACCESSIBILITY SETTINGS", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception e) {
                        showInfo("Settings unavailable", "Open Android Settings → Accessibility manually.");
                    }
                })
                .show();
    }

    private void rebootMode(String mode) {
        String title = "recovery".equals(mode) ? "Enter recovery mode" : "Enter bootloader mode";
        String prompt = "recovery".equals(mode)
                ? "FlashDock will try a root-based reboot first. Without root, power the OPPO phone off, then hold Power + Volume Down together until the OPPO logo appears; follow the recovery screen prompts."
                : "FlashDock will try a root-based reboot first. Without root, a computer with USB debugging can request `adb reboot bootloader`, but only if this model exposes a compatible bootloader mode. OPPO CPH2185 is not guaranteed to support standard Fastboot; do not flash unless `fastboot devices` detects it and the product matches.\n\nTry the root command now?";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(prompt)
                .setNegativeButton("CANCEL", (d, w) -> { })
                .setNeutralButton("NO-ROOT GUIDE", (d, w) -> showNoRootModeGuide(mode))
                .setPositiveButton("TRY ROOT REBOOT", (d, w) -> executor.execute(() -> {
                    String cmd = "reboot" + ("recovery".equals(mode) ? " recovery" : " bootloader");
                    CommandResult result = rootCommand(cmd);
                    runOnUiThread(() -> {
                        outputLog.setText("Reboot request exit code: " + result.exitCode + "\n" + result.output);
                        if (result.exitCode != 0) showNoRootModeGuide(mode);
                    });
                }))
                .show();
    }

    private void showNoRootModeGuide(String mode) {
        if ("recovery".equals(mode)) {
            showInfo("OPPO recovery — no root needed", "1. Power the phone off completely.\n2. Hold Power + Volume Down together.\n3. When the OPPO logo appears, follow the on-screen instructions and choose a language if prompted.\n\nThis enters recovery; it does not automatically install or flash a package. Avoid Wipe/Format unless you intend to erase data.");
        } else {
            showInfo("Bootloader / Fastboot — no-root guide", "If USB debugging is already enabled, connect the phone to a computer with Android platform-tools and run: adb devices\nadb reboot bootloader\nfastboot devices\n\nContinue only if fastboot detects the phone and reports the expected product. OPPO CPH2185 may not expose standard Fastboot. If `fastboot devices` is empty, stop; do not try random partition-writing tools.");
        }
    }

    private CommandResult rootCommand(String command) {
        StringBuilder output = new StringBuilder();
        int code = 127;
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 30000) output.append(line).append('\n');
                }
            }
            code = process.waitFor();
        } catch (Exception e) {
            output.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
        }
        return new CommandResult(code, output.toString().trim());
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private String safeFilename(String value) {
        String clean = value.replaceAll("[^A-Za-z0-9._() -]", "_").trim();
        return clean.isEmpty() ? "firmware_package.bin" : clean;
    }

    private String kindLabel(String kind) {
        if (BOOT.equals(kind)) return "Boot / kernel image";
        if (RECOVERY.equals(kind)) return "Recovery image";
        if (ROM.equals(kind)) return "ROM / OTA ZIP";
        if (SYSTEM_IMAGE.equals(kind)) return "System image";
        return "OEM stock firmware package";
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(Locale.US, "%.2f KiB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.2f MiB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.2f GiB", bytes / (1024.0 * 1024 * 1024));
    }

    private String toHex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) s.append(String.format(Locale.US, "%02x", b & 0xff));
        return s.toString();
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", (d, w) -> { }).show();
    }

    private static final class CommandResult {
        final int exitCode;
        final String output;
        CommandResult(int exitCode, String output) { this.exitCode = exitCode; this.output = output; }
    }
}
