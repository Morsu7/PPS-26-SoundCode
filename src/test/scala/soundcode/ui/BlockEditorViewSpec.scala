package soundcode.ui

import soundcode.ui.editor.BlockEditorView
import soundcode.mvu.AppModel
import soundcode.domain.TextPosition

class BlockEditorViewSpec extends UITestSupport:
  test("block editor can be created"):
    onFxThread:
      val editor = new BlockEditorView

      assert(editor.root != null)

  test("currentCode returns the initial source code"):
    onFxThread:
      val code = "note(\"c4 a4\").sound(\"piano\")\nsound(\"hb hd hh\")"

      val editor = new BlockEditorView(code)

      assert(editor.currentCode == code)

  test("currentCode normalizes Windows newlines"):
    onFxThread:
      val editor = new BlockEditorView("note(\"c4\")\r\nsound(\"hh\")")

      assert(editor.currentCode == "note(\"c4\")\nsound(\"hh\")")

  test("render applies playback highlights without changing source code"):
    onFxThread:
      val code = "note(\"c4 a4\").sound(\"piano\")\nsound(\"hb hd hh\")"

      val editor = new BlockEditorView(code)

      editor.render(AppModel(positions = Set(TextPosition(0, 4))))

      assert(editor.currentCode == code)

  // FIXME: Check if syntax highlighting is applied correctly even when showing visualizers
