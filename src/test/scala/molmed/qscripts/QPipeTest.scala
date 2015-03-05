package molmed.qscripts
import molmed.config.{SolidProgramAndResourceConfig, Constants, FileAndProgramResourceConfig, UppmaxXMLConfiguration}
import molmed.config.FileVersionUtilities._
import org.broadinstitute.gatk.queue.QScript
import java.io.File
/**
 * Created by biocyberman on 06/02/15.
 */
object QPipeTest extends App with SolidProgramAndResourceConfig
with UppmaxXMLConfiguration {
  val resourceMap:ResourceMap =
    this.configureResourcesFromConfigXML(Some(new File("config/afmd/uppmax_global_config.xml")), false)
  println(resourceMap.get(Constants.NOVOSORT).get)

  println(resourceMap.size)

  this.novosort = getFileFromKey(resourceMap, Constants.NOVOSORT)


  println(this.novosort.getPath)
}
