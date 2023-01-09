package audio.funkwhale.ffa.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

open class SquareImageView : AppCompatImageView {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, style: Int) : super(context, attrs, style)

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val dimension = if(measuredWidth == 0 && measuredHeight > 0) measuredHeight else measuredWidth

    setMeasuredDimension(dimension, dimension)
  }
}
