package analysis

/**
 * Rappresenta una coppia ordinata di località geografiche.
 * La coppia è sempre ordinata: first < second (lessicograficamente).
 * 
 * @param first Prima Location (minore)
 * @param second Seconda Location (maggiore)
 */
case class LocationPair(
  first: Location,
  second: Location
) {
  require(first <= second, s"LocationPair must be ordered: $first should be <= $second")
  
  /**
   * Rappresentazione testuale della coppia.
   */
  override def toString: String = s"($first, $second)"
  
  /**
   * Converte la coppia in formato tuple per compatibilità.
   * 
   * @return Tupla ((lat1, lon1), (lat2, lon2))
   */
  def toTuple: ((Double, Double), (Double, Double)) = {
    ((first.latitude, first.longitude), (second.latitude, second.longitude))
  }
}

object LocationPair {
  /**
   * Crea una LocationPair da due Location, assicurandosi che siano ordinate.
   * 
   * @param loc1 Prima location
   * @param loc2 Seconda location
   * @return LocationPair ordinata
   */
  def apply(loc1: Location, loc2: Location): LocationPair = {
    if (loc1 <= loc2) new LocationPair(loc1, loc2)
    else new LocationPair(loc2, loc1)
  }
  
  /**
   * Crea una LocationPair da tuple.
   * 
   * @param tuple1 Prima tupla (lat, lon)
   * @param tuple2 Seconda tupla (lat, lon)
   * @return LocationPair ordinata
   */
  def fromTuples(tuple1: (Double, Double), tuple2: (Double, Double)): LocationPair = {
    val loc1 = Location.fromTuple(tuple1)
    val loc2 = Location.fromTuple(tuple2)
    apply(loc1, loc2)
  }
}
