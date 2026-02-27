package analysis

/**
 * Rappresenta un evento sismico con informazioni geografiche e temporali.
 * 
 * @param latitude Latitudine dell'evento sismico
 * @param longitude Longitudine dell'evento sismico
 * @param date Data dell'evento in formato yyyy-MM-dd
 */
case class EarthquakeEvent(
  latitude: Double,
  longitude: Double,
  date: String
)
