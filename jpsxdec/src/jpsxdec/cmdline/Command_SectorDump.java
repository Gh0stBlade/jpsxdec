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

package jpsxdec.cmdline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jpsxdec.cdreaders.CdFileSectorReader;
import jpsxdec.i18n.I;
import jpsxdec.i18n.ILocalizedMessage;
import jpsxdec.sectors.IdentifiedSector;
import jpsxdec.sectors.IdentifiedSectorIterator;
import jpsxdec.util.ArgParser;


class Command_SectorDump extends Command {

    @Nonnull
    private String _sOutfile;

    public Command_SectorDump() {
        super("-sectordump");
    }

    protected @CheckForNull ILocalizedMessage validate(@Nonnull String s) {
        _sOutfile = s;
        return null;
    }

    public void execute(@Nonnull ArgParser ap) throws CommandLineException {
        CdFileSectorReader cdReader = getCdReader();
        _fbs.println(I.CMD_GENERATING_SECTOR_LIST());
        PrintStream ps = null;
        try {
            if (_sOutfile.equals("-")) {
                ps = System.out;
            } else {
                try {
                    ps = new PrintStream(_sOutfile);
                } catch (FileNotFoundException ex) {
                    throw new CommandLineException(I.IO_OPENING_FILE_NOT_FOUND_NAME(_sOutfile), ex);
                }
            }
            SectorCounter counter = new SectorCounter();
            IdentifiedSectorIterator it = IdentifiedSectorIterator.create(cdReader);
            while (it.hasNext()) {
                IdentifiedSector idSect = it.next();
                if (idSect != null)
                    ps.println(idSect);
                else
                    ps.println(it.currentCd());
                counter.increment(idSect);
            }
            for (Map.Entry<String, Integer> entry : counter) {
                ps.println(entry.getKey() + " " + entry.getValue());
            }
        } catch (IOException ex) {
            throw new CommandLineException(I.IO_READING_FROM_FILE_ERROR_NAME(cdReader.getSourceFile().toString()), ex);
        } finally {
            if (ps != null) {
                ps.flush();
                if (!ps.equals(System.out)) {
                    ps.close();
                }
            }
        }
    }

}
