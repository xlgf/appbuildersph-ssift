package com.screenshotbutler

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.screenshotbutler.ui.AnswerScreen
import com.screenshotbutler.ui.ButlerViewModel
import com.screenshotbutler.ui.CircleButton
import com.screenshotbutler.ui.DetailScreen
import com.screenshotbutler.ui.FolderScreen
import com.screenshotbutler.ui.HomeScreen
import com.screenshotbutler.ui.SensitiveScreen
import com.screenshotbutler.ui.SettingsScreen
import com.screenshotbutler.ui.theme.ButlerTheme
import com.screenshotbutler.ui.theme.Palette
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as ButlerApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The design is light, so system bar icons are dark.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            ButlerTheme {
                // Surface (not a plain Box) so every Text without an explicit color gets onBackground.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: ButlerViewModel = viewModel(factory = viewModelFactory { initializer { ButlerViewModel(container) } })
                    var granted by remember { mutableStateOf(hasPhotoAccess(this@MainActivity)) }
                    if (granted) {
                        LaunchedEffect(Unit) { vm.indexNew() }
                        Box(Modifier.fillMaxSize().safeDrawingPadding()) { ButlerNav(vm) }
                    } else {
                        Welcome(onGranted = { granted = true }) // full-bleed, handles its own insets
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up screenshots taken while the app was in the background.
        if (hasPhotoAccess(this)) container.scope.launch { container.indexer.indexNew() }
    }
}

private fun photoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Full access, or Android 14+ partial access to selected photos. */
private fun hasPhotoAccess(context: Context): Boolean =
    photoPermissions().any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

@Composable
private fun Welcome(onGranted: () -> Unit) {
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) onGranted()
    }
    // Sage top, coral bottom, with the white card straddling the seam (as in the Stitch design).
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Palette.Sage)
            drawRect(Palette.Coral, topLeft = Offset(0f, size.height * 0.64f))
        }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Spacer(Modifier.height(56.dp))
            Monogram(Modifier.padding(start = 32.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                "Find any screenshot",
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Ink,
                modifier = Modifier.padding(start = 32.dp),
            )
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth(0.94f)
                    .clip(RoundedCornerShape(topEnd = 40.dp, bottomEnd = 40.dp))
                    .background(Palette.Card)
                    .padding(start = 28.dp, end = 24.dp, top = 32.dp, bottom = 32.dp)
            ) {
                Text(
                    buildAnnotatedString {
                        append("Everything you need to organize your captures in a ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("fun") }
                        append(" and ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("definitive") }
                        append(" way. Every day, seven days a week.")
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 31.sp),
                    color = Palette.Ink,
                )
                Spacer(Modifier.height(24.dp))
                Text("Works completely offline, btw!", style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
            }
            Spacer(Modifier.weight(0.6f))
            CircleButton(
                "→",
                size = 72.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = { request.launch(photoPermissions()) },
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** The ssift mark and wordmark. */
@Composable
private fun Monogram(modifier: Modifier = Modifier) {
    Column(modifier) {
        Image(painterResource(R.drawable.ssift_logo), contentDescription = null, modifier = Modifier.size(112.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            "ssift",
            style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 88.sp, lineHeight = 92.sp, letterSpacing = (-4).sp),
            color = Palette.Ink,
        )
    }
}

@Composable
private fun ButlerNav(vm: ButlerViewModel) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = vm,
                onAsk = { q -> nav.navigate("answer?q=${Uri.encode(q)}") },
                onFolder = { tag -> nav.navigate(if (tag == "sensitive") "sensitive" else "folder/$tag") },
                onShot = { id -> nav.navigate("detail/$id") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("answer?q={q}", arguments = listOf(navArgument("q") { defaultValue = "" })) { entry ->
            AnswerScreen(vm, entry.arguments?.getString("q").orEmpty(), onBack = { nav.popBackStack() }, onShot = { nav.navigate("detail/$it") })
        }
        composable("folder/{tag}") { entry ->
            FolderScreen(vm, entry.arguments?.getString("tag").orEmpty(), onBack = { nav.popBackStack() }, onShot = { nav.navigate("detail/$it") })
        }
        composable("sensitive") {
            SensitiveScreen(vm, onBack = { nav.popBackStack() }, onShot = { nav.navigate("detail/$it") })
        }
        composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            DetailScreen(vm, entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
        }
        composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
    }
}
