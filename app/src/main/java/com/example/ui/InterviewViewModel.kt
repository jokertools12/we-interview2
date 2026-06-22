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
    CONCEPTS,
    EXAM,
    SPACED_REPETITION,
    SAVED_EXAMS,
    ANALYTICS,
    MOCK_INTERVIEW
}

data class BasicConcept(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val category: String, // "Fiber", "CCNA", "Power"
    val summaryAr: String,
    val summaryEn: String,
    val detailedExplanationAr: String,
    val detailedExplanationEn: String,
    val diagramType: String? = null
)

val preloadedConcepts = listOf(
    BasicConcept(
        id = "concept_01",
        titleAr = "شبكات الـ GPON وهيكلتها",
        titleEn = "GPON Networks & Architecture",
        category = "Fiber",
        summaryAr = "تقنية شبكات الألياف البصرية الخاملة الأكثر انتشاراً لربط المستخدمين بالسنترال الرئيسي.",
        summaryEn = "The most widely deployed passive optical network technology for connecting users to core exchange switches.",
        detailedExplanationAr = "شبكة GPON (Gigabit Passive Optical Network) هي شبكة ألياف ضوئية منفعلة (خاملة) لا تستخدم أي عناصر طاقة نشطة لتوزيع الإشارة. تتألف من الـ OLT (Optical Line Terminal) في السنترال، ومجزئات الإشارة Passive Splitters، والـ ONT (Optical Network Terminal) عند المشتركين. تدعم سرعات تحميل 2.488 Gbps وسرعات رفع 1.244 Gbps باستخدام أطوال موجية 1490nm للهابط و1310nm للصاعد.",
        detailedExplanationEn = "GPON is a point-to-multipoint passive distribution network. It does not contain active electrical components. Instead, passive optical splitters route signals from the Optical Line Terminal (OLT) in the central exchange to multiple Optical Network Terminals (ONTs) at subscriber locations. Operating downstream at 1490nm (2.488 Gbps) and upstream at 1310nm (1.244 Gbps).",
        diagramType = "gpon_split"
    ),
    BasicConcept(
        id = "concept_02",
        titleAr = "جهاز الـ OTDR وفحص الفايبر",
        titleEn = "OTDR & Fiber Diagnosis",
        category = "Fiber",
        summaryAr = "أداة قياس طول الفايبر والوهن وتحديد مواقع الكسور واللحامات.",
        summaryEn = "An essential diagnosis device used to measure cable lengths, loss, splices, and detect cable cuts.",
        detailedExplanationAr = "جهاز الـ OTDR (Optical Time Domain Reflectometer) يرسل نبضات ليزيرية عالية الكثافة داخل الليف البصري ويقيس الضوء المرتد والمشتت (Backscattering) ليرسم منحنياً بيانياً. يتيح تحديد بعد الكسر بدقة بالغة بالامتار وحساب نسبة الفقد (Attenuation / Loss inside splices) بالديسيبل (dB).",
        detailedExplanationEn = "An OTDR is an optoelectronic instrument used to characterize optical fibers. It injects a series of optical pulses into the fiber and extracts light that is scattered or reflected back. This measures attenuation coefficients, splice losses, connector reflections, and locates fiber fractures precisely.",
        diagramType = "otdr_curve"
    ),
    BasicConcept(
        id = "concept_03",
        titleAr = "توصيل كوابل الـ FTTH ومواصفتها",
        titleEn = "FTTH Cabling & Standards",
        category = "Fiber",
        summaryAr = "شبكة الألياف الممتدة للبيوت وأنواع كوابل الشعرة الأحادية.",
        summaryEn = "Fiber deployment options extending straight to the subscriber home using standard single-mode cables.",
        detailedExplanationAr = "تعتمد مشاريع الـ FTTH (Fiber to the Home) على كوابل شعرة أحادية النمط (Single-Mode Fiber) متطابقة مع المعيار G.652 لأنابيب الكواشف وسهولة الانحناء المعياري G.657 للفروع الداخلية. يتراوح الفقد النظري للضوء بحدود 0.22 dB/km عند الطول الموجي 1550nm، وتتراوح طاقة الاستقبال بمودم العميل بين -8 dBm إلى -27 dBm كأقصى وهن مسموح.",
        detailedExplanationEn = "FTTH structures bring gigabit-class throughput to homes. Utilizing premium single-mode drop fiber designed under G.652D backbone norms or bend-insensitive G.657A standards inside client flats. Expected light attenuation is 0.22 dB/km at 1550nm, aiming for an ONT receive optical budget between -8 dBm and -27 dBm.",
        diagramType = "gpon_split"
    ),
    BasicConcept(
        id = "concept_04",
        titleAr = "حسابات فقد مجزئ الإشارة (Splitters)",
        titleEn = "Optical Splitter Loss Formulas",
        category = "Fiber",
        summaryAr = "الفاقد الرياضي لقدرة الإشارة الضوئية عند استخدام مجزءات GPON.",
        summaryEn = "The mathematical light power drop associated with various optical split ratios.",
        detailedExplanationAr = "يعمل مجزئ الإشارة البصرية (Optical Splitter) بالانقسام الثنائي. حساب الفقد النظري لكل انكسار 1:2 يعادل فقد 3 dB. بمعادلة الفقد التقريبية: Loss = 3 * log2(N)، فيكون مجزئ 1:4 يفقد حوالي 6-7 dB، ومجزئ 1:8 يفقد 9-10 dB، ومجزئ 1:16 يفقد 13-14 dB، ومجزئ 1:32 يفقد 16-17 dB.",
        detailedExplanationEn = "Optical splitters divide the power of laser light into multiple streams. Every binary split (1:2) introduces a theoretical 3 dB loss (plus connector/excess losses). Computed using Loss ≈ 3 * log2(N), so a 1:4 splitter experiences ~6-7 dB insertion loss, 1:8 is ~10 dB, 1:16 is ~14 dB, and a 1:32 splitter exhibits ~17-18 dB drop.",
        diagramType = "gpon_split"
    ),
    BasicConcept(
        id = "concept_05",
        titleAr = "بروتوكول OSPF وبنية الشبكة",
        titleEn = "OSPF Routing & Area Design",
        category = "CCNA",
        summaryAr = "بروتوكول تسيير هرمي يعتمد على حالة الارتباط وحساب أقصر مسار لكلف الروابط.",
        summaryEn = "A hierarchical link-state routing protocol computing shortest path speeds recursively.",
        detailedExplanationAr = "بروتوكول OSPF (Open Shortest Path First) هو بروتوكول توجيه داخلي (IGP) ينتمي لفئة Link-State. يبني كل راوتر خارطة طوبولوجية كاملة للشبكة مستخدماً خوارزمية ديكسترا (SPF) لحساب المسار الأقل كلفة. يعتمد OSPF على نظام المناطق حيث تمثل Area 0 العمود الفقري، وترسل تحديثات الـ LSAs عند حدوث تغييرات فقط للحفاظ على الباندويدث.",
        detailedExplanationEn = "OSPF is a robust Link-State dynamic routing protocol. It utilizes Dijkstra's SPF algorithm to determine the loop-free shortest path to any IP prefix. OSPF organizes routers in a hierarchical zone tree where Area 0 is the mandatory backbone. Rather than routing tables, routers exchange Link State Advertisements (LSAs) for instant topology syncing.",
        diagramType = "ospf_topology"
    ),
    BasicConcept(
        id = "concept_06",
        titleAr = "مبادئ الـ IP Subnetting والـ CIDR",
        titleEn = "IP Subnetting & CIDR Logic",
        category = "CCNA",
        summaryAr = "تقسيم الشبكات الكبيرة إلى مجموعات فرعية للحد من استهلاك الآي بي.",
        summaryEn = "Dividing large logical networks into smaller subnets to mitigate IP depletion.",
        detailedExplanationAr = "تنسيق الـ IP Subnetting يقتطع بتات من جزء المضيف (Host bits) ويضيفها لقسم الشبكة (Network bits) باستخدام قناع شبكة فرعية (Subnet Mask) مخصص. يرمز له بنظام CIDR (مثال /24 أو /26). يتم حساب عدد الأجهزة المتاحة بالمعادلة 2^n - 2 (حيث يقتطع عنوان الشبكة وعنوان البث العام).",
        detailedExplanationEn = "Subnetting borrows host bits from an IP address to create smaller logical subdivisions. Standardized via CIDR notation (e.g., /26), it improves security and confines broadcast domains. Usable hosts satisfy the equation 2^H - 2, where the first IP is reserved as the Network ID, and the last host represents the broadcast address.",
        diagramType = "ospf_topology"
    ),
    BasicConcept(
        id = "concept_07",
        titleAr = "تقسيم المبدل للشبكات الافتراضية VLANs",
        titleEn = "Virtual LANs & Trunking Ports",
        category = "CCNA",
        summaryAr = "عزل أجهزة المشتركين منطقياً داخل نفس السويتش لزيادة الأمان والأداء.",
        summaryEn = "Logically dividing a physical switch into isolated broadcast domains to enhance control.",
        detailedExplanationAr = "الـ VLANs (Virtual Local Area Networks) تعزل منافذ جهاز المبدل (Switch Ports) منطقياً لتكوين نطاقات بث منفصلة (Isolated Broadcast Domains) تماماً كما لو كانت على سويتشات مختلفة. يتم ربط السويتشات معاً باستخدام منافذ Trunk لتمرير ترافيك كافة الـ VLANs بوسم معيار IEEE 802.1Q.",
        detailedExplanationEn = "VLANs isolate network segments on the same physical switch, confining broadcast storms and maximizing security. To span VLANs across multiple switches, trunk ports are established, tagging Ethernet frames using the standard 802.1Q encapsulation protocols.",
        diagramType = "ospf_topology"
    ),
    BasicConcept(
        id = "concept_08",
        titleAr = "تأمين وحل مشاكل بروتوكول الـ DHCP",
        titleEn = "DHCP & APIPA Troubleshooting",
        category = "CCNA",
        summaryAr = "توزيع خوادم عناوين IP التلقائية ومعيار فشل الاتصال APIPA.",
        summaryEn = "Automatic client IP provisioning and standard link-local failover ranges.",
        detailedExplanationAr = "بروتوكول DHCP يسلم عناوين الـ IP والقناع والـ Gateway تلقائياً للأشخاص المتصلين عبر خطوات DORA (Discover, Offer, Request, Acknowledge). وفي حال تعذر وجود الخادم، يقوم نظام التشغيل بويندوز بتعيين عنوان APIPA من النطاق 169.254.0.0/16 محلياً لتمكين الاتصال بالـ LAN.",
        detailedExplanationEn = "DHCP automates IP client setups via the 4-step DORA exchange. If a client sends a broadcast DHCP Discover but secures no lease from the router, APIPA assigns a temporary link-local IP in the 169.254.0.0/16 pool, allowing flat ad-hoc local communication.",
        diagramType = "ospf_topology"
    ),
    BasicConcept(
        id = "concept_09",
        titleAr = "نظام موحد التيار الـ Rectifier بالسنترال",
        titleEn = "Rectifier Panel & -48V DC Busbar",
        category = "Power",
        summaryAr = "تحويل طاقة المتردد العمومية المتقلبة لطاقة مستمرة آمنة لتشغيل السنترالات وشحن البطاريات.",
        summaryEn = "Converting variable utility AC grid power to stable -48V DC power and managing battery charges.",
        detailedExplanationAr = "لوحة الموحد (Rectifier) هي القلب النابض لطاقة السنترال. تحول التيار المتردد AC (220V/380V) لتيار مستمر DC سالب بمقدار -48V لتغذية المعالجات والكروت دون تداخل مغناطيسي، كما تعوم وتشحن البطاريات باستمرار لصد نقص التيار المفاجئ.",
        detailedExplanationEn = "A Rectifier cabinet converts high-power input commercial AC into stable -48V DC electricity, the industry standard. This powers core telecom processors (minimizing electrical noise interference) while continuously delivering a trickling float charge to backup battery strings.",
        diagramType = "rectifier"
    ),
    BasicConcept(
        id = "concept_10",
        titleAr = "لوحة التحويل التلقائي للقوى الـ ATS",
        titleEn = "Automatic Transfer Switch (ATS)",
        category = "Power",
        summaryAr = "مراقبة التيار العمومي ومفتاح ربط مولد الديزل لحماية السنترال.",
        summaryEn = "Monitoring primary utility lines and switching power paths to diesel generator loops.",
        detailedExplanationAr = "الـ ATS (Automatic Transfer Switch) هو مفتاح تبديل كهرومغناطيسي ذكي يراقب خط التغذية العمومي الأساسي. عند انقطاع الكهرباء، تشعر اللوحة تلقائياً وتطلق أمراً للمولد الاحتياطي بالتشغيل، وعندما يستقر جهد المولد تقوم بتحويل مفاتيح الإتصال للمولد فوراً.",
        detailedExplanationEn = "An ATS is an automated double-throw switchboard. It monitors tension in public AC lines. Upon blackout detection, it signals the Backup Generator to start, waits for electrical output stabilization, and safely routes the exchange's main busbar intake away from the dead grid.",
        diagramType = "ats_switch"
    ),
    BasicConcept(
        id = "concept_11",
        titleAr = "بنك بطاريات الـ VRLA للكبائن",
        titleEn = "VRLA Battery Bank Chemistry",
        category = "Power",
        summaryAr = "بطاريات اتصالات مغلقة لتغذية كبائن MSAN السريعة لساعات.",
        summaryEn = "Maintenance-free oxygen recombination cells kept inside compact outdoor cabinets.",
        detailedExplanationAr = "تستخدم كبائن MSAN الخارجية بطاريات الرصاص الحمضية المغلقة بصمامات (VRLA) سواء AGM أو Gel. تتركب على التوالي لتكون تيار -48V DC (24 خلية بسعة 2V لكل واحدة). تتميز بطاقات صيانة معدومة، وعدم تسريب غاز هيدروجين هيدروليكي كالبطاريات المفتوحة.",
        detailedExplanationEn = "VRLA batteries AGM or Gel models are sealed with custom pressure safety valves. Operating via internal oxygen recombination to eliminate water evaporation. Running series connections of 24 cells sums 2V blocks to deliver stable -48V backup feed for MSAN nodes.",
        diagramType = "battery_series"
    ),
    BasicConcept(
        id = "concept_12",
        titleAr = "المولدات وسرعتها التزامنية",
        titleEn = "Standby Generators Synchronous Speed",
        category = "Power",
        summaryAr = "علاقة عدد الأقطاب والتردد بمحركات الديزل للحفاظ على تردد 50Hz بالسنترال.",
        summaryEn = "How magnetic alternators regulate engine speeds to deliver a steady 50Hz stream.",
        detailedExplanationAr = "المولد الديزل (Standby Generator) يؤمن طاقة المتردد عند الخطر. السرعة الميكانيكية التي يجب أن يدور بها العمود تدعى السرعة التزامنية (Ns). تحت تردد 50Hz ومجموعة 4 أقطاب مغناطيسية، يحسب دوران الديزل بالقانون Ns = 120 * f / P فيكون تمامه 1500 RPM بالتوازي.",
        detailedExplanationEn = "A standby diesel generator converts mechanical torque into AC power. Its Synchronous Speed (Ns in RPM) is tied directly to the grid frequency f (50Hz in Egypt) and magnetic poles P via Ns = 120 * f / P. With a standard 4-pole alternator, the engine must lock at exactly 1500 RPM.",
        diagramType = "battery_series"
    )
)

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

    // --- Basic Concepts with AI Search & Explanation State ---
    var conceptsList = preloadedConcepts + additionalConcepts
    var searchConceptQuery by mutableStateOf("")
        private set
    var selectedConcept by mutableStateOf<BasicConcept?>(null)
        private set
    var conceptAiExplanation by mutableStateOf("")
        private set
    var isExplainingConcept by mutableStateOf(false)
        private set

    fun setConceptSearch(query: String) {
        searchConceptQuery = query
    }

    fun getFilteredConcepts(): List<BasicConcept> {
        val q = searchConceptQuery.trim()
        if (q.isEmpty()) return conceptsList
        return conceptsList.filter {
            it.titleAr.contains(q, ignoreCase = true) ||
            it.titleEn.contains(q, ignoreCase = true) ||
            it.summaryAr.contains(q, ignoreCase = true) ||
            it.summaryEn.contains(q, ignoreCase = true) ||
            it.category.contains(q, ignoreCase = true)
        }
    }

    fun selectConcept(concept: BasicConcept?) {
        selectedConcept = concept
        conceptAiExplanation = ""
    }

    fun fetchAiExplanationForSelectedConcept() {
        val concept = selectedConcept ?: return
        isExplainingConcept = true
        conceptAiExplanation = ""
        viewModelScope.launch {
            try {
                val explanation = com.example.data.GeminiClient.explainConcept(concept.titleEn, concept.category)
                conceptAiExplanation = explanation
            } catch (e: Exception) {
                conceptAiExplanation = if (isEnglish) {
                    "Error fetching explanation: ${e.localizedMessage}"
                } else {
                    "عذراً، حدث خطأ أثناء الاتصال بالذكاء الاصطناعي: ${e.localizedMessage}"
                }
            } finally {
                isExplainingConcept = false
            }
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

    // Timed Quiz Mode State
    var isTimedQuizMode by mutableStateOf(false)
    var examTimeRemainingSeconds by mutableStateOf(0)
    var initialQuizTimeSeconds by mutableStateOf(0)

    // AI Mock Interview Simulation State
    var activeMockQuestions = listOf<Question>()
    var currentMockIndex by mutableStateOf(0)
    var mockUserAnswer by mutableStateOf("")
    var isEvaluatingMockAnswer by mutableStateOf(false)
    var mockEvaluationResult by mutableStateOf<InterviewEvaluation?>(null)
    var mockInterviewError by mutableStateOf<String?>(null)

    // Store user answers and evaluations for the current session
    val mockSessionHistory = androidx.compose.runtime.mutableStateListOf<MockAnsweredItem>()
    var showMockSessionSummary by mutableStateOf(false)
    var isGeneratingSessionReport by mutableStateOf(false)
    var sessionReportFeedback by mutableStateOf("")
    var sessionAverageScore by mutableStateOf(0)

    fun startMockInterview() {
        currentMockIndex = 0
        mockUserAnswer = ""
        isEvaluatingMockAnswer = false
        mockEvaluationResult = null
        mockInterviewError = null
        activeMockQuestions = QuestionsData.allQuestions.shuffled().take(10)
        
        mockSessionHistory.clear()
        showMockSessionSummary = false
        isGeneratingSessionReport = false
        sessionReportFeedback = ""
        sessionAverageScore = 0
        
        currentTab = AppTab.MOCK_INTERVIEW
    }

    fun submitMockAnswer() {
        val question = activeMockQuestions[currentMockIndex]
        val questionText = question.getLocalizedText(isEnglish)
        
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
                
                // Track in history
                mockSessionHistory.add(
                    MockAnsweredItem(
                        questionAr = question.text,
                        questionEn = question.englishText ?: question.text,
                        userAnswer = mockUserAnswer,
                        evaluation = eval
                    )
                )
                
                AudioSynthesizer.playSuccess()
            } catch (e: Exception) {
                mockInterviewError = if (isEnglish) "Evaluation failed: ${e.message}" else "فشل التقييم: ${e.message}"
            } finally {
                isEvaluatingMockAnswer = false
            }
        }
    }

    fun generateSessionFeedbackReport() {
        showMockSessionSummary = true
        isGeneratingSessionReport = true
        sessionReportFeedback = ""
        
        viewModelScope.launch {
            try {
                val answeredCount = mockSessionHistory.size
                val totalScore = mockSessionHistory.sumOf { it.evaluation.score }
                sessionAverageScore = if (answeredCount > 0) totalScore / answeredCount else 0
                
                if (GeminiClient.isApiKeyAvailable()) {
                    sessionReportFeedback = GeminiClient.generateSessionDetailedFeedback(mockSessionHistory.toList())
                } else {
                    sessionReportFeedback = getLocalFallbackSessionReport(sessionAverageScore, mockSessionHistory.toList())
                }
                AudioSynthesizer.playFanfare()
            } catch (e: Exception) {
                sessionReportFeedback = if (isEnglish) {
                    "Analysis failure: ${e.message}. Quick summary score: $sessionAverageScore% across ${mockSessionHistory.size} questions."
                } else {
                    "فشل إعداد التقرير التفاعلي: ${e.message}. ملخص رقمي سريع: معدل القبول $sessionAverageScore% عبر إجابة ${mockSessionHistory.size} أسئلة."
                }
            } finally {
                isGeneratingSessionReport = false
            }
        }
    }

    fun getLocalFallbackSessionReport(averageScore: Int, history: List<MockAnsweredItem>): String {
        val acceptedStringAr = when {
            averageScore >= 85 -> "معتمد كمهندس أول فني جاهز للتسليم والتشغيل المباشر بسنترالات WE"
            averageScore >= 70 -> "مقبول مبدئياً للخدمات الهندسية الميدانية مع التوصية بفترة تدريبية"
            else -> "يحتاج لإعادة المقابلة والاطلاع على المراجع الملحقة وأطلس القوانين"
        }
        val acceptedStringEn = when {
            averageScore >= 85 -> "Senior Engineering Level - Highly compliant with Telecom Egypt requirements."
            averageScore >= 70 -> "Competent Field Level - Approved subject to brief practical onboarding."
            else -> "Unsatisfactory Level - Recommended for thorough review of standard telemetry coefficients."
        }
        return """
            📊 الخلاصة ومستوى القبول المعياري (Overall Summary & Acceptance Level):
            - معدل الأداء الإجمالي: $averageScore%
            - حالة لجنة التقييم: $acceptedStringAr
            - HR Level: $acceptedStringEn

            🌟 نقاط القوة الفنية التي تم رصدها (Technical Strengths Demonstrated):
            ${if (averageScore >= 70) {
                "- إظهار فهم طيب لمصطلحات ومفردات السنترال والاتصالات الأساسية.\n- الالتزام بشروحات متسلسلة لعمليات الطاقة و/أو شبكات النفاذ."
            } else {
                "- الالتزام بمحاولة الإجابة والمحاكاة.\n- فهم أولي لبيئة العمل الهندسية."
            }}

            ⚠️ فجوات المعرفة ونقاط الضعف (Knowledge Gaps & Pitfalls):
            ${if (averageScore < 85) {
                "- عدم الاهتمام بذكر النطاقات والأرقام الدقيقة في حسابات فقد الألياف أو فولت النظم المستمرة سالب 48 فولت.\n- الحاجة لمراجعة دقيقة لآليات حماية القوى والـ ATS."
            } else {
                "- تفاصيل طفيفة حول تصميم شبكات الخوادم وتقادم بعض كبائن الـ MSAN القديمة."
            }}

            🚀 خارطة الطريق وتوصيات التعيين لمشروع WE:
            1. ننصحك بالرجوع المتكرر إلى "صفحة أطلس القوانين والمعادلات" للاطلاع التام وممارسة نموذج حساب الفقد البصري.
            2. قم بإعادة التدريب المستمر وزيادة سعة المفردات في قسم SRS لحفظ مصطلحات ومفاهيم الفايبر كـ Cleaver, Fusion Splice, OTDR, Event Zone.
            3. شارك في الاختبارات التنافسية بمختلف المحاور لقياس تقدم مستواك في مهارات التقسيم الفرعي للشبكات والـ Routing Protocols.
        """.trimIndent()
    }

    fun nextMockQuestion() {
        if (currentMockIndex < activeMockQuestions.size - 1) {
            currentMockIndex++
            mockUserAnswer = ""
            mockEvaluationResult = null
            mockInterviewError = null
        } else {
            // Completed all questions, transition to generating detailed session summary report
            generateSessionFeedbackReport()
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
        isTimedQuizMode = false
        examTimeRemainingSeconds = 0
    }

    fun startTimedQuiz(category: String, size: Int = 15) {
        val exam = QuestionsData.getRandomExam(category, size)
        activeExam = exam
        currentQuestionIndex = 0
        selectedOptionIndex = null
        showImmediateFeedback = false
        currentTab = AppTab.EXAM
        reviewExam = null
        aiGenerationError = null
        isUsingFallbackAiGen = false
        
        // Setup timer: 20 seconds per question (e.g. 15 questions * 20s = 300 seconds / 5 mins)
        isTimedQuizMode = true
        examTimeRemainingSeconds = size * 20
        initialQuizTimeSeconds = size * 20
    }

    fun autoSubmitTimedQuiz() {
        val exam = activeExam ?: return
        if (exam.isCompleted) return
        
        // Let's mark all unanswered questions as answered with index -1 (incorrect)
        val updatedQuestions = exam.questions.map { eq ->
            if (!eq.isAnswered) {
                eq.copy(isAnswered = true, selectedIndex = -1)
            } else {
                eq
            }
        }
        activeExam = exam.copy(questions = updatedQuestions)
        finishExam()
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
                        questions = generated.map { ExamQuestion(it.getShuffled()) }
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
                        "IT INFRASTRUCTURE" -> "يرجى توليد أسئلة متخصصة في البنية التحتية لتكنولوجيا المعلومات IT Infrastructure، والتي تشمل السيرفرات، لينكس، النسخ الاحتياطي، المحاكاة الافتراضية Virtualization، قواعد البيانات، والـ Active Directory."
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
                        questions = generated.map { ExamQuestion(it.getShuffled()) }
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
        isTimedQuizMode = false
        examTimeRemainingSeconds = 0
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
                (sortedSrsList + otherCandidates.shuffled().take(10)).map { it.getShuffled() }
            } else {
                otherCandidates.shuffled().map { it.getShuffled() }
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
