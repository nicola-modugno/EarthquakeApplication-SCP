package analysis

import org.apache.spark.sql.SparkSession
import utils.Utils

object MetricsCollector {

  val CSV_HEADER: String = "approach,num_workers,num_partitions,total_events,unique_events," +
    "co_occurrences,load_time_ms,analysis_time_ms,total_time_ms,max_count,timestamp"

  /**
   * Salva le metriche in un file CSV.
   */
  def saveMetricsToCsv(
                        spark: SparkSession,
                        metrics: ExecutionMetrics,
                        outputPath: String
                      ): Unit = {
    val sc = spark.sparkContext
    val metricsPath = outputPath + "/metrics"

    // Elimina directory esistente
    Utils.deletePathIfExists(spark, metricsPath)

    val content = s"$CSV_HEADER\n${metrics.toCsvRow}"
    val rdd = sc.parallelize(Seq(content))
    rdd.coalesce(1).saveAsTextFile(metricsPath)
  }

  /**
   * Salva le metriche in formato leggibile.
   */
  def saveMetricsReadable(
                           spark: SparkSession,
                           metrics: ExecutionMetrics,
                           outputPath: String
                         ): Unit = {
    val sc = spark.sparkContext
    val readablePath = outputPath + "/metrics-readable"

    // Elimina directory esistente
    Utils.deletePathIfExists(spark, readablePath)

    val rdd = sc.parallelize(Seq(metrics.toReadableString))
    rdd.coalesce(1).saveAsTextFile(readablePath)
  }
}
