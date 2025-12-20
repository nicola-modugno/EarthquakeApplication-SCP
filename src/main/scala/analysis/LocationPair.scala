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

}
