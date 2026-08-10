package com.example.chatapp.utilities;

import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * A custom TextView that ensures the view width is exactly as wide as the longest line
 * of text, preventing "ghost" gaps in multi-line bubbles.
 */
public class TightTextView extends AppCompatTextView {

    public TightTextView(Context context) {
        super(context);
    }

    public TightTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TightTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        Layout layout = getLayout();
        if (layout != null) {
            int lineCount = layout.getLineCount();
            if (lineCount > 1) {
                float maxLineWidth = 0;
                for (int i = 0; i < lineCount; i++) {
                    maxLineWidth = Math.max(maxLineWidth, layout.getLineWidth(i));
                }
                
                // Add horizontal padding to the measured width
                int width = (int) Math.ceil(maxLineWidth) + getPaddingLeft() + getPaddingRight();
                
                // Ensure we don't exceed the original measured width
                if (width < getMeasuredWidth()) {
                    super.onMeasure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY)
                    );
                }
            }
        }
    }
}
