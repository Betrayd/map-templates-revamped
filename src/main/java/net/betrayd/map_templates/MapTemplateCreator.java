package net.betrayd.map_templates;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

/**
 * Creates map templates from the existing world.
 */
public class MapTemplateCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapTemplateCreator.class);

    // public MapTemplate compileWorld(World world, ChunkSectionPos bounds1, ChunkSectionPos bounds2, @Nullable Predicate<Entity> entityPredicate) {
    //     if (entityPredicate != null) {
    //         entityPredicate = entityPredicate.and(e -> !(e instanceof PlayerEntity));
    //     }

    //     ChunkSectionPos minPos = min(bounds1, bounds2);
    //     ChunkSectionPos maxPos = max(bounds1, bounds2);

    //     MapTemplate template = MapTemplate.createEmpty();

    //     for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
    //         for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
    //             Chunk chunk = world.getChunk(x, z);
    //             if (chunk == null) continue;
                
    //             for (MapChunk mapChunk : compileChunk(chunk, new ChunkPos(x, z))) {
    //                 template.putChunk(mapChunk); 
    //             }
    //         }
    //     }
    // }
    
    /**
     * Compile a section of a world into a map template.
     * 
     * @param world           World to compile.
     * @param bounds1         One corner of the bounding box to compile.
     * @param bounds2         The other corner of the bounding box.
     * @param entityPredicate A predicate to determine which entities are include.
     *                        If <code>null</code> no entities are included. Players
     *                        are never included.
     * @param executor        An executor to export chunks on. If <code>null</code>,
     *                        {@link Util#getMainWorkerExecutor()} is used.
     * @return A future with the compiled map template.
     */
    public static CompletableFuture<MapTemplate> compileWorld(World world, ChunkSectionPos bounds1,
            ChunkSectionPos bounds2, @Nullable Predicate<Entity> entityPredicate, @Nullable Executor executor) {
        
        if (executor == null)
                executor = Util.getMainWorkerExecutor();
        
        ChunkSectionPos minPos = min(bounds1, bounds2);
        ChunkSectionPos maxPos = max(bounds1, bounds2);

        BlockPos minBlockPos = new BlockPos(minPos.getMinX(), minPos.getMinY(), minPos.getMinZ());
        BlockPos maxBlockPos = new BlockPos(maxPos.getMaxX(), maxPos.getMaxY(), maxPos.getMaxZ());

        MapTemplate template = MapTemplate.createEmpty();

        if (entityPredicate != null && world instanceof ServerWorld serverWorld) {
            entityPredicate = entityPredicate.and(ent -> !(ent instanceof PlayerEntity));

            for (Entity ent : serverWorld.iterateEntities()) {
                if (entityPredicate.test(ent) && blockBoundsContains(minBlockPos, maxBlockPos, ent.getBlockPos())) {
                    template.addEntity(ent, ent.getPos());
                }
            }
        }

        int expectedSize = (maxPos.getX() + 1 - minPos.getX()) * (maxPos.getZ() + 1 - minPos.getZ());        
        List<CompletableFuture<?>> futures = new ArrayList<>(expectedSize);

        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                Chunk chunk = world.getChunk(x, z);
                if (chunk == null) continue;

                ChunkPos chunkPos = new ChunkPos(x, z);
                futures.add(CompletableFuture.supplyAsync(() -> compileChunk(chunk, chunkPos, minPos.getY(), maxPos.getY()), executor).thenAccept(array -> {
                    
                    for (MapChunk c : array) {
                        if (c != null)
                            putChunkInTemplate(template, c);
                    }
                }));
            }
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture<?>[]::new)).thenApply(v -> template);
    }

    // Synchronized wrapper in dedicated function; I don't know how it will play with lambdas.
    private static synchronized void putChunkInTemplate(MapTemplate template, MapChunk chunk) {
        template.putChunk(chunk);
    }

    /**
     * Compile a Minecraft chunk into a set of map chunks. Includes blocks and block
     * entities.
     * 
     * @param chunk    Minecraft chunk
     * @param chunkPos Minecraft chunk pos.
     * @return Compiled map chunks. Unlike Minecraft chunks, map chunks are cubic,
     *         meaning that a single Minecraft chunk will have multiple map chunks.
     */
    public static MapChunk[] compileChunk(Chunk chunk, ChunkPos chunkPos, int minY, int maxY) {
        ChunkSection[] sections = chunk.getSectionArray();
        MapChunk[] chunks = new MapChunk[sections.length];
        LOGGER.info("Writing chunk {}", chunkPos);
        int y;
        for (int i = 0; i < sections.length; i++) {
            y = chunk.sectionIndexToCoord(i);
            if (minY <= y && y <= maxY)
                chunks[i] = MapChunk.loadFrom(ChunkSectionPos.from(chunkPos, y), sections[i]);
        }

        for (BlockPos pos : chunk.getBlockEntityPositions()) {
            NbtCompound nbt = chunk.getPackedBlockEntityNbt(pos);
            if (nbt == null) continue;

            MapChunk mapChunk = chunks[chunk.getSectionIndex(pos.getY())];
            if (mapChunk == null) continue;

            int chunkX = pos.getX() & 0xF;
            int chunkY = pos.getY() & 0xF;
            int chunkZ = pos.getZ() & 0xF;
            
            mapChunk.putBlockEntity(chunkX, chunkY, chunkZ, nbt);
        }

        return chunks;
    }
    
    private static ChunkSectionPos min(ChunkSectionPos a, ChunkSectionPos b) {
        return ChunkSectionPos.from(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
    }

    private static ChunkSectionPos max(ChunkSectionPos a, ChunkSectionPos b) {
        return ChunkSectionPos.from(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
    }

    private static boolean blockBoundsContains(BlockPos minPos, BlockPos maxPos, BlockPos pos) {
        return minPos.getX() <= pos.getX() && pos.getX() <= maxPos.getX()
            && minPos.getY() <= pos.getY() && pos.getY() <= maxPos.getY()
            && minPos.getZ() <= pos.getZ() && pos.getZ() <= maxPos.getZ();
    }
}
