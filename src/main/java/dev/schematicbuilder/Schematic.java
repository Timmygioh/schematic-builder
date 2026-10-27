package dev.schematicbuilder;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Sponge-Schematic (.schem), Version 2 und 3. Nur Bloecke, keine Block-Entities/Entities. */
public final class Schematic {
    public final int width, height, length;
    private final BlockState[] states; // Index = x + z*width + y*width*length

    private Schematic(int w, int h, int l, BlockState[] states) {
        this.width = w;
        this.height = h;
        this.length = l;
        this.states = states;
    }

    public int volume() {
        return states.length;
    }

    public BlockState stateAt(int i) {
        return states[i];
    }

    public int xOf(int i) {
        return i % width;
    }

    public int zOf(int i) {
        return (i / width) % length;
    }

    public int yOf(int i) {
        return i / (width * length);
    }

    public static Schematic load(Path file, HolderLookup<Block> blocks) throws IOException {
        CompoundTag root;
        try (InputStream in = Files.newInputStream(file)) {
            root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }

        // v3 liegt unter "Schematic", v2 direkt im Root
        CompoundTag s = root.getCompound("Schematic").orElse(root);
        int version = s.getIntOr("Version", 2);

        int w = s.getShortOr("Width", (short) 0) & 0xFFFF;
        int h = s.getShortOr("Height", (short) 0) & 0xFFFF;
        int l = s.getShortOr("Length", (short) 0) & 0xFFFF;
        if (w == 0 || h == 0 || l == 0) {
            throw new IOException("Ungueltige Schematic-Groesse");
        }

        CompoundTag palette;
        byte[] data;
        if (version >= 3) {
            CompoundTag b = s.getCompound("Blocks").orElseThrow(() -> new IOException("Blocks fehlt"));
            palette = b.getCompound("Palette").orElseThrow(() -> new IOException("Palette fehlt"));
            data = b.getByteArray("Data").orElseThrow(() -> new IOException("Data fehlt"));
        } else {
            palette = s.getCompound("Palette").orElseThrow(() -> new IOException("Palette fehlt"));
            data = s.getByteArray("BlockData").orElseThrow(() -> new IOException("BlockData fehlt"));
        }

        // Palette: Name -> Index. In ein Array nach Index umdrehen.
        int maxIndex = 0;
        for (String key : palette.keySet()) {
            maxIndex = Math.max(maxIndex, palette.getIntOr(key, 0));
        }
        BlockState[] byIndex = new BlockState[maxIndex + 1];
        java.util.Arrays.fill(byIndex, Blocks.AIR.defaultBlockState());
        for (String key : palette.keySet()) {
            try {
                byIndex[palette.getIntOr(key, 0)] = BlockStateParser.parseForBlock(blocks, key, false).blockState();
            } catch (CommandSyntaxException e) {
                SchematicBuilder.LOGGER.warn("Unbekannter Block in Schematic, ersetze durch Luft: {}", key);
            }
        }

        // BlockData: VarInts
        int volume = w * h * l;
        BlockState[] out = new BlockState[volume];
        int pos = 0;
        for (int i = 0; i < volume; i++) {
            int value = 0, shift = 0;
            while (true) {
                if (pos >= data.length) throw new IOException("BlockData zu kurz");
                byte b = data[pos++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            out[i] = value < byIndex.length ? byIndex[value] : Blocks.AIR.defaultBlockState();
        }
        return new Schematic(w, h, l, out);
    }
}
