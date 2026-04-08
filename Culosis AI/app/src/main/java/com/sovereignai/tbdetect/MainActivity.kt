package com.sovereignai.tbdetect

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.sovereignai.tbdetect.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Color tokens ──────────────────────────────────────────────
// Light: Clinical Clarity
val LightBackground   = Color(0xFFFFFFFF)
val LightPrimary      = Color(0xFF005EB8)
val LightSecondary    = Color(0xFF64748B)
val LightHealthy      = Color(0xFF15803D)
val LightWarning      = Color(0xFFA16207)
val LightDanger       = Color(0xFFB91C1C)
val LightInfo         = Color(0xFF1D4ED8)
val LightSurface      = Color(0xFFF1F5F9)
val LightGlass        = Color(0xCCFFFFFF)

// Dark: Radiology Suite
val DarkBackground    = Color(0xFF121212)
val DarkPrimary       = Color(0xFF3B82F6)
val DarkSecondary     = Color(0xFF94A3B8)
val DarkHealthy       = Color(0xFF22C55E)
val DarkWarning       = Color(0xFFEAB308)
val DarkDanger        = Color(0xFFEF4444)
val DarkInfo          = Color(0xFF60A5FA)
val DarkSurface       = Color(0xFF1E1E2E)
val DarkGlass         = Color(0x99121212)

class MainActivity : ComponentActivity() {
    private lateinit var classifier: ImageClassifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        classifier = ImageClassifier(this)
        setContent {
            TBDetectApp(classifier)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
    }
}

@Composable
fun TBDetectApp(classifier: ImageClassifier) {
    var isDark by remember { mutableStateOf(true) }

    val lightScheme = lightColorScheme(
        primary        = LightPrimary,
        secondary      = LightSecondary,
        background     = LightBackground,
        surface        = LightSurface,
        onPrimary      = Color.White,
        onBackground   = Color(0xFF0F172A),
        onSurface      = Color(0xFF0F172A),
    )
    val darkScheme = darkColorScheme(
        primary        = DarkPrimary,
        secondary      = DarkSecondary,
        background     = DarkBackground,
        surface        = DarkSurface,
        onPrimary      = Color.White,
        onBackground   = Color(0xFFF1F5F9),
        onSurface      = Color(0xFFF1F5F9),
    )

    MaterialTheme(colorScheme = if (isDark) darkScheme else lightScheme) {
        Dashboard(classifier = classifier, isDark = isDark, onToggleTheme = { isDark = !isDark })
    }
}

// ── Liquid glass card modifier ────────────────────────────────
@Composable
fun Modifier.glassCard(isDark: Boolean): Modifier {
    val glassColor = if (isDark) Color(0x33FFFFFF) else Color(0xBBFFFFFF)
    val borderColor = if (isDark) Color(0x44FFFFFF) else Color(0x88FFFFFF)
    return this
        .clip(RoundedCornerShape(24.dp))
        .background(glassColor)
        .border(1.dp, borderColor, RoundedCornerShape(24.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(classifier: ImageClassifier, isDark: Boolean, onToggleTheme: () -> Unit) {
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var classificationResult by remember { mutableStateOf<ClassificationResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showHeatmap by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Animated gradient background
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "grad"
    )

    val bgGradient = if (isDark) Brush.radialGradient(
        colors = listOf(Color(0xFF1E1B4B), DarkBackground, Color(0xFF0F172A)),
        center = Offset(300f + gradientOffset * 200f, 400f),
        radius = 900f
    ) else Brush.radialGradient(
        colors = listOf(Color(0xFFDBEAFE), LightBackground, Color(0xFFEFF6FF)),
        center = Offset(300f + gradientOffset * 200f, 400f),
        radius = 900f
    )

    fun reset() {
        selectedBitmap = null; classificationResult = null
        isLoading = false; showHeatmap = false
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            isLoading = true; classificationResult = null; showHeatmap = false
            coroutineScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val src = ImageDecoder.createSource(context.contentResolver, it)
                        ImageDecoder.decodeBitmap(src) { d, _, _ ->
                            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE; d.isMutableRequired = true
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                    }
                    raw.copy(Bitmap.Config.ARGB_8888, true)
                }
                selectedBitmap = bitmap
                val result = withContext(Dispatchers.Default) { classifier.classify(bitmap) }
                classificationResult = result
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // ── Top bar ──────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text(
                            "Culosis",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else LightPrimary
                        )
                        Text(
                            "AI-Powered TB Diagnosis",
                            fontSize = 13.sp,
                            color = if (isDark) DarkSecondary else LightSecondary
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Theme toggle
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0x33FFFFFF) else Color(0x22005EB8))
                    ) {
                        Text(if (isDark) "☀" else "🌙", fontSize = 18.sp)
                    }
                    // Refresh
                    IconButton(
                        onClick = { reset() },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0x33FFFFFF) else Color(0x22005EB8))
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Reset",
                            tint = if (isDark) Color.White else LightPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Upload button ─────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isDark)
                            Brush.horizontalGradient(listOf(DarkPrimary, Color(0xFF6366F1)))
                        else
                            Brush.horizontalGradient(listOf(LightPrimary, LightInfo))
                    )
                    .clickable(enabled = !isLoading) { launcher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLoading) "Analyzing..." else "Upload X-Ray",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Loading indicator ─────────────────────────────
            AnimatedVisibility(visible = isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = if (isDark) DarkInfo else LightInfo,
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Scanning X-Ray...",
                        color = if (isDark) DarkInfo else LightInfo,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ── X-Ray image card ──────────────────────────────
            AnimatedVisibility(
                visible = selectedBitmap != null,
                enter = fadeIn() + slideInVertically()
            ) {
                selectedBitmap?.let { bmp ->
                    val displayBitmap =
                        if (showHeatmap && classificationResult?.heatmapBitmap != null)
                            classificationResult!!.heatmapBitmap!!
                        else bmp

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(isDark)
                                .padding(8.dp)
                        ) {
                            Image(
                                bitmap = displayBitmap.asImageBitmap(),
                                contentDescription = "X-Ray",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                        }

                        // Heatmap toggle chip
                        classificationResult?.heatmapBitmap?.let {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(
                                        if (showHeatmap)
                                            (if (isDark) DarkDanger else LightDanger).copy(alpha = 0.15f)
                                        else
                                            (if (isDark) Color(0x33FFFFFF) else Color(0x22005EB8))
                                    )
                                    .border(
                                        1.dp,
                                        if (showHeatmap) (if (isDark) DarkDanger else LightDanger)
                                        else (if (isDark) Color(0x44FFFFFF) else LightPrimary.copy(0.3f)),
                                        RoundedCornerShape(50.dp)
                                    )
                                    .clickable { showHeatmap = !showHeatmap }
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = if (showHeatmap) "🔬 Show Original" else "🌡 Show TB Heatmap",
                                    color = if (showHeatmap)
                                        (if (isDark) DarkDanger else LightDanger)
                                    else
                                        (if (isDark) Color.White else LightPrimary),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            // ── Result card ───────────────────────────────────
            AnimatedVisibility(
                visible = classificationResult != null,
                enter = fadeIn() + slideInVertically { it / 2 }
            ) {
                classificationResult?.let { result ->
                    ResultCard(result = result, isDark = isDark)
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun ResultCard(result: ClassificationResult, isDark: Boolean) {
    when {
        result.status.startsWith("Error") -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(isDark)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = result.status,
                    color = if (isDark) DarkSecondary else LightSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        else -> {
            val isAtRisk = result.status == "At Risk"
            val probability = (result.tbProbability * 100).toInt()

            // Pick colors based on probability + theme
            val (statusColor, bgTint, emoji, label) = when {
                !isAtRisk -> if (isDark)
                    listOf(DarkHealthy, DarkHealthy.copy(0.12f), "✓", "Healthy")
                else
                    listOf(LightHealthy, LightHealthy.copy(0.08f), "✓", "Healthy")
                probability in 51..70 -> if (isDark)
                    listOf(DarkWarning, DarkWarning.copy(0.12f), "⚠", "Low Risk")
                else
                    listOf(LightWarning, LightWarning.copy(0.08f), "⚠", "Low Risk")
                else -> if (isDark)
                    listOf(DarkDanger, DarkDanger.copy(0.12f), "🚨", "At Risk")
                else
                    listOf(LightDanger, LightDanger.copy(0.08f), "🚨", "At Risk")
            }

            @Suppress("UNCHECKED_CAST")
            val colorList = listOf(statusColor, bgTint, emoji, label) as List<Any>
            val sColor = colorList[0] as Color
            val sBg   = colorList[1] as Color
            val sEmoji = colorList[2] as String
            val sLabel = colorList[3] as String

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(sBg)
                    .border(1.5.dp, sColor.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {

                    // Status icon circle
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(sColor.copy(alpha = 0.15f))
                            .border(2.dp, sColor.copy(0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(sEmoji, fontSize = 28.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = sLabel,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = sColor
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Probability bar
                    Text(
                        "TB Probability",
                        fontSize = 12.sp,
                        color = if (isDark) DarkSecondary else LightSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (isDark) Color(0x33FFFFFF) else Color(0x22000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(result.tbProbability)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(sColor.copy(0.7f), sColor)
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "$probability%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = sColor
                    )

                    if (isAtRisk && result.heatmapBitmap != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Tap 'Show TB Heatmap' above to view affected regions",
                            fontSize = 12.sp,
                            color = sColor.copy(0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
