import analysis.{CoOccurrenceAnalysis, ExecutionMetrics, MetricsCollector}
import extraction.DataExtractor
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import utils.Utils

object Main {

  def main(args: Array[String]): Unit = {
    configureLogging()

    if (args.length < 2) {
      println("Usage: Main <input-file> <output-file> [num-partitions] [approach] [num-workers]")
      println("  num-partitions: number of partitions for repartition() (default: 8)")
      println("  approach: groupbykey|aggregatebykey|reducebykey (default: groupbykey)")
      println("  num-workers: number of workers in cluster (default: 1)")
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
    val numWorkers = if (args.length > 4) args(4).toInt else 1

    val spark = SparkSession.builder()
      .appName("Earthquake Co-occurrence Analysis")
      .config("spark.ui.showConsoleProgress", "false")
      .getOrCreate()

    printHeader(inputFile, outputFile, numPartitions, approach, numWorkers)

    var exitCode = 0

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
        approach
      )
      val analysisTime = System.currentTimeMillis() - startAnalysis
      println(s"SUCCESS! - Analysis completed in ${analysisTime}ms (${analysisTime / 1000.0}s)")

      // Salvataggio risultati
      println("\n[3/4] Saving results...")
      result.maxPair match {
        case Some(pair) =>
          val output = Utils.formatOutput(pair, result.dates)
          Utils.saveOutput(spark, output, outputFile)

          println(s"Results saved to: $outputFile")
          printResultsSummary(pair, result)

        case None =>
          println("No co-occurrences found")
      }

      // Salvataggio metriche
      println("\n[4/4] Saving metrics...")
      val totalTime = loadTime + analysisTime

      val metrics = ExecutionMetrics(
        approach = CoOccurrenceAnalysis.approachName(approach),
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

      println(s"Metrics saved to: $outputFile/metrics")

      // Stampa performance summary
      printPerformanceSummary(loadTime, analysisTime, totalTime, approach)

    } catch {
      case e: Exception =>
        println(s"\n Error during execution: ${e.getMessage}")
        e.printStackTrace()
        exitCode = 1
    } finally {
      shutdownSparkGracefully(spark)
    }

    System.exit(exitCode)
  }

  private def configureLogging(): Unit = {
    Logger.getLogger("org.apache.spark.util.ShutdownHookManager").setLevel(Level.FATAL)
    Logger.getLogger("org.apache.spark.SparkEnv").setLevel(Level.FATAL)
    Logger.getLogger("org.apache.spark.util.Utils").setLevel(Level.ERROR)
    Logger.getLogger("org.apache.spark.SparkContext").setLevel(Level.INFO)
    Logger.getRootLogger.setLevel(Level.WARN)
  }

  private def shutdownSparkGracefully(spark: SparkSession): Unit = {
    try {
      println("\nShutting down Spark gracefully...")
      spark.stop()
      Thread.sleep(500)
      println("Spark shutdown completed")
    } catch {
      case _: Exception =>
    }
  }

  private def printHeader(
                           inputFile: String,
                           outputFile: String,
                           numPartitions: Int,
                           approach: analysis.CoOccurrenceAnalysis.AnalysisApproach,
                           numWorkers: Int
                         ): Unit = {
    println("EARTHQUAKE CO-OCCURRENCE ANALYSIS")
    println(s"Input file: $inputFile")
    println(s"Output file: $outputFile")
    println(s"Number of workers: $numWorkers")
    println(s"Number of partitions: $numPartitions")
    println(s"Analysis approach: ${CoOccurrenceAnalysis.approachName(approach)}")
    println(s"Partitioner: Hash (via repartition)")
  }

  private def printResultsSummary(
                                   pair: analysis.LocationPair,
                                   result: analysis.AnalysisResult
                                 ): Unit = {
    println("RESULTS SUMMARY")
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

  private def printPerformanceSummary(
                                       loadTime: Long,
                                       analysisTime: Long,
                                       totalTime: Long,
                                       approach: analysis.CoOccurrenceAnalysis.AnalysisApproach
                                     ): Unit = {
    println("PERFORMANCE SUMMARY")
    println(f"Load time:     ${loadTime}%8d ms (${loadTime / 1000.0}%6.2f s)")
    println(f"Analysis time: ${analysisTime}%8d ms (${analysisTime / 1000.0}%6.2f s)")
    println(f"Total time:    ${totalTime}%8d ms (${totalTime / 1000.0}%6.2f s)")
    println(f"Approach:      ${CoOccurrenceAnalysis.approachName(approach)}")
    println(f"Partitioner:   Hash (via repartition)")
    println("\nMetrics CSV file has been generated for report analysis.")
  }
}
