package de.adracus.elco.grammar.core

/**
 * Created by axel on 10/06/15.
 */
class Grammar(pointer: NonTerminal, ruleSet: Set[Rule], val precedences: Set[Precedence]) {
  require(ruleSet.exists(_.nonTerminal == pointer), message = "Pointer has to be contained in rule set")

  val startSymbol = NonTerminal("Start")
  val startRule = Rule(startSymbol, Production(List(pointer)))
  val rules = ruleSet + startRule

  def statements = rules.flatMap { rule =>
    Set(rule.nonTerminal) ++ rule.production.statements
  }

  def rulesStartingWith(producable: Producable) = {
    rules.filter(_.headOption.exists(_ == producable))
  }
}
