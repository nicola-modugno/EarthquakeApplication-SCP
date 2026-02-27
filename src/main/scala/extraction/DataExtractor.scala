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
          println(s"WARNING: Failed to parse row: ${e.getMessage}")
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

}