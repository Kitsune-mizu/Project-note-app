package com.android.alpha.ui.geminichat;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

/**
 * Mengubah teks markdown sederhana dari Gemini menjadi SpannableString
 * yang tampil rapi di TextView dan plain text untuk disimpan ke catatan.

 * Didukung:
 *  - **bold** → bold span
 *  - ## Heading → bold + sedikit lebih besar
 *  - * / - / • item  → BulletSpan
 *  - 1. 2. 3.        → angka rapi dengan indentasi
 *  - Baris kosong    → spacing antar paragraf
 */
public class MarkdownFormatter {

    private static final int BULLET_GAP = 16;

    /** Untuk ditampilkan di bubble chat (Spannable dengan format visual). */
    public static SpannableStringBuilder toSpannable(String raw) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String[] lines = raw.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append("\n");
                }
                continue;
            }

            int lineStart = sb.length();

            // Heading
            if (trimmed.startsWith("## ") || trimmed.startsWith("# ")) {
                String text = trimmed.replaceFirst("^##? ", "").trim();
                text = stripInlineBold(text);

                sb.append(text);
                int end = sb.length();

                sb.setSpan(new StyleSpan(Typeface.BOLD), lineStart, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new RelativeSizeSpan(1.1f), lineStart, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                sb.append("\n");

                // Bullet
            } else if (trimmed.startsWith("* ")
                    || trimmed.startsWith("- ")
                    || trimmed.startsWith("• ")) {

                String text = trimmed.substring(2).trim();
                int bulletStart = sb.length();

                appendWithInlineBold(sb, text);

                int end = sb.length();
                sb.setSpan(new BulletSpan(BULLET_GAP), bulletStart, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                sb.append("\n");

                // Numbered list
            } else if (trimmed.matches("^\\d+\\.\\s+.*")) {

                int dotIdx = trimmed.indexOf('.');
                String number = trimmed.substring(0, dotIdx + 1);
                String text = stripInlineBold(trimmed.substring(dotIdx + 1).trim());

                sb.append(number).append("  ").append(text);
                sb.append("\n");

                // Paragraf biasa
            } else {
                appendWithInlineBold(sb, trimmed);
                sb.append("\n");
            }
        }

        // Hapus trailing newline berlebih
        while (sb.length() > 1
                && sb.charAt(sb.length() - 1) == '\n'
                && sb.charAt(sb.length() - 2) == '\n') {
            sb.delete(sb.length() - 1, sb.length());
        }

        return sb;
    }

    /**
     * Untuk disimpan ke catatan: plain text rapi tanpa markdown syntax,
     * bullet diganti •, heading tetap tapi tanpa ##.
     */
    public static String toPlainNote(String raw) {
        StringBuilder sb = new StringBuilder();
        String[] lines = raw.split("\n");
        boolean lastWasEmpty = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                if (!lastWasEmpty) sb.append("\n");
                lastWasEmpty = true;
                continue;
            }
            lastWasEmpty = false;

            if (trimmed.startsWith("## ") || trimmed.startsWith("# ")) {
                String text = trimmed.replaceFirst("^##? ", "").trim();
                sb.append(stripMarkdownBold(text)).append("\n");
            } else if (trimmed.startsWith("* ") || trimmed.startsWith("- ")
                    || trimmed.startsWith("• ")) {
                sb.append("• ").append(stripMarkdownBold(trimmed.substring(2).trim())).append("\n");
            } else if (trimmed.matches("^\\d+\\.\\s+.*")) {
                sb.append(stripMarkdownBold(trimmed)).append("\n");
            } else {
                sb.append(stripMarkdownBold(trimmed)).append("\n");
            }
        }

        return sb.toString().trim();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Proses inline **bold** — tambahkan teks ke sb dengan StyleSpan. */
    private static void appendWithInlineBold(SpannableStringBuilder sb, String text) {
        int start = 0;
        while (true) {
            int open  = text.indexOf("**", start);
            if (open < 0) { sb.append(text.substring(start)); break; }
            int close = text.indexOf("**", open + 2);
            if (close < 0) { sb.append(text.substring(start)); break; }

            sb.append(text, start, open);
            int boldStart = sb.length();
            sb.append(text, open + 2, close);
            sb.setSpan(new StyleSpan(Typeface.BOLD), boldStart, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = close + 2;
        }
    }

    private static String stripInlineBold(String text) {
        return text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
    }

    private static String stripMarkdownBold(String text) {
        return text.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*(.+?)\\*", "$1");
    }
}