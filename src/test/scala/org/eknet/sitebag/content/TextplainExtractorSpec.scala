package org.eknet.sitebag.content

import org.scalatest.{ FlatSpec, Matchers }

class TextplainExtractorSpec extends FlatSpec with Matchers {

  "A TextplainExtractor" should "add some html around paragraphs" in {
    val text = "first word or the next word.\nnext work or the first work"
    val extr = TextplainExtractor.addMinimalHtml(text)
    assert(extr === ("<p>" + text + "</p>"))
  }

  it should "find simple headlines" in {
    val text = """1. head line
                 |
                 |this is an abstract with a few
                 |lines. this is an abstract with
                 |a few lines.
                 |
                 |this is an abstract with a few
                 |lines. this is an abstract with
                 |a few lines.
                 |
                 |1.2. sub heading
                 |
                 |this is an abstract with a few
                 |lines. this is an abstract with
                 |a few lines""".stripMargin
    val extr = TextplainExtractor.addMinimalHtml(text)
    val exspect = """<h1>1. head line</h1>
                    |
                    |<p>this is an abstract with a few
                    |lines. this is an abstract with
                    |a few lines.</p>
                    |
                    |<p>this is an abstract with a few
                    |lines. this is an abstract with
                    |a few lines.</p>
                    |
                    |<h2>1.2. sub heading</h2>
                    |
                    |<p>this is an abstract with a few
                    |lines. this is an abstract with
                    |a few lines</p>""".stripMargin

    assert(extr === exspect)
  }

  it should "not fail on weird content" in {
    val text = "{ /%&() }" :: "" :: "   " :: "\n\n\n\n \n\n\n \n\n \n \n\n" :: Nil
    text foreach { txt =>
      val extr = TextplainExtractor.addMinimalHtml(txt)
      assert(extr === ("<p>" + txt.trim + "</p>"))
    }
  }

}
