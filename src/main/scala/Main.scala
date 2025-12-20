import analysis.CoOccurrenceAnalysis
import extraction.DataExtractor
import org.apache.spark.sql.SparkSession
import utils.Utils

/**
 * Classe principale per l'analisi delle co-occorrenze di eventi sismici.
 * 
 * Il programma:
 * 1. Carica eventi sismici da un file CSV
 * 2. Normalizza le coordinate arrotondando alla prima cifra decimale
 * 3. Rimuove duplicati (stessa località, stessa data)
 * 4. Trova la coppia di località che co-occorre più frequentemente
 * 5. Estrae le date in cui avvengono tali co-occorrenze
 * 6. Salva i risultati in formato testuale
 * 
 * Supporta TRE APPROCCI DIVERSI per confrontare prestazioni:
 * - Approccio 1 (groupByKey): Semplice ma meno efficiente
 * - Approccio 2 (aggregateByKey): Ottimizzato, riduce shuffling
 * - Approccio 3 (reduceByKey): Bilanciato tra efficienza e semplicità
 * 
 * Usage: Main <input-file> <output-file> [num-partitions] [approach]
 *   approach: 1 o groupbykey | 2 o aggregatebykey | 3 o reducebykey
 */
object Main {
  
  /**
   * Entry point del programma.
   * 
   * @param args Argomenti da linea di comando:
   *             args(0): Path file CSV di input (locale o gs://)
   *             args(1): Path directory output (locale o gs://)
   *             args(2): [Opzionale] Numero di partizioni (default: 8)
   *             args(3): [Opzionale] Approccio: 1|2|3 o groupbykey|aggregatebykey|reducebykey (default: 1)
   */
  def main(args: Array[String]): Unit = {
    // Validazione argomenti
    if (args.length < 2) {
      println("Usage: Main <input-file> <output-file> [num-partitions] [approach]")
      println("  approach: 1|groupbykey (default), 2|aggregatebykey, 3|reducebykey")
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

    // Inizializzazione Spark
    val spark = SparkSession.builder
      .appName("Earthquake Co-occurrence Analysis")
      .getOrCreate()

    println("=" * 70)
    println("EARTHQUAKE CO-OCCURRENCE ANALYSIS")
    println("=" * 70)
    println(s"Input file: $inputFile")
    println(s"Output file: $outputFile")
    println(s"Number of partitions: $numPartitions")
    println(s"Analysis approach: ${approach.getClass.getSimpleName.replace("$", "")}")
    println("=" * 70)

    try {
      // Caricamento dati
      println("\n[1/3] Loading data...")
      val startLoad = System.currentTimeMillis()
      val rawData = DataExtractor.loadData(spark, inputFile)
      val loadTime = System.currentTimeMillis() - startLoad
      println(s"✓ Data loaded in ${loadTime}ms (${loadTime / 1000.0}s)")

      // Analisi co-occorrenze
      println("\n[2/3] Analyzing co-occurrences...")
      val startAnalysis = System.currentTimeMillis()
      val result = CoOccurrenceAnalysis.findMaxCoOccurrence(
        rawData, 
        numPartitions,
        approach
      )
      val analysisTime = System.currentTimeMillis() - startAnalysis
      println(s"✓ Analysis completed in ${analysisTime}ms (${analysisTime / 1000.0}s)")

      // Salvataggio risultati
      println("\n[3/3] Saving results...")
      result match {
        case Some((pair, dates)) =>
          val output = Utils.formatOutput(pair, dates)
          Utils.saveOutput(spark, output, outputFile)
          
          println(s"✓ Results saved to: $outputFile")
          println("\n" + "=" * 70)
          println("RESULTS SUMMARY")
          println("=" * 70)
          println(s"Max co-occurrence pair:")
          println(s"  Location 1: ${pair.first}")
          println(s"  Location 2: ${pair.second}")
          println(s"Number of co-occurrences: ${dates.length}")
          println(s"Date range: ${dates.head} to ${dates.last}")
          
          if (dates.length <= 10) {
            println("\nAll co-occurrence dates:")
            dates.foreach(date => println(s"  - $date"))
          } else {
            println(s"\nFirst 5 dates:")
            dates.take(5).foreach(date => println(s"  - $date"))
            println(s"  ... (${dates.length - 5} more dates)")
          }
          
        case None =>
          println("⚠ No co-occurrences found in the dataset")
      }

      // Performance summary
      val totalTime = loadTime + analysisTime
      println("\n" + "=" * 70)
      println("PERFORMANCE SUMMARY")
      println("=" * 70)
      println(f"Load time:     ${loadTime}%8d ms (${loadTime / 1000.0}%6.2f s)")
      println(f"Analysis time: ${analysisTime}%8d ms (${analysisTime / 1000.0}%6.2f s)")
      println(f"Total time:    ${totalTime}%8d ms (${totalTime / 1000.0}%6.2f s)")
      println(f"Approach:      ${approach.getClass.getSimpleName.replace("$", "")}")
      println("=" * 70)

    } catch {
      case e: Exception =>
        println(s"\n✗ Error during execution: ${e.getMessage}")
        e.printStackTrace()
        System.exit(1)
    } finally {
      spark.stop()
    }
  }
}
