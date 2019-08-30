package com.yungnickyoung.minecraft.bettercaves.world.cave;

import com.yungnickyoung.minecraft.bettercaves.config.Configuration;
import com.yungnickyoung.minecraft.bettercaves.config.Settings;
import com.yungnickyoung.minecraft.bettercaves.noise.NoiseTuple;
import com.yungnickyoung.minecraft.bettercaves.noise.PerlinNoiseGen;
import com.yungnickyoung.minecraft.bettercaves.util.BetterCaveUtil;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;

import java.util.List;

/**
 * Generates large cavernous caves of uniform size, i.e. not depending on depth
 */
public class PerlinCavern extends BetterCave {
    private PerlinNoiseGen noiseGen;

    public PerlinCavern(World world) {
        super(world);
        noiseGen = new PerlinNoiseGen(
                world,
                Configuration.caveSettings.perlinCavern.fractalOctaves,
                Configuration.caveSettings.perlinCavern.fractalGain,
                Configuration.caveSettings.perlinCavern.fractalFrequency,
                Configuration.caveSettings.perlinCavern.turbulenceOctaves,
                Configuration.caveSettings.perlinCavern.turbulenceGain,
                Configuration.caveSettings.perlinCavern.turbulenceFrequency,
                Configuration.caveSettings.perlinCavern.enableTurbulence,
                Configuration.caveSettings.perlinCavern.enableSmoothing
        );
    }

    @Override
    public void generate(int chunkX, int chunkZ, ChunkPrimer primer) {
//        if (Settings.DEBUG_WORLD_GEN) {
//            debugGenerate(chunkX, chunkZ, primer);
//            return;
//        }

        int maxHeight = Configuration.caveSettings.perlinCavern.maxHeight;
        int minHeight = Configuration.caveSettings.perlinCavern.minHeight;
        int numGenerators = Configuration.caveSettings.perlinCavern.numGenerators;

        List<NoiseTuple[][]> noises = noiseGen.generateNoise(chunkX, chunkZ, minHeight, maxHeight, numGenerators);

        for (int realY = maxHeight; realY >= minHeight; realY--) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    List<Float> blockNoise = noises.get(maxHeight - realY)[localX][localZ].getNoiseValues();

                    boolean digBlock = true;
//                    for (float noise : blockNoise) {
//                        if (noise < Configuration.perlinCavern.noiseThreshold) {
//                            digBlock = false;
//                            break;
//                        }
//                    }

//                    float totalNoise = 0;
//                    for (float noise : blockNoise)
//                        totalNoise += noise;
//
//                    totalNoise /= blockNoise.size();
//                    if (totalNoise < Configuration.perlinCavern.noiseThreshold)
//                        digBlock = false;

                    float totalNoise = 1;
                    for (float noise : blockNoise)
                        totalNoise *= noise;

                    if (totalNoise < Configuration.caveSettings.perlinCavern.noiseThreshold)
                        digBlock = false;

                    if (digBlock) {
                        IBlockState blockState = primer.getBlockState(localX, realY, localZ);
                        IBlockState blockStateAbove = primer.getBlockState(localX, realY + 1, localZ);
                        boolean foundTopBlock = BetterCaveUtil.isTopBlock(world, primer, localX, realY, localZ, chunkX, chunkZ);

                        if (blockStateAbove.getMaterial() == Material.WATER)
                            continue;
                        if (localX < 15 && primer.getBlockState(localX + 1, realY, localZ).getMaterial() == Material.WATER)
                            continue;
                        if (localX > 0 && primer.getBlockState(localX - 1, realY, localZ).getMaterial() == Material.WATER)
                            continue;
                        if (localZ < 15 && primer.getBlockState(localX, realY, localZ + 1).getMaterial() == Material.WATER)
                            continue;
                        if (localZ > 0 && primer.getBlockState(localX, realY, localZ - 1).getMaterial() == Material.WATER)
                            continue;

                        boolean lava = true;

                        BetterCaveUtil.digBlock(world, primer, localX, realY, localZ, chunkX, chunkZ);
                    }


                    if (Settings.DEBUG_LOG_ENABLED) {
                        float avg = 0;
                        for (float n : blockNoise)
                            avg += n;
                        avg /= blockNoise.size();

                        avgNoise = ((numChunksGenerated * avgNoise) + avg) / (numChunksGenerated + 1);

                        if (avg > maxNoise) maxNoise = avg;
                        if (avg < minNoise) minNoise = avg;

                        numChunksGenerated++;

                        if (numChunksGenerated == CHUNKS_PER_REPORT) {
                            Settings.LOGGER.info(CHUNKS_PER_REPORT + " Chunks Generated Report");

                            Settings.LOGGER.info("--> Noise");
                            Settings.LOGGER.info("  > Average: {}", avgNoise);
                            Settings.LOGGER.info("  > Max: {}", maxNoise);
                            Settings.LOGGER.info("  > Min: {}", minNoise);

                            // Reset vals
                            numChunksGenerated = 0;

                            avgNoise = 0;
                            maxNoise = -10;
                            minNoise = 10;
                        }
                    }
                }
            }
        }
    }


    private void debugGenerate(int chunkX, int chunkZ, ChunkPrimer primer) {
    }
}
