package com.arupine.arpkey

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

class QinPadIME : InputMethodService() {
    private var currentType = InputType.TYPE_CLASS_TEXT
    private var ic: InputConnection? = null
    private val kpTimeout = 500 //single-char input timeout
    private var cachedDigit = 10
    private var caps = false
    private var rotationMode = false
    private var rotIndex = 0
    private var lockFlag = 0 //input lock flag for long presses
    private var currentLayoutIndex = 0 //you can change the default here
    private val rotResetHandler = Handler(Looper.getMainLooper())

    //layoutIconsNormal, layoutIconsCaps and layouts must match each other
    private val layoutIconsNormal = arrayOf(R.drawable.ime_latin_normal, R.drawable.ime_numbers_normal)
    private val layoutIconsCaps = arrayOf(R.drawable.ime_latin_caps, R.drawable.ime_numbers_normal)
    private val layouts = arrayOf(
        arrayOf( //latin
            " +\n_$#()[]{}", ".,?!¿¡'\"1-~@/:\\", "abc2áäåāǎàçč",
            "def3éēěè", "ghi4ģíīǐì", "jkl5ķļ",
            "mno6ñņóõöøōǒò", "pqrs7ßš", "tuv8úüūǖǘǔǚùǜ",
            "wxyz9ýž"
        ),
        arrayOf( //numbers
            "0", "1",
            "2", "3", "4",
            "5", "6", "7", "8",
            "9"
        )
    )
    private var currentLayout: Array<String>? = null

    private fun resetRotator() {
        rotIndex = 0
        rotationMode = false
        cachedDigit = 10
    }

    private fun updateCurrentStatusIcon() {
        val icon = if(caps) layoutIconsCaps[currentLayoutIndex] else layoutIconsNormal[currentLayoutIndex]
        hideStatusIcon()
        showStatusIcon(icon)
    }

    private fun kkToDigit(keyCode: Int): Int {
        return when(keyCode) {
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_9 ->
                keyCode - KeyEvent.KEYCODE_0
            else -> 10
        }
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        currentType = info.inputType
        if(currentType == 0 || currentType and EditorInfo.TYPE_MASK_CLASS == EditorInfo.TYPE_CLASS_PHONE)
            onFinishInput()
        else {
            ic = currentInputConnection
            caps = false
            resetRotator()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            updateCurrentStatusIcon()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        ic = null
        hideStatusIcon()
        requestHideSelf(0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if(ic == null) {
            resetRotator()
            return false
        }
        val digit = kkToDigit(keyCode)
        var pound = false
        var star = false
        if(digit > 9) {
            when(keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DEL -> return handleBackspace(event)
                KeyEvent.KEYCODE_POUND -> {
                    pound = true
                    event.startTracking()  // Enable long press detection for pound key
                }
                KeyEvent.KEYCODE_STAR -> star = true
                else -> {
                    resetRotator()
                    return super.onKeyDown(keyCode, event)
                }
            }
        }

        when(currentType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> {
                ic?.let { inputConnection ->
                    if(pound)
                        inputConnection.commitText(".", 1)
                    else if(!star)
                        inputConnection.commitText(Integer.toString(digit), 1)
                }
                return true
            }
            else -> if(lockFlag == 0) {
                event.startTracking()
                return handleTextInput(digit, star, pound)
            } else
                return true
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        when(keyCode) {
            KeyEvent.KEYCODE_POUND -> {
                ic?.let { inputConnection ->
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
                return true
            }
        }
        
        val digit = kkToDigit(keyCode)
        if(digit > 9) {
            resetRotator()
            return false
        }
        
        ic?.let { inputConnection ->
            inputConnection.deleteSurroundingText(1, 0)
            inputConnection.commitText(Integer.toString(digit), 1)
        }
        resetRotator()
        lockFlag = 1
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        var res: Boolean = if(keyCode == KeyEvent.KEYCODE_BACK && ic != null) {
            true
        } else {
            if(lockFlag == 1) lockFlag = 0
            super.onKeyUp(keyCode, event)
        }
        return res
    }

    private fun handleBackspace(ev: KeyEvent): Boolean {
        resetRotator()
        val inputConnection = ic ?: return false
        
        if(inputConnection.getTextBeforeCursor(1, 0)?.isEmpty() != false) {
            inputConnection.sendKeyEvent(ev)
            requestHideSelf(0)
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            return false
        } else {
            inputConnection.deleteSurroundingText(1, 0)
            return true
        }
    }

    private fun handleTextInput(digit: Int, star: Boolean, pound: Boolean): Boolean {
        val inputConnection = ic ?: return false
        
        if(star) {
            caps = !caps
            updateCurrentStatusIcon()
        } else if(pound) {
            nextLang()
        } else {
            var targetSequence: CharSequence
            currentLayout = layouts[currentLayoutIndex]
            val selection = currentLayout?.get(digit) ?: return false

            rotResetHandler.removeCallbacksAndMessages(null)
            if(digit != cachedDigit) {
                resetRotator()
                cachedDigit = digit
            } else {
                rotationMode = true //mark that we're going to delete the next char
                rotIndex++
                if(rotIndex >= selection.length)
                    rotIndex = 0
            }
            rotResetHandler.postDelayed({ resetRotator() }, kpTimeout.toLong())

            targetSequence = selection.subSequence(rotIndex, rotIndex + 1)
            if(rotationMode)
                inputConnection.deleteSurroundingText(1, 0)
            if(caps)
                targetSequence = targetSequence.toString().uppercase()
            inputConnection.commitText(targetSequence, 1)
        }
        return true
    }

    private fun nextLang() {
        val maxInd = layouts.size - 1
        currentLayoutIndex++
        if(currentLayoutIndex > maxInd) currentLayoutIndex = 0
        currentLayout = layouts[currentLayoutIndex]
        updateCurrentStatusIcon()
        resetRotator()
    }
} 