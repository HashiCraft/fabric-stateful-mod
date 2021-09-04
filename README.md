# fabric-stateful-mod

[![](https://jitpack.io/v/hashicraft/fabric-stateful-mod.svg)](https://jitpack.io/#hashicraft/fabric-stateful-mod)


Mod that allows BlockEntites state to be automatically synchronised to the server.

To use stateful in your mod first add the following to your gradle.build file. You can replace `main-SNAPSHOT` with a commit
or a git tag.

```gradle
repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.

	maven { url 'https://jitpack.io' }
}

dependencies {
  modImplementation include('com.github.hashicraft:fabric-stateful-mod:main-SNAPSHOT')
}
```

Next you can extend your Block class with `StatefulBlock` rather than `Block`. StatefulBlock adds default methods that 
allow your entities state and data to be automatically synchronised to the server.

To sync a block's state to the server you can call `markForUpdate()` on the block entity. This will ensure that the
entities properties are sent to the server and then distributed to all clients.

```java
public class MyBlock extends StatefulBlock {
  public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
  public static final BooleanProperty POWERED = Properties.POWERED;

  public MyBlock(Settings settings) {
    super(settings);
    setDefaultState(getStateManager().getDefaultState().with(POWERED, false));
  }

  @Override
  public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
      BlockHitResult hit) {

    WasmBlockEntity blockEntity = (WasmBlockEntity) world.getBlockEntity(pos);

    if (world.isClient()) {
      // world.updateNeighborsAlways(pos, state.getBlock());

      WasmBlockClicked.EVENT.invoker().interact(blockEntity, () -> {
        executeWasmFunction(state, world, pos, player, blockEntity);

        // ensure that the state is synced
        blockEntity.markForUpdate();
      });
    }

    return ActionResult.SUCCESS;
  }

  @Override
  public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
    // pass a reference to self so that neighbors can be updated later
    return new MyBlockEntity(pos, state, this);
  }
}
```

Finally you create the MyBlockEntity class. This is a subclass of `StatefulBlockEntity`. StatefulBlockEntity contains
all the methods needed to serialize and deserialize the block's state to a NBT. To serialize a property you can annotate
the property with `@Syncable` annotation. This will allow the stored type to be serialized to the entities NBT data. It is
also possible to sync the state of a block using the `@Syncable` annotation but alos providing the name of the state property
and the type.

```java
public class MyBlockEntity extends StatefulBlockEntity {

  @Syncable
  public ArrayList<String> modules = new ArrayList<String>();
  public ArrayList<String> names = new ArrayList<String>();

  @Syncable
  public String function;

  @Syncable
  public ArrayList<String> parameters;

  @Syncable
  public String result;

  @Syncable
  public Integer redstonePower = 0;

  // Sync the class property powered to the block state.
  @Syncable(property = "powered", type = BooleanProperty.class)
  public boolean powered = false;

  public WasmBlockEntity(BlockPos pos, BlockState state) {
    super(WasmcraftMod.WASM_BLOCK_ENTITY, pos, state, null);
  }

  public MyBlockEntity(BlockPos pos, BlockState state, Block parent) {
    super(MyMod.MY_BLOCK_ENTITY, pos, state, parent);
  }
}
```

To enable synchonisation from the client to the server your mod needs to initialize this mod. You can do this by
adding the following line to your mods `onIniitialize` method.

```java
  @Override
  public void onInitialize() {
    EntityServerState.RegisterStateUpdates();
  }
```
