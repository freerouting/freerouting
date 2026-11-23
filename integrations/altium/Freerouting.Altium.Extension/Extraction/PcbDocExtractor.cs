using System;
using System.Collections.Generic;
using Freerouting.Altium.Core.Model;
using RT_PCB;

namespace Freerouting.Altium.Extension.Extraction
{
    public class PcbDocExtractor
    {
        public BoardModel Extract(IPCB_Board board)
        {
            if (board == null) throw new ArgumentNullException("board");

            string fileName = ResolveBoardFileName(board);
            string boardName = !string.IsNullOrEmpty(fileName)
                ? System.IO.Path.GetFileNameWithoutExtension(fileName)
                : "AltiumBoard";

            var model = new BoardModel(boardName);

            // 1. Extract Layer Stack
            var layerMap = ExtractLayerStack(board, model);

            // 2. Extract Components, Padstacks, Footprint Images and Pins (Pass 1)
            var components = ExtractComponentsAndPadstacks(board, model, layerMap);

            // 3. Extract Board Outline Boundary
            ExtractBoardOutline(board, model, components);

            // 4. Single-Pass Unified Extraction for all PCB primitives (Pass 2)
            // Reads rules, classes, cutouts, keepouts, nets, existing routes, vias, and shelves polygons
            ExtractBoardPrimitives(board, model, layerMap);

            // 5. Post-process rules and Net Classes
            PostProcessRulesAndNetClasses(model);

            return model;
        }

        private static string GetDesignator(IPCB_Component comp)
        {
            if (comp == null) return null;
            string des = null;
            try
            {
                var nameObj = comp.GetState_Name();
                if (nameObj != null) des = nameObj.GetState_Text();
            }
            catch { }

            if (string.IsNullOrEmpty(des))
            {
                try
                {
                    des = comp.GetState_SourceDesignator();
                }
                catch { }
            }
            return des;
        }

        private static bool IsMatchingBoardBoundary(List<Point2D> pts, List<Point2D> boundaryVertices)
        {
            if (boundaryVertices == null || boundaryVertices.Count < 3 || pts == null || pts.Count < 3)
                return false;

            int bMinX = int.MaxValue, bMaxX = int.MinValue, bMinY = int.MaxValue, bMaxY = int.MinValue;
            foreach (var p in boundaryVertices)
            {
                if (p.X < bMinX) bMinX = p.X;
                if (p.X > bMaxX) bMaxX = p.X;
                if (p.Y < bMinY) bMinY = p.Y;
                if (p.Y > bMaxY) bMaxY = p.Y;
            }

            int kMinX = int.MaxValue, kMaxX = int.MinValue, kMinY = int.MaxValue, kMaxY = int.MinValue;
            foreach (var p in pts)
            {
                if (p.X < kMinX) kMinX = p.X;
                if (p.X > kMaxX) kMaxX = p.X;
                if (p.Y < kMinY) kMinY = p.Y;
                if (p.Y > kMaxY) kMaxY = p.Y;
            }

            const int tolerance = 500000; // 50 mils
            return (Math.Abs(kMinX - bMinX) <= tolerance &&
                    Math.Abs(kMaxX - bMaxX) <= tolerance &&
                    Math.Abs(kMinY - bMinY) <= tolerance &&
                    Math.Abs(kMaxY - bMaxY) <= tolerance) ||
                   (kMinX <= bMinX + tolerance && kMaxX >= bMaxX - tolerance &&
                    kMinY <= bMinY + tolerance && kMaxY >= bMaxY - tolerance);
        }

        private static bool IsMatchingBoardBoundary(int kMinX, int kMaxX, int kMinY, int kMaxY, List<Point2D> boundaryVertices)
        {
            if (boundaryVertices == null || boundaryVertices.Count < 3)
                return false;

            int bMinX = int.MaxValue, bMaxX = int.MinValue, bMinY = int.MaxValue, bMaxY = int.MinValue;
            foreach (var p in boundaryVertices)
            {
                if (p.X < bMinX) bMinX = p.X;
                if (p.X > bMaxX) bMaxX = p.X;
                if (p.Y < bMinY) bMinY = p.Y;
                if (p.Y > bMaxY) bMaxY = p.Y;
            }

            const int tolerance = 500000; // 50 mils
            return (Math.Abs(kMinX - bMinX) <= tolerance &&
                    Math.Abs(kMaxX - bMaxX) <= tolerance &&
                    Math.Abs(kMinY - bMinY) <= tolerance &&
                    Math.Abs(kMaxY - bMaxY) <= tolerance) ||
                   (kMinX <= bMinX + tolerance && kMaxX >= bMaxX - tolerance &&
                    kMinY <= bMinY + tolerance && kMaxY >= bMaxY - tolerance);
        }

        private void ExtractBoardOutline(IPCB_Board board, BoardModel model, List<IPCB_Component> components = null)
        {
            var outline = board != null ? board.GetState_BoardOutline() : null;
            if (outline != null)
            {
                // Level 1: Geometric polygon from BoardOutline
                try
                {
                    var geomPoly = outline.BoardOutline_GeometricPolygon();
                    if (geomPoly != null && geomPoly.GetState_Count() > 0)
                    {
                        var contour = geomPoly.GetState_Contour(0);
                        if (contour != null && contour.GetState_Count() >= 3)
                        {
                            int ptCount = contour.GetState_Count();
                            for (int i = 0; i < ptCount; i++)
                            {
                                model.BoundaryVertices.Add(new Point2D(contour.GetState_PointX(i), contour.GetState_PointY(i)));
                            }
                        }
                    }
                }
                catch { }

                // Level 2: Poly segments from BoardOutline
                if (model.BoundaryVertices.Count < 3)
                {
                    try
                    {
                        int ptCount = outline.GetState_PointCount();
                        if (ptCount >= 3)
                        {
                            for (int i = 0; i < ptCount; i++)
                            {
                                var seg = outline.GetState_Segments(i);
                                model.BoundaryVertices.Add(new Point2D(seg.vx, seg.vy));
                            }
                        }
                    }
                    catch { }
                }

                // Level 3: BoundingRectangle from BoardOutline
                if (model.BoundaryVertices.Count < 3)
                {
                    try
                    {
                        var rect = outline.BoundingRectangle();
                        if (rect.Right > rect.Left && rect.Top > rect.Bottom)
                        {
                            model.BoundaryVertices.Add(new Point2D(rect.Left, rect.Bottom));
                            model.BoundaryVertices.Add(new Point2D(rect.Right, rect.Bottom));
                            model.BoundaryVertices.Add(new Point2D(rect.Right, rect.Top));
                            model.BoundaryVertices.Add(new Point2D(rect.Left, rect.Top));
                        }
                    }
                    catch { }
                }
            }

            // Level 4: BoundingRectangle from Board object
            if (model.BoundaryVertices.Count < 3 && board != null)
            {
                try
                {
                    var bRect = board.BoundingRectangle();
                    if (bRect.Right > bRect.Left && bRect.Top > bRect.Bottom)
                    {
                        model.BoundaryVertices.Add(new Point2D(bRect.Left, bRect.Bottom));
                        model.BoundaryVertices.Add(new Point2D(bRect.Right, bRect.Bottom));
                        model.BoundaryVertices.Add(new Point2D(bRect.Right, bRect.Top));
                        model.BoundaryVertices.Add(new Point2D(bRect.Left, bRect.Top));
                    }
                }
                catch { }
            }

            // Level 5: Fallback to dynamic bounding box of all components + 200 mil (5 mm) margin
            if (model.BoundaryVertices.Count < 3)
            {
                int minX = int.MaxValue, maxX = int.MinValue;
                int minY = int.MaxValue, maxY = int.MinValue;

                if (components != null && components.Count > 0)
                {
                    foreach (var c in components)
                    {
                        try
                        {
                            var r = c.BoundingRectangleNoNameComment();
                            if (r.Right > r.Left && r.Top > r.Bottom)
                            {
                                if (r.Left < minX) minX = r.Left;
                                if (r.Right > maxX) maxX = r.Right;
                                if (r.Bottom < minY) minY = r.Bottom;
                                if (r.Top > maxY) maxY = r.Top;
                            }
                            else
                            {
                                int cx = c.GetState_XLocation();
                                int cy = c.GetState_YLocation();
                                if (cx < minX) minX = cx;
                                if (cx > maxX) maxX = cx;
                                if (cy < minY) minY = cy;
                                if (cy > maxY) maxY = cy;
                            }
                        }
                        catch
                        {
                            int cx = c.GetState_XLocation();
                            int cy = c.GetState_YLocation();
                            if (cx < minX) minX = cx;
                            if (cx > maxX) maxX = cx;
                            if (cy < minY) minY = cy;
                            if (cy > maxY) maxY = cy;
                        }
                    }
                }

                if (minX < maxX && minY < maxY)
                {
                    const int margin = 2000000; // 200 mil margin (approx 5 mm)
                    int x1 = minX - margin;
                    int y1 = minY - margin;
                    int x2 = maxX + margin;
                    int y2 = maxY + margin;

                    model.BoundaryVertices.Clear();
                    model.BoundaryVertices.Add(new Point2D(x1, y1));
                    model.BoundaryVertices.Add(new Point2D(x2, y1));
                    model.BoundaryVertices.Add(new Point2D(x2, y2));
                    model.BoundaryVertices.Add(new Point2D(x1, y2));
                }
                else
                {
                    int x1 = 0, y1 = 0, x2 = 10000000, y2 = 10000000;
                    model.BoundaryVertices.Clear();
                    model.BoundaryVertices.Add(new Point2D(x1, y1));
                    model.BoundaryVertices.Add(new Point2D(x2, y1));
                    model.BoundaryVertices.Add(new Point2D(x2, y2));
                    model.BoundaryVertices.Add(new Point2D(x1, y2));
                }
            }
        }

        private Dictionary<TV6_Layer, string> ExtractLayerStack(IPCB_Board board, BoardModel model)
        {
            var layerMap = new Dictionary<TV6_Layer, string>();
            var stack = board.GetState_LayerStack();

            if (stack != null && stack.Count() > 0)
            {
                int layerNum = 1;

                // Check Top Layer
                var topObj = stack.LayerObject(TV6_Layer.eV6_TopLayer);
                if (topObj != null && topObj.IsInLayerStack())
                {
                    string name = topObj.GetState_LayerName();
                    model.Layers.Add(new LayerInfo(name, LayerType.Signal, layerNum++));
                    layerMap[TV6_Layer.eV6_TopLayer] = name;
                }

                // Check Mid Layers 1..30
                for (int l = (int)TV6_Layer.eV6_MidLayer1; l <= (int)TV6_Layer.eV6_MidLayer30; l++)
                {
                    var midObj = stack.LayerObject((TV6_Layer)l);
                    if (midObj != null && midObj.IsInLayerStack())
                    {
                        string name = midObj.GetState_LayerName();
                        model.Layers.Add(new LayerInfo(name, LayerType.Signal, layerNum++));
                        layerMap[(TV6_Layer)l] = name;
                    }
                }

                // Check Bottom Layer
                var botObj = stack.LayerObject(TV6_Layer.eV6_BottomLayer);
                if (botObj != null && botObj.IsInLayerStack())
                {
                    string name = botObj.GetState_LayerName();
                    model.Layers.Add(new LayerInfo(name, LayerType.Signal, layerNum++));
                    layerMap[TV6_Layer.eV6_BottomLayer] = name;
                }

                // Check Internal Planes 1..16
                for (int l = (int)TV6_Layer.eV6_InternalPlane1; l <= (int)TV6_Layer.eV6_InternalPlane16; l++)
                {
                    var planeObj = stack.LayerObject((TV6_Layer)l);
                    if (planeObj != null && planeObj.IsInLayerStack())
                    {
                        string name = planeObj.GetState_LayerName();
                        model.Layers.Add(new LayerInfo(name, LayerType.Plane, layerNum++));
                        layerMap[(TV6_Layer)l] = name;
                    }
                }
            }

            // Fallback if no layer stack was populated
            if (model.Layers.Count == 0)
            {
                model.Layers.Add(new LayerInfo("Top Layer", LayerType.Signal, 1));
                model.Layers.Add(new LayerInfo("Bottom Layer", LayerType.Signal, 2));
                layerMap[TV6_Layer.eV6_TopLayer] = "Top Layer";
                layerMap[TV6_Layer.eV6_BottomLayer] = "Bottom Layer";
            }

            return layerMap;
        }

        private List<IPCB_Component> ExtractComponentsAndPadstacks(IPCB_Board board, BoardModel model, Dictionary<TV6_Layer, string> layerMap)
        {
            var compMap = new Dictionary<string, ComponentImage>(StringComparer.OrdinalIgnoreCase);
            var seenPadstacks = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            var components = new List<IPCB_Component>();

            // 1. Collect all components on the board
            var iter = board.BoardIterator_Create();
            if (iter == null) return components;

            try
            {
                iter.SetState_FilterAll();
                iter.AddFilter_AllLayers();
                iter.AddFilter_Method(TIterationMethod.eProcessAll);

                for (var prim = iter.FirstPCBObject(); prim != null; prim = iter.NextPCBObject())
                {
                    if (prim.GetState_ObjectID() != TObjectId.eComponentObject) continue;

                    var comp = prim as IPCB_Component;
                    if (comp != null) components.Add(comp);
                }
            }
            finally
            {
                board.BoardIterator_Destroy(ref iter);
            }

            // 2. Collect all pads grouped strictly by component designator
            var padsByComponent = new Dictionary<string, List<IPCB_Pad>>(StringComparer.OrdinalIgnoreCase);
            iter = board.BoardIterator_Create();
            if (iter != null)
            {
                try
                {
                    iter.SetState_FilterAll();
                    iter.AddFilter_AllLayers();
                    iter.AddFilter_Method(TIterationMethod.eProcessAll);

                    for (var prim = iter.FirstPCBObject(); prim != null; prim = iter.NextPCBObject())
                    {
                        if (prim.GetState_ObjectID() != TObjectId.ePadObject) continue;

                        var pad = prim as IPCB_Pad;
                        if (pad == null) continue;

                        var comp = pad.GetState_Component();
                        if (comp != null)
                        {
                            string compDes = GetDesignator(comp);
                            if (!string.IsNullOrEmpty(compDes))
                            {
                                List<IPCB_Pad> padList;
                                if (!padsByComponent.TryGetValue(compDes, out padList))
                                {
                                    padList = new List<IPCB_Pad>();
                                    padsByComponent[compDes] = padList;
                                }
                                padList.Add(pad);
                            }
                        }
                    }
                }
                finally
                {
                    board.BoardIterator_Destroy(ref iter);
                }
            }

            // 3. Process each component and populate library images and placement
            // Sort components so front-side instances are processed first to define canonical unmirrored images
            components.Sort((a, b) =>
            {
                bool b1 = a.GetState_FlippedOnLayer() || (a.GetState_Layer() == TV6_Layer.eV6_BottomLayer);
                bool b2 = b.GetState_FlippedOnLayer() || (b.GetState_Layer() == TV6_Layer.eV6_BottomLayer);
                return b1.CompareTo(b2);
            });

            foreach (var comp in components)
            {
                string des = GetDesignator(comp);
                string pattern = comp.GetState_Pattern();
                if (string.IsNullOrEmpty(pattern)) pattern = "Footprint_" + des;
                bool isBottom = comp.GetState_FlippedOnLayer() || (comp.GetState_Layer() == TV6_Layer.eV6_BottomLayer);

                List<IPCB_Pad> compPads;
                padsByComponent.TryGetValue(des, out compPads);
                if (!isBottom && compPads != null)
                {
                    foreach (var p in compPads)
                    {
                        if (p.GetState_Layer() == TV6_Layer.eV6_BottomLayer)
                        {
                            isBottom = true;
                            break;
                        }
                    }
                }

                string side = isBottom ? "back" : "front";
                double compRot = comp.GetState_Rotation();
                if (isBottom)
                {
                    compRot = (compRot + 180.0) % 360.0;
                }
                int compX = comp.GetState_XLocation();
                int compY = comp.GetState_YLocation();

                ComponentImage img;
                bool isNewPattern = !compMap.TryGetValue(pattern, out img);
                if (isNewPattern)
                {
                    img = new ComponentImage(pattern);
                    compMap[pattern] = img;
                    model.Images.Add(img);

                    // Populate pins strictly once from this first instance of the footprint
                    if (compPads != null)
                    {
                        double rad = compRot * Math.PI / 180.0;
                        double cos = Math.Cos(rad);
                        double sin = Math.Sin(rad);

                        int minPadX = int.MaxValue, maxPadX = int.MinValue;
                        int minPadY = int.MaxValue, maxPadY = int.MinValue;

                        foreach (var pad in compPads)
                        {
                            // Calculate local relative coordinates by inverse-rotating
                            int dx = pad.GetState_XLocation() - compX;
                            int dy = pad.GetState_YLocation() - compY;

                            double u = dx * cos + dy * sin;
                            double v = -dx * sin + dy * cos;
                            if (isBottom) u = -u;

                            int localX = (int)Math.Round(u);
                            int localY = (int)Math.Round(v);

                            // Calculate relative pad rotation within the footprint
                            double padRot = pad.GetState_Rotation();
                            double relRot = isBottom ? (compRot - padRot) : (padRot - compRot);
                            relRot = ((relRot % 360.0) + 360.0) % 360.0;
                            if (Math.Abs(relRot - 360.0) < 0.001) relRot = 0.0;

                            // Extract pad size from top/bottom
                            int w = isBottom ? pad.GetState_BotXSize() : pad.GetState_TopXSize();
                            if (w <= 0) w = pad.GetState_TopXSize();
                            if (w <= 0) w = pad.GetState_BotXSize();
                            if (w <= 0) w = 600000;

                            int h = isBottom ? pad.GetState_BotYSize() : pad.GetState_TopYSize();
                            if (h <= 0) h = pad.GetState_TopYSize();
                            if (h <= 0) h = pad.GetState_BotYSize();
                            if (h <= 0) h = 600000;

                            var shape = isBottom ? pad.GetState_BotShape() : pad.GetState_TopShape();
                            var kind = (shape == TShape.eRectangular || shape == TShape.eRotatedRectShape || shape == TShape.eRoundedRectangular)
                                ? PadShapeKind.Rectangular
                                : PadShapeKind.Round;

                            // Normalize orthogonal rotations (0, 90, 180, 270) to canonical axis-aligned rectangles
                            int effW = w;
                            int effH = h;
                            double padstackRot = 0.0;

                            if (kind == PadShapeKind.Rectangular)
                            {
                                double rem90 = relRot % 90.0;
                                bool isOrthogonal = rem90 < 0.5 || rem90 > 89.5;
                                if (isOrthogonal)
                                {
                                    int quadrant = (int)Math.Round(relRot / 90.0) % 4;
                                    if (quadrant == 1 || quadrant == 3) // 90 or 270 degrees
                                    {
                                        effW = h;
                                        effH = w;
                                    }
                                    padstackRot = 0.0;
                                }
                                else
                                {
                                    padstackRot = relRot;
                                }
                            }

                            string padstackName = GetOrCreatePadstack(pad, effW, effH, padstackRot, kind, model, seenPadstacks);

                            img.Pins.Add(new ComponentPin(pad.GetState_Name(), padstackName, new Point2D(localX, localY), 0.0));

                            // Accumulate bounding box of pads using effective dimensions
                            int hw = effW / 2;
                            int hh = effH / 2;

                            if (localX - hw < minPadX) minPadX = localX - hw;
                            if (localX + hw > maxPadX) maxPadX = localX + hw;
                            if (localY - hh < minPadY) minPadY = localY - hh;
                            if (localY + hh > maxPadY) maxPadY = localY + hh;
                        }

                        // Extract physical courtyard boundary from Altium component body bounds
                        bool courtyardExtracted = false;
                        try
                        {
                            var bodyRect = comp.BoundingRectangleNoNameComment();
                            if (bodyRect.Right > bodyRect.Left && bodyRect.Top > bodyRect.Bottom)
                            {
                                Point2D[] worldCorners = new Point2D[]
                                {
                                    new Point2D(bodyRect.Left, bodyRect.Bottom),
                                    new Point2D(bodyRect.Right, bodyRect.Bottom),
                                    new Point2D(bodyRect.Right, bodyRect.Top),
                                    new Point2D(bodyRect.Left, bodyRect.Top)
                                };

                                var localCorners = new List<Point2D>();
                                foreach (var p in worldCorners)
                                {
                                    int dx = p.X - compX;
                                    int dy = p.Y - compY;
                                    double u = dx * cos + dy * sin;
                                    double v = -dx * sin + dy * cos;
                                    if (isBottom) u = -u;
                                    localCorners.Add(new Point2D((int)Math.Round(u), (int)Math.Round(v)));
                                }
                                localCorners.Add(localCorners[0]); // Guarantee closed path
                                img.OutlinePaths.Add(localCorners);
                                courtyardExtracted = true;
                            }
                        }
                        catch { }

                        if (!courtyardExtracted && minPadX < maxPadX && minPadY < maxPadY)
                        {
                            const int courtyardMargin = 50000; // 5 mil courtyard margin
                            int left = minPadX - courtyardMargin;
                            int right = maxPadX + courtyardMargin;
                            int bottom = minPadY - courtyardMargin;
                            int top = maxPadY + courtyardMargin;

                            var localCorners = new List<Point2D>
                            {
                                new Point2D(left, bottom),
                                new Point2D(right, bottom),
                                new Point2D(right, top),
                                new Point2D(left, top),
                                new Point2D(left, bottom)
                            };
                            img.OutlinePaths.Add(localCorners);

                            img.CourtyardMin = new Point2D(left, bottom);
                            img.CourtyardMax = new Point2D(right, top);
                            img.HasCourtyard = true;
                        }
                    }
                }

                model.Components.Add(new PlacedComponent(des, pattern, new Point2D(compX, compY), compRot, side));
            }

            return components;
        }

        private string GetOrCreatePadstack(IPCB_Pad pad, int w, int h, double rot, PadShapeKind kind, BoardModel model, HashSet<string> seenPadstacks)
        {
            bool isThru = pad.GetState_HoleSize() > 0;
            int intRot = (int)Math.Round(rot);

            string padstackName = string.Format("PAD_{0}_{1}x{2}{3}",
                isThru ? "TH" : "SMD",
                w, h,
                intRot != 0 ? string.Format("_{0}rot", intRot) : "");

            if (seenPadstacks.Add(padstackName))
            {
                var padstack = new PadstackInfo(padstackName, pad.GetState_HoleSize(), isThru);
                if (isThru)
                {
                    foreach (var layer in model.Layers)
                    {
                        padstack.Shapes.Add(new PadstackShape(layer.Name, kind, w, h, rot));
                    }
                }
                else
                {
                    // SMD padstacks in Specctra DSN are defined on layer 0 (Top Layer).
                    // When placed on "back", Freerouting automatically flips them to the bottom layer.
                    string topSignalLayer = model.Layers.Count > 0 ? model.Layers[0].Name : "Top Layer";
                    padstack.Shapes.Add(new PadstackShape(topSignalLayer, kind, w, h, rot));
                }
                model.Padstacks.Add(padstack);
            }

            return padstackName;
        }

        /// <summary>
        /// Single-pass unified board iterator that extracts all primitives:
        /// rules, classes, cutouts, keepouts, nets, existing routes/vias, and shelves copper pours.
        /// Replaces 8 separate full-board iterations with 1 pass.
        /// </summary>
        private void ExtractBoardPrimitives(IPCB_Board board, BoardModel model, Dictionary<TV6_Layer, string> layerMap)
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
                    var objId = prim.GetState_ObjectID();

                    // A. Design Rules
                    if (objId == TObjectId.eRuleObject)
                    {
                        var rule = prim as IPCB_Rule;
                        if (rule != null)
                        {
                            ExtractSingleRule(rule, model);
                        }
                    }
                    // B. Net Classes
                    else if (objId == TObjectId.eClassObject)
                    {
                        var cls = prim as IPCB_ObjectClass;
                        if (cls != null && cls.GetState_MemberKind() == TClassMemberKind.eClassMemberKind_Net)
                        {
                            string clsName = cls.GetState_Name();
                            if (!string.IsNullOrEmpty(clsName))
                            {
                                var netClassInfo = new NetClassInfo(clsName);
                                for (int i = 0; ; i++)
                                {
                                    string memberNet = cls.GetState_MemberName(i);
                                    if (string.IsNullOrEmpty(memberNet)) break;
                                    netClassInfo.NetNames.Add(memberNet);
                                }
                                if (netClassInfo.NetNames.Count > 0)
                                {
                                    model.NetClasses.Add(netClassInfo);
                                }
                            }
                        }
                    }
                    // C. Regions (Cutouts, Board Outline, or Keepouts)
                    else if (objId == TObjectId.eRegionObject)
                    {
                        var region = prim as IPCB_Region;
                        if (region != null)
                        {
                            var kind = region.GetState_Kind();
                            if (kind == TRegionKind.eRegionKind_Cutout || kind == TRegionKind.eRegionKind_BoardCutout)
                            {
                                var contour = region.GetMainContour();
                                if (contour != null && contour.GetState_Count() >= 3)
                                {
                                    var cutout = new List<Point2D>();
                                    for (int i = 0; i < contour.GetState_Count(); i++)
                                    {
                                        cutout.Add(new Point2D(contour.GetState_PointX(i), contour.GetState_PointY(i)));
                                    }
                                    if (!IsMatchingBoardBoundary(cutout, model.BoundaryVertices))
                                    {
                                        model.Cutouts.Add(cutout);
                                    }
                                }
                            }
                            else if (prim.GetState_IsKeepout() || prim.GetState_Layer() == TV6_Layer.eV6_KeepOutLayer)
                            {
                                var contour = region.GetMainContour();
                                if (contour != null && contour.GetState_Count() >= 3)
                                {
                                    string layerName = ResolveLayerName(prim.GetState_Layer(), layerMap);
                                    var pts = new List<Point2D>();
                                    for (int i = 0; i < contour.GetState_Count(); i++)
                                    {
                                        pts.Add(new Point2D(contour.GetState_PointX(i), contour.GetState_PointY(i)));
                                    }
                                    if (!IsMatchingBoardBoundary(pts, model.BoundaryVertices))
                                    {
                                        model.BoardKeepouts.Add(new KeepoutArea(layerName, pts, false));
                                    }
                                }
                            }
                        }
                    }
                    // D. Fills (Keepouts)
                    else if (objId == TObjectId.eFillObject)
                    {
                        if (prim.GetState_IsKeepout() || prim.GetState_Layer() == TV6_Layer.eV6_KeepOutLayer)
                        {
                            var fill = prim as IPCB_Fill;
                            if (fill != null)
                            {
                                string layerName = ResolveLayerName(prim.GetState_Layer(), layerMap);
                                int minX = Math.Min(fill.GetState_X1Location(), fill.GetState_X2Location());
                                int maxX = Math.Max(fill.GetState_X1Location(), fill.GetState_X2Location());
                                int minY = Math.Min(fill.GetState_Y1Location(), fill.GetState_Y2Location());
                                int maxY = Math.Max(fill.GetState_Y1Location(), fill.GetState_Y2Location());
                                if (!IsMatchingBoardBoundary(minX, maxX, minY, maxY, model.BoundaryVertices))
                                {
                                    model.BoardKeepouts.Add(new KeepoutArea(layerName, new Point2D(minX, minY), new Point2D(maxX, maxY), false));
                                }
                            }
                        }
                    }
                    // E. Tracks (Keepouts or Pre-existing electrical wires)
                    else if (objId == TObjectId.eTrackObject)
                    {
                        var track = prim as IPCB_Track;
                        if (track != null)
                        {
                            if (prim.GetState_IsKeepout() && prim.GetState_Layer() != TV6_Layer.eV6_KeepOutLayer)
                            {
                                string layerName = ResolveLayerName(prim.GetState_Layer(), layerMap);
                                int hw = Math.Max(track.GetState_Width() / 2, 50000);
                                int minX = Math.Min(track.GetState_X1(), track.GetState_X2()) - hw;
                                int maxX = Math.Max(track.GetState_X1(), track.GetState_X2()) + hw;
                                int minY = Math.Min(track.GetState_Y1(), track.GetState_Y2()) - hw;
                                int maxY = Math.Max(track.GetState_Y1(), track.GetState_Y2()) + hw;
                                if (!IsMatchingBoardBoundary(minX, maxX, minY, maxY, model.BoundaryVertices))
                                {
                                    model.BoardKeepouts.Add(new KeepoutArea(layerName, new Point2D(minX, minY), new Point2D(maxX, maxY), false));
                                }
                            }
                            else
                            {
                                string layerName;
                                if (layerMap.TryGetValue(track.GetState_Layer(), out layerName))
                                {
                                    var net = track.GetState_Net();
                                    string netName = net != null ? net.GetState_Name() : null;
                                    if (!string.IsNullOrEmpty(netName))
                                    {
                                        bool isLocked = !track.GetState_Moveable();
                                        var wire = new WireModel(netName, layerName, track.GetState_Width(), isLocked);
                                        wire.Vertices.Add(new Point2D(track.GetState_X1(), track.GetState_Y1()));
                                        wire.Vertices.Add(new Point2D(track.GetState_X2(), track.GetState_Y2()));
                                        model.PreExistingWires.Add(wire);
                                    }
                                }
                            }
                        }
                    }
                    // F. Vias (Pre-existing electrical vias)
                    else if (objId == TObjectId.eViaObject)
                    {
                        var via = prim as IPCB_Via;
                        if (via != null)
                        {
                            var net = via.GetState_Net();
                            string netName = net != null ? net.GetState_Name() : null;
                            if (!string.IsNullOrEmpty(netName))
                            {
                                string viaPadstackName = model.Rules.DefaultViaPadstack ?? "Via_24_12";
                                bool isLocked = !via.GetState_Moveable();
                                var viaModel = new ViaModel(netName, viaPadstackName, new Point2D(via.GetState_XLocation(), via.GetState_YLocation()), isLocked);
                                model.PreExistingVias.Add(viaModel);
                            }
                        }
                    }
                    // G. Pads (Nets and Connectivity)
                    else if (objId == TObjectId.ePadObject)
                    {
                        var pad = prim as IPCB_Pad;
                        if (pad != null)
                        {
                            var net = pad.GetState_Net();
                            var comp = pad.GetState_Component();
                            if (net != null && comp != null)
                            {
                                string netName = net.GetState_Name();
                                string compDes = GetDesignator(comp);
                                string pinName = pad.GetState_Name();
                                if (!string.IsNullOrEmpty(netName) && !string.IsNullOrEmpty(compDes) && !string.IsNullOrEmpty(pinName))
                                {
                                    string pinRef = string.Format("{0}-{1}", compDes, pinName);
                                    NetInfo netInfo = model.Nets.Find(n => string.Equals(n.Name, netName, StringComparison.OrdinalIgnoreCase));
                                    if (netInfo == null)
                                    {
                                        netInfo = new NetInfo(netName);
                                        model.Nets.Add(netInfo);
                                    }
                                    if (!netInfo.PinReferences.Contains(pinRef))
                                    {
                                        netInfo.PinReferences.Add(pinRef);
                                    }
                                }
                            }
                        }
                    }
                    // H. Polygons (Shelve poured signal copper)
                    else if (objId == TObjectId.ePolyObject)
                    {
                        var poly = prim as IPCB_Polygon;
                        if (poly != null && poly.GetState_Poured())
                        {
                            poly.SetState_Poured(false);
                        }
                    }
                }
            }
            finally
            {
                board.BoardIterator_Destroy(ref iter);
            }
        }

        private void ExtractSingleRule(IPCB_Rule rule, BoardModel model)
        {
            var kind = rule.GetState_RuleKind();
            if (kind == TRuleKind.eRule_Clearance)
            {
                var clRule = rule as IPCB_ClearanceConstraint;
                if (clRule != null && clRule.GetState_Gap() > 0)
                {
                    int gap = clRule.GetState_Gap();
                    string scope = clRule.GetState_Scope1Expression() ?? "";
                    if (string.Equals(scope, "All", StringComparison.OrdinalIgnoreCase) || string.IsNullOrEmpty(scope))
                    {
                        model.Rules.DefaultClearance = gap;
                        model.Rules.WireToWireClearance = gap;
                        model.Rules.WireToViaClearance = gap;
                        model.Rules.ViaToViaClearance = gap;
                        model.Rules.SmdToSmdClearance = gap;
                    }
                    else
                    {
                        // Check if targeting a NetClass
                        foreach (var nc in model.NetClasses)
                        {
                            if (scope.IndexOf(nc.Name, StringComparison.OrdinalIgnoreCase) >= 0)
                            {
                                nc.Clearance = gap;
                            }
                        }
                    }
                }
            }
            else if (kind == TRuleKind.eRule_MaxMinWidth)
            {
                var wRule = rule as IPCB_MaxMinWidthConstraint;
                if (wRule != null)
                {
                    int prefW = wRule.GetState_PreferedWidth();
                    int minW = wRule.GetState_MinLimit();
                    int maxW = wRule.GetState_MaxLimit();
                    string scope = wRule.GetState_Scope1Expression() ?? "";

                    if (string.Equals(scope, "All", StringComparison.OrdinalIgnoreCase) || string.IsNullOrEmpty(scope))
                    {
                        if (prefW > 0) model.Rules.DefaultWidth = prefW;
                        else if (minW > 0) model.Rules.DefaultWidth = minW;
                        if (minW > 0) model.Rules.MinWidth = minW;
                        if (maxW > 0) model.Rules.MaxWidth = maxW;
                    }
                    else
                    {
                        foreach (var nc in model.NetClasses)
                        {
                            if (scope.IndexOf(nc.Name, StringComparison.OrdinalIgnoreCase) >= 0)
                            {
                                nc.Width = prefW > 0 ? prefW : minW;
                            }
                        }
                    }
                }
            }
            else if (kind == TRuleKind.eRule_RoutingViaStyle)
            {
                var viaRule = rule as IPCB_RoutingViaStyleRule;
                if (viaRule != null && viaRule.GetState_PreferedWidth() > 0)
                {
                    int prefWidth = viaRule.GetState_PreferedWidth();
                    int prefHole = viaRule.GetState_PreferedHoleWidth();
                    string viaPadstackName = string.Format("Via_{0}_{1}", prefWidth, prefHole);
                    model.Rules.DefaultViaPadstack = viaPadstackName;

                    if (!model.Rules.AvailableViaPadstacks.Contains(viaPadstackName))
                    {
                        model.Rules.AvailableViaPadstacks.Add(viaPadstackName);
                    }
                    RegisterViaPadstack(model, viaPadstackName, prefWidth, prefHole);

                    int minWidth = viaRule.GetState_MinWidth();
                    int minHole = viaRule.GetState_MinHoleWidth();
                    if (minWidth > 0 && minWidth < prefWidth)
                    {
                        if (minHole <= 0) minHole = prefHole;
                        string minViaName = string.Format("Via_{0}_{1}", minWidth, minHole);
                        if (!model.Rules.AvailableViaPadstacks.Contains(minViaName))
                        {
                            model.Rules.AvailableViaPadstacks.Add(minViaName);
                            RegisterViaPadstack(model, minViaName, minWidth, minHole);
                        }
                    }
                }
            }
            else if (kind == TRuleKind.eRule_BoardOutlineClearance)
            {
                var outlineRule = rule as IPCB_BoardOutlineClearanceConstraint;
                if (outlineRule != null && outlineRule.GetState_Gap() > 0)
                {
                    model.Rules.BoardOutlineClearance = outlineRule.GetState_Gap();
                }
            }
            else if (kind == TRuleKind.eRule_HoleToHoleClearance)
            {
                var holeRule = rule as IPCB_HoleToHoleClearanceRule;
                if (holeRule != null && holeRule.GetState_Gap() > 0)
                {
                    model.Rules.HoleToHoleClearance = holeRule.GetState_Gap();
                }
            }
        }

        private void PostProcessRulesAndNetClasses(BoardModel model)
        {
            // Ensure standard 24 mil / 12 mil escape via exists if preferred via exceeds 30 mil (300,000 units)
            // or if no via was extracted, so dense SMD pins (like SOIC-8 or 0402) can route to other layers cleanly
            if (model.Rules.AvailableViaPadstacks.Count == 0 ||
                (model.Rules.AvailableViaPadstacks.Count == 1 && model.Padstacks.Find(p => p.Name == model.Rules.DefaultViaPadstack)?.Shapes.Count > 0 &&
                 model.Padstacks.Find(p => p.Name == model.Rules.DefaultViaPadstack).Shapes[0].Width > 300000))
            {
                const string stdViaName = "Via_240000_120000";
                if (!model.Rules.AvailableViaPadstacks.Contains(stdViaName))
                {
                    model.Rules.AvailableViaPadstacks.Add(stdViaName);
                    RegisterViaPadstack(model, stdViaName, 240000, 120000);
                }
            }

            // Sync NetClass via padstacks
            foreach (var nc in model.NetClasses)
            {
                if (nc.ViaPadstackNames.Count == 0)
                {
                    nc.ViaPadstackNames.AddRange(model.Rules.AvailableViaPadstacks);
                }
            }
        }

        private static string ResolveLayerName(TV6_Layer layer, Dictionary<TV6_Layer, string> layerMap)
        {
            string name;
            if (layerMap != null && layerMap.TryGetValue(layer, out name))
            {
                return name;
            }
            return "signal";
        }

        private void RegisterViaPadstack(BoardModel model, string name, int width, int hole)
        {
            if (model.Padstacks.Exists(p => string.Equals(p.Name, name, StringComparison.OrdinalIgnoreCase))) return;

            var viaPadstack = new PadstackInfo(name, hole, true);
            foreach (var layer in model.Layers)
            {
                viaPadstack.Shapes.Add(new PadstackShape(layer.Name, PadShapeKind.Round, width, width, 0.0));
            }
            model.Padstacks.Add(viaPadstack);
        }

        public static string ResolveBoardFileName(IPCB_Board board)
        {
            try
            {
                var client = Freerouting.Altium.Extension.Commands.FreeroutingServerModule.HostClient;
                var view = client != null ? client.GetCurrentView() : null;
                if (view != null)
                {
                    var doc = view.GetOwnerDocument();
                    if (doc != null)
                    {
                        string fn = doc.GetFileName();
                        if (!string.IsNullOrEmpty(fn)) return fn;
                    }
                }
            }
            catch { }

            return "PCB1.PcbDoc";
        }
    }
}
