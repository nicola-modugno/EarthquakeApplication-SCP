package analysis

/**
 * Rappresenta un punto geografico con coordinate arrotondate.
 * 
 * @param latitude Latitudine arrotondata alla prima cifra decimale
 * @param longitude Longitudine arrotondata alla prima cifra decimale
 */
case class Location(
  latitude: Double,
  longitude: Double
) extends Ordered[Location] {
  /**
   * Confronta due location per ordinamento.
   * Prima per latitudine, poi per longitudine.
   */
  override def compare(that: Location): Int = {
    val latCompare = this.latitude.compare(that.latitude)
    if (latCompare != 0) latCompare
    else this.longitude.compare(that.longitude)
  }
  
  /**
   * Rappresentazione testuale della location.
   */
  override def toString: String = s"($latitude, $longitude)"
}

object Location {
}
