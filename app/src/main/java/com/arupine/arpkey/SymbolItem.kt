package com.arupine.arpkey

data class SymbolItem(
    val symbol: String,
    val category: Category
) {
    enum class Category {
        PUNCTUATION,
        SYMBOL,
        EMOJI
    }
} 