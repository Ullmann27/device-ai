#!/bin/sh
set -eu
/tools/kotlin/bin/kotlinc /work/src/Main.kt -include-runtime -d /scratch/main.jar
/tools/jdk/bin/java -jar /scratch/main.jar
