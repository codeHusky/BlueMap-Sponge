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

import com.flowpowered.math.vector.Vector3i;

import de.bluecolored.bluemap.api.Block;
import de.bluecolored.bluemap.api.ChunkNotGeneratedException;
import de.bluecolored.bluemap.api.WorldChunk;
import de.bluecolored.bluemap.sponge.WorldUtil;
import de.bluecolored.bluemap.util.AABB;

public class WorldChunkImpl implements WorldChunk {

	private WorldImpl world;
	private AABB bounds;
	
	private boolean isGenerated;
	private boolean checkedGenerated;
	
	public WorldChunkImpl(WorldImpl world, AABB bounds) {
		this.world = world;
		this.bounds = bounds;
		
		this.isGenerated = false;
		this.checkedGenerated = false;
		
		//System.out.println("Created WorldChunk: " + bounds + " W:" + world.getName());
	}
	
	@Override
	public WorldImpl getWorld() {
		return world;
	}

	@Override
	public Block getBlock(Vector3i pos) throws ChunkNotGeneratedException {
		//if(!bounds.contains(pos)) throw new IndexOutOfBoundsException(pos + " is not inside this world-chunks's bounds " + bounds);
		
		return world.getBlock(pos);
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
			return new CachedWorldChunkImpl(world, boundaries);
		}
		
		return new WorldChunkImpl(world, boundaries);
	}

	@Override
	public boolean isGenerated() {
		if (!checkedGenerated){
			isGenerated = checkGenerated();
			checkedGenerated = true;
		}
		
		return isGenerated;
	}
	
	private boolean checkGenerated(){ 
		for (Vector3i chunk : WorldUtil.getContainedSpongeChunks(bounds)) {
			if(!world.ensureLoadedChunk(chunk)) return false;
		}
		
		return true;
	}

}
