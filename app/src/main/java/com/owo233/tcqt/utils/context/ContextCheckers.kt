package com.owo233.tcqt.utils.context

//import android.content.Context
//import androidx.appcompat.app.AppCompatActivity
//import androidx.appcompat.R as AR
//import com.google.android.material.R

/*object ContextCheckers {

    private val MATERIAL_ATTRS = intArrayOf(R.attr.colorPrimaryVariant)

    fun isAppCompatContext(context: Context): Boolean {
        return try {
            context.classLoader
                ?.loadClass(AppCompatActivity::class.java.name) ==
                    AppCompatActivity::class.java &&
                    context.obtainStyledAttributes(AR.styleable.AppCompatTheme).run {
                        try {
                            hasValue(AR.styleable.AppCompatTheme_windowActionBar)
                        } finally {
                            recycle()
                        }
                    }
        } catch (_: Throwable) {
            false
        }
    }

    fun isMaterialContext(context: Context): Boolean {
        if (!isAppCompatContext(context)) return false
        val a = context.obtainStyledAttributes(MATERIAL_ATTRS)
        return try {
            a.hasValue(0)
        } finally {
            a.recycle()
        }
    }
}*/
