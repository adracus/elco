package de.adracus.elco.parser.production

import de.adracus.elco.grammar.production.ElcoGrammarBuilder
import de.adracus.elco.lexer.production.ElcoLexer
import de.adracus.elco.parser.core.Parser

/**
 * Created by axel on 16/06/15.
 */
class ElcoTransformer {
  val parser = Parser parsing ElcoGrammarBuilder.build()
  val lexer = new ElcoLexer()

  def transform(code: String) = {
    val stream = lexer.lex(code)
    val tree = parser.parse(stream)
    println(tree.toFormatted())
  }
}

object ElcoTransformer extends App {
  val transformer = new ElcoTransformer()

  val tree = transformer.transform("5 + 6 - 4 / 3 * 5 + 7")
}
