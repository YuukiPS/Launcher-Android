package yuuki.yuukips

import android.content.Context

object Utils {
    var isInit = false

    fun dp2px(context: Context, dpValue: Float): Int {
        val scale: Float = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }
}