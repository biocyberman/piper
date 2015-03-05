package molmed.utils.traits

/**
 * Created by vql on 27/02/15.
 */
abstract class MultiLibrariesTrait {

  def isBarcoded: Boolean

  /**
   * LibraryIndex: return a map of LibraryName -> Index
   */
  def getLibraryIndex: Map[String, Int]

  def getLibraryNames: Seq[String]

  def getLibraries: Seq[LibraryTrait]

  def printLibNames = for (n <- getLibraryNames) println(n)

  def printLibraryIndex = {
    for (k <- getLibraryIndex.keys) {
      println(k + " " + getLibraryIndex.get(k).get.toString)
    }
  }
  //def runMetadata:Map[String, Any]
  def lane:Int
  def flowCellId:String
  def date:String
}
