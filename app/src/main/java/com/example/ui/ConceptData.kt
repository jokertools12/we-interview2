package com.example.ui

// Additional concepts
val additionalConcepts = listOf(
    BasicConcept(
        id = "concept_power_01",
        titleAr = "قانون أوم في الدوائر الاحتياطية",
        titleEn = "Ohm's Law in Backup Circuits",
        category = "Power",
        summaryAr = "العلاقة الأساسية للجهد والتيار والمقاومة لتصميم دوائر الباور.",
        summaryEn = "Fundamental V=IR relationship for power circuit design.",
        detailedExplanationAr = "يستخدم قانون أوم V=IR لتحديد التيار المار في دوائر التغذية (-48V) ولحساب مقاطع الكابلات المناسبة لمنع الهبوط في الجهد.",
        detailedExplanationEn = "Ohm's Law V=IR is crucial for calculating current in -48V circuits and sizing cables correctly to prevent voltage drop.",
        diagramType = null
    ),
    BasicConcept(
        id = "concept_ccna_01",
        titleAr = "مبدأ عمل الـ VLANs",
        titleEn = "VLAN Operation Principle",
        category = "CCNA",
        summaryAr = "تقسيم منطقي للسويتش لعزل حركة المرور.",
        summaryEn = "Logical segmentation of a switch to isolate traffic.",
        detailedExplanationAr = "تسمح الـ VLANs بتقسيم الشبكة المادية الواحدة إلى شوكات منطقية منعزلة، مما يزيد الأمان ويقلل ازدحام الـ Broadcast.",
        detailedExplanationEn = "VLANs allow partitioning a single physical network into isolated logical LANs, enhancing security and reducing broadcast traffic.",
        diagramType = null
    ),
    BasicConcept(
        id = "concept_fiber_13",
        titleAr = "أنواع الوصلات الضوئية (SC, LC, ST)",
        titleEn = "Fiber Connector Types (SC, LC, ST)",
        category = "Fiber",
        summaryAr = "أنواع الموصلات الضوئية المستخدمة في شبكات الفايبر.",
        summaryEn = "Common fiber connector types used in optical networks.",
        detailedExplanationAr = "تختلف الموصلات بناءً على حجم الفيرول (Ferrule) وآلية التعشيق. SC (Subscriber Connector) يكثر في الـ FAT، و LC (Lucent Connector) صغير مدمج يستخدم في كروت OLT، و ST (Straight Tip) نوع قديم بآلية بيونيت.",
        detailedExplanationEn = "Connectors vary by ferrule size and latching mechanism. SC is prevalent in outdoor FATs, LC is small factor used in OLT ports, and ST is a legacy bayonet-style connector.",
        diagramType = null
    )
    // Add many more...
)
