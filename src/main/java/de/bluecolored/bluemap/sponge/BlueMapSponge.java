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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.SpongeExecutorService;
import org.spongepowered.api.world.storage.WorldProperties;

import com.flowpowered.math.vector.Vector2i;
import com.google.common.collect.Lists;

import de.bluecolored.bluemap.logger.Logger;
import de.bluecolored.bluemap.render.RenderManager;
import de.bluecolored.bluemap.render.TileRenderer;
import de.bluecolored.bluemap.render.hires.HiresModelManager;
import de.bluecolored.bluemap.render.lowres.LowresModelManager;
import de.bluecolored.bluemap.resourcepack.NoSuchResourceException;
import de.bluecolored.bluemap.resourcepack.ResourcePack;
import de.bluecolored.bluemap.sponge.BlueMapConfig.MapConfig;
import de.bluecolored.bluemap.sponge.impl.BlockStateResourceNameMapper;
import de.bluecolored.bluemap.sponge.impl.WorldImpl;
import de.bluecolored.bluemap.sponge.task.RenderTaskManager;
import de.bluecolored.bluemap.web.BlueMapWebServer;
import de.bluecolored.bluemap.web.WebSettings;

@Plugin(
		id = BlueMapSponge.PLUGIN_ID, 
		name = BlueMapSponge.PLUGIN_NAME,
		authors = { "Blue (Lukas Rieger)" },
		description = "This plugin provides a fully 3D map of your world!",
		version = BlueMapSponge.PLUGIN_VERSION
		)
public class BlueMapSponge {

	public static final String PLUGIN_ID = "bluemap";
	public static final String PLUGIN_NAME = "BlueMap";
	public static final String PLUGIN_VERSION = "1.0.0";
	
	private static Object plugin;
	
	@Inject
	@ConfigDir(sharedRoot = false)
	private Path configurationDir;

	private Logger logger;
	
	private BlueMapConfig config;
	private ResourcePack resourcePack;
	private BlockStateResourceNameMapper bsrnm;
	
	private WebSettings webSettings;
	private BlueMapWebServer webServer;

	private RenderManager renderManager;
	private SpongeExecutorService syncExecutor;
	private SpongeExecutorService asyncExecutor;
	
	private Collection<MapUpdater> mapUpdater;
	
	private Map<UUID, WorldImpl> worlds;
	private Map<String, MapType> maps;
	
	private RenderTaskManager renderTaskManager;

	@Inject
	public BlueMapSponge(org.slf4j.Logger logger) {
		plugin = this;
		
		this.logger = new Slf4jLogger(logger);
		
		this.mapUpdater = new ArrayList<>();
		this.worlds = new HashMap<>();
		this.maps = new HashMap<>();
	}
	
	private synchronized void load() throws IOException, NoSuchResourceException {
		worlds.clear();
		maps.clear();
		
		//load config
		config = BlueMapConfig.loadOrCreate(getConfigPath().resolve("bluemap.conf").toFile());

		//load resource-pack
		File defaultResource = getConfigPath().resolve("resourcepacks").resolve("DefaultResources.zip").toFile();
		File textureExportFile = config.getWebDataPath().resolve("textures.json").toFile();
		ResourcePack.createDefaultResource(defaultResource);
		resourcePack = new ResourcePack(Lists.newArrayList(defaultResource), textureExportFile, logger);
		
		//load name-mappings
		bsrnm = BlockStateResourceNameMapper.load();
		
		//load map-types
		for (MapConfig map : config.getMapConfigs()) {
			UUID worldUuid = null;
			try {
				worldUuid = UUID.fromString(map.getWorldId());
			} catch (IllegalArgumentException ex) {
				Optional<WorldProperties> optWorldProps = Sponge.getServer().getWorldProperties(map.getWorldId());
				if (optWorldProps.isPresent()) {
					worldUuid = optWorldProps.get().getUniqueId();
				}
			}
			
			WorldImpl world = null;
			if (worldUuid != null) {
				world = getWorld(worldUuid).orElse(null);
			}
			
			if (world == null) {
				logger.logWarning("Configured world for map '" + map.getId() + "' could not be found! Skipping..");
				continue;
			}
			
			logger.logInfo("Initializing map '" + map.getId() + "'...");
			
			HiresModelManager hiresModelManager = new HiresModelManager(
					config.getWebDataPath().resolve("hires").resolve(map.getId()),
					resourcePack,
					new Vector2i(map.getHiresTileSize(), map.getHiresTileSize()),
					getAsyncExecutor(),
					logger
					);
			
			LowresModelManager lowresModelManager = new LowresModelManager(
					config.getWebDataPath().resolve("lowres").resolve(map.getId()), 
					new Vector2i(map.getLowresPointsPerLowresTile(), map.getLowresPointsPerLowresTile()),
					new Vector2i(map.getLowresPointsPerHiresTile(), map.getLowresPointsPerHiresTile()),
					logger
					);
			
			TileRenderer tileRenderer = new TileRenderer(hiresModelManager, lowresModelManager, map);
			
			MapType mapType = new MapType(
					map.getId(),
					map.getName(),
					world,
					tileRenderer
					);
			
			maps.put(map.getId(), mapType);
		}

		//prepare render-manager
		if (renderManager != null) renderManager.shutdown();
		renderManager = new RenderManager(config.getRenderThreadCount());
		
		//prepare render-task-manager
		renderTaskManager = new RenderTaskManager(getConfigPath().resolve("scheduledRenderTasks.json").toFile(), logger);
		
		//prepare web-server
		if (webServer != null) webServer.close();
		if (config.isWebserverEnabled()) {
			webServer = new BlueMapWebServer(config, logger);
			webServer.updateWebfiles();
		}
		
		//prepare web-settings
		webSettings = new WebSettings(config.getWebDataPath().resolve("settings.json").toFile());
		for (MapType map : maps.values()) {
			webSettings.setName(map.getName(), map.getId());
			webSettings.setFrom(map.getTileRenderer(), map.getId());
		}
		for (BlueMapConfig.MapConfig map : config.getMapConfigs()) {
			webSettings.setHiresViewDistance(map.getHiresViewDistance(), map.getId());
			webSettings.setLowresViewDistance(map.getLowresViewDistance(), map.getId());
		}
		webSettings.save();
		
	}
	
	public synchronized void save() {
		for (MapType map : maps.values()) {
			map.getTileRenderer().save();
		}
	}
	
	private synchronized void start() {
		if (renderManager != null) {
			renderManager.start();
			
			try {
				File renderTicketFile = getConfigPath().resolve("scheduledRenderTickets.json").toFile();
				PersistanceUtil.loadRenderTickets(renderManager, renderTicketFile, getMapTypes());
				renderTicketFile.delete();
			} catch (IOException e) {
				logger.logError("Failed to load saved render-tickets", e);
			}
			
			try {
				renderTaskManager.loadAndResumeTasks(renderManager, getMapTypes());
			} catch (IOException e) {
				logger.logError("Failed to load saved render-tasks", e);
			}
		}
		if (config.isWebserverEnabled() && webServer != null) webServer.start();
		
		synchronized (mapUpdater) {
			for (MapType map : maps.values()) {
				MapUpdater updater = new MapUpdater(renderManager, map.getTileRenderer(), map.getWorld(), 60000);
				Sponge.getEventManager().registerListeners(this, updater);
				mapUpdater.add(updater);
			}
		}
		
	}
	
	private synchronized void stop() {
		synchronized (mapUpdater) {
			for (MapUpdater updater : mapUpdater) {
				Sponge.getEventManager().unregisterListeners(updater);
			}
			mapUpdater.clear();
		}
		
		if (webServer != null) {
			webServer.close();
			webServer = null;
		}
		
		if (renderManager != null) {
			renderManager.shutdown();
			
			try {
				renderManager.awaitShutdown(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {}
			
			try {
				renderTaskManager.stopAndSaveTasks();
			} catch (IOException e) {
				logger.logError("Failed to save running render-tasks", e);
			}
			
			try {
				PersistanceUtil.saveRenderTickets(renderManager, getConfigPath().resolve("scheduledRenderTickets.json").toFile());
			} catch (IOException e) {
				logger.logError("Failed to save remaining render-tickets", e);
			}
			
			renderManager = null;
		}
	}
	
	public synchronized void reload() {
		try {
			logger.logInfo("Reloading...");
		
			save();
			stop();
			
			logger.clearNoFloodLog();
			
			load();
			start();
			
			logger.logInfo("Reloaded successfully.");
		} catch (IOException | NoSuchResourceException e) {
			logger.logError("Failed to reload!", e);
		}
	}
	
	@Listener
	public void onInit(GameStartingServerEvent evt) {
		try {
			logger.logInfo("Loading...");

			syncExecutor = Sponge.getScheduler().createSyncExecutor(this);
			asyncExecutor = Sponge.getScheduler().createAsyncExecutor(this);

			load();
			start();
			
			logger.logInfo("Loaded successfully.");
		} catch (IOException | NoSuchResourceException e) {
			logger.logError("Failed to load!", e);
		}

		initCommands();
	}
	
	private void initCommands() {
		BlueMapCommands blueMapCommands = new BlueMapCommands(this);
		Sponge.getCommandManager().register(this, blueMapCommands.getCombinedCommand(), "bluemap");
	}
	
	@Listener
	public void onReload(GameReloadEvent evt){
		reload();
	}
	
	@Listener
	public void onStop(GameStoppingEvent evt){
		save();
		stop();
	}
	
	public Optional<WorldImpl> getWorld(UUID id){
		WorldImpl world = worlds.get(id);
		if (world == null) {
			if (!Sponge.getServer().getWorldProperties(id).isPresent()) return Optional.empty();
			
			world = new WorldImpl(getSyncExecutor(), bsrnm, id, getLogger());
			worlds.put(id, world);
		}
		return Optional.of(world);
	}
	
	public RenderManager getRenderManager() {
		return renderManager;
	}

	public BlueMapWebServer getWebServer() {
		return webServer;
	}
	
	public BlockStateResourceNameMapper getBlockStateResourceNameMapper(){
		return bsrnm;
	}
	
	public ResourcePack getResourcePack() {
		return resourcePack;
	}
	
	public SpongeExecutorService getSyncExecutor(){
		return syncExecutor;
	}
	
	public SpongeExecutorService getAsyncExecutor(){
		return asyncExecutor;
	}
	
	public Collection<MapType> getMapTypes(){
		return Collections.unmodifiableCollection(maps.values());
	}
	
	public Path getConfigPath(){
		return configurationDir;
	}
	
	public RenderTaskManager getRenderTaskManager() {
		return renderTaskManager;
	}
	
	public de.bluecolored.bluemap.logger.Logger getLogger(){
		return logger;
	}
	
	public static Object getPlugin() {
		return plugin;
	}
	
}
