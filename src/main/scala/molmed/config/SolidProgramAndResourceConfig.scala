package molmed.config

/**
 * Created by biocyberman on 05/03/15.
 */

import java.io.File
import molmed.xml.globalconfig.GlobalConfig
import molmed.config.FileVersionUtilities._

trait SolidProgramAndResourceConfig extends FileAndProgramResourceConfig {

  /**
   * Define additional programs or resources for working with SOLiD. Other stuffs are defined in FileAndProgramResourceConfig
   */

  @Argument(doc = "Indexed reference file for use with novoalignCS", fullName = "novoalignCSReference", shortName = "nvoalnCSRef", required = false)
  var novoalignCSReference: File = _

  @Argument(doc = "The path to novoalignCS program", fullName = "path_to_novoalignCS", shortName = "novoalignCS", required = false)
  var novoalignCS: File = _

  @Argument(doc = "The path to novoalign program", fullName = "path_to_novoalign", shortName = "novoalign", required = false)
  var novoalign: File = _

  @Argument(doc = "The path to novosort program", fullName = "path_to_novosort", shortName = "novosort", required = false)
  var novosort: File = _

  @Argument(doc = "The path to novoindex program", fullName = "path_to_novoindex", shortName = "novoindex", required = false)
  var novoindex: File = _


  /**
   * Helper functions for SOLiD
   */

  /**
   * Will set all file resources specified in the config,
   * but will not override them if they have been setup via the
   * commandline.
   * @param	config	The GlobalConfig instance containing all paths.
   * @retuns Unit
   */
   protected override def setFileResources(config: GlobalConfig): ResourceMap = {

    val resourceNameToPathsMap =  super.setFileResources(config)

    if (this.novoalignCSReference == null)
      this.novoalignCSReference = getFileFromKey(resourceNameToPathsMap, Constants.NVOALNCSREF).get

    resourceNameToPathsMap
  }

  /**
   * Sets the program resources.
   *
   * @param config the new program resources
   */
  protected override def setProgramResources(config: GlobalConfig): ResourceMap = {

    val programNameToPathsMap = super.setProgramResources(config)

    if (this.novoalignCS == null)
      this.novoalignCS = getFileFromKey(programNameToPathsMap, Constants.NOVOALIGNCS)

    if (this.novoalign== null)
      this.novoalign = getFileFromKey(programNameToPathsMap, Constants.NOVOALIGN)

    if (this.novosort == null)
      this.novosort = getFileFromKey(programNameToPathsMap, Constants.NOVOSORT)

    programNameToPathsMap

  }

}
