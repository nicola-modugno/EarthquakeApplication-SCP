package extraction

import analysis.EarthquakeEvent
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

/**
 * Oggetto per l'estrazione e il parsing dei dati sismici da file CSV.
 * Fornisce metodi alternativi per il caricamento ottimizzato in base alle dimensioni del dataset.
 */
object DataExtractor {

  /**
   * Carica i dati dal file CSV e li converte in RDD di EarthquakeEvent.
   * Utilizza spark.read per parsing efficiente del CSV come da specifiche.
   *
   * @param spark Sessione Spark attiva
   * @param filename Path al file CSV (locale o su GCS)
   * @return RDD di EarthquakeEvent con eventi validi
   */
  def loadData(spark: SparkSession, filename: String): RDD[EarthquakeEvent] = {
    import spark.implicits._

    println(s"Loading data from: $filename")

    // Lettura CSV con header come da specifiche del progetto
    val data = spark.read
      .option("header", value = true)
      .csv(filename)
      .rdd

    println(s"Raw rows loaded: ${data.count()}")

    // Conversione a RDD con mapping a case class
    val events = data.flatMap { row =>
      try {
        // Gestione robusta dei tipi (può essere String, Double, Int, etc)
        val latOpt = Option(row.getAs[Any](0)).orElse(Option(row.getAs[Any]("latitude"))).flatMap {
          case d: Double => Some(d)
          case i: Int => Some(i.toDouble)
          case s: String if s.nonEmpty => scala.util.Try(s.toDouble).toOption
          case _ => None
        }

        val lonOpt = Option(row.getAs[Any](1)).orElse(Option(row.getAs[Any]("longitude"))).flatMap {
          case d: Double => Some(d)
          case i: Int => Some(i.toDouble)
          case s: String if s.nonEmpty => scala.util.Try(s.toDouble).toOption
          case _ => None
        }

        val datetimeOpt = Option(row.getAs[Any](2)).orElse(Option(row.getAs[Any]("date"))).flatMap {
          case s: String if s.nonEmpty && s.length >= 10 => Some(s)
          case _ => None
        }

        // Combina le Option e crea l'evento
        (latOpt, lonOpt, datetimeOpt) match {
          case (Some(lat), Some(lon), Some(datetime)) =>
            val date = datetime.substring(0, 10)
            Some(EarthquakeEvent(lat, lon, date))
          case _ =>
            None
        }
      } catch {
        case e: Exception =>
          println(s"Warning: Failed to parse row: ${e.getMessage}")
          None
      }
    }

    val eventCount = events.count()
    println(s"Valid events parsed: $eventCount")

    if (eventCount == 0) {
      println("WARNING: No events were loaded! Check:")
      println("  1. File exists and path is correct")
      println("  2. File has correct CSV format with header")
      println("  3. File has columns: latitude,longitude,date")
    }

    events
  }

  /**
   * Versione alternativa per dataset molto grandi.
   * Carica direttamente come RDD testuale e fa parsing manuale,
   * evitando l'overhead della DataFrame API.
   *
   * @param spark Sessione Spark attiva
   * @param filename Path al file CSV (locale o su GCS)
   * @return RDD di EarthquakeEvent con eventi validi
   */
  def loadDataAsTextRDD(spark: SparkSession, filename: String): RDD[EarthquakeEvent] = {
    val sc = spark.sparkContext

    sc.textFile(filename)
      .mapPartitionsWithIndex { (idx, iter) =>
        // Salta header solo nella prima partizione
        if (idx == 0 && iter.hasNext) iter.drop(1) else iter
      }
      .flatMap(parseCSVLine)
  }

  /**
   * Parser manuale per una riga CSV.
   * Più efficiente per dataset molto grandi con formato fisso.
   *
   * @param line Riga CSV da parsare
   * @return Option[EarthquakeEvent] - Some se parsing riuscito, None altrimenti
   */
  private def parseCSVLine(line: String): Option[EarthquakeEvent] = {
    try {
      val parts = line.split(",")
      if (parts.length >= 3) {
        val lat = parts(0).trim.toDouble
        val lon = parts(1).trim.toDouble
        val datetime = parts(2).trim

        // Estrai data (primi 10 caratteri: yyyy-MM-dd)
        val date = if (datetime.length >= 10) {
          datetime.substring(0, 10)
        } else {
          datetime
        }

        Some(EarthquakeEvent(lat, lon, date))
      } else {
        None
      }
    } catch {
      case _: Exception => None
    }
  }

  /**
   * Carica i dati con repartitioning esplicito per ottimizzare il parallelismo.
   * Utile quando si conosce il numero ottimale di partizioni in base al cluster.
   *
   * @param spark Sessione Spark attiva
   * @param filename Path al file CSV
   * @param numPartitions Numero di partizioni desiderato
   * @return RDD di EarthquakeEvent partizionato
   */
  def loadDataWithPartitioning(
                                spark: SparkSession,
                                filename: String,
                                numPartitions: Int
                              ): RDD[EarthquakeEvent] = {
    loadData(spark, filename).repartition(numPartitions)
  }
}