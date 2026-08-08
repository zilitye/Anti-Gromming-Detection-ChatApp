# Implementation Plan - Chat UI Fixes (Input, Back Button & Scrolling)

The goal is to update the chat input field, the back button, and fix the message scrolling issue where new messages are hidden under the keyboard.

## Proposed Changes

### UI Components

#### [ic_back.xml](file:///C:/Users/User/Documents/GitHub/ChatApp/app/src/main/res/drawable/ic_back.xml)

- Update path to a standard back arrow with a tail.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#000000">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z" />
</vector>
```

#### [ic_arrow_up.xml](file:///C:/Users/User/Documents/GitHub/ChatApp/app/src/main/res/drawable/ic_arrow_up.xml) [NEW]

- New vector drawable for the up-arrow icon. (Already created)

#### [background_chat_input_refined.xml](file:///C:/Users/User/Documents/GitHub/ChatApp/app/src/main/res/drawable/background_chat_input_refined.xml)

- Update to a `selector` to show the blue border (`@color/macos_accent`) only when focused.

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape>
            <solid android:color="#F2F2F7" />
            <stroke android:width="1dp" android:color="@color/macos_accent" />
            <corners android:radius="@dimen/_20sdp" />
        </shape>
    </item>
    <item>
        <shape>
            <solid android:color="#F2F2F7" />
            <corners android:radius="@dimen/_20sdp" />
        </shape>
    </item>
</selector>
```

#### [activity_chat.xml](file:///C:/Users/User/Documents/GitHub/ChatApp/app/src/main/res/layout/activity_chat.xml)

- Restructure the input area: wrap `EditText` and `layoutSend` in a `FrameLayout` or `ConstraintLayout` that uses the `background_chat_input_refined`.
- Set initial visibility of `layoutSend` to `gone`.
- Use `@drawable/ic_arrow_up` for the send icon.

### Business Logic

#### [ChatActivity.java](file:///C:/Users/User/Documents/GitHub/ChatApp/app/src/main/java/com/example/chatapp/activities/ChatActivity.java)

- **Fix Scroll Index**: Update `smoothScrollToPosition` to use `chatMessages.size()` instead of `chatMessages.size() - 1` to account for the header in `ChatAdapter`.
- **Handle Keyboard Resize**: Add an `OnLayoutChangeListener` to `chatRecyclerView` to automatically scroll to the bottom when the keyboard appears (i.e., when the view's bottom coordinate decreases).
- **TextWatcher**: Add a `TextWatcher` to `inputMessage` to toggle `layoutSend` visibility.
- **Focus Listener**: Add a focus listener to `inputMessage` to update the container's activated state.

```java
// Fix scroll index
if (!chatMessages.isEmpty()) {
    binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size());
}

// Handle keyboard resize
binding.chatRecyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
    if (bottom < oldBottom) {
        binding.chatRecyclerView.postDelayed(() -> {
            if (chatMessages.size() > 0) {
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size());
            }
        }, 100);
    }
});
```

## Verification Plan

### Manual Verification
1.  Deploy the app.
2.  Navigate to `ChatActivity`.
3.  Verify:
    - Back button at the top left has a tail (`<-`).
    - Input field has NO blue border by default.
    - Blue border appears when typing/clicking the input.
    - Send button (blue circle, white arrow) is HIDDEN when input is empty.
    - Send button APPEARS when text is typed.
    - Send button is inside the input field.
    - **Scrolling**: When the keyboard appears, the list scrolls to the bottom.
    - **Sending**: When a message is sent, the list scrolls to the bottom, and the new message is FULLY visible above the keyboard.
    - **Header**: The profile header at the top of the list is still present and accessible.
