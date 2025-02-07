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

class QinPadIME : InputMethodService() {
    private var currentType = InputType.TYPE_CLASS_TEXT
    private var ic: InputConnection? = null
    private val kpTimeout = 500L
    private var lastKeyPressed = -1
    private var caps = false
    private var rotIndex = -1
    private var isEnglishLayout = true
    private var lockFlag = 0
    private val rotResetHandler = Handler(Looper.getMainLooper())
    
    // Double-tap handling
    private var isPendingPoundTap = false
    private val doubleTapTimeout = 300L
    private val doubleTapHandler = Handler(Looper.getMainLooper())
    private val doubleTapRunnable = Runnable {
        isPendingPoundTap = false
        isEnglishLayout = !isEnglishLayout
        resetRotator()
    }

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
        rotResetHandler.removeCallbacksAndMessages(null)
    }

    private fun getDigit(keyCode: Int): Int =
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) keyCode - KeyEvent.KEYCODE_0 else -1

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        currentType = info.inputType

        // Check for unsupported input types
        if (currentType == 0 || 
            currentType and EditorInfo.TYPE_MASK_CLASS == EditorInfo.TYPE_CLASS_PHONE) {
            onFinishInput()
            return
        }
        
        ic = currentInputConnection
        caps = false
        isEnglishLayout = true
        resetRotator()

        window?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        ic = null
        resetRotator()
        doubleTapHandler.removeCallbacks(doubleTapRunnable)
        isPendingPoundTap = false
        requestHideSelf(0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Ignore repeated key events
        if (event.repeatCount > 0) {
            return true
        }

        ic?.let { inputConnection ->
            when (keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DEL -> return handleBackspace(inputConnection)
                KeyEvent.KEYCODE_POUND -> {
                    if (isPendingPoundTap) {
                        // Double tap detected - send Enter
                        doubleTapHandler.removeCallbacks(doubleTapRunnable)
                        isPendingPoundTap = false
                        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    } else {
                        // First tap - start waiting for second tap
                        isPendingPoundTap = true
                        doubleTapHandler.postDelayed(doubleTapRunnable, doubleTapTimeout)
                    }
                    return true
                }
                KeyEvent.KEYCODE_STAR -> {
                    caps = !caps
                    return true
                }
            }

            // Any other key press should cancel pending pound tap
            doubleTapHandler.removeCallbacks(doubleTapRunnable)
            isPendingPoundTap = false

            val digit = getDigit(keyCode)
            if (digit == -1) {
                resetRotator()
                return super.onKeyDown(keyCode, event)
            }

            if (lockFlag == 0) {
                event.startTracking()
                return when (currentType and InputType.TYPE_MASK_CLASS) {
                    InputType.TYPE_CLASS_NUMBER -> {
                        inputConnection.commitText(digit.toString(), 1)
                        true
                    }
                    else -> handleTextInput(inputConnection, digit)
                }
            }
        }
        return false
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        // Ignore repeated key events
        if (event.repeatCount > 1) {
            return true
        }

        val digit = getDigit(keyCode)
        if (digit == -1) {
            resetRotator()
            return false
        }
        
        ic?.let { inputConnection ->
            if (isEnglishLayout) {
                // In English layout, long press for direct number input
                inputConnection.deleteSurroundingText(1, 0)
                inputConnection.commitText(digit.toString(), 1)
                resetRotator()
                lockFlag = 1
                return true
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && ic != null) {
            return true
        } else {
            if (lockFlag == 1) lockFlag = 0
            return super.onKeyUp(keyCode, event)
        }
    }

    private fun handleBackspace(inputConnection: InputConnection): Boolean {
        resetRotator()
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

    private fun handleTextInput(inputConnection: InputConnection, digit: Int): Boolean {
        val currentLayout = if (isEnglishLayout) ENGLISH_LAYOUT else NUMBER_LAYOUT
        val selection = LAYOUTS[currentLayout][digit]

        if (digit != lastKeyPressed) {
            rotIndex = 0
            lastKeyPressed = digit
        } else {
            rotIndex = (rotIndex + 1) % selection.length
            inputConnection.deleteSurroundingText(1, 0)
        }

        rotResetHandler.removeCallbacksAndMessages(null)
        rotResetHandler.postDelayed({ resetRotator() }, kpTimeout)

        val char = selection[rotIndex].toString()
        val text = if (caps) char.uppercase() else char
        
        // Simple direct text commit
        inputConnection.commitText(text, 1)
        
        return true
    }
} 