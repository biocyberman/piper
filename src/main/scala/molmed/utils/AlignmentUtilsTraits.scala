package molmed.utils
// This file can't be put under molmed.utils.traits. Don't know why yet.

/**
 * Base class for alignment workflows.
 */
abstract class AligmentUtils(projectName: Option[String], uppmaxConfig: UppmaxConfig) extends UppmaxJob(uppmaxConfig)

// Possible alignment options.
trait AlignerOption
case object BwaMem extends AlignerOption
case object BwaAln extends AlignerOption
//Novoalign (Novocraft) options
case object NvoAln extends AlignerOption
case object NvoAlnMpi extends AlignerOption
case object NvoAlnCS extends AlignerOption
case object NvoAlnCSMpi extends AlignerOption

