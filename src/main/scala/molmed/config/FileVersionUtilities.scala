package molmed.config

import java.io.File

object FileVersionUtilities {

  type ResourceMap = Map[String, Option[Seq[VersionedFile]]]

  /**
   * Sticks a version on the file resource
   */
  case class VersionedFile(
    file: File,
    version: Option[String] = Some(Constants.unknown))

  implicit def versionedFile2File(x: VersionedFile): File =
    x.file

  implicit def seqOfVersionedFile2SeqOfFile(x: Seq[VersionedFile]): Seq[File] =
    x.map(y => versionedFile2File(y))

  def fileVersionFromKey(map: ResourceMap, key: String): String = {
    val versions = multipleFileVersionsFromKey(map, key)
    if (versions.size == 1)
      versions.head.version.getOrElse(Constants.unknown)
    else
      Constants.unknown
  }

  def multipleFileVersionsFromKey(map: ResourceMap, key: String): Seq[VersionedFile] = {
    val versions = map(key).getOrElse(Seq())
    versions
  }

  /**
   * This trait is used to make sure that both programs
   * and file resources can be managed by the same code
   * downstream once they've been extended with this trait.
   */
  trait NameVersionAndPath {
    def getName(): String
    def getPath(): String
    def getVersion(): String
  }

  /**
   * Transform list of resources (with name and path) to a map from the
   * resource name to the file(s) associated with the resource.
   * @param 	list	A list of resources which have both name and path
   * @return A map with resource names as keys and file resources as values
   */
  def transformToNamePathMap[T](list: Seq[T with NameVersionAndPath]): ResourceMap = {
    list.groupBy(x => x.getName()).
      mapValues(x =>
      Some(x.map(f =>
        new VersionedFile(new File(f.getPath), Some(f.getVersion()))).toSeq)).
      withDefaultValue(None)
  }


  /**
   * Extract a file for a certain resource. Note that getFileSeqFromKey should be used
   * if you are looking a resource with multiple files.
   * @param 	map		from the resource name to the file relating to that resource.
   * @param 	key		the key to look for
   * @throws IllegalArgumentException if one tries to look for a key that is no present.
   * @throws AssertionError if there is multiple hits for this key.
   * @returns The file related to the key.
   */
  def getFileFromKey(map: ResourceMap, key: String): File = {
    val value = map(key).
      getOrElse(
        throw new IllegalArgumentException("Couldn't find: \"" + key +
          "\" key in for program/resource read from global config file."))

    assert(value.length == 1, "Tried to get a single path for key: \"" + key + "\" but found multiple hits.")
    value.head
  }

  /**
   * Extract the file version from the specified resource key.
   * @param map	from the resource name to the versioned file relating to that resource.
   * @param key	the key to look for
   * @throws IllegalArgumentException if one tries to look for a key that is no present.
   * @throws AssertionError if there is multiple hits for this key.
   * @returns The version related to the key.
   */
  def getVersionFromKey(map: ResourceMap, key: String): Option[String] = {
    val value = map(key).
      getOrElse(
        throw new IllegalArgumentException("Couldn't find: \"" + key +
          "\" key in for program/resource read from global config file."))

    assert(value.length == 1, "Tried to get a single path for key: \"" + key + "\" but found multiple hits.")
    value.head.version
  }


  /**
   * Extract the path for a program from the specified resource key.
   * @param map	from the resource name to the versioned file relating to that resource.
   * @param key	the key to look for
   * @throws IllegalArgumentException if one tries to look for a key that is no present.
   * @throws AssertionError if there is multiple hits for this key.
   * @returns The version related to the key.
   */
  def getPathFromKey(map: ResourceMap, key: String): String = {
    val value = map(key).
      getOrElse(
        throw new IllegalArgumentException("Couldn't find: \"" + key +
          "\" key in for program/resource read from global config file."))

    assert(value.length == 1, "Tried to get a single path for key: \"" + key + "\" but found multiple hits.")
    value.head.getPath
  }

  /**
   * Extract file(s) for a certain resource.
   * @param 	map		from the resource name to the file(s) relating to that resource.
   * @param 	key		the key to look for
   * @throws IllegalArgumentException if one tries to look for a key that is no present.
   * @throws AssertionError if there is multiple hits for this key.
   * @returns The file related to the key.
   */
  def getFileSeqFromKey(map: ResourceMap, key: String): Seq[VersionedFile] = {
    val value = map(key).
      getOrElse(
        throw new IllegalArgumentException("Couldn't find: \"" + key +
          " \" key in for program/resource read from global config file."))
    value
  }

}