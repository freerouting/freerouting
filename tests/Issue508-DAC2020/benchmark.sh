#!/bin/sh


TEST_FILTER="/home/boyne/autorouter/ACORN/tests/DAC2020_bm*.dsn"

for pathFileName in `ls $TEST_FILTER`
do
  if [ -f "$pathFileName" ]; then
    echo "Full path is $pathFileName"
    DSNfileName=$(basename "$pathFileName")    # Remove the directory path
    echo "  File name is $DSNfileName"
    baseName=$(basename "$pathFileName" .dsn)  # Remove the '.dsn' extension
    echo "    Base name is $baseName"
    baseName=$(basename "$baseName" .unrouted) # Remove the '.unrouted' extension
    echo "      Base name is $baseName"

    mkdir "$baseName"  # Create a sub-directory for the benchmark test
    cd "$baseName"
    mkdir "FRv1.9.0"   # Create a sub-sub-directory for Freerouting v1.9.0
    cd "FRv1.9.0"
    cp $pathFileName .  # Copy DSN file to local directory

    # Launch Freerouting v1.9.0:
    echo "Starting Freerouting v1.9.0 for DSN file $DSNfileName"
    java -jar /mnt/c/Users/danbo/AppData/Local/freerouting/app/freerouting-1.9.0.jar -de $DSNfileName -do $baseName.ses -mp 1000 -mt 1 > $baseName.log
    cd ../
    mkdir "FRv2.1.0"   # Create a sub-sub-directory for Freerouting v2.1.0
    cd "FRv2.1.0"
    cp $pathFileName .  # Copy DSN file to local directory

    # Launch Freerouting v2.1.0:
    echo "Starting Freerouting v2.1.0 for DSN file $DSNfileName"
    java -jar /mnt/c/Users/danbo/AppData/Local/freerouting/app/freerouting-executable-2.1.0.jar -de $DSNfileName -do $baseName.ses -mp 1000 -mt 1 --gui.enabled=false > $baseName.log
    cd ..
    cd ..
  fi  # End of if-block for $pathFileName being a regular file

done  # End of for-loop

