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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

import com.flowpowered.math.vector.Vector2i;

import de.bluecolored.bluemap.render.RenderManager;
import de.bluecolored.bluemap.render.RenderManager.RenderTicket;
import de.bluecolored.bluemap.render.TileRenderer;
import de.bluecolored.bluemap.render.WorldTile;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.gson.GsonConfigurationLoader;

class PersistanceUtil {

	public static void saveRenderTickets(RenderManager renderManager, File file) throws IOException {
		file.getParentFile().mkdirs();
		if (!file.exists()) file.createNewFile();
		
		Collection<RenderTicket> renderTickets = renderManager.drainScheduledTickets();
		
		GsonConfigurationLoader configLoader = GsonConfigurationLoader.builder().setFile(file).setIndent(1).build();
		ConfigurationNode node = configLoader.createEmptyNode();
		
		for (RenderTicket ticket : renderTickets) {
			ConfigurationNode ticketNode = node.getAppendedNode();
			ticketNode.getNode("world").setValue(ticket.getTile().getWorld().getUUID().toString());
			ticketNode.getNode("tile", "x").setValue(ticket.getTile().getTile().getX());
			ticketNode.getNode("tile", "z").setValue(ticket.getTile().getTile().getY());
		}
		
		configLoader.save(node);
	}
	
	public static void loadRenderTickets(RenderManager renderManager, File file, Collection<MapType> mapTypes) throws IOException {
		if (!file.exists()) return;
		
		try {
			GsonConfigurationLoader configurationLoader = GsonConfigurationLoader.builder().setFile(file).build();
			ConfigurationNode node = configurationLoader.load();
			
			for (ConfigurationNode ticketNode : node.getChildrenList()) {
				UUID worldUUID = UUID.fromString(ticketNode.getNode("world").getString());
				for (MapType map : mapTypes) {
					if (!map.getWorld().getUUID().equals(worldUUID)) continue;
					
					TileRenderer renderer = map.getTileRenderer();
					WorldTile tile = new WorldTile(
							map.getWorld(), 
							new Vector2i(
									ticketNode.getNode("tile", "x").getInt(), 
									ticketNode.getNode("tile", "z").getInt()
									)
							);
					
					renderManager.scheduleRender(tile, renderer);
				}
			}
		} catch (IllegalArgumentException ex) {
			throw new IOException(ex);
		}
	}
	
}
