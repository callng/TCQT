package com.owo233.tcqt.core.env


internal object Toasts {

    const val ICON_DEFAULT: Int = 0
    const val ICON_DELETE_SUCCESS: Int = 4
    const val ICON_ERROR: Int = 1
    const val ICON_FAVORITE_SUCCESS: Int = 5
    const val ICON_NONE: Int = -1
    const val ICON_SEND_SUCCESS: Int = 3
    const val ICON_SUCCESS: Int = 2
    const val ICON_VOLUME_UP: Int = 6
    const val LENGTH_LONG: Int = 1
    const val LENGTH_SHORT: Int = 0

    @JvmStatic
    fun info(message: String) = HostServicesProvider.requireServices().showToast(ICON_DEFAULT, message)

    @JvmStatic
    fun success(message: String) = HostServicesProvider.requireServices().showToast(ICON_SUCCESS, message)

    @JvmStatic
    fun error(message: String) = HostServicesProvider.requireServices().showToast(ICON_ERROR, message)
}
