package com.example.chatapp.utilities;

import android.content.Context;
import io.noties.markwon.Markwon;

public class MarkdownUtils {

    public static CharSequence formatMarkdown(Context context, String text) {
        if (text == null) return "";
        
        final Markwon markwon = Markwon.create(context);
        CharSequence markdown = markwon.toMarkdown(text);
        
        // Trim trailing newlines which can affect layout measurement and cause gaps
        int len = markdown.length();
        while (len > 0 && Character.isWhitespace(markdown.charAt(len - 1))) {
            len--;
        }
        return markdown.subSequence(0, len);
    }
}
