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

package jpsxdec.util;

import java.io.PrintStream;
import javax.annotation.Nonnull;
import jpsxdec.i18n.ILocalizedMessage;

public abstract class ProgressLogger extends UserFriendlyLogger {

    private double _dblMin = 0;
    private double _dblMax = 0;

    public ProgressLogger(@Nonnull String sBaseName, @Nonnull PrintStream ps) {
        super(sBaseName, ps);
    }
    public ProgressLogger(@Nonnull String sBaseName) {
        super(sBaseName);
    }

    final public void progressStart(double dblMaxValue) throws TaskCanceledException {
        progressStart(0, dblMaxValue);
    }

    final public void progressStart(double dblMinValue, double dblMaxValue)
            throws TaskCanceledException
    {
        if (dblMaxValue < dblMinValue)
            throw new IllegalArgumentException();
        _dblMin = dblMinValue;
        _dblMax = dblMaxValue;
        handleProgressStart();
    }

    /** @param dblProgress Between the minimum and maximum values specified by
     *                     {@link #progressStart(double)} or
     *                     {@link #progressStart(double, double)}.  */
    final public void progressUpdate(double dblProgress) throws TaskCanceledException
    {
        double dblPercent;
        if (dblProgress < _dblMin)
            dblPercent = 0;
        else if (dblProgress > _dblMax)
            dblPercent = 1;
        else
            dblPercent = (dblProgress - _dblMin) / (_dblMax - _dblMin);
        handleProgressUpdate(dblPercent);
    }

    final public void progressEnd() throws TaskCanceledException {
        _dblMin = _dblMax = 0;
        handleProgressEnd();
    }

    abstract protected void handleProgressStart() throws TaskCanceledException;
    abstract protected void handleProgressUpdate(double dblPercentComplete) throws TaskCanceledException;
    abstract protected void handleProgressEnd() throws TaskCanceledException;

    /** If the progress listener is wanting an event. */
    abstract public boolean isSeekingEvent();
    /** Report progress event. */
    abstract public void event(@Nonnull ILocalizedMessage msg);
}
