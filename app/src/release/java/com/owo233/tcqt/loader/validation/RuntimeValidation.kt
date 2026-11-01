package com.owo233.tcqt.loader.validation

import android.app.Application

/** Release builds expose no debug receiver or validation entry point. */
internal object RuntimeValidation {
    fun install(app: Application) = Unit
}
