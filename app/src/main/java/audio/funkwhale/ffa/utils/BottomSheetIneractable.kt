package audio.funkwhale.ffa.utils

import androidx.customview.widget.Openable

interface BottomSheetIneractable: Openable {
  val isHidden: Boolean
  fun show()
  fun hide()
  fun toggle()
}