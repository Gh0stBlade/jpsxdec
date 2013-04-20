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

import argparser.ArgParser;
import argparser.BooleanHolder;
import argparser.IntHolder;
import argparser.StringHolder;
import java.util.List;
import jpsxdec.discitems.DiscItemSaverBuilder;
import jpsxdec.discitems.DiscItemVideoStream;
import jpsxdec.discitems.IDiscItemSaver;
import jpsxdec.formats.JavaImageFormat;
import jpsxdec.psxvideo.mdec.Calc;
import jpsxdec.psxvideo.mdec.MdecDecoder;
import jpsxdec.psxvideo.mdec.MdecDecoder_double_interpolate;
import jpsxdec.psxvideo.mdec.MdecDecoder_double_interpolate.Upsampler;
import jpsxdec.util.FeedbackStream;
import jpsxdec.util.Fraction;
import jpsxdec.util.Misc;
import jpsxdec.util.TabularFeedback;

/** Manages all possible options for saving PSX video. */
public abstract class VideoSaverBuilder extends DiscItemSaverBuilder {

    private final DiscItemVideoStream _sourceVidItem;

    public VideoSaverBuilder(DiscItemVideoStream vidItem) {
        _sourceVidItem = vidItem;
        resetToDefaults();
    }

    public void resetToDefaults() {
        setVideoFormat(VideoFormat.AVI_MJPG);
        setCrop(true);
        setDecodeQuality(MdecDecodeQuality.LOW);
        setChromaInterpolation(Upsampler.Bicubic);
        setJpgCompression(0.75f);
        setSaveStartFrame(_sourceVidItem.getStartFrame());
        setSaveEndFrame(_sourceVidItem.getEndFrame());
        setSingleSpeed(false);
        setAudioVolume(1.0);
    }

    public boolean copySettingsTo(DiscItemSaverBuilder otherBuilder) {
        if (otherBuilder instanceof VideoSaverBuilder) {
            VideoSaverBuilder other = (VideoSaverBuilder) otherBuilder;
            // only copy valid settings
            other.setVideoFormat(getVideoFormat());
            //other.setParallelAudio(getParallelAudio());
            if (getCrop_enabled())
                other.setCrop(getCrop());
            if (getDecodeQuality_enabled())
                other.setDecodeQuality(getDecodeQuality());
            if (getChromaInterpolation_enabled())
                other.setChromaInterpolation(getChromaInterpolation());
            if (getJpgCompression_enabled())
                other.setJpgCompression(getJpgCompression());
            if (getSingleSpeed_enabled())
                other.setSingleSpeed(getSingleSpeed());
            if (getAudioVolume_enabled())
                other.setAudioVolume(getAudioVolume());
            return true;
        }
        return false;
    }

    // .........................................................................

    public String getOutputBaseName() {
        return _sourceVidItem.getSuggestedBaseName().getPath();
    }

    public String getOutputPostfixStart() {
        return getVideoFormat().formatPostfix(_sourceVidItem, getStartFrame());
    }

    public String getOutputPostfixEnd() {
        return getVideoFormat().formatPostfix(_sourceVidItem, getEndFrame());
    }

    // .........................................................................

    private boolean _blnSingleSpeed;
    public boolean getSingleSpeed() {
        switch (findDiscSpeed()) {
            case 1:
                return true;
            case 2:
                return false;
            default:
                return _blnSingleSpeed;
        }
    }
    private int findDiscSpeed() {
        return _sourceVidItem.getDiscSpeed();
    }
    public void setSingleSpeed(boolean val) {
        _blnSingleSpeed = val;
        firePossibleChange();
    }
    public boolean getSingleSpeed_enabled() {
        return getVideoFormat().canSaveAudio() &&
               (findDiscSpeed() < 1);
    }
    public Fraction getFps() {
        return Fraction.divide( 
                getSingleSpeed() ? 75 : 150,
                _sourceVidItem.getSectorsPerFrame());
    }

    // .........................................................................

    private final List<VideoFormat> _imgFmtList = VideoFormat.getAvailable();
    public VideoFormat getVideoFormat_listItem(int i) {
        return _imgFmtList.get(i);
    }
    public int getVideoFormat_listSize() {
        return _imgFmtList.size();
    }

    private VideoFormat _videoFormat;
    public VideoFormat getVideoFormat() {
        return _videoFormat;
    }
    public void setVideoFormat(VideoFormat val) {
        _videoFormat = val;
        firePossibleChange();
    }

    // .........................................................................

    private float _jpgCompressionOption = 0.75f;
    public float getJpgCompression() {
        return _jpgCompressionOption;
    }
    public void setJpgCompression(float val) {
        _jpgCompressionOption = Math.max(Math.min(val, 1.f), 0.f);
        firePossibleChange();
    }

    public boolean getJpgCompression_enabled() {
        return getVideoFormat().hasCompression();
    }

    // .........................................................................

    private boolean _blnCrop = true;
    public boolean getCrop() {
        if (getCrop_enabled())
            return _blnCrop;
        else
            return false;
    }
    public void setCrop(boolean val) {
        _blnCrop = val;
        firePossibleChange();
    }
    public boolean getCrop_enabled() {
        return _sourceVidItem.shouldBeCropped() && getVideoFormat().isCroppable();
    }

    public int getWidth() {
        if (getCrop())
            return _sourceVidItem.getWidth();
        else
            return Calc.fullDimension(_sourceVidItem.getWidth());
    }

    public int getHeight() {
        if (getCrop())
            return _sourceVidItem.getHeight();
        else
            return Calc.fullDimension(_sourceVidItem.getHeight());
    }

    // .........................................................................

    public int getDecodeQuality_listSize() {
        return getVideoFormat().getDecodeQualityCount();
    }
    public MdecDecodeQuality getDecodeQuality_listItem(int i) {
        return getVideoFormat().getMdecDecodeQuality(i);
    }

    private MdecDecodeQuality _decodeQuality = MdecDecodeQuality.LOW;
    public MdecDecodeQuality getDecodeQuality() {
        if (getVideoFormat().getDecodeQualityCount() == 1)
            return getVideoFormat().getMdecDecodeQuality(0);
        return _decodeQuality;
    }
    public void setDecodeQuality(MdecDecodeQuality val) {
        _decodeQuality = val;
        firePossibleChange();
    }

    public boolean getDecodeQuality_enabled() {
        return getVideoFormat().getDecodeQualityCount() > 0;
    }

    // .........................................................................

    private Upsampler _mdecUpsampler = Upsampler.Bicubic;

    public boolean getChromaInterpolation_enabled() {
        return getDecodeQuality_enabled() && getDecodeQuality().canUpsample();
    }

    public Upsampler getChromaInterpolation_listItem(int i) {
        return Upsampler.values()[i];
    }

    public int getChromaInterpolation_listSize() {
        return Upsampler.values().length;
    }

    public Upsampler getChromaInterpolation() {
        if (getChromaInterpolation_enabled())
            return _mdecUpsampler;
        else
            return Upsampler.NearestNeighbor;
    }

    public void setChromaInterpolation(Upsampler val) {
        _mdecUpsampler = val;
        firePossibleChange();
    }

    // .........................................................................

    private int _iSaveStartFrame;
    public int getSaveStartFrame() {
        return _iSaveStartFrame;
    }
    public void setSaveStartFrame(int val) {
        _iSaveStartFrame = Math.max(val, _sourceVidItem.getStartFrame());
        _iSaveEndFrame = Math.max(_iSaveEndFrame, _iSaveStartFrame);
        firePossibleChange();
    }

    public int getStartFrame() {
        return _sourceVidItem.getStartFrame();
    }

    // .........................................................................
    
    private int _iSaveEndFrame;
    public int getSaveEndFrame() {
        return _iSaveEndFrame;
    }
    public void setSaveEndFrame(int val) {
        _iSaveEndFrame = Math.min(val, _sourceVidItem.getEndFrame());
        _iSaveStartFrame = Math.min(_iSaveEndFrame, _iSaveStartFrame);
        firePossibleChange();
    }

    public int getEndFrame() {
        return _sourceVidItem.getEndFrame();
    }

    ////////////////////////////////////////////////////////////////////////////

    public String[] commandLineOptions(String[] asArgs, FeedbackStream fbs) {
        if (asArgs == null) return null;
        
        ArgParser parser = new ArgParser("", false);

        //...........

        StringHolder vidfmt = new StringHolder();
        parser.addOption("-vidfmt,-vf %s", vidfmt);

        IntHolder jpg = null;
        if (JavaImageFormat.JPG.isAvailable()) {
            jpg = new IntHolder(-999);
            parser.addOption("-jpg %i", jpg);
        }

        BooleanHolder nocrop = new BooleanHolder(false);
        parser.addOption("-nocrop %v", nocrop); // only non demux & mdec formats

        StringHolder quality = new StringHolder();
        parser.addOption("-quality,-q %s", quality);

        StringHolder up = new StringHolder();
        parser.addOption("-up %s", up);

        //...........

        IntHolder discSpeed = new IntHolder(-10);
        parser.addOption("-ds %i {[1, 2]}", discSpeed);

        StringHolder frames = new StringHolder();
        parser.addOption("-frame,-frames,-f %s", frames);

        //...........
        
        BooleanHolder noaud = new BooleanHolder(false);
        parser.addOption("-noaud %v", noaud); // Only with AVI & audio

        BooleanHolder emulateav = new BooleanHolder(false);
        parser.addOption("-psxav %v", emulateav); // Only with AVI & audio

        BooleanHolder emulatefps = new BooleanHolder(false);
        parser.addOption("-psxfps %v", emulatefps); // Mutually excusive with fps...

        // -------------------------
        String[] asRemain = parser.matchAllArgs(asArgs, 0, 0);
        // -------------------------

        if (frames.value != null) {
            try {
                int iFrame = Integer.parseInt(frames.value);
                setSaveStartFrame(iFrame);
                setSaveEndFrame(iFrame);
            } catch (NumberFormatException ex) {
                int[] aiRange = Misc.splitInt(frames.value, "-");
                if (aiRange != null && aiRange.length == 2) {
                    setSaveStartFrame(aiRange[0]);
                    setSaveEndFrame(aiRange[1]);
                } else {
                    fbs.printlnWarn("Invalid frame(s) " + frames.value);
                }
            }
        }

        if (vidfmt.value != null) {
            VideoFormat vf = VideoFormat.fromCmdLine(vidfmt.value);
            if (vf != null) 
                setVideoFormat(vf);
             else 
                fbs.printlnWarn("Invalid video format " + vidfmt.value);
        }

        if (quality.value != null) {
            MdecDecodeQuality dq = MdecDecodeQuality.fromCmdLine(quality.value);
            if (dq != null)
                setDecodeQuality(dq);
            else
                fbs.printlnWarn("Invalid decode quality " + quality.value);
        }

        if (up.value != null) {
            Upsampler upsampler = Upsampler.fromCmdLine(up.value);
            if (up != null)
                setChromaInterpolation(upsampler);
            else
                fbs.printlnWarn("Invalid upsample quality " + up.value);
        }

        // make sure to process this after the video format is set
        if (jpg != null && jpg.value != -999) {
            if (jpg.value >= 0 && jpg.value <= 100)
                setJpgCompression(jpg.value / 100.f);
            else
                fbs.printlnWarn("Invalid jpg compression " + jpg.value);
        }

        setCrop(!nocrop.value);

        if (discSpeed.value == 1) {
            setSingleSpeed(true);
        } else if (discSpeed.value == 2) {
            setSingleSpeed(false);
        }

        return asRemain;
    }

    final public void printHelp(FeedbackStream fbs) {
        TabularFeedback tfb = new TabularFeedback();
        makeHelpTable(tfb);
        tfb.write(fbs);
    }

    /** Override to append additional help items. */
    protected void makeHelpTable(TabularFeedback tfb) {

        tfb.setRowSpacing(1);

        tfb.print("-vidfmt,-vf <format>").tab().print("Output video format (default avi:mjpg). Options:");
        tfb.indent();
        for (VideoFormat fmt : VideoFormat.values()) {
            if (fmt.isAvailable()) {
                tfb.ln().print(fmt.getCmdLine());
            }
        }
        tfb.newRow();

        tfb.print("-quality,-q <quality>").tab().println("Decoding quality (default low). Options:")
                              .indent().print(MdecDecodeQuality.getCmdLineList());
        tfb.newRow();

        tfb.print("-vf <format>").tab().println("Output video format (default avi). Options:")
                              .indent().print(VideoFormat.getCmdLineList());
        tfb.newRow();

        tfb.print("-up <upsampling>").tab().print("Chroma upsampling method (default NearestNeighbor). Options:")
                              .indent();
        for (Upsampler up : Upsampler.values()) {
            tfb.println("").print(up.name());
        }
        tfb.newRow();

        JavaImageFormat JPG = JavaImageFormat.JPG;
        if (JPG.isAvailable()) {
            tfb.print("-jpg <between 1 and 100>").tab()
                    .println("Output quality when saving as jpg or avi:mjpg")
                    .print("(default is 75).");
            tfb.newRow();
        }

        if (getSingleSpeed_enabled()) {
            tfb.newRow();
            tfb.print("-ds <disc speed>").tab().print("Specify 1 or 2 if disc speed is undetermined.");
        }
        
        tfb.newRow();
        tfb.print("-psxfps").tab().print("Emulate PSX FPS timing");

        if (_sourceVidItem.shouldBeCropped()) {
            tfb.newRow();
            tfb.print("-nocrop").tab().print("Don't crop data around unused frame edges.");
        }
    }

    /** Make the snapshot with the right demuxer and audio decoder. */
    abstract protected VideoSaverBuilderSnapshot makeSnapshot();

    /** Called by {@link #makeSnapshot()}. */
    final protected MdecDecoder makeVideoDecoder() {
        final MdecDecoder vidDecoder = getDecodeQuality().makeDecoder(
                _sourceVidItem.getWidth(), _sourceVidItem.getHeight());
        if (vidDecoder instanceof MdecDecoder_double_interpolate)
            ((MdecDecoder_double_interpolate)vidDecoder).setResampler(getChromaInterpolation());
        return vidDecoder;
    }

    final public IDiscItemSaver makeSaver() {

        VideoSaverBuilderSnapshot snap = makeSnapshot();

        VideoSaver writer;
        switch (snap.videoFormat) {
            case AVI_JYUV:
                writer = new VideoSavers.DecodedAviWriter_JYV12(snap); break;
            case AVI_YUV:
                writer = new VideoSavers.DecodedAviWriter_YV12(snap); break;
            case AVI_MJPG:
                writer = new VideoSavers.DecodedAviWriter_MJPG(snap); break;
            case AVI_RGB:
                writer = new VideoSavers.DecodedAviWriter_DIB(snap); break;
            case IMGSEQ_DEMUX:
                writer = new VideoSavers.BitstreamSequenceWriter(snap); break;
            case IMGSEQ_MDEC:
                writer = new VideoSavers.MdecSequenceWriter(snap); break;
            case IMGSEQ_JPG:
            case IMGSEQ_BMP:
            case IMGSEQ_PNG:
                writer = new VideoSavers.DecodedJavaImageSequenceWriter(snap); break;
            default:
                throw new UnsupportedOperationException(getVideoFormat() + " not implemented yet.");
        }
        return writer;

    }

    // audio related subclass methods

    private double _dblAudioVolume = 1.0;
    public double getAudioVolume() {
        return _dblAudioVolume;
    }
    public void setAudioVolume(double val) {
        _dblAudioVolume = Math.min(Math.max(0.0, val), 1.0);
        firePossibleChange();
    }
    abstract public boolean getAudioVolume_enabled();
    
    abstract boolean getEmulatePsxAvSync();
}