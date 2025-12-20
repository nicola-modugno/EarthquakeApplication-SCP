package utils

import analysis.LocationPair
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

}
