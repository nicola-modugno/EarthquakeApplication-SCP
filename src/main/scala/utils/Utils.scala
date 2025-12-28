package utils

import analysis.LocationPair
import org.apache.spark.sql.SparkSession
import org.apache.hadoop.fs.{FileSystem, Path}

import scala.math.BigDecimal.RoundingMode

object Utils {

  /**
   * Scarta le cifre decimali successive alla prima di una coordinata: arrotondamento con DOWN.
   */
  def roundCoordinate(coord: Double): Double = {
    BigDecimal(coord).setScale(1, RoundingMode.DOWN).toDouble
  }

  /**
   * Formatta l'output secondo le specifiche del progetto.
   */
  def formatOutput(pair: LocationPair, dates: Array[String]): String = {
    val pairStr = s"(${pair.first}, ${pair.second})"
    val datesStr = dates.mkString("\n")
    s"$pairStr\n$datesStr"
  }

  /**
   * Elimina una directory se esiste (ricorsivamente).
   */
  def deletePathIfExists(spark: SparkSession, path: String): Unit = {
    try {
      val hadoopPath = new Path(path)
      val fs = FileSystem.get(spark.sparkContext.hadoopConfiguration)

      if (fs.exists(hadoopPath)) {
        println(s"Deleting existing directory: $path")
        fs.delete(hadoopPath, true) // true = ricorsivo
        println(s"✓ Directory deleted")
      }
    } catch {
      case e: Exception =>
        println(s"Warning: Could not delete $path: ${e.getMessage}")
    }
  }

  /**
   * Salva l'output su file usando Spark RDD.
   * Elimina automaticamente la directory se esiste.
   */
  def saveOutput(spark: SparkSession, content: String, outputPath: String): Unit = {
    // ✅ Elimina directory esistente
    deletePathIfExists(spark, outputPath)

    val sc = spark.sparkContext
    val outputRDD = sc.parallelize(Seq(content))
    outputRDD.coalesce(1).saveAsTextFile(outputPath)
  }

}