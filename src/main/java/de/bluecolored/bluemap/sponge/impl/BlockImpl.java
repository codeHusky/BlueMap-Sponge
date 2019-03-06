/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
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

import org.spongepowered.api.world.biome.BiomeType;

import com.flowpowered.math.vector.Vector3i;

import de.bluecolored.bluemap.api.Block;
import de.bluecolored.bluemap.api.BlockState;
import de.bluecolored.bluemap.api.World;

public class BlockImpl extends Block {

	private World world;
	private BlockState state;
	private Vector3i position;
	private double sunLight, blockLight;
	private String biome;
	private boolean isCulling;
	private boolean isOccluding;
	
	public BlockImpl(World world, BlockState state, Vector3i position, double sunLight, double blockLight, BiomeType biome, boolean isCulling, boolean isOccluding) {
		this.world = world;
		this.state = state;
		this.position = position;
		this.sunLight = sunLight;
		this.blockLight = blockLight;
		this.biome = biome.getId();
		this.isCulling = isCulling;
		this.isOccluding = isOccluding;
		
		if (this.biome.startsWith("minecraft:")){
			this.biome = this.biome.substring("minecraft:".length());
		}
	}

	@Override
	public BlockState getBlockState() {
		return state;
	}

	@Override
	public World getWorld() {
		return world;
	}

	@Override
	public Vector3i getPosition() {
		return position;
	}

	@Override
	public double getSunLightLevel() {
		return sunLight;
	}

	@Override
	public double getBlockLightLevel() {
		return blockLight;
	}

	@Override
	public boolean isCullingNeighborFaces() {
		return isCulling;
	}
	
	@Override
	public boolean isOccludingNeighborFaces() {
		return isOccluding;
	}

	@Override
	public String getBiome() {
		return biome;
	}

}
