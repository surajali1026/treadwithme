package com.example.ui.chart

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AlertItem
import com.example.data.model.Candle
import com.example.data.model.CandlePriceSource
import com.example.data.model.ChartPoint
import com.example.data.model.ChartScaleSettings
import com.example.data.model.DrawingItem
import com.example.data.model.DrawingToolType
import com.example.data.model.PriceInfo
import com.example.data.model.ScalePosition
import com.example.data.model.Timeframe
import com.example.ui.theme.AlertLineColor
import com.example.ui.theme.BearRed
import com.example.ui.theme.BinanceGold
import com.example.ui.theme.BullGreen
import com.example.ui.theme.CrosshairColor
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GridLineColor
import com.example.ui.theme.IndexPricePurple
import com.example.ui.theme.MarkPriceCyan
import com.example.ui.theme.TVBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CandlestickChart(
    symbol: String,
    candles: List<Candle>,
    timeframe: Timeframe,
    priceInfo: PriceInfo,
    candlePriceSource: CandlePriceSource = CandlePriceSource.LAST,
    settings: ChartScaleSettings,
    alerts: List<AlertItem>,
    drawings: List<DrawingItem>,
    activeDrawingTool: DrawingToolType,
    areDrawingsHidden: Boolean,
    selectedDrawing: DrawingItem?,
    onSelectDrawing: (DrawingItem?) -> Unit,
    onAddDrawing: (DrawingItem) -> Unit,
    onUpdateDrawing: (DrawingItem) -> Unit,
    onLoadMoreHistory: () -> Unit,
    onSelectCandleForReplay: ((Int) -> Unit)? = null,
    onOpenCircularTimeframe: (Offset) -> Unit,
    onResetToolToCursor: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Chart view state: horizontal pan (scrollOffset in candles), zoom (candleWidth in pixels)
    var candleWidth by remember { mutableFloatStateOf(16f) }
    var scrollOffset by remember { mutableFloatStateOf(0f) } // 0 = rightmost (latest); negative = future empty space
    var verticalPanOffset by remember { mutableFloatStateOf(0f) }
    var verticalScaleRatio by remember { mutableFloatStateOf(1.0f) }

    // Pre-allocated Paints to eliminate GC allocations inside 60 FPS draw loops
    val watermarkPaint = remember {
        Paint().apply {
            color = android.graphics.Color.argb((0.04f * 255).toInt(), 255, 255, 255)
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val tfWatermarkPaint = remember {
        Paint().apply {
            color = android.graphics.Color.argb((0.04f * 255).toInt(), 255, 255, 255)
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val ohlcPaint = remember {
        Paint().apply {
            textSize = 22f
            isAntiAlias = true
        }
    }
    val scaleTextPaint = remember {
        Paint().apply {
            color = TextSecondary.toArgb()
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val timeTextPaint = remember {
        Paint().apply {
            color = TextSecondary.toArgb()
            textSize = 19f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val priceTagPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val alertScalePaint = remember {
        Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val emptyConnectingPaint = remember {
        Paint().apply {
            color = TextSecondary.toArgb()
            textSize = 36f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val timeFormat = remember(timeframe.id, timeframe.isSeconds) {
        if (timeframe.isSeconds) {
            SimpleDateFormat("HH:mm:ss", Locale.US)
        } else if (timeframe.durationMs < 86_400_000L) {
            SimpleDateFormat("HH:mm", Locale.US)
        } else {
            SimpleDateFormat("MM/dd", Locale.US)
        }
    }
    val reusableDate = remember { Date() }

    // Crosshair state
    var isCrosshairEnabled by remember { mutableStateOf(false) }
    var crosshairPos by remember { mutableStateOf<Offset?>(null) }
    var hoveredCandle by remember { mutableStateOf<Candle?>(null) }

    // Bar Time Countdown Ticker (updates every 500ms to stay accurately in sync)
    var currentTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMs = System.currentTimeMillis()
            delay(500L)
        }
    }

    val countdownPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    // Active drawing in-progress points
    val inProgressPoints = remember { mutableStateListOf<ChartPoint>() }

    // Clear any stray in-progress drawing points when switching back to CURSOR mode
    LaunchedEffect(activeDrawingTool) {
        if (activeDrawingTool == DrawingToolType.CURSOR) {
            inProgressPoints.clear()
        }
    }

    val timeScaleHeight = 44f
    val priceScaleWidth = 145f
    val density = LocalDensity.current
    val priceScaleWidthDp = with(density) { priceScaleWidth.toDp() }
    val timeScaleHeightDp = with(density) { timeScaleHeight.toDp() }
    val isRightScale = settings.scalePosition == ScalePosition.RIGHT

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val timeScaleHeight = 44f
            val priceScaleWidth = 145f

            val isRightScale = settings.scalePosition == ScalePosition.RIGHT
            val chartLeft = if (isRightScale) 0f else priceScaleWidth
            val chartRight = if (isRightScale) width - priceScaleWidth else width
            val chartWidth = chartRight - chartLeft
            val chartHeight = height - timeScaleHeight

            if (candles.isEmpty()) {
                drawContext.canvas.nativeCanvas.drawText(
                    "Connecting to Binance USD-M Futures...",
                    chartWidth / 2f,
                    chartHeight / 2f,
                    emptyConnectingPaint
                )
                return@Canvas
            }

            val candleSpacing = candleWidth * 0.25f
            val totalSlotWidth = candleWidth + candleSpacing
            val visibleCount = (chartWidth / totalSlotWidth).toInt() + 2

            val rightIndex = (candles.lastIndex - scrollOffset.toInt()).coerceIn(0, candles.lastIndex)
            val leftIndex = (rightIndex - visibleCount).coerceAtLeast(0)
            val visibleCandles = candles.subList(leftIndex, rightIndex + 1)

            if (visibleCandles.isEmpty()) return@Canvas

            // Compute visible min & max price
            var minP = Double.MAX_VALUE
            var maxP = Double.MIN_VALUE
            var maxVol = 0.0

            if (settings.autoScale || settings.manualMinPrice == null || settings.manualMaxPrice == null) {
                for (c in visibleCandles) {
                    if (c.low < minP) minP = c.low
                    if (c.high > maxP) maxP = c.high
                    if (c.volume > maxVol) maxVol = c.volume
                }
                // Add vertical padding
                val range = (maxP - minP).coerceAtLeast(0.0001)
                minP -= range * 0.08
                maxP += range * 0.08
            } else {
                minP = settings.manualMinPrice
                maxP = settings.manualMaxPrice
                for (c in visibleCandles) {
                    if (c.volume > maxVol) maxVol = c.volume
                }
            }

            val priceRange = (maxP - minP).coerceAtLeast(0.000001)
            val scaledRange = (priceRange / verticalScaleRatio).coerceAtLeast(0.000001)
            val midP = (minP + maxP) / 2.0
            val effMinP = midP - (scaledRange / 2.0)
            val effMaxP = midP + (scaledRange / 2.0)
            val basePrice = visibleCandles.first().close

            fun priceToY(p: Double): Float {
                var norm = if (settings.isLogarithmic) {
                    val logP = ln(p.coerceAtLeast(0.00001))
                    val logMin = ln(effMinP.coerceAtLeast(0.00001))
                    val logMax = ln(effMaxP.coerceAtLeast(0.00001))
                    ((logP - logMin) / (logMax - logMin)).toFloat()
                } else {
                    ((p - effMinP) / scaledRange).toFloat()
                }

                if (settings.isInverted) {
                    norm = 1f - norm
                }

                val y = chartHeight - (norm * chartHeight) + verticalPanOffset
                return y
            }

            fun yToPrice(y: Float): Double {
                val adjY = y - verticalPanOffset
                var norm = 1f - (adjY / chartHeight)
                if (settings.isInverted) norm = 1f - norm
                norm = norm.coerceIn(0f, 1f)

                return if (settings.isLogarithmic) {
                    val logMin = ln(effMinP.coerceAtLeast(0.00001))
                    val logMax = ln(effMaxP.coerceAtLeast(0.00001))
                    Math.exp((norm * (logMax - logMin) + logMin).toDouble())
                } else {
                    (effMinP + norm * scaledRange)
                }
            }

            fun timeToX(t: Long): Float {
                if (candles.isEmpty()) return chartRight
                val rightIdxFloat = (candles.lastIndex.toFloat() - scrollOffset)
                val firstT = candles.first().openTime
                val lastT = candles.last().openTime
                val avgSlotMs = if (candles.size > 1) (lastT - firstT).toDouble() / (candles.size - 1) else timeframe.durationMs.toDouble()

                val candleIdxFloat: Float = if (t <= firstT) {
                    val diff = (t - firstT) / avgSlotMs
                    diff.toFloat()
                } else if (t >= lastT) {
                    val diff = (t - lastT) / avgSlotMs
                    (candles.lastIndex).toFloat() + diff.toFloat()
                } else {
                    var low = 0
                    var high = candles.lastIndex
                    while (low <= high) {
                        val mid = (low + high) ushr 1
                        if (candles[mid].openTime == t) {
                            low = mid
                            high = mid - 1
                            break
                        } else if (candles[mid].openTime < t) {
                            low = mid + 1
                        } else {
                            high = mid - 1
                        }
                    }
                    val prev = (low - 1).coerceIn(0, candles.lastIndex)
                    val next = low.coerceIn(0, candles.lastIndex)
                    if (prev == next) {
                        prev.toFloat()
                    } else {
                        val span = (candles[next].openTime - candles[prev].openTime).coerceAtLeast(1)
                        val frac = (t - candles[prev].openTime).toFloat() / span
                        prev.toFloat() + frac
                    }
                }
                val dist = rightIdxFloat - candleIdxFloat
                return chartRight - (dist * totalSlotWidth) - (candleWidth / 2f)
            }

            // 1. Watermark background showing Symbol, Timeframe, and Candle Price Source
            if (settings.showWatermark) {
                watermarkPaint.textSize = chartWidth * 0.12f
                drawContext.canvas.nativeCanvas.drawText(
                    symbol,
                    chartLeft + chartWidth / 2f,
                    chartHeight * 0.42f,
                    watermarkPaint
                )
                tfWatermarkPaint.textSize = chartWidth * 0.06f
                val sourceLabel = when (candlePriceSource) {
                    CandlePriceSource.LAST -> "${timeframe.displayName} • LAST"
                    CandlePriceSource.MARK -> "${timeframe.displayName} • MARK PRICE"
                    CandlePriceSource.INDEX -> "${timeframe.displayName} • INDEX PRICE"
                }
                drawContext.canvas.nativeCanvas.drawText(
                    sourceLabel,
                    chartLeft + chartWidth / 2f,
                    chartHeight * 0.54f,
                    tfWatermarkPaint
                )
            }

            // Shared chart states and price calculations
            val latestCandle = candles.lastOrNull()
            val lastP = if (priceInfo.lastPrice > 0.0) priceInfo.lastPrice else latestCandle?.close ?: 0.0
            val activeAlerts = alerts.filter { it.symbol.equals(symbol, ignoreCase = true) && it.isActive }

            // 2. Grid lines, Volume, Candles, and User Drawings strictly clipped to the chart content viewport
            clipRect(left = chartLeft, top = 0f, right = chartRight, bottom = chartHeight) {
                if (settings.showGrid) {
                    val numGridSteps = 7
                    val stepY = chartHeight / (numGridSteps + 1)
                    for (i in 1..numGridSteps) {
                        val y = i * stepY
                        drawLine(
                            color = GridLineColor,
                            start = Offset(chartLeft, y),
                            end = Offset(chartRight, y),
                            strokeWidth = 1f
                        )
                    }

                    // Vertical time grid lines
                    for (i in rightIndex downTo leftIndex step 10) {
                        val dist = rightIndex - i
                        val x = chartRight - (dist * totalSlotWidth)
                        drawLine(
                            color = GridLineColor,
                            start = Offset(x, 0f),
                            end = Offset(x, chartHeight),
                            strokeWidth = 1f
                        )
                    }
                }

                // 3. Volume Bars (Bottom ~18%)
                if (settings.showVolume && maxVol > 0.0) {
                    val volHeight = chartHeight * 0.18f
                    for (i in rightIndex downTo leftIndex) {
                        val candle = candles[i]
                        val distFromRight = (candles.lastIndex.toFloat() - scrollOffset) - i
                        val x = chartRight - (distFromRight * totalSlotWidth) - candleWidth
                        val barH = ((candle.volume / maxVol) * volHeight).toFloat()
                        val y = chartHeight - barH

                        val volColor = if (candle.isBullish)
                            BullGreen.copy(alpha = 0.3f)
                        else
                            BearRed.copy(alpha = 0.3f)

                        drawRect(
                            color = volColor,
                            topLeft = Offset(x, y),
                            size = Size(candleWidth, barH)
                        )
                    }
                }

                // 4. Candlesticks
                for (i in rightIndex downTo leftIndex) {
                    val candle = candles[i]
                    val distFromRight = (candles.lastIndex.toFloat() - scrollOffset) - i
                    val x = chartRight - (distFromRight * totalSlotWidth) - candleWidth
                    val centerX = x + candleWidth / 2f

                    val openY = priceToY(candle.open)
                    val closeY = priceToY(candle.close)
                    val highY = priceToY(candle.high)
                    val lowY = priceToY(candle.low)

                    val candleColor = if (candle.isBullish) BullGreen else BearRed

                    // Wick
                    drawLine(
                        color = candleColor,
                        start = Offset(centerX, highY),
                        end = Offset(centerX, lowY),
                        strokeWidth = 1.5f
                    )

                    // Body
                    val bodyTop = min(openY, closeY)
                    val bodyBottom = max(openY, closeY)
                    val bodyH = (bodyBottom - bodyTop).coerceAtLeast(1.5f)

                    drawRect(
                        color = candleColor,
                        topLeft = Offset(x, bodyTop),
                        size = Size(candleWidth, bodyH)
                    )
                }

                // 5. Draw User Drawings
                if (!areDrawingsHidden) {
                    for (drawing in drawings) {
                        drawDrawingItem(
                            drawing = drawing,
                            isSelected = drawing.id == selectedDrawing?.id,
                            candles = candles,
                            rightIndex = rightIndex,
                            totalSlotWidth = totalSlotWidth,
                            candleWidth = candleWidth,
                            chartRight = chartRight,
                            chartLeft = chartLeft,
                            chartWidth = chartWidth,
                            chartHeight = chartHeight,
                            priceToY = ::priceToY,
                            timeToX = ::timeToX
                        )
                    }

                    // In-progress drawing preview
                    if (inProgressPoints.isNotEmpty()) {
                        drawInProgressDrawing(
                            tool = activeDrawingTool,
                            points = inProgressPoints,
                            candles = candles,
                            rightIndex = rightIndex,
                            totalSlotWidth = totalSlotWidth,
                            chartRight = chartRight,
                            priceToY = ::priceToY,
                            timeToX = ::timeToX
                        )
                    }
                }

                // 6. Alert Lines (dashed line strictly on chart canvas)
                val activeAlerts = alerts.filter { it.symbol.equals(symbol, ignoreCase = true) && it.isActive }
                for (alert in activeAlerts) {
                    val alertY = priceToY(alert.targetPrice)
                    if (alertY in 0f..chartHeight) {
                        drawLine(
                            color = AlertLineColor,
                            start = Offset(chartLeft, alertY),
                            end = Offset(chartRight, alertY),
                            strokeWidth = 1.8f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    }
                }

                // 7. Live Price Lines (Mark, Index, Active Price)
                if (settings.showMarkPrice && priceInfo.markPrice > 0.0) {
                    val markY = priceToY(priceInfo.markPrice)
                    if (markY in 0f..chartHeight) {
                        drawLine(
                            color = MarkPriceCyan.copy(alpha = 0.7f),
                            start = Offset(chartLeft, markY),
                            end = Offset(chartRight, markY),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }
                }

                if (settings.showIndexPrice && priceInfo.indexPrice > 0.0) {
                    val indexY = priceToY(priceInfo.indexPrice)
                    if (indexY in 0f..chartHeight) {
                        drawLine(
                            color = IndexPricePurple.copy(alpha = 0.7f),
                            start = Offset(chartLeft, indexY),
                            end = Offset(chartRight, indexY),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                        )
                    }
                }

                val activeP = when (candlePriceSource) {
                    CandlePriceSource.LAST -> lastP
                    CandlePriceSource.MARK -> if (priceInfo.markPrice > 0) priceInfo.markPrice else latestCandle?.close ?: 0.0
                    CandlePriceSource.INDEX -> if (priceInfo.indexPrice > 0) priceInfo.indexPrice else latestCandle?.close ?: 0.0
                }
                if (activeP > 0.0) {
                    val activeY = priceToY(activeP)
                    val lineColor = when (candlePriceSource) {
                        CandlePriceSource.MARK -> MarkPriceCyan
                        CandlePriceSource.INDEX -> IndexPricePurple
                        CandlePriceSource.LAST -> if (latestCandle?.isBullish == true) BullGreen else BearRed
                    }
                    if (activeY in 0f..chartHeight) {
                        drawLine(
                            color = lineColor,
                            start = Offset(chartLeft, activeY),
                            end = Offset(chartRight, activeY),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                        )
                    }
                }
            }

            // 8. Price Scale Axis (Labels & Background)
            val scaleBgX = if (isRightScale) chartRight else 0f
            drawRect(
                color = DarkBackground,
                topLeft = Offset(scaleBgX, 0f),
                size = Size(priceScaleWidth, chartHeight)
            )
            drawLine(
                color = DarkBorder,
                start = Offset(if (isRightScale) chartRight else priceScaleWidth, 0f),
                end = Offset(if (isRightScale) chartRight else priceScaleWidth, chartHeight),
                strokeWidth = 1.2f
            )

            // Price Labels on Scale
            val numPriceLabels = 7
            val stepY = chartHeight / (numPriceLabels + 1)
            for (i in 1..numPriceLabels) {
                val y = i * stepY
                val p = yToPrice(y)
                val label = formatPriceLabel(p, settings, basePrice)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    scaleBgX + priceScaleWidth / 2f,
                    y + 4f,
                    scaleTextPaint
                )
            }

            // Alert indicators on Price Scale
            val scaleX = if (isRightScale) chartRight else 0f
            for (alert in activeAlerts) {
                val alertY = priceToY(alert.targetPrice)
                if (alertY in 0f..chartHeight) {
                    drawRect(
                        color = AlertLineColor,
                        topLeft = Offset(scaleX, alertY - 10f),
                        size = Size(priceScaleWidth, 20f)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        formatPriceLabel(alert.targetPrice, settings, basePrice),
                        scaleX + priceScaleWidth / 2f,
                        alertY + 4f,
                        alertScalePaint
                    )
                }
            }

            // Active Price Indicator Pill on Price Scale
            val activePillPrice = when (candlePriceSource) {
                CandlePriceSource.LAST -> lastP
                CandlePriceSource.MARK -> if (priceInfo.markPrice > 0) priceInfo.markPrice else latestCandle?.close ?: 0.0
                CandlePriceSource.INDEX -> if (priceInfo.indexPrice > 0) priceInfo.indexPrice else latestCandle?.close ?: 0.0
            }
            if (activePillPrice > 0.0) {
                val pillY = priceToY(activePillPrice)
                val pillColor = when (candlePriceSource) {
                    CandlePriceSource.MARK -> MarkPriceCyan
                    CandlePriceSource.INDEX -> IndexPricePurple
                    CandlePriceSource.LAST -> if (latestCandle?.isBullish == true) BullGreen else BearRed
                }
                if (pillY in 0f..chartHeight) {
                    // Price Pill
                    drawRect(
                        color = pillColor,
                        topLeft = Offset(scaleX, pillY - 12f),
                        size = Size(priceScaleWidth, 24f)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        formatPriceLabel(activePillPrice, settings, basePrice),
                        scaleX + priceScaleWidth / 2f,
                        pillY + 5f,
                        Paint().apply {
                            color = if (candlePriceSource == CandlePriceSource.MARK) Color.Black.toArgb() else Color.White.toArgb()
                            textSize = 22f
                            typeface = Typeface.DEFAULT_BOLD
                            textAlign = Paint.Align.CENTER
                        }
                    )

                    // Bar Time Count Down directly under the price line in the price scale
                    val candleEndTime = latestCandle?.let { candle ->
                        if (candle.closeTime > candle.openTime) {
                            candle.closeTime + 1L
                        } else {
                            candle.openTime + timeframe.durationMs
                        }
                    } ?: (currentTimeMs + timeframe.durationMs)

                    val remainingSec = ((candleEndTime - currentTimeMs).coerceAtLeast(0L) / 1000L)
                    val countdownStr = when {
                        remainingSec >= 86400L -> {
                            val days = remainingSec / 86400L
                            val hrs = (remainingSec % 86400L) / 3600L
                            "${days}d ${hrs}h"
                        }
                        remainingSec >= 3600L -> {
                            val hrs = remainingSec / 3600L
                            val mins = (remainingSec % 3600L) / 60L
                            val secs = remainingSec % 60L
                            String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
                        }
                        else -> {
                            val mins = remainingSec / 60L
                            val secs = remainingSec % 60L
                            String.format(Locale.US, "%02d:%02d", mins, secs)
                        }
                    }

                    // Countdown badge right under the active price line
                    val countdownTop = pillY + 14f
                    val countdownHeight = 18f
                    if (countdownTop + countdownHeight <= chartHeight) {
                        drawRect(
                            color = DarkSurface,
                            topLeft = Offset(scaleX + 4f, countdownTop),
                            size = Size(priceScaleWidth - 8f, countdownHeight)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            countdownStr,
                            scaleX + priceScaleWidth / 2f,
                            countdownTop + 14f,
                            countdownPaint
                        )
                    }
                }
            }

            // 9. Time Scale Axis (Bottom)
            drawRect(
                color = DarkBackground,
                topLeft = Offset(0f, chartHeight),
                size = Size(width, timeScaleHeight)
            )
            drawLine(
                color = DarkBorder,
                start = Offset(0f, chartHeight),
                end = Offset(width, chartHeight),
                strokeWidth = 1.2f
            )

            val timeStepSlots = (visibleCount / 5).coerceAtLeast(4)
            for (i in rightIndex downTo leftIndex step timeStepSlots) {
                val candle = candles[i]
                val dist = rightIndex - i
                val x = chartRight - (dist * totalSlotWidth) - candleWidth / 2f
                if (x in chartLeft..chartRight) {
                    reusableDate.time = candle.openTime
                    val dateStr = timeFormat.format(reusableDate)
                    drawContext.canvas.nativeCanvas.drawText(
                        dateStr,
                        x,
                        chartHeight + 26f,
                        timeTextPaint
                    )
                }
            }

            // 10. Crosshair Overlay (Only when enabled)
            if (isCrosshairEnabled) {
                crosshairPos?.let { pos ->
                    if (pos.x in chartLeft..chartRight && pos.y in 0f..chartHeight) {
                        // Dotted crosshair lines
                        drawLine(
                            color = CrosshairColor,
                            start = Offset(chartLeft, pos.y),
                            end = Offset(chartRight, pos.y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                        )
                        drawLine(
                            color = CrosshairColor,
                            start = Offset(pos.x, 0f),
                            end = Offset(pos.x, chartHeight),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                        )

                        // Price Tag on Scale
                        val hoveredPrice = yToPrice(pos.y)
                        val priceTagX = if (isRightScale) chartRight else 0f
                        drawRect(
                            color = DarkBorder,
                            topLeft = Offset(priceTagX, pos.y - 10f),
                            size = Size(priceScaleWidth, 20f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            formatPriceLabel(hoveredPrice, settings, basePrice),
                            priceTagX + priceScaleWidth / 2f,
                            pos.y + 4f,
                            priceTagPaint
                        )

                        // Nearest candle under crosshair X
                        var nearestCandle: Candle? = null
                        var minDist = Float.MAX_VALUE
                        for (i in rightIndex downTo leftIndex) {
                            val c = candles[i]
                            val dist = rightIndex - i
                            val cx = chartRight - (dist * totalSlotWidth) - candleWidth / 2f
                            val d = abs(pos.x - cx)
                            if (d < minDist) {
                                minDist = d
                                nearestCandle = c
                            }
                        }

                        nearestCandle?.let { c ->
                            hoveredCandle = c
                            reusableDate.time = c.openTime
                            val timeStr = timeFormat.format(reusableDate)
                            val badgeW = 200f
                            val badgeX = (pos.x - badgeW / 2f).coerceIn(0f, width - badgeW)
                            drawRect(
                                color = DarkBorder,
                                topLeft = Offset(badgeX, chartHeight),
                                size = Size(badgeW, timeScaleHeight)
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                timeStr,
                                badgeX + badgeW / 2f,
                                chartHeight + 26f,
                                priceTagPaint
                            )
                        }
                    }
                }
            }

            // 11. OHLCV Banner at Top Left
            val displayCandle = if (isCrosshairEnabled) (hoveredCandle ?: latestCandle) else latestCandle
            if (displayCandle != null) {
                val ohlcColor = if (displayCandle.isBullish) BullGreen.toArgb() else BearRed.toArgb()

                var curX = chartLeft + 16f
                val curY = 32f

                // Symbol + Timeframe + Price Source indicator
                ohlcPaint.color = TextPrimary.toArgb()
                ohlcPaint.typeface = Typeface.DEFAULT_BOLD
                val candleModeText = when (candlePriceSource) {
                    CandlePriceSource.LAST -> ""
                    CandlePriceSource.MARK -> " [MARK]"
                    CandlePriceSource.INDEX -> " [INDEX]"
                }
                val headerText = "$symbol • ${timeframe.displayName}$candleModeText  "
                drawContext.canvas.nativeCanvas.drawText(headerText, curX, curY, ohlcPaint)
                curX += ohlcPaint.measureText(headerText)

                ohlcPaint.typeface = Typeface.DEFAULT
                // O
                ohlcPaint.color = TextSecondary.toArgb()
                drawContext.canvas.nativeCanvas.drawText("O ", curX, curY, ohlcPaint)
                curX += ohlcPaint.measureText("O ")
                ohlcPaint.color = ohlcColor
                val oStr = "${formatPrice(displayCandle.open)} "
                drawContext.canvas.nativeCanvas.drawText(oStr, curX, curY, ohlcPaint)
                curX += ohlcPaint.measureText(oStr)

                // H
                ohlcPaint.color = TextSecondary.toArgb()
                drawContext.canvas.nativeCanvas.drawText("H ", curX, curY, ohlcPaint)
                curX += ohlcPaint.measureText("H ")
                ohlcPaint.color = ohlcColor
                val hStr = "${formatPrice(displayCandle.high)} "
                drawContext.canvas.nativeCanvas.drawText(hStr, curX, curY, ohlcPaint)
                curX += ohlcPaint.measureText(hStr)

                // L
                ohlcPaint.color = TextSecondary.toArgb()
                drawContext.canvas.nativeCanvas.drawText("L ", curX, curY, ohlcPaint)
                curX += ohlcPaint.measureText("L ")
                ohlcPaint.color = ohlcColor
                val lStr = "${formatPrice(displayCandle.low)} "
                drawContext.canvas.nativeCanvas.drawText(lStr, curX, curY, ohlcPaint)
                curX += ohlcPaint.measureText(lStr)

                // C
                ohlcPaint.color = TextSecondary.toArgb()
                drawContext.canvas.nativeCanvas.drawText("C ", curX, curY, ohlcPaint)
                curX += ohlcPaint.measureText("C ")
                ohlcPaint.color = ohlcColor
                val cStr = "${formatPrice(displayCandle.close)} "
                drawContext.canvas.nativeCanvas.drawText(cStr, curX, curY, ohlcPaint)
                curX += ohlcPaint.measureText(cStr)

                // % Change
                val changeStr = (if (displayCandle.changePercent >= 0) "+" else "") +
                        String.format(Locale.US, "%.2f%%", displayCandle.changePercent)
                drawContext.canvas.nativeCanvas.drawText(changeStr, curX, curY, ohlcPaint)
            }
        }

        // --- DEDICATED GESTURE OVERLAYS (TradingView / MetaTrader 5 Multi-Touch Engine) ---

        // 1. PRICE SCALE GESTURE STRIP (Right edge ~55dp)
        // Vertical drag adjusts price scale; double-tap resets price scaling
        Box(
            modifier = Modifier
                .align(if (isRightScale) Alignment.CenterEnd else Alignment.CenterStart)
                .width(priceScaleWidthDp)
                .fillMaxHeight()
                .padding(bottom = timeScaleHeightDp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val scaleFactor = 1f - (dragAmount / 220f)
                        verticalScaleRatio = (verticalScaleRatio * scaleFactor).coerceIn(0.25f, 6.0f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            verticalScaleRatio = 1.0f
                            verticalPanOffset = 0.0f
                        }
                    )
                }
        )

        // 2. TIME AXIS GESTURE STRIP (Bottom edge ~22dp)
        // Horizontal drag stretches/compresses candle width; double-tap resets default candle width
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = if (isRightScale) 0.dp else priceScaleWidthDp,
                    end = if (isRightScale) priceScaleWidthDp else 0.dp
                )
                .height(timeScaleHeightDp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val widthFactor = 1f + (dragAmount / 240f)
                        candleWidth = (candleWidth * widthFactor).coerceIn(4f, 80f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            candleWidth = 16f
                        }
                    )
                }
        )

        // 3. MAIN CHART CANVAS GESTURE OVERLAY
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isRightScale) 0.dp else priceScaleWidthDp,
                    end = if (isRightScale) priceScaleWidthDp else 0.dp,
                    bottom = timeScaleHeightDp
                )
                .then(
                    if (isCrosshairEnabled) {
                        Modifier
                            .pointerInput(isCrosshairEnabled) {
                                detectDragGestures(
                                    onDrag = { change, _ ->
                                        crosshairPos = change.position
                                    },
                                    onDragEnd = { /* keep active */ }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        isCrosshairEnabled = false
                                        crosshairPos = null
                                        hoveredCandle = null
                                    }
                                )
                            }
                    } else if (activeDrawingTool != DrawingToolType.CURSOR) {
                        // DRAWING MODE: Precise and isolated drawing creation
                        Modifier.pointerInput(activeDrawingTool, candles.size, candleWidth, scrollOffset, verticalPanOffset, verticalScaleRatio) {
                            val chartW = size.width.toFloat()
                            val chartH = size.height.toFloat()

                            if (activeDrawingTool == DrawingToolType.HORIZONTAL_LINE ||
                                activeDrawingTool == DrawingToolType.VERTICAL_LINE ||
                                activeDrawingTool == DrawingToolType.TEXT
                            ) {
                                detectTapGestures { tapOffset ->
                                    val pt = screenToChartPoint(
                                        tapOffset, candles, candleWidth, scrollOffset,
                                        chartW, chartH, settings, verticalPanOffset, verticalScaleRatio
                                    ) ?: return@detectTapGestures

                                    val item = when (activeDrawingTool) {
                                        DrawingToolType.HORIZONTAL_LINE -> DrawingItem(
                                            symbol = symbol, timeframeId = timeframe.id,
                                            toolType = DrawingToolType.HORIZONTAL_LINE,
                                            points = listOf(pt)
                                        )
                                        DrawingToolType.VERTICAL_LINE -> DrawingItem(
                                            symbol = symbol, timeframeId = timeframe.id,
                                            toolType = DrawingToolType.VERTICAL_LINE,
                                            points = listOf(pt)
                                        )
                                        DrawingToolType.TEXT -> DrawingItem(
                                            symbol = symbol, timeframeId = timeframe.id,
                                            toolType = DrawingToolType.TEXT,
                                            points = listOf(pt),
                                            textContent = "Note @ ${String.format(Locale.US, "%.2f", pt.price)}"
                                        )
                                        else -> return@detectTapGestures
                                    }
                                    inProgressPoints.clear()
                                    onAddDrawing(item)
                                    onResetToolToCursor()
                                }
                            } else if (activeDrawingTool == DrawingToolType.BRUSH) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        inProgressPoints.clear()
                                        val pt = screenToChartPoint(
                                            startOffset, candles, candleWidth, scrollOffset,
                                            chartW, chartH, settings, verticalPanOffset, verticalScaleRatio
                                        )
                                        if (pt != null) inProgressPoints.add(pt)
                                    },
                                    onDrag = { change, _ ->
                                        val pt = screenToChartPoint(
                                            change.position, candles, candleWidth, scrollOffset,
                                            chartW, chartH, settings, verticalPanOffset, verticalScaleRatio
                                        )
                                        if (pt != null) {
                                            inProgressPoints.add(pt)
                                        }
                                    },
                                    onDragEnd = {
                                        if (inProgressPoints.size >= 2) {
                                            val item = DrawingItem(
                                                symbol = symbol,
                                                timeframeId = timeframe.id,
                                                toolType = DrawingToolType.BRUSH,
                                                points = inProgressPoints.toList()
                                            )
                                            onAddDrawing(item)
                                        }
                                        inProgressPoints.clear()
                                        onResetToolToCursor()
                                    },
                                    onDragCancel = {
                                        inProgressPoints.clear()
                                    }
                                )
                            } else {
                                // 2-point and 3-point tools
                                var startOffset = Offset.Zero
                                var hasDragged = false

                                detectDragGestures(
                                    onDragStart = { startPos ->
                                        startOffset = startPos
                                        hasDragged = false
                                        val pt = screenToChartPoint(
                                            startPos, candles, candleWidth, scrollOffset,
                                            chartW, chartH, settings, verticalPanOffset, verticalScaleRatio
                                        )
                                        if (pt != null) {
                                            if (inProgressPoints.isEmpty()) {
                                                inProgressPoints.add(pt)
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val dist = (change.position - startOffset).getDistance()
                                        if (dist > 10f) {
                                            hasDragged = true
                                        }
                                        val pt = screenToChartPoint(
                                            change.position, candles, candleWidth, scrollOffset,
                                            chartW, chartH, settings, verticalPanOffset, verticalScaleRatio
                                        )
                                        if (pt != null && inProgressPoints.isNotEmpty()) {
                                            if (inProgressPoints.size == 1) {
                                                inProgressPoints.add(pt)
                                            } else {
                                                inProgressPoints[1] = pt
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val requiredPoints = when (activeDrawingTool) {
                                            DrawingToolType.FIB_EXTENSION, DrawingToolType.PARALLEL_CHANNEL -> 3
                                            else -> 2
                                        }
                                        if (hasDragged && inProgressPoints.size >= 2) {
                                            val item = DrawingItem(
                                                symbol = symbol,
                                                timeframeId = timeframe.id,
                                                toolType = activeDrawingTool,
                                                points = inProgressPoints.toList()
                                            )
                                            onAddDrawing(item)
                                            inProgressPoints.clear()
                                            onResetToolToCursor()
                                        } else if (!hasDragged) {
                                            if (inProgressPoints.size >= requiredPoints) {
                                                val item = DrawingItem(
                                                    symbol = symbol,
                                                    timeframeId = timeframe.id,
                                                    toolType = activeDrawingTool,
                                                    points = inProgressPoints.toList()
                                                )
                                                onAddDrawing(item)
                                                inProgressPoints.clear()
                                                onResetToolToCursor()
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        inProgressPoints.clear()
                                    }
                                )
                            }
                        }
                    } else {
                        // CURSOR MODE: Multi-touch focal pinch zoom, buttery smooth pan, double tap reset
                        Modifier.pointerInput(activeDrawingTool, selectedDrawing?.id, drawings.size) {
                            val chartW = size.width.toFloat()
                            val chartH = size.height.toFloat()
                            var lastTapTime = 0L

                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()

                                    // Mouse wheel scroll zoom support
                                    if (event.type == PointerEventType.Scroll) {
                                        val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                        if (scrollDelta != 0f) {
                                            val factor = if (scrollDelta < 0) 1.15f else 0.85f
                                            val oldSlot = candleWidth * 1.25f
                                            val newCandleWidth = (candleWidth * factor).coerceIn(4f, 80f)
                                            val newSlot = newCandleWidth * 1.25f
                                            val mousePos = event.changes.first().position
                                            val distRight = (chartW - mousePos.x).coerceAtLeast(0f)
                                            val candleOffset = distRight / oldSlot
                                            val scrollAdj = candleOffset * (1f - (oldSlot / newSlot))
                                            val maxScroll = (candles.size - 5).coerceAtLeast(0).toFloat()
                                            scrollOffset = (scrollOffset + scrollAdj).coerceIn(-60f, maxScroll)
                                            candleWidth = newCandleWidth
                                        }
                                        continue
                                    }

                                    val downChanges = event.changes.filter { it.pressed }
                                    if (downChanges.isEmpty()) continue

                                    if (downChanges.size >= 2) {
                                        // Multi-touch 2-Finger Pinch Zoom & Pan
                                        var prevDist = (downChanges[0].position - downChanges[1].position).getDistance().coerceAtLeast(1f)
                                        var prevCentroid = (downChanges[0].position + downChanges[1].position) / 2f

                                        while (true) {
                                            val moveEvent = awaitPointerEvent()
                                            val activeChanges = moveEvent.changes.filter { it.pressed }
                                            if (activeChanges.size < 2) break

                                            val p1 = activeChanges[0].position
                                            val p2 = activeChanges[1].position
                                            val currentDist = (p1 - p2).getDistance().coerceAtLeast(1f)
                                            val currentCentroid = (p1 + p2) / 2f

                                            val zoom = currentDist / prevDist
                                            val pan = currentCentroid - prevCentroid

                                            activeChanges.forEach { it.consume() }

                                            if (abs(zoom - 1f) > 0.001f) {
                                                val oldSlot = candleWidth * 1.25f
                                                val newCandleWidth = (candleWidth * zoom).coerceIn(4f, 80f)
                                                val newSlot = newCandleWidth * 1.25f

                                                val centroidDistFromRight = (chartW - currentCentroid.x).coerceAtLeast(0f)
                                                val candleOffsetAtCentroid = centroidDistFromRight / oldSlot
                                                val scrollAdjustment = candleOffsetAtCentroid * (1f - (oldSlot / newSlot))

                                                val maxScroll = (candles.size - 5).coerceAtLeast(0).toFloat()
                                                scrollOffset = (scrollOffset + scrollAdjustment).coerceIn(-60f, maxScroll)
                                                candleWidth = newCandleWidth
                                            }

                                            if (pan.x != 0f) {
                                                val totalSlot = candleWidth * 1.25f
                                                val maxScroll = (candles.size - 5).coerceAtLeast(0).toFloat()
                                                scrollOffset = (scrollOffset + (pan.x / totalSlot)).coerceIn(-60f, maxScroll)
                                                if (scrollOffset >= maxScroll - 10f) {
                                                    onLoadMoreHistory()
                                                }
                                            }
                                            if (!settings.isScaleLocked && pan.y != 0f) {
                                                verticalPanOffset += pan.y
                                            }

                                            prevDist = currentDist
                                            prevCentroid = currentCentroid
                                        }
                                    } else {
                                        // Single Finger: Handle dragging, body movement, chart pan, or tap
                                        val firstChange = downChanges.first()
                                        val startPos = firstChange.position
                                        var lastPos = startPos
                                        var hasDragged = false

                                        val currDrawing = selectedDrawing
                                        var activeHandleIndex = -1
                                        var isDraggingBody = false

                                        fun tToX(t: Long): Float {
                                            if (candles.isEmpty()) return chartW
                                            val totalSlot = candleWidth * 1.25f
                                            val rightIdxFloat = (candles.lastIndex.toFloat() - scrollOffset)
                                            val firstT = candles.first().openTime
                                            val lastT = candles.last().openTime
                                            val avgSlotMs = if (candles.size > 1) (lastT - firstT).toDouble() / (candles.size - 1) else timeframe.durationMs.toDouble()
                                            val candleIdxFloat: Float = if (t <= firstT) {
                                                ((t - firstT) / avgSlotMs).toFloat()
                                            } else if (t >= lastT) {
                                                candles.lastIndex.toFloat() + ((t - lastT) / avgSlotMs).toFloat()
                                            } else {
                                                val diff = (t - firstT) / avgSlotMs
                                                diff.toFloat().coerceIn(0f, candles.lastIndex.toFloat())
                                            }
                                            val dist = rightIdxFloat - candleIdxFloat
                                            return chartW - (dist * totalSlot) - (candleWidth / 2f)
                                        }

                                        fun xToT(x: Float): Long {
                                            if (candles.isEmpty()) return 0L
                                            val totalSlot = candleWidth * 1.25f
                                            val rightIdxFloat = (candles.lastIndex.toFloat() - scrollOffset)
                                            val distFromRight = (chartW - x) / totalSlot
                                            val idxFloat = rightIdxFloat - distFromRight
                                            val firstT = candles.first().openTime
                                            val lastT = candles.last().openTime
                                            val avgSlotMs = if (candles.size > 1) (lastT - firstT).toDouble() / (candles.size - 1) else timeframe.durationMs.toDouble()
                                            return if (idxFloat <= 0f) {
                                                (firstT + idxFloat * avgSlotMs).toLong()
                                            } else if (idxFloat >= candles.lastIndex.toFloat()) {
                                                val excess = idxFloat - candles.lastIndex.toFloat()
                                                (lastT + excess * avgSlotMs).toLong()
                                            } else {
                                                val base = idxFloat.toInt().coerceIn(0, candles.lastIndex - 1)
                                                val frac = (idxFloat - base).toDouble()
                                                val t0 = candles[base].openTime
                                                val t1 = candles[base + 1].openTime
                                                (t0 + frac * (t1 - t0)).toLong()
                                            }
                                        }

                                        fun pToY(p: Double): Float {
                                            val minP = candles.minOfOrNull { it.low } ?: 0.0
                                            val maxP = candles.maxOfOrNull { it.high } ?: 1.0
                                            val priceRange = (maxP - minP).coerceAtLeast(0.0001)
                                            val scaledRange = (priceRange / verticalScaleRatio).coerceAtLeast(0.000001)
                                            val midP = (minP + maxP) / 2.0
                                            val effMinP = midP - (scaledRange / 2.0)
                                            var norm = ((p - effMinP) / scaledRange).toFloat()
                                            if (settings.isInverted) norm = 1f - norm
                                            return chartH - (norm * chartH) + verticalPanOffset
                                        }

                                        fun yToP(y: Float): Double {
                                            val minP = candles.minOfOrNull { it.low } ?: 0.0
                                            val maxP = candles.maxOfOrNull { it.high } ?: 1.0
                                            val priceRange = (maxP - minP).coerceAtLeast(0.0001)
                                            val scaledRange = (priceRange / verticalScaleRatio).coerceAtLeast(0.000001)
                                            val midP = (minP + maxP) / 2.0
                                            val effMinP = midP - (scaledRange / 2.0)
                                            val adjY = y - verticalPanOffset
                                            var norm = 1f - (adjY / chartH)
                                            if (settings.isInverted) norm = 1f - norm
                                            return effMinP + norm.toDouble() * scaledRange
                                        }

                                        if (currDrawing != null && !areDrawingsHidden && !currDrawing.isLocked) {
                                            val handles = getDrawingHandles(currDrawing, ::tToX, ::pToY, 0f, chartW, chartH)
                                            val hitIdx = handles.indexOfFirst { (it - startPos).getDistance() <= 38f }
                                            if (hitIdx >= 0) {
                                                activeHandleIndex = hitIdx
                                            } else if (isPointOnDrawing(startPos, currDrawing, ::tToX, ::pToY, 0f, chartW, chartH)) {
                                                isDraggingBody = true
                                            }
                                        }

                                        while (true) {
                                            val moveEvent = awaitPointerEvent()
                                            val activeChanges = moveEvent.changes.filter { it.pressed }
                                            if (activeChanges.isEmpty()) {
                                                // Pointer UP
                                                val totalDist = (lastPos - startPos).getDistance()
                                                if (!hasDragged && totalDist < 14f) {
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastTapTime < 320) {
                                                        // Double-tap: restore default sensible zoom
                                                        candleWidth = 16f
                                                        scrollOffset = 0f
                                                        verticalPanOffset = 0f
                                                        verticalScaleRatio = 1.0f
                                                        lastTapTime = 0L
                                                    } else {
                                                        lastTapTime = now
                                                        if (isCrosshairEnabled) {
                                                            isCrosshairEnabled = false
                                                            crosshairPos = null
                                                            hoveredCandle = null
                                                        } else if (onSelectCandleForReplay != null && candles.isNotEmpty()) {
                                                            val totalSlot = candleWidth * 1.25f
                                                            val rightIdxFloat = candles.lastIndex.toFloat() - scrollOffset
                                                            val distFromRight = (chartW - startPos.x) / totalSlot
                                                            val candleIdx = (rightIdxFloat - distFromRight).roundToInt().coerceIn(0, candles.lastIndex)
                                                            onSelectCandleForReplay(candleIdx)
                                                        } else {
                                                            val tapped = findDrawingNearPoint(
                                                                tapOffset = startPos,
                                                                drawings = drawings,
                                                                candles = candles,
                                                                candleWidth = candleWidth,
                                                                scrollOffset = scrollOffset,
                                                                chartWidth = chartW,
                                                                chartHeight = chartH,
                                                                settings = settings,
                                                                verticalOffset = verticalPanOffset,
                                                                verticalScaleRatio = verticalScaleRatio
                                                            )
                                                            onSelectDrawing(tapped)
                                                        }
                                                    }
                                                }
                                                break
                                            }

                                            if (activeChanges.size >= 2) {
                                                // Transition to pinch zoom
                                                break
                                            }

                                            val change = activeChanges.first()
                                            val curPos = change.position
                                            val moveDist = (curPos - startPos).getDistance()
                                            if (moveDist > 10f) {
                                                hasDragged = true
                                            }

                                            if (hasDragged) {
                                                change.consume()
                                                val deltaX = curPos.x - lastPos.x
                                                val deltaY = curPos.y - lastPos.y

                                                if (activeHandleIndex >= 0 && currDrawing != null) {
                                                    val newPoint = screenToChartPoint(
                                                        curPos, candles, candleWidth, scrollOffset,
                                                        chartW, chartH, settings, verticalPanOffset, verticalScaleRatio
                                                    )
                                                    if (newPoint != null) {
                                                        val updated = updateDrawingHandle(currDrawing, activeHandleIndex, newPoint)
                                                        onUpdateDrawing(updated)
                                                    }
                                                } else if (isDraggingBody && currDrawing != null) {
                                                    val oldP = yToP(lastPos.y)
                                                    val newP = yToP(curPos.y)
                                                    val deltaPrice = newP - oldP

                                                    val oldT = xToT(lastPos.x)
                                                    val newT = xToT(curPos.x)
                                                    val deltaTimestamp = newT - oldT

                                                    val updated = translateDrawing(currDrawing, deltaPrice, deltaTimestamp)
                                                    onUpdateDrawing(updated)
                                                } else {
                                                    val totalSlot = candleWidth * 1.25f
                                                    val maxScroll = (candles.size - 5).coerceAtLeast(0).toFloat()
                                                    // Swipe right -> deltaX > 0 -> scrollOffset increases -> content moves right!
                                                    // Swipe left -> deltaX < 0 -> scrollOffset decreases -> content moves left!
                                                    scrollOffset = (scrollOffset + (deltaX / totalSlot)).coerceIn(-60f, maxScroll)
                                                    if (scrollOffset >= maxScroll - 10f) {
                                                        onLoadMoreHistory()
                                                    }
                                                    if (!settings.isScaleLocked) {
                                                        verticalPanOffset += deltaY
                                                    }
                                                }
                                                lastPos = curPos
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
        )

        // Floating Active Drawing Banner with Exit/Done affordance
        AnimatedVisibility(
            visible = activeDrawingTool != DrawingToolType.CURSOR,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.95f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, TVBlue)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TVBlue)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Drawing: ${activeDrawingTool.title}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(TVBlue.copy(alpha = 0.25f))
                            .border(0.8.dp, TVBlue, RoundedCornerShape(10.dp))
                            .clickable {
                                inProgressPoints.clear()
                                onResetToolToCursor()
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("Done / Pan", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Floating Quick Snap-to-Latest Button (Appears when user has scrolled back in history or into future)
        AnimatedVisibility(
            visible = scrollOffset > 5f || scrollOffset < -5f || verticalScaleRatio != 1f || verticalPanOffset != 0f,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 155.dp, bottom = 52.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(TVBlue)
                    .border(1.dp, Color.White, RoundedCornerShape(16.dp))
                    .clickable {
                        scrollOffset = 0f
                        verticalPanOffset = 0f
                        verticalScaleRatio = 1.0f
                        candleWidth = 16f
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Jump to Latest",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Latest",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Floating On-Chart Zoom / Crosshair Controls (Top Right corner)
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.88f)),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkBorder),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 152.dp, top = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom in
                IconButton(
                    onClick = { candleWidth = (candleWidth * 1.25f).coerceAtMost(60f) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Zoom out
                IconButton(
                    onClick = { candleWidth = (candleWidth * 0.8f).coerceAtLeast(4f) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Crosshair toggle button
                IconButton(
                    onClick = {
                        isCrosshairEnabled = !isCrosshairEnabled
                        if (!isCrosshairEnabled) {
                            crosshairPos = null
                            hoveredCandle = null
                        }
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = "Crosshair",
                        tint = if (isCrosshairEnabled) TVBlue else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Reset view
                IconButton(
                    onClick = {
                        scrollOffset = 0f
                        verticalPanOffset = 0f
                        verticalScaleRatio = 1.0f
                        candleWidth = 16f
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Chart View",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

// Drawing Renderer Helpers
private fun DrawScope.drawDrawingItem(
    drawing: DrawingItem,
    isSelected: Boolean,
    candles: List<Candle>,
    rightIndex: Int,
    totalSlotWidth: Float,
    candleWidth: Float,
    chartRight: Float,
    chartLeft: Float,
    chartWidth: Float,
    chartHeight: Float,
    priceToY: (Double) -> Float,
    timeToX: (Long) -> Float
) {
    val drawColor = Color(drawing.colorHex)
    val stroke = Stroke(
        width = drawing.strokeWidth * 2f,
        pathEffect = if (drawing.isDashed) PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f) else null
    )

    when (drawing.toolType) {
        DrawingToolType.HORIZONTAL_LINE -> {
            val p = drawing.points.firstOrNull()?.price ?: return
            val y = priceToY(p)
            drawLine(
                color = drawColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = drawing.strokeWidth * 2f,
                pathEffect = stroke.pathEffect
            )
            if (isSelected) {
                drawCircle(color = TVBlue, radius = 6f, center = Offset(chartLeft + 30f, y))
                drawCircle(color = TVBlue, radius = 6f, center = Offset(chartRight - 30f, y))
            }
        }
        DrawingToolType.VERTICAL_LINE -> {
            val t = drawing.points.firstOrNull()?.timestamp ?: return
            val x = timeToX(t)
            drawLine(
                color = drawColor,
                start = Offset(x, 0f),
                end = Offset(x, chartHeight),
                strokeWidth = drawing.strokeWidth * 2f,
                pathEffect = stroke.pathEffect
            )
            if (isSelected) {
                drawCircle(color = TVBlue, radius = 6f, center = Offset(x, chartHeight * 0.3f))
                drawCircle(color = TVBlue, radius = 6f, center = Offset(x, chartHeight * 0.7f))
            }
        }
        DrawingToolType.TREND_LINE -> {
            if (drawing.points.size < 2) return
            val p1 = drawing.points[0]
            val p2 = drawing.points[1]
            val start = Offset(timeToX(p1.timestamp), priceToY(p1.price))
            val end = Offset(timeToX(p2.timestamp), priceToY(p2.price))
            drawLine(color = drawColor, start = start, end = end, strokeWidth = drawing.strokeWidth * 2f, pathEffect = stroke.pathEffect)
            if (isSelected) {
                drawCircle(color = TVBlue, radius = 6f, center = start)
                drawCircle(color = TVBlue, radius = 6f, center = end)
            }
        }
        DrawingToolType.HORIZONTAL_RAY -> {
            if (drawing.points.isEmpty()) return
            val p = drawing.points[0]
            val start = Offset(timeToX(p.timestamp), priceToY(p.price))
            drawLine(color = drawColor, start = start, end = Offset(chartRight, start.y), strokeWidth = drawing.strokeWidth * 2f, pathEffect = stroke.pathEffect)
            if (isSelected) {
                drawCircle(color = TVBlue, radius = 6f, center = start)
            }
        }
        DrawingToolType.RECTANGLE -> {
            if (drawing.points.size < 2) return
            val p1 = drawing.points[0]
            val p2 = drawing.points[1]
            val x1 = timeToX(p1.timestamp)
            val y1 = priceToY(p1.price)
            val x2 = timeToX(p2.timestamp)
            val y2 = priceToY(p2.price)

            val left = min(x1, x2)
            val top = min(y1, y2)
            val rectW = abs(x2 - x1)
            val rectH = abs(y2 - y1)

            // Fill
            drawRect(
                color = drawColor.copy(alpha = 0.15f),
                topLeft = Offset(left, top),
                size = Size(rectW, rectH)
            )
            // Border
            drawRect(
                color = drawColor,
                topLeft = Offset(left, top),
                size = Size(rectW, rectH),
                style = stroke
            )
            if (isSelected) {
                drawCircle(color = TVBlue, radius = 6f, center = Offset(x1, y1))
                drawCircle(color = TVBlue, radius = 6f, center = Offset(x2, y2))
            }
        }
        DrawingToolType.FIB_RETRACEMENT -> {
            if (drawing.points.size < 2) return
            val p1 = drawing.points[0]
            val p2 = drawing.points[1]
            val startX = min(timeToX(p1.timestamp), timeToX(p2.timestamp))
            val diff = p2.price - p1.price

            val levels = listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0, 1.618)
            val levelColors = listOf(
                Color(0xFF787B86), Color(0xFFF23645), Color(0xFFFFA726),
                Color(0xFF089981), Color(0xFF26A69A), Color(0xFF2962FF),
                Color(0xFF787B86), Color(0xFF9C27B0)
            )

            levels.forEachIndexed { idx, fib ->
                val levelPrice = p1.price + (diff * fib)
                val ly = priceToY(levelPrice)
                val col = levelColors.getOrElse(idx) { drawColor }

                drawLine(
                    color = col,
                    start = Offset(startX, ly),
                    end = Offset(chartRight, ly),
                    strokeWidth = 1.2f
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "$fib (${String.format(Locale.US, "%.2f", levelPrice)})",
                    startX + 8f,
                    ly - 4f,
                    Paint().apply {
                        color = col.toArgb()
                        textSize = 18f
                    }
                )
            }
        }
        DrawingToolType.PRICE_RANGE -> {
            if (drawing.points.size < 2) return
            val p1 = drawing.points[0]
            val p2 = drawing.points[1]
            val x1 = timeToX(p1.timestamp)
            val y1 = priceToY(p1.price)
            val x2 = timeToX(p2.timestamp)
            val y2 = priceToY(p2.price)

            val left = min(x1, x2)
            val top = min(y1, y2)
            val rectW = abs(x2 - x1)
            val rectH = abs(y2 - y1)

            val priceDiff = p2.price - p1.price
            val pctDiff = if (p1.price > 0) (priceDiff / p1.price) * 100.0 else 0.0

            val boxColor = if (priceDiff >= 0) BullGreen else BearRed
            drawRect(
                color = boxColor.copy(alpha = 0.2f),
                topLeft = Offset(left, top),
                size = Size(rectW, rectH)
            )
            drawRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(rectW, rectH),
                style = Stroke(1.5f)
            )

            val label = String.format(Locale.US, "%.2f (%+.2f%%)", priceDiff, pctDiff)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                left + rectW / 2f,
                top + rectH / 2f,
                Paint().apply {
                    color = Color.White.toArgb()
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }
            )
        }
        DrawingToolType.TEXT -> {
            if (drawing.points.isEmpty()) return
            val p = drawing.points[0]
            val x = timeToX(p.timestamp)
            val y = priceToY(p.price)
            drawContext.canvas.nativeCanvas.drawText(
                drawing.textContent.ifBlank { "Note" },
                x,
                y,
                Paint().apply {
                    color = drawColor.toArgb()
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                }
            )
        }
        DrawingToolType.BRUSH -> {
            if (drawing.points.size < 2) return
            val path = Path()
            val first = drawing.points[0]
            path.moveTo(timeToX(first.timestamp), priceToY(first.price))
            for (i in 1 until drawing.points.size) {
                val pt = drawing.points[i]
                path.lineTo(timeToX(pt.timestamp), priceToY(pt.price))
            }
            drawPath(path = path, color = drawColor, style = stroke)
        }
        else -> {
            if (drawing.points.size >= 2) {
                val p1 = drawing.points[0]
                val p2 = drawing.points[1]
                drawLine(
                    color = drawColor,
                    start = Offset(timeToX(p1.timestamp), priceToY(p1.price)),
                    end = Offset(timeToX(p2.timestamp), priceToY(p2.price)),
                    strokeWidth = drawing.strokeWidth * 2f
                )
            }
        }
    }

    if (isSelected) {
        val handles = getDrawingHandles(drawing, timeToX, priceToY, chartLeft, chartRight, chartHeight)
        val handleColor = if (drawing.isLocked) BinanceGold else TVBlue
        for (h in handles) {
            drawCircle(color = handleColor.copy(alpha = 0.35f), radius = 11f, center = h)
            drawCircle(color = handleColor, radius = 7f, center = h)
            drawCircle(color = Color.White, radius = 3.5f, center = h)
        }
    }
}

private fun DrawScope.drawInProgressDrawing(
    tool: DrawingToolType,
    points: List<ChartPoint>,
    candles: List<Candle>,
    rightIndex: Int,
    totalSlotWidth: Float,
    chartRight: Float,
    priceToY: (Double) -> Float,
    timeToX: (Long) -> Float
) {
    if (points.isEmpty()) return
    val stroke = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
    val p1 = points[0]
    val o1 = Offset(timeToX(p1.timestamp), priceToY(p1.price))
    drawCircle(color = TVBlue, radius = 7f, center = o1)
    drawCircle(color = Color.White, radius = 3f, center = o1)

    if (points.size >= 2) {
        val p2 = points[1]
        val o2 = Offset(timeToX(p2.timestamp), priceToY(p2.price))
        drawCircle(color = TVBlue, radius = 7f, center = o2)
        drawCircle(color = Color.White, radius = 3f, center = o2)

        when (tool) {
            DrawingToolType.TREND_LINE -> {
                drawLine(color = TVBlue, start = o1, end = o2, strokeWidth = 3f, pathEffect = stroke.pathEffect)
            }
            DrawingToolType.HORIZONTAL_RAY -> {
                drawLine(color = TVBlue, start = o1, end = Offset(chartRight, o1.y), strokeWidth = 3f, pathEffect = stroke.pathEffect)
            }
            DrawingToolType.RECTANGLE -> {
                val left = min(o1.x, o2.x)
                val top = min(o1.y, o2.y)
                val rectW = abs(o2.x - o1.x)
                val rectH = abs(o2.y - o1.y)
                drawRect(color = TVBlue.copy(alpha = 0.2f), topLeft = Offset(left, top), size = Size(rectW, rectH))
                drawRect(color = TVBlue, topLeft = Offset(left, top), size = Size(rectW, rectH), style = stroke)
            }
            DrawingToolType.PRICE_RANGE -> {
                val left = min(o1.x, o2.x)
                val top = min(o1.y, o2.y)
                val rectW = abs(o2.x - o1.x)
                val rectH = abs(o2.y - o1.y)
                val priceDiff = p2.price - p1.price
                val boxColor = if (priceDiff >= 0) BullGreen else BearRed
                drawRect(color = boxColor.copy(alpha = 0.2f), topLeft = Offset(left, top), size = Size(rectW, rectH))
                drawRect(color = boxColor, topLeft = Offset(left, top), size = Size(rectW, rectH), style = stroke)
            }
            DrawingToolType.FIB_RETRACEMENT -> {
                drawLine(color = TVBlue, start = o1, end = o2, strokeWidth = 2f)
                val diff = p2.price - p1.price
                val levels = listOf(0.0, 0.382, 0.5, 0.618, 1.0)
                levels.forEach { fib ->
                    val ly = priceToY(p1.price + (diff * fib))
                    drawLine(color = TVBlue.copy(alpha = 0.7f), start = Offset(min(o1.x, o2.x), ly), end = Offset(chartRight, ly), strokeWidth = 1.2f, pathEffect = stroke.pathEffect)
                }
            }
            DrawingToolType.BRUSH -> {
                val path = Path()
                path.moveTo(o1.x, o1.y)
                for (i in 1 until points.size) {
                    val pt = points[i]
                    path.lineTo(timeToX(pt.timestamp), priceToY(pt.price))
                }
                drawPath(path = path, color = TVBlue, style = Stroke(width = 3.5f))
            }
            else -> {
                drawLine(color = TVBlue, start = o1, end = o2, strokeWidth = 3f, pathEffect = stroke.pathEffect)
            }
        }
    }
}

private fun screenToChartPoint(
    tap: Offset,
    candles: List<Candle>,
    candleWidth: Float,
    scrollOffset: Float,
    chartWidth: Float,
    chartHeight: Float,
    settings: ChartScaleSettings,
    verticalOffset: Float,
    verticalScaleRatio: Float = 1.0f
): ChartPoint? {
    if (candles.isEmpty()) return null
    val totalSlot = candleWidth + (candleWidth * 0.25f)
    val rightIdxFloat = (candles.lastIndex.toFloat() - scrollOffset)
    val distFromRight = (chartWidth - tap.x) / totalSlot
    val idxFloat = rightIdxFloat - distFromRight

    val firstT = candles.first().openTime
    val lastT = candles.last().openTime
    val avgSlotMs = if (candles.size > 1) (lastT - firstT).toDouble() / (candles.size - 1) else 60000.0

    val timestamp: Long = if (idxFloat <= 0f) {
        (firstT + idxFloat * avgSlotMs).toLong()
    } else if (idxFloat >= candles.lastIndex.toFloat()) {
        val excess = idxFloat - candles.lastIndex.toFloat()
        (lastT + excess * avgSlotMs).toLong()
    } else {
        val base = idxFloat.toInt().coerceIn(0, candles.lastIndex - 1)
        val frac = (idxFloat - base).toDouble()
        val t0 = candles[base].openTime
        val t1 = candles[base + 1].openTime
        (t0 + frac * (t1 - t0)).toLong()
    }

    val minP = candles.minOfOrNull { it.low } ?: 0.0
    val maxP = candles.maxOfOrNull { it.high } ?: 1.0
    val priceRange = (maxP - minP).coerceAtLeast(0.0001)
    val scaledRange = (priceRange / verticalScaleRatio).coerceAtLeast(0.000001)
    val midP = (minP + maxP) / 2.0
    val effMinP = midP - (scaledRange / 2.0)
    val effMaxP = midP + (scaledRange / 2.0)

    var norm = (1f - ((tap.y - verticalOffset) / chartHeight)).coerceIn(0f, 1f)
    if (settings.isInverted) norm = 1f - norm

    val price = if (settings.isLogarithmic) {
        val logMin = ln(effMinP.coerceAtLeast(0.00001))
        val logMax = ln(effMaxP.coerceAtLeast(0.00001))
        Math.exp((norm * (logMax - logMin) + logMin).toDouble())
    } else {
        effMinP + norm * scaledRange
    }

    return ChartPoint(timestamp = timestamp, price = price)
}

private fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val l2 = dx * dx + dy * dy
    if (l2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0f, 1f)
    val proj = Offset(a.x + t * dx, a.y + t * dy)
    return (p - proj).getDistance()
}

private fun getDrawingHandles(
    drawing: DrawingItem,
    timeToX: (Long) -> Float,
    priceToY: (Double) -> Float,
    chartLeft: Float,
    chartRight: Float,
    chartHeight: Float
): List<Offset> {
    return when (drawing.toolType) {
        DrawingToolType.HORIZONTAL_LINE -> {
            val p = drawing.points.firstOrNull()?.price ?: return emptyList()
            val y = priceToY(p)
            listOf(Offset(chartLeft + 50f, y), Offset(chartRight - 50f, y))
        }
        DrawingToolType.VERTICAL_LINE -> {
            val t = drawing.points.firstOrNull()?.timestamp ?: return emptyList()
            val x = timeToX(t)
            listOf(Offset(x, chartHeight * 0.35f), Offset(x, chartHeight * 0.65f))
        }
        DrawingToolType.HORIZONTAL_RAY -> {
            val p = drawing.points.firstOrNull() ?: return emptyList()
            listOf(Offset(timeToX(p.timestamp), priceToY(p.price)))
        }
        else -> {
            drawing.points.map { pt ->
                Offset(timeToX(pt.timestamp), priceToY(pt.price))
            }
        }
    }
}

private fun updateDrawingHandle(
    drawing: DrawingItem,
    handleIndex: Int,
    newPoint: ChartPoint
): DrawingItem {
    return when (drawing.toolType) {
        DrawingToolType.HORIZONTAL_LINE -> {
            val old = drawing.points.firstOrNull() ?: newPoint
            drawing.copy(points = listOf(ChartPoint(old.timestamp, newPoint.price)))
        }
        DrawingToolType.VERTICAL_LINE -> {
            val old = drawing.points.firstOrNull() ?: newPoint
            drawing.copy(points = listOf(ChartPoint(newPoint.timestamp, old.price)))
        }
        DrawingToolType.HORIZONTAL_RAY -> {
            drawing.copy(points = listOf(newPoint))
        }
        else -> {
            val list = drawing.points.toMutableList()
            if (handleIndex in 0 until list.size) {
                list[handleIndex] = newPoint
            }
            drawing.copy(points = list)
        }
    }
}

private fun translateDrawing(
    drawing: DrawingItem,
    deltaPrice: Double,
    deltaTimestamp: Long
): DrawingItem {
    return when (drawing.toolType) {
        DrawingToolType.HORIZONTAL_LINE -> {
            val old = drawing.points.firstOrNull() ?: return drawing
            drawing.copy(points = listOf(ChartPoint(old.timestamp, old.price + deltaPrice)))
        }
        DrawingToolType.VERTICAL_LINE -> {
            val old = drawing.points.firstOrNull() ?: return drawing
            drawing.copy(points = listOf(ChartPoint(old.timestamp + deltaTimestamp, old.price)))
        }
        else -> {
            val newPoints = drawing.points.map { pt ->
                ChartPoint(pt.timestamp + deltaTimestamp, pt.price + deltaPrice)
            }
            drawing.copy(points = newPoints)
        }
    }
}

private fun isPointOnDrawing(
    p: Offset,
    drawing: DrawingItem,
    timeToX: (Long) -> Float,
    priceToY: (Double) -> Float,
    chartLeft: Float,
    chartRight: Float,
    chartHeight: Float
): Boolean {
    val touchThreshold = 28f
    when (drawing.toolType) {
        DrawingToolType.HORIZONTAL_LINE -> {
            val price = drawing.points.firstOrNull()?.price ?: return false
            val y = priceToY(price)
            return abs(p.y - y) <= touchThreshold && p.x in chartLeft..chartRight
        }
        DrawingToolType.VERTICAL_LINE -> {
            val t = drawing.points.firstOrNull()?.timestamp ?: return false
            val x = timeToX(t)
            return abs(p.x - x) <= touchThreshold && p.y in 0f..chartHeight
        }
        DrawingToolType.HORIZONTAL_RAY -> {
            val pt = drawing.points.firstOrNull() ?: return false
            val o = Offset(timeToX(pt.timestamp), priceToY(pt.price))
            return p.x >= o.x - 12f && abs(p.y - o.y) <= touchThreshold
        }
        DrawingToolType.RECTANGLE -> {
            if (drawing.points.size < 2) return false
            val o1 = Offset(timeToX(drawing.points[0].timestamp), priceToY(drawing.points[0].price))
            val o2 = Offset(timeToX(drawing.points[1].timestamp), priceToY(drawing.points[1].price))
            val left = min(o1.x, o2.x) - 16f
            val right = max(o1.x, o2.x) + 16f
            val top = min(o1.y, o2.y) - 16f
            val bottom = max(o1.y, o2.y) + 16f
            return p.x in left..right && p.y in top..bottom
        }
        DrawingToolType.TEXT -> {
            val pt = drawing.points.firstOrNull() ?: return false
            val o = Offset(timeToX(pt.timestamp), priceToY(pt.price))
            return (p - o).getDistance() <= 40f
        }
        else -> {
            if (drawing.points.size < 2) return false
            for (i in 0 until drawing.points.size - 1) {
                val o1 = Offset(timeToX(drawing.points[i].timestamp), priceToY(drawing.points[i].price))
                val o2 = Offset(timeToX(drawing.points[i + 1].timestamp), priceToY(drawing.points[i + 1].price))
                if (distToSegment(p, o1, o2) <= touchThreshold) {
                    return true
                }
            }
            return false
        }
    }
}

private fun findDrawingNearPoint(
    tapOffset: Offset,
    drawings: List<DrawingItem>,
    candles: List<Candle>,
    candleWidth: Float,
    scrollOffset: Float,
    chartWidth: Float,
    chartHeight: Float,
    settings: ChartScaleSettings,
    verticalOffset: Float,
    verticalScaleRatio: Float = 1.0f
): DrawingItem? {
    if (candles.isEmpty()) return null
    val totalSlot = candleWidth * 1.25f
    val rightIdxFloat = (candles.lastIndex.toFloat() - scrollOffset)
    val firstT = candles.first().openTime
    val lastT = candles.last().openTime
    val avgSlotMs = if (candles.size > 1) (lastT - firstT).toDouble() / (candles.size - 1) else 60000.0

    fun tToX(t: Long): Float {
        val candleIdxFloat: Float = if (t <= firstT) {
            val diff = (t - firstT) / avgSlotMs
            diff.toFloat()
        } else if (t >= lastT) {
            val diff = (t - lastT) / avgSlotMs
            candles.lastIndex.toFloat() + diff.toFloat()
        } else {
            val diff = (t - firstT) / avgSlotMs
            diff.toFloat().coerceIn(0f, candles.lastIndex.toFloat())
        }
        val dist = rightIdxFloat - candleIdxFloat
        return chartWidth - (dist * totalSlot) - (candleWidth / 2f)
    }

    val minP = candles.minOfOrNull { it.low } ?: 0.0
    val maxP = candles.maxOfOrNull { it.high } ?: 1.0
    val priceRange = (maxP - minP).coerceAtLeast(0.0001)
    val scaledRange = (priceRange / verticalScaleRatio).coerceAtLeast(0.000001)
    val midP = (minP + maxP) / 2.0
    val effMinP = midP - (scaledRange / 2.0)

    fun pToY(p: Double): Float {
        var norm = ((p - effMinP) / scaledRange).toFloat()
        if (settings.isInverted) norm = 1f - norm
        return chartHeight - (norm * chartHeight) + verticalOffset
    }

    for (d in drawings.reversed()) {
        if (isPointOnDrawing(tapOffset, d, ::tToX, ::pToY, 0f, chartWidth, chartHeight)) {
            return d
        }
    }
    return null
}

private fun formatPrice(price: Double): String {
    return when {
        price >= 1000.0 -> String.format(Locale.US, "%,.2f", price)
        price >= 1.0 -> String.format(Locale.US, "%.4f", price)
        price > 0.0001 -> String.format(Locale.US, "%.6f", price)
        else -> String.format(Locale.US, "%.8f", price)
    }
}

private fun formatPriceLabel(price: Double, settings: ChartScaleSettings, basePrice: Double): String {
    return if (settings.isPercentage && basePrice > 0.0) {
        val pct = ((price - basePrice) / basePrice) * 100.0
        (if (pct >= 0) "+" else "") + String.format(Locale.US, "%.2f%%", pct)
    } else {
        formatPrice(price)
    }
}
