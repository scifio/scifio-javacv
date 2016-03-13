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

import java.util.Arrays;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;

/**
 * A container for static methods to analyze test Imgs.
 * 
 * @author Johannes Schindelin
 */
public class TestImgStatistics {

	/**
	 * Calculate an image signature
	 *
	 * The image signature are 1st and 2nd order moments of the intensity and the coordinates.
	 * 
	 * @param image the image
	 * @return a signature
	 */
	public static<T extends RealType<T>> float[] signature(IterableInterval<T> image) {
		float[] result = new float[( image.numDimensions() + 1 ) * 2];
		signature( image, result );
		return result;
	}

	/**
	 * Calculate an image signature
	 *
	 * The image signature are 1st and 2nd order moments of the intensity and the coordinates.
	 * 
	 * @param image the image to analyze
	 * @param result a float array that will be filled with the (numDims + 1) values making up the signature
	 */
	public static<T extends RealType<T>> void signature(IterableInterval<T> image, float[] result) {
		Arrays.fill( result, 0 );
		Cursor<T> cursor = image.localizingCursor();
		int dim = cursor.numDimensions();
		int[] pos = new int[dim];
		while (cursor.hasNext()) {
			cursor.fwd();
			cursor.localize(pos);
			float value = cursor.get().getRealFloat();
			result[0] += value;
			result[dim + 1] += value * value;
			for( int i = 0; i < dim; i++ ) {
				result[i + 1] += value * pos[i];
				result[i + 1 + dim + 1] += value * pos[i] * pos[i];
			}
		}

		for( int i = 1; i < dim + 1; i++ ) {
			result[i] /= result[0];
			result[i + dim + 1] = ( float )Math.sqrt( result[i + dim + 1] / result[0] - result[i] * result[i] );
		}

		long[] dims = Intervals.dimensionsAsLongArray( image );
		float total = dims[0];
		for( int i = 1; i < dim; i++ )
			total *= dims[i];

		result[0] /= total;
		result[dim + 1] = ( float )Math.sqrt( result[dim + 1] / total - result[0] * result[0] );
	}

	/**
	 * Verify that an image has a certain image signature
	 *
	 * When it is hard/computationally expensive to calculate the values of the expected image, we need a quick test like this one.
	 * 
	 * @param image the image to analyze
	 * @param signature the expected signature
	 * @return whether the signature matches <b>exactly</b> (use {@link #matchSignature(Img, float[], float)} for less strict checking)
	 */
	public static<T extends RealType<T>> boolean matchSignature(IterableInterval<T> image, float[] signature) {
		float[] result = signature(image);
		return Arrays.equals( result, signature );
	}

	/**
	 * Verify that an image has a certain image signature, with fuzz
	 *
	 * When it is hard/computationally expensive to calculate the values of the expected image, we need a quick test like this one.
	 * 
	 * @param image the image to analyze
	 * @param signature the expected signature
	 * @param tolerance the tolerance of the check: elements of the signature deviating less than this value are still considered to match
	 * @return whether the signature matches
	 */
	public static<T extends RealType<T>> boolean matchSignature(IterableInterval<T> image, float[] signature, float tolerance) {
		float[] result = signature(image);
		for (int i = 0; i < signature.length; i++)
			if (Math.abs(result[i] - signature[i]) > tolerance)
				return false;
		return true;
	}

	/**
	 * Verify that two images match.
	 * 
	 * @param a the first image
	 * @param b the second image
	 * @param tolerance the tolerance regarding the images' signatures
	 * @return whether the images match
	 */
	public static<T extends RealType<T>> boolean match(IterableInterval<T> a, IterableInterval<T> b, float tolerance) {
		return matchSignature(a, signature(b), tolerance);
	}

}
