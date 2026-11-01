package com.owo233.tcqt.annotations

/** Process selection shared by source annotations and generated runtime metadata. */
enum class ActionProcess { MSF, MAIN, TOOL, OPENSDK, QZONE, QQFAV, OTHER, ALL }

/** CRITICAL runs during Application.onCreate; other phases are scheduled afterwards. */
enum class ActionPriority { CRITICAL, EARLY, DEFERRED, BACKGROUND }
