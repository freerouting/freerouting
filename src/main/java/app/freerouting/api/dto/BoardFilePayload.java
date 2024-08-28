package app.freerouting.api.dto;

import app.freerouting.board.BoardFileDetails;

import java.util.UUID;

public class BoardFilePayload extends BoardFileDetails
{
  public UUID jobId;
  public String dataBase64;
}