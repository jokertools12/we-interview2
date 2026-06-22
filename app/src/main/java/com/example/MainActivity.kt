package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.RecognizerIntent
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null

    private val speechToTextLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                viewModel.appendMockAnswer(spokenText)
            }
        }
    }

    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "we_interview_db"
        ).fallbackToDestructiveMigration().build()
    }

    private val repository by lazy {
        InterviewRepository(db)
    }

    private val viewModel: InterviewViewModel by viewModels {
        InterviewViewModelFactory(repository, application)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize TTS
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale.US
            }
        }

        // Setup speech-to-text and speaker callbacks
        viewModel.ttsSpeaker = { text, isEng ->
            tts?.apply {
                language = if (isEng) java.util.Locale.US else java.util.Locale("ar")
                speak(text, TextToSpeech.QUEUE_FLUSH, null, "mock_q")
            }
        }

        viewModel.speechToTextTrigger = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (viewModel.isEnglish) "en-US" else "ar-EG")
                putExtra(RecognizerIntent.EXTRA_PROMPT, if (viewModel.isEnglish) "Speak your technical answer now..." else "تحدث بإجابتك التقنية الآن...")
            }
            try {
                speechToTextLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Speech recognition is not supported on this device/emulator.", Toast.LENGTH_SHORT).show()
            }
        }

        enableEdgeToEdge()
        setContent {
            val direction = if (viewModel.isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MyApplicationTheme {
                    val currentTab = viewModel.currentTab
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = BackgroundDark,
                        bottomBar = {
                            if (viewModel.activeExam == null) {
                                MainBottomNavigationBar(
                                    currentTab = currentTab,
                                    isEnglish = viewModel.isEnglish,
                                    onTabSelected = { viewModel.navigateTo(it) }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            val activeExam = viewModel.activeExam
                            if (activeExam != null) {
                                ActiveExamScreen(
                                    exam = activeExam,
                                    viewModel = viewModel
                                )
                            } else {
                                when (currentTab) {
                                    AppTab.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                                    AppTab.CONCEPTS -> ConceptsScreen(viewModel = viewModel)
                                    AppTab.EXAM -> ExamSelectionScreen(viewModel = viewModel)
                                    AppTab.SPACED_REPETITION -> SpacedRepetitionScreen(viewModel = viewModel)
                                    AppTab.SAVED_EXAMS -> SavedExamsScreen(viewModel = viewModel)
                                    AppTab.ANALYTICS -> AnalyticsScreen(viewModel = viewModel)
                                    AppTab.MOCK_INTERVIEW -> MockInterviewScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Bottom Navigation ---
@Composable
fun MainBottomNavigationBar(currentTab: AppTab, isEnglish: Boolean, onTabSelected: (AppTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(NavBackground)
            .border(1.dp, Slate800)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(if (isEnglish) "Home" else "الرئيسية", "🏠", currentTab == AppTab.DASHBOARD) { onTabSelected(AppTab.DASHBOARD) }
        NavItem(if (isEnglish) "Concepts" else "المفاهيم", "💡", currentTab == AppTab.CONCEPTS) { onTabSelected(AppTab.CONCEPTS) }
        NavItem(if (isEnglish) "Exams" else "الاختبارات", "📝", currentTab == AppTab.EXAM) { onTabSelected(AppTab.EXAM) }
        NavItem(if (isEnglish) "SRS" else "التكرار", "🔄", currentTab == AppTab.SPACED_REPETITION) { onTabSelected(AppTab.SPACED_REPETITION) }
        NavItem(if (isEnglish) "Archive" else "الأرشيف", "📁", currentTab == AppTab.SAVED_EXAMS) { onTabSelected(AppTab.SAVED_EXAMS) }
        NavItem(if (isEnglish) "Reports" else "التقارير", "📊", currentTab == AppTab.ANALYTICS) { onTabSelected(AppTab.ANALYTICS) }
        NavItem(if (isEnglish) "Mock" else "مقابلة", "🤖", currentTab == AppTab.MOCK_INTERVIEW) { onTabSelected(AppTab.MOCK_INTERVIEW) }
    }
}

@Composable
fun NavItem(label: String, icon: String, isActive: Boolean, onClick: () -> Unit) {
    val color = if (isActive) Indigo400 else Slate500
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .width(60.dp)
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .width(20.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// --- Dashboard Screen ---
@Composable
fun DashboardScreen(viewModel: InterviewViewModel) {
    val analytics by viewModel.analyticsState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var showFormulaSheet by remember { mutableStateOf(false) }
    var showFiberCalc by remember { mutableStateOf(false) }
    var showSubnetTrainer by remember { mutableStateOf(false) }
    var showDailyChallenge by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        HeaderSection(viewModel = viewModel)
        Spacer(modifier = Modifier.height(20.dp))
        
        // Dynamic Readiness Section computed from DB analytics
        DashboardReadinessCard(analytics = analytics)
        
        Spacer(modifier = Modifier.height(16.dp))

        // --- NEW Advanced Tools & Daily Challenge Section ---
        Text(
            text = if (viewModel.isEnglish) "Interactive Telecom Tools & Daily Challenges ⚙️" else "مركز الأدوات والتحديات التفاعلية ⚙️",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Slate200,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Daily Challenge trigger
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Purple500.copy(alpha = 0.15f), Slate800)))
                    .border(1.dp, Purple500.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { showDailyChallenge = true }
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "🎯", fontSize = 22.sp)
                    Text(
                        text = if (viewModel.isEnglish) "Daily Challenge" else "التحدي اليومي",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (viewModel.isEnglish) "Streak: ${viewModel.dailyStreak} 🔥" else "الالتزام: ${viewModel.dailyStreak} أيام 🔥",
                        fontSize = 9.sp,
                        color = Color(0xFFFACC15),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Fiber Calculator trigger
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Cyan500.copy(alpha = 0.15f), Slate800)))
                    .border(1.dp, Cyan400.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { showFiberCalc = true }
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "📡", fontSize = 22.sp)
                    Text(
                        text = if (viewModel.isEnglish) "Fiber Planner" else "حاسبة الفقد",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (viewModel.isEnglish) "Power budget simulator" else "حساب الفقد وميزانية الليزر",
                        fontSize = 9.sp,
                        color = Slate400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // IP Subnetting Trainer trigger
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Emerald500.copy(alpha = 0.15f), Slate800)))
                    .border(1.dp, Emerald400.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { showSubnetTrainer = true }
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "🌐", fontSize = 22.sp)
                    Text(
                        text = if (viewModel.isEnglish) "Subnet Trainer" else "مدرب الشبكات",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (viewModel.isEnglish) "CCNA IP partitions" else "تقسيم عناوين IP المتعددة",
                        fontSize = 9.sp,
                        color = Slate400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        
        // Formula reference sheet launcher row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Indigo600.copy(alpha = 0.2f), Cyan500.copy(alpha = 0.2f))))
                .border(1.dp, Indigo500.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .clickable { showFormulaSheet = true }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "📐", fontSize = 24.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (viewModel.isEnglish) "Telecom Formulas & Reference" else "أطلس القوانين والمعادلات الهندسية",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (viewModel.isEnglish) "Review Link Budget, Shannon Capacity, and Power formulas" else "عناوين تقسيم الشبكات وحسابات فقد الفايبر وطاقة السنترالات",
                        fontSize = 10.sp,
                        color = Slate400
                    )
                }
                Text(text = if (viewModel.isEnglish) "Open ➔" else "عرض ➔", fontSize = 10.sp, color = Cyan400, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (viewModel.isEnglish) "Primary Engineering Sectors" else "المحاور الهندسية الأساسية",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Slate200,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TechnicalCategoryCard(
                categoryName = if (viewModel.isEnglish) "Fiber-Optic Networks" else "شبكات الألياف البصرية (Fiber)",
                iconBadge = "📡",
                successRate = analytics.fiberReadiness,
                description = if (viewModel.isEnglish) "Study FTTH, GPON, OTDR, and optical splicing technologies." else "دراسة FTTH, GPON, OTDR وتقنيات اللحام البصري بكفاءة.",
                colorAccent = Indigo400,
                onGenerateAiClick = { viewModel.generateAiExamForCategory("Fiber") },
                isAiGenerating = viewModel.isGeneratingCategoryAiExam == "Fiber"
            ) {
                viewModel.startExam("Fiber")
            }
            
            TechnicalCategoryCard(
                categoryName = if (viewModel.isEnglish) "Networking Engineering (CCNA)" else "هندسة الشبكات والـ (CCNA)",
                iconBadge = "🌐",
                successRate = analytics.ccnaReadiness,
                description = if (viewModel.isEnglish) "Subnetting math, OSPF routing protocol, and OSI layers." else "حسابات Subnetting, بروتوكول توجيه OSPF ومفاهيم OSI layers.",
                colorAccent = Cyan400,
                onGenerateAiClick = { viewModel.generateAiExamForCategory("CCNA") },
                isAiGenerating = viewModel.isGeneratingCategoryAiExam == "CCNA"
            ) {
                viewModel.startExam("CCNA")
            }
            
            TechnicalCategoryCard(
                categoryName = if (viewModel.isEnglish) "Telecom Power Systems" else "أنظمة القوى والباور (Power)",
                iconBadge = "⚡",
                successRate = analytics.powerReadiness,
                description = if (viewModel.isEnglish) "Generator sets, Rectifiers, cabinet cooling, and battery backups." else "دراسة مولدات السولار، تبريد الكبائن، البطاريات ومحولات الـ Rectifier.",
                colorAccent = Orange400,
                onGenerateAiClick = { viewModel.generateAiExamForCategory("Power") },
                isAiGenerating = viewModel.isGeneratingCategoryAiExam == "Power"
            ) {
                viewModel.startExam("Power")
            }
            
            TechnicalCategoryCard(
                categoryName = if (viewModel.isEnglish) "IT Infrastructure" else "البنية التحتية لتكنولوجيا المعلومات",
                iconBadge = "🖥️",
                successRate = analytics.overallReadiness, // Re-use overall or add it to analytics. 
                description = if (viewModel.isEnglish) "Servers, Virtualization, Linux, Storage and Data Centers." else "السيرفرات، الانظمة الوهمية، أنظمة تشغيل لينكس ومراكز البيانات.",
                colorAccent = Emerald400,
                onGenerateAiClick = { viewModel.generateAiExamForCategory("IT INFRASTRUCTURE") },
                isAiGenerating = viewModel.isGeneratingCategoryAiExam == "IT INFRASTRUCTURE"
            ) {
                viewModel.startExam("IT INFRASTRUCTURE")
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // 🎙️ AI Oral Interview Simulator Launcher Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Indigo600, Purple500)))
                .clickable { viewModel.startMockInterview() }
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (viewModel.isEnglish) "AI Mock Oral Interview Simulator 🎙️" else "محاكي المقابلة الشفهية بالذكاء الاصطناعي 🎙️",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (viewModel.isEnglish) "Vocal Prep" else "تحدي فوري ⚡",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (viewModel.isEnglish)
                        "Hold a technical conversation. Gemini acts as an experienced WE panel interviewer, listening, rating, and grading your descriptive performance with model answers."
                        else "تحدث واكتب بأسلوبك الفني وسيقوم خبير السنترالات بالذكاء الاصطناعي بتقييم إلقائك التقني بلغة الأرقام ومنحك تقارير تدريب حية وتصحيحات مفصلة.",
                    fontSize = 11.sp,
                    color = Indigo100,
                    lineHeight = 16.sp
                )
            }
        }
        

    }

    if (showFormulaSheet) {
        TelecomFormulaReferenceSheet(
            onDismiss = { showFormulaSheet = false },
            isEnglish = viewModel.isEnglish
        )
    }

    if (showFiberCalc) {
        FiberCalculatorSheet(
            isEnglish = viewModel.isEnglish,
            onDismiss = { showFiberCalc = false }
        )
    }

    if (showSubnetTrainer) {
        SubnettingTrainerSheet(
            viewModel = viewModel,
            onDismiss = { showSubnetTrainer = false }
        )
    }

    if (showDailyChallenge) {
        DailyTelecomChallengeSheet(
            viewModel = viewModel,
            onDismiss = { showDailyChallenge = false }
        )
    }
}

@Composable
fun HeaderSection(viewModel: InterviewViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = if (viewModel.isEnglish) "Welcome, Interviewee Candidate" else "أهلاً بك، مهندس المقابلات",
                color = Slate400,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (viewModel.isEnglish) "Telecom Egypt (WE)" else "المصرية للاتصالات WE",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Cyan400
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Language selector button
            Button(
                onClick = { viewModel.setLanguage(!viewModel.isEnglish) },
                colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = if (viewModel.isEnglish) "🇪🇬 AR" else "🇺🇸 EN",
                    fontSize = 10.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sound Effects toggle button with adaptive styling
            IconButton(
                onClick = { viewModel.toggleSound() },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (viewModel.isSoundEnabled) Indigo600.copy(alpha = 0.15f) else Slate800)
                    .border(
                        1.dp,
                        if (viewModel.isSoundEnabled) Indigo500.copy(alpha = 0.4f) else Slate800,
                        CircleShape
                    )
            ) {
                Text(
                    text = if (viewModel.isSoundEnabled) "🔊" else "🔇",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Styled WE Emblem Badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(2.dp, Indigo500.copy(alpha = 0.5f), CircleShape)
                    .background(Brush.horizontalGradient(listOf(Indigo600, Cyan500))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "WE",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun DashboardReadinessCard(analytics: AnalyticsData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDark)
            .border(1.dp, Slate800, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${analytics.overallReadiness}%",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "معدل الجاهزية للمقابلة",
                        fontSize = 12.sp,
                        color = Slate400,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (analytics.overallReadiness >= 75) Emerald500.copy(alpha = 0.15f)
                            else if (analytics.overallReadiness >= 50) Orange500.copy(alpha = 0.15f)
                            else Slate800
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (analytics.overallReadiness >= 75) "جاهز للتقديم"
                               else if (analytics.overallReadiness >= 40) "قيد التطوير"
                               else "ابدأ بالامتحانات",
                        color = if (analytics.overallReadiness >= 75) Emerald400
                                else if (analytics.overallReadiness >= 40) Orange400
                                else Slate300,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Centralized Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Slate800)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(analytics.overallReadiness.toFloat() / 100f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Indigo500, Cyan400)))
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "فايبر", fontSize = 11.sp, color = Slate500)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${analytics.fiberReadiness}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate200)
                }
                Box(modifier = Modifier.height(24.dp).width(1.dp).background(Slate800).align(Alignment.CenterVertically))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "شبكات (CCNA)", fontSize = 11.sp, color = Slate500)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${analytics.ccnaReadiness}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate200)
                }
                Box(modifier = Modifier.height(24.dp).width(1.dp).background(Slate800).align(Alignment.CenterVertically))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "باور وقوى", fontSize = 11.sp, color = Slate500)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${analytics.powerReadiness}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate200)
                }
            }
        }
    }
}

@Composable
fun TechnicalCategoryCard(
    categoryName: String,
    iconBadge: String,
    successRate: Int,
    description: String,
    colorAccent: Color,
    onGenerateAiClick: (() -> Unit)? = null,
    isAiGenerating: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, Slate800, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorAccent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = iconBadge, fontSize = 18.sp)
                    }
                    Text(
                        text = categoryName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                }
                
                Text(
                    text = if (successRate > 0) "Level: $successRate%" else "Not rated",
                    fontSize = 11.sp,
                    color = colorAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = Slate400,
                lineHeight = 16.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Classic Mock Exam • 15 Qs",
                    fontSize = 10.sp,
                    color = Indigo300
                )
                Text(
                    text = "Start ⚡",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            if (onGenerateAiClick != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Slate800, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onGenerateAiClick,
                    enabled = !isAiGenerating,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Indigo600.copy(alpha = 0.8f),
                        disabledContainerColor = Slate800
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isAiGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Generating custom AI nodes...", fontSize = 10.sp, color = Color.White)
                        } else {
                            Text(text = "Generate New Exam (AI) 🤖✨", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardDynamicTip(analytics: AnalyticsData) {
    val TipBody = remember(analytics) {
        val lowest = listOf("Fiber" to analytics.fiberReadiness, "CCNA" to analytics.ccnaReadiness, "Power" to analytics.powerReadiness)
            .filter { it.second > 0 }
            .minByOrNull { it.second }
        
        when {
            lowest == null -> "أهلاً بك! نقترح البدء بحل اختبار متكامل لتحديد نقاط قوتك وصياغة تقارير تحسين مخصصة."
            lowest.first == "Fiber" -> "نصيحة اليوم: ركز على معايير فقد الألياف البصرية وحسابات الـ Splice insertion loss، نلاحظ حاجة لرفع تقييمك البصري."
            lowest.first == "CCNA" -> "نصيحة اليوم: تدرب على تقسيم عناوين الآي بي (Subnetting) وحساب Hosts، هذا المحور حيوي جداً في مقابلات WE."
            else -> "نصيحة اليوم: تعمق بفهم فروق أنظمة الباور وحسابات Rectifier السنترالات لتثبيت معلومات الطاقة الاحتياطية."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Indigo500.copy(alpha = 0.05f))
            .border(1.dp, Indigo500.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Indigo505Accent()),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "💡", fontSize = 20.sp)
            }
            Column {
                Text(
                    text = "توجيه التحسين الذكي اليومي",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Indigo300
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = TipBody,
                    fontSize = 11.sp,
                    color = Slate300,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private fun Indigo505Accent(): Color = Indigo500.copy(alpha = 0.15f)

// --- Exam Selection Screen ---
@Composable
fun ExamSelectionScreen(viewModel: InterviewViewModel) {
    val analytics by viewModel.analyticsState.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "توليد اختبارات هندسية مخصصة",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "اختر المحور والخيارات المناسبة للتعلم والتقييم الفوري والذكي للأداء",
            fontSize = 12.sp,
            color = Slate400,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )
        
        // Massive Interactive Exam button Generator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Indigo600, Cyan500)))
                .clickable { viewModel.startExam("Mixed") }
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "مولد الاختبار التقني الشامل الذكي",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "يولّد 40 سؤالاً مختلطاً بشكل عشوائي تغطي الألياف، الشبكات والباور دفعة واحدة لمحاكاة مقابلة السنترالات الحقيقية.",
                    fontSize = 11.sp,
                    color = Indigo100.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "40 سؤالاً • تقييم فوري ونمذجة ذكية",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = "ابدأ الكل ⚡", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(18.dp))
        
        // ⏱️ Stress Simulation Mode Panel Setup
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (viewModel.isStressModeEnabled) Orange500.copy(alpha = 0.12f) else SurfaceDark)
                .border(
                    width = 1.6.dp, 
                    color = if (viewModel.isStressModeEnabled) Orange400 else Slate800, 
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { 
                    viewModel.isStressModeEnabled = !viewModel.isStressModeEnabled
                    viewModel.toggleSound()
                }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (viewModel.isStressModeEnabled) Orange500 else Slate800),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⏱️", fontSize = 20.sp)
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                Column(modifier = Modifier.weight(1.0f)) {
                    Text(
                        text = if (viewModel.isEnglish) "Stress Simulation Mode" else "نمط محاكاة اضطراب وضغوط اللجان",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (viewModel.isStressModeEnabled) Orange300 else Color.White
                    )
                    Text(
                        text = if (viewModel.isEnglish) 
                            "30s ticking time constraint per question with caution audio warnings." 
                            else "مؤقت 30 ثانية تنازلي إجباري لكل سؤال مع إنذارات ميكانيكية متسارعة.",
                        fontSize = 11.sp,
                        color = Slate400,
                        lineHeight = 15.sp
                    )
                }
                
                Switch(
                    checked = viewModel.isStressModeEnabled,
                    onCheckedChange = { 
                        viewModel.isStressModeEnabled = it
                        if (viewModel.isSoundEnabled) {
                            AudioSynthesizer.playClick()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Orange400,
                        checkedTrackColor = Orange500.copy(alpha = 0.3f),
                        uncheckedThumbColor = Slate500,
                        uncheckedTrackColor = Slate800
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (viewModel.isEnglish) "Explore Specialized Categories" else "تصنيف محاور الاختبار المتخصصة",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Slate300,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                CategoryOptionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "محور الألياف",
                    desc = "GPON, OTDR & Loss",
                    badge = "📡",
                    bgGradient = listOf(Indigo600, Indigo600.copy(alpha = 0.7f))
                ) {
                    viewModel.startExam("Fiber")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.startTimedQuiz("Fiber") },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp).border(0.8.dp, Indigo500, RoundedCornerShape(10.dp)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "⏱️ اختبار مؤقت دقيق", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Cyan400)
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                CategoryOptionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "محور الشبكات",
                    desc = "OSPF, NAT & IPs",
                    badge = "🌐",
                    bgGradient = listOf(Cyan500, Cyan500.copy(alpha = 0.7f))
                ) {
                    viewModel.startExam("CCNA")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.startTimedQuiz("CCNA") },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp).border(0.8.dp, Cyan400, RoundedCornerShape(10.dp)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "⏱️ اختبار مؤقت دقيق", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Cyan400)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                CategoryOptionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "محور القوى",
                    desc = "Rectifiers, ATS & Ground",
                    badge = "⚡",
                    bgGradient = listOf(Orange500, Orange500.copy(alpha = 0.7f))
                ) {
                    viewModel.startExam("Power")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.startTimedQuiz("Power") },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp).border(0.8.dp, Orange400, RoundedCornerShape(10.dp)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "⏱️ اختبار مؤقت دقيق", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Orange300)
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                        Text(text = "💡 معلومات", fontSize = 11.sp, color = Orange400, fontWeight = FontWeight.Bold)
                        Text(
                            text = "المقابلات الفعلية تسألك في الثلاثة بنسب متساوية لتحديد شموليتك الهندسية.",
                            fontSize = 10.sp,
                            color = Slate400,
                            lineHeight = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark.copy(alpha = 0.5f))
                        .border(1.dp, Slate800, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🛡️ معايير المصرية للاتصالات", fontSize = 9.sp, color = Slate400, fontWeight = FontWeight.Medium)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // --- Custom AI Document Exam Generator Section ---
        Text(
            text = if (viewModel.isEnglish) "AI Exam Generator from Files & Pictures" else "توليد امتحان بالذكاء الاصطناعي من ملفاتك وصورك (Gemini)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Slate200,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        
        val context = LocalContext.current
        val contentResolver = context.contentResolver
        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                try {
                    val mimeType = contentResolver.getType(it) ?: "application/octet-stream"
                    if (mimeType.startsWith("image/") || mimeType == "application/pdf") {
                        val inputStream = contentResolver.openInputStream(it)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        var name = "attached_file"
                        contentResolver.query(it, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                name = cursor.getString(nameIndex)
                            }
                        }
                        viewModel.attachedImageBytes = bytes
                        viewModel.attachedImageMimeType = mimeType
                        viewModel.attachedFileName = name
                        viewModel.aiGenerationError = null
                    } else {
                        viewModel.aiGenerationError = if (viewModel.isEnglish) {
                            "Unsupported file type. Please attach a PDF document or a layout image."
                        } else {
                            "عذرًا، نوع الملف غير مدعوم! يرجى إرفاق ملف PDF أو صورة شرح لشفرة الاتصال."
                        }
                    }
                } catch (e: Exception) {
                    viewModel.aiGenerationError = "Failed to read attachment: " + e.localizedMessage
                }
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
                .border(1.dp, Indigo500.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Indigo600.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🤖", fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            text = if (viewModel.isEnglish) "Custom reference-based generation" else "توليد مخصص من نصوصك وصورك المرجعية",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (viewModel.isEnglish) "Attach documents or pictures so Gemini can extract master-class exams" else "الصق نصوص ملفات الـ PDF أو الشروحات ليعمل الذكاء الاصطناعي كمرجع كامل",
                            fontSize = 10.sp,
                            color = Slate400
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = viewModel.userReferenceText,
                    onValueChange = { viewModel.userReferenceText = it },
                    placeholder = {
                        Text(
                            text = if (viewModel.isEnglish) "Paste PDF text, guidelines, or summaries..." else "الصق هنا نصوص ملفاتك أو أسئلتك أو الملخصات لإنتاج الامتحان منها...",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Slate100,
                        unfocusedTextColor = Slate200,
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark,
                        focusedBorderColor = Indigo400,
                        unfocusedBorderColor = Slate800,
                        focusedLabelColor = Indigo300
                    ),
                    trailingIcon = {
                        IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                            Text(text = "📎", fontSize = 20.sp)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
                )
                
                if (viewModel.attachedFileName != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Indigo600.copy(alpha = 0.15f))
                            .border(1.dp, Indigo500.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val bytes = viewModel.attachedImageBytes
                            if (bytes != null && (viewModel.attachedImageMimeType?.startsWith("image/") == true)) {
                                val bitmap = remember(bytes) {
                                    try {
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    } catch (e: java.lang.Exception) {
                                        null
                                    }
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Preview",
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(text = "🖼️", fontSize = 14.sp)
                                }
                            } else {
                                Text(text = "📄", fontSize = 14.sp)
                            }
                            
                            Column {
                                Text(
                                    text = viewModel.attachedFileName ?: "File",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (viewModel.isEnglish) "Attached! Ready for AI extraction ⚡" else "تم الإرفاق ودبوس الملف متصل بالذكاء الاصطناعي ⚡",
                                    fontSize = 8.sp,
                                    color = Cyan400
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = { viewModel.removeAttachment() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text(text = "✕", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                if (viewModel.aiGenerationError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (viewModel.isUsingFallbackAiGen) Indigo500.copy(alpha = 0.1f)
                                else Color(0xFFEF4444).copy(alpha = 0.1f)
                            )
                            .border(
                                1.dp,
                                if (viewModel.isUsingFallbackAiGen) Indigo500.copy(alpha = 0.3f)
                                else Color(0xFFEF4444).copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (viewModel.isUsingFallbackAiGen) "💡" else "⚠️",
                            fontSize = 14.sp
                        )
                        Text(
                            text = viewModel.aiGenerationError ?: "",
                            fontSize = 10.sp,
                            color = if (viewModel.isUsingFallbackAiGen) Indigo300 else Color(0xFFFCA5A5),
                            modifier = Modifier.weight(1f),
                            lineHeight = 14.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                var showQuestionsCountMenu by remember { mutableStateOf(false) }
                var selectedQuestionsCount by remember { mutableStateOf(10) }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Button(
                            onClick = { showQuestionsCountMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(text = (if (viewModel.isEnglish) "Questions: " else "رقم الأسئلة: ") + "$selectedQuestionsCount ▾", fontSize = 10.sp, color = Slate200)
                        }
                        DropdownMenu(
                            expanded = showQuestionsCountMenu,
                            onDismissRequest = { showQuestionsCountMenu = false },
                            modifier = Modifier.background(SurfaceDark).border(1.dp, Slate800)
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (viewModel.isEnglish) "10 Questions" else "10 أسئلة", color = Slate100, fontSize = 11.sp) },
                                onClick = {
                                    selectedQuestionsCount = 10
                                    showQuestionsCountMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (viewModel.isEnglish) "20 Questions" else "20 سؤالاً", color = Slate100, fontSize = 11.sp) },
                                onClick = {
                                    selectedQuestionsCount = 20
                                    showQuestionsCountMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (viewModel.isEnglish) "30 Questions" else "30 سؤالاً", color = Slate100, fontSize = 11.sp) },
                                onClick = {
                                    selectedQuestionsCount = 30
                                    showQuestionsCountMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (viewModel.isEnglish) "40 Questions (Interviewer)" else "40 سؤالاً (الإنترفيو الكامل)", color = Slate100, fontSize = 11.sp) },
                                onClick = {
                                    selectedQuestionsCount = 40
                                    showQuestionsCountMenu = false
                                }
                            )
                        }
                    }
                    
                    if (viewModel.isGeneratingAiExam) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Indigo400)
                            Text(text = if (viewModel.isEnglish) "Analyzing & Generating..." else "جارٍ التحليل والإنتاج...", fontSize = 11.sp, color = Indigo300)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.generateAiExam(selectedQuestionsCount) },
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(text = if (viewModel.isEnglish) "Generate AI Exam ⚡" else "توليد بالذكاء الاصطناعي ⚡", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun CategoryOptionCard(
    modifier: Modifier,
    title: String,
    desc: String,
    badge: String,
    bgGradient: List<Color>,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, Slate800, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgGradient.first().copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = badge, fontSize = 16.sp)
                }
                Text(text = "15 سؤالاً", fontSize = 9.sp, color = Slate500)
            }
            
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate100)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = desc, fontSize = 10.sp, color = Slate400, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// --- Spaced Repetition Screen ---
@Composable
fun SpacedRepetitionScreen(viewModel: InterviewViewModel) {
    val repetitionStates by viewModel.repetitionStates.collectAsStateWithLifecycle()
    val currentQuestion = viewModel.currentSrsQuestion
    val showAnswer = viewModel.showSrsAnswer
    val currentIndex = viewModel.currentSrsIndex
    val totalCount = viewModel.srsFilteredList.size
    var activeFilter by remember { mutableStateOf("All") }

    // Load initial deck if not loaded
    LaunchedEffect(Unit) {
        if (currentQuestion == null) {
            viewModel.loadSrsDeck("All")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = if (viewModel.isEnglish) "Review & Spaced Repetition (SRS)" else "المراجعة والتكرار الذكي (SRS)",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = if (viewModel.isEnglish) "Programmatic revision system designed to solidify complex telecom engineering parameters." else "نظام التكرار المبرمج لترسيخ المفاهيم الهندسية التي تواجه صعوبة بحفظها.",
            fontSize = 12.sp,
            color = Slate400,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )
        
        // Category filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SrsCategoryFilterButton(if (viewModel.isEnglish) "All Decks" else "الكل الشامل", activeFilter == "All") { 
                activeFilter = "All"
                viewModel.loadSrsDeck("All") 
            }
            SrsCategoryFilterButton(if (viewModel.isEnglish) "Fiber Wavelengths" else "ألياف وبصريات", activeFilter == "Fiber") { 
                activeFilter = "Fiber"
                viewModel.loadSrsDeck("Fiber") 
            }
            SrsCategoryFilterButton(if (viewModel.isEnglish) "Networks & Subnets" else "شبكات وrouting", activeFilter == "CCNA") { 
                activeFilter = "CCNA"
                viewModel.loadSrsDeck("CCNA") 
            }
            SrsCategoryFilterButton(if (viewModel.isEnglish) "Power Cabinets" else "باور ومولدات", activeFilter == "Power") { 
                activeFilter = "Power"
                viewModel.loadSrsDeck("Power") 
            }
        }

        if (currentQuestion == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Indigo500)
            }
        } else {
            // Flashcard container
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceDark)
                        .border(1.dp, Slate800, RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Indigo600.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = when (currentQuestion.category) {
                                        "Fiber" -> "📡 الألياف البصرية"
                                        "CCNA" -> "🌐 الشبكات"
                                        else -> "⚡ أنظمة القوى"
                                    },
                                    color = Indigo300,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Text(
                                text = "البطاقة ${currentIndex + 1} من $totalCount",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Text(
                            text = currentQuestion.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate100,
                            lineHeight = 24.sp
                        )
                        
                        // Show beautiful diagram in SRS card if applicable
                        TechnicalDiagram(diagramType = currentQuestion.diagramType, isEnglish = viewModel.isEnglish)
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        if (!showAnswer) {
                            Button(
                                onClick = { viewModel.showAnswerSrs() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = "اضغط لكشف تبرير الإجابة الصحيحة", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Revealed Answer Options and Detailed explanation
                            Text(
                                text = "الخيارات المطروحة:",
                                fontSize = 11.sp,
                                color = Slate400,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            currentQuestion.options.forEachIndexed { i, opt ->
                                val isCorrect = i == currentQuestion.correctIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isCorrect) Emerald500.copy(alpha = 0.1f)
                                            else Slate800.copy(alpha = 0.5f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isCorrect) Emerald500.copy(alpha = 0.3f)
                                            else Slate800,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = opt,
                                        fontSize = 12.sp,
                                        color = if (isCorrect) Emerald400 else Slate300,
                                        fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isCorrect) {
                                        Text(text = "الإجابة الصحيحة ✓", color = Emerald400, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = Slate800)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "💡 التفسير الهندسي المبسّط:",
                                fontSize = 12.sp,
                                color = Cyan400,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Text(
                                text = currentQuestion.explanation,
                                fontSize = 11.sp,
                                color = Slate300,
                                lineHeight = 16.sp
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            Divider(color = Slate800)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "قيّم مدى تذكّرك وفهمك لحفظ المصطلح:",
                                fontSize = 11.sp,
                                color = Slate400,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp),
                                textAlign = TextAlign.Center
                            )
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.rateSrsRecall(1) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = RedButtonAccent()),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("صعب 🔴", fontSize = 11.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { viewModel.rateSrsRecall(2) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = OrangeButtonAccent()),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("متوسط 🟡", fontSize = 11.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { viewModel.rateSrsRecall(3) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenButtonAccent()),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("مُثبَّت 🟢", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun RedButtonAccent(): Color = Color(0xFFEF4444)
private fun OrangeButtonAccent(): Color = Color(0xFFF59E0B)
private fun GreenButtonAccent(): Color = Color(0xFF10B981)

@Composable
fun SrsCategoryFilterButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isActive) Indigo600 else SurfaceDark)
            .border(1.dp, if (isActive) Indigo500 else Slate800, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = if (isActive) Color.White else Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// --- Saved Exams Screen (الأرشيف والأوفلاين) ---
@Composable
fun SavedExamsScreen(viewModel: InterviewViewModel) {
    val list by viewModel.savedExams.collectAsStateWithLifecycle()
    val reviewExam = viewModel.reviewExam

    if (reviewExam != null) {
        // Full exam review detail mode
        SavedExamReviewScreen(exam = reviewExam) {
            viewModel.closeReview()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "الأرشيف والاختبارات المحفوظة",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "راجع تبرير الإجابات وتدريباتك في أي وقت دون اتصال بالإنترنت (Offline Mode).",
                fontSize = 12.sp,
                color = Slate400,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            if (list.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(text = "📂", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "الأرشيف فارغ حالياً",
                            fontSize = 14.sp,
                            color = Slate300,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "بمجرد حل أي اختبار مخصص بالكامل، سيتم حفظ تبريره وعلاماتك تلقائياً هنا وبشكل دائم للأرشفة.",
                            fontSize = 11.sp,
                            color = Slate500,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(list, key = { it.id }) { exam ->
                        SavedExamItem(
                            exam = exam,
                            onReview = { viewModel.openSavedExamForReview(exam) },
                            onDelete = { viewModel.deleteSavedExam(exam.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedExamItem(exam: Exam, onReview: () -> Unit, onDelete: () -> Unit) {
    val dateStr = remember(exam.timestamp) {
        val date = Date(exam.timestamp)
        val sdf = SimpleDateFormat("yyyy/MM/dd (hh:mm a)", Locale("ar"))
        sdf.format(date)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, Slate800, RoundedCornerShape(16.dp))
            .clickable(onClick = onReview)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                when (exam.category) {
                                    "Fiber" -> Indigo500.copy(alpha = 0.15f)
                                    "CCNA" -> Cyan500.copy(alpha = 0.15f)
                                    "Power" -> Orange500.copy(alpha = 0.15f)
                                    else -> Color.Gray.copy(alpha = 0.15f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when (exam.category) {
                                "Fiber" -> "ألياف"
                                "CCNA" -> "شبكات"
                                "Power" -> "قوى"
                                else -> "تقني شامل"
                            },
                            color = when (exam.category) {
                                "Fiber" -> Indigo300
                                "CCNA" -> Cyan400
                                "Power" -> Orange400
                                else -> Slate300
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "النتيجة: ${exam.score}/${exam.totalQuestions}",
                        fontSize = 11.sp,
                        color = Emerald400,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = exam.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate100,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = dateStr,
                    fontSize = 9.sp,
                    color = Slate500
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "مراجعة 🔎",
                    fontSize = 11.sp,
                    color = Indigo400,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف الاختبار",
                        tint = Slate500,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Full Saved Exam Review Detail tab
@Composable
fun SavedExamReviewScreen(exam: Exam, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("← تراجع للأرشيف", color = Slate100, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            
            Text(
                text = "تقييم: ${exam.score}/${exam.totalQuestions}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Emerald400
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = exam.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "مراجعة جميع الأسئلة المحفوظة مع تبريرها الهندسي بالتفصيل ومطابقتها لإجابتك.",
            fontSize = 11.sp,
            color = Slate400,
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Indigo600.copy(alpha = 0.1f))
                        .border(1.dp, Indigo600.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(text = "💡 التقرير والنصيحة الذكية المسجلة:", fontSize = 11.sp, color = Indigo300, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = exam.generalFeedback, fontSize = 11.sp, color = Slate300, lineHeight = 16.sp)
                    }
                }
            }
            
            items(exam.questions) { eq ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = eq.question.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate100,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        eq.question.options.forEachIndexed { i, opt ->
                            val isCorrect = i == eq.question.correctIndex
                            val isSelected = i == eq.selectedIndex
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isCorrect) Emerald500.copy(alpha = 0.08f)
                                        else if (isSelected) Color.Red.copy(alpha = 0.08f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        1.dp,
                                        if (isCorrect) Emerald500.copy(alpha = 0.25f)
                                        else if (isSelected) Color.Red.copy(alpha = 0.25f)
                                        else Slate800,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 12.sp,
                                    color = if (isCorrect) Emerald400
                                            else if (isSelected) Color.Red
                                            else Slate300,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                when {
                                    isCorrect -> {
                                        Text("الإجابة الصحيحة ✓", color = Emerald400, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    isSelected -> {
                                        Text("إجابتك المسجلّة ✗", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Slate800)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(text = "💡 التحليل الهندسي والمبرر للجواب:", fontSize = 11.sp, color = Cyan400, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = eq.question.explanation, fontSize = 11.sp, color = Slate400, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

// --- Active Exam Full-Screen Overlay ---
@Composable
fun ActiveExamScreen(exam: Exam, viewModel: InterviewViewModel) {
    if (exam.isCompleted) {
        ExamResultsScreen(exam = exam, isEnglish = viewModel.isEnglish) {
            viewModel.closeExam()
        }
    } else {
        val currentIdx = viewModel.currentQuestionIndex
        val currentEq = exam.questions.getOrNull(currentIdx)
        val selectedIdx = viewModel.selectedOptionIndex
        val feedbackShown = viewModel.showImmediateFeedback
        
        // ⏱️ Countdown Timer state for Stress Simulation Mode
        var secondsLeft by remember { mutableStateOf(30) }
        LaunchedEffect(currentIdx, feedbackShown) {
            if (viewModel.isStressModeEnabled && !feedbackShown) {
                secondsLeft = 30
                while (secondsLeft > 0) {
                    kotlinx.coroutines.delay(1000L)
                    secondsLeft--
                    if (secondsLeft <= 8 && secondsLeft > 0 && viewModel.isSoundEnabled) {
                        AudioSynthesizer.playTone(listOf(520f), 30, volume = 0.25f)
                    }
                }
                if (secondsLeft == 0 && !feedbackShown) {
                    // Time out
                    viewModel.submitAnswer()
                }
            }
        }

        // ⏱️ Global Countdown Timer for Timed Quiz Mode
        LaunchedEffect(viewModel.isTimedQuizMode, exam.isCompleted) {
            if (viewModel.isTimedQuizMode && !exam.isCompleted) {
                while (viewModel.examTimeRemainingSeconds > 0 && !exam.isCompleted) {
                    kotlinx.coroutines.delay(1000L)
                    if (viewModel.examTimeRemainingSeconds > 0) {
                        viewModel.examTimeRemainingSeconds--
                    }
                    if (viewModel.examTimeRemainingSeconds <= 15 && viewModel.examTimeRemainingSeconds > 0 && viewModel.isSoundEnabled) {
                        // Warning chime
                        AudioSynthesizer.playTone(listOf(600f), 45, volume = 0.22f)
                    }
                }
                if (viewModel.examTimeRemainingSeconds == 0 && !exam.isCompleted) {
                    viewModel.autoSubmitTimedQuiz()
                }
            }
        }
        
        if (currentEq != null) {
            val localizedText = currentEq.question.getLocalizedText(viewModel.isEnglish)
            val localizedOptions = currentEq.question.getLocalizedOptions(viewModel.isEnglish)
            val localizedExplanation = currentEq.question.getLocalizedExplanation(viewModel.isEnglish)
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Header with custom Close and Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exam.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Cyan400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(onClick = { viewModel.closeExam() }) {
                        Text(text = "✕", color = Slate400, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                // Progress Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Slate800)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((currentIdx + 1).toFloat() / exam.questions.size)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(Indigo500, Cyan400)))
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (viewModel.isEnglish) "Question ${currentIdx + 1} of ${exam.questions.size}" else "السؤال ${currentIdx + 1} من ${exam.questions.size}",
                        fontSize = 11.sp,
                        color = Slate300,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (viewModel.isTimedQuizMode && !exam.isCompleted) {
                    val minutes = viewModel.examTimeRemainingSeconds / 60
                    val seconds = viewModel.examTimeRemainingSeconds % 60
                    val timeFormatted = String.format("%02d:%02d", minutes, seconds)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (viewModel.examTimeRemainingSeconds <= 30) Orange500.copy(alpha = 0.15f) else SurfaceDark)
                            .border(1.dp, if (viewModel.examTimeRemainingSeconds <= 30) Orange400 else Indigo500.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "⏱️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (viewModel.isEnglish) "Timed Quiz Timer" else "مؤقت الاختبار التنافسي",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (viewModel.examTimeRemainingSeconds <= 30) Orange300 else Color.White
                            )
                        }
                        
                        Text(
                            text = timeFormatted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewModel.examTimeRemainingSeconds <= 30) Orange400 else Cyan400
                        )
                    }
                }
                
                if (viewModel.isStressModeEnabled && !feedbackShown) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (secondsLeft <= 8) Orange500.copy(alpha = 0.15f) else SurfaceDark)
                            .border(1.dp, if (secondsLeft <= 8) Orange400 else Slate800, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "⏳", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (viewModel.isEnglish) "Time Remaining:" else "الوقت المتبقي للمراجعة والرد:",
                                fontSize = 11.sp,
                                color = if (secondsLeft <= 8) Orange300 else Slate300
                            )
                        }
                        
                        Text(
                            text = if (secondsLeft == 0) {
                                if (viewModel.isEnglish) "TIME EXPIRED!" else "انتهى الوقت!"
                            } else {
                                "$secondsLeft " + (if (viewModel.isEnglish) "sec" else "ثانية")
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (secondsLeft <= 8) Color.Red else Cyan400
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Question Box Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = localizedText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 22.sp
                    )
                }
                
                // Show beautiful illustrative schematic diagram if requested by index
                TechnicalDiagram(diagramType = currentEq.question.diagramType, isEnglish = viewModel.isEnglish)
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 4 Interactive Options Selection
                localizedOptions.forEachIndexed { i, option ->
                    val isSelected = selectedIdx == i
                    val isCorrect = i == currentEq.question.correctIndex
                    
                    // Style differently based on selection & verification states
                    val cardBgColor = when {
                        feedbackShown && isCorrect -> Emerald500.copy(alpha = 0.12f)
                        feedbackShown && isSelected && !isCorrect -> Color.Red.copy(alpha = 0.12f)
                        isSelected -> Indigo555Accent()
                        else -> SurfaceDark
                    }
                    
                    val cardBorderColor = when {
                        feedbackShown && isCorrect -> Emerald400
                        feedbackShown && isSelected && !isCorrect -> Color.Red
                        isSelected -> Indigo400
                        else -> Slate800
                    }
                    
                    val contentColor = when {
                        feedbackShown && isCorrect -> Emerald400
                        feedbackShown && isSelected && !isCorrect -> Color.Red
                        isSelected -> Indigo300
                        else -> Slate200
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBgColor)
                            .border(1.dp, cardBorderColor, RoundedCornerShape(12.dp))
                            .clickable(enabled = !feedbackShown) { viewModel.selectOption(i) }
                            .padding(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                  text = option,
                                  fontSize = 13.sp,
                                  fontWeight = if (isSelected || (feedbackShown && isCorrect)) FontWeight.Bold else FontWeight.Normal,
                                  color = contentColor,
                                  modifier = Modifier.weight(1f)
                            )
                            
                            // Visual tick or cross if verified
                            if (feedbackShown) {
                                if (isCorrect) {
                                    Text(text = if (viewModel.isEnglish) "✓ Correct" else "✓ صح", color = Emerald400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else if (isSelected) {
                                    Text(text = if (viewModel.isEnglish) "✗ Wrong" else "✗ خطأ", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                // Simple bullet point
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, if (isSelected) Indigo400 else Slate500, CircleShape)
                                        .background(if (isSelected) Indigo500 else Color.Transparent)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Explanatory Block after answer submission
                if (feedbackShown) {
                    val answeredCorrectly = (selectedIdx == currentEq.question.correctIndex)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (answeredCorrectly) Emerald500.copy(alpha = 0.05f)
                                else Color.Red.copy(alpha = 0.05f)
                            )
                            .border(
                                1.dp,
                                if (answeredCorrectly) Emerald500.copy(alpha = 0.15f)
                                else Color.Red.copy(alpha = 0.15f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (answeredCorrectly) {
                                        if (viewModel.isEnglish) "✓ Correct! Great job!" else "✓ أحسنت! إجابة صحيحة."
                                    } else {
                                        if (viewModel.isEnglish) "✗ Incorrect. Review the explanation:" else "✗ إجابة خاطئة. انتبه للتفسير:"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (answeredCorrectly) Emerald400 else Orange400
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = localizedExplanation,
                                fontSize = 11.sp,
                                color = Slate300,
                                lineHeight = 16.sp
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            // 🤖 Ask AI feature for this specific question
                            var localAiExplanation by remember { mutableStateOf("") }
                            var isLocalAiLoading by remember { mutableStateOf(false) }
                            val compScope = rememberCoroutineScope()

                            if (localAiExplanation.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceDark).padding(12.dp)) {
                                    Text(
                                        text = localAiExplanation,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        lineHeight = 16.sp
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { 
                                        isLocalAiLoading = true
                                        compScope.launch {
                                            try {
                                                val prompt = "قم بشرح هذا السؤال التقني وإجابته ببساطة وبطريقة تلائم معايير المصرية للاتصالات: \nالسؤال: ${currentEq.question.text} \nالإجابة: ${currentEq.question.options[currentEq.question.correctIndex]}"
                                                localAiExplanation = com.example.data.GeminiClient.explainConcept(prompt, currentEq.question.category)
                                            } catch (e: Exception) {
                                                localAiExplanation = "تعذر الاتصال بالذكاء الاصطناعي."
                                            } finally {
                                                isLocalAiLoading = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(30.dp),
                                    enabled = !isLocalAiLoading,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500.copy(alpha=0.2f))
                                ) {
                                    if (isLocalAiLoading) {
                                        CircularProgressIndicator(color = Cyan400, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else {
                                        Text(text = "🤖", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = if (viewModel.isEnglish) "Ask AI to clarify this" else "اسأل الذكاء الاصطناعي للتوضيح الإضافي",
                                        fontSize = 9.sp,
                                        color = Cyan400,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Button(
                        onClick = { viewModel.nextQuestion() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (currentIdx == exam.questions.size - 1) {
                                if (viewModel.isEnglish) "Show Final Score" else "عرض النتيجة الإجمالية"
                            } else {
                                if (viewModel.isEnglish) "Next Question" else "السؤال التالي"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.submitAnswer() },
                        enabled = selectedIdx != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Indigo600,
                            disabledContainerColor = Slate800
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (viewModel.isEnglish) "Confirm and Verify ⚡" else "التأكيد والتحقق ⚡",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedIdx != null) Color.White else Slate500
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun Indigo555Accent(): Color = Indigo500.copy(alpha = 0.15f)

@Composable
fun ExamResultsScreen(exam: Exam, isEnglish: Boolean, onExit: () -> Unit) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = if (isEnglish) "Exam Completed! 🎉" else "اكتمل الاختبار بنجاح 🎉",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = exam.title,
            fontSize = 12.sp,
            color = Slate400,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )
        
        // Circular percentage score box
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(SurfaceDark, BackgroundDark)))
                .border(2.dp, Brush.horizontalGradient(listOf(Indigo500, Cyan400)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${exam.score} / ${exam.totalQuestions}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                val rate = ((exam.score.toFloat() / exam.totalQuestions) * 100).toInt()
                Text(
                    text = if (isEnglish) "$rate% Success" else "$rate% نجاح",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (rate >= 80) Emerald455() else Orange455()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(28.dp))
        
        // Report evaluation card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = if (isEnglish) "📋 Performance Report & Review:" else "📋 تقرير التقييم ومراجعة الأخطاء المبرمجة:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Cyan400
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = exam.generalFeedback,
                    fontSize = 11.sp,
                    color = Slate300,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) {
                        "Your performance has been analyzed and saved to your Archive and Spaced Repetition decks."
                    } else {
                        "تم التوطين وحفظ هذا الاختبار تلقائيًا في أرشيفك وبطاقات التكرار لضمان المراجعة دون اتصال بالإنترنت في أي وقت."
                    },
                    fontSize = 10.sp,
                    color = Indigo300,
                    lineHeight = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(28.dp))
        
        Button(
            onClick = onExit,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (isEnglish) "Complete & Return to Dashboard" else "حفظ ومتابعة للرئيسية",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun Emerald455(): Color = Emerald400
private fun Orange455(): Color = Orange400

// --- Analytics Screen ---
@Composable
fun AnalyticsScreen(viewModel: InterviewViewModel) {
    val analytics by viewModel.analyticsState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "التقارير التحليلية لمستوى تقدمك",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "خلاصة إنجازاتك في الاختبارات وفاعلية التكرار المبرمج على الأجهزة.",
            fontSize = 12.sp,
            color = Slate400,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )
        
        // Circular progress meters block
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnalyticsValueCard(
                modifier = Modifier.weight(1f),
                title = "جاهزية كاملة",
                value = "${analytics.overallReadiness}%",
                subtitle = "تقدير شامل للمعرفة",
                circleColor = Indigo400
            )
            AnalyticsValueCard(
                modifier = Modifier.weight(1f),
                title = "مجموع الاختبارات",
                value = "${analytics.totalExamsCount}",
                subtitle = "اختبارات منجزة",
                circleColor = Cyan400
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnalyticsValueCard(
                modifier = Modifier.weight(1f),
                title = "بطاقات الـ SRS",
                value = "${analytics.repetitionCardsCount}",
                subtitle = "بطاقات نشطة بالذاكرة",
                circleColor = Orange400
            )
            AnalyticsValueCard(
                modifier = Modifier.weight(1f),
                title = "ثبات التكرار",
                value = if (analytics.repetitionCardsCount > 0) {
                    val pct = ((analytics.correctRepetitions.toFloat() / analytics.repetitionCardsCount) * 100).toInt()
                    "$pct%"
                } else "0%",
                subtitle = "إجابات SRS صحيحة",
                circleColor = Emerald400
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "تقييم الجاهزية حسب الأقسام الهندسية",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Slate200,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AnalyticsProgressRow(title = "الألياف البصرية (Fiber)", percentage = analytics.fiberReadiness, color = Indigo400)
                AnalyticsProgressRow(title = "شبكات الاتصالات (CCNA)", percentage = analytics.ccnaReadiness, color = Cyan400)
                AnalyticsProgressRow(title = "الطاقة والقوى (Power)", percentage = analytics.powerReadiness, color = Orange400)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Deep recommendations generator
        Text(
            text = "تقارير التوصيات المخصصة لـ WE",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Slate200,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Indigo505Accent())
                .border(1.dp, Indigo500.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(text = "📋 خطة الدراسة والتحسين الهندسية:", fontSize = 12.sp, color = Indigo300, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                
                val lowerItem = listOf("أنظمة الفايبر" to analytics.fiberReadiness, "شبكات الـ CCNA" to analytics.ccnaReadiness, "أنظمة الباور وقوى السنترالات" to analytics.powerReadiness)
                    .filter { it.second > 0 }
                    .minByOrNull { it.second }
                
                val recText = when {
                    lowerItem == null -> "يرجى البدء بحل عدة اختبارات تقييمية لنستطيع تحليل أدائك وبث خطة دراسية تلائم خلفيتك الهندسية بدقة."
                    lowerItem.first == "أنظمة الفايبر" -> "أظهرت نتائجك ضعفاً طفيفاً بقسم الفايبر. نقترح التركيز على آليات الـ Splice parameters وقراءة أطوال جهاز OTDR ومعدلات فقد كوابل GPON."
                    lowerItem.first == "شبكات الـ CCNA" -> "أجبت بشكل ممتاز بطرق الباور، لكن نوصيك بالتدرب على تقسيم عناوين الآي بي الفرعية (VLSM subnetting) وهندسة بروتوكول توجيه OSPF لرفع علامتك بقسم الشبكات."
                    else -> "مستواك عالٍ بالشبكات البصرية وفروع الـ Routing، ولكن واجهت صعوبات بحسابات الجسور والاتصال في بطاريات الرصاص الحمضية. نوصي بمراجعة دور Rectifier والتحكم بمولد السولار من خلال الـ ATS."
                }
                
                Text(
                    text = recText,
                    fontSize = 11.sp,
                    color = Slate300,
                    lineHeight = 16.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun AnalyticsValueCard(
    modifier: Modifier,
    title: String,
    value: String,
    subtitle: String,
    circleColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, Slate800, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(circleColor)
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 9.sp, color = Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun AnalyticsProgressRow(title: String, percentage: Int, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = 12.sp, color = Slate200, fontWeight = FontWeight.Medium)
            Text(text = "$percentage%", fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(Slate800)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage.toFloat() / 100f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun TelecomFormulaReferenceSheet(onDismiss: () -> Unit, isEnglish: Boolean) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = if (isEnglish) "Close" else "إغلاق", color = Cyan400, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "📐", fontSize = 22.sp)
                Text(
                    text = if (isEnglish) "Engineering Formulas Reference" else "مرجع المعادلات والقوانين الهندسية",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isEnglish) 
                        "Review these mathematical formulas commonly used in Telecom Egypt interviews and site implementations:"
                    else 
                        "راجع القوانين والمعادلات الأكثر تكراراً في مقابلات المصرية للاتصالات وأعمال التجهيز الهندسي للشبكات والباور:",
                    fontSize = 11.sp,
                    color = Slate300,
                    lineHeight = 16.sp
                )
                
                // 1. Fiber Optic
                FormulaCategoryCard(
                    title = if (isEnglish) "Fiber-Optic & GPON Formulas" else "📡 قوانين وهندسة كوابل الألياف",
                    formulas = listOf(
                        FormulaItem(
                            name = if (isEnglish) "Optical Link Budget (ميزانية الفقد)" else "Optical Link Budget",
                            expression = "P_Loss = (α × L) + (Ns × Ls) + (Nc × Lc)",
                            explanation = if (isEnglish) 
                                "Where α is fiber attenuation (0.2 dB/km @ 1550nm), L is distance, Ns/Ls is splices, Nc/Lc is connector losses."
                            else 
                                "حيث α معدل فقد كابل الألياف (0.2 dB/km عند 1550nm)، L طول الكابل، Ns/Ls عدد وقيمة فقد اللحامات بالصهر، Nc/Lc الفقد بالموصلات."
                        ),
                        FormulaItem(
                            name = if (isEnglish) "dBm to Milliwatt Conversion" else "التحويل من dBm لـ mW الاسمية",
                            expression = "P(mW) = 10^(P(dBm) / 10)",
                            explanation = if (isEnglish)
                                "Converts optical level from logarithmic dBm to linear Milliwatt (e.g., 0 dBm = 1 mW, -10 dBm = 0.1 mW)."
                            else
                                "تحويل من مقياس الطاقة ديسيبل اللوغاريتمي dBm للميللي وات المباشر (مثال: 0 dBm يمثل 1mW، و -10 dBm يمثل 0.1mW)."
                        )
                    )
                )

                // 2. Networking
                FormulaCategoryCard(
                    title = if (isEnglish) "Networking & Information Theory" else "🌐 قوانين شبكات الاتصالات (CCNA)",
                    formulas = listOf(
                        FormulaItem(
                            name = if (isEnglish) "Shannon Capacity (سعة القناة القصوى)" else "Shannon-Hartley Capacity Theorem",
                            expression = "C = B × log₂ (1 + SNR)",
                            explanation = if (isEnglish)
                                "Calculates maximum theoretical data rate in bps. C is capacity, B is bandwidth, SNR is Signal-to-Noise linear ratio."
                            else
                                "حساب أقصى سعة نقل بيانات نظرية خالية من الأخطاء في مسار مشوش. C السعة بالهيرتز، B النطاق الترددي، SNR نسبة الإشارة للضوضاء."
                        ),
                        FormulaItem(
                            name = if (isEnglish) "Nyquist Bit Rate (معدل بت نيكويست)" else "Nyquist Bit Rate",
                            expression = "R = 2 × B × log₂ (M)",
                            explanation = if (isEnglish)
                                "Calculates bit rate for noiseless channel. B is bandwidth, M is the discrete signal level count (e.g., 2 for binary)."
                            else
                                "معدل الإرسال الأقصى في قناة خالية تماماً من التشويش. B العرض الترددي، M مستويات الإشارة المنفصلة."
                        )
                    )
                )

                // 3. Power
                FormulaCategoryCard(
                    title = if (isEnglish) "Power & Battery Backups" else "⚡ أنظمة الباور والطاقة الاحتياطية",
                    formulas = listOf(
                        FormulaItem(
                            name = if (isEnglish) "Battery Autonomy Time (زمن التغطية)" else "Backup Battery Discharge Time",
                            expression = "T = (C × V × η) / P_load",
                            explanation = if (isEnglish)
                                "T is hours, C is capacity in Ah, V is nominal voltage (e.g., -48V), η is efficiency factor, P_load is system power."
                            else
                                "زمن الاحتياط بالساعات لتشغيل الكبائن. C سعة البطاريات بالـ Amp-hour، V الفولتية الاسمية (مثال: -48V)، η معامل الكفاءة، P_load الباور الفعلي للموقع."
                        ),
                        FormulaItem(
                            name = if (isEnglish) "Copper Voltage Drop (الفقد بالنحاس)" else "Voltage Drop Calculation",
                            expression = "V_drop = (2 × L × I) / (A × σ)",
                            explanation = if (isEnglish)
                                "L is cable length, I is current, A is cross section area in mm2, σ is conductivity of copper (approx 58)."
                            else
                                "حساب سقوط الفولتية في الكوابل لتفادي سخونة الأسلاك وضمان كفاءتها. L طول كابل الباور، I التيار الكهربائي، A مساحة المقطع بالـ mm2، σ موصلية النحاس."
                        )
                    )
                )
            }
        },
        containerColor = SurfaceDark,
        textContentColor = Slate200,
        titleContentColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

data class FormulaItem(val name: String, val expression: String, val explanation: String)

@Composable
fun FormulaCategoryCard(title: String, formulas: List<FormulaItem>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundDark)
            .border(1.dp, Slate800, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cyan400)
            
            formulas.forEach { formula ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .padding(10.dp)
                ) {
                    Text(text = formula.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formula.expression,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Cyan400,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formula.explanation,
                        fontSize = 9.sp,
                        color = Slate400,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}

// --- Dynamic Glossary & Interactivity support ---
data class GlossaryItem(
    val term: String,
    val termAr: String,
    val descAr: String,
    val descEn: String,
    val category: String
)

// --- AI Mock Interview Simulator UI ---
@Composable
fun MockInterviewScreen(viewModel: InterviewViewModel) {
    val currentIdx = viewModel.currentMockIndex
    val totalQs = viewModel.activeMockQuestions.size
    val activeQuestion = viewModel.activeMockQuestions.getOrNull(currentIdx)
    val isEnglish = viewModel.isEnglish
    val scrollState = rememberScrollState()

    if (viewModel.showMockSessionSummary) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnglish) "Session Performance Report" else "تقرير جودة الأداء النهائي للجنة 📜",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                IconButton(onClick = { viewModel.navigateTo(AppTab.DASHBOARD) }) {
                    Text(text = "✕", color = Slate400, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(18.dp))
            
            // Overall Score Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Indigo600, Cyan500)))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isEnglish) "OVERALL SUITABILITY SCORE" else "معدل الملاءمة الهندسية الكلي",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Indigo100
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${viewModel.sessionAverageScore}%",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (viewModel.sessionAverageScore >= 85) {
                            if (isEnglish) "Senior Engineer (Highly Recommended)" else "معتمد كمهندس أول (موثق بالتسليم الميداني)"
                        } else if (viewModel.sessionAverageScore >= 70) {
                            if (isEnglish) "Competent Field Lead (Approved)" else "مقبول فني متوسط (مع مراعاة التوصيات)"
                        } else {
                            if (isEnglish) "Review Required" else "بحاجة لمراجعة القوانين وأطلس الاتصالات"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            if (viewModel.isGeneratingSessionReport) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Cyan400)
                        Text(
                            text = if (isEnglish) "AI is auditing response history based on Telecom Egypt standards..." else "الذكاء الاصطناعي يحلل درجات التقرير طبقاً لمعايير المصرية للاتصالات...",
                            fontSize = 11.sp,
                            color = Slate400,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                // Display feedback
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = if (isEnglish) "📋 AUDIT FEEDBACK & REPORT DETAIL" else "📋 تفاصيل تقرير تقييم الجلسة المجمع",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Cyan400
                        )
                        Divider(color = Slate800, thickness = 0.8.dp)
                        
                        Text(
                            text = viewModel.sessionReportFeedback,
                            fontSize = 12.sp,
                            color = Slate200,
                            lineHeight = 18.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = if (isEnglish) "Questions History Review" else "مراجعة تفصيلية للأسئلة والأجوبة السابقة",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate300,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )
                
                viewModel.mockSessionHistory.forEachIndexed { index, item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .border(0.8.dp, Slate800, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEnglish) "Question #${index + 1}" else "السؤال #${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate400
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (item.evaluation.score >= 70) Emerald500.copy(alpha = 0.2f) else Orange500.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${item.evaluation.score}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.evaluation.score >= 70) Emerald400 else Orange400
                                    )
                                }
                            }
                            
                            Text(
                                text = if (isEnglish) item.questionEn else item.questionAr,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            
                            Divider(color = Slate800.copy(alpha = 0.5f), thickness = 0.6.dp)
                            
                            Text(
                                text = if (isEnglish) "Your Answer:" else "إجابتك المقدمة:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate400
                            )
                            Text(
                                text = item.userAnswer,
                                fontSize = 11.sp,
                                color = Slate300,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                            
                            Divider(color = Slate800.copy(alpha = 0.5f), thickness = 0.6.dp)
                            
                            Text(
                                text = if (isEnglish) "Standard Model Answer Reference:" else "المرجع الفني للجواب النموذجي:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Cyan400
                            )
                            Text(
                                text = if (isEnglish) item.evaluation.modelAnswerEn else item.evaluation.modelAnswerAr,
                                fontSize = 11.sp,
                                color = Slate300
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { viewModel.navigateTo(AppTab.DASHBOARD) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Return to Dashboard" else "العودة للوحة القيادة الفنية ➔",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { viewModel.navigateTo(AppTab.DASHBOARD) }) {
                        Text(text = "➔", color = Cyan400, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = if (isEnglish) "AI Mock Board" else "لجنة محبي السنترالات بالـ AI 🎙️",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Orange500.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Question ${currentIdx + 1} of $totalQs" else "السؤال ${currentIdx + 1} من $totalQs",
                        fontSize = 11.sp,
                        color = Orange400,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(18.dp))
            
            if (activeQuestion != null) {
                val questionText = activeQuestion.getLocalizedText(isEnglish)
                
                // Question prompt
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .border(1.6.dp, Slate800, RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🗣️", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEnglish) "Oral Question Promoted by Board Editor:" else "السؤال الشفهي المطروح من لجنة السنترال:",
                                    fontSize = 11.sp,
                                    color = Slate400,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            IconButton(
                                onClick = { viewModel.ttsSpeaker?.invoke(questionText, isEnglish) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Cyan400.copy(alpha = 0.15f))
                            ) {
                                Text("🔊", fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = questionText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 22.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Interactive Input
                Text(
                    text = if (isEnglish) "Describe your answers in technical depth:" else "اكتب إجابتك الهندسية التفصيلية هنا بالتفصيل الميكانيكي:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate300,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
                
                TextField(
                    value = viewModel.mockUserAnswer,
                    onValueChange = { viewModel.mockUserAnswer = it },
                    placeholder = {
                        Text(
                            text = if (isEnglish) 
                                "Include real coordinates, terms, voltages, attenuations, protocols..." 
                                else "اذكر الفتحات، الفولتيات، القيم الاسمية، البروتوكولات والمعادلات ذات الصلة...",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Slate800, RoundedCornerShape(16.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
 
                Spacer(modifier = Modifier.height(8.dp))
 
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Record Button
                    Button(
                        onClick = { viewModel.speechToTextTrigger?.invoke() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo500.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎙️", fontSize = 16.sp)
                            Text(
                                text = if (isEnglish) "Record Answer (Speech)" else "إملاء صوتي شفهي للمقابلة",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
 
                    // Clear Button
                    Button(
                        onClick = { viewModel.mockUserAnswer = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.border(1.dp, Slate800, RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = if (isEnglish) "Clear" else "مسح",
                            fontSize = 11.sp,
                            color = Slate300
                        )
                    }
                }
                
                viewModel.mockInterviewError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = err, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action button
                if (viewModel.isEvaluatingMockAnswer) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate800),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Cyan400,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = if (isEnglish) "AI Board evaluating in depth..." else "الذكاء الاصطناعي يحلل الجوانب الهندسية...",
                                fontSize = 12.sp,
                                color = Slate300,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.submitMockAnswer() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Submit Technical Answer  ➔" else "إرسال الإجابة للمراجعة والتقييم ⚡",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Render evaluation outputs
                viewModel.mockEvaluationResult?.let { result ->
                    val ratingString = when {
                        result.score >= 85 -> if (isEnglish) "Excellent (WE Certified)" else "ممتاز معتمد (مستوى مهندس أول)"
                        result.score >= 70 -> if (isEnglish) "Competent (Good Field Prep)" else "مقبول فني في السنترال"
                        else -> if (isEnglish) "Needs Practice (Review Glossary)" else "بحاجة لمزيد من المذاكرة والمصطلحات"
                    }
                    val feedbackText = if (isEnglish) {
                        "Analysis: ${result.analysisEn}\n\nTip: ${result.tipsEn}"
                    } else {
                        "التحليل الهندسي: ${result.analysisAr}\n\nنصيحة اللجنة: ${result.tipsAr}"
                    }
                    val modelAnswerText = if (isEnglish) result.modelAnswerEn else result.modelAnswerAr
 
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (result.score >= 70) Emerald500.copy(alpha = 0.08f) else Orange500.copy(alpha = 0.08f))
                            .border(
                                width = 1.3.dp,
                                color = if (result.score >= 70) Emerald400 else Orange400,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEnglish) "Mock Board Evaluation Report:" else "تقرير تقييم المهندسين من اللجنة:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (result.score >= 70) Emerald500 else Orange500)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${result.score}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                            
                            Divider(color = Slate800, thickness = 0.8.dp)
                            
                            // Rating level
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "📢", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEnglish) "Rating Level: $ratingString" else "درجة التقييم: $ratingString",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (result.score >= 70) Emerald400 else Orange400
                                )
                            }
                            
                            // Technical analysis Feedback
                            Text(
                                text = feedbackText,
                                fontSize = 11.sp,
                                color = Slate200,
                                lineHeight = 16.sp
                            )
                            
                            // Golden Standard Model Answer
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BackgroundDark)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = if (isEnglish) "🏆 Golden Standard Model Answer:" else "🏆 الجواب المعياري المعتمد لدى لجان التعيين:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Cyan400
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = modelAnswerText,
                                        fontSize = 11.sp,
                                        color = Slate300,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    
                    // Button to jump forward
                    Button(
                        onClick = { viewModel.nextMockQuestion() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (currentIdx == totalQs - 1) Cyan500 else Emerald500),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (currentIdx == totalQs - 1) {
                                if (isEnglish) "Complete Mock & Finish" else "إنهاء محاكي المقابلات بنجاح 🎉"
                            } else {
                                if (isEnglish) "Next Interview Question ➔" else "الانتقال للسؤال التالي باللجنة ➔"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun FiberCalculatorSheet(isEnglish: Boolean, onDismiss: () -> Unit) {
    var length by remember { mutableStateOf(10.0f) }
    var wavelength1550 by remember { mutableStateOf(true) } // true = 1550nm, false = 1310nm
    var splices by remember { mutableStateOf(4.0f) }
    var connectors by remember { mutableStateOf(2.0f) }
    var margin by remember { mutableStateOf(3.0f) }

    val attenuationCoeff = if (wavelength1550) 0.22f else 0.35f
    val fiberLoss = length * attenuationCoeff
    val spliceLoss = splices * 0.05f
    val connectorLoss = connectors * 0.5f
    val totalLoss = fiberLoss + spliceLoss + connectorLoss + margin

    val remainingMargin = 30.0f - totalLoss
    val statusColor = when {
        totalLoss <= 22.0f -> Emerald400
        totalLoss <= 28.0f -> Orange400
        else -> Color.Red
    }

    val statusTextAr = when {
        totalLoss <= 22.0f -> "إشارة ممتازة (آمنة تماماً ومطابقة لمعايير WE GPON)"
        totalLoss <= 28.0f -> "ضمن نطاق الحساسية (مقبول فنيًا لكن يفضّل مراجعة اللحامات)"
        else -> "حرجة جداً! (فقد ضوئي مفرط، قد ينقطع الاتصال بالكامل)"
    }
    val statusTextEn = when {
        totalLoss <= 22.0f -> "Excellent Signal (Safe & fully compliant with WE GPON specs)"
        totalLoss <= 28.0f -> "Within Sensitivity Threshold (Competent but splicing reviews recommended)"
        else -> "Critical Level! (Excessive loss, receiver link will fail)"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = if (isEnglish) "Submit Results" else "اعتماد الحسبة", color = Cyan400, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "📡", fontSize = 24.sp)
                Text(
                    text = if (isEnglish) "Fiber Link Power Budget Calculator" else "آلة حاسبة فقد الفايبر وميزانية طاقة الليزر",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isEnglish) 
                        "Input parameters dynamically to compute total loss for a fiber run in Telecom Egypt FTTH/GPON access. Standard transmit power is +2 dBm, Receiver limit is -28 dBm."
                    else "أدخل معطيات كابل الألياف الضوئية لحساب معدل الفقد الكلي لمسار GPON. مستوى الإشارة الصادرة القياسي هو +2 dBm وحساسية المستقبل هي -28 dBm.",
                    fontSize = 11.sp,
                    color = Slate300,
                    lineHeight = 16.sp
                )

                // Slider Length
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = if (isEnglish) "Fiber Distance" else "طول كابل الفايبر", fontSize = 11.sp, color = Slate400)
                        Text(text = "${String.format("%.1f", length)} km", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Cyan400)
                    }
                    Slider(
                        value = length,
                        onValueChange = { length = it },
                        valueRange = 0.5f..50.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Cyan400,
                            activeTrackColor = Cyan400,
                            inactiveTrackColor = Slate800
                        )
                    )
                }

                // Wavelength selectors
                Column {
                    Text(text = if (isEnglish) "Operating Wavelength" else "الطول الموجي للشبكة", fontSize = 11.sp, color = Slate400, modifier = Modifier.padding(bottom = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (wavelength1550) Cyan500.copy(alpha = 0.2f) else Slate800)
                                .border(1.dp, if (wavelength1550) Cyan400 else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { wavelength1550 = true }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("1550 nm (0.22 dB/km)", fontSize = 11.sp, color = if (wavelength1550) Color.White else Slate300)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!wavelength1550) Cyan500.copy(alpha = 0.2f) else Slate800)
                                .border(1.dp, if (!wavelength1550) Cyan400 else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { wavelength1550 = false }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("1310 nm (0.35 dB/km)", fontSize = 11.sp, color = if (!wavelength1550) Color.White else Slate300)
                        }
                    }
                }

                // Splice slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = if (isEnglish) "Fusion Splices count" else "عدد نقاط لحام الصهر (Fusion Splices)", fontSize = 11.sp, color = Slate400)
                        Text(text = "${splices.toInt()} splices", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Indigo400)
                    }
                    Slider(
                        value = splices,
                        onValueChange = { splices = it },
                        valueRange = 0.0f..15.0f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = Indigo400,
                            activeTrackColor = Indigo400,
                            inactiveTrackColor = Slate800
                        )
                    )
                }

                // Connector slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = if (isEnglish) "Connectors" else "عدد المحولات والمنافذ (Connectors)", fontSize = 11.sp, color = Slate400)
                        Text(text = "${connectors.toInt()} connectors", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Orange400)
                    }
                    Slider(
                        value = connectors,
                        onValueChange = { connectors = it },
                        valueRange = 0.0f..8.0f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = Orange400,
                            activeTrackColor = Orange400,
                            inactiveTrackColor = Slate800
                        )
                    )
                }

                // Safety Margin slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = if (isEnglish) "Safety Margin" else "هامش الأمان الفني المقبول (Margin)", fontSize = 11.sp, color = Slate400)
                        Text(text = "${String.format("%.1f", margin)} dB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate200)
                    }
                    Slider(
                        value = margin,
                        onValueChange = { margin = it },
                        valueRange = 1.0f..5.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Slate300,
                            activeTrackColor = Slate400,
                            inactiveTrackColor = Slate800
                        )
                    )
                }

                // Calculation Results Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.08f))
                        .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = if (isEnglish) "Computed Total Attenuation:" else "معدل الفقد الكلي المحسوب:", fontSize = 12.sp, color = Color.White)
                            Text(
                                text = "${String.format("%.2f", totalLoss)} dB",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }

                        Divider(color = Slate800, thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = if (isEnglish) "Link Budget Margin (to -28dBm):" else "الهامش المتبقي (حتى -28dBm):", fontSize = 11.sp, color = Slate300)
                            Text(
                                text = "${String.format("%.2f", remainingMargin)} dB",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (remainingMargin >= 2.0f) Emerald400 else Color.Red
                            )
                        }

                        Text(
                            text = if (isEnglish) statusTextEn else statusTextAr,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = statusColor
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun SubnettingTrainerSheet(viewModel: InterviewViewModel, onDismiss: () -> Unit) {
    val qText = viewModel.currentSubnetQuestion
    val options = viewModel.currentSubnetOptions
    val correctIndex = viewModel.currentSubnetCorrectIndex
    val explanation = viewModel.currentSubnetExplanation
    val selectedIndex = viewModel.subnetSelectedOptionIndex
    val showFeedback = viewModel.showSubnetImmediateFeedback
    val streakCount = viewModel.subnetSuccessCount
    val isEnglish = viewModel.isEnglish

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = if (isEnglish) "Exit Trainer" else "إنهاء التدريب", color = Slate400)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "🌐", fontSize = 24.sp)
                Column {
                    Text(
                        text = if (isEnglish) "IP Subnetting Practice Box" else "محاكي تقسيم ودراسة الـ Subnetting",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isEnglish) "Streak: $streakCount solved" else "معدل نجاحك الحالي: $streakCount شبكة صحيحة 🔥",
                        fontSize = 10.sp,
                        color = Cyan400,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate800.copy(alpha = 0.4f))
                        .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = qText,
                        fontSize = 12.sp,
                        color = Color.White,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEachIndexed { idx, opt ->
                        val buttonColor = when {
                            showFeedback && idx == correctIndex -> Emerald500.copy(alpha = 0.2f)
                            showFeedback && idx == selectedIndex && idx != correctIndex -> Color.Red.copy(alpha = 0.2f)
                            selectedIndex == idx -> Cyan500.copy(alpha = 0.15f)
                            else -> Slate800
                        }
                        val borderColor = when {
                            showFeedback && idx == correctIndex -> Emerald400
                            showFeedback && idx == selectedIndex && idx != correctIndex -> Color.Red
                            selectedIndex == idx -> Cyan400
                            else -> Slate500.copy(alpha = 0.4f)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(buttonColor)
                                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                .clickable(enabled = !showFeedback) { viewModel.submitSubnetAnswer(idx) }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 11.sp,
                                    color = if (selectedIndex == idx || (showFeedback && idx == correctIndex)) Color.White else Slate200,
                                    fontWeight = if (selectedIndex == idx || (showFeedback && idx == correctIndex)) FontWeight.Bold else FontWeight.Normal
                                )
                                if (showFeedback && idx == correctIndex) {
                                    Text("✔️", fontSize = 11.sp)
                                } else if (showFeedback && idx == selectedIndex && idx != correctIndex) {
                                    Text("❌", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                if (showFeedback) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Indigo500.copy(alpha = 0.08f))
                            .border(1.dp, Indigo500.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = if (isEnglish) "Step-by-Step Breakdown:" else "💡 التخطيط والتحليل للحل بالتكرار:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Indigo300
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = explanation,
                                fontSize = 10.sp,
                                color = Slate200,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { viewModel.generateNewSubnetChallenge() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Deploy Next Subnet Challenge ➔" else "توليد مسألة Subnetting أخرى ➔",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun DailyTelecomChallengeSheet(viewModel: InterviewViewModel, onDismiss: () -> Unit) {
    val q = viewModel.currentDailyQuestion
    val selectedIndex = viewModel.dailySelectedOptionIndex
    val showFeedback = viewModel.showDailyImmediateFeedback
    val streak = viewModel.dailyStreak
    val points = viewModel.dailyPoints
    val completedToday = viewModel.isDailyChallengeCompletedToday
    val isEnglish = viewModel.isEnglish

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = if (isEnglish) "Done" else "تم", color = Cyan400, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🎯", fontSize = 24.sp)
                    Text(
                        text = if (isEnglish) "Daily Telecom Challenge" else "التحدي الهندسي اليومي لـ WE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Slate800)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🔥", fontSize = 16.sp)
                            Text(
                                text = if (isEnglish) "Streak: $streak Days" else "الالتزام المتتالي: $streak أيام",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Orange400
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Slate800)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("⭐", fontSize = 16.sp)
                            Text(
                                text = if (isEnglish) "$points XP" else "$points نقطة خبرة",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFACC15)
                            )
                        }
                    }
                }

                if (completedToday) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Emerald500.copy(alpha = 0.08f))
                            .border(1.dp, Emerald500.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🎉", fontSize = 36.sp)
                            Text(
                                text = if (isEnglish) "You Solved Today's Challenge!" else "لقد حللت تحدي اليوم بنجاح!",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald400
                            )
                            Text(
                                text = if (isEnglish) 
                                    "Your answers were validated successfully. Streak extended by 1 day! Return tomorrow to unlock another Telecom challenge."
                                else "تم اعتماد الإجابة الصحيحة وتوسيع أيام الالتزام بنجاح في سجلاتك. عد غدًا لفتح سؤال فني فائق الذكاء.",
                                fontSize = 11.sp,
                                color = Slate300,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else if (q != null) {
                    Text(
                        text = if (isEnglish) "Answer correctly to gain +25 XP and protect your daily engineering streak!" else "أجب بشكل صحيح لتجني +25 نقطة خبرة وتحافظ على لهيب تحديك المتتالي!",
                        color = Slate400,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate800.copy(alpha = 0.4f))
                            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = q.text,
                            fontSize = 12.sp,
                            color = Color.White,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        q.options.forEachIndexed { idx, opt ->
                            val buttonColor = when {
                                showFeedback && idx == q.correctIndex -> Emerald500.copy(alpha = 0.2f)
                                showFeedback && idx == selectedIndex && idx != q.correctIndex -> Color.Red.copy(alpha = 0.2f)
                                selectedIndex == idx -> Cyan500.copy(alpha = 0.15f)
                                else -> Slate800
                            }
                            val borderColor = when {
                                showFeedback && idx == q.correctIndex -> Emerald400
                                showFeedback && idx == selectedIndex && idx != q.correctIndex -> Color.Red
                                selectedIndex == idx -> Cyan400
                                else -> Slate500.copy(alpha = 0.4f)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(buttonColor)
                                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                    .clickable(enabled = !showFeedback) { viewModel.submitDailyChallengeAnswer(idx) }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = opt,
                                        fontSize = 11.sp,
                                        color = if (selectedIndex == idx || (showFeedback && idx == q.correctIndex)) Color.White else Slate200,
                                        fontWeight = if (selectedIndex == idx || (showFeedback && idx == q.correctIndex)) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (showFeedback && idx == q.correctIndex) {
                                        Text("✔️", fontSize = 11.sp)
                                    } else if (showFeedback && idx == selectedIndex && idx != q.correctIndex) {
                                        Text("❌", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (showFeedback) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Indigo500.copy(alpha = 0.08f))
                                .border(1.dp, Indigo500.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (isEnglish) "Engineering Explanation:" else "💡 الشرح الهندسي:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Indigo300
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = q.explanation,
                                    fontSize = 10.sp,
                                    color = Slate200,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun TechnicalDiagram(diagramType: String?, isEnglish: Boolean) {
    if (diagramType.isNullOrEmpty()) return

    Spacer(modifier = Modifier.height(12.dp))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Slate800, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isEnglish) "📊 Technical Diagram Helper" else "📊 مخطط توضيحي هندسي للمفهوم",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Cyan400,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(BackgroundDark, RoundedCornerShape(12.dp))
                    .border(1.dp, Slate800, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridWidth = 40.dp.toPx()
                    val gridHeight = 40.dp.toPx()
                    for (x in 0 until (size.width / gridWidth).toInt() + 1) {
                        drawLine(
                            color = Slate800.copy(alpha = 0.25f),
                            start = androidx.compose.ui.geometry.Offset(x * gridWidth, 0f),
                            end = androidx.compose.ui.geometry.Offset(x * gridWidth, size.height),
                            strokeWidth = 1f
                        )
                    }
                    for (y in 0 until (size.height / gridHeight).toInt() + 1) {
                        drawLine(
                            color = Slate800.copy(alpha = 0.25f),
                            start = androidx.compose.ui.geometry.Offset(0f, y * gridHeight),
                            end = androidx.compose.ui.geometry.Offset(size.width, y * gridHeight),
                            strokeWidth = 1f
                        )
                    }

                    when (diagramType) {
                        "ohms_law" -> {
                            val cx = size.width / 2f
                            val cy = size.height / 2f - 5f
                            val radius = 50.dp.toPx()
                            
                            drawCircle(
                                color = Indigo500.copy(alpha = 0.15f),
                                radius = radius,
                                center = androidx.compose.ui.geometry.Offset(cx, cy)
                            )
                            drawCircle(
                                color = Indigo500,
                                radius = radius,
                                center = androidx.compose.ui.geometry.Offset(cx, cy),
                                style = Stroke(width = 3.dp.toPx())
                            )
                            drawLine(
                                color = Indigo500,
                                start = androidx.compose.ui.geometry.Offset(cx - radius, cy),
                                end = androidx.compose.ui.geometry.Offset(cx + radius, cy),
                                strokeWidth = 3.dp.toPx()
                            )
                            drawLine(
                                color = Indigo500,
                                start = androidx.compose.ui.geometry.Offset(cx, cy),
                                end = androidx.compose.ui.geometry.Offset(cx, cy + radius),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                        "transformer" -> {
                            val w = size.width
                            val h = size.height
                            
                            drawRoundRect(
                                color = Slate800,
                                topLeft = androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.15f),
                                size = androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.65f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                                style = Stroke(width = 12.dp.toPx())
                            )
                            
                            val primaryX = w * 0.3f
                            val secondaryX = w * 0.7f
                            val startY = h * 0.25f
                            val endY = h * 0.7f
                            
                            for (i in 0..4) {
                                val cy1 = startY + i * (endY - startY) / 4f
                                drawArc(
                                    color = Cyan400,
                                    startAngle = -90f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft = androidx.compose.ui.geometry.Offset(primaryX - 12.dp.toPx(), cy1 - 8.dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size(24.dp.toPx(), 16.dp.toPx()),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                                drawArc(
                                    color = Orange400,
                                    startAngle = 90f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft = androidx.compose.ui.geometry.Offset(secondaryX - 12.dp.toPx(), cy1 - 8.dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size(24.dp.toPx(), 16.dp.toPx()),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        }
                        "ats_switch" -> {
                            val w = size.width
                            val h = size.height
                            
                            drawCircle(
                                color = Slate500,
                                radius = 20f,
                                center = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.4f)
                            )
                            drawCircle(
                                color = Orange400,
                                radius = 20f,
                                center = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.4f)
                            )
                            drawCircle(
                                color = Cyan400,
                                radius = 25f,
                                center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.75f)
                            )
                            
                            drawLine(
                                color = Slate500,
                                start = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.4f),
                                end = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.45f),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = Orange400.copy(alpha = 0.5f),
                                start = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.4f),
                                end = androidx.compose.ui.geometry.Offset(w * 0.58f, h * 0.45f),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = Cyan400,
                                start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.75f),
                                end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.55f),
                                strokeWidth = 3.dp.toPx()
                            )
                            drawLine(
                                color = Emerald400,
                                start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.55f),
                                end = androidx.compose.ui.geometry.Offset(w * 0.41f, h * 0.44f),
                                strokeWidth = 4.dp.toPx()
                            )
                        }
                        "rectifier" -> {
                            val w = size.width
                            val h = size.height
                            
                            drawCircle(
                                color = Color.Gray,
                                radius = 18f,
                                center = androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.4f)
                            )
                            drawArc(
                                color = Color.White,
                                startAngle = 0f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(w * 0.15f - 8f, h * 0.4f - 6f),
                                size = androidx.compose.ui.geometry.Size(16f, 10f),
                                style = Stroke(width = 2f)
                            )
                            drawLine(
                                color = Slate500,
                                start = androidx.compose.ui.geometry.Offset(w * 0.15f + 22f, h * 0.4f),
                                end = androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.4f),
                                strokeWidth = 2f
                            )
                            drawRoundRect(
                                color = Indigo500.copy(alpha = 0.2f),
                                topLeft = Offset(w * 0.35f, h * 0.2f),
                                size = Size(w * 0.3f, h * 0.5f),
                                cornerRadius = CornerRadius(8f, 8f)
                            )
                            drawRoundRect(
                                color = Indigo500,
                                topLeft = Offset(w * 0.35f, h * 0.2f),
                                size = Size(w * 0.3f, h * 0.5f),
                                cornerRadius = CornerRadius(8f, 8f),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawLine(
                                color = Indigo300,
                                start = Offset(w * 0.42f, h * 0.45f),
                                end = Offset(w * 0.58f, h * 0.45f),
                                strokeWidth = 3f
                            )
                            drawLine(
                                color = Orange400,
                                start = Offset(w * 0.65f + 5f, h * 0.4f),
                                end = Offset(w * 0.85f, h * 0.4f),
                                strokeWidth = 3f
                            )
                            drawLine(
                                color = Orange400,
                                start = Offset(w * 0.75f, h * 0.4f),
                                end = Offset(w * 0.75f, h * 0.7f),
                                strokeWidth = 2f
                            )
                            drawRoundRect(
                                color = Emerald500.copy(alpha = 0.2f),
                                topLeft = Offset(w * 0.68f, h * 0.72f),
                                size = Size(w * 0.15f, h * 0.18f),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                        "battery_series" -> {
                            val w = size.width
                            val h = size.height
                            val startX = w * 0.15f
                            val bWidth = w * 0.14f
                            val spacing = w * 0.05f
                            
                            for (i in 0..3) {
                                val bx = startX + i * (bWidth + spacing)
                                val by = h * 0.35f
                                
                                drawRoundRect(
                                    color = Emerald500.copy(alpha = 0.15f),
                                    topLeft = Offset(bx, by),
                                    size = Size(bWidth, h * 0.35f),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                                drawRoundRect(
                                    color = Emerald400,
                                    topLeft = Offset(bx, by),
                                    size = Size(bWidth, h * 0.35f),
                                    cornerRadius = CornerRadius(6f, 6f),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawRect(
                                    color = Emerald400,
                                    topLeft = Offset(bx + bWidth/2f - 6f, by - 6f),
                                    size = Size(12f, 6f)
                                )
                                if (i < 3) {
                                    val nextBx = startX + (i + 1) * (bWidth + spacing)
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(bx + bWidth, by + h * 0.18f),
                                        end = Offset(nextBx, by + h * 0.18f),
                                        strokeWidth = 2f
                                    )
                                }
                            }
                        }
                        "gpon_split" -> {
                            val w = size.width
                            val h = size.height
                            
                            drawRoundRect(
                                color = Indigo600.copy(alpha = 0.3f),
                                topLeft = Offset(w * 0.08f, h * 0.32f),
                                size = Size(w * 0.18f, h * 0.36f),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                            drawRoundRect(
                                color = Indigo300,
                                topLeft = Offset(w * 0.08f, h * 0.32f),
                                size = Size(w * 0.18f, h * 0.36f),
                                cornerRadius = CornerRadius(6f, 6f),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawLine(
                                color = Cyan400,
                                start = androidx.compose.ui.geometry.Offset(w * 0.26f, h * 0.5f),
                                end = androidx.compose.ui.geometry.Offset(w * 0.48f, h * 0.5f),
                                strokeWidth = 3.dp.toPx()
                            )
                            
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(w * 0.48f, h * 0.4f)
                                lineTo(w * 0.58f, h * 0.5f)
                                lineTo(w * 0.48f, h * 0.6f)
                                close()
                            }
                            drawPath(
                                path = path,
                                color = Purple500
                            )
                            
                            val branchDestinations = listOf(h * 0.22f, h * 0.5f, h * 0.78f)
                            branchDestinations.forEach { destY ->
                                drawLine(
                                    color = Cyan400,
                                    start = androidx.compose.ui.geometry.Offset(w * 0.58f, h * 0.5f),
                                    end = androidx.compose.ui.geometry.Offset(w * 0.78f, destY),
                                    strokeWidth = 1.8f.dp.toPx()
                                )
                                drawCircle(
                                    color = Emerald500,
                                    radius = 12f,
                                    center = androidx.compose.ui.geometry.Offset(w * 0.78f + 12f, destY)
                                )
                            }
                        }
                        "otdr_curve" -> {
                            val w = size.width
                            val h = size.height
                            
                            val curvePath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(w * 0.05f, h * 0.85f)
                                lineTo(w * 0.12f, h * 0.15f)
                                lineTo(w * 0.16f, h * 0.5f)
                                lineTo(w * 0.45f, h * 0.65f)
                                lineTo(w * 0.49f, h * 0.35f)
                                lineTo(w * 0.53f, h * 0.72f)
                                lineTo(w * 0.82f, h * 0.83f)
                                lineTo(w * 0.82f, h * 0.9f)
                                lineTo(w * 0.95f, h * 0.9f)
                            }
                            drawPath(
                                path = curvePath,
                                color = Orange400,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                        "ospf_topology" -> {
                            val w = size.width
                            val h = size.height
                            
                            val nodeA = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.35f)
                            val nodeB = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.35f)
                            val nodeC = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.75f)
                            
                            drawLine(color = Indigo400, start = nodeA, end = nodeB, strokeWidth = 2.dp.toPx())
                            drawLine(color = Indigo400, start = nodeB, end = nodeC, strokeWidth = 2.dp.toPx())
                            drawLine(color = Indigo400, start = nodeC, end = nodeA, strokeWidth = 2.dp.toPx())
                            
                            drawCircle(color = SurfaceDark, radius = 28f, center = nodeA)
                            drawCircle(color = Slate200, radius = 28f, center = nodeA, style = Stroke(width = 2.dp.toPx()))
                            
                            drawCircle(color = SurfaceDark, radius = 28f, center = nodeB)
                            drawCircle(color = Slate200, radius = 28f, center = nodeB, style = Stroke(width = 2.dp.toPx()))
                            
                            drawCircle(color = SurfaceDark, radius = 28f, center = nodeC)
                            drawCircle(color = Slate200, radius = 28f, center = nodeC, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                }

                when (diagramType) {
                    "ohms_law" -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "V",
                                color = Orange400,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 34.dp)
                            )
                            Text(
                                text = "I",
                                color = Cyan400,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = 120.dp, bottom = 54.dp)
                            )
                            Text(
                                text = "R",
                                color = Purple500,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 120.dp, bottom = 54.dp)
                            )
                            Text(
                                text = "V = I × R",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                            )
                        }
                    }
                    "transformer" -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = if (isEnglish) "Primary Side\nN1 (V1)" else "الملف الابتدائي\nN1 (V1 AC)",
                                color = Cyan400,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                            )
                            Text(
                                text = "V1 / V2 = N1 / N2",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                            )
                            Text(
                                text = if (isEnglish) "Secondary Side\nN2 (V2)" else "الملف الثانوي\nN2 (V2 AC)",
                                color = Orange400,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                            )
                        }
                    }
                    "ats_switch" -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = if (isEnglish) "Commercial Grid" else "طاقة البلدية AC",
                                color = Slate300,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 20.dp)
                            )
                            Text(
                                text = if (isEnglish) "Generator FEED" else "المولد الاحتياطي",
                                color = Orange300,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopEnd).padding(end = 32.dp, top = 20.dp)
                            )
                            Text(
                                text = if (isEnglish) "ATS Switch (Relay Active)" else "مفتاح التحويل ATS - مغذّي الخط",
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                            )
                            Text(
                                text = if (isEnglish) "Telecom Loads (-48V rect.)" else "أحمال الاتصالات وفلترة الجهد",
                                color = Cyan400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                            )
                        }
                    }
                    "rectifier" -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "AC Input",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 22.dp)
                            )
                            Text(
                                text = if (isEnglish) "Rectifier System\n(AC ➔ DC Converter)" else "جهاز الموحد Rectifier\n(توليد جهد مستمر)",
                                color = Indigo300,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center).padding(bottom = 10.dp)
                            )
                            Text(
                                text = "Outputs: -48V DC",
                                color = Orange400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 24.dp)
                            )
                            Text(
                                text = if (isEnglish) "Float Backup Batteries (24x 2V)" else "البطاريات الاحتياطية (تغذية عائمة)",
                                color = Emerald400,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 8.dp)
                            )
                        }
                    }
                    "battery_series" -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = if (isEnglish) "Series Accumulative Voltage (24 Cells connected)" else "تجميع الجهد المتتالي بالتسلسل (24 خلية × 2 فولت)",
                                color = Emerald400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                            )
                            Text(
                                text = "V_total = 2 + 2 + 2 + ... = 48V DC",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                            )
                        }
                    }
                    "gpon_split" -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "OLT (Central)",
                                color = Indigo300,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp, bottom = 42.dp)
                            )
                            Text(
                                text = if (isEnglish) "1:N Passive Splitter" else "مقسم سلبي 1:N Splitter",
                                color = Purple500,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp)
                            )
                            Text(
                                text = "ONT (Client)",
                                color = Emerald400,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, bottom = 42.dp)
                            )
                        }
                    }
                    "otdr_curve" -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = if (isEnglish) "OTDR Attenuation Trace Curve (dB vs Distance)" else "منحنى الوهن وجدول الأحداث (النبضة الضوئية والانعكاس)",
                                color = Orange400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                            )
                            Text(
                                text = if (isEnglish) "Initial Connect" else "موصل السنترال",
                                color = Slate400,
                                fontSize = 8.sp,
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp, bottom = 32.dp)
                            )
                            Text(
                                text = if (isEnglish) "Fault/Reflective" else "حدث انعكاسي/كسر",
                                color = Orange300,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center).padding(bottom = 32.dp)
                            )
                            Text(
                                text = if (isEnglish) "Noise Floor" else "مستوى الضجيج والنهاية",
                                color = Slate500,
                                fontSize = 8.sp,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 42.dp, bottom = 22.dp)
                            )
                        }
                    }
                    "ospf_topology" -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "Router A",
                                color = Slate100,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopStart).padding(start = 58.dp, top = 16.dp)
                            )
                            Text(
                                text = "Router B",
                                color = Slate100,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopEnd).padding(end = 58.dp, top = 16.dp)
                            )
                            Text(
                                text = "Router C",
                                color = Slate100,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                            )
                            Text(
                                text = "OSPF Shortest Path First",
                                color = Indigo300,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Basic Concepts with AI Search & Explanation Screen ---
@Composable
fun ConceptsScreen(viewModel: InterviewViewModel) {
    val isEng = viewModel.isEnglish
    val selectedConcept = viewModel.selectedConcept
    
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    
    val filteredConcepts = viewModel.getFilteredConcepts().filter {
        selectedCategoryFilter == "All" || it.category == selectedCategoryFilter
    }

    if (selectedConcept != null) {
        ConceptDetailView(
            concept = selectedConcept,
            viewModel = viewModel,
            onBack = { viewModel.selectConcept(null) }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isEng) "Core Concepts & AI Guide" else "المفاهيم الأساسية وشرح الـ AI 💡",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isEng) "Master fundamental telecom systems & prepare for interviews" 
                               else "اشحن رصيدك المعرفي بأدق الأسس الهندسية والتقنية المطلوبة بالامتحان",
                        fontSize = 12.sp,
                        color = Slate400,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                }
            }

            // Search Bar
            val searchVal = viewModel.searchConceptQuery
            OutlinedTextField(
                value = searchVal,
                onValueChange = { viewModel.setConceptSearch(it) },
                placeholder = {
                    Text(
                        text = if (isEng) "Search concepts, formulas, keywords..." else "ابحث عن مفهوم، قانون، أو مصطلح معين...",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                trailingIcon = {
                    if (searchVal.isNotEmpty()) {
                        Text(
                            text = "✕",
                            color = Slate300,
                            modifier = Modifier
                                .clickable { viewModel.setConceptSearch("") }
                                .padding(8.dp)
                        )
                    } else {
                        Text(text = "🔍", modifier = Modifier.padding(8.dp))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate200,
                    focusedContainerColor = BackgroundDark,
                    unfocusedContainerColor = BackgroundDark,
                    focusedBorderColor = Indigo400,
                    unfocusedBorderColor = Slate800
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Category quick tags
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val listCats = listOf(
                    "All" to (if (isEng) "All" else "الكل"),
                    "Fiber" to (if (isEng) "Fiber" else "الفايبر"),
                    "CCNA" to (if (isEng) "Networks" else "الشبكات CCNA"),
                    "Power" to (if (isEng) "Power" else "الباور والقوى"),
                    "IT INFRASTRUCTURE" to (if (isEng) "IT Infrastructure" else "البنية التحتية IT")
                )
                listCats.forEach { (catId, catLabel) ->
                    val isSelected = selectedCategoryFilter == catId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Indigo500 else Slate800)
                            .clickable { selectedCategoryFilter = catId }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = catLabel,
                            color = if (isSelected) Color.White else Slate300,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredConcepts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(text = "🔎", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isEng) "No matching concepts found." else "لم نعثر على مفاهيم مطابقة لبحثك.",
                            color = Slate300,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredConcepts) { concept ->
                        ConceptCardItem(concept = concept, isEng = isEng) {
                            viewModel.selectConcept(concept)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConceptCardItem(concept: BasicConcept, isEng: Boolean, onClick: () -> Unit) {
    val pillBg = when (concept.category) {
        "Fiber" -> Color(0xFF4A148C) // Purple900
        "CCNA" -> Indigo500
        else -> Color(0xFFE65100) // Amber900
    }
    val pillLabel = when (concept.category) {
        "Fiber" -> if (isEng) "Fiber" else "ألياف ضوئية"
        "CCNA" -> if (isEng) "CCNA" else "شبكات"
        else -> if (isEng) "Power" else "قوى وباور"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = pillLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(text = "💡", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (isEng) concept.titleEn else concept.titleAr,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isEng) concept.summaryEn else concept.summaryAr,
                fontSize = 12.sp,
                color = Slate300,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEng) "Study & Ask AI ➔" else "اقرأ المفهوم واسأل الذكاء الاصطناعي ➔",
                    color = Indigo300,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ConceptDetailView(concept: BasicConcept, viewModel: InterviewViewModel, onBack: () -> Unit) {
    val isEng = viewModel.isEnglish
    val scrollState = rememberScrollState()
    
    // Technical loading phrases
    val explanationLoadingPhrases = listOf(
        if (isEng) "Analyzing core circuit standards..." else "جاري استعراض القوام الفنية من أرشيف السنترال...",
        if (isEng) "Engaging Gemini telecom models..." else "جاري مواءمة خوارزمية Gemini مع لوائح المقابلات الفنية لشركة وي...",
        if (isEng) "Drafting interview cheat-sheets..." else "جاري صياغة النصائح العملية والخدع البرمجية لتخطي السؤال بامتياز..."
    )
    var activeLoadingPhraseIndex by remember { mutableStateOf(0) }
    
    LaunchedEffect(viewModel.isExplainingConcept) {
        if (viewModel.isExplainingConcept) {
            activeLoadingPhraseIndex = 0
            while (viewModel.isExplainingConcept) {
                kotlinx.coroutines.delay(2000)
                activeLoadingPhraseIndex = (activeLoadingPhraseIndex + 1) % explanationLoadingPhrases.size
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Simple Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "➔",
                fontSize = 20.sp,
                color = Color.White,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(8.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isEng) "Concept Exploration" else "تصفح المفهوم الهرمي",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // Category + Title
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (concept.category == "Fiber") Color(0xFF4A148C) else if (concept.category == "CCNA") Indigo500 else Color(0xFFE65100))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = concept.category,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = concept.titleAr,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = concept.titleEn,
                fontSize = 14.sp,
                color = Slate400,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Substantial Static local details
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEng) "Summary Overview" else "شرح أساسي مبسط",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Indigo300
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isEng) concept.detailedExplanationEn else concept.detailedExplanationAr,
                        fontSize = 13.sp,
                        color = Slate100,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // If a diagram exists, present it in the center!
            if (concept.diagramType != null) {
                Text(
                    text = if (isEng) "Technical Schematic" else "الرسم البياني للتوصيل والقوانين 📊",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                ) {
                    TechnicalDiagram(diagramType = concept.diagramType, isEnglish = isEng)
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Gemini Interactive Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate800, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEng) "AI Deep-Dive Explainer" else "الشرح الموسع بالذكاء الاصطناعي 🤖",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700) // Golden Accent
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1B4B))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "Gemini Flash", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isEng) "Query Gemini to compile highly specialized Egyptian telecom interview questions, standards, and model responses for this concept."
                               else "انقر لاستقصاء نموذج Gemini لتقديم إجابة نموذجية معدة خصيصاً لاجتياز المقابلة الفنية والأسئلة الحرجة بشركة وي (WE).",
                        fontSize = 11.sp,
                        color = Slate300,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (viewModel.isExplainingConcept) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Indigo400)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = explanationLoadingPhrases[activeLoadingPhraseIndex],
                                fontSize = 11.sp,
                                color = Slate300,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (viewModel.conceptAiExplanation.isEmpty()) {
                        Button(
                            onClick = { viewModel.fetchAiExplanationForSelectedConcept() },
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isEng) "⚡ Generate AI Explanation" else "⚡ استعراض شرح الذكاء الاصطناعي الفوري",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        // We have the explanation rendered beautifully!
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEng) "Gemini Analysis Result:" else "تحليل الذكاء الاصطناعي والأسئلة المتوقعة:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.LightGray
                                    )
                                    // Speak Button
                                    Button(
                                        onClick = { 
                                            viewModel.ttsSpeaker?.invoke(viewModel.conceptAiExplanation, isEng)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text(text = "🔊 استمع للشرح الصوتي", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = viewModel.conceptAiExplanation,
                                    fontSize = 12.sp,
                                    color = Slate100,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Retry/Refresh Option
                        Button(
                            onClick = { viewModel.fetchAiExplanationForSelectedConcept() },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isEng) "↻ Regenerate Explanation" else "↻ تحديث الشرح وإعادة توليد النطاق",
                                fontSize = 11.sp,
                                color = Slate200
                            )
                        }
                    }
                }
            }
        }
    }
}
