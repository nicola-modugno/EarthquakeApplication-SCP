package analysis

/**
 * Risultato completo dell'analisi con metriche.
 */
case class AnalysisResult(
                           maxPair: Option[LocationPair],
                           dates: Array[String],
                           totalEvents: Long,
                           uniqueEvents: Long,
                           coOccurrences: Long,
                           maxCount: Int
                         )
