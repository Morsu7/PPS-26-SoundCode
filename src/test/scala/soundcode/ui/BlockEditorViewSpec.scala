package soundcode.ui

import soundcode.ui.editor.BlockEditorView

class BlockEditorViewSpec extends UITestSupport:
  test("block editor can be created"):
    onFxThread:
      val editor = new BlockEditorView

      assert(editor.root != null)

  // FIXME: Check if syntax highlighting is applied correctly even when showing visualizers
  // test("automatic piano roll insertion preserves editable source code"):
  //   onFxThread:
  //     val code =
  //       """note("c4 a4").sound("piano")
  //         |sound("hb hd hh")""".stripMargin

  //     val editor = new BlockEditorView(code)

  //     assert(editor.currentCode == code)
