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

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.api.CatalogType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.trait.BlockTrait;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import de.bluecolored.bluemap.api.BlockState;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;

public class BlockStateResourceNameMapper {

	private static Pattern BLOCKSTATE_SERIALIZATION_PATTERN = Pattern.compile("^(.+?)(?:\\[(.+)\\])?$");
	
	private Multimap<String, BlockMapping> mappings;
	private Cache<org.spongepowered.api.block.BlockState, BlockState> mappingCache;
	
	public final BlockState AIR;
	
	private BlockStateResourceNameMapper() throws IOException {
		mappings = HashMultimap.create();
		
		HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
				.setURL(getClass().getResource("/blockmapping.conf"))
				.build();
		
		ConfigurationNode node = loader.load();
		
		for (Entry<Object, ? extends ConfigurationNode> e : node.getChildrenMap().entrySet()){
			String key = e.getKey().toString();
			String value = e.getValue().getString();
			BlockState bsKey = blockStateFromString(key);
			BlockState bsValue = blockStateFromString(value);
			BlockMapping mapping = new BlockMapping(bsKey, bsValue);
				mappings.put(bsKey.getResourceId(), mapping);
		}
		
		mappings = Multimaps.unmodifiableMultimap(mappings);
		
		mappingCache = CacheBuilder.newBuilder()
				.concurrencyLevel(8) //TODO: find out configured thread count
				.maximumSize(10000)
				.build();
		
		AIR = map(BlockTypes.AIR.getDefaultState());
	}
	
	public BlockState map(org.spongepowered.api.block.BlockState from){
		BlockState to = mappingCache.getIfPresent(from);
		
		if (to == null){
			to = mapNoCache(from);
			mappingCache.put(from, to);
		}
		
		return to;
	}

	private BlockState mapNoCache(org.spongepowered.api.block.BlockState from){
		BlockState bs = blockStateFromSponge(from);

		for (BlockMapping bm : mappings.get(bs.getResourceId())){
			if (bm.fitsTo(bs)){
				BlockState mapped = bm.getMappedBlockState();
				
				Map<String, String> properties = new ConcurrentHashMap<>(); 
				
				//add original properties
				properties.putAll(bs.getProperties());
				
				//remove properties used in mapping 
				for (Entry<String, String> e : bm.getBlockState().getProperties().entrySet()){
					properties.remove(e.getKey(), e.getValue());
				}
				
				//add mapped properties
				properties.putAll(mapped.getProperties());
				
				return new BlockStateImpl(mapped.getResourceId(), properties);
			}
		}
		
		String id = bs.getResourceId();
		id = id.substring(id.lastIndexOf(':') + 1);
		
		return new BlockStateImpl(id, bs.getProperties());
	}
	
	private BlockState blockStateFromString(String serializedBlockState){
		
		Matcher m = BLOCKSTATE_SERIALIZATION_PATTERN.matcher(serializedBlockState);
		m.find();

		Map<String, String> pt = new ConcurrentHashMap<>();
		String g2 = m.group(2);
		if (g2 != null){
			String[] propertyStrings = g2.trim().split(",");
			for (String s : propertyStrings){
				String[] kv = s.split("=", 2);
				pt.put(kv[0], kv[1]);
			}
		}

		String blockId = m.group(1).trim();
		Map<String, String> properties = Collections.unmodifiableMap(pt);
		
		return new BlockStateImpl(blockId, properties);
	}
	
	private BlockState blockStateFromSponge(org.spongepowered.api.block.BlockState blockState){
		String blockId = blockState.getType().getId();
		
		Map<String, String> properties = new ConcurrentHashMap<>();
		for(BlockTrait<?> bt : blockState.getTraits()){
			
			Optional<?> oP = blockState.getTraitValue(bt);
			if (oP.isPresent()){
				Object prop = oP.get();
				
				String value;
				if (prop instanceof CatalogType){
					value = ((CatalogType) prop).getId();
				} else {
					value = prop.toString();
				}

				value = value.substring(value.lastIndexOf(':') + 1);
				
				properties.put(bt.getName(), value);
			}
		}
		
		return new BlockStateImpl(blockId, properties);
	}
	
	public static BlockStateResourceNameMapper load() throws IOException {
		return new BlockStateResourceNameMapper();
	}
	
	class BlockMapping {
		private BlockState blockState;
		private BlockState mappedBlockState;
		
		public BlockMapping(BlockState blockState, BlockState mappedBlockState) {
			this.blockState = blockState;
			this.mappedBlockState = mappedBlockState;
		}
		
		/**
		 * Returns true if the all the properties on this BlockMapping-key are the same in the provided BlockState.<br>
		 * Properties that are not defined in this Mapping are ignored on the provided BlockState.<br>
		 */
		public boolean fitsTo(BlockState blockState){
			if (!this.blockState.getResourceId().equals(blockState.getResourceId())) return false;
			for (Entry<String, String> e : this.blockState.getProperties().entrySet()){
				if (!blockState.getProperties().get(e.getKey()).equals(e.getValue())){
					return false;
				}
			}
			
			return true;
		}
		
		public BlockState getBlockState(){
			return blockState;
		}
		
		public BlockState getMappedBlockState(){
			return mappedBlockState;
		}
		
	}
	
}
