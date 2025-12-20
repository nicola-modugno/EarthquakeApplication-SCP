package analysis

/**
 * Genera file CSV con tutte le metriche necessarie per il report.
 */
case class ExecutionMetrics(
  approach: String,
  partitioner: String,
  numWorkers: Int,
  numPartitions: Int,
  totalEvents: Long,
  uniqueEvents: Long,
  coOccurrences: Long,
  loadTimeMs: Long,
  analysisTimeMs: Long,
  totalTimeMs: Long,
  maxCoOccurrenceCount: Int,
  timestamp: Long = System.currentTimeMillis()
) {
  
  /**
   * Converte le metriche in formato CSV.
   */
  def toCsvRow: String = {
    s"$approach,$partitioner,$numWorkers,$numPartitions,$totalEvents,$uniqueEvents,$coOccurrences," +
    s"$loadTimeMs,$analysisTimeMs,$totalTimeMs,$maxCoOccurrenceCount,$timestamp"
  }
  
  /**
   * Converte le metriche in formato leggibile.
   */
  def toReadableString: String = {
    s"""
       |Approach: $approach
       |Partitioner: $partitioner
       |Workers: $numWorkers
       |Partitions: $numPartitions
       |Total Events: $totalEvents
       |Unique Events: $uniqueEvents
       |Co-occurrences: $coOccurrences
       |Load Time: ${loadTimeMs}ms (${loadTimeMs/1000.0}s)
       |Analysis Time: ${analysisTimeMs}ms (${analysisTimeMs/1000.0}s)
       |Total Time: ${totalTimeMs}ms (${totalTimeMs/1000.0}s)
       |Max Co-occurrence Count: $maxCoOccurrenceCount
       |Throughput: ${totalEvents * 1000.0 / totalTimeMs} events/sec
       |""".stripMargin
  }
}




