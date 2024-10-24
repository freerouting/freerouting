package app.freerouting.api.dev;

import app.freerouting.api.dto.BoardFilePayload;
import app.freerouting.core.RoutingJob;
import app.freerouting.settings.RouterSettings;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/dev/jobs")
public class JobControllerMocked
{
  public JobControllerMocked()
  {
  }

  @POST
  @Path("/enqueue")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response enqueueJob(RoutingJob job)
  {
    return Response.ok("""
                       {
                            "id": "934291f2-8498-4294-a4f4-2058dcfd4edd",
                            "createdAt": "2024-10-14T11:18:24.094005900Z",
                            "sessionId": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
                            "name": "Issue102-Mars-64-revE-rot00",
                            "state": "QUEUED",
                            "priority": "NORMAL",
                            "stage": "IDLE",
                            "routerSettings": {
                                "default_preferred_direction_trace_cost": 1.0,
                                "default_undesired_direction_trace_cost": 1.0,
                                "max_passes": 100,
                                "fanout_max_passes": 20,
                                "max_threads": 11,
                                "improvement_threshold": 0.01,
                                "trace_pull_tight_accuracy": 500,
                                "allowed_via_types": true,
                                "via_costs": 50,
                                "plane_via_costs": 5,
                                "start_ripup_costs": 100,
                                "automatic_neckdown": true
                            }
                       }
                       """).build();
  }

  /* Get a list of all jobs in the session with the given id, returning only basic details about them. */
  @GET
  @Path("/list/{sessionId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listJobs(
      @PathParam("sessionId")
      String sessionId)
  {
    return Response.ok("""
                       [
                            {
                                "id": "934291f2-8498-4294-a4f4-2058dcfd4edd",
                                "createdAt": "2024-10-14T11:18:24.094005900Z",
                                "sessionId": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
                                "name": "Issue102-Mars-64-revE-rot00",
                                "state": "QUEUED",
                                "priority": "NORMAL",
                                "stage": "IDLE",
                                "routerSettings": {
                                    "default_preferred_direction_trace_cost": 1.0,
                                    "default_undesired_direction_trace_cost": 1.0,
                                    "max_passes": 100,
                                    "fanout_max_passes": 20,
                                    "max_threads": 11,
                                    "improvement_threshold": 0.01,
                                    "trace_pull_tight_accuracy": 500,
                                    "allowed_via_types": true,
                                    "via_costs": 50,
                                    "plane_via_costs": 5,
                                    "start_ripup_costs": 100,
                                    "automatic_neckdown": true
                                }
                            },
                            {
                                "id": "4edaebab-ae08-4abe-9ef4-33316fe93821",
                                "createdAt": "2024-10-14T11:20:02.755544600Z",
                                "sessionId": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
                                "name": "Issue102-Mars-64-revE-rot00",
                                "state": "QUEUED",
                                "priority": "NORMAL",
                                "stage": "IDLE",
                                "routerSettings": {
                                    "default_preferred_direction_trace_cost": 1.0,
                                    "default_undesired_direction_trace_cost": 1.0,
                                    "max_passes": 100,
                                    "fanout_max_passes": 20,
                                    "max_threads": 11,
                                    "improvement_threshold": 0.01,
                                    "trace_pull_tight_accuracy": 500,
                                    "allowed_via_types": true,
                                    "via_costs": 50,
                                    "plane_via_costs": 5,
                                    "start_ripup_costs": 100,
                                    "automatic_neckdown": true
                                }
                            },
                            {
                                "id": "482abd42-b6be-47e9-8f33-93b0236f443e",
                                "createdAt": "2024-10-14T11:20:04.282402900Z",
                                "sessionId": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
                                "name": "Issue102-Mars-64-revE-rot00",
                                "state": "QUEUED",
                                "priority": "NORMAL",
                                "stage": "IDLE",
                                "routerSettings": {
                                    "default_preferred_direction_trace_cost": 1.0,
                                    "default_undesired_direction_trace_cost": 1.0,
                                    "max_passes": 100,
                                    "fanout_max_passes": 20,
                                    "max_threads": 11,
                                    "improvement_threshold": 0.01,
                                    "trace_pull_tight_accuracy": 500,
                                    "allowed_via_types": true,
                                    "via_costs": 50,
                                    "plane_via_costs": 5,
                                    "start_ripup_costs": 100,
                                    "automatic_neckdown": true
                                }
                            }
                       ]
                       """).build();
  }

  /* Get the current detailed status of the job with id, including statistical data about the (partially) completed board is the process already started. */
  @GET
  @Path("/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getJob(
      @PathParam("jobId")
      String jobId)
  {
    return Response.ok("""
                       {
                            "id": "934291f2-8498-4294-a4f4-2058dcfd4edd",
                            "createdAt": "2024-10-14T11:18:24.094005900Z",
                            "sessionId": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
                            "name": "Issue102-Mars-64-revE-rot00",
                            "state": "READY_TO_START",
                            "priority": "NORMAL",
                            "stage": "IDLE",
                            "routerSettings": {
                                "default_preferred_direction_trace_cost": 1.0,
                                "default_undesired_direction_trace_cost": 1.0,
                                "max_passes": 100,
                                "fanout_max_passes": 20,
                                "max_threads": 11,
                                "improvement_threshold": 0.01,
                                "trace_pull_tight_accuracy": 500,
                                "allowed_via_types": true,
                                "via_costs": 50,
                                "plane_via_costs": 5,
                                "start_ripup_costs": 100,
                                "automatic_neckdown": true
                            }
                       }
                       """).build();
  }

  /* Start or continue the job with the given id. */
  @PUT
  @Path("/{jobId}/start")
  @Produces(MediaType.APPLICATION_JSON)
  public Response startJob(
      @PathParam("jobId")
      String jobId)
  {
    return Response.ok("""
                       {
                            "id": "934291f2-8498-4294-a4f4-2058dcfd4edd",
                            "createdAt": "2024-10-14T11:18:24.094005900Z",
                            "sessionId": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
                            "name": "Issue102-Mars-64-revE-rot00",
                            "state": "RUNNING",
                            "priority": "NORMAL",
                            "stage": "IDLE",
                            "routerSettings": {
                                "default_preferred_direction_trace_cost": 1.0,
                                "default_undesired_direction_trace_cost": 1.0,
                                "max_passes": 100,
                                "fanout_max_passes": 20,
                                "max_threads": 11,
                                "improvement_threshold": 0.01,
                                "trace_pull_tight_accuracy": 500,
                                "allowed_via_types": true,
                                "via_costs": 50,
                                "plane_via_costs": 5,
                                "start_ripup_costs": 100,
                                "automatic_neckdown": true
                            }
                       }
                       """).build();
  }

  /* Stop the job with the given id, and cancels the job. */
  @PUT
  @Path("/{jobId}/cancel")
  @Produces(MediaType.APPLICATION_JSON)
  public Response cancelJob(
      @PathParam("jobId")
      String jobId)
  {
    return Response.ok("""
                       {
                            "id": "934291f2-8498-4294-a4f4-2058dcfd4edd",
                            "createdAt": "2024-10-14T11:18:24.094005900Z",
                            "sessionId": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
                            "name": "Issue102-Mars-64-revE-rot00",
                            "state": "CANCELLED",
                            "priority": "NORMAL",
                            "stage": "IDLE",
                            "routerSettings": {
                                "default_preferred_direction_trace_cost": 1.0,
                                "default_undesired_direction_trace_cost": 1.0,
                                "max_passes": 100,
                                "fanout_max_passes": 20,
                                "max_threads": 11,
                                "improvement_threshold": 0.01,
                                "trace_pull_tight_accuracy": 500,
                                "allowed_via_types": true,
                                "via_costs": 50,
                                "plane_via_costs": 5,
                                "start_ripup_costs": 100,
                                "automatic_neckdown": true
                            }
                       }
                       """).build();
  }

  /* Change the settings of the job, such as the router settings. */
  @POST
  @Path("/{jobId}/settings")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response changeSettings(
      @PathParam("jobId")
      String jobId, RouterSettings routerSettings)
  {
    return Response.ok("""
                       {
                            "id": "9af24675-4000-4f3f-a3d4-8784820cda97",
                            "createdAt": "2024-10-14T11:22:02.231487Z",
                            "sessionId": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
                            "name": "Issue102-Mars-64-revE-rot00",
                            "state": "QUEUED",
                            "priority": "NORMAL",
                            "stage": "IDLE",
                            "routerSettings": {
                                "default_preferred_direction_trace_cost": 1.0,
                                "default_undesired_direction_trace_cost": 1.0,
                                "max_passes": 100,
                                "fanout_max_passes": 20,
                                "max_threads": 11,
                                "improvement_threshold": 0.01,
                                "trace_pull_tight_accuracy": 500,
                                "allowed_via_types": true,
                                "via_costs": 42,
                                "plane_via_costs": 5,
                                "start_ripup_costs": 100,
                                "automatic_neckdown": true
                            }
                       }
                       """).build();
  }

  /**
   * Upload the input of the job, typically in Specctra DSN format.
   * Note: the input file limit depends on the server configuration, but it is at least 1MB and typically 30MBs if hosted by ASP.NET Core web server.
   */
  @POST
  @Path("/{jobId}/input")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response uploadInput(
      @PathParam("jobId")
      String jobId, BoardFilePayload input)
  {
    return Response.ok("""
                       {
                            "id": "9af24675-4000-4f3f-a3d4-8784820cda97",
                            "createdAt": "2024-10-14T11:22:02.231487Z",
                            "input": {
                                "size": 50853,
                                "crc32": 4085067588,
                                "format": "DSN",
                                "layer_count": 0,
                                "component_count": 0,
                                "netclass_count": 0,
                                "net_count": 0,
                                "track_count": 0,
                                "trace_count": 0,
                                "via_count": 0,
                                "filename": "Issue102-Mars-64-revE-rot00.dsn",
                                "path": ""
                            },
                            "sessionId": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
                            "name": "Issue102-Mars-64-revE-rot00",
                            "state": "QUEUED",
                            "priority": "NORMAL",
                            "stage": "IDLE",
                            "routerSettings": {
                                "default_preferred_direction_trace_cost": 1.0,
                                "default_undesired_direction_trace_cost": 1.0,
                                "max_passes": 100,
                                "fanout_max_passes": 20,
                                "max_threads": 11,
                                "improvement_threshold": 0.01,
                                "trace_pull_tight_accuracy": 500,
                                "allowed_via_types": true,
                                "via_costs": 42,
                                "plane_via_costs": 5,
                                "start_ripup_costs": 100,
                                "automatic_neckdown": true
                            }
                       }
                       """).build();
  }

  /* Download the output of the job, typically in Specctra SES format. */
  @GET
  @Path("/{jobId}/output")
  @Produces(MediaType.APPLICATION_JSON)
  public Response downloadOutput(
      @PathParam("jobId")
      String jobId)
  {
    return Response.ok("""
                       {
                            "jobId": "0a5447bf-45bd-4793-bf07-09916a8ca10b",
                            "dataBase64": "KHNlc3Npb24gIklzc3VlMTAyLU1hcnMtNjQtcmV2RS1yb3QwMCIKICAoYmFzZV9kZXNpZ24gIklzc3VlMTAyLU1hcnMtNjQtcmV2RS1yb3QwMCIpCiAgKHBsYWNlbWVudAogICAgKHJlc29sdXRpb24gdW0gMTApCiAgICAoY29tcG9uZW50ICJNYXJzLTY0OlBpbkhlYWRlcl8yeDA2X1AyLjU0bW1fVmVydGljYWxfU01EX1RvcEJvdHRvbV9NaXJyb3JlZCIKICAgICAgKHBsYWNlIFBtb2RNIDkzOTgwMCAtNTMzNDAwIGJhY2sgMTgwKQogICAgKQogICAgKGNvbXBvbmVudCAiTWFycy02NDpQaW5IZWFkZXJfMngwNl9QMi41NG1tX1ZlcnRpY2FsX1NNRF9Ub3BCb3R0b20iCiAgICAgIChwbGFjZSBQbW9kRiA2ODU4MDAgLTUzMzQwMCBiYWNrIDE4MCkKICAgICkKICAgIChjb21wb25lbnQgVGVzdFBvaW50OlRlc3RQb2ludF9QYWRfNC4weDQuMG1tCiAgICAgIChwbGFjZSBUUDEwMSA2MDk2MDAgLTgzODIwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAxMDMgNzExMjAwIC04MzgyMDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMTA1IDgxMjgwMCAtODM4MjAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDEwNiA4NjM2MDAgLTgzODIwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAxMDcgOTE0NDAwIC04MzgyMDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMTA5IDYwOTYwMCAtODg5MDAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDExMSA3MTEyMDAgLTg4OTAwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAxMTMgODEyODAwIC04ODkwMDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMTE3IDYwOTYwMCAtOTM5ODAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDExOSA3MTEyMDAgLTkzOTgwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAxMjEgODEyODAwIC05Mzk4MDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMTIzIDkxNDQwMCAtOTM5ODAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDEyNSA2MDk2MDAgLTk5MDYwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAxMjcgNzExMjAwIC05OTA2MDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMTI5IDgxMjgwMCAtOTkwNjAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDEzMSA5MTQ0MDAgLTk5MDYwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAyMDEgNjA5NjAwIC0xMDQxNDAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDIwMyA3MTEyMDAgLTEwNDE0MDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMjA1IDgxMjgwMCAtMTA0MTQwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAyMDkgNjA5NjAwIC0xMDkyMjAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDIxMSA3MTEyMDAgLTEwOTIyMDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMjEzIDgxMjgwMCAtMTA5MjIwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAyMTUgOTE0NDAwIC0xMDkyMjAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDIxNiA5NjUyMDAgLTEwOTIyMDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMjE4IDY2MDQwMCAtMTE0MzAwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAyMjAgNzYyMDAwIC0xMTQzMDAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDIyMiA4NjM2MDAgLTExNDMwMDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMjI0IDk2NTIwMCAtMTE0MzAwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAyMjYgNjYwNDAwIC0xMTkzODAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDIyOCA3NjIwMDAgLTExOTM4MDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMjMwIDg2MzYwMCAtMTE5MzgwMCBmcm9udCAwKQogICAgKQogICAgKGNvbXBvbmVudCBUZXN0UG9pbnQ6VGVzdFBvaW50X1BhZF80LjB4NC4wbW06OjEKICAgICAgKHBsYWNlIFRQMTAyIDY2MDQwMCAtODM4MjAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDEwNCA3NjIwMDAgLTgzODIwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAxMDggOTY1MjAwIC04MzgyMDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMTEwIDY2MDQwMCAtODg5MDAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDExMiA3NjIwMDAgLTg4OTAwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAxMTQgODYzNjAwIC04ODkwMDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMTE1IDkxNDQwMCAtODg5MDAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDExNiA5NjUyMDAgLTg4OTAwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAxMTggNjYwNDAwIC05Mzk4MDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMTIwIDc2MjAwMCAtOTM5ODAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDEyMiA4NjM2MDAgLTkzOTgwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAxMjQgOTY1MjAwIC05Mzk4MDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMTI2IDY2MDQwMCAtOTkwNjAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDEyOCA3NjIwMDAgLTk5MDYwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAxMzAgODYzNjAwIC05OTA2MDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMTMyIDk2NTIwMCAtOTkwNjAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDIwMiA2NjA0MDAgLTEwNDE0MDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMjA0IDc2MjAwMCAtMTA0MTQwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAyMDYgODYzNjAwIC0xMDQxNDAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDIwNyA5MTQ0MDAgLTEwNDE0MDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMjA4IDk2NTIwMCAtMTA0MTQwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAyMTAgNjYwNDAwIC0xMDkyMjAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDIxMiA3NjIwMDAgLTEwOTIyMDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMjE0IDg2MzYwMCAtMTA5MjIwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAyMTcgNjA5NjAwIC0xMTQzMDAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDIxOSA3MTEyMDAgLTExNDMwMDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMjIxIDgxMjgwMCAtMTE0MzAwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAyMjMgOTE0NDAwIC0xMTQzMDAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDIyNSA2MDk2MDAgLTExOTM4MDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMjI3IDcxMTIwMCAtMTE5MzgwMCBmcm9udCAwKQogICAgICAocGxhY2UgVFAyMjkgODEyODAwIC0xMTkzODAwIGZyb250IDApCiAgICAgIChwbGFjZSBUUDIzMSA5MTQ0MDAgLTExOTM4MDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIFRQMjMyIDk2NTIwMCAtMTE5MzgwMCBmcm9udCAwKQogICAgKQogICAgKGNvbXBvbmVudCAiQ29ubmVjdG9yX0NvYXhpYWw6U01BX0FtcGhlbm9sXzEzMjEzNC0xMF9WZXJ0aWNhbCIKICAgICAgKHBsYWNlIEpBIDkzOTgwMCAtNzYyMDAwIGJhY2sgMTgwKQogICAgKQogICAgKGNvbXBvbmVudCAiQ29ubmVjdG9yX0NvYXhpYWw6U01BX0FtcGhlbm9sXzEzMjEzNC0xMF9WZXJ0aWNhbDo6MSIKICAgICAgKHBsYWNlIEpCIDkzOTgwMCAtMTM3MTYwMCBiYWNrIDE4MCkKICAgICkKICAgIChjb21wb25lbnQgVGVzdFBvaW50OlRlc3RQb2ludF9QYWRfMS4weDEuMG1tCiAgICAgIChwbGFjZSBUUEFHTkQxIDY0NzcwMCAtNzYyMDAwIGJhY2sgMTgwKQogICAgKQogICAgKGNvbXBvbmVudCAiSnVtcGVyOlNvbGRlckp1bXBlci0zX1AxLjNtbV9CcmlkZ2VkMkJhcjEyX1BhZDEuMHgxLjVtbV9OdW1iZXJMYWJlbHMiCiAgICAgIChwbGFjZSBKUDQgNjM1MDAwIC03MzY2MDAgYmFjayAwKQogICAgKQogICAgKGNvbXBvbmVudCAiUGFja2FnZV9RRlA6VFFGUC00OF83eDdtbV9QMC41bW0iCiAgICAgIChwbGFjZSBVMTAxIDc4NzQwMCAtNjg1ODAwIGJhY2sgMCkKICAgICkKICAgIChjb21wb25lbnQgIlBhY2thZ2VfUUZQOlRRRlAtNDhfN3g3bW1fUDAuNW1tOjoxIgogICAgICAocGxhY2UgVTEwMiA3NjIwMDAgLTEzNzE2MDAgYmFjayAwKQogICAgKQogICAgKGNvbXBvbmVudCAiSnVtcGVyOlNvbGRlckp1bXBlci0yX1AxLjNtbV9CcmlkZ2VkX1JvdW5kZWRQYWQxLjB4MS41bW0iCiAgICAgIChwbGFjZSBKUDUgNjA5NjAwIC0xMzQ2MjAwIGJhY2sgMTgwKQogICAgKQogICAgKGNvbXBvbmVudCBDYXBhY2l0b3JfU01EOkNfMTIwNl8zMjE2TWV0cmljX1BhZDEuNDJ4MS43NW1tX0hhbmRTb2xkZXIKICAgICAgKHBsYWNlIEMxIDY0NzcwMCAtNjQ3NzAwIGJhY2sgMTgwKQogICAgKQogICAgKGNvbXBvbmVudCBDYXBhY2l0b3JfU01EOkNfMTIwNl8zMjE2TWV0cmljX1BhZDEuNDJ4MS43NW1tX0hhbmRTb2xkZXI6OjEKICAgICAgKHBsYWNlIEMyIDYwOTYwMCAtMTM5NzAwMCBiYWNrIDE4MCkKICAgICkKICAgIChjb21wb25lbnQgTW91bnRpbmdIb2xlOk1vdW50aW5nSG9sZV8yLjdtbV9NMi41CiAgICAgIChwbGFjZSBIMSA1OTY5MDAgLTY4NTgwMCBmcm9udCAwKQogICAgICAocGxhY2UgSDQgOTc3OTAwIC0xMjcwMDAwIGZyb250IDApCiAgICApCiAgICAoY29tcG9uZW50IE1vdW50aW5nSG9sZTpNb3VudGluZ0hvbGVfMi43bW1fTTIuNTo6MQogICAgICAocGxhY2UgSDIgOTc3OTAwIC02ODU4MDAgZnJvbnQgMCkKICAgICAgKHBsYWNlIEgzIDU5NjkwMCAtMTI3MDAwMCBmcm9udCAwKQogICAgKQogICkKICAod2FzX2lzCiAgKQogIChyb3V0ZXMgCiAgICAocmVzb2x1dGlvbiB1bSAxMCkKICAgIChwYXJzZXIKICAgICAgKGhvc3RfY2FkICJLaUNhZCdzIFBjYm5ldyIpCiAgICAgIChob3N0X3ZlcnNpb24gIig1LjEuNSktMyIpCiAgICApCiAgICAobGlicmFyeV9vdXQgCiAgICAgIChwYWRzdGFjayAiVmlhWzAtMV1fODAwOjQwMF91bSIKICAgICAgICAoc2hhcGUKICAgICAgICAgIChjaXJjbGUgRi5DdSA4MDAwIDAgMCkKICAgICAgICApCiAgICAgICAgKHNoYXBlCiAgICAgICAgICAoY2lyY2xlIEIuQ3UgODAwMCAwIDApCiAgICAgICAgKQogICAgICAgIChhdHRhY2ggb2ZmKQogICAgICApCiAgICAgIChwYWRzdGFjayAiVmlhWzAtMV1fODAwOjQwMF91bSIKICAgICAgICAoc2hhcGUKICAgICAgICAgIChjaXJjbGUgRi5DdSA4MDAwIDAgMCkKICAgICAgICApCiAgICAgICAgKHNoYXBlCiAgICAgICAgICAoY2lyY2xlIEIuQ3UgODAwMCAwIDApCiAgICAgICAgKQogICAgICAgIChhdHRhY2ggb2ZmKQogICAgICApCiAgICApCiAgICAobmV0d29ya19vdXQgCiAgICAgIChuZXQgL0FHTkQKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA1Nzc4NTAgLTgwNjQ1MAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEwMS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA2MDk2MDAgLTgzODIwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEwMi1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA2NjA0MDAgLTgzODIwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEwMy1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA3MTEyMDAgLTgzODIwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEwNC1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA3NjIwMDAgLTgzODIwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEwNS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA4MTI4MDAgLTgzODIwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEwNi1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA4NjM2MDAgLTgzODIwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEwNy1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA5MTQ0MDAgLTgzODIwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEwOC1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA5NjUyMDAgLTgzODIwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEwOS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA2MDk2MDAgLTg4OTAwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDExMC1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA2NjA0MDAgLTg4OTAwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDExMS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA3MTEyMDAgLTg4OTAwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDExMi1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA3NjIwMDAgLTg4OTAwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDExMy1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA4MTI4MDAgLTg4OTAwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDExNC1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA4NjM2MDAgLTg4OTAwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDExNS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA5MTQ0MDAgLTg4OTAwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDExNi1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA5NjUyMDAgLTg4OTAwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDExNy1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA2MDk2MDAgLTkzOTgwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDExOC1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA2NjA0MDAgLTkzOTgwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDExOS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA3MTEyMDAgLTkzOTgwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEyMC1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA3NjIwMDAgLTkzOTgwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEyMS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA4MTI4MDAgLTkzOTgwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEyMi1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA4NjM2MDAgLTkzOTgwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEyMy1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA5MTQ0MDAgLTkzOTgwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEyNC1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA5NjUyMDAgLTkzOTgwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEyNS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA2MDk2MDAgLTk5MDYwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEyNi1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA2NjA0MDAgLTk5MDYwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEyNy1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA3MTEyMDAgLTk5MDYwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEyOC1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA3NjIwMDAgLTk5MDYwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEyOS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA4MTI4MDAgLTk5MDYwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEzMC1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA4NjM2MDAgLTk5MDYwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEzMS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA5MTQ0MDAgLTk5MDYwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDEzMi1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA5NjUyMDAgLTk5MDYwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDIwMS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA2MDk2MDAgLTEwNDE0MDAKICAgICAgICAgICh0eXBlIHByb3RlY3QpCiAgICAgICAgKQogICAgICApCiAgICAgIChuZXQgIk5ldC0oVFAyMDItUGFkMSkiCiAgICAgICAgKHZpYSAiVmlhWzAtMV1fODAwOjQwMF91bSIgNjYwNDAwIC0xMDQxNDAwCiAgICAgICAgICAodHlwZSBwcm90ZWN0KQogICAgICAgICkKICAgICAgKQogICAgICAobmV0ICJOZXQtKFRQMjAzLVBhZDEpIgogICAgICAgICh2aWEgIlZpYVswLTFdXzgwMDo0MDBfdW0iIDcxMTIwMCAtMTA0MTQwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDIwNC1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA3NjIwMDAgLTEwNDE0MDAKICAgICAgICAgICh0eXBlIHByb3RlY3QpCiAgICAgICAgKQogICAgICApCiAgICAgIChuZXQgIk5ldC0oVFAyMDUtUGFkMSkiCiAgICAgICAgKHZpYSAiVmlhWzAtMV1fODAwOjQwMF91bSIgODEyODAwIC0xMDQxNDAwCiAgICAgICAgICAodHlwZSBwcm90ZWN0KQogICAgICAgICkKICAgICAgKQogICAgICAobmV0ICJOZXQtKFRQMjA2LVBhZDEpIgogICAgICAgICh2aWEgIlZpYVswLTFdXzgwMDo0MDBfdW0iIDg2MzYwMCAtMTA0MTQwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDIwNy1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA5MTQ0MDAgLTEwNDE0MDAKICAgICAgICAgICh0eXBlIHByb3RlY3QpCiAgICAgICAgKQogICAgICApCiAgICAgIChuZXQgIk5ldC0oVFAyMDgtUGFkMSkiCiAgICAgICAgKHZpYSAiVmlhWzAtMV1fODAwOjQwMF91bSIgOTY1MjAwIC0xMDQxNDAwCiAgICAgICAgICAodHlwZSBwcm90ZWN0KQogICAgICAgICkKICAgICAgKQogICAgICAobmV0ICJOZXQtKFRQMjA5LVBhZDEpIgogICAgICAgICh2aWEgIlZpYVswLTFdXzgwMDo0MDBfdW0iIDYwOTYwMCAtMTA5MjIwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDIxMC1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA2NjA0MDAgLTEwOTIyMDAKICAgICAgICAgICh0eXBlIHByb3RlY3QpCiAgICAgICAgKQogICAgICApCiAgICAgIChuZXQgIk5ldC0oVFAyMTEtUGFkMSkiCiAgICAgICAgKHZpYSAiVmlhWzAtMV1fODAwOjQwMF91bSIgNzExMjAwIC0xMDkyMjAwCiAgICAgICAgICAodHlwZSBwcm90ZWN0KQogICAgICAgICkKICAgICAgKQogICAgICAobmV0ICJOZXQtKFRQMjEyLVBhZDEpIgogICAgICAgICh2aWEgIlZpYVswLTFdXzgwMDo0MDBfdW0iIDc2MjAwMCAtMTA5MjIwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDIxMy1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA4MTI4MDAgLTEwOTIyMDAKICAgICAgICAgICh0eXBlIHByb3RlY3QpCiAgICAgICAgKQogICAgICApCiAgICAgIChuZXQgIk5ldC0oVFAyMTQtUGFkMSkiCiAgICAgICAgKHZpYSAiVmlhWzAtMV1fODAwOjQwMF91bSIgODYzNjAwIC0xMDkyMjAwCiAgICAgICAgICAodHlwZSBwcm90ZWN0KQogICAgICAgICkKICAgICAgKQogICAgICAobmV0ICJOZXQtKFRQMjE1LVBhZDEpIgogICAgICAgICh2aWEgIlZpYVswLTFdXzgwMDo0MDBfdW0iIDkxNDQwMCAtMTA5MjIwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDIxNi1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA5NjUyMDAgLTEwOTIyMDAKICAgICAgICAgICh0eXBlIHByb3RlY3QpCiAgICAgICAgKQogICAgICApCiAgICAgIChuZXQgIk5ldC0oVFAyMTctUGFkMSkiCiAgICAgICAgKHZpYSAiVmlhWzAtMV1fODAwOjQwMF91bSIgNjA5NjAwIC0xMTQzMDAwCiAgICAgICAgICAodHlwZSBwcm90ZWN0KQogICAgICAgICkKICAgICAgKQogICAgICAobmV0ICJOZXQtKFRQMjE4LVBhZDEpIgogICAgICAgICh2aWEgIlZpYVswLTFdXzgwMDo0MDBfdW0iIDY2MDQwMCAtMTE0MzAwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDIxOS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA3MTEyMDAgLTExNDMwMDAKICAgICAgICAgICh0eXBlIHByb3RlY3QpCiAgICAgICAgKQogICAgICApCiAgICAgIChuZXQgIk5ldC0oVFAyMjAtUGFkMSkiCiAgICAgICAgKHZpYSAiVmlhWzAtMV1fODAwOjQwMF91bSIgNzYyMDAwIC0xMTQzMDAwCiAgICAgICAgICAodHlwZSBwcm90ZWN0KQogICAgICAgICkKICAgICAgKQogICAgICAobmV0ICJOZXQtKFRQMjIxLVBhZDEpIgogICAgICAgICh2aWEgIlZpYVswLTFdXzgwMDo0MDBfdW0iIDgxMjgwMCAtMTE0MzAwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDIyMi1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA4NjM2MDAgLTExNDMwMDAKICAgICAgICAgICh0eXBlIHByb3RlY3QpCiAgICAgICAgKQogICAgICApCiAgICAgIChuZXQgIk5ldC0oVFAyMjMtUGFkMSkiCiAgICAgICAgKHZpYSAiVmlhWzAtMV1fODAwOjQwMF91bSIgOTE0NDAwIC0xMTQzMDAwCiAgICAgICAgICAodHlwZSBwcm90ZWN0KQogICAgICAgICkKICAgICAgKQogICAgICAobmV0ICJOZXQtKFRQMjI0LVBhZDEpIgogICAgICAgICh2aWEgIlZpYVswLTFdXzgwMDo0MDBfdW0iIDk2NTIwMCAtMTE0MzAwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDIyNS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA2MDk2MDAgLTExOTM4MDAKICAgICAgICAgICh0eXBlIHByb3RlY3QpCiAgICAgICAgKQogICAgICApCiAgICAgIChuZXQgIk5ldC0oVFAyMjYtUGFkMSkiCiAgICAgICAgKHZpYSAiVmlhWzAtMV1fODAwOjQwMF91bSIgNjYwNDAwIC0xMTkzODAwCiAgICAgICAgICAodHlwZSBwcm90ZWN0KQogICAgICAgICkKICAgICAgKQogICAgICAobmV0ICJOZXQtKFRQMjI3LVBhZDEpIgogICAgICAgICh2aWEgIlZpYVswLTFdXzgwMDo0MDBfdW0iIDcxMTIwMCAtMTE5MzgwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDIyOC1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA3NjIwMDAgLTExOTM4MDAKICAgICAgICAgICh0eXBlIHByb3RlY3QpCiAgICAgICAgKQogICAgICApCiAgICAgIChuZXQgIk5ldC0oVFAyMjktUGFkMSkiCiAgICAgICAgKHZpYSAiVmlhWzAtMV1fODAwOjQwMF91bSIgODEyODAwIC0xMTkzODAwCiAgICAgICAgICAodHlwZSBwcm90ZWN0KQogICAgICAgICkKICAgICAgKQogICAgICAobmV0ICJOZXQtKFRQMjMwLVBhZDEpIgogICAgICAgICh2aWEgIlZpYVswLTFdXzgwMDo0MDBfdW0iIDg2MzYwMCAtMTE5MzgwMAogICAgICAgICAgKHR5cGUgcHJvdGVjdCkKICAgICAgICApCiAgICAgICkKICAgICAgKG5ldCAiTmV0LShUUDIzMS1QYWQxKSIKICAgICAgICAodmlhICJWaWFbMC0xXV84MDA6NDAwX3VtIiA5MTQ0MDAgLTExOTM4MDAKICAgICAgICAgICh0eXBlIHByb3RlY3QpCiAgICAgICAgKQogICAgICApCiAgICAgIChuZXQgIk5ldC0oVFAyMzItUGFkMSkiCiAgICAgICAgKHZpYSAiVmlhWzAtMV1fODAwOjQwMF91bSIgOTY1MjAwIC0xMTkzODAwCiAgICAgICAgICAodHlwZSBwcm90ZWN0KQogICAgICAgICkKICAgICAgKQogICAgKQogICkKKQ==",
                            "size": 13150,
                            "crc32": 264089660,
                            "format": "SES",
                            "layer_count": 0,
                            "component_count": 0,
                            "netclass_count": 0,
                            "net_count": 0,
                            "track_count": 0,
                            "trace_count": 0,
                            "via_count": 0,
                            "filename": "Issue102-Mars-64-revE-rot00.ses",
                            "path": ""
                       }
                       """).build();
  }

  @GET
  @Path("/{jobId}/logs")
  @Produces(MediaType.APPLICATION_JSON)
  public Response logs(
      @PathParam("jobId")
      String jobId)
  {
    return Response.ok("""
                       [
                       ]
                       """).build();
  }
}