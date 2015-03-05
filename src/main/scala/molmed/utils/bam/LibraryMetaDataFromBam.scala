package molmed.utils.bam

import java.io.File

import htsjdk.samtools.SamReaderFactory
import htsjdk.samtools.SAMReadGroupRecord
import molmed.utils.traits.LibraryTrait

/**
 * Created by vql on 27/02/15.
 */

/**
 *
 * @param seqFile  A BAM file to extract metadata.
 */
case class LibraryMetaDataFromBam(seqFile: String) extends LibraryTrait{

  private val bamReader = SamReaderFactory.makeDefault().open(new File(seqFile))
  val bamHeader = bamReader.getFileHeader
  //Todo: Make compatible with multisample/multilibrary BAM file
  //Currently only expect on RG record per BAM file
  private val readGroup:SAMReadGroupRecord = bamHeader.getReadGroups.get(0)

  def libraryName:String = readGroup.getLibrary
  override def indexID: Option[Int] = Some(libraryName.split("_").last.toInt)
  override def indexName: Option[String] = Some(indexID.toString)


}
