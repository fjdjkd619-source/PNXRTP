#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
usage(){ echo "Usage: $0 --pnx DIR --terra DIR --adapter DIR [--apply]" >&2; exit 2; }
PNX=""; TERRA=""; ADAPTER=""; PNX_REF="${PNX_EXPECTED_REF:-c57499365737654eca32df008751ef6bca5af142}"; APPLY=0
while (($#)); do case "$1" in
  --pnx) PNX="${2:-}"; shift 2;; --terra) TERRA="${2:-}"; shift 2;; --adapter) ADAPTER="${2:-}"; shift 2;; --pnx-ref) PNX_REF="${2:-}"; shift 2;; --apply) APPLY=1; shift;; *) usage;; esac; done
[[ -n "$PNX" && -n "$TERRA" && -n "$ADAPTER" ]] || usage
for dir in "$PNX" "$TERRA" "$ADAPTER"; do [[ -x "$dir/gradlew" ]] || { echo "Missing Gradle wrapper in $dir" >&2; exit 1; }; done
if (( APPLY )); then PNX_EXPECTED_REF="$PNX_REF" "$ROOT/apply-stages1-36.sh" --pnx "$PNX" --terra "$TERRA" --adapter "$ADAPTER" --pnx-ref "$PNX_REF"; fi
for spec in "$PNX:$PNX_REF:PowerNukkitX" "$TERRA:016961c19c41c2501737599175937ae40699beb1:Terra" "$ADAPTER:74318b4a1eae95322550f12d5eb8a76d92a93731:PNX-Terra"; do
  IFS=: read -r dir expected name <<< "$spec"
  actual="$(git -C "$dir" rev-parse HEAD)"
  [[ "$actual" == "$expected" ]] || { echo "$name ref mismatch; expected $expected got $actual" >&2; exit 1; }
done
# Terra's pinned monorepo auto-includes the optional Allay platform, whose
# org.allaymc:stateupdater:master-SNAPSHOT dependency is not published at the
# pinned revision. Exclude only that unrelated platform; retain the full Gradle
# build for Terra API, implementation, addons, Bukkit/NMS and Minestom projects.
grep -q 'includeImmediateChildren(file("platforms")' "$TERRA/settings.gradle.kts" || {
  echo "Unexpected Terra settings.gradle.kts layout; refusing to alter the build graph" >&2
  exit 1
}
grep -q 'type == "platform" && it.name == "allay"' "$TERRA/settings.gradle.kts" || \
  sed -i '/if (!it.isDirectory || !File(it, "build.gradle.kts").exists()) return@forEach/a\        if (type == "platform" && it.name == "allay") return@forEach' "$TERRA/settings.gradle.kts"
# Prefer Google's Maven Central mirror in CI to avoid transient Maven Central HTTP 429s.
MAVEN_CENTRAL_MIRROR='https://maven-central.storage-download.googleapis.com/maven2/'
for settings_file in "$TERRA/settings.gradle.kts" "$PNX/settings.gradle.kts"; do
  [[ -f "$settings_file" ]] || { echo "Missing Gradle settings file: $settings_file" >&2; exit 1; }
  if ! grep -q "$MAVEN_CENTRAL_MIRROR" "$settings_file"; then
    python3 - "$settings_file" "$MAVEN_CENTRAL_MIRROR" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
mirror = sys.argv[2]
content = path.read_text()
content = content.replace("repositories {", 'repositories {\n        maven("' + mirror + '")')
path.write_text(content)
PY
  fi
done
TERRA_BUILDSRC="$TERRA/buildSrc/build.gradle.kts"
[[ -f "$TERRA_BUILDSRC" ]] || { echo "Missing Terra buildSrc build file" >&2; exit 1; }
if ! grep -q "$MAVEN_CENTRAL_MIRROR" "$TERRA_BUILDSRC"; then
  python3 - "$TERRA_BUILDSRC" "$MAVEN_CENTRAL_MIRROR" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
mirror = sys.argv[2]
content = path.read_text()
content = content.replace("repositories {", 'repositories {\n    maven("' + mirror + '")')
path.write_text(content)
PY
fi
# The pinned Terra revision targets Minecraft 1.21.5. In that mapping VillagerType.PLAINS
# is a RegistryKey<VillagerType>, while the biome-to-villager map stores VillagerType values.
# Resolve the key through the built-in registry before inserting the fallback value.
LIFECYCLE_BIOME_UTIL="$TERRA/platforms/mixin-lifecycle/src/main/java/com/dfsek/terra/lifecycle/util/LifecycleBiomeUtil.java"
[[ -f "$LIFECYCLE_BIOME_UTIL" ]] || { echo "Missing pinned Terra lifecycle biome utility" >&2; exit 1; }
grep -q 'import net.minecraft.registry.Registry;' "$LIFECYCLE_BIOME_UTIL" || { echo "Unexpected LifecycleBiomeUtil imports" >&2; exit 1; }
if grep -q 'villagerMap.getOrDefault(vanilla, VillagerType.PLAINS)' "$LIFECYCLE_BIOME_UTIL"; then
  grep -q 'import net.minecraft.registry.Registries;' "$LIFECYCLE_BIOME_UTIL" || \
    sed -i '/import net.minecraft.registry.Registry;/a import net.minecraft.registry.Registries;' "$LIFECYCLE_BIOME_UTIL"
  sed -i 's/villagerMap.getOrDefault(vanilla, VillagerType.PLAINS)/villagerMap.getOrDefault(vanilla, Objects.requireNonNull(Registries.VILLAGER_TYPE.get(VillagerType.PLAINS)))/' "$LIFECYCLE_BIOME_UTIL"
elif grep -q 'villagerMap.getOrDefault(vanilla, Objects.requireNonNull(Registries.VILLAGER_TYPE.get(VillagerType.PLAINS)))' "$LIFECYCLE_BIOME_UTIL"; then
  grep -q 'import net.minecraft.registry.Registries;' "$LIFECYCLE_BIOME_UTIL" || { echo "Missing Registries import after compatibility patch" >&2; exit 1; }
else
  echo "Unexpected pinned Terra VillagerType fallback expression" >&2
  exit 1
fi
# Build the exact Terra 6.6.1 Bukkit-common distribution consumed by the PNX adapter.
# The adapter uses org.powernukkitx:Terra:6.6.1-BETA, not Terra's internal com.dfsek.terra modules.
(cd "$TERRA" && ./gradlew --no-daemon --no-configuration-cache --no-parallel --max-workers=1 clean build -x test --stacktrace)
mapfile -t TERRA_JARS < <(find "$TERRA/platforms/bukkit/common/build/libs" -maxdepth 1 -type f -name '*shaded.jar' | sort)
[[ "${#TERRA_JARS[@]}" -eq 1 ]] || { echo "Expected exactly one Terra 6.6.1 shaded Bukkit-common jar, found ${#TERRA_JARS[@]}" >&2; printf '%s\n' "${TERRA_JARS[@]}" >&2; exit 1; }
TERRA_M2="$HOME/.m2/repository/org/powernukkitx/Terra/6.6.1-BETA"
mkdir -p "$TERRA_M2"
cp "${TERRA_JARS[0]}" "$TERRA_M2/Terra-6.6.1-BETA.jar"
cat > "$TERRA_M2/Terra-6.6.1-BETA.pom" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.powernukkitx</groupId>
  <artifactId>Terra</artifactId>
  <version>6.6.1-BETA</version>
  <packaging>jar</packaging>
</project>
POM
# The pinned PNX 3.0.6 source predates the official Beta8 camera API migration,
# but its floating Beta7-Debug-SNAPSHOT dependency now resolves without the legacy
# camera instruction classes. Apply the exact upstream PNX protocol-upgrade commit
# (camera API migration + matching Beta8 dependency pin) to this isolated build tree.
PNX_PROTOCOL_COMPAT_COMMIT="ff86b15d18e3699459c7913eb1ebec16521b7ad0"
git -C "$PNX" fetch --no-tags origin "$PNX_PROTOCOL_COMPAT_COMMIT"
# Replace only the six upstream-owned protocol/camera files with their exact contents
# from the official Beta8 migration commit. Stage 1–42 patches do not touch these files.
git -C "$PNX" checkout "$PNX_PROTOCOL_COMPAT_COMMIT" -- gradle/libs.versions.toml src/main/java/org/powernukkitx/camera/CameraProgressKeyFrame.java src/main/java/org/powernukkitx/camera/CameraRotationKeyFrame.java src/main/java/org/powernukkitx/camera/PlayerCamera.java src/main/java/org/powernukkitx/command/defaults/CameraCommand.java src/test/java/org/powernukkitx/utils/DefaultCameraPresetsTest.java
# Cloudburst Network's NetherNet child now accepts a provenance-bearing remote address.
# Keep the transport identity regression test aligned with that API while preserving
# the same signaled peer and local endpoint used by the test.
python3 - "$PNX/src/test/java/org/powernukkitx/network/TransportIdentityBindingTest.java" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
content = path.read_text()
content = content.replace(
    "import org.cloudburstmc.netty.channel.nethernet.NetherNetChildChannel;\n",
    "import org.cloudburstmc.netty.channel.nethernet.AddressSource;\n"
    "import org.cloudburstmc.netty.channel.nethernet.NetherNetChildChannel;\n"
    "import org.cloudburstmc.netty.channel.nethernet.SourcedAddress;\n"
)
old = '''new NetherNetChildChannel(null, null,
            new InetSocketAddress("127.0.0.1", 51234), new InetSocketAddress("127.0.0.1", 19132));'''
new = '''new NetherNetChildChannel(null, null,
            new SourcedAddress(new InetSocketAddress("127.0.0.1", 51234), AddressSource.SIGNALING, null),
            new InetSocketAddress("127.0.0.1", 19132));'''
if old in content:
    content = content.replace(old, new)
elif new not in content:
    raise SystemExit("Unexpected NetherNetChildChannel test constructor; refusing unsafe rewrite")
if "import org.cloudburstmc.netty.channel.nethernet.SourcedAddress;" not in content:
    raise SystemExit("SourcedAddress import missing after compatibility rewrite")
path.write_text(content)
PY
# Keep snapshot dependencies on the repository's moving snapshot coordinates so Gradle
# resolves the currently published timestamp rather than an expired timestamped build.
sed -i 's/2.0.0.CR6-20261026.130552-16/2.0.0.CR6-SNAPSHOT/g' "$PNX/gradle/libs.versions.toml"
sed -i 's/0.24.6.0-20261023.142230-9/0.24.6.0-SNAPSHOT/g' "$PNX/gradle/libs.versions.toml"
# Add the current PNX-compatible RakNet transport version, superseding the obsolete
# 1.1.0.CR4-SNAPSHOT transitive edge from the protocol snapshot.
grep -q 'netty-transport-raknet:2.0.0.CR4-20260922.202324-5' "$PNX/build.gradle.kts" || \
  sed -i '/api(libs.bundles.nethernet)/a\    api("org.cloudburstmc.netty:netty-transport-raknet:2.0.0.CR4-20260922.202324-5")' "$PNX/build.gradle.kts"
# These legacy transitive artifacts are published outside Maven Central.
grep -q 'repo.minebench.de' "$PNX/settings.gradle.kts" || \
  sed -i '/maven("https:\/\/repo.powernukkitx.org\/releases")/a\        maven("https://repo.minebench.de/")' "$PNX/settings.gradle.kts"
# Cloudburst's immutable 2.0 release replaces the no-longer-published 2.0-SNAPSHOT.
grep -q 'force("org.cloudburstmc.math:immutable:2.0")' "$PNX/build.gradle.kts" || \
  sed -i '/cacheChangingModulesFor(10, TimeUnit.MINUTES)/a\        force("org.cloudburstmc.math:immutable:2.0")' "$PNX/build.gradle.kts"
# Publish the modified PNX API to Maven Local so the adapter compiles against the patched source, not a remote snapshot.
(cd "$PNX" && ./gradlew --no-daemon --no-configuration-cache --no-parallel --max-workers=1 clean build publishToMavenLocal -x test --stacktrace)
# mavenLocal() is first in the adapter's repositories; both dependencies above are now the patched exact-ref builds.
# The adapter resolves the PNX Maven-local POM transitively. Mirror the upstream
# snapshot and legacy native repositories here as well; repositories declared in
# the PNX checkout do not carry over to this independent Gradle build.
ADAPTER_BUILD="$ADAPTER/build.gradle.kts"
[[ -f "$ADAPTER_BUILD" ]] || { echo "Missing adapter build file" >&2; exit 1; }
python3 - "$ADAPTER_BUILD" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
content = path.read_text()
repos = [
    'maven("https://maven-central.storage-download.googleapis.com/maven2/")',
    'maven("https://repo.opencollab.dev/maven-snapshots/") { mavenContent { snapshotsOnly() } }',
    'maven("https://repo.minebench.de/")',
]
anchor = "repositories {"
if anchor not in content:
    raise SystemExit("Adapter repositories block not found; refusing to rewrite")
for repo in repos:
    if repo not in content:
        content = content.replace(anchor, anchor + "\n    " + repo, 1)
force = """
configurations.configureEach {
    resolutionStrategy.force("org.cloudburstmc.math:immutable:2.0-20220327.162752-1")
}
"""
if 'org.cloudburstmc.math:immutable:2.0-20220327.162752-1' not in content:
    content += "\n" + force
path.write_text(content)
PY
(cd "$ADAPTER" && ./gradlew --no-daemon --no-configuration-cache --no-parallel --max-workers=1 clean build -x test --stacktrace)
echo "UPSTREAM_BUILD=PASS"
