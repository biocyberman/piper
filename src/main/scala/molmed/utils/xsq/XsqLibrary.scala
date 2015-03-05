package molmed.utils.xsq

import molmed.utils.traits.LibraryTrait

/**
 * Object containing a XSQ library node and a function to process it.
 * @author Biocyberman
 */

/**
 *
 * @param seqFile An XSQ file that contains this library
 * @param libraryName Name of this library
 * @param indexID An integer as index of the library
 * @param indexName Name of the index, in XSQ it is usually the same as indexID.
 */
case class XsqLibrary(seqFile: String,
                   libraryName: String,
                   override val indexID: Option[Int] = None,
                   override val indexName: Option[String] = None) extends LibraryTrait{
  //TODO: Implement adding libraries to combine or link the data to a new XSQ file

}
