package soundcode.ui

private object UITheme:
  val Background = "#1f1f24"
  val Foreground = "#f4f4f5"
  val Muted = "#8b949e"
  val String = "#d9f99d"
  val Function = "#93c5fd"
  val VisualizerLine = "#505058"
  val FontFamily = "'Cascadia Code', 'JetBrains Mono', 'Consolas'"

  def backgroundStyle: String =
    s"-fx-background-color: $Background;"

  def textStyle(color: String): String =
    s"-fx-fill: $color; -fx-font-smoothing-type: gray;"
