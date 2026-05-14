package net.enelson.sopmines.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.List;

final class FaweIntegration {

    private final boolean available;
    private final Method bukkitAdapterWorld;
    private final Method bukkitAdapterBlockData;
    private final Method worldEditGetInstance;
    private final Method worldEditNewEditSessionBuilder;
    private final Method builderWorld;
    private final Method builderMaxBlocks;
    private final Method builderBuild;
    private final Method blockVectorAt;
    private final Method editSessionSetBlock;
    private final Method editSessionClose;

    FaweIntegration() {
        Method tmpBukkitAdapterWorld = null;
        Method tmpBukkitAdapterBlockData = null;
        Method tmpWorldEditGetInstance = null;
        Method tmpWorldEditNewEditSessionBuilder = null;
        Method tmpBuilderWorld = null;
        Method tmpBuilderMaxBlocks = null;
        Method tmpBuilderBuild = null;
        Method tmpBlockVectorAt = null;
        Method tmpEditSessionSetBlock = null;
        Method tmpEditSessionClose = null;
        boolean ok = false;
        try {
            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Class<?> worldEditClass = Class.forName("com.sk89q.worldedit.WorldEdit");
            Class<?> editSessionBuilderClass = Class.forName("com.sk89q.worldedit.EditSessionBuilder");
            Class<?> blockVector3Class = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData");
            Class<?> editSessionClass = Class.forName("com.sk89q.worldedit.EditSession");
            Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
            Class<?> blockStateHolderClass = Class.forName("com.sk89q.worldedit.world.block.BlockStateHolder");

            tmpBukkitAdapterWorld = bukkitAdapterClass.getMethod("adapt", World.class);
            tmpBukkitAdapterBlockData = bukkitAdapterClass.getMethod("adapt", blockDataClass);
            tmpWorldEditGetInstance = worldEditClass.getMethod("getInstance");
            tmpWorldEditNewEditSessionBuilder = worldEditClass.getMethod("newEditSessionBuilder");
            tmpBuilderWorld = editSessionBuilderClass.getMethod("world", weWorldClass);
            tmpBuilderMaxBlocks = editSessionBuilderClass.getMethod("maxBlocks", int.class);
            tmpBuilderBuild = editSessionBuilderClass.getMethod("build");
            tmpBlockVectorAt = blockVector3Class.getMethod("at", int.class, int.class, int.class);
            tmpEditSessionSetBlock = editSessionClass.getMethod("setBlock", blockVector3Class, blockStateHolderClass);
            tmpEditSessionClose = editSessionClass.getMethod("close");

            ok = Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
                    || Bukkit.getPluginManager().isPluginEnabled("WorldEdit");
        } catch (Throwable ignored) {
            ok = false;
        }

        this.available = ok;
        this.bukkitAdapterWorld = tmpBukkitAdapterWorld;
        this.bukkitAdapterBlockData = tmpBukkitAdapterBlockData;
        this.worldEditGetInstance = tmpWorldEditGetInstance;
        this.worldEditNewEditSessionBuilder = tmpWorldEditNewEditSessionBuilder;
        this.builderWorld = tmpBuilderWorld;
        this.builderMaxBlocks = tmpBuilderMaxBlocks;
        this.builderBuild = tmpBuilderBuild;
        this.blockVectorAt = tmpBlockVectorAt;
        this.editSessionSetBlock = tmpEditSessionSetBlock;
        this.editSessionClose = tmpEditSessionClose;
    }

    boolean isAvailable() {
        return available;
    }

    boolean applyPlan(World world, List<AutoMineService.BlockChange> changes) {
        if (!available || world == null || changes == null || changes.isEmpty()) {
            return false;
        }
        Object editSession = null;
        try {
            Object weWorld = bukkitAdapterWorld.invoke(null, world);
            Object worldEdit = worldEditGetInstance.invoke(null);
            Object builder = worldEditNewEditSessionBuilder.invoke(worldEdit);
            builderWorld.invoke(builder, weWorld);
            builderMaxBlocks.invoke(builder, -1);
            editSession = builderBuild.invoke(builder);

            for (AutoMineService.BlockChange change : changes) {
                Material material = change.getMaterial();
                if (material == null) {
                    continue;
                }
                Object vec = blockVectorAt.invoke(null, change.getX(), change.getY(), change.getZ());
                Object weBlockState = bukkitAdapterBlockData.invoke(null, material.createBlockData());
                editSessionSetBlock.invoke(editSession, vec, weBlockState);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (editSession != null) {
                try {
                    editSessionClose.invoke(editSession);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
