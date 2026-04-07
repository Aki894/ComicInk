package com.comicink.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition

/**
 * E-ink 优化图片 View
 * - 支持全刷新（消除残影）
 * - 简化手势（左右滑动手势翻页）
 * - 严格的内存管理
 */
class EInkImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    // 翻页回调
    var onPageChangeListener: OnPageChangeListener? = null

    // 手势检测
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val diffX = e2.x - (e1?.x ?: 0f)
            if (Math.abs(diffX) > 100) {
                if (diffX > 0) {
                    // 向右滑 -> 上一页
                    onPageChangeListener?.onPrevPage()
                } else {
                    // 向左滑 -> 下一页
                    onPageChangeListener?.onNextPage()
                }
                return true
            }
            return false
        }
    })

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let {
            // 居中绘制
            val left = (width - it.width) / 2f
            val top = (height - it.height) / 2f
            canvas.drawBitmap(it, left, top, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    /**
     * 加载图片
     * E-ink 优化：严格的内存管理
     */
    fun loadImage(url: String) {
        // 释放旧 Bitmap
        recycleBitmap()

        Glide.with(context)
            .asBitmap()
            .load(url)
            .into(object : SimpleTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    bitmap = resource
                    invalidate()
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    recycleBitmap()
                }

                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    // 加载失败处理
                    invalidate()
                }
            })
    }

    /**
     * E-ink 全刷新
     * 调用 invalidate() 强制重绘整个屏幕
     */
    fun fullRefresh() {
        invalidate()
    }

    /**
     * 释放 Bitmap 内存
     */
    private fun recycleBitmap() {
        bitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        bitmap = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        recycleBitmap()
    }

    interface OnPageChangeListener {
        fun onNextPage()
        fun onPrevPage()
    }
}