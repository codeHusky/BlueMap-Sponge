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

import java.util.Iterator;

import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3i;

import de.bluecolored.bluemap.util.AABB;

public class WorldUtil {

	public static Iterable<Vector3i> getContainedSpongeChunks(AABB bounds){
		final Vector2i min = bounds.getMin().toVector2(true).div(16).floor().toInt();
		final Vector2i max = bounds.getMax().toVector2(true).div(16).floor().toInt();
		
		return new Iterable<Vector3i>() {
			@Override
			public Iterator<Vector3i> iterator() {
				return new Iterator<Vector3i>() {
					int x = min.getX();
					int z = min.getY();
					
					@Override
					public boolean hasNext() {
						return z <= max.getY() || x <= max.getX();
					}

					@Override
					public Vector3i next() {
						Vector3i next = new Vector3i(x, 0, z);
						
						x++;
						
						if (x > max.getX()) {
							z++;
							x = min.getX();
						}
						
						return next;
					}
				};
			}
		};
	}
	
}
