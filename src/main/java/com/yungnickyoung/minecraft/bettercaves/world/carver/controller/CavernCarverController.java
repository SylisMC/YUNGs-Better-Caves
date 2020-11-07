package com.yungnickyoung.minecraft.bettercaves.world.carver.controller;

import com.yungnickyoung.minecraft.bettercaves.BetterCaves;
import com.yungnickyoung.minecraft.bettercaves.config.BCSettings;
import com.yungnickyoung.minecraft.bettercaves.config.util.ConfigHolder;
import com.yungnickyoung.minecraft.bettercaves.enums.CavernType;
import com.yungnickyoung.minecraft.bettercaves.noise.FastNoise;
import com.yungnickyoung.minecraft.bettercaves.noise.NoiseColumn;
import com.yungnickyoung.minecraft.bettercaves.noise.NoiseUtils;
import com.yungnickyoung.minecraft.bettercaves.util.BetterCavesUtils;
import com.yungnickyoung.minecraft.bettercaves.util.ColPos;
import com.yungnickyoung.minecraft.bettercaves.world.carver.CarverNoiseRange;
import com.yungnickyoung.minecraft.bettercaves.world.carver.cavern.CavernCarver;
import com.yungnickyoung.minecraft.bettercaves.world.carver.cavern.CavernCarverBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

public class CavernCarverController {
    private WorldGenLevel world;
    private FastNoise cavernRegionController;
    private List<CarverNoiseRange> noiseRanges = new ArrayList<>();

    // Vars from config
    private boolean isDebugViewEnabled;
    private boolean isOverrideSurfaceDetectionEnabled;
    private boolean isFloodedUndergroundEnabled;

    public CavernCarverController(WorldGenLevel worldIn, ConfigHolder config) {
        this.world = worldIn;
        this.isDebugViewEnabled = config.debugVisualizer.get();
        this.isOverrideSurfaceDetectionEnabled = config.overrideSurfaceDetection.get();
        this.isFloodedUndergroundEnabled = config.enableFloodedUnderground.get();

        // Configure cavern region controller, which determines what type of cavern should be carved in any given region
        float cavernRegionSize = calcCavernRegionSize(config.cavernRegionSize.get(), config.cavernRegionCustomSize.get().floatValue());
        this.cavernRegionController = new FastNoise();
        this.cavernRegionController.SetSeed((int)world.getSeed() + 333);
        this.cavernRegionController.SetFrequency(cavernRegionSize);

        // Initialize all carvers using config options
        List<CavernCarver> carvers = new ArrayList<>();
        carvers.add(new CavernCarverBuilder(world.getSeed())
            .ofTypeFromConfig(CavernType.LIQUID, config)
            .debugVisualizerBlock(Blocks.REDSTONE_BLOCK.defaultBlockState())
            .build()
        );
        carvers.add(new CavernCarverBuilder(world.getSeed())
            .ofTypeFromConfig(CavernType.FLOORED, config)
            .debugVisualizerBlock(Blocks.GOLD_BLOCK.defaultBlockState())
            .build()
        );

        float spawnChance = config.cavernSpawnChance.get().floatValue() / 100f;
        int totalPriority = carvers.stream().map(CavernCarver::getPriority).reduce(0, Integer::sum);

        BetterCaves.LOGGER.debug("CAVERN INFORMATION");
        BetterCaves.LOGGER.debug("--> SPAWN CHANCE SET TO: " + spawnChance);
        BetterCaves.LOGGER.debug("--> TOTAL PRIORITY: " + totalPriority);

        carvers.removeIf(carver -> carver.getPriority() == 0);
        float totalDeadzonePercent = 1 - spawnChance;
        float deadzonePercent = carvers.size() > 1
            ? totalDeadzonePercent / (carvers.size() - 1)
            : totalDeadzonePercent;

        BetterCaves.LOGGER.debug("--> DEADZONE PERCENT: " + deadzonePercent + "(" + totalDeadzonePercent + " TOTAL)");

        float currNoise = -1f;

        for (CavernCarver carver : carvers) {
            BetterCaves.LOGGER.debug("--> CARVER");
            float rangeCDFPercent = (float)carver.getPriority() / totalPriority * spawnChance;
            float topNoise = NoiseUtils.simplexNoiseOffsetByPercent(currNoise, rangeCDFPercent);
            CarverNoiseRange range = new CarverNoiseRange(currNoise, topNoise, carver);
            noiseRanges.add(range);

            // Offset currNoise for deadzone region
            currNoise = NoiseUtils.simplexNoiseOffsetByPercent(topNoise, deadzonePercent);

            BetterCaves.LOGGER.debug("    --> RANGE PERCENT LENGTH WANTED: " + rangeCDFPercent);
            BetterCaves.LOGGER.debug("    --> RANGE FOUND: " + range);
        }
    }

    public void carveChunk(ChunkAccess chunk, int chunkX, int chunkZ, int[][] surfaceAltitudes, BlockState[][] liquidBlocks, Map<Long, Biome> biomeMap, BitSet airCarvingMask, BitSet liquidCarvingMask) {
        // Prevent unnecessary computation if caverns are disabled
        if (noiseRanges.size() == 0) {
            return;
        }

        boolean flooded = false;
        float smoothAmpFloodFactor = 1;

        for (int subX = 0; subX < 16 / BCSettings.SUB_CHUNK_SIZE; subX++) {
            for (int subZ = 0; subZ < 16 / BCSettings.SUB_CHUNK_SIZE; subZ++) {
                int startX = subX * BCSettings.SUB_CHUNK_SIZE;
                int startZ = subZ * BCSettings.SUB_CHUNK_SIZE;
                int endX = startX + BCSettings.SUB_CHUNK_SIZE - 1;
                int endZ = startZ + BCSettings.SUB_CHUNK_SIZE - 1;
                BlockPos startPos = new BlockPos(chunkX * 16 + startX, 1, chunkZ * 16 + startZ);
                BlockPos endPos = new BlockPos(chunkX * 16 + endX, 1, chunkZ * 16 + endZ);

                noiseRanges.forEach(range -> range.setNoiseCube(null));

                // Get max height in subchunk. This is needed for calculating the noise cube
                int maxHeight = 0;
                if (!isOverrideSurfaceDetectionEnabled) { // Only necessary if we aren't overriding surface detection
                    for (int x = startX; x < endX; x++) {
                        for (int z = startZ; z < endZ; z++) {
                            maxHeight = Math.max(maxHeight, surfaceAltitudes[x][z]);
                        }
                    }
                    for (CarverNoiseRange range : noiseRanges) {
                        CavernCarver carver = (CavernCarver) range.getCarver();
                        maxHeight = Math.max(maxHeight, carver.getTopY());
                    }
                }

                for (int offsetX = 0; offsetX < BCSettings.SUB_CHUNK_SIZE; offsetX++) {
                    for (int offsetZ = 0; offsetZ < BCSettings.SUB_CHUNK_SIZE; offsetZ++) {
                        int localX = startX + offsetX;
                        int localZ = startZ + offsetZ;
                        ColPos colPos = new ColPos(chunkX * 16 + localX, chunkZ * 16 + localZ);

                        if (isFloodedUndergroundEnabled && !isDebugViewEnabled) {
                            flooded = biomeMap.get(colPos.toLong()).getBiomeCategory() == Biome.BiomeCategory.OCEAN;
                            smoothAmpFloodFactor = BetterCavesUtils.getDistFactor(world, biomeMap, colPos, 2, flooded ? BetterCavesUtils.isNotOcean : BetterCavesUtils.isOcean);
                            if (smoothAmpFloodFactor <= .25) { // Wall between flooded and normal caves.
                                continue; // Continue to prevent unnecessary noise calculation
                            }
                        }

                        int surfaceAltitude = surfaceAltitudes[localX][localZ];
                        BlockState liquidBlock = liquidBlocks[localX][localZ];

                        // Get noise values used to determine cavern region
                        float cavernRegionNoise = cavernRegionController.GetNoise(colPos.getX(), colPos.getZ());

                        // Carve cavern using matching carver
                        for (CarverNoiseRange range : noiseRanges) {
                            if (!range.contains(cavernRegionNoise)) {
                                continue;
                            }
                            CavernCarver carver = (CavernCarver)range.getCarver();
                            int bottomY = carver.getBottomY();
                            int topY = isDebugViewEnabled ? carver.getTopY() : Math.min(surfaceAltitude, carver.getTopY());
                            if (isOverrideSurfaceDetectionEnabled) {
                                topY = carver.getTopY();
                                maxHeight = carver.getTopY();
                            }
                            float smoothAmp = range.getSmoothAmp(cavernRegionNoise) * smoothAmpFloodFactor;
                            if (range.getNoiseCube() == null) {
                                range.setNoiseCube(carver.getNoiseGen().interpolateNoiseCube(startPos, endPos, bottomY, maxHeight));
                            }
                            NoiseColumn noiseColumn = range.getNoiseCube().get(offsetX).get(offsetZ);
                            carver.carveColumn(chunk, colPos, topY, smoothAmp, noiseColumn, liquidBlock, flooded, flooded ? liquidCarvingMask : airCarvingMask);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * @return frequency value for cavern region controller
     */
    private float calcCavernRegionSize(String cavernRegionSize, float cavernRegionCustomSize) {
        switch (cavernRegionSize.toLowerCase()) {
            case "small":
                return .01f;
            case "large":
                return .005f;
            case "extralarge":
                return .001f;
            case "custom":
                return cavernRegionCustomSize;
            case "medium":
            default:
                return .007f;
        }
    }

    public void setWorld(WorldGenLevel worldIn) {
        this.world = worldIn;
    }
}
