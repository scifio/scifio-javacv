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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.scif.Format;
import io.scif.FormatException;
import io.scif.img.ImgIOException;
import io.scif.img.ImgOpener;
import io.scif.img.ImgSaver;
import io.scif.javacv.MovieFormat;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;

import java.io.File;
import java.io.IOException;

import net.imglib2.IterableInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.io.location.FileLocation;

/**
 * Tests the SCIFIO format to read and write movies.
 * 
 * @author Johannes Schindelin
 */
public class MovieIOTest {

	private File file;
	private Context context;

	@Before
	public void setUp() {
		context = new Context();
	}

	@After
	public void cleanup() {
		if (file != null && file.exists()) {
			assertTrue(file.delete());
		}
		context.dispose();
	}

	@Test
	public void testFormat() throws FormatException {
		DatasetIOService datasetIOService = context.service(DatasetIOService.class);
		FormatService formatService = context.service(FormatService.class);
		Format format = formatService.getFormat(new FileLocation("formatTest.mpg"));
		assertEquals(MovieFormat.class, format.getClass());
	}

	@Test
	public void writeAndRead() throws IOException, ImgIOException, IncompatibleTypeException {
		final long width = 512, height = 512, frameCount = 30;
		final Img<UnsignedByteType> img = TestImgGenerator.makeGradientImage(width, height, frameCount);

		// for now, JavaCV writes .mpg files that are 2 frames too short
		final IterableInterval<UnsignedByteType> cropped =
				Views.iterable(Views.interval(img, new long[] { 0,  0, 0}, new long[] { width - 1, height - 1, frameCount - 3 }));

		final ImgSaver saver = new ImgSaver();
		final ImgPlus<UnsignedByteType> imgPlus =
				new ImgPlus<UnsignedByteType>(img, "test", new AxisType[] { Axes.X, Axes.Y, Axes.TIME });
		file = File.createTempFile("write-and-read-test", ".mpg");
		saver.saveImg(file.getAbsolutePath(), imgPlus);
		saver.saveImg(new FileLocation(file), imgPlus);

		final ImgOpener opener = new ImgOpener(context);
		Img<UnsignedByteType> img2 = (Img<UnsignedByteType>) opener.openImgs(new FileLocation(file)).get(0);
		assertTrue(TestImgStatistics.match(cropped, img2, 10));
	}

}
