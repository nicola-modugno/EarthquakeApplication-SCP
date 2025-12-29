import analysis.CoOccurrenceAnalysis
import extraction.DataExtractor
import analysis.ExecutionMetrics
import analysis.MetricsCollector
import org.apache.spark.sql.SparkSession
import utils.Utils

/**
 * Main per l'analisi delle co-occorrenze di eventi sismici.
 *
 * Genera automaticamente metriche in formato CSV per facilitare
 * l'analisi delle performance e la creazione del report.
 *
 * Usage: Main <input-file> <output-file> [num-partitions] [approach] [partitioner] [num-workers]
 *
 * Parametri:
 *   input-file: Path al CSV di input
 *   output-file: Path directory output
 *   num-partitions: Numero di partizioni (default: 8)
 *   approach: 1|groupbykey, 2|aggregatebykey, 3|reducebykey (default: 1)
 *   partitioner: hash|range (default: hash)
 *   num-workers: Numero di worker nel cluster (default: 1, per metriche)
 */
object Main {

  def main(args: Array[String]): Unit = {
    // Validazione argomenti
    if (args.length < 2) {
      println("Usage: Main <input-file> <output-file> [num-partitions] [approach] [partitioner] [num-workers]")
      println("  approach: 1|groupbykey (default), 2|aggregatebykey, 3|reducebykey")
      println("  partitioner: hash (default), range")
      println("  num-workers: number of workers in cluster (for metrics)")
      System.exit(1)
    }

    val inputFile = args(0)
    val outputFile = args(1)
    val numPartitions = if (args.length > 2) args(2).toInt else 8
    val approach = if (args.length > 3) {
      CoOccurrenceAnalysis.parseApproach(args(3))
    } else {
      analysis.CoOccurrenceAnalysis.GroupByKeyApproach
    }
    val partitioner = if (args.length > 4) {
      CoOccurrenceAnalysis.parsePartitioner(args(4))
    } else {
      analysis.HashPartitionerType
    }
    val numWorkers = if (args.length > 5) args(5).toInt else 1

    // Crea nome output con partitioner
    /*val partitionerSuffix = partitioner match {
      case analysis.HashPartitionerType => "hash"
      case analysis.RangePartitionerType => "range"
      case _ => "hash"
    }
    //val outputFile = s"${baseOutputFile}-${partitionerSuffix}"*/
    val outputFile = baseOutputFile

    // Spark/YARN gestirà automaticamente la configurazione di rete
    val spark = SparkSession.builder()
      .appName("Earthquake Co-occurrence Analysis")
      .config("spark.ui.showConsoleProgress", "false")
      .getOrCreate()

    printHeader(inputFile, outputFile, numPartitions, approach, partitioner, numWorkers)

    try {
      // Caricamento dati
      println("\n[1/4] Loading data...")
      val startLoad = System.currentTimeMillis()
      val rawData = DataExtractor.loadData(spark, inputFile)
      val loadTime = System.currentTimeMillis() - startLoad
      println(s"✓ Data loaded in ${loadTime}ms (${loadTime / 1000.0}s)")

      // Analisi co-occorrenze
      println("\n[2/4] Analyzing co-occurrences...")
      val startAnalysis = System.currentTimeMillis()
      val result = CoOccurrenceAnalysis.findMaxCoOccurrence(
        rawData,
        numPartitions,
        approach,
        partitioner
      )
      val analysisTime = System.currentTimeMillis() - startAnalysis
      println(s"✓ Analysis completed in ${analysisTime}ms (${analysisTime / 1000.0}s)")

      // Salvataggio risultati
      println("\n[3/4] Saving results...")
      result.maxPair match {
        case Some(pair) =>
          val output = Utils.formatOutput(pair, result.dates)
          Utils.saveOutput(spark, output, outputFile)

          println(s"✓ Results saved to: $outputFile")
          printResultsSummary(pair, result)

        case None =>
          println("⚠ No co-occurrences found")
      }

      // Salvataggio metriche
      println("\n[4/4] Saving metrics...")
      val totalTime = loadTime + analysisTime

      val metrics = ExecutionMetrics(
        approach = CoOccurrenceAnalysis.approachName(approach),
        partitioner = CoOccurrenceAnalysis.partitionerName(partitioner),
        numWorkers = numWorkers,
        numPartitions = numPartitions,
        totalEvents = result.totalEvents,
        uniqueEvents = result.uniqueEvents,
        coOccurrences = result.coOccurrences,
        loadTimeMs = loadTime,
        analysisTimeMs = analysisTime,
        totalTimeMs = totalTime,
        maxCoOccurrenceCount = result.maxCount
      )

      // Salva metriche in formato CSV
      MetricsCollector.saveMetricsToCsv(spark, metrics, outputFile)

      // Salva anche in formato leggibile
      MetricsCollector.saveMetricsReadable(spark, metrics, outputFile)

      println(s"✓ Metrics saved to: $outputFile/metrics")

      // Stampa performance summary
      printPerformanceSummary(loadTime, analysisTime, totalTime, approach, partitioner)

    } catch {
      case e: Exception =>
        println(s"\n✗ Error during execution: ${e.getMessage}")
        e.printStackTrace()
        System.exit(1)
    } finally {
      spark.stop()
    }
  }

  /**
   * Stampa l'header con le configurazioni.
   */
  private def printHeader(
                           inputFile: String,
                           outputFile: String,
                           numPartitions: Int,
                           approach: analysis.CoOccurrenceAnalysis.AnalysisApproach,
                           partitioner: analysis.PartitionerType,
                           numWorkers: Int
                         ): Unit = {
    println("=" * 70)
    println("EARTHQUAKE CO-OCCURRENCE ANALYSIS")
    println("=" * 70)
    println(s"Input file: $inputFile")
    println(s"Output file: $outputFile")
    println(s"Number of workers: $numWorkers")
    println(s"Number of partitions: $numPartitions")
    println(s"Analysis approach: ${CoOccurrenceAnalysis.approachName(approach)}")
    println(s"Partitioner: ${CoOccurrenceAnalysis.partitionerName(partitioner)}")
    println("=" * 70)
  }

  /**
   * Stampa il sommario dei risultati.
   */
  private def printResultsSummary(
                                   pair: analysis.LocationPair,
                                   result: analysis.AnalysisResult
                                 ): Unit = {
    println("\n" + "=" * 70)
    println("RESULTS SUMMARY")
    println("=" * 70)
    println(s"Max co-occurrence pair:")
    println(s"  Location 1: ${pair.first}")
    println(s"  Location 2: ${pair.second}")
    println(s"Number of co-occurrences: ${result.dates.length}")
    println(s"Total events processed: ${result.totalEvents}")
    println(s"Unique events (after dedup): ${result.uniqueEvents}")
    println(s"Total co-occurrences found: ${result.coOccurrences}")

    if (result.dates.nonEmpty) {
      println(s"Date range: ${result.dates.head} to ${result.dates.last}")

      if (result.dates.length <= 10) {
        println("\nAll co-occurrence dates:")
        result.dates.foreach(date => println(s"  - $date"))
      } else {
        println(s"\nFirst 5 dates:")
        result.dates.take(5).foreach(date => println(s"  - $date"))
        println(s"  ... (${result.dates.length - 5} more dates)")
      }
    }
  }

  /**
   * Stampa il sommario delle performance.
   */
  private def printPerformanceSummary(
                                       loadTime: Long,
                                       analysisTime: Long,
                                       totalTime: Long,
                                       approach: analysis.CoOccurrenceAnalysis.AnalysisApproach,
                                       partitioner: analysis.PartitionerType
                                     ): Unit = {
    println("\n" + "=" * 70)
    println("PERFORMANCE SUMMARY")
    println("=" * 70)
    println(f"Load time:     ${loadTime}%8d ms (${loadTime / 1000.0}%6.2f s)")
    println(f"Analysis time: ${analysisTime}%8d ms (${analysisTime / 1000.0}%6.2f s)")
    println(f"Total time:    ${totalTime}%8d ms (${totalTime / 1000.0}%6.2f s)")
    println(f"Approach:      ${CoOccurrenceAnalysis.approachName(approach)}")
    println(f"Partitioner:   ${CoOccurrenceAnalysis.partitionerName(partitioner)}")
    println("=" * 70)
    println("\nMetrics CSV file has been generated for report analysis.")
  }
}