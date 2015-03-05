#!/bin/bash -l

PIPER_NAME_PREFIX="piper-v"

print_usage(){
    echo "########################################################"
    echo "# Utility script to install Piper"
    echo "# Piper"
    echo "# Usage:"
    echo "#  /install_piper.sh <install location> <institution_code> <library_path>"
    echo "# Default install location is $HOME/Bin/Piper"
    echo "########################################################"
}

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

if [[ $# -lt 2 ]]; then
    print_usage
    exit 1
fi

INSTALL_PREFIX=${1:-"${HOME}/bin/Piper"}
DIST=${2:-upsala}
LIB_PATH=${3:-/sw/apps/build/slurm-drmaa/default/lib}

# Red text - making people notice instructions since pre-school!
coloured_text() {
  echo -e "\e[1;31m ${1} \e[0m"
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

install_piper(){
    # Prepare directory structure
    install -d $INSTALL_PREFIX/Piper
    if [ ! -d ${PIPER_NAME_PREFIX}* ]; then
      coloured_text "Can't find Piper binary distribution to work! Check Piper build process"
      exit 1
    fi

    if [[ 1 -lt $(ls -1d ${PIPER_NAME_PREFIX}*|wc -l) ]]; then
        coloured_text "There are more than one directories/files start with ${PIPER_NAME_PREFIX}!"
        coloured_text "Installation quited to avoid troubles, please put ${0} alone in a directory and try running it from there again."
        exit 1
    fi

    BINARY_PACK=$(ls -1d ${PIPER_NAME_PREFIX}*)
    echo "Installing ${BINARY_PACK} to ${INSTALL_PREFIX}"
    cp -rv $BINARY_PACK ${INSTALL_PREFIX}/Piper/
    cd $INSTALL_PREFIX
    ln -sfn ${BINARY_PACK} ./Piper/current
    ln -snf ./Piper/current/bin bin
    ln -snf ./Piper/current/lib lib

    echo "########################################################"
    echo "Creating symolic links to workflowsls , qscripts and globalConfig for ${DIST}"
    echo "########################################################"
    ln -snf ./Piper/current/config/${DIST}/workflows ./workflows
    check_errs $? "linking workflows failed."
    ln -snf ./Piper/current/qscripts ./
    check_errs $? "linkking qscripts failed."

    echo $PWD
    ln -snf ${INSTALL_PREFIX}/Piper/current/config/${DIST}/globalConfig.sh ${INSTALL_PREFIX}/Piper/current/config/${DIST}/uppmax_global_config.xml ./workflows/
    check_errs $? "linking globalConfig.sh failed."

    ln -snf ${INSTALL_PREFIX}/Piper/current/config/${DIST}/globalConfig.xml ./workflows/
    check_errs $? "linking globalConfig.xml failed."
    cd -

}

print_guide(){
   echo "########################################################"
   echo "Piper successfully installed to ${INSTALL_PREFIX}"
   echo "Add it to your PATH by running:"
   coloured_text "  PATH=\$PATH:$INSTALL_PREFIX/bin"
   echo "And verify it's been installed by running:"
   coloured_text "  piper -S qscripts/examples/NonUppmaxableTestScript.scala --help"
   echo "This should show a list of available options if Piper"
   echo "has been installed correctly."
   echo "To access to workflows from any folder, add them to your path:"
   coloured_text " PATH=\$PATH:$INSTALL_PREFIX/workflows/"
   echo "If you want to run on a cluster you need to make sure "
   echo "that the appropriate libraries are available on the path."
   echo "On Uppmax this is done by exporting: "
   coloured_text "  export LD_LIBRARY_PATH=/sw/apps/build/slurm-drmaa/default/lib/:$LD_LIBRARY_PATH"
   echo "On other systems you need to figure out the path to the slurm-drmaa libraries, "
   echo "and add it in the same way."
   echo "To make sure you can always reach piper, add run following commands to "
   echo "add it to you .bashrc: "
   coloured_text "  echo \"\" >>  ~/.bashrc"
   coloured_text "  echo \"# Piper related variables and setup\" >>  ~/.bashrc"
   coloured_text "  echo 'PATH=\$PATH:$INSTALL_PREFIX/bin' >> ~/.bashrc"
   coloured_text "  echo 'PATH=\$PATH:$INSTALL_PREFIX/workflows' >> ~/.bashrc"
   coloured_text "  echo 'export LD_LIBRARY_PATH=${LIB_PATH}:\$LD_LIBRARY_PATH' >> ~/.bashrc"
   coloured_text "  echo 'export PIPER_GLOB_CONF=$INSTALL_PREFIX/workflows/globalConfig.sh' >> ~/.bashrc"
   echo "########################################################"
}

uuencode=0
binary=1

untar_payload(){
	match=$(grep --text --line-number '^PAYLOAD:$' $0 | cut -d ':' -f 1)
	payload_start=$((match + 1))
	if [[ $binary -ne 0 ]]; then
		tail -n +$payload_start $0 | tar -xjvf -
	fi
	if [[ $uuencode -ne 0 ]]; then
		tail -n +$payload_start $0 | uudecode | tar -xjvf -
	fi
}

read -p "Install files to ${INSTALL_PREFIX} [Y/N]? " ans
if [[ "${ans:0:1}" == "Y" ||  "${ans:0:1}" == "y" ]]; then
	untar_payload
	install_piper
	check_errs $? "Installing piper failed."
	print_guide
	rm -r ${PIPER_NAME_PREFIX}*

fi
exit 0

