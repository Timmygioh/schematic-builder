package dev.schematicbuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/** Verteilt das Platzieren ueber mehrere Ticks, damit der Server nicht laggt. */
public final class PasteManager {
    public static final int BLOCKS_PER_TICK = 2000;

    public record UndoEntry(BlockPos pos, BlockState state) {}

    private static final class Job {
        final UUID owner;
        final ServerLevel level;
        final BlockPos origin;
        final Schematic schematic;
        final boolean ignoreAir;
        final List<UndoEntry> undo = new ArrayList<>();
        int index = 0;

        Job(UUID owner, ServerLevel level, BlockPos origin, Schematic schematic, boolean ignoreAir) {
            this.owner = owner;
            this.level = level;
            this.origin = origin;
            this.schematic = schematic;
            this.ignoreAir = ignoreAir;
        }
    }

    private static final class UndoJob {
        final UUID owner;
        final ServerLevel level;
        final List<UndoEntry> entries;
        int index;

        UndoJob(UUID owner, ServerLevel level, List<UndoEntry> entries) {
            this.owner = owner;
            this.level = level;
            this.entries = entries;
            this.index = entries.size() - 1; // rueckwaerts
        }
    }

    private static final Map<UUID, Schematic> LOADED = new HashMap<>();
    private static final Map<UUID, List<UndoEntry>> UNDO = new HashMap<>();
    private static final Map<UUID, ServerLevel> UNDO_LEVEL = new HashMap<>();
    private static final List<Job> JOBS = new ArrayList<>();
    private static final List<UndoJob> UNDO_JOBS = new ArrayList<>();

    public static void setLoaded(UUID player, Schematic s) {
        LOADED.put(player, s);
    }

    public static Schematic getLoaded(UUID player) {
        return LOADED.get(player);
    }

    public static boolean isBusy(UUID player) {
        return JOBS.stream().anyMatch(j -> j.owner.equals(player))
                || UNDO_JOBS.stream().anyMatch(j -> j.owner.equals(player));
    }

    public static void startPaste(UUID player, ServerLevel level, BlockPos origin, Schematic s, boolean ignoreAir) {
        JOBS.add(new Job(player, level, origin, s, ignoreAir));
    }

    public static boolean startUndo(UUID player) {
        List<UndoEntry> entries = UNDO.remove(player);
        ServerLevel level = UNDO_LEVEL.remove(player);
        if (entries == null || entries.isEmpty() || level == null) return false;
        UNDO_JOBS.add(new UndoJob(player, level, entries));
        return true;
    }

    public static int cancel(UUID player) {
        int before = JOBS.size() + UNDO_JOBS.size();
        JOBS.removeIf(j -> j.owner.equals(player));
        UNDO_JOBS.removeIf(j -> j.owner.equals(player));
        return before - JOBS.size() - UNDO_JOBS.size();
    }

    public static void clear() {
        LOADED.clear();
        UNDO.clear();
        UNDO_LEVEL.clear();
        JOBS.clear();
        UNDO_JOBS.clear();
    }

    private static void notify(ServerLevel level, UUID player, String msg) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(player);
        if (p != null) p.sendSystemMessage(Component.literal(msg));
    }

    public static void tick() {
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            int total = job.schematic.volume();
            int placed = 0;
            while (job.index < total && placed < BLOCKS_PER_TICK) {
                int i = job.index++;
                BlockState state = job.schematic.stateAt(i);
                if (job.ignoreAir && state.isAir()) continue;
                BlockPos pos = job.origin.offset(
                        job.schematic.xOf(i), job.schematic.yOf(i), job.schematic.zOf(i));
                if (!job.level.isInWorldBounds(pos)) continue;
                BlockState old = job.level.getBlockState(pos);
                if (old == state) continue;
                job.undo.add(new UndoEntry(pos, old));
                job.level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                placed++;
            }
            if (job.index >= total) {
                UNDO.put(job.owner, job.undo);
                UNDO_LEVEL.put(job.owner, job.level);
                notify(job.level, job.owner, "Schematic fertig platziert (" + job.undo.size() + " Bloecke geaendert). /schem undo macht es rueckgaengig.");
                it.remove();
            }
        }

        Iterator<UndoJob> uit = UNDO_JOBS.iterator();
        while (uit.hasNext()) {
            UndoJob job = uit.next();
            int done = 0;
            while (job.index >= 0 && done < BLOCKS_PER_TICK) {
                UndoEntry e = job.entries.get(job.index--);
                job.level.setBlock(e.pos(), e.state(), Block.UPDATE_CLIENTS);
                done++;
            }
            if (job.index < 0) {
                notify(job.level, job.owner, "Undo fertig.");
                uit.remove();
            }
        }
    }

    private PasteManager() {}
}
