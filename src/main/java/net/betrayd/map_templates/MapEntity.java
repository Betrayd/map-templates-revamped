package net.betrayd.map_templates;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public record MapEntity(Vec3d position, NbtCompound nbt) {
    public NbtCompound createEntityNbt(BlockPos origin) {
        var nbt = this.nbt.copy();

        var templateLocalPos = listToPos(this.nbt.getList("Pos", NbtElement.DOUBLE_TYPE));

        var worldPosition = this.position.add(origin.getX(), origin.getY(), origin.getZ());
        nbt.put("Pos", posToList(worldPosition));

        // AbstractDecorationEntity has special position handling with an attachment position.
        if (nbt.contains("TileX", NbtElement.INT_TYPE)) {
            nbt.putInt("TileX", MathHelper.floor(nbt.getInt("TileX") + worldPosition.x - templateLocalPos.x));
            nbt.putInt("TileY", MathHelper.floor(nbt.getInt("TileY") + worldPosition.y - templateLocalPos.y));
            nbt.putInt("TileZ", MathHelper.floor(nbt.getInt("TileZ") + worldPosition.z - templateLocalPos.z));
        }

        return nbt;
    }

    public void createEntities(World world, BlockPos origin, Consumer<Entity> consumer) {
        var nbt = this.createEntityNbt(origin);
        EntityType.loadEntityWithPassengers(nbt, world, entity -> {
            consumer.accept(entity);
            return entity;
        });
    }

    @Nullable
    public static MapEntity fromEntity(Entity entity, Vec3d position) {
        var nbt = new NbtCompound();
        if (!entity.saveNbt(nbt)) {
            return null;
        }

        // Avoid conflicts.
        nbt.remove("UUID");

        BlockPos minChunkPos = getMinChunkPosFor(position);
        nbt.put("Pos", posToList(position.subtract(minChunkPos.getX(), minChunkPos.getY(), minChunkPos.getZ())));

        // AbstractDecorationEntity has special position handling with an attachment position.
        if (nbt.contains("TileX", NbtElement.INT_TYPE)) {
            BlockPos localPos = new BlockPos(nbt.getInt("TileX"), nbt.getInt("TileY"), nbt.getInt("TileZ"))
                    .subtract(entity.getBlockPos())
                    .add(MathHelper.floor(position.getX()), MathHelper.floor(position.getY()), MathHelper.floor(position.getZ()))
                    .subtract(minChunkPos);
            nbt.putInt("TileX", localPos.getX());
            nbt.putInt("TileY", localPos.getY());
            nbt.putInt("TileZ", localPos.getZ());
        }

        return new MapEntity(position, nbt);
    }

    @Deprecated
    public static MapEntity fromNbt(ChunkSectionPos sectionPos, NbtCompound nbt) {
        Vec3d localPos = listToPos(nbt.getList("Pos", NbtElement.DOUBLE_TYPE));
        Vec3d globalPos = localPos.add(sectionPos.getMinX(), sectionPos.getMinY(), sectionPos.getMinZ());

        return new MapEntity(globalPos, nbt);
    }

    public static MapEntity fromNbt(NbtCompound nbt) {
        Vec3d pos = listToPos(nbt.getList("Pos", NbtElement.DOUBLE_TYPE));
        return new MapEntity(pos, nbt);
    }

    MapEntity transformed(MapTransform transform) {
        var resultPosition = transform.transformedPoint(this.position);
        var resultNbt = this.nbt.copy();

        var minChunkPos = getMinChunkPosFor(this.position);
        var minResultChunkPos = getMinChunkPosFor(resultPosition);

        resultNbt.put("Pos", posToList(resultPosition.subtract(minResultChunkPos.getX(), minResultChunkPos.getY(), minResultChunkPos.getZ())));

        // AbstractDecorationEntity has special position handling with an attachment position.
        if (resultNbt.contains("TileX", NbtElement.INT_TYPE)) {
            var attachedPos = new BlockPos(
                    resultNbt.getInt("TileX") + minChunkPos.getX(),
                    resultNbt.getInt("TileY") + minChunkPos.getY(),
                    resultNbt.getInt("TileZ") + minChunkPos.getZ()
            );

            var localAttachedPos = transform.transformedPoint(attachedPos)
                    .subtract(minResultChunkPos);
            resultNbt.putInt("TileX", localAttachedPos.getX());
            resultNbt.putInt("TileY", localAttachedPos.getY());
            resultNbt.putInt("TileZ", localAttachedPos.getZ());
        }

        return new MapEntity(resultPosition, resultNbt);
    }

    private static BlockPos getMinChunkPosFor(Vec3d position) {
        return new BlockPos(
                MathHelper.floor(position.getX()) & ~15,
                MathHelper.floor(position.getY()) & ~15,
                MathHelper.floor(position.getZ()) & ~15
        );
    }

    private static NbtList posToList(Vec3d pos) {
        var list = new NbtList();
        list.add(NbtDouble.of(pos.x));
        list.add(NbtDouble.of(pos.y));
        list.add(NbtDouble.of(pos.z));
        return list;
    }

    private static Vec3d listToPos(NbtList list) {
        return new Vec3d(list.getDouble(0), list.getDouble(1), list.getDouble(2));
    }
}
