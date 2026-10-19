package app.freerouting.management.jobs;

import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.io.FileFormat;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.HeadlessBoardManager;
import java.io.ByteArrayOutputStream;

/** Publishes complete output snapshots without losing the caller's output configuration. */
final class RoutingJobOutputWriter {
  private RoutingJobOutputWriter() {}

  static boolean write(RoutingJob job) {
    try {
      if (job.board == null) {
        throw new java.io.IOException("The final routing board is unavailable");
      }
      if (job.output == null) {
        job.output = new BoardFileDetails(job.board);
        job.output.addUpdatedEventListener(_ -> job.fireOutputUpdatedEvent());
        String name = job.input != null ? job.input.getFilenameWithoutExtension() : job.name;
        if (job.input != null && job.input.format == FileFormat.KICAD_DESIGN_JSON) {
          job.output.format = FileFormat.KICAD_SESSION_JSON;
          job.output.setFilename(name + ".json");
        } else {
          job.output.format = FileFormat.SES;
          job.output.setFilename(name + ".ses");
        }
      }
      byte[] bytes;
      if (job.output.format == FileFormat.KICAD_SESSION_JSON) {
        bytes =
            app.freerouting.io.kicad.KiCadJsonWriter.write(job.board, job.name)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
      } else {
        HeadlessBoardManager manager = new HeadlessBoardManager(job);
        manager.replaceRoutingBoard(job.board);
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
          boolean saved;
          if (job.output.format == FileFormat.SES) {
            saved = manager.saveAsSpecctraSessionSes(buffer, job.name);
          } else if (job.output.format == FileFormat.SCR) {
            saved = manager.saveAsFusionScriptScr(buffer, job.name);
          } else {
            throw new java.io.IOException("Unsupported routing output format");
          }
          if (!saved) {
            throw new java.io.IOException("The current routing board could not be exported");
          }
          bytes = buffer.toByteArray();
        }
      }
      if (bytes.length == 0) {
        throw new java.io.IOException("The current routing board produced empty output");
      }
      // Publish only the complete current snapshot. A failed export must not
      // leave an earlier preview available as a completed routing result.
      job.output.setData(bytes);
      return true;
    } catch (Exception error) {
      if (job.output != null) {
        job.output.invalidateData();
      }
      FRLogger.error("The current routing output was not published.", error);
      return false;
    }
  }
}
