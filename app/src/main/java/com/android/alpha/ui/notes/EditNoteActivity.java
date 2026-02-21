package com.android.alpha.ui.notes;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.*;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.auth.LoginActivity;
import com.android.alpha.utils.DialogUtils;
import com.google.gson.Gson;
import yuku.ambilwarna.AmbilWarnaDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.ArrayList;

/**
 * Activity untuk mengedit catatan dengan dukungan formatting teks lengkap,
 * undo/redo berbasis snapshot, serialisasi HTML kustom, dan pengingat alarm.
 */
public class EditNoteActivity extends AppCompatActivity {

    // --- Konstanta ---
    private static final String TAG = "EditNoteActivity";
    private static final int MAX_UNDO_HISTORY = 100;
    private static final long UNDO_DEBOUNCE_MS = 500;
    private static final float BASE_TEXT_SIZE_SP = 14f;
    private static final float SIZE_STEP_SP = 2f;
    private static final float MIN_TEXT_SIZE_SP = 10f;
    private static final float MAX_TEXT_SIZE_SP = 36f;

    // --- ViewModel & Model ---
    private NoteViewModel viewModel;
    private Note currentNote;

    // --- Komponen UI utama ---
    private EditText etTitle, etContent;
    private TextView tvMetadata;
    private LinearLayout layoutDefaultActions, layoutInputActions;
    private ImageButton btnDelete, btnShare;

    // --- Tombol undo/redo & simpan manual ---
    private ImageButton btnUndo, btnRedo, btnSaveManual;

    // --- Stack undo/redo berbasis snapshot SpannableStringBuilder ---
    private final Deque<SpannableStringBuilder> undoStack = new ArrayDeque<>();
    private final Deque<SpannableStringBuilder> redoStack = new ArrayDeque<>();

    // --- Flag mencegah TextWatcher terpicu saat restore history ---
    private boolean isRestoringHistory = false;

    // --- Debounce handler untuk push snapshot undo ---
    private final Handler undoDebounceHandler = new Handler(Looper.getMainLooper());
    private final Runnable undoPushRunnable = this::pushUndoSnapshot;

    // --- Tombol toolbar formatting ---
    private ImageButton btnBold, btnItalic, btnUnderline, btnHighlight, btnStrike, btnTextColor;
    private ImageButton btnAlignLeft, btnAlignCenter, btnAlignRight;
    private ImageButton btnBullet, btnSizeUp, btnSizeDown;

    // --- State editor ---
    private boolean bulletMode = false;
    private String originalTitle = "";
    private String originalContent = "";
    private int currentTextColor = Color.BLACK;
    private int currentHighlightColor = Color.YELLOW;

    // --- Utilitas ---
    private final Gson gson = new Gson();

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_note);
        initViews();
        initViewModel();
        loadNoteData();
        setupEditorWatcher();
        setupButtons();
        setupKeyboardToolbar();
        setupBackPressed();
        detectKeyboardVisibility();
    }

    // =========================================================================
    // INISIALISASI
    // =========================================================================

    /** Menginisialisasi semua komponen UI dan listener dasar. */
    private void initViews() {
        etTitle   = findViewById(R.id.et_title);
        etContent = findViewById(R.id.et_content);
        tvMetadata = findViewById(R.id.tv_metadata);

        layoutDefaultActions = findViewById(R.id.layout_default_actions);
        layoutInputActions   = findViewById(R.id.layout_input_actions);

        btnDelete     = findViewById(R.id.btn_delete);
        btnShare      = findViewById(R.id.btn_share);
        btnUndo       = findViewById(R.id.btn_undo);
        btnRedo       = findViewById(R.id.btn_redo);
        btnSaveManual = findViewById(R.id.btn_save_manual);

        btnBold      = findViewById(R.id.action_bold);
        btnItalic    = findViewById(R.id.action_italic);
        btnUnderline = findViewById(R.id.action_underline);
        btnHighlight = findViewById(R.id.action_highlight);
        btnStrike    = findViewById(R.id.action_strike);
        btnTextColor = findViewById(R.id.action_text_color);
        btnBullet    = findViewById(R.id.action_bullet_list);
        btnSizeUp    = findViewById(R.id.action_size_up);
        btnSizeDown  = findViewById(R.id.action_size_down);
        btnAlignLeft   = findViewById(R.id.action_align_left);
        btnAlignCenter = findViewById(R.id.action_align_center);
        btnAlignRight  = findViewById(R.id.action_align_right);

        refreshUndoRedoButtons();

        // Auto-linkify URL di konten
        etContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable editable) {
                Linkify.addLinks(editable, Linkify.WEB_URLS);
                etContent.setMovementMethod(LinkMovementMethod.getInstance());
            }
        });
        etContent.setLinkTextColor(Color.BLUE);

        // Tampilkan cursor & keyboard saat fokus/klik
        etContent.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) etContent.setCursorVisible(true);
        });
        etContent.setOnClickListener(v -> {
            etContent.setCursorVisible(true);
            etContent.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etContent, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    /** Menginisialisasi ViewModel dan validasi sesi pengguna. */
    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);

        String userId = getIntent().getStringExtra("user_id");
        if (userId == null && UserSession.getInstance().isInitialized()) {
            userId = UserSession.getInstance()
                    .getUserData(UserSession.getInstance().getUsername()).userId;
        }
        if (userId == null) {
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }
        viewModel.setUserId(userId);
    }

    /** Memuat data catatan dari intent atau membuat catatan baru jika tidak ada. */
    private void loadNoteData() {
        String noteId = getIntent().getStringExtra("note_id");
        if (noteId != null) currentNote = viewModel.getNoteById(this, noteId);

        if (currentNote != null) {
            etTitle.setText(currentNote.getTitle());
            etContent.setText(htmlToSpannable(currentNote.getContent()));
            originalTitle   = etTitle.getText().toString();
            originalContent = spannableToHtml(etContent.getText());
        } else {
            currentNote = new Note(UUID.randomUUID().toString());
            currentNote.setTimestamp(System.currentTimeMillis());
        }

        pushUndoSnapshot();
        updateMetadata();
    }

    // =========================================================================
    // WATCHER EDITOR
    // =========================================================================

    /** Mendaftarkan TextWatcher untuk auto-bullet, debounce undo, dan update toolbar. */
    private void setupEditorWatcher() {
        etContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateToolbarState();
                if (isRestoringHistory) return;
                // Tambahkan bullet otomatis saat Enter pada mode bullet
                if (bulletMode && count > 0 && s.subSequence(start, start + count).toString().contains("\n")) {
                    etContent.post(() -> {
                        Editable editable = etContent.getText();
                        int cursor = etContent.getSelectionStart();
                        if (cursor > 0 && editable.charAt(cursor - 1) == '\n')
                            editable.insert(cursor, "• ");
                    });
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isRestoringHistory) return;
                // Nonaktifkan bullet mode jika bullet dihapus manual
                int cursor = etContent.getSelectionStart();
                int lineStart = s.toString().lastIndexOf('\n', cursor - 1) + 1;
                if (bulletMode && !s.toString().startsWith("• ", lineStart)) {
                    bulletMode = false;
                    updateToolbarState();
                }
                updateMetadata();
                // Debounce push snapshot undo
                undoDebounceHandler.removeCallbacks(undoPushRunnable);
                undoDebounceHandler.postDelayed(undoPushRunnable, UNDO_DEBOUNCE_MS);
                redoStack.clear();
                refreshUndoRedoButtons();
            }
        });

        // Update toolbar saat selection action mode berubah
        etContent.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
            @Override public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return true; }
            @Override public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { updateToolbarState(); return true; }
            @Override public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
            @Override public void onDestroyActionMode(android.view.ActionMode mode) { updateToolbarState(); }
        });
    }

    // =========================================================================
    // UNDO / REDO
    // =========================================================================

    /** Menyimpan snapshot teks + span saat ini ke undo stack. */
    private void pushUndoSnapshot() {
        SpannableStringBuilder snapshot = new SpannableStringBuilder(etContent.getText());
        SpannableStringBuilder top = undoStack.peek();
        if (top != null && top.toString().equals(snapshot.toString())) return;
        undoStack.push(snapshot);
        if (undoStack.size() > MAX_UNDO_HISTORY)
            undoStack.removeLast();
        refreshUndoRedoButtons();
    }

    /** Membatalkan perubahan terakhir (undo). */
    private void undo() {
        if (undoStack.size() <= 1) return;
        isRestoringHistory = true;
        redoStack.push(undoStack.pop());
        SpannableStringBuilder restored = new SpannableStringBuilder(undoStack.peek());
        int cursor = Math.min(etContent.getSelectionStart(), restored.length());
        etContent.setText(restored);
        etContent.setSelection(Math.max(0, cursor));
        isRestoringHistory = false;
        refreshUndoRedoButtons();
    }

    /** Menerapkan ulang perubahan yang dibatalkan (redo). */
    private void redo() {
        if (redoStack.isEmpty()) return;
        isRestoringHistory = true;
        SpannableStringBuilder restored = new SpannableStringBuilder(redoStack.pop());
        undoStack.push(new SpannableStringBuilder(restored));
        int cursor = Math.min(etContent.getSelectionStart(), restored.length());
        etContent.setText(restored);
        etContent.setSelection(Math.max(0, cursor));
        isRestoringHistory = false;
        refreshUndoRedoButtons();
    }

    /** Memperbarui status enabled/alpha tombol undo & redo. */
    private void refreshUndoRedoButtons() {
        boolean canUndo = undoStack.size() > 1;
        boolean canRedo = !redoStack.isEmpty();
        btnUndo.setEnabled(canUndo);
        btnRedo.setEnabled(canRedo);
        btnUndo.setAlpha(canUndo ? 1f : 0.35f);
        btnRedo.setAlpha(canRedo ? 1f : 0.35f);
    }

    // =========================================================================
    // SETUP TOMBOL & TOOLBAR
    // =========================================================================

    /** Mendaftarkan listener tombol aksi utama (back, menu, undo, redo, save). */
    private void setupButtons() {
        findViewById(R.id.btn_back).setOnClickListener(v -> saveAndExit());
        btnShare.setVisibility(View.GONE);
        btnDelete.setVisibility(View.GONE);
        findViewById(R.id.btn_menu).setOnClickListener(this::showMenuPopup);
        btnUndo.setOnClickListener(v -> undo());
        btnRedo.setOnClickListener(v -> redo());
        btnSaveManual.setOnClickListener(v -> manualSave());
    }

    /** Mendaftarkan listener tombol formatting pada keyboard toolbar. */
    private void setupKeyboardToolbar() {
        btnBold.setOnClickListener(v      -> { pushUndoSnapshot(); toggleStyle(Typeface.BOLD); });
        btnItalic.setOnClickListener(v    -> { pushUndoSnapshot(); toggleStyle(Typeface.ITALIC); });
        btnUnderline.setOnClickListener(v -> { pushUndoSnapshot(); toggleSpan(UnderlineSpan.class); });
        btnStrike.setOnClickListener(v    -> { pushUndoSnapshot(); toggleSpan(StrikethroughSpan.class); });
        btnHighlight.setOnClickListener(v -> { pushUndoSnapshot(); openHighlightColorPicker(); });
        btnTextColor.setOnClickListener(v -> { pushUndoSnapshot(); openTextColorPicker(); });
        btnAlignLeft.setOnClickListener(v   -> { pushUndoSnapshot(); setAlignment(Layout.Alignment.ALIGN_NORMAL); });
        btnAlignCenter.setOnClickListener(v -> { pushUndoSnapshot(); setAlignment(Layout.Alignment.ALIGN_CENTER); });
        btnAlignRight.setOnClickListener(v  -> { pushUndoSnapshot(); setAlignment(Layout.Alignment.ALIGN_OPPOSITE); });
        btnBullet.setOnClickListener(v    -> { pushUndoSnapshot(); toggleBullet(); });
        btnSizeUp.setOnClickListener(v    -> { pushUndoSnapshot(); changeTextSize(true); });
        btnSizeDown.setOnClickListener(v  -> { pushUndoSnapshot(); changeTextSize(false); });
    }

    // =========================================================================
    // KEYBOARD VISIBILITY
    // =========================================================================

    /** Mendeteksi visibilitas keyboard dan menampilkan/menyembunyikan toolbar sesuai. */
    private void detectKeyboardVisibility() {
        final View contentView = findViewById(android.R.id.content);
        contentView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            boolean isKeyboardVisible =
                    (contentView.getRootView().getHeight() - contentView.getHeight()) > dpToPx();
            if (isKeyboardVisible) {
                etContent.post(() -> { etContent.requestFocus(); etContent.setCursorVisible(true); });
                layoutDefaultActions.setVisibility(View.GONE);
                layoutInputActions.setVisibility(View.VISIBLE);
            } else {
                layoutDefaultActions.setVisibility(View.VISIBLE);
                layoutInputActions.setVisibility(View.GONE);
            }
            refreshUndoRedoButtons();
        });
    }

    /** Konversi dp ke pixel. */
    private int dpToPx() {
        return Math.round(200 * getResources().getDisplayMetrics().density);
    }

    /** Mendaftarkan handler tombol back sistem. */
    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { saveAndExit(); }
        });
    }

    // =========================================================================
    // METADATA & SIMPAN
    // =========================================================================

    /** Memperbarui teks metadata (tanggal & jumlah karakter). */
    private void updateMetadata() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy, HH:mm", Locale.getDefault());
        long ts = currentNote.getTimestamp() == 0 ? System.currentTimeMillis() : currentNote.getTimestamp();
        tvMetadata.setText(getString(R.string.metadata_format, sdf.format(new Date(ts)), etContent.length()));
    }

    /** Menyimpan catatan secara manual dan menutup keyboard. */
    private void manualSave() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etContent.getWindowToken(), 0);
        layoutInputActions.setVisibility(View.GONE);
        layoutDefaultActions.setVisibility(View.VISIBLE);

        String title       = etTitle.getText().toString().trim();
        String contentHtml = spannableToHtml(etContent.getText());
        String plainText   = Html.fromHtml(contentHtml, Html.FROM_HTML_MODE_LEGACY).toString().trim();

        // Hapus catatan jika judul dan konten kosong
        if (currentNote != null && title.isEmpty() && plainText.isEmpty()) {
            getSharedPreferences("notes_" + viewModel.getUserId(), MODE_PRIVATE)
                    .edit().remove(currentNote.getId()).apply();
            viewModel.deleteNote(this, currentNote.getId());
            Toast.makeText(this, getString(R.string.toast_note_deleted), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!title.equals(originalTitle) || !contentHtml.equals(originalContent)) {
            assert currentNote != null;
            currentNote.setTitle(title);
            currentNote.setContent(contentHtml);
            currentNote.setTimestamp(System.currentTimeMillis());
            getSharedPreferences("notes_" + viewModel.getUserId(), MODE_PRIVATE)
                    .edit().putString(currentNote.getId(), gson.toJson(currentNote)).apply();
            viewModel.saveNote(this, currentNote);
            originalTitle   = title;
            originalContent = contentHtml;
            Toast.makeText(this, getString(R.string.toast_note_saved), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.toast_no_changes), Toast.LENGTH_SHORT).show();
        }
    }

    /** Menyimpan catatan lalu menutup Activity. */
    private void saveAndExit() {
        undoDebounceHandler.removeCallbacks(undoPushRunnable);

        String title       = etTitle.getText().toString().trim();
        String contentHtml = spannableToHtml(etContent.getText());
        String plainText   = Html.fromHtml(contentHtml, Html.FROM_HTML_MODE_LEGACY).toString().trim();

        // Hapus catatan yang sudah ada jika kosong
        if (originalContent != null && currentNote != null && title.isEmpty() && plainText.isEmpty()) {
            getSharedPreferences("notes_" + viewModel.getUserId(), MODE_PRIVATE)
                    .edit().remove(currentNote.getId()).apply();
            viewModel.deleteNote(this, currentNote.getId());
            setResult(RESULT_OK);
            finish();
            return;
        }

        // Catatan baru kosong — batalkan
        if (title.isEmpty() && plainText.isEmpty()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // Simpan jika ada perubahan, selesaikan tanpa simpan jika tidak ada
        if (!title.equals(originalTitle) || !contentHtml.equals(originalContent)) {
            assert currentNote != null;
            currentNote.setTitle(title);
            currentNote.setContent(contentHtml);
            currentNote.setTimestamp(System.currentTimeMillis());
            getSharedPreferences("notes_" + viewModel.getUserId(), MODE_PRIVATE)
                    .edit().putString(currentNote.getId(), gson.toJson(currentNote)).apply();
            viewModel.saveNote(this, currentNote);
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    // =========================================================================
    // SERIALISASI HTML KUSTOM
    // =========================================================================

    /** Mengembalikan atribut style CSS sesuai nilai alignment. */
    private String buildAlignStyle(Layout.Alignment a) {
        if (a == Layout.Alignment.ALIGN_CENTER)   return " style=\"text-align:center;\"";
        if (a == Layout.Alignment.ALIGN_OPPOSITE) return " style=\"text-align:right;\"";
        return " style=\"text-align:left;\"";
    }

    /**
     * Mengonversi Spannable ke HTML dengan menyimpan:
     * - Alignment  → atribut style pada tag <p>
     * - Ukuran teks → atribut data-sizes="start:end:sp,..." pada tag <p>
     * - Span lain (bold, italic, warna, dll.) → via Html.toHtml()
     */
    private String spannableToHtml(Spannable text) {
        StringBuilder sb = new StringBuilder();
        // Trim trailing newlines dari teks agar tidak menghasilkan baris kosong di akhir
        String rawText = text.toString();
        while (rawText.endsWith("\n")) rawText = rawText.substring(0, rawText.length() - 1);

        String[] lines = rawText.split("\n", -1);
        int offset = 0;

        for (String line : lines) {
            int lineEnd = offset + line.length();

            // Alignment
            AlignmentSpan.Standard[] alignSpans = text.getSpans(offset, lineEnd, AlignmentSpan.Standard.class);
            String alignAttr = alignSpans.length > 0 ? buildAlignStyle(alignSpans[0].getAlignment()) : "";

            // Ukuran teks → data-sizes
            AbsoluteSizeSpan[] sizeSpans = text.getSpans(offset, lineEnd, AbsoluteSizeSpan.class);
            String sizeAttr = "";
            if (sizeSpans.length > 0) {
                StringBuilder sizes = new StringBuilder();
                for (AbsoluteSizeSpan ss : sizeSpans) {
                    int sS = Math.max(0, text.getSpanStart(ss) - offset);
                    int sE = Math.min(line.length(), text.getSpanEnd(ss) - offset);
                    if (sS >= sE) continue;
                    float sp = ss.getDip() ? ss.getSize()
                            : ss.getSize() / getResources().getDisplayMetrics().scaledDensity;
                    if (sizes.length() > 0) sizes.append(",");
                    sizes.append(sS).append(":").append(sE).append(":").append((int) sp);
                }
                if (sizes.length() > 0) sizeAttr = " data-sizes=\"" + sizes + "\"";
            }

            // Span inline lain via Html.toHtml()
            SpannableStringBuilder lineSpan = new SpannableStringBuilder(line);
            for (Object span : text.getSpans(offset, lineEnd, Object.class)) {
                if (span instanceof AlignmentSpan || span instanceof AbsoluteSizeSpan) continue;
                int sS = Math.max(0, text.getSpanStart(span) - offset);
                int sE = Math.min(line.length(), text.getSpanEnd(span) - offset);
                if (sS >= sE) continue;
                try { lineSpan.setSpan(span, sS, sE, text.getSpanFlags(span)); } catch (Exception ignored) {}
            }
            String lineHtml = Html.toHtml(lineSpan, Html.FROM_HTML_MODE_COMPACT)
                    .replaceAll("(?i)</?p[^>]*>", "")
                    .replaceAll("(?i)<br\\s*/?>", "")
                    .trim();

            // Rakit tag <p>
            sb.append("<p").append(alignAttr).append(sizeAttr).append(">")
                    .append(lineHtml)
                    .append("</p>");

            offset += line.length() + 1;
        }
        return sb.toString();
    }

    /**
     * Mengonversi HTML ke SpannableStringBuilder dengan memulihkan:
     * - Alignment dari style text-align pada tag <p>
     * - Ukuran teks dari atribut data-sizes
     * - Span lain (bold, italic, warna, dll.) via Html.fromHtml()
     */
    private CharSequence htmlToSpannable(String html) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        String[] paragraphs = html.split("(?i)</p>", -1);

        for (String paragraph : paragraphs) {
            String para = paragraph.trim();
            if (para.isEmpty()) continue;

            // Ekstrak alignment dari CSS
            Layout.Alignment alignment = null;
            java.util.regex.Matcher mAlign = java.util.regex.Pattern
                    .compile("(?i)text-align\\s*:\\s*(\\w+)").matcher(para);
            if (mAlign.find()) {
                String a = Objects.requireNonNull(mAlign.group(1)).toLowerCase(Locale.ROOT);
                if ("center".equals(a)) alignment = Layout.Alignment.ALIGN_CENTER;
                else if ("right".equals(a)) alignment = Layout.Alignment.ALIGN_OPPOSITE;
                else alignment = Layout.Alignment.ALIGN_NORMAL;
            }

            // Ekstrak ukuran teks dari data-sizes
            List<int[]> sizeRanges = new ArrayList<>();
            java.util.regex.Matcher mSize = java.util.regex.Pattern
                    .compile("data-sizes=\"([^\"]*)\"").matcher(para);
            if (mSize.find()) {
                for (String token : Objects.requireNonNull(mSize.group(1)).split(",")) {
                    String[] parts = token.split(":");
                    if (parts.length == 3) {
                        try {
                            sizeRanges.add(new int[]{
                                    Integer.parseInt(parts[0]),
                                    Integer.parseInt(parts[1]),
                                    Integer.parseInt(parts[2])
                            });
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            // Hapus tag <p ...>, sisakan inner HTML
            String innerHtml = para.replaceAll("(?i)<p[^>]*>", "").trim();
            if (innerHtml.isEmpty()) continue; // skip paragraf kosong sepenuhnya

            // Parse inner HTML (bold, italic, warna, dll.)
            SpannableStringBuilder parsed = new SpannableStringBuilder(
                    Html.fromHtml(innerHtml, Html.FROM_HTML_MODE_COMPACT));
            // Hapus newline trailing yang ditambahkan Html.fromHtml
            while (parsed.length() > 0 && parsed.charAt(parsed.length() - 1) == '\n')
                parsed.delete(parsed.length() - 1, parsed.length());
            if (parsed.length() == 0) continue; // skip jika setelah trim hasilnya kosong

            // Tambahkan pemisah newline antar paragraf (bukan setelah paragraf terakhir)
            if (result.length() > 0) result.append("\n");

            int lineStart = result.length();
            result.append(parsed);
            int lineEnd = result.length();

            // Terapkan alignment
            if (alignment != null && lineStart < lineEnd)
                result.setSpan(new AlignmentSpan.Standard(alignment),
                        lineStart, lineEnd, Spannable.SPAN_INCLUSIVE_INCLUSIVE);

            // Terapkan ukuran teks
            for (int[] range : sizeRanges) {
                int sS = Math.max(lineStart, Math.min(lineEnd, lineStart + range[0]));
                int sE = Math.max(lineStart, Math.min(lineEnd, lineStart + range[1]));
                if (sS < sE)
                    result.setSpan(new AbsoluteSizeSpan(range[2], true),
                            sS, sE, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return result;
    }

    // =========================================================================
    // SHARE SEBAGAI GAMBAR
    // =========================================================================

    /** Membuat Bitmap dari View yang diberikan. */
    private Bitmap createBitmapFromView(View view) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(view.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    /** Menyimpan Bitmap ke cache app dan mengembalikan URI-nya via FileProvider. */
    private Uri saveBitmapToCache(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "shared_notes");
            if (!cachePath.exists() && !cachePath.mkdirs()) {
                Log.e("ShareNote", "Gagal membuat direktori cache");
                return null;
            }
            File file = new File(cachePath, "note_share_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }
            return FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
        } catch (Exception e) {
            Log.e("ShareNote", "Error menyimpan bitmap: " + e.getMessage(), e);
            return null;
        }
    }

    /** Merender catatan ke gambar PNG lalu membagikannya via intent. */
    private void shareNoteAsImage() {
        LinearLayout container = findViewById(R.id.share_container);
        TextView sTitle   = findViewById(R.id.share_title);
        TextView sContent = findViewById(R.id.share_content);
        TextView sDate    = findViewById(R.id.share_date);

        sTitle.setText(etTitle.getText().toString());
        sContent.setText(Html.fromHtml(currentNote.getContent(), Html.FROM_HTML_MODE_COMPACT));
        sDate.setText(new SimpleDateFormat("MMM dd, yyyy, HH:mm", Locale.getDefault())
                .format(new Date(currentNote.getTimestamp())));

        int bgColor   = ContextCompat.getColor(this, R.color.md_theme_light_onPrimary);
        int textColor = ContextCompat.getColor(this, R.color.md_theme_light_onBackground);
        container.setPadding(48, 48, 48, 48);
        container.setBackgroundColor(bgColor);
        sTitle.setTextColor(textColor);
        sContent.setTextColor(textColor);
        sDate.setTextColor(textColor);
        container.setVisibility(View.VISIBLE);

        int targetWidth = container.getRootView().getWidth();
        container.measure(
                View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        container.layout(0, 0, container.getMeasuredWidth(), container.getMeasuredHeight());

        Bitmap bitmap = createBitmapFromView(container);
        container.setVisibility(View.GONE);

        Uri uri = saveBitmapToCache(bitmap);
        if (uri == null) {
            Toast.makeText(this, getString(R.string.toast_image_failed), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)));
    }

    // =========================================================================
    // MENU POPUP, REMINDER, HAPUS
    // =========================================================================

    /** Menampilkan popup menu (reminder, share, delete) di bawah tombol anchor. */
    private void showMenuPopup(View anchor) {
        ViewGroup root = (ViewGroup) anchor.getRootView();
        View popupView = getLayoutInflater().inflate(R.layout.layout_menu_note, root, false);

        PopupWindow popup = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(10f);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        popupView.findViewById(R.id.action_reminder).setOnClickListener(v -> { setReminder(); popup.dismiss(); });
        popupView.findViewById(R.id.action_share).setOnClickListener(v    -> { shareNoteAsImage(); popup.dismiss(); });
        popupView.findViewById(R.id.action_delete).setOnClickListener(v   -> { confirmDelete(); popup.dismiss(); });
        popup.showAsDropDown(anchor, 0, 0);
    }

    /** Membuka DatePicker lalu TimePicker untuk mengatur waktu pengingat. */
    private void setReminder() {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> {
            calendar.set(y, m, d);
            new TimePickerDialog(this, (v1, h, min) -> {
                calendar.set(Calendar.HOUR_OF_DAY, h);
                calendar.set(Calendar.MINUTE, min);
                calendar.set(Calendar.SECOND, 0);
                currentNote.setTimestamp(calendar.getTimeInMillis());
                viewModel.saveNote(this, currentNote);
                scheduleNotification(calendar.getTimeInMillis());
                Toast.makeText(this, getString(R.string.toast_reminder_set), Toast.LENGTH_SHORT).show();
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    /** Menjadwalkan alarm notifikasi via AlarmManager pada waktu yang ditentukan. */
    private void scheduleNotification(long triggerTime) {
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("title", etTitle.getText().toString());
        intent.putExtra("note_id", currentNote.getId());
        intent.putExtra("user_id", viewModel.getUserId());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this,
                currentNote.getId().hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, getString(R.string.toast_alarm_permission_prompt), Toast.LENGTH_LONG).show();
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                return;
            }
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } catch (SecurityException e) {
            Log.e(TAG, "Izin exact alarm ditolak", e);
            Toast.makeText(this, getString(R.string.toast_reminder_failed), Toast.LENGTH_SHORT).show();
        }
    }

    /** Menampilkan dialog konfirmasi sebelum menghapus catatan. */
    private void confirmDelete() {
        DialogUtils.showConfirmDialog(
                this,
                getString(R.string.dialog_delete_note_title),
                getString(R.string.dialog_delete_note_msg),
                getString(R.string.action_delete),
                getString(R.string.action_cancel),
                () -> {
                    getSharedPreferences("notes_" + viewModel.getUserId(), MODE_PRIVATE)
                            .edit().remove(currentNote.getId()).apply();
                    viewModel.deleteNote(this, currentNote.getId());
                    setResult(RESULT_OK);
                    finish();
                },
                null);
    }

    // =========================================================================
    // SPAN & STYLING TEKS
    // =========================================================================

    /** Mengaktifkan/menonaktifkan style bold atau italic pada teks yang dipilih. */
    private void toggleStyle(int style) {
        int start = etContent.getSelectionStart();
        int end   = etContent.getSelectionEnd();
        if (start == end) return;

        Spannable str = etContent.getText();
        boolean exists = false;
        for (StyleSpan span : str.getSpans(start, end, StyleSpan.class)) {
            if (span.getStyle() == style) { str.removeSpan(span); exists = true; }
        }
        if (!exists) str.setSpan(new StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        updateToolbarState();
    }

    /** Mengaktifkan/menonaktifkan span karakter (underline atau strikethrough). */
    private void toggleSpan(Class<? extends CharacterStyle> spanClass) {
        int start = etContent.getSelectionStart();
        int end   = etContent.getSelectionEnd();
        if (start == end) return;

        Spannable str = etContent.getText();
        Object[] spans = str.getSpans(start, end, spanClass);
        if (spans.length > 0) {
            for (Object span : spans) str.removeSpan(span);
        } else if (spanClass.equals(StrikethroughSpan.class)) {
            str.setSpan(new StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (spanClass.equals(UnderlineSpan.class)) {
            str.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        updateToolbarState();
    }

    /** Membuka color picker untuk warna foreground teks. */
    private void openTextColorPicker() {
        openColorPicker(currentTextColor, this::setTextColor, color -> currentTextColor = color);
    }

    /** Membuka color picker untuk warna highlight (background). */
    private void openHighlightColorPicker() {
        openColorPicker(currentHighlightColor, this::toggleBackgroundColor, color -> currentHighlightColor = color);
    }

    /** Menampilkan dialog AmbilWarna dan memanggil callback saat warna dipilih. */
    private void openColorPicker(int initialColor, ColorCallback onOk, ColorCallback onSave) {
        new AmbilWarnaDialog(this, initialColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override public void onOk(AmbilWarnaDialog dialog, int color) { onOk.onColorSelected(color); onSave.onColorSelected(color); }
            @Override public void onCancel(AmbilWarnaDialog dialog) {}
        }).show();
    }

    /** Callback untuk menerima warna yang dipilih dari color picker. */
    private interface ColorCallback { void onColorSelected(int color); }

    /** Menerapkan warna teks (foreground) pada teks yang dipilih. */
    private void setTextColor(int color) {
        int start = etContent.getSelectionStart();
        int end   = etContent.getSelectionEnd();
        if (start == end) return;
        Spannable str = etContent.getText();
        for (ForegroundColorSpan s : str.getSpans(start, end, ForegroundColorSpan.class)) str.removeSpan(s);
        str.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        updateToolbarState();
    }

    /** Mengaktifkan/menonaktifkan warna latar belakang (highlight) pada seleksi. */
    private void toggleBackgroundColor(int color) {
        int start = etContent.getSelectionStart();
        int end   = etContent.getSelectionEnd();
        if (start == end) return;
        Spannable str = etContent.getText();
        BackgroundColorSpan[] spans = str.getSpans(start, end, BackgroundColorSpan.class);
        if (spans.length > 0) {
            for (BackgroundColorSpan s : spans) str.removeSpan(s);
        } else {
            str.setSpan(new BackgroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        updateToolbarState();
    }

    /** Menerapkan alignment pada semua baris yang tercakup dalam seleksi. */
    private void setAlignment(Layout.Alignment alignment) {
        int start = etContent.getSelectionStart();
        int end   = etContent.getSelectionEnd();
        Editable editable = etContent.getText();
        String text = editable.toString();

        int pos = text.lastIndexOf('\n', start - 1) + 1;
        while (pos <= end && pos <= editable.length()) {
            int lineEnd = text.indexOf('\n', pos);
            if (lineEnd == -1) lineEnd = editable.length();
            for (AlignmentSpan.Standard s : editable.getSpans(pos, lineEnd, AlignmentSpan.Standard.class))
                editable.removeSpan(s);
            editable.setSpan(new AlignmentSpan.Standard(alignment),
                    pos, lineEnd, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            if (lineEnd >= editable.length()) break;
            pos = lineEnd + 1;
        }
        updateToolbarState();
    }

    /** Mengaktifkan/menonaktifkan mode bullet pada baris saat ini. */
    private void toggleBullet() {
        int start = etContent.getSelectionStart();
        Editable editable = etContent.getText();
        int lineStart = editable.toString().lastIndexOf('\n', start - 1) + 1;
        if (bulletMode && editable.toString().startsWith("• ", lineStart)) {
            editable.delete(lineStart, lineStart + 2);
            bulletMode = false;
        } else {
            editable.insert(lineStart, "• ");
            bulletMode = true;
        }
        updateToolbarState();
    }

    /**
     * Mengubah ukuran teks seleksi sebesar ±SIZE_STEP_SP.
     * Span yang tumpang tindih di luar seleksi dipertahankan dengan ukuran lama.
     */
    private void changeTextSize(boolean sizeUp) {
        int start = etContent.getSelectionStart();
        int end   = etContent.getSelectionEnd();
        if (start == end) {
            Toast.makeText(this, "Pilih teks terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        Spannable str = etContent.getText();
        AbsoluteSizeSpan[] existing = str.getSpans(start, end, AbsoluteSizeSpan.class);

        // Tentukan ukuran SP saat ini dari span pertama yang ditemukan
        float currentSp = BASE_TEXT_SIZE_SP;
        if (existing.length > 0) {
            AbsoluteSizeSpan first = existing[0];
            currentSp = first.getDip() ? first.getSize()
                    : first.getSize() / getResources().getDisplayMetrics().scaledDensity;
        }

        float newSp = Math.max(MIN_TEXT_SIZE_SP,
                Math.min(MAX_TEXT_SIZE_SP, sizeUp ? currentSp + SIZE_STEP_SP : currentSp - SIZE_STEP_SP));

        // Hapus span yang ada; pertahankan area di luar seleksi dengan ukuran lama
        for (AbsoluteSizeSpan s : existing) {
            int sStart = str.getSpanStart(s);
            int sEnd   = str.getSpanEnd(s);
            str.removeSpan(s);
            if (sStart < start)
                str.setSpan(new AbsoluteSizeSpan((int) currentSp, true),
                        sStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (sEnd > end)
                str.setSpan(new AbsoluteSizeSpan((int) currentSp, true),
                        end, sEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Terapkan ukuran baru pada rentang seleksi
        str.setSpan(new AbsoluteSizeSpan((int) newSp, true),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        updateToolbarState();
    }

    /**
     * Memperbarui tampilan (alpha/selected) tombol toolbar sesuai formatting aktif
     * pada posisi kursor atau seleksi saat ini.
     */
    private void updateToolbarState() {
        if (etContent == null || etContent.getText() == null) return;
        int start = etContent.getSelectionStart();
        int end   = etContent.getSelectionEnd();
        if (start < 0) return;

        Spannable str  = etContent.getText();
        int queryStart = start == end ? Math.max(0, start - 1) : start;
        int queryEnd   = Math.min(start == end ? Math.max(1, start) : end, str.length());
        queryStart     = Math.min(queryStart, queryEnd);

        // Bold
        boolean isBold = false;
        for (StyleSpan s : str.getSpans(queryStart, queryEnd, StyleSpan.class))
            if (s.getStyle() == Typeface.BOLD) { isBold = true; break; }
        btnBold.setAlpha(isBold ? 1f : 0.45f);
        btnBold.setSelected(isBold);

        // Italic
        boolean isItalic = false;
        for (StyleSpan s : str.getSpans(queryStart, queryEnd, StyleSpan.class))
            if (s.getStyle() == Typeface.ITALIC) { isItalic = true; break; }
        btnItalic.setAlpha(isItalic ? 1f : 0.45f);
        btnItalic.setSelected(isItalic);

        // Underline
        boolean isUnderline = str.getSpans(queryStart, queryEnd, UnderlineSpan.class).length > 0;
        btnUnderline.setAlpha(isUnderline ? 1f : 0.45f);
        btnUnderline.setSelected(isUnderline);

        // Strikethrough
        boolean isStrike = str.getSpans(queryStart, queryEnd, StrikethroughSpan.class).length > 0;
        btnStrike.setAlpha(isStrike ? 1f : 0.45f);
        btnStrike.setSelected(isStrike);

        // Highlight
        boolean isHighlight = str.getSpans(queryStart, queryEnd, BackgroundColorSpan.class).length > 0;
        btnHighlight.setAlpha(isHighlight ? 1f : 0.45f);
        btnHighlight.setSelected(isHighlight);

        // Warna teks — tint ikon dengan warna aktif
        ForegroundColorSpan[] colorSpans = str.getSpans(queryStart, queryEnd, ForegroundColorSpan.class);
        if (colorSpans.length > 0) {
            btnTextColor.setColorFilter(colorSpans[0].getForegroundColor());
            btnTextColor.setAlpha(1f);
        } else {
            btnTextColor.clearColorFilter();
            btnTextColor.setAlpha(0.45f);
        }

        // Bullet
        btnBullet.setAlpha(bulletMode ? 1f : 0.45f);
        btnBullet.setSelected(bulletMode);

        // Alignment
        AlignmentSpan.Standard[] alignSpans = str.getSpans(queryStart, queryEnd, AlignmentSpan.Standard.class);
        Layout.Alignment currentAlign = alignSpans.length > 0
                ? alignSpans[0].getAlignment() : Layout.Alignment.ALIGN_NORMAL;
        btnAlignLeft.setAlpha(currentAlign == Layout.Alignment.ALIGN_NORMAL   ? 1f : 0.45f);
        btnAlignCenter.setAlpha(currentAlign == Layout.Alignment.ALIGN_CENTER  ? 1f : 0.45f);
        btnAlignRight.setAlpha(currentAlign == Layout.Alignment.ALIGN_OPPOSITE ? 1f : 0.45f);

        // Size up/down — aktif hanya jika ada seleksi
        float sizeAlpha = start != end ? 1f : 0.45f;
        btnSizeUp.setAlpha(sizeAlpha);
        btnSizeDown.setAlpha(sizeAlpha);
    }
}