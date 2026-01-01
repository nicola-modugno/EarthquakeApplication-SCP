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

    println(s"\n=== Using approach: ${approachName(approach)} ===")
    println(s"=== Using $numPartitions partitions ===\n")

    approach match {
      case GroupByKeyApproach =>
        findMaxCoOccurrenceGroupByKey(events, numPartitions)
      case AggregateByKeyApproach =>
        findMaxCoOccurrenceAggregateByKey(events, numPartitions)
      case ReduceByKeyApproach =>
        findMaxCoOccurrenceReduceByKey(events, numPartitions)
      case _ =>
        println(s"Warning: Unknown approach, falling back to GroupByKey")
        findMaxCoOccurrenceGroupByKey(events, numPartitions)
    }
  }

  /**
   * APPROCCIO 1: GroupByKey
   */
  private def findMaxCoOccurrenceGroupByKey(
                                             events: RDD[EarthquakeEvent],
                                             numPartitions: Int
                                           ): AnalysisResult = {

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

    println("Step 2: Deduplicazione eventi...")
    val uniqueEvents = normalizedEvents
      .map { case (lat, lon, date) => (Location(lat, lon), date) }
      .distinct()

    val uniqueCount = uniqueEvents.count()
    println(s"Unique events after deduplication: $uniqueCount")

    // ✅ Repartitioning esplicito come richiesto dal progetto
    println(s"Step 2.1: Repartitioning data to $numPartitions partitions using repartition()...")
    val repartitionedEvents = uniqueEvents.repartition(numPartitions).persist()

    println("Step 3: Raggruppamento per data (groupByKey)...")
    val eventsByDate = repartitionedEvents
      .map { case (location, date) => (date, location) }

    val locationsByDate = eventsByDate.groupByKey()

    println("Step 4: Generazione coppie di località...")
    val coOccurrences = locationsByDate
      .flatMap { case (date, locations) =>
        val locList = locations.toList.sorted
        for {
          i <- locList.indices
          j <- (i + 1) until locList.length
        } yield (LocationPair(locList(i), locList(j)), date)
      }
      .persist()

    val coOccCount = coOccurrences.count()
    println(s"Total co-occurrences found: $coOccCount")

    println("Step 5: Conteggio co-occorrenze per coppia...")
    val pairCounts = coOccurrences
      .map { case (pair, _) => (pair, 1) }
      .reduceByKey(_ + _)
      .persist()

    extractResults(pairCounts, coOccurrences, totalCount, uniqueCount, coOccCount)
  }

  /**
   * APPROCCIO 2: AggregateByKey
   */
  private def findMaxCoOccurrenceAggregateByKey(
                                                 events: RDD[EarthquakeEvent],
                                                 numPartitions: Int
                                               ): AnalysisResult = {

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

    println("Step 2: Deduplicazione eventi...")
    val uniqueEvents = normalizedEvents
      .map { case (lat, lon, date) => (Location(lat, lon), date) }
      .distinct()

    val uniqueCount = uniqueEvents.count()
    println(s"Unique events after deduplication: $uniqueCount")

    // ✅ Repartitioning esplicito
    println(s"Step 2.1: Repartitioning data to $numPartitions partitions using repartition()...")
    val repartitionedEvents = uniqueEvents.repartition(numPartitions).persist()

    println("Step 3: Aggregazione per data (aggregateByKey)...")
    val eventsByDate = repartitionedEvents
      .map { case (location, date) => (date, location) }

    val locationsByDate = eventsByDate
      .aggregateByKey(Set.empty[Location])(
        (set, loc) => set + loc,
        (set1, set2) => set1 ++ set2
      )

    println("Step 4: Generazione coppie di località...")
    val coOccurrences = locationsByDate
      .flatMap { case (date, locations) =>
        val locList = locations.toList.sorted
        for {
          i <- locList.indices
          j <- (i + 1) until locList.length
        } yield (LocationPair(locList(i), locList(j)), date)
      }
      .persist()

    val coOccCount = coOccurrences.count()
    println(s"Total co-occurrences found: $coOccCount")

    println("Step 5: Conteggio co-occorrenze per coppia...")
    val pairCounts = coOccurrences
      .map { case (pair, _) => (pair, 1) }
      .reduceByKey(_ + _)
      .persist()

    extractResults(pairCounts, coOccurrences, totalCount, uniqueCount, coOccCount)
  }

  /**
   * APPROCCIO 3: ReduceByKey
   */
  private def findMaxCoOccurrenceReduceByKey(
                                              events: RDD[EarthquakeEvent],
                                              numPartitions: Int
                                            ): AnalysisResult = {

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

    println("Step 2: Deduplicazione eventi...")
    val uniqueEvents = normalizedEvents
      .map { case (lat, lon, date) =>
        ((Location(lat, lon), date), 1)
      }
      .reduceByKey(_ + _)
      .map { case ((location, date), _) => (location, date) }

    val uniqueCount = uniqueEvents.count()
    println(s"Unique events after deduplication: $uniqueCount")

    // ✅ Repartitioning esplicito
    println(s"Step 2.1: Repartitioning data to $numPartitions partitions using repartition()...")
    val repartitionedEvents = uniqueEvents.repartition(numPartitions).persist()

    println("Step 3: Aggregazione località per data...")
    val eventsByDate = repartitionedEvents
      .map { case (location, date) => (date, location) }

    val locationsByDate = eventsByDate
      .aggregateByKey(Set.empty[Location])(
        (set, loc) => set + loc,
        (set1, set2) => set1 ++ set2
      )

    println("Step 4: Generazione e conteggio coppie...")
    val coOccurrences = locationsByDate
      .flatMap { case (date, locations) =>
        val locList = locations.toList.sorted
        for {
          i <- locList.indices
          j <- (i + 1) until locList.length
        } yield (LocationPair(locList(i), locList(j)), date)
      }
      .persist()

    val coOccCount = coOccurrences.count()
    println(s"Total co-occurrences found: $coOccCount")

    val pairCounts = coOccurrences
      .map { case (pair, _) => (pair, 1) }
      .reduceByKey(_ + _)
      .persist()

    extractResults(pairCounts, coOccurrences, totalCount, uniqueCount, coOccCount)
  }

  /**
   * Estrae i risultati finali da pairCounts e coOccurrences.
   */
  private def extractResults(
                              pairCounts: RDD[(LocationPair, Int)],
                              coOccurrences: RDD[(LocationPair, String)],
                              totalCount: Long,
                              uniqueCount: Long,
                              coOccCount: Long
                            ): AnalysisResult = {

    println("Step 6: Ricerca coppia con massime co-occorrenze...")

    val maxPairOption = if (pairCounts.isEmpty()) {
      None
    } else {
      Some(pairCounts.reduce((a, b) => if (a._2 > b._2) a else b))
    }

    maxPairOption match {
      case Some((maxPair, count)) =>
        println(s"Max co-occurrence pair: $maxPair with $count occurrences")

        println("Step 7: Estrazione date per la coppia massima...")
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
