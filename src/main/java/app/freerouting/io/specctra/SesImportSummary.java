package app.freerouting.io.specctra;

/**
 * Summary of a Specctra session (.ses) file import operation returned by
 * {@code SesReader.read}.
 *
 * <p>A non-zero {@link #errorsEncountered()} value indicates that one or more wires or vias
 * could not be imported (e.g. an unknown net name or a malformed coordinate). The board may
 * still be partially populated in that case; callers should inspect {@link #wiresImported()} and
 * {@link #viasImported()} to confirm how much routing data was successfully applied.
 *
 * @param wiresImported     the number of wire traces successfully imported into the board
 * @param viasImported      the number of vias successfully imported into the board
 * @param errorsEncountered the number of non-fatal errors encountered during import
 */
public record SesImportSummary(int wiresImported, int viasImported, int errorsEncountered) {}


