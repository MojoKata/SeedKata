package com.cobaltiron.diceseed

import android.content.Context
import java.security.MessageDigest

/** BIP39 mnemonic encoding from raw entropy. */
object Bip39 {

    @Volatile
    private var words: List<String> = emptyList()

    /** Load the official English wordlist from assets (2048 words). */
    fun load(context: Context) {
        if (words.isEmpty()) {
            words = context.assets.open("bip39-english.txt")
                .bufferedReader().readLines().filter { it.isNotBlank() }
            check(words.size == 2048) { "BIP39 wordlist must have 2048 words" }
        }
    }

    val wordlist: List<String> get() = words

    /**
     * Encode entropy (16 or 32 bytes) into BIP39 word indices: one 11-bit
     * index per word, entropy bits MSB-first followed by the SHA-256
     * checksum bits.
     */
    fun mnemonicIndices(entropy: ByteArray): List<Int> {
        val entBits = entropy.size * 8
        val csBits = entBits / 32
        val checksum = MessageDigest.getInstance("SHA-256").digest(entropy)[0].toInt() and 0xff

        fun bit(i: Int): Int =
            if (i < entBits) {
                (entropy[i / 8].toInt() shr (7 - (i % 8))) and 1
            } else {
                (checksum shr (7 - (i - entBits))) and 1
            }

        return (0 until (entBits + csBits) / 11).map { w ->
            var acc = 0
            for (b in 0 until 11) acc = (acc shl 1) or bit(w * 11 + b)
            acc
        }
    }
}
