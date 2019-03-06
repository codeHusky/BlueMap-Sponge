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

import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.ChildCommandElementExecutor;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Locatable;
import org.spongepowered.api.world.storage.WorldProperties;

import de.bluecolored.bluemap.sponge.task.WorldRenderTask;

public class BlueMapCommands {

	private BlueMapSponge blueMap;
	
	public BlueMapCommands(BlueMapSponge blueMap) {
		this.blueMap = blueMap;
	}

	public CommandSpec getCombinedCommand() {
		ChildCommandElementExecutor childCommands = new ChildCommandElementExecutor(null, null, false);
		
		childCommands.register(getRenderCommand(), "render");
		childCommands.register(getSaveCommand(), "save");
		childCommands.register(getReloadCommand(), "reload");
		childCommands.register(getPauseCommand(), "pause");
		childCommands.register(getResumeCommand(), "resume");
		
		return CommandSpec.builder()
				.description(Text.of("BlueMap command"))
				.arguments(childCommands)
				.executor(childCommands)
				.build();
	}
	
	public CommandSpec getRenderCommand() {
		return CommandSpec.builder()
				.description(Text.of("BlueMap render command"))
				.arguments(GenericArguments.optional(GenericArguments.world(Text.of("world"))))
				.executor((source, arguments) -> {
					WorldProperties world = null;
					
					if (source instanceof Locatable) {
						world = ((Locatable) source).getWorld().getProperties();
					}
					
					if (arguments.hasAny("world")) {
						world = arguments.<WorldProperties>getOne("world").get();
					}
						
					if (world == null) throw new CommandException(Text.of("No world could be determined, you need to define one!"), true);
					
					for (MapType map : blueMap.getMapTypes()) {
						if (map.getWorld().getUUID().equals(world.getUniqueId())) {
							WorldRenderTask task;
							
							if (source instanceof Player) {
								task = new WorldRenderTask(blueMap.getRenderManager(), map, ((Player) source).getUniqueId());
							} else {
								task = new WorldRenderTask(blueMap.getRenderManager(), map);
							}
							
							task.start();
							blueMap.getRenderTaskManager().registerRenderTask(task);
						}
					}
					return CommandResult.success();
				})
				.build();
	}
	
	public CommandSpec getSaveCommand() {
		return CommandSpec.builder()
				.description(Text.of("BlueMap save command"))
				.executor((source, arguments) -> {
					blueMap.save();
					
					source.sendMessage(Text.of(TextColors.GREEN, "BlueMap saved!"));
					return CommandResult.success();
				})
				.build();
	}
	
	public CommandSpec getReloadCommand() {
		return CommandSpec.builder()
				.description(Text.of("BlueMap reload command"))
				.executor((source, arguments) -> {
					blueMap.reload();
					
					source.sendMessage(Text.of(TextColors.GREEN, "BlueMap reloaded!"));
					return CommandResult.success();
				})
				.build();
	}
	
	public CommandSpec getPauseCommand() {
		return CommandSpec.builder()
				.description(Text.of("BlueMap pause command"))
				.executor((source, arguments) -> {
					blueMap.getRenderManager().pause();
					
					source.sendMessage(Text.of(TextColors.GREEN, "BlueMap renders paused!"));
					return CommandResult.success();
				})
				.build();
	}
	
	public CommandSpec getResumeCommand() {
		return CommandSpec.builder()
				.description(Text.of("BlueMap resume command"))
				.executor((source, arguments) -> {
					blueMap.getRenderManager().resume();
					
					source.sendMessage(Text.of(TextColors.GREEN, "BlueMap renders resumed!"));
					return CommandResult.success();
				})
				.build();
	}
	
}
