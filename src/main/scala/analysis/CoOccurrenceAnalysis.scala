package analysis

import org.apache.spark.HashPartitioner
import org.apache.spark.rdd.RDD
import utils.Utils

/**
 * Oggetto per l'analisi delle co-occorrenze di eventi sismici.
 * Implementa TRE APPROCCI DIVERSI per il confronto delle prestazioni:
 * 
 * 1. APPROCCIO 1 (groupByKey): Usa groupByKey per raggruppare eventi per data.
 *    - Pro: Implementazione semplice e diretta
 *    - Contro: Alto memory footprint, molto shuffling, meno efficiente
 *    
 * 2. APPROCCIO 2 (aggregateByKey): Usa aggregateByKey con Set per aggregazione incrementale.
 *    - Pro: Riduce memory footprint, combina localmente prima dello shuffling
 *    - Contro: Leggermente più complesso
 *    
 * 3. APPROCCIO 3 (reduceByKey): Usa multiple reduceByKey per minimizzare shuffling.
 *    - Pro: Bilanciato tra efficienza e semplicità, ottimo per conteggi
 *    - Contro: Richiede più passaggi sui dati
 */
object CoOccurrenceAnalysis {
  
  /**
   * Approccio selezionabile per l'analisi.
   */
  sealed trait AnalysisApproach
  case object GroupByKeyApproach extends AnalysisApproach
  case object AggregateByKeyApproach extends AnalysisApproach
  case object ReduceByKeyApproach extends AnalysisApproach
  
  /**
   * Trova la coppia di località con il massimo numero di co-occorrenze.
   * Delega all'implementazione specifica in base all'approccio scelto.
   * 
   * @param events RDD di eventi sismici
   * @param numPartitions Numero di partizioni per ottimizzare il parallelismo
   * @param approach Approccio da utilizzare per l'analisi
   * @return Option contenente la coppia con più co-occorrenze e le relative date
   */
  def findMaxCoOccurrence(
    events: RDD[EarthquakeEvent],
    numPartitions: Int,
    approach: AnalysisApproach = GroupByKeyApproach
  ): Option[(LocationPair, Array[String])] = {
    
    println(s"\n=== Using approach: ${approach.getClass.getSimpleName} ===\n")
    
    approach match {
      case GroupByKeyApproach => findMaxCoOccurrenceGroupByKey(events, numPartitions)
      case AggregateByKeyApproach => findMaxCoOccurrenceAggregateByKey(events, numPartitions)
      case ReduceByKeyApproach => findMaxCoOccurrenceReduceByKey(events, numPartitions)
    }
  }
  
  /**
   * APPROCCIO 1: Implementazione usando groupByKey.
   * Questo approccio raggruppa tutti gli eventi per data usando groupByKey,
   * che sposta TUTTI i dati attraverso la rete prima di raggrupparli.
   * 
   * Caratteristiche:
   * - Shuffling massiccio: tutti gli eventi vengono trasmessi
   * - Memory intensive: tutti i valori per chiave devono stare in memoria
   * - Più lento ma concettualmente semplice
   * 
   * @param events RDD di eventi sismici
   * @param numPartitions Numero di partizioni
   * @return Option con risultato dell'analisi
   */
  private def findMaxCoOccurrenceGroupByKey(
    events: RDD[EarthquakeEvent],
    numPartitions: Int
  ): Option[(LocationPair, Array[String])] = {
    
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
        (Location(lat, lon), date) 
      }
      .distinct()
      .partitionBy(new HashPartitioner(numPartitions))
      .persist()
    
    val uniqueCount = uniqueEvents.count()
    println(s"Unique events after deduplication: $uniqueCount")
    
    println("Step 3: Raggruppamento per data (groupByKey)...")
    // Inverti chiave-valore per raggruppare per data
    val eventsByDate = uniqueEvents
      .map { case (location, date) => (date, location) }
      .partitionBy(new HashPartitioner(numPartitions))
    
    // groupByKey: TUTTO il data viene trasmesso
    val locationsByDate = eventsByDate.groupByKey()
    
    println("Step 4: Generazione coppie di località...")
    val coOccurrences = locationsByDate
      .flatMap { case (date, locations) =>
        val locList = locations.toList.sorted
        // Genera coppie ordinate
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
    
    findMaxPairAndDates(pairCounts, coOccurrences)
  }
  
  /**
   * APPROCCIO 2: Implementazione usando aggregateByKey.
   * Questo approccio usa aggregateByKey per combinare i valori localmente
   * prima di trasmetterli attraverso la rete, riducendo significativamente
   * la quantità di dati trasmessi.
   * 
   * Caratteristiche:
   * - Shuffling ridotto: solo Set aggregati vengono trasmessi
   * - Memory efficient: aggregazione incrementale
   * - Più veloce e scalabile
   * 
   * @param events RDD di eventi sismici
   * @param numPartitions Numero di partizioni
   * @return Option con risultato dell'analisi
   */
  private def findMaxCoOccurrenceAggregateByKey(
    events: RDD[EarthquakeEvent],
    numPartitions: Int
  ): Option[(LocationPair, Array[String])] = {
    
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
        (Location(lat, lon), date) 
      }
      .distinct()
      .partitionBy(new HashPartitioner(numPartitions))
      .persist()
    
    val uniqueCount = uniqueEvents.count()
    println(s"Unique events after deduplication: $uniqueCount")
    
    println("Step 3: Aggregazione per data (aggregateByKey)...")
    val eventsByDate = uniqueEvents
      .map { case (location, date) => (date, location) }
      .partitionBy(new HashPartitioner(numPartitions))
    
    // aggregateByKey: combina localmente poi trasmette solo i Set
    val locationsByDate = eventsByDate
      .aggregateByKey(Set.empty[Location])(
        (set, loc) => set + loc,      // seqOp: aggiungi location al set locale
        (set1, set2) => set1 ++ set2   // combOp: unisci i set (riduce shuffling)
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
    
    findMaxPairAndDates(pairCounts, coOccurrences)
  }
  
  /**
   * APPROCCIO 3: Implementazione usando principalmente reduceByKey.
   * Questo approccio minimizza lo shuffling usando reduceByKey dove possibile,
   * che combina valori localmente prima della trasmissione.
   * 
   * Caratteristiche:
   * - Shuffling ottimizzato: solo conteggi parziali vengono trasmessi
   * - Bilanciato: combina efficienza e semplicità
   * - Ottimo per operazioni di conteggio e somma
   * 
   * @param events RDD di eventi sismici
   * @param numPartitions Numero di partizioni
   * @return Option con risultato dell'analisi
   */
  private def findMaxCoOccurrenceReduceByKey(
    events: RDD[EarthquakeEvent],
    numPartitions: Int
  ): Option[(LocationPair, Array[String])] = {
    
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
      .reduceByKey(_ + _)  // Usa reduceByKey per deduplicazione efficiente
      .map { case ((location, date), _) => (location, date) }
      .partitionBy(new HashPartitioner(numPartitions))
      .persist()
    
    val uniqueCount = uniqueEvents.count()
    println(s"Unique events after deduplication: $uniqueCount")
    
    println("Step 3: Creazione chiavi composte data-coppia...")
    // Usa reduceByKey per contare occorrenze per (data, location)
    val eventsByDate = uniqueEvents
      .map { case (location, date) => ((date, location), 1) }
      .reduceByKey(_ + _)
      .map { case ((date, location), _) => (date, location) }
      .partitionBy(new HashPartitioner(numPartitions))
    
    println("Step 4: Aggregazione località per data...")
    val locationsByDate = eventsByDate
      .aggregateByKey(Set.empty[Location])(
        (set, loc) => set + loc,
        (set1, set2) => set1 ++ set2
      )
    
    println("Step 5: Generazione e conteggio coppie...")
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
    
    // Usa reduceByKey per conteggio efficiente
    val pairCounts = coOccurrences
      .map { case (pair, _) => (pair, 1) }
      .reduceByKey(_ + _)  // Combina conteggi localmente
      .persist()
    
    findMaxPairAndDates(pairCounts, coOccurrences)
  }
  
  /**
   * Trova la coppia con il massimo numero di co-occorrenze ed estrae le date.
   * Funzione di utilità comune a tutti gli approcci.
   * 
   * @param pairCounts RDD di (LocationPair, count)
   * @param coOccurrences RDD di (LocationPair, date)
   * @return Option con coppia massima e array di date
   */
  private def findMaxPairAndDates(
    pairCounts: RDD[(LocationPair, Int)],
    coOccurrences: RDD[(LocationPair, String)]
  ): Option[(LocationPair, Array[String])] = {
    
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
        Some((maxPair, dates))
        
      case None =>
        println("No co-occurrences found")
        None
    }
  }
  
  /**
   * Trova le top-K coppie con più co-occorrenze.
   * Utile per debugging e analisi comparativa.
   * 
   * @param events RDD di eventi sismici
   * @param numPartitions Numero di partizioni
   * @param k Numero di top coppie da restituire
   * @param approach Approccio da utilizzare
   * @return Array di (LocationPair, count, Array[date])
   */
  def findTopKCoOccurrences(
    events: RDD[EarthquakeEvent],
    numPartitions: Int,
    k: Int,
    approach: AnalysisApproach = AggregateByKeyApproach
  ): Array[(LocationPair, Int, Array[String])] = {
    
    println(s"\n=== Finding top-$k co-occurrences using ${approach.getClass.getSimpleName} ===\n")
    
    val normalizedEvents = events
      .map(e => (
        Utils.roundCoordinate(e.latitude),
        Utils.roundCoordinate(e.longitude),
        e.date
      ))
      .persist()
    
    val uniqueEvents = normalizedEvents
      .map { case (lat, lon, date) => (Location(lat, lon), date) }
      .distinct()
      .partitionBy(new HashPartitioner(numPartitions))
      .persist()
    
    val eventsByDate = uniqueEvents
      .map { case (location, date) => (date, location) }
      .partitionBy(new HashPartitioner(numPartitions))
    
    val locationsByDate = eventsByDate
      .aggregateByKey(Set.empty[Location])(
        (set, loc) => set + loc,
        (set1, set2) => set1 ++ set2
      )
    
    val coOccurrences = locationsByDate
      .flatMap { case (date, locations) =>
        val locList = locations.toList.sorted
        for {
          i <- locList.indices
          j <- (i + 1) until locList.length
        } yield (LocationPair(locList(i), locList(j)), date)
      }
      .persist()
    
    val pairCounts = coOccurrences
      .map { case (pair, _) => (pair, 1) }
      .reduceByKey(_ + _)
      .persist()
    
    // Prendi le top-K coppie
    val topKPairs = pairCounts
      .takeOrdered(k)(Ordering.by[(LocationPair, Int), Int](_._2).reverse)
    
    println(s"Top-$k pairs found, extracting dates...")
    
    // Per ogni coppia top-K, estrai le date
    topKPairs.map { case (pair, count) =>
      val dates = coOccurrences
        .filter { case (p, _) => p == pair }
        .map { case (_, date) => date }
        .distinct()
        .sortBy(identity)
        .collect()
      
      (pair, count, dates)
    }
  }
  
  /**
   * Converte una stringa in un AnalysisApproach.
   * Utile per parsing da linea di comando.
   * 
   * @param str Stringa rappresentante l'approccio
   * @return AnalysisApproach corrispondente, default a GroupByKeyApproach
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
}
