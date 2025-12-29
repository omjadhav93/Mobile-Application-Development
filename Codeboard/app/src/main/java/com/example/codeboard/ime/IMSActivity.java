package com.example.codeboard.ime;

import android.annotation.SuppressLint;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;

import com.example.codeboard.R;

public class IMSActivity extends InputMethodService {
    private static class KeySpec {
        int keyCode;
        int meta;

        KeySpec(int keyCode, int meta) {
            this.keyCode = keyCode;
            this.meta = meta;
        }
    }

    /* ───────── STATE VARIABLES ───────── */
    private static final int DOUBLE_TAP_TIMEOUT = 250; // ms
    private long lastSpaceTapTime = 0;
    private float spaceDownX;
    private static final int SWIPE_THRESHOLD = 100; // px
    private boolean specialMode = false;

    /* ───── F-KEY NAVIGATION ───── */
    private boolean fHeld = false;
    private boolean navUsed = false;

    private float fDownX, fDownY;
    private float lastEmitX, lastEmitY;

    private static final int NAV_TRIGGER_DISTANCE = 14; // px
    private static final int NAV_REPEAT_DISTANCE  = 22; // px

    /* ───────── MODIFIER STATES ───────── */
    private boolean ctrlToggle = false, ctrlHold = false;
    private boolean shiftToggle = false, shiftHold = false;
    private boolean altToggle = false, altHold = false;
    private boolean capsOn = false;

    /* ───────── TIMING ───────── */
    private long keyDownTime;
    private static final int TAP_THRESHOLD = 200;

    /* ───────── BACKSPACE ───────── */
    private boolean backspaceHeld = false;
    private final Handler handler = new Handler();

    /* ───────── KEY REFERENCES (IMPORTANT) ───────── */
    private View ctrlKey, shiftKey, altKey, capsKey, root;
    private ViewGroup keyContainer, keyboardContainer;

    /* ───────── EFFECTIVE STATES ───────── */
    private boolean ctrlOn()  { return ctrlToggle || ctrlHold; }
    private boolean shiftOn() { return shiftToggle || shiftHold; }
    private boolean altOn()   { return altToggle || altHold; }

    @SuppressLint("InflateParams")
    @Override
    public View onCreateInputView() {
        root = getLayoutInflater().inflate(R.layout.keyboard_root, null);
        keyboardContainer = root.findViewById(R.id.keyboardContainer);

        // 🔑 Initialize default keyboard here
        showMainKeyboard();

        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        if (keyboardContainer == null) return;

        int cls = info.inputType & EditorInfo.TYPE_MASK_CLASS;

        if (cls == EditorInfo.TYPE_CLASS_NUMBER) {
            showNumericKeyboard();
        } else {
            showMainKeyboard();
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        autoResetModifiers();
    }


    /* ───────── TOUCH HANDLER ───────── */
    private void attachTouchListeners(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup) {
                attachTouchListeners((ViewGroup) child);
            } else {
                child.setOnTouchListener(this::onKeyTouch);
            }
        }
    }
    private boolean onKeyTouch(View view, MotionEvent e) {
        Object tagObj = view.getTag();
        if (tagObj == null) return false;

        String tag = tagObj.toString();

        switch (e.getAction()) {

            case MotionEvent.ACTION_DOWN:
                view.setPressed(true); // ⭐ REQUIRED
                keyDownTime = System.currentTimeMillis();
                if ("SPACE".equals(tag)) {
                    spaceDownX = e.getRawX();
                }
                if ("ALPHA".equals(tag) && view instanceof Button) {
                    String txt = ((Button) view).getText().toString();

                    if ("f".equalsIgnoreCase(txt)) {
                        fHeld = true;
                        navUsed = false;

                        fDownX = e.getRawX();
                        fDownY = e.getRawY();
                        lastEmitX = fDownX;
                        lastEmitY = fDownY;

                        return true; // F owns gesture
                    }
                }
                handleKeyDown(tag);
                return true;
            case MotionEvent.ACTION_MOVE:

                if (fHeld) {
                    float dxTotal = e.getRawX() - fDownX;
                    float dyTotal = e.getRawY() - fDownY;

                    float dist = (float) Math.hypot(dxTotal, dyTotal);

                    // Wait for intent
                    if (!navUsed && dist < NAV_TRIGGER_DISTANCE) {
                        return true;
                    }

                    navUsed = true;

                    float dx = e.getRawX() - lastEmitX;
                    float dy = e.getRawY() - lastEmitY;

                    float absDx = Math.abs(dx);
                    float absDy = Math.abs(dy);

                    if (absDx > absDy && absDx > NAV_REPEAT_DISTANCE) {
                        sendArrowWithModifiers(
                                dx > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT
                                        : KeyEvent.KEYCODE_DPAD_LEFT
                        );
                        lastEmitX = e.getRawX();
                        lastEmitY = e.getRawY();
                    }
                    else if (absDy > NAV_REPEAT_DISTANCE) {
                        sendArrowWithModifiers(
                                dy > 0 ? KeyEvent.KEYCODE_DPAD_DOWN
                                        : KeyEvent.KEYCODE_DPAD_UP
                        );
                        lastEmitX = e.getRawX();
                        lastEmitY = e.getRawY();
                    }

                    return true;
                }

                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                view.setPressed(false); // ⭐ REQUIRED
                long duration = System.currentTimeMillis() - keyDownTime;
                if ("SPACE".equals(tag)) {

                    float deltaX = e.getRawX() - spaceDownX;
                    Log.d("MyActivityTag", String.valueOf(deltaX));

                    if (Math.abs(deltaX) > SWIPE_THRESHOLD) {
                        toggleSpecialKeyboard();
                        return true; // consume swipe
                    }
                }
                if (fHeld) {
                    fHeld = false;

                    if (!navUsed) {
                        // No navigation → normal 'f'
                        commit("f");
                    }

                    navUsed = false;
                    return true;
                }
                handleKeyUp(tag, view, duration);
                return true;
        }
        return false;
    }

    /* ───────── Swipe LOGIC ───────── */
    private void toggleSpecialKeyboard() {
        if (specialMode) {
            showAlphaKeyboard();
        } else {
            showSpecialKeyboard();
        }
        specialMode = !specialMode;
    }

    /* ───────── HOLD LOGIC ───────── */
    private void handleKeyDown(String tag) {
        switch (tag) {
            case "CTRL":  ctrlHold = true; break;
            case "SHIFT": shiftHold = true; updateKeyLabels(); break;
            case "ALT":   altHold = true; break;

            case "BACK":
                backspaceHeld = true;
                deleteChar();
                handler.postDelayed(backspaceRunnable, 400);
                break;
        }
    }

    /* ───────── RELEASE / TOGGLE ───────── */
    private void handleKeyUp(String tag, View view, long duration) {
        boolean isTap = duration < TAP_THRESHOLD;

        switch (tag) {

            case "CTRL":
                ctrlHold = false;
                if (isTap) ctrlToggle = !ctrlToggle;
                ctrlKey.setActivated(ctrlOn());
                return;

            case "SHIFT":
                shiftHold = false;
                if (isTap) shiftToggle = !shiftToggle;
                shiftKey.setActivated(shiftOn());
                updateKeyLabels();
                return;

            case "ALT":
                altHold = false;
                if (isTap) altToggle = !altToggle;
                altKey.setActivated(altOn());
                return;

            case "CAPS":
                capsOn = !capsOn;
                capsKey.setActivated(capsOn);
                updateKeyLabels();
                return;
        }

        handleNormalKey(tag, view);
        autoResetModifiers();
    }

    /* ───────── NORMAL KEYS ───────── */
    private void handleNormalKey(String tag, View view) {
        switch (tag) {
            case "ALPHA": handleAlphaKey(view); break;
            case "ENTER":
                if (ctrlOn()) {
                    sendCtrlEnter();
                    return;
                }
                sendKey();
                break;
            case "SPACE":
//                if (navUsed) return;

                long now = System.currentTimeMillis();

                if (now - lastSpaceTapTime < DOUBLE_TAP_TIMEOUT) {
                    lastSpaceTapTime = 0;

                    InputConnection ic = ic();
                    if (ic != null) ic.deleteSurroundingText(1, 0); // remove single space

                    insertTab();

                    autoResetModifiers();
                    return;
                }

                // single tap (delay decision)
                lastSpaceTapTime = now;
                commit(" ");
                autoResetModifiers();
                return;

            case "BACK":
                backspaceHeld = false;
                handler.removeCallbacks(backspaceRunnable);
                return;
        }
    }

    /* ───────── AUTO RESET ───────── */
    private void autoResetModifiers() {
        if (ctrlToggle)  { ctrlToggle = false; ctrlKey.setActivated(false); }
        if (altToggle)   { altToggle  = false; altKey.setActivated(false); }
        if (shiftToggle && !capsOn) {
            shiftToggle = false;
            shiftKey.setActivated(false);
        }
    }

    /* ───────── BACKSPACE REPEAT ───────── */
    private final Runnable backspaceRunnable = () -> {
        if (!backspaceHeld) return;
        deleteChar();
        handler.postDelayed(this.backspaceRunnable, 60);
    };

    /* ───────── ALPHA ───────── */
    private void handleAlphaKey(View view) {
        if (!(view instanceof Button)) return;
        String text = ((Button) view).getText().toString();

        if (ctrlOn()) {
            handleCtrlShortcut(text.toLowerCase());
            ctrlToggle = false;
            ctrlHold = false;
            ctrlKey.setActivated(false);
            return;
        }

        text = (shiftOn() ^ capsOn) ? text.toUpperCase() : text.toLowerCase();
        commit(text);

        if (shiftToggle && !capsOn) {
            shiftToggle = false;
            shiftKey.setActivated(false);
            updateKeyLabels();
        }
    }

    private void updateKeyLabels() {
        if (keyContainer == null) return;

        boolean upper = shiftOn() ^ capsOn;

        updateKeyLabelsRecursive(keyContainer, upper);
    }

    private void updateKeyLabelsRecursive(ViewGroup parent, boolean upper) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);

            if (v instanceof Button) {
                Object tag = v.getTag();
                if (tag != null && "ALPHA".equals(tag.toString())) {
                    Button b = (Button) v;
                    String txt = b.getText().toString();
                    if (txt.length() == 1 && Character.isLetter(txt.charAt(0))) {
                        b.setText(upper
                                ? txt.toUpperCase()
                                : txt.toLowerCase());
                    }
                }
            }
            else if (v instanceof ViewGroup) {
                updateKeyLabelsRecursive((ViewGroup) v, upper);
            }
        }
    }


    /* ───────── SHORTCUTS ───────── */
    private void handleCtrlShortcut(String key) {
        InputConnection ic = ic();
        if (ic == null || key == null || key.isEmpty()) return;

        if ("BACK".equals(key)) {
            deletePreviousWord();
            return;
        }

        switch (key) {

            // ---- Explicitly handled shortcuts ----
            case "a":
                ic.performContextMenuAction(android.R.id.selectAll);
                break;

            case "c":
                ic.performContextMenuAction(android.R.id.copy);
                break;

            case "x":
                ic.performContextMenuAction(android.R.id.cut);
                break;

            case "v":
                ic.performContextMenuAction(android.R.id.paste);
                break;

            case "z":
                ic.performContextMenuAction(android.R.id.undo);
                break;

            case "y":
                ic.performContextMenuAction(android.R.id.redo);
                break;

            // ---- Everything else: try hardware ----
            default:
                if (key.length() == 1) {
                    sendCtrlKeyEvent(key.charAt(0));
                }
                break;
        }
    }

    private void sendCtrlEnter() {
        InputConnection ic = ic();
        if (ic == null) return;

        ic.sendKeyEvent(new KeyEvent(
                0, 0,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ENTER,
                0,
                KeyEvent.META_CTRL_ON
        ));

        ic.sendKeyEvent(new KeyEvent(
                0, 0,
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_ENTER,
                0,
                KeyEvent.META_CTRL_ON
        ));
    }


    private void sendCtrlKeyEvent(char c) {
        KeySpec spec = mapCharToKeySpec(c);
        if (spec == null) return;

        InputConnection ic = ic();
        if (ic == null) return;

        int meta = spec.meta | KeyEvent.META_CTRL_ON;

        ic.sendKeyEvent(new KeyEvent(
                0, 0,
                KeyEvent.ACTION_DOWN,
                spec.keyCode,
                0,
                meta
        ));

        ic.sendKeyEvent(new KeyEvent(
                0, 0,
                KeyEvent.ACTION_UP,
                spec.keyCode,
                0,
                meta
        ));
    }

    private KeySpec mapCharToKeySpec(char c) {

        // letters
        if (c >= 'a' && c <= 'z') {
            return new KeySpec(
                    KeyEvent.KEYCODE_A + (c - 'a'),
                    0
            );
        }

        if (c >= 'A' && c <= 'Z') {
            return new KeySpec(
                    KeyEvent.KEYCODE_A + (c - 'A'),
                    KeyEvent.META_SHIFT_ON
            );
        }

        // digits
        if (c >= '0' && c <= '9') {
            return new KeySpec(
                    KeyEvent.KEYCODE_0 + (c - '0'),
                    0
            );
        }

        switch (c) {

            case ';': return new KeySpec(KeyEvent.KEYCODE_SEMICOLON, 0);
            case ':': return new KeySpec(KeyEvent.KEYCODE_SEMICOLON, KeyEvent.META_SHIFT_ON);

            case '=': return new KeySpec(KeyEvent.KEYCODE_EQUALS, 0);
            case '+': return new KeySpec(KeyEvent.KEYCODE_EQUALS, KeyEvent.META_SHIFT_ON);

            case '-': return new KeySpec(KeyEvent.KEYCODE_MINUS, 0);
            case '_': return new KeySpec(KeyEvent.KEYCODE_MINUS, KeyEvent.META_SHIFT_ON);

            case '[': return new KeySpec(KeyEvent.KEYCODE_LEFT_BRACKET, 0);
            case '{': return new KeySpec(KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.META_SHIFT_ON);

            case ']': return new KeySpec(KeyEvent.KEYCODE_RIGHT_BRACKET, 0);
            case '}': return new KeySpec(KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.META_SHIFT_ON);

            case '\\': return new KeySpec(KeyEvent.KEYCODE_BACKSLASH, 0);
            case '|':  return new KeySpec(KeyEvent.KEYCODE_BACKSLASH, KeyEvent.META_SHIFT_ON);

            case ',': return new KeySpec(KeyEvent.KEYCODE_COMMA, 0);
            case '<': return new KeySpec(KeyEvent.KEYCODE_COMMA, KeyEvent.META_SHIFT_ON);

            case '.': return new KeySpec(KeyEvent.KEYCODE_PERIOD, 0);
            case '>': return new KeySpec(KeyEvent.KEYCODE_PERIOD, KeyEvent.META_SHIFT_ON);

            case '/': return new KeySpec(KeyEvent.KEYCODE_SLASH, 0);
            case '?': return new KeySpec(KeyEvent.KEYCODE_SLASH, KeyEvent.META_SHIFT_ON);

            case '\'': return new KeySpec(KeyEvent.KEYCODE_APOSTROPHE, 0);
            case '"':  return new KeySpec(KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.META_SHIFT_ON);

            case '`': return new KeySpec(KeyEvent.KEYCODE_GRAVE, 0);
            case '~': return new KeySpec(KeyEvent.KEYCODE_GRAVE, KeyEvent.META_SHIFT_ON);
        }

        return null; // unsupported
    }

    /* ───────── HELPERS ───────── */
    private void commit(String t) {
        InputConnection ic = ic();
        if (ic != null) ic.commitText(t, 1);
    }

    private void deleteChar() {
        InputConnection ic = ic();
        if (ic == null) return;

        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        else ic.deleteSurroundingText(1, 0);
    }

    private void insertTab() {
        InputConnection ic = ic();
        if (ic != null) {
            ic.commitText("\t", 1); // works in editors & termux
        }
    }

    private void sendKey() {
        InputConnection ic = ic();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    }

    private void sendArrowWithModifiers(int keyCode) {
        InputConnection ic = ic();
        if (ic == null) return;

        int meta = 0;
        if (shiftOn()) {
            meta |= KeyEvent.META_SHIFT_ON; // selection
        }

        ic.sendKeyEvent(new KeyEvent(0, 0,
                KeyEvent.ACTION_DOWN, keyCode, 0, meta));

        ic.sendKeyEvent(new KeyEvent(0, 0,
                KeyEvent.ACTION_UP, keyCode, 0, meta));
    }

    private void showMainKeyboard() {
        keyboardContainer.removeAllViews();

        View alphaRoot = getLayoutInflater().inflate(
                R.layout.keyboard_main,
                keyboardContainer,
                true
        );

        keyContainer = alphaRoot.findViewById(R.id.keyContainer);

        ctrlKey  = alphaRoot.findViewById(R.id.keyCtrl);
        shiftKey = alphaRoot.findViewById(R.id.keyShift);
        altKey   = alphaRoot.findViewById(R.id.keyAlt);
        capsKey  = alphaRoot.findViewById(R.id.keyCaps);

        attachTouchListeners((ViewGroup) alphaRoot);
        shiftKey.setActivated(shiftOn());
    }


    private void showAlphaKeyboard() {
        keyContainer.removeAllViews();
        View v = getLayoutInflater().inflate(R.layout.keyboard_alpha, keyContainer, false);
        keyContainer.addView(v);
        attachTouchListeners((ViewGroup) v);
        shiftKey = root.findViewById(R.id.keyShift);
        shiftKey.setActivated(shiftOn());
    }

    private void showSpecialKeyboard() {
        keyContainer.removeAllViews();
        View v = getLayoutInflater().inflate(R.layout.keyboard_special, keyContainer, false);
        keyContainer.addView(v);
        attachTouchListeners((ViewGroup) v);
        shiftKey = root.findViewById(R.id.keyShift2);
        shiftKey.setActivated(shiftOn());
    }

    private void showNumericKeyboard() {
        keyboardContainer.removeAllViews();

        View numRoot = getLayoutInflater().inflate(
                R.layout.keyboard_numeric,
                keyboardContainer,
                true
        );

        attachTouchListeners((ViewGroup) numRoot);
    }


    private void deletePreviousWord() {
        InputConnection ic = ic();
        if (ic == null) return;

        // Get text before cursor (limit to avoid huge strings)
        CharSequence before = ic.getTextBeforeCursor(100, 0);
        if (before == null || before.length() == 0) return;

        int deleteCount = 0;

        // 1️⃣ Skip trailing spaces
        int i = before.length() - 1;
        while (i >= 0 && Character.isWhitespace(before.charAt(i))) {
            deleteCount++;
            i--;
        }

        // 2️⃣ Delete word characters
        while (i >= 0 && !Character.isWhitespace(before.charAt(i))) {
            deleteCount++;
            i--;
        }

        if (deleteCount > 0) {
            ic.deleteSurroundingText(deleteCount, 0);
        }
    }

    private InputConnection ic() {
        return getCurrentInputConnection();
    }

}
