/*
 * jPSXdec: PlayStation 1 Media Decoder/Converter in Java
 * Copyright (C) 2007-2017  Michael Sabin
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

package jpsxdec.discitems.psxvideoencode;


import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import jpsxdec.cdreaders.CdFileSectorReader;
import jpsxdec.discitems.IDemuxedFrame;
import jpsxdec.discitems.savers.FrameLookup;
import jpsxdec.formats.RgbIntImage;
import jpsxdec.i18n.I;
import jpsxdec.i18n.UnlocalizedMessage;
import jpsxdec.psxvideo.bitstreams.BitStreamCompressor;
import jpsxdec.psxvideo.bitstreams.BitStreamUncompressor;
import jpsxdec.psxvideo.encode.MdecEncoder;
import jpsxdec.psxvideo.encode.ParsedMdecImage;
import jpsxdec.psxvideo.encode.PsxYCbCrImage;
import jpsxdec.psxvideo.mdec.Ac0Cleaner;
import jpsxdec.psxvideo.mdec.Calc;
import jpsxdec.psxvideo.mdec.MdecDecoder_double;
import jpsxdec.psxvideo.mdec.MdecException;
import jpsxdec.psxvideo.mdec.idct.StephensIDCT;
import jpsxdec.util.BinaryDataNotRecognized;
import jpsxdec.util.DeserializationFail;
import jpsxdec.util.ILocalizedLogger;
import jpsxdec.util.LocalizedIncompatibleException;
import jpsxdec.util.LoggedFailure;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ReplaceFramePartial extends ReplaceFrame {

    public static final String XML_TAG_NAME = "partial-replace";

    private int _iTolerance;
    @CheckForNull
    private File _imageMaskFile;
    @CheckForNull
    private Rectangle _rectMask;

    public ReplaceFramePartial(@Nonnull Element element) throws DeserializationFail {
        super(element);
        if (element.hasAttribute("tolerance")) {
            setTolerance(element.getAttribute("tolerance").trim());
        }
        if (element.hasAttribute("mask")) {
            setImageMaskFile(element.getAttribute("mask").trim());
        }
        if (element.hasAttribute("rect")) {
            setRectMask(element.getAttribute("rect").trim());
        }
    }
    @Override
    public @Nonnull Element serialize(@Nonnull Document document) {
        Element node = document.createElement(XML_TAG_NAME);
        node.setAttribute("frame", getFrameLookup().toString());
        node.setTextContent(getImageFile().toString());
        ImageFormat fmt = getFormat();
        if (fmt != null)
            node.setAttribute("format", fmt.serialize());
        if (_iTolerance > 0)
            node.setAttribute("tolerance", String.valueOf(_iTolerance));
        if (_imageMaskFile != null)
            node.setAttribute("mask", _imageMaskFile.toString());
        if (_rectMask != null)
            node.setAttribute("rect", String.format("%d,%d,%d,%d",
                    _rectMask.x, _rectMask.y, _rectMask.width, _rectMask.height));
        return node;
    }

    public ReplaceFramePartial(@Nonnull String sFrameNumber, @Nonnull String sImageFile) throws DeserializationFail {
        super(sFrameNumber, sImageFile);
    }
    public ReplaceFramePartial(@Nonnull String sFrameNumber, @Nonnull File imageFile) throws DeserializationFail {
        super(sFrameNumber, imageFile);
    }
    public ReplaceFramePartial(@Nonnull FrameLookup frameNumber, @Nonnull String sImageFile) {
        super(frameNumber, sImageFile);
    }
    public ReplaceFramePartial(@Nonnull FrameLookup frameNumber, @Nonnull File imageFile) {
        super(frameNumber, imageFile);
    }

    public @CheckForNull File getImageMaskFile() {
        return _imageMaskFile;
    }

    final public void setImageMaskFile(@Nonnull String sImageMaskFile) {
        setImageMaskFile(new File(sImageMaskFile));
    }
    public void setImageMaskFile(@CheckForNull File imageMaskFile) {
        _imageMaskFile = imageMaskFile;
    }

    public int getTolerance() {
        return _iTolerance;
    }

    final public void setTolerance(@Nonnull String sToleranceValue) {
        setTolerance(Integer.parseInt(sToleranceValue));
    }
    public void setTolerance(int iTolerance) {
        _iTolerance = iTolerance;
    }

    public @CheckForNull Rectangle getRectMask() {
        return _rectMask;
    }


    final public void setRectMask(@Nonnull String sRectMask) {
        String[] asCoords = sRectMask.trim().split("\\D+");
        if (asCoords.length != 4)
            throw new IllegalArgumentException("Invalid rectangle string " + sRectMask);
        setRectMask(new Rectangle(
                Integer.parseInt(asCoords[0]),
                Integer.parseInt(asCoords[1]),
                Integer.parseInt(asCoords[2]),
                Integer.parseInt(asCoords[3])));
    }
    public void setRectMask(@CheckForNull Rectangle rectMask) {
        _rectMask = rectMask;
    }

    @Override
    public void replace(@Nonnull IDemuxedFrame frame, @Nonnull CdFileSectorReader cd,
                        @Nonnull ILocalizedLogger log)
            throws LoggedFailure
    {
        File newImgFile = getImageFile();
        BufferedImage newImg;
        try {
            newImg = ImageIO.read(newImgFile);
        } catch (IOException ex) {
            throw new LoggedFailure(log, Level.SEVERE,
                                    I.IO_READING_FILE_ERROR_NAME(newImgFile.toString()), ex);
        }
        if (newImg == null)
            throw new LoggedFailure(log, Level.SEVERE, I.REPLACE_UNABLE_READ_IMAGE(newImgFile));

        final int WIDTH = frame.getWidth();
        final int HEIGHT = frame.getHeight();
        if (newImg.getWidth() < WIDTH || newImg.getHeight() < HEIGHT)
            throw new LoggedFailure(log, Level.SEVERE, I.REPLACE_FRAME_DIMENSIONS_TOO_SMALL());

        // 1. Parse original image
        byte[] abExistingFrame = frame.copyDemuxData(null);
        BitStreamUncompressor bsu;
        try {
            bsu = BitStreamUncompressor.identifyUncompressor(abExistingFrame);
        } catch (BinaryDataNotRecognized ex) {
            throw new LoggedFailure(log, Level.SEVERE, I.UNABLE_TO_DETERMINE_FRAME_TYPE_FRM(getFrameLookup().toString()), ex);
        }

        MdecDecoder_double decoder = new MdecDecoder_double(new StephensIDCT(),
                                                            WIDTH, HEIGHT);
        try {
            ParsedMdecImage parsedOrig = new ParsedMdecImage(new Ac0Cleaner(bsu), WIDTH, HEIGHT);

            // 2. convert both to RGB
            // TODO: use best quality to decode, but same as encode
            decoder.decode(parsedOrig.getStream());

            RgbIntImage rgb = new RgbIntImage(WIDTH, HEIGHT);
            decoder.readDecodedRgb(rgb.getWidth(), rgb.getHeight(), rgb.getData());
            BufferedImage origImg = rgb.toBufferedImage();

            // 3. compare the macroblocks, considering the tolerance
            //    and filter any that don't have any differences within
            //    the bounding box and mask
            ArrayList<Point> diffMacblks = findDiffMacroblocks(origImg, newImg, log);
            if (diffMacblks.isEmpty()) {
                log.log(Level.INFO, I.CMD_NO_DIFFERENCE_SKIPPING());
                return;
            } else if (diffMacblks.size() == Calc.macroblocks(WIDTH, HEIGHT)) {
                log.log(Level.WARNING, I.CMD_ENTIRE_FRAME_DIFFERENT());
            }
            printDiffMacroBlocks(diffMacblks, Calc.macroblockDim(WIDTH), Calc.macroblockDim(HEIGHT), log);

            // 4. Encode and compress
            PsxYCbCrImage newYuvImg = new PsxYCbCrImage(newImg);
            MdecEncoder encoder = new MdecEncoder(parsedOrig, newYuvImg, diffMacblks);
            BitStreamCompressor comp = bsu.makeCompressor();

            byte[] abNewFrame;
            try {
                abNewFrame = comp.compressPartial(abExistingFrame, getFrameLookup().toString(), encoder, log);
            } catch (LocalizedIncompatibleException ex) {
                throw new LoggedFailure(log, Level.SEVERE, ex.getSourceMessage(), ex);
            }
            if (abNewFrame == null)
                throw new LoggedFailure(log, Level.SEVERE,
                        I.CMD_UNABLE_TO_COMPRESS_FRAME_SMALL_ENOUGH(getFrameLookup().toString(), frame.getDemuxSize()));

            // 5. replace the frame
            frame.writeToSectors(abNewFrame, abNewFrame.length, comp.getMdecCodesFromLastCompress(), cd, log);

        } catch (MdecException.EndOfStream ex) {
            // existing frame is incomplete
            throw new LoggedFailure(log, Level.SEVERE, I.FRAME_NUM_INCOMPLETE(getFrameLookup().toString()), ex);
        } catch (MdecException.ReadCorruption ex) {
            // existing frame is corrupted
            throw new LoggedFailure(log, Level.SEVERE, I.FRAME_NUM_CORRUPTED(getFrameLookup().toString()), ex);
        }
        
    }

    private void printDiffMacroBlocks(@Nonnull ArrayList<Point> diffMacblks,
                                      int iMbWidth, int iMbHeight,
                                      @Nonnull ILocalizedLogger log)
    {
        log.log(Level.INFO, I.CMD_REPLACE_FOUND_DIFFERENT_MACRO_BLOCKS(diffMacblks.size()));

        Point p = new Point();
        for (int iMbY = 0; iMbY < iMbHeight; iMbY++) {
            StringBuilder sb = new StringBuilder();
            for (int iMbX = 0; iMbX < iMbWidth; iMbX++) {
                p.setLocation(iMbX, iMbY);
                if (diffMacblks.contains(p)) {
                    sb.append('X');
                } else {
                    sb.append('.');
                }
            }
            log.log(Level.INFO, new UnlocalizedMessage(sb.toString()));
        }
    }

    private @Nonnull ArrayList<Point> findDiffMacroblocks(@Nonnull BufferedImage origImg,
                                                          @Nonnull BufferedImage newImg,
                                                          @Nonnull ILocalizedLogger log)
              throws LoggedFailure
    {
        int iMacblkWidth  = Calc.macroblockDim(origImg.getWidth());
        int iMacblkHeight = Calc.macroblockDim(origImg.getHeight());

        ArrayList<Point> diffMacblks = new ArrayList<Point>();

        BufferedImage maskImg = null;
        if (_imageMaskFile != null) {
            try {
                maskImg = ImageIO.read(_imageMaskFile);
            } catch (IOException ex) {
                throw new LoggedFailure(log, Level.SEVERE,
                        I.IO_READING_FILE_ERROR_NAME(_imageMaskFile.toString()), ex);
            }
            if (maskImg == null)
                throw new LoggedFailure(log, Level.SEVERE, I.REPLACE_UNABLE_READ_IMAGE(_imageMaskFile));
        }

        Point macblk = new Point();
        for (int y=0; y<iMacblkHeight; y++) {
            for (int x=0; x<iMacblkWidth; x++) {
                macblk.setLocation(x, y);
                if (blockIsDifferent(macblk, origImg, newImg, maskImg)) {
                    diffMacblks.add(new Point(macblk));
                }
            }
        }

        return diffMacblks;
    }
    
    private boolean blockIsDifferent(@Nonnull Point macblk,
                                     @Nonnull BufferedImage bi1,
                                     @Nonnull BufferedImage bi2,
                                     @CheckForNull BufferedImage maskImg)
    {
        // 1. filter out macroblocks that aren't touched by the bounding box
        if (_rectMask != null) {
            Rectangle rectMb = new Rectangle(macblk.x * 16, macblk.y * 16, 15, 15);
            if (!_rectMask.intersects(rectMb))
                return false;
        }

        int iStartX = macblk.x * 16, iStartY = macblk.y * 16;
        for (int y=iStartY; y<iStartY+16; y++) {
            for (int x=iStartX; x<iStartX+16; x++) {
                // 2. filter out pixels that aren't in the bounding box
                if (_rectMask != null) {
                    if (!_rectMask.contains(x, y))
                        continue;
                }
                // 3. filter out pixels that aren't in the bitmap mask
                if (maskImg != null) {
                    if ((maskImg.getRGB(x, y) & 0xffffff) == 0)
                        continue;
                }
                // finally compare the pixels
                int iRgb1 = bi1.getRGB(x, y), iRgb2 = bi2.getRGB(x, y);
                int iDiffR = ((iRgb1 >> 16) & 0xff) - ((iRgb2 >> 16) & 0xff);
                int iDiffG = ((iRgb1 >>  8) & 0xff) - ((iRgb2 >>  8) & 0xff);
                int iDiffB = ((iRgb1      ) & 0xff) - ((iRgb2      ) & 0xff);
                if (Math.abs(iDiffR) > _iTolerance ||
                    Math.abs(iDiffG) > _iTolerance ||
                    Math.abs(iDiffB) > _iTolerance)
                    return true;
            }
        }

        return false;
    }


}