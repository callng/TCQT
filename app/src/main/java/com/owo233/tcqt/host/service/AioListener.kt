package com.owo233.tcqt.host.service

object AioListener : MessageHandler by RecallPushHandler(GoogleRecallMetadataDecoder)
