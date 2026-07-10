package com.dainon.skep.ui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 새 조회 화면들이 공유하는 프로그래밍 방식 UI 헬퍼 (XML 없이 카드 리스트 구성). */
object UiKit {

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /** 헤더(뒤로/타이틀) + 스크롤 본문 골격을 setContentView 하고, 본문 컨테이너를 반환. */
    fun page(act: Activity, title: String, onBack: (() -> Unit)?): LinearLayout {
        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF1F5F9.toInt())
        }
        val header = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF2563EB.toInt())
            setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16))
        }
        if (onBack != null) header.addView(TextView(act).apply {
            text = "‹"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, dp(act, 14), 0)
            setOnClickListener { onBack() }
        })
        header.addView(TextView(act).apply {
            text = title
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(header)

        val scroll = ScrollView(act).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val content = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 14), dp(act, 14), dp(act, 14), dp(act, 28))
        }
        scroll.addView(content)
        root.addView(scroll)
        act.setContentView(root)
        return content
    }

    fun sectionTitle(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 13f
        setTextColor(0xFF475569.toInt())
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(ctx, 2), dp(ctx, 16), 0, dp(ctx, 6))
    }

    fun hint(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 13f
        setTextColor(0xFF94A3B8.toInt())
        gravity = Gravity.CENTER
        setBackgroundColor(0xFFFFFFFF.toInt())
        setPadding(dp(ctx, 16), dp(ctx, 18), dp(ctx, 16), dp(ctx, 18))
    }

    fun primaryButton(ctx: Context, text: String, onClick: () -> Unit): Button = Button(ctx).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    /** 카드 한 줄: 제목 + (선택)부제 + (선택)우측 액션/뱃지 + (선택)클릭. */
    fun card(ctx: Context, title: String, subtitle: CharSequence?, action: String?, onClick: (() -> Unit)?): View {
        val cardView = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(ctx, 8) }
            if (onClick != null) setOnClickListener { onClick() }
        }
        val left = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(TextView(ctx).apply {
            text = title
            textSize = 14f
            setTextColor(0xFF0F172A.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        if (!subtitle.isNullOrBlank()) left.addView(TextView(ctx).apply {
            text = subtitle
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, dp(ctx, 3), 0, 0)
        })
        cardView.addView(left)
        if (action != null) cardView.addView(TextView(ctx).apply {
            text = action
            textSize = 12f
            setTextColor(0xFF2563EB.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        return cardView
    }

    /** 제목/부제 + 하단 버튼들(최대 2개) 카드. 수락·거절, 증빙 제출 등 액션용. */
    fun actionCard(ctx: Context, title: String, subtitle: CharSequence?, buttons: List<Pair<String, () -> Unit>>): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(ctx, 8) }
        }
        box.addView(TextView(ctx).apply {
            text = title; textSize = 14f; setTextColor(0xFF0F172A.toInt()); setTypeface(typeface, Typeface.BOLD)
        })
        if (!subtitle.isNullOrBlank()) box.addView(TextView(ctx).apply {
            text = subtitle; textSize = 12f; setTextColor(0xFF64748B.toInt()); setPadding(0, dp(ctx, 3), 0, 0)
        })
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(ctx, 8), 0, 0)
        }
        buttons.forEach { (label, onClick) ->
            row.addView(Button(ctx).apply {
                text = label; isAllCaps = false; textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginEnd = dp(ctx, 6) }
                setOnClickListener { onClick() }
            })
        }
        box.addView(row)
        return box
    }
}
