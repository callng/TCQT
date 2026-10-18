/**
 * Copyright 2024-2026 github@Suzhelan,github@leafmoes
 *
 * suzhelan@gmail.com
 *
 * It is forbidden to use the file and this source code for commercial purposes
 * please let me know before modifying the file and source code
 * If your project is open source, please indicate that this feature is from https://github.com/suzhelan/TimTool
 */
package top.artmoe.inao.item

import com.owo233.tcqt.host.service.KotlinxRecallMetadataDecoder
import com.owo233.tcqt.host.service.MessageHandler
import com.owo233.tcqt.host.service.RecallPushHandler

/** kotlinx.serialization decodes recall metadata; common handling preserves all other wire fields. */
object NewPreventRetractingMessageCore : MessageHandler by RecallPushHandler(KotlinxRecallMetadataDecoder)
