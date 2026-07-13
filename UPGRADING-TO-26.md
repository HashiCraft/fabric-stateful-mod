# Upgrading a Fabric mod to Minecraft 26.x

Notes from porting `fabric-stateful-mod` from **1.21.5 → 26.2**. Every mapping below was verified
against the real decompiled jar, not taken from a blog post — several widely-repeated "obvious"
renames turned out to be wrong (see [Gotchas](#gotchas-things-that-are-not-what-you-expect)).

---

## The headline: Yarn is dead

**Minecraft 26.1+ ships unobfuscated.** Mojang now publishes readable names, so Fabric stopped
maintaining Yarn mappings from that version on.

The practical consequence: **every `net.minecraft.*` import in your mod is now wrong.** This is not a
handful of compile errors, it's all of them. If your mod is still on Yarn, you migrate to Mojang
official mappings *first*, then port.

---

## Verify mappings yourself — don't trust tables (including this one)

Once Loom has resolved the new version, the decompiled jar is sitting in your Gradle cache. `javap`
answers any mapping question in seconds and is never out of date:

```bash
JAR=~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/\
minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar

# What does BlockEntity actually look like now?
javap -cp "$JAR" net.minecraft.world.level.block.entity.BlockEntity

# Does this class have any byte-array method at all?
javap -cp "$JAR" net.minecraft.world.level.storage.ValueOutput | grep -i byte

# Find a constant's real value
javap -cp "$JAR" -constants net.minecraft.world.level.block.Block | grep UPDATE_
```

This is the single highest-leverage habit for a port. Three of the assumptions we started with were
wrong, and `javap` caught all three in under a minute each.

---

## Build script changes

| Old | New |
|---|---|
| `id 'fabric-loom'` | `id 'net.fabricmc.fabric-loom'` |
| `mappings "net.fabricmc:yarn:..."` | **delete the line entirely** |
| `modImplementation` / `modApi` / `modCompileOnly` | plain `implementation` / `api` / `compileOnly` |
| `remapJar` task | `jar` |
| Java 21 | Java 25 |

Also required, and easy to miss:

- **Gradle 9 *removed* `project.archivesBaseName`** (not just deprecated it). If your `jar` block
  references it, the build dies during configuration, before javac ever runs:
  ```groovy
  base { archivesName = project.archives_base_name }

  jar {
      from("LICENSE") { rename { "${it}_${base.archivesName.get()}" } }
  }
  ```
- Loom 1.17 + Gradle 9.x for MC 26.2.

### Java 25 without touching JAVA_HOME

Two *separate* JVM requirements, and conflating them will waste your time:

1. **The Gradle daemon itself** must be Java 21+, or the Loom plugin won't even load:
   `Could not resolve net.fabricmc:fabric-loom … Dependency requires at least JVM runtime version 21.`
   A toolchain block **cannot** fix this — the plugin loads before any toolchain applies.
2. **The compiler** must target Java 25.

Fix (1) with `org.gradle.java.home`, and (2) with a toolchain:

```groovy
java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
    withSourcesJar()
}
```

Put `org.gradle.java.home` in **`~/.gradle/gradle.properties`**, not the repo's — a hardcoded local
JDK path in a committed file breaks CI and every other contributor:

```properties
org.gradle.java.home=C:/Program Files/Microsoft/jdk-25.0.3.9-hotspot
```

Gate the whole port on `./gradlew javaToolchains` succeeding and listing a JDK 25. Do that *before*
touching a single `.java` file, so every error afterwards is known to be yours.

Don't forget CI: `jitpack.yml`, GitHub Actions, etc. all need the JDK bump too.

---

## Gotchas: things that are *not* what you expect

These cost the most time. All verified with `javap`.

- **`Identifier` was NOT renamed to `ResourceLocation`.** It's the reverse of what everyone assumes:
  Mojang's `ResourceLocation` became **`Identifier`**. There is no `ResourceLocation` class in 26.2.
  Only the package moved: `net.minecraft.util.Identifier` → `net.minecraft.resources.Identifier`.
  The factory did change: `Identifier.of(...)` → **`Identifier.parse(...)`**.
- **`ResourceKey` uses `.identifier()`**, not `.location()`.
- **`Level.setBlock` has no 2-arg overload.** Update flags are mandatory: `setBlock(pos, state, Block.UPDATE_ALL)`.
- **`Level.updateNeighborsAt` gained a third `Orientation` arg** — *but* `ServerLevel` still has the
  2-arg overload. If you already hold a `ServerLevel`, nothing changes. Check the concrete type
  before contorting the call.
- **`ServerPlayer.getServer()` is gone.** Use `player.level().getServer()`.

---

## The block entity serialization rewrite

**This is the part that isn't a rename, and it will bite you.** Budget real time for it.

`BlockEntity` no longer saves through `CompoundTag`. It uses `ValueInput`/`ValueOutput`:

```java
// before (1.21.5)                          // after (26.2)
readNbt(NbtCompound, WrapperLookup)         loadAdditional(ValueInput)
writeNbt(NbtCompound, WrapperLookup)        saveAdditional(ValueOutput)
toInitialChunkDataNbt(WrapperLookup)        getUpdateTag(HolderLookup.Provider)
createNbt(lookup)                           saveCustomOnly(HolderLookup.Provider)
toUpdatePacket()                            getUpdatePacket()
```

Note the registry-lookup parameter **disappears entirely** from load/save (`ValueInput` carries it
internally via `input.lookup()`).

### The trap: ValueOutput has almost no primitives

`ValueInput`/`ValueOutput` support boolean/byte/short/int/long/float/double/String — and **`int[]` is
the only array type.** There is no `putByteArray`/`getByteArray`.

If your block entity persists a `byte[]` (ours did — a JSON blob), there is no like-for-like
replacement. Use the generic codec methods with a `Codec<byte[]>` that Minecraft already ships:

```java
@Override
protected void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    output.store("serverState", ExtraCodecs.BASE64_STRING, this.serverState.toBytes());
}

@Override
protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    input.read("serverState", ExtraCodecs.BASE64_STRING)   // -> Optional<byte[]>
         .ifPresent(this::applyStateBytes);
}
```

**This changes the on-disk format** (byte-array tag → base64 string), so state saved by an older
world won't load — it resets to default rather than crashing. Decide whether you can accept that, and
put it in the release notes if so.

### Disk and network no longer share a type

The subtle structural consequence, and the thing most likely to produce a confusing port:

- **Disk** (`loadAdditional`/`saveAdditional`) → `ValueInput`/`ValueOutput`, **no byte arrays**
- **Network** (`getUpdateTag`) → still a real `CompoundTag`, which **does** still have
  `putByteArray`/`getByteArray`

If you have a helper serving *both* paths (we had `toClientTag`/`fromClientTag` doing double duty),
**it must split in two.** Keep the `CompoundTag` version for networking and add a separate
`ValueInput`/`ValueOutput` pair for disk, factoring the shared decode logic into one helper so the
two paths can't drift.

---

## Verified rename tables

### Classes

| Yarn | Mojang official (26.2) |
|---|---|
| `block.Block` / `block.BlockState` | `world.level.block.Block` / `world.level.block.state.BlockState` |
| `block.BlockWithEntity` | `world.level.block.BaseEntityBlock` |
| `block.entity.{BlockEntity,BlockEntityType,BlockEntityTicker}` | `world.level.block.entity.*` (names unchanged) |
| `AbstractBlock.Settings` (ctor arg) | `BlockBehaviour.Properties` |
| `world.World` / `server.world.ServerWorld` | `world.level.Level` / `server.level.ServerLevel` |
| `util.math.BlockPos` | `core.BlockPos` |
| `nbt.NbtCompound` | `nbt.CompoundTag` |
| `util.Identifier` | `resources.Identifier` *(package only)* |
| `registry.RegistryKey` | `resources.ResourceKey` |
| `registry.RegistryWrapper.WrapperLookup` | `core.HolderLookup.Provider` |
| `state.property.BooleanProperty` | `world.level.block.state.properties.BooleanProperty` |
| `state.property.IntProperty` | `…properties.IntegerProperty` *(Int → Integer)* |
| `network.listener.ClientPlayPacketListener` | `network.protocol.game.ClientGamePacketListener` |
| `network.packet.Packet` | `network.protocol.Packet` |
| `…s2c.play.BlockEntityUpdateS2CPacket` | `network.protocol.game.ClientboundBlockEntityDataPacket` |
| `network.RegistryByteBuf` | `network.RegistryFriendlyByteBuf` |
| `network.codec.PacketCodec` / `PacketCodecs` | `network.codec.StreamCodec` / `ByteBufCodecs` |
| `network.packet.CustomPayload` | `network.protocol.common.custom.CustomPacketPayload` |
| `CustomPayload.Id<T>` | `CustomPacketPayload.Type<T>` |
| — *(new)* | `world.level.storage.ValueInput` / `ValueOutput`, `util.ExtraCodecs` |

### Methods

| Yarn | Mojang (26.2) |
|---|---|
| `markDirty()` | `setChanged()` |
| `getWorld()` / `getPos()` / `getCachedState()` | `getLevel()` / `getBlockPos()` / `getBlockState()` |
| protected field `world` | protected field `level` |
| `World.isClient()` | `Level.isClientSide()` |
| `updateListeners(...)` | `sendBlockUpdated(...)` |
| `World.getRegistryKey()` | `Level.dimension()` |
| `RegistryKey.getValue()` | `ResourceKey.identifier()` |
| `Identifier.of(s)` | `Identifier.parse(s)` |
| `Block.NOTIFY_ALL` | `Block.UPDATE_ALL` (== 3) |
| `setBlockState(pos, state)` | `setBlock(pos, state, flags)` |
| `BlockState.with(p, v)` | `BlockState.setValue(p, v)` |
| `BooleanProperty.of` / `IntProperty.of` | `BooleanProperty.create` / `IntegerProperty.create` |
| `MinecraftServer.getWorlds()` | `getAllLevels()` |
| `ServerPlayer.getServer()` | `player.level().getServer()` |
| `getCodec()` / `createCodec(fn)` | `codec()` / `simpleCodec(fn)` |
| `createBlockEntity(...)` | `newBlockEntity(...)` |
| `PacketCodec.xmap(a, b)` | `StreamCodec.map(a, b)` |
| `CustomPayload.getId()` | `CustomPacketPayload.type()` |

### Fabric API

| Old | New |
|---|---|
| `PayloadTypeRegistry.playC2S()` / `playS2C()` | `serverboundPlay()` / `clientboundPlay()` |
| `ClientPlayNetworking.createC2SPacket()` | `createServerboundPacket()` |
| `ServerPlayNetworking.createS2CPacket()` | `createClientboundPacket()` |
| `C2SPlayChannelEvents` / `S2CPlayChannelEvents` | `ServerboundPlayChannelEvents` / `ClientboundPlayChannelEvents` |
| `FabricRegistryBuilder.createSimple()` | `create()` |
| `ExtendedScreenHandlerFactory` | `ExtendedMenuProvider` (package `menu.v1`) |

`ClientPlayNetworking.send(payload)` and `ServerPlayNetworking.registerGlobalReceiver` are unchanged.

General pattern: World→Level, ScreenHandler→Menu, C2S/S2C→Serverbound/Clientbound.

---

## Testing: Minecraft now fights back

Two failures you'll hit the moment unit tests touch any MC class:

1. **`IllegalArgumentException: Not bootstrapped`** — `BlockEntity`'s *static initializer* reads
   `BuiltInRegistries`. Merely loading the class throws. Fixable with
   `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();` in a `@BeforeAll`.
2. **`IllegalStateException: This registry can't create intrusive holders`** — and this one you
   *can't* work around: `new BlockEntityType<>(...)` only works while the registry is open during
   bootstrap. You cannot construct one in a plain unit test. (26.2 also made the `BlockEntity`
   constructor validate its `BlockState` against its type, so passing `null` no longer works either.)

**The lesson: don't let a `BlockEntity` superclass into your unit tests.** If the logic you want to
test is plain Java (reflection, serialization, validation), extract it into a Minecraft-free class
and test *that*. We pulled our `@Syncable` reflection into a standalone `SyncableFields` helper that
`StatefulBlockEntity` delegates to; the test now exercises the real shipped code path with zero
Minecraft on the classpath, and runs instantly.

---

## Suggested order

1. Fix `build.gradle` + `gradle.properties`. **Gate on `./gradlew javaToolchains`.**
2. `./gradlew tasks` — confirm dependencies resolve before writing any Java.
3. `./gradlew genSources` — decompile for reference.
4. Port the trivial files (imports only).
5. Port the API-heavy files.
6. Do the block-entity serialization rewrite **last** — it's the only real design work.
7. `./gradlew compileJava` as the fast iteration loop, then `test`, then `build`.
8. Update `fabric.mod.json` and CI config.

A note on **Loom's `migrateMappings` task**: it remaps *names*. It cannot do the serialization
rewrite, so you'll hand-write the hard part regardless. For a small mod, porting by hand with `javap`
on standby is faster than setting the task up.
