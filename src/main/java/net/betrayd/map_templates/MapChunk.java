package net.betrayd.map_templates;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;

public final class MapChunk {
    private static final Codec<PalettedContainer<BlockState>> BLOCK_CODEC = PalettedContainer
            .createPalettedContainerCodec(Block.STATE_IDS, BlockState.CODEC,
                    PalettedContainer.PaletteProvider.BLOCK_STATE, Blocks.AIR.getDefaultState());    
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MapChunk.class);

    private final ChunkSectionPos pos;

    private PalettedContainer<BlockState> container = new PalettedContainer<>(Block.STATE_IDS, Blocks.AIR.getDefaultState(), PalettedContainer.PaletteProvider.BLOCK_STATE);
    // private final List<MapEntity> entities = new ArrayList<>();
    // private final Map<BlockPos, NbtCompound> blockEntities = new HashMap<>();
    private final Long2ObjectMap<NbtCompound> blockEntities = new Long2ObjectOpenHashMap<>();

    MapChunk(ChunkSectionPos pos) {
        this.pos = pos;
    }

    /**
     * Set the block at a particular position.
     * 
     * @param x     Chunk-local X
     * @param y     Chunk-local Y
     * @param z     Chunk-local Z
     * @param state Block state to set.
     */
    public void set(int x, int y, int z, BlockState state) {
        this.container.set(x, y, z, state);
    }

    /**
     * Set the block at a particular position.
     * 
     * @param pos   Position in local chunk space.
     * @param state Block state to set.
     */
    public final void set(Vec3i pos, BlockState state) {
        set(pos.getX(), pos.getY(), pos.getZ(), state);
    }

    /**
     * Get the block at a particular position.
     * 
     * @param x Chunk-local X
     * @param y Chunk-local Y
     * @param z Chunk-local Z
     * @return The block state at this position.
     */
    public BlockState get(int x, int y, int z) {
        return this.container.get(x, y, z);
    }

    /**
     * Get the block at a particular position.
     * 
     * @param pos Position in local chunk space.
     * @return The block state at this position.
     */
    public final BlockState get(Vec3i pos) {
        return get(pos.getX(), pos.getY(), pos.getZ());
    }

    // /**
    //  * Adds an entity to this chunk.
    //  * <p>
    //  * The position of the entity must be relative to the map template.
    //  *
    //  * @param entity The entity to add.
    //  * @param position The entity position relative to the map.
    //  */
    // public void addEntity(Entity entity, Vec3d position) {
    //     var mapEntity = MapEntity.fromEntity(entity, position);
    //     if (mapEntity != null) {
    //         this.entities.add(mapEntity);
    //     }
    // }

    // public void addEntity(MapEntity entity) {
    //     this.entities.add(entity);
    // }

    public ChunkSectionPos getPos() {
        return this.pos;
    }

    // /**
    //  * Returns the entities in this chunk.
    //  *
    //  * @return The entities in this chunk.
    //  */
    // public List<MapEntity> getEntities() {
    //     return this.entities;
    // }

    public Long2ObjectMap<NbtCompound> getBlockEntities() {
        return blockEntities;
    }

    /**
     * Get a stream of block entity entries, where the keys are
     * <code>BlockPos</code> objects instead of longs.
     * 
     * @return Block entity stream.
     */
    public Stream<Map.Entry<BlockPos, NbtCompound>> streamBlockEntities() {
        return blockEntities.long2ObjectEntrySet().stream().map(e -> {
            BlockPos localPos = BlockPos.fromLong(e.getLongKey());
            return new AbstractMap.SimpleEntry<>(localPos, e.getValue());
        });
    }

    /**
     * Get the block entity at a specific block.
     * 
     * @param pos Position of the block in local chunk space.
     * @return The block entity NBT, or <code>null</code> if this block does not
     *         have an entity.
     */
    @Nullable
    public final NbtCompound getBlockEntity(Vec3i pos) {
        return getBlockEntity(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Get the block entity at a specific block.
     * 
     * @param x Chunk-relative X
     * @param y Chunk-relative Y
     * @param z Chunk-relative Z
     * @return The block entity NBT, or <code>null</code> if this block does not
     *         have an entity.
     */
    @Nullable
    public NbtCompound getBlockEntity(int x, int y, int z) {
        return blockEntities.get(BlockPos.asLong(x, y, z));
    }

    /**
     * Put a block entity at a specific block in the chunk.
     * 
     * @param pos Position of the block in local chunk space
     * @param nbt Block entity NBT.
     * @return The previous block entity occupying this block, if any.
     * @throws IndexOutOfBoundsException If the desired coordinates are outside the
     *                                   bounds of this chunk.
     */
    @Nullable
    public final NbtCompound putBlockEntity(Vec3i pos, NbtCompound nbt) {
        return putBlockEntity(pos.getX(), pos.getY(), pos.getZ(), nbt);
    }

    /**
     * Put a block entity at a specific block in the chunk.
     * 
     * @param x   Chunk-relative X
     * @param y   Chunk-relative Y
     * @param z   Chunk-relative Z
     * @param nbt Block entity NBT.
     * @return The previous block entity occupying this block, if any.
     * @throws IndexOutOfBoundsException If the desired coordinates are outside the
     *                                   bounds of this chunk.
     */
    @Nullable
    public NbtCompound putBlockEntity(int x, int y, int z, NbtCompound nbt) throws IndexOutOfBoundsException {
        if (x < 0 || x >= 16) throw new IndexOutOfBoundsException(x);
        if (y < 0 || y >= 16) throw new IndexOutOfBoundsException(y);
        if (z < 0 || z >= 16) throw new IndexOutOfBoundsException(z);

        nbt = nbt.copy();
        nbt.putInt("x", pos.getX());
        nbt.putInt("y", pos.getY());
        nbt.putInt("z", pos.getZ());

        return blockEntities.put(BlockPos.asLong(x, y, z), nbt);
    }

    public void serialize(NbtCompound nbt) {
        nbt.put("block_states", BLOCK_CODEC.encodeStart(NbtOps.INSTANCE, container).getOrThrow(false, LOGGER::error));

        if (!this.blockEntities.isEmpty()) {
            NbtList blockEntitiesList = new NbtList();
            for (var entry : this.blockEntities.long2ObjectEntrySet()) {
                BlockPos pos = BlockPos.fromLong(entry.getLongKey());
                NbtCompound ent = entry.getValue().copy();

                ent.putInt("x", pos.getX());
                ent.putInt("y", pos.getY());
                ent.putInt("z", pos.getZ());

                blockEntitiesList.add(ent);
            }

            nbt.put("block_entities", blockEntitiesList);
        }
    }

    public static MapChunk deserialize(ChunkSectionPos pos, NbtCompound nbt) {
        MapChunk chunk = new MapChunk(pos);
        var container = BLOCK_CODEC.parse(NbtOps.INSTANCE, nbt.getCompound("block_states"))
                .promotePartial(LOGGER::error).get().left();
        
        if (container.isPresent()) {
            chunk.container = container.get();
        }

        NbtList blockEntitiesList = nbt.getList("block_entities", NbtElement.COMPOUND_TYPE);
        if (blockEntitiesList != null) {
            for (NbtElement entNbt : blockEntitiesList) {

                // Copy because we don't know what will happen to the NBT after this.
                NbtCompound ent = (NbtCompound) entNbt.copy();
                int x = ent.getInt("x");
                int y = ent.getInt("y");
                int z = ent.getInt("z");

                if (x < 0 || x >= 16 || y < 0 || y >= 16 || z < 0 || z >= 16) {
                    LOGGER.error(
                            "Unable to load block entity '{}' because the saved coordinates ({}, {}, {}) are outside the chunk's bounds.",
                            ent.getString("id"), x, y, z);
                    continue;
                }

                chunk.putBlockEntity(x, y, z, nbt);
            }
        }

        return chunk;
    }

    /**
     * Load a map chunk from the blocks in a ChunkSection.
     * @param pos Coordinates of chunk.
     * @param section Chunk section to load from.
     * @return Loaded map chunk.
     */
    public static MapChunk loadFrom(ChunkSectionPos pos, ChunkSection section) {
        MapChunk chunk = new MapChunk(pos);
        chunk.container = section.getBlockStateContainer().copy();

        return chunk;
    }

    /**
     * Check if a given block is in a given chunk section.
     * 
     * @param x      Global block X.
     * @param y      Global block Y.
     * @param z      Global block Z.
     * @param chunkX Chunk section X.
     * @param chunkY Chunk section Y.
     * @param chunkZ Chunk section Z.
     * @return Whether the block is in the chunk.
     */
    public static boolean isInChunk(int x, int y, int z, int chunkX, int chunkY, int chunkZ) {
        return (chunkX << 4 <= x && x < (chunkX + 1) << 4)
                && (chunkY << 4 <= y && y < (chunkY + 1) << 4)
                && (chunkZ << 4 <= z && z < (chunkZ + 1) << 4);
    }

    /**
     * Check if a given block is in a given chunk section.
     * 
     * @param x        Global block X.
     * @param y        Global block Y.
     * @param z        Global block Z.
     * @param chunkPos Chunk section coordinates.
     * @return Whether the block is in the chunk.
     */
    public static boolean isInChunk(int x, int y, int z, ChunkSectionPos chunkPos) {
        return isInChunk(x, y, z, chunkPos.getX(), chunkPos.getY(), chunkPos.getZ());
    }
    
    /**
     * Check if a given block is in a given chunk section.
     * 
     * @param pos    Global block position.
     * @param chunkX Chunk section X.
     * @param chunkY Chunk section Y.
     * @param chunkZ Chunk section Z.
     * @return Whether the block is in the chunk.
     */
    public static boolean isInChunk(BlockPos pos, int chunkX, int chunkY, int chunkZ) {
        return isInChunk(pos.getX(), pos.getY(), pos.getZ(), chunkX, chunkY, chunkZ);
    }

    /**
     * Check if a given block is in a given chunk section.
     * 
     * @param pos      Global block position.
     * @param chunkPos Chunk section coordinates.
     * @return Whether the block is in the chunk.
     */
    public static boolean isInChunk(BlockPos pos, ChunkSectionPos chunkPos) {
        return isInChunk(pos.getX(), pos.getY(), pos.getZ(), chunkPos.getX(), chunkPos.getY(), chunkPos.getZ());
    }

    public static boolean isInChunk(Vec3d pos, ChunkSectionPos chunkPos) {
        int blockX = MathHelper.floor(pos.getX());
        int blockY = MathHelper.floor(pos.getY());
        int blockZ = MathHelper.floor(pos.getZ());
        return isInChunk(blockX, blockY, blockZ, chunkPos);
    }
}
