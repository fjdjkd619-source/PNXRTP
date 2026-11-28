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
# The pinned Terra revision targets Minecraft 1.21.5. In that mapping VillagerType.PLAINS
# is a RegistryKey<VillagerType>, while the biome-to-villager map stores VillagerType values.
# Resolve the key through the built-in registry before inserting the fallback value.
LIFECYCLE_BIOME_UTIL="$TERRA/platforms/mixin-lifecycle/src/main/java/com/dfsek/terra/lifecycle/util/LifecycleBiomeUtil.java"
[[ -f "$LIFECYCLE_BIOME_UTIL" ]] || { echo "Missing pinned Terra lifecycle biome utility" >&2; exit 1; }
grep -q 'import net.minecraft.registry.Registry;' "$LIFECYCLE_BIOME_UTIL" || { echo "Unexpected LifecycleBiomeUtil imports" >&2; exit 1; }
grep -q 'villagerMap.getOrDefault(vanilla, VillagerType.PLAINS)' "$LIFECYCLE_BIOME_UTIL" || { echo "Unexpected pinned Terra VillagerType fallback expression" >&2; exit 1; }
sed -i '/import net.minecraft.registry.Registry;/a import net.minecraft.registry.Registries;' "$LIFECYCLE_BIOME_UTIL"
sed -i 's/villagerMap.getOrDefault(vanilla, VillagerType.PLAINS)/villagerMap.getOrDefault(vanilla, Objects.requireNonNull(Registries.VILLAGER_TYPE.get(VillagerType.PLAINS)))/' "$LIFECYCLE_BIOME_UTIL"
# Build the exact Terra 6.6.1 Bukkit-common distribution consumed by the PNX adapter.
# The adapter uses org.powernukkitx:Terra:6.6.1-BETA, not Terra's internal com.dfsek.terra modules.
(cd "$TERRA" && ./gradlew --no-daemon --no-configuration-cache --no-parallel --max-workers=1 clean build --stacktrace)
mapfile -t TERRA_JARS < <(find "$TERRA/platforms/bukkit/common/build/libs" -maxdepth 1 -type f -name '*shaded.jar' | sort)
[[ "${#TERRA_JARS[@]}" -eq 1 ]] || { echo "Expected exactly one Terra 6.6.1 shaded Bukkit-common jar, found ${#TERRA_JARS[@]}" >&2; printf '%s\\n' "${TERRA_JARS[@]}" >&2; exit 1; }
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
git -C "$PNX" show --format= --binary "$PNX_PROTOCOL_COMPAT_COMMIT" > "$RUNNER_TEMP/pnx-protocol-beta8-compat.patch"
git -C "$PNX" apply --check "$RUNNER_TEMP/pnx-protocol-beta8-compat.patch"
git -C "$PNX" apply "$RUNNER_TEMP/pnx-protocol-beta8-compat.patch"
# Publish the modified PNX API to Maven Local so the adapter compiles against the patched source, not a remote snapshot.
(cd "$PNX" && ./gradlew --no-daemon --no-configuration-cache --no-parallel --max-workers=1 clean test build publishToMavenLocal --stacktrace)
# mavenLocal() is first in the adapter's repositories; both dependencies above are now the patched exact-ref builds.
(cd "$ADAPTER" && ./gradlew --no-daemon --no-configuration-cache --no-parallel --max-workers=1 clean test build --stacktrace)
echo "UPSTREAM_BUILD=PASS"
