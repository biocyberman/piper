package molmed.utils.xsq

/**
 * Created by vql on 09/02/15.
 */
import java.io.File

import ch.systemsx.cisd.hdf5._
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._

import ncsa.hdf.hdf5lib.exceptions.HDF5SymbolTableException

/**
 * A class to link or join multiple XSQ files.
 * @param targetFilePath This will delete any existing file with the same name as <var>targetFilePath</var>
 * @param sourceXSQFiles Sequence of files to link or join
 * @param createNew  If set as false, the file in <var>targetFilePath</var> is not overwritten, but at the moment,
 *                   not appended neither
 */

class MultiXSQFiles(targetFilePath: String, sourceXSQFiles:Seq[File], createNew: Boolean= true ) {

  val logging = LoggerFactory.getLogger(classOf[MultiXSQFiles])

  private val tFile = new File(targetFilePath)

  if(tFile.exists() && createNew) tFile.delete()

  private val xsqWriter = HDF5Factory.configure(targetFilePath).writer()


  def linkXSQFiles: Unit = {
    for {sourceXSQ <- sourceXSQFiles
    } {
      val  xsqReader: XSQFile = new XSQFile(sourceXSQ.getPath)
      copyRunMetaData(xsqReader.xsqReadHandler)
      copyIndexing(xsqReader.xsqReadHandler)
      for (library <- xsqReader.getLibraries) {
        createEternalLinks(xsqReader.xsqReadHandler, "/" + library.getNameAndBarCode, "/" + library.getNameAndBarCode)
      }
      xsqReader.close
    }
  }


  def joinXSQFiles: Unit = {
    for {sourceXSQ <- sourceXSQFiles
    } {
      val  xsqReader: XSQFile = new XSQFile(sourceXSQ.getPath)
      copyRunMetaData(xsqReader.xsqReadHandler)
      copyIndexing(xsqReader.xsqReadHandler)
      for (library <- xsqReader.getLibraries) {
        joinLibrary(xsqReader.xsqReadHandler, "/" + library.getNameAndBarCode, "/" + library.getNameAndBarCode)
      }
      xsqReader.close
    }
  }


  /**
   * Create HDF external soft links to all members of the sourceRootGroup in the sourceFile to targetRootGroup.
   * @return number of subgroups got linked.
   */
  def createEternalLinks(sourceFile:IHDF5Reader,
                         sourceRootGroup: String,
                         targetRootGroup:String): Int ={
    if (!xsqWriter.`object`().isGroup(targetRootGroup)) {
      // create an empty targetRootGroup and copy all attributes from sourceRootGroup
      createEmptyAndCopyAttributes(targetRootGroup, sourceFile)
    }
    // create soft links
    logging.debug("Linking {} from {} as {}",sourceRootGroup, sourceFile.file().getFile.getCanonicalPath, targetRootGroup)

    val subGroupsNames:Seq[Int] = xsqWriter.`object`().getAllGroupMembers(targetRootGroup).map(n => n.toInt)
    logging.info("Number of existing subgroups in link file: {}", subGroupsNames.size)
    val lastSubGroupCount = if (subGroupsNames.size > 0) subGroupsNames.max else 0
    val sourceSubGroups = sourceFile.`object`().getAllGroupMembers(targetRootGroup).toSeq
    for {subGroup <- sourceSubGroups}{
        val targetPath = sourceRootGroup + "/" + subGroup
        val linkSubGroup:String = "%04d".format(subGroup.toInt + lastSubGroupCount)
        val linkPath = targetRootGroup + "/" +  linkSubGroup
      try {
        xsqWriter.`object`().createExternalLink(sourceFile.file().getFile.getPath, targetPath, linkPath)
      } catch {
        case e: HDF5SymbolTableException => {
          logging.debug("Linking failed: linkPath: {}, subGroup: {}, targetPath: {}, lastSubGroupCount: {}",
            linkPath, subGroup, targetPath, lastSubGroupCount.toString)
          e
        }
      }
    }
    logging.info("Linked {} subgroups of library from {} to {}", sourceSubGroups.size.toString, sourceRootGroup, targetRootGroup)
    sourceSubGroups.size

  }

  def joinLibrary(sourceFile:IHDF5Reader,
                  sourceRootGroup: String,
                  targetRootGroup:String): Int ={
    if (!xsqWriter.`object`().isGroup(targetRootGroup)) {
      // create an empty targetRootGroup and copy all attributes from sourceRootGroup
      createEmptyAndCopyAttributes(targetRootGroup, sourceFile)
    }
    // create soft links
    logging.debug("Copying {} from {} as {}",sourceRootGroup, sourceFile.file().getFile.getCanonicalPath, targetRootGroup)

    val subGroupsNames:Seq[Int] = xsqWriter.`object`().getAllGroupMembers(targetRootGroup).map(n => n.toInt)
    logging.info("Number of existing subgroups in link file: {}", subGroupsNames.size)
    val lastSubGroupCount = if (subGroupsNames.size > 0) subGroupsNames.max else 0
    val sourceSubGroups = sourceFile.`object`().getAllGroupMembers(targetRootGroup).toSeq
    for {subGroup <- sourceSubGroups}{
      val targetPath = sourceRootGroup + "/" + subGroup
      val linkSubGroup:String = "%04d".format(subGroup.toInt + lastSubGroupCount)
      val linkPath = targetRootGroup + "/" +  linkSubGroup
      try {
        sourceFile.`object`().copy(targetPath, xsqWriter, linkPath)
      } catch {
        case e: HDF5SymbolTableException => {
          logging.debug("Copy failed: linkPath: {}, subGroup: {}, targetPath: {}, lastSubGroupCount: {}",
            linkPath, subGroup, targetPath, lastSubGroupCount.toString)
          e
        }
      }
    }
    logging.info("Copied {} subgroups of library from {} to {}", sourceSubGroups.size.toString, sourceRootGroup, targetRootGroup)
    sourceSubGroups.size


  }


  /**
   * Create an empty group and copy attributes from the source group
   * @param hdfGroupName
   * @param sourceFileHandle
   */
  def createEmptyAndCopyAttributes(hdfGroupName: String,
                                   sourceFileHandle:IHDF5Reader, targetGroupName: String = ""):Unit ={
    val realTargetName = if (targetGroupName.isEmpty) hdfGroupName else targetGroupName
    sourceFileHandle.`object`().copy(hdfGroupName, xsqWriter, realTargetName)
    val subGroupsNames:Seq[String] =  xsqWriter.`object`().getAllGroupMembers(realTargetName)
    // A better way would be to copy all attributes only, or 'shallow' copy
    // But I don't know how to do that with JHDF5 yet.
    subGroupsNames.map(sg => xsqWriter.`object`().delete(realTargetName + "/" + sg))

  }

  /**
   * This assumes that @sourceXSQFile is an indexed XSQ file
   * @param sourceXSQFile a IHDF5Reader instance
   */
  def copyRunMetaData(sourceXSQFile: IHDF5Reader): Unit ={
    logging.info("Group exists: {} as {}", "/RunMetadata", xsqWriter.`object`().isGroup("/RunMetadata"))
    if (!xsqWriter.`object`().isGroup("/RunMetadata")) {
      // copy RunMetaData
      logging.info("Copying RunMetadata from {}", sourceXSQFile.file().getFile.getCanonicalPath)
      sourceXSQFile.`object`().copy("RunMetadata", xsqWriter, "/RunMetadata")

    }
   }

  /**
   * This assumes that sourceXSQFile is an indexed XSQ file
   * @param sourceXSQFile a IHDF5Reader instance
   */
  
  def copyIndexing(sourceXSQFile: IHDF5Reader): Unit ={
    if (!xsqWriter.`object`().isGroup("/Indexing")) {
      // copy Indexing
      logging.info("Copying Indexing from {}", sourceXSQFile.file().getFile.getCanonicalPath)
      sourceXSQFile.`object`().copy("/Indexing", xsqWriter, "/Indexing")


    }

  }

  def close =    xsqWriter.close()

}
