package net.betrayd.map_templates;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Represents a map template.
 * <p>
 * A map template stores serialized chunks, block entities, entities, the bounds, the biome, and regions.
 * <p>
 * It can be loaded from resources with {@link MapTemplateSerializer#loadFromResource(MinecraftServer, Identifier)}.
 */
public final class MapTemplate {
    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    final Long2ObjectMap<MapChunk> chunks = new Long2ObjectOpenHashMap<>();
    final Collection<MapEntity> entities = new ArrayList<>();

    RegistryKey<Biome> biome = BiomeKeys.THE_VOID;

    BlockBounds bounds = null;
    BlockBounds generatedBounds = null;

    MapTemplateMetadata metadata = new MapTemplateMetadata();

    private MapTemplate() {
    }

    public static MapTemplate createEmpty() {
        return new MapTemplate();
    }
    

    /**
     * Sets the biome key of the map template.
     *
     * @param biome The biome key.
     */
    public void setBiome(RegistryKey<Biome> biome) {
        this.biome = biome;
    }

    /**
     * Returns the biome key of the map template.
     *
     * @return The biome key.
     */
    public RegistryKey<Biome> getBiome() {
        return this.biome;
    }

    /**
     * Returns the non-world data of this MapTemplate that can be used to control additional game logic, but has no
     * impact in what blocks or entities are placed in the world. This includes regions and arbitrary attached data.
     *
     * @return the map template metadata for this map.
     */
    public MapTemplateMetadata getMetadata() {
        return this.metadata;
    }

    public void setBlockState(int x, int y, int z, BlockState state) {
        MapChunk chunk = this.getOrCreateChunk(chunkPos(x, y, z));

        int localX = x & 0xF;
        int localY = y & 0xF;
        int localZ = z & 0xF;
        chunk.set(localX, localY, localZ, state);

        this.generatedBounds = null;

        if (state.hasBlockEntity()) {
            var nbt = new NbtCompound();
            nbt.putString("id", "DUMMY");
            chunk.putBlockEntity(localX, localY, localZ, nbt);
        }
    }

    public final void setBlockState(Vec3i pos, BlockState state) {
        setBlockState(pos.getX(), pos.getY(), pos.getZ(), state);
    }

    public BlockState getBlockState(int x, int y, int z) {
        MapChunk chunk = chunks.get(chunkPos(x, y, z));
        if (chunk == null) return AIR;
        return chunk.get(x & 0xF, y & 0xF, z & 0xF);
    }

    public final BlockState getBlockState(Vec3i pos) {
        return getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }

    public void setBlockEntity(BlockPos pos, @Nullable BlockEntity entity) {
        if (entity != null) {
            this.setBlockEntityNbt(pos, entity.createNbtWithId());
        } else {
            this.setBlockEntityNbt(pos, null);
        }
    }
    
    @Nullable
    public NbtCompound setBlockEntityNbt(int x, int y, int z, @Nullable NbtCompound nbt) {
        MapChunk chunk = this.getOrCreateChunk(chunkPos(x, y, z));

        int localX = x & 0xF;
        int localY = y & 0xF;
        int localZ = z & 0xF;

        return chunk.putBlockEntity(localX, localY, localZ, nbt);
    }

    public NbtCompound setBlockEntityNbt(Vec3i pos, @Nullable NbtCompound nbt) {
        return setBlockEntityNbt(pos.getX(), pos.getY(), pos.getZ(), nbt);
    }

    @Nullable
    public NbtCompound getBlockEntityNbt(int x, int y, int z) {
        MapChunk chunk = chunks.get(chunkPos(x, y, z));
        if (chunk == null) return null;
        return chunk.getBlockEntity(x & 0xF, y & 0xF, z & 0xF);
    }

    @Nullable
    public final NbtCompound getBlockEntityNbt(Vec3i pos) {
        return getBlockEntityNbt(pos.getX(), pos.getY(), pos.getZ());
    }

    public NbtCompound getBlockEntityNbt(BlockPos localPos, BlockPos worldPos) {
        NbtCompound nbt = getBlockEntityNbt(localPos);
        if (nbt == null) return null;

        nbt = nbt.copy(); // Don't fuck up the original nbt
        nbt.putInt("x", worldPos.getX());
        nbt.putInt("y", worldPos.getY());
        nbt.putInt("z", worldPos.getZ());
        
        return nbt;
    }

    /**
     * Get all the block entities in this map template.
     * 
     * @return An iterable providing block entities with their global block
     *         positions.
     * @implNote The returned map entries are not based on a real map. This is just
     *           a convenient Java version of a tuple.
     */
    public Iterable<Map.Entry<BlockPos, NbtCompound>> getBlockEntities() {
        return () -> streamBlockEntities().iterator();
    }

    /**
     * Stream all the block entities in this map template.
     * @return A stream of block entities with their global block positions.
     */
    public Stream<Map.Entry<BlockPos, NbtCompound>> streamBlockEntities() {
        return chunks.long2ObjectEntrySet().stream().flatMap(
            entry -> streamBlockEntities(entry.getValue())
        );
    }

    private Stream<Map.Entry<BlockPos, NbtCompound>> streamBlockEntities(MapChunk chunk) {
        ChunkSectionPos chunkPos = chunk.getPos();
        return chunk.streamBlockEntities().map(e -> {
            int x = e.getKey().getX() + chunkPos.getMinX();
            int y = e.getKey().getY() + chunkPos.getMinY();
            int z = e.getKey().getZ() + chunkPos.getMinZ();
            return new AbstractMap.SimpleEntry<>(new BlockPos(x, y, z), e.getValue());
        });
    }

    public void addEntity(MapEntity entity) {
        entities.add(entity);
    }

    public final void addEntity(Entity entity, Vec3d pos) {
        MapEntity mapEntity = MapEntity.fromEntity(entity, pos);
        if (mapEntity != null) addEntity(mapEntity);
    }

    public Collection<MapEntity> getEntities() {
        return entities;
    }
    
    /**
     * Returns a stream of serialized entities from a chunk.
     *
     * @param chunkX The chunk X-coordinate.
     * @param chunkY The chunk Y-coordinate.
     * @param chunkZ The chunk Z-coordinate.
     * @return The stream of entities.
     */
    public Stream<MapEntity> getEntitiesInChunk(int chunkX, int chunkY, int chunkZ) {
        ChunkSectionPos chunkPos = ChunkSectionPos.from(chunkX, chunkY, chunkZ);
        return entities.stream().filter(ent -> MapChunk.isInChunk(ent.position(), chunkPos));
    }

    // TODO: store / lookup more efficiently?
    public int getTopY(int x, int z, Heightmap.Type heightmap) {
        var predicate = heightmap.getBlockPredicate();

        var bounds = this.getBounds();
        int minY = bounds.min().getY();
        int maxY = bounds.max().getY();

        var mutablePos = new BlockPos.Mutable(x, 0, z);
        for (int y = maxY; y >= minY; y--) {
            mutablePos.setY(y);

            BlockState state = this.getBlockState(mutablePos);
            if (predicate.test(state)) {
                return y;
            }
        }

        return 0;
    }

    public BlockPos getTopPos(int x, int z, Heightmap.Type heightmap) {
        int y = this.getTopY(x, z, heightmap);
        return new BlockPos(x, y, z);
    }

    public boolean containsBlock(BlockPos pos) {
        return this.getBlockState(pos) != AIR;
    }

    @NotNull
    public MapChunk getOrCreateChunk(long pos) {
        return this.chunks.computeIfAbsent(pos, p -> new MapChunk(ChunkSectionPos.from(p)));
    }

    protected MapChunk putChunk(long pos, MapChunk chunk) {
        return this.chunks.put(pos, chunk);
    }

    protected final MapChunk putChunk(MapChunk chunk) {
        return putChunk(chunk.getPos().asLong(), chunk);
    }
    

    @Nullable
    public MapChunk getChunk(long pos) {
        return this.chunks.get(pos);
    }

    public void setBounds(BlockBounds bounds) {
        this.bounds = bounds;
        this.generatedBounds = null;
    }

    public BlockBounds getBounds() {
        var bounds = this.bounds;
        if (bounds != null) {
            return bounds;
        }

        var generatedBounds = this.generatedBounds;
        if (generatedBounds == null) {
            this.generatedBounds = generatedBounds = this.computeBounds();
        }

        return generatedBounds;
    }

    @Nullable
    private BlockBounds getBoundsOrNull() {
        var bounds = this.bounds;
        return bounds != null ? bounds : this.generatedBounds;
    }

    private BlockBounds computeBounds() {
        int minChunkX = Integer.MAX_VALUE;
        int minChunkY = Integer.MAX_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int maxChunkY = Integer.MIN_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;

        for (var entry : Long2ObjectMaps.fastIterable(this.chunks)) {
            long chunkPos = entry.getLongKey();
            int chunkX = ChunkSectionPos.unpackX(chunkPos);
            int chunkY = ChunkSectionPos.unpackY(chunkPos);
            int chunkZ = ChunkSectionPos.unpackZ(chunkPos);

            if (chunkX < minChunkX) minChunkX = chunkX;
            if (chunkY < minChunkY) minChunkY = chunkY;
            if (chunkZ < minChunkZ) minChunkZ = chunkZ;

            if (chunkX > maxChunkX) maxChunkX = chunkX;
            if (chunkY > maxChunkY) maxChunkY = chunkY;
            if (chunkZ > maxChunkZ) maxChunkZ = chunkZ;
        }

        return BlockBounds.of(
                minChunkX << 4, minChunkY << 4, minChunkZ << 4,
                (maxChunkX << 4) + 15, (maxChunkY << 4) + 15, (maxChunkZ << 4) + 15
        );
    }

    static long chunkPos(Vec3i pos) {
        return chunkPos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    static long chunkPos(Vec3d pos) {
        return chunkPos(MathHelper.floor(pos.getX()) >> 4, MathHelper.floor(pos.getY()) >> 4, MathHelper.floor(pos.getZ()) >> 4);
    }

    static long chunkPos(int x, int y, int z) {
        return ChunkSectionPos.asLong(x, y, z);
    }

    public MapTemplate translated(int x, int y, int z) {
        return this.transformed(MapTransform.translation(x, y, z));
    }

    public MapTemplate rotateAround(BlockPos pivot, BlockRotation rotation, BlockMirror mirror) {
        return this.transformed(MapTransform.rotationAround(pivot, rotation, mirror));
    }

    public MapTemplate rotate(BlockRotation rotation, BlockMirror mirror) {
        return this.rotateAround(BlockPos.ORIGIN, rotation, mirror);
    }

    public MapTemplate rotate(BlockRotation rotation) {
        return this.rotate(rotation, BlockMirror.NONE);
    }

    public MapTemplate mirror(BlockMirror mirror) {
        return this.rotate(BlockRotation.NONE, mirror);
    }

    public MapTemplate transformed(MapTransform transform) {
        var result = MapTemplate.createEmpty();

        var mutablePos = new BlockPos.Mutable();

        for (MapChunk chunk : this.chunks.values()) {
            var minChunkPos = chunk.getPos().getMinPos();

            for (int chunkZ = 0; chunkZ < 16; chunkZ++) {
                for (int chunkY = 0; chunkY < 16; chunkY++) {
                    for (int chunkX = 0; chunkX < 16; chunkX++) {
                        var state = chunk.get(chunkX, chunkY, chunkZ);
                        if (!state.isAir()) {
                            state = transform.transformedBlock(state);

                            mutablePos.set(minChunkPos, chunkX, chunkY, chunkZ);
                            result.setBlockState(transform.transformPoint(mutablePos), state);
                        }
                    }
                }
            }

            // for ()

            // for (var entity : chunk.getEntities()) {
            //     result.addEntity(entity.transformed(transform));
            // }
        }

        for (var entry : getBlockEntities()) {
            mutablePos.set(entry.getKey());
            transform.transformPoint(mutablePos);
            result.setBlockEntityNbt(mutablePos, entry.getValue());
        }

        for (MapEntity entity : entities) {
            result.addEntity(entity.transformed(transform));
        }

        // for (var blockEntity : Long2ObjectMaps.fastIterable(this.blockEntities)) {
        //     mutablePos.set(blockEntity.getLongKey());
        //     transform.transformPoint(mutablePos);

        //     var nbt = blockEntity.getValue().copy();
        //     result.setBlockEntityNbt(mutablePos, nbt);
        // }

        result.biome = this.biome;

        result.metadata.data = this.metadata.data.copy();

        for (var sourceRegion : this.metadata.regions) {
            result.metadata.regions.add(new TemplateRegion(
                    sourceRegion.getMarker(),
                    transform.transformedBounds(sourceRegion.getBounds()),
                    sourceRegion.getData().copy()
            ));
        }

        return result;
    }

    /**
     * Copies and merges the contents of the given two map templates, where the first given template takes priority
     * in case of a conflict.
     *
     * @param primary the primary map template to merge (overrides the secondary template)
     * @param secondary the secondary map template to merge
     * @return the merged template
     */
    public static MapTemplate merged(MapTemplate primary, MapTemplate secondary) {
        var result = MapTemplate.createEmpty();
        secondary.mergeInto(result);
        primary.mergeInto(result);
        return result;
    }

    public void mergeFrom(MapTemplate other) {
        other.mergeInto(this);
    }

    public void mergeInto(MapTemplate other) {
        for (var entry : Long2ObjectMaps.fastIterable(this.chunks)) {
            long chunkPos = entry.getLongKey();
            var chunk = entry.getValue();
            var otherChunk = other.getOrCreateChunk(chunkPos);

            for (int chunkZ = 0; chunkZ < 16; chunkZ++) {
                for (int chunkY = 0; chunkY < 16; chunkY++) {
                    for (int chunkX = 0; chunkX < 16; chunkX++) {
                        var state = chunk.get(chunkX, chunkY, chunkZ);
                        if (!state.isAir()) {
                            otherChunk.set(chunkX, chunkY, chunkZ, state);
                        }
                    }
                }
            }

            for (var entEntry : chunk.getBlockEntities().long2ObjectEntrySet()) {
                otherChunk.getBlockEntities().put(entEntry.getLongKey(), entEntry.getValue().copy());
            }

        }

        for (MapEntity entity : this.entities) {
            other.addEntity(entity);
        }

        other.metadata.data.copyFrom(this.metadata.data);

        for (var region : this.metadata.regions) {
            other.metadata.addRegion(region.copy());
        }

        other.bounds = this.getBounds().union(other.getBounds());
        other.biome = this.biome;
    }
}
