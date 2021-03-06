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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import de.bluecolored.bluemap.logger.Logger;
import de.bluecolored.bluemap.render.RenderManager;
import de.bluecolored.bluemap.sponge.MapType;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.gson.GsonConfigurationLoader;

public class RenderTaskManager {

	private File file;
	private Logger logger;
	
	private Collection<RenderTask> renderTasks;
	
	public RenderTaskManager(File file, Logger logger) {
		this.file = file;
		this.logger = logger;
		this.renderTasks = new HashSet<>();
	}
	
	public synchronized void loadAndResumeTasks(RenderManager renderManager, Collection<MapType> mapTypes) throws IOException {
		if (!file.exists()) return;
		
		GsonConfigurationLoader configurationLoader = GsonConfigurationLoader.builder().setFile(file).build();
		ConfigurationNode node = configurationLoader.load();
		
		WorldRenderTask.loadAndResumeTasks(this, renderManager, node, mapTypes, logger);
		
		file.delete();
	}
	
	public synchronized void stopAndSaveTasks() throws IOException {
		file.getParentFile().mkdirs();
		if (!file.exists()) file.createNewFile();
		
		GsonConfigurationLoader configLoader = GsonConfigurationLoader.builder().setFile(file).setIndent(1).build();
		ConfigurationNode taskNode = configLoader.createEmptyNode();
		
		for (RenderTask task : renderTasks) {
			task.interruptAndSave(taskNode);
		}
		
		renderTasks.clear();

		configLoader.save(taskNode);
	}
	
	public synchronized void registerRenderTask(RenderTask task) {
		renderTasks.add(task);
		task.registerListender(this::unregisterRenderTask);
	}
	
	public synchronized void unregisterRenderTask(RenderTask task) {
		renderTasks.remove(task);
	}
	
}
