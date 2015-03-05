package molmed.utils.traits

import java.io.File

/**
 * Created by vql on 27/02/15.
 */
trait LibraryTrait{
  def seqFile: String
  def libraryName:String
  def indexID: Option[Int] = None
  def indexName: Option[String] = None
  def getNameAndBarCode:String = if (indexName.isEmpty) libraryName else libraryName + "_" + indexName.get

}
