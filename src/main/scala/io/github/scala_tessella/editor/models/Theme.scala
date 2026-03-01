package io.github.scala_tessella.editor.models

enum Theme(val modeClass: String):
  case Light extends Theme("light-mode")
  case Dark  extends Theme("dark-mode")

  def toggle: Theme =
    this match
      case Light => Dark
      case Dark  => Light
