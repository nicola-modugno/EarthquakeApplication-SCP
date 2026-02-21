package analysis

import org.apache.spark.rdd.RDD
import utils.Utils

object CoOccurrenceAnalysis {

  trait AnalysisApproach
  case object GroupByKeyApproach extends AnalysisApproach
  case object AggregateByKeyApproach extends AnalysisApproach
  case object ReduceByKeyApproach extends AnalysisApproach

  /**
   * Trova la coppia di località con il massimo numero di co-occorrenze.
   * Versione completa che restituisce anche metriche intermedie.
   *
   * @param events RDD di eventi sismici
   * @param numPartitions Numero di partizioni per ottimizzare il parallelismo
   * @param approach Approccio da utilizzare per l'analisi
   * @return AnalysisResult con coppia, date e metriche
   */
  def findMaxCoOccurrence(
                           events: RDD[EarthquakeEvent],
                           numPartitions: Int,
                           approach: AnalysisApproach = GroupByKeyApproach
                         ): AnalysisResult = {

    println(s"\nUsing approach: ${approachName(approach)}")
    println(s"Using $numPartitions partitions\n")

    // Step 1: Normalizzazione coordinate
    println("Step 1: Normalizzazione coordinate...")
    val normalizedEvents = events
      .map(e => (
        Utils.roundCoordinate(e.latitude),
        Utils.roundCoordinate(e.longitude),
        e.date
      ))
      .persist()

    val totalCount = normalizedEvents.count()
    println(s"Total events loaded: $totalCount")

    // Step 2: Deduplicazione eventi
    println("Step 2: Deduplicazione eventi...")
    val uniqueEvents = normalizedEvents
      .map { case (lat, lon, date) => (Location(lat, lon), date) }
      .distinct()

    val uniqueCount = uniqueEvents.count()
    println(s"Unique events after deduplication: $uniqueCount")
    normalizedEvents.unpersist()

    // Step 3: Repartitioning
    println(s"Step 3: Repartitioning data to $numPartitions partitions using repartition()...")
    val repartitionedEvents = uniqueEvents.repartition(numPartitions).persist()

    // Step 4: Raggruppamento location per data (comune - sempre groupByKey)
    println("Step 4: Raggruppamento per data (groupByKey)...")
    val locationsByDate = repartitionedEvents
      .map { case (location, date) => (date, location) }
      .groupByKey()

    // Step 5: Generazione coppie di co-occorrenze (comune)
    println("Step 5: Generazione coppie di località...")
    val coOccurrences = locationsByDate
      .flatMap { case (date, locations) =>
        val locList = locations.toList.sorted
        for {
          i <- locList.indices
          j <- (i + 1) until locList.length
        } yield (LocationPair(locList(i), locList(j)), date)
      }

    val coOccCount = coOccurrences.count()
    println(s"Total co-occurrences found: $coOccCount")

    // Step 6: Conteggio co-occorrenze per coppia (DIFFERENZIATO)
    println(s"Step 6: Conteggio co-occorrenze per coppia (${approachName(approach)})...")
    val pairCounts = approach match {
      case GroupByKeyApproach =>
        coOccurrences
          .map { case (pair, _) => (pair, 1) }
          .groupByKey()
          .mapValues(_.sum)

      case AggregateByKeyApproach =>
        coOccurrences
          .map { case (pair, _) => (pair, 1) }
          .aggregateByKey(0)(_ + _, _ + _)

      case ReduceByKeyApproach =>
        coOccurrences
          .map { case (pair, _) => (pair, 1) }
          .reduceByKey(_ + _)

      case _ =>
        println("WARNING: Unknown approach, falling back to GroupByKey")
        coOccurrences
          .map { case (pair, _) => (pair, 1) }
          .groupByKey()
          .mapValues(_.sum)
    }

    // Step 7: Ricerca coppia massima + estrazione date
    val result = extractResults(pairCounts, coOccurrences, totalCount, uniqueCount, coOccCount)

    // Cleanup
    repartitionedEvents.unpersist()

    result
  }

  /**
   * Estrae i risultati finali: trova la coppia con il massimo conteggio
   * e raccoglie le date di co-occorrenza.
   */
  private def extractResults(
                              pairCounts: RDD[(LocationPair, Int)],
                              coOccurrences: RDD[(LocationPair, String)],
                              totalCount: Long,
                              uniqueCount: Long,
                              coOccCount: Long
                            ): AnalysisResult = {

    println("Step 7: Ricerca coppia con massime co-occorrenze...")

    val maxPairOption = if (pairCounts.isEmpty()) {
      None
    } else {
      Some(pairCounts.reduce((a, b) => if (a._2 > b._2) a else b))
    }

    maxPairOption match {
      case Some((maxPair, count)) =>
        println(s"Max co-occurrence pair: $maxPair with $count occurrences")

        println("Step 8: Estrazione date per la coppia massima...")
        val dates = coOccurrences
          .filter { case (pair, _) => pair == maxPair }
          .map { case (_, date) => date }
          .distinct()
          .sortBy(identity)
          .collect()

        println(s"Found ${dates.length} unique dates")

        AnalysisResult(
          maxPair = Some(maxPair),
          dates = dates,
          totalEvents = totalCount,
          uniqueEvents = uniqueCount,
          coOccurrences = coOccCount,
          maxCount = count
        )

      case None =>
        println("No co-occurrences found")
        AnalysisResult(
          maxPair = None,
          dates = Array.empty,
          totalEvents = totalCount,
          uniqueEvents = uniqueCount,
          coOccurrences = coOccCount,
          maxCount = 0
        )
    }
  }

  /**
   * Parse string to approach.
   */
  def parseApproach(str: String): AnalysisApproach = {
    str.toLowerCase match {
      case "groupbykey" | "1" => GroupByKeyApproach
      case "aggregatebykey" | "2" => AggregateByKeyApproach
      case "reducebykey" | "3" => ReduceByKeyApproach
      case _ =>
        println(s"Unknown approach '$str', using default (groupByKey)")
        GroupByKeyApproach
    }
  }

  def approachName(approach: AnalysisApproach): String = approach match {
    case GroupByKeyApproach => "GroupByKey"
    case AggregateByKeyApproach => "AggregateByKey"
    case ReduceByKeyApproach => "ReduceByKey"
    case _ => "Unknown"
  }
}