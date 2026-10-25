package com.cobaltiron.diceseed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.security.SecureRandom

const val CANDIDATES = 6

/** Session state shared by all screens; mirrors the Prime app's AppState. */
class AppViewModel : ViewModel() {

    private val random = SecureRandom()

    // Session configuration
    var wordCount by mutableStateOf(12)
    var dieSides by mutableStateOf(6)
    var quality by mutableStateOf(3)

    // Rolling progress
    var bitsCollected by mutableStateOf(0); private set
    var bitsTarget by mutableStateOf(128); private set
    var rollNumber by mutableStateOf(1); private set
    var rollEstimate by mutableStateOf(0); private set
    var wordsSoFar by mutableStateOf(""); private set
    var currentWord by mutableStateOf(1); private set
    var wordProgress by mutableStateOf(0f); private set
    var rollingComplete by mutableStateOf(false); private set
    var cancelDialogOpen by mutableStateOf(false)

    // Seed words (completed)
    var words by mutableStateOf<List<String>>(emptyList()); private set

    // Verification
    var verifyStep by mutableStateOf(1); private set
    var verifyWordNumber by mutableStateOf(1); private set
    var verifyCandidates by mutableStateOf<List<String>>(emptyList()); private set
    var verifyWrong by mutableStateOf(List(CANDIDATES) { false }); private set
    var verifyComplete by mutableStateOf(false); private set

    private var engine: Engine? = null
    private var wordIndices: List<Int> = emptyList()
    private var verifyOrder: List<Int> = emptyList()
    private var verifyPos = 0
    private var correctPos = 0

    fun beginRolling() {
        engine = Engine(
            wordCount,
            dieSides,
            when (quality) {
                0 -> Mode.TRUST
                1 -> Mode.HASH
                2 -> Mode.MOD_SUM
                else -> Mode.PAIRWISE
            },
        )
        wordIndices = emptyList()
        words = emptyList()
        cancelDialogOpen = false
        pushRollingState()
    }

    fun roll(face: Int) {
        val eng = engine ?: return
        if (eng.isComplete) return
        eng.roll(face)
        if (eng.isComplete) finishWords(eng)
        pushRollingState()
    }

    fun undoRoll() {
        val eng = engine ?: return
        eng.undo()
        if (!eng.isComplete) wordIndices = emptyList()
        pushRollingState()
    }

    /** Discard the session (back confirmed, req 2.8.1). */
    fun cancelRolling() {
        engine = null
    }

    /**
     * True while leaving the rolling screen would lose dice rolls. Derived
     * from [rollNumber] (Compose state, rolls + 1) rather than the engine's
     * plain field so composables observing it — the rolling screen's
     * BackHandler in particular — recompose when it changes.
     */
    val hasRolls: Boolean get() = rollNumber > 1

    private fun finishWords(eng: Engine) {
        wordIndices = eng.wordIndices()
        words = wordIndices.map { Bip39.wordlist[it] }
    }

    private fun pushRollingState() {
        val eng = engine ?: return
        bitsCollected = eng.bitsCollected()
        bitsTarget = eng.bitsTarget()
        rollNumber = eng.rolls + 1
        rollEstimate = eng.estimatedTotalRolls()
        currentWord = eng.currentWordNumber()
        wordProgress = eng.currentWordProgress()
        rollingComplete = eng.isComplete
        wordsSoFar = eng.wordIndices()
            .mapIndexed { i, w -> "${i + 1}. ${Bip39.wordlist[w]}" }
            .joinToString("   ")
    }

    fun beginVerify() {
        val n = wordIndices.size
        if (n == 0) return
        verifyOrder = (0 until n).shuffled(java.util.Random(random.nextLong()))
        verifyPos = 0
        verifyComplete = false
        nextCandidates()
    }

    private fun nextCandidates() {
        val wordPos = verifyOrder[verifyPos]
        val correct = wordIndices[wordPos]
        val decoys = ArrayList<Int>()
        while (decoys.size < CANDIDATES - 1) {
            val d = random.nextInt(Bip39.wordlist.size)
            if (d != correct && d !in decoys) decoys.add(d)
        }
        correctPos = random.nextInt(CANDIDATES)
        decoys.add(correctPos, correct)

        verifyWordNumber = wordPos + 1
        verifyStep = verifyPos + 1
        verifyCandidates = decoys.map { Bip39.wordlist[it] }
        verifyWrong = List(CANDIDATES) { false }
    }

    fun verifySelect(idx: Int) {
        if (verifyComplete || verifyOrder.isEmpty()) return
        if (idx == correctPos) {
            verifyPos += 1
            if (verifyPos == verifyOrder.size) {
                verifyStep = verifyPos
                verifyComplete = true
            } else {
                nextCandidates()
            }
        } else if (idx in 0 until CANDIDATES && !verifyWrong[idx]) {
            verifyWrong = verifyWrong.toMutableList().also { it[idx] = true }
        }
    }
}
