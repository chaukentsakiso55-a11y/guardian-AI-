package com.cyberpulse.guardianai

val appSpec = AppSpec(
    name = "Guardian AI",
    shortName = "GA",
    tagline = "Safer digital spaces for growing minds.",
    hero = "Bring protection, digital wellbeing and trusted guidance into one clear family dashboard.",
    primary = 0xFF48F0D0,
    secondary = 0xFF6787FF,
    focusLabel = "Digital wellbeing session",
    logHint = "Record a safety concern or family rule",
    features = listOf(
        AppFeature("Safety Shield", "Prepare on-device protection and clear safety status.", "SAFE"),
        AppFeature("Content Rules", "Define age-appropriate boundaries transparently.", "RULES"),
        AppFeature("Risk Signals", "Organize future phishing and cyberbullying warnings.", "ALERT"),
        AppFeature("Wellbeing", "Balance screen time with healthy digital habits.", "CARE"),
        AppFeature("Family View", "Prepare trusted summaries for connected guardians.", "FAMILY"),
        AppFeature("Privacy", "Keep child-safety data minimal and controlled.", "PRIVATE")
    ),
    metrics = listOf(
        AppMetric("Protection", "Prepared"),
        AppMetric("Rules", "Local"),
        AppMetric("Monitoring", "Off"),
        AppMetric("Family sync", "Phase 2")
    ),
    about = "Guardian AI is a Cyber Pulse child-safety and digital-wellbeing product. This foundation does not claim to monitor content yet; sensitive permissions and detection will be added only with visible consent and testing."
)
