package app.goals

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

// ضع false لو عايز الودجت من الشمال لليمين (سبت على الشمال)
private const val RTL = true

private val MONTHS = arrayOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
private val DAYS = arrayOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة")
private val ACCENT = Color.parseColor("#4A7BFF")

private class T(val title: String, val date: Int, val done: Boolean)
private class G(val id: String, val title: String, val color: Int, val s: Int, val e: Int, val long: Boolean, val parent: String, val tasks: List<T>)
private class Seg(val g: G, val a: Int, val b: Int, val s: Int, val e: Int, var r: Int = 0)
private class Item(val s: Int, val e: Int, val g: G?, val seg: Seg?, val kids: List<Seg>, var row: Int = 0, var h: Float = 0f)

class GoalsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) update(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        update(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            for (id in m.getAppWidgetIds(ComponentName(c, GoalsWidget::class.java))) update(c, m, id)
        }

        fun update(c: Context, m: AppWidgetManager, id: Int) {
            val o = m.getAppWidgetOptions(id)
            val d = c.resources.displayMetrics.density
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300).coerceAtLeast(200)
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300).coerceAtLeast(200)
            val bmp = Bitmap.createBitmap((wDp * d).toInt(), (hDp * d).toInt(), Bitmap.Config.ARGB_8888)
            try {
                draw(Canvas(bmp), bmp.width.toFloat(), bmp.height.toFloat(), d, c)
            } catch (e: Exception) {
            }
            val rv = RemoteViews(c.packageName, R.layout.widget)
            rv.setImageViewBitmap(R.id.img, bmp)
            val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.root, pi)
            m.updateAppWidget(id, rv)
        }
    }
}

private fun ymd(y0: Int, m: Int, d: Int): Int {
    val y = if (m <= 2) y0 - 1 else y0
    val era = Math.floorDiv(y, 400)
    val yoe = y - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

private fun days(s: String): Int {
    val p = s.split("-")
    return ymd(p[0].toInt(), p[1].toInt(), p[2].toInt())
}

private fun mix(a: Int, b: Int, t: Float): Int {
    fun m(x: Int, y: Int) = (x + (y - x) * t).toInt()
    return Color.rgb(m(Color.red(a), Color.red(b)), m(Color.green(a), Color.green(b)), m(Color.blue(a), Color.blue(b)))
}

private fun parseGoals(arr: JSONArray): List<G> {
    val out = ArrayList<G>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        try {
            val ts = ArrayList<T>()
            val ta = o.optJSONArray("tasks") ?: JSONArray()
            for (j in 0 until ta.length()) {
                val t = ta.getJSONObject(j)
                ts.add(T(t.optString("title"), days(t.getString("date")), t.optBoolean("done")))
            }
            out.add(G(o.getString("id"), o.optString("title"), Color.parseColor(o.optString("color", "#4a7bff")), days(o.getString("start")), days(o.getString("end")), o.optBoolean("long"), o.optString("parent"), ts))
        } catch (e: Exception) {
        }
    }
    return out
}

private fun draw(cv: Canvas, W: Float, H: Float, d: Float, c: Context) {
    fun dp(v: Float) = v * d
    val root = try {
        JSONObject(c.getSharedPreferences("g", 0).getString("d", "") ?: "")
    } catch (e: Exception) {
        JSONObject()
    }
    val goals = parseGoals(root.optJSONArray("goals") ?: JSONArray())
    val byId = goals.associateBy { it.id }
    fun col(g: G): Int = byId[g.parent]?.color ?: g.color
    fun kids(g: G): List<G> = goals.filter { it.parent == g.id }
    fun prog(g: G): Float {
        val ts = if (g.long) kids(g).flatMap { it.tasks } else g.tasks
        return if (ts.isEmpty()) 0f else ts.count { it.done }.toFloat() / ts.size
    }
    val loose = ArrayList<T>()
    val la = root.optJSONArray("loose") ?: JSONArray()
    for (i in 0 until la.length()) {
        val o = la.optJSONObject(i) ?: continue
        try {
            loose.add(T(o.optString("title"), days(o.getString("date")), o.optBoolean("done")))
        } catch (e: Exception) {
        }
    }

    val now = Calendar.getInstance()
    val today = ymd(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
    val ws = today - Math.floorMod(today + 5, 7)
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    fun cal(day: Int): Calendar {
        utc.timeInMillis = day * 86400000L
        return utc
    }

    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 255, 255, 255) }
    val strike = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 255, 255, 255)
        flags = flags or Paint.STRIKE_THRU_TEXT_FLAG
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    fun text(s: String, x: Float, y: Float, size: Float, p: Paint, align: Paint.Align, b: Boolean = false) {
        p.textSize = dp(size)
        p.textAlign = align
        p.typeface = if (b) bold else Typeface.DEFAULT
        cv.drawText(s, x, y, p)
    }

    val pad = dp(16f)
    val colW = (W - 2 * pad) / 7
    fun xl(i: Int): Float = if (RTL) pad + (6 - i) * colW else pad + i * colW
    val startAlign = if (RTL) Paint.Align.RIGHT else Paint.Align.LEFT
    var y = dp(12f)

    // الهيدر: أيقونة التاريخ + اسم الشهر
    val ix = if (RTL) W - pad - dp(26f) else pad
    stroke.color = Color.WHITE
    stroke.strokeWidth = dp(2f)
    cv.drawRoundRect(RectF(ix, y, ix + dp(26f), y + dp(26f)), dp(7f), dp(7f), stroke)
    text(cal(today).get(Calendar.DAY_OF_MONTH).toString(), ix + dp(13f), y + dp(18f), 11f, white, Paint.Align.CENTER, true)
    text(MONTHS[cal(today).get(Calendar.MONTH)], W / 2, y + dp(22f), 22f, white, Paint.Align.CENTER, true)
    y += dp(42f)

    // الأهداف فوق أسماء الأيام
    val barH = dp(22f)
    val gap = dp(4f)
    val segs = goals.mapNotNull { g ->
        val a = g.s - ws
        val b = g.e - ws
        if (b < 0 || a > 6) null else Seg(g, a, b, maxOf(a, 0), minOf(b, 6))
    }
    fun <X> pack(list: List<X>, s: (X) -> Int, e: (X) -> Int, set: (X, Int) -> Unit): Int {
        val ends = ArrayList<Int>()
        for (x in list.sortedWith(compareBy<X>({ s(it) }, { -e(it) }))) {
            var r = ends.indexOfFirst { en -> en < s(x) }
            if (r < 0) {
                ends.add(e(x))
                r = ends.size - 1
            } else ends[r] = e(x)
            set(x, r)
        }
        return ends.size
    }
    val items = ArrayList<Item>()
    for (g in goals.filter { it.long }) {
        val ks = segs.filter { it.g.parent == g.id }
        val ls = segs.firstOrNull { it.g.id == g.id }
        val all = ks + listOfNotNull(ls)
        if (all.isEmpty()) continue
        val rows = pack(ks, { it.s }, { it.e }, { x, r -> x.r = r })
        items.add(Item(all.minOf { it.s }, all.maxOf { it.e }, g, null, ks, h = dp(22f) + rows * barH + maxOf(rows - 1, 0) * gap))
    }
    for (sg in segs) {
        if (!sg.g.long && byId[sg.g.parent] == null) items.add(Item(sg.s, sg.e, null, sg, emptyList(), h = barH))
    }
    val nrows = pack(items, { it.s }, { it.e }, { x, r -> x.row = r })
    val rowH = FloatArray(maxOf(nrows, 1))
    for (im in items) rowH[im.row] = maxOf(rowH[im.row], im.h)
    val rowY = FloatArray(rowH.size)
    var acc = y
    for (r in rowH.indices) {
        rowY[r] = acc
        if (r < nrows) acc += rowH[r] + gap
    }
    y = acc + dp(2f)

    fun drawBar(sg: Seg, top: Float, color: Int) {
        val left = (if (RTL) xl(sg.e) else xl(sg.s)) + 1
        val w = (sg.e - sg.s + 1) * colW - 2
        val rr = RectF(left, top, left + w, top + barH)
        val p = prog(sg.g)
        val f = ((p * (sg.b - sg.a + 1) - (sg.s - sg.a)) / (sg.e - sg.s + 1)).coerceIn(0f, 1f)
        val path = Path().apply { addRoundRect(rr, barH / 2, barH / 2, Path.Direction.CW) }
        cv.save()
        cv.clipPath(path)
        fill.style = Paint.Style.FILL
        fill.color = Color.argb(100, 0, 0, 0)
        cv.drawRect(rr, fill)
        fill.color = color
        if (RTL) cv.drawRect(rr.right - f * w, rr.top, rr.right, rr.bottom, fill) else cv.drawRect(rr.left, rr.top, rr.left + f * w, rr.bottom, fill)
        text(sg.g.title + " · " + Math.round(p * 100) + "%", if (RTL) rr.right - dp(8f) else rr.left + dp(8f), top + barH * 0.7f, 11f, white, startAlign, true)
        cv.restore()
    }

    for (im in items) {
        val g = im.g
        if (g != null) {
            val p = prog(g)
            val left = (if (RTL) xl(im.e) else xl(im.s)) + 1
            val w = (im.e - im.s + 1) * colW - 2
            val top = rowY[im.row]
            val rr = RectF(left, top, left + w, top + im.h)
            fill.style = Paint.Style.FILL
            fill.color = g.color
            fill.alpha = (255 * (0.15f + 0.85f * p)).toInt()
            cv.drawRoundRect(rr, dp(14f), dp(14f), fill)
            stroke.color = g.color
            stroke.strokeWidth = dp(1.5f)
            cv.drawRoundRect(rr, dp(14f), dp(14f), stroke)
            cv.save()
            cv.clipRect(rr)
            text(g.title + " · " + Math.round(p * 100) + "%", if (RTL) rr.right - dp(10f) else rr.left + dp(10f), top + dp(14f), 10f, white, startAlign, true)
            cv.restore()
            val ks = kids(g)
            for (k in im.kids) {
                val sc = when (ks.indexOf(k.g) % 3) {
                    0 -> mix(g.color, Color.WHITE, 0.4f)
                    1 -> g.color
                    else -> mix(g.color, Color.BLACK, 0.3f)
                }
                drawBar(k, top + dp(18f) + k.r * (barH + gap), sc)
            }
        } else {
            val sg = im.seg
            if (sg != null) drawBar(sg, rowY[im.row], col(sg.g))
        }
    }

    // أسماء الأيام
    for (i in 0..6) text(DAYS[i], xl(i) + colW / 2, y + dp(14f), 12f, muted, Paint.Align.CENTER)
    y += dp(22f)

    // أرقام الأيام + نقط المهام
    val dots = HashMap<Int, ArrayList<Int>>()
    for (g in goals) {
        if (g.long) continue
        for (t in g.tasks) dots.getOrPut(t.date) { ArrayList() }.add(col(g))
    }
    for (t in loose) dots.getOrPut(t.date) { ArrayList() }.add(Color.argb(200, 255, 255, 255))
    for (i in 0..6) {
        val cx = xl(i) + colW / 2
        val cy = y + dp(22f)
        if (ws + i == today) {
            fill.style = Paint.Style.FILL
            fill.color = ACCENT
            cv.drawCircle(cx, cy, dp(20f), fill)
        }
        text(cal(ws + i).get(Calendar.DAY_OF_MONTH).toString(), cx, cy + dp(7f), 20f, white, Paint.Align.CENTER)
        val ds = dots[ws + i]
        if (ds != null && ws + i != today) {
            val n = minOf(ds.size, 3)
            for (j in 0 until n) {
                fill.color = ds[j]
                cv.drawCircle(cx + (j - (n - 1) / 2f) * dp(7f), cy + dp(27f), dp(2.2f), fill)
            }
        }
    }
    y += dp(52f)

    stroke.color = Color.argb(45, 255, 255, 255)
    stroke.strokeWidth = dp(1f)
    cv.drawLine(pad, y, W - pad, y, stroke)
    y += dp(8f)

    // مهام اليوم
    class Row(val title: String, val done: Boolean, val color: Int, val tag: String)
    val rows = ArrayList<Row>()
    for (g in goals) for (t in g.tasks) if (t.date == today) rows.add(Row(t.title, t.done, col(g), g.title))
    for (t in loose) if (t.date == today) rows.add(Row(t.title, t.done, Color.argb(210, 255, 255, 255), ""))
    val rowStep = dp(36f)
    if (rows.isEmpty()) {
        text("لا توجد مهام اليوم", W / 2, y + dp(46f), 17f, white, Paint.Align.CENTER)
        return
    }
    val fit = maxOf(((H - dp(8f) - y) / rowStep).toInt(), 1)
    val shown = if (rows.size > fit) fit - 1 else rows.size
    for (i in 0 until shown) {
        val r = rows[i]
        val cy = y + i * rowStep + rowStep / 2
        val cx = if (RTL) W - pad - dp(10f) else pad + dp(10f)
        stroke.color = r.color
        stroke.strokeWidth = dp(1.6f)
        if (r.done) {
            fill.style = Paint.Style.FILL
            fill.color = r.color
            cv.drawCircle(cx, cy, dp(9f), fill)
            val ck = Path().apply {
                moveTo(cx - dp(4f), cy)
                lineTo(cx - dp(1f), cy + dp(3f))
                lineTo(cx + dp(4f), cy - dp(3f))
            }
            stroke.color = Color.WHITE
            cv.drawPath(ck, stroke)
        } else {
            cv.drawCircle(cx, cy, dp(9f), stroke)
        }
        val tx = if (RTL) W - pad - dp(30f) else pad + dp(30f)
        val tagW = if (r.tag.isEmpty()) 0f else dp(90f)
        cv.save()
        if (RTL) cv.clipRect(pad + tagW, cy - rowStep / 2, tx + 2, cy + rowStep / 2) else cv.clipRect(tx - 2, cy - rowStep / 2, W - pad - tagW, cy + rowStep / 2)
        text(r.title, tx, cy + dp(6f), 16f, if (r.done) strike else white, startAlign)
        cv.restore()
        if (r.tag.isNotEmpty()) {
            cv.save()
            if (RTL) cv.clipRect(pad, cy - rowStep / 2, pad + tagW, cy + rowStep / 2) else cv.clipRect(W - pad - tagW, cy - rowStep / 2, W - pad, cy + rowStep / 2)
            text(r.tag, if (RTL) pad else W - pad, cy + dp(5f), 12f, muted, if (RTL) Paint.Align.LEFT else Paint.Align.RIGHT)
            cv.restore()
        }
    }
    if (rows.size > shown) text("+" + (rows.size - shown) + " أخرى", W / 2, y + shown * rowStep + dp(22f), 13f, muted, Paint.Align.CENTER)
}
