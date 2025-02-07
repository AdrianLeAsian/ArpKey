package com.arupine.arpkey

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.WindowManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import androidx.recyclerview.widget.GridLayoutManager
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import android.net.Uri
import android.provider.Settings
import android.content.Intent
import android.view.ViewGroup
import android.widget.TextView

class QinPadIME : InputMethodService() {
    private var currentType = InputType.TYPE_CLASS_TEXT
    private var ic: InputConnection? = null
    private val kpTimeout = 500L
    private var lastKeyPressed = -1
    private var caps = false
    private var rotIndex = -1
    private var isEnglishLayout = true
    private var lockFlag = 0
    private var isLongPressing = false  // Add flag for long press detection
    private val rotResetHandler = Handler(Looper.getMainLooper())
    
    // Double-tap handling
    private var lastPoundTapTime = 0L
    private var lastStarTapTime = 0L
    private val doubleTapTimeout = 300L  // Reduced for faster response
    private val doubleTapHandler = Handler(Looper.getMainLooper())
    private val poundSingleTapRunnable = Runnable {
        // Execute single tap action for pound key
        isEnglishLayout = !isEnglishLayout
        resetRotator()
        lastPoundTapTime = 0L
    }
    private val starSingleTapRunnable = Runnable {
        // Execute single tap action for star key
        caps = !caps
        lastStarTapTime = 0L
    }

    private var currentWord = StringBuilder()
    private var isComposing = false

    // Symbol popup handling
    private var symbolPopupWindow: PopupWindow? = null
    private var currentSymbolPage = 0
    
    private val symbolPages = listOf(
        // Common Punctuation
        listOf(".", ",", "!", "?", "'", "\"", "-", "_", ";"),
        // More Punctuation
        listOf(":", "(", ")", "[", "]", "{", "}", "¿", "¡"),
        // Math Symbols
        listOf("+", "=", "<", ">", "×", "÷", "±", "%", "≠"),
        // Currency
        listOf("$", "€", "£", "¥", "¢", "₹", "₽", "₩", "₪"),
        // Common Symbols
        listOf("@", "#", "&", "*", "\\", "/", "|", "~", "^"),
        // More Symbols
        listOf("°", "•", "†", "‡", "§", "¶", "©", "®", "™"),
        // Arrows
        listOf("←", "→", "↑", "↓", "↔", "↕", "⇐", "⇒", "⇔"),
        // Smileys
        listOf("😊", "😂", "🥰", "😎", "🤔", "😅", "😭", "😍", "🥺"),
        // More Emotions
        listOf("😤", "😡", "🥱", "😴", "🤮", "🤑", "😱", "🤯", "🥳"),
        // Hearts & Love
        listOf("❤️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟"),
        // Hands & People
        listOf("👍", "👎", "👏", "🙌", "🤝", "👊", "✌️", "🤞", "🤙"),
        // Nature
        listOf("🌟", "⭐", "🌙", "☀️", "🌈", "⚡", "❄️", "🌸", "🍀"),
        // Objects
        listOf("💡", "⚡", "💣", "⚔️", "🎮", "📱", "💻", "⌚", "📷"),
        // Food & Drink
        listOf("☕", "🍕", "🍔", "🌮", "🍜", "🍣", "🍩", "🍪", "🍷"),
        // Activities
        listOf("⚽", "🎮", "🎵", "🎨", "🎭", "🎪", "🎯", "🎲", "🎱"),
        // Travel & Places
        listOf("🚗", "✈️", "🏖️", "🗽", "🗼", "🏰", "⛰️", "🌋", "🏝️"),
        // Flags
        listOf("🏁", "🚩", "🎌", "🏴", "🏳️", "🏳️‍🌈", "🏴‍☠️", "🇺🇳", "⚐"),
        // Zodiac
        listOf("♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐"),
        // More Zodiac & Symbols
        listOf("♑", "♒", "♓", "⛎", "☮️", "✝️", "☪️", "🕉️", "☯️"),
        // Music
        listOf("♩", "♪", "♫", "♬", "🎵", "🎶", "🎼", "🎹", "🎸")
    )

    companion object {
        private const val ENGLISH_LAYOUT = 0
        private const val NUMBER_LAYOUT = 1
        
        private val LAYOUTS = arrayOf(
            arrayOf( // English layout
                " +\n_$#()[]{}", 
                ".,?!¿¡'\"1-~@/:\\", 
                "abc2áäåāǎàçč",
                "def3éēěè", 
                "ghi4ģíīǐì", 
                "jkl5ķļ",
                "mno6ñņóõöøōǒò", 
                "pqrs7ßš", 
                "tuv8úüūǖǘǔǚùǜ",
                "wxyz9ýž"
            ),
            arrayOf( // Number layout
                "0", "1", "2", "3", "4",
                "5", "6", "7", "8", "9"
            )
        )
    }

    private fun resetRotator() {
        rotIndex = -1
        lastKeyPressed = -1
        isLongPressing = false  // Reset long press flag
        rotResetHandler.removeCallbacksAndMessages(null)
    }

    private fun getDigit(keyCode: Int): Int =
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) keyCode - KeyEvent.KEYCODE_0 else -1

    private fun commitCurrentWord(inputConnection: InputConnection) {
        if (currentWord.isNotEmpty()) {
            inputConnection.commitText(currentWord.toString(), 1)
            currentWord.clear()
            isComposing = false
        }
    }

    private fun handleTextInput(inputConnection: InputConnection, digit: Int): Boolean {
        val currentLayout = if (isEnglishLayout) ENGLISH_LAYOUT else NUMBER_LAYOUT
        val selection = when (currentLayout) {
            ENGLISH_LAYOUT -> (LAYOUTS[ENGLISH_LAYOUT] as Array<String>)[digit]
            else -> (LAYOUTS[NUMBER_LAYOUT] as Array<String>)[digit]
        }

        if (digit != lastKeyPressed) {
            // New key pressed - commit previous character and start new one
            if (isComposing) {
                commitCurrentWord(inputConnection)
            }
            rotIndex = 0
            lastKeyPressed = digit
        } else {
            // Same key pressed - cycling through characters
            rotIndex = if (selection.isNotEmpty()) (rotIndex + 1).rem(selection.length) else 0
            if (currentWord.isNotEmpty()) {
                currentWord.deleteCharAt(currentWord.length - 1)
            }
        }

        rotResetHandler.removeCallbacksAndMessages(null)
        rotResetHandler.postDelayed({ 
            if (isComposing) {
                commitCurrentWord(inputConnection)
            }
            resetRotator() 
        }, kpTimeout)

        val char = if (selection.isNotEmpty()) selection[rotIndex].toString() else ""
        val text = if (caps) char.uppercase() else char

        // Handle space character
        if (text == " ") {
            commitCurrentWord(inputConnection)
            inputConnection.commitText(" ", 1)
        } else {
            // Add character to current word
            currentWord.append(text)
            isComposing = true
            // Show the entire word as composing text
            inputConnection.setComposingText(currentWord.toString(), 1)
        }
        
        return true
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        currentType = info.inputType

        if (currentType == 0 || 
            currentType and EditorInfo.TYPE_MASK_CLASS == EditorInfo.TYPE_CLASS_PHONE) {
            onFinishInput()
            return
        }
        
        ic = currentInputConnection
        caps = false
        isEnglishLayout = true
        currentSymbolPage = 0
        resetRotator()
        currentWord.clear()
        isComposing = false

        window?.window?.let { win ->
            win.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            )
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        ic?.let { inputConnection ->
            commitCurrentWord(inputConnection)
        }
        ic = null
        resetRotator()
        currentWord.clear()
        isComposing = false
        lastPoundTapTime = 0L
        lastStarTapTime = 0L
        doubleTapHandler.removeCallbacks(poundSingleTapRunnable)
        doubleTapHandler.removeCallbacks(starSingleTapRunnable)
        symbolPopupWindow?.dismiss()
        symbolPopupWindow = null
        requestHideSelf(0)
    }

    private fun showSymbolPopup() {
        try {
            // Check for overlay permission
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            }

            // Dismiss any existing popup
            symbolPopupWindow?.dismiss()
            symbolPopupWindow = null
            
            val inflater = LayoutInflater.from(this)
            val popupView = inflater.inflate(R.layout.symbol_popup_layout, null)
            
            // Create new popup window with fixed size
            symbolPopupWindow = PopupWindow(
                popupView,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                false  // Changed to false to prevent auto-dismiss
            ).apply {
                isOutsideTouchable = false
                isFocusable = false  // Changed to false to prevent focus stealing
                elevation = 24f
                
                setBackgroundDrawable(resources.getDrawable(R.drawable.popup_background, theme))
                setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                contentView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }

            // Update symbols in grid
            updateSymbolGrid(popupView)

            // Show popup immediately
            window?.window?.decorView?.let { decorView ->
                try {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            symbolPopupWindow?.showAtLocation(
                                decorView,
                                Gravity.CENTER,
                                0,
                                0
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateSymbolGrid(view: View) {
        val symbols = symbolPages[currentSymbolPage]
        for (i in 1..9) {
            val symbolView = view.findViewById<TextView>(
                resources.getIdentifier("symbol$i", "id", packageName)
            )
            symbolView.text = symbols[i - 1]
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // If popup is showing, handle symbol selection
        if (symbolPopupWindow != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_STAR -> {
                    val newPage = if (currentSymbolPage > 0) currentSymbolPage - 1 else symbolPages.size - 1
                    currentSymbolPage = newPage
                    updateSymbolGrid(symbolPopupWindow!!.contentView)
                    return true
                }
                KeyEvent.KEYCODE_POUND -> {
                    val newPage = if (currentSymbolPage < symbolPages.size - 1) currentSymbolPage + 1 else 0
                    currentSymbolPage = newPage
                    updateSymbolGrid(symbolPopupWindow!!.contentView)
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    symbolPopupWindow?.dismiss()
                    symbolPopupWindow = null
                    return true
                }
                in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                    val index = keyCode - KeyEvent.KEYCODE_1
                    val symbols = symbolPages[currentSymbolPage]
                    if (index < symbols.size) {
                        ic?.commitText(symbols[index], 1)
                        symbolPopupWindow?.dismiss()
                        symbolPopupWindow = null
                    }
                    return true
                }
                else -> return true
            }
        }

        if (event.repeatCount > 0) {
            return true
        }

        ic?.let { inputConnection ->
            when (keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DEL -> return handleBackspace(inputConnection)
                KeyEvent.KEYCODE_POUND -> {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastPoundTapTime < doubleTapTimeout) {
                        // Double tap # - Enter/new line
                        doubleTapHandler.removeCallbacks(poundSingleTapRunnable)
                        commitCurrentWord(inputConnection)
                        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                        lastPoundTapTime = 0L
                    } else {
                        // Schedule single tap action
                        lastPoundTapTime = currentTime
                        doubleTapHandler.postDelayed(poundSingleTapRunnable, doubleTapTimeout)
                    }
                    return true
                }
                KeyEvent.KEYCODE_STAR -> {
                    if (currentType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastStarTapTime < doubleTapTimeout) {
                            // Double tap * - Show symbol popup
                            doubleTapHandler.removeCallbacks(starSingleTapRunnable)
                            showSymbolPopup()
                            lastStarTapTime = 0L
                        } else {
                            // Schedule single tap action
                            lastStarTapTime = currentTime
                            doubleTapHandler.postDelayed(starSingleTapRunnable, doubleTapTimeout)
                        }
                        return true
                    }
                }
            }

            val digit = getDigit(keyCode)
            if (digit == -1) {
                resetRotator()
                return super.onKeyDown(keyCode, event)
            }

            if (lockFlag == 0) {
                event.startTracking()
                return when {
                    currentType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER -> {
                        inputConnection.commitText(digit.toString(), 1)
                        true
                    }
                    !isEnglishLayout -> {
                        inputConnection.commitText(digit.toString(), 1)
                        true
                    }
                    else -> handleTextInput(inputConnection, digit)
                }
            }
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Regular key handling if popup is not showing
        if (keyCode == KeyEvent.KEYCODE_BACK && ic != null) {
            return true
        } else {
            if (lockFlag == 1) lockFlag = 0
            return super.onKeyUp(keyCode, event)
        }
    }

    private fun handleBackspace(inputConnection: InputConnection): Boolean {
        resetRotator()
        if (currentWord.isNotEmpty()) {
            // Remove last character from current word
            currentWord.deleteCharAt(currentWord.length - 1)
            if (currentWord.isEmpty()) {
                isComposing = false
                inputConnection.finishComposingText()
            } else {
                inputConnection.setComposingText(currentWord.toString(), 1)
            }
            return true
        }
        return if (inputConnection.getTextBeforeCursor(1, 0)?.isEmpty() != false) {
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            requestHideSelf(0)
            false
        } else {
            inputConnection.deleteSurroundingText(1, 0)
            true
        }
    }
} 