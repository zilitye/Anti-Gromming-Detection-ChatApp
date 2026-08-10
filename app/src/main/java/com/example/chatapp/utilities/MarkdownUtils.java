package com.example.chatapp.utilities;

import android.content.Context;
import android.graphics.Color;
import androidx.annotation.NonNull;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.MarkwonTheme;

public class MarkdownUtils {

    public static CharSequence formatMarkdown(Context context, String text) {
        if (text == null) return "";
        
        // Trim leading/trailing whitespace to prevent accidental 4-space markdown code blocks
        String trimmedText = text.trim();
        if (trimmedText.isEmpty()) return "";

        final Markwon markwon = Markwon.builder(context)
                .usePlugin(new io.noties.markwon.AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        // Forcefully disable all backgrounds and margins for code blocks
                        // This ensures they blend perfectly with the chat bubble.
                        builder.codeBlockBackgroundColor(Color.TRANSPARENT);
                        builder.codeBackgroundColor(Color.TRANSPARENT);
                        builder.codeBlockMargin(0);
                    }
                })
                .build();

        CharSequence markdown = markwon.toMarkdown(trimmedText);
        
        // Trim trailing newlines which can affect layout measurement and cause gaps
        int len = markdown.length();
        while (len > 0 && Character.isWhitespace(markdown.charAt(len - 1))) {
            len--;
        }
        return markdown.subSequence(0, len);
    }
}
