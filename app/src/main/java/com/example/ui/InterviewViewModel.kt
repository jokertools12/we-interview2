package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppTab {
    DASHBOARD,
    EXAM,
    SPACED_REPETITION,
    SAVED_EXAMS,
    ANALYTICS,
    MOCK_INTERVIEW
}

data class ReferenceDocument(
    val id: String,
    val name: String,
    val type: String, // "pdf" or "image"
    val sizeStr: String,
    val description: String,
    val descriptionAr: String,
    val isPreloaded: Boolean,
    val fileContentSample: String,
    val bytesField: ByteArray? = null,
    val mimeTypeField: String? = null
)

class InterviewViewModel(
    private val repository: InterviewRepository,
    private val application: Application
) : ViewModel() {

    var isEnglish by mutableStateOf(false)
        private set

    // Speak and Recording capabilities
    var ttsSpeaker: ((String, Boolean) -> Unit)? = null
    var speechToTextTrigger: (() -> Unit)? = null

    fun appendMockAnswer(text: String) {
        mockUserAnswer = if (mockUserAnswer.isEmpty()) text else "$mockUserAnswer $text"
    }

    // --- Daily Telecom Challenge State ---
    var dailyStreak by mutableStateOf(3)
    var dailyPoints by mutableStateOf(150)
    var isDailyChallengeCompletedToday by mutableStateOf(false)
    var currentDailyQuestion by mutableStateOf<Question?>(null)
    var dailySelectedOptionIndex by mutableStateOf<Int?>(null)
    var showDailyImmediateFeedback by mutableStateOf(false)
    
    fun initDailyChallenge() {
        val sharedPrefs = application.getSharedPreferences("we_daily_prefs", Context.MODE_PRIVATE)
        dailyStreak = sharedPrefs.getInt("streak", 3)
        dailyPoints = sharedPrefs.getInt("points", 150)
        
        val lastCompletedDate = sharedPrefs.getString("last_date", "")
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val todayStr = sdf.format(java.util.Date())
        
        isDailyChallengeCompletedToday = (lastCompletedDate == todayStr)
        
        val allQs = QuestionsData.fiberQuestions + QuestionsData.ccnaQuestions + QuestionsData.powerQuestions
        if (allQs.isNotEmpty()) {
            val dateHash = todayStr.hashCode().let { if (it < 0) -it else it }
            val qIndex = dateHash % allQs.size
            currentDailyQuestion = allQs[qIndex]
        }
    }
    
    fun submitDailyChallengeAnswer(optionIndex: Int) {
        val q = currentDailyQuestion ?: return
        dailySelectedOptionIndex = optionIndex
        showDailyImmediateFeedback = true
        
        val isCorrect = (optionIndex == q.correctIndex)
        val sharedPrefs = application.getSharedPreferences("we_daily_prefs", Context.MODE_PRIVATE)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val todayStr = sdf.format(java.util.Date())
        
        if (isCorrect) {
            dailyPoints += 25
            val currentStreak = sharedPrefs.getInt("streak", 3)
            val nextStreak = currentStreak + 1
            dailyStreak = nextStreak
            
            sharedPrefs.edit()
                .putInt("points", dailyPoints)
                .putInt("streak", nextStreak)
                .putString("last_date", todayStr)
                .apply()
                
            isDailyChallengeCompletedToday = true
            AudioSynthesizer.playSuccess()
        } else {
            dailyStreak = 0
            sharedPrefs.edit()
                .putInt("streak", 0)
                .apply()
            AudioSynthesizer.playFailure()
        }
    }

    // --- Interactive IP Subnetting Trainer State ---
    var currentSubnetQuestion by mutableStateOf<String>("")
    var currentSubnetOptions by mutableStateOf<List<String>>(emptyList())
    var currentSubnetCorrectIndex by mutableStateOf<Int>(0)
    var currentSubnetExplanation by mutableStateOf<String>("")
    var subnetSelectedOptionIndex by mutableStateOf<Int?>(null)
    var showSubnetImmediateFeedback by mutableStateOf(false)
    var subnetSuccessCount by mutableStateOf(0)

    fun generateNewSubnetChallenge() {
        val classChoice = (0..2).random()
        val subnetsNeeded = listOf(2, 4, 8, 16).random()
        
        val baseIp = when(classChoice) {
            0 -> "192.168.1.0"
            1 -> "172.16.0.0"
            else -> "10.0.0.0"
        }
        
        val bitsNeeded = kotlin.math.ceil(kotlin.math.log2(subnetsNeeded.toDouble())).toInt()
        val basePrefix = when(classChoice) {
            0 -> 24
            1 -> 16
            else -> 8
        }
        val prefix = basePrefix + bitsNeeded
        val numHosts = (1 shl (32 - prefix)) - 2
        
        val maskString = when(prefix) {
            25 -> "255.255.255.128"
            26 -> "255.255.255.192"
            27 -> "255.255.255.224"
            28 -> "255.255.255.240"
            29 -> "255.255.255.248"
            17 -> "255.255.128.0"
            18 -> "255.255.192.0"
            19 -> "255.255.224.0"
            20 -> "255.255.240.0"
            9 -> "255.128.0.0"
            10 -> "255.192.0.0"
            11 -> "255.224.0.0"
            12 -> "255.240.0.0"
            else -> "255.255.255.0"
        }
        
        val correctIndex = (0..3).random()
        val optionsList = mutableListOf<String>()
        
        val qTitleAr = "لديك عنوان الشبكة $baseIp/$basePrefix وتريد تقسيمها لتناسب متطلبات السنترال لـ $subnetsNeeded شبكات فرعية على الأقل. ما هو قناع الشبكة (Subnet Mask) المقابل وعدد أجهزة المضيف المتاحة لكل فرع (Usable Hosts)؟"
        val qTitleEn = "Given network block $baseIp/$basePrefix, you want to divide it for at least $subnetsNeeded subnets. What is the matching Subnet Mask and number of Usable Hosts per subnet?"
        
        val questionText = if (isEnglish) qTitleEn else qTitleAr
        
        val correctOption = "Mask: $maskString, Usable Hosts: $numHosts"
        optionsList.add(correctOption)
        
        val invalidMasks = listOf("255.255.255.128", "255.255.255.240", "255.255.192.0", "255.255.255.10", "255.255.255.224", "255.248.0.0")
        val invalidHosts = listOf(30, 62, 14, 254, 126, 510, 2046)
        
        while(optionsList.size < 4) {
            val randomMask = invalidMasks.random()
            val randomHost = invalidHosts.random()
            val wrongOpt = "Mask: $randomMask, Usable Hosts: $randomHost"
            if (wrongOpt != correctOption && !optionsList.contains(wrongOpt)) {
                optionsList.add(wrongOpt)
            }
        }
        
        optionsList.shuffle()
        val newCorrectIndex = optionsList.indexOf(correctOption)
        
        currentSubnetQuestion = questionText
        currentSubnetOptions = optionsList
        currentSubnetCorrectIndex = newCorrectIndex
        subnetSelectedOptionIndex = null
        showSubnetImmediateFeedback = false
        
        val expAr = "التقسيم يحتاج إلى $bitsNeeded بتات إضافية (N=$bitsNeeded) لتوفير $subnetsNeeded شبكات فرعية. قناع الشبكة الجديد يصبح /$prefix وهو ما يطابق المظهر العشري $maskString. عدد أجهزة المضيف المتاحة يحسب بالصيغة (2 أس عدد بتات الأجهزة الطليقة ناقص 2) أي 2^${32 - prefix} - 2 = $numHosts مضيف."
        val expEn = "Subnetting demands $bitsNeeded bits (N=$bitsNeeded) to fulfill $subnetsNeeded subnets. The CIDR prefix increases to /$prefix, which translates to mask $maskString. Usable hosts are calculated via (2^(32-$prefix) - 2) which yields $numHosts host addresses."
        
        currentSubnetExplanation = if (isEnglish) expEn else expAr
    }
    
    fun submitSubnetAnswer(optionIndex: Int) {
        subnetSelectedOptionIndex = optionIndex
        showSubnetImmediateFeedback = true
        if (optionIndex == currentSubnetCorrectIndex) {
            subnetSuccessCount++
            AudioSynthesizer.playSuccess()
        } else {
            AudioSynthesizer.playFailure()
        }
    }

    init {
        initDailyChallenge()
        generateNewSubnetChallenge()
    }

    // Tab Navigation State
    var currentTab by mutableStateOf(AppTab.DASHBOARD)
        private set

    fun navigateTo(tab: AppTab) {
        currentTab = tab
        if (tab == AppTab.EXAM && activeExam == null) {
            // If they click on Exam and there is no active, pre-load a mixed test option
        }
    }

    // Active Exam State
    var activeExam by mutableStateOf<Exam?>(null)
        private set
    var currentQuestionIndex by mutableStateOf(0)
        private set
    var selectedOptionIndex by mutableStateOf<Int?>(null)
        private set
    var showImmediateFeedback by mutableStateOf(false)
        private set

    // Saved Exams Flow
    val savedExams: StateFlow<List<Exam>> = repository.savedExams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Spaced Repetition Flow
    val repetitionStates: StateFlow<List<RepetitionStateEntity>> = repository.repetitionStates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Spaced Repetition Card Study
    var currentSrsQuestion by mutableStateOf<Question?>(null)
        private set
    var showSrsAnswer by mutableStateOf(false)
        private set
    var srsFilteredList by mutableStateOf<List<Question>>(emptyList())
        private set
    var currentSrsIndex by mutableStateOf(0)
        private set

    // Analytics computation
    val analyticsState: StateFlow<AnalyticsData> = combine(savedExams, repetitionStates) { exams, srsList ->
        computeAnalytics(exams, srsList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsData())

    // Review Mode: reviewing a past completed exam
    var reviewExam by mutableStateOf<Exam?>(null)
        private set

    var userReferenceText by mutableStateOf("")
    var isGeneratingAiExam by mutableStateOf(false)
    var aiGenerationError by mutableStateOf<String?>(null)
    var isUsingFallbackAiGen by mutableStateOf(false)

    fun setLanguage(english: Boolean) {
        isEnglish = english
    }

    var attachedImageBytes by mutableStateOf<ByteArray?>(null)
    var attachedImageMimeType by mutableStateOf<String?>(null)
    var attachedFileName by mutableStateOf<String?>(null)

    fun removeAttachment() {
        attachedImageBytes = null
        attachedImageMimeType = null
        attachedFileName = null
    }

    var isGeneratingCategoryAiExam by mutableStateOf<String?>(null) // category name if loading

    // Time-Stress Exam Mode Config
    var isStressModeEnabled by mutableStateOf(false)

    // AI Mock Interview Simulation State
    var activeMockQuestions = listOf(
        "اشرح بالتفصيل الفرق بين شبكات GPON السلبية (PON) والشبكات النشطة (AON). أيهما تفضل المصرية للاتصالات WE لبناء الـ FTTH للمنازل ولماذا؟" to 
        "Explain the main differences between passive GPON and active AON networks. Which does Telecom Egypt prioritize for residential FTTH deployments and why?",
        
        "إذا طلبت منك الإدارة تقسيم شبكة فرعية تخدم سنترالاً عملاقاً يحتوي على 30 مهندساً وعميلاً نشطاً باستخدام الـ Subnetting، ما هو أفضل قناع شبكة CIDR مناسب، وما هو عنوان الشبكة وعنوان البث المباشر (Broadcast)؟" to 
        "If you are requested to subnet a network block to host 30 active engineers/nodes using VLSM, what is the best CIDR subnet mask, and what are the matching network and broadcast coordinates?",
        
        "عند انقطاع تيار الشبكة الكهربائية العمومية (AC) في كابينة الـ MSAN الخارجية، اشرح بالتفصيل وبشكل متسلسل دور الـ ATS، وجهاز الـ Rectifier، وبطاريات الطوارئ الاحتياطية لضمان عدم توقف المكالمات والإنترنت." to 
        "When the utility AC electricity fails in an active MSAN cabinet, outline the chronological roles of the ATS, the Rectifier cabinet, and the lead-acid VRLA backup batteries to avoid service dropout.",
        
        "كيف تفرق على شاشة جهاز الـ OTDR بين الحدث العاكس (Reflective Event) والحدث غير العاكس (Non-Reflective Event)؟ اذكر أمثلة لكل منهما في مسار كابل الألياف الضوئية للمشروع." to 
        "How do you distinguish between a Reflective Event and a Non-Reflective Event on an OTDR trace? Provide practical physical structure/failure examples for each in a fiber link."
    )
    
    var currentMockIndex by mutableStateOf(0)
    var mockUserAnswer by mutableStateOf("")
    var isEvaluatingMockAnswer by mutableStateOf(false)
    var mockEvaluationResult by mutableStateOf<InterviewEvaluation?>(null)
    var mockInterviewError by mutableStateOf<String?>(null)

    fun startMockInterview() {
        currentMockIndex = 0
        mockUserAnswer = ""
        isEvaluatingMockAnswer = false
        mockEvaluationResult = null
        mockInterviewError = null
        currentTab = AppTab.MOCK_INTERVIEW
    }

    fun submitMockAnswer() {
        val questionPair = activeMockQuestions[currentMockIndex]
        val questionText = if (isEnglish) questionPair.second else questionPair.first
        if (mockUserAnswer.trim().length < 5) {
            mockInterviewError = if (isEnglish) "Please write a comprehensive engineering answer (at least 5 characters) to evaluate!" else "يرجى كتابة إجابة هندسية كافية (5 أحرف على الأقل) لنقوم بتقييمها!"
            return
        }
        mockInterviewError = null
        isEvaluatingMockAnswer = true
        viewModelScope.launch {
            try {
                val eval = GeminiClient.evaluateInterviewAnswer(questionText, mockUserAnswer)
                mockEvaluationResult = eval
                AudioSynthesizer.playSuccess()
            } catch (e: Exception) {
                mockInterviewError = if (isEnglish) "Evaluation failed: ${e.message}" else "فشل التقييم: ${e.message}"
            } finally {
                isEvaluatingMockAnswer = false
            }
        }
    }

    fun nextMockQuestion() {
        if (currentMockIndex < activeMockQuestions.size - 1) {
            currentMockIndex++
            mockUserAnswer = ""
            mockEvaluationResult = null
            mockInterviewError = null
        } else {
            // Completed all questions, exit back to dashboard
            currentTab = AppTab.DASHBOARD
            AudioSynthesizer.playFanfare()
        }
    }

    fun addGlossaryTermToSrs(termAr: String, termEn: String, explanationAr: String, category: String) {
        viewModelScope.launch {
            val normalizedEn = termEn.lowercase().trim().replace(" ", "_")
            val qId = "glossary_$normalizedEn"
            
            // Find predefined or create a custom fallback
            val existingQuestion = QuestionsData.allQuestions.firstOrNull { it.id == qId }
            val question = if (existingQuestion != null) {
                existingQuestion
            } else {
                Question(
                    id = qId,
                    text = "ما هو المعنى الهندسي والمفهوم لـ: $termEn ($termAr)؟",
                    options = listOf(explanationAr, "تعريف خاطئ غير مرتبط بالشبكات", "توجيه البيانات في السنترال القديم", "طاقة تشغيل مستمرة وبدون فواتير"),
                    correctIndex = 0,
                    explanation = explanationAr,
                    category = category,
                    englishText = "What is the technical concept of: $termEn ($termAr)?",
                    englishOptions = listOf(explanationAr, "Incorrect non-telecom definition", "Legacy PSTN trunk routing", "Active rectifiers without fuses"),
                    englishExplanation = explanationAr
                )
            }
            updateSpacedRepetitionOnExamAnswer(question, isCorrect = false) // Start as un-learned (hard) for review
            loadSrsDeck("All") // Refresh SRS deck to immediately include this new card!
            AudioSynthesizer.playSuccess()
        }
    }

    var isSoundEnabled by mutableStateOf(true)
        private set

    fun toggleSound() {
        val next = !isSoundEnabled
        isSoundEnabled = next
        AudioSynthesizer.isSoundEnabled = next
        if (next) {
            AudioSynthesizer.playTone(listOf(880f), 60, volume = 0.25f)
        }
    }

    fun startExam(category: String, size: Int = if (category == "Mixed") 40 else 15) {
        val exam = QuestionsData.getRandomExam(category, size)
        activeExam = exam
        currentQuestionIndex = 0
        selectedOptionIndex = null
        showImmediateFeedback = false
        currentTab = AppTab.EXAM
        reviewExam = null
        aiGenerationError = null
        isUsingFallbackAiGen = false
    }

    fun generateAiExam(numberOfQuestions: Int = 5) {
        if (userReferenceText.trim().isBlank() && attachedImageBytes == null) {
            aiGenerationError = "يرجى كتابة نصوص مرجعية أو إرفاق صورة الشرح أولاً لتوليد من نصوصك."
            return
        }
        
        viewModelScope.launch {
            isGeneratingAiExam = true
            aiGenerationError = null
            isUsingFallbackAiGen = false
            
            try {
                if (GeminiClient.isApiKeyAvailable()) {
                    val exclude = savedExams.value.flatMap { exam ->
                        exam.questions.map { it.question.text }
                    }.takeLast(35)
                    
                    val generated = GeminiClient.generateCustomExam(
                        referenceText = userReferenceText,
                        numberOfQuestions = numberOfQuestions,
                        excludeQuestions = exclude,
                        attachedImageBytes = attachedImageBytes,
                        attachedImageMimeType = attachedImageMimeType
                    )
                    if (generated.isEmpty()) {
                        throw Exception("فشل الذكاء الاصطناعي في إرجاع أسئلة متوافقة.")
                    }
                    val customExam = Exam(
                        title = "امتحان ذكي مخصص (Gemini) 🤖",
                        category = "Mixed",
                        questions = generated.map { ExamQuestion(it) }
                    )
                    activeExam = customExam
                } else {
                    // Fallback to local offline smart generation based on keywords
                    isUsingFallbackAiGen = true
                    val trimmedRefText = userReferenceText.lowercase()
                    // Search for matching category offline
                    val categoryChoice = when {
                        trimmedRefText.contains("فايبر") || trimmedRefText.contains("fiber") || trimmedRefText.contains("ftth") || trimmedRefText.contains("gpon") -> "Fiber"
                        trimmedRefText.contains("شبك") || trimmedRefText.contains("ccna") || trimmedRefText.contains("routing") || trimmedRefText.contains("ip") -> "CCNA"
                        trimmedRefText.contains("باور") || trimmedRefText.contains("power") || trimmedRefText.contains("طاق") || trimmedRefText.contains("rectifier") -> "Power"
                        else -> "Mixed"
                    }
                    
                    val exam = QuestionsData.getRandomExam(categoryChoice, numberOfQuestions)
                    val customExam = Exam(
                        title = "اختبار مخصص - محاكاة ذكية (أوفلاين) 📁",
                        category = categoryChoice,
                        questions = exam.questions
                    )
                    activeExam = customExam
                }
                
                currentQuestionIndex = 0
                selectedOptionIndex = null
                showImmediateFeedback = false
                currentTab = AppTab.EXAM
                reviewExam = null
                removeAttachment() // Clean attachments on success
            } catch (e: Exception) {
                // If real AI gen crashed with an exception, let's gracefully fall back to offline simulation
                isUsingFallbackAiGen = true
                val exam = QuestionsData.getRandomExam("Mixed", numberOfQuestions)
                activeExam = Exam(
                    title = "اختبار مخصص - محاكاة ذكية (أوفلاين) 📁",
                    category = "Mixed",
                    questions = exam.questions
                )
                currentQuestionIndex = 0
                selectedOptionIndex = null
                showImmediateFeedback = false
                currentTab = AppTab.EXAM
                reviewExam = null
                aiGenerationError = "تنبيه: تحولنا للمحاكاة المحلية بسبب: ${e.message}"
            } finally {
                isGeneratingAiExam = false
            }
        }
    }

    fun generateAiExamForCategory(category: String, numberOfQuestions: Int = 10) {
        viewModelScope.launch {
            isGeneratingCategoryAiExam = category
            aiGenerationError = null
            isUsingFallbackAiGen = false
            
            try {
                if (GeminiClient.isApiKeyAvailable()) {
                    val promptText = when (category) {
                        "Fiber" -> "يرجى توليد أسئلة متخصصة وجديدة في هندسة الألياف الضوئية وكوابل الفايبر والشبكات الضوئية النشطة والسلبية FTTH, GPON, OTDR, Splice loss, optical budget."
                        "CCNA" -> "يرجى توليد أسئلة متخصصة وجديدة في هندسة الشبكات والـ CCNA والـ Subnetting وتوجيه الترافيك والـ Routing protocols OSPF, EIGRP, Static routing, OSI layers."
                        "Power" -> "يرجى توليد أسئلة متخصصة وجديدة في أنظمة القوى والباور ومصادر الطاقة الاحتياطية ومولدات السولار والبطاريات والريكتاير وتبريد الكبائن ومزودات الطاقة غير المنقطعة UPS."
                        else -> "يرجى توليد أسئلة في هندسة الاتصالات والشبكات الشاملة."
                    }
                    
                    val generated = GeminiClient.generateCustomExam(
                        referenceText = promptText,
                        numberOfQuestions = numberOfQuestions,
                        excludeQuestions = emptyList()
                    )
                    if (generated.isEmpty()) {
                        throw Exception("لم يتم توليد أي أسئلة.")
                    }
                    
                    val title = when (category) {
                        "Fiber" -> "امتحان فايبر ذكي بقوة (Gemini) 📡"
                        "CCNA" -> "امتحان شبكات ذكي بقوة (Gemini) 🌐"
                        "Power" -> "امتحان باور ذكي بقوة (Gemini) ⚡"
                        else -> "امتحان ذكي مولّد بالذكاء الاصطناعي 🤖"
                    }
                    
                    val customExam = Exam(
                        title = title,
                        category = category,
                        questions = generated.map { ExamQuestion(it) }
                    )
                    
                    activeExam = customExam
                    currentQuestionIndex = 0
                    selectedOptionIndex = null
                    showImmediateFeedback = false
                    currentTab = AppTab.EXAM
                    reviewExam = null
                } else {
                    // Local fallback: just shuffle the offline questions
                    startExam(category, numberOfQuestions)
                    isUsingFallbackAiGen = true
                    aiGenerationError = "تم توليد امتحان محلي لعدم توفر مفتاح AI"
                }
            } catch (e: Exception) {
                startExam(category, numberOfQuestions)
                aiGenerationError = "حدث خطأ بالاتصال بالذكاء الاصطناعي: ${e.message}. تم تحميل امتحان محلي كبديل تلقائي."
                isUsingFallbackAiGen = true
            } finally {
                isGeneratingCategoryAiExam = null
            }
        }
    }

    fun selectOption(index: Int) {
        if (!showImmediateFeedback) {
            selectedOptionIndex = index
            AudioSynthesizer.playClick()
        }
    }

    fun submitAnswer() {
        val exam = activeExam ?: return
        val currentSelected = selectedOptionIndex ?: return
        
        showImmediateFeedback = true
        
        val isCorrect = currentSelected == exam.questions[currentQuestionIndex].question.correctIndex
        if (isCorrect) {
            AudioSynthesizer.playSuccess()
        } else {
            AudioSynthesizer.playFailure()
        }
        
        // Update the current question in the active exam with the selected index
        val updatedQuestions = exam.questions.mapIndexed { idx, q ->
            if (idx == currentQuestionIndex) {
                q.copy(selectedIndex = currentSelected, isAnswered = true)
            } else {
                q
            }
        }
        
        activeExam = exam.copy(questions = updatedQuestions)
    }

    fun nextQuestion() {
        val exam = activeExam ?: return
        if (currentQuestionIndex < exam.questions.size - 1) {
            currentQuestionIndex++
            selectedOptionIndex = null
            showImmediateFeedback = false
        } else {
            // End of exam
            finishExam()
        }
    }

    private fun finishExam() {
        val exam = activeExam ?: return
        
        // Calculate Score
        var correctCount = 0
        exam.questions.forEach { eq ->
            if (eq.selectedIndex == eq.question.correctIndex) {
                correctCount++
            }
        }
        
        // Generate general advice/tips
        val scorePercent = (correctCount.toFloat() / exam.questions.size * 100).toInt()
        val feedback = when {
            scorePercent >= 90 -> "ممتاز جداً! لديك إلمام كامل بمتطلبات المقابلة في هذا المحور. ننصحك بتكرار المراجعة على فترات بعيدة لترسيخ الذاكرة."
            scorePercent >= 70 -> "مستوى جيد جداً! أجبت بشكل صحيح على معظم الأسئلة. انتبه إلى التبريرات المرفقة لشرح النقاط التي أخطأت فيها."
            else -> "مستوى متواسط. لست بعيداً، لكن ننصحك بمراجعة القسم النظري جيداً، واستخدام بطاقات التكرار الاحترافية لتثبيت المصطلحات الهندسية."
        }

        val completedExam = exam.copy(
            score = correctCount,
            isCompleted = true,
            generalFeedback = feedback,
            timestamp = System.currentTimeMillis()
        )

        activeExam = completedExam
        AudioSynthesizer.playFanfare()

        // Save to DB
        viewModelScope.launch {
            repository.saveExam(completedExam)
            // Save initial repetition state for every incorrect question
            completedExam.questions.forEach { eq ->
                val isCorrect = eq.selectedIndex == eq.question.correctIndex
                updateSpacedRepetitionOnExamAnswer(eq.question, isCorrect)
            }
        }
    }

    fun closeExam() {
        activeExam = null
        currentTab = AppTab.DASHBOARD
    }

    fun openSavedExamForReview(exam: Exam) {
        reviewExam = exam
        currentTab = AppTab.SAVED_EXAMS
    }

    fun closeReview() {
        reviewExam = null
    }

    fun deleteSavedExam(examId: String) {
        viewModelScope.launch {
            repository.deleteExam(examId)
        }
    }

    // --- Spaced Repetition Section ---
    fun loadSrsDeck(categoryFilter: String = "All") {
        viewModelScope.launch {
            val states = repetitionStates.value
            val now = System.currentTimeMillis()
            val srsRegisteredIds = states.map { it.questionId }.toSet()
            
            // Filter questions that are due, or if nothing is due we provide customized study candidates
            val dueQuestionIds = states.filter { it.nextReviewTimestamp <= now }
                .map { it.questionId }
                .toSet()

            val customQuestions = savedExams.value.flatMap { exam -> exam.questions.map { it.question } }
            val combinedQuestions = (QuestionsData.allQuestions + customQuestions).distinctBy { it.id }

            var candidates = combinedQuestions.filter { q ->
                categoryFilter == "All" || q.category == categoryFilter
            }

            // Separate into those explicitly registered in SRS vs other general questions
            val explicitlyInSrs = candidates.filter { q -> q.id in srsRegisteredIds }
            val otherCandidates = candidates.filter { q -> q.id !in srsRegisteredIds }

            // Sort explicitlyInSrs by nextReviewTimestamp ascending, so due/new ones come first!
            val sortedSrsList = explicitlyInSrs.sortedWith(compareBy<Question> { q ->
                // Prioritize due questions (where nextReviewTimestamp <= now)
                val isDue = q.id in dueQuestionIds
                if (isDue) 0 else 1
            }.thenBy { q ->
                states.firstOrNull { it.questionId == q.id }?.nextReviewTimestamp ?: 0L
            })

            srsFilteredList = if (sortedSrsList.isNotEmpty()) {
                // Return explicitly added/practiced ones first, followed by some from general list for exploration
                sortedSrsList + otherCandidates.shuffled().take(10)
            } else {
                otherCandidates.shuffled()
            }

            currentSrsIndex = 0
            showSrsAnswer = false
            currentSrsQuestion = srsFilteredList.firstOrNull()
        }
    }

    fun showAnswerSrs() {
        showSrsAnswer = true
    }

    fun rateSrsRecall(rating: Int) {
        val question = currentSrsQuestion ?: return
        
        if (rating >= 2) {
            AudioSynthesizer.playSuccess()
        } else {
            AudioSynthesizer.playFailure()
        }
        
        viewModelScope.launch {
            // Apply SM-2 Spaced Repetition Algorithm
            // rating: 1 = Hard, 2 = Medium, 3 = Easy
            val existing = repository.getRepetitionState(question.id)
            val now = System.currentTimeMillis()

            val easeFactor: Double
            val repetitions: Int
            val intervalDays: Int

            if (existing == null) {
                when (rating) {
                    1 -> { // Hard
                        easeFactor = 1.8
                        repetitions = 0
                        intervalDays = 1
                    }
                    2 -> { // Medium
                        easeFactor = 2.5
                        repetitions = 1
                        intervalDays = 1
                    }
                    else -> { // Easy
                        easeFactor = 2.7
                        repetitions = 1
                        intervalDays = 4
                    }
                }
            } else {
                val lastEF = existing.easeFactor
                val lastRep = existing.repetitions
                val lastInt = existing.intervalDays

                when (rating) {
                    1 -> { // Hard
                        easeFactor = maxOf(1.3, lastEF - 0.2)
                        repetitions = 0
                        intervalDays = 1
                    }
                    2 -> { // Medium
                        easeFactor = lastEF
                        repetitions = lastRep + 1
                        intervalDays = when (repetitions) {
                            1 -> 1
                            2 -> 3
                            else -> (lastInt * lastEF).toInt()
                        }
                    }
                    else -> { // Easy
                        easeFactor = lastEF + 0.15
                        repetitions = lastRep + 1
                        intervalDays = when (repetitions) {
                            1 -> 3
                            2 -> 6
                            else -> (lastInt * lastEF * 1.2).toInt()
                        }
                    }
                }
            }

            val nextReview = now + (intervalDays * 24 * 60 * 60 * 1000L)
            
            val updatedState = RepetitionStateEntity(
                questionId = question.id,
                easeFactor = easeFactor,
                repetitions = repetitions,
                intervalDays = intervalDays,
                nextReviewTimestamp = nextReview,
                lastReviewedTimestamp = now,
                category = question.category,
                answeredCorrectlyLastTime = rating >= 2
            )

            repository.saveRepetitionState(updatedState)

            // Move to next card
            nextSrsCard()
        }
    }

    private fun nextSrsCard() {
        if (currentSrsIndex < srsFilteredList.size - 1) {
            currentSrsIndex++
            showSrsAnswer = false
            currentSrsQuestion = srsFilteredList[currentSrsIndex]
        } else {
            // Finished current batch, reload deck
            loadSrsDeck()
        }
    }

    private suspend fun updateSpacedRepetitionOnExamAnswer(question: Question, isCorrect: Boolean) {
        val existing = repository.getRepetitionState(question.id)
        val now = System.currentTimeMillis()
        
        val rating = if (isCorrect) 3 else 1 // Map correct to 'easy/good' and incorrect to 'hard'
        val easeFactor: Double
        val repetitions: Int
        val intervalDays: Int

        if (existing == null) {
            if (isCorrect) {
                easeFactor = 2.5
                repetitions = 1
                intervalDays = 3
            } else {
                easeFactor = 1.7
                repetitions = 0
                intervalDays = 1
            }
        } else {
            if (isCorrect) {
                easeFactor = existing.easeFactor + 0.1
                repetitions = existing.repetitions + 1
                intervalDays = (existing.intervalDays * easeFactor).toInt().coerceAtLeast(2)
            } else {
                easeFactor = maxOf(1.3, existing.easeFactor - 0.25)
                repetitions = 0
                intervalDays = 1
            }
        }

        val nextReview = now + (intervalDays * 24 * 60 * 60 * 1000L)
        val stateEntity = RepetitionStateEntity(
            questionId = question.id,
            easeFactor = easeFactor,
            repetitions = repetitions,
            intervalDays = intervalDays,
            nextReviewTimestamp = nextReview,
            lastReviewedTimestamp = now,
            category = question.category,
            answeredCorrectlyLastTime = isCorrect
        )
        repository.saveRepetitionState(stateEntity)
    }

    private fun computeAnalytics(exams: List<Exam>, srsList: List<RepetitionStateEntity>): AnalyticsData {
        if (exams.isEmpty()) {
            return AnalyticsData()
        }

        // Segment scores
        val fiberExams = exams.filter { it.category == "Fiber" || it.category == "Mixed" }
        val ccnaExams = exams.filter { it.category == "CCNA" || it.category == "Mixed" }
        val powerExams = exams.filter { it.category == "Power" || it.category == "Mixed" }

        // Helper to calculate total correct vs total questions
        fun calcSuccessRate(filteredExams: List<Exam>, categoryName: String): Int {
            var correctCount = 0
            var totalCount = 0
            filteredExams.forEach { ex ->
                ex.questions.forEach { eq ->
                    if (eq.question.category == categoryName && eq.isAnswered) {
                        totalCount++
                        if (eq.selectedIndex == eq.question.correctIndex) {
                            correctCount++
                        }
                    }
                }
            }
            return if (totalCount > 0) (correctCount.toFloat() / totalCount * 100).toInt() else 0
        }

        val fiberRate = calcSuccessRate(fiberExams, "Fiber")
        val ccnaRate = calcSuccessRate(ccnaExams, "CCNA")
        val powerRate = calcSuccessRate(powerExams, "Power")

        // Overall readiness: average of rates, fallback to some smart values if standard is empty
        val overall = if (fiberRate + ccnaRate + powerRate > 0) {
            val categoriesWithData = listOf(fiberRate, ccnaRate, powerRate).count { it > 0 }
            (fiberRate + ccnaRate + powerRate) / categoriesWithData.coerceAtLeast(1)
        } else {
            0
        }

        // Active repetition stats
        val totalCardsInSrs = srsList.size
        val correctSrsMatches = srsList.count { it.answeredCorrectlyLastTime }

        // Streaks & progress estimation
        val totalExamsCompleted = exams.count { it.isCompleted }
        
        return AnalyticsData(
            overallReadiness = overall,
            fiberReadiness = if (fiberExams.isNotEmpty()) fiberRate else 0,
            ccnaReadiness = if (ccnaExams.isNotEmpty()) ccnaRate else 0,
            powerReadiness = if (powerExams.isNotEmpty()) powerRate else 0,
            totalExamsCount = totalExamsCompleted,
            repetitionCardsCount = totalCardsInSrs,
            correctRepetitions = correctSrsMatches
        )
    }
}

data class AnalyticsData(
    val overallReadiness: Int = 0,
    val fiberReadiness: Int = 0,
    val ccnaReadiness: Int = 0,
    val powerReadiness: Int = 0,
    val totalExamsCount: Int = 0,
    val repetitionCardsCount: Int = 0,
    val correctRepetitions: Int = 0
)

class InterviewViewModelFactory(
    private val repository: InterviewRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InterviewViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InterviewViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
