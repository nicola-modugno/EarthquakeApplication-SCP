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
   * Carica i dati dal file CSV utilizzando Spark DataFrame API.
   * Questo approccio è ottimizzato per parsing CSV complessi e gestione automatica dei tipi.
   *
   * @param spark Sessione Spark attiva
   * @param filename Path al file CSV (locale o su GCS)
   * @return RDD di EarthquakeEvent con eventi validi
   */
  def loadData(spark: SparkSession, filename: String): RDD[EarthquakeEvent] = {

    // Lettura CSV con header usando Spark DataFrame API
    val df = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(filename)

    // Conversione a RDD con mapping a case class
    // Filter per rimuovere righe con valori null o malformate
    df.rdd.flatMap { row =>
      try {
        // Usa Option per gestire i valori null
        val latOpt = Option(row.getAs[Any]("latitude")).flatMap {
          case d: Double => Some(d)
          case i: Int => Some(i.toDouble)
          case s: String => scala.util.Try(s.toDouble).toOption
          case _ => None
        }

        val lonOpt = Option(row.getAs[Any]("longitude")).flatMap {
          case d: Double => Some(d)
          case i: Int => Some(i.toDouble)
          case s: String => scala.util.Try(s.toDouble).toOption
          case _ => None
        }

        val datetimeOpt = Option(row.getAs[String]("date"))

        // Combina le Option e crea l'evento
        for {
          lat <- latOpt
          lon <- lonOpt
          datetime <- datetimeOpt
          if datetime.length >= 10
        } yield {
          val date = datetime.substring(0, 10)
          EarthquakeEvent(lat, lon, date)
        }
      } catch {
        case _: Exception => None
      }
    }
  }

}