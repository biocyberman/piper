#!/bin/bash -l

########################################################
# Utility script to download dependencies and install them for building Piper
# Piper
# Usage:
#  /build_piper.sh [number of thread] [force install jhdf5 for maven] [force compile gatk]
# Example: ./build_piper.sh 4
########################################################

# Run parrallel mode for Maven. This requires Maven 3.0 onward
MAVEN_NUM_THREADS=${1:-4}

# Put anything at second positional parameter to skip installing jhdf5
# JHDF5 is downloaded from
# https://wiki-bsse.ethz.ch/download/attachments/26609237/sis-jhdf5-14.12.1-r33502.zip?version=1&modificationDate=1424599261225&api=v2
INSTALL_JHDF5=${2}

# Put anything at third positional parameter will force download and compile GATK
COMPILE_GATK=${3}


echo "########################################################"
echo "Building Piper"
echo "########################################################"

check_errs()
{
  # Function. Parameter 1 is the return code
  # Parameter 2 is text to display on failure.
  # Kill all child processes before exit.

  if [ "${1}" -ne "0" ]; then
    echo "ERROR # ${1} : ${2}"
    for job in `jobs -p`
    do
        kill -9 $job
    done
    exit ${1}
  fi
}

prepare_maven_deps(){
echo "Installing JDHF5 for Maven before it can compile Piper for support of colorspace data"
if [ !-f "lib/sis-jhdf5-batteries_included.jar" ]; then
     wget https://wiki-bsse.ethz.ch/download/attachments/26609237/sis-jhdf5-14.12.1-r33502.zip -O sis-jhdf5-14.12.1-r33502.zip
     unzip -of sis-jhdf5-14.12.1-r33502.zip
     cp sis-jhdf5/lib/batteries_included/sis-jhdf5-batteries_included.jar lib/
     cp sis-jhdf5/src/sis-jhdf5-src.zip lib/
     rm -rf sis-jhdf5
     rm -rf sis-jhdf5-14.12.1-r33502.zip
 fi
# Prepare library dependencies for maven
 mvn install:install-file -Dfile=lib/sis-jhdf5-batteries_included.jar -DgroupId=ch.systemsx.cisd \
 -DartifactId=jhdf5 -Dversion=14.12 -Dpackaging=jar

 mvn install:install-file -Dfile=lib/sis-jhdf5-javadoc.zip -DgroupId=ch.systemsx.cisd \
  -DartifactId=jhdf5 -Dversion=14.12 -Dpackaging=zip -Dclassifier=javadoc

 mvn install:install-file -Dfile=lib/sis-jhdf5-src.zip -DgroupId=ch.systemsx.cisd \
 -DartifactId=jhdf5 -Dversion=14.12 -Dpackaging=zip -Dclassifier=sources

}

if [ -z ${INSTALL_JHDF5-x} ];
then
    echo "Skipped installing jhdf5 for maven to compile Piper"
else
    prepare_maven_deps
fi

download_and_install_gatk()
{
  GATK_INSTALL_DIR="gatk-protected"

  if [ ! -d "$GATK_INSTALL_DIR" ]; then
    echo "Cloning..."
    git clone https://github.com/broadgsa/gatk-protected.git $GATK_INSTALL_DIR
    check_errs $? "git clone FAILED"
  fi

  cd $GATK_INSTALL_DIR

  # Validated gatk-version
  git checkout eee94ec81f721044557f590c62aeea6880afd927
  check_errs $? "git checkout FAILED"

  mvn -T ${MAVEN_NUM_THREADS} package
  check_errs $? "gatk compilation FAILED"
  mvn install
}

#Load needed module for build on uppmax
module load java/sun_jdk1.7.0_25

if (( $? == 0 )); then
  echo "Used module system to load Java (sun_jdk1.7.0_25)."
else
  echo "Couldn\'t load Java using a module system - but don't worry."
  echo "As long as you have Java installed (preferably sun_jdk1.7.0_25)"
  echo "you should be find anyway."
  echo
fi


if [ -z ${COMPILE_GATK-x} ];
then
    echo "Skipped downloading and compiling of GATK"
else
    echo "########################################################"
    echo "Checking out and compiling the GATK and Queue"
    echo "########################################################"
    download_and_install_gatk
fi

echo "########################################################"
echo "Download RNA-SeQC"
echo "########################################################"

wget http://www.broadinstitute.org/cancer/cga/sites/default/files/data/tools/rnaseqc/RNA-SeQC_v1.1.7.jar --directory-prefix=resources/ --no-clobber
check_errs $? "wget RNA-SeQC FAILED"

echo "########################################################"
echo "Compile, package and install Piper"
echo "########################################################"

mvn -T ${MAVEN_NUM_THREADS} clean package

echo "########################################################"
echo "Creating Piper installable script with payload"
echo "########################################################"
which uuencode &>/dev/null

if [ $? -eq 0 ]; then
   echo "Packing with uuencode ..."
   bash src/main/resources/make_piper_installable.sh --uuencode target/piper-v*-bin.tar.bz2
else
   echo "Packing with binary mode..."
   bash src/main/resources/make_piper_installable.sh --binary target/piper-v*-bin.tar.bz2
fi

chmod +x install_piper.sh
check_errs $? "Createing Piper installable script FAILED"
if [[ -d installable ]]; then
 rm -rf installable
fi
mkdir installable && mv install_piper.sh installable/
