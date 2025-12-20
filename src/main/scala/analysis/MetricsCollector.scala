package analysis

import org.apache.spark.sql.SparkSession

/**
 * Raccoglie e salva metriche di performance per l'analisi.
 */
object MetricsCollector {

  val CSV_HEADER: String = "approach,partitioner,num_workers,num_partitions,total_events,unique_events," +
    "co_occurrences,load_time_ms,analysis_time_ms,total_time_ms,max_count,timestamp"

  /**
   * Salva le metriche in un file CSV.
   * Se il file esiste, appende una nuova riga; altrimenti crea il file con header.
   */
  def saveMetricsToCsv(
                        spark: SparkSession,
                        metrics: ExecutionMetrics,
                        outputPath: String
                      ): Unit = {
    val sc = spark.sparkContext

    // Crea RDD con header + dati
    val content = s"$CSV_HEADER\n${metrics.toCsvRow}"
    val rdd = sc.parallelize(Seq(content))

    // Salva con coalesce(1) per avere un solo file
    rdd.coalesce(1).saveAsTextFile(outputPath + "/metrics")
  }

  /**
   * Salva le metriche in formato leggibile per debugging.
   */
  def saveMetricsReadable(
                           spark: SparkSession,
                           metrics: ExecutionMetrics,
                           outputPath: String
                         ): Unit = {
    val sc = spark.sparkContext
    val rdd = sc.parallelize(Seq(metrics.toReadableString))
    rdd.coalesce(1).saveAsTextFile(outputPath + "/metrics-readable")
  }

}




