package audio.funkwhale.ffa.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageButton
import androidx.annotation.ColorRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.databinding.BindingAdapter


@BindingAdapter("srcCompat")
fun setImageViewResource(imageView: AppCompatImageView, resource: Any?) = when (resource) {
  is Bitmap -> imageView.setImageBitmap(resource)
  is Int -> imageView.setImageResource(resource)
  is Drawable -> imageView.setImageDrawable(resource)
  else -> imageView.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
}

@BindingAdapter("tint")
fun setTint(imageView: ImageButton, @ColorRes resource: Int) = resource.let {
  imageView.setColorFilter(resource)
}