package org.eknet.sitebag.lucene

import org.apache.lucene.search.{TermQuery, Query}
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer

trait QueryMaker extends (() => Query) with Serializable

object QueryMaker {

  def fromString(query: String, field: String, analyzer: Analyzer = new StandardAnalyzer()): QueryMaker = new QueryMaker {
    def apply() = {
      val parser = new QueryParser(field, analyzer)
      parser.setAllowLeadingWildcard(true)
      parser.setLowercaseExpandedTerms(true)
      parser.setAutoGeneratePhraseQueries(true)
      parser.setDefaultOperator(QueryParser.Operator.AND)
      parser.parse(query)
    }
  }

  def fromTerm[T](value: T)(implicit tc: TermCreator[T]) = new QueryMaker {
    private val term = tc.create(value)
    def apply() = new TermQuery(term)
  }
}
