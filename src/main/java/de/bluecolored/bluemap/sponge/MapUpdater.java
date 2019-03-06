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
package de.bluecolored.bluemap.sponge;

import java.util.Optional;

import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.filter.type.Exclude;
import org.spongepowered.api.event.world.chunk.PopulateChunkEvent;
import org.spongepowered.api.world.Location;

import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3i;

import de.bluecolored.bluemap.api.World;
import de.bluecolored.bluemap.render.RenderManager;
import de.bluecolored.bluemap.render.TileRenderer;
import de.bluecolored.bluemap.render.WorldTile;

public class MapUpdater {

	private RenderManager renderManager;
	private TileRenderer tileRenderer;
	private World world;
	private long updateDelay;
	
	public MapUpdater(RenderManager renderManager, TileRenderer tileRenderer, World world, long updateDelay) {
		this.renderManager = renderManager;
		this.tileRenderer = tileRenderer;
		this.world = world;
		this.updateDelay = updateDelay;
	}
	
	private void updateBlock(Vector3i pos){
		Vector2i tilePos = tileRenderer.getHiresModelManager().posToTile(pos);
		WorldTile tile = new WorldTile(world, tilePos);
		renderManager.scheduleDelayedRender(tile, tileRenderer, updateDelay);
	}
	
	@Listener
	@Exclude({ChangeBlockEvent.Post.class, ChangeBlockEvent.Pre.class})
	public void onBlockChange(ChangeBlockEvent evt) {
		for (Transaction<BlockSnapshot> tr : evt.getTransactions()) {
			Optional<Location<org.spongepowered.api.world.World>> ow = tr.getFinal().getLocation();
			if (ow.isPresent()) {
				if (ow.get().getExtent().getUniqueId().equals(world.getUUID()))
					updateBlock(ow.get().getPosition().toInt());
			}
		}
	}
	
	@Listener
	public void onChunkPopulate(PopulateChunkEvent.Post evt) {
		if (!evt.getTargetChunk().getWorld().getUniqueId().equals(world.getUUID())) return;
		
		Vector3i min = evt.getTargetChunk().getBlockMin();
		Vector3i max = evt.getTargetChunk().getBlockMax();
		
		Vector3i xmin = new Vector3i(min.getX(), min.getY(), max.getZ());
		Vector3i xmax = new Vector3i(max.getX(), max.getY(), min.getZ());
		
		updateBlock(min);
		updateBlock(max);
		updateBlock(xmin);
		updateBlock(xmax);
	}
	
}
