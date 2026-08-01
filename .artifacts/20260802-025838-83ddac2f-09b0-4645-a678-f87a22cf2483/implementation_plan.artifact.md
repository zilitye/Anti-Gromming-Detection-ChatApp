# Implementation Plan - Change Menu Text Color to Black

The goal is to change the text color of the overflow menu (three-dot menu) in `MainActivity` to black. Currently, the text is almost invisible (white on white background).

## Proposed Changes

### [colors.xml](file:///C:/Users/User/Desktop/Anti-Gromming-Detection-ChatApp/app/src/main/res/values/colors.xml)

- Add `black` color definition.

```diff
+    <color name="black">#000000</color>
```

### [themes.xml](file:///C:/Users/User/Desktop/Anti-Gromming-Detection-ChatApp/app/src/main/res/values/themes.xml)

- Add a new style for the `PopupMenu` to force black text color.

```xml
    <style name="PopupMenuTheme" parent="Theme.AppCompat.Light">
        <item name="android:textColor">@color/black</item>
    </style>
```

### [MainActivity.java](file:///C:/Users/User/Desktop/Anti-Gromming-Detection-ChatApp/app/src/main/java/com/example/chatapp/activities/MainActivity.java)

- Update `showOverflowMenu` to use `ContextThemeWrapper` with the new `PopupMenuTheme`.

```diff
+    import android.view.ContextThemeWrapper;
+    import android.view.Context;

     private void showOverflowMenu(View anchor) {
-        PopupMenu popupMenu = new PopupMenu(this, anchor);
+        Context wrapper = new ContextThemeWrapper(this, R.style.PopupMenuTheme);
+        PopupMenu popupMenu = new PopupMenu(wrapper, anchor);
         popupMenu.getMenu().add(Menu.NONE, 1, 1, R.string.menu_safety_center);
```

---

## Verification Plan

### Automated Tests
- None applicable for UI styling.

### Manual Verification
- Deploy the app.
- Click on the three-dot icon in the top right corner.
- Verify that the menu items ("Safety Center", "AI Assistant Settings", "Sign Out") have black text.
- Take a screenshot as proof.
