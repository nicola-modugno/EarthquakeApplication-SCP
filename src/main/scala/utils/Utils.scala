package utils

import analysis.{Location, LocationPair}
import org.apache.spark.sql.SparkSession

import scala.math.BigDecimal.RoundingMode

/**
 * Oggetto contenente funzioni di utilità per il progetto.
 * Include funzioni per arrotondamento, formattazione, I/O e validazione.
 */
object Utils {
  
  /**
   * Arrotonda una coordinata alla prima cifra decimale con arrotondamento HALF_UP.
   * Es: 112.234 -> 112.2, 112.251 -> 112.3
   * 
   * @param coord Coordinata da arrotondare
   * @return Coordinata arrotondata alla prima cifra decimale
   */
  def roundCoordinate(coord: Double): Double = {
    BigDecimal(coord).setScale(1, RoundingMode.HALF_UP).toDouble
  }
  
  /**
   * Formatta l'output secondo le specifiche del progetto.
   * Prima riga: coppia di località nel formato ((lat1, lon1), (lat2, lon2))
   * Righe successive: date in ordine crescente, una per riga
   * 
   * @param pair Coppia di località che co-occorrono
   * @param dates Array di date ordinate in cui avvengono le co-occorrenze
   * @return Stringa formattata pronta per l'output
   */
  def formatOutput(pair: LocationPair, dates: Array[String]): String = {
    val pairStr = s"(${pair.first}, ${pair.second})"
    val datesStr = dates.mkString("\n")
    s"$pairStr\n$datesStr"
  }
  
  /**
   * Versione alternativa di formatOutput che accetta tuple per compatibilità.
   * 
   * @param pair Tupla di tuple ((lat1, lon1), (lat2, lon2))
   * @param dates Array di date ordinate
   * @return Stringa formattata pronta per l'output
   */
  def formatOutputFromTuple(
    pair: ((Double, Double), (Double, Double)),
    dates: Array[String]
  ): String = {
    val ((lat1, lon1), (lat2, lon2)) = pair
    val pairStr = s"(($lat1, $lon1), ($lat2, $lon2))"
    val datesStr = dates.mkString("\n")
    s"$pairStr\n$datesStr"
  }
  
  /**
   * Salva l'output su file usando Spark RDD.
   * Usa coalesce(1) per generare un singolo file di output.
   * 
   * @param spark Sessione Spark attiva
   * @param content Contenuto da salvare
   * @param outputPath Path di output (locale o su GCS)
   */
  def saveOutput(spark: SparkSession, content: String, outputPath: String): Unit = {
    val sc = spark.sparkContext
    val outputRDD = sc.parallelize(Seq(content))
    
    // Coalesce(1) per avere un singolo file di output
    // Questo è accettabile perché l'output finale è piccolo
    outputRDD.coalesce(1).saveAsTextFile(outputPath)
  }
  
  /**
   * Stampa statistiche dettagliate su un RDD per debugging e analisi.
   * 
   * @param rdd RDD di cui stampare le statistiche
   * @param name Nome descrittivo dell'RDD
   * @tparam T Tipo degli elementi nell'RDD
   */
  def printRDDStats[T](rdd: org.apache.spark.rdd.RDD[T], name: String): Unit = {
    println(s"=== Stats for $name ===")
    println(s"Count: ${rdd.count()}")
    println(s"Partitions: ${rdd.getNumPartitions}")
    println(s"First 5 elements:")
    rdd.take(5).foreach(println)
    println("=" * 30)
  }
  
  /**
   * Calcola e stampa metriche di performance dell'esecuzione.
   * 
   * @param totalTime Tempo totale di esecuzione in millisecondi
   * @param numWorkers Numero di worker nodes nel cluster
   * @param inputSize Numero totale di eventi processati
   */
  def calculateMetrics(
    totalTime: Long,
    numWorkers: Int,
    inputSize: Long
  ): Unit = {
    println("\n=== Performance Metrics ===")
    println(s"Total execution time: ${totalTime}ms (${totalTime / 1000.0}s)")
    println(s"Number of workers: $numWorkers")
    println(s"Input size: $inputSize events")
    println(s"Throughput: ${inputSize * 1000.0 / totalTime} events/sec")
    println("=" * 30)
  }
  
  /**
   * Formatta una Location come stringa.
   * 
   * @param location Location da formattare
   * @return Stringa nel formato (lat, lon)
   */
  def formatLocation(location: Location): String = {
    s"(${location.latitude}, ${location.longitude})"
  }
  
  /**
   * Valida i parametri di input del programma.
   * 
   * @param args Array di argomenti da linea di comando
   * @return Either con errore (Left) o tuple validata (Right)
   */
  def validateInputParameters(args: Array[String]): Either[String, (String, String, Int)] = {
    if (args.length < 2) {
      Left("Insufficient arguments. Usage: Main <input-file> <output-file> [num-partitions]")
    } else {
      val inputFile = args(0)
      val outputFile = args(1)
      val numPartitions = if (args.length > 2) {
        try {
          val n = args(2).toInt
          if (n <= 0) {
            return Left(s"Number of partitions must be positive, got: $n")
          }
          n
        } catch {
          case _: NumberFormatException =>
            return Left(s"Invalid number of partitions: ${args(2)}")
        }
      } else {
        8 // default: 2 workers × 4 cores
      }
      
      Right((inputFile, outputFile, numPartitions))
    }
  }
  
  /**
   * Genera un report dettagliato dei risultati dell'analisi.
   * 
   * @param pair Coppia di località con massime co-occorrenze
   * @param dates Array di date delle co-occorrenze
   * @param totalEvents Numero totale di eventi caricati
   * @param uniqueEvents Numero di eventi unici dopo deduplicazione
   * @param executionTime Tempo di esecuzione totale in millisecondi
   * @return Stringa contenente il report formattato
   */
  def generateReport(
    pair: LocationPair,
    dates: Array[String],
    totalEvents: Long,
    uniqueEvents: Long,
    executionTime: Long
  ): String = {
    val report = new StringBuilder
    
    report.append("=" * 60 + "\n")
    report.append("EARTHQUAKE CO-OCCURRENCE ANALYSIS REPORT\n")
    report.append("=" * 60 + "\n\n")
    
    report.append("INPUT STATISTICS:\n")
    report.append(s"  Total events processed: $totalEvents\n")
    report.append(s"  Unique events (after deduplication): $uniqueEvents\n")
    report.append(s"  Execution time: ${executionTime}ms (${executionTime / 1000.0}s)\n\n")
    
    report.append("RESULTS:\n")
    report.append(s"  Maximum co-occurrence pair:\n")
    report.append(s"    Location 1: ${pair.first}\n")
    report.append(s"    Location 2: ${pair.second}\n")
    report.append(s"  Number of co-occurrences: ${dates.length}\n\n")
    
    report.append("CO-OCCURRENCE DATES (in ascending order):\n")
    dates.zipWithIndex.foreach { case (date, idx) =>
      report.append(f"  ${idx + 1}%3d. $date\n")
    }
    
    report.append("\n" + "=" * 60 + "\n")
    
    report.toString()
  }
}
