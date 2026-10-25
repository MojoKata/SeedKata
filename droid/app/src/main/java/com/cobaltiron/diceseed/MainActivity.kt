package com.cobaltiron.diceseed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object Palette {
    val background = Color(0xFF181314)
    val card = Color(0xFF241E1F)
    val foreground = Color(0xFFF2EDEA)
    val muted = Color(0xFF9B9394)
    val primary = Color(0xFF2FB5CB)
    val secondary = Color(0xFF4A4344)
    val danger = Color(0xFFF23B33)
    val brass = Color(0xFFC8913F)
}

private val DiceSeedColors = darkColorScheme(
    primary = Palette.primary,
    onPrimary = Color.White,
    secondary = Palette.secondary,
    onSecondary = Palette.foreground,
    background = Palette.background,
    onBackground = Palette.foreground,
    surface = Palette.card,
    onSurface = Palette.foreground,
    surfaceVariant = Palette.card,
    onSurfaceVariant = Palette.muted,
    error = Palette.danger,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bip39.load(applicationContext)

        setContent { DiceSeedApp() }
    }
}

@Composable
fun DiceSeedApp(vm: AppViewModel = viewModel()) {
    MaterialTheme(colorScheme = DiceSeedColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            DiceSeedNav(vm)
        }
    }
}

object Routes {
    const val LAUNCH = "launch"
    const val SEED_LENGTH = "seed-length"
    const val DICE_TYPE = "dice-type"
    const val DICE_QUALITY = "dice-quality"
    const val ROLLING = "rolling"
    const val WORDS = "words"
    const val VERIFY = "verify"
}

@Composable
fun DiceSeedNav(vm: AppViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.LAUNCH) {
        composable(Routes.LAUNCH) { LaunchScreen(onGetStarted = { nav.navigate(Routes.SEED_LENGTH) }) }
        composable(Routes.SEED_LENGTH) {
            SeedLengthScreen(onSelect = { count ->
                vm.wordCount = count
                nav.navigate(Routes.DICE_TYPE)
            })
        }
        composable(Routes.DICE_TYPE) {
            DiceTypeScreen(onSelect = { sides ->
                vm.dieSides = sides
                nav.navigate(Routes.DICE_QUALITY)
            })
        }
        composable(Routes.DICE_QUALITY) {
            DiceQualityScreen(onSelect = { quality ->
                vm.quality = quality
                vm.beginRolling()
                nav.navigate(Routes.ROLLING)
            })
        }
        composable(Routes.ROLLING) {
            RollingScreen(
                vm = vm,
                onContinue = { nav.navigate(Routes.WORDS) },
                onCancelled = { nav.popBackStack() },
            )
        }
        composable(Routes.WORDS) {
            WordsScreen(
                vm = vm,
                onTrustMe = { nav.popBackStack(Routes.LAUNCH, inclusive = false) },
                onVerify = {
                    vm.beginVerify()
                    nav.navigate(Routes.VERIFY)
                },
            )
        }
        composable(Routes.VERIFY) {
            VerifyScreen(
                vm = vm,
                onDone = { nav.popBackStack(Routes.LAUNCH, inclusive = false) },
            )
        }
    }
}
