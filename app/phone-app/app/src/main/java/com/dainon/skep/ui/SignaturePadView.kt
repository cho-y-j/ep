package com.dainon.skep.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** 손가락 사인용 단순 캔버스. toPngBytes() 로 PNG 추출. */
class SignaturePadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val path = Path()
    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private var hasInk = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { path.moveTo(event.x, event.y); hasInk = true }
            MotionEvent.ACTION_MOVE -> path.lineTo(event.x, event.y)
        }
        invalidate()
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        canvas.drawPath(path, paint)
    }

    fun clear() {
        path.reset()
        hasInk = false
        invalidate()
    }

    fun isEmpty(): Boolean = !hasInk

    fun toPngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.drawPath(path, paint)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
