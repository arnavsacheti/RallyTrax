#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJ_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

KOTLIN_STDLIB="/opt/gradle-8.14.3/lib/kotlin-stdlib-2.0.21.jar"
KOTLIN_COMPILER="/opt/gradle-8.14.3/lib/kotlin-compiler-embeddable-2.0.21.jar"
KOTLIN_SCRIPT="/opt/gradle-8.14.3/lib/kotlin-script-runtime-2.0.21.jar"
KOTLIN_REFLECT="/opt/gradle-8.14.3/lib/kotlin-reflect-2.0.21.jar"

OUT="$SCRIPT_DIR/build"
mkdir -p "$OUT"

# Source files to compile (order matters: entities first, then generator, then test)
SOURCES=(
  "$SCRIPT_DIR/RoomStubs.kt"
  "$SCRIPT_DIR/TrackEntityStub.kt"
  "$PROJ_ROOT/app/src/main/java/com/rallytrax/app/data/local/entity/TrackPointEntity.kt"
  "$PROJ_ROOT/app/src/main/java/com/rallytrax/app/data/local/entity/PaceNoteEntity.kt"
  "$PROJ_ROOT/app/src/main/java/com/rallytrax/app/pacenotes/PaceNoteGenerator.kt"
  "$SCRIPT_DIR/SegmentTest.kt"
)

GRADLE_LIB="/opt/gradle-8.14.3/lib"
COMPILER_CP="$GRADLE_LIB/kotlin-compiler-embeddable-2.0.21.jar:$GRADLE_LIB/kotlin-stdlib-2.0.21.jar:$GRADLE_LIB/kotlin-reflect-2.0.21.jar:$GRADLE_LIB/kotlinx-coroutines-core-jvm-1.6.4.jar:$GRADLE_LIB/trove4j-1.0.20200330.jar:$GRADLE_LIB/kotlin-script-runtime-2.0.21.jar:$GRADLE_LIB/annotations-24.0.1.jar"

echo "Compiling..."
java -cp "$COMPILER_CP" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -classpath "$KOTLIN_STDLIB:$GRADLE_LIB/annotations-24.0.1.jar" \
  -d "$OUT" \
  -no-reflect \
  -jvm-target 17 \
  "${SOURCES[@]}" 2>&1

echo ""
echo "Running segment analysis..."
echo ""
java -cp "$OUT:$KOTLIN_STDLIB" SegmentTestKt
