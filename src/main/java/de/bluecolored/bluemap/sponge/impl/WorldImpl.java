/*
 * This file is part of BlueMapSponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bluecolored.bluemap.sponge.impl;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Property;
import org.spongepowered.api.data.property.block.FullBlockSelectionBoxProperty;
import org.spongepowered.api.data.property.block.GroundLuminanceProperty;
import org.spongepowered.api.data.property.block.SkyLuminanceProperty;
import org.spongepowered.api.data.property.block.SolidCubeProperty;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.ChunkTicketManager.LoadingTicket;
import org.spongepowered.api.world.Location;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.Sets;

import de.bluecolored.bluemap.api.Block;
import de.bluecolored.bluemap.api.ChunkNotGeneratedException;
import de.bluecolored.bluemap.api.World;
import de.bluecolored.bluemap.logger.Logger;
import de.bluecolored.bluemap.util.AABB;

public class WorldImpl implements World {

	private static final Set<BlockType> OCCLUDING_BLOCKS = Collections.unmodifiableSet(Sets.newHashSet(
				BlockTypes.LEAVES,
				BlockTypes.LEAVES2
			));
	
	private ExecutorService syncExecutor;
	private Logger logger;
	private BlockStateResourceNameMapper bsrnm;
	private UUID uuid;
	private AABB bounds;
	private SoftReference<org.spongepowered.api.world.World> worldRef;
	
	@Nullable
	private LoadingTicket chunkLoadingTicket;
	
	public WorldImpl(ExecutorService syncExecutor, BlockStateResourceNameMapper bsrnm, UUID uuid, Logger logger) {
		this.syncExecutor = syncExecutor;
		this.bsrnm = bsrnm;
		this.uuid = uuid;

		org.spongepowered.api.world.World world = getSpongeWorld();
		
		this.bounds = new AABB(world.getBlockMin(), world.getBlockMax());
		
		/* TODO
		chunkLoadingTicket = Sponge.getServer().getChunkTicketManager().createTicket(BlueMapSponge.getPlugin(), world).orElse(null);
		if (chunkLoadingTicket != null) {
			chunkLoadingTicket.setNumChunks(Math.min(100, chunkLoadingTicket.getMaxNumChunks()));
			
			//System.out.println("Num Chunks: " + chunkLoadingTicket.getNumChunks() + " (" + chunkLoadingTicket.getMaxNumChunks() + ")");
		}
		*/
	}

	@Override
	public World getWorld() {
		return this;
	}
	
	@Override
	public Block getBlock(Vector3i pos) throws ChunkNotGeneratedException {
		//if(!bounds.contains(pos)) throw new IndexOutOfBoundsException(pos + " is not inside this world's bounds " + bounds);
	
		if (!ensureLoadedChunkAtBlock(pos)){
			throw new ChunkNotGeneratedException("Cannot access block for position: " + pos + ". Failed to load chunk!");
		}

		org.spongepowered.api.world.World world = getSpongeWorld();
		int sunHeight = world.getHighestYAt(pos.getX(), pos.getZ());

		//try to shortcut block generation
		if (pos.getY() > sunHeight){
			BlockType blockType = world.getBlockType(pos);
			if (blockType == BlockTypes.AIR){
				return new BlockImpl(
						this, 
						bsrnm.AIR, 
						pos,
						15d,
						0d, //incorrect value (this does't matter now but has to be corrected if we'd generate night-maps)
						world.getBiome(pos),
						false, 
						false
					);
			}
		}
		
		Location<org.spongepowered.api.world.World> location = world.getLocation(pos);
		BlockState blockState = location.getBlock().withExtendedProperties(location);
		
		double sunLight = getPropertyOrDefault(location, SkyLuminanceProperty.class, 15d);
		double blockLight = getPropertyOrDefault(location, GroundLuminanceProperty.class, 15d);
		boolean solidCube = getPropertyOrDefault(location, SolidCubeProperty.class, false);
		boolean fullBox = getPropertyOrDefault(location, FullBlockSelectionBoxProperty.class, false);
		boolean isCulling = fullBox && solidCube && sunLight == 0 && blockLight == 0;
		boolean isOccluding = isCulling || OCCLUDING_BLOCKS.contains(blockState.getType());
		
		return new BlockImpl(
			this, 
			bsrnm.map(blockState), 
			pos,
			sunLight,
			blockLight,
			location.getBiome(),
			isCulling, 
			isOccluding
		);
	}
	
	private <V, K> V getPropertyOrDefault(Location<org.spongepowered.api.world.World> loc, Class<? extends Property<K, V>> propertyClass, V defaultValue){
		Property<K, V> property = loc.getProperty(propertyClass).orElse(null);
		if (property == null) return defaultValue;
		return property.getValue();
	}

	@Override
	public AABB getBoundaries() {
		return bounds;
	}

	@Override
	public WorldChunkImpl getWorldChunk(AABB boundaries) {
		//if (!bounds.contains(boundaries.getMin()) || !bounds.contains(boundaries.getMax())) throw new IndexOutOfBoundsException(boundaries + " is not inside this world's bounds " + bounds);
		
		Vector3i size = boundaries.getSize().floor().toInt();
		int blockCount = size.getX() * size.getY() * size.getZ(); 
		if (blockCount < 300000){
			return new CachedWorldChunkImpl(this, boundaries);
		}
		
		return new WorldChunkImpl(this, boundaries);
	}

	@Override
	public boolean isGenerated() {
		return false;
	}

	@Override
	public String getName() {
		return getSpongeWorld().getName();
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public int getSeaLevel() {
		return getSpongeWorld().getSeaLevel();
	}
	
	@Override
	public Vector3i getSpawnPoint() {
		return getSpongeWorld().getSpawnLocation().getBlockPosition();
	}
	
	public org.spongepowered.api.world.World getSpongeWorld() {
		if (worldRef == null || worldRef.get() == null){
			try {
				worldRef = new SoftReference<org.spongepowered.api.world.World>(Sponge.getServer().getWorld(uuid).get());
			} catch (NoSuchElementException e){
				throw new RuntimeException("The world " + uuid + " is gone!");
			}
		}
		
		return worldRef.get();
	}
	
	/**
	 * Ensures that the chunk at that location is loaded and if not, tries to load it.
	 * @return true if successful, false if not
	 */
	protected boolean ensureLoadedChunkAtBlock(Vector3i blockPosition){
		Vector3i chunkPosition = new Vector3i(Math.floorDiv(blockPosition.getX(), 16), 0, Math.floorDiv(blockPosition.getZ(), 16));
		return ensureLoadedChunk(chunkPosition);
	}
		
	/**
	 * Ensures that the chunk at that location is loaded and if not, tries to load it.
	 * @return true if successful, false if not
	 */
	protected boolean ensureLoadedChunk(final Vector3i chunkPosition){
		final org.spongepowered.api.world.World world = getSpongeWorld();

		/* TODO
		if (chunkLoadingTicket != null) {
			chunkLoadingTicket.forceChunk(chunkPosition);
		}
		*/
		
		//most of the time it should be loaded and present
		if (world.getChunk(chunkPosition).isPresent()) return true;
		
		synchronized (this) {
			//if not, start the chunk-loading on the server Thread, but try to do it async 
			Future<CompletableFuture<Optional<Chunk>>> futureFuture = syncExecutor.submit(() -> {
				return world.loadChunkAsync(chunkPosition, false);
			});
	
			//wait for the futures to complete and return the result
			try {
				return futureFuture.get(10, TimeUnit.SECONDS).get(10, TimeUnit.SECONDS).isPresent();
			} catch (InterruptedException e) {
				return false;
			} catch (ExecutionException | TimeoutException e){
				logger.logError("Failed to load Chunk: " + chunkPosition, e);
				return false;
			}
		}
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof World) {
			return ((World) obj).getUUID().equals(getUUID());
		}
		
		return false;
	}
	
	@Override
	public int hashCode() {
		return getUUID().hashCode();
	}
	
	/* TODO
	@Override
	protected void finalize() throws Throwable {
		if (chunkLoadingTicket != null) {
			chunkLoadingTicket.release();
			chunkLoadingTicket = null;
		}
		
		super.finalize();
	}
	*/

}
