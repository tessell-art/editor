package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L._

object IconsSVG:

  private def createIcon(viewBoxStr: String)(paths: Element*): Element =
    svg.svg(
      svg.width   := "1em",
      svg.height  := "1em",
      svg.viewBox := viewBoxStr,
      svg.fill    := "currentColor",
      paths
    )

  private[components] def selectByColorIcon: Element =
    createIcon("0 0 37.643265 44.674143")(
      svg.path(
        svg.d           :=
          "M 15.302,0 C 6.85,0 0,6.309 0,14.09 c 0,7.781 6.85,14.092 15.302,14.092 1.519,-8.259 4.996,-9.012 8.362,-9.012 0.751,0 1.497,0.038 2.214,0.038 2.521,0 4.687,-0.463 5.502,-4.646 C 32.744,7.586 23.752,0 15.302,0 Z m 14.335958,14.790305 c -0.744518,2.094393 -0.955291,2.261786 -3.024775,2.620009 -0.933269,0.161547 -0.832255,0.05748 -1.541035,-0.01983 -0.399,-0.01 -1.037565,-0.119539 -1.441565,-0.119539 -3.879,0 -7.639278,1.034464 -9.861278,8.777464 C 6.2285932,24.929856 1.9315932,19.753102 1.9315932,14.357102 c 0,-6.1150003 4.4505932,-12.4255088 13.5039578,-12.5590596 4.028562,-0.059427 9.877508,3.1268559 12.564508,6.3888559 0.901,1.0939997 2.079899,4.3374067 1.637899,6.6034067 z"
      ),
      svg.path(
        svg.d           :=
          "m 10.26,15.943 c -1.565,0 -2.839,1.273 -2.839,2.839 0,1.566 1.273,2.839 2.839,2.839 1.564,0 2.838,-1.273 2.838,-2.839 0,-1.566 -1.273,-2.839 -2.838,-2.839 z m 0,4.178 c -0.738,0 -1.339,-0.602 -1.339,-1.339 0,-0.738 0.601,-1.339 1.339,-1.339 0.737,0 1.338,0.602 1.338,1.339 0,0.737 -0.6,1.339 -1.338,1.339 z"
      ),
      svg.circle(svg.cx := "8.467", svg.cy     := "11.012", svg.r    := "2.0880001"),
      svg.circle(svg.cx := "13.296", svg.cy    := "7.2950001", svg.r := "2.089"),
      svg.circle(svg.cx := "19.381001", svg.cy := "8.7869997", svg.r := "2.089"),
      svg.circle(svg.cx := "24.089001", svg.cy := "12.497", svg.r    := "2.089"),
      svg.g(
        svg.transform   := "matrix(0.09071207,0,0,0.09071207,11.351823,13.156144)",
        svg.polygon(
          svg.points :=
            "57.617,303.138 123.48,224.061 181.017,347.451 244.459,317.867 186.921,194.478 289.834,194.854 57.617,0 "
        )
      )
    )

  private[components] def quadrupleIcon: Element =
    createIcon("0 0 34.535 34.546")(
      svg.g(
        svg.transform        := "translate(-60.118 -68.204)",
        svg.stroke           := "currentColor",
        svg.strokeLineJoin   := "round",
        svg.strokeMiterLimit := "16",
        svg.rect(
          svg.x           := "61.388",
          svg.y           := "69.478",
          svg.width       := "16",
          svg.height      := "16",
          svg.fill        := "none",
          svg.strokeWidth := "1.0"
        ),
        svg.rect(
          svg.x           := "61.383",
          svg.y           := "85.488",
          svg.width       := "16",
          svg.height      := "16",
          svg.fillOpacity := "1.0",
          svg.fillRule    := "evenodd",
          svg.strokeWidth := "1.0"
        ),
        svg.rect(
          svg.x           := "77.38",
          svg.y           := "69.477",
          svg.width       := "16",
          svg.height      := "16",
          svg.fill        := "none",
          svg.strokeWidth := "1.0"
        ),
        svg.rect(
          svg.x           := "77.382",
          svg.y           := "85.479",
          svg.width       := "16",
          svg.height      := "16",
          svg.fill        := "none",
          svg.strokeWidth := "1.0"
        ),
        svg.path(
          svg.d           := "m65.25 70.958-2.4539 5.6317 1.605-5.2e-4v7.3765h1.6999v-7.3776l1.6057-5.2e-4z",
          svg.fillOpacity := "1.0",
          svg.fillRule    := "evenodd",
          svg.strokeWidth := "0.46226"
        ),
        svg.path(
          svg.d           :=
            "m91.889 70.958-5.7174 2.247 1.1352 1.1345-5.216 5.216 1.202 1.202 5.2167-5.2167 1.1358 1.1351z",
          svg.fillOpacity := "1.0",
          svg.fillRule    := "evenodd",
          svg.strokeWidth := "0.46226"
        ),
        svg.path(
          svg.d           := "m91.889 97.63-5.6317-2.4539 5.2e-4 1.605h-7.3765v1.6999h7.3776l5.2e-4 1.6058z",
          svg.fillOpacity := "1.0",
          svg.fillRule    := "evenodd",
          svg.strokeWidth := "0.46226"
        ),
        svg.circle(
          svg.cx          := "61.406",
          svg.cy          := "101.46",
          svg.r           := "1",
          svg.fillRule    := "evenodd",
          svg.strokeWidth := "0.52917"
        ),
        svg.circle(
          svg.cx          := "61.383",
          svg.cy          := "69.485",
          svg.r           := "1",
          svg.fillRule    := "evenodd",
          svg.strokeWidth := "0.52917"
        ),
        svg.circle(
          svg.cx          := "93.388",
          svg.cy          := "69.469",
          svg.r           := "1",
          svg.fillRule    := "evenodd",
          svg.strokeWidth := "0.52917"
        ),
        svg.circle(
          svg.cx          := "93.388",
          svg.cy          := "101.49",
          svg.r           := "1",
          svg.fillRule    := "evenodd",
          svg.strokeWidth := "0.52917"
        ),
        svg.circle(
          svg.cx          := "77.378",
          svg.cy          := "85.465",
          svg.r           := ".5",
          svg.fillRule    := "evenodd",
          svg.strokeWidth := "0.26458"
        ),
        svg.circle(
          svg.cx          := "77.385",
          svg.cy          := "101.49",
          svg.r           := ".5",
          svg.fillRule    := "evenodd",
          svg.strokeWidth := "0.26458"
        ),
        svg.circle(
          svg.cx          := "61.404",
          svg.cy          := "85.472",
          svg.r           := ".5",
          svg.fillRule    := "evenodd",
          svg.strokeWidth := "0.26458"
        ),
        svg.circle(
          svg.cx          := "61.414",
          svg.cy          := "101.47",
          svg.r           := ".5",
          svg.fillRule    := "evenodd",
          svg.strokeWidth := "0.26458"
        )
      )
    )

  private[components] def rulerIcon: Element =
    createIcon("0 0 256 256")(
      svg.g(
        svg.transform := "translate(871, -1129)",
        svg.path(
          svg.d :=
            "M-871,1185.5l199.2,199.7l56.8-56.7l-199.2-199.7L-871,1185.5z M-627,1328.5l-36.3,36.3l-187.3-187.7l36.4-36.2l25.4,25.4 l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12 l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12l-11.2,11.2l6,6l11.2-11.2l12,12l-17.2,17.2l6,6l17.2-17.2l12,12 l-11.2,11.2l6,6l11.2-11.2L-627,1328.5z M-820.3,1165.2c3.1,3,3.2,8,0.2,11.2c-3,3.1-8,3.2-11.2,0.2c-3.1-3-3.2-8-0.2-11.2 C-828.5,1162.3-823.5,1162.2-820.3,1165.2z"
        )
      )
    )

  private[components] def fanIcon: Element =
    createIcon("0 0 522.89362 522.89362")(
      svg.path(
        svg.d :=
          "m 247.14894,448.78305 c -10.35722,-2.2429 -27.57447,-11.62749 -27.57447,-15.02999 0,-0.71388 6.69119,-7.43938 14.86931,-14.94556 8.17812,-7.50618 14.49727,-13.96448 14.04255,-14.35177 -0.45471,-0.3873 -56.43527,-24.72583 -124.40122,-54.08562 C 56.119149,321.01031 0.36023509,296.84559 0.17640896,296.67074 -3.0919826,293.56183 13.386374,242.78202 25.515216,218.5865 c 97.114854,-193.731934 374.748334,-193.731934 471.863184,0 12.12884,24.19552 28.6072,74.97533 25.33881,78.08424 -0.18383,0.17485 -55.94274,24.33957 -123.9087,53.69937 -67.96596,29.35979 -123.94651,53.69832 -124.40123,54.08562 -0.45471,0.38729 5.86443,6.84559 14.04256,14.35177 16.63075,15.26432 16.5145,15.05175 10.58168,19.34946 -14.66914,10.62627 -33.79399,14.54324 -51.88258,10.62609 z M 158.49421,329.87234 c 2.207,-4.77447 5.82314,-11.47034 8.03586,-14.87971 2.21272,-3.40938 4.02312,-6.46993 4.02312,-6.80123 0,-0.47948 -50.29929,-40.38272 -57.34514,-45.49281 -1.59087,-1.1538 -2.22412,-0.63254 -5.58317,4.59575 -8.257442,12.85256 -22.456816,46.14343 -20.058757,47.02839 0.425866,0.15716 15.250897,6.63309 32.944517,14.39096 30.76056,13.48714 32.20966,14.01175 33.07052,11.97235 0.49517,-1.17307 2.70604,-6.03923 4.91305,-10.8137 z m 245.93132,-2.05473 c 17.13192,-7.44743 31.27212,-13.64008 31.42269,-13.76145 3.42274,-2.75914 -22.63697,-53.91462 -26.16266,-51.35757 -7.66751,5.56097 -57.3442,45.03497 -57.34076,45.56402 0.002,0.37046 1.41671,2.74164 3.1429,5.2693 3.50505,5.13244 12.5306,22.88801 13.48005,26.51873 0.79115,3.02534 -1.3091,3.74993 35.45778,-12.23303 z M 188.79444,290.17435 c 4.72919,-3.94639 16.52251,-11.88042 18.83508,-12.67143 1.43274,-0.49006 2.28679,1.10467 -21.33358,-39.83494 l -17.54936,-30.41712 -9.05414,6.08492 c -10.41744,7.00113 -26.49084,20.95378 -32.75201,28.43064 l -4.2936,5.12728 29.82552,23.53076 c 34.14928,26.94197 30.24647,24.81984 36.32209,19.74989 z m 181.34029,-19.66395 30.11159,-23.61726 -4.29337,-5.127 c -11.19941,-13.37394 -39.39913,-35.12452 -41.93847,-32.34732 -1.97326,2.1581 -38.44001,65.84062 -38.44001,67.12847 0,0.60063 2.64255,2.71619 5.87234,4.70124 3.22978,1.98505 8.4,5.67739 11.48936,8.2052 3.08936,2.52781 5.94776,4.61355 6.35199,4.63498 0.40424,0.0214 14.28519,-10.58882 30.84657,-23.57831 z m -134.73048,-3.30712 c 3.37022,-0.82504 8.54043,-1.81153 11.48937,-2.19219 l 5.3617,-0.69212 v -41.07134 -41.07134 l -7.9149,0.80824 c -22.00222,2.24677 -58.46808,12.51182 -58.46808,16.45863 0,0.59725 3.60999,7.32431 8.0222,14.94903 4.41221,7.62472 13.42039,23.18573 20.01819,34.58002 9.20739,15.90103 12.38744,20.60231 13.67993,20.22401 0.92616,-0.27108 4.44138,-1.1679 7.81159,-1.99294 z m 82.5815,-32.39968 c 11.03635,-19.14738 19.84061,-35.17825 19.56502,-35.62415 -2.658,-4.30073 -38.73825,-14.279 -58.48694,-16.17501 l -8.42553,-0.80891 v 41.04007 41.04006 l 7.91489,1.164 c 4.35319,0.6402 9.98298,1.81127 12.51064,2.60236 2.52766,0.7911 5.10427,1.46911 5.72579,1.50668 0.62153,0.0376 10.15979,-15.59772 21.19613,-34.7451 z"
      )
    )

  private[components] val eraserViewBox: String = "0 0 507.85 507.85"
  private[components] val eraserPathD: String   =
    "M490.8,148.725l-104.8-104c-11.2-10.8-26-17.2-41.6-17.2c-15.6,0-30.4,6-41.6,17.2l-285.6,284 C6,339.525,0,354.325,0,369.925c0,15.6,6,30.4,17.2,41.2l66.8,66.8c1.6,1.6,3.6,2.4,5.6,2.4h279.6c4.4,0,8-3.6,8-8 c0-4.4-3.6-8-8-8H256.4l234-232.8C513.6,208.725,513.6,171.525,490.8,148.725z M234,464.325H92.8l-64.4-64 c-8-8-12.4-18.8-12.4-30.4c0-11.6,4.4-22,12.4-30.4l140.4-139.2l165.2,164.4L234,464.325z"

  private[components] def eraserIcon: Element =
    createIcon(eraserViewBox)(
      svg.path(
        svg.d := eraserPathD
      )
    )

//  private[components] val inserterViewBox: String = "0 0 22 22"
//  private[components] val inserterPathD: String   = "M 2,2 H 22 V 22 H 2 Z M 2,22 L 12,4.68 L 22,22 Z"
//
//  private[components] def inserterIcon: Element =
//    createIcon(inserterViewBox)(
//      svg.path(
//        svg.d           := inserterPathD,
//        svg.fill        := "none",
//        svg.stroke      := "currentColor",
//        svg.strokeWidth := "2"
//      )
//    )

  private[components] def plusIcon: Element =
    createIcon("0 0 24 24")(
      svg.path(
        svg.d             := "M12 5v14M5 12h14",
        svg.stroke        := "currentColor",
        svg.fill          := "none",
        svg.strokeWidth   := "2",
        svg.strokeLineCap := "round"
      )
    )

  /** Fit-to-viewport icon: four corner brackets + diagonals from center to each corner. Used by the mobile
    * toolbar's Fit button as a replacement for the textual "Fit" label.
    */
  private[components] def maximizeIcon: Element =
    createIcon("0 0 24 24")(
      svg.g(
        svg.fill           := "none",
        svg.stroke         := "currentColor",
        svg.strokeWidth    := "2",
        svg.strokeLineCap  := "round",
        svg.strokeLineJoin := "round",
        svg.path(svg.d := "M4 9V4h5"),
        svg.path(svg.d := "M20 9V4h-5"),
        svg.path(svg.d := "M4 15v5h5"),
        svg.path(svg.d := "M20 15v5h-5"),
        svg.path(svg.d := "M10 10L4 4"),
        svg.path(svg.d := "M14 10l6-6"),
        svg.path(svg.d := "M10 14l-6 6"),
        svg.path(svg.d := "M14 14l6 6")
      )
    )

  /** 2×2 grid of empty cells. Mobile toolbar uses this when *all* polygons are currently selected — the next
    * tap will deselect, and the empty grid previews that result.
    */
  private[components] def selectionGridEmptyIcon: Element =
    createIcon("0 0 24 24")(
      svg.g(
        svg.fill           := "none",
        svg.stroke         := "currentColor",
        svg.strokeWidth    := "1.6",
        svg.strokeLineJoin := "round",
        svg.rect(svg.x := "3", svg.y  := "3", svg.width  := "8", svg.height := "8"),
        svg.rect(svg.x := "13", svg.y := "3", svg.width  := "8", svg.height := "8"),
        svg.rect(svg.x := "3", svg.y  := "13", svg.width := "8", svg.height := "8"),
        svg.rect(svg.x := "13", svg.y := "13", svg.width := "8", svg.height := "8")
      )
    )

  /** 2×2 grid of filled cells. Mobile toolbar uses this when polygons are partly or not selected — the next
    * tap will select all, and the filled grid previews that result.
    */
  private[components] def selectionGridFilledIcon: Element =
    createIcon("0 0 24 24")(
      svg.g(
        svg.fill           := "currentColor",
        svg.stroke         := "currentColor",
        svg.strokeWidth    := "1.6",
        svg.strokeLineJoin := "round",
        svg.rect(svg.x := "3", svg.y  := "3", svg.width  := "8", svg.height := "8"),
        svg.rect(svg.x := "13", svg.y := "3", svg.width  := "8", svg.height := "8"),
        svg.rect(svg.x := "3", svg.y  := "13", svg.width := "8", svg.height := "8"),
        svg.rect(svg.x := "13", svg.y := "13", svg.width := "8", svg.height := "8")
      )
    )

  private def eyeDropperPath: Element =
    svg.path(
      svg.d :=
        "M 39.6485 28.9024 L 40.3048 28.1758 C 41.4532 26.9805 41.5001 25.5742 40.2813 24.3555 L 39.5782 23.6758 C 43.1641 20.4649 47.1485 20.0195 49.6095 17.5117 C 53.1015 13.9961 51.9533 9.0742 49.5159 6.6133 C 47.0780 4.1289 42.2032 3.0742 38.6172 6.5195 C 36.0860 8.9571 35.6641 12.9649 32.4532 16.5508 L 31.7735 15.8476 C 30.5548 14.6289 29.1485 14.6758 27.9532 15.8242 L 27.2266 16.4805 C 25.7969 17.8633 26.0548 19.0820 27.2969 20.3242 L 28.2813 21.3086 L 10.5860 39.0274 C 3.3438 46.2695 6.8360 45.1445 2.8985 50.6992 L 4.9845 52.9258 C 10.3750 49.0117 9.6485 52.8555 16.9845 45.5195 L 34.7969 27.8242 L 35.8048 28.8320 C 37.0469 30.0742 38.2657 30.3320 39.6485 28.9024 Z M 10.1172 46.1289 C 9.2501 45.1914 9.4141 44.3008 10.3516 43.3633 L 30.2969 23.3242 L 32.8516 25.8789 L 12.8126 45.8945 C 11.9923 46.7383 10.9141 46.9961 10.1172 46.1289 Z"
    )

  private val EyeDropperViewBox = "0 0 56 56"

  private[components] def eyeDropperIcon: Element =
    createIcon(EyeDropperViewBox)(
      eyeDropperPath
    )

  private def pentagonPath: Element =
    svg.path(
      svg.d                :=
        "M 26.825174,2.5454546 43.692134,26.323083 26.290443,49.712206 -1.3313534,40.389851 -1.000871,11.239195 Z",
      svg.transform        := "matrix(1.0313471,0,0,1.025013,7.6870211,-0.9863272)",
      svg.opacity          := "1",
      svg.fill             := "none",
      svg.fillRule         := "evenodd",
      svg.stroke           := "currentColor",
      svg.strokeWidth      := "3.03576",
      svg.strokeLineJoin   := "round",
      svg.strokeMiterLimit := "16",
      svg.strokeDashArray  := "none",
      svg.strokeOpacity    := "1"
    )

  private[components] def eyeDropperPentagonIcon: Element =
    createIcon(EyeDropperViewBox)(
      pentagonPath,
      eyeDropperPath
    )

  private[components] def sunIcon: Element =
    createIcon("0 0 24 24")(
      svg.path(
        svg.d :=
          "M12 0a1 1 0 0 1 1 1v4a1 1 0 1 1-2 0V1a1 1 0 0 1 1-1ZM4.929 3.515a1 1 0 0 0-1.414 1.414l2.828 2.828a1 1 0 0 0 1.414-1.414L4.93 3.515ZM1 11a1 1 0 1 0 0 2h4a1 1 0 1 0 0-2H1ZM18 12a1 1 0 0 1 1-1h4a1 1 0 1 1 0 2h-4a1 1 0 0 1-1-1ZM17.657 16.243a1 1 0 0 0-1.414 1.414l2.828 2.828a1 1 0 1 0 1.414-1.414l-2.828-2.828ZM7.757 17.657a1 1 0 1 0-1.414-1.414L3.515 19.07a1 1 0 1 0 1.414 1.414l2.828-2.828ZM20.485 4.929a1 1 0 0 0-1.414-1.414l-2.828 2.828a1 1 0 1 0 1.414 1.414l2.828-2.828ZM13 19a1 1 0 1 0-2 0v4a1 1 0 1 0 2 0v-4ZM12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10Z"
      )
    )

  private[components] def moonIcon: Element =
    createIcon("0 0 35 35")(
      svg.path(
        svg.d :=
          "M18.44,34.68a18.22,18.22,0,0,1-2.94-.24,18.18,18.18,0,0,1-15-20.86A18.06,18.06,0,0,1,9.59.63,2.42,2.42,0,0,1,12.2.79a2.39,2.39,0,0,1,1,2.41L11.9,3.1l1.23.22A15.66,15.66,0,0,0,23.34,21h0a15.82,15.82,0,0,0,8.47.53A2.44,2.44,0,0,1,34.47,25,18.18,18.18,0,0,1,18.44,34.68ZM10.67,2.89a15.67,15.67,0,0,0-5,22.77A15.66,15.66,0,0,0,32.18,24a18.49,18.49,0,0,1-9.65-.64A18.18,18.18,0,0,1,10.67,2.89Z"
      )
    )
