package com.cobaltiron.diceseed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun ScreenColumn(content: @Composable ColumnScopeAlias.() -> Unit) =
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.background)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = { ColumnScopeAlias(this).content() },
    )

/** Thin alias so screens read like the Slint pages. */
class ColumnScopeAlias(scope: androidx.compose.foundation.layout.ColumnScope) :
    androidx.compose.foundation.layout.ColumnScope by scope

@Composable
private fun PageTitle(text: String) = Text(
    text = text,
    modifier = Modifier.fillMaxWidth(),
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.headlineSmall,
    fontWeight = FontWeight.Bold,
    color = Palette.foreground,
)

@Composable
private fun InfoCard(title: String, body: String, footer: String? = null) =
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Palette.card),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Palette.foreground)
            Text(body, color = Palette.foreground.copy(alpha = 0.9f), fontSize = 16.sp)
            if (footer != null) Text(footer, color = Palette.muted, fontSize = 13.sp)
        }
    }

@Composable
private fun BigButton(
    text: String,
    primary: Boolean = true,
    danger: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) = Button(
    onClick = onClick,
    modifier = modifier.height(56.dp),
    shape = RoundedCornerShape(28.dp),
    colors = ButtonDefaults.buttonColors(
        containerColor = if (danger) Palette.danger else if (primary) Palette.primary else Palette.secondary,
    ),
) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun LabeledBar(label: String, progress: Float) =
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Palette.foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = Palette.primary,
            trackColor = Palette.secondary,
        )
    }

// Screen 1: Launch (req 1.1)
@Composable
fun LaunchScreen(onGetStarted: () -> Unit) = ScreenColumn {
    Text(
        "DiceSeed",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = Palette.muted,
        fontWeight = FontWeight.SemiBold,
    )
    Image(
        painter = painterResource(R.drawable.dice_banner),
        contentDescription = "Steampunk dice rolling toward the viewer",
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Crop,
    )
    Text(
        "TRUE ENTROPY",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = Palette.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
    InfoCard(
        title = "Welcome to DiceSeed",
        body = "Generate a BIP39 seed phrase from physical dice rolls — no electronic randomness involved.",
        footer = "Grab your dice and tap below to get started.",
    )
    Spacer(Modifier.weight(1f))
    BigButton("Get started", onClick = onGetStarted)
}

// Screen 2: Word selection (req 1.2)
@Composable
fun SeedLengthScreen(onSelect: (Int) -> Unit) = ScreenColumn {
    PageTitle("Seed Length")
    InfoCard(
        title = "How long should your seed be?",
        body = "12 words carry 128 bits of entropy — the Bitcoin standard, secure for virtually all uses, and needs fewer rolls. 24 words carry 256 bits — the maximum, with roughly twice the rolling.",
    )
    Spacer(Modifier.weight(1f))
    BigButton("12 words") { onSelect(12) }
    BigButton("24 words", primary = false) { onSelect(24) }
}

// Screen 3: Dice selection (req 1.3)
@Composable
fun DiceTypeScreen(onSelect: (Int) -> Unit) = ScreenColumn {
    PageTitle("Your Dice")
    Text(
        "Which dice will you roll?",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = Palette.muted,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = painterResource(R.drawable.die_d6),
                contentDescription = "Six-sided die",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
            )
            BigButton("6-sided") { onSelect(6) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = painterResource(R.drawable.die_d8),
                contentDescription = "Eight-sided die",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
            )
            BigButton("8-sided") { onSelect(8) }
        }
    }
    Spacer(Modifier.weight(1f))
}

// Screen 4: Dice Quality (req 1.4)
@Composable
fun DiceQualityScreen(onSelect: (Int) -> Unit) = ScreenColumn {
    PageTitle("Dice Quality")
    InfoCard(
        title = "How fair are your dice?",
        body = "Real dice are rarely perfect. DiceSeed can compensate for biased dice by collecting additional rolls — the more suspicious you are of your dice, the more extra rolls it asks for (shown as a multiplier).",
    )
    Spacer(Modifier.weight(1f))
    BigButton("I trust them (1x)") { onSelect(0) }
    BigButton("Maybe biased (~1.5x)", primary = false) { onSelect(1) }
    BigButton("Very sus (2x)", primary = false) { onSelect(2) }
    BigButton("Maybe loaded (~4-7x)", primary = false) { onSelect(3) }
}

// Screen 5: Dice rolling (req 1.5 and section 2)
@Composable
fun RollingScreen(vm: AppViewModel, onContinue: () -> Unit, onCancelled: () -> Unit) {
    // Device back with rolls at stake opens the confirmation (req 2.8)
    BackHandler(enabled = vm.hasRolls && !vm.cancelDialogOpen) {
        vm.cancelDialogOpen = true
    }

    ScreenColumn {
        PageTitle("Roll the Dice")
        InfoCard(
            title = if (vm.wordsSoFar.isEmpty()) "Ready to roll" else "Words so far",
            body = if (vm.wordsSoFar.isEmpty())
                "Roll your die and tap the matching face. Each roll adds entropy; finished words appear here."
            else vm.wordsSoFar,
        )
        Spacer(Modifier.weight(1f))

        // Overall entropy progress, directly above the word bar (req 2.1)
        LabeledBar(
            "Bits: ${vm.bitsCollected} of ${vm.bitsTarget}",
            vm.bitsCollected.toFloat() / maxOf(1, vm.bitsTarget),
        )
        if (!vm.rollingComplete) {
            // Current word progress (req 2.2, 2.6)
            LabeledBar("Word ${vm.currentWord} of ${vm.wordCount}", vm.wordProgress)
        }

        // Roll counter (req 2.4) and undo of the last roll (req 2.7)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(44.dp))
            Text(
                text = if (vm.rollingComplete) "Dice rolls complete"
                else "Roll ${vm.rollNumber} of ${vm.rollEstimate} (estimated)",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = if (vm.rollingComplete) Palette.foreground else Palette.muted,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { vm.undoRoll() }, enabled = vm.rollNumber > 1) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Undo last roll",
                    tint = if (vm.rollNumber > 1) Palette.foreground else Palette.secondary,
                )
            }
        }

        if (!vm.rollingComplete) {
            // Die image left of the face buttons (req 2.3)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    painter = painterResource(
                        if (vm.dieSides == 8) R.drawable.die_d8_roll else R.drawable.die_d6_roll
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .width(72.dp)
                        .height(124.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val perRow = if (vm.dieSides == 8) 4 else 3
                    for (row in 0 until 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (col in 1..perRow) {
                                val face = row * perRow + col
                                BigButton(
                                    "$face",
                                    primary = false,
                                    modifier = Modifier.weight(1f),
                                ) { vm.roll(face) }
                            }
                        }
                    }
                }
            }
        } else {
            // req 2.9.2 / 2.9.3
            BigButton("Continue", onClick = onContinue)
        }
    }

    // Confirmation before discarding rolls (req 2.8)
    if (vm.cancelDialogOpen) {
        AlertDialog(
            onDismissRequest = { vm.cancelDialogOpen = false },
            containerColor = Palette.card,
            title = { Text("Cancel dice rolling?", color = Palette.foreground) },
            text = {
                Text(
                    "All ${vm.rollNumber - 1} dice rolls so far will be discarded.",
                    color = Palette.muted,
                )
            },
            confirmButton = {
                BigButton("Discard", danger = true, modifier = Modifier.width(140.dp)) {
                    vm.cancelDialogOpen = false
                    vm.cancelRolling()
                    onCancelled()
                }
            },
            dismissButton = {
                BigButton("Keep rolling", primary = false, modifier = Modifier.width(170.dp)) {
                    vm.cancelDialogOpen = false
                }
            },
        )
    }
}

// Screen 6: Words (req 1.6 and section 3)
@Composable
fun WordsScreen(vm: AppViewModel, onTrustMe: () -> Unit, onVerify: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val pageCount = if (vm.wordCount > 12) 2 else 1

    ScreenColumn {
        PageTitle("Your Seed Words")

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (col in 0 until 2) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (r in 0 until 6) {
                        val number = page * 12 + col * 6 + r + 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Palette.card)
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            Text("$number.", color = Palette.muted, modifier = Modifier.width(34.dp))
                            Text(
                                vm.words.getOrElse(number - 1) { "" },
                                color = Palette.foreground,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        // Back/forward page navigation for 24-word seeds (req 3.2)
        if (pageCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { page = 0 }, enabled = page > 0) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous page",
                        tint = if (page > 0) Palette.foreground else Palette.secondary,
                    )
                }
                Text("Page ${page + 1} of $pageCount", color = Palette.muted)
                IconButton(onClick = { page = 1 }, enabled = page < pageCount - 1) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next page",
                        tint = if (page < pageCount - 1) Palette.foreground else Palette.secondary,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // req 3.3
        Text(
            "Be sure to write down your seed words",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Palette.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton("Trust me!", primary = false, modifier = Modifier.weight(1f), onClick = onTrustMe)
            BigButton("Verify", modifier = Modifier.weight(1f), onClick = onVerify)
        }
    }
}

// Screen 7: Verify (req 1.7 and section 4)
@Composable
fun VerifyScreen(vm: AppViewModel, onDone: () -> Unit) = ScreenColumn {
    PageTitle("Verify")

    if (!vm.verifyComplete) {
        InfoCard(
            title = "Which is word #${vm.verifyWordNumber}?",
            body = "Check your written backup and tap the matching word. Every word is verified once, in random order.",
            footer = "Verified ${vm.verifyStep - 1} of ${vm.wordCount}",
        )
        Spacer(Modifier.height(8.dp))
        // Two columns of three candidates (req 4.2); wrong picks turn red (req 4.4)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (col in 0 until 2) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (r in 0 until 3) {
                        val idx = col * 3 + r
                        BigButton(
                            vm.verifyCandidates.getOrElse(idx) { "" },
                            primary = false,
                            danger = vm.verifyWrong.getOrElse(idx) { false },
                        ) { vm.verifySelect(idx) }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    } else {
        InfoCard(
            title = "All words verified!",
            body = "Your written backup matches the generated seed phrase. Keep it safe and private.",
        )
        Spacer(Modifier.weight(1f))
        BigButton("Done", onClick = onDone)
    }
}
