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
import de.bluecolored.bluemap.util.AABB;

public class CachedWorldChunkImpl extends WorldChunkImpl {

	private Vector3i min; 
	private Vector3i size; 
	private Block[][][] blockCache;
	
	public CachedWorldChunkImpl(WorldImpl world, AABB bounds) {
		super(world, bounds);
		
		this.min = bounds.getMin().floor().toInt();
		this.size = bounds.getSize().floor().toInt().add(1, 1, 1);
	}
	
	@Override
	public Block getBlock(Vector3i pos) throws ChunkNotGeneratedException {
		//if(!bounds.contains(pos)) throw new IndexOutOfBoundsException(pos + " is not inside this world-chunks's bounds " + bounds);

		//initialise cache on first call to getBlock() to avoid useless memory-allocation
		if (blockCache == null) blockCache = new Block[size.getX()][size.getY()][size.getZ()];
		
		int x = pos.getX() - min.getX();
		int y = pos.getY() - min.getY();
		int z = pos.getZ() - min.getZ();
		
		Block block = blockCache[x][y][z];
		
		if (block == null){
			block = super.getBlock(pos);
			blockCache[x][y][z] = block; 
		}
		
		return block;
	}

}
