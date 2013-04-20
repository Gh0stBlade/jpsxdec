/*
 * jPSXdec: PlayStation 1 Media Decoder/Converter in Java
 * Copyright (C) 2007-2013  Michael Sabin
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

import java.io.FileNotFoundException;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import jpsxdec.cdreaders.CdFileSectorReader;
import jpsxdec.cdreaders.CdSector;
import jpsxdec.discitems.DiscItemAudioStream;
import jpsxdec.discitems.DiscItemVideoStream;
import jpsxdec.discitems.IDemuxedFrame;
import jpsxdec.discitems.ISectorAudioDecoder;
import jpsxdec.discitems.ISectorFrameDemuxer;
import jpsxdec.psxvideo.bitstreams.BitStreamUncompressor;
import jpsxdec.psxvideo.mdec.MdecDecoder_int;
import jpsxdec.psxvideo.mdec.MdecException;
import jpsxdec.psxvideo.mdec.idct.simple_idct;
import jpsxdec.sectors.IdentifiedSector;
import jpsxdec.util.NotThisTypeException;
import jpsxdec.util.player.AudioPlayer;
import jpsxdec.util.player.IAudioVideoReader;
import jpsxdec.util.player.IDecodableFrame;
import jpsxdec.util.player.ObjectPool;
import jpsxdec.util.player.VideoProcessor;

/** Holds all the class implementations that the {@link jpsxdec.util.player} 
 * framework needs to playback PlayStation audio and/or video. */
public class MediaPlayer implements IAudioVideoReader {

    private static final boolean DEBUG = false;

    private final int _iMovieStartSector;
    private final int _iMovieEndSector;
    private int _iSector;
    private final CdFileSectorReader _cdReader;

    //----------------------------------------------------------

    private final MdecDecoder_int _decoder;
    private final DiscItemVideoStream _vid;
    private int _iSectorsPerSecond;
    private BitStreamUncompressor _uncompressor;
    private ISectorFrameDemuxer _demuxer;
    private ISectorFrameDemuxer.ICompletedFrameListener _frameListener;

    public MediaPlayer(DiscItemVideoStream vid, ISectorFrameDemuxer demuxer)
            throws UnsupportedAudioFileException, IOException
    {
        this(vid, demuxer, vid.getStartSector(), vid.getEndSector());
    }


    public MediaPlayer(DiscItemVideoStream vid, ISectorFrameDemuxer demuxer, int iSectorStart, int iSectorEnd)
            throws UnsupportedAudioFileException, IOException
    {
        _cdReader = vid.getSourceCd();
        _iSector = _iMovieStartSector = iSectorStart;
        _iMovieEndSector = iSectorEnd;
        if (vid.getDiscSpeed() == 1) {
            _iSectorsPerSecond = 75;
        } else {
            // if disc speed is unknown, assume 2x
            _iSectorsPerSecond = 150;
        }

        _vid = vid;
        _demuxer = demuxer;
        _decoder = new MdecDecoder_int(new simple_idct(),
                                       vid.getWidth(),
                                       vid.getHeight());

    }

    //-----------------------------------------------------------------------

    private ISectorAudioDecoder _audioDecoder;
    private AudioPlayerSectorTimedWriter _audioOut;

    public MediaPlayer(DiscItemAudioStream aud)
            throws FileNotFoundException,
                   UnsupportedAudioFileException,
                   IOException
    {
        _cdReader = aud.getSourceCd();
        _iSector = _iMovieStartSector = aud.getStartSector();
        _iMovieEndSector = aud.getEndSector();
        if (aud.getDiscSpeed() == 1) {
            _iSectorsPerSecond = 75;
        } else {
            // if disc speed is unknown, assume 2x
            _iSectorsPerSecond = 150;
        }

        _audioDecoder = aud.makeDecoder(1.0);

        // ignore video
        _decoder = null;
        _vid = null;
    }
    
    //----------------------------------------------------------

    public MediaPlayer(DiscItemVideoStream vid, ISectorFrameDemuxer demuxer, ISectorAudioDecoder audio, int iSectorStart, int iSectorEnd) throws UnsupportedAudioFileException, IOException {
        // do the video init
        this(vid, demuxer, iSectorStart, iSectorEnd);

        if (audio.getDiscSpeed() == 1) {
            _iSectorsPerSecond = 75;
        } else {
            // if disc speed is unknown, assume 2x
            _iSectorsPerSecond = 150;
        }

        // manually init the audio
        _audioDecoder = audio;
    }

    public int readNext(final VideoProcessor vidProc, AudioPlayer audPlay) {

        try {

            if (!(_iSector < _iMovieEndSector)) {
                return -1;
            }

            CdSector cdSector = _cdReader.getSector(_iSector);
            IdentifiedSector identifiedSector = IdentifiedSector.identifySector(cdSector);
            if (identifiedSector != null) {
                // setup frame demuxer if it isn't setup yet
                if (vidProc != null) {
                    if (_frameListener == null) {
                        _frameListener = new ISectorFrameDemuxer.ICompletedFrameListener() {
                            public void frameComplete(IDemuxedFrame frame) throws IOException {
                                StrFrame strFrame = _framePool.borrow();
                                strFrame.init(frame.getDemuxSize(), frame.getFrame(), frame.getPresentationSector() - _iMovieStartSector);
                                strFrame.__abDemuxBuf = frame.copyDemuxData(strFrame.__abDemuxBuf);
                                vidProc.addFrame(strFrame);
                            }
                        };
                        _demuxer.setFrameListener(_frameListener);
                    }
                }
                // setup audio decoder if it isn't setup yet
                if (audPlay != null) {
                    if (_audioOut == null) {
                        _audioOut = new AudioPlayerSectorTimedWriter(audPlay, _iMovieStartSector, _iSectorsPerSecond, _audioDecoder.getSamplesPerSecond());
                        _audioDecoder.setAudioListener(_audioOut);
                    }
                }
                // if frame demuxer and audio decoder are the same object
                // don't feed the sector twice (this is currently only for
                // Crusader movies)
                if (_demuxer == _audioDecoder) {
                    _demuxer.feedSector(identifiedSector, null);
                } else {
                    if (_demuxer != null) {
                        _demuxer.feedSector(identifiedSector, null);
                    }
                    if (_audioDecoder != null) {
                        _audioDecoder.feedSector(identifiedSector, null);
                    }
                }
            }
            _iSector++;
            return (_iSector - _iMovieStartSector) * 100 / (_iMovieEndSector - _iMovieStartSector + 1);

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void reset() {
        if (_audioDecoder != null)
            _audioDecoder.reset();
        _iSector = _iMovieStartSector;
    }

    // #########################################################################
    // #########################################################################

    public AudioFormat getAudioFormat() {
        if (_audioDecoder == null)
            return null;
        return _audioDecoder.getOutputFormat();
    }


    // #########################################################################
    // #########################################################################

    private class DecodableFramePool extends ObjectPool<StrFrame> {

        @Override
        protected StrFrame createNewObject() {
            if (DEBUG) System.err.println("Creating new pool object.");
            return new StrFrame();
        }

    }
    private final DecodableFramePool _framePool = new DecodableFramePool();


    public boolean hasVideo() {
        return _vid != null;
    }

    public int getVideoWidth() {
        return _vid.getWidth();
    }

    public int getVideoHeight() {
        return _vid.getHeight();
    }


    private class StrFrame implements IDecodableFrame {

        public byte[] __abDemuxBuf;
        private int __iFrame;
        private int __iSectorFromStart;
        
        public void init(int iSize, int iFrame, int iSectorFromStart) {
            if (__abDemuxBuf == null || __abDemuxBuf.length < iSize)
                __abDemuxBuf = new byte[iSize];
            __iSectorFromStart = iSectorFromStart;
            __iFrame = iFrame;
        }

        public long getPresentationTime() {
            return (__iSectorFromStart * 1000000000L / _iSectorsPerSecond);
        }

        public void decodeVideo(int[] drawHere) {
            if (_uncompressor == null) {
                _uncompressor = BitStreamUncompressor.identifyUncompressor(__abDemuxBuf);
                if (_uncompressor == null) {
                    System.err.println("Unable to identify type of frame " + __iFrame);
                    return;
                }
            }
            try {
                _uncompressor.reset(__abDemuxBuf);
            } catch (NotThisTypeException ex) {
                _uncompressor = BitStreamUncompressor.identifyUncompressor(__abDemuxBuf);
                if (_uncompressor == null) {
                    System.err.println("Unable to identify type of frame " + __iFrame);
                    return;
                }
            }

            try {
                _decoder.decode(_uncompressor);
            } catch (MdecException.Decode ex) {
                System.err.print("Frame ");
                System.err.print(__iFrame);
                System.err.print(' ');
                System.err.print(ex.getMessage());
                if (ex.getCause() != null && ex.getCause().getMessage() != null)
                    System.err.println(": " + ex.getCause().getMessage());
                else
                    System.err.println();
            }

            _decoder.readDecodedRgb(getVideoWidth(), getVideoHeight(), drawHere);
        }

        public void returnToPool() {
            if (DEBUG) System.err.println("Returning object to pool.");
            _framePool.giveBack(this);
        }

    }

}