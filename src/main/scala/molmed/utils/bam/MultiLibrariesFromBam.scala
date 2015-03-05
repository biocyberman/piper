package molmed.utils.bam

import java.io.File

import molmed.utils.traits.{LibraryTrait, MultiLibrariesTrait}

/**
 * Created by vql on 27/02/15.
 */
case class MultiLibrariesFromBam(bamFile: File) extends MultiLibrariesTrait{
  val bamMeta = LibraryMetaDataFromBam(bamFile.getCanonicalPath)
  private val readGroups = bamMeta.bamHeader.getReadGroups
  private val firstReadGroup = readGroups.get(0)

  override def date: String = firstReadGroup.getRunDate.toString

  override def lane: Int = firstReadGroup.getPlatformUnit.split("\\.|_").last.toInt

  /**
   * LibraryIndex: return a map of LibraryName -> Index
   */
  override def getLibraryIndex: Map[String, Int] = Map(bamMeta.libraryName -> bamMeta.indexID.get)

  override def isBarcoded: Boolean = !bamMeta.indexID.isEmpty

  override def getLibraryNames: Seq[String] = Seq(bamMeta.libraryName)

  override def flowCellId: String = firstReadGroup.getPlatformUnit.split("\\.|_").head

  override def getLibraries: Seq[LibraryTrait] = Seq(bamMeta)
}
