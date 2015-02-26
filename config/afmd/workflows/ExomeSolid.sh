#!/bin/bash -l
#SBATCH -A a2009002
#SBATCH -p core
#SBATCH -n 1
#SBATCH -t 120:00:00
#SBATCH -J piper
#SBATCH -o pipeline-%j.out
#SBATCH -e pipeline-%j.error

function usage {
   echo "Usage: ./workflows/ExomeSolid.sh --xml_input <setup.xml> --intervals <intervals> --bed_intervals <intervals.bed>  [--alignments_only] [--run]"
}

# Loads the global settings. To change them open globalConfig.sh and rewrite them.
source $PIPER_GLOB_CONF

#---------------------------------------------
# Create output directories
#---------------------------------------------
if [ ! -d "${LOGS}" ]; then
   mkdir -p ${LOGS}
fi

# Timestamp: YearMonthDayHourMinute
TIMESTAMP=$(date +"%y%m%d%H%M")

# Record how this script is called.
echo "# Run Time YearMonthDayHourMinute: $TIMESTAMP" >> ${LOGS}/bash_run_commands.log
echo "$0 $@" >> ${LOGS}/bash_run_commands.log
echo "######################" >> ${LOGS}/bash_run_commands.log

#---------------------------------------------
# Parse the arguments
#---------------------------------------------
PIPELINE_SETUP=""
RUN=""
ONLY_ALIGMENTS="--create_delivery"
INTERVALS=""
BED_INTERVALS=""
# For additional arguments to novoalignCS/MPI, i.e. number of reads to align
# This can also be used to pass in runtime argument for any other commandline programs.
TRIAL_ARGUMENTS=""

while :
    do
       case $1 in
           -h | --help | -\?)
               usage
               exit 0
               ;;
           -s | --xml_input)
               PIPELINE_SETUP=$2
               shift 2
               ;;
           -r | --run)
               RUN="-run"
               shift
               ;;
           -a | --alignments_only)
               ONLY_ALIGMENTS="--alignment_and_qc"
               shift
               ;; 
           -i | --intervals)
               INTERVALS=$2
               shift 2
               ;;
           -b | --bed_intervals)
               BED_INTERVALS=$2
               shift 2
               ;;
            -t | --trial_arguments)
               TRIAL_ARGUMENTS=$2
               shift 2
               ;;
           -*)
               echo "WARN: Unknown option (ignored): $1" >&2
               shift
               ;;
           *)  # no more options. Stop while loop
               break
               ;;
       esac
   done

if [ ! "$PIPELINE_SETUP" ]; then
   usage
   exit 1
fi

if [ ! "$INTERVALS" ]; then
   usage
   exit 1
fi
# We also need the correct java engine and R version
# module load java/sun_jdk1.7.0_25
# module load R/2.15.0

#---------------------------------------------
# Run the qscript
#---------------------------------------------
piper -S ${SCRIPTS_DIR}/QPipe.scala \
	      --xml_input ${PIPELINE_SETUP} \
	      --isExome \
	      --gatk_interval_file ${INTERVALS} \
	      --bed_interval_file ${BED_INTERVALS} \
	      --global_config ${_THIS_SCRIPT_LOCATION}/uppmax_global_config.xml \
	      --number_of_threads 20 \
	      --scatter_gather 18 \
	      --disableJobReport \
	      -jobRunner ${JOB_RUNNER} \
	      -jobNative "${JOB_NATIVE_ARGS}" \
	      --job_walltime 345600 \
	      --trial_arguments "${TRIAL_ARGUMENTS}" \
	      ${RUN} ${ONLY_ALIGMENTS} ${DEBUG} 2>&1 | tee -a ${LOGS}/exome.${TIMESTAMP}.log

# Perform final clean up
final_clean_up
