/*
 * jPSXdec: PlayStation 1 Media Decoder/Converter in Java
 * Copyright (C) 2007-2010  Michael Sabin
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

package jpsxdec.modules.xa;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import jpsxdec.modules.DiscItemSerialization;
import jpsxdec.modules.DiscItemSaver;
import jpsxdec.modules.IdentifiedSector;
import jpsxdec.util.NotThisTypeException;

/** Represents a series of XA ADPCM sectors that combine to make an audio stream */
public class DiscItemXAAudioStream extends DiscItemAudioStream {
    /** Type identifier for this disc item. */
    public static final String TYPE_ID = "XA";

    /** Serialziation key for Channel. */
    private final static String CHANNEL_KEY = "Channel";
    /** CD stream channel number. Should be between 0 and 31, inclusive. */
    private final int _iChannel;
    
    /** Serialziation key for Samples. */
    private final static String SAMPLE_COUNT_KEY = "Samples";
    /** Total number of audio samples in this stream. */
    private final long _lngSampleCount;
    
    /** Serialziation key for sample rate. */
    private static final String SAMPLES_PER_SEC_KEY = "Samples/Sec";
    /** Sample rate of the audio stream. Should be either 37800 or 18900. */
    private final int _iSamplesPerSecond;

    /** Serialziation key for stereo. */
    private static final String STEREO_KEY = "Stereo?";
    /** If the audio is in stereo. */
    private final boolean _blnIsStereo;

    /** Serialziation key for bits/sample. */
    private final static String BITSPERSAMPLE_KEY = "Bits/Sample";
    /** ADPCM bits per sample that the audio is encoded as. Should be 4 or 8. */
    private final int _iBitsPerSample;

    /** Serialziation key for audio sector stride. */
    private final static String STRIDE_KEY = "Sector stride";
    /** Number of non-audio sectors between each audio sector. 
     *  Should be 4, 8, 16, or 32, or -1 if n/a (only one sector of audio).*/
    private final int _iSectorStride;

    /** Serialziation key for disc speed. */
    private final static String DISC_SPEED_KEY = "Disc speed";
    /** Speed that the disc must spin to properly play the audio stream.
     * <ul>
     * <li>1 for 1x (75 sectors/second)
     * <li>2 for 2x (150 sectors/second)
     * <li> -1 for unknown
     *     (unknown case should only occur if this audio
     *      stream is only one sector long)
     * </ul>
     */
    private int _iDiscSpeed;

    public DiscItemXAAudioStream(int iStartSector, int iEndSector,
            int iChannel, long lngSampleCount,
            int iSamplesPerSecond, boolean blnIsStereo, int iBitsPerSample,
            int iStride)
    {
        super(iStartSector, iEndSector);
        if (iChannel < 0 || iChannel >= 32) throw new IllegalArgumentException(
                "Channel " + iChannel + " is not between 0 and 31");
        if (iBitsPerSample != 4 && iBitsPerSample != 8) throw new 
                IllegalArgumentException("Bits/sample " + iBitsPerSample + " is not 4 or 8");
        if (iSamplesPerSecond != 37800 && iSamplesPerSecond != 18900)
            throw new IllegalArgumentException();
        if (lngSampleCount < 0) throw new IllegalArgumentException();
        if (iStride != -1 && iStride != 4 && iStride != 8 && iStride != 16 && iStride != 32) 
            throw new IllegalArgumentException("Illegal audio sector stride " + iStride);
        _lngSampleCount = lngSampleCount;
        _iSamplesPerSecond = iSamplesPerSecond;
        _blnIsStereo = blnIsStereo;
        _iChannel = iChannel;
        _iBitsPerSample = iBitsPerSample;
        _iSectorStride = iStride;

        if (_iSectorStride < 1) {
            _iDiscSpeed = -1;
        } else {
            _iDiscSpeed = SectorXA.calculateDiscSpeed(_iSamplesPerSecond, _blnIsStereo, _iSectorStride);
            if (_iDiscSpeed < 1)
                throw new RuntimeException("Disc speed calc doesn't add up.");
        }
    }

    public DiscItemXAAudioStream(DiscItemSerialization oFields)
            throws NotThisTypeException
    {
        super(oFields);
        
        String sStereo = oFields.getString(STEREO_KEY);
        if ("Yes".equals(sStereo))
            _blnIsStereo = true;
        else if ("No".equals(sStereo))
            _blnIsStereo = false;
        else throw new NotThisTypeException(STEREO_KEY + " field has invalid value: " + sStereo);
            
        _iSamplesPerSecond = oFields.getInt(SAMPLES_PER_SEC_KEY);
        _iChannel = oFields.getInt(CHANNEL_KEY);

        _lngSampleCount = oFields.getInt(SAMPLE_COUNT_KEY);
        
        _iBitsPerSample = oFields.getInt(BITSPERSAMPLE_KEY);
        _iSectorStride = oFields.getInt(STRIDE_KEY);

        String sDiscSpeed = oFields.getString(DISC_SPEED_KEY);
        if ("1x".equals(sDiscSpeed))
            _iDiscSpeed = 1;
        else if ("2x".equals(sDiscSpeed))
            _iDiscSpeed = 2;
        else if ("?".equals(sDiscSpeed))
            _iDiscSpeed = -1;
        else throw new NotThisTypeException(DISC_SPEED_KEY + " field has invalid value: " + sDiscSpeed);
    }
    
    @Override
    public DiscItemSerialization serialize() {
        DiscItemSerialization fields = super.superSerial(TYPE_ID);

        fields.addNumber(CHANNEL_KEY, _iChannel);
        fields.addString(STEREO_KEY, _blnIsStereo ? "Yes" : "No");
        fields.addNumber(SAMPLES_PER_SEC_KEY, _iSamplesPerSecond);
        fields.addNumber(BITSPERSAMPLE_KEY, _iBitsPerSample);
        fields.addNumber(SAMPLE_COUNT_KEY, _lngSampleCount);
        fields.addNumber(STRIDE_KEY, _iSectorStride);
        switch (_iDiscSpeed) {
            case 1:  fields.addString(DISC_SPEED_KEY, "1x"); break;
            case 2:  fields.addString(DISC_SPEED_KEY, "2x"); break;
            default: fields.addString(DISC_SPEED_KEY, "?"); break;
        }
        return fields;
    }

    @Override
    public int getHierarchyLevel() {
        return 100 + _iChannel;
    }

    public int getChannel() {
        return _iChannel;
    }
    
    public boolean isStereo() {
        return _blnIsStereo;
    }

    public int getSectorsPastEnd() {
        return super.getEndSector() + _iSectorStride - 1;
    }

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    public int getDiscSpeed() {
        return _iDiscSpeed;
    }

    public int getSampleRate() {
        return _iSamplesPerSecond;
    }

    public int getADPCMBitsPerSample() {
        return _iBitsPerSample;
    }

    public int getPresentationStartSector() {
        return getStartSector();
    }

    public AudioFormat getAudioFormat(boolean blnBigEndian) {
        return new AudioFormat(_iSamplesPerSecond, 16, _blnIsStereo ? 2 : 1, true, blnBigEndian);
    }

    public IAudioSectorDecoder makeDecoder(boolean blnBigEndian, double dblVolume) {
        return new XAConverter(blnBigEndian, dblVolume);
    }

    private class XAConverter implements IAudioSectorDecoder {

        private final XAADPCMDecoder __decoder;
        private IAudioReceiver __outFeed;
        private final byte[] __abTempBuffer = new byte[XAADPCMDecoder.BYTES_GENERATED_FROM_XAADPCM_SECTOR];
        private AudioFormat __format;

        public XAConverter(boolean blnBigEndian, double dblVolume) {
            __decoder = XAADPCMDecoder.create(getADPCMBitsPerSample(), isStereo(), blnBigEndian, dblVolume);
        }

        public void open(IAudioReceiver audioFeed) {
            __outFeed = audioFeed;
        }

        public void feedSector(IdentifiedSector sector) throws IOException {
            if (sector == null)
                return;
            
            if (sector.getSectorNumber() < getStartSector() ||
                sector.getSectorNumber() > getEndSector() ||
                sector.getChannel() != getChannel() ||
                !(sector instanceof SectorXA))
            {
                return;
            }

            SectorXA xaSector = (SectorXA) sector;
            if (xaSector.isStereo() != isStereo() ||
                xaSector.getBitsPerSample() != getADPCMBitsPerSample())
            {
                return;
            }

            __decoder.decode(xaSector.getIdentifiedUserDataStream(), __abTempBuffer);

            if (__format == null)
                __format = __decoder.getOutputFormat(xaSector.getSamplesPerSecond());

            __outFeed.write(__format, __abTempBuffer, 0, __abTempBuffer.length, xaSector.getSectorNumber());
        }

        public double getVolume() {
            return __decoder.getVolume();
        }

        public void setVolume(double dblVolume) {
            __decoder.setVolume(dblVolume);
        }

        public AudioFormat getOutputFormat() {
            return __decoder.getOutputFormat(getSampleRate());
        }

        public void reset() {
            __decoder.resetContext();
        }

        public int getEndSector() {
            return DiscItemXAAudioStream.this.getEndSector();
        }

        public int getStartSector() {
            return DiscItemXAAudioStream.this.getStartSector();
        }

        public int getPresentationStartSector() {
            return DiscItemXAAudioStream.this.getPresentationStartSector();
        }
    }

    public boolean isPartOfStream(SectorXA xaSector) {
        return xaSector.getBitsPerSample() == _iBitsPerSample &&
               xaSector.getChannel() == _iChannel &&
               xaSector.getBitsPerSample() == _iBitsPerSample;
    }

    @Override
    public DiscItemSaver getSaver() {
        return new XAAudioItemSaver(this);
    }

}