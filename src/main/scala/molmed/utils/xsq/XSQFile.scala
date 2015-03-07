package molmed.utils.xsq

import ch.systemsx.cisd.hdf5._
import molmed.utils.traits.MultiLibrariesTrait
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConversions._

// JHDF5 author: Dr. Bernd Rinn  brinn@ethz.ch

/**
 * Created by vql on 29/01/15.
 * Present information for XSQ file
 * @param xsqFile full path to the XSQ file to read/write
 */
case class XSQFile(xsqFile: String) extends MultiLibrariesTrait {

  final val log: Logger = LoggerFactory.getLogger(classOf[XSQFile])
  val xsqReadHandler = HDF5Factory.openForReading(xsqFile)

  //  private object ReservedNames extends Enumeration {
  //    type ReservedNames = Value
  //    val RunMetadata, TagDetails, Indexing, LibraryDetails, Unassigned, Unclassified = Value
  //  }
  private val ReservedNames: List[String] = List("RunMetadata", "TagDetails",
    "Indexing", "LibraryDetails", "Unassigned", "Unclassified")

  private val runMetadataDSPath = "/RunMetadata"
  private val libraryDetailsDSPath =  runMetadataDSPath + "/LibraryDetails"
  private val libraryIndexDSPath = "/Indexing/LibraryIndex"
  private val libraryDetails:Array[HDF5CompoundDataMap] = xsqReadHandler.compound().
    readArray(libraryDetailsDSPath, classOf[HDF5CompoundDataMap])
  private val libraryGroupIndex = xsqReadHandler.`object`().getAllGroupMembers("/").filter(r => !ReservedNames.exists(_ == r) )

  // TODO: Isn't there a better way to do this runMetadata thingie?
  val runMetadata:Map[String, Any] = Map(
    "FileVersion" -> xsqReadHandler.string().getAttr(runMetadataDSPath, "FileVersion"),
    "FlowcellAssignment" -> xsqReadHandler.int8().getAttr(runMetadataDSPath, "FlowcellAssignment"),
    "HDFVersion" -> xsqReadHandler.string().getAttr(runMetadataDSPath, "HDFVersion"),
    "InstrumentModel" -> xsqReadHandler.string().getAttr(runMetadataDSPath, "InstrumentModel"),
    "InstrumentName" -> xsqReadHandler.string().getAttr(runMetadataDSPath, "InstrumentName"),
    "InstrumentSerial" -> xsqReadHandler.string().getAttr(runMetadataDSPath, "InstrumentSerial"),
    "InstrumentVendor" -> xsqReadHandler.string().getAttr(runMetadataDSPath, "InstrumentVendor"),
    "IsIndexingRun" -> xsqReadHandler.int8().getAttr(runMetadataDSPath, "IsIndexingRun"),
    "LaneNumber" -> xsqReadHandler.int8().getAttr(runMetadataDSPath, "LaneNumber"),
    "LibraryType" -> xsqReadHandler.string().getAttr(runMetadataDSPath, "LibraryType"),
    "Operator" -> xsqReadHandler.string().getAttr(runMetadataDSPath, "Operator"),
    "RunEndTime" -> xsqReadHandler.string().getAttr(runMetadataDSPath, "RunEndTime"),
    "RunName" -> xsqReadHandler.string().getAttr(runMetadataDSPath, "RunName"),
    "RunStartTime" -> xsqReadHandler.string().getAttr(runMetadataDSPath, "RunStartTime"),
    "SequencingSampleDescription" -> xsqReadHandler.string().getAttr(runMetadataDSPath, "SequencingSampleDescription")
  )

  override val isBarcoded: Boolean = runMetadata.get("IsIndexingRun").getOrElse(2).toString.toInt == 1

  /**
   * LibraryIndex: return a map of LibraryName -> Index
   */
  def getLibraryIndex: Map[String, Int] = {
    if (isBarcoded) {
      getLibraries.map(l => l.libraryName -> l.indexID.get).toMap
    } else Map("DefaultLibrary" -> 0)
  }

  def getLibraryNames: Seq[String] = {

    libraryDetails.map(row => row.get("LibraryName").toString)
  }

  def getLibraries: Seq[XsqLibrary] = {
    if (isBarcoded) {
      val libraries = libraryGroupIndex.filter(e => !e.contains("Unassigned") && !e.contains("__DATA_TYPES__")).
        map(l => {
        log.info(s"Library found in ...${xsqFile.takeRight(30)}: $l")
        val libName = xsqReadHandler.string().getAttr("/" + l, "LibraryName")
        val indexName = xsqReadHandler.string().getAttr("/" + l, "IndexName")
        val indexID = xsqReadHandler.int16().getAttr("/" + l, "IndexID").toInt
        new XsqLibrary(xsqFile, libraryName = libName, indexID = Some(indexID), indexName = Some(indexName))
      })
      libraries
    } else {
      Seq(new XsqLibrary(xsqFile, libraryName = "DefaultLibrary"))
    }
  }

  val lane = runMetadata.get("LaneNumber").get.toString.toInt
  val flowCellId = runMetadata.get("FlowcellAssignment").get.toString
  val date = runMetadata.get("RunEndTime").get.toString

  private val indexingMeta = if (isBarcoded) {
    Map("IndexKitName" -> xsqReadHandler.string().getAttr("/Indexing", "IndexKitName"),
      "IndexLength" -> xsqReadHandler.int32().getAttr("/Indexing","IndexLength")
    )
  }   else Map()

  def close = xsqReadHandler.close()

}
