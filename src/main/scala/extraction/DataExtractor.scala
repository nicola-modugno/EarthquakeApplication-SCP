package extraction

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import analysis.EarthquakeEvent

object DataExtractor {
  /**
   * Carica i dati dal file CSV e li converte in RDD di EarthquakeEvent
   * Utilizza spark.read per parsing efficiente del CSV
   */
  def loadData(spark: SparkSession, filename: String): RDD[EarthquakeEvent] = {
    import spark.implicits

    // Lettura CSV con header usando Spark DataFrame API
    val df = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(filename)

    // Conversione a RDD con mapping a case class
    // Filter per rimuovere righe con valori null
    df.rdd.flatMap { row =>
      try {
        val lat = row.getAs[Double]("latitude")
        val lon = row.getAs[Double]("longitude")
        val datetime = row.getAs[String]("date")
        
        // Estrai solo la parte della data, ignorando l'orario
        val date = if (datetime != null && datetime.length >= 10) {
          datetime.substring(0, 10)
        } else {
          null
        }

        if (lat != null && lon != null && date != null) {
          Some(EarthquakeEvent(lat, lon, date))
        } else {
          None
        }
      } catch {
        case _: Exception => None
      }
    }
  }

  /**
   * Versione alternativa per dataset molto grandi
   * Carica direttamente come RDD testuale e fa parsing manuale
   */
  def loadDataAsTextRDD(spark: SparkSession, filename: String): RDD[EarthquakeEvent] = {
    val sc = spark.sparkContext
    
    sc.textFile(filename)
      .mapPartitionsWithIndex { (idx, iter) =>
        // Salta header nella prima partizione
        if (idx == 0 && iter.hasNext) iter.drop(1) else iter
      }
      .flatMap(parseCSVLine)
  }

  /**
   * Parser manuale per una riga CSV
   * Più efficiente per dataset molto grandi
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
}
