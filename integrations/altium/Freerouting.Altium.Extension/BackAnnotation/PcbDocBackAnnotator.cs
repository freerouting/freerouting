using System;
using System.Collections.Generic;
using Freerouting.Altium.Core.Ses;
using Freerouting.Altium.Extension.Commands;
using Freerouting.Altium.Extension.Sdk;
using RT_PCB;

namespace Freerouting.Altium.Extension.BackAnnotation
{
    public class PcbDocBackAnnotator
    {
        public void BackAnnotate(IPCB_ServerInterface pcbServer, IPCB_Board board, SesResult sesResult)
        {
            if (pcbServer == null) throw new ArgumentNullException("pcbServer");
            if (board == null) throw new ArgumentNullException("board");
            if (sesResult == null) throw new ArgumentNullException("sesResult");

            FreeroutingServerModule.LogTrace(string.Format("BackAnnotate started: {0} wires, {1} vias to process.",
                sesResult.Wires != null ? sesResult.Wires.Count : 0,
                sesResult.Vias != null ? sesResult.Vias.Count : 0));

            // 1. Identify all routed nets
            var routedNetNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            if (sesResult.Wires != null)
            {
                foreach (var wire in sesResult.Wires)
                {
                    if (!string.IsNullOrEmpty(wire.NetName)) routedNetNames.Add(wire.NetName);
                }
            }
            if (sesResult.Vias != null)
            {
                foreach (var via in sesResult.Vias)
                {
                    if (!string.IsNullOrEmpty(via.NetName)) routedNetNames.Add(via.NetName);
                }
            }

            // 2. Build lookups for layers and nets
            var layerMap = BuildLayerLookup(board);
            var netMap = BuildNetLookup(board);

            FreeroutingServerModule.LogTrace(string.Format("Lookups built: {0} layers mapped, {1} nets mapped.",
                layerMap.Count, netMap.Count));

            // 3. Pre-cleanup: Delete existing unlocked tracks and vias on routed electrical signal layers
            CleanupExistingRoutes(pcbServer, board, routedNetNames, layerMap);

            int tracksCreated = 0;
            int viasCreated = 0;

            // 4. Atomic Back-Annotation Transaction
            pcbServer.PreProcess();
            try
            {
                // A. Add Tracks
                if (sesResult.Wires != null)
                {
                    foreach (var wire in sesResult.Wires)
                    {
                        TV6_Layer layer;
                        if (!layerMap.TryGetValue(wire.LayerName, out layer))
                        {
                            // Strictly skip wires on unmapped or non-electrical layers
                            continue;
                        }

                        IPCB_Net net = null;
                        if (!string.IsNullOrEmpty(wire.NetName))
                        {
                            netMap.TryGetValue(wire.NetName, out net);
                        }

                        if (wire.Vertices == null) continue;

                        for (int i = 0; i < wire.Vertices.Count - 1; i++)
                        {
                            var p1 = wire.Vertices[i];
                            var p2 = wire.Vertices[i + 1];

                            try
                            {
                                var track = pcbServer.PCBObjectFactory(
                                    TObjectId.eTrackObject,
                                    TDimensionKind.eNoDimension,
                                    TObjectCreationMode.eCreate_Default) as IPCB_Track;

                                if (track == null) continue;

                                track.SetState_X1(p1.X);
                                track.SetState_Y1(p1.Y);
                                track.SetState_X2(p2.X);
                                track.SetState_Y2(p2.Y);
                                track.SetState_Layer(layer);
                                track.SetState_Width(wire.Width > 0 ? wire.Width : 100000);
                                if (net != null) track.SetState_Net(net);
                                track.SetState_UserRouted(true);

                                board.AddPCBObject(track);

                                try
                                {
                                    pcbServer.SendMessageToRobots(
                                        board.I_ObjectAddress(),
                                        PCBMessageConstants.c_BroadCast,
                                        PCBMessageConstants.PCBM_BoardRegisteration,
                                        track.I_ObjectAddress());
                                }
                                catch { }

                                tracksCreated++;
                            }
                            catch (Exception ex)
                            {
                                FreeroutingServerModule.LogError("Error creating track segment", ex);
                            }
                        }
                    }
                }

                // B. Add Vias
                if (sesResult.Vias != null)
                {
                    foreach (var viaItem in sesResult.Vias)
                    {
                        IPCB_Net net = null;
                        if (!string.IsNullOrEmpty(viaItem.NetName))
                        {
                            netMap.TryGetValue(viaItem.NetName, out net);
                        }

                        try
                        {
                            var via = pcbServer.PCBObjectFactory(
                                TObjectId.eViaObject,
                                TDimensionKind.eNoDimension,
                                TObjectCreationMode.eCreate_Default) as IPCB_Via;

                            if (via == null) continue;

                            via.SetState_XLocation(viaItem.X);
                            via.SetState_YLocation(viaItem.Y);
                            via.SetState_Size(viaItem.Diameter > 0 ? viaItem.Diameter : 240000);
                            via.SetState_HoleSize(viaItem.HoleSize > 0 ? viaItem.HoleSize : 120000);
                            if (net != null) via.SetState_Net(net);
                            via.SetState_UserRouted(true);

                            board.AddPCBObject(via);

                            try
                            {
                                pcbServer.SendMessageToRobots(
                                    board.I_ObjectAddress(),
                                    PCBMessageConstants.c_BroadCast,
                                    PCBMessageConstants.PCBM_BoardRegisteration,
                                    via.I_ObjectAddress());
                            }
                            catch { }

                            viasCreated++;
                        }
                        catch (Exception ex)
                        {
                            FreeroutingServerModule.LogError("Error creating via", ex);
                        }
                    }
                }
            }
            finally
            {
                pcbServer.PostProcess();

                FreeroutingServerModule.LogTrace(string.Format("BackAnnotate completed: {0} tracks created, {1} vias created. Updating view...",
                    tracksCreated, viasCreated));

                // 5. Post-process updates
                // A. Validate live connectivity and ratsnest
                try
                {
                    board.ConnectivelyValidateNets();
                }
                catch { }

                // B. Unshelve and repour signal copper polygons
                try
                {
                    RepourPolygons(board);
                }
                catch { }

                // C. Full viewport refresh & redraw
                try
                {
                    board.ViewManager_FullUpdate();
                    board.ConnectivelyValidateNets();
                    board.Update_PCBGraphicalView(true, true);
                    board.GraphicalView_ZoomRedraw();
                    board.GraphicallyInvalidate();
                }
                catch (Exception ex)
                {
                    FreeroutingServerModule.LogError("Error updating viewport after back-annotation", ex);
                }
            }
        }

        private void CleanupExistingRoutes(IPCB_ServerInterface pcbServer, IPCB_Board board, HashSet<string> routedNets, Dictionary<string, TV6_Layer> layerMap)
        {
            var objectsToDelete = new List<IPCB_Primitive>();
            var iter = board.BoardIterator_Create();
            if (iter == null) return;

            try
            {
                iter.SetState_FilterAll();
                iter.AddFilter_AllLayers();
                iter.AddFilter_Method(TIterationMethod.eProcessAll);

                for (var prim = iter.FirstPCBObject(); prim != null; prim = iter.NextPCBObject())
                {
                    var objId = prim.GetState_ObjectID();
                    if (objId != TObjectId.eTrackObject && objId != TObjectId.eViaObject) continue;

                    // Strictly skip non-electrical layers (never touch silkscreen or mechanical lines)
                    if (layerMap != null && !layerMap.ContainsValue(prim.GetState_Layer())) continue;

                    // Preserve locked tracks and vias (matching KiCad behavior)
                    if (!prim.GetState_Moveable()) continue;

                    var net = prim.GetState_Net();
                    if (net != null && routedNets.Contains(net.GetState_Name()))
                    {
                        objectsToDelete.Add(prim);
                    }
                }
            }
            finally
            {
                board.BoardIterator_Destroy(ref iter);
            }

            foreach (var prim in objectsToDelete)
            {
                try
                {
                    board.RemovePCBObject(prim);
                    pcbServer.SendMessageToRobots(
                        board.I_ObjectAddress(),
                        PCBMessageConstants.c_BroadCast,
                        PCBMessageConstants.PCBM_BoardRegisteration,
                        prim.I_ObjectAddress());
                }
                catch { }
            }
        }

        private Dictionary<string, TV6_Layer> BuildLayerLookup(IPCB_Board board)
        {
            var map = new Dictionary<string, TV6_Layer>(StringComparer.OrdinalIgnoreCase);
            var stack = board.GetState_LayerStack();
            if (stack != null)
            {
                for (int l = 1; l <= 60; l++)
                {
                    try
                    {
                        var layerObj = stack.LayerObject((TV6_Layer)l);
                        if (layerObj != null && layerObj.IsInLayerStack())
                        {
                            string name = layerObj.GetState_LayerName();
                            if (!string.IsNullOrEmpty(name))
                            {
                                map[name] = (TV6_Layer)l;
                                map[name.Replace(" ", "")] = (TV6_Layer)l;
                            }
                        }
                    }
                    catch { }
                }
            }

            // Defaults & aliases
            map["TopLayer"] = TV6_Layer.eV6_TopLayer;
            map["Top Layer"] = TV6_Layer.eV6_TopLayer;
            map["Top"] = TV6_Layer.eV6_TopLayer;
            map["BottomLayer"] = TV6_Layer.eV6_BottomLayer;
            map["Bottom Layer"] = TV6_Layer.eV6_BottomLayer;
            map["Bottom"] = TV6_Layer.eV6_BottomLayer;

            return map;
        }

        private Dictionary<string, IPCB_Net> BuildNetLookup(IPCB_Board board)
        {
            var map = new Dictionary<string, IPCB_Net>(StringComparer.OrdinalIgnoreCase);
            var iter = board.BoardIterator_Create();
            if (iter == null) return map;

            try
            {
                iter.SetState_FilterAll();
                iter.AddFilter_AllLayers();
                iter.AddFilter_Method(TIterationMethod.eProcessAll);

                // Collect net COM pointers from all primitives on the board
                for (var prim = iter.FirstPCBObject(); prim != null; prim = iter.NextPCBObject())
                {
                    try
                    {
                        var net = prim.GetState_Net();
                        if (net != null)
                        {
                            string name = net.GetState_Name();
                            if (!string.IsNullOrEmpty(name) && !map.ContainsKey(name))
                            {
                                map[name] = net;
                            }
                        }
                    }
                    catch { }
                }
            }
            finally
            {
                board.BoardIterator_Destroy(ref iter);
            }

            return map;
        }

        private void RepourPolygons(IPCB_Board board)
        {
            var iter = board.BoardIterator_Create();
            if (iter == null) return;

            try
            {
                iter.SetState_FilterAll();
                iter.AddFilter_AllLayers();
                iter.AddFilter_Method(TIterationMethod.eProcessAll);

                for (var prim = iter.FirstPCBObject(); prim != null; prim = iter.NextPCBObject())
                {
                    if (prim.GetState_ObjectID() != TObjectId.ePolyObject) continue;

                    var poly = prim as IPCB_Polygon;
                    if (poly != null)
                    {
                        poly.SetState_Poured(true);
                        try { poly.Rebuild(); } catch { }
                    }
                }
            }
            finally
            {
                board.BoardIterator_Destroy(ref iter);
            }
        }
    }
}
