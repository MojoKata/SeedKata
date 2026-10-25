package com.cobaltiron.diceseed

import java.security.MessageDigest
import kotlin.math.ceil

/**
 * Dice-roll entropy engine. A direct port of the Prime app's engine
 * (prime/src/engine.rs); see docs/design-notes.md for the security notes.
 *
 * Modes, selected on the Dice Quality screen:
 * - TRUST ("I trust them", 1x): straight bit-mapping, assumes fair dice.
 * - HASH ("Maybe biased", ~1.5x): rolls are collected until the bit-mapped
 *   raw entropy reaches 1.5x the target, then the seed entropy is SHA-256
 *   over the full roll transcript. Words appear only at completion.
 * - MOD_SUM ("Very sus", 2x): pairs of rolls combined as (a + b) mod faces
 *   before bit-mapping.
 * - PAIRWISE ("Maybe loaded", ~4-7x): von Neumann comparison of roll pairs;
 *   a < b emits 0, a > b emits 1, ties are discarded.
 */
enum class Mode { TRUST, HASH, MOD_SUM, PAIRWISE }

class Engine(wordCount: Int, val sides: Int, val mode: Mode) {

    val entBits: Int = if (wordCount == 24) 256 else 128
    private val bits = ArrayList<Int>()
    private var rawBits = 0
    private val transcript = ArrayList<Byte>()
    private var pending: Int? = null
    private val rollHistory = ArrayList<Int>()

    var rolls = 0
        private set

    val wordTotal: Int get() = (entBits + entBits / 32) / 11
    val checksumBits: Int get() = entBits / 32
    private val rawBitsNeeded: Int get() = entBits * 3 / 2

    val isComplete: Boolean
        get() = if (mode == Mode.HASH) rawBits >= rawBitsNeeded else bits.size >= entBits

    private fun mappedBitCount(value: Int): Int =
        if (sides == 8) 3 else if (value <= 4) 2 else 1

    /** Feed one physical roll (face value, 1-based). Ignored once complete. */
    fun roll(value: Int) {
        if (isComplete || value < 1 || value > sides) return
        rollHistory.add(value)
        process(value)
    }

    /** Roll back the last dice roll by replaying the remaining history. */
    fun undo() {
        if (rollHistory.isEmpty()) return
        rollHistory.removeAt(rollHistory.size - 1)
        val history = ArrayList(rollHistory)
        bits.clear(); rawBits = 0; transcript.clear(); pending = null; rolls = 0
        rollHistory.clear()
        for (v in history) {
            rollHistory.add(v)
            process(v)
        }
    }

    private fun process(value: Int) {
        rolls += 1
        when (mode) {
            Mode.PAIRWISE -> {
                val prev = pending
                if (prev == null) {
                    pending = value
                } else {
                    pending = null
                    if (prev != value) pushBit(if (prev > value) 1 else 0)
                }
            }
            Mode.HASH -> {
                transcript.add(value.toByte())
                rawBits += mappedBitCount(value)
            }
            Mode.MOD_SUM -> {
                val prev = pending
                if (prev == null) {
                    pending = value
                } else {
                    pending = null
                    pushMapped(((prev - 1) + (value - 1)) % sides + 1)
                }
            }
            Mode.TRUST -> pushMapped(value)
        }
    }

    private fun pushMapped(value: Int) {
        if (sides == 8) {
            val v = value - 1
            pushBit((v shr 2) and 1); pushBit((v shr 1) and 1); pushBit(v and 1)
        } else if (value <= 4) {
            val v = value - 1
            pushBit((v shr 1) and 1); pushBit(v and 1)
        } else {
            pushBit(value - 5)
        }
    }

    private fun pushBit(bit: Int) {
        if (bits.size < entBits) bits.add(bit)
    }

    private fun rollsPerMappedBit(): Double = if (sides == 8) 1.0 / 3.0 else 3.0 / 5.0

    /** Total rolls this session is estimated to need, given progress so far. */
    fun estimatedTotalRolls(): Int {
        val (remaining, rollsPerBit) = when (mode) {
            Mode.HASH -> (rawBitsNeeded - rawBits).toDouble() to rollsPerMappedBit()
            Mode.TRUST -> (entBits - bits.size).toDouble() to rollsPerMappedBit()
            Mode.MOD_SUM -> (entBits - bits.size).toDouble() to rollsPerMappedBit() * 2.0
            Mode.PAIRWISE -> (entBits - bits.size).toDouble() to
                if (sides == 8) 16.0 / 7.0 else 12.0 / 5.0
        }
        return rolls + ceil(remaining * rollsPerBit).toInt()
    }

    /** Entropy bits collected so far (raw bits in HASH mode). */
    fun bitsCollected(): Int =
        if (mode == Mode.HASH) minOf(rawBits, rawBitsNeeded) else bits.size

    /** Bits needed to finish (the 1.5x raw target in HASH mode). */
    fun bitsTarget(): Int = if (mode == Mode.HASH) rawBitsNeeded else entBits

    private fun overallProgress(): Float = bitsCollected().toFloat() / bitsTarget()

    /** Number of fully-determined words (all words once complete). */
    fun wordsDone(): Int = when {
        isComplete -> wordTotal
        mode == Mode.HASH -> 0
        else -> minOf(bits.size / 11, wordTotal - 1)
    }

    /** 1-based number of the word currently being rolled. */
    fun currentWordNumber(): Int {
        if (isComplete) return wordTotal
        return if (mode == Mode.HASH) {
            minOf((overallProgress() * wordTotal).toInt(), wordTotal - 1) + 1
        } else {
            wordsDone() + 1
        }
    }

    /** Progress (0..1) through the word currently being rolled. */
    fun currentWordProgress(): Float {
        if (isComplete) return 1f
        return if (mode == Mode.HASH) {
            val scaled = overallProgress() * wordTotal
            scaled - (currentWordNumber() - 1)
        } else {
            val done = wordsDone()
            val have = bits.size - done * 11
            val need = if (done == wordTotal - 1) 11 - checksumBits else 11
            have.toFloat() / need
        }
    }

    /** Entropy bytes; only meaningful once complete. */
    fun entropy(): ByteArray {
        if (mode == Mode.HASH) {
            val digest = MessageDigest.getInstance("SHA-256").digest(transcript.toByteArray())
            return digest.copyOf(entBits / 8)
        }
        return ByteArray(bits.size / 8) { i ->
            var b = 0
            for (j in 0 until 8) b = (b shl 1) or bits[i * 8 + j]
            b.toByte()
        }
    }

    /** Word indices for every fully-determined word. */
    fun wordIndices(): List<Int> {
        if (isComplete) return Bip39.mnemonicIndices(entropy())
        if (mode == Mode.HASH) return emptyList()
        return (0 until wordsDone()).map { w ->
            var acc = 0
            for (b in 0 until 11) acc = (acc shl 1) or bits[w * 11 + b]
            acc
        }
    }
}
