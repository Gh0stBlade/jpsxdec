/*
 * jPSXdec: PlayStation 1 Media Decoder/Converter in Java
 * Copyright (C) 2013-2017  Michael Sabin
 * All rights reserved.
 *
 * Redistribution and use of the jPSXdec code or any derivative works are
 * permitted provided that the following conditions are met:
 *
 *  * Redistributions may not be sold, nor may they be used in commercial
 *    or revenue-generating business activities.
 *
 *  * Redistributions that are modified from the original source must
 *    include the complete source code, including the source code for all
 *    components used by a binary built from the modified sources. However, as
 *    a special exception, the source code distributed need not include
 *    anything that is normally distributed (in either source or binary form)
 *    with the major components (compiler, kernel, and so on) of the operating
 *    system on which the executable runs, unless that component itself
 *    accompanies the executable.
 *
 *  * Redistributions must reproduce the above copyright notice, this list
 *    of conditions and the following disclaimer in the documentation and/or
 *    other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package jpsxdec.discitems.savers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import jpsxdec.discitems.FrameNumber;
import jpsxdec.discitems.ISectorAudioDecoder;
import jpsxdec.formats.JavaImageFormat;
import jpsxdec.formats.RgbIntImage;
import jpsxdec.formats.YCbCrImage;
import jpsxdec.i18n.I;
import jpsxdec.i18n.ILocalizedMessage;
import jpsxdec.i18n.LocalizedFileNotFoundException;
import jpsxdec.psxvideo.bitstreams.BitStreamUncompressor;
import jpsxdec.psxvideo.mdec.Calc;
import jpsxdec.psxvideo.mdec.MdecDecoder;
import jpsxdec.psxvideo.mdec.MdecDecoder_double;
import jpsxdec.psxvideo.mdec.MdecException;
import jpsxdec.psxvideo.mdec.MdecInputStream;
import jpsxdec.psxvideo.mdec.MdecInputStreamReader;
import jpsxdec.util.BinaryDataNotRecognized;
import jpsxdec.util.ExposedBAOS;
import jpsxdec.util.Fraction;
import jpsxdec.util.ILocalizedLogger;
import jpsxdec.util.IO;
import jpsxdec.util.LoggedFailure;
import jpsxdec.util.aviwriter.AviWriter;
import jpsxdec.util.aviwriter.AviWriterDIB;
import jpsxdec.util.aviwriter.AviWriterMJPG;
import jpsxdec.util.aviwriter.AviWriterYV12;

/** Video Decoding Pipeline. 
 * The pipeline is a little complicated since each path is specific about
 * its inputs and outputs. Here are all the possible branches:
 *<pre>
 *  Bitstream -+-> File (Bitstream2File)
 *             |
 *             +-> Mdec (Bitstream2Mdec) -+-> File (Mdec2File)
 *                                        |
 *                                        +-> Jpeg (Mdec2Jpeg)
 *                                        |
 *                                        +-> MjpegAvi (Mdec2MjpegAvi)
 *                                        |
 *                                        +-> Decoded (Mdec2Decoded) -+-> JavaImage (Decoded2JavaImage)
 *                                                                    |
 *                                                                    +-> RgbAvi, YuvAvi, JYuvAvi (Decoded2...)
 *</pre>
 */
public class VDP {

    private static final Logger LOG = Logger.getLogger(VDP.class.getName());
    
    public interface GeneratedFileListener {
        void fileGenerated(@Nonnull File f);
    }

    public interface IBitstreamListener {
        void bitstream(@Nonnull byte[] abBitstream, int iSize,
                       @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure;
    }

    public static class Bitstream2File implements IBitstreamListener {

        @Nonnull
        private final FrameFileFormatter _formatter;
        @Nonnull
        private final ILocalizedLogger _log;
        @CheckForNull
        private GeneratedFileListener _fileGenListener;

        public Bitstream2File(@Nonnull FrameFileFormatter formatter, @Nonnull ILocalizedLogger log) {
            _formatter = formatter;
            _log = log;
        }

        public void bitstream(@Nonnull byte[] abBitstream, int iSize,
                              @Nonnull FrameNumber frameNumber, int iFrameEndSector)
        {
            File f = _formatter.format(frameNumber, _log);
            try {
                IO.makeDirsForFile(f);
            } catch (LocalizedFileNotFoundException ex) {
                _log.log(Level.SEVERE, ex.getSourceMessage(), ex);
                return;
            }
            
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(f);
                if (_fileGenListener != null)
                    _fileGenListener.fileGenerated(f);
                fos.write(abBitstream, 0, iSize);
            } catch (FileNotFoundException ex) {
                _log.log(Level.SEVERE, I.IO_OPENING_FILE_ERROR_NAME(f.toString()), ex);
            } catch (IOException ex) {
                _log.log(Level.SEVERE, I.FRAME_WRITE_ERR(f, frameNumber), ex); // TODO: use formatted frame number in messages
            } finally {
                IO.closeSilently(fos, LOG);
            }
        }

        public void setGenFileListener(@CheckForNull GeneratedFileListener listener) {
            _fileGenListener = listener;
        }
    }

    public static class Bitstream2Mdec implements IBitstreamListener {

        @Nonnull
        private final ILocalizedLogger _log;
        @CheckForNull
        private BitStreamUncompressor _uncompressor;
        @Nonnull
        private final IMdecListener _listener;

        public Bitstream2Mdec(@Nonnull IMdecListener mdecListener) {
            _listener = mdecListener;
            _log = _listener.getLog();
        }

        private @CheckForNull BitStreamUncompressor identify(@Nonnull byte[] abBitstream, int iBitstreamSize) {
            BitStreamUncompressor uncompressor;
            try {
                uncompressor = BitStreamUncompressor.identifyUncompressor(abBitstream, iBitstreamSize);
                _log.log(Level.INFO, I.VIDEO_FMT_IDENTIFIED(uncompressor.getName()));
            } catch (BinaryDataNotRecognized ex) {
                uncompressor = null;
            }
            return uncompressor;
        }
        private @CheckForNull BitStreamUncompressor resetUncompressor(@Nonnull byte[] abBitstream, int iBitstreamSize) {
            if (_uncompressor == null) {
                _uncompressor = identify(abBitstream, iBitstreamSize);
            } else {
                try {
                    _uncompressor.reset(abBitstream, iBitstreamSize);
                } catch (BinaryDataNotRecognized ex) {
                    _uncompressor = identify(abBitstream, iBitstreamSize);
                }
            }
            return _uncompressor;
        }

        public void bitstream(@Nonnull byte[] abBitstream, int iBitstreamSize, 
                              @Nonnull FrameNumber frameNumber, int iFrameEndSector)
                throws LoggedFailure
        {
            resetUncompressor(abBitstream, iBitstreamSize);
            if (_uncompressor == null) {
                ILocalizedMessage msg = I.UNABLE_TO_DETERMINE_FRAME_TYPE_FRM(frameNumber.toString());
                _log.log(Level.SEVERE, msg);
                _listener.error(msg, frameNumber, iFrameEndSector);
            } else
                _listener.mdec(_uncompressor, frameNumber, iFrameEndSector);
        }

    }

    /** Either
     * {@link #mdec(jpsxdec.psxvideo.mdec.MdecInputStream, jpsxdec.discitems.FrameNumber, int)}
     * or
     * {@link #error(jpsxdec.i18n.ILocalizedMessage, jpsxdec.discitems.FrameNumber, int)}
     * will be called for each frame. */
    public interface IMdecListener {
        void mdec(@Nonnull MdecInputStream mdecIn, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure;
        void error(@Nonnull ILocalizedMessage errMsg, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure;
        @Nonnull ILocalizedLogger getLog();
    }
    
    public static class Mdec2File implements IMdecListener {

        @Nonnull
        private final FrameFileFormatter _formatter;
        private final int _iTotalBlocks;
        @Nonnull
        private final ILocalizedLogger _log;
        @CheckForNull
        private GeneratedFileListener _fileGenListener;

        public Mdec2File(@Nonnull FrameFileFormatter formatter, int iWidth, int iHeight, @Nonnull ILocalizedLogger log) {
            _formatter = formatter;
            _iTotalBlocks = Calc.blocks(iHeight, iWidth);
            _log = log;
        }
        
        public void mdec(@Nonnull MdecInputStream mdecIn, @Nonnull FrameNumber frameNumber, int iFrameEndSector_ignored)
                throws LoggedFailure
        {
            File f = _formatter.format(frameNumber, _log);
            try {
                IO.makeDirsForFile(f);
            } catch (LocalizedFileNotFoundException ex) {
                _log.log(Level.SEVERE, ex.getSourceMessage(), ex);
                return; // just skip the file without failing
            }
            
            BufferedOutputStream bos = null;
            try {
                bos = new BufferedOutputStream(new FileOutputStream(f));
                if (_fileGenListener != null)
                    _fileGenListener.fileGenerated(f);
                try {
                    MdecInputStreamReader.writeMdecBlocks(mdecIn, bos, _iTotalBlocks);
                } catch (MdecException.ReadCorruption ex) {
                    _log.log(Level.SEVERE, I.FRAME_NUM_CORRUPTED(frameNumber.toString()), ex);
                } catch (MdecException.EndOfStream ex) {
                    _log.log(Level.SEVERE, I.FRAME_NUM_INCOMPLETE(frameNumber.toString()), ex);
                }
            } catch (FileNotFoundException ex) {
                _log.log(Level.SEVERE, I.IO_OPENING_FILE_ERROR_NAME(f.toString()), ex);
            } catch (IOException ex) {
                _log.log(Level.SEVERE, I.FRAME_WRITE_ERR(f, frameNumber), ex);
            } finally {
                IO.closeSilently(bos, LOG);
            }
        }

        public void error(@Nonnull ILocalizedMessage errMsg, @Nonnull FrameNumber frameNumber, int iFrameEndSector) {
            // error frames are simply not written
        }

        public @Nonnull ILocalizedLogger getLog() {
            return _log;
        }

        public void setGenFileListener(@CheckForNull GeneratedFileListener listener) {
            _fileGenListener = listener;
        }
    }

    
    public static class Mdec2Jpeg implements IMdecListener {

        @Nonnull
        private final FrameFileFormatter _formatter;
        @Nonnull
        private final jpsxdec.psxvideo.mdec.tojpeg.Mdec2Jpeg _jpegTranslator;
        @Nonnull
        private final ExposedBAOS _buffer = new ExposedBAOS();
        @Nonnull
        private final ILocalizedLogger _log;
        @CheckForNull
        private GeneratedFileListener _fileGenListener;

        public Mdec2Jpeg(@Nonnull FrameFileFormatter formatter, int iWidth, int iHeight, @Nonnull ILocalizedLogger log) {
            _formatter = formatter;
            _jpegTranslator = new jpsxdec.psxvideo.mdec.tojpeg.Mdec2Jpeg(iWidth, iHeight);
            _log = log;
        }

        public void mdec(@Nonnull MdecInputStream mdecIn, @Nonnull FrameNumber frameNumber, int iFrameEndSector) 
                throws LoggedFailure
        {
            File f = _formatter.format(frameNumber, _log);
            try {
                IO.makeDirsForFile(f);
            } catch (LocalizedFileNotFoundException ex) {
                _log.log(Level.SEVERE, ex.getSourceMessage(), ex);
                return; // just skip the file without failing
            }

            try {
                _jpegTranslator.readMdec(mdecIn);
            } catch (MdecException.TooMuchEnergy ex) {
                _log.log(Level.WARNING, I.JPEG_ENCODER_FRAME_FAIL(frameNumber), ex);
                return; // just skip the file without failing
            } catch (MdecException.ReadCorruption ex) {
                _log.log(Level.WARNING, I.FRAME_NUM_CORRUPTED(frameNumber.toString()), ex);
                return; // just skip the file without failing
            } catch (MdecException.EndOfStream ex) {
                _log.log(Level.WARNING, I.FRAME_NUM_INCOMPLETE(frameNumber.toString()), ex);
                return; // just skip the file without failing
            }

            _buffer.reset();
            try {
                _jpegTranslator.writeJpeg(_buffer);
            } catch (IOException ex) {
                throw new RuntimeException("Should not happen", ex);
            }

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(f);
                if (_fileGenListener != null)
                    _fileGenListener.fileGenerated(f);
                fos.write(_buffer.getBuffer(), 0, _buffer.size());
            } catch (FileNotFoundException ex) {
                _log.log(Level.SEVERE, I.IO_OPENING_FILE_ERROR_NAME(f.toString()), ex);
            } catch (IOException ex) {
                _log.log(Level.WARNING, I.FRAME_WRITE_ERR(f, frameNumber), ex);
            } finally {
                IO.closeSilently(fos, LOG);
            }
        }

        public void error(@Nonnull ILocalizedMessage errMsg, @Nonnull FrameNumber frameNumber, int iFrameEndSector) {
            // error frames are simply not written
        }

        public @Nonnull ILocalizedLogger getLog() {
            return _log;
        }

        public void setGenFileListener(@CheckForNull GeneratedFileListener listener) {
            _fileGenListener = listener;
        }
    }


    public static class Mdec2Decoded implements IMdecListener {

        @Nonnull
        private final MdecDecoder _decoder;
        @Nonnull
        private final ILocalizedLogger _log;
        @CheckForNull
        private IDecodedListener _listener;

        public Mdec2Decoded(@Nonnull MdecDecoder decoder, @Nonnull ILocalizedLogger log) {
            _decoder = decoder;
            _log = log;
        }

        public void mdec(@Nonnull MdecInputStream mdecIn, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure {
            if (_listener == null)
                throw new IllegalStateException("IDecodedListener must be set");
            try {
                _decoder.decode(mdecIn);
            } catch (MdecException.ReadCorruption ex) {
                _log.log(Level.SEVERE, I.FRAME_NUM_CORRUPTED(frameNumber.toString()), ex);
            } catch (MdecException.EndOfStream ex) {
                _log.log(Level.SEVERE, I.FRAME_NUM_INCOMPLETE(frameNumber.toString()), ex);
            }
            _listener.decoded(_decoder, frameNumber, iFrameEndSector);
        }

        public void error(@Nonnull ILocalizedMessage errMsg, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure {
            if (_listener == null)
                throw new IllegalStateException("IDecodedListener must be set");
            _listener.error(errMsg, frameNumber, iFrameEndSector);
        }

        public void setDecoded(@CheckForNull IDecodedListener decoded) {
            if (decoded == null)
                return;
            decoded.assertAcceptsDecoded(_decoder);
            _listener = decoded;
        }

        public @Nonnull ILocalizedLogger getLog() {
            return _log;
        }

    }

    public interface IDecodedListener {
        void decoded(@Nonnull MdecDecoder decoder, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure;
        void error(@Nonnull ILocalizedMessage errMsg, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure;
        void assertAcceptsDecoded(@Nonnull MdecDecoder decoder) throws IllegalArgumentException;
    }

    public static class Decoded2JavaImage implements IDecodedListener {

        @Nonnull
        private final FrameFileFormatter _formatter;
        @Nonnull
        private final String _sFmt;
        @Nonnull
        private final BufferedImage _rgbImg;
        @Nonnull
        private final ILocalizedLogger _log;
        @CheckForNull
        private GeneratedFileListener _fileGenListener;

        public Decoded2JavaImage(@Nonnull FrameFileFormatter formatter, @Nonnull JavaImageFormat eFmt, int iWidth, int iHeight, @Nonnull ILocalizedLogger log) {
            _formatter = formatter;
            _sFmt = eFmt.getId();
            _rgbImg = new BufferedImage(iWidth, iHeight, BufferedImage.TYPE_INT_RGB);
            _log = log;
        }
        
        public void decoded(@Nonnull MdecDecoder decoder, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure {
            decoder.readDecodedRgb(_rgbImg.getWidth(), _rgbImg.getHeight(),
                    ((DataBufferInt)_rgbImg.getRaster().getDataBuffer()).getData());
            
            File f = _formatter.format(frameNumber, _log);
            try {
                IO.makeDirsForFile(f);
            } catch (LocalizedFileNotFoundException ex) {
                _log.log(Level.SEVERE, ex.getSourceMessage(), ex);
                return;
            }

            try {
                if (ImageIO.write(_rgbImg, _sFmt, f)) {
                    if (_fileGenListener != null)
                        _fileGenListener.fileGenerated(f);
                } else {
                    _log.log(Level.WARNING, I.FRAME_FILE_WRITE_UNABLE(f, frameNumber));
                }
            } catch (IOException ex) {
                _log.log(Level.WARNING, I.FRAME_WRITE_ERR(f, frameNumber), ex);
            }
        }

        public void error(@Nonnull ILocalizedMessage errMsg, @Nonnull FrameNumber frameNumber, int iFrameEndSector) {
            // error frames are simply not written
        }

        public void assertAcceptsDecoded(@Nonnull MdecDecoder decoder) {}

        public void setGenFileListener(@CheckForNull GeneratedFileListener listener) {
            _fileGenListener = listener;
        }
    }

    // ########################################################################
    // ########################################################################
    // ########################################################################
    // ########################################################################

    /** Most Avi will take Decoded as input, but MJPG will need Mdec as input,
     *  so save the interface implementation for subclasses. */
    public static abstract class ToAvi implements ISectorAudioDecoder.ISectorTimedAudioWriter, Closeable {
        @Nonnull
        protected final File _outputFile;
        protected final int _iWidth, _iHeight;
        @Nonnull
        protected final VideoSync _vidSync;
        @CheckForNull
        private final AudioVideoSync _avSync;
        @CheckForNull
        protected final AudioFormat _af;
        @Nonnull
        protected final ILocalizedLogger _log;
        @CheckForNull
        protected AviWriter _writer;
        @CheckForNull
        protected GeneratedFileListener _fileGenListener;

        /** Video without audio. */
        public ToAvi(@Nonnull File outputFile, int iWidth, int iHeight, @Nonnull VideoSync vidSync, @Nonnull ILocalizedLogger log) {
            _outputFile = outputFile;
            _iWidth = iWidth; _iHeight = iHeight;
            _vidSync = vidSync; _avSync =  null;
            _af = null;
            _log = log;
        }

        /** Video with audio. */
        public ToAvi(@Nonnull File outputFile, int iWidth, int iHeight,
                     @Nonnull AudioVideoSync avSync, @Nonnull AudioFormat af, @Nonnull ILocalizedLogger log)
        {
            _outputFile = outputFile;
            _iWidth = iWidth; _iHeight = iHeight;
            _vidSync = _avSync = avSync;
            _af = af;
            _log = log;
        }

        final public @Nonnull File getOutputFile() {
            return _outputFile;
        }

        abstract public void open() 
                throws LocalizedFileNotFoundException, FileNotFoundException, IOException;

        // subclasses will implement IDecodedListener or IMdecListener to match this
        abstract public void error(@Nonnull ILocalizedMessage sErr, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure;

        final protected void prepForFrame(@Nonnull FrameNumber frameNumber, int iFrameEndSector) throws IOException {
            if (_writer == null)
                throw new IllegalStateException("Avi writer is not open");

            // if first frame
            if (_writer.getVideoFramesWritten() < 1 && _vidSync.getInitialVideo() > 0) {

                _log.log(Level.INFO, I.WRITING_BLANK_FRAMES_TO_ALIGN_AV(_vidSync.getInitialVideo()));
                _writer.writeBlankFrame();
                for (int i = _vidSync.getInitialVideo()-1; i > 0; i--) {
                    _writer.repeatPreviousFrame();
                }

            }

            int iDupCount = _vidSync.calculateFramesToCatchUp(
                                        iFrameEndSector,
                                        _writer.getVideoFramesWritten());

            if (iDupCount < 0) {
                // this does happen on occasion:
                // * A few frames get off pretty bad from fps
                // * Frames end early (like iki) so presentation sector is
                //   pretty off (but frame is ok)
                // TODO: fix when frame ends early
                // i.e. have a range of presentation sectors (ug)
                _log.log(Level.WARNING, I.FRAME_NUM_AHEAD_OF_READING(frameNumber, -iDupCount));
            } else {
                while (iDupCount > 0) { // could happen with first frame
                    if (_writer.getVideoFramesWritten() < 1) // TODO: fix design so this isn't needed
                        _writer.writeBlankFrame();
                    else
                        _writer.repeatPreviousFrame();
                    iDupCount--;
                }
            }
        }

        /** Writes audio. */
        final public void write(@Nonnull AudioFormat format, @Nonnull byte[] abData, int iStart, int iLen, @Nonnull Fraction presentationSector) throws LoggedFailure {
            if (_writer == null)
                throw new IllegalStateException("Avi writer is not open");

            // _avSync should not be null if this method is called
            try {
                if (_writer.getAudioSampleFramesWritten() < 1 &&
                    _avSync.getInitialAudio() > 0)
                {
                    _log.log(Level.INFO, I.WRITING_SILECE_TO_SYNC_AV(_avSync.getInitialAudio()));
                    _writer.writeSilentSamples(_avSync.getInitialAudio());
                }
                long lngNeededSilence = _avSync.calculateAudioToCatchUp(presentationSector, _writer.getAudioSampleFramesWritten());
                if (lngNeededSilence > 0) {
                    _log.log(Level.INFO, I.WRITING_SILENCE_TO_KEEP_AV_SYNCED(lngNeededSilence));
                    _writer.writeSilentSamples(lngNeededSilence);
                }

                _writer.writeAudio(abData, iStart, iLen);
            } catch (IOException ex) {
                throw new LoggedFailure(_log, Level.SEVERE, I.IO_WRITING_TO_FILE_ERROR_NAME(_writer.getFile().toString()), ex);
            }
        }

        public void close() throws IOException {
            if (_writer != null) {
                _writer.close();
            }
        }

        public void setGenFileListener(@CheckForNull GeneratedFileListener listener) {
            _fileGenListener = listener;
        }
    }

    static class Decoded2RgbAvi extends ToAvi implements IDecodedListener {
        @CheckForNull
        private AviWriterDIB _writerDib;
        @CheckForNull
        private int[] _aiImageBuf;

        public Decoded2RgbAvi(@Nonnull File outputFile, int iWidth, int iHeight, @Nonnull VideoSync vidSync, @Nonnull ILocalizedLogger log) {
            super(outputFile, iWidth, iHeight, vidSync, log);
        }

        public Decoded2RgbAvi(@Nonnull File outputFile, int iWidth, int iHeight, @Nonnull AudioVideoSync avSync, @Nonnull AudioFormat af, @Nonnull ILocalizedLogger log) {
            super(outputFile, iWidth, iHeight, avSync, af, log);
        }

        public void assertAcceptsDecoded(@Nonnull MdecDecoder decoder) {}

        public void open()
                throws LocalizedFileNotFoundException, FileNotFoundException, IOException
        {
            if (_writer == null) {
                IO.makeDirsForFile(_outputFile);
                _writer = _writerDib = new AviWriterDIB(_outputFile,
                                                        _iWidth, _iHeight,
                                                        _vidSync.getFpsNum(),
                                                        _vidSync.getFpsDenom(),
                                                        _af);
                if (_fileGenListener != null)
                    _fileGenListener.fileGenerated(_outputFile);
                _aiImageBuf = new int[_iWidth*_iHeight];
            }
        }

        public void decoded(@Nonnull MdecDecoder decoder, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure {
            if (_writerDib == null)
                throw new IllegalStateException("AVI not open.");
            decoder.readDecodedRgb(_writerDib.getWidth(), _writerDib.getHeight(), _aiImageBuf);
            try {
                prepForFrame(frameNumber, iFrameEndSector);
                _writerDib.writeFrameRGB(_aiImageBuf, 0, _writerDib.getWidth());
            } catch (IOException ex) {
                throw new LoggedFailure(_log, Level.SEVERE,
                        I.IO_WRITING_TO_FILE_ERROR_NAME(_writer.getFile().toString()), ex);
            }
        }

        public void error(@Nonnull ILocalizedMessage errMsg, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure {
            if (_writerDib == null)
                throw new IllegalStateException("AVI not open.");
            BufferedImage bi = makeErrorImage(errMsg, _writerDib.getWidth(), _writerDib.getHeight());
            RgbIntImage rgb = new RgbIntImage(bi);
            try {
                prepForFrame(frameNumber, iFrameEndSector);
                _writerDib.writeFrameRGB(rgb.getData(), 0, _writerDib.getWidth());
            } catch (IOException ex) {
                throw new LoggedFailure(_log, Level.SEVERE,
                        I.IO_WRITING_TO_FILE_ERROR_NAME(_writer.getFile().toString()), ex);
            }
        }

    }

    static class Decoded2YuvAvi extends ToAvi implements IDecodedListener {
        @CheckForNull
        protected YCbCrImage _yuvImgBuff;
        @CheckForNull
        protected AviWriterYV12 _writerYuv;

        public Decoded2YuvAvi(@Nonnull File outputFile, int iWidth, int iHeight, @Nonnull VideoSync vidSync, @Nonnull ILocalizedLogger log) {
            super(outputFile, iWidth, iHeight, vidSync, log);
        }

        public Decoded2YuvAvi(@Nonnull File outputFile, int iWidth, int iHeight, 
                              @Nonnull AudioVideoSync avSync, @Nonnull AudioFormat af, @Nonnull ILocalizedLogger log)
        {
            super(outputFile, iWidth, iHeight, avSync, af, log);
        }

        public void assertAcceptsDecoded(@Nonnull MdecDecoder decoder) throws IllegalArgumentException {
            if (!(decoder instanceof MdecDecoder_double))
                throw new IllegalArgumentException(getClass().getName() + " can't handle " + decoder.getClass().getName());
        }
        
        public void open()
                throws LocalizedFileNotFoundException, FileNotFoundException, IOException
        {
            if (_writer == null) {
                IO.makeDirsForFile(_outputFile);
                _writer = _writerYuv = new AviWriterYV12(_outputFile,
                                                         _iWidth, _iHeight,
                                                         _vidSync.getFpsNum(),
                                                         _vidSync.getFpsDenom(),
                                                         _af);
                if (_fileGenListener != null)
                    _fileGenListener.fileGenerated(_outputFile);
                _yuvImgBuff = new YCbCrImage(_iWidth, _iHeight);
            }
        }

        public void decoded(@Nonnull MdecDecoder decoder, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure {
            if (_writerYuv == null)
                throw new IllegalStateException("AVI not open.");
            // only accepts MdecDecoder_double, verified in assertAcceptsDecoded()
            ((MdecDecoder_double)decoder).readDecoded_Rec601_YCbCr420(_yuvImgBuff);
            try {
                prepForFrame(frameNumber, iFrameEndSector);
                _writerYuv.write(_yuvImgBuff.getY(), _yuvImgBuff.getCb(), _yuvImgBuff.getCr());
            } catch (IOException ex) {
                throw new LoggedFailure(_log, Level.SEVERE, 
                        I.IO_WRITING_TO_FILE_ERROR_NAME(_writer.getFile().toString()), ex);
            }
        }

        public void error(@Nonnull ILocalizedMessage errMsg, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure {
            if (_writerYuv == null)
                throw new IllegalStateException("AVI not open.");
            // TODO: write error with proper sample range
            BufferedImage bi = makeErrorImage(errMsg, _writerYuv.getWidth(), _writerYuv.getHeight());
            try {
                prepForFrame(frameNumber, iFrameEndSector);
                YCbCrImage yuv = new YCbCrImage(bi);
                _writerYuv.write(yuv.getY(), yuv.getCb(), yuv.getCr());
            } catch (IOException ex) {
                throw new LoggedFailure(_log, Level.SEVERE,
                        I.IO_WRITING_TO_FILE_ERROR_NAME(_writer.getFile().toString()), ex);
            }
        }

    }


    static class Decoded2JYuvAvi extends Decoded2YuvAvi {

        public Decoded2JYuvAvi(@Nonnull File outputFile, int iWidth, int iHeight, 
                               @Nonnull AudioVideoSync avSync, @Nonnull AudioFormat af, @Nonnull ILocalizedLogger log)
        {
            super(outputFile, iWidth, iHeight, avSync, af, log);
        }

        public Decoded2JYuvAvi(@Nonnull File outputFile, int iWidth, int iHeight, @Nonnull VideoSync vidSync, @Nonnull ILocalizedLogger log) {
            super(outputFile, iWidth, iHeight, vidSync, log);
        }

        @Override
        public void decoded(@Nonnull MdecDecoder decoder, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure {
            if (_writerYuv == null)
                throw new IllegalStateException("AVI not open.");
            // only accepts MdecDecoder_double, verified in assertAcceptsDecoded()
            ((MdecDecoder_double)decoder).readDecoded_JFIF_YCbCr420(_yuvImgBuff);
            try {
                prepForFrame(frameNumber, iFrameEndSector);
                _writerYuv.write(_yuvImgBuff.getY(), _yuvImgBuff.getCb(), _yuvImgBuff.getCr());
            } catch (IOException ex) {
                throw new LoggedFailure(_log, Level.SEVERE,
                        I.IO_WRITING_TO_FILE_ERROR_NAME(_writer.getFile().toString()), ex);
            }
        }
        
    }

    /** This Avi output is unique in that it takes Mdec as input instead of Decoded. */
    public static class Mdec2MjpegAvi extends ToAvi implements IMdecListener {
        @Nonnull
        private final jpsxdec.psxvideo.mdec.tojpeg.Mdec2Jpeg _jpegTranslator;
        @Nonnull
        private final ExposedBAOS _buffer = new ExposedBAOS();
        @CheckForNull
        private AviWriterMJPG _mjpegWriter;

        public Mdec2MjpegAvi(@Nonnull File outputFile, int iWidth, int iHeight, @Nonnull VideoSync vidSync, @Nonnull ILocalizedLogger log) {
            super(outputFile, iWidth, iHeight, vidSync, log);
            _jpegTranslator = new jpsxdec.psxvideo.mdec.tojpeg.Mdec2Jpeg(iWidth, iHeight);
        }

        public Mdec2MjpegAvi(@Nonnull File outputFile, int iWidth, int iHeight, @Nonnull AudioVideoSync avSync, @Nonnull AudioFormat af, @Nonnull ILocalizedLogger log) {
            super(outputFile, iWidth, iHeight, avSync, af, log);
            _jpegTranslator = new jpsxdec.psxvideo.mdec.tojpeg.Mdec2Jpeg(iWidth, iHeight);
        }

        public void open()
                throws LocalizedFileNotFoundException, FileNotFoundException, IOException
        {
            if (_writer == null) {
                IO.makeDirsForFile(_outputFile);
                _writer = _mjpegWriter = new AviWriterMJPG(_outputFile, _iWidth, _iHeight, _vidSync.getFpsNum(), _vidSync.getFpsDenom(), _af);
                if (_fileGenListener != null)
                    _fileGenListener.fileGenerated(_outputFile);
            }
        }

        public void mdec(@Nonnull MdecInputStream mdecIn, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure {
            if (_mjpegWriter == null)
                throw new IllegalStateException("AVI not open.");
            ILocalizedMessage err;
            Exception fail;
            try {
                _jpegTranslator.readMdec(mdecIn);
                _buffer.reset();
                try {
                    _jpegTranslator.writeJpeg(_buffer);
                } catch (IOException ex) {
                    throw new RuntimeException("Should not happen", ex);
                }

                try {
                    prepForFrame(frameNumber, iFrameEndSector);
                    _mjpegWriter.writeFrame(_buffer.getBuffer(), 0, _buffer.size());
                } catch (IOException ex) {
                    throw new LoggedFailure(_log, Level.SEVERE,
                            I.IO_WRITING_TO_FILE_ERROR_NAME(_writer.getFile().toString()), ex);
                }
                return;
                // kinda icky way to do this
            } catch (MdecException.ReadCorruption ex) {
                err = I.FRAME_NUM_CORRUPTED(frameNumber.toString());
                fail = ex;
            } catch (MdecException.EndOfStream ex) {
                err = I.FRAME_NUM_INCOMPLETE(frameNumber.toString());
                fail = ex;
            } catch (MdecException.TooMuchEnergy ex) {
                err = I.JPEG_ENCODER_FRAME_FAIL(frameNumber);
                fail = ex;
            }
            _log.log(Level.WARNING, err, fail);
            error(err, frameNumber, iFrameEndSector);
        }

        public void error(@Nonnull ILocalizedMessage errMsg, @Nonnull FrameNumber frameNumber, int iFrameEndSector) throws LoggedFailure {
            if (_mjpegWriter == null)
                throw new IllegalStateException("AVI not open.");
            try {
                prepForFrame(frameNumber, iFrameEndSector);
                _mjpegWriter.writeFrame(makeErrorImage(errMsg, _iWidth, _iHeight));
            } catch (IOException ex) {
                throw new LoggedFailure(_log, Level.SEVERE,
                        I.IO_WRITING_TO_FILE_ERROR_NAME(_writer.getFile().toString()), ex);
            }
        }

        public @Nonnull ILocalizedLogger getLog() {
            return _log;
        }

    }

    
    /** Draw the error onto a blank image. */
    private static @Nonnull BufferedImage makeErrorImage(@Nonnull ILocalizedMessage sErr, int iWidth, int iHeight) {
        BufferedImage bi = new BufferedImage(iWidth, iHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.white);
        g.drawString(sErr.getLocalizedMessage(), 5, 20);
        g.dispose();
        return bi;
    }
}
