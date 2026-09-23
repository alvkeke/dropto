package cn.alvkeke.dropto.ui.listener

/**
 * Keeps a short sliding window of touch samples, so a released gesture can be
 * turned into the speed (px/ms) it ended with.
 */
class ReleaseVelocitySampler(private val windowMs: Long = 60L) {

    private val times = ArrayList<Long>()
    private val xs = ArrayList<Float>()
    private val ys = ArrayList<Float>()

    fun clear() {
        times.clear()
        xs.clear()
        ys.clear()
    }

    fun add(rawX: Float, rawY: Float) {
        val now = System.currentTimeMillis()
        times.add(now)
        xs.add(rawX)
        ys.add(rawY)
        while (times.size > 1 && now - times[0] > windowMs) {
            times.removeAt(0)
            xs.removeAt(0)
            ys.removeAt(0)
        }
    }

    fun recentReleaseVelocity(): Pair<Float, Float> {
        val n = times.size
        if (n < 2) return 0f to 0f
        val last = n - 1
        val tEnd = times[last]
        var start = 0
        while (start < last && tEnd - times[start] > windowMs) {
            start++
        }
        val dt = tEnd - times[start]
        if (dt <= 0L) return 0f to 0f
        return ((xs[last] - xs[start]) / dt) to
            ((ys[last] - ys[start]) / dt)
    }
}
