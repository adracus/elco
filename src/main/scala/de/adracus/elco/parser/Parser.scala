package de.adracus.elco.parser

import de.adracus.elco.grammar.core._
import de.adracus.elco.lexer.core.Token

import scala.collection.mutable

/**
 * Created by axel on 02/06/15.
 */
class Parser(val grammar: Grammar) {
  private def firstStep(first: Map[String, Set[Statement]], statements: List[Statement]) = {
    def inner(acc: Set[Statement], statements: List[Statement]): Set[Statement] = statements match {
      case Nil => acc + Epsilon
      case st :: tail =>
        if (st.isInstanceOf[BaseTerminal]) acc + st
        else {
          if (first(st.name) contains Epsilon) inner(acc ++ (first(st.name) - Epsilon), tail)
          else acc ++ first(st.name)
        }
    }

    inner(Set.empty, statements)
  }

  private def computeFirst(grammar: Grammar) = {
    val first = new mutable.HashMap[String, Set[Statement]]().withDefaultValue(Set.empty)
    val rules = grammar.rules.groupBy(_.nonTerminal)

    def addToFirst(key: Statement, values: Statement*) = {
      first(key.name) = first(key.name) ++ values
    }


    for (nonTerminal <- grammar.statements
         if nonTerminal.isInstanceOf[NonTerminal];
         rule <- rules(nonTerminal.asInstanceOf[NonTerminal])) {
      if (rule.production.isEpsilonProduction) {
        addToFirst(nonTerminal, Epsilon)
      }
      if (rule.production.startsWithTerminal) {
        addToFirst(nonTerminal, rule.head)
      }
    }

    var old: mutable.Map[String, Set[Statement]] = null
    do {
      old = new mutable.HashMap[String, Set[Statement]]() ++ first

      for (ruleSet <- rules.values; rule <- ruleSet) {
        val A = rule.nonTerminal
        val production = rule.production

        addToFirst(A, firstStep(first.toMap.withDefaultValue(Set.empty), production.toList).toSeq:_*)
      }
    } while (old != first)

    Map[String, Set[Statement]]() ++ first
  }

  private def computeFollow(grammar: Grammar, first: Map[String, Set[Statement]]) = {
    val follow = new mutable.HashMap[String, Set[Statement]]().withDefaultValue(Set.empty)

    def addToFollow(key: Statement, values: Statement*) = {
      follow(key.name) = follow(key.name) ++ values
    }

    def followStep(statements: List[Statement], producer: NonTerminal) = {
      def inner(statements: List[Statement]): Unit = statements match {
        case Nil =>
        case st :: Nil =>
          if (st.isInstanceOf[NonTerminal]) addToFollow(st, follow(producer.name).toSeq:_*)
        case st :: tail =>
          if (!st.isInstanceOf[NonTerminal]) inner(tail)
          else {
            val addition = firstStep(first, tail)
            addToFollow(st, (addition - Epsilon).toSeq:_*)
            if (addition contains Epsilon) {
              addToFollow(st, follow(producer.name).toSeq:_*)
            }
            inner(tail)
          }
        case _ =>
      }

      inner(statements)
    }

    addToFollow(grammar.startSymbol, End)
    val allRules = grammar.rules

    for (nonTerminal <- grammar.statements
         if nonTerminal.isInstanceOf[NonTerminal];
         rule <- allRules) {
      addToFollow(
        nonTerminal,
        rule.production.terminalsAfter(nonTerminal.asInstanceOf[NonTerminal]).toSeq:_*)
    }

    var old: mutable.Map[String, Set[Statement]] = null
    do {
      old = new mutable.HashMap[String, Set[Statement]]() ++ follow
      for (rule <- grammar.rules) {
        followStep(rule.production.toList, rule.nonTerminal)
      }
    } while (old != follow)

    Map[String, Set[Statement]]() ++ follow
  }

  def firstOf(rule: Rule) = firstStep(first, rule.toList)

  def ruleFor(nonTerminal: NonTerminal, token: Token) = {
    val possibleRules = grammar.rules.filter(_.nonTerminal == nonTerminal)
    possibleRules.find(firstOf(_).contains(Terminal(token.name))).get
  }

  def computeItemSets(grammar: Grammar) = {
    val rules = grammar.rules.groupBy(_.nonTerminal).mapValues(Set() ++ _)

    val startingRule = grammar.rules.find(_.nonTerminal == grammar.startSymbol).get
    val itemSets = new mutable.LinkedHashSet[ItemSet]() + ItemSet.buildSet(Set(Item.start(startingRule)), rules)

    var old: Set[ItemSet] = null
    do {
      old = Set() ++ itemSets
      val addition = mutable.HashSet[ItemSet]()

      for(itemSet <- itemSets) {
        addition ++= itemSet.advance(rules)
      }

      itemSets ++= addition
    } while (old != itemSets)

    itemSets
  }

  def computeExtendedGrammar(grammar: Grammar) = {
    val rules = grammar.rules.groupBy(_.nonTerminal).mapValues(Set() ++ _)

    val itemSets = computeItemSets(grammar).toSeq

    def extend(itemSet: ItemSet, item: Item) = {
      val nonTerminal = item.rule.nonTerminal

      def inner(acc: Seq[ExtendedStatement], state: ItemSet, remaining: List[Statement]): Seq[ExtendedStatement] = remaining match {
        case Nil => acc
        case head :: tail =>
          val next = state.advanceBy(head, rules)
          val statement = ExtendedStatement(state, head, next)
          inner(acc :+ statement, next, tail)
      }

      val newNonTerminal = inner(Seq.empty, itemSet, List(nonTerminal)).head.asInstanceOf[ExtendedNonTerminal]
      val newStatements = inner(Seq.empty, itemSet, item.rule.toList)
      ExtendedRule(newNonTerminal, new ExtendedProduction(newStatements:_*))
    }

    val extendedBase = itemSets.flatMap { itemSet =>
      itemSet.filter(_.isAtStart).map { item =>
        extend(itemSet, item)
      }
    }

    new ExtendedGrammar(grammar.startSymbol, extendedBase.toSet)
  }

  def computeTranslationTable(grammar: Grammar) = {
    val rules = grammar.rules.groupBy(_.nonTerminal).mapValues(Set() ++ _)

    val result = new mutable.HashMap[(ItemSet, Statement), ItemSet]()
    for (itemSet <- itemSets; statement <- grammar.statements) {
      val advanced = itemSet.advanceBy(statement, rules)
      if (advanced.nonEmpty) {
        result((itemSet, statement)) = advanced
      }
    }
    result
  }

  def computeActionTable() = {
    val result = new mutable.HashMap[(ItemSet, Statement), Action]() ++ translationTable.map { tuple =>
      if (tuple._1._2.isInstanceOf[NonTerminal]) (tuple._1, Goto(tuple._2))
      else (tuple._1, Shift(tuple._2))
    }
    for (itemSet <- itemSets) {
      if (itemSet.hasItemAtEnd) {
        result((itemSet, End)) = Accept
      }
    }
    result
  }

  def reducedExtendedGrammar() = {
    val extendedGrammar = computeExtendedGrammar(grammar)
    val eGrammar = extendedGrammar.toEGrammar()
    val first = computeFirst(eGrammar)
    val follow = computeFollow(eGrammar, first)

    def eFollow(extendedNonTerminal: ExtendedNonTerminal) = {
      follow(extendedNonTerminal.eStatement.name)
    }

    val mappedRules = extendedGrammar.rules.foldLeft(new mutable.HashMap[ExtendedRule, Set[Statement]]())
    { (acc, rule) =>
      acc.put(rule, eFollow(rule.nonTerminal))
      acc
    }

    mappedRules.remove(extendedGrammar.startingRule)

    val result = new mutable.HashMap[(BaseItemSet, Statement), Rule]()

    def merge(rule: ExtendedRule) = {
      def inner(follow: Set[Statement], rules: List[ExtendedRule]): Unit = rules match {
        case Nil =>
          mappedRules.remove(rule)
          for (statement <- follow) {
            result((rule.finalSet, statement)) = rule.toRule()
          }
        case head :: tail =>
          if (rule.toRule() == head.toRule() && rule.finalSet == head.finalSet) {
            val addition = mappedRules(head)
            mappedRules.remove(head)
            inner(follow ++ addition, tail)
          } else {
            inner(follow, tail)
          }
      }

      inner(mappedRules(rule), mappedRules.keys.toList)
    }

    while (mappedRules.nonEmpty) {
      merge(mappedRules.head._1)
    }

    result
  }

  lazy val first = computeFirst(grammar)
  lazy val follow = computeFollow(grammar, first)
  lazy val itemSets = computeItemSets(grammar)
  lazy val translationTable = computeTranslationTable(grammar)
  lazy val actionTable = computeActionTable()
}
