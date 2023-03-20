package com.zwt.spark

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.Interpolator
import kotlin.math.pow
import kotlin.math.sqrt

class SparkView : View {
    companion object {
        private const val TAG = "SparkView"
        private const val DEFAULT_SPARK_WIDTH = 10F
        private const val DEFAULT_SPARK_SLOPE = 0F
        private const val DEFAULT_SPARK_DURATION = 1000
        private const val DEFAULT_SPARK_DIRECTION = 0
        private val DEFAULT_INTERPOLATOR = AccelerateInterpolator()
    }

    private lateinit var mPaint: Paint
    private var mBackgroundColor : Int = Color.BLACK
    private var mGradientStartColor : Int = Color.BLACK

    private var mParallelSlope: Float = 0f

    // 左上->右下 右下->左上 左下->右上 右上->左下
    private var mDirection: Int = 1

    private var mParallelAnimator: ValueAnimator? = null

    private var mParallelAnimatorDuration: Long = 1000

    var mParallelAnimatorInterpolator: Interpolator = DEFAULT_INTERPOLATOR

    // 绘制的平行线与视图相交的点
    private val mStartPoint : PointF = PointF()
    private val mEndPoint : PointF = PointF()

    private val mGradientStartPoint : PointF = PointF()
    private val mGradientEndPoint : PointF = PointF()

    private var mSparkWidth : Float = DEFAULT_SPARK_WIDTH

    private var mIsAnimationRun = false

    private var mLinearGradient : LinearGradient? = null

    constructor(context: Context?) : this(context, null) {}

    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0) {}

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs,R.styleable.SparkView)
        val sparkColor = typedArray.getColor(R.styleable.SparkView_sparkColor,resources.getColor(R.color.spark_color))
        mSparkWidth = typedArray.getDimension(R.styleable.SparkView_sparkWidth,DEFAULT_SPARK_WIDTH)
        val sparkSlope = typedArray.getFloat(R.styleable.SparkView_sparkSlope,DEFAULT_SPARK_SLOPE)
        val sparkDuration = typedArray.getInt(R.styleable.SparkView_sparkDuration,DEFAULT_SPARK_DURATION).toLong()
        val sparkDirection = typedArray.getInt(R.styleable.SparkView_sparkDirection, DEFAULT_SPARK_DIRECTION)
        typedArray.recycle()

        mBackgroundColor = resources.getColor(R.color.spark_background_color)
        mGradientStartColor = resources.getColor(R.color.spark_gradient_start_color)

        mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mPaint.style = Paint.Style.STROKE
        mPaint.strokeCap = Paint.Cap.SQUARE
        mPaint.color = sparkColor
        mPaint.strokeWidth = mSparkWidth
        mParallelSlope = sparkSlope
        mParallelAnimatorDuration = sparkDuration
        mDirection = sparkDirection

        // 移动方向的矫正
        correctDirection()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        Log.d(TAG,"onMeasure")
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        Log.d(TAG,"onLayout")
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        Log.d(TAG,"onDraw")
        canvas?.drawColor(mBackgroundColor)
        if(mIsAnimationRun){
            canvas?.drawLine(mStartPoint.x, mStartPoint.y, mEndPoint.x, mEndPoint.y, mPaint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG,"onAttachedToWindow")
        cancelParallelAnimator()
        post {
            var startValue = 0f
            var endValue = 0f
            when(mDirection){
                0->{
                    if(mParallelSlope <= 0){
                        startValue = 0f
                        endValue = height.toFloat() - mParallelSlope * width.toFloat()
                    }
                }

                1->{
                    if(mParallelSlope <= 0){
                        startValue = height.toFloat() - mParallelSlope * width.toFloat()
                        endValue = 0f
                    }
                }

                2->{
                    if(mParallelSlope > 0){
                        startValue = height.toFloat()
                        endValue = 0f - mParallelSlope * width.toFloat()
                    }
                }

                3->{
                    if(mParallelSlope > 0){
                        startValue = 0f - mParallelSlope * width.toFloat()
                        endValue = height.toFloat()
                    }
                }

                else->{

                }
            }

            mParallelAnimator = ValueAnimator.ofFloat(startValue, endValue)
            mParallelAnimator?.addUpdateListener {
                val animatedValue = it.animatedValue as Float
                Log.d(TAG,"animatedValue:${animatedValue}")
                if(mParallelSlope != 0f){
                    calculateSparkLineIntersection(animatedValue)
                    calculateGradientIntersection(animatedValue)
                }else{
                    mStartPoint.x = 0f
                    mStartPoint.y = animatedValue
                    mEndPoint.x = width.toFloat()
                    mEndPoint.y = animatedValue
                }

                invalidate()
            }

            mParallelAnimator?.addListener(object : AnimatorListener {
                override fun onAnimationStart(p0: Animator) {
                    Log.d(TAG,"animation start")
                    mIsAnimationRun = true
                }

                override fun onAnimationEnd(p0: Animator) {
                    Log.d(TAG,"animation end")
                    mIsAnimationRun = false
                    invalidate()
                }

                override fun onAnimationCancel(p0: Animator) {
                    Log.d(TAG,"animation cancel")
                }

                override fun onAnimationRepeat(p0: Animator) {
                    Log.d(TAG,"animation repeat")
                }
            })

            mParallelAnimator?.interpolator = mParallelAnimatorInterpolator
            mParallelAnimator?.duration = mParallelAnimatorDuration
            mParallelAnimator?.repeatCount = ValueAnimator.INFINITE
            mParallelAnimator?.start()
        }
    }

    private fun calculateSparkLineIntersection(animatedValue: Float) {
        correctDirection()
        // 与y = 0 的交点（0<= x <= width)
        val intersectionXAxis = (0 - animatedValue) / mParallelSlope
        if (intersectionXAxis in 0f..width.toFloat()) {
            mStartPoint.x = intersectionXAxis;
            mStartPoint.y = 0f;
        }

        // 与y轴的交点（0，0）-》（0，height)
        val intersectionYAxis = animatedValue
        if (intersectionYAxis in 0f..height.toFloat()) {
            if(mDirection == 2 || mDirection == 3){
                mStartPoint.x = 0f;
                mStartPoint.y = intersectionYAxis
            }else{
                mEndPoint.x = 0f;
                mEndPoint.y = intersectionYAxis
            }
        }

        // 与x轴平行的: y = height （0<= x <= width)
        val intersectionXParallelXAxis = (height.toFloat() - animatedValue) / mParallelSlope
        if (intersectionXParallelXAxis in 0f..width.toFloat()) {
            mEndPoint.x = intersectionXParallelXAxis
            mEndPoint.y = height.toFloat()
        }

        // 与y轴平行: (width,0)....(width,height)
        val intersectionYParallelYAxis = mParallelSlope * width.toFloat() + animatedValue
        if (intersectionYParallelYAxis in 0f..height.toFloat()) {
            if(mDirection == 2 || mDirection == 3){
                mEndPoint.x = width.toFloat()
                mEndPoint.y = intersectionYParallelYAxis
            }else{
                mStartPoint.x = width.toFloat()
                mStartPoint.y = intersectionYParallelYAxis
            }
        }
    }

    private fun calculateGradientIntersection(animatedValue: Float){
        val dH = sqrt((mParallelSlope.pow(2) + 1) * mSparkWidth.pow(2))
        mGradientStartPoint.x = mParallelSlope * mStartPoint.y + mStartPoint.x - mParallelSlope * (animatedValue - dH)
        mGradientStartPoint.y = mParallelSlope * mGradientStartPoint.x + (animatedValue - dH)

        mGradientEndPoint.x = mParallelSlope * mStartPoint.y + mStartPoint.x - mParallelSlope * (animatedValue + dH)
        mGradientEndPoint.y = mParallelSlope * mGradientStartPoint.x + (animatedValue + dH)

        mLinearGradient = LinearGradient(mGradientStartPoint.x,
                                        mGradientStartPoint.y,
                                        mGradientEndPoint.x,
                                        mGradientEndPoint.y,
                                        mGradientStartColor,
                                        Color.WHITE,
                                        Shader.TileMode.CLAMP)
        mPaint.shader = mLinearGradient
    }

    private fun correctDirection() {
        if (mParallelSlope < 0 && (mDirection == 2 || mDirection == 3)) {
            mDirection = 0
        } else if (mParallelSlope > 0 && (mDirection == 0 || mDirection == 1)) {
            mDirection = 2
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelParallelAnimator()
    }

    private fun cancelParallelAnimator() {
        mParallelAnimator?.cancel()
        mParallelAnimator = null
    }

}