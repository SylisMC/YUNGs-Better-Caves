package com.yungnickyoung.minecraft.bettercaves.world.carver;


import com.yungnickyoung.minecraft.bettercaves.BetterCaves;
import com.yungnickyoung.minecraft.bettercaves.config.ConfigLoader;
import com.yungnickyoung.minecraft.bettercaves.config.util.ConfigHolder;
import com.yungnickyoung.minecraft.bettercaves.util.ColPos;
import com.yungnickyoung.minecraft.bettercaves.world.carver.bedrock.FlattenBedrock;
import com.yungnickyoung.minecraft.bettercaves.world.carver.controller.CaveCarverController;
import com.yungnickyoung.minecraft.bettercaves.world.carver.controller.CavernCarverController;
import com.yungnickyoung.minecraft.bettercaves.world.carver.controller.RavineController;
import com.yungnickyoung.minecraft.bettercaves.world.carver.controller.WaterRegionController;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

public class BetterCavesCarver {
    private WorldGenLevel world;
    public long seed = 0;
    public ConfigHolder config;

    // Controllers
    private CaveCarverController   caveCarverController;
    private CavernCarverController cavernCarverController;
    private WaterRegionController  waterRegionController;
    private RavineController       ravineController;

    public BetterCavesCarver() {
    }

    // Override the default carver's method to use Better Caves carving instead.
    public void carve(ChunkAccess chunkIn, int chunkX, int chunkZ) {
        BitSet airCarvingMask = ((ProtoChunk) chunkIn).getOrCreateCarvingMask(GenerationStep.Carving.AIR);
        BitSet liquidCarvingMask = ((ProtoChunk) chunkIn).getOrCreateCarvingMask(GenerationStep.Carving.LIQUID);

        // Flatten bedrock into single layer, if enabled in user config
        if (config.flattenBedrock.get()) {
            FlattenBedrock.flattenBedrock(chunkIn, config.bedrockWidth.get());
        }

        // Determine surface altitudes in this chunk
        int[][] surfaceAltitudes = new int[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Map.Entry<Heightmap.Types, Heightmap> surfaceHeightmapEntry = chunkIn.getHeightmaps().stream()
                    .filter(entry -> entry.getKey() == Heightmap.Types.WORLD_SURFACE_WG)
                    .findFirst().orElse(null);
                Map.Entry<Heightmap.Types, Heightmap> oceanHeightmapEntry = chunkIn.getHeightmaps().stream()
                    .filter(entry -> entry.getKey() == Heightmap.Types.OCEAN_FLOOR_WG)
                    .findFirst().orElse(null);
                int surfaceHeight = surfaceHeightmapEntry == null ? 35 : surfaceHeightmapEntry.getValue().getFirstAvailable(x, z);
                int oceanHeight = oceanHeightmapEntry == null ? 35 : oceanHeightmapEntry.getValue().getFirstAvailable(x, z);
                surfaceAltitudes[x][z] = Math.min(surfaceHeight, oceanHeight);
            }
        }

        // Determine biomes in this chunk - used for flooded cave checking
        Map<Long, Biome> biomeMap = new HashMap<>();
        for (int x = chunkX * 16 - 2; x <= chunkX * 16 + 17; x++) {
            for (int z = chunkZ * 16 - 2; z <= chunkZ * 16 + 17; z++) {
                ColPos pos = new ColPos(x, z);
                biomeMap.put(pos.toLong(), world.getBiome(pos.toBlockPos()));
            }
        }

        // Determine liquid blocks for this chunk
        BlockState[][] liquidBlocks = waterRegionController.getLiquidBlocksForChunk(chunkX, chunkZ);

        // Carve chunk
        ravineController.carveChunk(chunkIn, chunkX, chunkZ, liquidBlocks, biomeMap, airCarvingMask, liquidCarvingMask);
        caveCarverController.carveChunk(chunkIn, chunkX, chunkZ, surfaceAltitudes, liquidBlocks, biomeMap, airCarvingMask, liquidCarvingMask);
        cavernCarverController.carveChunk(chunkIn, chunkX, chunkZ, surfaceAltitudes, liquidBlocks, biomeMap, airCarvingMask, liquidCarvingMask);

        // Set carving masks for features to use
        ((ProtoChunk) chunkIn).setCarvingMask(GenerationStep.Carving.AIR, airCarvingMask);
        ((ProtoChunk) chunkIn).setCarvingMask(GenerationStep.Carving.LIQUID, liquidCarvingMask);
    }

    /**
     * Initialize Better Caves generators and cave region controllers for this world.
     */
    public void initialize(WorldGenLevel worldIn) {
        // Extract world information
        this.world = worldIn;
        this.seed = worldIn.getSeed();
        String dimensionName = "";

        try {
            dimensionName = Objects.requireNonNull(world.registryAccess().dimensionTypes().getKey(world.dimensionType())).toString();
        } catch (NullPointerException e) {
            BetterCaves.LOGGER.error("ERROR: Unable to get dimension name! This could be a problem...");
        }

        // Load config from file for this dimension
        this.config = dimensionName.equals("") ? new ConfigHolder() : ConfigLoader.loadConfigFromFileForDimension(dimensionName);

        // Initialize controllers
        this.caveCarverController   = new CaveCarverController(worldIn, config);
        this.cavernCarverController = new CavernCarverController(worldIn, config);
        this.waterRegionController  = new WaterRegionController(worldIn, config);
        this.ravineController       = new RavineController(worldIn, config);

        BetterCaves.LOGGER.debug("BETTER CAVES WORLD CARVER INITIALIZED WITH SEED {} IN {}", seed, dimensionName);
    }

    public void setWorld(WorldGenLevel worldIn) {
        this.world = worldIn;
        this.caveCarverController.setWorld(worldIn);
        this.cavernCarverController.setWorld(worldIn);
        this.waterRegionController.setWorld(worldIn);
        this.ravineController.setWorld(worldIn);
    }

    public long getSeed() {
        return this.seed;
    }
}
