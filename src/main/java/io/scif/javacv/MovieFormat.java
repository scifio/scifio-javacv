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

package io.scif.javacv;

import io.scif.AbstractFormat;
import io.scif.AbstractMetadata;
import io.scif.AbstractParser;
import io.scif.AbstractWriter;
import io.scif.BufferedImagePlane;
import io.scif.FormatException;
import io.scif.ImageMetadata;
import io.scif.Plane;
import io.scif.config.SCIFIOConfig;
import io.scif.gui.AWTImageTools;
import io.scif.gui.BufferedImageReader;
import io.scif.io.RandomAccessInputStream;
import io.scif.io.RandomAccessOutputStream;
import io.scif.util.FormatTools;
import io.scif.util.SCIFIOMetadataTools;

import java.awt.image.BufferedImage;
import java.io.IOException;

import net.imglib2.meta.Axes;

import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import com.googlecode.javacv.FFmpegFrameGrabber;
import com.googlecode.javacv.FFmpegFrameRecorder;
import com.googlecode.javacv.FrameGrabber.Exception;
import com.googlecode.javacv.FrameRecorder.Exception;
import com.googlecode.javacv.cpp.opencv_core.IplImage;

/**
 * A SCIFIO format for reading and writing movies using JavaCV.
 * 
 * Underneath, JavaCV uses LibAV/FFMPEG, of course.
 * 
 * @author Johannes Schindelin
 */
@Plugin(type = MovieFormat.class)
public class MovieFormat extends AbstractFormat {

	@Parameter
	private LogService log;

	@Override
	public String getFormatName() {
		return "Movies (javacv/LibAV)";
	}

	@Override
	public String[] makeSuffixArray() {
		return new String[] { "avi", "mov","mp4", "flv", "mpg", "ogv" };
	}

	// -- Nested Classes --

	public static class Metadata extends AbstractMetadata {

		private static final long serialVersionUID = 1L;

		private int bitRate = 400000;
		private double frameRate = 25;

		@Override
		public void populateImageMetadata() {
		}

		public void setBitRate(int bitRate) {
			this.bitRate = bitRate;
		}

		public int getBitRate() {
			return bitRate;
		}

		public void setFrameRate(double frameRate) {
			this.frameRate = frameRate;
		}

		public double getFrameRate() {
			return frameRate;
		}

	}

	private static Metadata parseMetadata(final FFmpegFrameGrabber grabber, Metadata meta) throws IOException {
		if (meta.getImageCount() < 1) meta.createImageMetadata(1);
		final ImageMetadata iMeta = meta.get(0);
		try {
			grabber.start();
			meta.setFrameRate(grabber.getFrameRate());
			iMeta.setAxisLength(Axes.X, grabber.getImageWidth());
			iMeta.setAxisLength(Axes.Y, grabber.getImageHeight());
			iMeta.setAxisLength(Axes.TIME, grabber.getLengthInFrames());
			final BufferedImage image = grabber.grab().getBufferedImage();
			iMeta.setPixelType(AWTImageTools.getPixelType(image));
			iMeta.setAxisLength(Axes.Z, 1);
			iMeta.setBitsPerPixel(grabber.getBitsPerPixel());
			iMeta.setLittleEndian(false);
			iMeta.setMetadataComplete(true);
			iMeta.setFalseColor(false);
			return meta;
		} catch (FrameGrabber.Exception e) {
			throw new IOException(e);
		}
	}

	public static class Parser extends AbstractParser<Metadata> {

		@Override
		protected void typedParse(RandomAccessInputStream stream, Metadata meta, SCIFIOConfig config)
				throws IOException, FormatException {
			final FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(stream.getFileName());
			parseMetadata(grabber, meta);
			try {
				grabber.stop();
			} catch (FrameGrabber.Exception e) {
				throw new IOException(e);
			}
		}
	}

	public static class Writer extends AbstractWriter<Metadata> {

		private int nextPlaneIndex;
		private long width, height;
		private FFmpegFrameRecorder recorder;

		@Override
		public void setDest(RandomAccessOutputStream stream, int imageIndex)
				throws FormatException, IOException {
			throw new IllegalArgumentException("Cannot write to RandomAccessOutputStream");
		}

		@Override
		public void setDest(final String path, final int imageIndex)
				throws FormatException, IOException {
			if (imageIndex != 0) {
				throw new IllegalArgumentException("Illegal image index: " + imageIndex);
			}

			final Metadata metadata = getMetadata();
			width = metadata.get(imageIndex).getAxisLength(Axes.X);
			height = metadata.get(imageIndex).getAxisLength(Axes.Y);
			recorder = new FFmpegFrameRecorder(path, (int) width, (int) height);
			recorder.setFrameRate(metadata.getFrameRate());
			recorder.setVideoBitrate(metadata.getBitRate());
			try {
				recorder.start();
			} catch (FrameRecorder.Exception e) {
				throw new IOException(e);
			}

			nextPlaneIndex = 0;
		}

		@Override
		public synchronized void close() throws IOException {
			if (recorder == null) return;
			try {
				recorder.stop();
				recorder.release();
			} catch (FrameRecorder.Exception e) {
				throw new IOException(e);
			}
			recorder = null;
		}

		@Override
		public void writePlane(int imageIndex, long planeIndex, Plane plane,
				long[] min, long[] max) throws FormatException, IOException {
			if (imageIndex != 0) {
				throw new IllegalArgumentException("Invalid image index: " + imageIndex);
			}
			if (planeIndex != nextPlaneIndex) {
				throw new IllegalArgumentException("Out of sequence: "
						+ planeIndex + " (expected: " + nextPlaneIndex + ")");
			}
			Metadata meta = getMetadata();

			if (!SCIFIOMetadataTools.wholePlane(imageIndex, meta, min, max)) {
				throw new FormatException(
						"MovieWriter does not yet support saving image tiles.");
			}

			final BufferedImage image;
			if (!(plane instanceof BufferedImagePlane)) {
				final ImageMetadata imageMeta = meta.get(0);
				int type = imageMeta.getPixelType();
				image = AWTImageTools.makeImage(plane.getBytes(), (int) imageMeta.getAxisLength(Axes.X),
					(int) imageMeta.getAxisLength(Axes.Y), (int) imageMeta.getAxisLength(Axes.CHANNEL),
					imageMeta.getInterleavedAxisCount() > 0, FormatTools.getBytesPerPixel(type),
					FormatTools.isFloatingPoint(type), imageMeta.isLittleEndian(),
					FormatTools.isSigned(type));
			}
			else {
				image = ((BufferedImagePlane)plane).getData();
			}

			try {
				recorder.record(IplImage.createFrom(image));
			} catch (FrameRecorder.Exception e) {
				throw new IOException(e);
			}

			nextPlaneIndex++;
		}

		@Override
		protected String[] makeCompressionTypes() {
			return new String[0];
		}
	}

	public static class Reader extends BufferedImageReader<Metadata> {

		private FFmpegFrameGrabber grabber;
		private String path;
		private int nextPlaneIndex;

		@Override
		public String[] createDomainArray() {
			return new String[] { FormatTools.GRAPHICS_DOMAIN };
		}

		@Override
		public String getCurrentFile() {
			return grabber == null ? null : path;
		}

		@Override
		public void setSource(final String path) throws IOException {
			close();
			this.path = path;
			grabber = new FFmpegFrameGrabber(path);
			try {
				final Metadata meta = (Metadata)getFormat().createMetadata();
				setMetadata(parseMetadata(grabber, meta));
				grabber.start();
				nextPlaneIndex = 0;
			} catch (FrameGrabber.Exception e) {
				throw new IOException(e);
			} catch (FormatException e) {
				throw new IOException(e);
			}
		}

		@Override
		public void close() throws IOException {
			if (grabber == null) return;
			try {
				grabber.stop();
				grabber.release();
			} catch (FrameGrabber.Exception e) {
				throw new IOException(e);
			}
			grabber = null;
			path = null;
		}

		@Override
		public BufferedImagePlane openPlane(int imageIndex, long planeIndex,
				BufferedImagePlane plane, long[] min, long[] max)
				throws FormatException, IOException {
			return openPlane(imageIndex, planeIndex, plane, min, max, null);
		}

		@Override
		public BufferedImagePlane openPlane(int imageIndex, long planeIndex,
			BufferedImagePlane plane, long[] min, long[] max,
			SCIFIOConfig config) throws FormatException, IOException
		{
			if (imageIndex != 0) {
				throw new IllegalArgumentException("Illegal image index: " + imageIndex);
			}
			if (planeIndex != nextPlaneIndex) {
				throw new UnsupportedOperationException(
						"Out-of-sequence plane index: " + planeIndex
						+ " (expected: " + nextPlaneIndex + ")");
			}
			try {
				final BufferedImage image = grabber.grab().getBufferedImage();
				nextPlaneIndex++;
				plane.setData(AWTImageTools.getSubimage(image, false, (int) min[0], (int) min[1], (int) max[0], (int) max[1]));
				return plane;
			} catch (FrameGrabber.Exception e) {
				throw new IOException(e);
			}
		}
	}
}
