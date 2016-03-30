package molmed.config

import java.io.File
import javax.xml.bind.JAXBContext
import molmed.xml.globalconfig.GlobalConfig
import java.io.StringReader
import scala.collection.JavaConversions._
import molmed.xml.globalconfig.Resource
import molmed.xml.globalconfig.Program
import molmed.config.FileVersionUtilities._

/**
 * Contains arguments for loading file resources (e.g. dbSNP). Also contains the
 * setResourcesFromConfigXML which allows all of these values to be loaded from
 *  a xml file conforming to the GlobalConfigSchema.xsd specification (see:
 *  src/main/resources/).
 */
trait FileAndProgramResourceConfig {

  @Input(doc = "XML configuration file containing system configuration. E.g. paths to resources and programs etc. " +
    "Any thing specified in this file will be overriden if this is specifically set on the commandline.",
    fullName = "global_config", shortName = "gc", required = false)
  var globalConfig: File = _

  /**
   * File resources relating to the human genome.
   */

  @Input(doc = "dbsnp ROD to use (must be in VCF format)", fullName = "dbsnp", shortName = "D", required = false)
  var dbSNP: File = _

  @Input(doc = "extra VCF files to use as reference indels for Indel Realignment", fullName = "extra_indels", shortName = "indels", required = false)
  var indels: Seq[File] = Seq()

  @Input(doc = "HapMap file to use with variant recalibration.", fullName = "hapmap", shortName = "hm", required = false)
  var hapmap: File = _

  @Input(doc = "Omni file fo use with variant recalibration ", fullName = "omni", shortName = "om", required = false)
  var omni: File = _

  @Input(doc = "Mills indel file to use with variant recalibration", fullName = "mills", shortName = "mi", required = false)
  var mills: File = _

  @Input(doc = "1000 Genomes high confidence SNP file to use with variant recalibration", fullName = "thousandGenomes", shortName = "tg", required = false)
  var thousandGenomes: File = _

  @Argument(doc = "snpEff reference to use", fullName = "snpEff_reference", shortName = "snpEffRef", required = false)
  var snpEffReference: String = _

  @Argument(doc = "Indexed reference file for use with novoalignCS", fullName = "novoalignCSReference", shortName = "nvoalnCSRef", required = false)
  var novoalignCSReference: File = _

  /**
   * Paths to programs
   */

  @Input(doc = "The path to the binary of bwa (usually BAM files have already been mapped - but if you want to remap this is the option)", fullName = "path_to_aligner", shortName = "alner", required = false)
  var alignerPath: File = _

  @Input(doc = "The path to the binary of samtools", fullName = "path_to_samtools", shortName = "samtools", required = false)
  var samtoolsPath: File = _

  @Input(doc = "The path to the binary of qualimap", fullName = "path_to_qualimap", shortName = "qualimap", required = false)
  var qualimapPath: File = _

  @Input(doc = "The path to RNA-SeQC", shortName = "rnaseqc", fullName = "rna_seqc", required = false)
  var pathToRNASeQC: File = _

  @Input(doc = "The path to the perl script used correct for empty reads ", fullName = "path_sync_script", shortName = "sync", required = false)
  var syncPath: File = _

  @Input(doc = "The path to the binary of cufflinks", fullName = "path_to_cufflinks", shortName = "cufflinks", required = false)
  var cufflinksPath: File = _

  @Input(doc = "The path to the binary of cutadapt", fullName = "path_to_cutadapt", shortName = "cutadapt", required = false)
  var cutadaptPath: File = _

  @Input(doc = "The path to the binary of tophat", fullName = "path_to_tophat", shortName = "tophat", required = false)
  var tophatPath: File = _

  @Input(doc = "The path to the start-up script of snpEff", fullName = "path_to_snpeff", shortName = "snpEff", required = false)
  var snpEffPath: File = _

  @Input(doc = "The path to the binary of bcftools", fullName = "path_to_bcftools", shortName = "bcftools", required = false)
  var bcftoolsPath: File = _

  // Please not that this has no override in the xml file, but has to be overriden from the commandline if this is necessary.
  @Argument(doc = "The path to snpEff config", fullName = "path_to_snpeff_config", shortName = "snpEffConf", required = false)
  var snpEffConfigPath: File = _

  @Argument(doc = "The path to novoalignCS program", fullName = "path_to_novoalignCS", shortName = "novoalignCS", required = false)
  var novoalignCS: File = _

  @Argument(doc = "The path to novoalign program", fullName = "path_to_novoalign", shortName = "novoalign", required = false)
  var novoalign: File = _

  @Argument(doc = "The path to novosort program", fullName = "path_to_novosort", shortName = "novosort", required = false)
  var novosort: File = _

  @Argument(doc = "The path to novoindex program", fullName = "path_to_novoindex", shortName = "novoindex", required = false)
  var novoindex: File = _

  /**
   * Implicitly convert any File to Option File, as necessary.
   */
  implicit def file2Option(file: File) = if (file == null) None else Some(file)

  /**
   * Will load file resources from XML file. Any values set via the
   * commandline will not be overriden by this.
   * @param	xmlFile	A xml file conforming to the specification in GlobalConfigSchema.xsd
   * @param	doNotLoadDefaultResourceFiles Skip loading the default resource files.
   * 									  Will only get resource file explicitly from the
   *                                      commandline.
   * @returns A map from a resource key to a versioned file. This will be empty
   * if the xmlFile was not defined
   */
  def configureResourcesFromConfigXML(
    xmlFile: Option[File],
    doNotLoadDefaultResourceFiles: Boolean = false): ResourceMap = {

    /**
     * Will set all file resources specified in the config,
     * but will not override them if they have been setup via the
     * commandline.
     * @param	config	The GlobalConfig instance containing all paths.
     * @retuns Unit
     */

    println("Working with global config file: "+ xmlFile.get.getAbsolutePath)

    def setFileResources(config: GlobalConfig): ResourceMap = {

      val resources = config.getResources().getResource().map(f => {
        val res = new Resource with NameVersionAndPath
        res.setName(f.getName())
        res.setPath(f.getPath())
        res.setVersion(f.getVersion())
        res
      })

      val resourceNameToPathsMap = transformToNamePathMap(resources)

      if (this.dbSNP == null)
        this.dbSNP = getFileFromKey(resourceNameToPathsMap, Constants.DB_SNP)

      if (this.indels.isEmpty)
        this.indels = getFileSeqFromKey(resourceNameToPathsMap, Constants.INDELS)

      if (this.hapmap == null)
        this.hapmap = getFileFromKey(resourceNameToPathsMap, Constants.HAPMAP)

      if (this.omni == null)
        this.omni = getFileFromKey(resourceNameToPathsMap, Constants.OMNI)

      if (this.mills == null)
        this.mills = getFileFromKey(resourceNameToPathsMap, Constants.MILLS)

      if (this.thousandGenomes == null)
        this.thousandGenomes = getFileFromKey(resourceNameToPathsMap, Constants.THOUSAND_GENOMES)

      if (this.snpEffReference == null)
        this.snpEffReference = getVersionFromKey(resourceNameToPathsMap, Constants.SNP_EFF_REFERENCE).get

      if (this.novoalignCSReference == null)
        this.novoalignCSReference = getFileFromKey(resourceNameToPathsMap, Constants.NVOALNCSREF).get

      resourceNameToPathsMap
    }

    /**
     * Sets the program resources.
     *
     * @param config the new program resources
     */
    def setProgramResources(config: GlobalConfig): ResourceMap = {

      val programs = config.getPrograms().getProgram().map(f => {
        val prog = new Program with NameVersionAndPath
        prog.setName(f.getName())
        prog.setPath(f.getPath())
        prog.setVersion(f.getVersion())
        prog
      })

      val programNameToPathsMap = transformToNamePathMap(programs)

      if (this.alignerPath == null)
        this.alignerPath = getFileFromKey(programNameToPathsMap, Constants.BWA)

      if (this.samtoolsPath == null)
        this.samtoolsPath = getFileFromKey(programNameToPathsMap, Constants.SAMTOOLS)

      if (this.qualimapPath == null)
        this.qualimapPath = getFileFromKey(programNameToPathsMap, Constants.QUALIMAP)

      if (this.pathToRNASeQC == null)
        this.pathToRNASeQC = getFileFromKey(programNameToPathsMap, Constants.RNA_SEQC)

      if (this.syncPath == null)
        this.syncPath = getFileFromKey(programNameToPathsMap, Constants.FIX_EMPTY_READS)

      if (this.cufflinksPath == null)
        this.cufflinksPath = getFileFromKey(programNameToPathsMap, Constants.CUFFLINKS)

      if (this.cutadaptPath == null)
        this.cutadaptPath = getFileFromKey(programNameToPathsMap, Constants.CUTADAPT)

      if (this.tophatPath == null)
        this.tophatPath = getFileFromKey(programNameToPathsMap, Constants.TOPHAP)

      if (this.snpEffPath == null)
        this.snpEffPath = getFileFromKey(programNameToPathsMap, Constants.SNP_EFF)

<<<<<<< variant A
      if (this.novoalignCS == null)
        this.novoalignCS = getFileFromKey(programNameToPathsMap, Constants.NOVOALIGNCS)

      if (this.novoalign== null)
        this.novoalign = getFileFromKey(programNameToPathsMap, Constants.NOVOALIGN)

      if (this.novosort == null)
        this.novosort = getFileFromKey(programNameToPathsMap, Constants.NOVOSORT)

>>>>>>> variant B
======= end
      if (this.bcftoolsPath == null)
        this.bcftoolsPath = getFileFromKey(programNameToPathsMap, Constants.BCFTOOLS)

      programNameToPathsMap

    }

    if (xmlFile.isDefined) {

      val context = JAXBContext.newInstance(classOf[GlobalConfig])
      val unmarshaller = context.createUnmarshaller()
      val reader = new StringReader(scala.io.Source.fromFile(xmlFile.get).mkString)
      val config = unmarshaller.unmarshal(reader).asInstanceOf[GlobalConfig]
      reader.close()

      val fileResources =
        if (doNotLoadDefaultResourceFiles)
          Map()
        else
          setFileResources(config)

      val programResources = setProgramResources(config)

      (fileResources ++ programResources).withDefaultValue(None)

    } else
      Map().withDefaultValue(None)

  }

}
