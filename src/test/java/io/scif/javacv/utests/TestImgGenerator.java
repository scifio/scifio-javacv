/*
 * #%L
 * SCIFIO format for reading and converting movie file formats.
 * %%
 * Copyright (C) 2013 Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package io.scif.javacv.utests;

import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/**
 * Container for static methods to generate Imgs for tests.
 * 
 * @author Johannes Schindelin
 */
public class TestImgGenerator {

	/**
	 * Make a 2-4 dimensional test Img with slightly non-boring pixel values.
	 * 
	 * @param dims the desired dimensions
	 * @return the test Img
	 */
	public static Img<UnsignedByteType> makeGradientImage(final long... dims) {
		if (dims.length != 2 && dims.length != 3 && dims.length != 4) {
			throw new IllegalArgumentException("Cannot generate " + dims.length + "-dimensional gradient image");
		}
		final long width = dims[0];
		final long height = dims[1];
		final long sliceCount = dims.length > 2 ? dims[dims.length - 1] : 1;

		return makeImage(new Function() {

			private long previousSlice = -1;
			private double angle, c, s, factor;

			@Override
			public float calculate(final long... pos) {
				final long x = pos[0];
				final long y = pos[1];
				final long slice;
				if (pos.length < 3) slice = 0;
				else slice = pos[pos.length - 1] + (pos.length < 4 ? 0 : 4 * pos[pos.length - 2]);

				if (slice != previousSlice) {
					angle = slice * Math.PI / sliceCount;
					c = Math.cos(angle);
					s = Math.sin(angle);
					factor = 255 / Math.sqrt(width * width + height * height);
					previousSlice = slice;
				}

				return (float)(127 + factor * ((x - width / 2.0) * s - (y - height / 2.0) * c));
			}

		}, dims);
	}

	/**
	 * An interface for image generators
	 */
	public interface Function {
		public float calculate(final long... pos);
	}

	/**
	 * Generates a test Img.
	 * 
	 * @param function implicitly-defined pixel values
	 * @param dims the desired dimensions
	 * @return the test Img
	 */
	public static Img<UnsignedByteType> makeImage(final Function function, final long... dims) {
		return makeImage(new UnsignedByteType(), function, dims);
	}

	/**
	 * Generates a test Img.
	 * 
	 * @param type the desired pixel type
	 * @param function the desired pixel values
	 * @param dims the desired dimensions
	 * @return the test Img
	 */
	public static<T extends RealType<T> & NativeType< T >> Img<T> makeImage(final T type, final Function function, final long... dims) {
		ImgFactory<T> factory = new PlanarImgFactory<T>();
		Img<T> result = factory.create( dims, type );
		Cursor<T> cursor = result.cursor();
		final long[] pos = new long[cursor.numDimensions()];
		while (cursor.hasNext()) {
			cursor.fwd();
			cursor.localize(pos);
			float value = function.calculate(pos);
			cursor.get().setReal(value);
		}
		return result;
	}

}
