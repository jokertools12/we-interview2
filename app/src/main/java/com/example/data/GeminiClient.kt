package com.example.data

import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Checks if a Gemini API key is configured.
     */
    fun isApiKeyAvailable(): Boolean {
        val apiKey = BuildConfig.GEMINI_API_KEY
        return apiKey.isNotEmpty() && apiKey != "AIzaSyDAOO1A6Z30yjb_OkRSsVOy8glCn4tLUp0"
    }

    /**
     * Generates a comprehensive, localized explanation of a telecom concept using Gemini.
     */
    suspend fun explainConcept(conceptTitle: String, conceptCategory: String): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext "عذراً، يجب إدخال مفتاح Gemini API في لوحة الأسرار لتفعيل الشرح التفصيلي بالذكاء الاصطناعي."
        }
        val apiKey = BuildConfig.GEMINI_API_KEY
        val url = "$BASE_URL?key=$apiKey"

        val systemInstruction = "أنت أستاذ هندسة اتصالات وخبير مقابلات فنية في الشركة المصرية للاتصالات Telecom Egypt (WE). ولديك المهارة العالية لتبسيط المفاهيم الهندسية المعقدة بأسلوب عربي فصيح واحترافي غني بالمعلومات والقوانين الرياضية والتقنية الهامة للمقابلة الشخصية."

        val prompt = """
            قم بتقديم شرح تفصيلي واحترافي وشامل لمفهوم: "$conceptTitle" في تخصص: "$conceptCategory" لغرض اجتياز المقابلة الفنية في شركة وي (المصرية للاتصالات WE). 
            قم بتضمين:
            1. الفكرة الأساسية لعمل المفهوم والمصطلحات الانجليزية الهامة المقابلة.
            2. الأرقام الهندسية الدقيقة والقوانين الهامة المرتبطة به في شركة وي (مثل الترددات، درجات الفقد بالـ dB، الفولتيات، الـ Wavelengths).
            3. نصائح وحيل عملية وسيناريوهات للإجابة عندما يسألك الممتحن عنه في المقابلة الشخصية.
            
            نسق المخرجات بشكل رائع باستخدام نقاط واضحة وعناوين فرعية وتنسيق Markdown مريح ومحترف.
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "explainConcept call failed: Code ${response.code}, Body: $errBody")
                    "عذراً، فشل الاتصال بمحرك Gemini لشرح المفهوم الحالي."
                } else {
                    val bodyString = response.body?.string() ?: throw Exception("Empty body")
                    val responseJson = JSONObject(bodyString)
                    val candidates = responseJson.getJSONArray("candidates")
                    val contentObj = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = contentObj.getJSONArray("parts")
                    parts.getJSONObject(0).getString("text").trim()
                }
            }
        } catch (e: Exception) {
            "فشل استقصاء المعلومات من الذكاء الاصطناعي: ${e.localizedMessage}"
        }
    }

    /**
     * Generates custom questions from Gemini using user's text from PDFs, images, or attached files.
     */
    suspend fun generateCustomExam(
        referenceText: String,
        numberOfQuestions: Int = 10,
        excludeQuestions: List<String> = emptyList(),
        attachedImageBytes: ByteArray? = null,
        attachedImageMimeType: String? = null
    ): List<Question> = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            throw IllegalStateException("API key is missing")
        }
        val apiKey = BuildConfig.GEMINI_API_KEY
        val url = "$BASE_URL?key=$apiKey"

        val systemInstruction = """
أنت أستاذ هندسة اتصالات وخبير مقابلات فنية في الشركة المصرية للاتصالات Telecom Egypt (WE). ولديك المهارة العالية لتحليل ملفات الـ PDF وصور الشروحات التقنية واستخراج أسئلة دقيقة وعشوائية فريدة واحترافية تلائم المقابلات الحقيقية دون أي تكرار.
لقد تم تدريبك بالكامل على المرجع المعرفي والأسئلة الشاملة لـ WE في الأقسام التالية:
1. قسم تكنولوجيا الألياف FTTH وGPON:
   - الأجهزة النشطة والسلبية و OTDR.

2. قسم الشبكات والـ CCNA:
   - عناوين الـ IP والموجهات والأوامر.

3. قسم البنية التحتية وتكنولوجيا المعلومات (IT Infrastructure):
   - الخوادم الفيزيائية والوهمية (Virtualization, VMs, VMware) وتقنية الحاويات (Docker).
   - أنظمة تشغيل Linux وأوامرها الأساسية (grep, top, chown, SSH).
   - تخزين البيانات (RAID 0, 1, 5, 10, SAN, NAS, SSD vs HDD).
   - نظم التشغيل وخدمات الدومين (Active Directory, LDAP, DNS, DHCP).
   - التوافر العالي High Availability وموازنة الأحمال Load Balancers.

4. قسم الهندسة الكهربائية والباور (Power Basics):
   - قانون أوم الكلاسيكي للجهد والتيار والمقاومة: V = I * R.
   - قوانين كيرشوف للتيار (KCL: مجموع التيارات عند عقدة = صفر) وللجهد (KVL: مجموع الهبوط في مسار مغلق = صفر).
   - نسبة تحويل المحولات الكهربائية الحثية (Transformer ratio): V1 / V2 = N1 / N2.
   - المكثفات لتخزين الشحنات (C = Q/V) والملفات الحثية وتثبيت عامل القدرة cos(θ).
   - المحركات الحثية والسرعة التزامنية: ns = 120f / P ومعدل الانزلاق s = (ns - n)/ns. ومحركات التيار المستمر: V = E ± IaRa.
   - أنظمة التأريض الآمنة (Grounding/Earthing): TN, TT, IT.
   - تيار ثلاثي الأطوار المتزن (Three-Phase balanced system): VL = sqrt(3) * VP.
   - أجهزة التحكم الصناعية PLC المبرمجة بلغة السلم (Ladder Logic) ونظم المراقبة والتحكم SCADA وجودة القدرة (Voltage Sags, Swells, Harmonics).
   - كبائن الباور في الاتصالات: الـ Rectifier الذي يحول كهرباء الشبكة العمومية AC إلى جهد مستمر سالب بقيمة -48V DC لتغذية أجهزة الاتصالات وشحن البطاريات على العائم لمنع سقوط الخدمة، ومفاتيح التحريك ATS (Auto Transfer Switch) التي تراقب الشبكة العمومية وتعطي أمر تشغيل وتوجيه للأحمال آلياً نحو مولد الديزل الاحتياطي عند حدوث Blackout.
   - بنك البطاريات الاحتياطي: لتأمين طاقة مستمر -48V DC من خلال توصيل عدد 24 خلية بطارية حمض رصاصية بجهد 2V على التوالي (In Series).

يجب عليك توليد الأسئلة بشكل ثنائي اللغة (Bilingual) بملء حقول الإنجليزية والعربية معاً بدقة بالغة.
""".trimIndent()

        val randomSeed = (1000..99999).random()
        val excludePrompt = if (excludeQuestions.isNotEmpty()) {
            """
            ⚠️ هام جداً: يرجى عدم تكرار أو توليد أي من الأسئلة التالية التي مَرّت سابقاً وموجودة في ذاكرة المستخدم لضمان التدريب المتنوع والشامل:
            ${excludeQuestions.joinToString("\n") { "- $it" }}
            """.trimIndent()
        } else {
            ""
        }

        val prompt = """
            بناءً على المرجع أو الصور والمواد الهندسية المترابطة أدناه، ونظراً لتدربك الشديد على بنك ومفاهيم السنترالات وشركة WE، قم بتوليد $numberOfQuestions أسئلة اختيار من متعدد (MCQs) جديدة تماماً ومحترفة للغاية للتدريب الهندسي لمقابلات المصرية للاتصالات.
            
            عنصر عشوائية الاختيار والتشعيب: $randomSeed
            
            $excludePrompt
            
            المرجع التقني المدخل:
            ---
            $referenceText
            ---
            
            تعليمات التنوع والجودة الإجبارية:
            - قم بإنشاء الأسئلة ثنائية اللغة: املأ الحقول العربية (text, options, explanation) واملأ أيضاً الحقول الإنجليزية المقابلة لها تماماً (englishText, englishOptions, englishExplanation).
            - يجب أن تكون الأسئلة بالغة الاحترافية، وتغطي تفاصيل دقيقة (مثل بروتوكولات الشبكات، أو أطوال موجات الفايبر، أو فولتيات أنظمة القوى للاتصالات).
            - نوّع الصعوبة لتشمل أسئلة ذكية تختبر فهم القوانين والأسس الهندسية.
            - **تنوع الإجابات**: تجنب أن تكون الإجابة دائماً في الخيار الأول أو الثاني. قم بتوزيع المؤشر 'correctIndex' بشكل عشوائي تماماً وبشكل حقيقي بين 0 و 3.
            - إذا كان السؤال ذا طابع هندسي أو شبكات أو فايبر، يمكنك إضافة حقل "diagramType" بالقيمة الملائمة:
              * "ohms_law" لأسئلة قانون أوم والحسابات الكهربائية
              * "transformer" لأسئلة نسب تحويل ملفات المحولات
              * "ats_switch" لأسئلة التحويل التلقائي للطاقة من الباور والمولدات
              * "rectifier" لتأمين وفك تشويه تيار البطاريات -48V DC
              * "battery_series" لأسئلة بنوك بطاريات الرصاص المتسلسلة
              * "gpon_split" لتقسيم الفايبر والربط بين الـ OLT والـ ONT والـ Splitters
              * "otdr_curve" لقياس الوهن والانعكاسات على كوابيل الألياف
              * "ospf_topology" لتوجيه طبقات الشبكة وتوزيع عناوين IP والـ PAT/APIPA
            
            المطلوب:
            إنشاء مصفوفة أسئلة بصيغة JSON مطابقة تماماً للمواصفات التالية:
            1. كل سؤال يحتوي على حقول: 
               - "id": (شكل فريد يبدأ بـ ai_${randomSeed}_)
               - "text": (نص السؤال باللغة العربية الفصحى الفنية الرفيعة)
               - "options": (مصفوفة من 4 خيارات باللغة العربية)
               - "correctIndex": (مؤشر الإجابة الصحيحة من 0 إلى 3)
               - "explanation": (تبرير تفصيلي كامل باللغة العربية لحل السؤال مع ذكر القوانين)
               - "category": (إما "Fiber" أو "CCNA" أو "Power" حسب مضمون السؤال)
               - "englishText": (نص السؤال المترجم والمصاغ بأفضل لغة تقنية إنجليزية فنية)
               - "englishOptions": (مصفوفة من 4 خيارات باللغة الإنجليزية متوافقة بنفس الترتيب والمؤشر بالضبط)
               - "englishExplanation": (التبرير الهندسي الكامل والمفصّل باللغة الإنجليزية)
               - "diagramType": (اختياري، يمثل نوع الرسم البياني لتعزيز الفهم)
            2. يجب أن تكون مخرجاتك عبارة عن كود JSON صالح تماماً، ومنسق للتحليل المباشر (Raw JSON array of objects).
            3. تجنب تضمين أي نصوص خارج مصفوفة الـ JSON.
            
            مثال للمخرجات المطلوبة:
            [
              {
                "id": "ai_ex_01",
                "text": "السؤال المطور باللغة العربية؟",
                "options": ["خيار 1", "خيار 2", "خيار 3", "خيار 4"],
                "correctIndex": 2,
                "explanation": "التوضيح الهندسي باللغة العربية.",
                "category": "Power",
                "englishText": "The technical question in English?",
                "englishOptions": ["Option 1", "Option 2", "Option 3", "Option 4"],
                "englishExplanation": "The technical details in English.",
                "diagramType": "ohms_law"
              }
            ]
        """.trimIndent()

        // Construct JSON Request Body
        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                        if (attachedImageBytes != null && attachedImageMimeType != null) {
                            val base64Data = android.util.Base64.encodeToString(attachedImageBytes, android.util.Base64.NO_WRAP)
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", attachedImageMimeType)
                                    put("data", base64Data)
                                })
                            })
                        }
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 1.0)
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Gemini call failed: Code ${response.code}, Body: $errBody")
                    throw Exception("فشل الاتصال بالذكاء الاصطناعي: رمز ${response.code}")
                }

                val bodyString = response.body?.string() ?: throw Exception("مخرجات الذكاء الاصطناعي فارغة")
                Log.d(TAG, "Raw Gemini Response: $bodyString")

                // Parse response
                val responseJson = JSONObject(bodyString)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    throw Exception("الذكاء الاصطناعي لم يرجع أي مرشحين إجابات")
                }

                val contentObj = candidates.getJSONObject(0).getJSONObject("content")
                val parts = contentObj.getJSONArray("parts")
                if (parts.length() == 0) {
                    throw Exception("أجزاء الإجابة فارغة")
                }

                val responseText = parts.getJSONObject(0).getString("text").trim()
                Log.d(TAG, "Extracted Response Text: $responseText")

                // Parse the JSON array of questions
                val questionsArray = JSONArray(responseText)
                val generatedQuestions = mutableListOf<Question>()

                for (i in 0 until questionsArray.length()) {
                    val qObj = questionsArray.getJSONObject(i)
                    val id = qObj.optString("id", "ai_${System.currentTimeMillis()}_$i")
                    val text = qObj.getString("text")
                    
                    val optArray = qObj.getJSONArray("options")
                    val options = mutableListOf<String>()
                    for (j in 0 until optArray.length()) {
                        options.add(optArray.getString(j))
                    }
                    
                    val correctIndex = qObj.getInt("correctIndex")
                    val explanation = qObj.getString("explanation")
                    val category = qObj.optString("category", "Mixed")

                    // Opting for the bilingual translated versions
                    val englishText = qObj.optString("englishText", null).takeIf { it.isNotEmpty() }
                    
                    val optEnArray = qObj.optJSONArray("englishOptions")
                    val englishOptions = if (optEnArray != null) {
                        val list = mutableListOf<String>()
                        for (k in 0 until optEnArray.length()) {
                            list.add(optEnArray.getString(k))
                        }
                        list
                    } else null

                    val englishExplanation = qObj.optString("englishExplanation", null).takeIf { it.isNotEmpty() }

                    generatedQuestions.add(
                        Question(
                            id = id,
                            text = text,
                            options = options,
                            correctIndex = correctIndex,
                            explanation = explanation,
                            category = category,
                            englishText = englishText,
                            englishOptions = englishOptions,
                            englishExplanation = englishExplanation
                        )
                    )
                }

                generatedQuestions
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating custom exam", e)
            throw e
        }
    }

    /**
     * Evaluates a candidate's answer to an interview question.
     */
    suspend fun evaluateInterviewAnswer(
        questionText: String,
        userAnswerText: String
    ): InterviewEvaluation = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext getLocalFallbackEvaluation(questionText, userAnswerText)
        }
        val apiKey = BuildConfig.GEMINI_API_KEY
        val url = "$BASE_URL?key=$apiKey"

        val systemInstruction = "أنت كبير المهندسين وممتحن المقابلات الشخصية والهندسية في السنترال الرئيسي للشركة المصرية للاتصالات Telecom Egypt (WE). دورك هو تقييم إجابة المرشح بدقة علمية عالية مع تقديم النصح باللغتين."

        val prompt = """
            قم بتقييم إجابة المرشح على سؤال المقابلة التالي تقييماً هندسياً دقيقاً وهادئاً:
            
            السؤال المطروح في المقابلة:
            "$questionText"
            
            إجابة المرشح:
            "$userAnswerText"
            
            قم بإرجاع التقييم على شكل بصيغة JSON مطابقة تماماً للموديل التالي:
            {
               "score": 85,
               "modelAnswerAr": "...",
               "modelAnswerEn": "...",
               "analysisAr": "...",
               "analysisEn": "...",
               "tipsAr": "...",
               "tipsEn": "..."
            }
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Failed connection to evaluation server")
                }
                val bodyString = response.body?.string() ?: throw Exception("Empty evaluation text")
                val responseJson = JSONObject(bodyString)
                val candidates = responseJson.getJSONArray("candidates")
                val contentObj = candidates.getJSONObject(0).getJSONObject("content")
                val parts = contentObj.getJSONArray("parts")
                val responseText = parts.getJSONObject(0).getString("text").trim()

                val evalObj = JSONObject(responseText)
                InterviewEvaluation(
                    score = evalObj.optInt("score", 70),
                    modelAnswerAr = evalObj.optString("modelAnswerAr", "لا تتوفر إجابة نموذجية بالعربية حالياً."),
                    modelAnswerEn = evalObj.optString("modelAnswerEn", "No reference answer set currently."),
                    analysisAr = evalObj.optString("analysisAr", "تحليل عام لإجابة المرشح الهندسية."),
                    analysisEn = evalObj.optString("analysisEn", "General analysis on candidate output."),
                    tipsAr = evalObj.optString("tipsAr", "نصيحة عامة: تذكر دائماً ربط المفاهيم النظرية بالتطبيقات العملية في شبكات WE."),
                    tipsEn = evalObj.optString("tipsEn", "Tip: Always link theoretical concepts with practical deployment parameters.")
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Evaluation call failed, using offline fallback", e)
            getLocalFallbackEvaluation(questionText, userAnswerText)
        }
    }

    /**
     * Generates a comprehensive session-level report using Gemini based on the user's mock interview answers
     */
    suspend fun generateSessionDetailedFeedback(
        history: List<MockAnsweredItem>
    ): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            throw IllegalStateException("API Key missing")
        }
        val apiKey = BuildConfig.GEMINI_API_KEY
        val url = "$BASE_URL?key=$apiKey"
        
        val systemInstruction = "أنت كبير ممتحني واختصاصي الموارد البشرية والتقييم الفني في الشركة المصرية للاتصالات Telecom Egypt (WE). دورك هو مراجعة كامل أداء المشترك في جلسة المقابلة الشخصية وإعداد تقرير أداء نهائي شامل ومفصل باللغتين."
        
        val historyJson = JSONArray()
        history.forEach { item ->
            historyJson.put(JSONObject().apply {
                put("questionAr", item.questionAr)
                put("questionEn", item.questionEn)
                put("userAnswer", item.userAnswer)
                put("score", item.evaluation.score)
                put("analysisAr", item.evaluation.analysisAr)
                put("tipsAr", item.evaluation.tipsAr)
            })
        }
        
        val prompt = System.getProperty("line.separator") + """
            قم بتحليل وبناء تقرير جلسة تقييم شامل (Post-Session Performance Audit Report) متكامل للمرشح الذي أجرى لتوه مقابلة محاكاة فنية للقبول بالشركة المصرية للاتصالات.
            
            إليك سجل الأسئلة وإجابات المرشح مع تفاصيل تقييمها الفردي:
            ---
            ${historyJson.toString(2)}
            ---
            
            المطلوب:
            قم بكتابة تقرير أداء نهائي مفصل، موجه للمرشح، تلتزم فيه بالمعايير الفنية الصارمة لشركة Telecom Egypt (WE) من حيث الفايبر، الشبكات والباور.
            
            التقرير يجب أن يكون مقسماً بدقة إلى العناوين التالية (اكتب التقرير ثنائي اللغة، أو باللغة العربية أولاً وتحتها الترجمة الإنجليزية):
            
            1. 📊 الخلاصة العامة ومستوى القبول (Overall Summary & Acceptance Level):
               (حدد هل هو مهندس مستوى أول Senior فايبر، أم فني متوسط، أم يحتاج مراجعة فورية. اربطه بمتطلبات العمل في المصرية للاتصالات وسنترالاتها).
            
            2. 🌟 نقاط القوة الهندسية (Technical Strengths Demonstrated):
               (أبرز المواضيع التي أظهر فيها فهماً ممتازاً مع إشارة لأقواله وتبريره).
            
            3. ⚠️ الفجوات المعرفية التي تم رصدها (Knowledge Gaps & Pitfalls Found):
               (أبرز النقاط والمفاهيم التي تعثر فيها أو لم يذكر قيمها الدقيقة، مثل مستويات الفولت، الفقد، أو مفاهيم الشبكات الفرعية).
            
            4. 🚀 خارطة الطريق الفنية للتحسين (Custom Engineering Roadmap & Recommendations):
               (خطوات عملية دقيقة جداً من السنترال لتغطيتها، مثل مراجع الألياف G.657 أو صيانة الـ Rectifier والبطاريات).
            
            تجنب صيغ الـ JSON في هذا الرد؛ أرسل نصًا منسقًا بجمال عبر المزيج الذكي من النقاط Markdown للمستخدم المباشر.
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to contact Gemini evaluation engine")
            }
            val bodyString = response.body?.string() ?: throw Exception("Empty body")
            val responseJson = JSONObject(bodyString)
            val candidates = responseJson.getJSONArray("candidates")
            val contentObj = candidates.getJSONObject(0).getJSONObject("content")
            val parts = contentObj.getJSONArray("parts")
            parts.getJSONObject(0).getString("text").trim()
        }
    }

    private fun getLocalFallbackEvaluation(questionText: String, userAnswerText: String): InterviewEvaluation {
        val normalizedAnswer = userAnswerText.lowercase().trim()
        
        // Dynamic scoring based on technical keywords inside user answer
        var scoreScore = 45
        val matchedKeywords = mutableListOf<String>()
        val keywordsToCheck = listOf(
            "fiber", "gpon", "gpon", "olt", "ont", "db", "dbm", "attenuation", "g.652", "otdr", "loss", "splices",
            "subnet", "ip", "routing", "ospf", "vlan", "dhcp", "dns", "ping", "mac", "packet", "switch", "router",
            "power", "rectifier", "battery", "dc", "ac", "ups", "generator", "cooling", "amperes", "fuse", "mcb"
        )
        for (kw in keywordsToCheck) {
            if (normalizedAnswer.contains(kw)) {
                scoreScore += 8
                matchedKeywords.add(kw.uppercase())
            }
        }
        scoreScore = scoreScore.coerceAtMost(98)

        if (userAnswerText.isBlank()) {
            scoreScore = 0
        }

        val evalList = when {
            questionText.contains("fiber") || questionText.contains("الألياف") || questionText.contains("فايبر") -> {
                val arMod = "يفضل عند الجواب تبيان الطول الموجي المستخدم (1490nm للهابط و1310nm للصاعد) وأن طاقة الاستقبال اللائقة تتراوح بين -8 dBm و -27 dBm مع الاستشهاد بفقد الألياف النظري للألياف الأحادية النمط 0.2 dB/km."
                val enMod = "A perfect answer values operational wavelengths (1490nm DL, 1310nm UL), optical receive budgets between -8 and -27 dBm, and standard single-mode attenuation coefficients (0.2 dB/km @ 1550nm)."
                val arAnal = if (matchedKeywords.isNotEmpty()) {
                    "لقد قمت بشكل رائع بتطويع بعض المصطلحات الهندسية مثل: ${matchedKeywords.joinToString(", ")}. هذا يدل على فهم جيد للمصطلحات، ولكن يجب تدعيمها بالحسابات الرقمية للمشروع."
                } else {
                    "الإجابة عامة وتفتقر للمصطلحات الخاصة مثل OLT, ONT ودرجات الفقد المقبولة عند لحامات الفايبر بالصهر."
                }
                val enAnal = "Your explanation lacks structural measurements. Mentioning values like OTDR traces or optical splitting ratios would show comprehensive mastery."
                val arTips = "تأكد من مراجعة 'أطلس القوانين والمعادلات' بالصفحة الرئيسية للموجات والتعرف على كود الألوان المكون من 12 لوناً."
                val enTips = "We recommend checking the Formula atlas in the main screen to learn GPON receive thresholds and color coding standards."
                listOf(arMod, enMod, arAnal, enAnal, arTips, enTips)
            }
            questionText.contains("subnet") || questionText.contains("ccna") || questionText.contains("الشبكات") || questionText.contains("ip") -> {
                val arMod = "يجب تحديد شبكة الـ IP مع القناع الصحيح وعدد العناوين المتاحة، واستخدم المبدأ المعياري (2 أس N ناقص 2) لحساب مضيفين كل شبكة فرعية (hostsper subnet)."
                val enMod = "Always compute subnets using the mathematical block limits, i.e., math formula (2^N - 2) for usable hosts, identifying network, range, and broadcast interfaces cleanly."
                val arAnal = "إجابتك تحتوي على تفيد بالمصطلحات العامة للاتصال، ولكن تتطلب المقابلة الفنية دقة حسابية كاملة في تحديد قناع الشبكة الفرعية ومجالات البث."
                val enAnal = "Good attempt. Practice calculating network boundaries rapidly, identifying CIDR masks correctly is highly rated by WE interviewers."
                val arTips = "تدرب على حسابات الـ IP Subnetting السريعة ولا تخلط بين بروتوكولات المسار الداخلي OSPF والتوجيه الخارجي BGP."
                val enTips = "Be steady on calculating host ranges. Remember that OSPF uses Area 0 as the backbone for inter-area routing."
                listOf(arMod, enMod, arAnal, enAnal, arTips, enTips)
            }
            else -> {
                val arMod = "يجب تفصيل فولتية التشغيل المستمر (-48V DC) وطرق ربط وارتفاع كفاءة محول الـ Rectifier وأهمية الـ ATS (مفتاح التحويل التلقائي للقوى والباور) عند انقطاع التيار العمومي."
                val enMod = "You should explain the standard DC operational voltage (-48V DC), the operation of Rectifiers converting AC to stable DC, and automatic main power recovery utilizing ATS with backup battery banks."
                val arAnal = "الإجابة تفيد بمعرفتك للتيار العام والقوى والباور، ولكن مراجعة الفوارق الكهربية بين خلايا البطاريات المفتوحة والـ VRLA تمنحك تفوقاً كبيراً."
                val enAnal = "The answer demonstrates a general background. Clarify the rectifier cabinet's capacity calculations and cooling mechanics to impress the panel."
                val arTips = "ركز على مراجعة أحمال التيار بـ الأمبير وتأكد من حفظ شروط المولد الاحتياطي."
                val enTips = "Review the basic ohm's law calculations for DC power loads and standard rectifier power metrics."
                listOf(arMod, enMod, arAnal, enAnal, arTips, enTips)
            }
        }

        val modelAr = evalList[0]
        val modelEn = evalList[1]
        val analysisAr = evalList[2]
        val analysisEn = evalList[3]
        val tipsAr = evalList[4]
        val tipsEn = evalList[5]

        return InterviewEvaluation(
            score = scoreScore,
            modelAnswerAr = modelAr,
            modelAnswerEn = modelEn,
            analysisAr = analysisAr,
            analysisEn = analysisEn,
            tipsAr = tipsAr,
            tipsEn = tipsEn
        )
    }
}


