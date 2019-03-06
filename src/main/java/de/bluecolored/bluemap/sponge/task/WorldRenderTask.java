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
package de.bluecolored.bluemap.sponge.task;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;

import com.flowpowered.math.vector.Vector2i;

import de.bluecolored.bluemap.logger.Logger;
import de.bluecolored.bluemap.render.RenderManager;
import de.bluecolored.bluemap.render.RenderManager.RenderTicket;
import de.bluecolored.bluemap.render.WorldTile;
import de.bluecolored.bluemap.sponge.BlueMapSponge;
import de.bluecolored.bluemap.sponge.MapType;
import gnu.trove.set.hash.THashSet;
import gnu.trove.set.hash.TLinkedHashSet;
import ninja.leaping.configurate.ConfigurationNode;

public class WorldRenderTask extends RenderTask {

	private RenderManager renderManager;
	private MapType mapType;
	private UUID executor;
	
	private Set<Vector2i> closedTiles;
	private Set<Vector2i> openTiles;
	private Set<Vector2i> renderingTiles;
	private boolean terminate;
	
	private Task task;
	private long lastUpdate;
	
	private long startTime;
	private int renderedTiles;
	
	private RenderTicket lastScheduledTicket;
	
	public WorldRenderTask(RenderManager renderManager, MapType mapType) {
		this(renderManager, mapType, null);
	}

	public WorldRenderTask(RenderManager renderManager, MapType mapType, UUID executor) {
		this.renderManager = renderManager;
		this.mapType = mapType;
		this.executor = executor;
		
		this.closedTiles = new THashSet<>(1000);
		this.openTiles = new TLinkedHashSet<>(100);
		this.renderingTiles = new THashSet<>(100);
		
		this.terminate = false;
	}
	
	public synchronized void start() {
		openTiles.clear();
		closedTiles.clear();
		renderedTiles = 0;
		startTime = System.currentTimeMillis();
		lastUpdate = startTime;

		WorldTile start = new WorldTile(mapType.getWorld(), mapType.getTileRenderer().getHiresModelManager().posToTile(mapType.getWorld().getSpawnPoint()));
		openTiles.add(start.getTile());
		
		resume();
	}
	
	private void resume() {
		terminate = false;
		if (task != null) task.cancel();
		
		lastUpdate = System.currentTimeMillis();
		message(Text.of(TextColors.GREEN, "World render task started for map '" + mapType.getId() + "' on world '" + mapType.getWorld().getName() + "'..."));
		
		task = Sponge.getScheduler().createTaskBuilder()
				.async()
				.interval(1, TimeUnit.SECONDS)
				.execute(this::update)
				.submit(BlueMapSponge.getPlugin());
	}
	
	private void message(Text message) {
		Sponge.getServer().getConsole().sendMessage(message);
		Sponge.getServer().getPlayer(executor).ifPresent(p -> p.sendMessage(message));
	}
	
	public void terminate() {
		terminate = true;
		task.cancel();
		notifyFinished();
	}
	
	private synchronized void update(Task task) {
		if (terminate) {
			task.cancel();
			return;
		}
		
		while (openTiles.size() > 0 && renderManager.getScheduledTicketCount() < 100) {
			Vector2i next = openTiles.iterator().next();
			openTiles.remove(next);
			closedTiles.add(next);
			renderingTiles.add(next);
			WorldTile tile = new WorldTile(mapType.getWorld(), next);
			RenderTicket ticket = renderManager.scheduleRender(tile, mapType.getTileRenderer());
			ticket.addListener(this::rendered); // add listener so that the rendered() method gets called if the ticket has been processed
			lastScheduledTicket = ticket;
		}
		
		long now = System.currentTimeMillis();
		
		if (openTiles.isEmpty() && (lastScheduledTicket == null || lastScheduledTicket.isDone())) {
			terminate();

			message(Text.of(TextColors.DARK_GREEN, "World render task finished for map '" + mapType.getId() + "' on world '" + mapType.getWorld().getName() + "'!", Text.NEW_LINE, 
					TextActions.showText(Text.of(TextColors.GRAY, "(O:" + openTiles.size() + "|C:" + closedTiles.size() + "|R:" + renderManager.getScheduledTicketCount() + ")")), TextColors.GREEN, "Rendered " + renderedTiles + " tiles in " + ((now - startTime) / 60000) + " min!"));
		} else {
			if (lastUpdate + 60000 < now) {
				lastUpdate = now;
				
				message(Text.of(TextActions.showText(Text.of(TextColors.GRAY, "(O:" + openTiles.size() + "|C:" + closedTiles.size() + "|R:" + renderManager.getScheduledTicketCount() + ")")), TextColors.YELLOW, "Rendered " + renderedTiles + " tiles in " + ((now - startTime) / 60000) + " min!"));
			}
		}
	}
	
	private synchronized void rendered(RenderTicket ticket) {
		if (terminate) return;		
		if (!ticket.getTileRenderer().equals(mapType.getTileRenderer())) return;
		if (!ticket.getTile().getWorld().equals(mapType.getWorld())) return;

		renderingTiles.remove(ticket.getTile().getTile());
		
		//do not add surrounding tiles if render has thrown an error
		try {
			ticket.check();
		} catch (Throwable t){
			return;
		}
		
		renderedTiles++;
		
		Vector2i rel = ticket.getTile().getTile();
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				Vector2i tile = rel.add(x, z);
				if (!closedTiles.contains(tile)) openTiles.add(tile);
			}
		}
	}

	@Override
	public synchronized void interruptAndSave(ConfigurationNode node) {
		task.cancel();
		terminate = true;
		
		node = node.getNode("world-render").getAppendedNode();
		
		node.getNode("map-type").setValue(mapType.getId());
		node.getNode("world").setValue(mapType.getWorld().getUUID().toString());
		node.getNode("rendered-tiles").setValue(renderedTiles);
		node.getNode("start-time").setValue(startTime);
		if (executor != null) node.getNode("executor").setValue(executor.toString());
		
		ConfigurationNode closedTilesNode = node.getNode("closed-tiles");
		for (Vector2i tile : closedTiles) {
			ConfigurationNode tileNode = closedTilesNode.getAppendedNode();
			tileNode.getNode("x").setValue(tile.getX());
			tileNode.getNode("z").setValue(tile.getY());
		}
		
		ConfigurationNode openTilesNode = node.getNode("open-tiles");
		for (Vector2i tile : openTiles) {
			ConfigurationNode tileNode = openTilesNode.getAppendedNode();
			tileNode.getNode("x").setValue(tile.getX());
			tileNode.getNode("z").setValue(tile.getY());
		}

		for (Vector2i tile : renderingTiles) {
			ConfigurationNode tileNode = openTilesNode.getAppendedNode();
			tileNode.getNode("x").setValue(tile.getX());
			tileNode.getNode("z").setValue(tile.getY());
		}
		
	}

	@Override
	public boolean isFinished() {
		return terminate;
	}

	public static void loadAndResumeTasks(RenderTaskManager renderTaskManager, RenderManager renderManager, ConfigurationNode taskNode, Collection<MapType> mapTypes, Logger logger) {
		for (ConfigurationNode node : taskNode.getNode("world-render").getChildrenList()) {
			try {
				String mapTypeId = node.getNode("map-type").getString();
				
				MapType type = null;
				for (MapType t : mapTypes) {
					if (t.getId().equals(mapTypeId)) {
						type = t;
						break;
					}
				}
				
				if (type == null) {
					logger.logWarning("Could not resume world-render-task: No such map-type '" + mapTypeId + "'. Skipping...");
					continue;
				}
				
				if (!type.getWorld().getUUID().equals(UUID.fromString(node.getNode("world").getString()))) {
					logger.logWarning("Could not resume world-render-task: Map-type '" + mapTypeId + "' uses a different world now. Skipping...");
					continue;
				}
				
				WorldRenderTask task = new WorldRenderTask(renderManager, type);
				
				String executorString = node.getNode("executor").getString();
				if (executorString != null) task.executor = UUID.fromString(executorString);
				task.renderedTiles = node.getNode("rendered-tiles").getInt();
				task.startTime = node.getNode("start-time").getLong();
				
				for (ConfigurationNode tileNode : node.getNode("closed-tiles").getChildrenList()) {
					Vector2i tile = new Vector2i(
							tileNode.getNode("x").getInt(),
							tileNode.getNode("z").getInt()
							);
					
					task.closedTiles.add(tile);
				}
				
				for (ConfigurationNode tileNode : node.getNode("open-tiles").getChildrenList()) {
					Vector2i tile = new Vector2i(
							tileNode.getNode("x").getInt(),
							tileNode.getNode("z").getInt()
							);
					
					task.openTiles.add(tile);
				}
				
				task.resume();
				renderTaskManager.registerRenderTask(task);
				
			} catch (Throwable t) {
				logger.logError("Failed to load world-render-task", t);
			}
		}
	}
	
}
