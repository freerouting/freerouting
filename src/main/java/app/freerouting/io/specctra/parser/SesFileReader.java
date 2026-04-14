package app.freerouting.io.specctra.parser;

import app.freerouting.board.BasicBoard;
import app.freerouting.io.specctra.SesImportSummary;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.logger.FRLogger;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads a Specctra session file (.ses) and imports the routing data (wires and
 * vias) into the board.
 *
 * @deprecated Use {@link SesReader} instead, which returns a typed
 *             {@link SesImportSummary} and throws {@link IOException} instead of
 *             returning a bare {@code boolean}.
 */
@Deprecated
public class SesFileReader {

    private SesFileReader() {
    }

    /**
     * Reads a SES file and imports the routing data into the board.
     *
     * @param p_session Input stream of the SES file
     * @param p_board   The board to import routing data into
     * @return true if successful, false if an error occurred
     * @deprecated Use {@link SesReader#read(InputStream, BasicBoard)} instead.
     */
    @Deprecated
    public static boolean read(InputStream p_session, BasicBoard p_board) {
        try {
            SesImportSummary summary = SesReader.read(p_session, p_board);
            return summary.errorsEncountered() == 0 || summary.wiresImported() > 0 || summary.viasImported() > 0;
        } catch (IOException e) {
            FRLogger.error("Unable to process SES file", e);
            return false;
        }
    }
}
